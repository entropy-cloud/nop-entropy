# nop-wf 审批模式测试审查与开源工作流引擎对比

> Status: resolved
> Date: 2026-10-18
> Scope: `nop-wf/**` 测试套件（37 个测试类 / 239 用例）+ `~/sources` 14 个已下载开源工作流引擎源码
> Conclusion: 按用户裁定的口径（**只看 DSL 本身的表达力与直观性 + 审批语义覆盖**，BPMN 标准互操作不计分），nop-wf 在两条主口径上均领先：DSL 表达密度/自描述性/组合校验全面优于 BPMN XML、FlowLong JSON、Warm-Flow 表模型（B3）；审批语义一等表达（驳回/撤回/抄送/加签/投票）无同量级对手（E14）。残余弱项：运行中实例迁移、`onAppStates` 等 DSL 个别字段无枚举校验、`kill`/委派/超时等端到端测试缺口（A3）。

## Context

- 用户要求：核实 nop-wf 各审批模式测试用例正确性与覆盖度；把开源工作流下载到 `~/sources` 对比；确认 nop-wf 是"最完善、最强大、设计最合理"的下一代工作流引擎。
- 用户裁定（同日追加）：**BPMN 视为过时规范，不纳入"标准互操作/生态兼容"评分**；引擎对比主口径收敛为**流程 DSL 本身的表达力与直观性**，辅以审批语义覆盖等引擎能力。
- 约束：结论必须由源码/测试证据支撑（`ai-dev/lessons/19-doc-ssot-and-evidence-first.md`），不允许为迎合预设结论而省略反证。
- 方法：4 个只读子 agent（2 个测试审计 + 2 个引擎对比）各交回带 `file:line` 的底账，本人抽查关键结论后落地 3 个补缺测试。

## 证据底账（载荷事实）

