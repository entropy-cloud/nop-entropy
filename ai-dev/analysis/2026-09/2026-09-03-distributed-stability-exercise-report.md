# 分布式稳定性与性能演练报告（soak / chaos / backpressure）

> Status: open
> Date: 2026-09-03
> Scope: nop-stream DISTRIBUTED 模式稳定性演练（roadmap item 15，plan `ai-dev/plans/nop-stream-productization/2026-09-02-2216-2-stability-performance-exercise.md`）
> Conclusion: （Phase 5 收口时填写）

## Context

- 把 item 14 交付的「C0—C3 短时正确性基线」（gated 13/13）升级为「持续运行稳定性基线」：长时 soak（含状态增长）、随机 chaos（kill 循环 + 分区等价形态）、backpressure 行为观察，全部在 `MiniStreamCluster` 真实多 JVM 基线上执行。
- 本文件 = Phase 1 落档的**演练方案**（矩阵定义、判据、预算、产物规范）+ Phase 3/4 追加的**执行结果** + Phase 5 收口汇总。矩阵定义在 Phase 1 完成时即持久化，非事后补写。
- 执行顺序裁定兑现：item 16（`2026-09-02-2216-1`）已于 2026-09-03 completed，本演练按其交付的指标面执行（见「观察模式裁定」）。

## 观察模式裁定（Phase 1）

**裁定：直接指标（JC 面）+ 存储面/输出面/共享库面可观察量；背压格以 JC 面代理量化。** 依据（live 核对 2026-09-03）：

- **可用直接指标**（item 16 交付，JC 进程 `opsHttpPort` 启用，`GET /metrics` TextFormat + `GET /jobs/{jobId}/checkpoints` REST）：
  - job 族：`nop.stream.engine.checkpoints.completed` / `checkpoints.failed` / `checkpoints.aborted` / `checkpoint.duration` / `checkpoint.size.bytes` / `recoveries.total`（名表权威落点 `docs-for-ai/03-modules/nop-stream.md`）；
  - cluster 族：`nop.stream.engine.nodes.active`；
  - REST checkpoints overview/history（含 `failureCause`、时长）。
- **非指标直接可观察量**：durable EpochManifest epoch（checkpoint 存储目录）、S2 输出行数（epoch 文件）、S1 sink 表行数（SQL COUNT）、`nop_stream_msg_queue` 表深度（队列水位代理）、进程存活（`ProcessHandle`）。
- **显式缺口（不静默，随 Phase 5 缺陷清单路由 Follow-up 候选）**：
  1. TM 进程侧 io 层指标（`nop.stream.io.emit.time` / `operator.processing.time` / `io.records.emitted.total`）在多 JVM 模式下不经 JC 暴露——`TaskManagerMain` 无 ops 端点、无 TM→JC 指标上报通道（live 核对：`StreamMetricsReporter` 为进程内周期 sink；无跨进程 metric transport）。因此背压格量化代理 = JC checkpoint 时长趋势 + 输出行增速 + durable epoch 推进速率（与节流档位关联），而非 runbook §7 所列 TM 侧 io 名。
  2. 队列水位直测 gauge 缺（runbook §7 已记，归属本 item 采样装置裁定）→ 代理 = `nop_stream_msg_queue` 表 `COUNT(*)` 周期采样。
- **再裁定钩子处置**：钩子条件为「item 16 在 Phase 1 之后、Phase 3 之前落地」——实际 item 16 在本 plan 启动前已 completed，钩子未触发；观察模式自始为上列直接指标 + 代理组合，全矩阵 run 间一致，无中途升级。

## 演练矩阵定义

通用约定：

