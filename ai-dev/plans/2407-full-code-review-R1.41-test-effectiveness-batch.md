# 2407 full-code-review R1.41 — 测试有效性补齐批（P1-4.2-003/004/005/006 + P2-4.2-009）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.41；finding `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.2-test-effectiveness-audit.md:633-780`（P1-4.2-003/004/005/006）+ P2-4.2-009
> Related: R1.16（nop-batch 记录面，done；P1-4.2-003 主归属本文）、R1.29（P1-4.4-C3 sentinel context 配对，done；P1-4.2-004 同批互引）、`unit-test-antipatterns.md`
> Owner Doc: `docs-for-ai/02-core-guides/testing.md`
> Plan Review: 首轮 agent_bd70c014 **NOT READY**（2026-10-10）→ B1/M1/M2 + Minor×5 修订（见「修订记录」节）→ 第二轮 agent_1b455691 **READY**（2026-10-10，live 亲读 + javap/反射可行性实证）：8/8 修订全 FIXED、约 25 个行号锚零漂移、5 条新发现均为 Minor 执行细节（① M1 措辞 NPE 位置在下游解引用；② Validator 测试需先设合法机器 initial；③ SystemBlockException 断言 ruleLimitApp 需 setRuleLimitApp；④ entity==item 腿 item/fake 同对象构造细节；⑤ 编程构造需先设 id 再 setTransitions/setStates〔KeyedList keyFn〕）——执行中就地消化。

## 修订记录（响应首轮审查）

- **B1（已修）**：`StateMachineValidator.validate`（StateMachineValidator.java:20-28）只校验 机器 `initial`、各 state 的子 `initial`、`onDone`/`onError` 存在性（`ERR_FSM_UNDEFINED_STATE`）；**不校验 transition `target`**。Phase 3 原「悬空 target 被 Validator 拒绝」的 Exit Criterion 不可满足，已改为：Validator 测试用悬空 initial/onDone/onError；悬空 target 改为**运行时路径**测试（`transit`→`moveToNext`→`requireState`（StateMachine.java:172-177）抛 `ERR_FSM_UNDEFINED_STATE`）。
- **M1（已修）**：Phase 1 fake 面已逐项枚举（见 Phase 1 执行项）。关键：`IEntityModel.requireColumn(String)` 是 **default 方法**（IEntityModel.java:191），JDK Proxy 会把 default 方法同样路由到 InvocationHandler——handler 必须显式处理 `requireColumn`（返回 fake column），不能落到「未知方法返回 null」兜底，否则 `getColumns`（OrmBatchConsumer.java:54-57）拿到 null 列直接 NPE。
- **M2（已修）**：动作记录分两类——`transition.invoke`/`state.onEntry`/`state.onExit` 是 **`IEvalAction`**（`invoke(IEvalContext)`，_StateTransitionModel.java:59、_StateModel.java:101/115）；仅 `state.exit`/`transition.actions` 字符串集走 `model.getInvokeAction()`（**`IEvalFunction`**，`call1(this,arg,scope)`，_StateModel.java:52、_StateTransitionModel.java:24）。两类各自 recording fake。
- **Minor（已修）**：① 复合键过滤形状断言需 **≥2 行**复合键（`FilterBeans.or` 单元素折叠返回该元素本身，FilterBeans.java:330-336——单行时 OR-AND 形状坍缩为 AND）；② 全部行号锚已按 2026-10-10 live 重标；③ Phase 2 明确五种 task 路径语义：**stage 异常走返回的 stage，supplier 同步抛出走 runAsync 同步裸抛（不经 catch(BlockException)、不包装）**，仅 BlockException 走 rejected future；④ Deferred H2 措辞收紧：本批不验证 QueryBean→SQL 翻译；nop-batch-orm 自身无 localDb 基建（nop-batch-dao/dsl 有，`@NopTestConfig(localDb=true)` 先例）；⑤ 各 Phase 增设 **Mutation Evidence** 专段（stash→FAIL→还原→PASS + `git diff` 核对）。

## Purpose

