package com.twinmind.live.service;

import com.twinmind.live.dto.TranscriptChunkDto;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ContextService {

    public static final int DEFAULT_LIVE_CONTEXT_WINDOW = 8;
    public static final int DEFAULT_CHAT_CONTEXT_WINDOW = 20;

    public List<TranscriptChunkDto> latestTranscriptChunks(List<TranscriptChunkDto> transcript, Integer contextWindow) {
        if (transcript == null || transcript.isEmpty()) {
            return Collections.emptyList();
        }

        int resolvedWindow = contextWindow == null || contextWindow <= 0
                ? DEFAULT_LIVE_CONTEXT_WINDOW
                : contextWindow;

        int fromIndex = Math.max(0, transcript.size() - resolvedWindow);
        return transcript.subList(fromIndex, transcript.size());
    }

    public String formatTranscript(List<TranscriptChunkDto> transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (TranscriptChunkDto chunk : transcript) {
            if (chunk == null || chunk.text() == null || chunk.text().isBlank()) {
                continue;
            }

            String timestamp = chunk.endedAt() == null ? "unknown time" : chunk.endedAt().toString();
            builder.append("[").append(timestamp).append("] ")
                    .append(chunk.text().trim())
                    .append("\n");
        }
        return builder.toString().trim();
    }
}
