package com.example.wea_trav_assistant.travel;

import com.example.wea_trav_assistant.common.PreferenceMapper;
import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.PlanModifyRequest;
import com.example.wea_trav_assistant.dto.RouteDTO;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 行程修改服务：保留未变条件，局部改时段；不调用外部 API。
 */
@Service
public class PlanModifyService {

    public TravelPlanDTO apply(TravelPlanDTO current, PlanModifyRequest req) {
        if (current == null) {
            throw new IllegalArgumentException("currentPlan 不能为空");
        }

        String city = req.getCity() != null && !req.getCity().isBlank()
                ? req.getCity() : current.city();
        String date = req.getDate() != null && !req.getDate().isBlank()
                ? req.getDate() : current.date();
        Double budget = req.getBudget() != null ? req.getBudget() : current.budget();

        boolean locationChanged = !equalsSafe(city, current.city())
                || !equalsSafe(date, current.date());

        List<ScheduleItemDTO> schedule = new ArrayList<>();
        if (current.schedule() != null) {
            for (ScheduleItemDTO item : current.schedule()) {
                schedule.add(modifyItem(item, req, locationChanged));
            }
        }

        return new TravelPlanDTO(
                city,
                date,
                locationChanged ? new ArrayList<AttractionDTO>() : current.attractions(),
                locationChanged ? new ArrayList<RouteDTO>() : current.routes(),
                schedule,
                budget,
                current.conflicts() == null ? new ArrayList<>() : current.conflicts());
    }

    private ScheduleItemDTO modifyItem(ScheduleItemDTO item, PlanModifyRequest req,
                                       boolean locationChanged) {
        if (item == null) return null;

        String period = item.period();
        boolean targeted = req.getTargetPeriod() != null
                && req.getTargetPeriod().equalsIgnoreCase(period);

        String newPref = null;
        if (targeted) {
            newPref = req.getNewActivityPreference();
        } else if (req.getTargetPeriod() == null && req.getNewActivityPreference() != null) {
            newPref = req.getNewActivityPreference();
        }

        AttractionDTO attraction = locationChanged ? null : item.attraction();
        String activity = item.activity();

        if (newPref != null && !newPref.isBlank()) {
            attraction = null;
            activity = activityLabel(newPref);
        } else if (locationChanged) {
            activity = "待按新地点重新安排";
        }

        return new ScheduleItemDTO(period, item.startTime(), item.endTime(), attraction, activity);
    }

    private String activityLabel(String preference) {
        String p = preference.toUpperCase(Locale.ROOT);
        switch (p) {
            case "OUTDOOR": return "户外活动（待按新偏好重查景点）";
            case "INDOOR":  return "室内活动（待按新偏好重查景点）";
            case "FOOD":    return "美食体验（待按新偏好重查地点）";
            default:        return preference + "（待按新偏好重查景点）";
        }
    }

    private boolean equalsSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public boolean needsRegenerateAttractions(PlanModifyRequest req) {
        return req.getCity() != null || req.getDate() != null
                || req.getTargetPeriod() != null
                || req.getNewActivityPreference() != null;
    }

    public String normalizePreference(String preference) {
        if (preference == null) return null;
        if (PreferenceMapper.isValidActivityPreference(preference)) {
            return PreferenceMapper.toAttractionPreference(preference);
        }
        return preference;
    }
}