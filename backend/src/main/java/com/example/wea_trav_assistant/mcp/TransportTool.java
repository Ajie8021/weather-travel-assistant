package com.example.wea_trav_assistant.mcp;

import com.example.wea_trav_assistant.dto.RouteDTO;
import com.example.wea_trav_assistant.dto.TransportMode;
import com.example.wea_trav_assistant.service.RouteService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransportTool {
    private final RouteService routeService;
    public TransportTool(RouteService routeService) { this.routeService = routeService; }

    @McpTool(name = "plan_transport", description = "Plan real routes between two actual POI IDs. Use returned duration and distance, not model estimates.", generateOutputSchema = true)
    public List<RouteDTO> planTransport(
            @McpToolParam(description = "Actual origin POI ID", required = true) String originPoiId,
            @McpToolParam(description = "Actual destination POI ID", required = true) String destinationPoiId,
            @McpToolParam(description = "PUBLIC_TRANSPORT, DRIVING, WALKING or CYCLING", required = true) TransportMode transportPreference) {
        return routeService.planRoutes(originPoiId, destinationPoiId, List.of(transportPreference));
    }
}
