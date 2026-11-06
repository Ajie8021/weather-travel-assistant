package com.example.wea_trav_assistant.conflict;

/**
 * 天气/行程冲突类型。
 * 命名与需求文档保持一致，新增类型需同步更新前端与文档。
 */
public enum ConflictType {
    /** 降雨风险（降水概率高或降水量大） */
    RAIN,
    /** 雷暴/强对流风险（天气代码可识别时） */
    THUNDERSTORM,
    /** 大风风险 */
    WIND,
    /** 高温风险 */
    HEAT,
    /** 低温风险 */
    COLD,
    /** 天气数据不完整，无法判断 */
    DATA_INCOMPLETE,
    /** 景点开放时间与计划时段冲突 */
    OPENING_HOURS_CONFLICT
}