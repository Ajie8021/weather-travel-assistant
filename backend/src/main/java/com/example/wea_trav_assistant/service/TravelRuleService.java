
package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherDataIncompleteException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 天气规则判断服务。
 *
 * 职责：
 * 1. 将 WMO 天气代码映射为天气类型。
 * 2. 识别天气代码、温度、降雨概率和风速对应的风险。
 * 3. 处理多风险并存、天气数据缺失和日期不匹配。
 *
 * 本类不请求外部天气 API，也不负责组装最终 TravelPlan。
 */
@Service
public class TravelRuleService {

    /** 高温阈值：最高温度达到 35°C。 */
    private static final double HIGH_TEMP_THRESHOLD = 35.0;

    /** 低温阈值：最低温度低于或等于 5°C。 */
    private static final double LOW_TEMP_THRESHOLD = 5.0;

    /** 大风阈值：风速达到 30 km/h。 */
    private static final double HIGH_WIND_THRESHOLD = 30.0;

    /** 降雨概率阈值：达到 50%。 */
    private static final int PRECIPITATION_PROBABILITY_THRESHOLD = 50;

    /**
     * 统一天气类型。
     * UNKNOWN 表示代码缺失或当前规则未支持该代码，
     * 绝不能默认映射为 CLEAR。
     */
    public enum WeatherCondition {
        CLEAR,
        CLOUDY,
        FOG,
        DRIZZLE,
        RAIN,
        FREEZING_DRIZZLE,
        FREEZING_RAIN,
        SNOW,
        RAIN_SHOWERS,
        SNOW_SHOWERS,
        THUNDERSTORM,
        UNKNOWN
    }

    /**
     * 规则识别出的风险类型。
     * 使用枚举集合，避免同一种风险重复添加。
     */
    public enum WeatherRisk {
        SEVERE_WEATHER,
        RAIN,
        SNOW,
        FOG,
        HIGH_TEMPERATURE,
        LOW_TEMPERATURE,
        HIGH_WIND,
        HIGH_PRECIPITATION_PROBABILITY,
        UNKNOWN_WEATHER,
        INCOMPLETE_DATA
    }

    /**
     * 天气规则分析结果。
     * 结果只保存判断结论，不修改传入的 WeatherData。
     */
    public static class WeatherAnalysis {

        private final WeatherCondition condition;
        private final Set<WeatherRisk> risks;
        private final List<String> warnings;
        private final List<String> informationNotes;
        private final boolean severeWeather;
        private final boolean outdoorActivityNeedsAdjustment;

        private WeatherAnalysis(
                WeatherCondition condition,
                Set<WeatherRisk> risks,
                List<String> warnings,
                List<String> informationNotes,
                boolean severeWeather,
                boolean outdoorActivityNeedsAdjustment) {

            this.condition = condition;

            // EnumSet.copyOf(emptyCollection) 会抛出异常，因此先创建空集合再添加风险项。
            EnumSet<WeatherRisk> riskCopy = EnumSet.noneOf(WeatherRisk.class);
            riskCopy.addAll(risks);
            // 对外提供不可修改的集合，避免调用方意外改变分析结果。
            this.risks = Collections.unmodifiableSet(riskCopy);

            this.warnings = Collections.unmodifiableList(
                    new ArrayList<>(warnings));

            this.informationNotes = Collections.unmodifiableList(
                    new ArrayList<>(informationNotes));

            this.severeWeather = severeWeather;
            this.outdoorActivityNeedsAdjustment =
                    outdoorActivityNeedsAdjustment;
        }

        public WeatherCondition getCondition() {
            return condition;
        }

        public Set<WeatherRisk> getRisks() {
            return risks;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getInformationNotes() {
            return informationNotes;
        }

        public boolean hasSevereWeather() {
            return severeWeather;
        }

        public boolean needsOutdoorActivityAdjustment() {
            return outdoorActivityNeedsAdjustment;
        }
    }

