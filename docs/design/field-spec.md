# 字段需求说明

> 面向成员一：行程、日程项、景点、冲突、修改请求、编排结果及修改记录的字段需求。  
> 代码基线：2026-11-22。本文以实际 Java 类为准，不把建议字段写成现有字段。  
> 事实来源： DTO 源码、`ConflictStringMapper.java`、`docs/integration/conflict-decisions.md` 和 `docs/冲突解决与语音接口交付.md` 。

## 1. 类型与访问器约定

| 类型 | 是否为 record | 不可变性 | 访问器 | 事实来源 |
|---|---|---|---|---|
| `TravelPlanDTO` | 是 | 不可变，修改需构造新实例 | `city()`、`date()` 等 | `TravelPlanDTO.java` |
| `ScheduleItemDTO` | 是 | 不可变，修改需构造新实例 | `period()`、`startTime()` 等 | `ScheduleItemDTO.java` |
| `AttractionDTO` | 是 | 不可变，修改需构造新实例 | `poiId()`、`name()` 等 | `AttractionDTO.java` |
| `WeatherConflictDTO` | 否 | 可变 POJO | `getType()`、`getSeverity()` 等 | `WeatherConflictDTO.java` |
| `PlanModifyRequest` | 否 | 可变 POJO | `getCity()`、`isConfirmConflicts()` 等 | `PlanModifyRequest.java` |
| `PlanResult` | 否 | 可变 POJO | `getPlan()`、`isNeedConfirm()` 等 | `PlanResult.java` |

“可空”按当前代码判断：record 没有 compact constructor 或非空校验，普通类也没有字段校验。因此引用类型在类型层面均可能为 null；下表中的 null 语义是业务约定，不代表代码已经强制校验。

## 2. `TravelPlanDTO`

**类型：Java record。不可变，修改必须构造新的 `TravelPlanDTO`。访问器为 `city()`、`date()`、`attractions()`、`routes()`、`schedule()`、`budget()`、`conflicts()`，不是 `getXxx()`。**

| 字段 | 类型 | 可空 | 单位/格式 | 来源 | 说明 |
|---|---|---|---|---|---|
| `city` | `String` | 是 | 城市名称 | `TravelPlanDTO.java` | 目的地城市；record 本身未强制非空 |
| `date` | `String` | 是 | `YYYY-MM-DD` | `TravelPlanDTO.java` | 出行日期字符串 |
| `attractions` | `List<AttractionDTO>` | 是 | 集合 | `TravelPlanDTO.java` | 行程涉及的景点；可能为空列表，record 未强制非空 |
| `routes` | `List<RouteDTO>` | 是 | 集合 | `TravelPlanDTO.java` | 行程路线；`RouteDTO` 不在本次目标 DTO 范围内 |
| `schedule` | `List<ScheduleItemDTO>` | 是 | 集合 | `TravelPlanDTO.java` | 早、午、晚日程项；可能为空或 null |
| `budget` | `Double` | 是 | 金额，币种未在 DTO 中声明 | `TravelPlanDTO.java` | 预算；null 表示未提供 |
| `conflicts` | `List<String>` | 是 | 字符串列表 | `TravelPlanDTO.java`、`ConflictStringMapper.java` | **明确为 `List<String>`，不是 `List<WeatherConflictDTO>`**；`ConflictStringMapper.withConflicts(...)` 生成时保证传入的集合由 `toLines(...)` 产生 |

### `conflicts` 映射说明

`ConflictStringMapper` 将结构化 `WeatherConflictDTO` 投影为字符串列表：

- 单条格式：`[SEVERITY][PERIOD] message 建议：suggestion`。
- `period` 为 null 时使用 `ALL_DAY`。
- `message` 为 null 时输出空字符串。
- `suggestion` 为空时不追加“建议”部分。
- `toLines(...)` 对字符串结果去重；`conflicts` 为 null 时返回空 `ArrayList`。
- `withConflicts(...)` 通过构造新 `TravelPlanDTO` 写入 `List<String>`，不修改原 record。
- 事实来源：`ConflictStringMapper.java`、`TravelPlanDTO.java`。

## 3. `ScheduleItemDTO`

