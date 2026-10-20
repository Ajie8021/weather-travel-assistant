package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.WeatherData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSessionStore {
    public static final class Session {
        private final List<Map<String, Object>> history = new ArrayList<>();
        private List<CityData> pendingCities = List.of();
        private CityData confirmedCity;
        private TravelRequest lastRequest;
        private TravelPlan lastPlan;
        private WeatherData lastWeather;
        private volatile Instant touchedAt = Instant.now();
        public List<Map<String, Object>> history() { return history; }
        public List<CityData> pendingCities() { return pendingCities; }
        public void pendingCities(List<CityData> value) { pendingCities = value; }
        public CityData confirmedCity() { return confirmedCity; }
        public void confirmedCity(CityData value) { confirmedCity = value; }
        public TravelRequest lastRequest() { return lastRequest; }
        public void lastRequest(TravelRequest value) { lastRequest = value; }
        public TravelPlan lastPlan() { return lastPlan; }
        public void lastPlan(TravelPlan value) { lastPlan = value; }
        public WeatherData lastWeather() { return lastWeather; }
        public void lastWeather(WeatherData value) { lastWeather = value; }
        public void touch() { touchedAt = Instant.now(); }
        public Instant touchedAt() { return touchedAt; }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    public ChatSessionStore() { }

    public String newId() { return UUID.randomUUID().toString(); }

    public Session get(String id) {
        cleanupExpired();
        return sessions.computeIfAbsent(id, ignored -> new Session());
    }

    public String selectPendingCity(Session session, String message) {
        List<CityData> candidates = session.pendingCities();
        if (candidates.isEmpty()) return null;
        String input = message.trim();
        CityData selected = null;
        try {
            String ordinal = input.replaceAll("[^0-9]", "");
            if (ordinal.isEmpty()) ordinal = switch (input) {
                case "第一个", "第1个", "第１个" -> "1";
                case "第二个", "第2个", "第２个" -> "2";
                case "第三个", "第3个", "第３个" -> "3";
                case "第四个", "第4个", "第４个" -> "4";
                case "第五个", "第5个", "第５个" -> "5";
                default -> "";
            };
            int index = Integer.parseInt(ordinal);
            if (index >= 1 && index <= candidates.size()) selected = candidates.get(index - 1);
        } catch (NumberFormatException ignored) { }
        if (selected == null) {
            String normalizedInput = normalize(input);
            List<CityData> exactLocations = candidates.stream().filter(candidate ->
                    normalize(candidate.name() + value(candidate.adminArea()) + value(candidate.country()))
                            .equals(normalizedInput)).toList();
            if (exactLocations.size() == 1) selected = exactLocations.getFirst();
        }
        if (selected == null) {
            List<CityData> regionMatches = candidates.stream().filter(candidate ->
                    candidate.adminArea() != null && normalizedInputContains(input, candidate.name())
                            && normalizedInputContains(input, candidate.adminArea())).toList();
            if (regionMatches.size() == 1) selected = regionMatches.getFirst();
        }
        if (selected == null) {
            List<CityData> nameMatches = candidates.stream()
                    .filter(candidate -> normalize(candidate.name()).equals(normalize(input))).toList();
            if (nameMatches.size() == 1) selected = nameMatches.getFirst();
        }
        if (selected == null) return "请回复候选编号或更完整的城市名称，例如“1”或“广州，广东省，中国”。";
        session.confirmedCity(selected);
        session.pendingCities(List.of());
        session.touch();
        return null;
    }

    private String value(String value) { return value == null ? "" : value; }
    private boolean normalizedInputContains(String input, String value) {
        return normalize(input).contains(normalize(value));
    }
    private String normalize(String value) { return value == null ? "" : value.toLowerCase().replaceAll("[\\s,，、-]", ""); }

    public void addMessage(Session session, String role, Object message) {
        session.history().add(Map.of("role", role, "content", String.valueOf(message)));
        while (session.history().size() > 24) session.history().remove(0);
        session.touch();
    }

    public void addRawMessage(Session session, Map<String, Object> message) {
        session.history().add(message);
        while (session.history().size() > 24) session.history().remove(0);
        session.touch();
    }

    private void cleanupExpired() {
        Instant expiry = Instant.now().minusSeconds(1800);
        sessions.entrySet().removeIf(entry -> entry.getValue().touchedAt().isBefore(expiry));
    }
}
