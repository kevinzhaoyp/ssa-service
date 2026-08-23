# mcp-api 插件（MCP Server v0.2，基于官方 MCP Java SDK 2.0.1）

把 MCP server 作为 **ssa 插件**随 `ssa-ser` 一起启动，让 WorkBuddy 等 MCP 客户端
通过 **HTTP（Streamable HTTP）** 直接调用 `sql-api`。它与 `sql-api`、`file-api` 完全同构，
运行在 ssa-ser **同一个 JVM** 里，直接走 `ApiRunner.safeRun(...)`，无需 HTTP 代理、无需重复实现登录。

> v0.2 升级说明：v0.1 为手搓 JSON-RPC（缺会话/SSE/版本协商/入参校验）；v0.2 改为基于
> **官方 MCP Java SDK 2.0.1** 的 `HttpServletStreamableServerTransportProvider` + `McpServer`，
> 协议层全部由 SDK 托管（initialize 版本协商、Mcp-Session-Id 会话、SSE 流式、tools/list、
> 入参 JSON Schema 校验）。
> SDK 依赖只放在 `ssa-ser`（运行期 classpath）与 `mcp-api`（编译期 `provided`），**不放在 ssa-core**。

> 相较早期的独立 Node 版（`SSA-OPC/Dev/ssa-mcp-server`，stdio 本地进程），本插件版：
> - 部署在**服务器**（嵌在 ssa-ser 内），WorkBuddy 用 `url` 远程连；
> - 复用现有 JWT 鉴权与 `SqlApiManager`，零重复代码；
> - 单次构建产物即插件 jar，由 ssa-ser 启动期自动加载。

---

## 一、架构

```
WorkBuddy (mcp.json/.mcp.json: url=http://127.0.0.1:8000/mcp 或 http://111.228.11.205:8000/mcp)
   │  HTTP + Bearer JWT（Streamable HTTP 协议）
   ▼
ssa-ser  (同一 JVM)
   ├─ /mcp   ← mcp-api 插件的 @RestController（routePrefix=/mcp 拼装，最终 POST/GET/DELETE /mcp）
   │           请求经 JwtValidationFilter 注入 cur_user 后，反射委托给官方
   │           HttpServletStreamableServerTransportProvider.doXxx 处理协议
   ├─ McpServer (SDK)  +  5 个工具（sql_api/save_user/get_current_user/list_projects/list_workpapers）
   ├─ ApiRunner.safeRun(name, params, reload)  ← 直接调用，同进程
   └─ SqlApiManager / JwtValidationFilter（cur_user 注入）
```

**协议要点**：MCP 客户端先 `initialize` 建立会话（服务端返回 `Mcp-Session-Id`），随后 `tools/list`
拿工具清单、`tools/call` 调具体工具。所有交互走 `POST /mcp`，`GET /mcp`/`DELETE /mcp` 为协议补充端点。

---

## 二、构建（务必重建 jar，否则插件不会更新）

> ⚠️ 关键：插件加载器**只读取 `plugins/mcp-api-2.0.0.jar` 这个文件，绝不读 IDEA 的 `target/classes`**。
> 任何对 `McpController` 的源码改动，**必须重新打包 jar 并覆盖到 `plugins/` 目录**，仅靠 IDEA 编译
> `mcp-api` 模块或重启 ssa-ser 都不会生效（这是排查 `/mcp` 一直 401 时踩过的最大坑）。

```bash
# 在 ssa-service 根目录
mvn -pl ssa-plugins/mcp-api -am clean package -DskipTests
# 把产物复制/覆盖到插件加载目录（由父 pom 的 plugins.output.dir 控制，通常是 ssa-service/plugins/）
copy ssa-plugins\mcp-api\target\mcp-api-2.0.0.jar plugins\mcp-api-2.0.0.jar /Y
```

jar 自动产出到 `ssa-service/plugins/mcp-api-2.0.0.jar`。

---

