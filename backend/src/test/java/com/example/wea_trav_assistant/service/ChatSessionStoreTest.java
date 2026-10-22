package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.WeatherData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatSessionStoreTest {
    @Test
    void contextsAreIsolatedBySessionId() {
        ChatSessionStore store = new ChatSessionStore();
        ChatSessionStore.Session first = store.get("session-one");
        ChatSessionStore.Session second = store.get("session-two");
        first.confirmedCity(new CityData("广州", "广东省", "中国", 23.1, 113.2, "Asia/Shanghai"));

        assertNotNull(first.confirmedCity());
        assertNull(second.confirmedCity());
        assertNotSame(first, second);
    }

    @Test
    void requiresCandidateChoiceAndAcceptsCandidateNumber() {
        ChatSessionStore store = new ChatSessionStore();
        ChatSessionStore.Session session = store.get("session-one");
        session.pendingCities(List.of(
                new CityData("Springfield", "Illinois", "United States", 39.78, -89.64, "America/Chicago"),
                new CityData("Springfield", "Massachusetts", "United States", 42.10, -72.59, "America/New_York")));

        assertNotNull(store.selectPendingCity(session, "另一个城市"));
        assertNull(session.confirmedCity());
        assertNull(store.selectPendingCity(session, "第2个"));
        assertEquals("Massachusetts", session.confirmedCity().adminArea());
        assertTrue(session.pendingCities().isEmpty());
    }

    @Test
    void reconfirmingTheSameLocationPreservesConversationContext() {
        ChatSessionStore store = new ChatSessionStore();
        ChatSessionStore.Session session = store.get("session-one");
        CityData guangzhou = new CityData("广州", "广东省", "中国", 23.13, 113.26, "Asia/Shanghai");
        WeatherData weather = new WeatherData();
        TravelPlan plan = new TravelPlan();
        session.confirmCity(guangzhou);
        session.lastWeather(weather);
        session.lastPlan(plan);

        session.confirmCity(new CityData("广州", "广东", "中国", 23.13001, 113.26001, "Asia/Shanghai"));

        assertSame(weather, session.lastWeather());
        assertSame(plan, session.lastPlan());
    }

    @Test
    void confirmingANewLocationClearsOldWeatherAndPlan() {
        ChatSessionStore store = new ChatSessionStore();
        ChatSessionStore.Session session = store.get("session-one");
        session.confirmCity(new CityData("广州", "广东省", "中国", 23.13, 113.26, "Asia/Shanghai"));
        session.lastWeather(new WeatherData());
        session.lastPlan(new TravelPlan());

        session.confirmCity(new CityData("深圳", "广东省", "中国", 22.54, 114.06, "Asia/Shanghai"));

        assertEquals("深圳", session.confirmedCity().name());
        assertNull(session.lastWeather());
        assertNull(session.lastPlan());
    }
}
