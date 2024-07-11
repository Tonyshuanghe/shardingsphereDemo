/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.dmSupport.sharding;

import com.google.common.collect.Range;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p> @Title TimeShardingAlgorithm
 * <p> @Description 分片算法，按月分片
 *
 * @author ACGkaka
 * @date 2022/12/20 11:33
 */
@Slf4j
public final class TimeShardingAlgorithm implements StandardShardingAlgorithm<LocalDateTime>{

    /**
     * 分片时间格式
     */
    private static final DateTimeFormatter TABLE_SHARD_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 完整时间格式
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 表分片符号，例：t_contract_202201 中，分片符号为 "_"
     */
    private final String TABLE_SPLIT_SYMBOL = "_";


    @Getter
    private Integer autoTablesAmount = 0;


    public static LocalDateTime convertToLocalDateTime(Object obj) {
        if (obj instanceof LocalDateTime) {
            return (LocalDateTime) obj;
        }else if (obj instanceof Date) {
            return ((Date) obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }  else if (obj instanceof String) {
            try {
                return LocalDateTime.parse((String) obj, DATE_TIME_FORMATTER);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid date time string: " + obj);
            }
        } else {
            throw new IllegalArgumentException("Unsupported type: " + obj.getClass().getName());
        }
    }


    // --------------------------------------------------------------------------------------------------------------
    // 私有方法
    // --------------------------------------------------------------------------------------------------------------


    /**
     * 检查分表获取的表名是否存在，不存在则自动建表
     *
     * @param logicTableName        逻辑表
     * @param resultTableNames     真实表名，例：t_user_202201
     * @param availableTargetNames 可用的数据库表名
     * @return 存在于数据库中的真实表名集合
     */
    public Set<String> getShardingTablesAndCreate(String logicTableName, Collection<String> resultTableNames, Collection<String> availableTargetNames) {
        return resultTableNames.stream().map(o -> getShardingTableAndCreate(logicTableName, o, availableTargetNames)).collect(Collectors.toSet());
    }

    /**
     * 检查分表获取的表名是否存在，不存在则自动建表
     * @param logicTableName   逻辑表
     * @param resultTableName 真实表名，例：t_user_202201
     * @return 确认存在于数据库中的真实表名
     */
    private String getShardingTableAndCreate(String logicTableName, String resultTableName, Collection<String> availableTargetNames) {
        // 缓存中有此表则返回，没有则判断创建
        if (availableTargetNames.contains(resultTableName)) {
            return resultTableName;
        } else {
            // 检查分表获取的表名不存在，需要自动建表
            boolean isSuccess = ShardingAlgorithmTool.createShardingTable(logicTableName, resultTableName);
            if (isSuccess) {
                // 如果建表成功，需要更新缓存
                availableTargetNames.add(resultTableName);
                autoTablesAmount++;
                return resultTableName;
            } else {
                // 如果建表失败，返回逻辑空表
                return logicTableName;
            }
        }
    }

    /**
     * 获取 最小分片值
     * @param tableNames 表名集合
     * @return 最小分片值
     */
    private LocalDateTime getLowerEndpoint(Collection<String> tableNames) {
        Optional<LocalDateTime> optional = tableNames.stream()
                .map(o -> LocalDateTime.parse(o.replace(TABLE_SPLIT_SYMBOL, "") + "01 00:00:00", DATE_TIME_FORMATTER))
                .min(Comparator.comparing(Function.identity()));
        if (optional.isPresent()) {
            return optional.get();
        } else {
            log.error(">>>>>>>>>> 【ERROR】获取数据最小分表失败，请稍后重试，tableName：{}", tableNames);
            throw new IllegalArgumentException("获取数据最小分表失败，请稍后重试");
        }
    }

    /**
     * 获取 最大分片值
     * @param tableNames 表名集合
     * @return 最大分片值
     */
    private LocalDateTime getUpperEndpoint(Collection<String> tableNames) {
        Optional<LocalDateTime> optional = tableNames.stream()
                .map(o -> LocalDateTime.parse(o.replace(TABLE_SPLIT_SYMBOL, "") + "01 00:00:00", DATE_TIME_FORMATTER))
                .max(Comparator.comparing(Function.identity()));
        if (optional.isPresent()) {
            return optional.get();
        } else {
            log.error(">>>>>>>>>> 【ERROR】获取数据最大分表失败，请稍后重试，tableName：{}", tableNames);
            throw new IllegalArgumentException("获取数据最大分表失败，请稍后重试");
        }
    }

    @Override
    public String doSharding(Collection<String> collection, PreciseShardingValue<LocalDateTime> preciseShardingValue) {
        String logicTableName = preciseShardingValue.getLogicTableName();

        /// 打印分片信息
        log.debug(">>>>>>>>>> 【INFO】精确分片，节点配置表名：{}", collection);
        LocalDateTime dateTime = convertToLocalDateTime(preciseShardingValue.getValue());
        String resultTableName = logicTableName + "_" + dateTime.format(TABLE_SHARD_TIME_FORMATTER);

        // 检查是否需要初始化
        if (collection.size() == 1) {
            // 如果只有一个表，说明需要获取所有表名
            List<String> allTableNameBySchema = ShardingAlgorithmTool.getAllTableNameBySchema(logicTableName);
            collection.clear();
            collection.addAll(allTableNameBySchema);
            autoTablesAmount = allTableNameBySchema.size();
            return resultTableName;
        }

        return getShardingTableAndCreate(logicTableName, resultTableName, collection);
    }



    @Override
    public Collection<String> doSharding(Collection<String> collection, RangeShardingValue<LocalDateTime> rangeShardingValue) {
        String logicTableName = rangeShardingValue.getLogicTableName();

        /// 打印分片信息
        log.debug(">>>>>>>>>> 【INFO】范围分片，节点配置表名：{}", collection);

        // between and 的起始值
        Range<LocalDateTime> valueRange = rangeShardingValue.getValueRange();
        boolean hasLowerBound = valueRange.hasLowerBound();
        boolean hasUpperBound = valueRange.hasUpperBound();

        // 获取最大值和最小值
        LocalDateTime min = hasLowerBound ? convertToLocalDateTime(valueRange.lowerEndpoint()) :getLowerEndpoint(collection);
        LocalDateTime max = hasUpperBound ? convertToLocalDateTime(valueRange.upperEndpoint()) :getUpperEndpoint(collection);

        // 循环计算分表范围
        Set<String> resultTableNames = new LinkedHashSet<>();
        while (min.isBefore(max) || min.equals(max)) {
            String tableName = logicTableName + TABLE_SPLIT_SYMBOL + min.format(TABLE_SHARD_TIME_FORMATTER);
            resultTableNames.add(tableName);
            min = min.plusMinutes(1);
        }
        if(autoTablesAmount == 0){
            List<String> allTableNameBySchema = ShardingAlgorithmTool.getAllTableNameBySchema(logicTableName);
            collection.clear();
            collection.addAll(allTableNameBySchema);
            autoTablesAmount = allTableNameBySchema.size();
        }
        return getShardingTablesAndCreate(logicTableName, resultTableNames, collection);
    }


}

