# 工作流引擎使用模式与实现指南（最终版）

本文档基于 Nop Workflow 的实际 xdef 元模型和源码实现，提供准确的使用指南。

> **严格遵循**: 所有示例严格符合 `/nop/schema/wf/wf.xdef` 和 `/nop/schema/wf/assignment.xdef` 元模型定义

## 关键修正说明

1. **source元素语法**: 使用xpl模板或xscript语法，不需要 `${}` 包裹整个表达式
2. **撤回机制**: 通过 `forWithdraw="true"` 的 action 实现
3. **驳回机制**: 通过 `forReject="true"` 的 action 实现
4. **actor配置**: 必须包含 `actorModelId`、`actorType`、`actorId` 属性
5. **transition配置**: 使用 `<to-step stepName="..."/>` 而非 `name` 属性

---

## 1. 工作流定义模式

### 1.1 基本结构

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<workflow wfName="leave-approval" wfVersion="1" displayName="请假审批流程"
          xmlns:x="/nop/schema/xdsl.xdef">
  
  <start startStepName="submit">
    <arg name="leaveDays" type="Integer" mandatory="true" displayName="请假天数"/>
  </start>
  
  <steps>
    <step name="submit" displayName="提交申请">
      <assignment>
        <actors>
          <actor actorModelId="starter" actorType="wf-actor:Starter" selectUser="true"/>
        </actors>
      </assignment>
      <actions>
        <action name="submit" displayName="提交">
          <transition splitType="and">
            <to-step stepName="approval"/>
          </transition>
        </action>
      </actions>
    </step>
    
    <step name="approval" displayName="审批" allowWithdraw="true" allowReject="true">
      <assignment>
        <actors>
          <actor actorModelId="manager" actorType="wf-actor:StarterManager" 
                 selectUser="true" wf:upLevel="1"/>
        </actors>
      </assignment>
      <actions>
        <action name="approve" displayName="同意">
          <transition splitType="and">
            <to-end/>
          </transition>
        </action>
        <action name="reject" displayName="拒绝" forReject="true">
          <transition splitType="and">
            <to-step stepName="submit" backLink="true"/>
          </transition>
        </action>
      </actions>
    </step>
  </steps>
  
  <end/>
</workflow>
```

---

## 2. 任务分配模式

### 2.1 Assignment 机制

Nop Workflow 区分**允许的 Actor** 和**实际执行的 Actor**：

- **允许的 Actor**: `<assignment>` 中配置的 actor
- **实际执行的 Actor**: 运行时从前台传入或自动选择的 actor

**选择模式**:

```xml
<assignment selection="auto" mustInAssignment="true" ignoreNoAssign="false">
  <actors>
    <actor actorModelId="user1" actorType="user" actorId="user001" selectUser="true"/>
  </actors>
</assignment>
```

- `selection="auto"`: 自动选择所有允许的 actor（默认）
- `selection="single"`: 从允许的 actor 中选择唯一一个
- `selection="multiple"`: 从允许的 actor 中选择多个

### 2.2 Actor 类型

#### 2.2.1 内置类型

```xml
<!-- 指定用户 -->
<actor actorModelId="user1" actorType="user" actorId="user001" selectUser="true"/>

<!-- 指定角色 -->
<actor actorModelId="role1" actorType="role" actorId="finance-manager" selectUser="true"/>

<!-- 指定部门 -->
<actor actorModelId="dept1" actorType="dept" actorId="dept001" selectUser="true"/>
```

#### 2.2.2 动态类型（wf-actor标签库）

```xml
<!-- 流程发起人 -->
<actor actorModelId="starter" actorType="wf-actor:Starter" selectUser="true"/>

<!-- 发起人的上级 -->
<actor actorModelId="starter-manager" actorType="wf-actor:StarterManager" 
       selectUser="true" wf:upLevel="1"/>

<!-- 发起人的部门负责人 -->
<actor actorModelId="dept-manager" actorType="wf-actor:StarterDeptManager" 
       selectUser="true" wf:upLevel="0"/>

<!-- 当前步骤actor的上级 -->
<actor actorModelId="step-manager" actorType="wf-actor:StepActorManager" 
       selectUser="true" wf:upLevel="1"/>

<!-- 所有可选用户（用于发起人选择） -->
<actor actorModelId="all-users" actorType="wf-actor:AllUser" selectUser="true"/>
```

**参数说明**:
- `wf:upLevel="0"`: 当前层级
- `wf:upLevel="1"`: 直属上级
- `wf:upLevel="2"`: 上两级

### 2.3 发起人选择实现

**业务场景**: 发起人从所有用户中选择下一步处理人

```xml
<assignment selection="multiple" mustInAssignment="false" ignoreNoAssign="false">
  <actors>
    <actor actorModelId="all-users" actorType="wf-actor:AllUser" selectUser="true"/>
  </actors>
