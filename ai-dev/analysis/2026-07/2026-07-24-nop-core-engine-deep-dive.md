# nop-entropy 核心引擎剖析：core / xlang / xdef / dao / graphql / NopIoC

> Status: resolved
> Date: 2026-07-24
> Scope: `nop-kernel`（nop-core / nop-xlang / nop-xdefs / nop-api-core / nop-commons）、`nop-core-framework`（nop-ioc / nop-config）、`nop-persistence`（nop-dao / nop-orm / nop-orm-eql）、`nop-service-framework`（nop-graphql-core / nop-graphql-orm / nop-biz）；六大引擎模块职责、协作链、运行时执行链 + NopIoC vs Spring 工程差异 + 4 框架联网对标
> Conclusion: nop-entropy 的核心引擎以「VFS + Delta 资源层 → XDef 元模型 → XDSL 加载期合并 → 反射式 BizModel → GraphQL 自动暴露 → ORM/EQL 执行」为运行时主干；其差异化定位在于 (1) Bean 发现完全文件化（`beans.xml`，无注解扫描）；(2) AOP 是**源码生成式**（build-time 生成 `__aop` 子类 + 运行时注入拦截器数组，非 CGLIB/ASM 运行时字节码）；(3) 字段注入要求非 `private`（反射层 `ClassModelBuilder` 直接跳过 private 字段）；(4) 配置注入双实现（IoC 容器 `@cfg:`/`@r-cfg:` 与 DSL 加载期 `@cfg:` 写法相同但实现独立）。相比 Spring（运行时反射+扫描）、Quarkus Arc（构建时 CDI 织入）、Micronaut（编译时 APT DI），nop 把"复杂性预算"放在**加载期代码生成 + XDSL Delta**，而非启动期反射。
> Mission: nop-deep-analysis（Work Item A2）
> Superseded By: （本分析为 A4 GraphQL/服务层、A5 模块矩阵提供核心引擎参照；若 A7 capstone 重新组织引擎章节，则被替代）

## Context

- **要回答的问题**：平台骨架模块（nop-core / nop-xlang / nop-xdef / nop-dao / nop-orm / nop-graphql / NopIoC）如何协作支撑「可逆计算」在运行时的执行？NopIoC 与 Spring 在工程层面有哪些系统性差异（字段注入可见性、注解扫描、`beans.xml` 发现、AOP 机制）？与 Spring（Boot/Context）、Quarkus、Micronaut、Helidon 的 IoC/核心抽象/启动模型如何对标？
- **涉及模块/子系统**：`nop-kernel`（core/xlang/xdefs）、`nop-core-framework`（ioc/config）、`nop-persistence`（dao/orm/orm-eql）、`nop-service-framework`（graphql/biz）。
- **约束**：仅引擎机制剖析——A2 覆盖 GraphQL BizModel 如何注册为 operation；A4 覆盖 CRUD 约定（`CrudBizModel`、`@BizMutation`、xbiz、xmeta 字段可见性）。不重复 A1 已建立的可逆计算公理体系（`ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`）。
- **来源基线**：`docs-for-ai/04-reference/source-anchors.md`（IOC/EXT/VFS/DQL/GQL/CFG/MOD/TNT/TXN 系列）、`docs-for-ai/02-core-guides/`（ioc-and-config 等）、`docs/compare/nop-vs-springcloud.md`、`docs/theory/lowcode-ioc.md`。本分析通过 3 个并行 explore 子 agent 对 **25+ 个 source-anchors 锚点**做源码交叉核对（全部 PASS）建立。

## 1. 六大引擎模块职责与协作总览

### 1.1 模块职责矩阵

| 模块 | 职责 | 关键类/接口（source-anchor） |
|------|------|------------------------------|
| `nop-core` | 核心抽象：VFS/Delta 资源、反射（`ClassModel`/`IAopProxy`）、JSON/YAML、模块管理 | `DeltaResourceStore`（EXT-003/VFS-001）、`DefaultVirtualFileSystem`（VFS-003）、`DeltaResourceStoreBuilder`（VFS-002）、`ModuleManager`（MOD-001）、`JsonTool`（DDD-002）、`ClassModelBuilder`（IOC-001）、`AopCodeGenerator`/`IAopProxy`（AOP） |
| `nop-api-core` | 无依赖核心 API：`QueryBean`、`IContext`、`ConvertHelper`、`CoreMetrics`、注解 | `QueryBean`（DQL-001）、`ConvertHelper`（DDD-003）、`CoreMetrics`（DDD-006）、`IContext`/`ContextProvider`（TNT-001/002） |
| `nop-commons` | 通用工具：字符串、日期、集合 | `StringHelper`（DDD-004）、`DateHelper`（DDD-005） |
| `nop-xlang` | DSL 解析与 Delta 合并执行链、xpl 模板、xlib 编译期元编程 | `XDslExtender`（EXT-002）、`DslModelParser`（EXT-004）、`DeltaMerger`、`XplLibTagCompiler`（EXT-006） |
| `nop-xdefs` | 元模型定义（XDef）：平台所有 DSL 的 schema | `xdsl.xdef`（EXT-001/XLANG-003）、`obj-schema.xdef`、`orm.xdef` 等 |
| `nop-config` | 配置系统：多 source 优先级合并 + profile | `ConfigStarter`（CFG-001） |
| `nop-ioc` | Bean 容器：文件化发现、字段注入、配置注入、AOP 织入 | `AppBeanContainerLoader`（IOC-002）、`ConfigExpressionProcessor`（RESOLVE-003）、`AopBeanProcessor`、`BeanDefinition` |
| `nop-dao` | 数据访问抽象、Dialect、事务 | `DaoConfigs`（DB-001）、`DialectImpl`（DB-002）、`TransactionalMethodInterceptor`（TXN）、`dao-defaults.beans.xml`（IOC-003） |
| `nop-orm` | ORM 会话/模板、EQL 执行、租户隔离、拦截器 | `IOrmTemplate`（DQL-002）、`DaoQueryHelper`（DQL-006）、`EntityPersisterImpl`（TNT-004）、`IOrmInterceptor`（AUDIT-002） |
| `nop-orm-eql` | EQL → SQL 编译（含租户/逻辑删除注入） | `EqlTransformVisitor`（TNT-003） |
| `nop-graphql-core` | GraphQL 引擎、HTTP 入口、BizModel 反射暴露 | `GraphQLEngine`（IGraphQLEngine 实现）、`GraphQLWebService`（GQL-009）、`ReflectionBizModelBuilder`、`RpcServiceOnGraphQL`（RPC-008） |
| `nop-graphql-orm` | ORM relation fetcher 生成 | `OrmFetcherBuilder`（GQL-001） |
| `nop-biz` | BizModel 服务层、事务封装、Biz 对象管理 | `BizActionInvoker`（TXN-001）、`BizObjectManager`、`CrudBizModel`（BIZ-002） |

