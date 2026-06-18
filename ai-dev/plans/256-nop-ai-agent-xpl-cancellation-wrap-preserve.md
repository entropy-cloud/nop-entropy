# 256 nop-task isCancelledException 穿透 xpl 包装识别 cancellation（cancelled step 不再误判 FAILED）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: xpl-cancellation-wrap-preserve (carry-over from plan 254)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/254-nop-ai-agent-terminal-failure-failed-driver.md`（§Closure Follow-up line 196「xpl 方法调用包装穿透 cancellation 字符（与 plan 249 前 bizFatal 同类已知限制）——独立 successor」+ §Non-Goals line 44 EXPIRED/KILLED 与 cancel 排除语义 + §Closure line 187 Deferred 项分类检查「xpl 包装穿透 cancellation 字符」三处一致记录的独立 successor）。plan 254 交付了 FAILED-driver（终态失败 → stepStatus FAILED 可观测），其 cancel-check（`TaskStepExecution` async/sync 错误分支）调 `TaskStepHelper.isCancelledException` 判定是否为 cancelled。但当 cancelled step 经 xpl 方法调用（`.task.xml` `<source>` 调 bean 方法）抛 `NopTaskCancelledException` 时，`AbstractObjFunctionExecutable.wrapInvokeException`（plan 249 已让 5 个 doInvoke* 统一委托）将其包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)`，`isCancelledException` 直接检查顶层异常、不解包 cause → 返回 false → cancel-check 未命中 → FAILED-driver 将 cancelled step 误标记 FAILED。
> Related: `254`（FAILED-driver，其 cancel-check 依赖 isCancelledException——本计划让 cancelled step 经 xpl 包装后仍被 cancel-check 正确识别）、`249`/`250`/`251`（bizFatal 包装穿透三部曲 sibling——同一 wrapInvokeException，但 cancellation 是类型检查而非 flag，修复点不同）、`252`（TaskStepStateBean 状态机 isDone/isSuccess）

## Carry-over framing correction

原 carry-over 描述（NEXT_ITEM）将修复定位为「与 plan 249 bizFatal 同文件、同 helper、同模式：在 `wrapInvokeException` 通过 `.forWrap()` 保留 cancellation 标记」。此定位与 cancellation 的实际机制不匹配：

- **bizFatal 是 NopException 的 boolean flag**（`isBizFatal()`），可被「拷贝」到包装异常（plan 249 `wrapInvokeException:82-83` `err.bizFatal(true)`），使消费方 `RetryPolicy.isRecoverableException:152` 读取顶层异常的 flag 即可判定。
- **cancellation 是类型检查**（`isCancelledException` 第二档 `instanceof NopTaskCancelledException` + 第三档 errorCode `== ERR_TASK_CANCELLED`），而非可拷贝的 flag。`NopEvalException` 无法「变成」`NopTaskCancelledException`，故 plan 249 的 flag-拷贝模式不可直接复用于 producer 侧。

本计划将修复定位在 **消费侧**（`TaskStepHelper.isCancelledException` 解包 cause chain），而非 producer 侧（`wrapInvokeException`）。理由：(1) cause chain 完整保留（plan 249 Closure Audit 已验证 `newError(errorCode, e)` 保留 `getCause()`）——`NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL).getCause()` 返回原 `NopTaskCancelledException`，消费侧解包即可识别；(2) 消费侧解包覆盖**全部** xpl 包装路径（doInvoke* 方法调用 / readAttr-setAttr bracket / readProp-setProp dot，及任何未来包装点），而非仅 doInvoke* 一条；(3) 仅触及 `nop-task-core`（非 Protected Area），不触及 `nop-xlang`（框架核心引擎，plan-first），blast radius 最小；(4) 标准 Java 异常 cause-chain 解包模式。

## Purpose

修复 `TaskStepHelper.isCancelledException` 直接检查顶层异常、不解包 cause 的缺陷，使经 xpl 包装（`NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=NopTaskCancelledException)`）的 cancelled 异常被正确识别为 cancelled。修复后，plan 254 的 cancel-check（在 FAILED-driver 之前）能命中经 xpl 包装的 cancellation → cancelled step 不被误标记 FAILED → `TryTaskStepWrapper` / `SelectorTaskStep` cancel 语义在 `.task.xml` E2E 路径端到端成立。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path）：

