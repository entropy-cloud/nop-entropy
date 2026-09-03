# 2 演练观察面上收（TM 进程指标端点 + 通道队列水位直测 gauge）

> Plan Status: active
> Mission: nop-stream-productization
> Work Item: item 32
> Last Reviewed: 2026-09-04
> Source: roadmap `ai-dev/backlog/nop-stream-productization-roadmap.md` item 32（来源 item 15 演练报告 `ai-dev/analysis/2026-09/2026-09-03-distributed-stability-exercise-report.md` Phase 1 观察模式裁定显式缺口）
> Related: `ai-dev/plans/nop-stream-productization/2026-09-02-2216-1-observability-ops-productization.md`（指标标准集与 ops 端点的建立 plan）、`2026-09-03-1951-3-remote-deploy-dataplane-stability.md`（复验以 JC 面代理完成的裁定记录）

## Purpose

补齐分布式演练的两个观察面缺口：多 JVM 模式下 TM 进程侧 io/operator/task 指标可被直接刮取（背压量化不再依赖 JC 面代理），数据面通道队列水位有直测 gauge（不再以 `nop_stream_msg_queue` COUNT 为唯一代理），并把演练装置接到新观察面上。

## Current Baseline

（live 核对于 2026-09-04，行号为当日基线）

**指标基建现状**：
- 五层指标标准集已落地（owner doc `docs-for-ai/03-modules/nop-stream.md` 指标名表）。io/operator 层经 `MicrometerStreamTaskMetrics`（core `metrics/MicrometerStreamTaskMetrics.java:23-28`，含 `nop.stream.io.emit.time` 等）注入：LOCAL 在 JC 进程内（`GraphModelCheckpointExecutor` :614-626），REMOTE 在 TM 进程内（`TaskManager` :421-426/:589-593）。
- `StreamMetricsRegistries`（core `nop-stream-core/.../metrics/StreamMetricsRegistries.java`）是**进程级静态** CompositeMeterRegistry（首个成员为进程内 SimpleMeterRegistry，:32-34；刻意不用平台 GlobalMeterRegistry，javadoc :22-28）——TM JVM 注册的 meters 在 JC JVM 不可见，无跨 JVM 成员。
- `StreamOpsHttpServer`（runtime `nop-stream-runtime/.../ops/StreamOpsHttpServer.java`，main scope）：JDK HttpServer，TextFormat 0.0.4 默认 + OpenMetrics 协商（:133-162），start 时把 PrometheusMeterRegistry 附到 composite（:87-91）；配置键 `nop.stream.ops.http.enabled`（默认 false）/.port（8901）/.bind/.metrics.enabled（`ops/StreamOpsConfig.java:18-29`）。**仅 JC 侧构造**：`JobCoordinatorMain`（test-scope launch harness，`opsHttpPort` raw arg :316-344）+ 测试；`TaskManagerMain`/`TaskManager` 无任何引用。`/jobs` 族 handler 依赖 `IOpsJobRegistry`/`OpsJobManager`（coordinator 形态）。
- `TaskManagerMain`（test scope，`nop-stream-runtime/.../launch/TaskManagerMain.java`，start :74-129）：JDBC/消息服务/注册表 + `TaskManager` + task RPC server + coordinator RPC proxy；**无 ops 端点、无 metrics reporter、无 TM→JC metric transport**。TM 已把 `TaskNodeMetrics`（task 层）与 `MicrometerStreamTaskMetrics`（io/operator 层）注册进**自身进程** registry——数据在，只缺暴露面。

**TM→JC 既有上行通道**（供形态对比，非本计划改造对象）：`IStreamCoordinatorRpcService`（checkpointAck/taskStatus/5s 心跳 `reportNodeTaskLiveness(nodeId, List<TaskProgress>)`/terminate/abortCheckpoint）；`TaskManager.heartbeat()` :268-311。

**队列水位现状**：
- `nop_stream_msg_queue` 是**演练基建表**（test scope `PollingJdbcMessageService`，DDL :108-116）：INSERT-only，消费后**不删除行**（游标式 SELECT，`Subscription.poll()` :305-338）；生产对应物为 `SysDaoMessageService`（不同表命名空间）。因此 COUNT 只能作代理。
- 演练采样：`ExerciseSampler`（fraud-example test，`SampleSources` SPI :48-60）写 `samples/samples.jsonl`；`ClusterSampleSources.fetchQueueDepth()` = `SELECT COUNT(*)`（`StabilityExerciseSupport` :313-327）；`fetchMetrics()` 仅刮 JC 端点（:286-307）。泄漏判定启发式 `queueDepthUnboundedGrowth`（:233-278）已校准为"深度增长 + epoch 无推进"耦合（2026-09-04，runbook :131 记录）。
- `RemoteInputChannel`（runtime `nop-stream-runtime/.../transport/RemoteInputChannel.java`，main scope）：有界队列容量 1024（:77/:95），enqueue 10s 有界等待 + `ERR_STREAM_CHANNEL_OVERFLOW`（items 28/31 D2）；**已暴露 `queueSize()` :476-478 / `isOverflowed()` :485-487 等方法但未注册任何 gauge**（queueSize 仅被 `TestRemoteDataExchange` 引用）。

