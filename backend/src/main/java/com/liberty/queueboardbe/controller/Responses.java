package com.liberty.queueboardbe.controller;

import com.liberty.queueboardbe.model.common.ApiResult;
import org.springframework.http.ResponseEntity;

/** Keeps the HTTP status and the body's status identical. */
final class Responses {

    private Responses() {
    }

    static <T> ResponseEntity<ApiResult<T>> of(ApiResult<T> result) {
        return ResponseEntity.status(result.status()).body(result);
    }
}