**类型：Java record。不可变，修改必须构造新的 `ScheduleItemDTO`。访问器为 `period()`、`startTime()`、`endTime()`、`attraction()`、`activity()`。**

| 字段 | 类型 | 可空 | 单位/格式 | 来源 | 说明 |
|---|---|---|---|---|---|
| `period` | `String` | 是 | 时段字符串，约定 `MORNING` / `AFTERNOON` / `EVENING` | `ScheduleItemDTO.java` | 时段值；比较时业务代码通常忽略大小写，冲突服务会归一化为大写 |
| `startTime` | `String` | 是 | 时间字符串，示例 `09:00` | `ScheduleItemDTO.java` | 开始时间；record 未强制格式 |
| `endTime` | `String` | 是 | 时间字符串，示例 `11:00` | `ScheduleItemDTO.java` | 结束时间；record 未强制格式 |
| `attraction` | `AttractionDTO` | 是 | 对象引用 | `ScheduleItemDTO.java` | 关联景点；为 null 表示该日程项没有具体景点，例如晚间休闲项 |
| `activity` | `String` | 是 | 活动文案 | `ScheduleItemDTO.java` | 该时段的活动说明；null 表示未知或未提供 |

## 4. `AttractionDTO`

**类型：Java record。不可变，修改必须构造新的 `AttractionDTO`。访问器为 `poiId()`、`name()`、`address()`、`city()`、`category()`、`longitude()`、`latitude()`、`rating()`、`openingHours()`、`source()`。**

| 字段 | 类型 | 可空 | 单位/格式 | 来源 | 说明 |
|---|---|---|---|---|---|
| `poiId` | `String` | 是 | 供应商 POI 标识 | `AttractionDTO.java` | 真实景点 ID；由景点服务提供 |
| `name` | `String` | 是 | 文本 | `AttractionDTO.java` | 景点名称 |
| `address` | `String` | 是 | 文本 | `AttractionDTO.java` | 景点地址；null 表示未知 |
| `city` | `String` | 是 | 城市名称 | `AttractionDTO.java` | 所属城市 |
| `category` | `String` | 是 | 文本/分类 | `AttractionDTO.java` | 景点分类；用于户外关键词判断 |
| `longitude` | `Double` | 是 | 经度（度） | `AttractionDTO.java` | 经度数值；坐标系由上游来源决定，DTO 未声明 |
| `latitude` | `Double` | 是 | 纬度（度） | `AttractionDTO.java` | 纬度数值；坐标系由上游来源决定，DTO 未声明 |
| `rating` | `String` | 是 | 供应商原值，**不是 Double** | `AttractionDTO.java` | 评分字符串；null 表示未知 |
| `openingHours` | `String` | 是 | 文本，示例 `09:00-17:00` | `AttractionDTO.java` | 开放时间；null 表示未知，不参与冲突判断 |
| `source` | `String` | 是 | 数据源标识，示例 `AMAP` | `AttractionDTO.java` | 景点数据来源 |

## 5. `WeatherConflictDTO`

**类型：普通 Java 类，可变 POJO。访问器为 `getType()`、`getSeverity()`、`getPeriod()`、`getMessage()`、`getSuggestion()`、`getSource()`；设置器为对应的 `setXxx(...)`。**

| 字段 | 类型 | 可空 | 单位/格式 | 来源 | 说明 |
|---|---|---|---|---|---|
| `type` | `ConflictType` | 是 | 枚举 | `WeatherConflictDTO.java`、`ConflictType.java` | 冲突类型：`RAIN`、`THUNDERSTORM`、`WIND`、`HEAT`、`COLD`、`DATA_INCOMPLETE`、`OPENING_HOURS_CONFLICT` |
| `severity` | `ConflictSeverity` | 是 | 枚举 | `WeatherConflictDTO.java`、`ConflictSeverity.java` | 严重级别：`INFO`、`WARN`、`HIGH` |
| `period` | `String` | 是 | `MORNING` / `AFTERNOON` / `EVENING` / `ALL_DAY` | `WeatherConflictDTO.java` | 冲突所属时段；null 在字符串映射时按 `ALL_DAY` 处理 |
| `message` | `String` | 是 | 面向用户的文本 | `WeatherConflictDTO.java` | 冲突说明；不得断言未发生的天气事实 |
| `suggestion` | `String` | 是 | 面向用户的文本 | `WeatherConflictDTO.java` | 可执行调整建议；不得编造景点、票价或营业时间 |
| `source` | `String` | 是 | 数据源标识，如 `Open-Meteo` / `AMAP` / `rule` | `WeatherConflictDTO.java` | 冲突判断的数据来源 |

