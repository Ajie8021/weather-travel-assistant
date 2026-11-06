package com.example.wea_trav_assistant.contract;

import com.example.wea_trav_assistant.controller.WeatherController;
import com.example.wea_trav_assistant.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 只加载 WeatherController，WeatherService 用替身。
 * 契约目标：/weather 路由存在、city/date 参数可绑定、不返回 404。
 * 不断言 200，因为参数校验/日期范围校验属于业务，由业务测试覆盖；
 * 日期用“今天+1”，避免写死日期导致跨天失败。
 */
@WebMvcTest(WeatherController.class)
class LegacyEndpointContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WeatherService weatherService;

    @Test
    void weather_page_route_accepts_city_and_date() throws Exception {
        when(weatherService.getWeather(anyString(), any())).thenReturn(null);

        String tomorrow = LocalDate.now().plusDays(1).toString(); // yyyy-MM-dd

        mockMvc.perform(get("/weather").param("city", "Beijing").param("date", tomorrow))
                .andExpect(status().is(org.hamcrest.Matchers.not(404)));
    }
}