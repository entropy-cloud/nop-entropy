# EQL WI4 Phase 0 裁定

**日期**：2026-10-04
**范围**：`nop-persistence/nop-orm`（`LoadedOrmModel.compileSql` 方法体 + `ILoadedOrmModel` javadoc）、`nop-persistence/nop-orm-eql`（预期零改动的契约复核）
**状态**：active
**来源**：`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md` Phase 0；`ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI4 节（v7.2）；plan 审查确认的 6 项 Minor 在本文一并吸收（见 §六）
**授权**：零签名改动裁定 ⇒ 不触发 roadmap 门控矩阵 WI4 行的 plan-first 门（该门触发条件 = 6 组签名点之一需要改动；§一逐文件记录"不动"判定）

---

## 一、裁定 1：签名范围 = 零签名改动（不触发 plan-first）

**结论**：6 组签名点全部不动。全部 5 类生产入口已被"env 钩子 + 两处便捷重载直通 + `LoadedOrmModel` 回落"覆盖，无需任何签名透传。

| 签名点 | 判定 | 依据 |
|---|---|---|
| `IOrmSessionFactory`（`IOrmSessionFactory.java` 的 compileSql 声明） | 不动 | 7-arg 委托（`SessionFactoryImpl`）已把 transformer 透传到 `LoadedOrmModel.compileSql`，回落在其方法体内完成 |
| `ISqlCompileTool`（compileSql 声明） | 不动 | 同上；`ILoadedOrmModel extends ISqlCompileTool` 的两个 default 重载（传 null）经回落解析到 env 默认 |
| `SessionFactoryConfig.eqlAstTransformer`（字段 :81 / getter :95 / setter :99） | 不动 | 配置注入链已存在且完好：`OrmSessionFactoryBean.init()` :88 `impl.setDefaultAstTransformer(getEqlAstTransformer())` |
| `IPersistEnv.getDefaultAstTransformer()`（:48） | 不动 | env 级默认钩子的接口面，`SessionFactoryImpl` 字段 :100 / setter :127 / getter :131 已实现 |
| `LoadedOrmModel.compileSql` 7-arg 签名（:89-91） | 不动（只改方法体） | transformer 解析（:92-93 回落）+ 缓存 bypass（裁定 3）都在方法体内实现，不涉签名 |
| `EqlCompileContext` 构造签名（:30-32） | 不动 | 构造点仅在 `LoadedOrmModel`（:100 / :119），transformer 解析在构造之前完成；"空值回落不变式"不加（见 §四 No-Silent-NoOp 与 plan Non-Blocking Follow-ups） |

**升级流程（本裁定唯一的 plan-first 触发路径）**：若执行中发现必须改上述任一签名，**先回 plan 21 增补 Protected Area 声明并完成 draft review，再动代码**（plan 21「Protected Areas 与准入声明」表内已写明）。

**执行期复核（2026-10-04 实测）**：plan Current Baseline 的全部锚点逐一开文件命中，行号零漂移；`EqlCompiler` 仍为 `ISqlCompiler` 唯一实现者、零参构造，transformer 仍从 `ISqlCompileContext.getAstTransformer()`（`EqlCompiler.java:56`）读取。

## 二、裁定 2：transform 时机槽位

**结论**：**`transformBeforeAnalyze` 为固定 lowering 槽位**（改写产物会被完整分析管线处理）；`transformAfterAnalyze` 仅对**不引入新参数、不引入新实体引用**的纯语法改写安全。

理由要素（复核 `EqlTransformVisitor.java` 后落档）：

- **(a) before 槽位的改写产物被完整分析管线处理**。`EqlCompiler.compile` 的执行序是：parse → `transformBeforeAnalyze`（:58-60）→ `EqlTransformVisitor.transform(program)`（:62-63）→ `transformAfterAnalyze`（:65-67）→ 生成器。`transform()` 内部依次执行 `collectNames`（参数收集/编号）、实体解析、隐式 join 展开、`CollectionOperatorTransformer`、`SqlParamTypeResolver`（类型解析）——before 槽位注入或改写的节点全部经过这些步骤。
- **(b) after 槽位绕过上述管线**。`collectNames` 已在 `transform()` 开头运行完毕，after 槽位新引入的 `SqlParameter` 节点不编号（`buildParams` 错位）、新属性导航不展开（生成器直印未解析名）。after 槽位的安全条件因此是：改写只重排/包装**既有**节点，不新增参数标记、不新增实体引用，且不改变既有参数标记的相对顺序。

### 2c：需要方言判定的 lowering（WI8 `IS DISTINCT FROM`）的方言信息来源

**结论：该类改写放 `transformAfterAnalyze` 槽位，方言经"已解析 AST 的表源 → querySpace → `context.getDialectForQuerySpace(querySpace)`"确定。候选"before + 默认 querySpace 猜测"标定低可信，拒绝。**

可信度排序与理由：

1. **after 槽位 + 已解析 AST（高可信，推荐）**：analyze 完成后，主表源持有已解析的表元数据（`SqlSingleTableSource.getResolvedTableMeta()`，管线在 `EqlTransformVisitor` :773 正是用 `tableMeta.getQuerySpace()` 确定 querySpace）。transformer 从已解析表源取 querySpace 再调 `context.getDialectForQuerySpace(querySpace)`，与管线同源、无猜测。
2. **before 槽位 + AST 实体名自行推导（中可信，不推荐为默认）**：`context.resolveEntityTableMeta(entityName)` 在 before 槽位可用（实体元数据解析不依赖 analyze），但对 prop-path join、CTE、子查询等复杂语句，transformer 需复刻管线逻辑才能找到"主表"，复刻出错即静默错方言。
3. **before 槽位 + `context.getDialectForQuerySpace(<默认 querySpace>)` 猜测（低可信，拒绝）**：`getDialectForQuerySpace(null)` 只保证返回默认数据源方言；语句实际路由的 querySpace（经 selector/tenant 路由）可能指向其他方言。错方言判定对"按方言矩阵仅某方言需展开"的 lowering 意味着双向静默错误（漏展开=方言不识别该语法；错展开=该方言本可原生执行却拿到降级 SQL）。**不得作为 WI8 的默认契约。**

`IS DISTINCT FROM` 展开满足 (b) 的论证（WI8 可直接引用）：展开式 `NOT ((a = b) OR (a IS NULL AND b IS NULL))`（方言展开形态按 WI8 裁定）只**重排与包装既有操作数节点**并新增对同一操作数的 null 谓词——不新增 `SqlParameter`（参数标记来自原文 parse，已在 `collectNames` 编号）、不新增实体引用（操作数已解析）、相对参数顺序不变（a 前于 b 在原式与展开式中一致）⇒ 满足 after 槽位安全条件。

**分工总结（WI8 契约）**：引入新参数/实体引用的 lowering → before 槽位（产物过完整管线），方言判定在 before 槽位拿不到 ⇒ 这类 lowering 必须做成方言无关（或经中可信路径显式裁定）；需要方言判定的纯语法改写 → after 槽位 + 已解析 AST 推导方言。

## 三、裁定 3：缓存键方案 = 候选 B（非默认 transformer 绕过缓存），候选 A 被拒

**结论**：`LoadedOrmModel.compileSql` 在 transformer 解析（回落）之后比较 resolved transformer 与 `env.getDefaultAstTransformer()`：**不同实例则 `useCache` 按 false 处理（不读不写缓存）**。缓存的键（`QueryPlanCacheKey` 五字段）保持不含 transformer；不变式 = **缓存中只存在"默认 transformer（或无 transformer）编译的计划"**，而默认 transformer 是 per-session-factory 常量 ⇒ 键不含 transformer 即无歧义。

**三个显式假设（本裁定的成立前提，违者需重开裁定）**：

1. **实例身份比较语义**：resolved transformer 与 env 默认按**引用同一性**（`!=`）比较。"同一实例 ⇒ 同一语义"依赖下述假设 3（确定性）；不同实例即使语义相同也绕缓存——这是**保守方向**的误差（多编译、少复用），不产生正确性风险。比较发生在每次 `compileSql` 调用时（env 默认为 null 且显式传 null ⇒ resolved 与默认同为 null，视为同一，走缓存）。
2. **init 期常量守护**：`SessionFactoryImpl.setDefaultAstTransformer` 是 public setter，技术上可在 init 之后改写默认 transformer。本裁定假设它遵守 **init 期常量契约**：唯一调用方是 `OrmSessionFactoryBean.init()`（:88，全仓实测唯一调用点），init 在 factory 对外服务前执行一次。若未来出现 init 后换默认 transformer 的需求，缓存中旧默认的存量计划立即过期——届时必须清缓存或改键方案，该场景触发时重开裁定。本假设的守护方式 = 契约落 owner doc + javadoc，不添加运行时守卫（零生产配置源状态下守卫无从触发，反成死代码）。
3. **transformer 确定性**：同一 transformer 实例对相同输入（name, sqlText, flags）产出相同改写。这是"缓存默认 transformer 的计划可复用"的前提；非确定 transformer（按调用序/外部状态改变输出）在候选 B 下仍会拿到首次编译的缓存计划。该假设与 `enableFilter` 等既有键维度的隐含假设同构，写入 owner doc 迁移说明。

**被拒候选 A**：transformer（或其语义指纹）纳入 `QueryPlanCacheKey`（照 `enableFilter` 先例）。拒绝理由：指纹来源要么给 `IEqlAstTransformer` 加方法（跨模块消费接口变更，触发 Protected Area），要么用 className+identity（脆弱、跨进程不可比）；收益面（非默认 transformer 也可安全用缓存）在本仓库无真实场景——生产唯一 7-arg 调用点 `FilterSqlHelper` 传 null。已登记 plan 21 `Deferred But Adjudicated`（out-of-scope improvement，触发条件 = 高频编译 + 必须非默认 transformer 且需要计划缓存的真实场景）。

**No-Silent-NoOp 裁定说明（guide Minimum Rules #24）**：候选 B 的"绕过缓存"是显式策略而非静默降级——绕过后仍完整走 `new EqlCompiler().compile(...)` 并返回正确结果，不存在被跳过的能力分支；被"降级"的只是复用性（性能面），且方向保守。与既有先例一致：`TestEqlCompiler` 显式传 transformer 时本就 `useCache=false`（测试层既成事实升格为代码强制）。

## 四、裁定 4：7-arg 的 `astTransformer` 参数 = 保留

**结论**：保留，不弃用、不加 `@Deprecated`。

理由：① 测试/工具 opt-in 路径在用（`TestEqlCompiler.testAstTransform`）；② 删参即签名改动，触发 roadmap plan-first 门，无对应收益；③ "固定性"不由删参达成，而由**解析点唯一化**（全仓唯一生产编译入口 `LoadedOrmModel.compileSql`，null 回落 env 默认）+ **缓存策略**（裁定 3）保证。

**使用边界（写入 owner doc 的迁移说明）**：生产代码**不应** per-call 传 transformer，统一走 factory 默认配置（`SessionFactoryConfig.eqlAstTransformer` → init 注入 → env 默认）；显式传入视为测试/工具行为，且非默认实例不参与计划缓存（不读不写）。

## 五、复核 WI0 基线未回退

- `ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 存在，棘轮区间标记完好（`EQL-CORPUS-BASELINE-BODY-START/END`）。
- 测试 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlCorpusBaseline.java` 存在可跑（Phase 2 以 1/1 为 BC 判据之一）。

## 六、plan 审查 6 项 Minor 的吸收记录

1. **Minor ①（"其余 17 处"计数）**：执行期 2026-10-04 实测 `rg 'new EqlCompiler'` 全仓 32 处命中 = **生产 2 处**（`LoadedOrmModel.java:102/:121`）+ **测试 30 处**（nop-orm-eql src/test 29 处 + nop-duckdb src/test 1 处）。plan 起草时的"其余 17 处"与审查修订值"其余 15 处（合计 17）"均已被 WI1-WI3 新增的方言门测试（`TestEqlForUpdateDialectGate`/`TestEqlFullJoinDialectGate`/`TestEqlSetOperatorDialectGate`/`TestEqlUpdateReturningDialectGate`/`TestEqlIlikeDialectFunction` 等）超越。**结构性结论不变且为本裁定关键依据：生产构造点仍然只有 `LoadedOrmModel` 内 2 处，EQL 编译的唯一生产入口是 `LoadedOrmModel.compileSql`。**
2. **Minor ②（git log 表述精确化）**：plan Current Baseline 原文"`git log master..HEAD` 仅 roadmap/WI0 文档与测试提交"已过时——分支现含 81 个提交（WI1/WI2/WI3 产品提交在内）。**精确表述**：`git log master..HEAD -- <接线链 9 文件>` 中仅 `EqlTransformVisitor.java` 被 WI2 触及（BUG-6 fullJoin 门，与 transformer 管线无接续关系），其余 8 文件（`OrmSessionFactoryBean`/`SessionFactoryConfig`/`SessionFactoryImpl`/`ILoadedOrmModel`/`LoadedOrmModel`/`IPersistEnv`/`IEqlAstTransformer`/`EqlCompiler`）**零提交** ⇒ "该接线非本分支引入（master 侧继承）"的结论成立且比原表述更强。
3. **Minor ③（裁定 3 三假设）**：已落 §三（实例身份比较语义 / init 期常量守护 / transformer 确定性）。
4. **Minor ④（2c 低可信标注）**：已落 §二 2c（"before + 默认 querySpace 猜测"= 低可信，拒绝；推荐 after 槽位 + 已解析 AST 推导）。
5. **Minor ⑤（证明测试 1 须自建 `OrmSessionFactoryBean`）**：`AbstractOrmTestCase.setUp()`（:43-55）自行构造并 `init()`，继承基类**无法**在 init 前注入 transformer。证明测试 1 因此**自建** `OrmSessionFactoryBean`：照 ：48-53 的构造配方（jdbcTemplate/beanProvider/globalCache/sequenceGenerator/columnBinderEnhancer），在 `init()` 前插入 `setEqlAstTransformer(...)`，走与生产完全相同的配置路径。测试不继承 `AbstractOrmTestCase` 的 sessionFactory，自行管理生命周期（`destroy()`）。
6. **Minor ⑥（测试数据）**：证明测试的 EQL 语料与断言值由本 WI 自行落定（证明测试 1 用 h2 内存库 + 既有测试实体模型；具体文本见测试源码，本文不复制）。

## 七、与已有设计的关系

- 上游：`2026-10-03-wi0-gate-adjudications.md`（WI0 三门禁与语料棘轮——本裁定第五节的 BC 判据来源）。
- 下游：owner doc `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 的"AST 改写管线"小节（Phase 1 落档，内容 = 本裁定三、四的使用面：固定环节契约、配置方式、参数使用边界、缓存策略、时机槽位）；WI8 直接消费 §二 2c 的槽位分工与方言来源。
