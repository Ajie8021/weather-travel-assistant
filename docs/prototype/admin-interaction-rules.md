# 后台管理原型交互规则

> 适用范围：管理员登录、用户/会话记录、行程记录、异常与 API 日志、反馈管理。  
> 输出性质：交互规格与字段说明，不是已实现的后台接口契约。  
> 当前代码和 `openapi/travel-assistant.yaml` 中尚未设计 `/admin` 后台接口；本文的页面路由是原型路由，后台 API 需后续实现。  
> 事实来源：`openapi/travel-assistant.yaml`、controller 实际代码、`docs/design/field-spec.md` ；实际页面模板位于 `backend/src/main/resources/templates/`。

## 1. 全局约定

### 1.1 事实来源

| 来源 | 用途 | 状态 |
|---|---|---|
| `docs/design/field-spec.md` | `TravelPlanDTO`、`ScheduleItemDTO`、`AttractionDTO`、`WeatherConflictDTO`、`PlanModifyRequest`、`PlanResult` 字段 | 存在 |
| `openapi/travel-assistant.yaml` | 现有错误码和接口语义 | 存在 |
| `backend/src/main/java/.../controller/` | 现有 controller 行为与边界 | 存在 |
| `backend/src/main/resources/templates/` | 视觉风格、字体、颜色和基础组件 | 存在 |

### 1.2 管理台布局

- 登录页使用独立居中的表单卡片。
- 登录后页面使用统一后台壳层：
  - 顶部品牌区沿用 `site-header` 视觉语言。
  - 左侧导航包含：用户、会话、行程、日志、反馈。
  - 右侧内容区使用纸白卡片、细边框和轻阴影。
  - 页面之间通过导航、详情按钮和返回按钮可点击跳转。
- 公共页面最大内容宽度沿用现有站点的 1080px；宽表格在内容区内横向滚动，不把整页撑破。
- 所有页面使用中文；页面标题、筛选项、表头、按钮和状态文案必须可读。

### 1.3 视觉基线

来自现有 `backend/src/main/resources/templates/` 和 `static/css/app.css`：

- 页面背景：`#f6f8f5`
- 卡片底色：`#fffefa`
- 主文字：`#263a30`
- 品牌绿：`#315f4b`
- 分割线：`#e4eae3`
- 输入框边框：`#dfe7df`
- 聚焦色：`#d99551`
- 正文：`Aptos`, `Segoe UI`, `Microsoft YaHei`, sans-serif
- 标题：`Georgia`, `Songti SC`, serif
- 卡片圆角：14–25px；按钮和输入框圆角：9px
- 错误提示沿用浅红底：`#fff2ed` / 文字 `#934d40`

### 1.4 查询、详情和状态规则

- 查询表单：提交前做字段格式校验；点击“重置”清空筛选并回到第一页。
- 分页：默认每页 20 条；上一页/下一页不可用时禁用；页码和总数可见。
- 详情：支持“返回列表”和“上一条/下一条”；切换详情不丢失当前筛选和页码。
- 所有列表页：
  - 空态必须出现插图/图标、“暂无数据”和操作引导。
  - 失败态必须出现错误码、可读文案和“重试”按钮。
- 列表页默认不展示完整手机号、密码、Cookie、Authorization、API Key 或完整请求体。
- 所有示例只能是占位数据，例如 `{用户名}`、`{手机号（脱敏）}`、`{sessionId}`，不得写真实用户数据。

### 1.5 错误码约定

现有 `openapi/travel-assistant.yaml` 中的错误码可用于日志和详情展示：

`INVALID_INPUT`、`CITY_NOT_FOUND`、`CITY_AMBIGUOUS`、`OUT_OF_FORECAST_RANGE`、`API_TIMEOUT`、`WEATHER_SERVICE_UNAVAILABLE`、`WEATHER_DATA_INCOMPLETE`、`INTERNAL_ERROR`。

后台专属错误码当前不存在。以下仅为本原型建议，标记为“建议新增”，不能当作已实现契约：

- `ADMIN_AUTH_FAILED`：登录失败或会话失效。
- `ADMIN_FORBIDDEN`：已登录但无权访问。
- `ADMIN_DATA_LOAD_FAILED`：列表或详情加载失败。
- `ADMIN_ACTION_FAILED`：标记处理、回复等操作失败。

