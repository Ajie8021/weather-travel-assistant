package com.example.wea_trav_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RoutePlanRequest(
        @NotBlank String originPoiId,
        @NotBlank String destinationPoiId,
        @NotEmpty List<TransportMode> transportModes) {
}
