# 251 xpl 属性读/写异常包装保留 bizFatal 标记（retry 分类 prop/dot 路径端到端修复）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: xpl-prop-bizfatal-wrap-preserve (carry-over from plan 250 / dot 记法 sibling)

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/250-nop-ai-agent-xpl-attr-bizfatal-wrap-preserve.md`（§Non-Goals:46「属性读/写包装（dot 记法 / prop 路径：`ERR_EXEC_READ_PROP_FAIL` / `ERR_EXEC_WRITE_PROP_FAIL`）…successor plan required」+ §Closure Follow-up:180「dot/prop 路径 bizFatal 穿透…successor plan required」+ §Closure Status Note 末句「dot/prop 路径（readProp/setProp）仍为显式 successor」三处一致记录）。plan 249 已修复「方法调用包装（doInvoke*）」路径，plan 250 已修复「bracket 属性读/写包装（readAttr/setAttr）」路径，本计划修复其共同明确切出的 sibling——「dot 记法属性读/写包装（readProp/setProp）」路径。属框架核心引擎（nop-kernel/nop-xlang，Protected Area，plan-first）。
> Related: `249`（交付方法调用路径 doInvoke* bizFatal 穿透）、`250`（交付 bracket 属性读/写路径 readAttr/setAttr bizFatal 穿透——本计划修复同一缺陷类的 dot 记法 sibling，复用其已验证的修复模式 `wrapAttrException`）

## Purpose

修复 `AbstractPropertyExecutable.readProp`/`setProp` 及其子类覆写、`StaticGetterGetPropertyExecutable.execute` 共 **5 处 catch 块** 在捕获属性读/写异常后无条件包装为 `NopEvalException(ERR_EXEC_READ_PROP_FAIL / ERR_EXEC_WRITE_PROP_FAIL).forWrap()`、丢弃原 `NopException.bizFatal=true` 标记的缺陷。修复后，当 dot 记法属性访问（`obj.prop`）的 getter/setter 抛出 bizFatal 异常时，包装后的异常必须仍报告 `isBizFatal()==true`，从而使 retry 分类在「.task.xml → xpl dot 属性访问」端到端路径下对不可恢复异常立即 honest throw（执行次数=1，不重试），而非当前的无条件重试至 retryCount 耗尽。

这是 plan 250（已 completed）明确切出的活缺陷 successor：plan 250 修复了 bracket 记法路径（readAttr/setAttr，经 `IBeanModel.getProperty`/`Map.get`），本计划修复 dot 记法路径（readProp/setProp，经 `IPropertyGetter`/`IPropertySetter` 反射调用）——不同代码路径、不同 error code、不同反射机制，故独立成计划。修复模式已被 plan 249（`AbstractObjFunctionExecutable.wrapInvokeException:78-85`）和 plan 250（`AbstractExecutable.wrapAttrException:172-179`）双重验证，可直接复用。

> **Scope 边界提示**：本计划覆盖 **dot/prop 路径全部 catch 块**（见 Current Baseline 5 个修复点）。方法调用路径（doInvoke*，plan 249）、bracket 路径（readAttr/setAttr，plan 250）均已收敛，不在本计划 scope。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path，行号基于当前 HEAD）：

- **5 处属性读/写 catch 块无条件包装并丢失 bizFatal（核心缺口，dot 记法）**，全部位于 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`：
  1. **`AbstractPropertyExecutable.readProp`**（`AbstractPropertyExecutable.java:113-120`）catch 块（`:116-119`）：`throw newError(ERR_EXEC_READ_PROP_FAIL, e).forWrap().param(ARG_CLASS_NAME, obj.getClass().getName()).param(ARG_PROP_NAME, propName);` —— 基类，无 bizFatal 拷贝。
  2. **`AbstractPropertyExecutable.setProp`**（`:64-71`）catch 块（`:67-70`）：`throw newError(ERR_EXEC_WRITE_PROP_FAIL, e).forWrap().param(ARG_CLASS_NAME, ...).param(ARG_PROP_NAME, propName).forWrap();` —— 基类，无 bizFatal 拷贝，且 `.forWrap()` 被重复调用两次（`:68` + `:69`，幂等无副作用但为冗余）。
  3. **`GetterGetPropertyExecutable.readProp`**（`GetterGetPropertyExecutable.java:63-70`）catch 块（`:66-69`）：覆写基类，**自带独立 catch 块**，同样 `.forWrap()` 包装无 bizFatal 拷贝。用于 method-reference getter（如静态/注解属性），不经 `getGetter()` 解析。
  4. **`SetterSetPropertyExecutable.setProp`**（`SetterSetPropertyExecutable.java:77-84`）catch 块（`:80-83`）：覆写基类，**自带独立 catch 块**，同样 `.forWrap()` 包装无 bizFatal 拷贝。
  5. **`StaticGetterGetPropertyExecutable.execute`**（`StaticGetterGetPropertyExecutable.java:42-49`）catch 块（`:45-48`）：直接继承 `AbstractExecutable`（**非** `AbstractPropertyExecutable`），`throw newError(ERR_EXEC_READ_PROP_FAIL, e).forWrap().param(ARG_CLASS_NAME, className).param(ARG_PROP_NAME, propName);` —— 静态属性读 `Cls.prop`，独立 catch 块，无 bizFatal 拷贝。
