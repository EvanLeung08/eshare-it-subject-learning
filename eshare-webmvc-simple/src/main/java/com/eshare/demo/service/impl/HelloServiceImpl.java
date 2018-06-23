package com.eshare.demo.service.impl;

import com.eshare.demo.annotation.EsService;
import com.eshare.demo.service.HelloService;

/**
 * Created by liangyh on 2018/6/23.
 * Email:10856214@163.com
 */
@EsService("helloService")
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String message) {
        return "say hello :"+message;
    }
}
