-- ----------------------------
-- 管理端人员表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `nick_name` varchar(64) NOT NULL COMMENT '昵称',
    `phone_number` varchar(64) NOT NULL COMMENT '电话',
    `password` varchar(255) NOT NULL COMMENT '密码',
    `identity` varchar(16) NOT NULL COMMENT '身份',
    `remark` varchar(50) DEFAULT NULL COMMENT '备注',
    `status` varchar(10) NOT NULL COMMENT '状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone_number`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000001 DEFAULT CHARSET = utf8mb4 COMMENT = '管理端人员表';
