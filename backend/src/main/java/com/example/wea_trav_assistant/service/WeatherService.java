package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.client.OpenMeteoClient;
import com.example.wea_trav_assistant.dto.CityData;
import com.example.wea_trav_assistant.dto.WeatherData;
import com.example.wea_trav_assistant.exception.WeatherErrorCode;
import com.example.wea_trav_assistant.exception.WeatherException;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class WeatherService {
    private final OpenMeteoClient client;
    public WeatherService(OpenMeteoClient client) { this.client = client; }

    public WeatherData getWeather(String city, LocalDate date) {
        validateDate(date);
        List<CityData> cities = searchCityCandidates(city);
        if (cities.isEmpty()) throw new WeatherException(WeatherErrorCode.CITY_NOT_FOUND, "未找到匹配的城市，请补充地区后重试");
        if (cities.size() != 1) throw new WeatherException(WeatherErrorCode.CITY_AMBIGUOUS, "找到多个可能的城市，请补充省份或国家后重试");
        return getWeather(cities.getFirst(), date);
    }

    public List<CityData> searchCityCandidates(String city) {
        if (city == null || city.isBlank()) {
            throw new WeatherException(WeatherErrorCode.INVALID_INPUT, "请输入城市名称");
        }
        String query = city.trim();
        var knownChineseCity = ChinaCityCatalog.find(query);
        if (knownChineseCity.isPresent()) return List.of(knownChineseCity.get());
        List<CityData> candidates = client.searchCities(query);

        // A query for one of China's four municipalities normally means the
        // municipality itself, not a same-named village in another province.
        // This remains deliberately narrow; ordinary same-name cities still
        // require the user to choose a candidate.
        List<CityData> municipalityMatches = candidates.stream()
                .filter(candidate -> isExactChineseMunicipality(query, candidate))
                .toList();
        if (municipalityMatches.size() == 1) return municipalityMatches;

        // Open-Meteo may return several same-name cities.  When the user has
        // already supplied a province/state or country, retain candidates
        // whose returned administrative information occurs in that input.
        // If no such metadata was supplied, keep every candidate so that the
        // Agent can ask the user to choose instead of guessing.
        List<CityData> administrativeMatches = candidates.stream()
                .filter(candidate -> hasAdditionalLocationInfo(query, candidate)
                        && containsLocationPart(query, candidate.adminArea()))
                .toList();
        if (!administrativeMatches.isEmpty()) return administrativeMatches;

        List<CityData> countryMatches = candidates.stream()
                .filter(candidate -> hasAdditionalLocationInfo(query, candidate)
                        && containsLocationPart(query, candidate.country()))
                .toList();
        return countryMatches.isEmpty() ? candidates : countryMatches;
    }

    private boolean hasAdditionalLocationInfo(String query, CityData candidate) {
        String normalizedQuery = normalizeLocation(query);
        String normalizedName = normalizeLocation(candidate.name());
        return !normalizedName.isBlank() && !normalizedQuery.replace(normalizedName, "").isBlank();
    }

    private boolean isExactChineseMunicipality(String query, CityData candidate) {
        String normalizedQuery = normalizeLocation(query);
        if (!List.of("北京", "上海", "天津", "重庆").contains(normalizedQuery)) return false;
        return normalizedQuery.equals(normalizeLocation(candidate.name()))
                && normalizedQuery.equals(normalizeLocation(candidate.adminArea()));
    }

    private boolean containsLocationPart(String query, String locationPart) {
        if (locationPart == null || locationPart.isBlank()) return false;
        return normalizeLocation(query).contains(normalizeLocation(locationPart));
    }

    private String normalizeLocation(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，、()（）-]", "")
                .replace("省", "").replace("市", "")
                .replace("自治区", "").replace("特别行政区", "");
    }

    public WeatherData getWeather(CityData confirmedCity, LocalDate date) {
        if (confirmedCity == null) {
            throw new WeatherException(WeatherErrorCode.INVALID_INPUT, "请选择已确认的城市");
        }
        validateDate(date);
        JsonNode daily = client.getForecast(confirmedCity, date).path("daily");
        JsonNode returnedDate = first(daily, "time");
        if (returnedDate == null || !date.toString().equals(returnedDate.asText())) throw new WeatherException(WeatherErrorCode.WEATHER_DATA_INCOMPLETE, "天气服务未返回所选日期的数据");

        // 创建天气数据对象
        WeatherData weather = new WeatherData();

        // 设置已确认的城市信息
        weather.setCity(confirmedCity.name());
        weather.setAdminArea(confirmedCity.adminArea());
        weather.setCountry(confirmedCity.country());
        weather.setLatitude(confirmedCity.latitude());
        weather.setLongitude(confirmedCity.longitude());

        // 设置查询日期和天气数据
        weather.setDate(date);
        weather.setWeatherCode(optionalInt(daily, "weather_code"));
        weather.setMaxTemp(optionalDouble(daily, "temperature_2m_max"));
        weather.setMinTemp(optionalDouble(daily, "temperature_2m_min"));
        weather.setPrecipitationProbability(
                optionalInt(daily, "precipitation_probability_max"));
        weather.setPrecipitation(
                optionalDouble(daily, "precipitation_sum"));
        weather.setWindSpeed(
                optionalDouble(daily, "wind_speed_10m_max"));

        // 设置天气数据来源
        weather.setSource("Open-Meteo");

        return weather;
    }

    private void validateDate(LocalDate date) {
        if (date == null) throw new WeatherException(WeatherErrorCode.INVALID_INPUT, "请输入城市和有效日期");
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(15))) {
            throw new WeatherException(WeatherErrorCode.OUT_OF_FORECAST_RANGE, "日期不在当前可查询的天气预报范围内");
        }
    }
    private JsonNode first(JsonNode daily, String field) { JsonNode a = daily.path(field); return a.isArray() && !a.isEmpty() ? a.get(0) : null; }
    private Double optionalDouble(JsonNode d, String f) { JsonNode v = first(d, f); return v != null && v.isNumber() && Double.isFinite(v.doubleValue()) ? v.doubleValue() : null; }
    private Integer optionalInt(JsonNode d, String f) { JsonNode v = first(d, f); return v != null && v.isIntegralNumber() ? v.intValue() : null; }
}
