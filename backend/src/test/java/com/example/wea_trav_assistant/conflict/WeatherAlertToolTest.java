package com.example.wea_trav_assistant.conflict;

import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具层测试：验证参数校验和调用链，不启动 Spring、不访问外部 API。
 */
class WeatherAlertToolTest {

    @Test
    void invalidDate_throwsIllegalArgument() {
        WeatherService ws = Mockito.mock(WeatherService.class);
        WeatherConflictService cs = new WeatherConflictService(new WeatherConflictProperties());
        WeatherAlertTool tool = new WeatherAlertTool(ws, cs);

        assertThrows(IllegalArgumentException.class,
                () -> tool.checkWeatherConflicts("广州", "2026/11/06", null));
    }

    @Test
    void blankCity_throwsIllegalArgument() {
        WeatherService ws = Mockito.mock(WeatherService.class);
        WeatherConflictService cs = new WeatherConflictService(new WeatherConflictProperties());
        WeatherAlertTool tool = new WeatherAlertTool(ws, cs);

        assertThrows(IllegalArgumentException.class,
                () -> tool.checkWeatherConflicts(" ", "2026-11-06", null));
    }

    @Test
    void validInput_delegatesToConflictService() {
        WeatherService ws = Mockito.mock(WeatherService.class);
        WeatherData w = new WeatherData();
        w.setCity("广州");
        w.setMaxTemp(36.0);
        w.setMinTemp(28.0);
        w.setPrecipitationProbability(10);
        w.setPrecipitation(0.0);
        w.setWindSpeed(10.0);
        w.setWeatherCode(0);
        Mockito.when(ws.getWeather("广州", LocalDate.of(2026, 11, 6))).thenReturn(w);

        WeatherConflictService cs = new WeatherConflictService(new WeatherConflictProperties());
        WeatherAlertTool tool = new WeatherAlertTool(ws, cs);

        List<WeatherConflictDTO> result = tool.checkWeatherConflicts("广州", "2026-11-06", null);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.HEAT));
    }
}