package com.twinmind.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.twinmind.live.dto.ChatMessageDto;
import com.twinmind.live.exception.BadRequestException;
import com.twinmind.live.exception.GroqApiException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GroqService {

    public static final String TRANSCRIPTION_MODEL = "whisper-large-v3";
    public static final String CHAT_MODEL = "openai/gpt-oss-120b";

    private static final Duration TRANSCRIPTION_TIMEOUT = Duration.ofSeconds(75);
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(45);

    private final WebClient groqWebClient;
    private final ObjectMapper objectMapper;

    public GroqService(WebClient groqWebClient, ObjectMapper objectMapper) {
        this.groqWebClient = groqWebClient;
        this.objectMapper = objectMapper;
    }

    public String transcribe(MultipartFile audioFile, String authorizationHeader) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BadRequestException("Audio file is required.");
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("model", TRANSCRIPTION_MODEL);
        bodyBuilder.part("response_format", "json");
        bodyBuilder.part("file", audioResource(audioFile))
                .filename(resolveFilename(audioFile))
                .contentType(resolveContentType(audioFile));

        JsonNode response = groqWebClient.post()
                .uri("/audio/transcriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + extractToken(authorizationHeader))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toGroqException)
                .bodyToMono(JsonNode.class)
                .block(TRANSCRIPTION_TIMEOUT);

        String text = response == null ? "" : response.path("text").asText("").trim();
        if (!StringUtils.hasText(text)) {
            throw new GroqApiException("Groq returned an empty transcription.");
        }
        return text;
    }

    public String completeChat(
            List<ChatMessageDto> messages,
            String authorizationHeader,
            boolean jsonMode,
            double temperature,
            int maxCompletionTokens
    ) {
        if (messages == null || messages.isEmpty()) {
            throw new BadRequestException("At least one chat message is required.");
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", CHAT_MODEL);
        request.put("temperature", temperature);
        request.put("max_completion_tokens", maxCompletionTokens);
        request.put("reasoning_effort", "low");

        if (jsonMode) {
            ObjectNode responseFormat = objectMapper.createObjectNode();
            responseFormat.put("type", "json_object");
            request.set("response_format", responseFormat);
        }

        ArrayNode groqMessages = request.putArray("messages");
        for (ChatMessageDto message : messages) {
            if (message == null || !StringUtils.hasText(message.role()) || !StringUtils.hasText(message.content())) {
                throw new BadRequestException("Chat messages must include role and content.");
            }

            ObjectNode groqMessage = objectMapper.createObjectNode();
            groqMessage.put("role", message.role());
            groqMessage.put("content", message.content());
            groqMessages.add(groqMessage);
        }

        JsonNode response = groqWebClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + extractToken(authorizationHeader))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toGroqException)
                .bodyToMono(JsonNode.class)
                .block(CHAT_TIMEOUT);

        String content = response == null
                ? ""
                : response.path("choices").path(0).path("message").path("content").asText("").trim();

        if (!StringUtils.hasText(content)) {
            throw new GroqApiException("Groq returned an empty chat response.");
        }
        return content;
    }

    private Mono<? extends Throwable> toGroqException(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("No error details returned by Groq.")
                .map(body -> new GroqApiException("Groq returned HTTP "
                        + response.statusCode().value() + ": " + body));
    }

    private ByteArrayResource audioResource(MultipartFile audioFile) {
        try {
            byte[] bytes = audioFile.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return resolveFilename(audioFile);
                }
            };
        } catch (IOException exception) {
            throw new BadRequestException("Could not read uploaded audio file.");
        }
    }

    private MediaType resolveContentType(MultipartFile audioFile) {
        String contentType = audioFile.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(contentType);
    }

    private String resolveFilename(MultipartFile audioFile) {
        String filename = audioFile.getOriginalFilename();
        return StringUtils.hasText(filename) ? filename : "audio.webm";
    }

    private String extractToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            throw new BadRequestException("Missing Groq API key. Paste your key in Settings.");
        }

        String trimmed = authorizationHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmed.substring(7).trim();
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        if (trimmed.startsWith("gsk_")) {
            return trimmed;
        }

        throw new BadRequestException("Invalid Groq API key header. Use Authorization: Bearer <key>.");
    }
}
