# 分布式微服务与网关配置

> **本文档说明 Nop 平台的分布式服务注册发现、RPC 调用、网关配置等内容**

## 一、核心概念

### 1.1 Nop 平台的分布式架构

Nop 平台采用与 Spring Boot 不同的配置体系，使用 `nop.*` 作为配置前缀而非 `spring.*`。

```
┌─────────────────────────────────────────────────────────────┐
│                    Nop 分布式服务架构                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐       ┌──────────────┐                   │
│  │   Gateway    │◄──────│   Service A  │                   │
│  │   网关       │       │  (demo-svc)  │                   │
│  └──────────────┘       └──────────────┘                   │
│         │                       │                           │
│         │                       │                           │
│         ▼                       ▼                           │
│  ┌─────────────────────────────────────┐                   │
│  │    Naming Service (服务注册中心)      │                   │
│  │  - Nacos                             │                   │
│  │  - DB (sys-dao-naming-service)       │                   │
│  │  - Consul                            │                   │
│  └─────────────────────────────────────┘                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 关键配置项

| 配置项 | 说明 | 对应 Spring Boot 配置 |
|--------|------|---------------------|
| `nop.application.name` | **应用名称/服务注册名** | `spring.application.name` |
| `nop.application.group` | **服务分组名（逻辑隔离）** | - |
| `nop.cluster.name` | **集群名称（物理/机房隔离）** | - |
| `nop.application.zone` | **可用区（地理标识）** | - |
| `nop.server.port` | 服务端口 | `server.port` |
| `nop.cluster.discovery.*` | 服务发现配置 | `spring.cloud.nacos.discovery.*` |
| `nop.cluster.registration.enabled` | 是否启用服务注册 | `spring.cloud.nacos.discovery.enabled` |
| `nop.rpc.proxy.allowed-service-names` | 允许调用的服务名列表 | - |

## 二、服务注册与发现

### 2.1 AutoRegistration 工作原理

Nop 平台通过 `AutoRegistration` bean 自动将服务注册到服务发现中心：

```java
// 位置: nop-cluster/nop-rpc-cluster/src/main/resources/_vfs/nop/rpc/beans/rpc-cluster-defaults.beans.xml
<bean id="nopAutoRegistration" class="io.nop.cluster.naming.AutoRegistration"
      feature:on="nop.cluster.registration.enabled">
    <property name="serviceName" value="@cfg:nop.application.name"/>
    <property name="groupName" value="@cfg:nop.application.group|DEFAULT" />
    <property name="clusterName" value="@cfg:nop.cluster.name|DEFAULT" />
    <property name="addr" value="@cfg:nop.server.addr|"/>
    <property name="port" value="@cfg:nop.server.port"/>
    <property name="tags" value="@cfg:nop.cluster.registration.tags|"/>
    <property name="metadata">
        <map>
            <entry key="version" value="@cfg:nop.application.version|1.0.0"/>
            <entry key="zone" value="@cfg:nop.application.zone|"/>
            <entry key="kind" value="@cfg:nop.application.kind|http"/>
        </map>
    </property>
</bean>
```

**关键点**：
- 服务名从 `nop.application.name` 配置读取
- 如果未配置 `nop.application.name`，则使用 `AppConfig.appName()` 作为默认值
- 启动时自动注册，停止时自动注销

### 2.2 配置服务注册名

#### 方式一：在 application.yaml 中配置（推荐）

```yaml
nop:
  application:
    name: demo-service    # 服务注册名

  cluster:
    discovery:
      sys-dao-naming-service:
        enabled: true     # 启用 DB 模式的服务发现
    registration:
      enabled: true       # 启用服务注册
```

#### 方式二：在 bootstrap.yaml 中配置

```yaml
nop:
  application:
    name: rpc-demo-consumer

  cluster:
    discovery:
      nacos:
        enabled: true
        server-addr: localhost:8848
```

### 2.3 服务发现实现方式

Nop 平台支持多种服务发现实现：

#### 1. DB 模式（内置）

使用数据库表存储服务实例信息，适合小型项目或开发环境：

```yaml
nop:
  cluster:
    discovery:
      sys-dao-naming-service:
        enabled: true
```

**优点**：
- 无需额外部署 Nacos/Consul
- 配置简单，适合开发测试
- 使用共享数据库即可

**缺点**：
- 性能不如专业注册中心
- 不适合大规模生产环境

#### 2. Nacos 模式

```yaml
nop:
  cluster:
    discovery:
      nacos:
        enabled: true
        server-addr: localhost:8848
        namespace: dev
        group: DEFAULT_GROUP
