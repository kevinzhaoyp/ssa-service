package com.ssa.plugin.sqlApi.ext;

import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.dialect.MysqlDialect;
import com.jfinal.plugin.activerecord.dialect.OracleDialect;
import com.jfinal.plugin.activerecord.dialect.SqlServerDialect;
import com.ssa.core.utils.SpringUtil;
/**
 *  Enjoy 模板引擎默认配置添加了 com.jfinal.template.ext.sharedmethod.SharedMethodLib 为 Shared Method
 */
public class SharedMethodEx {
    private final ActiveRecordPlugin arp;
    public SharedMethodEx(ActiveRecordPlugin arp) {
        this.arp = arp;
    }

    /**
     * 获取配置文件中的信息
     * @param key 配置文件key
     * @return 配置文件value
     * 用法：#conf()
     */
    public String conf(String key) {
        return SpringUtil.getEnvironment().getProperty(key);
    }

    /**
     * 为解决sql注入增加的。
     * 先支持sqlserver,mysql,oracle三种数据库吧。后面在说。
     * @param v 原对象名。
     * @return 格式化后的数据库对象，如库名，表名等
     */
    public String o(String v) {
        // 默认
        if (arp == null) {
            return "[" + v.replaceAll("]", "]]") + "]";
        }
        if (arp.getConfig().getDialect() instanceof SqlServerDialect) {
            return "[" + v.replaceAll("]", "]]") + "]";
        } else if (arp.getConfig().getDialect() instanceof MysqlDialect) {
            return "`" + v + "`";
        } else if (arp.getConfig().getDialect() instanceof OracleDialect) {
            return "\"" + v + "\"";
        }
        return v;
    }

}