补齐四处「测试无法捕获真实回归」的 P1 缺口并合并 P2-4.2-009：
1. **P1-4.2-003** nop-batch-orm `OrmBatchConsumer` upsert 语义（key 归一/IN vs OR-AND 过滤/insert-update 计数）零直接测试；
2. **P1-4.2-004** nop-cluster-sentinel entry/exit 配对与 BlockException→错误码映射零直接测试（R1.29 仅补 context 泄漏判别）；
3. **P1-4.2-005** nop-fsm `StateMachine` 转移语义（命中/未命中/ignore/动作顺序/Validator）零直接测试；
4. **P1-4.2-006 + P2-4.2-009** `TestStringHelper` 5 方法不触 SUT + 1 零断言，并补 `isWhitespace` 家族「XML 6 字符集 vs JDK 语义」分歧 pinning。

## Current Baseline（2026-10-10 live 亲读）

- **P1-4.2-003**：`OrmBatchConsumer.java`——构造函数 :32-41 `isId(dao,keyFields)`（:43-52）决定 `useIdAsKey`；`consume` :59-108 按 key 拆 insert/update（`!allowUpdate`/`!allowInsert`/`entity==item` 三早退）；`appendFilter` :110-122 单键 `FilterBeans.in` / 复合键 `or(and(eq…),…)`（`buildEqCond` :124-131 按 keyCols 顺序）；`getKey` :133-143 / `getKeyFromEntity` :145-155。`OrmBatchHelper.assignEntity`（support/OrmBatchHelper.java:8-16）遍历 `entity.orm_entityModel().getColumns()` + `BeanTool.hasProperty/getProperty(item)`。模块测试仅 `TestOrmBatchRecordSnapshotRestore`（plan 2382，JDK Proxy fake 先例），`grep OrmBatchConsumer src/test`=0。pom 已有 test-scope junit-jupiter（plan 2382 注）。
- **P1-4.2-004**：`SentinelFlowControlRunner.java`——`runAsync` :44-100（`ContextUtil.enter` :47 → `SphU.asyncEntry` :63 → `proceedCalled=true` :68 → `whenComplete` 内 `Tracer.traceEntry`+`entry.exit(1,args)` :71-81；catch BlockException :83-86 → `FutureHelper.reject(newError(ex))`；finally :87-99 `!proceedCalled` 时 entry.exit + 无条件 `ContextUtil.exit()` :98）；`newError` :102-124 **private**，四态映射 Degrade/Authority/SystemBlock/Flow→`ERR_RPC_FLOW_CONTROL_DEGRADE/AUTHORITY/SYS/BLOCK`（SystemBlock 另带 `ARG_LIMIT_TYPE`）。**路径语义**：stage 异常经返回的 stage 传播；task supplier 同步抛出从 runAsync **同步裸抛**（不进 catch(BlockException)、不包装）；仅 block 走 rejected future。既有测试 `TestSentinelFlowControlRunnerContext`（R1.29，反射 `contextHolder` ThreadLocal 判别 + `@AfterEach` 防御清理），pom 已有 test-scope junit。
- **P1-4.2-005**：`StateMachine.java`——`transit` :110-118（未知 stateValue→`ERR_FSM_UNKNOWN_STATE_VALUE`）；`doTransit` :120-170（`getTransition` 未命中→`isIgnoreUnknownTransition()` 返回或抛 `ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT`（param stateId+event）；动作顺序 invoke→onExit→exit 集→transition actions→onEntry（onEntry 在 `enterState` :208-231）；异常路径 `handleError`/`onError`）；`moveToNext` :233-244→`requireState` :172-177 悬空 target 抛 `ERR_FSM_UNDEFINED_STATE`；`initState` :45-55 / `getInitStateValue` :57-68（无 stateValue 时 fallback `fullStateId.toString()`）。`StateMachineValidator.java:20-28` 只校验 initial/子 initial/onDone/onError；`StateMachineModel.init` :38-49 + `initState` :51-74 重复 stateValue 抛 `ERR_FSM_DUPLICATE_STATE_VALUE`（FsmErrors.java:26-38 全部错误码在案）。模块内 0 测试、**无 src/test 树**，pom 无 test 依赖（deps：nop-core+nop-xlang）。
- **P1-4.2-006**：`TestStringHelper.java` 51 测试中 **5 方法不触 SUT + 1 零断言**：`testWhitespace` :72-74（断言 `Character.isWhitespace`）、`testUrlPath` :77-82（建 URI 仅打印，零断言）、`testSplit` :108-111（JDK `String.split`）、`testFileName` :400-406（`java.io.File` 建删，char 127）、`testUUID` :496-498（调 `StringHelper.generateUUID` 但仅打印）、`testStringFormat` :586-591（JDK `MessageFormat`）。可对应的 SUT 改写目标均在案：`splitToArray(str,sep)` :708、`safeFileName` :2579（含 `_containsControlAscii` 控制字符转义——域为 0-31，DEL 127 不在转义域）、`format(String,Object...)` :4659（String.format 语义，≠MessageFormat）、`generateUUID()` :1084。
- **P2-4.2-009**：`StringHelper.isWhitespace` :990-994 = `ch<=' ' && (' '|'\n'|'\r'|'\t'|'\f'|'\b')`——与 `Character.isWhitespace` **双向分歧**（认 `\b`；拒 VT `\u000B`、`\u001C-\u001F`）；`onlyContainsWhitespace` :996-1005 / `containsWhitespace` :1007-1016 均基于它；main 承重 6+ 处（`XNodeParser:441`、`CharSequenceHelper:54` 等）；全仓 src/test 对三方法零命中，唯一名义测试 `testWhitespace` 恰为 JDK 语义背书。
- **模块测试设施**：nop-batch-orm 已有 junit test-scope + Proxy fake 先例（plan 2382）；nop-cluster-sentinel 测试树由 R1.29 首建（junit+反射判别先例）；nop-fsm **无 src/test、无 test 依赖**（需新建 pom test-scope junit-jupiter，先例 nop-batch-orm）；nop-commons 测试齐全。

