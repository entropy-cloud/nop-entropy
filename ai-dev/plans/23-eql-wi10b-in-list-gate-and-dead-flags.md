# 23 EQL WI10b — `IN` 列表长度门禁实现 + BUG-13 死标志三档处置

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`「WI10a / WI10b」节 + BUG-13 三档处置表；`ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`（Status: active，定稿 commit `edbeaf0968`）；`ai-dev/plans/22-eql-wi10a-in-list-strategy-design.md`；Current Baseline 全部代码事实**截至 commit `777e822ace`（2026-10-03）**在本工作区逐文件实测
> Related: `ai-dev/plans/03-eql-wi1-class-a-defects.md`（实施中）、`ai-dev/plans/19-eql-wi2-class-b-defects.md`（已定稿待实施）、`ai-dev/plans/20-eql-wi3-dialect-config-fixes.md`（已定稿待实施）、`ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md`（已定稿待实施）

## Purpose

把 roadmap WI10b 收口为两个结果面：

1. **`IN` 列表长度门禁**：按 WI10a 定稿策略（统一阈值 1000、超阈值显式报错、不做改写、方言无关）在 **EQL 编译路径**（`nop-orm-eql`）与 **`SqlBuilder` 集合展开路径**（`nop-core`）各落一个检查点，配新错误码与可调高的全局配置项，并以五条测试证明契约。
2. **BUG-13 死标志三档处置**：对 roadmap 点名的 14 个存量死标志完成「接线 / 补 accessor+消费者 / 软弃用」三档处置，逐个给出消费者方法与负向用例（或按本 plan Phase 0 的显式裁定改档），并更新死标志台账。

roadmap 门控矩阵对 WI10b 的 Protected Area 判定：**plan-first**（xdef 硬删属性会破坏现有 XML 校验）。本 plan 因此采用软弃用路径（**不硬删任何 xdef 属性**），并以本 plan 通过 draft review 作为该 plan-first 门禁的满足形式。

## Current Baseline

> 所有锚点为**符号定位**（类#方法 / 常量名）。file:line 仅用于截至 commit `777e822ace` 实测时点的参考，WI1-WI4 实施后必然漂移——执行时以 Phase 0 的锚点复核为准（见 Phase 0 最后一项）。
> **勘误记录（2026-10-04 执行时点）**：WI1-WI5 已全部收口后的 live 复核结论见裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-04-wi10b-phase0-adjudications.md` §0.2（漂移登记表）——含 accessor 现状更新（`isSupportFullJoin` 已由 WI2 落地，无 accessor 集合从 5 个变 4 个）与全部符号锚点的逐项复核结果；本节正文的 file:line 不再逐一维护，以裁定文档为准。

### 执行时点依赖状态（截至 commit `777e822ace`，2026-10-03 实测）

- WI10a 已 `done`：策略设计文档 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md` 状态 active（独立审查 approve-with-nits，commit `edbeaf0968`），roadmap WI10b Exit Criteria ② 已按其定稿结论修订为「报错-无改写-方言无关」。**roadmap WI10b EC ① 已由 WI10a 满足**，本 plan 不重复产出策略文档，只在 Phase 0 做契约内的落点裁定。
- **WI1 实施中**（`ai-dev/plans/03-eql-wi1-class-a-defects.md`，Plan Status 仍 active）：BUG-1（LIKE..ESCAPE，commit `151c48e12e`）、BUG-2（LATERAL + `supportLateral` 四层接线：xdef / `DialectFeatures` retention / `IDialect.isSupportLateral` / `DialectImpl` + 方言 XML 显式配置，commit `8312d79d54`）、BUG-3（for update 走方言锁模板 + 空模板编译期报错，commit `43483a10ce`）、BUG-5（`sqlIntervalExpr` PG 形态文法分支 `5afea1d3f3` + 构建期归一化与 `sqlIntervalLiteral` 骨架 `6b229a2224`）**已落地**；BUG-10（`cross join` 文法）/BUG-14（集合下标）待实施，WI1 整体未收口。WI1 剩余项将改 `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（`joinType_`+`crossJoin_`）、`SqlJoinType`、`EqlASTBuildVisitor`、`nop-stream/nop-stream-sql` 的 `StreamSqlCompiler`。
- **WI2 plan 已定稿待实施**（`19-`）：将改 `AstToEqlGenerator#visitSqlStringLiteral/#visitSqlBooleanLiteral`、`printSelect` 回显、`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` `fullJoin_`、**补 `IDialect.isSupportFullJoin` accessor + `DialectImpl` 实现**、给 `mysql.dialect.xml`（及 mariadb 按 Phase 0 裁定）显式配 `supportFullJoin="false"`、在 `EqlTransformVisitor` 落 full-join 能力门（先例 `checkWindowFrameFeature`）、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` `dataTypeName_`、BUG-15 防护（可能新增 `OrmEqlErrors` 错误码）、`OrmEqlConstants`。
- **WI3 plan 已定稿待实施**（`20-`）：将改 `mysql/oracle/h2/duckdb/dm/postgresql/mysql5.7/mariadb` 各 `*.dialect.xml`、补 `IDialect.isDefaultNullsFirst` + `DialectImpl`（accessor 层）、`getIntersectKeyword` 覆写、`DialectSqls`、`AstToSqlGenerator#visitSqlLikeExpr` 周边（`:501-515` 区域）、`db2.dialect.xml`/`db2.selector.yaml`、能力矩阵快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json`。
- **WI4 plan 已定稿待实施**（`21-`）：将改 `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java`（缓存键）、`ILoadedOrmModel` javadoc、可能 `EqlCompileContext`。WI4 落地后 `EqlCompiler#compile` 的 transform 环节从「可选 opt-in」变为「固定环节」。
- **以上四个 WI 都将在 WI10b 之前实施。** 本 plan 所有落点判据用符号定位；与它们的文件交集与 rebase 规则见下文「与 WI1-WI4 的文件交集与 rebase 规则」。

### `IN` 列表现状（WI10a 设计 §二 已定稿，本 plan 逐条实测确认）

