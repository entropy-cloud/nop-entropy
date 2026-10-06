# nop-stream 深度审计 R6 轮（quality 系列）汇总报告

> 审核日期: 2026-10-06（0522 批次）
> 审核模块: nop-stream（core / runtime / cep / flow / rocksdb / connector* / fraud-example / sql）
> 基线: HEAD = 3b2493aef5（R5 修复批次 plan 2310–2317 全部完成后）
> 方法: 10 个独立维度审计（每维度初审 + 同会话深挖第 2 轮 + 独立维度复核）+ 1 份开放式对抗审查（含独立复核）+ 主 agent 实测测试基线 + 独立合并裁定代理。全部子代理只读。
> 工具基线（主 agent 实测）: `./mvnw install -DskipTests -pl <11 模块> -am` 成功；`./mvnw test -fn -pl <11 模块>` —— **nop-stream-fraud-example 4 个 E2E Error（HEAD 真实红灯），其余 10 模块全绿**（4 error / 16 skipped / 其余通过）。

## 执行范围（11 份报告 + 实测基线 + 合并裁定）

| 报告 | 维度 | 初审+深挖 | 保留 | 降级 | 驳回 |
|------|------|-----------|------|------|------|
| `00-fix-of-fix-regression.md` | R5 修复批次（2310–2317 + WI11 治理）回归 | 13 | 11 | 1（REG-04→P3） | 0 |
| `01-cep-flow-correctness.md` | CEP 引擎 + XDSL StreamModel 编排 | 10 | 9 | 0 | 1（CEP-02，Flink 同构排除） |
| `02-state-backend-serialization.md` | 状态后端与序列化 | 11 | 10 | 1（ST-04→P2） | 0 |
| `03-connectors-security.md` | 连接器与安全 | 6 | 6 | 0 | 0 |
| `04-concurrency-resource.md` | 并发与资源管理 | 8 | 8 | 0 | 0 |
| `05-keyed-routing-window.md` | 键控路由/属主 + 窗口/watermark | 7 | 7 | 0 | 0 |
| `06-test-effectiveness.md` | 测试覆盖与单元测试有效性 | 7 | 7 | 0 | 0 |
| `07-readability-structure.md` | 可读性/结构/命名/死代码 | 13 | 13 | 1（RD-04→P3） | 0 |
| `08-performance.md` | 性能候选（PF×6）+ 基准缺口（BG×9） | 15 | 6+8 | 0 | 1（BG-5，基准已演进） |
| `09-doc-contract-consistency.md` | 文档-代码一致性 + 跨模块契约 | 6 | 6 | 0 | 0 |
| `../2026-10-06-0522-adversarial-review-nop-stream/01-open-findings.md` | 开放式对抗审查 | 9 | 9 | 0 | 0 |

初审/深挖原始条目 105（不含 BG），独立复核驳回 2、降级 3；独立合并裁定吸收 9 项重复/子集、升格 1 项（TE-03→P0 族）。

## 实测 HEAD 红灯（主 agent 确诊，本轮最高优先级）——定性已按 plan 2322 Adjudication 修订

4 个 fraud-example E2E Error，根因为 **R5 守卫语义偏离其设计契约与 owner doc**（plan 2322 Adjudication 裁定，推翻本汇总最初"守卫行为正确、改 fixture"的方向；三重证据：守卫 javadoc 明文"partition vertex 取目标值"、守卫文案自建议 "or a HASH edge" 而抛出点正在 HASH 分支内、user-guide :126 已写 resize 语义）：

| 测试 | 根因 | 修复归属 |
|------|------|---------|
| TestPerTransformParallelismE2E / TestParallel2PcFileE2E / TestParallel2PcJdbcE2E | `nop.err.stream.edge-partition-unsupported`：FLOW-5 守卫在 HASH 边分支错误拒绝 keyed 阶段并行度≠上游（HASH 是分区边界，本应 resize；执行面 HashPartitionRouter 本就支持跨并行度散列）。fixture 声明是平台能力的合法验证，不改 | **2322 Phase 1**：HASH 分支改 resize；`<keyBy>` 变换分支保留拒绝（防下游宽度静默丢失） |
| TestS2OfflineReshardE2E | `nop.err.stream.invalid-arg` "keyBy maxParallelism drift (R5 KR-3)"：守卫文案承诺的合法路径 "configure the state backend before keyBy" 在 DSL 入口不可达；修复后第二层 KR-2 守卫又因 restore 期 keyed 后端未创建回落默认 128 产生幻影失配 | **2322 Phase 2**：DSL `withPreBuildEnvironment` pre-build 后端入口 + restore 侧 `resolveMaxParallelism` 回落链读配置后端 M |

