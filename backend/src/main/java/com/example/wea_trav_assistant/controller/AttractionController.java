package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.ApiResponse;
import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.exception.AmapException;
import com.example.wea_trav_assistant.service.PoiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attractions")
public class AttractionController {
    private final PoiService poiService;
    public AttractionController(PoiService poiService) { this.poiService = poiService; }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> search(
            @RequestParam String city, @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            List<AttractionDTO> attractions = poiService.searchAttractions(city, keyword, limit);
            return ResponseEntity.ok(ApiResponse.success(Map.of("city", city, "attractions", attractions)));
        } catch (AmapException error) {
            return error(error);
        }
    }

    @GetMapping("/{poiId}")
    public ResponseEntity<ApiResponse<?>> detail(@PathVariable String poiId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(poiService.getAttractionDetail(poiId)));
        } catch (AmapException error) {
            return ResponseEntity.status(status(error)).body(ApiResponse.error(status(error).value(), error.getMessage()));
        }
    }

    private ResponseEntity<ApiResponse<?>> error(AmapException error) {
        HttpStatus status = status(error);
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), error.getMessage()));
    }

    private HttpStatus status(AmapException error) {
        return switch (error.getCode()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case POI_NOT_FOUND, ROUTE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case API_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case API_KEY_MISSING, API_KEY_INVALID, API_QUOTA_EXCEEDED, API_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
