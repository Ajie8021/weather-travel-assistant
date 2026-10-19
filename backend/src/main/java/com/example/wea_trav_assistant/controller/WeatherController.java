package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.service.WeatherService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;

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
