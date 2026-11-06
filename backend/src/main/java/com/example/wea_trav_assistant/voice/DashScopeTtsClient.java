package com.example.wea_trav_assistant.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DashScope TTS 适配占位。
 * 真实实现需调用 CosyVoice/Qwen TTS，并把输出转换为 16k/16bit/单声道 WAV。
 */
@Component
@ConditionalOnProperty(name = "voice.provider", havingValue = "dashscope")
public class DashScopeTtsClient implements TtsClient {

    @Override
    public byte[] synthesize(String text) {
        // TODO 按 DashScope 官方 TTS 文档实现；未实现前抛错，避免假成功
        throw new TtsException("DashScope TTS 尚未接入，请使用 voice.provider=mock 或完成真实实现");
    }
}