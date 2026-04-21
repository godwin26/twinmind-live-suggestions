package com.twinmind.live.dto;

import java.time.Instant;

public record TranscriptionResponse(
        String text,
        Instant createdAt
) {
}
