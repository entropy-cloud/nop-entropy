# 250 xpl 属性读/写异常包装保留 bizFatal 标记（retry 分类 attr 路径端到端修复）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: xpl-attr-bizfatal-wrap-preserve (carry-over from plan 249 / layer L4-8)

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/249-nop-ai-agent-xpl-bizfatal-wrap-preserve.md`（§Non-Goals:42「attr 读/写包装 ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL … successor plan required」+ §Deferred But Adjudicated:146-151「Successor Required: yes」+ §Closure Follow-up:194「attr 读/写包装 bizFatal 穿透 … `AbstractExecutable.java:146/:163` 仍为活缺陷」+ Closure Audit Evidence [E] 三处一致记录）。plan 249 已修复「方法调用包装（doInvoke*）」路径的 bizFatal 穿透，本计划修复其明确切出的 sibling——「属性读/写包装（readAttr/setAttr）」路径。属框架核心引擎（nop-kernel/nop-xlang，Protected Area，plan-first）。
> Related: `249`（交付方法调用路径 doInvoke* bizFatal 穿透——本计划修复同一缺陷类的属性读/写 sibling，复用其已验证的修复模式 `wrapInvokeException`）、`247`（交付 TaskStepStateBean exception 持久化，retry 分类价值主张的源头）、`246`（交付 retry/timeout/rateLimit decorator）

## Purpose

修复 `AbstractExecutable` 的 `readAttr`（`:146`）/`setAttr`（`:163`）在捕获属性读/写异常后无条件包装为 `NopEvalException(ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL).forWrap()`、丢弃原 `NopException.bizFatal=true` 标记的缺陷。修复后，当被访问属性的 getter/setter 抛出 bizFatal 异常时，包装后的异常必须仍报告 `isBizFatal()==true`，从而使 retry 分类在「.task.xml → xpl 属性访问」端到端路径下对不可恢复异常立即 honest throw（执行次数=1，不重试），而非当前的无条件重试至 retryCount 耗尽。

这是 plan 249（已 completed）明确切出的活缺陷 successor：plan 249 修复了方法调用路径（doInvoke*），但属性读/写路径（readAttr/setAttr，**bracket 记法 `obj[attrExpr]`**）是不同代码路径、不同 error code、不同 blast radius，故独立成计划。修复模式已被 plan 249 验证（`AbstractObjFunctionExecutable.wrapInvokeException:78-85`），可直接复用。

> **Scope 边界提示**：本计划仅覆盖 **bracket/attr 路径**（`readAttr`/`setAttr`，error code `ERR_EXEC_READ_ATTR_FAIL`/`ERR_EXEC_WRITE_ATTR_FAIL`）。dot 记法 `obj.prop` 走 `readProp`/`setProp`（`ERR_EXEC_READ_PROP_FAIL`/`ERR_EXEC_WRITE_PROP_FAIL`），存在**同类缺陷**但属独立代码路径，显式 adjudicate 为 successor（见 Non-Goals），不在本计划修复。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path）：

- **2 个属性读/写方法无条件包装并丢失 bizFatal（核心缺口）**：`AbstractExecutable`（`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java`）：
  - `readAttr`（`:139-149`）catch 块（`:145-148`）：`throw newError(ERR_EXEC_READ_ATTR_FAIL, e).forWrap().param(ARG_CLASS_NAME, obj.getClass().getName()).param(ARG_ATTR_VALUE, attrValue);`
  - `setAttr`（`:155-166`）catch 块（`:162-165`）：`throw newError(ERR_EXEC_WRITE_ATTR_FAIL, e).forWrap().param(ARG_CLASS_NAME, obj.getClass().getName()).param(ARG_ATTR_VALUE, attrValue);`
- **路径可达（非死代码，bracket 记法）**：`readAttr`/`setAttr` 被 3 个 executable 表达式调用——`GetAttrExecutable`（属性读，编译 **bracket 记法 `obj[attrExpr]`**：`GetAttrExecutable.display()` 在 `:53-55` 渲染为 `objExpr[attrExpr]`、`:94` 调 `readAttr`）、`SetAttrExecutable`（属性写，`obj[attrExpr] = v`）、`SelfAssignAttrExecutable`（自赋值，`obj[attrExpr] += v`），均位于 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`。**注意区分**：dot 记法 `obj.prop` 走**另一条路径**（`GetPropertyExecutable`/`SetPropertyExecutable` → `AbstractPropertyExecutable.readProp`/`setProp`），是同类缺陷的独立 sibling（见 Non-Goals）。即 xpl **bracket 属性访问**表达式会真实触发 readAttr/setAttr 包装路径。
- **cause 被保留但 bizFatal 标记丢失**：`newError(errorCode, e)`（`AbstractExecutable.java:66-68`）= `new NopEvalException(errorCode, e)`，故 `getCause()` 返回原异常。但 `.forWrap()`（`NopException.java:246`）仅设 `wrapException=true`，新建包装异常 `bizFatal` 字段保持默认 `false`（`NopException.java:229`），**原异常 `bizFatal=true` 不被拷贝**。
- **分类器直接检查接收异常、不解包 cause**：`RetryPolicy.isRecoverableException`（`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/retry/RetryPolicy.java:152`）：`return !((NopException) exception).isBizFatal();` 即只检查传入异常自身 `isBizFatal()`，不递归 `getCause()`。故包装后 `NopEvalException`（bizFatal=false）→ 判定可恢复 → 返回 true。
- **已验证的修复模式（plan 249，可直接复用）**：`AbstractObjFunctionExecutable.wrapInvokeException`（`AbstractObjFunctionExecutable.java:78-85`）：`.forWrap()` 包装后追加 `if (e instanceof NopException && ((NopException) e).isBizFatal()) err.bizFatal(true);`（`:82-83`）。注意该 helper 位于子类 `AbstractObjFunctionExecutable`，本计划修复点位于其父类 `AbstractExecutable`，故 helper 不能直接调用（要么内联到两处 catch，要么在 `AbstractExecutable` 提取同类 helper——属 execution 裁定）。
- **消费路径（端到端）**：`.task.xml` retry-decorated step 经 xpl **bracket 属性访问**（`obj[attrExpr]` → `GetAttrExecutable`/`SetAttrExecutable`）→ 属性 getter/setter 抛 bizFatal `NopException` → readAttr/setAttr 包装（bizFatal 丢失）→ `TaskStepHelper.retry`（`nop-task/nop-task-core/.../TaskStepHelper.java:138`）消费 `state.exception()`（包装异常，bizFatal=false）→ `RetryPolicy.isRecoverableException` 返回 true → 退化为无条件重试至耗尽，而非 bizFatal 的立即 honest throw。
- **事务回滚不受影响（风险已降低）**：`NopException.shouldRollback`（`NopException.java:95-102`）检查 `isNotRollback()`（`:100`，独立字段），**不检查 `isBizFatal()`**。故传播 bizFatal 标记不改变事务回滚判定。blast radius 主要集中在 retry 分类（`RetryPolicy.isRecoverableException`）这一消费路径。该结论已被 plan 249 Closure Audit Evidence [D] 验证。
- **E2E fixture 基座已就绪（可复用/扩展）**：`nop-task/nop-task-ext` 的 `FailureSimulatorBean`（`nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/FailureSimulatorBean.java`）+ `TestReliabilityDecorators`（同目录）+ `.task.xml` 提供标准 E2E 测试基座。plan 249 已新增 `throwBizFatal()`（`:48-51`，方法调用 fixture）。本计划需新增「属性访问」fixture（属性 getter/setter 抛 bizFatal），因为 attr 路径经属性 get/set 触发，不经方法调用。

