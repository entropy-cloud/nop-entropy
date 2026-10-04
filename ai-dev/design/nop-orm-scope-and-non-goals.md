# nop-orm / EQL 能力边界与显式 Non-Goals

**日期**：2026-10-04
**范围**：`nop-persistence/nop-orm`、`nop-persistence/nop-orm-eql`、`nop-persistence/nop-dao`
**状态**：active
**灵感来源**：nop-orm / EQL 能力盘点中被误判为"功能缺失"的特性裁定

---

## 一、设计结论

1. **不支持对象继承映射**（JOINED / TABLE_PER_CLASS / 单表判别符 / MappedSuperclass 等价物）。这是设计层面的确定性裁定，不是待实现项，也不是缺陷。
2. **不实现 JPA 形态的二级表**（`@SecondaryTable` 那种"按列把一个实体摊到多张表、运行期决定每个字段从哪张表读"）。二级表所需的语义由平台既有的 **alias 机制**承担，见 §3.2。
3. **不支持多对多连接表（join table）的自动持久化**：`m2m` 必须在实体层显式建模为连接实体，由平台按普通实体能力处理。
4. **不支持 Savepoint**：不实现，也不接受相关需求。
5. **不在 ORM 层做 PreparedStatement 缓冲**：属于 DataSource / 连接池层职责。
6. **不支持 XA / 2PC**：跨数据源事务组按 best-effort 顺序提交语义处理。
7. 事务**隔离级别**归属 `ITransactionTemplate` 层实现（见 §五 G1，当前为已知 gap，非 non-goal）。

以上裁定适用于 EQL 与 ORM 映射两层，不因"主流 ORM 有该特性"而改变。

---

## 二、背景与动机

nop-orm 与 JPA 系 ORM 的根本分歧在于**实体形态在编译期即完全确定**：实体类是 codegen 的产物，字段访问、脏检查触发、懒加载触发全部由生成代码内联完成（getter 内联 `onPropGet`、setter 内联 `onPropSet`），运行期不做字节码增强、不创建代理、不做运行期子类装配。

这条根本约束直接推导出下述边界。凡是需要"运行期决定实体真实类型 / 跨表拼装同一个对象 / 隐式生成持久化结构"的能力，都与该约束正面冲突，因此被裁定为**不应该实现**，而不是"暂时没做"。

---

## 三、核心设计

### 3.1 实体形态编译期确定

- 实体类由 `model/*.orm.xml` 经 codegen 生成，模型是唯一真源；模型变更必须重新生成，不存在运行期修补实体形态的路径。
- L1 实体缓存的身份键是 `(entityName, id)`。继承映射下同一行数据会映射到多个候选实体类，键无法唯一定位已加载实例，代理替换与批量加载的语义也随之失效。
- 懒加载触发点内联在生成的 getter 中，绑定到具体属性；继承层次上的属性覆盖会让触发点与属性解析规则二义化。

### 3.2 二级表语义由 alias 机制承担

一个实体需要把数据放在主表以外的物理表时，Nop 的既定路径是 alias，而不是 JPA 式二级表。存在两类 alias，各自对应一种跨表存储诉求：

| 机制 | 模型声明 | 承载的二级表语义 | 读写 |
|------|---------|-----------------|------|
| **关联表列投影** | `<alias name="collegeName" propPath="simsCollege.collegeName" type="String"/>` | 把 to-one 关联表上的列投影为本地属性 | 可读可写 |
| **纵表（EAV）扩展属性表** | 目标实体声明 `kvTable="true"`，本地通过 `<alias propPath="extFields.xxx.type">` 声明带类型的快捷属性 | 任意数量的动态扩展属性存到独立扩展表，不动主表 DDL | 可读可写 |
| **组件内路径** | `<alias propPath="examScoreDecimal.normalizedValue">` | 组件字段投影为顶层属性 | 可读可写 |

