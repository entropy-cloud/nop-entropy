# 2379 full-code-review R1.13 — nop-wf 双修复（P1-3.1-001 审批双事实源 + P1-3.1-002 超时 dueAction 通道）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.13；findings `P1-3.1-001`（SM-01）+ `P1-3.1-002`（SM-08），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.1-wf-deep-audit.md`
> Related: plan 2378（R1.12 已交付 approval-support auth 元数据——访问控制层；本 plan 为状态机层，分立互补不吞并，互引 R1.12 行）；P2-3.1-001/004/005/006 为台账行〔advisory〕非本 plan 交付物
> Owner Doc: `docs-for-ai/02-core-guides/workflow-configuration.md`

## Purpose

收口两条 P1：①approval-support 直改实体字段绕过工作流引擎（双事实源）——wf 活跃时拒绝直改（引擎路由 gate）+ 引擎回调改走内部专用通道；受派人校验由引擎既有 allowCallByUser 承担（wf 活跃路径只允许引擎落点）。②步骤级超时 dueAction 执行通道被 allowCallByUser 结构性阻断——系统身份旁路（scheduler 触发标记），使 xdef「自动触发」承诺对目标场景真实生效。

## Current Baseline

- **双事实源链（plan 审查 B1 勘正后的真实回调路径）**：`wf-approval.xlib` 的 `<notifyResult>` 标签（wf-approval.xlib:23 含 invoke('approve'/'reject') 逻辑）**全仓零消费方=死标签**；真实引擎→实体回调是 3 处内联 `*end` listener 脚本，均直调**公开 mutation**：①`nop-wf-service` 测试流 `approval-form/v1.xwf:20`（TestUseApprovalE2E WORKFLOW 模式全链）；②③`nop-metadata-service` 生产流 `metaDataContractApproval/v1.xwf` 与 `tagLabelConfirmApproval/v1.xwf`（各含 SUBMITTED 幂等检查与 oa.xwf disagree 护栏）。`approval-support.xbiz` 的 approve/reject/withdrawApproval/reverseApprove 四脚本纯翻转实体字段零流程感知。同一 mutation 既可被引擎回调（合法落点）也可被任意持有 admin 角色者直调（R1.12 后 auth=admin 收紧了"谁"，但没管"调了之后改什么"）。
- **metadata 两实体本地覆盖加重**：NopMetaDataContract/NopMetaTagLabel 的 approve/reject 是**本地覆盖**（自带 source，语义含状态生命周期推进）——base 文件加 gate 对这两个已部署且 wf 绑定的实体不生效，gate 必须同步进本地覆盖文件（B1 后果 B 的假闭包封堵）。
- **加重事实（审计复核举证）**：REJECTED 状态可再 submitForApproval 启动第二活跃实例（旧实例步骤成孤儿）；submitForApproval 前置检查接受 UNSUBMITTED/REJECTED/null（approval-support.xbiz:20）。
- **引擎写通道零存在**：WorkflowEngineImpl grep approveStatus 零命中——引擎从不直接写业务实体，唯一落点就是 notifyResult 回调。
- **绑定读取路径**：引擎经 `wfModel.getBizEntityFlowIdProp()`（xwf 模型属性）在启动时把 wfId 写到实体属性（AbstractWorkflowStore.bindBizEntityFlowId:43-46，BeanTool.setComplexProperty）。读侧 gate 需按同名模型属性从实体取回 flowId。
- **P1-3.1-002 阻断链**：`WfTaskScanner.newSchedulerContext()`（:150-154）设固定虚拟身份 `userId=wf-scheduler`（context 未设 roles）→ `workflowService.invokeActionAsync(request, null, schedulerCtx)` → `WorkflowStepImpl.invokeAction:228` 先 `allowCallByUser(ctx)` → `WorkflowEngineImpl.allowCallByUser:1151` owner/actor/delegate 四路径对 "wf-scheduler" 全 false → `ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER` → scanner 仅 WARN 跳过（scanDueTasksInCurrentSession :103-110 的"预期形态"warn）。xdef dueAction「自动触发」承诺对唯一目标场景结构性永不生效。**当前 scanner 的 WARN+continue 是阻断的掩蔽不是修复**。**该 WARN 分支无既有测试锚定**（TestWfTaskScanner 只断言 ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 分支与 scheduler userId，plan 审查 A1 勘正）——修复后须新增正/负路径断言。
- **dueAction 触发主体是系统**：动作名来自 xwf 模型 stepModel.getDueAction()（非用户输入），爆炸半径=流程定义自身声明的超时动作。
- **既有测试布景**：nop-wf-service `TestUseApprovalE2E`（DIRECT 模式直调 approve/reject 全链+WORKFLOW 模式经 oa.xwf 全链）+ `TestApprovalFlowHelper`（nop-wf-core）+ `TestUseApprovalComponents`（对 wf-approval.xlib 的 notifyResult 标签做结构性存在断言——死标签处置须与此测试一致）。scheduler 侧扫描测试在 nop-wf-scheduler（仅覆盖 ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 分支与 scheduler userId 断言，WARN 分支无锚定）。
- **R1.12 已交付**：approval-support 五 mutation 带 `<auth roles="admin"/>`（合并终值断言+enforcement 在位）——本 plan 的 gate 在 auth 之后的状态机层。
- **IBizObject.getAction 分发序**：先查 operations（对外 query/mutation）再查 actions（内部 action）——内部 action 可经 `entityBizObj.invoke('x')` 到达且不入 operations（不暴露 RPC）（BizObjectImpl:171-176）。

