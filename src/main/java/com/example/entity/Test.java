package com.example.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class Test implements Serializable {

    private Long id;

    private String name;

    private Date createdDate;
}
