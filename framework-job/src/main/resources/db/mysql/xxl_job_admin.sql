-- Optional XXL-JOB admin MySQL schema.
-- Run this script in the database used by xxl-job-admin, not in the business database.

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE TABLE IF NOT EXISTS xxl_job_info (
    id INT NOT NULL AUTO_INCREMENT,
    job_group INT NOT NULL,
    job_desc VARCHAR(255) NOT NULL,
    add_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    author VARCHAR(64) DEFAULT NULL,
    alarm_email VARCHAR(255) DEFAULT NULL,
    schedule_type VARCHAR(50) NOT NULL DEFAULT 'NONE',
    schedule_conf VARCHAR(128) DEFAULT NULL,
    misfire_strategy VARCHAR(50) NOT NULL DEFAULT 'DO_NOTHING',
    executor_route_strategy VARCHAR(50) DEFAULT NULL,
    executor_handler VARCHAR(255) DEFAULT NULL,
    executor_param VARCHAR(512) DEFAULT NULL,
    executor_block_strategy VARCHAR(50) DEFAULT NULL,
    executor_timeout INT NOT NULL DEFAULT 0,
    executor_fail_retry_count INT NOT NULL DEFAULT 0,
    glue_type VARCHAR(50) NOT NULL,
    glue_source MEDIUMTEXT,
    glue_remark VARCHAR(128) DEFAULT NULL,
    glue_updatetime DATETIME DEFAULT NULL,
    child_jobid VARCHAR(255) DEFAULT NULL,
    trigger_status TINYINT NOT NULL DEFAULT 0,
    trigger_last_time BIGINT NOT NULL DEFAULT 0,
    trigger_next_time BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB task definition';

CREATE TABLE IF NOT EXISTS xxl_job_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_group INT NOT NULL,
    job_id INT NOT NULL,
    executor_address VARCHAR(255) DEFAULT NULL,
    executor_handler VARCHAR(255) DEFAULT NULL,
    executor_param VARCHAR(512) DEFAULT NULL,
    executor_sharding_param VARCHAR(20) DEFAULT NULL,
    executor_fail_retry_count INT NOT NULL DEFAULT 0,
    trigger_time DATETIME DEFAULT NULL,
    trigger_code INT NOT NULL,
    trigger_msg TEXT,
    handle_time DATETIME DEFAULT NULL,
    handle_code INT NOT NULL,
    handle_msg TEXT,
    alarm_status TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_trigger_time (trigger_time),
    INDEX idx_handle_code (handle_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB execution log';

CREATE TABLE IF NOT EXISTS xxl_job_log_report (
    id INT NOT NULL AUTO_INCREMENT,
    trigger_day DATETIME DEFAULT NULL,
    running_count INT NOT NULL DEFAULT 0,
    suc_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trigger_day (trigger_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB log report';

CREATE TABLE IF NOT EXISTS xxl_job_logglue (
    id INT NOT NULL AUTO_INCREMENT,
    job_id INT NOT NULL,
    glue_type VARCHAR(50) DEFAULT NULL,
    glue_source MEDIUMTEXT,
    glue_remark VARCHAR(128) NOT NULL,
    add_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_job_id (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB GLUE source history';

CREATE TABLE IF NOT EXISTS xxl_job_registry (
    id INT NOT NULL AUTO_INCREMENT,
    registry_group VARCHAR(50) NOT NULL,
    registry_key VARCHAR(255) NOT NULL,
    registry_value VARCHAR(255) NOT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_registry (registry_group, registry_key, registry_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB executor registry';

CREATE TABLE IF NOT EXISTS xxl_job_group (
    id INT NOT NULL AUTO_INCREMENT,
    app_name VARCHAR(64) NOT NULL,
    title VARCHAR(12) NOT NULL,
    address_type TINYINT NOT NULL DEFAULT 0,
    address_list TEXT,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB executor group';

CREATE TABLE IF NOT EXISTS xxl_job_user (
    id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(50) NOT NULL,
    role TINYINT NOT NULL,
    permission VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB admin user';

CREATE TABLE IF NOT EXISTS xxl_job_lock (
    lock_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXL-JOB distributed lock';

INSERT INTO xxl_job_lock (lock_name)
VALUES ('schedule_lock')
ON DUPLICATE KEY UPDATE lock_name = VALUES(lock_name);

INSERT INTO xxl_job_user (id, username, password, role, permission)
VALUES (1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', 1, NULL)
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    role = VALUES(role);
