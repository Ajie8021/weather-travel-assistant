# 业务规则说明

> 适用范围：天气冲突、`needConfirm`、未确认不改、仅改目标时段、数据缺失处理。  
>
> 代码基线：2026-11-22。本文所有阈值与判断均以当前 Java 代码和 `application.properties` 为准。  
>
> 事实来源：`docs/冲突解决与语音接口交付.md` 第 2、3 节、`docs/integration/conflict-decisions.md` 。

## 1. 范围与术语

| 术语 | 含义 | 事实来源 |
|---|---|---|
| `WeatherData` | 已确认地点、日期的标准化天气数据；字段可能为 null | `WeatherConflictService.java` |
| `TravelPlanDTO` | 不可变行程 record，包含城市、日期、景点、路线、日程、预算和冲突字符串 | `TravelPlanDTO.java` |
| `WeatherConflictDTO` | 结构化冲突，包含类型、级别、时段、说明、建议和来源 | `WeatherConflictDTO.java` |
| `WARN` | 建议调整，需要用户确认 | `ConflictSeverity.java` |
| `HIGH` | 强烈建议调整，必须先询问用户 | `ConflictSeverity.java` |
| `INFO` | 提示性信息，不要求确认 | `ConflictSeverity.java` |
| `needConfirm` | 是否需要先向用户确认后再修改行程 | `PlanResult.java`、`PlanSmokeController.java` |
| `confirmConflicts` | 修改请求中的确认标志；false 时只检测、不修改 | `PlanModifyRequest.java`、`PlanSmokeController.java` |
| `targetPeriod` | 需要局部修改的目标时段，如 `AFTERNOON` | `PlanModifyRequest.java`、`PlanModifyService.java` |

本文中的“不修改”指不改变原计划的业务内容；由于 `TravelPlanDTO` 是 record，返回新 DTO 时只允许投影冲突字符串，不得改变日程、景点、路线或预算。

## 2. 天气冲突规则

### 2.1 阈值总表

| 规则 | 触发条件 | 冲突类型 | 级别 | 事实来源 |
|---|---|---|---|---|
| 高温 | `maxTemp >= 35℃` | `HEAT` | `WARN` | `WeatherConflictProperties.java`、`WeatherConflictService.java` |
| 低温 | `minTemp <= 5℃` | `COLD` | `WARN` | `WeatherConflictProperties.java`、`WeatherConflictService.java` |
| 大风 | `windSpeed >= 30 km/h` | `WIND` | `WARN` | `WeatherConflictProperties.java`、`WeatherConflictService.java` |
| 降雨 | `precipitationProbability >= 50%` 或 `precipitation > 0 mm` | `RAIN` | `WARN` | `WeatherConflictProperties.java`、`WeatherConflictService.java` |
| 雷暴 | WMO 天气代码为 `95`、`96` 或 `99` | `THUNDERSTORM` | `HIGH` | `WeatherConflictService.java` |

以上 35/5/30/50 为 `WeatherConflictProperties` 的默认值，并与 `backend/src/main/resources/application.properties` 中的配置一致。雷暴代码集合是 `WeatherConflictService` 内的 `Set.of(95, 96, 99)`。

### 2.2 高温

- 触发条件：`maxTemp != null` 且 `maxTemp >= weather.conflict.high-temp`。
- 当前默认阈值：`35℃`。
- 冲突类型/级别：`HEAT` / `WARN`。
- 语义：最高温达到或超过 35℃ 即触发，不是只在大气温度严格高于 35℃ 时触发。
- 事实来源：`WeatherConflictProperties.java`、`WeatherConflictService.java`。

### 2.3 低温

- 触发条件：`minTemp != null` 且 `minTemp <= weather.conflict.low-temp`。
- 当前默认阈值：`5℃`。
- 冲突类型/级别：`COLD` / `WARN`。
- 语义：最低温达到或低于 5℃ 即触发。
- 事实来源：`WeatherConflictProperties.java`、`WeatherConflictService.java`。

