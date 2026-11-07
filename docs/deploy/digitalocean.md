# DigitalOcean App Platform 部署说明

**适用项目：** weather-travel-assistant（Java 21 + Spring Boot + Thymeleaf）
**适用分支：** `main`
**文档状态：** 配置与步骤已确认；**线上部署待统一测试通过后执行**，执行结果补记于第 8 节。
**最后更新：** 2026-11-07

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
- 已准备模型 Key（`DASHSCOPE_API_KEY` 或 `OPENAI_API_KEY`）；
- 运行环境可访问 `geocoding-api.open-meteo.com`、`api.open-meteo.com`、`restapi.amap.com` 及模型服务域名。

---

## 2. 应用配置

### 2.1 基本信息

| 配置项           | 值                             | 说明                                                         |
| ---------------- | ------------------------------ | ------------------------------------------------------------ |
| 平台             | DigitalOcean App Platform      |                                                              |
| 仓库             | `weather-travel-assistant`     | 以实际 GitHub 地址为准                                       |
| 分支             | `main`                         | 受保护分支，合并走 PR                                        |
| Source Directory | `backend`                      | Maven 工程在 `backend/` 子目录；若在仓库根则留空并调整命令   |
| 构建方式         | Buildpack（Java）或 Dockerfile | 二选一，见 2.3                                               |
| HTTP Port        | `8080`                         | Spring 已配 `server.port=${PORT:8080}`，平台注入 `PORT` 也可 |
| Health Check     | HTTP，路径 `/health`           | 阶段4 新增端点，不检查外部依赖                               |
| Instance Size    | Basic / 1GB 起                 | 外部调用为 HTTP，不占大量内存                                |
| Instance Count   | 1                              | 演示环境足够；扩容前先确认会话与 MCP 无状态                  |

### 2.2 构建与运行命令（Buildpack 方式）

| 配置项        | 值                                          |
| ------------- | ------------------------------------------- |
| Build Command | `./mvnw -B clean package -DskipTests`       |
| Run Command   | `java -jar target/wea-trav-assistant-*.jar` |

若平台默认 JDK 不是 21，在仓库根新增 `system.properties`：

```properties
java.runtime.version=21
```

### 2.3 Dockerfile 方式（可选）

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

### 2.4 环境变量

在 App Platform → Settings → Environment Variables 配置。**所有 Key 使用加密（Secret）变量，不写进仓库、Dockerfile、前端 JS、日志。**

| 变量                     | 必需       | 类型   | 说明                                              |
| ------------------------ | ---------- | ------ | ------------------------------------------------- |
| `AMAP_WEB_KEY`           | 是         | Secret | 高德 Web 服务 Key（景点/路线）                    |
| `DASHSCOPE_API_KEY`      | Agent 必需 | Secret | 模型 Key；若用 OpenAI 兼容则改用 `OPENAI_API_KEY` |
| `OPENAI_API_KEY`         | 视模型     | Secret | 与 `DASHSCOPE_API_KEY` 二选一                     |
| `OPENAI_BASE_URL`        | 视模型     | 普通   | 兼容服务地址，如 `https://api.openai.com/v1`      |
| `OPENAI_MODEL`           | 视模型     | 普通   | 模型名，如 `gpt-4o-mini`                          |
| `MCP_ENDPOINT`           | 否         | 普通   | 留空走本地服务适配；接入远程 MCP 时填写           |
| `VOICE_PROVIDER`         | 是         | 普通   | 先 `mock`；真实 ASR/TTS 接入后改 `dashscope`      |
| `SPRING_PROFILES_ACTIVE` | 建议       | 普通   | `prod`                                            |
| `PORT`                   | 平台注入   | —      | 不需要手动配置                                    |

> 说明：`VOICE_PROVIDER=mock` 时语音链路可跑通格式校验与错误映射，但成功路径需要模型 Key；答辩前若未完成真实 ASR/TTS，保持 `mock` 并在演示中说明。

### 2.5 配置即代码（可选，`.do/app.yaml`）

若团队希望配置纳入版本管理，可在仓库根新增 `.do/app.yaml`（值与 2.2–2.4 一致）：

```yaml
name: weather-travel-assistant
region: sgp
services:
  - name: backend
    github:
      repo: your-org/weather-travel-assistant
      branch: main
      deploy_on_push: true
    source_dir: backend
    build_command: ./mvnw -B clean package -DskipTests
    run_command: java -jar target/wea-trav-assistant-*.jar
    http_port: 8080
    health_check:
      http_path: /health
      initial_delay_seconds: 30
      period_seconds: 10
      timeout_seconds: 3
      failure_threshold: 3
    instance_size_slug: basic-xxs
    instance_count: 1
    envs:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: VOICE_PROVIDER
        value: mock
      - key: AMAP_WEB_KEY
        scope: RUN_TIME
        type: SECRET
        value: ${AMAP_WEB_KEY}
      - key: DASHSCOPE_API_KEY
        scope: RUN_TIME
        type: SECRET
        value: ${DASHSCOPE_API_KEY}
```

