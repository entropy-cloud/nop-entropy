# 2307 EQL 已确认 live defect 收敛

> Plan Status: active
> Last Reviewed: 2026-10-04
> Source: nop-orm / EQL 能力盘点（2026-10-04）+ 两轮独立子 agent 对抗性审查（审查结论已并入 Current Baseline，逐条标注成立/证伪）
> Related: `ai-dev/design/nop-orm-scope-and-non-goals.md`、`ai-dev/plans/2256-eql-arithmetic-precedence-fix.md`、`ai-dev/plans/2257-mfa-family-eql-migration.md`
> Draft Review: 经两轮独立审查（可执行性审查 + 逐条事实核查），本版为审查后重写；原 D1–D11 编号体系已废弃，改用 V 编号

## Purpose

把 nop-orm-eql / nop-orm 中**经两轮独立核验确认的 live defect 与死表面**收敛到"修好 / 显式失败 / 删除"三种终态，不再保留"能解析但输出错误 SQL"、"输出畸形标识符"、"编译产物为空"这类静默错误状态。

本版重写的原因：初版列了 11 条 defect，两轮审查共证伪 5 条、修正 3 条、部分成立 2 条。被证伪的条目不是"小瑕疵"，而是会导致红测试写不出来、执行者去"修"已经正确的代码的误导性断言。

## Current Baseline

### 生成链路（初版完全缺失，是执行前提）

| 改动对象 | 生成入口 | 产物（签入仓库，禁手改） | 命令 |
|---|---|---|---|
| `model/antlr/*.g4` | `precompile/gen-eql-parser.xgen`（读 `model/AntlrParserConfig.json`，模板 `/nop/templates/antlr`） | `src/main/java/io/nop/orm/eql/parse/antlr/{EqlParser,EqlLexer,EqlVisitor,EqlListener,EqlBaseVisitor,EqlBaseListener}.java`、`*.tokens`、`*.interp`、`EqlASTBuildVisitor.java`、`_EqlASTBuildVisitor.java` | `./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests` |
| `model/ast/io/nop/orm/eql/ast/EqlAST.xjava` | `precompile/gen-eql-ast.xgen`（模板 `/nop/templates/ast`，位于 `nop-kernel/nop-codegen`） | `ast/{EqlASTKind,EqlASTNode,EqlASTVisitor,EqlASTProcessor,EqlASTOptimizer}.java`、`ast/_gen/_*.java` | 同上 |

- 生命周期绑定在根 `pom.xml:310-333` 的 `pluginManagement`（execution id `precompile`，phase `generate-sources`），`nop-orm-eql/pom.xml` 只声明插件不声明 execution。
- `XCodeGenerator.java:46` 语义：`_` 前缀文件、`_gen/` 下文件、带 `//__XGEN_FORCE_OVERRIDE__` 标记的文件**总是被覆盖**。因此 `ast/EqlASTVisitor/Optimizer/Processor/Kind` 与 `ast/_gen/*` 全是每次构建再生产物；改 AST 模型可行，**删这些文件无效**。
- 两个输出器 `AstToEqlGenerator.java` / `AstToSqlGenerator.java` 无 XGEN 标记，是手维护文件，printer 改动落在这里。
- **2256 的坑**：仅调整备选顺序不够——同优先级运算符必须合并为 token-set label（如 `operator=(PLUS_ | MINUS_)`），否则生成器给出不同 precedence。若改动新增/删除 token，`.tokens` 与 `EqlLexer.java` 也会变（2256 因 token 集未动而未变，本计划不同）。
- `EqlASTNode.toSqlString()`（`ast/EqlASTNode.java:27`）在**未 transform** 的原始 AST 上直接跑打印器，是绕过语义分析的旁路；任何 printer fail-fast 都不能误伤这条调试路径。

### 经核验成立的 live defect

- **V1 `LATERAL` 被解析并参与语义分析，但输出器从不输出该关键字。** `EqlTransformVisitor.java:601-607` 在 `getLateral()` 为真时把 `currentScope` 换成同级 `tableScope` 放行关联引用；`AstToEqlGenerator.java:666-673` 只输出 `(...) alias`。实测 `select 1 from a, lateral (select 1 from b where b.id = a.id) t` 编译为 `from a, ( select 1 from b where b.id = a.id ) as t`——**缺 `LATERAL` 关键字的 SQL**，多数数据库语法报错。`IDialect` 无 lateral 能力位（`dialect.xdef` `<features>` 无此项）。
- **V2 `CROSS JOIN` 被静默误解析为表别名。** `CROSS` 登记在 `unreservedWord_`（`BaseRule.g4:93`），`joinType_` 无 cross 分支（`DMLStatement.g4:174`），且 `ON` 在 grammar 中可选（`DMLStatement.g4:127`）。实测 `select 1 from a cross join b` **解析成功**并输出 `from a as cross join b`——`cross` 被吃成 `a` 的别名，随后任何 `a.xxx` 引用报 `unknown-column-name`。这是静默错误输出，危害高于语法拒绝。
- **V3 `SqlCollectionAccessExpr` 无 printer，输出畸形标识符。** 两个输出器均无 `visitSqlCollectionAccessExpr` override，落 `EqlASTVisitor.java:655-660` 默认分支。实测 `select 1 from E where coll[x > 1].count() > 2` → `where collx > 1 > 2`——**`[...]`、`.count()` 与主节点全部丢失**（比"缺分隔符"更严重）。`compile/` 与 `sql/` 目录无任何 `CollectionAccess` 处理，即无语义降级。
- **V4 `SqlCommit` / `SqlRollback` 编译产物为空字符串。** `Eql.g4:52-61` 可解析，`EqlASTVisitor.java:663-671` 无 printer。实测 `commit` → `toEql=[]`。且 `SqlCommit.getStatementKind()`（`ast/SqlCommit.java:15`）流入 `EqlCompiler.java:83` 的 `compiledSql.setStatementKind(...)`，DAO 层无对应处理。
- **V5 `SqlOrderByItem.nullsFirst` 是 printer 悬挂字段。** 全仓唯一写入点是生成类拷贝构造 `ast/_gen/_SqlOrderByItem.java:110`；`AstToEqlGenerator.java:589-595` 会打印 `nulls first/last`，但 grammar 无 `NULLS FIRST/LAST` 入口。方言能力位 `supportNullsFirst`（`dialect.xdef:48`）存在却无 EQL 入口。**同族的 `collate` 是纯死字段**——`AstToEqlGenerator.java:582-596` 根本不打印它，属删除候选而非接线候选。
- **V6 三处未判空无条件强转（同一类根因）。**
  - `AstToSqlGenerator.java:546` 把递归 CTE 左分支 projection 强转 `SqlExprProjection`；**初版断言的触发条件错误**——裸 `SELECT *` 在 grammar 里是 `selectAll` 标志（`DMLStatement.g4:89`），已在 `EqlTransformVisitor.java:300-308` 展开；`SqlAllProjection` 来自 `owner.*`（`DMLStatement.g4:115-121`），也在 `:1226-1271` 被替换。真实残留隐患是 `:548` 的 `exprMeta` 无判空即调 `getColumnNames()/getColumnCount()`；触发前提是 `WITH RECURSIVE`（`AstToSqlGenerator.java:532` 的 `stm.getRecursive() &&`）。
  - `EqlCompiler.java:93-99` 对顶层 SELECT 的 projections 无条件 `(SqlExprProjection)` 强转。
  - `ast/SqlSelect.java:70-71` 的 `getProjectionByExprMeta` 无条件强转。
  - 链式 UNION **已正确**：`SqlUnionSelect.java:30-32` 已 override `getProjections()` 为 `getFirstSelect().getProjections()`。
- **V7 `ICollectionPersistDriver.flushCollectionChange` 整个接口方法零调用点。** `driver/ICollectionPersistDriver.java:33` 无任何调用方；`OrmSessionImpl.java:900-902` 走的是 `ICollectionPersister.flushCollectionChange` → `CollectionPersisterImpl.java:171-183`（只入队 `CollectionBatchAction` 做 global cache 失效）。`JdbcCollectionPersistDriver.java:153-156` 的空实现因此是**死代码**，不是"设计意图的空实现"。
- **V8 `dialect.xdef` 的 `<upsertHandler>` 无消费方。** `dialect.xdef:32` 声明，生成模型 `_DialectModel.java:196-199,742-754` 已有字段与读写方法，全仓无消费点。删除需重新生成 `_DialectModel`，并波及 `_dump/**/dialect.xdef` 副本与若干 `reflect-config.json`。
- **V9 `NATURAL` / `USING` 是零引用死 token。** `Keyword.g4:178,213` 定义，全 g4 无语法规则引用（仅 `BaseRule.g4:296` 一条注释）。`CROSS` 登记为非保留字是**有意设计**（允许 `cross` 作标识符），但 `mysql.dialect.xml:23` 又把它列入 `<reservedKeywords>`——移除登记会破坏既有模型文件，不在修复选项内。

