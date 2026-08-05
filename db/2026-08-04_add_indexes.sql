-- 조회 인덱스 추가 (2026-08-04)
--
-- prod는 ddl-auto: validate 이고 Hibernate의 validate는 인덱스를 검증하지 않는다.
-- 따라서 엔티티의 @Index 선언과 별개로 운영 DB에는 이 스크립트를 직접 실행해야 한다.
-- dev는 ddl-auto: update 라 앱 기동 시 자동 생성된다.
--
-- 위에서부터 한 문장씩 실행한다.
-- MySQL은 ADD INDEX IF NOT EXISTS를 지원하지 않으므로, 재실행할 때는
-- ①의 결과를 보고 없는 인덱스만 골라서 실행할 것.
-- 이미 있는 인덱스에 실행하면 ERROR 1061이 나지만 데이터에는 영향이 없다.

-- ① 현재 인덱스 확인
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('todo', 'category', 'recurrence')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;


-- ② 인덱스 생성
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


-- ③ 통계 갱신
ANALYZE TABLE todo, category, recurrence;


-- ④ 적용 결과 확인 (①과 동일한 쿼리)
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('todo', 'category', 'recurrence')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
