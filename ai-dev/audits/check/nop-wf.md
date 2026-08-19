# nop-wf 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-wf（core/api/service/dao/scheduler/ai 等，重点 wf-core）
- 文件数: 286（src/main/java 含 `_gen` 生成代码；非生成代码约 204 个）
- 覆盖范围声明:
  - **全文深读**（wf-core）: WorkflowEngineImpl（1813 行全文）、WfRuntime、WorkflowImpl、WorkflowStepImpl、WorkflowManagerImpl、WorkflowCoordinatorImpl、DefaultWorkflowExecutor、ExecGroupSupport、WfActorAssignSupport、WfActorWithWeight、AbstractWorkflowStore、WorkflowStepRecordBean、WorkflowRecordBean（主体）、NopWfCoreConstants/_NopWfCoreConstants、WfModel、WfModelAnalyzer、ApprovalFlowHelper、WfModelParser、ResourceWorkflowModelStore、core/service/impl/WorkflowServiceImpl。
  - **全文阅读**（其他模块）: nop-wf-dao 的 DaoWorkflowStore、DaoWorkflowModelLoader、WorkflowDefinitionDO、DefaultWorkflowDOProvider、WfResourceNamespaceHandler；nop-wf-scheduler 的 WfTaskScanner（含其单测 TestWfTaskScanner 以核实语义）；nop-wf-service 的 WorkflowDesignerService、DaoWfActorResolver；nop-wf-ai 的 WfAiHelper；nop-wf-api 的 IWfActor、WfActorBean/IWfActor 局部。
  - **结构化扫描 + 定点验证**（grep 空 catch / new RuntimeException / printStackTrace / synchronized / static 可变集合 / 鉴权关键字后逐点 Read 核实）: 其余全部非生成文件。nop-wf-service/entity 下 13 个 BizModel 为 15-47 行生成式 CRUD 壳，逐个确认无自定义逻辑。WfGraphDocumentCodec（714 行）仅抽查异常处理与头部契约。
  - **不在范围**: `_gen/`、`_` 前缀生成文件、target/、测试代码（除为核实 scheduler 语义读了一个测试）、orm.xml 等 resource（仅用于核实乐观锁 versionProp 配置）。
  - 扫描结论: 主代码中无 `new RuntimeException`、无 `printStackTrace`、无 `synchronized`、无 `@Inject private` 字段注入（全部 setter 注入）、beans.xml 显式定义（D7 常规项未发现违背）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 8 |
| P3 | 4 |

## 发现列表

### [P0] transferActors 批量转办公开接口无任何鉴权，且目标用户未校验存在性

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java:176-210`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:849-859`
- **维度**: D5（审批人身份校验）
- **证据**:
```java
// WorkflowServiceImpl.transferActorsAsync —— @BizMutation 公开变更，无任何权限判断
List<? extends IWorkflowStepRecord> stepRecords = workflowStore.findActivatedStepsByOwner(
        request.getFromUserId(), request.getWfIds());
for (IWorkflowStepRecord stepRecord : stepRecords) {
    ...
    IWorkflowStep step = wf.getStepById(stepRecord.getStepId());
    step.changeOwnerId(request.getToUserId(), ctx);   // 直接改派

// WorkflowEngineImpl.changeOwner —— 无鉴权、无用户存在性校验
public void changeOwner(IWorkflowStepImplementor step, String ownerId, IServiceContext ctx) {
    WfRuntime wfRt = newWfRuntime(step, ctx);
    IWfActor owner = StringHelper.isEmpty(ownerId) ? null : resolveUser(ownerId);
    step.getRecord().setOwner(owner);                 // resolveUser 返回 null 时静默清空 owner
```
- **现状**: 对比同文件 `invokeActionAsync` 最终走 `WorkflowStepImpl.invokeAction` 的 `allowCallByUser` 身份校验，`transferActorsAsync` 直接调用 `changeOwnerId` → `engine.changeOwner`，全程无调用者身份/权限校验，也无 `checkManageAuth`。`nop-wf.api.xml` 中该服务方法未声明任何 auth。`toUserId` 非法（用户不存在）时 `resolveUser` 返回 null，owner 被静默清空（对比 `transferToActor` 路径使用 `requireUser` 校验）。
- **风险**: 任意登录用户可将任意用户（fromUserId 为请求参数）名下所有待审批任务改派给自己（或任意人），随后以新 owner 身份通过 `invokeAction` 完成审批——"未被指定的审批人完成审批"，直接破坏审批合规语义。这是审计范围内最直接的安全缺陷。
- **建议**: 在 `transferActorsAsync` 入口校验调用者应为工作流 manager 或 fromUserId 本人（或平台级 admin 角色）；`toUserId` 改用 `requireUser` 校验。
- **误报排除**: 已核对 api.xml（无 auth 声明）、beans 注册（`_api-impl.beans.xml` 确认该服务对外发布）、全模块 grep 无任何 `isUserInRole`/`BizAuth` 类检查；非测试代码、非生成代码，路径现实可达。

