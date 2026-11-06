package com.example.wea_trav_assistant.voice;

/** 语音识别接口。实现方不得编造识别文本。 */
public interface AsrClient {
    /**
     * @param wav 原始 WAV 字节
     * @return 识别文本；识别失败抛 AsrException
     */
    String transcribe(byte[] wav);
}