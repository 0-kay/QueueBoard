package com.liberty.queueboardbe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

@Configuration
@EnableScheduling
public class WorkerConfig {

    public static final String JOB_EXECUTOR = "jobExecutor";

    /**
     * Fixed-size pool. The poller only claims as many jobs as there are free permits, so the
     * executor's queue stays empty; the small capacity is a safety margin for the instant between a
     * task releasing its permit and its thread returning to the pool.
     */
    @Bean(name = JOB_EXECUTOR)
    public ThreadPoolTaskExecutor jobExecutor(QueueBoardProperties properties) {
        int threads = properties.worker().threads();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(threads);
        executor.setThreadNamePrefix("job-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