## 6. `PlanModifyRequest`

**类型：普通 Java 类，可变 POJO。访问器为 `getSessionId()`、`getCurrentPlan()`、`getActivityPreference()`、`getCity()`、`getDate()`、`getBudget()`、`getTransportPreference()`、`getTargetPeriod()`、`getNewActivityPreference()`、`isConfirmConflicts()`。**

| 字段 | 类型 | 可空 | 单位/格式 | 来源 | 说明 |
|---|---|---|---|---|---|
| `sessionId` | `String` | 是 | 会话标识 | `PlanModifyRequest.java` | 当前会话 ID；null 表示未提供 |
| `currentPlan` | `TravelPlanDTO` | 是 | 对象引用 | `PlanModifyRequest.java` | 待修改的原计划；修改服务对 null 会抛异常 |
| `activityPreference` | `String` | 是 | 活动偏好字符串 | `PlanModifyRequest.java` | 原活动偏好字段；与 `newActivityPreference` 并存 |
| `city` | `String` | 是 | 城市名称 | `PlanModifyRequest.java` | 未传或空白时继承原计划城市 |
| `date` | `String` | 是 | `YYYY-MM-DD` | `PlanModifyRequest.java` | 未传或空白时继承原计划日期 |
| `budget` | `Double` | 是 | 金额，币种未在 DTO 中声明 | `PlanModifyRequest.java` | 未传时继承原计划预算 |
| `transportPreference` | `String` | 是 | 交通偏好字符串 | `PlanModifyRequest.java` | 交通偏好；DTO 未声明枚举校验 |
| `targetPeriod` | `String` | 是 | 时段字符串，如 `AFTERNOON` | `PlanModifyRequest.java` | 局部修改的目标时段；为空时当前实现会把新偏好应用到所有日程项 |
| `newActivityPreference` | `String` | 是 | 活动偏好字符串，如 `INDOOR` | `PlanModifyRequest.java` | 目标时段的新活动偏好 |
| `confirmConflicts` | `boolean` | 否 | 布尔值，默认 `false` | `PlanModifyRequest.java` | false 时只检测和询问，不执行修改；true 时才允许修改 |

> 类注释明确约定：“未传字段表示保持不变”；`confirmConflicts=false` 时只返回冲突和询问，不执行修改。

## 7. `PlanResult`

**类型：普通 Java 类，可变 POJO。访问器为 `getPlan()`、`getConflicts()`、`isNeedConfirm()`、`getReply()`。**

| 字段 | 类型 | 可空 | 单位/格式 | 来源 | 说明 |
|---|---|---|---|---|---|
| `plan` | `TravelPlanDTO` | 是 | 对象引用 | `PlanResult.java` | 当前计划或修改后的计划；默认构造器下可能为 null |
| `conflicts` | `List<WeatherConflictDTO>` | 否（构造器/setter 会归一化 null 为空列表） | 结构化冲突列表 | `PlanResult.java` | 编排结果中的结构化冲突；为空列表表示没有冲突 |
| `needConfirm` | `boolean` | 否 | 布尔值，默认 `false` | `PlanResult.java` | 是否存在 `HIGH` 或 `WARN` 冲突，需要用户确认 |
| `reply` | `String` | 是 | 面向用户的回复/询问文案 | `PlanResult.java` | `needConfirm=true` 时通常是询问文案 |

## 8. `PlanModifyRecord`（建议新增）

**当前代码中未找到 `PlanModifyRecord` 类。以下字段全部为“建议新增”，不是现有 DTO 字段。**