## Goals

- **bizFatal 标记穿透 xpl 属性读/写包装**：`readAttr`/`setAttr` 捕获属性 getter/setter 抛出的 bizFatal `NopException` 后，最终抛出的异常必须报告 `isBizFatal()==true`（不论采用「拷贝标记到包装异常」还是复用 plan 249 `wrapInvokeException` 模式）。
- **非 bizFatal 异常包装行为零回归**：非 bizFatal 异常仍被包装为 `NopEvalException(ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL)`，仍设 `wrapException=true`，`getCause()` 返回原异常，error code 不变，`isBizFatal()==false`。
- **retry 分类在 attr 路径端到端成立**：`.task.xml` retry-decorated step 经 xpl 属性访问触发 bizFatal `NopException` → 经包装 → `TaskStepHelper.retry` 消费 `state.exception()`（包装异常，bizFatal 已保留）→ `RetryPolicy.isRecoverableException` 判定不可恢复 → 执行次数 = 1（立即 honest throw，不重试）。
- **零回归**：nop-xlang + nop-task + nop-ai-agent 既有测试全绿。

## Non-Goals

- **其他 NopException 语义标记（notRollback / status / params 等）的传播**：仅 bizFatal 被 `RetryPolicy.isRecoverableException` 直接消费（本计划结果面）。其他标记是否应在包装时传播属独立裁定。Classification: out-of-scope improvement（plan 249 §Non-Goals carry-over）。
- **改变包装异常的 error code**：包装异常 error code 保持 `ERR_EXEC_READ_ATTR_FAIL` / `ERR_EXEC_WRITE_ATTR_FAIL`（不改为原异常 error code），仅传播 bizFatal 标记。Classification: rejected（改变 error code 影响错误展示/日志消费方，risk profile 不同——plan 249 §Non-Goals carry-over）。
- **跨重启 exception 持久化（transient 移除）**：Classification: optimization candidate（plan 247 carry-over，in-memory retry 不依赖跨重启异常传递）。
- **属性读/写包装（dot 记法 / prop 路径：`ERR_EXEC_READ_PROP_FAIL` / `ERR_EXEC_WRITE_PROP_FAIL`）**：`AbstractPropertyExecutable.readProp`（`AbstractPropertyExecutable.java:117-118`）/`setProp`（`:68-69`）及 getter/setter executable（`GetterGetPropertyExecutable`/`SetterSetPropertyExecutable`/`StaticGetterGetPropertyExecutable`）存在**同类** `.forWrap()` 丢失 bizFatal 缺陷（dot 记法 `obj.prop` 路径）。Classification: successor plan required（confirmed live defect，但属不同代码路径、不同 error code、不同 blast radius——attr/bracket 路径经 `IBeanModel.getProperty`/`Map.get`，prop/dot 路径经 `IPropertyGetter`/`IPropertySetter` 反射调用，独立验证更安全。同 plan 249 将 doInvoke*/attr 各自拆分 successor 的 rationale）。
- **方法调用路径包装（doInvoke*）**：plan 249 已 completed 修复，不在本计划 scope。Classification: already landed。
- **readIndex / setByIndex 等其他包装路径**：`AbstractExecutable.readIndex:135` / `setByIndex:151` 直接委托 `BeanTool`，不经 `newError(...).forWrap()` 包装（无 bizFatal 丢失问题），不在 scope。Classification: 不适用。

