package com.example.wea_trav_assistant.voice;

import com.example.wea_trav_assistant.dto.ChatRequest;
import com.example.wea_trav_assistant.dto.ChatResponse;
import com.example.wea_trav_assistant.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 语音编排测试：覆盖 ASR 失败/空文本、Agent 失败、TTS 失败降级、成功路径。
 * AgentService 按 chat(ChatRequest) mock，不启动 Spring、不访问外部服务。
 */
class VoiceChatServiceTest {

    private AsrClient asr;
    private TtsClient tts;
    private AgentService agent;
    private VoiceProperties props;
    private VoiceChatService service;

    @BeforeEach
    void setUp() {
        asr = Mockito.mock(AsrClient.class);
        tts = Mockito.mock(TtsClient.class);
        agent = Mockito.mock(AgentService.class);
        props = new VoiceProperties();
        props.setProvider("mock");
        props.setStrictWavCheck(false); // 测试用简化字节
        props.setTtsFailureTolerant(true);
        service = new VoiceChatService(asr, tts, agent, props);
    }

    /** 构造 Agent 正常回复 */
    private ChatResponse reply(String sessionId, String text) {
        return new ChatResponse(sessionId, text, "COMPLETED", List.of(), null, null);
    }

    /** 构造 Agent 失败回复 */
    private ChatResponse failed(String sessionId, String text) {
        return new ChatResponse(sessionId, text, "FAILED", List.of(), null, null);
    }

    @Test
    void success_returnsQuestionAnswerAudio() {
        Mockito.when(asr.transcribe(any())).thenReturn("明天广州天气");
        Mockito.when(agent.chat(any(ChatRequest.class))).thenReturn(reply("s1", "多云 28℃"));
        Mockito.when(tts.synthesize("多云 28℃")).thenReturn(new byte[]{1, 2, 3});

        VoiceChatResult r = service.chat(new byte[]{9}, "s1");

        assertEquals("OK", r.getStatus());
        assertEquals("明天广州天气", r.getQuestion());
        assertEquals("多云 28℃", r.getAnswer());
        assertArrayEquals(new byte[]{1, 2, 3}, r.getAudio());
    }

    @Test
    void sessionId_isPassedToAgent() {
        Mockito.when(asr.transcribe(any())).thenReturn("hi");
        Mockito.when(agent.chat(any(ChatRequest.class))).thenReturn(reply("esp32s3_user_001", "hello"));
        Mockito.when(tts.synthesize(any())).thenReturn(new byte[]{1});

        service.chat(new byte[]{9}, "esp32s3_user_001");

        // 验证传给 Agent 的 sessionId 与硬件端一致
        Mockito.verify(agent).chat(Mockito.argThat(req ->
                req != null && "esp32s3_user_001".equals(req.sessionId())
                        && "hi".equals(req.message())));
    }

    @Test
    void emptySessionId_generated() {
        Mockito.when(asr.transcribe(any())).thenReturn("hi");
        Mockito.when(agent.chat(any(ChatRequest.class))).thenReturn(reply("auto", "hello"));
        Mockito.when(tts.synthesize(any())).thenReturn(new byte[]{1});

        VoiceChatResult r = service.chat(new byte[]{9}, null);
        assertNotNull(r.getSessionId());
        assertTrue(r.getSessionId().startsWith("voice-"));
    }

    @Test
    void asrFailure_returnsAsrFailed_noAgentCall() {
        Mockito.when(asr.transcribe(any())).thenThrow(new AsrException("boom"));

        VoiceChatResult r = service.chat(new byte[]{9}, "s1");

        assertEquals("ASR_FAILED", r.getStatus());
        Mockito.verifyNoInteractions(agent);
    }

    @Test
    void asrEmpty_returnsAsrEmpty() {
        Mockito.when(asr.transcribe(any())).thenReturn("   ");
        VoiceChatResult r = service.chat(new byte[]{9}, "s1");
        assertEquals("ASR_EMPTY", r.getStatus());
    }

    @Test
    void agentFailure_returnsAgentFailed() {
        Mockito.when(asr.transcribe(any())).thenReturn("hi");
        Mockito.when(agent.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("model down"));

        VoiceChatResult r = service.chat(new byte[]{9}, "s1");
        assertEquals("AGENT_FAILED", r.getStatus());
    }

    @Test
    void agentReturnsFailedStatus_returnsAgentFailed_noTts() {
        Mockito.when(asr.transcribe(any())).thenReturn("hi");
        Mockito.when(agent.chat(any(ChatRequest.class)))
                .thenReturn(failed("s1", "模型鉴权失败"));

        VoiceChatResult r = service.chat(new byte[]{9}, "s1");

        assertEquals("AGENT_FAILED", r.getStatus());
        assertEquals("模型鉴权失败", r.getErrorMessage());
        Mockito.verifyNoInteractions(tts);
    }

    @Test
    void ttsFailure_tolerant_returnsTextWithFlag() {
        Mockito.when(asr.transcribe(any())).thenReturn("hi");
        Mockito.when(agent.chat(any(ChatRequest.class))).thenReturn(reply("s1", "hello"));
        Mockito.when(tts.synthesize("hello")).thenThrow(new TtsException("tts down"));

        VoiceChatResult r = service.chat(new byte[]{9}, "s1");

        assertEquals("TTS_FAILED", r.getStatus());
        assertEquals("hello", r.getAnswer());
        assertNull(r.getAudio());
        assertNotNull(r.getErrorMessage());
    }

    @Test
    void ttsFailure_notTolerant_failsWhole() {
        props.setTtsFailureTolerant(false);
        Mockito.when(asr.transcribe(any())).thenReturn("hi");
        Mockito.when(agent.chat(any(ChatRequest.class))).thenReturn(reply("s1", "hello"));
        Mockito.when(tts.synthesize("hello")).thenThrow(new TtsException("tts down"));

        VoiceChatResult r = service.chat(new byte[]{9}, "s1");
        assertEquals("TTS_FAILED", r.getStatus());
        assertNull(r.getAudio());
    }

    @Test
    void invalidAudio_returnsInvalidAudio() {
        props.setStrictWavCheck(true);
        VoiceChatResult r = service.chat(new byte[0], "s1");
        assertEquals("INVALID_AUDIO", r.getStatus());
        Mockito.verifyNoInteractions(asr);
    }
}