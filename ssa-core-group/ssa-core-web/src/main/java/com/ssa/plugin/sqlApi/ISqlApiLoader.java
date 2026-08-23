package com.ssa.plugin.sqlApi;

import com.ssa.core.service.ApiBase;

/**
 * 加载sqlapi 接口，增加可扩展性，后期可扩展，支持从Redis等加载sqlapi
 * @author ssa
 */
public interface ISqlApiLoader {
    /**
     * 根据apiName获取Api
     * @param apiName api名称
     * @param cla 实现了 IApi 的 class
     * @return IApi
     */
    <T> ApiBase getApi(String apiName, Class<T> cla);
}