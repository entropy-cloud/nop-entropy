# 3 复合场景分布式落地（roadmap item 14）

> Plan Status: completed
> Last Reviewed: 2026-09-02
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 14（Phase S，critical path，deps: item 13）；stage details「复合场景分布式落地」
> Related: `2026-09-01-2217-2-composite-scenario-local-implementation.md`（item 13，前置依赖，其场景测试资产是本 plan 的输入）；`2026-09-01-2217-1-composite-scenario-design.md`（item 12，分布式验证矩阵定义来源）；`2026-09-01-0938-3-runtime-module-audit.md`（item 8，R-14 真实 3 进程 gated fencing 测试先例 + `MiniStreamCluster` 基建复核结论）
> Mission: nop-stream-productization
> Work Item: roadmap item 14

## Purpose

用 `MiniStreamCluster` 真实多 JVM DISTRIBUTED 模式验证全部复合场景：按设计文档的分布式验证矩阵执行「场景部署 → checkpoint → kill TaskManager → recover（fencing 断言）→ rescale → exactly-once 结果断言」；修复分布式路径发现的缺陷（配 gated 测试）；交付分布式运行手册初稿（供 items 16/17 深化）。本 plan 与 plan 2（item 13）共同构成 M3 解锁条件（13+14 done）。

## Current Baseline

（2026-09-01 live 核对；**执行前置条件**：plan 2（item 13）已 completed——场景 LOCAL 测试资产存在且绿；不存在则 blocked）

- **分布式验证基建**（live，roadmap Framework/platform reuse 表）：`MiniStreamCluster`（`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/multijvm/MiniStreamCluster.java`，572 行，ProcessBuilder + H2 AUTO_SERVER）+ 独立进程入口 `JobCoordinatorMain`/`TaskManagerMain`（`runtime/test/.../launch/`，含 `SharedJdbcInfrastructure`/`PollingJdbcMessageService`/`ClusterLaunchConfig`）。**能力边界（live 核实，本 plan 的主要工程量所在）**：`JobCoordinatorMain` 硬编码 trivial 管线（`buildTrivialSourceSinkJobGraph`：空 `CollectionReplayableSource` 零记录 + 丢弃型 `PrintSinkFunction`，:171—189，javadoc 自述验证面是 deploy/recovery/fencing 基建而非端到端数据流）；`CheckpointCoordinator.startCheckpointScheduler()` 在 launch 路径无调用方（现有多 JVM 运行从不产生 checkpoint）；跨进程 sink 可观察面（S1 JDBC 表 / S2 文件产物）无传递与断言通道——「场景管线多 JVM 部署 + 真实数据流 + checkpoint 产生 + sink 断言」需本 plan 落地（Phase 1 第 1 项）
- **测试归属事实（live 核实）**：`nop-stream-runtime` pom **未导出 test-jar**（其对 `nop-stream-core` 的 test-jar 依赖是消费方；仅 core 导出 test-jar）——`MiniStreamCluster` 与全部 launch Main 位于 runtime `src/test/java`，其他模块无法 import。可行路径预定（Phase 1 裁定，二选一）：① runtime 挂 maven-jar-plugin test-jar 执行（构建配置变更，本 plan 显式批准）+ gated 测试放场景模块；② gated 测试放 runtime + 显式新增 test 依赖（cep/flow/connector-debezium 等），须核验不构成 reactor 环（场景模块若依赖 runtime 则禁止反向 test 依赖）
- **gated 多 JVM 测试先例**：`TestMultiJvmExactlyOnceRecovery`（288 行）/`TestMultiJvmCoordinatorFailover`（304 行）/`TestMiniStreamClusterProcessSpawn`，门禁 `@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")`；item 8 R-14：真实多 JVM（3 JVM：JC + TM 独立 spawn 进程 + 测试 JVM 扮 zombie）gated fencing 回滚防护测试（7/7 绿，`testZombieCoordinatorEpochRollbackRejectedAtRpcBoundary`）——kill/recover/fencing 断言模式与全套 helper（epoch rotation 轮询、assignment 行计数、coordinator log delta 读取）可复用；**其管线为 trivial 空数据流**，场景级数据流/checkpoint/sink 断言面是本 plan 新建（见上条）
- **fencing 断言语义**：fencing token 统一（跨 JVM 控制面 RPC）；`TestFencingTokenRejection`/`TestJobCoordinatorJdbcHaIntegration` 等单进程内测试存在——多 JVM 场景断言以 R-14 模式为准
- **验证矩阵来源**：`composite-scenario-design.md`（plan 1 交付）定义「场景 × 演练组合」矩阵（kill / kill+fencing / restore-rescale / backpressure 触发）+ D-GAP §3.2 约束 ⑤（MiniStreamCluster 真实多 JVM 基线，禁 K8s）与约束 ②（rescale 仅 restore-time / 离线 reshard 路径）
- **场景资产**：plan 2 交付的 XDSL 场景定义 + LOCAL 测试（S1: CDC → CEP → 窗口聚合 → 2PC JDBC sink；S2: file → keyBy + Delta → exactly-once 文件 sink + rescale）——本 plan 将其提升到 DISTRIBUTED 模式验证
- **exactly-once 断言语义边界**：D-GAP §3.2 约束 ④（最新 durable epoch manifest 恢复 + 结果无重复无丢失；不断言 manifest 级 checksum 行为——item 25 未落地）
- **已知分布式遗留**（Follow-up item 28 记载）：remote-deploy 数据面全对订阅 + 队列满阻塞泄漏、`JdbcCheckpointStorage.loadRetainedEpochManifests` override 缺失（Stage-31 重启恢复在 JDBC 后端降级）——场景演练若触发这些路径，按缺陷处置规则就地修复或核验 Follow-up 归属，**不得静默绕过**
- **运行手册定位**：roadmap stage details——分布式运行手册初稿供 item 16（可观测性与运维）/17（文档产品化）深化；交付位置 `ai-dev/design/nop-stream/` 或 `docs-for-ai/`（执行时按文档分工裁定：设计/契约入前者，使用指南入后者）
- **工具门禁**：hollow scan / invariants / doc-links / check-plan-checklist（同 items 7—11 基线）

