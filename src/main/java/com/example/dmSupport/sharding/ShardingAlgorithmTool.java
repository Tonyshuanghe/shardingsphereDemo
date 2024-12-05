package com.example.dmSupport.sharding;

import cn.hutool.extra.spring.SpringUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
    public static final String TABLE_SPLIT_SYMBOL = "_";
    private static Connection connection = null;
   private static Statement statement = null;

    private static String dbName = null;

    static {
        try {
            String url = SpringUtil.getProperty("subDb.jdbcUrl"); // 数据库 URL
            String username = SpringUtil.getProperty("subDb.username"); // 数据库用户名
            String password = SpringUtil.getProperty("subDb.password"); // 数据库密码
            String classDriver = SpringUtil.getProperty("subDb.driverClassName");
            dbName = SpringUtil.getProperty("subDb.dbName");
            // 1. 加载驱动程序
            Class.forName(classDriver);
            // 2. 建立连接
            connection = DriverManager.getConnection(url, username, password);
            // 3. 创建 Statement 对象
            statement = connection.createStatement();
        } catch (Exception e) {
        }
    }

    /**
     * 获取所有表名
     * @return 表名集合
     * @param logicTableName 逻辑表
     */
    public static synchronized List<String> getAllTableNameBySchema(String logicTableName) {
        List<String> tableNames = new ArrayList<>();
        try {
            for (String tableName : getTableNameList(logicTableName + TABLE_SPLIT_SYMBOL)) {
                // 匹配分表格式 例：^(t\_contract_\d{6})$
                if (tableName != null && tableName.matches(String.format("^(%s\\d{6})$", logicTableName + TABLE_SPLIT_SYMBOL))) {
                    tableNames.add(tableName);
                }
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
        YearMonth shardingMonth = YearMonth.parse(month, DateTimeFormatter.ofPattern(TimeShardingAlgorithm.DATE_FORMAT));
        if (shardingMonth.isAfter(YearMonth.now())) {
            return false;
        }

        return createShardingTableExecute(logicTableName, resultTableName);
    }

    public static Boolean createShardingTableExecute(String logicTableName, String resultTableName) {
        synchronized (resultTableName.intern()){
            // 缓存中无此表，则建表并添加缓存
            String sql;
            String oracle = "CREATE TABLE " + resultTableName + " AS SELECT * FROM " + logicTableName + " WHERE 1=2";
            String mysql = "CREATE TABLE IF NOT EXISTS `" + resultTableName + "` LIKE `" + logicTableName + "`;";            sql = oracle;
            sql = mysql;
            return executeReBoolean(sql);
        }
    }

    private static Boolean executeReBoolean(String sql) {
        boolean f = false;
        try {
            // 4. 执行查询
            statement.execute(sql);
            f = true;
        } catch (Exception e) {
        }
        return f;
    }

    /**
     * 执行SQL
     * @param sqlList SQL集合
     */
    private static void executeSql(List<String> sqlList) {

        try  {
            for (String string : sqlList) {
                executeSql(string);
            }
        } catch (Exception e) {
            log.error(">>>>>>>>>> 【ERROR】数据库操作失败，请稍后重试，原因：{}", e.getMessage(), e);
            throw new IllegalArgumentException("数据库操作失败，请稍后重试");
        }
    }

    private static ResultSet executeSql(String sql) {
        ResultSet resultSet = null;
        try {
            // 4. 执行查询
            resultSet = statement.executeQuery(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultSet;
    }


    private static List<String> getTableNameList(String tableNamePre){
        List<String> objects = Lists.newArrayList();
        String sql;
        String mysql = "show TABLES like '" + tableNamePre + TABLE_SPLIT_SYMBOL + "%'";
        String oracle = "SELECT table_name FROM ALL_TABLES WHERE table_name LIKE '" + tableNamePre + "%' and OWNER = '" + dbName + "'";
        sql = mysql;
        ResultSet resultSet = executeSql(sql);
        try {
            while (resultSet.next()) {
                String tableName = resultSet.getString(1);
                objects.add(tableName);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // 6. 关闭连接
            try {
                if (resultSet != null) resultSet.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return objects;
    }

}
