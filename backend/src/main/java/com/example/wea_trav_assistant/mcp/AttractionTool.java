package com.example.wea_trav_assistant.mcp;

import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.service.PoiService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Business-level attraction tools; no Amap HTTP details are exposed to the model. */
@Component
public class AttractionTool {
    private final PoiService poiService;
    public AttractionTool(PoiService poiService) { this.poiService = poiService; }

    @McpTool(name = "search_attractions", description = "Search real Amap attractions for a city and preference. Return actual POI IDs, coordinates and source; never invent places.", generateOutputSchema = true)
    public List<AttractionDTO> searchAttractions(
            @McpToolParam(description = "Destination city, for example 广州", required = true) String city,
            @McpToolParam(description = "User preference or keyword, such as 历史文化、美食、自然风光", required = true) String keyword,
            @McpToolParam(description = "Maximum result count, 1 to 10", required = false) Integer limit) {
        return poiService.searchAttractions(city, keyword, limit == null ? 5 : limit);
    }

    @McpTool(name = "get_attraction_detail", description = "Get one real attraction's detail using a POI ID returned by search_attractions.", generateOutputSchema = true)
    public AttractionDTO getAttractionDetail(@McpToolParam(description = "Actual POI ID returned by search_attractions", required = true) String poiId) {
        return poiService.getAttractionDetail(poiId);
    }
}
