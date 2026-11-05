package com.example.wea_trav_assistant.dto;

/** Internal, provider-neutral representation of a real point of interest. */
public record AttractionDTO(
        String poiId,
        String name,
        String address,
        String city,
        String category,
        Double longitude,
        Double latitude,
        String rating,
        String openingHours,
        String source) {
}
