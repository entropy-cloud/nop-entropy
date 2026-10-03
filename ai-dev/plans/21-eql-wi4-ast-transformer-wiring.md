# 21 EQL WI4 — AST 改写管线接线（`IEqlAstTransformer` 固定环节 + 编译缓存键，WI8 硬前置）

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI4 节 + 门控矩阵 WI4 行 + Cross-Cutting，v7.2）；全部代码锚点于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 逐文件实测核验
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置，已 completed）；`ai-dev/plans/03-eql-wi1-class-a-defects.md`（结构先例）；本 plan 是 WI8（NULL 三值语义谓词）的硬前置

## Purpose

收口 roadmap WI4：把 `IEqlAstTransformer` 从"按调用点 opt-in 的可选变量"提升为 session factory 级的**固定编译环节**（消除 per-call 可选性），并修复**编译缓存键不含 transformer** 的过期 SQL 缺陷。本 plan 不实现任何生产 transformer（那是 WI8 的事），但必须让 WI8 拿到三个已证明的契约：① 不传 transformer 的调用方也能拿到 lowering；② 同一 querySpace 下 transformer 语义不一致时绝不返回过期 SQL；③ transform 时机槽位（before/after analyze）有书面裁定。

## Current Baseline

以下事实全部于 2026-10-03 实测核验（逐个开文件比对 file:line；行号漂移登记见「与 roadmap 的差异登记」第 5 条，结论：**零漂移**）。

### 接口与实现者现状

- `IEqlAstTransformer`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/IEqlAstTransformer.java:13-17`）：仅两个方法——`transformBeforeAnalyze`（`:14`）与 `transformAfterAnalyze`（`:16`），均接收 `(SqlProgram, name, sql, ISqlCompileContext)`。
- **零生产实现者**：全仓 `implements IEqlAstTransformer` 唯一命中是测试桩 `nop-persistence/nop-orm/src/test/java/io/nop/orm/eql/TestEqlCompiler.java:76`（`static class TestTransformer`，两个方法均为空体）。
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionOperatorTransformer.java:57` 是 `public class`，**不实现** `IEqlAstTransformer`；它由 `EqlTransformVisitor` 在构造时内部创建（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:181-184`，`new CollectionOperatorTransformer(aliasGenerator)` 在 `:184`），做 collection operator → exists 的树改写，与 `IEqlAstTransformer` 管线**无接续关系**——不得混淆两者。
- `EqlCompiler`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java`）是 `ISqlCompiler` 的**唯一**实现者。transformer 从编译上下文读取：`:56` `IEqlAstTransformer astTransformer = context.getAstTransformer();`（接口方法 `ISqlCompileContext.java:22`），`:58` null 保护 + `:59` `transformBeforeAnalyze(...)`，`:65` null 保护 + `:66` `transformAfterAnalyze(...)`。⚠️ transformer **不是** `EqlCompiler` 的构造参数（roadmap"`:56` 传入"应按"上下文传入"理解）；`EqlCompiler` 只有零参构造。
- 时机语义：`transformBeforeAnalyze`（`EqlCompiler.java:59`）运行在 `EqlTransformVisitor.transform(program)`（`EqlCompiler.java:62-63`）**之前**——实体未解析、SQL 参数未编号（参数编号 `collectNames` 在 `EqlTransformVisitor.java:203-204`）；`transformAfterAnalyze`（`:66`）运行在其**之后**——实体已解析，且 querySpace/dialect 是 analyze **中途**才确定的（`EqlTransformVisitor.java:274`、`:752-761`）。

### 生产调用点与 transformer 来源链（`rg 'new EqlCompiler'` + `rg '\.compileSql\('` 全量实测）

- **`new EqlCompiler()` 的生产构造点仅 2 处**：`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:102`（缓存未命中路径）与 `:121`（非缓存路径）。⇒ **EQL 编译的唯一生产入口是 `LoadedOrmModel.compileSql`**。其余命中（执行期 2026-10-04 实测 30 处，WI1-WI3 方言门测试新增所致；起草值"其余 17 处"、审查修订值"15 处"均已过时）全部在 `src/test`（nop-orm-eql 29 处 + nop-duckdb 1 处）。计数演进与结构性结论见裁定文档 §六.1。
- 上游 **5 类生产入口**，全部最终汇入 `LoadedOrmModel.compileSql`（经 `SessionFactoryImpl.compileSql` 7-arg 委托 `:344-349`）：
  1. **7-arg**（`SessionFactoryImpl.java:344-349`）← `nop-persistence/nop-orm/src/main/java/io/nop/orm/filter/FilterSqlHelper.java:29-30`：**显式传 null transformer、useCache=true**（上游为数据权限过滤器 `DataAuthEntityFilterProvider.java:36`）。
  2. **3-arg**（`SessionFactoryImpl.java:333-335`，**已显式传 `defaultAstTransformer`**，`:334`）← `SqlLibManager.java:259`（sql-lib 启动校验 `checkLibValid`，仅 `<eql>` 类型语句）。
  3. **5-arg**（`SessionFactoryImpl.java:338-341`，**已显式传 `defaultAstTransformer`**，`:340`）← `JdbcQueryExecutor.java:56 / :75 / :100`（session 查询/更新/语句执行的**主路径**）。
  4. `ILoadedOrmModel` 的两个 default 方法（`nop-persistence/nop-orm/src/main/java/io/nop/orm/ILoadedOrmModel.java:76-78`、`:80-83`，传 null → `LoadedOrmModel.java:92-93` 回落）。
  5. `IPersistEnv` 的同名编译重载（`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:64`、`:66-67`）。
