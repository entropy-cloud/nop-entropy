# 03: Plan Guide 是强制程序，不是参考文档

**日期**：2026-05-20
**触发**：Plan 30 (nop-stream audit remediation) 执行后遗漏 checklist 打勾、Phase Status 更新、独立 closure audit

## 教训

Plan guide (`ai-dev/plans/00-plan-authoring-and-execution-guide.md`) 定义了 19 条硬规则。这些规则不是因为 agent 能力不足才写的——正是因为能力强但注意力聚焦在代码上时，会系统性跳过计划维护。

### 根因模式

Agent 把 plan 当成"需求规格"（读一次就不再看），而不是"活文档"（每个 slice 完成后必须同步更新）。具体表现：

1. **不读 guide 就开始执行** — 最根本的错误
2. **Checklist 批量补勾** — 而非每完成一项立刻勾掉
3. **Phase Status 不更新** — 代码写完但 plan 文件里的状态还是旧值
4. **Self-audit** — 自己检查自己的工作，违反独立审计要求
5. **文本不一致** — Plan Status 说 `completed`，但 Phase 状态还有 `in_progress`

### 对比：chaos-flux 项目

chaos-flux 从未出过此问题。原因：它的 agent 在每次执行 plan 前先读 guide，然后机械地按步骤执行。Guide 的步骤和代码改动是同等优先级的工作。

### 防御措施

已在 `AGENTS.md` 新增 **Plan Execution** 节，包含 5 条硬规则：

1. 执行任何 plan 前必须先读 plan guide
2. 机械地按 guide 步骤执行
3. Checklist 即时勾选，不批量
4. Closure 必须独立 subagent 审计
5. `completed` 前所有文本必须一致

## 适用范围

所有 `ai-dev/plans/` 下的计划执行，无例外。