## 三、启用（在 ssa-ser 配置）

`ssa-ser/src/main/resources/application-dev.yml` 的 `ssa.plugin.plugins` 已加入：

```yaml
      - key: mcp-api
        fileName: mcp-api-2.0.0.jar
        state: enable
        type: jar
```

重启 ssa-ser → `AppListener` 在 `ApplicationStartedEvent` 时 `loadPlugins()` 加载本插件，
把 `McpController` 注册进主服务的 `RequestMappingHandlerMapping`（路由前缀 `/mcp` 由加载器拼装）。

**加载成功标志**：启动日志出现「插件加载成功(mcp-api-2.0.0.jar)」。
**加载失败标志**：日志出现「load plugin error」或「插件.*加载失败」（含堆栈，见下方排错）——
此时 `/mcp` 无任何 handler，所有请求都会落到鉴权层。

---

## 四、鉴权（两种模式）

| 模式 | 适用 | 配置 |
|------|------|------|
| **Bearer JWT（默认，当前生效）** | 生产 / 本地一致，规范做法 | `application-dev.yml` 的 `security.ignore-urls` **不含** `/mcp`，`/mcp` 走 `anyRequest().authenticated()` |
| **本地免 token 临时调试** | 本地 curl 冒烟、快速验证 | `security.ignore-urls` 追加 `/mcp` 与 `/mcp/**`（已在源码 `SecurityConfig` 中 `permitAll` 兜底） |

> 当前仓库状态：dev 的 `ignore-urls` 已为空，`/mcp` **默认要求 Bearer**。若要本地免 token 调试，
> 在 `application-dev.yml` 的 `security.ignore-urls` 加 `- /mcp` 和 `- /mcp/**` 即可，生产务必移除。

### 获取 JWT

```bash
curl -s -X POST http://127.0.0.1:8000/auth/v1/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"你的真实密码"}'
# 从返回 JSON 中取 token 字段（dev 下 ttl 默认约 8.3 天有效）
```

---

## 五、对接 MCP 客户端

### 5.1 对接 WorkBuddy（HTTP 型）—— 主要场景

**第 1 步：写配置文件**

WorkBuddy 启动时扫描并实际读取的是**用户级带点前缀文件** `~/.workbuddy/.mcp.json`
（不是文档里常写的 `mcp.json`，已实测确认）。把以下内容并入该文件
（保留文件中已有的其他条目，如 `connector-proxy`，只新增 `ssa-mcp`）：

```json
{
  "mcpServers": {
    "ssa-mcp": {
      "type": "http",
      "url": "http://127.0.0.1:8000/mcp",
      "description": "SSA电子审计底稿 MCP Server（本地 ssa-ser）",
      "headers": {
        "Authorization": "Bearer 这里填第四步拿到的token"
      }
    }
  }
}
```

> - 连本地 dev：`url=http://127.0.0.1:8000/mcp`
> - 连服务器：`url=http://111.228.11.205:8000/mcp`（**公网务必带 Bearer，否则裸奔**）
> - url **不要带尾斜杠**（`…/mcp` 而非 `…/mcp/`）
> - 若 dev 已配置免 token 放行，可省略 `headers`

**第 2 步：UI 启用**

打开 WorkBuddy 桌面端 → 左侧「连接器」→ 连接器管理页 → 找到 **ssa-mcp** → 点 **「信任」(Trust)**。
（WorkBuddy 仅在启动时扫描 `.mcp.json`，新增条目后若未出现，**完全退出并重启 WorkBuddy** 即可。）

**第 3 步：验证连通**

在 WorkBuddy 对话框直接说：「列出所有的审计项目」或「查一下当前登录用户」——
AI 会自动挑选 `list_projects` / `get_current_user` 工具并走 MCP 调用。能返回真实业务数据即对接成功。

> 重新 Trust 的小技巧：因鉴权方式变了，可先「取消信任」再「信任」（或重启 WorkBuddy），
> 让它用新 header 重新建连。

