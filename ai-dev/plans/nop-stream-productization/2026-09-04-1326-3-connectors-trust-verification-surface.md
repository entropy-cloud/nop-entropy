# 连接器恢复契约、传输命名与信任边界收口 + 验证面诚实化（AR-12..AR-15 + F-07..F-11）

> Plan Status: active
> Mission: nop-stream-productization
> Last Reviewed: 2026-09-04
> Source: `ai-dev/audits/nop-stream-productization/2026-09-03-1951-open-audit-nop-stream-productization.md`（AR-12、AR-13、AR-14、AR-15）+ `ai-dev/audits/nop-stream-productization/2026-09-03-1951-multi-audit-nop-stream-productization.md`（F-07、F-08、F-09、F-10、F-11）
> Related: `2026-09-04-1326-1-checkpoint-identity-recovery-hardening.md`（{1}）、`2026-09-04-1326-2-dataplane-window-barrier-lifecycle.md`（{2}）
> Execution Order: {3}（最后执行；不阻塞 {1}/{2}，其修复面相互独立）
> Review: 两轮独立对抗性审查（fresh sessions `ses_f9518edd1ffekowu4A3hMmCnWk` / `ses_f9509777dffevJ5wE1Ws2vMdt5`）——首轮 1 Blocker（broker 不可得的端到端 Proof）+ 4 Major + 6 Minor 全修复；第二轮 11/11 RESOLVED、in-repo 替代验收经想象性分析可执行且非空洞，新发现 4 Minor（控制面 topic 登记、合法输入恒等断言、防空集合断言、loader 确定性前提）已修复，判定可 active。共识达成。

## Purpose

把连接器恢复契约（batch 源 offset、文件 2PC 台账、文件游标）收口为文档承诺的真实行为；Kafka/Pulsar 数据面 topic 命名合法化；reset 工具路径穿越、ops REST 类加载/认证、原生反序列化三个信任边界加固；多 JVM gated 测试与 runbook 命令两个「文档声称 vs 实际可达」缺口诚实化。

## Current Baseline

（anchor 格式 `文件:行号`，核对日期 2026-09-04；全部来自两份 open 审计并经本轮 live 路径存在性复核——`LocalFileSegmentStore`/`JavaStreamSerializer` 的实际路径与审计引用略有偏差已按 live 修正，见 {1}）

**连接器恢复契约（AR-13/AR-14/AR-15）：**

- AR-13：`BatchLoaderSourceFunction.java:63-92` 的 `run()` 无条件从 provider 首记录发射（`loader.load(batchSize, chunkContext)` 无 skip/reposition）；`:124-132` 的 `seek(offset)` 只动计数器。`StreamSourceOperator.restoreState`（`:367`）对 Replayable 源 seek 后重跑 run → 一次恢复 = 全量重复发射 + 计数器谎报（约 2N），二次恢复再放大；负 offset 不校验。
- AR-14：`FileTwoPhaseCommitSink.java:400-419` 的 `updateManifestAtomically` 用固定名 `manifest.properties.tmp`（无 subtask 后缀，对照 `subtaskSuffix:434-436` 只后缀化数据文件与 manifest 键）→ sink parallelism>1（`fraud-parallel-2pc-file.stream.xml` 声明 2）时两 subtask 并发竞写同一 tmp：丢条目/`NoSuchFileException` 伪失败——item 35 刚交付的并行 2PC 形态上的幂等台账完整性缺陷。
- AR-15：`FileSourceReader.java:163-168` 恢复游标 skip 循环 `skipped <= 0` 即 break——部分/失败 skip 静默从错误位置读（正确范式参照 `DirectoryFileSourceFunction.emitRemaining`（`nop-stream-fraud-example` 模块，:160-162 `skipped != cursor` 即抛 IOException））；`:117-141` 游标推进写在 `snapshotState`（`:248-259`）所用 monitor 之外（JMM 竞态）；`readNextLine` 不以 `split.getEndOffset()` 封顶（文件增大越界发射）。

