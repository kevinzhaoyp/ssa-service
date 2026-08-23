package com.ssa.plugin.sqlApi.event.events;

import com.jfinal.plugin.activerecord.DbPro;
import com.jfinal.plugin.activerecord.Record;
import com.ssa.core.utils.ApiException;
import com.ssa.core.service.ApiParam;
import com.ssa.plugin.sqlApi.event.IApiEvent;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;

import java.util.List;
import java.util.Map;

public class OpenSqlEvent implements IApiEvent {
    @Override
    public boolean exec(SqlApiEvent sqlApiEvent, DbPro db, Map<String, Object> args) throws ApiException {
        List<Record> list = db.templateByString(sqlApiEvent.getContent(), args).find();
        if (list.isEmpty()) return true;
        // 可以指定下标默认0
        Record record = list.get(0);
        // 设置输出参数 和 原来的params合并，有重名的覆盖。
        List<ApiParam> stringList = sqlApiEvent.getOutParams();
        if ((!stringList.isEmpty()) && (record != null)) {
            for (ApiParam param : sqlApiEvent.getOutParams()) {
                // 可以指定别名
//                param.getDefValue()
                args.put(param.getName(), record.getObject(param.getName()));
            }
        }
        return true;
    }
}
