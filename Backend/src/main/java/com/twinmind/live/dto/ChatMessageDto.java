package com.twinmind.live.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record ChatMessageDto(
        @NotBlank String role,
        @NotBlank String content,
        Instant createdAt
) {
}
