# Relation Prop WriteMode 设计

> Status: draft
> Date: 2026-04-19
> Scope: `nop-biz` CrudBizModel / OrmEntityCopier / ObjMetaBasedValidator
> Affects: `IObjPropMeta`, `OrmEntityCopier`, `ObjMetaBasedValidator`, `CrudToolProvider`

---

## 一、问题背景

在 low-code relation write 场景中，前端提交的 relation payload 在业务语义上可能对应 3 类写入路径：

| Mode | 语义 | target 自身业务字段是否更新 |
|------|------|------------------------------|
| `inline` | relation payload 作为 aggregate graph 的一部分内联保存 | 是 |
| `link` | relation payload 只表达关系结构，只同步 source 和 target 的关联 | 否 |
| `biz` | relation payload 仍然是同构对象图，但应由 target 自己的标准 BizModel 入口解释 | 是 |

当前 `OrmEntityCopier` 对 relation prop 的默认处理等价于 `inline`。它已经支持通过 `_chgType` 控制集合项的增删改，但还没有 prop-level 的 write semantic routing。

本文要解决的问题不是设计一套新的 relation command 协议，而是在保持 `CrudBizModel` 现有同构风格的前提下，为 relation prop 增加可声明的写入语义。

## 二、核心理念

### 2.1 ObjMeta 的读写对称性

类型层次：

- `IObjSchema` 是结构接口，定义 props、className、type 等基础结构
- `IObjMeta extends IObjSchema`，在结构定义之上增加 CRUD 相关元数据，例如 `primaryKey`、`selection`、`filter`、`keys`、`tree`
- `ObjMetaImpl.getRootSchema()` 返回 `this`，说明 ObjMeta 本身就是 root schema
- `CrudBizModel` 在运行时实际使用的是 `IObjMeta`

这里的“对偶性”不是指 `ObjSchema` 与 `ObjMeta` 分别对应输入和输出，而是指：

- 同一个 `ObjMeta` 定义领域对象的完整形状
- **读侧**通过 GraphQL selection 从这个完整形状中取切片
- **写侧**通过 fieldSelection 向这个完整形状写入切片

因此，在 CRUD 语义下，可读取的业务对象形状与可提交的业务对象形状是对称的。prop 的元数据也是共享的，包括：

- 类型
- mandatory
- transformIn / transformOut
- insertable / updatable
- auth
- relation writeMode

如果某些场景的输入语义不再属于这种可再次读取回来的业务数据，则它不属于这里讨论的 CRUD 对偶输入，应由专门的 action request 模型承接。

### 2.2 同构性

这里的 relation write 必须保持同构设计：

- 前端提交的仍然是对象图的一个同构片段
- 后端根据 prop 的 write 语义，把这份同构数据投影到实体对象图上
- lane 改变的是 **解释语义**，不是 **payload 形状**

因此：

- 不引入 `add/remove/replace/clear` 之类的 action-style relation 协议
- 不把 relation write 变成一组命令式 operation list
- `inline` / `link` / `biz` 共享同样的 relation-shaped JSON，只是解释方式不同

这是本文设计与常见 nested mutation / command DTO 方案的根本区别。

### 2.3 Validator 与 Copier 的职责边界

```text
ObjMetaBasedValidator: JSON -> JSON
    负责结构校验、类型转换、auth 检查，主体仍然是 JSON 数据

OrmEntityCopier: JSON -> Entity
    负责把同构 JSON 投影到 ORM 实体图，并在 relation prop 上做 writeMode routing

CrudBizModel: Entity -> Entity
    负责驱动主流程 phase，并在合适时机执行 delayed relation biz actions
```

`writeMode` routing 的本质是“某个 relation prop 的 JSON 该如何映射到实体图”，因此路由决策仍然属于 `OrmEntityCopier`，而不是 `ObjMetaBasedValidator`。

但 `biz` mode 与 `inline/link` 有一个关键差异：

- `inline/link` 可以在 copier 遍历 relation prop 时立即投影到实体图
- `biz` 需要走 target 自己的标准 BizModel 入口
- 如果在 parent 尚未完成自己的 `prepareSave/prepareUpdate`、`dataAuth`、`unique` 等主线检查前就直接调用 child BizModel，会产生语义倒置

