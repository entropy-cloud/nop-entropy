# 249 xpl 方法调用异常包装保留 bizFatal 标记（retry 分类 E2E 端到端修复）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: xpl-bizfatal-wrap-preserve (carry-over from L4-nop-task-decorator / plan 247)

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/247-nop-ai-agent-task-state-exception-persistence.md`（§Follow-up line 181「xpl 函数调用异常包装丢失 bizFatal 标记」+ §Closure line 162 已知限制「bizFatal 分类在 .task.xml E2E 路径不生效」+ §Phase 2 line 102/114 实现裁定明确指出 xpl 包装为本 successor）。该 carry-over 是 plan 247 交付（TaskStepStateBean exception 持久化）后唯一阻断「不可恢复异常 fail-fast」价值主张端到端成立的缺口：plan 247 已让 `state.exception()` 返回真实异常，但该异常在 .task.xml E2E 路径下是 xpl 包装后的 `NopEvalException`（bizFatal 丢失）→ 分类器仍判定可恢复 → 仍无条件重试。属框架核心引擎（nop-kernel/nop-xlang，Protected Area，plan-first）。
> Related: `247`（交付 TaskStepStateBean exception 持久化——本计划修复其遗留的 xpl 包装缺口，使分类联动在 E2E 路径端到端成立）、`246`（交付 retry/timeout/rateLimit decorator——本计划让其 retry 分类价值主张在 .task.xml E2E 路径真正可用）

## Purpose

修复 `AbstractObjFunctionExecutable` 的 5 个 `doInvoke*` 方法在捕获被调用方法异常后无条件包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL).forWrap()`、丢弃原 `NopException.bizFatal=true` 标记的缺陷。修复后，当被调用方法抛出 bizFatal 异常时，包装后的异常必须仍报告 `isBizFatal()==true`，从而使 plan 247 的 exception 持久化 + plan 246 的 retry decorator 在 `.task.xml` 端到端路径下真正实现「不可恢复异常立即 honest throw（执行次数=1，不重试）」，而非当前的无条件重试至 retryCount 耗尽。

**为何需要独立 successor**：plan 247 scope 严控在 `TaskStepStateBean` 2 个方法体（nop-task-core 内部），xpl 包装位于框架核心引擎（nop-kernel/nop-xlang，Protected Area），plan-first 要求独立计划。本计划以最小手术式修复（5 个 catch 块的包装语义）闭合 plan 247 §Follow-up 明确切出的 carry-over，不扩大到 attr 读/写包装（独立代码路径）或其他 NopException 语义标记。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path）：

- **5 个 doInvoke* 方法无条件包装并丢失 bizFatal（核心缺口）**：`AbstractObjFunctionExecutable`（`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractObjFunctionExecutable.java`）的 `doInvoke`（`:55-62`）、`doInvoke0`（`:72-79`）、`doInvoke1`（`:81-88`）、`doInvoke2`（`:90-97`）、`doInvoke3`（`:99-107`）每个 catch 块均为：
  `throw newError(ERR_EXEC_INVOKE_METHOD_FAIL, e).forWrap().param(ARG_CLASS_NAME, getClassName(obj)).param(ARG_FUNC_NAME, funcName);`
- **cause 被保留但 bizFatal 标记丢失**：`newError(errorCode, e)`（`AbstractExecutable.java:66-68`）= `new NopEvalException(errorCode, e)`，故 `getCause()` 返回原异常。但 `.forWrap()`（`NopException.java:246-249`）仅设 `wrapException=true`，新建的包装异常 `bizFatal` 字段保持默认 `false`（`NopException.java:60`），**原异常的 `bizFatal=true` 不被拷贝到包装异常**。
- **分类器直接检查接收到的异常、不解包 cause**：`RetryPolicy.isRecoverableException`（`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/retry/RetryPolicy.java:148-155`）：
  `if (exception instanceof NopException) return !((NopException) exception).isBizFatal();`
  即只检查传入异常自身的 `isBizFatal()`，不递归 `getCause()`。故包装后的 `NopEvalException`（bizFatal=false）→ 判定可恢复 → 返回 true。
