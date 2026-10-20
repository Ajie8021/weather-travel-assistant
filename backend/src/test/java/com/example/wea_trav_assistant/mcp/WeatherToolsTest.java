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
        WeatherData weather = new WeatherData("Beijing", "Beijing", "China", 39.9, 116.4, date,
                3, 20.0, 10.0, 10, 0.0, 15.0, "Open-Meteo");
        when(weatherService.getWeather(city, date)).thenReturn(weather);

        assertEquals(weather, tools.getWeather(city, date.toString()));
        verify(weatherService).getWeather(city, date);
    }

    @Test
    void getWeatherRejectsAnInvalidDate() {
        assertThrows(WeatherException.class, () -> tools.getWeather(city, "20-10-2026"));
    }
}
