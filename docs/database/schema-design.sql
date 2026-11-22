-- MySQL 8.0+ 设计草案。仅供空测试库人工审阅后执行；本任务不执行迁移。
-- 时间统一UTC，字符集utf8mb4。无DROP语句。

-- 用户
CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT AUTO_INCREMENT NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user (username),
  KEY idx_app_user_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 会话
CREATE TABLE IF NOT EXISTS chat_session (
  id VARCHAR(64) NOT NULL,
  user_id BIGINT NULL,
  device_id VARCHAR(64) NULL,
  confirmed_city JSON NULL,
  pending_cities JSON NULL,
  state_snapshot JSON NULL,
  last_active_at DATETIME(3) NOT NULL,
  version INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_chat_session_user_id FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT AUTO_INCREMENT NOT NULL,
  session_id VARCHAR(64) NOT NULL,
  sequence_no INT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  tool_calls JSON NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_chat_message_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE RESTRICT,
  UNIQUE KEY uk_chat_message (session_id, sequence_no),
  KEY idx_chat_message_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 天气查询
CREATE TABLE IF NOT EXISTS weather_query (
  id BIGINT AUTO_INCREMENT NOT NULL,
  session_id VARCHAR(64) NULL,
  city VARCHAR(128) NOT NULL,
  forecast_date DATE NOT NULL,
  latitude DECIMAL(10,6) NOT NULL,
  longitude DECIMAL(10,6) NOT NULL,
  weather_snapshot JSON NOT NULL,
  source VARCHAR(32) NOT NULL,
  queried_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_weather_query_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 行程
CREATE TABLE IF NOT EXISTS travel_plan (
  id BIGINT AUTO_INCREMENT NOT NULL,
  session_id VARCHAR(64) NULL,
  user_id BIGINT NULL,
  city VARCHAR(128) NOT NULL,
  travel_date DATE NOT NULL,
  preference VARCHAR(128) NOT NULL,
  budget DECIMAL(10,2) NULL,
  transport_mode VARCHAR(32) NULL,
  version INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_travel_plan_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE RESTRICT,
  CONSTRAINT fk_travel_plan_user_id FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  KEY idx_travel_plan_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 景点缓存
CREATE TABLE IF NOT EXISTS attraction (
  id BIGINT AUTO_INCREMENT NOT NULL,
  provider VARCHAR(32) NOT NULL,
  poi_id VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  city VARCHAR(128) NOT NULL,
  address VARCHAR(512) NULL,
  longitude DECIMAL(10,6) NOT NULL,
  latitude DECIMAL(10,6) NOT NULL,
  category VARCHAR(255) NULL,
  rating DECIMAL(4,2) NULL,
  opening_hours VARCHAR(512) NULL,
  expires_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attraction (provider, poi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 日程项
CREATE TABLE IF NOT EXISTS schedule_item (
  id BIGINT AUTO_INCREMENT NOT NULL,
  plan_id BIGINT NOT NULL,
  attraction_id BIGINT NULL,
  period VARCHAR(16) NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  activity TEXT NOT NULL,
  attraction_snapshot JSON NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_schedule_item_plan_id FOREIGN KEY (plan_id) REFERENCES travel_plan(id) ON DELETE RESTRICT,
  CONSTRAINT fk_schedule_item_attraction_id FOREIGN KEY (attraction_id) REFERENCES attraction(id) ON DELETE RESTRICT,
  UNIQUE KEY uk_schedule_item (plan_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 路线
CREATE TABLE IF NOT EXISTS route_plan (
  id BIGINT AUTO_INCREMENT NOT NULL,
  plan_id BIGINT NULL,
  origin_id BIGINT NOT NULL,
  destination_id BIGINT NOT NULL,
  transport_mode VARCHAR(32) NOT NULL,
  distance_meters DECIMAL(12,2) NULL,
  duration_seconds INT NULL,
  segments JSON NULL,
  source VARCHAR(32) NOT NULL,
  queried_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_route_plan_plan_id FOREIGN KEY (plan_id) REFERENCES travel_plan(id) ON DELETE RESTRICT,
  CONSTRAINT fk_route_plan_origin_id FOREIGN KEY (origin_id) REFERENCES attraction(id) ON DELETE RESTRICT,
  CONSTRAINT fk_route_plan_destination_id FOREIGN KEY (destination_id) REFERENCES attraction(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 风险记录
CREATE TABLE IF NOT EXISTS conflict_record (
  id BIGINT AUTO_INCREMENT NOT NULL,
  plan_id BIGINT NOT NULL,
  plan_version INT NOT NULL,
  type VARCHAR(48) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  message TEXT NOT NULL,
  detected_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_conflict_record_plan_id FOREIGN KEY (plan_id) REFERENCES travel_plan(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 修改记录
CREATE TABLE IF NOT EXISTS plan_revision (
  id BIGINT AUTO_INCREMENT NOT NULL,
  plan_id BIGINT NOT NULL,
  from_version INT NOT NULL,
  target_period VARCHAR(16) NULL,
  confirmed BOOLEAN NOT NULL,
  before_snapshot JSON NOT NULL,
  after_snapshot JSON NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_plan_revision_plan_id FOREIGN KEY (plan_id) REFERENCES travel_plan(id) ON DELETE RESTRICT,
  UNIQUE KEY uk_plan_revision (plan_id, from_version),
  KEY idx_plan_revision_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收藏
CREATE TABLE IF NOT EXISTS attraction_favorite (
  id BIGINT AUTO_INCREMENT NOT NULL,
  user_id BIGINT NOT NULL,
  attraction_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_attraction_favorite_user_id FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_attraction_favorite_attraction_id FOREIGN KEY (attraction_id) REFERENCES attraction(id) ON DELETE RESTRICT,
  UNIQUE KEY uk_attraction_favorite (user_id, attraction_id),
  KEY idx_attraction_favorite_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 反馈
CREATE TABLE IF NOT EXISTS user_feedback (
  id BIGINT AUTO_INCREMENT NOT NULL,
  user_id BIGINT NULL,
  content TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_user_feedback_user_id FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  KEY idx_user_feedback_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 调用日志
CREATE TABLE IF NOT EXISTS api_call_log (
  id BIGINT AUTO_INCREMENT NOT NULL,
  session_id VARCHAR(64) NULL,
  provider VARCHAR(32) NOT NULL,
  operation VARCHAR(64) NOT NULL,
  http_status INT NULL,
  duration_ms INT NULL,
  error_code VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_api_call_log_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE RESTRICT,
  KEY idx_api_call_log_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
