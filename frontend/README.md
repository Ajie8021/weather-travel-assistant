# 前端页面说明

当前版本按 openapi/travel-assistant.yaml 采用 Spring Boot + Thymeleaf 服务端渲染。页面模板放在 backend/src/main/resources/templates/，样式和交互脚本放在 backend/src/main/resources/static/；本版本不单独运行 Vue 开发服务器。

## 页面与路由

- index.html：首页表单，向 GET /weather 提交 city 和 date。
- weather.html：天气结果页；模型属性为 weather（WeatherData）和可选的 errorMessage。页面向 POST /plan/generate 提交 city、date、activityPreference。
- plan.html：单日计划结果页；模型属性为 plan（TravelPlan）和可选的 errorMessage。
- static/css/app.css：响应式页面样式。
- static/js/app.js：日期范围校验（今天至未来 15 天）和 WMO 天气代码的中文显示名称。

## 后端对接

Controller 返回相应模板名：index、weather、plan。首页 GET / 返回 index；天气查询 GET /weather 接收 city 和 date，并将 WeatherData 作为 weather 交给 weather 模板；计划生成 POST /plan/generate 接收 city、date、activityPreference，调用天气服务和计划生成服务，再将 TravelPlan 作为 plan 交给 plan 模板。发生业务错误时，页面使用 errorMessage 显示提示。

活动偏好表单值必须与契约一致：OUTDOOR、INDOOR、FOOD。天气字段缺失时页面显示“暂无”或信息提示，不用默认值伪装天气数据。
