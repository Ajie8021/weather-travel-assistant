# 天气与出行 Agent 使用说明

本次 C 模块为现有天气和计划服务增加了聊天式 Agent。用户用自然语言输入需求，后端调用真实天气/计划工具，再把回复与结构化结果交给聊天页面。旧的 `/weather` 和 `/plan/generate` 页面继续保留。

## 能力和调用流程

1. `/chat` 提供中文聊天界面，`POST /api/chat` 接收 `{ "sessionId": "可选", "message": "必填" }`。
2. Agent 将消息交给支持 OpenAI Chat Completions 工具调用格式的模型，由模型选择 `search_city`、`get_weather`、`generate_travel_plan` 或 `modify_travel_plan`。
3. 默认使用仓库已有的 `WeatherService`、`TravelPlanService`；如果设置 `MCP_ENDPOINT`，则发现并调用 MCP 服务中允许的天气和计划工具。模型只能看到天气/计划工具白名单，最多连续调用 4 次。
4. 城市搜索返回多个地点时，Agent 保存候选并等待用户按编号或名称确认；天气查询只接受本轮对话已确认的地点。天气缺失字段继续以 `null` 表示。
5. 会话上下文按 `sessionId` 隔离，内存保存，30 分钟无活动后清理。部署重启后会话不会恢复。

## 启动配置

后端需要 Java 21。使用 PowerShell 启动前，在当前窗口设置模型服务配置：

```powershell
$env:DASHSCOPE_API_KEY = "你的 DashScope 模型服务密钥"
# DashScope 的 OpenAI 兼容接口；如团队指定了其他模型，可按需要覆盖模型名。
$env:DASHSCOPE_BASE_URL = "https://llm-435dp91na852pfx3.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
$env:DASHSCOPE_MODEL = "qwen3.7-plus"
# A/B 的 MCP Streamable HTTP 服务接入后再配置；留空时使用仓库内本地服务适配器：
$env:MCP_ENDPOINT = ""
cd backend
.\mvnw.cmd spring-boot:run
```

然后访问 `http://localhost:8080/chat`。模型密钥只从环境变量读取，不要写入仓库或提交到 Git。没有设置 `DASHSCOPE_API_KEY` 时，聊天 API 会返回明确的配置提示；原有表单功能不受影响。

## 演示示例

- `查一下明天广州的天气`：搜索地点、确认唯一候选、调用天气服务并展示天气卡片。
- `帮我安排明天广州一日游，偏好美食`：调用天气和单日规划服务，展示分时段计划、穿衣建议、物品及提醒。
- `下午改成室内活动`：使用当前会话的计划，仅更新下午活动，其他计划条件保持不变。
- 城市有多个候选时：页面列出候选；用户回复 `1` 或城市全名后才继续。

## 聊天 API

请求：

```json
{ "sessionId": "可选的会话标识", "message": "帮我安排明天广州一日游，偏好美食" }
```

响应包含 `sessionId`、`reply`、`status`、`toolCalls`，以及可选的 `weather` 和 `travelPlan`。`toolCalls` 的成功状态由后端实际执行工具后填写，页面不会自行模拟成功状态。

## MCP 联调约定

配置 `MCP_ENDPOINT` 后，客户端通过 Streamable HTTP JSON-RPC 发现并调用工具：优先协商当前无状态协议；若服务端使用旧版 `initialize` 握手，则自动回退。只向模型暴露 `search_city`、`get_weather`、`generate_travel_plan`。团队服务端应保持与《天气模块使用与协作接口.md》一致的 `CityData`、`WeatherData` 字段语义和 `location` 确认流程。若 A/B 的 MCP Server 工具输出不是结构化 JSON 或 JSON 文本，需先统一工具返回格式再联调。

未配置 `MCP_ENDPOINT` 时，本地默认模式调用同一套 Java 天气及规划服务；配置团队提供的 MCP 服务地址后，工具发现和调用由该服务端提供。远程 MCP 的工具名称、参数和返回结构需要与团队服务端约定一致，并通过实际运行环境完成联调。