**传输命名（AR-12）：**

- `RemoteGraphExecutionPlanBuilder.java:154,383` edgeId 含 `->`（`>` 在 Kafka topic 名非法，合法集 `[a-zA-Z0-9._-]`、≤249 字符）；`StreamTopicNaming.java:31-35` topic = 前缀 + 裸 jobId + edgeId + subtask 序号；`KafkaMessageService` 直通 `ProducerRecord`/`consumer.subscribe` → 任何真实 Kafka/Pulsar 部署首条消息即 `InvalidTopicException`；gated e2e `TestDataPlaneKafkaBackendE2E` 自用 `EDGE_ID="src->tgt"` 不连真 broker，掩盖该缺陷。

**信任边界（F-08/F-09/F-10）：**

- F-08：`StreamStateResetTool.java:85-118` 的 `Path.of(checkpointBaseDir, jobId)` 仅 blank 检查后递归删除——`jobId=../other-job` 相对遍历静默删除兄弟作业全部状态；对照姊妹类 `LocalFileCheckpointStorage.java:56,325-340` 的 `SAFE_ID_PATTERN` + canonical `startsWith` 双防护 + 专测（`TestLocalFileCheckpointStoragePathTraversal`），本工具双缺且 owner doc（`nop-stream.md:125-132`）将其记录为文档化编程 API。
- F-09：`OpsJobManager.java:191-197` 单参 `Class.forName(factoryClass)`（initialize=true）先于接口检查执行任意类静态初始化器；ops server 全端点零认证原语；`StreamOpsConfig.java:26-28` 默认 127.0.0.1 但官方模板（`metrics.properties.template:14-18`）引导改 0.0.0.0 跨机采集；同库持久化数据反射加载点均用 ClassNameValidator 而 OpsJobManager 不用（纪律不对称）。
- F-10：`JavaStreamSerializer.java:94-105`（`nop-stream-core/.../common/typeutils/`）裸 `ObjectInputStream.readObject()` 无 ObjectInputFilter（全仓 `rg ObjectInputFilter` 零命中）；主恢复路径 `.checkpoint` body（`restoreFromCheckpoint:1043-1093` → `deserializeCheckpoint`）完全无 checksum——跨信任边界（checkpoint 存储写权限集 > operator 集）的反序列化 gadget 面，绕过平台自身 ClassNameValidator 白名单纪律的唯一通道。

**验证面诚实性（F-07/F-11）：**

- F-07：10 个多 JVM gated 测试（fraud-example 6 类 + runtime/multijvm 4 类，均 `@EnabledIfSystemProperty(named="nop.stream.test.multi-jvm.enabled")`）在 3 个 CI workflow（`.github/workflows/maven.yml:43` 等）中永久跳过；`docs-for-ai/04-reference/source-anchors.md:268`、`nop-stream-user-guide.md:149`、`nop-stream-connectors.md:81` 引用其为能力「已证明」锚点且零启用文档（`rg "nop.stream.test" docs-for-ai/` 0 命中）。
- F-11：`JobCoordinatorMain.java`/`TaskManagerMain.java` 仅存在于 test scope（`nop-stream-runtime/src/test/java/.../launch/`）；`docs-for-ai/03-modules/nop-stream.md:90-91,112,231,254` 运维手册给命令且自称权威速查；唯一披露处 `nop-stream-user-guide.md:174`——按 runbook 用发布 runtime jar 拉起 JC/TM 直接 `ClassNotFoundException`。

## Goals

- 三个连接器的恢复契约与文档声明一致：batch 源恢复从正确 offset 续跑（重复量受控、offset 不谎报）；文件 2PC sink 并行 subtask 的 manifest 更新无竞写；文件源恢复游标精确（skip 不足即 fail、checkpoint 线程读到的游标一致、endOffset 封顶）。
- 数据面 topic 名对 Kafka/Pulsar 恒合法（字符集 + 长度 + 消歧），真 broker 部署不再首条消息即失败。
- reset 工具 typo 不可静默删除他人状态；ops REST 类加载先检查后初始化且跨机暴露有最小认证；原生反序列化有 JEP 290 白名单 filter。
- 文档中的「已证明」锚点要么在 CI 中可复现（nightly lane），要么如实降级为「手动验证 + 启用命令」；runbook 命令在发布产物上可用或明确标注 test-jar 限制。

