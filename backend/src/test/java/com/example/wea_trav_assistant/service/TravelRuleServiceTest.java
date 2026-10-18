
package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherDataIncompleteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TravelRuleService 单元测试。
 *
 * 使用手动构造的天气数据，不依赖网络或外部天气服务，
 * 重点验证业务规则的边界条件和异常场景。
 */
class TravelRuleServiceTest {

    private TravelRuleService ruleService;

    private final LocalDate targetDate =
            LocalDate.of(2026, 10, 20);

    @BeforeEach
    void setUp() {
        ruleService = new TravelRuleService();
    }

    /**
     * 构造一份正常天气数据，其他测试在此基础上修改字段。
     */
    private WeatherData createNormalWeather() {
        WeatherData weather = new WeatherData();
        weather.setDate(targetDate);
        weather.setWeatherCode(0); // 晴朗
        weather.setMaxTemp(25.0);
        weather.setMinTemp(18.0);
        weather.setPrecipitationProbability(10);
        weather.setPrecipitation(0.0);
        weather.setWindSpeed(10.0);
        return weather;
    }

    @Test
    void shouldMapKnownWeatherCodes() {
        // 验证常见 WMO 天气代码能够映射到预期类型。
        assertEquals(
                TravelRuleService.WeatherCondition.CLEAR,
                ruleService.mapWeatherCode(0));

        assertEquals(
                TravelRuleService.WeatherCondition.CLOUDY,
                ruleService.mapWeatherCode(3));

        assertEquals(
                TravelRuleService.WeatherCondition.RAIN,
                ruleService.mapWeatherCode(63));

        assertEquals(
                TravelRuleService.WeatherCondition.SNOW,
                ruleService.mapWeatherCode(75));

        assertEquals(
                TravelRuleService.WeatherCondition.THUNDERSTORM,
                ruleService.mapWeatherCode(95));
    }

    @Test
    void shouldNotTreatUnknownCodeAsClear() {
        // 未知代码不能被默认解释为晴天。
        assertEquals(
                TravelRuleService.WeatherCondition.UNKNOWN,
                ruleService.mapWeatherCode(999));

        assertEquals(
                TravelRuleService.WeatherCondition.UNKNOWN,
                ruleService.mapWeatherCode(null));
    }

    @Test
    void shouldDetectHighTemperatureAtBoundary() {
        // 最高温度恰好达到 35°C，也应触发高温风险。
        WeatherData weather = createNormalWeather();
        weather.setMaxTemp(35.0);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_TEMPERATURE));
    }

    @Test
    void shouldDetectLowTemperatureAtBoundary() {
        // 最低温度恰好为 5°C，也应触发低温风险。
        WeatherData weather = createNormalWeather();
        weather.setMinTemp(5.0);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.LOW_TEMPERATURE));
    }

    @Test
    void shouldDetectHighWindAndHighPrecipitationProbability() {
        // 风速 30 km/h、降雨概率 50% 均属于触发边界。
        WeatherData weather = createNormalWeather();
        weather.setWindSpeed(30.0);
        weather.setPrecipitationProbability(50);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_WIND));

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_PRECIPITATION_PROBABILITY));
    }

    @Test
    void shouldKeepSevereAndOrdinaryRisksTogether() {
        // 雷暴、高温和大风同时出现时，不能只保留严重天气风险。
        WeatherData weather = createNormalWeather();
        weather.setWeatherCode(95);
        weather.setMaxTemp(36.0);
        weather.setWindSpeed(35.0);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.hasSevereWeather());

        assertTrue(result.needsOutdoorActivityAdjustment());

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.SEVERE_WEATHER));

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_TEMPERATURE));

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_WIND));
    }

    @Test
    void shouldAllowLimitedAnalysisWhenSomeCoreDataIsMissing() {
        // 缺少天气代码，但温度有效：允许有限分析，并说明信息不足。
        WeatherData weather = createNormalWeather();
        weather.setWeatherCode(null);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertEquals(
                TravelRuleService.WeatherCondition.UNKNOWN,
                result.getCondition());

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.UNKNOWN_WEATHER));

        assertFalse(result.getInformationNotes().isEmpty());
    }

    @Test
    void shouldRejectWhenAllCoreWeatherDataIsMissing() {
        // 天气代码、最高温度和最低温度全部缺失，必须返回业务错误。
        WeatherData weather = createNormalWeather();
        weather.setWeatherCode(null);
        weather.setMaxTemp(null);
        weather.setMinTemp(null);

        WeatherDataIncompleteException exception =
                assertThrows(
                        WeatherDataIncompleteException.class,
                        () -> ruleService.analyze(targetDate, weather));

        assertEquals(
                "WEATHER_DATA_INCOMPLETE",
                exception.getErrorCode());
    }

    @Test
    void shouldRejectMismatchedDate() {
        // 不允许用其他日期的天气数据生成当前日期的计划。
        WeatherData weather = createNormalWeather();
        weather.setDate(targetDate.plusDays(1));

        WeatherDataIncompleteException exception =
                assertThrows(
                        WeatherDataIncompleteException.class,
                        () -> ruleService.analyze(targetDate, weather));

        assertEquals(
                "WEATHER_DATA_INCOMPLETE",
                exception.getErrorCode());
    }

    @Test
    void shouldNotTreatMissingPrecipitationProbabilityAsZero() {
        // 降雨概率缺失时，不应认定为无降雨风险。
        WeatherData weather = createNormalWeather();
        weather.setPrecipitationProbability(null);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.getInformationNotes().stream()
                .anyMatch(note -> note.contains("降雨概率")));
    }

    @Test
    void shouldAllowNormalWeatherWithNoRisks() {
        // 正常天气不应报错，风险集合允许为空。
        WeatherData weather = createNormalWeather();

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertEquals(
                TravelRuleService.WeatherCondition.CLEAR,
                result.getCondition());

        assertTrue(result.getRisks().isEmpty());
        assertFalse(result.hasSevereWeather());
        assertFalse(result.needsOutdoorActivityAdjustment());
    }
}