## Scope

### In Scope

- `AbstractExecutable` 的 `readAttr`（`:146`）/`setAttr`（`:163`）包装语义修复：bizFatal 标记穿透。
- xlang 层单元测试：属性 getter/setter 抛 bizFatal `NopException` → 经 readAttr/setAttr → 最终异常 `isBizFatal()==true`；非 bizFatal → 包装为 `NopEvalException(ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL)` 且 `isBizFatal()==false` 且 `getCause()` 保留原异常。
- E2E 验证：新增 attr 路径 bizFatal fail-fast E2E 测试（`nop-task/nop-task-ext`，扩展 `FailureSimulatorBean` + `TestReliabilityDecorators` + 新增 `.task.xml`）。
- 零回归：nop-xlang + nop-task + nop-ai-agent 既有测试全绿。
- 设计裁定 + owner-doc 更新（若改变框架行为契约）。

### Out Of Scope

- 见 Non-Goals（其他语义标记 / error code 改变 / 跨重启持久化 / doInvoke* / readIndex 路径 均为显式 rejected / already-landed / 不适用）。

### 设计裁定（Pre-Adjudicated）

1. **修复目标以「可观测行为」定义，不绑死实现方式**。要求：当属性 getter/setter 抛出的 `Throwable` 是 `NopException` 且 `isBizFatal()==true` 时，readAttr/setAttr 最终抛出的异常必须使接收方调用 `isBizFatal()` 返回 true。实现方式（由 execution 阶段裁定，必要时记录到 `ai-dev/design/`）：
   - 方式 A（保守，推荐）：catch 包装后若 cause 为 bizFatal `NopException`，将包装异常自身 `bizFatal(true)`。保留 `wrapException`/cause/errorCode 全部既有语义，blast radius 最小，与 plan 249 `wrapInvokeException:82-83` 模式一致。
   - 方式 B：在 `AbstractExecutable` 提取同类 helper（如 `wrapAttrException`），两处 catch 统一委托。属内部重构，行为等价方式 A。
   理由：plan 只描述 what（bizFatal 穿透）+ 验证，how（内联 vs helper）属 execution/design（Minimum Rules #10）。

