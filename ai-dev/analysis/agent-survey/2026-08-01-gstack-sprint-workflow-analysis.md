# Gstack Sprint 数据流转与 WIP Checkpoint 分析 & Nop AI Agent Team/压缩

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/gstack`（Garry Tan/YC，TS+Markdown，Claude Code 技能集合）vs `nop-ai-agent`（team 包 + compact）
> Conclusion:

## 一、总览与机制
Gstack 把单 agent 变成"虚拟工程团队"：23 个专家技能（纯 Markdown slash command）。核心：**Sprint 流水线**（Think→Plan→Build→Review→Test→Ship→Reflect，技能间数据传递）；**持续 WIP checkpoint**（自动 WIP commit 含结构化 `[gstack-context]` body：决策/剩余工作/失败方法，`/context-restore` 恢复，`/ship` filter-squash WIP 保留非 WIP）；**分层 prompt injection 防御**（22MB ML 分类器 + Haiku transcript 投票 + canary token + verdict combiner 双分类器共识才阻断）；**Taste memory**（5%/周衰减）。

## 二、对 nop-ai-agent 的借鉴要点
1. **Sprint 技能间 artifact 数据流转**（高价值，team 包）——上游 agent 产出作为下游 agent 结构化输入，对应 nop team 包 agent 协调。
2. **WIP checkpoint + filter-squash**（高价值）——结构化上下文自动提交 + 按需压缩，直接增强 checkpoint 的人可读性与 git 集成（与 planning-with-files 文件三件套 `2026-08-01-planning-with-files-persistent-plan-analysis.md`、beads 可逆压缩 `2026-08-01-beads-versioned-graph-memory-analysis.md` 呼应）。
3. **分层 injection 防御（多分类器 + canary + verdict combiner）**（中价值）——可用于 middleware 洋葱链的安全层（双分类器共识才阻断，降低误报）。
4. **Taste memory + 衰减**（中价值）——agent 偏好学习，team 包实现 agent 个性化。

## 三、结论
Gstack 的 Sprint 数据流转 + WIP checkpoint 是 nop team/compact 的实践参考；分层 injection 防御值得借鉴。局限：非引擎/框架（是 Claude Code prompt 集合）、无程序化控制、强耦合 Claude Code 技能系统、高度流程主观。

## References
- `~/ai/gstack/`（commands/、skills/、security/）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-team.md`、`nop-ai-agent-compaction.md`、`nop-ai-agent-security.md`
- `ai-dev/analysis/agent-survey/2026-08-01-planning-with-files-persistent-plan-analysis.md`、`2026-08-01-beads-versioned-graph-memory-analysis.md`
