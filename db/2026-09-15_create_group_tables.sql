-- 그룹 기능 테이블 생성 (2026-09-15)
--
-- prod는 ddl-auto: validate 이므로 테이블이 없으면 애플리케이션이 기동되지 않는다.
-- 배포 전에 이 스크립트를 운영 DB에 먼저 실행해야 한다.
-- dev는 ddl-auto: update 라 앱 기동 시 자동 생성된다.
--
-- 테이블명 주의: group / groups 는 MySQL 예약어라 그룹 테이블은 fry_group 을 쓴다.
--
-- 위에서부터 한 문장씩 실행한다.
-- 이미 있는 테이블에 실행하면 ERROR 1050 이 나지만 데이터에는 영향이 없다.

-- ① 현재 상태 확인
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('fry_group', 'group_member', 'group_public_category');


-- ② 테이블 생성

-- 그룹. 이름은 중복 가능하며, 초대 코드로 그룹을 구분한다.
CREATE TABLE fry_group
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(10)  NOT NULL,
    invite_code VARCHAR(6)   NOT NULL,
    owner_id    BIGINT       NOT NULL,
    created_at  DATETIME(6)  NULL,
    updated_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fry_group_invite_code (invite_code),
    KEY idx_fry_group_owner (owner_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 그룹 참여 정보. 그룹장 여부는 fry_group.owner_id 가 단일 소스이므로 role 컬럼을 두지 않는다.
-- 참여 순서는 created_at 오름차순을 사용한다.
CREATE TABLE group_member
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    group_id             BIGINT      NOT NULL,
    user_id              BIGINT      NOT NULL,
    notification_enabled TINYINT(1)  NOT NULL DEFAULT 1,
    created_at           DATETIME(6) NULL,
    updated_at           DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_member (group_id, user_id),
    KEY idx_group_member_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 사용자가 특정 그룹에 공개한 카테고리. 공개 설정은 그룹마다 독립적으로 관리한다.
CREATE TABLE group_public_category
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    group_id    BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL,
    created_at  DATETIME(6) NULL,
    updated_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- (group_id), (group_id, user_id) 조회는 아래 unique 인덱스가 커버한다.
    UNIQUE KEY uk_group_public_category (group_id, user_id, category_id),
    -- 회원 탈퇴(user_id 기준) / 카테고리 삭제(category_id 기준) 삭제가 풀스캔이 되지 않도록 따로 둔다.
    KEY idx_gpc_user (user_id),
    KEY idx_gpc_category (category_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ③ 통계 갱신
ANALYZE TABLE fry_group, group_member, group_public_category;


-- ④ 적용 결과 확인
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('fry_group', 'group_member', 'group_public_category')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
