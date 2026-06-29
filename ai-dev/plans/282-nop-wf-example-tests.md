# 282 nop-wf 审批流示例单元测试补充计划

> Plan Status: completed
> Last Reviewed: 2026-06-28
> Source: `ai-dev/plans/281-nop-wf-example-fixes.md`（修复完成后补测试）
> Related: `ai-dev/analysis/2026-06-27-nop-wf-example-files-audit.md`

## Purpose

为 `examples/` 目录下全部 15 个审批流示例补充完整的单元测试，覆盖每个示例的核心行为语义。Plan 281 修复了引擎 bug 和部分示例缺陷，但 15 个示例中 0 个有直接对应的测试，且 Plan 281 遗漏了 3 个示例级缺陷（Phase 0 修复）。

## Current Baseline

- 15 个示例 workflow 文件位于 `nop-wf/nop-wf-service/src/main/resources/_vfs/nop/wf/examples/`（14 个可独立测试 + 1 个 sub-workflow 随 subprocess 测试）
- 现有测试 `TestWorkflowEngine`（30 tests）和 `TestDaoWorkflowEngine`（26 tests）使用 `test/` 目录下的简化 workflow，不测 `examples/`
- `TestCountersign` 被 `@Disabled`，且测的是 `flowlong/counter-sign`（不是 `examples/countersign`）
- **Plan 281 遗漏的 3 个示例缺陷**（对抗性审查发现）：
  - `conditional-branch/v1.xwf:8` 的 `leaveDays` 缺少 `persist="true"` → transition 条件 `wfVars.leaveDays` 在步骤迁移后为 null → 条件分支失效
  - `inclusive-branch/v1.xwf:13` 的 `riskLevel` 缺少 `persist="true"` → 同上
  - `parallel-branch/v1.xwf:69` merge 步骤使用 `<ref-action name="confirm"/>`，但 WhenAllowConfirm 限制 `specialType=='cc'`，merge 非 cc 步骤 → confirm 被拒绝 → workflow 无法结束
- **子流程 `<return>` 映射引擎未实现**：`subprocess/v1.xwf` 的 `<return var="handoverResult" output="result"/>` 在引擎运行时代码中无调用点（仅存在于生成代码 `_WfSubFlowStartModel.java`）。`subWfResults` 传入 `notifySubFlowEnd` 但未映射到 `wfVars`。本计划不修复此引擎缺陷
- 4 个示例全部使用 explicit `user` actor + `wf-actor:Starter`，与 `MockWfActorResolver` 兼容：vote-sign、parallel-branch、inclusive-branch、or-sign
- 11 个示例使用了 `wf-actor:StarterManager`/`wf-actor:StarterDeptManager`/`role` actor，`MockWfActorResolver` 返回 null → 步骤无 actor
- `wf-actor:StarterManager` 标签调用 `wf.getManager(starter, upLevel)`（`wf-actor.xlib:94-104`）
- `wf-actor:StarterDeptManager` 标签调用 `wf.getDeptManager(starter, upLevel)`（`wf-actor.xlib:106-116`）
- `role` actor：引擎不通过 `selectUser` 展开 role 为 user 步骤（`selectUser` 仅用于 candidate matching）。步骤的 actor 保持为 `role:xxx` 类型。测试需将 role 解析为 user 以避免权限检查问题
- `MockWorkflowStore` 支持子流程（testFlow 已验证）、globalVars（MapVarSet）
- 抄送 `specialType="cc"` 仅靠 oa.xlib 的 when 条件控制（WhenAllowConfirm/WhenAllowAgree），无额外 mock 需求
- 超时 `dueAction` 仅写入 `dueTime` 字段，实际触发依赖外部调度器

## Goals

- 修复 Plan 281 遗漏的 3 个示例级缺陷（persist 缺失 + confirm 误用）
- 为全部 15 个示例编写单元测试，每个测试验证核心行为语义
- 增强测试基础设施以支持 `StarterManager`/`StarterDeptManager`/`role` actor

## Non-Goals

- 不修改引擎代码（子流程 `<return>` 映射实现不在本计划范围）
- 不修改 wf.xdef Schema
- 不集成外部调度器（Quartz）实现真实超时
- 不解除 `TestCountersign` 的 `@Disabled`

## Scope

### In Scope

