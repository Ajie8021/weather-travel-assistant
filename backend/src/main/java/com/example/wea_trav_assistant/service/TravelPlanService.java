
package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherDataIncompleteException;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 出行计划生成服务。
 *
 * 职责：
 * 1. 获取天气规则分析结果；
 * 2. 根据活动偏好和天气风险推荐活动；
 * 3. 生成穿衣建议、携带物品及注意事项；
 * 4. 组装 TravelPlan。
 *
 * 本类不负责请求天气 API，也不负责解析原始天气 JSON。
 */
@Service
public class TravelPlanService {

    private final TravelRuleService travelRuleService;

    public TravelPlanService(TravelRuleService travelRuleService) {
        this.travelRuleService = travelRuleService;
    }

    /**
     * 根据用户请求和已标准化的天气数据生成单日计划。
     *
     * @param request 用户请求，包含城市、日期和活动偏好
     * @param weather 天气服务返回的标准化天气数据
     * @return 完整的单日出行计划
     */
    public TravelPlan generatePlan(
            TravelRequest request,
            WeatherData weather) {

        // 先检查请求，避免空参数进入后续业务逻辑。
        validateRequest(request);

        // 由规则服务统一进行天气分类、风险识别和日期校验。
        TravelRuleService.WeatherAnalysis analysis =
                travelRuleService.analyze(request.getDate(), weather);

        // 用户请求的日期已通过规则服务校验。
        TravelPlan plan = new TravelPlan();

        // 优先使用天气数据中经过地理编码确认的城市名称；
        // 天气数据未提供城市名时，使用用户输入的城市名。
        String city = weather.getCity();
        if (city == null || city.trim().isEmpty()) {
            city = request.getCity().trim();
        }

        plan.setCity(city);
        plan.setDate(request.getDate().format(
                DateTimeFormatter.ISO_LOCAL_DATE));

        // 天气概况必须只描述实际可用的数据。
        plan.setWeatherSummary(buildWeatherSummary(weather, analysis));

        // 按偏好和天气风险分别生成三个时段的活动建议。
        plan.setMorningActivity(
                recommendActivity(request.getActivityPreference(),
                        analysis, "上午"));

        plan.setAfternoonActivity(
                recommendActivity(request.getActivityPreference(),
                        analysis, "下午"));

        plan.setEveningActivity(
                recommendActivity(request.getActivityPreference(),
                        analysis, "晚间"));

        // 穿衣、物品和提醒均由同一份天气分析结果生成，
        // 减少活动安排与风险提醒互相矛盾的情况。
        plan.setClothingAdvice(buildClothingAdvice(weather, analysis));
        plan.setItemsToPack(buildItemsToPack(weather, analysis));
        plan.setWarnings(buildWarnings(analysis));

        return plan;
    }

    /**
     * 检查必填请求参数。
     * 更完整的表单校验也可以由 Controller 或 Bean Validation 完成；
     * 此处仍保留必要检查，避免服务被其他调用方错误使用。
     */
    private void validateRequest(TravelRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("出行请求不能为空");
        }

        if (request.getCity() == null
                || request.getCity().trim().isEmpty()) {
            throw new IllegalArgumentException("目的地城市不能为空");
        }

        if (request.getDate() == null) {
            throw new IllegalArgumentException("出行日期不能为空");
        }