## Goals

- 每个场景（S1/S2）至少一条真实多 JVM gated E2E 测试：场景部署（JobCoordinator + 多 TaskManager 独立进程）→ checkpoint 产生 → kill TaskManager → 恢复（含 fencing 断言：旧 attempt 的 mutation 被拒或等价可观察证明）→ exactly-once 结果断言（sink 端无重复无丢失）
- 验证矩阵逐格执行或显式裁定（不可执行格记录原因与归属——如 backpressure 行为演练若属 item 15 范围则显式路由，不留含糊）
- rescale 验证（S2 矩阵）：restore-time parallelism rescale 在多 JVM 下的结果一致性测试（离线 reshard 路径按设计矩阵裁定）
- 分布式路径缺陷：就地修复 + gated focused 测试；结构性大缺陷转 Follow-up（编号顺延）
- 分布式运行手册初稿：部署拓扑、启动顺序、checkpoint/恢复操作、kill/rescale 演练步骤（与场景测试命令对齐）
- gated 套件在 `-Dnop.stream.test.multi-jvm.enabled=true` 下全绿；默认关闭时其余测试不受影响
- roadmap item 14 写回；M3 解锁条件（items 13+14 done）随写回核对声明

## Non-Goals

- 长时 soak / chaos 矩阵 / backpressure 行为稳定性量化（item 15；本 plan 仅覆盖矩阵中场景验收所需的触发组合）
- K8s/YARN 部署（D-GAP 裁定 defer/exclude；验证以 `MiniStreamCluster` 为基线）
- 可观测性指标暴露/运维 API（item 16；运行手册只记录既有可观察面）
- flow item 29（xpl source 取消语义等）与 item 26/27/28 结构治理的主动实施（仅当场景演练触发其路径时核验归属；主动实施属各自 plan）
- standby 热备 / 状态查询 / 运行时自动 reshard（P-REQ-18/19/15 exclude+non-goal 边界，D-GAP §3.2）
- 用户指南/连接器目录（item 17）

## Scope

### In Scope

