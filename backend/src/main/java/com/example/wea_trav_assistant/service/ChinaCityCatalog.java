package com.example.wea_trav_assistant.service;

import com.example.wea_trav_assistant.dto.CityData;

import java.util.List;
import java.util.Optional;

/**
 * Canonical coordinates for commonly queried Chinese prefecture-level cities
 * and municipalities.  This prevents a global geocoder from treating a
 * same-named village as the city a user normally intends to query.
 */
final class ChinaCityCatalog {
    private static final String COUNTRY = "中国";
    private static final String TIMEZONE = "Asia/Shanghai";

    private static final List<CityData> CITIES = List.of(
            city("北京", "北京市", 39.9042, 116.4074), city("上海", "上海市", 31.2304, 121.4737),
            city("天津", "天津市", 39.3434, 117.3616), city("重庆", "重庆市", 29.5630, 106.5516),
            city("广州", "广东省", 23.1291, 113.2644), city("深圳", "广东省", 22.5431, 114.0579),
            city("东莞", "广东省", 23.0207, 113.7518), city("佛山", "广东省", 23.0215, 113.1214),
            city("珠海", "广东省", 22.2707, 113.5767), city("杭州", "浙江省", 30.2741, 120.1551),
            city("宁波", "浙江省", 29.8683, 121.5440), city("温州", "浙江省", 27.9949, 120.6994),
            city("苏州", "江苏省", 31.2989, 120.5853), city("南京", "江苏省", 32.0603, 118.7969),
            city("无锡", "江苏省", 31.4900, 120.3124), city("武汉", "湖北省", 30.5928, 114.3055),
            city("长沙", "湖南省", 28.2282, 112.9388), city("郑州", "河南省", 34.7472, 113.6249),
            city("济南", "山东省", 36.6512, 117.1201), city("青岛", "山东省", 36.0671, 120.3826),
            city("成都", "四川省", 30.5728, 104.0668), city("西安", "陕西省", 34.3416, 108.9398),
            city("昆明", "云南省", 25.0389, 102.7183), city("贵阳", "贵州省", 26.6470, 106.6302),
            city("福州", "福建省", 26.0745, 119.2965), city("厦门", "福建省", 24.4798, 118.0894),
            city("合肥", "安徽省", 31.8206, 117.2272), city("南昌", "江西省", 28.6820, 115.8579),
            city("石家庄", "河北省", 38.0428, 114.5149), city("太原", "山西省", 37.8706, 112.5489),
            city("沈阳", "辽宁省", 41.8057, 123.4315), city("大连", "辽宁省", 38.9140, 121.6147),
            city("长春", "吉林省", 43.8171, 125.3235), city("哈尔滨", "黑龙江省", 45.8038, 126.5349),
            city("海口", "海南省", 20.0444, 110.1999), city("三亚", "海南省", 18.2528, 109.5119),
            city("南宁", "广西壮族自治区", 22.8170, 108.3669), city("拉萨", "西藏自治区", 29.6520, 91.1721),
            city("兰州", "甘肃省", 36.0611, 103.8343), city("西宁", "青海省", 36.6171, 101.7782),
            city("银川", "宁夏回族自治区", 38.4872, 106.2309), city("乌鲁木齐", "新疆维吾尔自治区", 43.8256, 87.6168),
            city("呼和浩特", "内蒙古自治区", 40.8426, 111.7492)
    );

    private ChinaCityCatalog() { }

    static Optional<CityData> find(String input) {
        String normalizedInput = normalize(input);
        return CITIES.stream().filter(city -> matches(normalizedInput, city)).findFirst();
    }

    private static boolean matches(String input, CityData city) {
        String name = normalize(city.name());
        String admin = normalize(city.adminArea());
        return input.equals(name)
                || input.equals(admin + name)
                || input.equals(normalize(city.country()) + admin + name);
    }

    private static CityData city(String name, String adminArea, double latitude, double longitude) {
        return new CityData(name, adminArea, COUNTRY, latitude, longitude, TIMEZONE);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\s,，、()（）-]", "")
                .replace("特别行政区", "").replace("自治区", "")
                .replace("省", "").replace("市", "");
    }
}