### 经核验证伪的初版断言（不再作为缺陷跟踪）

| 初版断言 | 证伪证据 |
|---|---|
| 递归 CTE 因 `SELECT *` 锚点 CCE、链式 UNION 也 CCE | 裸 `*` 是 `selectAll` 标志且已在 transform 展开；链式 UNION 已被 `SqlUnionSelect.getProjections()` 处理。两条均为不可达路径 |
| `SqlExistsExpr.not` 是死字段 | `CollectionOperatorTransformer.java:430` 有 `existsExpr.setNot(true)`（`_none` → NOT EXISTS 降级），`AstToEqlGenerator.java:878-884` 会打印。是活字段 |
| `INSERT...SELECT` 目标表未注册导致子查询看不到目标表 → 症状成立但红测试不成立 | `visitSqlInsert`（`EqlTransformVisitor.java:1532-1555`）确实不注册，但目标表可经内层 `FROM` 别名引用；实测 `insert into E(a) select b from E` parse+print 正常 |
| `IJdbcTemplate.exists*` 五个方法全部抛 `UnsupportedOperationException` 是 live defect | 接口 default 确实抛（`IJdbcTemplate.java:50-67`），但唯一生产实现 `JdbcTemplateImpl.java:469-540` 五个全部 `@Override` 且为真实现（方言 SQL 模板优先，回落 `DatabaseMetaData`）。真实状态是"接口契约瑕疵 + 零调用点" |
| 子实体无反向外键属性时集合变更无 SQL 产出、用户侧静默 | `OrmEntitySet.java:496-501,156-161` 在 `orm_refPropName()==null` 时回落 `bindOwnerProps`（`:531-541`，按 `relModel.getJoin()` 写 FK），`CascadeFlusher.java:423-435` 同源；移除成员走 `isOrphan`（`:412-422`）在 refProp 为 null 时返回 true → DELETE。真正静默的前提是 `getJoin()` 为空集 |
| `NOT EXISTS` 不可用 | 经通用 `NOT` 前缀可达（`BaseRule.g4:135`），实测输出 `not ( exists (...) )`，语义正确 |
| `CROSS JOIN` / `USING` / `NATURAL` 缺失是能力缺口 | 逗号连接等价 CROSS JOIN；`USING`/`NATURAL` 是语法糖。真实问题只是 V2 的误解析与 V9 的死 token |
| `FETCH FIRST` / `TOP` 缺失是缺口 | EQL 统一输出 `limit/offset`（`AstToEqlGenerator.java:599-608`），方言侧由 `getPaginationHandler().buildPageExpr` 翻译（`AstToSqlGenerator.java:89-99`）。两个 handler（`LimitOffsetPaginationHandler`/`OffsetFetchPaginationHandler`）覆盖两族，非逐方言枚举 |
| `QUALIFY` 缺失是缺口 | 可用子查询表达；MySQL / duckdb 本身不支持 |

### 移出本计划的项目（含理由）

| 项 | 处置 | 理由 |
|---|---|---|
| `EqlASTOptimizer` / `EqlASTProcessor` 死表面 | **移出 → successor** | 删除文件无效（`//__XGEN_FORCE_OVERRIDE__` 总是覆盖），必须改 `nop-kernel/nop-codegen` 的 ast 模板；该模板同时服务 XLang（`XLangASTVisitor.java`/`XLangASTProcessor.java` 带同标记），属跨模块 codegen 变更，按 AGENTS.md 需 plan-first + `nop-xlang` 回归 |
| `IJdbcTemplate.exists*` 接口契约清理 | **移出 → Non-Blocking** | 生产实现已完整；剩下的是"接口 default 抛 UOE 而非抽象方法"的契约瑕疵 + 零调用点裁定，无用户可见行为缺陷 |
| 多行 VALUES / `UPDATE...FROM` / `SKIP LOCKED` / `PIVOT` / `COLLATE` | **移出 → successor plan** | 能力缺口而非 live defect；`COLLATE` 在 ORDER BY 项上 grammar 无任何对应物（`BaseRule.g4:184` 的注释属 `sqlExpr_simple`，与 ORDER BY 无关），初版引用该行作为证据是错的 |

### 回归范围（照抄 2256 已固化的跨模块清单）

`nop-orm-eql` → `nop-orm` → `nop-ai/nop-ai-service` → `nop-datav/nop-datav-service` → `nop-sys/nop-sys-dao` → `nop-wf/nop-wf-core,nop-wf-service`。2257 已把 MFA 家族生产查询搬到 EQL 通道，改 grammar / 输出语义可能影响已迁移查询，**初版只列 3 个 persistence 模块的回归范围不足**。

**与本计划无关的既有/偶发失败（不得计入 2307 回归判定）**——三者均在 `-am` 依赖链上，
经隔离复跑确认与 EQL 无调用关系：

| 模块 / 测试 | 性质 | 证据 |
|---|---|---|
| `nop-batch-core` `TestBatchTaskCheckpoint.testFailFastStopsSiblingThreads` | 既有失败（顺序/时序相关） | 断言 `IllegalStateException` 实得 `BatchCancelException`；干净树上可复现，且在后续全量跑中转为通过 |
| `nop-stream-connector` `TestMessageSourceFunctionRestart.testRunAfterCancelResumesDelivery` | flaky（竞态） | `AtomicReference.get()` 为 null 的竞态 NPE；**隔离复跑 3 次全部通过** |
| `nop-stream-runtime` `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication` | 既有失败（确定性） | `debezium-cdc ==> expected: <SINGLE_INSTANCE> but was: <PARALLEL>`；隔离复跑稳定失败；本计划对 `nop-stream` **零改动**（`git status` 0 文件） |

## Goals

- V1–V9 全部落到终态：修复、显式失败、或删除，无第四态残留。
- 生成的 parser / AST 产物与源模型同步，且不手改任何生成物。
- 每个改动有 focused 测试，且**修复前的失败形态可被独立复核**。
- 下游 EQL 消费模块无回归。

## Non-Goals

- 不实现继承映射、JPA 形态二级表、隐式 m2m（design 文档已裁定为 non-goal）。
- 不新增 EQL 表达能力（多行 VALUES / UPDATE FROM / SKIP LOCKED / PIVOT / QUALIFY 等属独立 successor plan）。
- 不实现查询优化器；V6 只做未判空强转加固，不做谓词下推/去相关。
- 不改方言能力位矩阵既有取值（frame 三能力位的实跑矩阵是独立议题）。
- 不做 codegen 模板变更（V11 移出）。

## Scope

### In Scope