## Goals

- P1-3.1-001：实体绑定的 wf 活跃（未结束）期间，approve/reject/withdrawApproval/reverseApprove 公开 mutation 直调被响亮拒绝（新错误码）；引擎回调经 notifyResult 改走内部 action 专用通道（不经公开 mutation）；submitForApproval 在绑定 wf 未结束时拒绝重提交（封死第二活跃实例）。
- P1-3.1-002：dueAction 超时触发对 scheduler 身份真实生效——系统触发标记（服务端内部 context attr，外部不可伪造）使 allowCallByUser 对携带标记的调用放行；无标记的用户路径行为不变。
- owner doc（workflow-configuration.md）登记：审批落点契约（引擎通道 vs 直调窗口）、dueAction 系统触发语义、wf-scheduler 保留身份说明。
- 受影响模块（nop-wf-core/nop-wf-service/nop-wf-scheduler）测试全绿。

## Non-Goals

- P2-3.1-001（job yaml invoker.bean 失配）、P2-3.1-004（remind 链三重死）、P2-3.1-005/006（加签/转办）——台账行，非本 plan 交付物（advisory 互引：P1-3.1-002 修门后超时功能整体生效仍需 P2-3.1-001 bean 修复，登记于 owner doc 开启前置）。
- 不改 WorkflowEngineImpl allowCallByUser 对普通用户路径的既有语义（owner/actor/delegate 判定不动）。
- 不改 approval-support 既有生命周期语义（SUBMITTED 前置校验的接受集合不变）——本 plan 仅新增 wf 活跃维度的 gate 与重提交守卫（显式开口：守卫在既有状态检查之外叠加绑定 wf 未结束条件，不改动既有条件）。
- 不动 demo 模块与 nop-wf-meta 设计器面。

## Scope

### In Scope

- `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/base/approval-support.xbiz`：四 mutation 加 wf 活跃 gate；submitForApproval 增绑定 wf 未结束守卫
- `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/model/NopMetaDataContract/NopMetaDataContract.xbiz`、`NopMetaTagLabel/NopMetaTagLabel.xbiz`：本地覆盖 approve/reject 同步 gate（B1 封堵）
- `nop-wf/nop-wf-core/.../support/ApprovalFlowHelper.java` + `NopWfCoreConstants`/`NopWfCoreErrors`：引擎回调标记包裹函数（withEngineCallback，零新依赖——不得给 wf-core 引入 nop-biz）+ 标记常量 + gate 错误码注册
- 3 处 listener：`nop-wf/nop-wf-service/src/test/resources/_vfs/nop/wf/test/approval-form/v1.xwf`、`nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/wf/metaDataContractApproval/v1.xwf`、`tagLabelConfirmApproval/v1.xwf`（invoke 行改经 helper；幂等/护栏逻辑不动）
- `nop-wf/nop-wf-core/src/main/resources/_vfs/nop/wf/xlib/wf-approval.xlib`：死标签 notifyResult 保留并勘注 deprecated（**不删除**——TestUseApprovalComponents 结构性断言其存在，删除会打红该测试且属无谓破坏）
- `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java`：allowCallByUser 系统触发标记放行（标记常量入 NopWfCoreConstants）
- `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java`：newSchedulerContext 设系统触发标记；同步修订 WARN 分支语义（阻断 warn 改为不再出现的防御性保留或删除——按测试同步裁定）
- 测试：wf-service E2E 扩展（活跃 wf 直调拒绝+引擎通道继续工作+REJECTED 重提交守卫）、scheduler 侧 dueAction 触发正路径测试、既有 DIRECT 模式回归
- owner doc：workflow-configuration.md

