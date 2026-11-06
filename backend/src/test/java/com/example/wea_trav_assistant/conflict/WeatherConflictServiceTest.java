package com.example.wea_trav_assistant.conflict;

import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import com.example.wea_trav_assistant.dto.WeatherData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 冲突服务单元测试。
 * DTO 为 record，使用构造器创建；不依赖网络、不启动 Spring。
 */
class WeatherConflictServiceTest {

    private WeatherConflictService service;

    @BeforeEach
    void setUp() {
        WeatherConflictProperties props = new WeatherConflictProperties();
        service = new WeatherConflictService(props);
    }

    // ---------- 构造工具 ----------

    private WeatherData weather(Integer code, Double max, Double min,
                                Integer rainProb, Double rain, Double wind) {
        WeatherData w = new WeatherData();
        w.setCity("广州");
        w.setDate(LocalDate.of(2026, 11, 6));
        w.setWeatherCode(code);
        w.setMaxTemp(max);
        w.setMinTemp(min);
        w.setPrecipitationProbability(rainProb);
        w.setPrecipitation(rain);
        w.setWindSpeed(wind);
        w.setSource("Open-Meteo");
        return w;
    }

    private AttractionDTO attraction(String name, String category, String openingHours) {
        return new AttractionDTO(
                "poi-1", name, "地址", "广州", category,
                113.0, 23.0, null, openingHours, "AMAP");
    }

    private ScheduleItemDTO item(String period, String activity,
                                 AttractionDTO attraction, String start, String end) {
        return new ScheduleItemDTO(period, start, end, attraction, activity);
    }

    private TravelPlanDTO planWith(ScheduleItemDTO... items) {
        List<ScheduleItemDTO> schedule = new ArrayList<>();
        for (ScheduleItemDTO i : items) schedule.add(i);
        return new TravelPlanDTO(
                "广州", "2026-11-06",
                new ArrayList<>(), new ArrayList<>(),
                schedule, null, new ArrayList<>());
    }

    // ---------- 用例 ----------

    @Test
    void nullWeather_returnsDataIncomplete_notNoRisk() {
        List<WeatherConflictDTO> result = service.detect(null, null);
        assertEquals(1, result.size());
        assertEquals(ConflictType.DATA_INCOMPLETE, result.get(0).getType());
        assertEquals(ConflictSeverity.WARN, result.get(0).getSeverity());
    }

