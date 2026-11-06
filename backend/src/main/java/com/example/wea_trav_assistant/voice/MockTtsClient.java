package com.example.wea_trav_assistant.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Mock TTS：返回一个最小合法 WAV 头 + 文本字节，便于端到端验证响应格式。
 * 仅用于无 Key 环境和单元测试。
 */
@Component
@ConditionalOnProperty(name = "voice.provider", havingValue = "mock", matchIfMissing = true)
public class MockTtsClient implements TtsClient {

    @Override
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) {
            throw new TtsException("待合成文本为空");
        }
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        return WavUtils.wrapPcmAsWav(payload);
    }
}