- 新增（基建能力扩展）：launch/测试基建支持场景管线多 JVM 部署——场景 JobGraph/XDSL 来源（替代硬编码 trivial 管线）、真实数据源接入、checkpoint 调度启动（launch 路径接线 `startCheckpointScheduler` 或等价机制）、跨进程 sink 可观察面（S1 JDBC 表 / S2 文件产物路径）、并行度参数化；含「测试归属」二选一路径实施（Current Baseline 预定路径，构建配置变更显式批准）
- 新增：场景级 gated 多 JVM 测试（归属按 Phase 1 裁定）；每场景 ≥1 条 kill/recover/fencing/exactly-once 主链路测试 + 矩阵要求的 rescale/backpressure 触发组合测试
- 新增：分布式运行手册初稿（位置执行时裁定，见 Current Baseline）
- 修复：分布式路径缺陷（单 plan 范围内）+ gated focused 测试；`_` 前缀生成文件禁改
- 修改：`ai-dev/backlog/nop-stream-productization-roadmap.md`（Follow-up 追加（如有）+ item 14 closure 写回 + M3 声明 + Last updated）
- 修改（条件性）：受影响 owner-doc 最小同步（`failover-design.md`/`checkpoint-design.md` 等，仅当缺陷修复改变契约语义；否则显式 `No owner-doc update required`）
- 只读输入：`composite-scenario-design.md` 矩阵、plan 2 场景资产、item 8 报告 R-14 模式、`MiniStreamCluster`/launch 基建

### Out Of Scope

- 与场景验证无关的 `MiniStreamCluster`/launch 基建重构（**场景部署所需的能力扩展属 In Scope**；仅排除无关重构——item 28 的结构性治理归其自身 Follow-up）
- 单进程（LOCAL）行为变更（plan 2 已收口；本 plan 发现的 LOCAL 缺陷仅路由）
- `ai-dev/analysis/` 演练报告（属 item 15 的 soak/chaos 交付；本 plan 结果记录于 plan closure + 日志 + 运行手册）

## Execution Plan

### Phase 1 - 场景级多 JVM 主链路测试（kill → recover → fencing → exactly-once）

Status: completed
Targets: `nop-stream-runtime` launch/multijvm 测试基建（能力扩展）、场景 gated 测试（归属 Phase 1 裁定）、场景 XDSL 资产（plan 2 交付）

- Item Types: `Fix | Decision | Proof`

