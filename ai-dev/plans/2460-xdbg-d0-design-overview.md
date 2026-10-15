# 2460 XDBG D0 总体架构与协议总纲（roadmap item 1）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 1（Stage detail "1. D0 总体架构与协议总纲"）
> Related: `ai-dev/analysis/2026-09/2026-09-26-magic-api-online-debug-analysis.md`（§6 三条用户裁定，作为硬约束输入）

## Purpose

产出 XLang 在线调试编辑器的总体架构文档（`ai-dev/design/xlang-web-debugger/00-overview.md`）与各里程碑验收标准，完成 Wave 0 的第一个设计项 D0：组件图、端到端消息时序、与 SimpleRpcServer+IDEA 通道共存策略、WS 传输容器裁定、范围裁定（集群转发/协同编辑 out of scope），并把 open questions 清零或显式转派 D1-D6。

## Current Baseline

以下均为 2026-10-16 live repo 已核验事实：

- **调试引擎**：`nop-dev-tools/nop-xlang-debugger` 的 `XLangDebugger`（实例字段持有 `suspendedThreads`/`stepMode`/`lastSuspendThread`，无静态单例）+ `BreakpointManagerImpl` + `SuspendedThread` + `IDebugNotifier`；通过 `XLangDebuggerInitializer` 在 `nop.xlang.debugger.enabled=true` 时创建 `SimpleRpcServer`（默认 `127.0.0.1:12345`）并 `EvalExprProvider.registerGlobalExecutor(new DebugExpressionExecutor(debugger))` 注入全局 executor。
- **协议层**：`nop-dev-tools/nop-api-debugger`（仅依赖 `nop-api-core`）11 个类：`IDebugger`/`IDebuggerAsync`/`IBreakpointManager`/`Breakpoint`(sourcePath+line+condition+logExpr)/`BreakpointHitMessage`/`DebugVariable`/`DebugValueKey`/`LineLocation`/`StackInfo`/`StackTraceElement`/`ThreadInfo`。
- **executor 槽位**：`nop-core` `EvalExprProvider._executor` 静态全局槽位（默认 `DefaultExpressionExecutor.INSTANCE`）；`IExpressionExecutor.execute(IExecutableExpression, EvalRuntime)` 单方法接口；`EvalRuntime` 无 executor/debugger 字段，executor 作为方法参数逐层传递。
- **绕过路径**：`EvalBackendRouter.executeAdjudicated` 对 `EvalStaticBoundExecutable` 直接 `binding.execute(rt)`（不经过全局 executor，`allowBreakPoint()=false`）；`XplLibTagCompiler.LazyCompiledFunction.applyGeneratedClassBinding()` 经 `EvalBackendRouter.bindTagFunction` 产生同样的标签绑定体直通。
- **WS 基座**：`nop-graphql-core` `io.nop.graphql.core.ws.JsonRpcWebSocketHandler`（JSON-RPC 2.0 over WS：`IUserContextExtractor` 握手鉴权、无凭证/登录失败 4401、`tokenRefresh`、10s keepalive ping、4400/4403/4409 关闭码）；quarkus（`nop-quarkus-web` `/ws/jsonrpc` `@ServerEndpoint`）与 spring（`nop-spring-web-starter` `/ws/jsonrpc`）双端点适配器；`IUserContextExtractor` 实现为 `ILoginService`（`nop-biz-auth-core`）。
- **模块结构**：`nop-dev-tools/` 现有 `nop-api-debugger`、`nop-xlang-debugger`、`nop-maven-shaded-plugin` 三模块 + `nop-idea-plugin`（Gradle，不入 Maven 构建）；nop-dev-tools 下无任何 WebSocket 代码。
- **前端基座**：nop-chaos-flux `flux-code-editor`（CodeMirror 6，`EditorLanguage` 封闭联合 + `createLanguageExtension` 硬编码 switch）、`env.openSocket` 结构化 WS 抽象、`pack-flux-bundle.mjs` 跨仓打包链。
- **设计目录**：`ai-dev/design/xlang-web-debugger/` 尚不存在；`ai-dev/design/README.md` 子系统表无对应条目。
- **设计裁定输入**：分析文档 §6 三条用户裁定——①前端基座 = flux-code-editor 上改进不引 Monaco；②挂起超时 = 前端租约续期，前端消失后端不得永久阻塞（到期自动放行 = resume 语义）；③多用户并发 = 每会话独立 debugger 实例。

## Goals