- `nop-stream/nop-stream-flow/.../StreamModelDslBuilder.java:285` 的 `compileSql` 是 `ISqlStreamCompiler`（stream 领域），与 EQL 无关（已核实调用上下文）。
- `nop-metadata-service` / `nop-orm-drivers/nop-orm-tdengine` / `nop-db-migration` 均**不构造** `EqlCompiler`、**不调用** `compileSql`（tdengine 仅静态使用 `EqlHelper.appendCol`）。

### env 级默认钩子（已存在，非本 plan 新建）

- 回落点：`LoadedOrmModel.java:92-93`——`astTransformer == null` 时回落 `env.getDefaultAstTransformer()`。解析发生在缓存查找（`:98`）**之前**。
- 接口声明：`IPersistEnv.java:48`；唯一实现 `SessionFactoryImpl`（`:64 implements IPersistEnv`；字段 `:100`、setter `:127`、getter `:131`）。
- 配置注入链：`OrmSessionFactoryBean.java:88` `impl.setDefaultAstTransformer(getEqlAstTransformer())` ← `SessionFactoryConfig.java:81` 字段 / `:95` getter / `:99` setter（`OrmSessionFactoryBean extends SessionFactoryConfig`，`OrmSessionFactoryBean.java:40`）。
- ⚠️ **有管线、无水源**：全仓**零** `setEqlAstTransformer` 调用方、零 beans/XML 配置 ⇒ 生产 `defaultAstTransformer` 恒为 null。叠加零生产实现者 ⇒ WI4 落地后、WI8 配置真实 transformer 之前，固定环节处于"已接线、未配置"的合法态（本 plan 用测试桩证明契约，不产生生产行为变化）。
- 该接线**非本分支引入**：`git log master..HEAD -- <接线链 9 文件>` 中仅 `EqlTransformVisitor.java` 被 WI2 触及（BUG-6 fullJoin 门，与 transformer 管线无接续关系），其余 8 文件（`OrmSessionFactoryBean`/`SessionFactoryConfig`/`SessionFactoryImpl`/`ILoadedOrmModel`/`LoadedOrmModel`/`IPersistEnv`/`IEqlAstTransformer`/`EqlCompiler`）零提交 ⇒ master 侧继承（精确化于裁定文档 §六.2；分支现含 81 个提交，原"仅 roadmap/WI0 文档与测试提交"表述过时）；`ILoadedOrmModel.java:67-71` 的缓存键警告 javadoc 来自 master 侧 check2 整改提交 `0084822b0b`——即缓存键风险此前被登记为 **javadoc 层的"调用方责任"**，本 plan 将其升级为**代码强制**。

### 缓存键链路（缺陷本体）

- 键构造：`LoadedOrmModel.java:96-97` `new QueryPlanCacheKey(name, sqlText, disableLogicalDelete, allowUnderscoreName, enableFilter)`——键类 `nop-persistence/nop-orm/src/main/java/io/nop/orm/QueryPlanCacheKey.java:16-36` 恰这五个字段，`hashCode`（`:59-66`）与 `equals`（`:68-88`）均**不含 transformer**。
- 查/写点：`LoadedOrmModel.java:98` `env.getQueryPlanCache().get(key)`、`:115` `put(key, result)`（全仓唯一读/写点）；缓存实例 per-session-factory（`OrmSessionFactoryBean.java:95-96` `"orm-query-plan-cache"`）；`SessionFactoryImpl.java:167` getter、`:303` `clearQueryPlanCache()`。
- 过期 SQL 的成立链：显式传**非默认** transformer + `useCache=true` 的编译会把"transformer 改写后的 SQL"存进不含 transformer 的键下 ⇒ 另一 transformer（或无 transformer）的同键查询直接命中过期计划。`ILoadedOrmModel.java:69` 警告原文："astTransformer不参与缓存键，调用方必须保证同一session factory内使用语义一致的transformer"。
- 生产暴露面：今天**不存在**显式传非 null transformer 的生产调用点（`FilterSqlHelper.java:29` 传 null）⇒ 缺陷尚未实际触发；但 WI8 一旦配置默认 transformer，任何 per-call 覆写都会立即激活它。
- 键维度先例：`enableFilter` 参与键（`QueryPlanCacheKey.java:23-25` 注释 + `:62-64`/`:84-85` 实现；测试 `nop-persistence/nop-orm/src/test/java/io/nop/orm/TestQueryPlanCacheKey.java:22-37`）——"编译上下文影响生成结果则必须参与键"的既有判例。
- 隐含先例：`TestEqlCompiler.java:63-65` 显式传 transformer 时用 `useCache=false`——"自定义 transformer 与缓存不混用"已是测试层的既成事实。

### 测试基建与模块边界

