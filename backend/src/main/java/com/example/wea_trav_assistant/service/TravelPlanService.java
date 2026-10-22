
package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.TravelPlan;
import com.example.wea_trav_assistant.dto.TravelRequest;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.dto.PeriodPlan;
import com.example.wea_trav_assistant.dto.ItineraryItem;
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

        // 请求城市与天气数据城市必须一致，避免使用其他城市的天气生成计划。
        if (city != null
                && !request.getCity().trim().equalsIgnoreCase(city.trim())) {
            throw new WeatherDataIncompleteException(
                    "请求城市与天气数据城市不一致，无法生成出行计划"
            );
        }

        plan.setCity(city);
        plan.setDate(request.getDate().format(
                DateTimeFormatter.ISO_LOCAL_DATE));

        // 记录本次计划采用的活动偏好，便于展示和后续局部修改。
        plan.setActivityPreference(request.getActivityPreference());

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

        // 将原有三个时段的建议转换为结构化数据。
        // 保留旧文本字段，兼容已有调用方和测试。
        plan.setPeriodPlans(buildPeriodPlans(
                request.getActivityPreference(),
                analysis,
                plan.getMorningActivity(),
                plan.getAfternoonActivity(),
                plan.getEveningActivity()));

        // 暂无可信 POI、地图和价格数据时，不伪造预算金额。
        plan.setBudgetCurrency("CNY");
        plan.setBudgetMin(null);
        plan.setBudgetMax(null);
        plan.setBudgetNote(
                "暂无可靠费用数据。交通、餐饮及门票价格均未核实，"
                        + "请在确定具体地点后再估算预算。");

        // 根据天气风险提供保守的备选安排。
        plan.setAlternativePlan(
                buildAlternativePlan(analysis));

        // 标明天气数据来源状态，以及地点、路线和价格数据的缺口。
        plan.setDataSources(buildDataSources(weather));

        // 穿衣、物品和提醒均由同一份天气分析结果生成，
        // 减少活动安排与风险提醒互相矛盾的情况。
        plan.setClothingAdvice(buildClothingAdvice(weather, analysis));
        plan.setItemsToPack(buildItemsToPack(weather, analysis));
        plan.setWarnings(buildWarnings(analysis));

        return plan;
    }

    /**
     * 把三个时段的原有活动建议转换为结构化行程。
     *
     * 当前阶段没有接入可信 POI/地图服务，因此这里只组织活动建议，
     * 不生成虚构的景点名称、餐厅名称或路线耗时。
     */
    private List<PeriodPlan> buildPeriodPlans(
            String preference,
            TravelRuleService.WeatherAnalysis analysis,
            String morningActivity,
            String afternoonActivity,
            String eveningActivity) {

        List<PeriodPlan> plans = new ArrayList<>();

        // 每个时段独立建模，方便后续只替换指定时段。
        plans.add(buildPeriodPlan(
                "MORNING", preference, analysis,
                morningActivity, 120));

        plans.add(buildPeriodPlan(
                "AFTERNOON", preference, analysis,
                afternoonActivity, 120));

        plans.add(buildPeriodPlan(
                "EVENING", preference, analysis,
                eveningActivity, 90));

        return plans;
    }

    /**
     * 创建单个时段。
     *
     * durationMinutes 是规划阶段的建议活动时长，不是交通耗时，
     * 也不代表某个真实地点已确认可停留这么久。
     */
    private PeriodPlan buildPeriodPlan(
            String period,
            String preference,
            TravelRuleService.WeatherAnalysis analysis,
            String activityText,
            int durationMinutes) {

        PeriodPlan periodPlan = new PeriodPlan(period);

        ItineraryItem item = new ItineraryItem();

        // 暂无可信地点数据，必须明确标记，而不是编造 POI。
        item.setName("具体活动地点待核实");
        item.setCategory(resolveActivityCategory(preference, analysis));
        item.setDurationMinutes(durationMinutes);

        // 不存在真实路线查询结果时，不给出路线耗时或已验证的交通结论。
        item.setTransportToNext(
                "暂无路线数据；确定具体地点后再核实交通方式和游览顺序");

        // 原有天气规则产生的建议作为备注保留下来。
        item.setNotes(activityText
                + "；当前为活动类型建议，具体地点和营业情况尚未核实。");

        item.setSource("暂无可信 POI 数据源");
        item.setVerificationStatus("UNAVAILABLE");

        periodPlan.setActivities(List.of(item));
        return periodPlan;
    }

    /**
     * 根据实际推荐内容确定活动类别。
     *
     * 恶劣天气或天气信息不完整时，非餐饮偏好优先标记为室内活动。
     */
    private String resolveActivityCategory(
            String preference,
            TravelRuleService.WeatherAnalysis analysis) {

        if ("FOOD".equals(preference)) {
            return "餐饮体验";
        }

        if (analysis.hasSevereWeather()
                || analysis.getCondition()
                == TravelRuleService.WeatherCondition.UNKNOWN
                || requiresIndoorAlternative(analysis)) {
            return "室内活动";
        }

        if ("OUTDOOR".equals(preference)) {
            return "户外活动";
        }

        return "室内活动";
    }

    /**
     * 生成天气变化或恶劣天气下的保守备选方案。
     * 不涉及未经查询的具体地点或路线。
     */
    private String buildAlternativePlan(
            TravelRuleService.WeatherAnalysis analysis) {

        if (analysis.hasSevereWeather()
                || requiresIndoorAlternative(analysis)) {
            return "优先选择交通方便的室内活动，避免不安全的户外安排；"
                    + "具体场所、营业情况和交通路线需出行前核实。";
        }

        if (hasTemperatureRisk(analysis)) {
            return "缩短户外活动时间，增加休息，并按实际气温做好防暑或保暖；"
                    + "必要时改为室内活动，具体地点待核实。";
        }

        return "如天气发生变化，可调整为室内活动或缩短户外行程；"
                + "具体场所及交通路线需进一步核实。";
    }

    /**
     * 只记录实际可说明的数据来源。
     *
     * 天气来源取自 WeatherData；地点、路线和费用尚未接入可信数据源，
     * 因此明确写出缺口，避免将其误认为已核实。
     */
    private List<String> buildDataSources(WeatherData weather) {

        List<String> sources = new ArrayList<>();

        if (weather.getSource() != null
                && !weather.getSource().isBlank()) {
            sources.add("天气数据来源：" + weather.getSource());
        } else {
            sources.add("天气数据来源未在 WeatherData 中提供");
        }

        sources.add("地点、路线和费用：暂无可信 POI/地图数据，尚未核实");

        return sources;
    }

    /**
     * 只修改当前计划中的一个时段。
     *
     * 业务约定：
     * 1. 根据当前计划的城市和日期重新生成活动建议；
     * 2. 只替换用户指定时段的活动字段；
     * 3. 其余时段和天气相关字段保持原值；
     * 4. 复用 generatePlan，避免重复实现天气风险判断。
     *
     * @param currentPlan 当前会话中已经生成的计划
     * @param weather 当前计划对应的标准化天气数据
     * @param period 要修改的时段：morning、afternoon、evening
     * @param activityPreference 新时段的活动偏好
     * @return 修改后的新计划，不直接修改传入的旧计划
     */
    public TravelPlan modifyPlanForPeriod(
            TravelPlan currentPlan,
            WeatherData weather,
            String period,
            String activityPreference) {

        // 1. 检查当前计划，避免没有旧计划时执行局部修改。
        if (currentPlan == null) {
            throw new IllegalArgumentException("当前没有出行计划，请先生成计划");
        }

        // 2. 统一时段格式，并检查时段是否合法。
        if (period == null || period.trim().isEmpty()) {
            throw new IllegalArgumentException("请指定要修改的时段");
        }

        String normalizedPeriod = period.trim().toLowerCase(Locale.ROOT);

        if (!"morning".equals(normalizedPeriod)
                && !"afternoon".equals(normalizedPeriod)
                && !"evening".equals(normalizedPeriod)) {
            throw new IllegalArgumentException(
                    "时段必须为 morning、afternoon 或 evening");
        }

        // 3. 检查活动偏好，不接受契约以外的值。
        if (activityPreference == null
                || activityPreference.trim().isEmpty()) {
            throw new IllegalArgumentException("活动偏好不能为空");
        }

        String normalizedPreference =
                activityPreference.trim().toUpperCase(Locale.ROOT);

        if (!"OUTDOOR".equals(normalizedPreference)
                && !"INDOOR".equals(normalizedPreference)
                && !"FOOD".equals(normalizedPreference)) {
            throw new IllegalArgumentException(
                    "活动偏好必须为 OUTDOOR、INDOOR 或 FOOD");
        }

        // 4. 检查计划自身的城市和日期是否完整。
        if (currentPlan.getCity() == null
                || currentPlan.getCity().trim().isEmpty()
                || currentPlan.getDate() == null
                || currentPlan.getDate().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "当前计划缺少城市或日期，无法修改");
        }

        // 5. 构造新请求，仍然使用原计划的城市和日期。
        // 局部修改不应意外改变用户已经确认的出行条件。
        final java.time.LocalDate planDate;
        try {
            planDate = java.time.LocalDate.parse(currentPlan.getDate());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "当前计划日期格式错误，无法修改", ex);
        }

        TravelRequest updatedRequest = new TravelRequest();
        updatedRequest.setCity(currentPlan.getCity());
        updatedRequest.setDate(planDate);
        updatedRequest.setActivityPreference(normalizedPreference);

        // 6. 重新计算新偏好下的活动建议。
        // generatePlan 内部会校验天气日期、城市和天气风险。
        TravelPlan regenerated = generatePlan(updatedRequest, weather);

        // 7. 复制旧计划，避免直接修改会话中保存的原对象。
        TravelPlan updatedPlan = copyPlan(currentPlan);

        // 8. 只替换指定时段的活动字段。
        // 其他时段和天气相关字段均保留旧值。
        switch (normalizedPeriod) {
            case "morning":
                updatedPlan.setMorningActivity(
                        regenerated.getMorningActivity());
                break;
            case "afternoon":
                updatedPlan.setAfternoonActivity(
                        regenerated.getAfternoonActivity());
                break;
            case "evening":
                updatedPlan.setEveningActivity(
                        regenerated.getEveningActivity());
                break;
            default:
                // 前面已经校验过，此处作为防御性检查。
                throw new IllegalArgumentException("不支持的修改时段");
        }

        return updatedPlan;
    }

    /**
     * 复制现有计划的所有字段。
     *
     * 用于保证局部修改返回新对象，并保留未修改的业务数据。
     */
    private TravelPlan copyPlan(TravelPlan source) {
        TravelPlan target = new TravelPlan();

        target.setCity(source.getCity());
        target.setDate(source.getDate());
        target.setWeatherSummary(source.getWeatherSummary());
        target.setMorningActivity(source.getMorningActivity());
        target.setAfternoonActivity(source.getAfternoonActivity());
        target.setEveningActivity(source.getEveningActivity());
        target.setClothingAdvice(source.getClothingAdvice());

        // 复制集合，避免新旧计划共用同一个可变集合。
        target.setItemsToPack(source.getItemsToPack() == null
                ? new ArrayList<>()
                : new ArrayList<>(source.getItemsToPack()));

        target.setWarnings(source.getWarnings() == null
                ? new ArrayList<>()
                : new ArrayList<>(source.getWarnings()));

        return target;
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
     * 根据用户偏好、天气风险和时段推荐活动。
     *
     * 先处理天气安全风险，再根据用户偏好和时段生成活动建议。
     *
     * @param preference 用户偏好：OUTDOOR、INDOOR、FOOD
     * @param analysis 天气风险分析结果
     * @param period 时段，例如“上午”“下午”“晚间”
     * @return 活动建议
     */
    private String recommendActivity(
            String preference,
            TravelRuleService.WeatherAnalysis analysis,
            String period) {

        String time = normalizePeriod(period);

        // 严重天气优先级最高，避免推荐户外活动。
        if (analysis.hasSevereWeather()) {
            return recommendIndoorActivity(
                    preference, time, "当前存在严重天气风险");
        }

        // 天气类型未知时，优先选择方便调整的室内活动。
        if (analysis.getCondition()
                == TravelRuleService.WeatherCondition.UNKNOWN) {
            return recommendIndoorActivity(
                    preference, time, "天气情况尚不能完整确认");
        }

        // 存在影响户外安全或舒适度的风险时，优先提供室内方案。
        if (requiresIndoorAlternative(analysis)) {
            return recommendIndoorActivity(
                    preference, time, "当前天气可能影响户外活动");
        }

        boolean temperatureRisk = hasTemperatureRisk(analysis);

        // 户外偏好：天气正常时推荐户外活动；温度异常时降低活动强度。
        if ("OUTDOOR".equals(preference)) {
            if (temperatureRisk) {
                return switch (time) {
                    case "MORNING" ->
                            "上午：安排短时公园漫步或轻松户外步行，"
                                    + "注意防暑或保暖，避免长时间活动。";
                    case "AFTERNOON" ->
                            "下午：安排短时、低强度的户外游览，"
                                    + "适当休息并根据气温调整活动时长。";
                    default ->
                            "晚上：安排适度的户外散步或夜间街区游览，"
                                    + "注意照明、气温变化和返程安全。";
                };
            }

            return switch (time) {
                case "MORNING" ->
                        "上午：安排户外公园漫步或自然景观游览。";
                case "AFTERNOON" ->
                        "下午：安排户外探索、城市步行或景观游览。";
                default ->
                        "晚上：安排户外散步或夜间街区游览，"
                                + "注意照明和返程安全。";
            };
        }

        // 室内偏好：按时段安排不同的室内活动。
        if ("INDOOR".equals(preference)) {
            return switch (time) {
                case "MORNING" ->
                        "上午：安排室内展览、博物馆参观或文化体验。";
                case "AFTERNOON" ->
                        "下午：安排室内文化体验、展览参观或休闲活动。";
                default ->
                        "晚上：安排室内休闲、文化体验或安静的阅读休息。";
            };
        }

        // 美食偏好：三个时段均明确体现美食体验。
        if ("FOOD".equals(preference)) {
            return switch (time) {
                case "MORNING" ->
                        "上午：体验当地早餐、传统早点或特色饮品，"
                                + "感受当地美食文化。";
                case "AFTERNOON" ->
                        "下午：品尝地方特色小吃、午餐或特色美食。";
                default ->
                        "晚上：体验当地晚餐或地方特色美食，"
                                + "出发前确认餐饮地点的营业时间和交通情况。";
            };
        }

        // 正常情况下，validateRequest 应已拦截不支持的偏好。
        // 保留防御性处理，避免未知偏好被误认为美食偏好。
        throw new IllegalArgumentException(
                "不支持的活动偏好：" + preference);
    }

    /**
     * 天气存在风险时，按时段提供室内替代活动。
     *
     * 不论用户原本偏好什么，都优先避免不必要的户外风险。
     */
    private String recommendIndoorActivity(
            String preference,
            String time,
            String reason) {

        String activity;

        if ("FOOD".equals(preference)) {
            activity = switch (time) {
                case "MORNING" ->
                        "选择室内早餐或特色饮品，体验当地美食";
                case "AFTERNOON" ->
                        "选择室内用餐或地方特色美食体验";
                default ->
                        "选择室内餐厅享用晚餐或体验地方美食";
            };
        } else {
            activity = switch (time) {
                case "MORNING" -> "安排室内展览、文化体验或休息";
                case "AFTERNOON" -> "安排室内文化体验、展览或休闲活动";
                default -> "安排室内休闲、文化体验或休息";
            };
        }

        return timeLabel(time) + "：" + activity + "；"
                + reason + "，建议避免户外游览，"
                + "并在出行前再次确认天气情况。";
    }

    /**
     * 将内部时段标识转换为中文。
     */
    private String timeLabel(String time) {
        return switch (time) {
            case "MORNING" -> "上午";
            case "AFTERNOON" -> "下午";
            default -> "晚上";
        };
    }

    /**
     * 统一识别中文或英文时段名称，避免调用方传入不同写法时匹配失败。
     */
    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "AFTERNOON";
        }

        String value = period.trim().toLowerCase();

        if (value.contains("morning")
                || value.contains("早")
                || value.contains("上午")) {
            return "MORNING";
        }

        if (value.contains("afternoon")
                || value.contains("下午")
                || value.contains("中午")) {
            return "AFTERNOON";
        }

        return "EVENING";
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
