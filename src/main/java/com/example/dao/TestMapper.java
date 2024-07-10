package com.example.dao;

import com.example.entity.Test;
import com.example.query.TestQuery;

import java.util.List;

public interface TestMapper {

    int insert(Test test);

    Test getById(Long id);

    List<Test> queryList(TestQuery query);
}