三类 alias 共用同一条读写通道：生成代码的 getter/setter 落到 `internalGetAliasValue/internalSetAliasValue`，后者即对整条 `propPath` 做 `BeanTool.getComplexProperty/setComplexProperty`。因此 alias 是**属性路径级别的读写投影**，不是只读视图——写入沿路径进行（关联赋值 / 组件字段赋值 / 纵表行写入），由该路径自身的持久化机制落库。

纵表扩展属性表具备完整运行时语义：按 owner key + `fieldName` 定位、按 `fieldType` 分列存不同数据类型、支持 `batchLoadProps` 批量预取、支持以扩展字段值作为关联条件。它使"主表 + 扩展表"在实体侧表现为**一个扁平的实体**，这正是二级表要达到的使用效果，且完全落在编译期确定的形态之内——扩展属性在模型里是显式 alias，不是运行期从多表拼装的。

这套机制比 JPA `@SecondaryTable` 强的地方在于它不要求扩展表由本框架设计：任何具备 (owner key, field name, 按类型分列的值) 形态的既有纵表——包括历史系统的属性表——声明 `kvTable="true"` 后即可按 key 反转成横表属性，并立即获得类型化读写、批量预取、租户隔离与审计能力。`kvTable` 机制既支持全局通用扩展表（主键 `entityName + entityId + fieldName`），也支持按实体专属扩展表（如 `examId + fieldName`）。

因此本裁定的准确表述是：**不需要 JPA 形态的二级表**。真正被拒绝的只有"字段到表的映射在运行期才决定"这一种形态，因为它要求实体形态脱离编译期确定性（见 §3.1）。使用侧写法见 `docs-for-ai/02-core-guides/orm-model-design.md` 的"跨表属性：alias 与扩展属性表"一节。

### 3.3 关系由外键显式承载

`<to-one>` / `<to-many>` 的外键列始终位于子表（见 `docs-for-ai/02-core-guides/orm-model-design.md` §关系声明）。父→子的集合变更通过级联保存子实体表达（`CascadeFlusher`），父实体自身不承担连接表写职责；`ICollectionPersister.flushCollectionChange` 只负责把变更登记到批处理队列、在变更成功后失效全局缓存，不产生集合成员增删 SQL。这是设计意图。

需要说明的是持久化驱动层的同名方法：`ICollectionPersistDriver.flushCollectionChange` 及其 JDBC 实现在当前代码中**没有任何调用点**，是死接口（其实现为空方法体，缓存失效实际发生在 `CollectionPersisterImpl` 的批处理成功回调中）。该死接口的清理已登记为 `ai-dev/plans/2307-eql-live-defect-and-dead-surface-remediation.md` 的执行项，本节描述的语义归属只针对 `ICollectionPersister`。

### 3.4 多对多的实体层表达

m2m 关联表是"带两个外键的普通表"，因此它在 Nop 中就是普通实体。显式建模后可直接获得：字典与 `_label`、租户列、逻辑删除、审计戳、数据权限、审批流、GraphQL CRUD、页面与前端关联渲染——这些能力隐式 m2m 无法提供。

### 3.5 数据源层职责外置

连接级资源（PreparedStatement 缓冲、连接池、读写分离路由）统一在 DataSource / 连接池层解决，ORM 层不重复实现，避免两处策略冲突。

事务边界同理归属 `ITransactionTemplate` / 连接层：EQL 编译器不承担事务控制语义。

### 3.6 EQL 语法面只保留"能产出正确 SQL"的构造

EQL 的语法与 AST 存在一条硬约束：**一个语法构造要么有一条能把它打印成正确目标 SQL 的输出路径，
要么必须在解析期被拒绝**。不允许"能解析、但不输出关键字 / 输出畸形标识符 / 编译产物为空"这种中间态——
因为它把错误从编译期推迟到执行期，且往往以"看起来跑通了"的形态出现。

据此裁定以下三类构造为**不支持并从语法中移除**（不是"暂时没做"，见 §四）：

