# 255 nop-task TaskFlowAnalyzer nextOnError 校验字段错配 bug 修复

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: task-flow-analyzer-nextOnError-validation-bug (L4, carry-over from plan 254)

> Last Reviewed: 2026-06-19 (closure audit approved)
> Source: carry-over from `ai-dev/plans/254-nop-ai-agent-terminal-failure-failed-driver.md`（§Non-Blocking Follow-ups「`TaskFlowAnalyzer.checkStepRef` nextOnError 校验 bug（用 `getNext()` 代替 `getNextOnError()`）——独立 successor」；Closure §Follow-up 末项同）。plan 254 closure audit 将其裁定为「独立 successor」，无前置依赖。NEXT_ITEM priority P1、type carry-over。
> Related: `254`（发现并 defer 该 bug 的 plan；其 `TaskStepExecution` 错误分支运行时消费 `nextOnError` 路由）、`252`/`253`（task step state 生命周期）

## Purpose

修复 `TaskFlowAnalyzer.checkStepRef` 中 `nextOnError` 引用校验的**字段错配** bug：`nextOnError` 分支用 `hasStep(subStep.getNext())` 校验，而非 `hasStep(subStep.getNextOnError())`，导致两类校验语义错误（假阴性阻塞合法配置 + 假阳性放行非法引用）。修复后 task flow 错误处理路由（`nextOnError`，运行时由 plan 254 `TaskStepExecution` 错误分支消费）的**模型校验层**与运行时行为一致。

## Current Baseline

基于 live repo 核对（`TaskFlowAnalyzer.java` 已读，bug 确认 live）：

- **bug 确认 live**：`nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskFlowAnalyzer.java` `:56-63` 的 `nextOnError` 分支：
  - `:56` `if (subStep.getNextOnError() != null)` — 进入条件正确（`getNextOnError()`）
  - `:57` `if (!stepsModel.hasStep(subStep.getNext()))` — **BUG**：`hasStep` 实参为 `getNext()` 而非 `getNextOnError()`
  - `:61` `.param(ARG_NEXT_STEP, subStep.getNextOnError())` — 错误参数正确（`getNextOnError()`），仅 `:57` 的 `hasStep` 实参错配
- **对照（正确的 `next` 分支）**：`:47-54` 校验 `hasStep(subStep.getNext())`，字段与分支匹配，行为正确。
- **两类语义错误（bug 影响）**：
  - **假阴性（spurious error，阻塞合法配置）**：`nextOnError` 已设但 `next` 为 null → `hasStep(null)` 返回 false → 抛 `ERR_TASK_UNKNOWN_NEXT_STEP`，即使 `nextOnError` 指向合法 step。**阻塞所有「只设 nextOnError、不设 next」的合法错误处理配置**。
  - **假阳性（invalid 通过，静默错误）**：`nextOnError` 指向不存在 step，但 `next` 指向合法 step → `hasStep(next)` 返回 true → 不抛错，无效 `nextOnError` 通过校验，运行时路由到不存在 step 才暴露。
- **消费侧**：`nextOnError` 运行时路由由 plan 254 `TaskStepExecution` 错误分支（`nextStepNameOnError` / `buildErrorResult`）消费。本计划仅修复 **analyze-time 模型校验**，不涉及运行时路由（plan 254 已交付）。
- **错误码**：`nextOnError` 分支复用 `ERR_TASK_UNKNOWN_NEXT_STEP`（`:16` import，与 `next` 分支共用）。
- **既有测试风险**：若存在断言 buggy 行为的测试（如「nextOnError + null next 抛错」），修复后会失败，须同步更新为正确语义。执行时复核。

## Goals

- **字段错配修复**：`TaskFlowAnalyzer.checkStepRef` `:57` 的 `hasStep` 实参从 `subStep.getNext()` 改为 `subStep.getNextOnError()`，使 `nextOnError` 分支校验自身字段。
- **校验语义正确**：合法 `nextOnError` 引用（含 `next` 为 null）通过校验；无效 `nextOnError` 引用被拒绝（抛 `ERR_TASK_UNKNOWN_NEXT_STEP`）。
- **focused 回归测试**：覆盖 (a) 合法 nextOnError + null next 通过；(b) 无效 nextOnError 被拒；(c) `next` 分支零回归。
- **零回归**：`next` / `waitSteps` / `waitErrorSteps` 校验不变；nop-task-core + nop-task-ext + nop-ai-agent 既有测试全绿。

## Non-Goals

