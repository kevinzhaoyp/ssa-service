package com.ssa.plugin.importApi.model;

import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ApiParam;
import com.ssa.plugin.sqlApi.ReturnType;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImportApi extends ApiBase {
    /**
     * 返回类型
     */
    private ReturnType returnType;
    /**
     * 多数据源支持，默认可以不填。
     */
    private String datasource;
    /**
     * 选项
     */
    private ImportApiOption options;
    /**
     * 因为 存储过程的参数 需要按顺序传递，所以必须使用 String[], 后期可考虑修改该问题
     */
    private List<ApiParam> params;
    /**
     * 是否需要验证 目前好像用不着了？？
     */
    private Boolean requireAuth;
    /**
     * 内容,支持enjoy sql模板
     * 每个文件上传成功后都会执行一次（注意是每个）。多个文件上传时和onSuccess有区别
     */
    private String content;
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
