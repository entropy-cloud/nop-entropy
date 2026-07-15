# 构建审批流

> **前置阅读**：[`02-core-guides/workflow-configuration.md`](../02-core-guides/workflow-configuration.md)（理解 step、transition、action、assignment 等基础概念）

本文以 `nop-wf` 的 14 个顶层产品级示例为基础（其中 `subprocess` 内嵌一个 `sub-workflow`），按场景说明审批流的配置方法。

---

## 快速开始：最简单的线性审批

```xml
<workflow x:extends="/nop/wf/base/oa.xwf"
          x:schema="/nop/schema/wf/wf.xdef" xmlns:x="/nop/schema/xdsl.xdef"
          wfName="simple-approval" wfVersion="1" displayName="简单审批">

    <start startStepName="submit"/>
    <end/>

    <steps>
        <step name="submit" displayName="提交审批">
            <assignment>
                <actors>
                    <actor actorModelId="starter" actorType="wf-actor:Starter"/>
                </actors>
            </assignment>
            <transition onAppStates="agree">
                <to-step stepName="manager-approval"/>
            </transition>
        </step>

        <step name="manager-approval" displayName="经理审批" allowReject="true">
            <assignment>
                <actors>
                    <actor actorModelId="manager" actorType="wf-actor:StarterManager" wf:upLevel="1"/>
                </actors>
            </assignment>
            <transition onAppStates="agree">
                <to-end/>
            </transition>
        </step>
    </steps>
</workflow>
```

**流程说明**：
1. 发起人提交 → `submit` 步骤激活 → 发起人执行 `agree`
2. `submit.appState=agree` → 步骤级 transition 触发 → `manager-approval` 创建
3. 经理执行 `agree` → 工作流结束

**对应的 example**：`examples/simple-approval/v1.xwf`

---

## 串签（顺序审批）

多人按顺序依次审批，前一个人完成后下一个人才能处理。

### 方式一：多步骤链（不同步骤）

```xml
<step name="team-lead" displayName="团队负责人审批">
    <assignment>
        <actors>
            <actor actorModelId="lead" actorType="wf-actor:StarterManager" wf:upLevel="1"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="dept-manager"/>
    </transition>
</step>

<step name="dept-manager" displayName="部门经理审批">
    <assignment>
        <actors>
            <actor actorModelId="dept-mgr" actorType="wf-actor:StarterManager" wf:upLevel="2"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="vp"/>
    </transition>
</step>

<step name="vp" displayName="VP 审批">
    <assignment>
        <actors>
            <actor actorModelId="vp" actorType="role" actorId="vp-role"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-end/>
    </transition>
</step>
```

### 方式二：同一步骤多人串签（seq-group）

```xml
<step name="sequential-review" displayName="顺序审批"
      execGroupType="seq-group">
    <assignment>
        <actors>
            <actor actorModelId="lead" actorType="wf-actor:StarterManager"
                   wf:upLevel="1" selectUser="true"/>
            <actor actorModelId="dept-mgr" actorType="wf-actor:StarterManager"
                   wf:upLevel="2" selectUser="true"/>
            <actor actorModelId="vp" actorType="wf-actor:StarterManager"
                   wf:upLevel="3" selectUser="true"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-end/>
    </transition>
</step>
```

**对应的 example**：`examples/sequential-approval/v1.xwf`

---

## 并行会签（and-group）

多人同时审批，所有人都同意才通过。

```xml
<step name="countersign-review" displayName="会签审批"
      execGroupType="and-group">
    <assignment>
        <actors>
            <actor actorModelId="finance" actorType="role" actorId="finance-manager"/>
            <actor actorModelId="hr" actorType="role" actorId="hr-manager"/>
            <actor actorModelId="legal" actorType="role" actorId="legal-manager"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="confirm"/>
    </transition>
</step>
```

**行为**：
- 3 人同时激活，各自独立操作
- 所有人 agree → and-group 完成 → 触发 transition
- 如果某一步骤为 reject 状态 → 组可能整体拒绝

