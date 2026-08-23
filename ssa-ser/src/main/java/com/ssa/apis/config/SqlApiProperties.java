package com.ssa.apis.config;

import lombok.Data;

import java.util.Map;
import java.util.TreeMap;

/**
 * SQL API 配置属性模型
 */
@Data
public class SqlApiProperties {
    private String primary = "main";
    private Map<String, DbProperties> datasource = new TreeMap<>();
}

