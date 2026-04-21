package com.twinmind.live.dto;

import java.time.Instant;

public record ApiErrorResponse(
        String error,
        String details,
        Instant timestamp
) {
    public static ApiErrorResponse of(String error, String details) {
        return new ApiErrorResponse(error, details, Instant.now());
    }
}