- `nop-persistence/nop-orm/src/test/java/io/nop/orm/AbstractOrmTestCase.java:48-55` 程序化构造 `OrmSessionFactoryBean` 并在 `:55` 调 `init()` ⇒ 测试可在 `init()` 之前调 `setEqlAstTransformer(...)`，走**与生产完全相同的配置路径**注入测试 transformer。
- 语料 BC 基线：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md`（WI0 Phase 3 产物，测试 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java`）。
- 模块依赖方向：`nop-orm` → `nop-orm-eql`（`nop-persistence/nop-orm-eql/pom.xml` 不依赖 nop-orm，仅 `nop-dao` `:31`；`nop-persistence/nop-orm/pom.xml:24` 依赖 nop-orm-eql）⇒ **env 回落只能在 nop-orm 侧实现**，`EqlCompiler`（nop-orm-eql）无法自行回落 env 默认——这是"签名透传"问题的结构性根源，也是"env 钩子是唯一正道"的依据。

## Goals

- Phase 0 裁定并落档四件事：签名改动范围（倾向结论 = **零签名改动**，不触发 roadmap WI4 行的 plan-first）；transform 时机槽位（倾向结论 = `transformBeforeAnalyze` 为固定 lowering 槽位）；缓存键方案（**推荐候选 B = 非默认 transformer 绕过缓存**）；旧 7-arg 参数"保留 vs 弃用"（倾向结论 = 保留 + 收窄使用边界）。
- 固定环节成立：全部 5 类生产入口在**不传 transformer** 时解析到同一 env 默认（`SessionFactoryConfig.eqlAstTransformer` → `SessionFactoryImpl.defaultAstTransformer`），由证明测试 1 从生产配置路径到生成 SQL 端到端验证（Minimum Rules #22）+ 运行时调用断言（#23）。
- 缓存键缺陷修复：同一 querySpace / 同键下 transformer 语义不一致时绝不返回过期 SQL（证明测试 2，修复前红、修复后绿）。
- 契约落文档：`ILoadedOrmModel.java:67-71` javadoc 按新契约更新；owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`，全仓唯一同时覆盖 EQL 编译管线与方言兼容性的 owner doc，`docs-for-ai` 中 `IEqlAstTransformer`/`eqlAstTransformer` 现为零提及）写入迁移说明与固定环节契约。
- 回归守护：未配置 transformer 的默认路径行为逐字不变（三份必测语料与 WI0 基线快照一致）。

## Non-Goals

- 不实现任何生产 `IEqlAstTransformer`（WI8 是该管线的第一个生产消费者；本 plan 只用测试桩证明管线契约）。
- 不改任何 `.g4` / `EqlAST.xjava`，不触生成管线（`EqlParser.java` 等重生成与本 plan 无关）。
- 不动 `CollectionOperatorTransformer`（`EqlTransformVisitor` 内部的 collection-operator 改写，与 `IEqlAstTransformer` 管线无关）。
- 不做候选 A（transformer 语义指纹纳入 `QueryPlanCacheKey`）——Phase 0 记录为被拒备选；见 `Deferred But Adjudicated`。
- 不重构 `TenantCachedQueryPlan` / 租户计划组织（本 plan 只改键策略与解析点，不改缓存计划结构）。
- 不把 `:nop-orm-tdengine` / `:nop-db-migration` / `:nop-orm-demo` 塞进 `-pl`（roadmap 级 EC 第 8 条覆盖）。
- 不修改 `ISqlCompileContext`（nop-orm-eql 侧接口，`getAstTransformer()` 语义不变）。

## Scope

### In Scope

- `nop-persistence/nop-orm`：`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java`（transformer 解析点 + 缓存策略）、`nop-persistence/nop-orm/src/main/java/io/nop/orm/ILoadedOrmModel.java`（javadoc 契约）、（仅当 Phase 0 裁定）`nop-persistence/nop-orm/src/main/java/io/nop/orm/compile/EqlCompileContext.java`（空值回落不变式——签名不变）、（仅当裁定候选 A 时才动）`nop-persistence/nop-orm/src/main/java/io/nop/orm/QueryPlanCacheKey.java`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryConfig.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSessionFactory.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/ISqlCompileTool.java` —— **预期零改动**（Phase 0 裁定记录）。
- `nop-persistence/nop-orm-eql`：**预期零改动**（`EqlCompiler.java:56-67` 已无条件调用两个钩子；若 Phase 0 裁定需要 EqlCompiler 侧强化，则纳入本范围）。
- 测试：`nop-persistence/nop-orm/src/test/java/io/nop/orm/` 下新增证明测试（参照 `TestEqlCompiler.java` 测试桩先例与 `TestQueryPlanCacheKey.java` 键维度先例）。
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（AST 改写管线小节 + 迁移说明）；`ai-dev/design/eql-cross-db-compat/`（Phase 0 裁定文档）；`ai-dev/logs/`。

### Out Of Scope

- Non-Goals 所列全部；执行中发现的新缺陷一律登记 roadmap/新 plan，不顺手修。

## 与 roadmap 的差异登记（起草时实测发现）

