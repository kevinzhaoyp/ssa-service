package com.ssa.plugin.sqlApi.entity;

import com.ssa.plugin.sqlApi.ReturnType;
import lombok.Data;

/**
 * API 子表类型，主要用于 OBJECT_SUB 类型
 * @author ssa
 */
@Data
public class ApiSub {
    /**
     * 参数名
     */
    private String name;
    /**
     * 返回类型
     */
    private ReturnType rt;
    /**
     * 默认值
     */
    private String sql;
    /**
     * 返回对象Key别名，是因为 oracle返回字段都为大写，所以增加，可能后期有用。
     */
    private String alias;
}
