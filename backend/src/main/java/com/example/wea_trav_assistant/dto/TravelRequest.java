
package com.example.wea_trav_assistant.dto;

import java.time.LocalDate;

/**
 * 用户出行请求 DTO。
 *
 * 用于接收 Controller 传入的出行条件。
 * 活动偏好必须使用约定的值：
 * OUTDOOR：户外游览
 * INDOOR：室内休闲
 * FOOD：美食体验
 */
public class TravelRequest {

    /** 目的地城市名称 */
    private String city;

    /** 出行日期，表示目的地当地日期 */
    private LocalDate date;

    /** 活动偏好：OUTDOOR、INDOOR 或 FOOD */
    private String activityPreference;

    public TravelRequest() {
        // 保留无参构造方法，方便 Spring MVC 表单绑定。
    }

    public TravelRequest(
            String city,
            LocalDate date,
            String activityPreference) {
        this.city = city;
        this.date = date;
        this.activityPreference = activityPreference;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getActivityPreference() {
        return activityPreference;
    }

    public void setActivityPreference(String activityPreference) {
        this.activityPreference = activityPreference;
    }
}