## Non-Goals

- 不重写 Kafka/Pulsar transport 本身（item 28/31 的数据面停摆缺陷是另一工作面，本 plan 只修命名合法性这一确定性硬失败）。**控制面 topic 不在本轮范围**：`RpcDistributedExecutor:212`/`EmbeddedDistributedExecutor:143`（`"nop-stream.control." + jobId`）与 `StreamControlRpcTopics:28-29`（`nop-stream.rpc.task.` + nodeId）的裸拼不经 `StreamTopicNaming.buildTopic`——jobId/nodeId 非法时控制面 topic 在真 broker 部署同样会挂，登记为 Non-Blocking Follow-up（消毒机制落地后复用）。
- 不做 ops REST 完整认证体系（用户/角色/审计）——只做 bind≠loopback 时的最小 token 门禁 + 类加载加固。
- 不做 manifest 级 checksum 扩展（manifest checksum 已由 roadmap item 25 落地——`CheckpointSerDe` canonical checksum 机制（`computeManifestChecksumHex`/`MANIFEST_FIELD_ORDER`/两个 typed 错误码）已存在；本 plan 的 F-10b 复用同一 canonical 机制扩展到 `.checkpoint` body，不另造轮子）。
- 不为全部 10 个 gated 测试建 CI 基建——裁定 nightly lane 或文档降级，二选一（F-07 的 Decision）。
- 不把 `JobCoordinatorMain`/`TaskManagerMain` 落到 main scope（那是「main-scope 启动入口」follow-up 的范围；本 plan 只做文档诚实化，除非裁定升级）。

## Scope

### In Scope

- `nop-stream-connector-batch`：`BatchLoaderSourceFunction` seek 语义。
- `nop-stream-connector`：`FileTwoPhaseCommitSink` manifest 原子更新；`FileSourceReader` 游标/封顶/锁。
- `nop-stream-runtime`：`RemoteGraphExecutionPlanBuilder`/`StreamTopicNaming` 命名消毒；`StreamStateResetTool` 路径校验；`OpsJobManager`/`StreamOpsHttpServer`/`StreamOpsConfig` 类加载与最小认证；`maintain/StreamMaintenanceMain`（如需透传校验）。
- `nop-stream-core`：`JavaStreamSerializer` JEP 290 filter；`CheckpointSerDe` `.checkpoint` body checksum（最小面）。
- CI/workflows：`.github/workflows/`（若 D2 裁定 nightly lane）。
- docs：`docs-for-ai/03-modules/nop-stream.md`（runbook test-jar 限制、reset 拒绝语义）、`nop-stream-connectors.md`（能力矩阵恢复语义、验证锚点降级或保留）、`nop-stream-user-guide.md`、`docs-for-ai/04-reference/source-anchors.md`、distributed-runbook（如涉及）。

### Out Of Scope

- CEP/窗口/barrier（{2}）；checkpoint 身份/RocksDB/CEP 键类（{1}）。
- P2 项：F-12（JDBC provided 作用域）、F-13（错误分类消息子串）、F-17（吞 IOException）等（backlog 登记）。
- 数据面停摆（item 28/31 已有归属）、OLAP SPI（item 19）。

## Execution Plan

### Phase 1 - 连接器恢复契约（AR-13、AR-14、AR-15）