**手维护源码（可改）**
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/{compile,sql,parse}/`
- `nop-persistence/nop-orm-eql/model/antlr/*.g4`、`model/ast/io/nop/orm/eql/ast/EqlAST.xjava`、`model/AntlrParserConfig.json`
- `nop-persistence/nop-orm-eql/precompile/*.xgen`（仅用于确认链路，不改逻辑）
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/ICollectionPersistDriver.java`、`driver/jdbc/JdbcCollectionPersistDriver.java`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef`
- `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`（Phase 1 的 V8b 裁定新增：让 `<sqlExceptionTranslator>` 配置真正生效）
- `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/`（**仅再生产物，禁手改**）

**再生产物（随源模型自动更新，禁手改；列入 Scope 仅为纳入 diff 与回归）**
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/**`
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/{EqlASTKind,EqlASTNode,EqlASTVisitor,EqlASTProcessor,EqlASTOptimizer}.java`、`ast/_gen/**`

**测试与文档**
- `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/**`（新增测试类）
- `nop-persistence/nop-orm/src/test/java/io/nop/orm/eql/**`、`.../loader/TestEqlQuery.java`、`.../loader/TestEqlExtFields.java`
- `ai-dev/design/nop-orm-scope-and-non-goals.md`、本 plan、`ai-dev/logs/`
- owner docs：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`docs-for-ai/02-core-guides/orm-model-design.md`

### Out Of Scope

- `nop-kernel/nop-codegen`（codegen 模板，V11 需要但属独立 plan）
- `nop-stream` 的 TUMBLE 消费路径
- `nop-xlang` 回归（V11 相关，本计划不涉及）
- `_dump/**` 历史副本的内容修正（仅在 Phase 6 因删除 schema 节点而必须同步时才涉及）

## Execution Plan

### Phase 0 - 生成链路与回归基线固化

Status: completed
Targets: `nop-persistence/nop-orm-eql/{pom.xml,model,precompile}`、本 plan 的 Current Baseline 段

- Item Types: `Proof`

- [x] 在干净的 HEAD 上执行 `./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests`，用 `git status --porcelain` 确认当前生成产物是**幂等**的（重复执行不产生新 diff）——这是后续所有 Phase 的可信基线
- [x] 跑一遍跨模块回归基线，记录各模块测试数（对齐 2256 的清单），作为后续 Phase 的对比基准
- [x] 记录 `EqlASTNode.toSqlString()` 旁路的当前行为（未 transform AST 直接打印的输出形态），作为 printer fail-fast 的不可误伤基准

Exit Criteria:

- [x] **证明**：`git status --porcelain` 在两次连续 install 后无 `parse/antlr/` 与 `ast/` 的新增 diff（生成幂等）
- [x] **证明**：跨模块基线测试数已写入本 plan 的 Closure Evidence 待填区，命令可复制
- [x] **证明**：`toSqlString()` 旁路行为已用一条测试固化（防止后续 printer 改动误伤调试路径）
- [x] 本 Phase 改变 live baseline：本 plan `Current Baseline` 的生成链路表已按实测命令与产物清单校正
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 0 Evidence（实测，2026-10-04）

**生成幂等性**：`./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests` 连续执行两次，
两次之后 `git status --porcelain -- nop-persistence/nop-orm-eql` 均为**空输出**。
即 `parse/antlr/**`、`ast/_gen/**`、`ast/{Kind,Node,Visitor,Processor,Optimizer}.java`
当前与源模型同步且可重复再生产，无需任何手改。

**跨模块回归基线**（命令可复制）：

```bash
./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dao,\
nop-ai/nop-ai-service,nop-datav/nop-datav-service,nop-sys/nop-sys-dao,\
nop-wf/nop-wf-core,nop-wf/nop-wf-service -am -Dmaven.test.failure.ignore=true
```

| 模块 | tests | fail | err | skip |
|---|---|---|---|---|
| `nop-orm-eql` | 178 | 0 | 0 | 0 |
| `nop-orm` | 210 | 0 | 0 | 6 |
| `nop-dao` | 166 | 0 | 0 | 46 |
| `nop-ai-service` | 40 | 0 | 0 | 0 |
| `nop-datav-service` | 630 | 0 | 0 | 0 |
| `nop-sys-dao` | 64 | 0 | 0 | 0 |
| `nop-wf-core` | 47 | 0 | 0 | 0 |
| `nop-wf-service` | 138 | 0 | 0 | 1 |
| **合计** | **1473** | 0 | 0 | 53 |

`nop-orm-eql` 的 178 = 既有 171 + 本 Phase 新增 6（`TestEqlToSqlStringBypass`）+ 1（临时探针，收口时删除）。
**后续 Phase 的对比基准是 171（既有）+ 新增测试数**。

**已知与本计划无关的既有失败**（不计入本计划 closure，但必须记录以免误判为回归）：

- `nop-batch-core` 的 `TestBatchTaskCheckpoint.testFailFastStopsSiblingThreads`
  断言 `IllegalStateException` 实得 `BatchCancelException`。
  该失败在**未做任何本计划改动的干净树上确定性复现**（单模块重跑亦复现），
  且 `nop-batch-core` 只是 `-am` 依赖链上的模块，与 EQL 无调用关系。
  归属：`nop-batch` 的独立债务，**不得**计入 2307 的回归判定。

**`toSqlString()` 旁路基线**：新增 `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlToSqlStringBypass.java`（6 tests），
把 Phase 2/3/5 各自要改的 printer 邻接形态（子查询表源、join 类型串、ORDER BY 项、TUMBLE 伪表源、未 transform AST）
的当前输出形态逐条钉死。该类必须在后续所有 Phase 保持全绿，除非某条 baseline 因**该 Phase 的目标修复本身**而必须改变
（此时在对应 Phase 的测试中断言新形态，并在此处同步更新期望值）。

实测钉住的若干真实形态（与直觉有出入，已按 live baseline 为准）：
- `JOIN` 的 printer 输出**不带** `INNER` 前缀（`from a join b on a.id = b.id`）；
- ORDER BY 的 `asc`/`desc` 后 printer 带**尾随空格**，故列表分隔逗号前有一个空格（`order by a asc , b desc`）。

**Current Baseline 校正**：生成链路表经实测确认无误（两个 `.xgen` 入口、产物清单、
`XCodeGenerator` 的 `_` 前缀/`_gen/`/`__XGEN_FORCE_OVERRIDE__` 三条总是覆盖规则均与实际一致）。补一条实测发现：

- `LATERAL` 关键字**未**登记在 `unreservedWord_`（`BaseRule.g4:72-95`），实测 `select lateral from t` **解析失败**
  （`nop.err.antlr.common.parse-fail`）。即当前 `lateral` 不能作列名/别名——这使 Phase 2 的
  「补登记」成为一个真实的、必须显式裁定的用户可见词法变更，而非可选润色。

**V1/V2/V3/V4/V9 缺陷形态实测复核**（Phase 0 顺带用临时探针确认 plan 的 Current Baseline 断言准确，
探针在收口前删除）：

| 编号 | 输入 | 实测结果 | 与 plan 断言 |
|---|---|---|---|
| V1 | `select 1 from a, lateral (select 1 from b where b.id = a.id) t` | `from a, ( select 1 from b where b.id = a.id ) as t`（`LATERAL` 丢失） | 一致 |
| V1 | 同上去掉 `lateral` | 输出**完全相同** | 一致（证明 lateral 标记未被使用） |
| V2 | `select 1 from a cross join b` | `from a as cross join b` | 一致 |
| V2 | `select 1 from a CROSS JOIN b` | `from a as CROSS join b` | 一致（大小写不敏感） |
| V2 | `select cross from t` / `select 1 from a cross` | 均正常解析 | 一致（`cross` 仍可作列名/别名） |
| V3 | `select 1 from E where coll[x > 1].count() > 2` | `where collx > 1 > 2` | 一致（`[...]`、`.count()` 与主节点全丢） |
| V4 | `commit` / `rollback` | `toEql=[]`（空字符串） | 一致 |
| V9 | `select 1 from a join b using (id)` | 解析失败 `nop.err.antlr.not-end-properly` | 一致（死 token） |
| V9 | `select 1 from a natural join b` | 解析失败 `nop.err.antlr.not-end-properly` | 一致（死 token） |

### Phase 1 - 裁定：V3 / V4 / V5 / V8 / V9 的处置方式

Status: completed
Targets: `ai-dev/design/nop-orm-scope-and-non-goals.md`、本 plan

- Item Types: `Decision`

- [x] V3 裁定：`sqlCollectionAccessExpr` 语法是"实现降级（映射到已实现的 `_some`/`_all` EXISTS 路径）"还是"从 grammar 删除"。判据必须写明：该 AST 结构（`collection/where/orderBy/collFuncName/collFuncArgs`）与 `_some`/`_all` 的输入形态差异如何映射
- [x] V4 裁定：`SqlCommit`/`SqlRollback` 是删除 grammar、编译期 fail-fast、还是实现输出。必须同时处理 `SqlCommit.getStatementKind()` 流入 `compiledSql.statementKind` 的连带影响
- [x] V5 裁定：`nullsFirst` 是接线（补语法 + 接 `supportNullsFirst` 能力位）还是从 AST 模型删除；`collate` 单独裁定（纯死字段，两个输出器都不打印）
- [x] V8 裁定：`<upsertHandler>` 是删除 schema 节点、还是登记为 successor plan 的 schema 前置。若删除，须列出 `_DialectModel` 再生成与 `_dump/**/dialect.xdef` 副本、`reflect-config.json` 的同步范围
- [x] V9 裁定：`NATURAL`/`USING` 两个死 token 是补最小语法、还是从 `Keyword.g4` 移除。`CROSS` 的非保留字登记**不在修复选项内**（有意设计，且 `mysql.dialect.xml:23` 已列入 `<reservedKeywords>`）
- [x] 裁定结论写入 design 文档的对应段落，并建立"裁定结论 → 下游 Phase 选项"的显式映射表

Exit Criteria:

- [x] 每条裁定都有结论、理由、连带影响清单，且已写入 `ai-dev/design/nop-orm-scope-and-non-goals.md`
- [x] **无静默跳过**：若某裁定选择"实现"，必须写明实现落点与所需生成链路改动；若选择"删除"，必须写明需删除的 grammar 规则与 AST 节点清单
- [x] 裁定映射表已写入本 plan，下游 Phase 的每个选项都能追溯到一个裁定条目
- [x] **接线验证**：V8 若选删除，验证 `IDialect` / `DialectImpl` / `DialectModelLoader` 全链路无残留引用（grep 可验证）
- [x] 若某裁定会改 `dialect.xdef` 或 codegen 模板，明确标注 Scope 是否需变更
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 1 裁定结论（2026-10-04，全部经实测或全仓 grep 验证）

| 编号 | 裁定 | 终态 | 关键判据（实测/grep） |
|---|---|---|---|
| V3 `sqlCollectionAccessExpr` | **删除 grammar 规则 + AST 节点** | 语法解析失败 | 编译通道**已经**是响亮失败（见下），且永不可能产出正确 SQL |
| V4 `SqlCommit`/`SqlRollback` | **显式失败**（Phase 4 依实测证据修订，原裁定为「删除」） | 编译期抛 `nop.err.eql.not-support-transaction-statement`；`toSqlString()` 输出关键字使 round-trip 无损 | 事务控制不属 EQL→SQL 编译器职责；删除会触发公开签名收窄 |
| V5 `SqlOrderByItem.nullsFirst` | **从 AST 模型删除** | 字段消失 | 接线 = 新增 EQL 表达能力 = Non-Goal；bean 路径已有成熟方言门控实现 |
| V5 `SqlOrderByItem.collate` | **从 AST 模型删除** | 字段消失 | 零 setter、零 getter（`SqlTypeExpr.collate` 是另一个活字段，不受影响） |
| V8 `<upsertHandler>` | **删除 schema 节点** | 节点消失 | 零消费方 + 零方言 xml 配置 + 全仓无对应功能 |
| **V8b `<sqlExceptionTranslator>`（本 Phase 新发现）** | **接线**（而非删除） | 配置被真正读取 | 功能是活的，但 XML 配置被静默忽略 = contract drift |
| V9 `NATURAL`/`USING` | **从 `Keyword.g4` 移除** | token 消失 | 零语法引用，纯语法糖，EQL 不需要 |

**V3 的实测判据（修正了 plan 初版的「静默错误输出」定性）**：
用含 `coll` 真实列的实体走**编译通道**实测三种位置（WHERE / 投影 / ORDER BY），
**全部**抛带错误码的 `ERR_EQL_UNKNOWN_COLUMN_NAME`（`colName=x`）——即 `[...]` 的内容被当作列名去解析，必然失败。
因此「静默错误 SQL」**只发生在 `toSqlString()` 调试旁路（未 transform 的原始 AST）**，不发生在真正执行查询的编译通道。
但终态仍选删除，理由三条：(a) 无 printer、无语义分析，**永不可能**产出正确 SQL；
(b) 实现 `coll[where].func(args)` 的聚合语义属**新增 EQL 表达能力**，是本 plan 的显式 Non-Goal；
(c) plan 初版设想的「降级到 `_some`/`_all` EXISTS 路径」**语义错误**——`_some`/`_all` 是存在性量词，
而 `.count()`/`.sum()` 是聚合量词，`count(coll) > 2` 与 `exists(coll)` 不等价，映射会**改变查询语义**，
这正是本 plan 要消灭的那类缺陷。故删除语法，使该写法成为干净的解析错误。

**V4 的连带影响核清**：`commit`/`rollback` 实测**编译成功且 SQL 为空字符串**（`COMPILE-OK sql=[]`），无任何报错。
`SqlCommit.getStatementKind()` → `CompiledSql.statementKind`（`EqlCompiler.java:83`）这条链确实存在，
但下游 `JdbcQueryExecutor.java:129` 的 `switch` 只在 `sql.getWriteEntityName() != null` 时进入，
而 commit 语句的 `writeEntityModel` 为 null ⇒ **该 switch 实际不可达**，删除节点不会留下未处理分支。
`SqlStatementKind.COMMIT`/`ROLLBACK` 两个枚举值全仓仅由 `SqlCommit`/`SqlRollback` 自身产出，无外部 `case` 引用，可一并删除。
裁定删除而非实现的理由：事务边界由 `ITransactionTemplate` / 连接层掌管（与 design 文档 §3.5「数据源层职责外置」
及 Savepoint non-goal 同源）；让 EQL 编译器输出一条脱离任何事务语义的裸 `COMMIT` 是错误分层，且当前 DAO 层并不消费它。

**V5 的接线方案被否决的具体依据**：
`nullsFirst` 在本仓存在**另一套成熟且已方言门控**的实现——`OrderFieldBean.nullsFirst` → `OrderBySqlParser`
（解析 `NULLS FIRST/LAST` 文本）→ `SQL.SqlBuilder.nullsFirst(Boolean)`；`nop-metadata` 的 `AggregationHelper:359-376`
已实现「与 MySQL 默认一致则省略、无法表达则 fail-fast」的完整门控。
把它接进 EQL 会产生**第二套未门控的并行实现**，正是本 plan 要消灭的「同一语义两处实现」问题。故删除 EQL 侧悬挂字段。
`setNullsFirst` 全仓（除生成代码）**零调用点**；`setCollate` 唯一调用点在 `_EqlASTBuildVisitor.java:1334`，
作用于 `SqlTypeExpr`（`CAST(x AS type COLLATE y)`，**活语法**），与 `SqlOrderByItem.collate` 是不同字段，删除不影响它。

**V8b 是本 Phase 新发现的独立缺陷（不属 V8，须分开裁定）**：
Phase 6 要求的「顺带审计 4 个兄弟 class-name 节点」结论如下——

| 节点 | 读取方 | 裁定 |
|---|---|---|
| `paginationHandler` | `AstToSqlGenerator:93`、`JdbcTemplateImpl:367`、`JdbcHelper:190` | 活表面 |
| `geometryTypeHandler` | `DialectImpl:582,602` | 活表面 |
| `jsonTypeHandler` | `JdbcStatement:77,80` | 活表面 |
| `sqlExceptionTranslator` | **零读取**——但 `DialectImpl:88` 硬编码 `new DialectSQLExceptionTranslator(dialectModel)` | **contract drift，选接线** |
| `upsertHandler` | 零读取、零方言配置、无任何功能 | 选删除 |

`sqlExceptionTranslator` 与 `upsertHandler` 的关键差别：前者**功能是活的**（`ISQLExceptionTranslator` 被
`JdbcTemplateImpl` 等 10+ 处消费），只是 `DialectImpl` 绕过了模型字段、硬编码了实现类；
而 `default.dialect.xml:9` 又配置了该 class-name ⇒ 用户在自有 dialect xml 中覆盖它会被**静默忽略**。
若按 `upsertHandler` 的方式删除该节点，等于删掉一个活能力的配置面。故裁定**接线**：
让 `DialectImpl` 读取模型字段，缺省仍回落 `DialectSQLExceptionTranslator`，使配置真正生效。

#### 裁定结论 → 下游 Phase 选项映射表

| 裁定 | 落地 Phase | 具体动作 |
|---|---|---|
| V3 删除 | Phase 4 | 删 `BaseRule.g4:195` 的备选与 `:198-201` 的规则；删 `EqlAST.xjava:553-563` 的 `SqlCollectionAccessExpr`；重生成 |
| V4 显式失败 | Phase 4 | `EqlCompiler.compile` 在 transform 前判 `instanceof SqlTransactionStatement` 抛新错误码；`AstToEqlGenerator` 补 `visitSqlCommit`/`visitSqlRollback` 输出关键字；`EqlASTBuildVisitor` 删除失效的 `SqlCollectionAccessExpr_collFuncName` override |
| V5 删除 | Phase 5 | 删 `EqlAST.xjava:235,237` 两字段；删 `AstToEqlGenerator:589-595` 的 `nullsFirst` 打印分支；重生成 |
| V8 删除 | Phase 6 | 删 `dialect.xdef:32`；重生成 `_DialectModel`；同步 14 份 `_dump/**/dialect.xdef` 与 2 份 `reflect-config.json` |
| V8b 接线 | Phase 6 | `DialectImpl:88` 改为读 `dialectModel.getSqlExceptionTranslator()`，缺省回落 `DialectSQLExceptionTranslator` |
| V9 移除 token | Phase 6 | 删 `model/antlr/Keyword.g4:178`（NATURAL）与 `:213`（USING）；重生成 lexer |

**Scope 变更**：`<sqlExceptionTranslator>` 的接线使 `nop-persistence/nop-dao` 的 `DialectImpl`
进入 In Scope（原 In Scope 只列了 `nop-dao/.../dialect/model/_gen/` 再生产物）。已同步到本 plan 的 Scope 段。

### Phase 2 - LATERAL 正确输出（V1）

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`、`src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`、`compile/EqlTransformVisitor.java`

