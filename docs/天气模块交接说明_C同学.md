# 天气模块交接说明（给 C 同学）

## 1. 本次交付概览

当前功能分支为 `feat/weather-module`，对应 Pull Request 为 `#7`。

本次主要完成了天气查询后端模块：用户提交城市和日期后，系统先通过 Open-Meteo 地理编码接口确定城市坐标，再查询指定日期的天气预报，最后将结果交给 `weather.html` 页面展示。

调用链如下：

```text
GET /weather?city=城市&date=yyyy-MM-dd
        ↓
WeatherController
        ↓
WeatherService（输入校验、城市筛选、天气数据转换）
        ↓
OpenMeteoClient
        ├─ Geocoding API：城市 → 经纬度、时区
        └─ Forecast API：经纬度、日期 → 天气数据
        ↓
WeatherData
        ↓
weather.html
```

## 2. 我负责并上传的文件

### 天气业务代码

| 文件 | 作用 |
|---|---|
| `backend/src/main/java/com/example/wea_trav_assistant/controller/WeatherController.java` | 接收 `/weather` 请求，调用天气服务并向页面传递 `weather` 模型数据 |
| `backend/src/main/java/com/example/wea_trav_assistant/service/WeatherService.java` | 校验城市和日期、处理城市匹配、映射天气返回值 |
| `backend/src/main/java/com/example/wea_trav_assistant/client/OpenMeteoClient.java` | 调用 Open-Meteo 地理编码与天气预报接口，配置连接/读取超时 |
| `backend/src/main/java/com/example/wea_trav_assistant/dto/CityData.java` | 保存城市名、行政区、国家、经纬度和时区 |
| `backend/src/main/java/com/example/wea_trav_assistant/dto/WeatherData.java` | 保存页面需要的天气查询结果 |

### 异常处理

| 文件 | 作用 |
|---|---|
| `backend/src/main/java/com/example/wea_trav_assistant/exception/WeatherErrorCode.java` | 统一定义天气模块错误类型 |
| `backend/src/main/java/com/example/wea_trav_assistant/exception/WeatherException.java` | 天气业务异常 |
| `backend/src/main/java/com/example/wea_trav_assistant/exception/GlobalExceptionHandler.java` | 统一处理输入错误、天气业务错误和外部接口异常，返回天气页面并显示错误信息 |

### 测试及工程配置

| 文件 | 作用 |
|---|---|
| `backend/src/test/java/com/example/wea_trav_assistant/service/WeatherServiceTest.java` | 测试空城市、过去日期、未知城市和天气数据映射 |
| `backend/pom.xml` | 补充Swagger注解、JsonNullable和OpenAPI Generator相关依赖/插件 |
| `openapi/travel-assistant.yaml` | 修正活动类型枚举说明格式 |
| `.gitignore` | 忽略本机IntelliJ IDEA配置目录 `.idea/` |

说明：`TravelPlanController`、`TravelPlanService`、`TravelRuleService`、`TravelPlan`、`TravelRequest` 五个空占位文件已恢复，目前仍保留在仓库中，不属于本次天气模块实现。

## 3. 接口使用方式

### 请求

```http
GET /weather?city=Beijing&date=2026-10-20
```

参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `city` | String | 城市名称，不能为空；出现同名城市时需要补充地区信息 |
| `date` | `yyyy-MM-dd` | 查询日期，只接受今天至未来15天以内的日期 |

### 页面模型

成功时，Controller向 `weather.html` 注入：

```text
weather.city
weather.adminArea
weather.country
weather.latitude
weather.longitude
weather.date
weather.weatherCode
weather.maxTemp
weather.minTemp
weather.precipitationProbability
weather.precipitation
weather.windSpeed
weather.source
```

其中 `source` 当前固定为 `Open-Meteo`。部分天气字段在上游缺失时可能为 `null`，页面渲染时需要做好空值显示。

失败时，异常处理器向 `weather.html` 注入：

```text
errorMessage
```

前端页面应优先判断并展示该字段，避免只渲染成功结果。

## 4. 当前校验与错误类型

已实现以下处理：

- 城市为空或日期为空：`INVALID_INPUT`；
- 日期早于今天或超过未来15天：`OUT_OF_FORECAST_RANGE`；
- 找不到城市：`CITY_NOT_FOUND`；
- 同名城市无法唯一确定：`CITY_AMBIGUOUS`；
- 天气接口未返回有效日数据：`WEATHER_DATA_INCOMPLETE`；
- 外部天气服务调用异常：页面提示“天气服务暂不可用，请稍后重试”；
- 日期格式错误或请求参数缺失：HTTP 400，并返回天气页面显示错误信息。

## 5. 外部服务

模块使用无需API Key的 Open-Meteo 服务：

```text
城市检索：https://geocoding-api.open-meteo.com/v1/search
天气预报：https://api.open-meteo.com/v1/forecast
```

HTTP配置：

```text
连接超时：5秒
读取超时：10秒
```

因此运行环境必须能够访问上述域名。

## 6. 本地运行和测试

进入后端目录：

```powershell
cd backend
```

运行测试：

```powershell
.\mvnw.cmd test
```

启动项目：

```powershell
.\mvnw.cmd spring-boot:run
```

启动后可访问示例：

```text
http://localhost:8080/weather?city=Beijing&date=2026-10-20
```

测试日期应根据运行当天调整，必须位于当天至未来15天以内。

## 7. C同学对接时需要确认

1. `weather.html` 是否同时正确展示 `weather` 和 `errorMessage`；
2. 首页表单字段名必须是 `city` 和 `date`，日期格式为 `yyyy-MM-dd`；
3. 页面需要处理温度、降雨、风速等字段为 `null` 的情况；
4. 如果行程规划模块需要天气数据，建议直接注入并调用 `WeatherService`，不要再次从后端内部请求 `/weather` 页面接口；
5. 若需要提供给前端JavaScript或其他服务使用，应新增独立的JSON REST接口，不要直接改变现有页面Controller的返回行为；
6. 合并前执行后端测试，并确认PR没有冲突。

## 8. 当前注意事项与后续建议

- 当前城市匹配采用“名称完全匹配且结果唯一”的规则。中文名、英文名或同名城市可能需要用户补充行政区/国家；后续可增加候选城市选择功能。
- `WeatherException` 当前返回错误页面，但没有针对每种业务错误分别设置HTTP状态码；如果后续改为REST接口，应增加统一错误响应结构和明确状态码。
- 当前单元测试覆盖Service主要路径，但尚未加入Controller层MockMvc测试和Open-Meteo Client集成测试。
- 当前确定日期范围为未来15天；如Open-Meteo实际可用预报范围或项目需求改变，应同步修改校验规则。
- `API_TIMEOUT` 已在错误枚举中定义，但目前外部调用异常统一由 `RestClientException` 处理，尚未细分超时错误。

## 9. 合并状态

本地分支已经与远程 `origin/feat/weather-module` 同步，工作区干净。PR #7在合并前还需在GitHub页面确认自动检查、审查意见和冲突状态。
