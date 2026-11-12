package com.liberty.queueboardbe.repository.job;

import com.liberty.queueboardbe.model.job.Job;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.job.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdAndDeletedAtIsNull(UUID id);

    /** Oldest QUEUED, non-deleted jobs first. The page size is the number of free worker permits. */
    @Query("""
            select j.id from Job j
            where j.status = com.liberty.queueboardbe.model.job.JobStatus.QUEUED and j.deletedAt is null
            order by j.createdAt asc, j.id asc""")
    List<UUID> findQueuedIds(Pageable pageable);

    /**
     * Atomic claim. Returns 1 only for the caller that won; 0 means another thread claimed it or it
     * was deleted. This is the only path from QUEUED to PROCESSING.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE job
            SET status = 'PROCESSING', attempts = attempts + 1,
                version = version + 1, updated_at = :now
            WHERE id = :id AND status = 'QUEUED' AND deleted_at IS NULL""")
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    /** Newest first. Null filters are ignored; {@code search} is a lower-cased LIKE pattern escaped with '!'. */
    @Query(value = """
            select j from Job j
            where j.deletedAt is null
              and (:status is null or j.status = :status)
              and (:type is null or j.type = :type)
              and (:search is null or lower(j.name) like :search escape '!')
            order by j.createdAt desc, j.id desc""",
            countQuery = """
            select count(j) from Job j
            where j.deletedAt is null
              and (:status is null or j.status = :status)
              and (:type is null or j.type = :type)
              and (:search is null or lower(j.name) like :search escape '!')""")
    Page<Job> search(@Param("status") JobStatus status,
                     @Param("type") JobType type,
                     @Param("search") String search,
                     Pageable pageable);
}
