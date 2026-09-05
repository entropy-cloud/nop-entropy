# 分布式稳定性与性能演练报告（soak / chaos / backpressure）

> Status: resolved
> Date: 2026-09-03
> Scope: nop-stream DISTRIBUTED 模式稳定性演练（roadmap item 15，plan `ai-dev/plans/nop-stream-productization/2026-09-02-2216-2-stability-performance-exercise.md`）
> Conclusion: 6/6 演练格全参数执行留档；1 格 fail（根因 item 28 继发）+ 5 格 triggered-known-defect（item 28）——持续流 ~800—1000 条跨 TM 记录后数据面永久停摆（无回压传导 + recovery cap 耗尽继发失效），控制面租约 failover 与低档背压健康；证据锐化路由 Follow-up 31/32，item 28 修复前分布式持续运行稳定性不可承诺。

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

### Phase 3 — soak 执行结果（2026-09-03，全部全参数留档 run）

| 格 | runId（`_tmp/mini-stream-cluster/`） | 墙钟 | 结局 | 关键观察 |
|----|----|----|----|----|
| SOAK-1 S2 600s+状态增长 | `1788380205294-1` | 902.6s | **triggered-known-defect（item 28）** | jam@+40s（最后 epoch 推进）；输出 6/120 行后冻结；仅 9 次 durable epoch（interval 2s）；queue 163→22275（24.2/s 持续涨，源持续写入不回压）；通道 `vertex-A→B` 16796 条未消费、`B→C` 4910 条；TM 日志 `RemoteInputChannel Interrupted while enqueueing` ×3（in-memory 队列满阻塞签名）；checkpoint timeout/abort 日志 ×114 |
| SOAK-2 S1 CDC 600s | `1788381152134-1` | 903.5s | **triggered-known-defect（item 28）** | jam@+60s（13 次 durable epoch 后冻结）；告警 5/60 行提交后冻结；queue→18681；同签名（RemoteInputChannel ×4、timeout/abort ×108） |
| SOAK-3 S2 高速率 200 行/s ×180s（item 28 核验格） | `1788382070775-1` | 481.8s | **triggered-known-defect（item 28）** | jam@+5s（仅 2 次 durable epoch）；输出 0 行；queue→41662（36000 数据行全量滞留 + 放大）；timeout/abort ×62 |

**item 28 遗留核验结论（三格证据合并，归属确认非新缺陷）**：

1. **触发阈值定量**：remote-deploy JDBC 数据面在跨 TM 通道累计 ~800—1000 条记录后永久停摆（20 行/s jam@40s≈800 条、10 ev/s jam@60s≈600 事件、200 行/s jam@5s≈1000 条）——与 item 28 已知「1024 槽队列满后 dispatch 线程永久阻塞」机制定量吻合。
2. **停摆签名（runbook §7① hang 判据命中）**：durable epoch 冻结 + 输出冻结 + 进程全存活 + `nop_stream_msg_queue` 单调增长 + TM 日志 `RemoteInputChannel -- Interrupted while enqueueing decoded element`（consumer 侧 in-memory 队列满阻塞、shutdown 时被打断的直接证据）。
3. **无回压传导**：源侧不经 JDBC transport 感知下游拥塞（SOAK-1 中 12000 行全部写完、通道积压 16796 条）——JDBC 消息表为无界缓冲，生产侧全速、消费侧死亡。
4. **队列水位代理观察（Phase 1 缺口②的替代面）**：`nop_stream_msg_queue` COUNT 采样曲线清晰刻画 jam 时点与增长速率（健康短跑亦有 ~34/s 残留增长——全对订阅派生垃圾消息，见 Phase 2 smoke）。
5. **健康基线对照**：520 记录（20 行/s×20s+pump）同管线完整收敛 exactly-once（Phase 2 smoke，runDir `1788378479512-1` 系列）——缺陷为流量阈值触发，非管线语义错误。
6. **归属路由确认**：机制族（全对订阅 + 队列满阻塞 + 通道收敛缺失）= roadmap Follow-up **item 28**（`todo`）；本演练不就地修复。kill/恢复变体下的同族停摆见 Phase 4。

**观察模式实况**：JC /metrics 直接指标采集成功（`checkpoints.completed` 等族全 run 可用，samples.jsonl 内含）；REST checkpoints history 未采样（metrics 面已覆盖判据所需；TM 侧 io 层指标如 Phase 1 裁定不可达）。