### 1.2 端到端运行时调用链（HTTP → SQL）

请求从 HTTP 进入到落库，统一经过 GraphQL 引擎中枢。以一个 mutation `/r/NopAuthUser__save` 为例（锚点均经源码核对）：

```
HTTP /r/{opName}
  └─> GraphQLWebService.runRest(opType, operationName, ...)          [GQL-009: GraphQLWebService.java:229]
        解析 operationName = "{bizObj}__{method}"（分隔符 OBJ_ACTION_SEPARATOR="__", GraphQLConstants.java:96）
        engine = BeanContainer.getBeanByType(IGraphQLEngine.class)   [GraphQLWebService.java:233]
      └─> IGraphQLEngine.executeRpcAsync(ctx)                        [GraphQLEngine.java:492]
            （引擎路径：operationInvoker 在 GraphQLEngine.java:108 注入，包装 session+txn）
          └─> GraphQLExecutor.invokeOperation                         [GraphQLExecutor.java:115]
                └─> ServiceActionFetcher（反射调用 BizModel 方法）    [ReflectionBizModelBuilder.java:349]
                      └─> @BizMutation/@BizAction 方法
                            └─> （mutation 默认进事务；query 不进）   [TXN-001: BizActionInvoker.java:42-48]
                                  └─> IOrmTemplate.findListByQuery/  [DQL-002: IOrmTemplate.java:190-194]
                                      save/update/delete
                                        └─> DaoQueryHelper → EQL 文本 [DQL-006: DaoQueryHelper.java:122]
                                              └─> EqlTransformVisitor 注入 tenantId=?/逻辑删除 [TNT-003: EqlTransformVisitor.java:317,330,372]
                                                    └─> 编译为方言 SQL → ISqlExecutor
```

**所有 HTTP 入口**（`/graphql`、`/r/{opName}`、`/p/{opName}`、`/px/{svc}/{method}`、`/jsonrpc`）统一由 `GraphQLWebService` 适配到 `IGraphQLEngine`（GQL-009）。`/r/` 与 `/p/` 共用 `runRest()`；`/px/` 分布式代理走 `runProxy()`（RPC-007）；`/jsonrpc` 走 `runJsonRpc()`。

**事务边界**：mutation 默认进入事务（`TransactionPropagation.REQUIRED`），query 不进入（`BizActionInvoker.java:43` 分支 `opType == query` 跳过 txn）。两条路径都强制 `ormTemplate.runInSession(...)`。

### 1.3 加载期 vs 运行期分离（核心设计不变量）

沿用 A1 公理 I（S-N-V 阶段分离）：**所有 Delta 合并、XDef 校验、xpl 元编程、BizModel 反射注册都在加载期完成**，运行时操作的是"烘焙"好的静态模型/注册表。引擎各层的加载期职责：

- **资源层（VFS/Delta）**：classpath 扫描 `_vfs/` + Delta 层叠加 → `DeltaResourceStore`
- **元模型层（XDef）**：`xdsl.xdef` 定义合并语义，`orm.xdef`/`obj-schema.xdef` 等定义领域结构
- **合并层（XDSL）**：`XDslExtender` 执行 `x:extends`/`x:gen-extends`/`x:post-extends` + `DeltaMerger`
- **Bean 层（IoC）**：`AppBeanContainerLoader` 加载 `beans.xml`，`AopBeanProcessor` 织入 AOP
- **Schema 层（GraphQL）**：`BizObjectManager.init()` → `GraphQLBizModels.build()` → `ReflectionBizModelBuilder` 扫描注解注册 operation
- **运行期**：`GraphQLEngine` 只查静态注册表执行；ORM 只执行编译好的 EQL→SQL

## 2. nop-core：核心抽象与 VFS/Delta 资源层

### 2.1 统一入口 helper（DDD-002~006）

平台强制所有通用操作收敛到少数 `@GlobalInstance` 单例，禁止散用 `System.currentTimeMillis()`/`new Date()`/`Jackson` 直调：

| helper | 锚点 | 职责 |
|--------|------|------|
| `JsonTool` | DDD-002 (`JsonTool.java:37`) | JSON **与** YAML 统一入口（SnakeYAML）；`parseBeanFromYaml`/`serializeToYaml`/`loadDeltaBeanFromResource` |
| `ConvertHelper` | DDD-003 (`ConvertHelper.java:57`) | 类型转换（String→Date/BigDecimal/Boolean，原始类型默认值） |
| `StringHelper` | DDD-004 (`StringHelper.java:76`) | 字符串工具（含 `isValidVPath`/`absolutePath` 等路径校验），null-tolerant |
| `DateHelper` | DDD-005 | 日期 helper |
| `CoreMetrics` | DDD-006 (`CoreMetrics.java:20`) | **唯一允许的时间 API**：`currentTimeMillis`(L52)/`currentTimestamp`(L56)/`currentDate`(L64)/`currentDateTime`(L68)/`nanoTime`(L72)；clock-backed，可经 `registerClock`(L44) 替换 |

### 2.2 VFS 分层资源解析（VFS-001~006）

资源解析是整个 Delta 机制的物理基础。`DefaultVirtualFileSystem`（VFS-003, `DefaultVirtualFileSystem.java:37`）是全局单例，构造期（L42-56）预注册 11 个 namespace handler：`super:`/`raw:`/`v:`/`module:`/`file:`/`class-path:`/`temp:`/`dump:`/`dev:`/`data:`/`dynamic:`。

`DeltaResourceStore`（VFS-001/EXT-003）实现分层解析，层序自顶向下：

```
tenant 层 (/_tenant/{tenantId}/...)        [DeltaResourceStore.java:122-133, enableTenantResource 时]
  → delta 层 (/_delta/{layer}/...)         [L135-145, 遍历 deltaLayerIds，首个命中即返回]
    → base 层 (store.getResource(path))    [L147-148]
```

`getSuperResource(currentPath, ...)`（L251-294）实现 `super:` 语义：定位调用者所在 delta 层的 index，从 `deltaIndex+1` 向下搜索（L282-290）——即 app 层 delta 的 `super:` 找到产品/平台/base 版本。

