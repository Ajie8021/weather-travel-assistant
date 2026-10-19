package com.example.wea_trav_assistant.client;

import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.exception.WeatherErrorCode;
import com.example.wea_trav_assistant.exception.WeatherException;
import tools.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenMeteoClient {
    private final RestClient geoClient;
    private final RestClient forecastClient;

    public OpenMeteoClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        geoClient = RestClient.builder().baseUrl("https://geocoding-api.open-meteo.com").requestFactory(factory).build();
        forecastClient = RestClient.builder().baseUrl("https://api.open-meteo.com").requestFactory(factory).build();
    }

    public List<CityData> searchCities(String name) {
        JsonNode root = geoClient.get().uri(b -> b.path("/v1/search")
                .queryParam("name", name).queryParam("count", 10).queryParam("language", "zh")
                .queryParam("format", "json").build()).retrieve().body(JsonNode.class);
        List<CityData> result = new ArrayList<>();
        if (root == null) return result;
        for (JsonNode node : root.path("results")) {
            if (node.hasNonNull("name") && node.hasNonNull("latitude") && node.hasNonNull("longitude")) {
                result.add(new CityData(node.path("name").asText(), node.path("admin1").asText(null),
                        node.path("country").asText(null), node.path("latitude").asDouble(),
                        node.path("longitude").asDouble(), node.path("timezone").asText("auto")));
            }
        }
        return result;
    }

    public JsonNode getForecast(CityData city, LocalDate date) {
        JsonNode root = forecastClient.get().uri(b -> b.path("/v1/forecast")
                .queryParam("latitude", city.latitude()).queryParam("longitude", city.longitude())
                .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max")
                .queryParam("timezone", city.timezone()).queryParam("start_date", date).queryParam("end_date", date)
                .build()).retrieve().body(JsonNode.class);
        if (root == null || !root.path("daily").isObject()) {
            throw new WeatherException(WeatherErrorCode.WEATHER_SERVICE_UNAVAILABLE, "天气服务未返回有效预报数据");
        }
        return root;
    }
}
