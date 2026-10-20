package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.ChatRequest;
import com.example.wea_trav_assistant.dto.ChatResponse;
import com.example.wea_trav_assistant.service.AgentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatControllerTest {
    @Test
    void delegatesChatRequestToAgentService() {
        AgentService service = mock(AgentService.class);
        ChatRequest request = new ChatRequest("session-one", "查明天天气");
        ChatResponse expected = new ChatResponse("session-one", "正在查询", "COMPLETED", List.of(), null, null);
        when(service.chat(request)).thenReturn(expected);
        ChatController controller = new ChatController(service);

        ChatResponse actual = controller.chat(request);

        assertSame(expected, actual);
        verify(service).chat(request);
    }
}