</assignment>
```

**使用方式**: 前台调用时传入 `selectedActors` 参数

---

## 3. 多人审批模式

使用 `execGroupType` 属性实现：

### 3.1 顺序审批 (seq-group)

```xml
<step name="multi-approval" displayName="多级审批" execGroupType="seq-group">
  <assignment selection="auto">
    <actors>
      <actor actorModelId="level1" actorType="wf-actor:StarterManager" 
             selectUser="true" wf:upLevel="1" voteWeight="1"/>
      <actor actorModelId="level2" actorType="wf-actor:StarterManager" 
             selectUser="true" wf:upLevel="2" voteWeight="1"/>
      <actor actorModelId="level3" actorType="wf-actor:StarterManager" 
             selectUser="true" wf:upLevel="3" voteWeight="1"/>
    </actors>
  </assignment>
</step>
```

### 3.2 会签 (and-group)

```xml
<step name="countersign" displayName="会签" execGroupType="and-group">
  <assignment selection="auto">
    <actors>
      <actor actorModelId="finance" actorType="role" actorId="finance" 
             selectUser="true" voteWeight="1"/>
      <actor actorModelId="hr" actorType="role" actorId="hr" 
             selectUser="true" voteWeight="1"/>
      <actor actorModelId="legal" actorType="role" actorId="legal" 
             selectUser="true" voteWeight="1"/>
    </actors>
  </assignment>
</step>
```

### 3.3 或签 (or-group)

```xml
<step name="or-sign" displayName="或签" execGroupType="or-group">
  <assignment selection="auto">
    <actors>
      <actor actorModelId="duty-staff" actorType="role" actorId="duty-staff" 
             selectUser="true" voteWeight="1"/>
    </actors>
  </assignment>
</step>
```

### 3.4 投票 (vote-group)

```xml
<step name="voting" displayName="投票" execGroupType="vote-group" 
      passWeight="50" passPercent="0.5">
  <assignment selection="auto">
    <actors>
      <actor actorModelId="chairman" actorType="user" actorId="chairman" 
             selectUser="true" voteWeight="40"/>
      <actor actorModelId="director1" actorType="user" actorId="director1" 
             selectUser="true" voteWeight="20"/>
      <actor actorModelId="director2" actorType="user" actorId="director2" 
             selectUser="true" voteWeight="20"/>
      <actor actorModelId="director3" actorType="user" actorId="director3" 
             selectUser="true" voteWeight="20"/>
    </actors>
  </assignment>
</step>
```

---

## 4. 流转控制模式

### 4.1 条件分支 (splitType="or")

```xml
<action name="route" displayName="路由">
  <transition splitType="or">
    <to-step stepName="manager-approval" when="${amount &lt;= 10000}"/>
    <to-step stepName="director-approval" when="${amount > 10000 &amp;&amp; amount &lt;= 100000}"/>
    <to-step stepName="ceo-approval" when="${amount > 100000}"/>
  </transition>
</action>
```

### 4.2 并行分支 (splitType="and")

```xml
<action name="fork" displayName="分支">
  <transition splitType="and">
    <to-step stepName="branch1"/>
    <to-step stepName="branch2"/>
    <to-step stepName="branch3"/>
  </transition>
</action>
```

### 4.3 Join 节点

**基本 Join（等待所有上游步骤完成）**:

```xml
<join name="join" displayName="汇聚" joinType="and" waitStepNames="branch1,branch2,branch3">
  <assignment>
    <actors>
      <actor actorModelId="user1" actorType="user" actorId="user001" selectUser="true"/>
    </actors>
  </assignment>
  <transition splitType="and">
    <to-end/>
  </transition>
</join>
```

**带分组表达式的 Join**:

`join-group-expr` 用于动态分组：相同分组值的步骤会被归为一组，Join 只等待同组内的所有上游步骤完成。

```xml
<join name="join" displayName="汇聚" joinType="and" waitStepNames="branch1,branch2,branch3">
  <join-group-expr>
    <c:script>
      return wf.bizEntity.deptId
    </c:script>
  </join-group-expr>
  <assignment>
    <actors>
      <actor actorModelId="user1" actorType="user" actorId="user001" selectUser="true"/>
    </actors>
  </assignment>
  <transition splitType="and">
    <to-end/>
  </transition>
