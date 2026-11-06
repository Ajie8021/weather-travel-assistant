package com.example.wea_trav_assistant.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DashScope ASR 适配占位。
 * 真实实现需调用 DashScope 语音识别接口，按官方文档上传 WAV 并解析文本。
 * 当前保留骨架，未配置 Key 时不启用；启用前必须完成真实联调，不得用假数据冒充。
 */
@Component
@ConditionalOnProperty(name = "voice.provider", havingValue = "dashscope")
public class DashScopeAsrClient implements AsrClient {

    @Override
    public String transcribe(byte[] wav) {
        // TODO 按 DashScope 官方 ASR 文档实现；未实现前抛错，避免假成功
        throw new AsrException("DashScope ASR 尚未接入，请使用 voice.provider=mock 或完成真实实现");
    }
}