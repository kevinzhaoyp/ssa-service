package com.ssa.plugin.sqlApi.impl;

import com.alibaba.fastjson.JSONObject;
import com.jfinal.plugin.activerecord.DbPro;
import com.jfinal.plugin.activerecord.Record;
import com.ssa.core.service.ApiBase;
import com.ssa.core.utils.ApiResult;
import com.ssa.core.utils.ApiResultCode;
import com.ssa.core.utils.ApiUtils;
import com.ssa.plugin.sqlApi.entity.ApiSub;
import com.ssa.plugin.sqlApi.ISqlApiExec;
import com.ssa.plugin.sqlApi.entity.SqlApi;
import com.ssa.plugin.sqlApi.utils.BuildSql;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.ssa.core.utils.ApiResultCode.ERROR_SQL;

/**
 * @author ssa
 * 执行sql-api
 */
@Slf4j
public class SqlApiExecSql implements ISqlApiExec {
    /**
     * 将 Map中的Key替换为别名
     * @param map   map
     * @param alias 别名列表
     * @return key 替换为别名
     */
    public static Map<String, Object> convertKeyName(Map<String, Object> map, String alias) {
        if (ApiUtils.strIsEmpty(alias)) {
            return map;
        }
        Map<String, Object> newMap = new HashMap<>(16);
        alias = alias.replaceAll("\\s+", "");//别名替换进 删除所有空格和换行
        String[] aliasList = alias.split(",");
        for (String key : map.keySet()) {
            String newKey = key;
            for (String alia : aliasList) {
                if (alia.trim().equalsIgnoreCase(key.trim())) {
                    newKey = alia;
                    break;
                }
            }
            newMap.put(newKey, map.get(key));
        }
        return newMap;
    }