1. **"`EqlCompiler.java:56` 传入"的准确机制**：transformer 来自 `ISqlCompileContext.getAstTransformer()`（编译上下文），**不是** `EqlCompiler` 的构造参数或字段；`EqlCompiler` 只有零参构造。roadmap 锚点行号 `:56/:58/:59/:65/:66` 全部命中、零漂移，仅"传入"一词需按上下文语义理解。
2. **roadmap 未登记 `SessionFactoryImpl.java:334`/`:340`**：两个便捷重载（3-arg/5-arg）**已显式注入 `defaultAstTransformer`**——即 `JdbcQueryExecutor`（session 执行主路径）与 `SqlLibManager`（启动校验）这类"不传 transformer 的调用方"在**工厂已配置**的前提下今天就能拿到 env 默认。"env 钩子已覆盖"的证据面因此比 roadmap WI4 节所列（`:100/:127/:131` 字段三件套 + `LoadedOrmModel` 回落）更宽，进一步支持零签名裁定。
3. **roadmap 未登记"有管线无水源"**：全仓零 `setEqlAstTransformer` 配置源 ⇒ env 钩子当前从未被喂过 transformer；这也意味着 WI4 的改动在"未配置"状态下**零生产行为变化**（BC 风险近似为零），固定环节的证明只能靠测试桩。
4. **roadmap 未登记警告的出处与性质**：`ILoadedOrmModel.java:67-71` 的缓存键警告来自 master 侧 check2 整改（`0084822b0b`），性质是"把风险转嫁给调用方"的 javadoc；本 plan 将其升级为代码强制后，该 javadoc 必须同步改写（Phase 1 项）。
5. **行号漂移登记：无**。roadmap WI4 节全部锚点（`EqlCompiler.java:56/:58/:65`、`TestEqlCompiler.java:76`、`CollectionOperatorTransformer.java:57`、`LoadedOrmModel.java:90/:92-93/:96-97`、`ILoadedOrmModel.java:69`、`IPersistEnv.java:48`、`OrmSessionFactoryBean.java:88`、`SessionFactoryConfig.java:81/:95/:99`、`SessionFactoryImpl.java:100/:127/:131`、`IOrmSessionFactory.java:69`、`ISqlCompileTool.java:17`、`EqlCompileContext.java:24/:31`）逐个开文件核验命中。

## Protected Areas 与准入声明（Phase 0 裁定载体）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `IOrmSessionFactory.java:68-70` / `ISqlCompileTool.java:16-18` / `SessionFactoryConfig.java:81,95,99` / `IPersistEnv.java:48` / `LoadedOrmModel.java:89-91` / `EqlCompileContext.java:24,30-32` 等**签名** | roadmap 门控矩阵 WI4 行的 plan-first **触发条件**（"签名透传"） | Phase 0 裁定：**倾向 = 全部不动 ⇒ 不触发 plan-first**（依据：5 类生产入口已被 env 钩子 + 两处便捷重载覆盖，见 Current Baseline）。⚠️ 若执行中发现必须改签名：**先回本 plan 增补 Protected Area 声明并完成 draft review，再动代码** | 裁定文档（`ai-dev/design/eql-cross-db-compat/`）逐文件记录"不动"判定 |
| `IEqlAstTransformer` 接口（nop-orm-eql 定义、nop-orm 消费） | 跨模块消费接口 | 候选 B **不需要**改接口；若裁定候选 A 需加指纹方法 ⇒ 视同跨模块 API 变更，先 draft review | 同上 |
| `EqlCompiler.java` / `EqlTransformVisitor.java`（nop-orm-eql 内部） | 普通手写代码（非生成链） | 无额外门禁 | 回归测试 |
| 生成管线（`.g4`/`EqlAST.xjava`/`ast/_gen/`） | **本 plan 不触碰** | — | — |

⚠️ 本 plan 不触碰 `model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api` 模块。

## Execution Plan

Phase 0 → Phase 1 → Phase 2 顺序执行；Phase 1 的代码改动单独提交。

### Phase 0 - 四项裁定（签名范围 / transform 时机 / 缓存键方案 / 旧参数去留）

