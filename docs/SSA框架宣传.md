# SSA Service · 平台无业务，一切皆插件

> 一套内核 + 动态插件 JAR：用「写 SQL / 写 YML」的方式生成接口；一行 `mcptool:true` 把任意接口变成 AI 智能体工具；同一套代码在**单体、微服务、嵌入式**之间自由伸缩——我们称之为 **一体多态 · Fluid Topology**。

---

## 一句话定位

**SSA Service 是一个「没有业务的后端运行平台」。** 平台内核只负责：接收请求 → 找到插件里的接口 → 执行 → 返回。业务规则完全写在插件 JAR 里（Java 类，或一份 SQL YML），需要什么能力就挂什么插件，不需要就卸载——**平台本身永远不碰业务逻辑**。

> 💡 区别于传统 Spring Boot 脚手架：传统脚手架把 Controller/Service 写死在主工程；SSA 的主工程是「空壳运行时」，能力 100% 来自外挂插件，且插件可**运行时增删**。

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

## 五大亮点

### 亮点一 · SQL 即 API（sql-api 插件）
不用写 Controller、不用写 Service。新建一份 YML，写上 SQL 和参数，接口就生成了，自动暴露为 `POST /sqlApi/v1/{apiName}`。
支持多数据源、存储过程、前后置事件链；事件还能反过来调用其它接口，形成编排能力。

### 亮点二 · 动态插件 JAR
自研 `SpringMvcPluginClassLoader`（继承 URLClassLoader）在运行时扫描插件 jar，把其中标注的类注册成 Spring Bean、把 Controller 按 `routePrefix` 挂到主路由。
支持**运行时启停插件**（`enablePlugin / disablePlugin`）+ API 列表热刷新，坏插件不影响主程序启动。

### 亮点三 · MCP 工具动态注册（mcp-api 插件）
内嵌官方 MCP Java SDK 2.x（Streamable HTTP）。启动时**自动扫描** ymlapi 目录，凡是声明 `mcptool: true` 的 SQL 接口，一键变身为 AI 智能体的工具；JSON Schema 由参数自动生成；`POST /mcp/rescan` 可热刷新。**工具调用走同进程 ApiRunner，零网络跳。**

### 亮点四 · 注解即 API，且可覆盖 sql-api（MethodApi）
用 `@SsaApiClass` 标注类、`@SsaApiMethod` 标注方法，Java 方法直接注册为接口，无需写 Controller；注册名规则 `类前缀.方法名`。
关键能力：**同名 API 时 MethodApi 覆盖 SqlApi**——先用 YML 快速声明接口跑通 80% 场景，遇到 YML 难以表达的复杂逻辑（多表事务、第三方调用、特殊校验），写一个同名 `@SsaApiMethod` 即可无缝覆盖，无需改删原 YML。这是声明式体系的「代码级逃生舱」。

### 亮点五 · 一体多态部署（Fluid Topology）
同一套插件 JAR，可在「单体 JAR（ssa-ser）」「嵌入式桌面（ssa-ser-client）」「微服务集群（Gateway + Nacos + N×ssa-ser）」三种形态间自由切换，**插件代码零改动**。小项目单 JAR 起步，业务涨了加网关注册多实例即可水平扩展。就像水没有固定形状，却能装进任何容器。

### Bonus · 传统后台秒变 Agent 工具箱
因为每一项能力都已是统一 HTTP API（sql-api + method-api），并在 YML 末追加 `mcptool:true` 即可注册为 MCP 工具——你原来在管理后台里一个个点的操作，瞬间变成智能体（WorkBuddy 等）能用自然语言驱动的工具，也能被命令行 / 脚本直接调。**一套声明，前端 / CLI / Agent 三端复用。**

---

## 详解 · SQL 即 API + 一行变 MCP 工具

这是「平台无业务、全插件化」与「AI 友好」结合得最妙的地方。

**① 写一份 YML，就有一个接口（含参数校验、多数据源、事件）：**

```yaml
# user/list.yml
key: user-list
datasource: main
describe: 查询启用用户
content: |
  SELECT id, name, email FROM ssa_user
  WHERE status = #{status}
  ORDER BY id
params2:
  - name: status
    type: int
    required: 1
    hint: 用户状态 1=启用 0=禁用
returnType: list
```

**② 加一行 `mcptool: true`，它自动成为 MCP 工具：**

```yaml
mcptool: true
```

**③** MCP 客户端（WorkBuddy / Claude 等）连接 `POST /mcp`，`tools/list` 即可发现 `user_list` 工具；`tools/call` 时平台在同进程内执行该 SQL 并返回——无需任何额外胶水代码。

