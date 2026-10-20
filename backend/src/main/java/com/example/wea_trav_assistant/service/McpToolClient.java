package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.config.AgentProperties;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Stateless Streamable HTTP MCP client with modern/legacy protocol negotiation. */
@Component
public class McpToolClient {
    private static final String MODERN_VERSION = "2026-07-28";
    private static final String LEGACY_VERSION = "2025-11-25";
    private static final String CLIENT_NAME = "weather-travel-agent";
    private final AgentProperties properties;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final AtomicLong ids = new AtomicLong(1);
    private volatile String protocolVersion;
    private volatile boolean modern;
    private volatile String sessionId;

    public McpToolClient(AgentProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return properties.getMcpEndpoint() != null && !properties.getMcpEndpoint().isBlank();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTools() {
        ensureNegotiated();
        Object result = rpc("tools/list", Map.of()).get("result");
        if (!(result instanceof Map<?, ?> resultMap) || !(resultMap.get("tools") instanceof List<?> tools)) {
            throw new IllegalStateException("MCP 服务未返回工具列表");
        }
        return (List<Map<String, Object>>) tools;
    }

    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        ensureNegotiated();
        return rpc("tools/call", Map.of("name", name, "arguments", arguments));
    }

    private synchronized void ensureNegotiated() {
        if (protocolVersion != null) return;
        HttpResult discovery = send("server/discover", Map.of("_meta", metadata(MODERN_VERSION)), MODERN_VERSION, true);
        Map<String, Object> parsed = parseBody(discovery.body(), discovery.requestId());
        List<String> advertised = supportedVersions(parsed);
        String selectedModern = advertised.stream().filter(version -> version.compareTo("2026-01-01") >= 0)
                .findFirst().orElse(null);
        if (selectedModern != null && discovery.status() < 400) {
            modern = true;
            protocolVersion = selectedModern;
            return;
        }

        // A modern endpoint may reject our preferred version with a structured list of supported versions.
        if (!advertised.isEmpty() && advertised.stream().anyMatch(version -> version.startsWith("2026-"))) {
            modern = true;
            protocolVersion = advertised.stream().filter(version -> version.startsWith("2026-")).findFirst().orElse(MODERN_VERSION);
            return;
        }

        modern = false;
        protocolVersion = advertised.stream().filter(version -> version.startsWith("2025-"))
                .findFirst().orElse(LEGACY_VERSION);
        legacyInitialize();
    }

    private void legacyInitialize() {
        HttpResult initialized = send("initialize", Map.of(
                "protocolVersion", protocolVersion,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", CLIENT_NAME, "version", "1.0.0")),
                protocolVersion, false);
        Map<String, Object> response = parseBody(initialized.body(), initialized.requestId());
        if (response.containsKey("error")) throw new IllegalStateException("MCP 初始化失败：" + response.get("error"));
        Object result = response.get("result");
        if (result instanceof Map<?, ?> map && map.get("protocolVersion") != null) {
            protocolVersion = String.valueOf(map.get("protocolVersion"));
        }
        sessionId = initialized.headers().getFirst("Mcp-Session-Id");
        sendNotification("notifications/initialized", Map.of());
    }

    private Map<String, Object> rpc(String method, Map<String, Object> params) {
        Map<String, Object> requestParams = new LinkedHashMap<>(params);
        if (modern) requestParams.put("_meta", metadata(protocolVersion));
        HttpResult result = send(method, requestParams, protocolVersion, modern);
        Map<String, Object> response = parseBody(result.body(), result.requestId());
        if (response.containsKey("error")) throw new IllegalStateException("MCP 调用失败：" + response.get("error"));
        return response;
    }

    private void sendNotification(String method, Map<String, Object> params) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);
        sendBody(notification, method, null, protocolVersion, false, null);
    }

    private HttpResult send(String method, Map<String, Object> params, String version, boolean useModernHeaders) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        long requestId = ids.getAndIncrement();
        request.put("id", requestId);
        request.put("method", method);
        request.put("params", params);
        String toolName = params.get("name") == null ? null : String.valueOf(params.get("name"));
        return sendBody(request, method, toolName, version, useModernHeaders, String.valueOf(requestId));
    }

    private HttpResult sendBody(Map<String, Object> body, String method, String toolName,
                                String version, boolean useModernHeaders, String requestId) {
        HttpResult result = client.post().uri(properties.getMcpEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> addHeaders(headers, method, toolName, version, useModernHeaders))
                .body(body)
                .exchange((request, response) -> readResponse(response));
        return new HttpResult(result.status(), result.body(), result.headers(), requestId);
    }

    private void addHeaders(HttpHeaders headers, String method, String toolName,
                            String version, boolean useModernHeaders) {
        if (sessionId != null && !useModernHeaders) headers.set("Mcp-Session-Id", sessionId);
        if (useModernHeaders) {
            headers.set("MCP-Protocol-Version", version);
            headers.set("Mcp-Method", method);
            if (toolName != null) headers.set("Mcp-Name", toolName);
        } else if (version != null && !"initialize".equals(method)) {
            headers.set("MCP-Protocol-Version", version);
        }
    }

    private HttpResult readResponse(ClientHttpResponse response) throws java.io.IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        HttpHeaders copy = new HttpHeaders();
        copy.putAll(response.getHeaders());
        return new HttpResult(response.getStatusCode().value(), body, copy, null);
    }

    private Map<String, Object> metadata(String version) {
        return Map.of(
                "io.modelcontextprotocol/protocolVersion", version,
                "io.modelcontextprotocol/clientInfo", Map.of("name", CLIENT_NAME, "version", "1.0.0"),
                "io.modelcontextprotocol/clientCapabilities", Map.of());
    }

    @SuppressWarnings("unchecked")
    private List<String> supportedVersions(Map<String, Object> response) {
        Object result = response.get("result");
        if (result instanceof Map<?, ?> map && map.get("supportedVersions") instanceof List<?> versions) {
            return versions.stream().map(String::valueOf).toList();
        }
        Object error = response.get("error");
        if (error instanceof Map<?, ?> errorMap && errorMap.get("data") instanceof Map<?, ?> data
                && data.get("supported") instanceof List<?> versions) {
            return versions.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(String body, String expectedId) {
        Map<String, Object> response = parseBodyOrEmpty(body);
        if (!response.isEmpty()) return response;
        for (String line : body.split("\\R")) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            try {
                Map<String, Object> event = mapper.readValue(data, Map.class);
                if (expectedId == null || (event.get("id") != null
                        && String.valueOf(event.get("id")).equals(expectedId))) return event;
            }
            catch (Exception ignored) { }
        }
        throw new IllegalStateException("MCP 流式响应中没有有效的 JSON-RPC 结果");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBodyOrEmpty(String body) {
        if (body == null || body.isBlank()) return Map.of();
        try { return mapper.readValue(body, Map.class); }
        catch (Exception ignored) { return Map.of(); }
    }

    private record HttpResult(int status, String body, HttpHeaders headers, String requestId) { }
}