因此本文采用：

- `OrmEntityCopier` 仍负责识别 `biz` mode
- 但不在 relation 分支里立即执行 target BizModel
- 而是记录为显式的 `DelayedRelationAction`
- 由 `CrudBizModel` 在 parent 主线校验完成后、同一事务内统一执行

这里特意不采用“在 `serviceContext` 上注册通用监听器”的方式，因为 relation write 不是横切事件，而是 CRUD 主流程中的一个明确 phase。显式的 delayed action 队列更容易保证顺序、调试和回挂语义。

## 三、WriteMode 设计

### 3.1 枚举值

| 枚举值 | 含义 |
|--------|------|
| `inline` | 按 aggregate graph 递归 copy 的现有逻辑处理 relation payload |
| `link` | 只把 relation payload 解释为关系结构，不更新 target 的业务字段 |
| `biz` | 不做 inline copy，而是把 nested payload 转发给 target BizModel 的标准入口解释 |

这里 `inline` / `link` / `biz` 不是三套请求协议，而是三种 prop-level write semantic。

### 3.2 配置来源

延续现有 `_chgType` 的风格，writeMode 的来源分为两层：

| 层面 | 角色 |
|------|------|
| prop meta 的 `writeMode` | prop 的默认或约束性写入语义 |
| 前端 payload `_writeMode_<propName>` | 本次提交在允许范围内选择的运行时语义 |

推荐语义：

- `writeMode` 是 prop 自身的声明
- 前端可以在允许范围内选择更具体的解释方式
- 如果 prop 明确要求更严格的语义，则以 prop 声明为准

Merge 规则：

| prop 声明 | 前端传值 | 最终结果 | 说明 |
|-----------|----------|----------|------|
| 未声明 | 任意 `inline/link/biz` | 使用前端传值 | prop 无约束，前端自由选择 |
| `inline` | `link` 或 `biz` | 使用前端传值 | `inline` 是最宽松模式，允许升级 |
| `link` | `inline` 或 `biz` | 使用 `link` | `link` 是约束性声明，锁定为只改关系 |
| `biz` | `inline` 或 `link` | 使用 `biz` | `biz` 是约束性声明，锁定为走 target BizModel |
| `link` | `link` | `link` | 一致 |
| `biz` | `biz` | `biz` | 一致 |

核心原则：

- **`inline` 是宽松模式**，允许前端在运行时升级为 `link` 或 `biz`
- **`link` 和 `biz` 是约束性声明**，前端不可降级为其他模式
- 如果前端传值与 prop 约束冲突，**以 prop 声明为准，不报错**（静默收窄）
- 这保证了 prop 声明的业务语义不会被前端绕过，同时不增加错误处理的复杂度

## 四、为什么落在 OrmEntityCopier

### 4.1 与 CrudBizModel 主流程的关系

`CrudBizModel` 的主流程是：

```text
save/update
  -> buildEntityDataForSave/Update
      -> ObjMetaBasedValidator.validate...
  -> OrmEntityCopier.copyToEntity(...)
  -> prepareSave/prepareUpdate
  -> dataAuth / unique
  -> doSaveEntity/doUpdateEntity
```

relation write 的 lane selection 发生在“递归处理某个 relation prop”的时刻。这个断点天然就在 `OrmEntityCopier.copyField()` 的 relation 分支里，而不是 `CrudToolProvider` 这种工厂对象里。

### 4.2 为什么不是应用层 router

- `CrudBizModel` 当前没有单独的 relation routing hook
- 如果在应用层再包一层 router，会把“同构 JSON -> 实体图”的映射拆成两段，打破现有简单链路
- relation routing 和 relation copy 使用的是同一组输入（`propMeta`、relation model、validated data、selection、scope/context），强耦合，不值得人为拆开

因此，最自然的演进方式是：

- `CrudToolProvider` 继续负责创建 copier
- `OrmEntityCopier` 负责 prop-level semantic routing

### 4.3 上下文不是障碍

