package com.ssa.plugin.sqlApi.event;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiException;
import com.ssa.plugin.sqlApi.entity.SqlApiEvent;
import com.ssa.plugin.sqlApi.event.events.AssertEvent;
import com.ssa.plugin.sqlApi.event.events.ExecSqlEvent;
import com.ssa.plugin.sqlApi.event.events.OpenSqlEvent;
import com.ssa.plugin.sqlApi.event.events.SsaApiEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SqlApi 事件执行器
 * @author ssa
 */
@Slf4j
public class SqlApiEventExec {
    private static final Map<String, Class<? extends IApiEvent>> registry = new HashMap<>();

    static {
        regEvent("OPEN_SQL", OpenSqlEvent.class);
        regEvent("EXEC_SQL", ExecSqlEvent.class);
        regEvent("SSA_API", SsaApiEvent.class);
        regEvent("ASSERT", AssertEvent.class);
    }
    public static void regEvent(String type, Class<? extends IApiEvent> eventClass) {
        registry.put(type.toLowerCase(), eventClass);
    }
    public static IApiEvent createEvent(String type) {
        if (type == null || type.isEmpty()) {
            throw new ApiException("类型不能为空");
        }

        Class<? extends IApiEvent> eventClass = registry.get(type.toLowerCase());
        if (eventClass == null) {
            throw new ApiException("未知的事件类型: " + type);
        }

        try {
            return eventClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("创建事件实例失败: " + type, e);
        }
    }
    /**
     * 宿主API
     */
    private ApiBase ownerApi;
    /**
     * 宿主API数据源
     */
    private final DbPro ownerDb;
    /**
     * API实参
     */
    private final Map<String, Object> args;

    public SqlApiEventExec(ApiBase ownerApi, DbPro ownerDb, Map<String, Object> args) {
        this.ownerApi = ownerApi;
        this.ownerDb = ownerDb;
        this.args = args;
    }

    /**
     * 执行事件
     * @param listEvent 事件列表
     * @return 执行结果
     */
    public boolean exec(List<SqlApiEvent> listEvent) {
        // 如果没有设置，直接返回true
        if ((listEvent == null) || (listEvent.isEmpty())) {
            return true;
        }
        for (SqlApiEvent apiEvent : listEvent) {
            // 内容为空，直接返回true视为无效
            if (!apiEvent.isEnable() || !StringUtils.hasText(apiEvent.getContent())) {
                continue;
            }

            DbPro db = ownerDb;
            if (StringUtils.hasText(apiEvent.getDs())) {
                db = Db.use(apiEvent.getDs());
            }
            IApiEvent event = createEvent(apiEvent.getContentType());
            try {
                event.exec(apiEvent, db, args);
            } catch (ApiException e) {
                if (apiEvent.isOnErrorGotoNext()) {
                    log.error("执行事件【{}】异常，已忽略，继续往下执行。", apiEvent.getName());
                } else {
                    throw new ApiException(e.getMessage());
                }
            }
        }
        return true;
    }
}
