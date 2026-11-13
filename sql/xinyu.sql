-- 心屿 AI 情绪陪伴系统数据库初始化脚本
-- 数据库名需要和 xinyu-backend/src/main/resources/application.properties 中的配置保持一致。
-- 注意：本脚本会先 DROP 再 CREATE，适合初始化环境；已有正式数据时不要直接整份执行。

CREATE DATABASE IF NOT EXISTS `xinyu`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `xinyu`;

-- 先删除依赖表，再删除主表，避免外键约束导致删除失败。
DROP TABLE IF EXISTS `favorite`;
DROP TABLE IF EXISTS `post_like`;
DROP TABLE IF EXISTS `community_reply`;
DROP TABLE IF EXISTS `community_post`;
DROP TABLE IF EXISTS `topic`;
DROP TABLE IF EXISTS `ai_reply`;
DROP TABLE IF EXISTS `mood_record`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码密文，使用 BCrypt 保存',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `gender` VARCHAR(20) DEFAULT NULL COMMENT '性别：MALE/FEMALE/OTHER/UNDISCLOSED',
  `birthday` DATE DEFAULT NULL COMMENT '生日（用于推算年龄）',
  `bio` VARCHAR(300) DEFAULT NULL COMMENT '个性签名',
  `city` VARCHAR(50) DEFAULT NULL COMMENT '所在城市',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `mood_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '心情记录ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `mood_type` VARCHAR(30) NOT NULL COMMENT '心情类型：开心、平静、焦虑、难过、疲惫、其他等',
  `content` TEXT NOT NULL COMMENT '心情内容',
  `ai_reply` TEXT DEFAULT NULL COMMENT 'AI回复内容',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0已删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mood_record_user_status_time` (`user_id`, `status`, `create_time`),
  KEY `idx_mood_record_mood_type` (`mood_type`),
  CONSTRAINT `fk_mood_record_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心情记录表';

CREATE TABLE `ai_reply` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'AI回复ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `target_type` VARCHAR(50) NOT NULL COMMENT '关联业务类型：MOOD_RECORD、POST、SUMMARY',
  `target_id` BIGINT NOT NULL COMMENT '关联业务ID',
  `prompt` TEXT DEFAULT NULL COMMENT '发送给AI的提示词',
  `reply_content` TEXT DEFAULT NULL COMMENT 'AI回复内容',
  `model_name` VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1成功，0失败',
  `error_message` TEXT DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_user_time` (`user_id`, `create_time`),
  KEY `idx_ai_target` (`target_type`, `target_id`),
  KEY `idx_ai_status_time` (`status`, `create_time`),
  CONSTRAINT `fk_ai_reply_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI回复记录表';

CREATE TABLE `topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '话题ID',
  `name` VARCHAR(50) NOT NULL COMMENT '话题名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '话题说明',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_name` (`name`),
  KEY `idx_topic_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区话题表';

CREATE TABLE `community_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '发布用户ID',
  `topic_id` BIGINT NOT NULL COMMENT '所属话题ID',
  `mood_type` VARCHAR(30) NOT NULL COMMENT '帖子心情类型',
  `content` TEXT NOT NULL COMMENT '帖子内容',
  `ai_reply` TEXT DEFAULT NULL COMMENT 'AI 心屿回复内容（最新一次成功回复的快照）',
  `ai_generation_id` VARCHAR(36) DEFAULT NULL COMMENT '当前AI生成标识，NULL表示无有效生成',
  `anonymous_name` VARCHAR(50) NOT NULL DEFAULT '匿名岛民' COMMENT '匿名展示名',
  `reply_count` INT NOT NULL DEFAULT 0 COMMENT '回复数冗余字段',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数冗余字段',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0已删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_topic_status_time` (`topic_id`, `status`, `create_time`),
  KEY `idx_post_user_status_time` (`user_id`, `status`, `create_time`),
  KEY `idx_post_status_hot` (`status`, `like_count`, `create_time`),
  CONSTRAINT `fk_post_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_post_topic`
    FOREIGN KEY (`topic_id`) REFERENCES `topic` (`id`)
    ON DELETE RESTRICT
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区帖子表';

CREATE TABLE `community_reply` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '回复ID',
  `post_id` BIGINT NOT NULL COMMENT '所属帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '回复用户ID',
  `content` TEXT NOT NULL COMMENT '回复内容',
  `anonymous_name` VARCHAR(50) NOT NULL DEFAULT '匿名岛民' COMMENT '匿名展示名',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0已删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_reply_post_status_time` (`post_id`, `status`, `create_time`),
  KEY `idx_reply_user_status_time` (`user_id`, `status`, `create_time`),
  CONSTRAINT `fk_reply_post`
    FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_reply_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区回复表';

CREATE TABLE `post_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
  `post_id` BIGINT NOT NULL COMMENT '帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_like_user` (`post_id`, `user_id`),
  KEY `idx_post_like_user` (`user_id`, `create_time`),
  CONSTRAINT `fk_post_like_post`
    FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_post_like_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';

CREATE TABLE `favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `post_id` BIGINT NOT NULL COMMENT '帖子ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user` (`post_id`, `user_id`),
  KEY `idx_favorite_user` (`user_id`, `create_time`),
  CONSTRAINT `fk_favorite_post`
    FOREIGN KEY (`post_id`) REFERENCES `community_post` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_favorite_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子收藏表';

INSERT INTO `topic` (`name`, `description`, `sort`, `status`) VALUES
  ('学习压力', '学习、考试、作业带来的压力和情绪', 10, 1),
  ('实习焦虑', '实习、面试、就业准备相关的焦虑', 20, 1),
  ('人际关系', '朋友、同学、家人相处中的情绪', 30, 1),
  ('情绪低落', '难过、失落、没有动力的时候', 40, 1),
  ('开心分享', '记录一点点开心和被治愈的时刻', 50, 1),
  ('睡眠问题', '失眠、疲惫、作息紊乱相关讨论', 60, 1);

-- STAGE2_REVIEW_TABLES
CREATE TABLE mood_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    review_type VARCHAR(16) NOT NULL COMMENT 'WEEK / RECENT_30',
    range_start DATE NOT NULL COMMENT '含',
    range_end DATE NOT NULL COMMENT '不含',
    revision INT NOT NULL COMMENT '同一用户、类型、范围内第几次生成，从1开始',
    source_fingerprint CHAR(64) NOT NULL COMMENT '来源记录(id,content_version)集合的SHA-256',
    status VARCHAR(16) NOT NULL COMMENT 'queued/running/succeeded/failed',
    stats_json JSON NOT NULL COMMENT '生成时由Java统计的数字快照',
    body TEXT NULL COMMENT '信的正文，含引用标记',
    ps VARCHAR(400) NULL COMMENT '可选P.S.',
    result_kind VARCHAR(16) NULL COMMENT 'MODEL / SUPPORT',
    template_version VARCHAR(64) NULL,
    model_name VARCHAR(128) NULL,
    last_error_code VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_revision (user_id, review_type, range_start, revision),
    KEY idx_review_user_type_start (user_id, review_type, range_start, status),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心情回顾与周报';

CREATE TABLE mood_review_source (
    review_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    content_version BIGINT NOT NULL COMMENT '生成时的记录版本',
    sent_to_model TINYINT NOT NULL COMMENT '是否发给了模型（D1）',
    PRIMARY KEY (review_id, record_id),
    KEY idx_review_source_record (record_id),
    CONSTRAINT fk_review_source_review FOREIGN KEY (review_id) REFERENCES mood_review (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回顾的来源记录';