    /**
     * List<Record> 转 List<Map<String, Object>> 只提取 Columns 因为 jFinal 返回的是Record 如果以后找到方法了可以换
     *
     * @param recs  JFinal 的Record类型
     * @param alias
     * @return
     */
    public static List<Map<String, Object>> RecToMap(List<Record> recs, String alias) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Record rec : recs) {
            result.add(convertKeyName(rec.getColumns(), alias));
        }
        return result;
    }

    /**
     * 解析JSON数据，并执行Sql 支持，insert,update,delete
     * 执行 更新语句 多行（批量）
     *
     * @param params 实参
     * @return ApiResult 执行成功行数
     */
    public static ApiResult<Object> updateTable(DbPro db, SqlApi api, Map<String, Object> params) {
        if (params == null) {
            return ApiResult.failed("未获取任何参数");
        }
        String table = params.get("name").toString();
        if (ApiUtils.strIsEmpty(table)) {
            table = params.getOrDefault("table", "").toString();
        }
        List<Map<String, Object>> rows = (List<Map<String, Object>>) params.get("rows");
        //
        String dbName = params.getOrDefault("dbName", "").toString();
        if (!ApiUtils.strIsEmpty(dbName)) {
            table = "[" + dbName + "].." + table;
        }
        BuildSql bs = new BuildSql(table, rows);
        String sql = bs.getSqlText();
        // 增加事务
        AtomicReference<ApiResult> apiResult = new AtomicReference<>();
        db.tx(() -> {
            try {
                int i = db.update(sql);
                apiResult.set(ApiResult.success(i));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                apiResult.set(ERROR_SQL.getResult(e.getMessage()));
                return false;
            }
        });
        return apiResult.get();
    }

    /**
     * 6 执行Sql语句 返回响应的行数，update,delete
     * Object s = db.getSqlParaByString(api.getContent(), params);
     *
     * @param db
     * @param api
     * @param params
     * @return
     */
    public static ApiResult<Object> updateSql(DbPro db, SqlApi api, Map<String, Object> params) {
        AtomicReference<ApiResult> apiResult = new AtomicReference<>();
        db.tx(() -> {
            try {
                int i = db.templateByString(api.getContent(), params).update();
                apiResult.set(ApiResult.success(i));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                apiResult.set(ERROR_SQL.getResult(e.getMessage()));
                return false;
            }
        });
        return apiResult.get();
    }

    /**
     * 5,51 执行Sql语句  Sql 语句做为参数
     *
     * @return List<Record>
     */
    public static ApiResult<Object> getListBySql(DbPro db, SqlApi api, Map<String, Object> params, boolean first) {
        try {
            //如果后台没有配置别名且前天传入了,就使用前台的
            if (!StringUtils.hasText(api.getAlias())) {
                Object aliasObj = params.get("alias");
                if (aliasObj instanceof String) {
                    api.setAlias(aliasObj.toString());
                }
            }

            if (first) {
                Record rec = db.templateByString(api.getContent(), params).findFirst();
                return ApiResult.success((rec == null) ? null : convertKeyName(rec.getColumns(), api.getAlias()));
            } else {
                List<Record> list = db.templateByString(api.getContent(), params).find();
                return ApiResult.success(RecToMap(list, api.getAlias()));
            }
        } catch (Exception e) {
            log.error("执行接口{}失败：{}", "getListBySql", e.getMessage());
            return ApiResultCode.ERROR_SQL.getResult(e.getMessage());
        }
    }

    public static ApiResult<Object> getObjectSub(DbPro db, SqlApi api, Map<String, Object> params) {
        try {
            Record rec = db.templateByString(api.getContent(), params).findFirst();
            Map<String, Object> main;
            if (rec == null) {
                main = new HashMap<>();
            } else {
                main = convertKeyName(rec.getColumns(), null);
            }
            List<ApiSub> subs = api.getSubs();
            if (!subs.isEmpty()) {
                for (ApiSub apiSub : subs) {
                    List<Record> subList = db.templateByString(apiSub.getSql(), params).find();
                    main.put(apiSub.getName(), RecToMap(subList, apiSub.getAlias()));
                }
            }
            return ApiResult.success(main);
        } catch (Exception e) {
            log.error("执行接口失败：{}", e.getMessage());
            return ApiResultCode.ERROR_SQL.getResult(e.getMessage());
        }
    }
    /**
     * 7 执行Sql语句 Sql语句做为参数  返回 带分页 信息 {data:{total:100,rows:[]}}
     *
     * @param db
     * @param api
     * @param params
     * @return
     */
    public static ApiResult<Object> getListBySqlPager(DbPro db, SqlApi api, Map<String, Object> params) {
        JSONObject data = new JSONObject();
        try {
            //如果后台没有配置别名且前天传入了,就使用前台的 add by csb 20230303
            if (!StringUtils.hasText(api.getAlias())) {
                Object aliasObj = params.get("alias");
                if (aliasObj != null && aliasObj instanceof String) {
                    api.setAlias(aliasObj.toString());
                }
            }
            Object pageSize = params.get("pageSize");
            if (isNumeric(pageSize)) {
                // 默认10页
                params.put("pageSize", 10);
            }
            Object pageNum = params.get("pageNum");
            if (isNumeric(pageNum)) {
                // 默认第1页
                params.put("pageNum", 1);
            }
            List<Record> list = db.templateByString(api.getContent(), params).find();
            List<Map<String, Object>> mapList = RecToMap(list, api.getAlias());
            if (mapList.size() <= 0) {
                data.put("total", 0);
            } else {
                data.put("total", mapList.get(0).get("tc"));
            }
            data.put("rows", mapList);
            return ApiResult.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResultCode.ERROR_SQL.getResult(e.getMessage());
        }
    }

    private static boolean isNumeric(Object value) {
        if (value == null) {
            return true;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ApiResult<Object> exec(DbPro db, ApiBase api, Map<String, Object> params) {
        SqlApi sqlApi = (SqlApi) api;
        // 以下是系统默认执行的方法，目前只有在sql server下暂时无bug其它库没用过，还不确定。
        return switch (sqlApi.getReturnType()) {
            case UPDATE_TABLE -> updateTable(db, sqlApi, (params == null) ? new HashMap<>(16) : params);
            case EXEC_PROC, OPEN_PROC, OPEN_PROC_LIST ->
//                sqlApi.getVersion() = "20230303";
                    new SqlApiExecProcV2().exec(db, sqlApi, (params == null) ? new HashMap<>(16) : params);
            // 执行Sql语句，返回结果集 data=[]
            case LIST -> getListBySql(db, sqlApi, params, false);
            // 执行Sql语句，返回 第一条数据 对象 data={}
            case OBJECT -> getListBySql(db, sqlApi, params, true);
            case OBJECT_SUB -> getObjectSub(db, sqlApi, params);
            // 执行Sql语句 返回响应的行数 update,delete data=int
            case UPDATE -> updateSql(db, sqlApi, params);
            // 执行Sql语句 返回 带分页 信息 {data:{total:100,rows:[]}}
            case PAGE_LIST -> getListBySqlPager(db, sqlApi, params);
            default -> ApiResult.failed(ApiResultCode.ERROR_RETURN_TYPE);
        };
    }
}
