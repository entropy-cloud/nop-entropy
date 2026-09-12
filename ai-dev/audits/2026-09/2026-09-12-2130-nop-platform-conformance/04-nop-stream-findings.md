# 04 · nop-stream 深审发现

> 范围：`nop-stream/` 9 个子模块（core/cep/runtime/connector/connector-batch/connector-debezium/flow 等），651 个 main Java 文件（排除 src/test、_gen、target、quickstart、*-example）。
> 引擎内部代码允许部分底层写法，但工具约定、异常策略、DSL 优先、能力复用、防过程式大方法等规则仍然适用。规则编号 R1-R16 见 `01-checklist-and-baseline.md`。✅verified 条目已经主审计源码复核。

## A. 做对的部分

1. **DSL 优先（高符合，R2）**：nop-stream-flow 基于 `stream.xdef` 元模型生成全套模型类，`StreamModelDslBuilder` 桥接且未标 `@Deprecated`；CEP 独立模式 DSL；提交前校验直接走 XDef 字段级校验；支持 Delta 定制。
2. **连接器注册（高符合，R2）**：`StreamConnectorRegistry` 聚合 `IStreamConnectorFactory` bean，beans.xml 显式装配，重复注册 fail-fast typed 错误，无 if-else 链。
3. **模块异常骨架正确（R13）**：`NopStreamErrors` 约 80 个 typed ErrorCode，错误消息全英文（1 处例外）。
4. **健康状态机声明式（R12）**：`JobHealthStateMachine` 转换表 + 非法迁移 fail-fast。
5. **平台复用（R3）**：凭据走 `ICredentialProvider`；消息走 `IMessageService`；JDBC 用 `IJdbcTemplate`；batch 连接器复用 nop-batch-core。
6. **IoC 全绿**：`@Inject private` 0、Spring `@Value` 0；构造器注入 + beans.xml 注册。`new ObjectMapper` 0、Commons 0（cep SharedBuffer 1 处见 P3）。

## B. 发现清单

**ST-1 ✅verified · 裸时间 API 大面积绕过 CoreMetrics（95 处 / 31 文件）——P1（R14）**
`JdbcClusterRegistry.java:61/108`（租约/心跳时间戳 `System.currentTimeMillis()`）、`StreamTaskInvokable.java:903`（`System.nanoTime()` 算子计时）、`InputGate.java:510`、`StreamSourceOperator.java:253`——连模块自造的 `util.clock.Clock` 抽象也被绕过，CoreMetrics 全模块仅 1 处使用。
后果：`IClock/TestClock` 注入对引擎失效，背压计时、租约超时、checkpoint 时长等断言不可测/时钟漂移。
修复：统一 `CoreMetrics.currentTimeMillis()/nanoTime()`；删除与平台平行的自造 Clock；全仓同类问题见 02 §F-A。

**ST-2 ✅verified · 裸异常绕过模块 ErrorCode（293 处 throw）——P1（R13）**
IAE 147 + ISE 111 + UOE 35；集中：`OpsJobManager`(11)、`ConnectorCapabilityDescriptor`(9，registry 侧已有专用 ErrorCode 却在 descriptor 校验里不用)、`KeyGroupAssignment`(9)、`StreamStateResetTool`(6)。
加重因素：`StreamOpsHttpServer.java:338` 靠 `e instanceof IllegalStateException` + 消息嗅探区分 409 重复提交——typed ErrorCode 可消除字符串嗅探。
修复：统一 `throw new StreamException(NopStreamErrors.ERR_...).param(...)`。

**ST-3 · 超长方法 24 个（>120 行 9 个），编排/构建/序列化未拆步——P1（R10）**
Top5：`CheckpointPlanBuilder.build` 173 行（4 层嵌套循环）、`RpcDistributedExecutor.startJob` 172 行（TM 启动+RPC 构建+装配+首 assignment 一体）、`EmbeddedDistributedExecutor.execute` 157 行、`WindowOperator.open` 149 行、`CheckpointSerDe.deserializeEpochManifest` 148 行（手写逐字段反序列化）。
裁量：数据面算子 open 容忍度高；plan builder / executor 编排 / SerDe 应拆 Builder Step 或表驱动。

