# FlowLong / Warm-Flow vs nop-wf 工作流引擎深度对比分析

> Status: resolved
> Date: 2026-07-02
> Scope: 三套工作流引擎的架构、数据模型、流转能力、扩展点、覆盖度核查
> Conclusion: nop-wf 在引擎核心能力（节点/路由/会签/驳回/转办/子流程/扩展点/审批人解析）上 **基本覆盖** FlowLong 与 Warm-Flow 的功能集，且在 XDSL 可逆计算、状态机严谨性、信号机制、业务实体绑定等方面 **明显领先**；识别的 5 个缺口（AI 审批/调度器/离职转办/票签策略/动态审批）已落入设计文档，决策为"通过扩展点和外置模块补齐 4 项、动态审批重新定义为设计选择"。
> Superseded By: `ai-dev/design/nop-wf/extensions-design.md`（缺口处理决策已沉淀为设计）

---

## Context

- **要回答的问题**：nop-wf 是否能作为 FlowLong 和 Warm-Flow 的上位替代？哪些功能点已覆盖、哪些缺失或弱化？
- **涉及模块**：`c:/can/sources/flowlong`（com.aizuda.bpm.engine）、`c:/can/sources/warm-flow`（org.dromara.warm.flow.core）、`nop-entropy/nop-wf`
- **约束**：对比基于源码（不依赖 README），结论需可追溯 `file:line`
- **核查路径**：架构 → 数据模型 → 节点/路由 → 办理方式 → 流转操作 → 审批人 → 驳回/撤回 → 条件/表达式 → 监听器/扩展点 → 子流程 → AI/调度/表单/流程图 → 多租户/解耦 → 覆盖度矩阵

---

## 一、三个项目一句话定位

| 项目 | 定位 | 技术代际 |
|------|------|---------|
| **FlowLong** | 极简 JSON 工作流引擎，"中国特色流程操作"卖点的集大成者，Aizuda（保险蛙）出品，强调 AI 审批 + 离职转办 + 穿越时空 | 2.5 代（JSON 模型 + 多 SPI） |
| **Warm-Flow** | Dromara 社区"7 张表"极简引擎，强调多 ORM 多框架解耦、设计器 jar 集成、流程图自带状态着色 | 2 代（interface+Supplier ORM 无关） |
| **nop-wf** | Nop 平台工作流引擎，基于 XDSL（.xwf + XDef Schema），强调可逆计算、状态机严谨、信号机制、与 nop-auth/nop-job 深度协同 | 3 代（XDSL + Delta 定制 + 模型分析器） |

---

## 二、架构对比

### 2.1 引擎入口与分层

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 引擎门面 | `FlowLongEngine` 单接口 + `configure(ctx)` 注入 4 服务（`FlowLongEngine.java:36-412`） | `FlowEngine` **静态注册中心**（非实例接口，`FlowEngine.java:41-170`） | `IWorkflow`（实例门面）+ `IWorkflowManager`（工厂+缓存）+ `IWorkflowStep`（步骤门面）三接口分离（`IWorkflow.java:25`、`IWorkflowManager.java:21`、`IWorkflowStep.java:25`） |
| 服务分层 | 4 大 Service：Process/Runtime/Task/Query（`ProcessService.java:24` 等） | 9 大 Service：Def/Ins/Task/HisTask/Node/User/Form/Skip/Chart（`DefService.java:34` 等） | 引擎内部 `IWorkflowEngine`（23 方法，`IWorkflowEngine.java:22-89`）+ `WorkflowServiceSpi`（6 异步方法，`WorkflowServiceSpi.java:24-63`）+ 11 个 CRUD BizModel |
| 执行上下文 | `Execution` 数据总线（`Execution.java:41-383`） | 流转参数包 `FlowParams`（`FlowParams.java:33-335`） | `WfRuntime` 封装 `IEvalScope` + 暴露 `wf/wfRt/wfVars` 根变量（`WfRuntime.java:40-391`） |
| 编排机制 | 直接方法调用 + `FlowDataTransfer` ThreadLocal 传参（`FlowDataTransfer.java:20`） | 直接方法调用 + `FlowParams` 显式传递 | **trampoline 式延迟执行队列** `commandQueue`（`WorkflowImpl.java:44-98`），避免级联递归爆栈 |

**判断**：nop-wf 的"门面三分 + 延迟执行队列"在工程严谨性上最强；Warm-Flow 的"静态注册中心 + Service 多达 9 个"在职责切分上最细；FlowLong 介于二者之间。

### 2.2 模型存储格式

| 项目 | 模型格式 | Schema | 解析 |
|------|---------|--------|------|
| FlowLong | **JSON BPM**（树状递归节点，非 BPMN 图） | 无 schema，靠 POJO 反序列化 | `DefaultProcessModelParser.java:28-77` |
| Warm-Flow | **JSON DefJson**（节点 + Skip 连线分离存储） | 无 schema | `DefJson.java:44-291` + 各 ORM 序列化 |
| nop-wf | **XDSL（.xwf XML）** | `/nop/schema/wf/wf.xdef`（296 行 XDef，`wf.xdef`） | `WfModelParser` + 自动生成模型类 + `WfModelAnalyzer` 拓扑分析（`WfModelAnalyzer.java:46-265`） |

**nop-wf 的关键优势**：
- XDef schema 提供 **编译期模型校验**（FlowLong/Warm-Flow 均无）
- 支持 `x:extends` 模板继承（`oa.xwf` 被 15 个示例继承）
- 支持 Delta 定制（运行时覆盖而不改源）
- 模型加载时**自动构建 DAG 并检测循环**（`WfModelAnalyzer.java:60-71, 257-259`），回退连接需 `backLink=true` 豁免——这是 FlowLong/Warm-Flow 都没有的健壮性保证

---

## 三、数据模型对比

### 3.1 表清单

