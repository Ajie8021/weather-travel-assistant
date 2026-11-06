package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.voice.VoiceChatResult;
import com.example.wea_trav_assistant.voice.VoiceChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller 契约测试：手动构建 MockMvc，不依赖 @WebMvcTest / @MockBean。
 * 这样不受 Spring Boot 测试自动配置包路径变化影响。
 */
class VoiceChatControllerTest {

    private MockMvc mockMvc;
    private VoiceChatService service;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(VoiceChatService.class);
        VoiceChatController controller = new VoiceChatController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void jsonMode_returnsMetadata() throws Exception {
        VoiceChatResult r = VoiceChatResult.ok("q", "a", "s1", new byte[]{1, 2});
        Mockito.when(service.chat(Mockito.any(), Mockito.eq("s1"))).thenReturn(r);

        MockMultipartFile file = new MockMultipartFile("file", "a.wav",
                "audio/wav", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/voice/chat")
                        .file(file)
                        .param("sessionId", "s1")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("OK"))
                .andExpect(jsonPath("$.data.hasAudio").value(true));
    }

    @Test
    void invalidAudio_returnsInvalidAudio() throws Exception {
        VoiceChatResult r = VoiceChatResult.fail("INVALID_AUDIO", "音频文件为空", "s1");
        Mockito.when(service.chat(Mockito.any(), Mockito.any())).thenReturn(r);

        MockMultipartFile file = new MockMultipartFile("file", "a.wav",
                "audio/wav", new byte[0]);

        mockMvc.perform(multipart("/api/voice/chat")
                        .file(file)
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVALID_AUDIO"));
    }
}