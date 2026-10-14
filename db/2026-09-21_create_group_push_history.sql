-- 그룹 푸시 발송 기록 테이블 생성 (2026-09-21)
--
-- 튀기기 시작 / 영업종료 알림을 그룹원마다 하루 한 번만 보내기 위한 기록이다.
-- prod는 ddl-auto: validate 이므로 테이블이 없으면 애플리케이션이 기동되지 않는다.
-- 배포 전에 이 스크립트를 운영 DB에 먼저 실행해야 한다.
-- dev는 ddl-auto: update 라 앱 기동 시 자동 생성된다.
--
-- 위에서부터 한 문장씩 실행한다.
-- 이미 있는 테이블에 실행하면 ERROR 1050 이 나지만 데이터에는 영향이 없다.

-- ① 현재 상태 확인
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('group_push_history');


-- ② 테이블 생성

-- 하루 한 번만 보내는 그룹 알림의 발송 기록. 30일이 지난 기록은 매일 00:50 스케줄러가 지운다.
CREATE TABLE group_push_history
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    push_date  DATE        NOT NULL,
    push_type  VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 같은 날 같은 그룹원에게 같은 알림이 두 번 기록되지 않게 막는다.
    UNIQUE KEY uk_group_push_history (group_id, user_id, push_date, push_type),
    -- 발송 여부 판단 시 사용자의 오늘 기록을 조회한다.
    KEY idx_gph_user_date (user_id, push_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ③ 통계 갱신
ANALYZE TABLE group_push_history;


-- ④ 적용 결과 확인
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('group_push_history')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
