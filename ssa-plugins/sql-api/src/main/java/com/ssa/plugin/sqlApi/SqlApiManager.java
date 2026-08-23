package com.ssa.plugin.sqlApi;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.service.ApiState;
import com.ssa.core.service.ISsaApiManager;
import com.ssa.core.utils.*;
import com.ssa.plugin.sqlApi.utils.DbUtils;
import com.ssa.plugin.sqlApi.utils.SqlApiParamUtils;
import com.ssa.plugin.annotation.SsaApiMethod;
import com.ssa.plugin.sqlApi.event.SqlApiEventExec;
import com.ssa.plugin.sqlApi.entity.SqlApi;
import com.ssa.plugin.sqlApi.impl.SqlApiExecSql;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 根据API名称获取一个 API实例
 * 不管这个API模板从那来，最终都返回一个 SqlApi
 * 目前没办法得知 一个API要从那里来，所以要按以下顺序去找。后期可全注册一下，就能知道在那了。这时候可以使用 工厂模式了。
 * 目前按这个顺序找：
 * 1、在ApiMap内存中找（sqlCache 开启时有用）
 * 2、从loaders中加载（策略，实现一个策略时可设置Order）
 * 目前已实现的策略
 * 1、SqlApiYmlLoader 从yml文件中加载
 * 2、从Redis中获取（未实现，可能后期会实现）
 * 3、从数据库中找（不建议使用。对数据库压力大，已有实现的方法但不默认增加）
 * @author ssa
 */
@Service("sqlApiManager")
@Order(1)
@Slf4j
public class SqlApiManager implements ISsaApiManager {
    /**
     * 这个相当于内置Api名称吧，外部不让使用
     */
    public final static String UPDATE_TABLE = "sqlApi.update";
    /**
     * 缓存中的SqlApi 如果要按顺序使用 LinkedHashMap TreeMap,如果要快使用HashMap
     */
    private final static Map<String, SqlApi> cpasApiMap = new LinkedHashMap<>();
    /**
     * SqlApi 加载器列表
     */
    private final static List<ISqlApiLoader> loaders = new ArrayList<>();

    /**
     * 是否启用缓存 默认启用内存，后期要加入redis缓存
     */
    @Value("${sqlapi.template.cache:0}")
    private boolean sqlCache;

    private ApiBase loadApi(String name) {
        if (loaders.isEmpty()) {
            loaders.addAll(SpringUtil.getAppContext().getBeansOfType(ISqlApiLoader.class).values());
            // 按照定义的优先级顺序排序
            try {
                AnnotationAwareOrderComparator.sort(loaders);
            } catch (Exception e) {
                log.error("未找到SqlApi加载器：{}", e.getMessage());
            }
        }
        for (ISqlApiLoader sqlApiLoader : loaders) {
            ApiBase api = (sqlApiLoader).getApi(name, SqlApi.class);
            if(api != null) {
                return api;
            }
        }
        return null;
    }

    @Override
    @SsaApiMethod(name = "getSqlApi", describe = "获取SqlApi对象")
    public ApiBase getApi(String name, boolean reload) {
        if (UPDATE_TABLE.equals(name)) {
            SqlApi api = new SqlApi();
            api.setReturnType(ReturnType.UPDATE_TABLE);
            api.setRequireAuth(true);
            return api;
        }
        // 先从缓存中找
        if (reload) { cpasApiMap.remove(name); }
        ApiBase api = cpasApiMap.get(name);
        // 从扩展加载器中找
        if (api == null) {
            api = loadApi(name);
        }
        // 缓存
        if ((api != null) && sqlCache) {
            cpasApiMap.put(name, (SqlApi) api);
        }
        return api;
    }


    @Override
    @SsaApiMethod(name = "reloadSqlApi")
    public boolean reload() {
        // 全部清空缓存
        cpasApiMap.clear();
        return true;
    }

    @Override
    @SsaApiMethod(name = "runSqlApi")
    public ApiResult<Object> run(String name, Map<String, Object> params, boolean reload) {
        // 获取 SqlApi 实例
        ApiBase api = getApi(name, reload);
        return run(api, params, reload);
    }

    @Override
    public ApiResult<Object> run(ApiBase api, Map<String, Object> params, boolean reload) {
        if (api == null) {
            return ApiResult.failed(ApiResultCode.NOT_FOUND);
        }
        // 如果有错直接返回内容
        if (ApiState.ERROR.equals(api.getState())) {
            return ApiResult.failed(api.getDescribe());
        }
        SqlApi sqlApi = (SqlApi) api;
        DbPro db;
        try {
            // 参数校验 只有params2支持校验
            SqlApiParamUtils.checkParams(params, sqlApi.getParams2());
            // 先获取 db 判断Dialect
            db = DbUtils.getDb(sqlApi.getDatasource());
        } catch (ApiException e) {
            return ApiResult.failed(e.getMessage());
        }
        // params 不能为null所以这里要处理一下。
        if (params == null) {
            params = new HashMap<>(16);
        }
        // 执行前置操作
        SqlApiEventExec eventExec = new SqlApiEventExec(sqlApi, db, params);
        try{
            if (!eventExec.exec(sqlApi.getOnBefore())) {
                return ApiResult.failed("前置事件执行错误，终止执行");
            }
        } catch (RuntimeException e) {
            return ApiResult.failed(e.getMessage());
        }

        // 如果用户实现了 ISqlApiExec 就执行用户实现的。
        ApiResult<Object> apiResult;
        if(db.getConfig().getDialect() instanceof ISqlApiExec) {
            apiResult = ((ISqlApiExec) db.getConfig().getDialect()).exec(db, api, params);
        } else {
            ISqlApiExec apiExec = new SqlApiExecSql();
            apiResult = apiExec.exec(db, api, params);
        }

        // 后置事件
        if (apiResult.isOk()) {
            eventExec.exec(sqlApi.getOnSuccess());
        } else {
            eventExec.exec(sqlApi.getOnError());
        }
        return apiResult;
    }
}