Status: completed（2026-10-04，裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi4-phase0-adjudications.md`）
Targets: `ai-dev/design/eql-cross-db-compat/`（新增裁定文档）

- Item Types: `Decision`、`Proof`

- [x] **裁定 1（签名范围）**：逐一复核 Current Baseline 列出的 6 组签名点在"接线收窄后"是否必须改动；默认裁定 = **零签名改动**。记录每组"不动"的依据（env 钩子 + `SessionFactoryImpl.java:334/:340` 直通 + `LoadedOrmModel.java:92-93` 回落已覆盖全部 5 类生产入口），并写明若必须改签名时的 plan-first 增补流程。（裁定文档 §一：6 组签名点逐文件"不动"判定表 + 升级流程；执行期锚点复核零漂移）
- [x] **裁定 2（transform 时机）**：给出 `transformBeforeAnalyze` vs `transformAfterAnalyze` 的结论与理由。倾向结论 = **`transformBeforeAnalyze` 为固定 lowering 槽位**。理由要素（执行时复核后落档）：(a) before 槽位的改写产物会被完整分析管线处理——参数编号（`EqlTransformVisitor.java:204` `collectNames`）、实体解析（`:622/:690/:1049/:1137/:1528`）、隐式 join 展开、`CollectionOperatorTransformer`、`SqlParamTypeResolver`（`:206-208`）；(b) after 槽位注入的新节点全部绕过上述管线（`SqlParameterMarker` 不编号 → `buildParams` 错位；属性导航不展开 → 生成器直印未解析名），只对"不引入参数/实体引用的纯语法改写"安全；(c) before 槽位拿不到 querySpace（`EqlTransformVisitor.java:274/:752-761` 在 analyze 中途才确定）⇒ **需要方言判定的 lowering（WI8 的 `IS DISTINCT FROM` 按方言矩阵仅 Oracle 需展开）的方言信息来源必须在本裁定中一并给出**（候选：经 `context.getDialectForQuerySpace(<默认 querySpace>)`；或该具体改写放 after 槽位并满足 (b) 的约束），不得留空给 WI8。（裁定文档 §二：before=固定 lowering 槽位、after=仅参数保持型改写；2c 三级可信度排序——after 槽位+已解析 AST 推导（推荐）/ before+AST 自行推导（中）/ before+默认 querySpace 猜测（低可信，拒绝）；`IS DISTINCT FROM` 展开的参数保持性论证 WI8 可直接引用）
- [x] **裁定 3（缓存键方案，两候选给推荐 + 理由）**：
  - **候选 A**：把 transformer（或其语义指纹）纳入 `QueryPlanCacheKey`（照 `enableFilter` 先例 `QueryPlanCacheKey.java:23-25` + `TestQueryPlanCacheKey.java:22-37`）。成本：指纹来源要么给 `IEqlAstTransformer` 加方法（跨模块消费接口变更），要么用 className+identity（脆弱、跨进程不可比）；收益：非默认 transformer 也可安全使用缓存。
  - **候选 B（推荐）**：**非默认 transformer 强制绕过缓存**——`LoadedOrmModel.compileSql` 在 `:92-93` 解析后，比较 resolved transformer 与 `env.getDefaultAstTransformer()`，不同实例则 `useCache` 按 false 处理。收益：零签名、零接口变更；缓存中只存在"默认 transformer 编译的计划"，而默认 transformer 是 **per-session-factory 常量**（`init()` 时注入一次，`OrmSessionFactoryBean.java:88`）⇒ 键不含 transformer 即无歧义；与既有先例一致（`TestEqlCompiler.java:63-65` 显式 transformer 时 `useCache=false`）。代价：显式非默认 transformer 的编译不缓存（生产唯一 7-arg 调用点 `FilterSqlHelper.java:29` 传 null，无实际影响）。
  - 记录被拒候选与理由，并做 **No-Silent-NoOp 裁定说明**（Minimum Rules #24）：候选 B 的"绕过缓存"是显式策略——绕过后仍完整编译并返回正确结果，不存在被跳过的能力分支。（裁定文档 §三：候选 B + 三个显式假设（实例身份比较语义 / `setDefaultAstTransformer` public setter 的 init 期常量守护 / transformer 确定性）；候选 A 被拒理由 + No-Silent-NoOp 说明）
- [x] **裁定 4（旧参数去留）**：7-arg `compileSql` 的 `astTransformer` 参数结论 = **保留**（理由：测试/工具 opt-in 路径在用——`TestEqlCompiler.java:63-65`；删除即触发签名 plan-first；"固定性"由解析点唯一化 + 缓存策略保证而非删参），使用边界写进 owner doc："生产代码不应 per-call 传 transformer，统一走 factory 默认配置；显式传入视为测试/工具行为且不参与缓存"。（裁定文档 §四，使用边界 Phase 1 落 owner doc）
- [x] 复核 WI0 基线未回退：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 与 `TestEqlCorpusBaseline` 存在且可跑——Phase 1 的 BC 判据依赖。（裁定文档 §五）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含四项裁定、被拒候选与理由、WI8 面向的方言信息来源约束（裁定 2c）。（`2026-10-04-wi4-phase0-adjudications.md`；plan 审查 6 项 Minor 一并吸收，见裁定文档 §六）
- [x] 裁定 1 明确记录"零签名改动 ⇒ 不触发 roadmap WI4 行 plan-first"，或触发时的增补流程已写明。
- [x] 本 Phase 纯裁定不改产品代码：`No owner-doc update required`（裁定文档本身即交付物）。
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/10-04.md` WI4 Phase 0 条目）

### Phase 1 - 接线落地与缓存键修复（两个证明测试）

Status: completed（2026-10-04；commit 见 daily log）
Targets: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/ILoadedOrmModel.java`（javadoc）、（按裁定）`nop-persistence/nop-orm/src/main/java/io/nop/orm/compile/EqlCompileContext.java`、`nop-persistence/nop-orm/src/test/java/io/nop/orm/`（新增测试）、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Fix`（缓存键缺陷：同键不同 transformer 返回过期 SQL）、`Feature`（固定环节契约）、`Proof`（两个证明测试）

