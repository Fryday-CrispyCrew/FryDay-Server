-- 반복 회차는 규칙당 날짜당 1행이어야 한다. V10 의 유니크 제약을 걸기 전에 남아 있는 중복을 정리한다.
-- 운영 중복은 2026-03~04 옛 반복 모델(삭제를 별도 예외 테이블로 표시하던 방식)에서 규칙 수정 때 생겼고,
-- 확인 시점(2026-11-21) 기준 30그룹 46행 모두 이미 삭제 표시된 행이다.
-- 운영 데이터를 지우지 않으려고, 1행만 남기고 나머지는 반복 연결(recurrence_id)만 끊어 일반 투두로 둔다.
-- 남길 행 우선순위: 삭제 안 됨 > 완료 > 개별 수정 > 알림 있음 > 가장 작은 id. 다시 실행해도 결과가 같다.
UPDATE todo t
    JOIN (SELECT id
          FROM (SELECT t.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY t.recurrence_id, t.date
                           ORDER BY t.deleted_at IS NULL DESC,
                               t.status = 'COMPLETED' DESC,
                               t.is_overridden DESC,
                               ta.id IS NOT NULL DESC,
                               t.id) AS rn
                FROM todo t
                         LEFT JOIN todo_alarms ta ON ta.todo_id = t.id
                WHERE t.recurrence_id IS NOT NULL) ranked
          WHERE rn > 1) dup ON dup.id = t.id
SET t.recurrence_id = NULL;
