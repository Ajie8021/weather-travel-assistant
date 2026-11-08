# DigitalOcean App Platform 部署说明

**适用项目：** weather-travel-assistant（Java 21 + Spring Boot + Thymeleaf）
**适用分支：** `main`
**文档状态：** 已上线并完成跨境网络与模型链路排错。
**最后更新：** 2026-11-08

---

## 1. 前置条件

部署前必须在本地确认以下全部通过，否则不要在平台上重试：

| 检查项       | 命令 / 动作                                                  | 通过标准                                                     |
| ------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| JDK          | `java -version`                                              | 21                                                           |
| 构建         | `cd backend && ./mvnw -B clean test`                         | `BUILD SUCCESS`，测试全绿                                    |
| 可执行 jar   | `./mvnw -B clean package -DskipTests`                        | `target/wea-trav-assistant-*.jar` 存在                       |
| 启动         | `java -jar target/wea-trav-assistant-*.jar`                  | 日志出现 `Tomcat started on port 8080`、`Registered tools: N` |
| 健康检查     | `GET http://localhost:8080/health`                           | `{"status":"UP"}`                                            |
| 旧页面       | `/`、`/weather`、`/plan/generate`、`/chat`                   | 均可访问                                                     |
| 新接口       | `POST /api/plans/generate`、`POST /api/plans/modify`、`POST /api/voice/chat?format=json` | 返回统一 `ApiResponse`，不 500                               |
| 密钥         | `application.properties`、`pom.xml`、前端 JS                 | 无任何 Key 明文                                              |
| `.gitignore` | 仓库根                                                       | 含 `.idea/`、`*.iml`、`.env`、`*.log`、`target/`             |

外部账号准备：

- GitHub 仓库可被 DigitalOcean 授权访问；
- 已申请高德 **Web 服务** Key（`AMAP_WEB_KEY`）；
- **已准备模型服务 Key（换成 DeepSeek，解决阿里云跨境连接受阻问题）**；
- 运行环境可访问 `geocoding-api.open-meteo.com`、`api.open-meteo.com`、`restapi.amap.com` 及模型服务域名。

---

## 2. 应用配置

### 2.1 基本信息

| 配置项           | 值                         | 说明                                                         |
| ---------------- | -------------------------- | ------------------------------------------------------------ |
| 平台             | DigitalOcean App Platform  |                                                              |
| 区域             | `sgp` (新加坡)             | 跨境延迟低                                                   |
| 仓库             | `weather-travel-assistant` |                                                              |
| 分支             | `main`                     | 受保护分支，合并走 PR                                        |
| Source Directory | `backend`                  | Maven 工程在 `backend/` 子目录；若在仓库根则留空并调整命令   |
| 构建方式         | Dockerfile                 | 二选一，见 2.3                                               |
| HTTP Port        | `8080`                     | Spring 已配 `server.port=${PORT:8080}`，平台注入 `PORT` 也可 |
| Health Check     | HTTP，路径 `/health`       | 阶段4 新增端点，不检查外部依赖                               |
| Instance Size    | Basic / 1GB 起             | 外部调用为 HTTP，不占大量内存                                |
| Instance Count   | 1                          | 演示环境足够；扩容前先确认会话与 MCP 无状态                  |

### 2.2 构建与运行命令（Buildpack 方式）

| 配置项        | 值                                          |
| ------------- | ------------------------------------------- |
| Build Command | `./mvnw -B clean package -DskipTests`       |
| Run Command   | `java -jar target/wea-trav-assistant-*.jar` |

若平台默认 JDK 不是 21，在仓库根新增 `system.properties`：

```properties
java.runtime.version=21
```

### 2.3 Dockerfile 方式

`backend/Dockerfile`：

```dockerfile
# 多阶段构建：编译用 JDK21，运行用 JRE21，减小镜像
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

App Platform 选 Dockerfile，Source Directory `backend`，HTTP Port `8080`，Health Check `/health`。

### 2.4 环境变量（关键，已根据实际代码修正）

在 App Platform → Settings → Environment Variables 配置。**所有 Key 必须使用加密（Secret）变量，不写进仓库、Dockerfile、前端 JS、日志。**

| 变量                     | 必需       | 类型   | 说明                                                         |
| :----------------------- | :--------- | :----- | :----------------------------------------------------------- |
| `AMAP_WEB_KEY`           | 是         | Secret | 高德 Web 服务 Key（景点/路线）                               |
| `AMAP_TIMEOUT`           | 是         | 普通   | **设为 `15000`（15秒），缓解跨境调用高德超时**               |
| `DASHSCOPE_API_KEY`      | Agent 必需 | Secret | 模型 Key（代码中变量名为 DASHSCOPE_*）                       |
| `DASHSCOPE_MODEL`        | 在仓库     | 普通   | **设为 `deepseek-chat`**                                     |
| `DASHSCOPE_BASE_URL`     | 在仓库     | 普通   | **设为 `https://api.deepseek.com/v1`**                       |
| `MCP_ENDPOINT`           | 否         | 普通   | 留空走本地服务适配；接入远程 MCP 时填写                      |
| `SPRING_PROFILES_ACTIVE` | 在仓库     | 普通   | `prod`                                                       |
| `JAVA_TOOL_OPTIONS`      | 在仓库     | 普通   | **设为 `-Djava.net.preferIPv4Stack=true`，避免 IPv6 导致 SSL 握手失败** |
| `PORT`                   | 平台注入   | —      | 不需要手动配置                                               |

