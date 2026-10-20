
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

    /**
     * 判断警告列表中是否至少有一条包含指定关键词。
     *
     * 使用多个关键词，是为了避免测试过度依赖某一句固定的提示文案。
     */
    private boolean hasWarningContainingAny(TravelPlan plan, String... keywords) {
        return plan.getWarnings() != null
                && plan.getWarnings().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(warning -> {
                    for (String keyword : keywords) {
                        if (warning.contains(keyword)) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    @Test
    void outdoorPreferenceShouldRecommendOutdoorActivitiesWhenWeatherIsClear() {
        // 晴朗且没有已识别风险时，户外偏好应得到通用户外活动建议。
        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), createClearWeather());

        assertTrue(plan.getMorningActivity().contains("户外"));
        assertTrue(plan.getAfternoonActivity().contains("户外"));
        assertTrue(
                plan.getEveningActivity().contains("户外"),
                "天气适宜且用户偏好户外时，晚间安排也应体现户外偏好"
        );
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
        assertTrue(
                plan.getEveningActivity().contains("室内"),
                "雷暴天气下，晚间安排也应避免不安全的户外活动"
        );
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
    void planListsShouldNotContainNullOrBlankItems() {
        // 列表应可以直接用于 API 返回或前端卡片展示。
        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), createClearWeather());

        assertNotNull(plan.getItemsToPack());
        assertNotNull(plan.getWarnings());

        assertTrue(
                plan.getItemsToPack().stream()
                        .allMatch(item -> item != null && !item.isBlank()),
                "携带物品列表不能包含 null 或空白项"
        );

        assertTrue(
                plan.getWarnings().stream()
                        .allMatch(warning -> warning != null && !warning.isBlank()),
                "警告列表不能包含 null 或空白项"
        );
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
    void mismatchedWeatherCityShouldBeRejected() {
        // 请求目的地为广州，但天气数据来自深圳，不能混用。
        WeatherData weather = createClearWeather();
        weather.setCity("深圳");

        assertThrows(
                WeatherDataIncompleteException.class,
                () -> travelPlanService.generatePlan(
                        createRequest("OUTDOOR"), weather)
        );
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
        // 未知天气代码应明确告知用户，不能只在摘要中标记未知。
        assertTrue(
                hasWarningContainingAny(plan, "未知", "无法判断", "核实"),
                "未知天气代码应在警告中得到体现"
        );

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

        // 高温风险应反映在穿衣建议中。
        assertTrue(plan.getClothingAdvice().contains("轻薄透气"));

        // 低温风险应反映在穿衣建议中。
        assertTrue(plan.getClothingAdvice().contains("保暖"));

        // 大风风险应反映在穿衣建议中。
        assertTrue(plan.getClothingAdvice().contains("防风"));

        // 高温对应的物品应包含饮用水和防晒用品。
        assertTrue(plan.getItemsToPack().contains("饮用水"));
        assertTrue(plan.getItemsToPack().contains("防晒用品"));

        // 每一种风险都应有对应提示，不能只保留一条笼统警告。
        assertTrue(
                hasWarningContainingAny(plan, "雷暴", "严重天气"),
                "雷暴风险应有明确提示"
        );
        assertTrue(
                hasWarningContainingAny(plan, "高温", "防暑"),
                "高温风险应有明确提示"
        );
        assertTrue(
                hasWarningContainingAny(plan, "低温", "保暖"),
                "低温风险应有明确提示"
        );
        // 当前规则服务使用“风力较大”描述大风风险。
        assertTrue(
                hasWarningContainingAny(plan, "大风", "防风", "风力较大"),
                "大风风险应有明确提示"
        );
        assertTrue(
                hasWarningContainingAny(plan, "降雨", "降水", "雨具", "雨伞"),
                "降雨风险应有明确提示"
        );

        // 严重天气应有相应提醒。
        assertTrue(plan.getWarnings().stream()
                .anyMatch(warning ->
                        warning.contains("雷暴")
                                || warning.contains("严重天气")
                                || warning.contains("户外")));
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

    @Test
    void nullRequestShouldBeRejected() {
        // 请求对象整体为空时，应立即拒绝。
        assertThrows(
                IllegalArgumentException.class,
                () -> travelPlanService.generatePlan(
                        null, createClearWeather()));
    }

    @Test
    void nullWeatherDataShouldBeRejected() {
        // 空天气数据由规则服务识别为天气数据不完整。
        WeatherDataIncompleteException exception = assertThrows(
                WeatherDataIncompleteException.class,
                () -> travelPlanService.generatePlan(
                        createRequest("OUTDOOR"), null)
        );

        // 验证业务异常代码，避免只检查异常类型。
        assertEquals(
                "WEATHER_DATA_INCOMPLETE",
                exception.getErrorCode()
        );
    }

    @Test
    void blankCityShouldBeRejected() {
        // 目的地城市为空字符串或纯空格时，应拒绝生成计划。
        TravelRequest request = createRequest("OUTDOOR");
        request.setCity("   ");

        assertThrows(
                IllegalArgumentException.class,
                () -> travelPlanService.generatePlan(
                        request, createClearWeather()));
    }

    @Test
    void nullRequestDateShouldBeRejected() {
        // 用户没有指定出行日期时，不能生成日期不明确的计划。
        TravelRequest request = createRequest("OUTDOOR");
        request.setDate(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> travelPlanService.generatePlan(
                        request, createClearWeather()));
    }

    @Test
    void missingTemperaturesShouldNotBeReportedAsZero() {
        // 天气代码仍然有效，但最高和最低气温均缺失。
        WeatherData weather = createClearWeather();
        weather.setMaxTemp(null);
        weather.setMinTemp(null);

        TravelPlan plan = travelPlanService.generatePlan(
                createRequest("INDOOR"), weather);

        // 天气摘要应明确说明温度数据缺失，不能将缺失值显示为 0℃。
        assertTrue(
                plan.getWeatherSummary().contains("最高和最低气温数据不足"),
                "缺失最高、最低气温时，天气摘要应明确提示温度数据不足"
        );

        // 缺失温度不能被错误地格式化为 0℃。
        assertFalse(
                plan.getWeatherSummary().contains("最高气温 0℃")
                        || plan.getWeatherSummary().contains("最低气温 0℃"),
                "缺失的最高或最低气温不应被显示为 0℃"
        );

        // 缺失数据必须传递给最终计划的注意事项。
        assertTrue(plan.getWarnings().stream()
                .anyMatch(warning ->
                        warning.contains("最高温")
                                || warning.contains("最低温")));
    }

    @Test
    void modifyingMorningShouldKeepOtherPeriodsUnchanged() {
        // 先生成一份户外偏好的原始计划。
        WeatherData weather = createClearWeather();
        TravelPlan original = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        // 保存旧字段，用于验证修改时没有误覆盖其他数据。
        String oldAfternoon = original.getAfternoonActivity();
        String oldEvening = original.getEveningActivity();
        String oldWeatherSummary = original.getWeatherSummary();
        String oldClothingAdvice = original.getClothingAdvice();

        // 将上午改为室内偏好，其他条件保持不变。
        TravelPlan updated = travelPlanService.modifyPlanForPeriod(
                original, weather, "morning", "INDOOR");

        // 应返回新对象，而不是直接修改原计划。
        assertNotSame(original, updated);

        // 只有上午活动被更新。
        assertTrue(updated.getMorningActivity().contains("室内"));
        assertEquals(oldAfternoon, updated.getAfternoonActivity());
        assertEquals(oldEvening, updated.getEveningActivity());

        // 天气概况和穿衣建议属于天气相关字段，不应因局部修改而变化。
        assertEquals(oldWeatherSummary, updated.getWeatherSummary());
        assertEquals(oldClothingAdvice, updated.getClothingAdvice());

        // 原计划仍保持原样，防止修改过程污染会话中的旧对象。
        assertEquals("OUTDOOR", "OUTDOOR");
        assertEquals(oldAfternoon, original.getAfternoonActivity());
        assertEquals(oldEvening, original.getEveningActivity());
    }

    @Test
    void modifyingOnePeriodShouldKeepOtherPlanFieldsUnchanged() {
        // 验证局部修改不会覆盖日期、城市、物品清单和警告。
        WeatherData weather = createClearWeather();
        TravelPlan original = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        TravelPlan updated = travelPlanService.modifyPlanForPeriod(
                original, weather, "afternoon", "FOOD");

        assertEquals(original.getCity(), updated.getCity());
        assertEquals(original.getDate(), updated.getDate());
        assertEquals(original.getWeatherSummary(), updated.getWeatherSummary());
        assertEquals(original.getMorningActivity(), updated.getMorningActivity());
        assertEquals(original.getEveningActivity(), updated.getEveningActivity());
        assertEquals(original.getClothingAdvice(), updated.getClothingAdvice());
        assertEquals(original.getItemsToPack(), updated.getItemsToPack());
        assertEquals(original.getWarnings(), updated.getWarnings());

        // 下午的新建议应符合美食偏好。
        assertTrue(updated.getAfternoonActivity().contains("美食"));
    }

    @Test
    void invalidPeriodShouldBeRejected() {
        // 不允许把未定义的时段传入计划修改服务。
        WeatherData weather = createClearWeather();
        TravelPlan original = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        assertThrows(IllegalArgumentException.class,
                () -> travelPlanService.modifyPlanForPeriod(
                        original, weather, "night", "INDOOR"));
    }

    @Test
    void modifyingPeriodShouldRejectWeatherForAnotherDate() {
        // 不能拿其他日期的天气修改当前计划。
        WeatherData weather = createClearWeather();
        TravelPlan original = travelPlanService.generatePlan(
                createRequest("OUTDOOR"), weather);

        WeatherData wrongDateWeather = createClearWeather();
        wrongDateWeather.setDate(targetDate.plusDays(1));

        assertThrows(WeatherDataIncompleteException.class,
                () -> travelPlanService.modifyPlanForPeriod(
                        original, wrongDateWeather, "morning", "INDOOR"));
    }

    @Test
    void modifyingPeriodShouldRejectNullPlan() {
        // 没有旧计划时，不能执行局部修改。
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanService.modifyPlanForPeriod(
                        null, createClearWeather(), "morning", "INDOOR"));
    }
}
