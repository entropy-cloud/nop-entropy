# nop-wf 审批流示例文件审查报告

> Status: resolved
> Date: 2026-06-27
> Scope: nop-wf 模块 — `nop-wf-service/src/main/resources/_vfs/nop/wf/examples/` 下 15 个审批流示例定义（.xwf），`nop-wf-core/src/main/resources/_vfs/nop/wf/base/oa.xwf` 基模板，`oa.xlib`/`wf-gen.xlib` 标签库
> Conclusion: 经 3 轮独立子 agent 审查后确认，共发现 15 个问题（6 引擎 Bug + 9 示例 Bug），分级为 P0:4、P1:7、P2:2、P3:2。原始 17 个问题中有 2 个误报（包容分支/并行分支死锁，经核实 join 逻辑正确处理了未创建和已驳回的步骤），已移除。

## Context

nop-wf 模块提供了 15 个产品级 `.xwf` 审批流示例文件和 1 个基模板 `oa.xwf`，覆盖串签、并签、会签、票签、或签、条件分支、包容分支、驳回、撤回、转办、委派、抄送、超时、子流程等主流 OA 审批模式。这些示例既是开发者的学习参考，也作为自动化测试的输入。

在编写 `ai-dev/design/nop-wf/approval-flow-design.md` 过程中，通过逐文件对照 XDef Schema 和引擎源码（`WorkflowEngineImpl.java`、`ExecGroupSupport.java`、`WorkflowStepImpl.java`），发现示例文件中存在与引擎实际行为不一致的问题，以及部分示例设计逻辑缺陷。

本报告系统性地记录所有发现的问题，区分"引擎实现缺失"与"示例文件自身的逻辑错误"两类根因，为后续修复决策提供依据。

## Analysis

### 调查方法

1. 逐一读取 15 个示例 `.xwf` 文件 + `oa.xwf` 基模板 + `oa.xlib`/`wf-gen.xlib`
2. 对照 `wf.xdef` Schema 验证 XML 结构合规性
3. 逐条追踪引擎源码的执行路径（`WorkflowEngineImpl.doInvokeAction`、`doReject`、`doWithdraw`、`doTransition`、`runStepAutoTransition`、`newSteps`、`ExecGroupSupport.*`）
4. 按影响程度分为 P0（数据损坏风险）、P1（功能不可用）、P2（逻辑缺陷）、P3（配置隐患）
5. 区分"引擎 Bug"和"示例 Bug"两类根因

### 问题总览

| 严重度 | 引擎 Bug | 示例 Bug | 合计 |
|--------|----------|----------|------|
| P0 | 0 | 4 | 4 |
| P1 | 6 | 1 | 7 |
| P2 | 0 | 2 | 2 |
| P3 | 0 | 2 | 2 |
| **合计** | **6** | **9** | **15** |

### 问题清单

#### P0 — 示例逻辑与引擎行为不一致，导致数据损坏

**P0-01: `reject-withdraw/v1.xwf` — `reject-to-start` 动作双重创建步骤实例**

文件：`examples/reject-withdraw/v1.xwf:15-20`

```xml
<action name="reject-to-start" displayName="驳回到发起人" 
        common="true" forReject="true" forActivated="true">
    <transition splitType="and">
        <to-step stepName="submit" backLink="true"/>
    </transition>
</action>
```

引擎执行顺序（`WorkflowEngineImpl:1101-1169`）：
1. `doReject()` 被调用 → 自动发现前驱步骤 "submit" → 调用 `doRejectStep()` 创建新实例（actor/owner 沿用历史记录）
2. `doTransition()` 被调用 → 处理 action 上的 `<to-step stepName="submit"/>` → 再次解析 assignment 创建新实例

结果：**"submit" 步骤被创建 2 个实例**。根源在于 `forReject` action 示例错误地同时依赖引擎内置的 `doReject` 行为和显式 transition。

证据：`WorkflowEngineImpl.java:1122-1123`（doReject 先执行），`WorkflowEngineImpl.java:1133-1136`（doTransition 后执行）。

**P0-02: `reject-withdraw/v1.xwf` — `withdraw` 动作同样双重创建**

文件：`examples/reject-withdraw/v1.xwf:22-27`

```xml
<action name="withdraw" displayName="撤回" common="true" 
        forWithdraw="true" forHistory="true">
    <transition splitType="and">
        <to-step stepName="submit" backLink="true"/>
    </transition>
</action>
```

`doWithdraw()`（`WorkflowEngineImpl:1221-1240`）已处理撤回逻辑：kill 下游步骤 + 重创当前步骤。`doTransition()` 又额外创建一个 "submit" 步骤。双重创建。

**P0-03: `comprehensive-leave/v1.xwf` — `reject-to-starter` 双重创建**

文件：`examples/comprehensive-leave/v1.xwf:29-34`