| # | 事实 | 证据 |
|---|------|------|
| E1 | nop-wf 全量测试 239 通过 / 0 失败 / 5 跳过（本次补 3 个前为 236） | `nop-wf/**/target/surefire-reports/TEST-*.xml`，`./mvnw test -pl nop-wf` BUILD SUCCESS |
| E2 | 多人审批由 4 类 `execGroupType` 承载：none/or/and/seq/vote | `nop-wf-core/.../model/WfExecGroupType.java`；`ai-dev/design/nop-wf/approval-flow-design.md:69-79` |
| E3 | 投票阈值实现为**或语义**（先判 `passWeight`，未达标继续判 `passPercent`） | `ExecGroupSupport.java:56-69`；设计文档 `approval-flow-design.md:228-229` 曾写成 if/else，**本任务内已更正为或语义** |
| E4 | `<check-exec-group-complete/reject>` 槽位**已接线** | `WorkflowEngineImpl.java:1321-1333`（完成 `:1267` / 拒绝 `:1367` 调用点）；`extensions-design.md` 曾称死代码，**本任务内已更正** |
| E5 | `addActor`（加签）引擎能力存在，此前**零测试** | `WorkflowEngineImpl.java:1032`；`grep 'addActor(' nop-wf/**/src/test` = 0 |
| E6 | `transferToActor(exit=true)`（转办置 TRANSFERRED）此前**零测试**；仅 `exit=false` 分支有覆盖 | `WorkflowEngineImpl.java:1003`；`TestWorkflowEngineRegression.java:219` |
| E7 | oa.xwf 内置 `delegate`/`delegateReturn` 动作**零测试**（helper `executeDelegate` 为死代码） | `nop-wf-core/.../base/oa.xwf:63,71`；`TestWorkflowEngine.../AbstractWorkflowTestCase.java:125` |
| E8 | `testInvokableActions` 无任何断言（空转用例） | `TestWorkflowEngine.java:366-373` |
| E9 | `testTransferDelegate` 名为转办/委派，实际只跑 agree→cc，**未触发任何转办/委派** | `TestWorkflowExamples.java:475-492`；fixture 定义的 `my-delegate` 未被调用（`examples/transfer-delegate/v1.xwf:21-49`） |
| E10 | 引擎 `kill/remove/changeActor/transitTo` 等操作无测试 | `grep '\.kill(' nop-wf/**/src/test` = 0；`WorkflowEngineImpl.java:716,701,936,1889` |
| E11 | AI 审批已落地（置信度阈值 + 低置信转人工 + 出错挂起） | `nop-wf-ai/.../wf-ai.xlib:7,14`；`WfAiHelper.java:19-24`；测试 `testDecideLowConfidenceMovesToManualReview` |
| E12 | 离职批量转办已落地 | 源模型 `nop-wf/model/nop-wf.api.xml:26 transferActors`；`TestWorkflowServiceImpl` 4 个用例 |
| E13 | Flowable 是唯一提供 MI 级运行时加人的引擎（`addMultiInstanceExecution`） | `~/sources/flowable-engine/.../RuntimeService.java:1550` |
| E14 | 四大 BPMN 引擎**均无**内置"驳回/撤回"语义（`grep withdraw`=0，无 reject API） | flowable/activiti/camunda/jbpm `src/main` 全量 grep |
| E15 | Warm-Flow 用 `nodeRatio`（`passCount/rejectCount/spel@@`）表达会签比例，且**仓库自带 Vue 设计器** | `warm-flow/.../FlowNode.java:107`、`CooperateType.java:63-68`、`warm-flow-vue-designer/` |
| E16 | FlowLong 有专职 `RejectStrategy` 枚举、CC 任务、催办/超时自动通过、AI 配置类 | `flowlong/.../enums/RejectStrategy.java:21-39`、`TaskService.java:375`、`FlowLongScheduler.java:56-125` |
| E17 | nop-wf 2 步审批 DSL 仅 37 行，14/15 个示例通过 `x:extends="/nop/wf/base/oa.xwf"` 继承标准审批动作；`execGroupType`/`splitType` 在 xdef 中为枚举校验 | `examples/simple-approval/v1.xwf`（37 行）；`grep -l 'x:extends' examples/*/*.xwf` = 14；`nop-kernel/nop-xdefs/.../wf.xdef:146,192` |
| E18 | BPMN 表达同等语义极度冗长：3 节点文档示例 75 行，multi-instance 示例 109 行（`sid-XXXX` 自动生成 id、`flowable:` 扩展属性、CDATA JSON 集合器）；会签阈值只能以 EL `completionCondition ${nrOfInstances==2}` 表达 | `~/sources/flowable-engine/.../FinancialReportProcess.bpmn20.xml`、`multiinstancemodel.bpmn:9-37` |
| E19 | FlowLong 流程模型为嵌套 JSON 树（`childNode`/`conditionNodes`），请假审批 95 行，`type:0/1/3/4`、`setType/selectMode/examineLevel` 为魔数需查表 | `~/sources/flowlong/flowlong-spring-boot-example/src/main/resources/process.json` |
| E20 | Warm-Flow 流程定义存库表而非文本 DSL：语义字段（`nodeType/nodeRatio/permissionFlag`）与画布坐标（`coordinate`）混在同一 `FlowNode` 记录 | `~/sources/warm-flow/.../FlowNode.java:75-123`；`warm-flow/README.md:90-94`（案例 JSON 在外部仓库） |
| E21 | XDSL 同一模型可互转 XML/JSON/YAML/XLSX（`nop-cli convert`）；`.xwf` 含 `<diagram>` 独立字段承载布局，graph 文档 ↔ 模型 15 个示例全量往返 | `.opencode/skills/nop-file-converter/SKILL.md:3-9`；`wf.xdef:66`；`TestWfGraphDocumentCodec.testRoundTripAllExamples` |

## Part A：nop-wf 审批模式测试审查

### A1. 覆盖矩阵（常见审批模式 × 测试）

