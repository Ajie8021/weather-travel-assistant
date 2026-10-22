
package com.example.wea_trav_assistant.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证结构化行程 DTO 的基本行为。
 */
class TravelPlanTest {

    @Test
    void shouldKeepLegacyFieldsAndSupportStructuredPlans() {
        // 旧字段必须继续可用，避免现有业务服务和页面被破坏。
        TravelPlan plan = new TravelPlan();
        plan.setMorningActivity("上午：户外游览");

        // 新字段应能保存结构化时段和活动。
        PeriodPlan morning = new PeriodPlan("MORNING");
        ItineraryItem activity = new ItineraryItem();
        activity.setName("公园漫步");
        activity.setCategory("OUTDOOR");
        activity.setDurationMinutes(60);
        activity.setVerificationStatus("UNVERIFIED");

        morning.setActivities(List.of(activity));
        plan.setPeriodPlans(List.of(morning));

        assertEquals("上午：户外游览", plan.getMorningActivity());
        assertEquals(1, plan.getPeriodPlans().size());
        assertEquals(
                "公园漫步",
                plan.getPeriodPlans().get(0)
                        .getActivities().get(0).getName());
    }

    @Test
    void shouldUseEmptyListsWhenNullIsProvided() {
        // 列表统一使用空列表，减少调用方的空指针判断。
        TravelPlan plan = new TravelPlan();
        plan.setPeriodPlans(null);
        plan.setDataSources(null);

        PeriodPlan period = new PeriodPlan();
        period.setActivities(null);

        assertNotNull(plan.getPeriodPlans());
        assertTrue(plan.getPeriodPlans().isEmpty());
        assertNotNull(plan.getDataSources());
        assertTrue(plan.getDataSources().isEmpty());
        assertNotNull(period.getActivities());
        assertTrue(period.getActivities().isEmpty());
    }

    @Test
    void shouldNotMarkUnverifiedActivityAsVerifiedByDefault() {
        // 未取得真实地点数据时，不能默认声称地点已经核实。
        ItineraryItem activity = new ItineraryItem();

        assertEquals("UNAVAILABLE", activity.getVerificationStatus());
    }

    @Test
    void shouldCopyIncomingActivityLists() {
        // 修改传入列表不应直接改变 DTO 内部的列表。
        PeriodPlan period = new PeriodPlan("MORNING");
        List<ItineraryItem> activities = new ArrayList<>();
        activities.add(new ItineraryItem());

        period.setActivities(activities);
        activities.clear();

        assertEquals(1, period.getActivities().size());
    }
}
