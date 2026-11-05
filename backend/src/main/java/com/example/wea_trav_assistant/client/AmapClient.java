package com.example.wea_trav_assistant.client;

import com.example.wea_trav_assistant.config.AmapProperties;
import com.example.wea_trav_assistant.dto.TransportMode;
import com.example.wea_trav_assistant.exception.AmapErrorCode;
import com.example.wea_trav_assistant.exception.AmapException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.SocketTimeoutException;

/** Sole HTTP gateway for Amap Web Service APIs. */
@Component
public class AmapClient {
    private final RestClient client;
    private final AmapProperties properties;

    public AmapClient(AmapProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        this.client = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }

    public JsonNode searchPoi(String city, String keyword, int limit) {
        return get("/v5/place/text", builder -> builder
                .queryParam("keywords", keyword).queryParam("region", city)
                .queryParam("city_limit", "true").queryParam("page_size", limit));
    }

    public JsonNode getPoiDetail(String poiId) {
        return get("/v5/place/detail", builder -> builder.queryParam("id", poiId));
    }

    public JsonNode planRoute(TransportMode mode, String origin, String destination, String city) {
        String path = switch (mode) {
            case DRIVING -> "/v5/direction/driving";
            case WALKING -> "/v5/direction/walking";
            case CYCLING -> "/v5/direction/bicycling";
            case PUBLIC_TRANSPORT -> "/v5/direction/transit/integrated";
        };
        return get(path, builder -> {
            builder.queryParam("origin", origin).queryParam("destination", destination);
            // V5 returns route duration as an optional cost field for several route modes.
            builder.queryParam("show_fields", "cost");
            if (mode == TransportMode.PUBLIC_TRANSPORT && city != null && !city.isBlank()) {
                // Amap v5 transit accepts citycode, not the display city name.
                String cityCode = resolveCityCode(city);
                builder.queryParam("city1", cityCode).queryParam("city2", cityCode);
            }
            return builder;
        });
    }

    private String resolveCityCode(String city) {
        JsonNode root = get("/v3/config/district", builder -> builder
                .queryParam("keywords", city).queryParam("subdistrict", 0).queryParam("extensions", "base"));
        JsonNode district = root.path("districts").path(0);
        String cityCode = district.path("citycode").asText("").trim();
        if (cityCode.isEmpty() || "[]".equals(cityCode)) {
            throw new AmapException(AmapErrorCode.ROUTE_NOT_FOUND, "未找到该城市对应的高德公交城市代码");
        }
        return cityCode;
    }

    private JsonNode get(String path, java.util.function.UnaryOperator<org.springframework.web.util.UriBuilder> customizer) {
        if (properties.getKey() == null || properties.getKey().isBlank()) {
            throw new AmapException(AmapErrorCode.API_KEY_MISSING, "未配置高德服务 Key，请设置环境变量 AMAP_WEB_KEY");
        }
        try {
            JsonNode root = client.get().uri(builder -> {
                org.springframework.web.util.UriBuilder withKey = builder.path(path).queryParam("key", properties.getKey());
                return customizer.apply(withKey).build();
            }).retrieve().body(JsonNode.class);
            if (root == null) throw new AmapException(AmapErrorCode.API_UNAVAILABLE, "高德服务未返回有效数据");
            validateResponse(root);
            return root;
        } catch (AmapException exception) {
            throw exception;
        } catch (RestClientException exception) {
            if (exception instanceof ResourceAccessException && hasTimeoutCause(exception)) {
                throw new AmapException(AmapErrorCode.API_TIMEOUT, "高德服务请求超时，请稍后重试");
            }
            throw new AmapException(AmapErrorCode.API_UNAVAILABLE, "高德服务暂不可用，请稍后重试");
        }
    }

    private void validateResponse(JsonNode root) {
        String status = root.path("status").asText("1");
        if ("1".equals(status) || "0".equals(status) && root.path("data").isObject()) return;
        String infoCode = root.path("infocode").asText("");
        if ("10001".equals(infoCode) || "10002".equals(infoCode) || "10003".equals(infoCode)) {
            throw new AmapException(AmapErrorCode.API_KEY_INVALID, "高德服务 Key 无效或没有该服务权限");
        }
        if ("10004".equals(infoCode) || "10007".equals(infoCode)) {
            throw new AmapException(AmapErrorCode.API_QUOTA_EXCEEDED, "高德服务调用配额不足，请稍后重试");
        }
        throw new AmapException(AmapErrorCode.API_UNAVAILABLE, root.path("info").asText("高德服务调用失败"));
    }

    private boolean hasTimeoutCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