### [P1] 超时处理（dueAction）与身份校验冲突：定时触发必然失败并中止整个扫描任务

- **文件**: `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:86-101,127-131`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowStepImpl.java:227-243`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1053-1072`
- **维度**: D1/D8（超时语义与实现不匹配）
- **证据**:
```java
// WfTaskScanner —— 以固定身份 "wf-scheduler" 调用审批动作
try {
    FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
} catch (NopException e) {
    if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS.getErrorCode()
            .equals(e.getErrorCode())) { ... continue; }
    throw e;                     // 其余异常码直接抛出，中断整个 for 循环
}
// WorkflowStepImpl.invokeAction —— 无差别身份校验
if (!allowCallByUser(ctx))
    throw new NopException(ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER)...
```
- **现状**: `allowCallByUser` 只认 step 的 owner/actor/被委托人；`"wf-scheduler"`（且 `IWfActor.SYS_USER_ID = "0"`，两者不同）对普通 user/dept/role 步骤必然返回 false，抛 `ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER`。该错误码不在 scanner 的白名单内，会 `throw e` 中断本次全部到期任务的处理。
- **风险**: 配置了 `dueTimeExpr + dueAction` 的步骤（引擎在 `newStepForActor` 中明确支持）到期后超时动作永不执行（审批流卡死）；且第一个到期任务即令整个 job 失败，后续到期任务均不处理。job 已通过 `wf-due-task-scan.job.yaml` 实际接线生产可达。
- **建议**: scanner 对 `ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER` 增加处理（或对系统触发路径绕过 allowCallByUser 的显式通道）；同时将单个任务失败改为记录并 continue，避免整批中断。
- **误报排除**: 核对 `IWfActor.SYS_USER_ID="0"` 与 `"wf-scheduler"` 不等；`DaoWfActorResolver.resolveUser("wf-scheduler")` 查库返回 null（会更早失败）；`WfActorBean.containsUser` 对 user/dept/role 型 actor 不含该 id；唯一测试 `TestWfTaskScanner` 将 `WorkflowServiceSpi` 整体 mock，恰好掩盖了真实调用链上的身份校验，佐证而非排除本问题。

