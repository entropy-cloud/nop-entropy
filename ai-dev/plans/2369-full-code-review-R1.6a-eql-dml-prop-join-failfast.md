# 2369 full-code-review R1.6a — nop-orm-eql DML prop-join 渲染修复批（簇 D：P1-2.2-001/002/004/005/008/010 + P1-2.2-003 回归确认）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.6a 行；finding `P1-2.2-001/002/004/005/008/010`（簇 D）+`P1-2.2-003`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.2-orm-eql-deep-audit.md`；修复簇定义 ：33「一个 DML 感知 fail-fast + keyProp/操作符位置矩阵专门错误码 + 渲染前 propJoins 断言多入口齐封」）
> Related: R1.6b（簇 B/C）/R1.6c（簇 E）分立行

## Purpose

按审计簇 D 处方闭合 nop-orm-eql 的 DML prop-join 渲染缺口族：编译器不再对「DML 语句中属性路径隐式 join / 集合操作符出没于不受支持位置」静默产出非法或语义错误 SQL，改为编译期具名错误码响亮失败（审计明确短期 fail-fast 优先，合法方言化改写不在本批）；同时核销 P1-2.2-003（`NOT (col._all P)` 崩溃）——确认已由 plan 2320 连带消除并补语义回归。

## Current Baseline

- **共同根因**：`CollectionOperatorTransformer` 仅挂载于 `visitSqlQuerySelect` 的 where（`EqlTransformVisitor.java:314-316`）；UPDATE/DELETE/HAVING/ORDER BY/投影/窗口等位置的属性路径走 `resolvePropPath`（`EqlTransformVisitor.java:795-835` to-many keyProp 分支），keyValue=`_some`/`_all` 被当作 keyProp 键值拼进 join 条件（`addToManyRelationJoin`）。
- **三入口实测**（审计探针）：①`update ... where o.roles._some.userId='y'`（keyProp 模型）→ `where t1.USER_ID='y'` 未声明别名（P1-2.2-001，nop-wf globalVars/nop-dyn extFields 真实模型可达）；②`order by o.roles._some.userId` → `t1.SID='_some'` 死 join（P1-2.2-002）；③普通属性路径 `update/delete ... where o.dept.name='y'` → `t1.NAME` 未声明（P1-2.2-008 canonical，AstToEqlGenerator.visitSqlUpdate/visitSqlDelete 不渲染 propJoin）；④相关子查询多段 owner `o.dept.id` → 同 ③（P1-2.2-010；单段 owner 有 resolveIgnoredOwner 物理表名回退，合法）；⑤UPDATE SET/RETURNING prop-join 路径（P1-2.2-005，dim10c-15 同族 SET 入口）。
- **判定结构**：`SqlPropJoin` 有 `left/right`（`compile/SqlPropJoin.java`）——`join.getLeft()==dmlTargetSource` 可精确判定「路径起自 DML 目标表」；子查询自身别名的多段路径（合法）不受影响。
- **visitSqlUpdate/visitSqlDelete 现状**（`EqlTransformVisitor.java:1541-1585/1600-1617`）：各自解析目标 source（delete 已有 `source` 局部变量 + `node.setResolvedTableSource`）；`AstToEqlGenerator.visitSqlUpdate/visitSqlDelete`（:333-390）纯 AST 渲染、不读 propJoins。
- **P1-2.2-003 现状**：plan 2320 簇A 已重写 `negateConditionForAll`（`CollectionOperatorTransformer.java:632` 起含 SqlNotExpr 处理 :465/:481-482）——需端到端回归确认 `not (o.roles._all.userId='x')` 不再崩溃且语义为 ∃(¬P) 的正确 NOT(_all) 形态。
- 错误码定义于 `OrmEqlErrors.java`（`ErrorCode.define` 手写常量）；测试先例：`TestEqlCompileSql`（SQL 文本断言）、审计探针模型（AppUser/AppRole/AppDept，keyProp 形态在 `nop-wf/model/nop-wf.orm.xml:295`、nop-dyn app.orm.xml:13 有真实在案）。
- nop-orm-eql 测试基线：2.2 审计后全绿（模块级先例 2.8 审计「三模块全绿」）。

## Goals

- 集合操作符出现在 select-where 之外的任何位置（update/delete where、order by、having、投影、窗口、group by 等）→ 编译期具名错误码响亮失败（单一判定点：resolvePropPath to-many keyProp 分支收到 `_some`/`_all` 段）。
- DML 语句（update/delete）中起自目标表的**多段**属性路径（where/相关子查询/SET 赋值目标）→ 编译期具名错误码；单段 owner（合法物理表名回退）与子查询自身作用域路径不受影响。
- 渲染层兜底断言：AstToEqlGenerator 的 update/delete 渲染若遇非空 propJoins → 具名错误码（防线纵深）。
- P1-2.2-003 端到端回归确认 + 语义断言钉住；状态核销。

