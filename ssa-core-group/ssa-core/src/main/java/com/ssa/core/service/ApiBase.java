package com.ssa.core.service;

import lombok.Data;

import java.util.List;

@Data
public class ApiBase {
    /**
     * 版本号，有助于后期升级使用 ,1.0 为数据库版，2.0为文件版
     */
    private String version;
    /**
     * 记录是从那个Loader中加载的，以方便找出真正的API
     * api来源 格式，type:from:
     */
    private String loaderFrom;
    /**
     * 状态
     */
    private ApiState state = ApiState.NORMAL;
    /**
     * 这个是说明，如果不加载是不是会省内存？
     */
    private String describe;

    /**
     * 是否需要验证 非安全调用时需要判断，controller中的白名单 需要判断
     */
    private Boolean requireAuth;
    /**
     * 是否注册到mcpTool中，1为注册，0为不注册
     */
    private int mcpTool = 0;
    /**
     * 参数列表, mpcTool中也能使用
     */
    private List<ApiParam> params2;
}
