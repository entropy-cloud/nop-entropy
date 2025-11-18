# API无缝升级方案：从推模式到拉模式的架构演进

知乎上有人问了一个问题：**Java微服务API版本兼容如何实现平滑升级**？

在微服务架构中，服务频繁迭代导致API版本差异增大，而客户端（如App、Web前端）的升级节奏往往滞后，这常常引发兼容性问题，甚至导致线上故障。常见的版本控制策略，比如在URL路径中加版本号（`/v1/user`）或使用请求头区分版本，虽然能明确区分不同版本，但也带来了维护多个版本接口的沉重成本，并增加了客户端的适配难度。

本文将分析这一问题的根源所在，并介绍 **NopGraphQL 框架如何创新地解决这一问题**。

## 1. 问题根源：推模式导致的共变问题

REST 本质上是“推模式”，在理论层面必然导致**共变问题**。
REST API 的设计范式是由服务端预先定义每个端点返回的完整数据结构（DTO）。客户端被动接收这些数据，无法控制内容粒度。这种“服务端推送、客户端全盘接受”的模型，在信息论上属于 **封闭输出系统**。

一旦服务端对返回结构做出变更——无论是新增字段、修改嵌套结构，还是调整字段语义——所有消费该接口的客户端都必须同步适配。这就形成了典型的 **共变耦合**（covariance coupling）：服务端和客户端被迫在版本上强绑定，违背了微服务“独立演进”的核心原则。

即使采用 URL 路径（`/v1/user`）或 Accept Header 等版本控制手段，也只是将耦合显式化，并未消除根本问题：**每个版本仍是一个刚性、全量的数据契约**，维护成本随版本数线性甚至指数增长。

```java
// REST接口的刚性数据契约
@GetMapping("/api/v1/users/{id}")
public UserDTOV1 getUserV1() {  // 版本1的固定结构
  return userService.getUser();
}

@GetMapping("/api/v2/users/{id}")
public UserDTOV2 getUserV2() {  // 版本2的固定结构
  return userService.getUser();
}
// 每个版本都是完整的DTO，变更需要新接口
```

更糟糕的是，当 `UserDTO` 被嵌入到多个不同 API 的响应中时（如订单详情、审批流、通知中心），它的任何变动都将引发**涟漪效应**，导致大量接口连锁修改，形成典型的“组合爆炸”。

## 2. 解决方案：反转信息流向，转向拉模式

GraphQL 提出了一种颠覆性的思路：**由客户端声明所需字段，服务端按需返回**。这种“客户端驱动的拉取模型”天然支持 **渐进式演进**，其核心在于**解耦了服务端信息完整性与客户端消费粒度之间的强绑定**。

```graphql
# 2018年客户端 - 仅请求基础字段
query {
  getUser(id: "123") {
    id
    name
    email
  }
}

# 2020年客户端 - 开始使用新增的安全字段
query {
  getUser(id: "123") {
    id
    name
    email
    twoFactorEnabled  # 新增字段，老客户端不受影响
    lastLoginIp
  }
}

# 2023年客户端 - 使用完整功能集
query {
  getUser(id: "123") {
    id
    name
    email
    twoFactorEnabled
    lastLoginIp
    preferences {     # 新增嵌套对象
      theme
      language
    }
  }
}
```

在传统 REST 的“推模式”中，服务端必须为每个接口预定义一个固定的响应结构。这意味着：
- 服务端和客户端对“什么是有效数据”的理解必须完全一致；
- 一旦服务端模型扩展（例如用户对象新增 `twoFactorEnabled` 字段），要么强行让所有客户端升级以处理新字段，要么维护多个版本的 DTO 和端点。

而拉取模型彻底改变了这一范式：
- **服务端作为完整、权威的信息源，持续演进其领域模型**；
- **客户端则根据自身场景，仅拉取当前所需的字段子集**。

### GraphQL 拉模式的核心优势：
- 新增字段对旧客户端不可见；
- 字段删除可通过废弃标记逐步下线；
- 嵌套查询避免多次往返，同时保持细粒度控制。

然而，在现有 Java 微服务体系中全面切换到 GraphQL 协议面临显著障碍：
- 需要重构网关、鉴权、限流、监控等基础设施；
- 客户端（尤其是移动端或第三方）需重写调用逻辑；
- 团队需掌握新语法、类型系统和性能调优模式；
- 与 gRPC、消息队列等其他通信方式难以统一。

