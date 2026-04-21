package com.twinmind.live.controller;

import com.twinmind.live.dto.TranscriptionResponse;
import com.twinmind.live.service.GroqService;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class TranscriptionController {

    private final GroqService groqService;

    public TranscriptionController(GroqService groqService) {
        this.groqService = groqService;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(
            @RequestPart("audio") MultipartFile audioFile,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        String text = groqService.transcribe(audioFile, authorizationHeader);
        return new TranscriptionResponse(text, Instant.now());
    }
}
