
package com.example.wea_trav_assistant.controller;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.TravelPlanService;
import com.example.wea_trav_assistant.service.WeatherService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TravelPlanController 的 Web 层测试。
 *
 * Mock 天气服务和计划服务，专注验证表单请求、模型数据和页面响应，
 * 不依赖真实网络或天气 API。
 */
@WebMvcTest(TravelPlanController.class)
class TravelPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherService weatherService;

    @MockitoBean
    private TravelPlanService travelPlanService;

    @Test
    void validFormShouldGeneratePlanAndRenderPlanPage() throws Exception {
        // 准备：模拟天气服务返回指定日期的天气。
        LocalDate date = LocalDate.of(2026, 10, 20);
        WeatherData weather = new WeatherData();
        weather.setCity("广州");
        weather.setDate(date);
        weather.setWeatherCode(0);
        weather.setMaxTemp(25.0);
        weather.setMinTemp(18.0);

        when(weatherService.getWeather("广州", date))
                .thenReturn(weather);

        // 准备：模拟计划服务返回完整的页面展示对象。
        TravelPlan plan = new TravelPlan();
        plan.setCity("广州");
        plan.setDate("2026-10-20");
        plan.setWeatherSummary("晴朗，18-25°C");
        plan.setMorningActivity("适合安排户外活动");
        plan.setAfternoonActivity("可以进行户外游览");
        plan.setEveningActivity("可安排休闲活动");
        plan.setClothingAdvice("建议准备薄外套");
        plan.setItemsToPack(List.of());
        plan.setWarnings(List.of());

        when(travelPlanService.generatePlan(any(), eq(weather)))
                .thenReturn(plan);

        // 执行：模拟浏览器提交表单。
        mockMvc.perform(post("/plan/generate")
                        .param("city", " 广州 ")
                        .param("date", "2026-10-20")
                        .param("activityPreference", "OUTDOOR"))
                .andExpect(status().isOk())
                .andExpect(view().name("plan"))
                .andExpect(model().attribute("plan", plan))
                .andExpect(model().attribute("city", "广州"))
                .andExpect(model().attribute(
                        "activityPreference", "OUTDOOR"));

        // 验证 Controller 将标准化后的城市和日期交给天气服务。
        verify(weatherService).getWeather("广州", date);

        // 验证天气结果传给计划服务，并由其生成页面数据。
        verify(travelPlanService).generatePlan(any(), eq(weather));
    }

    @Test
    void invalidPreferenceShouldReturnBadRequestWithoutCallingServices()
            throws Exception {
        // SHOPPING 不属于契约允许的活动偏好。
        mockMvc.perform(post("/plan/generate")
                        .param("city", "广州")
                        .param("date", "2026-10-20")
                        .param("activityPreference", "SHOPPING"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("plan"))
                .andExpect(model().attributeExists("errorMessage"));

        // 参数校验失败时，不应查询天气或生成计划。
        verifyNoInteractions(weatherService, travelPlanService);
    }

    @Test
    void weatherApiFailureShouldReturnPlanPageWithServiceUnavailable()
            throws Exception {

        // 模拟天气服务连接超时，避免测试依赖真实网络。
        when(weatherService.getWeather(
                eq("广州"), eq(LocalDate.of(2026, 10, 20))))
                .thenThrow(new ResourceAccessException("模拟连接超时"));

        mockMvc.perform(post("/plan/generate")
                        .param("city", "广州")
                        .param("date", "2026-10-20")
                        .param("activityPreference", "OUTDOOR"))
                // 天气 API 不可用时，应返回 503。
                .andExpect(status().isServiceUnavailable())
                // 必须返回计划页面，而不是天气查询页面。
                .andExpect(view().name("plan"))
                // 页面应显示可理解的错误信息。
                .andExpect(model().attributeExists("errorMessage"))
                // 保留表单输入，方便用户稍后重试。
                .andExpect(model().attribute("city", "广州"))
                .andExpect(model().attribute("date", "2026-10-20"))
                .andExpect(model().attribute(
                        "activityPreference", "OUTDOOR"));

        // 天气查询失败时，计划生成服务不应被调用。
        verifyNoInteractions(travelPlanService);
    }
}