**VFS 构建入口**（VFS-002, `DeltaResourceStoreBuilder.java`）：`build()`（L64-80）从 4 个来源填充 `InMemoryResourceStore`：(1) 预构建的 `_vfs/index` 快速路径（L115-128）；(2) classpath 扫描 `_vfs/`（L130-153，`ClassPathScanner`）；(3) 配置的 lib paths（目录或 jar，L184-200）；(4) 当前 Maven 项目的 `src/main/resources/_vfs`（L202-235，`target/classes/` 透明重映射到源）。重复检测由 `CFG_CHECK_DUPLICATE_VFS_RESOURCE` 控制（L149-152），除非路径是 `_module`（允许重复）。

### 2.3 模块发现机制（MOD-001~005）

`ModuleManager`（MOD-001, `ModuleManager.java:39` `@GlobalInstance`）的 `discover()`（L70-94）：

- L74：`VirtualFileSystem.instance().findAll("*/*/_module")` —— 通过 glob 扫描零字节 `_module` 标记文件发现模块（**非**注解扫描）
- L71-72, 80-87：按 `CFG_MODULE_ENABLED_MODULE_NAMES`/`CFG_MODULE_DISABLED_MODULE_NAMES` 过滤
- L90/L116-127：`loadModuleById` 读取 `/{moduleId}/app.module.yaml`（MOD-002 `ModuleModel`）
- L165-171：`getEnabledModules()` 返回静态 ∪ 动态 ∪ 租户模块的合并视图，被所有模块相关资源查找消费

**关键约束**（`docs-for-ai/02-core-guides/ioc-and-config.md:131`）：`_module` 是 VFS 中唯一允许重复的资源类型——多个子模块（如 `nop-job-dao`/`nop-job-service`/`nop-job-local`）可为同一 moduleId 放置 `_module` 而不冲突。

## 3. nop-xlang / nop-xdef：运行时执行链（可逆计算落地）

> 公理映射详见 A1 §2。本节聚焦引擎层执行链实现细节。

### 3.1 元模型层：XDef（EXT-001）

`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef` 是平台所有 DSL 的元模型入口：

- L14：根 `<xdef:unknown-tag xdsl:schema="/nop/schema/xdef.xdef">` —— **自举**（`xdsl.xdef` 的 schema 指向 `xdef.xdef`，而 `xdef.xdef` 自定义自身，闭环）
- L36/L43：声明 `x:gen-extends`（Generator）/`x:post-extends`（后置差量）子元素
- **L70**：`x:override="enum:io.nop.xlang.xdef.XDefOverride=merge"` —— 合并模式枚举，默认 `merge`，枚举类 `XDefOverride` 定义 **8 种模式**（remove/replace/prepend/append/merge/merge-replace/bounded-merge/merge-super，`XDefOverride.java:19-50`）

### 3.2 合并执行链：XDslExtender（EXT-002）

`XDslExtender.java`（506 行）是 `x:extends`/`x:gen-extends`/`x:post-extends`/`x:override` 的核心展开与合并引擎：

- **L78**：构造期实例化 `this.merger = new DeltaMerger(keys);`（合并算子 `⊕` 的实现）
- **L81**：入口 `xtend(...)`：短路于 `x:validated`(L83) → `extendNode`(L198)
- `extendNode`：对每个 `x:extends` source 经 `buildSource`(L264) 加载，递归处理 `x:gen-extends`(L299 `genCpExtends`，**编译期**编译 XPL body) 后，从最外层到最内层经 `mergeNode`(L229-237) 折叠
- **L457**：`merger.merge(...)` 调用点（在 `mergeNode` L450 内）
- `postProcess`(L468-485)：在已合并结果上执行 `x:post-extends`（仅当 phase ≥ postExtends）
- L283-290：`super:` 重写为 `super:{currentPath}`

合并顺序（A1 §2.2 已述）：`F -> E -> Model -> D -> C -> B -> A`（最深的基 A 在底，post-extends 最后）。

**常量位置精度提示**（源码补充）：`EXTENDS`/`GEN_EXTENDS`/`POST_EXTENDS`/`OVERRIDE` 不是 `XDslExtender` 内字面常量，而是 `XDslKeys.java` 的 final String 字段（L72/74/75/85，ns 前缀 `"x"`），`XDslExtender` 经 `keys.XXX` 引用。

### 3.3 模型解析与初始化链：DslModelParser（EXT-004）

`DslModelParser.java`（137 行）把 XNode 转为运行时 bean 并完成延迟初始化：

- L110 `doParseNode0`：经 `DslBeanModelParser`(L125) 转 bean，L127-131 stamp schema path
- **L133-134**：`if (!disableInit && model instanceof INeedInit) ((INeedInit) model).init();` —— 反序列化后自动调用 `init()`（`INeedInit.java:13-15` 单方法延迟初始化契约）
- L28/L62-65：`disableInit` 字段与 fluent 选项存在，编辑器/原始加载场景可禁用

这是公理 I「加载期烘焙」的代码锚点：`init()` 后模型即静态化，运行时无 delta 历史。

## 4. nop-dao / nop-orm：执行模型、DQL 与租户隔离

### 4.1 DQL/QueryBean 核心模型（DQL-001, DQL-002, DQL-006）

`QueryBean`（DQL-001, `QueryBean.java`）是平台 DQL 的核心数据模型：

- `sourceName`(L49)、`fields`=`List<QueryFieldBean>`(L46)、`dimFields`(L51)、`filter`=`TreeBean`(L57)、`orderBy`=`List<OrderFieldBean>`(L59)
- 顶层聚合用 `aggregates`=`List<QueryAggregateFieldBean>`(L47)

> **源码精度提示**（交叉核对发现）：source-anchors.md DQL-001 描述把 `owner`/`aggFunc` 列为 `QueryBean` 字段，但它们实际是 `QueryFieldBean`（`fields` 内元素）与 `QueryAggregateFieldBean` 的属性，非 `QueryBean` 顶层字段。这是 anchor 描述的精度瑕疵，非平台行为偏差（记录为 Non-Blocking Follow-up）。

DQL 执行入口在 ORM facade：`IOrmTemplate`（DQL-002, `IOrmTemplate.java`）：

- **L190** `findListByQuery(QueryBean, IRowMapper)` / **L192** `findFirstByQuery(...)` / **L194** `existsByQuery(QueryBean)`（便捷重载 L196-202 默认 `ColumnMapRowMapper.INSTANCE`）

