# nop-wf 审批流设计

> Status: draft
> Created: 2026-06-27

## 定位

本文档描述 Nop 平台工作流模块 (`nop-wf`) 中审批流（Approval Flow）的架构设计、模式映射和实现契约。
面向需要在 nop-wf 上构建复杂审批流程的开发者。

## 架构概览

```text
User/Frontend
    | (GraphQL / RPC)
    v
WorkflowServiceImpl (nop-wf-service)
    | (IWorkflowManager)
    v
IWorkflow / IWorkflowImplementor
    | (IWorkflowEngine / WorkflowEngineImpl)
    v
WfRuntime (per-action context, eval scope)
    |
    |--- Step lifecycle (create → waiting → activated → executed → completed/rejected)
    |--- Action execution (validate → auth → init-args → source → transition → exit)
    |--- Transition routing (evaluate to-step conditions → create new step instances)
    |--- ExecGroup management (or-group/and-group/seq-group/vote-group completion)
    |
    v
DaoWorkflowStore (nop-wf-dao)
    | (ORM entities)
    v
NopWfInstance / NopWfStepInstance / NopWfAction (DB)
```

### 模块职责

| 模块 | 职责 |
|------|------|
| `nop-wf-api` | 公共 API 接口、DTO、Actor 模型 |
| `nop-wf-core` | 引擎核心 (`WorkflowEngineImpl`)、模型类、Store SPI、审批流辅助(`ApprovalFlowHelper`) |
| `nop-wf-dao` | ORM 实体、DAO Store 实现、DB 模型加载 |
| `nop-wf-service` | BizModel 实现、Actor 解析器、测试用例 |
| `nop-wf-web` | Web 代码生成、前端组件 |

### 关键接口

| 接口 | 路径 | 职责 |
|------|------|------|
| `IWorkflow` | `nop-wf-core/.../IWorkflow.java` | 外部 API：start/suspend/resume/kill/save/runAutoTransitions |
| `IWorkflowStep` | `nop-wf-core/.../IWorkflowStep.java` | 步骤实例 API：invokeAction/triggerTransition/transitTo/transferToActor |
| `IWorkflowEngine` | `nop-wf-core/.../engine/IWorkflowEngine.java` | 引擎 SPI（89 方法），所有生命周期操作 |
| `IWorkflowModel` | `nop-wf-core/.../model/IWorkflowModel.java` | 模型定义接口：steps/actions/start/end/DAG |
| `IWorkflowStore` | `nop-wf-core/.../store/IWorkflowStore.java` | 持久化抽象（110 方法） |
| `IWorkflowRecord` | `nop-wf-core/.../store/IWorkflowRecord.java` | WF 实例记录 |
| `IWorkflowStepRecord` | `nop-wf-core/.../store/IWorkflowStepRecord.java` | 步骤实例记录 |

## 核心概念

### 步骤类型 (`WfStepType`)

| 类型 | XML 标签 | 语义 |
|------|---------|------|
| `step` | `<step>` | 普通步骤，需要 actor 处理 |
| `join` | `<join>` | 汇聚步骤，等待所有前置（joinType=and）或任一（joinType=or）上游步骤到达后激活 |
| `flow` | `<flow>` | 子流程步骤，启动一个子工作流实例，等待其结束后继续 |

### 执行分组 (`execGroup`, `WfExecGroupType`)

执行分组是 nop-wf 实现多人审批的核心机制。当 transition 到某一步骤时，如果该步骤的 `execGroupType` 不为 `none`，则每个 actor 生成一个步骤实例，这些实例共享同一个 `execGroup` ID。

| `execGroupType` | 中文 | 语义 |
|----------------|------|------|
| `none` | 无 | 不分组，生成一个步骤实例 |
| `or-group` | 或签 | 任意一个完成即完成，其余跳过 |
| `and-group` | 并签 | 所有步骤都要完成 |
| `seq-group` | 串签 | 所有步骤顺序完成（按 execOrder） |
| `vote-group` | 会签 | 投票制，达到 passWeight/passPercent 即通过 |

### 分支类型 (`WfSplitType`)

| 类型 | 语义 |
|------|------|
| `and` | 所有带 `<when>` 条件且满足的分支都执行 |
| `or` | 按 order 排序，只执行第一个满足条件的分支 |

### 步骤状态机

