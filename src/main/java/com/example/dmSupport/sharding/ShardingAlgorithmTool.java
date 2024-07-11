package com.example.dmSupport.sharding;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p> @Title ShardingAlgorithmTool
 * <p> @Description 按月分片算法工具
 *
 * @author ACGkaka
 * @date 2022/12/20 14:03
 */
@Slf4j
public class ShardingAlgorithmTool {

    /** 表分片符号，例：t_user_202201 中，分片符号为 "_" */
    private static final String TABLE_SPLIT_SYMBOL = "_";
    public static final String MASTER_DB = "BDS_DB";
    /**
     * 获取所有表名
     * @return 表名集合
     * @param logicTableName 逻辑表
     */
    public static List<String> getAllTableNameBySchema(String logicTableName) {
        List<String> tableNames = new ArrayList<>();
        JdbcTemplate jdbcTemplate = SpringUtil.getBean(JdbcTemplate.class);
        try {
            String s = "SELECT table_name FROM ALL_TABLES WHERE table_name LIKE '" + logicTableName + TABLE_SPLIT_SYMBOL + "%' and OWNER = '" + MASTER_DB + "'";
            List<Map<String, Object>> maps = jdbcTemplate.queryForList(s);
            for (Map<String, Object> map : maps) {
                map.forEach((k,v)->{
                    String tableName = v.toString();
                    // 匹配分表格式 例：^(t\_contract_\d{6})$
                    if (tableName != null && tableName.matches(String.format("^(%s\\d{6})$", logicTableName + TABLE_SPLIT_SYMBOL))) {
                        tableNames.add(tableName);
                    }
                });
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("数据库操作失败，请稍后重试");
        }
        return tableNames;
    }


    /**
     * 创建分表2
     * @param logicTableName  逻辑表
     * @param resultTableName 真实表名，例：t_user_202201
     * @return 创建结果（true创建成功，false未创建）
     */
    public static boolean createShardingTable(String logicTableName, String resultTableName) {
        // 根据日期判断，当前月份之后分表不提前创建
        String month = resultTableName.replace(logicTableName + TABLE_SPLIT_SYMBOL,"");
        YearMonth shardingMonth = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"));
        if (shardingMonth.isAfter(YearMonth.now())) {
            return false;
        }

        synchronized (logicTableName.intern()) {
            // 缓存中无此表，则建表并添加缓存
            executeSql(Collections.singletonList("CREATE TABLE "+resultTableName+" AS SELECT * FROM "+logicTableName+" WHERE 1=2"));
        }
        return true;
    }

    /**
     * 执行SQL
     * @param sqlList SQL集合
     */
    private static void executeSql(List<String> sqlList) {

        try  {
            for (String string : sqlList) {
                SpringUtil.getBean(JdbcTemplate.class).execute(string);
            }
        } catch (Exception e) {
            log.error(">>>>>>>>>> 【ERROR】数据库操作失败，请稍后重试，原因：{}", e.getMessage(), e);
            throw new IllegalArgumentException("数据库操作失败，请稍后重试");
        }
    }

}
