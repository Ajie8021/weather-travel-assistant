package com.example.wea_trav_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PlanGenerateRequest(
        @NotBlank String city,
        @NotNull LocalDate date,
        @NotBlank String preference,
        Double budget,
        TransportMode transportPreference) {
}