**ST-4 · 手写重试循环——P2（R3）**
`WebhookAlertChannel.java:139` 显式 for 重试 + 固定 sleep；`CheckpointCoordinator.java:910` 自维护 `failedCommitParticipants` 跨 epoch 手工重试。owner doc 明载固定 200ms 退避属有意决策——属"文档化的能力复用缺口"。修复：webhook 投递换 nop-retry 策略。

**ST-5 · 手写 JSON 拼接——P2（R14）**
`WebhookAlertChannel.java:133` `"{\"jobId\":" + json(...) + ...`，`severity` 直接内插转义不全。修复：`@DataBean` AlertPayload + `JsonTool.stringify`。

**ST-6 · 调度/轮询自造（6 文件 10+ 处 ScheduledExecutor + 5 处 Thread.sleep 轮询）——P2（R3）**
引擎底座（barrier/心跳/checkpoint 超时）属引擎内部边界可辩护；**ops 面**（治理扫描 `OpsJobManager:316`、metrics 周期 sink、webhook 投递线程）应走平台调度约定；completion 等待改 CompletableFuture/回调。

**ST-7 · 直连 java.nio/java.io（15 文件）——P2（R14）**
LocalFile checkpoint / RocksDB SST / 本地 file source 属"需求明确要求本地文件"边界（类名即契约，判豁免）；但 `FileSourceReader:163-168`、`FileTwoPhaseCommitSink` 宜包一层 `IResource`（`StreamConfValidateCommand:115` 已示范 VFS 优先 + 本地回落的正确形态）。

**ST-8 · 巨型协调器类——P2（R10 结构信号）**
`JobCoordinator` 2534 行、`WindowOperator` 2331 行、`GraphModelCheckpointExecutor` 2008 行、`InputGate` 1153 行——JobCoordinator 混装 assignment/checkpoint/健康/RPC/恢复/事件。修复：按已有 `CheckpointCoordinator/JobHealthStateMachine` 方向继续抽离。

**ST-9 · `StreamMaintenanceMain.java:64` printStackTrace——P3**
**ST-10 · 中文嵌入错误消息——P3**：`GraphModelCheckpointExecutor.java:1163` 消息体内引用中文设计文档节名。
**ST-11 · `SharedBuffer.java` 使用 Commons/Guava import——P3（R14）**（S05 证据）
**ST-12 · `StreamRuntimeException` 等 extends 关系核实——P3**：`StreamRuntimeException` 实际 extends NopException（S08 初扫为字面误报）；`TaskProcessingTimeService` 有 extends 裸异常（见 evidence/S08）。
**ST-13 · AssertionError 44 处——P3**：内部不变量断言可接受，但不应出现在可恢复路径。

## C. 统计

| 指标 | 数值 |
|---|---|
| 超长方法 >80 行 | 24（>120 行 9 个） |
| 裸时间 API | 95 处 / 31 文件（CoreMetrics 仅 1 文件） |
| 裸异常 throw | 293（IAE 147 / ISE 111 / UOE 35）+ AssertionError 44 |
| 手写重试 / 手写 JSON / 自建调度线程 | 2 / 1 / 6 文件 10+ 处；Thread.sleep 轮询 5 处 |
| 直连 nio/io | 15 文件（多数边界豁免） |
| `new ObjectMapper`/Commons/`getBytes()`无字符集/`@Inject private`/`@Value` | 0 / 1 / 0 / 0 / 0 |

## D. 小结

**总体合规率 ~75-80%**：架构性约定（DSL 优先、SPI+beans.xml、typed ErrorCode、声明式状态机、credential/message/dao 复用、IoC 规范）落实度高，明显优于平均水平；失分集中在工程细粒度约定。修复顺序建议：ST-1 时间 API → ST-2 异常 → ST-3 拆分（先 executor/plan-builder，后算子 open）。
