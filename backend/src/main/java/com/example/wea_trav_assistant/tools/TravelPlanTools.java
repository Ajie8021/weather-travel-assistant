
package com.example.wea_trav_assistant.tools;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.service.TravelPlanService;
import com.example.wea_trav_assistant.service.WeatherDataProvider;
import org.springframework.stereotype.Component;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 出行计划工具入口。
 *
 * 供后续 Agent/MCP 层调用。
 *
 * 本类只负责：
 * 1. 接收并校验工具输入；
 * 2. 获取指定城市、指定日期的天气数据；
 * 3. 调用 TravelPlanService 生成计划；
 * 4. 返回结构化 TravelPlan。
 *
 * 天气风险分析、活动推荐、穿衣建议等业务规则
 * 均由已有服务负责，本类不重复实现。
 */
@Component
public class TravelPlanTools {

    private final WeatherDataProvider weatherDataProvider;
    private final TravelPlanService travelPlanService;

    /**
     * 通过构造器注入依赖，便于 Spring 管理和单元测试。
     */
    public TravelPlanTools(
            WeatherDataProvider weatherDataProvider,
            TravelPlanService travelPlanService) {
        this.weatherDataProvider = weatherDataProvider;
        this.travelPlanService = travelPlanService;
    }

    /**
     * MCP 工具：生成单日出行计划。
     *
     * MCP 工具名为 generate_travel_plan。
     * Agent 可以根据用户的城市、日期和活动偏好调用本方法。
     *
     * 方法内部仍然复用 WeatherDataProvider 和 TravelPlanService，
     * 不在 MCP 工具层重复实现天气查询、风险判断或活动推荐。
     *
     * @param city 目的地城市，例如广州
     * @param date 出行日期，格式为 YYYY-MM-DD
     * @param activityPreference 活动偏好，只允许 OUTDOOR、INDOOR、FOOD
     * @return 包含天气概况、活动建议、穿衣建议、携带物品和注意事项的计划
     */
    @McpTool(
            name = "generate_travel_plan",
            description = "根据目的地城市、出行日期和活动偏好，查询对应天气并生成单日出行计划。"
                    + "活动偏好可选 OUTDOOR（户外）、INDOOR（室内）或 FOOD（美食）。"
                    + "计划包含天气概况、上午/下午/晚间活动、穿衣建议、携带物品和注意事项。",
            generateOutputSchema = true
    )
    public TravelPlan generateTravelPlan(
            @McpToolParam(
                    description = "目的地城市名称，例如广州、深圳",
                    required = true
            )
            String city,

            @McpToolParam(
                    description = "出行日期，格式为 YYYY-MM-DD，表示目的地当地日期",
                    required = true
            )
            String date,

            @McpToolParam(
                    description = "活动偏好：OUTDOOR 表示户外，INDOOR 表示室内，FOOD 表示美食",
                    required = true
            )
            String activityPreference) {

        // 1. 校验城市，避免空城市进入天气查询流程。
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("城市不能为空");
        }

        // 2. 校验并解析日期。
        // 使用 ISO_LOCAL_DATE，要求输入类似 2026-10-20。
        if (date == null || date.trim().isEmpty()) {
            throw new IllegalArgumentException("出行日期不能为空");
        }

        final LocalDate travelDate;
        try {
            travelDate = LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "出行日期格式错误，应为 YYYY-MM-DD", ex);
        }

        // 3. 校验活动偏好。
        // 不在工具层自行转换或猜测用户意图，
        // 具体业务规则仍由 TravelPlanService 统一校验。
        if (activityPreference == null
                || activityPreference.trim().isEmpty()) {
            throw new IllegalArgumentException("活动偏好不能为空");
        }

        // 4. 获取目标城市、目标日期的标准化天气数据。
        // 不允许用其他日期的天气冒充目标日期的天气。
        WeatherData weather = weatherDataProvider.getWeather(
                city.trim(), travelDate);

        // 5. 构造现有业务服务所使用的请求 DTO。
        TravelRequest request = new TravelRequest();
        request.setCity(city.trim());
        request.setDate(travelDate);
        request.setActivityPreference(activityPreference.trim());

        // 6. 委托现有计划服务完成风险分析和计划生成。
        // 日期匹配、天气风险和具体建议由业务服务统一处理。
        return travelPlanService.generatePlan(request, weather);
    }
}