```
CREATED (0)
    → WAITING (20)     [waitSignals 未满足 / join 未就绪 / initAsWaiting]
    → ACTIVATED (30)   [等待用户处理]
        → EXECUTED (35)   [runStepAutoTransition 运行 source 后]
        → COMPLETED (40)  [正常完成]
        → REJECTED (90)   [退回]
        → WITHDRAWN (100) [撤回]
        → CANCELLED (110) [取消]
        → TRANSFERRED (120) [转交]
        → KILLED (70)   [强制终止]
```

## 审批流模式映射

### 1. 串签（Serial Signing / 顺序审批）

典型场景：A → B → C，必须按顺序依次审批。

**模型方法：** 使用 DAG 步骤链 + 步骤级 transition。

```xml
<step name="approve1" displayName="审批人1">
    <assignment>
        <actors>
            <actor actorType="user" actorId="2" actorModelId="m1"/>
        </actors>
    </assignment>
    <!-- 步骤级 transition：当 appState=agree 时自动前进 -->
    <transition onAppStates="agree">
        <to-step stepName="approve2"/>
    </transition>
</step>

<step name="approve2" displayName="审批人2">
    ...
    <transition onAppStates="agree">
        <to-step stepName="approve3"/>
    </transition>
</step>

<step name="approve3" displayName="审批人3">
    ...
    <transition onAppStates="agree">
        <to-end/>
    </transition>
</step>
```

**执行流程：**
1. `start` → `approve1`（ACTIVATED，actor=user2）
2. user2 调用 `agree` action → `approve1.appState=agree` → 步骤级 transition 触发 → `approve2` ACTIVATED
3. 以此类推

**同一步骤多人串签：** 使用 `execGroupType="seq-group"`（见下文"同节点多人"）。

### 2. 并签（Parallel Signing / 会签-并行）

典型场景：多个审批人并行审批，所有人都同意才通过。

**模型方法：** `execGroupType="and-group"`。

```xml
<step name="parallel-approve" displayName="并行审批"
      execGroupType="and-group">
    <assignment>
        <actors>
            <actor actorType="user" actorId="2" actorModelId="m1"/>
            <actor actorType="user" actorId="3" actorModelId="m2"/>
            <actor actorType="user" actorId="4" actorModelId="m3"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="next-step"/>
    </transition>
</step>
```

**执行流程：**
1. 进入此步骤时，为 user2/user3/user4 各创建一步骤实例（同一 execGroup）
2. 每个 actor 独立操作自己的步骤实例
3. 当其中一个 actor 调用 `agree` → `shouldExecGroupComplete` 检查：`and-group` 要求所有成员完成 → 直到所有人 agree 才触发 transition
4. 如果某 actor 调用 `disagree` → `shouldExecGroupReject` 返回 true → 整个组拒绝

### 3. 或签（OR Signing）

典型场景：多个人中任意一人审批即可。

**模型方法：** `execGroupType="or-group"`。

```xml
<step name="or-approve" displayName="任意审批"
      execGroupType="or-group">
    <assignment>
        <actors>
            <actor actorType="role" actorId="manager" actorModelId="m1"/>
            <actor actorType="role" actorId="dept-manager" actorModelId="m2"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="next-step"/>
    </transition>
</step>
```

**执行流程：**
1. 为 role=manager 和 role=dept-manager 各创建一个步骤实例
2. 当任意一个 actor 调用 `agree` → `shouldExecGroupComplete` 对 `or-group` 直接返回 true → transition 触发
3. `skipExecGroupMembers` 杀死同组中其余活跃步骤

### 4. 投票会签（Vote-based Countersign）

典型场景：5 人投票，3 人同意即通过，或超过 60% 即通过。

**模型方法：** `execGroupType="vote-group"`，通过 `passWeight` 或 `passPercent` 控制阈值。

```xml
<step name="vote-approve" displayName="投票审批"
      execGroupType="vote-group" passWeight="3">
    <assignment>
        <actors>
            <actor actorType="user" actorId="2" actorModelId="m1" voteWeight="1"/>
            <actor actorType="user" actorId="3" actorModelId="m2" voteWeight="1"/>
            <actor actorType="user" actorId="4" actorModelId="m3" voteWeight="1"/>
            <actor actorType="user" actorId="5" actorModelId="m4" voteWeight="1"/>
            <actor actorType="user" actorId="6" actorModelId="m5" voteWeight="1"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="next-step"/>
    </transition>
</step>
```

