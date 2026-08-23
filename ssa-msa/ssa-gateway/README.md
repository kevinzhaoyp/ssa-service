# SSA Gateway

SSA Gateway 是基于 Spring Cloud Gateway 构建的服务网关，负责请求转发、流量控制和全局过滤等功能。

## 项目结构

```
ssa-gateway/
├── pom.xml                           # Maven配置文件
├── README.md                         # 使用说明文档
├── src/main/java/com/ssa/gateway/
│   ├── GatewayApp.java               # 主启动类
│   ├── controller/
│   │   └── HealthController.java     # 健康检查控制器
│   ├── filter/
│   │   └── GlobalGatewayFilter.java  # 全局过滤器
│   ├── util/
│   │   └── JwtUtil.java              # JWT工具类（简单验证）
│   └── config/
│       ├── GatewayConfig.java        # 网关配置
│       ├── GatewayConfigProps.java   # 配置属性激活
│       ├── GatewayProperties.java    # 配置属性类
│       ├── LoadBalancerConfig.java   # 负载均衡配置
│       └── RoundRobinLoadBalancer.java # 自定义负载均衡器
└── src/main/resources/
    └── application.yml               # 应用配置文件
```

## 开发运行

### 环境要求

- JDK 17+
- Maven 3.8+
- Redis 服务
- Nacos 服务注册中心

### 快速启动

Windows环境可直接运行 `run_dev.bat` 脚本启动开发模式。

### 配置说明

- 端口: 8080
- 服务发现: 使用Nacos
- 负载均衡: 自定义轮询策略
- 限流: Redis实现的令牌桶算法
- 安全: JWT简单验证（非Spring Security）

## 功能特性
1. 接收所有前端请求，作为唯一入口；
2. 配置白名单（放行登录接口、认证授权接口、健康检查等）；
3. 轻量级安全校验：仅校验 JWT 令牌的「格式合法性、是否过期」（不解析复杂用户信息，保证网关性能）；
4. 拦截无有效令牌的请求，直接返回 401，不转发至后端；
5. 令牌有效时，转发请求至业务微服务（可将令牌或简化用户信息放入请求头）；
6. 实现全局非安全相关功能（限流、熔断、路径重写、跨域配置）。
### 1. 请求转发
- 支持基于路径的路由转发
- 支持服务发现和负载均衡
- 支持路径重写和 StripPrefix

### 2. 流量控制
- 基于IP的限流机制
- 滑动窗口限流算法
- Redis存储限流计数

### 3. 全局过滤
- 请求日志记录
- IP地址获取和传递
- 请求ID生成和传递
- 安全头处理

### 4. Token验证
- 简单的JWT Token验证（不处理认证逻辑）
- 用户身份信息传递到下游服务
- 无效令牌拒绝访问

注意：网关仅进行JWT令牌格式和有效性的基础验证，具体的认证和授权逻辑仍由下游的ssa-auth服务处理。

## 配置说明

### 路由配置
```yaml
spring:
  cloud:
    gateway:
      routes:
        # 路由到后端API服务
        - id: ssa-ser-route
          uri: lb://ssa-ser
          predicates:
            - Path=/api/**
          filters:
            - StripPrefix=1
        # 路由到客户端API服务
        - id: ssa-ser-client-route
          uri: lb://ssa-ser-client
          predicates:
            - Path=/client/**
          filters:
            - StripPrefix=1
```

### 限流配置
- 每分钟最大请求数：100次/分钟
- 限流键：基于客户端IP地址
- 存储：使用Redis进行计数

## 使用方法

### 1. 启动Nacos Server
首先需要启动Nacos Server:
```bash
# 下载并启动Nacos Server
# 访问 https://nacos.io/zh-cn/download.html 下载最新版本
# 解压后运行
sh bin/startup.sh -m standalone  # Linux/Mac
# 或
cmd bin\startup.cmd -m standalone  # Windows
```

### 2. 启动网关
```bash
mvn spring-boot:run
```

或者打包后运行：
```bash
mvn clean package
java -jar target/ssa-gateway-1.0-SNAPSHOT.jar
```

### 2. 访问服务
- 访问后端API：`http://localhost:8080/api/xxx`
- 访问客户端API：`http://localhost:8080/client/xxx`
- 访问WebSocket：`http://localhost:8080/ws/xxx`
- 访问认证服务：`http://localhost:8080/auth/xxx`

### 3. 自定义路由
可以在 `application.yml` 中添加新的路由规则：
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: custom-route
          uri: http://your-service-url
          predicates:
            - Path=/custom/**
          filters:
            - StripPrefix=1
```

## 过滤器功能

### GlobalGatewayFilter
实现了以下功能：
- 添加请求ID到请求头
- 获取和传递客户端真实IP
- 实现流量控制
- JWT Token基础验证
- 记录请求日志
- 统计请求耗时

## 监控和运维

### Actuator端点
- `/actuator/health` - 健康检查
- `/actuator/info` - 应用信息
- `/actuator/prometheus` - Prometheus指标

### 日志级别
- `DEBUG` - Spring Cloud Gateway详细日志
- `INFO` - 请求处理日志

## 部署建议

1. 在生产环境中配置Redis连接信息
2. 根据实际需求调整限流参数
3. 配置外部负载均衡器（如Nginx）指向网关
4. 启用SSL/TLS加密通信
5. 配置监控和告警系统

## 注意事项

- 网关仅进行JWT令牌的基础验证，不处理复杂认证逻辑
- 认证和授权逻辑由下游服务（如ssa-auth）处理
- 确保目标服务能够通过服务发现机制被找到
- 配置合适的超时时间以避免请求挂起
- 根据实际流量调整限流阈值
- 定期清理Redis中的限流计数数据