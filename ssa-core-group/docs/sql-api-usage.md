# sql-api 使用文档（ssa-core 动态插件 · 即写 SQL 即 API）

> 适用版本：sql-api 2.0 / ssa-core 2.0.0
> 核心理念：**一个 API 就是一个 YML 文件**。不需要 Controller、不需要 Mapper、不需要 XML，写一段 SQL 模板，接口就自动上线，改完热更新即时生效。

---

## 一、快速开始

1. 在约定目录下新建一个 `.yml` 文件，文件名即决定 `apiName`：
   - 文件 `sqlapi/getListPage.yml` → `apiName = sqlapi.getListPage`
2. 在 YML 里写好 `returnType`、`content`（SQL 模板）、`params2`（参数）等。
3. 通过框架统一通用端点调用（如 `POST /v1/sqlapi.getListPage`），`apiName` 即路由键。
4. 修改 YML 后传 `reload=true` 即可热更新，无需重启服务。

最小示例（分页查询）：

```yaml
version: 2.0
returnType: PAGE_LIST
datasource: ~
describe: 项目列表（分页）
content: |-
  select id, project_name, audit_year, status
  from project
  where 1=1
  #if(projectName) and project_name like #para(projectName)
  #if(status)     and status = #para(status)
```

---

## 二、YML 完整字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | string | 否 | 模板版本，当前 `2.0` |
| `returnType` | 枚举 | **是** | 返回类型（见第三节，写错会静默降级为 `ERROR`） |
| `datasource` | string | 否 | 多数据源名称，省略用默认库（`~` 表示 null） |
| `describe` | string | 否 | 接口说明（文档/排查用） |
| `params2` | List\<ApiParam\> | 否 | 参数定义列表（**注意是 `params2`，不是 `params`**） |
| `content` | string | **是** | SQL 模板，使用 Enjoy 模板语法 |
| `alias` | string | 否 | 返回对象 key 别名（应对 Oracle 大写字段等） |
| `subs` | List | 否 | 主子表定义，配合 `OBJECT_SUB` 使用 |
| `onBefore` | List\<SqlApiEvent\> | 否 | 执行前事件（校验 / 取参 / 级联） |
| `onSuccess` | List\<SqlApiEvent\> | 否 | 执行成功后事件 |
| `onError` | List\<SqlApiEvent\> | 否 | 执行出错后事件 |

> ⚠️ **字段名坑**：项目早期示例 YML 用的是 `params`（已废弃，映射到 `String[]`，无法承载结构化参数）。**定义参数必须用 `params2`**，与 Java 实体 `SqlApi.params2` 一致。

---

## 三、ReturnType 返回类型枚举

以代码 `ReturnType.java` 为准，必须是下列常量名之一：

| 枚举常量 | 含义 |
|---|---|
| `EXEC_PROC` | 执行存储过程，无返回结果（如 update/delete） |
| `OPEN_PROC` | 执行存储过程，返回结果集第一条（对象） |
| `OPEN_PROC_LIST` | 执行存储过程，返回结果列表 |
| `LIST` | 执行 SQL，返回列表 |
| `PAGE_LIST` | 执行 SQL，返回**分页**列表（框架包装 `{total, rows}`，数据自带 `rn` 行号、`tc` 总记录数；分页参数 `pageSize` / `minRN`） |
| `OBJECT` | 执行 SQL，返回对象（列表第一条） |
| `OBJECT_SUB` | 执行 SQL，返回对象并支持主子表（`subs`） |
| `UPDATE` | 执行增删改 SQL，返回影响行数 |
| `UPDATE_TABLE` | 批量执行多条 SQL |
| `JAVA_CLASS` | 预留（暂未启用） |
| `ERROR` | 加载失败时置为 `ERROR`，直接返回 `content` 中的错误信息 |

> ⚠️ **枚举名坑**：示例 YML 中写的是 `LIST_PAGE`，但代码枚举名是 **`PAGE_LIST`**。`returnType` 写错不会报错，而是被静默置为 `ERROR` 类型——接口看似"已发布"却无法正常工作。务必使用上表中的准确常量名。

---

## 四、参数定义 `ApiParam`