- **覆写但无需独立修复（基类修复自动覆盖）**：`MakePropertyExecutable.readProp`（`MakePropertyExecutable.java:55-60`）覆写基类但仅调 `super.readProp(obj, reader, scope)` 后做 null 检查，无独立 catch 块——基类 readProp 修复后自动覆盖。
- **路径可达（非死代码，dot 记法 `obj.prop`）**：`readProp`/`setProp` 被以下 executable 表达式调用——`GetPropertyExecutable:105`（dot 属性读，编译 `obj.prop`，编译期解析 `getGetter()`，用基类 readProp）、`SetPropertyExecutable:72`（dot 属性写，`obj.prop = v`，用基类 setProp）、`SelfAssignPropertyExecutable:77,84`（自赋值 `obj.prop += v`，用基类 readProp/setProp）、`GetterGetPropertyExecutable:60`/`SetterSetPropertyExecutable:73`（method-reference getter/setter，用各自覆写）。`StaticGetterGetPropertyExecutable:44` 编译静态属性读 `Cls.prop`。即 xpl **dot 属性访问**表达式会真实触发 readProp/setProp 包装路径。
- **cause 被保留但 bizFatal 标记丢失**：`newError(errorCode, e)`（`AbstractExecutable.java:66-68`）= `new NopEvalException(errorCode, e)`，故 `getCause()` 返回原异常。但 `.forWrap()`（`NopException.java:246`）仅设 `wrapException=true`，新建包装异常 `bizFatal` 字段保持默认 `false`（`NopException.java:229`），**原异常 `bizFatal=true` 不被拷贝**。（与 plan 249/250 完全相同的根因。）
- **分类器直接检查接收异常、不解包 cause**：`RetryPolicy.isRecoverableException`（`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/retry/RetryPolicy.java:151-152`）：`if (exception instanceof NopException) return !((NopException) exception).isBizFatal();` 即只检查传入异常自身 `isBizFatal()`，不递归 `getCause()`。故包装后 `NopEvalException`（bizFatal=false）→ 判定可恢复 → 返回 true。
- **已验证的修复模式（plan 249 + plan 250，可直接复用）**：
  - `AbstractObjFunctionExecutable.wrapInvokeException`（`AbstractObjFunctionExecutable.java:78-85`，plan 249）：`.forWrap()` 包装后追加 `if (e instanceof NopException && ((NopException) e).isBizFatal()) err.bizFatal(true);`
  - `AbstractExecutable.wrapAttrException`（`AbstractExecutable.java:172-179`，plan 250）：提取 private helper，统一两处 catch 委托，模式与上一致。
  - 本计划修复点分布在 `AbstractPropertyExecutable`（2 处 + 2 子类覆写）和 `StaticGetterGetPropertyExecutable`（1 处，继承 `AbstractExecutable` 非 `AbstractPropertyExecutable`），故 helper 放置位置（内联 vs AbstractPropertyExecutable helper vs AbstractExecutable helper）属 execution 裁定（见设计裁定 1）。
