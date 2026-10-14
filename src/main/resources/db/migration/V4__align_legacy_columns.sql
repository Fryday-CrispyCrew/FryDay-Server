-- 예전 ddl-auto 와 수동 변경으로 엔티티 매핑과 어긋나 있던 운영 컬럼을 맞춘다.

-- 약관 동의 통합(c1992d7)으로 없어진 값. 운영에 이 값을 가진 유저가 없음을 확인했다 (2026-09-30).
ALTER TABLE users
    MODIFY COLUMN onboarding_status ENUM ('COMPLETED', 'NEEDS_AGREEMENT', 'NEEDS_NICKNAME', 'NEEDS_ONBOARDING') NOT NULL;

-- 다른 테이블과 같이 auditing 컬럼은 NULL 을 허용한다.
ALTER TABLE notice
    MODIFY COLUMN created_at DATETIME(6) NULL,
    MODIFY COLUMN updated_at DATETIME(6) NULL;
