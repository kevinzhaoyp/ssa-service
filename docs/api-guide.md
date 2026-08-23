# SSA Service 接口调用说明文档

> 本文档基于 `ssa-service` 后端源码分析生成，供前端团队对接使用。
> 分析日期：2026-02-XX | 源码版本：当前 main 分支

---

## 目录

1. [服务概览](#1-服务概览)
2. [认证机制](#2-认证机制)
3. [统一响应格式](#3-统一响应格式)
4. [接口清单](#4-接口清单)
   - 4.1 [认证接口（ssa-ser）](#41-认证接口ssa-ser-端口-8000)
   - 4.2 [认证接口（ssa-oauth 微服务）](#42-认证接口ssa-oauth-微服务-端口-8081)
   - 4.3 [权限校验接口](#43-权限校验接口ssa-oauth)
   - 4.4 [短信验证码接口](#44-短信验证码接口ssa-oauth)
   - 4.5 [微信扫码登录接口](#45-微信扫码登录接口ssa-ser)
   - 4.6 [动态 SQL API 接口（sql-api）](#46-动态-sql-api-接口sql-api)
   - 4.7 [文件操作接口（file-api）](#47-文件操作接口file-api)
   - 4.8 [数据导入导出接口（import-api）](#48-数据导入导出接口import-api)
   - 4.9 [AI 技能管理接口](#49-ai-技能管理接口ssa-ai-端口-9000)
5. [动态 API 调用规则](#5-动态-api-调用规则)
6. [WebSocket 协议](#6-websocket-协议)
7. [前后端对接注意事项](#7-前后端对接注意事项)

---

## 1. 服务概览

### 1.1 模块与端口

| 模块 | 服务名 | 端口 | 说明 |
|------|--------|------|------|
| **ssa-ser** | ssaApi | **8000** | 主服务，提供认证、微信扫码、动态 SQL API、文件操作、数据导入导出等接口。前端主要对接此服务。 |
| **ssa-oauth** | oauth | 8081 | OAuth2 认证微服务，提供多登录方式认证、权限校验、短信验证码。通过 Nacos 注册，可经网关访问。 |
| **ssa-gateway** | gateway | 8080 | Spring Cloud Gateway 网关，基于 Nacos 服务发现自动路由（如 `/oauth/**` → ssa-oauth）。 |
| **ssa-ser-client** | clientApi | 8000 | WebSocket 服务（STOMP + SockJS），与 ssa-ser 同端口部署。 |
| **ssa-ai** | ssa-ai | 9000 | AI 智能助手服务，提供技能管理 CRUD。 |

### 1.2 基础设施

| 组件 | 地址 |
|------|------|
| MySQL（主库） | `113.44.117.125:9920/sso` |
| Redis | `113.44.117.125:9900` |
| Nacos | `113.44.117.125:8800`（namespace: `gateway2`） |

### 1.3 前端对接说明

- **前端主要对接 ssa-ser（端口 8000）**，该服务集成了认证、动态 API、文件操作等全部核心功能。
- ssa-oauth（8081）为独立微服务，若需多登录方式（手机/微信/QQ）或权限校验，可通过网关（8080）路由访问，路径前缀 `/oauth`。
- ssa-ai（9000）为独立 AI 服务，如需 AI 技能管理功能则直连此端口。

---

## 2. 认证机制

### 2.1 认证流程（ssa-ser）

```
┌──────────┐    1.POST /auth/v1/login      ┌──────────┐
│  前端     │ ──────────────────────────▶  │ ssa-ser  │
│          │ ◀────────────────────────── │          │
│          │    2.返回 accessToken(JWT)     │          │
│          │      + refreshToken(UUID)     │          │
│          │                               │          │
│          │    3.后续请求带 Bearer Token   │          │
│          │ ──────────────────────────▶  │          │
│          │                               │          │
│          │    4.Token过期→POST /refresh  │          │
│          │ ──────────────────────────▶  │          │
│          │ ◀────────────────────────── │          │
│          │    5.返回新accessToken        │          │
│          │      +新refreshToken(旋转)    │          │
└──────────┘                               └──────────┘
```

### 2.2 JWT Token 结构

**AccessToken（JWT）** Payload 字段：

| 字段 | 说明 |
|------|------|
| `user_id` | 用户 ID |
| `username` | 用户名 |
| `roles` | 角色集合（`["ROLE_ADMIN", ...]`） |
| `iat` | 签发时间 |
| `exp` | 过期时间（Unix 秒） |
| `sub` | 主题（用户名，等同 username） |
| `jti` | JWT 唯一 ID（用于黑名单） |

- **签名密钥**：`ssaAuditor`
- **AccessToken 有效期**：720000 秒（约 8.3 天）
- **RefreshToken 有效期**：2592000 秒（30 天）

### 2.3 请求认证方式

所有需要认证的接口，请求头需携带：

```
Authorization: Bearer <accessToken>
```

### 2.4 Token 刷新机制（旋转机制）

1. 前端用 `refreshToken` 调用 `POST /auth/v1/refresh`
2. 服务端校验 Redis 中 `token:refresh_token:{refreshToken}` 是否存在
3. 校验通过后：**删除旧 refreshToken**，颁发新的 accessToken + 新 refreshToken
4. 前端需用新 refreshToken 替换旧的（旧 refresh 立即作废）

### 2.5 登出黑名单机制

1. 前端调用 `POST /auth/v1/logout`（需带 Bearer Token）
2. 服务端解析 Token 的 `jti`（JWT ID）
3. 将 `token:blacklist:{jti}` 写入 Redis，TTL = Token 剩余有效时间
4. 后续请求携带该 Token 时，JwtValidationFilter 检查黑名单，命中则返回 401

### 2.6 免认证路径

以下路径不需要认证（`SecurityConfig` 中 `permitAll`）：

| 路径 | 说明 |
|------|------|
| `/auth/v1/login` | 登录 |
| `/auth/v1/refresh` | 刷新 Token |

> ⚠️ **注意**：`security.ignore-urls` 配置为空，除上述两个路径外，**所有其他路径均需认证**。

### 2.7 JWT 校验过滤器逻辑（JwtValidationFilter）

```
请求到达
  │
  ├─ 无 Authorization 头 → 继续过滤链（后续 Security 判断是否需要认证）
  │
  └─ 有 Bearer Token
       ├─ 解析 + 验签 + 检查过期 → 失败 → 返回 401 (TOKEN_INVALID)
       ├─ 检查 Redis 黑名单 (token:blacklist:{jti}) → 命中 → 返回 401
       └─ 有效 → 设置 SecurityContext → 继续过滤链
```

---

## 3. 统一响应格式

### 3.1 ApiResult 结构

> ⚠️ **重要**：ssa-ser 主服务的接口统一使用 `ApiResult<T>` 包装返回；但 **ssa-oauth 微服务的接口不使用 ApiResult 包装**，直接返回业务对象。详见各接口说明。

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `ok` | boolean | `code == 0` 时为 `true` |
| `code` | int | 状态码，`0` 表示成功 |
| `msg` | string | 消息描述 |
| `data` | T | 业务数据 |

### 3.2 错误码表

| 错误码 | 枚举名 | 说明 |
|--------|--------|------|
| **0** | OK | 成功 |
| 101 | TOKEN_ACCESS_FORBIDDEN | token 已被禁止访问 |
| 102 | RESOURCE_NOT_FOUND | 请求资源不存在 |
| 103 | USERNAME_OR_PASSWORD_ERROR | 用户名或密码错误 |
| 104 | PASSWORD_ENTER_EXCEED_LIMIT | 用户输入密码次数超限 |
| 105 | ACCESS_UNAUTHORIZED | 访问未授权 |
| 201 | NOT_FOUND | 接口未实现 |
| 202 | ERROR_RETURN_TYPE | ReturnType 返回类型未实现 |
| 203 | ERROR_SQL | SQL 语句执行错误 |
| 204 | NOT_DBNAME | 需要指定数据库名：dbname |
| 205 | NOT_API_NAME | 缺少参数 name |
| 206 | ERROR_API_CLASS | 自定义 API 类错误 |
| 207 | NOT_DS_NAME | 未找到数据源 |
| 300 | API_DISABLED | 接口被禁用 |
| 301 | NOT_API_METHOD | 未找到方法 |
| **401** | TOKEN_INVALID | token 无效或已过期 |
| **50000** | FAILED | 通用失败 |

---

## 4. 接口清单

### 4.1 认证接口（ssa-ser 端口 8000）

> 控制器：`com.ssa.apis.controller.AuthV1`
> 基础路径：`/auth/v1`
> 响应格式：统一使用 `ApiResult<T>` 包装

#### 4.1.1 用户登录

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/login` |
| **认证** | ❌ 不需要 |
| **说明** | 账号密码登录，返回 accessToken + refreshToken |

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | 是 | 用户名（会自动转小写并 trim） |
| `password` | string | 是 | 密码 |

```json
{
  "username": "admin",
  "password": "123456"
}
```

**成功响应：**

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "refreshToken": "a1b2c3d4e5f6...",
    "expires": 1735689600,
    "firstLogin": null
  }
}
```

**LoginResult 字段说明（ssa-ser 版）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | string | JWT 访问令牌 |
| `tokenType` | string | 固定 `"Bearer"` |
| `refreshToken` | string | 刷新令牌（UUID），用于获取新 accessToken |
| `expires` | long | Token 过期时间（Unix 秒级时间戳） |
| `firstLogin` | boolean | 是否首次登录（当前实现中未赋值，可能为 null） |

**失败响应示例：**

```json
{
  "ok": false,
  "code": 50000,
  "msg": "用户名或密码错误",
  "data": null
}
```

> ⚠️ 登录失败时 `code` 为 50000（FAILED），`msg` 为异常消息。认证失败会触发 `AuthenticationException`，具体消息取决于 Spring Security 配置。

---

#### 4.1.2 刷新 Token

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/refresh` |
| **认证** | ❌ 不需要 |
| **说明** | 使用 refreshToken 获取新的 accessToken + refreshToken（旋转机制，旧 refresh 立即作废） |

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `refreshToken` | string | 是 | 登录时获取的 refreshToken |

```json
{
  "refreshToken": "a1b2c3d4e5f6..."
}
```

**成功响应：**

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": {
    "accessToken": "eyJhbGciOi...(新Token)",
    "tokenType": "Bearer",
    "refreshToken": "f6e5d4c3b2a1...(新refreshToken)",
    "expires": 1735689600,
    "firstLogin": null
  }
}
```

**失败响应：** refreshToken 无效时抛出异常，返回 `ApiResult.failed(ex.getMessage())`。

---

#### 4.1.3 获取当前用户信息

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/getUserInfo` |
| **认证** | ✅ 需要 |
| **说明** | 从 JWT 中提取 userId，内部调用动态 API `user_getUserInfo` 查询用户信息 |

**请求参数：** 无（userId 从 Token 中自动提取）

**响应：** 返回 `ApiResult<Object>`，data 为用户信息记录。

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": {
    "user_id": "admin",
    "username": "admin",
    "nickname": "管理员",
    "mobile": "13800138000",
    "status": 1
  }
}
```

> ⚠️ **已知问题**：代码中调用 `ApiRunner.safeRun("user_getUserInfo", ...)`，但 YML 文件名为 `user/getUserInfo.yml`。命名可能不匹配（下划线 vs 斜杠），需后端确认 API 加载器是否做了名称转换。详见 [注意事项 7.1](#71-接口命名不一致)。

---

#### 4.1.4 用户登出

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/logout` |
| **认证** | ✅ 需要 |
| **说明** | 将当前 Token 的 jti 加入 Redis 黑名单，清空 Security 上下文 |

**请求参数：** 无（从请求头 `Authorization` 中提取 Token）

**响应：**

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": "ok"
}
```

---

### 4.2 认证接口（ssa-oauth 微服务 端口 8081）

> 控制器：`com.ssa.oauth.controller.AuthController`
> 基础路径：`/auth/v1`
> 响应格式：⚠️ **不使用 ApiResult 包装**，直接返回业务对象（`ResponseEntity<LoginResult>`）
> 访问方式：直连 `http://host:8081/auth/v1/*` 或经网关 `http://host:8080/oauth/auth/v1/*`

#### 4.2.1 多方式登录

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/login` |
| **认证** | ❌ 不需要 |
| **说明** | 支持密码、手机验证码、微信、QQ 多种登录方式 |

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `loginType` | string | 是 | 登录类型：`password` / `phone` / `wechat` / `qq` |
| `identifier` | string | 视情况 | 用户名/手机号/邮箱（password/phone 登录时使用） |
| `password` | string | 视情况 | 密码（password 登录时使用） |
| `code` | string | 视情况 | 验证码（phone 登录时使用） |
| `thirdPartyCode` | string | 视情况 | 第三方登录 code（wechat/qq 登录时使用） |
| `clientId` | string | 否 | 客户端 ID |
| `clientSecret` | string | 否 | 客户端密钥 |
| `captcha` | string | 否 | 图形验证码 |
| `captchaKey` | string | 否 | 图形验证码 KEY |

**密码登录示例：**

```json
{
  "loginType": "password",
  "identifier": "admin",
  "password": "123456",
  "clientId": "web_client"
}
```

**手机验证码登录示例：**

```json
{
  "loginType": "phone",
  "identifier": "13800138000",
  "code": "123456"
}
```

**成功响应（直接返回 LoginResult，无 ApiResult 包装）：**

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "a1b2c3d4e5f6...",
  "expires": 7200,
  "userInfo": {
    "userId": "1",
    "username": "admin",
    "nickName": "管理员",
    "realName": "系统管理员",
    "phone": "13800138000",
    "email": "admin@example.com",
    "avatar": "https://...",
    "gender": "M"
  },
  "needBindPhone": false,
  "firstLogin": false
}
```

**LoginResult 字段说明（ssa-oauth 版）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `tokenType` | string | 固定 `"Bearer"` |
| `accessToken` | string | JWT 访问令牌 |
| `refreshToken` | string | 刷新令牌 |
| `expires` | long | 过期时间（秒，注意：此服务中为有效期秒数如 7200，与 ssa-ser 的 Unix 时间戳不同） |
| `userInfo` | object | 用户信息（见下表） |
| `needBindPhone` | boolean | 是否需要绑定手机号 |
| `firstLogin` | boolean | 是否首次登录 |

**UserInfo 字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | string | 用户 ID |
| `username` | string | 用户名 |
| `nickName` | string | 昵称 |
| `realName` | string | 真实姓名 |
| `phone` | string | 手机号 |
| `email` | string | 邮箱 |
| `avatar` | string | 头像 URL |
| `gender` | string | 性别 |

> ⚠️ **与 ssa-ser 的差异**：ssa-oauth 的 `expires` 为有效期秒数（如 7200），ssa-ser 的 `expires` 为 Unix 时间戳。详见 [注意事项 7.2](#72-两套认证服务的差异)。

---

#### 4.2.2 刷新 Token

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/refresh` |
| **认证** | ❌ 不需要 |
| **说明** | 使用 refreshToken 获取新令牌 |

**请求参数：**

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `refreshToken` | Query Param | string | 是 | 刷新令牌 |

```
POST /auth/v1/refresh?refreshToken=a1b2c3d4e5f6...
```

**响应：** 直接返回 `LoginResult`（同登录响应结构）。

> ⚠️ **与 ssa-ser 的差异**：ssa-oauth 的 refresh 参数为 **Query Param**（`@RequestParam`），ssa-ser 的为 **JSON Body**。

---

#### 4.2.3 用户登出

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/logout` |
| **认证** | ✅ 需要 |
| **说明** | 将 Token 加入黑名单 |

**请求头：**

```
Authorization: Bearer <accessToken>
```

**响应：** `200 OK`，无响应体（`ResponseEntity<Void>`）。

> ⚠️ **与 ssa-ser 的差异**：ssa-oauth 的 logout 需在请求头带 Token（`@RequestHeader("Authorization")`），ssa-ser 的 logout 从 SecurityContext 自动获取。

---

#### 4.2.4 验证令牌

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/validate` |
| **认证** | ❌ 不需要（此接口本身用于校验 Token） |
| **说明** | 验证指定 Token 是否有效 |

**请求参数：**

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `token` | Query Param | string | 是 | 待验证的 accessToken |

```
POST /auth/v1/validate?token=eyJhbGciOi...
```

**响应：** 直接返回 `boolean`（`true` 有效 / `false` 无效）。

---

### 4.3 权限校验接口（ssa-oauth）

> 控制器：`com.ssa.oauth.controller.PermissionController`
> 基础路径：`/auth/v1/permission`
> 响应格式：直接返回业务对象（无 ApiResult 包装）
> 说明：主要供微服务内部调用做权限兜底校验，前端一般不直接调用。

#### 4.3.1 检查单个权限

| 项目 | 内容 |
|------|------|
| **URL** | `GET /auth/v1/permission/check` |
| **认证** | 视网关配置 |

**请求参数（Query Param）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | string | 是 | 用户 ID |
| `permissionCode` | string | 是 | 权限编码 |

```
GET /auth/v1/permission/check?userId=1&permissionCode=user:add
```

**响应：** `boolean`

---

#### 4.3.2 检查任意权限（OR）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/permission/check-any` |
| **认证** | 视网关配置 |

**请求参数：**

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `userId` | Query Param | string | 是 | 用户 ID |
| - | Body | string[] | 是 | 权限编码列表（JSON 数组） |

```
POST /auth/v1/permission/check-any?userId=1
Body: ["user:add", "user:edit", "user:delete"]
```

**响应：** `boolean`（拥有任意一个权限即为 `true`）

---

#### 4.3.3 检查全部权限（AND）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/permission/check-all` |
| **认证** | 视网关配置 |

**请求参数：**

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `userId` | Query Param | string | 是 | 用户 ID |
| - | Body | string[] | 是 | 权限编码列表（JSON 数组） |

```
POST /auth/v1/permission/check-all?userId=1
Body: ["user:add", "user:edit"]
```

**响应：** `boolean`（拥有全部权限才为 `true`）

---

#### 4.3.4 获取用户所有权限

| 项目 | 内容 |
|------|------|
| **URL** | `GET /auth/v1/permission/user/{userId}/permissions` |
| **认证** | 视网关配置 |

**路径参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | string | 是 | 用户 ID |

```
GET /auth/v1/permission/user/1/permissions
```

**响应：** `string[]`（权限编码列表）

```json
["user:add", "user:edit", "user:delete", "system:config"]
```

---

#### 4.3.5 检查角色

| 项目 | 内容 |
|------|------|
| **URL** | `GET /auth/v1/permission/role/check` |
| **认证** | 视网关配置 |

**请求参数（Query Param）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | string | 是 | 用户 ID |
| `roleCode` | string | 是 | 角色编码 |

```
GET /auth/v1/permission/role/check?userId=1&roleCode=ADMIN
```

**响应：** `boolean`

---

### 4.4 短信验证码接口（ssa-oauth）

> 控制器：`com.ssa.oauth.controller.SmsCodeController`
> 基础路径：`/auth/v1/sms`

#### 4.4.1 发送验证码

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/sms/send` |
| **认证** | ❌ 不需要 |

**请求参数（Query Param）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phone` | string | 是 | 手机号 |

```
POST /auth/v1/sms/send?phone=13800138000
```

**响应：** `boolean`（发送成功为 `true`）

---

#### 4.4.2 验证验证码

| 项目 | 内容 |
|------|------|
| **URL** | `POST /auth/v1/sms/verify` |
| **认证** | ❌ 不需要 |

**请求参数（Query Param）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `phone` | string | 是 | 手机号 |
| `code` | string | 是 | 验证码 |

```
POST /auth/v1/sms/verify?phone=13800138000&code=123456
```

**响应：** `boolean`（验证通过为 `true`）

---

### 4.5 微信扫码登录接口（ssa-ser）

> 控制器：`com.ssa.apis.wechat.WechatController`
> 基础路径：`/wechat`
> 响应格式：直接返回 Map（无 ApiResult 包装）
> 说明：当前为 Demo 实现，qrUrl 为模拟链接，非真实微信 OAuth。

#### 4.5.1 获取二维码

| 项目 | 内容 |
|------|------|
| **URL** | `GET /wechat/qr` |
| **认证** | 视配置（默认需要，因 /wechat 不在免认证路径中） |

**请求参数：** 无

**响应：**

```json
{
  "ticket": "a1b2c3d4e5f6...",
  "qrUrl": "weixin://scan?ticket=a1b2c3d4e5f6..."
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `ticket` | string | 扫码票据，用于后续状态轮询 |
| `qrUrl` | string | 二维码内容（Demo 模拟链接，生产环境应替换为真实微信 OAuth URL） |

---

#### 4.5.2 查询扫码状态

| 项目 | 内容 |
|------|------|
| **URL** | `GET /wechat/qr/status` |
| **认证** | 视配置 |

**请求参数（Query Param）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ticket` | string | 是 | 扫码票据 |

```
GET /wechat/qr/status?ticket=a1b2c3d4e5f6...
```

**响应：**

```json
{
  "logged": false
}
```

> 前端通过轮询此接口判断用户是否已扫码确认。

---

#### 4.5.3 模拟扫码（Demo）

| 项目 | 内容 |
|------|------|
| **URL** | `GET /wechat/qr/simulate` |
| **认证** | 视配置 |
| **说明** | 模拟用户扫码，将 ticket 标记为已登录。仅供开发测试使用。 |

**请求参数（Query Param）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ticket` | string | 是 | 扫码票据 |

**响应：**

```json
{
  "ticket": "a1b2c3d4e5f6...",
  "marked": true,
  "logged": true
}
```

---

#### 4.5.4 模拟登录（Demo）

| 项目 | 内容 |
|------|------|
| **URL** | `GET /wechat/qr/simulateLogin` |
| **认证** | 视配置 |
| **说明** | 模拟扫码登录，创建测试会话并设置 HttpOnly Cookie。仅供开发测试使用。 |

**请求参数（Query Param）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `ticket` | string | 是 | 扫码票据 |

**响应：**

```json
{
  "ticket": "a1b2c3d4e5f6...",
  "marked": true,
  "logged": true,
  "user": "testuser"
}
```

同时设置 Cookie：`SSA_TEST_AUTH={ticket}`（HttpOnly，有效期 1 小时）。

---

#### 4.5.5 获取当前登录用户（测试接口）

> 控制器：`com.ssa.apis.auth.AuthTestController`
> 基础路径：`/api`

| 项目 | 内容 |
|------|------|
| **URL** | `GET /api/me` |
| **认证** | 基于 Cookie（SSA_TEST_AUTH），非 JWT |
| **说明** | 测试接口，通过 Cookie 中的 ticket 获取模拟登录用户信息 |

**响应（已登录）：**

```json
{
  "username": "testuser",
  "ticket": "a1b2c3d4e5f6..."
}
```

**响应（未登录）：** HTTP 401

```json
{
  "error": "unauthenticated"
}
```

---

### 4.6 动态 SQL API 接口（sql-api）

> 控制器：`com.ssa.plugin.sqlApi.controller.SqlApiV1`
> 基础路径：`/v1`
> 响应格式：统一使用 `ApiResult<T>` 包装
> 说明：基于 YML 配置文件的动态 SQL 模板引擎，前端通过 API 名称调用预定义的 SQL 查询。

#### 4.6.1 健康检查

| 项目 | 内容 |
|------|------|
| **URL** | `GET /v1/index` |
| **认证** | ✅ 需要 |

**响应（纯文本）：**

```
SqlApi V1 服务已启动
```

---

#### 4.6.2 通用数据查询（按名称调用）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /v1/getData` |
| **认证** | ✅ 需要 |
| **说明** | 通过 `name` 字段指定要调用的 API，支持 `reload` 参数强制重新加载 YML 配置 |

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | API 名称（对应 YML 文件名，如 `user.getUserInfo`、`user/getUserInfo`） |
| `params` | object | 否 | SQL 参数键值对 |
| `reload` | boolean | 否 | 是否重新加载 API 配置（默认 false） |

```json
{
  "name": "user/getUserInfo",
  "params": {
    "user_id": "admin"
  },
  "reload": false
}
```

**响应：** `ApiResult<Object>`，data 取决于 YML 中 `returnType` 配置。

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": {
    "user_id": "admin",
    "username": "admin",
    "nickname": "管理员"
  }
}
```

---

#### 4.6.3 按路径直接调用（单级）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /v1/{apiName}` |
| **认证** | ✅ 需要 |
| **说明** | URL 路径中的 `apiName` 直接作为 API 名称，Body 作为参数 |

**请求参数：**

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `apiName` | Path | string | 是 | API 名称（如 `pro.getUserInfo`、`test.test`） |
| - | Body | object | 否 | SQL 参数键值对 |

```
POST /v1/pro.getUserInfo
Body: { "user_id": "admin" }
```

**响应：** `ApiResult<Object>`

---

#### 4.6.4 按路径直接调用（两级）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /v1/{path}/{apiName}` |
| **认证** | ✅ 需要 |
| **说明** | 两级路径拼接为 `path/apiName` 作为 API 名称 |

**请求参数：**

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `path` | Path | string | 是 | 路径前缀（如 `user`） |
| `apiName` | Path | string | 是 | API 名称（如 `getUserInfo`） |
| - | Body | object | 否 | SQL 参数键值对 |

```
POST /v1/user/getUserInfo
Body: { "user_id": "admin" }
```

> 实际调用的 API 名称为 `user/getUserInfo`（用 `/` 拼接）。

**响应：** `ApiResult<Object>`

---

#### 4.6.5 批量调用多个 API

| 项目 | 内容 |
|------|------|
| **URL** | `POST /v1/getApisData` |
| **认证** | ✅ 需要 |
| **说明** | 一次请求调用多个 API，共享参数 + 各 API 独立参数 |

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `params` | object | 否 | 共享参数（所有 API 都会接收） |
| `apis` | array | 是 | API 列表，每项含 `name` 和 `params` |
| `reload` | boolean | 否 | 是否重新加载（默认 false） |

`apis` 数组每项结构：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | API 名称 |
| `params` | object | 否 | 该 API 独有参数（会与共享 params 合并，独立参数优先） |

```json
{
  "params": {
    "user_id": "admin"
  },
  "apis": [
    { "name": "user/getUserInfo", "params": {} },
    { "name": "pro.getUserInfo", "params": { "user_id": "zhangsan" } }
  ],
  "reload": false
}
```

**响应：** `ApiResult<Object>`，data 为对象，key 为 API 名称，value 为该 API 的 `ApiResult`。

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": {
    "user/getUserInfo": {
      "ok": true,
      "code": 0,
      "msg": "success",
      "data": { "user_id": "admin", "username": "admin" }
    },
    "pro.getUserInfo": {
      "ok": true,
      "code": 0,
      "msg": "success",
      "data": { "username": "zhangsan" }
    }
  }
}
```

---

### 4.7 文件操作接口（file-api）

> 控制器：`com.ssa.plugin.fileApi.controller.FileApiController`
> 基础路径：`/fileApi/v1`
> 响应格式：统一使用 `ApiResult<T>` 包装
> 说明：基于 YML 配置的文件上传/下载 API，通过 API 名称区分不同文件操作。

#### 4.7.1 文件操作（单级路径）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /fileApi/v1/{apiName}`（支持所有 HTTP 方法） |
| **认证** | ✅ 需要 |
| **说明** | 根据 apiName 加载对应的 FileApi YML 配置，执行上传或下载操作 |

**请求参数：**

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `apiName` | Path | string | 是 | 文件 API 名称 |
| - | Form/Query | mixed | 视配置 | 文件（multipart）或查询参数，取决于 YML 中 `params` 定义 |

**文件上传示例：**

```
POST /fileApi/v1/upload
Content-Type: multipart/form-data

file: <二进制文件>
businessType: audit_papers
```

**文件下载示例：**

```
POST /fileApi/v1/download?fileId=123456
```

**响应（上传）：**

```json
{
  "ok": true,
  "code": 0,
  "msg": "success",
  "data": { "fileId": "123456", "fileName": "report.pdf" }
}
```

**响应（下载）：** 直接写入 HttpServletResponse 流（文件二进制内容），不返回 JSON。

> ⚠️ 当前代码库中未发现 file-api 的 YML 配置文件，具体可用的 apiName 需后端补充配置后确认。

---

#### 4.7.2 文件操作（两级路径）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /fileApi/v1/{path1}/{apiName}`（支持所有 HTTP 方法） |
| **认证** | ✅ 需要 |
| **说明** | 两级路径拼接为 `path1.apiName`（注意：file-api 用 `.` 拼接，sql-api 用 `/` 拼接） |

```
POST /fileApi/v1/audit/upload
```

实际调用的 API 名称为 `audit.upload`。

---

### 4.8 数据导入导出接口（import-api）

> 控制器：`com.ssa.plugin.importApi.controller.ImportApiController`
> 基础路径：`/importApi/v1`
> 响应格式：统一使用 `ApiResult<T>` 包装
> 说明：基于 YML 配置的 Excel 导入导出 API。当前仅支持 Excel 格式。

#### 4.8.1 数据导入导出（单级路径）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /importApi/v1/{apiName}`（支持所有 HTTP 方法） |
| **认证** | ✅ 需要 |
| **说明** | 根据 apiName 加载对应的 ImportApi YML 配置，执行 Excel 导入或导出 |

**导入示例：**

```
POST /importApi/v1/importUsers
Content-Type: multipart/form-data

file: <Excel 文件>
```

**导出示例：**

```
POST /importApi/v1/exportUsers
Body: { "deptId": "001" }
```

> ⚠️ 当前代码库中未发现 import-api 的 YML 配置文件，具体可用的 apiName 需后端补充配置后确认。

---

#### 4.8.2 数据导入导出（两级路径）

| 项目 | 内容 |
|------|------|
| **URL** | `POST /importApi/v1/{path1}/{apiName}`（支持所有 HTTP 方法） |
| **认证** | ✅ 需要 |
| **说明** | 两级路径拼接为 `path1.apiName`（用 `.` 拼接） |

```
POST /importApi/v1/user/import
```

实际调用的 API 名称为 `user.import`。

---

### 4.9 AI 技能管理接口（ssa-ai 端口 9000）

> 控制器：`com.ssa.ai.web.SkillsController`
> 基础路径：`/api/skills`
> 响应格式：直接返回业务对象（无 ApiResult 包装）

#### 4.9.1 获取技能列表

| 项目 | 内容 |
|------|------|
| **URL** | `GET /api/skills` |
| **认证** | 视配置 |

**响应：** `Skill[]`

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "数据清洗",
    "description": "自动清洗审计数据",
    "enabled": true,
    "metadata": { "category": "data" },
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  }
]
```

---

#### 4.9.2 获取单个技能

| 项目 | 内容 |
|------|------|
| **URL** | `GET /api/skills/{id}` |
| **认证** | 视配置 |

**路径参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 技能 ID（UUID） |

**响应：** `Skill`（不存在则返回 404）

---

#### 4.9.3 创建技能

| 项目 | 内容 |
|------|------|
| **URL** | `POST /api/skills` |
| **认证** | 视配置 |

**请求参数（JSON Body）：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 技能名称 |
| `description` | string | 否 | 技能描述 |
| `enabled` | boolean | 否 | 是否启用（默认 true） |
| `metadata` | object | 否 | 元数据键值对 |

```json
{
  "name": "数据清洗",
  "description": "自动清洗审计数据",
  "metadata": { "category": "data" }
}
```

**响应：** HTTP 201，返回创建的 `Skill`（含自动生成的 id、createdAt、updatedAt）。

---

#### 4.9.4 更新技能

| 项目 | 内容 |
|------|------|
| **URL** | `PUT /api/skills/{id}` |
| **认证** | 视配置 |

**请求参数：** 同创建，但 `id` 在路径中指定。Body 传入完整的 Skill 对象。

**响应：** `Skill`（更新后的对象）

---

#### 4.9.5 删除技能

| 项目 | 内容 |
|------|------|
| **URL** | `DELETE /api/skills/{id}` |
| **认证** | 视配置 |

**响应：** HTTP 204（无响应体）

---

### Skill 数据模型

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 技能 ID（UUID，自动生成） |
| `name` | string | 技能名称 |
| `description` | string | 技能描述 |
| `enabled` | boolean | 是否启用（默认 true） |
| `metadata` | map<string,string> | 元数据键值对 |
| `createdAt` | Instant | 创建时间 |
| `updatedAt` | Instant | 更新时间 |

---

## 5. 动态 API 调用规则

### 5.1 YML API 文件结构

动态 SQL API 通过 YML 文件定义，文件位于 `plugins/ymlapi/` 目录。文件名即 API 名称。

**YML 文件字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | string | 是 | 版本号（当前 `2.0`） |
| `returnType` | string | 是 | 返回类型：`OBJECT`（单条）、`LIST`（列表）、`INT`（影响行数） |
| `datasource` | string | 否 | 数据源名称（`~` 表示默认主库） |
| `describe` | string | 否 | 接口描述 |
| `params2` | list | 否 | 参数定义列表（`List<ApiParam>`），YML 键名必须为 `params2` |
| `requireAuth` | boolean | 否 | 是否需要认证（默认 false） |
| `content` | string | 是 | SQL 模板内容（使用 `#para(参数名)` 占位符） |
| `onBefore` | list | 否 | 前置事件（如数据校验） |
| `onSuccess` | list | 否 | 成功后事件 |
| `onError` | list | 否 | 失败后事件 |

#### ApiParam 参数定义结构

`params2` 列表中每个元素是一个 `ApiParam` 对象，Java 类定义如下：

```java
public class ApiParam {
    private String name;       // 参数名
    private String type;       // 参数类型，目前支持 string, int
    private Object defValue;   // 默认值，在事件里可以设置提取值
    private boolean required;  // 是否必填
    private String hint;       // 校验不通过时的提示信息
}
```

**YML 写法示例：**

```yaml
params2:
  - name: user_id
    type: string
    required: true
    hint: 用户ID不能为空
  - name: page_num
    type: int
    required: false
    defValue: 1
    hint: 页码，从1开始
```

> ⚠️ **关键注意事项：**
> 1. **YML 键名必须为 `params2`**，不是 `params`。`SqlApi` 类中 `params` 是已废弃的 `String[]` 字段，`params2` 才是 `List<ApiParam>`。SnakeYAML 按字段名直接映射，写错则参数定义完全不生效。
> 2. **字段名必须与 Java 类完全一致**：`defValue`（不是 `defaultValue`）、`hint`（不是 `describe`）。SnakeYAML 无自定义别名，字段名不匹配则静默忽略。
> 3. **`type` 只支持小写** `string` 和 `int`，不支持 `STRING`/`INTEGER` 等大写形式。
> 4. 参数校验逻辑（`SqlApiParamUtils.checkParams`）：必填参数为空时，若有 `defValue` 则使用默认值，否则抛出 `hint` 提示信息。

### 5.2 API 命名规则

API 名称由 YML 文件相对路径决定，支持两种分隔符：

| 文件路径 | API 名称 | 调用方式 |
|----------|----------|----------|
| `user/getUserInfo.yml` | `user/getUserInfo` | `POST /v1/user/getUserInfo` 或 `POST /v1/getData` (name=`user/getUserInfo`) |
| `pro.getUserInfo.yml` | `pro.getUserInfo` | `POST /v1/pro.getUserInfo` 或 `POST /v1/getData` (name=`pro.getUserInfo`) |
| `oauth/client/list.yml` | `oauth/client/list` | `POST /v1/oauth/client/list`（三级路径不支持，需用 getData） |
| `oauth/client/add.yml` | `oauth/client/add` | 同上 |
| `test.test.yml` | `test.test` | `POST /v1/test.test` 或 `POST /v1/getData` (name=`test.test`) |

> ⚠️ 两级路径模式 `POST /v1/{path}/{apiName}` 只支持两级，三级路径（如 `oauth/client/list`）只能通过 `POST /v1/getData` 传 `name` 调用。

### 5.3 已注册的 YML API 清单

| API 名称 | returnType | requireAuth | 说明 |
|----------|------------|-------------|------|
| `user/getUserInfo` | OBJECT | false | 按 `user_id` 查用户信息（`select * from sys_user where user_id = #para(user_id)`） |
| `user/login` | OBJECT | false | 登录查询，按 username 或 mobile 查用户（对接 Security） |
| `pro.getUserInfo` | OBJECT | - | 按用户名查用户信息（`select * from sys_user where username = #para(user_id)`） |
| `test.test` | LIST | false | 测试接口（含前置校验事件），查 project 表 |
| `oauth/client/list` | LIST | false | 获取 OAuth2 客户端列表 |
| `oauth/client/add` | INT | true | 添加 OAuth2 客户端 |
| `oauth/client.add` | INT | true | 添加 OAuth2 客户端（同级路径版本，与 `oauth/client/add` 功能相同） |

### 5.4 SQL 模板语法

使用 JFinal SQL 模板引擎，参数通过 `#para(参数名)` 占位：

```sql
-- 简单查询
select * from sys_user where user_id = #para(user_id)

-- 多参数
select * from sys_user where username = #para(username) or mobile = #para(username)

-- 条件分支（使用模板指令）
select * from project
#if(cond(projectName))
where project_name like concat('%', #para(projectName), '%')
#end
```

### 5.5 调用方式对比

| 调用方式 | URL | 参数位置 | 适用场景 |
|----------|-----|----------|----------|
| 通用查询 | `POST /v1/getData` | Body `{name, params, reload}` | 任意 API，特别是三级路径 API |
| 单级路径 | `POST /v1/{apiName}` | Body（params 对象） | API 名不含 `/` 的场景 |
| 两级路径 | `POST /v1/{path}/{apiName}` | Body（params 对象） | API 名含一级 `/` 的场景 |
| 批量调用 | `POST /v1/getApisData` | Body `{params, apis[], reload}` | 需一次请求多个 API |

### 5.6 file-api / import-api 命名规则差异

| 插件 | 两级路径拼接符 | 示例 |
|------|---------------|------|
| sql-api | `/`（斜杠） | `POST /v1/user/getUserInfo` → `user/getUserInfo` |
| file-api | `.`（点号） | `POST /fileApi/v1/audit/upload` → `audit.upload` |
| import-api | `.`（点号） | `POST /importApi/v1/user/import` → `user.import` |

---

## 6. WebSocket 协议

> 服务：ssa-ser-client（端口 8000）
> 配置类：`com.ssa.webSocket.config.WebSocketConfig`

### 6.1 连接方式

| 项目 | 内容 |
|------|------|
| **端点** | `ws://host:8000/ws` |
| **协议** | STOMP over WebSocket |
| **兼容** | SockJS（浏览器不支持 WebSocket 时自动降级） |
| **跨域** | 允许所有来源（`*`） |

**连接时认证：** 在 STOMP CONNECT 帧的 Header 中传入 `username` 字段：

```javascript
// 前端示例（使用 SockJS + Stomp.js）
const socket = new SockJS('http://host:8000/ws');
const stompClient = Stomp.over(socket);
stompClient.connect(
  { username: 'admin' },  // 在 header 中传入用户名
  function(frame) {
    console.log('Connected: ' + frame);
  }
);
```

> ⚠️ 当前 WebSocket 认证仅基于 `username` Header，**无 JWT 校验**。生产环境需补充 Token 校验逻辑。

### 6.2 消息代理配置

| 前缀 | 用途 |
|------|------|
| `/app` | 客户端发送消息的目标前缀（发送到服务端 @MessageMapping） |
| `/topic` | 广播消息订阅前缀（一对多） |
| `/queue` | 点对点消息订阅前缀（一对一） |
| `/group` | 群组消息订阅前缀 |
| `/user` | 服务端推送给指定用户的前缀（默认前缀） |

### 6.3 消息接口

#### 6.3.1 广播消息

| 项目 | 内容 |
|------|------|
| **客户端发送** | `SEND /app/sendToAll` |
| **消息体** | 纯文本字符串 |
| **服务端订阅** | `/topic/notice` |

```javascript
// 发送
stompClient.send("/app/sendToAll", {}, "Hello Everyone");

// 订阅
stompClient.subscribe("/topic/notice", function(message) {
  console.log("收到广播: " + message.body);
  // message.body = "服务端通知: Hello Everyone"
});
```

**服务端返回格式：** `"服务端通知: " + 原始消息内容`

---

#### 6.3.2 点对点消息

| 项目 | 内容 |
|------|------|
| **客户端发送** | `SEND /app/sendToUser/{username}` |
| **消息体** | 纯文本字符串 |
| **接收方订阅** | `/user/{username}/queue/greeting` |

```javascript
// 发送者：给李四发消息
stompClient.send("/app/sendToUser/lisi", {}, "你好李四");

// 接收者（李四）订阅自己的消息队列
stompClient.subscribe("/user/lisi/queue/greeting", function(message) {
  const chatMessage = JSON.parse(message.body);
  console.log("收到来自 " + chatMessage.sender + " 的消息: " + chatMessage.content);
});
```

**服务端推送的消息体格式（ChatMessage）：**

```json
{
  "sender": "zhangsan",
  "content": "你好李四"
}
```

---

#### 6.3.3 群组消息

| 项目 | 内容 |
|------|------|
| **客户端发送** | `SEND /app/sendToGroup/{groupID}` |
| **消息体** | 纯文本字符串 |
| **接收方订阅** | `/user/{groupID}/group/greeting` |

```javascript
// 发送到群组
stompClient.send("/app/sendToGroup/dept001", {}, "群组通知");

// 群组成员订阅
stompClient.subscribe("/user/dept001/group/greeting", function(message) {
  const chatMessage = JSON.parse(message.body);
  console.log("收到群组消息 from " + chatMessage.sender + ": " + chatMessage.content);
});
```

**ChatMessage 数据模型：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `sender` | string | 发送者用户名 |
| `content` | string | 消息内容 |

---

## 7. 前后端对接注意事项

### 7.1 接口命名不一致

| 问题 | 详情 | 影响 |
|------|------|------|
| `getUserInfo` API 名称不匹配 | `AuthV1.getUserInfo()` 调用 `ApiRunner.safeRun("user_getUserInfo", ...)`（下划线），但 YML 文件名为 `user/getUserInfo.yml`（斜杠） | `POST /auth/v1/getUserInfo` 可能返回「接口未实现」错误 |
| `oauth/client/add` 重复定义 | `oauth/client/add.yml` 和 `oauth/client.add.yml` 两个文件功能完全相同 | 两级路径调用 `POST /v1/oauth/client/add` 和单级路径 `POST /v1/oauth.client.add` 均可，但存在冗余 |

**建议**：后端确认 API 加载器（`ApiLoaderYml`）是否对名称做了下划线/斜杠转换处理。若无，需修复 `AuthV1` 中的调用名称为 `user/getUserInfo`。

---

### 7.2 两套认证服务的差异

系统中存在两套独立的认证服务，前端需明确对接哪一套：

| 对比项 | ssa-ser（端口 8000） | ssa-oauth（端口 8081） |
|--------|---------------------|----------------------|
| **控制器** | `AuthV1` | `AuthController` |
| **登录方式** | 仅账号密码 | 密码/手机/微信/QQ 多方式 |
| **响应格式** | `ApiResult<LoginResult>` 包装 | 直接返回 `LoginResult`（无包装） |
| **LoginResult 字段** | accessToken, tokenType, refreshToken, expires, firstLogin | accessToken, tokenType, refreshToken, expires, **userInfo**, **needBindPhone**, firstLogin |
| **expires 含义** | Unix 时间戳（秒） | 有效期秒数（如 7200） |
| **refresh 参数** | JSON Body `{ "refreshToken": "..." }` | Query Param `?refreshToken=...` |
| **logout 参数** | 无参数（从 SecurityContext 获取） | 请求头 `Authorization: Bearer ...` |
| **Token 有效期** | 720000 秒（~8.3 天） | 7200 秒（2 小时） |
| **Refresh 有效期** | 2592000 秒（30 天） | 604800 秒（7 天） |

**建议**：前端需根据实际使用的服务端口，采用不同的响应解析逻辑。推荐统一使用 ssa-ser（8000）作为主认证服务，如需多登录方式再对接 ssa-oauth。

---

### 7.3 免认证路径配置问题

`application-dev.yml` 中 `security.ignore-urls` 为空，且注释说明不能配置 `/auth/**`（否则密码错误后后续请求报 401）。当前只有 `/auth/v1/login` 和 `/auth/v1/refresh` 两个精确路径免认证。

**影响**：
- `/wechat/**` 路径需要认证（除非前端先获取 Token）
- `/v1/index`（sql-api 健康检查）需要认证
- 所有动态 API 调用需要认证

**建议**：微信扫码登录流程需要先有一个免认证的入口，建议将 `/wechat/qr` 和 `/wechat/qr/status` 加入 `ignore-urls`。

---

### 7.4 ssa-oauth 接口无统一响应包装

ssa-oauth 微服务的所有接口（Auth、Permission、SmsCode）**直接返回业务对象**，不使用 `ApiResult` 包装。前端如同时对接两个服务，需做差异化处理：

```javascript
// ssa-ser 响应解析
const data = response.data.data;  // ApiResult.data

// ssa-oauth 响应解析
const data = response.data;  // 直接是业务对象
```

---

### 7.5 WebSocket 安全性

当前 WebSocket 连接仅通过 `username` Header 标识用户，**无 JWT Token 校验**。任何人知道用户名即可冒充连接。

**建议**：在 `configureClientInboundChannel` 的 CONNECT 拦截中增加 JWT 校验，从 Header 中提取 `Authorization` 并验证。

---

### 7.6 file-api / import-api 缺少 YML 配置

当前代码库中未发现 file-api 和 import-api 的 YML 配置文件，无法确定具体可用的 API 名称。

**需后端补充**：
- 文件上传/下载的 YML 配置（操作类型、存储路径、参数定义）
- Excel 导入/导出的 YML 配置（模板映射、数据校验规则）

---

### 7.7 网关路由说明

ssa-gateway（8080）基于 Nacos 服务发现自动路由，路由规则为 `/{service-name}/**` → 对应服务。

| 网关路径 | 路由目标 | 示例 |
|----------|----------|------|
| `/oauth/**` | ssa-oauth:8081 | `http://8080/oauth/auth/v1/login` → `http://8081/auth/v1/login` |

> ⚠️ ssa-ser 未注册到 Nacos，**无法通过网关访问**。前端需直连 `http://host:8000`。

---

### 7.8 登录失败错误码处理

ssa-ser 登录失败时返回 `code: 50000`（FAILED），`msg` 为异常消息文本。前端不应依赖固定错误码判断失败原因，应解析 `msg` 文本。

ssa-ser 的 `AuthV1.login()` 捕获所有异常并返回 `ApiResult.failed(ex.getMessage())`，不会返回 103（USERNAME_OR_PASSWORD_ERROR）等精确错误码。

---

### 7.9 数据库差异

| 环境 | 数据库 | 说明 |
|------|--------|------|
| dev（MySQL） | `113.44.117.125:9920/sso` | ssa-ser dev 环境 |
| dev（SQL Server） | `113.44.117.125:9910/zssys` | ssa-ser-client dev 环境 |
| ssa-oauth | `113.44.117.125:9920/ssa` | 注意数据库名不同（`ssa` vs `sso`） |

**影响**：ssa-oauth 的 sql-api 配置指向 `ssa` 库，ssa-ser 指向 `sso` 库，两套服务的用户表数据可能不同步。

---

### 7.10 待确认项清单

| 编号 | 待确认项 | 说明 |
|------|----------|------|
| 1 | `user_getUserInfo` API 名称是否匹配 | AuthV1 调用名与 YML 文件名是否一致 |
| 2 | ssa-ser 是否加载 file-api / import-api 插件 | dev 配置中仅列出 sql-api，其他插件加载方式待确认 |
| 3 | ssa-oauth 是否有独立的安全过滤链 | ssa-oauth 的免认证路径配置未在源码中找到 |
| 4 | WebSocket 是否需要 JWT 认证 | 当前仅 username Header，安全性不足 |
| 5 | file-api / import-api 的具体 API 名称 | 需后端提供 YML 配置清单 |
| 6 | ssa-oauth 的 LoginResult.expires 具体含义 | 是有效期秒数还是过期时间戳 |
| 7 | 网关是否需要为 ssa-ser 配置路由 | ssa-ser 当前无法通过网关访问 |
| 8 | 微信扫码登录的生产实现方案 | 当前为 Demo，qrUrl 为模拟链接 |
| 9 | ssa-oauth 的 Permission 接口是否需要认证 | 源码中未配置安全过滤，依赖网关层鉴权 |
| 10 | Redis 黑名单与 ssa-oauth 是否共享 | 两套服务使用同一 Redis，但 Token 体系不同 |

---

## 附录：接口速查表

### ssa-ser（端口 8000）

| 方法 | URL | 认证 | 说明 |
|------|-----|------|------|
| POST | `/auth/v1/login` | ❌ | 账号密码登录 |
| POST | `/auth/v1/refresh` | ❌ | 刷新 Token |
| POST | `/auth/v1/getUserInfo` | ✅ | 获取当前用户信息 |
| POST | `/auth/v1/logout` | ✅ | 登出 |
| GET | `/wechat/qr` | ✅ | 获取扫码二维码 |
| GET | `/wechat/qr/status` | ✅ | 查询扫码状态 |
| GET | `/wechat/qr/simulate` | ✅ | 模拟扫码（Demo） |
| GET | `/wechat/qr/simulateLogin` | ✅ | 模拟登录（Demo） |
| GET | `/api/me` | Cookie | 获取当前用户（测试） |
| GET | `/v1/index` | ✅ | sql-api 健康检查 |
| POST | `/v1/getData` | ✅ | 通用数据查询 |
| POST | `/v1/{apiName}` | ✅ | 按 API 名调用 |
| POST | `/v1/{path}/{apiName}` | ✅ | 两级路径调用 |
| POST | `/v1/getApisData` | ✅ | 批量调用 |
| ALL | `/fileApi/v1/{apiName}` | ✅ | 文件操作 |
| ALL | `/fileApi/v1/{path1}/{apiName}` | ✅ | 文件操作（两级） |
| ALL | `/importApi/v1/{apiName}` | ✅ | 数据导入导出 |
| ALL | `/importApi/v1/{path1}/{apiName}` | ✅ | 数据导入导出（两级） |
| WS | `/ws` | username | WebSocket 连接端点 |

### ssa-oauth（端口 8081 / 网关 8080）

| 方法 | URL | 认证 | 说明 |
|------|-----|------|------|
| POST | `/auth/v1/login` | ❌ | 多方式登录 |
| POST | `/auth/v1/refresh` | ❌ | 刷新 Token |
| POST | `/auth/v1/logout` | ✅ | 登出 |
| POST | `/auth/v1/validate` | ❌ | 验证 Token |
| GET | `/auth/v1/permission/check` | - | 检查单个权限 |
| POST | `/auth/v1/permission/check-any` | - | 检查任意权限 |
| POST | `/auth/v1/permission/check-all` | - | 检查全部权限 |
| GET | `/auth/v1/permission/user/{userId}/permissions` | - | 获取用户权限列表 |
| GET | `/auth/v1/permission/role/check` | - | 检查角色 |
| POST | `/auth/v1/sms/send` | ❌ | 发送短信验证码 |
| POST | `/auth/v1/sms/verify` | ❌ | 验证短信验证码 |

### ssa-ai（端口 9000）

| 方法 | URL | 认证 | 说明 |
|------|-----|------|------|
| GET | `/api/skills` | - | 技能列表 |
| GET | `/api/skills/{id}` | - | 获取单个技能 |
| POST | `/api/skills` | - | 创建技能 |
| PUT | `/api/skills/{id}` | - | 更新技能 |
| DELETE | `/api/skills/{id}` | - | 删除技能 |
