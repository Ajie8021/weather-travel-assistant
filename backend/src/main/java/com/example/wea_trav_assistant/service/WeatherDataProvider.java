
package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.WeatherData;

import java.time.LocalDate;

/**
 * 天气数据提供接口。
 *
 * 职责：
 * 1. 根据城市和出行日期获取天气数据；
 * 2. 返回已经标准化的 WeatherData；
 * 3. 不在此接口中实现天气风险判断或出行计划生成。
 *
 * 该接口用于隔离 TravelPlanTools 与具体天气服务实现，
 * 方便后续接入队友 A 的 WeatherService，也便于编写单元测试。
 */
public interface WeatherDataProvider {

    /**
     * 获取指定城市、指定日期的天气数据。
     *
     * @param city 出行城市
     * @param date 出行日期，表示目的地当地日期
     * @return 标准化天气数据
     */
    WeatherData getWeather(String city, LocalDate date);
}