### Out Of Scope

- P2-3.1 全部条目（见 Non-Goals）
- nop-wf-meta / demo / 设计器面
- allowCallByUser 的委托语义重构

## Execution Plan

### Phase 1 - P1-3.1-002：dueAction 系统触发通道（独立面先行；与 Phase 2 无执行依赖，顺序取其小步合入）

Status: completed
Targets: `WorkflowEngineImpl`、`NopWfCoreConstants`、`WfTaskScanner`、nop-wf-scheduler 测试

- Item Types: `Fix`、`Proof`

- [x] NopWfCoreConstants 增系统触发标记常量（如 `ATTR_WF_SYSTEM_ACTION_TRIGGER`）；WfTaskScanner.newSchedulerContext 在 context 上设该标记（服务端内部 attr，RPC 入口无用户可达写路径——实施时核实 IContext attr 的外部入口面并在测试中断言）——标记设于内层 IContext（与 userId 同层），引擎读取同层对齐
- [x] `WorkflowEngineImpl.allowCallByUser`：方法入口处对携带系统触发标记的 ctx 直接放行（其余 owner/actor/delegate 路径不动）
- [x] WfTaskScanner 的 `ERR_WF_NOT_ALLOW_CALL_ACTION_BY_USER` WARN 分支语义同步：修门后该错误不应再出现于 dueAction 路径——保留 catch 作为防御（并发步骤状态变迁竞态下 invokeAction 前置校验仍可能拒绝）并勘正注释（该分支无既有测试锚定，A1）
- [x] 测试：TestWfTaskScanner 增标记携带断言（scanner context 必须携带 ATTR_WF_SYSTEM_ACTION_TRIGGER，7/7）；TestWorkflowEngine 同一被拒 ghost-actor 步骤在带标记 context 下放行、无标记仍拒（39/39）——allowCallByUser 层正负路径锚定
- [x] **接线验证**：scanner 测试断言 context 携带标记（scanner→service 链）+ 引擎测试断言同链终点 allowCallByUser 放行（两段合计覆盖完整链；正路径真实动作执行由 WORKFLOW 模式 E2E 全链背书）

Exit Criteria:

- [x] 带系统标记的 dueAction 调用在 allowCallByUser 放行且动作真实执行（正路径：标记断言+WORKFLOW E2E 动作执行背书）
- [x] 无标记用户路径行为零变化（ghost-actor 步骤无标记仍拒，回归锚定）
- [x] nop-wf-scheduler 7 + nop-wf-core 47 既有测试全绿
- [x] owner doc 处置归 Phase 3（已落地：dueAction 系统触发小节）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1-3.1-001：审批 gate + 引擎回调标记通道（B1 修订设计）

Status: completed
Targets: `approval-support.xbiz`、`NopMetaDataContract.xbiz`/`NopMetaTagLabel.xbiz`（本地覆盖）、`ApprovalFlowHelper`（wf-core support）、3 处 listener 脚本、nop-wf-service E2E 测试、`NopWfCoreErrors`（错误码注册）

- Item Types: `Fix`、`Proof`