    /**
     * 分析指定出行日期的天气。
     *
     * @param requestedDate 用户请求的出行日期
     * @param weather       天气服务提供的标准化天气数据
     * @return 天气类型、风险列表及提示信息
     * @throws WeatherDataIncompleteException 日期不匹配或关键天气数据严重不足时抛出
     */
    public WeatherAnalysis analyze(
            LocalDate requestedDate,
            WeatherData weather) {

        // 防止空请求或空天气对象进入后续规则判断。
        if (requestedDate == null) {
            throw new IllegalArgumentException("请求日期不能为空");
        }

        if (weather == null) {
            throw new WeatherDataIncompleteException("天气数据为空，无法生成天气判断");
        }

        // 天气数据必须与用户选择的出行日期一致。
        // 不允许使用其他日期的天气生成计划。
        if (weather.getDate() == null
                || !requestedDate.equals(weather.getDate())) {
            throw new WeatherDataIncompleteException(
                    "天气数据日期与所选出行日期不一致，请重新查询");
        }

        WeatherCondition condition =
                mapWeatherCode(weather.getWeatherCode());

        /*
         * 业务规则约定：
         * 天气代码、最高温度、最低温度全部不可用时，
         * 认为关键天气数据严重不足。
         *
         * 只要其中至少一项有效，就允许继续分析，
         * 但需要在结果中标明信息不足。
         */
        boolean hasUsableWeatherCode =
                condition != WeatherCondition.UNKNOWN;

        boolean hasMaxTemp = weather.getMaxTemp() != null;
        boolean hasMinTemp = weather.getMinTemp() != null;

        if (!hasUsableWeatherCode && !hasMaxTemp && !hasMinTemp) {
            throw new WeatherDataIncompleteException(
                    "天气代码、最高温度和最低温度均不可用");
        }

        Set<WeatherRisk> risks = EnumSet.noneOf(WeatherRisk.class);
        List<String> warnings = new ArrayList<>();
        List<String> informationNotes = new ArrayList<>();

        // 一、根据天气代码识别天气类型和风险。
        applyWeatherCodeRules(condition, risks, warnings);

        // 二、根据温度阈值识别高温、低温风险。
        if (hasMaxTemp
                && weather.getMaxTemp() >= HIGH_TEMP_THRESHOLD) {
            risks.add(WeatherRisk.HIGH_TEMPERATURE);
            warnings.add("最高温度较高，建议补水、遮阳并避免长时间处于高温环境。");
        }

        if (hasMinTemp
                && weather.getMinTemp() <= LOW_TEMP_THRESHOLD) {
            risks.add(WeatherRisk.LOW_TEMPERATURE);
            warnings.add("最低温度较低，建议准备保暖衣物。");
        }

        // 三、根据风速识别大风风险。
        if (weather.getWindSpeed() != null
                && weather.getWindSpeed() >= HIGH_WIND_THRESHOLD) {
            risks.add(WeatherRisk.HIGH_WIND);
            warnings.add("风力较大，建议谨慎安排对风力敏感的户外活动。");
        }

        // 四、根据降雨概率识别降雨风险。
        // null 不等于 0%，缺失时只说明无法判断。
        if (weather.getPrecipitationProbability() != null) {
            if (weather.getPrecipitationProbability()
                    >= PRECIPITATION_PROBABILITY_THRESHOLD) {
                risks.add(WeatherRisk.HIGH_PRECIPITATION_PROBABILITY);
                warnings.add("降雨概率较高，建议携带雨具并考虑室内备选活动。");
            }
        } else {
            informationNotes.add("缺少降雨概率数据，无法根据该字段判断降雨风险。");
        }

        // 五、记录缺失字段，供后续计划生成时明确说明信息限制。
        if (!hasUsableWeatherCode) {
            risks.add(WeatherRisk.UNKNOWN_WEATHER);
            informationNotes.add("天气代码缺失或未知，无法可靠判断具体天气类型。");
        }

        if (!hasMaxTemp) {
            informationNotes.add("缺少最高温度数据，无法判断是否达到高温阈值。");
        }

        if (!hasMinTemp) {
            informationNotes.add("缺少最低温度数据，无法判断是否达到低温阈值。");
        }

        if (weather.getWindSpeed() == null) {
            informationNotes.add("缺少风速数据，无法根据风速阈值判断大风风险。");
        }

        if (weather.getPrecipitation() == null) {
            informationNotes.add("缺少降水量数据，无法根据降水量进行额外判断。");
        }

        if (!informationNotes.isEmpty()) {
            risks.add(WeatherRisk.INCOMPLETE_DATA);
        }

        /*
         * 严重风险优先：
         * 雷暴、冻雨等严重天气一旦出现，就要求调整户外活动。
         *
         * 普通风险仍保留在 risks 和 warnings 中，
         * 不会因为严重风险出现而被覆盖。
         */
        boolean severeWeather = risks.contains(WeatherRisk.SEVERE_WEATHER);

        boolean outdoorActivityNeedsAdjustment =
                severeWeather
                        || risks.contains(WeatherRisk.RAIN)
                        || risks.contains(WeatherRisk.SNOW)
                        || risks.contains(WeatherRisk.FOG)
                        || risks.contains(WeatherRisk.HIGH_TEMPERATURE)
                        || risks.contains(WeatherRisk.LOW_TEMPERATURE)
                        || risks.contains(WeatherRisk.HIGH_WIND)
                        || risks.contains(
                        WeatherRisk.HIGH_PRECIPITATION_PROBABILITY);

        return new WeatherAnalysis(
                condition,
                risks,
                warnings,
                informationNotes,
                severeWeather,
                outdoorActivityNeedsAdjustment);
    }