- **零截断**：`AstToEqlGenerator#visitSqlInValuesExpr`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java`；截至基线 commit 位于 `:816`，WI1 的 interval 文法落地所致漂移，符号定位为准）把 `SqlInValuesExpr#getValues()` 交给 `#printList`（裸 for 循环，无阈值判断）。`AstToSqlGenerator`（同目录）`extends AstToEqlGenerator` 且**无 `visitSqlInValuesExpr` 覆写** ⇒ 执行 SQL 与 `EqlASTNode#toSqlString()` 回显共享同一段无界打印。
- **`NOT IN` 与 `IN` 同节点**：文法 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlInValuesExpr` 规则带 `not=NOT?` ⇒ 同一个 `SqlInValuesExpr` 节点，单点门禁天然覆盖两者。
- **`SqlMultiValueExpr` 是另一个节点、不在门禁范围**：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlMultiValueExpr: LP_ values=sqlInValues_ RP_` 虽与 IN 列表共享 `sqlInValues_` 内部规则，但产物是 `SqlMultiValueExpr`（`(a,b)` 值列表），由 `AstToEqlGenerator#visitSqlMultiValueExpr` 无条件打印。WI10a 门禁的对象是 `SqlInValuesExpr`；`SqlMultiValueExpr` 的门控属 `supportRowValueConstructor` 处置（Phase 2），两者不得混为一谈。
- **EQL 编译链**（WI4 落地前）：`EqlCompiler#compile` → `EqlASTParser#parseFromText` → （可选 `IEqlAstTransformer`）→ `EqlTransformVisitor#transform` → `AstToSqlGenerator` 打印 → `CompiledSql`。`EqlTransformVisitor` 今天**没有** `visitSqlInValuesExpr` 覆写。方言能力门先例：`EqlTransformVisitor#checkWindowFrameFeature` 抛 `OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（`nop.err.eql.dialect-not-support-feature`，param `ARG_DIALECT`/`ARG_FEATURE`）；feature 名常量先例 `OrmEqlConstants.FEATURE_SUPPORT_LATERAL` 等；returning 门先例 `EqlTransformVisitor#visitSqlUpdate` 内 `dialect.isSupportReturningForUpdate()` 校验。
- **`SqlBuilder` 集合展开路径**：`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java` 的 `SqlBuilder#paramCollection` → `SqlBuilder#spreadParams`。上游消费点：`SqlBuilder#in(Collection)`、`SqlBuilder#in(owner,name,value)`（`FilterBeanToSQLTransformer#visitCompareOp` 的 `FILTER_OP_IN`/`FILTER_OP_NOT_IN` 落到这里）、`SqlBuilder#paramEx`；**`spreadParams` 另有非 IN 消费方 `CollectSqlOutput`**（同包 `CollectSqlOutput.java:52` 直接调 `sb.spreadParams(...)`，截至基线 commit）。⚠️ `spreadParams` 循环体对 `Object[]` / 嵌套 `Collection` 元素按**行分组**展开（`(a,b),(c,d)` 形态）——计数口径（顶层元素 vs 展开后 `?` 总数）不是自明的，见 Phase 0 挂载点裁定项。⇒ 挂载点选择是 Phase 0 的显式裁定项（设计 §3.4 原文候选：`spreadParams` 全局拦 vs `FilterBeanToSQLTransformer` 仅 filter 路径拦）。
- **配置载体先例**：`nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java` 的 `varRef(s_loc, "nop.core.<kebab>", Integer.class, default)` 模式（如 `CFG_CORE_STAT_SQL_CACHE_MAX_SIZE` 默认即 1000）。WI10a 设计 §3.1/§3.5 已裁定：配置必须落 `nop-core`（`CoreConfigs`）或 `nop-commons`——`nop-orm-eql` 依赖 `nop-core`，反向不成立，`DaoConfigs`/`OrmEqlConfigs` 无法被两条检查路径同时读到。
- **错误码归属先例**：`OrmEqlErrors`（`nop.err.eql.*`）与 `CoreErrors`（`nop.err.core.*`）各自 `ErrorCode.define(...)`；param 常量命名先例 `ARG_MAX_ITEMS`（`CoreErrors`）、`ARG_DIALECT`/`ARG_FEATURE`（`OrmEqlErrors`）。设计 §3.4 已裁定：两条路径**不允许共用一个模块的码**（EQL 路径归 `OrmEqlErrors`、`SqlBuilder` 路径归 `CoreErrors`）。
- **回显与语料基线**：`EqlASTNode#appendTo` 硬编码 `new AstToEqlGenerator(sb)`（无 pretty 开关），回显语义 = 忠实转储，门禁不得触碰（设计 §3.4）。语料基线快照 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md` + 回归测试 `TestEqlCorpusBaseline`（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/`）已由 WI0 落地；7 个含 `<eql>` 的 `sql-lib.xml` 中 `-pl` 可覆盖 3 份（`:nop-job-dao` NopJobTask、`:nop-auth-service` test、`:nop-orm` test），另两份（`nop-orm-demo` chapter 3/4/5、`nop-spring-simple-demo`）由 roadmap 级 EC 第 8 条覆盖。
- **合成方言测试先例**：`TestEqlTranslationSemantics#buildDialect`（`nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/`）——负向用例在 XML 无 false 载体时的既有范式。

### BUG-13 死标志 14 个（2026-10-03 逐个实测）