- **isCancelledException 直接检查、不解包 cause（核心缺口）**：`TaskStepHelper.isCancelledException`（`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:72-80`）三档检查均针对传入异常 `e` 自身，不递归 `getCause()`：
  - 第一档 `e instanceof CancellationException`（`:73`）；
  - 第二档 `e instanceof NopTaskFailException || e instanceof NopTaskCancelledException`（`:75`）；
  - 第三档 `e instanceof NopException` 且 `getErrorCode().equals(ERR_TASK_CANCELLED.getErrorCode())`（`:77-78`）。
  - 三档均不查 cause → 当 `e` 是 `NopEvalException`（包装异常）时全部 false。
- **xpl 方法调用包装丢失 cancellation 类型（端到端路径）**：`.task.xml` `<source>` 经 xpl 调 bean 方法（`AbstractObjFunctionExecutable.doInvoke*`，plan 249 已让 5 重载统一委托 `wrapInvokeException`）→ bean 方法抛 `NopTaskCancelledException` → `wrapInvokeException` 包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=NopTaskCancelledException)` → 传播到 `TaskStepExecution` 错误分支。
- **cause chain 完整保留（plan 249 已验证）**：`newError(errorCode, e)` = `new NopEvalException(errorCode, e)`，`getCause()` 返回原异常。故 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL).getCause()` = 原 `NopTaskCancelledException.INSTANCE`——消费侧解包 cause chain 即可识别。
- **cancel-check 在 FAILED-driver 之前（plan 254 已交付，结构正确）**：`TaskStepExecution.executeWithParentRt` async err!=null 分支与 sync catch 分支的 cancel-check（调 `isCancelledException`）在 FAILED-driver wiring 之前 throw。结构正确，但 `isCancelledException` 因不解包 cause 对经 xpl 包装的 cancelled 异常返回 false → cancel-check 未命中 → 继续到 FAILED-driver → cancelled step 被误标记 FAILED。
- **cancelled step fixture 已就绪**：`FailureSimulatorBean.throwCancelled()`（`nop-task-ext/.../reliability/FailureSimulatorBean.java:113-116`）抛 `NopTaskCancelledException.INSTANCE`；`cancelled-step/v1.task.xml`（`nop-task-ext/.../_vfs/nop/task/test/cancelled-step/v1.task.xml`）经 xpl `<source>` 调 `sim.throwCancelled()`。fixture 经 xpl 方法调用触发包装路径（非直接抛出）。
- **NopTaskCancelledException 是 NopSingletonException 子类**：`NopTaskCancelledException`（`nop-task-core/.../exceptions/NopTaskCancelledException.java`）`extends NopSingletonException`，errorCode = `ERR_TASK_CANCELLED`，单例 `INSTANCE`。cause chain 解包命中第二档（`instanceof NopTaskCancelledException`）。
- **isCancelledException 有 3 个消费方（全部经 isCancelledException，非直接 instanceof）**：grep `isCancelledException` 全仓消费方 = (1) `TaskStepExecution` cancel-check（async `:230` / sync `:280`，plan 254 FAILED-driver 之前）；(2) `SelectorTaskStep`（`:43, :84`，selector error-handling loop）；(3) `TryTaskStepWrapper`（`:46, :69`，try/catch wrapper）。三者均调 `isCancelledException`（非直接 `instanceof NopTaskCancelledException`），故消费侧 cause-chain 解包**自动覆盖全部 3 个消费方**。但三者的当前行为对经 xpl 包装的 cancelled 异常均不正确（isCancelledException 返回 false）：
  - **SelectorTaskStep**：selector 选中的 step 经 xpl 抛 cancellation → 包装 → `isCancelledException` false → selector 视为普通失败（尝试下一分支或传播失败），而非 cancellation 取消。
  - **TryTaskStepWrapper**：try-block step 经 xpl 抛 cancellation → 包装 → `isCancelledException` false → catchAction 被触发（吞掉 cancellation 当普通错误处理），而非 cancellation 穿透。
  - 当前**无任何测试**覆盖 SelectorTaskStep / TryTaskStepWrapper 的 cancellation 路径（既有 fixture 用 `sim.throwRecoverable()` 普通 failure，非 cancellation）。
