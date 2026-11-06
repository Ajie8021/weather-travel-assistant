package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.service.WeatherService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;


/**
 * 契约冻结说明（V2 集成阶段）：
 * 本 Controller 的请求参数、返回页面/模型属性保持不变。
 * 新增能力通过独立 JSON 接口或编排层实现，不修改本类语义。
 */
@Controller
public class WeatherController {
    private final WeatherService weatherService;
    public WeatherController(WeatherService weatherService) { this.weatherService = weatherService; }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/weather")
    public String weather(@RequestParam String city, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        model.addAttribute("weather", weatherService.getWeather(city, date));
        return "weather";
    }
}
