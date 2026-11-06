package com.example.wea_trav_assistant.common;

import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.ItineraryItem;
import com.example.wea_trav_assistant.dto.PeriodPlan;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TravelPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * TravelPlan（旧规则计划 + periodPlans）与 ScheduleItemDTO（新行程时段）之间的适配。
 *
 * 相关文件结构：
 * - PeriodPlan：period + List<ItineraryItem> activities；
 * - ItineraryItem：name/category/durationMinutes/transportToNext/notes/source/verificationStatus；
 * - ScheduleItemDTO / AttractionDTO 是 record，只能构造器创建。
 *
 * 映射约束（阶段0 冻结）：
 * 1) ScheduleItemDTO.startTime/endTime 在 ItineraryItem 中无对应字段，转换时丢弃，
 *    不把时间硬塞进 notes，避免伪造结构化数据；
 * 2) ScheduleItemDTO.attraction 在 ItineraryItem 中无对应引用，
 *    转 ItineraryItem 时把 attraction.name 写入 name，poiId 写入 source（若 source 为空）；
 *    转 ScheduleItemDTO 时 attraction 只能为 null，不伪造坐标/地址/营业时间；
 * 3) 列表字段返回空列表而不是 null；
 * 4) 无法映射的信息保留 null 或“暂无安排”，绝不编造。
 */
public final class TravelPlanDtoAdapter {

    private TravelPlanDtoAdapter() {}

    // ---------------------------------------------------------------------
    // 一、TravelPlan.periodPlans -> List<ScheduleItemDTO>
    // ---------------------------------------------------------------------

    /**
     * 把 TravelPlan 的结构化时段计划转换为 ScheduleItemDTO 列表。
     * 每个 ItineraryItem 生成一个 ScheduleItemDTO，period 继承所属 PeriodPlan。
     * ItineraryItem 没有时间/景点对象，startTime/endTime/attraction 填 null。
     */
    public static List<ScheduleItemDTO> toScheduleItems(TravelPlan plan) {
        if (plan == null) {
            return Collections.emptyList();
        }

        List<ScheduleItemDTO> result = new ArrayList<>();

        if (plan.getPeriodPlans() != null && !plan.getPeriodPlans().isEmpty()) {
            for (PeriodPlan pp : plan.getPeriodPlans()) {
                if (pp == null) {
                    continue;
                }
                List<ItineraryItem> items = pp.getActivities();
                if (items == null || items.isEmpty()) {
                    result.add(new ScheduleItemDTO(
                            pp.getPeriod(), null, null, null, "暂无安排"));
                    continue;
                }
                for (ItineraryItem it : items) {
                    result.add(toScheduleItem(pp.getPeriod(), it));
                }
            }
            return result;
        }

        // 兜底：旧计划只有三段文字
        result.add(new ScheduleItemDTO("MORNING", null, null, null, safeText(plan.getMorningActivity())));
        result.add(new ScheduleItemDTO("AFTERNOON", null, null, null, safeText(plan.getAfternoonActivity())));
        result.add(new ScheduleItemDTO("EVENING", null, null, null, safeText(plan.getEveningActivity())));
        return result;
    }

    /**
     * ItineraryItem -> ScheduleItemDTO。
     * ItineraryItem 没有时间和 AttractionDTO，对应字段填 null；
     * activity 取 name，若 name 为空则用“暂无安排”。
     */
    private static ScheduleItemDTO toScheduleItem(String period, ItineraryItem it) {
        if (it == null) {
            return new ScheduleItemDTO(period, null, null, null, "暂无安排");
        }
        String activity = (it.getName() == null || it.getName().isBlank())
                ? "暂无安排"
                : it.getName();
        return new ScheduleItemDTO(
                period,
                null,   // ItineraryItem 无开始时间
                null,   // ItineraryItem 无结束时间
                null,   // ItineraryItem 无 AttractionDTO 引用，不伪造
                activity
        );
    }

    // ---------------------------------------------------------------------
    // 二、List<ScheduleItemDTO> -> TravelPlan.periodPlans + 三个文字字段
    // ---------------------------------------------------------------------

