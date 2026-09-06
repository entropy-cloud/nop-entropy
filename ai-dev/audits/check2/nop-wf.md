# nop-wf 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-wf（nop-wf-api / nop-wf-core / nop-wf-dao / nop-wf-service / nop-wf-scheduler / nop-wf-ai / nop-wf-app）
- 文件数: 202（`*/src/main/java`，不含 target/、`_` 前缀生成文件与 `_gen/`、src/test）
- 覆盖范围声明: 深读 26 个关键实现文件（WorkflowEngineImpl、WfRuntime、WorkflowImpl、WorkflowStepImpl、WorkflowManagerImpl、WorkflowCoordinatorImpl、DefaultWorkflowExecutor、WfActorAssignSupport、ExecGroupSupport、WorkflowServiceImpl、ApprovalFlowHelper、DaoWorkflowStore、AbstractWorkflowStore、DaoWorkflowModelLoader、WfResourceNamespaceHandler、DaoWfActorResolver、WfTaskScanner、WorkflowDesignerService、WfGraphDocumentCodec、WfModelAnalyzer、WorkflowStepRecordBean、WorkflowRecordBean、WorkflowDefinitionDO、DefaultWorkflowDOProvider、WfAiHelper、NopWfDefinitionBizModel），并交叉核验 beans.xml、nop-wf.orm.xml、wf.xdef、ConvertHelper/QueryBean/GraphDepthFirstIterator/UserContextImpl 等依赖类；其余约 176 个文件（api bean、模型对象、生成实体的保留类）做模式扫描（private @Inject、@Value、空 catch、bare RuntimeException、SimpleDateFormat/Random、流泄漏、static 可变状态），模式扫描覆盖 100%，深读比例约 13%（按文件数，但覆盖引擎/鉴权/持久化/调度全部高风险路径）。未覆盖区域: nop-wf-api 的 Input/OutputBean 字段级代码、_gen 生成代码细节、nop-wf-web/nop-wf-meta/nop-wf-codegen（无 src/main/java 实现或纯生成）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 4 |
| P2 | 8 |
| P3 | 3 |

## 发现列表

