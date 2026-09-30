# nop-stream 深度审计 R4 轮（quality 系列）汇总报告

> 审核日期: 2026-09-30（0530 批次）
> 审核模块: nop-stream（core / runtime / cep / flow / rocksdb / connector* / fraud-example / quickstart）
> 基线: HEAD = 66eaa9ae91（plan 366 收口后）；`../mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb -am` 全绿（`_tmp/r5-baseline-test.log` EXIT=0）
> 方法: 9 个并行只读子代理独立审计 + 报告落盘（本目录），主代理汇总。前置基线：R3 轮（2026-09-29）与 plan 366 修复记录。

## 执行范围（9 份报告）

| 报告 | 维度 | 发现数 |
|------|------|--------|
| `00-plan366-regression.md` | plan 366 三提交回归审计（逐 hunk diff + 修复验证 + 变异式测试推演） | 7 |
| `01-cep-correctness.md` | CEP 引擎正确性（NFA/compiler/SharedBuffer/CepOperator/Pattern） | 8 |
| `02-state-backend-serialization.md` | 状态后端与序列化（memory/RocksDB/JDBC checkpoint/SerDe/TTL/keyed） | 18 |
| `03-connectors-security.md` | 连接器与安全（JDBC 2PC/Debezium/kafka-pulsar codec/file/SQL 注入五项标准检查） | 11 + 6 项 R1 遗留登记 |
| `04-concurrency-resource.md` | 并发与资源管理（coordinator/taskmanager/InputGate/transport） | 17 |
| `05-readability-structure.md` | 可读性/结构（死代码/重复/超大文件/注释失实/命名/import） | 13 |
| `06-test-effectiveness.md` | 测试覆盖与单元测试有效性（P-1..P-8 反模式 + plan 366 保护力矩阵） | 16 |
| `07-performance.md` | 性能候选与基准缺口（静态扫描 + 5 项既有基准缺口复核） | 11 |
| `08-doc-contract-consistency.md` | 文档-代码一致性与契约一致性 | 13 |
| （独立目录）`2026-09-30-0530-adversarial-review-nop-stream/01-open-findings.md` | 开放式对抗审查（发现导向，跨机制组合缝） | 11 |

## 按严重程度分布（合计 125 项）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | keyBy 路由哈希与 keyed-state 属主哈希双轨错位（AR-01，跨机制组合缝） |
| P1 | 15 | 状态恢复（ST-01/02/03）、协调并发（CC-01/02/03）、连接器（CON-01/02/03）、CEP（CEP-01）、plan 366 回归（REG-01）、文档门面（DC-01/02/03/04） |
| P2 | 36 | 局部缺陷与治理（死协议残留、孤儿错误码、测试保护力弱、序列化边界、P2 并发竞态等） |
| P3 | 73 | 低优先级（风格、防御性缺口、性能候选、观察项） |

## 关键发现摘要

### P0

- **AR-01** `DataStreamImpl.java:403` 路由用 `(key.hashCode() & MAX) % parallelism`，而状态属主用 `stableHash % maxParallelism(默认128) → 连续区间`（`MemoryKeyedStateBackend.java:466`）——两套公式仅当 maxParallelism==parallelism 才重合。rescale/reshard 按 ownership 公式归置状态，记录却按另一套公式路由 → 恢复后接收子任务从零重算、owner 子任务积累幻影状态。POJO key 的身份哈希使同并行度跨 JVM 恢复也漂移。rescale E2E 测试用 ownership 公式手工构造 savepoint，真实路由产物从未流经恢复过滤。信心：很可能（五点代码事实闭环推导，建议先落真实路由 E2E 钉子测试再修）。

### P1（15 项）

