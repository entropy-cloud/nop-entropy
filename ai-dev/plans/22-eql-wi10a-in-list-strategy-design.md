# 22 EQL WI10a — `IN` 列表长度上限策略设计（设计文档 + 独立审查）

> Plan Status: draft
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（Work Item Status WI10a 行 + Phase Details「WI10a / WI10b」节 + 依赖与门控矩阵 WI10a 行 + G-1）；全部代码锚点于 2026-10-03 在本工作区分支 `feat/eql-cross-db-compat` 逐文件实测核验（核验记录见 Current Baseline，行号零漂移）
> Related: `ai-dev/plans/02-eql-wi0-verification-skeleton-and-gates.md`（硬前置 WI0，已 completed）；`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`（G-1 已裁决，unverified 纪律与编译期报错默认形态的确立处）；successor = WI10b（按策略实现 + BUG-13 死标志三档处置）
> 执行约束：本 plan 为**纯文档计划**（不涉及任何 Java/资源代码变更），按 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 模板说明，Closure Gates 中的 `./mvnw` 构建验证条目已删除，不执行。

## Purpose

收口 roadmap WI10a 的 gate 交付：产出 `IN` 列表长度上限**策略设计文档**（阈值来源引用 + 字面量 vs 参数化的取舍 + 改写形态 + 与 `IPaginationHandler` 同类扩展点的关系 + 临时表生命周期与事务语义，即 roadmap 点名的五要素），存入 `ai-dev/design/eql-cross-db-compat/`，并经**独立子 agent 对抗性审查**后定稿。WI10b（按策略实现）开工的前提是本 plan 的五要素全部有书面裁决。

## Current Baseline

以下事实全部于 2026-10-03 实测核验（逐个开文件比对 file:line）。

### 缺陷现状（为什么需要策略）

- **零截断**：`AstToEqlGenerator.visitSqlInValuesExpr`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:802-819`）→ `printList(values, ",")`（同文件 `:212-223`，裸 for 循环）——无截断、无分批、无阈值。与 roadmap WI10a 节记载的行号**零漂移**。
- **执行路径共享同一无界打印**：`AstToSqlGenerator.java:67` `extends AstToEqlGenerator`，**无** `visitSqlInValuesExpr` 覆写（该类 `printList` 唯一消费点是投影 `:119`）⇒ `EqlCompiler.java:73` 的执行 SQL 与 `toSqlString()` 回显同路径。roadmap 只量化了回显路径（10000 个 5 位数字面量 ≈ 13.0 万字符）；执行路径同构是本 plan 核验时确认的补充事实，已写入设计文档。
- **回显硬编码**：`EqlASTNode.java:26-29` 硬编码 `new AstToEqlGenerator(sb)`，`toSqlString()`（`:31-35`）仅委托——与 roadmap 记载一致，零漂移。

### 参数化现状（设计必须回答的前提）

- 文法：`sqlInValues_: e=sqlExpr (COMMA_ e=sqlExpr)*`（`BaseRule.g4:163-164`；`# SqlInValuesExpr` 在 `:156`）——元素可为字面量或 `:name` 参数。
- 参数机制：`visitSqlParameterMarker` 打印 `?`（`AstToEqlGenerator.java:488-490`，经 `SQL.SqlBuilder.markValue`）；`paramIndex` 由 `EqlTransformVisitor.collectNames`（`compile/EqlTransformVisitor.java:220-236`）编号；`SqlParamTypeResolver`（`compile/SqlParamTypeResolver.java:69-130`）每 marker 产出一个 `ISqlParamBuilder`（`SimpleParamBuilder` 恒 1 值 `:24-28`；`EntityPropParamBuilder` 按 component/to-one 拆**固定元数** `:40-89`）；`CompiledSql.buildParams`（`:134-137`）→ `JdbcQueryExecutor.java:148`。⇒ **EQL 编译路径没有任何集合展开**，IN 列表 marker 个数是编译期常量。
- 真正的参数化 IN 列表在 filter-bean 路径：`FilterBeanToSQLTransformer.java:167-170` → `SQL.SqlBuilder.in(owner,name,value)`（`SQL.java:527-546`）→ `paramCollection`（`:724-726`）→ `spreadParams`（`:728` 起，每元素一个 `?` 绑定参数，`param` 见 `:421-433`）。⇒ MSSQL 2100 案例只在绑定参数路径"回来"。

### 扩展点先例（设计要素四的对照物）