</join>
```

**实现原理**（源码：`WorkflowEngineImpl.java:317-339, 464-493`）:
1. `join-group-expr` 在创建 Join 步骤时执行，生成 `joinGroup` 值
2. 相同 `joinGroup` 和 `stepName` 的步骤会复用同一个 Join 实例
3. `checkWaitingJoinStep()` 检查所有上游步骤（同组）是否完成
4. 所有上游步骤完成后，Join 步骤从 `WAITING` 转为 `ACTIVATED`

### 4.4 子流程 (flow)

使用 `<flow>` 元素调用子工作流。

```xml
<steps>
  <step name="wf-start" displayName="开始">
    <assignment>
      <actors>
        <actor actorModelId="starter" actorType="wf-actor:Starter" selectUser="true"/>
      </actors>
    </assignment>
    <transition splitType="and">
      <to-step stepName="subFlow"/>
    </transition>
  </step>
  
  <flow name="subFlow" displayName="子流程">
    <start wfName="test/subflow" wfVersion="1">
      <!-- 传递参数给子流程 -->
      <arg name="bizKey">
        <source>wf.bizEntity.id</source>
      </arg>
    </start>
    <transition splitType="and">
      <to-end/>
    </transition>
  </flow>
</steps>
```

**实现原理**（源码：`WorkflowEngineImpl.java:372-374, 416-425, 1015-1036`）:
1. 子流程步骤创建时进入 `WAITING` 状态
2. 调用 `Coordinator.startSubFlow()` 启动子流程
3. 子流程结束时调用 `notifySubFlowEnd()` 通知父流程
4. 父流程步骤收到通知后从 `WAITING` 转为 `ACTIVATED`

**参数传递**:
```xml
<flow name="subFlow">
  <start wfName="sub-workflow" wfVersion="1">
    <arg name="param1">
      <source>wf.bizEntity.field1</source>
    </arg>
    <arg name="param2">
      <source>wf.globalVars.get('key')</source>
    </arg>
  </start>
</flow>
```

**结果获取**（子流程输出存入 `VAR_SUB_WF_RESULTS` 变量）:
```xml
<flow name="subFlow">
  <start wfName="sub-workflow" wfVersion="1">
    <arg name="input">
      <source>data</source>
    </arg>
    <!-- 返回值映射（可选）-->
    <return var="result" output="output1"/>
  </start>
  <transition splitType="and">
    <to-step stepName="next"/>
  </transition>
</flow>
```

---

## 5. 撤回与驳回

### 5.1 撤回机制

**实现方式**: 定义一个 `forWithdraw="true"` 的 common action

```xml
<workflow>
  <actions>
    <!-- 撤回动作（common action，所有步骤可用） -->
    <action name="_withdrawAction" displayName="撤回" 
            common="true" forWithdraw="true" forHistory="true"/>
  </actions>
  
  <steps>
    <!-- 在需要允许撤回的步骤上设置 allowWithdraw="true" -->
    <step name="submit" displayName="提交申请" allowWithdraw="true">
      <assignment>
        <actors>
          <actor actorModelId="starter" actorType="wf-actor:Starter" selectUser="true"/>
        </actors>
      </assignment>
      <actions>
        <action name="submit" displayName="提交">
          <transition splitType="and">
            <to-step stepName="approval"/>
          </transition>
        </action>
      </actions>
    </step>
    
    <step name="approval" displayName="审批">
      <assignment>
        <actors>
          <actor actorModelId="manager" actorType="wf-actor:StarterManager" 
                 selectUser="true" wf:upLevel="1"/>
        </actors>
      </assignment>
    </step>
  </steps>
</workflow>
```

**实现原理**（源码：`WorkflowEngineImpl.java:1221-1240, 1613-1631`）:
1. `canWithdraw()` 验证条件：
   - 步骤必须是历史记录（已完成的步骤）
   - 步骤必须配置 `allowWithdraw="true"`
   - 后续步骤不能是已完成状态（REJECTED 除外）
2. `doWithdraw()` 执行撤回：
   - 将所有后续步骤状态设为 `WITHDRAWN`
   - 重新创建当前步骤实例（恢复到撤回前状态）

**使用说明**:
1. `forWithdraw="true"` 标记此 action 为撤回操作
2. `forHistory="true"` 标记此 action 只对历史步骤可用
3. `common="true"` 使 action 对所有步骤可用
4. `allowWithdraw="true"` 在步骤上启用撤回功能
5. 前台调用 `invokeAction(step, "_withdrawAction", args, ctx)` 执行撤回
### 5.2 驳回机制

**实现方式**: 定义一个 `forReject="true"` 的 common action

**方式一：驳回至上一节点（自动）**

```xml
<action name="_rejectAction" displayName="驳回" 
        common="true" forReject="true" forActivated="true"/>
