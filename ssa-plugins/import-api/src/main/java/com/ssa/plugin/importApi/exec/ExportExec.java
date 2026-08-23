package com.ssa.plugin.importApi.exec;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.sqlApi.ISqlApiExec;

import java.util.Map;

public class ExportExec implements ISqlApiExec {
    @Override
    public ApiResult<Object> exec(DbPro db, ApiBase api, Map<String, Object> params) {
        return null;
    }
}
