package com.twinmind.live.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChatRequest(
        @NotBlank String message,
        SuggestionDto selectedSuggestion,
        List<@Valid TranscriptChunkDto> transcript,
        List<@Valid ChatMessageDto> chatHistory,
        String chatPrompt,
        String detailedAnswerPrompt,
        Integer contextWindow
) {
}
