package com.example.wea_trav_assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(String sessionId,
                          @NotBlank @Size(max = 2000) String message) { }