## Goals

- 四 SUT 各有**直接、可判别**的行为测试（assert 具体值/错误码/param/调用记录，非裸 assertThrows），并在 mutation 腿下变红。
- `isWhitespace` 家族分歧有 pinning 断言 + owner doc 记载「勿改代理 Character.isWhitespace」。
- 改写/删除 5 个不触 SUT 方法与 1 个零断言方法（保留 UUID 正路径断言，而非仅打印）。
- 受影响模块测试全绿。

## Non-Goals

- 修改任何生产行为（若测试暴露确定性缺陷则单列 Fix 并最小化 diff）。
- 补 P2-4.2-001/002 等其他 zero-test 缺口（另属 R1.0）。
- H2 真库 round-trip（见 Deferred）：本批以 Proxy fake 直测 upsert 决策/计数/过滤形状；**QueryBean→SQL 翻译与本批无涉**；nop-batch-orm 自身无 localDb 测试基建（nop-batch-dao/dsl 有 `@NopTestConfig(localDb=true)` 先例，跨模块不搬）。

## Scope

### In Scope

- `nop-batch/nop-batch-orm/src/test/java/io/nop/batch/orm/**`（新增 consumer 测试）
- `nop-cluster/nop-cluster-sentinel/src/test/java/io/nop/cluster/sentinel/**`（扩展）
- `nop-utils/nop-fsm/src/test/java/io/nop/fsm/**`（新建测试树 + pom test 依赖）
- `nop-kernel/nop-commons/src/test/java/io/nop/commons/util/TestStringHelper.java`（改写 6 方法 + 新增 pinning）
- `docs-for-ai/02-core-guides/testing.md`（owner doc）
- 治理文件（Phase 5）：roadmap、index、daily log、本 plan

### Out Of Scope

- 生产代码；其他 zero-test 模块；H2 真库/e2e

## Execution Plan

### Phase 1 - nop-batch-orm upsert 直接测试（P1-4.2-003）

Status: completed
Targets: `nop-batch-orm/src/test/**`

