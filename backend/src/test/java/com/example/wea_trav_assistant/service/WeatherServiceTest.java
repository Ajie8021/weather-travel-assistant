package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.client.OpenMeteoClient;
import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WeatherServiceTest {
    private final OpenMeteoClient client = mock(OpenMeteoClient.class);
    private final WeatherService service = new WeatherService(client);
    private final CityData beijing = new CityData("Beijing", "Beijing", "China", 39.9, 116.4, "Asia/Shanghai");

    @Test void rejectsBlankCity() {
        assertThrows(WeatherException.class, () -> service.getWeather(" ", LocalDate.now().plusDays(1)));
    }
    @Test void rejectsPastDate() {
        assertThrows(WeatherException.class, () -> service.getWeather("Beijing", LocalDate.now().minusDays(1)));
    }
    @Test void rejectsUnknownCity() {
        when(client.searchCities("NoSuchCity")).thenReturn(List.of());
        assertThrows(WeatherException.class, () -> service.getWeather("NoSuchCity", LocalDate.now().plusDays(1)));
    }
    @Test void rejectsAmbiguousCityInsteadOfChoosingTheFirstCandidate() {
        CityData anotherBeijing = new CityData("Beijing", "Hebei", "China", 40.0, 116.5, "Asia/Shanghai");
        when(client.searchCities("Beijing")).thenReturn(List.of(beijing, anotherBeijing));

        assertThrows(WeatherException.class, () -> service.getWeather("Beijing", LocalDate.now().plusDays(1)));
        verify(client, never()).getForecast(any(), any());
    }
    @Test void mapsForecast() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        when(client.searchCities("Beijing")).thenReturn(List.of(beijing));
        when(client.getForecast(any(), any())).thenReturn(new ObjectMapper().readTree("""
                {"daily":{"time":["%s"],"weather_code":[3],"temperature_2m_max":[20.5],"temperature_2m_min":[12.1],"precipitation_probability_max":[10],"precipitation_sum":[0.0],"wind_speed_10m_max":[15.2]}}
                """.formatted(date)));
        WeatherData data = service.getWeather("Beijing", date);
        assertEquals(3, data.weatherCode());
        assertEquals(20.5, data.maxTemp());
        assertEquals(10, data.precipitationProbability());
    }
    @Test void preservesMissingOptionalWeatherFieldsAsNull() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        when(client.searchCities("Beijing")).thenReturn(List.of(beijing));
        when(client.getForecast(any(), any())).thenReturn(new ObjectMapper().readTree("""
                {"daily":{"time":["%s"]}}
                """.formatted(date)));

        WeatherData data = service.getWeather("Beijing", date);

        assertNull(data.weatherCode());
        assertNull(data.maxTemp());
        assertNull(data.minTemp());
        assertNull(data.precipitationProbability());
        assertNull(data.precipitation());
        assertNull(data.windSpeed());
    }
}