- **当前测试明确记录 gap**：`TestReliabilityDecorators.cancelledStep_notMarkedFailed_cancelExclusion`（`nop-task-ext/.../reliability/TestReliabilityDecorators.java:816-845`）显式注释「不严格断言『不为 FAILED』，因为 xpl 包装使 cancellation 字符丢失」，仅断言「任务抛异常」（确认 cancelled exception 经包装后失去 cancellation 字符、被识别为普通失败）。单元测试 `TestTaskStepHelperIsCancelledException`（`nop-task-core/.../utils/TestTaskStepHelperIsCancelledException.java`）验证 cancel-check 真值表，但**不含**包装异常的 cause-chain 测试用例（因为修复前该路径不可行）。

## Goals

- **isCancelledException 穿透 xpl 包装识别 cancellation**：当传入异常（或其 cause chain 中任一层）是 cancellation 异常（`CancellationException` / `NopTaskCancelledException` / `NopException` 带 `ERR_TASK_CANCELLED` errorCode）时，`isCancelledException` 返回 true。核心场景：`NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=NopTaskCancelledException)` → true。
- **非 cancellation 异常不被误判**：不含 cancellation 的包装异常（如 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=ordinary NopException)`）仍返回 false（防止 over-matching）。
- **cancelled step 不再误标记 FAILED（端到端）**：`.task.xml` cancelled step 经 xpl 调用抛 `NopTaskCancelledException` → 包装 → cancel-check 命中（isCancelledException 返回 true）→ throw → step 不进入 FAILED-driver → `getStepStatus() != FAILED(60)`。
- **composite step cancellation 传播正确**：经 xpl 包装的 cancellation 在 `SelectorTaskStep`（selector 选中 step 抛 cancellation → selector 取消而非视普通失败）和 `TryTaskStepWrapper`（try-block step 抛 cancellation → catchAction 跳过、cancellation 穿透）中正确传播。3 个消费方（TaskStepExecution + SelectorTaskStep + TryTaskStepWrapper）均有 focused E2E 验证。
- **零回归**：plan 254 终态失败 FAILED-driver 路径不变（非 cancelled 失败仍标记 FAILED）；plan 246-251 retry/timeout/rateLimit/bizFatal 行为不变；nop-task-core + nop-task-ext + nop-ai-agent 既有测试全绿。

## Non-Goals

- **producer 侧 cancellation 保留（wrapInvokeException / readAttr-setAttr / readProp-setProp 内重抛或保留）**：cancellation 是类型检查非 flag，producer 侧保留需重抛原异常（改变 error code 语义）或侵入式改 NopEvalException 类型层级，blast radius 大且仅覆盖单一包装路径。消费侧 cause-chain 解包已严格更优（覆盖全部包装路径）。Classification: rejected（设计裁定，消费侧解包为更优解）。
- **EXPIRED/KILLED 终态 status（timeout/cancel 语义的终态标记）**：plan 254 §Non-Goals carry-over。cancelled step 本计划仅保证「不误标记 FAILED」，不为其新增 KILLED 终态标记（cancelled step 当前无终态 status，保持 ACTIVE/null）。Classification: out-of-scope improvement。
- **直接 `instanceof NopTaskCancelledException` 检查的其他消费方**（不经 `isCancelledException`）：Phase 1 grep 已确认 `SelectorTaskStep`（`:43,:84`）/ `TryTaskStepWrapper`（`:46,:69`）/ `TaskStepExecution`（`:230,:280`）三者均经 `isCancelledException`，无直接 instanceof 消费方。若 grep 发现额外的不经 `isCancelledException` 的 cancellation 检查点，为独立 successor。Classification: execution-time 核对 / successor plan required（若存在额外消费方）。
- **跨重启 exception 持久化（transient 移除）**：plan 247/252/254 carry-over。Classification: optimization candidate。
- **continuation-skip 消费侧**：依赖 DB-backed state persistence。Classification: successor plan required。

## Scope

### In Scope

- `TaskStepHelper.isCancelledException`（`nop-task-core/.../utils/TaskStepHelper.java:72-80`）cause-chain 解包修复：当顶层异常非 cancellation 时，递归检查 `getCause()` 链。
- `TestTaskStepHelperIsCancelledException`（`nop-task-core/.../utils/`）新增包装异常 cause-chain 单元测试用例。
- `TestReliabilityDecorators.cancelledStep_notMarkedFailed_cancelExclusion`（`nop-task-ext/.../reliability/TestReliabilityDecorators.java:816-845`）更新：从「记录 gap、不严格断言」改为「断言 cancelled step 经 xpl 包装后仍不被标记 FAILED（stepStatus != 60）」。
- 新增 SelectorTaskStep cancellation 传播 E2E：selector 选中 step 经 xpl 抛 cancellation → 包装 → `isCancelledException` true → selector 取消（非普通失败处理）。新增对应 `.task.xml` fixture。
- 新增 TryTaskStepWrapper cancellation 穿透 E2E：try-block step 经 xpl 抛 cancellation → 包装 → `isCancelledException` true → catchAction 跳过、cancellation 穿透。新增对应 `.task.xml` fixture。
- 零回归：plan 254 FAILED-driver + plan 246-251 decorator/bizFatal + cancel 路径 + nop-ai-agent 既有测试全绿。

### Out Of Scope

- 见 Non-Goals（producer 侧保留 / EXPIRED-KILLED 终态 / 直接 instanceof 消费方 / 跨重启持久化 / continuation-skip reader 均为显式 rejected / out-of-scope / successor）。

### 设计裁定（Pre-Adjudicated）

1. **修复目标以「可观测行为」定义，不绑死实现方式**。要求：当传入异常的 cause chain 中任一层是 cancellation 异常时，`isCancelledException` 返回 true。实现方式（while 循环解包 / 递归 / 提取私有 helper）属 execution 裁定（Minimum Rules #10）。现有三档检查逻辑保持不变，仅追加对 cause chain 的遍历。

2. **消费侧修复（isCancelledException），非 producer 侧（wrapInvokeException）**。理由见 Carry-over framing correction：cancellation 是类型检查非 flag，producer 侧 flag-拷贝不可用；消费侧 cause-chain 解包覆盖全部包装路径、不触及 Protected Area（nop-xlang）、blast radius 最小。

3. **cause-chain 解包须防循环**。Java 异常 cause chain 理论上可自引用（`initCause(this)`）。Execution 须保证解包不会无限循环（visited-set 或深度上限），虽实际场景罕见但属防御性编码要求。Exit Criteria 验证深度嵌套（≥2 层包装）正常返回。

4. **blast-radius 评估须覆盖 isCancelledException 全部 3 个消费方**。grep 已确认 3 个消费方均经 `isCancelledException`：(1) `TaskStepExecution` cancel-check（`:230,:280`）；(2) `SelectorTaskStep`（`:43,:84`）；(3) `TryTaskStepWrapper`（`:46,:69`）。三者行为均受 cause-chain 解包影响（更多异常被识别为 cancelled）。Execution Phase 1 须 grep 复核无额外消费方；Phase 3 为 SelectorTaskStep + TryTaskStepWrapper 各加 focused E2E 验证 cancellation 传播行为变更正确。

5. **cancelled step 不新增终态 status**。本计划仅保证 cancelled step「不误标记 FAILED」（cancel-check 在 FAILED-driver 之前 throw）。cancelled step 的 stepStatus 保持 ACTIVE/null（无 KILLED 终态），与 plan 254 §Non-Goals EXPIRED/KILLED 一致。

## Execution Plan

### Phase 1 - isCancelledException cause-chain 解包 + 单元测试 + compile

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java`（`isCancelledException:72-80`）、`nop-task/nop-task-core/src/test/java/io/nop/task/utils/TestTaskStepHelperIsCancelledException.java`