`DaoQueryHelper`（DQL-006, `DaoQueryHelper.java`）把 QueryBean 转 EQL SQL 文本：

- **L122** `queryToSelectFieldsSql(QueryBean, delFlagProp)`：组装 SELECT/FROM/WHERE/GROUP BY/ORDER BY，委托 `appendFilter`→`FilterBeanToSQLTransformer`(L203)、`appendGroupBy`(L185)、`appendOrderBy`(L207)
- 同族：`queryToCountSql`(L237)、`queryToDeleteSql`(L251)、`queryToUpdateSql`(L259)

### 4.2 租户隔离机制（TNT-001~008）

租户隔离横跨上下文→SQL 生成→实体加载→session 缓存：

| 锚点 | 位置 | 机制 |
|------|------|------|
| TNT-001 | `IContext.java` | 上下文持有 `getTenantId()`/`setTenantId()` |
| TNT-002 | `ContextProvider.java` | `currentTenantId()`/`runWithTenant()`/`runWithoutTenantId()` |
| **TNT-003** | `EqlTransformVisitor.java:317,330,336-339,372` | EQL 编译时：`entityModel.isUseTenant()` 为真则 `newTenantExpr` 生成 `SqlBinaryExpr(EQ, SqlParameterMarker bound to TenantParamBuilder)`，`addEntityFilter` 经 `node.makeWhere().appendFilter(filter)` 注入 `tenantId = ?` |
| TNT-004 | `EntityPersisterImpl.java` | 加载时自动填充/校验租户 ID |
| TNT-005 | `OrmEntityIdGenerator.java` | 保存时自动填充租户 ID |
| TNT-006 | `TenantOrmSessionEntityCache.java` | session 缓存按租户分区 |
| TNT-007 | `GenSqlHelper.java` | SQL 生成追加租户过滤 |
| TNT-008 | `OrmEntityModelInitializer.java` | `useTenant=true` 时自动创建 `nopTenantId` 列 |

关键：租户过滤在 **EQL 编译期**注入（TNT-003），运行期 SQL 已含 `tenantId = ?`，非应用层手动拼条件。

### 4.3 事务与拦截器（TXN-001, AUDIT-002）

mutation 默认事务化（TXN-001, `BizActionInvoker.java`）：`invokeActionSync`(L34) 先 `ormTemplate.runInSession(...)`(L42)，L43 分支 `opType == query` 跳过事务，否则 L48 `txnTemplate.runInTransaction(null, REQUIRED, ...)`。异步镜像 `invokeActionAsync` 同理（L64/L70）。

> **精度提示**：`BizActionInvoker` 是**直接调用旁路**路径；正常 GraphQL HTTP 路径通过引擎注入的 `operationInvoker`（`GraphQLEngine.java:108, setOperationInvoker L206`）包装 session+txn。两条路径都保证"mutation 进事务，query 不进"。

ORM 拦截器（AUDIT-002）：`IOrmInterceptor` 8 个 hook（pre/post save/update/delete/load/reset/flush）+ `MultiOrmInterceptor` 按 `IOrdered` 聚合；配置式 `orm-interceptor.xml` 支持 per-entity + XPL source。

## 5. nop-graphql：引擎核心与 BizModel 自动暴露

### 5.1 引擎与 HTTP 入口（GQL-009）

- **接口**：`IGraphQLEngine.java:34`
- **默认实现**：`engine/GraphQLEngine.java:95`（`implements IGraphQLEngine`）：`executeRpcAsync`(L492)、`executeGraphQLAsync`(L535)
- **HTTP 入口**：`GraphQLWebService`（抽象类，GQL-009）—— JAX-RS 子类（Spring/Quarkus 适配）路由请求到统一方法：`runGraphQL`(L70)、`runRest`(L229)、`runProxy`(L166)、`runJsonRpc`(L395)。各方法经 `BeanContainer.getBeanByType(IGraphQLEngine.class)`(L72/L181/L233) 取引擎
- **operationName 格式**：`{bizObj}__{method}`，分隔符 `__` 定义于 `GraphQLConstants.OBJ_ACTION_SEPARATOR`(L96)

### 5.2 BizModel 自动暴露机制（引擎层，A2 边界）

BizModel 方法如何成为 GraphQL operation（核心发现）：

- **生产端 schema loader**：`BizObjectManager.init()`（`nop-biz/.../impl/BizObjectManager.java:138-139`）调用 `bizModels.build(typeRegistry, bizModelBeans)`
- **构建器**：`GraphQLBizModels.build()`（`reflection/GraphQLBizModels.java:54`，也发现 `.xbiz`/`.xmeta` 资源 L64-72）→ `ReflectionBizModelBuilder.build(Object bean, TypeRegistry, GraphQLBizModels)`（`reflection/ReflectionBizModelBuilder.java:86`）
- **注解扫描注册**（L110-198）：遍历 `classModel.getMethods()`，按注解注册：
  - `@BizMutation`(L111-126) / `@BizQuery`(L128-144) / `@BizSubscription`(L146-157) / `@BizAction`(L159-168) / `@BizLoader`(L170-198)
  - 每个 operation 名经 `GraphQLNameHelper.getOperationName(bizObjName, action)`（L119/L136/L154）生成
  - 包裹为 `ServiceActionFetcher`(L349)，内部 `BeanMethodAction`(L373/L403) 反射调用 BizModel 方法

**A2/A4 边界划分**（plan Scope 明确）：A2 覆盖"BizModel 如何注册为 operation"（即本节反射注册机制）；A4 覆盖 CRUD 约定（`CrudBizModel`、`@BizMutation` 业务语义、xbiz、xmeta 字段可见性）。两者不重叠。

### 5.3 OrmFetcher 链（GQL-001~008）

relation 字段经 `OrmFetcherBuilder`（GQL-001, `nop-graphql-orm/.../OrmFetcherBuilder.java`）生成 fetcher：

- `getConnectionFetcher`(L135) 读 `graphql:connectionProp`(L137)/`graphql:queryMethod`(L136)
- `buildConnectionFetcher`(L158) 读 `graphql:disableLogicalDelete`(L160)/`graphql:maxFetchSize`(L161)/`graphql:filter`(L169)/`graphql:orderBy`(L177) → 返回 `OrmEntityPropConnectionFetcher`(L180)
- `buildFetcher`(L192) 按 relation kind 选 ref/set/column fetcher
- `graphql:*` 属性名常量全集定义于 `GraphQLConstants.java`(GQL-008, L26-43)