### [P1] doReject 用步骤名调用按 stepId（主键 UUID）查询的 getStepById，指定目标步骤的驳回必然失败

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1238-1247`
- **维度**: D1（另涉 D8：参数语义与被调方法契约不符）
- **证据**:
```java
Set<String> rejectSteps = wfRt.getRejectSteps();
...
if (rejectSteps != null && !rejectSteps.isEmpty()) {
    for (String rejectStepName : rejectSteps) {
        IWorkflowStepImplementor rejectStep = wf.getStepById(rejectStepName);
        if (rejectStep == null)
            throw wfRt.newError(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, rejectStepName);

        if (!dag.hasAncestor(stepModel.getName(), rejectStepName))
            throw wfRt.newError(ERR_WF_REJECT_STEP_IS_NOT_ANCESTOR_OF_CURRENT_STEP)
```
- **现状**: `rejectSteps` 来自用户 action 参数 `VAR_REJECT_STEPS`（`initArgs` L532-534 `ConvertHelper.toCsvSet`），语义是步骤名——同函数下方 `dag.hasAncestor(stepModel.getName(), rejectStepName)` 按 stepName 做 DAG 祖先校验可证。但 `wf.getStepById`（`WorkflowImpl.java:168-181`）先查内存 `steps` map（key 为 stepId），再走 `wfStore.getStepRecordById(wfRecord, stepId)`，而 `DaoWorkflowStore.getStepRecordById`（`DaoWorkflowStore.java:310-314`）实现为 `stepDao().getEntityById(stepId)`，按 UUID 主键查询。stepName 不是 UUID，查询必然 miss，`WorkflowImpl.getStepById` 直接抛 `ERR_WF_STEP_INSTANCE_NOT_EXISTS`。
- **风险**: 任何带 `rejectSteps` 参数的驳回 action 100% 抛出语义错误的异常（且到不了本应执行的 `ERR_WF_UNKNOWN_STEP`/祖先校验/`doRejectStep` 重建目标步骤逻辑），“指定目标步骤驳回”功能完全不可用；不带 rejectSteps 的默认驳回（走 `getPrevNormalStepsInTree` 分支）不受影响。
- **建议**: 按名称解析：`wf.getLatestStepByName(rejectStepName)` 或提供 `getStepByName`；或在 `IWorkflow` 上明确该参数接受 stepId 并同步修正服务层/前端契约。
- **误报排除**: 已读 `WorkflowImpl.getStepById`、`DaoWorkflowStore.getStepRecordById`、`DaoWorkflowStore.newStepRecord`（L99 `setStepId(StringHelper.generateUUID())` 确认 stepId 恒为 UUID）、`WorkflowServiceImpl` 各处 `getStepById(request.getStepId())` 均传真实 stepId，仅 doReject 传 stepName；全仓库 grep `rejectSteps` 无其他生产写入方。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `doReject` 改用 `wf.getLatestStepByName(rejectStepName)` 按步骤名解析（与 `rejectSteps` 参数语义及下方 `hasAncestor` 的 stepName 校验一致），未知步骤名仍走 `ERR_WF_UNKNOWN_STEP`。红验证：TestWorkflowEngine#testRejectToSpecifiedStepByName → NopException `nop.err.wf.step-instance-not-exists`(stepId=wf-start)（与审计证据同形态）。模块测试：service 121/0/1（skip 为既有 @Disabled）、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P1] joinGroupExpr 分组汇聚端到端失效：joinGroup 字段从不写入，读侧用上游步骤的模型求值恒为 null

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/AbstractWorkflowStore.java:108-133`（同文件 `:183-197`）、`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1808-1821`
- **维度**: D1、D8
- **证据**:
```java
// AbstractWorkflowStore.getJoinWaitStepRecords
String joinGroup = stepRecord.getJoinGroup();        // ← 引擎从不 setJoinGroup，恒 null
for (IWorkflowStepRecord step : getSteps(wfBean)) {
    ...
    String stepJoinGroup = joinGroupGetter.apply(step);
    if (Objects.equals(joinGroup, stepJoinGroup)) {  // null == null 恒真，分组过滤失效
        ret.add(step);
    }
}
// WorkflowEngineImpl.getJoinWaitSteps L1813-1816：getter 用 waitStep 自己的模型
return getJoinGroup((WfStepModel) waitStep.getModel(), waitStep, (WfRuntime) wfRt);
```
而基类 `WfStepModel.getJoinGroupExpr()`（`WfStepModel.java:201-206`）硬编码返回 `null`——joinGroupExpr 只定义在 `WfJoinStepModel` 上，上游 waitStep 的模型取到的永远是 null。
- **现状**: wf.xdef（L268-273）文档承诺“joinGroupExpr 相同的步骤会被认为是一组”。实现上：(1) `newStepForActor` 新建/复用 join 步骤实例时从不调用 `stepRecord.setJoinGroup(...)`（全模块 grep `setJoinGroup` 仅命中 IO bean 与 ORM 生成代码）；(2) 读侧 joinGroupGetter 用 waitStep 的模型（而非配置了 joinGroupExpr 的 join 步骤模型）求值。两侧恒为 null，`Objects.equals(null,null)` 恒真。
- **风险**: 配置了 joinGroupExpr 的 and-join：所有同名上游步骤被视为同组，任一分组到达即满足 `allWaitFinished`，join 步骤提前激活（等价 or-join），审批流在其余分组未完成时放行；`getNextJoinStepRecord` 的按组复用同样退化为“复用任意同名实例”，跨组实例被错误合并。
- **建议**: join 实例创建时把 joinGroupExpr 求值结果写入 `stepRecord.setJoinGroup` 并持久化；读侧 getter 改用 join 步骤自身的 joinGroupExpr 在对应上下文求值；为该特性补充多组并行的回归测试。
- **误报排除**: 已读 `WorkflowEngineImpl.newStepForActor`/`getJoinGroup`/`getJoinWaitSteps`、`AbstractWorkflowStore` 全文、`WfStepModel`/`WfJoinStepModel`/`_WfJoinStepModel`、wf.xdef 中 join-group-expr 的文档说明，并 grep 确认运行时无任何 `setJoinGroup` 写入点；未配置 joinGroupExpr 时两侧同为 null 属正常匹配，不构成问题。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 写侧：`newStepForActor` 创建 join 实例时持久化 `stepRecord.setJoinGroup(joinGroupExpr求值结果)`（此前求值结果只用于复用查找、从不落库，后续到达与新实例的 null 比较错位）；读侧：`getJoinWaitSteps` 的分组 getter 改用 join 步骤自身的 `joinGroupExpr` 在每个上游等待步骤上下文求值（原误用 waitStep 自己的模型、基类恒返回 null）。未配置 joinGroupExpr 时两侧仍为 null、行为不变（testJoin/testCosign 回归通过）。新增 test/joinGroup 模型（双 actor 上游按 actorId 分组）。红验证：TestWorkflowEngine#testJoinGroupExprSeparatesJoinInstances → expected [2,3] but was [null]（分组值丢失；且修复前第二个到达复用失败产生孤儿 WAITING 实例）。模块测试：service 121/0/1、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P1] WorkflowDesignerService.saveDocument 的 admin 鉴权条件写反：roles==null 时直接放行

- **文件**: `nop-wf/nop-wf-service/src/main/java/io/nop/wf/service/designer/WorkflowDesignerService.java:119-125`
- **维度**: D5
- **证据**:
```java
io.nop.api.core.auth.IUserContext userContext = context == null ? null : context.getUserContext();
java.util.Set<String> roles = userContext == null ? null : userContext.getRoles();
if (roles != null && !(roles.contains("admin") || roles.contains("nop-admin"))) {
    throw new NopException(io.nop.wf.core.NopWfCoreErrors.ERR_WF_NOT_ALLOW_MANAGE_BY_USER)
            ...;
}
```
- **现状**: 意图（见注释“要求admin角色”）是仅 admin 可保存；实际写成了 `roles != null && !contains` —— 只有 roles 为**非空集且不含 admin** 才拒绝。`context == null`、`userContext == null`、`getRoles() == null` 三种情况全部静默通过。`UserContextImpl`（nop-biz-auth-core）的 `roles` 字段无默认值，未填充时即为 null（对比 `WorkflowServiceImpl.checkManageAuthByDefault` 对 userId/manager 判空后才比对，且未登录时最终抛错，该处是 fail-closed 写法）。
- **风险**: 上下文无角色信息（无角色声明的 token、内部调用传 null context、未初始化的 UserContext）的调用者可以改写未发布流程定义（modelText 落库），审批人/网关/action source 可被篡改；对比已发布的 `checkNotPublished` 只挡已发布定义。
- **建议**: 改为 fail-closed：`if (roles == null || !(roles.contains("admin") || roles.contains("nop-admin"))) throw ...`。
- **误报排除**: 已读 saveDocument 全函数及上下文、`IUserContext`/`UserContextImpl` 的 roles 可空性、同仓 `WorkflowServiceImpl.checkManageAuthByDefault`（L268-287）的正确先例；`loadDesignerPage`（BizQuery）确实无鉴权但只读模型文本，风险低不单列。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 条件改为 fail-closed：`roles == null || !(contains admin/nop-admin)` 一律拒绝（对照 checkManageAuthByDefault 的判空先例）。既有 6 个 saveDocument 用例原以 null context 断言保存成功（编码了 fail-open 行为），同步改为带 admin 角色的上下文；新增无角色/null context 拒绝用例并断言拒绝后不落库。红验证：TestWorkflowDesignerService#testSaveDocumentRejectsMissingAdminRole → expected `nop.err.wf.not-allow-manage-by-user` but was `nop.err.wf.designer-invalid-document`（无角色请求穿透鉴权进入文档解析，与审计的 fail-open 证据一致）。模块测试：service 121/0/1、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P1] TO_ASSIGNED 迁移在 targetSteps 缺失/为空时静默“成功”，不建新步骤，流程可被意外自动结束

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1431-1439`（关联 `:1378-1387`、`:1467-1484`）
- **维度**: D1
- **证据**:
```java
case TO_ASSIGNED: {
    LOG.debug("nop.wf.transition-to-assigned:...");
    if (targetSteps != null) {              // null 或空集都导致零创建
        for (String targetStep : targetSteps) {
            transitionToStep(currentStep, targetStep, actionName, toM, wfRt);
        }
    }
    break;                                  // 外层 L1369 已置 hasTrans = true
}
```
- **现状**: `doTransition` 对每个 toM 在 L1369 置 `hasTrans=true` 后才进入 `transitionTo`；TO_ASSIGNED 分支在 `targetSteps == null` 或空集（`ConvertHelper.toCsvSet("")` 产生空集，见 ConvertHelper.java:1284-1298）时不创建任何步骤。随后 `doInvokeAction` 因 hasTrans=true 不抛 `ERR_WF_ACTION_TRANSITION_NO_NEXT_STEP`，`doExitStep(COMPLETED)` 正常完成当前步骤，`delayExecute(checkEnd)` 中 `isAllStepsHistory` 为真时整个流程被 `doEndWorkflow(COMPLETED)` 自动结束。`ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH`（L1379-1381）仅在 targetSteps 非空且无匹配时触发，缺失参数场景无任何防线。
- **风险**: to-assigned 动作（自由迁移到用户选择的目标步骤）被调用时若前端未传/传空 `targetSteps` 参数（调用方缺陷或恶意构造报文），当前步骤静默完成、无后继步骤、流程意外整单结束——审批链被截断且无错误可查。
- **建议**: `doTransition` 进入 TO_ASSIGNED 分支时校验 `targetSteps` 非空，为空抛 `ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH`（或专用错误码）；对空集与 null 一视同仁。
- **误报排除**: 已读 `doTransition`/`transitionTo`/`doInvokeAction`/`checkEnd`/`initArgs`（VAR_TARGET_STEPS 的 toCsvSet 转换）完整链路，确认空集/null 两条路径都绕过 L1348 的过滤与 L1379 的报错；TO_STEP 分支不受影响（无 caseValue 时按模型配置创建）。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `transitionTo` 的 TO_ASSIGNED 分支入口校验 `targetSteps` 非空，null/空集一致抛 `ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH`（在执行任何 beforeTransition/建步骤之前快速失败，当前步骤不再被静默完成）。既有 testToAssign/testToAssign1（均显式传 targetSteps）回归通过。红验证：TestWorkflowEngine#testToAssignedWithoutTargetStepsRejected → "Expected NopException to be thrown, but nothing was thrown"（原路径静默完成步骤、流程可被意外结束）。模块测试：service 121/0/1、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P2] WfModelAnalyzer.initTransitionFromSteps 复制粘贴错误：fromSteps 为 null 时误清空 transitionToSteps

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/model/analyze/WfModelAnalyzer.java:136-143`
- **维度**: D1
- **证据**:
```java
wfModel.getSteps().forEach(step -> {
    if (step.getTransitionToSteps() == null) {
        step.setTransitionToSteps(Collections.emptyList());
    }
    if (step.getTransitionFromSteps() == null) {
        step.setTransitionToSteps(Collections.emptyList());   // ← 应为 setTransitionFromSteps
    }
});
```
- **现状**: `buildDag`（L60，先于本方法执行）已为每个步骤 `setTransitionToSteps(new ArrayList<>(toSteps))`；`initTransitionFromSteps` 的 L132-134 只给有入边的步骤设置 fromSteps。因此对**无入边步骤（起始步骤等）**：fromSteps 保持 null（本应初始化为空表），而已计算好的 toSteps 被 `setTransitionToSteps(emptyList())` 覆盖清空。
- **风险**: `IWorkflowStepModel.getTransitionToSteps()/getTransitionFromSteps()` 是公开模型接口，暴露的模型数据错误（起始步骤出边列表为空、部分步骤 fromSteps 为 null）。当前仓库内运行时无消费方（全仓 grep 仅接口/实现/分析器自身/测试 mock 命中），故暂无直接运行时危害；一旦被 XPL 模板或业务代码消费即成 bug。
- **建议**: 第二个分支改为 `step.setTransitionFromSteps(Collections.emptyList())`。
- **误报排除**: 已读 `WfModelAnalyzer.analyze` 的调用顺序（buildDag → setDag → initTransitionFromSteps → checkEnd）、`GraphDepthFirstIterator` 对 null targets 的容忍（L59 null 检查，故 checkEnd 不 NPE），并全仓 grep 确认两个字段当前无运行时消费方。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 第二个分支改为 `step.setTransitionFromSteps(Collections.emptyList())`。红验证：TestWorkflowEngine#testAnalyzedModelInitializesTransitionStepLists → "start step toSteps must be preserved expected: true but was false"（test/join 模型起始步骤出边被清空），fromSteps 断言（非 null）同场验证。模块测试：service 121/0/1、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P2] allowCallByUser 对已删除用户的步骤 NPE：actor 解析结果未判空

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:1062-1081`
- **维度**: D1
- **证据**:
```java
IWfActor owner = step.getOwner();
String userId = ctx.getUserId();
if (owner != null) { ... }
IWfActor actor = step.getActor();                       // 用户被删时 resolveUser 返回 null
if (IWfActor.ACTOR_TYPE_USER.equals(actor.getActorType())) {   // ← NPE
```
- **现状**: `WorkflowStepImpl.getActor()` → `wf.resolveActor(...)` → `DaoWfActorResolver.resolveUser`（`DaoWfActorResolver.java:72-77`）在用户不存在时 LOG + **return null**。owner 为空且 actor 指向已删除用户/部门/角色时，`actor.getActorType()` 抛 NPE。
- **风险**: 组织数据清理（删除离职用户、撤并部门）后，遗留步骤实例上的任何 `invokeAction`（含 WfTaskScanner 的到期动作）都以 NPE 而非业务错误失败，掩盖真实原因。
- **建议**: actor 为 null 时返回 false（或抛带 wfId/stepId 上下文的 NopException）。
- **误报排除**: 已读 `allowCallByUser` 全文、`WorkflowStepImpl.getActor/getOwner`、`DaoWfActorResolver.resolveUser/resolveDept/resolveGroup/resolveRole` 的可空返回；`containsUser` 分支同样依赖非空 actor。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. owner 为空分支后补 `if (actor == null) return false;`（actor 指向的用户/部门/角色已被删除时无人可调用该步骤，后续 invokeAction 得到 `ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER` 而非 NPE）。红验证：TestWorkflowEngine#testAllowCallByUserWithDeletedActorReturnsFalse → NPE "Cannot invoke IWfActor.getActorType() because actor is null"（与审计证据同形态；用例将 record actor 置为 resolver 返回 null 的已删除用户并清空 owner）。模块测试：service 121/0/1、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P2] startStepName 指向 join 步骤时 newStepForActor 空指针：currentStep 为 null 未防护

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:331-336`
- **维度**: D1
- **证据**:
```java
if (stepModel.getJoinType() != null) {
    String joinGroup = getJoinGroup(stepModel, currentStep, wfRt);
    // join步骤会自动查找已经存在的步骤实例
    IWorkflowStepRecord stepRecord = wf.getStore().getNextJoinStepRecord(currentStep.getRecord(),
            joinGroup, stepModel.getName(), actor);      // ← currentStep == null 时 NPE
```
- **现状**: `start` L221 调用 `newSteps(null, wfModel.getStartStep(), ...)`，currentStep 为 null。若模型的 startStepName 指向配置了 joinType 的 join 元素（xdef 层未禁止，`WfModelAnalyzer.analyze` 只校验步骤存在性），`currentStep.getRecord()` 抛 NPE，流程启动裸崩。L344/L402 的 `currentStep != null` 检查说明该方法设计上要容忍 null，此处遗漏。
- **风险**: 畸形/误配模型在运行期才以 NPE 暴露，而非模型加载期的可诊断错误。
- **建议**: `currentStep == null` 时跳过 join 复用查找（直接新建），或在 `WfModelAnalyzer` 中禁止 startStepName 指向 join 元素。
- **误报排除**: 已读 `start`→`newSteps(null,...)` 调用链、`newStepForActor` 其余两处对 currentStep 的判空（L344、L402）、`WfModelAnalyzer.analyze` 的校验范围。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. joinType 分支内对 `currentStep == null`（startStepName 指向 join 的畸形配置）跳过 `getNextJoinStepRecord` 复用查找、直接新建实例（与 L344/L402 的容忍语义一致）。新增 test/joinStart 模型。红验证：TestWorkflowEngine#testStartStepAsJoinStepDoesNotFail → NPE "Cannot invoke IWorkflowStepImplementor.getRecord() because currentStep is null"（WorkflowEngineImpl.newStepForActor，与审计证据同形态）。模块测试：service 121/0/1、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P2] 引擎管理类步骤操作（转办/加签/改执行人/改 owner/transitTo/exitStep）无操作者鉴权，鉴权责任完全上推且无文档约束

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowStepImpl.java:115-144,252-265`、`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/support/ApprovalFlowHelper.java:44-50`
- **维度**: D5
- **证据**:
```java
// WorkflowStepImpl：invokeAction 有 allowCallByUser 门禁（L228），以下则直接放行
public IWorkflowStep transferToActor(WfActorAndOwner actorAndOwner, boolean exitCurrentStep, IServiceContext ctx) {
    return wf.executeNow(() -> {
        IWorkflowStepImplementor step = wf.getEngine().transferToActor(this, actorAndOwner, exitCurrentStep, ctx);
        return step; });
}
// ApprovalFlowHelper.transferToUser 同样无校验，直接 step.transferToActor(...)
```
- **现状**: `invokeAction` 强制 `allowCallByUser`（校验 owner/actor/代办关系），而 `transferToActor`/`addActor`/`changeActor`/`changeOwnerId`/`transitTo`/`exitStep` 在 WorkflowStepImpl 与 WorkflowEngineImpl 两层都无任何操作者校验。`WorkflowServiceImpl.transferActorsAsync` 是唯一带鉴权（本人或 manager）的入口，但 `ApprovalFlowHelper`（公开静态工具，供业务 XPL/服务调用）及任何直接持有 IWorkflowStep/IWorkflow 引擎 API 的代码都可以任意转办/替换他人任务。对比 `suspend/resume/kill` 至少有 `checkManageAuth` 模型钩子。
- **风险**: 公开引擎 API 形成隐式信任边界：业务层一旦拿到 step 引用（例如通过 wf.getStepById）即可把审批任务转给任意人（`transferToActor` 内 `resolveDynamicActor`/`requireUser` 只校验目标存在，不校验操作者），越权转办/代办难以在代码评审中系统性发现。
- **建议**: 至少在 `IWorkflowStepImplementor` 层为管理操作补充可选的 checkManageAuth 钩子（与 suspend/resume 一致），或在接口 Javadoc/owner doc 中显式声明“调用方必须自行鉴权”的契约。
- **误报排除**: 已读 WorkflowStepImpl 全部操作方法、WorkflowEngineImpl 对应实现（transferToActor/addActor/changeActor/changeOwner/transitTo/exitStep 均无鉴权）、WorkflowServiceImpl（transferActorsAsync 有 checkTransferActorsAuth/transferBySelf，是唯一带鉴权入口）、ApprovalFlowHelper 全文。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（按审计建议的可选形态之二：契约显式化）. IWorkflowStep 六个管理方法（changeActor/changeOwnerId/transferToActor/addActor/transitTo/exitStep）与 ApprovalFlowHelper 补充接口 Javadoc：显式声明"引擎层不做操作者鉴权，调用方必须自行鉴权（对照 transferActorsAsync 先例），不得将未鉴权的用户输入直接透传"。免红理由：纯注释，无行为变化。引擎层 checkManageAuth 钩子扩展（需区分 owner 自转办/manager/admin 三类合法操作者）与 P2-11 的系统旁路应一并设计，见 follow-up。

### [P2] changeOwner 不校验目标用户存在，owner 可被静默清空（含 WfAiHelper 的 manual-review 转人工）

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:859-868`、`nop-wf/nop-wf-ai/src/main/java/io/nop/wf/ai/WfAiHelper.java:27-33`
- **维度**: D1、D5
- **证据**:
```java
// WorkflowEngineImpl.changeOwner
IWfActor owner = StringHelper.isEmpty(ownerId) ? null : resolveUser(ownerId);
step.getRecord().setOwner(owner);          // resolveUser 返回 null → owner 被置空，无任何报错
// WfAiHelper.decide：低置信度转人工
step.changeOwnerId("manual-review", wfRt.getSvcCtx());   // "manual-review" 用户未必存在
```
- **现状**: `resolveUser` 对不存在用户返回 null，`setOwner(null)` 直接清空 owner 且不抛错（对比同文件 `transferToActor`/`addActor` 使用 `requireUser` 校验并抛 `ERR_WF_USER_NOT_EXISTS`）。`WfAiHelper.decide` 的兜底路径把任务转给硬编码的 "manual-review" 用户，该用户不存在时任务变成无主任务，且 `allowCallByUser` 对无 owner/无 user-actor 步骤的访问判定随之失效（走到 `actor.containsUser`）。
- **风险**: 转人工场景（AI 审批低置信度）或手工 changeOwnerId 拼写错误时任务静默失去归属，审批任务“消失”；也削弱后续鉴权判定。
- **建议**: `changeOwner` 中对非空 ownerId 使用 `requireUser`；`WfAiHelper` 的 manual-review 用户应可配置并在缺失时快速失败。
- **误报排除**: 已读 `changeOwner`/`transferToActor`/`addActor`（后两者用 requireUser 的对照）、`DaoWfActorResolver.resolveUser` 返回 null 的行为、`WfAiHelper.decide` 全文与 `allowCallByUser` 对 owner==null 的分支。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `changeOwner` 对非空 ownerId 改用 `requireUser`（不存在即抛 `ERR_WF_USER_NOT_EXISTS`，与 transferToActor/addActor 先例一致，ownerId 传空清空 owner 的语义保留）；WfAiHelper 的 "manual-review" 用户不存在时随之快速失败，不再静默失主。manual-review 用户可配置化涉及 wf-ai.xlib 标签契约，记 follow-up。红验证：TestWorkflowEngine#testChangeOwnerRejectsUnknownUser → "Expected NopException to be thrown, but nothing was thrown"（owner 被静默置空）；修复后同一用例断言存在用户仍可正常改派。模块测试：service 121/0/1、scheduler 7/0/0、ai 4/0/0，core/dao 无测试源。

### [P2] WfTaskScanner：getDueAction 与整个 remind 扫描循环无异常隔离，单条失败中断整批

- **文件**: `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:77-135`
- **维度**: D4
- **证据**:
```java
void scanDueTasksInCurrentSession() {
    for (IWorkflowStepRecord stepRecord : workflowStore.findDueActivatedSteps()) {
        String dueAction = getDueAction(stepRecord);      // ← try 之外：getWorkflow 对已删实例直接抛
        ...
        try {
            FutureHelper.syncGet(workflowService.invokeActionAsync(...));
        } catch (NopException e) { ... continue; }        // 只隔离 NopException
```
```java
void scanRemindTasksInCurrentSession() {
    for (IWorkflowStepRecord stepRecord : workflowStore.findRemindActivatedSteps()) {
        IWorkflowStep step = workflowManager.getWorkflow(stepRecord.getWfId())...;  // ← 无任何 catch
        reminderListeners.forEach(listener -> listener.onRemind(step));             // ← listener 异常同样外泄
```
- **现状**: 到期扫描中 `getDueAction`（内部 `getWorkflow` 对已删除实例抛 `ERR_WF_MISSING_WF_INSTANCE`）位于 try 之外；且 catch 仅覆盖 NopException，非 NopException 的 RuntimeException 仍会中断循环。提醒扫描则完全没有 try-catch。注释“单个到期任务失败不中断整批”（L108）表达的意图未被完整实现。
- **风险**: 一条脏数据（实例被并发删除、listener 抛错）导致该轮调度中排在其后的所有到期/提醒任务全部跳过，直到数据被人为清理；到期自动处理延迟不可控。
- **建议**: 将 `getDueAction` 移入 try，catch 范围放宽到 Throwable（记录 error 后 continue），remind 循环同样逐条隔离。
- **误报排除**: 已读 WfTaskScanner 全文、`WorkflowManagerImpl.getWorkflow`（记录缺失即抛错）、`DaoWorkflowStore.findDueActivatedSteps/findRemindActivatedSteps`（纯查询不抛）。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 到期扫描：getDueAction 及请求构造移入 try，新增 catch(Exception) 兜底（原有 NopException 特判分支保留）；提醒扫描：整块逐条 try-catch，失败记录不更新提醒计数（下一轮可重试）。红验证（临时还原产品代码后运行确认）：TestWfTaskScanner#testScanDueTasksContinuesAfterGetDueActionFailure、#testScanRemindTasksContinuesAfterFailure、#testScanRemindTasksListenerFailureDoesNotBreakBatch → "Unexpected exception thrown: NopException nop.err.wf.missing-wf-instance(wfId=wf-broken)"/IllegalStateException（单条失败中断整批）。事实纠正：invokeActionAsync 抛出的非 Nop 异常经 `FutureHelper.syncGet`→`NopException.adapt` 包装，原 catch(NopException) 已能隔离该路径（审计中"非 NopException 的 RuntimeException 仍会中断循环"对动作执行路径不成立，对 try 之外的 getDueAction 阶段成立）；#testScanDueTasksContinuesAfterNonNopActionFailure 为钉死该适配行为的伴随用例。模块测试：scheduler 7/0/0、service 121/0/1、ai 4/0/0，core/dao 无测试源。

### [P2] dueAction 到期动作以固定 "wf-scheduler" 身份走 invokeActionAsync，被 allowCallByUser 永久拒绝，超时自动处理对普通用户任务失效

- **文件**: `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:92-107,137-141`
- **维度**: D1、D5
- **证据**:
```java
} catch (NopException e) {
    ...
    if (NopWfCoreErrors.ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER.getErrorCode().equals(e.getErrorCode())) {
        // wf-scheduler固定身份对普通user/dept/role步骤必然通不过allowCallByUser：
        // ... 该拒绝属预期形态，记录后继续处理后续到期任务
        LOG.warn("nop.wf.scheduler.due-action-blocked-by-user-check:...");
        continue;
    }
```
- **现状**: 到期动作用 `newSchedulerContext()`（userId="wf-scheduler"）调用 `invokeActionAsync` → `WorkflowStepImpl.invokeAction` 的 `allowCallByUser` 对 owner/user-actor 步骤判定 `ownerId.equals("wf-scheduler")`=false、`canBeDelegatedBy`=false，必然抛 `ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER`。代码注释已知晓并把该拒绝作为“预期形态”记录 warn 后跳过。
- **风险**: 配置了 dueTime/dueAction 的普通人工审批步骤，超时后的自动动作（如自动同意/自动驳回）实际永不执行，只产生 warn 日志；业务以为有超时兜底实际没有（静默失效，需靠日志才能发现）。
- **建议**: 为调度器提供系统旁路（例如引擎层增加 system-caller 通道绕过 allowCallByUser 但保留 action 状态检查），或模型级显式声明该 dueAction 允许系统触发。
- **误报排除**: 已读 `newSchedulerContext`、`invokeActionAsync` → `WorkflowStepImpl.invokeAction` 的强制鉴权、`allowCallByUser`/`canBeDelegatedBy` 的判定逻辑，确认无任何角色/身份旁路。

> **处置（fix-ai-check 分支，2026-08-28）**: 裁定暂缓. 决策点：系统旁路的信任通道设计——(a) 引擎层 system-caller 通道（绕过 allowCallByUser 但保留 action 状态检查）的适用面与在进程内可构造 ServiceContext 下的可伪装性，或 (b) 模型级显式声明 dueAction 允许系统触发（更窄但需扩展 wf.xdef 模型语义）。属鉴权语义设计变更：check1（commit 075561f891，2026-08-23）已知情并刻意选择"warn 记录后跳过"的保守处置，推翻需设计评审，且与 P2-8 管理操作 checkManageAuth 钩子的扩展应一并决策（同属"系统/管理身份如何合法越过用户门禁"）。影响面：配置 dueTime/dueAction 的普通用户任务超时自动动作继续不执行（仅 warn 日志可发现），功能缺陷仍在。

### [P2] AbstractWorkflowStore.getNextJoinStepRecord 声明 actor 过滤参数但完全未使用（D8 契约漂移）

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/AbstractWorkflowStore.java:183-197`
- **维度**: D8
- **证据**:
```java
public IWorkflowStepRecord getNextJoinStepRecord(IWorkflowStepRecord stepRecord, String joinGroup,
                                                 String stepName, IWfActor actor) {
    IWorkflowRecord wfRecord = getWfRecord(stepRecord);
    for (IWorkflowStepRecord prev : getSteps(wfRecord)) {
        if (prev.getStatus() >= NopWfCoreConstants.WF_STEP_STATUS_HISTORY_BOUND) continue;
        if (prev.getStepName().equals(stepName)) {
            if (Objects.equals(joinGroup, prev.getJoinGroup()))
                return prev;      // actor 从未参与判定
```
- **现状**: 接口与实现签名承诺按 actor 区分 join 实例，实现只匹配 stepName+joinGroup；调用方 `WorkflowEngineImpl.newStepForActor` L335-336 传入 actor 期望“每个 actor 一个 join 实例”（多实例会签汇聚），实际任何 actor 都会复用第一个同名实例并覆盖其 execGroup/execOrder/actorModelId。
- **风险**: 多实例 join（同一步骤按 actor 展开多份后汇聚）时实例被错误合并，actorModelId/execGroup 被后到者覆盖；当前因 joinGroup 恒 null（见 P1 joinGroupExpr 条目）单实例场景“凑巧”正确，一旦分组修复此问题会显形。
- **建议**: 要么实现 actor 匹配（`actor.isActor(prev.getActorType(), prev.getActorId(), ...)`），要么从签名移除该参数并注明单实例语义。
- **误报排除**: 已读该方法全文、`IWorkflowStore` 接口声明、唯一调用方 `WorkflowEngineImpl.newStepForActor`，确认 actor 参数无任何使用。

> **处置（fix-ai-check 分支，2026-08-28）**: 裁定暂缓. 决策点：实现 actor 匹配会把"同名同组单实例复用"改为"每 actor 一个 join 实例"（多实例会签汇聚语义），改变所有 and-join 的实例拓扑——现有 testCosign 明确断言单实例复用语义，实施即破坏现行为且无多实例 join 的需求方与回归样例；且本次 P1-2 修复已激活 joinGroupExpr 分组维度，actor 维度与分组维度如何组合（交/并）需先定义。check1（075561f891）同样以"join按actor匹配改变复用拓扑"暂缓。备选：若最终裁定不做多实例 join，改为从签名移除 actor 参数（io.nop.wf.core.store.IWorkflowStore 为模块内接口，非跨模块公共契约）。

### [P3] getExecGroupFirstStep 对 Integer execOrder 自动拆箱，且全仓无调用方（死代码）

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowStepImpl.java:343-352`
- **维度**: D1
- **证据**:
```java
for (IWorkflowStep step : ret) {
    if (Objects.equals(step.getRecord().getExecGroup(), stepGroup) && step.getRecord().getExecOrder() == 0) {
        return (IWorkflowStepImplementor) step;
    }
}
```
- **现状**: `getExecOrder()` 返回 Integer，`doRejectStep`/`doWithdraw` 重建历史步骤时传入的 execOrder 可为 null，`== 0` 触发拆箱 NPE。全仓库 grep 无调用方（仅接口声明）。
- **风险**: 当前无运行时危害；未来被调用且数据含 null execOrder 时 NPE。
- **建议**: 用 `Integer.valueOf(0).equals(...)` 或 `Objects.equals`；若确认无需求可删除。
- **误报排除**: 已读该方法与 `doRejectStep`/`doWithdraw` 传入 execOrder 的来源（历史记录字段可空），并 grep 全仓确认无调用方。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. `== 0` 改为 `Integer.valueOf(0).equals(step.getRecord().getExecOrder())`（null execOrder 不再拆箱 NPE）。免测试理由：全仓无调用方的死代码上的防御性判空，无行为面可断言；保留方法未删除（IWorkflowStep 公开接口声明）。

### [P3] DefaultWorkflowDOProvider 将携带 IServiceContext 的 DO 缓存在实体对象上，context 字段实际未使用

- **文件**: `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/dataobject/DefaultWorkflowDOProvider.java:20-27`、`nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/dataobject/WorkflowDefinitionDO.java:29-44`
- **维度**: D3、D5
- **证据**:
```java
IWorkflowDefinitionDO definitionDO = entity.computeIfAbsent(
        IWorkflowDefinitionDO.class.getSimpleName(),
        k -> new WorkflowDefinitionDO(daoProvider, ormTemplate,
                workflowManager, entity, context));
```
- **现状**: 第一次调用者的 IServiceContext 被封进 DO 并随实体（ORM 会话内）缓存；`WorkflowDefinitionDO.serviceContext` 字段无任何读取点。
- **风险**: 当前仅是无用持有（无泄漏后果）；未来若有人开始使用该字段，会造成跨请求读取到陈旧调用者上下文（用户身份/求值作用域）的隐患。
- **建议**: 移除 serviceContext 字段或不将其纳入缓存键对象。
- **误报排除**: 已读 WorkflowDefinitionDO 全文确认字段无读取点、`NopWfDefinitionBizModel` 两处调用（save/copyForNew）均在请求上下文内即时使用。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 删除 WorkflowDefinitionDO 中无任何读取点的 serviceContext 字段及构造参数（调用方仅 DefaultWorkflowDOProvider 一处，已同步；IWorkflowDOProvider 接口签名未动，实体上的 DO 缓存不再携带调用者上下文）。免测试理由：纯字段删除，编译期保证，无行为变化。

### [P3] WorkflowImpl 实例状态非线程安全且 DefaultWorkflowExecutor 对同一 wfId 无互斥，依赖 ORM 乐观锁兜底

- **文件**: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/DefaultWorkflowExecutor.java:32-49`、`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/impl/WorkflowImpl.java:41-51,80-115`
- **维度**: D3
- **证据**:
```java
// DefaultWorkflowExecutor.execute：每次请求各自 new WorkflowImpl，无按 wfId 串行化
wf = workflowManager.getWorkflow(wfRef.getWfId());
T ret = task.apply(wf);
// WorkflowImpl：HashMap steps / ArrayDeque commandQueue / boolean executing 均非线程安全
private final Map<String, WorkflowStepImpl> steps = new HashMap<>();
```
- **现状**: 同一 wfId 的两个并发请求各自构造独立 WorkflowImpl，内存状态互不可见（如都读到步骤 ACTIVATED 并各自 invokeAction）；写竞争最终由 `nop_wf_step_instance` 的 versionProp 乐观锁（nop-wf.orm.xml L352-353）在提交时拦截。设计上是“乐观并发 + 提交冲突”的取舍。
- **风险**: 冲突方以乐观锁异常收场（可接受），但 `delayExecute` 的命令队列与 `getActivatedSteps` 的快照判断在并发窗口内可能产生重复的迁移尝试日志/中间写入回滚，行为不直观。
- **建议**: 若需严格串行可提供按 wfId 分段的锁实现（IWorkflowExecutor 可替换），至少在接口 Javadoc 标明并发语义。
- **误报排除**: 已读 DefaultWorkflowExecutor/WorkflowImpl 全文、beans.xml（注册的是 DefaultWorkflowExecutor，无其他串行化实现）、nop-wf.orm.xml 中 instance/step_instance 的 versionProp 配置。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（按审计建议的最小形态：契约文档化）. IWorkflowExecutor Javadoc 标明并发语义：缺省实现不对同一 wfId 互斥、每次调用独立 WorkflowImpl 内存状态、写竞争由 nop_wf_instance/nop_wf_step_instance 的乐观锁（versionProp）在提交时拦截、需要严格串行可提供按 wfId 分段的锁实现替换 bean。免红理由：纯注释。分段锁实现留作可选增强（无现实并发投诉驱动，暂不引入锁复杂度与死锁面）。

## 模式扫描阴性结果（D2/D3/D4/D7 常见项）

- 无 `@Inject private` 字段、无 Spring `@Value`（全部为 setter/包私有字段注入，beans.xml 注册齐全：wf-core/wf-dao/app-service/app-scheduler 均核对）。
- 无空 catch 块（`getAllowedActions` 的 `catch (NopException e) { continue; }` 带注释、属权限过滤语义）。
- 无 bare RuntimeException（错误码体系 NopWfCoreErrors/NopWfErrors/NopWfDesignerErrors 使用完整，cause 链保留）。
- 无 SimpleDateFormat/Math.random/new Random/printStackTrace；无流/连接资源需关闭的代码路径（WfGraphDocumentCodec/DaoWorkflowModelLoader 均为纯内存解析）。
- 实体乐观锁 versionProp 存在（nop_wf_instance/nop_wf_step_instance/nop_wf_definition）。
