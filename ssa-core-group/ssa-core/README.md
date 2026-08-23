# ssa-core

## 插件配置示例

使用配置文件驱动插件加载（推荐）：

```yaml
ssa:
  plugin:
    pluginPath: D:/work/SSAuditor/plugins
    plugins:
      - key: demo-plugin
        fileName: demo-plugin.jar
        state: enable
        type: jar
      - key: other-plugin
        fileName: other-plugin.jar
        state: disable
```

字段说明：
- `pluginPath`：插件 JAR 根目录（可选，不填默认 `JarHome/plugins`）
- `plugins`：插件列表
  - `key`：插件唯一标识
  - `fileName`：插件 JAR 文件名
  - `state`：`enable` 或 `disable`
  - `type`：插件类型（默认 `jar`）


## ServiceLoader 插件加载器

`ssa-core` 通过 `ServiceLoader` 发现并选择 `SsaPluginLoader` 实现：

```java
SsaPluginLoader loader = SsaPluginLoaderRegistry.getLoader(type);
```

### SPI 注册示例

在实现模块（如 `ssa-core-web`）中添加 SPI 文件：

`META-INF/services/com.ssa.plugin.SsaPluginLoader`

内容：

```
com.ssa.plugin.SpringMvcPluginLoader
```

### SPI 最小示例（扩展加载器）

1) 定义加载器实现（在扩展模块中，例如 `ssa-core-web`）：

```java
package com.ssa.plugin;

public class SpringMvcPluginLoader implements SsaPluginLoader {
    @Override
    public boolean supports(String type) {
        return type == null || type.isEmpty() || "jar".equalsIgnoreCase(type);
    }

    @Override
    public AutoCloseable load(String fileName, String namespace, ClassLoader parent) throws Exception {
        // ...创建并初始化你的 ClassLoader
        return null;
    }
}
```

2) 添加 SPI 配置文件（打进 jar 包）：

`META-INF/services/com.ssa.plugin.SsaPluginLoader`

内容：
```
com.ssa.plugin.SpringMvcPluginLoader
```

3) 运行时触发：

`SsaPluginManager.loadPlugins()` -> `SsaPlugin.load()` -> `SsaPluginLoaderRegistry.getLoader(type)`。

### 常见问题

- 报 `No plugin loader found for type: jar`：
  - 确认扩展模块（如 `ssa-core-web`）已被引入。
  - 确认 SPI 文件路径与内容正确，并已打进 jar 包。

### 触发时机

- `SsaPluginManager.loadPlugins()` 或 `enablePlugin()` 调用 `SsaPlugin.load()` 时触发。
- 加载器负责创建并返回可关闭的 `AutoCloseable` 实例，供卸载时释放资源。


## 插件描述文件（ssa-plugin.yml）

建议每个插件 Jar 内提供 `ssa-plugin.yml`，用于描述插件元信息与路由前缀（供动态注册路由使用）：

```yaml
key: sql-api
version: 2.0.0
mainClass: com.ssa.plugin.SqlApiPlugin
routePrefix: /sqlApi
dependencies:
  - mysql
  - druid
author: ssa-team
releaseDate: 2026-02-01
description: SQL API plugin 支持多数据源
```

说明：
- `key`：插件唯一标识（必填）
- `version`：插件版本（必填）
- `mainClass`：插件入口类（可选）
- `routePrefix`：动态注册路由统一前缀（可选）
- `author`：插件作者（可选）
- `releaseDate`：发布日期（可选，建议 `YYYY-MM-DD`）
- `description`：插件描述（可选）

注意：`ssa-plugin.yml` 缺少 `key/version` 时会阻止插件加载。


## 动态路由前缀

当插件 Jar 中存在 `ssa-plugin.yml` 且配置 `routePrefix` 时，`ssa-core-web` 会在注册 `@Controller/@RestController` 路由时自动加上该前缀。

## 插件市场准备

建议插件包统一包含 `ssa-plugin.yml`，并在后续市场中统一维护索引（key/version/下载地址/签名）。