- 全部经 **Phase 2 参数化联合入口** `io.nop.stream.fraud.scenario.TestStabilityExerciseMultiJvm`（gated）执行；参数取系统属性 `-Dexercise.*`，非法值（速率 ≤0、轮数 ≤0、时长低于下限、未知场景等）**fail-fast 报错，不静默使用默认值**。
- 演练命令**一律启用** `preserve-artifacts`（入口显式校验该属性为 true，否则报错拒绝执行——MiniStreamCluster 默认删除 runDir，不保留则 closure 抽样无据）。产物根 = `<repo>/_tmp/mini-stream-cluster/<runId>/`（logs / checkpoints / output / input + Phase 2 新增 `samples/`：采样 JSONL + 演练事件日志 + run-summary.json）。
- 集群基线：JC + 2 TM（ProcessBuilder 真实多 JVM，H2 AUTO_SERVER 共享库，LocalFileCheckpointStorage），checkpoint interval 2000ms（soak）/ 400ms（chaos/bp，与 C0—C3 一致），`maxRetained=5`。
- 期望集一律由负载生成装置**从同一生成计划推导**（单一事实源），断言用 multiset 语义（无重复无丢失同时可判）。

### SOAK-1 S2 持续负载 + 状态增长

- 场景：S2（file source → keyBy 窗口聚合 → 2PC 文件 sink，base XDSL）。
- 参数：源发射时长下限 **600s**；lineDelay=50ms（≈20 行/s）→ 12000 数据行；状态增长曲线 = 每 100 行引入 1 个新用户（u-0000..u-0119，共 120 个 keyed 用户，amount=10+(k mod 40)）；事件时间 T0+i×50ms（与墙钟 1:1）；尾部远未来 pump（u-keep 用户，使全部数据窗口 mid-run 关闭，pump 窗口留 in-flight 不入期望集——沿用 C0 的 watermark-pump 语义）。
- 复现命令：

```bash
./mvnw test -pl nop-stream/nop-stream-fraud-example -am -T 1C \
  -Dtest=TestStabilityExerciseMultiJvm#soakS2 \
  -Dnop.stream.test.multi-jvm.enabled=true \
  -Dnop.stream.test.multi-jvm.preserve-artifacts=true \
  -Dsurefire.failIfNoSpecifiedTests=false
```

（默认参数即矩阵值；显式覆写示例 `-Dexercise.soak.durationSec=600 -Dexercise.soak.lineDelayMs=50`。）
- 通过判据：① 终态输出 == 期望集（multiset，无重复无丢失）；② durable epoch 周期推进（采样序列中 epoch 严格递增，且无 >60s 的推进停顿——checkpoint 持续推进）；③ 全程 JC/TM 进程存活；④ 资源泄漏信号：`nop_stream_msg_queue` 深度不持续单调增长（有回落）、retained manifest 数 ≤ maxRetained 有界、日志无未处理异常风暴。
- 观察指标：`checkpoints.completed` / `checkpoint.duration` / `checkpoint.size.bytes` 时间序列、输出行增速、队列深度、epoch 序列、进程存活。
- 结局分类：三态（见下）。

### SOAK-2 S1 CDC 持续负载 + keyed 状态增长

- 场景：S1（CDC → 富化（keyed state）→ CEP 4 链 → 窗口 → 2PC JDBC sink，XDSL 原样）。
- 参数：emitDelay=100ms（≈10 ev/s）× 6000 数据事件 = **600s** 源发射时长；每 10s 事件时间窗口 1 个 burst 用户 `su-k`（2 笔 amount=1200、同城 PURCHASE → 恰好 1 条 RAPID_TRANSACTION 告警）+ 98 笔噪声（amount=50，非触发形态：金额 ≤1000 / 同城 / PURCHASE）；事件时间 T0+i×100ms（1:1 墙钟）；尾部 frank 噪声 pump（远未来，关最后的数据窗口，自身不入期望）。期望集 = 60 条 RAPID 告警（窗口 k × su-k），可由生成计划精确推导。
- 复现命令：同上，方法 `#soakS1`。
- 通过判据：① sink 表行集 == 期望集 **且 SQL COUNT(*) == 期望数**（Set 读取掩盖重复，故加计数）；② 四链 ledger 持续记epoch；③/④ 同 SOAK-1。
- 观察指标：同 SOAK-1 + ledger epoch 序列。

