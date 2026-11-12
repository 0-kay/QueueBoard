package com.liberty.queueboardbe;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * The full application with the poller running. A small delay scale makes jobs take a few
 * milliseconds, so the five worker threads genuinely overlap.
 */
@SpringBootTest(properties = "queueboard.simulation.delay-scale=0.01")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class WorkerTest {
}