- Item Types: `Proof`

- [x] `TestOrmBatchConsumer` + fake 设施（JDK Proxy，沿用 `TestOrmBatchRecordSnapshotRestore` 先例）。**fake 面逐项枚举**：
  - fake `IEntityModel`（handler）：`requireColumn(String)` **必须显式处理返回 fake 列**（default 方法，兜底 null 即 NPE）；`getColumns()` 返回全部 fake 列（`assignEntity` 遍历面）
  - fake `IColumnModel`：`getName`/`getPropId`/`getStdDataType`（返回真实 `StdDataType.STRING` 等，`getKey` 用其 convert）
  - fake entity（`IOrmEntity`+`IDaoEntity`）：`orm_entityModel()`、`orm_propValue(int)` 读、`orm_propValue(int,Object)` 写（记录）、`orm_id()`、identity `hashCode/equals`（`entity==item` 早退依赖同一对象）
  - fake dao（`IOrmEntityDao`）：`getPkColumnNames`、`getEntityModel`、`batchGetEntityMapByIds`、`findAllByQuery`（记录 QueryBean）、`newEntity`、`saveEntity`/`updateEntity`（记录）、`castId`
  - item 用真实 POJO（`BeanTool.hasProperty/getProperty` 面向真实 bean）
  - fake `IBatchChunkContext.getTaskContext()`（IBatchChunkContext.java:59）→ `IBatchTaskContext` 记录 `incInsertCount/incUpdateCount`（IBatchTaskContext.java:174/176）累计值
