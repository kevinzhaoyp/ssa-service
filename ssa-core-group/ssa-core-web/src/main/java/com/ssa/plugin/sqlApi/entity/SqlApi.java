package com.ssa.plugin.sqlApi.entity;

import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ApiParam;
import com.ssa.plugin.sqlApi.ReturnType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 定义Api的结构，不管模板从那来，都要创建这么一个结构
 * 这个结构对应 json 文件对象
 * @author ssa
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SqlApi extends ApiBase {
    /**
     * 多数据源支持，默认可以不填。
     */
    private String datasource;
    /**
     * 返回类型
     */
    private ReturnType returnType;
    /**
     * 因为 存储过程的参数 需要按顺序传递，所以必须使用 String[], 后期可考虑修改该问题
     */
    @Deprecated
    private String[] params;

    private List<ApiParam> params2;
    /**
     * 内容
     */
    private String content;
    /**
     * 返回对象Key别名，是因为 oracle返回字段都为大写，所以增加，可能后期有用。
     */
    private String alias;

    private List<ApiSub> subs;
    /**
     * 执行前事件
     */
    private List<SqlApiEvent> onBefore;
    /**
     * 出错后事件
     */
    private List<SqlApiEvent> onError;
    /**
     * 执行成功后事件
     */
    private List<SqlApiEvent> onSuccess;
}

