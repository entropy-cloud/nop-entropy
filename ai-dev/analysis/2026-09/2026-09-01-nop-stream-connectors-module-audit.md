# nop-stream connectors 四模块审计（roadmap item 10，Phase M 第四项）

> Status: resolved
> Date: 2026-09-01
> Scope: `nop-stream/nop-stream-connector/`（10 main / 9 test，含本审计新增 TestFileSourceAuditFixes）+ `nop-stream/nop-stream-connector-batch/`（3 main / 5 test）+ `nop-stream/nop-stream-connector-jdbc/`（2 main / 3 test）+ `nop-stream/nop-stream-connector-debezium/`（1 main / 4 test）四模块：2026-05-20 duplicate-code audit 与 2026-06-30 code audit 的 connector 相关发现整改收口验证 + 2300-1/2/3 remediation plans connector 侧归属核对 + 产品化视角新增审计（D-GAP §3.1 item 10 两项重点：候选钩子清单 + XDef/字段校验覆盖现状）+ 小缺陷就地修复与收口
> Conclusion: 两轮历史审计 connector 相关发现整改收口**成立**（05-20 §1—§9 无 connector 专属组——归属核对显式记录；06-30 seed+派生核对：landed/unchanged 为主、模块统计漂移按 AR-2 拆分记录、test 通配符导入 16 文件 partial 路由 Follow-up item 22，无 regressed；2300-1/2/3 三 plan Targets 逐条核对零 connector 侧修复点——预期成立）；D-GAP item 10 两项重点全部消化（①候选钩子清单自包含落地，供 Follow-up item 20 直接消费；②XDef/字段校验覆盖现状结论表——连接器配置全部走 Java 构造期校验，不经 stream.xdef 字段级链路）；产品化审计采信 8 项缺陷就地修复（CN-1 high 恢复后首次 checkpoint 光标回退 + 3 项行为修复配 14 个新 focused 用例 + fix-revert 验证 + 3 项日志/异常 taxonomy 修复 + hollow-scan 工具 P6b 检测缺陷修正）+ owner-doc（connector-design.md）3 处 stale 事实最小同步；hollow scan 四模块 exit 0；无大缺陷（显式无 Follow-up 追加；item 22 枚举事实补全随 closure 写回）；`./mvnw test -pl nop-stream -am -T 1C` 全模块绿
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 10；plan `ai-dev/plans/nop-stream-productization/2026-09-01-1457-2-connectors-module-audit.md`
> Related: `2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`、`2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7，方法论/报告结构复用；其 §2.2 Flink/Beam 8 格 Phase M 级裁定、§1.1 §7 行空壳模块结论本报告引用不重复裁定）、`2026-09/2026-09-01-nop-stream-runtime-module-audit.md`（item 8，§1.3 2300-2 runtime 侧复核）、`2026-09/2026-09-01-nop-stream-cep-module-audit.md`（item 9，前序 sibling）、`2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 10 两项重点）

## Context

- roadmap item 10（Phase M 第四个审计项，deps item 6 已 done，sibling items 7/8/9 已 done）：对四个 connector 模块按产品标准完成统一审计并收口。与 items 7/8/9 的差异：本审计有两项 D-GAP 下发的**新增产出物**（候选钩子清单、XDef 校验覆盖现状表），且 closure 写回含 item 22 枚举事实补全（connector 16 文件）。
- 审计方法与 items 7/8/9 对齐：live 锚点优先；所有 live 核对命令于 2026-09-01 在 worktree 根执行，排除 `target`；16 个 main 文件全量人工读审 + 20 个 test 文件覆盖盘点（模块小，全量盘点替代抽查）；核心契约对照面 = core 侧 `Source`/`SplitEnumerator`/`SourceReader`（FLIP-27 四契约）、`SourceFunction`/`SinkFunction`、`TwoPhaseCommitSinkFunction`、`CheckpointedSourceFunction`/`ReplayableSourceFunction`、`DrainableSource`。
- 验证基线：修复后 `./mvnw test -pl nop-stream -am -T 1C` 全模块绿（见 §3.3）。

## Phase 1 — 历史审计整改收口验证

### 1.1 2026-05-20 duplicate-code audit 归属核对

逐组确认 §1—§9 无 connector 专属组：

| 05-20 组 | 涉及模块 | connector 归属 |
|---|---|---|
| §1 operator vs operators、§3 runtime 死代码、§4 core 死代码、§5 TimerService、§6 孤立图执行路径、§8 core/state 包分裂、§9 core/sink 单文件包 | core/runtime | 无 connector 文件（§4.4 的 `TwoPhaseCommitSinkFunction`/`CheckpointedSourceFunction` 为 core 侧接口定义，其生产消费方在 connector——**正向接线证据**而非重复代码，见 §2.4） |
| §2 CepOperator vs CepWindowOperator | cep/runtime | 无 connector 文件 |
| §7 空壳模块（api/checkpoint/flink/flow 四件） | api/checkpoint/flink/flow | **connector 模块不在其中**（引用 item 7 报告 §1.1 §7 行已落定结论：3 删 1 实现，勿重复立项；本表仅记录归属） |
| §10 建议包结构 | 设计建议 | §10.2 将 connector 列为 Source/Sink 适配层（`core ← connector` 单向依赖的设计陈述，非缺陷组）；live 已细化为 4 模块（AR-2 拆分，见 §1.2 #1） |

**结论：05-20 审计无 connector 专属组**（显式记录，核对基准成立）；connector 相关的唯一正向事实——core 侧 2PC/Checkpointed 契约的生产消费方位于四模块（`FileTwoPhaseCommitSink`/`JdbcTwoPhaseCommitSink` 实现 `TwoPhaseCommitSinkFunction`，`DebeziumCdcSourceFunction` 实现 `CheckpointedSourceFunction`+`DrainableSource`，`BatchLoaderSourceFunction` 实现 `ReplayableSourceFunction`）——与 item 7 报告 §1.1 §4.4 行的「已收编」结论互证。

### 1.2 2026-06-30 code audit connector 相关发现核对表

> 先列全表再逐项核对。seed 清单 = plan Current Baseline 所列；派生规则 = 报告中「涉及文件位于四个 connector 模块」的其余发现。三态：landed / partial / regressed。

