# 工作流引擎使用模式补充说明

本文档是对 `workflow-patterns.md` 的补充，包含了之前文档中遗漏或不够详细的工作流模式。

## 目录

1. [抄送模式](#1-抄送模式)
2. [跳转模式](#2-跳转模式)
3. [信号控制模式](#3-信号控制模式)
4. [已阅模式](#4-已阅模式)
5. [执行分组详细类型](#5-执行分组详细类型)
6. [Action 高级属性](#6-action-高级属性)
7. [流程暂停与恢复](#7-流程暂停与恢复)
8. [工作流版本控制](#8-工作流版本控制)
9. [批量操作与监控](#9-批量操作与监控)
10. [错误处理与重试](#10-错误处理与重试)
11. [扩展点与自定义处理器](#11-扩展点与自定义处理器)
12. [specialType 可视化标记](#12-specialtype-可视化标记)

---

## 1. 抄送模式

### 1.1 业务场景

**抄送**是一种通知机制，将流程信息发送给相关人员，但**不需要他们进行审批操作**。抄送人只需确认已阅读即可，不会阻塞流程的正常流转。

**典型场景**:
- 请假申请审批后，抄送给考勤管理员
- 合同审批通过后，抄送给财务部门
- 重要决策流程，抄送给相关领导知悉

### 1.2 配置示例

```xml
<step name="notify-hr" displayName="抄送HR">
  <assignment>
    <actor actorModelId="hr" actorType="role" actorId="hr-manager" selectUser="true"/>
  </assignment>
  <actions>
    <!-- 抄送任务使用 confirm 动作 -->
    <action name="confirm" displayName="确认">
      <source>
        <!-- 标记为已读，不进行其他处理 -->
        <c:script>
          step.markRead(ctx)
        </c:script>
      </source>
      <transition>
        <to-step stepName="next-step"/>
      </transition>
    </action>
  </actions>
</step>
```

### 1.3 抄送与审批的区别

| 特性 | 审批任务 | 抄送任务 |
|------|---------|---------|
| 是否阻塞流程 | 是，必须处理 | 否，不影响流程 |
| 是否需要处理 | 是 | 否（可选） |
| 处理动作 | approve/reject | confirm/acknowledge |
| 处理后流程状态 | 继续或终止 | 继续执行 |
| 典型用途 | 决策、批准 | 通知、知悉 |

### 1.4 Nop Workflow 实现

```java
// WorkflowStepImpl.java
public void markRead(IServiceContext ctx) {
    if (record.getIsRead()) 
        return;
    
    record.setIsRead(true);
    record.setReadTime(CoreMetrics.currentTimestamp());
    
    WfRuntime wfRt = new WfRuntime(this.workflow, ctx);
    wfRt.setCurrentStep(this);
    wfRt.triggerEvent(NopWfCoreConstants.EVENT_MARK_READ);
    
    workflow.getStore().saveStepRecord(record);
}
```

**实现位置**:
- `WorkflowStepImpl.markRead()`
- `NopWfCoreConstants.ACTION_CONFIRM = "confirm"`
- `NopWfCoreConstants.EVENT_MARK_READ`

---

## 2. 跳转模式

### 2.1 业务场景

**跳转（Jump）** 允许直接导航到流程中的任意步骤，而不按照正常流程顺序执行。这会**取消所有当前活动的步骤**，并在目标步骤创建新任务。

**典型场景**:
- 特殊情况下需要跳过某些审批环节
- 需要重新审批时，跳回到某个历史节点
- 流程出现异常，需要跳转到异常处理节点
- VIP客户申请，跳过部分审批流程

### 2.2 配置示例

```xml
<action name="jump-to-dept" displayName="跳转到部门审批">
  <transition>
    <to-step stepName="dept-approval"/>
  </transition>
</action>
```

### 2.3 使用方式

```java
// 直接跳转到指定步骤
workflowEngine.transitTo(step, "target-step-name", args, ctx);

// 或者使用辅助方法
ApprovalFlowHelper.jump(step, "target-step-name", ctx);
```

### 2.4 执行流程

1. **取消当前步骤**: 所有活动状态的步骤被取消
2. **创建目标步骤**: 在目标节点创建新任务
3. **自动迁移**: 执行目标步骤的自动迁移逻辑

### 2.5 与驳回的区别

| 特性 | 驳回（Reject） | 跳转（Jump） |
|------|--------------|-------------|
| 目标节点 | 历史节点 | 任意节点 |
| 当前任务 | 标记为拒绝 | 被取消 |
| 其他活动任务 | 不受影响 | 全部取消 |
| 使用场景 | 审批不通过 | 特殊流程控制 |
| 语义 | 退回修改 | 流程干预 |

### 2.6 Nop Workflow 实现

```java
// IWorkflowEngine.java
void transitTo(IWorkflowStepImplementor step, String stepName, 
               Map<String, Object> args, IServiceContext ctx);

// ApprovalFlowHelper.java
public static void jump(IWorkflowStepImplementor step, String targetStepName, 
                        IServiceContext ctx) {
    IWorkflow wf = step.getWorkflow();
    
    // 取消所有活动步骤
    List<? extends IWorkflowStep> activeSteps = wf.getActiveSteps();
    for (IWorkflowStep activeStep : activeSteps) {
        wf.getEngine().exitStep(activeStep, 
            NopWfCoreConstants.WF_STEP_STATUS_JUMPED, null, ctx);
    }
    
    // 跳转到目标步骤
    wf.getEngine().transitTo(step, targetStepName, null, ctx);
    
    // 执行自动迁移
    wf.getEngine().runAutoTransitions(wf, ctx);
}
```

**实现位置**:
- `IWorkflowEngine.transitTo()`
- `ApprovalFlowHelper.jump()`

---

## 3. 信号控制模式

### 3.1 业务场景

**信号控制**提供了一种基于事件的流程控制机制。通过打开或关闭信号，可以**动态控制某些步骤或动作的可用性**。

**典型场景**:
- 等待外部系统确认后才能继续
- 条件满足时才允许某些操作
- 事件驱动的流程控制
- 跨流程协调

### 3.2 配置示例

#### 3.2.1 定义等待信号的步骤

```xml
<step name="wait-payment">
  <waitSignals>
    <signal>payment-confirmed</signal>
    <signal>stock-reserved</signal>
  </waitSignals>
  <actions>
    <action name="continue">
      <!-- 只有当所有信号都打开时，此动作才可用 -->
      <transition>
      <transition>
        <to-step stepName="ship-order"/>
      </transition>

#### 3.2.2 打开信号

```xml
<!-- 在其他步骤中触发信号 -->
<action name="payment-complete">
  <action name="payment-complete">
  <source>
    <c:script>
      // 支付完成后，打开信号
      wf.turnSignalOn(['payment-confirmed'], ctx)
    </c:script>
  </source>
</action>

#### 3.2.3 关闭信号

```xml
<action name="cancel-payment">
  <source>
    <c:script>
      // 取消支付，关闭信号
      wf.turnSignalOff(['payment-confirmed'], ctx)
    </c:script>
  </source>
</action>

### 3.3 信号状态管理

```java
// 打开信号
workflowEngine.turnSignalOn(wf, signals, ctx);

// 关闭信号
workflowEngine.turnSignalOff(wf, signals, ctx);

// 检查信号是否全部打开
boolean isAllOn = wf.isAllSignalOn(waitSignals);

// 获取当前打开的信号集合
Set<String> signals = wf.getStore().getOnSignals(wf.getRecord());
```

### 3.4 信号控制流程

1. **步骤创建时**: 检查 `waitSignals`，如果信号未全部打开，步骤进入 **WAITING** 状态
2. **信号打开时**: 触发 `EVENT_SIGNAL_ON`，检查所有 WAITING 状态的步骤
3. **激活步骤**: 如果某个步骤等待的所有信号都已打开，将其状态改为 **ACTIVATED**
4. **执行迁移**: 执行步骤的自动迁移逻辑

### 3.5 信号与Join的区别

| 特性 | Join | 信号控制 |
|------|------|---------|
| 触发条件 | 上游步骤完成 | 外部事件/信号 |
| 等待对象 | 分支步骤 | 信号集合 |
| 动态性 | 静态定义 | 运行时动态 |
| 跨流程 | 不支持 | 支持 |
| 使用场景 | 并行分支汇聚 | 事件驱动流程 |

### 3.6 Nop Workflow 实现

```java
// WorkflowEngineImpl.java
@Override
public void turnSignalOn(IWorkflowImplementor wf, Set<String> signals, 
                         IServiceContext ctx) {
    IWorkflowStore wfStore = wf.getStore();
    wfStore.addSignals(wf.getRecord(), signals);
    
    wfRt.triggerEvent(NopWfCoreConstants.EVENT_SIGNAL_ON);
    
    // 检查等待中的步骤是否可以激活
    wf.delayExecute(() -> {
        checkWaitingSteps(wf, wfRt);
    });
}

@Override
public void turnSignalOff(IWorkflowImplementor wf, Set<String> signals, 
                          IServiceContext ctx) {
    IWorkflowStore wfStore = wf.getStore();
    if (wfStore.removeSignals(wf.getRecord(), signals)) {
        wfRt.triggerEvent(NopWfCoreConstants.EVENT_SIGNAL_OFF);
    }
}
```

**实现位置**:
- `WorkflowEngineImpl.turnSignalOn()`
- `WorkflowEngineImpl.turnSignalOff()`
- `WorkflowEngineImpl.checkWaitingSteps()`
- `NopWfCoreConstants.EVENT_SIGNAL_ON/OFF`

---

## 4. 已阅模式

### 4.1 业务场景

**已阅（Mark as Read）** 标记任务为已读状态，但**不完成**任务。这与抄送的 `confirm` 动作不同，`confirm` 会完成任务并流转到下一步，而 `markRead` 只是标记状态。

**典型场景**:
- 查看任务详情但不立即处理
- 表示已知晓任务存在
- 用于任务统计和报表
- 提醒功能的触发条件

### 4.2 使用方式

```java
// 在步骤上标记已阅
step.markRead(ctx);

// 检查是否已阅
boolean isRead = step.getRecord().getIsRead();
Timestamp readTime = step.getRecord().getReadTime();
```

### 4.3 已阅状态字段

- **isRead**: Boolean - 是否已阅
- **readTime**: Timestamp - 已阅时间
- **触发事件**: `EVENT_MARK_READ`

### 4.4 已阅与完成的区别

| 操作 | markRead() | complete() |
|------|-----------|-----------|
| 任务状态 | 不改变 | 变为 COMPLETED |
| 是否阻塞流程 | 否 | 是（如果需要） |
| 是否触发事件 | 是（MARK_READ） | 是（COMPLETE） |
| 可执行次数 | 一次 | 一次 |
| 使用场景 | 标记已读 | 完成任务 |

### 4.5 Nop Workflow 实现

```java
// WorkflowStepImpl.java
public void markRead(IServiceContext ctx) {
    if (record.getIsRead()) 
        return;
    
    record.setIsRead(true);
    record.setReadTime(CoreMetrics.currentTimestamp());
    
    WfRuntime wfRt = new WfRuntime(this.workflow, ctx);
    wfRt.setCurrentStep(this);
    wfRt.triggerEvent(NopWfCoreConstants.EVENT_MARK_READ);
    
    workflow.getStore().saveStepRecord(record);
}
```

**实现位置**:
- `WorkflowStepImpl.markRead()`
- `IWorkflowStepRecord.isRead/readTime`

---

## 5. 执行分组详细类型

### 5.1 概述

**执行分组（ExecGroup）** 控制多人审批任务的处理逻辑。Nop Workflow 支持 **5 种执行分组类型**，每种类型对应不同的多人审批模式。

### 5.2 执行分组类型

#### 5.2.1 NONE - 独立执行

**说明**: 每个人独立执行，无分组概念

**场景**: 每个审批人独立处理自己的任务

**行为**:
- 每个处理人一个任务实例
- 任务之间相互独立
- 任何一人完成都不影响其他人

```xml
<step name="independent-review">
  <assignment>
    <actor type="user" id="user1"/>
    <actor type="user" id="user2"/>
    <actor type="user" id="user3"/>
  </assignment>
  <execGroupType>none</execGroupType>
</step>
```

#### 5.2.2 OR_GROUP - 或签（任意一人）

**说明**: 任意一人完成，整个分组完成

**场景**: 值班人员审批，任何一人处理即可

**行为**:
- 所有处理人同时收到任务
- 任意一人完成，其他人的任务自动取消
- 整个分组标记为完成

```xml
<step name="duty-approval" execGroupType="or-group">
  <assignment>
    <actor actorModelId="duty" actorType="role" actorId="duty-staff" selectUser="true"/>
  </assignment>
</step>
```

#### 5.2.3 AND_GROUP - 会签（所有人）

**说明**: 所有人都完成，整个分组完成

**场景**: 多部门会签，所有部门都必须同意

**行为**:
- 所有处理人同时收到任务
- 每个人都必须完成
- 所有人完成后，整个分组完成

```xml
<step name="countersign-approval" execGroupType="and-group">
  <assignment>
    <actor actorModelId="finance" actorType="role" actorId="finance" selectUser="true"/>
    <actor actorModelId="hr" actorType="role" actorId="hr" selectUser="true"/>
    <actor actorModelId="legal" actorType="role" actorId="legal" selectUser="true"/>
  </assignment>
</step>
```
#### 5.2.4 SEQ_GROUP - 顺序执行

**说明**: 按顺序依次执行

**场景**: 多级审批，需要按级别顺序审批

**行为**:
- 按 `execOrder` 顺序执行
- 前一个完成后，下一个才激活
- 所有人的 `execGroup` 相同

```xml
<step name="sequential-approval" execGroupType="seq-group">
  <assignment>
    <actor actorModelId="sup1" actorType="wf-actor:StarterManager" wf:upLevel="1"/>
    <actor actorModelId="sup2" actorType="wf-actor:StarterManager" wf:upLevel="2"/>
    <actor actorModelId="sup3" actorType="wf-actor:StarterManager" wf:upLevel="3"/>
  </assignment>
</step>
```
- `execGroup`: 同一组任务共享的UUID
- `execOrder`: 执行顺序（1000, 2000, 3000...）
- 引擎按 `execOrder` 顺序激活任务

#### 5.2.5 VOTE_GROUP - 投票表决

**说明**: 按权重投票，达到阈值通过

**场景**: 董事会投票、专家评审

**行为**:
- 每个人有投票权重
- 统计同意票的总权重
- 总权重 >= `passWeight` 时通过

```xml
<step name="board-voting" execGroupType="vote-group" passWeight="50">
  <assignment>
    <actor actorModelId="chairman" actorType="user" actorId="chairman" voteWeight="40"/>
    <actor actorModelId="director1" actorType="user" actorId="director1" voteWeight="20"/>
    <actor actorModelId="director2" actorType="user" actorId="director2" voteWeight="20"/>
    <actor actorModelId="director3" actorType="user" actorId="director3" voteWeight="20"/>
  </assignment>
</step>
```
**投票规则**:
- 每人投票：同意或拒绝
- 统计同意票的 `voteWeight` 总和
- 总和 >= `passWeight`（默认50），分组完成

### 5.3 执行分组配置

```xml
<step name="multi-person-task" execGroupType="and-group" passWeight="50">
  <assignment>
    <!-- 分配多个处理人 -->
  </assignment>
  
  <!-- 分组完成检查条件（可选） -->
  <checkExecGroupComplete>
    <c:script>
      customGroupCompleteChecker.check(step, wfRt)
    </c:script>
  </checkExecGroupComplete>
</step>
```
### 5.4 Nop Workflow 实现

```java
// WfExecGroupType.java
public enum WfExecGroupType {
    none,       // 独立执行
    or_group,   // 或签
    and_group,  // 会签
    seq_group,  // 顺序
    vote_group  // 投票
}

// WorkflowEngineImpl.java
private boolean isExecGroupComplete(IWorkflowStepImplementor step, WfRuntime wfRt) {
    WfStepModel stepModel = (WfStepModel) step.getModel();
    
    // 自定义完成条件
    if (stepModel.getCheckExecGroupComplete() != null)
        return stepModel.getCheckExecGroupComplete().passConditions(wfRt);
    
    // 默认：所有同组任务都完成
    return true;
}
```

**实现位置**:
- `WfExecGroupType.java`
- `WorkflowEngineImpl.newSteps()`
- `WorkflowEngineImpl.isExecGroupComplete()`

---

## 6. Action 高级属性

### 6.1 概述

Action（动作）除了基本的 `name`、`source`、`transition` 外，还支持多个高级属性，用于精细控制动作的行为和可用性。

### 6.2 可用性控制属性

#### 6.2.1 forActivated - 步骤激活时可用

**说明**: 步骤处于 ACTIVATED 状态时，此动作可用

**默认值**: true

**使用场景**: 大部分审批动作

```xml
<action name="approve" forActivated="true">
  <transition>
    <to-step stepName="next"/>
  </transition>
</action>
```

#### 6.2.2 forHistory - 历史步骤可用

**说明**: 步骤处于历史状态（COMPLETED, REJECTED等）时，此动作可用

**默认值**: false

**使用场景**: 查看历史任务、撤回已完成的任务

```xml
<action name="view" forHistory="true">
  <source>
    <c:script>
      log.info('查看历史任务')
    </c:script>
  </source>
</action>
```

#### 6.2.3 forFlowEnded - 流程结束后可用

**说明**: 流程结束后，此动作仍然可用

**默认值**: false

**使用场景**: 流程归档后查看、重新激活

```xml
<action name="reactivate" forFlowEnded="true">
  <source>
    <c:script>
      wf.reactivate(ctx)
    </c:script>
  </source>
</action>
```

#### 6.2.4 forWaiting - 等待状态可用

**说明**: 步骤处于 WAITING 状态时，此动作可用

**默认值**: false

**使用场景**: 唤醒等待中的任务、取消等待

```xml
<action name="cancel-wait" forWaiting="true">
  <source>
    <c:script>
      step.cancel(ctx)
    </c:script>
  </source>
</action>
```
#### 6.2.5 forWithdraw - 撤回操作

**说明**: 标记为撤回操作，不需要执行 transition

**默认值**: false

**使用场景**: 撤回已发送的任务

```xml
<action name="withdraw" forWithdraw="true">
  <source>
    ${withdrawHandler.withdraw(step, ctx)}
  </source>
</action>
```

### 6.3 执行行为属性

#### 6.3.1 local - 本地操作

**说明**: 本地操作，不结束步骤，不触发流程流转

**默认值**: false

**使用场景**: 保存草稿、添加评论、上传附件

```xml
<action name="save-draft" local="true">
  <source>
    ${form.save()}
  </source>
</action>
```

#### 6.3.2 common - 通用动作

**说明**: 通用动作，所有步骤都可以使用

**默认值**: false

**使用场景**: 通用查看、评论、撤回等

```xml
<action name="comment" common="true">
  <source>
    ${commentService.add(args.comment)}
  </source>
</action>
```

**配合 `whenSteps` 使用**:

```xml
<action name="special-approve" common="true" whenSteps="step1,step2">
  <!-- 仅在 step1 和 step2 上可用 -->
</action>
```

#### 6.3.3 waitSignals - 等待信号

**说明**: 只有当指定的信号全部打开时，此动作才可用

**使用场景**: 事件驱动的动作控制

```xml
<action name="ship" waitSignals="payment-confirmed,stock-reserved">
  <transition>
    <toStep name="complete"/>
  </transition>
</action>
```

#### 6.3.4 afterTransition - 迁移后执行

**说明**: 在流程迁移完成后执行，而不是迁移前

**默认值**: false

**使用场景**: 迁移后的清理工作、通知

```xml
<action name="approve" afterTransition="true">
  <source>
    ${notificationService.send('流程已审批')}
  </source>
  <transition>
    <toStep name="next"/>
  </transition>
</action>
```

### 6.4 特殊用途属性

#### 6.4.1 forReject - 驳回动作

**说明**: 标记为驳回动作，执行特殊的驳回逻辑

```xml
<action name="reject" forReject="true">
  <source>
    ${wfRt.setRejectSteps(['previous-step'])}
  </source>
</action>
```

#### 6.4.2 saveActionRecord - 保存动作记录

**说明**: 是否将此动作的执行记录保存到数据库

**默认值**: true

```xml
<action name="view" saveActionRecord="false">
  <!-- 查看动作不保存记录 -->
</action>
```

### 6.5 完整示例

```xml
<action name="approve" 
        displayName="批准"
        forActivated="true"
        forHistory="false"
        forFlowEnded="false"
        forWaiting="false"
        local="false"
        common="false"
        waitSignals=""
        afterTransition="false"
        saveActionRecord="true">
  
  <args>
    <arg name="comment" type="String"/>
  </args>
  
  <source>
    ${
      // 记录审批意见
      wf.globalVars.setVar('approveComment', args.comment)
      notificationService.send('审批通过')
    }
  </source>
  
  <transition when="${amount <= 10000}">
    <toStep name="manager-approval"/>
  </transition>
  <transition when="${amount > 10000}">
    <toStep name="director-approval"/>
  </transition>
</action>
```

### 6.6 Nop Workflow 实现

**实现位置**:
- `_WfActionModel.java` - 模型定义
- `WorkflowEngineImpl.checkAllowedAction()` - 权限检查
- `WorkflowEngineImpl.invokeAction()` - 动作执行

---

## 7. 流程暂停与恢复

### 7.1 业务场景

**暂停（Suspend）**: 临时挂起流程，所有活动步骤暂停，所有动作不可用

**恢复（Resume）**: 恢复挂起的流程，继续正常执行

**典型场景**:
- 等待外部事件或审批
- 流程出现异常需要暂停
- 临时中止流程执行
- 流程维护或升级

### 7.2 暂停流程

```java
// 挂起工作流
workflowEngine.suspend(wf, args, ctx);
```

**执行效果**:
- 流程状态变为 `SUSPENDED`
- 触发 `EVENT_SUSPEND` 事件
- 所有活动步骤保持当前状态
- **所有动作不可用**（包括审批、驳回等）

```xml
<!-- 监听挂起事件 -->
<listener event="suspend">
  <source>
    ${log.info('流程已挂起: ' + wf.wfId)}
  </source>
</listener>
```

### 7.3 恢复流程

```java
// 恢复工作流
workflowEngine.resume(wf, args, ctx);
```

**执行效果**:
- 流程状态恢复为 `ACTIVATED`
- 触发 `EVENT_RESUME` 事件
- 活动步骤恢复可用
- 动作恢复可执行

```xml
<!-- 监听恢复事件 -->
<listener event="resume">
  <source>
    ${log.info('流程已恢复: ' + wf.wfId)}
  </source>
</listener>
```

### 7.4 暂停状态限制

**重要**: 在 `SUSPENDED` 状态下：
- ✅ 可以查询流程和步骤
- ✅ 可以查看历史记录
- ❌ 不能执行任何动作
- ❌ 不能启动子流程
- ❌ 不能转交或委托
- ✅ 可以恢复流程
- ✅ 可以终止流程

### 7.5 与挂起状态的区别

| 特性 | 暂停（Suspend） | 等待（Waiting） |
|------|----------------|----------------|
| 流程级别 | 整个流程 | 单个步骤 |
| 触发方式 | 主动操作 | 条件触发 |
| 动作可用性 | 全部不可用 | 部分可用 |
| 恢复条件 | 手动恢复 | 条件满足自动恢复 |
| 使用场景 | 流程控制 | 并行汇聚、子流程 |

### 7.6 Nop Workflow 实现

```java
// WorkflowEngineImpl.java
@Override
public void suspend(IWorkflowImplementor wf, Map<String, Object> args, 
                    IServiceContext ctx) {
    if (wf.isSuspended())
        return;
    
    WfRuntime wfRt = newWfRuntime(wf, ctx);
    if (wf.isEnded())
        throw wfRt.newError(ERR_WF_NOT_ALLOW_SUSPEND);
    
    initArgs(wfRt, args);
    checkManageAuth(wfRt);
    
    wf.getRecord().setLastOperateTime(CoreMetrics.currentTimestamp());
    IWfActor caller = wfRt.getCaller();
    wf.getRecord().setLastOperator(caller);
    
    wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_SUSPENDED);
    wfRt.triggerEvent(NopWfCoreConstants.EVENT_SUSPEND);
}

@Override
public void resume(IWorkflowImplementor wf, Map<String, Object> args, 
                   IServiceContext ctx) {
    if (!wf.isSuspended())
        return;
    
    WfRuntime wfRt = newWfRuntime(wf, ctx);
    initArgs(wfRt, args);
    checkManageAuth(wfRt);
    
    wf.getRecord().setLastOperateTime(CoreMetrics.currentTimestamp());
    IWfActor caller = wfRt.getCaller();
    wf.getRecord().setLastOperator(caller);
    
    wfRt.saveWfRecord(NopWfCoreConstants.WF_STATUS_ACTIVATED);
    wfRt.triggerEvent(NopWfCoreConstants.EVENT_RESUME);
}
```

**实现位置**:
- `WorkflowEngineImpl.suspend()`
- `WorkflowEngineImpl.resume()`
- `NopWfCoreConstants.WF_STATUS_SUSPENDED`
- `NopWfCoreConstants.EVENT_SUSPEND/RESUME`

---

## 8. 工作流版本控制

### 8.1 业务场景

**版本控制**管理流程定义的演进和兼容性，支持：
- 流程定义升级
- 多版本并存
- 平滑迁移
- 版本兼容性

**典型场景**:
- 流程优化和调整
- 业务规则变更
- 合规性要求更新
- 功能增强

### 8.2 版本管理策略

#### 8.2.1 版本号定义

```xml
<workflow name="leave-approval" wfVersion="1">
  <!-- 版本 1 的定义 -->
</workflow>

<workflow name="leave-approval" wfVersion="2">
  <!-- 版本 2 的定义 -->
</workflow>
```

#### 8.2.2 版本命名规则

- **主版本号**: 重大变更，不兼容旧版本
- **次版本号**: 功能增强，向后兼容
- **修订号**: Bug修复，完全兼容

**示例**:
- `leave-approval@1` - 初始版本
- `leave-approval@2` - 增加审批环节
- `leave-approval@3` - 调整审批规则

### 8.3 版本演进模式

#### 8.3.1 新实例使用新版本

**策略**: 新启动的流程使用最新版本，已运行的流程继续使用旧版本

**优点**:
- 平滑升级
- 无需迁移
- 风险最低

**缺点**:
- 多版本并存
- 维护成本高

```java
// 启动时指定版本
wfCoordinator.startWorkflow("leave-approval", "2", args, ctx);

// 或使用最新版本
wfCoordinator.startWorkflow("leave-approval", null, args, ctx);
```

#### 8.3.2 迁移到新版本

**策略**: 将运行中的流程迁移到新版本

**优点**:
- 统一版本
- 便于维护

**缺点**:
- 迁移复杂
- 风险较高

**实现方式**:

```java
// 迁移单个实例
wfInstance.migrateToVersion("2", ctx);

// 批量迁移
List<IWorkflow> instances = wfQueryService.findInstances("leave-approval", "1");
for (IWorkflow instance : instances) {
    instance.migrateToVersion("2", ctx);
}
```

### 8.4 兼容性管理

#### 8.4.1 向后兼容

**原则**: 新版本应该能够处理旧版本的数据

**实践**:
- 保留旧字段
- 提供默认值
- 数据转换逻辑

```xml
<!-- 版本 1 -->
<args>
  <arg name="leaveDays" type="Integer"/>
</args>

<!-- 版本 2 - 增加新字段，保持兼容 -->
<args>
  <arg name="leaveDays" type="Integer"/>
  <arg name="leaveType" type="String" defaultValue="事假"/>
</args>
```

#### 8.4.2 数据迁移

```java
// 数据迁移脚本
public void migrateData() {
    List<NopWfInstance> instances = dao.findAll();
    for (NopWfInstance instance : instances) {
        if (instance.getVersion() == 1) {
            // 迁移数据
            instance.setLeaveType("事假");
            dao.update(instance);
        }
    }
}
```

### 8.5 版本生命周期

```
开发 → 测试 → 发布 → 活跃 → 废弃 → 停用
```

- **开发**: 新版本开发中
- **测试**: 测试环境验证
- **发布**: 生产环境可用
- **活跃**: 主要使用版本
- **废弃**: 不推荐使用，但继续支持
- **停用**: 不再支持，禁止启动新实例

### 8.6 Nop Workflow 实现

```java
// WfModel.java
public class WfModel extends _WfModel {
    private String wfName;
    private long wfVersion;
    
    public void init() {
        if (getWfName() == null) {
            guessWfNameFromPath();
        }
        new WfModelAnalyzer().analyze(this);
    }
}

// NopWfDefinitionBizModel.java
public void defaultPrepareCopyForNew() {
    // 增加版本号
    entity.setWfVersion(entity.getWfVersion() + 1);
}
```

**实现位置**:
- `WfModel.java` - 版本字段
- `NopWfDefinitionBizModel` - 版本管理
- `ResourceVersionHelper` - 版本号解析

---

## 9. 批量操作与监控

### 9.1 批量取消步骤

**业务场景**: 需要一次性取消多个活动步骤

```java
// 批量取消步骤
List<? extends IWorkflowStep> steps = wf.getActiveSteps();
ApprovalFlowHelper.cancelSteps(steps, ctx);
```

**实现**:

```java
// ApprovalFlowHelper.java
public static void cancelSteps(List<? extends IWorkflowStep> steps, 
                               IServiceContext ctx) {
    for (IWorkflowStep step : steps) {
        step.getWorkflow().getEngine().exitStep(
            step, 
            NopWfCoreConstants.WF_STEP_STATUS_CANCELLED, 
            null, 
            ctx
        );
    }
}
```

### 9.2 流程查询

#### 9.2.1 查询活动流程

```java
// 查询用户的活动任务
List<IWorkflowStep> myTasks = wfQueryService.findActiveStepsByUser(userId);

// 查询指定流程的活动步骤
List<IWorkflowStep> steps = wf.getActiveSteps();

// 查询指定状态的流程
List<IWorkflow> instances = wfQueryService.findInstancesByStatus(
    NopWfCoreConstants.WF_STATUS_ACTIVATED
);
```

#### 9.2.2 查询历史流程

```java
// 查询用户的历史任务
List<IWorkflowStep> myHistory = wfQueryService.findHistoryStepsByUser(userId);

// 查询已完成流程
List<IWorkflow> completed = wfQueryService.findInstancesByStatus(
    NopWfCoreConstants.WF_STATUS_COMPLETED
);
```

#### 9.2.3 高级查询

```java
// 按业务对象查询
List<IWorkflow> instances = wfQueryService.findInstancesByBizObj(
    "LeaveRequest", 
    "LEAVE-001"
);

// 按时间范围查询
List<IWorkflow> instances = wfQueryService.findInstancesByTimeRange(
    startTime, 
    endTime
);

// 按流程名称和版本查询
List<IWorkflow> instances = wfQueryService.findInstances(
    "leave-approval", 
    "2"
);
```

### 9.3 流程监控

#### 9.3.1 统计信息

```java
// 流程实例统计
WorkflowStatistics stats = wfQueryService.getStatistics("leave-approval");
long totalCount = stats.getTotalCount();
long activeCount = stats.getActiveCount();
long completedCount = stats.getCompletedCount();

// 步骤统计
StepStatistics stepStats = wfQueryService.getStepStatistics("dept-approval");
long avgDuration = stepStats.getAverageDuration();
long timeoutCount = stepStats.getTimeoutCount();
```

#### 9.3.2 性能监控

```java
// 流程执行时间
long duration = wf.getRecord().getEndTime().getTime() - 
                wf.getRecord().getStartTime().getTime();

// 步骤处理时间
long stepDuration = step.getRecord().getFinishTime().getTime() - 
                    step.getRecord().getCreateTime().getTime();

// 超时统计
List<IWorkflowStep> timeoutSteps = wfQueryService.findTimeoutSteps();
```

### 9.4 审计日志

```java
// 查询操作日志
List<IWorkflowActionRecord> logs = wfQueryService.findActionRecords(
    wf.getWfId()
);

// 查询状态变更历史
List<IWorkflowStatusHistory> history = wfQueryService.findStatusHistory(
    wf.getWfId()
);
```

### 9.5 Nop Workflow 实现

**实现位置**:
- `ApprovalFlowHelper.cancelSteps()`
- `IWorkflowStore` - 查询接口
- `NopWfInstanceBizModel` - 业务查询
- `NopWfStepInstanceBizModel` - 步骤查询

---

## 10. 错误处理与重试

### 10.1 错误处理策略

#### 10.1.1 异常捕获

```xml
<action name="approve">
  <source>
    ${
      try {
        externalService.call()
      } catch (Exception e) {
        log.error('调用外部服务失败', e)
        // 标记错误，但不中断流程
        wf.globalVars.setVar('error', e.message)
      }
    }
  </source>
  <transition>
    <toStep name="next"/>
  </transition>
</action>
```

#### 10.1.2 错误事件监听

```xml
<listener event="error">
  <source>
    ${
      errorNotifier.notify(
        wf.wfId,
        step.stepName,
        error
      )
    }
  </source>
</listener>
```

### 10.2 重试机制

#### 10.2.1 配置重试

```xml
<action name="call-external">
  <retry>
    <maxRetries>3</maxRetries>
    <retryInterval>5000</retryInterval>
    <retryCondition>${error instanceof TimeoutException}</retryCondition>
  </retry>
  
  <source>
    ${externalService.call()}
  </source>
</action>
```

#### 10.2.2 重试模型

```java
// WfRetryModel.java
public class WfRetryModel {
    private int maxRetries;        // 最大重试次数
    private long retryInterval;    // 重试间隔（毫秒）
    private IEvalAction retryCondition;  // 重试条件
}
```

### 10.3 补偿机制

**业务场景**: 流程执行失败时，需要回滚已执行的操作

```xml
<action name="reserve-stock">
  <source>
    ${
      // 预留库存
      stockService.reserve(itemId, quantity)
      
      // 记录补偿操作
      wf.globalVars.setVar('compensation', {
        action: 'release-stock',
        itemId: itemId,
        quantity: quantity
      })
    }
  </source>
</action>

<!-- 失败时执行补偿 -->
<action name="compensate">
  <source>
    ${
      compensation = wf.globalVars.getVar('compensation')
      if (compensation.action == 'release-stock') {
        stockService.release(compensation.itemId, compensation.quantity)
      }
    }
  </source>
</action>
```

### 10.4 超时处理

```xml
<step name="wait-external">
  <dueTimeExpr>${sysDate + 3600000}</dueTimeExpr>
  <dueAction>timeout-handler</dueAction>
  
  <actions>
    <action name="timeout-handler">
      <source>
        ${
          // 超时处理逻辑
          if (autoCancel) {
            wf.kill(null, ctx)
          } else {
            notificationService.send('任务超时')
          }
        }
      </source>
    </action>
  </actions>
</step>
```

### 10.5 Nop Workflow 实现

**实现位置**:
- `WfRetryModel.java` - 重试配置
- `WorkflowEngineImpl.handleError()` - 错误处理
- 事件系统 - 错误事件监听

---

## 11. 扩展点与自定义处理器

### 11.1 监听器扩展

#### 11.1.1 实现监听器接口

```java
public class CustomTaskListener implements IWfListener {
    @Override
    public void onEvent(WfRuntime wfRt) {
        String event = wfRt.getEvent();
        IWorkflowStep step = wfRt.getCurrentStep();
        
        if ("task.create".equals(event)) {
            // 任务创建时的处理
            onTaskCreate(step, wfRt);
        } else if ("task.complete".equals(event)) {
            // 任务完成时的处理
            onTaskComplete(step, wfRt);
        }
    }
    
    private void onTaskCreate(IWorkflowStep step, WfRuntime wfRt) {
        // 自定义逻辑
    }
    
    private void onTaskComplete(IWorkflowStep step, WfRuntime wfRt) {
        // 自定义逻辑
    }
}
```

#### 11.1.2 注册监听器

```xml
<workflow>
  <listeners>
    <!-- 全局监听器 -->
    <listener event="task.create" source="${beans.customTaskListener}"/>
    <listener event="task.complete" source="${beans.customTaskListener}"/>
    
    <!-- 内联监听器 -->
    <listener event="instance.end">
      <source>
        ${auditLogger.log(wf)}
      </source>
    </listener>
  </listeners>
</workflow>
```

### 11.2 自定义动作处理器

```java
public class CustomActionHandler {
    public Object handle(IWorkflowStep step, Map<String, Object> args, 
                         IServiceContext ctx) {
        // 自定义动作处理逻辑
        return result;
    }
}
```

**配置**:

```xml
<action name="custom-action">
  <source>
    ${
      inject('customActionHandler').handle(step, args, ctx)
    }
  </source>
</action>
```

### 11.3 自定义条件评估器

```java
public class CustomConditionEvaluator {
    public boolean evaluate(String condition, WfRuntime wfRt) {
        // 自定义条件评估逻辑
        return true;
    }
}
```

**配置**:

```xml
<transition when="${customEvaluator.evaluate('complex-condition', wfRt)}">
  <toStep name="next"/>
</transition>
```

### 11.4 自定义分配策略

```java
public class CustomAssignmentStrategy {
    public List<IWfActor> resolve(WfAssignmentModel assignment, 
                                   WfRuntime wfRt) {
        // 自定义分配逻辑
        return actors;
    }
}
```

**配置**:

```xml
<step name="custom-assignment">
  <assignment>
    <actor type="custom" resolver="${beans.customAssignmentStrategy}"/>
  </assignment>
</step>
```

### 11.5 自定义迁移处理器

```java
public class CustomTransitionHandler {
    public void beforeTransition(IWorkflowStep step, 
                                  WfTransitionModel transition,
                                  WfRuntime wfRt) {
        // 迁移前处理
    }
    
    public void afterTransition(IWorkflowStep step, 
                                 WfTransitionModel transition,
                                 WfRuntime wfRt) {
        // 迁移后处理
    }
}
```

**配置**:

```xml
<action name="approve">
  <transition>
    <beforeHandler>${beans.customTransitionHandler.beforeTransition}</beforeHandler>
    <afterHandler>${beans.customTransitionHandler.afterTransition}</afterHandler>
    <toStep name="next"/>
  </transition>
</action>
```

### 11.6 Nop Workflow 扩展机制

**核心接口**:
- `IWfListener` - 监听器接口
- `IWorkflowEngine` - 引擎扩展点
- `IWorkflowStore` - 存储扩展点
- `IWfActorResolver` - 分配解析器

**实现位置**:
- `WfListenerModel.java`
- `WorkflowEngineImpl.java`
- 各种 Handler 接口

---

## 12. specialType 可视化标记

### 12.1 概述

**specialType** 是可视化设计器识别的分类标记，每种 specialType 对应设计器中的一种图标和特定的处理逻辑。

### 12.2 可用的 specialType 值

#### 12.2.1 步骤类型

- `start` - 开始节点
- `end` - 结束节点
- `approval` - 审批节点
- `cc` - 抄送节点
- `condition` - 条件分支
- `parallel` - 并行分支
- `inclusive` - 包容分支
- `subprocess` - 子流程
- `timer` - 定时器
- `trigger` - 触发器

#### 12.2.2 审批类型

- `single-approval` - 单人审批
- `countersign` - 会签
- `or-sign` - 或签
- `sequential` - 顺序审批
- `vote` - 投票表决

### 12.3 使用示例

```xml
<!-- 审批步骤 -->
<step name="dept-approval" specialType="approval">
  <assignment>
    <actor type="deptManager"/>
  </assignment>
</step>

<!-- 会签步骤 -->
<step name="countersign" specialType="countersign">
  <assignment>
    <actor type="role" id="finance"/>
    <actor type="role" id="hr"/>
  </assignment>
  <execGroupType>and-group</execGroupType>
</step>

<!-- 抄送步骤 -->
<step name="notify" specialType="cc">
  <assignment>
    <actor type="role" id="admin"/>
  </assignment>
  <actions>
    <action name="confirm">
      <source>${step.markRead(ctx)}</source>
    </action>
  </actions>
</step>
```

### 12.4 specialType 与可视化

```
specialType → 设计器图标 → 用户理解
-------------------------------------------
approval    → 审批图标    → 需要审批
cc          → 信封图标    → 通知知悉
condition   → 菱形图标    → 条件判断
parallel    → 分叉图标    → 并行执行
```

### 12.5 Nop Workflow 实现

```java
// WfStepModel.java
public class WfStepModel extends _WfStepModel {
    private String specialType;  // 可视化标记
    
    public String getSpecialType() {
        return specialType;
    }
    
    public void setSpecialType(String specialType) {
        this.specialType = specialType;
    }
}

// WfActionModel.java
public class WfActionModel extends _WfActionModel {
    private String specialType;  // 动作的视觉标记
    
    public String getSpecialType() {
        return specialType;
    }
    
    public void setSpecialType(String specialType) {
        this.specialType = specialType;
    }
}
```

**实现位置**:
- `WfStepModel.specialType`
- `WfActionModel.specialType`
- 可视化设计器使用此属性渲染图标

---

## 总结

本文档补充了 `workflow-patterns.md` 中遗漏或不够详细的工作流模式，包括：

1. ✅ **抄送模式** - 通知机制，不阻塞流程
2. ✅ **跳转模式** - 直接导航到任意步骤
3. ✅ **信号控制模式** - 事件驱动的流程控制
4. ✅ **已阅模式** - 标记已读但不完成
5. ✅ **执行分组详细类型** - 5种多人审批模式
6. ✅ **Action 高级属性** - 精细控制动作行为
7. ✅ **流程暂停与恢复** - 临时挂起和恢复
8. ✅ **工作流版本控制** - 版本管理和迁移
9. ✅ **批量操作与监控** - 批量处理和查询
10. ✅ **错误处理与重试** - 异常处理机制
11. ✅ **扩展点** - 自定义处理器和监听器
12. ✅ **specialType** - 可视化标记

这些补充内容与原文档一起，构成了完整的企业级工作流引擎使用模式指南。
