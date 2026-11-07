package com.example.wea_trav_assistant.tools;

import com.example.wea_trav_assistant.conflict.ConflictStringMapper;
import com.example.wea_trav_assistant.conflict.WeatherConflictDTO;
import com.example.wea_trav_assistant.conflict.WeatherConflictService;
import com.example.wea_trav_assistant.dto.PlanGenerateRequest;
import com.example.wea_trav_assistant.dto.PlanResult;
import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.EnrichedTravelPlanService;
import com.example.wea_trav_assistant.service.TravelPlanService;
import com.example.wea_trav_assistant.service.WeatherDataProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 出行计划工具入口。
 *
 * 本次只在本类内部做编排：
 * - generate_travel_plan：旧工具，返回 TravelPlan，保持兼容；
 * - generate_travel_plan_with_conflicts：新工具，复用 EnrichedTravelPlanService 生成真实行程，
 *   复用 WeatherConflictService 检测冲突，返回 PlanResult（含 needConfirm/reply）。
 *
 * 不新建编排器、不改已有服务，避免重复逻辑。
 */
@Component
public class TravelPlanTools {

    private final WeatherDataProvider weatherDataProvider;
    private final TravelPlanService travelPlanService;
    private final EnrichedTravelPlanService enrichedTravelPlanService;
    private final WeatherConflictService conflictService;

    public TravelPlanTools(WeatherDataProvider weatherDataProvider,
                           TravelPlanService travelPlanService,
                           EnrichedTravelPlanService enrichedTravelPlanService,
                           WeatherConflictService conflictService) {
        this.weatherDataProvider = weatherDataProvider;
        this.travelPlanService = travelPlanService;
        this.enrichedTravelPlanService = enrichedTravelPlanService;
        this.conflictService = conflictService;
    }

    /** 旧工具：保持兼容，返回 TravelPlan，不改变原有行为。 */
    @McpTool(
            name = "generate_travel_plan",
            description = "根据城市、日期和活动偏好生成单日出行计划（不含天气冲突检测）。"
                    + "活动偏好可选 OUTDOOR、INDOOR 或 FOOD。",
            generateOutputSchema = true
    )
    public TravelPlan generateTravelPlan(
            @McpToolParam(description = "目的地城市名称，例如广州、深圳", required = true) String city,
            @McpToolParam(description = "出行日期，格式 YYYY-MM-DD", required = true) String date,
            @McpToolParam(description = "活动偏好：OUTDOOR/INDOOR/FOOD", required = true) String activityPreference) {

        validate(city, date, activityPreference);
        LocalDate travelDate = LocalDate.parse(date.trim());

        WeatherData weather = weatherDataProvider.getWeather(city.trim(), travelDate);
        TravelRequest request = new TravelRequest();
        request.setCity(city.trim());
        request.setDate(travelDate);
        request.setActivityPreference(activityPreference.trim());
        return travelPlanService.generatePlan(request, weather);
    }

    /**
     * 新工具：生成行程并检测冲突。
     *
     * 编排在本方法内完成，不依赖不存在的 Orchestrator：
     * 1) EnrichedTravelPlanService 生成真实景点/路线/时段；
     * 2) WeatherDataProvider 取天气（失败置 null，由冲突服务产出 DATA_INCOMPLETE）；
     * 3) WeatherConflictService 检测冲突；
     * 4) 返回 PlanResult，needConfirm=true 时 Agent 必须先问用户。
     */
    @McpTool(
            name = "generate_travel_plan_with_conflicts",
            description = "根据城市、日期和活动偏好生成行程，并检测天气冲突。"
                    + "返回 needConfirm=true 时，必须先向用户展示冲突并询问是否调整，"
                    + "用户确认后再调用 modify_travel_plan。",
            generateOutputSchema = true
    )
    public PlanResult generateTravelPlanWithConflicts(
            @McpToolParam(description = "目的地城市名称，例如广州、深圳", required = true) String city,
            @McpToolParam(description = "出行日期，格式 YYYY-MM-DD", required = true) String date,
            @McpToolParam(description = "活动偏好：OUTDOOR/INDOOR/FOOD", required = true) String activityPreference,
            @McpToolParam(description = "可选：预算，单位元", required = false) Double budget,
            @McpToolParam(description = "可选：交通偏好 PUBLIC_TRANSPORT/DRIVING/WALKING/CYCLING", required = false)
            String transportPreference) {

        validate(city, date, activityPreference);
        LocalDate travelDate = LocalDate.parse(date.trim());

        // PlanGenerateRequest 是 record，用构造器，不 new 空对象。
        com.example.wea_trav_assistant.dto.TransportMode mode = null;
        if (transportPreference != null && !transportPreference.isBlank()) {
            try {
                mode = com.example.wea_trav_assistant.dto.TransportMode
                        .valueOf(transportPreference.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "交通偏好非法，可选 PUBLIC_TRANSPORT/DRIVING/WALKING/CYCLING", e);
            }
        }
        PlanGenerateRequest req = new PlanGenerateRequest(
                city.trim(), travelDate, activityPreference.trim(), budget, mode);

        // 1) 真实生成
        TravelPlanDTO plan = enrichedTravelPlanService.generate(req);

        // 2) 天气 + 冲突；天气异常不编造
        WeatherData weather;
        try {
            weather = weatherDataProvider.getWeather(city.trim(), travelDate);
        } catch (Exception e) {
            weather = null;
        }
        List<WeatherConflictDTO> conflicts = conflictService.detect(weather, plan);

        // 3) 结果组装
        TravelPlanDTO enriched = ConflictStringMapper.withConflicts(plan, conflicts);
        boolean needConfirm = conflictService.hasBlockingConflict(conflicts);
        String reply = needConfirm
                ? "根据最新天气预报，" + conflicts.get(0).getMessage()
                + " 是否按建议调整行程？回复“确认调整”后我再修改，未确认前不会改动原计划。"
                : "已生成行程，未发现需要确认的天气冲突。";

        return new PlanResult(enriched, conflicts, needConfirm, reply);
    }

    private void validate(String city, String date, String activityPreference) {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("城市不能为空");
        }
        if (date == null || date.trim().isEmpty()) {
            throw new IllegalArgumentException("出行日期不能为空");
        }
        try {
            LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("出行日期格式错误，应为 YYYY-MM-DD", ex);
        }
        if (activityPreference == null || activityPreference.trim().isEmpty()) {
            throw new IllegalArgumentException("活动偏好不能为空");
        }
    }
}