## 2. 组件对照表（与成员三统一）

| 组件 | 现有页面依据 | 统一规格 | 与成员三确认项 |
|---|---|---|---|
| 顶部品牌区 | `site-header`、`brand`、`brand-mark` | 高度 78px；左侧品牌，右侧管理员身份占位；背景与公共站点一致 | 后台是否保留“慢游指南”品牌字样和绿色 W 标记 |
| 主内容卡片 | `result-card`、`planner-card`、`empty-card` | 纸白 `#fffefa`，边框 `#e4eae3`，圆角 14–18px，轻阴影 `0 17px 45px #304b3810` | 后台是否沿用公共页阴影强度 |
| 字体层级 | `:root`、`.hero h1`、`.eyebrow` | 标题 Georgia/Songti SC；正文 Aptos/Segoe UI/Microsoft YaHei；辅助信息使用小号大写 eyebrow | 表头、详情标题、按钮字号最终统一 |
| 表格 | 新建组件，沿用现有色板 | 表头浅绿灰 `#eef3ed`；行高 48px；分割线 `#e4eae3`；选中行 `#f2f7f1`；长文本换行 | 是否固定表头、是否支持列宽拖拽 |
| 筛选栏 | 现有 `search-form`、`field` | 输入框高 46px、圆角 9px；筛选按钮与“重置”按钮并排；手机端纵向排列 | 筛选项数量与折叠方式 |
| 主按钮 | `.button.primary` | 品牌绿底、白字、最小高度 44px、圆角 9px | hover 和禁用态是否统一 |
| 次按钮 | `.button.secondary` | 白底、绿色文字、边框 `#dfe7df` | 重置、返回、上一条/下一条是否都用次按钮 |
| 状态标签 | 现有天气/警告浅色块 | `正常/已完成` 用绿色系；`处理中/待确认` 用暖色系；`失败/异常` 用浅红系 | 状态色和文案映射表 |
| 分页 | 新建组件 | 上一页、页码、下一页、总数；禁用态降低透明度 | 是否显示跳页输入框 |
| 弹窗 | 新建组件 | 纸白卡片、圆角 18px、遮罩；主按钮右置，取消按钮在左 | 确认弹窗宽度、按钮顺序 |
| Toast | 新建组件 | 成功：浅绿底 `#edf5eb`；失败：浅红底 `#fff0eb`；停留 3 秒 | 文案长度和是否可手动关闭 |
| 空态 | `.empty-card` | 图标 + “暂无数据” + 操作引导 + 主/次按钮 | 每个页面是否使用同一插图 |
| 失败态 | `.notice` | 错误码 + 可读文案 + 重试按钮，使用浅红底和 `role="alert"` | 错误码是否对管理员完整展示 |
| 详情布局 | 现有 `result-card` + 内容分块 | 顶部摘要、字段分组、时间线/表格；支持返回和上一条/下一条 | 详情使用抽屉还是独立页面 |

## 3. 页面清单

| 页面 | 路由 | 角色 |
|---|---|---|
| 管理员登录 | `/admin/login` | 未登录管理员 |
| 用户列表 | `/admin/users` | 已登录管理员 |
| 会话记录 | `/admin/sessions` | 已登录管理员 |
| 行程记录 | `/admin/plans` | 已登录管理员 |
| 异常与 API 日志 | `/admin/logs` | 已登录管理员 |
| 反馈管理 | `/admin/feedback` | 已登录管理员 |

## 4. `/admin/login` 管理员登录

**角色：** 未登录管理员。

### 4.1 页面目标

管理员通过账号和密码进入后台；验证码为可选能力，若启用则显示验证码输入框和刷新入口。

### 4.2 字段

| 字段 | 类型/控件 | 必填 | 展示/校验 | 来源 |
|---|---|---|---|---|
| 管理员账号 | 文本输入框 | 是 | 去除首尾空格；不能为空 | 后台原型建议字段；当前 `field-spec.md` 无管理员认证 DTO |
| 密码 | 密码输入框 | 是 | 掩码显示；不能为空 | 后台原型建议字段 |
| 验证码 | 文本输入框 | 可选 | 启用时才显示；刷新按钮更新验证码 | 后台原型可选字段 |

