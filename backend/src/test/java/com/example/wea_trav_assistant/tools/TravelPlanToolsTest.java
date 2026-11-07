package com.example.wea_trav_assistant.tools;

import com.example.wea_trav_assistant.conflict.WeatherConflictProperties;
import com.example.wea_trav_assistant.conflict.WeatherConflictService;
import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.PlanGenerateRequest;
import com.example.wea_trav_assistant.dto.PlanResult;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.EnrichedTravelPlanService;
import com.example.wea_trav_assistant.service.TravelPlanService;
import com.example.wea_trav_assistant.service.TravelRuleService;
import com.example.wea_trav_assistant.service.WeatherDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TravelPlanTools 单元测试。
 *
 * 说明：
 * - 旧工具 generateTravelPlan 走 WeatherDataProvider + TravelPlanService；
 * - 新工具 generateTravelPlanWithConflicts 走 EnrichedTravelPlanService + WeatherConflictService，
 *   编排在工具内部完成，不存在外部 Orchestrator，因此这里不 mock 编排器；
 * - PlanGenerateRequest 是 record，断言使用访问器 city()/date()/preference()/budget()/transportMode()。
 */
class TravelPlanToolsTest {

    private WeatherDataProvider weatherDataProvider;
    private TravelPlanService travelPlanService;
    private EnrichedTravelPlanService enrichedService;
    private WeatherConflictService conflictService;
    private TravelPlanTools travelPlanTools;

    private final LocalDate targetDate = LocalDate.of(2026, 10, 20);

    @BeforeEach
    void setUp() {
        weatherDataProvider = mock(WeatherDataProvider.class);
        travelPlanService = new TravelPlanService(new TravelRuleService());
        enrichedService = mock(EnrichedTravelPlanService.class);
        conflictService = new WeatherConflictService(new WeatherConflictProperties());

        travelPlanTools = new TravelPlanTools(
                weatherDataProvider, travelPlanService, enrichedService, conflictService);

        when(weatherDataProvider.getWeather("广州", targetDate))
                .thenReturn(createClearWeather());
    }

    private WeatherData createClearWeather() {
        WeatherData w = new WeatherData();
        w.setCity("广州");
        w.setDate(targetDate);
        w.setWeatherCode(0);
        w.setMaxTemp(25.0);
        w.setMinTemp(18.0);
        w.setPrecipitationProbability(10);
        w.setPrecipitation(0.0);
        w.setWindSpeed(10.0);
        return w;
    }

    private WeatherData createRainyWeather() {
        WeatherData w = new WeatherData();
        w.setCity("广州");
        w.setDate(targetDate);
        w.setWeatherCode(61);
        w.setMaxTemp(25.0);
        w.setMinTemp(20.0);
        w.setPrecipitationProbability(70);
        w.setPrecipitation(2.0);
        w.setWindSpeed(10.0);
        return w;
    }

    /** 构造一个含户外景点的行程，用于触发时段级冲突。 */
    private TravelPlanDTO samplePlan() {
        AttractionDTO outdoor = new AttractionDTO(
                "poi-1", "模拟公园", "地址", "广州", "公园",
                113.0, 23.0, null, null, "MOCK");
        List<ScheduleItemDTO> schedule = new ArrayList<>();
        schedule.add(new ScheduleItemDTO("AFTERNOON", "14:00", "16:00", outdoor, "游览"));
        return new TravelPlanDTO("广州", "2026-10-20",
                List.of(outdoor), new ArrayList<>(), schedule, null, new ArrayList<>());
    }

    // ==================== 旧工具 generateTravelPlan ====================

    @Test
    void shouldGeneratePlanAndQueryWeather() {
        TravelPlan plan = travelPlanTools.generateTravelPlan("广州", "2026-10-20", "OUTDOOR");
        assertNotNull(plan);
        assertEquals("广州", plan.getCity());
        verify(weatherDataProvider, times(1)).getWeather("广州", targetDate);
    }