**④** 热刷新，注册新工具无需重启：

```text
POST /mcp/rescan
→ notifyToolsListChanged() 通知已连客户端
```

> 🔗 **完美结合点：** sql-api 的「前后置事件」可以调用 `ApiRunner.safeRun` 执行任意其它接口；当这些接口被标记为 MCP 工具后，AI 智能体不仅能查数据，还能在工具链里触发审批、推送、导入等动作——「数据接口」与「AI 工具」是同一份声明，两条链路共享一套执行引擎。MethodApi 的同名覆盖能力，则让你在「声明式 YML」与「命令式 Java」之间自由切换，互不打架。

---

## 传统后台，秒变 Agent 工具箱（CLI 对接 Agent）

这是「一体多态」之外，最让老系统重获新生的能力。

**问题：后台操作困在 UI 里。** 传统管理后台里，「改个账期」「导一份报表」「发一条通知」都是按钮背后的写死逻辑。没人点，它就动不了；想让 AI 或脚本去驱动，就得再写一整套对接层。系统越老，越像一座孤岛。

**解法：把操作声明成 API，再标 `mcptool`。** 在 SSA 里，这些操作只需声明成 sql-api 或 method-api（写 SQL / 写注解即可），末尾加一行 `mcptool:true`。于是同一批接口：
- 前端页面照常调；
- 命令行 / 脚本 / CI 直接调；
- 智能体（WorkBuddy 等）经 MCP 用自然语言调。

> 🤖 **一句话场景：** 不必再打开后台逐个点击——「把 X 客户的账期改到 90 天」「导出发票台账并发给财务」，智能体直接调用对应的 MCP 工具即可完成。你的传统后台，等于瞬间装上了一套 **Agent 可调用工具箱 + CLI 入口**，业务零重写，AI-ready 即插即用。这也是它已经与 WorkBuddy 打通的实战路径。

---

## 一体多态 · Fluid Topology（正式命名）

你提的「灵活的伸缩式服务」概念很准，我们正式把它定名为——

### 🏆 一体多态 · Fluid Topology
> **One Core, Many Shapes —— 一个内核，多种部署形态自由流动**

内核与插件不变，部署形态随业务规模「流动」：单机单 JAR（起步）→ 网关后的多实例集群（扩容）→ 或塞进桌面做嵌入式一体机。就像水没有固定形状，却能装进任何容器。

---

## 技术栈

- Java 17 + Spring Boot（4.x 已验证）
- Spring Security + JWT 鉴权
- 数据库访问：JFinal ActiveRecord（多数据源）
- 微服务：Spring Cloud 2025.1 + Spring Cloud Alibaba + Nacos
- AI：官方 MCP Java SDK 2.x（Streamable HTTP）
- 插件描述：ssa-plugin.yml（含 routePrefix / dependencies）

### 与传统方案差异

| 维度 | 传统 Spring Boot | SSA Service |
|------|------------------|-------------|
| 业务逻辑位置 | 写死在主工程 | **100% 在插件 JAR** |
| 新增接口 | 改代码+重启 | **加 YML / 挂 jar，热装载** |
| 单体↔微服务 | 常需重构 | **同插件零改动切换（一体多态）** |
| AI 工具暴露 | 另写适配层 | **mcptool:true 自动注册** |
| 能力下线 | 删代码发版 | **卸载插件即停** |

---

## 适用场景

- 🏢 **企业内部系统**：把增删改查做成 SQL 接口，告别重复 CRUD 样板
- 🤖 **AI 智能体后端**：现有数据接口一键变 MCP 工具，让智能体直接调用你的业务
- 📦 **私有化 / 一体机**：嵌入式形态塞进桌面或内网机器，离线可用
- 🧱 **中台 / 能力市场**：每个业务域一个插件，组合拼装；可规划「插件市场」按需下载
- ⚡ **快速验证 MVP**：单 JAR 起步，业务起来再扩微服务，架构演进不返工
- 🔐 **合规敏感行业**：审计、财务等场景，平台与业务隔离，插件可独立审计

---

## 起源故事（作者自述）

> 一个被写死 SQL 折磨过的程序员，如何一步步把它逼成今天的样子。

**作者 · SSA Service 原作者**

我最早是个 Delphi 程序员，平时就爱捣腾各种框架。2017 年之前，我还写过一套 Delphi 版的低代码平台，PPReport 之类的小东西——除了那套低代码平台，基本都死在了自己的硬盘上。