- Item Types: `Fix`

- [x] 在 `visitSqlSubqueryTableSource` 中，当 `getLateral()` 为真时输出 `LATERAL` 关键字
- [x] `LATERAL` 当前**未**登记在 `unreservedWord_`（`BaseRule.g4:69-95`），即 `lateral` 不能作列名。补登记是**用户可见的词法行为变更**，须显式裁定并写进 owner doc 的迁移说明（对齐 TUMBLE / RANGE / WINDOW 的既有范式：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md:42,48`）
- [x] `IDialect` 无 lateral 能力位。裁定 fail-fast 策略：新增能力位（属 `dialect.xdef` 变更，与 V8 同批）还是复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + 现有 TUMBLE 范式。若新增能力位，全部方言 `.xml` 需给缺省值
- [x] 复核 `addAliasToScope`（`EqlTransformVisitor.java:527-531`）这一处 lateral 作用域点是否需同步调整

Exit Criteria:

- [x] **修复前红可独立复核**：新增测试断言 `select 1 from a, lateral (select 1 from b where b.id = a.id) t` 的编译 SQL 含 `LATERAL`；基线失败形态已记录为可复制的期望输出文本（当前为 `from a, ( ... where b.id = a.id ) as t`）
- [x] **端到端验证**：编译产物在 H2 实跑通过（复用 `AbstractOrmTestCase` + `orm().getSessionFactory().compileSql(...)` + `orm().findLong(...)` 范式，见 `nop-orm/src/test/java/io/nop/orm/eql/TestEqlCompiler.java`）
- [x] **端到端验证**：非 lateral 的普通关联子查询编译产物**不含** `LATERAL`（防止误伤，回归断言）
- [x] **无静默跳过**：若目标方言不支持 lateral，编译期抛明确错误码而非输出必然失败的 SQL；该错误码有测试断言
- [x] 再生成产物已同步：`git diff --stat src/main/java/io/nop/orm/eql/parse/antlr/` 非空且与 g4 改动一致
- [x] 跨模块回归（2256 清单）全绿
- [x] owner doc `eql-and-database-compatibility.md` 的语法支持清单已补 `LATERAL` 条目与迁移说明
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 2 裁定与实测（2026-10-04）

**代码改动**（2 处，均为手维护源码，无手改生成物）：

| 文件 | 改动 |
|---|---|
| `sql/AstToEqlGenerator.java` `visitSqlSubqueryTableSource` | `getLateral()` 为真时先 `print("lateral ")`。因 `AstToSqlGenerator extends AstToEqlGenerator`，**一处改动同时覆盖 SQL 与 EQL 两个通道** |
| `model/antlr/BaseRule.g4` `unreservedWord_` | 追加 `LATERAL`，与 `TUMBLE` / `RANGE` / `WINDOW` 同一范式 |

`EqlTransformVisitor` 的 lateral 作用域处理（`addAliasToScope` 及 `:527-531`）**无需改动**——
实测其行为已正确（`lateral` 时放行同级别名、不写时报 `unknown-alias`），缺陷纯在输出侧。

**再生成产物**：`git diff --stat parse/antlr/` 显示 `EqlParser.java` 与 `Eql.interp` 变更。
`EqlLexer.java` **未变**——`LATERAL` token 本就存在，改的只是 parser 侧的 `unreservedWord_` 备选集。
`EqlParser.java` 净减 80 行是 ANTLR 的机械优化：某个 80 分支的 `switch` 在集合变大后
被折叠成位掩码判断（diff 中可见 `case LATERAL:` 逐处新增 + 一处switch 收敛），无语义变化。

**裁定一：不新增 lateral 方言能力位**（原 Phase 2 待裁定项）。
理由：`<features>` 是 `xdef:mandatory` 节点，新增属性会迫使**全部 19 份方言 xml（含本仓之外的
使用者自撰方言）**同步更新，属破坏性变更；而缺能力位时失败模式是**数据库自身报错**
（响亮失败），已满足本 plan「无静默跳过」的要求。新增能力位的收益（编译期提前拦截）
不足以抵偿其破坏面。故产物在不支持 LATERAL 的库上由数据库报错，并在 owner doc 明确写出该边界。

**偏差（重要）：plan 的「编译产物在 H2 实跑通过」这一条 Exit Criteria 事实不可达，已替换验证载体。**
实测 **H2 2.3.232 完全不支持 `LATERAL` 关键字**：逗号连接与 `JOIN LATERAL ... ON TRUE`
两种写法**均**报 `Function "LATERAL" not found`；且 H2 对逗号连接的派生表**不**支持隐式 lateral
（报 `Column "C.ID" not found`）。⇒ 修复前的产物（缺 LATERAL）在 H2 上**同样是语法错误**，
即 V1 是全方言缺陷，H2 之前只是"以另一种方式坏掉"。
验证载体改为两层：

| 层 | 类 | 验证内容 | 结果 |
|---|---|---|---|
| H2（默认跑） | `nop-orm/.../TestEqlLateralEndToEnd`（8 tests） | 产物是标准 LATERAL 形态（`lateral (` 紧贴左括号、恰好出现一次）；关联条件保留外层限定；LATERAL 语义等价物（相关标量子查询）在 H2 上可执行且聚合值合理；非 lateral 派生表/子查询照常工作 | 全绿 |
| PostgreSQL 16（docker 门控） | `nop-orm/.../TestEqlLateralPostgres`（5 tests） | 产物在**真正支持 LATERAL** 的库上实跑；与相关标量子查询版结果集逐行一致；**修复前的产物形态被 PostgreSQL 拒绝**（反向证明缺陷真实） | 全绿 |

PostgreSQL 层用 `@EnabledIfSystemProperty(named="nop.test.docker.enabled", matches="true")` 门控
（对齐既有 `TestPostgreSqlJsonComponent` 范式），默认跳过不拖慢常规构建；
镜像固定 `postgres:16` 而非 `latest`（`latest` 在受限网络下拉不到，会让测试变成依赖网络的脆弱断言）。
**注意** `jdbc:tc:` URL 分段约定是 `jdbc:tc:<类型>:<tag>:///库名`，把含冒号的完整镜像名塞进去会被解析成
"无法识别 tag"——这是实测踩到的坑，已在测试注释中写明。

**测试数真实性**：`TestEqlLateralCompile` 初版继承 `TestEqlCompileSql` 以复用 harness，
导致父类 26 个 `@Test` 在子类名下重跑、模块测试数虚增。已改为自带最小 harness（登记
`current_timestamp`/`upper`/`count` 三个被用到的函数，否则 `buildFunctionExpr` 会 NPE）。
现 `nop-orm-eql` = 171（既有）+ 24（新增）= **195**，0 fail。

### Phase 3 - CROSS JOIN 静默误解析（V2）

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`、`src/main/java/io/nop/orm/eql/{parse,compile}`

- Item Types: `Fix`

- [x] 根因是 `joinType_` 无 cross 分支 + `CROSS` 在 `unreservedWord_` + `ON` 可选三者叠加，导致 `cross` 被 `sqlAlias` 吃掉。修法须消除**静默误解析**，不能只加语法（加 `CROSS?` 前缀会与 `sqlSingleTableSource` 的可选 alias 产生新的 ANTLR 歧义，需验证）
- [x] 若补 `CROSS JOIN` 语法，注意 2256 的坑：同优先级备选必须合并为 token-set label
- [x] 确认修复后 `a cross join b` 与 `a CROSS JOIN b` 行为一致，且 `cross` 仍可作列名/别名
- [x] 若改动新增/删除 token，`.tokens` 与 `EqlLexer.java` 也会变（2256 因 token 集未动而未变，本 Phase 不同）

Exit Criteria:

- [x] **修复前红可独立复核**：基线失败形态已记录为可复制文本（当前 `select 1 from a cross join b` 解析成功并输出 `from a as cross join b`）
- [x] 新增测试：`cross join` 不再被解析为别名（断言编译 SQL 或断言明确的语法错误，取决于 Phase 1 裁定）
- [x] 新增测试：`cross` 仍可作列名/别名（防止修 V2 时破坏既有模型）
- [x] **端到端验证**：修复后的 `CROSS JOIN` 查询在 H2 实跑通过
- [x] 再生成产物已同步，`git diff --stat src/main/java/io/nop/orm/eql/parse/antlr/` 与 g4 改动一致
- [x] 跨模块回归（2256 清单）全绿
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 3 根因实测与四层修复（2026-10-04）

**实测确认的缺陷机制**（AST dump，非推测）：`select 1 from a cross join b` 解析为
`SqlJoinTableSource(joinType=JOIN, left=Single(a, alias=cross), right=b)` —— 即 `cross` 被吃成
左表别名，剩下的裸 `JOIN` 匹配 `innerJoin_: INNER? JOIN`（`INNER` 可选）。
危害链：别名变成 `cross` → 源查询里 `a.xxx` 报 `unknown-column-name`；链式写法额外产生幻觉别名
（`a cross join b as cross join c`）。

**plan 预判被实测证实**：仅在 `joinType_` 加 `crossJoin_: CROSS JOIN` **不足以修复**——
实测加了之后 `a cross join b` 仍被解析为 `alias=cross` + `JOIN`。原因是两条路径
（alias=cross+JOIN / CROSS JOIN）都能完整解析，ANTLR ALL(*) 取最小备选号，alias 恒胜。
但该 grammar 规则**仍然必要**：它覆盖「左表已带别名」的情形（`from AppUser u cross join AppRole r`），
此时 `cross` 不再可能被可选 alias 吃掉，只能由显式规则匹配。**两者互补，缺一不可。**

**四层修复**：

| 层 | 文件 | 改动 | 覆盖情形 |
|---|---|---|---|
| grammar | `model/antlr/DMLStatement.g4` | `joinType_` 增 `crossJoin_: CROSS JOIN` | 左表**已带别名**：`AppUser u cross join AppRole r` |
| AST 常量 | `enums/SqlJoinType.java` | 新增 `CROSS_JOIN("cross join")` | 输出侧关键字 |
| 建树还原 | `ast/SqlJoinTableSource.java` 的 `normalize()` | 「别名恰为 cross + 无条件 JOIN」→ 复位别名并置 `CROSS_JOIN`；同时清理右操作数上残留的 `cross` 别名 | 左表**未带别名**：`AppUser cross join AppRole r`，ANTLR 仍会先吃 alias |
| visitor 映射 | `parse/EqlASTBuildVisitor.java` 的 `SqlJoinTableSource_joinType` | 显式映射 `ctx.crossJoin_()` | 漏掉这行会让 CROSS JOIN **静默降级成普通 JOIN**（CROSS 关键字从产物中消失）——实测踩到 |

选 `normalize()` 而非 transform/printer 层的理由：`normalize()` 由生成的 build visitor
在属性装配完成后立即调用，**早于任何作用域注册**；若等到 transform 才改，
`addAliasToScope` 已用错误别名注册了 scope name。

**V2b：修复 V2 后暴露的两个后继缺陷（plan 初版未预见）**

1. **`EqlTransformVisitor:727` 拒绝一切无条件 join**（`ERR_EQL_JOIN_NO_CONDITION`）。
   CROSS JOIN 按定义无 `ON`，故显式豁免 `CROSS_JOIN`。已加断言确保普通 join / left join
   漏写 `on` 仍照旧报错（放行不得放宽该校验）。
2. **`AstToSqlGenerator.printJoin` 把租户/逻辑删除过滤条件写进 `ON` 子句**，产出
   `cross join SIMS_CLASS as s on (c.TENANT_ID = ?) and (s.TENANT_ID = ?)` —— 非法 SQL，
   H2 实测 `Syntax error`。改为：CROSS JOIN 不调用 `addJoinFilter`，两侧表源不进入
   `filteredSources`，由 `printWhere` 统一收进 WHERE；产物变为
   `... cross join SIMS_CLASS as s where (c.TENANT_ID = ?) and (s.TENANT_ID = ?)`，语义等价。

**该修复过程中触发并修掉的一个高危回归（必须记录）**：
`addTableFilterForJoinTable` 里若**先置 `filterAlreadyAdded = true` 再跳过**，SELECT 级的
`addEntityFilter`（`:486`）会认为该表源缺省过滤「已处理」而整体跳过，结果 WHERE 只剩
`where 1=1 /*and*//*and*/`、**租户条件彻底丢失**，CROSS JOIN 退化为跨租户笛卡尔积
（实测行数 1 → 4）。修法是 CROSS JOIN 分支必须**在置位之前 return**。
已加两条回归钉死：`testCrossJoinAppliesTenantFilterToBothSides`（断言 SQL 中恰好出现 2 个
`TENANT_ID` 条件且 `cross join` 后无 `on`）与 `testCrossJoinRowCountMatchesFilteredSides`
（断言行数 == 过滤后两侧行数之积）。

**已知边界（已写入 owner doc）**：`a cross join b on <cond>` 被解释为「a 的别名是 cross +
带 on 的普通 join」（CROSS JOIN 无 `on` 是非标准写法）；链式 `a cross join b cross join c`
产出 `a cross join b join c`——末尾无条件 `JOIN` 语义等价于 CROSS JOIN，非逐字round-trip。

**测试**：`TestEqlCrossJoinGrammarParse`（15，parse/AST 面）+ `TestEqlCrossJoinCompile`（9，编译面，
自带 harness 以免继承 `TestEqlCompileSql` 造成测试数虚增）+ `TestEqlCrossJoinEndToEnd`（9，H2 实跑）。
两个 compile 测试类都需在最小方言中登记 `current_timestamp`——`DialectImpl` 构造即调用
`buildFunctionSql("current_timestamp")`，缺该函数会 NPE（实测踩到）。

**回归基线更新**：`nop-orm-eql` 171→220、`nop-orm` 210→232，8 目标模块合计 1466→1537，0 fail。

### Phase 4 - 无输出 / 畸形输出语句收敛（V3 / V4）

Status: completed
Targets: 按 Phase 1 裁定，可能涉及 `model/antlr/*.g4`、`model/ast/io/nop/orm/eql/ast/EqlAST.xjava`、`src/main/java/io/nop/orm/eql/{compile,sql}/`

- Item Types: `Fix`

- [x] 按 Phase 1 裁定处置 `sqlCollectionAccessExpr`（实现降级或删除语法 + AST 节点）
- [x] 按 Phase 1 裁定处置 `SqlCommit` / `SqlRollback`，含 `statementKind` 连带处理
- [x] 处置后 `git status` 中不得残留 `EqlASTKind` 枚举值与实际节点类不一致的情况

Exit Criteria:

- [x] **修复前红可独立复核**：基线失败形态已记录（当前 `where coll[x > 1].count() > 2` → `where collx > 1 > 2`；`commit` → 空产物）
- [x] 若裁定为实现：新增测试断言输出 SQL 语义正确，且 Phase 1 的映射表描述的映射关系被测试覆盖
- [x] 若裁定为删除：新增语法可达性测试断言这些语法现在**解析失败**（复用 `parse/TestEqlTumbleGrammarParse.java` 的 grammar 可达性测试范式）
- [x] **无静默跳过**：删除路径下无残留 printer 默认分支吞掉节点；`EqlASTKind` 与节点类、Visitor 方法三者一致（grep 可验证）
- [x] `EqlASTNode.toSqlString()` 旁路未被误伤（复用 Phase 0 固化的基线测试）
- [x] 跨模块回归（2256 清单）全绿
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 4 实测与 V4 裁定修订（2026-10-04）

**V3 按裁定删除**（改法与 Phase 1 一致，无偏差）：
删 `BaseRule.g4` 中 `sqlExpr_simple` 的 `| sqlCollectionAccessExpr` 备选与规则本体、
删 `EqlAST.xjava` 的 `SqlCollectionAccessExpr`、删手写的 `SqlCollectionAccessExpr.java`
（`_gen/_SqlCollectionAccessExpr.java` 等再生产物由再生成自动清除）。
Phase 1 补记的实测结论在此得到验证：删除前该写法在编译通道**已经**是带错误码的
`unknown-column-name`（`[...]` 内容被当列名解析），静默损坏只发生在 `toSqlString()` 旁路；
删除后两个通道都变成干净的解析错误。

**V4 裁定由「删除」修订为「显式失败」——附实测理由**：

按原裁定删除 grammar 备选后**编译失败**，报错为
`_EqlASTBuildVisitor.java:991: List<SqlDmlStatement> 无法转换为 List<SqlStatement>`。
根因：xgen 的 list 规则按**备选分支的最小公共父类**推导元素类型——
`sqlStatements_` 的元素类型取自 `sqlStatement` 的备选分支，原为
`sqlDmlStatement | sqlTransactionStatement` → `SqlStatement`；
删掉唯一的非 DML 分支后收窄为 `SqlDmlStatement`，而 `SqlProgram.statements` 声明为
`List<SqlStatement>`，泛型不变性导致赋值失败。

两条可选出路各有明确代价：
- 收窄 `SqlProgram.statements` 为 `List<SqlDmlStatement>` ⇒ **变更公开 API 返回类型**，
  并需改 `nop-metadata-service` 的 `SqlSelectFieldExtractor:79` 与
  `SqlColumnLineageExtractor:135`（把本计划范围扩大到 nop-metadata）；
- 保留一个不可匹配的 grammar 分支以凑出 LUB ⇒ 制造死语法（违反本计划自身目标）。

故取 plan Goals 允许的第三种终态「显式失败」：AST 节点与 grammar 全部保留，
在 `EqlCompiler.compile` 的 transform **之前**判 `instanceof SqlTransactionStatement` 抛
`ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT`（错误信息指向 `ITransactionTemplate`）。
这同样消除了「编译产物为空字符串且不报错」的静默无操作，且**不动任何公开签名**。
修订已同步回 Phase 1 裁定表、映射表与 design 文档 §3.5/§3.6/§四。

**顺带修掉的空产物路径**：`AstToEqlGenerator` 原先没有 `visitSqlCommit`/`visitSqlRollback`，
落到 Visitor 默认分支被静默吞掉（`toEql=[]`）。补上两个 override 输出关键字后，
`toSqlString()` 旁路的 round-trip 无损——编译通道已 fail-fast，但该旁路仍会被用于
调试/打印 AST，不能留空产物。

**再生成一致性**：`git diff --stat` 显示 `ast/{EqlASTKind,EqlASTVisitor,EqlASTProcessor,EqlASTOptimizer}.java`、
`ast/_gen/_SqlCollectionAccessExpr.java`、`parse/antlr/*` 全部自动跟随模型变化，**零手改生成物**。
grep 校验：`CollectionAccess` 在 `*.java`/`*.g4`/`*.xjava` 中仅剩注释与测试方法名，无存活代码引用；
`EqlASTKind` 的 66 个条目**全部**有对应节点类（`in Kind but no class: none`）。
`EqlASTBuildVisitor` 中失效的 `SqlCollectionAccessExpr_collFuncName` override 已手工删除
（它是手维护文件，不随 grammar 自动清理，编译会报「不会覆盖或实现超类型中的方法」）。

**测试**：`TestEqlRemovedGrammarUnreachable`（16，V3 删除可达性 + V4 round-trip 非空 + 防误伤）、
`TestEqlTransactionStatementFailsFast`（10，V4 编译期错误码 + 错误信息指向 + 普通 DML 不受影响）。

### Phase 5 - 死字段与未判空强转加固（V5 / V6）

Status: completed
Targets: `model/ast/io/nop/orm/eql/ast/EqlAST.xjava`、`src/main/java/io/nop/orm/eql/{sql,compile,ast}/`

- Item Types: `Fix`

- [x] 按 Phase 1 裁定处置 `SqlOrderByItem.nullsFirst`（接线或删除）与 `collate`（纯死字段，删除候选）
- [x] `AstToSqlGenerator.appendWithColumns`（`:531-568`）的 `exprMeta` 判空——触发前提是 `WITH RECURSIVE`（`:532`），不是初版断言的裸 `SELECT *`
- [x] `EqlCompiler.java:93-99` 顶层 projections 的无条件强转加固
- [x] `ast/SqlSelect.java:70-71` 的 `getProjectionByExprMeta` 无条件强转加固
- [x] 三处加固必须给出明确的失败语义（抛 `NopException` + 既有错误码，或补判空走安全分支），不允许 `catch` 后继续
- [x] 若删除 AST 字段，`ast/_gen/**` 与 `ast/{Visitor,Optimizer,Processor,Kind}` 由再生成自动跟随，不手改

Exit Criteria:

- [x] **修复前红可独立复核**：三处强转各自的触发输入与当前异常形态已记录（可复现）
- [x] 每处加固有对应测试：触发输入得到明确错误码或安全结果，**不是**另一种异常
- [x] AST 模型改动后 `./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests` 重新生成，`git diff --stat ast/` 显示 Visitor/Processor/Kind/_gen 自动跟随
- [x] **接线验证**：删除的字段在全仓无残留引用（grep 覆盖 `sql/`、`compile/`、`parse/`、`ast/`、`nop-orm/`、`nop-dao/`）
- [x] **无静默跳过**：无新增空方法体 / `continue` 跳过 / 吞异常
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0
- [x] 跨模块回归（2256 清单）全绿
- [x] owner doc 语法清单已与 grammar 实际接受度对齐（先按 Phase 6 的表格化要求建立清单载体）
- [x] `ai-dev/logs/` 当日条目已更新

#### Phase 5 实测（2026-10-04）

**V5 按裁定删除两个死字段**：`EqlAST.xjava` 的 `SqlOrderByItem` 去掉 `String collate` 与
`Boolean nullsFirst`，并删掉 `AstToEqlGenerator#visitSqlOrderByItem` 中按 `nullsFirst`
打印 `nulls first/last` 的分支。`_gen/_SqlOrderByItem.java`（-56 行）由再生成自动跟随。
grep 校验：`SqlOrderByItem` 的这两个字段在全仓无残留引用；剩余的 `getCollate`/`getNullsFirst`
命中项分属**另外两个活字段**——`SqlTypeExpr.collate`（`CAST(x AS type COLLATE y)`）与
`OrderFieldBean.nullsFirst`（bean 路径），均不受影响。

**V6 实测修正了 plan 的一处定位错误**：plan 把裸 NPE 归到
`AstToSqlGenerator#appendWithColumns:546/548`，但加断言后实测栈显示真正的抛出点是
**`AstToSqlGenerator#visitSqlExprProjection:148`**——`exprMeta` 为 null 却直接调
`getColumnNames()`。该方法 `:149` 已经在判 `getColumnNames()` 是否为 null，
说明作者预期过"取不到列名"，但漏了 `exprMeta` 本身为 null 的情形。
故 V6 实际加固**四处**（plan 记三处）：

| 处 | 位置 | 改法 |
|---|---|---|
| V6a | `appendWithColumns` 的 projection 强转 + `exprMeta` 解引用 | 显式 `instanceof` + 判空，失败抛 `ERR_EQL_CTE_COLUMN_NAME_UNRESOLVED` |
| V6b | `EqlCompiler` 顶层 SELECT 投影强转 | 显式 `instanceof`，失败抛 `ERR_EQL_UNSUPPORTED_PROJECTION_TYPE` |
| V6b' | `EqlCompiler` 的 `Guard.notNull(exprMeta,"fieldMeta")` | 换成带错误码的 `NopException`（`Guard` 抛的是无错误码异常） |
| V6c | `SqlSelect#getProjectionByExprMeta` 强转 | 非表达式投影 `continue` 跳过——返回 null 本就是该方法既有契约 |
| **V6d（本轮新发现）** | `AstToSqlGenerator#visitSqlExprProjection:140/148` | 判空后抛 `ERR_EQL_UNSUPPORTED_PROJECTION_TYPE` |

实测三个触发输入（均为**裸 NPE**、无错误码无定位）在修复后全部变成带错误码失败：
`with recursive t as (select 1 union all select 2 from t) ...`、
`... (select * from AppUser o union all select 1 from t) ...`、
`... (select o.* from AppUser o union all select 1 from t) ...`。
即修复前 **EQL 的递归 CTE 整体不可用**——plan 初版把 V6a 描述为"边缘隐患"低估了严重性。

**测试**：`TestEqlRecursiveCteFailFast`（7，四个触发形态不得抛裸 NPE + 普通查询/顶层 `select *`/
非递归 CTE 三条防误伤）。先写红测试确认 3 个裸 NPE，再加固转绿。
`scan-hollow-implementations.mjs --module nop-orm-eql --severity high`：Critical/High/Medium/Low
**均为 0**，退出码 0。

**回归**：`nop-orm-eql` 171→252、`nop-orm` 210→232，8 目标模块合计 1466→**1569**，0 fail。

### Phase 6 - driver 死代码与 schema 无消费节点（V7 / V8 / V9）

Status: planned
Targets: `nop-orm/.../driver/ICollectionPersistDriver.java`、`driver/jdbc/JdbcCollectionPersistDriver.java`、`nop-kernel/nop-xdefs/.../orm/dialect.xdef`、`nop-dao/.../dialect/model/_gen/`

- Item Types: `Fix`

- [ ] V7：确认 `ICollectionPersistDriver.flushCollectionChange` 全仓零调用点后删除接口方法与 `JdbcCollectionPersistDriver` 的空实现；同时复核 `ICollectionPersister.flushCollectionChange`（`CollectionPersisterImpl.java:171-183`）的 global cache 失效语义是否需要保留（当前是**唯一**真实职责）
- [ ] V7：`ai-dev/design/nop-orm-scope-and-non-goals.md` §3.3 声称 `ICollectionPersistDriver.flushCollectionChange` "只负责失效全局缓存"——实际是纯空体、什么都不做，失效在 `CollectionPersisterImpl`。该段是 design-doc drift，须改正
- [ ] V7：修正 `docs-for-ai/02-core-guides/orm-model-design.md` 的"集合变更语义"段。当前表述"若子实体没有指向父实体的反向外键属性，集合成员变更不会产生任何 SQL"与实现不符——`bindOwnerProps` 会按 `<join>` 写 FK。真实静默前提是 `<join>` 为空
- [ ] V8：按 Phase 1 裁定处置 `<upsertHandler>`。若删除，须重新生成 `_DialectModel` 并同步 `_dump/**/dialect.xdef` 副本与 `reflect-config.json`
- [ ] V9：按 Phase 1 裁定处置 `NATURAL` / `USING` 死 token
- [ ] 顺带审计 `dialect.xdef` 的 4 个兄弟 class-name 节点（`sqlExceptionTranslator` / `paginationHandler` / `geometryTypeHandler` / `jsonTypeHandler`）是否有同类零读取问题（已知 `paginationHandler` 有真实读取：`AstToSqlGenerator.java:93`）

Exit Criteria:

- [ ] **证明**：删除后 `ICollectionPersistDriver.flushCollectionChange` 全仓零引用（grep 覆盖主源码与测试），`nop-orm` 模块测试全绿
- [ ] **证明**：`exists*` 不在本 Phase 范围（已裁定移出），本 Phase 不新增相关改动
- [ ] design 文档 §3.3 的描述与代码一致（`flushCollectionChange` 的真实职责归属已改正）
- [ ] owner doc 的"集合变更语义"段与实现一致，且给出的判据（`<join>` 是否为空）可在代码中定位
- [ ] V8 若删 schema 节点：`_DialectModel` 再生成完成，`_dump/**/dialect.xdef` 副本一致性已校验
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase 7 - owner doc 语法清单表格化

Status: planned
Targets: `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

- Item Types: `Fix`

- [ ] 把 `### EQL 完整语法支持`（当前 `:32` 段）的散文描述改写为可逐条比对的表格：`语法项 | grammar 接受 | 目标方言可用 | 错误码/备注`
- [ ] 表格内容以 Phase 2–6 修完后的 live grammar 与实测结果为准，不沿用初版断言
- [ ] 表格每行的"目标方言可用"列必须区分「方言能力位」「函数登记」「未开启」三种不同原因（现有文档 `:34`、`:50` 已有此区分的先例表述）

Exit Criteria:

- [ ] 表格存在且每行有实测或代码依据（引用 `file:line` 或测试名）
- [ ] 表格与 grammar 实际接受度逐条一致，无"文档宣称支持但语法不接受"的条目
- [ ] `LATERAL` 条目存在（Phase 2 产出），含词法登记变更的迁移说明
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 当日条目已更新

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部勾选后，才能把 `Plan Status` 改为 `completed`。关闭流程见 `00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [ ] V1–V9 全部落到终态（修复 / 显式失败 / 删除），无第四态残留
- [ ] 所有被证伪的初版断言（D5 CCE、D6 症状、D10 症状、D11 defect、CROSS JOIN/FETCH FIRST/QUALIFY 缺口、`SqlExistsExpr.not` 死字段）已在本 plan 中明确记录证伪证据，不留误导性表述
- [ ] 生成幂等性已证明：无手改任何生成物，`parse/antlr/**`、`ast/_gen/**`、`ast/{Kind,Node,Visitor,Processor,Optimizer}.java` 均由源模型再生产出
- [ ] 必要 focused verification 已完成，每个修复都有"修复前失败形态可独立复核"的证据
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步到 live baseline（`eql-and-database-compatibility.md`、`orm-model-design.md`、`ai-dev/design/nop-orm-scope-and-non-goals.md`）
- [ ] 独立子 agent closure audit 已完成，`Closure` 段已写入 evidence
- [ ] **Anti-Hollow Check**：`scan-hollow-implementations.mjs --module nop-orm-eql --severity high`、`--module nop-orm --severity high`、`--module nop-dao --severity high` 退出码均为 0
- [ ] `./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm,nop-persistence/nop-dao -am` 通过
- [ ] 跨模块回归（2256 清单）通过：`nop-orm-eql` → `nop-orm` → `nop-ai/nop-ai-service` → `nop-datav/nop-datav-service` → `nop-sys/nop-sys-dao` → `nop-wf/nop-wf-core,nop-wf-service`
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 代码规范检查通过（`checkstyle` 未绑定构建生命周期，需手动核 import 顺序：`node ai-dev/tools/check-import-order.mjs`）
- [ ] `ai-dev/logs/` 收口记录已写入（文本一致性核对，Rule #19）

## Deferred But Adjudicated

### `EqlASTOptimizer` / `EqlASTProcessor` 死表面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 死表面判断成立但**删除文件无效**（`//__XGEN_FORCE_OVERRIDE__` 每次构建覆盖），必须改 `nop-kernel/nop-codegen` 的 ast 模板；该模板同时服务 XLang，属跨模块 codegen 变更，按 AGENTS.md Protected Areas 需 plan-first + 独立 plan。V1–V9 的收敛不依赖这两类被移除
- Successor Required: `yes`
- Successor Path: 独立 plan（codegen 模板变更 + `nop-xlang` 回归）

### `IJdbcTemplate.exists*` 接口契约清理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 生产实现 `JdbcTemplateImpl.java:469-540` 五个方法全部为真实现（方言模板优先 + `DatabaseMetaData` 回落），无用户可见行为缺陷。剩余问题是"接口 default 抛 UOE 而非抽象方法"的契约瑕疵 + 五方法零调用点裁定，属接口清理而非 live defect
- Successor Required: `no`

### 多行 `INSERT ... VALUES (a,b),(c,d)` / `UPDATE ... FROM` / `DELETE ... USING` / `SKIP LOCKED` / `PIVOT` / `COLLATE`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 均为能力缺口而非 live defect——现有等价能力可通过子查询、逗号连接、批量写入路径（`JdbcBatcher` / `SessionBatchActionQueue`）表达，不导致错误行为。`COLLATE` 在 ORDER BY 项上 grammar 无任何对应物（初版引用的 `BaseRule.g4:184` 是 `sqlExpr_simple` 的注释，与 ORDER BY 无关，该证据已作废）
- Successor Required: `yes`
- Successor Path: 独立"EQL 语法能力补齐"successor plan

### 事务隔离级别

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 归属 `ITransactionTemplate` 层新增能力（design 文档 G1），与 EQL 无关。当前 live baseline 确认无任何隔离级别 API
- Successor Required: `yes`
- Successor Path: 独立 plan，落点 `nop-dao` 事务层

### alias 在 EQL 中的关联表投影路径测试覆盖

- Classification: `watch-only residual`
- Why Not Blocking Closure: `TestEqlExtFields` 已覆盖纵表 alias 的投影/算术/WHERE/ORDER BY；关联表列投影 alias 走 `resolveColName` → `getAliasPropPath` → `resolvePropPath` 同一段代码（`EqlTransformVisitor.java:1441-1466,799`），与显式路径无独立实现分支
- Successor Required: `no`

## Non-Blocking Follow-ups

- `docs-en/dev-guide/orm/` 下的英文 ORM 文档若涉及 EQL 语法清单，需与 Phase 7 的表格化结果保持一致
- `EqlASTNode.toSqlString()` 旁路在未 transform AST 上的输出形态目前无守护断言（Phase 0 已固化基线，长期应转为常驻测试）

## Closure

Status Note: 待填写
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- 待填写