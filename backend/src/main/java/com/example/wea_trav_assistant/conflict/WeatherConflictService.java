package com.example.wea_trav_assistant.conflict;

import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import com.example.wea_trav_assistant.dto.WeatherData;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 天气冲突检测服务。
 *
 * 重要：TravelPlanDTO / ScheduleItemDTO / AttractionDTO 在本仓库中是 Java record，
 * 访问器是 xxx() 而不是 getXxx()，本类按 record 写法访问字段。
 *
 * 职责边界：
 * 1) 不调用 Open-Meteo / 高德，只消费传入的 WeatherData 和 TravelPlanDTO；
 * 2) 阈值来自 WeatherConflictProperties，与 B 模块保持一致（见 C-07）；
 * 3) 只产出冲突列表，不修改行程（record 不可变）；
 * 4) 缺失数据不当作无风险，必须产出 DATA_INCOMPLETE。
 */
@Service
public class WeatherConflictService {

    /** WMO 天气代码中可识别为雷暴/强对流的集合 */
    private static final Set<Integer> THUNDERSTORM_CODES = Set.of(95, 96, 99);

    /** WMO 天气代码中可识别为降雨的集合 */
    private static final Set<Integer> RAIN_CODES =
            Set.of(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82);

    private final WeatherConflictProperties props;

    public WeatherConflictService(WeatherConflictProperties props) {
        this.props = props;
    }

    /**
     * 检测冲突主入口。
     *
     * @param weather 已确认地点、对应日期的标准化天气；可为 null（视为数据缺失）
     * @param plan    含 schedule/attractions 的行程；可为 null（只做日级检测）
     * @return 冲突列表，永不为 null；无冲突返回空列表
     */
    public List<WeatherConflictDTO> detect(WeatherData weather, TravelPlanDTO plan) {
        List<WeatherConflictDTO> conflicts = new ArrayList<>();

        if (weather == null) {
            conflicts.add(new WeatherConflictDTO(
                    ConflictType.DATA_INCOMPLETE, ConflictSeverity.WARN, "ALL_DAY",
                    "未获取到该日期的天气数据，无法判断天气风险。",
                    "请稍后重试或改用手动天气查询；在数据恢复前不要默认天气良好。",
                    "rule"));
            return conflicts;
        }

        checkMissingFields(weather, conflicts);
        checkDailyRisks(weather, conflicts);

        if (plan != null) {
            checkPeriodRisks(weather, plan, conflicts);
            if (props.isOpeningHoursCheckEnabled()) {
                checkOpeningHours(plan, conflicts);
            }
        }

        return deduplicate(conflicts);
    }

    // ---------------------------------------------------------------------
    // 缺失字段
    // ---------------------------------------------------------------------

    private void checkMissingFields(WeatherData w, List<WeatherConflictDTO> out) {
        List<String> missing = new ArrayList<>();
        if (w.getMaxTemp() == null) missing.add("最高温");
        if (w.getMinTemp() == null) missing.add("最低温");
        if (w.getPrecipitationProbability() == null) missing.add("降水概率");
        if (w.getWindSpeed() == null) missing.add("风速");
        if (w.getWeatherCode() == null) missing.add("天气代码");

        if (!missing.isEmpty()) {
            out.add(new WeatherConflictDTO(
                    ConflictType.DATA_INCOMPLETE, ConflictSeverity.WARN, "ALL_DAY",
                    "天气数据不完整，缺少：" + String.join("、", missing) + "。",
                    "相关风险无法判断，建议出行前再次查询，不要默认无风险。",
                    "rule"));
        }
    }

    // ---------------------------------------------------------------------
    // 日级风险
    // ---------------------------------------------------------------------