### 5.2 对接其他 MCP 客户端（DeepSeek harness / Cherry Studio / LobeChat / OpenWebUI 等）

MCP 是标准协议，与底层模型无关。任何支持 MCP 的客户端指向同一 URL 即可。

**（a）带 UI 的客户端**：在「MCP 服务器」配置入口填入（字段名可能为 `transport: "streamable-http"`，本质一样）：

```json
{
  "mcpServers": {
    "ssa-mcp": { "type": "http", "url": "http://127.0.0.1:8000/mcp" }
  }
}
```

**（b）自写 Python agent（以 DeepSeek 为例）**：用官方 `mcp` Python SDK 连端点，
把工具转成 OpenAI 兼容的 `tools` schema，模型决定调用后 `session.call_tool(...)` 回写：

```python
import asyncio
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client

async def main():
    async with streamablehttp_client("http://127.0.0.1:8000/mcp") as (r, w, _):
        async with ClientSession(r, w) as session:
            await session.initialize()
            tools = await session.list_tools()          # 拿到 5 个工具定义
            # 转成 DeepSeek/OpenAI 的 tools 格式
            deepseek_tools = [{
                "type": "function",
                "function": {
                    "name": t.name,
                    "description": t.description,
                    "parameters": t.inputSchema,
                }
            } for t in tools.tools]
            res = await session.call_tool("list_projects", {})  # 调具体工具
            print(res.content[0].text)

asyncio.run(main())
```

---

## 六、使用方法（在 WorkBuddy 中怎么用）

MCP 工具不是让你手动敲命令，而是**把工具交给 AI，你用自然语言说需求，AI 自动决定调用哪个工具**
并把结果带回对话。**你不需要手动「打开」它**——`ssa-mcp` 信任后，AI 自动感知下方 5 个工具。

### 6.1 工具清单（实际暴露名）

| MCP 工具名 | 后端接口 | 用途 | 关键参数 |
|-----------|---------|------|---------|
| `list_projects` | `project/list` | 查审计项目列表 | `keyword`（名称关键字）、`status`（状态） |
| `list_workpapers` | `wp/paper.list` | 查底稿列表 | `project_id`（限定项目）、`keyword` |
| `get_current_user` | `user/getUserInfo` | 取当前登录用户 | 无（**需带 Bearer**，否则 cur_user 为空） |
| `save_user` | `user/edit` | 新增/编辑用户 | `username`(必填)、`user_id`(改)、`real_name`、`phone`、`email`、`status`、`dept_id`、`role_ids` |
| `sql_api` | 通用 | 调任意 sql-api 接口 | `name`(接口名如 `project/list`)、`params`、`reload` |

> 接口名规则：`_` 与 `/` 都按路径分隔符解析；`.` 是文件名的一部分（如 `wp/paper.list`）。

### 6.2 直接用自然语言

描述清楚意图即可，AI 会挑工具（不必记工具名）：

- 「**列出我所有的审计项目**」 → 调 `list_projects`
- 「**查一下项目 XM2026000001 下面的底稿**」 → 调 `list_workpapers`（带 `project_id`）
- 「**帮我新增一个用户，用户名 zhangsan，姓名张三，手机号 138…**」 → 调 `save_user`
- 「**调用 project/list 接口看一下原始返回**」 → 调 `sql_api`

若想**强制指定工具**，在话里点名即可，例如「用 `sql_api` 调 `wp/paper.list` 并把 `project_id` 设为 XM2026000001」。

### 6.3 两个必须知道的坑

1. **`get_current_user` 必须带 Bearer**：免 token 模式下 `cur_user` 为空，后端拿不到登录身份，返回空/报错。
   只有在 `.mcp.json` 的 `headers` 里挂了 `Authorization: Bearer <token>`，这个工具才返回真实用户。