与 P0-01 相同模式：`forReject="true"` + `<to-step stepName="submit">`。

**P0-04: `comprehensive-leave/v1.xwf` — `withdraw-to-submit` 双重创建**

文件：`examples/comprehensive-leave/v1.xwf:37-42`

与 P0-02 相同模式：`forWithdraw="true"` + `<to-step stepName="submit">`。

#### P1 — 引擎实现缺失导致示例功能不可用

> 以下 6 个问题中前 5 个根源相同：`ExecGroupSupport.shouldExecGroupComplete()`、`shouldExecGroupReject()`、`skipExecGroupMembers()` 均已实现但**引擎从未调用**。同时 `WorkflowStepImpl.getStepsInSameExecGroup()` 的过滤逻辑反向（`WorkflowStepImpl.java:325` — `return Objects.equals(...)` 应为 `return !Objects.equals(...)`）。

**P1-01: `countersign/v1.xwf` — `and-group` 不生效**

期望行为：3 个部门全部同意才通过。
实际结果：每个人独立触发 transition，各自创建下游步骤。
引擎路径：`doInvokeAction` → `doTransition` → `transitionToStep`，中间没有任何检查 `shouldExecGroupComplete` 的调用。

**P1-02: `or-sign/v1.xwf` — `or-group` 不生效**

期望行为：任意一人同意即通过，其余自动跳过。
实际结果：两人都独立触发 transition，下游步骤被创建多次。`skipExecGroupMembers` 从未被调用。

**P1-03: `vote-sign/v1.xwf` — `vote-group` 不生效**

期望行为：权重和 ≥ passWeight(50) 时通过。
实际结果：每人独立触发 transition，权重计算从不运行。即使引擎调用了 `isVoteGroupComplete`，该方法还存在变量名错误（`ExecGroupSupport.java:66` — `passWeight` 应为 `passPercent`）。

**P1-04: `sequential-approval/v1.xwf` — `seq-group` 不生效**

期望行为：按 `execOrder` 顺序审批（team-lead → dept-manager → vp）。
实际结果：三人同时激活，无顺序控制。即使 `shouldExecGroupComplete` 被调用，`SEQ_GROUP` 当前实现也只是检查"全部完成"（同 AND），没有执行 `execOrder` 的顺序控制逻辑。

**P1-05: `comprehensive-leave/v1.xwf` — `vp-approval` 的 `or-group` 不生效**

同上，3 位 VP 各自独立触发 transition 到 hr-review，下游步骤被创建多次。

**P1-06: `oa.xlib:17` — 调用不存在的方法 `getStepsWithSameStepGroup`**

文件：`nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/oa.xlib`，第 17 行：

```javascript
const otherSteps = currentStep.getStepsWithSameStepGroup(currentStep.name, false, false);
```

方法 `getStepsWithSameStepGroup(String, boolean, boolean)` 在 `IWorkflowStep`、`IWorkflowStepImplementor`、`WorkflowStepImpl` 中均不存在。最接近的方法是 `getStepsInSameExecGroup(boolean, boolean)`。当 `ExitCosignStep` 标签被触发时（例如会签步骤驳回场景），此处会抛出运行时错误。

证据：`WorkflowStepImpl.java:322-333`、`IWorkflowStep.java:262-268`、`oa.xlib:17`。

**P1-07: `subprocess/sub-workflow/v1.xwf` — `<output>` source 返回值错误**

```xml
<output name="result" type="String" displayName="交接结果">
    <source>wfVars.setVar('result', 'completed')</source>
</output>
```

`<source>` 应返回输出值。`wfVars.setVar(...)` 是 void 方法，返回 null。该 output 写入 null。父流程获得空值，破坏了子流程数据传递。正确写法（单行表达式直接取值，不需要 `return`）：

```xml
<source>'completed'</source>
```

#### P2 — 示例设计逻辑缺陷

**P2-01: `oa.xwf` — `reject` 动作 `local="true"` 导致步骤不退出**

```xml
<action name="reject" displayName="退回" forReject="true" common="true" local="true">
```

`local="true"` → `doExitStep` 不执行。当前步骤保持 ACTIVATED。但 `doReject` 已创建前驱步骤新实例。结果是**两个步骤同时 ACTIVE**：新创建的 submit 步骤 + 原来的 reject 步骤。用户在待办列表中看到两个应处理的步骤。

**P2-02: `simple-approval/v1.xwf` — `allowWithdraw="true"` 无对应 `forWithdraw` 动作**

```xml
<step name="manager-approval" allowWithdraw="true" ...>
    <ref-actions>
        <ref-action name="agree"/>
        <ref-action name="reject"/>
    </ref-actions>
```

`oa.xwf` 基类没有定义 `forWithdraw` 动作，本示例也未自定义。`allowWithdraw` 属性变成死配置。此问题也存在于其他依赖 oa.xwf 基类且未自定义 withdraw 动作的示例中。



