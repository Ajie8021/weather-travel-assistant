package com.example.wea_trav_assistant.voice;

/**
 * 语音交互结果。
 * status: OK / ASR_EMPTY / ASR_FAILED / AGENT_FAILED / TTS_FAILED
 * 任何失败都不抛到 Controller 外部，由 Controller 映射为明确错误。
 */
public class VoiceChatResult {

    private String status;
    private String question;
    private String answer;
    private String sessionId;
    private byte[] audio;
    private String audioFormat;
    private String errorMessage;

    public static VoiceChatResult ok(String q, String a, String sid, byte[] audio) {
        VoiceChatResult r = new VoiceChatResult();
        r.status = "OK";
        r.question = q;
        r.answer = a;
        r.sessionId = sid;
        r.audio = audio;
        r.audioFormat = "wav";
        return r;
    }

    public static VoiceChatResult fail(String status, String message, String sid) {
        VoiceChatResult r = new VoiceChatResult();
        r.status = status;
        r.errorMessage = message;
        r.sessionId = sid;
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public byte[] getAudio() { return audio; }
    public void setAudio(byte[] audio) { this.audio = audio; }
    public String getAudioFormat() { return audioFormat; }
    public void setAudioFormat(String audioFormat) { this.audioFormat = audioFormat; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}