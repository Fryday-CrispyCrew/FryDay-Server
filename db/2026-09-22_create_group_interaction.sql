-- 그룹 상호작용 기록 테이블 생성 (2026-09-22)
--
-- 똑똑똑 / 주문이요 / 맛있어요 / 박수 상호작용의 발송 기록이다.
-- 30초 쿨다운 판정은 Redis 가 하고, 이 테이블은 이력만 남긴다.
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
  AND table_name IN ('group_interaction');


-- ② 테이블 생성

-- 그룹 상호작용 발송 기록. 서비스 지표와 실험 분석에 쓰도록 1년간 보관하고, 1년이 지난 기록은 매일 00:55 스케줄러가 지운다.
CREATE TABLE group_interaction
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    group_id         BIGINT      NOT NULL,
    sender_id        BIGINT      NOT NULL,
    target_id        BIGINT      NOT NULL,
    interaction_type VARCHAR(30) NOT NULL,
    created_at       DATETIME(6) NULL,
    updated_at       DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 1년 지난 기록 정리가 풀스캔이 되지 않도록 둔다.
    KEY idx_group_interaction_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ③ 통계 갱신
ANALYZE TABLE group_interaction;


-- ④ 적용 결과 확인
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('group_interaction')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
