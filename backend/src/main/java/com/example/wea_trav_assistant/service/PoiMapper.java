package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.AttractionDTO;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Converts Amap's provider response into the application's stable DTO. */
public final class PoiMapper {
    private PoiMapper() { }

    public static List<AttractionDTO> attractions(JsonNode root) {
        List<AttractionDTO> result = new ArrayList<>();
        for (JsonNode poi : root.path("pois")) result.add(attraction(poi));
        if (result.isEmpty()) {
            for (JsonNode poi : root.path("data").path("pois")) result.add(attraction(poi));
        }
        return result;
    }

    public static AttractionDTO detail(JsonNode root) {
        JsonNode pois = root.path("pois");
        if (pois.isArray() && pois.size() > 0) return attraction(pois.get(0));
        JsonNode dataPois = root.path("data").path("pois");
        if (dataPois.isArray() && dataPois.size() > 0) return attraction(dataPois.get(0));
        return null;
    }

    private static AttractionDTO attraction(JsonNode poi) {
        double[] point = point(poi.path("location").asText(""));
        JsonNode business = poi.path("business");
        String rating = textOrNull(poi, "rating");
        if (rating == null) rating = textOrNull(business, "rating");
        String openingHours = textOrNull(poi, "open_time");
        if (openingHours == null) openingHours = textOrNull(business, "open_time");
        return new AttractionDTO(
                textOrNull(poi, "id"), textOrNull(poi, "name"), textOrNull(poi, "address"),
                firstText(poi, "cityname", "city"), firstText(poi, "type", "typecode"),
                point == null ? null : point[0], point == null ? null : point[1],
                rating, openingHours, "AMAP");
    }

    private static String firstText(JsonNode node, String first, String second) {
        String value = textOrNull(node, first);
        return value == null ? textOrNull(node, second) : value;
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() || "[]".equals(value) ? null : value;
    }

    private static double[] point(String location) {
        String[] values = location.split(",");
        if (values.length != 2) return null;
        try { return new double[]{Double.parseDouble(values[0]), Double.parseDouble(values[1])}; }
        catch (NumberFormatException ignored) { return null; }
    }
}
