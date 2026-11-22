# 慢游指南接口设计（成员一）
版本：1.0；依据当前本地控制器和 DTO 核对。本文描述契约，不代表所有入口已经完成线上验收。

## 1. 缩写与公共约定
API：应用程序接口；HTTP：超文本传输协议；JSON：JavaScript对象表示法；DTO：数据传输对象；POI：兴趣点；MCP：模型上下文协议；ASR：语音识别；TTS：语音合成；WAV：波形音频格式。
日期采用 YYYY-MM-DD；经纬度使用十进制度；路线距离为米、时长为秒。JSON 请求使用 UTF-8。天气缺失值保留 null。
景点、路线、完整行程使用 {code,message,data,timestamp}；聊天直接返回 ChatResponse；健康检查直接返回状态对象。不要把这些响应混为同一格式。

## 2. 接口清单
|接口|输入|成功结果|失败/边界|
|---|---|---|---|
|GET /weather|city、date|HTML天气页面及WeatherData|城市歧义、无结果、日期超范围、天气网络异常|
|POST /api/chat|sessionId可选；message必填，最长2000字符|sessionId、reply、status、toolCalls、weather、travelPlan|输入错误HTTP 400；模型/工具失败检查业务status与reply|
|GET /api/attractions|city、keyword必填；limit默认5|data.city、data.attractions|400参数错误；404无地点；504超时；503密钥/配额/上游错误|
|GET /api/attractions/{poiId}|真实poiId|data为AttractionDTO|同上|
|POST /api/routes/plan|originPoiId、destinationPoiId、transportModes|data.routes|Controller将参数类错误映射400，其他AmapException映射503|
|POST /api/plans/generate|city、date、preference必填；budget与transportPreference可选|data为TravelPlanDTO|400参数错误，503高德服务异常|
|POST /api/voice/chat|multipart：file必填、sessionId可选；format=json可选|JSON+Base64或multipart/mixed|检查data.status；INVALID_AUDIO、ASR_EMPTY、ASR_FAILED、AGENT_FAILED、TTS_FAILED|
|GET /health|无|status=UP、timestamp|仅反映进程健康，不验证外部API|

交通方式：PUBLIC_TRANSPORT、WALKING、DRIVING、CYCLING。
AttractionDTO包含poiId、name、address、city、category、longitude、latitude、rating、openingHours、source。
TravelPlanDTO包含city、date、attractions、routes、schedule、budget、conflicts。
ChatResponse.travelPlan当前为基础TravelPlan，与TravelPlanDTO不同，前端必须分别适配。

## 3. 请求示例
```json
{"sessionId":"demo-device-001","message":"查询明天广州天气"}
```
```json
{"city":"广州","date":"2026-11-22","preference":"历史文化","budget":200,"transportPreference":"PUBLIC_TRANSPORT"}
```
```json
{"originPoiId":"从景点检索取得的真实ID","destinationPoiId":"另一个真实ID","transportModes":["PUBLIC_TRANSPORT","WALKING"]}
```
示例日期须在测试时更新为服务允许的预报日期范围。

## 4. 计划修改接口的集成差异
分工材料提出POST /api/plans/modify；本地当前controller目录未发现该生产入口，也未发现TravelPlanOrchestrator。不可将其写为已完成接口。
现有基础Agent通过TravelPlanService.modifyPlanForPeriod修改；真实景点计划有PlanModifyService.apply；开发冒烟入口为/api/plans/smoke/modify。
拟新增生产入口请求采用PlanModifyRequest：currentPlan、targetPeriod、newActivityPreference、confirmConflicts，及可选sessionId、city、date、budget、transportPreference。
拟返回PlanResult：plan、conflicts、needConfirm、reply。未确认仅返回风险，确认后修改并复检。具体接入由成员二与集成负责人确认。

## 5. 硬件协议
ESP32-S3经Wi-Fi调用部署域名的/api/chat，发送UTF-8 JSON，持续使用设备独立sessionId。读取reply和status，结构化天气用于LCD摘要。请求设置超时，网络失败提示重试，不能将失败响应当成功。
语音入口默认mock，真实ASR/TTS尚待实现。BLE（低功耗蓝牙）负责手机到设备输入，不参与后端HTTP通信。

## 6. 安全与后续
DASHSCOPE_API_KEY、AMAP_WEB_KEY仅由服务端环境变量读取。后台鉴权、用户身份与持久化均属设计扩展，不能仅凭sessionId认定用户身份。
后续需统一重复ApiResponse类型、接入生产冲突编排、增加访问控制和限流；不得无版本协商更改既有聊天响应。