- **消费路径（端到端）**：`TaskStepHelper.retry()`（`nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:138`）`retryPolicy.getRetryDelay(state.exception(), retryAttempt, stepRt)` → plan 247 修复后 `state.exception()` 返回真实异常，但该异常在 .task.xml E2E 路径下是包装后的 `NopEvalException`（bizFatal=false）→ `getRetryDelay` 内 `isRecoverableException` 返回 true → delay >= 0 → 继续重试至 `isExceedRetryCount` → 退化为无条件重试至耗尽，而非 bizFatal 的立即 honest throw。
- **plan 247 已确认的后果**：plan 247 §Phase 2 line 102/114 实现裁定 + §Closure line 162 已知限制 + §Follow-up line 181 明确记录：bizFatal 分类在 Java 单元测试层（不经 xpl 包装）已生效，但 `.task.xml` E2E 路径因 xpl 包装丢失 bizFatal 而不生效，为本 successor。
- **事务回滚不受影响（风险已降低）**：`NopException.shouldRollback`（`NopException.java:95-102`）检查的是 `isNotRollback()`（独立字段 `notRollback`，`:61/:104-110`），**不检查 `isBizFatal()`**。故保留/传播 bizFatal 标记不改变事务回滚判定，blast radius 主要集中在 retry 分类（`RetryPolicy.isRecoverableException`）这一消费路径。
- **验证 fixture 已就绪（可复用）**：`nop-task/nop-task-ext` 的 `TestReliabilityDecorators`（`nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/TestReliabilityDecorators.java`）+ `FailureSimulatorBean`（同目录）提供标准 `.task.xml` → decorator → step 抛异常 E2E 测试基座。当前 `FailureSimulatorBean` 仅提供可恢复路径 fixture（`throwRecoverable`），需新增 bizFatal fixture。
- **同类缺陷但独立代码路径（Non-Goal）**：`AbstractExecutable`（非 `AbstractObjFunctionExecutable`）的 attr 读/写包装 `ERR_EXEC_READ_ATTR_FAIL`（`AbstractExecutable.java:146`）/`ERR_EXEC_WRITE_ATTR_FAIL`（`:163`）同样 `.forWrap()` 丢失 bizFatal，但属不同代码路径、不同 error code、不同 blast radius，为独立 successor。

## Goals

- **bizFatal 标记穿透 xpl 方法调用包装**：5 个 `doInvoke*` 方法捕获被调用方法抛出的 bizFatal `NopException` 后，最终抛出的异常必须报告 `isBizFatal()==true`（不论采用「拷贝标记到包装异常」还是「bizFatal 时直接重抛原异常」的实现方式）。
- **非 bizFatal 异常包装行为零回归**：非 bizFatal 异常仍被包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)`，仍设 `wrapException=true`（`isWrapException()==true`），`getCause()` 仍返回原异常，error code 不变。
- **retry 分类 E2E 端到端成立**：`.task.xml` 声明 retry decorator → step 经 xpl 调用 bean 方法抛 bizFatal `NopException` → 经包装 → `TaskStepHelper.retry` 消费 `state.exception()`（包装异常，但 bizFatal 已保留）→ `RetryPolicy.isRecoverableException` 判定不可恢复 → 执行次数 = 1（立即 honest throw，不重试）。
- **零回归**：nop-xlang + nop-task + nop-ai-agent 既有测试全绿（xpl 方法调用是核心路径，blast radius 大，须广覆盖回归）。

## Non-Goals

- **attr 读/写包装（ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL）**：`AbstractExecutable.java:146/:163` 的同类 `.forWrap()` 丢失 bizFatal 缺陷，但属不同代码路径、不同 error code、不同 blast radius（属性读写 vs 方法调用）。Classification: successor plan required（blast-radius 控制与独立验证）。
- **其他 NopException 语义标记（notRollback / status / params 等）的传播**：仅 bizFatal 被 `RetryPolicy.isRecoverableException` 直接消费（本计划结果面）。其他标记是否应在包装时传播属独立裁定。Classification: out-of-scope improvement。
- **改变包装异常的 error code**：包装异常的 error code 保持 `ERR_EXEC_INVOKE_METHOD_FAIL`（不改为原异常 error code），仅传播 bizFatal 标记。Classification: rejected（改变 error code 会影响错误展示/日志消费方，risk profile 不同）。
- **跨重启 exception 持久化（transient 移除）**：plan 247 §Non-Goals carry-over。Classification: optimization candidate（in-memory retry 不依赖跨重启异常传递）。
- **retry loop 同步成功路径 return quirk / succeed/isDone/isSuccess/result 生命周期**：plan 247/248 carry-over，独立结果面。Classification: successor plan required（plan 248 已接管 sync-return quirk）。

## Scope

### In Scope

- `AbstractObjFunctionExecutable` 的 5 个 `doInvoke*` 方法（`doInvoke` / `doInvoke0` / `doInvoke1` / `doInvoke2` / `doInvoke3`）包装语义修复：bizFatal 标记穿透。
- xlang 层单元测试：被调用 `IEvalFunction` 抛 bizFatal `NopException` → 经 doInvoke* → 最终异常 `isBizFatal()==true`；非 bizFatal → 包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)` 且 `isBizFatal()==false` 且 `getCause()` 保留原异常。
- E2E 验证：新增 bizFatal-fail-fast E2E 测试（`nop-task/nop-task-ext`，复用 `TestReliabilityDecorators` + `FailureSimulatorBean`，新增 bizFatal fixture）。
- 零回归：nop-xlang + nop-task + nop-ai-agent 既有测试全绿。
- 设计裁定 + owner-doc 更新（若改变框架行为契约）。

