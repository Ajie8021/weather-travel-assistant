
package com.example.wea_trav_assistant.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个时段的行程安排。
 */
public class PeriodPlan {

    /** 时段标识：MORNING、AFTERNOON、EVENING */
    private String period;

    /** 当前时段的活动列表 */
    private List<ItineraryItem> activities = new ArrayList<>();

    public PeriodPlan() {
    }

    public PeriodPlan(String period) {
        this.period = period;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public List<ItineraryItem> getActivities() {
        return activities;
    }

    public void setActivities(List<ItineraryItem> activities) {
        this.activities = activities == null
                ? new ArrayList<>()
                : new ArrayList<>(activities);
    }
}