accessor 现状（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`，实现 `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`，冻结生成类 `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectFeatures.java`）：

- **有 `IDialect` accessor 的 9 个**：`isSupportSavePoint` / `isSupportTransaction` / `isSupportTruncateTable` / `isSupportUpdateTableAlias` / `isSupportDeleteTableAlias` / `isSupportRowValueConstructor` / `isSupportUpdateFromJoin` / `isSupportSequence` / `isSupportILike`。
- **无 accessor、只有 `_DialectFeatures` Boolean getter 的 5 个**：`getSupportFullJoin` / `getSupportRightJoin` / `getSupportDeleteFromJoin` / `getSupportOutputForUpdate` / `getSupportNullsFirst`。
- 全仓 grep（排除 `_gen` 与 `IDialect`/`DialectImpl` 自身）：上述 14 个标志的 accessor **零生产消费方**。

逐标志调研结论（三档 + owner；"接线点" 一律点名目标 visitXxx 方法）：

| 标志 | accessor | 门控的生成器行为今天存在吗 | 接线点（目标方法） | 负向用例方言载体 | owner |
|---|---|---|---|---|---|
| `supportFullJoin` | 无 | 存在且**无条件输出**：`SqlJoinType.FULL_JOIN` 可解析，`AstToEqlGenerator#visitSqlJoinTableSource` 打印 `getJoinType().getText()`，`EqlTransformVisitor#markMainSource` 已处理 FULL_JOIN | `EqlTransformVisitor` full-join 能力门（照 `checkWindowFrameFeature` 先例） | mysql/mariadb 显式配 `false`（WI2 plan 已裁定） | **WI2**（plan 19-；WI10b 只做落地核对） |
| `supportILike` | 有 | **flag 本身无消费者**：全仓 `isSupportILike` 除 accessor 链（`IDialect`/`DialectImpl`）外**零命中**（含测试）；ilike 的真实门是 `AstToSqlGenerator#visitSqlLikeExpr` 的 ilike 消费路径在 `dialect.getFunction(ILIKE)` 取不到函数时抛 `ERR_EQL_NOT_SUPPORT_ILIKE`——门在**函数表**不在标志；WI3 plan（`20-`）明写其第 9 项 = 补 h2/pg 的 `ilike` **函数注册**，不是改标志、更不是接标志 | 若要真实接线 = 新增标志消费点（Phase 0 终裁 vs 改档/废弃，登记理由）；**不得在台账产生虚假"已接线"登记** | `default.dialect.xml` false / `h2` true（配置断言载体现成） | **WI10b Phase 0 终裁**（WI3 只负责函数注册，不处置标志）；纳入 Phase 3 的 roadmap EC③ 修订 |
| `supportNullsFirst` | 无 | 消费点未建：`SqlOrderByItem.nullsFirst` 只能程序化赋值，文法不接受 `NULLS FIRST/LAST` | `AstToSqlGenerator` ORDER BY 打印路径 + 文法接受（WI7 范围） | 4 个 XML true（duckdb/h2/oracle/postgresql），负向载体由 WI7 裁定 | **WI7**（todo，未立项；WI3 已落 `isDefaultNullsFirst` accessor 层并书面移交）。WI10b 只核对移交链、**不重复实施** |
| `supportUpdateFromJoin` | 有 | **不存在**：`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `sqlUpdate` 规则只有 `tableName (AS? alias)? assignments (where)? (RETURNING)?`，无 FROM 子句；`_SqlUpdate` 无 from/join 字段 ⇒ 该语法 EQL 根本不可解析，无任何生成器输出可门 | 未来文法扩展（路径 ⓒ，plan-first）后的 `EqlTransformVisitor#visitSqlUpdate` / `AstToSqlGenerator#visitSqlUpdate`；**今天不存在可接线行为** | 不可解析 ⇒ 今天无静默错误风险 | **WI10b Phase 0 裁定改档**（候选：契约登记 + 接线契约测试证明 / 转软弃用；不得伪造消费者） |
| `supportTruncateTable` | 有 | **不存在**：EQL 无 TRUNCATE 语法；roadmap「明确延后项」ledger 对 "`TRUNCATE` 进 EQL" 为**永久否决**；`nop-core`/`nop-dao`/`nop-orm` 生产代码零消费 | 无 EQL 目标（永久否决 ⇒ 永不会有） | 不适用 | **WI10b Phase 0 裁定改档**（永久否决 ⇒ 接线档不成立，候选转软弃用或"能力声明 + 测试证明"档） |
| `supportDeleteFromJoin` | 无 | **不存在**：`sqlDelete` 规则单表（`DELETE FROM tableName AS? alias? where?`），`_SqlDelete` 无多表字段 | 同 `supportUpdateFromJoin` | 同上 | **WI10b Phase 0 裁定改档** |
| `supportRightJoin` | 无 | 存在且**无条件输出**：`SqlJoinType.RIGHT_JOIN` 可解析，`AstToEqlGenerator#visitSqlJoinTableSource` 打印，`EqlTransformVisitor#markMainSource` 已处理 | `EqlTransformVisitor` join 能力门（`checkWindowFrameFeature` 先例）+ 补 `IDialect.isSupportRightJoin` accessor | **全部 XML 继承 `default` 的 `true`，无 false 载体** ⇒ 负向用例用合成方言（`TestEqlTranslationSemantics#buildDialect` 先例） | **WI10b**（接线档） |
| `supportUpdateTableAlias` | 有 | **半存在**：别名可解析（`sqlUpdate` 的 `AS? alias`）且 transform 期真实消费（`EqlTransformVisitor#visitSqlUpdate` 经 `#makeTableAlias` 注册进 table scope，列可写 `o.col`），但打印被抑制（`AstToEqlGenerator#visitSqlUpdate` 置 `ownerShouldBeIgnored`，输出 SQL 不含别名）——单表 UPDATE 下语义无损 | ⚠️ 打印器按 flag 输出 = "不支持"时静默抑制（无编译期报错负向用例、违反无静默跳过）⇒ 该形态不成立（Phase 0 约束）；实质两向：`EqlTransformVisitor#visitSqlUpdate` 编译期门（别名存在 && `!isSupportUpdateTableAlias` ⇒ 报错）**+ 迁移说明** vs **改档**。⚠️ 全部 XML 现值 `false` ⇒ 编译期门 = **全方言拒绝今天可解析的 update 别名写法**（BC 变更，迁移说明必含） | 负向（不支持）载体：任意 XML 方言（全 false）；正向（支持）载体：合成方言 | **WI10b**（Phase 0 两向终裁） |
| `supportDeleteTableAlias` | 有 | 同上（`EqlTransformVisitor#visitSqlDelete` + `AstToEqlGenerator#visitSqlDelete` 同形态） | 同 `supportUpdateTableAlias` 的两向约束（编译期门 + 迁移说明 vs 改档；打印器形态不成立） | 同上 | **WI10b**（Phase 0 两向终裁） |
| `supportRowValueConstructor` | 有 | **部分存在**：`SqlMultiValueExpr`（`(a,b)`）可解析且 `AstToEqlGenerator#visitSqlMultiValueExpr` 无条件打印；"真 row value constructor 比较"（`(a,b)=(1,2)` / `(a,b) IN ((1,2))`）能否落到该节点并产出合法 SQL 由 Phase 0 实测裁定 | 候选：`AstToSqlGenerator#visitSqlMultiValueExpr` 覆写门 或 `EqlTransformVisitor` 比较上下文门 | **全部 XML 继承 `default` 的 `true`，无 false 载体** ⇒ 合成方言 | **WI10b**（接线档，形态 Phase 0 裁定） |
| `supportOutputForUpdate`（档 2） | 无 | **无消费者**。消费点调研结论：`UPDATE..OUTPUT` 确为 **MSSQL 形态**——唯一 XML 载体 `mssql.dialect.xml` 配 `true`，`dialect.xdef` javadoc 明写 "Update语句支持output子句"；EQL 现状是 `EqlTransformVisitor#visitSqlUpdate` 用 `isSupportReturningForUpdate()` 门 `returning` 子句（mssql 未配 returning ⇒ false ⇒ `update..returning` 在 mssql 被拒），而 `AstToEqlGenerator#visitSqlUpdate` 对 returning 的打印是硬编码、无 OUTPUT 分支 | 候选 A：`EqlTransformVisitor#visitSqlUpdate` 中 "returning 被拒 && `supportOutputForUpdate`" 分支（需先设计 MSSQL `OUTPUT inserted./deleted.` 形态，v1 可裁定不做 lowering）；候选 B（roadmap 原文给的轻选项）：补 accessor + 测试证明（配置断言 + 语义文档）。**Phase 0 裁定** | mssql（true）载体存在；其余方言 false | **WI10b**（档 2：补 accessor + 真实消费点**或**补测试证明） |
| `supportSavePoint`（档 3） | 有 | **零调用方**（全仓含测试零命中） | — | — | **WI10b**（软弃用：xdef javadoc 标 deprecated，属性保留） |
| `supportTransaction`（档 3） | 有 | **零调用方**（同上） | — | — | **WI10b**（软弃用，同上） |
| `supportSequence`（档 3） | 有 | `isSupportSequence` 唯一调用方是测试 `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialect.java` 的 `testSqls`；`getCreateSequenceSql`/`getSequenceNextValSql` 仅该测试调用 | — | — | **WI10b**（软弃用，同上） |

xdef 现状：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef` 的 `<features>` 元素声明全部 14 个属性；javadoc 块（`@supportUpdateTableAlias` / `@supportDeleteTableAlias` / `@supportOutputForUpdate` 等逐行注释）在 `<features>` 前的注释块内——**软弃用的落点就是该注释块加 `@deprecated` 行**，不动属性声明本身（硬删 = plan-first 破坏 XML 校验，本 plan 不做）。`default.dialect.xml` 的 `<features>` 现配 `supportTransaction="true" supportSavePoint="true"`、`supportSequence="false"`。

### 与 WI1-WI4 的文件交集与 rebase 规则

WI1-WI4 将在 WI10b 之前实施，以下文件会被它们先改过，WI10b 执行时**必须基于实施后的 live repo 工作，不得回退它们的改动**：

| 文件 | WI1-WI4 的在途改动 | WI10b 的改动 | rebase 规则 |
|---|---|---|---|
| `.../sql/AstToEqlGenerator.java` | WI1：LATERAL 回显（已落）；WI2：`#printSelect`、字面量回显 | **零改动**（回显忠实转储，只在 `AstToSqlGenerator` 子类覆写） | — |
| `.../compile/EqlTransformVisitor.java` | WI2：full-join 能力门；WI3：intersect 相关无（accessor 在 nop-dao）；WI4：无直接改动（`EqlCompileContext` 在 nop-orm） | IN 门禁首选挂载点（`#visitSqlInValuesExpr` 新覆写）；join/别名能力门 | WI10b 的门以**新增覆写/新增私有校验方法**形式追加；与 WI2 的 full-join 门共存于同类，风格对齐 `checkWindowFrameFeature` |
| `.../compile/EqlCompiler.java` | WI4：transform 固定环节 | IN 门禁备选挂载点（Phase 0 裁定） | 若 WI4 已改 `#compile` 结构，锚点以实施后代码为准 |
| `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java` + `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java` | WI1：`isSupportLateral`（已落）；WI2：`isSupportFullJoin`；WI3：`isDefaultNullsFirst`、`getIntersectKeyword` 覆写 | WI10b 需补的 accessor：`isSupportRightJoin`、`isSupportDeleteFromJoin`（若裁定补）、`isSupportOutputForUpdate` | **只追加**新 accessor，不改既有；javadoc 照 `isSupportLateral` 先例（写明报错契约） |
| `nop-kernel/nop-xdefs/.../dialect.xdef` | WI1：`sqlIntervalLiteral`（pending） | 软弃用 javadoc 注释行（不改属性声明） | 只加注释行；若 WI1 的 interval 元素已在文件中，避同一注释块冲突以 git rebase 为准 |
| `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/*.dialect.xml` | WI1（lateral 显式配置，已落）、WI2（mysql/mariadb fullJoin=false）、WI3（returning 纠错等 7 项改值） | **计划内零改值**（WI10b 不翻任何既有能力值；负向载体用合成方言） | 若 Phase 0 裁定必须新增某个 XML 显式值（如显式 `supportRightJoin="false"` 于某测试专用场景），走 Phase 0 书面记录，且不碰 WI2/WI3 的值 |
| `.../OrmEqlErrors.java` | WI2：BUG-15 可能新增错误码 | 新增 IN 列表超长错误码 | 只追加 |
| `.../OrmEqlConstants.java` | WI2：feature 常量 | 新增 `FEATURE_SUPPORT_IN_LIST_LENGTH` 一类常量（命名 Phase 0 定） | 只追加 |
| `nop-persistence/nop-orm/.../factory/LoadedOrmModel.java` | WI4：缓存键 | **零改动**（门禁在编译链内随编译执行，不进缓存键；缓存语义限制见 Phase 1） | — |

