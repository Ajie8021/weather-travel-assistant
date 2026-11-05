package com.example.wea_trav_assistant.dto;

import java.util.List;

public record RouteDTO(
        String origin,
        String destination,
        String transportMode,
        Double distanceMeters,
        Integer durationSeconds,
        String summary,
        List<RouteSegmentDTO> segments,
        String source) {
}
