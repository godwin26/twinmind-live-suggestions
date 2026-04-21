package com.twinmind.live.dto;

import jakarta.validation.constraints.NotBlank;

public record SuggestionDto(
        String id,
        @NotBlank String type,
        @NotBlank String title,
        @NotBlank String preview,
        String reason
) {
}
