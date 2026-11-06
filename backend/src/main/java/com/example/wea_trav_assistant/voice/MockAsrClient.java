package com.example.wea_trav_assistant.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock ASR：从 WAV 文件名/请求头无法拿到文本，这里用固定前缀 + 音频长度做确定性桩。
 * 仅用于无 Key 环境和单元测试，不用于生产。
 */
@Component
@ConditionalOnProperty(name = "voice.provider", havingValue = "mock", matchIfMissing = true)
public class MockAsrClient implements AsrClient {

    @Override
    public String transcribe(byte[] wav) {
        if (wav == null || wav.length == 0) {
            throw new AsrException("音频为空");
        }
        // 确定性桩：便于测试断言，不依赖真实识别
        return "明天广州天气怎么样";
    }
}