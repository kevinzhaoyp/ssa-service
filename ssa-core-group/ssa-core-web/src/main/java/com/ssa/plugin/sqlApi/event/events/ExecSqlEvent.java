package com.ssa.plugin.sqlApi.event.events;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.plugin.sqlApi.event.IApiEvent;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;

import java.util.Map;

public class ExecSqlEvent implements IApiEvent {
    @Override
    public boolean exec(SqlApiEvent sqlApiEvent, DbPro db, Map<String, Object> args) {
        db.templateByString(sqlApiEvent.getContent(), args).update();
        return true;
    }
}
