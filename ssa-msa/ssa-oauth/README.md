# SSA OAuth 服务

SSA OAuth 是基于 Spring Security + OAuth2.0/OIDC 构建的认证授权中心，支持通过 `sqlapi` 插件加载用户与权限数据，不依赖 ORM。

## 关键改动
- 认证与权限查询改为调用 `sqlapi`（YML 模板）
- `ssa-core` 插件机制加载 `sql-api` 插件
- `SysUser/SysRole/SysPermission` 为纯 POJO（不依赖 JPA）

## sqlapi 模板
默认使用以下 API 名称（可在 `application.yml` 覆盖）：
- `user.login`：按用户名/手机号查用户
- `user.getUserInfo`：按 `user_id` 查用户
- `user.permissions`：查询用户权限码列表
- `user.roles`：查询用户角色码列表

对应示例文件（可直接复用）：
- `sql/mysql/ymlapi/user/login.yml`
- `sql/mysql/ymlapi/user/getUserInfo.yml`
- `sql/mysql/ymlapi/user/permissions.yml`
- `sql/mysql/ymlapi/user/roles.yml`

## 配置示例
```yaml
ssa:
  plugin:
    pluginPath: D:/work/SSAuditor/ssa-service/plugins/
    plugins:
      - key: sql-api
        fileName: sql-api-2.0.0.jar
        state: enable
        type: jar
  oauth:
    sqlapi:
      user-by-identifier: user.login
      user-by-id: user.getUserInfo
      user-permissions: user.permissions
      user-roles: user.roles

sqlapi:
  datasource:
    main:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://113.44.117.125:9920/ssa
      username: root
      password: ysj@2019
  template:
    cache: 0
    path:
      - D:\work\SSAuditor\ssa-service\sql\mysql\ymlapi
```

## 认证流程说明
1. `CustomUserDetailsService` 通过 `sqlapi` 查询用户信息
2. `AuthUserAuthenticationProvider` 校验密码
3. `PermissionService` 通过 `sqlapi` 拉取用户权限/角色

## API 接口
- `POST /auth/v1/login` - 用户登录
- `POST /auth/v1/refresh` - 刷新令牌
- `POST /auth/v1/logout` - 注销
- `POST /auth/v1/validate` - 验证令牌
