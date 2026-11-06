package com.example.wea_trav_assistant.voice;

import com.example.wea_trav_assistant.dto.ChatRequest;
import com.example.wea_trav_assistant.dto.ChatResponse;
import com.example.wea_trav_assistant.service.AgentService;
import org.springframework.stereotype.Service;

/**
 * 语音交互编排：校验 → ASR → 现有 Agent → TTS。
 *
 * 约束：
 * 1) 识别文本进入现有 AgentService，不另写业务；
 * 2) AgentService 的公开入口是 chat(ChatRequest)，本类按 record 构造请求；
 * 3) 同一 sessionId 传给 Agent，保证与 Web 文本对话共享上下文；
 * 4) ASR 失败/空文本不进入 Agent；Agent 失败/空回复不进入 TTS；
 * 5) TTS 失败按 ttsFailureTolerant 降级，所有异常转为 VoiceChatResult，不抛给 Controller。
 */
@Service
public class VoiceChatService {

    private final AsrClient asrClient;
    private final TtsClient ttsClient;
    private final AgentService agentService;
    private final VoiceProperties props;

    public VoiceChatService(AsrClient asrClient, TtsClient ttsClient,
                            AgentService agentService, VoiceProperties props) {
        this.asrClient = asrClient;
        this.ttsClient = ttsClient;
        this.agentService = agentService;
        this.props = props;
    }

    /**
     * @param wav       上传的 WAV 字节
     * @param sessionId 会话 ID；为空时生成临时 ID（与 Web 共用同一 session 存储）
     */
    public VoiceChatResult chat(byte[] wav, String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank())
                ? "voice-" + java.util.UUID.randomUUID() : sessionId;

        // 1. 音频格式校验
        try {
            AudioValidator.validateWav(wav, props.getMaxAudioBytes(), props.isStrictWavCheck());
        } catch (IllegalArgumentException e) {
            return VoiceChatResult.fail("INVALID_AUDIO", e.getMessage(), sid);
        }

        // 2. ASR
        String question;
        try {
            question = asrClient.transcribe(wav);
        } catch (Exception e) {
            return VoiceChatResult.fail("ASR_FAILED", "语音识别失败：" + e.getMessage(), sid);
        }
        if (question == null || question.isBlank()) {
            return VoiceChatResult.fail("ASR_EMPTY", "未识别到有效文本，请重新说话", sid);
        }

        // 3. 复用现有 Agent：构造 ChatRequest(record)，同一 sessionId
        String answer;
        try {
            ChatRequest request = new ChatRequest(sid, question);
            ChatResponse response = agentService.chat(request);
            answer = response == null ? null : response.reply();
            if (response != null && "FAILED".equals(response.status())) {
                // Agent 明确失败时不再走 TTS，避免把错误提示合成语音
                return VoiceChatResult.fail("AGENT_FAILED",
                        answer == null ? "智能体处理失败" : answer, sid);
            }
        } catch (Exception e) {
            return VoiceChatResult.fail("AGENT_FAILED", "智能体处理失败：" + e.getMessage(), sid);
        }
        if (answer == null || answer.isBlank()) {
            return VoiceChatResult.fail("AGENT_FAILED", "智能体未返回有效回复", sid);
        }

        // 4. TTS
        try {
            byte[] audio = ttsClient.synthesize(answer);
            return VoiceChatResult.ok(question, answer, sid, audio);
        } catch (Exception e) {
            if (props.isTtsFailureTolerant()) {
                VoiceChatResult r = VoiceChatResult.ok(question, answer, sid, null);
                r.setStatus("TTS_FAILED");
                r.setErrorMessage("语音合成失败，已返回文本：" + e.getMessage());
                return r;
            }
            return VoiceChatResult.fail("TTS_FAILED", "语音合成失败：" + e.getMessage(), sid);
        }
    }
}