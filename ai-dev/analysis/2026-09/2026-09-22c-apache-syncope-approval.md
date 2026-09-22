# Apache Syncope 源码级调研：内嵌 Flowable 的实体生命周期审批

> Status: resolved
> Date: 2026-09-22
> Scope: maker-checker 通用机制设计参考（实体级审批/工作流桥接/审批等待态语义）
> Conclusion: Syncope 用"确定性 businessKey（流程 key:实体 ID）绑定实体与流程实例 + 实体 status 字段直接存当前节点名 + 对外传播（propagation）扣住到审批通过后 + reject 只丢弃变更不回滚"实现实体生命周期审批；全链路**没有 approver≠submitter 的代码级强制**（结构性靠 BPMN 条件保证），证明 SoD 硬校验必须自建。对本平台的核心启示：L1↔L2 桥接时实体↔流程用确定性 key 绑定即可、审批等待期间下游副作用扣住、approve 事务用 REQUIRES_NEW 与业务隔离。

## Context

- 上位调研：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md` 建议本平台 maker-checker 走"轻量内置（L1）+ nop-wf 复杂审批（L2）"两层方案，其中 L1→L2 桥接（轻量审批升级到工作流会签）需要参考"实体生命周期审批"的成熟实现。
- Syncope（开源身份治理系统）是把 Flowable 内嵌进业务域做实体审批的代表性 Java 项目，与 nop-wf `approval-support.xbiz`（实体级 submitForApproval/approve/reject）形态同构。
- 源码：本地 `~/sources/syncope`（git clone --depth 1，Apache-2.0）。文中路径均为仓库相对路径，Syncope 3.x 为准。

## 调研目标

1. 工作流引擎嵌入架构与默认审批流程的节点结构。
2. 审批任务与业务实体的绑定方式；等待审批期间实体的状态语义。
3. 审批动作 API 面、approve 后对外传播（propagation）的触发时机。
4. SoD 是否有代码级强制；拒绝路径语义；域模型与持久化策略；多级审批扩展点；审计与通知。

## 调研结果

### 1. 架构总览：Flowable 是可选扩展而非内核

- Flowable 以 `ext/flowable/` 扩展形式存在（6 个 Maven 模块：flowable-bpmn/logic/rest-api/rest-cxf/common-lib/client-console）；core 自带的 `syncope/core/workflow-java/` 是**无引擎的默认实现**——self 注册 + `requiresApproval()` + 配置项 `default.workflow.requires.approval` 时直接抛 `WorkflowException("This operation requires approval")`（`syncope/core/workflow-java/.../DefaultUserWorkflowAdapter.java` `throwApprovalRequired` L90-99；`requiresApproval()` 定义在 `common/idrepo/lib/.../request/UserCR.java` L221、`UserUR.java` L199：有 manager/资源/关系/成员/角色/账号之一即需审批）。
- 多域支持：`syncope/ext/flowable/flowable-bpmn/.../support/DomainProcessEngine.java` 内部持有 `Map<String, ProcessEngine>`，按 `AuthContextUtils.getDomain()` 路由。
- 适配器：`syncope/ext/flowable/flowable-bpmn/.../impl/FlowableUserWorkflowAdapter.java` 继承 core 的 `AbstractUserWorkflowAdapter`，同时实现 `WorkflowTaskManager`。
- 默认 BPMN 模板：`syncope/ext/flowable/flowable-bpmn/src/main/resources/userWorkflow.bpmn20.xml`，节点结构：`theStart` → `create`（serviceTask `${create}`）→ 网关 →（匿名自注册且需审批）`createApproval`（userTask，formKey="createApproval"）→（通过）→ `activate` → `active`（userTask 终态驻留）。

### 2. 实体绑定与等待态语义

- **businessKey = `流程定义key + ':' + userKey`**：`syncope/ext/flowable/flowable-bpmn/.../impl/FlowableRuntimeUtils.java` `getProcBusinessKey()`（L102-104）/`getWFProcInstID()`（L96-100 反查）。没有自建"流程实例表"。
- **等待审批时实体状态 = 当前 BPMN 节点名**：`FlowableRuntimeUtils.updateStatus()`（L132-139）把 task definitionKey 直接写进 `user.status`（如 "createApproval"/"rejected"/"active"）；是否 suspended 由独立 `enabled` 流程变量控制。
- 审批中再提交 update/delete：BPMN 有专门分支（`updateWhilePendingCreateApproval` 等）回到对应 approval userTask；`doDelete`（`FlowableUserWorkflowAdapter` L500-547）在流程未结束时不删实体，传播信息 `saveForFormSubmit` 暂存，流程结束才真删。
- 真正的实体创建发生在 BPMN service task 里（`syncope/ext/flowable/flowable-bpmn/.../task/Create.java` 调 dataBinder.create），adapter 只负责启动流程并回写 businessKey。

### 3. 审批 API 与 propagation 触发时机

- REST：`syncope/ext/flowable/rest-api/.../service/UserRequestService.java`（`@Path("flowable/userRequests")`）：`GET /forms`（待办）、`POST /forms/{taskId}/claim|unclaim`、`POST /forms`（submitForm=approve/reject）、`POST /start/{bpmnProcess}`、`DELETE /{executionId}`（撤回）。
- 实现链：`UserRequestServiceImpl`（rest-cxf）→ `UserRequestLogic`（logic）→ `syncope/ext/flowable/flowable-bpmn/.../impl/FlowableUserRequestHandler.java` `submitForm`（L635-755）：校验 `task.getAssignee().equals(authUser)`（L641-644）→ 写 `formSubmitter` 变量 → `formService.submitTaskFormData` → 流程沿网关走 approve/reject。
- **propagation 在审批通过后才执行**：`UserRequestLogic.submitForm`（L217-233）拿到 `UserWorkflowResult`（含 `propByRes`）后调 `propagationManager` + `taskExecutor.execute`。等待期间传播计划由 `FlowableRuntimeUtils.saveForFormSubmit()`（L166-208）**暂存为流程变量**（`propByResource/encryptedPwd` 等）。`submitForm` 内注释 "supports approval chains"（L720-728）：流程未结束就重新暂存给下一级。
- 事务边界：`FlowableUserRequestHandler` 标注 `@Transactional(propagation = REQUIRES_NEW)`——**审批提交与主业务事务隔离**。

### 4. SoD：无代码级强制

- 全链路无 approver≠submitter 显式校验。仅有：submitForm 校验"提交人必须是 assignee"；claimForm 校验"claim 人必须是 candidate/assignee"（L573-582）；admin 用户绕过全部检查（L472-474）。
- 结构性隔离靠 BPMN 条件：只有 `wfExecutor == 'anonymous'`（自注册）或本人自改才进审批分支；若某用户既是 candidate 又是 submitter，技术上可自批。

### 5. 拒绝路径：丢弃变更而非回滚

- 创建拒绝：`reject` scriptTask（`userWorkflow.bpmn20.xml` 配套 groovy L179-185）移除 `userTO/encryptedPwd/propByResource` 变量 → 实体滞留 status="rejected" 等人处理（`task=='delete'` 才真删）。
- 更新拒绝：`rejectUpdate`（L158-162）仅清 `propByResource`，变更内容（`userUR` 变量）被丢弃，实体保持原样，无版本/回滚机制。
- 拒绝原因 `rejectReason` 表单属性用完即移除，不持久化到域模型。

### 6. 域模型与持久化：不建自有审批表

- `UserRequest`/`UserRequestForm` 只是 TO（`syncope/ext/flowable/common-lib/.../to/`），无 JPA 实体、无专有表。
- 查询直接打 Flowable 表：待办用 TaskQuery（`ACT_RU_TASK`）；"用户请求"用原生 SQL 查 `ACT_RU_EXECUTION`（`BUSINESS_KEY_ NOT LIKE 'userWorkflow:%' AND BUSINESS_KEY_ LIKE '%:<userKey>'`，`FlowableUserRequestHandler.createProcessInstanceQuery` L109-124）；历史用 `ACT_HI_TASKINST/ACT_HI_DETAIL`。
- 审批组直接解析为 Syncope Group：`syncope/ext/flowable/flowable-bpmn/.../support/SyncopeIdmIdentityService.java`（Flowable candidateGroups → Syncope Group 桥接）。

### 7. 表单/变量与审批页

- BPMN 表单属性（`userWorkflow.bpmn20.xml` L36-42）：`username`（只读 expression）、`approveCreate`（boolean required）、`rejectReason`；审批页可见申请人、完整 `UserTO`（当前实体）与 `UserUR`（待审 diff）、assignee/到期时间（`getForm()` L320-330、L400-461）。
- 流程变量全集常量化在 `FlowableRuntimeUtils.java` L49-79（`wfExecutor/formSubmitter/userCR/userTO/userUR/enabled/task/propByResource/...`）。

### 8. 多级审批与通知/审计

- 默认单级；官方两级示范：`syncope/fit/core-reference/src/test/resources/directorGroupRequest.bpmn20.xml`（`firstApproval` candidateGroups="managingDirector" → `secondApproval` candidateGroups="root"），端到端测试 `UserRequestITCase.twoLevelsApproval()`。
- 通知两条通路：BPMN 内 `Notify` delegate（`task/Notify.java`）；通用 AOP `syncope/core/idrepo/logic/.../LogicInvocationHandler.java` 对所有 logic 方法产生 `[LOGIC]:[类]:[方法]:[SUCCESS]` 事件，通知与审计各自订阅。无默认"待审批提醒"，需管理员配置 Notification。
- 审计：`AuditManager`（`syncope/core/provisioning-api/`）→ `AuditEvent` 表（`core/persistence-jpa/.../entity/JPAAuditEvent.java`）；引擎历史 `ACT_HI_*` 保留"谁在何时批了什么值"。

## 与当前项目的关系

- **可借鉴**：
  1. 确定性 businessKey（`流程key:实体ID`）作为实体↔流程实例的唯一绑定，不建额外关联表——L1↔L2 桥接时 `nop_sys_checker_record` 与 nop-wf 流程实例的关联可用同法；
  2. 实体 status 字段直接存"当前节点名"，状态机即流程位置，无需"审批中"布尔；
  3. 对外副作用（传播/通知/同步）扣住到 approve 之后，且副作用计划作为流程上下文携带避免审批后重算——对应 maker-checker 的"重放的是批准过的意图"；
  4. reject = 丢弃变更 + 实体滞留待处理（rejected 状态）是简单可靠的缺省语义；
  5. 审批事务 REQUIRES_NEW 与业务事务隔离，防止引擎回滚连带业务不一致；
  6. 通知/审计用 AOP 统一事件（`[LOGIC]:[类]:[方法]:[结果]`）+ 订阅式挂接，不散落在审批代码里。
- **不可照搬**：
  1. SoD 无代码强制——本平台必须自建 `approver != maker` 硬校验（Syncope 反例恰好证明引擎不会替你做）；
  2. 无自有审批持久化实体——L1 需要独立的 `nop_sys_checker_record` 做查询/审计索引（Syncope 直接打 ACT_ 表的做法绑定引擎表结构，不利于替换引擎）；
  3. rejectReason 不持久化——审批记录应保留驳回原因（审计要求）。

## Open Questions

- [ ] L1 审批记录与 L2 流程实例并存时的状态权威归属：以 `nop_sys_checker_record.status` 为准还是以流程节点为准？
- [ ] nop-wf 流程变量暂存副作用计划（如通知计划）是否引入？还是重放时重新计算？

## References

- 本地源码：`~/sources/syncope`（clone 自 github.com/apache/syncope，--depth 1）
- 上位分析：`ai-dev/analysis/2026-09/2026-09-22-maker-checker-mechanism-research.md`
- 关键源码文件：`syncope/ext/flowable/flowable-bpmn/src/main/resources/userWorkflow.bpmn20.xml`、`syncope/ext/flowable/flowable-bpmn/src/main/java/org/apache/syncope/core/flowable/impl/FlowableRuntimeUtils.java`、`syncope/ext/flowable/flowable-bpmn/src/main/java/org/apache/syncope/core/flowable/impl/FlowableUserRequestHandler.java`、`syncope/ext/flowable/flowable-bpmn/src/main/java/org/apache/syncope/core/flowable/impl/FlowableUserWorkflowAdapter.java`