- `dialect.xdef:25` `<paginationHandler>class-name</paginationHandler>`；`default.dialect.xml:11` / `mssql.dialect.xml:11` / `oracle.dialect.xml:12` 按方言配置；`DialectImpl.java:89-90` `newInstance(model.getPaginationHandler(), LimitOffsetPaginationHandler.INSTANCE)`（反射 + 默认回落，`newInstance` 本体 `:120-125`）；`IDialect.java:52` / `DialectImpl.java:200-202`；消费点 `AstToSqlGenerator.java:93`、`JdbcTemplateImpl.java:367`、`JdbcHelper.java:190`。同构先例：`geometryTypeHandler`/`jsonTypeHandler`（`DialectImpl.java:103`，xdef `:28/:30`）。
- `nop-dao` 新增 public 接口的纪律约束：`IUpsertHandler` 2026-08-22 删除先例（roadmap WI11 行引用 `ai-dev/audits/check/nop-dao.md:415`）；「AI Must Ask Before」第 1 条。

### 文档现状

- `ai-dev/design/eql-cross-db-compat/` 现只有 WI0 裁决文档一篇（`2026-10-03-wi0-gate-adjudications.md`）+ README；本 plan 交付第二篇。
- roadmap 文档 broken link 基线 = **3**（`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`，pre-existing，本 plan 不修，门禁只比对新增量）。
- roadmap WI10a 行状态为 `todo`（流转由本 plan Phase 3 收口推动，见 Phase 3）。

## Goals

- 五要素逐项有书面结论的设计文档落 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`：① 阈值来源引用（Oracle 1000 唯一硬依据 + MSSQL 2100 不适用的论证 + 其余方言 unverified 如实标注）；② 字面量 vs 参数化取舍（以实测代码事实回答"EQL 何时发参数化 IN 列表"，判定 MSSQL 案例何时回来）；③ 超阈值行为与改写形态裁定（含各形态语义/事务影响对比）；④ 与 `IPaginationHandler` 扩展点的关系裁定（含升级触发条件）；⑤ 临时表生命周期与事务语义分析（形态裁定 + 重提前置清单）。
- 设计文档给出**推荐方案 + 被拒备选及理由**，并把依赖后续真库取证的结论逐条标 unverified（roadmap 证据等级纪律）。
- 独立子 agent 对抗性审查完成，审查结论（0 Blocker 或 Blocker 清单）记录在案，修订后定稿。
- 本 plan 文档改动不引入任何新增 broken link。

## Non-Goals

- 不实现任何策略代码（阈值检查、错误码、配置项的实现全部归属 WI10b）；本 plan 只产出策略与契约。
- 不裁定 WI10b 的方法级落点（编译管线内具体挂载点、`spreadParams` vs `FilterBeanToSQLTransformer` 的取舍、错误码最终命名）——设计文档给出契约约束，方法级细节由 WI10b plan 裁定。
- 不做 MSSQL 绑定参数路径与其余方言上限的真库取证（设计文档只登记 unverified，取证归属 WI10b 及其后）。
- 不修改 `docs-for-ai/` owner doc（`eql-and-database-compatibility.md` 的阈值契约表由 WI10b 落地时同步——设计先于实现，`docs-for-ai/` 描述已实现行为）。
- 不顺手修 3 个 pre-existing broken link。
- 不改 roadmap 文件本身（WI10a 状态流转的回写是 Phase 3 收口项，届时按 roadmap 自身流转纪律执行）。

## Scope

### In Scope

- 新建 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`（设计文档本体）。
- 更新 `ai-dev/design/eql-cross-db-compat/README.md`（文档清单表登记新文档——目录 README 的既有约定"后续 WI 产生的新裁决追加到本目录，并在上表登记"）。
- 独立子 agent 审查的组织、结论登记与按结论修订。
- `ai-dev/logs/2026/10-03.md` 追加执行条目。

### Out Of Scope

- 一切 Java/XML/资源代码变更（WI10b）。
- BUG-13 死标志三档处置（WI10b 的另一半，见 roadmap）。
- 真库取证（MSSQL 参数化上限、其余方言 IN 上限）。
- `docs-for-ai/` owner doc 更新。

## Execution Plan