- [x] `useIdAsKey=true` 路径：`batchGetEntityMapByIds` 被调（`isId` 命中，keyFields==pk）；命中→`assignEntity`+`updateEntity`+updateCount；未命中→`newEntity`+`saveEntity`+insertCount
- [x] `useIdAsKey=false` 路径（复合键，**≥2 行** key）：`findAllByQuery` 被调 + 返回实体经 `getKeyFromEntity`（`orm_propValue(propId)` 列表）配对 update；`appendFilter` 生成 `or(and(eq(c1,k1),eq(c2,k2)), and(eq(c1,k3),eq(c2,k4)))` 且每支 eq 顺序与 keyCols 一致（**单行会折叠为 AND——FilterBeans.or 单元素直返（FilterBeans.java:330-336），断言形状必须 ≥2 行**）
- [x] 计数与开关：混合 insert+update 各计一次；`allowInsert=false` 未命中不 insert、`allowUpdate=false` 命中不 update；`entity==item`（同一对象）早退不计 update；空 chunk 计数为 0
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-10.md` R1.41 条目）
- [x] `./mvnw test -pl nop-batch/nop-batch-orm -am -T 1C`

Exit Criteria:

- [x] 两种 key 模式 + 计数/开关/过滤形状用例在案（类/方法名可指）：`io.nop.batch.orm.consumer.TestOrmBatchConsumer` 8 用例（testIdKeyPathBatchGetThenInsertAndUpdate / testCompositeKeyOrAndFilterShapeAndMatchedUpdate / testSingleNonIdKeyUsesInFilter / testAllowInsertFalseSkipsInsert / testAllowUpdateFalseSkipsUpdate / testSameObjectSkipsUpdate / testEmptyChunkZeroCounts / testItemPropsPropagateToNewEntity）
- [x] **Mutation Evidence**（专段）：①`isId` 恒 false→4 FAIL（含 testIdKeyPathBatchGetThenInsertAndUpdate/testSameObjectSkipsUpdate 等全部 id 路径用例）非 id 路径用例不受扰；②删除 `entity==item` 早退→恰 testSameObjectSkipsUpdate 1 FAIL；③单键 `FilterBeans.in` 改写为 `eq`→恰 testSingleNonIdKeyUsesInFilter 1 FAIL；三腿均 `git checkout` 还原后 8/8 PASS，`git status src/main` CLEAN
- [x] nop-batch-orm 全模块测试全绿（`./mvnw test -pl nop-batch/nop-batch-orm -am -T 1C` BUILD SUCCESS，TestOrmBatchConsumer 8/8）

### Phase 2 - nop-cluster-sentinel entry/exit 配对 + 降级映射（P1-4.2-004）

Status: completed
Targets: `nop-cluster-sentinel/src/test/**`

- Item Types: `Proof`

- [x] `TestSentinelFlowControlRunner`（扩展 R1.29 测试树与反射 context 判别先例）。**五种 task 路径**（语义差异已在 Current Baseline 标定）：
  1. 立即完成 stage（`CompletableFuture.completedFuture`）→ 值经 stage 送达 + 发起线程 context 无残留
  2. 异步完成 stage（测试内手动 complete）→ 值送达 + context 无残留（entry exit 在 whenComplete）
  3. stage 异常完成（`completeExceptionally`）→ 原异常经返回的 stage 传播 + context 无残留
  4. **task supplier 同步抛出**（非 Block RuntimeException）→ 从 `runAsync` **同步裸抛**同一异常（不经 rejected future、不被包装）+ context 无残留
  5. block 路径：为测试资源名加载 FlowRule（阈值 0 或 QPS 0）→ `runAsync` 返回 **rejected future** 且错误码 `ERR_RPC_FLOW_CONTROL_BLOCK`；测试后 `FlowRuleManager.loadRules(Collections.emptyList())` 清理
- [x] **映射表**：经反射调用 private `newError(BlockException)`（R1.29 反射判别先例）覆盖 DegradeException/AuthorityException/SystemBlockException/FlowException 四态——错误码 + `ARG_RULE_LIMIT_APP`/`ARG_MSG`/`ARG_LIMIT_TYPE`。**执行发现（javap -c 实证）**：sentinel 1.8.6 `SystemBlockException(String,String)` 把 limitType 存入 `BlockException.ruleLimitApp` 字段且 `getLimitType()` 直接委托 `getRuleLimitApp()`——两 param 恒同值，此真实契约已钉入测试注释；`(String,String)` 构造器首参即 ruleLimitApp（非 message）
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-10.md` R1.41 条目）
- [x] `./mvnw test -pl nop-cluster/nop-cluster-sentinel -am -T 1C`

Exit Criteria:

- [x] 五路径 + 四态映射用例在案：`io.nop.cluster.sentinel.TestSentinelFlowControlRunner` 6 用例（testImmediateCompletionPairsEntryExit / testAsyncCompletionPairsEntryExit / testStageFailurePropagatesViaStage / testSupplierSyncThrowPropagatesSynchronously / testBlockReturnsRejectedFutureWithBlockErrorCode / testNewErrorMapsAllBlockExceptionTypes）
- [x] **Mutation Evidence**（专段）：①`newError` Degrade 分支改抛 BLOCK 码→恰 testNewErrorMapsAllBlockExceptionTypes 1 FAIL；②finally 的 `ContextUtil.exit()` 删除→6 FAIL（本类 immediate/async/block/sync-throw 四条 context 配对断言 + R1.29 既有 TestSentinelFlowControlRunnerContext 2 用例同判据齐红）；还原后 8/8 PASS，`git status src/main` CLEAN
- [x] nop-cluster-sentinel 全模块测试全绿（`-am -T 1C` BUILD SUCCESS）

### Phase 3 - nop-fsm StateMachine 转移语义直接测试（P1-4.2-005）

Status: completed
Targets: `nop-fsm/src/test/**`

- Item Types: `Proof`

- [x] 新建 `nop-utils/nop-fsm/src/test` + pom test-scope junit-jupiter（先例 nop-batch-orm）；测试纯程序化构造模型（无 VFS/DB/`CoreInitialization` 依赖）
- [x] `TestStateMachine` 基线语义：命中转移改状态值（`onStateChange` 收 target stateValue）；未命中抛 `ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT`（param stateId+event 断言）；`ignoreUnknownTransition=true` 未命中静默无变化；`transit` 未知 stateValue 抛 `ERR_FSM_UNKNOWN_STATE_VALUE`（param stateValue）；`initState`/`getInitStateValue` 取 initial 的 stateValue（**未设时 fallback `fullStateId.toString()`**）；嵌套子状态（parent.initial→child）经 `enterState` 递归，`onStateChange` 收叶子 stateValue
- [x] 动作顺序（**两类 recording**）：`transition.invoke`/`state.onExit`/`state.onEntry` 用 recording **`IEvalAction`**（`invoke(IEvalContext)`）；`state.exit`/`transition.actions` 字符串集经 `model.getInvokeAction()`（**`IEvalFunction.call1`**）记录动作名；断言精确序列 `invoke → onExit → exit 集 → transition actions → onEntry`（StateMachine.java:136-149,167-168,208-231）。`IEvalContext` stub 只需 `getEvalScope()` 返回可空 IEvalScope（直接调 `transit`，不走 `triggerStateChange`）
- [x] **模型校验（按 Validator 真实面，B1 修订）**：
  - `StateMachineValidator`：机器级 `initial` 悬空 / state 子 `initial` 悬空 / `onDone` 悬空 / `onError` 悬空 → 各抛 `ERR_FSM_UNDEFINED_STATE`（param stateId）（StateMachineValidator.java:20-40；**Validator 不校验 transition target**）
  - 悬空 transition `target` 为**运行时**行为：`transit`→`moveToNext`→`requireState` 抛 `ERR_FSM_UNDEFINED_STATE`（StateMachine.java:172-177）
  - `StateMachineModel.init` 重复 stateValue 抛 `ERR_FSM_DUPLICATE_STATE_VALUE`（StateMachineModel.java:51-74）
- [x] **Mutation Evidence**（专段）：①`isIgnoreUnknownTransition` 分支反转→1F+1E（testNoTransitionThrowsWithParams FAIL + testIgnoreUnknownTransitionKeepsState ERROR——反转后 ignore=true 路径抛异常，判别成立）；②transition actions 移至 onExit 之前→恰 testActionExactOrder 1 FAIL；均还原后 12/12 PASS，`git status src/main` CLEAN。**执行勘误**：动作顺序测试中 `invoke` recording 挂在 transition（`t1.setInvoke`）而非 state——`setInvoke` 属 StateTransitionModel（首轮 review M2 类型的镜像勘误，编译期发现）
- [x] 本 Phase owner-doc 处置：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-10.md` R1.41 条目）
- [x] `./mvnw test -pl nop-utils/nop-fsm -am -T 1C`

Exit Criteria:

- [x] 转移命中/未命中/ignore/未知 state/动作顺序/Validator 四悬空点/运行时悬空 target/重复 stateValue 用例在案：`io.nop.fsm.TestStateMachine` 12 用例（testHitTransitionChangesStateValue / testNoTransitionThrowsWithParams / testIgnoreUnknownTransitionKeepsState / testUnknownStateValueThrows / testInitStateAndInitValueFallbackToFullStateId / testNestedChildStateTransition / testActionExactOrder / testValidatorDanglingMachineInitial / testValidatorDanglingSubInitialOnDoneOnError / testRuntimeDanglingTransitionTargetThrows / testDuplicateStateValueRejected / testStateValueTypeNormalization）
- [x] **Mutation Evidence** 两腿 FAIL→PASS 已填写，`src/main` 还原核对
- [x] nop-fsm 全模块测试全绿（`-am -T 1C` BUILD SUCCESS）

### Phase 4 - TestStringHelper 反模式清理 + isWhitespace pinning（P1-4.2-006 + P2-4.2-009）

Status: completed
Targets: `TestStringHelper.java`

- Item Types: `Fix | Proof`

- [x] 逐方法改写（SUT 对应点见 Current Baseline）：`testWhitespace`→isWhitespace 家族 pinning 锚（见下）；`testSplit`→`StringHelper.splitToArray`；`testFileName`→`StringHelper.safeFileName`（char 127 控制 ASCII 转义正是其域）；`testStringFormat`→`StringHelper.format`（String.format 语义，断言改为 `"%s a"` 形态）；`testUrlPath`→**删除**（无 SUT 对应物）
- [x] `testUUID` 补断言：非空、两次不同、长度/字符集符合 `generateUUID()` 形态（正则或字符范围），替换纯打印
- [x] 新增 pinning：`assertTrue(isWhitespace('\b'))`、`assertFalse(isWhitespace('\u000B'))`、`assertFalse(isWhitespace('\u001F'))`、`assertTrue` 于 `' '`/`'\f'`/`'\t'`/`'\n'`/`'\r'`，并覆盖 `onlyContainsWhitespace`（null→true、含 `\u000B`→false）与 `containsWhitespace`（null→false、含 `\b`→true）对应边界
- [x] 本 Phase owner-doc 处置：见 Phase 5（testing.md 增反模式 + 空白语义注记）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-10.md` R1.41 条目）
- [x] `./mvnw test -pl nop-kernel/nop-commons -am -T 1C`

Exit Criteria:

- [x] 6 个反模式方法已清理（无「不断言 SUT」用例残留；5 改写/补断言 + 1 删除——testWhitespace→pinning 锚、testSplit→splitToArray、testFileName→safeFileName、testStringFormat→format、testUUID 补断言、testUrlPath 删除；51→50 用例）
- [x] pinning 断言在案（\b 认/VT 拒/\u001C-\u001F 拒三分歧 + onlyContains/contains 边界）
- [x] **Mutation Evidence**（专段）：`isWhitespace` 改为委托 `Character.isWhitespace`→恰 testWhitespace 1 FAIL（三分歧断言齐红）→还原→PASS
- [x] nop-commons 全模块测试全绿（`-am -T 1C` BUILD SUCCESS，50/50）
- [x] **附带生产修复（Non-Goal 逃生条款：测试暴露确定性缺陷→单列 Fix 最小 diff）**：`StringHelper._escapeControlAscii` 的 `sb.append(c)`（c 为 int）把**全部**非控制字符写成十进制码（`safeFileName("a\u0001b")`→`"97_98"`）——改为 `sb.append((char) c)` 一行；行为变更仅影响含控制 ASCII 0-31 的输入（_containsControlAscii 短路门控），旧输出本为十进制码垃圾；全仓 4 处 src/main 消费方（nop-datav PanelDataExporter / nop-ai-core DefaultAiChatResponseCache / nop-ooxml-xlsx ExcelHelper / nop-excel SimpleHtmlSplitter）逐一核查均为改善面、无回归面；nop-commons 全模块回归绿；testFileName 回归断言 `a\u0001b→a_b` 钉定修复契约〔closure audit 勘误：原「全仓无其他 Java 消费方」系初查目录范围不足所致不实陈述，已按 4 处消费方勘正〕

### Phase 5 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/02-core-guides/testing.md`、治理文件

- Item Types: `Proof | Decision`

- [x] owner doc：testing.md 增「测试必须触及 SUT（反模式）」+「刻意语义分歧需 pinning 锚定（StringHelper 空白 6 字符集 vs JDK；勿改代理 Character.isWhitespace）」
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；6 warnings 为无关历史遗留 plan 文件）
- [x] roadmap R1.41 翻 done；index P1-4.2-003/004/005/006 翻 fixed（P2-4.2-009 随 P1-4.2-006 行登记）+ MR 表 R1.41 行；daily log 条目
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `./mvnw -q compile -pl nop-batch/nop-batch-orm,nop-cluster/nop-cluster-sentinel,nop-utils/nop-fsm,nop-kernel/nop-commons -am` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] hollow 扫描按模块限定（四模块各跑 `--module <m> --severity high`）退出码均 0

Exit Criteria:

- [x] owner doc 已更新
- [x] doc-links 退出码 0
- [x] roadmap/index/log 三方一致
- [x] 独立 closure audit 落盘

## Closure Gates

- [x] 四处 in-scope 测试缺口（003/004/005/006+009）已闭合
- [x] 行为结果已达成（各 SUT 直接断言 + pinning；反模式方法清理）
- [x] 必要 focused verification 已完成（四模块全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 缺口
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证测试断言的是 SUT 行为（非 stub/JDK），且各 Phase Mutation Evidence 可判别
- [x] `./mvnw -q compile -pl <四模块> -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### nop-batch-orm H2 真库 round-trip（P1-4.2-003 建议项）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 决策/计数/过滤形状由 Proxy fake 直测已可判别回归；真库链路（H2+ORM session flush+QueryBean→SQL 翻译）属集成/e2e 面，且 nop-batch-orm 模块自身无 localDb 测试基建（组内 nop-batch-dao/dsl 有，但跨模块搬运基建与本批范围不匹配）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- P2-4.2-001 nacos / P2-4.2-002 autotest-junit 等其余 zero-test 缺口（R1.0 面）

## Closure

Status Note: 四处 in-scope 测试有效性缺口（P1-4.2-003/004/005/006+P2-4.2-009）全部闭合：四模块新增/改写 36 用例（batch-orm 8 + sentinel 6 + fsm 12〔测试树首建〕+ commons 反模式清理与 pinning），mutation 腿 8/8 判别；附带一行生产修复（_escapeControlAscii append(int)→(char)，closure audit 独立论证为改善面）。独立 closure audit 首轮 Cannot Close（1 Major 证据勘误 + 2 Minor）→ 三处已修（「safeFileName 全仓无消费方」不实陈述按 4 处真实消费方勘正〔plan/roadmap/index/log 四处〕+ char 127 域措辞 + mutation 腿②计数补注）→ 全部实质 Exit Criteria 经 live 复核与命令重放证实，按 audit 处方「改完勾 gate 即可关」关闭。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_705964c2（独立 fresh-session closure auditor，2026-10-10）
- Evidence:
  - Phase 1 PASS：TestOrmBatchConsumer 8 用例重放 8/8；断言锚定 OrmBatchConsumer 可观测行为（batchGet id 集/assertSame 命中实例/assignEntity 复制/IN 与 OR-AND 形状 ≥2 复合键）；mutation 三腿独立推演与 plan 叙述精确吻合
  - Phase 2 PASS：TestSentinelFlowControlRunner 6 用例重放 6/6（+R1.29 2 用例 8/8）；五路径与 runAsync :44-100 语义一致；newError 四态映射反射直测 + sentinel 1.8.6 SystemBlock 契约钉定
  - Phase 3 PASS：TestStateMachine 12 用例重放 12/12；动作顺序与 doTransit 逐行对应；Validator 四悬空点符合真实校验面（不校验 target）；运行时悬空 target 指向 requireState
  - Phase 4 PASS：反模式 grep 清零（50 方法）；pinning 三分歧在案；50/50 重放绿
  - 附带生产修复 PASS：StringHelper.java:2533 `append((char) c)` 在 live；git status src/main 仅此一处；4 处消费方均为改善面（audit 独立论证）
  - Phase 5 PASS：testing.md 两节在案；doc-links --strict 重放 exit 0（0 errors/6 无关 warnings）；hollow 四模块重放均 exit 0；roadmap/index/log 四方一致重放核实
  - Anti-Hollow：四组测试全部锚定 SUT 可观测行为，无恒真断言；Mutation Evidence 腿名与测试代码一一对应
  - Deferred 分类检查：H2 round-trip 为 watch-only residual（非 in-scope defect 降级），分类诚实
  - 首轮 audit 发现勘误落实：①「safeFileName 全仓无其他 Java 消费方」不实陈述（初查 grep 目录范围不足）已在 plan/roadmap/index/log 四处按 4 处真实消费方勘正；②Current Baseline char 127 域措辞勘正；③Phase 2 mutation 腿② FAIL 计数补注（含 R1.29 既有 2 用例共 6F）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关单前重放）

Follow-up:

- P2-4.2-001 nacos / P2-4.2-002 autotest-junit 等其余 zero-test 缺口（R1.0 台账既载，非本 plan 范围）
- nop-batch-orm H2 真库 round-trip：watch-only residual（见 Deferred But Adjudicated）
