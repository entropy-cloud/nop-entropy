# nop-wf 完善 Roadmap — 从「DSL 与审批语义领先」到「测试可信、模式齐全、可运维」

> Last updated: 2026-10-18（全 WI 未启动）
> 位置：按仓库 roadmap 惯例存放于 ai-dev/backlog/。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的文档路径用反引号，持续受 check-doc-links 保护。
> Sources:
> - `ai-dev/analysis/2026-10/2026-10-18-nop-wf-approval-test-audit-and-engine-comparison.md`（**主输入**：E1–E21 证据底账 + A1 覆盖矩阵 + A3 用例缺陷清单 + B3 DSL 对比 + Open Questions）
> - `ai-dev/design/nop-wf/approval-flow-design.md`（审批模式映射规范，投票阈值或语义已更正）
> - `ai-dev/design/nop-wf/extensions-design.md`（扩展机制现状：AI/调度器/批量转办/票签槽位已落地）
> - `docs-for-ai/03-modules/nop-wf.md`（owner doc）
> - 既有 wf 相关 plan 均已 completed（281/282/283/285/338/347/2297/2328/2379），无重复在途工作
> - 需求来源：2026-10-18 会话——审批模式测试审查 + 开源引擎对比；用户裁定 BPMN 不计互操作分、主口径为 DSL 表达力与直观性；随后「写一个 roadmap 完善 nop-wf」

## Purpose

本 roadmap 把 nop-wf 从「DSL 表达力与审批语义已领先同量级引擎」推进到「测试证据可信、审批模式补齐国内 OA 全家桶、DSL 可发现性强、运行时与可观测性可运维」。

覆盖五条主线：

1. **裁定与基线**（P0）：把对比分析遗留的 3 个 Open Questions 收敛为 go/no-go，并刷新 owner doc 功能概览。
2. **测试加固**（P0，纯测试）：修复 A3 的 Major 弱断言/空转/名义覆盖，补 `kill` 等引擎操作与双阈值/减签语义的端到端覆盖。
3. **审批模式补全**（P1，依裁定）：认领、征求意见、唤醒历史任务三个「平台不支持」模式。
4. **DSL 强化**（P1）：`onAppStates` 枚举化、`wf:*` 扩展属性与审批 DSL 参考表（补齐 B3 记录的可发现性局限）。
5. **运行时与收口**（P2）：实例迁移（依裁定）、wf 级可观测性、全量回归与文档收口。

**范围硬边界**（改动须先改本节）：

- **不做 BPMN/CMMN/DMN 导入导出与标准互操作**（用户 2026-10-18 裁定：BPMN 为过时规范，对比不计分，roadmap 不投入）。
- **不做运行时动态组装未知结构的审批链**（`extensions-design.md` §一.5 explicit non-goal；可参数化需求用既有三种等价模式）。
- **不自建第二套调度循环**：催办/超时归 `nop-wf-scheduler` + nop-job（设计已裁定，引擎不内置 timer）。
- **不重建待办中心/审批中心**：待办与审批入口复用 `WorkflowService` API 与 nop-auth actor resolver，本 roadmap 只补引擎与 DSL 能力。
- **引擎状态机语义不无故变更**：WI 均为补能力/补测试；凡改 `WorkflowEngineImpl`、`wf.xdef`（nop-xdefs）、`nop-wf.api.xml`、`model/*.orm.xml` 的项**显式 plan-first**（框架核心 / 跨模块公共 API / ORM Protected Area）。
- **不动 nop-wf 与 nop-task 的独立关系**（owner doc 已裁定 nop-wf 不基于 nop-task）。

不含实现细节。每个 WI 由独立执行计划承载（`ai-dev/plans/00-plan-authoring-and-execution-guide.md`）；roadmap 就绪后可经 mission-driver draft 生成 mission config 驱动（当前未立项 mission）。

## Evidence Ledger（证据底账，2026-10-18 闭合）

