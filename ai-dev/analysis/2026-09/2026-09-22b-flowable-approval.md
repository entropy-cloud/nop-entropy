# Flowable 源码级调研：支撑审批/四眼场景的引擎机制

> Status: resolved
> Date: 2026-09-22
> Scope: maker-checker 通用机制设计参考（任务分配/claim 并发/会签/驳回回环/SoD 边界）
> Conclusion: Flowable 引擎**没有内置四眼/SoD 约束**（全仓无命中），只提供 `flowable:initiator` 变量注入链路；其价值在工程语义层：assignee 单列 + candidate IdentityLink 双轨分配模型、claim 幂等 + 乐观锁并发语义、多实例 + 表达式 completionCondition 表达投票会签、`ChangeActivityStateBuilder` 把"驳回回跳"做成一等 API。对本平台 nop-wf：分配双轨、认领异常语义、驳回一等 API、投票谓词设计均可直接对照借鉴；SoD 必须在 nop-wf 自己的 complete/assign 校验点实现。

## Context

- 上位调研：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md` 将 BPM 工作流（Camunda/Flowable）列为"审批对象 = 流程任务"的形态，并建议本平台复杂审批（会签/多级/条件路由）走 nop-wf（L2），轻量 maker-checker（L1）不重复造工作流。
- 本文在源码级摸清 Flowable 支撑审批场景的机制边界，为 nop-wf 的 checker 侧语义（认领、会签、驳回、SoD 校验点）提供对照。Flowable 与 Activiti/Camunda 7 同源，结论对三者的设计取舍有代表性。
- 源码：本地 `~/sources/flowable-engine`（git clone --depth 1，Apache-2.0）。文中路径均为仓库相对路径。

## 调研目标

1. 任务分配模型与存储；2. claim/complete 的并发语义；3. 引擎是否有四眼/SoD 内置约束；4. 会签/多实例与投票条件；5. 驳回回环的惯用做法；6. 任务状态机与历史审计；7. 委托机制；8. 变量/businessKey 与业务侧感知钩子；9. 待办聚合查询。

## 调研结果

### 1. 任务分配：assignee 单列 + candidate IdentityLink 双轨

- IdentityLink 类型全集仅 6 值：`assignee/candidate/owner/starter/participant/reactivator`（`modules/flowable-identitylink-service-api/src/main/java/org/flowable/identitylink/api/IdentityLinkType.java`）；候选"人 vs 组"靠 `USER_ID_`/`GROUP_ID_` 哪个非空区分，不设独立类型。
- 一张 `ACT_RU_IDENTITYLINK` 表同时服务任务/流程实例/流程定义（`IdentityLinkEntity` 带 `taskId/processInstanceId/processDefId/scopeId/scopeType`）。
- **assignee 冗余存在任务行上**（`modules/flowable-task-service/.../entity/TaskEntityImpl.java`：`assignee/owner/delegationState/claimTime/claimedBy/originalAssignee/state/...`），候选池只在 identitylink——`flowable-engine/.../behavior/UserTaskActivityBehavior.java#handleAssignments`：assignee → `TaskHelper.changeTaskAssignee`（写列 + assignee 链接），candidateUsers/candidateGroups → 只写 IdentityLink。
- **claim 时删除 candidate 链接**：`TaskHelper.changeTaskAssignee` → 认领后候选记录清空，"该谁办"由任务行单列回答。候选查询 SQL（`modules/flowable-task-service/src/main/resources/org/flowable/task/service/db/mapping/entity/Task.xml` 约 L867-930）用 exists 子查询；"candidate 或 assignee" 语义 = `taskCandidateOrAssigned()`（`TaskQueryImpl.java` L638 + Task.xml 约 L1094-1130）。

### 2. claim/complete 并发语义

- `ClaimTaskCmd`：任务已被他人认领 → `FlowableTaskAlreadyClaimedException`（`modules/flowable-engine-common-api/.../FlowableTaskAlreadyClaimedException.java`）；**同人重复 claim 静默成功（幂等）**；成功写 `claimTime/claimedBy`、置 `task.setState(CLAIMED)`，回调 `UserTaskStateInterceptor.handleClaim` 扩展点。
- 底层并发兜底是**乐观锁**：`modules/flowable-engine-common/.../db/DbSqlSession.java`（L622/690）REV_ 不匹配抛 `FlowableOptimisticLockingException`；无 select-for-update 抢占。
- **complete 未认领任务合法**：`TaskHelper.completeTask` 不校验 assignee，只校验 PENDING 委托必须先 resolve + `NeedsActiveTaskCmd` 的存在/未删除/未挂起检查——"是否必须先认领"交给上层应用决定。
- 转办 `setAssignee` 与 claim 的区别：不设 claimTime/state，只改 assignee + 发 TASK_ASSIGNED 事件（`TaskHelper.java` L258）。

