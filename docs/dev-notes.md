# 开发笔记（内部归档，非对外文档）

> 本文件由根目录旧 README.md 的开发性内容归档而来，仅供内部参考。

### 说明
SmartSpectrum Auditor - 智慧光谱

简写SSA
SSAuditor

### 源码目录
1. ssa-core api核心包
2. ssa-ser 服务端的服务
3. ssa-ser-client  客户端中的服务
4. ssa-ai AI 服务
5. ssa-plugin 插件包
6. ssa-gateway 网关服务

### 大小记录
 未增加 Security 前 46.7MB
 增加后 69-70MB

### 问题记录
1. security.ignore-urls 不能将/auth增加到白名单，要不用户密码错误时一直返回401
2. 把所有插件中的Controller移出去，是为了让APP来控制权限，插件就不用关系了。

### 名词解释
https://www.chinaacc.com/new/635_656_/2009_4_27_le6912948471724900219208.shtml

## 项目结构总览（旧版说明）

根目录 `ssa-service` 作为聚合项目与统一版本管理中心：

- `docs/` 公共文档目录
- `sql/` 数据库相关脚本（如初始化、迁移、示例数据）
- `ssa-ai/` AI 相关服务（规划/演进中）
- `ssa-core-group/` 所有服务的核心包（通用能力）
- `ssa-msa/` 微服务聚合（nacos 等微服务相关依赖在此管理）
- `ssa-plugins/` 插件包目录
- `ssa-ser/` 单体服务
- `ssa-ser-client/` 可嵌入桌面应用的单体服务

## 聚合与版本管理边界
- `ssa-service`（根 POM）：统一管理 Java 版本、Spring Cloud 版本、Spring Cloud Alibaba 版本；管理通用依赖版本（hutool、fastjson、日志框架等）
- `ssa-core-group`（核心包聚合 POM）：产出核心 JAR：ssa-core、ssa-core-web、ssa-core-starter；仅包含插件机制、API 运行入口、通用工具
- `ssa-msa`（微服务聚合 POM）：管理微服务相关依赖与配置（如 Nacos 相关）；包含子模块：ssa-gateway、ssa-oauth
- `ssa-plugins`（插件包聚合 POM）：管理插件相关子模块与扩展点
- `ssa-ser` / `ssa-ser-client`：单体服务与桌面嵌入式服务的独立 POM 管理

## ssa-core 拆分说明
`ssa-core-group` 生成三类 JAR：
- `ssa-core`：核心库（无 Spring MVC 依赖）
- `ssa-core-web`：Web 扩展（Controller、Servlet 相关）
- `ssa-core-starter`：Spring Boot 自动装配（引入即用）

推荐在 Spring Boot 服务中使用：
```xml
<dependency>
  <groupId>org.ssa</groupId>
  <artifactId>ssa-core-starter</artifactId>
  <version>2.0.0</version>
</dependency>
```

## Maven setup (Windows)
1) Download Maven: https://maven.apache.org/download.cgi (Binary zip)
2) Unzip to a folder, e.g. `C:\tools\apache-maven-3.9.6`
3) Set environment variables:
   - `MAVEN_HOME=C:\tools\apache-maven-3.9.6`
   - Add `%MAVEN_HOME%\bin` to `PATH`
4) Open a new PowerShell window and verify: `mvn -v`

## Build
```powershell
mvn -pl ssa-core -am -DskipTests compile
```