### 4.3 操作

- “登录”：提交表单，请求期间按钮禁用并显示“登录中…”。
- “重置”：清空账号、密码和验证码。
- 登录成功后进入 `/admin/users`；登录失败停留在当前页。
- 不展示测试账号、真实密码或真实验证码。

### 4.4 空态

- 初始状态显示空表单，提示“请输入管理员账号和密码”。
- 未填写必填项时，登录按钮不可用或提交后显示“请输入管理员账号和密码”。

### 4.5 失败态

- 账号或密码错误：显示“账号或密码错误，请重新输入”（建议错误码 `ADMIN_AUTH_FAILED`）。
- 验证码错误：显示“验证码错误，请刷新后重试”（同上，建议新增）。
- 服务不可用：显示“登录服务暂时不可用，请稍后重试”+“重试”按钮（建议错误码 `ADMIN_DATA_LOAD_FAILED`）。
- 不使用“用户不存在”等可枚举账号的提示。

## 5. `/admin/users` 用户列表

**角色：** 已登录管理员。

### 5.1 页面目标

查询用户并查看用户详情及该用户的会话列表。

### 5.2 查询字段和列表字段

| 字段 | 类型/格式 | 来源 | 说明 |
|---|---|---|---|
| 用户名 | 文本 | 建议新增；当前 `field-spec.md` 无 UserDTO | 支持模糊查询，去首尾空格 |
| 手机号 | 文本 | 建议新增 | 查询时允许输入完整或部分号码；列表脱敏展示 |
| 状态 | 下拉选择 | 建议新增 | 建议值：`全部`、`正常`、`禁用` |
| `userId` | String | 建议新增 | 用户稳定标识 |
| `username` | String | 建议新增 | 用户显示名 |
| `phoneMasked` | String | 建议新增 | 脱敏手机号，例如 `138****0000` |
| `status` | String | 建议新增 | 用户状态 |
| `sessionCount` | Integer | 建议新增 | 会话数量 |
| `lastActiveAt` | Instant | 建议新增 | ISO-8601 时间 |
| `createdAt` | Instant | 建议新增 | 注册时间 |

### 5.3 详情字段

用户详情中的会话列表建议展示：

| 字段 | 类型/格式 | 来源 |
|---|---|---|
| `sessionId` | String | 建议新增；可参照 `PlanModifyRequest.sessionId` |
| `startedAt` | Instant | 建议新增 |
| `lastActiveAt` | Instant | 建议新增 |
| `messageCount` | Integer | 建议新增 |
| `status` | String | 建议新增 |

### 5.4 操作

- 查询、重置、分页。
- 点击行或“查看详情”打开用户详情。
- 详情支持“返回列表”“上一条”“下一条”。
- 只读展示，不在原型中实现用户删除或修改权限，避免误操作。

### 5.5 空态

- 无用户：图标 + “暂无用户数据” + “尝试清空筛选条件，或等待用户产生记录。” + “重置筛选”按钮。
- 用户存在但详情无会话：显示“该用户暂无会话记录”。

### 5.6 失败态

- 列表加载失败：`ADMIN_DATA_LOAD_FAILED` + “用户列表加载失败，请稍后重试” + “重试”按钮。
- 详情加载失败：`ADMIN_DATA_LOAD_FAILED` + “用户详情加载失败” + “返回列表”/“重试”。
- 无权限：`ADMIN_FORBIDDEN` + “当前账号无权访问用户记录”。

## 6. `/admin/sessions` 会话记录

**角色：** 已登录管理员。

### 6.1 页面目标

按会话、用户或时间范围查询会话，查看消息列表和工具调用摘要。

### 6.2 查询字段和列表字段