2. **readAttr 与 setAttr 必须统一修复**。二者共享同一 catch 包装模式（`.forWrap().param(ARG_CLASS_NAME, ...).param(ARG_ATTR_VALUE, ...)`），必须全部修复，否则属性读 vs 属性写的 bizFatal 行为不一致。理由：(1) 同一缺陷类、同一类内、同一模式（Granularity Rule：bundle 同 subsystem 同模式）；(2) 遗漏任一会致「读属性 vs 写属性 → bizFatal 行为不同」的隐蔽不一致。

3. **scope 控制在 readAttr/setAttr（bracket 路径），不触及 doInvoke*（plan 249 已修复）、dot/prop 路径（readProp/setProp，独立 successor）、或其他包装路径**。理由：(1) Protected Area scope control + blast-radius 控制与独立验证（同 plan 249 拆分 rationale）；(2) doInvoke* 已由 plan 249 修复并验证；(3) dot/prop 路径（`AbstractPropertyExecutable.readProp`/`setProp`）是同类缺陷但不同代码路径、不同 error code、不同反射机制（`IPropertyGetter`/`IPropertySetter`），需独立 successor 验证；(4) readIndex/setByIndex 无 `.forWrap()` 包装（不适用）。

4. **blast-radius 复用 plan 249 已验证结论 + 增量核对**。`isBizFatal` 主消费方为 `RetryPolicy.isRecoverableException:152`；`shouldRollback` 用 `isNotRollback()`（不查 bizFatal，已验证不受影响）。Execution Phase 1 前须 grep `isBizFatal` 全仓消费方增量确认传播 bizFatal 不引入非预期行为变更（结论应与 plan 249 一致，因同一标记、同一传播机制）。

## Execution Plan

### Phase 1 - readAttr/setAttr 包装 bizFatal 穿透 + xlang 单元测试 + compile

