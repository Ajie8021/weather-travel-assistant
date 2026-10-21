
package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.WeatherData;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 天气数据提供器实现。
 *
 * 将 TravelPlanTools 的天气查询请求转发给项目已有的 WeatherService，
 * 避免重复实现天气查询逻辑。
 */
@Component
public class WeatherDataProviderImpl implements WeatherDataProvider {

    private final WeatherService weatherService;

    public WeatherDataProviderImpl(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * 根据城市和日期查询天气数据。
     *
     * @param city 城市名称
     * @param date 出行日期
     * @return 查询到的天气数据
     */
    @Override
    public WeatherData getWeather(String city, LocalDate date) {
        return weatherService.getWeather(city, date);
    }
}