2. **`sql_api` 是「万能入口」，权限等同于后端**：它能直接调任意 sql-api 接口（`name` 传什么跑什么），
   不要在公共/不可信环境暴露；本地 dev 用着方便，外网部署务必加鉴权。

---

## 七、本地冒烟（带 Bearer，当前默认模式）

下面脚本完整演示：登录取 token → initialize 建会话 → tools/call 调 `list_projects`。
（若 dev 已配置免 token 放行，可去掉 `-H 'Authorization: Bearer $TOKEN'` 直接使用。）

```bash
BASE=http://127.0.0.1:8000
# 1) 登录取 token
TOKEN=$(curl -s -X POST "$BASE/auth/v1/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"你的真实密码"}' | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"//')
echo "TOKEN_LEN=${#TOKEN}"

# 2) initialize（带 Bearer，拿到 Mcp-Session-Id）
curl -s -D /tmp/mcp_h.txt -X POST "$BASE/mcp" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke","version":"1.0"}}}'
SID=$(grep -i '^mcp-session-id:' /tmp/mcp_h.txt | tail -1 | tr -d '\r' | awk '{print $2}')
echo "SID=$SID"

# 3) initialized 通知
curl -s -X POST "$BASE/mcp" -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -H "Mcp-Session-Id: $SID" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}' >/dev/null

# 4) tools/call list_projects
curl -s -X POST "$BASE/mcp" -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -H "Mcp-Session-Id: $SID" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_projects","arguments":{}}}'
```

预期：`initialize` 返回 200 + session；`tools/call` 返回真实业务数据（如 5 个项目）。

---

## 八、排错手册

### 8.1 不存在的 URL 报 401 而非 404（正常现象）

`SecurityConfig` 仅对 `/mcp`、`/mcp/**` 放行，其余 `anyRequest().authenticated()` 兜底。
Spring Security 过滤器链在 `DispatcherServlet` **之前**执行——未知路径落入 authenticated 规则、
无 token → 401，**请求从未到达 DispatcherServlet**，故不会出现 404。这是防端点枚举的刻意安全设计，非 bug。

对照验证：
- `/mcp/whatever`（在 `/mcp/**` 白名单内）→ 穿过 Security → DispatcherServlet 无 handler → **404**
- `/nonexist`（不在白名单）→ Security 拦截 → **401**

### 8.2 `/mcp` 一直 401（插件没加载成功）

最常见根因（已实战验证）：**插件 jar 没重建**。`McpController` 旧字节码里若残留
`SpringUtil.getBean(ObjectMapper.class)` 会在 `init()` 抛 `NoSuchBeanDefinitionException`，
插件加载失败（管理器只打日志不崩溃）→ `/mcp` 无 handler → 401。
解决：按第二节重建 `mcp-api-2.0.0.jar` 并覆盖到 `plugins/`，重启。

### 8.3 插件加载失败看不到根因

`SsaPlugin` / `SsaPluginManager` 的异常日志已改为打印完整堆栈（`log.error("...", e)`）。
重启后在日志搜 `load plugin error` 或 `插件.*加载失败`，看 `Caused by:` 即可定位。

### 8.4 首次 tools/call 超时

冷启动：HikariCP 建连 + yml 首编译会让首次调用偏慢。WorkBuddy 若超时失败，**重试一次**即可。

---

## 九、已知限制 / 后续

1. `GET /mcp` / `DELETE /mcp` 暂按官方 provider 实现（协议补充端点），`tools/call` 主链路走 `POST`。
2. 文件上传/下载/对比（`upload_file`/`download_file`/`compare_files`）待后端 `file-api` 补
   list / 工作空间 / 路径穿越防护后再加。
3. 当前已用官方 MCP Java SDK 2.0.1 的 `HttpServletStreamableServerTransportProvider`（反射委托，非手写 JSON-RPC）；
   后续若需更规范可评估 Spring AI 的 `mcp-spring-webmvc` 自动装配（SDK 2.x 已并入 Spring AI）。