`biz` mode 需要触达 target 的标准 BizModel 入口，这要求 copier 能获得完整上下文。

这里不是架构问题：

- `OrmEntityCopier` 现在已经接收 `IEvalScope`
- `IEvalScope` 实际上可以关联到上下文
- 更直接的实现方式是把 `IServiceContext` 显式继续传入 `OrmEntityCopier`

因此 `biz` mode 的路由仍然可以落在 copier 内部，不需要把整个 routing 上提到应用层 pipeline。

### 4.4 为什么 `biz` 需要 delayed action

如果 `biz` mode 在 `OrmEntityCopier.copyField()` 的 relation 分支中被立即执行，会出现下面的顺序问题：

- parent 只完成了 JSON validate 和部分 copy
- parent 还没有完成 `prepareSave/prepareUpdate`
- parent 还没有完成 `dataAuth / unique / 其他业务前置检查`
- child 却已经通过自己的标准 BizModel 入口开始执行业务语义

这在事务上虽然仍可回滚，但在业务语义上是不自然的，因为它允许“尚未通过主流程前置校验的父对象”驱动子对象的标准业务操作。

因此，`biz` mode 的合理执行模型应当是：

- copier 阶段只完成 parent 实体图初始化，以及对 relation prop 的 mode 识别
- 对于 `biz` relation，不立即执行业务写入，而是生成 `DelayedRelationAction`
- `CrudBizModel` 在 parent 主线通过前置 phase 后，统一执行这些 delayed actions
- 整个过程仍处于同一事务中，任何一步失败都整体回滚

这意味着：

- `inline/link` 是 immediate projection
- `biz` 是 delayed business dispatch

二者共享同构输入，但执行 phase 不同。

## 五、三种模式的解释语义

### 5.1 `inline`

`inline` 保持当前语义不变：

- toOne 走 `copyRefEntity`
- toMany 走 `copyRefEntitySet` / `syncEntitySet`
- nested payload 被视为 aggregate graph 的一部分递归 copy

这是当前默认行为。

### 5.2 `link`

`link` 不改变输入形状，仍然接受 relation-shaped JSON，但其解释方式发生变化：

- toOne：payload 只用于确定绑定到哪个 target，或解绑，不递归更新 target 自身业务字段
- toMany：payload 表示 relation 的目标集合快照；同步时只关注 relation membership，不递归更新集合项自身业务字段
- nested payload 中除 identity / relation binding 所需信息外，其余 target 字段忽略

也就是说，`link` 的本质不是 action-op，而是：

**同样的 relation JSON，只做 relation structure 的投影，不做 target business update。**

### 5.3 `biz`

`biz` 也不改变输入形状。它的含义是：

- 当前 relation prop 收到的 nested payload 不再按 inline 方式直接 copy 到 target entity
- 而是把这份同构 JSON 注册为 delayed action，随后交给 target 自己的标准 BizModel 入口解释
- 从而让 target 自己的 validator / auth / hook / state machine 生效

因此，`biz` 的本质是：

**relation prop 的 target-side write semantic 切换为“通过 target BizModel 解释这份同构 JSON”，但解释时机不是 copier 递归当下，而是 parent 主线前置 phase 完成后的 delayed dispatch。**

换句话说，`biz` mode 不是“立即递归改为调用 BizModel”，而是：

- copier 负责识别 relation payload 的 `biz` 语义
- copier 负责记录需要执行的 target-side business action
- `CrudBizModel` 负责在后续 phase 中统一执行它们
- action 执行结果再回挂到当前 parent 的 relation 上

### 5.4 统一语义表

| mode | 输入形状 | 解释方式 | 是否更新 target 业务字段 |
|------|----------|----------|--------------------------|
| `inline` | 同构 relation JSON | aggregate recursive copy | 是 |
| `link` | 同构 relation JSON | 只投影 relation structure | 否 |
| `biz` | 同构 relation JSON | 记录 delayed action，后续交给 target BizModel 解释 | 是 |

## 六、与现有 `_chgType` 的关系

`writeMode` 和 `_chgType` 是两个正交维度：

- `writeMode` 决定 relation prop 的整体解释语义
- `_chgType` 继续决定某个集合项在同构同步过程中的增删改意图

