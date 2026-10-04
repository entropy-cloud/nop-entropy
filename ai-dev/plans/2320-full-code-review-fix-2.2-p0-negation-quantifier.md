# 2320 full-code-review 修复：nop-orm-eql 否定与量词编码簇（P0-2.2-001/002/003 + P1-2.2-003）

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: 审计报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.2-orm-eql-deep-audit.md` P0 即时通道（异步注入）；roadmap 规则 #6
> Related: plan 2319（2.2 深审，P0 发现来源）；契约基线 `docs/dev-guide/orm/eql-collection-operator.md`（§3/§5/嵌套量词语义）
> Item Types: `Fix`（全部为审计确认的 live defect，不得降级为 Follow-up）
> Draft Review: agent_aa3f8f1a 首轮 1 Blocker（NOT 形态编码机制双读法矛盾）+ 5 Minor——已钉死唯一机制（读法 B）并全部修订

## Purpose

修复 nop-orm-eql 集合操作符否定与量词编码根因簇：P0-2.2-001（`NOT (col._some P)` 语义反转）、P0-2.2-002（嵌套 `_all` 量词静默翻转/丢失）、P0-2.2-003（NOT+嵌套集合路径否定错位），连带修复 P1-2.2-003（`NOT (col._all P)` 编译崩溃——仅 detach 修复会落入静默反转 P0，必须与本簇同设计）。修复以契约文档语义为准：`NOT (collection._some P) ≡ collection._all (NOT P)`、嵌套量词逐层语义保持。

## Current Baseline

- 根因（审计复核探针实证，`_tmp/probe/`、`_tmp/Dim10bRv*.java` 可重放）：
  - `CollectionOperatorTransformer.findLogicUnitNode`（:109-122）穿透 `SqlNotExpr`，使子 scope 的逻辑单元=整个 NOT 子树；`replaceLogicNode`（:536-541）用子 EXISTS 替换含 NOT 克隆条件——`NOT (col._some P)` 被编译为 `exists(... not(P))`（∃(¬P)≠¬∃(P)），单层与嵌套均反转。
  - `shouldNegateExists`（:436-442/:444-454）的 `hasChild()` 守卫 + 按 ALL 祖先奇偶而非本层操作符计算否定——嵌套 `_all` 量词翻转/丢失（`_all._some` ≡ `_some._all` 逐字符同 SQL；`_all._all` 双 ∀ 全丢）。
  - `negateConditionForAll`（:626-629）SqlNotExpr 分支返回未 detach 的内层节点——`:534` setExpr 挂链冲突编译崩溃（ast-node-not-allow-multiple-parent，文档 §5:204 原文形态 100% 触发）。
  - `mergeConditions`（:154）无条件覆盖 `canMergeSomeScopes`（:242）设置的 commonAnd——`p1 and (p2 or q)` 编译失败 unknown-alias[t1]（P1-2.2-007，同文件；**不在本 plan 范围**，归 R1.0 簇C，但回归时注意区分失败形态）。
- 测试债务：`TestCollectionOperatorTransformer.testThreeLevelNestedOperators`（:187-198）把错误输出 `∃r∃d∀t` 钉为期望值——修复必须显式修订该期望。
- 现有绿色基线：nop-orm-eql 163 测试全绿（2026-10-04 实跑）；单层 `_some`/`_all` 正语义、`_all` AND/OR 合并正确部分有 19+ 条测试钉住（修复不得破坏）。
- 判级依据：deep-audit 共享前缀 + 2.2 报告判级调和记录（崩溃类基线 P2、文档契约 100% 触发+内部错误码升 P1、静默错果= P0）。

## Goals

- `NOT (col._some P)` 全形态（含 `not(a._some p and q)` 合并落入 SqlNotExpr 形态、双 NOT）编译为 ¬∃ 语义（EXISTS-not 或 NOT EXISTS），与文档 §5 一致。
- 嵌套集合操作符逐层量词语义保持：`_all._some`/`_some._all`/`_all._all`/三级链各自编译为正确嵌套 EXISTS/NOT EXISTS（正确编码规则：scope_i 的 EXISTS-not=(自身为 ALL) XOR (父 scope 为 ALL)，叶子条件取反=(叶子为 ALL)——dim10b 复核验算规则，实施时重新验算全部形态）。
- `NOT (col._all P)` 与无括号文档原文形态编译成功且语义正确（¬∀P ≡ ∃(¬P)），不再崩溃、不落入反转。
- 修订 testThreeLevelNestedOperators 错误期望 + 为三个 P0 与崩溃形态补回归测试（含空集三值行为断言）。
- 既有 163 测试保持全绿（单层正语义与合并规则不被回归）。

## Non-Goals

- P1-2.2-006 别名查重、P1-2.2-007 mergeConditions、DML 渲染缺口族（P1-2.2-001/002/004/005/008/010）、eval 否定（P1-2.2-011）——归 R1.0 对应簇。
- 契约文档迁移 docs-for-ai（G.2 移交项）；语法/grammar 变更；AstToEqlGenerator 往返形状（P3）。
- nop-orm 会话域（plan 2318 已审）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CollectionOperatorTransformer.java`（及否定编码直接涉及的 compile 包协作类）
- `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestCollectionOperatorTransformer.java`（期望修订+新增回归）
- 探针形态对应的端到端测试落点（TestEqlCompileSql 或新测试类）

### Out Of Scope

- CollectionOperatorTransformer 之外的 P1/P2/P3 发现；nop-orm-eql 其他包（除非否定编码修复必须触及的最小协作面，触及需在 plan 回写说明）。

## Execution Plan

### Phase 1 - 修复实现