| FlowLong（8 表） | Warm-Flow（8 表） | nop-wf（11 表 + user_delegate） |
|------------------|-------------------|-------------------------------|
| `flw_process` | `flow_definition` | `nop_wf_definition` + `nop_wf_definition_auth`（权限独立成表） |
| `flw_instance` | `flow_instance`（含 `def_json` 流程图快照） | `nop_wf_instance`（含 `bizKey/bizObjName/bizObjId` 业务绑定三件套） |
| `flw_his_instance` | （并入 instance，靠 `flowStatus` 区分） | `nop_wf_status_history`（状态变迁历史，独立） |
| `flw_task` | `flow_task` | `nop_wf_step_instance`（**52 列**，最复杂） |
| `flw_task_actor` | `flow_user`（type+processed_by 多种角色） | （actor 信息内嵌在 `nop_wf_step_instance` 5 字段：`actorModelId/actorType/actorId/actorDeptId/actorName`） |
| `flw_his_task` | `flow_his_task`（含 `cooperateType/approver/collaborator`） | `nop_wf_action`（每次动作历史 + 审批意见 `opinion`） |
| `flw_his_task_actor` | （并入 his_task） | `nop_wf_step_instance_link`（步骤间迁移链接） |
| `flw_ext_instance`（实例模型快照） | （并入 instance `def_json`） | `nop_wf_var` + `nop_wf_output`（KV 变量，按类型分 5 列） |
| — | `flow_node` / `flow_skip`（节点+连线独立成表） | （节点和转移定义在 `.xwf` 文件，不入库） |
| — | `flow_form`（内置表单） | （formPath 字段指向外部表单，无独立表） |
| — | — | `nop_wf_log`（错误日志） |
| — | — | `nop_wf_work`（代办） |
| — | — | `nop_wf_user_delegate`（用户委托配置） |

### 3.2 关键设计差异

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| **参与者存储** | 独立 `flw_task_actor` 表，含 `weight/agentId/agentType/ext` | 独立 `flow_user` 表，`type` 区分审批/转办/委派 | **内嵌** step 表 5 字段 + `ownerId/assignerId/callerId` 分离"配置 actor/实际处理人/分配人/调用者" |
| **历史策略** | 活动/历史双表镜像（`flw_task` ↔ `flw_his_task`） | 单表 + `flowStatus` 区分 | **活动=history 边界** `WF_STEP_STATUS_HISTORY_BOUND=COMPLETED=40`（`NopWfCoreConstants.java:19`），`status >= 40` 即历史 |
| **会签状态** | `task.performType + weight` | `node.nodeRatio + cooperateType` | **独立 execGroup UUID + execOrder + execCount**（乐观锁，`orm.xml:452-454`），每个 actor 一个 step 实例 |
| **重试/错误** | 无 | 无 | `nextRetryTime/retryCount/errCode/errMsg` + `<retry>` 配置（指数退避） |
| **业务绑定** | `businessKey`（弱） | `businessId`（弱） | `bizObjName+bizObjId+bizKey` + `bizEntityStateProp` 反写状态 + `bizEntityFlowIdProp` 反写 wfId（强） |

---

## 四、节点与路由能力对比

### 4.1 节点类型

| 节点概念 | FlowLong | Warm-Flow | nop-wf |
|---------|----------|-----------|--------|
| 显式节点类型数 | **13**（`TaskType.java:24-163`）：发起/审批/抄送/条件审批/条件分支/并行分支/包容分支/路由分支/子流程/定时器/触发器/自动通过/自动拒绝/结束 | **6**（`NodeType.java:30-57`）：开始/中间/结束/互斥/并行/包容网关 | **3**（`WfStepType.java:10-14`）：`step`/`join`/`flow` |
| 网关表达 | 节点类型即网关 | 节点类型即网关 | 通过 `<transition splitType="and/or">` + `<to-step order=N><when>` 表达（`wf.xdef:146,162`） |
| 排他网关 | `conditionNode/conditionBranch` 类型 + SpEL 优先级匹配 | SERIAL 网关 + 条件 Skip | `splitType="or"` 取首个匹配（`WorkflowEngineImpl.java:1356-1359`） |
| 并行网关 | `parallelBranch` 全分支执行 | PARALLEL 全分支执行 | `splitType="and"` 全分支执行 + `<join joinType="and">` 汇聚 |
| 包容网关 | `inclusiveBranch` 多条件匹配 | INCLUSIVE 多条件匹配 | `splitType="and"` + 每个 `<to-step>` 独立 `<when>`（示例 `inclusive-branch/v1.xwf:29-46`） |
| **动态汇聚** | ❌ 无 | ❌ 无 | ✅ `<join-group-expr>` 按运行时表达式分组（`wf.xdef:270`） |
| **汇聚 waitStep 推导** | ❌ 无 | ❌ 无 | ✅ `WfModelAnalyzer.java:192-207` 按 DAG 祖先自动推导 |
| 定时器节点 | ✅ `TaskType.timer(6)` + `extendConfig.time={"time":"1:h"}` | ❌ 无 | ⚠️ 仅 `due-time-expr` 字段 + `dueAction`，**外部调度器触发**（示例 `timeout-auto/v1.xwf:7-9` 注释） |
| 触发器节点 | ✅ `TaskType.trigger(7)` + `triggerType/delayType` | ❌ 无 | ⚠️ 通过 action + listener 等价实现 |
| 路由分支 | ✅ `routeBranch` 跳到指定节点 | ✅ `anyNodeSkip` 任意跳转 | ✅ `transitTo(stepName)` 强制迁移 + `<to-assigned>` 动态目标 |
| 自动通过/拒绝 | ✅ `autoPass/autoReject` 类型 | ⚠️ 仅 `FlowStatus.AUTO_PASS` 标记 | ⚠️ 通过 `<when>` + listener 等价实现 |

**判断**：FlowLong 把每种语义都做成显式节点类型（学习曲线低但扩展性差）；Warm-Flow 6 类够用但不够灵活；nop-wf **用"step + transition + join"三件套 + XLang 表达式** 表达所有这些语义，**更接近 BPMN 的核心抽象**，灵活性最高，但要求使用者理解 transition 模型。

### 4.2 子流程

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 是否支持 | ✅ `callProcess/callAsync/callAi` | ❌ **不支持**（单定义作用域） | ✅ `<flow>` step 类型 + `IWorkflowCoordinator` |
| 同步/异步 | ✅ 同步/异步两种（`NodeModel.callAsync`） | — | ✅ 异步为主，子流程结束通过 `notifySubFlowEnd` 回调（`WorkflowServiceImpl.java:105`） |
| 参数/返回 | 弱（仅 `businessKey`） | — | ✅ `<arg>` schema 校验 + `<return>` 输出回传（`wf.xdef:285-291`） |
| 子流程回流 | ✅ `subprocessRollback`（`TaskService.java:458`） | — | ✅ `IWorkflowStep.notifySubFlowEnd`（`WorkflowEngineImpl.java:1029-1050`） |

