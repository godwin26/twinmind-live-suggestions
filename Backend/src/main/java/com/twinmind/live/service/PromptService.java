package com.twinmind.live.service;

import com.twinmind.live.dto.SuggestionDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PromptService {

    public static final String DEFAULT_LIVE_SUGGESTION_PROMPT = """
            You are TwinMind, an always-on AI meeting copilot.

            Your job is to produce exactly 3 useful live suggestions for the person wearing the mic.
            The suggestions must help them in the next few moments of the conversation.

            Requirements:
            - Return only valid JSON.
            - Return exactly 3 suggestions.
            - Each suggestion must be specific to the transcript, not generic meeting advice.
            - Each preview must be useful even if the user never clicks it.
            - The 3 suggestions should be meaningfully different from each other.
            - Choose the best mix for the moment: question_to_ask, talking_point, answer, fact_check, clarification, risk, next_step, summary.
            - If the transcript is thin, make the suggestions cautious and ask for clarification.
            - Do not invent facts. If a fact check is needed but uncertain, say what should be verified.

            JSON schema:
            {
              "suggestions": [
                {
                  "type": "question_to_ask",
                  "title": "Short action-oriented title",
                  "preview": "One or two concrete sentences the user can immediately use.",
                  "reason": "Why this is useful right now."
                }
              ]
            }
            """;

    public static final String DEFAULT_CHAT_PROMPT = """
            You are TwinMind, an AI meeting copilot answering questions during a live conversation.

            Use the transcript context as your source of truth. Be direct, practical, and specific.
            If the transcript does not contain enough information, say what is missing and give the best next question to ask.
            Do not invent details. Do not mention that you are using a prompt.
            """;

    public static final String DEFAULT_DETAILED_ANSWER_PROMPT = """
            You are TwinMind, expanding a clicked live suggestion into a useful answer for the user.

            Explain why the suggestion matters right now, give concrete language the user can say, and include any caveats.
            Ground the answer in the transcript. If something is uncertain, call it out clearly.
            Keep the answer concise enough to read during a meeting.
            """;

    public String buildLiveSuggestionSystemPrompt(String customPrompt) {
        return StringUtils.hasText(customPrompt) ? customPrompt : DEFAULT_LIVE_SUGGESTION_PROMPT;
    }

    public String buildLiveSuggestionUserPrompt(String transcriptContext, List<SuggestionDto> previousSuggestions) {
        StringBuilder builder = new StringBuilder();
        builder.append("Recent transcript context:\n");
        builder.append(StringUtils.hasText(transcriptContext) ? transcriptContext : "(No usable transcript yet.)");
        builder.append("\n\n");

        builder.append("Previous suggestions to avoid repeating:\n");
        if (previousSuggestions == null || previousSuggestions.isEmpty()) {
            builder.append("(None)");
        } else {
            previousSuggestions.stream()
                    .filter(suggestion -> suggestion != null && StringUtils.hasText(suggestion.preview()))
                    .limit(12)
                    .forEach(suggestion -> builder
                            .append("- ")
                            .append(suggestion.title())
                            .append(": ")
                            .append(suggestion.preview())
                            .append("\n"));
        }

        builder.append("\nReturn exactly 3 fresh suggestions as JSON.");
        return builder.toString();
    }

    public String buildChatSystemPrompt(String customPrompt, boolean suggestionClick) {
        if (StringUtils.hasText(customPrompt)) {
            return customPrompt;
        }
        return suggestionClick ? DEFAULT_DETAILED_ANSWER_PROMPT : DEFAULT_CHAT_PROMPT;
    }

    public String buildChatUserPrompt(String message, String transcriptContext, SuggestionDto selectedSuggestion) {
        StringBuilder builder = new StringBuilder();
        builder.append("Transcript context:\n");
        builder.append(StringUtils.hasText(transcriptContext) ? transcriptContext : "(No usable transcript context.)");
        builder.append("\n\n");

        if (selectedSuggestion != null) {
            builder.append("Clicked suggestion:\n");
            builder.append("Type: ").append(nullToEmpty(selectedSuggestion.type())).append("\n");
            builder.append("Title: ").append(nullToEmpty(selectedSuggestion.title())).append("\n");
            builder.append("Preview: ").append(nullToEmpty(selectedSuggestion.preview())).append("\n");
            builder.append("Reason: ").append(nullToEmpty(selectedSuggestion.reason())).append("\n\n");
            builder.append("Expand this suggestion into a detailed, useful answer.");
        } else {
            builder.append("User question:\n");
            builder.append(message);
        }

        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
