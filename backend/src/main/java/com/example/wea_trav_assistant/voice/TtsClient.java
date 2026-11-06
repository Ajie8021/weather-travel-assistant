package com.example.wea_trav_assistant.voice;

/** 语音合成接口。实现方不得返回空音频冒充成功。 */
public interface TtsClient {
    /**
     * @param text 待合成文本
     * @return WAV 字节；失败抛 TtsException
     */
    byte[] synthesize(String text);
}