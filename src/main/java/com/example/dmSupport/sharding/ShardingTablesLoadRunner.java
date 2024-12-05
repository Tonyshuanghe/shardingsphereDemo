package com.example.dmSupport.sharding;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p> @Title ShardingTablesLoadRunner
 * <p> @Description 项目启动后，读取已有分表，进行缓存
 *
 * @author ACGkaka
 * @date 2022/12/20 15:41
 */
@Slf4j
@Order(value = 1) // 数字越小，越先执行
@Component
public class ShardingTablesLoadRunner implements CommandLineRunner {


    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Override
    public void run(String... args) {
        String now = DateUtil.now();
        String startTime = "2024-01-01 00:00:00";
        DateTime start = DateUtil.parse(startTime);
        DateTime end = DateUtil.parse("2030-12-01 00:00:00");
        List<String> allTableList = Lists.newArrayList();

        // 获取 sharding-test-mysql.yaml 配置文件路径
        String yamlFilePath = getYamlFilePath(datasourceUrl);
        if (yamlFilePath == null) {
            System.out.println("配置文件路径无效");
            return;
        }

        // 跳过 - !SINGLE 和 - !SHARDING 之前的所有行，保留之后的内容
        String yamlContent = skipBeforeSharding(yamlFilePath);

        // 解析 YAML 配置文件
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(yamlContent);
        List<String> tableList = Lists.newArrayList();

        // 获取分片规则
        Map<String, Object> tables = (Map<String, Object>) config.get("tables");
        if (tables != null) {
            tables.forEach((key, value) -> {
                if (value instanceof Map) {
                    Map<String, Object> tableDetails = (Map<String, Object>) value;
                    String actualDataNodes = (String) tableDetails.get("actualDataNodes");
                    Object tableStrategy = tableDetails.get("tableStrategy");
                    if(tableStrategy != null){
                        tableList.add(actualDataNodes.split("\\.")[1]);
                    }
                }
            });
        }
        for (String data : tableList) {
            buildTableList(start, end, data, allTableList);
        }
        if (CollUtil.isNotEmpty(allTableList)) {
            for (String table : allTableList) {
                ShardingAlgorithmTool.createShardingTableExecute(table.substring(0, table.length() - 7),table);
                log.info("create table:{}",table);
            }
        }
        log.info(">>>>>>>>>> 【ShardingTablesLoadRunner end】<<<<<<<<<<{}",now);
    }

    private void buildTableList(DateTime start, DateTime end, String tableName, List<String> allTableList) {
        List<String>  face_structuredAll = DateUtil.rangeToList(start, end, DateField.DAY_OF_MONTH).stream().map(e-> tableName +ShardingAlgorithmTool.TABLE_SPLIT_SYMBOL+DateUtil.format(e,TimeShardingAlgorithm.DATE_FORMAT)).distinct().collect(Collectors.toList());
        allTableList.addAll(face_structuredAll);
        List<String> face_structured = ShardingAlgorithmTool.getAllTableNameBySchema(tableName);
        allTableList.removeAll(face_structured);
    }

    public static void main(String[] args) {
        String a = "devi_action_log_202411";

        System.out.println(a.substring(0, a.length() - 7));
    }


    private String getYamlFilePath(String datasourceUrl) {
        // 从 "jdbc:shardingsphere:classpath:" 提取出文件路径部分
        if (datasourceUrl != null && datasourceUrl.startsWith("jdbc:shardingsphere:classpath:")) {
            return datasourceUrl.replace("jdbc:shardingsphere:classpath:", "");
        }
        return null;
    }

    private String skipBeforeSharding(String filePath)  {
        StringBuilder content = null;
        try {
            content = new StringBuilder();
            boolean startCollecting = false;  // 标志位，表示是否开始收集数据
            boolean startCollecting2 = false;  // 标志位，表示是否开始收集数据
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(filePath)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 跳过 - !SINGLE 和 - !SHARDING 行
                    if (line.contains("  - !SINGLE") ) {
                        startCollecting = true; // 一旦遇到这些行，开始收集之后的内容
                        continue; // 跳过当前行
                    }
                    if (line.contains("  - !SHARDING") ) {
                        startCollecting2 = true; // 一旦遇到这些行，开始收集之后的内容
                        continue; // 跳过当前行
                    }

                    // 一旦开始收集内容，则保留后续所有行
                    if (startCollecting&& startCollecting2) {
                        content.append(line).append("\n");
                    }
                }
            }
        } catch (Exception e) {
        }
        return content.toString();
    }
}