| 字段 | 类型/格式 | 来源 | 说明 |
|---|---|---|---|
| `sessionId` | String | 建议新增；可参照 `PlanModifyRequest.sessionId` | 精确或模糊查询 |
| 用户 | 文本/用户选择器 | 建议新增 | 按用户名或用户 ID 查询 |
| 时间范围 | 日期时间范围 | 建议新增 | 开始时间和结束时间 |
| `messageCount` | Integer | 建议新增 | 消息数量 |
| `startedAt` | Instant | 建议新增 | 会话开始时间 |
| `lastActiveAt` | Instant | 建议新增 | 最近活跃时间 |
| `status` | String | 建议新增 | 建议值：进行中、已结束、异常 |

### 6.3 详情字段

- 消息列表：角色（用户/助手/工具）、消息摘要、时间、状态。
- 工具调用：工具名、状态（成功/失败）、耗时、错误摘要。
- 详情必须脱敏，不展示模型 Key、Authorization、Cookie、完整内部地址或完整原始响应。
- 建议新增消息与工具调用 DTO；当前 `field-spec.md` 未定义会话记录 DTO。

### 6.4 操作

- 查询、重置、分页。
- 打开会话详情。
- 返回列表、上一条、下一条。
- 消息列表支持按时间正序/倒序切换。

### 6.5 空态

- 无会话：图标 + “暂无会话记录” + “尝试调整 sessionId、用户或时间范围。” + “重置筛选”。
- 详情无消息：显示“该会话暂无消息记录”。

### 6.6 失败态

- `ADMIN_DATA_LOAD_FAILED` + “会话记录加载失败，请稍后重试” + “重试”按钮。
- 详情失败：显示错误码和“返回列表”；不得用空白页代替。

## 7. `/admin/plans` 行程记录

**角色：** 已登录管理员。

### 7.1 页面目标

查询并查看已生成行程、结构化冲突、用户确认状态和修改记录。

### 7.2 查询字段

| 字段 | 类型/格式 | 来源 | 说明 |
|---|---|---|---|
| 城市 | 文本 | `TravelPlanDTO.city` | 模糊查询 |
| 日期 | 日期 | `TravelPlanDTO.date` | `YYYY-MM-DD` |
| 状态 | 下拉选择 | 建议新增 | 建议值：全部、已生成、待确认、已修改、失败；当前 DTO 无状态字段 |
| `planId` | String | 建议新增 | 当前 `TravelPlanDTO` 无计划 ID，持久化时需补 |
| 用户 | 文本/用户选择器 | 建议新增 | 当前 `TravelPlanDTO` 无用户字段 |

### 7.3 列表字段

| 字段 | 类型 | 来源 | 说明 |
|---|---|---|---|
| 计划 ID | String | 建议新增 | 稳定计划标识 |
| 城市 | String | `TravelPlanDTO.city` | 目的地城市 |
| 日期 | String | `TravelPlanDTO.date` | `YYYY-MM-DD` |
| 预算 | Double | `TravelPlanDTO.budget` | 金额；币种未在 DTO 中声明 |
| 冲突数 | Integer | 由 `TravelPlanDTO.conflicts` 计算 | `conflicts` 是 `List<String>` |
| 是否需确认 | Boolean | `PlanResult.needConfirm` | 仅在保存了编排结果时可用 |
| 更新时间 | Instant | 建议新增 | 当前 DTO 无更新时间 |
| 状态 | String | 建议新增 | 当前 DTO 无状态字段 |

### 7.4 详情字段与 `field-spec.md` 对应

**基础信息**

| 页面字段 | 来源字段 | 类型/说明 |
|---|---|---|
| 城市 | `TravelPlanDTO.city` | String |
| 日期 | `TravelPlanDTO.date` | `YYYY-MM-DD` |
| 预算 | `TravelPlanDTO.budget` | Double |
| 冲突提示 | `TravelPlanDTO.conflicts` | **`List<String>`** |
| 是否需确认 | `PlanResult.needConfirm` | Boolean |
| 回复/询问文案 | `PlanResult.reply` | String |

**日程表**

| 页面字段 | 来源字段 | 类型 |
|---|---|---|
| 时段 | `ScheduleItemDTO.period` | String |
| 开始时间 | `ScheduleItemDTO.startTime` | String |
| 结束时间 | `ScheduleItemDTO.endTime` | String |
| 景点 | `ScheduleItemDTO.attraction.name` | String |
| 活动 | `ScheduleItemDTO.activity` | String |

**景点表**