Status: planned
Targets: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java`、`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java`、`FileSourceReader.java`

- Item Types: `Fix | Decision | Proof`

- [ ] **D1 batch 源语义裁定（Decision）**：AR-13 修复方向——(a) `run()` 按 seek offset 客户端跳过已发记录（**限定 client-side 实现：`BatchLoaderSourceFunction` 在 run 循环内跳过前 offset 条记录，不动 `IBatchLoaderProvider`/`IBatchLoader` 接口——该接口在 `nop-batch/nop-batch-core` 平台模块，属跨模块公共 API（Protected Area，plan-first），本轮不扩展**；接口级定位语义如确需，另立 plan）；(b) 老实降级：`BatchLoaderSourceFunction` 不再实现 `ReplayableSourceFunction`（移除接口宣称，恢复语义对齐普通 SourceFunction）。依据：客户端跳过的正确性（count/offset 对应关系，统一 next-index vs last-emitted-index 约定——对齐 `CollectionReplayableSource`；**前提：`IBatchLoader` 恢复后重新遍历的前 N 条与崩溃前一致（loader 遍历顺序确定性，如带 ORDER BY 的查询）——非确定性 loader 下客户端跳过会错位，该前提须写进裁定文档与矩阵行声明**）、connectors 能力矩阵 live 现状（batch 行现声明 `AT_LEAST_ONCE（声明）` 且已如实记载「seek 只置计数器、run 不跳过」——缺陷是行为层谎报 + 接口宣称 `implements ReplayableSourceFunction`，非矩阵声明虚假）。裁定落 connectors 能力矩阵 + owner doc
- [ ] **Fix（AR-13 按 D1）**：消除「计数器谎报 + 全量重放」——按裁定实现 offset 跳过或移除 Replayable 宣称；`offset < 0` 校验（typed 拒绝）
- [ ] **Fix（AR-14 manifest 竞写）**：`updateManifestAtomically` 的临时文件名加 subtask 维度（对齐 `subtaskSuffix` 既有模式）或 outputDir 文件锁串行化——并行 subtask commit 交错无丢条目/伪失败
- [ ] **Fix（AR-15 游标三缺口）**：① skip 累计不等 cursor 即抛 IOException（镜像 `DirectoryFileSourceFunction.emitRemaining` 范式）；② `activeSplit` 游标推进移入 `snapshotState` 同一 monitor；③ `readNextLine` 以 `split.getEndOffset()` 封顶（文件增大不越界）
- [ ] **Proof（AR-13）**：恢复重放测试——seek(offset) 后无全量重放（重复量 = 声明语义），checkpoint 上报 offset 与实际发射一致；负 offset typed 拒绝；既有钉定测试 `TestBatchLoaderSourceFunction.testStreamSourceOperatorCheckpointRestoreWithBatchLoader` 随裁定同步更新（其现断言谎报语义的路径）
- [ ] **Proof（AR-14）**：并行 commit 交错测试（parallelism=2，模拟 item 35 的 `fraud-parallel-2pc-file` 形态）——manifest 无条目丢失、无伪 commit 失败；既有并行 2PC e2e 零回归
- [ ] **Proof（AR-15）**：三缺口各一测试——截断文件恢复 typed 失败（非静默错位读）；并发 snapshot 期间游标一致性（或等价 JMM 论证 + 断言）；endOffset 封顶（文件增大后不越界发射）

Exit Criteria:

- [ ] D1 裁定落档；三组 Proof 测试存在且全绿（测试类/方法可指认）
- [ ] **无静默跳过**：skip 不足/负 offset/越界读均为显式 typed 失败，非静默继续
- [ ] connectors 能力矩阵（`nop-stream-connectors.md`）batch 行与实现一致（按裁定改写「发射计数 offset checkpoint + seek()」句与接口宣称，两分支收敛后行文如实）
- [ ] `./mvnw test -pl nop-stream/nop-stream-connector-batch,nop-stream/nop-stream-connector -am` 全绿；`ai-dev/logs/` 已更新

### Phase 2 - 数据面 topic 命名合法化（AR-12）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/StreamTopicNaming.java`、gated e2e fixtures（`RemoteGraphExecutionPlanBuilder.java:154` 的 edgeId 拼接为消费输入随消毒收口）

- Item Types: `Fix | Decision | Proof`

