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
| 读取已持有实体的关联实体 | 优先通过 ORM 关系 getter（如 `cart.getGoods()`）。`I*Biz.get()`/`requireEntity()` 会触发 `checkDataAuth` 数据权限检查和 `checkMetaFilter`，适用于需要校验当前用户对该实体访问权限的场景。内部业务逻辑仅需读取关联数据时，ORM 关系 getter 更直接，无需额外权限检查 |
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
- 最后一个参数为 `IServiceContext context`（见「参数与返回值」，跨服务调用需透传）
- 需要字段选择时带 `FieldSelectionBean`
- 查询能力优先走 `doFindList()`、`doFindPage()`

> **QueryBean 会被原地修改**
>
> `CrudBizModel` 的 `findPage`、`findList`、`findCount`、`findFirst` 等方法**不会**对传入的 `QueryBean` 做防御性拷贝，而是在原对象上直接修改。执行过程中会追加数据权限过滤、objMeta 过滤/排序、limit 上限裁剪、主键排序补全、filter 转换等操作，全部作用于同一个 query 实例。
>
> **如果需要在调用后复用原始查询条件，必须在调用前自行拷贝：**
>
> ```java
> QueryBean copy = query.cloneInstance();
> PageBean<T> page = bizObj.findPage(copy, selection, context);
> ```
>
> 这条规则同时适用于直接调用 `findPage`/`findList` 和通过 `I*Biz` 代理调用。

### 修改

- 使用 `@BizMutation`
- 最后一个参数为 `IServiceContext context`（见「参数与返回值」，跨服务调用需透传）
- 取实体优先走 `requireEntity()`
- 持久化优先走 `updateEntity()`、`save()`、`delete()`
- 提交后副作用优先走 `txn().afterCommit(...)`

如果修改入口最终走 `CrudBizModel.copyToEntity()` / `OrmEntityCopier`，则 xmeta 中的 `autoExpr` 会在实体拷贝阶段执行。
对 inline 主子表关系，子实体的 `autoExpr` 可以依赖已绑定的主实体反向 relation（例如 `refPropName` 对应的 owner 属性）；不要假设必须等到整棵对象图挂载完成后才可读取主表字段。

注意：`txn().afterCommit(...)` 只有在当前已经处于事务中时才能注册。普通 `@BizMutation` 场景满足这个前提（R1.10 起 mutation 默认事务装饰器对后台直调亦生效）；`@BizQuery` 和其他无事务场景下不要直接套用。

### 参数与返回值

- 1–5 个参数使用 `@Name`
- 超过 5 个参数使用 `@RequestBean` + `@DataBean` DTO（详见 `../03-runbooks/create-request-response-dto.md`）
- BizModel/`I*Biz` 自定义方法以 `IServiceContext context` 作为最后一个参数（对齐 `ICrudBiz` 契约）；调用其它 `I*Biz` 时透传 `context`，保证用户身份、数据权限与缓存跨服务一致
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
前端 ──/graphql · /r/ · /p/──> GraphQL Engine ──> BizModel 方法（@BizQuery/@BizMutation）
后端模块A ──I*Biz代理──> BizObject.invoke() ──> BizModel 方法（@BizQuery/@BizMutation/@BizAction）
```

> 各 HTTP 入口（`/graphql`、`/r/`、`/p/` 等）的详细对比和统一分发机制见 `./api-and-graphql.md` 的"统一请求分发模型"章节。

### BizModel 的 public 方法必须同步到 `I*Biz` 接口

**规则：BizModel 上新增的每一个 `public` 方法，都必须在对应的 `I*Biz` 接口上声明。** 这是强制的，原因：

1. **动态代理依赖接口**：`BizProxyFactoryBean` 生成的代理只识别接口上的方法。如果 BizModel 有 public 方法但接口上没声明，通过注入的 `I*Biz` 代理调用时会抛 `unsupported-method`。
2. **接口即契约**：`I*Biz` 是该 BizModel 对外暴露的全部能力清单。不在接口上的方法等于没有承诺，其他模块无法调用。
3. **`@BizModel` 实现类必须 `implements I*Biz`**：如果接口缺少方法，编译就不通过。

```java
// 1. 在 I*Biz 接口上声明方法
public interface IOrderBiz extends ICrudBiz<LitemallOrder> {
    @BizMutation
    LitemallOrder createOrder(@Name("addressId") String addressId,
                              @Name("message") String message,
                              IServiceContext context);

