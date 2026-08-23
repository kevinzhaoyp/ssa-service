package com.ssa.plugin.sqlApi.event;

import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;

import java.util.Map;

public interface IApiEvent {
    /**
     * 执行事件
     * @param sqlApiEvent 事件
     * @param db 数据库连接实例
     * @param args 实参
     * @return 执行结果
     */
    boolean exec(SqlApiEvent sqlApiEvent, DbPro db, Map<String, Object> args);
}