| # | 06-30 发现（章节） | live 三态 | 证据锚点 |
|---|---|---|---|
| 1 | §1.1 模块统计（时点 connector 7 main/11 test 单模块「活跃开发」） | **landed（活跃开发；统计漂移 = AR-2 模块拆分，改善性漂移不判 regressed）** | live 4 模块 16 main/20 test（connector 10/8→9、batch 3/5、jdbc 2/3、debezium 1/4；test 含本审计新增 1 文件）；拆分动因与契约由 base 模块 `package-info.java`「AR-2 module split」显式记载（可选依赖 connector 独立成模块，base 仅依赖 nop-api-core 的 IMessageService） |
| 2 | §1.3 依赖方向（时点 connector → core + nop-batch-core ✅） | **landed（依赖改善漂移：nop-batch-core 移至 connector-batch 专用模块）** | live pom：connector → core + nop-message-core；connector-batch → core + nop-batch-core；connector-jdbc → core + nop-dao；connector-debezium → core + nop-message-debezium——`runtime/connector/cep → core` 设计约束持续成立，且 base 模块比 06-30 时点更轻（AR-2） |
| 3 | §2.1 UOE 桩 6 处中无 connector 项（派生规则全扫四模块 main） | **unchanged（零 UOE，持续成立）** | `grep -rn UnsupportedOperationException` 四模块 main 零命中（exit 1）；`TwoPhaseCommitSinkFunction.copyForSubtask` 的 UOE guard 在 core（item 7 已裁定为 No-Silent-No-Op 合法模式），两个 2PC sink 均已 override |
| 4 | §2.2 通配符导入（live 复核：test 侧 16 文件残留——connector 5 / batch 5 / jdbc 2 / debezium 4；main 侧全模块已清零） | **partial（main 清零 / test 16 文件残留）** | `grep -rl "import .*\.\*;"` 逐模块复算：connector 5 / batch 5 / jdbc 2 / debezium 4（test），main 0/0/0/0。→ **路由 Follow-up item 22**（跨模块统一 sweep；**closure 写回含 item 22 枚举事实补全：connector 16 文件**，见 §3.2） |
| 5 | §2.2 大量 `return null`（集中 runtime JDBC/LocalFile；派生规则扫描四模块 main） | **unchanged（无 catch-return-null 模式）** | 四模块 main 仅 `MessageSourceFunction:153/170/173` 三处 `return null`——全部位于 `IMessageConsumer.onMessage` 回调（接口 ack 语义：返回 null = 无回复），非 catch-return-null |
| 6 | §2.2 硬编码值（派生：JdbcTwoPhaseCommitSink `DEFAULT_LEDGER_TABLE`） | **unchanged（非缺陷）** | ledger 表名有 ctor 参数可配（`ledgerTableName`），默认值 `stream_epoch_ledger` 为合理 default（对照 06-30 点名的 `JdbcCheckpointStorage` 表名硬编码无参数化——本类不属同款问题） |
| 7 | §3.1 测试覆盖（时点 11 类 50 @Test，测试/源码比 1.57） | **landed（覆盖增长）** | live 21 类（+本审计 1）静态 @Test 计数 133→147；修复后 surefire 执行 49+37+34+25=145（debezium 含 1 gated skipped），模块拆分后 main 从 7→16；覆盖面盘点见 §2.5 |
| 8 | §3.2 common.functions.source 零独立测试（「在 connector 中有间接测试」） | **unchanged（判定持续成立）** | `TestMessageAdapters`（5 用例）+ `TestBatchLoaderSourceFunction`（9）+ debezium 4 文件直接驱动 `SourceFunction.run/cancel`；core 接口层测试仍归 core（item 7 范畴） |
| 9 | §3.3 E2E 清单中 connector 相关（TestE2ETwoPhaseCommitSink） | **landed（E2E 家族扩展）** | live runtime 侧 `TestE2ETwoPhaseCommitSink` + `TestE2EJdbcTwoPhaseCommitSink` + `TestFileSourceE2E`（4 用例含 empty-dir 与 wiring 验证）+ connector 内 kill-recover 家族（file 2PC `testFileSinkKillRecoverExactlyOnce`、CDC `testCdcCheckpointKillRecoverNoDuplicates`、本审计新增 reader kill-recover，见 §3.1） |
| 10 | §4.4/§6.1/五（与 SeaTunnel 对比：连接器生态组织） | **unchanged（非缺陷项）** | `package-info.java` + AR-2 拆分已吸收「可选依赖分模块」组织法；生态产品化（SPI 注册中心/OLAP 连接器）归 Follow-up item 19（本 plan Non-Goal） |

**结论：无 regressed**；唯一 partial（#4 test 通配符 16 文件）路由 Follow-up item 22 并随本 plan closure 完成枚举事实补全；#1/#2 的统计与依赖漂移均为 AR-2 拆分带来的改善，按「时点值 → live 变化」记录。

### 1.3 2026-08-04-2300-1/2/3 remediation plans connector 侧归属核对

| Plan | Targets 逐条核对 | 复核结论 | 证据 |
|---|---|---|---|
| 2300-1 coordinator-runtime-concurrency-recovery-hardening | Targets：JobCoordinator（runtime）、InputGate（core）/GraphModelCheckpointExecutor（runtime）、TaskManager（runtime）、SupervisionLoop（runtime）/NopStreamErrors（core） | **不适用（无 connector 侧修复点）** | plan 全文 `grep -i connector` 零命中（exit 1）；四个 Targets 块逐条列于 :58/:80/:100/:117，全在 runtime/core |
| 2300-2 checkpoint-state-backend-cep-correctness | Targets：RocksDBKeyedStateBackend/RocksDBKeyEncoder（rocksdb）、CheckpointCoordinator（runtime）、JdbcCheckpointStorage（runtime）、SharedBufferAccessor（cep） | **不适用（无 connector 侧修复点）** | 同上零命中；Targets :59/:76/:93/:111 全在 rocksdb/runtime/cep（runtime 侧复核引用 item 8 报告 §1.3，rocksdb/cep 侧引用 item 9/11） |
| 2300-3 contract-drift-config-test-integrity | Targets：state-management-design/core-design + core state SPI、runtime `_module`、core/runtime 测试完整性 | **不适用（无 connector 侧修复点）** | 同上零命中；Targets :64/:84/:101 全在 core/runtime/docs |

**结论**：「三 remediation plans 已收口」对四 connector 模块**成立**（预期「无 connector 侧修复点」经执行时确认成立；注意 `JdbcCheckpointStorage`（runtime）与 `JdbcTwoPhaseCommitSink`（connector-jdbc）为不同类，前者归 item 8 已复核）。

## Phase 2 — 产品化视角新增审计

### 2.1 D-GAP 重点 ①：Source/Sink 契约「连通性校验钩子」候选清单

> D-GAP §3.1 item 10 要求输出**候选钩子清单**（每钩子：落点契约接口/方法、现有校验存在性证据、最小侵入点评估、供 Follow-up item 20 直接消费的接口语义说明）。本清单自包含，item 20 plan 无需回读 D-GAP 报告。核对范围：FLIP-27 `Source`/`SplitEnumerator`/`SourceReader`、`SourceFunction`、`SinkFunction`、`TwoPhaseCommitSinkFunction`、`CheckpointedSourceFunction`、配置 bean 构造期。