因此：

- 不需要替换现有 `_chgType`
- `writeMode` 是 prop-level semantic
- `_chgType` 是 item-level delta hint

对于 `biz` mode，还需要把 `_chgType` 映射为 target-side 标准入口：

| `_chgType` | `biz` mode 下的推荐解释 |
|------------|----------------------------|
| `A` | 调用 target `save` |
| `U` | 调用 target `update` |
| `D` | 调用 target `delete` 或在不允许物理/逻辑删除的 relation 上退化为 unlink，具体规则另行约束 |
| 缺省 | 按是否带 identity 判定为 `save` / `update`，延续当前同构风格 |

这里需要注意：

- `writeMode` 决定“是否走 target BizModel”
- `_chgType` 决定“走 target BizModel 的哪个标准 action”
- toMany 集合项缺席是否视为删除/解绑，不应在本文先拍死，建议在实现阶段结合当前 `syncEntitySet` 语义单独锁定

因此两者不是互相替代，而是在 `biz` lane 上形成“路由 + 动作”组合。

## 七、Delayed Action 设计

### 7.1 为什么要显式对象，而不是隐式监听器

本文推荐显式的 `DelayedRelationAction` 队列，而不是把执行逻辑隐藏到 `serviceContext` 监听器中。

原因：

- relation write 是 `CrudBizModel` 主流程的一部分，不是泛化事件机制
- delayed action 需要稳定顺序、稳定 phase、稳定回挂语义
- 显式对象更便于调试、测试和错误定位

### 7.2 最小数据结构

`DelayedRelationAction` 不要求一开始就做成复杂框架，但至少应包含：

- `propName`
- `writeMode`
- `targetBizObjName`
- `action`：`save` / `update` / `delete` / `unlink` 等
- `payload`
- `parentEntity`
- `relationModel` 或等价的 relation 元信息
- `selection`
- `scope/context`
- `applyResult`：执行后如何把结果回挂到 parent relation
- `orderKey`：同一 parent 内的稳定执行顺序，默认按 propMeta 的声明顺序（即 ORM 模型中 relation prop 的定义序号）填充

### 7.4 执行顺序

`DelayedRelationAction` 的默认执行顺序按 propMeta 在 ORM 模型中的声明顺序确定。

- 同一 parent 的 relation prop 按 meta 定义序号依次执行
- 不依赖 JSON payload 中的 key 顺序（JSON object key 顺序在规范上不可靠）
- 当前不保证跨 relation 的数据传递（即 A relation 的结果不会作为 B relation 的输入）
- 如需显式依赖声明，后续可扩展 `dependsOn` 机制，但第一版不引入

### 7.5 执行 phase

当前推荐只定义一个主 phase：

- `AFTER_PARENT_PREPARE`

它的含义是：

- parent 已完成 validate
- parent 已完成 copier 第一阶段和 immediate relation projection
- parent 已完成 `prepareSave/prepareUpdate`
- parent 已完成 `dataAuth / unique / 其他前置检查`
- 此时开始执行 delayed relation biz actions

后续如果出现必须在 parent 持久化后再执行的 relation 语义，可以再扩展更多 phase；但当前设计不必提前复杂化。

## 八、执行算法

### 8.1 总体流程

推荐把 relation write 的执行顺序明确为：

```text
save/update
  -> ObjMetaBasedValidator.validate...
  -> OrmEntityCopier.copyToEntity(...)
       Phase-1: copy parent scalar/basic props, initialize parent id if needed
       Phase-2: process relation props
           inline -> immediate recursive copy
           link   -> immediate relation projection only
           biz    -> collect DelayedRelationAction
  -> prepareSave/prepareUpdate
  -> dataAuth / unique / other parent pre-checks
  -> executeDelayedRelationActions(...)
       -> invoke target BizModel standard actions in same transaction
       -> applyResult back to parent relation
  -> doSaveEntity/doUpdateEntity
  -> flush/commit
```

这个顺序表达了两个原则：

- parent 先通过自己的主线前置约束，再驱动 child biz actions
- child biz actions 和 parent persistence 仍处于同一事务中

