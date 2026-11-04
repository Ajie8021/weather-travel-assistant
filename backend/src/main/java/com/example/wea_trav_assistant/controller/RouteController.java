package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.ApiResponse;
import com.example.wea_trav_assistant.dto.RouteDTO;
import com.example.wea_trav_assistant.dto.RoutePlanRequest;
import com.example.wea_trav_assistant.exception.AmapException;
import com.example.wea_trav_assistant.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
    private final RouteService routeService;
    public RouteController(RouteService routeService) { this.routeService = routeService; }

    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<?>> plan(@Valid @RequestBody RoutePlanRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(Map.of("routes", routeService.planRoutes(
                    request.originPoiId(), request.destinationPoiId(), request.transportModes()))));
        } catch (AmapException error) {
            HttpStatus status = error.getCode() == com.example.wea_trav_assistant.exception.AmapErrorCode.INVALID_INPUT
                    ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(ApiResponse.error(status.value(), error.getMessage()));
        }
    }
}
