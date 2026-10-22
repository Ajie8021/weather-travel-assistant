
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

    /** 结构化时段计划，逐步替代三个纯文本活动字段 */
    private List<PeriodPlan> periodPlans = new ArrayList<>();

    /** 预算估算下限，单位由 budgetCurrency 指定 */
    private Double budgetMin;

    /** 预算估算上限，单位由 budgetCurrency 指定 */
    private Double budgetMax;

    /** 预算币种，例如 CNY */
    private String budgetCurrency = "CNY";

    /** 预算估算说明，例如是否包含交通、餐饮和门票 */
    private String budgetNote;

    /** 天气不适宜时的备选方案 */
    private String alternativePlan;

    /** 数据来源及核实情况的说明 */
    private List<String> dataSources = new ArrayList<>();

    /** 用户选择的活动偏好：OUTDOOR、INDOOR 或 FOOD */
    private String activityPreference;

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

    public List<PeriodPlan> getPeriodPlans() {
        return periodPlans;
    }

    public void setPeriodPlans(List<PeriodPlan> periodPlans) {
        this.periodPlans = periodPlans == null
                ? new ArrayList<>()
                : new ArrayList<>(periodPlans);
    }

    public Double getBudgetMin() {
        return budgetMin;
    }

    public void setBudgetMin(Double budgetMin) {
        this.budgetMin = budgetMin;
    }

    public Double getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(Double budgetMax) {
        this.budgetMax = budgetMax;
    }

    public String getBudgetCurrency() {
        return budgetCurrency;
    }

    public void setBudgetCurrency(String budgetCurrency) {
        this.budgetCurrency = budgetCurrency;
    }

    public String getBudgetNote() {
        return budgetNote;
    }

    public void setBudgetNote(String budgetNote) {
        this.budgetNote = budgetNote;
    }

    public String getAlternativePlan() {
        return alternativePlan;
    }

    public void setAlternativePlan(String alternativePlan) {
        this.alternativePlan = alternativePlan;
    }

    public List<String> getDataSources() {
        return dataSources;
    }

    public void setDataSources(List<String> dataSources) {
        this.dataSources = dataSources == null
                ? new ArrayList<>()
                : new ArrayList<>(dataSources);
    }

    public String getActivityPreference() {
        return activityPreference;
    }

    public void setActivityPreference(String activityPreference) {
        this.activityPreference = activityPreference;
    }
}