| 构造 | 移除理由 |
|------|---------|
| 集合聚合访问 `coll[where].func(args)` | 无输出器、无语义分析，永不可能产出正确 SQL；其语义是**聚合量词**（`count`/`sum`），与集合算子 `_some`/`_all` 的**存在性量词**不等价，降级映射会改变查询语义。集合上的存在性判断用 `_some`/`_all`，聚合值应下推到 SQL 表达式或子查询 |
| ORDER BY 项上的 `NULLS FIRST/LAST` 与 `COLLATE` | EQL 侧无语法入口，字段悬挂。空序策略的**方言门控实现已存在**于 bean 路径（`OrderFieldBean.nullsFirst` → `SQL.SqlBuilder.nullsFirst`，并在 `nop-metadata` 的聚合查询中按方言判定省略或 fail-fast）；在 EQL 侧另建一套会产生第二个未门控的并行实现 |

事务控制语句 `commit` / `rollback` 同样**不被支持**，但采用「显式失败」而非删除的终态：
编译期抛 `nop.err.eql.not-support-transaction-statement`（错误信息指向 `ITransactionTemplate`），
EQL 通道的 `toSqlString()` 输出对应关键字使 round-trip 无损。
之所以不用删除，是因为 xgen 的 list 规则按「备选分支的最小公共父类」推导元素类型——
`sqlStatements_` 的元素类型来自 `sqlStatement` 的备选分支，删掉唯一的非 DML 分支后
它会从 `SqlStatement` 收窄为 `SqlDmlStatement`，迫使 `SqlProgram.statements` 的公开签名变更
并波及 `nop-metadata` 的两个消费方。保留 AST 节点 + 显式失败，可在不触碰公开签名的前提下
消除原先「编译产物为空字符串且不报错」的静默无操作。

其中 ORDER BY 项上的 `NULLS FIRST/LAST` 字段被删除只是**第一步**：它当时是悬挂字段（EQL 侧无语法入口）。
空序能力的实际实现由 plan 2308 承接，其契约见 §3.8；`COLLATE` 则维持不支持。

`cross` / `lateral` / `tumble` / `range` / `window` 等字面量**可**作标识符（登记为非保留字），
这与主流 SQL 方言一致；`cross` 同时出现在 MySQL 方言的 `<reservedKeywords>` 中，二者不冲突。

### 3.7 方言 schema 节点必须有真实消费方

`dialect.xdef` 中的每个节点都必须是**活的**：要么有读取方并真正影响行为，要么从 schema 中删除。
不允许保留"能配置但永远不生效"的节点——那会让使用者以为配置生效了。

据此：`<upsertHandler>` 因零消费方且无对应功能而**删除**；
`<sqlExceptionTranslator>` 因实现被硬编码、配置被静默忽略而**接线**（改为读取模型字段，缺省回落默认实现）。

同样的判据也适用于能力位：`<features>` 中的 `supportNullsFirst` 曾长期"声明即死"（见 §3.8）。

### 3.8 排序空序策略：只依赖「是否支持原生语法」这一个能力位

`ORDER BY` 的空序修饰（`NULLS FIRST` / `NULLS LAST`）在各方言上的落地只由**一件事**决定：
目标数据库**是否接受原生语法**。

- 接受 → 直接输出 `NULLS FIRST` / `NULLS LAST`；
- 不接受 → 用 `CASE WHEN <col> IS NULL THEN ... END, <col> <dir>` 改写，该写法只用到 SQL 标准中
  普遍支持的 `CASE WHEN` 与 `IS NULL`，因此对**任何**方言都语义正确。

据此，**不使用** `dialect.defaultNullsFirst` 做「请求与方言缺省一致就省略子句」的优化。
原因是一个布尔量无法表达真实的空序模型：H2 / PostgreSQL / MySQL 的缺省都随排序方向翻转
（实测），而 DuckDB **不翻转**（其 `default_null_order` 有 always-first / always-last /
first-on-asc-last-on-desc / last-on-asc-first-on-desc 四种模式）。用单布尔推导 DESC 缺省会让
`ORDER BY x DESC NULLS FIRST` 在 DuckDB 上被误判为"缺省即满足"而省略子句，从而**静默返回相反行序**——
查询成功、数据错乱、分页取到错数据。

