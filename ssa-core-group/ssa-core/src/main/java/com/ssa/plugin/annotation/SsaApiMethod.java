package com.ssa.plugin.annotation;

import com.ssa.core.service.ApiState;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Api注解
 * 本来要命名为 SsaApi的，但跟实体 api命名冲突了，所以就用 MethodApi代替，表明为方法类型的Api接口
 * 想加个检查工作，还不会加，后期在加把，
 * SsaApiMethod 方法只接受一个参数，Map,这个现在没有限制了。
 * 新问题：不知道如何获取方法上的 注释。所以就辛苦程序员们在写一次了。。。。
 * @author ssa
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SsaApiMethod {
    /**
     * api 名称
     * @return 名称格式为 类名.方法名
     */
    String name() default "";

    /**
     * 接口简要说明
     * @return 说明信息
     */
    String describe() default "";

    /**
     * 增加是否需要认证
     * @return 默认需要
     */
    boolean requireAuth() default true;

    /**
     * 增加个使用频率的标记(0-3)，越大表示频率越高，想着为提高效率增加，不知道有没有用。。。
     * @return 0-3,数字，0系统API使用了。
     */
    byte frequency() default 1;

    /**
     * 忽略ClassValue值，意味着不使用类名或指定名称，直接使用方法名做为ApiName
     * @return 默认不忽略
     */
    boolean ignoreClassValue() default false;

    /**
     * Api 状态
     * @return 默认 NORMAL正常
     */
    ApiState state() default ApiState.NORMAL;
}