Status: completed
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractExecutable.java`（readAttr `:146` / setAttr `:163`）、`nop-kernel/nop-xlang/src/test/`（新增单元测试）

- Item Types: `Fix`（confirmed live defect：readAttr/setAttr catch 块包装异常丢弃 bizFatal，导致 retry 分类在 attr E2E 路径失效——plan 249 已确认的 sibling 活缺陷，不得降级为 Follow-up）

- [x] blast-radius 评估：grep 全仓 `isBizFatal` 消费方，增量确认传播 bizFatal 不引入非预期行为变更（复用 plan 249 结论：shouldRollback 不查 bizFatal；主消费方为 RetryPolicy；结果记录到 daily log）
- [x] 修复 `readAttr`（`:146`）catch 块：属性 getter 抛 bizFatal `NopException` 时，最终抛出异常 `isBizFatal()==true`（实现方式见设计裁定 1）
- [x] 修复 `setAttr`（`:163`）catch 块：属性 setter 抛 bizFatal `NopException` 时，最终抛出异常 `isBizFatal()==true`
- [x] 新增 xlang 层单元测试（readAttr）：构造 bean/beanModel 使属性 getter 抛 bizFatal `NopException` → 经 readAttr → 断言最终异常 `isBizFatal()==true`、`getCause()` 保留原异常
- [x] 新增 xlang 层单元测试（readAttr 回归）：getter 抛非 bizFatal 异常 → 经 readAttr → 断言最终异常为 `NopEvalException`、error code = `ERR_EXEC_READ_ATTR_FAIL`、`isBizFatal()==false`、`isWrapException()==true`、`getCause()` 保留原异常
- [x] 新增 xlang 层单元测试（setAttr）：属性 setter 抛 bizFatal → 经 setAttr → 断言 `isBizFatal()==true`；非 bizFatal → 回归断言（error code = `ERR_EXEC_WRITE_ATTR_FAIL`、`isBizFatal()==false`、`getCause()` 保留）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] readAttr + setAttr 均已修复（读码可复核：bizFatal 异常路径下最终抛出异常 `isBizFatal()==true`，无遗漏）
- [x] **无静默跳过**（#24）：两处 catch 块均有真实实现，非空 catch / 非吞异常 / 非 TODO；bizFatal 判定缺失时显式失败
- [x] 新增功能各有 focused 测试覆盖（#25）：readAttr bizFatal 穿透 + readAttr 非 bizFatal 回归 + setAttr bizFatal + setAttr 非 bizFatal 各有断言
- [x] `./mvnw compile -pl nop-kernel/nop-xlang -am` 通过
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 既有测试全绿 + 新增单元测试全绿
- [x] 若该 Phase 改变 live baseline：框架异常包装行为变更 → 评估是否需更新 `docs-for-ai/02-core-guides/error-handling.md`（bizFatal 在 xpl 属性读/写包装下的传播行为）；裁定结果记录到 daily log（需更新则 Phase 2 完成，否则显式 `No owner-doc update required: <理由>`）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 实现 landing）

### Phase 2 - E2E attr 路径 bizFatal fail-fast 验证 + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/`（`FailureSimulatorBean` 新增属性访问 bizFatal fixture + `TestReliabilityDecorators` 新增 E2E 测试 + 对应 `.task.xml`）、`docs-for-ai/`（若 Phase 1 裁定需更新）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 在 `FailureSimulatorBean` 新增 bracket 属性访问 bizFatal fixture（如属性 getter `getBizFatalAttr()` 或 setter 抛 `NopException(...).bizFatal(true)`），供 .task.xml E2E 经 xpl **bracket 记法**（`obj[attrExpr]`，触发 `GetAttrExecutable`/`SetAttrExecutable` → readAttr/setAttr，**不是** dot 记法 `obj.prop`）触发包装路径（区别于 plan 249 的方法调用 `throwBizFatal()`）
- [x] 新增对应 `.task.xml`（声明 retry decorator，step 经 xpl **bracket 属性访问** `obj[attrExpr]` 触发 fixture）+ `TestReliabilityDecorators` E2E 测试（如 `retry_bizFatalAttrFailFastE2e`）：step 经 bracket 属性访问 → 包装（bizFatal 已保留）→ `TaskStepHelper.retry` 消费 `state.exception()` → `RetryPolicy.isRecoverableException` 判定不可恢复 → 断言执行次数 = 1（立即 honest throw，不重试）+ 抛出异常 `isBizFatal()==true`
- [x] 零回归：nop-xlang + nop-task-core + nop-task-ext（含 plan 246/247/249 交付的 decorator / state / 方法调用路径测试）+ nop-ai-agent 既有测试全绿
- [x] owner-doc 更新（依 Phase 1 裁定）：error-handling.md 反映 bizFatal 在 xpl 属性读/写包装下的传播；或显式 `No owner-doc update required`
- [x] 回写 plan 249 §Follow-up line 194 successor 归属（已由本计划接管）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] attr 路径 bizFatal fail-fast E2E 测试全绿（核心验证：执行次数 = 1，不重试，honest throw bizFatal 异常）——**此测试在 Phase 1 修复前会失败**（包装丢失 bizFatal → 判定可恢复 → 重试至耗尽），修复后通过
- [x] **端到端验证**（#22）：从 `.task.xml` 声明 retry decorator → step 经 xpl **bracket 属性访问** `obj[attrExpr]`（`GetAttrExecutable`/`SetAttrExecutable`）→ 属性 getter/setter 抛 bizFatal `NopException` → readAttr/setAttr 包装（bizFatal 保留）→ `TaskStepHelper.retry:138` → `state.exception()` 返回包装异常 → `RetryPolicy.java:152` 判定不可恢复（delay < 0）→ 立即 honest throw（执行次数 = 1）完整路径跑通
- [x] **接线验证**（#23）：readAttr/setAttr 包装后的异常确实被 `TaskStepHelper.retry` 消费且 bizFatal 被分类器读取（E2E 测试断言执行次数 = 1 + 抛出异常 `isBizFatal()==true` 可观测——修复前执行次数 = 1 + maxRetryCount、bizFatal=false）
- [x] **无静默跳过**（#24）：readAttr/setAttr catch 块均有真实处理；attr bizFatal fixture 真实抛 bizFatal 异常（非 stub）
- [x] 新增功能各有 focused 测试覆盖（#25）：Phase 1 xlang 单元测试（readAttr/setAttr bizFatal 穿透 + 回归）+ Phase 2 E2E（attr 路径 bizFatal fail-fast）
- [x] 零回归：`./mvnw test -pl nop-kernel/nop-xlang -am` + `./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-task/nop-task-ext -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿
- [x] owner-doc 裁定已落地（更新或显式 No owner-doc update required）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] readAttr + setAttr 修复为真实代码（bizFatal 异常路径下最终抛出异常 `isBizFatal()==true`）
- [x] bizFatal 穿透在 attr 路径 `.task.xml` E2E 端到端成立：不可恢复异常执行次数 = 1（立即 honest throw），不再无条件重试至耗尽（闭合 plan 249 §Follow-up line 194 + §Deferred:146-151）
- [x] 非 bizFatal 异常包装行为零回归（仍 NopEvalException(ERR_EXEC_READ_ATTR_FAIL / ERR_EXEC_WRITE_ATTR_FAIL)、isWrapException==true、getCause 保留、isBizFatal==false）
- [x] blast-radius 评估已完成（isBizFatal 消费方已核对，shouldRollback 不受影响，无非预期行为漂移）
- [x] 必要 focused verification 已完成（xlang 单元测试 readAttr/setAttr + E2E attr fail-fast）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（其他语义标记 / error code 改变 / 跨重启持久化 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）readAttr/setAttr → TaskStepHelper.retry → RetryPolicy 调用链在 attr 路径运行时连通（E2E 执行次数 = 1 可观测），（b）无空方法体/静默跳过/no-op，（c）bizFatal 标记确实从属性 getter/setter 经包装传递到分类器（E2E 抛出异常 isBizFatal()==true）
- [x] `./mvnw compile -pl nop-kernel/nop-xlang -am`
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` + `nop-task-core` + `nop-task-ext` + `nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 out-of-scope improvement / rejected / already-landed / 不适用，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- **其他 NopException 语义标记包装传播裁定**（notRollback / status / params）：Classification: out-of-scope improvement（仅 bizFatal 被 RetryPolicy 直接消费，plan 249 carry-over）。
- **跨重启 exception 持久化**（移除 transient + 异常序列化）：Classification: optimization candidate（plan 247 carry-over，in-memory retry 不依赖跨重启异常传递）。

## Closure

Status Note: Plan 250 fully executed against live code. Phase 1 修复 `AbstractExecutable.readAttr`(`:146`)/`setAttr`(`:162`) 两处 catch 块统一委托新增 private `wrapAttrException`(`:172-179`) helper：`.forWrap()` 包装后当 cause 为 bizFatal `NopException` 时 `err.bizFatal(true)`（设计裁定 1 方式 B：保守拷贝标记，与 plan 249 `wrapInvokeException:78-85` 模式一致），非 bizFatal 路径保留 forWrap/cause/errorCode 既有语义。Phase 2 新增 `getBizFatalAttr`/`getRecoverableAttr` getter fixture + `retry-decorator-attr-bizfatal-failfast/v1.task.xml`（bracket 记法 `sim['bizFatalAttr']`）+ `retry_bizFatalAttrFailFastE2e` E2E。端到端链路运行时连通（`.task.xml` retry decorator → `GetAttrExecutable.execute` → `readAttr:94` 包装 bizFatal 保留 → `TaskStepHelper.retry:138` → `RetryPolicy.java:152` 实读 `isBizFatal` → 不可恢复 → delay<0 → 立即 honest throw，执行次数=1）。`shouldRollback` 用 `isNotRollback()`（独立字段，不受影响）。闭合 plan 249 §Non-Goals:42 + §Deferred:146-151 + §Closure Follow-up:194 的 attr-path successor。doInvoke*（plan 249）+ readAttr/setAttr（plan 250）两条 xpl 包装路径 bizFatal 穿透均已收敛；dot/prop 路径（readProp/setProp）仍为显式 successor。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task id `ses_12653eebdffeI3uQE1w2Wn58rx`，general subagent type，非实现阶段 session）
- Audit Session: ses_12653eebdffeI3uQE1w2Wn58rx
- Evidence:
  - Phase 1 Exit Criteria 全 PASS：readAttr(`:146`)+setAttr(`:162`) 修复为真实代码，bizFatal 路径下 `err.bizFatal(true)`（`:176-177`），无空 catch/吞异常/TODO/continue。非 bizFatal 路径 forWrap/cause/errorCode 保留、bizFatal=false（`TestAttrBizFatalWrap` 4 单元测试断言）。`./mvnw compile -pl nop-kernel/nop-xlang -am` BUILD SUCCESS；`./mvnw test -pl nop-kernel/nop-xlang` → **432 tests / 0 fail / 2 skipped**（plan 249 基线 428 → +4 新增）。
  - Phase 2 Exit Criteria 全 PASS：`FailureSimulatorBean.getBizFatalAttr():63`/`getRecoverableAttr():73` 真实抛 bizFatal/非 bizFatal NopException；`retry-decorator-attr-bizfatal-failfast/v1.task.xml:7` 真用 bracket 记法 `sim['bizFatalAttr']`；`TestReliabilityDecorators.retry_bizFatalAttrFailFastE2e` 断言 counter==1（`:193`）+ isBizFatal()==true（`:186`）。`./mvnw test -pl nop-task/nop-task-ext -am -Dtest=TestReliabilityDecorators` → **19 tests / 0 fail**（plan 249 基线 18 → +1）。
  - 端到端验证（#22）PASS：`.task.xml` retry decorator → xpl bracket `sim['bizFatalAttr']` → `GetAttrExecutable.execute:94` 调 `readAttr`（非 Integer attr 走 readAttr 非 readIndex/readProp）→ 包装 bizFatal 保留 → `TaskStepHelper.retry:138` → `RetryPolicy.java:152` 判定不可恢复 → delay<0 → 立即 honest throw → counter==1 完整路径运行时连通。
  - 接线验证（#23）PASS：readAttr 包装后的异常被 `TaskStepHelper.retry` 消费（state.exception() 返回包装异常），bizFatal 被 `RetryPolicy.isRecoverableException` 读取（E2E 断言执行次数=1 + 抛出异常 isBizFatal()==true）。**Anti-Hollow 反向验证**：临时回退 `wrapAttrException` bizFatal 拷贝后 `retry_bizFatalAttrFailFastE2e` FAIL（`expected:<true> but was:<false>`，wrap errorCode=`nop.err.xlang.exec.read-attr-fail`），恢复后 PASS——证明测试有意义、修复必要。
  - 零回归 PASS：`./mvnw test -pl nop-kernel/nop-xlang` 432 / `nop-task-core` 34 / `nop-task-ext` 26 / `nop-ai-agent -am -T 1C` 2714 tests 全 0 fail（nop-ai-agent 与 plan 249 基线一致）。
  - blast-radius PASS：`isBizFatal` 消费方 = RetryPolicy/RetryEngineImpl/NamedExceptionFilter + 4 error-metadata propagators + plan 249 wrapInvokeException；`NopException.shouldRollback:95-102` 用 `isNotRollback()`（`:100`，独立字段 `notRollback:61`），不查 isBizFatal，事务回滚不受影响。
  - owner-doc 裁定 PASS：daily log `06-18.md:14` 显式 `No owner-doc update required`（error-handling.md 是面向应用开发者指南，未描述 xpl 内部异常包装/bizFatal 语义，本修复对齐已落地 method-invocation 路径，无新反直觉契约）。
  - Deferred 项分类检查 PASS：dot/prop 路径（readProp/setProp）/其他语义标记/error code 改变/跨重启持久化 均为显式 Non-Goals（successor/rejected/out-of-scope/optimization），无 in-scope live defect 被降级到 follow-up。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/250-nop-ai-agent-xpl-attr-bizfatal-wrap-preserve.md --strict` 退出码为 0（Closure Evidence 已写入，无未勾选项）。
  - Anti-Hollow 检查结果：组件间调用链运行时连通（E2E 执行次数=1 + isBizFatal==true 可观测）；无空方法体/静默跳过/no-op；bizFatal 标记确实从属性 getter 经 `wrapAttrException` 包装传递到分类器。

Follow-up:

- dot/prop 路径 bizFatal 穿透（`AbstractPropertyExecutable.readProp`/`setProp`，error code `ERR_EXEC_READ_PROP_FAIL`/`ERR_EXEC_WRITE_PROP_FAIL`）：successor plan required（同类 `.forWrap()` 丢失 bizFatal 缺陷，但 dot 记法 `obj.prop` 经 `IPropertyGetter`/`IPropertySetter` 反射调用，独立代码路径/不同 error code/不同 blast radius，需独立验证）。
- 其他 NopException 语义标记包装传播裁定（notRollback/status/params）：out-of-scope improvement（仅 bizFatal 被 RetryPolicy 直接消费，plan 249 carry-over）。
- bizFatal 完整语义（retry/rollback 交互）的 owner-doc 文档化：out-of-scope improvement（更适合 reliability/retry 上下文，非 error-handling.md，plan 249 carry-over）。
- 跨重启 exception 持久化（移除 transient + 异常序列化）：optimization candidate（plan 247 carry-over，in-memory retry 不依赖跨重启异常传递）。