### 5.4 RPC 包装（RPC-008）

`RpcServiceOnGraphQL`（`rpc/RpcServiceOnGraphQL.java:31`）把 BizModel 包装为 `IRpcService`：`callAsync`(L50) 经 `GraphQLNameHelper.getOperationName`(L52) 映射到 GraphQL operation，`engine.executeRpcAsync`(L72) 分派（可选经 `IRpcServiceInterceptor`，L66-68）。`asProxy`(L42) 暴露为类型化 RPC 代理。

## 6. NopIoC：机制与 Spring 差异

### 6.1 Bean 发现：完全文件化（IOC-002, IOC-003）

**核心差异**：NopIoC **没有**注解驱动的 classpath bean 扫描（无 `@ComponentScan` 等价物）。Bean 发现纯靠 `beans.xml` 文件经 VFS 解析。

- `AppBeanContainerLoader.loadBeansFile()`（IOC-002, `AppBeanContainerLoader.java:107-150`）：遍历 `ModuleManager.getEnabledModules()`(L129)，对每个模块 `getModuleAppResources`(L254-273) 列出 `/{moduleId}/beans` 子项
- **L275-284 `isAppBeans`**：只接受 `app.beans.xml` 或任意 `app-*.beans.xml`
- L170-185：`/nop/autoconfig` 下的 `.beans` 资源 + `nop.ioc.app-beans.files` 配置补充
- **结论**：`@BizModel`/`@Inject` 等注解**只用于元数据标记和字段注入**，不会触发自动 bean 注册。所有 bean 必须在 `beans.xml` 有显式 `<bean>` 定义（`docs-for-ai/02-core-guides/ioc-and-config.md:127`）

平台内置 bean 广泛使用 `nop*` 命名约定（IOC-003，`dao-defaults.beans.xml`/`orm-defaults.beans.xml`/`biz-defaults.beans.xml`），这是仓库强约定但非 IoC 保留前缀规则。

### 6.2 字段注入可见性：private 不可注入（IOC-001）

**关键差异**：`@Inject private Foo foo;` 不会成为可靠注入点。

- `ClassModelBuilder.discoverDeclaredFields()`（IOC-001, `ClassModelBuilder.java:396-415`）：**L404 `if (Modifier.isPrivate(fld.getModifiers())) continue;`** 直接跳过 private 字段
- `discoverDeclaredMethods()`(L279) 同样跳过 private 方法
- 因此 private 字段/方法不会成为 `FieldModel`/反射可访问注入点

**推荐写法**（`docs-for-ai/02-core-guides/ioc-and-config.md:14-27`）：`protected` 字段或 setter 注入；这是 NopIoC 与 Spring 最易踩坑的差异（Spring 经反射 `setAccessible(true)` 可注入 private）。

### 6.3 配置注入：双实现（RESOLVE-003, CFG-001）

`@cfg:` 写法在两处实现，**写法相同、实现独立**：

**IoC 容器侧**（RESOLVE-003，作用于 `*.beans.xml` 与 `@InjectValue`）：

- `IocConstants.java:25,27`：`PREFIX_CFG = "@cfg:"`、`PREFIX_R_CFG = "@r-cfg:"`（reactive 变体）
- `ConfigExpressionProcessor.parsePrefixExpr`(L122-156)：L137 匹配 `@cfg:`/`@r-cfg:`，剥前缀、取 `|defaultValue`、按 `,` 分割 config var，返回 `ConfigValueResolver`(L151)，`reactive=true` 当匹配 `@r-cfg:`
- `ConfigValueResolver`(L26-102)：`resolveValue`(L69) 读 `container.getConfigValue(...)` 返回首个非空，否则默认值；`collectConfigVars`(L48) 仅当 `reactive=true` 收集 → **`@r-cfg:` 支持运行期重配，`@cfg:` 是启动快照**

**DSL 加载侧**（RESOLVE-001/002，作用于 YAML/JSON/XML delta 加载）：`ValueResolverCompilerRegistry.DEFAULT`（`BIND_EXPR_SYMBOL='@'`），内置 `@cfg:`/`@i18n:`/`@var:`/`@uuid:`/`@load:`/`@empty:`，加载期一次性求值（非 reactive）。

**配置系统加载链**（CFG-001, `ConfigStarter.java:106-233`）：`doStart()` 按优先级构建 `CompositeConfigSource`：env → system props → `bootstrap.yaml`(L108-110/243-252) → 配置中心/JDBC/key/props → `application.yaml`(L185/416) → `application-{profile}.yaml`(L187, L430-438)；每个 source 经 `ProfileConfigSource`(L195-209) 包装使 profile 前缀变量正确解析。**跨 source 时高优先级 source 直接生效**（与 Quarkus/Spring Boot 一致）。

### 6.4 AOP 机制：源码生成式（重大发现，source-anchors 无 AOP 锚点）

> AGENTS.md 记录"Nop 使用源码生成的 AOP，非运行时字节码"。本节给出完整代码证据链。

AOP 是**两阶段设计**：(1) build/codegen 期生成代理源码；(2) 运行期注入拦截器数组。**无运行时字节码操纵**（无 CGLIB/ASM 启动期生成）。