**权重计算规则（`ExecGroupSupport.isVoteGroupComplete`）：**
- 遍历组内所有成员，累加 `voteWeight`
- `totalWeight` = 所有成员权值和
- `completeWeight` = 已成功完成成员的权值和
- 如果已设置 `passWeight`：`completeWeight >= passWeight` 即为通过
- 如果未设置 `passWeight` 但设置了 `passPercent`：`completeWeight/totalWeight >= passPercent`
- 如果两者都未设置：默认要求半数以上（`passPercent = 0.5`）

**拒绝判定规则（`isVoteGroupReject`）：**
- `rejectWeight` = 已经被拒绝/取消/跳过的成员的权值和
- 如果 `totalWeight - rejectWeight < passWeight`（剩余不足），则整个组拒绝

### 5. 条件路由（Conditional Routing）

典型场景：金额 ≤ 30000 走审批人 A，> 30000 走审批人 B。

**模型方法：** `splitType="or"` + `<when>` 条件。

```xml
<step name="start">
    <transition splitType="or">
        <to-step stepName="approve-a" order="1">
            <when>
                <le name="wfRt.bizEntity.amount" value="@:30000"/>
            </when>
        </to-step>
        <to-step stepName="approve-b" order="2">
            <!-- 默认分支，无需 when -->
        </to-step>
    </transition>
</step>
```

**执行逻辑（`doTransition`）：**
- 遍历 `transitionTos` 按 `order` 排序
- 对每个 `to-step`，检查 `<when>` 条件，不满足则跳过
- `splitType=or` 时，第一个满足条件的执行后 `break`
- `splitType=and` 时，所有满足条件的分支都执行（适用于并行分发）

### 6. 全局驳回（Rejection）

典型场景：当前审批人驳回，流程退回到前一步骤。

**模型方法：** `forReject="true"` action + `allowReject="true"` 步骤属性。

```xml
<steps>
    <step name="approve1" allowReject="true">
        ...
        <ref-actions>
            <ref-action name="_rejectAction"/>
        </ref-actions>
    </step>
</steps>

<actions>
    <action name="_rejectAction" displayName="退回" forReject="true" common="true">
        <transition appState="reject"/>
    </action>
</actions>
```

**执行逻辑（`doReject`）：**
1. 如果 `rejectSteps` 参数指定了目标步骤，验证该步骤必须是 DAG 中当前步骤的祖先
2. 如果未指定目标，则默认退回到所有直接前驱步骤（`getPrevNormalStepsInTree`）
3. 对每个目标：如果该步骤已是历史 → 创建新步骤实例，actor/owner 沿用
4. 如果当前步骤是 `and-join` 类型 → 将其状态重置为 WAITING（可重新激活）
5. 当前步骤标记为 REJECTED(90)

### 7. 驳回到指定步骤

典型场景：审批人选择"驳回到发起人"而不是"驳回到上一步"。

**模型方法：** 通过 `rejectSteps` 参数指定目标步骤名。

```java
// 通过 API 调用时传入参数
Map<String, Object> args = new HashMap<>();
args.put("rejectSteps", Set.of("start"));
step.invokeAction("rejectAction", args, ctx);
```

或通过前端指定 `targetSteps` 参数，引擎通过 `wfRt.getRejectSteps()` 获取。

### 8. 撤回（Withdrawal）

典型场景：发起人撤回已提交的申请（后续步骤尚未处理）。

**模型方法：** `forWithdraw="true"` action + `allowWithdraw="true"`。

```xml
<step name="start" allowWithdraw="true">
    ...
</step>

<actions>
    <action name="_withdrawAction" displayName="撤回" forWithdraw="true" common="true"/>
</actions>
```

**执行逻辑（`doWithdraw`）：**
1. 检查后续步骤是否全部未处理（状态不是历史）
2. 如果有后续步骤已变成历史 → 不允许撤回
3. 将所有未处理的后续步骤标记为 WITHDRAWN(100)
4. 如果当前步骤已是历史 → 创建新的步骤实例（重新激活）

### 9. 转交（Transfer）

典型场景：当前审批人将任务转交给其他人处理。

**执行逻辑（`transferToActor`）：**
- `exitCurrentStep=true`：当前步骤标记为 TRANSFERRED(120)，新步骤 ACTIVATED
- `exitCurrentStep=false`：当前步骤变为 WAITING，新步骤 ACTIVATED（委派模式，原处理人可收回）

