package com.twinmind.live.controller;

import com.twinmind.live.dto.ChatMessageDto;
import com.twinmind.live.dto.ChatRequest;
import com.twinmind.live.dto.ChatResponse;
import com.twinmind.live.dto.TranscriptChunkDto;
import com.twinmind.live.exception.BadRequestException;
import com.twinmind.live.service.ContextService;
import com.twinmind.live.service.GroqService;
import com.twinmind.live.service.PromptService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final GroqService groqService;
    private final ContextService contextService;
    private final PromptService promptService;

    public ChatController(
            GroqService groqService,
            ContextService contextService,
            PromptService promptService
    ) {
        this.groqService = groqService;
        this.contextService = contextService;
        this.promptService = promptService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        boolean suggestionClick = request.selectedSuggestion() != null;
        if (!suggestionClick && !StringUtils.hasText(request.message())) {
            throw new BadRequestException("Message is required for direct chat questions.");
        }

        List<TranscriptChunkDto> transcriptContext;
        if (suggestionClick) {
            transcriptContext = request.transcript() == null ? List.of() : request.transcript();
        } else {
            Integer contextWindow = request.contextWindow() == null
                    ? ContextService.DEFAULT_CHAT_CONTEXT_WINDOW
                    : request.contextWindow();
            transcriptContext = contextService.latestTranscriptChunks(
                    request.transcript(),
                    contextWindow
            );
        }
        String formattedTranscript = contextService.formatTranscript(transcriptContext);

        String systemPrompt = promptService.buildChatSystemPrompt(
                suggestionClick ? request.detailedAnswerPrompt() : request.chatPrompt(),
                suggestionClick
        );

        List<ChatMessageDto> messages = new ArrayList<>();
        messages.add(new ChatMessageDto("system", systemPrompt, Instant.now()));

        if (request.chatHistory() != null) {
            request.chatHistory().stream()
                    .filter(message -> message != null
                            && StringUtils.hasText(message.role())
                            && StringUtils.hasText(message.content()))
                    .limit(20)
                    .forEach(messages::add);
        }

        messages.add(new ChatMessageDto(
                "user",
                promptService.buildChatUserPrompt(
                        request.message(),
                        formattedTranscript,
                        request.selectedSuggestion()
                ),
                Instant.now()
        ));

        String content = groqService.completeChat(messages, authorizationHeader, false, 0.45, 1200);
        return new ChatResponse(new ChatMessageDto("assistant", content, Instant.now()));
    }
}
