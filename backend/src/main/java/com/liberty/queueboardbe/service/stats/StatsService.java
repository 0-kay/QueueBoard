package com.liberty.queueboardbe.service.stats;

import com.liberty.queueboardbe.model.stats.StatsResponse;

/**
 * Dashboard metrics, excluding soft-deleted jobs.
 * <ul>
 *   <li>queue wait: job created to first attempt start</li>
 *   <li>run time: attempt start to finish, completed attempts only</li>
 *   <li>failure rate: FAILED jobs over finished jobs, split by the latest error's kind</li>
 *   <li>retry recovery: jobs that failed at least once and are now COMPLETED, over jobs that failed at least once</li>
 * </ul>
 * Rates are 0 when the denominator is 0; averages are 0 when there is no data.
 */
public interface StatsService {

    StatsResponse stats();
}