### SOAK-3 S2 高速率 burst（item 28 遗留核验观察点）

- 场景：S2。
- 参数：lineDelay=5ms（≈200 行/s）× 36000 行 = **180s** 持续较高速率；每 200 行 1 用户（180 用户）；期望集由生成计划推导。
- 复现命令：同上，方法 `#soakHighRateItem28Observation`。
- **item 28 核验观察点（证据采集规范，非修复）**：
  1. `nop_stream_msg_queue` 深度趋势（持续增长不回落 = 通道积压证据；回落 = 正常排空）；
  2. 停滞检测：durable epoch 不推进 **且** 输出行数不增长 **且** 进程全存活，持续 ≥60s = 疑似 dispatch 阻塞（hang 信号）；
  3. TM 日志 grep dispatch/queue-full/背压类告警锚点；
  4. 结果完整性 + checkpoint 推进（同 SOAK-1 ①②）。
- 通过判据：①②（同 SOAK-1）；③ 显式记录「hang 未触发（附采样证据）」或按 hang 处置规则核验 item 28 归属（见结局分类）。

### CHAOS-1 S2 随机 kill TM 循环（含分区等价轮）

- 场景：S2。
- 参数：**8 轮**；每轮时点 = 上轮恢复确认后随机延迟 U[20s, 60s]（种子化 RNG，schedule 落档可复现）；轮 1—6 变体 = 随机 kill tm-0/tm-1（SIGTERM）→ restart；**轮 7—8 = 分区等价形态**：SIGSTOP 暂停 TM 15—25s（JDBC 轮询面失联等价 → 租约到期触发恢复）→ SIGCONT 恢复（陈旧视图复活，fencing 拒绝可观察）后正常 restart。每轮断言：fencing epoch 严格递增（相对上轮）+ 恢复日志锚点；kill 前置 = durable manifest 已存在且源仍在发射（fixture 数据量按 ≥ 8×60s + 恢复余量 ≈ 9 min 发射时长配置，lineDelay=100ms）。
- 复现命令：同上，方法 `#chaosKillLoop`。
- 通过判据：① 每轮 fencing 严格递增留档（演练事件日志）；② 分区等价轮：恢复期后陈旧 epoch 控制面 mutation 被拒（日志锚点）；③ 终态 == 期望集（multiset）；④ kill 全部落在发射期内（源未提前耗尽）。
- 网络分区模拟三态裁定（依据 wire 后端能力，live 依据：控制/数据面 = SysDao JDBC 消息表轮询（`PollingJdbcMessageService`），无 TM↔JC 直连通道，共享 H2 单库）：
  - **可模拟等价形态**：节点失联 + 陈旧复活（SIGSTOP/SIGCONT）——轮询后端下「失联」与「分区」在观察面不可区分（无心跳/无消费/租约到期），复活后的陈旧 mutation 由 fencing 拒绝（R-14 语义）。轮 7—8 执行。
  - **不可模拟项（显式记录）**：IP 级真实分区、非对称分区（JC 可达库而 TM 不可达且另有直连通道——无此拓扑）、split-brain 双主并发多数派（JDBC 单点共享库 + 租约表下无此形态）。
  - **替代观察代理**：split-brain 风险由 CHAOS-2 的租约 fencing 断言覆盖（leaderEpoch 严格递增 + 旧主 mutation 拒绝）。

### CHAOS-2 S2 HA JC 随机 kill（coordinator 故障变体）

- 场景：S2，**HA 模式**（JC leader-gated + standby，共享租约表）。
- 参数：**2 轮**；启动后 spawn standby（coordinator-1）；轮 1 = 随机延迟 U[20s, 40s] 后 kill 活跃 leader（coordinator-0）→ 断言 standby 接管（租约 leaderId 翻转 + leaderEpoch 严格递增 + 任务重部署 fencing epoch 严格递增）；轮 2 = spawn 新 standby（coordinator-2）后 kill 现任 leader（coordinator-1）→ 同断言。发射时长 ≥ 5 min（lineDelay=100ms）。
- 复现命令：同上，方法 `#chaosJcHaFailover`。
- 通过判据：每轮租约翻转 + 双 epoch（租约 epoch 与 assignment fencing epoch）严格递增；终态 == 期望集。

