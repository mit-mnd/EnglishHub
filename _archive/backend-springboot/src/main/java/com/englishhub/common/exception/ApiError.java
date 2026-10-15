package com.englishhub.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Format lỗi thống nhất cho toàn bộ API. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> errors) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), code.getStatus().value(), code.name(), message, path, List.of());
    }

    public static ApiError of(ErrorCode code, String message, String path, List<FieldViolation> errors) {
        return new ApiError(Instant.now(), code.getStatus().value(), code.name(), message, path, errors);
    }
}