### [P1] notifySubFlowEnd 公开接口无鉴权、无来源校验，可伪造子流程结束推进父流程

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java:84-99`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1028-1050`
- **维度**: D5
- **证据**:
```java
// WorkflowServiceImpl.notifySubFlowEndAsync —— 请求参数即信任
return workflowExecutor.execute(parentWfRef, ctx, parentWf -> {
    IWorkflowStep parentStep = parentWf.getStepById(request.getParentWfStepId());
    parentStep.notifySubFlowEnd(request.getStatus(), request.getResults(), ctx);

// WorkflowEngineImpl.notifySubFlowEnd —— 无校验子流程确实结束
step.getRecord().setSubWfResultStatus(status);
step.triggerWaiting(args, ctx);      // 激活子流程步骤
step.triggerTransition(args, ctx);   // 推进迁移
```
- **现状**: 该 `@BizMutation` 只要 parentWfId + stepId 即可调用；引擎侧不校验调用来源、不比对 `step.getRecord().getSubWfId()` 与实际结束的子流程，`status/results` 完全由请求方提供。
- **风险**: 任意登录用户可对任意含子流程步骤的工作流伪造"子流程已结束（含返回变量）"，激活并推进该步骤——跳过实际子流程审批（跳步/未审先过）。
- **建议**: 校验请求对应的子流程实例状态（如要求携带 subWfRef 并核实其已结束、状态一致），并对调用方做服务级鉴权。
- **误报排除**: 已核对引擎与 service 两层均无上述校验；api.xml 无 auth 声明；属主流程正常依赖的公开 mutation。

### [P1] kill/suspend/resume/signalWf 依赖模型级 checkManageAuth XPL，缺省配置下为无操作（默认开放）

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:649-651,1596-1598,716-733`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java:115-174`
- **维度**: D5
- **证据**:
```java
void checkManageAuth(WfRuntime wfRt) {
    runXpl(wfRt.getWfModel().getCheckManageAuth(), wfRt);   // 模型未配置 => null => 无操作
}
private void checkActionAuth(WfModel wfModel, WfRuntime wfRt) {
    runXpl(wfModel.getCheckActionAuth(), wfRt);             // 同上
}
// turnSignalOn/turnSignalOff 则连 checkManageAuth 都没有调用
```
- **现状**: `kill/suspend/resume/remove` 仅靠流程模型里的 `checkManageAuth` XPL 把关；`_WfModel` 中该字段仅由模型 XML 设置，无代码层默认值（全仓库 grep 无 `setCheckManageAuth` 调用）。`signalWf`（开关信号，直接影响步骤激活与 action 可用性）连模型级检查都没有。对比 invokeAction 有硬编码的 `allowCallByUser`，管理类操作是"默认不设防"。
- **风险**: 未配置 `checkManageAuth` 的流程（缺省情况）可被任意登录用户 kill（审批流被任意中止）、suspend、resume、翻转信号。
- **建议**: 提供缺省的 checkManageAuth 实现（至少校验 caller == manager 或 starter），或在 service 层增加角色门槛；signalWf 纳入 checkManageAuth。
- **误报排除**: 已确认引擎调用点仅 4 处 checkManageAuth（suspend/resume/remove/kill），turnSignalOn/Off 无；无任何代码为模型注入默认 auth XPL。