- Item Types: `Fix`（confirmed live defect：isCancelledException 不解包 cause → 经 xpl 包装的 cancelled 异常被误判为普通失败 → plan 254 FAILED-driver 误标记 cancelled step FAILED——plan 254 §Closure Follow-up line 196 明确切出的独立 successor 活缺陷，不得降级为 Follow-up）

- [x] blast-radius 评估：grep 全仓 `isCancelledException` 消费方，复核 3 个已知消费方（TaskStepExecution `:230,:280` / SelectorTaskStep `:43,:84` / TryTaskStepWrapper `:46,:69`）均经 isCancelledException，无额外直接 instanceof 消费方；确认「更多异常被识别为 cancelled」对三者的影响可接受（结果记录到 daily log）
- [x] grep 核对：是否存在不经 `isCancelledException` 的直接 `instanceof NopTaskCancelledException` 消费方；若发现额外消费方，记录为独立 successor（设计裁定 4 / Non-Goals）
- [x] 修复 `isCancelledException`（`:72-80`）：现有三档检查保持，追加 cause-chain 遍历——当顶层异常非 cancellation 时，递归检查 `getCause()` 链（设计裁定 1/2/3）
- [x] 新增单元测试：`NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=NopTaskCancelledException.INSTANCE)` → `isCancelledException` 返回 true（核心新行为）
- [x] 新增单元测试（对偶/回归）：`NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=ordinary NopException)` → `isCancelledException` 返回 false（防止 over-matching）
- [x] 新增单元测试（深度嵌套）：`NopEvalException` 包装 `NopEvalException` 包装 `NopTaskCancelledException`（≥2 层）→ 返回 true（设计裁定 3 防循环 + 深度覆盖）
- [x] 新增单元测试（cause 为 CancellationException）：`NopEvalException` 包装 `CancellationException` → 返回 true（cause-chain 第一档变体）
- [x] 确认现有 5 个单元测试（CancellationException / NopTaskCancelledException / ERR_TASK_CANCELLED errorCode / ordinary NopException / ordinary RuntimeException）零回归

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `isCancelledException` 解包 cause chain（读码可复核：顶层非 cancellation 时递归 getCause），防循环（设计裁定 3）
- [x] 包装 cancellation 异常（`NopEvalException` wrapping `NopTaskCancelledException`）→ `isCancelledException` 返回 true
- [x] 包装非 cancellation 异常 → `isCancelledException` 返回 false（无 over-matching）
- [x] 现有 5 个单元测试零回归
- [x] **无静默跳过**（#24）：cause-chain 解包为真实实现（非空方法体 / 非 TODO / 非 return false 占位）
- [x] 新增功能各有 focused 测试覆盖（#25）：包装 cancellation 穿透 + 包装非 cancellation 回归 + 深度嵌套 + CancellationException cause 各有断言
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] `./mvnw test -pl nop-task/nop-task-core -am` 既有测试 + 新增单元测试全绿
- [x] owner-doc 裁定：`No owner-doc update required`（内部 helper 行为，step/cancel public 执行契约不变，cancelled step 仍抛出、仍不产生 KILLED 终态）——Phase 2 复核
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 实现 landing）

