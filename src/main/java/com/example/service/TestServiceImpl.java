package com.example.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mapper.TestMapper;
import com.example.domain.Test;
import com.example.query.TestQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestServiceImpl implements TestService {
    @Autowired
    private TestMapper mapper;

    @Transactional
    @Override
    public boolean add(Test test) {
        int n = mapper.insert(test);
        return n > 0;
    }

    @Override
    public List<Test> queryList(TestQuery query) {
        return mapper.selectList(Wrappers.lambdaQuery());
    }
}
