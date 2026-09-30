# nop-stream R5 波次 successor 登记（plan 368 收口移交）

> Source: `ai-dev/plans/368-nop-stream-audit-r5-defects-governance.md` 裁定总表（successor=yes 项）
> Date: 2026-09-30

以下为 plan 368 收口时裁定"需后续归属"的事项，按建议波次分组。各项的发现细节、证据与审计原文见 `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/` 对应报告编号。

## 状态序列化专项

- **ST-05** 容器类型 ValueState 元素类型在恢复链路丢失（需要容器元素类型管道设计；ListState/MapState 不受影响）。
- **ST-18**（关联）增量快照共享 SST 的 task-local 绝对路径依赖（最终 fail-fast 无静默）。

## checkpoint 编排并发重构

- **CC-06** CheckpointCoordinator monitor 内执行 N×阻塞 RPC fan-out（monitor 与 fan-out 线程分离）。
- **CC-17**（同波次）taskExecutor 无界队列 + 终端 RPC 在任务线程 finally 同步发送。

## HA / fencing 专项

- **CC-07** deployTask 传输失败后任务无 liveness 记录的永久 benefit-of-the-doubt 豁免（部署宽限期；plan 368 Phase 3 已收敛两个更大 wedge 入口）。
- **CC-15** JdbcLeaderElector tryBecomeLeader 硬编码 epoch=1（租约行丢失后 fencing 回卷）。
- **CC-05 后续**（关联）TaskProgress DTO 携带 fencingEpoch 的契约扩展（短期 attemptNumber 过滤已由 plan 368 Phase 3 落地）。

## 资源上界专项

- **ST-12** TTL 清理仅发生在 checkpoint 时（RocksDB sidecar 无界增长、memory 过期条目永生）。
- **CC-10**（同波次）commitExecutor 单线程无界队列。
- **CON-11**（既往登记）JDBC 2PC 整 epoch 驻留内存无上限。
- **新移交**：WindowOperator:412 numLateRecordsDropped 同型无 tag 指标注册（CEP-03 同族收口）。

## CEP 专项

- **CEP-05** copyForSubtask 共享 nfaFactory（Rich 条件 + 并行子任务竞争）。
- **CEP-06**（同波次）NFAState toString().split 辅助比较器双标准。
- **TE-03 / TE-10**（测试深化）qualifier 家族语义断言增强、NFA 超时×skip 策略交叉测试。

## 可读性波次

- **RD-05** JobCoordinator 2355 行拆分（checkpoint 编排/终止/Status/fencing 四条拆分线）。
- **RD-06/07/08** executor 双实现 ~60 行重复、窗口函数 adapter 30 行重复（首选）、fraud-example ~50 行重复。
- **RD-13**（关联）Stage-NN 考古注释家族全量清理（main 273 处/117 文件）。
- **TE-07**（关联）toString/hashCode/equals 镜像测试家族 16 处批量处置。

## checkpoint 保留策略专项

- **ST-16** retention 双平面界限不一致（checkpoint 平面按 jobId 全局、manifest 平面按 (jobId, pipelineId)）。

## flow / XDSL 声明面波次

- **AR-06** xpl watermark 生成器 onPeriodicEmit 恒空、interval 声明对该组合静默失效。
- **AR-08** 重复 from→to 边不校验、findEdge 取首条（partition 声明顺序依赖失效）。
- **AR-09** `<checkpoint enabled="true" interval="0">` 静默禁用（与 F-04b 0-语义双标一并裁定）。

## 全仓风格裁定（非 nop-stream 独有）

- **RD-12** import 分组系统性偏离（274 文件 java.*-first）——需与 MA4.2-14 裁定对齐后单独立项。
