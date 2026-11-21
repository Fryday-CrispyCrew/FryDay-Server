-- 반복 회차를 규칙당 날짜당 1행으로 DB 가 보장한다. 삭제된 회차 행도 포함한다(삭제한 회차를 다시 만들지 않는 기준).
-- 일반 투두는 recurrence_id 가 NULL 이라 걸리지 않는다.
-- 한 문장으로 바꿔서, 실패하면 기존 일반 인덱스가 그대로 남는다.
ALTER TABLE todo
    DROP INDEX idx_todo_recurrence_date,
    ADD UNIQUE INDEX uk_todo_recurrence_date (recurrence_id, date);
