package com.example.controller;

import com.example.domain.Test;
import com.example.query.TestQuery;
import com.example.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {
    @Autowired
    private TestService orderService;

    @GetMapping("/add")
    public String add() {
        Test order = new Test();
        order.setId(System.currentTimeMillis());
        order.setName("test-" + System.currentTimeMillis());
        order.setCreatedDate(new Date());
        boolean bl = orderService.add(order);
        return bl ? "success" : "fail";
    }

    @GetMapping("/page")
    public ResponseEntity<List<Test>> page() {
        TestQuery query = new TestQuery();
        List<Test> list = orderService.queryList(query);
        return new ResponseEntity<>(list, HttpStatus.OK);
    }
}