## Non-Goals

- 不实现 DML 的合法 join 改写（审计：方言化改写须处理 MySQL 1093 等限制，短期 fail-fast 优先）。
- 不修簇 B（P1-2.2-006/007→R1.6b）、簇 E（P1-2.2-009/011→R1.6c）。
- 不修 dim10a-07 误导性错误消息（P3，非本批）。
- 不改 SELECT 通道任何现有行为（transform 位置矩阵只加守卫不改写）。

## Scope

### In Scope

- `EqlTransformVisitor`：①`resolvePropPath` to-many keyProp 分支操作符段守卫；②`visitSqlUpdate/visitSqlDelete` 设置 `dmlTargetSource` 字段并在 `visitSqlColumnName` owner-path 分支对「起自 DML 目标表的多段路径」守卫；③语句边界清场。
- `AstToEqlGenerator.visitSqlUpdate/visitSqlDelete`：渲染前 `resolvedTableSource` propJoins 非空断言（兜底）。
- `OrmEqlErrors`：新增 2 个错误码（DML prop-join 不支持 / 集合操作符位置不支持）。
- 回归测试：位置矩阵（update/delete where/order by/having/投影/group by × keyProp/普通模型 × `_some`/`_all`）断言新错误码；DML 多段路径三入口断言；单段 owner 与子查询自身路径合法形态不破坏；`NOT _all` 语义回归。

### Out Of Scope

- P2-2.2-004（transformAfterAnalyze 钩子）等簇外 P2/P3。

## Execution Plan

### Phase 1 - 三守卫 + 错误码 + 回归测试