**统一 rebase 规则**：① WI10b 每个 Phase 开工前先核对本 plan 引用的符号在 live repo 仍成立（Phase 0 末项一次性复核 + 每 Phase 开工抽查）；② 行号漂移一律以符号重定位并在本 plan 勘误，不重写 baseline；③ 与 WI1-WI4 语义冲突时（例如 WI2 已给 `EqlTransformVisitor` 加了同型门），复用其门形态而不是再发明一种。

## Goals

- `IN` 列表长度门禁按 WI10a 设计落地：统一阈值 1000（常量默认 + `CoreConfigs` 全局配置可调高、无关闭取值）、超阈值编译/构建期显式报错（新错误码、`actual`/`max` 等参数）、门禁方言无关、不做任何改写；`toSqlString()` 回显路径零变化。
- 两条路径（EQL 编译、`SqlBuilder` 集合展开）都有检查点，执行必过门禁；错误契约一致（错误码语义、param 集合、默认阈值同一配置项）。
- BUG-13 的 14 个存量死标志全部落到明确处置（最终档位以 Phase 0 终裁为准）：接线档每个标志有「消费者方法 + 负向用例」；测试证明/改档档有等价处置证据；软弃用档落 xdef javadoc；`supportFullJoin`（WI2）/`supportNullsFirst`（WI7）的 owner 边界核对清楚，无双 owner、无重复实施；`supportILike` 按 Phase 0 终裁处置（`ilike` 函数注册 ≠ 标志接线，台账不得虚假登记"已接线"）。
- roadmap WI10b 的 EC③ 措辞、BUG-13 三档表、Current Baseline 死标志行与 WI 状态行按终裁修订并留原地注记（对齐 WI10a 对 EC② 的先例，commit `edbeaf0968`），消除 plan↔roadmap 文本冲突。
- 死标志台账与 owner doc（`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`）与 live baseline 一致，含 WI10a 设计 §3.2 要求的迁移说明必含项。

## Non-Goals

- 不做任何超阈值改写（分批 OR / 临时表 / 自动参数化 / 静默截断 / warn-only）——WI10a 设计 §四 已全部拒绝。
- 不做 per-dialect 阈值或方言 XML 阈值属性（设计 §3.1）。
- 不新增方言可插拔公共接口（`IInListLimitHandler` 之类）——设计 §3.5，升级触发条件写入设计，v1 单一覆写点。
- 不硬删 `dialect.xdef` 的任何属性（plan-first 红线；`TRUNCATE 进 EQL` 亦为 ledger 永久否决项，本 plan 不为其开语法口）。
- 不实施 `supportUpdateFromJoin`/`supportDeleteFromJoin`/`supportNullsFirst` 对应的**新语法**（UPDATE..FROM / DELETE..USING / `NULLS FIRST/LAST` 文法）——那是未来文法 WI（plan-first，路径 ⓒ）与 WI7 的范围；WI10b 只处置标志本身。
- 不改任何既有方言 XML 能力值（WI3/G-4 的批准边界不属本 plan）。
- 不处理 MSSQL 语句级参数总数（2100）取证——登记为 Deferred（设计 §3.3 unverified 项）。
- 不动 `nop-orm-demo` / `nop-spring-simple-demo` 两份不可 `-pl` 覆盖的语料（roadmap 级 EC 第 8 条覆盖）。

## Scope

### In Scope

- `nop-kernel/nop-core`：`CoreConfigs` 新增一个 IN 列表阈值配置项；`CoreErrors` 新增一个错误码；`SQL.java` 的 `SqlBuilder` 集合展开路径门禁（挂载点 Phase 0 裁定）；对应单测。
- `nop-persistence/nop-orm-eql`：`OrmEqlErrors` 新增错误码；`OrmEqlConstants` 新增 feature 常量；EQL 编译路径门禁（挂载点 Phase 0 裁定）；五条测试；`supportRightJoin`/`supportUpdateTableAlias`/`supportDeleteTableAlias`/`supportRowValueConstructor` 的接线（形态 Phase 0 裁定）与负向用例。
- `nop-persistence/nop-dao`：`IDialect`/`DialectImpl` 按 Phase 0 裁定补 accessor（`isSupportRightJoin` 必补；`isSupportDeleteFromJoin`/`isSupportOutputForUpdate` 按裁定）；方言配置断言测试。
- `nop-kernel/nop-xdefs`：`dialect.xdef` javadoc 注释块为 3 个软弃用标志加 `@deprecated` 说明（不改属性声明、不改绑定行为）。
- `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`：IN 契约表 + 迁移说明 + 能力门登记表 + 死标志台账更新。
- `ai-dev/design/eql-cross-db-compat/`：Phase 0 裁定文档（一份）。

### Out Of Scope

- WI1-WI4 范围内的全部在途改动（见交集表）。
- WI5-WI12 的任何语法/函数工作。
- 真库执行类取证（MSSQL 参数总数等）。
- `nop-dao` 新 public 接口（除 `IDialect` accessor 追加外）。

## Execution Plan

### Phase 0 - 前置裁定（纯裁定，不改产品代码）

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增一份裁定文档）；`ai-dev/logs/`

- Item Types: `Decision`、`Proof`