- [ ] **Fix（消毒，主修方向单一收口）**：`StreamTopicNaming.buildTopic`（`:31-35`）统一消毒——非法字符映射 + hash 后缀消歧（同 jobId+edgeId 消毒后冲突时仍可区分）+ 长度上限（Kafka 249 字符），jobId 不再裸拼。**收口原则：只改 `buildTopic` 一处**——`RemoteGraphExecutionPlanBuilder.java:383` 的 `edgeKey` 是 `deploymentPlan.getEdgeConfigs()` 的 map 查表 key（生产方 `DeploymentPlanGenerator.buildEdgeConfigs`，测试有字面量 `"A->B"` 如 `TestBufferPoolWiring:158`），**不进 topic、不动**（改它会静默破坏 edge config 查找）；`:154` 的 edgeId 生成保持原样，经消毒层产出合法 topic
- [ ] **Fix（测试 fixture 对齐）**：gated e2e `TestDataPlaneKafkaBackendE2E` 的 `EDGE_ID="src->tgt"` 改合法形态——测试不再自证非法命名「可用」
- [ ] **Proof（合法性）**：topic 构造单元测试——CJK/空格/`:`/`->`/超长 jobId 输入下产物恒匹配 `^[a-zA-Z0-9._-]{1,249}$` 且确定性（同输入同输出）、可消歧（不同输入不碰撞或碰撞可区分）；**合法输入恒等**：输入各段已合法时产物与旧格式逐字一致（既有 topic 字面量测试如 `TestRemoteInputChannelQueueFull:54` 不破坏）
- [ ] **Proof（端到端，in-repo 可执行形态）**：repo 内无嵌入式 Kafka broker 且 gated 测试（`nop.stream.test.kafka.enabled`）在 CI 从未启用——端到端验收定义为**不依赖真 broker 的 in-repo 形态**：(i) 经 `RemoteGraphExecutionPlanBuilder` 构建完整分布式 plan + fake/mock messageService，断言全部生成 topic（数据面 + `DataPlaneMessageServiceAdapter` 的 subscribeName）匹配合法 regex，**且收集到的 topic 集合非空/规模与远程边×subtask 对数相符（防空集合恒真）**；(ii) edge config 查表回归——`buildTopic` 消毒不改变 edgeKey 语义（含 `"A->B"` 形态 key 的 config 仍被找到）。真 broker 形态（gated e2e）作为可选加强证据（如执行环境可得则跑），非 closure 必需

Exit Criteria:

- [ ] 消毒单元测试 + in-repo 端到端断言（全 plan topic 合法 + edge config 查表零破坏）存在；fixture 非法 EDGE_ID 清除
- [ ] 命名方案记录（Daily log 或 design 注记：buildTopic 单点收口 + hash 消歧依据 + edgeKey 不动的论证）
- [ ] distributed-runbook 数据面节增补消毒后的 topic 命名规则说明（主动项：用户可见命名格式变化需文档化）
- [ ] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿；`ai-dev/logs/` 已更新

### Phase 3 - 信任边界加固（F-08、F-09、F-10）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/maintain/StreamStateResetTool.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/OpsJobManager.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsConfig.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeutils/JavaStreamSerializer.java`、`CheckpointSerDe.java`、`metrics.properties.template`

- Item Types: `Fix | Decision | Proof`

