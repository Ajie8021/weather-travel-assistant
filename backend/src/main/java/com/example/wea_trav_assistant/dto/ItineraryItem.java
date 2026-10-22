
package com.example.wea_trav_assistant.dto;

/**
 * 单项行程活动。
 *
 * 地点和路线信息必须来自可信数据源。
 * 没有实际数据时，相关字段允许为空，
 * 不得将未经核实的信息标记为已核实。
 */
public class ItineraryItem {

    /** 地点或活动名称 */
    private String name;

    /** 活动类别，例如 OUTDOOR、INDOOR、FOOD */
    private String category;

    /** 建议停留时长，单位：分钟 */
    private Integer durationMinutes;

    /** 前往下一站的交通方式，例如 WALK、TRANSIT */
    private String transportToNext;

    /** 活动说明及注意事项 */
    private String notes;

    /** 信息来源，例如 POI 服务名称或来源标识 */
    private String source;

    /** 核实状态：VERIFIED、UNVERIFIED、UNAVAILABLE */
    private String verificationStatus = "UNAVAILABLE";

    public ItineraryItem() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getTransportToNext() {
        return transportToNext;
    }

    public void setTransportToNext(String transportToNext) {
        this.transportToNext = transportToNext;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
}
