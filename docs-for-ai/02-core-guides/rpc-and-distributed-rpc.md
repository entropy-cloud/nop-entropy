# RPC 机制与分布式调用

本文描述 Nop 平台中 HTTP 接口、RPC 调用与分布式代理之间的关系与架构。

## 三层调用模型

Nop 平台有三种调用方式，内核统一，但定位不同：

| 层级 | 入口 | 调用方式 | 进程边界 | 适用场景 |
|------|------|---------|---------|---------|
| **基本 HTTP** | `/graphql` `/r/` `/p/` | `IGraphQLEngine` → 进程内 BizModel 方法 | 同 JVM | 前端 AMIS、外部 curl/E2E 测试、文件下载 |
| **类型化 RPC** | `{ServiceName}.java` 接口 | `IRpcService` 代理（AOP 或 codegen 生成） → 远程 HTTP 调用 | 跨 JVM | 跨模块/跨服务的强类型 Java 调用 |
| **分布式 RPC 代理** | `/px/{serviceName}/{opName}` | `IRpcServiceInvoker` → 服务发现 → `IRpcService` → 远程 HTTP 调用 | 跨 JVM | HTTP 网关模式，非 Java 客户端经 `/px/` 调用远程服务 |

### 基本 HTTP（进程内）

`/graphql`、`/r/{opName}`、`/p/{opName}` 直接调用**同 JVM 内**的 BizModel 方法，不涉及远程调用。详见 `api-and-graphql.md`。

### 类型化 RPC（跨进程）

由 `*.api.xml` codegen 生成的 `{ServiceName}.java` 接口，通过 AOP 代理透明地将本地方法调用转为远程 `IRpcService.callAsync()`。详见 `api-model-and-codegen.md`。

### 分布式 RPC 代理（HTTP 网关）

`/px/{serviceName}/{opName}` 是一个**通用 HTTP 代理入口**。其中 `{serviceName}` 是 RPC 注册的服务名（用于服务发现和集群路由），`{opName}` 是完整的 GraphQL 操作名（`{bizObj}__{method}`）。适用于：
- 非 Java 客户端（Python、Node.js 等）调用远程 Nop 服务
- 前端需要绕过 GraphQL schema 直接调用远程服务
- 网关模式下的服务路由

## 核心接口

### IRpcService

`IRpcService` 是 NopRPC 的最底层抽象，定义在 `nop-api-core`：

```java
interface IRpcService {
    CompletionStage<ApiResponse<?>> callAsync(
        String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken);
}
```

所有 RPC 调用最终都落到这个接口。它有多种实现：

| 实现类 | 模块 | 作用 |
|--------|------|------|
| `HttpRpcService` | `nop-rpc-http` | 将 `ApiRequest` 序列化为 HTTP 请求，通过 `IHttpClient` 发送 |
| `RpcServiceOnGraphQL` | `nop-biz` | 将 GraphQL BizModel 方法包装为 `IRpcService`，支持字段选择 |
| `ClusterRpcClient` | `nop-rpc-cluster` | 通过 `IServerChooser` 选择集群节点，向选中节点发送 RPC |
| `BroadcastRpcClient` | `nop-rpc-cluster` | 广播到集群所有节点 |
| `AopRpcService` | `nop-rpc-core` | 装饰器，叠加拦截器（限流、熔断等） |
| `CancellableRpcClient` | `nop-rpc-core` | 支持取消的装饰器 |

### IRpcServiceInvoker

`IRpcServiceInvoker` 是服务发现与路由的抽象：

```java
interface IRpcServiceInvoker {
    CompletionStage<ApiResponse<?>> invokeAsync(
        String serviceName, String serviceMethod,
        ApiRequest<?> request, ICancelToken cancelToken);
}
```

- `serviceName`：注册中心的服务名
- 内部调用 `getRpcService(serviceName).callAsync(serviceMethod, request, cancelToken)`

平台提供两种 `IRpcServiceInvoker` 实现（`nopRpcServiceInvoker` 别名按 feature 条件选择其一）：