```

#### 3. 自定义实现

实现 `INamingService` 接口，可集成其他注册中心（Consul、Zookeeper 等）。

### 2.4 完整配置示例

#### 示例1：demo-service（服务提供者）

```yaml
# nop-spring-gateway-demo-service/src/main/resources/application.yaml
  application:
    name: demo-service        # 服务注册名
    group: user-center       # 服务分组（逻辑隔离）

  cluster:
    name: BJ-IDC            # 集群名称（物理/机房隔离）
    discovery:
      sys-dao-naming-service:
        enabled: true     # 启用 DB 模式服务发现
    registration:
      enabled: true       # 启用自动注册

  orm:
    init-database-schema: true
    enable-tenant-by-default: false

  dao:
    use-parent-data-source: true

spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:./db/demo-service;AUTO_SERVER=TRUE
    username: sa
    password:

server:
  port: 8082
```

#### 示例2：rpc-demo-consumer（服务消费者）

```yaml
# nop-rpc-client-demo/src/main/resources/bootstrap.yaml
nop:
  application:
    name: rpc-demo-consumer

  cluster:
    discovery:
      nacos:
        enabled: true
        server-addr: localhost:8848

---
# nop-rpc-client-demo/src/main/resources/application.yaml
nop:
  rpc:
    proxy:
      allowed-service-names: '*'   # 允许调用所有服务

  cluster:
    registration:
      enabled: true
```

## 三、网关配置

### 3.1 TCC 分布式事务网关

Nop 平台提供了内置的分布式事务网关，支持 TCC 模式：

```yaml
nop:
  # TCC 网关配置
  tcc:
    gateway:
      default-txn-group: demo              # 默认事务组
      auto-create-transaction: true        # 自动创建事务

  # HTTP 过滤器
  gateway:
    http-filter:
      enabled: true                        # 启用网关 HTTP 过滤器

  # 服务发现
  cluster:
    discovery:
      sys-dao-naming-service:
        enabled: true

  # 数据源配置
  dao:
    use-parent-data-source: true
```

**TCC 网关功能**：
- 自动管理分布式事务
- 支持事务补偿
- HTTP 请求自动包装为分布式事务
- 与服务发现集成

### 3.2 完整网关配置示例

```yaml
# nop-spring-gateway/src/main/resources/application.yaml
nop:
  # TCC Gateway 配置
  tcc:
    gateway:
      default-txn-group: demo
      auto-create-transaction: true

  # 网关 HTTP 过滤器
  gateway:
    http-filter:
      enabled: true

  # 启用 DB 模式的服务发现
  cluster:
    discovery:
      sys-dao-naming-service:
        enabled: true

  # ORM 配置
  orm:
    init-database-schema: true
    enable-tenant-by-default: false

  # 使用 Spring 管理的数据源
  dao:
    use-parent-data-source: true

# Spring 数据源配置 - 使用与 demo-service 共享的数据库
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:./db/demo-service;AUTO_SERVER=TRUE
    username: sa
    password:

# 服务器配置
server:
  port: 8081
  http2:
    enabled: true

# 日志配置
logging:
  level:
    ROOT: INFO
    io.nop.tcc: DEBUG
    io.nop.gateway: DEBUG

# 健康检查
management:
  health:
    probes:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health

# 开发模式配置
"%dev":
  nop:
    debug: true
    tcc:
      gateway:
        auto-create-transaction: true

  logging:
    level:
      ROOT: DEBUG
      io.nop.tcc: TRACE
```

### 3.3 网关路由配置

网关路由通过服务发现自动发现后端服务，无需手动配置路由规则。

**工作流程**：
1. 网关收到 HTTP 请求
2. 根据请求头或路径识别目标服务
3. 通过 NamingService 查询服务实例
4. 负载均衡选择一个实例
5. 转发请求到后端服务

## 四、RPC 调用配置

### 4.1 允许调用的服务

Nop 平台的安全机制要求显式声明允许调用的服务：

```yaml
nop:
  rpc:
    proxy:
      allowed-service-names: '*'          # 允许所有服务
      # 或者指定具体服务
      # allowed-service-names: 'demo-service,user-service,order-service'
```

### 4.2 服务实例过滤器

Nop 平台提供了多种服务实例过滤器，用于负载均衡和路由：

```xml
<!-- 位置: nop-cluster/nop-rpc-cluster/src/main/resources/_vfs/nop/rpc/beans/rpc-cluster-defaults.beans.xml -->
<util:list id="nopRequestServiceInstanceFilters">
    <ref bean="nopServiceInstanceFilter_healthy"/>      # 只选择健康的实例
    <ref bean="nopServiceInstanceFilter_specific"/>     # 支持指定特定实例
    <ref bean="nopServiceInstanceFilter_tag"/>          # 按标签过滤
    <ref bean="nopServiceInstanceFilter_zone"/>         # 按可用区过滤
    <ref bean="nopServiceInstanceFilter_route"/>        # 按路由规则过滤
