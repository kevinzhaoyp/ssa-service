# ssa-plugins

## 模块说明

- `sql-api`：SQL API 插件模块，提供基于配置的 SQL 执行能力。
- `file-api`：文件相关 API 插件模块（上传、下载等）。
- `import-api`：数据导入导出 API 插件模块（Excel 等）。
- `push-way`：推送相关插件模块。
- `ssa-api-editor`：API 编辑相关插件模块。

## 依赖说明

- `file-api` 与 `import-api` 依赖 `ssa-core` 与 `sql-api`。
- `import-api` 额外依赖 `easyexcel`。
- `file-api` 与 `import-api` 依赖 `spring-boot-starter-web` 提供 Web 能力。

## sql-api 配置说明（SqlApiConfig）

`SqlApiConfig` 使用 `@ConfigurationProperties(prefix = "ssa.sqlapi")` 绑定配置，支持多数据源与主数据源选择。

### 示例配置


## 插件说明文档（resources）

插件可以在 `resources` 中提供说明文档（如 `plugin-doc.md`），并在 `ssa-plugin.yml` 中设置 `docPath`。运行时可通过插件管理接口读取并展示。