### 8.2 Copier 的两阶段职责

`OrmEntityCopier` 在引入 `biz` mode 后，不应继续保持“遍历到哪个字段就立即完全处理哪个字段”的单阶段心智，而应转为更明确的两阶段模型：

- Phase-1：处理 parent 自身字段、join 所需字段、主键初始化
- Phase-2：处理 relation props，并按 `writeMode` 分流

之所以需要这样强调，是因为 `biz` mode 需要 parent 的基础状态先稳定下来，才能安全地构造 delayed action。

### 8.3 Delayed action 的回挂规则

执行 target BizModel action 后，结果不能只停留在返回值层面，而必须重新回挂到 parent 的 relation 上，否则后续实体图与业务语义会脱节。

推荐规则：

- toOne：以 target action 返回的 entity 为准回挂；如果返回值不是实体，则按 identity reload 后回挂
- toMany：把每个 delayed item 的结果聚合成目标集合，再与 parent relation 同步
- delete/unlink：需要显式体现在 parent relation membership 上，避免“target 已处理，但 parent 图未更新”

### 8.4 失败与回滚

本文假设 parent 与 delayed relation biz actions 处于同一事务中。

因此：

- parent 前置检查失败，则 delayed actions 不执行
- delayed action 任一失败，则整体事务回滚
- parent 持久化失败，则此前已执行的 child biz actions 一并回滚

## 九、Validator 层需要补的最小支持

`ObjMetaBasedValidator` 不负责 routing，但需要允许 `_writeMode_*` 这类字段透传，方式与 `_chgType_*` 一致。

也就是说，Validator 侧只需要：

- 识别 `_writeMode_<propName>` 是合法控制字段
- 在 validate/convert 后保留该字段到 validated map 中

除此之外，不应把 `writeMode` 的业务语义放到 Validator 层解释。

需要补充的是：

- 对 `inline/biz`，validator 仍按当前 nested object 语义工作
- 对 `link`，后续可能需要收窄为“只校验 identity / relation binding 所需字段”

但这属于 validator 细化问题，不影响本文先确立 `biz` delayed action 的主算法。

## 十、fieldSelection 与 mandatory 的关联问题

当前 `ObjMetaBasedValidator._validate()` 遍历 `data.entrySet()`，导致一个已知 gap：

- 如果 selection 声明了某 prop
- 但数据中缺失该字段
- 当前可能不会触发 selection 范围内的 mandatory 校验

这是独立于 relation writeMode 的问题，但与“ObjMeta 作为写侧对偶模型”的理念有关。

建议后续单独处理：

- `save`：按创建语义检查 mandatory
- `update`：按部分更新语义检查 mandatory
- 自定义 selection：selection 范围内缺失 mandatory 字段应报错

## 十一、与其他常见设计的比较

### 11.1 对比纯 aggregate nested save

纯 aggregate nested save 只支持一种解释语义：所有 nested payload 都 inline 保存。

本文设计比它强在：

- 仍保持同构对象图输入
- 但允许 relation prop 声明不同写入语义
- 可以表达“只改关系，不改 target”以及“target 必须走自己的业务入口”

### 11.2 对比 op-style nested mutation

很多 GraphQL / ORM 方案会使用：

- `connect`
- `disconnect`
- `set`
- `upsert`

这类方案的优点是操作性强，但代价是：

- 输入协议变成命令语言
- 不再保持 CRUD JSON 的同构性
- 元数据驱动更复杂，前端心智负担更重

本文设计选择另一条路：保持同构 payload，mode 只改变解释语义。

### 11.3 对比 action DTO 分裂式设计

另一种常见方案是为不同 relation 行为拆很多专用 DTO / action：

- 一个 save DTO
- 一个 link DTO
- 一个 child update DTO

短期清楚，但长期会导致：

- 元数据复用下降
- 前端模型分裂
- auth / transform / validation 规则散落

本文设计把核心语义收敛到 prop-level metadata，更符合 Nop 的 model-first / metadata-first 平台方向。

## 十二、当前结论

当前阶段推荐的主线是：