**判断**：Warm-Flow **完全没有子流程**；FlowLong 和 nop-wf 都支持，但 nop-wf 的参数 schema 校验和输出回传最严谨。

---

## 五、办理方式（会签/或签/票签/顺签）对比

### 5.1 支持矩阵

| 办理方式 | FlowLong | Warm-Flow | nop-wf |
|---------|----------|-----------|--------|
| **会签（所有人通过）** | ✅ `PerformType.countersign(2)` | ✅ `nodeRatio=100` | ✅ `execGroupType="and-group"` |
| **或签（任一通过）** | ✅ `PerformType.orSign(3)` | ✅ `nodeRatio=0` | ✅ `execGroupType="or-group"` |
| **顺签（按顺序）** | ✅ `PerformType.sort(1)`（默认） | ✅ 表达式末尾 `@sequence`（`CooperateType.java:179-194`） | ✅ `execGroupType="seq-group"` 按 `execOrder` |
| **票签（按权重/比例）** | ✅ `PerformType.voteSign(4)` + `passWeight`（默认 50） | ✅ `nodeRatio=0~100` 或 `passCount=N`/`rejectCount=N`/`spel@@...` | ✅ `execGroupType="vote-group"` + `passWeight`/`passPercent` |
| **抄送** | ✅ `PerformType.copy(9)` + `createCcTask` | ⚠️ 通过 cc 步骤 + `confirm` action | ✅ `specialType="cc"` + oa.xwf `confirm/delegate` |
| **分组策略** | ✅ `groupStrategy`（0认领/1全部参与） | ⚠️ 通过 `selectMode` | ✅ `selection="auto/single/multiple"` |

### 5.2 票签的拒绝剩余可达性判定（关键差异）

- **FlowLong**：`afterDoneTask` 计算剩余权重判断是否还能达到 `passWeight`（`FlowLongEngineImpl.java:464-478`）
- **Warm-Flow**：`isVoteGroupComplete` + 拒绝时判断"剩下的人是否还可能达到通过率"（`TaskServiceImpl.java:781-819`）
- **nop-wf**：✅ **`ExecGroupSupport.shouldExecGroupReject` + `isVoteGroupComplete` 也实现了这个判定**（`ExecGroupSupport.java:33-70, 84-129`），细节与 Warm-Flow 等价

**判断**：会签/或签/票签/顺签 **三者功能等价**，nop-wf **完全覆盖**。

---

## 六、中国特色操作（流转操作）对比

| 操作 | FlowLong | Warm-Flow | nop-wf | 覆盖判定 |
|------|----------|-----------|--------|---------|
| 启动 | `startInstanceById/ByKey/Process` | `InsService.start` | `IWorkflow.start` | ✅ |
| 暂存待审 | ✅ `saveAsDraft`（InstanceState=-1） | ✅ `pending`（FlowStatus=13） | ⚠️ `IWorkflow.save`（CREATED 状态，但语义偏弱，无独立的"暂存"枚举） | 🟡 弱覆盖 |
| 提交/前进 | `executeTask` | `pass/skip` | `invokeAction`（agree 等） | ✅ |
| 跳转（任意节点） | `executeJumpTask` | `passAtWill/rejectAtWill` | `transitTo(stepName)` | ✅ |
| 驳回 | `executeRejectTask` + **5 种 RejectStrategy** | `reject/rejectLast` + `skipType=REJECT` | `doReject`（forReject action）+ `rejectSteps` 参数（必须 DAG 祖先） | ✅ |
| 驳回到发起人 | ✅ `RejectStrategy.TO_INITIATOR` | ⚠️ 需配置 | ✅ 通过 `rejectSteps` 指定 start step | ✅ |
| 驳回到上一步 | ✅ `RejectStrategy.TO_PREVIOUS_NODE` | ✅ `rejectLast` | ✅ 默认行为（DAG 父节点） | ✅ |
| 驳回到指定节点 | ✅ `RejectStrategy.TO_SPECIFIED_NODE` | ✅ `passAtWill/rejectAtWill` | ✅ `rejectSteps` 参数 | ✅ |
| 驳回终止审批 | ✅ `RejectStrategy.TERMINATE_APPROVAL` | ⚠️ `termination` | ✅ `disagree` action + `<to-end>` | ✅ |
| 驳回到模型父节点 | ✅ `RejectStrategy.TO_PARENT_NODE` | ❌ 无 | ⚠️ 通过 DAG 祖先 + `rejectSteps` 等价 | 🟡 等价 |
| 驳回重新审批策略 | ✅ `rejectStart`（1继续/2回上一步） | ⚠️ | ⚠️ 通过 `<when>` 表达式自定义 | 🟡 等价 |
| 撤销（发起人撤回） | `RuntimeService.revoke` | `TaskService.revoke` | ⚠️ 无显式 `revoke`，靠 `kill`/`remove` 或自定义 action 等价 | 🟡 等价（需约定） |
| 拿回（已办拿回） | `TaskService.reclaim`（TaskType.reclaim） | `taskBack/taskBackByInsId` | ⚠️ 通过 `doWithdraw`（forWithdraw action）等价覆盖 | 🟡 等价 |
| 撤回（办完撤回） | `TaskService.withdraw` | （同上） | ✅ `doWithdraw`（`WorkflowEngineImpl.java:1265-1284`） | ✅ |
| 唤醒（历史复活） | ✅ `executeResumeTask`（TaskType.resume） | ❌ 无 | ⚠️ 通过 `triggerWaiting` + 信号等价 | 🟡 等价 |
| 转办 | `transferTask` | `transfer` | ✅ `transferToActor`（支持 exitCurrentStep true/false） | ✅ |
| **离职转办（批量）** | ✅ `transferTask(flowCreator, assignee)`（`TaskService.java:211`） | ❌ 无 | ❌ **无内置批量转办 API**，需在 BizModel 自行实现 | ❌ 缺口 |
| 委派 + 解决 | `delegateTask` + `resolveTask` | `depute`（受托人办完自动回委托人） | ✅ `transferToActor(exitCurrentStep=false)` 等价 + `nop_wf_user_delegate` 配置 | ✅ |
| 代理（agent） | ✅ `agentTask`（TaskType.agent 系列 4 种） | ❌ 无（仅 depute） | ⚠️ 通过 `nop_wf_user_delegate` + `IUserDelegateService.canDelegate` | 🟡 等价（语义稍弱） |
| 加签 | `addTaskActor`（前/后置） | `addSignature` | ✅ `addActor`（同 execGroup 追加） | ✅ |
| 减签（execGroup 成员） | `removeTaskActor` | `reductionSignature` | ✅ `cancelStep` 取消 execGroup 内目标成员 step（`IWorkflowStep.java:210`） | ✅ 等价 |
| **节点模型加签**（动态插节点） | ✅ `executeAppendNodeModel`（`FlowLong.java:396`） | ❌ 无 | ⚠️ 需 Delta 定制 .xwf 或 `transitTo` 等价 | 🟡 等价 |
| **节点模型减签**（删节点） | ✅ `executeRemoveNodeModel`（`FlowLong.java:411`） | ❌ 无 | ⚠️ 同上 | 🟡 等价 |
| 抄送 | `createCcTask` | （cc 步骤） | ✅ `specialType="cc"` | ✅ |
| 认领（角色/部门） | ✅ `claimRole/claimDepartment`（AgentType 2/3） | ❌ 无（仅权限校验） | ⚠️ 通过 `or-group` + `changeActor` 等价 | 🟡 等价 |
| 已阅 | ✅ `viewTask`（viewed 字段） | ⚠️ 通过 listener | ✅ `markRead`（isRead/readTime） | ✅ |
| 催办 | ✅ `TaskReminder` SPI | ❌ 无 | ⚠️ `remindTime/remindCount` 字段 + 外部调度 | 🟡 弱覆盖 |
| 终止 | `terminate` | `termination` | ✅ `kill` | ✅ |
| 作废 | ✅ `destroyByInstanceId` | ✅ `FlowStatus.NULLIFY` | ⚠️ `remove`（仅未启动或已结束） | 🟡 弱覆盖 |
| 暂停/激活 | `suspend/active` | `active/unActive` | ✅ `suspend/resume` | ✅ |

