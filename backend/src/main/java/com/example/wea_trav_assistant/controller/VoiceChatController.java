package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.common.ApiResponse;
import com.example.wea_trav_assistant.voice.VoiceChatResult;
import com.example.wea_trav_assistant.voice.VoiceChatService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音交互接口。
 *
 * POST /api/voice/chat
 * - multipart/form-data：file（WAV）、sessionId（可选）
 * - 默认返回 multipart/mixed：第一段 JSON 元数据，第二段 WAV 音频
 * - 加 ?format=json 或 Accept: application/json 时返回 JSON + Base64，便于 Postman 调试
 *
 * 不修改 /api/chat、/weather、/plan/generate。
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceChatController {

    private final VoiceChatService voiceChatService;

    public VoiceChatController(VoiceChatService voiceChatService) {
        this.voiceChatService = voiceChatService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> chat(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "sessionId", required = false) String sessionId,
                                  @RequestParam(value = "format", required = false) String format,
                                  @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) throws IOException {

        byte[] wav = file == null ? null : file.getBytes();
        VoiceChatResult result = voiceChatService.chat(wav, sessionId);

        boolean jsonMode = "json".equalsIgnoreCase(format)
                || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE));

        if (jsonMode) {
            return ResponseEntity.ok(ApiResponse.success(toMeta(result)));
        }
        return buildMultipart(result);
    }

    /** multipart/mixed：JSON 元数据 + 可选 WAV */
    private ResponseEntity<?> buildMultipart(VoiceChatResult result) {
        String boundary = "----voice-" + System.currentTimeMillis();
        org.springframework.util.LinkedMultiValueMap<String, Object> parts =
                new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("metadata", new org.springframework.http.HttpEntity<>(
                toMeta(result), jsonHeaders()));
        if (result.getAudio() != null && result.getAudio().length > 0) {
            org.springframework.http.HttpHeaders audioHeaders = new org.springframework.http.HttpHeaders();
            audioHeaders.setContentType(MediaType.parseMediaType("audio/wav"));
            parts.add("audio", new org.springframework.http.HttpEntity<>(result.getAudio(), audioHeaders));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("multipart/mixed;boundary=" + boundary))
                .body(parts);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** 统一元数据，不暴露音频二进制 */
    private Map<String, Object> toMeta(VoiceChatResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("question", r.getQuestion());
        m.put("answer", r.getAnswer());
        m.put("sessionId", r.getSessionId());
        m.put("audioFormat", r.getAudioFormat());
        m.put("hasAudio", r.getAudio() != null && r.getAudio().length > 0);
        m.put("errorMessage", r.getErrorMessage());
        if (r.getAudio() != null && r.getAudio().length > 0) {
            m.put("audioBase64", java.util.Base64.getEncoder().encodeToString(r.getAudio()));
        }
        return m;
    }
}