### 3. 四眼/SoD：引擎零内置

- 全仓库（modules + docs）搜 `four-eyes/fourEye/separation of duties/maker-checker/sod` 均无命中。
- 但提供 initiator 注入链路供应用层自实现：BPMN `flowable:initiator` 属性解析（`flowable-engine/.../bpmn/parser/BpmnParse.java` `PROPERTYNAME_INITIATOR_VARIABLE_NAME`）→ 启动时读取（`flowable-engine/.../util/ProcessInstanceHelper.java` L187-201）→ 落为流程变量 `setVariable(initiatorVariableName, authenticatedUserId)`（`flowable-engine/.../persistence/entity/ExecutionEntityManagerImpl.java` L298-299）。
- 对应应用层扩展点：`UserTaskStateInterceptor`、`IdentityLinkInterceptor`（`ProcessEngineConfigurationImpl` 可注入）。

### 4. 会签/多实例与投票

- 框架：`flowable-engine/.../behavior/MultiInstanceActivityBehavior.java`，计数常量 `nrOfInstances/nrOfActiveInstances/nrOfCompletedInstances`（L103-105）维护于 MI 根执行局部变量。
- 串行 `SequentialMultiInstanceBehavior`（每完成一个 +1 后评估完成条件，L78-90）；并行 `ParallelMultiInstanceBehavior`（完成时评估，满足则**销毁剩余实例**，L231/270-300）。
- 完成条件：`completionConditionSatisfied`（L374-398）每实例结束时求布尔表达式，非布尔抛 `FlowableIllegalArgumentException`；支持 `DynamicBpmnService` 运行时改条件。
- **投票式会签标准写法**（官方文档）：`docs/docusaurus/docs/bpmn/ch07b-BPMN-Constructs.md`：`<completionCondition>${nrOfCompletedInstances/nrOfInstances >= 0.6}</completionCondition>`（过 60% 销毁其余）。
- 测试：`modules/flowable-engine/src/test/java/org/flowable/engine/test/bpmn/multiinstance/MultiInstanceTest.java`（串/并行、3/5 触发条件、按 collection 按人会签）。

### 5. 驳回回环：一等 API 而非流程图技巧

- `modules/flowable-engine/src/main/java/org/flowable/engine/runtime/ChangeActivityStateBuilder.java`：`moveActivityIdTo/moveActivityIdsToSingleActivityId/moveSingleActivityIdToActivityIds/moveActivityIdToParentActivityId` + 变量注入 + `changeState()`；入口 `RuntimeService.createChangeActivityStateBuilder()`，实现 `ChangeActivityStateCmd`。
- 回退到 maker = `moveActivityIdTo("approveTask", "draftTask")` + 携带变量；BPMN 图层面惯用 exclusive gateway 回边。
- 测试：`flowable-engine/src/test/java/org/flowable/engine/test/api/runtime/changestate/ChangeStateTest.java`（含 Gateways/MultiInstance 变体——多实例中途跳转）。

### 6. 任务状态机与历史

- 状态常量（`modules/flowable-task-service-api/.../Task.java` L25-29）：`created/claimed/inProgress/suspended/completed`；另有独立 `suspensionState` 与 `deleted` 布尔；完成即任务行删除转历史。
- 历史：`HistoricTaskInstance` extends `TaskInfo` + `deleteReason/startTime/endTime/completedBy/durationInMillis/workTimeInMillis`；变量快照经 `includeTaskLocalVariables/includeProcessVariables`；claim 变更留 identity link 评论。

### 7. 委托：PENDING/RESOLVED 二态回环

- `delegate`（`DelegateTaskCmd`）：置 PENDING，owner 为空则 `owner = 原assignee`，assignee 改为受托人；`resolve`（`ResolveTaskCmd`）：置 RESOLVED 并还 assignee 给 owner，owner 再 complete 才真正驱动流程；**PENDING 状态下 complete 被拒**（"A delegated task cannot be completed, but should be resolved instead."，`TaskHelper.completeTask` 首行）。与转办的区别：delegate 是有 owner 参与的"二审回环"。

