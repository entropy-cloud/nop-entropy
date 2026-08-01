# Beads 可逆压缩与版本化图谱记忆深度分析 & Nop AI Agent 压缩/并发协调

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/beads`（bd，Go，分布式图谱记忆 + issue tracker）vs `nop-ai-agent`（compact 包 + memory 包 + team 包）
> Conclusion:

## 一、总览

**Beads** 把 agent 的"记忆"建模为**版本化图谱 issue**（基于 Dolt，cell-level merge 的 SQL DB，支持分支/合并/远程同步），而非自由文本。核心：**可逆多层级压缩**（先 snapshot 再覆写，记压缩比）、**租约+心跳+row_lock 并发协调**、**依赖图自动就绪计算**、**联邦同步**。

| 维度 | Beads | nop-ai-agent |
|------|-------|--------------|
| 记忆载体 | 版本化图谱 issue（Dolt） | 消息流 + memory 适配器 |
| 压缩 | 可逆（snapshot→summarize→ratio 校验→commit hash） | PipelineCompactor 3 层（有损） |
| 并发 | 租约心跳 + row_lock（crypto/rand） | 会话接管锁 |
| 依赖 | relates-to/blocks/parent-child 图 + ready_work | 无 |

## 二、核心机制

### 2.1 可逆多层级压缩（`compact/compactor.go:88`）
- `CompactTier1` 调 Claude Haiku 生成摘要；**压缩前先 `SnapshotIssue` 归档**保证可逆；压缩后记 `originalSize/compactedSize/commitHash`。
- Tier2 候选批量并发（`CompactTier1Batch`，信号量限流）。

### 2.2 租约 + 心跳 + row_lock（`issueops/lease.go:25`，DefaultLeaseTTL=5min）
- 原子认领 + 租约心跳 + `row_lock`（crypto/rand）防并发冲突。

### 2.3 依赖图自动就绪（`ready_work.go`）
- relates-to/blocks/parent-child 构建依赖图；"所有 blocker 关闭才 ready"。

### 2.4 联邦同步（`storage/federation.go`）
- Dolt remote 的 push/pull/clone。

## 三、对 nop-ai-agent 的借鉴要点

1. **可逆压缩管线**（最高价值）——nop PipelineCompactor 当前是有损覆盖；借鉴 beads：**压缩前 snapshot 归档 + 记录压缩比 + 失败保留原文**。与 context-mode 的引用式压缩（`2026-08-01-context-mode-compaction-analysis.md`）正交：引用式是无损指针，snapshot 是有损但可回退。
2. **租约+心跳+row_lock 并发协调**（高价值，team 包）——nop 多 agent 认领同一任务时的乐观锁机制；row_lock（crypto/rand 随机 token）比简单计数更抗冲突。
3. **依赖图自动就绪**（高价值）——与 jcode DAG（`2026-08-01-jcode-dag-first-agent-analysis.md`）的 schedule.rs 同构；"所有 blocker 关闭才 ready"是 plan 任务调度的就绪条件。

## 四、结论

beads 的可逆压缩（snapshot→summarize→ratio）与租约并发协调直接增强 nop compact 与 team 包。局限：强耦合 Dolt、Go-only、代码量过大。

## References
- `~/ai/beads/compact/compactor.go`、`issueops/lease.go`、`ready_work.go`、`storage/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-compaction.md`、`nop-ai-agent-team.md`
- `ai-dev/analysis/agent-survey/2026-08-01-context-mode-compaction-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`
