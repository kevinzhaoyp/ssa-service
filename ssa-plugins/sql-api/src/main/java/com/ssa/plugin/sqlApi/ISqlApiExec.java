package com.ssa.plugin.sqlApi;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;

import java.util.Map;

/**
 * 执行 Api接口 并返回数据
 * @author ssa
 */
public interface ISqlApiExec {

    /**
     * 执行Api
     * @param db 数据库conn
     * @param api SqlApi 实例
     * @param params 实参，不能为 null
     * @return 返回 标准 RestResult值
     */
    ApiResult<Object> exec(DbPro db, ApiBase api, Map<String, Object> params);
}
