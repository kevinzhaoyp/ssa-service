# ssa-core-group
## 推荐拆分结构
### ssa-core（纯Spring核心）
1. 依赖：spring-context、spring-beans、spring-core（必要时 spring-boot-autoconfigure）
2. 不依赖 spring-boot-starter-web
3. 仅包含：插件机制、API 运行入口、通用工具、非 Web 的扩展点
### ssa-core-web（可选扩展）
1. 依赖：spring-boot-starter-web
2. 放置：Servlet、Web 路由注册、WebPluginClassLoader 等
3. 只给需要 HTTP 的服务用
### ssa-core-starter（可发布 starter）
1. 依赖：ssa-core +（可选）ssa-core-web
2. 提供自动装配 @AutoConfiguration 让使用方“引入即用”
3. 负责配置绑定、Bean 装配、默认实现启用
### 这样做的好处
任何服务都能用 ssa-core（包括非 Web、批处理、命令行）
Web 服务需要的能力可通过 ssa-core-web 打开
发布出去更规范、可组合性更强

## When to use which

- Use `ssa-core` for non-web services or libraries.
- Use `ssa-core-web` if you need file/import APIs or Spring MVC plugin loading.
- Use `ssa-core-starter` for Spring Boot services (recommended default).

## Maven dependencies

```xml
<!-- Recommended for Spring Boot services -->
<dependency>
  <groupId>org.ssa</groupId>
  <artifactId>ssa-core-starter</artifactId>
  <version>2.0.0</version>
</dependency>

<!-- Optional: only if you do NOT want the starter -->
<dependency>
  <groupId>org.ssa</groupId>
  <artifactId>ssa-core</artifactId>
  <version>2.0.0</version>
</dependency>

<dependency>
  <groupId>org.ssa</groupId>
  <artifactId>ssa-core-web</artifactId>
  <version>2.0.0</version>
</dependency>
```