    @BizQuery
    List<LitemallOrder> myOrders(@Name("orderStatus") Short orderStatus,
                                 IServiceContext context);
}

// 2. 在 BizModel 实现类中实现方法
@BizModel("LitemallOrder")
public class LitemallOrderBizModel extends CrudBizModel<LitemallOrder> implements IOrderBiz {
    @Override
    @BizMutation
    public LitemallOrder createOrder(...) { ... }

    @Override
    @BizQuery
    public List<LitemallOrder> myOrders(...) { ... }
}
```

> **开发顺序**：先在 `I*Biz` 接口上加方法声明（含注解和 `@Name`），再在 BizModel 中实现。不要在 BizModel 中写了 public 方法却忘了同步接口。

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

## BizModel 自定义方法的 @Auth 权限命名约定

自定义 BizModel 方法（`@BizQuery`/`@BizMutation`）需要权限控制时，用 `@Auth(permissions = ...)` 注解。

**权限名 = `<BizObjName>:<action>`**（与平台 GraphQL 权限约定一致：`bizObjName + ':' + action`，见 `ReflectionBizModelBuilder`）。`BizObjName` 即 `@BizModel("...")` 声明的对象名，`action` 用动词/查询语义命名：

| BizObjName | action | 示例 permission 字符串 |
|-----------|--------|----------------------|
| `FileTool` | read / write / search | `FileTool:read`、`FileTool:write` |
| `SequentialThinking` | process / query / delete | `SequentialThinking:process` |
| `AiTool` | read / write | `AiTool:read` |
| `NopAiChatResponse` | query | `NopAiChatResponse:query` |

要点：

- 不要自创 `ai:` 之类的命名空间前缀（历史上 MR2 计划文本曾写 `ai:<entity>:<action>`，实际落地为 `<BizObjName>:<action>` 平台约定，见 nop-ai MR4 裁定记录）。
- `@Auth` 只放在自定义方法上；标准 CRUD 方法（`CrudBizModel` 继承的 `findPage`/`save` 等）由平台按 BizObjName 自动派生权限，不需要也不应该重复标注。
- 权限字符串应能对应到权限配置/数据字典中的条目，保持与 `I*Biz` 调用方期望一致；不要用空字符串或占位符绕过权限检查。

## xbiz 动作的 auth 声明（默认公开 + 显式收权模型）

Java BizModel 方法不标注 `@Auth` 时平台自动派生缺省权限（`obj:opType|obj:name`），但 **xbiz 文件里声明的动作不声明 `<auth>` 即公开**（`GraphQLActionAuthChecker.isAllowAccess` 对 `auth==null` 返回 true——这是平台的显式声明模型，`nop.auth.enable-action-auth` 默认关闭时整条链路不生效；即使开启，auth==null 的动作也直接放行）。纯 xbiz 自定义动作（如状态机触发的 publish/unpublish、绕过 findPage 权限的 active_findPage、审批类 mutation）**必须显式声明 `<auth>`**：

```xml
<mutation name="publish" bo:triggerStateChange="true">
    <auth roles="admin" permissions="NopWfDefinition:publish"/>
</mutation>

<query name="active_findPage" x:prototype="findPage">
    <!-- 单动作内 | 分组 = OR 组，镜像 Java 缺省串 obj:query|obj:findPage 的语义 -->
    <auth permissions="NopAuthUser:query|NopAuthUser:findPage"/>
