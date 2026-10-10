# 2407 full-code-review R1.41 — 测试有效性补齐批（P1-4.2-003/004/005/006 + P2-4.2-009）

> Plan Status: draft
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.41；finding `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.2-test-effectiveness-audit.md:633-780`（P1-4.2-003/004/005/006）+ P2-4.2-009
> Related: R1.16（nop-batch 记录面，done；P1-4.2-003 主归属本文）、R1.29（P1-4.4-C3 sentinel context 配对，done；P1-4.2-004 同批互引）、`unit-test-antipatterns.md`
> Owner Doc: `docs-for-ai/02-core-guides/testing.md`
> Plan Review: agent_bd70c014 首轮 **NOT READY**（2026-10-10，live 亲读 + 原型实证）——**未执行，待修订后复审**。必须修订项：**B1** `StateMachineValidator` 不校验 transition `target`（只校验 initial/子 state initial/onDone/onError；target 仅在运行时 `transit` 抛 `ERR_FSM_UNDEFINED_STATE`）→ Phase 3 该 Exit Criterion 不可满足，须改为悬空 onDone/onError/initial 或用例运行时路径；**M1** Phase 1 fake 面欠规格：`IEntityModel.requireColumn` 是 default 方法，裸 Proxy 返回 null 会 NPE（须显式指派/`invokeDefault`），并需枚举 fake entity 的 `orm_entityModel()/orm_propValue(int)/orm_propValue(int,Object)/orm_id()` 与 `IBatchChunkContext.getTaskContext`→`incInsertCount/incUpdateCount`；**M2** Phase 3 动作类型错：`transition.invoke`/`state.onExit`/`state.onEntry` 是 `IEvalAction`（非 `IEvalFunction`），仅 `exit`/`actions` 字符串集走 `model.getInvokeAction()`，需两类 recording。其余 Minor：单复合键时 `FilterBeans.or` 折叠为 `and`（OR-AND 形状需 ≥2 复合键）；多处行号锚漂移；Phase 2 同步非 Block 异常为同步抛而非 rejected stage；Deferred 的 H2 措辞需收紧（QueryBean→SQL 翻译未验证，且 nop-batch 组确有 localDb 基建）；mutation 证据无专用字段。已实证可行：Phase 1/2/4 全可行（Proxy fake 原型跑通；`newError` 可反射且四态错误码正确；`FlowRuleManager`+`SphU.asyncEntry` 可靠 block；isWhitespace pinning 三断言正确且委托 JDK 的 mutation 判别成立）；四模块 hollow 扫描 0；与 R1.16/R1.29 无双重登记。

## Purpose

补齐四处「测试无法捕获真实回归」的 P1 缺口并合并 P2-4.2-009：
1. **P1-4.2-003** nop-batch-orm `OrmBatchConsumer` upsert 语义（key 归一/IN vs OR-AND 过滤/insert-update 计数）零直接测试；
2. **P1-4.2-004** nop-cluster-sentinel entry/exit 配对与 BlockException→错误码映射零直接测试（R1.29 仅补 context 泄漏判别）；
3. **P1-4.2-005** nop-fsm `StateMachine` 转移语义（命中/未命中/ignore/动作顺序/Validator）零直接测试；
4. **P1-4.2-006 + P2-4.2-009** `TestStringHelper` 5 方法不触 SUT + 1 零断言，并补 `isWhitespace` 家族「XML 6 字符集 vs JDK 语义」分歧 pinning。

## Current Baseline（live 亲读）