- 修复 3 个 xwf 缺陷（conditional-branch/inclusive-branch persist + parallel-branch confirm→agree）
- 新建 `EnhancedMockWfActorResolver`（支持 manager/deptManager/role）
- 新建测试类 `TestWorkflowExamples`，覆盖 15 个示例
- 每个示例至少 1 个测试方法，验证核心行为

### Out Of Scope

- 子流程 `<return>` 映射的引擎实现（Non-Blocking Follow-up）
- 真实超时调度器集成
- DAO-based 测试（统一用增强 Mock 模式）

## Execution Plan

### Phase 0 - 修复 Plan 281 遗漏的示例缺陷

Status: completed

Targets: `conditional-branch/v1.xwf`, `inclusive-branch/v1.xwf`, `parallel-branch/v1.xwf`, 全部 15 个示例

- Item Types: `Fix`

> 执行中发现：全部 15 个示例的 `<ref-actions>` 引用了 oa.xwf 中的 common action（agree/reject/confirm 等均为 `common="true"`），触发 `ERR_WF_STEP_REF_ACTION_IS_COMMON`，导致示例无法加载。common action 会自动添加到所有步骤，步骤级 `<transition onAppStates>` 已足够。修复方式：删除全部 `<ref-actions>` 块（包括自定义 common action 如 reject-to-start/my-delegate 等）。

- [x] `conditional-branch/v1.xwf:8`：`leaveDays` arg 添加 `persist="true"`
- [x] `inclusive-branch/v1.xwf:13`：`riskLevel` arg 添加 `persist="true"`
- [x] `parallel-branch/v1.xwf:69`：merge 步骤 `<ref-action name="confirm"/>` → `<ref-action name="agree"/>`（实际修复：删除 ref-actions 块，transition 改为 agree）
- [x] `parallel-branch/v1.xwf:71`：merge transition `onAppStates="confirm"` → `onAppStates="agree"`
- [x] **执行中发现并修复**：全部 15 个示例 + sub-workflow 删除引用 common action 的 `<ref-actions>` 块（agree/reject/confirm 等）；5 个 execGroup 示例新增非 local 专用 action 的 ref-action（review-agree/vote-agree/seq-agree/or-agree/vp-agree）

Exit Criteria:

- [x] conditional-branch 的 leaveDays 有 `persist="true"`
- [x] inclusive-branch 的 riskLevel 有 `persist="true"`
- [x] parallel-branch merge 步骤使用 `agree` 而非 `confirm`
- [x] `./mvnw test -pl nop-wf/nop-wf-service -am` 通过（无回归）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 - 测试基础设施：EnhancedMockWfActorResolver

Status: completed

Targets: `nop-wf/nop-wf-service/src/test/java/io/nop/wf/service/mock/EnhancedMockWfActorResolver.java`

- Item Types: `Decision | Proof`

> 设计决策：role/dept actor 在 Mock 中直接解析为 user（返回 WfUserActorBean），因为引擎不通过 selectUser 展开 role 为 user 步骤。伪装为 user 可避免权限检查中对 role actor 的 delegate 检查问题。

- [x] 创建 `EnhancedMockWfActorResolver extends MockWfActorResolver`
- [x] 覆写 `getManager(actor, upLevel)`：返回 `resolveUser("mgr" + upLevel)`（固定规则，非 null）
- [x] 覆写 `getDeptManager(actor, upLevel)`：返回 `resolveUser("deptMgr" + upLevel)`（固定规则，非 null）
- [x] 覆写 `resolveActor(actorType, actorId, deptId)`：当 actorType 为 `role` 或 `dept` 时，直接返回 `resolveUser(actorId)`（伪装为 user，使步骤 actor 为 user 类型）
- [x] 确认现有 `TestWorkflowEngine` setUp 不受影响（仍用原版 MockWfActorResolver）

Exit Criteria:

- [x] `EnhancedMockWfActorResolver` 编译通过
- [x] `getManager(starter, 1)` 返回 actorId="mgr1" 的 actor
- [x] `getDeptManager(starter, 0)` 返回 actorId="deptMgr0" 的 actor
- [x] `resolveActor("role", "hr-manager", null)` 返回 actorType="user" 且 actorId="hr-manager"
- [x] 现有 TestWorkflowEngine 30 个测试仍全部通过（无回归）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 基础流程示例测试

Status: completed