| 实现类 | bean 名 | 路由方式 | 前提 |
|--------|---------|---------|------|
| `ClusterRpcServiceInvoker` | `nopClusterRpcServiceInvoker` | `serviceName` → 注册中心服务发现 → 负载均衡选实例 | 有注册中心（Nacos/Consul 等），自动注入 `nopHttpRpcClientInstanceProvider` |
| `HttpRpcServiceInvoker` | `nopHttpRpcServiceInvoker` | `serviceName` → `urlMap` 查 baseUrl | 开启 `nop.rpc.service-mesh.enabled` |

`/px/` 代理入口 `runProxy()` 使用 `nopProxyRpcServiceInvoker` bean。**该 bean 平台不预定义，应用必须自行配置**（可 alias 到 `nopRpcServiceInvoker` 或定义独立实现）。

## `/px/` 组合规则与配置

### serviceName 与 opName 的关系

```
POST /px/nop-auth-service/NopAuthUser__findPage
     └── serviceName ──┘ └── opName ───────────┘
          RPC 服务注册名         GraphQL 操作名 (bizObj__method)
```

`serviceName` 与 `opName` 中的 `bizObj`**可以不同**，各司其职：

| 参数 | 作用 | 值示例 | 约束 |
|------|------|--------|------|
| `serviceName` | 服务发现：定位目标集群/实例 | `nop-auth-service` | 必须在 `nop.rpc.proxy.allowed-service-names` 白名单中 |
| `opName` | 方法路由：指定目标 BizModel 和方法 | `NopAuthUser__findPage` | 必须符合 `{bizObj}__{method}` 格式，目标服务必须注册了该 operation |

`serviceName` 只决定"发到哪个节点"，`opName` 只决定"调用哪个方法"，两者独立。

### 配置步骤

#### 1. 定义 nopProxyRpcServiceInvoker bean

通过 alias 复用现有 `nopRpcServiceInvoker`（推荐）：

```xml
<!-- 复用 ClusterRpcServiceInvoker（基于服务发现） -->
<alias name="nopRpcServiceInvoker" alias="nopProxyRpcServiceInvoker"/>
```

或独立定义：

```xml
<bean id="nopProxyRpcServiceInvoker" class="io.nop.rpc.cluster.ClusterRpcServiceInvoker">
    <property name="serverChooser" ref="nopServerChooser_default"/>
    <property name="interceptors" ref="nopRpcServiceClientInterceptors"/>
    <property name="retryCount" value="1"/>
    <property name="allowedServiceNames" value="*"/>
</bean>
```

#### 2. 配置 serviceName 白名单

通过配置项 `nop.rpc.proxy.allowed-service-names` 控制哪些 RPC 服务名可被代理调用：

```yaml
nop:
  rpc:
    proxy:
      allowed-service-names: nop-auth-service,nop-file-service
      # 或允许所有：allowed-service-names: *
```

**默认值为空**（不允许任何服务），不配置则所有 `/px/` 请求被 `ClusterRpcServiceInvoker.isAllowedService()` 拒绝。

#### 3. 配置服务发现

`ClusterRpcServiceInvoker` 依赖于标准 Nop RPC 集群配置：

```yaml
nop:
  cluster:
    naming:
      # 配置注册中心地址（以 Nacos 为例）
      service-addr: ${nacos.addr:localhost:8848}
  application:
    name: my-app                     # 本地应用名，用于注册
```

### opName 的合法性

`opName` 必须是目标服务上已注册的 GraphQL operation。目标服务收到转发请求后执行：
```java
// 目标端
GraphQLFieldDefinition action = schemaLoader.getOperationDefinition(null, opName);
```

如果 `opName` 格式错误或目标服务未注册该 operation，返回 `ERR_GRAPHQL_UNDEFINED_OBJECT` 或 `ERR_GRAPHQL_UNKNOWN_OPERATION`。

`opName` 的标准格式 `{bizObj}__{method}` 对应的 BizModel 注册规则见 `api-model-and-codegen.md`。

### 典型配置对照