- **P1-4.2-003**：`OrmBatchConsumer.java:34-60` 构造函数 `isId(dao,keyFields)` 决定 `useIdAsKey`；`consume:55-108` 按 key 拆 insert/update（`allowInsert`/`allowUpdate`/`entity==item` 三早退）；`appendFilter:110-131` 单键 `FilterBeans.in` / 复合键 `or(and(eq…))`；`getKey/getKeyFromEntity:133-155` 类型转换。模块测试仅 `TestOrmBatchRecordSnapshotRestore`（plan 2382，Proxy fake 先例），`grep OrmBatchConsumer src/test`=0。
- **P1-4.2-004**：`SentinelFlowControlRunner.java:44-97` `runAsync`——`ContextUtil.enter` → `SphU.asyncEntry` → `proceedCalled=true` → `whenComplete` 内 `Tracer.traceEntry`+`entry.exit`；catch `BlockException` → `newError` 映射；finally 仅 `!proceedCalled` 时 exit + 无条件 `ContextUtil.exit()`。`newError:99-121` 为 **private**，四态映射（Degrade/Authority/SystemBlock/Block→`ERR_RPC_FLOW_CONTROL_*` + params）。既有测试 `TestSentinelFlowControlRunnerContext`（R1.29，2 用例 context 泄漏判别）。
- **P1-4.2-005**：`StateMachine.java:92-140` `transit`→`doTransit`（`getTransition` 未命中时 `model.isIgnoreUnknownTransition()` 返回或抛 `ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT`）；`doTransit:131-160` 动作顺序 invoke→onExit→exit-actions→transition-actions→onEntry；`StateMachineValidator:17-37` 模型校验；`StateMachineModel.init:33-50` 状态值/全 id 映射 + 重复 stateValue 拒绝。模块内 0 测试。
- **P1-4.2-006**：`TestStringHelper.java` 51 测试中 **5 方法不触 SUT + 1 零断言**：`testWhitespace:71-74`（断言 `Character.isWhitespace`）、`testSplit:107-111`（JDK `String.split`）、`testFileName:400-406`（`java.io.File` 建删）、`testStringFormat:586-590`（JDK `MessageFormat`）、`testUrlPath:76-81`（零断言仅打印）、`testUUID:495-498`（调 SUT 但零断言）。
- **P2-4.2-009**：`StringHelper.isWhitespace:990-993` = `ch<=' ' && (' '|'\n'|'\r'|'\t'|'\f'|'\b')`——与 `Character.isWhitespace` **双向分歧**（认 `\b`；拒 VT `\u000B`、`\u001C-\u001F`）；`onlyContainsWhitespace:996-1006`/`containsWhitespace:1009-1019` 均基于它；main 承重 6+ 处（`XNodeParser:441`、`CharSequenceHelper:54` 等）；全仓 src/test 对三方法零命中，唯一名义测试 `testWhitespace` 恰为 JDK 语义背书。
- **模块测试设施**：nop-batch-orm 已有 junit test-scope（plan 2382）；nop-cluster-sentinel 测试树由 R1.29 首建（junit+pom）；nop-fsm **无 src/test**；nop-commons 测试齐全。

## Goals

- 四 SUT 各有**直接、可判别**的行为测试（assert 具体值/错误码/param，非裸 assertThrows），并在 mutation 腿下变红。
- `isWhitespace` 家族分歧有 pinning 三断言 + owner doc 记载「勿改代理 Character.isWhitespace」。
- 删除/改写 5 个不触 SUT 方法与 1 个零断言方法（保留 UUID 生成正路径断言，而非仅打印）。
- 受影响模块测试全绿。

## Non-Goals

- 修改任何生产行为（若测试暴露确定性缺陷则单列 Fix 并最小化 diff）。
- 补 P2-4.2-001/002 等其他 zero-test 缺口（另属 R1.0）。
- H2 真库 round-trip（P1-4.2-003 建议项）：本批以 `IOrmEntityDao` Proxy fake 直测 upsert 决策/计数/过滤形状；真库链路属 e2e 面（见 Deferred）。

## Scope

### In Scope

- `nop-batch/nop-batch-orm/src/test/java/io/nop/batch/orm/consumer/**`（新增）
- `nop-cluster/nop-cluster-sentinel/src/test/java/io/nop/cluster/sentinel/**`（新增）
- `nop-utils/nop-fsm/src/test/java/io/nop/fsm/**`（新建测试树 + 必要测试依赖）
- `nop-kernel/nop-commons/src/test/java/io/nop/commons/util/TestStringHelper.java`（改写 6 方法 + 新增 pinning）
- `docs-for-ai/02-core-guides/testing.md`（owner doc）
- 治理文件（Phase 5）：roadmap、index、daily log、本 plan

### Out Of Scope

- 生产代码；其他 zero-test 模块；H2 真库/e2e

## Execution Plan

### Phase 1 - nop-batch-orm upsert 直接测试（P1-4.2-003）

Status: planned
Targets: `nop-batch-orm/src/test/**`

- Item Types: `Proof`

