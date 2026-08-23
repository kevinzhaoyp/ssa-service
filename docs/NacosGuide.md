# Nacos 集成指南

## 概述

Nacos 是阿里巴巴开源的服务发现与配置管理平台，为微服务架构提供服务发现、配置管理、动态DNS服务等功能。在 SSA 项目中，Nacos 主要用于：

1. 服务注册与发现
2. 统一配置管理
3. 服务健康监测

## 安装 Nacos

### 1. 下载 Nacos

访问 [Nacos 官方网站](https://nacos.io/zh-cn/download.html) 下载最新版本的 Nacos。

### 2. 解压安装包

```bash
tar -xvf nacos-server-$version.tar.gz
cd nacos/bin
```

### 3. 启动 Nacos 服务

#### Linux/Unix/Mac:
```bash
sh startup.sh -m standalone
```

#### Windows:
```cmd
cmd bin\startup.cmd -m standalone
```

> 注意：`-m standalone` 表示以单机模式启动，生产环境建议使用集群模式。

### 4. 访问 Nacos 控制台

启动完成后，访问 `http://localhost:8848/nacos`，默认用户名和密码均为 `nacos`。

## 配置说明

### 1. 服务发现配置

在各服务的 `application.yml` 文件中配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848  # Nacos服务器地址
        namespace: dev               # 命名空间，可根据环境调整
        group: DEFAULT_GROUP         # 分组
        username: nacos              # 用户名（如果启用了认证）
        password: nacos              # 密码（如果启用了认证）
```

### 2. 配置管理配置

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848  # Nacos配置服务器地址
        file-extension: yaml         # 配置文件格式
        namespace: dev               # 命名空间
        group: DEFAULT_GROUP         # 分组
        username: nacos              # 用户名
        password: nacos              # 密码
```

### 3. 配置文件命名规则

Nacos 配置文件命名规则：`${prefix}-${spring.profiles.active}.${file-extension}`

- `prefix` 默认为 `${spring.application.name}`
- `spring.profiles.active` 即为环境标识
- `file-extension` 为配置文件格式，如 yaml、properties

例如：`ssa-gateway-dev.yaml`

## 服务注册与发现

### 1. 服务注册

在服务启动时，Spring Boot 应用会自动向 Nacos 注册自身信息，包括：

- 服务名
- IP 地址
- 端口号
- 健康状态
- 元数据信息

### 2. 服务发现

通过 `@LoadBalanced` 注解和 `RestTemplate` 或 `WebClient` 可以实现服务间调用：

```java
@LoadBalanced
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}

@Autowired
private RestTemplate restTemplate;

// 调用服务
String result = restTemplate.getForObject("http://ssa-oauth/auth/v1/validate", String.class);
```

## 配置管理

### 1. 动态配置

在 Nacos 控制台创建配置文件，服务启动时会自动拉取配置，并支持运行时动态刷新。

### 2. 配置刷新

使用 `@RefreshScope` 注解标记需要动态刷新的 Bean：

```java
@Component
@RefreshScope
public class SomeConfigBean {
    @Value("${some.property}")
    private String property;
}
```

## 在 SSA 项目中的应用

### 1. SSA Gateway

网关服务使用 Nacos 实现动态路由，自动发现后端服务实例：

- 自动注册网关服务
- 动态路由到后端服务
- 服务健康监测

### 2. SSA OAuth

认证服务注册到 Nacos，供其他服务调用：

- 提供统一认证接口
- 服务间认证调用
- 集中权限管理

## 最佳实践

### 1. 环境隔离

使用不同的命名空间区分开发、测试、生产环境：

- DEV: 开发环境
- TEST: 测试环境  
- PROD: 生产环境

### 2. 配置分离

将不同类型的配置分离到不同分组：

- DEFAULT_GROUP: 通用配置
- CONFIG_GROUP: 配置相关
- ROUTE_GROUP: 路由相关

### 3. 安全配置

生产环境建议启用 Nacos 认证：

```yaml
nacos:
  core:
    auth:
      enabled: true
```

## 故障排查

### 1. 服务未注册

- 检查 Nacos 服务是否正常运行
- 确认网络连通性
- 检查配置文件中的地址是否正确

### 2. 配置未生效

- 检查配置文件名是否符合命名规范
- 确认命名空间和分组是否正确
- 检查配置内容格式是否正确

### 3. 服务间调用失败

- 确认服务均已注册到 Nacos
- 检查服务名拼写是否正确
- 验证网络连通性