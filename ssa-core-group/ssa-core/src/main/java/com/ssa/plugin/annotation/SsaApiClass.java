package com.ssa.plugin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Api类注解
 * 启动后，找 所有的 SsaApiClass
 * @author ssa
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SsaApiClass {
    /**
     * 设置接口名称 可为空
     * @return 返回接口名前缀，默认为 类名（首字母会小写，如Class Test）返回 test.xxx
     */
    String value() default "";
    /**
     * 接口简要说明
     * @return 说明信息
     */
    String describe() default "";

    /**
     * 目前貌似没啥用，准备做为API的命名空间
     */
    String namespace() default "";
}
