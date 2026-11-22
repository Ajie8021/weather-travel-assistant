# 关系模型与字段字典

所有表均为下一阶段持久化设计，尚未接入现有业务。类型采用MySQL 8.0+；id为主键，数值id自增；未写NULL的字段必填。默认值由应用显式提供，version初始0，预算不得小于0，角色/状态由应用枚举校验。UUID会话ID兼容现有外部sessionId。

## app_user（用户）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|username|VARCHAR(64)|唯一登录名|
|password_hash|VARCHAR(255)|密码哈希|
|role|VARCHAR(16)|USER或ADMIN|
|created_at|DATETIME(3)|创建时间|

## chat_session（会话）

|字段|类型|含义|
|---|---|---|
|id|VARCHAR(64)|主键，会话标识|
|user_id|BIGINT NULL|外键app_user，可匿名|
|device_id|VARCHAR(64) NULL|硬件来源标识|
|confirmed_city|JSON NULL|已确认地点快照|
|pending_cities|JSON NULL|待确认候选|
|state_snapshot|JSON NULL|上次天气、请求及计划上下文|
|last_active_at|DATETIME(3)|最后活动时间|
|version|INT|乐观锁版本|

## chat_message（消息）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|session_id|VARCHAR(64)|外键chat_session|
|sequence_no|INT|会话内序号|
|role|VARCHAR(16)|user/assistant/tool|
|content|TEXT|消息内容|
|tool_calls|JSON NULL|工具调用结构|
|created_at|DATETIME(3)|时间|

## weather_query（天气查询）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|session_id|VARCHAR(64) NULL|外键chat_session|
|city|VARCHAR(128)|确认城市|
|forecast_date|DATE|预报日期|
|latitude|DECIMAL(10,6)|纬度|
|longitude|DECIMAL(10,6)|经度|
|weather_snapshot|JSON|天气DTO快照，保留null|
|source|VARCHAR(32)|数据来源|
|queried_at|DATETIME(3)|查询时间|

## travel_plan（行程）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|session_id|VARCHAR(64) NULL|外键chat_session|
|user_id|BIGINT NULL|外键app_user|
|city|VARCHAR(128)|城市|
|travel_date|DATE|出行日期|
|preference|VARCHAR(128)|活动偏好|
|budget|DECIMAL(10,2) NULL|预算，非实际报价|
|transport_mode|VARCHAR(32) NULL|交通偏好|
|version|INT|乐观锁|
|created_at|DATETIME(3)|创建时间|

## attraction（景点缓存）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|provider|VARCHAR(32)|AMAP|
|poi_id|VARCHAR(64)|提供方地点ID|
|name|VARCHAR(255)|名称|
|city|VARCHAR(128)|城市|
|address|VARCHAR(512) NULL|地址|
|longitude|DECIMAL(10,6)|经度|
|latitude|DECIMAL(10,6)|纬度|
|category|VARCHAR(255) NULL|分类|
|rating|DECIMAL(4,2) NULL|上游评分|
|opening_hours|VARCHAR(512) NULL|原始开放时间|
|expires_at|DATETIME(3)|缓存到期时间|

## schedule_item（日程项）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|plan_id|BIGINT|外键travel_plan|
|attraction_id|BIGINT NULL|外键attraction|
|period|VARCHAR(16)|MORNING/AFTERNOON/EVENING|
|start_time|TIME|开始时间|
|end_time|TIME|结束时间|
|activity|TEXT|活动说明|
|attraction_snapshot|JSON NULL|历史地点快照|

## route_plan（路线）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|plan_id|BIGINT NULL|外键travel_plan，独立查询可空|
|origin_id|BIGINT|外键attraction|
|destination_id|BIGINT|外键attraction|
|transport_mode|VARCHAR(32)|交通方式|
|distance_meters|DECIMAL(12,2) NULL|距离米|
|duration_seconds|INT NULL|耗时秒|
|segments|JSON NULL|分段结构|
|source|VARCHAR(32)|AMAP|
|queried_at|DATETIME(3)|查询时间|

## conflict_record（风险记录）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|plan_id|BIGINT|外键travel_plan|
|plan_version|INT|检测时计划版本|
|type|VARCHAR(48)|冲突类型|
|severity|VARCHAR(16)|WARN/HIGH|
|message|TEXT|风险原因|
|detected_at|DATETIME(3)|检测时间|

## plan_revision（修改记录）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|plan_id|BIGINT|外键travel_plan|
|from_version|INT|修改前版本|
|target_period|VARCHAR(16) NULL|目标时段|
|confirmed|BOOLEAN|是否用户确认|
|before_snapshot|JSON|修改前快照|
|after_snapshot|JSON|修改后快照|
|created_at|DATETIME(3)|修改时间|

## attraction_favorite（收藏）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|user_id|BIGINT|外键app_user|
|attraction_id|BIGINT|外键attraction|
|created_at|DATETIME(3)|收藏时间|

## user_feedback（反馈）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|user_id|BIGINT NULL|外键app_user|
|content|TEXT|反馈内容|
|status|VARCHAR(16)|OPEN/RESOLVED|
|created_at|DATETIME(3)|提交时间|

## api_call_log（调用日志）

|字段|类型|含义|
|---|---|---|
|id|BIGINT|主键|
|session_id|VARCHAR(64) NULL|外键chat_session|
|provider|VARCHAR(32)|外部服务|
|operation|VARCHAR(64)|操作名称|
|http_status|INT NULL|上游HTTP状态|
|duration_ms|INT NULL|耗时毫秒|
|error_code|VARCHAR(64) NULL|脱敏错误码|
|created_at|DATETIME(3)|调用时间|