### 2.4 大风

- 触发条件：`windSpeed != null` 且 `windSpeed >= weather.conflict.high-wind-speed`。
- 当前默认阈值：`30 km/h`。
- 冲突类型/级别：`WIND` / `WARN`。
- 语义：风速达到或超过 30 km/h 即触发。
- 事实来源：`WeatherConflictProperties.java`、`WeatherConflictService.java`。

### 2.5 降雨

- 日级触发条件一：`precipitationProbability != null` 且 `precipitationProbability >= weather.conflict.rain-probability`。
- 日级触发条件二：`precipitation != null` 且 `precipitation > weather.conflict.rain-amount`。
- 当前默认阈值：降水概率 `50%`，降水量 `0.0 mm`；由于使用严格大于，任意正降水量都会触发第二条件。
- 冲突类型/级别：`RAIN` / `WARN`。
- 时段级补充：当日程项为户外景点时，代码还会用 WMO 降雨代码集合
  `51、53、55、56、57、61、63、65、66、67、80、81、82`
  作为降雨风险来源；该集合不替代上述 50%/0 mm 阈值。
- 事实来源：`WeatherConflictProperties.java`、`WeatherConflictService.java`。

### 2.6 雷暴

- 触发条件：`weatherCode != null` 且天气代码属于 `95、96、99`。
- 冲突类型/级别：`THUNDERSTORM` / `HIGH`。
- 日级和户外时段级均按该代码集合判断。
- 事实来源：`WeatherConflictService.java`、`ConflictType.java`、`ConflictSeverity.java`。

### 2.7 数据缺失摘要

- `weather == null` 时：直接产生 `DATA_INCOMPLETE` / `WARN`。
- 关键字段存在 null 时：产生 `DATA_INCOMPLETE` / `WARN`，并逐项列出缺失字段。
- `DATA_INCOMPLETE` 不是 INFO，不能视为“天气正常”。
- 详细字段范围与处理规则见第 6 节。
- 事实来源：`WeatherConflictService.java`。

> 开放时间冲突 `OPENING_HOURS_CONFLICT` 在当前代码中存在，但不属于本次业务规则范围，本文不展开其判断语义。

## 3. `needConfirm` 规则

### 3.1 判定条件

`needConfirm` 由 `WeatherConflictService.hasBlockingConflict(List<WeatherConflictDTO>)` 计算：

| 冲突列表状态 | `needConfirm` | 事实来源 |
|---|---|---|
| 任一冲突级别为 `HIGH` | `true` | `WeatherConflictService.java` |
| 任一冲突级别为 `WARN` | `true` | `WeatherConflictService.java` |
| 仅有 `INFO` | `false` | `WeatherConflictService.java` |
| `null` 或空列表 | `false` | `WeatherConflictService.java` |

因此规则必须表述为：**存在 HIGH 或 WARN 冲突时 `needConfirm=true`；仅 INFO 或空列表时 `needConfirm=false`。**

### 3.2 与结果对象的关系

- `PlanSmokeController.smokeGenerate(...)` 在检测后调用 `hasBlockingConflict(conflicts)`，并将结果写入 `PlanResult.needConfirm`。
- 修改后的再次检测也使用同一方法计算新的 `needConfirm`。
- `TravelPlanTools.generateTravelPlanWithConflicts(...)` 也复用 `detect(...)` 与 `hasBlockingConflict(...)`，返回包含 `needConfirm` 的 `PlanResult`。
- 事实来源：`PlanSmokeController.java`、`TravelPlanTools.java`、`PlanResult.java`。

### 3.3 数据缺失时的确认

`DATA_INCOMPLETE` 的级别是 `WARN`，所以：

- `needConfirm=true`；
- 必须向用户说明“无法判断”；
- 不得回答“天气良好”或“无风险”。
- 事实来源：`WeatherConflictService.java`、`PlanResult.java`、`docs/冲突解决与语音接口交付.md` 第 4.4 节。

