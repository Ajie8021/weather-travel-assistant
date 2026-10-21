
package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.TravelPlanService;
import com.example.wea_trav_assistant.service.WeatherService;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * 出行计划页面控制器。
 *
 * 负责接收页面表单、校验请求、调用天气服务和计划服务，
 * 最后将 TravelPlan 放入 Model 供 Thymeleaf 页面展示。
 * 天气风险判断和活动推荐不在 Controller 中实现。
 */
@Controller
public class TravelPlanController {

    private static final Set<String> VALID_PREFERENCES =
            Set.of("OUTDOOR", "INDOOR", "FOOD");

    private final WeatherService weatherService;
    private final TravelPlanService travelPlanService;

    /**
     * 通过构造器注入依赖，便于 Spring 管理和单元测试。
     */
    public TravelPlanController(
            WeatherService weatherService,
            TravelPlanService travelPlanService) {
        this.weatherService = weatherService;
        this.travelPlanService = travelPlanService;
    }

    /**
     * 接收出行计划表单。
     *
     * 请求地址：POST /plan/generate
     * 表单字段：city、date、activityPreference
     */
    @PostMapping("/plan/generate")
    public String generatePlan(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String activityPreference,
            Model model,
            HttpServletResponse response) {

        // 先保留用户输入，校验失败时页面可以重新显示。
        String normalizedCity = city == null ? "" : city.trim();
        String normalizedPreference =
                activityPreference == null
                        ? ""
                        : activityPreference.trim();

        model.addAttribute("city", normalizedCity);
        model.addAttribute("date", date == null ? "" : date);
        model.addAttribute(
                "activityPreference", normalizedPreference);

        // 校验必填字段，避免无效请求进入业务服务。
        if (normalizedCity.isEmpty()
                || date == null
                || date.isBlank()
                || normalizedPreference.isEmpty()) {
            return inputError(
                    model, response, "城市、日期和活动偏好均为必填项");
        }

        // 活动偏好必须使用接口契约规定的英文枚举值。
        if (!VALID_PREFERENCES.contains(normalizedPreference)) {
            return inputError(
                    model, response, "活动偏好无效，请重新选择");
        }

        // 将 YYYY-MM-DD 转换为日期对象；非法日历日期返回友好提示。
        final LocalDate travelDate;
        try {
            travelDate = LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            return inputError(
                    model, response, "日期格式无效，请使用 YYYY-MM-DD");
        }

        // 构造业务层使用的标准请求对象。
        TravelRequest request = new TravelRequest();
        request.setCity(normalizedCity);
        request.setDate(travelDate);
        request.setActivityPreference(normalizedPreference);

        /*
         * 查询指定日期的标准化天气数据。
         * 注意：此处的方法签名需要与仓库中的 WeatherService 实际实现一致。
         * 不要在 Controller 中直接请求 Open-Meteo。
         */
        WeatherData weather =
                weatherService.getWeather(normalizedCity, travelDate);

        // 调用已实现的业务服务生成计划，不在 Controller 中复制规则。
        TravelPlan plan =
                travelPlanService.generatePlan(request, weather);

        // plan.html 使用 plan.xxx 读取计划字段。
        model.addAttribute("plan", plan);
        model.addAttribute("request", request);

        return "plan";
    }

    /**
     * 统一处理表单参数错误。
     *
     * 返回计划页面而不是 JSON，符合当前 Thymeleaf 页面流程。
     */
    private String inputError(
            Model model,
            HttpServletResponse response,
            String message) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorMessage", message);
        return "plan";
    }
}