> 按 `ai-dev/lessons/19-doc-ssot-and-evidence-first.md`：先固化载荷事实与既有裁定，每条带 file:line。完整 E1–E21/A1/A3/B3 以上方 Sources 的分析文档为 canonical，此处只列 roadmap 直接依赖的载荷条目。

1. **测试基线全绿但含 9 项 Major 缺陷**：`./mvnw test -pl nop-wf` → 239 tests / 0 failures / 5 skipped；分析 A3 列出空转用例、名义覆盖、弱断言等 9 项 Major（file:line 齐备）。
2. **三类端到端缺口**：`kill` 等引擎操作零测试（`WorkflowEngineImpl.java:716,701,936,1889`）；oa.xwf `delegate`/`delegateReturn` 动作零测试（`oa.xwf:63,71`，helper `executeDelegate` 死代码）；超时 `dueAction` 从未端到端触发（fixture 到期 2099）。
3. **票签双阈值为或语义且缺「两阈值同设」用例**：`ExecGroupSupport.java:56-69`（先判 `passWeight`，未达标继续判 `passPercent`）；设计文档已同步更正。
4. **exec-group 槽位已接线且有专项 fixture**：`WorkflowEngineImpl.java:1321-1333`（完成 `:1267`/拒绝 `:1367`）；测试 fixture `execGroupVoteSlot`/`execGroupVoteRejectSlot` 存在；`wf-vote.xlib` 已提供 `passCount`/`passPercent`/`rejectCount`。
5. **认领/征求意见/唤醒历史任务三模式平台不支持**：覆盖矩阵 A1 判定「不支持」；API mutation 面仅 `startWorkflow/invokeAction/killWorkflow/suspendWorkflow/resumeWorkflow/signalWf/transferActors`（`nop-wf.api.xml`）；wf.xdef 无 claim 相关属性。
6. **运行中实例迁移缺失但定义版本化已具备**：grep `migrate`=0；`NopWfDefinition` 已有 `versionProp="version"` 乐观锁与版本列（`nop-wf/model/nop-wf.orm.xml:73,105`），迁移缺的是实例侧能力。
7. **`onAppStates` 为自由 csv-set**：`wf.xdef:146`（对比同文件 `execGroupType`/`splitType` 均为枚举校验）。
8. **DSL 可发现性局限已记录**：`wf:upLevel` 等扩展属性依赖 xdef 注释、无集中参考表（分析 B3 局限条目）。
9. **AI 审批已落地**：`wf-ai.xlib` + 置信度阈值 + 低置信转人工 + signal 唤醒（`WfAiHelper.java:19-24`）。
10. **批量转办已落地**：`transferActors`（源模型 `nop-wf/model/nop-wf.api.xml:26`）+ 4 个测试。
11. **调度器已落地且防伪造**：`nop-wf-scheduler`（`WfTaskScanner` + nop-job YAML）；系统触发标记 `ATTR_WF_SYSTEM_ACTION_TRIGGER` 仅服务端可设（`WorkflowEngineImpl.allowCallByUser`）。
12. **实体审批绑定已落地**：`approval-support.xbiz` 5 mutations + `TestUseApprovalE2E` 8 例强覆盖。
13. **设计器链路已通**：`WorkflowDesignerService` + `WfGraphDocumentCodec`；15 个示例 graph↔模型全量往返（`TestWfGraphDocumentCodec.java:60`）；flux 画布集成 plan 338 已 completed。
14. **owner doc 功能概览滞后**：`docs-for-ai/03-modules/nop-wf.md` 功能概览未列驳回/撤回一等语义、抄送、AI 审批、调度器模块、批量转办、投票槽位、实体审批绑定（对照本底账 4/5/9–12）。
15. **对比口径裁定**：BPMN 互操作不计分；主口径为 DSL 表达力/直观性 + 审批语义（分析 Context/Conclusion）。BPMN 系仅在「实例迁移」一项领先（分析 B2）。