- [x] 执行前置核对：plan 2 completed（场景 LOCAL 资产存在且绿）；否则置 blocked 并记录（2026-09-02 live 核对：plan 2 `Plan Status: completed`，fraud-example 场景资产 3 XDSL + 8 测试类齐全，前置满足）
- [x] 基建能力扩展：launch/测试基建支持场景管线多 JVM 部署（场景 JobGraph 来源 / 真实数据源 / checkpoint 调度启动 / sink 可观察面 / 并行度参数化——Current Baseline「能力边界」条逐项落地）+ 测试归属裁定实施（**裁定 = 路径①**：runtime 挂 maven-jar-plugin test-jar 导出 launch/multijvm 基建（沿 core test-jar 先例），gated 场景测试放 fraud-example（test 依赖 runtime test-jar）——fraud-example 已依赖 runtime main，路径②（runtime test 反向依赖场景模块）构成禁戒环，拒绝；理由与实施见设计文档 A.0-D 裁定 1）。基建落地：`ClusterPipelineFactory` seam（`pipelineFactoryClass` launch 参数，工厂构建失败 fail-fast 不回落 trivial 管线）+ `RemotePipelineSpec`/`RemotePipelineResolver`（XDSL 管线以声明 spec 跨 JVM 运输、各 TM ServiceLoader 重建同构图，fingerprint 一致由 `TestDistributedScenarioSerialization` 钉定）+ `RemoteTaskDeploySupport`（TM 侧 tracker/后端/restore 三空白补全）+ launch 周期 checkpoint（`startPeriodicCheckpoints`）+ 分布式提交通知/abort 通道 + JC 重启恢复 + `MiniStreamCluster.withCoordinatorArg`/jobId/checkpointDir 参数化 + `BlockingSourcePipelineFactory`（kill 确定性）
- [x] S1 多 JVM 主链路 gated 测试：`TestS1MultiJvmE2E`（C0 基线 + C1 kill/recover：JC + 2 TM 独立进程部署 → CDC 事件流入 → 周期 checkpoint（durable manifest 断言）→ SIGTERM kill tm-1 → restart 恢复 → fencing 断言（epoch 严格递增 + 陈旧 epoch mutation 在 tm-0 RPC 边界被拒，日志可观察）→ JDBC 2PC sink 终表 == 精确期望集 + 4 ledger 非空 + ledger epoch ≥ kill 前 durable epoch）；启用态 2/2 绿
- [x] S2 多 JVM 主链路 gated 测试：`TestS2MultiJvmE2E`（C0 base+Delta（Delta 定制拓扑在 DISTRIBUTED 模式生效断言 = delta 输出恰为 base − 黑名单行集）+ C1 变体（kill 后不 restart，租约到期触发 failure-detector 恢复——补充走另一生产恢复触发路径）→ 文件 sink exactly-once（多重集精确 + manifest keys == epoch 文件集 + 无 .tmp））；启用态 2/2 绿
- [x] fencing 可观察断言设计落地（沿 R-14 模式）：`MultiJvmTestSupport.probeStaleEpochRejection`——测试 JVM 作为真实第三方控制面调用者向存活 TM 发 stale-epoch `triggerCheckpoint`，断言其日志记录 `ERR_STREAM_FENCING_TOKEN_MISMATCH` 拒绝（行为级拒绝证据，非进程存活）；S1/S2 C1 均断言
- [x] kill 时机受 durable manifest 约束：`waitForDurableManifest`（kill 前断言最新 durable epoch manifest 已落盘，`LocalFileCheckpointStorage.loadLatestEpochManifest` 可观察）+ S1 附加「kill 前至少 1 行 alert 已提交」/S2「kill 前部分输出已提交」前置——恢复断言不退化为从零重跑
- [x] 已知遗留路径核验：① remote-deploy 全对订阅 + 队列满阻塞泄漏（item 28）——全部场景 gated 测试真实流量流经 remote-deploy 数据面通道，**未触发 hang**（13 用例全部在超时内完成；有界 fixture 低速率远低于 1024 槽水位），归属不变（item 28 收敛，无静默绕过；运行手册 §5 已记载「hang 优先核验 item 28」提示）；② `JdbcCheckpointStorage.loadRetainedEpochManifests` override 缺失（Stage-31 JDBC 后端恢复降级）——场景 checkpoint 存储均为 `LocalFileCheckpointStorage`，该路径**未触发**（显式记录，非绕过；运行手册 §5 披露）

Exit Criteria:

- [x] 基建能力扩展落地且被场景 gated 测试真实消费（trivial 空管线不再是唯一部署路径——`pipelineFactoryClass` 缺省保持 trivial 基线（既有 Stage 42 能力测试不变），场景运行全部经工厂部署真实管线；checkpoint 在多 JVM 运行中实际产生——durable manifest / ledger / TM「restored from durable epoch N」日志均可观察）（**接线验证**，Rule #23）
- [x] 每场景 ≥1 条 gated 测试文件存在且在 `-Dnop.stream.test.multi-jvm.enabled=true` 下通过（S1 `TestS1MultiJvmE2E` 2/2、S2 `TestS2MultiJvmE2E` 2/2；另 C2/C3 见 Phase 2）（**端到端验证**：多 JVM 部署到 sink 结果断言的完整路径，Rule #22）
- [x] fencing 断言为行为级（可观察拒绝/拒绝日志/结果一致性证明），非进程存活级（probeStaleEpochRejection 日志断言 + epoch 严格递增 + 恢复后结果一致性）
- [x] kill 前 durable manifest 存在性断言落地（恢复断言的确定性前提）
- [x] exactly-once 断言落在 sink 端最终产物（JDBC 表内容精确集 + ledger / 文件 epoch 产物 + manifest + 无 .tmp）
- [x] **无静默跳过**（Rule #24）：基建扩展与测试无空方法体/吞异常/占位返回（hollow scan fraud-example + runtime 均 0 findings exit 0）；遗留路径触发时显式记录（见上条）
- [x] 默认（门禁关闭）构建不受影响：`./mvnw test -pl nop-stream -am -T 1C` 全绿（gated 用例 skip；含修复 `getRunningTaskCount()` 语义后 6 个 distributed executor 测试回归绿）
- [x] 新增测试断言正确结果而非仅无异常（Rule #25 映射：C0 断言精确期望集；C1 断言恢复后全量期望集 + fencing 行为；基建扩展新公共方法未实现路径显式抛异常——`ClusterPipelineFactory` 契约 fail-fast、`JobCoordinatorMain` 工厂构建失败抛出、`MiniStreamCluster` override 空参抛 IAE）
- [x] owner-doc 裁定：launch/运行契约变更（`pipelineFactoryClass` 参数面、barrier 扇出面、timeout abort 语义、TM checkpoint 接线、C2/C3 形态）→ `composite-scenario-design.md` **A.0-D 分布式落地裁定（8 条）**回写 + 新增 `distributed-runbook.md`（运行手册初稿）+ 设计 README 索引更新 + fraud-example README 分布式测试命令/Scenario Notes 同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 矩阵收口（rescale + backpressure 触发组合）+ 缺陷处置

