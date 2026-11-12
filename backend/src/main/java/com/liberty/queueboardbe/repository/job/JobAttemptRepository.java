package com.liberty.queueboardbe.repository.job;

import com.liberty.queueboardbe.model.job.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, Long> {

    List<JobAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId);

    Optional<JobAttempt> findFirstByJobIdOrderByAttemptNumberDesc(UUID jobId);

    /** Latest attempt of each given job, in one query (used to build list pages). */
    @Query("""
            select a from JobAttempt a
            where a.jobId in :jobIds
              and a.attemptNumber = (select max(b.attemptNumber) from JobAttempt b where b.jobId = a.jobId)""")
    List<JobAttempt> findLatestForJobs(@Param("jobIds") Collection<UUID> jobIds);
}