### [P1] 会签（vote-group）权重统计使用 `step.isExcludeInExecGroup()` 而非 `member.`，排除逻辑完全失效

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/ExecGroupSupport.java:33-70,84-129`
- **维度**: D1（会签计数边界）
- **证据**:
```java
private static boolean isVoteGroupComplete(IWorkflowStepImplementor step) {
    List<? extends IWorkflowStep> steps = step.getStepsInSameExecGroup(true, true);
    for (IWorkflowStep member : steps) {
        if (step.isExcludeInExecGroup())   // 循环变量是 member，却判断当前 step —— 永为 false
            continue;
        Integer weight = member.getRecord().getVoteWeight();
        ...
        totalWeight += weight;
// isVoteGroupReject 第 89-91 行同样的 `step.isExcludeInExecGroup()`
```
- **现状**: `isExcludeInExecGroup()` 定义为 `status >= WF_STEP_STATUS_CANCELLED(110)`（IWorkflowStep.java:108-110），是**成员级**属性（用于把已取消/已转交的成员排除出计票）。两处循环判断的都是当前触发 step（判断时状态为 ACTIVATED=30，恒 false），成员级排除永不生效。而 `transferToActor` 会把新步骤放入同一 execGroup（WorkflowEngineImpl.java:893-895），旧步骤状态 TRANSFERRED(120)、新步骤 active，二者同时留在组内。
- **风险**: 已转交/已取消成员的权重仍被计入 totalWeight（转办场景下同一人新旧实例双重计数），`passPercent = completeWeight/totalWeight` 分母虚高，会签通过阈值判定错误（该通过的不通过 → 流程卡滞）；reject 判定的 `totalWeight - rejectWeight < passWeight` 同样失真，且被转交的旧实例会被计入 rejectWeight（其状态非 completed/activated/waiting）。
- **建议**: 两处改为 `member.isExcludeInExecGroup()`。
- **误报排除**: 循环体内注释明确以 member 语义书写（"member == step 为当前正在执行 agree 的步骤"），变量误用确凿；已核对状态常量与 transferToActor 的同组复用逻辑。

### [P1] resolveDynamicActor 自赋值丢失 deptId，动态审批人的部门限定失效

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WfActorAssignSupport.java:134-141`
- **维度**: D1
- **证据**:
```java
WfAssignmentActorModel actorModel = new WfAssignmentActorModel();
actorModel.setActorId(actorAndOwner.getActorId());
actorModel.setActorType(actorAndOwner.getActorType());
actorModel.setDeptId(actorModel.getDeptId());     // 自赋值！应为 actorAndOwner.getActorDeptId()
actorModel.setExtProps(actorAndOwner.getAttrs());
List<IWfActor> actors = getDynamicActors(actorModel, wfRt);
```
- **现状**: 构造 `WfAssignmentActorModel` 时 deptId 取自自身（恒 null），调用方传入的 `actorDeptId` 被丢弃（`WfActorAndOwner.getActorDeptId()` 在同函数 148/151 行均有使用，证明字段存在且语义明确）。
- **风险**: 通过 `transferToActor`/`addActor` 指定 `wf-actor:*` 动态审批人并附带部门限定时，动态 actor 标签库（wf-actor.xlib）拿到 deptId=null，可能解析到跨部门的角色/分组审批人——审批人指派错误。
- **建议**: 改为 `actorModel.setDeptId(actorAndOwner.getActorDeptId())`。
- **误报排除**: 新建对象的 getDeptId() 必为 null，自赋值无任何效果；相邻行同类字段均取自 actorAndOwner，笔误确凿。

### [P2] initArgs 仅在 `schema != null && value != null` 时才将参数写入求值上下文，无 schema 的 action 参数静默丢失

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:547-561`
- **维度**: D1/D8（契约漂移）
- **证据**:
```java
Object value = entry.getValue();
ISchema schema = argModel.getSchema();
if (schema != null && value != null) {
    ...
    wfRt.setValue(name, value);        // 位于 guard 内部
}
// 随后 mandatory/persist 检查使用 args.get(name)，但求值作用域中没有该变量
```
- **现状**: wf.xdef 中 `<arg>` 的 `<schema>` 子元素为可选（另有独立的 `type` 属性，`_WfArgVarModel` 中 `type` 与 `schema` 是两个字段）。参数通过了"已知参数名"校验、mandatory 校验、甚至 persist 落库，但只要 arg 未声明 `<schema>`（或传入值为 null），该变量就不会进入 `wfRt` 求值作用域，action 的 source XPL 引用它时为 undefined。
- **风险**: 声明式契约（arg 可无 schema）与运行时行为不匹配：参数被接受却不生效，模型调试困难；type 属性声明的类型转换也被跳过。
- **建议**: 将 `wfRt.setValue(name, value)` 移出 guard（schema 仅用于校验/转换分支）；或强制要求 arg 必须有 schema 并在解析期报错。
- **误报排除**: 已核对 xdef（schema 非强制）与 `_WfArgVarModel`（type/schema 分离）；invokeAction 路径参数仅经此方法进入作用域，无其他注入通道。

### [P2] `EVENT_AFTER_END = "before-end"` 常量复制错误，after-end 监听器永不触发

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/NopWfCoreConstants.java:76-77`
- **维度**: D8/D1
- **证据**:
```java
String EVENT_BEFORE_END = "before-end";
String EVENT_AFTER_END = "before-end";   // 复制粘贴错误，应为 "after-end"
```
- **现状**: `doEndWorkflow` 结尾 `triggerEvent(EVENT_AFTER_END)` 实际再次触发 `before-end`；匹配 `after-end` 的 listener 永远不会执行，匹配 `before-end` 的 listener 在同一流程结束时被执行两次。
- **风险**: 依赖 after-end 做后续业务（通知、归档、状态回写）的流程模型静默失效；before-end listener 双触发可能产生重复副作用。
- **建议**: 改为 `"after-end"`；全局 grep 事件名字符串确认无下游按错误值适配。
- **误报排除**: 常量定义与使用点（WorkflowEngineImpl.doEndWorkflow:1499）均已核对，无其他地方定义同名事件。

### [P2] suspend/kill/transitTo 等路径将未校验的客户端 args 直接注入求值作用域，可覆盖内置变量

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:672-676`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WfRuntime.java:86-92`
- **维度**: D5（流程变量注入）
- **证据**:
```java
private void initArgs(WfRuntime wfRt, Map<String, Object> args) {
    if (args != null) {
        wfRt.getEvalScope().setLocalValues(args);   // 无白名单校验
    }
}
// WfRuntime 构造时在同一 scope 设置了 wf / wfRt / wfVars 内置变量，此处可覆盖
```
- **现状**: 与 action 参数走 `initArgs(argsModel,...)` 的白名单校验不同，`suspend/resume/remove/kill/transitTo/exitStep/triggerTransition/triggerWaiting/notifySubFlowEnd` 的 args 直接 `setLocalValues` 写入 WfRuntime 作用域。客户端可通过 `WfCommandRequestBean.args` 传入键 `wf`/`wfRt`/`wfVars` 覆盖内置变量，随后 `checkManageAuth`、listener、source XPL 读到的是攻击者控制的对象。
- **风险**: 求值上下文污染：模型 XPL 中基于 `wf`/`wfRt` 的权限判断或取值可被诱导（类型混淆/绕过判断的潜在注入面）。
- **建议**: 该重载改为过滤保留内置变量名，或统一走白名单校验版本。
- **误报排除**: 两个 initArgs 重载的实现与全部调用点已核对；WfRuntime 构造与 initArgs 在同一子 scope 上操作，覆盖关系成立。

### [P2] WorkflowDesignerService.saveDocument 无权限校验，未发布流程定义可被任意登录用户改写

- **文件**: `nop-wf/nop-wf-service/src/main/java/io/nop/wf/service/designer/WorkflowDesignerService.java:114-154`
- **维度**: D5
- **证据**:
```java
@BizMutation("saveDocument")
public Map<String, Object> saveDocument(@Name("wfDefId") String wfDefId, @Name("doc") String docJson,
                                        IServiceContext context) {
    NopWfDefinition entity = requireDefinition(wfDefId);
    checkNotPublished(entity);          // 仅挡已发布，无调用者权限判断
    ...
    entity.setModelText(workflow.xml());
    definitionDao().updateEntity(entity);
```
- **现状**: 除 `checkNotPublished` 外无任何鉴权；api 层也未声明 auth。虽然保存前经引擎加载路径校验（DAG 等合法），但内容本身不受控。
- **风险**: 任意登录用户可改写未发布流程定义的 modelText（审批人、网关条件、action source 等），待发布后生效——间接篡改审批语义。
- **建议**: 增加设计器操作角色校验（如流程定义维护权限）。
- **误报排除**: 已通读该类全部代码与 api 注册路径，无其他拦截。

### [P2] 自动迁移推进 `while (wf.runAutoTransitions(ctx)) ;` 无迭代/深度上限

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/DefaultWorkflowExecutor.java:44`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/support/ApprovalFlowHelper.java:14-16`
- **维度**: D6（递归推进无深度限制）
- **证据**:
```java
T ret = task.apply(wf);
// 触发步骤的自动转换
while (wf.runAutoTransitions(ctx)) ;
```
- **现状**: 自动迁移循环无上限。`WfModelAnalyzer` 默认拒绝环路，但 `allowStepLoop=true` 的模型允许回退边；若自动迁移条件在回退环上恒真（条件表达式写错或依赖变量未按预期变化），每轮都会创建新的步骤实例并写库，循环永不退出。
- **风险**: 单请求线程死循环 + 步骤记录无限膨胀（数据库资源耗尽），且事务长期不提交。
- **建议**: 增加最大迭代次数（如步骤数×常数）并在超限时抛出明确错误。
- **误报排除**: 两处调用点均无保护；runStepAutoTransition 每轮 `newSteps` 落库已核实（transitionTo → newSteps）。

### [P2] 并发审批下 exec-group 完成判定基于请求内快照，无事后补偿，可能产生"无活动步骤且未结束"的僵尸流程

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowManagerImpl.java:79-88`；`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1207-1212,1458-1475`
- **维度**: D3
- **证据**:
```java
// WorkflowManagerImpl.getWorkflow —— 每次请求新建 WorkflowImpl，无跨请求互斥
IWorkflowRecord wfRecord = workflowStore.getWfRecord(null, null, wfId);
return new WorkflowImpl(workflowEngine, workflowStore, workflowCoordinator, wfModel, wfRecord);

// doInvokeAction —— 组完成判定读取当前会话内步骤集合
boolean groupComplete = isExecGroupComplete(step, wfRt);   // getStepsInSameExecGroup 读 store
```
- **现状**: 同一步骤记录有 ORM 乐观锁（`nop_wf_step_instance` 配置 `versionProp="version"`）可挡住同记录并发写；但会签组"最后一个成员完成即触发迁移"的判定读取的是本请求会话内快照。两个组内成员在两个请求中并发 agree，各自快照中对方步骤仍是激活态 → 双方均判"组未完成"→ 均不触发迁移 → 双方 checkEnd 时对方未提交、`isAllStepsHistory=false` → 双双提交后流程再无任何触发点。
- **风险**: 会签流程停留在"无活动步骤且未结束"的僵尸状态（审批流卡死），直到外部再次触碰该流程（如 signal、再次 action）。
- **建议**: doInvokeAction 提交后（或乐观锁冲突重试时）补一次 runAutoTransitions/checkEnd；或对 wfId 加短暂分布式锁串行化推进。
- **误报排除**: 代码路径核实无误（无任何提交后复查逻辑）；发生需两请求精确交错，故定 P2 而非 P1；ORM 乐观锁存在性已从 orm.xml 核实（仅保护同记录写冲突，不解决快照判定问题）。

### [P2] getNextJoinStepRecord 声明了 actor 参数但实现完全忽略

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/AbstractWorkflowStore.java:183-197`；调用点 `WorkflowEngineImpl.java:335`
- **维度**: D8
- **证据**:
```java
public IWorkflowStepRecord getNextJoinStepRecord(IWorkflowStepRecord stepRecord, String joinGroup,
                                                 String stepName, IWfActor actor) {
    for (IWorkflowStepRecord prev : getSteps(wfRecord)) {
        ...
        if (prev.getStepName().equals(stepName)) {
            if (Objects.equals(joinGroup, prev.getJoinGroup()))
                return prev;          // 从未使用 actor
        }
    }
    return null;
}
```
- **现状**: 唯一实现（含 DaoWorkflowStore 继承的该实现）忽略 actor；`newStepForActor` 传入 actor 意图按 actor 复用 join 步骤实例，实际总是复用第一个同名同组非历史实例并改写其 execGroup/execOrder/actorModelId。
- **风险**: 多 actor 进入同一 join 步骤时，后到 actor"合并"进先到 actor 的实例（成员信息被覆盖），与接口签名承诺的按 actor 匹配语义不符；多实例会签 join 场景参与者计数可能少算。
- **建议**: 实现按 `actor.isActor(...)` 匹配，或删除该参数并注明单实例 join 语义。
- **误报排除**: 全仓库仅此一处实现与一处调用；接口无文档豁免说明。

### [P2] WfAiHelper.decide：低置信度 AI 结果仅当 onLowConfidence=manual 才转人工，否则照常生效；onError=suspend 时吞异常无日志

- **文件**: `nop-wf/nop-wf-ai/src/main/java/io/nop/wf/ai/WfAiHelper.java:19-51`
- **维度**: D5/D8/D4
- **证据**:
```java
if (confidence.doubleValue() < threshold && "manual".equals(onLowConfidence)) {
    step.changeOwnerId("manual-review", wfRt.getSvcCtx());
    return result;
}
...
if ("PASS".equals(decision)) {
    wfRt.getCurrentStep().getRecord().setAppState("agree");      // AI 直接置 agree 状态
} else if ("REJECT".equals(decision)) {
    wfRt.getCurrentStep().getRecord().setAppState("disagree");
}
...
} catch (RuntimeException e) {
    if ("suspend".equals(onError)) {
        wfRt.getWf().suspend(null, wfRt.getSvcCtx());            // 原始异常未记录、未传播
        return null;
```
- **现状**: ① 置信度低于阈值时，只有 `onLowConfidence` 恰为 `"manual"` 才转人工；为 null 或其他值时低置信度 AI 决策仍然写入 `appState=agree/disagree`（该状态驱动 `runStepAutoTransition` 的 onAppStates 迁移，即 AI 判定直接决定审批走向）。② AI 调用异常且 `onError=suspend` 时吞掉异常，无任何日志。
- **风险**: 配置遗漏（未写 onLowConfidence）时低置信度自动审批静默通过（合规风险）；异常被吞后难以排查流程为何挂起。
- **建议**: 低置信度缺省走 manual；catch 分支至少 LOG.warn 原始异常。
- **误报排除**: 通读该类全文确认无其他置信度兜底；appState 驱动迁移的关系在 WorkflowEngineImpl.runStepAutoTransition:808-815 核实。

### [P3] WorkflowStepImpl.compareTo 在步骤名不同时返回 0，违反 Comparable 契约

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowStepImpl.java:72-82`
- **维度**: D1（排序）/D8
- **证据**:
```java
cmp = getStepName().compareTo(o.getStepName());
if (cmp != 0)
    return 0;                    // 计算了 cmp 却返回 0，疑似应为 return cmp
return record.getStepId().compareTo(o.getRecord().getStepId());
```
- **现状**: 同一创建时刻、不同步骤名的两个步骤比较结果为 0（"相等"），但 `equals` 为对象同一性；同为正序/逆序比较也返回 0，进入 TreeMap/TreeSet 或需全序的算法时行为不可预期（`Collections.sort` 为稳定排序，当前 `getStepsByRecords` 用 `Collections.sort` 尚未爆发）。
- **风险**: 排序不确定、契约违背埋雷（如后续有人将步骤放入有序集合）。
- **建议**: 改为 `return cmp;`，或明确注释"同名实例才需要 stepId 稳定序"并改用比较器。
- **误报排除**: 代码全文核对；`cmp` 计算后丢弃属明显笔误特征，但当前调用方式下无现实数据损坏，定 P3。

### [P3] WorkflowImpl.getStepsByRecords 抛裸 IllegalArgumentException，违背错误处理两档策略

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowImpl.java:262-263`
- **维度**: D7
- **证据**:
```java
if (stepRecord == null)
    throw new IllegalArgumentException("wf.err_null_step_record");
```
- **现状**: wf-core 属框架核心，按平台规范应使用 `NopException + ErrorCode + .param(...)`；此处使用裸 IllegalArgumentException 且消息是伪错误码字符串。
- **风险**: 错误码体系外异常，前端/日志无法结构化处理。
- **建议**: 改用 NopWfCoreErrors 中定义的 ErrorCode（新增一个）。
- **误报排除**: 全模块仅此一处裸非受检异常（grep 已扫全模块）。

### [P3] WorkflowEngineImpl.logError 以 INFO 级别记录引擎错误

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1548-1554`
- **维度**: D4
- **证据**:
```java
public void logError(IWorkflowImplementor wf, String stepName, String actionName, Throwable e) {
    LOG.info("nop.wf.error:wfName={},wfId={},stepName={},actionName={}",
            wf.getWfName(), wf.getWfId(), stepName, actionName, e);
    wf.getStore().logError(wf.getRecord(), stepName, actionName, e);
}
```
- **现状**: 错误（含异常栈，SLF4J 尾参 throwable 机制使其作为异常输出）以 INFO 级别打印；生产日志级别设为 WARN 时将丢失现场（DB log 表仍有记录，DaoWorkflowStore.logError 用 LOG.error + 落库）。
- **风险**: 排障信息不完整；日志级别语义混乱。
- **建议**: 改为 `LOG.error`。
- **误报排除**: 代码核对无误；DaoWorkflowStore 侧对照确认仅此一处 INFO 记错误。

### [P3] WorkflowDesignerService 三处 catch 包装异常时丢弃 cause

- **文件**: `nop-wf/nop-wf-service/src/main/java/io/nop/wf/service/designer/WorkflowDesignerService.java:83-89,120-126,136-142`
- **维度**: D4（丢 cause）
- **证据**:
```java
} catch (Exception e) {
    throw new NopException(ERR_WF_DESIGNER_MODEL_PARSE_FAILED)
            .param(ARG_WF_DEF_ID, entity.getWfDefId())
            .param(ARG_DETAIL, e.getMessage());      // 未 .cause(e)，栈丢失
}
```
- **现状**: 三处（模型文本解析、JSON 解析、模型校验）均只保留 `e.getMessage()`，原始异常链丢失（对比 WorkflowDefinitionDO.parseWorkflowModel 的 `new NopException(ERR_WF_PARSE_MODEL_TEXT_FAIL, e)` 正确传 cause）。
- **风险**: 解析失败只能看到一行 message，定位 XNode/JSON 具体错误位置困难。
- **建议**: 补 `.cause(e)`。
- **误报排除**: 三处均已逐行核对；NopException 支持 cause 构造/链。

---

## 补充说明（未单列为发现的核实结论）

- **D2 资源管理**: 未发现流/连接泄漏点。主链路使用 ORM 会话与内存解析（`parseFromText`），无手工流操作。
- **D6 重复加载流程定义**: `WorkflowManagerImpl.getWorkflowModel` 走 `ResourceComponentManager.loadComponentModel`（带缓存的组件模型装载，DaoWorkflowModelLoader 为 loader），未发现每请求重复解析问题。
- **D7 Nop IoC 约定**: 全模块 `@Inject` 均为 setter 注入（无 private 字段注入）、无 `@Value`、无 Spring AOP 假设、bean 均在 `_vfs` 下 beans.xml 显式定义，未发现违背。
- **网关条件求值**: `passConditions` → `when.passConditions(scope)` 逐边求值 + or/and split 语义（`doTransition`）实现与模型语义一致，未发现求值错误；`targetSteps/targetCases` 过滤与 `ERR_WF_TRANSITION_TARGET_*` 报错路径一致。
- **状态机**: `transitToStatus` 为无校验 setter，合法性完全依赖引擎层 `isForStatus`/`checkAllowedAction` 前置检查；invokeAction 主路径检查完备（含挂起/已结束/等待/历史状态区分），但 engine 层 `transitTo`/`exitStep`/`changeActor` 等编程入口无状态前置校验（当前无对外暴露面，随 P0/P1 条目一并收敛即可）。
