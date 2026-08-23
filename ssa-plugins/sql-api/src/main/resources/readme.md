# sql-api 插件使用说明

## 功能简介

- 提供 SQL API 执行能力（查询、分页、更新、存储过程等）
- 支持多数据源配置（主数据源 + 业务数据源）
- 可通过插件路由前缀访问（由 `ssa-plugin.yml` 的 `routePrefix` 决定）

## 配置说明

`SqlApiConfig` 使用 `ssa.sqlapi` 前缀绑定配置：
```yaml
ssa:
  sqlapi:
    primary: main
    datasource:
      main:
        driverClassName: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://127.0.0.1:3306/ssa?useSSL=false&serverTimezone=Asia/Shanghai
        username: root
        password: 123456
        maxActive: 10
        maxWait: 1000
      audit:
        driverClassName: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://127.0.0.1:3306/ssa_audit?useSSL=false&serverTimezone=Asia/Shanghai
        username: root
        password: 123456
        maxActive: 10
        maxWait: 1000
```

### 字段说明

- `ssa.sqlapi.primary`：主数据源 key，默认 `main`。
- `ssa.sqlapi.datasource`：数据源配置集合，key 作为数据源名称。
- `driverClassName`：JDBC 驱动类名。
- `url`：JDBC 连接地址。
- `username` / `password`：数据库用户名/密码。
- `maxActive`：最大连接数，未设置时默认 8。
- `maxWait`：获取连接的最大等待时间（毫秒），默认 1000。

### 行为说明

- `primary` 对应的 key 会作为主数据源返回（当需要主 `ActiveRecordPlugin` 时）。
- 若 `ssa.sqlapi.datasource` 为空，将跳过 `ActiveRecordPlugin` 初始化并记录告警日志。

## 说明接口

- 插件包内放置 `plugin-doc.md`，并在 `ssa-plugin.yml` 中设置 `docPath`。
- 启动后可通过插件管理接口获取说明内容（见 `SsaPluginManager.getPluginDoc`）。

