
package com.example.wea_trav_assistant.dto;

import java.time.LocalDate;

/**
 * 标准化后的天气数据。
 *
 * 由 WeatherService 负责从天气 API 获取数据并转换为该对象，
 * TravelRuleService 只根据这些字段执行业务规则判断。
 *
 * 可选天气字段允许为 null，表示数据缺失，不能用 0 或晴天代替。
 */
public class WeatherData {

    /** 天气数据对应的目的地当地日期。 */
    private LocalDate date;

    /** WMO 天气代码，缺失时为 null。 */
    private Integer weatherCode;

    /** 当日最高温度，单位：摄氏度。 */
    private Double maxTemp;

    /** 当日最低温度，单位：摄氏度。 */
    private Double minTemp;

    /** 降水概率，单位：%，范围 0～100。 */
    private Integer precipitationProbability;

    /** 当日降水量，单位：毫米。 */
    private Double precipitation;

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAdminArea() {
        return adminArea;
    }

    public void setAdminArea(String adminArea) {
        this.adminArea = adminArea;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /** 风速，单位：km/h。 */
    private Double windSpeed;

    // 地点信息：供天气服务和页面展示使用。
    private String city;
    private String adminArea;
    private String country;

    // 地理坐标：由地理编码服务提供。
    private Double latitude;
    private Double longitude;

    // 天气数据来源，例如 Open-Meteo。
    private String source;

    public WeatherData() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getWeatherCode() {
        return weatherCode;
    }

    public void setWeatherCode(Integer weatherCode) {
        this.weatherCode = weatherCode;
    }

    public Double getMaxTemp() {
        return maxTemp;
    }

    public void setMaxTemp(Double maxTemp) {
        this.maxTemp = maxTemp;
    }

    public Double getMinTemp() {
        return minTemp;
    }

    public void setMinTemp(Double minTemp) {
        this.minTemp = minTemp;
    }

    public Integer getPrecipitationProbability() {
        return precipitationProbability;
    }

    public void setPrecipitationProbability(Integer precipitationProbability) {
        this.precipitationProbability = precipitationProbability;
    }

    public Double getPrecipitation() {
        return precipitation;
    }

    public void setPrecipitation(Double precipitation) {
        this.precipitation = precipitation;
    }

    public Double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(Double windSpeed) {
        this.windSpeed = windSpeed;
    }
}
