-- 지표 집계에서 빼고 볼 수 있도록 표시한 팀 계정. 활동 기록 원본은 그대로 두고, 집계할 때 이 테이블로 거른다.
-- 탈퇴로 users 행이 지워져도 계속 거를 수 있도록 외래 키를 걸지 않는다.
CREATE TABLE internal_account
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    memo       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NULL,
    updated_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_internal_account_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
