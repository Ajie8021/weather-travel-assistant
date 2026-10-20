package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.CityData;
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
}
