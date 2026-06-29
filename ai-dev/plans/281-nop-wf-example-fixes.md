# 281 nop-wf 审批流示例与引擎修正计划

> Plan Status: completed
> Last Reviewed: 2026-06-28
> Source: `ai-dev/analysis/2026-06-27-nop-wf-example-files-audit.md`
> Related: `ai-dev/design/nop-wf/approval-flow-design.md`

## Purpose

将分析报告中确认的 15 个问题全部修复，使所有 15 个审批流示例可正常运行，执行分组（execGroupType）功能完整可用。

## Current Baseline

- `ExecGroupSupport.shouldExecGroupComplete()`、`shouldExecGroupReject()`、`skipExecGroupMembers()` 已实现但引擎从未调用（`WorkflowEngineImpl.doInvokeAction` 中没有调用点）
- `WorkflowStepImpl.getStepsInSameExecGroup()` 过滤逻辑反向（`Objects.equals` 应为 `!Objects.equals`），导致执行分组方法取到的是"不在同组"的步骤
- `ExecGroupSupport.isVoteGroupComplete()` 和 `isVoteGroupReject()` 使用 `passWeight` 变量名而非 `passPercent`，当 `passWeight` 为 null 时引发 NPE
- `SEQ_GROUP` 类型没有任何 `execOrder` 顺序控制逻辑
- `oa.xlib:17-19` 调用不存在的 `getStepsWithSameStepGroup` 和 `kill` 方法（三处），触发时抛 NoSuchMethodError
- `oa.xwf` 基模板的 reject action 含 `local="true"`，导致 `doReject` 创建前驱步骤后当前步骤不 exit，两个步骤同时 ACTIVE（影响 12/15 通过 `<ref-action name="reject"/>` 引用基类的示例）
- 4 个示例（`reject-withdraw/v1.xwf`、`comprehensive-leave/v1.xwf`）的 `forReject`/`forWithdraw` action 同时含 `<to-step>`，导致 `doReject`/`doWithdraw` 和 `doTransition` 双重创建步骤实例
- `subprocess/sub-workflow/v1.xwf` 的 `<output>` source 使用 `wfVars.setVar(...)`（void），返回 null
- `simple-approval/v1.xwf` 的 `allowWithdraw="true"` 无对应 `forWithdraw` action，死配置
- `comprehensive-leave/v1.xwf` 的 start arg `leaveDays` 缺 `persist="true"`，流程迁移后丢失
- `timeout-auto/v1.xwf` 的 `dueAction` 依赖外部调度器，引擎仅写 `dueTime` 字段

## Goals

- 引擎修正：完成执行分组（and-group/or-group/vote-group）的调用链接通 + 过滤逻辑修复 + 变量名修复
- 引擎修正：修复 `oa.xlib` 调用不存在方法的问题（两处：`getStepsWithSameStepGroup` 和 `kill`）
- 引擎修正：为 `SEQ_GROUP` 增加 `execOrder` 顺序控制
- 示例修正：消除 `oa.xwf` 基模板 reject 的 `local="true"`，使 12 个依赖基类的示例 reject 行为正确
- 示例修正：消除所有 P0 双重创建问题
- 示例修正：修复子流程 output 返回值、死配置、persist 缺失等逻辑缺陷
- 验证：所有 15 个示例在修正后可通过对应功能测试

## Non-Goals

- 不为示例新增测试（已有 `TestCountersign` 等，如被 `@Disabled` 则启用）
- 不涉及 wf.xdef Schema 本身的修改

## Scope

### In Scope

- `WorkflowEngineImpl.java` —— 增加 `ExecGroupSupport` 方法的调用点
- `WorkflowStepImpl.java` —— 修正 `getStepsInSameExecGroup` 过滤条件
- `ExecGroupSupport.java` —— 修正 `isVoteGroupComplete`/`isVoteGroupReject` 变量名；增加 `SEQ_GROUP` 的 `execOrder` 控制
- `oa.xlib` —— 修复 `ExitCosignStep` 中三处调用不存在的方法（`getStepsWithSameStepGroup` → `getStepsInSameExecGroup`，`kill` → `killStep`）
- `oa.xwf` 基模板 —— 修正 reject action 的 `local="true"`
- 4 个 P0 示例的 `forReject`/`forWithdraw` action 删除 `<to-step>`（连同空 `<transition>` 一并删除）
- `subprocess/sub-workflow/v1.xwf` 修正 output source
- `simple-approval/v1.xwf` 消除 `allowWithdraw` 死配置
- `comprehensive-leave/v1.xwf` 添加 `persist="true"`
- `timeout-auto/v1.xwf` 添加注释说明调度器依赖