- [ ] `TestOrmBatchConsumer`（Proxy fake `IOrmEntityDao` + fake `IEntityModel`/`IColumnModel`，沿用 `TestOrmBatchRecordSnapshotRestore` 先例）：`useIdAsKey=true` 路径（`batchGetEntityMapByIds` 被调、命中→update、未命中→insert）与 `useIdAsKey=false` 路径（复合键 `or(and(eq,eq))` 过滤构造 + `findAllByQuery` 返回命中）
- [ ] 计数与开关：混合 insert+update 各计一次（`incInsertCount`/`incUpdateCount`）；`allowInsert=false` 未命中不 insert、`allowUpdate=false` 命中不 update；`entity==item` 早退不计 update；空 chunk 计数为 0
- [ ] 过滤形状：单键 `FilterBeans.in(col, keys)`；复合键 OR-AND 且每支 eq 顺序与 keyCols 一致；`getKeyFromEntity` 复合键列表顺序
- [ ] **mutation 腿**：①`isId` 恒 false → useIdAsKey 用例 FAIL；②`entity==item` 早退删除→计数用例 FAIL；③单键改写 OR-AND→过滤形状用例 FAIL（各恰对应 FAIL）
- [ ] 本 Phase owner-doc 处置：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] `./mvnw test -pl nop-batch/nop-batch-orm -am -T 1C`

Exit Criteria:

- [ ] 两种 key 模式 + 计数/开关/过滤形状用例在案（类/方法名可指）
- [ ] mutation 三腿 FAIL→PASS 记录在 plan，`src/main` 还原经 `git diff --stat -- '*/src/main/**'` 核对
- [ ] nop-batch-orm 全模块测试全绿

### Phase 2 - nop-cluster-sentinel entry/exit 配对 + 降级映射（P1-4.2-004）

Status: planned
Targets: `nop-cluster-sentinel/src/test/**`

- Item Types: `Proof`

- [ ] `TestSentinelFlowControlRunner`（扩展 R1.29 测试树）：`runAsync` 正常完成（立即完成 + 异步完成）与 task 抛非 Block 异常三路径——断言 stage 结果/异常传播 + 同线程 `ContextUtil.getContext()` 不残留（entry/exit 与 context 配对）
- [ ] block 路径：为测试资源名加载 FlowRule（测试后 `FlowRuleManager.loadRules(Collections.emptyList())` 清理）→ `runAsync` 返回 rejected future 且错误码 `ERR_RPC_FLOW_CONTROL_BLOCK`
- [ ] **映射表**：经反射调用 private `newError(BlockException)`（R1.29 反射判别先例）覆盖 DegradeException/AuthorityException/SystemBlockException/FlowException 四态——错误码 + `ARG_RULE_LIMIT_APP`/`ARG_MSG`/`ARG_LIMIT_TYPE`（SystemBlock）
- [ ] **mutation 腿**：①`newError` Degrade 分支改抛 BLOCK 码 → 映射用例 FAIL；②`finally` 的 `ContextUtil.exit()` 删除 → context 残留用例 FAIL（R1.29 判别面）
- [ ] 本 Phase owner-doc 处置：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] `./mvnw test -pl nop-cluster/nop-cluster-sentinel -am -T 1C`

Exit Criteria:

- [ ] 三路径 + block + 四态映射用例在案
- [ ] mutation 两腿 FAIL→PASS 记录在 plan，`src/main` 还原核对
- [ ] nop-cluster-sentinel 全模块测试全绿

### Phase 3 - nop-fsm StateMachine 转移语义直接测试（P1-4.2-005）

Status: planned
Targets: `nop-fsm/src/test/**`

- Item Types: `Proof`

- [ ] 新建 `nop-utils/nop-fsm/src/test`（BaseTestCase/`CoreInitialization`，无 DB）+ pom test-scope junit-jupiter（先例 nop-batch-orm）
- [ ] `TestStateMachine`：构造 `StateMachineModel`（states + transitions + `stateProp`/`initial`/`stateValueType`）覆盖——命中转移改状态值；未命中抛 `ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT`（param state/event 断言）；`ignoreUnknownTransition=true` 时未命中静默无变化；`transit` 未知 stateValue 抛 `ERR_FSM_UNKNOWN_STATE_VALUE`；`initState`/`getInitStateValue` 取 initial 的 stateValue（未设时 fallback fullStateId）
- [ ] 动作顺序：以 recording `IEvalFunction` 记录 `invoke→onExit→exit actions→transition actions→onEntry` 顺序（spy 断言）；嵌套子状态 fullStateId 解析
- [ ] `StateMachineValidator`：非法模型（悬空 target / 重复 stateValue）拒绝并断言错误码；`StateMachineModel.init` 重复 stateValue 抛 `ERR_FSM_DUPLICATE_STATE_VALUE`
- [ ] **mutation 腿**：①`isIgnoreUnknownTransition` 分支反转 → ignore 用例 FAIL；②动作顺序调整（onEntry 提前）→ 顺序用例 FAIL
- [ ] 本 Phase owner-doc 处置：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] `./mvnw test -pl nop-utils/nop-fsm -am -T 1C`

