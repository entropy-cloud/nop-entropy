---
status: active
mission: nop-ai-agent-design-comparison
work-item: WI2
group: "2026-09-12-1121"
verify: [test]
---

# WI2 对比维度矩阵 D1–D10

## Current Baseline

- 前置依赖 WI1 交付物 ai-dev/analysis/compare-agent-design/01-code-map.md（deps: WI1），三方代码锚点以此为基础
- 本 roadmap 定义 D1–D10 十个维度（内部 agent loop / 扩展点 / 事件类型与触发 / 容错性 / 自动切换 / 前缀缓存利用 / 工具系统 / 上下文工程与压缩 / 会话持久化与恢复 / 多代理与子代理），各维度的子机制拆解与报告模板由本计划落定，后续维度报告（WI8–WI27）必须引用本矩阵
- 专项深挖主题 S1–S4（agent loop 执行流程 / 扩展点全量清单与能力语义 / 同点多触发顺序 / 跨扩展机制协同）的章节结构需在本矩阵登记，作为 WI4–WI7 的权威源契约
- 报告格式先例：ai-dev/analysis/agent-survey/agentscope-harness-vs-nop-ai-agent-comparison.md（结论先行 + 勘误 + 对照表结构）
- 分析写作规范：ai-dev/analysis/00-analysis-writing-guide.md（所有报告写作前必读）
- 报告语言约定（roadmap Cross-Cutting）：中文行文，类名/函数名/术语保留英文原名

## Goals

- 产出 Deliverable：ai-dev/analysis/compare-agent-design/00-dimension-matrix.md
- 每个维度 D1–D10 给出子机制拆解、三方代码锚点候选、报告固定 6 节模板与裁定格式（nop 领先 / 对方领先 / 等价 / 双方均无 / 不可比）
- 登记 S1–S4 专项文档章节结构（供 WI4–WI7 使用），固化 D1–D10 六节报告模板（供 WI8–WI27 使用）与权威源约定（WI28/WI29 引用）

## Non-Goals

- 不产出任何维度的实际对比结论（WI8–WI27 的工作）
- 不产出术语对齐表（WI3，本计划只定义矩阵与模板契约供其引用）
- 不修改任何代码；纯分析任务，不涉及 mvn 构建与测试

## Phase 1 — 维度矩阵产出

Targets: ai-dev/analysis/compare-agent-design/00-dimension-matrix.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` 阅读 ai-dev/analysis/00-analysis-writing-guide.md 与 agentscope-harness-vs-nop-ai-agent-comparison.md 先例，抽取报告结构约定
- [x] `Decision` 为 D1–D10 每维拆解子机制清单，并为每个子机制标注三方代码锚点候选（基于 WI1 代码地图）
- [x] `Decision` 固化报告固定 6 节模板（结论摘要 ≤10 行 / nop 侧机制与锚点 / 对方侧机制与锚点 / 子机制逐项对照表 / 语义差异与取舍 / 裁定 + 可吸收增量建议）与裁定格式
- [x] `Decision` 登记 S1–S4 专项文档章节结构，写入本矩阵作为权威源契约（含"以专项文档为准"的冲突收敛规则）
- [x] `Follow-up` 将矩阵汇总写入 ai-dev/analysis/compare-agent-design/00-dimension-matrix.md

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [x] ai-dev/analysis/compare-agent-design/00-dimension-matrix.md 存在，D1–D10 每维含子机制拆解、三方代码锚点候选、报告模板引用
- [x] 报告固定 6 节模板与裁定格式已固化，WI8–WI27 可直接按此产出
- [x] S1–S4 章节结构与权威源约定已登记，WI4–WI7 可直接按此产出
- [x] 端到端验证（不适用）：纯文档分析任务，无运行时路径
- [x] 接线验证（不适用）：无新组件与已有组件协作
- [x] 无静默跳过（不适用）：无代码变更
- [x] No owner-doc update required（分析任务，不改变 live baseline）
- [x] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0（非阻塞，analysis mission）
- [x] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

- dispatch review #review-2026-09-12-112137-mission-driver-2026-09-12-1121-2-wi2-dimension-matrix-1-a1dcca41 to ses_f6c52102affe8Pap477ycrKmiu
- 2026-09-12：iteration 1，共识 approved #review-2026-09-12-112137-mission-driver-2026-09-12-1121-2-wi2-dimension-matrix-1-a1dcca41

## Verification

（待 BUILD_VERIFY 填写）

## Closure

（待 CLOSURE_AUDIT 填写）