### Out Of Scope

- 修改 `TestCountersign` 或其他测试代码本身
- 修改 `wf.xdef` Schema
- 集成外部调度器（Quartz）实现超时机制

## Execution Plan

### Phase 1 - 引擎核心：ExecGroup 调用链接通 + 过滤 + 变量名 + oa.xwf 模板修正

Status: completed

> **执行顺序**：以下 checklist 必须按 0→1→2→3→4→5→6→7 的顺序执行。

Targets: `nop-wf-core/.../engine/WorkflowEngineImpl.java`, `WorkflowStepImpl.java`, `ExecGroupSupport.java`, `oa.xwf`

- Item Types: `Fix`

0. [x] **`oa.xwf` 基模板 reject 修正**：删除 reject action（第 49 行）的 `local="true"`。此为单属性 XML 修改，无代码依赖，优先完成以消除 exec group 测试时的"步骤不退出"干扰。修正后 `doExitStep` 正常执行 → 当前步骤 exit → `doReject` 创建的前驱步骤为唯一 ACTIVE 步骤。**本项影响 12/15 个通过 `<ref-action name="reject"/>` 引用基类的示例，必须第一优先完成。**

1. [x] **`WorkflowStepImpl.getStepsInSameExecGroup()`**: 将 `Objects.equals(step.getExecGroup(), stepGroup)` 取反为 `!Objects.equals(...)`。这是所有 exec group 功能的前置条件。

2. [x] **`ExecGroupSupport.isVoteGroupComplete()`**: 将变量 `passWeight`（第 65 行）替换为 `passPercent`，添加 null 安全处理（`passPercent != null ? completeWeight * 1.0 / totalWeight >= passPercent : false`）。同时修正 `isVoteGroupReject()`（第 118 行）中同样的变量名错误。

3. [x] **`WorkflowEngineImpl.doInvokeAction()`：插入 exec group 完成检查**。在 `doTransition(step, action.getTransition(), ...)` 调用之前（现行代码 `~L1133`），插入以下逻辑：

    伪代码行为规格：
    ```
    // After forReject/forWithdraw handling, before doTransition:
    boolean shouldTransition = true;
    String stepGroup = step.getExecGroup();
    if (stepGroup != null && !action.isForReject() && !action.isForWithdraw()) {
        shouldComplete = execGroupSupport.shouldExecGroupComplete(step, ...);
        if (!shouldComplete) {
            // Group not yet complete:
            //   - skip transition (no downstream steps)
            //   - but fall through to doExitStep (the user's action WAS performed)
            shouldTransition = false;
        } else {
            // Group complete:
            if (execGroupType == OR_GROUP || execGroupType == VOTE_GROUP) {
                execGroupSupport.skipExecGroupMembers(step, ...);
            }
            // and-group: fall through to normal doTransition
        }
    }
    // Then wrap doTransition:
    if (shouldTransition && action.getTransition() != null) {
        doTransition(step, action.getTransition(), ...);
    }
    ```

    **关键设计决策**：
    - 使用 `shouldTransition` flag 而非 `return`：确保 `doExitStep`（`~L1148`）不受影响，步骤正常退出
    - 仅跳过 transition，不跳过 exit step——用户的 action 已执行，步骤应标记为完成
    - 现存 `isExecGroupComplete` 私有方法（`~L1171`，检查 `checkExecGroupComplete` XPL）保留不动：它检查的是步骤级自定义条件，而 `ExecGroupSupport.shouldExecGroupComplete()` 检查的是类型级（and/or/vote）完成逻辑，两者正交，互不替代

4. [x] **`WorkflowEngineImpl`：插入 reject 分组检查**。在 `doReject()` / `doWithdraw()` 方法内部、创建前驱步骤之后，插入：

    伪代码行为规格：
    ```
    if (step.getExecGroup() != null):
        shouldReject = execGroupSupport.shouldExecGroupReject(step, ...);
        if (shouldReject):
            execGroupSupport.skipExecGroupMembers(step, ...);
    ```

