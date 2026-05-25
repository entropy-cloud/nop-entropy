# nop-job 设计文档索引

## 阅读顺序

| # | 文档 | 职责 | 状态 |
|---|------|------|------|
| 1 | `rewrite-design.md` | **架构基线**：整体分布式调度架构 | resolved（已实现） |
| 2 | `invoker-design.md` | Invoker 路由体系：IJobInvoker/IJobTaskBuilder/IJobWorker | 草案 v5 |
| 3 | `block-strategy-design.md` | 四种阻塞策略（BYPASS/REPLACE/WAIT/RECOVERY） | implemented |
| 4 | `metrics-design.md` | Micrometer Metrics 命名和埋点规范 | implemented |
| 5 | `rate-limiting-design.md` | 限流设计与 nop 平台限流体系集成 | draft |
| 6 | `retry-integration-design.md` | retryPolicyId 对接 nop-retry | 设计完成 |
| 7 | `cluster-ha-design.md` | 集群 HA 与动态分区设计 | active |

## 实现状态

| 设计 | 实现状态 | 备注 |
|------|---------|------|
| rewrite-design | ✅ 已实现 | 新架构已落地 |
| block-strategy | ✅ 已实现 | 四种策略 + Metrics |
| metrics | ✅ 已实现 | 三套 Metrics + 规范 |
| retry-integration | ✅ 已实现 | retryPolicyId 对接 |
| invoker | ⚠️ 草案 | 路由逻辑已部分实现，设计文档待最终确认 |
| cluster-ha | ⏳ 规划中 | 设计方向已定 |
| rate-limiting | ⏳ 规划中 | 依赖平台限流体系 |

## 相关文档

- `ai-dev/lessons/02-metrics-design-convention.md` — Metrics 命名规范
- `ai-dev/analysis/2026-05-18-fault-tolerance-deep-dive.md` — 容错深度分析
