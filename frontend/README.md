# 前端页面说明

当前版本按 openapi/travel-assistant.yaml 采用 Spring Boot + Thymeleaf 服务端渲染。页面模板放在 backend/src/main/resources/templates/，样式和交互脚本放在 backend/src/main/resources/static/；本版本不单独运行 Vue 开发服务器。

## 页面与路由

- index.html：首页表单，向 GET /weather 提交 city 和 date。
- weather.html：天气结果页；模型属性为 weather（WeatherData）和可选的 errorMessage。页面向 POST /plan/generate 提交 city、date、activityPreference。
- plan.html：单日计划结果页；模型属性为 plan（TravelPlan）和可选的 errorMessage。
- static/css/app.css：响应式页面样式。
- static/js/app.js：日期最小值和 WMO 天气代码的中文显示名称。

活动偏好表单值必须与契约一致：OUTDOOR、INDOOR、FOOD。

## 后端对接

Controller 需要返回相应模板名：index、weather、plan。天气查询成功时向 weather 模板添加 weather；计划生成成功时向 plan 模板添加 plan。发生业务错误时添加 errorMessage，并返回对应页面。首页需要一个 GET / 页面路由。

当前 Controller 和 DTO 文件尚无实现，因此页面数据绑定需由后端同学按上面的属性名接通。