| # | 位置 | 问题 |
|---|------|------|
| REG-01 | SupervisionLoop/ResultPartition | no-mat 内部边 × F1 COMPLETED 跳过 × checkpoint 回滚：producer 已完成时无重发源，重启后静默丢失 (N, M] 记录窗口（修复前为可检测悬挂，现变为不可检测截断） |
| CEP-01 | CepOperator:808-814/1063-1131 | PT+comparator 模式 bucket 排水由事件时间 ledger 驱动，但 PT 分支从不写 ledger 且 reconcile 每 key 仅一次 → 首轮 timer 后缓冲事件永久滞留（匹配静默丢失 + 状态无界增长） |
| ST-01 | PendingCheckpoint abort 双重 CAS | abort 路径 future 永不异常完成，savepoint 等待方空等满超时看到 TimeoutException 而非 abort 原因 |
| ST-02 | RocksDBSnapshotSerDe:408-418/480-482 等 | restore 路径 key-group 用 JSON 原生 key 计算，重材料化缺失（RocksDB 三入口 + memory rescale filter + KeyGroupReshard），非原始类型 key 恢复后不可达/误路由 |
| ST-03 | KeyGroupAssignment:203-205 | stableHash 将 Enum 标为稳定值 hash，但 Enum.hashCode 是 identity hash，跨 JVM 稳定性契约对 enum key 失效 |
| CON-01 | JDBC 2PC 幂等账本 | 账本键 (epoch_id, subtask_id) 缺作业/管道命名空间——同 DAG 两条 jdbc-2pc 支路（或共库多作业）互撞账本行，guard 命中即静默跳写整批数据 |
| CON-02 | DebeziumMessageSource.dispatchEvent | 吞掉 ctx.collect 异常——CDC 事件被丢、offset 照常推进并持久化，永久静默数据丢失（与 2026-05 已修 P1-9 同族） |
| CON-03 | Debezium 引擎线程 | 线程死亡仅 LOG.error——任务保持 RUNNING、checkpoint 空转、CDC 静默断流，监督体系无感知 |
| CC-01 | JobCoordinator.rotateFencingEpochCoreLocked:1600-1615 | fencing 轮换推送无 per-node 异常隔离且工作集先清空——恢复半途失败后 detectFailures 永不触发，作业级永久悬挂无自愈（准 P0） |
| CC-02 | SupervisionLoop.buildConsumerInvokableWithReplay:825-827 | 物化回放"先存缝隙"竞态：drain 与 store 快照之间生产者写入 → 同一记录交付两次，破坏物化边 exactly-once（R4-N1 遗留 follow-up，本轮确认仍开放且为真实数据错误） |
| CC-03 | stop()/terminateCancel() | 不向 TM fan-out cancelTask——远程任务成为孤儿：slot permit 永久占用、数据面持续产数 |
| DC-01/02 | nop-stream/README.md、nop-stream-user-guide.md:22-31 | 两份门面文档的快速开始示例原样执行必抛 nop.err.stream.invalid-state（默认 STRICT_EXACTLY_ONCE + 无条件 validateConnectorConsistency；实测复现） |
| DC-03 | ai-dev/design/nop-stream/cep-design.md | 「方式二（当前推荐）」示例两个 API 均不存在（NFACompiler.compile / nfa.advanceTimeSharedBuffer 全仓 0 命中） |
| DC-04 | nop-stream/README.md 模块表 | 仅 6/10 模块（缺 rocksdb/connector-jdbc/connector-batch/connector-debezium）；01-architecture §二同病 |

## 正面结论（抽查通过，不立案）

- plan 366 三提交（Phase 2/3/4）回归审计：9 项修复全部真实落地、语义正确、配套测试变异式推演全部"能捕获"；Phase 3 行为保持声明抽查全部通过（12+ 删除符号全仓 0 引用）；Phase 4 文件源缓冲化逐字节语义保持。无 fix-of-fix 回归。
- SQL 注入不成立（标识符全经 escapeSQLName，值全参数化）；白名单校验无透传回退；凭证设计正确（Debezium D4 fail-closed 为正面样本）。
- XDSL 契约（xdef↔_gen↔builder、x:schema 路径、FL-1 fail-fast）完全一致；跨模块 RPC/elector 契约零漂移；owner doc 指标名表 24 项与代码字面量一致。
- plan 366 后零引用类/常量全仓清零；测试隔离状况良好；全库有效测试 ≈90%。

## 与既有裁定的关系

- R4-N1 遗留的"回放窗口先存缝隙"follow-up 本轮升级为确认数据错误（CC-02），纳入本轮修复。
- Deferred 项（A3-A6/B2/B7、G1-G3、D4/A11、跨模块测试脚手架、RemoteTaskDeploySupport 语义分歧）维持原裁定，未被本轮破坏（除 CC-02 升级）。
- 性能系列收敛裁定维持：本轮无 ≥2% 低风险可收割项；基准缺口 5 项中 1 项已闭合（connector 数据面），2 项仍开放（TaskDispatchLoop 中间层、嵌入态 checkpoint 循环），新增 1 项（MemoryKeyedStateBench namespace 交替档）。
- 上轮登记 follow-up 的现状：withLateDataOutputTag 孤儿（未复核本轮）、processWatermark1/2 测试驱动候选（TE-10 相关）、file source 行累积重构（PF-01 精确化）。

## 修复归属

P0×1 + P1 代码×11 → 本轮修复计划（P0/P1 缺陷修复批次，含聚焦回归测试）；文档 P1×4 + P2 治理批次 → 治理计划。详见 `ai-dev/plans/`（本轮新建计划）。

## 本次审核盲区自评

- AR-01 未运行时验证（静态五点闭环推导），修复前必须先落真实路由 E2E 钉子测试确证。
- DISTRIBUTED 模式的多 JVM 故障注入（TestMultiJvmExactlyOnceRecovery 之外的场景）覆盖仍薄，CC 组多项发现依赖时序推演而非复现。
- nop-message-debezium / nop-message-core / nop-batch-core 上游模块本身未在本轮范围内（CON-02/03 的根因可能在包装层与引擎层的契约）。
- JMH 基准未实际运行（本轮为静态审计）；性能候选收益均为估算档位。
