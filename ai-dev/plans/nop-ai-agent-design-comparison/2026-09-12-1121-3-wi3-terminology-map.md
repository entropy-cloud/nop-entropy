---
status: completed
mission: nop-ai-agent-design-comparison
work-item: WI3
group: "2026-09-12-1121"
verify: [test]
---

# WI3 术语与概念对齐表

## Current Baseline

- 前置依赖 WI2 交付物 ai-dev/analysis/compare-agent-design/00-dimension-matrix.md（deps: WI2），维度子机制与报告模板以此为准
- 待对齐概念族（roadmap 定义）：loop/turn/step/iteration；hook/middleware/waterfall；持久化事件 vs 瞬时事件；session/compaction/checkpoint/spill；另需覆盖矩阵新增子机制引出的概念
- 三方语言范式不同（Java vs TypeScript），术语映射须避免直译制造伪差异（roadmap Cross-Cutting 语义对齐约定：统一按术语表翻译，禁止直译制造伪差异）
- nop 侧术语参考：ai-dev/design/nop-ai-agent/ 下 02-execution-model.md、03-extension-matrix.md 等设计文档
- 本计划交付物是 WI4 起专项深挖与维度报告的翻译口径来源

## Goals

- 产出 Deliverable：ai-dev/analysis/compare-agent-design/02-terminology-map.md
- 建立三方概念映射表：每个概念在三方的等价实现/命名/语义差异
- 为 WI4–WI27 提供统一的翻译口径，消除直译伪差异

## Non-Goals

- 不产出维度对比结论（WI8–WI27 的工作）
- 不产出代码地图（WI1）或维度矩阵（WI2）
- 不修改任何代码；纯分析任务，不涉及 mvn 构建与测试

## Phase 1 — 术语对齐表产出

Status: completed

Targets: ai-dev/analysis/compare-agent-design/02-terminology-map.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` 按 WI2 矩阵的子机制清单逐族收集三方术语：loop/turn/step/iteration、hook/middleware/waterfall、持久化事件 vs 瞬时事件、session/compaction/checkpoint/spill 及矩阵新增概念族
- [x] `Decision` 对每个术语给出三方等价映射、命名对照与语义差异说明，统一 Java↔TS 翻译口径（含"同一子机制双方皆无 → 裁定双方均无"的映射规则）
- [x] `Follow-up` 将术语表汇总写入 ai-dev/analysis/compare-agent-design/02-terminology-map.md

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [x] ai-dev/analysis/compare-agent-design/02-terminology-map.md 存在，覆盖 roadmap 列出的全部概念族及矩阵新增概念族
- [x] 每个概念族含三方等价映射与语义差异说明，无直译伪差异
- [x] 术语表与 WI2 矩阵的子机制清单交叉引用一致
- [x] 端到端验证（不适用）：纯文档分析任务，无运行时路径
- [x] 接线验证（不适用）：无新组件与已有组件协作
- [x] 无静默跳过（不适用）：无代码变更
- [x] No owner-doc update required（分析任务，不改变 live baseline）
- [x] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0（非阻塞，analysis mission）
- [x] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

- dispatch review #review-2026-09-12-112137-mission-driver-2026-09-12-1121-3-wi3-terminology-map-1-5b3f9c71 to ses_f6c4ebd9bffeF3gBVGHlf4uOGi
- 2026-09-12：iteration 1，共识 approved #review-2026-09-12-112137-mission-driver-2026-09-12-1121-3-wi3-terminology-map-1-5b3f9c71

## Verification

- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors / 8 warnings，warnings 全部为 `ai-dev/plans/2258-xlang-try-catch-switch-fix.md` 存量问题，非本次产物）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ai-agent-design-comparison/2026-09-12-1121-3-wi3-terminology-map.md --strict` 退出码 0（Phase 1 执行项 3/3 + Exit Criteria 9/9 全部勾选，Closure Evidence 已写入）
- dsh 侧调研覆盖 14 概念族 + 17 项 dsh 独有概念；pi 侧覆盖 14 概念族 + 12 项 pi 独有概念 + 双会话栈勘误（均按当日 HEAD 实测锚点）
- 纯文档分析任务，无代码变更：mvn 构建与测试不适用（Non-Goals 已声明）

## Closure

Status Note: 交付物 02-terminology-map.md 已产出并通过独立子代理 closure audit（E1–E5 全部 PASS，16/16 锚点行号级抽查命中，K1 HookResult 四态 / K2 pi 双会话栈两处勘误主张均核验属实）；audit 提出的概念族计数口径问题（14→17 节）已修正（交付物 scope 行 + Conclusion + daily log 三处）。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_d2148517-6cf8-4db9-893e-886996d3bdd3（fresh session，非实现 session）
- Evidence:
  - E1（概念族覆盖 ≥ roadmap 四族 + 矩阵新增族）：PASS — T1–T17 共 17 节，roadmap 四族与 D1–D10 全维映射齐全
  - E2（三方映射 + 语义差异 + 伪差异警示）：PASS — 17 节全部三列映射表 + 语义注记 + 14 条伪差异警示专节
  - E3（与 WI2 矩阵交叉引用一致）：PASS — 抽查 5 族 D 编号语义一致，nop 类名与矩阵锚点逐一对应（10 文件存在性实测）
  - E4（daily log WI3 条目）：PASS — ai-dev/logs/2026/09-12.md 顶部条目
  - E5（check-doc-links --strict 退出码 0）：PASS — 0 errors
  - 锚点抽查 16/16 命中：nop 6 项（HookResult 四态内部类、AgentActor.steeringQueue:90、ReActAgentExecutor:444 maxIterations 循环、AgentLifecyclePoint 12 点、AgentEventType 20 值、10 文件存在性批查）；dsh 4 项（DispatchMode:32、TurnEndReasonMap:155、StreamChunk:312、Inbox:26，行号精确）；pi 5 项（StopReason:405、AgentTool:386、runLoop:155/170/174、QueueMode:44-50、CURRENT_SESSION_VERSION=3:30，行号精确）+ HEAD 基线一致
  - K1 PASS：HookResult 确有 Pass/Veto/Reenter/Bail 四态（矩阵勘误回写属实）
  - K2 PASS：pi 双会话栈属实（v3 session-manager.ts:30 + v4 codec version===4 :73）
  - Minor finding 已修复：概念族计数 14→17（scope/Conclusion/daily log）
  - 文本一致性：Phase checkbox 12/12 勾选，frontmatter status=completed 与 Closure 一致

Follow-up:

- no remaining plan-owned work