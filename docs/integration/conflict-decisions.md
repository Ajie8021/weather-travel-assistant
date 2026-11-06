# 集成冲突处理决定（阶段0）

| 冲突                                                         | 决定                                                         |
| :----------------------------------------------------------- | :----------------------------------------------------------- |
| `TravelPlan` vs `TravelPlanDTO`                              | 两套并存，适配器转换；旧页面用前者，新行程用后者             |
| `activityPreference` vs `preference`                         | `PreferenceMapper` 映射，不改枚举                            |
| 错误响应 HTML vs JSON                                        | 旧接口保持 `errorMessage`；新接口用 `ApiResponse`            |
| `conflicts` 为 null                                          | 统一改为空数组，适配器和冲突服务都保证                       |
| 日期范围                                                     | 统一在编排层用天气模块 15 天规则校验，不各写一套             |
| MCP 工具命名                                                 | 保留已有 6 个，新增 `check_weather_conflicts`（阶段1），不重命名已有工具 |
| `ScheduleItemDTO` 有`startTime/endTime/attraction`，`ItineraryItem `没有 | 转换时时间与 `AttractionDTO` 引用丢弃，不塞进 notes；`ItineraryItem` 只保留 `name/source/verificationStatus` 等真实字段 |
| 冲突模块与 B 的阈值重复                                      | 阈值集中到 `WeatherConflictProperties`，与 B 保持一致，变更需同步 |
| 开放时间冲突语义                                             | 采用严格包含：计划时段必须完全在开放时间内，否则报冲突       |



