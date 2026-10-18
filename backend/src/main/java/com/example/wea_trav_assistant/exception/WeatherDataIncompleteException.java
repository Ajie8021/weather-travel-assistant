
package com.example.wea_trav_assistant.exception;

/**
 * 天气数据不满足规则判断或计划生成条件时抛出的业务异常。
 *
 * 例如：天气数据日期与请求日期不一致，
 * 或天气代码、最高温度、最低温度均不可用。
 */
public class WeatherDataIncompleteException extends RuntimeException {

    /** 与团队接口约定保持一致的错误码。 */
    private final String errorCode = "WEATHER_DATA_INCOMPLETE";

    public WeatherDataIncompleteException(String message) {
        super(message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