- **消费路径（端到端）**：`.task.xml` retry-decorated step 经 xpl **dot 属性访问**（`obj.prop` → `GetPropertyExecutable`/`SetPropertyExecutable`）→ 属性 getter/setter 抛 bizFatal `NopException` → readProp/setProp 包装（bizFatal 丢失）→ `TaskStepHelper.retry`（`nop-task/nop-task-core/.../TaskStepHelper.java:138`）消费 `state.exception()`（包装异常，bizFatal=false）→ `RetryPolicy.isRecoverableException` 返回 true → 退化为无条件重试至耗尽，而非 bizFatal 的立即 honest throw。
- **事务回滚不受影响（风险已降低，plan 249/250 双重验证）**：`NopException.shouldRollback`（`NopException.java:95-102`）检查 `isNotRollback()`（`:100`，独立字段），**不检查 `isBizFatal()`**。故传播 bizFatal 标记不改变事务回滚判定。blast radius 主要集中在 retry 分类（`RetryPolicy.isRecoverableException`）这一消费路径。该结论已被 plan 249 Closure Audit Evidence [D] + plan 250 Closure Audit Evidence 验证。
- **E2E fixture 基座已就绪（可复用/扩展）**：`nop-task/nop-task-ext` 的 `FailureSimulatorBean`（`nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/FailureSimulatorBean.java`）+ `TestReliabilityDecorators`（同目录）+ `.task.xml` 提供标准 E2E 测试基座。plan 250 已新增 bracket 路径 fixture（`getBizFatalAttr`/`getRecoverableAttr`，bracket 记法 `sim['bizFatalAttr']`）。本计划需新增 dot 路径 fixture（属性 getter/setter 抛 bizFatal，经 **dot 记法** `sim.bizFatalProp` 触发，区别于 plan 250 的 bracket 记法），因为 dot 路径经 `IPropertyGetter`/`IPropertySetter` 反射调用触发包装，与 bracket 路径（`IBeanModel.getProperty`）机制不同。

## Goals

- **bizFatal 标记穿透 xpl dot 属性读/写包装（全部 5 个 catch 块）**：`readProp`/`setProp`（基类 + 子类覆写）及 `StaticGetterGetPropertyExecutable.execute` 捕获属性 getter/setter 抛出的 bizFatal `NopException` 后，最终抛出的异常必须报告 `isBizFatal()==true`。
- **非 bizFatal 异常包装行为零回归**：非 bizFatal 异常仍被包装为 `NopEvalException(ERR_EXEC_READ_PROP_FAIL / ERR_EXEC_WRITE_PROP_FAIL)`，仍设 `wrapException=true`，`getCause()` 返回原异常，error code 不变，`isBizFatal()==false`。
- **retry 分类在 prop/dot 路径端到端成立**：`.task.xml` retry-decorated step 经 xpl dot 属性访问触发 bizFatal `NopException` → 经包装 → `TaskStepHelper.retry` 消费 `state.exception()`（包装异常，bizFatal 已保留）→ `RetryPolicy.isRecoverableException` 判定不可恢复 → 执行次数 = 1（立即 honest throw，不重试）。
- **零回归**：nop-xlang + nop-task + nop-ai-agent 既有测试全绿。
- **setProp 重复 `.forWrap()` 冗余清理**：基类 `setProp:68-69` 的 `.forWrap()` 被调用两次（幂等但冗余），修复时顺带收敛为单次（行为等价，不改变语义）。

## Non-Goals

- **其他 NopException 语义标记（notRollback / status / params 等）的传播**：仅 bizFatal 被 `RetryPolicy.isRecoverableException` 直接消费（本计划结果面）。其他标记是否应在包装时传播属独立裁定。Classification: out-of-scope improvement（plan 249 carry-over）。
- **改变包装异常的 error code**：包装异常 error code 保持 `ERR_EXEC_READ_PROP_FAIL` / `ERR_EXEC_WRITE_PROP_FAIL`（不改为原异常 error code），仅传播 bizFatal 标记。Classification: rejected（改变 error code 影响错误展示/日志消费方，risk profile 不同——plan 249 §Non-Goals carry-over）。
- **跨重启 exception 持久化（transient 移除）**：Classification: optimization candidate（plan 247 carry-over，in-memory retry 不依赖跨重启异常传递）。
- **方法调用路径包装（doInvoke*）**：plan 249 已 completed 修复。Classification: already landed。
- **bracket 属性读/写包装（readAttr/setAttr）**：plan 250 已 completed 修复。Classification: already landed。
- **readIndex / setByIndex 等其他包装路径**：`AbstractExecutable.readIndex:135` / `setByIndex:150` 直接委托 `BeanTool`，不经 `newError(...).forWrap()` 包装（无 bizFatal 丢失问题）。Classification: 不适用。
- **bizFatal 完整语义（retry/rollback 交互）的 owner-doc 文档化**：Classification: out-of-scope improvement（更适合 reliability/retry 上下文，非 error-handling.md，plan 249/250 carry-over）。

