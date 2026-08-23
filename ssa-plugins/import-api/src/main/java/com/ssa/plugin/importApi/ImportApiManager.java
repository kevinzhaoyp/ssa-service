package com.ssa.plugin.importApi;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ApiState;
import com.ssa.core.service.ISsaApiManager;
import com.ssa.core.utils.ApiResult;
import com.ssa.core.utils.ApiResultCode;
import com.ssa.plugin.sqlApi.utils.DbUtils;
import com.ssa.core.utils.SpringUtil;
import com.ssa.plugin.importApi.exec.ExportExec;
import com.ssa.plugin.importApi.exec.ImportExec;
import com.ssa.plugin.importApi.model.ImportApi;
import com.ssa.plugin.sqlApi.ISqlApiExec;
import com.ssa.plugin.sqlApi.event.SqlApiEventExec;
import com.ssa.plugin.sqlApi.utils.ApiLoaderYml;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service("importApiManager")
public class ImportApiManager implements ISsaApiManager {
    /**
     * 从request中获取实参
     * @param args 原实参，会增加到args中
     * @return
     */
    private boolean getArgs(Map args) {
        ServletWebRequest request = (ServletWebRequest) args.get("request");
        Map<String, String[]> params = request.getParameterMap();
        for (String key: params.keySet()) {
            String[] values = params.get(key);
            Object value = null;
            if (values != null) {
                value = values.length == 1 ? values[0] : values;
            }
            args.put(key, value);
        }
        return true;
    }
    @Override
    public ApiBase getApi(String name, boolean reload) {
        ApiLoaderYml apiLoaderYml = SpringUtil.getBean(ApiLoaderYml.class);
        return apiLoaderYml.getApi(name, ImportApi.class);
    }

    @Override
    public boolean reload() {
        return false;
    }

    @Override
    public ApiResult run(String name, Map params, boolean reload) {
        // 获取 SqlApi 实例
        ApiBase api = getApi(name, reload);
        if (api == null) {
            return ApiResult.failed("未找到API: " + name);
        }
        return run(api, params, reload);
    }

    @Override
    public ApiResult run(ApiBase api, Map params, boolean reload) {
        if(ApiState.ERROR.equals(api.getState())) {
            return ApiResult.failed(api.getDescribe());
        }
        // 先获取 db 判断Dialect
        ImportApi importApi = (ImportApi) api;
        DbPro db = DbUtils.getDb(importApi.getDatasource());
        if (db == null) {
            log.error("datasource {} not found.", importApi.getDatasource());
            return ApiResult.failed(ApiResultCode.NOT_FOUND, "datasource " + importApi.getDatasource() + " not found.");
        }
        // 获取实参
        if (!getArgs(params)) {
            return ApiResult.failed(params.getOrDefault("errorMsg", "获取实参出错 ").toString());
        }
        // 执行前置操作
        SqlApiEventExec eventExec = new SqlApiEventExec(importApi, db, params);
        if (!eventExec.exec(importApi.getOnBefore())) {
            return ApiResult.failed("前置事件执行错误，终止执行");
        }
        // 执行
        ApiResult apiResult;
        ISqlApiExec apiExec;
        switch (importApi.getOptions().getOperate()) {
            case IMPORT:
                apiExec = new ImportExec();
                break;
            default:
                apiExec = new ExportExec();
        }
        apiResult = apiExec.exec(db, api, params);
        // 后置事件
        if (apiResult.isOk()) {
            eventExec.exec(importApi.getOnSuccess());
        } else {
            eventExec.exec(importApi.getOnError());
        }
        return apiResult;
    }

    public static ApiResult runApi(String name, ServletWebRequest request, HttpServletResponse response) {
        ImportApiManager importApiManager = SpringUtil.getBean(ImportApiManager.class);
        Map params = new HashMap<>(16);
        params.put("request", request);
        params.put("response", response);
        return importApiManager.run(name, params, true);
    }
}