Status: completed
Targets: multijvm 测试、（条件性）runtime/场景模块缺陷修复点

- Item Types: `Fix | Decision | Proof`

- [x] S2 rescale 多 JVM 测试：`TestS2RestoreRescaleMultiJvmE2E`——run 1（2 TM）至 durable checkpoint + 部分提交输出后整集群优雅停止 → run 2 以相同 jobId + checkpoint 目录、3 TM、严格更大 fencingEpoch 重启 → cursor/keyed 状态/manifest 跨 JVM 恢复 → 终态 == 全量期望集（无重复无丢失；TM 日志「restored from durable epoch N」为直接证据）；**keyed-parallelism（P>1 + 2PC sink）形态显式路由**（引擎硬门禁 `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`，CONN-01 successor + per-transform parallelism 消费（item 29 家族）；keyed 再路由语义已由 executor 级 `TestKeyGroupRescaleDispatchE2E` + 离线 reshard 恢复（A2-5）覆盖——Deferred 区裁定 1）；离线 reshard 工具路径按设计矩阵 D5 裁定不入矩阵（工具操作 savepoint 产物，与 JVM 拓扑正交，单进程验证已覆盖其语义——设计文档 §3.3 矩阵约束合规行维持）
- [x] 矩阵 backpressure 触发组合：`TestScenarioBackpressureMultiJvmE2E`（S1 + S2 两格）——`ThrottledScenarioSinks`（生产 2PC 类子类，sink bean 内有界节流，release marker 文件跨 JVM 控制）：节流期间 durable epoch 严格推进（无死锁，`waitForDurableEpochAdvanceUnderThrottle`）+ 释放后终态 == 精确期望集（S1 A1-5 表 + ledger / S2 A2-7 目录规范）；稳定性量化显式路由 item 15（Deferred 区裁定 3）。**矩阵逐格结论**：C0 S1=已执行（`s1MultiJvmBaseline...`）/C0 S2=已执行（`s2MultiJvmBaseline...` base+Delta）；C1 S1=已执行（`s1MultiJvmKillRecover...`，FAILED-report 恢复路径）/C1 S2=已执行（`s2MultiJvmKillRecover...`，租约到期恢复路径）；C2 S1=恢复断言经 C1 成立（附加格：keyed 富化/CEP/窗口状态恢复由 C1 覆盖；P>1 再路由形态同裁定 1 路由）/C2 S2=已执行（`s2RestoreRescaleAcrossTmTopology2To3...` TM 2→3 形态）+ P>1 形态路由；C3 S1=已执行（`s1ThrottledJdbcSinks...`）/C3 S2=已执行（`s2ThrottledSink...`）；离线 reshard=显式路由（D5）。无未裁定格
- [x] 分布式路径缺陷就地修复 + gated focused 测试（7 项，bug note `ai-dev/bugs/2026-09/2026-09-02-distributed-scenario-engine-defects.md`）：remote-deploy checkpoint 接线三空白（tracker/后端/restore-on-deploy）、barrier 扇出面、timeout abort 级联、新鲜启动 initializeState 缺失（CDC 空 offset 快照→恢复全量重放）、完成态任务注销丢尾部提交、陈旧 attempt 注册表竞态（`testStaleAttemptExitDoesNotRemoveReplacementRegistryEntry` 确定性复现）、完成态保留后 running 计数语义漂移（6 个 embedded/rpc distributed 默认套件回归转绿）；无结构性大缺陷需转 Follow-up（item 28 两项归属不变，未触发未绕过）
- [x] 运行手册初稿：`ai-dev/design/nop-stream/distributed-runbook.md`——部署拓扑 / 启动顺序（含 pipelineFactoryClass 恢复语义）/ checkpoint 与恢复操作（单集群恢复 + 跨集群重启）/ 演练步骤表（C0—C3 与 gated 测试命令一一对应 + 产物保留开关）/ 已知边界（item 28 两项、2PC P>1 门禁、manifest 无 checksum、背压无直接指标、fencingEpoch 缺省值警示）；位置裁定：现有 stream 文档全部在 `ai-dev/design/nop-stream/`（docs-for-ai 无 stream 区，迁移归 item 17）

