# 2335 WI3 Kafka 原生 exactly-once 连接器（nop-stream-connector-kafka）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI3（M1 P0）+ 裁定 `ai-dev/design/nop-stream/multi-engine-gap-adjudication.md` §三 R1
> Related: TwoPhaseCommitSinkFunction 范式（plan 368/369/2306 交付的 JdbcTwoPhaseCommitSink 契约面）；productization roadmap（SPI registry 既有面）

## Purpose

新增专用连接器模块 `nop-stream-connector-kafka`：Kafka source offset 入 checkpoint（REPLAYABLE）+ 事务 sink（checkpoint barrier 处融合提交的 epoch 事务，全窗口零丢失；零重复在残余窗口外成立——见 Current Baseline 语义边界与 Deferred pin），kill-recover E2E 在窗口外 kill 点钉定跨运行零重复零丢失主路径；R1 边界（与 nop-message-kafka at-least-once 通道的适用判据）写入 owner doc。

## Current Baseline

> 核实时点 2026-10-07，含第一/二轮审查修订（M2 处置记录见下）。
> **M2 处置记录**：模块骨架（pom、4 主类、beans.xml、双 pom 挂入）已在本 plan 置 active 前先行落盘（untracked）——第一轮对抗性审查（agent_1e0ad975-3448-4e3d-9262-99d2eb0d7a3d）恰好证明该流程违规的代价：B2（source 漏载重接口语义）即「plan 缺规格 → 实现已踩坑」的实证。处置 = 审查发现全部落 plan 后修订实现（已完成：B1 javadoc/高水位守卫、B2 SubtaskShardedSourceFunction+记录义务、m3 EXPERIMENTAL），plan 修订后置 active，后续以本 plan 为准。

- **注册面**：连接器经 `/nop/stream/beans/connector-*.beans.xml` 被 `StreamConnectorCatalog` 扫描发现（非 Java SPI services）；工厂形态 = `IStreamSourceFunctionFactory`/`IStreamSinkFactory` + `ConnectorCapabilityDescriptor`（现成例子：`DebeziumCdcSourceConnectorFactory` TYPE_NAME="debezium-cdc"、`JdbcTwoPhaseCommitSinkConnectorFactory` TYPE_NAME="jdbc-2pc"）。当前 5 beans 文件 8 端点（`TestStreamConnectorRegistryDiscovery`）。
- **source 范式**：Kafka offset 是 partition→offset map，不走 `ReplayableSourceFunction` 单 long 游标——按 `DebeziumCdcSourceFunction` 范式走 `CheckpointedSourceFunction` 自管：`snapshotState` 用自定义 operator-state key `putOperatorState`，`initializeState` 读回；`StreamSourceOperator` 对 Checkpointed 分支只 merge 结果并调 initializeState（restoreState :389-400），不代管。
- **sink 范式**：`TwoPhaseCommitSinkFunction`（beginTransaction/invoke/preCommit/commit/rollback 五抽象 + `CheckpointParticipant` 四方法）；`JdbcTwoPhaseCommitSink` 的 `copyForSubtask(TaskLocation)` namespace 化与 `saveState` 先移 buffer 入 pendingCommits 模式可复制；**公共 API 下 Kafka 事务无法跨重启 resume**（Flink 依 producer 内部 API）——re-send 恢复会在「已提交窗口」产生重复，「跳过恢复」会在「未提交窗口」产生丢失，二者都不可证明。
- **barrier 融合提交设计（2026-10-07 第一轮对抗性审查 B1 修订后的诚实口径）**：`saveState(epoch)` 运行在 checkpoint barrier 内（`StreamSinkOperator.processBarrier`，saveState :117 先于 prepareCommit :127）——`flush + commitTransaction + beginTransaction(next) + pendingCommits 标记`融合在 saveState 点，标记仅在 commitTransaction 成功后写入（失败即 checkpoint 失败，无未提交脏标记）。**语义边界（如实声明，不宣称全窗口可证）**：`kafka-clients` 公共 API 无法跨重启 resume 事务，故 (a) **零丢失可证**——barrier 前崩溃 = 悬挂事务被 initTransactions abort + source 从上个 checkpoint 重放补齐；barrier 后崩溃 = 数据已可见；(b) **零重复存在一个有界残余窗口**——崩溃落在「本 barrier commitTransaction 已成功、checkpoint 尚未 durable」区间时，恢复点早于本 barrier，source 重放该 epoch、sink 重发 → 重复可见。窗口 ≈ 一次 broker commit RPC + checkpoint 持久化（同步 persist 时仅 RPC）；无事务 resume 则不可关闭，按 checkpoint-design §6.4 残余模式 pin-and-record（sink javadoc + owner doc 显式声明），E2E 的 kill 点选在窗口之外（已完 checkpoint 后、下一 barrier 前的注入失败），钉定可确定性验证的主路径。
- **testcontainers 先例**：`KafkaBrokerE2ETest`（nop-message-kafka）——`org.testcontainers.kafka.KafkaContainer` + `apache/kafka:4.3.1`（KRaft），Docker gate `assumeTrue` SKIP 可见 + opt-out `-Dnop.test.message.e2e.kafka=false` + 镜像前缀 `docker.m.daocloud.io/` 属性覆盖；testcontainers BOM 2.0.5；本机 Docker 实测可用。
- **nop-message-kafka 现状（R1 边界对象）**：`KafkaMessageService`（IMessageService 后端）at-least-once；stream 侧经 `MessageSourceFunction`/`MessageSinkFunction`（nop-stream-connector）适配，无 checkpoint 状态，双方 `AT_LEAST_ONCE` 声明。
- **模块挂点**：`nop-stream/pom.xml` modules 列表（:17-27）；新模块 pom 仿 `nop-stream-connector-jdbc`（parent nop-stream、compile 依赖 nop-stream-core、test junit）；testcontainers 三件（testcontainers/testcontainers-junit-jupiter/testcontainers-kafka）仿 nop-message-kafka pom。

