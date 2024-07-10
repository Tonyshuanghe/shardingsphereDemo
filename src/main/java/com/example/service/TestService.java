package com.example.service;

import com.example.entity.Test;
import com.example.query.TestQuery;

import java.util.List;

public interface TestService {

    boolean add(Test test);

    List<Test> queryList(TestQuery query);
}
