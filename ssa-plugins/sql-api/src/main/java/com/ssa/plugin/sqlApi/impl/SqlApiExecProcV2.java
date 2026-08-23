package com.ssa.plugin.sqlApi.impl;

import com.jfinal.plugin.activerecord.DbPro;
import com.jfinal.plugin.activerecord.ICallback;
import com.jfinal.plugin.activerecord.SqlPara;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;
import com.ssa.core.utils.ApiResultCode;
import com.ssa.plugin.sqlApi.ISqlApiExec;
import com.ssa.plugin.sqlApi.ReturnType;
import com.ssa.plugin.sqlApi.entity.SqlApi;
import com.ssa.plugin.sqlApi.utils.SqlUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * 执行MsSql的存储过程,2.0版本
 * 该版本完全使用模板引擎,支持使用#p的使用
 * 2.0版本，要求把调用存储过程的方法写全。如： {call sp_test(1,2,3)}
 * Version 1.0
 */
@Slf4j
public class SqlApiExecProcV2 implements ISqlApiExec, ICallback {
    /**
     * 当前API
     */
    private SqlApi sqlApi = null;

    /**
     * sqlPara
     */
    private SqlPara sqlPara;
    @SneakyThrows
    @Override
    public Object call(Connection conn) {
        CallableStatement proc = conn.prepareCall(sqlPara.getSql());
        // parameter=形参(formal parameter) 简写 param， argument=实参(actual parameter) arg。
        for (int i = 0; i < sqlPara.getPara().length; i++) {
            Object arg = sqlPara.getPara()[i];
            // 原 proc.setString(i + 1, params.get(p[i]).toString());
            proc.setObject(i + 1, arg);
        }
        // proc.registerOutParameter(2, Types.INTEGER);//     注册输出参数
        // int a = proc.getInt(2); 获取输出参数
        // 执行，没有返回列表
        if (sqlApi.getReturnType() == ReturnType.EXEC_PROC) {
            //.registerOutParameter(2, java.sql.Types.VARCHAR);     注册输出参数
            return proc.execute();
            // proc.getObject(0);
        }
        ResultSet rs = proc.executeQuery();
        List<Map<String, Object>> list = SqlUtils.rsToList(rs);
        if (sqlApi.getReturnType() == ReturnType.OPEN_PROC) {
            return list.isEmpty() ? null : list.get(0);
        } else {
            return list;
        }
    }

    @Override
    public ApiResult<Object> exec(DbPro db, ApiBase api, Map<String, Object> params) {
        this.sqlApi = (SqlApi)api;
        sqlPara =  db.templateByString(sqlApi.getContent(), params).getSqlPara();
        log.debug("sql：{} 参数：{}", sqlPara.getSql(), sqlPara.getPara());
        try{
            Object data = db.execute(this);
            return ApiResult.success(data);
        } catch (Exception e) {
            log.error("执行接口失败：{}", e.getMessage());
//            e.printStackTrace();
            return ApiResultCode.ERROR_SQL.getResult(e.getMessage());
        }
    }
}