```

如果不指定目标步骤，系统自动选择当前步骤的前一个正常步骤。

**方式二：驳回到指定步骤**

```xml
<!-- 驳回到发起人 -->
<action name="_rejectToStarter" displayName="驳回到发起人" 
        common="true" forReject="true" forActivated="true">
  <transition splitType="and">
    <to-step stepName="submit" backLink="true"/>
  </transition>
</action>

<!-- 驳回到部门审批 -->
<action name="_rejectToDept" displayName="驳回到部门审批" 
        common="true" forReject="true" forActivated="true">
  <transition splitType="and">
    <to-step stepName="dept-approval" backLink="true"/>
  </transition>
</action>
```

**方式三：运行时动态指定驳回目标**

```xml
<action name="_rejectDynamic" displayName="驳回至指定步骤" 
        common="true" forReject="true" forActivated="true">
  <!-- 不配置 transition，通过参数 rejectSteps 指定目标 -->
</action>
```

前台调用时传入 `rejectSteps` 参数：
```java
Map<String, Object> args = new HashMap<>();
args.put("rejectSteps", "submit");  // 或 "step1,step2" 多个目标
step.invokeAction("_rejectDynamic", args, ctx);
```

**完整示例**:
```xml
<workflow>
  <actions>
    <!-- 驳回至上一节点 -->
    <action name="_reject" displayName="驳回" 
            common="true" forReject="true" forActivated="true"/>
    
    <!-- 驳回到发起人 -->
    <action name="_rejectToStarter" displayName="驳回到发起人" 
            common="true" forReject="true" forActivated="true">
      <transition splitType="and">
        <to-step stepName="submit" backLink="true"/>
      </transition>
    </action>
  </actions>
  
  <steps>
    <step name="submit" displayName="提交申请">
      <assignment>
        <actors>
          <actor actorModelId="starter" actorType="wf-actor:Starter" selectUser="true"/>
        </actors>
      </assignment>
    </step>
    
    <step name="approval" displayName="审批" allowReject="true">
      <assignment>
        <actors>
          <actor actorModelId="manager" actorType="wf-actor:StarterManager" 
                 selectUser="true" wf:upLevel="1"/>
        </actors>
      </assignment>
    </step>
  </steps>
</workflow>
```

**实现原理**（源码：`WorkflowEngineImpl.java:1178-1219`）:
1. `doReject()` 获取驳回目标步骤：
   - 优先使用 `wfRt.getRejectSteps()`（通过参数传入）
   - 否则使用 `step.getPrevNormalStepsInTree()` 获取前一步骤
2. 验证目标步骤必须是当前步骤的祖先节点（`dag.hasAncestor()`）
3. `doRejectStep()` 重新创建被驳回步骤的实例

**使用说明**:
1. `forReject="true"` 标记此 action 为驳回操作
2. `forActivated="true"` 标记此 action 只对激活状态的步骤可用
3. `backLink="true"` 标记回退连接，避免循环检测
4. `allowReject="true"` 在步骤上启用驳回功能（可选，用于界面控制）
5. 可以定义多个驳回 action 指向不同目标步骤
---

## 6. Source 元素的正确语法

### 6.1 使用 c:script 标签

```xml
<action name="approve" displayName="同意">
  <source>
    <c:script>
      // 直接编写脚本，不需要 ${} 包裹
      log.info('审批通过: ' + step.stepName)
      wf.globalVars.setVar('approvedBy', caller.userId)
      wf.globalVars.setVar('approveTime', sysDate)
    </c:script>
  </source>
  <transition splitType="and">
    <to-step stepName="next-step"/>
  </transition>
</action>
```

### 6.2 使用表达式

```xml
<action name="check" displayName="检查">
  <source>
    <!-- 简单表达式可以直接写 -->
    ${form.amount > 10000 ? 'high' : 'low'}
  </source>
</action>
```

### 6.3 在 on-enter/on-exit 中使用

```xml
<step name="approval" displayName="审批">
  <on-enter>
    <c:script>
      log.info('进入步骤: ' + step.stepName)
      // 初始化步骤变量
      step.vars.put('startTime', sysDate)
    </c:script>
  </on-enter>
  
  <on-exit>
    <c:script>
      log.info('退出步骤: ' + step.stepName)
      // 记录处理时长
      duration = sysDate - step.vars.get('startTime')
      log.info('处理时长: ' + duration + 'ms')
    </c:script>
  </on-exit>
