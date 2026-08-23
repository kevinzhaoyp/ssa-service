package com.ssa.apis.config;

import com.alibaba.druid.filter.stat.StatFilter;
import com.jfinal.plugin.activerecord.ActiveRecordPlugin;
import com.jfinal.plugin.activerecord.OrderedFieldContainerFactory;
import com.jfinal.plugin.druid.DruidPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.sql.Connection;
import java.util.Collections;
import java.util.Map;

/**
 * @author kevin
 */
@Configuration
@Slf4j
public class SqlApiConfig {
    private static final int SLOW_SQL_MILLIS = 1500; // 单位：毫秒
    private static final int DEFAULT_MAX_ACTIVE = 8;


    /**
     * 将所有数据源配置加载到 SqlApiProperties 中
     */
    @Bean
    @ConfigurationProperties(prefix = "ssa.sqlapi")
    public SqlApiProperties sqlApiProperties() {
        return new SqlApiProperties();
    }

    /**
     * 将 dbProperties 转为 DruidPlugin
     * @param dbProperties 数据库链接属性
     * @return DruidPlugin
     */
    public DruidPlugin getDruidPlugin(DbProperties dbProperties) {
        DruidPlugin druidPlugin = new DruidPlugin(
                dbProperties.getUrl(),
                dbProperties.getUsername(),
                dbProperties.getPassword(),
                dbProperties.getDriverClassName());
        int maxActive = dbProperties.getMaxActive() > 0 ? dbProperties.getMaxActive() : DEFAULT_MAX_ACTIVE;
        druidPlugin.setMaxActive(maxActive);
        // 单位：毫秒，超过这个时间还没获取到连接则抛出异常，默认值是-1表示永不超时
        druidPlugin.setMaxWait(dbProperties.getMaxWait());
        // 连接有效性检测，防止空闲连接被防火墙/网络设备断开
        druidPlugin.setValidationQuery("SELECT 1");
        druidPlugin.setTestWhileIdle(true);
        druidPlugin.setTestOnBorrow(false);
        druidPlugin.setTestOnReturn(false);
        // 空闲连接保活间隔，小于数据库端的 wait_timeout 即可
        druidPlugin.setTimeBetweenEvictionRunsMillis(60000);
        druidPlugin.setMinEvictableIdleTimeMillis(300000);
        return druidPlugin;
    }

    /**
     * 配置监控
     * @param druidPlugin plugin
     */
    private void addStatFilter(DruidPlugin druidPlugin) {
        StatFilter statFilter = new StatFilter();
        statFilter.setMergeSql(true);
        statFilter.setLogSlowSql(true);
        statFilter.setSlowSqlMillis(SLOW_SQL_MILLIS);
        druidPlugin.addFilter(statFilter);
    }

    private void configureActiveRecordPlugin(ActiveRecordPlugin arp) {
        arp.setTransactionLevel(Connection.TRANSACTION_READ_COMMITTED);
        arp.setDevMode(true);
        arp.setContainerFactory(new OrderedFieldContainerFactory());
        arp.setShowSql(false);
    }
    @Bean
    public ActiveRecordPlugin initActiveRecordPlugin(SqlApiProperties properties) {
        Map<String, DbProperties> map = properties == null ? Collections.emptyMap() : properties.getDatasource();
        if (map == null || map.isEmpty()) {
            log.warn("sqlapi.datasource is empty, skip ActiveRecordPlugin init.");
            return null;
        }
        ActiveRecordPlugin mainArp = null;
        String primaryKey = properties.getPrimary() == null || properties.getPrimary().isEmpty()
                ? "main"
                : properties.getPrimary();
        for (String key : map.keySet()) {
            DbProperties dbProperties = map.get(key);
            DruidPlugin druidPlugin = getDruidPlugin(dbProperties);
            addStatFilter(druidPlugin);
            // 必须手动调用 start
            druidPlugin.start();
            //
            ActiveRecordPlugin arp = new ActiveRecordPlugin(key, druidPlugin);
            configureActiveRecordPlugin(arp);
            //
            try {
                // 增加扩展方法
//                arp.getEngine().addSharedMethod(new SharedMethodEx(arp));
                arp.start();
                log.info("数据源 {} 加载成功:{}", key, dbProperties.getUrl());
            } catch (Exception e) {
                log.error("数据源 {} 加载失败:{}", key, dbProperties.getUrl(), e);
            }
            if (primaryKey.equals(key)) {
                mainArp = arp;
            }
        }
        return mainArp;
    }
}