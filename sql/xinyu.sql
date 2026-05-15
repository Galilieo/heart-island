-- 心屿 AI 情绪陪伴系统数据库脚本
-- 数据库名需要和 xinyu-backend/src/main/resources/application.properties 中的配置保持一致。

CREATE DATABASE IF NOT EXISTS `xinyu`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `xinyu`;

DROP TABLE IF EXISTS `mood_record`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码密文，使用 BCrypt 保存',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `mood_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '心情记录ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `mood_type` VARCHAR(30) NOT NULL COMMENT '心情类型',
  `content` TEXT NOT NULL COMMENT '心情内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mood_record_user_id` (`user_id`),
  KEY `idx_mood_record_create_time` (`create_time`),
  CONSTRAINT `fk_mood_record_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心情记录表';