- [ ] **Fix（F-08 路径校验）**：`StreamStateResetTool.reset` 复用 storage 侧两段校验（`SAFE_ID_PATTERN` + canonical `startsWith`，对齐 `LocalFileCheckpointStorage.java:325-340` 范式）；零合法用例损失（合法 jobId 全部通过）
- [ ] **Proof（F-08）**：`refusesDotDotJobId` 等穿越拒绝测试（`../other-job`、编码变体、绝对路径）+ 既有 5 测试零回归
- [ ] **Fix（F-09a 类加载）**：`OpsJobManager.java:191-197` 加固——`Class.forName(name, false, loader)` 先接口检查后初始化（消除静态初始化器执行面）为无争议主修；**类名策略需裁定（Decision）**：直接套用 `ClassNameValidator.ALLOWED_PREFIXES` 白名单会拒掉第三方 `pipelineFactoryClass`（`nop-stream.md:117` 文档化的用户可扩展契约；in-repo 工厂全在白名单内，测试探不出）——裁定维度：(i) 拦截面收窄（拒 JDK 内部/数组/已知危险前缀 + 接口检查 + 不初始化，保留第三方扩展）；(ii) 白名单 + 契约变更（owner doc 明示「工厂类须在受控包前缀下」，breaking change 落档）。裁定 + 兼容性影响落 owner doc
- [ ] **Fix（F-09b 最小认证）**：bind ≠ 127.0.0.1 时强制最小 token 认证（配置键 + 全端点校验 + 未授权 401）；模板（`metrics.properties.template:14-18`）的「确保网络访问受控」注释升级为硬约束说明（跨机暴露必配 token）
- [ ] **Proof（F-09）**：未认证请求对非 loopback bind 的 server 收 401；恶意工厂类名（不在白名单/含初始化器副作用探测形态）被拒绝且无类初始化副作用；loopback 默认行为零回归
- [ ] **Fix（F-10a JEP 290 filter，含白名单兼容裁定（Decision））**：`JavaStreamSerializer.deserialize` 加 `ObjectInputFilter`。**裁定维度（执行须知）**：`MemoryStateSerDe.deserializeValue`（:786-813）把任意裸 `byte[]`/`__java_bytes__` payload 路由进该通道，而 `IStreamSerializer` 用户可插拔——合法负载可含任意用户 `Serializable` 类型，白名单过窄会拒掉合法用户状态（in-repo 测试全是 `io.nop.*` 类型探不出）。裁定项：白名单基线形态（如 `io.nop.**` + `java.**` + JDK 集合）+ 可配置逃生口（允许用户声明额外前缀/类）+ 拒绝时 typed 错误码与迁移说明 + 「用户自定义 serializer 负载恢复」兼容性测试（或显式 breaking-change 落档 migration-guide）。裁定落 owner doc + migration-guide
- [ ] **Fix（F-10b body checksum）**：`.checkpoint` body 增加 checksum（写入侧计算、恢复侧先验后析——checksum-before-deserialize），**复用 item 25 已落地的 `CheckpointSerDe` canonical checksum 机制扩展，不另造格式**；旧产物兼容路径按既有 `CheckpointFormatVersions` 版本信封裁定（legacy 容忍或拒绝，与 migration-guide 版本策略一致）
- [ ] **Proof（F-10）**：filter 拒绝测试（非白名单类反序列化被拒，typed 错误）；篡改 body checksum 失配 → 恢复期 typed 拒绝（先验后析，不进入 readObject）

Exit Criteria:

- [ ] F-08/F-09/F-10 各有 Proof 测试且全绿；`rg -n 'ObjectInputFilter' nop-stream` 命中（filter 已接线）
- [ ] F-09a 类名策略裁定 + F-10a 白名单兼容裁定均落档；第三方工厂/用户自定义 serializer 负载的兼容性有测试或显式 breaking-change 记录（migration-guide）
- [ ] **无静默跳过**：三个信任边界的拒绝路径全部 typed/显式（401 / filter 拒绝 / checksum 失配错误），无静默放行或静默跳过
- [ ] 既有 ops/CLI/恢复测试零回归（默认 loopback + 无 token 行为不变；legacy checkpoint 恢复路径按裁定有测试）
- [ ] owner docs 更新：`nop-stream.md`（reset 拒绝语义、ops token 配置键、反序列化白名单说明）、`metrics.properties.template` 注释、`nop-stream-migration-guide.md`（如 breaking change）；`ai-dev/logs/` 已更新

### Phase 4 - 验证面诚实化（F-07、F-11）

Status: planned
Targets: `.github/workflows/`、`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-user-guide.md`、`nop-stream-connectors.md`、`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Decision | Fix | Proof`