</step>
```

---

## 7. 完整示例：请假审批流程

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<workflow wfName="leave-approval" wfVersion="1" displayName="请假审批流程"
          xmlns:x="/nop/schema/xdsl.xdef">
  
  <description>员工请假审批流程</description>
  
  <!-- 启动参数 -->
  <start startStepName="submit">
    <arg name="leaveDays" type="Integer" mandatory="true" displayName="请假天数"/>
    <arg name="leaveType" type="String" displayName="请假类型"/>
    <arg name="reason" type="String" displayName="请假原因"/>
  </start>
  
  <!-- 公共动作 -->
  <actions>
    <!-- 撤回动作 -->
    <action name="_withdraw" displayName="撤回" common="true" forWithdraw="true">
      <transition splitType="and">
        <to-step stepName="submit" backLink="true"/>
      </transition>
    </action>
    
    <!-- 驳回动作 -->
    <action name="_reject" displayName="驳回" common="true" forReject="true">
      <transition splitType="and">
        <to-step stepName="submit" backLink="true"/>
      </transition>
    </action>
  </actions>
  
  <!-- 流程步骤 -->
  <steps>
    <!-- 提交申请 -->
    <step name="submit" displayName="提交申请">
      <assignment>
        <actors>
          <actor actorModelId="starter" actorType="wf-actor:Starter" selectUser="true"/>
        </actors>
      </assignment>
      <actions>
        <action name="submit" displayName="提交">
          <transition splitType="and">
            <to-step stepName="dept-approval"/>
          </transition>
        </action>
      </actions>
    </step>
    
    <!-- 部门审批 -->
    <step name="dept-approval" displayName="部门审批" 
          allowWithdraw="true" allowReject="true">
      <assignment>
        <actors>
          <actor actorModelId="dept-manager" actorType="wf-actor:StarterDeptManager" 
                 selectUser="true" wf:upLevel="0"/>
        </actors>
      </assignment>
      <on-enter>
        <c:script>
          log.info('进入部门审批: ' + wf.starter.deptId)
        </c:script>
      </on-enter>
      <actions>
        <action name="approve" displayName="同意">
          <source>
            <c:script>
              log.info('部门审批通过')
              wf.globalVars.setVar('deptApprover', caller.userId)
            </c:script>
          </source>
          <transition splitType="or">
            <to-step stepName="hr-approval" when="${leaveDays &lt;= 3}"/>
            <to-step stepName="ceo-approval" when="${leaveDays > 3}"/>
          </transition>
        </action>
      </actions>
    </step>
    
    <!-- HR审批 -->
    <step name="hr-approval" displayName="HR审批" 
          allowWithdraw="true" allowReject="true">
      <assignment>
        <actors>
          <actor actorModelId="hr-role" actorType="role" actorId="hr-manager" 
                 selectUser="true"/>
        </actors>
      </assignment>
      <actions>
        <action name="approve" displayName="同意">
          <transition splitType="and">
            <to-end/>
          </transition>
        </action>
      </actions>
    </step>
    
    <!-- CEO审批 -->
    <step name="ceo-approval" displayName="CEO审批" 
          allowWithdraw="true" allowReject="true">
      <assignment>
        <actors>
          <actor actorModelId="ceo" actorType="user" actorId="ceo" selectUser="true"/>
        </actors>
      </assignment>
      <actions>
        <action name="approve" displayName="同意">
          <transition splitType="and">
            <to-end/>
          </transition>
        </action>
      </actions>
    </step>
  </steps>
  
  <!-- 结束节点 -->
  <end>
    <output name="result" type="String" displayName="审批结果"/>
  </end>
</workflow>
```

---

## 8. 重要提醒

### 8.1 必须严格遵循 xdef

所有 XML 配置必须符合元模型定义：

- ✅ 正确: `actorType`, `actorId`, `actorModelId`
- ❌ 错误: `type`, `id` (不存在)

- ✅ 正确: `execGroupType="and-group"`
- ❌ 错误: `performType="countersign"` (不存在)

- ✅ 正确: `<to-step stepName="..."/>`
- ❌ 错误: `<toStep name="..."/>` (属性名错误)

### 8.2 Source 语法

- ✅ 正确: 使用 `<c:script>` 或直接表达式
- ❌ 错误: 整个 source 用 `${}` 包裹

### 8.3 撤回和驳回

- ✅ 已实现: 通过 `forWithdraw` 和 `forReject` 的 common action
- ❌ 不需要: 额外的代理审批系统

---

**参考文件**:
- 元模型: `/nop/schema/wf/wf.xdef`, `/nop/schema/wf/assignment.xdef`
- 实现代码: `nop-wf-core` 模块
- 测试示例: `nop-wf-service/src/test/resources/_vfs/nop/wf/test/`