- [x] **死标志 owner 核对表**：对 14 个标志逐个核对 owner 落地状态——`supportFullJoin`（WI2 是否已实施并按其 plan 落 accessor + 门 + mysql/mariadb false）、`supportILike`（WI3 是否已收口 `ilike` **函数注册**——注意函数注册 ≠ 标志接线，该标志本身仍零消费者）、`supportNullsFirst`（WI7 是否已立项；WI3 的 `isDefaultNullsFirst` accessor 是否已落）、本 plan 自有的接线/档 2 标志以 Current Baseline 表为基线复核 live repo。核对表写入裁定文档；WI2/WI3 未实施时按「登记过的 successor 所有权移交」记录（WI3 plan 对 `defaultNullsFirst` 的先例措辞），不得因此阻塞本 plan。（裁定文档 §0.2/§1：WI2/WI3 均已收口，`isSupportFullJoin` accessor 已落、消费门在位；`supportILike` 函数注册已收口但标志仍零消费；WI7 `planned` 未实施、WI3 移交链在位）
- [x] **终裁 5 个"行为不存在/部分存在/无消费者"标志的处置**（裁定文档 §2）：`supportUpdateFromJoin`→改档（档 2 契约登记 + 文法级测试证明 + 触发条件）；`supportTruncateTable`→改档（档 2 能力声明，ledger 永久否决 EQL 消费）；`supportDeleteFromJoin`→改档（档 2 契约登记 + 补 accessor）；`supportRowValueConstructor`→**接线**（`EqlTransformVisitor#visitSqlMultiValueExpr` 覆写门，三形态可达性文法实测）；`supportILike`→**改档（禁止接标志）**——决定性证据：postgresql 继承 `supportILike="false"` 但 WI3 已注册 ilike 函数且工作正常，接标志即破坏两个已收口方言；台账不得虚假登记"已接线"
- [x] **终裁 `supportRightJoin` 接线形态**：门扩展 `EqlTransformVisitor#checkJoinTypeFeature`（WI2 已建）+ 补 `IDialect.isSupportRightJoin`；负向载体 = **合成方言**（全部 XML 继承 default true，RIGHT JOIN 为全支持标准能力，无任何真实方言应配 false 的一手证据，按裁定规则取合成方言，`TestEqlTranslationSemantics#buildDialect` 先例）
- [x] **终裁 `supportUpdateTableAlias`/`supportDeleteTableAlias` 接线形态**：**改档（档 2）**——编译期门被否决（全方言 false ⇒ 门=拒绝全方言今天语义无损可解析的写法，纯 BC 破坏零收益；打印抑制是"接受并归一化"非静默错误）。BC 影响清单已盘点：三份 `-pl` 语料实测零 update/delete 别名写法，零语料影响
- [x] **终裁 `supportOutputForUpdate` 消费点**：**候选 B**（补 accessor + mssql 配置断言 + 语义文档；v1 不做 OUTPUT lowering，升级触发条件已写入）；与 roadmap 档 2 原文一致；"运行时消费"接线验证的**显式豁免及理由已写入裁定文档 §2.7**（scan-hollow P5 未实现，豁免由 Phase 2 手工 gate 把关）
- [x] **终裁 EQL 路径门禁挂载点**：`EqlTransformVisitor#visitSqlInValuesExpr` 新覆写（check-first；构造器 +name 传语句名，全仓唯一实例化点已实测）；缓存/非缓存路径行为一致性论证已记录；检查在编译管线、不在打印器
- [x] **终裁 `SqlBuilder` 路径挂载点**：`SQL.SqlBuilder#spreadParams` **全局拦**（全仓调用面实测：paramCollection 链 + `CollectSqlOutput.java:52` 直调，paramCollection-only 与 filter-only 均不完备）；**计数口径 = Collection 顶层元素个数**（标量集合时==`?` 数；行组按 1 计，与 `supportRowValueConstructor` 处置正交核对完成）；空集合 `(NULL)` 行为不变
- [x] **终裁错误码归属与命名**：EQL=`nop.err.eql.in-list-too-large`（param：itemCount/maxItems/stmtName/dialect）；SqlBuilder=`nop.err.core.sql.in-list-too-large`（param：itemCount/maxItems[/name]）；两码不共用。⚠️ 执行期偏差：消息语言取**中文**（偏离 plan 原文"英文"）——owner doc `error-handling.md:120` 现行裁定"存量中文模块新增码延续中文、同文件不中英混排"，WI1/WI2/WI3 新增码先例一致；已在裁定文档 §5 登记
- [x] **终裁配置载体**：`CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE`（`nop.core.sql.in-list-max-size`，Integer 默认 1000）；无关闭取值（≤0 算术上=拒绝全部而非关闭）；单键两路径同读
- [x] **开工前置 + 锚点复核（排队约束，硬性）**：`03-`(WI1)/`19-`(WI2)/`20-`(WI3)/`21-`(WI4) 四份 plan 的 Plan Status 均 `completed`（2026-10-04 逐一开文件核验，含各自 closure audit 证据）——开工门满足，Phase 1 放行；全部符号锚点 live 复核完成，漂移登记于裁定文档 §0.2（accessor 集合 5→4 为唯一实质漂移，系 WI2 合法落地）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：14 标志 owner 核对表（含 WI2/WI3/WI7 落地状态）、5 个"行为不存在/部分存在/无消费者"标志的终裁（每个有点名 visitXxx 或改档理由，含 `supportILike` 的"真实接线 vs 改档"结论）、两个挂载点终裁（EQL 侧 + SqlBuilder 侧，含计数口径与 `CollectSqlOutput` 直调事实）、错误码归属与命名、配置载体键名、`supportOutputForUpdate` 消费点终裁（若裁候选 B，同时写入对"运行时消费"接线验证的显式豁免及理由）、别名两标志的两向终裁与 BC 影响清单。（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi10b-phase0-adjudications.md`，§0/§1/§2/§3/§4/§5/§6）
- [x] WI1-WI4 四份前置 plan 的 Plan Status 核验结论已记录（全部 `completed` 方可放行 Phase 1；否则本 plan 停留在 Phase 0 并登记阻塞原因）。（裁定文档 §0.1：四份全 completed，Phase 1 放行）
- [x] 每个 Phase 0 裁定都能回答"执行者照此能直接写代码吗"——无留给执行期再发明的设计开口（设计 §3.4/§3.5 移交本 plan 的两个开口——`spreadParams` vs `FilterBeanToSQLTransformer`、方法级落点——均已关闭）。（裁定文档 §7 执行要点汇总）
- [x] 本 Phase 纯裁定不改产品代码：`No owner-doc update required`（裁定文档本身即交付物）
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - `IN` 列表长度门禁实现 + 五条测试

Status: completed
Targets: `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java`、`nop-kernel/nop-core/src/main/java/io/nop/core/CoreErrors.java`、`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java`（按 Phase 0 挂载点）、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java`、`.../OrmEqlConstants.java`、`.../compile/EqlTransformVisitor.java` 或 `.../compile/EqlCompiler.java`（按 Phase 0 挂载点）、`nop-core`/`nop-orm-eql` 测试、`ai-dev/logs/`

- Item Types: `Feature`（门禁、配置、错误码）、`Proof`（五条测试）

