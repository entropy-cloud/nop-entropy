---
status: completed
mission: nop-ai-agent-design-comparison
work-item: WI4
group: "2026-09-12-1121"
verify: [test]
---

# WI4 S1 agent loop 具体执行流程逐步分解

## Current Baseline

- 前置依赖 WI3 交付物 ai-dev/analysis/compare-agent-design/02-terminology-map.md（deps: WI3），术语翻译口径以此为准（T1 主循环层级 / T2 终止 / T3 迭代上限 / T4 流式 / T5 steering / T6-T7 扩展与能力）
- 章节结构契约：WI2 矩阵"权威源约定"登记 S1 = ① 结论摘要 ② 三方各自一条完整调用链（从输入进入到最终响应的逐步分解：阶段划分、每步职责、流式路径），流程图上逐点标注该阶段挂载的扩展点 ③ 扩展点挂载位置汇总表（阶段 × 扩展点）④ 三方流程结构差异点 ⑤ 权威源引用关系（供 dsh-D1 / pi-D1 引用）
- 已知锚点基础：nop ReActAgentExecutor（外层 sustainLoop while(true) :441-442 + 内层 reactLoop while maxIterations :443-444）、LlmCallCoordinator、AgentToolDispatcher；dsh ReactLoopAgent（kick :210 / turn :246 / step :332，agent/request 发射在 buildRequest :457-460，工具管线在 tool-calls.ts）；pi runLoop（agent-loop.ts:155-279，链体分布在 streamAssistantResponse :281-372 / executeToolCalls :411-554 / prepareToolCall :600 / finalizeExecutedToolCall :713 等辅助函数）
- 本交付物是执行流程主题的**权威深挖**：dsh-D1（WI8）/ pi-D1（WI18）引用本专项结论，只写对比增量；结论冲突时以本专项为准（WI29 收敛）
- 报告语言：中文行文，类名/函数名保留英文；外部仓库路径以 `~/ai/...` 书写并注明仓库

## Goals

- 产出 Deliverable：ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md
- 三方各一条从输入入口到最终响应的完整调用链：阶段编号、每步职责、锚点（文件:行）、流式路径；扩展点在流程图上逐点标注
- 产出阶段 × 扩展点汇总表与三方流程结构差异点清单，供 WI8/WI18 引用

## Non-Goals

- 不产出能力级别分级结论（WI5 S2 的工作，本专项只标注"此处挂载扩展点 X"）
- 不产出同点多触发顺序结论（WI6）与跨扩展协同结论（WI7）
- 不产出 D1 维度对比裁定（WI8/WI18 的工作）
- 不修改任何代码；纯分析任务，不涉及 mvn 构建与测试

## Phase 1 — 三方调用链分解与专项文档产出

Status: completed

