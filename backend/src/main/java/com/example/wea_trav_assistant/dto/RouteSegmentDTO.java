package com.example.wea_trav_assistant.dto;

public record RouteSegmentDTO(
        String mode,
        String origin,
        String destination,
        Double distanceMeters,
        Integer durationSeconds,
        String line,
        String instruction) {
}