5. [x] **`ExecGroupSupport.shouldExecGroupComplete()`：SEQ_GROUP 的完成检查**。对于 `SEQ_GROUP` 类型，当前走与 `AND_GROUP` 相同的"全部完成"逻辑（已有 fallthrough 路径）。不抛出 `UnsupportedOperationException`。Phase 4 测试前此行无用户可见效果（SEQ_GROUP 成员同时激活），但保证代码路径可运行。

6. [x] **回归测试**：
    - [x] `and-group`：3 个 actor 全部 agree 后才 transition（前 2 人 agree 后步骤 exit 但无下游；第 3 人 agree 后创建 1 个下游步骤）
    - [x] `or-group`：任意 1 个 actor agree 后，其余 skipped，仅 1 个下游步骤
    - [x] `vote-group`：权重和 ≥ passPercent 后 transition，其余 skipped

Exit Criteria:

- [x] `oa.xwf` reject 执行后当前步骤正常 exit，仅前驱步骤 ACTIVE（待办列表只有 1 项）—— 基模板修正验证
- [x] `and-group`：前 N-1 人 agree 后步骤 exit 但无下游创建；最后 1 人 agree 后创建 1 个下游
- [x] `or-group`：1 人 agree 后其余 skipped，仅 1 个下游步骤
- [x] `vote-group`：权重和达到 passPercent 后 transition，不触发 NPE
- [x] `getStepsInSameExecGroup` 返回的是同一执行组的步骤列表（与 `execGroup` 字段匹配）
- [x] **端到端验证**：从 `doInvokeAction` 入口点到 `transitionToStep`，exec group 检查方法在正常 agree 路径上被调用
- [x] **接线验证**：`shouldExecGroupComplete` / `shouldExecGroupReject` / `skipExecGroupMembers` 三方法在 `WorkflowEngineImpl` 中至少有调用点（grep 确认）
- [x] `nop-wf-core` 模块 `./mvnw test` 通过
- [x] No owner-doc update required（引擎内部实现，不涉及对外契约变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎标签库：修复 oa.xlib 不存在的方法调用

Status: completed
Targets: `nop-wf-core/.../xlib/oa.xlib`

- Item Types: `Fix`

**推荐方案（Option B）**：将 `getStepsWithSameStepGroup` 和 `kill` 调用替换为已存在的方法：
- `currentStep.getStepsWithSameStepGroup(currentStep.name, false, false)` → `currentStep.getStepsInSameExecGroup(false, false)`（`getStepsInSameExecGroup` 内部已按 `model.getName()` 过滤，语义等价）
- `otherStep.kill(null, wfRt.svcCtx)` → `otherStep.killStep(null, wfRt.svcCtx)`（`killStep` 已在 `IWorkflowStep` 接口中作为 default method 存在）
- `?.kill(null, wfRt.svcCtx)` → `?.killStep(null, wfRt.svcCtx)`（Optional chaining 保留）

不采用 Option A（新增 `getStepsWithSameStepGroup` 方法），理由：无需新增 API surface，Option B 更简洁。

- [x] 修改 `oa.xlib:17`：`getStepsWithSameStepGroup` → `getStepsInSameExecGroup`
- [x] 修改 `oa.xlib:18`：`otherStep.kill` → `otherStep.killStep`
- [x] 修改 `oa.xlib:19`：`?.kill` → `?.killStep`
- [x] 验证 `ExitCosignStep` 标签触发时不抛 NoSuchMethodError（三处均通过）

Exit Criteria:

- [x] `oa.xlib:17-19` 的方法调用不再导致运行时 NoSuchMethodError
- [x] 会签场景（cosign）的驳回路径可正常执行
- [x] **接线验证**：`oa.xlib` → `getStepsInSameExecGroup` / `killStep` 的调用链在运行时连通
- [x] `nop-wf-core` 模块 `./mvnw test` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 示例修正

Status: completed
Targets: `examples/` 下 7 个 .xwf 文件

> **注意**：`oa.xwf` 基模板 reject 的 `local="true"` 修正已在 Phase 1 第 0 项完成。Phase 3 不重复处理。

- Item Types: `Fix`

