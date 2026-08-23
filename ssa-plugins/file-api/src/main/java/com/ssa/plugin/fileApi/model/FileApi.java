package com.ssa.plugin.fileApi.model;

import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ApiParam;
import com.ssa.plugin.sqlApi.ReturnType;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


/**
 * 文件操作API，定义
 * 上传：先上传文件，成功后，在执行content
 * @author kevin
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileApi extends ApiBase {
     /**
     * 多数据源支持，默认可以不填。
     */
    private String datasource;

    /**
     * 返回类型
     */
    private ReturnType returnType;
    /**
     * 选项
     */
    private FileApiOption options;
    /**
     *
     */
    private List<ApiParam> params2;
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