### Phase 2 - E2E cancelled-step 不误判 FAILED + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/TestReliabilityDecorators.java`（`cancelledStep_notMarkedFailed_cancelExclusion:816-845`）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 更新 `cancelledStep_notMarkedFailed_cancelExclusion`（`:816-845`）：从「记录 gap、不严格断言」改为断言 cancelled step 经 xpl 包装后**不被标记 FAILED**——`stateStore().getCapturedState("cancelledStep").getStepStatus() != 60(FAILED)`（cancel-check 命中 → FAILED-driver 被跳过）
- [x] 更新该测试注释块（`:818-844`）：移除「xpl 包装使 cancellation 字符丢失」「不严格断言不为 FAILED」等 gap 记录，改为描述修复后正确行为（cancelled step 经 xpl 包装仍被 cancel-check 识别）
- [x] 确认 `runTask("test/cancelled-step")` 仍抛异常（cancelled exception 经 cancel-check throw 传播），但 step state 不为 FAILED
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am`（含 plan 252-254 状态机/driver + Phase 1 新增单元测试）+ `./mvnw test -pl nop-task/nop-task-ext -am`（plan 246-251 decorator/bizFatal E2E + 本计划 cancelled E2E）+ `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] cancelled-step E2E 全绿：cancelled step（经 xpl 方法调用包装）`getStepStatus() != FAILED(60)`（修复前此值为 60，修复后 cancel-check 命中跳过 FAILED-driver）——**此断言在 Phase 1 修复前会失败**（包装丢失 cancellation → isCancelledException false → FAILED-driver 标记 60），修复后通过
- [x] **端到端验证**（#22）：从 `.task.xml` cancelled-step 经 xpl `<source>` 调 `sim.throwCancelled()` → `doInvoke*` 包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=NopTaskCancelledException)` → `TaskStepExecution` 错误分支 cancel-check 调 `isCancelledException`（解包 cause → 命中 NopTaskCancelledException → true）→ throw（跳过 FAILED-driver）→ step 不标记 FAILED 完整路径连通
- [x] **接线验证**（#23）：isCancelledException cause-chain 解包在 runtime 被 TaskStepExecution cancel-check 调用（E2E 断言 stepStatus != FAILED 可观测——修复前为 60）
- [x] **无静默跳过**（#24）：cause-chain 解包真实执行；E2E 断言具体状态值（非仅断言不抛异常）
- [x] 新增功能各有 focused 测试覆盖（#25）：Phase 1 单元测试（包装穿透 + 回归 + 深度嵌套 + CancellationException cause）+ Phase 2 E2E（cancelled step 不误判 FAILED）；Selector/Try composite 由 Phase 3 覆盖
- [x] 零回归：plan 254 FAILED-driver（非 cancelled 失败仍标记 FAILED）+ plan 246-251 decorator/bizFatal + cancel 路径 + nop-ai-agent 全绿
- [x] owner-doc 裁定落地（`No owner-doc update required`：内部 helper 行为，step/cancel public 执行契约不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - composite step cancellation 传播 E2E（Selector + Try）+ 零回归收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/TestReliabilityDecorators.java`（新增 Selector/Try cancellation E2E）、`nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/`（新增对应 `.task.xml` fixture）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 新增 SelectorTaskStep cancellation 传播 E2E：新建 `.task.xml`（selector 选中 step 经 xpl `<source>` 调 `sim.throwCancelled()` 抛 cancellation）+ `TestReliabilityDecorators` 测试——断言 task 传播 cancellation（throw，非普通失败处理）、selector 不将 cancellation 当普通 failure（如不尝试下一分支 / 不标记 FAILED）
- [x] 新增 TryTaskStepWrapper cancellation 穿透 E2E：新建 `.task.xml`（`<try>` block step 经 xpl 调 `sim.throwCancelled()` + `<catch>` catchAction）+ `TestReliabilityDecorators` 测试——断言 catchAction **未被触发**（cancellation 穿透 catch）、task 传播 cancellation
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-ext -am`（含 Phase 2 cancelled-step E2E + Phase 3 Selector/Try E2E + plan 246-251 decorator/bizFatal）+ `./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] SelectorTaskStep cancellation E2E 全绿：selector 选中 step 经 xpl 包装的 cancellation 被 `isCancelledException`（`:43`/`:84`）识别为 cancelled → selector 取消而非普通失败——**此行为在 Phase 1 修复前不成立**（包装丢失 cancellation → selector 视普通失败），修复后通过
- [x] TryTaskStepWrapper cancellation E2E 全绿：try-block step 经 xpl 包装的 cancellation 被 `isCancelledException`（`:46`/`:69`）识别为 cancelled → catchAction 跳过、cancellation 穿透——**此行为在 Phase 1 修复前不成立**（catchAction 被触发吞掉 cancellation），修复后通过
- [x] **端到端验证**（#22）：selector / try 路径从 `.task.xml` 声明 → step 经 xpl 调用抛 cancellation → doInvoke* 包装 → SelectorTaskStep/TryTaskStepWrapper 调 `isCancelledException`（解包 cause → true）→ cancellation 正确传播 完整路径连通
- [x] **接线验证**（#23）：isCancelledException cause-chain 解包在 runtime 被 SelectorTaskStep + TryTaskStepWrapper 调用（E2E 断言 cancellation 传播行为可观测——修复前 catchAction 触发 / selector 视普通失败，修复后穿透/取消）
- [x] **无静默跳过**（#24）：E2E 断言具体行为（catchAction 触发计数 / selector 分支选择 / cancellation 传播），非仅断言不抛异常
- [x] 新增功能各有 focused 测试覆盖（#25）：SelectorTaskStep cancellation 传播 + TryTaskStepWrapper cancellation 穿透 各有 E2E 断言
- [x] 零回归：plan 254 FAILED-driver + plan 246-251 decorator/bizFatal + simple cancelled-step（Phase 2）+ nop-ai-agent 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `isCancelledException` 解包 cause chain 修复为真实代码（包装 cancellation → true）
- [x] cancelled step 经 xpl 包装在 `.task.xml` E2E 路径不被误标记 FAILED（闭合 plan 254 §Closure Follow-up line 196）
- [x] SelectorTaskStep + TryTaskStepWrapper cancellation 传播在 `.task.xml` E2E 路径正确（3 个消费方均有 focused E2E）
- [x] 非 cancellation 包装异常不被误判（无 over-matching）
- [x] blast-radius 评估已完成（isCancelledException 3 个消费方已核对，无非预期行为漂移）
- [x] 必要 focused verification 已完成（单元测试包装穿透 + 回归 + 深度嵌套 + E2E cancelled 不误判 FAILED + Selector/Try cancellation 传播）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（producer 侧保留 / EXPIRED-KILLED / 直接 instanceof 消费方 / 跨重启持久化 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）isCancelledException cause-chain 解包在 runtime 被 TaskStepExecution cancel-check 调用（E2E stepStatus != FAILED 可观测），（b）无空方法体/静默跳过/no-op 作为正常实现，（c）cancelled 异常确实从 bean 方法经 xpl 包装传递到 cancel-check 并被识别（E2E 可观测）
- [x] `./mvnw compile -pl nop-task/nop-task-core -am`
- [x] `./mvnw test -pl nop-task/nop-task-core -am` + `nop-task-ext` + `nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / out-of-scope improvement / successor plan required，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- **额外不经 `isCancelledException` 的 cancellation 消费方**：Phase 1 grep 已确认 3 个消费方均经 isCancelledException；若执行中发现额外直接 instanceof 消费方，为独立 successor。Classification: execution-time 核对 / successor plan required（若存在）。
- **EXPIRED/KILLED 终态 status（timeout/cancel 语义）**：out-of-scope improvement（plan 254 carry-over）。
- **跨重启 exception 持久化（transient 移除）**：optimization candidate（plan 247/252/254 carry-over）。
- **continuation-skip 消费侧**：successor plan required（依赖 DB-backed state persistence）。

