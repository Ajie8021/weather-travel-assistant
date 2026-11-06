package com.example.wea_trav_assistant.dto;

/**
 * 行程修改请求。未传字段表示保持不变。
 * confirmConflicts=false 时只返回冲突和询问，不执行修改。
 */
public class PlanModifyRequest {

    private String sessionId;
    private TravelPlanDTO currentPlan;
    private String activityPreference;
    private String city;
    private String date;
    private Double budget;
    private String transportPreference;
    private String targetPeriod;
    private String newActivityPreference;
    private boolean confirmConflicts;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public TravelPlanDTO getCurrentPlan() { return currentPlan; }
    public void setCurrentPlan(TravelPlanDTO currentPlan) { this.currentPlan = currentPlan; }
    public String getActivityPreference() { return activityPreference; }
    public void setActivityPreference(String activityPreference) { this.activityPreference = activityPreference; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Double getBudget() { return budget; }
    public void setBudget(Double budget) { this.budget = budget; }
    public String getTransportPreference() { return transportPreference; }
    public void setTransportPreference(String transportPreference) { this.transportPreference = transportPreference; }
    public String getTargetPeriod() { return targetPeriod; }
    public void setTargetPeriod(String targetPeriod) { this.targetPeriod = targetPeriod; }
    public String getNewActivityPreference() { return newActivityPreference; }
    public void setNewActivityPreference(String newActivityPreference) { this.newActivityPreference = newActivityPreference; }
    public boolean isConfirmConflicts() { return confirmConflicts; }
    public void setConfirmConflicts(boolean confirmConflicts) { this.confirmConflicts = confirmConflicts; }
}