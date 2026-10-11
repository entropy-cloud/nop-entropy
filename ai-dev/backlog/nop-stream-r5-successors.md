# nop-stream R5 波次 successor 登记（plan 368 收口移交）

> Source: `ai-dev/plans/368-nop-stream-audit-r5-defects-governance.md` 裁定总表（successor=yes 项）
> Date: 2026-09-30

以下为 plan 368 收口时裁定"需后续归属"的事项，按建议波次分组。各项的发现细节、证据与审计原文见 `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/` 对应报告编号。

## 状态序列化专项

- **ST-05** 容器类型 ValueState 元素类型在恢复链路丢失（需要容器元素类型管道设计；ListState/MapState 不受影响）。
- **ST-18**（关联）增量快照共享 SST 的 task-local 绝对路径依赖（最终 fail-fast 无静默）。

## checkpoint 编排并发重构

- **CC-06** CheckpointCoordinator monitor 内执行 N×阻塞 RPC fan-out（monitor 与 fan-out 线程分离）。
- **CC-17**（同波次）taskExecutor 无界队列 + 终端 RPC 在任务线程 finally 同步发送。——其**超时契约 javadoc 前半已由 plan 369 Phase 4 落地**（`IStreamTaskRpcService`/`IStreamCoordinatorRpcService` 接口级"单向、无内建超时/重试、逐方法丢失兜底"契约；队列/线程后半仍 open）。

## HA / fencing 专项

- **CC-07** ~~deployTask 传输失败后任务无 liveness 记录的永久 benefit-of-the-doubt 豁免~~（部署宽限期；plan 368 Phase 3 已收敛两个更大 wedge 入口）——**已由 plan 369 收口**（`ai-dev/plans/369-nop-stream-r6-flink-compare-fixes.md` Phase 4：JobCoordinator 部署宽限期 + detectFailures 挂点 + deploy 传输失败记录点，可注入时钟；测试 `TestJobCoordinatorDeployGracePeriod`）。
- **CC-15** ~~JdbcLeaderElector tryBecomeLeader 硬编码 epoch=1（租约行丢失后 fencing 回卷）~~——**已由 plan 369 收口**（plan 369 Phase 4：独立于租约行的持久 epoch 计数行 `<leaseTable>_epoch_counter`，事务内 read-and-increment + CAS，永不清理；测试 `TestJdbcLeaderElectorEpochMonotonicity`）。
- **CC-05 后续**（关联）TaskProgress DTO 携带 fencingEpoch 的契约扩展（短期 attemptNumber 过滤已由 plan 368 Phase 3 落地）。

## 资源上界专项

- **ST-12** TTL 清理仅发生在 checkpoint 时（RocksDB sidecar 无界增长、memory 过期条目永生）。
- **CC-10**（同波次）~~commitExecutor 单线程无界队列~~——**已由 plan 369 收口**（plan 369 Phase 4：TaskManager commitExecutor 有界队列（可配容量，默认 1000）+ 满队 CallerRuns 背压（选择记录见 `saturatedCommitQueuePolicy` javadoc）+ 积压打点 `nop.stream.task.commitQueueDepth` / `nop.stream.task.commitCallerRuns.total`；测试 `TestTaskManagerCommitQueueBound`）。
- **CON-11**（既往登记）JDBC 2PC 整 epoch 驻留内存无上限。
- **新移交**：~~WindowOperator:412 numLateRecordsDropped 同型无 tag 指标注册（CEP-03 同族收口）~~——**已由 plan 369 收口**（W-M1，plan 369 Phase 2 带 tag 作用域注册方案，照抄 CepOperator）。

## CEP 专项

- **CEP-05** copyForSubtask 共享 nfaFactory（Rich 条件 + 并行子任务竞争）。
- **CEP-06**（同波次）NFAState toString().split 辅助比较器双标准。
- **TE-03 / TE-10**（测试深化）qualifier 家族语义断言增强、NFA 超时×skip 策略交叉测试。

## 可读性波次