### BP-1 S2 节流档位递增（backpressure 行为观察）

- 场景：S2 + `ThrottledScenarioSinks` 扩展（**档位文件**：sink 每记录消费前轮询档位文件当前 ms 值；文件缺失/非法值 fail-fast）。
- 参数：档位序列 50ms → 200ms → 500ms（每记录），每档观察窗 **≥60s**；观察窗结束后写 release 标记全速放行；lineDelay=50ms（20 行/s 输入，500ms 档构成显著背压）；发射时长 ≥ 档位总窗 + 收尾（≈ 4 min）。
- 复现命令：同上，方法 `#backpressureSteppedThrottle`。
- 通过判据：① 每档窗口内 durable epoch 至少推进 1 次（节流期间 checkpoint 持续前进，无死锁——C3 语义的多档扩展）；② 输出行增速随档位回落（JC 面代理量化：各档窗口内输出行增量序列留档；非硬数值门禁，但必须有序列证据）；③ 无 >90s checkpoint 停顿；④ 释放后终态 == 期望集（multiset）。
- 观察指标：每档窗口的输出行增量、checkpoint.duration 趋势、epoch 推进计数；TM 侧 io 指标缺口见「观察模式裁定」。

## 结局分类（每格必须落三态之一）

- `pass`：该格以**完整矩阵参数**执行且通过判据全成立，产物留档。
- `fail`：出现新缺陷（判据不成立且不命中已知边界）→ 缺陷条目（现象、复现命令、日志/产物锚点）+ Follow-up 工作项路由（roadmap 自进化规则），**不就地修引擎**。
- `triggered-known-defect`：命中既有已知边界（如 item 28 队列满阻塞、item 25 manifest 无校验和）→ 证据锚点 + 归属路由确认即闭环；不算通过、不阻塞 phase 收口。
- 闭环条件：每格最终判定必须对应一次**完整参数执行的留档 run**；演练**基建**缺陷就地修复后按**原参数**重跑合法；**降档**（缩时/降速/减轮）后标 pass **不允许**。
- **hang 处置规则**（soak/chaos/bp 通用）：出现疑似 hang（停滞检测命中）→ 先按 runbook §7① 核验 item 28 归属（队列深度、dispatch 阻塞日志、进程存活证据留档）→ 再判定新缺陷与否。不静默绕过、不越权修复。

## 资源预算与产物规范

- 单演练格墙钟上限 **20 min**（含 spawn/恢复/收敛等待）；全矩阵总预算 **120 min**（执行机器单测并发 1，串行执行）。超限 = 该格 fail（或 hang 处置规则），记录后继续后续格。
- 产物：一律 `_tmp/`（禁系统 `/tmp`）；一律 `preserve-artifacts=true`。产物锚点记录进本报告结果章节 + `run-summary.json`（cell 名、参数、起止时间、结局、采样文件相对路径）。
- 复现命令与 runbook §5（现 runbook 演练章节）模板对齐（gated + `-Dtest` + preserve-artifacts）。

## 执行结果（Phase 3 / 4 追加）

（占位——Phase 3/4 执行后逐格追加：runId、执行时间、参数、观察数据摘要、结局判定、产物锚点。）

## 收口汇总（Phase 5 追加）

（占位——矩阵 × 结果逐格对账、缺陷/Follow-up 清单、runbook §7 证据更新。）

## References

- `ai-dev/plans/nop-stream-productization/2026-09-02-2216-2-stability-performance-exercise.md`
- `ai-dev/design/nop-stream/distributed-runbook.md`（§5 演练步骤 / §7 已知边界）
- `docs-for-ai/03-modules/nop-stream.md`（指标名表权威落点）
- `ai-dev/backlog/nop-stream-productization-roadmap.md`（item 15 / item 25 / item 28）