## Goals

- 新模块 `nop-stream-connector-kafka`（挂入 nop-stream modules + nop-bom）：
  - `KafkaSourceFunction<T>`：`implements SubtaskShardedSourceFunction<T>, CheckpointedSourceFunction<T>, ConnectivityCheckable`（**载重接口 = SubtaskShardedSourceFunction**——`StreamSourceOperator.copyForSubtask(int)` 只对该接口做函数级复制，缺它则 parallelism>1 各 subtask 共享模板实例 = 全量重复）；`validateParallelism(int)` 把部署并行度**记录**进模板实例（Debezium 先例），`copyForSubtask(int)` 按记录值做分区 round-robin 独占分片（空分片 = 无分区可分配、空 map 快照，与 watermark-idle 机制无关）；offset map（`TreeMap<Integer,Long>`，JSON 安全 String 键）入 operator-state key `kafka-offsets`；无 checkpoint 冷启动位 `startFrom=earliest|latest`（缺省 earliest）；value 反序列化函数注入（缺省 UTF-8 String）；`getSourceConsistency()=REPLAYABLE`；禁止 consumer 自动提交（true = typed 拒绝）；`implements DrainableSource`——`truncateForDrain()` 使 in-flight 批消费完后自然终态（有界重放形态与 E2E harness 用；缺省无界——第二轮审查 Major 修订：source 为无界 poll 循环，自然 EOS 经 DrainableSource 达成，终批由最后一个周期性 checkpoint 的 saveState 提交）。
  - `KafkaTwoPhaseCommitSink<T>`：`extends TwoPhaseCommitSinkFunction<T>, implements ConnectivityCheckable`；transactional.id = `prefix|jobId|vertexId|taskIndex`（经 `copyForSubtask(TaskLocation)` 稳定跨重启）；`beginTransaction` 首次 `initTransactions()+beginTransaction`、后续 `beginTransaction`；invoke=send；`preCommit=flush`；**`saveState(epoch)= commitTransaction+beginTransaction(next)+pendingCommits[epoch] 标记+super.saveState`（barrier 融合提交点；flush 由紧随的 preCommit 承担，commitTransaction 自身覆盖未决发送）**；`commit(epoch)`=幂等 no-op（epoch ≤ barrier 提交高水位——守卫用高水位而非可变 pendingCommits，使基类空 pending fallback 的重发 commit 保持无害 no-op；超过高水位的 commit = typed 拒绝契约违约）；`restoreFromEpoch` 先从重建的 pendingCommits 重放高水位再走基类 re-commit/abort；`rollback/abort`=abortTransaction/丢弃标记；`getSinkConsistency()=TWO_PHASE_COMMIT`。
  - SPI 工厂对：`KafkaSourceConnectorFactory`（typeName "kafka"，DESCRIPTOR sourceConsistency(REPLAYABLE)/recoverySemantic(OFFSET_CHECKPOINT)）+ `KafkaTwoPhaseCommitSinkConnectorFactory`（typeName "kafka-2pc"，TWO_PHASE_COMMIT）+ `connector-kafka.beans.xml` 注册。**成熟度（第二轮 m3 修订后按 WI4 语义强度判据落定）**：source = PRODUCTION（offset 进 checkpoint 完全引擎内建，判据直接命中）；sink = EXPERIMENTAL（commit-at-barrier 携带文档化残余重复窗口，保证尚未完全引擎内建——豁免说明写 owner doc；窗口关闭或被接受后按判据晋级）。
