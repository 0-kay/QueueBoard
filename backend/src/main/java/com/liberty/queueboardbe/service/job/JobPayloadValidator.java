package com.liberty.queueboardbe.service.job;

import com.liberty.queueboardbe.config.QueueBoardProperties;
import com.liberty.queueboardbe.exception.JobValidationException;
import com.liberty.queueboardbe.model.common.FieldError;
import com.liberty.queueboardbe.model.job.JobType;
import com.liberty.queueboardbe.model.payload.JobPayload;
import com.liberty.queueboardbe.service.processing.JobProcessor;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a submitted (type, payload) pair into a typed payload record, or a 400 listing every problem.
 * Order: type known, size, shape (unknown/wrong fields), required fields, demo flag, then the processor's
 * own checks (referenced codes exist).
 */
@Component
public class JobPayloadValidator {

    /** Matches the job.payload column. */
    public static final int MAX_PAYLOAD_BYTES = 4000;

    private final JobProcessor processor;
    private final Validator validator;
    private final JsonMapper strictMapper;
    private final boolean demoEnabled;

    public JobPayloadValidator(JobProcessor processor, Validator validator, JsonMapper jsonMapper,
                               QueueBoardProperties properties) {
        this.processor = processor;
        this.validator = validator;
        this.demoEnabled = properties.demo().enabled();
        // Jackson 3 ignores unknown properties by default; payloads must not.
        this.strictMapper = jsonMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
    }

    public record ValidatedPayload(JobType type, JobPayload payload, String json) {
    }

    public ValidatedPayload validate(String rawType, Map<String, Object> rawPayload) {
        List<FieldError> errors = new ArrayList<>();

        JobType type = parseType(rawType);
        if (type == null) {
            errors.add(new FieldError("type", "Unknown job type. Use one of " + Arrays.toString(JobType.values())));
        }
        if (rawPayload == null) {
            errors.add(new FieldError("payload", "Payload is required"));
        }
        if (!errors.isEmpty()) {
            throw new JobValidationException(errors);
        }

        String json = strictMapper.writeValueAsString(rawPayload);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new JobValidationException(List.of(
                    new FieldError("payload", "Payload must be at most " + MAX_PAYLOAD_BYTES + " bytes")));
        }

        JobPayload payload;
        try {
            payload = strictMapper.readValue(json, type.payloadType());
        } catch (JacksonException e) {
            throw new JobValidationException(List.of(toFieldError(e, type)));
        }

        validator.validate(payload).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(this::toFieldError)
                .forEach(errors::add);

        if (!demoEnabled && payload.simulateFailure() != null) {
            errors.add(new FieldError("payload.simulateFailure", "Simulated failures are disabled on this server"));
        }

        if (errors.isEmpty()) {
            processor.validate(payload, errors);
        }
        if (!errors.isEmpty()) {
            throw new JobValidationException(errors);
        }
        return new ValidatedPayload(type, payload, json);
    }

    /** Reads a payload stored at submit time. It was validated then, so this only fails on corruption. */
    public JobPayload parseStored(JobType type, String json) {
        return strictMapper.readValue(json, type.payloadType());
    }

    private static JobType parseType(String rawType) {
        if (rawType == null) {
            return null;
        }
        try {
            return JobType.valueOf(rawType.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private FieldError toFieldError(ConstraintViolation<JobPayload> violation) {
        return new FieldError("payload." + violation.getPropertyPath(), violation.getMessage());
    }

    private static FieldError toFieldError(JacksonException e, JobType type) {
        String path = e.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(name -> name != null)
                .collect(Collectors.joining("."));
        String field = path.isEmpty() ? "payload" : "payload." + path;

        if (e instanceof UnrecognizedPropertyException unknown) {
            return new FieldError("payload." + unknown.getPropertyName(), "Unknown field for " + type);
        }
        if (e instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                && invalid.getTargetType().isEnum()) {
            return new FieldError(field, "Must be one of " + Arrays.toString(invalid.getTargetType().getEnumConstants()));
        }
        if (e instanceof MismatchedInputException mismatch && mismatch.getTargetType() != null) {
            return new FieldError(field, "Wrong type, expected " + simpleTypeName(mismatch.getTargetType()));
        }
        return new FieldError(field, "Payload doesn't match " + type);
    }

    private static String simpleTypeName(Class<?> type) {
        if (type == String.class) {
            return "a string";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true or false";
        }
        if (type.isEnum()) {
            return "one of " + Arrays.toString(type.getEnumConstants());
        }
        return type.getSimpleName();
    }
}
