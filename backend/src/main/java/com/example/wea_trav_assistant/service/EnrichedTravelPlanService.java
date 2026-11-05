package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.dto.PlanGenerateRequest;
import com.example.wea_trav_assistant.dto.RouteDTO;
import com.example.wea_trav_assistant.dto.ScheduleItemDTO;
import com.example.wea_trav_assistant.dto.TransportMode;
import com.example.wea_trav_assistant.dto.TravelPlanDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Creates a real-POI plan; weather conflict assessment remains a separate module. */
@Service
public class EnrichedTravelPlanService {
    private final PoiService poiService;
    private final RouteService routeService;

    public EnrichedTravelPlanService(PoiService poiService, RouteService routeService) {
        this.poiService = poiService;
        this.routeService = routeService;
    }

    public TravelPlanDTO generate(PlanGenerateRequest request) {
        List<AttractionDTO> attractions = poiService.searchAttractions(request.city(), request.preference(), 3);
        List<ScheduleItemDTO> schedule = new ArrayList<>();
        String[][] slots = {{"morning", "09:30", "11:30"}, {"afternoon", "13:30", "16:00"}, {"evening", "18:00", "20:00"}};
        for (int i = 0; i < attractions.size() && i < slots.length; i++) {
            schedule.add(new ScheduleItemDTO(slots[i][0], slots[i][1], slots[i][2], attractions.get(i), request.preference()));
        }
        List<RouteDTO> routes = new ArrayList<>();
        TransportMode mode = request.transportPreference() == null ? TransportMode.PUBLIC_TRANSPORT : request.transportPreference();
        for (int i = 0; i + 1 < attractions.size(); i++) {
            routes.addAll(routeService.planRoutes(attractions.get(i).poiId(), attractions.get(i + 1).poiId(), List.of(mode)));
        }
        // No ticket price or conflict is fabricated: both need data owned by other services.
        return new TravelPlanDTO(request.city(), request.date().toString(), List.copyOf(attractions),
                List.copyOf(routes), List.copyOf(schedule), request.budget(), List.of());
    }
}
