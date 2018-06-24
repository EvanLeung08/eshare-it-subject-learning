package com.eshare.framework.mvc.annotation;

import java.lang.annotation.*;

/**
 * Created by liangyh on 2018/6/20.
 * Email:10856214@163.com
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EsRequestParam {
    String value() default "";
}