**操作覆盖度统计**：完全覆盖 ✅ = 17 项；等价覆盖 🟡 = 12 项；缺口 ❌ = 1 项（离职转办）。

**判断**：nop-wf 在"中国特色操作"上**绝大多数能等价实现**，但部分需要通过自定义 action + listener + XLang 表达式**自行约定**，而 FlowLong 是**开箱即用**。最显著的缺口是**离职转办**（批量改办理人）和**节点模型动态加减签**（FlowLong 独有的"运行时插节点"）。

---

## 七、审批人（Actor）解析对比

### 7.1 设置方式

| 设置类型 | FlowLong | Warm-Flow | nop-wf |
|---------|----------|-----------|--------|
| 指定成员 | ✅ NodeSetType.specifyMembers | ✅ permissionFlag 字符串 | ✅ `<actor actorType="user" actorId="...">` |
| 角色 | ✅ NodeSetType.role | ✅ permissionFlag | ✅ `actorType="role"` |
| 部门 | ✅ NodeSetType.department | ✅ permissionFlag | ✅ `actorType="dept"` |
| 主管 | ✅ NodeSetType.supervisor + `examineLevel` | ⚠️ `${handler}` + SpEL | ✅ `wf-actor:StarterManager` + `wf:upLevel` |
| **连续多级主管** | ✅ NodeSetType.multiLevelSupervisors + `directorLevel/directorMode` | ⚠️ SpEL 自实现 | ✅ `wf-actor:StarterManager` + `wf:upLevel="N"` 多级上溯 |
| 发起人自选 | ✅ NodeSetType.initiatorSelected + `selectMode` | ⚠️ | ✅ `selection="single/multiple"` + `mustInAssignment` |
| 发起人自己 | ✅ NodeSetType.initiatorThemselves | ⚠️ | ✅ `wf-actor:Starter` |
| 指定候选人 | ✅ NodeSetType.designatedCandidate | ⚠️ | ✅ `<selectExpr>` 过滤 |
| 动态分配 | ✅ `DynamicAssignee` + `FlowDataTransfer.dynamicAssignee` | ✅ `${flag}` + SpEL | ✅ `wf-actor:xxx` 标签库（任意自定义） |
| **审批人=提交人处理** | ✅ NodeApproveSelf 4 种（自审/跳过/转上级/转部门负责人） | ❌ 无 | ⚠️ 通过 `<when>` + `defaultActorExpr` 自实现 | 🟡 等价 |
| 办理人变量替换 | ⚠️ `NodeExpression` | ✅ `${flag}` + `PermissionHandler.convertPermissions` | ✅ XLang 完整表达式 |
| 权限转换（角色→用户） | ⚠️ TaskActorProvider | ✅ `PermissionHandler.convertPermissions` | ✅ `DaoWfActorResolver` + `selectUser` |

### 7.2 内置动态 actor 标签

- **FlowLong**：8 种 NodeSetType + DynamicAssignee 工厂方法
- **Warm-Flow**：仅 `${handler}` 默认 + SpEL 自由扩展
- **nop-wf**：✅ **10 种内置标签**（`wf-actor.xlib:1-129`）：CurrentCaller/Starter/StarterManager/StarterDeptManager/StepActorManager/StepActorDeptManager/PrevStepActor/ManagerActor/ManagerActorOrSysUser/SysUser/AllUser，且**可往 `/nop/wf/xlib/xxx.xlib` 自定义标签**（`WfActorAssignSupport.java:153-174`）

**判断**：nop-wf 在 actor 解析上**最完善**，"动态 actor 标签库 + upLevel 多级上溯 + 自定义标签扩展" 是 FlowLong/Warm-Flow 都不及的。

---

