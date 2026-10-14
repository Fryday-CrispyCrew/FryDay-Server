-- 그룹 그래픽 코드 컬럼 추가 (2026-09-28)
--
-- prod는 ddl-auto: validate 이므로 컬럼이 없으면 애플리케이션이 기동되지 않는다.
-- 배포 전에 이 스크립트를 운영 DB에 먼저 실행해야 한다.
-- dev는 ddl-auto: update 라 앱 기동 시 자동 추가되지만, NOT NULL 컬럼이라
-- 기존 행이 있으면 실패할 수 있으므로 dev 에서도 이 스크립트를 먼저 실행한다.
--
-- 앱은 이 코드로 그룹 그래픽 파일을 고른다. 신규 그룹은 생성 시 01/02/03 중
-- 하나를 무작위로 배정받고, 이미 만들어진 그룹은 ②에서 무작위로 채운다.
--
-- 위에서부터 한 문장씩 실행한다.

-- ① 현재 상태 확인
SELECT COUNT(*)                                        AS total_groups,
       SUM(image_code IS NULL)                         AS missing_image_code
FROM fry_group;


-- ② 컬럼 추가 → 기존 그룹 무작위 배정 → NOT NULL 확정
-- 세 문장을 순서대로 실행한다. 중간에 멈추면 기존 행이 NULL 로 남는다.
ALTER TABLE fry_group
    ADD COLUMN image_code VARCHAR(2) NULL AFTER owner_id;

-- 빈 문자열도 함께 채운다. 이 스크립트보다 앱이 먼저 떠서 ddl-auto: update 가
-- 컬럼을 만들면, 기존 행은 NULL 이 아니라 '' 로 채워지기 때문이다.
UPDATE fry_group
SET image_code = LPAD(FLOOR(RAND() * 3) + 1, 2, '0')
WHERE image_code IS NULL
   OR image_code = '';

ALTER TABLE fry_group
    MODIFY COLUMN image_code VARCHAR(2) NOT NULL;


-- ③ 적용 결과 확인
-- image_code 가 NOT NULL 이고, 배정된 값이 01/02/03 뿐이어야 한다.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'fry_group'
  AND column_name = 'image_code';

SELECT image_code, COUNT(*) AS groups
FROM fry_group
GROUP BY image_code
ORDER BY image_code;
