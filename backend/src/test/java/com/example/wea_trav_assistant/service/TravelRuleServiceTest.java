
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
    void shouldKeepAllRisksWhenMultipleRisksOccur() {
        // 同时触发雷暴、高温、低温、大风和高降雨概率。
        WeatherData weather = createNormalWeather();
        weather.setWeatherCode(95);
        weather.setMaxTemp(35.0);
        weather.setMinTemp(5.0);
        weather.setWindSpeed(30.0);
        weather.setPrecipitationProbability(50);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.hasSevereWeather());
        assertTrue(result.needsOutdoorActivityAdjustment());

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.SEVERE_WEATHER));
        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_TEMPERATURE));
        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.LOW_TEMPERATURE));
        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_WIND));
        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.HIGH_PRECIPITATION_PROBABILITY));
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
    void shouldRejectNullWeatherDate() {
        // 天气数据的日期缺失时，无法确认天气是否对应用户选择的出行日期。
        WeatherData weather = createNormalWeather();
        weather.setDate(null);

        WeatherDataIncompleteException exception = assertThrows(
                WeatherDataIncompleteException.class,
                () -> ruleService.analyze(targetDate, weather));

        // 验证返回的业务错误码符合接口契约。
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

    @Test
    void shouldAllowLimitedAnalysisWhenBothTemperaturesAreMissing() {
        // 天气代码有效，但最高温和最低温缺失时，仍允许进行有限分析。
        WeatherData weather = createNormalWeather();
        weather.setMaxTemp(null);
        weather.setMinTemp(null);

        TravelRuleService.WeatherAnalysis result =
                assertDoesNotThrow(
                        () -> ruleService.analyze(targetDate, weather));

        // 天气代码仍然有效，因此天气类型应保持为晴朗。
        assertEquals(
                TravelRuleService.WeatherCondition.CLEAR,
                result.getCondition());

        // 温度缺失必须有信息提示，不能将缺失温度当作 0°C。
        assertTrue(result.getInformationNotes().stream()
                .anyMatch(note -> note.contains("最高温")));

        assertTrue(result.getInformationNotes().stream()
                .anyMatch(note -> note.contains("最低温")));
    }

    @Test
    void shouldKeepUnknownConditionWhenCodeIsUnknownButTemperaturesAreValid() {
        // 未知天气代码不能因温度有效而被错误地解释为晴朗。
        WeatherData weather = createNormalWeather();
        weather.setWeatherCode(999);
        weather.setMaxTemp(25.0);
        weather.setMinTemp(18.0);

        TravelRuleService.WeatherAnalysis result =
                assertDoesNotThrow(
                        () -> ruleService.analyze(targetDate, weather));

        assertEquals(
                TravelRuleService.WeatherCondition.UNKNOWN,
                result.getCondition());

        assertTrue(result.getRisks().contains(
                TravelRuleService.WeatherRisk.UNKNOWN_WEATHER));

        // 未知代码应通过信息提示明确告知用户。
        assertTrue(result.getInformationNotes().stream()
                .anyMatch(note -> note.contains("未知")));
    }

    @Test
    void shouldRejectWeatherWithNullDate() {
        // 天气数据没有日期时，无法确认其对应用户选择的出行日期。
        WeatherData weather = createNormalWeather();
        weather.setDate(null);

        WeatherDataIncompleteException exception = assertThrows(
                WeatherDataIncompleteException.class,
                () -> ruleService.analyze(targetDate, weather));

        assertEquals(
                "WEATHER_DATA_INCOMPLETE",
                exception.getErrorCode());
    }

    @Test
    void shouldReportMissingWindSpeed() {
        // 风速缺失时，应提示信息不足，而不是将风速当作 0。
        WeatherData weather = createNormalWeather();
        weather.setWindSpeed(null);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.getInformationNotes().stream()
                .anyMatch(note -> note.contains("风速")));
    }

    @Test
    void shouldReportMissingPrecipitationAmount() {
        // 降水量缺失时，应提示信息不足，而不是将降水量当作 0。
        WeatherData weather = createNormalWeather();
        weather.setPrecipitation(null);

        TravelRuleService.WeatherAnalysis result =
                ruleService.analyze(targetDate, weather);

        assertTrue(result.getInformationNotes().stream()
                .anyMatch(note -> note.contains("降水量")
                        || note.contains("降水")));
    }
}