- **`next` / `waitSteps` / `waitErrorSteps` 校验逻辑**：行为正确，不在本计划 scope。Classification: rejected（非 defect）。
- **运行时 `nextOnError` 路由**：plan 254 `TaskStepExecution` 错误分支已交付，本计划仅修 analyze-time 校验。Classification: rejected（已交付）。
- **`nextOnError` 专属错误码**（如 `ERR_TASK_UNKNOWN_NEXT_ON_ERROR_STEP`）：当前复用 `ERR_TASK_UNKNOWN_NEXT_STEP` 可接受；是否引入专属错误码属 execution 裁定（Minimum Rules #10），非本计划目标。Classification: optimization candidate。
- **xpl 方法调用包装穿透 cancellation**：不同子系统（nop-xlang），独立 carry-over。Classification: successor plan required（不同 package，不满足 bundling 条件）。
- **continuation-skip / afterLoad-beforeSave / EXPIRED-KILLED / 跨重启持久化**：均依赖 DB-backed state persistence，blocked。Classification: successor plan required。

## Scope

### In Scope

- `TaskFlowAnalyzer.java:57` 字段错配修复（`getNext()` → `getNextOnError()`）。
- focused 单元测试：nextOnError 校验正确性三类场景 + next 分支零回归。
- 零回归验证：nop-task-core + nop-task-ext + nop-ai-agent 全绿。

### Out Of Scope

- 见 Non-Goals（next/wait 校验 / 运行时路由 / 专属错误码 / xpl cancellation / DB-backed 持久化相关 carry-over 均为显式 rejected / successor / optimization）。

### Granularity Justification

本计划为单一 work item（1 行字段错配修复 + focused 测试），同文件（`TaskFlowAnalyzer.java`）、同模式（字段错配 → 校验自身字段）。不与其余 carry-over 合并，理由：(1) **无同 package 小项可 bundle**——其余 carry-over（xpl cancellation 在 nop-xlang、DB 持久化相关在 state 层）均在不同 package，不满足 bundling「same subsystem/package」条件；(2) **已确认 P1 live defect，不可 defer**（Minimum Rules #16：confirmed live defect 不能延期），且位于 task flow 引擎 analyze-time 校验路径（Protected Area，plan-first 规则适用），无法跳过 plan 仪式直接修复；(3) plan 254 closure 已将其裁定为「独立 successor（无前置依赖）」。

## Execution Plan

