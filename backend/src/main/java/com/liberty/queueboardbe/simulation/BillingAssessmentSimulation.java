package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.exception.JobProcessingException;
import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.payload.BillingAssessmentPayload;
import com.liberty.queueboardbe.simulation.LmsRepository.Account;
import com.liberty.queueboardbe.simulation.LmsRepository.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * BILLING_ASSESSMENT: recalculates each account's balance, adds one LATE_FEE to past-due accounts,
 * and updates aid status. One transaction around all of it, so any failure rolls back every change.
 */
@Component
public class BillingAssessmentSimulation {

    private static final Logger log = LoggerFactory.getLogger(BillingAssessmentSimulation.class);

    static final BigDecimal LATE_FEE = new BigDecimal("75.00");
    /** Past-due ELIGIBLE accounts owing more than this go to PENDING_REVIEW. */
    static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("2000.00");

    private final LmsRepository lms;
    private final SimulatedWork work;
    private final Clock clock;

    public BillingAssessmentSimulation(LmsRepository lms, SimulatedWork work, Clock clock) {
        this.lms = lms;
        this.work = work;
        this.clock = clock;
    }

    public void validate(BillingAssessmentPayload payload, List<FieldError> errors) {
        if (lms.term(payload.termCode()).isEmpty()) {
            errors.add(new FieldError("payload.termCode", "Term not found"));
        }
    }

    @Transactional
    public String run(UUID jobId, BillingAssessmentPayload payload) {
        Term term = lms.term(payload.termCode()).orElseThrow();
        if (term.closed()) {
            throw new JobProcessingException(JobErrorCode.TERM_CLOSED, "Term " + term.code() + " is closed");
        }
        List<Account> accounts = lms.accounts(term.id());
        work.pause(SimulatedWork.between(4000, 8000, 100, accounts.size()));

        Instant now = clock.instant();
        boolean feesDue = LocalDate.ofInstant(now, clock.getZone()).isAfter(term.feeDueDate());
        boolean applyLateFees = Boolean.TRUE.equals(payload.applyLateFees());
        int lateFees = 0;
        int aidChanges = 0;

        for (Account account : accounts) {
            BigDecimal balance = account.owed().add(account.charges());
            boolean pastDue = feesDue && balance.signum() > 0;
            if (applyLateFees && pastDue && lms.addLateFeeIfMissing(account.id(), LATE_FEE, now, jobId) == 1) {
                balance = balance.add(LATE_FEE);
                lateFees++;
            }
            String aidStatus = nextAidStatus(account.aidStatus(), pastDue, balance);
            if (!aidStatus.equals(account.aidStatus())) {
                aidChanges++;
            }
            lms.saveAssessment(account.id(), balance, aidStatus, now);
        }
        log.debug("Assessed {} accounts in {}: {} late fees, {} aid changes", accounts.size(), term.code(), lateFees,
                aidChanges);
        return accounts.size() + " accounts assessed, " + lateFees + " late fees, " + aidChanges + " aid changes";
    }

    /**
     * PENDING_REVIEW resolves to ELIGIBLE, or INELIGIBLE if past due. ELIGIBLE accounts past due by more
     * than the threshold go to PENDING_REVIEW. INELIGIBLE stays until a person reviews it.
     */
    static String nextAidStatus(String current, boolean pastDue, BigDecimal balance) {
        return switch (current) {
            case "PENDING_REVIEW" -> pastDue ? "INELIGIBLE" : "ELIGIBLE";
            case "ELIGIBLE" -> pastDue && balance.compareTo(REVIEW_THRESHOLD) > 0 ? "PENDING_REVIEW" : "ELIGIBLE";
            default -> current;
        };
    }
}