#### A. 逐契约钩子落点表

| # | 契约接口/方法（落点） | 现有「连通性/配置校验」存在性（live 证据） | 最小侵入点评估（供 item 20） |
|---|---|---|---|
| H-1 | `Source.createEnumerator()/restoreEnumerator()` + **`SplitEnumerator.start(ctx)`**（core/source/SplitEnumerator.java） | **构造期**：FileSource ctor 校验 directoryPath 非空（typed StreamException，本审计 CN-4）。**open 期（coordinator 侧）**：`FileSplitEnumerator.start()` → `discoverSplits()` :77-81 校验目录存在且为目录（IOException fail-fast）——**这是四模块唯一的 open 期连通性校验实证** | **推荐主钩子**：dry-run 以 no-op `AssignmentDeliveryService` 调 `createEnumerator()+start()` 即可探测目录可达性，**零 core 改动**。若要统一化：`Source` 增加 `default void validateConnection()` 空实现（core 侧 additive default，与 `SinkFunction.finish()` default 先例同构，低风险）——item 20 设计输入 |
| H-2 | `SourceReader` 首次 `pollNext()` 触发的 `openSplit()`（FileSourceReader.java:156-160） | open 期（task 侧）：`Files.exists(path)` 校验，缺失即 IOException fail-fast | 次选钩子（task 侧延迟发现）；dry-run 不应走 reader 路径（需要 assignment 基建） |
| H-3 | `SourceFunction.run(ctx)`（core/common/functions/source） | **无 open 期钩子**（Flink parity：SourceFunction 契约无 open()）。三个实现各自在 run 内建立连接：MessageSourceFunction :136 `messageService.subscribe`、BatchLoaderSourceFunction :68 `loaderProvider.setup`、DebeziumCdcSourceFunction :140-146 `createMessageSource+subscribe`。构造期均为 typed null/空串校验 | **推荐**：仿 `DrainableSource` 先例（core/connector 包，marker 接口）新增 `ConnectivityCheckable`（`void checkConnection() throws Exception`），连接器按能力实现——**additive、不改既有契约签名、无 @Internal 面破坏**；dry-run 驱动器 instanceof 分派。替代方案（不推荐）：dry-run 直接 run()+cancel()——时序竞争且对 Debezium 会拉起真引擎 |
| H-4 | `SinkFunction.consume(value)`（core/common/functions） | **无 open 期钩子**。MessageSinkFunction 逐条 `messageService.send`（构造期 typed 校验 + CN-7 null 边界）；BatchConsumerSinkFunction **构造函数即调用 `consumerProvider.setup(taskContext)`** :71——构造期连通性探测的天然钩子 | BatchConsumer：dry-run 构造即可探测（零改动）。MessageSink：`IMessageService` 无 health-check API——item 20 需裁定探测语义（send 探针消息 vs 跳过），**记录为 item 20 的设计缺口** |
| H-5 | **`TwoPhaseCommitSinkFunction.beginTransaction()`**（core 抽象方法，两实现） | JdbcTwoPhaseCommitSink.beginTransaction → `ensureInitialized()` :176-185 → `jdbcTemplate.getDialectForQuerySpace(querySpace)`（datasource 配置解析，**运行前可触达的既有校验钩子**；物理连接在 commit :257 打开）；另有显式 `initializeLedgerTable()` DDL 探测。FileTwoPhaseCommitSink ctor :114-119 `Files.createDirectories(outputDirPath)`（存储可达性，构造期 typed fail-fast） | **推荐主钩子**：dry-run 调 `beginTransaction()+rollback()`——**零 core 改动**（复用既有生命周期语义）。JDBC 物理连通探测可追加 dry-run 专用 `initializeLedgerTable()`（幂等 DDL）或 `SELECT 1`（item 20 裁定） |
| H-6 | 配置 bean 构造期（各连接器 ctor，含 Builder） | 全部有 typed fail-fast（见 §2.2 表）；唯一跨字段校验样例：MessageSourceFunction :95-102（subtaskIndex/totalParallelism 联动） | 钩子不在 bean 本身：XDSL 路径的实例化落点在 `StreamModelDslBuilder.buildSource/buildSink`（flow 模块 :442-453/:509-519，bean 解析或 xpl 编译）——**item 20 可在 builder 解析后、execute 前插入 validate 回调**（flow 侧 additive；flow 深审归 item 11 重点 ②，此处仅记消费路径事实） |

#### B. item 20 消费建议（接口语义说明）

- **优先级排序**：H-5（2PC sink，零改动）> H-1（FLIP-27 source，零改动）> H-3/H-4（SourceFunction/SinkFunction 家族，需 `ConnectivityCheckable` additive 接口）> H-6（builder 级统一入口，flow 侧改动）。
- **凭据加密接入点**（P-REQ-14）：`DebeziumConfig` 20 字段含 `databaseUser/databasePassword`（明文 Serializable 进 checkpoint/TDD 序列化路径，`testConfigSurvivesSerialization` 钉定）——item 20 的 nop-credential 接入应覆盖该 bean 的 password 字段解密落点（构造 `DebeziumCdcSourceFunction` 前）；JDBC 侧凭据在 `IJdbcTemplate` 平台配置内（connector 不经手），探测点在 H-5。
- **conf-validate 命令的数据面**：字段级校验链路现状见 §2.2（模型层走 stream.xdef，连接器配置层走构造期）——独立校验命令可复用「构造 + H-1/H-5 探测」组合，不启动作业。

### 2.2 D-GAP 重点 ②：各连接器配置 bean 的 XDef/字段校验覆盖现状结论表

