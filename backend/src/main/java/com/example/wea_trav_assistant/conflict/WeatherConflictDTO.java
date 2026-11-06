package com.example.wea_trav_assistant.conflict;

import java.util.Objects;

/**
 * 单条天气/行程冲突。
 *
 * 设计约定：
 * 1) period 使用 MORNING/AFTERNOON/EVENING/ALL_DAY，与 ScheduleItemDTO.period 对齐；
 * 2) message 面向用户，措辞用“预计存在风险/建议调整”，不断言天气一定发生；
 * 3) suggestion 给出可执行建议，但不编造具体景点/票价/营业时间；
 * 4) source 标注数据来源，便于追溯（Open-Meteo / AMAP / rule）。
 */
public class WeatherConflictDTO {

    private ConflictType type;
    private ConflictSeverity severity;
    /** MORNING / AFTERNOON / EVENING / ALL_DAY */
    private String period;
    private String message;
    private String suggestion;
    private String source;

    public WeatherConflictDTO() {}

    public WeatherConflictDTO(ConflictType type, ConflictSeverity severity, String period,
                              String message, String suggestion, String source) {
        this.type = type;
        this.severity = severity;
        this.period = period;
        this.message = message;
        this.suggestion = suggestion;
        this.source = source;
    }

    public ConflictType getType() { return type; }
    public void setType(ConflictType type) { this.type = type; }
    public ConflictSeverity getSeverity() { return severity; }
    public void setSeverity(ConflictSeverity severity) { this.severity = severity; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeatherConflictDTO)) return false;
        WeatherConflictDTO that = (WeatherConflictDTO) o;
        return type == that.type
                && severity == that.severity
                && Objects.equals(period, that.period)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, severity, period, message);
    }

    @Override
    public String toString() {
        return
                "WeatherConflictDTO{" + type + ", " + severity + ", " + period + ", " + message + '}';
    }
}