    @Test
    void shouldRejectBlankCity() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan("  ", "2026-10-20", "OUTDOOR"));
        verifyNoInteractions(weatherDataProvider);
    }

    @Test
    void shouldRejectBlankDate() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan("广州", " ", "OUTDOOR"));
        verifyNoInteractions(weatherDataProvider);
    }

    @Test
    void shouldRejectInvalidDateFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan("广州", "2026/10/20", "OUTDOOR"));
        verifyNoInteractions(weatherDataProvider);
    }

    @Test
    void shouldRejectBlankActivityPreference() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlan("广州", "2026-10-20", " "));
        verifyNoInteractions(weatherDataProvider);
    }

    // ==================== 新工具 generateTravelPlanWithConflicts ====================

    @Test
    void withConflicts_shouldDelegateToEnrichedServiceAndAssembleResult() {
        when(enrichedService.generate(any(PlanGenerateRequest.class))).thenReturn(samplePlan());

        PlanResult result = travelPlanTools.generateTravelPlanWithConflicts(
                "广州", "2026-10-20", "OUTDOOR", 200.0, "PUBLIC_TRANSPORT");

        assertNotNull(result);
        assertNotNull(result.getPlan());
        assertNotNull(result.getConflicts());
        assertNotNull(result.getReply());

        // 校验传给生成服务的请求字段（record 访问器）
        ArgumentCaptor<PlanGenerateRequest> captor =
                ArgumentCaptor.forClass(PlanGenerateRequest.class);
        verify(enrichedService, times(1)).generate(captor.capture());
        PlanGenerateRequest req = captor.getValue();
        assertEquals("广州", req.city());
        assertEquals(targetDate, req.date());
        assertEquals("OUTDOOR", req.preference());
        assertEquals(200.0, req.budget());
        assertNotNull(req.transportPreference());
        assertEquals("PUBLIC_TRANSPORT", req.transportPreference().name());
    }

    @Test
    void withConflicts_rainAndOutdoor_shouldNeedConfirm() {
        when(weatherDataProvider.getWeather("广州", targetDate))
                .thenReturn(createRainyWeather());
        when(enrichedService.generate(any(PlanGenerateRequest.class))).thenReturn(samplePlan());

        PlanResult result = travelPlanTools.generateTravelPlanWithConflicts(
                "广州", "2026-10-20", "OUTDOOR", null, null);

        assertTrue(result.isNeedConfirm(), "降雨 + 户外应要求用户确认");
        assertFalse(result.getConflicts().isEmpty());
        assertTrue(result.getReply().contains("是否"));
    }

    @Test
    void withConflicts_clearWeather_shouldNotNeedConfirm() {
        when(enrichedService.generate(any(PlanGenerateRequest.class))).thenReturn(samplePlan());

        PlanResult result = travelPlanTools.generateTravelPlanWithConflicts(
                "广州", "2026-10-20", "INDOOR", null, null);

        assertFalse(result.isNeedConfirm());
    }

    @Test
    void withConflicts_weatherFailure_shouldProduceDataIncomplete() {
        when(weatherDataProvider.getWeather("广州", targetDate))
                .thenThrow(new RuntimeException("timeout"));
        when(enrichedService.generate(any(PlanGenerateRequest.class))).thenReturn(samplePlan());

        PlanResult result = travelPlanTools.generateTravelPlanWithConflicts(
                "广州", "2026-10-20", "OUTDOOR", null, null);

        assertTrue(result.getConflicts().stream()
                .anyMatch(c -> "DATA_INCOMPLETE".equals(c.getType().name())));
    }

    @Test
    void withConflicts_shouldAllowOptionalNulls() {
        when(enrichedService.generate(any(PlanGenerateRequest.class))).thenReturn(samplePlan());

        travelPlanTools.generateTravelPlanWithConflicts(
                "广州", "2026-10-20", "INDOOR", null, null);

        ArgumentCaptor<PlanGenerateRequest> captor =
                ArgumentCaptor.forClass(PlanGenerateRequest.class);
        verify(enrichedService).generate(captor.capture());
        assertNull(captor.getValue().budget());
        assertNull(captor.getValue().transportPreference());
    }

    @Test
    void withConflicts_shouldRejectBlankCity() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlanWithConflicts(
                        "  ", "2026-10-20", "OUTDOOR", null, null));
        verifyNoInteractions(enrichedService);
    }

    @Test
    void withConflicts_shouldRejectInvalidDate() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlanWithConflicts(
                        "广州", "2026/10/20", "OUTDOOR", null, null));
        verifyNoInteractions(enrichedService);
    }

    @Test
    void withConflicts_shouldRejectBlankPreference() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlanWithConflicts(
                        "广州", "2026-10-20", " ", null, null));
        verifyNoInteractions(enrichedService);
    }

    @Test
    void withConflicts_invalidTransport_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> travelPlanTools.generateTravelPlanWithConflicts(
                        "广州", "2026-10-20", "OUTDOOR", null, "PLANE"));
        verifyNoInteractions(enrichedService);
    }

    // ==================== MCP 元数据 ====================

    @Test
    void shouldExposeTravelPlanAsMcpTool() throws Exception {
        Method method = TravelPlanTools.class.getMethod(
                "generateTravelPlan", String.class, String.class, String.class);
        McpTool tool = method.getAnnotation(McpTool.class);
        assertNotNull(tool);
        assertEquals("generate_travel_plan", tool.name());
        assertTrue(tool.generateOutputSchema());

        Parameter[] parameters = method.getParameters();
        assertEquals(3, parameters.length);
        for (Parameter p : parameters) {
            McpToolParam param = p.getAnnotation(McpToolParam.class);
            assertNotNull(param);
            assertFalse(param.description().isBlank());
            assertTrue(param.required());
        }
    }

    @Test
    void shouldExposeGenerateWithConflictsAsMcpTool() throws Exception {
        Method method = TravelPlanTools.class.getMethod(
                "generateTravelPlanWithConflicts",
                String.class, String.class, String.class, Double.class, String.class);
        McpTool tool = method.getAnnotation(McpTool.class);
        assertNotNull(tool);
        assertEquals("generate_travel_plan_with_conflicts", tool.name());
        assertTrue(tool.generateOutputSchema());

        Parameter[] parameters = method.getParameters();
        assertEquals(5, parameters.length);
        for (int i = 0; i < 3; i++) {
            assertTrue(parameters[i].getAnnotation(McpToolParam.class).required());
        }
        for (int i = 3; i < 5; i++) {
            assertFalse(parameters[i].getAnnotation(McpToolParam.class).required());
        }
    }
}