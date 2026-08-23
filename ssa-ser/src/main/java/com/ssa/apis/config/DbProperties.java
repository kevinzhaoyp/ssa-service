package com.ssa.apis.config;

import lombok.Data;

/**
 * 数据库链接属性配置
 */
@Data
public class DbProperties {
    private String driverClassName;
    private String url;
    private String username;
    private String password;
    private int maxActive;
    private int maxWait = 6000;
}