修复验证：plan 2322 Phase 3 全量回归 `./mvnw test -pl <11 模块>` 全绿（含 4 个转绿 E2E 的分布/exactly-once 断言）。

## 跨报告合并裁定（独立裁定代理）

- **ST-01 ⊃ AR-03 + REG-08**（keyed payload 叶子活引用；ST-01 复核以 EquiJoinOperator `JoinSideRecord.setMatched` barrier 后原地变异推翻"无生产变异用法"，P1 成立）。
- **ST-01 与 ST-02 = 同族相邻**（同一冻结协议未闭合，失效层分别为 keyed payload / operator-state 通道），立为同一缺陷族两条，修复须同批且**必须同步修 REG-01**（putAll seq 估计器，否则修复引入静默覆盖）。
- **CC-01 = AR-02**（同一 monitor 同一调用链，两面互补：分布式 RPC fan-out + LOCAL sink commit I/O + recoveryLock 放大）。
- **CC-04 ⊃ AR-04 后半**（注册表裸 vertexId）；**AR-04 前半独立**（enumerator 状态仅 LOCAL 接线，分布式 manifest 恒空）。
- **KR-09 ⊃ REG-05**（RescaleStateAssembler 守卫名单漏 3 键，KR-09 为严格超集）。
- **CON-01 ⊃ REG-07 之 CON-08 面**（文件 2PC manifest 无运行代际命名空间 = R5 CON-08 已登记未修复）；REG-07 之 CON-04 面（截断提交无守卫）独立保留。
- **REG-02 ⊂ CC-03**（同一锁外激活写块）；**REG-11 = FLOW-04**；**ST-09 族 ⊃ REG-09**；**RD-05 ⊂ RD-03**；TE-01↔TE-02 交叉引用同批修复。
- **P0-E2E-RED**（新族，吸收 TE-03）：子面 (a) FLOW-5×3 fixture + 子面 (b) KR-3×MaxParallelismReshardMigration 入口冲突；FLOW-03（守卫文案自相矛盾）独立 P3、随批修。

## 按严重程度分布（合并裁定后唯一缺陷）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | E2E 红灯族（4 红灯 = R5 守卫×消费面未对齐：fixture×3 + 迁移入口×1） |
| P1 | 9 | 快照冻结协议族（ST-01/ST-02）、写读类门不对称（ST-03）、sink 错误优先级颠倒（AR-01）、恢复路由三缺陷（KR-06 恢复路由生产不可达 / KR-07 keyed 快照裸键覆盖 / KR-08 idle 永不恢复 ACTIVE）、文件 2PC 运行代际（CON-01）、终止派发测试保护真空（TE-01） |
| P2 | 28 | 恢复/守卫长尾（ST-04..08、CEP-01、FLOW-01/02、WIN-05、KR-09）、并发（CC-01..04、AR-04前半）、连接器（CON-02/03）、治理（REG-01/03/07残留/12、RD-01/02/03、TE-02/04、PF-01/02） |
| P3 | 49 | 防御性缺口、命名/死代码、文档漂移、性能候选、观察项（含 DC-01..06 归口） |
| 附：基准缺口 | 8 | BG-1..4、6..9（P3 级治理项） |

## 关键发现摘要

### P0

- **P0-E2E-RED** HEAD 默认构建 `./mvnw test` 必红（4 Error）：R5 守卫批次（FLOW-5 keyed-stage 并行度守卫、KR-3 maxParallelism 等值守卫）落地后未同步 fraud-example fixture 与离线 reshard 迁移路径。守卫行为本身经复核正确；缺陷是守卫×消费面集成缺口 + CI 红线常态化。修复方向：(a) 三个 fixture 按守卫语义改写；(b) KR-3 守卫识别迁移产物/迁移后重建图，S2 测试改走合法迁移入口；随批修 FLOW-03 文案矛盾。

### P1（9 项，去重后）