Targets: `simple-approval/v1.xwf`, `cc-notify/v1.xwf`

- Item Types: `Proof`

> 新建测试类 `TestWorkflowExamples`，setUp 使用 `EnhancedMockWfActorResolver`。
> **权限检查约定**：`invokeAction` 在执行前检查 `ctx.getUserId()` 是否匹配步骤 actor。每个 action 调用前需 `context.getContext().setUserId(stepActorId)` 切换到该步骤的 actor（与 `TestWorkflowEngine.testExecGroupAnd` 模式一致）。EnhancedMockWfActorResolver 的固定映射规则：mgr{upLevel}、deptMgr{upLevel}、role actorId 即 userId。

- [x] `testSimpleApproval()`：
    - [x] submit agree → manager-approval（mgr1）激活
    - [x] manager-approval agree → workflow ended
    - [x] manager-approval reject → submit 重新激活（驳回到前驱）
    - [x] submit 重新 agree → manager-approval 重新激活

- [x] `testCcNotify()`：
    - [x] submit agree → approval（mgr1）激活
    - [x] approval agree → cc-notify（role hr-staff → user "hr-staff"）激活
    - [x] cc-notify 调用 agree **失败**（WhenAllowAgree 限制 cc 步骤不能 agree）
    - [x] cc-notify 调用 confirm **成功** → workflow ended

Exit Criteria:

- [x] `testSimpleApproval` 验证基本审批路径 + reject 后 submit 重新激活
- [x] `testCcNotify` 验证 cc 步骤只能 confirm 不能 agree
- [x] **端到端验证**：两个测试均从 start 到 end 完整跑通
- [x] `./mvnw test -pl nop-wf/nop-wf-service -Dtest="TestWorkflowExamples#testSimpleApproval+testCcNotify" -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Exec Group 示例测试

Status: completed

Targets: `countersign/v1.xwf`, `vote-sign/v1.xwf`, `or-sign/v1.xwf`, `sequential-approval/v1.xwf`

- Item Types: `Proof`

- [x] `testCountersign()`：
    - [x] submit agree → countersign-review（and-group, 3 user: finance-manager/hr-manager/legal-manager）全部 ACTIVATED
    - [x] 1 个 agree → 仍 2 active，未 ended
    - [x] 2 个 agree → 仍 1 active，未 ended
    - [x] 3 个 agree → and-group 完成，transition 到 confirm（deptMgr0）
    - [x] confirm agree → workflow ended

- [x] `testVoteSign()`：
    - [x] submit agree → vote-review（vote-group, ceo=30/director1=35/director2=35, passWeight=50）全部 ACTIVATED
    - [x] ceo(30) agree → 30 < 50，未通过，2 active 剩余
    - [x] ceo+director1(65) agree → 65 >= 50，通过，director2 skipped，transition
    - [x] cc-notify（user test005）confirm → workflow ended

- [x] `testOrSign()`：
    - [x] submit agree → or-sign-review（or-group, 2 user: test001/test003）全部 ACTIVATED
    - [x] 1 个 agree → 组完成，另 1 个 skipped，transition
    - [x] cc-hr（user test002）confirm → workflow ended

- [x] `testSequentialApproval()`：
    - [x] submit agree → sequential-review（seq-group, 3 StarterManager upLevel=1/2/3）
    - [x] 仅 mgr1（execOrder 最小）ACTIVATED，mgr2/mgr3 WAITING
    - [x] mgr1 agree → exit，mgr2 激活，无下游
    - [x] mgr2 agree → exit，mgr3 激活，无下游
    - [x] mgr3 agree → 组完成，transition to end

Exit Criteria:

- [x] `testCountersign` 验证 and-group 全部通过后才 transition
- [x] `testVoteSign` 验证 passWeight 达标后 transition + cc confirm
- [x] `testOrSign` 验证任一 agree 后 skip 其余 + cc confirm
- [x] `testSequentialApproval` 验证 seq-group 按 execOrder 依次激活
- [x] **端到端验证**：4 个测试均从 start 到 end 完整跑通
- [x] `./mvnw test -pl nop-wf/nop-wf-service -Dtest="TestWorkflowExamples#testCountersign+testVoteSign+testOrSign+testSequentialApproval" -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 分支示例测试

Status: completed

Targets: `conditional-branch/v1.xwf`, `parallel-branch/v1.xwf`, `inclusive-branch/v1.xwf`