> `type: SECRET` + `${...}` 表示值由平台加密变量注入。采用方式A（界面配置）时不需要此文件，二者选一即可。

---

## 3. 首次部署步骤

1. 登录 DigitalOcean → **App Platform** → **Create App**；
2. 选择 GitHub 仓库 `weather-travel-assistant`，分支 `main`；
3. **Source Directory** 填 `backend`（若 Maven 在仓库根则留空）；
4. 选择构建方式：
   - Buildpack：填 2.2 的 Build/Run Command；
   - Dockerfile：选 `backend/Dockerfile`；
5. **HTTP Port** 填 `8080`；
6. **Health Check** 选 HTTP，路径 `/health`，初始延迟 30s、周期 10s、超时 3s、失败阈值 3；
7. 配置 2.4 的环境变量，Key 类型选 **Secret**；
8. 选择实例规格（Basic / 1GB 起）；
9. 点 **Deploy**，等待 Build 与 Deploy 均成功；
10. 进入应用详情，记录线上地址 `https://<app>.ondigitalocean.app`。

---

## 4. 部署后验证

### 4.1 基础验证

| 项       | 操作                                   | 预期                                   |
| -------- | -------------------------------------- | -------------------------------------- |
| 健康检查 | `GET /health`                          | `{"status":"UP"}`                      |
| 首页     | 浏览器打开 `/`                         | 页面正常，含 `/chat` 入口              |
| 旧天气页 | `/weather?city=Beijing&date=<未来3天>` | 页面正常或友好错误页                   |
| 旧计划页 | `/plan/generate`                       | 页面正常                               |
| 聊天页   | `/chat`                                | 页面可打开                             |
| 日志     | Runtime Logs                           | 无 Key 泄露、无未处理 500 堆栈直接暴露 |

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

---

## 5. 日志与排查

| 现象                           | 排查                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| Build 失败                     | 看 Build Logs；确认 JDK 21（`system.properties` 或 Dockerfile）、Source Directory、Maven Wrapper 可执行 |
| 启动失败                       | 看 Runtime Logs；确认端口 `${PORT:8080}`、环境变量齐全、无 Key 明文错误 |
| Health Check 失败              | 确认 `/health` 可访问、初始延迟足够、实例已监听端口          |
| 天气/景点 503                  | 检查 `AMAP_WEB_KEY`、出网、配额；未配置时接口应返回明确错误而非 500 |
| `/chat` 无回复                 | 检查模型 Key、`OPENAI_BASE_URL`/`OPENAI_MODEL`；未配 Key 时属预期 |
| 语音 `AGENT_FAILED`            | 未配模型 Key 时的预期；配 Key 后复测                         |
| 语音 `ASR_FAILED`/`TTS_FAILED` | `VOICE_PROVIDER=dashscope` 但真实实现未完成；改回 `mock` 或完成实现 |
| MCP 工具缺失                   | 看启动日志 `Registered tools` 数量；若 C 为显式白名单，加入三个新工具 |
| 中文乱码                       | 属本机控制台编码问题，浏览器与 UTF-8 客户端正常              |

**安全要求：** 不在日志打印 Key、完整会话内容、用户音频；错误响应不返回堆栈给前端。

---

## 6. 回滚

1. App Platform → 应用 → **Deployments**；
2. 选择上一个 **成功** 的部署版本；
3. 点 **Rollback**，等待重新部署完成；
4. 复跑 4.1–4.4 验证。

应用无数据库、会话在内存，回滚不涉及数据迁移；回滚后需重新确认环境变量未被旧版本覆盖。

---

## 7. 安全与运维约定

- 所有 Key 仅通过平台 Secret 变量注入，不提交仓库、不写 Dockerfile、不下发前端；
- 环境变量变更后需重新部署生效；
- 公共网络部署前，`/mcp` 与调试接口应增加认证或访问控制（当前为本机/演示用途）；
- 定期检查高德、模型、语音服务配额；
- 日志保留平台默认周期，不额外持久化敏感内容。

---

## 8. 执行记录（部署后填写）

| 项                                         | 结果   | 时间 | 执行人 |
| ------------------------------------------ | ------ | ---- | ------ |
| 首次部署成功                               | 待执行 |      |        |
| `/health` 返回 UP                          | 待执行 |      |        |
| 首页/旧页面/`/chat` 可访问                 | 待执行 |      |        |
| `/api/plans/generate`、`/api/plans/modify` | 待执行 |      |        |
| `/api/voice/chat?format=json`              | 待执行 |      |        |
| MCP `tools/list` 四工具                    | 待执行 |      |        |
| `scripts/smoke.sh` 通过                    | 待执行 |      |        |
| 回滚演练                                   | 待执行 |      |        |
| 线上地址                                   | 待填   |      |        |