**对应的 example**：`examples/countersign/v1.xwf`

---

## 或签（or-group）

多个人中任意一人审批即可通过。

```xml
<step name="or-sign-review" displayName="或签审批"
      execGroupType="or-group">
    <assignment>
        <actors>
            <actor actorModelId="mgr1" actorType="user" actorId="user001"/>
            <actor actorModelId="mgr2" actorType="user" actorId="user002"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="next"/>
    </transition>
</step>
```

**行为**：
- 两人同时激活
- 任意一人 agree → 组完成 → 另一人被跳过（SKIPPED）→ 触发 transition

**对应的 example**：`examples/or-sign/v1.xwf`

---

## 投票会签（vote-group）

按权重投票，达到阈值即通过。

```xml
<step name="vote-review" displayName="投票审批"
      execGroupType="vote-group" passWeight="50">
    <assignment>
        <actors>
            <actor actorModelId="ceo" actorType="user" actorId="ceo001" voteWeight="30"/>
            <actor actorModelId="dir1" actorType="user" actorId="dir001" voteWeight="35"/>
            <actor actorModelId="dir2" actorType="user" actorId="dir002" voteWeight="35"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-step stepName="cc-notify"/>
    </transition>
</step>
```

**权重规则**：
- `passWeight`：累计权重大于等于此值即通过
- `passPercent`：累计权重占比（如 `0.67` 表示 2/3）
- 两者都不设：默认 `passPercent=0.5`（过半）
- 拒绝判定：剩余不足 `passWeight` 时整体拒绝

**对应的 example**：`examples/vote-sign/v1.xwf`

---

## 条件分支（splitType=or + \<when\>）

根据业务数据走不同审批路径。

```xml
<step name="submit" displayName="提交申请">
    <transition splitType="or">
        <to-step stepName="manager-approval" order="1">
            <when>
                <gt name="wfVars.leaveDays" value="@:3"/>
            </when>
        </to-step>
        <to-step stepName="hr-approval" order="2">
            <!-- 默认分支 -->
        </to-step>
    </transition>
</step>
```

**关键点**：
- `splitType="or"`：排他路由，只走第一个满足条件的分支
- `order`：分支执行顺序
- `<when>`：条件表达式（使用 XLang）
- 变量需要通过 `persist="true"` 持久化，后续步骤才能读取

**条件变量持久化**：
```xml
<start startStepName="submit">
    <arg name="leaveDays" type="Integer" mandatory="true" displayName="请假天数" persist="true"/>
</start>
```

**对应的 example**：`examples/conditional-branch/v1.xwf`

---

## 并行分发（splitType=and）

同时分发到多个步骤。

```xml
<step name="submit" displayName="提交申请">
    <transition splitType="and">
        <to-step stepName="tech-review"/>
        <to-step stepName="finance-review"/>
    </transition>
</step>

<join name="merge" displayName="汇聚" joinType="and">
    <step-ref name="parallel-merge"/>
    <assignment>
        <actors>
            <actor actorModelId="final" actorType="user" actorId="final-user"/>
        </actors>
    </assignment>
    <transition onAppStates="agree">
        <to-end/>
    </transition>
</join>
```

**对应的 example**：`examples/parallel-branch/v1.xwf`

---

## 驳回处理

### 基本驳回

```xml
<!-- oa.xwf 已定义公共 reject action -->
<step name="manager-approval" displayName="经理审批" allowReject="true">
    <!-- 引用 reject action 后即可使用"退回"按钮 -->
    <transition onAppStates="agree">
        <to-end/>
    </transition>
</step>
```

`oa.xwf` 的公共 reject action 默认行为：退回到 DAG 中当前步骤的直接前驱。

### 驳回到指定步骤

通过 API 传入 `rejectSteps` 参数：

```java
Map<String, Object> args = new HashMap<>();
args.put("rejectSteps", Set.of("submit"));
step.invokeAction("reject", args, ctx);
```

或由前端传入，引擎通过 `wfRt.getRejectSteps()` 获取。