因此，尽管 GraphQL 思想先进，但**协议绑定限制了其在存量系统中的落地效率**。

## 3. 创新方案：NopGraphQL 的多协议通用框架

NopGraphQL 的关键创新在于：**将 GraphQL 从一种传输协议，升维为通用的信息操作引擎**。它提取 GraphQL 的核心思想——“字段级动态选择”——并将其泛化为可跨协议复用的能力。

在 Nop 中，同一个服务函数可以同时暴露为：
- REST 接口（通过 `@selection=name,email` 查询参数）
- GraphQL 查询
- gRPC 方法
- Kafka 消息处理器
- 批处理任务入口

开发者只需编写一次业务逻辑：

```java
@BizModel("NopAuthUser")
public class UserBizModel {
    @BizQuery
    public NopAuthUser getUser(
        @Name("id") String id,
        FieldSelectionBean selection  // 自动注入客户端字段选择信息
    ) {
        // 同一业务逻辑，多协议复用
        NopAuthUser user = dao.getById(id);

        // 可选：根据 selection 决定是否加载 expensive 字段
        if (selection != null && selection.hasField("totalOrders")) {
            user.setTotalOrders(orderDao.countByUserId(id));
        }

        return user;
    }
}
```

即可通过多种协议调用。

### GraphQL 协议调用：
```graphql
query {
  NopAuthUser__get(id: "123") {
    id
    name
    email
    roles {
      name
      permissions
    }
  }
}
```

### REST 协议调用：
```http
GET /r/NopAuthUser__get?id=123&@selection=id,name,email,roles{name,permissions}
```

NopGraphQL 通过统一的协议适配层，将不同协议的请求转换为标准化的内部表示：

```
GraphQL请求 → GraphQL适配器 → 统一服务调用引擎 → 业务函数
REST请求   → REST适配器    → 统一服务调用引擎 → 业务函数
gRPC请求   → gRPC适配器    → 统一服务调用引擎 → 业务函数
```

业务逻辑完全与协议解耦。开发者只需关注领域模型和字段加载逻辑，协议适配由框架自动完成。这使得团队可以在不改变现有调用链的前提下，**渐进引入“拉模式”能力**。

## 4. 核心机制：字段选择与默认策略

NopGraphQL 在字段返回策略上做了精细化设计，对 GraphQL 进行了简化，并使其自然映射到 REST 协议。

- 每个实体类型可定义一个默认字段集合 `F_defaults`（例如 `id, name, status`）；
- 当客户端未显式传入 `@selection` 时，自动返回 `F_defaults` 中的字段，行为等价于传统 REST，保障向后兼容；
- **所有新增字段默认标记为 `lazy`**：除非客户端在 `@selection` 中明确请求，否则不会加载也不会返回；
- 客户端可通过 `...F_defaults` 语法快速继承默认字段集，并叠加新增字段。

例如：
```http
GET /r/NopAuthUser__get?id=123&@selection=...F_defaults,avatarUrl,roles{name}
```
表示：“返回所有默认字段 + `avatarUrl` + `roles` 的 `name` 子字段”。

> 💡 **Lazy 字段的实现**：在 XMeta 元模型中，字段可声明为 `<prop name="avatarUrl" lazy="true">`，并且可以通过 `@BizLoader` 注解实现批量加载，避免 N+1 问题。

这种方式既保留了 REST 的简单性，又赋予了 GraphQL 的灵活性。服务端可以自由扩展模型，而客户端按需消费，**彻底解耦了 API 的演进节奏**。

### 结语

API 版本兼容的本质，不是管理多个版本，而是**消除不必要的耦合**。
NopGraphQL 通过将 GraphQL 的“拉取思想”从协议中剥离，并以 `@selection` + `F_defaults` + `lazy` 字段机制落地到多协议场景，为 Java 微服务提供了一条 **低侵入、高兼容、易演进** 的平滑升级路径。

未来，后端不应再是一堆僵化的 REST 端点，而应是一个**活的信息空间**——客户端可以像查询数据库一样，精确、安全、高效地从中拉取所需知识。

