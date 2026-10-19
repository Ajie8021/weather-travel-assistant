package com.example.wea_trav_assistant.exception;

public class WeatherException extends RuntimeException {
    private final WeatherErrorCode code;
    public WeatherException(WeatherErrorCode code, String message) {
        super(message); this.code = code;
    }
    public WeatherErrorCode getCode() { return code; }
}
