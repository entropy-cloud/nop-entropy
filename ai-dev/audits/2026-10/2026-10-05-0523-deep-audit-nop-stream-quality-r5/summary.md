# nop-stream 深度审计 R5 轮（quality 系列）汇总报告

> 审核日期: 2026-10-05（0523 批次）
> 审核模块: nop-stream（core / runtime / cep / flow / rocksdb / connector* / fraud-example / sql）
> 基线: HEAD = bd089444f5（plan 366/368/369/2306-bug-suspects 收口后）
> 方法: 10 个独立维度审计（每维度初审 + 深挖第 2 轮 + 独立维度复核）+ 1 份开放式对抗审查（含独立复核）+ 2 份 P0/P1 子项复核。全部子代理只读，主代理汇总。
> 前置基线: `./mvnw test -fn -pl <10 个 nop-stream 模块> -am` 实测：除 `nop-stream-runtime` 的 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`（本轮 CON-01，HEAD 真实红灯）外全绿；nop-batch-core 的 `TestBatchTaskCheckpoint.testFailFastStopsSiblingThreads` 为上游偶发 flake（同命令复跑通过），与本轮范围无关。

## 执行范围（11 份报告）

| 报告 | 维度 | 初审+深挖发现 | 保留 | 降级 | 驳回 |
|------|------|--------------|------|------|------|
| `00-fix-of-fix-regression.md` | plan 368/369/2306 修复批次回归（fix-of-fix） | 9 | 8 | 0 | 1（REG-5） |
| `01-cep-flow-correctness.md` | CEP 引擎 + XDSL StreamModel 编排正确性 | 12 | 12 | 0 | 0 |
| `02-state-backend-serialization.md` | 状态后端与序列化（含深挖 ST-11..13） | 13 | 13 | 0 | 0 |
| `03-connectors-security.md` | 连接器与安全（含深挖 CON-08..11） | 13 | 13 | 0 | 0 |
| `04-concurrency-resource.md` | 并发与资源管理（含深挖 CC-11/12） | 12 | 11 | 1（CC-11 P1→P2） | 0 |
| `05-keyed-routing-window.md` | 键控路由/属主哈希 + 窗口语义（含深挖 KR-7/8、WIN-4） | 12 | 12 | 0 | 0 |
| `06-test-effectiveness.md` | 测试覆盖与单元测试有效性 | 10 | 9 | 0 | 1（TE-7） |
| `07-readability-structure.md` | 可读性/结构/命名/死代码 | 14 | 14 | 0 | 0 |
| `08-performance.md` | 性能候选与基准缺口 | 8 | 8 | 0 | 0 |
| `09-doc-contract-consistency.md` | 文档-代码一致性 + 跨模块契约 | 14 | 14 | 0 | 0 |
| （独立目录）`../2026-10-05-0523-adversarial-review-nop-stream/01-open-findings.md` | 开放式对抗审查 | 7（新 4 + 旧重登记 3） | 7 | 0 | 0 |

## 跨报告合并裁定（独立复核判定）

- **AR-12 = KR-1**（同一缺陷：remote 部署强制注入默认 MemoryStateBackend(128)，路由 M 与属主 M 解耦）→ 以 KR-1 为主条目。
- **AR-13 = ST-12，且与 KR-7 同族**（PerKeyOrderedBuffer 无 JSON 往返形式；KR-7 主面孔=写侧 durable 默认下必抛，ST-12/AR-13 面孔=降级配置恢复侧静默清空）→ 合并为单一缺陷族，以 KR-7 为主条目，修复须同时覆盖写侧与恢复侧。
- **CC-11 与 ST-12 为遮蔽关系**（生产路径 restore 先于 open，静默碰撞覆盖当前不可达），降级 P2；但修复 KR-7 族恢复管道时**必须同步修** putAll 不推进 insertionSeq，否则转化为静默数据丢失。

## 按严重程度分布（去重后唯一缺陷）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | 异步 checkpoint 序列化活对象图（ST-01，默认配置即破坏 exactly-once） |
| P1 | 16 | 快照/恢复（ST-02/11、KR-7 族）、路由/属主配置一致性（KR-1/2/3、REG-1、FLOW-5）、并发活性（CC-01/02）、连接器（CON-01=HEAD 红灯）、CEP×RocksDB 守卫（CEP-1）、窗口 rescale 族（WIN-1/2/4）、文档门面（DC-1） |
| P2 | 48 | 局部缺陷与治理（信封/元数据、连接器生命周期与幂等、协调器长尾、测试保护力、结构治理、性能候选、文档漂移） |
| P3 | 54 | 低优先级（防御性缺口、命名、死代码、基准缺口、观察项） |

初审/深挖原始条目 122 条，独立复核驳回 2 条、降级 1 条、跨报告合并 2 条。

## 关键发现摘要

### P0

- **ST-01** `MemoryStateSerDe.snapshotKeyedState:239-249` 持活引用 + `CheckpointConfig.java:36` async 默认开：barrier 后任务线程继续原地变异 ACC（规范模式 `SumAggregateFunction` 的 `int[]` 原地累加），异步持久化线程 `JsonTool.serialize` 遍历同一活对象图 → 快照静默撕裂（JSON 可解析、checksum 自洽、restore 无跨校验）。主风险面为 AggregatingState/ReducingState/ValueState 的值图（ListState/MapState 容器本身有快照时拷贝保护，子项复核澄清）。修复方向（子项复核推荐）：`ser==null` 分支在任务线程 snapshotState 内做 JSON round-trip 深拷贝 + 变异回归钉子。

### P1（16 项，去重后）

| # | 位置 | 问题 |
|---|------|------|
| ST-02 | ProcessOperator/EquiJoinOperator/OverWindowOperator | 以 Object.class 声明 keyed 后端 → key 重材料化退化为恒等，非原始类型 key 恢复后经类敏感 equals 静默不可达 |
| ST-11 | ProcessOperator | timer service 从不进 checkpoint——恢复后全部 timer 静默丢失，且 setKeyType 未接线（WindowOperator 的 TimerSnapshot 先例未复用） |
| KR-7 族 | PerKeyOrderedBuffer 传输 | durable 默认（only-data-bean）下 checkpoint 序列化必抛 ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE；降级配置恢复侧 instanceof 静默跳过 → 工作视图空转；rebuildViewFromKeyedState 零调用 |
| KR-1 | RemoteTaskDeploySupport:171-181 | remote 部署恒定注入 MemoryStateBackend(128)，客户端烤入的 maxParallelism 与 TM 属主公式解耦（AR-01 缺陷族在 DISTRIBUTED 形态重开） |
| KR-2 | keyGroupOwnership 元数据 | checkpoint 落盘的 ownership 三元组 write-only，restore 从不读取；跨 maxParallelism 变更恢复静默错置 |
| KR-3 | DataStreamImpl:302 等 | keyBy 建图期一次性快照 maxParallelism，全库无 equality 校验；setStateBackend 后置即静默分裂；RocksDB 默认 M=1 首记录晚败 |
| REG-1 | remote 部署 ledger 命名空间 | subtask-0 未注入 ledger namespace → 回退 tableName，guard 命中静默跳写（R4 CON-01 修复在 remote 面不完整） |
| FLOW-5 | StreamModelDslBuilder/ForwardPartitionRouter | keyed 阶段并行度声明无守卫 + FORWARD 路由恒发 channel 0 → keyBy(P)→window/cep(Q≠P) 全负载压 subtask 0，rescale 后状态与流量永久分离 |
| CEP-1 | CepOperator×RocksDB | 已知不可用边界零守卫：RocksDB 静默忽略 serializer 契约、runtime 注入面无类型拒绝、NopCepConfigs 提示文本反向宣称 rocksdb 支持 |
| CC-01 | JobCoordinator.failJob:775-798 | 不 cancel 已分配远程任务 → 孤儿任务 + 2PC 悬挂 + 运维 remove/submit 全堵 |
| CC-02 | activateAsLeader | rebuild/assignment 失败留"半激活 leader"：active 先置位、elector 吞异常、同 epoch 守卫挡重试 |
| CON-01 | TestStreamConnectorRegistryDiscovery:132 | 能力矩阵钉定测试断言 debezium-cdc=SINGLE_INSTANCE，与 2026-10-02 用户裁定（工厂 PARALLEL）矛盾——**HEAD 当前真实红灯** |
| WIN-1 | WindowOperator merge 分支:1750-1790 | session/merging 窗口 + evictor：不合并 element-timestamps 侧存储 + 被并窗口条目泄漏；TimeEvictor 基于伪 watermark 时间戳驱逐（用户指南 :71/:244 将其列为公开契约，缓解语境不成立） |
| WIN-2 | RescaleStateAssembler:191-234 | 窗口作业 rescale：timer/pane-tracking/trigger-accumulators 走 operator state 1:1 拷贝，与按 KeyGroupRange 重分布的窗口内容分裂 → fire 后 null 静默跳过 + 清理失效（子项复核确认族谱系，+StreamReduceOperator reduce-state 第 6 成员） |
| WIN-4 | CepOperator ledger:692 | per-key event-time timer ledger 是 operator state，scale-down 后 drain 消费性销账空 keyed 状态，静默丢匹配推进 |
| DC-1 | nop-stream-user-guide.md:73 | 「能力边界」对 union/sideOutput 的否定已全面失真——两者 Java API 与 XDSL 均已落地 |

## 正面结论（抽查通过，不立案）

- R4 后三个修复批次（plan 368/369/2306）总体质量高：19 项修复抽样中 17 项具备"回退必红"真保护；治理批次「行为保持」声明抽查 9 项全部成立；fencing 三重闭合、abort future 完备性、2PC 端点协议、XDSL 契约（xdef↔builder）、SQL 注入防护、凭证 fail-closed 等历史缺陷族修复在 live code 中确认成立。
- R4 P0（AR-01 路由/属主公式分裂）在内建 LOCAL 链路已真实消除（14 个决定点全部核验汇聚单一公式链）——残留为 KR-1/2/3 三个配置入口。
- 全库有效测试外推约 70-75% 强保护、边际 20-25%、低价值 3-6%（口径见 06 报告）；文档约 130 项可检验声明中指标名表/REST/配置键/SPI 矩阵等全部一致。

## 修复归属

- **2307**: CON-01 HEAD 红灯修复（一行测试对齐 2026-10-02 裁定 + 护栏语义固化）。
- **2308**: P0 ST-01 + ST-13 + ST-02（快照点时正确性）。
- **2309**: ST-11 + KR-7 族（含 CC-11 同步修复）+ ST-03（恢复面正确性）。
- **2310**: KR-1/2/3 + REG-1 + REG-7 + FLOW-5（路由/属主一致性）。
- **2311**: CC-01/02/06/07（并发活性）。
- **2312**: CON-02/03/04/05/08 + SEC-01 + REG-3（连接器与安全）。
- **2313**: FLOW-2/4 + CEP-1/2/7 + WIN-1/2/4（fail-fast 与守卫族）。
- **2314**: DC-1..14 + RD-6 + REG-6 文档面（文档契约批次，纯文档）。

其余 P2/P3 按治理批次惯例进入后续轮次；驳回项（REG-5、TE-7）不再重开。

## 本次审核盲区自评

- ST-01 修复未运行时验证（静态全链推导 + 子项复核反向搜寻），落地时必须先落"快照后变异 ACC"回归钉子再修。
- WIN 族修复方向（fail-fast → key-group 重分布两阶段）中 Phase 2 未做工作量实证；2313 只承诺 Phase 1。
- nop-message-core/debezium 上游模块本身未在本轮范围内（REG-8/REG-3 的根因可能在包装层契约）。
- JMH 基准未实际运行（静态审计）；PF-01 档位为估算。
- 分布式多 JVM 故障注入场景覆盖仍薄，CC 组部分发现依赖时序推演。
