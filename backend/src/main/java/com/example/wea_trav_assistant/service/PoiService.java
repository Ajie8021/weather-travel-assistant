package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.client.AmapClient;
import com.example.wea_trav_assistant.dto.AttractionDTO;
import com.example.wea_trav_assistant.exception.AmapErrorCode;
import com.example.wea_trav_assistant.exception.AmapException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PoiService {
    private static final Map<String, String> PREFERENCE_KEYWORDS = Map.of(
            "历史文化", "博物馆 历史建筑 纪念馆",
            "自然风光", "山 湖 公园 植物园",
            "休闲娱乐", "游乐园 商业街 文化街区",
            "美食", "餐厅 特色美食 当地小吃",
            "亲子", "动物园 科技馆 自然教育场所");
    private final AmapClient client;

    public PoiService(AmapClient client) { this.client = client; }

    public List<AttractionDTO> searchAttractions(String city, String keyword, int limit) {
        if (city == null || city.isBlank()) throw new AmapException(AmapErrorCode.INVALID_INPUT, "城市不能为空");
        if (keyword == null || keyword.isBlank()) throw new AmapException(AmapErrorCode.INVALID_INPUT, "景点偏好或关键词不能为空");
        int safeLimit = Math.max(1, Math.min(limit, 10));
        String query = PREFERENCE_KEYWORDS.getOrDefault(keyword.trim(), keyword.trim());
        List<AttractionDTO> attractions = PoiMapper.attractions(client.searchPoi(city.trim(), query, safeLimit));
        if (attractions.isEmpty()) throw new AmapException(AmapErrorCode.POI_NOT_FOUND, "没有找到符合条件的真实景点，请更换关键词后重试");
        return attractions;
    }

    public AttractionDTO getAttractionDetail(String poiId) {
        if (poiId == null || poiId.isBlank()) throw new AmapException(AmapErrorCode.INVALID_INPUT, "POI_ID 不能为空");
        AttractionDTO detail = PoiMapper.detail(client.getPoiDetail(poiId.trim()));
        if (detail == null || detail.poiId() == null) throw new AmapException(AmapErrorCode.POI_NOT_FOUND, "没有找到该景点详情");
        return detail;
    }
}
