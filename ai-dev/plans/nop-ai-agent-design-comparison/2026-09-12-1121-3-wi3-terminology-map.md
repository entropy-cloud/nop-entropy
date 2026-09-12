---
status: active
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

Status: planned

Targets: ai-dev/analysis/compare-agent-design/02-terminology-map.md（新建文件）

- Item Types: `Proof | Decision | Follow-up`

- [ ] `Proof` 按 WI2 矩阵的子机制清单逐族收集三方术语：loop/turn/step/iteration、hook/middleware/waterfall、持久化事件 vs 瞬时事件、session/compaction/checkpoint/spill 及矩阵新增概念族
- [ ] `Decision` 对每个术语给出三方等价映射、命名对照与语义差异说明，统一 Java↔TS 翻译口径（含"同一子机制双方皆无 → 裁定双方均无"的映射规则）
- [ ] `Follow-up` 将术语表汇总写入 ai-dev/analysis/compare-agent-design/02-terminology-map.md

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase 标记为完成。

- [ ] ai-dev/analysis/compare-agent-design/02-terminology-map.md 存在，覆盖 roadmap 列出的全部概念族及矩阵新增概念族
- [ ] 每个概念族含三方等价映射与语义差异说明，无直译伪差异
- [ ] 术语表与 WI2 矩阵的子机制清单交叉引用一致
- [ ] 端到端验证（不适用）：纯文档分析任务，无运行时路径
- [ ] 接线验证（不适用）：无新组件与已有组件协作
- [ ] 无静默跳过（不适用）：无代码变更
- [ ] No owner-doc update required（分析任务，不改变 live baseline）
- [ ] node ai-dev/tools/check-doc-links.mjs --strict 退出码为 0（非阻塞，analysis mission）
- [ ] ai-dev/logs/ 对应日期条目已更新

## Draft Review Record

- dispatch review #review-2026-09-12-112137-mission-driver-2026-09-12-1121-3-wi3-terminology-map-1-5b3f9c71 to ses_f6c4ebd9bffeF3gBVGHlf4uOGi
- 2026-09-12：iteration 1，共识 approved #review-2026-09-12-112137-mission-driver-2026-09-12-1121-3-wi3-terminology-map-1-5b3f9c71

## Verification

（待 BUILD_VERIFY 填写）

## Closure

（待 CLOSURE_AUDIT 填写）