Exit Criteria:

- [x] 设计矩阵逐格结论落库（已执行测试名 / 显式路由 + 理由），无未裁定格（结论矩阵见 Phase 2 第 2 项；路由记录 = 本文件 Deferred 区 + 设计文档 A.0-D 裁定 7/8）
- [x] rescale 测试通过（恢复后结果一致 + 无重复丢失）；恢复走 restore-time/离线路径（D-GAP §3.2 约束 ② 合规——TM 拓扑变更恢复为 restore-time 形态；无运行时自动重分片）
- [x] 全部就地修复配 gated focused 测试（或显式豁免附理由）；无静默绕过已知遗留（**无静默跳过**，Rule #24：item 28 两项路径核验记录在 Phase 1 第 7 项与运行手册 §5）
- [x] 运行手册初稿存在且步骤与测试命令一致（repo-observable 路径 `ai-dev/design/nop-stream/distributed-runbook.md` §4 表格逐条给出 mvn 命令）；受影响 owner-doc 同步（composite-scenario-design.md A.0-D + distributed-runbook.md + 两处 README + bug note；failover/checkpoint-design 无契约语义变更——checkpoint timeout abort 行为语义经 bug note + A.0-D 记录，属缺陷修复收敛回设计意图而非契约变更）
- [x] gated 套件启用态全绿（执行记录：2026-09-02 `TestS1MultiJvmE2E` 2/2 + `TestS2MultiJvmE2E` 2/2 + `TestS2RestoreRescaleMultiJvmE2E` 1/1 + `TestScenarioBackpressureMultiJvmE2E` 2/2 + `TestDistributedScenarioSerialization` 6/6 = **13/13 绿**；runtime legacy gated `TestMiniStreamClusterProcessSpawn` 3 + `TestMultiJvmExactlyOnceRecovery` 1 + `TestMultiJvmCoordinatorFailover` 3 = 7/7 绿）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 默认态全绿
- [x] hollow scan / invariants / doc-links 三工具退出码 0（fraud-example + runtime hollow scan 均 0 findings；`gate-inventory.json` 随 `abortAllPendingCheckpoints` 新增同步）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 每场景 kill → recover（fencing 行为级断言）→ exactly-once 主链路 gated 测试存在且启用态绿（S1/S2 C1；陈旧 epoch mutation RPC 边界拒绝为日志可观察行为级证据）
- [x] launch 基建支持场景管线部署（真实数据流 + checkpoint 产生 + sink 可观察面），且被场景 gated 测试真实消费（非仅 trivial 空管线旁路——trivial 仅为缺省基线，场景运行全走 `pipelineFactoryClass`）
- [x] 设计矩阵 100% 逐格结论（执行或显式路由），D-GAP §3.2 六条约束合规（多 JVM 基线（全部 ProcessBuilder 真实 spawn）/ rescale 路径（restore-time + 离线 reshard 不入矩阵，无运行时自动重分片）/ 断言语义边界（最新 durable epoch manifest 恢复 + 结果无重复无丢失，未断言 manifest 校验））
- [x] 分布式缺陷已修复或 Follow-up 化（含 item 28 遗留的触发核验记录），无静默降级（7 项就地修复配测试（bug note）；item 28 两项未触发且显式记录于 plan + 运行手册 §5）
- [x] 运行手册初稿落库且与测试命令一致（`ai-dev/design/nop-stream/distributed-runbook.md`）
- [x] 受影响 owner docs 已同步或显式 `No owner-doc update required`（composite-scenario-design.md A.0-D 8 条 + distributed-runbook.md 新增 + 设计 README + fraud-example README + failover/checkpoint-design 无契约语义变更（timeout abort 收敛回设计意图，bug note 记录））
- [x] `./mvnw compile` / `./mvnw test -pl nop-stream -am -T 1C`（默认态）全绿；gated 套件启用态执行记录在案（13/13 场景 + 7/7 legacy，见 Phase 2 Exit Criteria）
- [x] checkstyle 随构建通过（沿现状基线）
- [x] hollow scan / invariants / doc-links 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] **Anti-Hollow Check**：closure audit 验证多 JVM 调用链真实连通（进程 spawn → 部署 → 数据面 → checkpoint → 恢复 → sink），gated 断言为行为级（audit Evidence 节：逐环节 live 锚点追踪）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session `ses_f9d9ba880ffeUBCdg4ulKxjRej`，13 项 PASS + 1 Blocker 已修复复验）
- [x] roadmap item 14 写回 + ★M3 解锁核对声明（items 13+14 均 done 后 M3 标 done——roadmap :57/:59 + Last updated）

