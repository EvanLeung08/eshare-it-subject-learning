package com.eshare.demo.controller;

import com.eshare.demo.annotation.EsAutowired;
import com.eshare.demo.annotation.EsController;
import com.eshare.demo.annotation.EsRequestMapping;
import com.eshare.demo.annotation.EsRequestParam;
import com.eshare.demo.service.HelloService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * hello控制器
 * Created by liangyh on 2018/6/20.
 * Email:10856214@163.com
 */
@EsController
@EsRequestMapping("/hello")
public class HelloController {

    @EsAutowired
    private HelloService helloService;

    @EsRequestMapping("/sayHello.do")
    public String sayHello(HttpServletRequest request, HttpServletResponse response,
                           @EsRequestParam("message") String message){
        return helloService.sayHello(message);
    }

}
