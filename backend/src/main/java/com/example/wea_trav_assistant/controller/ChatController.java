package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.ChatRequest;
import com.example.wea_trav_assistant.dto.ChatResponse;
import com.example.wea_trav_assistant.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@Validated
public class ChatController {
    private final AgentService agentService;

    public ChatController(AgentService agentService) { this.agentService = agentService; }

    @GetMapping("/chat")
    public String chatPage() { return "chat"; }

    @PostMapping("/api/chat")
    @ResponseBody
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
            MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> invalidRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "INVALID_INPUT",
                "message", "消息不能为空，且长度不能超过 2000 个字符。",
                "details", List.of()));
    }
}