- [x] **错误码注册**（A7）：NopWfCoreErrors 注册 ERR_WF_APPROVAL_FLOW_ACTIVE_LOCK（含 wfId/bizObjName/bizObjId 诊断参数），测试断言精确错误码
- [x] **引擎回调标记机制**（B2 形态裁定落地注记：plan 原定 withEngineCallback 闭包形态在 xpl listener 解析中不可用——XScript 函数表达式字面量未过 xdef xpl stdDomain 解析，实测 3 用例全挂；**裁定改为 listener 内联 try/finally 标记窗口**——set ATTR_WF_ENGINE_CALLBACK→invoke→finally remove，作用域同样收敛单次回调，wf-core 零新依赖、零 helper 方法；E2E 实测通过。标记常量与 Phase 1 分立；RPC 入口无用户可写路径）
- [x] **3 处 listener 改道**：①nop-wf-service 测试流 `approval-form/v1.xwf`、②③metadata 生产流两 v1.xwf——approve invoke 行改为内联 try/finally 标记窗口（保留各 listener 既有 SUBMITTED 幂等检查与 disagree 护栏逻辑不动）
- [x] **wf 活跃 gate**：approval-support.xbiz 四 mutation——approve/reject（allowEngineCallback=true）/withdraw/reverseApprove（无条件）。gate 实现为 `ApprovalFlowHelper.checkApprovalFlowLocked`（全 wf-core 可见类型签名：IWorkflowManager/String/Object/IServiceContext/boolean——B2 分层约束满足，未引入 nop-biz 依赖）；绑定 flowId 已被清理（历史遗留）视为非活跃放行
- [x] **metadata 本地覆盖同步 gate**（B1 后果 B 封堵）：NopMetaDataContract.xbiz/NopMetaTagLabel.xbiz 本地 approve/reject source 增同一 gate（本地文件补）
- [x] submitForApproval 增守卫：绑定 wf 存在且未结束时拒绝重提交（allowEngineCallback=false 无条件；REJECTED 且旧 wf 已结束的正常重提交保持）
- [x] 测试（TestUseApprovalE2E 扩展 testActiveFlowLocksDirectApprovalMutations）：①活跃 wf 期间直调 approve/reject/withdraw/reverseApprove/submitForApproval → 精确错误码；②WORKFLOW 模式全链（listener 内联标记回调）绿；③disagree 路径绿；④重提交守卫并入①断言；⑤DIRECT 模式既有用例绿；⑥metadata 侧 TestNopMetaDataContractBizModel 审批守卫族+全量 1345 绿（flow 级 listener 未被既有集成测试执行，其 marker 窗口逻辑与测试流同构且经②验证）
- [x] **端到端验证**：②③即端到端——submitForApproval 入口→xwf 步骤动作（agree/disagree）→*end listener 标记窗口回调→gate 放行→实体终态（TestUseApprovalE2E 8/8 全绿）

Exit Criteria:

- [x] 活跃 wf 直调四 mutation 全部被精确错误码拒绝（base 侧测试断言；metadata 本地覆盖 gate 与 base 同一 helper 调用、经模块全量回归验证解析与执行）
- [x] 引擎回调经标记窗口仍真实落点（WORKFLOW 模式全链测试绿；metadata 流 listener 同构改道+模块全量绿）
- [x] DIRECT 模式直调窗口零回归（既有 E2E DIRECT 用例绿）
- [x] 第二活跃实例路径封死（重提交守卫并入锁定测试断言绿）
- [x] nop-wf-core 47 + nop-wf-service 141（含 R1.12 的 7 用例+新 1 用例）+ nop-wf-scheduler 7 + nop-metadata-service 1345 全绿（含 TestApprovalAuthMergedDefinition/TestApprovalActionAuthEnforcement/TestNopMetaDataContractBizModel 复跑）
- [x] owner doc 处置归 Phase 3（已落地）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner doc 同步 + 收口

Status: completed
Targets: `docs-for-ai/02-core-guides/workflow-configuration.md`

- Item Types: `Fix`

