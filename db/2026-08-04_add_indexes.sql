-- 조회 인덱스 추가 (2026-08-04)
--
-- prod는 ddl-auto: validate 이고 Hibernate의 validate는 인덱스를 검증하지 않는다.
-- 따라서 엔티티의 @Index 선언과 별개로 운영 DB에는 이 스크립트를 직접 실행해야 한다.
-- dev는 ddl-auto: update 라 앱 기동 시 자동 생성된다.
--
-- 운영 중 무중단 적용을 위해 ALGORITHM=INPLACE, LOCK=NONE 으로 온라인 DDL을 강제한다.
-- 지원되지 않는 조건이면 즉시 에러가 나므로, 조용히 테이블을 잠그는 상황을 방지할 수 있다.

ALTER TABLE todo
    ADD INDEX idx_todo_category_date (category_id, date, deleted_at, display_order),
    ALGORITHM = INPLACE, LOCK = NONE;

ALTER TABLE todo
    ADD INDEX idx_todo_recurrence_date (recurrence_id, date),
    ALGORITHM = INPLACE, LOCK = NONE;

ALTER TABLE category
    ADD INDEX idx_category_user (user_id),
    ALGORITHM = INPLACE, LOCK = NONE;

ALTER TABLE recurrence
    ADD INDEX idx_recurrence_user (user_id, is_deleted),
    ALGORITHM = INPLACE, LOCK = NONE;

ANALYZE TABLE todo, category, recurrence;

-- 확인용
-- SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) cols
-- FROM information_schema.statistics
-- WHERE table_schema = DATABASE() AND table_name IN ('todo','category','recurrence')
-- GROUP BY table_name, index_name ORDER BY table_name, index_name;
