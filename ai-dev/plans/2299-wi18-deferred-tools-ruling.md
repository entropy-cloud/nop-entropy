# 2299 WI18 deferred 工具引入机制裁定（refactor roadmap M5，Decision）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI18（Deliverable: 裁定记录落 owner doc 裁定小节；deps: 无；Item Type: Decision）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI18 吸收，纯裁定项）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/04-tool-invocation.md`

## Purpose

pi-D7 建议1：deferred tools——工具结果可声明 addedTools，下一轮按需激活，降低工具面 token 常驻成本。本项裁定该机制与 nop 前缀缓存稳定目标（WI20 + autonomous WI1 工具排序）的冲突是否可调和；不可调和则裁定移出并记录。

## Ruling（Deliverable ①）

**结论：移出（不做）。**

依据（执行时 HEAD `312669e117`，2026-10-03）：

1. **冲突是结构性的**：工具定义位于 prompt 前缀最高价值段（Anthropic tools block 先于 system；OpenAI tools 为请求前缀组成）。结果驱动的 addedTools 自动激活 = 定义面在中途变化 = 该 session 工具段起的缓存全失效。这与 WI20（per-provider 缓存键放置）+ autonomous WI1（工具显式排序）建设的稳定前缀经济学正面冲突——deferred tools 节省的常驻成本会被缓存重建成本反噬（长会话、大定义面下净负）。
2. **用例已被既有机制覆盖**：nop 已有 activeTags 运行时切换（plan 296 set-active-tags meta 工具）——按需收缩工具面能力在位，且其定义面变化是显式、低频、用户主动的（缓存失效作为被接受的显式成本）。pi 式结果驱动自动激活会把定义面变化变成高频自动事件，性质不同。
3. **缓解方案不可验证**：append-only 激活（追加在排序末尾保前缀）理论上兼容前缀缓存，但依赖 provider 对 tools block 追加的前缀行为——需 provider 实测验证，本 roadmap 无法闭环；且会约束 WI1 排序契约。
4. **维度总裁定佐证**：pi-D7 总裁定为"nop 领先"（柔性创新不改刚性缺口）；建议2（路径串行）已由 WI17 落地；建议1 与建议3（throw 表失败约定）未承接——建议3 属规范类，归 error-handling/开发规范通道另行处理（round 1 审查修正：非"唯一未承接项"）。
5. **反方检验（round 1 审查补充）**：失效对齐激活（仅 compaction/fork 边界）近零缓存代价但属 activeTags 已覆盖的显式低频类；provider 原生 defer_loading 透传（pi 实现依赖 Anthropic defer_loading）不依赖追加行为实测——两形态均登记进重启条件/排除项，不构成翻案。

**重启条件**（登记）：生产负载实测工具面常驻 token 为可测量问题 **且** (a) provider 对 tools block 追加行为实测验证 或 (b) provider 原生 defer_loading 透传形态评估通过（接受 provider 专属/可移植性代价）——齐备方重新立项。

## Current Baseline

- pi-D7 建议1 原义：`packages/ai/src/utils/deferred-tools.ts`（~/ai/pi @ c49906ec7），工具结果声明 addedToolNames → 下一轮激活。
- nop 既有：activeTags 运行时切换（plan 296）、WI16 前缀缓存裁定（不注入参数保定义面稳定）、WI17 已落地。
- WI20/autonomous WI1：缓存键放置与工具排序在途（本裁定为其排除一个冲突源）。

## Goals

- owner doc `04-tool-invocation.md` 新增"deferred 工具引入裁定"小节（结论+四条依据+重启条件）。
- log 条目 + roadmap WI18 勾选 + 锚点。

## Non-Goals

- 任何代码变更；activeTags 机制改动；WI20 实施本身。

## Execution Plan

### Phase 1 - 裁定落档与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`、log、roadmap

- Item Types: `Decision`

- [x] owner doc 裁定小节（Ruling 节落档 + round 1 审查三处必改落实：建议3 如实表述/显式调用措辞/defer_loading 重启形态）
- [x] 独立子代理审查裁定论据（agent_cbaf4791：裁定维持，论据1/2 经 live 核实，反方检验无推翻形态；No new test required: 纯裁定无代码变更）
- [x] doc-links 0 error；roadmap WI18 勾选 + 锚点；log；commit

Exit Criteria:

- [x] 裁定在 owner doc 在位且论据与 live 一致；全部验证门通过

## Closure Gates

- [x] 无代码变更（纯裁定，构建验证门不适用——per guide 纯文档计划条款）
- [x] 独立子 agent closure-audit 已完成并记录证据（对抗审查 agent_cbaf4791-ae51-40f1-b7ba-449e75e21a35 三处必改已全部落实后裁定维持——审查兼 closure audit，纯裁定项证据充分）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2299-wi18-deferred-tools-ruling.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成

## Closure

Status Note: 移出裁定经独立对抗审查维持——论据1（结构性冲突，live 核实 buildToolDefinitions 每轮重算 + tools 段先于 system）、论据2（activeTags 显式调用覆盖用例）、论据4（pi-D7 总裁定）全部成立；三处必改（建议3 未承接如实表述/显式调用措辞/defer_loading 重启形态）已落实进 owner doc；无代码变更、无构建门（纯文档计划豁免）——WI18 交付（裁定记录）全部落地。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_cbaf4791-ae51-40f1-b7ba-449e75e21a35（对抗性审查兼 closure audit——纯裁定项，审查即验收）
- Evidence:
  - 5 审查点：1 PASS（live 核实 AnthropicDialect:260-275/OpenAiDialect:166/buildToolDefinitions）、2 PASS+STRENGTH（set-active-tags 在位，措辞修正）、3 主体 PASS+子断言修正（建议3 未承接如实登记）、4 STRENGTH（反方检验无推翻形态，两兼容形态登记）、5 PASS（guide 合规）
  - 三处必改全部落实；doc-links exit 0
  - Follow-up: 重启条件见 Ruling（双条件齐备方重新立项）；pi-D7 建议3 归开发规范通道
