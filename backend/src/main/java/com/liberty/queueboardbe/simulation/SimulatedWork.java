package com.liberty.queueboardbe.simulation;

import com.liberty.queueboardbe.config.QueueBoardProperties;
import org.springframework.stereotype.Component;

/** Stands in for real processing time. Scaled by {@code queueboard.simulation.delay-scale} (0 in tests). */
@Component
public class SimulatedWork {

    private final double scale;

    public SimulatedWork(QueueBoardProperties properties) {
        this.scale = properties.simulation().delayScale();
    }

    /** Base delay plus a per-item cost, clamped to [min, max]. */
    public static long between(long min, long max, long perItem, int items) {
        return Math.max(min, Math.min(max, min + perItem * items));
    }

    public void pause(long millis) {
        long scaled = Math.round(millis * scale);
        if (scaled <= 0) {
            return;
        }
        try {
            Thread.sleep(scaled);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during simulated work", e);
        }
    }
}
