package com.twinmind.live.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record TranscriptChunkDto(
        String id,
        @NotBlank String text,
        Instant startedAt,
        Instant endedAt
) {
}