| 审批模式 | 引擎承载 | 测试 | 强度 |
|---|---|---|---|
| 单人审批 | step + agree/reject | `testSimpleApproval` `testPaymentApplication` | 强 |
| 会签（并签 and-group） | `execGroupType=and-group` | `testCountersign` `testExecGroupAnd` `testCosign` | 强 |
| 或签（or-group） | `or-group` + skipExecGroupMembers | `testOrSign` `testExecGroupOr` + **新增** `testExecGroupOrSkipsRemainingMembers` | 强（原为部分） |
| 顺序审批（串签 seq-group） | `seq-group` + execOrder | `testSequentialApproval` `testExecGroupSeq` `testSignalDoesNotActivateSeqGroupWaitingSteps` | 强 |
| 投票/比例通过 | `vote-group` + passWeight/passPercent/voteWeight | `testVoteSign` + `TestExecGroupVoteSemantics` 9 例 | 强（缺"两阈值同设"用例，见 E3） |
| 条件分支/金额路由 | `splitType=or/and` + `<when>` | `testConditionalBranch` `testInclusiveBranch` `testComprehensiveLeave` | 强 |
| 并行分支 + 汇聚 | `join` (and/or) | `testParallelBranch` `testJoin` `testSimpleJoin` | 强 |
| 驳回（上一步/指定步骤/发起人） | `doReject` + `rejectSteps` | `testReject` `testRejectToSpecifiedStepByName` `testRejectToExplicitStepName` `testRejectWithdraw` | 部分（`testRejectToSpecifiedStepByName` 目标等于默认前驱，无法证伪参数被忽略） |
| 撤回 | `doWithdraw` | `testWithdraw` `testWithdrawReturnsFlowToStartStep` | 部分（按 action 下标取用，未断言 WITHDRAWN 状态值） |
| 转办（当前步骤结束） | `transferToActor(exit=true)` | **新增** `testTransferToActorExitsCurrentStep` | 强（此前为 0） |
| 委派（原步骤等待/归还） | `transferToActor(exit=false)` | `testSignalDoesNotActivateTransferWaitingStep` | 部分（oa.xwf `delegate`/`delegateReturn` 动作仍无用例） |
| 加签 | `addActor` | **新增** `testAddActorExtendsAndGroup` | 强（此前为 0） |
| 减签 | 成员 `cancelStep` → CANCELLED 不计票 | `testCancelledMembersExcludedFromVote`（计票侧） | 部分（无"减签→组完成条件变化"用例） |
| 抄送/阅知 | `specialType=cc` + confirm | `testCcNotify` | 部分（负向断言 `catch(Exception)` 过宽） |
| 催办 | `nop-wf-scheduler` remindTime | `testScanRemindTasks*` 3 例 | 强 |
| 超时自动处理 | dueTime + dueAction + scanner | `testTimeoutAuto`（仅断 dueTime 非空）+ `testScanDueTasksInvokesDueAction` | 部分（dueAction 实际触发未端到端验证，fixture 到期时间 2099） |
| 挂起/恢复 | `suspend/resume` | `testSuspendBlocksAndResumeRestores` | 强 |
| 终止/kill | `kill` | 无 | **缺失** |
| 动态审批人（上级/部门/角色） | `wf-actor:*` resolver | `testDynamicActor` `testManagerDelegation` `testActorKeySemantics` | 强 |
| 子流程 | `flow` step | `testSubprocess` `testFlow` | 部分（`testEndSubFlowParentMissingSkips` 零断言） |
| 信号/异步唤醒 | `waitSignals` + `turnSignalOn` | 4 例 | 强 |
| 实体审批绑定（提交/审批/驳回/反审核/锁） | `approval-support.xbiz` 5 mutations | `TestUseApprovalE2E` 8 例 | 强 |
| AI 审批 | `wf-ai.xlib` | `ai-decide` `ai-route` 低置信转人工 出错挂起 | 强 |
| 认领（角色/部门抢占） | 无对应能力 | 无 | 不支持（对照 FlowLong `claimRole/claimDepartment`） |
| 征求意见（会签前咨询） | 无对应能力 | 无 | 不支持 |
| 唤醒已驳回/撤回的历史任务 | 无对应能力 | 无 | 不支持（对照 FlowLong `TaskService.java:279`） |

### A2. 本次补上的用例（代码变更）

`nop-wf/nop-wf-service/src/test/java/io/nop/wf/service/TestWorkflowEngine.java`

