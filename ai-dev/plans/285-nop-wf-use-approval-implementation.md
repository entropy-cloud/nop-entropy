# 285 nop-wf use-approval 能力实现

> Plan Status: active
> Last Reviewed: 2026-07-03
> Source: `ai-dev/design/nop-wf/approvable-entity-design.md`（设计基线，当前 draft，需 Phase 1 收敛）、`docs-for-ai/03-runbooks/enable-approval-on-entity.md`、`ai-dev/logs/2026/07-03.md`
> Related: `281-nop-wf-example-fixes.md`、`283-nop-wf-extension-features.md`

## Purpose

把 `approvable-entity-design.md` 描述的 use-approval 能力从"纯设计文档"收口到"代码层完整可用且经端到端验证"。完成后，任意业务实体在 ORM 标记 `tagSet="use-approval"` 即可经 codegen 自动获得标准审批能力（提交/通过/驳回/撤回/反审核），并与 nop-wf 审批流正确串联（DIRECT/WORKFLOW 双模）。

## 已裁定的关键设计决策（本计划前提）

> 以下决策已与用户确认，修正了原设计文档与平台既有事实的三处冲突。Phase 1 负责把它们写入设计文档使其收敛为最终态。

1. **flowInstance 字段：改用平台内置 `useWorkflow="true"` + `nopFlowId`**。平台已有机制：`OrmEntityModelInitializer`（`nop-orm-model/.../init/OrmEntityModelInitializer.java:421-422`）对 `useWorkflow="true"` 实体自动补 `nopFlowId`(VARCHAR32) 列；wf 引擎经 `.xwf` 根 `bizEntityFlowIdProp="nopFlowId"` 自动反写 wfId（`WorkflowEngineImpl.java:462-464` + `AbstractWorkflowStore.bindBizEntityFlowId`）。`TestBeeflowCase.testSalaryAdjustment` 已验证此链路。**放弃原设计的自定义 `flowInstanceId` 列 + codegen 补齐**（功能重复，且 codegen 无"追加列"环节）。
2. **IApprovableBiz：弱化为独立 mixin 接口，落 `nop-wf-core`**。`IApprovableBiz<T>`（T 不绑定 `IOrmEntity`）不 `extends ICrudBiz`，落 `nop-wf-core`（包 `io.nop.wf.core.biz`）。`IXxxBiz extends ICrudBiz<X>, IApprovableBiz<X>`。
   - **为何落 nop-wf-core 而非 nop-wf-api**：审批方法签名需要 `IServiceContext`，该类在 **nop-core**（`io.nop.core.context.IServiceContext`，非 nop-api-core；nop-api-core 只有其父接口 `IContext`）。`nop-wf-api` 仅依赖 nop-api-core，放不下 IServiceContext。`nop-wf-core` 已（经 nop-xlang→nop-core）传递持有 IServiceContext，**且 mixin 不 extends ICrudBiz 故无需新增 nop-orm 依赖**（原"放 core 会加 nop-orm"的担忧随 mixin 化消失）。外部用 use-approval 的 `-dao/-service` 本就需 nop-wf-core 生态（approval-support.xbiz/wf-approval.xlib/IWorkflowManager 均在 nop-wf-core）。
   - **接口作为 mixin 提供 default 方法**（见决策 3）。