- E2E（testcontainers Kafka，Docker gate + opt-out + registry 前缀三属性，照 KafkaBrokerE2ETest 惯例）：100 条入 topic → source→sink 管道。**E2E 前置四钉（第二轮审查 Major 修订）**：(1) 显式 storage `path` 属性（auto-restore 仅在显式 path 下触发，缺省目录 = fresh start）；(2) `setAsyncSnapshotEnabled(false)` 同步快照（残余窗口 ≈ 一次 broker RPC 的前提）；(3) 两次提交用同一执行入口形态（均 `GraphModelCheckpointExecutor.executeWithCheckpoint(JobGraph...)`——StreamModel 条目带 fingerprint 而 JobGraph 条目无，混用触发 fingerprint fast-fail 拒绝 restore）；(4) 断言消费者 `isolation.level=read_committed` + `auto.offset.reset=earliest`（或显式 seekToBeginning）。**时序**：等待至少一个 checkpoint 完成（轮询 manifest 目录）后再经反序列化函数注入失败（kill 点在残余窗口之外，见 Current Baseline 语义边界）→ 同 jobId 重提交 auto-restore → **结尾（第二轮审查修订：source 为无界 poll 循环，自然 EOS 不可达；终批实际由最后一个周期性 checkpoint 的 saveState 提交——终态装配 final checkpoint 对全终态作业跳过 barrier fan-out，不经 sink barrier）**：经 read_committed 消费者轮询 topic-out 至静默 + ≥1 个 checkpoint interval 确认终批已入账，协作 cancel 使任务终态、execute 返回 → 断言 topic-out 每条 key 恰好一次（零重复零丢失）。
- 单测（非 gated）：offset map snapshot/restore 往返、冷启动位、分区 round-robin 分配、typed 拒绝面、sink epoch 生命周期（mock Producer：initTransactions 一次/每 epoch begin/saveState 提交/rollback/abort/re-commit 幂等）、工厂描述符与 beans 注册发现。
- owner doc：`docs-for-ai/03-modules/nop-stream-connectors.md` 能力矩阵增 kafka source/sink 两行 + R1 边界节（判据：是否需要流处理故障恢复语义——需要 = kafka 连接器；仅平台消息收发 = nop-message-kafka 通道）。

## Non-Goals

- Kafka Schema Registry / Avro（WI18）；exactly-once source 端 sendOffsetsToTransaction（本连接器是 source+sink 独立端点，非 consume-transform-produce 同事务链——如需事务链由上游管道组合）；Kafka source 分区再均衡（手动 assign 独占分区，无 consumer group 协调）；sink 的 key 提取策略只注入函数不内置路由。

## Scope

### In Scope

- 新模块骨架（pom + beans.xml + 工厂 + source/sink + 配置面）。
- 事务 sink 的 barrier 融合提交实现与 mock 单测。
- gated 真 broker E2E（kill-recover 零重复）。
- registry 发现回归（runtime 侧断言 8→10 端点）。
- owner doc 能力矩阵 + R1 边界节。

### Out Of Scope

- 事务链 consume-produce 同事务；分区再均衡/消费者组协议；schema registry；非紧凑型 topic 的 key 幂等语义；Kafka connect 集成。

## Execution Plan

### Phase 1 - 模块与连接器实现

Status: completed
Targets: nop-stream-connector-kafka（新模块）、nop-stream/pom.xml

- Item Types: `Feature`

