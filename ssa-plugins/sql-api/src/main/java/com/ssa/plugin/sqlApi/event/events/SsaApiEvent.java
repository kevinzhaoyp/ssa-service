package com.ssa.plugin.sqlApi.event.events;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiRunner;
import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.sqlApi.event.IApiEvent;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;

import java.util.Map;

public class SsaApiEvent implements IApiEvent {
    @Override
    public boolean exec(SqlApiEvent sqlApiEvent, DbPro db, Map<String, Object> args) {
        ApiResult<Object> r = ApiRunner.safeRun(sqlApiEvent.getContent(), args, false);
//        sqlApiEvent.getOutParams().forEach(p -> args.put(p.getName(), r.getData()));
        if (!r.isOk() && !sqlApiEvent.isOnErrorGotoNext()) {
            return false;
        }
        return true;
    }
}
