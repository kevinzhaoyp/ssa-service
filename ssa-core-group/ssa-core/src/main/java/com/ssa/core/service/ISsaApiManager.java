package com.ssa.core.service;

import com.ssa.core.utils.ApiResult;

import java.util.Map;

public interface ISsaApiManager {
    /**
     * 获取 SSAApi
     * @param name ApiName
     * @param reload 是否重新加载
     * @return ISSAApi
     */
    ApiBase getApi(String name, boolean reload);

    /**
     * 重新加载所有已加载的 API
     * @return true
     */
    boolean reload();

    /**
     * 按 SSAApi name 执行
     * @param name API 名称
     * @param params 实参
     * @param reload 是否重新加载，只有 SqlApi存在。
     * @return ApiResult
     */
    ApiResult<Object> run(String name, Map<String, Object> params, boolean reload);

    /**
     * 按 SSAApi执行
     * @param api ISsaApi
     * @param params 实参
     * @param reload 是否重新加载，只有 SqlApi存在。
     * @return ApiResult
     */
    ApiResult<Object> run(ApiBase api, Map<String, Object> params, boolean reload);
}
