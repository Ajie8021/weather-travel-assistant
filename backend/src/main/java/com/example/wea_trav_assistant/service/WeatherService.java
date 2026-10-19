package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.client.OpenMeteoClient;
import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherErrorCode;
import com.example.wea_trav_assistant.exception.WeatherException;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class WeatherService {
    private final OpenMeteoClient client;
    public WeatherService(OpenMeteoClient client) { this.client = client; }

    public WeatherData getWeather(String city, LocalDate date) {
        if (city == null || city.isBlank() || date == null) throw new WeatherException(WeatherErrorCode.INVALID_INPUT, "请输入城市和有效日期");
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(15))) throw new WeatherException(WeatherErrorCode.OUT_OF_FORECAST_RANGE, "日期不在当前可查询的天气预报范围内");
        List<CityData> cities = client.searchCities(city.trim());
        if (cities.isEmpty()) throw new WeatherException(WeatherErrorCode.CITY_NOT_FOUND, "未找到匹配的城市，请补充地区后重试");
        List<CityData> exactNames = cities.stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(city.trim()))
                .toList();
        if (exactNames.size() != 1) throw new WeatherException(WeatherErrorCode.CITY_AMBIGUOUS, "找到多个同名城市，请补充省份或国家后重试");
        CityData selected = exactNames.getFirst();
        JsonNode daily = client.getForecast(selected, date).path("daily");
        JsonNode returnedDate = first(daily, "time");
        if (returnedDate == null || !date.toString().equals(returnedDate.asText())) throw new WeatherException(WeatherErrorCode.WEATHER_DATA_INCOMPLETE, "天气服务未返回所选日期的数据");
        return new WeatherData(selected.name(), selected.adminArea(), selected.country(), selected.latitude(), selected.longitude(), date,
                optionalInt(daily, "weather_code"), optionalDouble(daily, "temperature_2m_max"), optionalDouble(daily, "temperature_2m_min"),
                optionalInt(daily, "precipitation_probability_max"), optionalDouble(daily, "precipitation_sum"), optionalDouble(daily, "wind_speed_10m_max"), "Open-Meteo");
    }
    private JsonNode first(JsonNode daily, String field) { JsonNode a = daily.path(field); return a.isArray() && !a.isEmpty() ? a.get(0) : null; }
    private Double optionalDouble(JsonNode d, String f) { JsonNode v = first(d, f); return v != null && v.isNumber() && Double.isFinite(v.doubleValue()) ? v.doubleValue() : null; }
    private Integer optionalInt(JsonNode d, String f) { JsonNode v = first(d, f); return v != null && v.isIntegralNumber() ? v.intValue() : null; }
}