## Work Item Status

> 唯一动态状态块。勾选 = 独立 closure audit 通过（完成判定见 Cross-Cutting）；未勾选 = `todo`（无计划），起草 plan 并通过 draft review 后标注 `planned`。WI 编号全文件递增不重排；**deps 是唯一并行屏障**。每个 WI 以独立 plan 承载。

### M0 — 裁定与文档基线

- [ ] WI0 三项 Open Questions 裁定 + owner doc 功能概览刷新：裁定（a）认领/征求意见是否为产品必备审批模式（决定 M2 走向）；（b）运行中实例迁移是否为真实需求（决定 WI8 去留）；（c）`onAppStates` 是否 dict/枚举化（决定 WI6 去留）；同步刷新 `docs-for-ai/03-modules/nop-wf.md` 功能概览——补驳回/撤回一等语义、抄送、AI 审批、`nop-wf-scheduler`、批量转办、投票槽位、实体审批绑定、四种 execGroup（reconcile-on-touch，以分析文档及 live code 为准，本底账仅作待补清单）。裁定结论回写本文件 Work Item Status（`planned` 或移入 Deferred）。（Deliverable: 三项裁定 + owner doc 修正；deps: 无；Item Type: Decision + Proof）

### M1 — 测试加固（P0，纯测试，不改产品行为）

- [ ] WI1 A3 Major 断言修复（8 项）：修复 `testInvokableActions` 空转（补断言）、`testTransferDelegate` 名义覆盖（真触发转办/委派）、`executeDelegate` 死代码接 oa.xwf `delegate`/`delegateReturn` 端到端、`testTimeoutAuto` 补 `dueAction` 实际触发验证（消除 2099 fixture 依赖）、cc 负向断言改为错误码校验、`testEndSubFlowParentMissingSkips` 补断言、`assertThrows` 补 error code 校验、`checkExecGroupReject` 全局缓存模型测试加 try/finally 恢复。逐项对照分析 A3 的 file:line。（Deliverable: 测试代码修复 + `./mvnw test -pl nop-wf` 全绿；deps: 无，与 WI0 并行；Item Type: Proof）
- [ ] WI2 引擎操作与票签语义端到端补测：为 `kill`/`remove`/`changeActor`/`transitTo` 提供行为级测试（kill 后实例与步骤终态、被杀实例不可再 invokeAction）；补「投票两阈值同设」用例（验证或语义：`passWeight` 未达标时继续判 `passPercent`）；补「减签→组完成条件变化」用例（cancelStep 成员后 and-group/vote-group 完成判定随动）；补「驳回目标非默认前驱」可证伪用例（目标与默认前驱不同，证参数生效）。（Deliverable: 新增测试 + 全绿；deps: WI1；Item Type: Proof）

### M2 — 审批模式补全（P1，依 WI0a go）

- [ ] WI3 认领（claim）模式：支持角色/部门分配的步骤先进入「可认领池」，首个认领者固化为 owner（防抢占语义：认领原子、后续认领报错）；DSL 侧在 assignment 增加 claim 语义声明，引擎侧新增 `claimStep` 路径（**WorkflowEngineImpl + wf.xdef + `nop-wf.api.xml` 变更，plan-first**）；对照 FlowLong `claimRole/claimDepartment` 语义，但不引入其表模型。（Deliverable: DSL + 引擎 + API + 测试；deps: WI0、WI2；Item Type: Feature）
- [ ] WI4 征求意见（consult）模式：非决策步骤——被咨询者提交意见（记录进 action/status history 与 wfVars），**不改变主流程状态机流转**；约定 `specialType=consult` 步骤 + `oa.xlib` 增加标准意见动作；与既有 cc（阅知）区分：consult 需要回执意见且可配置「意见齐全才完成」。优先纯 DSL/xlib 实现，若需引擎感知则单列 plan-first 段落。（Deliverable: DSL 约定 + 标签库 + 测试；deps: WI0、WI2；Item Type: Feature）
- [ ] WI5 唤醒已结束历史任务：评估 `transitTo`/`changeActor` 组合是否已可表达「把已驳回/已退出步骤重新拉起处理」；若可表达则补测试 + `WorkflowService` 暴露受控管理动作（权限走既有 `checkManageAuth`）；若不可表达则设计受限 re-activate（仅允许激活有后继历史的步骤，禁止复活已 kill 实例）（**API 变更 plan-first**）。（Deliverable: 评估结论 + 测试或实现；deps: WI0、WI2；Item Type: Decision + Feature）

