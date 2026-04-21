package com.twinmind.live.dto;

import jakarta.validation.Valid;
import java.util.List;

public record SuggestionRequest(
        List<@Valid TranscriptChunkDto> transcript,
        List<@Valid SuggestionDto> previousSuggestions,
        String liveSuggestionPrompt,
        Integer contextWindow
) {
}