1. 继续围绕 `OrmEntityCopier` 做 relation prop 的 write semantic routing
2. `biz` mode 不在 copier relation 分支中立即执行，而是生成显式 `DelayedRelationAction`
3. `CrudBizModel` 在 parent 主线前置 phase 完成后、同一事务内统一执行 delayed actions
4. `writeMode` 保持为 prop-level metadata，前端只是在允许范围内参与选择
5. 整体设计坚持两个原则：
   - **对偶性**：同一个 ObjMeta 同时支撑 CRUD 的读写切片
   - **同构性**：relation payload 始终是对象图的同构片段，lane 只改变解释语义
   - **时序性**：child biz action 不应早于 parent 的主线前置校验

这比纯 aggregate save 更完整，比 op-style nested mutation 更统一，比 action DTO 分裂更平台化，同时避免了“未完成 parent 主线校验就先驱动 child BizModel”的语义倒置。

## 十三、实现草案

### 13.1 `DelayedRelationAction` 建议形态

如果不想一开始引入太重的抽象，可以先定义一个偏数据对象的最小结构，例如：

```java
class DelayedRelationAction {
    String propName;
    String writeMode;
    String bizAction;
    String targetBizObjName;
    Object payload;
    FieldSelectionBean selection;
    IOrmEntity parentEntity;
    IEntityRelationModel relationModel;
    IEvalScope scope;
    IServiceContext context;
    int order;

    void applyResult(Object result) {
        // 回挂到 parent relation
    }
}
```

这里故意保持简单：

- 先把它视为 pipeline 中的显式记录对象
- 不急着抽象成通用 command bus
- `applyResult` 可以先是内聚在对象上的方法，后续再决定是否拆成策略接口

### 13.2 `CrudBizModel` 建议新增入口

建议不要把 delayed action 的执行混进已有 `doSave/doUpdate` 代码块里到处散落判断，而是给出一个明确的 phase 方法，例如：

- `executeDelayedRelationActions(EntityData<T> entityData, IServiceContext context)`
- 或更细一点：`executeDelayedRelationActions(List<DelayedRelationAction> actions, IServiceContext context)`

推荐调用顺序：

```text
buildEntityDataForSave/Update
  -> copyToEntityWithDelayedActions(...)
  -> prepareSave/prepareUpdate
  -> checkDataAuth / checkUnique / other checks
  -> executeDelayedRelationActions(...)
  -> doSaveEntity/doUpdateEntity
```

这样 `CrudBizModel` 里会多出一个明确的 phase 边界，后续测试也更容易分别覆盖：

- parent 前置检查失败时，不执行 delayed actions
- delayed action 失败时，整体事务回滚
- delayed action 成功后，parent relation 已正确回挂

### 13.3 `EntityData` 或 copier result 的承载方式

当前 `copyToEntity(...)` 是 `void` 风格。引入 delayed action 后，建议二选一：

方案 A：扩展 `EntityData`

- 在 `EntityData` 上增加 `List<DelayedRelationAction> delayedActions`
- `CrudBizModel` 继续围绕 `EntityData` 传递上下文

方案 B：让 copier 返回结果对象

- 例如 `OrmEntityCopyResult`
- 包含 `targetEntity`、`delayedActions`

我更偏向方案 A，因为当前 `CrudBizModel` 本来就以 `EntityData` 作为 save/update 主线载体，侵入更小。

### 13.4 `OrmEntityCopier` 建议拆分的方法

为了让两阶段模型在代码上清晰，建议至少拆出下面几类内部方法：

- `copyBasicFields(...)`
- `copyRelationFields(...)`
- `resolveWriteMode(...)`
- `copyRelationInline(...)`
- `copyRelationLink(...)`
- `collectRelationBizAction(...)`

如果继续沿用当前 `copyField(...)` 单入口，也建议让 relation 分支内部尽快下沉为这些方法，避免后续把 `inline/link/biz` 逻辑塞成一个过长的大函数。

### 13.5 `CrudToolProvider` 是否需要变化

如果 delayed action 的 collector 放在 `EntityData` 上，则 `CrudToolProvider` 只需要负责：

