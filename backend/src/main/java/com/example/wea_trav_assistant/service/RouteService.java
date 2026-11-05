package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.client.AmapClient;
import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.RouteDTO;
import com.example.wea_trav_assistant.dto.RouteSegmentDTO;
import com.example.wea_trav_assistant.dto.TransportMode;
import com.example.wea_trav_assistant.exception.AmapErrorCode;
import com.example.wea_trav_assistant.exception.AmapException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class RouteService {
    private final AmapClient client;
    private final PoiService poiService;

    public RouteService(AmapClient client, PoiService poiService) {
        this.client = client;
        this.poiService = poiService;
    }

    public List<RouteDTO> planRoutes(String originPoiId, String destinationPoiId, List<TransportMode> modes) {
        if (modes == null || modes.isEmpty()) throw new AmapException(AmapErrorCode.INVALID_INPUT, "至少选择一种交通方式");
        AttractionDTO origin = poiService.getAttractionDetail(originPoiId);
        AttractionDTO destination = poiService.getAttractionDetail(destinationPoiId);
        ensureCoordinates(origin, "起点");
        ensureCoordinates(destination, "终点");
        String city = origin.city() == null ? destination.city() : origin.city();
        List<RouteDTO> routes = new ArrayList<>();
        for (TransportMode mode : new LinkedHashSet<>(modes)) {
            routes.add(mapRoute(client.planRoute(mode, point(origin), point(destination), city), origin, destination, mode));
        }
        return routes;
    }

    private RouteDTO mapRoute(JsonNode root, AttractionDTO origin, AttractionDTO destination, TransportMode mode) {
        JsonNode route = root.path("route");
        JsonNode first = mode == TransportMode.PUBLIC_TRANSPORT ? route.path("transits").path(0) : route.path("paths").path(0);
        if (first.isMissingNode() || first.isNull()) throw new AmapException(AmapErrorCode.ROUTE_NOT_FOUND, "未找到可用路线");
        Double distance = number(first, "distance");
        Integer duration = integer(first, "duration");
        List<RouteSegmentDTO> segments = segments(first, mode);
        String summary = first.path("strategy").asText("").trim();
        if (summary.isEmpty()) summary = modeLabel(mode) + "路线（数据来自高德地图）";
        return new RouteDTO(origin.name(), destination.name(), mode.name(), distance, duration, summary, segments, "AMAP");
    }

    private List<RouteSegmentDTO> segments(JsonNode route, TransportMode mode) {
        List<RouteSegmentDTO> result = new ArrayList<>();
        JsonNode steps = route.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) result.add(segment(step, mode.name()));
        }
        JsonNode transitSegments = route.path("segments");
        if (transitSegments.isArray()) {
            for (JsonNode segment : transitSegments) result.add(segment(segment, "PUBLIC_TRANSPORT"));
        }
        return result;
    }

    private RouteSegmentDTO segment(JsonNode node, String defaultMode) {
        String line = node.path("bus").path("buslines").path(0).path("name").asText("");
        String instruction = node.path("instruction").asText("");
        if (instruction.isEmpty()) instruction = node.path("walking").path("destination").asText("");
        return new RouteSegmentDTO(defaultMode, node.path("road_name").asText(node.path("road").asText(null)), node.path("destination").asText(null),
                number(node, "distance") == null ? number(node, "step_distance") : number(node, "distance"), integer(node, "duration"), line.isEmpty() ? null : line,
                instruction.isEmpty() ? null : instruction);
    }

    private void ensureCoordinates(AttractionDTO attraction, String label) {
        if (attraction.longitude() == null || attraction.latitude() == null) {
            throw new AmapException(AmapErrorCode.ROUTE_NOT_FOUND, label + "缺少真实坐标，无法规划路线");
        }
    }
    private String point(AttractionDTO attraction) { return attraction.longitude() + "," + attraction.latitude(); }
    private Double number(JsonNode node, String field) {
        if (node.hasNonNull(field)) return node.path(field).asDouble();
        return node.path("cost").hasNonNull(field) ? node.path("cost").path(field).asDouble() : null;
    }
    private Integer integer(JsonNode node, String field) {
        if (node.hasNonNull(field)) return node.path(field).asInt();
        return node.path("cost").hasNonNull(field) ? node.path("cost").path(field).asInt() : null;
    }
    private String modeLabel(TransportMode mode) { return switch (mode) { case PUBLIC_TRANSPORT -> "公共交通"; case DRIVING -> "驾车"; case WALKING -> "步行"; case CYCLING -> "骑行"; }; }
}
