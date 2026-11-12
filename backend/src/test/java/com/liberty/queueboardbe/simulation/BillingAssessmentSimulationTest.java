package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.DatabaseTest;
import com.liberty.queueboardbe.exception.JobProcessingException;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.payload.BillingAssessmentPayload;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Rollback test runs first, while no late fees exist, so the failure undoes real inserts. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingAssessmentSimulationTest extends DatabaseTest {

    private static final String ACCOUNTS = """
            SELECT a.* FROM student_account a JOIN term t ON t.id = a.term_id WHERE t.code = 'FA26' ORDER BY a.id""";
    private static final String CHARGES = "SELECT * FROM account_charge ORDER BY id";

    @Autowired
    private BillingAssessmentSimulation simulation;

    private UUID newJob() {
        return insertJob(JobType.BILLING_ASSESSMENT, "{\"termCode\":\"FA26\",\"applyLateFees\":true}");
    }

    private static BigDecimal owed(Map<String, Object> account) {
        return ((BigDecimal) account.get("TUITION_AMOUNT"))
                .subtract((BigDecimal) account.get("AID_AMOUNT"))
                .subtract((BigDecimal) account.get("AMOUNT_PAID"));
    }

    @Test
    @Order(1)
    void failurePartwayLeavesZeroDomainChanges() {
        List<Map<String, Object>> accountsBefore = rows(ACCOUNTS);
        List<Map<String, Object>> chargesBefore = rows(CHARGES);
        // The first late fee really is added (and earlier accounts saved), then the second blows up.
        doCallRealMethod().doThrow(new IllegalStateException("ledger down"))
                .when(lms).addLateFeeIfMissing(anyLong(), any(), any(), any());

        assertThatThrownBy(() -> simulation.run(newJob(), new BillingAssessmentPayload("FA26", true, null)))
                .hasMessage("ledger down");

        verify(lms, times(2)).addLateFeeIfMissing(anyLong(), any(), any(), any());
        verify(lms, atLeastOnce()).saveAssessment(anyLong(), any(), any(), any());
        assertThat(rows(ACCOUNTS)).isEqualTo(accountsBefore);
        assertThat(rows(CHARGES)).isEqualTo(chargesBefore);
    }

    @Test
    @Order(2)
    void successRecalculatesBalancesAndChargesEachPastDueAccountOnce() {
        List<Map<String, Object>> before = rows(ACCOUNTS);
        long pastDue = before.stream().filter(a -> owed(a).signum() > 0).count();
        assertThat(pastDue).isPositive();

        UUID jobId = newJob();
        String summary = simulation.run(jobId, new BillingAssessmentPayload("FA26", true, null));

        List<Map<String, Object>> after = rows(ACCOUNTS);
        long aidChanges = 0;
        for (int i = 0; i < after.size(); i++) {
            Map<String, Object> account = after.get(i);
            BigDecimal fee = owed(account).signum() > 0 ? BillingAssessmentSimulation.LATE_FEE : BigDecimal.ZERO;
            assertThat((BigDecimal) account.get("BALANCE")).isEqualByComparingTo(owed(account).add(fee));
            assertThat(account.get("LAST_ASSESSED_AT")).isNotNull();
            if (!account.get("AID_STATUS").equals(before.get(i).get("AID_STATUS"))) {
                aidChanges++;
            }
        }
        assertThat(count("SELECT COUNT(*) FROM account_charge WHERE job_id = ? AND charge_type = 'LATE_FEE'", jobId))
                .isEqualTo((int) pastDue);
        assertThat(summary).isEqualTo(before.size() + " accounts assessed, " + pastDue + " late fees, "
                + aidChanges + " aid changes");

        // Running again never charges a second late fee.
        assertThat(simulation.run(newJob(), new BillingAssessmentPayload("FA26", true, null)))
                .contains(", 0 late fees, ");
        assertThat(count("SELECT COUNT(*) FROM account_charge")).isEqualTo((int) pastDue);
    }

    @Test
    void closedTermFailsPermanently() {
        assertThatThrownBy(() -> simulation.run(newJob(), new BillingAssessmentPayload("SP26", true, null)))
                .isInstanceOfSatisfying(JobProcessingException.class,
                        e -> assertThat(e.getCode()).isEqualTo(JobErrorCode.TERM_CLOSED));
    }

    @Test
    void aidStatusRules() {
        BigDecimal small = new BigDecimal("100.00");
        BigDecimal large = new BigDecimal("2500.00");
        assertThat(BillingAssessmentSimulation.nextAidStatus("PENDING_REVIEW", false, BigDecimal.ZERO)).isEqualTo("ELIGIBLE");
        assertThat(BillingAssessmentSimulation.nextAidStatus("PENDING_REVIEW", true, small)).isEqualTo("INELIGIBLE");
        assertThat(BillingAssessmentSimulation.nextAidStatus("ELIGIBLE", true, large)).isEqualTo("PENDING_REVIEW");
        assertThat(BillingAssessmentSimulation.nextAidStatus("ELIGIBLE", true, small)).isEqualTo("ELIGIBLE");
        assertThat(BillingAssessmentSimulation.nextAidStatus("INELIGIBLE", false, BigDecimal.ZERO)).isEqualTo("INELIGIBLE");
    }
}