### Phase 1 - 设计草拟与事实核验（草拟）

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`、`ai-dev/design/eql-cross-db-compat/README.md`

- Item Types: `Decision`、`Proof`

- [x] 逐文件实测核验五要素相关代码锚点（零截断打印路径、参数化机制无集合展开、filter 路径集合展开、pagination 扩展点先例），核验结果写入设计文档「量化现状」与「核心设计」（`Proof`）
- [x] 设计文档覆盖五要素且每要素给出结论（`Decision`）：阈值来源表（Oracle 硬依据 / MSSQL 不适用论证 / 其余 unverified）、字面量 vs 参数化技术事实与结论、超阈值行为裁定（含改写形态对比表）、扩展点关系（含升级触发条件）、临时表生命周期与事务语义（5 条重提前置清单）
- [x] 设计文档含推荐方案（统一阈值 1000 + 显式报错 + v1 不新增公共接口）与被拒备选及理由（静默截断 / 自动分批 OR / 临时表 / per-dialect 阈值 / 自动参数化 / v1 handler 接口 / 打印器内门禁）
- [x] 依赖真库取证的结论逐条标注 unverified（MSSQL 绑定参数路径、MySQL/MariaDB/PG/H2/DuckDB/SQLite/DB2/TDengine 上限、Oracle 绑定参数上限、临时表 5 项），不支撑任何覆盖率表述（遵循 G-1 已裁决的 unverified 纪律）（`Proof`）
- [x] `ai-dev/design/eql-cross-db-compat/README.md` 文档清单表登记新文档（层级=Decision，状态=draft）
- [x] `ai-dev/logs/2026/10-03.md` 追加本 Phase 条目

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`，含「设计结论」「背景与动机」（含量化现状锚点）「核心设计」§3.1-§3.5（五要素逐一对应）「拒绝了什么」「与已有设计的关系」章节，且不含 "Proposed vs Current" 式演进叙事（符合 `ai-dev/design/00-design-writing-guide.md` 第 14 条/最终状态要求）
- [x] 设计文档引用的全部 file:line 锚点与 live repo 一致（起草时已逐个开文件核对：`AstToEqlGenerator.java:212-223/488-490/802-819`、`AstToSqlGenerator.java:67/93/119`、`EqlASTNode.java:26-29/31-35`、`BaseRule.g4:156/163-164`、`EqlTransformVisitor.java:220-236`、`SqlParamTypeResolver.java:69-130`、`SimpleParamBuilder.java:24-28`、`EntityPropParamBuilder.java:40-89`、`CompiledSql.java:134-137`、`JdbcQueryExecutor.java:148`、`FilterBeanToSQLTransformer.java:167-170`、`SQL.java:421-433/523-546/724-728`、`DialectImpl.java:89-90/120-125/200-202`、`IDialect.java:52`、`dialect.xdef:25/28/30`、三个 dialect.xml 的 paginationHandler 行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 且本次改动新增 broken link = 0（基线 3 项 pre-existing 不动）
- [x] `ai-dev/logs/2026/10-03.md` 对应条目已更新

### Phase 2 - 独立子 agent 对抗性审查（独立审查）

Status: planned
Targets: `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`（审查对象）；审查结论登记于本 plan 的 Phase 3 段与 `ai-dev/logs/`

- Item Types: `Proof`

- [ ] 启动**独立子 agent**（与起草不同的 task/session）对设计文档做对抗性审查，审查维度至少包括：五要素是否全部有结论且结论自洽；代码锚点是否与 live repo 一致；推荐方案与被拒备选的论证是否存在事实错误或遗漏备选（如"分批 OR 的三值逻辑等价性论证是否成立"）；unverified 标注是否诚实（有没有把未取证内容写成结论）；设计文档是否泄漏到"怎么写代码"层（违反 design writing guide）
- [ ] 审查产出结论（0 Blocker / 或逐条 Blocker-Major-Minor 清单）登记到 `ai-dev/logs/` 对应条目并摘录进 Phase 3 的修订记录
- [ ] 审查者身份与 session/task 标识记录在案（独立审查证据，供 Phase 3 收口与 roadmap WI10a 状态流转引用）

Exit Criteria:

- [ ] 独立子 agent 审查结论已登记（含审查者标识），Blocker = 0（或有 Blocker 已全部流入 Phase 3 修订项）
- [ ] 审查覆盖上列五类维度，每类有明确判定（通过/发现问题），不是一句"审查通过"
- [ ] `ai-dev/logs/` 对应条目已更新

### Phase 3 - 修订与收口（修订）