### Out Of Scope

- 见 Non-Goals（attr 读/写包装 / 其他语义标记 / error code 改变 / 跨重启持久化 / sync-return quirk / 生命周期 no-op 方法 均为显式 rejected / successor）。

### 设计裁定（Pre-Adjudicated）

1. **修复目标以「可观测行为」定义，不绑死实现方式**。要求：当被调用方法抛出的 `Throwable` 是 `NopException` 且 `isBizFatal()==true` 时，doInvoke* 最终抛出的异常必须使接收方调用 `isBizFatal()` 返回 true。实现可在以下两种方式中选择（由 execution 阶段基于 blast radius / 语义一致性裁定，必要时记录到 `ai-dev/design/`）：
   - 方式 A（保守）：包装时若 cause 为 bizFatal `NopException`，将包装异常自身 `bizFatal(true)`。保留 `wrapException`/cause/errorCode 全部既有语义，blast radius 最小。
   - 方式 B（语义直接）：cause 为 bizFatal `NopException` 时直接重抛原异常（不经包装）。error code 变为原异常 error code——但本计划 Non-Goal 已排除「改变 error code」，故方式 B 若采用需重新审视 Non-Goal 边界。
   理由：plan 只描述 what（bizFatal 穿透）+ 验证，how（拷贝标记 vs 重抛）属 execution/design（Minimum Rules #10）。方式 A 为推荐方向（最小 blast radius，保留 forWrap 语义）。

2. **5 个方法统一处理**。`doInvoke` / `doInvoke0~3` 共享同一 catch 包装模式，必须全部修复（不可遗漏任一重载），否则不同 arity 的方法调用行为不一致。理由：(1) 同一缺陷类、同一类内、同一模式（Granularity Rule：bundle 同 subsystem 同模式）；(2) 遗漏任一重载会导致「参数个数不同 → bizFatal 行为不同」的隐蔽不一致。

3. **scope 控制在 `AbstractObjFunctionExecutable` 的 doInvoke*，不触及 attr 读/写包装**。理由：(1) Protected Area scope control；(2) attr 读/写（`AbstractExecutable`）是不同代码路径、不同 error code、不同 blast radius，独立验证更安全；(3) 本 carry-over 明确指向 `doInvoke*`（plan 247 §Follow-up line 181）。

4. **blast-radius 风险评估须覆盖 isBizFatal 全部消费方**。Execution Phase 1 前须 grep `isBizFatal` / `shouldRollback` 全仓消费方，确认传播 bizFatal 不引入非预期行为变更。已知：`shouldRollback` 用 `isNotRollback()`（不查 bizFatal，不受影响）；主消费方为 `RetryPolicy.isRecoverableException`。理由：框架核心改动须显式评估 blast radius，避免静默行为漂移。

## Execution Plan

### Phase 1 - doInvoke* 包装 bizFatal 穿透 + xlang 单元测试 + compile

