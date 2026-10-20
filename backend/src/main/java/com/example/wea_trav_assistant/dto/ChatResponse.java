package com.example.wea_trav_assistant.dto;

import java.util.List;

public record ChatResponse(String sessionId, String reply, String status,
                           List<ToolCallResult> toolCalls,
                           WeatherData weather, TravelPlan travelPlan) {
    public record ToolCallResult(String name, String status, String detail) { }
}
