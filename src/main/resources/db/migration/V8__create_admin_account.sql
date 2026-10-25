-- 어드민 웹 로그인 계정. 앱 유저(users)와 분리한다.
CREATE TABLE admin_account
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(50)  NOT NULL,
    password      VARCHAR(100) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    last_login_at DATETIME(6)  NULL,
    created_at    DATETIME(6)  NULL,
    updated_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_account_login_id (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
