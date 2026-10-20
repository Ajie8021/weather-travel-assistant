
package com.example.wea_trav_assistant.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。
 *
 * 统一处理天气查询和出行计划生成过程中的异常。
 * 根据请求路径返回对应的 Thymeleaf 页面，避免计划生成失败时
 * 错误地跳转到 weather.html。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理天气模块抛出的业务异常。
     *
     * 城市不存在、城市重名、日期超出预报范围等属于输入或业务校验错误，
     * 返回 400；天气数据缺失等无法完成请求的情况返回 503。
     */
    @ExceptionHandler(WeatherException.class)
    public String weatherError(
            WeatherException exception,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        // 业务异常默认按请求或业务规则错误处理
        response.setStatus(HttpStatus.BAD_REQUEST.value());

        model.addAttribute("errorMessage", exception.getMessage());

        // 如果来自计划表单，恢复用户之前填写的内容。
        restorePlanForm(request, model);

        return resolvePage(request);
    }

    /**
     * 处理缺少请求参数或日期格式转换失败。
     *
     * 例如：GET /weather 缺少 city，
     * 或者 /weather?date=not-a-date。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public String invalidInput(
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute(
                "errorMessage",
                "请求参数缺失或格式不正确，请检查输入");

        restorePlanForm(request, model);
        return resolvePage(request);
    }

    /**
     * 处理天气 API 调用异常。
     *
     * 不展示伪造的天气或出行计划，返回服务不可用提示。
     */
    @ExceptionHandler(RestClientException.class)
    public String apiError(
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute(
                "errorMessage",
                "天气服务暂不可用，请稍后重试");

        restorePlanForm(request, model);
        return resolvePage(request);
    }

    /**
     * 根据请求地址决定使用哪个页面。
     *
     * 计划生成请求返回 plan.html；
     * 其他天气查询请求返回 weather.html。
     */
    private String resolvePage(HttpServletRequest request) {
        if (request.getRequestURI().equals("/plan/generate")) {
            return "plan";
        }
        return "weather";
    }

    /**
     * 恢复计划表单输入，便于用户修改后重新提交。
     *
     * 这里使用表单原始字段名，与 OpenAPI 契约保持一致。
     */
    private void restorePlanForm(
            HttpServletRequest request,
            Model model) {

        if (!request.getRequestURI().equals("/plan/generate")) {
            return;
        }

        model.addAttribute(
                "city", request.getParameter("city"));
        model.addAttribute(
                "date", request.getParameter("date"));
        model.addAttribute(
                "activityPreference",
                request.getParameter("activityPreference"));
    }
}