## 八、条件表达式与策略对比

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 表达式引擎 | SpEL（Spring）/ Snel（Solon）（`FlowLongExpression.java:26-80`） | 8 内置运算符 + SpEL + default 扩展（`ExpressionStrategy.java:25-60`） | **XLang**（Nop 自研，完整 predicate） |
| 条件语法 | SpEL 拼接 `(...) && (...)` | `op@@var\|value`（如 `eq@@flag\|4`、`spel@@#{@bean.m()}`） | `<when><gt name="wfVars.leaveDays" value="${3}"/></when>`（XML 标签） |
| 表达式作用域变量 | `args` map | `variable` map | `wf/wfRt/wfVars/actorModel`（强类型 IEvalScope，`WfRuntime.java:86-92`） |
| **可插拔策略数** | 1（SpEL 或 Snel 二选一） | ✅ **4 类策略接口**（Condition/Handler/Listener/VoteSign）+ 倒序匹配 | ⚠️ 1（XLang）但可通过自定义标签库扩展 |

**判断**：
- Warm-Flow 的"**4 类策略接口 + 倒序匹配优先级**"是最清晰的策略可插拔设计，nop-wf 没有显式等价物
- nop-wf 的 XLang 表达式能力**远超** SpEL（支持标签库、强类型、Delta 定制），但**缺少 Warm-Flow 那种"按前缀路由策略"的标准化扩展点**

---

## 九、监听器与扩展点对比

### 9.1 监听器

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 监听器类型数 | 2：`InstanceListener`/`TaskListener`（`InstanceListener.java:24`） | ✅ **5**：start/assignment/finish/create/formLoad（`Listener.java:25-58`） + `GlobalListener` | ✅ **30+ 内置事件**（`NopWfCoreConstants.java:58-94`）按 `eventPattern` 通配符匹配（`WfListenerModel.java:13-21`） |
| 全局监听器 | ❌ 无 | ✅ `GlobalListener`（系统唯一） | ✅ `<listeners eventPattern="*">` |
| 监听器执行顺序 | 单一 | 节点 → 定义 → 全局 | 按 eventPattern 匹配，多个并行 |
| 带参监听器 | ❌ 无 | ✅ `Class({"a":1})` 语法 | ✅ XLang 完整参数 |
| 表达式监听器 | ❌ 无 | ✅ SpEL 优先于反射类 | ✅ `<source>xpl</source>` 完整 XLang |
| **全局事件订阅** | ❌ 无 | ❌ 无 | ✅ **`<subscribes from=bizObjName event=actionId>`** 订阅全局 EventBus（`wf.xdef:89-96`）—— **这是 nop-wf 独有** |

### 9.2 其他扩展点

| 扩展点 | FlowLong | Warm-Flow | nop-wf |
|--------|----------|-----------|--------|
| 任务创建拦截器 | ✅ `TaskCreateInterceptor`（before/after） | ⚠️ create 监听器 | ✅ `enter-step/exit-step/activate-step` 事件 |
| 参与人提供器 | ✅ `TaskActorProvider` SPI | ✅ `PermissionHandler` | ✅ `IWfActorResolver` + 标签库 |
| 访问策略 | ✅ `TaskAccessStrategy` | ⚠️ checkAuth 内置 | ✅ `check-action-auth` XPL |
| 触发器 | ✅ `TaskTrigger` SPI（finish 回调） | ❌ 无 | ⚠️ action + listener 等价 |
| 创建时间处理器 | ✅ `FlowCreateTimeHandler`（**穿越时空/补审**） | ❌ 无 | ❌ **无等价物** |
| JSON 处理器 | ✅ `FlowJsonHandler` | ✅ `JsonConvert` SPI（5 种实现） | （用 Nop 平台统一 JSON） |
| ID 生成器 | ✅ `FlowLongIdGenerator` | ✅ `KenGen`（雪花 14/15/19 位） | （用 Nop 平台统一 ID） |
| 缓存 | ✅ `FlowCache` SPI | ⚠️ | ✅ `IWorkflowManager` 内置模型缓存 |
| 数据填充 | ❌ 无 | ✅ `DataFillHandler`（自动填充主键/时间/操作人） | （Nop 平台 ORM 统一处理） |
| 多租户 | ✅ 实体 `tenantId` | ✅ `TenantHandler` SPI | ✅ Nop 平台统一租户 |
| 软删除 | ❌ 无 | ✅ `logicDelete` 配置 | ✅ Nop 平台 ORM 统一 |

**判断**：
- FlowLong 的 `FlowCreateTimeHandler`（**穿越时空/补审场景**）是 nop-wf **没有的独特能力**
- nop-wf 的 `<subscribes>` 全局事件订阅是 FlowLong/Warm-Flow 都没有的独有能力
- Warm-Flow 的 `formLoad` 监听器在 nop-wf 中需通过表单加载钩子等价实现

---

## 十、AI 审批集成对比

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 是否支持 | ✅ **完整支持** | ❌ **不支持** | ❌ **不支持**（但 XLang 可扩展） |
| 配置入口 | `NodeModel.callAi` + `extendConfig.aiConfig`（`NodeModel.java:80, 273-298`） | — | — |
| 处理器 SPI | ✅ `FlowAiHandler`（`FlowAiHandler.java:26-124`）：handle/execute/decideRoute/decideInclusiveRoutes/onAsyncComplete | — | — |
| 响应模型 | ✅ `AiResponse`（status/decision/advice/variables/confidence/metrics/rawContent/asyncToken）（`AiResponse.java:30-252`） | — | — |
| 配置模型 | ✅ `AiConfig`（agentId/promptTemplate/confidenceThreshold=0.8/timeoutSeconds=30/fallbackStrategy=MANUAL/asyncMode/maxRetries=3/outputMapping/modelParams）（`AiConfig.java:23-105`） | — | — |
| 降级策略 | ✅ 3 种：DEFAULT_PASS/DEFAULT_REJECT/MANUAL（`AiFallbackStrategy.java:20-69`） | — | — |
| AI 状态 | ✅ 2 种：`aiProcessing(8)`/`aiManualReview(9)`（`InstanceState.java:71-75`） | — | — |
| AI 状态枚举 | ✅ `AiStatus` 6 种：SUCCESS/FAILURE/LOW_CONFIDENCE/TIMEOUT/ASYNC/FALLBACK（`AiStatus.java:20-63`） | — | — |
| 集成模式 | 同步审批 / 异步审批（asyncToken 回调）/ 低置信度降级 / AI 路由 / AI 包容分支 | — | — |