    /**
     * 将 WMO 天气代码映射为内部天气类型。
     * 未知代码统一返回 UNKNOWN，不推断为晴天。
     */
    public WeatherCondition mapWeatherCode(Integer code) {
        if (code == null) {
            return WeatherCondition.UNKNOWN;
        }

        switch (code) {
            case 0:
                return WeatherCondition.CLEAR;

            case 1:
            case 2:
            case 3:
                return WeatherCondition.CLOUDY;

            case 45:
            case 48:
                return WeatherCondition.FOG;

            case 51:
            case 53:
            case 55:
                return WeatherCondition.DRIZZLE;

            case 56:
            case 57:
                return WeatherCondition.FREEZING_DRIZZLE;

            case 61:
            case 63:
            case 65:
                return WeatherCondition.RAIN;

            case 66:
            case 67:
                return WeatherCondition.FREEZING_RAIN;

            case 71:
            case 73:
            case 75:
            case 77:
                return WeatherCondition.SNOW;

            case 80:
            case 81:
            case 82:
                return WeatherCondition.RAIN_SHOWERS;

            case 85:
            case 86:
                return WeatherCondition.SNOW_SHOWERS;

            case 95:
            case 96:
            case 99:
                return WeatherCondition.THUNDERSTORM;

            default:
                return WeatherCondition.UNKNOWN;
        }
    }

    /**
     * 根据天气类型添加对应风险。
     *
     * 严重风险和普通风险分别记录：
     * 即使雷暴与降雨同时出现，也会保留两类风险。
     */
    private void applyWeatherCodeRules(
            WeatherCondition condition,
            Set<WeatherRisk> risks,
            List<String> warnings) {

        switch (condition) {
            case RAIN:
            case DRIZZLE:
            case RAIN_SHOWERS:
                risks.add(WeatherRisk.RAIN);
                warnings.add("存在降雨或阵雨天气，请关注降水情况并准备雨具。");
                break;

            case SNOW:
            case SNOW_SHOWERS:
                risks.add(WeatherRisk.SNOW);
                warnings.add("存在降雪或阵雪天气，建议注意保暖并谨慎安排户外活动。");
                break;

            case FOG:
                risks.add(WeatherRisk.FOG);
                warnings.add("存在雾天气况，出行时请注意能见度。");
                break;

            case FREEZING_DRIZZLE:
            case FREEZING_RAIN:
                risks.add(WeatherRisk.SEVERE_WEATHER);
                risks.add(WeatherRisk.RAIN);
                warnings.add("存在冻雨或冻毛毛雨风险，建议避免受影响的户外活动。");
                break;

            case THUNDERSTORM:
                risks.add(WeatherRisk.SEVERE_WEATHER);
                warnings.add("存在雷暴天气，建议取消或调整户外活动，优先选择安全的室内场所。");
                break;

            case CLEAR:
            case CLOUDY:
                // 这些天气类型本身不直接触发风险。
                break;

            case UNKNOWN:
            default:
                risks.add(WeatherRisk.UNKNOWN_WEATHER);
                break;
        }
    }
}
