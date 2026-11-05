package com.example.wea_trav_assistant.dto;

/** A concrete scheduled attraction, shared with the weather-conflict module. */
public record ScheduleItemDTO(
        String period,
        String startTime,
        String endTime,
        AttractionDTO attraction,
        String activity) {
}
