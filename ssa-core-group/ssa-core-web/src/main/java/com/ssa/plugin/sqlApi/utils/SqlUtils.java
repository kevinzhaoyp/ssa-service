package com.ssa.plugin.sqlApi.utils;

import com.ssa.core.utils.ApiException;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SqlUtils {
    /**
     * 将ResultSet转为List<Map<String,Object>>
     * @param  rs ResultSet
     * @return List<Map<String, Object>>
     */
    public static List<Map<String, Object>> rsToList(ResultSet rs) throws ApiException {
        if (rs == null) {
            return new ArrayList<>();
        }
        //
        try (ResultSet nonNullRs = rs) {
            List<Map<String, Object>> list = new ArrayList<>();
            ResultSetMetaData md = nonNullRs.getMetaData();
            int columnCount = md.getColumnCount();

            while (nonNullRs.next()) {
                Map<String, Object> row = new HashMap<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.put(md.getColumnName(i), nonNullRs.getObject(i));
                }
                list.add(row);
            }
            return list;
        } catch (SQLException e) {
            throw new ApiException("Failed to convert ResultSet to List<Map<String, Object>>", e);
        }
    }
}