## 4. 用户未确认时的行为

### 4.1 硬规则

用户未确认时：

1. 不得修改原行程；
2. 不得自动调用行程修改逻辑；
3. 只返回冲突提示和询问；
4. 原计划的时段、景点、路线、城市、日期和预算内容保持不变。

### 4.2 代码行为

`PlanSmokeController.smokeModify(...)` 中，当 `confirmConflicts=false` 时：

- 调用 `conflictService.detect(req.weather, req.currentPlan)`；
- 调用 `hasBlockingConflict(conflicts)` 得到 `need`；
- 直接返回 `PlanResult`；
- 不会执行后面的 `modifyService.apply(...)` 分支。

返回值中的 `TravelPlanDTO` 是 `ConflictStringMapper.withConflicts(req.currentPlan, conflicts)` 生成的新 record，只更新 `conflicts` 字符串列表，不改变日程和行程业务字段。

事实来源：`PlanSmokeController.java`、`PlanModifyRequest.java`、`PlanModifyService.java`、`ConflictStringMapper.java`、`docs/冲突解决与语音接口交付.md` 第 3.1、4.4 节。

## 5. 用户确认后的局部修改规则

### 5.1 修改前置条件

只有以下条件同时满足才执行修改：

- `confirmConflicts=true`；
- 存在当前计划 `currentPlan`；
- 指定了目标时段 `targetPeriod`；
- 指定了新的活动偏好 `newActivityPreference`。

`PlanSmokeController` 在确认分支中构造 `PlanModifyRequest`，设置当前计划、目标时段、新活动偏好和 `confirmConflicts=true`，然后调用 `modifyService.apply(req.currentPlan, m)`。

事实来源：`PlanSmokeController.java`、`PlanModifyRequest.java`。

### 5.2 仅修改目标时段

在 `PlanModifyService.apply(...)` 中：

- 遍历原计划 `schedule`；
- 仅当 `req.targetPeriod` 与 `ScheduleItemDTO.period()` 忽略大小写相等时，才把 `newActivityPreference` 应用于该日程项；
- 非目标时段保留原时段、开始/结束时间、景点和活动内容；
- 城市、日期、预算未显式传入时继承原计划；
- 原计划冲突字段在再次检测前保留，随后由新的检测结果替换。
- 事实来源：`PlanModifyService.java`、`PlanModifyRequest.java`。

示例：请求 `targetPeriod=AFTERNOON`、`newActivityPreference=INDOOR` 时，只调整下午日程；上午和晚间不应被重新编排。

### 5.3 修改后再次检测

确认修改后，`PlanSmokeController` 会：

1. 调用 `modifyService.apply(...)` 得到新 `TravelPlanDTO`；
2. 调用 `conflictService.detect(req.weather, modified)` 再次检测；
3. 调用 `hasBlockingConflict(conflicts)` 计算新的 `needConfirm`；
4. 返回新的 `PlanResult`，其中包含修改后的计划、新的冲突列表和新的确认状态。

事实来源：`PlanSmokeController.java`、`PlanResult.java`。

### 5.4 代码边界与文档差异

业务要求是“仅改目标时段”，但当前 `PlanModifyService` 没有强制校验 `targetPeriod` 必填：

- 当 `targetPeriod != null` 时，只修改匹配的时段，符合局部修改要求；
- 当 `targetPeriod == null` 且 `newActivityPreference != null` 时，代码会把新偏好应用到所有日程项。

这是当前实现与严格“仅改目标时段”规则之间的边界差异。调用方必须传入明确的 `targetPeriod`；后续若要把该规则变成代码强约束，应在 `PlanModifyService.apply(...)` 或 Controller 层增加参数校验。

事实来源：`PlanModifyService.java`、`PlanModifyRequest.java`、`PlanSmokeController.java`。

