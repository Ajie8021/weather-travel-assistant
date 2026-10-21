package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.config.AgentProperties;
import com.example.wea_trav_assistant.dto.ChatRequest;
import com.example.wea_trav_assistant.dto.ChatResponse;
import com.example.wea_trav_assistant.service.ChatSessionStore.Session;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {
    @Test
    void missingModelKeyReturnsConfigurationMessageWithoutCallingWeatherTools() {
        AgentProperties properties = new AgentProperties();
        ChatSessionStore sessions = new ChatSessionStore();
        WeatherService weatherService = mock(WeatherService.class);
        TravelPlanService planService = mock(TravelPlanService.class);
        McpToolClient mcpClient = mock(McpToolClient.class);
        AgentService service = new AgentService(properties, sessions, weatherService, planService,
                mcpClient, new ObjectMapper());

        ChatResponse response = service.chat(new ChatRequest(null, "查一下明天广州天气"));

        assertEquals("FAILED", response.status());
        assertTrue(response.reply().contains("DASHSCOPE_API_KEY"));
        assertNotNull(response.sessionId());
        assertTrue(response.toolCalls().isEmpty());
        verifyNoInteractions(weatherService, planService, mcpClient);
    }

    @Test
    void rejectsSessionIdsThatCouldCollideWithUntrustedInput() {
        AgentProperties properties = new AgentProperties();
        AgentService service = new AgentService(properties, new ChatSessionStore(),
                mock(WeatherService.class), mock(TravelPlanService.class),
                mock(McpToolClient.class), new ObjectMapper());

        ChatResponse response = service.chat(new ChatRequest("bad/session", "你好"));

        assertEquals("FAILED", response.status());
        assertEquals("会话标识无效，请重新开始对话。", response.reply());
    }
}
