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
```

`writeMode` routing 的本质是“某个 relation prop 的 JSON 该如何映射到实体图”，因此自然属于 `OrmEntityCopier` 的职责，而不是 `ObjMetaBasedValidator`。

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

文档当前不强制锁定最终的 merge algorithm，但平台语义上应保持：**prop 声明优先于前端自由覆盖**。

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

因此 `biz` mode 仍然可以落在 copier 内部，不需要把整个 routing 上提到应用层 pipeline。

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
- 而是把这份同构 JSON 交给 target 自己的标准 BizModel 入口解释
- 从而让 target 自己的 validator / auth / hook / state machine 生效

因此，`biz` 的本质是：

**relation prop 的 target-side write semantic 切换为“通过 target BizModel 解释这份同构 JSON”。**

### 5.4 统一语义表

| mode | 输入形状 | 解释方式 | 是否更新 target 业务字段 |
|------|----------|----------|--------------------------|
| `inline` | 同构 relation JSON | aggregate recursive copy | 是 |
| `link` | 同构 relation JSON | 只投影 relation structure | 否 |
| `biz` | 同构 relation JSON | 交给 target BizModel 解释 | 是 |

## 六、与现有 `_chgType` 的关系

`writeMode` 和 `_chgType` 是两个正交维度：

- `writeMode` 决定 relation prop 的整体解释语义
- `_chgType` 继续决定某个集合项在同构同步过程中的增删改意图

因此：

- 不需要替换现有 `_chgType`
- `writeMode` 是 prop-level semantic
- `_chgType` 是 item-level delta hint

## 七、Validator 层需要补的最小支持

`ObjMetaBasedValidator` 不负责 routing，但需要允许 `_writeMode_*` 这类字段透传，方式与 `_chgType_*` 一致。

也就是说，Validator 侧只需要：

- 识别 `_writeMode_<propName>` 是合法控制字段
- 在 validate/convert 后保留该字段到 validated map 中

除此之外，不应把 `writeMode` 的业务语义放到 Validator 层解释。

## 八、fieldSelection 与 mandatory 的关联问题

当前 `ObjMetaBasedValidator._validate()` 遍历 `data.entrySet()`，导致一个已知 gap：

- 如果 selection 声明了某 prop
- 但数据中缺失该字段
- 当前可能不会触发 selection 范围内的 mandatory 校验

这是独立于 relation writeMode 的问题，但与“ObjMeta 作为写侧对偶模型”的理念有关。

建议后续单独处理：

- `save`：按创建语义检查 mandatory
- `update`：按部分更新语义检查 mandatory
- 自定义 selection：selection 范围内缺失 mandatory 字段应报错

## 九、与其他常见设计的比较

### 9.1 对比纯 aggregate nested save

纯 aggregate nested save 只支持一种解释语义：所有 nested payload 都 inline 保存。

本文设计比它强在：

- 仍保持同构对象图输入
- 但允许 relation prop 声明不同写入语义
- 可以表达“只改关系，不改 target”以及“target 必须走自己的业务入口”

### 9.2 对比 op-style nested mutation

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

### 9.3 对比 action DTO 分裂式设计

另一种常见方案是为不同 relation 行为拆很多专用 DTO / action：

- 一个 save DTO
- 一个 link DTO
- 一个 child update DTO

短期清楚，但长期会导致：

- 元数据复用下降
- 前端模型分裂
- auth / transform / validation 规则散落

本文设计把核心语义收敛到 prop-level metadata，更符合 Nop 的 model-first / metadata-first 平台方向。

## 十、当前结论

当前阶段推荐的主线是：

1. 继续围绕 `OrmEntityCopier` 做 relation prop 的 write semantic routing
2. `writeMode` 保持为 prop-level metadata，前端只是在允许范围内参与选择
3. 整体设计坚持两个原则：
   - **对偶性**：同一个 ObjMeta 同时支撑 CRUD 的读写切片
   - **同构性**：relation payload 始终是对象图的同构片段，lane 只改变解释语义

这比纯 aggregate save 更完整，比 op-style nested mutation 更统一，比 action DTO 分裂更平台化。

## 十一、预估修改点

| 文件 | 改动 |
|------|------|
| `OrmEntityCopier` | 在 relation prop 分支增加 `writeMode` 解析与分流 |
| `ObjMetaBasedValidator` | 允许 `_writeMode_*` 透传，类似 `_chgType_*` |
| `CrudToolProvider` | 如有需要，负责创建带上下文的 copier |
| xmeta schema | 可选：为 prop 增加 `writeMode` 的枚举声明和 IDE 提示 |

## 十二、不在本次范围

- 自定义 action request / command DTO 的设计
- 非 CRUD 语义输入模型
- selection-scoped mandatory 校验细化实现
- `writeMode` 前端覆盖规则的最终细节锁定
