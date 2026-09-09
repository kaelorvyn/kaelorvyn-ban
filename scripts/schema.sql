CREATE DATABASE IF NOT EXISTS kaerban CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kaerban.ban_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ban_id VARCHAR(40) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    target_uuid CHAR(36) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    target_name_lower VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    banned_by VARCHAR(64) NOT NULL,
    start_at BIGINT NOT NULL,
    end_at BIGINT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    INDEX idx_ban_uuid_active (target_uuid, active),
    INDEX idx_ban_name_active (target_name_lower, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kaerban.ip_bans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ban_id VARCHAR(40) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    target_uuid CHAR(36) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    target_name_lower VARCHAR(16) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    reason TEXT NOT NULL,
    banned_by VARCHAR(64) NOT NULL,
    start_at BIGINT NOT NULL,
    end_at BIGINT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    INDEX idx_ipban_uuid_active (target_uuid, active),
    INDEX idx_ipban_name_active (target_name_lower, active),
    INDEX idx_ipban_ip_active (ip, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kaerban.player_ips (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    player_name_lower VARCHAR(16) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    first_seen BIGINT NOT NULL,
    last_seen BIGINT NOT NULL,
    UNIQUE KEY uk_player_ip (uuid, ip),
    INDEX idx_ip_last_seen (ip, last_seen),
    INDEX idx_name_last_seen (player_name_lower, last_seen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kaerban.ip_ban_exemptions (
    uuid CHAR(36) PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    player_name_lower VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
