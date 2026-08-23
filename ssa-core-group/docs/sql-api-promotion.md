# 会写 SQL 就会写 API：ssa-core 动态插件 + sql-api 技术布道

> 后端开发最大的痛点是什么？不是业务逻辑复杂，而是"改一个查询字段也要走完 写 Mapper → 改 XML → 编译 → 打包 → 部署 → 重启"一整套流程。ssa-core 的动态插件框架与 sql-api，把这个闭环压缩成"写一段 YML"。

---

## 一、核心理念：会写 SQL，就会写 API

在 ssa-core 体系里，**一个 API 就是一个 YML 文件**。你不需要写 Controller、不需要写 Mapper 接口、不需要写 XML，只要描述清楚"要查什么、参数怎么传、返回什么形态"，接口就自动上线。

```yaml
apiName: sqlapi.getListPage
returnType: PAGE_LIST          # LIST / PAGE_LIST / OBJECT / UPDATE / EXEC_PROC ...
datasource: ssa-auditor
content: |
  select id, project_name, audit_year, status
  from project
  where 1=1
  #if(projectName) and project_name like #para(projectName)
  #if(status)     and status = #para(status)
onBefore: [...]               # 事件钩子
```

`returnType` 决定返回形态（列表 / 分页 / 单对象 / 增删改 / 存储过程）；`datasource` 指定多数据源；`content` 就是你要写的 SQL。

**动态 SQL 引擎基于 JFinal Enjoy 模板**：`#if()` 做条件、`#for()` 做循环、`#para()` 做预编译参数绑定。能力和 MyBatis 的 `<if>/<foreach>` 对等，还多出一层**声明式事件钩子**——`onBefore / onSuccess / onError` 配合 `Assert`（断言校验）、`ExecSql`（前后置 SQL）、`SsaApi`（级联调用另一个 API），在不写代码的前提下完成业务编排。

结果以 `Record → Map/JSON` 透传，单 API 内 `db.tx` 保证事务。对前端而言，**YML 文件本身就是接口契约**。

---

## 二、杀手锏：热更新，零编译零重启

这是 sql-api 对比 MyBatis 最显著的胜出点。

YML 改完，传 `reload=true`，接口即时生效；插件可运行时 `enable / disable / reload`。**改一个查询、加一个报表、调一个过滤条件，不再需要重新编译打包、不再需要重启服务**。对需求多变、需要快速响应业务方的内网系统、报表平台、后台管理，这种发布效率是质变。

---

## 三、进阶亮点：MethodApi 动态插件 + API 覆盖机制

这一能力是 sql-api 的"逃生舱"，也是它区别于普通"SQL 映射工具"的关键。

### 3.1 两层 API 管理器，按优先级分发

ssa-core 把 API 分成多类管理器，全部实现 `ISsaApiManager`，由 `ApiRunner` 统一路由。它们的优先级由 Spring 的 `@Order` 决定：

| 管理器 | 优先级 `@Order` | 来源 |
|---|---|---|
| **MethodApiManager** | **0**（最高） | Java 方法型 API（含动态插件注册） |
| **SqlApiManager** | **1** | YML 声明的 sql-api |
| FileApiManager | 2 | 文件类 API |
| ImportApiManager | 最低（无 @Order） | 导入类 API |

`ApiRunner` 按排序遍历这些管理器，一旦某个管理器命中 `apiName` 就**立即返回、不再询问后续管理器**。

### 3.2 同名即覆盖：MethodApi 优先级高于 sql-api

**结论：当 api 名字相同时，MethodApi 会覆盖 SqlApi。**

也就是说，你先用 YML 快速声明了一个接口（比如 `report.profit`），跑通了 80% 的场景；某天发现这个接口需要一段 YML 难以表达的复杂逻辑（多表事务、第三方系统调用、特殊权限校验、兜底策略），你**无需修改或删除原来的 YML**，只要在插件里用 Java 写一个同名 `@SsaApiMethod`，注册为 MethodApi，它就会自动遮蔽掉同名的 YML 接口。

实现链路是：

1. 插件 jar 经 `SsaPluginLoader` 加载，`SpringMvcPluginClassLoader` 扫描 jar 内带注解的类并注册为 Spring Bean；
2. `MethodApiManager.reload()` 通过 `getBeansWithAnnotation(SsaApiClass.class)` 扫描，把 `@SsaApiMethod` 方法以 `classPrefix.methodName` 为 key 注册成 API；
3. 调用时 `ApiRunner` 先问 `@Order(0)` 的 MethodApiManager，命中即返回，SqlApiManager 根本不会被查询——**覆盖由此成立**。

### 3.3 反向保护：核心接口不被插件冲掉

覆盖是双向的。`MethodApiManager` 内部有一道**"防覆盖守卫"**：如果一个 api name 已经存在（先注册者胜），后到的同名方法会被**记 error 并跳过**。

这意味着：核心内置的 MethodApi 受到保护，插件即使想重名覆盖内置接口也会被忽略，避免了插件误伤系统核心。而插件覆盖 YML 声明的 SqlApi 则是被允许的——**代码级定制可以兜底声明式配置，但声明式配置无法反过来冲掉核心代码**。这套设计既给了灵活性，又守住了稳定性。

> 补充：若调用方显式传入 `apiManagerClassName`，可绕过优先级排序、强制指定某管理器执行，即可在需要时"绕过覆盖"直接用 YML 版接口。

---

## 四、与 MyBatis / MyBatis-Plus 的关系：互补，不替代

我们做过系统对比，结论很明确：**sql-api 不能完全替代 MyBatis，二者是互补 / 分层关系，可以在同一个 Spring 工程并存。**

- **sql-api 更擅长**：写 YML 即发布、热更新零重启、查询 / 报表 / 列表 / 后台 CRUD / 快速原型、前端"YML 即契约"协作。在这些场景，它比 MyBatis 更快、更省代码、运维效率更高。
- **MyBatis / MyBatis-Plus 更擅长**：强类型领域模型（resultMap 映射实体）、跨 API / 服务的复杂事务、成熟的一 / 二级缓存、标准 OpenAPI 文档与编译期可审计、高合规强权限场景。

**推荐落地策略**：核心领域层、复杂事务用 MyBatis（强类型、可测、可审计）；外围查询、报表、配置化接口用 sql-api（快、热更新）。sql-api 侧再补齐三件事即可放心推广：① 外部网关统一鉴权；② 团队强制用 `#para` 防注入并配合 `o()` 标识符转义；③ YML 模板做 CI 校验（returnType / 参数枚举合法性），弥补"纯文本模板运行期才解析、无编译期类型校验"的短板。

---

## 五、总结

sql-api 的本质，是把"接口"从**编译期产物**变成**运行期声明**：会写 SQL 就会写 API，改接口不再动代码、不再重启服务。而 MethodApi 动态插件 + 同名覆盖机制，又给了这套声明式体系一个**代码级的逃生舱**——简单的事 YML 秒出，复杂的事 Java 方法覆盖兜底，且核心接口受防覆盖守卫保护不会被插件冲掉。

对于事务所内网、多人协作、需求多变的审计底稿类系统，这是一套真正贴合业务节奏的数据接口方案。

**少写代码，多写 SQL，剩下的交给框架。**