- **RD-05** JobCoordinator 2355 行拆分（checkpoint 编排/终止/Status/fencing 四条拆分线）。
- **RD-06/07/08** executor 双实现 ~60 行重复、窗口函数 adapter 30 行重复（首选）、fraud-example ~50 行重复。
- **RD-13**（关联）Stage-NN 考古注释家族全量清理（main 273 处/117 文件）。
- **TE-07**（关联）toString/hashCode/equals 镜像测试家族 16 处批量处置。

## checkpoint 驱动面分叉专项（WI9）

> 测绘与裁定详见 `ai-dev/design/nop-stream/checkpoint-engine-convergence.md`（双向互指）。重构期一律不动这些分叉，处置须走差分门放行或 owner 裁决。

- **DIV-01**（2c 快照失败传播）分布式 per-task 快照失败只 LOG（`RemoteTaskDeploySupport#remoteAbortListener` 注释自认「waiting for coordinator-side timeout abort」），拖满 checkpointTimeout 才 abort；本地侧即时传播有专测。目标语义=即时传播；补 per-task 失败 RPC 或等价通道由 WI11 或 successor 立项。
- **DIV-02**（5b timeout abort 终局相反）本地：任务取消+作业失败态；分布式：只弃 epoch 任务继续（「routine back-pressure」，专测 TestDistributedAbortPath 锁定）。**checkpoint-design.md §8.7 设计意图（超时→job 失败态→恢复，distributed 经 cancelTask）与现行分布式代码冲突**——属偏离已记录意图的后续演进，非不可判定；E3 前提不适用，升格 owner 裁决（输入=§8.7 意图 vs 代码演进+测试）。重构期 policy hook 隔离，不得顺手统一；§8.7 已补偏差注记双向互指。
- **DIV-03**（6a 恢复深度）分布式 coordinator 级 failover 重建只认 raw checkpoint（不读 manifest、不做指纹校验）；source enumerator stash（GMCE :1081/:1124-1148）全仓唯一调用方在本地，分布式无等价物——源枚举器状态恢复缺口待证（潜在 exactly-once 风险点）。coordinator 级入口向 manifest-first 对齐已列入 WI10/WI11 下沉范围。
- **DIV-04**（4c savepoint 恢复能力）「从显式 savepoint 路径启动」仅本地驱动面存在，分布式无对等入口；是否补齐属产品裁定（与 DIV-03 合并审议）。
- **DIV-05**（7 CANCEL 终态语义相反）本地 CANCEL 无条件触发 COMPLETED_POINT_TYPE 终态 checkpoint（超出 checkpoint-design.md §7.3「bounded source 最终 checkpoint」定义域）；分布式立即取消无终态 epoch，**与 §7.2 CANCEL 流程（不保证输出完整、无终态步骤）一致**。本地为超出已记录契约的有意强化（专测锁定）。升格 owner 裁决（输入=§7 契约 vs 本地强化）；重构期两侧冻结；§7.2/§7.3 已补偏差注记双向互指。
- **DIV-06**（相邻：状态后端）分布式部署恢复侧固定 MemoryStateBackend（`RemoteTaskDeploySupport` 注释自认与 LOCAL 可配后端分叉、待裁决）。

## checkpoint 保留策略专项

- **ST-16** retention 双平面界限不一致（checkpoint 平面按 jobId 全局、manifest 平面按 (jobId, pipelineId)）。

## flow / XDSL 声明面波次

- **AR-06** xpl watermark 生成器 onPeriodicEmit 恒空、interval 声明对该组合静默失效。
- **AR-08** 重复 from→to 边不校验、findEdge 取首条（partition 声明顺序依赖失效）。
- **AR-09** `<checkpoint enabled="true" interval="0">` 静默禁用（与 F-04b 0-语义双标一并裁定）。

## 全仓风格裁定（非 nop-stream 独有）

- **RD-12** import 分组系统性偏离（274 文件 java.*-first）——需与 MA4.2-14 裁定对齐后单独立项。
