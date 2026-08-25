# SSA Service

> **平台无业务，一切皆插件。** 一套内核 + 动态插件 JAR：用「写 SQL / 写 YML」的方式生成接口；一行 `mcptool:true` 把任意接口变成 AI 智能体工具；同一套代码在**单体、微服务、嵌入式**之间自由伸缩 —— 我们称之为 **一体多态 · Fluid Topology**（One Core, Many Shapes —— 一个内核，多种部署形态自由流动），像水没有固定形状却可装进任何容器。

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green.svg)](https://spring.io)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> 📖 **完整介绍与架构图（项目主页）：** Gitee Pages（国内优先） `https://kevinzhaoyp.gitee.io/ssa-service/` ｜ GitHub Pages（备） `https://kevinzhaoyp.github.io/ssa-service/`
> 把本仓库 `docs/` 目录开启 Gitee Pages / GitHub Pages 即可获得该网址，README 与各平台文章都放这个链接。

---

## 一句话定位

**SSA Service 是一个「没有业务的后端运行平台」。** 内核只负责：接收请求 → 找到插件里的接口 → 执行 → 返回。业务规则 100% 写在插件 JAR 里（Java 类，或一份 SQL YML），需要什么能力就挂什么插件，不需要就卸载 —— **平台本身永远不碰业务逻辑**。

> 💡 区别于传统 Spring Boot 脚手架：传统脚手架把 Controller/Service 写死在主工程；SSA 的主工程是「空壳运行时」，能力全部来自外挂插件，且插件可**运行时增删**。

---

## 五大亮点

- **① SQL 即 API（sql-api）**：写一份 YML（SQL + 参数 + 事件链）就得到一个接口，自动暴露为 `POST /sqlApi/v1/{apiName}`，支持多数据源、存储过程、前后置事件编排。
- **② 动态插件 JAR**：自研 `SpringMvcPluginClassLoader`（继承 `URLClassLoader`）运行时扫描插件 jar，把标注类注册成 Spring Bean、按 `routePrefix` 挂载路由；支持 `enablePlugin/disablePlugin` **运行时热启停** + API 列表热刷新，坏插件不拖垮启动。
- **③ MCP 工具动态注册（mcp-api）**：内嵌官方 MCP Java SDK 2.x（Streamable HTTP），自动扫描 ymlapi 目录，凡 `mcptool:true` 的接口一键变身为 AI 智能体工具，JSON Schema 由参数自动生成；`POST /mcp/rescan` 热刷新；工具调用走**同进程 ApiRunner，零网络跳**。
- **④ 注解即 API，且可覆盖 sql-api（MethodApi）**：`@SsaApiClass` + `@SsaApiMethod` 把 Java 方法直接注册为接口；**同名 API 时 MethodApi 覆盖 SqlApi** —— 先用 YML 跑通 80% 场景，复杂逻辑写同名方法无缝覆盖（「代码级逃生舱」）。
- **⑤ 一体多态部署（Fluid Topology）**：同一套插件 JAR，在「单体 `ssa-ser`」「嵌入式 `ssa-ser-client`」「微服务 `ssa-msa`（Gateway+Nacos+N×ssa-ser）」间零改动切换。小项目单 JAR 起步，业务涨了加网关注册多实例水平扩展。

> **💡 Bonus · 传统后台秒变 Agent 工具箱**：因为每项能力都是统一 HTTP API，末尾加一行 `mcptool:true` 即注册为 MCP 工具 —— 原来在后台一个个点的操作，瞬间变成智能体（WorkBuddy 等）能用自然语言驱动的工具，也能被 CLI / 脚本直接调。**一套声明，前端 / CLI / Agent 三端复用。** 已与 WorkBuddy 打通。

---

## 架构总览

```
┌─────────────────────────┐     挂载     ┌──────────────────────────────┐     同一套     ┌─────────────────────────────┐
│  平台内核（无业务）        │ ────────▶  │  插件 JAR（承载全部业务）        │ ────────▶  │  三种部署形态（零改插件）       │
│  ssa-core / ssa-core-web │            │  sql-api ★ 旗舰：SQL 即 API     │            │  ① 单体 ssa-ser（单 JAR）      │
│  • API 运行引擎 ApiRunner │            │  method-api：注解即 API·可覆盖   │            │  ② 嵌入式 ssa-ser-client      │
│  • 插件加载器             │            │  mcp-api：MCP 工具动态注册       │            │  ③ 微服务 ssa-msa             │
│  • Spring 动态 Bean 注册  │            │  file-api / import-api          │            │     (Gateway+Nacos+N×ssa-ser) │
│  • 安全/JWT/多数据源      │            │  push-way / ssa-api-editor      │            │                              │
│  • MCP 运行基座           │            │  ＋ 你自己的业务插件.jar         │            │                              │
└─────────────────────────┘            └──────────────────────────────┘            └─────────────────────────────┘
```

---

## 快速开始（ssa-ser-client · 零鉴权尝鲜版）

> 想**最快跑起来、不碰鉴权登录**？用嵌入式形态 `ssa-ser-client`：启动即连数据库，直接写 SQL 接口，无需登录。适合本地尝鲜、CLI/脚本直连、把后台能力快速暴露成 API。

**① 构建（JDK 17+、Maven 3.8+）**

```bash
git clone https://gitee.com/kevinzhaoyp/ssa-service.git
# 或：git clone https://github.com/kevinzhaoyp/ssa-service.git
cd ssa-service
mvn clean package -DskipTests
```

构建产物：`ssa-ser-client/target/ssa-ser-client-2.0.0.jar` 与 `ssa-plugins/sql-api/target/sql-api-2.0.0.jar`。

**② 准备插件与模板目录**（jar 同目录即插件/模板根目录）

进入 `ssa-ser-client/target`，建 `plugins/`（放插件 jar）与 `sqlapi/`（放 SQL 模板），并把 sql-api 插件放进去：

```bash
cd ssa-ser-client/target
mkdir -p plugins sqlapi
cp ../../ssa-plugins/sql-api/target/sql-api-2.0.0.jar plugins/
```

**③ 写第一个 SQL 接口**（无需建表，查数据库当前时间）

在 `ssa-ser-client/target/sqlapi/now.yml` 写入：

```yaml
content: SELECT NOW() AS now_time
```

**④ 配置数据源**（在 `ssa-ser-client/target/application.yml`，jar 同目录的外部配置优先于 jar 内）

```yaml
sqlapi:
  datasource:
    main:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://127.0.0.1:3306/test?useSSL=false&serverTimezone=Asia/Shanghai
      username: root
      password: 你的密码
```

> 💡 `ssa-ser-client` 默认仅内置 **SQL Server** 驱动。用 MySQL/PostgreSQL 需先在 `ssa-ser-client/pom.xml` 加入对应 JDBC 驱动依赖（如 `com.mysql:mysql-connector-j`）再重新 `mvn package`；或直接使用 SQL Server 跳过此步。

**⑤ 启动（默认端口 8080，无登录、无鉴权）**

```bash
java -jar ssa-ser-client-2.0.0.jar
```

**⑥ 调用**

```bash
curl -X POST http://localhost:8080/sqlApi/v1/now \
  -H 'Content-Type: application/json' -d '{}'
```

返回含 `now_time` 的 JSON 即跑通 —— 你已经用「写一份 YML」换到了一个 HTTP 接口，全程没写一行 Java、没碰登录鉴权。

> 📌 插件扫描目录 `plugins/`、SQL 模板目录 `sqlapi/` 默认取**运行 jar 所在目录**（`SpringUtil.getJarHome()`）；数据源配置 key 为 `sqlapi.datasource.main.*`，由 sql-api 插件读取并交给 JFinal ActiveRecord 管理。

---

## 示例：写一份 YML，就有一个接口；加一行，就多一个 AI 工具

**① 定义 SQL 接口（含参数校验、数据源）：**

```yaml
# user/list.yml
datasource: main
describe: 查询启用用户
content: |
  SELECT id, name, email FROM ssa_user
  WHERE status = #(status)
  ORDER BY id
params2:
  - name: status
    type: int
    required: true
    hint: 用户状态 1=启用 0=禁用
returnType: list
```

> 占位符采用 JFinal ActiveRecord 模板语法 `#(name)`（或位置参数 `?`）；`params2` 用于参数校验，`required` 为布尔值。调用时请求体即参数对象，如 `POST /sqlApi/v1/user/list -d '{"status":1}'`。

**② 末尾加一行 `mcptool: true`，它自动成为 MCP 工具：**

```yaml
mcptool: true
```

**③** MCP 客户端（WorkBuddy / Claude 等）连接 `POST /mcp`，`tools/list` 即可发现 `user_list` 工具；`tools/call` 时平台在同进程内执行该 SQL 并返回 —— 无需任何额外胶水代码。

**④** 热刷新，注册新工具无需重启：`POST /mcp/rescan` → 通知已连接的客户端工具列表已变更。

> 🔗 **完美结合点**：sql-api 的「前后置事件」可调用 `ApiRunner.safeRun` 执行任意其它接口；当这些接口被标记为 MCP 工具后，AI 智能体不仅能查数据，还能在工具链里触发审批、推送、导入等动作 —— 「数据接口」与「AI 工具」是同一份声明，两条链路共享一套执行引擎。

---

## MethodApi：注解即 API，且可覆盖 sql-api

```java
@SsaApiClass("report")
public class ReportApi {
    @SsaApiMethod(name = "profit")   // 注册名 = report.profit，同名时覆盖 sql-api
    public Object profit(Map params) {
        // 多表事务、第三方调用、特殊校验等复杂逻辑写这里
        return ...;
    }
}
```

先用 YML 快速声明接口跑通 80% 场景，遇到 YML 难以表达的复杂逻辑，写一个同名 `@SsaApiMethod` 即可无缝覆盖，无需改删原 YML。

---

## 一体多态 · Fluid Topology（正式命名）

> **One Core, Many Shapes —— 一个内核，多种部署形态自由流动。**

内核与插件不变，部署形态随业务规模「流动」：单机单 JAR（起步）→ 网关后的多实例集群（扩容）→ 或塞进桌面做嵌入式一体机。就像水没有固定形状，却能装进任何容器。

---

## 技术栈

- Java 17 + Spring Boot 4.1
- Spring Security + JWT 鉴权
- 数据库访问：JFinal ActiveRecord（多数据源）
- 微服务：Spring Cloud 2025.1 + Spring Cloud Alibaba + Nacos
- AI：官方 MCP Java SDK 2.x（Streamable HTTP）
- 插件描述：`ssa-plugin.yml`（含 `routePrefix` / `dependencies`）

### 与传统方案差异

| 维度 | 传统 Spring Boot | SSA Service |
|------|------------------|-------------|
| 业务逻辑位置 | 写死在主工程 | **100% 在插件 JAR** |
| 新增接口 | 改代码 + 重启 | **加 YML / 挂 jar，热装载** |
| 单体 ↔ 微服务 | 常需重构 | **同插件零改动切换（一体多态）** |
| AI 工具暴露 | 另写适配层 | **`mcptool:true` 自动注册** |
| 能力下线 | 删代码发版 | **卸载插件即停** |

---

## 适用场景

- 🏢 企业内部系统：把增删改查做成 SQL 接口，告别重复 CRUD 样板
- 🤖 AI 智能体后端：现有数据接口一键变 MCP 工具，让智能体直接调用你的业务
- 📦 私有化 / 一体机：嵌入式形态塞进桌面或内网，离线可用
- 🧱 中台 / 能力市场：每个业务域一个插件，组合拼装；可规划「插件市场」按需下载
- ⚡ 快速验证 MVP：单 JAR 起步，业务起来再扩微服务，架构演进不返工
- 🔐 合规敏感行业：审计、财务等场景，平台与业务隔离，插件可独立审计

---

<!-- 起源故事已留档至 docs/起源故事_留档.md，暂不置于 README -->

---

## 一起共建

框架已开源，但它需要更多人一起养大。原作者目前时间有限，所以真心希望社区能接住它：**提 Issue、修 Bug、补文档、写示例插件、优化内核，甚至只是来聊聊架构想法 —— 有想法、有时间的你，都是我们想找的同行者。**

你的代码会被真实项目使用，一起把它打磨成更好用的「无业务、全插件化」底座。

---

## 许可证

本项目基于 **Apache License 2.0** 开源。你可以在遵守许可证条款（保留版权声明、注明修改、接受免责声明）的前提下，自由使用、修改与再分发，包括闭源商业集成。

- 许可证全文见仓库根目录 [`LICENSE`](LICENSE) 文件。
- 版权归属：作者（The Author）。如需商用授权或商业合作，欢迎通过项目主页联系。

---

*本 README 基于 ssa-service 源码整理。SSA Service —— 无业务、全插件化的企业级后端框架。欢迎共建。*
