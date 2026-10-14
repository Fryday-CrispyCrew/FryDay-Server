-- 운영 DB(fryday_prod) 스키마 기준선. 2026-09-30 mysqldump --no-data 결과에서 CREATE TABLE 만 남겼다.
-- 이미 테이블이 있는 DB 에서는 baseline 으로 건너뛰고, 빈 DB 에서만 실행된다.
-- 외래 키가 걸린 테이블보다 참조 대상 테이블을 먼저 만든다.

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `account_status` enum('ACTIVE','BLOCKED','WITHDRAWN') NOT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `onboarding_status` enum('COMPLETED','NEEDS_AGREEMENT','NEEDS_MARKETING','NEEDS_NICKNAME','NEEDS_ONBOARDING') NOT NULL,
  `provider` enum('APPLE','KAKAO','NAVER') NOT NULL,
  `provider_user_id` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `withdrawn_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `color` enum('BR','CB','DP','LG','MB','MT','OR','PK','VL') NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `display_order` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_category_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recurrence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category_id` bigint NOT NULL,
  `description` varchar(255) NOT NULL,
  `end_date` date DEFAULT NULL,
  `frequency_values` varchar(255) DEFAULT NULL,
  `last_generated_date` date NOT NULL,
  `memo` varchar(300) DEFAULT NULL,
  `notification_time` time(6) DEFAULT NULL,
  `start_date` date NOT NULL,
  `type` enum('DAILY','MONTHLY','WEEKLY','YEARLY') NOT NULL,
  `user_id` bigint NOT NULL,
  `end_type` varchar(10) NOT NULL DEFAULT 'NONE',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_recurrence_user` (`user_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recurrence_exception` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `detached_todo_id` bigint DEFAULT NULL,
  `occurrence_date` date NOT NULL,
  `recurrence_id` bigint NOT NULL,
  `type` enum('DELETED','DETACHED','MOVED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmwfxh5kdw1cuwnu146kjxghyx` (`recurrence_id`,`occurrence_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `todo` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `date` date NOT NULL,
  `deleted_at` date DEFAULT NULL,
  `description` varchar(255) NOT NULL,
  `display_order` bigint NOT NULL,
  `memo` varchar(300) DEFAULT NULL,
  `recurrence_id` bigint DEFAULT NULL,
  `status` enum('COMPLETED','IN_PROGRESS') NOT NULL,
  `category_id` bigint NOT NULL,
  `override_title` varchar(255) DEFAULT NULL,
  `override_memo` varchar(300) DEFAULT NULL,
  `override_is_alarm` tinyint(1) DEFAULT NULL,
  `override_alarm_time` time DEFAULT NULL,
  `is_overridden` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_todo_category_date` (`category_id`,`date`,`deleted_at`,`display_order`),
  KEY `idx_todo_recurrence_date` (`recurrence_id`,`date`),
  CONSTRAINT `FKeh6uro943emclp6kr1d4wysmo` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `todo_alarms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `fail_count` int NOT NULL,
  `notify_at` datetime(6) NOT NULL,
  `status` enum('FAILED','PENDING','SENT') NOT NULL,
  `version` bigint DEFAULT NULL,
  `todo_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjpkgjw1s52k7h4sm4dqa0h4v` (`todo_id`),
  KEY `FKmobshmo13p8nmdgkbrgigsn2w` (`user_id`),
  CONSTRAINT `FKe1ue92savplmdl7gsl9px83bt` FOREIGN KEY (`todo_id`) REFERENCES `todo` (`id`),
  CONSTRAINT `FKmobshmo13p8nmdgkbrgigsn2w` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `agreements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `marketing_agreed` bit(1) NOT NULL,
  `privacy_agreed` bit(1) NOT NULL,
  `user_id` bigint NOT NULL,
  `terms_agreed` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKldrabawg21gcyrx28dq5m79c5` (`user_id`),
  CONSTRAINT `FKl6ykxb4ojx5hvaqcoqjlrx4lv` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `daily_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `bowl_type` enum('BURNT','EMPTY','FULL','LESS','MORE') NOT NULL,
  `date` date NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `notice_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_devices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `device_id` varchar(100) NOT NULL,
  `device_name` varchar(100) DEFAULT NULL,
  `device_type` varchar(20) DEFAULT NULL,
  `fcm_token` varchar(500) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `push_notification_agreed` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbqrla8oqjhluy37oq2kltrcw` (`user_id`,`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