## Deferred But Adjudicated

- **C2 keyed-parallelism rescale 形态（P>1 + 2PC sink 组合，S1/S2 通用）**
  - Classification: `out-of-scope improvement`（路由 successor）
  - Why Not Blocking Closure: 引擎硬门禁不可降级——`StreamGraphGenerator` 对 2PC sink 有效并行度 >1 build 期 fail-fast（`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`，StreamGraphGenerator.java:367，CONN-01 P1 有意 defer，checkpoint-design §6.4.1），且引擎仅支持流级统一并行度（`Transformation.parallelism` final，per-transform 消费未实施）+ source UDF 跨 JVM 无 subtask 分片——该形态在当前引擎不可表达（与 plan 2 A2-4 LOCAL 路由同一约束，非本 plan 引入）；keyed 状态跨 key-group 再路由语义已由 executor 级 `TestKeyGroupRescaleDispatchE2E`（P_old→P_new per-subtask slice 断言）+ 离线 reshard 128→256 恢复（plan 2 A2-5）+ 本 plan TM 拓扑变更分布式恢复演练覆盖
  - Successor Required: yes
  - Successor Path: CONN-01 successor（并行 2PC sink，checkpoint-design §6.4.1 defer 记录）+ per-transform parallelism 消费（roadmap item 29 家族）
- **backpressure 行为稳定性量化（长时 soak / chaos 矩阵 / 指标采集）**
  - Classification: `optimization candidate`（路由 item 15）
  - Why Not Blocking Closure: 设计矩阵 C3 的场景验收口径 = 触发组合（节流期间 checkpoint 推进 + 解除后结果完整），本 plan 已以 gated 测试覆盖（两格均执行）；无直接背压指标（item 16 前提）与长时稳定性量化超出场景验收范围（设计文档 §3.3 矩阵约束合规行预先裁定「背压行为的稳定性演练属后续稳定性演练工作项」）
  - Successor Required: yes
  - Successor Path: roadmap item 15（分布式稳定性与性能演练）
- **离线 reshard 工具多 JVM 形态**
  - Classification: `out-of-scope improvement`（按设计裁定不入矩阵）
  - Why Not Blocking Closure: 设计 D5 显式裁定——工具操作对象是 savepoint 产物，与 JVM 拓扑正交，入矩阵只增加运行成本不增加语义覆盖；其语义（key 守恒 + 新 maxParallelism 恢复执行）已由 plan 2 A2-5 单进程 E2E 完整验证
  - Successor Required: no

## Non-Blocking Follow-ups

- （无新增 roadmap Follow-up：7 项分布式缺陷全部就地修复配回归（bug note `ai-dev/bugs/2026-09/2026-09-02-distributed-scenario-engine-defects.md`）；item 28 两项遗留归属不变（未触发、显式记录）；Deferred 区 3 条均为路由裁定非 live defect）

## Closure

