-- 合成社区持久化测试：表定义取自sql/xinyu.sql，不导入业务数据。
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