</util:list>
```

### 4.3 负载均衡策略

```xml
<!-- 随机策略 -->
<bean id="nopServerChooser_random" parent="nopServerChooser_base">
    <property name="loadBalance" ref="nopLoadBalance_random"/>
</bean>

<!-- 轮询策略 -->
<bean id="nopServerChooser_roundRobin" parent="nopServerChooser_base">
    <property name="loadBalance" ref="nopLoadBalance_roundRobin"/>
</bean>

<!-- 最少活跃调用数策略 -->
<bean id="nopServerChooser_leastActive" parent="nopServerChooser_base">
    <property name="loadBalance" ref="nopLoadBalance_leastActive"/>
</bean>

<!-- 默认使用随机策略 -->
<alias alias="nopServerChooser_default" name="nopServerChooser_random"/>
```

### 4.4 RPC 调用示例

#### 1. 声明 RPC 接口

```java
// 在 {appName}-api 模块中定义接口
@BizModel("DemoService")
public interface IDemoServiceBiz {
    
    @BizQuery
    String hello(@Name("name") String name);
    
    @BizMutation
    DemoResponse createOrder(@Name("request") CreateOrderRequest request);
}
```

#### 2. 通过 RPC 调用远程服务

```java
@Inject
IDemoServiceBiz demoService;  // 自动注入 RPC 代理

@BizQuery
public String callRemoteService(@Name("name") String name) {
    // 框架会自动通过 NamingService 发现 demo-service 的实例
    // 并进行负载均衡和远程调用
    return demoService.hello(name);
}
```

## 五、配置文件加载顺序

### 5.1 Nop 配置加载优先级

```
优先级从高到低：
1. 命令行参数 (-Dnop.xxx=yyy)
2. 系统环境变量 (NOP_XXX)
3. 配置中心 {nop.application.name}-{nop.profile}.yaml
4. 配置中心 {nop.application.name}.yaml
5. application-{profile}.yaml
6. application.yaml
7. bootstrap-{profile}.yaml
8. bootstrap.yaml
```

### 5.2 Profile 配置

```yaml
# application.yaml
nop:
  application:
    name: demo-service

  cluster:
    discovery:
      sys-dao-naming-service:
        enabled: true

# 开发环境配置
"%dev":
  nop:
    debug: true

  logging:
    level:
      ROOT: DEBUG
      io.nop: TRACE

# 生产环境配置
"%prod":
  nop:
    cluster:
      discovery:
        nacos:
          enabled: true
          server-addr: ${NACOS_ADDR:nacos:8848}
```

## 六、与 Spring Boot 的差异

### 6.1 配置前缀对比

| 功能 | Nop 配置 | Spring Boot 配置 |
|------|----------|-----------------|
| 应用名称 | `nop.application.name` | `spring.application.name` |
| 服务端口 | `nop.server.port` | `server.port` |
| 服务发现 | `nop.cluster.discovery.*` | `spring.cloud.nacos.discovery.*` |
| 服务注册 | `nop.cluster.registration.enabled` | `spring.cloud.nacos.discovery.enabled` |

### 6.2 配置文件差异

#### Spring Boot 风格（不推荐）

```yaml
spring:
  application:
    name: rpc-demo-producer
  cloud:
    nacos:
      discovery:
        enabled: true
        server-addr: 127.0.0.1:8848
```

#### Nop 平台风格（推荐）

```yaml
nop:
  application:
    name: demo-service
  cluster:
    discovery:
      nacos:
        enabled: true
        server-addr: localhost:8848
    registration:
      enabled: true
```

### 6.3 为什么使用 nop.* 配置？

1. **框架无关性**：Nop 平台可以运行在 Spring、Quarkus、Solon 等多种框架上
2. **统一配置**：无论底层框架是什么，配置方式保持一致
3. **增强功能**：Nop 的配置系统支持 Delta 定制、动态更新等高级特性
4. **更好的 IDE 支持**：通过 XDef 元模型提供配置提示和验证

## 七、常见场景配置

### 7.1 场景1：单机开发环境

使用 DB 模式的服务发现，无需额外部署注册中心：

```yaml
nop:
  application:
    name: my-service

  cluster:
    discovery:
      sys-dao-naming-service:
        enabled: true
    registration:
      enabled: true

  orm:
    init-database-schema: true

spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:./db/my-service;AUTO_SERVER=TRUE
    username: sa
    password:

server:
  port: 8080
