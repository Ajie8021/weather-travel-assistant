package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.common.ApiResponse;
import com.example.wea_trav_assistant.conflict.ConflictStringMapper;
import com.example.wea_trav_assistant.conflict.WeatherConflictDTO;
import com.example.wea_trav_assistant.conflict.WeatherConflictService;
import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.PlanModifyRequest;
import com.example.wea_trav_assistant.dto.PlanResult;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.travel.PlanModifyService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段2 手工验收专用接口（仅 dev/test）。
 * 不调用高德、不调用 Open-Meteo、不调用模型；
 * 天气由请求体传入，冲突检测仍走阶段1 的真实 WeatherConflictService。
 */
@RestController
@RequestMapping("/api/plans")
@Profile({"dev", "test"})
public class PlanSmokeController {

    private final WeatherConflictService conflictService;
    private final PlanModifyService modifyService;

    public PlanSmokeController(WeatherConflictService conflictService,
                               PlanModifyService modifyService) {
        this.conflictService = conflictService;
        this.modifyService = modifyService;
    }

    public static class SmokeGenerateRequest {
        public String city;
        public String date;
        public String preference;
        public Double budget;
        public WeatherData weather;
    }

    public static class SmokeModifyRequest {
        public TravelPlanDTO currentPlan;
        public String targetPeriod;
        public String newActivityPreference;
        public boolean confirmConflicts;
        public WeatherData weather;
    }

    @PostMapping("/smoke/generate")
    public ApiResponse<PlanResult> smokeGenerate(@RequestBody SmokeGenerateRequest req) {
        if (req.city == null || req.date == null) {
            throw new IllegalArgumentException("city/date 不能为空");
        }
        TravelPlanDTO plan = buildFixedPlan(req.city, req.date, req.preference, req.budget);
        List<WeatherConflictDTO> conflicts = conflictService.detect(req.weather, plan);
        TravelPlanDTO enriched = ConflictStringMapper.withConflicts(plan, conflicts);
        boolean needConfirm = conflictService.hasBlockingConflict(conflicts);
        String reply = needConfirm
                ? "根据天气，" + conflicts.get(0).getMessage() + " 是否按建议调整行程？"
                : "已生成行程，未发现需要确认的天气冲突。";
        return ApiResponse.success(new PlanResult(enriched, conflicts, needConfirm, reply));
    }

    @PostMapping("/smoke/modify")
    public ApiResponse<PlanResult> smokeModify(@RequestBody SmokeModifyRequest req) {
        if (req.currentPlan == null) {
            throw new IllegalArgumentException("currentPlan 不能为空");
        }
        if (!req.confirmConflicts) {
            List<WeatherConflictDTO> conflicts =
                    conflictService.detect(req.weather, req.currentPlan);
            boolean need = conflictService.hasBlockingConflict(conflicts);
            return ApiResponse.success(new PlanResult(
                    ConflictStringMapper.withConflicts(req.currentPlan, conflicts),
                    conflicts, need,
                    need ? "根据天气，" + conflicts.get(0).getMessage() + " 是否调整？"
                            : "当前无需要确认的冲突，可直接修改。"));
        }

        PlanModifyRequest m = new PlanModifyRequest();
        m.setCurrentPlan(req.currentPlan);
        m.setTargetPeriod(req.targetPeriod);
        m.setNewActivityPreference(req.newActivityPreference);
        m.setConfirmConflicts(true);
        TravelPlanDTO modified = modifyService.apply(req.currentPlan, m);

        List<WeatherConflictDTO> conflicts = conflictService.detect(req.weather, modified);
        boolean need = conflictService.hasBlockingConflict(conflicts);
        return ApiResponse.success(new PlanResult(
                ConflictStringMapper.withConflicts(modified, conflicts),
                conflicts, need,
                need ? "修改后仍存在冲突，是否继续调整？" : "已按新条件更新行程。"));
    }

    private TravelPlanDTO buildFixedPlan(String city, String date,
                                         String preference, Double budget) {
        AttractionDTO outdoor = new AttractionDTO(
                "mock-poi-1", "模拟公园", "模拟地址", city, "公园",
                113.0, 23.0, null, null, "MOCK");
        AttractionDTO indoor = new AttractionDTO(
                "mock-poi-2", "模拟博物馆", "模拟地址", city, "博物馆",
                113.1, 23.1, null, "09:00-17:00", "MOCK");

        List<ScheduleItemDTO> schedule = new ArrayList<>();
        schedule.add(new ScheduleItemDTO("MORNING", "09:00", "11:00", indoor, "参观"));
        schedule.add(new ScheduleItemDTO("AFTERNOON", "14:00", "16:00",
                "INDOOR".equalsIgnoreCase(preference) ? indoor : outdoor, "游览"));
        schedule.add(new ScheduleItemDTO("EVENING", "19:00", "20:00", null, "休闲"));

        return new TravelPlanDTO(city, date,
                List.of(outdoor, indoor), new ArrayList<>(),
                schedule, budget, new ArrayList<>());
    }
}