- [x] `reject-withdraw/v1.xwf`：`reject-to-start` action 删除 `<to-step stepName="submit"/>` 及外层 `<transition>...</transition>`
- [x] `reject-withdraw/v1.xwf`：`withdraw` action 同上
- [x] `comprehensive-leave/v1.xwf`：`reject-to-starter` action 同上
- [x] `comprehensive-leave/v1.xwf`：`withdraw-to-submit` action 同上
- [x] `subprocess/sub-workflow/v1.xwf`：`<source>wfVars.setVar('result', 'completed')</source>` 改为 `<source>'completed'</source>`
- [x] `simple-approval/v1.xwf`：移除 `manager-approval` 步骤上的 `allowWithdraw="true"`
- [x] `comprehensive-leave/v1.xwf`：start arg `leaveDays` 添加 `persist="true"`
- [x] `timeout-auto/v1.xwf`：添加 XPL 注释说明 `dueAction` 依赖外部调度器

Exit Criteria:

- [x] 所有 4 个 `forReject`/`forWithdraw` action 不再含 `<to-step>` 和 `<transition>`
- [x] `reject-to-start` 和 `withdraw` 执行后，submit 步骤只有 1 个实例
- [x] subworkflow output 返回值为 `'completed'` 而非 null
- [x] `simple-approval/v1.xwf` 不再引用 `allowWithdraw` 属性
- [x] `comprehensive-leave` 的 `leaveDays` 在 DB 中持久化（`persist="true"`）
- [x] `timeout-auto` 有明确的调度器依赖注释
- [x] **端到端验证**：运行 `reject-withdraw` 和 `comprehensive-leave` 示例对应的测试，确认无双重创建
- [x] `./mvnw test` 通过
- [x] No owner-doc update required（示例文件修正，不涉及对外契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 引擎增强：SEQ_GROUP execOrder 顺序控制

Status: completed
Targets: `ExecGroupSupport.java`, `WorkflowEngineImpl.java`

- Item Types: `Fix | Decision`

设计规格：

1. **创建时的激活策略**：在 `newSteps()`（`WorkflowEngineImpl`，执行新步骤创建的入口）中，对 `execGroupType="seq-group"` 的步骤组，仅将 `execOrder` 最小的步骤设为 `ACTIVATED`，其余设为 `WAITING`。这需要在 `newStepForActor()` 或 `newSteps()` 中增加 `execGroupType` 判断。
2. **等待状态的语义**：`WAITING` 步骤不出现在用户待办列表中，不可操作。`checkWaitingStep` 等自动激活机制不应激活 SEQ_GROUP 的等待步骤（仅在 `activateNextSeqStep` 中激活）。
3. **进度推进**：在 `doExitStep` 调用之后（仅步骤正常 exit + complete 的场景，不包括 reject/withdraw/kill），检查当前步骤是否属于 SEQ_GROUP。如果是，调用 `activateNextSeqStep(step)`：找到同组中 `execOrder > currentStep.execOrder` 且 `execOrder` 最小的步骤，将其状态从 `WAITING` 改为 `ACTIVATED`。
4. **驳回/撤回中的 SEQ_GROUP**：驳回时，SEQ_GROUP 按普通逻辑处理（`shouldExecGroupReject`）；驳回后整组重启，重新从最小 `execOrder` 开始激活。

- [x] 修改 `newStepForActor()` 或 `newSteps()`：对 SEQ_GROUP 成员，仅最小 execOrder 设为 ACTIVATED，其余设为 WAITING
- [x] 在 `ExecGroupSupport` 中实现 `activateNextSeqStep(step)`：获取同组步骤 → 按 execOrder 排序 → 找到下一个 WAITING 步骤 → 设为 ACTIVATED
- [x] 在 `WorkflowEngineImpl` 的 `doExitStep` 之后插入 SEQ_GROUP 推进调用点（仅 complete 路径，不包括 reject/withdraw/kill）
- [x] 为 `seq-group` 编写回归测试验证：team-lead → dept-manager → vp 按顺序激活

Exit Criteria:

