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

## 快速开始

```bash
# 环境要求：JDK 17+、Maven 3.8+
git clone https://gitee.com/kevinzhaoyp/ssa-service.git
# 或：git clone https://github.com/kevinzhaoyp/ssa-service.git
cd ssa-service
mvn clean package -DskipTests

# 启动单体服务（插件由配置目录扫描，默认 ./plugins）
java -jar ssa-ser/target/ssa-ser-2.0.0.jar
```

> 📌 插件扫描目录、端口、鉴权等以 `ssa-ser/src/main/resources/application.yml` 为准；若启动依赖自定义 `loader.path` 等参数，请以仓库实际脚本确认。

调用一个 SQL 接口：

```bash
curl -X POST http://localhost:8080/sqlApi/v1/user/list \
  -H 'Content-Type: application/json' \
  -d '{ "params": { "status": 1 } }'
```

---

## 示例：写一份 YML，就有一个接口；加一行，就多一个 AI 工具

**① 定义 SQL 接口（含参数校验、数据源）：**

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

## 起源故事（节选）

> 一个被写死 SQL 折磨过的程序员，如何一步步把它逼成今天的样子。

作者最早是 Delphi 程序员，爱捣腾框架；2019 年转 Java，接手了一个底层用 JFinal 的「很烂的系统」。系统里全是写死的 SQL，改个字段都是灾难 —— 于是作者把 SQL 写成模板放到外面热加载。2019 下半年微服务大火，作者也搭了一套微服务，结果发现客户只给 1~2 台服务器，中间件一跑资源就没了，「微服务变成了灾难」。后来边做业务边写出了插件机制，把 ActiveRecord 搬进来，形成内核；全公司大规模用上。再后来，作者（被离开了）那家公司（2026 年 7 月），被 AI 搞的大环境也很差，一直找不到工作 —— 与其让它再死在硬盘里，不如开源。现在又开发了 mcp-api，可动态注册 MCP Tool，已和 WorkBuddy 打通。

> 完整故事、架构图与更多示例见项目主页（Gitee Pages / GitHub Pages）。

---

## 一起共建

框架已开源，但它需要更多人一起养大。原作者目前时间有限、Java 水平也有限，所以真心希望社区能接住它：**提 Issue、修 Bug、补文档、写示例插件、优化内核，甚至只是来聊聊架构想法 —— 有想法、有时间的你，都是我们想找的同行者。**

你的代码会被真实项目使用，一起把它打磨成更好用的「无业务、全插件化」底座。

---

## 许可证

本项目基于 **Apache License 2.0** 开源。你可以在遵守许可证条款（保留版权声明、注明修改、接受免责声明）的前提下，自由使用、修改与再分发，包括闭源商业集成。

- 许可证全文见仓库根目录 [`LICENSE`](LICENSE) 文件。
- 版权归属：作者（The Author）。如需商用授权或商业合作，欢迎通过项目主页联系。

---

*本 README 基于 ssa-service 源码整理。SSA Service —— 无业务、全插件化的企业级后端框架。欢迎共建。*
