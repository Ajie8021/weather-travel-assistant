# 对象关系映射设计
ORM指对象关系映射。建议下一阶段采用Spring Data JPA或等价持久化方案；当前仓库没有这些Entity和Repository，本文不宣称已落地。

|拟新增实体|数据库表|业务来源|
|---|---|---|
|AppUserEntity|app_user|用户|
|ChatSessionEntity|chat_session|会话|
|ChatMessageEntity|chat_message|消息|
|WeatherQueryEntity|weather_query|天气查询|
|TravelPlanEntity|travel_plan|行程|
|AttractionEntity|attraction|景点缓存|
|ScheduleItemEntity|schedule_item|日程项|
|RoutePlanEntity|route_plan|路线|
|ConflictRecordEntity|conflict_record|风险记录|
|PlanRevisionEntity|plan_revision|修改记录|
|AttractionFavoriteEntity|attraction_favorite|收藏|
|UserFeedbackEntity|user_feedback|反馈|
|ApiCallLogEntity|api_call_log|调用日志|

实体与DTO分离：WeatherData、TravelPlan、TravelPlanDTO、AttractionDTO、RouteDTO仍是接口数据结构，不能直接当数据库实体。
ChatSessionStore.Session映射会话状态与消息；其lastPlan是基础TravelPlan，state_snapshot须保存schemaVersion与planType，避免误反序列化为TravelPlanDTO。
JPA实现建议：数值主键IDENTITY，session主键由应用分配；多对一默认延迟加载；不对用户或景点设置级联删除；travel_plan.version与chat_session.version使用Version乐观锁。
JSON字段需由项目实际采用的Hibernate版本进行映射验证，不应把本文片段当已编译代码。

保存行程时，在同一事务保存主表、日程和路线；修改时校验from_version及确认标志，保存revision、更新目标日程与版本、写新冲突结果，失败整体回滚。
实体不直接序列化给前端，避免循环引用与越权字段泄露。匿名会话可以查询；收藏和个人历史需要已认证用户绑定，sessionId不是认证凭据。