Status: completed
Targets: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`、`.../sql/AstToEqlGenerator.java`、`.../OrmEqlErrors.java`、`src/test/java/...`（新测试类）

- Item Types: `Fix`

- [x] `OrmEqlErrors` 新增：`ERR_EQL_DML_PROP_JOIN_NOT_SUPPORTED`（update/delete 不支持属性路径隐式 join）、`ERR_EQL_COLLECTION_OPERATOR_NOT_ALLOWED_HERE`（集合操作符仅支持在 select 语句 where 中使用）
- [x] 守卫 1（操作符位置矩阵）：`resolvePropPath` to-many keyProp 分支，**在 :821 `propPath = propPath.getNext()` 重赋值之前**判定 `propPath.getNext().getName()` 为 `_some`/`_all`（用 `SqlCollectionOperator.fromText(...) != null` 与既有代码一致）→ 抛新错误码（覆盖 update/delete where/order by/having/投影/group by 矩阵——transform 前置改写使 select-where 不会到达此分支；**窗口位置不在矩阵内**：测试方言未注册窗口函数，该位置先命中 ERR_EQL_UNKNOWN_FUNCTION 不可达——审查实测，声明与矩阵一致）
- [x] 守卫 2（DML 多段路径）：visitSqlUpdate/visitSqlDelete 解析目标 source 后设 `dmlTargetSource` 字段（语句边界清场）；`visitSqlColumnName` owner-path 分支 resolvePropPath 后判定 `join.getLeft()==dmlTargetSource`（引用相等）→ 抛新错误码（覆盖四入口：where / 相关子查询外层别名 / SET 赋值目标 / **RETURNING 投影**〔visitSqlUpdate :1573 visitChildren(returnProjections) 同经此分支〕；单段 owner 走 :1321-1325 直取分支不经此路径，分支结构天然区分）
- [x] 守卫 3（渲染兜底）：`AstToEqlGenerator.visitSqlUpdate/visitSqlDelete` 渲染前对 resolvedTableSource 判空后断言 propJoins 非空抛同错误码（**必须判空**——AstToEqlGenerator 同时服务未 transform 的 toSQL/dump 通道，resolvedTableSource 可为 null；AstToSqlGenerator 继承不覆写，base 守卫同时封 SQL 与 toSQL 双出口）
- [x] P1-2.2-003 回归确认：探针验证 `not (o.roles._all.userId='x')` 编译不再崩溃（探针用字面量非参数，避免克隆 SqlParameterMarker 卷入参数收集面）；新增语义回归测试**钉住 SQL 文本形态**（预期：无 NOT 包装的 `exists(... t1.USER_ID <> 'x' ...)`——语义 ¬∀(P)=∃(¬P)，量词方向由 shouldNegateExists ALL→true + absorbedNotParity 偶数→不否定存在量词、叶 EQ→NE 落实）
- [x] 新增测试类：位置矩阵 × 模型形态断言新错误码；四入口 DML 多段路径断言（where/相关子查询/SET 目标/RETURNING——RETURNING 用例按 dim10a-10 探针形态 `update ... returning o.roles._some.userId`）；单段 owner（`update ... where o.id='x'`）与子查询自身作用域多段路径合法形态回归（不破坏既有合法面）

Exit Criteria:

- [x] 位置矩阵全组（update/delete where/order by/having/投影/group by）：非法位置编译期抛 `ERR_EQL_COLLECTION_OPERATOR_NOT_ALLOWED_HERE`（测试证据；窗口位置不可达已声明）
- [x] DML 四入口：多段路径编译期抛 `ERR_EQL_DML_PROP_JOIN_NOT_SUPPORTED`（含 RETURNING）；合法形态（单段 owner/子查询自身路径/select 通道 `_some`）全部保持原行为（测试证据）
- [x] P1-2.2-003 状态核销：`NOT _all` 端到端不崩溃且语义正确（测试证据）
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 全绿（无行为回归）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: roadmap、轮次 index、本 plan

- Item Types: `Proof`

- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0
- [x] roadmap R1.6a 行 → done（含 P1-2.2-003 核销注记）；index P1-2.2-001/002/003/004/005/008/010 → fixed + MR 表 R1.6a 行
- [x] owner doc 裁定：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 现无集合操作符约束描述（审查 grep 零命中）——补一段「集合操作符仅支持 select where + DML 不支持属性路径隐式 join」；roadmap 行另一 owner doc `model-first-development.md` 核实后记录 No update required 或最小增补
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] roadmap 与 index 回填一致（7 个 finding 状态 + MR 行）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 簇 D 六条 finding 全部以具名错误码 fail-fast 封闭（位置矩阵 + DML 三入口 + 渲染兜底三层）
- [x] P1-2.2-003 核销：回归测试钉住 NOT(_all) 正确语义
- [x] 合法面零破坏：单段 owner/子查询自身路径/select 通道行为不变（回归测试证据）
- [x] focused verification：nop-orm-eql 全量测试绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] owner docs 已同步或显式记录 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：守卫真实触发（错误码断言测试）非恒真；合法面回归非空转
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### DML 属性路径的合法方言化改写

- Classification: `optimization candidate`
- Why Not Blocking Closure: 审计处方明示「短期 fail-fast 优先」；合法改写须按方言处理 UPDATE 自引用限制（MySQL 1093 派生表包装等），属能力增强非缺陷遗留
- Successor Required: `no`

## Non-Blocking Follow-ups

- dim10a-07 误导性错误消息（P3，`ERR_EQL_PROP_PATH_NOT_VALID_TO_ONE_REFERENCE` 复用面）：归 P3 watch
- P2-2.2-003（CTE 用户别名丢失）等簇外 P2：台账通道

## Closure

Status Note: 簇 D 六条 P1 以三层守卫（操作符位置矩阵/DML 多段路径四入口/渲染兜底）全部编译期响亮封闭，两个新错误码；P1-2.2-003 经 2320 修复端到端核销（exists+EQ→NE 语义钉住）；合法面零破坏回归；nop-orm-eql 191 tests 全绿。机制注记（审计 Advisory 1）：RETURNING 入口实际由守卫 3 接住（visitSqlUpdate 在 dmlTargetSource 清场后访问 returnProjections），功能等价、错误码一致。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_a2666516-def8-482c-88c2-d3d9c8193791
- Evidence:
  - **Anti-Hollow**：PASS——auditor 复跑 `TestEqlDmlPropJoinFailFast` 15/15 绿；守卫实证（守卫 1 :826-833 fromText 判定于重赋值前/守卫 2 :1342-1350 引用相等/守卫 3 AstToEqlGenerator:340-343 判空断言）；15 测试全部断言具体错误码非恒真；合法面三用例实质断言（SQL 文本形态）；NOT(_all) 语义断言实存。
  - **契约核验**：PASS——错误码定义与三处抛点参数齐备；**dmlTargetSource 异常路径无泄漏**（EqlCompiler:62 每次 compile 新建 visitor、:45-46 单语句，异常后实例丢弃——auditor 结论无需 try/finally）。
  - **独立复跑**：PASS——模块全量 `./mvnw test -pl nop-persistence/nop-orm-eql` 191/0/0；hollow scan 0；doc-links --strict 0。
  - **状态回填四处一致**：PASS——roadmap R1.6a(:135)/index 七行 fixed(:92-101)/index MR(:260)/daily log。
  - **Deferred 分类**：PASS——合法方言化改写=optimization candidate（审计处方短期 fail-fast 优先）；dim10a-07=P3 watch。
  - Advisory 处置：A1 RETURNING 归因注记（本段已记，功能等价）；A2 随本 closure 提交（已做）；A3 -pl/-am 等价性注记（模块级结论一致）。

Follow-up:

- DML 属性路径合法方言化改写（optimization candidate，见 Deferred）
- dim10a-07 误导性错误消息（P3 watch）
- P2-2.2 簇外条目（台账通道）