- [x] workflow-configuration.md：新增「审批落点契约（approval-support 双事实源收口）」小节（五场景矩阵+受派人校验分层+绑定读取链+listener 标记窗口写法+本地覆盖同步注意点+notifyResult deprecated）与「步骤超时 dueAction 的系统触发」小节（标记机制+开启前置=P2-3.1-001 台账行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap/index/daily log 收口动作落盘（roadmap R1.13 翻 done、index P1-3.1-001/002 → fixed、MR 表 R1.13 行）

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.1-001 + P1-3.1-002）
- [x] 行为/契约结果已达成（gate 拒绝断言+引擎通道端到端+dueAction 正路径）
- [x] 必要 focused verification 已完成（nop-wf-core 47/nop-wf-service 141/nop-wf-scheduler 7/nop-metadata-service 1345 全绿）
- [x] 不存在被静默降级的 in-scope live defect（P2-3.1 条目属台账行非本 plan 域）
- [x] 受影响 owner docs 已同步（workflow-configuration.md 两小节）
- [x] 独立子 agent closure-audit 已完成并记录证据（closure-audit agent_adbb6093，Can Close，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证（a）TestUseApprovalE2E.testActiveFlowLocksDirectApprovalMutations 真实直调拒绝+WORKFLOW 全链标记窗口真实穿透；（b）TestWfTaskScanner 标记断言+TestWorkflowEngine ghost-actor 双锚——scanner→service→engine 链两段覆盖
- [x] `./mvnw test -pl nop-wf/nop-wf-core,nop-wf/nop-wf-service,nop-wf/nop-wf-scheduler -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-wf-core --severity high` 退出码 0

## Deferred But Adjudicated

（无——P2 条目属台账行非本 plan 域，不构成本 plan 的 deferred 项）

## Non-Blocking Follow-ups

- P2-3.1-001/004/005/006 台账行修复（R1.0 P2 裁决通道）

## Closure

Status Note: P1-3.1-002（dueAction 系统触发通道）与 P1-3.1-001（审批双事实源 gate）全部交付：服务端标记机制（两常量分立、内层 IContext 同层读写、RPC 不可伪造）+ 绑定 flowId 活跃判定 gate（approve/reject 标记放行、withdraw/reverse 无条件、submit 重提交守卫）+ metadata 两本地覆盖同步 + 3 处 listener 标记窗口改道。执行裁定（withEngineCallback 闭包→内联 try/finally 标记窗口）与文件拼接事故修复均透明记录。三 Phase 全 completed，独立 closure audit Can Close。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: closure-audit agent_adbb6093（fresh read-only session）
- Evidence:
  - Req1 Phase/checkbox：Phase 1-2 全勾；Phase 3 两处 bookkeeping 残留勾（work 已核实完成），PASS
  - Req2 live-code：NopWfCoreConstants.java:47/:55 双常量、WfTaskScanner.java:151-158 内层 IContext 标记、WorkflowEngineImpl.java:1151-1158 入口放行、NopWfCoreErrors.java:276-278 错误码、ApprovalFlowHelper.java:87-121 gate 全链（7 个原方法全在——拼接事故修复核实）、approval-support.xbiz 5/5（allowEngineCallback 标志正确）、metadata 两覆盖 4 处 gate、3 listener try/finally 窗口（幂等/disagree 护栏未动）、xlib deprecated 未删+TestUseApprovalComponents 4/4，PASS
  - Req3 Anti-Hollow：TestWfTaskScanner.java:107-108 标记断言+TestWorkflowEngine.java:749,753-754 双锚；TestUseApprovalE2E.java:332-357 五动作精确错误码拒绝+fullChain :212-220 实体 APPROVED 穿透断言，PASS
  - Req4 测试证据：surefire（2026-10-10 02:02-02:03，晚于最新源码编辑）wf-core 47/0、wf-service 141/0（E2E 8/8、engine 39/39）、scheduler 7/0、metadata 1345/0，PASS
  - Req5/6/7/8：一致性（roadmap/index/MR/log/owner doc+三工具退出码 0）、裁定诚实性（plan L91+log 双记录）、262 行变更零 hollow/no-op，PASS
  - Advisory（不阻塞）：两处注释漂移（xlib:13,15/constants:51 的 withEngineCallback 残留命名）已在收口 pass 修正；Phase 3 bookkeeping 勾选随本 Closure 落盘
  - 工具退出码：check-plan-checklist --strict=0；scan-hollow-implementations（nop-wf-core）=0；check-doc-links --strict=0

Follow-up:

- P2-3.1-001/004/005/006 台账行修复（R1.0 P2 裁决通道，dueAction 整体生效开启前置已登记 owner doc）
- 无其余 plan-owned 剩余工作
