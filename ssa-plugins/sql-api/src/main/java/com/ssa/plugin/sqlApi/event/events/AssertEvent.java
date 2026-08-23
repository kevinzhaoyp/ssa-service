package com.ssa.plugin.sqlApi.event.events;

import com.jfinal.plugin.activerecord.DbPro;
import com.jfinal.plugin.activerecord.Record;
import com.ssa.core.utils.ApiException;
import com.ssa.plugin.sqlApi.event.IApiEvent;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AssertEvent implements IApiEvent {
    @Override
    public boolean exec(SqlApiEvent sqlApiEvent, DbPro db, Map<String, Object> args) {
        // 1、 执行sql内容，将返回结果传到 errorMsg中，解析。结果支持 map, list
        String result;
        Map<String, Object> map = new HashMap<>();
        map.put("args", args);
        if ("list".equalsIgnoreCase(sqlApiEvent.getOutType())) {
            List<com.jfinal.plugin.activerecord.Record> list = db.templateByString(sqlApiEvent.getContent(), args).find();
            map.put("list", list);
        } else if ("map".equalsIgnoreCase(sqlApiEvent.getOutType())) {
            Record record = db.templateByString(sqlApiEvent.getContent(), args).findFirst();
            map.put("map", record);
        }
        // 2、 判断返回值 ok或者空字符串算校验通过。
        result = db.templateByString(sqlApiEvent.getErrorMsg(), map).getSqlPara().getSql().trim();
        if (!StringUtils.hasText(result) || ("ok".equalsIgnoreCase(result))) {
            return true;
        } else if (sqlApiEvent.isOnErrorGotoNext()) {
            log.error("Assert error: AssertName:{}, msg:{}", sqlApiEvent.getName(), result);
            return true;
        } else {
            throw new ApiException(result);
        }
    }
}
