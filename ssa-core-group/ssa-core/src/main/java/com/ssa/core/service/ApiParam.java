package com.ssa.core.service;

import lombok.Data;

/**
 * API 参数类型
 * @author ssa
 */
@Data
public class ApiParam {
    /**
     * 参数名
     */
    private String name;
    /**
     * 参数类型，目前支持 string,int
     */
    private String type;
    /**
     * 默认值，在事件里可以设置提取值
     */
    private Object defValue;
    /**
     * 是否必填
     */
    private boolean required;
    /**
     * 校验不通过时的提示信息
     */
    private String hint;
}
