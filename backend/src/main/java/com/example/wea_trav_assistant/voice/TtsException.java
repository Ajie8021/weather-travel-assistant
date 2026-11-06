package com.example.wea_trav_assistant.voice;

/** TTS 失败，由服务层捕获；是否降级由 VoiceProperties.ttsFailureTolerant 决定 */
public class TtsException extends RuntimeException {
    public TtsException(String message) { super(message); }
    public TtsException(String message, Throwable cause) { super(message, cause); }
}