参数通过 `params2` 列表声明，每个元素结构如下（对应 `ApiParam.java`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | 参数名，在 `content` 中以 `#para(name)` 引用 |
| `type` | string | 类型，仅支持 `string` / `int`（**小写**） |
| `defValue` | object | 默认值（事件里也可动态设置提取值） |
| `required` | boolean | 是否必填，框架做必填校验 |
| `hint` | string | 校验不通过时的提示信息 |

示例：

```yaml
params2:
  - name: projectName
    type: string
    required: false
    hint: 项目名称模糊查询
  - name: status
    type: int
    required: true
    defValue: 1
    hint: 状态必填
```

---

## 五、Enjoy 模板 SQL 语法

`content` 是 JFinal Enjoy 模板，常用指令：

| 语法 | 用途 | 安全性 |
|---|---|---|
| `#if(cond) ... #else ... #end` | 条件片段 | — |
| `#for(x : list) ... #end` | 循环（如动态字段、动态条件） | — |
| `#(expr)` | 字符串插值（表名 / 列名 / 动态 SQL 片段） | ⚠️ **有注入风险**，仅用于可信标识符 |
| `#para(expr)` | 预编译参数绑定（`?` 占位） | ✅ **防注入**，用于值 |

循环内首元素判断示例：

```sql
#for(x : w)
  #(for.first ? "where": "and") #(x.key) #para(x.value)
#end
```

> 安全规范：值一律用 `#para()` 绑定；`#()` 插值只用于表名、列名等结构部分，并配合 `o()` 标识符转义与外部校验，避免 SQL 注入。

**分页（PAGE_LIST）**：框架自动分页，无需手写 `limit`；分页参数 `pageSize`（每页条数）、`minRN`（最小行号，从 0 开始）；返回数据自动带 `rn`（行号）与 `tc`（TotalCount）。

---

## 六、事件钩子 `SqlApiEvent`

通过 `onBefore` / `onSuccess` / `onError` 实现声明式 AOP（对应 `SqlApiEvent.java`）：

| 字段 | 说明 |
|---|---|
| `name` | 事件标识 |
| `enable` | 是否启用（默认 true） |
| `onErrorGotoNext` | 该事件出错时是否继续执行后续（默认 true） |
| `contentType` | 操作类型：`OPEN_SQL`（查询）、`EXEC_SQL`（执行，如校验/写入）、`SSA_API`（级联调用另一个 API）等 |
| `ds` | 事件独立数据源（与 `datasource` 一致命名） |
| `content` | SQL 或表达式内容（支持 Enjoy 语法） |
| `errorMsg` | 断言失败 / 出错时的提示（支持 Enjoy） |
| `outType` | 输出类型：`void` / `object` / `list` / `map` |
| `outParams` | 将执行结果提取回 `params`（同名参数会被覆盖），供主 SQL 或后续事件使用 |

典型用法：
- `onBefore` + `EXEC_SQL` + 断言：执行业务校验，不满足条件用 `errorMsg` 拦截；
- `onBefore` + `outParams`：先查一个值（如当前用户、配置项）注入参数，再跑主 SQL；
- `onSuccess` + `SSA_API`：主 SQL 成功后级联调用另一个 API（如写操作日志）。

---

## 七、MethodApi 动态插件（同名覆盖）

当声明式 YML 无法满足复杂逻辑（多表事务、第三方调用、特殊权限、兜底策略）时，可用 Java 方法 API 覆盖。

### 7.1 注解写法

```java
@SsaApiClass(value = "report")          // 类级前缀，注册名 = report.xxx
public class ReportApi {

    @SsaApiMethod(
        name = "report.profit",         // 显式指定 apiName
        describe = "利润计算",
        requireAuth = true               // 默认需要认证
    )
    public Map<String, Object> profit(Map<String, Object> params) {
        // 任意 Java 逻辑：复杂事务、远程调用、兜底……
        return result;
    }
}
```

- 类需标注 `@SsaApiClass`，方法标注 `@SsaApiMethod`。
- 注册名规则：`类名首字母小写.方法名`；`@SsaApiClass(value="report")` 可自定义前缀；`@SsaApiMethod(ignoreClassValue=true)` 时直接用方法名。
- 方法参数为 `Map`，返回 `Map` / 对象 / `ApiResult`。