```java
// 普通转交（当前步骤结束）
step.transferToActor(new WfActorAndOwner("user", "newUserId"), true, ctx);

// 委派（当前步骤等待，受派人处理后再回到当前步骤）
step.transferToActor(new WfActorAndOwner("user", "delegateUserId"), false, ctx);
```

### 10. 抄送（CC）

典型场景：审批通过后抄送给相关人员知晓。

**模型方法：** `specialType="cc"` + `confirm` action。

```xml
<step name="cc1" displayName="抄送人" specialType="cc">
    <assignment>
        <actors>
            <actor actorType="user" actorId="4" actorModelId="m1"/>
        </actors>
    </assignment>
    <transition onAppStates="confirm">
        <to-step stepName="next-step"/>
    </transition>
</step>
```

CC 步骤通过 `specialType="cc"` 标记，前端会将其渲染为只读/确认模式。`confirm` action 为 `local="true"`，仅标记已阅，不结束当前步骤（由 `confirm` action 的 transition 驱动前进）。

### 11. 会签（Cosign）— 代码生成方式

nop-wf 提供另一种会签实现方式：通过 `x:post-extends` + `wf-gen:CosignSupport` 动态生成 join 步骤。

```xml
<step name="cosign-step" specialType="co-sign">
    <assignment>
        <actors>
            <actor actorType="user" actorId="u1" actorModelId="a1"/>
            <actor actorType="user" actorId="u2" actorModelId="a2"/>
        </actors>
    </assignment>
    <ref-actions>
        <ref-action name="sp"/>
    </ref-actions>
</step>

<x:post-extends>
    <wf-gen:CosignSupport xpl:lib="/nop/wf/xlib/wf-gen.xlib"/>
</x:post-extends>
```

**代码生成逻辑（`wf-gen.xlib:CosignSupport`）：**
1. 找到所有 `specialType="co-sign"` 的步骤
2. 对于每个非 local 的 ref-action：
   - 创建一个 `join` 步骤（`joinType="and"`，`waitStepNames=cosignStepName`）
   - 将 action 原本的 transition 移到这个 join 步骤上
   - action 的 transition 改为指向 join 步骤
3. 在 step 的 `on-exit` 中添加 `oa:ExitCosignStep` 处理逻辑（驳回时 kill 同组步骤和 join 步骤）

这样所有 actor 都完成后，join 步骤才激活并执行 transition，实现"所有人同意才通过"的会签语义。

## Action 系统设计

### Action 分类

| 属性 | 语义 |
|------|------|
| `common="true"` | 公共 action，所有步骤都可见（可通过 `when-steps` 限定） |
| `local="true"` | 局部操作，不结束当前步骤 |
| `forReject="true"` | 退回操作，触发退回逻辑 |
| `forWithdraw="true"` | 撤回操作，触发撤回逻辑 |
| `internal="true"` | 内部 action，前端不显示 |
| `persist="true"` | 是否记录 action 记录到数据库 |

### 内置标准 action

| Action | 属性 | 行为 |
|--------|------|------|
| `agree` | common, local | 设置 appState=agree，触发 transition |
| `disagree` | common | 设置 wfAppState=disagree，transition to-end |
| `reject`/`_rejectAction` | common, forReject | 触发退回逻辑 |
| `confirm` | common, local | 设置 appState=confirm（用于 CC 步骤） |
| `complete` | （无特殊标记） | 标准完成，触发 transition |

### Action 执行生命周期

```
checkAllowedAction (状态/权限/when条件/waitSignals)
    → checkActionAuth (自定义权限检查)
    → initArgs (参数初始化)
    → doInvokeAction:
        1. forReject? → doReject
        2. forWithdraw? → doWithdraw
        3. runSource (XPL)
        4. saveActionRecord
        5. transition? → doTransition
        6. !local? → doExitStep (COMPLETED/REJECTED)
        7. triggerEvent (AFTER_ACTION)
        8. delayExecute → checkEnd
```

## Actor 模型

### 内置 Actor 解析器

| actorType | 语义 |
|-----------|------|
| `user` | 指定用户 ID |
| `role` | 指定角色 ID |
| `wf-actor:Starter` | 流程发起人 |
| `wf-actor:CurrentCaller` | 当前调用者 |
| `wf-actor:StarterManager` | 发起人的直接上级 |
| `wf-actor:StarterDeptManager` | 发起人的部门负责人 |
| `all` | 所有人（用于 start auth 检查） |

### Actor 选择策略

在 `<assignment>` 中通过 `selection` 控制：