**判断**：**这是 nop-wf 最大的功能缺口之一**。FlowLong 的 AI 审批是一个完整的、生产级的设计（含置信度阈值、降级策略、异步回调、AI 路由决策），nop-wf **完全没有等价物**。虽然 nop-wf 可以通过 XLang 标签库 + listener 等价实现"调用 AI"，但缺少标准化的 `AiConfig/AiResponse/AiStatus/FlowAiHandler` 抽象。

---

## 十一、调度与提醒对比

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| **内置调度器** | ✅ `FlowLongScheduler`（抽象类，cron 默认 `*/5 * * * * ?`，`FlowLongScheduler.java:39-155`） | ❌ 无 | ❌ **无内置**（示例 `timeout-auto/v1.xwf:7-9` 明确注释依赖外部） |
| 分布式锁 | ✅ `JobLock` SPI（默认 `LocalLock` ReentrantLock，`JobLock.java:17`） | ❌ 无 | ❌ 无（依赖调用方/Nop 平台） |
| 提醒参数 | ✅ `RemindParam`（cron/weeks/workTime/maximum=999）（`RemindParam.java:22-40`） | ❌ 无 | ⚠️ `remindTime/remindCount` 字段 + `remind-time-expr`（仅字段，无内置调度） |
| 提醒器 SPI | ✅ `TaskReminder`（返回下次提醒时间） | ❌ 无 | ❌ 无 |
| 超时审批 | ✅ `termAuto/termMode`（自动通过/拒绝/超时结束）（`FlowLongScheduler.java:97-128`） | ❌ 无 | ⚠️ `dueTime` + `dueAction`（**需外部调度器扫描调用**） |
| 自动跳转 | ✅ `autoJumpTask` | ❌ 无 | ⚠️ `runAutoTransitions`（仅自动迁移，不含定时） |

**判断**：**这是 nop-wf 第二大功能缺口**。FlowLong 的"内置调度器 + 分布式锁 + 提醒/超时全自动化"是开箱即用的；nop-wf 仅提供字段约定，**必须集成外部调度器（Quartz/nop-job）**才能实现等价功能。设计哲学不同（nop-wf 强调模块解耦，调度交给 nop-job），但**对用户而言是体验差距**。

---

## 十二、表单与流程图对比

### 12.1 表单

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 表单存储 | `instanceUrl/actionUrl`（简单 URL） | ✅ **内置表单**（`flow_form.form_content` JSON） + 外挂表单（`form_path`） | `formPath` 字段（指向外部 page/yform） |
| 表单服务 | ❌ 无 | ✅ `FormService`（save/publish/copy/publishedPage/saveContent） | （Nop 平台 page/yform 体系） |
| 表单字段权限 | ❌ 无 | ❌ 无 | ✅ **`wf:formAuth`**（actor 级字段权限，`assignment.xdef:37`）—— **独有** |
| 表单数据加载监听 | ❌ 无 | ✅ `formLoad` 监听器 | （Nop 平台 page loader） |

### 12.2 流程图

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 内置流程图 | ❌ 无（依赖前端） | ✅ **完整**：`ChartService` + `def_json` 快照 + 3 状态色（NOT_DONE/TO_DO/DONE）+ 3 配色方案（默认/CLASSICS/MIMIC） | ✅ 设计器：`dingflow` + `oa-flow` 2 套（`nop-wf-web/.../designer/`） |
| 流程图数据持久化 | ❌ 无 | ✅ `flow_instance.def_json`（流转时基于快照更新状态） | ✅ `<diagram>` 字段（设计器 JSON）+ step.status 高亮 |
| 流转途径记录 | ❌ 无 | ✅ `PathWayData`（pathWayNodes/pathWaySkips） | ⚠️ 通过 `step_instance_link` 表记录迁移历史 |
| 驳回时状态重置 | ❌ 无 | ✅ `rejectReset` 递归重置后置节点 | ⚠️ 通过 step status 隐式 |
| 设计器集成 | ❌ 无 | ✅ jar 集成（Vue3 前端） | ✅ AMIS graph-designer 注册 |

**判断**：
- Warm-Flow 的"**流程图快照存实例 + 状态自动着色 + PathWayData 回放**"是最完整的流程图方案
- nop-wf 的流程图**依赖 AMIS 设计器**，能力等价但实现路径不同
- FlowLong 在流程图上**最弱**

---

## 十三、多框架/多 ORM 解耦对比

| 维度 | FlowLong | Warm-Flow | nop-wf |
|------|----------|-----------|--------|
| 框架支持 | ✅ Spring Boot + Solon（双 starter） | ✅ Spring Boot + Solon（双 plugin-modes） | ⚠️ 仅 Nop 平台（但 Nop 本身可独立运行） |
| ORM 支持 | ⚠️ 仅 MybatisPlus（`flowlong-mybatis-plus`） | ✅ **5 种**：MyBatis/Mybatis-Plus/Jpa/Easy-Query/BeetlSql | ⚠️ 仅 Nop Dao（基于 JDBC + 自研 ORM） |
| JSON 库 | ⚠️ Jackson/Snack3（按框架） | ✅ **5 种**：Jackson/Jackson3/FastJson/Gson/Snack3/Snack4（SPI） | （Nop 平台统一） |
| 数据库 | MySQL/Oracle/PostgreSQL | MySQL/Oracle/PostgreSQL/SQLServer/国产 | （Nop 平台统一，支持所有主流+国产） |
| Java 版本 | 8/17/21 | 8/17/21 | **21**（强制） |
| 实体解耦模式 | class + DAO 接口 | ✅ **interface + Supplier 工厂**（ORM 包提供实现类） | class（Nop ORM 生成） |

**判断**：
- Warm-Flow 的"**interface + Supplier + FrameInvoker**"是三者中**最彻底的解耦设计**
- nop-wf 由于绑定 Nop 平台，**解耦性最差但集成度最高**（权限/调度/ORM/Web 一站式）

---

## 十四、nop-wf 独有能力（FlowLong/Warm-Flow 都没有）