**对应的 example**：`examples/reject-withdraw/v1.xwf`

---

## 撤回

```xml
<step name="submit" displayName="提交" allowWithdraw="true">
    <transition onAppStates="agree">
        <to-step stepName="manager-approval"/>
    </transition>
</step>
```

撤回条件：
1. 当前步骤必须已经是历史（已完成）
2. 所有后续步骤必须尚未完成（未进入历史）

**对应的 example**：`examples/reject-withdraw/v1.xwf`

---

## 抄送通知（specialType="cc"）

审批通过后自动通知相关人员知悉。

```xml
<step name="cc-notify" displayName="抄送通知" specialType="cc">
    <assignment>
        <actors>
            <actor actorModelId="hr" actorType="role" actorId="hr-staff" selectUser="true"/>
        </actors>
    </assignment>
    <transition onAppStates="confirm">
        <to-end/>
    </transition>
</step>
```

**关键规则**：
- `specialType="cc"` 标记步骤为抄送类型
- `oa:WhenAllowAgree` 阻止 cc 步骤执行 agree（只有 confirm 可用）
- `oa:WhenAllowConfirm` 只允许 cc 步骤执行 confirm
- cc 步骤不阻塞流程，接收人只需点击"已阅"（confirm）

**对应的 example**：`examples/cc-notify/v1.xwf`

---

## 转办与委派

通过引擎 API 实现。

### 转办（当前步骤结束）

```java
// 退出当前步骤，新步骤交给他人
step.transferToActor(new WfActorAndOwner("user", "newUserId"), true, ctx);
```

### 委派（当前步骤等待）

```java
// 委派人处理完成后回到原步骤
step.transferToActor(new WfActorAndOwner("user", "delegateUserId"), false, ctx);
```

**对应的 example**：`examples/transfer-delegate/v1.xwf`

---

## 子流程（\<flow\>）

将独立审批逻辑封装为子工作流。

```xml
<flow name="handover-subflow" displayName="交接确认子流程">
    <sub-flow wfName="examples/subprocess/sub-workflow" wfVersion="1">
        <input name="employeeId" from="wfVars.employeeId"/>
        <return var="handoverResult" output="result"/>
    </sub-flow>
</flow>
```

**对应的 example**：`examples/subprocess/v1.xwf` + `examples/subprocess/sub-workflow/v1.xwf`

---

## 综合示例

`examples/comprehensive-leave/v1.xwf` 是一个完整的请假审批流程，涵盖本文描述的多项模式。

**对应的 example**：`examples/comprehensive-leave/v1.xwf`

---

## 单元测试

所有 14 个顶层示例都有对应的测试，集中在 `TestWorkflowExamples.java`（`subprocess` 示例内嵌一个 `sub-workflow`）：

```bash
# 运行全部示例测试
./mvnw test -pl nop-wf/nop-wf-service -Dtest="TestWorkflowExamples"
```

**测试覆盖范围**：

| 测试方法 | 覆盖的示例 |
|----------|-----------|
| `testSimpleApproval` | 简单审批 + 驳回 |
| `testCcNotify` | 抄送通知 |
| `testCountersign` | 并行会签 |
| `testVoteSign` | 投票会签 |
| `testOrSign` | 或签 |
| `testSequentialApproval` | 顺序审批 |
| `testConditionalBranch` | 条件分支 |
| `testParallelBranch` | 并行分支 |
| `testInclusiveBranch` | 包容分支 |
| `testRejectWithdraw` | 驳回撤回 |
| `testTransferDelegate` | 转办委派 |
| `testSubprocess` | 子流程 |
| `testTimeoutAuto` | 超时自动 |
| `testComprehensiveLeave` | 综合请假 |

---

## 参考

- [工作流配置参考](../02-core-guides/workflow-configuration.md)
- 示例定义：`nop-wf/nop-wf-service/src/main/resources/_vfs/nop/wf/examples/`
- 基模板：`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/oa.xwf`
- XDef Schema：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef`
- 引擎源码：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java`
