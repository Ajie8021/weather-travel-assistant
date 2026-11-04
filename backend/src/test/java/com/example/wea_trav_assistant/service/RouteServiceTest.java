package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.client.AmapClient;
import com.example.wea_trav_assistant.exception.AmapErrorCode;
import com.example.wea_trav_assistant.exception.AmapException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class RouteServiceTest {
    @Test
    void requiresAtLeastOneTransportMode() {
        RouteService service = new RouteService(mock(AmapClient.class), mock(PoiService.class));

        AmapException error = assertThrows(AmapException.class,
                () -> service.planRoutes("origin", "destination", List.of()));

        assertEquals(AmapErrorCode.INVALID_INPUT, error.getCode());
    }
}