| selection | 语义 |
|-----------|------|
| `auto` | 自动选择（如果只有一个 actor 则自动分配） |
| `single` | 用户从候选人中选择一个 |
| `multiple` | 用户从候选人中选择多个 |

### Actor 扩展属性

在 `<actor>` 上支持 `wf:` 命名空间扩展：

```xml
<actor actorType="role" actorId="manager" actorModelId="m1"
       wf:permissions="transfer,reject,add-sign"
       wf:upLevel="1"/>
```

## ORM 持久化模型

### 核心表

| 表 | 记录 | 关键字段 |
|---|------|---------|
| `nop_wf_instance` | 工作流实例 | status, appState, bizObjName, bizObjId, signalText, parentWfId |
| `nop_wf_step_instance` | 步骤实例 | status, appState, execGroup, execOrder, voteWeight, actorId, ownerId |
| `nop_wf_action` | 操作记录 | actionName, callerId, execTime |
| `nop_wf_step_instance_link` | 步骤关系 | stepId, nextStepId, execAction |
| `nop_wf_definition` | 模型定义 | modelText, status |
| `nop_wf_var` | 全局变量 | key-value |
| `nop_wf_output` | 输出变量 | key-value |

### execGroup 在 ORM 中的体现

- `nop_wf_step_instance.execGroup`：分组 UUID，同一组内的步骤实例共享此值
- `nop_wf_step_instance.execOrder`：执行顺序（串签时按此排序）
- `nop_wf_step_instance.voteWeight`：投票权重

## 已知问题和未完成功能

以下问题基于源码分析发现，需要后续修复：

### P0 - `ExecGroupSupport` 未被引擎调用

`ExecGroupSupport.shouldExecGroupComplete()`、`shouldExecGroupReject()`、`skipExecGroupMembers()` 以及 `WorkflowEngineImpl.isExecGroupComplete()` 均已实现但**从未被引擎调用**。
引擎的 `doInvokeAction` 和 `doTransition` 中没有任何检查 exec group 是否完成的逻辑。

**影响：** 目前 `execGroupType="and-group|seq-group|vote-group"` 的步骤状态转换控制未生效。表现上每个 actor 的步骤实例独立完成，不会等待同组其他成员。

**修复方向：** 在 `doInvokeAction` 的非 local action 执行 `doExitStep` 之前，需要调用 `shouldExecGroupComplete` 检查组是否完成。如果组未完成，则不执行 `doExitStep` 和 transition。

### P1 - `getStepsInSameExecGroup` 过滤逻辑反向

在 `WorkflowStepImpl.getStepsInSameExecGroup()` 中：

```java
ret.removeIf(step -> {
    if (!includeSelf) {
        if (step == WorkflowStepImpl.this)
            return true;  // 正确：排除自身
    }
    return Objects.equals(step.getRecord().getExecGroup(), stepGroup);  // BUG: 应取反
});
```

当前逻辑**移除了**同 execGroup 的成员，保留了不同组的。正确逻辑应该是只保留同组的，移除不同组的。

**影响：** `ExecGroupSupport` 中所有调用 `getStepsInSameExecGroup` 的方法（包括 `shouldExecGroupComplete`、`isVoteGroupComplete`、`isVoteGroupReject`、`skipExecGroupMembers`）都拿到错误的数据集。

### P2 - `isVoteGroupComplete`/`isVoteGroupReject` 百分比比较变量名错误

```java
// BUG: 应使用 passPercent 而非 passWeight
return completeWeight * 1.0 / totalWeight >= passWeight;
return (1.0 - rejectWeight * 1.0 / totalWeight) < passWeight;
```

当 `passWeight` 为 null 而 `passPercent` 非 null 时，百分比的比较使用了 `passWeight` 变量（null），导致 NullPointerException。

### P3 - `oa.xlib` 中调用不存在的 `getStepsWithSameStepGroup`

在 `oa.xlib` 的 `ExitCosignStep` 标签中：

```javascript
const otherSteps = currentStep.getStepsWithSameStepGroup(currentStep.name, false, false);
```

方法 `getStepsWithSameStepGroup` 在 Java 代码中不存在。可能是意图调用 `getStepsInSameExecGroup`。

### P4 - `TestCountersign` 被 `@Disabled`

`nop-wf-service/.../flowlong/TestCountersign.java` 标记为 `@Disabled`，表明会签相关的集成测试未通过。

## 设计约束

### DAG 无环结构

