package com.example.wea_trav_assistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.client.RestClientException;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(WeatherException.class)
    public String weatherError(WeatherException e, Model model) { model.addAttribute("errorMessage", e.getMessage()); return "weather"; }
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public String invalidInput(Model model) { model.addAttribute("errorMessage", "请求参数缺失或日期格式不正确"); return "weather"; }
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(RestClientException.class)
    public String apiError(Model model) { model.addAttribute("errorMessage", "天气服务暂不可用，请稍后重试"); return "weather"; }
}
