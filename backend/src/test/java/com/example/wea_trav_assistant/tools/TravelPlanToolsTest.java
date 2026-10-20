
package com.example.wea_trav_assistant.tools;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.TravelPlanService;
import com.example.wea_trav_assistant.service.TravelRuleService;
import com.example.wea_trav_assistant.service.WeatherDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TravelPlanTools 单元测试。
 *
 * 天气提供接口使用 Mockito 模拟，
 * 不请求真实天气 API，保证测试稳定且可重复。
 */
class TravelPlanToolsTest {

    private WeatherDataProvider weatherDataProvider;
    private TravelPlanService travelPlanService;
    private TravelPlanTools travelPlanTools;

    private final LocalDate targetDate =
            LocalDate.of(2026, 10, 20);

    @BeforeEach
    void setUp() {
        // 模拟天气服务，避免测试依赖公网和外部 API。
        weatherDataProvider = mock(WeatherDataProvider.class);

        // 使用真实规则服务和计划服务，
        // 验证工具入口与已有业务逻辑之间的协作。
        travelPlanService = new TravelPlanService(
                new TravelRuleService());

        travelPlanTools = new TravelPlanTools(
                weatherDataProvider, travelPlanService);

        // 配置测试用的晴朗天气。
        WeatherData weather = createClearWeather();

        // 当查询广州指定日期的天气时，返回上述测试数据。
        when(weatherDataProvider.getWeather("广州", targetDate))
                .thenReturn(weather);
    }

    /**
     * 创建晴朗、温度适中的测试天气。
     */
    private WeatherData createClearWeather() {
        WeatherData weather = new WeatherData();
        weather.setCity("广州");
        weather.setDate(targetDate);
        weather.setWeatherCode(0);
        weather.setMaxTemp(25.0);
        weather.setMinTemp(18.0);
        weather.setPrecipitationProbability(10);
        weather.setPrecipitation(0.0);
        weather.setWindSpeed(10.0);
        return weather;
    }

    @Test
    void shouldGeneratePlanAndQueryWeather() {
        // 验证工具能够完成天气查询到计划生成的完整调用链。
        TravelPlan plan = travelPlanTools.generateTravelPlan(
                "广州", "2026-10-20", "OUTDOOR");

        assertNotNull(plan);
        assertEquals("广州", plan.getCity());
        assertEquals("2026-10-20", plan.getDate());
        assertNotNull(plan.getWeatherSummary());
        assertNotNull(plan.getMorningActivity());
        assertNotNull(plan.getAfternoonActivity());
        assertNotNull(plan.getEveningActivity());

        // 验证工具确实按照用户输入调用天气提供接口。
        verify(weatherDataProvider, times(1))
                .getWeather("广州", targetDate);
    }

    @Test
    void shouldRejectBlankCity() {
        // 城市为空时应立即报错，不应请求天气服务。
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan(
                        "  ", "2026-10-20", "OUTDOOR"));

        verifyNoInteractions(weatherDataProvider);
    }

    @Test
    void shouldRejectBlankDate() {
        // 日期缺失时不能继续生成计划。
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan(
                        "广州", " ", "OUTDOOR"));

        verifyNoInteractions(weatherDataProvider);
    }

    @Test
    void shouldRejectInvalidDateFormat() {
        // 不接受 2026/10/20 这类非约定格式。
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan(
                        "广州", "2026/10/20", "OUTDOOR"));

        verifyNoInteractions(weatherDataProvider);
    }

    @Test
    void shouldRejectBlankActivityPreference() {
        // 活动偏好是必填项。
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan(
                        "广州", "2026-10-20", " "));

        verifyNoInteractions(weatherDataProvider);
    }

    @Test
    void shouldPropagateInvalidPreferenceFromBusinessService() {
        // 工具层不复制活动偏好的业务规则；
        // 非法值由 TravelPlanService 统一拒绝。
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan(
                        "广州", "2026-10-20", "SHOPPING"));
    }
}
