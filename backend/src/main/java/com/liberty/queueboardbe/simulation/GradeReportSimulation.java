package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.exception.JobProcessingException;
import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.payload.GradeReportPayload;
import com.liberty.queueboardbe.simulation.LmsRepository.Course;
import com.liberty.queueboardbe.simulation.LmsRepository.Enrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GRADE_REPORT: letter grade and completion for every scored enrollment, plus awards for 90+.
 * One transaction around all of it, so any failure rolls back every change.
 */
@Component
public class GradeReportSimulation {

    private static final Logger log = LoggerFactory.getLogger(GradeReportSimulation.class);

    record Award(String type, String name) {
    }

    private final LmsRepository lms;
    private final SimulatedWork work;
    private final Clock clock;

    public GradeReportSimulation(LmsRepository lms, SimulatedWork work, Clock clock) {
        this.lms = lms;
        this.work = work;
        this.clock = clock;
    }

    public void validate(GradeReportPayload payload, List<FieldError> errors) {
        if (lms.course(payload.courseCode()).isEmpty()) {
            errors.add(new FieldError("payload.courseCode", "Course not found"));
        }
    }

    @Transactional
    public String run(UUID jobId, GradeReportPayload payload) {
        Course course = lms.course(payload.courseCode()).orElseThrow();
        if (course.closed()) {
            throw new JobProcessingException(JobErrorCode.COURSE_CLOSED, "Course " + course.code() + " is closed");
        }
        List<Enrollment> scored = lms.enrollments(course.id()).stream().filter(e -> e.score() != null).toList();
        work.pause(SimulatedWork.between(2000, 6000, 160, scored.size()));

        Instant now = clock.instant();
        int awards = 0;
        for (Enrollment enrollment : scored) {
            lms.saveGrade(enrollment.id(), letterFor(enrollment.score()), now);
            Optional<Award> award = awardFor(enrollment.score());
            if (award.isPresent()) {
                awards += lms.addAwardIfMissing(enrollment.id(), award.get().type(), award.get().name(), now, jobId);
            }
        }
        log.debug("Graded {} students in {}, {} new awards", scored.size(), course.code(), awards);
        return scored.size() + " students graded, " + awards + " awards";
    }

    static String letterFor(BigDecimal score) {
        double s = score.doubleValue();
        return s >= 90 ? "A" : s >= 80 ? "B" : s >= 70 ? "C" : s >= 60 ? "D" : "F";
    }

    /** 95+ earns a Distinction certificate, 90 to 95 an Honor roll badge. */
    static Optional<Award> awardFor(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(95)) >= 0) {
            return Optional.of(new Award("CERTIFICATE", "Distinction"));
        }
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return Optional.of(new Award("BADGE", "Honor roll"));
        }
        return Optional.empty();
    }
}