```

### 7.2 场景2：微服务集群（使用 Nacos）

```yaml
# bootstrap.yaml
nop:
  application:
    name: order-service

  cluster:
    discovery:
      nacos:
        enabled: true
        server-addr: ${NACOS_ADDR:nacos:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: DEFAULT_GROUP

---
# application.yaml
nop:
  cluster:
    registration:
      enabled: true

  rpc:
    proxy:
      allowed-service-names: 'user-service,product-service,payment-service'

server:
  port: ${PORT:8080}
```

### 7.3 场景3：API 网关 + 分布式事务

```yaml
nop:
  application:
    name: api-gateway

  tcc:
    gateway:
      default-txn-group: default
      auto-create-transaction: true

  gateway:
    http-filter:
      enabled: true

  cluster:
    discovery:
      nacos:
        enabled: true
        server-addr: nacos:8848

  rpc:
    proxy:
      allowed-service-names: '*'

server:
  port: 80
```

## 八、故障排查

### 8.1 服务未注册

**症状**：服务启动后，在注册中心看不到服务实例

**检查清单**：
1. 确认 `nop.application.name` 已配置
2. 确认 `nop.cluster.registration.enabled: true`
3. 确认服务发现已启用（如 `nop.cluster.discovery.sys-dao-naming-service.enabled: true`）
4. 检查日志中是否有 AutoRegistration 相关错误

### 8.2 RPC 调用失败

**症状**：调用远程服务时报错

**检查清单**：
1. 确认目标服务已注册到注册中心
2. 确认 `nop.rpc.proxy.allowed-service-names` 包含目标服务名
3. 确认服务名拼写正确（区分大小写）
4. 检查网络连通性

### 8.3 网关路由失败

**症状**：网关无法转发请求到后端服务

**检查清单**：
1. 确认后端服务已注册
2. 确认网关和服务使用同一个注册中心
3. 检查网关日志中的路由信息
4. 确认 `nop.gateway.http-filter.enabled: true`

## 九、最佳实践

### 9.1 服务命名规范

```yaml
# ✅ 推荐：使用有意义的名称，用中划线分隔
nop:
  application:
    name: user-service
    # name: order-service
    # name: payment-service

# ❌ 不推荐：使用无意义或过于简单的名称
nop:
  application:
    name: service1
    # name: app
    # name: test
```

### 9.2 环境隔离

```yaml
# 使用 namespace 或 group 隔离不同环境
nop:
  cluster:
    discovery:
      nacos:
        namespace: ${ENV:dev}      # dev/test/prod
        group: ${APP_GROUP:DEFAULT}
```

### 9.3 服务分组

```yaml
# 使用 metadata 对服务进行分组
nop:
  application:
    name: user-service
    group: user-center
    version: 1.0.0
    zone: zone-a

  cluster:
    registration:
      tags:
        - internal     # 内部服务
        - v1           # 版本标签
```

### 9.4 健康检查配置

```yaml
management:
  health:
    probes:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,info

server:
  port: 8080
```

## 十、参考资源

- [配置管理指南](../04-core-components/config-management.md)
- [事务管理指南](../04-core-components/transaction.md)
- [模块依赖文档](../02-architecture/module-dependencies.md)
- [API 开发指南](./api-development.md)

## 十一、常见问题

### Q1: 为什么服务注册名必须是 nop.application.name？

**A**: Nop 平台的 `AutoRegistration` bean 默认从 `nop.application.name` 读取服务名。这是框架的设计约定，与 Spring Boot 的 `spring.application.name` 类似。如果不配置，会使用 `AppConfig.appName()` 作为默认值，但这可能导致不可预期的行为。

### Q2: DB 模式的服务发现适合生产环境吗？

**A**: 不推荐。DB 模式主要用于开发测试环境，生产环境建议使用 Nacos、Consul 等专业的注册中心。

### Q3: 如何切换服务发现实现？

**A**: 只需修改配置，无需修改代码：

```yaml
# 从 DB 模式切换到 Nacos
nop:
  cluster:
    discovery:
      sys-dao-naming-service:
        enabled: false    # 禁用 DB 模式
      nacos:
        enabled: true     # 启用 Nacos
        server-addr: nacos:8848
```

### Q4: 网关和服务可以使用不同的服务发现实现吗？

**A**: 可以，但不推荐。建议网关和所有服务使用同一个注册中心，否则会导致服务发现失败。

### Q5: allowed-service-names 配置为 * 安全吗？

**A**: 在开发环境可以使用 `*` 允许所有服务，但在生产环境建议显式列出允许调用的服务名，以提高安全性。

### Q6: 如何查看服务注册信息？

**A**: 
- **Nacos**: 访问 Nacos 控制台（http://nacos:8848/nacos）
- **DB 模式**: 查询数据库表 `sys_service_instance`

### Q7: 服务实例的 metadata 有什么用？

**A**: metadata 用于存储服务的元数据，可用于：
- 服务路由（按 version、group、zone 路由）
- 服务过滤（如只选择特定版本的服务）
- 负载均衡策略（如优先选择同 zone 的服务）
- 监控和统计

---