- [ ] **D2 gated 测试 CI 裁定（Decision）**：F-07 处置——(a) CI 增加 nightly/低频 lane 启用 `nop.stream.test.multi-jvm.enabled`（workflow 变更 + 运行证据）；(b) docs 降级：所有引用 gated 测试为「已证明」的锚点改为「手动验证」并记录启用命令（`rg "nop.stream.test" docs-for-ai/` 由 0 命中变为有启用文档）。依据：CI 资源成本 vs 分布式能力主张的证据链强度（分布式 exactly-once/fencing 是产品核心主张）。裁定 + 拒绝方案落 daily log / roadmap 注记
- [ ] **Fix（F-07 按 D2）**：实施所选分支——(a) workflow 文件 + 首次运行记录；或 (b) `source-anchors.md:268`、`nop-stream-user-guide.md:149`、`nop-stream-connectors.md:81` 三处锚点改写 + 启用命令文档化
- [ ] **Fix（F-11 runbook 诚实化）**：`nop-stream.md:90-91,112,231,254` 运维手册首行（或命令处）补 test-scope 限制与 workaround（消费 test-jar 或自建 launch 类，与 `nop-stream-user-guide.md:174` 对齐）——runbook 命令与发布产物的关系如实声明
- [ ] **Proof（F-11）**：文档一致性验证——runbook 命令的可用性声明与 live 产物 scope 一致（按文档操作者不会遇未披露的 `ClassNotFoundException`）；doc link checker 通过

Exit Criteria:

- [ ] D2 裁定落档并实施；三处「已证明」锚点与 CI 实际能力一致（lane 存在且绿，或降级措辞 + 启用命令）
- [ ] runbook test-jar 限制补齐（4 处锚点）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 本 Phase 为纯 CI/文档变更：`No new test required: workflow/docs-only（D2(a) 分支的 lane 运行记录即 Proof）`
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] AR-13/AR-14/AR-15：恢复契约三组 Proof 全绿；能力矩阵与实现一致
- [ ] AR-12：topic 恒合法（单元 + 端到端）
- [ ] F-08/F-09/F-10：穿越拒绝 / 401+类加载加固 / filter+checksum 全落地且有 Proof
- [ ] F-07/F-11：D2 裁定实施；锚点与 runbook 诚实化
- [ ] 无任何 P0/P1 发现被降级为 follow-up
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿（F-07 若选 lane 分支另附 lane 运行证据）
- [ ] checkstyle 通过
- [ ] 独立子 agent closure audit 完成 + Anti-Hollow 检查 + evidence 写入

## Deferred But Adjudicated

（无——本 plan 无 deferred 项；全部 9 项发现均为 in-scope Fix/Decision。）

## Non-Blocking Follow-ups

- 控制面/RPC topic 裸拼不经 `StreamTopicNaming`（`RpcDistributedExecutor:212`、`EmbeddedDistributedExecutor:143`、`StreamControlRpcTopics:28-29`）——Phase 2 消毒机制落地后复用收口；真 broker 控制面拉通前的隐藏雷（audit 2026-09-03-1951 第二轮复审 N-1）
- F-13（ops 错误分类依赖消息子串，P2）、F-25（threaddump 忽略 jobId，P2）——ops 面后续修缮，backlog 登记
- F-12（runtime 对 nop-dao provided 但 main 类 import，P2）——依赖作用域裁定，backlog 登记
- F-23（文件 2PC 与本地存储无 fsync，AR-23 同族，P2）——崩溃模型裁定后处理，backlog 登记
- AR-24（`Files.walk` FD 泄漏，P2）——与 Phase 1 同模块，可顺手修但以 backlog 为准

## Closure

Status Note: （待 closure 填写）
Completed: （待 closure 填写）

Closure Audit Evidence:

- Reviewer / Agent: （待 closure 填写）
- Evidence: （待 closure 填写）

Follow-up:

- （待 closure 填写）