- XDBG-D0-01：`ai-dev/design/xlang-web-debugger/00-overview.md` 落地，含：总体组件图、端到端消息时序（断点命中→续期→恢复→完成）、SimpleRpcServer+IDEA 共存与开关策略、WS 传输容器裁定（复用 JsonRpcWebSocketHandler 模式 + 双端点适配器；给出 GraphQL subscription 的评估结论）、范围裁定（集群转发单实例先行、协同编辑 out of scope）、网关模块归属裁定（新子模块 vs 扩展 nop-xlang-debugger）。
- XDBG-D0-02：里程碑验收标准——对 roadmap 六个 milestone 各给出可观察的 done 判据（落为 00-overview.md 的章节）。
- 设计目录 `README.md`（AGE 声明 + 阅读顺序 + 文档职责边界），并在 `ai-dev/design/README.md` 子系统表登记。
- Open questions 清零或显式转派 D1-D6。

## Non-Goals

- 不写 D1-D6 子域细节（协议消息集 schema、租约状态机参数、会话透传机制、grammar 方案、REPL 沙箱规则表——各归 owner 设计文档）。
- 不改任何产品代码（Wave 0 为设计阶段）。
- 集群多实例消息转发、多人协同编辑、XDSL 资源保存/热发布（显式 out of scope，留 follow-up，本计划只做裁定记录）。
- 不改 `docs-for-ai/`（item 23 统一做文档注册；module-groups 最小条目由 item 8 的 plan 顺带补）。

## Scope

### In Scope

- `ai-dev/design/xlang-web-debugger/00-overview.md`（新建）
- `ai-dev/design/xlang-web-debugger/README.md`（新建）
- `ai-dev/design/README.md`（子系统表追加一行）
- `ai-dev/backlog/xlang-web-debugger-roadmap.md`（item 1 状态翻转 + Stages 表 owner plan 引用修正）
- `ai-dev/logs/2026/10-16.md`（执行当日日志；若滑日则对应日期文件）

### Out Of Scope

- D1-D6 六份子域设计文档（item 2-7 各自的 owner plan）
- 任何 Java/TS 代码、构建配置
- `docs-for-ai/` 全部文件

## Execution Plan

### Phase 1 - 设计目录与总纲文档

Status: completed
Targets: `ai-dev/design/xlang-web-debugger/`

- Item Types: `Feature`（新设计文档）、`Decision`（四项裁定：WS 传输容器、网关模块归属、共存策略、范围边界）

- [x] 写 `ai-dev/design/xlang-web-debugger/00-overview.md`：设计结论、组件图（Mermaid：浏览器前端 / WS 调试网关 / 会话管理器 / 每会话 XLangDebugger / 会话化落点（中性表述，方案 A/B 归属显式标注"见 D3"，不预判裁定）/ 执行后端 / SimpleRpcServer+IDEA 默认会话）、端到端时序（sequenceDiagram：设断点→执行命中→挂起推送→租约续期→求值→单步/放行→完成，含租约过期/断连分支）、共存与开关策略、WS 传输容器裁定（含 GraphQL subscription 评估与拒绝理由）、网关模块归属裁定、范围裁定（集群转发/协同编辑/热发布 out of scope）、协议分层声明（D0 定传输容器与通道边界，消息集 schema 归 D1）、里程碑验收标准（M1-M6）、拒绝了什么、与 xlang-truffle 设计的边界关系
- [x] 写 `ai-dev/design/xlang-web-debugger/README.md`：AGE owner-doc 声明、层级声明（00-overview 为 Vision + Architecture Baseline 双职）、阅读顺序（00-overview 为入口）、D1-D6 子域文档占位职责表
- [x] `ai-dev/design/README.md` 子系统表追加 `xlang-web-debugger/` 条目
- [x] open questions 清零：分析文档 Open Questions 节中每条要么在 00-overview.md 给出裁定，要么显式标注转派对象（允许复合问题拆分或多归属，如"租约归属→D2、单步目标→D3"）

Exit Criteria:

- [x] `00-overview.md` 含九个必备节：组件图、时序图、共存与开关策略、传输容器裁定（含 GraphQL subscription 结论）、模块归属裁定、范围裁定、协议分层声明、M1-M6 验收标准、拒绝了什么 + 与 xlang-truffle 的边界关系，且不引用 `ai-dev/analysis/`/`ai-dev/discussions/`（设计文档引用约束）
- [x] 三条用户裁定（flux-code-editor 基座 / 租约续期 / 每会话独立实例）在文档中以设计约束形式独立陈述（不依赖外部分析文档即可理解）
- [x] open questions 全部落到"本文件已裁定"或"转派 D<N>（可多归属）"两态之一，无悬空
- [x] 文档内容与 live repo 事实一致（类名/模块名/端点路径与 Current Baseline 核验结果相符）
- [x] owner-doc 更新 = 本 Phase 交付物自身（00-overview.md/README.md）+ `ai-dev/design/README.md` 登记；`docs-for-ai/` 按 Non-Goals 不动
- [x] `ai-dev/logs/` 执行当日条目已更新（随收口一并写入）