- [x] 模块骨架 + KafkaSourceFunction（offset map checkpoint 往返、分区分配、冷启动位、反序列化注入、ConnectivityCheckable）
- [x] KafkaTwoPhaseCommitSink（barrier 融合提交 + pendingCommits 标记 + transactional.id 稳定化 + typed 拒绝面）
- [x] 双工厂 + connector-kafka.beans.xml + nop-stream/pom.xml modules 挂入
- [x] 单测：offset 往返/分配/mock producer epoch 生命周期/工厂描述符

执行发现（2026-10-08，E2E 调试全程）：

- **引擎语义（第三条，与 WI10 终结出数发现同族）**：self-contained source 的 checkpoint 触发邮件**只在 `ctx.collect` 发射点经 `drainControlMails()` 排空**（`StreamSourceOperator` 注释明示「Drain any trigger-checkpoint mail … Mirrors the legacy drainAndInjectPendingBarriers」）——source 空转 poll（无记录发射）期间 checkpoint 永不 ACK（"too many pending checkpoints 1/1" 连锁阻塞后续触发）。E2E 结构性结论：**有界回放/kill-recover 测试必须让记录持续流动**（两阶段播种：先等 ≥1 durable checkpoint 再注入 kill；round 2 尾部以 150ms filler 记录维持流直至断言键全部提交）。这也是真实作业「持续流量」形态下的隐含前提，记 owner doc cookbook。
- **E2E 断言键空间**：管线携带 record value（seed key "kNNN" 不进管道），sink keyExtractor=identity(value) → out-topic 提交键 = "vNNN"；filler 键 "v-fill-*" 须从断言 distinct 计数中排除。
- **plan 2330 deferred「Kafka source 限速接入」在本 WI 落地（closure audit M4 承接）**：KafkaSourceFunction 消费 core `SourceRateLimiter`（poll 批 acquire，限速即背压，随分片拷贝携带，负值 typed 拒绝），工厂参数 `rateLimitRowsPerSecond`/`rateLimitBytesPerSecond`；connectors.md 限速段同步（"由 WI3 接入" → "已由 WI3 落地"），2330 successor 义务清偿。
- round-2 恢复路径实证：initTransactions 在 restore 线程 fence 僵尸代（producerId=1/epoch=3，EndTxn abort 悬挂事务）✓ 与设计一致。

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-connector-kafka -am` 全绿（模块单测 16/16：Sharding 10 + Lifecycle 6）
- [x] **分片正确性**：validateParallelism 记录 + copyForSubtask 分片断言——两 subtask shard 不相交且并集 = 全部分区（B2 修复钉）
- [x] **无静默跳过**：sink commit 对超过高水位的 epoch typed 拒绝而非静默；source 自动提交开启 typed 拒绝；topic 不存在 checkConnection/poll typed 拒绝
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-08.md` plan 2335 条目）

### Phase 2 - E2E 与收口

Status: completed
Targets: 测试 E2E、owner docs、registry 回归

- Item Types: `Feature + Proof`

- [x] testcontainers kill-recover E2E（gated）+ runtime registry 发现回归（8→10 端点断言更新）
- [x] owner docs：connectors.md 能力矩阵两行 + R1 边界节
- [x] 独立子 agent closure audit；roadmap WI3 勾选；git commit

Exit Criteria:

- [x] **端到端验证**：kill-recover E2E 从 offset 恢复到跨两次运行零重复零丢失（docker 可达时真实绿；SKIP 仅在 docker 不可达时可见发生并显式记录）——**本机 docker 可达，E2E 真跑绿（非 SKIP）**：100 条记录 kill 后恢复提交恰好一次、duplicated=[]，真 broker apache/kafka:4.3.1 KRaft
- [x] **接线验证**：registry 发现测试断言新 beans 文件被扫描、两工厂描述符可查（防孤岛注册）——`TestStreamConnectorRegistryDiscovery` 9/9（6 beans 文件、10 端点、能力矩阵 + 成熟度钉定 + unknown-type registeredTypes 清单）
- [x] owner docs 已更新（connectors.md 为连接器面单一事实源）
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI3 已勾选、提交完成

## Closure Gates