| 环节 | 锚点 | 机制 |
|------|------|------|
| 代理标记接口 | `IAopProxy.java:10-17` | 生成类 `implements IAopProxy`，暴露 `$$aop_interceptors(IMethodInterceptor[])` 供容器运行期注入拦截器链 |
| 源码生成器 | `AopCodeGenerator.java:40-88`（发代理 L73-74；重写拦截方法 L145-188） | `buildForMethods` 生成 `<Name>__aop extends <BaseClass> implements IAopProxy`，标 `@AopProxy({...})` 列出拦截的注解；被拦截方法 override 后构造 `AopMethodInvocation` 调 `$$inv.proceed()`，无拦截器时回退 `super.x()`(L162-165) |
| 构建期 codegen 任务 | `GenAopProxy.java:37-96` | 扫描 `target/classes` 的 `.class`，跳过已实现 `IAopProxy` 的(L72)与抽象类(L70)，运行 `AopCodeGenerator`，写 `__aop.java` 到 `target/generated-sources`，用 `JdkJavaCompiler`(L87-95) 编译 |
| 注解注册表（数据驱动） | `AopAnnotationsLoader.java:31-56`；注册表路径 `CoreConstants.java:26` `/nop/aop` 后缀 `.annotations`；示例 `nop-api-core/.../nop/aop/nop-api-core.annotations` | "可拦截注解"集合**数据驱动**：从 `_vfs/nop/aop/*.annotations` 读，非硬编码（列出 `@Transactional`、`@Cache`、`@SingleSession`、`@TccTransactional` 等） |
| IoC 运行期织入 | `AopBeanProcessor.java:67-417`（pointcut 匹配 L256-323；加载 AOP 类 L406-416；构造器重接 L343-355） | 收集声明 `ioc:pointcut` 的拦截器 bean 与标 `ioc:aop` 的 bean，匹配 pointcut 注解到 bean 的 `@AopProxy` 声明，**改写构造器**实例化生成的 `__aop` 子类 |
| 运行期拦截器注入 | `BeanDefinition.java:572-583`(`addInterceptors`) | bean 构造后 `((IAopProxy) bean).$$aop_interceptors(interceptors)`(L581) 注入容器解析的 `IMethodInterceptor[]` |
| 拦截器链执行 | `AopMethodInvocation.java:28-35` | `proceed()` 按索引走拦截器数组，耗尽后委托底层 `CallableMethodInvocation`（调 `__aop` 方法体里的 lambda，即 `super.x()`） |
| 事务拦截器实例 | `TransactionalMethodInterceptor.java:19-50`；bean 声明 `dao-defaults.beans.xml:95-96` | `@Transactional` 是注册的 AOP 注解，`__aop` 子类 override 方法后 `TransactionalMethodInterceptor.invoke` 把 `inv.proceed()` 包进 `txnTemplate.runInTransaction(...)` |

**生成代理示例**：`nop-core-framework/nop-ioc/src/test/java/io/nop/ioc/aop/MyClass$$aop.java:20-75` 展示生成形态：extends `MyClass`、implements `IAopProxy`、override `myMethod` 守卫 `$$interceptors==null` 后构造 `AopMethodInvocation`。

**与 Spring AOP 对照**：Spring AOP 在**运行期**经 CGLIB（或 JDK 动态代理）生成代理字节码；NopIoC 在 **build 期**生成 `__aop.java` 源码并编译，运行期只做拦截器数组注入——这与 Micronaut（APT 编译期生成）理念相近，但 nop 的生成器是独立 codegen 任务（`GenAopProxy`）而非 JSR 269 annotation processor。

### 6.5 NopIoC vs Spring 差异总结（≥4 差异点）

| # | 维度 | Spring（Boot/Context） | NopIoC |
|---|------|------------------------|--------|
| 1 | **Bean 发现** | 注解驱动 classpath 扫描（`@ComponentScan`+`@Component`/`@Service`/`@Repository`），运行期反射 | **完全文件化**：`beans.xml` 经 VFS 解析（`app.beans.xml`/`app-*.beans.xml`），无注解扫描（IOC-002） |
| 2 | **字段注入可见性** | private 可注入（反射 `setAccessible(true)`） | **private 不可靠**：反射层 `ClassModelBuilder` 直接跳过 private 字段/方法（IOC-001，L404） |
| 3 | **AOP 机制** | 运行期 CGLIB/JDK 动态代理字节码生成 | **源码生成式**：build 期 `GenAopProxy` 生成 `__aop.java`+编译，运行期仅注入拦截器数组（`IAopProxy`） |
| 4 | **配置注入** | `@Value("${...}")` 单一 SpEL | `@InjectValue("@cfg:key\|default")` 双实现：IoC 容器 `@cfg:`/`@r-cfg:`(reactive) vs DSL 加载期 `@cfg:`，写法同实现独立（RESOLVE-003） |
| 5 | **模块/条件加载** | `@ConditionalOnProperty` 注解 | `feature:on`/`feature:off` XDSL 属性 + `_module` 标记文件 + `beans.xml` 中 `feature:on` |
| 6 | **收集多 bean** | `@Inject List<T>` / `@Autowired` 集合 | `<ioc:collect-beans by-type/by-annotation>` 声明式标签，按 `ioc:sort-order` 排序 |

## 7. 联网对标与工程权衡

### 7.1 Spring Boot / Spring Context

| | Spring（Boot/Context） | nop-entropy |
|---|---|---|
| **IoC 模型** | `ApplicationContext` + `BeanFactory`；运行期注解扫描（`@ComponentScan`）+ `BeanPostProcessor` 感知注解；反射式字段/setter/构造器注入 | `BeanContainer` + 文件化 `beans.xml`；无注解扫描；反射式注入但 private 不可注入 |
| **核心抽象** | `BeanDefinition`/`BeanWrapper`/`FactoryBean`；SpEL (`@Value`) | `BeanDefinition`+`IAopProxy`；`@cfg:`/`@r-cfg:` ConfigExpressionProcessor |
| **启动模型** | 运行期 classpath 扫描 + 反射建图 + CGLIB 代理生成，启动重、内存占用高 | 加载期 VFS 扫描 + `beans.xml` 解析 + build 期 `__aop` 源码已编译，启动期只注入拦截器数组 |
| **AOP** | 运行期字节码（CGLIB/动态代理） | build 期源码生成（`GenAopProxy`） |

> 来源：Spring Framework Reference, "Classpath Scanning and Managed Components" — https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html（访问 2026-07-24）；"Annotation-based Container Configuration" — https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html（访问 2026-07-24）；仓库既有对比 `docs/compare/nop-vs-springcloud.md`。

### 7.2 Quarkus（Arc 容器）

| | Quarkus | nop-entropy |
|---|---|---|
| **IoC 模型** | **Arc**（CDI-based 容器），**build-time 注入织入**：扩展在构建期参与，消除反射调用，运行期无反射建图 | 文件化 `beans.xml` + 加载期反射注入；运行期仍用反射调 BizModel 方法 |
| **核心抽象** | CDI（`@Inject`/`@Produces`/`@Qualifier`）+ build steps（扩展模型） | XDSL `beans.xml` + `@Inject`/`@InjectValue` + `IAopProxy` |
| **启动模型** | **container-first**：构建时优化 + **GraalVM 原生镜像**，启动毫秒级、低内存，适配 serverless/容器 | 启动需加载期 VFS 扫描+XDSL 合并+反射注册，启动非毫秒级；目标偏单体/平台而非 serverless 冷启动 |
| **AOP** | build-time 拦截器绑定（无运行期代理） | build-time 源码生成 `__aop`（理念相近，实现路径不同） |