| # | 位置 | 问题 |
|---|------|------|
| ST-01 | MemoryStateSerDe writeListPayload/writeMapPayload | List/Map 载荷叶子活引用进异步快照（EquiJoin barrier 后 setMatched 原地变异实证）；含 AR-03/REG-08 |
| ST-02 | EquiJoin/OverWindowOperator.snapshotState | 活 PerKeyOrderedBuffer 放入 operator state，持久化线程才序列化 → 恢复后缓冲重复元素 |
| ST-03 | ClassNameValidator | 恢复侧白名单拒绝写侧已接受的用户类：checkpoint 成功变不可恢复资产（DataBean 用户类/用户 accumulator 面） |
| AR-01 | StreamSinkOperator.processBarrier | snapshotState 成功后 saveState/prepareCommit 失败仅记日志即按成功 ACK → durable checkpoint 落盘无 pending，恢复期静默丢数据 |
| KR-06 | GraphModelCheckpointExecutor | rescale 恢复路由生产不可达：isVertexKeyed 依赖 open() 才创建的 keyedStateBackend + remote provision 字段错位；setTargetKeyGroupRange 零调用 → 键控缩容恢复静默 1:1 错置（R5 KR-2/WIN-2/4 修复全部休眠） |
| KR-07 | CheckpointBarrierTracker | 全部 keyed 快照落裸键 `"keyed-state"` 后写覆盖前写：链内 ≥2 keyed 算子每次 checkpoint 丢状态（process→process 等普通 API 可达） |
| KR-08 | TimestampsAndWatermarksOperator | idle 恢复永不发 WatermarkStatus.ACTIVE（markActive 零调用者）→ 经历过 idle 的通道事件时间永久停摆 |
| CON-01 | FileTwoPhaseCommitSink | manifest 守卫无运行代际命名空间：fresh run 复用输出目录即静默丢弃新 epoch 数据（R5 CON-08 已登记未修复）；含 REG-07 之 CON-08 面 |
| TE-01 | TestGraphModelCheckpointExecutor | 终止派发（DRAIN/SUSPEND→TERMINAL_SAVEPOINT）executor 路径零保护（变异推演成立；JobCoordinator 路径有真保护） |

### 正面结论（抽查通过，不立案）

- R5 修复批次总体质量高：29 个修复面中真保护 14、部分 4、无保护 11（62% 强保护）；治理提交（WI11 UpsertDialect 迁移）抽查 4 项行为保持全部成立。
- R5 P0（ST-01 value 冻结）与快照/路由/并发/连接器主修复在 live code 确认成立；本轮 P1 均为修复残余或新失效面。
- CEP 核心语义（NFA/SharedBuffer/skip 策略/超时）与 Flink 参考逐项比对一致；CEP-02 按参考实现互证驳回。
- 有效测试外推 98.5%（3928 @Test 中低价值约 1.5%）；8 个高风险面全部有直接测试；隔离性纪律良好。
- 文档面约 130 项可检验声明中指标名表/配置键/错误码/SPI 矩阵/跨模块契约全部一致；R5"能力边界失真"模式未复现（本轮 DC 全部为 P3 级局部漂移）。

## 修复归属（建议）

- **2322**: P0-E2E-RED（fixture×3 + KR-3×迁移入口 + FLOW-03 文案）——恢复全绿底线。**已完成**（commit 753e187742）。
- **2323**: ST-01 + ST-02 + REG-01（快照冻结协议族，含回归钉子）。**已完成**（payload 叶子冻结 + buffer freeze 设计 B + toSerializableForm payload 自包含 + putAll 精确 seq；钉子捕获并根修 V 叶子活引用残余）。
- **2324**: KR-06 + KR-07 + KR-08 + KR-09（键控恢复/路由族；KR-09 守卫名单必须与 KR-06 同批落地）。**已完成**（KR-06 走快照存在性驱动方案 (b)；KR-09 前缀感知使休眠守卫在生产形状键上复活——审计后发现的第二休眠面；KR-07 operator 级前缀 + legacy 回退；KR-08 双侧 ACTIVE 接线；真并行度变更恢复 E2E 首次覆盖；KR-06 检测行经生产形状 fixture 变异探针实测红/绿闭环）。
- **2325**: AR-01 + CON-01 + ST-03 + TE-01（fail-closed 错误路径与测试保护族）。**已完成**（AR-01 错误优先级 + tracker 边界钉；CON-01 来源判别守卫——恢复条目 recover-safe/本 run 撞键 fail-fast，双面钉 + 2 既有测试迁移诚实入口；ST-03 Memory+RocksDB 写侧同规则校验（closure audit M2 整改后三入口全闭合含 keyType 头 + rocksdb 钉）；TE-01 真驱动终止派发 + 盘上类型断言，变异探针实测；TE-02 诚实化改名）。

其余 P2×28 / P3×49 / BG×8 按治理批次惯例进入后续轮次（R5 先例）；驳回项（CEP-02、BG-5、REG-04 降级裁定等）不再重开。

## 本次审核盲区自评

- KR-06 修复方案（restore 入口 keyed 元数据重接线）未做工作量实证，2313 同类 Phase 2 教训应引以为戒。
- 分布式多 JVM 故障注入覆盖仍薄；CC 组部分发现依赖时序推演（已逐一给出交错时序，但无运行时复现）。
- nop-message-core/debezium 上游模块仍在范围外（CON-05 根因可能在包装层契约）。
- JMH 基准未实际运行（静态审计 + 基准源码核对）。
- fraud E2E 修复后转绿的 S2 场景走同并行度恢复，KR-06 的真 P 变更恢复分支仍无 E2E 覆盖（2324 须补）。