1. **XDSL 可逆计算模型**（`.xwf` + XDef Schema + Delta 定制 + `x:extends` 继承）—— 允许运行时覆盖而不改源
2. **DAG 拓扑校验 + 循环检测**（`WfModelAnalyzer.java:60-71`）—— 模型加载时保证合法性
3. **trampoline 延迟执行队列**（`WorkflowImpl.java:44-98`）—— 避免级联递归爆栈
4. **信号机制（Signal）**（`IWorkflow.java:181-189`）—— 步骤级 `waitSignals` + 实例级 `signalText` 集合，唤醒 WAITING 步骤
5. **业务实体绑定三件套**（`bizObjName+bizObjId+bizKey` + `bizEntityStateProp` 反写状态 + `bizEntityFlowIdProp` 反写 wfId）
6. **动态汇聚分组**（`<join-group-expr>`）—— 运行时按表达式分组汇聚
7. **全局事件订阅**（`<subscribes from=bizObjName event=actionId>`）—— 跨 BizModel 的事件驱动
8. **错误处理与重试**（`<retry>` 指数退避 + `<on-error>` 兜底 + `errCode/errMsg` 字段）
9. **候选人预览**（`getTransitionTargetsForAction` 在执行 action 前返回候选 actor，`WorkflowEngineImpl.java:1686-1748`）
10. **表单字段权限**（`wf:formAuth` actor 级）
11. **可扩展动态 actor 标签库**（`/nop/wf/xlib/xxx.xlib` 自定义标签）
12. **完整的操作日志三表**（`NopWfAction` 动作 + `NopWfStatusHistory` 状态变迁 + `NopWfLog` 错误）

---

## 十五、覆盖度矩阵（核心结论）

> 图例：✅ 完全覆盖 | 🟡 等价覆盖（需自行约定/配置） | ❌ 缺口 | ⭐ nop-wf 更强

| 功能域 | FlowLong | Warm-Flow | nop-wf 覆盖度 |
|--------|----------|-----------|--------------|
| 引擎架构 | — | — | ⭐（门面三分 + trampoline） |
| 模型格式 | JSON | JSON DefJson | ⭐（XDSL + XDef + Delta） |
| 模型校验 | ❌ | ❌ | ⭐（DAG + 循环检测） |
| 表数量 | 8 | 8 | 11（更细粒度） |
| 节点类型 | 13 | 6 | 3（但表达能力 ⭐） |
| 排他/并行/包容网关 | ✅ | ✅ | ✅ |
| 动态汇聚 | ❌ | ❌ | ⭐ |
| 会签/或签/顺签/票签 | ✅ | ✅ | ✅ |
| 票签拒绝剩余可达性 | ✅ | ✅ | ✅ |
| 抄送 | ✅ | 🟡 | ✅ |
| 启动/提交/跳转/驳回/撤回 | ✅ | ✅ | ✅ |
| 驳回策略（5 种） | ✅ | 🟡 | 🟡（DAG 祖先 + rejectSteps） |
| 转办/委派/加签 | ✅ | ✅ | ✅ |
| **离职转办（批量）** | ✅ | ❌ | ❌ |
| 减签（execGroup 成员） | ✅ | ✅ | ✅（cancelStep） |
| **节点模型动态加减签** | ✅ | ❌ | 🟡（Delta 等价） |
| 认领（角色/部门） | ✅ | ❌ | 🟡 |
| 唤醒（历史复活） | ✅ | ❌ | 🟡 |
| 暂存待审 | ✅ | ✅ | 🟡（弱） |
| 作废 | ✅ | ✅ | 🟡（弱） |
| 子流程 | ✅ | ❌ | ⭐（schema 校验 + 输出回传） |
| 审批人设置（8 种） | ✅ | 🟡 | ⭐（10 内置标签 + 自定义） |
| 审批人=提交人处理 | ✅ | ❌ | 🟡 |
| 条件表达式 | SpEL | 8 运算符 + SpEL | ⭐（XLang）但缺策略路由 |
| 监听器类型数 | 2 | 5 + Global | ⭐（30+ 事件） |
| 全局事件订阅 | ❌ | ❌ | ⭐ |
| **AI 审批** | ✅ | ❌ | ❌ |
| **内置调度器** | ✅ | ❌ | ❌（外部 nop-job） |
| **穿越时空（补审）** | ✅ | ❌ | ❌ |
| 表单（内置） | ❌ | ✅ | 🟡（外挂 + 字段权限 ⭐） |
| 流程图（状态着色） | ❌ | ⭐ | 🟡（AMIS 设计器） |
| 多 ORM 解耦 | ❌ | ⭐ | ❌（绑定 Nop Dao） |
| 业务实体绑定 | 🟡 | 🟡 | ⭐ |
| 信号机制 | ❌ | ❌ | ⭐ |
| 错误重试 | ❌ | ❌ | ⭐ |

---

## Conclusion

### 核心结论

nop-wf 在**引擎核心能力**（节点/路由/会签/驳回/转办/子流程/审批人解析/监听器/扩展点）上 **基本覆盖** FlowLong 与 Warm-Flow 的功能集，且在 XDSL 可逆计算、DAG 校验、状态机严谨性、信号机制、业务实体绑定、错误重试、全局事件订阅、动态汇聚分组等方面 **明显领先**。

但 nop-wf 在以下 **5 个维度存在明显缺口或弱化**，要真正"覆盖"这两个开源项目需补齐：

### 缺口清单（按优先级）