- 创建带 `IServiceContext` 的 `OrmEntityCopier`
- 或创建时传入 delayed-action collector

也就是说，`CrudToolProvider` 仍然只是装配点，不承担 relation routing 语义。

## 十四、toOne / toMany 行为表

### 14.1 toOne

| mode | 输入 | copier 阶段行为 | delayed phase 行为 | parent 回挂 |
|------|------|-----------------|--------------------|-------------|
| `inline` | `null` / id / object | 按现有 `copyRefEntity` 处理 | 无 | copier 立即回挂 |
| `link` | `null` / id / object | 只根据 identity 绑定或解绑，不递归更新 target 字段 | 无 | copier 立即回挂 |
| `biz` | `null` / id / object | 不直接更新 target；收集一个 delayed action | 调 target `save/update/delete` 或 unlink | delayed action 结束后回挂 |

补充约束：

- `biz + null` 是否解释为 unlink 还是 no-op，需要结合 relation 是否允许解绑单独锁定
- `biz + object + 无id` 通常映射到 `save`
- `biz + object + 有id` 通常映射到 `update`

### 14.2 toMany

| mode | 输入 | copier 阶段行为 | delayed phase 行为 | parent 回挂 |
|------|------|-----------------|--------------------|-------------|
| `inline` | collection | 按现有 `copyRefEntitySet/syncEntitySet` 处理 | 无 | copier 立即同步集合 |
| `link` | collection | 只同步 relation membership，不递归更新 item 字段 | 无 | copier 立即同步集合 |
| `biz` | collection | 不直接递归更新 item；为每个 item 收集 delayed action | 调 target 标准 action，并聚合结果 | delayed action 结束后统一同步集合 |

当前更稳妥的主线是：

- `biz` 下每个 item 是否 `save/update/delete` 由 item 自身 `_chgType` 或 identity 决定
- 第一版明确默认行为：**缺席项 = no-op**，即不在本次提交中出现的集合项不会被自动删除或解绑
- 后续可扩展为 delete / unlink 语义，这是加法，不影响已有行为
- 第一版先只支持显式提交的 item 执行 biz action

### 14.3 `link` 的最小输入约束

`link` 的目标是只改 relation，不改 target 业务字段。因此更合理的最小输入约束是：

- toOne：`null`、`id`、或包含 identity 的 object
- toMany：identity object 列表或简单 id 列表
- 非 identity 字段即使出现，也不应产生 target business update

第一版实现要求：**`link` 模式下 validator 跳过 non-identity 字段的校验**（mandatory、type check 等），而不是留到后续。这是因为：

- 这些字段在 `link` 语义下不会被使用，对它们做校验只会产生误导性报错
- 实现方式是 validator 在处理 nested object 时检查当前 prop 的 `writeMode`，若为 `link` 则只校验 identity 所需字段
- 后续可进一步严格化为"报错多余字段"，但第一版先做到"不校验不报错"

在设计上至少要明确：

**`link` 不会因为 payload 中顺带携带了业务字段，就把它们解释为 target update。**

## 十五、预估修改点

| 文件 | 改动 |
|------|------|
| `OrmEntityCopier` | relation prop 分支增加 `writeMode` 解析；`biz` mode 改为收集 `DelayedRelationAction`；必要时明确两阶段 copy 与内部方法拆分 |
| `CrudBizModel` | 增加 delayed relation action 的执行 phase、结果回挂与测试入口 |
| `EntityData` 或新 result 对象 | 承载 delayed actions |
| `ObjMetaBasedValidator` | 允许 `_writeMode_*` 透传，类似 `_chgType_*` |
| `CrudToolProvider` | 如有需要，负责创建带上下文与 delayed-action collector 的 copier |
| xmeta schema | 可选：为 prop 增加 `writeMode` 的枚举声明和 IDE 提示 |

## 十六、不在本次范围

- 自定义 action request / command DTO 的设计
- 非 CRUD 语义输入模型
- selection-scoped mandatory 校验细化实现
- `biz` mode 下 toMany 缺席项扩展为 delete / unlink（第一版为 no-op）
- 跨 relation prop 的数据传递与显式 `dependsOn` 机制