3. **IApprovableBiz 提供 Java default 方法（编译占位，非 dispatch 入口）**：codegen 生成的 `XxxBizModel implements IXxxBiz` 必须满足接口契约才能编译。**关键机制事实**（`ReflectionBizModelBuilder.java:110/159/166` + `BizObjectBuildHelper`）：dispatch 层只注册带 `@BizAction`/`@BizMutation`/`@BizQuery` 注解的 Java 方法，无注解的 default 方法**对 dispatch 不可见**。因此 5 个审批 action 的运行时入口**唯一**是 `approval-support.xbiz` 的 action source（经 `x:extends` 合并注册）。Java default 方法**仅为满足编译**，禁止静默返回 null/false（须 fast-fail 抛异常，防被直接 Java 调用），但**不存在** "Java default 与 xbiz 二选一" 的 dispatch 冲突。真正的运行时风险是"x:extends 合并失败导致 action 未注册"（失败模式为 action-not-found 异常），由 Phase 5 的"operationDefinitions 含 5 mutation"断言覆盖。
4. **`_Xxx.xbiz` 根 `x:extends` 经 codegen 模板条件输出**：模板 `_{entityModel.shortName}.xbiz.xgen` 已用 `entityModel.containsTag(...)` 条件输出根属性（第 2 行 `tagSet` 范例），新增条件 `x:extends` 同型可行（B2 已验证）。
5. **wf 启动 API 链（供 approval-support.xbiz source）**：`inject('nopWorkflowManager')`（bean 名见 `wf-core.beans.xml:10`）→ `newWorkflow(wfName, null)`（`IWorkflowManager.java:28`，null=最新版本）→ `ApprovalFlowHelper.start(wf, args, ctx)`（`support/ApprovalFlowHelper.java:18`，内部 start+complete+runAutoTransitions）。args key：`PARAM_BIZ_OBJ_NAME="bizObjName"`、`PARAM_BIZ_OBJ_ID="bizObjId"`（`NopWfCoreConstants.java:25-27`）。
6. **approveStatus dict valueType = string**（四态枚举 UNSUBMITTED/SUBMITTED/APPROVED/REJECTED），列类型 `String`，字段由实体 `*.orm.xml` 手写（权威在 model，非 codegen 生成）。

## Current Baseline

> 基于 live repo 核对（grep/glob/read 逐项确认）。设计文档已完整但含三处与平台事实冲突（已由上述决策修正）；代码层为零实现。

### 设计层（已成立，待 Phase 1 收敛）

- `ai-dev/design/nop-wf/approvable-entity-design.md`（Status: draft）、`docs-for-ai/03-runbooks/enable-approval-on-entity.md`、`ai-dev/design/nop-wf/README.md`、`docs-for-ai/INDEX.md` 均已收录。设计文档需按"已裁定决策"修正后转最终态。

### 代码层（use-approval 全部产物不存在，从零实现）

| 期望产物 | 落点 | 现状 |
|---|---|---|
| `IApprovableBiz<T>` mixin 接口 | `nop-wf-core`（`io.nop.wf.core.biz`，手写非生成） | **不存在** |
| `approval-support.xbiz` | `/nop/wf/base/approval-support.xbiz`（nop-wf-core VFS，与 `oa.xwf` 同目录） | **不存在** |
| `wf-approval.xlib` | `/nop/wf/xlib/wf-approval.xlib`（nop-wf-core VFS） | **不存在** |
| `wf/approve-status` 字典 | `nop-wf-meta` `_vfs/dict/wf/approve-status.dict.yaml` | **不存在**（同目录 5 个既有字典均为 int 型） |

### codegen 挂接点（已调研清楚，机制可行）

- **`_Xxx.xbiz` 根 x:extends**：模板 `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/{appName}-service/.../_{entityModel.shortName}.xbiz.xgen`（16 行），第 2 行已用 `entityModel.containsTag('not-pub')?'not-pub':null` 条件输出根 `tagSet`。新增 `x:extends="${entityModel.containsTag('use-approval') ? '/nop/wf/base/approval-support.xbiz' : null}"` 同型。
- **`I*Biz` extends**：模板 `nop-kernel/nop-codegen/.../orm/{appName}-dao/.../I{entityModel.shortName}Biz.java.xgen`（11 行，`xpl:outputMode="text"`），第 8 行字面写 `extends ICrudBiz<...>`，改用 `<c:if>` + `entityModel.containsTag('use-approval')` 条件追加 `, IApprovableBiz<...>`（import `io.nop.wf.core.biz.IApprovableBiz`）。
- **BizModel 连锁点**：`{entityModel.shortName}BizModel.java.xgen` 生成 `... implements I${shortName}Biz`。一旦 `I*Biz` 多继承 `IApprovableBiz`，Java 类须有 5 方法——由 IApprovableBiz 的 Java default 方法满足（决策 3）。
- **flowInstanceId codegen**：**取消**（决策 1，改用 `useWorkflow`/`nopFlowId`，零 codegen）。
- **参照范例**：`biz-gen.xlib` 的 `TaskFlowSupport`（`hasClass` 守卫 + 注入节点）、`orm-gen.xlib` 的 `<ExtFieldsSupport>`（`attrCsvSet('tagSet')` 过滤）、`meta-gen.xlib`（`entityModel.containsTag('view')`）。

