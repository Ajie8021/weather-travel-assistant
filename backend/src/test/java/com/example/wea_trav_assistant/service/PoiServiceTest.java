package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.client.AmapClient;
import com.example.wea_trav_assistant.exception.AmapErrorCode;
import com.example.wea_trav_assistant.exception.AmapException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoiServiceTest {
    @Test
    void rejectsBlankCityBeforeCallingProvider() {
        AmapClient client = mock(AmapClient.class);
        PoiService service = new PoiService(client);

        AmapException error = assertThrows(AmapException.class,
                () -> service.searchAttractions(" ", "历史文化", 5));

        assertEquals(AmapErrorCode.INVALID_INPUT, error.getCode());
    }

    @Test
    void expandsKnownPreferenceToTargetedPoiKeywords() {
        AmapClient client = mock(AmapClient.class);
        PoiService service = new PoiService(client);
        AmapException expected = new AmapException(AmapErrorCode.API_UNAVAILABLE, "test provider failure");
        when(client.searchPoi(eq("广州"), eq("博物馆 历史建筑 纪念馆"), anyInt())).thenThrow(expected);

        AmapException actual = assertThrows(AmapException.class,
                () -> service.searchAttractions("广州", "历史文化", 5));

        assertEquals(expected, actual);
        verify(client).searchPoi("广州", "博物馆 历史建筑 纪念馆", 5);
    }

    @Test
    void rejectsBlankPoiIdBeforeRequestingDetail() {
        PoiService service = new PoiService(mock(AmapClient.class));

        AmapException error = assertThrows(AmapException.class,
                () -> service.getAttractionDetail(""));

        assertEquals(AmapErrorCode.INVALID_INPUT, error.getCode());
    }
}