Status Note: S1/S2 复合场景在真实多 JVM（MiniStreamCluster：JC + 2/3 TM spawn 进程，H2 AUTO_SERVER 共享库 + 共享 checkpoint 存储）完成分布式验证矩阵全部格子（C0 基线 / C1 kill-recover-fencing（FAILED-report 与租约到期两种恢复触发路径）/ C2 restore-rescale（TM 2→3 跨集群恢复演练 + keyed-P>1 形态显式路由）/ C3 backpressure 触发（两场景 sink 内节流））；launch 基建扩展（pipelineFactoryClass seam + RemotePipelineSpec 声明运输 + TM 侧 checkpoint 接线 + 分布式提交/abort 通道 + JC 重启恢复）被场景 gated 测试真实消费；分布式路径 7 项缺陷就地修复配回归（bug note）；分布式运行手册初稿落库（演练步骤与 gated 命令一一对应）；gated 13/13 + legacy 7/7 启用态绿、默认态全模块绿、四工具门禁 exit 0；Deferred 区 3 条均附 non-blocking 理由。M3 解锁（items 13+14 done）。无剩余 plan-owned work。

Completed: 2026-09-02

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，task `ses_f9d9ba880ffeUBCdg4ulKxjRej`）
- Evidence:
  - 审计结论 **CLOSURE-APPROVED**（1 项 Blocker——bug note Affected Files 6 处路径缺 `nop-stream/` 前缀致 doc-links exit 1——已就地修复并复跑 doc-links exit 0；其余 13 项核验全 PASS 后无遗留 Blocker）。
  - Phase 1 exit criteria 全 PASS：基建 4 文件 live 非空壳（ClusterPipelineFactory:49/RemotePipelineSpec:45/RemotePipelineResolver:33/RemoteTaskDeploySupport:93-155）且被 gated 测试真实消费（TestS1MultiJvmE2E:124 pipelineFactoryClass 接线）；test-jar 双向 pom 核对（runtime pom:164-168 / fraud-example pom:74-81）；gated 复跑 S1 2/2 + S2 2/2 BUILD SUCCESS（kill 前 durable manifest 前置 :171、fencing 日志级拒绝断言 :199-204、sink 端精确集断言）。
  - Phase 2 exit criteria 全 PASS：C2 复跑 1/1（withJobId:187/withCheckpointDir:174 跨集群同身份恢复；3 TM 注册 + fencing 严格递增 + 精确集收敛 + manifest==epoch 文件）；C3 复跑 2/2（ThrottledScenarioSinks 继承生产 2PC 类、节流期间 durable epoch 严格推进断言）；运行手册 §4 表格与 4 个 gated 类/方法名逐一对应；设计文档 A.0-D 恰 8 条裁定；bug note 7 项修复抽查 2 项 live 锚点核对（TaskManager:783/847 + TestTaskManager:693；CheckpointCoordinator:281/997 + gate-inventory:170）；P>1 路由的引擎门禁 live 核对（StreamGraphGenerator:367）。
  - 默认套件 BUILD SUCCESS（runtime 870 + fraud-example 70，gated 按设计 skip）；hollow（两模块）0 findings exit 0；invariants exit 0；doc-links 修复后 exit 0。
  - Anti-Hollow PASS：多 JVM 调用链逐环节 live 追踪连通（JobCoordinatorMain.start → factory → CheckpointCoordinator+fingerprint+restore → descriptor.setPipelineSpec（JobCoordinator:712）→ TaskManager.deployTask → SubtaskPlanBuilder:539（ServiceLoader resolver:158，META-INF/services 存在）→ RemoteTaskDeploySupport:543 → tracker → sendCheckpointAck（TaskManager:706）→ collectAck（JobCoordinator:887）→ durable → commit forwarder（:1153）→ notifyCheckpointComplete（TaskManager:684→finishCommit:1084））；`TestDistributedScenarioSerialization#s1FullTopologyRunsViaRemoteDeployInProcess` 进程内全链证明 6/6 绿。
  - 诚实性核对 PASS：item 28 两项遗留（LocalFileCheckpointStorage live 核对 JobCoordinatorMain:134，fraud-example 测试零 JdbcCheckpointStorage 引用）；Deferred 3 条均带 Classification + Why Not Blocking；daily log 2026/09-02 有 plan 2217-3 条目；roadmap item 14 done + M3 done + Last updated 同步。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Closure 填写后复核，见下）。

Follow-up:

- no remaining plan-owned work
- C2 keyed-P>1 形态 → CONN-01 successor + item 29 家族（Deferred 裁定 1）
- backpressure 稳定性量化 → item 15（Deferred 裁定 2）