    /**
     * 把新行程的 ScheduleItemDTO 列表回填到 TravelPlan：
     * - 按 period 分组生成 PeriodPlan，每个 ScheduleItemDTO 生成一个 ItineraryItem；
     * - morning/afternoon/eveningActivity 保存该时段第一个活动的展示文字，兼容旧页面。
     * 说明：ScheduleItemDTO 的 startTime/endTime/attraction 在 ItineraryItem 中无对应字段，
     * 转换时丢弃，不硬塞进 notes，避免伪造结构化数据。
     */
    public static void applyScheduleToPlan(TravelPlan plan, List<ScheduleItemDTO> schedule) {
        if (plan == null) {
            return;
        }
        List<ScheduleItemDTO> items = schedule == null ? Collections.emptyList() : schedule;

        List<PeriodPlan> periodPlans = new ArrayList<>();
        for (String period : List.of("MORNING", "AFTERNOON", "EVENING")) {
            List<ItineraryItem> activities = new ArrayList<>();
            for (ScheduleItemDTO item : items) {
                if (item == null || !period.equalsIgnoreCase(item.period())) {
                    continue;
                }
                activities.add(toItineraryItem(item));
            }
            if (!activities.isEmpty()) {
                PeriodPlan pp = new PeriodPlan(period);
                pp.setActivities(activities);
                periodPlans.add(pp);
            }
        }
        plan.setPeriodPlans(periodPlans);

        plan.setMorningActivity(findActivity(items, "MORNING"));
        plan.setAfternoonActivity(findActivity(items, "AFTERNOON"));
        plan.setEveningActivity(findActivity(items, "EVENING"));
    }

    /**
     * ScheduleItemDTO -> ItineraryItem。
     * - activity -> name；
     * - attraction.name 优先写入 name（若 activity 为空），attraction.poiId 写入 source（若 source 为空）；
     * - attraction 的坐标/地址/评分/营业时间不写入 ItineraryItem，避免伪造；
     * - startTime/endTime 丢弃（ItineraryItem 不承载时间）。
     */
    private static ItineraryItem toItineraryItem(ScheduleItemDTO item) {
        ItineraryItem it = new ItineraryItem();

        String name = item.activity();
        if ((name == null || name.isBlank()) && item.attraction() != null) {
            name = item.attraction().name();
        }
        it.setName(name == null || name.isBlank() ? "暂无安排" : name);

        AttractionDTO a = item.attraction();
        if (a != null) {
            // 只保存可核实的来源标识，不写坐标/地址等未承载字段
            if (a.source() != null && !a.source().isBlank()) {
                it.setSource(a.source());
            } else if (a.poiId() != null && !a.poiId().isBlank()) {
                it.setSource(a.poiId());
            }
            it.setVerificationStatus("VERIFIED");
        } else {
            it.setVerificationStatus("UNAVAILABLE");
        }
        return it;
    }

    private static String findActivity(List<ScheduleItemDTO> items, String period) {
        return items.stream()
                .filter(Objects::nonNull)
                .filter(i -> period.equalsIgnoreCase(i.period()))
                .map(ScheduleItemDTO::activity)
                .filter(a -> a != null && !a.isBlank())
                .findFirst()
                .orElse("暂无安排");
    }

    // ---------------------------------------------------------------------
    // 三、展示辅助
    // ---------------------------------------------------------------------

    /**
     * 为展示把真实景点名拼进 activity，例如“历史文化游览（广州博物馆）”。
     * 只生成展示文字，不修改底层 attraction 对象。
     */
    public static List<ScheduleItemDTO> enrichForDisplay(List<ScheduleItemDTO> schedule) {
        if (schedule == null) {
            return Collections.emptyList();
        }
        List<ScheduleItemDTO> result = new ArrayList<>(schedule.size());
        for (ScheduleItemDTO item : schedule) {
            if (item == null) {
                continue;
            }
            String activity = item.activity() == null ? "" : item.activity();
            if (item.attraction() != null && item.attraction().name() != null
                    && !activity.contains(item.attraction().name())) {
                activity = activity + "（" + item.attraction().name() + "）";
            }
            result.add(new ScheduleItemDTO(
                    item.period(), item.startTime(), item.endTime(),
                    item.attraction(), activity));
        }
        return result;
    }

    private static String safeText(String v) {
        return (v == null || v.isBlank()) ? "暂无安排" : v;
    }
}