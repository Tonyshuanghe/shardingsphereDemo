package com.example.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.extra.spring.SpringUtil;
import com.google.protobuf.ServiceException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库助手
 *
 * @author bds
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DataBaseHelper {

    private static final DataSource DS = SpringUtil.getBean(DataSource.class);

    /**
     * 获取当前数据库类型
     */
    public static DataBaseType getDataBaseType() {
        try (Connection conn = DS.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String databaseProductName = metaData.getDatabaseProductName();
            return DataBaseType.find(databaseProductName);
        } catch (Exception e) {
        }
        return null;
    }

    public static boolean isMySql() {
        return DataBaseType.MY_SQL == getDataBaseType();
    }

    public static boolean isOracle() {
        //达梦数据库兼容Oracle语法
        return DataBaseType.ORACLE == getDataBaseType();
    }

    public static boolean isPostgerSql() {
        return DataBaseType.POSTGRE_SQL == getDataBaseType();
    }

    public static boolean isSqlServer() {
        return DataBaseType.SQL_SERVER == getDataBaseType();
    }

    public static String findInSet(Object var1, String var2) {
        DataBaseType dataBasyType = getDataBaseType();
        String var = Convert.toStr(var1);
        if (dataBasyType == DataBaseType.SQL_SERVER) {
            // charindex(',100,' , ',0,100,101,') <> 0
            return "charindex(',%s,' , ','+%s+',') <> 0".formatted(var, var2);
        } else if (dataBasyType == DataBaseType.POSTGRE_SQL) {
            // (select position(',100,' in ',0,100,101,')) <> 0
            return "(select position(',%s,' in ','||%s||',')) <> 0".formatted(var, var2);
        } else if (dataBasyType == DataBaseType.ORACLE) {
            // instr(',0,100,101,' , ',100,') <> 0
            return "instr(','||%s||',' , ',%s,') <> 0".formatted(var2, var);
        }
        // find_in_set(100 , '0,100,101')
        return "find_in_set('%s' , %s) <> 0".formatted(var, var2);
    }

    /**
     * 获取当前加载的数据库名
     */
    public static List<String> getDataSourceNameList() {
        return new ArrayList<>();
    }
}
