package com.ssa.core.service;

import com.ssa.core.utils.ApiResult;
import com.ssa.core.utils.ApiResultCode;
import com.ssa.core.utils.ApiUtils;
import com.ssa.core.utils.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 */
@Slf4j
public final class ApiRunner {
    private ApiRunner() {}
    /**
     * Api管理列表，目前有 MethodApiManager,SqlApiManager,可指定执行顺序
     */
    private final static List<ISsaApiManager> apiManagerList = new ArrayList<>(5);

    /**
     * 根据SimpleClassName来获取 IApiManager
     * @param className SimpleName
     * @return IApiManager
     */
    private static ISsaApiManager getApiManagerBy(String className) {
        for (ISsaApiManager apiManager : apiManagerList) {
            if (apiManager.getClass().getSimpleName().equalsIgnoreCase(className)) {
                return apiManager;
            }
        }
        return null;
    }

    /**
     * 使用指定的 ApiManager 执行 API
     * @param name API name
     * @param params 实参
     * @param safe 调用是否是安全的，true，通行，false 只能执行 requireAuth=false的
     * @param reload 可为null,是否重新加载，MethodAPI不支持
     * @param apiManagerClassName 指定 apiManagerClassName
     * @return ApiResult
     */
    public static ApiResult<Object> run(String name, Map<String, Object> params, boolean safe, boolean reload, String apiManagerClassName) {
        if ((name == null) || name.isEmpty()) {
            return ApiResultCode.NOT_API_NAME.getResult();
        }
        // 使用指定的 ApiManager来执行API
        if (!ApiUtils.strIsEmpty(apiManagerClassName)) {
            ISsaApiManager apiManager = getApiManagerBy(apiManagerClassName);
            if (apiManager != null) {
                return apiManager.run(name, params, reload);
            }
        } else {
            for (ISsaApiManager apiManager : apiManagerList) {
                // 先获取 API
                ApiBase api = apiManager.getApi(name, reload);
                if (api != null) {
                    if (!safe && api.getRequireAuth()) {
                        log.info("api:{} 无权调用", name);
                        return ApiResult.failed("无权调用");
                    }
                    return apiManager.run(api, params, reload);
                }
            }
        }
        return ApiResult.failed("接口：" + name + "可能未实现");
    }

    /**
     * 执行 API
     * @param name API name
     * @param params 实参
     * @param safe 调用是否是安全的，true，通行，false 只能执行 requireAuth=false的
     * @param reload 可为null,是否重新加载，MethodAPI不支持
     * @return ApiResult
     */
    public static ApiResult<Object> run(String name, Map<String, Object> params, boolean safe, boolean reload) {
        return run(name, params, safe, reload, null);
    }

    /**
     * 认为已经通过了认证，可直接调用API
     * @param name apiName
     * @param params api 参数
     * @param reload 是否重新加载，MethodAPI不支持，只针对sqlapi
     * @return ApiResult
     */
    public static ApiResult<Object> safeRun(String name, Map<String, Object> params, boolean reload) {
        return run(name, params, true, reload);
    }

    /*
      通过 ApiPostBody 调用，一般都是内部调用
      @param apiPostBody
     * @return ApiResult
     */
//    public static ApiResult safeRun(ApiPostBody apiPostBody) {
//        return run(apiPostBody.getApiName(), apiPostBody.getArgs(), true, apiPostBody.getReload(), apiPostBody.getApiManagerClassName());
//    }

    /**
     * 获取实现了接口 IApiManager 的所有类，并增加到ApiManagerList中
     */
    public static void getApiManagerList() {
        apiManagerList.addAll(SpringUtil.getAppContext().getBeansOfType(ISsaApiManager.class).values());
        // 按照定义的优先级顺序排序
        AnnotationAwareOrderComparator.sort(apiManagerList);
        log.info("已加载：{}个 ApiManager", apiManagerList.size());
    }
}
