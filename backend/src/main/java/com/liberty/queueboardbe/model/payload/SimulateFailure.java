package com.liberty.queueboardbe.model.payload;

/** Demo-only switch accepted in every payload when {@code queueboard.demo.enabled=true}. */
public enum SimulateFailure {
    TEMPORARY,
    PERMANENT
}