## 6. 数据缺失处理

### 6.1 `weather == null`

- `WeatherConflictService.detect(null, plan)` 直接产生一条：
  - 类型：`DATA_INCOMPLETE`
  - 级别：`WARN`
  - 时段：`ALL_DAY`
  - 说明：未获取到该日期天气数据，无法判断天气风险。
- 该分支直接返回，不再执行日级或时段级天气风险判断。
- 事实来源：`WeatherConflictService.java`。

### 6.2 关键字段为 null

当前代码检查以下 5 个字段：

1. `maxTemp`（最高温）
2. `minTemp`（最低温）
3. `precipitationProbability`（降水概率）
4. `windSpeed`（风速）
5. `weatherCode`（天气代码）

只要其中任一字段为 null，就会生成一条 `DATA_INCOMPLETE` / `WARN`，消息中列出所有缺失项。

事实来源：`WeatherConflictService.java` 的 `checkMissingFields(...)`。

### 6.3 不得默认安全

- null 不得按 `0` 处理；
- null 不得按晴天处理；
- null 不得按“无降雨、无大风、无雷暴”处理；
- 无法判断时，必须明确说明数据不足，并置 `needConfirm=true`。
- 事实来源：`WeatherConflictService.java`、`ConflictSeverity.java`、`PlanResult.java`、`docs/冲突解决与语音接口交付.md` 第 2.3 节。

### 6.4 额外代码现状

当前 `checkMissingFields(...)` 的缺失字段清单不包含 `precipitation`（降水量）。这意味着降水量字段为 null、而降水概率低于阈值时，代码不会仅因降水量缺失追加 `DATA_INCOMPLETE`。本文不把 `precipitation == null` 写成“无降雨风险”；该字段应按未知处理，不能据此得出天气良好的结论。

事实来源：`WeatherConflictService.java`。

## 7. 阈值唯一来源与同步要求

- 数值阈值集中在 `WeatherConflictProperties`：
  - `highTemp=35.0`
  - `lowTemp=5.0`
  - `highWindSpeed=30.0`
  - `rainProbability=50`
  - `rainAmount=0.0`
- `application.properties` 当前配置与上述默认值一致。
- WMO 雷暴代码集合 `95、96、99` 定义在 `WeatherConflictService`，属于代码常量，不在可外部覆盖的属性中。
- 任何阈值变更必须同步：
  1. `WeatherConflictProperties.java`
  2. `backend/src/main/resources/application.properties`
  3. 本文档
  4. 相关业务实现和测试

事实来源：`WeatherConflictProperties.java`、`WeatherConflictService.java`、`backend/src/main/resources/application.properties`。

## 8. 一致性与差异记录

| 项目 | 当前结论 | 事实来源 |
|---|---|---|
| 高温/低温/大风/降雨/雷暴阈值 | 与代码一致：35/5/30/50/95,96,99 | `WeatherConflictProperties.java`、`WeatherConflictService.java` |
| `needConfirm` | 存在 HIGH 或 WARN 时为 true；只有 INFO 或空列表为 false | `WeatherConflictService.java`、`PlanSmokeController.java`、`PlanResult.java` |
| 未确认不改 | `confirmConflicts=false` 时不调用 `apply(...)`，只返回冲突提示 | `PlanSmokeController.java` |
| 仅改目标时段 | 传入目标时段时成立；`targetPeriod` 为空时当前实现会对全部日程应用新偏好 | `PlanModifyService.java` |
| 数据缺失不默认安全 | `weather==null` 和 5 个关键字段缺失均产生 `DATA_INCOMPLETE` / `WARN` | `WeatherConflictService.java` |
| 缺失字段清单 | 不包含 `precipitation`；降水量未知不能解释为无降雨风险 | `WeatherConflictService.java` |
| 指定集成文档 | `docs/integration/04-weather-conflict.md` 未找到，已使用现有冲突交付文档和代码替代 | 文件系统核对 |