### M3 — DSL 强化（P1，依 WI0c go）

- [ ] WI6 `onAppStates` 枚举化校验：将 `wf.xdef:146` 的 `onAppStates` 从自由 `csv-set` 收紧为受 dict/枚举约束（合法值 = 标准 appState 词表：agree/disagree/reject/confirm/complete 等，以 oa.xwf 与 `WfAppState` 常量为准）；`nop-xdefs` 属框架 schema（**plan-first**）；补非法值报错的解析测试；存量 15 个示例与 `_dump` 回归不受影响。（Deliverable: xdef 收紧 + 回归测试；deps: WI0；Item Type: Feature）
- [ ] WI7 审批 DSL 参考表与扩展属性可发现性：在 `docs-for-ai` 新增（或扩展 nop-wf 模块节）集中参考表——四种 `execGroupType` 语义与阈值或语义、11 个 `wf-actor:*` 标签、`wf:upLevel` 等全部 `wf:*` 扩展属性（含义 + 示例）、标准 appState 词表、`specialType=cc/consult` 约定；每项附最小 `.xwf` 片段；与 `wf.xdef` 注释互为一致（以 xdef 为准）。消除分析 B3 记录的「扩展属性隐式、表达式学习成本」局限中的可文档化部分。（Deliverable: 参考表文档 + 链接检查通过；deps: WI0、WI6（词表定稿）；Item Type: Proof）

### M4 — 运行时与可观测性（P2，依 WI0b go）

- [ ] WI8 运行中实例跨版本迁移：在定义已版本化（底账 6）前提下，补实例侧迁移——将运行中实例映射到新版本定义（按步骤名映射 + 未映射步骤处置策略：保持现状/终止/转人工）；**ORM + API 变更 plan-first**；无映射安全窗口时明确拒绝并给出错误码。若 WI0 裁定非真实需求则本 WI 移入 Deferred 不实施。（Deliverable: 迁移能力或 Deferred 记录；deps: WI0；Item Type: Feature）
- [ ] WI9 wf 级可观测性：自动接线 `CoreMetrics`——实例启动/完成/kill 计数、活跃实例 gauge、步骤停留时长分布（重点 step 超时前预警）、scheduler 扫描命中/触发计数；不引入新监控后端，复用平台 metrics 通道。**挂载通道约束**：优先经既有 `<listeners>` 事件通道与 `WfTaskScanner` 侧埋点，不改 `WorkflowEngineImpl`；若评估后确需引擎内埋点，则本 WI 升级 plan-first 并入 Cross-Cutting rule 2 清单后方可实施。（Deliverable: 指标自动挂载 + 测试断言；deps: WI2；Item Type: Feature）

### M5 — 收口

