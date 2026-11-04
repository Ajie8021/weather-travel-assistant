package com.example.wea_trav_assistant.dto;

import java.util.List;

/** Provider-neutral enriched plan. WeatherConflictService can fill conflicts later. */
public record TravelPlanDTO(
        String city,
        String date,
        List<AttractionDTO> attractions,
        List<RouteDTO> routes,
        List<ScheduleItemDTO> schedule,
        Double budget,
        List<String> conflicts) {
}
