# Beads 可逆压缩与版本化图谱记忆深度分析 & Nop AI Agent 压缩/并发协调

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/beads`（bd，Go，分布式图谱记忆 + issue tracker，~700+ Go 文件）vs `nop-ai-agent`（compact 包 + memory 包 + team 包）
> Conclusion:

## 一、总览

**Beads** 把 agent 的"记忆"建模为**版本化图谱 issue**（基于 Dolt，cell-level merge 的 SQL DB，支持分支/合并/远程同步），而非自由文本。

| 维度 | beads | nop-ai-agent |
|------|-------|--------------|
| 记忆载体 | 版本化图谱 issue（Dolt） | 消息流 + memory 适配器 |
| 压缩 | 可逆（snapshot→summarize→ratio 校验→commit hash） | PipelineCompactor 有损（MicroCompressionCompactor/Layer2/Layer3） |
| 并发 | 租约心跳 + row_lock（crypto/rand） | 会话接管锁 |
| 依赖 | relates-to/blocks/parent-child 图 + ready_work | 无 |
| 联邦 | Dolt remote push/pull/clone | 无 |

## 二、核心机制详解

### 2.1 Dolt 版本化存储（`internal/storage/storage.go`、`internal/storage/dolt/`）
- 以 Dolt（cell-level merge 的 SQL DB）为后端——支持分支/合并/远程同步（git-like 语义但 cell 级合并）。
- 三种部署模式：`dolt/`（独立 Dolt 服务）、`embeddeddolt/`（内嵌模式）、`dbproxy/`（进程代理模式）。

### 2.2 可逆多层级压缩（`internal/compact/compactor.go:88`）
- `CompactTier1`（:88）：调 Claude Haiku 生成摘要。
- **压缩前先 `SnapshotIssue` 归档**（:136）保证可逆——先快照再覆写。
- 压缩后记录 `originalSize/compactedSize/commitHash`——**压缩比可度量**。
- 失败则保留原文（不覆写）。
- Tier2 候选批量并发（`CompactTier1Batch`，信号量限流）。

### 2.3 租约 + 心跳 + row_lock（`internal/storage/issueops/lease.go:25`）
- `DefaultLeaseTTL = 5*time.Minute`。
- 原子认领 + 租约心跳续期。
- `row_lock`（crypto/rand 随机 token）防并发冲突——比简单计数更抗冲突。

### 2.4 依赖图自动就绪（`ready_work.go`）
- Issue 间通过 `relates-to`/`blocks`/`parent-child` 构建依赖图。
- **自动计算无阻塞任务**："所有 blocker 关闭才 ready"。

### 2.5 联邦同步（`internal/storage/federation.go`）
- Dolt remote 的 push/pull/clone——多节点 issue 同步。

## 三、对 nop-ai-agent 的借鉴要点

1. **可逆压缩管线**（最高价值，compact 包）——nop PipelineCompactor 当前是有损覆盖（MicroCompressionCompactor→Layer2TurnPruningStrategy→Layer3FullSummaryStrategy）；借鉴 beads：**压缩前 snapshot 归档 + 记录压缩比 + 失败保留原文**。与 context-mode 的引用式压缩（`2026-08-01-context-mode-compaction-analysis.md`）正交：引用式是无损指针，snapshot 是有损但可回退。
2. **租约+心跳+row_lock 并发协调**（高价值，team 包）——nop 多 agent 认领同一任务时的乐观锁机制；row_lock（crypto/rand 随机 token）比简单计数更抗冲突。DefaultLeaseTTL=5min 可作为 nop team-task-reclaim 的默认值参考。
3. **依赖图自动就绪**（高价值，plan 包）——与 jcode DAG（`2026-08-01-jcode-dag-first-agent-analysis.md`）的 schedule.rs 同构；"所有 blocker 关闭才 ready"是 plan 任务调度的就绪条件。`relates-to`/`blocks`/`parent-child` 三种关系可直接映射 nop plan 的任务依赖语义。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **租约心跳 + 过期 reclaim**（`internal/storage/issueops/lease.go:18-27`）：`DefaultLeaseTTL = 5*time.Minute`——worker 心跳续期，死亡后租约过期，`bd reclaim` 重新认领。这是**任务级重试/恢复**的核心：worker 崩溃 → 租约过期 → 其他 worker 重新执行。
- **可逆压缩的失败保护**（`compact/compactor.go:88,136`）：压缩前先 `SnapshotIssue` 归档，失败保留原文——压缩失败不丢数据。
- **依赖图就绪重试**：`ready_work.go` 自动计算无阻塞任务——阻塞解除后任务自动变为 ready（隐式重试路径）。
- **对 nop 的启示**：租约 + 心跳 + 过期 reclaim 是 nop team 包任务恢复的参考（对应 `nop-ai-agent-team-task-reclaim.md`）；DefaultLeaseTTL=5min 可作为默认值。

## 四、优缺点

### 优点
1. 可逆压缩（snapshot→summarize→ratio）——有损但可回退，压缩比可度量。
2. Dolt 版本化——记忆可分支/合并/联邦同步。
3. 租约+row_lock 并发协调——生产级多 agent 安全。

### 缺点
1. 强耦合 Dolt（重依赖）。
2. Go-only 无法直接复用。
3. 代码量过大（大量 test）。

## 五、结论

beads 的可逆压缩（snapshot→summarize→ratio）与租约并发协调直接增强 nop compact 与 team 包。依赖图自动就绪与 jcode DAG 互补。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D3**（版本化图谱记忆+Dolt 分支合并+可逆压缩）、**D4**（租约心跳+row_lock 持久化）、**D12**（租约过期 reclaim+依赖图就绪重试）。缺失/薄弱：D1、D6。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **压缩**：nop PipelineCompactor 3 层管线比 beads 的 CompactTier1 更工程化；nop checkpoint append-only 已有"可逆"特性（按 watermark 检索）。
- **存储**：nop memory 包 + DBCheckpointManager 比 beads 的 Dolt 依赖更轻、更贴合 Java 生态。

**必要参考的增量（以超越方式吸收）**：
- **压缩前 snapshot 归档 + 压缩比记录**：nop PipelineCompactor 增加"先快照再覆写 + originalSize/compactedSize 度量"——增强（与 context-mode 引用式压缩统一为双轨）。
- **租约心跳 + row_lock 并发协调**：nop team 包多 agent 认领任务可增加（DefaultLeaseTTL=5min 参考值）。

**总评**：nop-ai-agent **全面超越** beads（压缩/存储更优，append-only 已可逆）；snapshot 归档 + 租约并发两个增量吸收，nop 实现优于其 Dolt 重依赖方案。

## References
- `~/ai/beads/internal/compact/compactor.go:88,136`、`internal/storage/issueops/lease.go:25`、`ready_work.go`、`internal/storage/{storage.go,dolt/,federation.go}`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`（compact 包）、`nop-ai-agent-multi-agent.md`
- `ai-dev/analysis/agent-survey/2026-08-01-context-mode-compaction-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`