## Scope

### In Scope

- 5 处 catch 块包装语义修复（bizFatal 标记穿透）：`AbstractPropertyExecutable.readProp`/`setProp`、`GetterGetPropertyExecutable.readProp`、`SetterSetPropertyExecutable.setProp`、`StaticGetterGetPropertyExecutable.execute`。
- setProp 基类重复 `.forWrap()` 冗余收敛（行为等价清理）。
- xlang 层单元测试：属性 getter/setter 抛 bizFatal `NopException` → 经 readProp/setProp → 最终异常 `isBizFatal()==true`；非 bizFatal → 包装为 `NopEvalException(ERR_EXEC_READ_PROP_FAIL / ERR_EXEC_WRITE_PROP_FAIL)` 且 `isBizFatal()==false` 且 `getCause()` 保留原异常。
- E2E 验证：新增 prop/dot 路径 bizFatal fail-fast E2E 测试（`nop-task/nop-task-ext`，扩展 `FailureSimulatorBean` + `TestReliabilityDecorators` + 新增 `.task.xml`，用 dot 记法 `obj.prop` 触发）。
- 零回归：nop-xlang + nop-task + nop-ai-agent 既有测试全绿。
- 设计裁定 + owner-doc 更新裁定。

### Out Of Scope

- 见 Non-Goals（其他语义标记 / error code 改变 / 跨重启持久化 / doInvoke* / readAttr/setAttr / readIndex 路径 / bizFatal 文档化 均为显式 rejected / already-landed / 不适用 / out-of-scope）。

### 设计裁定（Pre-Adjudicated）

1. **修复目标以「可观测行为」定义，不绑死实现方式**。要求：当属性 getter/setter 抛出的 `Throwable` 是 `NopException` 且 `isBizFatal()==true` 时，readProp/setProp 最终抛出的异常必须使接收方调用 `isBizFatal()` 返回 true。实现方式（由 execution 阶段裁定，必要时记录到 `ai-dev/design/`）：
   - 方式 A（保守，推荐）：catch 包装后若 cause 为 bizFatal `NopException`，将包装异常自身 `bizFatal(true)`。保留 `wrapException`/cause/errorCode 全部既有语义，blast radius 最小，与 plan 249 `wrapInvokeException:82-83` 及 plan 250 `wrapAttrException:176-177` 模式一致。
   - 方式 B：在 `AbstractPropertyExecutable` 提取同类 private helper（如 `wrapPropException`），基类 + 覆写处统一委托。属内部重构，行为等价方式 A。注意 `StaticGetterGetPropertyExecutable` 继承 `AbstractExecutable` 非 `AbstractPropertyExecutable`，无法复用该 helper——需内联或在 `AbstractExecutable` 提取更通用 helper（execution 裁定）。
   理由：plan 只描述 what（bizFatal 穿透）+ 验证，how（内联 vs helper vs helper 放置位置）属 execution/design（Minimum Rules #10）。

2. **5 处 catch 块必须全部修复，不得遗漏任一**。它们分属 3 个类（`AbstractPropertyExecutable` 基类 2 处 + 2 子类覆写、`StaticGetterGetPropertyExecutable` 独立 1 处），但共享同一 `.forWrap()` 丢失 bizFatal 缺陷类。理由：(1) 同一缺陷类、同一 error code 族、同一反射机制族（Granularity Rule：bundle 同 subsystem 同模式）；(2) 遗漏任一会致「基类路径 vs 子类覆写路径 vs 静态路径 → bizFatal 行为不同」的隐蔽不一致；(3) plan 250 §Non-Goals:46 已明确将 getter/setter executable（`GetterGetPropertyExecutable`/`SetterSetPropertyExecutable`/`StaticGetterGetPropertyExecutable`）纳入 successor scope。