## Closure

Status Note: plan 256 闭合 plan 254 §Closure Follow-up「xpl 方法调用包装穿透 cancellation 字符」独立 successor。`TaskStepHelper.isCancelledException` 现遍历 cause chain（含 anti-cycle 保护），使经 xpl `wrapInvokeException` 包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL, cause=NopTaskCancelledException)` 的 cancelled 异常被正确识别为 cancelled。3 个消费方（TaskStepExecution cancel-check / SelectorTaskStep / TryTaskStepWrapper）均经 helper 调用，消费侧解包自动覆盖全部包装路径。修复前 cancelled step 经 xpl 包装后 isCancelledException 返回 false → FAILED-driver 误标记 FAILED / selector 视普通失败 / catchAction 吞掉 cancellation；修复后 cancel-check 命中 → throw 跳过 FAILED-driver → cancelled step 保持 ACTIVE（不进 KILLED 终态，与 plan 254 §Non-Goals EXPIRED/KILLED 一致）。Producer 侧 cancellation 保留（flag-拷贝不可用，类型检查非 flag）、EXPIRED/KILLED 终态、直接 instanceof 消费方、跨重启持久化、continuation-skip 均为显式 Non-Goals。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: 独立 read-only explore subagent（task `ses_1244cf483ffeNDOSzPyQJZTpuf`，fresh session，非实现阶段同一 session）
- Audit Session: `ses_1244cf483ffeNDOSzPyQJZTpuf`
- Evidence:
  - **Exit Criteria 验证**：
    - Phase 1 Exit Criteria 全 PASS：`isCancelledException` cause-chain 解包为真实 `while (e != null)` 循环 + `IdentityHashMap` visited-set anti-cycle 保护（`TaskStepHelper.java:75-95`），3-arm single-level 逻辑完整保留（`:87-95`）；4 新增单元测试构造真实包装异常并断言（`TestTaskStepHelperIsCancelledException.java:81-121`）；现有 5 单元零回归。
    - Phase 2 Exit Criteria 全 PASS：`TestReliabilityDecorators.cancelledStep_notMarkedFailed_cancelExclusion:845` 断言 `assertNotEquals(Integer.valueOf(60), stepState.getStepStatus())`，注释块无「不可行/gap」残留；端到端 `.task.xml` cancelled-step 经 xpl 包装 → cancel-check 命中 → stepStatus != FAILED 路径连通。
    - Phase 3 Exit Criteria 全 PASS：`selector_cancelledBranch_propagatesCancellation_plan256:854-889`（task 抛异常 + counter==1 证明 fallbackBranch 未执行 + cancelledBranch != FAILED）+ `try_cancelledStep_propagatesThroughCatch_plan256:892-923`（task 抛异常 + counter==1 证明 catchAction 未触发）；2 新 fixture（`composite-selector-cancelled/v1.task.xml` + `composite-try-cancelled/v1.task.xml`）结构正确。
  - **Closure Gates 验证**：全 13 项 PASS（见上方 [x]）。
  - **接线验证（#23）**：3 消费方 6 callsite 全部经 `isCancelledException`（`TaskStepExecution:230,280` / `SelectorTaskStep:43,84` / `TryTaskStepWrapper:46,69`，audit Task 2 逐行确认）。
  - **Anti-Hollow 检查**：(a) runtime 调用链连通——E2E 断言 stepStatus != 60 / counter==1 / task 抛异常均可观测；(b) 无空方法体/静默跳过——`isCancelledException` 真实循环 + `isCancelledExceptionSingleLevel` 3-arm 逻辑；(c) cancelled 异常从 bean 方法经 xpl `wrapInvokeException`（`AbstractObjFunctionExecutable.java:79` `newError(ERR_EXEC_INVOKE_METHOD_FAIL, e)` 保留 cause）传递到 cancel-check 并被识别（audit Task 7 确认）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task --severity high` 退出码 0（0 critical/high findings）。
  - **checklist 完整性证据**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/256-nop-ai-agent-xpl-cancellation-wrap-preserve.md --strict` 退出码 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - **构建验证**：`./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（nop-task-core 57 / nop-task-ext 44 含 TestReliabilityDecorators 37 / nop-ai-agent 2714，全 0 failures）。
  - **Deferred 项分类检查**：无 deferred 项；Non-Goals（producer 侧保留 / EXPIRED-KILLED / 直接 instanceof 消费方 / 跨重启持久化 / continuation-skip）均为显式 rejected / out-of-scope / successor，无 in-scope live defect 被降级。
  - **Audit 结论**：OVERALL: APPROVED（8/8 tasks PASS，实现真实非空壳）。

Follow-up:

- 无剩余 plan-owned work。Non-Blocking Follow-ups 中 EXPIRED/KILLED 终态、跨重启持久化、continuation-skip 均为 carry-over 独立 successor，非本 plan scope。