</mutation>
```

要点（P1-2.6-005/006 修复后确立，plan 2378）：

- **每动作单独声明自身权限，禁止跨动作权限串连写**。`permissions` 中 `|` 是 OR 组分隔符（组内逗号 AND、任一组全满足即放行），`publish|unpublish` 连写会互相提权。
- **`<auth>` 必须是 action 的第一个子元素**（`xbiz.xdef` BizActionModel 的元素顺序：auth → txn → arg → return → source）。
- **x:extends 合并终值决定生效权限**：本地覆盖文件（自带 `<source>` 的动作）必须在本地文件补 `<auth>`，只改基库不生效；继承文件（如 `NopAuthUser_tenant.xbiz`）继承基库动作的 auth。改动后应经 `IBizObjectManager` 取 merged definition 断言终值（参考 `TestUseApprovalE2E` 的合并终值断言写法）。
- **审批类公共基座 `approval-support.xbiz`**（`/nop/wf/base/approval-support.xbiz`）的 5 个 mutation 缺省 `roles="admin"`。可用性裁定：opt-in 部署（enable-action-auth=true）默认收紧到 admin；非 admin 提交人场景由应用按实体经 x:extends 覆盖点差异化声明（如 submitForApproval 放开到业务角色）。

## @BizLoader 的 auth 声明（两条路径）

loader（GraphQL 字段装载器）的权限声明有两条等价路径（P1-2.6-007 修复前 `@Auth` 是死注解、xbiz loader 无 auth 元素，均已收敛）：

| 路径 | 写法 | 装配点 |
|------|------|--------|
| Java 注解 | `@BizLoader` 方法上标注 `@Auth(roles/permissions=...)` | `ReflectionBizModelBuilder.buildFetcherField` 消费注解 → `field.setAuth` |
| xbiz 声明 | `<loaders><loader name="x"><auth roles="admin"/></loader></loaders>` | `BizModelToGraphQLDefinition.toBuilder` 装配 `loaderModel.getAuth()` |

与 action 路径的关键差异：**loader 不补缺省权限**——无 `@Auth`/无 `<auth>` 的 loader 字段 `auth==null`（公开）。loader 是字段装载器，按 action 缺省串回填过严，因此采用「显式声明才收权」。这意味着开启 `nop.auth.strict-action-auth` 前必须盘点全部无声明 loader（当前已知无 @Auth 生产 loader：`NopAuthRoleBizModel.roleUsers/roleResourceIds`、`NopDynEntityMetaBizModel.allProps`、`NopRuleNodeBizModel.predicateLabel`，见 `./auth-and-permissions.md` 严格模式章节）。

## 普通 BizModel 默认优先的 API

| 场景 | 默认方法 |
|------|---------|
| 获取实体 | `requireEntity(id, actionName, context)` |
| 列表查询 | `doFindList(query, this::invokeDefaultPrepareQuery, selection, context)` |
| 分页查询 | `doFindPage(query, this::invokeDefaultPrepareQuery, selection, context)` |
| 保存前端数据 | `save(data, context)` |
| 更新实体 | `updateEntity(entity, actionName, context)` |
| 删除实体 | `deleteEntity(entity, actionName, context)` / `delete(id, context)` |

> 以上方法中 `actionName` 是 `String` 类型（业务动作标识，用于权限检查 / afterEntityChange）。传 `null` 使用默认值即可，**不要传 `boolean`**。

### `afterEntityChange` 重载陷阱：必须覆写 2-arg 版本

`CrudBizModel` 提供两个 `afterEntityChange` 重载：3-arg（entity, actionName, context）与 2-arg（entity, context）——**save/update 路径经由 3-arg → 委托 2-arg；delete 路径直接调用 2-arg**。所以：

- 只覆写 3-arg → **delete 路径不会触发你的钩子**（依赖 2-arg 派生汇总（如父表 Σ 重算）停止在删除场景工作，且无警告）。
- 需要覆盖全部增/改/删的钩子逻辑时，**覆写 2-arg 版本**（它同时覆盖 save/update + delete 三条路径）。
- 在 2-arg 钩子中重查父表派生字段前先 `orm().flushSession()`，保证刚 save/delete 的行对查询可见（否则读到旧值，派生汇总 stale）。

## 批量与 by-query 变更的入参上限

`CrudBizModel` 前端可达的批量/按条件变更入口有显式资源上限，超限抛专用 ErrorCode 且**零副作用**（检查在任何实体加载或写入之前完成）：

| 入口 | 上限 | 超限错误码 |
|------|------|-----------|
| `batchGet` / `batchUpdate` / `batchDelete`（ids）、`batchModify`（data 与 delIds 各自检查）、`add/remove/updateManyToManyRelations`（relValues） | `nop.biz.max-batch-size`（缺省 500），xmeta `ext:maxBatchSize` 可按对象抬高（仅允许抬高，与 `ext:maxPageSize` 语义一致） | `nop.err.biz.batch-size-exceeds-limit` |
| `deleteByQuery` / `updateByQuery` | 命中数超过有效 limit（显式 limit 与 maxPageSize 归一化后的较小者；limit<=0 按 maxPageSize）时抛错 | `nop.err.biz.by-query-exceeds-limit` |
| `asDict` | 字典表行数超过 maxPageSize 时抛错（字典必须是完整值集，截断会导致表单选不到合法选项） | `nop.err.biz.dict-options-exceeds-limit` |

要点：

- **前端语义 = 有界操作**：`deleteByQuery`/`updateByQuery` 不再静默截断（旧行为是命中 maxPageSize 后仅记 warn、部分执行）。调用方应缩小过滤条件，或提示用户分批操作。
- **历史遗留模式失效**：靠显式传小 limit 循环调用 `deleteByQuery` 分批清空大表的用法已不可用——超限按**命中总数**判定，与传入的 limit 无关。分批请按业务键分段过滤，或由服务端走 `do*` 内部通道。
- **每次调用多一次 COUNT**：`deleteByQuery`/`updateByQuery` 在执行前先执行计数查询（变异操作低频，成本可接受）；`asDict` 的计数经字典缓存，仅冷缓存时有额外查询。`updateByQuery` 传空 `data` 仍是 no-op（返回 0，不计数）。
- **count==limit 的已知日志噪音**：命中数恰好等于 limit 时完整执行，但 `do*` 内部仍会记一条 `result-truncated` warn（实际未截断），运维看到该日志无需处理。
- **后台逃生通道**：`doDeleteByQuery`/`doUpdateByQuery`/`doBatchGet`（`@BizAction`，不暴露为 GraphQL operation）不做上述检查，服务端内部需要大批量操作时直接调用 `do*` 方法或组合 DAO；树查询内部路径（`findListForTree`/`findPageForTree`）即走 `doBatchGet`。真正的大规模数据变更使用 nop-batch。
- **三个上限互不联动，各管一段**：`maxBatchSize`（500）管**单次提交的集合元素数**（batch\* 系列入参）；`maxPageSize`（1000）管**单页查询返回行数**（findPage/findList 的 limit 归一化）；`in-op-max-allow-value-size`（100）管 **filter 中单个 IN 谓词的候选值数**（仅 QueryBean filter 校验路径）。ids 直传不经过 filter 校验，其上限是 maxBatchSize 而非 100——两条通道独立，调一个不影响另一个（唯一交叉：`asDict` 复用 maxPageSize 作为字典规模上限）。

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
| `@BizMutation @Transactional`，或在 BizModel 方法上叠 `@SingleSession` | BizModel 管道已自动包事务并提供 ORM Session（R1.10 起 mutation **默认**包事务——含无 `<txn>` 声明与后台直调路径）：`@Transactional` 是重复事务包裹（冗余非禁止，由 dao 层 AOP 切点消费），`@SingleSession` 是 **non-BizModel** 场景注解（定时任务、独立 service bean，见 `../03-runbooks/non-bizmodel-orm-access.md`），BizModel 上不需要 |
| BizModel/`I*Biz` 自定义方法签名缺 `IServiceContext context` 末参 | 对齐基类 `ICrudBiz` 契约（其所有方法末参均为 `IServiceContext context`）。`context` 承载 `IUserContext`（用户身份/数据权限）、缓存与事务上下文；跨服务调用（A 域注入并调用 B 域 `I*Biz`）必须把 `context` 透传给下游，否则下游拿不到身份、静默跳过数据权限、丢失缓存。查询带 `FieldSelectionBean` 时，`context` 仍是最后参数。在 xbiz `<source>` 脚本中，该上下文以内置变量 `svcCtx` 提供（详见 `./xlang-and-xpl-basics.md` 的"xbiz action source 内置变量"章节） |
| 直接注入其他 BizModel 实现类 | 降低跨模块可替换性 |
| 创建无 xmeta 的伪 BizModel | GraphQL 无法识别，浏览器页面报"未定义的对象" |
| BizModel 返回值无脑用 DTO 代替 Entity | 实体能表达的优先用实体，字段可见性由 xmeta 控制 |
| 创建 `*Service` / `*Controller` 类 | Nop 用 BizModel / `I*Biz` 承担这些职责，回避 Spring 命名 |
| 将局部 DTO 放入 `*-api/` 模块 | `*-api/` 只放外部 RPC 接口，局部 DTO 放 `*-dao/.../dto/` 或 `*-service/` |
| `I*Biz` 接口方法缺少 `@BizQuery`/`@BizMutation`/`@BizAction` 注解 | 动态代理无法识别该方法，运行时调用会抛异常 |
| BizModel 新增 public 方法但未同步到 `I*Biz` 接口 | 代理无法路由该方法，跨模块调用失败；接口是能力清单，必须与实现同步 |
| 自定义方法与 `ICrudBiz` 标准方法重名（如 `get`、`save`、`update`、`delete`、`findPage` 等） | BizModel 按方法名路由，不支持重载。重名会抛 `ERR_GRAPHQL_DUPLICATE_ACTION`。自定义方法用不同的名字，如 `getById`、`saveOrder` |
| 已有 ORM 关系时用 `I*Biz.get()` 获取关联实体 | 内部业务逻辑只需读取关联数据时，关系 getter（如 `cart.getGoods()`）更直接。`I*Biz.get()`/`requireEntity()` 会额外触发 `checkDataAuth` + `checkMetaFilter`，适用于需要权限校验的场景 |

## 何时拆 Processor

当出现以下特征时，Processor 通常比继续堆在 BizModel 方法里更合适：

1. 方法已经明显是多步骤编排流程。
2. 同一逻辑要被多个 BizModel 复用。
3. 需要把外部系统交互和业务 orchestration 拆开。
4. 单个 BizModel 方法已经难以阅读和测试。

## 边界场景与默认模式的区别

仓库里确实存在直接 `dao()`、`updateEntityDirectly()`、`REQUIRES_NEW` 的实现，例如调度 store 层或个别历史 BizModel。它们可以作为“边界场景参考”，但不要当普通业务层模板复制。

## GraphQL WS 订阅推送的执行载体（nop-service-framework，2.3-C8-001，plan 2498）

服务层方法（BizModel action / subscription publisher）向 WS 会话推送时，**阻塞 socket send 不在调用线程或共享定时器线程执行**（单慢客户端 TCP 零窗口曾可钉死全平台 globalTimer，ρ=N·L/40 钉死面已消除）：

- **会话级有界发送泵**：`JsonRpcWebSocketHandler`/`GraphQLSubscriptionManager` 的推送经 `WebSocketSendPump`（容量 128 有界队列、满则 DROP_NEWEST 并断开该会话 `close(1011)`）卸载至平台 `globalWorker` 池——序列化留在调用线程，仅阻塞写卸载；队列满 = 慢消费者会话被断开，不反压全局。
- **keepalive 扇出 O(1)**：JVM 单条共享周期扫描链（`globalTimer().executeOn(globalWorker())` 遍历连接注册表），非每连接一条 timer 周期任务。
- **边界**：WS 适配器层写超时（`getBasicRemote`→`getAsyncRemote` 族）为 Deferred，successor=R4-16/R4-17。测试锚点：`nop-graphql-core` `TestJsonRpcWsSendOffload_C8_001`/`TestSubscriptionPushOffload_C8_001`。

## 异步 biz action 的上下文传播契约（nop-service-framework，C3-002/C3-007 同族，plan 2499）

BizModel 异步 action（`callActionAsync` 及其执行链）的线程上下文契约——**异步续段不允许脱离请求 `IContext` 执行**（tenant/locale/trace/ORM 会话均按线程上下文解析，脱窗即读空或读残留）：

- **受管提交**：异步 action 的任务提交必须经受管入口（携带调用方 context 快照传播 + 任务 finally detachContext），禁止裸 `executor.execute(...)` 绕过平台约定；`BizActionInvocation.proceedAsync` 在无线附着线程发起时 fail-fast（类型化错误，不静默新建附着）。
- **异步出口挂接**：action 内 `thenCompose`/`thenAccept` 续段（出口泵、批量 loader、fetcher 变换）统一经 `ContextProvider.thenOnContext` 或 `IContext.execute` 执行窗口承载（见 `../04-reference/source-anchors.md` `CTX-001`）；TCC invoker 同口径——无线附着 fail-fast，恢复写回捕获的 context 对象而非完成线程的 `currentContext()`。
- **入口统一**：非 HTTP 入口（gRPC/WS/消息/文件）经 `IGraphQLEngine.newRpcContext`（内置 ContextBinder 装配）从消息头构造 context，不自行拼装线程附着。

测试锚点：`nop-graphql-core` `TestGraphQLExecutorContextPump_C3_002`/`TestTccContextInvokerFailFast_C3_008`、`nop-biz` `TestBizActionServiceManagedSubmit_C3_007`、`nop-graphql-orm` `TestAsyncBizActionOrmChain_P3_E2E`。

## SSO token 刷新的异步化契约（nop-auth-sso，2.3-C4-007，plan 2508）

认证过滤器对 SSO token 的半衰期刷新**不得在载体线程同步执行**（brownout 下 IdP HTTP 挂起按 RTT 钉满全局 worker 载体、全站确定性吞吐停摆面已消除；实现锚点见 `../04-reference/source-anchors.md` `AUTH-005`）：

- **契约 API**：`ILoginService.refreshTokenAsync`（default 方法，默认实现回退同步 `refreshToken`——未升级的实现保持现行行为；SSO 实现经纯异步 HTTP 链 override）。
- **请求超时**：刷新 HTTP 请求显式超时（键 `nop.auth.sso.refresh-token-timeout`，默认 10s；`≤0` = 回落客户端默认 30s）——无超时的 IdP 挂起不再钉死过滤器。
- **链序与执行介质**：过滤器刷新终态（成功/error→null 降级/异常三路一致）后，设头/cookie 与下游 `next.get()` 续段**经 `runOnContext` 再入上下文任务队列执行**——不在 HTTP 客户端回调线程直执（下游 `currentContext()` ThreadLocal 依赖任务队列绑定）。
- **降级语义**：刷新失败沿用既有 stale-serve（error→null）——fail-closed 不在本契约（如需按 ask-first 另行走查）。

## 网关上游调用的准入上界契约（nop-gateway，2.3-C8-002，plan 2508）

网关对上游 HTTP 的在途调用数**有并发上界**（429 重试 ×7 加法放大 + 90s 挂起窗的 OOM 轨迹已消除；实现锚点见 `../04-reference/source-anchors.md` `GW-001`）：

- **准入**：主腿（含 429/5xx 重试挂起窗全程）与 fallback 腿共享并发许可池（键 `nop.gateway.upstream-max-inflight`，默认 1000；`≤0` = 不设上界档）；超限**类型化快速拒绝**（不排队不静默），且该拒绝为不可重试错误——不触发 fallback 级联。主腿重试次数受 `nop.gateway.upstream-max-retries`（默认 3；`≤0` = 零重试）。
- **许可纪律**：获取在同步抛出点之后；全出口 finally/whenComplete 释放；重试挂起窗（内存持有窗）**不释放**许可。
- **时间整形**：429 Retry-After 钳制后施加 equal jitter（delay/2 + U(0, delay/2)）——保留 ≥delay/2 上游恢复窗并打破同值 Retry-After 的同步重试波；`parseRetryAfter` 与 30s 钳制语义零变化。XPL/RPC/流式路由不入池。

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