### Phase 2 - 对抗性设计评审与收口

Status: completed
Targets: `ai-dev/design/xlang-web-debugger/`、`ai-dev/backlog/xlang-web-debugger-roadmap.md`

- Item Types: `Proof`（独立评审）

- [x] 独立子代理对抗性评审一轮：组件职责是否与现有代码事实冲突、时序是否遗漏租约/断连分支、裁定是否自洽、文档是否符合 design writing guide（Mermaid 选型、无实现代码、无 analysis 引用）
- [x] 修复评审发现的问题（F1 Blocker + F2-F9 Minor 全部修复并经 closure audit 复核落地）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 本 plan 激活为 `active` 时，roadmap Work Item 1 状态 `todo` → `planned`
- [x] roadmap Work Item 1 状态 `planned` → `done`（closure audit 通过后），并同步修正 Stages 表第 1 行 owner plan 引用为实际 plan 路径 `2460-xdbg-d0-design-overview`
- [x] `ai-dev/logs/` 收口记录更新

Exit Criteria:

- [x] 评审发现的问题全部修复或有书面裁定理由
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap item 1 已翻转 `done` 且设计收口 milestone 未提前翻转（依赖 1-7 全 done）
- [x] `ai-dev/logs/2026/10-16.md` 含本项执行与评审记录

## Closure Gates

> 纯文档计划（无代码变更）：构建验证类条目按 guide 豁免，用 doc-links --strict 代替。

- [x] XDBG-D0-01、XDBG-D0-02 两项交付物齐备且内容满足 Stage detail 要求
- [x] 设计评审（对抗性审查一轮）完成，无未处置的 Blocker/Major 发现
- [x] 文档与 live repo 事实一致（抽查类名/配置键/端点路径）
- [x] 不存在被静默降级的 in-scope 缺陷（本计划无代码缺陷面）
- [x] 受影响的 owner docs 已同步（`ai-dev/design/README.md` 已登记；`docs-for-ai/` 明确不在本计划范围）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2460-xdbg-d0-design-overview.md --strict` 退出码 0
- [x] No build verification required（纯文档计划，无代码变更；doc-links --strict 即本计划的机械验证门）

## Deferred But Adjudicated

（无——本计划范围即两个交付物，无遗留项）

## Non-Blocking Follow-ups

- roadmap 整体的集群转发/协同编辑/热发布 follow-up 已在 roadmap Purpose 与 00-overview.md 范围裁定中登记，不属于本计划的债务。

## Closure

Status Note: D0 两项交付物（00-overview.md + 里程碑验收标准 M1-M6）已落地并登记目录索引；独立对抗性评审发现 F1 Blocker + F2-F9 Minor 全部修复；独立 closure audit 逐条核验 Exit Criteria/Closure Gates 与 F1-F9 修复落地均为 PASS，doc-links --strict 0 errors。roadmap Work Item 1 翻 `done`，设计收口 milestone 保持 `todo`（依赖 1-7）。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（设计评审 agent_1f3bb242-79b4-4b5e-bcf0-a5b0f3748bc5；closure audit agent_72f71bd4-3a8b-4744-b94b-47fe3967ed35）
- Audit Session: closure audit 由 fresh 子代理执行，与实现会话隔离
- Evidence:
  - Phase 1 Exit Criteria 6 条：5 PASS + 日志条目随收口写入后 PASS（`ai-dev/logs/2026/10-16.md` 已建）
  - Phase 2 Exit Criteria 4 条：全部 PASS（doc-links --strict 实测 exit 0；roadmap 状态机合法：item 1=done、设计收口 milestone=todo）
  - Closure Gates 9 条：全部 PASS（纯文档豁免条目按声明核验）
  - F1-F9 修复验证：9/9 PASS（F1 四处表述统一为"默认会话兜底" + roadmap item 8 Goal 对齐，git diff 证实）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2460-xdbg-d0-design-overview.md --strict` 退出码 0
  - Anti-Hollow（文档版）：13 节全部实质、M1-M6 repo-observable、无 TODO/TBD 占位、无 analysis/discussions 引用
  - Deferred 项分类检查：Deferred But Adjudicated 为空；Non-Blocking Follow-ups 仅 roadmap 级三项（集群转发/协同/热发布），分类诚实
- 登记补记（audit 发现 N1）：`ai-dev/analysis/2026-09/2026-09-26-magic-api-online-debug-analysis.md` 的 Open Questions 勾结属执行项 4（open questions 清零）的落盘动作，与 00-overview.md §五 处置表一致。