- [x] `CoreConfigs` 新增阈值配置项：常量默认 1000、`varRef` 模式、`@Description` 英文或中文照模块惯例、无"关闭检查"取值（配置调高即部署自担证据责任——设计 §3.1）。（`CFG_CORE_SQL_IN_LIST_MAX_SIZE`＝`nop.core.sql.in-list-max-size`，Integer 默认 1000，@Description 中文照模块惯例并写明"最小有效值为 1"）
- [x] `OrmEqlErrors` 新增 EQL 路径错误码 + `CoreErrors` 新增 `SqlBuilder` 路径错误码（Phase 0 命名），`.param(...)` 按 Phase 0 裁定的 param 集合；错误消息英文。（⚠️ 执行期偏差：消息取**中文**——owner doc `error-handling.md:120` 存量中文模块裁定 + WI1/WI2/WI3 先例，已在裁定文档 §5 登记；param 集合按裁定：EQL=itemCount/maxItems/stmtName/dialect，Core=itemCount/maxItems/name）
- [x] EQL 路径门禁：按 Phase 0 挂载点实现，对 `SqlInValuesExpr#getValues()` 计数（字面量与 marker 统一计数），超阈值抛 `NopException` + 新错误码；**不区分方言**（门禁方言无关）、**不改写**、**不触碰 `AstToEqlGenerator`/`AstToSqlGenerator` 的任何打印方法**。（`EqlTransformVisitor#visitSqlInValuesExpr` 新覆写，check-first，构造器 +stmtName 由 `EqlCompiler` 传入）
- [x] `SqlBuilder` 路径门禁：按 Phase 0 挂载点实现，超阈值抛同一契约错误（`CoreErrors` 码、同一配置项默认值）；空集合 `spreadParams` 的既有 `(NULL)` 行为不变（空 IN 列表语义不在本策略范围——设计 §3.4）。（`SQL.SqlBuilder#spreadParams` 入口全局拦，顶层元素计数；空集合分支先于门禁；现状锁定：`in(empty)` 经 paramCollection+spreadParams 叠加输出 ` in ((NULL))`——测试按现状断言，门禁未改变该行为）
- [x] **测试一（阈值内正常）**：`<default-threshold>` 个元素的 `IN` 与 `NOT IN` 均正常编译、SQL 文本正确。（`testInListWithinThresholdCompiles`：999 元素 IN/NOT IN 编译，尾部元素断言）
- [x] **测试二（超阈值报错）**：EQL 路径超阈值编译期抛新错误码（断言具体错误码与 `actual`/`max` param 值，非泛化异常）；`SqlBuilder` 路径超阈值构建期抛 `CoreErrors` 新码（同断言标准）。（`testInListOverThresholdThrowsWithCodeAndParams`：码 + itemCount=1001/maxItems=1000/stmtName=testStmt/dialect；core 侧 `testSqlBuilderInDirectCallOverThresholdThrows` + `testFilterBeanInOverThresholdThrowsEndToEnd`：码 + itemCount/maxItems）
- [x] **测试三（`NOT IN` 同阈值）**：`NOT IN` 超阈值同样报错、同错误码，无单独放宽。（EQL 侧 `testNotInOverThresholdSameError`；core 侧 `testFilterBeanNotInOverThresholdSameError`）
- [x] **测试四（回显不变）**：超阈值的 `SqlInValuesExpr` 经 `EqlASTNode#toSqlString()` 仍完整转出（门禁不在回显路径）；`TestEqlCorpusBaseline` 回归无差异。（`testEchoToSqlStringUnchangedOverThreshold`：1001 元素全转出 1000 逗号；`TestEqlCorpusBaseline` 1/1 PASS 无 diff）
- [x] **测试五（基线无 diff）**：执行载体 = Closure Gates EC⑤ 的完整命令 `./mvnw test -pl :nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C`（三份 `-pl` 可覆盖语料 `:nop-job-dao`/`:nop-auth-service`/`:nop-orm` 随该命令闭包覆盖）；既有 EQL 用例在新门禁下全部通过且输出与**当前棘轮快照**一致（比对对象是执行时点的现行快照——WI1/WI2 实施后快照会被合法再棘轮，不与 WI0 原始快照比对；不声称 7 个全测，剩余两份由 roadmap 级 EC 第 8 条覆盖）。（Phase 1 执行载体 `-pl :nop-core,:nop-orm-eql -am` 全绿：nop-orm-eql 302|0（含 CorpusBaseline 1/1 与三份语料回显断言）、nop-dao 204|46skip|0、nop-core 855|2skip|0（surefire 汇总）；完整 EC⑤ 命令在终局执行）
- [x] 配置行为测试：调高配置后原超阈值查询可过（证明配置通路真实生效，接线验证）；配置不存在"关闭"取值（可省略——类型即保证，如需断言写入测试 javadoc）。（两侧各一条：EQL `testConfigRaiseAllowsOverThresholdList`（恢复默认后重新被拒，证非一次性放行）+ core `testConfigRaiseAllowsOverThresholdCollection`（同型）；"无关闭取值"的算术保证写于 CoreConfigs @Description 与裁定文档 §6）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 五条测试全部落地且绿（阈值内 / 超阈值报错 / `NOT IN` 同阈值 / 回显不变 / 基线无 diff），断言具体错误码与 param，不断言"没抛异常"敷衍形态。（surefire XML：`TestEqlInListLengthGate` 5|0、`TestSqlBuilderInListLengthGate` 7|0；红证据 `_tmp/wi10b/red-eql.txt`（3×"nothing was thrown"）+ `_tmp/wi10b/red-core.txt`（4×"nothing was thrown"））
- [x] **接线验证**（Minimum Rules #23）：配置项被两条检查路径在运行时真实读取（调高配置的用例通过）；`SqlBuilder` 路径门禁在 `FilterBeanToSQLTransformer#visitCompareOp` 的 `IN` 用例上真实触发（端到端从 filter bean 到报错）。（EQL `testConfigRaiseAllowsOverThresholdList` + core `testConfigRaiseAllowsOverThresholdCollection` + `testFilterBeanInOverThresholdThrowsEndToEnd`：filter bean → visitCompareOp → sb.in → spreadParams → 报错全链）
- [x] **端到端验证**（Minimum Rules #22）：EQL 路径从 `EqlCompiler#compile` 入口到抛错完整走通（不经手工构造打印器）。（两个超阈值用例均经 `new EqlCompiler().compile("testStmt", eql, ctx)` 入口）
- [x] **无静默跳过**（Minimum Rules #24）：两条路径的超阈值行为都是显式报错，无截断、无 warn-only、无吞异常。（`NopException` + 具名错误码，两侧超阈值用例断言异常类型与码）
- [x] 回显零变化证明：`toSqlString()` 对超阈值 AST 不报错且输出完整；`TestEqlCorpusBaseline` 无 diff。（`testEchoToSqlStringUnchangedOverThreshold`：1001 元素/1000 逗号全转出；CorpusBaseline 1/1 PASS）
- [x] `./mvnw test -pl :nop-core,:nop-orm-eql -am -T 1C` 全绿。（`_tmp/wi10b/phase1-full.log`：BUILD SUCCESS、MVN_EXIT=0；nop-core 855|2skip|0、nop-orm-eql 302|0、nop-dao 204|46skip|0，闭包内其余模块全绿）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中。（exit 0；`--module nop-core` 附测同为 exit 0）
- [x] owner doc 更新归 Phase 3 统一执行，本 Phase 标注 `owner-doc update deferred to Phase 3`；`ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - BUG-13 死标志三档处置

Status: planned
Targets: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/IDialect.java`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`（join 门、别名两标志的编译期门（若终裁接线）、`supportRowValueConstructor` 门，均按 Phase 0 终裁形态）、必要时 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`（仅 `supportRowValueConstructor` 若裁打印侧覆写时使用，及 `supportILike` 若 Phase 0 裁真实接线则其消费点落 `visitSqlLikeExpr` 区域一并纳入本 Targets；回显生成器零改动）、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef`（仅 javadoc 注释块）、`nop-dao`/`nop-orm-eql` 测试、`ai-dev/logs/`

- Item Types: `Feature`（新接线）、`Fix`（死标志收拢）、`Decision`（Phase 0 改档的落地）、`Proof`（负向用例、配置断言）

- [ ] **`supportRightJoin` 接线**：补 `IDialect.isSupportRightJoin` + `DialectImpl` 实现（照 `isSupportLateral`/window-frame 先例，javadoc 写明报错契约）；按 Phase 0 形态在 `EqlTransformVisitor` 落 join 能力门，抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `FEATURE_*` 常量；正向用例（right join 正常方言）+ 负向用例（Phase 0 载体，断言错误码）。
- [ ] **`supportUpdateTableAlias`/`supportDeleteTableAlias` 处置**：按 Phase 0 两向终裁落地——裁「编译期门」者：门 + 负向用例（断言错误码）+ 正向用例，BC 迁移说明项已在 Phase 0 记录、Phase 3 落档；裁「改档」者：按改档档位落地（xdef javadoc 标注或档 2 测试证明）。三份语料回归不受影响（或有影响则按 Phase 0 的 BC 清单处理，不得静默）。
- [ ] **`supportRowValueConstructor` 处置**：按 Phase 0 终裁落地（接线 + 负向用例，或改档的测试证明）。
- [ ] **`supportUpdateFromJoin`/`supportTruncateTable`/`supportDeleteFromJoin`/`supportILike` 处置**：按 Phase 0 终裁落地——每个标志要么有可核验的测试证明（契约登记/能力声明形态；`supportILike` 若裁"真实接线"则为新增标志消费点 + 负向用例），要么已转入软弃用档并完成 xdef javadoc 标注；台账登记必须与实际处置一致（`ilike` 函数注册 ≠ 标志接线）；**不允许不落任何处置结论**。
- [ ] **`supportOutputForUpdate`（档 2）**：补 `IDialect.isSupportOutputForUpdate` + `DialectImpl`（照 Boolean getter 读法先例）；按 Phase 0 终裁补真实消费点或补测试证明（mssql 配置断言 + 语义文档落 owner doc）；mssql 载体的行为断言。⚠️ 若裁候选 B（accessor + 测试证明、无生产消费点），Phase 0 裁定文档必须**显式豁免**该 accessor 的"运行时消费"接线验证并写明理由（`scan-hollow-implementations.mjs` 的 P5「unused public methods」未实现，探不到"有 accessor 无消费者"形态——该豁免只能靠本条手工 gate 把关）；或直接裁候选 A 消除豁免。
- [ ] **软弃用 3 个**：`dialect.xdef` javadoc 注释块为 `supportSavePoint`/`supportTransaction`/`supportSequence` 加 `@deprecated` 行（说明"预留未接线、保留配置兼容、勿新增消费依赖"语义）；属性声明与绑定行为零变化（`default.dialect.xml:17` 的 `supportTransaction="true" supportSavePoint="true"` 等既有配置照常可加载——配置加载回归测试证明）；**不硬删属性**。
- [ ] **跨 WI 标志核对收拢（不重复实施）**：核对 `supportFullJoin`（owner WI2）、`supportNullsFirst`（owner WI7）在本 plan 执行时点的落地状态；已落地者纳入死标志台账"已处置"；未落地者（如 WI7 未立项）按 WI3 plan 的移交链措辞登记为 successor-owned 中间态，**WI10b 不补 accessor、不建消费点、不写负向用例**（无双 owner）。`supportILike` 不在此档——其标志处置已归 Phase 0 终裁（WI3 只负责函数注册）；台账不得出现"supportILike 已接线（WI3）"这类与 live code 不符的登记。
- [ ] 每个本 plan 新接线的标志在能力矩阵快照 `ai-dev/audits/eql-wi0/dialect-capability-matrix.json` 的口径下无破坏（若本 plan 未改任何方言 XML，则显式记录"快照无需更新"）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 14 个标志逐一有最终档位结论，与 Phase 0 裁定文档一致；roadmap「接线（10）/ 补 accessor+消费者（1）/ 软弃用（3）」的账面与本 plan 的执行结果差异已显式记录（含改档理由），不是静默偏差。
- [ ] 本 plan 接线的每个标志（**按 Phase 0 裁定形态条件化验收**）：裁「编译期门」者——具名消费者方法 + 一条"该方言不支持时编译期报错"的负向用例（断言错误码）+ 一条正向用例；裁「测试证明/改档」者——Phase 0 裁定文档中的等价处置证据（契约登记测试或改档落地）。负向载体（合成方言或 XML）与 Phase 0 裁定一致。
- [ ] **接线验证**（Minimum Rules #23，限**接线档** accessor）：每个接线档新 accessor 被其消费点在运行时真实调用（负向用例即证明——accessor 返回 false 时门真的拦）；Phase 0 裁为「accessor + 测试证明」（候选 B）且无生产消费点的 accessor（如 `supportOutputForUpdate` 若裁 B）在本条**显式豁免**，豁免及理由已写入 Phase 0 裁定文档。
- [ ] 软弃用 3 个标志：xdef javadoc 已更新；既有方言 XML 加载回归通过（配置兼容未破坏）；全仓 grep 确认无新增消费依赖。
- [ ] **无静默跳过**：本 Phase 新增的每个门/分支在不支持时显式报错，不静默忽略。
- [ ] `./mvnw test -pl :nop-orm-eql,:nop-dao -am -T 1C` 全绿。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 零命中；`--module nop-dao` 同样零命中。（注：该工具的 P5「unused public methods」规则**未实现**——实测规则面为 P1/P2a/P2b/P3/P3b/P4/P6/P6b/P8，探不到"有 accessor 无消费者"形态；该形态的唯一防线是上一条的接线验证 gate。）
- [ ] owner doc 更新归 Phase 3 统一执行，本 Phase 标注 `owner-doc update deferred to Phase 3`；`ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - owner doc 同步：IN 契约 + 迁移说明 + 死标志台账

