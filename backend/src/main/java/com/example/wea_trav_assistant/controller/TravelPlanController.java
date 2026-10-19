package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherDataIncompleteException;
import com.example.wea_trav_assistant.exception.WeatherException;
import com.example.wea_trav_assistant.service.TravelPlanService;
import com.example.wea_trav_assistant.service.WeatherService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClientException;

@Controller
public class TravelPlanController {

    private final WeatherService weatherService;
    private final TravelPlanService travelPlanService;

    public TravelPlanController(
            WeatherService weatherService,
            TravelPlanService travelPlanService) {
        this.weatherService = weatherService;
        this.travelPlanService = travelPlanService;
    }

    @PostMapping("/plan/generate")
    public String generatePlan(
            @ModelAttribute TravelRequest request,
            Model model) {
        try {
            validateRequest(request);
            WeatherData weather = weatherService.getWeather(
                    request.getCity(), request.getDate());
            TravelPlan plan = travelPlanService.generatePlan(request, weather);
            model.addAttribute("plan", plan);
        } catch (IllegalArgumentException
                 | WeatherException
                 | WeatherDataIncompleteException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        } catch (RestClientException exception) {
            model.addAttribute("errorMessage", "天气服务暂不可用，请稍后再试");
        }
        return "plan";
    }

    private void validateRequest(TravelRequest request) {
        if (request.getCity() == null || request.getCity().isBlank()) {
            throw new IllegalArgumentException("请输入目的地城市");
        }
        if (request.getDate() == null) {
            throw new IllegalArgumentException("请选择有效的出行日期");
        }
        String preference = request.getActivityPreference();
        if (!"OUTDOOR".equals(preference)
                && !"INDOOR".equals(preference)
                && !"FOOD".equals(preference)) {
            throw new IllegalArgumentException("请选择一种活动偏好");
        }
    }
}