- [ ] WI10 回归与文档收口：全量 `./mvnw test -pl nop-wf` + use-approval E2E 复跑；核对 `docs-for-ai/03-modules/nop-wf.md`、`docs-for-ai/04-reference/source-anchors.md` 中 wf 锚点、`docs-for-ai/INDEX.md` 路由；`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors；若 mission 已立项则附 mission 完成证据。（Deliverable: 回归证据 + 文档核对记录；deps: M1 全部 + 已 go 的 M2/M3/M4；Item Type: Proof）

## Current Baseline（2026-10-18 核对）

- 测试：239 tests / 0 failures / 5 skipped（skip 为 codegen/dynamic-js 类与 1 个 DB-only）；15 个审批示例 + 实体审批 E2E 8 例。
- 引擎：4 类 execGroup + 投票双阈值或语义 + exec-group 完成/拒绝槽位接线 + `wf-vote.xlib`；驳回/撤回/抄送/转办/加签一等表达；`kill`/`transitTo`/`changeActor` 引擎能力存在但测试缺失。
- 模块：`nop-wf-ai`（AI 审批）、`nop-wf-scheduler`（催办/超时）、`transferActors`（批量转办）均已落地并有测试。
- 设计器：graph codec + `WorkflowDesignerService` 可用；flux 画布集成 plan 338 已 completed。
- 不支持（对比裁定）：认领、征求意见、唤醒历史任务、运行中实例迁移、BPMN 互操作（后项已明确不做）。

## Framework / Platform Reuse

- **XDSL/Delta/`x:extends`**：审批基线动作继承（oa.xwf）、DSL 多格式互转（nop-cli convert）、xdef schema 校验——WI6/WI7 直接受益。
- **nop-auth actor resolver**：`wf-actor:*` 11 个标签；WI3 认领复用角色/部门解析，不自建组织模型。
- **nop-job + nop-wf-scheduler**：催办/超时调度已接线；WI9 指标在 scanner 与引擎双侧挂载。
- **`CoreMetrics`**：平台统一 metrics 通道（`WfTaskScanner` 已 import），WI9 不引入新后端。
- **graph codec / designer service**：WI3–WI5 新 DSL 属性须同步过 `WfGraphDocumentCodec` 往返与 `WorkflowDesignerService` 校验。
- **`approval-support.xbiz`**：实体审批绑定；WI3–WI5 若影响审批链语义须回归 `TestUseApprovalE2E`。

## Cross-Cutting Rules

1. **closure 判定**：每个 WI 的 plan 必须含可执行验证（测试命令 + 期望结果）；勾选本 roadmap 前须独立 closure audit（不同 task_id）对照 live code，禁止自审。
2. **plan-first 门禁**：WI3（引擎+API+xdef）、WI5（API）、WI6（nop-xdefs）、WI8（ORM+API）在实施前须独立 plan 通过 draft review；WI4 若需引擎感知同样升级；WI9 若评估后确需改 `WorkflowEngineImpl` 同样升级。
3. **SSOT**：审批语义事实的 canonical owner 是 `ai-dev/design/nop-wf/approval-flow-design.md` 与 `wf.xdef`；本 roadmap 只链接不复制语义表。WI0/WI7 修改 owner doc 时按 reconcile-on-touch 同步其他副本。
4. **测试纪律**：新增用例必须断言状态值/错误码，禁止 `catch(Exception)` 宽断言与零断言（A3 教训）；修改全局缓存/模型的测试必须 try/finally 恢复。
5. **WI 编号不重排**：裁决导致 WI 不实施时，移入 Deferred 并注明依据，编号保留。
6. **每 WI 独立 plan**：起草遵循 `ai-dev/plans/00-plan-authoring-and-execution-guide.md`；执行日志按 `ai-dev/logs/00-log-writing-guide.md`。

## Deferred / Out of Scope

- BPMN/CMMN/DMN 导入导出与标准互操作（用户 2026-10-18 裁定为过时规范）。
- 运行时动态组装未知结构审批链（extensions-design §一.5 non-goal）。
- 待办中心/审批中心产品化 UI（复用既有 API + 平台页面能力；flux 画布已由 plan 338 承载）。
- FlowLong/Warm-Flow 式表模型定义存储（与文本 DSL 审可性路线冲突，B3 已裁定不采纳）。
- 与 nop-task 合并（owner doc 已裁定独立）。
- AgileBPM 对照（源不可得，对比缺席已注明于分析 B1）。
- WI8 若 WI0 裁定迁移非真实需求，整体移入本节。