    private void checkDailyRisks(WeatherData w, List<WeatherConflictDTO> out) {
        if (w.getMaxTemp() != null && w.getMaxTemp() >= props.getHighTemp()) {
            out.add(new WeatherConflictDTO(
                    ConflictType.HEAT, ConflictSeverity.WARN, "ALL_DAY",
                    "预计最高温约 " + w.getMaxTemp() + "℃，长时间户外活动舒适度可能较低。",
                    "建议补水、遮阳，避开午后高温时段，优先安排室内或阴凉处活动。",
                    "Open-Meteo"));
        }
        if (w.getMinTemp() != null && w.getMinTemp() <= props.getLowTemp()) {
            out.add(new WeatherConflictDTO(
                    ConflictType.COLD, ConflictSeverity.WARN, "ALL_DAY",
                    "预计最低温约 " + w.getMinTemp() + "℃，需要注意保暖。",
                    "建议携带保暖衣物，减少长时间户外停留，可考虑室内活动。",
                    "Open-Meteo"));
        }
        if (w.getWindSpeed() != null && w.getWindSpeed() >= props.getHighWindSpeed()) {
            out.add(new WeatherConflictDTO(
                    ConflictType.WIND, ConflictSeverity.WARN, "ALL_DAY",
                    "预计风速约 " + w.getWindSpeed() + " km/h，部分户外活动可能受影响。",
                    "建议关注风力变化，谨慎安排高空、临水或开阔地带的户外活动。",
                    "Open-Meteo"));
        }

        boolean rainByProb = w.getPrecipitationProbability() != null
                && w.getPrecipitationProbability() >= props.getRainProbability();
        boolean rainByAmount = w.getPrecipitation() != null
                && w.getPrecipitation() > props.getRainAmount();
        if (rainByProb || rainByAmount) {
            out.add(new WeatherConflictDTO(
                    ConflictType.RAIN, ConflictSeverity.WARN, "ALL_DAY",
                    "预计存在降雨风险" + buildRainDetail(w) + "。",
                    "建议携带雨具，优先安排室内活动，户外活动预留调整方案。",
                    "Open-Meteo"));
        }

        if (w.getWeatherCode() != null && THUNDERSTORM_CODES.contains(w.getWeatherCode())) {
            out.add(new WeatherConflictDTO(
                    ConflictType.THUNDERSTORM, ConflictSeverity.HIGH, "ALL_DAY",
                    "预计可能出现雷暴或强对流天气（天气代码 " + w.getWeatherCode() + "）。",
                    "建议避开该时段户外活动，优先室内；出行前再次确认最新预报。",
                    "Open-Meteo"));
        }
    }

