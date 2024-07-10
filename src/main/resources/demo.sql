/*
Navicat MySQL Data Transfer

Source Server         : mysql-localhost
Source Server Version : 50619
Source Host           : localhost:3306
Source Database       : demo_master

Target Server Type    : MYSQL
Target Server Version : 50619
File Encoding         : 65001

Date: 2022-02-28 14:40:33
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for t_test_0
-- ----------------------------
DROP TABLE IF EXISTS `t_test_0`;
CREATE TABLE `t_test_0` (
  `id` bigint(20) NOT NULL,
  `type` int(11) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `created_date` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of t_test_0
-- ----------------------------

-- ----------------------------
-- Table structure for t_test_1
-- ----------------------------
DROP TABLE IF EXISTS `t_test_1`;
CREATE TABLE `t_test_1` (
  `id` bigint(20) NOT NULL,
  `type` int(11) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `created_date` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of t_test_1
-- ----------------------------

-- ----------------------------
-- Table structure for t_test_2
-- ----------------------------
DROP TABLE IF EXISTS `t_test_2`;
CREATE TABLE `t_test_2` (
  `id` bigint(20) NOT NULL,
  `type` int(11) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `created_date` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of t_test_2
-- ----------------------------