**对标要点**：Quarkus Arc 与 nop 都把 DI/AOP 复杂性前移到构建期（Arc 是 build-time 织入，nop 是 codegen 期源码生成），但 nop 的差异化在于 **XDSL Delta**（`beans.xml` 本身可 `x:extends`/`x:override` 叠加定制），Arc 无等价的差量组合代数。

> 来源：Quarkus, "Container First" — https://quarkus.io/container-first/（访问 2026-07-24）；"Building a Native Executable" — https://quarkus.io/guides/building-native-image（访问 2026-07-24）；"Build-Time Brilliance: How Quarkus Achieves Its Lightning-Fast..." — https://www.the-main-thread.com/p/quarkus-build-time-optimizations-performance-guide（访问 2026-07-24）。

### 7.3 Micronaut

| | Micronaut | nop-entropy |
|---|---|---|
| **IoC 模型** | **编译时 DI**（Java APT 注解处理器在编译期生成 `$bean`/注入元数据），运行期无反射，内存占用极低 | 加载期反射注入（运行期仍有反射）；build 期仅生成 `__aop` 代理 |
| **核心抽象** | JSR 330（`@Inject`/`@Singleton`）+ AOT（Micronaut AOT 进一步构建期优化） | XDSL `beans.xml` + 反射 + Delta |
| **启动模型** | 编译时建图 + AOT，启动快、无反射；面向微服务/原生 | 加载期合并 + 反射，启动非极快；面向可逆计算/Delta 定制 |
| **AOP** | 编译期生成 AOP 代码（annotation processor） | codegen 期生成 `__aop`（`GenAopProxy` 独立任务，非 JSR 269 APT） |

**对标要点**：Micronaut 与 nop 都强调"生成而非运行时反射"的 AOP/DI，但 Micronaut 的生成绑定在**编译器 APT**（per-class 元数据），nop 绑定在**独立 codegen 任务 + XDSL Delta**——nop 多出"bean 定义本身可被 Delta 定制叠加"这一维度。

> 来源：Micronaut, "Micronaut Framework code generation" — https://micronaut.io/2023/03/21/micronaut-framework-code-generation/（访问 2026-07-24）；"Micronaut AOT - Build-Time Optimizations" — https://micronaut.io/2021/12/20/micronaut-aot-build-time-optimizations-for-micronaut-applications/（访问 2026-07-24）；Micronaut AOT Guide — https://micronaut-projects.github.io/micronaut-aot/3.0.0/guide/（访问 2026-07-24）。

### 7.4 Helidon

| | Helidon | nop-entropy |
|---|---|---|
| **IoC 模型** | 双形态：**Helidon SE**（轻量、无 IoC 容器、函数式）与 **Helidon MP**（MicroProfile / CDI 容器） | 单一 NopIoC（文件化 `beans.xml`） |
| **核心抽象** | Oracle 云原生微服务库集合；Netty web core（Helidon 4 引入 Java 虚拟线程） | XDSL + VFS + Delta + GraphQL 引擎 |
| **启动模型** | 轻量库式（SE 无容器开销），云原生 ready | 加载期合并 + 反射 |
| **AOP** | MP 形态走 CDI 拦截器；SE 无 AOP | build 期源码生成 `__aop` |

**对标要点**：Helidon 走"库而非框架"的极简路线（尤其 SE 无 IoC 容器），强调虚拟线程/Netty 性能；nop 走"引擎+Delta+GraphQL 自动暴露"的全栈路线。两者目标域不同——Helidon 是微服务 runtime，nop 是可逆计算应用平台。

> 来源：Helidon Project — https://helidon.io/（访问 2026-07-24）；Helidon Introduction — https://helidon.io/docs/v2/about/02_introduction（访问 2026-07-24）；Oracle Technical Brief (Helidon) — https://www.oracle.com/a/ocom/docs/technical-brief--helidon-report.pdf（访问 2026-07-24）；InfoWorld, "Oracle Helidon 4 ... virtual threads" — https://www.infoworld.com/article/2335291/oracle-helidon-4-java-microservices-framework-stresses-virtual-threads.html（访问 2026-07-24）。

### 7.5 工程权衡差异化定位总结

nop-entropy 的核心引擎工程权衡可浓缩为三点，与上述框架形成对比：

1. **复杂性预算放在加载期 + codegen**：AOP（`GenAopProxy`）、BizModel 注册（`ReflectionBizModelBuilder`）、SQL 注入（`EqlTransformVisitor` 租户/逻辑删除）都在加载/编译期完成，运行期面向静态注册表/已编译 SQL。
2. **Bean 定义可被 Delta 定制**：`beans.xml` 本身是 XDSL，支持 `x:extends`/`x:override` 叠加——这是 Spring/Quarkus/Micronaut/Helidon 都不具备的差量组合维度（它们靠 profile/条件注解/扩展机制，而非结构化差量代数）。
3. **GraphQL 作为统一 HTTP 中枢**：所有入口（REST/JSON-RPC/proxy）收敛到 `IGraphQLEngine` → BizModel，而非每框架各有一套 web/REST 栈。

代价：启动期需 VFS 扫描 + XDSL 合并 + 反射注册，**非毫秒级冷启动**（弱于 Quarkus/Micronaut 的 serverless 定位）；运行期 BizModel 调用仍用反射（弱于 Micronaut 的零反射）。

## 8. 开放问题

- [ ] **DQL-001 anchor 精度**：source-anchors.md 把 `owner`/`aggFunc` 列为 `QueryBean` 字段，实际它们是 `QueryFieldBean`/`QueryAggregateFieldBean` 属性。属 anchor 描述精度瑕疵（非平台行为偏差），建议 EXT/DQL 维护时校正——记录为 Non-Blocking Follow-up，不在本 plan 修复。
- [ ] **AOP 锚点缺失**：source-anchors.md 无 AOP 系列 anchor，但 AOP 是 NopIoC 与 Spring 的关键差异。本分析已补全证据链（`GenAopProxy`/`AopCodeGenerator`/`IAopProxy`/`AopBeanProcessor`/`TransactionalMethodInterceptor`）。是否在 source-anchors.md 新增 `AOP-001~005`，由 source-anchors 维护任务评估（属 `docs-for-ai/` 维护，超出本分析 plan scope）。
- [ ] **`@BizAction` 与 AOP 的关系**：`@BizAction` 经 GraphQL/biz 反射层（`ReflectionBizModelBuilder`）暴露/调用，不直接经 AOP 代理；其事务语义通过 bean 的 `__aop` 子类 + `TransactionalMethodInterceptor` 获得。A4 可进一步澄清 `@BizAction` 在服务层约定中的角色。
- [ ] **启动性能量化**：本分析定性指出 nop 启动非毫秒级（弱于 Quarkus/Micronaut），但未做基准测量。是否在 A6（工程化/DX）补充启动性能基准，由 capstone 评估。
- [ ] **反射调用性能**：运行期 BizModel 调用仍用反射（`BeanMethodAction`），弱于 Micronaut 零反射。是否评估对 `ReflectionBizModelBuilder` 引入 codegen 化的方法句柄/直接调用，属后续演进建议（A7 capstone）。

