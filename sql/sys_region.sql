CREATE TABLE `sys_region` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `code` varchar(20) DEFAULT NULL COMMENT '区划编码',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父级id',
  `parent_code` varchar(20) DEFAULT NULL COMMENT '父级编码',
  `name` varchar(40) DEFAULT NULL COMMENT '区划名称',
  `full_name` varchar(40) DEFAULT NULL COMMENT '区划全称',
  `pinyin` varchar(50) DEFAULT NULL COMMENT '城市拼音',
  `level` varchar(20) DEFAULT NULL COMMENT '省-1,市-2,区-3',
  `longitude` decimal(10,7) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4;
