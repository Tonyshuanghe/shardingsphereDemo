package com.example.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("t_test")
public class Test implements Serializable {
    @TableField
    private Long id;

    private String name;

    private LocalDateTime createdDate;
}