- [x] `sequential-approval/v1.xwf` 中 team-lead、dept-manager、vp 按 `execOrder` 依次激活（而非同时激活）
- [x] 前序步骤未完成时，后续步骤处于 `WAITING` 状态，不可操作
- [x] SEQ_GROUP 成员创建后，只有最小 `execOrder` 的步骤为 `ACTIVATED`
- [x] **端到端验证**：从 start 到所有 3 个顺序步骤完成的完整路径已验证
- [x] **接线验证**：`activateNextSeqStep` 在 `WorkflowEngineImpl` 中被调用（grep 确认），`newStepForActor` 对 SEQ_GROUP 有特殊分支
- [x] `nop-wf-core` 模块 `./mvnw test` 通过
- [x] 如果改变了行为契约，更新 `docs-for-ai/03-modules/nop-wf.md`；否则写 No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 4 个 Phase 的 Status 均为 `completed`
- [x] 所有 in-scope live defect 已修复（引擎 Bug 6 个 + 示例 Bug 9 个）
- [x] 示例行为与引擎行为一致，不再存在已知的矛盾
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：exec group 调用链已验证运行时连通；SEQ_GROUP 按序激活已验证
- [x] `./mvnw test -pl nop-wf-core,nop-wf-service -am` 通过
- [x] `./mvnw compile` 通过
- [x] checkstyle / 代码规范通过

## Non-Blocking Follow-ups

- `timeout-auto` 的外部调度器集成方案不在本计划范围内，示例已加注释说明
- `TestCountersign` 的 `@Disabled` 解除在本计划完成后由独立任务处理

## Closure

Status Note: 所有 4 个 Phase 的 Exit Criteria 已满足。引擎 exec group 调用链（shouldExecGroupComplete/shouldExecGroupReject/skipExecGroupMembers）在 doInvokeAction/doReject 中接通，SEQ_GROUP 顺序推进（activateNextSeqStep）在 doExitStep 后接通。新增 4 个回归测试（testExecGroupAnd/Or/Vote/Seq）验证 and/or/vote/seq-group 行为。独立 closure audit agent 逐文件代码审查确认全部调用链在运行时连通，无空壳实现。
Completed: 2026-06-28

Closure Audit Evidence:

- Reviewer / Agent: Independent closure audit agent (task session: ses_0f43160a0ffeiT6EwLzYsIKQMS)
- Audit Session: fresh general agent session, 不复用执行阶段 task
- Evidence:
  - Phase 1 Exit Criteria: 全部 PASS — `getStepsInSameExecGroup` 过滤取反确认（WorkflowStepImpl.java:330）；`isVoteGroupComplete`/`isVoteGroupReject` 变量名 `passPercent` 确认（ExecGroupSupport.java:65,118）；exec group 完成检查条件 `execGroupType != null` 确认（WorkflowEngineImpl.java:1151-1153）；三方法调用点确认（L1153 shouldExecGroupComplete, L1243 shouldExecGroupReject, L1158/L1244 skipExecGroupMembers）
  - Phase 2 Exit Criteria: 全部 PASS — `oa.xlib:17-19` 方法名修正确认（getStepsInSameExecGroup, killStep×2）
  - Phase 3 Exit Criteria: 全部 PASS — 4 个 forReject/forWithdraw action 无 `<to-step>`/`<transition>`；sub-workflow output `'completed'`；simple-approval 无 allowWithdraw；leaveDays persist=true；timeout-auto 调度器注释
  - Phase 4 Exit Criteria: 全部 PASS — newSteps SEQ_GROUP WAITING 处理（L300-309）；activateNextSeqStep 实现（ExecGroupSupport L142-167）；doExitStep 后调用点（WorkflowEngineImpl L1188-1190）；testExecGroupSeq 端到端验证
  - Closure Gates: 全部 PASS
  - `node ai-dev/tools/check-plan-checklist.mjs 281-nop-wf-example-fixes.md --strict` 退出码为 0
  - Anti-Hollow 检查结果: 5 条调用链全部连通（doInvokeAction→shouldExecGroupComplete, doInvokeAction→skipExecGroupMembers, doReject→shouldExecGroupReject→skipExecGroupMembers, doExitStep后→activateNextSeqStep, newSteps→SEQ_GROUP WAITING）；`scan-hollow-implementations.mjs --module nop-wf-core --severity high` 退出码为 0
  - Deferred 项分类检查: timeout-auto 调度器集成（Non-Blocking Follow-up, 合理）+ TestCountersign @Disabled 解除（Non-Blocking Follow-up, 独立任务, 合理）
  - `./mvnw test -pl nop-wf-core,nop-wf-service -am` BUILD SUCCESS（执行者 + closure audit 补跑确认）

Follow-up:

- timeout-auto 外部调度器集成（Quartz）不在本计划范围内
- TestCountersign 的 @Disabled 解除由独立任务处理