### Phase 1 - nextOnError 字段错配修复 + focused 测试 + 零回归

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskFlowAnalyzer.java`（`:57`）+ `nop-task-core` 测试源（`io.nop.task.builder` 包下新增/扩展 `TestTaskFlowAnalyzer`）

- Item Types: `Fix`（plan 254 §Follow-up 确认的 live defect：字段错配导致校验语义错误）

- [x] 复核 `:56-63` `nextOnError` 分支结构：确认 `:57` `hasStep` 实参为 `subStep.getNext()`（bug），`:61` 错误参数为 `subStep.getNextOnError()`（正确）
- [x] 修复 `:57`：`hasStep(subStep.getNext())` → `hasStep(subStep.getNextOnError())`
- [x] 复核是否存在断言 buggy 行为的既有测试（如「nextOnError + null next 抛错」）；若存在则更新为正确语义
- [x] 新增 focused 测试：合法 `nextOnError`（指向存在 step）+ `next` 为 null → analyze 不抛错（回归假阴性 case）
- [x] 新增 focused 测试：无效 `nextOnError`（指向不存在 step）+ `next` 指向合法 step → analyze 抛 `ERR_TASK_UNKNOWN_NEXT_STEP`（回归假阳性 case）
- [x] 新增/确认测试：合法 `next`（`:47-54` 分支）→ analyze 不抛错（零回归）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TaskFlowAnalyzer.java:57` `hasStep` 实参为 `subStep.getNextOnError()`（读码可复核）
- [x] 合法 nextOnError + null next 通过 analyze 校验（不抛 `ERR_TASK_UNKNOWN_NEXT_STEP`）
- [x] 无效 nextOnError 引用被 analyze 拒绝（抛 `ERR_TASK_UNKNOWN_NEXT_STEP`，`ARG_NEXT_STEP` param 为 nextOnError 值）
- [x] `next` 分支（`:47-54`）零回归（合法 next 通过、非法 next 被拒）
- [x] **无静默跳过**（#24）：修复为真实字段引用（非 TODO / 非 placeholder / 非 no-op）
- [x] 新增功能各有 focused 测试覆盖（#25）：nextOnError 校验三类场景 + next 零回归
- [x] owner-doc 裁定：`No owner-doc update required`（内部校验 bug 修复，task flow public 契约不变，校验语义从「错误」收敛为「正确」）
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am` + `nop-task-ext` + `nop-ai-agent` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `nextOnError` 分支校验自身字段（`getNextOnError()`），与 `next` 分支字段各自匹配
- [x] 假阴性（合法 nextOnError 被拒）+ 假阳性（无效 nextOnError 通过）两类语义错误均消除
- [x] `next` / `waitSteps` / `waitErrorSteps` 校验零回归
- [x] 必要 focused verification 已完成（nextOnError 三类场景 + next 零回归）
- [x] 零回归：nop-task-core + nop-task-ext + nop-ai-agent 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：(a) 修复为真实字段引用且 analyze 调用链 runtime 可达；(b) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core -am`
- [x] `./mvnw test -pl nop-task/nop-task-core -am` + `nop-task-ext` + `nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / successor plan required / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- xpl 方法调用包装穿透 cancellation 字符（不同子系统 nop-xlang）——独立 successor
- continuation-skip 消费侧 / afterLoad-beforeSave / EXPIRED-KILLED / 跨重启持久化——依赖 DB-backed state persistence successor

## Closure

Status Note: completed — 字段错配修复已交付，独立 closure-audit APPROVED。
Completed: 2026-06-19

Closure Audit Evidence:

- **Independent subagent closure-audit**（task `ses_12493a2b7ffeyDixbix4SHnZmP`，read-only explore agent，独立 task_id）verdict: **APPROVED FOR CLOSURE**，逐条证据如下：
  - **Exit #1（字段修复 + 邻近分支零回归）PASS**：`TaskFlowAnalyzer.java:57` 读作 `if (!stepsModel.hasStep(subStep.getNextOnError()))`（正确字段）；`next` 分支 `:48` 仍为 `subStep.getNext()`（不变）；`waitSteps`/`waitErrorSteps` 分支未触及。
  - **Exit #2（Anti-Hollow 真实字段引用）PASS**：`:57` 为真实 `hasStep(subStep.getNextOnError())` 调用，位于 `checkStepRef`→`analyze`（`:25`）活跃路径，无 TODO/placeholder/no-op。
  - **Exit #3（focused 测试覆盖三类场景）PASS**：`TestTaskFlowAnalyzer.java` 5 测试——(a) 假阴性 `nextOnError_valid_andNextNull_analyzePasses`（nextOnError 合法 + next null → 不抛）；(b) 假阳性 `nextOnError_invalid_andNextValid_analyzeRejects`（nextOnError 无效 + next 合法 → 抛 `ERR_TASK_UNKNOWN_NEXT_STEP`，`ARG_NEXT_STEP=="nonexistent"`）；(c) 零回归 `next_valid_analyzePasses` + `next_invalid_analyzeRejects`；附加 `nextOnError_valid_distinctFromNext_analyzePasses`。
  - **Exit #4（三模块全绿）PASS**：执行 `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS，exit 0。per-module surefire 聚合：nop-task-core 53（0F/0E）、nop-task-ext 42（0F/0E）、nop-ai-agent 2714（0F/0E）。`TestTaskFlowAnalyzer` 贡献 nop-task-core 的 5/53。
  - **Exit #5（无既有测试断言 buggy 行为）PASS**：唯一 nextOnError E2E fixture `nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/next-step-on-error-failure/v1.task.xml` 同时设 `next="errorHandler"` + `nextOnError="errorHandler"` 均指向合法 step，从不触及 bug 的失败模式（null-next / 无效 nextOnError），修复后保持绿。
- **Closure Gate checkstyle PASS**：新测试文件 package `io.nop.task.builder` 与源码一致；import 分组与同级测试一致；4-space 缩进；JUnit 5。（注：root `pom.xml` 的 maven-checkstyle-plugin `<execution>` 被注释，checkstyle 不在 build 中运行；gate 由 compile + test 成功 + 房屋风格一致性满足。模块实际 import 顺序为 io.nop.* 在 third-party 前，与 AGENTS.md 文档声明顺序存在 docs-vs-code 偏差，非 closure blocker。）
- **Roadmap 同步**：`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` L4-8 表新增 `task-flow-analyzer-nextOnError-validation-bug` 行，标记 ✅（plan 254 行的「successor」已闭合）。
- **owner-doc 裁定**：`No owner-doc update required`（内部校验 bug 修复，task flow public 契约不变）。
