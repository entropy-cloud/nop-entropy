# 服务层与 BizModel 默认模式

当前仓库的默认服务层就是 BizModel 层。

如果你在写普通业务逻辑，默认先考虑：

1. 标准实体服务是否可以直接复用 `CrudBizModel<T>`。
2. 自定义动作是否可以写成 `@BizQuery` / `@BizMutation`。
3. 复杂流程是否需要拆成 Processor。

## 默认服务层模型

| 场景 | 默认做法 |
|------|---------|
| 标准实体服务 | `@BizModel + extends CrudBizModel<T>` |
| 普通查询 | `@BizQuery` + `QueryBean` + `doFindList()` / `doFindPage()` |
| 普通修改 | `@BizMutation` + `requireEntity()` + `updateEntity()` / `save()` / `delete()` |
| 跨模块调用 | 注入 `I*Biz` 接口（服务实现层接口，方法必须有 `@BizQuery`/`@BizMutation`/`@BizAction` 注解） |
| 复杂流程 | BizModel 入口 + Processor |

## 最小结构

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> implements IOrderBiz {
    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }
}
```

## 方法规则

### 查询

- 使用 `@BizQuery`
- 最后一个参数通常是 `IServiceContext`
- 需要字段选择时带 `FieldSelectionBean`
- 查询能力优先走 `doFindList()`、`doFindPage()`

### 修改

- 使用 `@BizMutation`
- 取实体优先走 `requireEntity()`
- 持久化优先走 `updateEntity()`、`save()`、`delete()`
- 提交后副作用优先走 `txn().afterCommit(...)`

如果修改入口最终走 `CrudBizModel.copyToEntity()` / `OrmEntityCopier`，则 xmeta 中的 `autoExpr` 会在实体拷贝阶段执行。
对 inline 主子表关系，子实体的 `autoExpr` 可以依赖已绑定的主实体反向 relation（例如 `refPropName` 对应的 owner 属性）；不要假设必须等到整棵对象图挂载完成后才可读取主表字段。

注意：`txn().afterCommit(...)` 只有在当前已经处于事务中时才能注册。普通 `@BizMutation` 场景满足这个前提；`@BizQuery` 和其他无事务场景下不要直接套用。

### 参数与返回值

- 单个或少量参数使用 `@Name`
- 多参数输入优先 `@RequestBean`
- 多字段返回优先 `@DataBean`
- 不要把复杂返回值做成 `Map<String, Object>`

### 返回值：实体 vs DTO

**实体能表达的数据优先用实体，但 DTO 在很多场景下是正常选择。**

实体的优势：

1. `CrudBizModel.get()` 直接返回 `T extends IOrmEntity`（平台基类设计）。
2. `GraphQLExecutor.fetchSelections()` 根据客户端 GraphQL selection 逐字段获取值。
3. 客户端只能看到 xmeta 中定义的、且在 selection 中请求的字段，不会暴露整个实体。
4. 字段可见性由 xmeta 控制，不需要靠改返回类型来隐藏字段。

| 场景 | 返回什么 |
|------|---------|
| 标准 CRUD 操作 | 直接返回实体（`CrudBizModel` 已实现） |
| 自定义查询/修改 | 直接返回实体 |
| 汇总统计、聚合数据（如社区检测结果、影响分析） | `@DataBean` DTO |
| 简化视图（如符号概要、文件树） | `@DataBean` DTO |
| 组合多个来源的数据 | `@DataBean` DTO |
| 内部服务接口（不对外暴露） | 返回 core 层模型（以高性能为优先） |

如果只是限制字段可见性，在 xmeta 中配置即可，不需要为此创建 DTO。但如果返回值本身是汇总、简化、计算后的数据结构，DTO 是正确选择。

**DTO 的放置：**

1. 局部 DTO 放在 `*-dao/.../dto/`（BizModel / Processor 共享时）或 `*-service/`（仅单个 BizModel 使用时）。
2. **局部 DTO 不要放进 `*-api/` 模块。** `*-api/` 只放外部系统 RPC 调用本模块的接口和 Message Bean。详见 `dto-json-and-message-beans.md`。
3. 外部 RPC 接口需要的 Message Bean 放 `*-api/.../beans/`。

## `I*Biz` 接口的定位

`I*Biz`（如 `IOrderBiz`、`ICartBiz`）是**服务实现层接口**，由 BizModel 实现类 `implements`，同时由 `BizProxyFactoryBean` 生成动态代理供跨模块注入使用。

**关键定位：`I*Biz` 不是前端 API 接口。** 它是后端服务之间的调用契约，通过动态代理走 BizModel 执行管道（权限、预处理、状态机等），不是传统 RPC 意义上的客户端/服务端共享接口。

Nop 平台的前后端分离方式与传统 RPC 不同：
- **前端接口**：GraphQL schema 自动由 `@BizQuery` / `@BizMutation` 方法 + xmeta 生成，前端通过 `query { Order__get(id: "1") }` 调用，请求/响应类型由 GraphQL 引擎处理。
- **后端 `I*Biz` 接口**：方法签名直接使用实体类型（如 `LitemallOrder`）、`IServiceContext` 等，不需要转换为 Message Bean。注入 `I*Biz` 的调用方与 BizModel 在同一进程中，通过动态代理走 `IServiceAction` 管道。

```
前端 ──GraphQL──> GraphQL Engine ──> BizModel 方法（@BizQuery/@BizMutation）
后端模块A ──I*Biz代理──> BizObject.invoke() ──> BizModel 方法（@BizQuery/@BizMutation/@BizAction）
```

### `I*Biz` 方法必须有注解

`I*Biz` 接口上的每个方法**必须**标注以下注解之一，否则动态代理无法识别该方法（`BizProxyInvocationHandler` 通过 `getServiceActionName` 扫描注解来路由调用）：

| 注解 | 可见性 | 用途 |
|------|--------|------|
| `@BizQuery` | 前端 GraphQL 可调用，只读 | 查询类方法 |
| `@BizMutation` | 前端 GraphQL 可调用，写操作 | 修改类方法 |
| `@BizAction` | **仅后端内部可调用**，不暴露为 GraphQL 操作 | BizModel 管道内部动作（如 `requireEntity`、`saveEntity`、`updateEntity`、`doFindList` 等） |

参数上还需标注 `@Name`（命名参数）或使用 `@RequestBean`（复杂输入对象），否则代理无法正确映射参数。

```java
// 正确：I*Biz 接口方法有注解
public interface IOrderBiz extends ICrudBiz<LitemallOrder> {
    @BizQuery
    List<LitemallOrder> myOrders(@Name("orderStatus") Short orderStatus, IServiceContext context);

