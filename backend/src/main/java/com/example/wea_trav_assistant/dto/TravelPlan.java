
package com.example.wea_trav_assistant.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 单日出行计划 DTO。
 *
 * 保存天气概况、分时段活动、穿衣建议、
 * 随身物品清单和注意事项，供页面层展示。
 */
public class TravelPlan {

    /** 目的地城市 */
    private String city;

    /** 出行日期，格式为 YYYY-MM-DD */
    private String date;

    /** 天气概况简述 */
    private String weatherSummary;

    /** 上午活动建议 */
    private String morningActivity;

    /** 下午活动建议 */
    private String afternoonActivity;

    /** 晚间活动建议 */
    private String eveningActivity;

    /** 穿衣建议 */
    private String clothingAdvice;

    /** 建议携带的物品；没有物品时为空列表 */
    private List<String> itemsToPack = new ArrayList<>();

    /** 注意事项；没有提醒时为空列表 */
    private List<String> warnings = new ArrayList<>();

    public TravelPlan() {
        // 保留无参构造方法，方便服务组装计划对象。
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getWeatherSummary() {
        return weatherSummary;
    }

    public void setWeatherSummary(String weatherSummary) {
        this.weatherSummary = weatherSummary;
    }

    public String getMorningActivity() {
        return morningActivity;
    }

    public void setMorningActivity(String morningActivity) {
        this.morningActivity = morningActivity;
    }

    public String getAfternoonActivity() {
        return afternoonActivity;
    }

    public void setAfternoonActivity(String afternoonActivity) {
        this.afternoonActivity = afternoonActivity;
    }

    public String getEveningActivity() {
        return eveningActivity;
    }

    public void setEveningActivity(String eveningActivity) {
        this.eveningActivity = eveningActivity;
    }

    public String getClothingAdvice() {
        return clothingAdvice;
    }

    public void setClothingAdvice(String clothingAdvice) {
        this.clothingAdvice = clothingAdvice;
    }

    public List<String> getItemsToPack() {
        return itemsToPack;
    }

    public void setItemsToPack(List<String> itemsToPack) {
        // 防止传入 null，避免页面遍历列表时出现空指针异常。
        this.itemsToPack = itemsToPack == null
                ? new ArrayList<>()
                : new ArrayList<>(itemsToPack);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        // 没有注意事项时统一使用空列表，而不是 null。
        this.warnings = warnings == null
                ? new ArrayList<>()
                : new ArrayList<>(warnings);
    }
}