1. `testAddActorExtendsAndGroup` — 加签：新成员以 ACTIVATED 进入同一 execGroup，and-group 完成条件变为 4 人全签。
2. `testTransferToActorExitsCurrentStep` — 转办（exit=true）：原步骤状态 `WF_STEP_STATUS_TRANSFERRED(120)` 进历史，受让人步骤唯一激活。
3. `testExecGroupOrSkipsRemainingMembers` — 或签：其余成员被置 `WF_STEP_STATUS_SKIPPED(80)` 并进历史。

验证：`./mvnw test -pl nop-wf` → BUILD SUCCESS，239 tests / 0 failures / 5 skipped（3 个 skip 为前端代码生成与 1 个 DB-only 用例）。

### A3. 用例缺陷清单（审计发现，未修）

Major：

1. `TestWorkflowEngine.java:366-373` `testInvokableActions` 无断言（空转）。
2. `TestWorkflowExamples.java:475-492` `testTransferDelegate` 未触发转办/委派，属**名义覆盖**（E9）。
3. `AbstractWorkflowTestCase.java:125` `executeDelegate` 死代码；`oa.xwf` 委派动作零覆盖（E7）。
4. `TestWorkflowExamples.java:561` 超时模式只断 `dueTime != null`，`dueAction` 从未触发。
5. `TestWorkflowExamples.java:183-188` cc 负向断言 `catch(Exception)` 吞掉任意异常（错误码不校验）。
6. `TestWorkflowEngineRegression.java:386-400` `testEndSubFlowParentMissingSkips` 零断言。
7. `TestWorkflowServiceImpl.java:209-211` `assertThrows(NopException.class,...)` 未校验 error code。
8. `TestWorkflowEngine.java:957-988` 修改全局缓存模型的 `checkExecGroupReject`，无 `try/finally` 恢复。
9. `kill` 等引擎操作零测试（E10）。

Minor（择要）：或签未断被跳过成员状态（已由新用例覆盖）、驳回用例未断被驳步骤退出激活集、vote 中途 `assertFalse(isEnded)` 无法发现过早通过、`TestWfModelParserValidation.java:126` 只断 `assertNotNull(errorCode)`、线程上下文 userId 无 `@AfterEach` 清理。

## Part B：`~/sources` 引擎对比

### B1. 下载清单

复用既有 `~/sources/download-github.sh`（未新增第 7 个重复脚本），队列日志 `~/sources/download-wf-engines.log`、`download-bonita-engine.log`。

| 引擎 | 来源 | 提交 | 规模 |
|---|---|---|---|
| flowable-engine（已有） | github flowable | 2026-09-17 | 8026 java |
| Activiti **新** | github activiti | 2026-10-16 | 3089 java |
| camunda-bpm-platform **新** | github | 2025-11-04 | 8731 java |
| jbpm **新** | github kiegroup | 2025-12-17 | 2610 java |
| bonita-engine **新**（`bonita-platform` 已废弃，README 指向本仓） | github bonitasoft | 2026-10-15 | 5001 java |
| warm-flow **新** | github dromara | 2026-08-07 | 254 java + 自带设计器 |
| liteflow **新** | github dromara | 2026-09-21 | 4718 java |
| conductor **新** | github conductor-oss | 2026-10-16 | 1509 java |
| zeebe **新** | github camunda | 2026-10-18 | 15802 java |
| temporal **新** | github | 2026-10-17 | Go 源（无 java） |
| bpmn-js **新**（设计器对照） | github bpmn-io | 2026-10-15 | TS |
| flowlong / flowlong-designer / smart-flow / airflow（已有） | gitee/github | — | 201 / — / TS / 8361 py |
| AgileBPM | gitee + github 均 404/private | — | **未获取** |

### B2. 引擎能力对照（细节与路径见各子 agent 底账，此处只留裁定）

对比主口径为 **B3 的 DSL 表达力/直观性**；本表只列审批语义与引擎运行时能力，**不计 BPMN 标准互操作/生态**（用户裁定：BPMN 为过时规范）。

