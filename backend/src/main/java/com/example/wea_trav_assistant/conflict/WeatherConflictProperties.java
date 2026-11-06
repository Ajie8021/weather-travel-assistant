package com.example.wea_trav_assistant.conflict;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 天气冲突阈值配置。
 *
 * 重要：这些数值必须与 B 模块 TravelRuleService 的阈值保持一致。
 * 本类作为“集成层唯一配置入口”，B 模块若已用常量，需在文档 C-07 中登记同步关系。
 * 可通过 application.properties 覆盖，例如：
 *   weather.conflict.high-temp=35
 */
@Component
@ConfigurationProperties(prefix = "weather.conflict")
public class WeatherConflictProperties {

    /** 高温阈值，最高温 >= 该值触发 HEAT */
    private double highTemp = 35.0;
    /** 低温阈值，最低温 <= 该值触发 COLD */
    private double lowTemp = 5.0;
    /** 大风阈值，风速 >= 该值触发 WIND，单位 km/h */
    private double highWindSpeed = 30.0;
    /** 降水概率阈值，>= 该值触发 RAIN，单位 % */
    private int rainProbability = 50;
    /** 降水量阈值，> 该值触发 RAIN，单位 mm */
    private double rainAmount = 0.0;
    /** 是否启用开放时间冲突检测 */
    private boolean openingHoursCheckEnabled = true;

    public double getHighTemp() { return highTemp; }
    public void setHighTemp(double highTemp) { this.highTemp = highTemp; }
    public double getLowTemp() { return lowTemp; }
    public void setLowTemp(double lowTemp) { this.lowTemp = lowTemp; }
    public double getHighWindSpeed() { return highWindSpeed; }
    public void setHighWindSpeed(double highWindSpeed) { this.highWindSpeed = highWindSpeed; }
    public int getRainProbability() { return rainProbability; }
    public void setRainProbability(int rainProbability) { this.rainProbability = rainProbability; }
    public double getRainAmount() { return rainAmount; }
    public void setRainAmount(double rainAmount) { this.rainAmount = rainAmount; }
    public boolean isOpeningHoursCheckEnabled() { return openingHoursCheckEnabled; }
    public void setOpeningHoursCheckEnabled(boolean v) { this.openingHoursCheckEnabled = v; }
}