Status: planned
Targets: CollectionOperatorTransformer 否定/量词编码

- Item Types: `Fix`

- [ ] 实施前基线：`git status` 干净、`./mvnw test -pl nop-persistence/nop-orm-eql -am` 全绿（163）
- [ ] **机制钉死（读法 B，唯一权威口径）**：逻辑单元=SqlNotExpr 本身（对直包 NOT 形态维持现状 unit 归属），条件克隆=`not(P)`；路径 NOT 奇偶**翻转有效操作符**（_some↔_all）进入编码；EXISTS-not 按 XOR 取「有效操作符」；条件级 NOT 由 negateConditionForAll 的 SqlNotExpr 分支吸收（detach 修复为 P0-2.2-001 的 load-bearing 依赖）。不采用「NOT 留外层」读法——该读法下 replaceLogicNode（:544-568）无 SqlNotExpr 分发分支会静默不挂链，且混合两读法在 `not(col._all P)` 上恰好双重取反
- [ ] 修复否定编码为逐层 XOR 规则：**scope_i 的 EXISTS-not = (有效操作符为 ALL) XOR (父 scope 有效操作符为 ALL)，叶子条件取反 = 有效操作符为 ALL**；删除 shouldNegateExists（:436-454）的 hasChild 守卫依赖，**并改造 replaceLogicNode（:526-531）的 `!scope.hasChild()` 叶子取反门控以支持非叶子 scope 的条件级 NOT 剥离**（否则 `not(嵌套_some)` 双重取反静默错果）；全部形态（六纯量词形态 + NOT 形态矩阵：`not(_some P)`/`not(_all P)`/`not(嵌套_some)`/`not(嵌套_all)`/双 NOT/合并+NOT）期望 SQL 写入 `_tmp/fcr-2320-truth-table.md` 作为唯一权威口径
- [ ] 修复 negateConditionForAll（:626-629）：SqlNotExpr 分支 detach 内层节点（ASTNode.detach :154-160 在位）+ 正确吸收语义（¬∀P≡∃(¬P)），消除 ast-node-not-allow-multiple-parent 崩溃且不落入反转
- [ ] 连带影响面留痕：findLogicUnitNode 三个调用点（:104 buildLogicNodes/:154 mergeConditions/:324 findNestedLogicNode）共享同一函数——读法 B 下 findLogicUnitNode 本体不改、:154（簇C，范围外）零行为影响（仅需在 truth table 记录该判别）；:324 行为变化为 P0-2.2-003 预期修复路径
- [ ] Owner doc 裁定：修复使实现对齐既有文档契约，`No owner-doc update required`（行为契约不变）

Exit Criteria:

- [ ] `_tmp/` 探针重放三 P0+崩溃形态全部输出正确语义 SQL（对照 dim10b 复核验算表）
- [ ] 无静默跳过：修复不引入吞异常/no-op 路径；错误路径仍响亮失败
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 回归测试与验证

Status: planned
Targets: TestCollectionOperatorTransformer + 端到端测试

- Item Types: `Fix` + `Proof`

- [ ] 修订 testThreeLevelNestedOperators 期望为正确语义（注释注明修订依据=2.2 审计 P0-2.2-002）
- [ ] 新增回归：NOT+_some（含无括号文档原形/合并形态/双 NOT）、NOT+_all（括号+原文形态）、嵌套矩阵（`_all._some`/`_some._all`/`_all._all`/三级链显式钉 `_all._some._some` 与 `_some._some._all` 两条）——断言完整 SQL 或量词结构，不只断言不崩溃
- [ ] 空集三值行为断言（NOT _some 空集应命中、_all 空集语义与文档 §3 一致）——runtime 落点指名：扩展 nop-orm TestCollectionOperator 的 runtime 数据模式或仿 TestH2WindowFrameCompileEndToEnd 内存 H2 模式（TestEqlCompileSql 纯编译层断言不了 runtime 行为）
- [ ] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 全绿（163+新增）；全量 `./mvnw test -pl nop-persistence/nop-orm,nop-persistence/nop-orm-eql -am`（消费方模块联动）

Exit Criteria:

- [ ] 新增测试 ≥8 条且逐条对齐三 P0+崩溃+嵌套矩阵形态
- [ ] 除显式修订的 testThreeLevelNestedOperators 与零断言的 testDebugScopes 外，全部既有测试零修改零回退
- [ ] `./mvnw test -pl nop-persistence/nop-orm,nop-persistence/nop-orm-eql -am` 全绿
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - Closure audit 与收口

Status: planned
Targets: 本 plan + index + roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（验证：三 P0 形态探针/测试复现正确语义、无反转回归、163+新增全绿、修订测试的正当性）
- [ ] index 回填：P0-2.2-001/002/003 修复状态→fixed（plan 2320）；log 收口记录
- [ ] 双 --strict（check-doc-links + check-plan-checklist）；commit

Exit Criteria:

- [ ] closure audit PASS 证据写入本 plan Closure 段
- [ ] index「P0 发现追踪」三行状态更新
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

- [ ] 三 P0 + P1 崩溃形态全部修复且语义正确（探针/测试双证据）
- [ ] 无「只修崩溃落入反转」回归（NOT 簇四形态矩阵全验）
- [ ] 新增回归测试覆盖全部修复形态；既有测试零回退
- [ ] 受影响模块测试全绿（nop-orm-eql + nop-orm 联动）
- [ ] 独立 closure audit PASS 并留痕
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0
- [ ] index/log 一致；双 --strict 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 嵌套量词语义文档化（契约文档迁移 docs-for-ai）归 G.2/R1.0（2.2 报告移交注记在案）。

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
