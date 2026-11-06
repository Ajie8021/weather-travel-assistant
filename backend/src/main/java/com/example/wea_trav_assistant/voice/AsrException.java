package com.example.wea_trav_assistant.voice;

/** ASR 失败，由服务层捕获并映射为错误码 */
public class AsrException extends RuntimeException {
    public AsrException(String message) { super(message); }
    public AsrException(String message, Throwable cause) { super(message, cause); }
}