Status: completed
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractObjFunctionExecutable.java`、`nop-kernel/nop-xlang/src/test/`（新增单元测试）

- Item Types: `Fix`（confirmed live defect：5 个 doInvoke* catch 块包装异常丢弃 bizFatal，导致 plan 247 retry 分类在 E2E 路径失效——已发运功能的静默正确性缺陷）

- [x] blast-radius 评估：grep 全仓 `isBizFatal` / `shouldRollback` 消费方，确认传播 bizFatal 不引入非预期行为变更（已知 shouldRollback 不查 bizFatal；结果记录到 daily log）
- [x] 修复 5 个 doInvoke* 方法（`doInvoke` / `doInvoke0` / `doInvoke1` / `doInvoke2` / `doInvoke3`）catch 块：被调用方法抛 bizFatal `NopException` 时，最终抛出异常 `isBizFatal()==true`（实现方式见设计裁定 1）
- [x] 新增 xlang 层单元测试：构造 `IEvalFunction` mock 抛 bizFatal `NopException` → 经 doInvoke*（覆盖至少一个重载，理想覆盖全部 5 个）→ 断言最终异常 `isBizFatal()==true`、`getCause()` 保留原异常
- [x] 新增 xlang 层单元测试（对偶/回归）：mock 抛非 bizFatal 异常 → 经 doInvoke* → 断言最终异常为 `NopEvalException`、error code = `ERR_EXEC_INVOKE_METHOD_FAIL`、`isBizFatal()==false`、`isWrapException()==true`、`getCause()` 保留原异常

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 5 个 doInvoke* 方法均已修复（读码可复核：bizFatal 异常路径下最终抛出异常 `isBizFatal()==true`，无遗漏重载）
- [x] **无静默跳过**（#24）：5 个方法均有真实实现，非空 catch / 非吞异常 / 非 TODO；bizFatal 判定缺失时显式失败而非静默返回
- [x] 新增功能各有 focused 测试覆盖（#25）：bizFatal 穿透 + 非 bizFatal 包装回归 各有断言
- [x] `./mvnw compile -pl nop-kernel/nop-xlang -am` 通过
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 既有测试全绿 + 新增单元测试全绿
- [x] 若该 Phase 改变 live baseline：框架异常包装行为变更 → 评估是否需更新 `docs-for-ai/02-core-guides/error-handling.md`（bizFatal 在 xpl 方法调用包装下的传播行为）；裁定结果记录到 daily log（需更新则 Phase 2 完成，否则显式 `No owner-doc update required: <理由>`）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 实现 landing）

### Phase 2 - E2E bizFatal fail-fast 验证 + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/`（`FailureSimulatorBean` 新增 bizFatal fixture + `TestReliabilityDecorators` 新增 E2E 测试 + 对应 `.task.xml`）、`docs-for-ai/`（若 Phase 1 裁定需更新）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 在 `FailureSimulatorBean` 新增 bizFatal fixture 方法（如 `throwBizFatal()`）：抛 `NopException(...).bizFatal(true)`，供 .task.xml E2E 经 xpl 调用触发包装路径
- [x] 新增对应 `.task.xml`（声明 retry decorator）+ `TestReliabilityDecorators` E2E 测试（如 `retry_bizFatalFailFastE2e`）：step 经 xpl 调用 `throwBizFatal()` → 包装（bizFatal 已保留）→ `TaskStepHelper.retry` 消费 `state.exception()` → `RetryPolicy.isRecoverableException` 判定不可恢复 → 断言执行次数 = 1（立即 honest throw，不重试）+ 抛出异常 `isBizFatal()==true`
- [x] 零回归：nop-xlang + nop-task-core + nop-task-ext（含 plan 246/247 交付的 decorator / state 测试）+ nop-ai-agent 既有测试全绿
- [x] owner-doc 更新（依 Phase 1 裁定）：error-handling.md 反映 bizFatal 在 xpl 方法调用包装下的传播；或显式 `No owner-doc update required`
- [x] 回写 plan 247 §Follow-up line 181 successor 归属（已由本计划接管）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] bizFatal fail-fast E2E 测试全绿（核心验证：执行次数 = 1，不重试，honest throw bizFatal 异常）——**此测试在 Phase 1 修复前会失败**（包装丢失 bizFatal → 判定可恢复 → 重试至耗尽，执行次数 = 1 + maxRetryCount），修复后通过。位于 `TestReliabilityDecorators#retry_bizFatalFailFastE2e`（或等价名）
- [x] **端到端验证**（#22）：从 `.task.xml` 声明 retry decorator → step 经 xpl 调用 bean 方法抛 bizFatal `NopException` → doInvoke* 包装（bizFatal 保留）→ `TaskStepHelper.retry:176 state.fail(e)` → `state.exception()` 返回包装异常 → `:138 retryPolicy.getRetryDelay` → `isRecoverableException` 判定不可恢复（delay < 0）→ 立即 honest throw（执行次数 = 1）完整路径跑通
- [x] **接线验证**（#23）：doInvoke* 包装后的异常确实被 `TaskStepHelper.retry` 消费且 bizFatal 被分类器读取（E2E 测试断言执行次数 = 1 + 抛出异常 `isBizFatal()==true` 可观测——修复前执行次数 = 1 + maxRetryCount、bizFatal=false）
- [x] **无静默跳过**（#24）：doInvoke* 5 方法均有真实 catch 处理；bizFatal fixture 真实抛 bizFatal 异常（非 stub）
- [x] 新增功能各有 focused 测试覆盖（#25）：Phase 1 xlang 单元测试（bizFatal 穿透 + 非 bizFatal 回归）+ Phase 2 E2E（bizFatal fail-fast）
- [x] 零回归：`./mvnw test -pl nop-kernel/nop-xlang -am` + `./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-task/nop-task-ext -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿
- [x] owner-doc 裁定已落地（更新或显式 No owner-doc update required）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 5 个 doInvoke* 方法修复为真实代码（bizFatal 异常路径下最终抛出异常 `isBizFatal()==true`）
- [x] bizFatal 穿透在 `.task.xml` E2E 路径端到端成立：不可恢复异常执行次数 = 1（立即 honest throw），不再无条件重试至耗尽（闭合 plan 247 §Closure line 162 已知限制 + §Follow-up line 181）
- [x] 非 bizFatal 异常包装行为零回归（仍 NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)、isWrapException==true、getCause 保留、isBizFatal==false）
- [x] blast-radius 评估已完成（isBizFatal 消费方已核对，shouldRollback 不受影响已确认，无非预期行为漂移）
- [x] 必要 focused verification 已完成（xlang 单元测试 + E2E fail-fast + E2E 回归）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（attr 读/写包装 / 其他语义标记 / error code 改变 均为显式 Non-Goals 独立 successor）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）doInvoke* → TaskStepHelper.retry → RetryPolicy 调用链在运行时连通（E2E 执行次数 = 1 可观测），（b）无空方法体/静默跳过/no-op 作为正常实现，（c）bizFatal 标记确实从被调用方法经包装传递到分类器（E2E 抛出异常 isBizFatal()==true）
- [x] `./mvnw compile -pl nop-kernel/nop-xlang -am`
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` + `nop-task-core` + `nop-task-ext` + `nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### attr 读/写包装（ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `AbstractExecutable.java:146/:163` 的属性读/写包装同样 `.forWrap()` 丢失 bizFatal，但属不同代码路径、不同 error code（ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL）、不同 blast radius（属性读写 vs 方法调用）。retry 分类的 E2E 路径（.task.xml → bean 方法调用）只经 doInvoke*，不经 attr 读/写。本计划结果面（方法调用包装）独立成立。
- Successor Required: yes
- Successor Path: 独立 successor plan（待创建）

## Non-Blocking Follow-ups

- **attr 读/写包装 bizFatal 穿透**：Classification: successor plan required（ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL 同类缺陷，独立代码路径）。
- **其他 NopException 语义标记包装传播裁定**（notRollback / status / params）：Classification: out-of-scope improvement（仅 bizFatal 被 RetryPolicy 直接消费，其他标记是否应传播需独立裁定）。
- **跨重启 exception 持久化**（移除 transient + 异常序列化）：Classification: optimization candidate（plan 247 carry-over，in-memory retry 不依赖跨重启异常传递）。

## Closure

Status Note: Plan 249 fully executed against live code. Phase 1 修复 `AbstractObjFunctionExecutable` 5 个 `doInvoke*` 方法（统一委托 `wrapInvokeException:78-85`，bizFatal 路径在 `:82-83` 拷贝标记到包装异常，非 bizFatal 路径保留 `forWrap`/cause/errorCode 既有语义）；Phase 2 新增 `throwBizFatal` fixture + `retry-decorator-bizfatal-failfast/v1.task.xml` + `retry_bizFatalFailFastE2e` E2E。端到端链路运行时连通（`.task.xml` → `doInvoke0` 包装 → `TaskStepHelper.retry:138` → `RetryPolicy.java:152` 实读 `isBizFatal` → 不可恢复 → delay<0 → 立即 honest throw，执行次数=1）。`shouldRollback` 用 `isNotRollback()`（不受影响）。attr 读/写包装为显式 successor（`readAttr:146`/`setAttr:163` 仍旧模式）。闭合 plan 247 §Closure line 162 已知限制 + §Follow-up line 181。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: independent-closure-audit subagent (task_id: ses_1269bf564ffeG14run4E6khpnn, fresh session)
- Audit Session: ses_1269bf564ffeG14run4E6khpnn
- Evidence:
  - [A1] PASS: `AbstractObjFunctionExecutable.java:56-118`（5 方法均委托 `wrapInvokeException`）+ `wrapInvokeException:78-85`（bizFatal 拷贝 `:82-83`，非 bizFatal 路径 `forWrap`+cause 保留 `:79-81`）
  - [A2] PASS: helper 真实逻辑，无 stub/TODO/空方法体
  - [A3] PASS: `AbstractExecutable.readAttr:146`/`setAttr:163` 仍旧 `.forWrap()` 模式（out-of-scope 未触及）
  - [B1] PASS: `TestInvokeMethodBizFatalWrap`（bizFatal 5 重载 `:67` + 非 bizFatal 回归断言齐备 `:55-61`）
  - [B2] PASS: `TestReliabilityDecorators.retry_bizFatalFailFastE2e:153,160` 双断言（`isBizFatal` + 执行次数=1）
  - [B3] PASS: `retry-decorator-bizfatal-failfast/v1.task.xml` 声明 retry decorator + 调 `throwBizFatal()`
  - [B4] PASS: `FailureSimulatorBean.throwBizFatal():48-51` 抛真实 `NopException.bizFatal(true)`
  - [C] PASS: 运行时链路连通，bizFatal 在 `RetryPolicy.java:152` 实读（非空壳类型）
  - [D] PASS: `NopException.shouldRollback:100` 用 `isNotRollback()`，不查 `isBizFatal()`，事务回滚不受影响
  - [E] PASS: attr 读/写包装显式 successor（Non-Goals:42 + Deferred:146-151 + Follow-ups:155 三处记录，`readAttr:146` 仍为活缺陷未被静默降级）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（36 items 全勾选）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码 0（唯一发现为预存 `MacroScriptTagCompiler:67`，与本计划无关）
  - Anti-Hollow 检查结果：通过（运行时链路连通 + 无空方法体 + bizFatal 经包装实际传到分类器，E2E 抛出异常 `isBizFatal()==true` 可观测）
  - 测试执行（审计期间重跑）:
    - `./mvnw test -pl nop-kernel/nop-xlang -am -Dtest=TestInvokeMethodBizFatalWrap` → 2/0/0 BUILD SUCCESS
    - `./mvnw test -pl nop-task/nop-task-ext -am -Dtest=TestReliabilityDecorators#retry_bizFatalFailFastE2e` → 1/0/0 BUILD SUCCESS
    - `./mvnw test -pl nop-task/nop-task-ext -am -Dtest=TestReliabilityDecorators` → 18/0/0 BUILD SUCCESS
    - `./mvnw test -pl nop-kernel/nop-xlang`（全量零回归）→ 428/0/0(2 skipped) BUILD SUCCESS
  - 实施阶段零回归（全模块）:
    - `./mvnw test -pl nop-task/nop-task-core,nop-task-ext -am` → 34 + 25 / 0 fail / BUILD SUCCESS
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 2714 / 0 fail / BUILD SUCCESS
  - Deferred 项分类检查：无 in-scope live defect 被降级（attr 读/写包装为显式 successor）

