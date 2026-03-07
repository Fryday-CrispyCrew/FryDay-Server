-- 기존 데이터 삭제
DELETE FROM todo;
DELETE FROM category;
DELETE FROM users;

-- 테스트용 사용자
INSERT INTO users (id, provider, provider_user_id, email, nickname, onboarding_status, account_status, role, created_at, updated_at)
VALUES (1, 'KAKAO', 'test_user_001', 'test@example.com', 'TestUser', 'COMPLETED', 'ACTIVE', 'USER', NOW(), NOW());

-- 카테고리 3개
INSERT INTO category (id, name, color, user_id, display_order, created_at, updated_at) VALUES
(1, '운동', 'OR', 1, 1, NOW(), NOW()),
(2, '공부', 'CB', 1, 2, NOW(), NOW()),
(3, '독서', 'LG', 1, 3, NOW(), NOW());

-- Todo 10개 (2025년 1월, COMPLETED 7개, IN_PROGRESS 3개 = 70% 달성률)
INSERT INTO todo (id, description, status, category_id, date, display_order, created_at, updated_at) VALUES
(1, '운동 1', 'COMPLETED', 1, '2025-01-01', 1, NOW(), NOW()),
(2, '운동 2', 'COMPLETED', 1, '2025-01-02', 1, NOW(), NOW()),
(3, '운동 3', 'IN_PROGRESS', 1, '2025-01-03', 1, NOW(), NOW()),
(4, '공부 1', 'COMPLETED', 2, '2025-01-01', 2, NOW(), NOW()),
(5, '공부 2', 'COMPLETED', 2, '2025-01-02', 2, NOW(), NOW()),
(6, '공부 3', 'IN_PROGRESS', 2, '2025-01-03', 2, NOW(), NOW()),
(7, '독서 1', 'COMPLETED', 3, '2025-01-01', 3, NOW(), NOW()),
(8, '독서 2', 'COMPLETED', 3, '2025-01-02', 3, NOW(), NOW()),
(9, '독서 3', 'COMPLETED', 3, '2025-01-03', 3, NOW(), NOW()),
(10, '독서 4', 'IN_PROGRESS', 3, '2025-01-04', 3, NOW(), NOW());
