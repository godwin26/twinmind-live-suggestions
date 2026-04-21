package com.twinmind.live.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twinmind.live.dto.ChatMessageDto;
import com.twinmind.live.dto.SuggestionDto;
import com.twinmind.live.dto.SuggestionRequest;
import com.twinmind.live.dto.SuggestionResponse;
import com.twinmind.live.dto.TranscriptChunkDto;
import com.twinmind.live.exception.BadRequestException;
import com.twinmind.live.exception.GroqApiException;
import com.twinmind.live.service.ContextService;
import com.twinmind.live.service.GroqService;
import com.twinmind.live.service.PromptService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api")
public class SuggestionsController {

    private final GroqService groqService;
    private final ContextService contextService;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public SuggestionsController(
            GroqService groqService,
            ContextService contextService,
            PromptService promptService,
            ObjectMapper objectMapper
    ) {
        this.groqService = groqService;
        this.contextService = contextService;
        this.promptService = promptService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/suggestions")
    public SuggestionResponse suggestions(
            @Valid @RequestBody SuggestionRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        List<TranscriptChunkDto> transcriptContext = contextService.latestTranscriptChunks(
                request.transcript(),
                request.contextWindow()
        );

        String formattedTranscript = contextService.formatTranscript(transcriptContext);
        if (!StringUtils.hasText(formattedTranscript)) {
            throw new BadRequestException("Transcript context is required before generating suggestions.");
        }

        List<ChatMessageDto> messages = List.of(
                new ChatMessageDto(
                        "system",
                        promptService.buildLiveSuggestionSystemPrompt(request.liveSuggestionPrompt()),
                        Instant.now()
                ),
                new ChatMessageDto(
                        "user",
                        promptService.buildLiveSuggestionUserPrompt(
                                formattedTranscript,
                                request.previousSuggestions()
                        ),
                        Instant.now()
                )
        );

        String content = groqService.completeChat(messages, authorizationHeader, true, 0.35, 900);
        return new SuggestionResponse(parseSuggestions(content), Instant.now());
    }

    private List<SuggestionDto> parseSuggestions(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode suggestionsNode = root.path("suggestions");
            if (!suggestionsNode.isArray() || suggestionsNode.size() != 3) {
                throw new GroqApiException("Suggestion response must include exactly 3 suggestions.");
            }

            List<SuggestionDto> suggestions = new ArrayList<>();
            for (JsonNode node : suggestionsNode) {
                String type = node.path("type").asText("").trim();
                String title = node.path("title").asText("").trim();
                String preview = node.path("preview").asText("").trim();
                String reason = node.path("reason").asText("").trim();

                if (!StringUtils.hasText(type) || !StringUtils.hasText(title) || !StringUtils.hasText(preview)) {
                    throw new GroqApiException("Each suggestion must include type, title, and preview.");
                }

                suggestions.add(new SuggestionDto(
                        UUID.randomUUID().toString(),
                        type,
                        title,
                        preview,
                        reason
                ));
            }

            return suggestions;
        } catch (GroqApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GroqApiException("Could not parse suggestion JSON returned by Groq.", exception);
        }
    }
}