| 场景 | serviceName | opName | 说明 |
|------|-------------|--------|------|
| 调用远程认证服务的用户查询 | `nop-auth-service` | `NopAuthUser__findPage` | `serviceName` 来自注册中心的服务注册名，`opName` 来自 GraphQL schema |
| 调用远程文件服务的文件上传 | `nop-file-service` | `NopFileRecord__save` | 同上 |
| 调用远程工作流服务 | `nop-wf-service` | `NopWfStep__findList` | 同上 |

## HTTP 异步架构

**Nop 平台的所有 HTTP 入口都是异步的**，所有 `GraphQLWebService` 方法都返回 `CompletionStage<T>`：

| 入口 | 方法 | 返回类型 |
|------|------|---------|
| `runGraphQL(body, responseBuilder)` | `CompletionStage<T>` |
| `runRest(opType, opName, requestBuilder, responseBuilder)` | `CompletionStage<T>` |
| `doPageQuery(opType, query, selection, body, responseBuilder)` | `CompletionStage<T>` |
| `runProxy(serviceName, serviceMethod, requestBuilder, responseBuilder)` | `CompletionStage<T>` |

框架层面所有 HTTP 请求都是异步处理的（Servlet 3.1 async / Quarkus reactive），不阻塞工作线程。这与服务接口层面定义的 `CompletionStage` 变体（参见 `async-service-guide.md`）是正交的两种异步：

- **框架异步**：HTTP 传输层不阻塞线程，所有请求默认以 `CompletionStage` 形式处理
- **服务接口异步**：业务代码返回 `CompletionStage` 实现非阻塞编排

## RPC Token 转发机制

当 `/px/{serviceName}/{opName}` 接收到请求时，认证令牌自动转发到目标服务。

### 转发链路

```
客户端请求 (HTTP headers: authorization, nop-locale, nop-tenant 等)
  │
  ▼
SpringGraphQLWebService.getHeaders()  ← 捕获所有 HTTP headers（排除 connection, accept 等）
  │
  ▼
buildRequest() → ApiRequest.headers = getHeaders()  ← headers 进入 ApiRequest
  │
  ▼
runProxy() → ContextBinder.init(request)  ← 选择性传播到 IContext
  │            (nop-locale, nop-timezone, nop-tenant,
  │              nop.rpc.propagate-headers 配置的 headers)
  │
  ▼
IRpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, cancelToken)
  │
  ▼
ClusterRpcClient.callAsync() → 服务发现 → 选中实例
  │
  ▼
HttpRpcService.toHttpRequest()  ← ApiRequest.headers → HTTP headers 转发给目标服务
```

**关键结论：所有原始请求头（`authorization`、`x-access-token`、`cookie` 等）都会被自动转发。** `SpringMvcHelper.getHeaders()` 捕获几乎所有 HTTP 头（仅排除 `connection`、`accept`、`accept-encoding`、`content-length`），这些头原封不动地进入 `ApiRequest.headers`，最终作为 HTTP 头转发到目标服务。

### ContextBinder 的上下文传播

`ContextBinder.initContext()` 从 `ApiRequest` 头部提取以下信息到当前的 `IContext`：

| 头部 | `IContext` 属性 | 来源 |
|------|----------------|------|
| `nop-locale` | `locale` | `ApiHeaders.getLocale()` |
| `nop-timezone` | `timezone` | `ApiHeaders.getTimeZone()` |
| `nop-tenant` | `tenantId` | `ApiHeaders.getTenant()` |
| 自定义传播头 | `propagateRpcHeaders` | `nop.rpc.propagate-headers` 配置 |

`nop.rpc.propagate-headers` 默认值：`nop-svc-route,nop-tags,nop-client-addr`

这些上下文信息在线程内可见，用于多租户、国际化、灰度路由等场景。但需要注意：`IContext` 的传播是针对当前处理线程的辅助信息，**实际的 headers 透传不依赖 `IContext`**，而是通过 `ApiRequest.headers` 原样传递。

## 分布式 RPC 代理（`/px/`）架构

### URL 格式

```
POST /px/{serviceName}/{opName}
```

- `{serviceName}`：RPC 注册的服务名，用于服务发现（如 `nop-auth-service`）
- `{opName}`：完整的 GraphQL 操作名 `{bizObj}__{method}`（如 `NopAuthUser__findPage`）

