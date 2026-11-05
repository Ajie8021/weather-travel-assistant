package com.example.wea_trav_assistant.common;

/**
 * 活动偏好映射。
 * 旧规则模块使用 OUTDOOR/INDOOR/FOOD；
 * 景点模块使用 历史文化/自然风光/休闲娱乐/美食/亲子。
 * 本类只做映射，不修改任何一方的枚举定义。
 */
public final class PreferenceMapper {

    private PreferenceMapper() {}

    /** 旧偏好 -> 景点搜索关键词/偏好 */
    public static String toAttractionPreference(String activityPreference) {
        if (activityPreference == null) return "休闲娱乐";
        switch (activityPreference.toUpperCase()) {
            case "OUTDOOR": return "自然风光";
            case "INDOOR":  return "历史文化";
            case "FOOD":    return "美食";
            default:        return "休闲娱乐";
        }
    }

    /** 景点偏好 -> 旧偏好（用于把行程回填给规则/计划模块） */
    public static String toActivityPreference(String attractionPreference) {
        if (attractionPreference == null) return "OUTDOOR";
        switch (attractionPreference) {
            case "自然风光": return "OUTDOOR";
            case "历史文化":
            case "亲子":     return "INDOOR";
            case "美食":     return "FOOD";
            default:         return "OUTDOOR";
        }
    }

    /** 校验旧偏好是否合法，供工具参数校验复用 */
    public static boolean isValidActivityPreference(String v) {
        return "OUTDOOR".equalsIgnoreCase(v)
                || "INDOOR".equalsIgnoreCase(v)
                || "FOOD".equalsIgnoreCase(v);
    }
}