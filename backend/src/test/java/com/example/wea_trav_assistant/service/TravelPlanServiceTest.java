
package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherDataIncompleteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TravelPlanService 单元测试。
 *
 * 测试直接构造天气数据，不请求真实天气 API，
 * 保证测试结果稳定、可重复。
 */
class TravelPlanServiceTest {

    private TravelPlanService travelPlanService;

    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        // 使用真实规则服务，验证规则分析与计划生成之间的协作。
        travelPlanService = new TravelPlanService(new TravelRuleService());
        targetDate = LocalDate.of(2026, 10, 20);
    }

    /**
     * 创建晴朗、温度适中、风速较低的天气数据。
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

    /**
     * 构造一个合法的用户出行请求。
     */
    private TravelRequest createRequest(String preference) {
        TravelRequest request = new TravelRequest();
        request.setCity("广州");
        request.setDate(targetDate);
        request.setActivityPreference(preference);
        return request;
    }

    @Test
    void outdoorPreferenceShouldRecommendOutdoorActivitiesWhenWeatherIsClear() {
        // 晴朗且没有已识别风险时，户外偏好应得到通用户外活动建议。
        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), createClearWeather());

        assertTrue(plan.getMorningActivity().contains("户外"));
        assertTrue(plan.getAfternoonActivity().contains("户外"));
        assertNotNull(plan.getEveningActivity());
    }

    @Test
    void indoorPreferenceShouldRecommendIndoorActivities() {
        // 室内偏好应在天气适宜时仍保持用户选择。
        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("INDOOR"), createClearWeather());

        assertTrue(plan.getMorningActivity().contains("室内"));
        assertTrue(plan.getAfternoonActivity().contains("室内"));
        assertTrue(plan.getEveningActivity().contains("室内"));
    }

    @Test
    void foodPreferenceShouldRecommendFoodExperience() {
        // 美食偏好应输出通用餐饮体验，而不是编造具体店铺。
        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("FOOD"), createClearWeather());

        assertTrue(plan.getMorningActivity().contains("美食"));
        assertTrue(plan.getAfternoonActivity().contains("美食"));
        assertTrue(plan.getEveningActivity().contains("美食"));
    }

    @Test
    void thunderstormShouldPreventOutdoorRecommendation() {
        // 雷暴属于严重天气，即使用户选择户外，也应调整为室内活动。
        WeatherData weather = createClearWeather();
        weather.setWeatherCode(95);

        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        assertTrue(plan.getMorningActivity().contains("室内"));
        assertTrue(plan.getAfternoonActivity().contains("室内"));
        assertFalse(plan.getWarnings().isEmpty());
    }

    @Test
    void rainRiskShouldIncludeRainRelatedAdvice() {
        // 降雨天气应在活动安排或物品清单中体现防雨措施。
        WeatherData weather = createClearWeather();
        weather.setWeatherCode(63);
        weather.setPrecipitationProbability(80);

        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        assertTrue(plan.getMorningActivity().contains("室内"));
        assertTrue(plan.getItemsToPack().stream()
                .anyMatch(item -> item.contains("雨伞")
                        || item.contains("防雨")));
        assertFalse(plan.getWarnings().isEmpty());
    }

    @Test
    void missingOptionalWeatherFieldsShouldNotCauseNullLists() {
        // 缺失可选字段时仍可利用已有数据生成有限计划，
        // 但清单字段必须非 null，且应提示信息不足。
        WeatherData weather = createClearWeather();
        weather.setWindSpeed(null);
        weather.setPrecipitationProbability(null);
        weather.setPrecipitation(null);

        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("INDOOR"), weather);

        assertNotNull(plan.getItemsToPack());
        assertNotNull(plan.getWarnings());
        // 验证缺少可选天气数据时，系统会给出相应提示
        assertTrue(plan.getWarnings().stream()
                        .anyMatch(warning -> warning.contains("缺少")
                                || warning.contains("缺失")
                                || warning.contains("不足")
                                || warning.contains("未知")),
                "缺少可选天气数据时，应在警告列表中提示用户");
    }

    @Test
    void planShouldContainAllRequiredFields() {
        // 验证计划 DTO 的契约字段均已生成。
        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), createClearWeather());

        // 基础结构检查
        assertEquals("广州", plan.getCity());
        assertEquals("2026-10-20", plan.getDate());
        assertNotNull(plan.getWeatherSummary());
        assertNotNull(plan.getMorningActivity());
        assertNotNull(plan.getAfternoonActivity());
        assertNotNull(plan.getEveningActivity());
        assertNotNull(plan.getClothingAdvice());
        assertNotNull(plan.getItemsToPack());
        assertNotNull(plan.getWarnings());

        // 针对空字符串的检测
        assertFalse(plan.getWeatherSummary().isBlank());
        assertFalse(plan.getMorningActivity().isBlank());
        assertFalse(plan.getAfternoonActivity().isBlank());
        assertFalse(plan.getEveningActivity().isBlank());
        assertFalse(plan.getClothingAdvice().isBlank());
    }

    @Test
    void mismatchedWeatherDateShouldBeRejected() {
        // 不得使用其他日期的天气生成计划。
        WeatherData weather = createClearWeather();
        weather.setDate(targetDate.plusDays(1));

        WeatherDataIncompleteException exception = assertThrows(
                WeatherDataIncompleteException.class,
                () -> travelPlanService.generatePlan(
                        createRequest("OUTDOOR"), weather));

        assertEquals("WEATHER_DATA_INCOMPLETE",
                exception.getErrorCode());
    }

    @Test
    void invalidPreferenceShouldBeRejected() {
        // 不接受契约之外的活动偏好值。
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanService.generatePlan(
                        createRequest("SHOPPING"), createClearWeather()));
    }

    @Test
    void unknownWeatherCodeShouldNotBeTreatedAsClear() {
        // 未知天气代码不能被当作晴朗天气，
        // 应提供保守的活动建议并提示用户核实天气。
        WeatherData weather = createClearWeather();
        weather.setWeatherCode(999);

        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        assertTrue(plan.getWeatherSummary().contains("未知"));

        assertTrue(plan.getMorningActivity().contains("室内"));
        assertTrue(plan.getAfternoonActivity().contains("室内"));
        assertTrue(plan.getEveningActivity().contains("室内"));

        assertFalse(plan.getWarnings().isEmpty());
    }

    @Test
    void multipleWeatherRisksShouldAdjustAllActivities() {
        // 同时出现雷暴、高温、低温、大风和高降雨概率，
        // 验证严重天气优先调整活动，同时保留风险提示。
        WeatherData weather = createClearWeather();
        weather.setWeatherCode(95);
        weather.setMaxTemp(35.0);
        weather.setMinTemp(5.0);
        weather.setWindSpeed(30.0);
        weather.setPrecipitationProbability(50);

        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        assertTrue(plan.getMorningActivity().contains("室内"));
        assertTrue(plan.getAfternoonActivity().contains("室内"));
        assertTrue(plan.getEveningActivity().contains("室内"));

        assertFalse(plan.getWarnings().isEmpty());
        assertFalse(plan.getItemsToPack().isEmpty());
    }

    @Test
    void foodPreferenceShouldRecommendIndoorDiningDuringThunderstorm() {
        // 雷暴时优先保障安全，美食偏好应转为室内餐饮建议。
        WeatherData weather = createClearWeather();
        weather.setWeatherCode(95);

        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("FOOD"), weather);

        assertTrue(plan.getMorningActivity().contains("室内"));
        assertTrue(plan.getMorningActivity().contains("美食"));

        assertTrue(plan.getAfternoonActivity().contains("室内"));
        assertTrue(plan.getAfternoonActivity().contains("美食"));

        assertTrue(plan.getEveningActivity().contains("室内"));
        assertTrue(plan.getEveningActivity().contains("美食"));
    }
}