- [x] **修复前红**：先落证明测试 2（缓存过期 SQL），在未修复代码上运行确认红（第二个 transformer 编译返回第一个 transformer 的 SQL）并留痕于日志/提交说明。（`TestEqlAstTransformerWiring.testNonDefaultTransformerBypassesQueryPlanCache` 修复前 FAILURE："B compile must contain B marker, got: select o.CLASS_ID as cid_wi4_a…"；留痕 `_tmp/wi4/red-evidence-TestEqlAstTransformerWiring.txt` + daily log）
- [x] 缓存键修复落地（按 Phase 0 裁定；默认 = 候选 B）于 `LoadedOrmModel.compileSql`。（回落解析后 `useCache && astTransformer != env.getDefaultAstTransformer()` ⇒ useCache=false，不读不写；零签名改动）
- [x] 固定环节落地（按 Phase 0 裁定）：transformer 解析点唯一化 +（如裁定）`EqlCompileContext` 空值回落不变式（`env` 字段已在 `EqlCompileContext.java:20`，签名不变）；确保 5 类生产入口在"不传 transformer"时都解析到 env 默认。（解析点唯一化结构性既存（`:92-93` 回落为全仓唯一解析点），本 Phase 以证明测试 1 三入口（3-arg/5-arg/ILoadedOrmModel default）+ 第 4 入口同构论证（FilterSqlHelper 7-arg 显式 null→同一回落）证明；`EqlCompileContext` 不变式按裁定 1 不加，登记 Non-Blocking Follow-ups）
- [x] **证明测试 1（端到端 #22 + 接线 #23）**：经生产配置路径注入测试 transformer（`OrmSessionFactoryBean.setEqlAstTransformer`，在 `init()` 前调用，参照 `AbstractOrmTestCase.java:48-55` 的构造顺序），随后经**不带 transformer 参数**的入口（3-arg `sessionFactory.compileSql` 与/或 5-arg `env.compileSql`——即 `JdbcQueryExecutor` 的实际路径）编译一条 EQL，断言 lowering 标记出现在生成 SQL 文本中（测试桩做确定性可断言的 AST 改写，如追加一个可识别投影别名，参照 `TestEqlCompiler.java:76` 桩先例）；并以计数器/标志断言 transformer 方法确实被运行时调用（#23 的 mock-verify 形态，不允许只断言"类型存在"）。（`testDefaultTransformerFromFactoryConfigEndToEnd`：自建 OrmSessionFactoryBean（裁定文档 §六.5——基类 setUp 自调 init 无法 init 前注入 + DefaultOrmModelProvider 固定名全局缓存注册限制）；三入口断言 `cid_wi4_default` 标记 + 双钩子计数 3/3；末段经 OrmTemplate session 查询真实执行 lowering SQL 并断言行数与计数 4/4；标记实现=改写已解析投影 alias（新增未解析节点在 after 槽位会被生成器 resolvedExprMeta 解引用拒绝，故非"追加投影"））
- [x] **证明测试 2（缓存）**：同一 querySpace、同 `name+sqlText+flags`：transformer A 编译（`useCache=true`）断言结果含 A 标记；transformer B 编译（`useCache=true`）断言结果含 B 标记、**不含** A 标记；无 transformer 的默认路径编译断言不含任何标记。参照 `TestQueryPlanCacheKey.java` 键维度测试先例。（`testNonDefaultTransformerBypassesQueryPlanCache` 全部三段断言 + B 计数=1 证明新鲜编译）
- [x] javadoc/注释同步：`ILoadedOrmModel.java:67-71` 警告改写为新契约（"缓存中只存在默认 transformer 编译的计划；非默认 transformer 的编译绕过缓存"），删除"调用方必须保证"的转嫁措辞；`QueryPlanCacheKey.java` 注释如需同步。（javadoc 已改写；`QueryPlanCacheKey` 类本体零改动、既有 enableFilter 注释仍准确——无需同步）
- [x] 无静默跳过自查：新增分支不吞错、不返回占位值；"绕过缓存"是显式裁定策略（Phase 0 记录）而非静默降级。（bypass 分支仅改写 useCache 局部值、后续仍完整编译返回；测试桩找不到改写目标时抛 IllegalStateException）
- [x] owner doc：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 新增"AST 改写管线（IEqlAstTransformer）"小节——固定环节契约、配置方式（`SessionFactoryConfig.eqlAstTransformer`）、旧参数保留结论与使用边界（裁定 4）、缓存策略（裁定 3）、时机槽位结论（裁定 2）。（小节含四块 + init 期常量契约 + WI8 消费指引；doc-links 0 error / 3 既有 warning）
- [x] 既有 EQL 语料 BC：三份必测（`:nop-job-dao` NopJobTask / `:nop-auth-service` test / `:nop-orm` test）与 WI0 基线快照逐字一致（未配置 transformer 的默认路径行为不变）；不声称"7 个全测"。（`TestEqlCorpusBaseline` 1/1（棘轮覆盖三份必测语料区间）；三份 sql-lib 文件本 Phase 零改动）

Exit Criteria:

- [x] **证明测试 1 存在且绿**：不传 transformer 的调用方拿到 lowering（断言具体 SQL 文本）+ transformer 方法运行时被调用的显式断言（端到端 #22 与接线 #23 同时满足）。
- [x] **证明测试 2 修复前红、修复后绿**：同一 querySpace 下 transformer 语义不一致时不返回过期 SQL（红证据留痕于日志或提交说明）。
- [x] 默认路径零行为变化：三份必测语料 BC 与 WI0 基线快照逐字一致。（`TestEqlCorpusBaseline` 1/1；nop-dao 205|46skip / nop-orm-eql 253|0 / nop-orm 212|6skip 全零失败，计数以各模块 target/surefire-reports 汇总为准）
- [x] `ILoadedOrmModel` javadoc 与实现一致（旧"调用方必须保证语义一致"的转嫁措辞已按新契约更新）。
- [x] owner doc"AST 改写管线"小节已落档（含迁移说明与四项裁定结论）；`node ai-dev/tools/check-doc-links.mjs --strict` 本 plan 新增 broken link = 0。（exit 0，0 error）
- [x] `./mvnw test -pl :nop-orm-eql,:nop-orm -am -T 1C` 全绿（快速反馈口径）。（exit 0；期间暴露并修复 2 个 WI3 遗留过期测试，见偏差登记）
- [x] `ai-dev/logs/` 对应日期条目已更新。（daily log WI4 Phase 1 条目）