### 可复用的既有基础设施（已就绪）

- wf 启动/结束事件：`IWorkflowManager`/`IWorkflow`/`ApprovalFlowHelper.start`、`WorkflowEngineImpl.java:1483/1499` 触发 `EVENT_BEFORE_END/AFTER_END`、`WfRuntime.java:302` listener `eventPattern` 匹配。
- `useWorkflow`/`nopFlowId` 反写：`OrmEntityModelInitializer.java:421-422`、`WorkflowEngineImpl.java:462-464`、`AbstractWorkflowStore.bindBizEntityFlowId`。
- 测试基类：`AbstractWorkflowTestCase`（`nop-wf-service/src/test/...`，`@Inject IWorkflowManager` + `startWorkflow`/`executeTask` 封装）；`TestBeeflowCase.testSalaryAdjustment`（业务实体+wf+flowId 反写端到端样板）；测试 `.xwf` 落 `/nop/wf/test/<name>/v1.xwf`。
- `xbiz.xdef` 已支持 `mutation/source`（line 72）与 `observes`（line 107-114）；xbiz source 里 `inject('beanName')` 范例（`NopDynModule.xbiz:26`、`NopAuthUserEx.xbiz:33`）。

### 预存 bug（不在本计划范围）

- `NopWfCoreConstants.EVENT_AFTER_END = "before-end"`（应为 `"after-end"`）。本计划用 `eventPattern="*end"` 通配规避，不依赖常量正确值。修复属独立收口（见 Deferred）。

## Goals

- 标记 `use-approval` 的实体经 codegen 自动获得：`IXxxBiz extends ICrudBiz<X>, IApprovableBiz<X>`、`_Xxx.xbiz` 继承 `approval-support.xbiz`。WORKFLOW 模式经实体 `useWorkflow="true"` + `nopFlowId` 自动获得 wfId 绑定（零 codegen）。
- 5 个标准 action 有真实 xbiz source（状态守卫 + wf 启动 + 幂等），非空壳。
- DIRECT 与 WORKFLOW 双模端到端跑通：DIRECT 单级直接状态迁移；WORKFLOW 经 wf 审批 + 结束事件 listener 回调业务 action 完成迁移。
- 审批状态 `approveStatus` 唯一由业务 action 写，wf 引擎不直写业务表（不用 `bizEntityStateProp` 写状态）。
- 设计文档收敛为最终态。

## Non-Goals

- 修复 `EVENT_AFTER_END` 常量 bug。
- 改动 `WorkflowEngineImpl` 引擎核心（Vision：引擎不可侵蚀，全走 codegen + objMeta + 扩展点）。
- 前端审批页面/控件。
- DingFlow JSON 对 use-approval 的导入。
- 真实业务模块（订单/请假）产品化落地——本计划仅用测试实体验证机制。

## Scope

### In Scope

- `IApprovableBiz` mixin 接口（nop-wf-core，含 Java default 方法）。
- `approval-support.xbiz`（5 action + source）、`wf-approval.xlib`（`notifyResult` 标签）。
- `wf/approve-status` 字典（string 四态）。
- codegen 扩展：`_Xxx.xbiz` 按 tag extends approval-support.xbiz、`IXxxBiz` 按 tag extends IApprovableBiz。
- DIRECT + WORKFLOW 双模端到端集成验证（落 nop-wf-service/src/test）。
- 设计文档 + runbook 按已裁定决策收敛。

### Out Of Scope

- `flowInstanceId` 自定义列及其 codegen 补齐（已被 `nopFlowId` 替代）。
- 审批历史/意见的产品化展示页面。

## Execution Plan

### Phase 1 - 设计文档收敛（基于已裁定决策）

Status: planned
Targets: `ai-dev/design/nop-wf/approvable-entity-design.md`、`docs-for-ai/03-runbooks/enable-approval-on-entity.md`、`ai-dev/design/nop-wf/README.md`