| 字段 | 类型 | 可空 | 单位/格式 | 来源 | 说明 |
|---|---|---|---|---|---|
| `sessionId` | `String` | 否 | 会话标识 | 建议新增；参照 `PlanModifyRequest.sessionId` | 关联产生修改的会话，便于审计和追踪 |
| `planId` | `String` | 否 | 稳定计划 ID，建议 UUID | 建议新增 | 当前 `TravelPlanDTO` 没有计划 ID；持久化前需要生成稳定标识 |
| `modifiedPeriod` | `String` | 否 | `MORNING` / `AFTERNOON` / `EVENING` | 建议新增；参照 `PlanModifyRequest.targetPeriod` | 实际修改的目标时段 |
| `modifyIntent` | `String` | 否 | 用户意图文本 | 建议新增 | 保存自然语言意图，例如“下午改为室内活动” |
| `beforePlan` | `TravelPlanDTO` | 否 | 对象快照 | 建议新增 | 修改前计划快照，用于对比和回滚 |
| `afterPlan` | `TravelPlanDTO` | 否 | 对象快照 | 建议新增 | 修改后计划快照 |
| `confirmedBy` | `String` | 否 | 用户/操作者标识 | 建议新增 | 记录确认人；当前代码没有用户主键体系 |
| `confirmedAt` | `Instant` | 否 | ISO-8601 时间 | 建议新增 | 用户确认时间，便于审计 |
| `resultConflicts` | `List<WeatherConflictDTO>` | 否，建议空列表表示无冲突 | 结构化冲突列表 | 建议新增；参照 `PlanResult.conflicts` | 修改后再次检测得到的最终冲突 |

> 额外建议字段：`createdAt`（`Instant`，建议新增）。当前需求未列出该字段，但如果要按创建时间排序或建立索引，需要增加它。  
> 上述“建议新增”字段只为持久化设计提供依据，不代表代码已实现。

## 9. 给成员一的建议

### 9.1 主键与外键

- `PlanModifyRecord` 建议新增独立主键 `id`，类型可用 `String`（UUID）或数据库自增主键；当前代码没有该记录类，也没有主键。
- `sessionId` 建议作为会话关联键，与 `ChatSessionStore` 的会话标识保持一致。
- `planId` 建议作为计划关联键；当前 `TravelPlanDTO` 没有 `id` 字段，因此需要先补充计划持久化 ID。
- `beforePlan`、`afterPlan` 建议存 JSON/JSONB 快照，避免把嵌套的日程和景点展开成多张表；若需要关系化查询，再单独拆表。

### 9.2 索引建议

- `sessionId`
- `planId`
- `createdAt`（需同时补充该建议字段）
- 可选：`confirmedAt`，用于确认时间审计查询

### 9.3 枚举存储建议

- `WeatherConflictDTO.type` 存字符串，值来自 `ConflictType`。
- `WeatherConflictDTO.severity` 存字符串，值来自 `ConflictSeverity`。
- `WeatherConflictDTO.period` 存字符串，保留 `MORNING`、`AFTERNOON`、`EVENING`、`ALL_DAY`。
- 枚举使用字符串而不是 ordinal，便于扩展和避免枚举顺序变化导致历史数据错位。

## 10. 自检与差异记录

| 检查项 | 结果 | 来源 |
|---|---|---|
| `TravelPlanDTO.conflicts` 是否为 `List<String>` | 是 | `TravelPlanDTO.java`、`ConflictStringMapper.java` |
| record 是否使用 `xxx()` 访问器 | 是，三个 record 均按此规则列出 | `TravelPlanDTO.java`、`ScheduleItemDTO.java`、`AttractionDTO.java` |
| 普通类是否使用 `getXxx()`/`isXxx()` | 是 | `WeatherConflictDTO.java`、`PlanModifyRequest.java`、`PlanResult.java` |
| `PlanResult.conflicts` 是否可能为 null | 构造函数和 setter 将 null 归一化为空集合；字段默认也是空 `ArrayList` | `PlanResult.java` |
| `PlanModifyRecord` | 代码中不存在，表中字段均标记为“建议新增” | 代码检索结果 |
| `docs/integration/01-dto-freeze.md` | 未找到，已改用实际代码和现有集成文档 | 文件系统核对 |

本文不包含真实用户数据、密钥或内部地址。