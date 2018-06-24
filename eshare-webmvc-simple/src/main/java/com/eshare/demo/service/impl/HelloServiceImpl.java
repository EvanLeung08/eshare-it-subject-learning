package com.eshare.demo.service.impl;

import com.eshare.demo.service.HelloService;
import com.eshare.framework.mvc.annotation.EsService;

/**
 * Created by liangyh on 2018/6/23.
 * Email:10856214@163.com
 */
@EsService
public class HelloServiceImpl implements HelloService {


    @Override
    public String sayHello(String message) {
        return "hello :"+message;
    }
}
