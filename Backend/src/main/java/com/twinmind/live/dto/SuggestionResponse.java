package com.twinmind.live.dto;

import java.time.Instant;
import java.util.List;

public record SuggestionResponse(
        List<SuggestionDto> suggestions,
        Instant createdAt
) {
}
