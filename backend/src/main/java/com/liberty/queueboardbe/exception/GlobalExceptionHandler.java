package com.liberty.queueboardbe.exception;

import com.liberty.queueboardbe.model.common.ApiResult;
import com.liberty.queueboardbe.model.common.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every error leaves through {@link #wrap}, so no response has any shape other than ApiResult,
 * including Spring MVC's own errors (overridden {@code handleExceptionInternal}) and unexpected 500s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String FIX_FIELDS = "Some fields need attention.";

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(JobNotFoundException ex) {
        log.debug("404: {}", ex.getMessage());
        return wrap(HttpStatus.NOT_FOUND, ex.getMessage(), "We couldn't find that job. It may have been deleted.",
                List.of());
    }

    @ExceptionHandler(JobValidationException.class)
    public ResponseEntity<Object> handleJobValidation(JobValidationException ex) {
        log.debug("400: job rejected: {}", ex.getErrors());
        return wrap(HttpStatus.BAD_REQUEST, "Validation failed", FIX_FIELDS, ex.getErrors());
    }

    @ExceptionHandler(RetryNotAllowedException.class)
    public ResponseEntity<Object> handleRetryNotAllowed(RetryNotAllowedException ex) {
        return wrap(HttpStatus.CONFLICT, ex.getMessage(), ex.getDescription(), List.of());
    }

    @ExceptionHandler(IllegalJobTransitionException.class)
    public ResponseEntity<Object> handleIllegalTransition(IllegalJobTransitionException ex) {
        log.info("409: {}", ex.getMessage());
        return wrap(HttpStatus.CONFLICT, ex.getMessage(), "This action isn't allowed while the job is "
                + ex.getCurrentStatus().name().toLowerCase(Locale.ROOT) + ".", List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleVersionConflict(OptimisticLockingFailureException ex) {
        log.info("409: version conflict: {}", ex.getMessage());
        return wrap(HttpStatus.CONFLICT, "Version conflict: " + ex.getMessage(),
                "This job was changed at the same time by something else. Refresh and try again.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return wrap(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + ex.getClass().getSimpleName(),
                "Something went wrong on our side. Please try again.", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        log.debug("400: invalid request body: {}", ex.getBindingResult().getFieldErrors());
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.add(new FieldError(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.add(new FieldError(e.getObjectName(), e.getDefaultMessage())));
        return wrap(HttpStatus.BAD_REQUEST, "Validation failed", FIX_FIELDS, errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(e -> errors.add(new FieldError(e.getField(), e.getDefaultMessage())));
                return;
            }
            RequestParam requestParam = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
            String name = requestParam != null && !requestParam.name().isEmpty()
                    ? requestParam.name() : result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldError(name, error.getDefaultMessage()));
            }
        });
        return wrap(HttpStatus.BAD_REQUEST, "Validation failed", FIX_FIELDS, errors);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        Class<?> required = ex.getRequiredType();
        String message;
        if (required != null && required.isEnum()) {
            message = "Must be one of " + Arrays.toString(required.getEnumConstants());
        } else if (required != null && required.getSimpleName().equals("UUID")) {
            message = "Must be a UUID";
        } else {
            message = "Invalid value";
        }
        return wrap(HttpStatus.BAD_REQUEST, "Invalid value '" + ex.getValue() + "' for parameter '" + name + "'",
                FIX_FIELDS, List.of(new FieldError(name, message)));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return wrap(HttpStatus.BAD_REQUEST, "Malformed request body",
                "The request body isn't valid JSON or has the wrong shape.", List.of());
    }

    /** Catches every other Spring MVC exception (404 route, 405, 415, missing parameter, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        log.debug("{}: {}", statusCode.value(), ex.getMessage());
        ResponseEntity<Object> wrapped = wrap(statusCode, ex.getMessage(), describe(statusCode), List.of());
        return new ResponseEntity<>(wrapped.getBody(), headers, wrapped.getStatusCode());
    }

    private ResponseEntity<Object> wrap(HttpStatusCode status, String message, String description,
                                        List<FieldError> errors) {
        return ResponseEntity.status(status)
                .body(ApiResult.error(status.value(), message, description, errors));
    }

    private static String describe(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "The requested resource was not found.";
            case 405 -> "That action isn't supported here.";
            case 406, 415 -> "Please send and accept JSON.";
            case 400 -> "The request couldn't be processed. Please check it and try again.";
            default -> status.is5xxServerError()
                    ? "Something went wrong on our side. Please try again."
                    : "The request couldn't be processed.";
        };
    }
}
