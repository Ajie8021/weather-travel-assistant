package com.example.wea_trav_assistant.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音模块配置。
 * provider=mock 时使用本地桩，便于无 Key 环境跑通链路和测试；
 * provider=dashscope 时使用真实 ASR/TTS（需要 DASHSCOPE_API_KEY）。
 */
@Component
@ConfigurationProperties(prefix = "voice")
public class VoiceProperties {

    /** mock | dashscope */
    private String provider = "mock";
    /** 上传大小上限（字节），默认 5MB */
    private long maxAudioBytes = 5 * 1024 * 1024L;
    /** 是否强制校验 WAV 头，测试可关 */
    private boolean strictWavCheck = true;
    /** TTS 失败时是否仍返回文本成功 */
    private boolean ttsFailureTolerant = true;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public long getMaxAudioBytes() { return maxAudioBytes; }
    public void setMaxAudioBytes(long maxAudioBytes) { this.maxAudioBytes = maxAudioBytes; }
    public boolean isStrictWavCheck() { return strictWavCheck; }
    public void setStrictWavCheck(boolean strictWavCheck) { this.strictWavCheck = strictWavCheck; }
    public boolean isTtsFailureTolerant() { return ttsFailureTolerant; }
    public void setTtsFailureTolerant(boolean ttsFailureTolerant) { this.ttsFailureTolerant = ttsFailureTolerant; }
}