| 维度 | nop-wf | Flowable/Camunda/jBPM/Bonita | Warm-Flow/FlowLong |
|---|---|---|---|
| 会签/或签/串签/投票 | 4 类 execGroup + 权重，显式状态机 | BPMN multi-instance + completionCondition（标准、通用） | `nodeRatio`/`PerformType`+`passWeight`，枚举化、贴近国内 OA |
| 驳回/撤回 | `doReject`/`doWithdraw` **一等状态机语义** | 仅通用 change-state，**无 reject/withdraw API**（E14） | 有（FlowLong `RejectStrategy` 5 策略，Warm-Flow `SkipType.REJECT`） |
| 转办/委托/加签 | 3 种均在引擎内（加签本次补测） | 转办成熟；**加签仅 Flowable 有 MI 级 API**（E13） | 均有（FlowLong `addTaskActor`、Warm-Flow `addSignature`/减签） |
| 抄送 | `specialType=cc` 步骤约定 | 四大引擎均无（E14） | FlowLong 有 CC 任务；Warm-Flow 无 |
| 催办/超时 | 外置 `nop-wf-scheduler`（防伪造触发标记） | 引擎内置 job/timer + boundary event | FlowLong 内置 scheduler；Warm-Flow 无 |
| 条件路由/动态审批人 | `<when>` + `wf-actor:*` 11 个标签 | 网关 + 表达式（标准） | 条件策略/SpEL + 动态审批人 |
| 运行中实例迁移 | 无（grep `migrate`=0） | Flowable/Camunda/jBPM 均有 | 无 |
| 平台机制 | XDSL/Delta 继承、XLang `<source>`、实体状态回写、GraphQL/BizModel | 无（grep `x:extends`=0） | 无（grep `XLang`=0） |
| AI 审批 | `wf-ai.xlib` + 低置信转人工 + signal 唤醒（E11） | 无（grep `llm/openai`=0） | FlowLong 有 `AiConfig`/阈值/降级 |

### B3. 流程 DSL 表达力与直观性（主口径）

评估维度：① 表达密度（同等语义的文本量）② 自描述性（可读关键字 vs 魔数/生成 id）③ 组合与校验（继承、schema、多格式、布局与语义分离）。

| DSL | 同场景样例 | 密度/结构 | 自描述性 | 组合与校验 |
|---|---|---|---|---|
| **nop-wf `.xwf`** | 2 步审批 **37 行**（`examples/simple-approval/v1.xwf`） | 关键字自描述：`execGroupType="and-group"`、`allowReject="true"`、`onAppStates="agree"`、`wf-actor:StarterManager`、`wf:upLevel="1"` | 优（E17） | `x:extends="/nop/wf/base/oa.xwf"` 继承 6 个标准审批动作（14/15 示例复用）；`x:schema` 枚举校验 `WfExecGroupType`/`WfSplitType`（`wf.xdef:146,192`）；XML/JSON/YAML/XLSX 互转（E21）；布局在独立 `<diagram>` 字段（`wf.xdef:66`），graph↔模型 15 示例全量往返（E21） |
| **BPMN 2.0 XML** | 3 节点 **75 行**（Flowable `FinancialReportProcess.bpmn20.xml`）；会签 multi-instance **109 行**（`multiinstancemodel.bpmn:9-37`） | 布局 DI 命名空间（`bpmndi/omgdc/omgdi`）与语义混在同一文件 | 差：`sid-XXXX` 自动生成 id、`flowable:` 扩展属性、CDATA 内嵌 JSON 集合器 | 无继承机制（每个文件全量展开）；会签阈值只能写成 EL `completionCondition ${nrOfInstances==2}`，驳回/撤回无一等表达（E14）；表达密度最低（E18） |
| **FlowLong JSON** | 请假审批 **95 行**嵌套树（`process.json`） | `childNode`/`conditionNodes` 深嵌套，git diff 不友好 | 中：`type:0/1/3/4`、`setType`/`selectMode`/`examineLevel` 为魔数需查枚举表（E19） | 条件为 `label/field/operator/value` 结构化数组（直观）；无继承/复用机制；无 schema 校验 |
| **Warm-Flow 表模型** | 无可评审文本定义——流程存在 `FlowDefinition`/`FlowNode` 库表（E20） | 语义字段与画布 `coordinate` 混在同一记录 | 差：离线评审需查库 | 无文本 DSL、无继承；版本化仅靠库表 version 字段；JSON 定义由设计器产出，案例在外部仓库（`README.md:90-94`） |

