package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.config.AgentProperties;
import com.example.wea_trav_assistant.dto.ChatRequest;
import com.example.wea_trav_assistant.dto.ChatResponse;
import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherException;
import com.example.wea_trav_assistant.service.ChatSessionStore.Session;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {
    private static final String SYSTEM_PROMPT = """
            你是天气与出行助手。你必须通过提供的工具查询真实天气、生成计划；绝不能编造工具调用、天气或地点信息。
            用户只给城市名时，先调用 search_city。若返回多个候选，列出候选并等待用户明确选择，不能自行选首项。
            对日期使用 ISO 日期格式，日期表示目的地当地日期；相对日期以当前日期为准。工具报错时如实说明，不伪造结果。
            生成行程时优先使用当前会话已确认的城市、日期和偏好。用户只修改一个条件时继承其他条件。
            缺失天气字段必须说明数据不足，不能用 0 或晴天代替。只根据工具返回的结构化结果展示天气卡片和计划。
            回复使用简洁中文。工具状态由实际调用结果决定。
            """;

    private final AgentProperties properties;
    private final ChatSessionStore sessions;
    private final WeatherService weatherService;
    private final TravelPlanService planService;
    private final McpToolClient mcpClient;
    private final ObjectMapper mapper;
    private final RestClient llmClient;
    private volatile Set<String> remoteToolNames = Set.of();

    public AgentService(AgentProperties properties, ChatSessionStore sessions,
                        WeatherService weatherService, TravelPlanService planService,
                        McpToolClient mcpClient, ObjectMapper mapper) {
        this.properties = properties;
        this.sessions = sessions;
        this.weatherService = weatherService;
        this.planService = planService;
        this.mcpClient = mcpClient;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(45_000);
        this.llmClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? sessions.newId() : request.sessionId().trim();
        if (sessionId.length() > 100 || !sessionId.matches("[A-Za-z0-9._-]+")) {
            return new ChatResponse(sessionId, "会话标识无效，请重新开始对话。", "FAILED", List.of(), null, null);
        }
        Session session = sessions.get(sessionId);
        synchronized (session) {
        String selectionPrompt = sessions.selectPendingCity(session, request.message());
        if (selectionPrompt != null) {
            sessions.addMessage(session, "user", request.message());
            sessions.addMessage(session, "assistant", selectionPrompt);
            return response(sessionId, selectionPrompt, "NEEDS_INPUT", List.of(), session);
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return new ChatResponse(sessionId,
                    "智能体模型尚未配置。请在运行环境中设置 DASHSCOPE_API_KEY，并重启服务后再试。",
                    "FAILED", List.of(), null, null);
        }

        sessions.addMessage(session, "user", request.message());
        List<ChatResponse.ToolCallResult> calls = new ArrayList<>();
        try {
            List<Map<String, Object>> tools = availableTools();
            String reply = runToolLoop(session, tools, calls);
            sessions.addMessage(session, "assistant", reply);
            String status = session.pendingCities().isEmpty() ? "COMPLETED" : "NEEDS_INPUT";
            return response(sessionId, reply, status, calls, session);
        } catch (Exception exception) {
            String message = safeMessage(exception);
            sessions.addMessage(session, "assistant", message);
            return response(sessionId, message, "FAILED", calls, session);
        }
        }
    }

    private String runToolLoop(Session session, List<Map<String, Object>> tools,
                               List<ChatResponse.ToolCallResult> calls) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        Set<String> failedToolRequests = new HashSet<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT + "\n当前日期：" + LocalDate.now()));
        messages.addAll(session.history());
        for (int round = 0; round < properties.getMaxToolCalls(); round++) {
            Map<String, Object> response = llmClient.post()
                    .uri(properties.getBaseUrl().replaceAll("/$", "") + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(Map.of("model", properties.getModel(), "messages", messages,
                            "tools", tools, "tool_choice", "auto", "temperature", 0.2))
                    .retrieve().body(Map.class);
            Map<String, Object> assistant = choiceMessage(response);
            Object toolCallsValue = assistant.get("tool_calls");
            if (!(toolCallsValue instanceof List<?> toolCalls) || toolCalls.isEmpty()) {
                Object content = assistant.get("content");
                return content == null ? "我暂时无法生成有效回复，请换一种说法再试。" : String.valueOf(content);
            }

            messages.add(assistant);
            sessions.addRawMessage(session, assistant);
            for (Object rawCall : toolCalls) {
                if (!(rawCall instanceof Map<?, ?> toolCall)) continue;
                String callId = String.valueOf(toolCall.get("id"));
                Map<?, ?> function = toolCall.get("function") instanceof Map<?, ?> f ? f : Map.of();
                String name = String.valueOf(function.get("name"));
                Map<String, Object> arguments = parseArguments(function.get("arguments"));
                if (calls.size() >= properties.getMaxToolCalls()) {
                    Map<String, Object> limitError = Map.of("error", "本轮工具调用已达到安全上限");
                    messages.add(toolMessage(callId, limitError));
                    sessions.addRawMessage(session, toolMessage(callId, limitError));
                    continue;
                }
                long startedAt = System.nanoTime();
                try {
                    Object result = executeTool(name, arguments, session);
                    messages.add(toolMessage(callId, result));
                    sessions.addRawMessage(session, toolMessage(callId, result));
                    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                    calls.add(new ChatResponse.ToolCallResult(name, "SUCCESS", "实际执行，耗时 " + elapsedMs + " ms"));
                } catch (Exception error) {
                    Map<String, Object> toolError = Map.of("error", safeMessage(error));
                    messages.add(toolMessage(callId, toolError));
                    sessions.addRawMessage(session, toolMessage(callId, toolError));
                    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                    calls.add(new ChatResponse.ToolCallResult(name, "FAILED", safeMessage(error) + "（耗时 " + elapsedMs + " ms）"));
                    if (!failedToolRequests.add(toolFailureKey(name, arguments))) {
                        throw new IllegalStateException("工具 " + name + " 对相同参数连续调用失败，已停止重试：" + safeMessage(error));
                    }
                }
            }
        }
        return "本次工具调用已达到安全上限，请缩小请求范围后重试。";
    }

    private String toolFailureKey(String name, Map<String, Object> arguments) {
        try {
            return name + ":" + mapper.writeValueAsString(arguments);
        } catch (Exception ignored) {
            return name + ":" + arguments;
        }
    }

    private Map<String, Object> choiceMessage(Map<String, Object> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("模型没有返回有效结果");
        }
        Object choice = choices.getFirst();
        if (!(choice instanceof Map<?, ?> map) || !(map.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("模型响应格式无效");
        }
        return (Map<String, Object>) message;
    }

    private List<Map<String, Object>> availableTools() {
        if (mcpClient.isConfigured()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            Set<String> discoveredNames = ConcurrentHashMap.newKeySet();
            for (Map<String, Object> tool : mcpClient.listTools()) {
                String name = String.valueOf(tool.get("name"));
                if (!(name.equals("search_city") || name.equals("get_weather")
                        || name.equals("generate_travel_plan"))) continue;
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", name);
                function.put("description", tool.getOrDefault("description", "天气与出行工具"));
                function.put("parameters", tool.getOrDefault("inputSchema", Map.of("type", "object")));
                tools.add(Map.of("type", "function", "function", function));
                discoveredNames.add(name);
            }
            if (tools.isEmpty()) throw new IllegalStateException("MCP 服务没有注册约定的天气/规划工具");
            remoteToolNames = Set.copyOf(discoveredNames);
            if (!remoteToolNames.contains("generate_travel_plan")) {
                tools.add(functionTool("generate_travel_plan", "按已确认城市、天气、日期和活动偏好生成单日计划。",
                        objectSchema(Map.of("city", stringSchema("城市名"), "date", stringSchema("YYYY-MM-DD"),
                                "activityPreference", enumSchema(List.of("OUTDOOR", "INDOOR", "FOOD"))),
                                List.of("city", "date", "activityPreference"))));
            }
            tools.add(functionTool("modify_travel_plan", "仅修改已生成计划的一个时段，保留其他已确认条件。",
                    objectSchema(Map.of("period", enumSchema(List.of("morning", "afternoon", "evening")),
                            "activityPreference", enumSchema(List.of("OUTDOOR", "INDOOR", "FOOD"))),
                            List.of("period", "activityPreference"))));
            return tools;
        }
        return List.of(
                functionTool("search_city", "按城市名称搜索地点候选。多个候选必须等待用户确认。",
                        objectSchema(Map.of("city", stringSchema("待搜索的城市名")), List.of("city"))),
                functionTool("get_weather", "查询已确认地点在指定日期的真实天气。",
                        objectSchema(Map.of("location", objectSchema(Map.of(
                                        "name", stringSchema("城市名"), "adminArea", stringSchema("行政区"),
                                        "country", stringSchema("国家"), "latitude", numberSchema(),
                                        "longitude", numberSchema(), "timezone", stringSchema("时区")),
                                        List.of("name", "latitude", "longitude", "timezone")),
                                "date", stringSchema("YYYY-MM-DD")), List.of("location", "date"))),
                functionTool("generate_travel_plan", "按已确认城市、天气、日期和活动偏好生成单日计划。",
                        objectSchema(Map.of("city", stringSchema("城市名"), "date", stringSchema("YYYY-MM-DD"),
                                "activityPreference", enumSchema(List.of("OUTDOOR", "INDOOR", "FOOD"))),
                                List.of("city", "date", "activityPreference"))),
                functionTool("modify_travel_plan", "仅修改已生成计划的一个时段，保留其他已确认条件。",
                        objectSchema(Map.of("period", enumSchema(List.of("morning", "afternoon", "evening")),
                                "activityPreference", enumSchema(List.of("OUTDOOR", "INDOOR", "FOOD"))),
                                List.of("period", "activityPreference")))
        );
    }

    private Object executeTool(String name, Map<String, Object> args, Session session) throws Exception {
        if (mcpClient.isConfigured()) {
            if (name.equals("modify_travel_plan")) return modifyPlan(args, session);
            if (!List.of("search_city", "get_weather", "generate_travel_plan").contains(name)) {
                throw new IllegalArgumentException("该工具不在允许列表中");
            }
            if (name.equals("generate_travel_plan") && !remoteToolNames.contains(name)) {
                return generatePlan(args, session);
            }
            if (name.equals("get_weather")) confirmedLocation(args, session);
            if (name.equals("generate_travel_plan")) {
                if (!session.pendingCities().isEmpty()) {
                    throw new IllegalArgumentException("地点尚未确认，请先让用户选择候选城市");
                }
                CityData confirmed = session.confirmedCity();
                String requestedCity = optionalString(args.get("city"));
                if (confirmed == null || (requestedCity != null && !requestedCity.equalsIgnoreCase(confirmed.name()))) {
                    throw new IllegalArgumentException("规划前必须先搜索并确认目的地城市");
                }
            }
            Map<String, Object> result = mcpClient.callTool(name, args);
            Object value = extractMcpResult(result.get("result"));
            if (name.equals("search_city")) acceptRemoteCandidates(value, session);
            if (name.equals("get_weather") && value instanceof Map<?, ?> map) {
                WeatherData weather = weatherFromMap(map);
                session.lastWeather(weather);
                session.lastPlan(null);
                TravelRequest previous = session.lastRequest();
                session.lastRequest(new TravelRequest(weather.getCity(), weather.getDate(),
                        previous == null ? "OUTDOOR" : previous.getActivityPreference()));
            }
            if (name.equals("generate_travel_plan") && value instanceof Map<?, ?> map) {
                Object plan = map.get("travelPlan");
                if (plan instanceof Map<?, ?> planMap) {
                    TravelPlan parsedPlan = planFromMap(planMap);
                    session.lastPlan(parsedPlan);
                    LocalDate plannedDate = parsedPlan.getDate() == null ? null : LocalDate.parse(parsedPlan.getDate());
                    String preference = optionalString(args.get("activityPreference"));
                    if (preference == null) preference = session.lastRequest() == null ? "OUTDOOR" : session.lastRequest().getActivityPreference();
                    if (plannedDate != null) session.lastRequest(new TravelRequest(parsedPlan.getCity(), plannedDate, preference));
                    Object weather = map.get("weather");
                    if (weather instanceof Map<?, ?> weatherMap) session.lastWeather(weatherFromMap(weatherMap));
                } else if (map.containsKey("morningActivity")) {
                    TravelPlan parsedPlan = planFromMap(map);
                    session.lastPlan(parsedPlan);
                    LocalDate plannedDate = parsedPlan.getDate() == null ? null : LocalDate.parse(parsedPlan.getDate());
                    String preference = optionalString(args.get("activityPreference"));
                    if (preference == null && session.lastRequest() != null) preference = session.lastRequest().getActivityPreference();
                    if (plannedDate != null) session.lastRequest(new TravelRequest(parsedPlan.getCity(), plannedDate,
                            preference == null ? "OUTDOOR" : preference));
                }
            }
            return value;
        }
        return switch (name) {
            case "search_city" -> searchCity(args, session);
            case "get_weather" -> getWeather(args, session);
            case "generate_travel_plan" -> generatePlan(args, session);
            case "modify_travel_plan" -> modifyPlan(args, session);
            default -> throw new IllegalArgumentException("该工具不在允许列表中");
        };
    }

    private Object searchCity(Map<String, Object> args, Session session) {
        String city = requiredString(args.get("city"), "请提供城市名称");
        List<CityData> candidates = weatherService.searchCityCandidates(city);
        if (candidates.isEmpty()) return Map.of("status", "NOT_FOUND", "message", "没有找到该城市，请补充地区或国家");
        if (candidates.size() == 1) {
            CityData confirmed = candidates.getFirst();
            session.confirmCity(confirmed);
            return Map.of("status", "CONFIRMED", "location", confirmed);
        }
        session.pendingCities(candidates);
        List<Map<String, Object>> summaries = candidates.stream().map(c -> Map.<String, Object>of(
                "name", c.name(), "adminArea", c.adminArea() == null ? "" : c.adminArea(),
                "country", c.country() == null ? "" : c.country())).toList();
        return Map.of("status", "NEEDS_CONFIRMATION", "candidates", summaries,
                "message", "找到多个地点，先让用户选择，不得查询天气");
    }

    private Object getWeather(Map<String, Object> args, Session session) {
        CityData selected = confirmedLocation(args, session);
        LocalDate date = resolveDate(args.get("date"), session);
        WeatherData weather = weatherService.getWeather(selected, date);
        session.lastWeather(weather);
        session.lastPlan(null);
        TravelRequest previous = session.lastRequest();
        String preference = previous == null ? "OUTDOOR" : previous.getActivityPreference();
        session.lastRequest(new TravelRequest(selected.name(), date, preference));
        return weather;
    }

    private Object generatePlan(Map<String, Object> args, Session session) {
        String city = optionalString(args.get("city"));
        CityData selected = session.confirmedCity();
        if (city != null && (selected == null || !city.equalsIgnoreCase(selected.name()))) {
            if (selected != null) session.confirmedCity(null);
            throw new IllegalArgumentException("请先调用 search_city 搜索并确认新的目的地");
        }
        if (selected == null) throw new IllegalArgumentException("请先告诉我城市，我会先确认地点再查询天气");
        LocalDate date = resolveDate(args.get("date"), session);
        String preference = optionalString(args.get("activityPreference"));
        if (preference == null && session.lastRequest() != null) preference = session.lastRequest().getActivityPreference();
        if (preference == null) preference = "OUTDOOR";
        preference = preference.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("OUTDOOR", "INDOOR", "FOOD").contains(preference)) {
            throw new IllegalArgumentException("活动偏好需为 OUTDOOR、INDOOR 或 FOOD");
        }
        TravelRequest request = new TravelRequest(selected.name(), date, preference);
        WeatherData weather = session.lastWeather() != null
                && date.equals(session.lastWeather().getDate())
                && selected.name().equalsIgnoreCase(session.lastWeather().getCity())
                ? session.lastWeather() : weatherService.getWeather(selected, date);
        TravelPlan plan = planService.generatePlan(request, weather);
        session.lastRequest(request);
        session.lastWeather(weather);
        session.lastPlan(plan);
        return Map.of("weather", weather, "travelPlan", plan);
    }

    private Object modifyPlan(Map<String, Object> args, Session session) {
        TravelPlan plan = session.lastPlan();
        WeatherData weather = session.lastWeather();
        if (plan == null) throw new IllegalArgumentException("当前会话还没有计划，请先生成一份计划");
        if (weather == null && session.confirmedCity() != null) {
            weather = weatherService.getWeather(session.confirmedCity(), LocalDate.parse(plan.getDate()));
            session.lastWeather(weather);
        }
        if (weather == null) throw new IllegalArgumentException("当前会话缺少可验证的天气数据，请先重新生成计划");

        String period = requiredString(args.get("period"), "请指定要修改的时段");
        String preference = requiredString(
                args.get("activityPreference"), "请指定修改偏好")
                .toUpperCase(java.util.Locale.ROOT);

        // 检查当前会话是否保留了原始计划条件。
        TravelRequest original = session.lastRequest();
        if (original == null) {
            throw new IllegalArgumentException(
                    "当前会话缺少计划条件，请先重新生成计划");
        }

        // 委托 B 的业务服务完成局部修改。
        // AgentService 不再自行生成新计划、复制时段字段。
        TravelPlan updatedPlan = planService.modifyPlanForPeriod(
                plan, weather, period, preference);

        // 只有业务方法成功返回后，才更新会话中的计划。
        // 失败时不覆盖已有计划。
        session.lastPlan(updatedPlan);

        return Map.of(
                "travelPlan", updatedPlan,
                "updatedPeriod", period
        );
    }

    private CityData confirmedLocation(Map<String, Object> args, Session session) {
        if (!session.pendingCities().isEmpty()) {
            throw new IllegalArgumentException("地点尚未确认，请先让用户选择候选城市");
        }
        CityData confirmed = session.confirmedCity();
        if (confirmed == null) throw new IllegalArgumentException("地点尚未确认，请先调用 search_city 并让用户选择候选");
        Object location = args.get("location");
        if (!(location instanceof Map<?, ?> map)) throw new IllegalArgumentException("天气查询必须传入已确认的完整地点对象");
        String name = optionalString(map.get("name"));
        Object lat = map.get("latitude");
        Object lon = map.get("longitude");
        String timezone = optionalString(map.get("timezone"));
        if (!confirmed.name().equals(name)
                || !(lat instanceof Number latitude) || Math.abs(latitude.doubleValue() - confirmed.latitude()) > 0.0001
                || !(lon instanceof Number longitude) || Math.abs(longitude.doubleValue() - confirmed.longitude()) > 0.0001
                || !confirmed.timezone().equals(timezone)) {
            throw new IllegalArgumentException("查询地点与用户确认的城市不一致");
        }
        return confirmed;
    }

    private LocalDate resolveDate(Object raw, Session session) {
        String value = optionalString(raw);
        if ((value == null || value.isBlank()) && session.lastRequest() != null) return session.lastRequest().getDate();
        if (value == null || value.isBlank()) throw new IllegalArgumentException("请说明出行日期");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日期无效，请使用 YYYY-MM-DD 格式");
        }
    }

    private String requiredString(Object value, String message) {
        String text = optionalString(value);
        if (text == null || text.isBlank()) throw new IllegalArgumentException(message);
        return text.trim();
    }
    private String optionalString(Object value) { return value == null ? null : String.valueOf(value).trim(); }

    private Map<String, Object> objectSchema(Map<String, Object> properties, java.util.Collection<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }
    private Map<String, Object> stringSchema(String description) { return Map.of("type", "string", "description", description); }
    private Map<String, Object> numberSchema() { return Map.of("type", "number"); }
    private Map<String, Object> enumSchema(List<String> values) { return Map.of("type", "string", "enum", values); }
    private Map<String, Object> functionTool(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description, "parameters", parameters));
    }

    private Map<String, Object> parseArguments(Object raw) throws Exception {
        if (raw instanceof Map<?, ?> map) return (Map<String, Object>) map;
        if (raw == null) return Map.of();
        return mapper.readValue(String.valueOf(raw), Map.class);
    }

    private Map<String, Object> toolMessage(String callId, Object result) {
        try {
            return Map.of("role", "tool", "tool_call_id", callId,
                    "content", mapper.writeValueAsString(result));
        } catch (Exception e) {
            return Map.of("role", "tool", "tool_call_id", callId, "content", "工具返回结果无法序列化");
        }
    }

    private Object extractMcpResult(Object result) throws Exception {
        if (!(result instanceof Map<?, ?> map)) return result;
        if (Boolean.TRUE.equals(map.get("isError"))) throw new IllegalStateException("MCP 工具执行失败");
        if (map.get("structuredContent") != null) return map.get("structuredContent");
        if (map.get("content") instanceof List<?> content) {
            List<String> texts = content.stream().filter(Map.class::isInstance)
                    .map(Map.class::cast).filter(item -> "text".equals(item.get("type")))
                    .map(item -> String.valueOf(item.get("text"))).toList();
            if (texts.size() == 1) {
                try { return mapper.readValue(texts.getFirst(), Object.class); }
                catch (Exception ignored) { return texts.getFirst(); }
            }
            return texts;
        }
        return result;
    }

    private void acceptRemoteCandidates(Object value, Session session) {
        Object candidatesValue = value;
        if (value instanceof Map<?, ?> map) {
            Object location = map.get("location");
            if (location instanceof Map<?, ?> cityMap) {
                CityData city = cityFromMap(cityMap);
                session.confirmCity(city);
                return;
            }
            if (map.get("candidates") != null) candidatesValue = map.get("candidates");
        }
        if (candidatesValue instanceof List<?> list) {
            List<CityData> candidates = list.stream().filter(Map.class::isInstance)
                    .map(Map.class::cast).map(this::cityFromMap).toList();
            if (candidates.size() == 1) {
                session.confirmCity(candidates.getFirst());
            }
            else if (!candidates.isEmpty()) {
                session.pendingCities(candidates);
            }
        }
    }

    private CityData cityFromMap(Map<?, ?> map) {
        return new CityData(stringValue(map.get("name")), optionalString(map.get("adminArea")),
                optionalString(map.get("country")), numberValue(map.get("latitude")),
                numberValue(map.get("longitude")), optionalString(map.get("timezone")) == null
                ? "auto" : optionalString(map.get("timezone")));
    }

    private WeatherData weatherFromMap(Map<?, ?> map) {
        WeatherData weather = new WeatherData();
        weather.setCity(optionalString(map.get("city")));
        weather.setAdminArea(optionalString(map.get("adminArea")));
        weather.setCountry(optionalString(map.get("country")));
        weather.setLatitude(optionalNumber(map.get("latitude")));
        weather.setLongitude(optionalNumber(map.get("longitude")));
        String date = optionalString(map.get("date"));
        if (date != null) weather.setDate(LocalDate.parse(date));
        weather.setWeatherCode(optionalInteger(map.get("weatherCode")));
        weather.setMaxTemp(optionalNumber(map.get("maxTemp")));
        weather.setMinTemp(optionalNumber(map.get("minTemp")));
        weather.setPrecipitationProbability(optionalInteger(map.get("precipitationProbability")));
        weather.setPrecipitation(optionalNumber(map.get("precipitation")));
        weather.setWindSpeed(optionalNumber(map.get("windSpeed")));
        weather.setSource(optionalString(map.get("source")));
        return weather;
    }

    private TravelPlan planFromMap(Map<?, ?> map) {
        TravelPlan plan = new TravelPlan();
        plan.setCity(optionalString(map.get("city")));
        plan.setDate(optionalString(map.get("date")));
        plan.setWeatherSummary(optionalString(map.get("weatherSummary")));
        plan.setMorningActivity(optionalString(map.get("morningActivity")));
        plan.setAfternoonActivity(optionalString(map.get("afternoonActivity")));
        plan.setEveningActivity(optionalString(map.get("eveningActivity")));
        plan.setClothingAdvice(optionalString(map.get("clothingAdvice")));
        if (map.get("itemsToPack") instanceof List<?> items) plan.setItemsToPack(items.stream().map(String::valueOf).toList());
        if (map.get("warnings") instanceof List<?> warnings) plan.setWarnings(warnings.stream().map(String::valueOf).toList());
        return plan;
    }

    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private double numberValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        throw new IllegalArgumentException("MCP 城市候选缺少有效经纬度");
    }
    private Double optionalNumber(Object value) { return value instanceof Number number ? number.doubleValue() : null; }
    private Integer optionalInteger(Object value) { return value instanceof Number number ? number.intValue() : null; }

    private ChatResponse response(String sessionId, String reply, String status,
                                  List<ChatResponse.ToolCallResult> calls, Session session) {
        boolean weatherChanged = calls.stream().anyMatch(call -> call.status().equals("SUCCESS")
                && (call.name().equals("get_weather") || call.name().equals("generate_travel_plan")
                || call.name().equals("modify_travel_plan")));
        boolean planChanged = calls.stream().anyMatch(call -> call.status().equals("SUCCESS")
                && (call.name().equals("generate_travel_plan") || call.name().equals("modify_travel_plan")));
        return new ChatResponse(sessionId, reply, status, List.copyOf(calls),
                weatherChanged ? session.lastWeather() : null,
                planChanged ? session.lastPlan() : null);
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof WeatherException weatherException) return weatherException.getMessage();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "智能体服务暂时不可用，请稍后重试。";
        if (message.contains("401") || message.contains("403")) return "模型鉴权失败，请检查 DASHSCOPE_API_KEY 配置。";
        if (message.length() > 240) return "天气或模型服务暂时不可用，请稍后重试。";
        return message;
    }
}
