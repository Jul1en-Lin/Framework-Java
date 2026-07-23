# 1、初始化数据库：创建nacos外接数据库frameworkjava_nacos_dev和脚手架业务数据库frameworkjava_dev
# 2、创建用户，用户名：liendev 密码：lien@123
# 3、授予bitedev用户特定权限

CREATE database if NOT EXISTS `frameworkjava_nacos_dev` default character set utf8mb4 collate utf8mb4_general_ci;
CREATE database if NOT EXISTS `frameworkjava_dev` default character set utf8mb4 collate utf8mb4_general_ci;

CREATE USER 'liendev'@'%' IDENTIFIED BY 'lien@123';
grant replication slave, replication client on *.* to 'liendev'@'%';

GRANT ALL PRIVILEGES ON frameworkjava_nacos_dev.* TO  'liendev'@'%';
GRANT ALL PRIVILEGES ON frameworkjava_dev.* TO  'liendev'@'%';

FLUSH PRIVILEGES;