        String preference = request.getActivityPreference();
        if (!"OUTDOOR".equals(preference)
                && !"INDOOR".equals(preference)
                && !"FOOD".equals(preference)) {
            throw new IllegalArgumentException("活动偏好不合法");
        }
    }

    /**
     * 生成天气概况。
     * 缺失的温度不补成 0℃，未知天气代码也不描述为晴天。
     */
    private String buildWeatherSummary(
            WeatherData weather,
            TravelRuleService.WeatherAnalysis analysis) {

        StringBuilder summary = new StringBuilder();
        summary.append("天气：").append(conditionLabel(analysis));

        if (weather.getMinTemp() != null
                || weather.getMaxTemp() != null) {
            summary.append("；气温");

            if (weather.getMinTemp() != null) {
                summary.append("最低 ")
                        .append(formatNumber(weather.getMinTemp()))
                        .append("℃");
            } else {
                summary.append("最低气温未知");
            }

            summary.append("，");

            if (weather.getMaxTemp() != null) {
                summary.append("最高 ")
                        .append(formatNumber(weather.getMaxTemp()))
                        .append("℃");
            } else {
                summary.append("最高气温未知");
            }
        } else {
            summary.append("；最高和最低气温数据不足");
        }

        // 只在有真实数据时显示降水概率和风速。
        if (weather.getPrecipitationProbability() != null) {
            summary.append("；降水概率 ")
                    .append(weather.getPrecipitationProbability())
                    .append("%");
        }

        if (weather.getWindSpeed() != null) {
            summary.append("；风速 ")
                    .append(formatNumber(weather.getWindSpeed()))
                    .append(" km/h");
        }

        return summary.toString();
    }

    /**
     * 根据偏好和天气风险推荐某一时段的活动。
     */
    private String recommendActivity(
            String preference,
            TravelRuleService.WeatherAnalysis analysis,
            String period) {

        // 雷暴、冻雨等严重天气优先级最高，避免推荐户外活动。
        if (analysis.hasSevereWeather()) {
            if ("FOOD".equals(preference)) {
                return "建议选择室内餐厅体验当地美食，避免户外活动";
            }
            return period + "：安排室内休闲活动，如室内展览、文化体验或休息；"
                    + "避免户外游览，并关注天气变化。";
        }

        // 天气分类未知时，不能据此断言户外条件适宜。
        if (analysis.getCondition()
                == TravelRuleService.WeatherCondition.UNKNOWN) {
            return period + "：优先选择方便调整的室内活动；"
                    + "天气情况尚不能完整确认，出行前请再次核实。";
        }

        // 可能影响户外安全或舒适度的风险，优先提供室内替代方案。
        if (requiresIndoorAlternative(analysis)) {
            if ("FOOD".equals(preference)) {
                return period + "：安排室内用餐或美食体验，"
                        + "尽量选择交通方便、无需长时间户外步行的安排。";
            }

            return period + "：建议安排室内休闲、文化体验或室内展览；"
                    + "如需外出，尽量缩短户外停留时间，并根据天气调整。";
        }

        // 高温或低温不一定意味着必须取消所有户外活动，
        // 但需要缩短活动时间、增加休息或加强保暖。
        boolean temperatureRisk = hasTemperatureRisk(analysis);

        if ("OUTDOOR".equals(preference)) {
            if (temperatureRisk) {
                return period + "：可考虑短时、低强度的户外游览，"
                        + "增加休息，并根据气温做好防暑或保暖。";
            }
            return period + "：安排轻松的户外步行、城市漫步或公园游览；"
                    + "具体活动地点由出行者自行选择。";
        }

        if ("INDOOR".equals(preference)) {
            return period + "：安排室内休闲、文化体验、室内展览或阅读休息。";
        }

        // FOOD 偏好只推荐通用餐饮体验，不虚构店名或营业信息。
        return period + "：安排当地美食体验或室内用餐，"
                + "具体餐饮地点请自行确认营业时间和交通情况。";
    }

    /**
     * 判断是否应优先给户外活动提供室内替代方案。
     *
     * 温度风险单独处理：可根据实际情况降低户外活动强度，
     * 不在所有高温或低温场景中一律认定必须室内活动。
     */
    private boolean requiresIndoorAlternative(
            TravelRuleService.WeatherAnalysis analysis) {

        Set<TravelRuleService.WeatherRisk> risks = analysis.getRisks();

        return risks.contains(TravelRuleService.WeatherRisk.RAIN)
                || risks.contains(TravelRuleService.WeatherRisk.SNOW)
                || risks.contains(TravelRuleService.WeatherRisk.FOG)
                || risks.contains(TravelRuleService.WeatherRisk.HIGH_WIND)
                || risks.contains(
                TravelRuleService.WeatherRisk.HIGH_PRECIPITATION_PROBABILITY)
                || risks.contains(TravelRuleService.WeatherRisk.UNKNOWN_WEATHER);
    }

    /**
     * 判断是否存在高温或低温风险。
     */
    private boolean hasTemperatureRisk(
            TravelRuleService.WeatherAnalysis analysis) {

        Set<TravelRuleService.WeatherRisk> risks = analysis.getRisks();

        return risks.contains(
                TravelRuleService.WeatherRisk.HIGH_TEMPERATURE)
                || risks.contains(
                TravelRuleService.WeatherRisk.LOW_TEMPERATURE);
    }

    /**
     * 根据天气和风险生成穿衣建议。
     */
    private String buildClothingAdvice(
            WeatherData weather,
            TravelRuleService.WeatherAnalysis analysis) {

        List<String> advice = new ArrayList<>();

        Set<TravelRuleService.WeatherRisk> risks = analysis.getRisks();

        if (risks.contains(TravelRuleService.WeatherRisk.HIGH_TEMPERATURE)) {
            advice.add("气温较高，建议穿着轻薄透气的衣物");
        }

        if (risks.contains(TravelRuleService.WeatherRisk.LOW_TEMPERATURE)) {
            advice.add("气温较低，建议增加保暖衣物");
        }

        if (risks.contains(TravelRuleService.WeatherRisk.RAIN)
                || risks.contains(
                TravelRuleService.WeatherRisk.HIGH_PRECIPITATION_PROBABILITY)
                || risks.contains(TravelRuleService.WeatherRisk.SEVERE_WEATHER)) {
            advice.add("建议选择便于应对降水的衣物和鞋履");
        }

        if (risks.contains(TravelRuleService.WeatherRisk.HIGH_WIND)) {
            advice.add("风力较大，建议穿着防风外套");
        }

        if (advice.isEmpty()) {
            if (weather.getMinTemp() != null
                    && weather.getMaxTemp() != null) {
                advice.add("请结合昼夜温差和个人体感选择合适衣物");
            } else {
                advice.add("温度信息不完整，请出发前核实当地天气并据此选择衣物");
            }
        }

        return String.join("；", advice);
    }

    /**
     * 生成随身物品清单。
     * 使用 LinkedHashSet 去重，同时保留稳定的展示顺序。
     */
    private List<String> buildItemsToPack(
            WeatherData weather,
            TravelRuleService.WeatherAnalysis analysis) {

        Set<String> items = new LinkedHashSet<>();
        Set<TravelRuleService.WeatherRisk> risks = analysis.getRisks();

        if (risks.contains(TravelRuleService.WeatherRisk.RAIN)
                || risks.contains(
                TravelRuleService.WeatherRisk.HIGH_PRECIPITATION_PROBABILITY)
                || risks.contains(TravelRuleService.WeatherRisk.SEVERE_WEATHER)) {
            items.add("雨伞或其他防雨用品");
        }

        if (risks.contains(TravelRuleService.WeatherRisk.HIGH_TEMPERATURE)) {
            items.add("饮用水");
            items.add("防晒用品");
        }

        if (risks.contains(TravelRuleService.WeatherRisk.LOW_TEMPERATURE)
                || risks.contains(TravelRuleService.WeatherRisk.HIGH_WIND)) {
            items.add("保暖或防风外套");
        }

        // 降水量缺失时不推断无降水；只在数据存在且大于 0 时补充提醒。
        if (weather.getPrecipitation() != null
                && weather.getPrecipitation() > 0) {
            items.add("防水用品");
        }

        // 即使没有额外物品，也返回空列表，不返回 null。
        return new ArrayList<>(items);
    }

    /**
     * 将规则服务识别出的风险提示合并到计划中。
     * 保留所有已识别风险，避免只提示其中一种。
     */
    private List<String> buildWarnings(
            TravelRuleService.WeatherAnalysis analysis) {

        Set<String> warnings = new LinkedHashSet<>();

        // 复制天气规则服务已经生成的风险提醒。
        warnings.addAll(analysis.getWarnings());

        // 数据不足提示也要传递给最终计划。
        warnings.addAll(analysis.getInformationNotes());

        return new ArrayList<>(warnings);
    }

    /**
     * 将内部天气分类转换成面向用户的中文描述。
     */
    private String conditionLabel(
            TravelRuleService.WeatherAnalysis analysis) {

        switch (analysis.getCondition()) {
            case CLEAR:
                return "晴朗";
            case CLOUDY:
                return "少云或多云";
            case FOG:
                return "雾";
            case DRIZZLE:
                return "毛毛雨";
            case RAIN:
                return "降雨";
            case FREEZING_DRIZZLE:
                return "冻毛毛雨";
            case FREEZING_RAIN:
                return "冻雨";
            case SNOW:
                return "降雪";
            case RAIN_SHOWERS:
                return "阵雨";
            case SNOW_SHOWERS:
                return "阵雪";
            case THUNDERSTORM:
                return "雷暴";
            default:
                return "天气情况未知";
        }
    }

    /**
     * 避免把温度格式化为多余的小数位。
     */
    private String formatNumber(Double value) {
        if (value == null) {
            return "未知";
        }

        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }

        return String.format(Locale.ROOT, "%.1f", value);
    }
}