Status: planned
Targets: `ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（仅 WI10a 状态流转）、本 plan

- Item Types: `Fix`、`Follow-up`

- [ ] 按 Phase 2 审查结论修订设计文档；每条 Blocker/Major 的处置（如何修 / 为何不修及理由）记录在修订日志（`ai-dev/logs/` 对应条目）
- [ ] 修订后设计文档状态从 `draft` 转 `active`（设计文档头部状态字段），作为 WI10b 的约束基准
- [ ] roadmap WI10a 行状态按其流转纪律更新（`todo` → `planned` 于设计文档转 active 时；`planned` → `done` 于本 plan closure audit 通过时，不得提前）
- [ ] 本 plan 收口：逐条勾选全部 Exit Criteria 与 Closure Gates，运行 `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/22-eql-wi10a-in-list-strategy-design.md --strict` 退出码 0，由**独立子 agent**（不同 task_id）做 closure audit 并把 evidence 写入本 plan `Closure` 段
- [ ] `ai-dev/logs/2026/` 对应日期条目更新（收口记录）

Exit Criteria:

- [ ] Phase 2 的每条 Blocker/Major 在修订记录中有处置结论，无一条被静默忽略
- [ ] 设计文档状态字段为 `active`，且文档内容与修订记录一致（抽查任意两条修订能在文档中找到对应变化）
- [ ] roadmap WI10a 行状态与本 plan 状态文本一致（顶部 Plan Status、Phase Status、Closure Gates、daily log 四处无矛盾）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 且新增 broken link = 0（相对 3 项 pre-existing 基线）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/22-eql-wi10a-in-list-strategy-design.md --strict` 退出码 0
- [ ] 独立子 agent closure audit 完成且 evidence 已写入本 plan `Closure` 段
- [ ] `ai-dev/logs/` 对应条目已更新

## Closure Gates

> **纯文档计划**：不涉及任何代码变更（仅 `ai-dev/` 下文件），按指南说明删除 `./mvnw compile/test`、checkstyle 等构建验证条目，不执行。

- [ ] 设计文档存在且覆盖五要素（阈值来源 / 字面量 vs 参数化 / 改写形态 / 扩展点关系 / 临时表生命周期与事务语义），每要素结论可在文档中直接指认
- [ ] 独立审查结论已登记且 Blocker 全部处置（无静默忽略）
- [ ] 设计文档中所有依赖真库取证的结论均标注 unverified，无一处用未取证内容支撑"已修正/已确认"结论
- [ ] 被拒备选（静默截断 / 分批 OR / 临时表 / per-dialect 阈值 / 自动参数化 / v1 handler 接口 / 打印器内门禁）均有理由记录，防止重复讨论
- [ ] 无 in-scope live defect 被降级为 follow-up（本 plan 范围内不存在代码缺陷，零截断缺陷的修复归属 WI10b，已在 roadmap 显式登记，非降级）
- [ ] 受影响 owner docs 已同步：`ai-dev/design/eql-cross-db-compat/README.md` 已登记新文档；`docs-for-ai/` 更新已显式裁定为 WI10b 落地时执行（见 Non-Goals，非默默跳过）
- [ ] 独立子 agent closure-audit 已完成并记录证据（evidence 写入本 plan `Closure` 段，审查者与实现者不同 task_id）
- [ ] **Anti-Hollow Check**（文档计划形态）：closure audit 已抽查设计文档内容与 live repo 代码事实的一致性（至少复核零截断锚点、参数化无集合展开、pagination 先例三组锚点），并确认设计结论没有被弱化成"待定/可以考虑"
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：新增 broken link = 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/22-eql-wi10a-in-list-strategy-design.md --strict` 退出码 0

## Deferred But Adjudicated

### MSSQL 绑定参数路径的 2100 上限真库取证

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本策略以统一阈值 1000 先行拦下绑定参数路径，1000 < 2100，取证结论不改变已裁决行为；只影响 owner doc 的证据等级列。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/` WI10b plan（实现批次）登记取证项；结论回写 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 证据等级列

### 其余方言（MySQL/MariaDB/PG/H2/DuckDB/SQLite/DB2/TDengine/DM8）IN 列表上限一手取证

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 统一阈值策略不依赖 per-dialect 上限（这正是拒绝 per-dialect 阈值的理由）；取证仅在将来重提 per-dialect 差异化时才有决策价值。
- Successor Required: `no`
- Successor Path: 无独立 successor；若重提 per-dialect 阈值，须先按本 plan 设计文档 §3.5 同样的"前置取证清单"模式立项

## Non-Blocking Follow-ups

- WI10b 实现时的错误码命名与 param 集合微调（契约已定：模块自有错误码 + actual/max/语句名/方言名；命名属实现细节）
- `ai-dev/design/eql-cross-db-compat/README.md` 阅读顺序在 WI10b 落地后的再排序（届时设计文档转 active 并新增实现层文档时一并处理）

## Closure

Status Note: （收口时填写）
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （Phase 3 收口时填写：独立子 agent 标识）
- Evidence: （待填：每条 Exit Criterion / Closure Gate 的验证结果、check-plan-checklist 退出码、Anti-Hollow 锚点抽查结果、新增 broken link 比对结果）

Follow-up:

- 见 `Non-Blocking Follow-ups`；无 in-scope live defect 遗留（零截断缺陷修复归属 WI10b，roadmap 已显式登记 owner）