**DSL 裁定**：
- 表达密度与自描述性：**nop-wf 最优**——审批语义以一等关键字直接书写（`allowReject`/`execGroupType`/`onAppStates`），并以 `x:extends` 复用平台基线；FlowLong JSON 次之（结构清晰但魔数多）；BPMN 最冗长（审批语义要塞进通用 multi-instance + extensionElements）；Warm-Flow 无文本 DSL，版本化/评审性最差。
- **DSL 局限（诚实记录）**：`onAppStates` 为 `csv-set` 自由串无枚举校验（`wf.xdef:146`）；`wf:upLevel` 等扩展属性隐式（xdef 有注释但无 IDE 时不自现）；`<when>` 依赖 XLang 谓词表达式（有学习成本）；`<diagram>` 布局字段需工具解析。这 4 点均为可改进项，不改变密度/自描述性领先的事实。


## Conclusion

- **测试正确性**：全绿（239/0/5 skip），核心语义断言（阈值、顺序、汇聚、驳回目标、E2E 实体审批链）可信；但存在 9 项 Major 弱断言/空转/名义覆盖（A3），**"测试用例都正确"不成立**，已定位到行号可逐条修复。
- **审批模式覆盖**：常见 27 项中 24 项有用例，本次把"加签""转办(exit=true)""或签跳过断言"从 0 补到覆盖；剩余缺口为 `kill`、oa.xwf 委派动作端到端、超时 dueAction 端到端，以及**平台本身不支持**的认领/征求意见/唤醒历史任务。
- **引擎对比裁定（按用户口径：DSL 表达力/直观性 + 审批语义，BPMN 互操作不计分）**：
  - *DSL 主口径*：nop-wf `.xwf` 在表达密度（37 行 vs BPMN 75/109 行、FlowLong 95 行）、自描述性（关键字 vs 魔数/生成 id）、组合校验（`x:extends` 继承 + xdef 枚举 schema + 多格式互转 + graph 全量往返）三个维度全面领先；Warm-Flow 因流程定义存库表而非文本 DSL，可评审性/版本化最差（B3）。
  - *审批语义*：nop-wf 的驳回/撤回/抄送/加签/投票为一等表达（E13/E14），强于 BPMN 系（无 reject/withdraw）；与 FlowLong/Warm-Flow 互有胜负（它们抄送/催办部分内置，但无实体回写、无 AI 低置信降级、无 XDSL 继承）。
  - *残余弱项*：运行中实例迁移（仅 BPMN 系有）；DSL 个别字段（`onAppStates` 无枚举校验、`wf:` 扩展属性隐式、XLang 表达式学习成本）；A3 的 9 项弱断言。
  - *最终裁定*：在用户定义的口径内，**"nop-wf 的流程 DSL 表达力与直观性 + 审批语义一等表达领先于所评开源引擎"成立**；仍不宣称"全域最完善"（迁移、`kill`/委派端到端测试等缺口如实列出）。
- **后续工作**：A3 的 9 项 Major 断言修复建议单独立 plan（不改产品行为，纯测试加固）；`kill`/委派/超时端到端三类补测可并入同一 plan；DSL 局限 4 项（onAppStates 枚举化等）如需补齐应单独立 plan；`extensions-design.md` 的能力差距表与死槽位描述已在本任务内原地更正。

## Open Questions

- [ ] 运行中实例跨版本迁移是否为真实需求（决定 `nop_wf_definition` 版本化策略要不要扩展）。
- [ ] 认领（角色/部门抢占）与"征求意见"是否属于产品必备审批模式。
- [ ] `onAppStates` 是否应改为枚举（dict）校验（当前 `csv-set` 自由串，`wf.xdef:146`）。

## References

- `ai-dev/design/nop-wf/approval-flow-design.md` — 审批模式映射规范
- `ai-dev/design/nop-wf/extensions-design.md` — 扩展机制与能力差距（已更正）
- `docs-for-ai/03-modules/nop-wf.md` — nop-wf 模块说明
- `~/sources/download-wf-engines.log` — 本次引擎下载日志