**基建就地修复（Phase 2/3 期间，原参数重跑合法闭环）**：pump 尾部单窗不自闭合锚定（否则泵自身窗口会闭合提交污染期望集）、chaos-events.jsonl CREATE+APPEND、queue 泄漏启发式改「run 尾仍活跃增长」判定（平台期残留≠无界增长——残留本身作为 item 28 证据记录）。

### Phase 4 — chaos 与 backpressure 执行结果（2026-09-03，全部全参数留档 run）

| 格 | runId | 墙钟 | 结局 | 关键观察 |
|----|----|----|----|----|
| CHAOS-1 S2 随机 kill TM ×8（含轮 7—8 分区等价） | `1788383926464-1` | 992.2s | **fail→归因 item 28 根因（控制面继发失效）** | 轮 1—2 kill/restart 正常恢复（fencing 1→2→4，恢复断言全过）；轮 3—8 fencing 停在 4——协调器日志揭示机制：jam 诱发 `taskStall=true` 自动 global recovery ×3 耗尽 **recovery cap(=3)**，此后真实节点 kill/租约到期均无法再触发恢复（`Starting global recovery #3 (cap=3)` 后再无 recovery）；分区等价轮 7—8：SIGSTOP 期间租约到期亦无 fencing 轮转（`leaseExpiryRotatedFencing=false`），SIGCONT 恢复后无陈旧视图 mutation 可拒（无新 epoch） |
| CHAOS-2 S2 HA JC kill ×2 | `1788385016277-1` | 1050s | **triggered-known-defect（item 28；租约子判据正证据）** | **HA 租约 failover 完全正常 ×2**：kill leader → 租约翻转 leaderEpoch 1→2→3 严格递增（coordinator-0:1 → coordinator-2:2 → coordinator-3:3，替代 standby/新 spawn 竞选胜出）——控制面租约独立于数据面 jam 存活（正向产品化证据）；但 failover 后 task_assignment fencing 未轮转（1000000 不变，新 leader 未重发 assignment——jam 抑制了重部署触发）；终态收敛失败（jam） |
| BP-1 S2 节流档位 50/200/500ms | `1788386243853-1` | 184.4s | **triggered-known-defect（item 28）** | 档位序列量化：50ms 档 60s 窗口 checkpoint 推进 ×18 + 输出 +6 行（**C3「节流期间 checkpoint 持续前进」语义在 50ms 档成立**）；200ms/500ms 档推进 ×0（累计记录跨过 ~1200 条阈值，item 28 jam 在档位窗口中命中）；释放后终态不收敛（jam）——**背压本身不是死锁触发器，累计流量才是** |

**分区模拟裁定项对账（Phase 1 三态裁定 vs 执行）**：

- ✅ 可模拟等价形态（SIGSTOP/SIGCONT 失联+陈旧复活）：CHAOS-1 轮 7—8 执行并留档（`chaos-events.jsonl` 含 `leaseExpiryRotatedFencing`/`staleViewRejectedOnResume` 字段）；本轮基线中因 recovery cap 已耗尽，租约到期不触发 fencing 轮转——该**负结果本身即证据**（健康基线下的正断言由既有 C1/R-14 测试覆盖）。
- ❌ 不可模拟项（未执行，Phase 1 已显式记录）：IP 级真实分区、非对称分区（无 TM↔JC 直连拓扑）、split-brain 双主并发多数派（JDBC 单点共享库 + 租约表无此形态）。
- ✅ 替代观察代理：split-brain 风险由 CHAOS-2 租约 fencing 断言覆盖（leaderEpoch 严格递增 ×2 验证通过）。

**继发控制面失效（新证据，归因 item 28 修复范围）**：jam 的 taskStall 自动恢复 ×3 耗尽 recovery cap 后，真实节点故障永久不可恢复（CHAOS-1 轮 3—8）——item 28 的修复必须覆盖「stall 诱发的恢复预算耗尽 vs 真实故障恢复」的区分，否则通道收敛修复前高流量作业在 3 次 stall 恢复后即丧失容错能力。

## 收口汇总（Phase 5）

### 矩阵 × 结果逐格对账（Phase 1 定义 → 执行 → 结局）

