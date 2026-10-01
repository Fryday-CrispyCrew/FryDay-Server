-- 그룹 기능 테이블. 운영 DB에 아직 적용되지 않은 db/2026-09-15 ~ 09-28 수동 스크립트를 최종 형태로 합쳤다.
-- 테이블명 주의: group / groups 는 MySQL 예약어라 그룹 테이블은 fry_group 을 쓴다.

-- 그룹. 이름은 중복 가능하며, 초대 코드로 그룹을 구분한다.
CREATE TABLE fry_group
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(10)  NOT NULL,
    invite_code VARCHAR(6)   NOT NULL,
    owner_id    BIGINT       NOT NULL,
    image_code  VARCHAR(2)   NOT NULL,
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
