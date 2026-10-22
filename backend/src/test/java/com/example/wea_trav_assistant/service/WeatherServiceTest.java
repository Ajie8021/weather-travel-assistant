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

    @Test
    void narrowsSameNameCandidatesWhenProvinceAndCountryAreProvided() {
        CityData shenzhenGuangdong = new CityData("深圳", "广东省", "中国", 22.54, 114.06, "Asia/Shanghai");
        CityData shenzhenHubei = new CityData("深圳", "湖北省", "中国", 30.50, 114.30, "Asia/Shanghai");
        when(client.searchCities("中国广东省深圳市"))
                .thenReturn(List.of(shenzhenGuangdong, shenzhenHubei));

        List<CityData> candidates = service.searchCityCandidates("中国广东省深圳市");

        assertEquals(List.of(shenzhenGuangdong), candidates);
    }

    @Test
    void keepsAllCandidatesWhenUserOnlyProvidesAnAmbiguousCityName() {
        CityData first = new CityData("Springfield", "Illinois", "United States", 39.78, -89.64, "America/Chicago");
        CityData second = new CityData("Springfield", "Massachusetts", "United States", 42.10, -72.59, "America/New_York");
        when(client.searchCities("Springfield")).thenReturn(List.of(first, second));

        assertEquals(List.of(first, second), service.searchCityCandidates("Springfield"));
    }

    @Test
    void mapsForecast() throws Exception {
        // 查询明天的天气，避免使用超出预报范围的日期
        LocalDate date = LocalDate.now().plusDays(1);

        // 模拟城市搜索结果
        when(client.searchCities("Beijing"))
                .thenReturn(List.of(beijing));

        // 模拟天气服务返回的 JSON 数据
        when(client.getForecast(any(), any())).thenReturn(
                new ObjectMapper().readTree("""
                    {
                      "daily": {
                        "time": ["%s"],
                        "weather_code": [3],
                        "temperature_2m_max": [20.5],
                        "temperature_2m_min": [12.1],
                        "precipitation_probability_max": [10],
                        "precipitation_sum": [0.0],
                        "wind_speed_10m_max": [15.2]
                      }
                    }
                    """.formatted(date))
        );

        // 调用天气查询服务
        WeatherData data = service.getWeather("Beijing", date);

        // 验证天气数据映射结果
        assertEquals(3, data.getWeatherCode());
        assertEquals(20.5, data.getMaxTemp());
        assertEquals(10, data.getPrecipitationProbability());
    }

    @Test
    void preservesMissingOptionalWeatherFieldsAsNull() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        // 模拟城市搜索成功
        when(client.searchCities("Beijing")).thenReturn(List.of(beijing));

        // 模拟天气接口只返回日期，不返回可选天气字段
        when(client.getForecast(any(), any())).thenReturn(
                new ObjectMapper().readTree("""
                    {"daily":{"time":["%s"]}}
                    """.formatted(date))
        );

        WeatherData data = service.getWeather("Beijing", date);

        // 未返回的可选字段应保持 null，而不是被错误地赋予默认值
        assertNull(data.getWeatherCode());
        assertNull(data.getMaxTemp());
        assertNull(data.getMinTemp());
        assertNull(data.getPrecipitationProbability());
        assertNull(data.getPrecipitation());
        assertNull(data.getWindSpeed());
    }
}
