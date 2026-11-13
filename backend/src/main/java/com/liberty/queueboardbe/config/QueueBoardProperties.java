package com.liberty.queueboardbe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** All {@code queueboard.*} settings. Defaults match the requirements doc. */
@ConfigurationProperties(prefix = "queueboard")
public record QueueBoardProperties(
        @DefaultValue Worker worker,
        @DefaultValue Jobs jobs,
        @DefaultValue Demo demo,
        @DefaultValue Simulation simulation) {

    /**
     * @param enabled        run the poller; tests that drive handlers directly turn it off
     * @param threads        worker pool size
     * @param pollIntervalMs delay between poller runs
     */
    public record Worker(@DefaultValue("true") boolean enabled,
                         @DefaultValue("5") int threads,
                         @DefaultValue("500") long pollIntervalMs) {
    }

    /** @param maxAttempts total attempts per job (1 run plus retries) */
    public record Jobs(@DefaultValue("3") int maxAttempts) {
    }

    /** @param enabled allow {@code simulateFailure} in payloads */
    public record Demo(@DefaultValue("true") boolean enabled) {
    }

    /** @param delayScale multiplier for simulated work delays; 0 makes jobs instant (tests) */
    public record Simulation(@DefaultValue("1.0") double delayScale) {
    }
}