2019 年我从 Delphi 转 Java，接手的是一个可以说很烂的系统，底层用的是 JFinal 框架。说来惭愧，JFinal 里的 ActiveRecord，后来成了今天这套内核的核心。

那个系统里全是写死的 SQL 语句。加个字段、改个字段，一个小小的调整都是灾难。我维护了大概一两个月，就开始琢磨：怎么让自己轻松点？于是我把这些 SQL 写成模板放到外面，热加载进来——小问题谁都可以改，改好了告诉我，我来合并。

大概是 2019 年下半年，微服务特别火，火到不用就好像落后了，不管系统本身大小、也不管客户能提供什么服务器，都上微服务。我当时也很期待，就找了前大厂同事、一位 Java 高手，帮我搭了一套微服务架构。我兴冲冲一头扎进去，一开始为业务怎么拆分就头疼，没多久我就拆出了好几个微服务——可业务还没写完呢。

最后发现，客户就只给一到两台服务器。数据库、Redis、Nacos……各种中间件一跑起来，资源就没了。要把所有微服务都跑起来，那就是灾难；再加上各种运维工具不起、日志满天飞、还不好找。那一刻我才意识到：微服务不是银弹，轻量才是大多数项目的真相。

后来，我边做业务，边加班加点，写出了插件机制（以我当时有限的 Java 水平），把 ActiveRecord 也一并搬了进来，慢慢形成了内核：sql-api + MethodApi（MethodApi 用注解自动注册成 API，还有覆盖 sql-api 的能力）。为了写这套东西，我受了很大的阻力，但后来全公司也大规模用上了。

再后来，我（被离开了）那家公司（2026 年 7 月），被 AI 搞的大环境也很差，我也一直找不到工作。我就在想：与其让它再死在我的硬盘里，不如把它开源出来。对了，就是现在，我又开发了 mcp-api，可以动态注册 MCP Tool 了——现在已经和 WorkBuddy 打通。

这就是 SSA Service。它不太完美，但它真是我被现实踹出来的、一个想让「写接口」变轻松的笨办法。

---

## 一起共建 · 招揽维护者

> 框架已经开源，但它需要更多人一起把它养大。

🙋 **诚邀有时间、有想法的人一起维护。** 坦白说，原作者目前处境有限，所以真心希望 community 能接住它：

- **时间有限：** 原作者正在找工作、要挣钱养家，能投入在框架上的时间实在不多。
- **技术有限：** 坦诚讲，作者的 Java 水平也还很有限——框架里一定有更优雅的写法等着被你改进。
- **初心不改：** 不想让它再死在某个人的硬盘里，所以选择开源，交给真正愿意折腾的人。

**你能得到什么：** 一个真实落地、已有生产实践的后端框架舞台；你的代码会被真实项目使用；一起把它打磨成更好用的「无业务、全插件化」底座。

**欢迎：** 提 Issue、修 Bug、补文档、写示例插件、优化内核、甚至只是来聊聊架构想法——有想法、有时间的你，都是我们想找的同行者。

---

## 快速感受

```bash
# 1) 启动单体服务（自带插件目录 ./plugins）
java -Dloader.path=plugins/lib/ -jar ssa-ser-2.0.0.jar

# 2) 运行时启停插件（无需重启）
POST /sqlApi/v1/plugin.enablePlugin?pluginKey=sql-api
POST /sqlApi/v1/plugin.disablePlugin?pluginKey=push-way

# 3) 调用一个 SQL 接口
POST /sqlApi/v1/user/list  { "params": { "status": 1 } }

# 4) MethodApi：用注解注册并覆盖同名 SQL 接口
@SsaApiClass("report")
public class ReportApi {
    @SsaApiMethod(name = "profit")   // 注册名 = report.profit，同名时覆盖 sql-api
    public Object profit(Map params) { /* 复杂逻辑写这里 */ return ...; }
}

# 5) AI 智能体接入 MCP（标记 mcptool:true 的接口自动出现）
POST /mcp   →  tools/list   →  tools/call user_list { "status": 1 }
```

> 📌 以上路径/参数为基于源码结构整理的最小示意，正式文档以仓库 README 的精确端点为准。

---

## 关于 SSA

**SSA Service** —— 无业务、全插件化的企业级后端框架。
内核：ssa-core · 插件：sql-api / method-api / mcp-api / file-api / import-api / push-way / ssa-api-editor · 部署：ssa-ser / ssa-ser-client / ssa-msa（一体多态 · Fluid Topology）

*本文档基于 ssa-service 源码分析整理，用于框架对外分享与技术布道。欢迎共建。*
