package com.example.wea_trav_assistant.mcp;

import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherException;
import com.example.wea_trav_assistant.service.WeatherService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherToolsTest {
    private final WeatherService weatherService = mock(WeatherService.class);
    private final WeatherTools tools = new WeatherTools(weatherService);
    private final CityData city = new CityData("Beijing", "Beijing", "China", 39.9, 116.4, "Asia/Shanghai");

    @Test
    void searchCityReturnsCandidatesWithoutChoosingOne() {
        when(weatherService.searchCityCandidates("Beijing")).thenReturn(List.of(city));

        assertEquals(List.of(city), tools.searchCity("Beijing"));
        verify(weatherService).searchCityCandidates("Beijing");
    }

    @Test
    void getWeatherUsesConfirmedLocationAndParsesDate() {
        LocalDate date = LocalDate.now().plusDays(1);

        // WeatherData 使用无参构造器，通过 setter 设置属性
        WeatherData weather = new WeatherData();
        weather.setCity("Beijing");
        weather.setAdminArea("Beijing");
        weather.setCountry("China");
        weather.setLatitude(39.9);
        weather.setLongitude(116.4);
        weather.setDate(date);
        weather.setWeatherCode(3);
        weather.setMaxTemp(20.0);
        weather.setMinTemp(10.0);
        weather.setPrecipitationProbability(10);
        weather.setPrecipitation(0.0);
        weather.setWindSpeed(15.0);
        weather.setSource("Open-Meteo");

        // 模拟天气服务返回天气数据
        when(weatherService.getWeather(city, date)).thenReturn(weather);

        // 验证工具返回结果，并确认调用参数正确
        assertEquals(weather, tools.getWeather(city, date.toString()));
        verify(weatherService).getWeather(city, date);
    }

    @Test
    void getWeatherRejectsAnInvalidDate() {
        assertThrows(WeatherException.class, () -> tools.getWeather(city, "20-10-2026"));
    }
}
