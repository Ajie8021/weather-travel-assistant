package com.example.wea_trav_assistant.mcp;

import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherErrorCode;
import com.example.wea_trav_assistant.exception.WeatherException;
import com.example.wea_trav_assistant.service.WeatherService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** MCP weather tools exposed to the Agent through a stateless HTTP server. */
@Component
public class WeatherTools {
    private final WeatherService weatherService;

    public WeatherTools(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @McpTool(
            name = "search_city",
            description = "Search Open-Meteo city candidates. Return candidates for the user or Agent to confirm; never choose an ambiguous city automatically.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
    public List<CityData> searchCity(
            @McpToolParam(description = "City name to search, for example Beijing or 上海", required = true) String city) {
        return weatherService.searchCityCandidates(city);
    }

    @McpTool(
            name = "get_weather",
            description = "Get daily weather for one user-confirmed location and one date. Do not call this for an unconfirmed ambiguous city.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = true))
    public WeatherData getWeather(
            @McpToolParam(description = "A location returned by search_city and confirmed by the user", required = true) CityData location,
            @McpToolParam(description = "Destination-local date in YYYY-MM-DD format", required = true) String date) {
        try {
            return weatherService.getWeather(location, LocalDate.parse(date));
        } catch (DateTimeParseException exception) {
            throw new WeatherException(WeatherErrorCode.INVALID_INPUT, "日期必须使用 YYYY-MM-DD 格式");
        }
    }
}
