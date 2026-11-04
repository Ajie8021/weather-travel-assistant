package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.ApiResponse;
import com.example.wea_trav_assistant.dto.PlanGenerateRequest;
import com.example.wea_trav_assistant.exception.AmapException;
import com.example.wea_trav_assistant.service.EnrichedTravelPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class EnrichedTravelPlanController {
    private final EnrichedTravelPlanService planService;
    public EnrichedTravelPlanController(EnrichedTravelPlanService planService) { this.planService = planService; }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<?>> generate(@Valid @RequestBody PlanGenerateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(planService.generate(request)));
        } catch (AmapException error) {
            HttpStatus status = error.getCode() == com.example.wea_trav_assistant.exception.AmapErrorCode.INVALID_INPUT
                    ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(ApiResponse.error(status.value(), error.getMessage()));
        }
    }
}
