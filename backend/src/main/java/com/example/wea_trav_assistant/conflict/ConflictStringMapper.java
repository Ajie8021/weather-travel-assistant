package com.example.wea_trav_assistant.conflict;

import com.example.wea_trav_assistant.dto.TravelPlanDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 把结构化冲突投影为 TravelPlanDTO.conflicts（List<String>）。
 * 不改 record 定义，只做字符串化。
 */
public final class ConflictStringMapper {

    private ConflictStringMapper() {}

    public static String toLine(WeatherConflictDTO c) {
        if (c == null) return "";
        String period = c.getPeriod() == null ? "ALL_DAY" : c.getPeriod();
        String msg = c.getMessage() == null ? "" : c.getMessage();
        String sug = c.getSuggestion() == null ? "" : c.getSuggestion();
        return "[" + c.getSeverity() + "][" + period + "] " + msg
                + (sug.isEmpty() ? "" : " 建议：" + sug);
    }

    public static List<String> toLines(List<WeatherConflictDTO> conflicts) {
        List<String> lines = new ArrayList<>();
        if (conflicts == null) return lines;
        for (WeatherConflictDTO c : conflicts) {
            String line = toLine(c);
            if (!line.isEmpty() && !lines.contains(line)) lines.add(line);
        }
        return lines;
    }

    public static TravelPlanDTO withConflicts(TravelPlanDTO plan,
                                              List<WeatherConflictDTO> conflicts) {
        if (plan == null) return null;
        return new TravelPlanDTO(
                plan.city(),
                plan.date(),
                plan.attractions(),
                plan.routes(),
                plan.schedule(),
                plan.budget(),
                toLines(conflicts));
    }
}