`{serviceName}` 与 `{opName}` 中的 `{bizObj}` 不一定相同——前者是部署层面的 RPC 服务注册名，后者是 BizModel 的对象名。

### 完整处理流程

```
POST /px/nop-auth-service/NopAuthUser__findPage
  │
  ▼
SpringGraphQLWebService.proxy(serviceName="nop-auth-service",
                              serviceMethod="NopAuthUser__findPage",
                              body)
  │
  ▼
GraphQLWebService.runProxy(serviceName, serviceMethod, requestBuilder, responseBuilder)
  │
  ├── 1. 获取 nopProxyRpcServiceInvoker bean（应用定义）
  ├── 2. 构建 ApiRequest（headers 来自 getHeaders()、data 来自 body、
  │                          selection 来自 ?selection= 参数）
  ├── 3. ContextBinder.init() 绑定请求上下文
  ├── 4. invoker.invokeAsync(serviceName, serviceMethod, request, ctx)
  │      └── ClusterRpcServiceInvoker.invokeAsync()
  │           ├── isAllowedService(serviceName)          ← 白名单校验
  │           ├── getRpcService(serviceName)             ← 创建 ClusterRpcClient
  │           │    └── ClusterRpcClient
  │           │         ├── serverChooser.getServers(serviceName)  ← 按服务名发现
  │           │         ├── clientProvider.getRpcClientInstance(instance)
  │           │         │    └── HttpRpcService(client, DefaultRpcUrlBuilder(baseUrl))
  │           │         └── HttpRpcService.callAsync(serviceMethod, request, token)
  │           │              ├── urlBuilder.buildUrl(request, serviceMethod)
  │           │              │    → baseUrl + "/r/" + "NopAuthUser__findPage"
  │           │              ├── toHttpRequest() → HttpRequest(headers, body)
  │           │              └── client.fetchAsync(req)  ← HTTP 请求发到目标
  │           └── 返回 ApiResponse
  └── 5. 响应转换 (ApiResponse → HTTP Response)
```

### 目标端接收

目标收到 `POST /r/NopAuthUser__findPage`，为标准 REST 调用：

```
GraphQLWebService.runRest()
  → engine.initRpcContext(context, null, "NopAuthUser__findPage", request)
  → BizObjectManager.getOperationDefinition("NopAuthUser__findPage")
     → 按 __ 拆分为 bizObjName="NopAuthUser", bizAction="findPage" ← 正确路由
```

`{opName}` 本身包含了完整的 BizModel 路由信息，因此**不需要在代理层做操作名拼接**。

### 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.rpc.propagate-headers` | `nop-svc-route,nop-tags,nop-client-addr` | 传播到 `IContext` 的头部 |
| `nop.rpc.proxy.allowed-service-names` | 空（不允许任何服务） | `/px/` 白名单，逗号分隔 |
| `nop.cluster.rpc.use-https` | `false` | 是否通过 HTTPS 调用目标 |
| `nop.cluster.client-retry-count` | `2` | 客户端调用重试次数 |

### 服务发现

`ClusterRpcServiceInvoker` 通过 `IServerChooser` 选择实例，默认使用随机负载均衡。实例过滤器链：健康检查 → 指定实例 → 标签匹配 → 区域匹配 → 路由匹配。

## 三种调用的选择指南

| 场景 | 推荐方式 |
|------|---------|
| 前端 AMIS 页面调用 | `/graphql`（框架默认入口） |
| curl / E2E 测试 | `/r/{operationName}` |
| 文件上传下载 | `/p/{operationName}` |
| Java 服务 A 调用 Java 服务 B（跨进程） | 类型化 RPC（`*.api.xml` 生成接口） |
| 非 Java 客户端调用远程 Nop 服务 | `/px/{serviceName}/{opName}` |
| JSON-RPC 协议兼容 | `/jsonrpc` |

## 相关文档

- `./api-and-graphql.md`（HTTP 入口路由表）
- `./api-model-and-codegen.md`（类型化 RPC 接口与 SPI 接口）
- `../04-reference/async-service-guide.md`（服务接口异步调用）
- `../04-reference/source-anchors.md`（实现锚点）