- Item Types: `Fix`

- [ ] 修正 `approvable-entity-design.md` 为最终态（去 draft），按"已裁定决策"改写：IApprovableBiz 落 nop-wf-core 为独立 mixin（附决策 2 的 IServiceContext 在 nop-core 的事实依据 + mixin 不需 nop-orm）、flowInstanceId→useWorkflow/nopFlowId（删 codegen 补齐章节，改为引用平台机制 + `OrmEntityModelInitializer:421`/`WorkflowEngineImpl:462` 锚点）、codegen 机制改述为"模板条件 `x:extends`/`extends`"、补 wf 启动 API 链（决策 5）、补 dispatch 机制事实（无注解 default 方法对 dispatch 不可见，xbiz action 为唯一注册源，见决策 3）。
- [ ] 同步修正 runbook `enable-approval-on-entity.md`：步骤 1 字段示例改 `useWorkflow="true"`+`approveStatus`（删 `flowInstanceId`）、步骤 3 生成物说明改为"xbiz source 提供 action，非 Java BizModel"（修正 M2 不一致）、步骤 5 配 `bizEntityFlowIdProp="nopFlowId"`。
- [ ] 修正 runbook 步骤 1 的 tag 说明保留 `use-approval`（kebab-case，对齐 `use-ext-field`）。

Exit Criteria:

- [ ] 设计文档 Status 字段去除 draft；IApprovableBiz 落点描述为 nop-wf-core 独立 mixin，含 IServiceContext 在 nop-core（非 nop-api-core）的事实依据
- [ ] 设计文档中 `flowInstanceId` 全部替换为 `nopFlowId` + `useWorkflow="true"` 机制说明，含平台源码锚点
- [ ] runbook `enable-approval-on-entity.md` 字段示例与 action 来源描述与平台事实一致（grep `flowInstanceId` 在两文档中应为 0 命中，或仅作为"已废弃"说明）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - IApprovableBiz 接口 + wf/approve-status 字典

Status: planned
Targets: `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/biz/IApprovableBiz.java`、`nop-wf/nop-wf-meta/src/main/resources/_vfs/dict/wf/approve-status.dict.yaml`

- Item Types: `Fix | Proof`

- [ ] 新建 `IApprovableBiz<T>`（独立 mixin，不 extends ICrudBiz），5 方法签名：`T submitForApproval(String id, IServiceContext context)` / `withdrawApproval` / `approve` / `reject` / `reverseApprove`（均同签名返 `T`）。5 方法均为 Java default 实现，**仅为满足编译**（dispatch 不可见无注解方法，见决策 3）。禁止静默返回 null/false——default 方法体须 fast-fail（抛带明确信息的异常，说明"真实逻辑由同名 xbiz action 提供，此 default 仅满足 Java 编译，若被直接 Java 调用说明用法异常"）。
- [ ] 新建 `approve-status.dict.yaml`：`valueType: string`，四态 UNSUBMITTED/SUBMITTED/APPROVED/REJECTED（格式参照 `wf-status.dict.yaml`，首行 `# __XGEN_FORCE_OVERRIDE__`）。
- [ ] 测试：接口编译 + 5 方法存在 + default 方法体非空且含 throw（fast-fail）；字典加载 + 四态 label/value 正确。

Exit Criteria:

- [ ] `IApprovableBiz.java` 存在于 `nop-wf-core`（`io.nop.wf.core.biz` 包），编译通过，5 方法均有 default 实现
- [ ] `approve-status.dict.yaml` 存在于 nop-wf-meta `_vfs/dict/wf/`，`valueType: string`，含四态
- [ ] **新功能必有测试**：列出验证"5 方法签名存在""default 方法 fast-fail（体含 throw 非返回 null）""四态 label/value"的测试用例名
- [ ] **无静默跳过**：default 方法体非空、非 `return null`/`return false`/空体（grep 可验：每个 default 方法体含 `throw`）
- [ ] `No owner-doc update required`（设计已在 Phase 1 更新）
- [ ] `./mvnw compile -pl nop-wf/nop-wf-core,nop-wf/nop-wf-meta -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - approval-support.xbiz + wf-approval.xlib

Status: planned
Targets: `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/approval-support.xbiz`、`nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/wf-approval.xlib`

- Item Types: `Proof`

- [ ] 新建 `approval-support.xbiz`（完整 `<biz>` 根文档，可被 `x:extends`），定义 5 个 mutation action，每个 source 含真实逻辑：
  - 状态守卫：`submitForApproval` 仅 UNSUBMITTED→SUBMITTED；`approve` 仅 SUBMITTED→APPROVED；`reject` 仅 SUBMITTED→REJECTED；`withdrawApproval` 仅 SUBMITTED→UNSUBMITTED；`reverseApprove` 仅 APPROVED→SUBMITTED。非法态抛异常（不静默跳过）。
  - wf 启动（仅 `submitForApproval`，且 objMeta 扩展属性 `wf:wfName` 非空时）：读取 `thisObj.objMeta.prop_get('wf:wfName')`（**注意**：`IObjMeta` 无 `.ext()` 方法，扩展属性统一用 `prop_get`；`wf:wfName` 含冒号必须用 `prop_get`/bracket 访问）；非空则 `inject('nopWorkflowManager')` → `newWorkflow(wfName, null)` → 设 args(`bizObjName`=实体短类名、`bizObjId`=`orm_idString()`) → `ApprovalFlowHelper.start(wf, args, svcCtx)`（import `io.nop.wf.core.support.ApprovalFlowHelper`）。`svcCtx` 为 xbiz source 内置变量（IServiceContext），无需声明 arg。
- [ ] 新建 `wf-approval.xlib`：`notifyResult` 标签，属性 `bizObj`/`approved`，source 内通过 `IBizObjectManager` 回调业务 action（**注意**：biz object **不是** IoC bean，无 `biz_Xxx` 命名约定；正确机制为 `inject(IBizObjectManager)` → `getBizObject(bizObj).invoke(approved?'approve':'reject', [id], null, svcCtx)`，参照 `BizActionInvoker.java:36-37`/`BizObjectQueryProcessorAdapter.java:40-41`）。具体 inject 的 bean 名/类型解析方式在实现时据 `IBizObjectManager`（`nop-biz/.../api/IBizObjectManager.java`）确认。
- [ ] 测试：`ResourceComponentManager.loadComponentModel` 加载 `approval-support.xbiz` 通过 `xbiz.xdef` 校验，断言 5 mutation 存在且每个 source 非空；`wf-approval.xlib` 经 `xlib.xdef` 校验，`notifyResult` 标签内引用 `IBizObjectManager`（非 `inject('biz_...')`）。

Exit Criteria:

- [ ] `approval-support.xbiz` 存在且经 `xbiz.xdef` 校验通过；5 mutation 各自 source 含状态迁移代码（grep source 含 `approveStatus` 赋值）+ `submitForApproval` source 含 `nopWorkflowManager`/`ApprovalFlowHelper.start`
- [ ] `approval-support.xbiz` 读 objMeta 扩展属性用 `prop_get('wf:wfName')`，**不含** `.ext(`（grep 验证）
- [ ] `wf-approval.xlib` 存在且经 `xlib.xdef` 校验通过，`notifyResult` 标签可解析；source 用 `IBizObjectManager`/`getBizObject`，**不含** `inject('biz_`（grep 验证 M8 修正）
- [ ] **无静默跳过**：5 action source 均为真实逻辑（无空 `<source/>`、无 `continue` 占位）；非法状态分支抛异常（grep 含 `throw` 或调校验标签）
- [ ] **新功能必有测试**：列出验证"5 mutation 存在""source 非空""submitForApproval 含 wf 启动调用"的测试用例名
- [ ] `No owner-doc update required`
- [ ] `./mvnw compile -pl nop-wf/nop-wf-core -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - codegen 扩展（xbiz x:extends + IBiz extends）

Status: planned
Targets: `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/{appName}-service/.../_{entityModel.shortName}.xbiz.xgen`、`.../orm/{appName}-dao/.../I{entityModel.shortName}Biz.java.xgen`

- Item Types: `Proof`

- [ ] 改 `_{entityModel.shortName}.xbiz.xgen`：`<biz>` 根增条件 `x:extends="${entityModel.containsTag('use-approval') ? '/nop/wf/base/approval-support.xbiz' : null}"`（参照第 2 行 `tagSet` 写法）。
- [ ] 改 `I{entityModel.shortName}Biz.java.xgen`：用 `<c:if test="${entityModel.containsTag('use-approval')}">` 在 import 段加 `import io.nop.wf.core.biz.IApprovableBiz;`，在 `extends` 行条件追加 `, IApprovableBiz<${entityModel.shortName}>`。
- [ ] **接线验证（模板逻辑，单元测试）**：写 codegen 单元测试，feed 一个含 `use-approval` tag 的 mock entityModel，断言两个模板渲染结果：(a) `_Xxx.xbiz` 文本含 `x:extends="/nop/wf/base/approval-support.xbiz"`；(b) `IXxxBiz.java` 文本含 `extends ICrudBiz<X>, IApprovableBiz<X>`。另 feed 一个**无** tag 的 entityModel，断言渲染结果**不含** approval 相关内容。
- [ ] **回归**：对至少 3 个未标 `use-approval` 的实体（如 NopWfInstance、NopAuthUser、NopDynEntity），用 `./mvnw` 触发对应模块 codegen/postcompile 后，生成物 git diff 无 approval/extends 相关新增行。

Exit Criteria:

- [ ] 两个 codegen 模板按条件生效：use-approval 实体的 `_Xxx.xbiz` 含 approval extends、`IXxxBiz` 含 IApprovableBiz extends（repo-observable，文件读取可断言）
- [ ] **接线验证（加载态）**：use-approval 实体生成的 `_Xxx.xbiz` 加载后含 5 个 approval mutation（不只文件含 extends 字符串，而是 `loadComponentModel` 结果含 action）——此加载态验证在 Phase 5 用真实 postcompile 生成物完成（M9：Phase 4 验模板逻辑、Phase 5 验真实生成物加载）
- [ ] 回归：未标 tag 实体 regenerate 后生成物无 approval 变化（git diff 为空或无关）
- [ ] **无静默跳过**：未实现的 codegen 分支不存在（条件 tag 命中即生成、不命中即不变，无占位）
- [ ] **新功能必有测试**：列出验证"tag→extends 映射""无 tag 不影响"的测试用例名
- [ ] 若改变 live baseline：`docs-for-ai/03-runbooks/debug-codegen-and-generated-files.md` 或相关 codegen 文档已评估（如无需改明确注明）
- [ ] `./mvnw compile -pl nop-kernel/nop-codegen -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 端到端集成验证（DIRECT + WORKFLOW）

Status: planned
Targets: `nop-wf/nop-wf-service/src/test/`（测试实体 + .xwf + 测试类，继承 `AbstractWorkflowTestCase`）

- Item Types: `Proof`

- [ ] 测试实体 A（DIRECT）：ORM `tagSet="use-approval"` + `useWorkflow="true"`，含 `approveStatus`（String，`ext:dict="wf/approve-status"`）；xmeta **不配** `wf:wfName`。
- [ ] 测试实体 B（WORKFLOW）：同上 + xmeta 配 `wf:wfName="test/approval-xxx"`；对应 `.xwf`（`/nop/wf/test/approval-xxx/v1.xwf`，根 `bizEntityFlowIdProp="nopFlowId"`，`x:extends` `/nop/wf/base/oa.xwf`）+ listener（`eventPattern="*end"` 调 `wf-approval:notifyResult`）。
- [ ] **codegen 触发（M9）**：在 nop-wf-service test 建 codegen config（orm model + postcompile 配置），使 `./mvnw` 在 `generate-test-resources` 阶段为 A/B 实体**真实生成** `_Xxx.xbiz`/`IXxxBiz`/`XxxBizModel`；e2e 测试消费这些真实生成物（不手写 xbiz 绕过 codegen，否则 Closure Gate "codegen→runtime 连通" 失效）。
- [ ] **端到端测试（DIRECT）**：`submitForApproval` UNSUBMITTED→SUBMITTED；`approve` SUBMITTED→APPROVED；`reject`/`withdrawApproval`/`reverseApprove` 各自迁移；幂等（重复 approve 在终态抛异常或不变）。
- [ ] **端到端测试（WORKFLOW）**：`submitForApproval` 启动 wf + `nopFlowId` 落库（断言 `entity.nopFlowId == wf.wfId`）；审批步 agree 到流程结束；listener 回调 `approve`；断言 `approveStatus`→APPROVED + 状态由业务 action 写（非 wf 直写）。
- [ ] **Anti-Hollow 断言（M6 修正）**：(a) 断言 bizObject 的 operation 定义中**实际注册了** 5 个 approval mutation（如读 `bizObj.getOperations()`/operationDefinitions 含 submitForApproval/approve/reject/withdrawApproval/reverseApprove）——这是**真正的运行时风险**（x:extends 合并是否成功），而非"Java default vs xbiz"（后者经核实不存在冲突，见决策 3）；(b) 断言 wf 结束事件 listener **运行时实际调用了业务 approve action**（计数器/标志/EventBus 事件，断言其在 wf end 后被触发）。

Exit Criteria:

- [ ] DIRECT 模式：5 action 状态迁移全绿 + 幂等守卫生效（测试方法明确命名，如 `testDirectApprove_stateTransition`）
- [ ] WORKFLOW 模式：submit→wf→listener→approve 全链绿，`nopFlowId` 反写 + approveStatus 由 action 写
- [ ] **端到端验证**（Rule #22）：从 `submitForApproval` 入口到 approveStatus 终态 + nopFlowId 绑定的完整路径已验证
- [ ] **接线验证**（Rule #23）：bizObject operationDefinitions 含 5 approval mutation（证明 x:extends 合并注册成功）；wf listener 运行时实际调用业务 approve（计数器/标志断言）
- [ ] **无静默跳过**：未实现 action 路径在测试中失败而非静默通过
- [ ] 若 runbook `enable-approval-on-entity.md` 与实现有出入则修正（Phase 1 已大改，此处复核）
- [ ] `./mvnw test -pl nop-wf,nop-kernel/nop-codegen -am` 通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] `IApprovableBiz`（nop-wf-core mixin + default 方法）、`approval-support.xbiz`、`wf-approval.xlib`、`approve-status.dict.yaml` 四产物存在且有测试
- [ ] codegen 两处扩展（`_Xxx.xbiz` x:extends / `IXxxBiz` extends）对 use-approval tag repo-observable 生效；未标 tag 实体回归无变化
- [ ] DIRECT + WORKFLOW 双模端到端通过（状态迁移 + wf 串联 + nopFlowId 反写 + 幂等）
- [ ] wf 引擎核心未被修改（`WorkflowEngineImpl` 无为本计划的改动）
- [ ] approveStatus 唯一写者为业务 action（端到端证明 wf 不直写业务表状态）
- [ ] 设计文档 `approvable-entity-design.md` 收敛为最终态（draft 移除，三处冲突已按裁定修正）
- [ ] runbook `enable-approval-on-entity.md` 与实现一致
- [ ] **Anti-Hollow Check**：closure audit 验证（a）codegen→xbiz（x:extends 合并注册 5 mutation）→wf→listener→业务 action 调用链运行时连通，（b）5 action source 非空壳，（c）5 mutation 实际注册进 bizObject operations（非仅类型/接口存在）
- [ ] 不存在被静默降级到 deferred 的 in-scope 项
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `./mvnw test -pl nop-wf,nop-kernel/nop-codegen -am` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### EVENT_AFTER_END 常量 bug 修复

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `NopWfCoreConstants.EVENT_AFTER_END` 当前值 `"before-end"`（应为 `"after-end"`）。本计划用 `eventPattern="*end"` 通配规避，不依赖常量正确值，use-approval 功能成立不受影响。修复可能影响既有依赖该值的 listener，属独立收口。
- Successor Required: yes
- Successor Path: 独立 nop-wf 常量修复计划（后续创建）

## Non-Blocking Follow-ups

- 真实业务模块产品化落地 use-approval 的 best-practice 示例（runbook 可补完整业务联动示例）。
- 审批历史/意见的产品化展示（前端）。

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:
- （关闭时由独立子 agent 填写）