Follow-up:

- attr 读/写包装 bizFatal 穿透（`ERR_EXEC_READ_ATTR_FAIL` / `ERR_EXEC_WRITE_ATTR_FAIL`）: successor plan required（`AbstractExecutable.java:146/:163` 仍为活缺陷）
- 其他 NopException 语义标记包装传播裁定（`notRollback` / `status` / `params`）: out-of-scope improvement（仅 bizFatal 被 RetryPolicy 直接消费）
- 跨重启 exception 持久化（移除 `transient` + 异常序列化）: optimization candidate（plan 247 carry-over，in-memory retry 不依赖跨重启异常传递）
- bizFatal 完整语义（retry/rollback 交互）的 owner-doc 文档化: out-of-scope improvement（更适合 reliability/retry 上下文，非 error-handling.md）

## Follow-up Handled By

- `ai-dev/plans/250-nop-ai-agent-xpl-attr-bizfatal-wrap-preserve.md` — attr 读/写包装 bizFatal 穿透（`readAttr:146` / `setAttr:163`），carry-over 本计划 §Non-Goals:42 / §Deferred:146-151 / §Closure Follow-up:194 三处一致标记的独立 successor。本计划已修复方法调用路径（doInvoke*），plan 250 修复其 sibling 属性读/写路径。