Targets: ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` nop 侧：从 DefaultAgentEngine/ReActAgentExecutor.execute 入口追踪到最终 AgentExecutionResult 的完整调用链（上下文构建、异步派发、sustainLoop×reactLoop 双层、每 iteration 内治理闸门（cancel/denial-pause/WAIT_FOR/force-stop/goal-STUCK）/压缩/PRE_REASONING/LLM 调用/响应处理/工具阶段/steering 注入/completion judge/sustainer/终态），含流式路径核实与扩展点位置
- [x] `Proof` dsh 侧：从输入/followup 到 turn/end 的完整调用链（kick→turn→preStep（claim inbox+prompt 组装+agent/pre-step）→step（agent/request→llm/stream→chunk 处理→tool-calls 调度 tools/* waterfall）→turn-stopping→turn/end），含 llm-retry 重入点、压缩触发点、流式路径
- [x] `Proof` pi 侧：从 prompt() 到 agent_end 的完整调用链（runLoop 外层 follow-up while + 内层工具 while：transformContext→getApiKey→streamFn→message 事件→beforeToolCall→并行/串行执行→afterToolCall→toolResult 回填→shouldStopAfterTurn/prepareNextTurn→队列 drain），含 auto-retry/overflow 恢复分支与扩展事件桥接点
- [x] `Decision` 按 WI2 矩阵 S1 章节结构（①–⑤）汇总三方调用链为 mermaid 流程图（扩展点逐点标注）+ 阶段 × 扩展点汇总表 + 三方流程结构差异点 + 权威源引用关系声明
- [x] `Follow-up` 写入 ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md 并交叉核对术语表口径；发现术语表映射与代码不符时以代码为准，回写 02-terminology-map.md 并在 daily log 登记勘误

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [x] ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md 存在，章节结构 = WI2 矩阵 S1 登记的 ①–⑤ 五节（① 结论摘要 ≤10 行），无缺节
- [x] 交付物头部记录 nop/dsh/pi 三方 HEAD commit（当日实测）与分析日期（WI2 矩阵证据纪律）
- [x] 三方各有编号阶段的完整调用链：入口→终态无断链，每阶段带职责说明 + 锚点（仓库相对路径:行号，行号实测），流式路径单独可辨（含"无流式路径"的核实结论）
- [x] 流程图上扩展点逐点标注（nop：AgentLifecyclePoint 12 点 + ExecutionPoint 4 点 + security 链挂载位置；dsh：agent/* tools/* llm/* 事件挂载位置；pi：AgentLoopConfig hook + ExtensionAPI 事件桥接位置），与 ③ 汇总表一致
- [x] ⑤ 节明确声明权威源关系：dsh-D1（WI8）/ pi-D1（WI18）引用本文档，只写对比增量
- [x] 端到端验证（不适用）：纯文档分析任务，无运行时路径
- [x] 接线验证（不适用）：无新组件与已有组件协作
- [x] 无静默跳过（不适用）：无代码变更
- [x] No owner-doc update required（分析任务，不改变 live baseline）
- [x] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0
- [x] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

- 对抗性审查（含想象性分析）：独立子代理 agent_a8271b95-b9f2-43b3-bb20-d968ae4633aa（fresh session），2026-09-12
- 结论：无 Blocker，可执行；1 Major（F4 交付物头部缺 HEAD 基线记录——已新增 Exit Criteria）+ 8 Minor（F1-F3 锚点精修、F5 EC 表述、F6 nop 链枚举补治理分支、F7 状态标记、F8 术语冲突处理规则、F9 ≤10 行约束）全部已修复

## Verification

- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors / 8 warnings，warnings 全部为 `ai-dev/plans/2258-xlang-try-catch-switch-fix.md` 存量问题）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ai-agent-design-comparison/2026-09-12-1121-4-wi4-flow-agent-loop.md --strict` 退出码 0（16/16 checkbox 全勾，Closure Evidence 已写入）
- `roadmap-check.mjs`（AGE 模板）`passed: true`
- 纯文档分析任务，无代码变更：mvn 构建与测试不适用（Non-Goals 已声明）

## Closure

Status Note: 交付物 03-flow-agent-loop.md 已产出并通过独立子代理 closure audit（E1–E7 全部 PASS，14/14 锚点行号级抽查命中，K1 nop 无流式 / K2 pi v4 未接线 / K3 术语表四处勘误回写全部核验属实）；audit 发现的唯一 minor（callStream 行号 :27→:33 漂移）已修正。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_18f5b290-cbc7-4cbe-b428-d6ff2fb39532（fresh session，非实现 session）
- Evidence:
  - E1–E7 全部 PASS：五节结构齐备（① ≤10 行）；头部三方 HEAD 实测（nop=800baf32da，`git diff c585459f83 HEAD -- nop-ai/` 为空确认锚点不漂移）；三方 P1..Pn 调用链无断链（含 nop P15X 异常支路闭合）；流程图与汇总表 9 点抽查位置一致；⑤ 权威源声明完整；daily log 条目在位；check-doc-links 退出码 0
  - 锚点抽查 14/14：nop 6（sustainLoop :441-442 / reactLoop :443-444、PRE_CALL :410-416、CompactionCoordinator :57-63、LlmCallCoordinator :156-401、drainSteering :318-325、HookInvoker REENTER 校验 :154-161）；dsh 4（pre-step :234-240、request-error :374-384、fillPool :198-213、llm/retry 持久化 :150）；pi 4（内层 while :174、prepareToolCall :600-668、摘 error 消息 :2836-2839、_handlePostAgentRun :1088-1116）
  - K1 PASS：grep 全仓库确认 REASONING_CHUNK/ITERATION_STARTED 无触发点、callStream 不被 engine 消费；K2 PASS：agent-harness.ts 18 处 unavailable()、create-harness.ts 仅测试引用；K3 PASS：术语表 T4/T9/T13/T15 四处勘误在位
  - Minor 已修复：callStream 行号 :27→:33（03 文档 :75）
  - 文本一致性：Phase 1 Status 与 frontmatter status 均为 completed，16/16 checkbox 全勾

Follow-up:

- no remaining plan-owned work
