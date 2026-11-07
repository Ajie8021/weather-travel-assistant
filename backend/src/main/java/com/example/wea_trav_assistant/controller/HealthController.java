package com.example.wea_trav_assistant.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查端点。
 * 不检查外部 API（天气/高德/模型），只证明进程存活，避免平台因外部抖动误判。
 * 外部依赖状态由 /api/health/dependencies 单独暴露（可选）。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("timestamp", Instant.now().toString());
        return m;
    }
}