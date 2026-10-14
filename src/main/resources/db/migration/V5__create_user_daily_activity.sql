-- 유저가 그날(Asia/Seoul) 인증된 API 요청을 한 번이라도 보냈다는 기록. DAU·리텐션 집계의 원천이다.
-- 탈퇴로 계정이 지워져도 행을 남긴다. 지우면 리텐션이 실제보다 좋게 나온다.
CREATE TABLE user_daily_activity
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    activity_date DATE        NOT NULL,
    created_at    DATETIME(6) NULL,
    updated_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 유저당 하루 한 행만 남긴다. 중복 기록은 INSERT IGNORE 로 흡수한다.
    UNIQUE KEY uk_user_daily_activity (user_id, activity_date),
    -- 날짜별 DAU·WAU 집계용.
    KEY idx_uda_activity_date (activity_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