## Conclusion

- 本分析建立了六大引擎模块（nop-core / nop-xlang / nop-xdef / nop-dao / nop-orm / nop-graphql / NopIoC）的职责矩阵、协作关系与端到端运行时调用链（HTTP → GraphQL 引擎 → BizModel → 事务 → ORM → EQL → SQL），并用 **25+ 个 source-anchors 锚点**源码交叉核对（全部 PASS）验证。
- NopIoC 与 Spring 的系统性差异已明确为 **6 个维度**（Bean 发现、字段注入可见性、AOP 机制、配置注入、条件加载、多 bean 收集），其中 **AOP 源码生成式**（`GenAopProxy` 生成 `__aop.java` + `IAopProxy` 拦截器数组注入）是重大发现，填补了 source-anchors 的 AOP 空白。
- 联网对标覆盖 **4 个框架**（Spring Boot/Context、Quarkus Arc、Micronaut、Helidon），每个附 ≥1 来源链接，给出"IoC 模型 / 核心抽象 / 启动模型 / AOP"四维度对照。
- nop 的差异化工程定位：**复杂性预算放在加载期+codegen、Bean 定义可被 Delta 定制、GraphQL 统一 HTTP 中枢**——前两者区别于 Spring/Quarkus/Micronaut/Helidon（它们无结构化差量组合代数）。
- 后续工作：A4（GraphQL/服务层）展开 CRUD 约定与前端渲染；A5（模块矩阵）以本引擎层为参照；是否将 AOP 锚点补入 source-anchors.md、是否迁移本分析到 `docs-for-ai/`，由 A7 capstone 综合评估。

## References

### 平台内部（file 锚点）

- 理论基线：`ai-dev/analysis/2026-07/2026-07-24-nop-theory-foundation.md`（A1 统一词汇表）
- 实现锚点：`docs-for-ai/04-reference/source-anchors.md`（IOC-001~003、EXT-001~004、VFS-001~003、DQL-001/002/006、GQL-001/008、CFG-001、RESOLVE-001~003、MOD-001/002、TNT-001~008、TXN-001、DDD-002~006、RPC-008、AUDIT-002、IOC-003、DB-001/002）
- 核心指南：`docs-for-ai/02-core-guides/ioc-and-config.md`、`docs-for-ai/02-core-guides/api-and-graphql.md`、`docs-for-ai/02-core-guides/model-first-development.md`
- IoC/VFS/合并核心代码：`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/AppBeanContainerLoader.java`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/ConfigExpressionProcessor.java`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/IocConstants.java`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/ConfigValueResolver.java`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/AopBeanProcessor.java`、`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java`、`nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java`
- AOP 代码（锚点补充）：`nop-kernel/nop-core/src/main/java/io/nop/core/reflect/aop/IAopProxy.java`、`AopCodeGenerator.java`、`AopMethodInvocation.java`、`AopAnnotationsLoader.java`、`nop-kernel/nop-codegen/src/main/java/io/nop/codegen/task/GenAopProxy.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/interceptor/TransactionalMethodInterceptor.java`
- 反射/资源/模块代码：`nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/ClassModelBuilder.java`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java`、`DefaultVirtualFileSystem.java`、`DeltaResourceStoreBuilder.java`、`nop-kernel/nop-core/src/main/java/io/nop/core/module/ModuleManager.java`、`ModuleModel.java`
- XDSL/xdef 代码：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslExtender.java`、`DslModelParser.java`、`XDslKeys.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java`
- DAO/ORM/EQL 代码：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmTemplate.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`
- GraphQL/Biz 代码：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/web/GraphQLWebService.java`、`engine/GraphQLEngine.java`、`reflection/ReflectionBizModelBuilder.java`、`reflection/GraphQLBizModels.java`、`GraphQLConstants.java`、`rpc/RpcServiceOnGraphQL.java`、`nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/OrmFetcherBuilder.java`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionInvoker.java`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/BizObjectManager.java`
- 既有对比：`docs/compare/nop-vs-springcloud.md`、`docs/theory/lowcode-ioc.md`、`docs/theory/nop-graphql-design-innovation.md`
- 计划：`ai-dev/plans/nop-deep-analysis/2026-07-24-1907-2-a2-core-engine.md`
- 路线图：`ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md`（Work Item A2）
- 交叉核对记录：`ai-dev/logs/2026/07-24.md`

### 外部（联网调研，访问日期 2026-07-24）

- Spring Framework Reference — Classpath Scanning and Managed Components: https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html
- Spring Framework Reference — Annotation-based Container Configuration: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html
- Spring Framework Reference — Introduction to the IoC Container: https://docs.spring.io/spring-framework/reference/core/beans/introduction.html
- Quarkus — Container First: https://quarkus.io/container-first/
- Quarkus — Building a Native Executable: https://quarkus.io/guides/building-native-image
- The Main Thread — "Build-Time Brilliance: How Quarkus Achieves Its Lightning-Fast..." : https://www.the-main-thread.com/p/quarkus-build-time-optimizations-performance-guide
- Micronaut — Framework code generation: https://micronaut.io/2023/03/21/micronaut-framework-code-generation/
- Micronaut — AOT Build-Time Optimizations: https://micronaut.io/2021/12/20/micronaut-aot-build-time-optimizations-for-micronaut-applications/
- Micronaut AOT Guide: https://micronaut-projects.github.io/micronaut-aot/3.0.0/guide/
- Helidon Project: https://helidon.io/
- Helidon — Introduction: https://helidon.io/docs/v2/about/02_introduction
- Oracle — Helidon Technical Brief (PDF): https://www.oracle.com/a/ocom/docs/technical-brief--helidon-report.pdf
- InfoWorld — "Oracle Helidon 4 ... virtual threads": https://www.infoworld.com/article/2335291/oracle-helidon-4-java-microservices-framework-stresses-virtual-threads.html