- [x] source offset checkpoint 恢复语义落地（单测往返 + E2E kill-recover 佐证）
- [x] 事务 sink epoch 生命周期落地且窗口外 kill 点恢复路径零重复零丢失、全窗口零丢失（mock 单测 + E2E）
- [x] `./mvnw test -pl nop-stream/nop-stream-connector-kafka,nop-stream/nop-stream-runtime -am` 全绿（全量链 EXIT=0：runtime 1306 tests 0 失败 + E2E 真跑 1/1 + 模块 17/17；首轮链唯一失败为 runtime 吃到 m2 旧版 kafka jar 的成熟度断言——重装模块后 9/9 绿，WI5/WI9 同款运行纪律）
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 两工厂经 beans 文件被 registry 真实发现（非孤岛注册）+ create 路径经 probe 测试消费（audit m1 修复：probe 8→10 端点），(b) source offset 恢复链与 sink epoch 提交链从入口到出口连通（E2E 真链 + fence 日志实证），(c) 无空方法体/静默跳过（validateParallelism 为记录义务实现）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-connector-kafka --severity high` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据（4 项 Major 已全部修复后勾选）

## Deferred But Adjudicated

### consume-transform-produce 同事务链（sendOffsetsToTransaction）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: R1 判据交付的是独立端点连接器；同事务链是 Flink Kafka EOS 的 consume-produce 一体形态，需 source/sink 共享 producer 事务，属管道级组合需求，出现真实诉求另立
- Successor Required: `no`

### 残余重复窗口 [barrier commitTransaction, checkpoint durable)

- Classification: `watch-only residual`
- Why Not Blocking Closure: 公共 API 无事务 resume，窗口不可关闭（一次 broker commit RPC + 持久化时延；同步快照下 ≈ RPC 时长）；已在 sink javadoc 与 owner doc 显式声明（pin-and-record），E2E 的 kill 点选在窗口之外钉定主路径。新证据（客户端暴露 resume 能力）出现时升级
- Successor Required: `no`

### 分区再均衡（消费者组协议）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 手动 assign 独占分区已满足确定性恢复；group 协议引入 rebalance 与 checkpoint 协同复杂度，单作业部署形态下无收益
- Successor Required: `no`

## Closure

Status Note: WI3 交付物真实且语义与 plan 一致（两轮对抗性审查 + 独立 closure audit 后 4 项 Major 全部修复：M1 connectors.md 矩阵行拆回修复、M2 Phase 1 勾选补账、M3 runtime pom test scope、M4 plan 2330 deferred「Kafka 限速」按原 successor 承诺在本 WI 落地清偿）；E2E 对真 broker 非跳过真跑绿（100 条恰好一次、零重复零丢失）；残余重复窗口按 §6.4 pin 为 watch-only 并三方（javadoc/owner doc/plan）同口径声明。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_5c7c4417-df87-45be-8c8c-9442301e54f4），全程只读
- Evidence:
  - 实现 vs plan 语义一致性 PASS（saveState 融合顺序/高水位守卫/restore 重放/分片路由/typed 拒绝面逐条 file:line 核实）
  - 测试真实覆盖 PASS（模块 16→17 用例全绿；E2E 四前置钉定 + 真跑非 SKIP + fence 日志实证；registry 9/9 含 10 端点与成熟度钉定）
  - owner doc 审计发现 M1（矩阵行损坏）已修复并复核（6 管道符×2 行 + doc-links 退出码 0）
  - Anti-Hollow PASS（beans→registry→probe→create 全链消费；E2E 真链；scan-hollow high 退出码 0）
  - closure audit 4 项 Major 处置：M1/M2/M3 修复，M4 以「Kafka 限速落地」清偿（源码 + 描述符参数 + 单测 rateLimitWiringTypedAndCarriedByCopies + owner doc 同步）
  - `./mvnw test -pl nop-stream/nop-stream-connector-kafka,nop-stream/nop-stream-runtime -am` EXIT=0（1306+17+1 tests，0 失败）
  - `check-doc-links --strict` / `check-plan-checklist --strict` / `scan-hollow --severity high` 全部退出码 0
  - 提交圈定（audit m4）：仅 WI3 文件（新模块 + 双 pom + runtime pom/E2E/registry 测试 + connectors.md + plan + roadmap 勾选 + 10-08 日志），sibling survey 工作流文件不混入

Follow-up:

- topic 缺失 typed 拒绝无专测（audit m2；broker 依赖路径，E2E 覆盖可达面）——watch-only，触碰 source 时可顺手补
- drainReachesTerminalState 仅断言 drain 标志（audit m3；真实路径由 gated E2E 覆盖，测试内已诚实注明）
- no remaining plan-owned work