3. **scope 严格控制在 prop/dot 路径，不触及已收敛路径**。理由：(1) Protected Area scope control + blast-radius 控制与独立验证（同 plan 249/250 拆分 rationale）；(2) doInvoke* 已由 plan 249 修复并验证；(3) readAttr/setAttr 已由 plan 250 修复并验证；(4) readIndex/setByIndex 无 `.forWrap()` 包装（不适用）。

4. **blast-radius 复用 plan 249/250 已验证结论 + 增量核对**。`isBizFatal` 主消费方为 `RetryPolicy.isRecoverableException:151-152`；`shouldRollback` 用 `isNotRollback()`（不查 bizFatal，plan 249/250 双重验证不受影响）。Execution Phase 1 前须 grep `isBizFatal` 全仓消费方增量确认传播 bizFatal 不引入非预期行为变更（结论应与 plan 249/250 一致，因同一标记、同一传播机制）。

5. **MakePropertyExecutable 无需独立修复**。它覆写 readProp 但仅调 `super.readProp()`（`:56`）后做 null 检查（`:57-58`），无独立 catch 块——基类 readProp 修复后其 bizFatal 穿透自动生效。

## Execution Plan

### Phase 1 - readProp/setProp 5 处 catch 块 bizFatal 穿透 + xlang 单元测试 + compile

