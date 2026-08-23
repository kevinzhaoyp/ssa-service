package com.ssa.plugin.sqlApi.utils;

import com.alibaba.druid.util.StringUtils;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.DbKit;
import com.jfinal.plugin.activerecord.DbPro;
import com.ssa.core.utils.ApiException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DbUtils {
    /**
     * 获取数据源 多数据源的情况下使用
     * @param datasource 数据库配置中的名称
     * @return DbPro
     */
    public static DbPro getDb(String datasource) throws ApiException {
        if (StringUtils.isEmpty(datasource)) {
            datasource = DbKit.MAIN_CONFIG_NAME;
        }
        try {
            return Db.use(datasource);
        } catch (Exception e) {
            log.error("datasource {} not found {}", datasource, e.getMessage());
            throw new ApiException("datasource " + datasource + " not found.");
        }
    }
}