    @BizMutation
    LitemallOrder createOrder(@Name("addressId") String addressId,
                              @Name("message") String message,
                              IServiceContext context);
}

// 错误：缺少注解，代理无法路由
public interface IOrderBiz extends ICrudBiz<LitemallOrder> {
    List<LitemallOrder> findCheckedCart(String userId, IServiceContext context); // 缺少 @BizQuery
}
```

> **选择 `@BizAction` 还是 `@BizQuery`/`@BizMutation`？** 如果方法只被后端其他 BizModel 调用、不应暴露给前端，用 `@BizAction`。如果前端页面也需要调用，用 `@BizQuery` 或 `@BizMutation`。参考 `ICrudBiz` 接口：对外方法标 `@BizQuery`/`@BizMutation`，管道内部方法（`requireEntity`、`saveEntity`、`updateEntity` 等）标 `@BizAction`。

## 命名约定

Nop 平台回避 Controller / Service 这类命名。这些词在 Spring 中有特定含义，容易产生误解。Nop 使用 BizModel（业务模型）、Processor（处理器）、`I*Biz`（服务实现层接口）、Api（外部 RPC 接口）等名称。不要在 Nop 模块中创建 `*Controller` 或 `*Service` 类——这些职责由 BizModel 和 `I*Biz` 接口承担。

## 普通 BizModel 默认优先的 API

| 场景 | 默认方法 |
|------|---------|
| 获取实体 | `requireEntity(id, action, context)` |
| 列表查询 | `doFindList(query, this::invokeDefaultPrepareQuery, selection, context)` |
| 分页查询 | `doFindPage(query, this::invokeDefaultPrepareQuery, selection, context)` |
| 保存前端数据 | `save(data, context)` |
| 更新实体 | `updateEntity(entity, action, context)` |
| 删除实体 | `deleteEntity(entity, action, context)` / `delete(id, context)` |

## BizModel 必须对应真实聚合根

**每个 `@BizModel` 必须对应一个有 xmeta 的实体（聚合根）。** 不允许创建无 ORM 实体、无 xmeta 的"伪 BizModel"。

原因：

1. GraphQL 引擎通过 xmeta 构建 object definition。没有 xmeta，`@query:` 前端请求会报"未定义的对象"。
2. RPC `/r/` 路径不校验 GraphQL schema，所以伪 BizModel 在 RPC 测试中能通过，但浏览器页面会失败。
3. 方法应该属于它所操作的聚合根。如果操作的是任务实体，方法就放 `NopJobBizModel`；如果是跨实体的调度级操作，放 `NopJobScheduleBizModel`。

判断规则：

| 操作对象 | 应归属的 BizModel |
|---------|-----------------|
| 某实体的类型信息/关联/派生查询 | 该实体本身的 BizModel |
| 跨多个实体的编排级操作（调度、分析等） | 编排入口 BizModel（仍需有 xmeta） |
| 某实体的标准 CRUD | 该实体的 `CrudBizModel` |

## 默认不要这样写

| 不推荐 | 原因 |
|--------|------|
| `dao().getEntityById(id)` 作为默认模板 | 绕过 `CrudBizModel` 的统一流程 |
| `dao().findAllByQuery(query)` 作为默认模板 | 绕过 query 预处理与权限过滤 |
| `dao().saveEntity(entity)` 作为默认模板 | 绕过上层封装与默认行为 |
| `@BizMutation @Transactional` | 重复事务包裹 |
| 直接注入其他 BizModel 实现类 | 降低跨模块可替换性 |
| 创建无 xmeta 的伪 BizModel | GraphQL 无法识别，浏览器页面报"未定义的对象" |
| BizModel 返回值无脑用 DTO 代替 Entity | 实体能表达的优先用实体，字段可见性由 xmeta 控制 |
| 创建 `*Service` / `*Controller` 类 | Nop 用 BizModel / `I*Biz` 承担这些职责，回避 Spring 命名 |
| 将局部 DTO 放入 `*-api/` 模块 | `*-api/` 只放外部 RPC 接口，局部 DTO 放 `*-dao/.../dto/` 或 `*-service/` |
| `I*Biz` 接口方法缺少 `@BizQuery`/`@BizMutation`/`@BizAction` 注解 | 动态代理无法识别该方法，运行时调用会抛异常 |

## 何时拆 Processor

当出现以下特征时，Processor 通常比继续堆在 BizModel 方法里更合适：

1. 方法已经明显是多步骤编排流程。
2. 同一逻辑要被多个 BizModel 复用。
3. 需要把外部系统交互和业务 orchestration 拆开。
4. 单个 BizModel 方法已经难以阅读和测试。

## 边界场景与默认模式的区别

仓库里确实存在直接 `dao()`、`updateEntityDirectly()`、`REQUIRES_NEW` 的实现，例如调度 store 层或个别历史 BizModel。它们可以作为“边界场景参考”，但不要当普通业务层模板复制。

## 相关文档

- `./architecture-principles.md` — 聚合根与数据库表的关系、DSL优先等跨切面原则
- `./domain-logic-and-ddd.md`
- `./dto-json-and-message-beans.md`
- `./testing.md`
- `./error-handling.md`
- `../03-runbooks/write-bizmodel-method.md`
- `../03-runbooks/choose-entity-bizmodel-processor.md`
- `../03-runbooks/implement-complex-business-flow.md`
- `../03-runbooks/create-request-response-dto.md`
- `../04-reference/safe-api-reference.md`