    private String buildRainDetail(WeatherData w) {
        StringBuilder sb = new StringBuilder();
        if (w.getPrecipitationProbability() != null) {
            sb.append("，降水概率约 ").append(w.getPrecipitationProbability()).append("%");
        }
        if (w.getPrecipitation() != null) {
            sb.append("，降水量约 ").append(w.getPrecipitation()).append("mm");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 时段级风险
    // ---------------------------------------------------------------------

    private void checkPeriodRisks(WeatherData w, TravelPlanDTO plan, List<WeatherConflictDTO> out) {
        // record 访问器：schedule()
        if (plan.schedule() == null || plan.schedule().isEmpty()) return;

        boolean thunderstorm = w.getWeatherCode() != null
                && THUNDERSTORM_CODES.contains(w.getWeatherCode());
        boolean rain = (w.getPrecipitationProbability() != null
                && w.getPrecipitationProbability() >= props.getRainProbability())
                || (w.getPrecipitation() != null && w.getPrecipitation() > props.getRainAmount())
                || (w.getWeatherCode() != null && RAIN_CODES.contains(w.getWeatherCode()));
        boolean wind = w.getWindSpeed() != null && w.getWindSpeed() >= props.getHighWindSpeed();

        for (ScheduleItemDTO item : plan.schedule()) {
            if (!hasOutdoorAttraction(item)) continue;

            String period = normalizePeriod(item.period());
            // record 访问器：attraction() / name()
            String attractionName = item.attraction() != null ? item.attraction().name() : "户外景点";

            if (thunderstorm) {
                out.add(new WeatherConflictDTO(
                        ConflictType.THUNDERSTORM, ConflictSeverity.HIGH, period,
                        periodLabel(period) + "安排的「" + attractionName + "」为户外活动，预计可能出现雷暴。",
                        "建议将该时段改为室内活动，或调整到其他时段；出行前再次确认预报。",
                        "Open-Meteo"));
            } else if (rain) {
                out.add(new WeatherConflictDTO(
                        ConflictType.RAIN, ConflictSeverity.WARN, period,
                        periodLabel(period) + "安排的「" + attractionName + "」为户外活动，预计存在降雨风险。",
                        "建议携带雨具，或将该时段改为室内活动。",
                        "Open-Meteo"));
            } else if (wind) {
                out.add(new WeatherConflictDTO(
                        ConflictType.WIND, ConflictSeverity.WARN, period,
                        periodLabel(period) + "安排的「" + attractionName + "」为户外活动，预计风力较大。",
                        "建议关注风力，谨慎安排开阔地带活动，或改为室内。",
                        "Open-Meteo"));
            }
        }
    }

    private boolean hasOutdoorAttraction(ScheduleItemDTO item) {
        if (item == null || item.attraction() == null) return false;
        String cat = item.attraction().category();
        if (cat == null) return false;
        String c = cat.toLowerCase(Locale.ROOT);
        return c.contains("公园") || c.contains("山") || c.contains("湖")
                || c.contains("自然") || c.contains("风景") || c.contains("户外")
                || c.contains("植物园") || c.contains("动物园");
    }

    // ---------------------------------------------------------------------
    // 开放时间冲突
    // ---------------------------------------------------------------------

    private void checkOpeningHours(TravelPlanDTO plan, List<WeatherConflictDTO> out) {
        if (plan.schedule() == null) return;
        for (ScheduleItemDTO item : plan.schedule()) {
            if (item == null || item.attraction() == null) continue;

            String hours = item.attraction().openingHours();
            String start = item.startTime();
            String end = item.endTime();
            if (hours == null || start == null || end == null) continue;

            LocalTime[] open = parseSimpleRange(hours);
            LocalTime[] visit = parseSimpleRange(start + "-" + end);
            if (open == null || visit == null) continue;

            // 严格包含：计划开始早于开放开始，或计划结束晚于开放结束，都视为冲突。
            // 这样 08:00-10:00 对 09:00-12:00 会报冲突（到的时候还没开门）。
            boolean notFullyInside = visit[0].isBefore(open[0]) || visit[1].isAfter(open[1]);
            if (notFullyInside) {
                out.add(new WeatherConflictDTO(
                        ConflictType.OPENING_HOURS_CONFLICT, ConflictSeverity.WARN,
                        normalizePeriod(item.period()),
                        "「" + item.attraction().name() + "」的开放时间（" + hours
                                + "）与计划时段（" + start + "-" + end + "）可能不一致。",
                        "建议以景点官方信息为准调整时间，或更换该时段安排。",
                        "AMAP"));
            }
        }
    }

    private LocalTime[] parseSimpleRange(String text) {
        try {
            String[] parts = text.trim().split("-");
            if (parts.length != 2) return null;
            return new LocalTime[]{LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim())};
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // 工具方法
    // ---------------------------------------------------------------------

    private String normalizePeriod(String period) {
        if (period == null) return "ALL_DAY";
        String p = period.toUpperCase(Locale.ROOT);
        if (p.equals("MORNING") || p.equals("AFTERNOON") || p.equals("EVENING")) return p;
        return "ALL_DAY";
    }

    private String periodLabel(String period) {
        switch (period) {
            case "MORNING": return "上午";
            case "AFTERNOON": return "下午";
            case "EVENING": return "晚间";
            default: return "全天";
        }
    }

    private List<WeatherConflictDTO> deduplicate(List<WeatherConflictDTO> list) {
        List<WeatherConflictDTO> result = new ArrayList<>();
        for (WeatherConflictDTO c : list) {
            if (!result.contains(c)) result.add(c);
        }
        return result;
    }

    public boolean hasBlockingConflict(List<WeatherConflictDTO> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) return false;
        return conflicts.stream().anyMatch(c ->
                c.getSeverity() == ConflictSeverity.HIGH
                        || c.getSeverity() == ConflictSeverity.WARN);
    }

    public List<WeatherConflictDTO> safeList(List<WeatherConflictDTO> conflicts) {
        return conflicts == null ? Collections.emptyList() : conflicts;
    }
}