> 核对基准：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef` live 存在（D-GAP §1.1 P-REQ-14 证据锚点复核成立）。flow 侧仅核对消费路径事实（flow 编译器深审归 item 11 重点 ②）。

**消费路径事实**：XDSL 的 source/sink 节点定义（`StreamSourceModel`/`StreamSinkModel`）携带 `consistencyCapability`（enum 校验）、`maxParallelism`、`params`（name/var-name + generic-type + defaultValue 字段级校验）与 `bean`（bean-name 引用）+ `<source>xpl</source>`（xpl 函数体）——连接器实例经 `StreamModelDslBuilder.buildSource:445` 的 `beanResolver.resolve(t.getBean(), SourceFunction.class)` 或 xpl 闭包构造。**连接器自身的配置字段（目录、表名、topic、Debezium 连接参数等）不作为 stream.xdef 的类型化字段存在**，因此不经 XDef 字段级校验链路。

| 连接器配置 bean | stream.xdef 字段级校验 | Java 构造期校验 | 运行期（open/run）校验 | 无校验面 |
|---|---|---|---|---|
| `FileSource(directoryPath)` | ✗（不在 xdef 模型内） | ✓ typed（CN-4 后 StreamException） | ✓ 枚举期目录存在性（H-1） | — |
| `FileTwoPhaseCommitSink(outputDir, charset)` | ✗ | ✓ typed（null/空 + createDirectories） | ✓ commit 期 IO fail-fast | — |
| `JdbcTwoPhaseCommitSink`（6 ctor 参数）/ `JdbcTwoPhaseCommitSinkBuilder` | ✗ | ✓ typed（4 项 null 检查；Builder 委托 ctor） | ✓ beginTransaction dialect 解析 + commit 物理连接（H-5） | — |
| `MessageSourceFunction(svc, topic[, type, subtask, parallelism])` | ✗ | ✓ typed + **跨字段**（subtask<parallelism） | ✗（subscribe 失败即任务失败，无预检） | broker 可达性无预检 |
| `MessageSinkFunction(svc, topic)` | ✗ | ✓ typed（+CN-7 value 边界） | ✗（send 失败即任务失败） | 同上 |
| `BatchLoaderSourceFunction(provider, batchSize)` | ✗ | ✓ typed（null + batchSize≥1） | ✓ setup 在 run 期（H-3） | — |
| `BatchConsumerSinkFunction(provider, batchSize)` | ✗ | ✓ typed + **setup 在构造期**（H-4，天然探测点） | ✓ | — |
| `DebeziumCdcSourceFunction(DebeziumConfig)`（20 字段：host/port/user/**password**/dbname/connectorType/offset 存储等） | ✗ | ✓ ctor null + `resolveConnectorName` 强制 name（AR-03，typed ERR_STREAM_CONFIG_ERROR） | Debezium engine 启动期校验 host/port 等 | **凭据明文**（无加密接入；P-REQ-14 缺口的 connector 侧实证） |

**结论**：模型结构层（拓扑/一致性能力/参数类型）已走 `stream.xdef` 字段级校验；**连接器配置层 8/8 全部依赖 Java 构造期 fail-fast，无一进入 XDef 类型化字段**；两处连通性预检空白（Message source/sink broker 可达性）与一处凭据加密空白（DebeziumConfig.password）为 item 20 的三项核心输入。

### 2.3 四模块统一审计（重复代码 / 契约一致性 / 错误处理与资源管理）

#### A. 验证干净的维度（明确核验无发现）

| 维度 | 结论 | 证据 |
|---|---|---|
| file/jdbc 2PC sink preCommit/commit 同构度 | **结构性镜像、无行为漂移**：两者均 saveState-first（buffer→pendingCommits 在 super.saveState 前）+ preCommit no-op（有注释依据：saveState 已完成搬移，与 `StreamSinkOperator.processBarrier` 的 saveState-before-preCommit 时序对齐）+ 幂等 commit guard（manifest containsKey vs ledger SELECT）+ copyForSubtask 独立拷贝（file 按 ownerSubtask 后缀路径 / jdbc 按 (epoch,subtask) 复合主键——同工异构均正确） | FileTwoPhaseCommitSink.java:171-244 vs JdbcTwoPhaseCommitSink.java:216-311；TestFileTwoPhaseCommitSink 14 用例 + TestJdbcTwoPhaseCommitSinkDeep 13 用例双方钉定 |
| batch 与 message 连接器 source/sink 对称性 | 对称成对（BatchLoader↔BatchConsumer、MessageSource↔MessageSink）；不对称处均有语义依据：BatchConsumer 有 finish/close 刷洗（缓冲型 sink 需要），MessageSink 无缓冲无需；BatchLoader 是 Replayable（批数据可重放）而 MessageSource 不参与 checkpoint（broker 语义） | 四文件对照 + TestConnectorConsistencyCapability 14 用例（能力枚举声明一致性） |
| 与 core 的重复逻辑 | 未发现应下沉的重复：2PC 协议骨架已在 core 基类（pendingCommits 管理/subsuming finishCommit/restoreFromEpoch durable-vs-nondurable 分流）；FileSourceReader 自带字节精确行读取为 cursor 记账所需（平台 FileHelper 无字节计数行读语义，非重复实现）；错误码全部复用 core NopStreamErrors（ERR_STREAM_NULL_ARG/INVALID_ARG/CHECKPOINT_ERROR/STATE_ERROR/CHAINING_OUTPUT_FLUSH_FAILED） | TwoPhaseCommitSinkFunction.java 全读 + 各连接器 import 面 |
| source/sink 契约一致性——生命周期时序 | 各连接器对「ctor（配置校验）→ open/setup（连通）→ run/consume（数据）→ close/abort（清理）」的职责分层一致；`StreamSourceOperator` 对 Replayable（:305 snapshot/:330 seek）与 CheckpointedSourceFunction（snapshotState/initializeState 经 TaskStateSnapshot 重建）的接线与连接器实现语义吻合 | StreamSourceOperator.java:300-345 + DebeziumCdcSourceFunction/BatchLoaderSourceFunction 实现 |
| 错误传播语义 | 无吞异常路径（修复 CN-6 后 Debezium 清理路径也留痕）；MessageSourceFunction P1-9 capture-and-rethrow 模式正确（pendingError → run() 重抛，region restart 复位三件套齐全）；BatchLoader 的 loader 异常自然传播；2PC commit 失败保留 pendingCommits 供 subsuming 重试（file :234 / jdbc finally 块 rollback+close） | 逐文件读审 + TestMessageSourceFunctionThreadSafety 3 用例 |
| 资源管理 | FileSourceReader.close/restoreState 双路径关闭 active reader（warn 留痕）；JdbcTwoPhaseCommitSink.commit finally 五步（rollback→autocommit→close，均 warn 留痕）+ initializeLedgerTable finally close；Debezium run-finally 双清理 + cancel/truncate 双入口清理（CN-6 后全部留痕）；BatchLoader finally 关 closable loader；BatchConsumer close 先 flush 后关 consumer（CN-3 后失败优先级正确） | 逐文件读审 + TestConnectorResourceManagement/TestDebeziumResourceManagement |
| 并发与复位 | MessageSourceFunction（volatile running/failed/pendingError + latch 复位）与 DebeziumCdcSourceFunction（runEntered CAS + latch 复位 + `testDrainingFlagResetOnReRun`）的 region-restart 复位语义均被测试钉定；FileSourceReader synchronized(this) 全方法守卫 | 对应测试用例锚点 |

#### B. 采信缺陷（Phase 3 就地修复，处置见 §3.1）

| ID | 严重度 | 发现 | 锚点 | 处置 |
|---|---|---|---|---|
| CN-1 | **high（checkpoint 正确性）** | `FileSourceReader` 恢复后首次 pollNext 的光标计算丢弃恢复基点：`openSplit` 将 `activeBytesConsumed` 清零（skip 到 cursor 后），pollNext 以 `startOffset + activeBytesConsumed` 计算新光标——恢复分片第一条记录后 snapshot 的光标**回退**为 `startOffset + 本次打开后读取字节`（如 cursor=3 恢复读 1 行后记 3 而非 6）。违反 FileSplit javadoc「cursor = 下一次读取的字节位置」契约；崩溃循环场景可无限重放；既有测试 `readerRestoreResumesFromCheckpointedCursor` 一次性 drain 不做中间 snapshot，缺陷不可见 | FileSourceReader.java openSplit/pollNext（修复前 :171/:137） | Phase 3 修复 + fix-revert 验证 + kill-recover E2E（§3.1） |
| CN-2 | medium（静默损坏） | FileSplit/FileSplitEnumeratorState 序列化器对畸形 payload 静默误解析：路径含 `\|` 时 split 为 >4 段却取前 4 段（路径截断+偏移错位）；enumerator state 的 split 行 `parts.length < 4` **静默 continue**（restore 时静默丢 split = 数据丢失）；非数字偏移抛未包装 NumberFormatException；CSV 段路径含 `,` 无法 round-trip 无任何报错 | FileSource.java FileSplitSerializer/FileSplitEnumeratorStateSerializer | Phase 3 修复（serialize 拒绝保留字符 + deserialize 拒绝段数≠4 + NumberFormatException→IOException） |
| CN-3 | low（错误信号优先级） | `BatchConsumerSinkFunction.close()`：flush 与 consumer.close() **双失败**时抛 close 错误——flushError（数据可能丢失信号）仅被加 suppressed 到自身后**丢弃**（`flushError.addSuppressed(e)` 后 throw close 异常，flushError 永不出场） | BatchConsumerSinkFunction.java:129-144 | Phase 3 修复（flush 失败优先为主信号） |
| CN-4 | low（异常 taxonomy 一致性） | `FileSource`/`FilePendingCommit` ctor 用裸 IllegalArgumentException——同模块 FileTwoPhaseCommitSink 及其余三模块全部用 StreamException + NopStreamErrors（06-30 §异常规范 + 两级错误策略），四模块内不一致 | FileSource.java:48-53 / FilePendingCommit.java:38-40 | Phase 3 修复 |
| CN-5 | low（log-and-throw 双重报告） | `MessageSourceFunction` onMessage collect 失败：`LOG.error` 后经 pendingError 由 run() 重抛——同一失败双通道报告（与 item 9 CE-6 同族；pendingError 重抛是单一可观测通道） | MessageSourceFunction.java:162 | Phase 3 修复（去 LOG.error） |
| CN-6 | low（清理路径静默吞异常） | `DebeziumCdcSourceFunction.run()` finally 两处 `catch (Exception e) { // ignore cleanup errors }` **不抛也不记**——违反「吞掉异常不抛出不记录」禁令的清理路径变体 | DebeziumCdcSourceFunction.java:156-168 | Phase 3 修复（LOG.warn 留痕） |
| CN-7 | low（边界一致性） | `MessageSinkFunction.consume(null)` 无守卫直达 `messageService.send`——同族 sink（File 2PC invoke/JDBC invoke/BatchConsumer P1-15）全部边界 fail-fast，本类为唯一例外（下游 NPE 或静默丢消息） | MessageSinkFunction.java:43-45 | Phase 3 修复 |
| CN-8 | **工具检测缺陷（hollow scan 误报）** | `scan-hollow-implementations.mjs` P6b 正则 `\/\/.*temp\s` 匹配任意含「temp+空格」的注释——FileTwoPhaseCommitSink :188「temp file. No-op.」/:230「temp → final」为**领域词汇**（临时文件/最终文件），两处代码均为真实逻辑（preCommit 有注释依据的 no-op；commit 的原子 rename 本体）→ 误报使 connector 模块 scan exit 1，阻断 closure 硬门禁 | scan-hollow-implementations.mjs P6b | Phase 3 修复工具正则（temp 仅在 implementation-marker 短语中匹配），全 10 模块复跑 exit 0 |

#### C. watch-only residual（逐条附 Why Not Blocking Closure，Allowed Deferred Classification）

| ID | 分类 | 发现 | Why Not Blocking Closure |
|---|---|---|---|
| W-1 | watch-only residual | `FileSourceReader.isFinished()` 空源早退仅识别 `LocalSourceCoordinator.ReaderChannel` 代理（:102-109）——分布式代理下空目录 source 无法经该路径判定完结 | 空目录 E2E（TestFileSourceE2E.e2eEmptyDirectoryCompletesWithEmptySink）覆盖 LOCAL 模式为当前 supported 基线；分布式 FLIP-27 source E2E 属 Phase S（items 13/14）验证面；接线变更在 core/runtime 侧（超出本 plan 四模块边界），记录为 Phase S 输入 |
| W-2 | watch-only residual | `FileSplitEnumeratorState.nextSubtaskIndex` 字段被序列化/恢复但从不参与分配（分配用 discovery-index % parallelism），字段 javadoc「round-robin cursor」与实现漂移 | 字段为版本化 payload 第 2 行（`lines.length < 6` 校验依赖），删除会改变序列化格式破坏既有 checkpoint 兼容——格式兼容优先；分配算法正确性不受影响（有测试钉定分配结果） |
| W-3 | optimization candidate | 两套 2PC sink 的 saveState-first/preCommit-no-op/null-guard/copyForSubtask 四处薄同构（各 ~10 行）+ FileSource 内 split 行格式在两个序列化器中重复编解码 | 去重需 core 基类增加模板钩子（跨模块契约变更，本 plan Non-Goal）；行为被双方测试钉定无漂移；与 item 21（core 重复代码第二轮）同族可合并处理 |
| W-4 | watch-only residual | **`DrainableSource` 契约生产接线为零**：core 定义接口（@Internal）、DebeziumCdcSourceFunction 实现、runtime DRAIN 收敛路径不调用 truncateForDrain（全仓 main 侧引用仅接口+实现两文件；调用点全在 test） | 连接器侧实现 live 且被 5 个测试钉定（含 runtime TestDistributedExactlyOnce 的手工调用）；是否让 DRAIN 收敛调用该契约属 runtime 侧行为决策（item 26 邻域），非四模块内缺陷；已写入 connector-design.md §7 已知限制 #12（本审计附带裁定） |
| W-5 | watch-only residual | `BatchLoaderSourceFunction.currentOffset` 非 volatile（run 线程写 / snapshot 读） | 引擎 task 单线程模型下 snapshot 与 run 同线程（StreamSourceOperator mailbox/barrier 模型）；`testStreamSourceOperatorCheckpointRestoreWithBatchLoader` 端到端钉定；若未来引入异步 barrier 需同步（与 BatchConsumerSinkFunction P1-15 的线程契约注释同族前提） |
| W-6 | watch-only residual | `MessageSourceFunction` 声明 AT_LEAST_ONCE 但无 offset checkpoint（重启后重订阅，投递语义依赖 IMessageService 后端 broker 重投递） | 能力声明与 nop-message 后端语义一致（roadmap Framework reuse 表：SysDao/Pulsar/Kafka 三后端均 broker 中介）；exactly-once 消息路径在 connector-design §5.2 已注记为 2PC 候选（Follow-up item 19/20 邻域）；已写入 §7 限制 #11 |
| W-7 | watch-only residual | `FileSplitEnumerator.discoveredFiles` 用 ConcurrentHashMap.newKeySet，restore 后迭代序与原发现序可能不同 → discovery-index % parallelism 的分片→子任务映射跨恢复不稳定 | 分配语义为 pull-anyway（任一 reader 可消费任一未分配 split），映射不稳定不破坏正确性/fair-share（总量守恒被 TestFileSource 钉定）； bounded 一次性发现场景下无实际投诉面 |
| W-8 | watch-only residual | `DebeziumCdcSourceFunction.run()` 内 `if (!draining)` 守卫在 `this.draining = false` 复位后仅剩并发窗口意义（与 truncateForDrain 竞态时跳过订阅） | volatile 读写的竞态守卫语义成立（非死代码）；极端窗口下行为正确（跳过订阅直接进入清理）；加注释属风格优化非缺陷 |

**否决项（读审中发现经复核不采信为缺陷）**：

| 发现 | 否决理由 |
|---|---|
| 「JdbcTwoPhaseCommitSink 使用未声明的 LOG 字段」 | LOG 继承自 `TwoPhaseCommitSinkFunction`（core 基类 protected static final，:29）——继承可见性合法 |
| 「MessageSourceFunction 三处 return null 违反 null 规范」 | `IMessageConsumer.onMessage` 的返回值为 ack 语义（null = 无回复），接口契约要求，非 placeholder |
| 「BatchConsumerSinkFunction 构造期 setup 是隐藏连通性副作用，应移入 open」 | SinkFunction 契约无 open()；构造期 setup 即 H-4 天然探测点且被 8 个测试钉定；移动属行为变更无收益 |

### 2.4 空壳/静默跳过扫描（hollow scan）

- 扫描命令与退出码（**修复 CN-8 工具正则后**）：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-<m> --severity high` → connector **0 critical/0 high, exit 0**；batch/jdbc/debezium 同命令 **exit 0**（四模块修复前基线：connector exit 1——2 个 P6b 误报即 CN-8，其余三模块本就 exit 0）。
- 误报处置：**已执行工具检测规则修正**（非仅记录）——P6b 的 `\/\/.*temp\s` 收紧为 `\/\/.*\btemp(orary)?\s+(implementation|method|fix|workaround|hack)\b`，工具内注释记录裁定来源；全 10 模块（含 core/runtime/cep/rocksdb/flow/fraud-example）复跑 exit 0，无回归（收紧只会减少匹配）。
- 多行 UOE 人工补审（行级正则盲区，沿 items 7/8/9 基线）：四模块 main 零 UOE（§1.2 #3），无多行盲区面；空方法体人工核验：`FileSplitEnumerator.close(){}`（无资源可释放的生命周期合规空实现）、`FileSourceReader.notifyCheckpointComplete`（有注释的 v1 no-op——文件 offset 天然幂等）、两个 2PC sink 的 `preCommit`/`beginTransaction`（有注释依据的协议锚点，语义由基类时序注释与测试钉定）——全部为**有注释依据的接口合规空实现**，非静默跳过。
- **结论：无 high/critical 真实发现；2 个误报已按处置路径消除（工具修正），四模块 exit 0 硬门禁达成**。

### 2.5 测试覆盖抽查（模块小，全量盘点替代抽查）

**四模块测试类 → 契约面映射（20+1 文件 / 133+14 @Test）**：

| 模块 | 测试文件（用例数） | 覆盖的契约面 |
|---|---|---|
| connector | TestFileSource(9)、TestFileSourceCheckpointRestore(3)、TestFileTwoPhaseCommitSink(14)、TestConnectorResourceManagement(2)、TestDrainableSourceSupport(1)、TestMessageAdapters(5+1)、TestMessageSourceFunctionRestart(1)、TestMessageSourceFunctionThreadSafety(3)、**TestFileSourceAuditFixes(10，本审计新增)** | FLIP-27 四契约（发现/分配/读取/恢复 round-trip）、file 2PC 全生命周期（含 kill-recover、并行子任务隔离、manifest 修复边缘）、message 双向适配（P1-9 错误 surfaced、线程安全、重启复位）、Drainable 契约、资源管理 |
| batch | TestBatchLoaderSourceFunction(9)、TestBatchConsumerSinkFunction(8)、CloseLogging(2+1)、Failure(3)、TestConnectorConsistencyCapability(14) | 加载/消费循环、cancel、batch 边界、flush 失败保留重试、close 失败语义、Replayable offset 接线（含 StreamSourceOperator 集成）、能力声明矩阵 |
| jdbc | Skeleton(19)、Deep(13)、ParallelIsolation(2) | 构造校验全集、saveState-first 语义（防 lag-one-epoch）、幂等 commit（ledger 跨 epoch）、abort/rollback、序列化 round-trip、restoreFromEpoch 三态（durable 重提交/非 durable abort/混合）、subsuming、独立事务、并行子任务隔离 |
| debezium | TestDebeziumCdcCheckpoint(12)、SourceFunction(11)、Completion(1)、ResourceManagement(1) | offset checkpoint round-trip、kill-recover 无重复、AR-03 首跑不续 stale offset、无名连接器 fail-fast、truncateForDrain/cancel/重入清理、自然完结、一致性声明 |

**行数 top-5 文件直接覆盖**：JdbcTwoPhaseCommitSink(473)→Skeleton+Deep+ParallelIsolation；FileTwoPhaseCommitSink(376)→14 用例；FileSourceReader(306)→TestFileSource+TestFileSourceAuditFixes；DebeziumCdcSourceFunction(296)→4 文件；FileSource(275)→TestFileSource+CheckpointRestore。

**契约一致性测试缺口清单（plan 交付物）**：

| # | 缺口 | 归属 | 理由 |
|---|---|---|---|
| G-1 | 恢复后**中间** checkpoint 的光标推进语义（既有测试只做终态 drain 或序列化 round-trip） | **本 plan 补齐**（CN-1 测试） | 小缺陷判定：四模块内、无新基建 |
| G-2 | 序列化器畸形 payload fail-fast（段数/保留字符/非数字） | **本 plan 补齐**（CN-2 测试） | 同上 |
| G-3 | close 双失败时 flush 信号优先级 | **本 plan 补齐**（CN-3 测试） | 同上 |
| G-4 | MessageSink null 边界 | **本 plan 补齐**（CN-7 测试） | 同上 |
| G-5 | LF/CRLF/lone-CR 三型行尾的字节精确记账 | **本 plan 补齐**（CN-1 附属测试） | 同上 |
| G-6 | 2PC 失败路径 / split enumerator 恢复 / CDC offset checkpoint | **已覆盖，无缺口** | Deep 13 用例（restoreFromEpoch 三态/subsuming/独立事务）、enumerator state round-trip + TestFileSourceE2E、CDC checkpoint 12 用例 |
| G-7 | file 2PC × FLIP-27 source 组合的 env 级恢复 E2E（mid-stream checkpoint + 重新 execute） | **不补齐，记录** | 需要跨 env 生命周期的 checkpoint 恢复基建（manifest 加载 + 二次 execute），属 Phase S（items 13/14）验证面与 item 28 邻域；契约层 kill-recover（G-1 测试 + 既有 2PC/CDC kill-recover 家族）已覆盖恢复语义本身 |

### 2.6 D-GAP §3.1 item 10 勾销清单对照

| # | D-GAP §3.1 item 10 条目 | 消化结论 | 证据 |
|---|---|---|---|
| ① | P-REQ-13/14 go 裁定的接口落点预核——输出候选钩子清单（供 item 20 直接消费） | **落地**：§2.1 A 表 6 钩子（H-1..H-6，各含落点/现有校验证据/最小侵入点）+ B 消费建议（优先级 + 凭据接入点 + conf-validate 数据面）——自包含，item 20 无需回读 D-GAP | 本报告 §2.1 |
| ② | 各连接器配置 bean 的 XDef/字段校验覆盖现状 | **落地**：§2.2 结论表（8/8 连接器配置 bean 逐条归属：全部构造期、无一经 stream.xdef 字段级；两处 broker 预检空白 + 一处凭据加密空白为 item 20 输入） | 本报告 §2.2 |

无遗漏条目（D-GAP item 10 行仅此两项）。

## Phase 3 — 缺陷处置与收口

### 3.1 小缺陷就地修复（8 项全部限四模块内 + 1 项工具修正；行为修复配 focused 测试）

| ID | 修复 | focused 测试（验证的新行为/契约语义） |
|---|---|---|
| CN-1 | `FileSourceReader.openSplit` 以 `split.getCurrentOffset() - split.getStartOffset()` 播种 `activeBytesConsumed`——恢复基点成为光标计算基底，首次恢复后 snapshot 光标 = 恢复基点 + 后续读取字节 | **新增 TestFileSourceAuditFixes**：`snapshotAfterFirstPostRestorePollAdvancesFromRestoredBase`（恢复 cursor=3 → 读 1 行 → snapshot=6；**fix-revert 验证：临时禁用守卫后该用例 FAIL（实际=3），证实非空壳**）+ `killRecoverContinuationEmitsEachRecordExactlyOnce`（两段 reader + 中间 snapshot，5 记录跨崩溃边界恰好一次——source→collected-sink 端到端，沿 file 2PC/CDC kill-recover 家族模式）+ `loneCrAndCrLfTerminatorsKeepCursorByteAccurate`（lone-CR/CRLF/LF 三型行尾字节精确记账） |
| CN-2 | FileSource 两序列化器 fail-fast 加固：serialize 拒绝路径含 `\|`/换行（split）/`,`（CSV 段）；deserialize 拒绝段数≠4（split 行静默丢 split → IOException）+ NumberFormatException 包装为 IOException | **新增** 4 用例：`splitSerializerRejectsPayloadWithExtraPipeParts`/`splitSerializerRejectsNonNumericOffsets`/`splitSerializerRejectsPathWithReservedSeparators`/`enumeratorStateSerializerRejectsMalformedSplitLine` + `enumeratorStateSerializerRejectsPathWithCommaInCsvSection` |
| CN-3 | `BatchConsumerSinkFunction.close()` 双失败时 flushError 为主信号（close 错误 suppressed 附着；仅 close 失败时维持原 close 异常路径） | **新增** TestBatchConsumerSinkFunctionCloseLogging.testCloseWithFlushAndCloseFailurePrioritizesFlushError（主异常含 flush 语义 + cause 链含原始 flush 失败 + suppressed[0]=close 失败） |
| CN-4 | FileSource/FilePendingCommit ctor 裸 IAE → StreamException（ERR_STREAM_NULL_ARG + 参数名，与其余连接器 taxonomy 对齐） | **新增** 2 用例（fileSourceConstructorRejectsNullAndEmptyWithTypedException / filePendingCommitConstructorRejectsNullWithTypedException）；无既有测试钉定 IAE（grep 零命中），无迁移成本 |
| CN-5 | MessageSourceFunction collect 失败路径去 LOG.error（pendingError 重抛为单一可观测通道） | 日志级清理（异常路径行为不变），既有 TestMessageSourceFunctionThreadSafety.testCollectFailureSurfacesFromRun 钉定重抛语义（No new test required per guide Rule #25） |
| CN-6 | DebeziumCdcSourceFunction run-finally 两处清理 catch 补 LOG.warn（消除不抛不记的静默吞） | 日志级增补（清理路径行为不变），既有 testFinallyBlockCleansUpResources 钉定清理语义（No new test required per Rule #25） |
| CN-7 | MessageSinkFunction.consume null 边界 fail-fast（StreamException，与同族 sink 对齐） | **新增** TestMessageAdapters.testSinkRejectsNullValueAtBoundary |
| CN-8 | hollow-scan 工具 P6b 正则收紧（`temp` 仅匹配 implementation-marker 短语；工具内注释记录裁定） | 工具修正：全 10 模块 `--severity high` exit 0 复跑验证（检测缺陷类修复，验证=门禁退出码本身） |
| — | **owner-doc 最小事实同步**（connector-design.md）：§7 #3「IBatchChunkContext 传 null」stale（live flush 已传 BatchChunkContextImpl）删除并重排；§6 表 MessageSourceFunction「CheckpointParticipant」/MessageSinkFunction「2PC（Pulsar）」两格漂移修正为 live 能力；新增 §7 #11（消息 source/sink 无 offset checkpoint）/#12（DrainableSource 未接线，W-4/W-6 的文档化裁定） | 纯文档级（与 live 对齐），doc-links 门禁验证（No new test required per Rule #25） |

**端到端验证裁定**（plan Phase 3 要求：触及 source/sink 数据面语义的修复——CN-1 触及 source 光标数据面）：`killRecoverContinuationEmitsEachRecordExactlyOnce` 为 source→sink 契约级 kill-recover E2E（沿 `testFileSinkKillRecoverExactlyOnce`/`testCdcCheckpointKillRecoverNoDuplicates` 家族先例——本仓库 E2E 家族的 crash-sim 惯例）；env.execute() 级全路径由既有 TestFileSourceE2E 4 用例（单/并行/空目录/wiring）+ TestE2ETwoPhaseCommitSink/TestE2EJdbcTwoPhaseCommitSink 持续绿承载；env 级 mid-stream 恢复 E2E 归 G-7（Phase S 基建）。CN-1 未触及 2PC 路径，exactly-once 断言由既有 2PC 测试族保障不回退。

### 3.2 大缺陷处置裁定：**无大缺陷**（显式记录）

全部采信发现（CN-1..CN-8）满足小缺陷判定准则（修复限四模块内/工具内、不改 core 公共契约、无需新测试基建）；W-1..W-8 watch-only residual 逐条附 Why Not Blocking（§2.3 C 表，均属三类合法延期分类）。**roadmap 无新 Follow-up 追加**；既有承接关系：test 通配符 16 文件 → item 22（本 closure 完成**枚举事实补全**：item 22 现有枚举 core 169/runtime 108/cep 15/flow 1 系 item 7 时点部分计数，补记 connector 四模块 16 文件——connector 5/batch 5/jdbc 2/debezium 4，live 复算值）；2PC sink 薄同构去重 → item 21 邻域（W-3）；DrainableSource 接线 → runtime 侧决策（W-4，item 26 邻域，非立项）。

### 3.3 回归验证（2026-09-01 执行记录）

- `./mvnw test -pl nop-stream/nop-stream-connector,nop-stream/nop-stream-connector-batch,nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector-debezium -T 1C` → BUILD SUCCESS：**connector 49/0、batch 37/0、jdbc 34/0、debezium 25/0（1 skipped 为既有 gated）**（含 14 个新 focused 用例）。
- `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` → BUILD SUCCESS（编译含 checkstyle 阶段）。
- `./mvnw test -pl nop-stream -am -T 1C`（全模块回归）→ **BUILD SUCCESS，10 模块全绿**（closure 前终验，见 closure 记录）。
- `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-<m> --severity high`（connector/batch/jdbc/debezium）→ **4×exit 0**（CN-8 后；另 6 模块复跑 exit 0 无回归）。
- `node ai-dev/tools/check-nop-stream-invariants.mjs` → exit 0。
- `node ai-dev/tools/check-doc-links.mjs --strict` → exit 0（本报告 + connector-design.md 修改后复跑）。
- `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` → exit 0（closure 时）。
- Owner-doc 裁定：connector-design.md **已做最小事实同步**（§3.1 末行：stale 限制删除 + 两格能力漂移修正 + 两条新已知限制），非 `No owner-doc update required`——本审计发现三处确认 owner-doc drift（Non-Degradable 项），就地最小修复。

## Conclusion

- **历史审计收口**：05-20 §1—§9 逐组归属核对——**无 connector 专属组**（显式记录）；06-30 seed+派生 10 项核对（8 landed/unchanged + 1 改善性漂移记录 + 1 partial 路由 item 22，无 regressed）；2300-1/2/3 Targets 逐条核对零 connector 侧修复点（预期确认）。
- **产品化审计（D-GAP 两重点）**：①候选钩子清单自包含落地（H-1..H-6 + 消费建议，Follow-up item 20 直接消费）；②XDef/字段校验覆盖现状结论表落地（8/8 连接器配置 bean 构造期校验、模型层才走 stream.xdef；broker 预检×2 + 凭据加密×1 为 item 20 核心输入）。
- **统一审计**：7 个干净维度显式核验（2PC 双 sink 同构无漂移、batch/message 对称性有据、core 重复为零、契约时序/错误传播/资源管理/并发复位全过）；8 项缺陷采信（1 high checkpoint 光标回退 + 1 medium 静默损坏 + 6 low）+ 8 组 watch-only residual（逐条 non-blocking 理由）+ 3 项读审否决；hollow scan 4×exit 0（CN-8 工具检测缺陷修正，全 10 模块无回归）；测试全量盘点 + 缺口清单 7 项（5 项本 plan 补齐 / 1 项已覆盖 / 1 项记录归 Phase S）。
- **修复与收口**：8 项小缺陷 + 1 项工具修正全部落地（14 个新 focused 用例 + fix-revert 验证 CN-1）；owner-doc 3 处 drift 最小同步 + 2 条新已知限制；无大缺陷、无 Follow-up 追加（显式）；item 22 枚举事实补全随 closure 写回；全模块回归绿 + 五工具门禁 exit 0。
- **被否决的方案**：把 BatchConsumer 构造期 setup 移入 open()（否决：SinkFunction 契约无 open，构造期 setup 即天然探测点且被测试钉定）；在 dry-run 中 run()+cancel() 探测 SourceFunction（否决：时序竞争且 Debezium 会拉起真引擎——推荐 marker 接口方案进 item 20）；删除 nextSubtaskIndex 字段（否决：破坏版本化 payload 兼容，W-2 记录）；env 级 mid-stream 恢复 E2E 本 plan 内补齐（否决：需跨 execute 生命周期的恢复基建，归 Phase S，G-7 记录）。
- **后续工作**：Follow-up item 20 消费 §2.1 钩子清单与 §2.2 覆盖表（自包含）；item 11 引用本报告 §2.2 消费路径事实（flow 侧 XDef 完备性深审的输入）；item 22 承接 connector 16 文件通配符 sweep；W-1/W-4/W-7 为 Phase S / runtime 侧决策输入（无 plan-owned 遗留工作）。

## References

- `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`（历史审计原文）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7：§1.1 §7 空壳模块结论、§2.2 Flink/Beam 裁定、hollow 工具消息语义分级先例）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md`（item 8：§1.3 2300-2 runtime 侧复核）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-cep-module-audit.md`（item 9：报告结构与 CE-6 log-and-throw 先例）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 10 两项重点）
- `ai-dev/plans/nop-stream-production/2026-08-04-2300-{1,2,3}-*.md`（connector 侧归属核对对象）
- `ai-dev/design/nop-stream/connector-design.md`（owner doc，本审计最小事实同步）
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`（XDef 校验链路证据）
- `ai-dev/plans/nop-stream-productization/2026-09-01-1457-2-connectors-module-audit.md`（执行 plan）