- Item Types: `Proof`

- [x] `testConditionalBranch()`：
    - [x] leaveDays=5(>3)：submit → manager-approval（mgr1）→ hr-approval（role hr-manager → user "hr-manager"）→ end
    - [x] leaveDays=2(<=3)：submit → hr-approval（role hr-manager）→ end（跳过 manager）
    - [x] 验证 split-or 排他语义：不同时激活两个分支

- [x] `testParallelBranch()`：
    - [x] submit agree → tech-review（test003）+ finance-review（test002）同时 ACTIVATED
    - [x] 只完成 tech-review → merge（test001, joinType=and）仍 WAITING
    - [x] 两者都完成 → merge ACTIVATED
    - [x] merge agree（Phase 0 修复 confirm→agree）→ workflow ended

- [x] `testInclusiveBranch()`：
    - [x] riskLevel=8：security-review（>7）+ compliance-review（>5）同时激活（2 并行）
    - [x] riskLevel=6：仅 compliance-review 激活（1 个）
    - [x] riskLevel=3：仅 final-review 激活（跳过中间步骤）
    - [x] riskLevel=8 路径：security + compliance 都完成后 final-review（join）ACTIVATED → ended

Exit Criteria:

- [x] `testConditionalBranch` 验证 split-or 排他分支按条件走不同路径（依赖 Phase 0 persist 修复）
- [x] `testParallelBranch` 验证 split-and 并行 + joinType=and 等待全部完成（依赖 Phase 0 confirm→agree 修复）
- [x] `testInclusiveBranch` 验证包容分支 + join（依赖 Phase 0 persist 修复）
- [x] **端到端验证**：3 个测试均从 start 到 end 完整跑通
- [x] `./mvnw test -pl nop-wf/nop-wf-service -Dtest="TestWorkflowExamples#testConditionalBranch+testParallelBranch+testInclusiveBranch" -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 高级示例测试

Status: completed

Targets: `reject-withdraw/v1.xwf`, `transfer-delegate/v1.xwf`, `subprocess/v1.xwf`, `timeout-auto/v1.xwf`, `comprehensive-leave/v1.xwf`

- Item Types: `Proof`

- [x] `testRejectWithdraw()`：
    - [x] submit → dept-approval（deptMgr0）→ finance-approval（role finance-manager → user "finance-manager"）
    - [x] dept-approval reject-to-start → submit 重新 ACTIVATED（仅 1 个 submit 实例，无双重创建）
    - [x] submit 重新 agree → dept-approval 重新激活
    - [x] 完整审批路径：dept-approval agree → finance-approval agree → ended

- [x] `testTransferDelegate()`：
    - [x] submit → approval（mgr1, permissions=transfer,delegate）激活
    - [x] 转办测试：approval 步骤通过引擎 API `transferToActor` 转给新 actor → 新步骤实例
    - [x] 基本路径：approval agree → cc-result（user test001）confirm → ended
    - [x] 注：委派完整路径（my-delegate → my-delegate-return）放入 Non-Blocking Follow-ups

- [x] `testSubprocess()`：
    - [x] submit agree → hr-approval（role hr-manager → user "hr-manager"）激活
    - [x] handoverItems 非空时：hr-approval agree → handover-subflow 子流程启动，父步骤 WAITING
    - [x] 子流程 receiver-confirm → sender-confirm → 子流程结束
    - [x] 子流程结束后父流程恢复，transition 到 manager-confirm（mgr1）
    - [x] manager-confirm agree → ended
    - [x] 注：`handoverResult` output 捕获验证移入 Non-Blocking Follow-ups（引擎 `<return>` 映射未实现）

- [x] `testTimeoutAuto()`：
    - [x] submit agree → timed-approval（mgr1）激活
    - [x] 验证 `stepRecord.dueTime` 被 due-time-expr 正确设置（非 null）
    - [x] 手动调用 auto-agree action → wfVars.autoApproved=true
    - [x] timed-approval agree → manager-approval（mgr2）→ ended

- [x] `testComprehensiveLeave()`：
    - [x] leaveDays=2(<=3)：submit → hr-review → cc-attendance → end（跳过 dept/vp）
    - [x] leaveDays=5(3<days<=7)：submit → dept-approval（deptMgr0）→ hr-review → cc-attendance → end
    - [x] leaveDays=8(>7)：submit → dept-approval → vp-approval（or-group, 3 VP user）→ hr-review → cc-attendance → end
    - [x] vp-approval or-group 验证：1 个 VP agree 即完成
    - [x] persist 验证：wfVars.leaveDays 在 transition 后仍可读取

Exit Criteria:

- [x] `testRejectWithdraw` 验证 reject 后 submit 重新激活且无双重创建
- [x] `testTransferDelegate` 验证转办 actor 变更 + 基本审批路径
- [x] `testSubprocess` 验证子流程启动/结束/父流程恢复（不验证 handoverResult，见 Non-Blocking Follow-ups）
- [x] `testTimeoutAuto` 验证 dueTime 设置和 auto-agree action 手动执行
- [x] `testComprehensiveLeave` 验证 3 条条件路径 + or-group + persist
- [x] **端到端验证**：5 个测试均从 start 到 end 完整跑通
- [x] `./mvnw test -pl nop-wf/nop-wf-service -Dtest="TestWorkflowExamples#testRejectWithdraw+testTransferDelegate+testSubprocess+testTimeoutAuto+testComprehensiveLeave" -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 6 个 Phase（0-5）的 Status 均为 `completed`
- [x] 15 个示例 workflow 文件全部有对应的测试覆盖（14 个测试方法，sub-workflow 随 subprocess 测试）
- [x] 每个测试方法验证了核心行为语义（有具体断言，不只是"不报错"）
- [x] Plan 281 遗漏的 3 个示例缺陷已修复（Phase 0）
- [x] EnhancedMockWfActorResolver 不影响现有测试
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-wf/nop-wf-service -am` 通过（含现有测试无回归）
- [x] `./mvnw compile` 通过
- [x] checkstyle / 代码规范通过

## Non-Blocking Follow-ups

- 子流程 `<return>` 映射的引擎实现（`subWfResults.result` → `wfVars.handoverResult`），需独立引擎增强计划
- `timeout-auto` 的真实超时触发测试（需集成外部调度器 mock）
- `transfer-delegate` 的委派完整路径测试（my-delegate → my-delegate-return）
- `TestCountersign` 的 `@Disabled` 解除（独立任务）

## Closure

Status Note: 全部 14 个测试方法通过（覆盖 15 个示例），`./mvnw test -pl nop-wf/nop-wf-service -am` 74 tests 0 failures。执行过程中发现并修复了 3 类示例级缺陷（ref-action 引用 common action、execGroup 步骤需要非 local action、due-time-expr/sysDate 变量缺失），这些超出原 Phase 0 范围但为达成 plan goal 的必要修复。
Completed: 2026-06-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（task ses_0f3834604ffeHBTev3oMbPRHJf）
- Evidence:
  - `./mvnw test -pl nop-wf/nop-wf-service -Dtest="TestWorkflowExamples"` → 14 tests, 0 failures, 0 errors
  - `./mvnw test -pl nop-wf/nop-wf-service -am` → 74 tests, 0 failures, 0 errors, 2 skipped (pre-existing @Disabled)
  - 15 个示例全部有测试覆盖：simple-approval, cc-notify, countersign, vote-sign, or-sign, sequential-approval, conditional-branch, parallel-branch, inclusive-branch, reject-withdraw, transfer-delegate, subprocess (含 sub-workflow), timeout-auto, comprehensive-leave
  - EnhancedMockWfActorResolver 不影响 TestWorkflowEngine（30 tests pass）
  - 发现并修复的额外缺陷：
    1. 全部 15 示例 `<ref-actions>` 引用 oa.xwf common action → 删除 ref-actions 块
    2. execGroup 步骤使用 local=true 的 agree 不退出 → 5 个示例添加非 local 专用 action
    3. due-time-expr 使用未定义变量 sysDate → 改用日期字符串
    4. subprocess args 缺 persist → handoverItems/employeeId 添加 persist=true
    5. subprocess flow transition 含 onAppStates → 移除（sub-flow end 不设 appState）
    6. nop-wf-core 编译产物过期 → 重新 install

Follow-up:

- 子流程 `<return>` 映射的引擎实现（需独立引擎增强计划）
- timeout-auto 的真实超时触发测试（需集成外部调度器 mock）
- transfer-delegate 的委派完整路径测试
- TestCountersign 的 @Disabled 解除