| 优先级 | 缺口 | 来源 | 影响 | 补齐建议 |
|--------|------|------|------|---------|
| **P0** | **AI 审批集成**（AiConfig/AiResponse/AiStatus/FlowAiHandler/置信度阈值/降级策略/异步回调/AI 路由） | FlowLong | AI 时代核心差异化能力 | 在 `.xwf` 节点扩展 `callAi/aiConfig`，新增 `IWfAiHandler` SPI + `WfAiResponse` 模型 + 2 个步骤状态（aiProcessing/aiManualReview） |
| **P0** | **内置调度器**（cron 扫描 + 分布式锁 + 超时自动通过/拒绝 + 提醒次数限制） | FlowLong | 开箱即用体验差 | 与 nop-job 集成提供 `WfScheduler` bean，扫描 `dueTime` + `remindTime`，调用 `invokeAction(dueAction)` |
| **P1** | **离职转办（批量改办理人）** | FlowLong | 企业场景高频需求 | 在 `WorkflowService` 新增 `transferActors(fromUserId, toUserId, wfIds?)` 批量 API |
| **P1** | **票签表达式策略可插拔**（按前缀路由：default@@/spel@@/passCount=N/rejectCount=N） | Warm-Flow | 票签规则灵活性不足 | `<check-exec-group-complete>` XPL 已存在，补示例文档 + 内置策略标签库 |
| **P1** | **节点模型动态加减签**（运行时插/删节点） | FlowLong | 动态审批场景 | 通过 `transitTo` + 临时步骤等价，补封装 API `addDynamicStep/removeDynamicStep` |
| **P2** | **穿越时空（补审）**（自定义当前时间） | FlowLong | 特殊合规场景 | 新增 `IWfTimeProvider` SPI（类似 FlowCreateTimeHandler） |
| **P2** | **流程图状态快照**（def_json 存实例 + 流转自动着色 + PathWayData 回放） | Warm-Flow | 流程图体验 | `<diagram>` 字段已有，补 `ChartService` 等价的快照更新逻辑 |
| **P2** | **内置表单**（form_content JSON + FormService） | Warm-Flow | 轻量场景 | 依赖 Nop 平台 page/yform 已足够，可不补 |
| **P3** | **审批人=提交人处理 4 种内置策略**（自审/跳过/转上级/转部门负责人） | FlowLong | 配置便利性 | 补 4 个内置 wf-actor 标签 + `<when>` 模板 |
| **P3** | **作废/暂存显式状态枚举** | FlowLong/Warm-Flow | 语义清晰度 | 新增步骤状态 `SUSPENDED_AS_DRAFT` + 工作流状态 `NULLIFIED` |
| **P3** | **多 ORM 解耦** | Warm-Flow | 生态受限 | 架构决策，**不建议补**（绑定 Nop Dao 是设计选择） |

### 被否决的方案

- **"把 nop-wf 改成多 ORM 解耦"**：否决。原因：nop-wf 的价值在于与 Nop 平台深度集成（权限/调度/ORM/Web 一站式），多 ORM 解耦会破坏集成度，且 Nop Dao 本身已支持所有主流+国产数据库。
- **"把 AI 审批做成 FlowLong 那样绑定具体大模型"**：否决。原因：应保留 SPI 抽象（`IWfAiHandler`），具体大模型实现由 nop-ai 模块提供。

### 后续工作

本分析已 **resolved**，缺口处理决策已沉淀到设计文档 `ai-dev/design/nop-wf/extensions-design.md`。后续执行工作指向：
- `ai-dev/plans/` — `nop-wf-scheduler` 调度器、`transferActors` 离职转办、`wf-vote.xlib` 票签策略、`nop-wf-ai` AI 审批的实施计划（按 `extensions-design.md` §十二 优先级拆分）

---

## Open Questions

- [ ] nop-wf 是否需要支持"节点模型动态加减签"（运行时改 .xwf），还是坚持"模型即代码 + Delta 定制"哲学？
- [ ] AI 审批的 `IWfAiHandler` SPI 是否应该放在 nop-wf-core，还是放在独立的 `nop-wf-ai` 模块（依赖 nop-ai）？
- [ ] 内置调度器是否应该作为 nop-wf 的默认行为，还是保持"外部 nop-job 集成"的可选模式？
- [ ] 流程图状态快照（def_json 模式）是否值得引入，还是继续依赖 step.status + AMIS 渲染？

---

## References

### FlowLong 源码证据
- 引擎：`c:/can/sources/flowlong/flowlong-core/src/main/java/com/aizuda/bpm/engine/FlowLongEngine.java:36-412`
- 实现：`.../engine/impl/FlowLongEngineImpl.java:36-550`
- 上下文：`.../engine/core/FlowLongContext.java:35-236`
- 执行总线：`.../engine/core/Execution.java:41-383`
- AI 处理器：`.../engine/handler/FlowAiHandler.java:26-124`
- AI 配置：`.../engine/model/AiConfig.java:23-105`、`AiResponse.java:30-252`
- 调度器：`.../engine/FlowLongScheduler.java:39-155`
- 任务类型：`.../engine/core/enums/TaskType.java:24-163`（32 取值）
- 办理类型：`.../engine/core/enums/PerformType.java:23-79`
- 驳回策略：`.../engine/core/enums/RejectStrategy.java:24-74`
- 实例状态：`.../engine/core/enums/InstanceState.java:23-94`（含 aiProcessing/aiManualReview）
- DDL：`c:/can/sources/flowlong/db/flowlong-mysql.sql`（7 表）

### Warm-Flow 源码证据
- 引擎注册中心：`c:/can/sources/warm-flow/warm-flow-core/src/main/java/org/dromara/warm/flow/core/FlowEngine.java:41-170`
- 框架桥接：`.../core/invoker/FrameInvoker.java:27-69`
- 流转核心：`.../core/service/impl/TaskServiceImpl.java:166-235`
- 协作类型：`.../core/enums/CooperateType.java:39-56`
- 表达式策略：`.../core/strategy/ExpressionStrategy.java:25-60`
- 条件策略：`.../core/condition/AbstractConditionStrategy.java:31-72`
- 监听器：`.../core/listener/Listener.java:25-58`、`GlobalListener.java:26-80`
- 流程图：`.../core/service/impl/ChartServiceImpl.java:46-166`
- 表单：`.../core/entity/Form.java:26-111`
- DDL：`c:/can/sources/warm-flow/sql/mysql/warm-flow-all.sql`（8 表）

### nop-wf 源码证据
- 核心接口：`nop-wf-core/src/main/java/io/nop/wf/core/IWorkflow.java:25-198`、`IWorkflowManager.java:21-45`、`IWorkflowStep.java:25-274`
- 引擎实现：`nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:117-1806`
- 运行时：`.../engine/WfRuntime.java:40-391`
- 会签算法：`.../engine/ExecGroupSupport.java:10-168`
- Actor 解析：`.../engine/WfActorAssignSupport.java:51-290`
- trampoline 队列：`.../impl/WorkflowImpl.java:44-98`
- 模型分析器：`.../model/analyze/WfModelAnalyzer.java:46-265`
- XDef Schema：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef:1-296`
- ORM 模型：`nop-wf/model/nop-wf.orm.xml`（11 实体）
- 内置 actor 标签：`nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/wf-actor.xlib:1-129`
- 15 个示例：`nop-wf-service/src/main/resources/_vfs/nop/wf/examples/`
- 配置文档：`docs-for-ai/02-core-guides/workflow-configuration.md`
- 实操指南：`docs-for-ai/03-runbooks/build-approval-flow.md`