代价是产出略冗长（即使请求恰与方言缺省一致也显式输出）；收益是彻底消除「方言缺省推导」这一整类
静默错误。这条取舍是本设计在"正确性优先于 SQL 简洁"上的明确选择。

改写的**适用边界**同样是契约的一部分：改写需要复制排序表达式，因此只对**纯列引用**允许；
含绑定参数标记、序号定位（`ORDER BY 1`）、易变函数的排序项一律显式失败而非产出错误结果。
`SELECT DISTINCT` 查询在改写路径上还要求排序列已出现在投影列表中。
窗口函数内（`OVER (...)` 与命名窗口 `WINDOW w AS (...)`）无法附加额外排序键，改写结构性不适用，
故同样显式失败。

---

## 四、拒绝了什么

| 被拒能力 | 拒绝理由 |
|---------|---------|
| 继承映射（三种策略） | 与"实体形态编译期确定 + 无代理"根本冲突；破坏 L1 身份键唯一性；破坏模型→Java 的可逆性 |
| JPA 形态的二级表 | 字段到表的映射必须在编译期确定；跨表存储需求由 alias 机制承担（关联表列投影 + `kvTable` 纵表扩展属性表），能力覆盖且不破坏实体形态确定性 |
| 隐式 m2m 连接表 | 隐式表 / 隐式列 / 隐式增删使持久化结构不可见，与"模型即元数据、可逆计算"直接冲突；且平台能力（字典、租户、审批、页面）无法作用于隐式结构 |
| Savepoint | 无实际业务使用；现代分布式数据库对 savepoint 支持本身不完整 |
| ORM 层 PS 缓冲 | 归属 DataSource 层；ORM 实现会产生两套策略 |
| XA / 2PC | 现代分布式数据库不提供；跨数据源按 best-effort 顺序提交，并在提交失败时回滚处理 |
| Criteria API | EQL + `QueryBean`/`FilterBeans` 已覆盖动态查询场景；Criteria 会引入第二套查询抽象 |
| 集合聚合访问语法 `coll[where].func(args)` | 无输出器、无语义分析，永不可能产出正确 SQL（见 §3.6）。需要集合聚合时应下推到 SQL 表达式或子查询，而不是在 EQL 层引入一套无输出路径的语法 |
| EQL 层事务语句 `commit`/`rollback` | 事务边界归属 `ITransactionTemplate` / 连接层（见 §3.5）；EQL 编译器输出裸 `COMMIT` 不携带任何事务语义，且当前 DAO 层并不消费它。终态取「显式失败」而非删除，理由见 §3.6 |
| EQL 层 ORDER BY 上的 `COLLATE` | 需按库校验排序规则名字（MySQL `utf8mb4_general_ci` / PG collation 名 / Oracle `NLS_SORT` 各异），且 H2 实测在 ORDER BY 表达式上不接受 COLLATE 语法，设计面远大于一个布尔能力位。`CAST(x AS type COLLATE y)`（`SqlTypeExpr.collate`）仍是活语法 |
| EQL 层「按方言缺省省略空序子句」 | 单个布尔无法表达各方言的真实空序模型（DuckDB 四种模式、不随方向翻转），推导错误会静默返回相反行序。见 §3.8 |

---

## 五、与已有设计的关系

### 已裁定但仍是 gap 的项

| ID | 项 | 归属 |
|----|----|------|
| G1 | 事务隔离级别 | 应在 `ITransactionTemplate` 层实现（新增 `isolation` 语义入口，由 `ITransaction` 侧应用到 JDBC 连接）。当前 live baseline 无任何隔离级别 API，`ITransactionTemplate`/`ITransaction` 均无相关方法，`Connection#setTransactionIsolation` 全仓未调用。属待实现能力，不属 non-goal。 |

### 相关文档

- `docs-for-ai/02-core-guides/orm-model-design.md` — ORM 模型设计规范（使用侧契约）
- `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` — EQL 语法与方言能力边界
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md` — 事务与并发契约
- `ai-dev/plans/2307-eql-live-defect-and-dead-surface-remediation.md` — EQL 已确认 live defect 的收敛计划