**真正剩余的 gap**：① TM 进程无指标暴露端点（数据已在 TM registry，缺 HTTP 面）；② RemoteInputChannel 队列深度无 gauge（方法已有，缺注册与命名归属）；③ 演练装置 fetch 面单一（JC-only metrics + COUNT 代理），新观察面落地后未消费。

## Goals

- TM 进程本地指标 HTTP 端点：多 JVM 模式下 task/operator/io 层指标可从 TM 进程直接刮取（含 `nop.stream.io.emit.time` 等背压代理指标）。
- 数据面通道队列水位直测：RemoteInputChannel 队列深度注册为进程内 gauge，随 TM 端点暴露，值随真实 enqueue/dequeue 变化。
- 演练装置消费新观察面：SampleSources 扩展 TM 面抓取；背压/泄漏量化有直接证据源（COUNT 代理保留为对照/后备）。

## Non-Goals

- 不做 TM→JC 指标 push transport 或 JC 侧跨进程聚合/federation（形态裁定见 Phase 1 D1，拒绝理由落档）。
- 不新建监控平台、不改平台 metrics 设施（`StreamMetricsRegistries` 进程级语义保持）。
- 不改 `nop_stream_msg_queue` INSERT-only 语义与泄漏启发式口径（COUNT 代理保留；gauge 为增量证据源）。
- 不做七态健康状态机的 TM 侧迁移（health 归属 JC 保持）。
- 不做生产部署级 TM 入口（TM 主入口现为 test-scope launch harness；main-scope 可复用性以 StreamOpsHttpServer/TaskManager main-scope 类承载，入口归属沿用 item 17 D1b 的 test-scope 如实标注模式）。

## Scope

### In Scope

- `nop-stream-runtime` main scope：`StreamOpsHttpServer` 复用确认（live 已支持 null-registry 构造：`/jobs` list/submit/stop 返回 503 REGISTRY_UNAVAILABLE、detail/checkpoints 404——见 `TestStreamOpsHttpServer.java:75/:109` 先例，预期服务端本体零或近零改动）；RemoteInputChannel 队列水位 gauge 注册；指标命名与归属层裁定。
- `nop-stream-runtime` test scope：`TaskManagerMain` ops 端点接线（launch args 对齐 JC 的 `opsHttpPort`/`opsHttpBind` 模式）；**`nop-stream-runtime/.../multijvm/MiniStreamCluster.java`（下称 `MiniStreamCluster`） TM 参数透传扩展**（现 `spawnTaskManager` :458-467 硬编码 5 参数、仅 `withCoordinatorArg` :498-503 一个透传机制——需增加 TM arg 透传 + TM 端口 getter 暴露 + `restartTaskManager` 复用同端口）。
- `nop-stream-fraud-example` test scope：`ExerciseSampler`/`ClusterSampleSources` 扩展（TM 端点列表抓取 + queue gauge 采样；SampleSources 是 interface，扩展方式须兼容 `TestExerciseSampler` 的 fake 实现——default 方法或同步适配 fakes）。
- owner docs：`docs-for-ai/03-modules/nop-stream.md`（指标名表 TM 暴露面 + 新 gauge 条目 + 配置键）、`ai-dev/design/nop-stream/distributed-runbook.md`（演练观察面章节）、`metrics.properties.template`（TM 侧说明，如适用）。

### Out Of Scope

- `nop-stream-core`（registry 语义不变；`StreamMetricsRegistries` 不动）。
- JC 侧 REST/告警/治理面的任何变更。
- item 33（manifest retention）/ item 34（HA 接管）内容。

## Execution Plan

### Phase 1 - TM 本地指标端点（复用 StreamOpsHttpServer 的 TM 形态）

Status: planned
Targets: `nop-stream-runtime/.../ops/StreamOpsHttpServer.java`、`nop-stream-runtime/.../ops/StreamOpsConfig.java`、`nop-stream-runtime/.../launch/TaskManagerMain.java`、相关测试

- Item Types: `Decision | Fix | Proof`