Exit Criteria:

- [ ] 转移命中/未命中/ignore/未知 state/动作顺序/Validator 用例在案
- [ ] mutation 两腿 FAIL→PASS 记录在 plan，`src/main` 还原核对
- [ ] nop-fsm 全模块测试全绿

### Phase 4 - TestStringHelper 反模式清理 + isWhitespace pinning（P1-4.2-006 + P2-4.2-009）

Status: planned
Targets: `TestStringHelper.java`

- Item Types: `Fix | Proof`

- [ ] 删除/改写 5 个不触 SUT 方法（`testWhitespace`/`testSplit`/`testFileName`/`testStringFormat`/`testUrlPath`）：改为调用 SUT 并断言输出；无法对应 SUT 行为者删除
- [ ] `testUUID` 补断言（返回非空、两次不同、正则格式），替换纯打印
- [ ] 新增 pinning：`assertTrue(StringHelper.isWhitespace('\b'))`、`assertFalse(StringHelper.isWhitespace('\u000B'))`、`assertFalse(StringHelper.isWhitespace('\u001F'))`、`assertTrue('\f'/' '/' \t'/'\n'/'\r')`，并覆盖 `onlyContainsWhitespace`/`containsWhitespace` 对应边界
- [ ] **mutation 腿**：把 `isWhitespace` 改为委托 `Character.isWhitespace` → pinning 用例 FAIL（证明分歧被锚定）
- [ ] 本 Phase owner-doc 处置：见 Phase 5（testing.md 增反模式 + 空白语义注记）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] `./mvnw test -pl nop-kernel/nop-commons -am -T 1C`

Exit Criteria:

- [ ] 6 个反模式方法已清理（无「不断言 SUT」用例残留）
- [ ] pinning 断言在案且 mutation 腿可判别
- [ ] nop-commons 全模块测试全绿

### Phase 5 - owner doc + 收口

Status: planned
Targets: `docs-for-ai/02-core-guides/testing.md`、治理文件

- Item Types: `Proof | Decision`

- [ ] owner doc：testing.md 增「测试必须触及 SUT（反模式）」+「刻意语义分歧需 pinning 锚定（StringHelper 空白 6 字符集 vs JDK；勿改代理 Character.isWhitespace）」
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] roadmap R1.41 翻 done；index P1-4.2-003/004/005/006（+P2-4.2-009 报告级）登记；daily log 条目
- [ ] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [ ] `./mvnw -q compile -pl nop-batch/nop-batch-orm,nop-cluster/nop-cluster-sentinel,nop-utils/nop-fsm,nop-kernel/nop-commons -am` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] hollow 扫描按模块限定（`nop-batch/nop-batch-orm`、`nop-cluster/nop-cluster-sentinel`、`nop-utils/nop-fsm`、`nop-kernel/nop-commons`）退出码 0（如父目录有既有 finding 则限定到本模块）

Exit Criteria:

- [ ] owner doc 已更新
- [ ] doc-links 退出码 0
- [ ] roadmap/index/log 三方一致
- [ ] 独立 closure audit 落盘

## Closure Gates

- [ ] 四处 in-scope 测试缺口（003/004/005/006+009）已闭合
- [ ] 行为结果已达成（各 SUT 直接断言 + pinning；反模式方法清理）
- [ ] 必要 focused verification 已完成（四模块全绿）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 缺口
- [ ] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证测试断言的是 SUT 行为（非 stub/JDK），且 mutation 腿可判别
- [ ] `./mvnw -q compile -pl <四模块> -am` 通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### nop-batch-orm H2 真库 round-trip（P1-4.2-003 建议项）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 决策/计数/过滤形状由 Proxy fake 直测已可判别回归；真库链路（H2+ORM session flush）属集成/e2e 面，且 nop-batch-orm 现无 localDb 测试基建，引入成本与本批范围不匹配。
- Successor Required: `no`

## Non-Blocking Follow-ups

- P2-4.2-001 nacos / P2-4.2-002 autotest-junit 等其余 zero-test 缺口（R1.0 面）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-
