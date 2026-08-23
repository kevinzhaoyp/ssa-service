package com.ssa.plugin.sqlApi.entity;

import lombok.Data;

import java.util.List;
/**
 * sqlapi 事件
 * @author ssa
 */
@Data
public class SqlApiEvent {
    /**
     * 事件名称，可以简单标记一下职责
     */
    private String name;
    /**
     * 是否启用，可以使用表达式(使用enjoy?)
     */
    private boolean enable = true;
    /**
     * 该方法执行发生错误时是否继续往下执行
     */
    private boolean onErrorGotoNext = true;
    /**
     * 操作类型，OPEN_SQL,EXEC_SQL,(还未完善),function(还未实现，内置方法 还通过方法改变params)
     */
    private String contentType;
    /**
     * datasource 多数据源设置，需要和config中的数据源名称一致
     */
    private String ds;
    /**
     * sql 内容
     */
    private String content;
    /**
     * 发生错误时的信息，支持enjoy语法。断言等
     */
    private String errorMsg;
    /**
     * 输出类型，会根据这个输出类型提取参数值,与contentType相关
     * void,object,list,map,map和object一样
     */
    private String outType;
    /**
     * 提取参数到params中，遇到同名参数会覆盖
     */
    private List<com.ssa.core.service.ApiParam> outParams;

}
