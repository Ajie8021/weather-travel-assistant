package com.example.wea_trav_assistant.dto;

import com.example.wea_trav_assistant.conflict.WeatherConflictDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排结果：计划 + 结构化冲突 + 是否需要用户确认 + 询问文案。
 * conflicts 永不为 null；needConfirm=true 时 reply 是询问文案。
 */
public class PlanResult {

    private TravelPlanDTO plan;
    private List<WeatherConflictDTO> conflicts = new ArrayList<>();
    private boolean needConfirm;
    private String reply;

    public PlanResult() {}

    public PlanResult(TravelPlanDTO plan, List<WeatherConflictDTO> conflicts,
                      boolean needConfirm, String reply) {
        this.plan = plan;
        this.conflicts = conflicts == null ? new ArrayList<>() : conflicts;
        this.needConfirm = needConfirm;
        this.reply = reply;
    }

    public TravelPlanDTO getPlan() { return plan; }
    public void setPlan(TravelPlanDTO plan) { this.plan = plan; }
    public List<WeatherConflictDTO> getConflicts() { return conflicts; }
    public void setConflicts(List<WeatherConflictDTO> conflicts) {
        this.conflicts = conflicts == null ? new ArrayList<>() : conflicts;
    }
    public boolean isNeedConfirm() { return needConfirm; }
    public void setNeedConfirm(boolean needConfirm) { this.needConfirm = needConfirm; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}