| 页面字段 | 来源字段 | 类型 |
|---|---|---|
| 景点 ID | `AttractionDTO.poiId` | String |
| 名称 | `AttractionDTO.name` | String |
| 地址 | `AttractionDTO.address` | String |
| 城市 | `AttractionDTO.city` | String |
| 分类 | `AttractionDTO.category` | String |
| 经度/纬度 | `AttractionDTO.longitude` / `latitude` | Double |
| 评分 | `AttractionDTO.rating` | String |
| 开放时间 | `AttractionDTO.openingHours` | String；null 显示未知 |
| 来源 | `AttractionDTO.source` | String |

**冲突与修改记录**

- 结构化冲突：`PlanResult.conflicts`（`List<WeatherConflictDTO>`），展示 `type`、`severity`、`period`、`message`、`suggestion`、`source`。
- 字符串冲突：`TravelPlanDTO.conflicts`（`List<String>`），展示 `ConflictStringMapper` 投影后的文本。
- 修改记录：使用 `field-spec.md` 中建议新增的 `PlanModifyRecord` 字段：
  `sessionId`、`planId`、`modifiedPeriod`、`modifyIntent`、`beforePlan`、`afterPlan`、`confirmedBy`、`confirmedAt`、`resultConflicts`。
- 这些修改记录字段当前没有 Java 实现，页面必须标注“建议新增字段”，不能当成现有接口字段。

### 7.5 操作

- 查询、重置、分页。
- 打开行程详情。
- 详情支持返回列表、上一条、下一条。
- 后台原型默认只读，不提供直接编辑行程的操作，避免绕过用户确认流程。
- 冲突与修改记录按区块折叠，默认展开基础信息。

### 7.6 空态

- 无行程：图标 + “暂无行程记录” + “尝试调整城市、日期或状态筛选。” + “重置筛选”。
- 行程无日程：显示“该行程暂无可展示的日程安排”。
- 冲突为空：显示“未发现冲突记录”；不显示伪造的风险项。

### 7.7 失败态

- `ADMIN_DATA_LOAD_FAILED` + “行程记录加载失败，请稍后重试” + “重试”按钮。
- 冲突详情失败：显示“冲突详情加载失败”并保留基础行程信息。
- 修改记录缺失时显示“暂无修改记录”，不能伪造成“未修改”。

## 8. `/admin/logs` 异常与 API 日志

**角色：** 已登录管理员。

### 8.1 页面目标

查询异常和 API 调用日志，查看错误码、耗时、接口和脱敏后的错误详情。

### 8.2 查询字段和列表字段

| 字段 | 类型/格式 | 来源 | 说明 |
|---|---|---|---|
| 级别 | 下拉选择 | 建议新增 | ERROR、WARN、INFO |
| 接口/路径 | 文本 | 建议新增 | 支持模糊查询 |
| 时间范围 | 日期时间范围 | 建议新增 | 开始/结束时间 |
| `timestamp` | Instant | 建议新增 | 日志时间 |
| `level` | String | 建议新增 | ERROR/WARN/INFO |
| `requestId` | String | 建议新增 | 请求追踪 ID |
| `httpMethod` | String | 建议新增 | GET/POST 等 |
| `path` | String | 建议新增 | 接口路径 |
| `statusCode` | Integer | 建议新增 | HTTP 状态码 |
| `errorCode` | String | 建议新增 | 现有错误码或 `INTERNAL_ERROR` |
| `durationMs` | Integer | 建议新增 | 耗时（毫秒） |

### 8.3 详情字段

- 堆栈摘要、错误码、可读错误信息、请求追踪 ID、耗时。
- 请求参数仅展示脱敏后的摘要。
- 不展示真实 Key、Cookie、Authorization、完整请求体或个人敏感信息。
- 堆栈仅对管理员可见，页面提供“复制追踪 ID”而非复制密钥或完整响应。

### 8.4 操作

- 查询、重置、分页。
- 打开日志详情。
- 返回列表、上一条、下一条。
- “复制追踪 ID”/“复制错误码”可作为辅助操作；“重试”仅用于重新加载详情，不自动重放外部请求。

### 8.5 空态