#### P3 — 配置隐患

**P3-01: `comprehensive-leave/v1.xwf` — start args 缺 `persist="true"`**

```xml
<arg name="leaveDays" type="Integer" mandatory="true" displayName="请假天数"/>
```

`leaveDays` 在后续 `<when>` 条件中通过 `wfVars.leaveDays` 使用。`persist` 缺省为 `false`，变量不写入 DB。跨服务重启或流程迁移后值丢失。对于条件路由的关键变量应设为 `persist="true"`。

**P3-02: `timeout-auto/v1.xwf` — `dueAction` 依赖外部调度器**

```xml
<step dueAction="auto-agree">
    <due-time-expr>return sysDate + 24 * 60 * 60 * 1000</due-time-expr>
</step>
```

引擎只将 `dueTime` 写入 `NopWfStepInstance.dueTime` 字段，不触发定时调度。超时功能需要外部基础设施（Quartz/定时批处理）。若未部署此组件，示例不工作。

### 根因分类

#### 引擎 Bug（6 个，需要修复引擎代码）

1. `ExecGroupSupport` 三个核心方法从未被引擎调用
2. `getStepsInSameExecGroup` 过滤逻辑反向
3. `isVoteGroupComplete/isVoteGroupReject` 百分比分支变量名错误
4. `SEQ_GROUP` 缺少 `execOrder` 顺序控制
5. `oa.xlib:17` 调用不存在的 `getStepsWithSameStepGroup` 方法（P1）

#### 示例 Bug（9 个，只需修改示例 XML）

1. 4 个 `forReject/forWithdraw` + `to-step` 双重创建（P0）
2. 5 个 `execGroupType` 示例依赖引擎缺失功能（P1，需结合引擎修复）
3. `subworkflow` output source 错误（P1）
4. `oa.xwf reject` 的 `local="true"` 语义争议（P2）
5. `simple-approval` allowWithdraw 死配置（P2）
6. `comprehensive-leave` persist 缺失（P3）
7. `timeout-auto` 调度器依赖（P3）

### 修复策略建议

#### 引擎修复（优先级列表）

| 优先级 | 修复内容 | 涉及文件 |
|--------|---------|---------|
| P0 | `WorkflowStepImpl.getStepsInSameExecGroup` 取反过滤条件 | `WorkflowStepImpl.java:325` |
| P0 | 引擎调用 `ExecGroupSupport.shouldExecGroupComplete` | `WorkflowEngineImpl.doInvokeAction` |
| P1 | 引擎调用 `ExecGroupSupport.skipExecGroupMembers` | `WorkflowEngineImpl` |
| P1 | 引擎调用 `ExecGroupSupport.shouldExecGroupReject` | `WorkflowEngineImpl` |
| P1 | 修复 `isVoteGroupComplete/isVoteGroupReject` 变量名 | `ExecGroupSupport.java:66,119` |
| P1 | 修复 `oa.xlib` 调用不存在的方法 `getStepsWithSameStepGroup` | `oa.xlib:17` |
| P2 | `SEQ_GROUP` 增加 `execOrder` 顺序控制 | `ExecGroupSupport` |

#### 示例修复（优先级列表）

| 优先级 | 修复内容 | 涉及文件 |
|--------|---------|---------|
| P0 | `forReject` action 删除 `<to-step>`，改用 `local="true"` | `reject-withdraw`、`comprehensive-leave` |
| P0 | `forWithdraw` action 删除 `<to-step>` | `reject-withdraw`、`comprehensive-leave` |
| P1 | 修复 subworkflow output source | `subprocess/sub-workflow/v1.xwf` |
| P2 | `comprehensive-leave` start args 加 `persist="true"` | `comprehensive-leave/v1.xwf` |
| P3 | 示例去掉或注释 `allowWithdraw` 死属性 | `simple-approval` 等 |

## Open Questions

- [ ] `oa.xwf` 的 `reject` 动作 `local="true"` 是设计意图还是历史缺陷？如果保留 `local="true"`，需要让 `doReject` 在创建前驱步骤后自动 exit 当前步骤？
- [ ] `execGroupType` 示例在引擎修复之前应标记为"仅作设计参考，暂不可运行"？
- [ ] 是否需要为 join/waitStepNames 增加兜底逻辑（例如：如果 waitStepName 引用的步骤因 `<when>` 条件未创建，join 应自动忽略该 waitStepName）？

## References

- `docs-for-ai/03-modules/nop-wf.md`
- `ai-dev/design/nop-wf/approval-flow-design.md`
- XDef Schema: `nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef`
- 引擎实现: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java`
- ExecGroup: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/ExecGroupSupport.java`
- 步骤实现: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowStepImpl.java`
- 基模板: `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/oa.xwf`
- 标签库: `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/oa.xlib`
- 会签生成: `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/wf-gen.xlib`