Status: completed
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/AbstractPropertyExecutable.java`（readProp `:113-120` / setProp `:64-71`）、`GetterGetPropertyExecutable.java`（readProp `:63-70`）、`SetterSetPropertyExecutable.java`（setProp `:77-84`）、`StaticGetterGetPropertyExecutable.java`（execute `:42-49`）、`nop-kernel/nop-xlang/src/test/`（新增单元测试）

- Item Types: `Fix`（confirmed live defect：5 处 catch 块包装异常丢弃 bizFatal，导致 retry 分类在 prop/dot E2E 路径失效——plan 250 已确认的 sibling 活缺陷，不得降级为 Follow-up）

- [x] blast-radius 评估：grep 全仓 `isBizFatal` 消费方，增量确认传播 bizFatal 不引入非预期行为变更（复用 plan 249/250 结论：shouldRollback 不查 bizFatal；主消费方为 RetryPolicy；结果记录到 daily log）
- [x] 修复 `AbstractPropertyExecutable.readProp`（`:116-119`）catch 块：属性 getter 抛 bizFatal `NopException` 时，最终抛出异常 `isBizFatal()==true`（实现方式见设计裁定 1）
- [x] 修复 `AbstractPropertyExecutable.setProp`（`:67-70`）catch 块：属性 setter 抛 bizFatal `NopException` 时，最终抛出异常 `isBizFatal()==true`；同时收敛重复的 `.forWrap()`（`:68`+`:69`）为单次（行为等价）
- [x] 修复 `GetterGetPropertyExecutable.readProp`（`:66-69`）覆写 catch 块：bizFatal 穿透
- [x] 修复 `SetterSetPropertyExecutable.setProp`（`:80-83`）覆写 catch 块：bizFatal 穿透
- [x] 修复 `StaticGetterGetPropertyExecutable.execute`（`:45-48`）catch 块：静态属性读 bizFatal 穿透
- [x] 新增 xlang 层单元测试（readProp 基类）：构造 bean 使属性 getter 抛 bizFatal `NopException` → 经 readProp → 断言最终异常 `isBizFatal()==true`、`getCause()` 保留原异常
- [x] 新增 xlang 层单元测试（readProp 回归）：getter 抛非 bizFatal 异常 → 经 readProp → 断言最终异常为 `NopEvalException`、error code = `ERR_EXEC_READ_PROP_FAIL`、`isBizFatal()==false`、`isWrapException()==true`、`getCause()` 保留原异常
- [x] 新增 xlang 层单元测试（setProp）：属性 setter 抛 bizFatal → 经 setProp → 断言 `isBizFatal()==true`；非 bizFatal → 回归断言（error code = `ERR_EXEC_WRITE_PROP_FAIL`、`isBizFatal()==false`、`getCause()` 保留）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 5 处 catch 块均已修复（读码可复核：bizFatal 异常路径下最终抛出异常 `isBizFatal()==true`，无遗漏——含基类 readProp/setProp + GetterGetPropertyExecutable 覆写 + SetterSetPropertyExecutable 覆写 + StaticGetterGetPropertyExecutable）
- [x] **无静默跳过**（#24）：5 处 catch 块均有真实实现，非空 catch / 非吞异常 / 非 TODO；bizFatal 判定缺失时显式失败
- [x] 新增功能各有 focused 测试覆盖（#25）：readProp bizFatal 穿透 + readProp 非 bizFatal 回归 + setProp bizFatal + setProp 非 bizFatal 各有断言
- [x] setProp 重复 `.forWrap()` 已收敛为单次（行为等价）
- [x] `./mvnw compile -pl nop-kernel/nop-xlang -am` 通过
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 既有测试全绿 + 新增单元测试全绿
- [x] 若该 Phase 改变 live baseline：框架异常包装行为变更 → 评估是否需更新 `docs-for-ai/02-core-guides/error-handling.md`（bizFatal 在 xpl 属性读/写包装下的传播行为）；裁定结果记录到 daily log（需更新则 Phase 2 完成，否则显式 `No owner-doc update required: <理由>`）→ **No owner-doc update required**（error-handling.md 面向应用开发者，未描述 xpl 内部包装机制/bizFatal 语义；本修复使 prop/dot 路径与已落地的方法调用 plan 249 + bracket plan 250 路径对齐，无新反直觉契约；与 plan 249/250 同一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 实现 landing）

### Phase 2 - E2E prop/dot 路径 bizFatal fail-fast 验证 + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/reliability/`（`FailureSimulatorBean` 新增 dot 属性访问 bizFatal fixture + `TestReliabilityDecorators` 新增 E2E 测试 + 对应 `.task.xml`）、`docs-for-ai/`（若 Phase 1 裁定需更新）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] 在 `FailureSimulatorBean` 新增 dot 属性访问 bizFatal fixture（如属性 getter `getBizFatalProp()` 或 setter 抛 `NopException(...).bizFatal(true)`），供 .task.xml E2E 经 xpl **dot 记法**（`obj.prop`，触发 `GetPropertyExecutable`/`SetPropertyExecutable` → readProp/setProp，**不是** bracket 记法 `obj[attrExpr]`）触发包装路径（区别于 plan 250 的 bracket 记法 `getBizFatalAttr`）
- [x] 新增对应 `.task.xml`（声明 retry decorator，step 经 xpl **dot 属性访问** `obj.prop` 触发 fixture）+ `TestReliabilityDecorators` E2E 测试（如 `retry_bizFatalPropFailFastE2e`）：step 经 dot 属性访问 → 包装（bizFatal 已保留）→ `TaskStepHelper.retry` 消费 `state.exception()` → `RetryPolicy.isRecoverableException` 判定不可恢复 → 断言执行次数 = 1（立即 honest throw，不重试）+ 抛出异常 `isBizFatal()==true`
- [x] 零回归：nop-xlang + nop-task-core + nop-task-ext（含 plan 246/247/249/250 交付的 decorator / state / 方法调用 / bracket 路径测试）+ nop-ai-agent 既有测试全绿
- [x] owner-doc 更新（依 Phase 1 裁定）：error-handling.md 反映 bizFatal 在 xpl 属性读/写包装下的传播；或显式 `No owner-doc update required`
- [x] 回写 plan 250 §Follow-up line 180 successor 归属（已由本计划接管）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] prop/dot 路径 bizFatal fail-fast E2E 测试全绿（核心验证：执行次数 = 1，不重试，honest throw bizFatal 异常）——**此测试在 Phase 1 修复前会失败**（包装丢失 bizFatal → 判定可恢复 → 重试至耗尽），修复后通过
- [x] **端到端验证**（#22）：从 `.task.xml` 声明 retry decorator → step 经 xpl **dot 属性访问** `obj.prop`（`GetPropertyExecutable`/`SetPropertyExecutable`）→ 属性 getter/setter 抛 bizFatal `NopException` → readProp/setProp 包装（bizFatal 保留）→ `TaskStepHelper.retry:138` → `state.exception()` 返回包装异常 → `RetryPolicy.java:151-152` 判定不可恢复（delay < 0）→ 立即 honest throw（执行次数 = 1）完整路径跑通
- [x] **接线验证**（#23）：readProp/setProp 包装后的异常确实被 `TaskStepHelper.retry` 消费且 bizFatal 被分类器读取（E2E 测试断言执行次数 = 1 + 抛出异常 `isBizFatal()==true` 可观测——修复前执行次数 = 1 + maxRetryCount、bizFatal=false）
- [x] **无静默跳过**（#24）：5 处 catch 块均有真实处理；dot bizFatal fixture 真实抛 bizFatal 异常（非 stub）
- [x] 新增功能各有 focused 测试覆盖（#25）：Phase 1 xlang 单元测试（readProp/setProp bizFatal 穿透 + 回归）+ Phase 2 E2E（prop/dot 路径 bizFatal fail-fast）
- [x] 零回归：`./mvnw test -pl nop-kernel/nop-xlang -am` + `./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-task/nop-task-ext -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿
- [x] owner-doc 裁定已落地（更新或显式 No owner-doc update required）→ **No owner-doc update required**（同 Phase 1 裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 5 处 catch 块修复为真实代码（bizFatal 异常路径下最终抛出异常 `isBizFatal()==true`）
- [x] bizFatal 穿透在 prop/dot 路径 `.task.xml` E2E 端到端成立：不可恢复异常执行次数 = 1（立即 honest throw），不再无条件重试至耗尽（闭合 plan 250 §Non-Goals:46 + §Follow-up:180）
- [x] 非 bizFatal 异常包装行为零回归（仍 NopEvalException(ERR_EXEC_READ_PROP_FAIL / ERR_EXEC_WRITE_PROP_FAIL)、isWrapException==true、getCause 保留、isBizFatal==false）
- [x] blast-radius 评估已完成（isBizFatal 消费方已核对，shouldRollback 不受影响，无非预期行为漂移）
- [x] 必要 focused verification 已完成（xlang 单元测试 readProp/setProp + E2E prop fail-fast）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（其他语义标记 / error code 改变 / 跨重启持久化 / bizFatal 文档化 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）readProp/setProp → TaskStepHelper.retry → RetryPolicy 调用链在 prop/dot 路径运行时连通（E2E 执行次数 = 1 可观测），（b）无空方法体/静默跳过/no-op，（c）bizFatal 标记确实从属性 getter/setter 经包装传递到分类器（E2E 抛出异常 isBizFatal()==true）
- [x] `./mvnw compile -pl nop-kernel/nop-xlang -am`
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` + `nop-task-core` + `nop-task-ext` + `nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 out-of-scope improvement / rejected / already-landed / 不适用，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- **其他 NopException 语义标记包装传播裁定**（notRollback / status / params）：Classification: out-of-scope improvement（仅 bizFatal 被 RetryPolicy 直接消费，plan 249/250 carry-over）。
- **跨重启 exception 持久化**（移除 transient + 异常序列化）：Classification: optimization candidate（plan 247 carry-over，in-memory retry 不依赖跨重启异常传递）。
- **bizFatal 完整语义（retry/rollback 交互）的 owner-doc 文档化**：Classification: out-of-scope improvement（更适合 reliability/retry 上下文，非 error-handling.md，plan 249/250 carry-over）。

## Closure

Status Note: Plan 251 fully executed against live code. Phase 1 修复 `AbstractPropertyExecutable.readProp`/`setProp`（基类）+ `GetterGetPropertyExecutable.readProp` + `SetterSetPropertyExecutable.setProp` + `StaticGetterGetPropertyExecutable.execute` 共 5 处 catch 块，统一委托新增 protected `wrapPropException`（`AbstractExecutable.java:190-197`）helper：`.forWrap()` 包装后当 cause 为 bizFatal `NopException` 时 `err.bizFatal(true)`（设计裁定 1 方式 B 泛化：因 `StaticGetterGetPropertyExecutable` 继承 `AbstractExecutable` 非 `AbstractPropertyExecutable`，helper 须放 `AbstractExecutable` 才能被全部 5 处复用；模式与 plan 249 `wrapInvokeException:78-85` 及 plan 250 `wrapAttrException:172-179` 一致）。基类 setProp 重复 `.forWrap()` 收敛为单次。非 bizFatal 路径保留 forWrap/cause/errorCode 既有语义。Phase 2 新增 `getBizFatalProp`/`getRecoverableProp` getter fixture + `retry-decorator-prop-bizfatal-failfast/v1.task.xml`（dot 记法 `sim.bizFatalProp`）+ `retry_bizFatalPropFailFastE2e` E2E。端到端链路运行时连通（`.task.xml` retry decorator → `GetPropertyExecutable.execute:105` → 基类 `readProp` 包装 bizFatal 保留 → `TaskStepHelper.retry:138` → `RetryPolicy.java:152` 实读 `isBizFatal` → 不可恢复 → delay<0 → 立即 honest throw，执行次数=1）。`shouldRollback` 用 `isNotRollback()`（独立字段，不受影响）。闭合 plan 250 §Non-Goals:46 + §Follow-up:180 的 prop/dot-path successor。doInvoke*（plan 249）+ readAttr/setAttr（plan 250）+ readProp/setProp（plan 251）三条 xpl 包装路径 bizFatal 穿透均已收敛，无剩余 plan-owned work。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general, session ses_12618f3f5ffe9GiS5f2wqTHXSO）— fresh closure-audit session，非实现阶段同一 session
- Audit Session: ses_12618f3f5ffe9GiS5f2wqTHXSO
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS（读码复核）：5 处 catch 块均委托 `wrapPropException`（`AbstractPropertyExecutable.readProp:116` / `setProp:68` / `GetterGetPropertyExecutable.readProp:65` / `SetterSetPropertyExecutable.setProp:79` / `StaticGetterGetPropertyExecutable.execute:44`），无遗漏、无直接 `.forWrap()` 残留、无空方法体。helper `AbstractExecutable.wrapPropException:190-197` 仅当 cause bizFatal 时 `err.bizFatal(true)`，逻辑正确。
  - Phase 1 测试 PASS：`TestPropBizFatalWrap` 4 tests（readProp bizFatal 穿透 + 非 bizFatal 回归 + setProp bizFatal + 非 bizFatal 回归），断言 isBizFatal/errorCode/getCause/isWrapException，`Tests run: 4, Failures: 0`。
  - Phase 2 Exit Criteria 全部 PASS：`FailureSimulatorBean.getBizFatalProp()` 抛 bizFatal；`retry-decorator-prop-bizfatal-failfast/v1.task.xml:7` 使用 **dot 记法** `sim.bizFatalProp`（非 bracket）；`retry_bizFatalPropFailFastE2e` 断言执行次数=1 + isBizFatal()==true，`Tests run: 20, Failures: 0`。
  - 端到端验证（#22）PASS：`.task.xml` retry decorator → `GetPropertyExecutable.execute:105`（继承 `AbstractPropertyExecutable`）→ 基类 `readProp` 包装 → `TaskStepHelper.retry:138` → `RetryPolicy.isRecoverableException:152` 实读 isBizFatal → delay<0 → honest throw（执行次数=1）。
  - 接线验证（#23）PASS：E2E 断言执行次数=1 + 抛出异常 isBizFatal()==true 可观测。
  - Anti-Hollow 检查 PASS：dot 记法 `obj.prop` → `GetPropertyExecutable`（extends `AbstractPropertyExecutable`）→ 基类 `readProp`，修复在运行时路径上（非死代码）；无空方法体/吞异常/no-op。
  - blast-radius PASS：`NopException.shouldRollback:100` 用 `isNotRollback()`（不查 isBizFatal），事务回滚不受影响；主消费方 `RetryPolicy.isRecoverableException:152`。
  - Closure Gates 每条 PASS（见上）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/251-nop-ai-agent-xpl-prop-bizfatal-wrap-preserve.md --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - Deferred 项分类检查 PASS：其他语义标记/error code 改变/跨重启持久化/bizFatal 文档化 均为显式 Non-Goals（out-of-scope/rejected/optimization），无 in-scope live defect 被降级到 follow-up。

Follow-up:

- 其他 NopException 语义标记包装传播裁定（notRollback/status/params）：out-of-scope improvement（plan 249/250 carry-over）。
- 跨重启 exception 持久化：optimization candidate（plan 247 carry-over）。
- bizFatal 完整语义 owner-doc 文档化：out-of-scope improvement（plan 249/250 carry-over）。
- no remaining plan-owned work（doInvoke*/readAttr/setAttr/readProp/setProp 三条 xpl 包装路径 bizFatal 穿透均已收敛）。
