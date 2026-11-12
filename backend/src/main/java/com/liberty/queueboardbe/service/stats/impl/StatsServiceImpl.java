package com.liberty.queueboardbe.service.stats.impl;

import com.liberty.queueboardbe.model.job.ErrorKind;
import com.liberty.queueboardbe.model.job.JobErrorCode;
import com.liberty.queueboardbe.model.job.JobStatus;
import com.liberty.queueboardbe.model.stats.StatsResponse;
import com.liberty.queueboardbe.repository.job.JobStatsRepository;
import com.liberty.queueboardbe.service.stats.StatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
public class StatsServiceImpl implements StatsService {

    private final JobStatsRepository stats;

    public StatsServiceImpl(JobStatsRepository stats) {
        this.stats = stats;
    }

    @Override
    @Transactional(readOnly = true)
    public StatsResponse stats() {
        Map<JobStatus, Long> byStatus = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            byStatus.put(status, 0L);
        }
        byStatus.putAll(stats.countByStatus());

        long finished = byStatus.get(JobStatus.COMPLETED) + byStatus.get(JobStatus.FAILED);
        long temporary = 0;
        long permanent = 0;
        for (Map.Entry<JobErrorCode, Long> entry : stats.failedJobsByLatestErrorCode().entrySet()) {
            if (entry.getKey().kind() == ErrorKind.TEMPORARY) {
                temporary += entry.getValue();
            } else {
                permanent += entry.getValue();
            }
        }
        long[] recovery = stats.retryRecoveryCounts();

        return new StatsResponse(
                byStatus,
                roundMs(stats.averageQueueWaitMs()),
                roundMs(stats.averageRunTimeMs()),
                new StatsResponse.FailureRate(
                        ratio(byStatus.get(JobStatus.FAILED), finished),
                        ratio(temporary, finished),
                        ratio(permanent, finished)),
                ratio(recovery[1], recovery[0]));
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(numerator * 10_000.0 / denominator) / 10_000.0;
    }

    private static long roundMs(Double value) {
        return value == null ? 0L : Math.round(value);
    }
}
