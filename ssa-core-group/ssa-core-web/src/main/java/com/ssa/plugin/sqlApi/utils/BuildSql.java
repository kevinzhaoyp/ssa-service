package com.ssa.plugin.sqlApi.utils;

import com.ssa.core.utils.ApiUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * cpas-server
 * 生成可执行sql，原在sqlapis下，为了方便使用 做为一个工具类使用
 *
 * @author: zyp
 * 只生成可执行sql  目前只支持mssql 后期增加 mysql等
 * 解析JSON数据，并执行Sql 支持，insert,update,delete
 */
public class BuildSql {
    static  public String lineEndChar = ";";
    private String tableName;
    /**
     * 主键，多个之间使用 , 号隔开
     */
    private String keys = "";
    /**
     * 当前活动行
     */
    private Map<String, Object> curRow = null;
    private List<Map<String, Object>> rows;

    private String QStr(Object value) {
        if (value == null) {
            return "null";
        } else {
            return "'" + value.toString().replaceAll("'", "''") + "'";
        }
    }

    /**
     * 获取插入语句，注：自动编号前端不用传
     *
     * @param row
     * @return
     */
    private String getInsert(Map<String, Object> row) {
        String fields = "";
        String values = "";
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            fields += "," + entry.getKey();
            values += "," + QStr(entry.getValue());
        }
        if (ApiUtils.strIsEmpty(fields)) {
            return "";
        } else {
            return "insert into " + tableName + "(" + fields.substring(1) + ")values(" + values.substring(1) + ")" + lineEndChar;
        }
    }

    /**
     * 获取条件语句
     *
     * @param row
     * @return
     */
    private String getWhere(Map<String, Object> row) {
        String sql = "";
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if ("".equals(sql)) {
                sql = entry.getKey() + "=" + QStr(entry.getValue());
            } else {
                sql += " and " + entry.getKey() + "=" + QStr(entry.getValue());
            }
        }
        return " where " + sql;
    }

    /**
     * 获取删除语句
     *
     * @param row
     * @return
     */
    private String getDelete(Map<String, Object> row) {
        return "delete from " + this.tableName + this.getWhere(row) + lineEndChar;
    }

    /**
     * 获取更新语句，不同的是，row中需要包含，where {}
     *
     * @param row
     * @return
     */
    private String getUpdate(Map<String, Object> row) {
        String where = "";
        if (row.get("where") != null) {
            Map<String, Object> joWhere = (Map<String, Object>) row.get("where");
            row.remove("where");
            where = this.getWhere(joWhere);
        } else if (!"".equals(keys)) {
            String[] keyList = keys.split(",");
            for (String key : keyList) {
                where += ("".equals(where) ? "" : " and ") + key + "=" + QStr(row.getOrDefault(key, ""));
                row.remove(key);
            }
            where = " where " + where;
        } else {
            return "";
        }
        //
        String sql = "";
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            sql += "," + entry.getKey() + "=" + QStr(entry.getValue());
        }
        return "update " + this.tableName + " set " + sql.substring(1) + where + lineEndChar;
    }

    /**
     * 返回 一条 Sql 语句
     *
     * @param row
     * @return
     */
    private String getRowSql(Map<String, Object> row) {
        String rowState = row.getOrDefault("rowState", "insert").toString();//默认 insert
        row.remove("rowState");
        switch (rowState) {
            case "insert":
                return this.getInsert(row);
            case "delete":
                return this.getDelete(row);
            case "update":
                return this.getUpdate(row);
            default:
                return "";
        }
    }

    /**
     * builder
     *
     * @param tableName
     * @return
     */
    public static BuildSql builder(String tableName) {
        return new BuildSql(tableName);
    }

    /**
     * 目前只支持mssql 后期增加 mysql等
     *
     * @param tableName
     */
    public BuildSql(String tableName) {
        this.tableName = tableName;
        this.rows = new ArrayList<>();
    }

    public BuildSql(String tableName, List rows) {
        this.tableName = tableName;
        this.rows = rows;
    }

    public BuildSql setKeys(String keys) {
        this.keys = keys;
        return this;
    }

    /**
     * 增加一行数据 包含 rowState
     *
     * @return true
     */
    public BuildSql addRow(String rowState) {
        curRow = new LinkedHashMap();
        curRow.put("rowState", rowState);
        this.rows.add(curRow);
        return this;
    }

    /**
     * 为当前行设置值
     *
     * @param key
     * @param value
     * @return
     */
    public BuildSql set(String key, Object value) {
        if (curRow == null) {
            curRow = new LinkedHashMap();
            curRow.put("rowState", "insert");
            this.rows.add(curRow);
        }
        curRow.put(key, value);
        return this;
    }

    ;

    /**
     * 获取sql语句 多行（批量）
     *
     * @return 返回sql文本
     */
    public String getSqlText() {
        String sqlText = "";
        if ((rows == null) || (rows.size() <= 0)) {
            return sqlText;
        }
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            sqlText += getRowSql(row) + "\n";//加个换行,调试方便
        }
        return sqlText;
    }
}