Status: planned
Targets: `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（WI10b EC③ 措辞 / BUG-13 三档表 / `:29` Current Baseline 行 / `:94` 状态行，四处修订 + 原地注记）、`docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`（如路由/锚点变化）、`ai-dev/logs/`

- Item Types: `Fix`（owner doc 与 live baseline 对齐）、`Proof`（doc-links 门禁）

- [ ] `eql-and-database-compatibility.md` 写入 **IN 列表契约表**：统一阈值 1000 及其唯一硬依据（Oracle `ORA-01795`）、配置项名称与调高方法（含"调高即自担证据责任"约定、无关闭取值）、两条路径的错误码、param 集合、`NOT IN` 同阈值、门禁方言无关不改写、回显不设门的边界。
- [ ] **roadmap 文本修订**（对齐 WI10a 先例：commit `edbeaf0968` + WI10a 设计 §五「定稿动作」的同一形态）：按 Phase 0 终裁结果修订 `ai-dev/backlog/eql-cross-db-compat-roadmap.md` 四处——① WI10b Exit Criteria ③ 措辞（接线数与档位按终裁，如「N 接线 / M 补测试证明或改档 / 3 软弃用」，以终裁为准）；② BUG-13 三档处置表（`:346-350` 区域）；③ Current Baseline `:29` 存量死标志行；④ `:94` WI10b 状态行（状态值按 roadmap 流转规则：本 plan draft review 通过后 `todo`→`planned`；closure audit 通过后 `planned`→`done`，不得提前）。修订处**原地留 2026-10 修订注记（若软弃用数因终裁变化，EC④ 一并按终裁修订）**（照 WI10a 对 EC② 的修订注记形态）。不加此项，closure audit 按 roadmap 原文核对时本 plan 与 roadmap 直接冲突。
- [ ] 写入 **迁移说明必含项**（WI10a 设计 §3.2 清单，对齐 WI9b "不接受留空"姿态）：哪些既有查询会开始失败（>1000 元素 IN，EQL 编译路径与 filter 展开路径两个面）、升级前评估步骤（预发环境按新错误码全量回归盘点）、阈值调高方法。破坏性变更裁定引用设计文档，不重写。
- [ ] 写入 **方言敏感输出点登记**（roadmap EC 第 4 条格式）：本 plan 新增的每个门——generator/校验方法、触发的能力标志、不支持时的错误码——逐行登记；与 WI1/WI2 已登记行合并成表。
- [ ] 更新 **死标志台账**：14 个标志的最终档位、消费者方法、负向用例位置、owner（WI2/WI3/WI7/WI10b）；`supportUpdateFromJoin` 等改档标志记录改档理由与未来触发条件（如 UPDATE..FROM 文法 WI 立项时必须接线该标志）。
- [ ] 若 `docs-for-ai/` 路由或实现锚点变化，同步 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（当前基线 3 项 pre-existing，见 roadmap Current Baseline；不顺手修无关文件，只比对新增量）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `eql-and-database-compatibility.md` 的 IN 契约表、迁移说明、能力门登记、死标志台账四项齐全，且与 live code 逐项对账无差异（错误码名、配置键名、方法名逐一可 grep 到）；死标志台账无与 live code 不符的"已接线"登记（`supportILike` 不得登记为已接线，除非 Phase 0 裁定真实接线且消费点已落）。
- [ ] roadmap 四处修订已落地且与 Phase 0 终裁一致（WI10b EC③ 措辞 / BUG-13 三档表 / `:29` 存量死标志行 / `:94` 状态行），修订注记原地可查；本次 roadmap 修订新增 broken link = 0。
- [ ] 迁移说明三要素（失败面 / 评估步骤 / 调高出口）无留空，与 WI10a 设计 §3.2 清单逐条对上。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 新增 broken link = 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] **roadmap WI10b EC ①**：策略设计文档已产出并被独立 audit——由 WI10a 交付满足（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md` active + plan 22 Closure 段证据），closure audit 复核其仍为 active 且与本 plan 实现一致。
- [ ] **roadmap WI10b EC ②**：阈值行为用例齐全——阈值内正常 / 超阈值编译·构建期报错（新错误码）/ 门禁方言无关、不做改写；策略文档已回答"字面量 vs 参数化"前提（设计 §3.3）。
- [ ] **roadmap WI10b EC ③**：接线档标志逐个有"消费者方法 + 负向用例"，改档/测试证明档有等价处置证据——**按 Phase 3 修订后的 roadmap 文本核对**（修订前原文与本 plan Phase 0 终裁的差异由原地修订注记解释，不构成冲突）。
- [ ] **roadmap WI10b EC ④**：3 个软弃用标志已标 deprecated 且 xdef javadoc 更新。
- [ ] **roadmap WI10b EC ⑤**：`./mvnw test -pl :nop-orm-eql,:nop-dao,:nop-metadata-service,:nop-job-dao,:nop-auth-service -am -T 1C` 全绿（⚠️ `-am` 闭包含 `nop-stream-runtime` 的既有失败 `TestStreamConnectorRegistryDiscovery` 1 项，与本 roadmap 零代码关联——"全绿"口径 = 本 WI 相关模块全绿 + 该既有失败与基线一致，见 roadmap Cross-Cutting）。
- [ ] **roadmap WI10b EC ⑥**：独立 closure audit 由**不同 `task_id`** 的子 agent 执行，evidence 写入本 plan 的 `Closure` 段。
- [ ] 所有 in-scope 破坏性变更（IN 门禁 + 按 Phase 0 裁定的别名门）的迁移说明已落 owner doc；不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [ ] 受影响的 owner docs 已同步到 live baseline（Phase 3 全勾）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/23-eql-wi10b-in-list-gate-and-dead-flags.md --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0（`--module nop-dao` 同样为 0）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0。
- [ ] 方言能力矩阵快照门禁不红（本 plan 若改方言 XML 则快照同步更新；未改则记录"无需更新"）。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）两条门禁路径从入口到抛错运行时连通（EQL：`EqlCompiler` 入口；SqlBuilder：filter `IN` 用例），（b）无空方法体/静默跳过/no-op 作为正常实现，（c）每个**接线档**新 accessor 被消费点真实调用；「accessor + 测试证明」档（无生产消费点，如 `supportOutputForUpdate` 若裁候选 B）按 Phase 0 裁定文档的豁免记录核对——`scan-hollow-implementations.mjs` 的 P5 未实现，探不到"有 accessor 无消费者"形态，唯一防线是本 gate。
- [ ] 文本一致性核对：`Plan Status`、各 Phase `Status`、各 Phase `Exit Criteria`、本 `Closure Gates`、`ai-dev/logs/` 收口记录五处一致。