    @Test
    void missingKeyFields_returnsDataIncomplete() {
        WeatherData w = weather(null, null, null, null, null, null);
        List<WeatherConflictDTO> result = service.detect(w, null);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.DATA_INCOMPLETE));
        assertFalse(result.isEmpty());
    }

    @Test
    void highTemp_triggersHeat() {
        WeatherData w = weather(0, 36.0, 28.0, 10, 0.0, 10.0);
        List<WeatherConflictDTO> result = service.detect(w, null);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.HEAT));
    }

    @Test
    void lowTemp_triggersCold() {
        WeatherData w = weather(0, 8.0, 4.0, 10, 0.0, 10.0);
        List<WeatherConflictDTO> result = service.detect(w, null);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.COLD));
    }

    @Test
    void highWind_triggersWind() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 35.0);
        List<WeatherConflictDTO> result = service.detect(w, null);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.WIND));
    }

    @Test
    void rainByProbability_triggersRain() {
        WeatherData w = weather(61, 25.0, 20.0, 60, 0.0, 10.0);
        List<WeatherConflictDTO> result = service.detect(w, null);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.RAIN));
    }

    @Test
    void thunderstormCode_triggersHighThunderstorm() {
        WeatherData w = weather(95, 25.0, 20.0, 80, 10.0, 20.0);
        List<WeatherConflictDTO> result = service.detect(w, null);
        WeatherConflictDTO t = result.stream()
                .filter(c -> c.getType() == ConflictType.THUNDERSTORM)
                .findFirst().orElse(null);
        assertNotNull(t);
        assertEquals(ConflictSeverity.HIGH, t.getSeverity());
    }

    @Test
    void multipleRisks_allReported_noContradiction() {
        WeatherData w = weather(61, 36.0, 28.0, 70, 5.0, 35.0);
        List<WeatherConflictDTO> result = service.detect(w, null);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.HEAT));
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.RAIN));
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.WIND));
    }

    @Test
    void outdoorAttractionWithRain_createsPeriodConflict() {
        WeatherData w = weather(61, 25.0, 20.0, 70, 2.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("AFTERNOON", "游览",
                        attraction("测试公园", "公园", null), "14:00", "16:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertTrue(result.stream().anyMatch(c ->
                c.getType() == ConflictType.RAIN && "AFTERNOON".equals(c.getPeriod())));
    }

    @Test
    void indoorAttractionWithRain_noPeriodConflict() {
        WeatherData w = weather(61, 25.0, 20.0, 70, 2.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("AFTERNOON", "参观",
                        attraction("测试博物馆", "博物馆", null), "14:00", "16:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertFalse(result.stream().anyMatch(c ->
                c.getType() == ConflictType.RAIN && "AFTERNOON".equals(c.getPeriod())));
    }

    @Test
    void openingHoursConflict_detectedWhenParseable() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("MORNING", "参观",
                        attraction("测试博物馆", "博物馆", "09:00-12:00"), "08:00", "10:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.OPENING_HOURS_CONFLICT));
    }

    @Test
    void unparseableOpeningHours_skipped_noFabrication() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("MORNING", "参观",
                        attraction("测试博物馆", "博物馆", "周一闭馆/节假日另行通知"), "08:00", "10:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertFalse(result.stream().anyMatch(c -> c.getType() == ConflictType.OPENING_HOURS_CONFLICT));
    }

    @Test
    void noRisk_returnsEmptyList_notNull() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 10.0);
        List<WeatherConflictDTO> result = service.detect(w, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void hasBlockingConflict_trueForWarnOrHigh() {
        assertTrue(service.hasBlockingConflict(List.of(
                new WeatherConflictDTO(ConflictType.RAIN, ConflictSeverity.WARN, "ALL_DAY", "m", "s", "src"))));
        assertFalse(service.hasBlockingConflict(List.of(
                new WeatherConflictDTO(ConflictType.RAIN, ConflictSeverity.INFO, "ALL_DAY", "m", "s", "src"))));
        assertFalse(service.hasBlockingConflict(null));
    }

    @Test
    void deduplicate_sameConflictOnlyOnce() {
        WeatherData w = weather(61, 25.0, 20.0, 70, 2.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("MORNING", "游览", attraction("公园A", "公园", null), "09:00", "11:00"),
                item("MORNING", "游览", attraction("公园A", "公园", null), "09:00", "11:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        long allDayRain = result.stream()
                .filter(c -> c.getType() == ConflictType.RAIN && "ALL_DAY".equals(c.getPeriod()))
                .count();
        assertEquals(1, allDayRain);
    }

    @Test
    void openingHoursConflict_detectedWhenVisitStartsBeforeOpening() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("MORNING", "参观",
                        attraction("测试博物馆", "博物馆", "09:00-12:00"), "08:00", "10:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.OPENING_HOURS_CONFLICT),
                "计划开始早于开放开始，应报开放时间冲突");
    }

    @Test
    void openingHoursConflict_detectedWhenVisitEndsAfterOpening() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("AFTERNOON", "参观",
                        attraction("测试博物馆", "博物馆", "09:00-17:00"), "16:00", "18:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.OPENING_HOURS_CONFLICT),
                "计划结束晚于开放结束，应报开放时间冲突");
    }

    @Test
    void openingHoursConflict_notDetectedWhenFullyInside() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("MORNING", "参观",
                        attraction("测试博物馆", "博物馆", "09:00-17:00"), "10:00", "12:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertFalse(result.stream().anyMatch(c -> c.getType() == ConflictType.OPENING_HOURS_CONFLICT),
                "计划完全落在开放时间内，不应报冲突");
    }

    @Test
    void openingHoursConflict_detectedWhenCompletelyOutside() {
        WeatherData w = weather(0, 25.0, 20.0, 10, 0.0, 10.0);
        TravelPlanDTO plan = planWith(
                item("EVENING", "参观",
                        attraction("测试博物馆", "博物馆", "09:00-17:00"), "19:00", "20:00"));
        List<WeatherConflictDTO> result = service.detect(w, plan);
        assertTrue(result.stream().anyMatch(c -> c.getType() == ConflictType.OPENING_HOURS_CONFLICT),
                "计划与开放时间完全错开，应报冲突");
    }
}