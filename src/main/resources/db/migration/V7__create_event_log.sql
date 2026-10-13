-- 현재 상태만 남는 테이블에서는 지나고 나면 알 수 없는 유저 행동을 쌓는다.
-- 탈퇴로 users 행이 지워져도 남도록 외래 키를 걸지 않는다.
CREATE TABLE event_log
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    target_id   BIGINT      NULL,
    properties  JSON        NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_event_log_type_occurred (event_type, occurred_at),
    KEY idx_event_log_user_occurred (user_id, occurred_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