### 8. 变量/businessKey/业务感知钩子

- 变量：`VariableInstanceEntity`（name/type/value/作用域 + 乐观锁版本），任务 complete 时变量默认升到 execution 级（`TaskHelper.completeTask` L87-96）。
- businessKey：`ExecutionEntityImpl` L211，启动时写入不参与流转，是"流程实例↔业务单据"的关联索引（`ProcessInstanceQueryImpl#processInstanceBusinessKey` L177-214）。
- 业务侧感知三钩子：JavaDelegate、TaskListener（create/assignment/complete/delete/update）、全局事件 `FlowableEngineEventType`（`TASK_CREATED/TASK_ASSIGNED/TASK_COMPLETED/ACTIVITY_*/MULTI_INSTANCE_ACTIVITY_COMPLETED[_WITH_CONDITION]/PROCESS_*`）。

### 9. 待办聚合

- `TaskQueryImpl` 组合 `taskCandidateOrAssigned(userId)` + `taskCandidateGroupIn`（组解析在查询前：`getGroupsForCandidateUser` 经 IDM `GroupQuery.groupMember()`）+ `processVariableValueEquals(...)`（L1374-1474）+ `or()`，编译为单条 SQL（exists 子查询 + 变量 join），可跨流程实例一次取回。测试：`TaskQueryTest.java`（L1751 起候选语义、L2667 起变量过滤）。

## 与当前项目的关系

- **可借鉴**：
  1. assignee 单列 + 候选池分离的双轨分配模型：nop-wf 的 execGroup 认领可对齐"认领后写 assignee 列 + 删候选记录"，查询与审计都简单；
  2. claim 语义三件套：同人幂等 + 他人已认领显式异常 + 乐观锁兜底——L1 checker 侧若引入认领，这是最低成本正确实现；
  3. "complete 不强制先认领"默认值得保留（后台代提交/系统代批场景需要）；
  4. 投票会签谓词设计：计数变量 + 表达式完成条件 + "满足后销毁剩余实例"正式语义，nop-wf vote-group 可对照设计 `completed/total >= ratio` 类谓词；
  5. 驳回做成引擎一等 API（activity 级跳转 + 变量注入），比每张流程图画回边可控——nop-wf 可提供等价"回退到指定步骤"API；
  6. delegate 的 PENDING/RESOLVED 二态回环 + "PENDING 不能 complete"的保护。
- **不可照搬**：
  1. SoD 不存在于引擎层——nop-wf 若要"办理人≠发起人"硬约束，必须在自己的任务分配/complete 校验点实现（Flowable 同样靠应用层，与 Syncope 结论一致，构成跨项目印证）；
  2. IdentityLink 的 processDefId/scopeId 等泛化字段对本场景冗余；组解析耦合内嵌 IDM——nop-wf 应查询前用自己的权限体系解析为用户/组 ID 集合再入 SQL；
  3. 任务完成即删行转历史的策略——L1 审批记录需要行保留（审计），不能照搬。

## Open Questions

- [ ] nop-wf 的 execGroup 认领（assignment selection="auto|single|multiple"）目前是否已有 assignee 落列 + 候选清理语义？需对照 `docs-for-ai/02-core-guides/workflow-configuration.md` 与引擎实现确认。
- [ ] L1 审批的"认领"是否需要？单级 maker-checker 通常免认领（approve 即办），quorum 场景才需要——倾向 L1 不引入认领以保持最小闭环。

## References

- 本地源码：`~/sources/flowable-engine`（clone 自 github.com/flowable/flowable-engine，--depth 1）
- 上位分析：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md`
- 同日姊妹篇：`ai-dev/analysis/2026-09/2026-09-22c-apache-syncope-approval.md`（Syncope 内嵌 Flowable 做实体审批）
- 关键源码文件：`modules/flowable-engine/src/main/java/org/flowable/engine/impl/bpmn/behavior/UserTaskActivityBehavior.java`、`modules/flowable-engine/src/main/java/org/flowable/engine/impl/util/TaskHelper.java`、`modules/flowable-engine/src/main/java/org/flowable/engine/impl/cmd/ClaimTaskCmd.java`、`modules/flowable-engine/src/main/java/org/flowable/engine/impl/bpmn/behavior/MultiInstanceActivityBehavior.java`、`modules/flowable-engine/src/main/java/org/flowable/engine/runtime/ChangeActivityStateBuilder.java`