- 工作流定义文件中的步骤必须构成有向无环图（DAG）
- 引擎在 `WfModelAnalyzer` 中验证无环
- 回退连接使用 `backLink="true"` 标记，DAG 分析忽略这些边
- `allowStepLoop` 控制是否允许非 backLink 的循环（默认 false）

### 与业务实体的关联

通过 `bizEntityFlowIdProp` 和 `bizEntityStateProp` 关联业务实体：

```xml
<workflow bizEntityFlowIdProp="nopFlowId" bizEntityStateProp="statusId">
```

- `bizEntityFlowIdProp`：业务实体上记录工作流 ID 的字段
- `bizEntityStateProp`：引擎自动更新业务实体的状态字段
- `bizEntityState`：transition 可设置业务实体状态值

### 步骤 `internal` 标记

- `internal="true"` 的步骤不在前端显示
- 通常用于 join 步骤、自动步骤
- 流程结束时，internal 步骤也会被清理

## 附录：典型审批流 XML 参考

### 标准线性审批

```xml
<workflow x:schema="/nop/schema/wf/wf.xdef"
          xmlns:x="/nop/schema/xdsl.xdef">
    <start startStepName="apply"/>
    <end/>

    <steps>
        <step name="apply" displayName="发起申请">
            <assignment>
                <actors>
                    <actor actorType="wf-actor:CurrentCaller" actorModelId="m1"/>
                </actors>
            </assignment>
            <transition>
                <to-step stepName="manager-approve"/>
            </transition>
        </step>

        <step name="manager-approve" displayName="经理审批">
            <assignment>
                <actors>
                    <actor actorType="wf-actor:StarterManager" actorModelId="m1"/>
                </actors>
            </assignment>
            <transition onAppStates="agree">
                <to-step stepName="dept-head-approve"/>
            </transition>
        </step>

        <step name="dept-head-approve" displayName="部门负责人审批">
            <assignment>
                <actors>
                    <actor actorType="wf-actor:StarterDeptManager" actorModelId="m1"/>
                </actors>
            </assignment>
            <transition onAppStates="agree">
                <to-end/>
            </transition>
        </step>
    </steps>

    <actions>
        <action name="agree" displayName="同意" common="true" local="true">
            <transition appState="agree"/>
        </action>
        <action name="disagree" displayName="拒绝" common="true">
            <transition wfAppState="disagree">
                <to-end/>
            </transition>
        </action>
        <action name="reject" displayName="退回" forReject="true" common="true"/>
    </actions>
</workflow>
```

### 条件分支 + 会签审批

```xml
<steps>
    <step name="apply" displayName="发起申请">
        <assignment>
            <actors>
                <actor actorType="wf-actor:CurrentCaller" actorModelId="m1"/>
            </actors>
        </assignment>
        <transition splitType="or">
            <to-step stepName="dept-approve" order="1">
                <when>
                    <eq name="wfRt.bizEntity.type" value="dept"/>
                </when>
            </to-step>
            <to-step stepName="company-approve" order="2"/>
        </transition>
    </step>

    <step name="dept-approve" displayName="部门会签"
          execGroupType="vote-group" passPercent="0.67">
        <assignment>
            <actors>
                <actor actorType="user" actorId="2" voteWeight="1"/>
                <actor actorType="user" actorId="3" voteWeight="1"/>
                <actor actorType="user" actorId="4" voteWeight="1"/>
            </actors>
        </assignment>
        <transition onAppStates="agree">
            <to-step stepName="final-approve"/>
        </transition>
    </step>

    <step name="company-approve" displayName="公司审批">
        <assignment>
            <actors>
                <actor actorType="role" actorId="company-manager" actorModelId="m1"/>
            </actors>
        </assignment>
        <transition onAppStates="agree">
            <to-step stepName="final-approve"/>
        </transition>
    </step>

    <step name="final-approve" displayName="终审">
        <assignment>
            <actors>
                <actor actorType="role" actorId="vp" actorModelId="m1"/>
            </actors>
        </assignment>
        <transition onAppStates="agree">
            <to-end/>
        </transition>
    </step>
</steps>
```

## 参考文献

- XDef Schema: `nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef`
- 引擎实现: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java`
- ExecGroup: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/ExecGroupSupport.java`
- 测试用例: `nop-wf/nop-wf-service/src/test/java/io/nop/wf/service/TestBeeflowCase.java`
- 会签测试: `nop-wf/nop-wf-service/src/test/java/io/nop/wf/service/flowlong/TestCountersign.java`
