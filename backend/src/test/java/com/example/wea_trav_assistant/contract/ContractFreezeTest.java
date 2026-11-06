package com.example.wea_trav_assistant.contract;

import com.example.wea_trav_assistant.common.PreferenceMapper;
import com.example.wea_trav_assistant.common.TravelPlanDtoAdapter;
import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TravelPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 契约测试：锁定阶段0 决定的字段语义。
 * 不依赖网络、不启动 Spring 上下文。
 */
class ContractFreezeTest {

    @Test
    void schedule_items_from_legacy_text_fields_never_null() {
        TravelPlan plan = new TravelPlan();
        plan.setCity("广州");
        plan.setDate("2026-11-06");
        plan.setMorningActivity("上午户外");
        plan.setAfternoonActivity("下午美食");
        plan.setEveningActivity("晚间休闲");

        List<ScheduleItemDTO> items = TravelPlanDtoAdapter.toScheduleItems(plan);

        assertEquals(3, items.size());
        assertEquals("MORNING", items.get(0).period());
        assertNull(items.get(0).attraction(), "旧计划没有真实景点，attraction 必须为 null");
        assertNull(items.get(0).startTime(), "旧计划没有具体时间，必须为 null");
        assertEquals("上午户外", items.get(0).activity());
    }

    @Test
    void apply_schedule_backfills_text_and_period_plans() {
        AttractionDTO a = new AttractionDTO(
                "B0FFH123", "广州博物馆", "越秀区镇海路99号", "广州",
                "博物馆", 113.26, 23.13, null, null, "AMAP");
        List<ScheduleItemDTO> items = List.of(
                new ScheduleItemDTO("MORNING", "09:00", "11:30", a, "历史文化游览"),
                new ScheduleItemDTO("AFTERNOON", "14:00", "16:00", null, "美食体验"),
                new ScheduleItemDTO("EVENING", "19:00", "20:30", null, "休闲散步")
        );

        TravelPlan plan = new TravelPlan();
        TravelPlanDtoAdapter.applyScheduleToPlan(plan, items);

        assertEquals("历史文化游览", plan.getMorningActivity());
        assertEquals("美食体验", plan.getAfternoonActivity());
        assertEquals("休闲散步", plan.getEveningActivity());
        assertEquals(3, plan.getPeriodPlans().size());
        assertEquals("MORNING", plan.getPeriodPlans().get(0).getPeriod());
        assertEquals(1, plan.getPeriodPlans().get(0).getActivities().size());
        // ItineraryItem 不承载时间，断言 name 和 source 被正确写入
        assertEquals("历史文化游览", plan.getPeriodPlans().get(0).getActivities().get(0).getName());
        assertEquals("AMAP", plan.getPeriodPlans().get(0).getActivities().get(0).getSource());
    }

    @Test
    void missing_period_should_be_placeholder_not_fabricated() {
        TravelPlan plan = new TravelPlan();
        TravelPlanDtoAdapter.applyScheduleToPlan(plan, List.of());

        assertEquals("暂无安排", plan.getMorningActivity());
        assertEquals("暂无安排", plan.getAfternoonActivity());
        assertEquals("暂无安排", plan.getEveningActivity());
        assertNotNull(plan.getPeriodPlans());
        assertTrue(plan.getPeriodPlans().isEmpty());
    }

    @Test
    void preference_mapping_is_bidirectional_and_safe() {
        assertEquals("自然风光", PreferenceMapper.toAttractionPreference("OUTDOOR"));
        assertEquals("历史文化", PreferenceMapper.toAttractionPreference("INDOOR"));
        assertEquals("美食", PreferenceMapper.toAttractionPreference("FOOD"));
        assertEquals("OUTDOOR", PreferenceMapper.toActivityPreference("自然风光"));
        assertEquals("INDOOR", PreferenceMapper.toActivityPreference("历史文化"));
        assertEquals("FOOD", PreferenceMapper.toActivityPreference("美食"));
        assertEquals("休闲娱乐", PreferenceMapper.toAttractionPreference("UNKNOWN"));
        assertTrue(PreferenceMapper.isValidActivityPreference("outdoor"));
        assertFalse(PreferenceMapper.isValidActivityPreference("UNKNOWN"));
    }
}