| 格 | 定义参数（全参数执行） | runId | 结局 | 归属路由 |
|----|----|----|----|----|
| SOAK-1 | S2 600s/20 行/s/120 用户/ckpt 2s | `1788380205294-1` | triggered-known-defect | item 28（+Follow-up 31 证据） |
| SOAK-2 | S1 600s/10 ev/s/60 窗/ckpt 2s | `1788381152134-1` | triggered-known-defect | item 28（同上） |
| SOAK-3 | S2 180s/200 行/s（item 28 核验格） | `1788382070775-1` | triggered-known-defect（**item 28 核验取得显式证据：触发，非未触发**） | item 28（同上） |
| CHAOS-1 | S2 8 轮 U[20,60]s + 轮 7—8 SIGSTOP/SIGCONT | `1788383926464-1` | fail（根因 item 28 继发：recovery cap 耗尽致真实故障不可恢复） | item 28 修复范围扩展（Follow-up 31） |
| CHAOS-2 | S2 HA 2 轮 U[20,40]s kill leader | `1788385016277-1` | triggered-known-defect（租约 failover 子判据 = 正证据） | item 28（数据面）；租约面无缺陷 |
| BP-1 | S2 档位 50/200/500ms × 60s + 释放 | `1788386243853-1` | triggered-known-defect（50ms 档 C3 语义成立；200/500 档 jam） | item 28（同上） |

全部 6 格以**完整矩阵参数**执行且产物留档（`_tmp/mini-stream-cluster/<runId>/`：logs/checkpoints/output/samples.{jsonl}/chaos-events.jsonl/run-summary.json）；无降档重跑；无未判定格。

### 瓶颈/缺陷清单（引擎缺陷全部路由 Follow-up，未就地修复）

1. **remote-deploy 数据面持续流停摆（item 28 已知边界的定量证据锐化 + 修复范围扩展）** → Follow-up 31（新增，引用 item 28）：
   - 触发阈值：跨 TM 通道累计 ~800—1000 条记录（三速率点：200/s@5s、20/s@40s、10/s@60s）；
   - 停摆签名：epoch/输出冻结 + 进程全活 + `nop_stream_msg_queue` 单调增长（22k/18k/41k）+ TM `RemoteInputChannel -- Interrupted while enqueueing`；
   - 无回压传导：JDBC 消息表无界缓冲，源全速写入（12000 行）而消费侧死亡；
   - 继发控制面失效：jam 诱发 `taskStall` 自动恢复 ×3 耗尽 recovery cap(=3)，此后真实 kill/租约到期永久不可恢复（CHAOS-1 轮 3—8 直接证据）；
   - 全对订阅残留：健康短跑亦有 ~34/s 垃圾消息入队（队列水位代理面观察）。
2. **演练观察面缺口（Phase 1 裁定的显式缺口，非引擎缺陷）** → Follow-up 32（新增）：TM 侧 io 层指标多 JVM 不经 JC 暴露（无 metric transport/TM ops 端点）+ 队列水位直测 gauge 缺（代理 = msg_queue COUNT）。
3. **确认无异常的观察项**：HA 租约 failover 在数据面 jam 下仍严格递增轮转（CHAOS-2 ×2 轮，正向证据）；50ms 节流档下 checkpoint 持续推进无死锁（BP-1，C3 语义成立）；retained manifests 全程有界（≤ maxRetained 生效）；fencing 严格递增在 cap 耗尽前每轮成立（轮 1—2 + 分区轮前）。

### runbook §7 已知边界证据更新（Phase 5 落档）

- item 28 条目：由「有界 fixture、低速率未触发」升级为「演练已定量触发（阈值/签名/无回压传导/cap 耗尽继发）」+ 复现命令锚点。
- item 28 JDBC 后端 Stage-31（`loadRetainedEpochManifests`）：本轮全部演练仍为 LocalFileCheckpointStorage 路径，未触发、未静默绕过（再次核验记录）。
- 背压条目：BP-1 量化结果补记（50ms 档成立 / 高档 jam 归因 item 28 非背压死锁）。

### 结论

「C0—C3 短时正确性基线」在持续运行条件下**不成立**：当前 remote-deploy 数据面存在累计流量阈值（~10³ 条记录级）的永久停摆缺陷（item 28 家族），且其诱发的 stall 恢复会耗尽 recovery cap 导致容错能力整体失效。控制面（租约 failover、fencing 语义）与低速率背压行为健康。**分布式稳定性基线的建立被 item 28 阻塞**——Follow-up 31/32 路由后，item 28 修复完成前，任何 >10³ 记录的分布式持续运行场景不可承诺稳定性；短时基线（C0—C3，≤~500 记录）不受影响，维持既有结论。

## References

- `ai-dev/plans/nop-stream-productization/2026-09-02-2216-2-stability-performance-exercise.md`
- `ai-dev/design/nop-stream/distributed-runbook.md`（§5 演练步骤 / §7 已知边界）
- `docs-for-ai/03-modules/nop-stream.md`（指标名表权威落点）
- `ai-dev/backlog/nop-stream-productization-roadmap.md`（item 15 / item 25 / item 28）