> **重要说明（踩坑记录）：**
>
> 1. DigitalOcean 新加坡节点访问国内阿里云 (`dashscope.aliyuncs.com`) 极不稳定，经常报 `Connect timed out` 或 `SSLHandshakeException`。强烈建议将 `DASHSCOPE_BASE_URL` 切换至 DeepSeek 等海外可达的 OpenAI 兼容服务。
> 2. 语音模块目前的 `voice.provider` 是写死为 `mock` 的（在 `application.properties` 中），无需通过环境变量覆盖。

------

## 3. 首次部署步骤

1. 登录 DigitalOcean → **App Platform** → **Create App**；
2. 选择 GitHub 仓库 `weather-travel-assistant`，分支 `main`；
3. **Source Directory** 留空：Maven 在仓库根；
4. 选择构建方式：
   - Dockerfile：选 `backend/Dockerfile`；
5. **HTTP Port** 填 `8080`；
6. **Health Check** 选 HTTP，路径 `/health`，初始延迟 30s、周期 10s、超时 3s、失败阈值 3；
7. 配置 2.4 的环境变量，Key 类型选 **Secret**（如 `AMAP_WEB_KEY`、`DASHSCOPE_API_KEY`），非敏感配置选普通；
8. 选择实例规格（Basic / 1GB 起）；
9. 点 **Deploy**，等待 Build 与 Deploy 均成功；
10. 线上地址 [慢游指南](https://weather-travel-assisant-o9c4j.ondigitalocean.app/)。

------

## 4. 部署后验证

### 4.1 基础验证

| 项       | 操作                                   | 预期                                       |
| :------- | :------------------------------------- | :----------------------------------------- |
| 健康检查 | `GET /health`                          | `{"status":"UP"}`                          |
| 首页     | 浏览器打开 `/`                         | 页面正常，含 `/chat` 入口                  |
| 旧天气页 | `/weather?city=Beijing&date=<未来3天>` | 页面正常或友好错误页                       |
| 旧计划页 | `/plan/generate`                       | 页面正常                                   |
| 聊天页   | `/chat`                                | 页面可打开，**提问后能稳定返回（不超时）** |
| 日志     | Runtime Logs                           | 无 Key 泄露、无未处理 500 堆栈直接暴露     |

### 4.2 接口验证（Postman/curl）

```bash
BASE_URL=https://<app>.ondigitalocean.app
DATE=$(date -d '+2 day' +%Y-%m-%d 2>/dev/null || date -v+2d +%Y-%m-%d)

# 生成行程
curl -fsS -X POST "$BASE_URL/api/plans/generate" \
  -H 'Content-Type: application/json' \
  -d "{\"city\":\"广州\",\"date\":\"$DATE\",\"preference\":\"OUTDOOR\",\"budget\":200}"

# 修改行程（未确认分支）
curl -fsS -X POST "$BASE_URL/api/plans/modify" \
  -H 'Content-Type: application/json' \
  -d '{"currentPlan":{...},"confirmConflicts":false,"targetPeriod":"AFTERNOON","newActivityPreference":"INDOOR"}'

# 语音（mock 模式）
curl -fsS -X POST "$BASE_URL/api/voice/chat?format=json" \
  -F "file=@smoke.wav" -F "sessionId=esp32s3_user_001"
```

预期：均返回 `{"code":200,...}`；语音在未配模型 Key 时返回 `status=AGENT_FAILED`，属预期，不 500。

### 4.3 MCP 验证（无模型也可）

Postman `POST https://<app>.ondigitalocean.app/mcp`，`Content-Type: application/json`：

```json
{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
```

预期返回包含 `generate_travel_plan`、`generate_travel_plan_with_conflicts`、`check_weather_conflicts`、`modify_travel_plan` 及各自 `inputSchema`。

### 4.4 冒烟脚本

仓库 `scripts/smoke.sh`：

```bash
BASE_URL=https://<app>.ondigitalocean.app ./scripts/smoke.sh
```

覆盖 `/health`、首页、旧天气页、`/api/plans/generate`、语音（若 `smoke.wav` 存在）。输出 `SMOKE OK` 即通过。

### 4.5 端到端（配模型 Key 后）

1. `/chat` 输入“帮我安排明天广州一日游，偏好户外”；
2. 若有冲突，Agent 展示冲突并询问，**不自动修改**；
3. 回复“确认调整，下午改成室内”，Agent 调 `modify_travel_plan`，返回新计划并重新检测；
4. 语音同一 `sessionId` 连续两轮，上下文继承、不串会话。

------

## 5. 日志与排查（重点：跨境网络排错）

| 现象                                   | 排查                                                         |
| :------------------------------------- | :----------------------------------------------------------- |
| Build 失败                             | 看 Build Logs；确认 JDK 21（`system.properties` 或 Dockerfile）、Source Directory、Maven Wrapper 可执行 |
| 启动失败                               | 看 Runtime Logs；确认端口 `${PORT:8080}`、环境变量齐全、无 Key 明文错误 |
| Health Check 失败                      | 确认 `/health` 可访问、初始延迟足够、实例已监听端口          |
| **`/chat` 报 `Connect timed out`**     | **DO 到模型服务网络超时。调大 `AgentService` 内的 `connectTimeout`，或换用 DeepSeek 等海外模型。** |
| **`/chat` 报 `SSLHandshakeException`** | **跨境 SSL 握手被阻断。添加环境变量 `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"`，或换用 DeepSeek。** |
| **`/chat` 前端报 `Failed to fetch`**   | **后端等待模型响应超过 60 秒被网关掐断。换用海外模型以降低响应延迟。** |
| **`plan_transport` 报超时**            | **DO 连高德 API 超时。增加环境变量 `AMAP_TIMEOUT=15000`。**  |
| 天气/景点 503                          | 检查 `AMAP_WEB_KEY`、出网、配额；未配置时接口应返回明确错误而非 500 |
| 语音 `AGENT_FAILED`                    | 未配模型 Key 时的预期；配 Key 后复测                         |
| 语音 `ASR_FAILED`/`TTS_FAILED`         | `VOICE_PROVIDER=dashscope` 但真实实现未完成；改回 `mock` 或完成实现 |

**安全要求：** 不在日志打印 Key、完整会话内容、用户音频；错误响应不返回堆栈给前端。

------

## 6. 回滚

1. App Platform → 应用 → **Deployments**；
2. 选择上一个 **成功** 的部署版本；
3. 点 **Rollback**，等待重新部署完成；
4. 复跑 4.1–4.4 验证。

应用无数据库、会话在内存，回滚不涉及数据迁移；回滚后需重新确认环境变量未被旧版本覆盖。

------

## 7. 安全与运维约定

- 所有 Key 仅通过平台 Secret 变量注入，不提交仓库、不写 Dockerfile、不下发前端；
- 环境变量变更后需重新部署生效；
- 公共网络部署前，`/mcp` 与调试接口应增加认证或访问控制（当前为本机/演示用途）；
- 定期检查高德、模型、语音服务配额；
- 日志保留平台默认周期，不额外持久化敏感内容。

------

## 8. 执行记录（部署后填写）

| 项                                         | 结果   | 时间                                                         | 执行人      |
| :----------------------------------------- | :----- | :----------------------------------------------------------- | :---------- |
| 首次部署成功                               | 已执行 | 2026-11-07                                                   | 任务5负责人 |
| `/health` 返回 UP                          | 已执行 | 2026-11-07                                                   | 任务5负责人 |
| 首页/旧页面/`/chat` 可访问                 | 已执行 | 2026-11-07                                                   | 任务5负责人 |
| `/api/plans/generate`、`/api/plans/modify` | 已执行 | 2026-11-08                                                   | 任务5负责人 |
| `/api/voice/chat?format=json`              | 已执行 | 2026-11-08                                                   | 任务5负责人 |
| MCP `tools/list` 四工具                    | 已执行 | 2026-11-08                                                   | 任务5负责人 |
| **切换海外模型（DeepSeek）及超时调优**     | 已执行 | 2026-11-08                                                   | 任务5负责人 |
| `scripts/smoke.sh` 通过                    | 已执行 | 2026-11-06                                                   | 任务5负责人 |
| 回滚演练                                   | 待执行 |                                                              |             |
| 线上地址                                   | 已生成 | [慢游指南](https://weather-travel-assisant-o9c4j.ondigitalocean.app/) | 任务5负责人 |