### 7.2 优先级与覆盖规则（重要）

API 管理器按 Spring `@Order` 排序，由 `ApiRunner` 统一路由并**命中即返回**：

| 管理器 | `@Order` | 来源 |
|---|---|---|
| **MethodApiManager** | **0（最高）** | Java 方法型（含动态插件注册） |
| **SqlApiManager** | **1** | YML 声明的 sql-api |
| FileApiManager | 2 | 文件类 API |
| ImportApiManager | 最低 | 导入类 API |

**结论：同名 API 时，MethodApi 覆盖 SqlApi。** 即先用 YML 快速声明接口，后用 Java 方法写同名 `@SsaApiMethod` 即可无缝覆盖，无需改动或删除原 YML——这是声明式体系的"代码级逃生舱"。

插件 jar 经 `SsaPluginLoader` → `SpringMvcPluginClassLoader` 注册为 Spring Bean 后，`MethodApiManager.reload()` 自动扫描 `@SsaApiClass` 并注册其中的 `@SsaApiMethod`，支持运行时 `enable/disable/reload` 热插拔。

### 7.3 反向保护（防覆盖守卫）

`MethodApiManager` 内部有"防覆盖守卫"：若一个 `apiName` 已存在（先注册者胜），后到的同名方法会被**记 error 并跳过**。

含义：**核心内置 MethodApi 受保护，插件无法用重名冲掉核心接口**；但插件覆盖 YML 声明的 SqlApi 是被允许的（代码可兜底声明式配置，反之不可）。既给了灵活性，又守住了稳定性。

> 补充：调用方可显式传入 `apiManagerClassName` 绕过优先级排序、强制指定某管理器执行，从而"绕过覆盖"直接用 YML 版接口。

---

## 八、多数据源

- 接口级：`datasource` 字段指定默认数据源名（需与配置中的数据源一致）。
- 事件级：`SqlApiEvent.ds` 可为单个事件单独指定数据源。
- 底层通过 `Db.use(dsName)` 切换。

---

## 九、热更新

- 修改 YML 后传 `reload=true`，接口即时重新加载生效，**无需重新编译、无需重启服务**。
- 插件（MethodApi）可在运行时 `enable / disable / reload`，调用 `MethodApiManager.reload()` 刷新已注册的 Java API。

---

## 十、安全须知

1. **防注入**：值一律用 `#para()` 预编译绑定；`#()` 插值仅用于可信的表名/列名，并配合 `o()` 转义与外部校验。
2. **鉴权**：`@SsaApiMethod(requireAuth=true)` 为默认；HTTP 控制器存在 `safeRun` 跳过 `requireAuth` 的通道，**高合规场景必须依赖外部网关统一鉴权**。
3. **CI 校验**：建议在流水线对 YML 做校验（`returnType` 枚举合法性、`params2` 结构），弥补"纯文本模板运行期才解析、无编译期类型校验"的短板（如 `PAGE_LIST` 误写为 `LIST_PAGE` 会静默降级为 `ERROR`）。

---

## 十一、常见坑速查

| 现象 | 原因 | 解决 |
|---|---|---|
| 接口发布后返回 ERROR / 不执行 | `returnType` 写错（如 `LIST_PAGE`） | 改为准确枚举名 `PAGE_LIST` 等 |
| 参数校验不生效 / 取不到参数 | 用了废弃的 `params` 而非 `params2` | 改用 `params2` 列表 |
| SQL 注入风险 | 值用了 `#()` 插值而非 `#para()` | 值改 `#para()`，结构片段才用 `#()` |
| YML 改了不生效 | 未触发 reload | 传 `reload=true` 或重启加载 |
| 同名 Java 方法没覆盖 YML | 注册名不一致（前缀/方法名） | 核对 `@SsaApiClass`/`@SsaApiMethod` 的 name 与类前缀 |
| 插件同名方法未注册 | 被"防覆盖守卫"跳过（核心已存在） | 确认是否重名内置核心 API |
