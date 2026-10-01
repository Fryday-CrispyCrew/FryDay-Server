-- 진행 중인 날 그릇(국자, COOKING) 추가. Hibernate validate 는 enum 값을 검사하지 않아, 이 변경이 없으면 기동은 되고 저장할 때 실패한다.
ALTER TABLE daily_result
    MODIFY COLUMN bowl_type ENUM ('BURNT', 'COOKING', 'EMPTY', 'FULL', 'LESS', 'MORE') NOT NULL;
