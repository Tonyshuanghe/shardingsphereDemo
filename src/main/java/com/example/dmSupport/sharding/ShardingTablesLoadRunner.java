package com.example.dmSupport.sharding;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.domain.Test;
import com.example.mapper.TestMapper;
import com.example.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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



    @Override
    public void run(String... args) {
        // 读取已有分表，进行缓存
        SpringUtil.getBean(TestMapper.class).selectList(Wrappers.<Test>lambdaQuery().between(Test::getCreatedDate, LocalDateTime.of(2024,6,7,7,7,7),LocalDateTime.now()));
        log.info(">>>>>>>>>> 【ShardingTablesLoadRunner】缓存已有分表成功 <<<<<<<<<<");
    }
}
