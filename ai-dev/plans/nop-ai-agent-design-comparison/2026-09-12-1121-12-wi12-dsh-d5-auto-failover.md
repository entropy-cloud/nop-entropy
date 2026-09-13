---
status: completed
mission: nop-ai-agent-design-comparison
work-item: WI12
group: "2026-09-12-1121"
verify: [test]
---

# WI12 dsh-5 自动切换 对比

## Current Baseline

- 前置依赖：deps = WI11（WI11 closure 后本计划方可执行；draft 阶段先拟制）；契约与材料齐备——00-dimension-matrix.md（D5 子机制拆解 + 锚点候选 + 报告 6 节模板 + 5 值裁定格式）、02-terminology-map.md（翻译口径 + 伪差异警示）、03-flow-agent-loop.md（执行流程权威源）、04-extension-capability-matrix.md（能力语义）、05-extension-ordering.md（顺序语义）、06-extension-composition.md（跨机制协同）
- 本报告为 dsh-D5 维度对比：按 6 节模板产出，专项主题（流程/能力/顺序/协同）引用 03-06 结论只写对比增量，不重复展开
- nop 侧 Owner doc：`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（结论须与代码核对，以 01-06 文档与代码为准）
- 基线：nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（WI6 重钉延续，执行时再确认 HEAD）；锚点行号当日实测
- 专项文档与本报告冲突时以专项文档为准（WI29 收敛）

## Goals

- 产出 Deliverable：ai-dev/analysis/compare-agent-design/dsh-D5-auto-failover.md
- 按 00 矩阵 D5 节子机制清单（D5-1 故障转移/D5-2 熔断与冷却/D5-3 配额感知/D5-4 模型分级路由/D5-5 切换语义与 fail-loud）逐项对照裁定 + 维度总裁定（5 值）+ 可吸收增量建议（仅记录不实施）

## Non-Goals

- 不重复专项文档已裁定的机制事实（引用之）
- 不排实施计划；不修改任何代码；纯分析任务
- 不覆盖 pi 侧（pi-D5 是 M3 的对应工作项）

## Phase 1 — dsh-D5 维度报告产出

Status: completed

Targets: ai-dev/analysis/compare-agent-design/dsh-D5-auto-failover.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` 复核 dsh 侧 D5 相关机制在当前 HEAD 的锚点（沿用 WI3-WI7 调研结论，抽查关键锚点行号）；核对 nop 侧锚点与 Owner doc 声明
- [x] `Decision` 按 6 节模板撰写：① 结论摘要 ≤10 行 ② nop 侧机制与锚点（专项主题引专项结论+只写增量）③ dsh 侧机制与锚点 ④ 子机制逐项对照表（D5-1 故障转移/D5-2 熔断与冷却/D5-3 配额感知/D5-4 模型分级路由/D5-5 切换语义与 fail-loud 每行 5 值裁定+证据）⑤ 语义差异与取舍（按 02 术语表翻译，含"双方均无"裁定如适用）⑥ 维度总裁定 + 可吸收增量建议
- [x] `Follow-up` 写入交付物；发现前序文档（02-06）与本维事实冲突时以代码为准回写勘误并登记 daily log

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [x] ai-dev/analysis/compare-agent-design/dsh-D5-auto-failover.md 存在，6 节模板无缺节（① ≤10 行），头部记录三方 HEAD 与分析日期
- [x] ④ 节覆盖 D5 全部子机制行（D5-1 故障转移/D5-2 熔断与冷却/D5-3 配额感知/D5-4 模型分级路由/D5-5 切换语义与 fail-loud），每行有 5 值裁定 + 至少一个锚点证据
- [x] ⑥ 节维度总裁定为 5 值之一 + 一句话依据；可吸收增量建议注明来源侧与针对的 nop 现状，不排实施
- [x] 专项主题（不适用：本维无专项权威源，机制事实直接对照）
- [x] 端到端验证（不适用）：纯文档分析任务
- [x] 接线验证（不适用）：无新组件
- [x] 无静默跳过（不适用）：无代码变更
- [x] No owner-doc update required（分析任务；Owner doc 与代码不符处登记 daily log）
- [x] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0
- [x] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

-（待子代理对抗性审查后回填）

## Verification

- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；9 warnings 全部为存量）
- `node ai-dev/tools/check-plan-checklist.mjs plans/nop-ai-agent-design-comparison/2026-09-12-1121-12-wi12-dsh-d5-auto-failover.md --strict` 退出码 0（13/13 checkbox 全勾，Closure Evidence 已写入）
- `roadmap-check.mjs`（AGE 模板）`passed: true`
- 纯文档分析任务，无代码变更：mvn 构建与测试不适用（Non-Goals 已声明）

## Closure

Status Note: 交付物 dsh-D5 维度报告已产出并通过独立子代理 closure audit；报告裁定与 daily log M2 汇总一致。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_c9f80d9c-26f8-4f77-b82f-1879c454df39（fresh session，非实现 session）
- Evidence:
  - Exit Criteria 全部 PASS：6 节模板齐（① ≤10 行）、头部三方 HEAD、④ 全子机制行 5 值裁定+锚点、⑥ 总裁定+增量建议、plan 13/13 勾选、daily log M2 条目一致
  - E1-E5 全部 PASS；retry-policy.ts 默认可重试集命中（不含 QUOTA/溢出码——⑥ 配置告警前提成立）；model-selection.ts:76 精确；runtime-types:347/:363 重钉
  - 无附加发现
  - 文本一致性：Phase Status=completed、frontmatter status=completed、13/13 checkbox 全勾

Follow-up:

- no remaining plan-owned work