## Deferred But Adjudicated

### MSSQL 语句级参数总数（2100）取证

- Classification: `watch-only residual`
- Why Not Blocking Closure: WI10a 设计 §3.3 已裁定本门禁只约束单 IN 列表元素个数；语句级参数总数的约束形态未取得一手引证，按 unverified 纪律不得进入契约；取证结论只影响 owner doc 证据等级列，不改变门禁行为（设计 §3.3 原文"WI10b 及其后"）。
- Successor Required: `no`
- Successor Path: 真库取证任务立项时并入（owner doc 证据等级列更新即可）。

### 其余方言 IN 列表上限一手取证

- Classification: `watch-only residual`
- Why Not Blocking Closure: 除 Oracle 外各方言上限均 unverified（roadmap 红线：DM8/TDengine 无一手文档）；统一阈值 1000 的取值不依赖这些取证（设计 §3.1 决策理由 1）。
- Successor Required: `no`
- Successor Path: 与上一条同一取证批次。

### `supportNullsFirst` 消费点（若 WI7 在本 plan 关闭时仍未立项）

- Classification: `moved to explicit successor ownership`（若 WI7 已立项实施则本条不适用，直接在台账记"已处置"）
- Why Not Blocking Closure: WI3 plan 已书面裁定 accessor/consumer 分层移交，roadmap WI10b 表原文点名「WI7 负责补 accessor + 接线 + 断言」——这是登记过的 successor 所有权移交，不是本 plan 的未完成项；WI10b 若越权实施即制造双 owner。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI7（Exit Criteria ③b）。

## Non-Blocking Follow-ups

- `SqlMultiValueExpr` 在比较上下文中的方言兼容性深化（若 Phase 0 对 `supportRowValueConstructor` 只做保守门，剩余场景登记于此）。
- 死标志台账的自动化巡检脚本（checker 化"accessor 存在 + 消费点存在 + 负向用例存在"三件套）——现有 `scan-hollow-implementations.mjs` 已覆盖半实现形态，台账巡检属增强。

## Risks And Rollback

- **IN 门禁是破坏性变更**（设计 §3.2 裁定接受）：回滚 = git revert 单独提交（门禁实现独立于死标志处置提交）；配置调高是部署侧零代码缓解。
- **别名门若裁定为编译期拒绝**：BC 影响已强制在 Phase 0 盘点三份语料；回滚同理独立提交。
- **与 WI1-WI4 的实施交错**：全部锚点符号定位 + Phase 0 末项复核 + 交集表 rebase 规则；WI10b 各 Phase 独立提交，可单独 revert。

## Closure

Status Note: （closure audit 通过后填写）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent，须不同于执行 task_id）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live code path 或 test name；`check-plan-checklist.mjs --strict` 退出码；`scan-hollow-implementations.mjs` 退出码；Anti-Hollow 调用链追踪结果；deferred 项分类检查）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