- [ ] Decision D1（暴露形态）：采用 **TM 进程本地 pull 端点**（每 TM 进程暴露 `/metrics`），拒绝 TM→JC push transport。理由落档：pull 模型与 Prometheus 架构一致（每进程一个 scrape target）、零 RPC 面扩张（不动 `IStreamCoordinatorRpcService`）、MiniStreamCluster 演练同机可刮、与 JC 端点行为对称；push 需新增 RPC 契约 + JC 侧聚合/基数管理，收益不抵复杂度。
- [ ] TM 端点接线（复用既有 null-registry 构造形态，`/jobs` 族维持既有 503/404 结构化错误语义，不新造"TM 专用构造"）：`TaskManagerMain` launch args 增加 TM ops 端口/bind（对齐 JC raw-arg 模式）；端口显式传入且**避开 JC 默认 8901/演练 8931**；端点默认关闭（显式开启语义与 JC 一致）；启动/关闭随进程生命周期。
- [ ] 单测/HTTP 集成测试：TM 形态端点启动后 `GET /metrics` 返回 200 + Prometheus 文本；无 registry 时 `/jobs` 族返回既有 503/404 结构化错误（不断言统一 404——live 语义是 list/submit/stop 503、detail 404）；Accept 协商生效；端点默认关闭。
- [ ] **接线验证**：在含真实任务执行的 TM 进程内（单测内嵌 TaskManager 或 e2e 前置）断言 `/metrics` 输出包含 io/operator/task 层指标——**断言用 Prometheus wire 形态名**（如 `nop_stream_io_emit_time_seconds_count`/`_sum`、`nop_stream_io_records_emitted_total`；timer 输出带 `_seconds_*` 后缀、counter 带 `_total`，与 `TestMetricsExposureE2E.java:157-163` 先例一致），不用 dot 形态（wire 上不存在）——证明端点连到的是任务真实注册的 registry，而非空 registry。

Exit Criteria:

- [ ] TM 进程端点可刮取 task/operator/io 层指标（集成测试断言 wire 形态指标名出现）
- [ ] 无 registry 端点维持既有 503/404 结构化错误语义，无静默空实现
- [ ] 形态 Decision D1 连同拒绝 push 的理由落 `docs-for-ai/03-modules/nop-stream.md`（暴露面章节）或 design doc
- [ ] owner docs 更新：指标暴露面（JC + TM 双端点）、TM 端配置/launch 参数；`distributed-runbook.md` 增补 TM 端点刮取方法
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 通道队列水位直测 gauge

Status: planned
Targets: `nop-stream-runtime/.../transport/RemoteInputChannel.java`、`nop-stream-runtime/.../transport/RemoteGraphExecutionPlanBuilder.java`、指标命名归属、相关测试

- Item Types: `Decision | Fix | Proof`

- [ ] Decision D2（命名与归属）：gauge 名（建议 `nop.stream.io.channel.queue.size`，归属 io 层；或独立 transport 命名空间——执行时对照指标名表裁定）与 tag 集（jobId/edgeId/sourceSubtask/targetSubtask 或其子集；基数与聚合可用性权衡落档）。
- [ ] Decision D2b（**注册范围**）：`RemoteGraphExecutionPlanBuilder.buildRemoteOnly` 在每个进程都构建全通道矩阵（JC 零订阅、每 TM 全矩阵但只订阅本 subtask 输入 topics）——gauge **只对 `subscribe==true` 的通道注册**，避免 JC 端点与 TM 上出现整面恒 0 的 construct-only 噪声 gauge（不改变 JC `/metrics` 契约面）。
- [ ] Decision D2c（**重复注册/关闭语义**）：global recovery 重新 assign → TM 重新 deploy → 再次 build → 同名同 tag gauge 二次注册时 micrometer 会静默丢弃新绑定返回旧 meter（旧通道 close 后 → NaN/冻结值）。裁定并实现防洞形态（可变 holder + 替换绑定、或注册前 remove 旧 meter、或 close 时 remove——与"通道关闭时语义"合并裁定），单测覆盖重注册场景。
- [ ] gauge 注册：在通道构建/生命周期归属点把 `queueSize()` 绑定为 gauge 注册进进程 registry；注意 micrometer gauge 弱引用语义（持有强引用或可变绑定）。
- [ ] 单测（**接线验证**）：enqueue N 条后 gauge 读数 = N（经 registry 读取，非直调 queueSize；注意进程级静态 registry 跨测试残留——unique tag 或测试间清理）；消费/取走后读数下降；**重注册用例**：模拟 recovery 重 build 后 gauge 反映新通道的真实值（无 NaN/冻结）；溢出状态 `isOverflowed` 可另设 gauge 或 tag（执行时裁定，保持指标面最小）。
- [ ] gauge 随 TM 端点可见：在 Phase 1 接线验证用例中扩展断言通道 gauge 出现在 `/metrics` 输出（wire 形态名）。

Exit Criteria:

- [ ] 队列水位 gauge 在进程 registry 中可读且随真实队列操作变化（非恒定值、非弱引用丢失的 NaN、重注册后无冻结旧值）
- [ ] 命名/标签/注册范围/重复注册与关闭语义（D2/D2b/D2c）裁定落 owner doc 指标名表（`docs-for-ai/03-modules/nop-stream.md`）
- [ ] 单测覆盖注册、取值跟随、重注册（recovery 重 build 场景）、（如裁定）关闭移除
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 演练装置消费 + gated 多 JVM e2e

Status: planned
Targets: `nop-stream-fraud-example/.../scenario/ExerciseSampler.java`、`StabilityExerciseSupport.java`、`TestStabilityExerciseMultiJvm.java`（gated）、owner docs

- Item Types: `Decision | Fix | Proof`

- [ ] Decision D3（TM 端口分配与发现）：harness 分配 `tmOpsHttpPortBase`，第 i 个 TM 取 base+i（避开 JC 端口与并发 run 区段）；`MiniStreamCluster` 透传 TM args（M-1 项）并暴露每 TM 实际端口 getter；`ClusterSampleSources` 经 cluster 枚举 TM 端口列表（替换"只知 JC 一个端口"的现状：live `fetchMetrics` 靠 `exercise.opsHttpPort` 系统属性）。
- [ ] `MiniStreamCluster` 扩展：TM arg 透传机制（`withTaskManagerArg` 或等价）+ TM ops 端口注入 + 端口 getter + `restartTaskManager` 复用同端口（fencing/kill 演练路径不因重启换端口）。
- [ ] `ClusterSampleSources` 扩展：`fetchMetrics` 支持多端点（JC + 各 TM）；`fetchQueueDepth` 增加 gauge 来源（TM 面通道 gauge 聚合口径裁定：max/sum per edge，执行时定并在代码 javadoc/文档说明），COUNT 代理保留双记（对照观察，不删既有启发式）。samples.jsonl 的 TM 指标快照 key **用 Prometheus wire 形态名**（与 parsePrometheusText 解析结果一致；如需 dot 别名在 parse 侧统一规则，不逐处发明）。
- [ ] samples.jsonl schema 增量字段（TM 指标快照 + gauge 深度），向后兼容（新增字段不破坏既有解析/测试；SampleSources SPI 扩展用 default 方法或同步适配 `TestExerciseSampler` fakes）。
- [ ] gated 多 JVM e2e（**端到端验证**，`@EnabledIfSystemProperty` 沿用 `nop.stream.test.multi-jvm.enabled`）：跑一个背压 cell（**选用能饱和通道的节流档位（≥500ms 档）或调低采样间隔**，避免 5s 采样 + 温和档位下 1024 容量队列水位不明显的 flaky 断言）+ 一个短 soak cell，断言：① samples.jsonl 含 TM 面指标序列（wire 形态名非空）；② 通道 queue gauge 序列存在且在节流期呈现水位抬升（背压量化直接证据）；③ 既有判据（epoch 推进/结果完整性/泄漏启发式）不回退。
- [ ] runbook 演练观察面章节更新：TM 端点刮取步骤、gauge 口径、COUNT 代理降级为对照的说明。

Exit Criteria:

- [ ] gated e2e 留档（runDir 产物含 samples/chaos-events/run-summary），背压档位下 TM 面 emit.time 与 queue gauge 呈现可量化差异
- [ ] 演练装置双源采样（gauge + COUNT 对照）落地，既有泄漏启发式行为不变
- [ ] owner docs（nop-stream.md 指标表/暴露面 + distributed-runbook.md 观察面章节）同步完成
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 两个观察面缺口（TM 指标暴露 / 队列水位直测）落地且各有接线级与 e2e 级证明
- [ ] TM 端点默认关闭语义、无 registry 端点显式结构化错误（503/404，沿既有语义）、gauge 非恒定值（无静默 no-op）
- [ ] 指标名表/暴露面/配置键 owner doc 同步；runbook 观察面章节更新
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿（默认态）
- [ ] gated 启用态演练套件绿（含新增 e2e cell）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` exit 0（fraud-example 测试面有改动则加跑 `--module nop-stream/nop-stream-fraud-example`）
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] 独立子 agent closure-audit 完成并写入证据（含 Anti-Hollow：TM registry→端点→samples 全链运行时连通）

## Deferred But Adjudicated

（起草时无；执行中如出现按 Allowed classifications 分类并附理由）

## Non-Blocking Follow-ups

- （候选，视 Phase 3 观察结果）若生产部署将来出现非同机 TM，Prometheus 联邦/远程写入属部署侧配置，不在本计划内

## Closure

Status Note: (待执行收口时填写)
Completed: (未完成)

Closure Audit Evidence:

- Reviewer / Agent: (待 closure audit)
- Evidence: (待 closure audit)

Follow-up:

- (待收口时裁定)