- 无日志：图标 + “暂无异常或 API 日志” + “调整级别、接口或时间范围后重试。” + “重置筛选”。

### 8.6 失败态

- `ADMIN_DATA_LOAD_FAILED` + “日志加载失败，请稍后重试” + “重试”按钮。
- 日志详情失败：显示错误码和“返回列表”。
- 不因日志详情失败而清空当前日志列表筛选条件。

## 9. `/admin/feedback` 反馈管理

**角色：** 已登录管理员。

### 9.1 页面目标

查询用户反馈，查看详情，标记处理并回复。

### 9.2 查询字段和列表字段

| 字段 | 类型/格式 | 来源 | 说明 |
|---|---|---|---|
| 状态 | 下拉选择 | 建议新增 | 全部、待处理、处理中、已处理 |
| 类型 | 下拉选择 | 建议新增 | 建议值：功能问题、数据问题、体验建议、其他 |
| `feedbackId` | String | 建议新增 | 反馈唯一标识 |
| 用户 | String | 建议新增 | 用户显示名或用户 ID |
| 类型 | String | 建议新增 | 反馈分类 |
| 内容摘要 | String | 建议新增 | 截断展示，详情看完整内容 |
| 状态 | String | 建议新增 | 待处理/处理中/已处理 |
| `createdAt` | Instant | 建议新增 | 提交时间 |
| `assignee` | String | 建议新增 | 处理人 |
| `updatedAt` | Instant | 建议新增 | 最近更新时间 |

### 9.3 详情字段

- 反馈完整内容。
- 用户/联系方式脱敏展示。
- 附件列表（若存在）；预览失败时显示文件名和“附件不可预览”。
- 处理历史：操作人、操作时间、处理结果。
- 当前 `field-spec.md` 未定义反馈 DTO，以上均为后台原型建议字段。

### 9.4 操作

- 查询、重置、分页。
- 打开反馈详情。
- “标记已处理”：先弹出确认弹窗，确认成功后 Toast“反馈已标记为已处理”，并刷新列表。
- “回复”：弹出编辑弹窗；内容不能为空；成功 Toast“回复已发送”；失败保留已输入内容并可重试。
- 返回列表、上一条、下一条。
- 不展示真实用户联系方式；不自动向真实用户发送未确认的内容。

### 9.5 空态

- 无反馈：图标 + “暂无反馈” + “当前筛选条件下没有反馈，可清空筛选或等待用户提交。” + “重置筛选”。

### 9.6 失败态

- 列表失败：`ADMIN_DATA_LOAD_FAILED` + “反馈列表加载失败，请稍后重试” + “重试”按钮。
- 标记处理失败：`ADMIN_ACTION_FAILED` + “标记失败，请重试”；保持原状态。
- 回复失败：`ADMIN_ACTION_FAILED` + “回复发送失败，请重试”；保留输入内容。
- 详情失败：显示错误码和“返回列表”。

## 10. 页面间跳转与处理闭环

- 登录成功：`/admin/login` → `/admin/users`。
- 左侧导航：`/admin/users`、`/admin/sessions`、`/admin/plans`、`/admin/logs`、`/admin/feedback` 相互可点击跳转。
- 列表 → 详情 → 返回列表：保留筛选条件与当前页码。
- 反馈处理成功后：关闭弹窗，刷新当前列表，使用 Toast 提示成功。
- 所有失败提示可见错误码、可读文案和重试动作；不得显示未脱敏的原始异常。

## 11. 验收清单

- [ ] 6 个页面均包含路由和角色。
- [ ] 6 个页面均包含字段、操作、空态和失败态。
- [ ] 行程详情字段与 `docs/design/field-spec.md` 对应。
- [ ] `TravelPlanDTO.conflicts` 明确展示为 `List<String>`。
- [ ] `PlanResult.conflicts` 明确展示为 `List<WeatherConflictDTO>`。
- [ ] 修改记录字段均标注“建议新增”。
- [ ] 用户、会话、日志、反馈页面中超出 `field-spec.md` 的字段均标注“建议新增”。
- [ ] 空列表和失败提示均有明确中文文案。
- [ ] 组件对照表列出与成员三统一项。
- [ ] 文档中无真实用户数据、真实 Key 或真实内部地址。