**Phase 1 执行期偏差登记**：`TestEqlCompiler`（nop-orm）2 用例（`testUpdateReturning`/`testFunctions`）在 WI4 全量口径下红——stash 本 Phase 全部改动 + reactor 依赖（`-am`，规避 .m2 过期 nop-dao 假绿）复跑证实为 **WI3 既有遗留**（h2 `supportReturningForUpdate=false` 纠错与 `ilike` 模板注册均 WI3 授权行为变更；WI3 的 `-pl :nop-dao,:nop-orm-eql` 闭包未覆盖 nop-orm 测试模块故未暴露），非 WI4 引入。按授权行为更新两测试（returning 断言编译期 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`；functions 迭代显式跳过非调用语法的 ilike 模板并注释缘由），随本 Phase 提交。

### Phase 2 - 全量回归与收口门禁

Status: completed（2026-10-04；执行者产品面收口，独立 closure audit 待启动）
Targets: 全量验证命令 + 门禁脚本 + roadmap 状态回写准备

- Item Types: `Proof`

- [x] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（roadmap WI4 EC 原文口径）。**"全绿"口径**：本 WI 相关模块全绿 + `nop-stream-runtime` 既有失败 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication` 1 项与基线一致即豁免（WI0 实测记录：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 文档头；debezium-cdc capability 期望 SINGLE_INSTANCE 实为 PARALLEL，与本 roadmap 零代码关联；该模块仅因 `:nop-auth-service` 的 `-am` 闭包进入）。（三段式实测：①`-T 1C` 首跑 reactor 在 `nop-batch-core` `TestBatchTaskCheckpoint.testFailFastStopsSiblingThreads` 中断——并行负载 flake，串行复跑 8/8 绿、与本 WI 零关联，经 `-am` 闭包进入；②排除该 flake 补跑（`-Dtest='!TestBatchTaskCheckpoint'`）：五目标模块测试绿，但 nop-auth-service 被豁免项 stream 失败的 fail-fast 级联取消（`--fail-at-end` 下 metadata/job-dao 完成、auth-service fork 仍未产出报告）；③按 WI2 补跑先例 `install -DskipTests` + module-only 补跑 nop-auth-service：461|4skip 全绿 exit 0。终态计数（各模块 target/surefire-reports 汇总）：nop-dao 205|46skip、nop-orm-eql 253|0、nop-orm 212|6skip、nop-metadata-service 1340|0、nop-job-dao 87|0、nop-auth-service 461|4skip、nop-stream-runtime 1238|1（豁免项，症状 `debezium-cdc ==> expected: <SINGLE_INSTANCE> but was: <PARALLEL>` 与 WI0 基线逐字一致））
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 与 `--module nop-orm-eql --severity high` 均零命中。（双 exit 0）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本 plan 新增 broken link = 0（既有 3 项基线不动：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`；不顺手修无关文件）。（exit 0：0 error、3 既有 warning；执行中发现 docs-for-ai 禁引用 ai-dev/ 的 boundary 规则并即时修正 owner doc 引用形态）
- [x] `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md` 裁定：若 owner doc 新增小节构成新路由锚点则同步；否则明确写 `No update required` 并记录理由。（INDEX.md :80 路由行关键词补「AST 改写管线 `IEqlAstTransformer`」——文件路径未变、仅描述增词；source-anchors **No update required**：该表无 EQL 规则 ID 命名空间、无本文件既有引用，新小节的实现锚点（LoadedOrmModel.compileSql/ILoadedOrmModel javadoc/TestEqlAstTransformerWiring）已内联于 owner doc 小节，沿 WI3 同型裁定先例）
- [x] WI8 前置条件确认：固定环节 + 缓存安全 + 时机槽位三项契约对 WI8 可用；缺口（如有）登记 successor，不允许名义 owner。（①固定环节：证明测试 1 三入口 lowering + 生产配置链；②缓存安全：证明测试 2 bypass + `ILoadedOrmModel` javadoc 新契约；③时机槽位：裁定文档 §二 槽位分工 + 2c 方言来源（after 槽位 + 已解析表源推导）+ `IS DISTINCT FROM` 参数保持性论证。无缺口，无需 successor）

Exit Criteria:

- [x] 上述命令与门禁全部通过且结果记录于 `ai-dev/logs/` 对应日期条目。
- [x] roadmap Work Item Status 的 WI4 行具备翻 `done` 的前提（独立 closure audit 通过后才翻，不得提前）。（产品面全部落地、四门禁通过、WI8 三契约可用；roadmap WI4 行按留白纪律未翻，待独立 audit）
- [x] `ai-dev/logs/` 对应日期条目已更新。（daily log WI4 Phase 2 终局条目）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 When Closing The Plan 与 Closure Audit Rule。

- [x] Phase 0 四项裁定（签名范围 / transform 时机 / 缓存键方案 / 旧参数去留）全部落档 `ai-dev/design/eql-cross-db-compat/`，含被拒候选与理由。（`2026-10-04-wi4-phase0-adjudications.md`，含 6 项审查 Minor 吸收记录 §六）
- [x] 缓存键缺陷（同键不同 transformer 返回过期 SQL）已修复：证明测试 2 修复前红、修复后绿，断言具体 SQL 文本而非"没抛异常"。（红证据 `_tmp/wi4/red-evidence-TestEqlAstTransformerWiring.txt`："got: select o.CLASS_ID as cid_wi4_a"；绿：`testNonDefaultTransformerBypassesQueryPlanCache` 三段断言全过）
- [x] 固定环节契约成立：证明测试 1 通过（生产配置路径 → 不带 transformer 参数的入口 → 生成 SQL 含 lowering + 运行时调用断言）。（`testDefaultTransformerFromFactoryConfigEndToEnd`：三入口 + 双钩子计数 + session 查询真实执行）
- [x] 零签名改动裁定成立（若执行中改为改签名，则 plan-first draft review 已完成并以 Closure audit 实核结论为准）。（全部改动 = `LoadedOrmModel.compileSql` 方法体 + `ILoadedOrmModel` javadoc + 测试；接口/方法签名零改动）
- [x] owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）迁移说明与 `ILoadedOrmModel.java` javadoc 已同步 live baseline。
- [x] 三份必测语料 BC（`:nop-job-dao` / `:nop-auth-service` / `:nop-orm`）与 WI0 基线快照逐字一致；未声称"7 个全测"。（`TestEqlCorpusBaseline` 1/1 覆盖三份必测语料棘轮区间；本 WI 零 sql-lib/XML 资源改动）
- [x] 无被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（候选 A 按 Phase 0 记录为被拒备选并登记于 `Deferred But Adjudicated`，属 non-defect 的范围裁定）。
- [x] `./mvnw test -pl :nop-orm-eql,:nop-orm,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（stream 既有失败按 Phase 2 口径豁免）。（三段式：-T 1C 首跑 break 于 nop-batch 并行 flake（串行 8/8）→ 排除补跑五目标绿但 auth-service 被级联取消 → auth-service install+module-only 补跑 461|4skip 绿；五目标模块终态全零失败，计数以各模块 surefire-reports 汇总）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md --strict` 退出码 0。（留白待独立 audit；执行者已实测 active 状态下 exit 0——completed 后才强制全勾+evidence，终勾随 Plan Status completed 一并复核）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 与 `--module nop-orm-eql --severity high` 零命中。（双 exit 0，2026-10-04 实测）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本 plan 新增 broken link = 0。（exit 0；执行中新增 1 处 docs-forai→ai-dev boundary 违规已即时修正）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）transformer 从 `SessionFactoryConfig` 配置到 `EqlCompiler` 钩子调用的运行时链确实连通（计数器/标志证据，不只是类型系统），（b）解析回落 / 缓存 bypass 路径无空方法体、吞异常、no-op 当作正常实现，（c）缓存键链路（`LoadedOrmModel.java:92-93` 解析 → `:96-97` 构键 → `:98` 查 → `:115` 写）与新策略逐点一致。（留白待独立 audit；执行者自查证据：证明测试 1 计数器断言 + bypass 分支完整编译返回 + hollow 扫描双 exit 0）
- [ ] 独立 closure audit 已完成并由**不同 task_id** 的子 agent 执行，evidence 写入本 plan `Closure` 段（guide #26/#27）；roadmap Work Item Status 的 WI4 行据此才可 `todo` →（执行中）→ `done`。（留白待独立 audit；roadmap WI4 行未翻）

## Deferred But Adjudicated

### 候选 A：transformer 语义指纹纳入 `QueryPlanCacheKey`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 生产零显式非 null transformer 调用点（唯一 7-arg 生产调用 `FilterSqlHelper.java:29` 传 null）；候选 B 已结构性消除过期 SQL（缓存中只存在默认 transformer 的计划，而默认 transformer 是 per-factory 常量），指纹方案无当期收益、且需要跨模块接口变更。
- Successor Required: `no`
- Successor Path: 触发条件已登记——出现"高频编译 + 必须非默认 transformer 且需要计划缓存"的真实场景时另立 plan（届时按 `enableFilter` 先例扩展键维度）。

## Non-Blocking Follow-ups

- `TestEqlCompiler.testAstTransform`（`:60-74`）现用 per-call transformer + `useCache=false`；WI4 落地后可改写为走 factory 默认配置形态（测试形态治理，非本 plan 范围）。
- `EqlCompileContext` 空值回落不变式：若 Phase 0 裁定"暂不加"，登记为 watch-only（防止未来新增绕过 `LoadedOrmModel` 的直接构造路径静默跳过固定环节），触发条件 = 出现新的 `EqlCompileContext` 生产构造点。

## Closure

Status Note: （closure audit 通过后填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:
  - （每条 Exit Criterion 的验证结果 PASS/FAIL + 对应 live code path 或 test name）
  - （每条 Closure Gate 的验证结果 PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md --strict` 退出码
  - Anti-Hollow 检查结果：`scan-hollow-implementations.mjs --module nop-orm / nop-orm-eql --severity high` 退出码 + "配置 → EqlCompiler 钩子"运行时调用链追踪结论
  - Deferred 项分类检查：确认候选 A 属范围裁定而非 live defect 降级；无 in-scope defect 被移入 non-blocking

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
