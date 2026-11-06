package com.example.wea_trav_assistant.conflict;

import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.WeatherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * MCP 工具：check_weather_conflicts。
 *
 * 只读、无副作用。供 Agent 在生成/修改行程后调用。
 * 参数校验在服务端完成，不依赖模型自觉。
 * 工具只检测冲突，不修改行程。
 */
@Component
public class WeatherAlertTool {

    private final WeatherService weatherService;
    private final WeatherConflictService conflictService;

    public WeatherAlertTool(WeatherService weatherService, WeatherConflictService conflictService) {
        this.weatherService = weatherService;
        this.conflictService = conflictService;
    }

    @Tool(name = "check_weather_conflicts",
            description = "检测指定城市和日期的天气与行程冲突，返回冲突列表；不修改行程。")
    public List<WeatherConflictDTO> checkWeatherConflicts(
            @ToolParam(description = "已确认的城市名称") String city,
            @ToolParam(description = "目的地当地日期，格式 YYYY-MM-DD") String date,
            @ToolParam(description = "可选：当前行程，用于时段级冲突检测", required = false)
            TravelPlanDTO plan) {

        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("city 不能为空");
        }
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (Exception e) {
            throw new IllegalArgumentException("date 格式必须为 YYYY-MM-DD");
        }

        WeatherData weather = weatherService.getWeather(city, d);
        return conflictService.detect(weather, plan);
    }
}