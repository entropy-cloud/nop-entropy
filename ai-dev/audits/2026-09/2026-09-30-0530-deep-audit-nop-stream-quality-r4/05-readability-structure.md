# nop-stream 可读性/结构深度审计 R4（2026-09-30 05:30 轮）

> 审计范围：nop-stream 全部 10 个子模块（core/runtime/cep/flow/rocksdb/connector×3/fraud-example/quickstart），main 源 98,755 行 / 1,355 文件（含测试）。
> 方法：全仓 token 索引零引用扫描（681 个 main 顶层类 + 全部 public 常量 + 抽样 public 方法，覆盖 java/xml/xpl/xlib/xmeta/json/yaml/properties/sh/mjs，防反射误判）；20 行滑窗 n-gram 跨文件重复检测 + SequenceMatcher 块级 diff；javadoc `{@link}` 目标存在性校验；import 分组全量统计；plan 366（65af207b07）涉及文件逐点复核。
> 已知项处理：R3 已登记且 plan 366 已修复的项不重复立案；跨模块测试脚手架合并、windowing 测试家族进一步收敛、G10 残余三项 Deferred 不重复立项；plan 2278 Deferred（GraphExecutionPlan/RemoteBuilder 语义分歧）仅在核对节报告状态。
> 本轮口径说明：编号沿用任务书要求的前缀 [R5-RD-NN]；可读性发现默认 P2 上限，仅风格问题计 P3。

---

## 一、发现总览

| 编号 | 严重程度 | 一句话标题 |
|------|---------|-----------|
| R5-RD-01 | P2 | CONTROL_HEARTBEAT 死协议残留：发送端已删，接收分支/常量/javadoc/测试名四处未同步 |
| R5-RD-02 | P2 | NopStreamErrors 9 个孤儿错误码（含归档整改记录声称"已接线"的 ERR_STREAM_INIT_ERROR） |
| R5-RD-03 | P2 | API javadoc 悬空/失实：IStateBackend 声称存在 RedisStateBackend 生产实现、WatermarkStatus 引用三个不存在的类 |
| R5-RD-04 | P2 | KafkaStringWireCodec "zero logic duplication" 声明失实：fromWire+extractData 与 Pulsar 版逐字复制 |
| R5-RD-05 | P3 | 巨型文件盘点与 JobCoordinator（2355 行）剩余拆分线：R2 评估的"纳入"项仅 5/12 落地 |
| R5-RD-06 | P3 | EmbeddedDistributedExecutor ↔ RpcDistributedExecutor ~60 行三段重复（本轮新发现） |
| R5-RD-07 | P3 | Internal{Iterable,SingleValue}ProcessWindowFunction 的 Context 适配器 30 行重复（Flink 血统） |
| R5-RD-08 | P3 | fraud-example 双 Aggregate 的累加器/守卫/窗口起点恢复 ~50 行重复（参考示例是用户拷贝源） |
| R5-RD-09 | P3 | 任务身份 DTO 三胞胎扁平委托样板 ~90 行（TaskAssignment/TaskDeploymentDescriptor/TaskStatusReport） |
| R5-RD-10 | P3 | TtlCleanupStrategy 退化为行为惰性旋钮：lazyEviction 标志主代码零读取 |
| R5-RD-11 | P3 | 命名不一致：同一 0-based 并行索引在控制面两端双命名（taskIndex vs subtaskIndex） |
| R5-RD-12 | P3 | import 分组系统性偏离约定：274 文件 java.*-first vs 86 文件守约，92 文件静态导入居中 |
| R5-RD-13 | P3 | 考古注释再生产：plan 366 自身引入 "B6' (plan 366)"；Stage-NN 家族存量 273 处 main |

**分布：P0=0，P1=0，P2=4，P3=9，合计 13 项。**

---

## 二、P2 发现

### [R5-RD-01] CONTROL_HEARTBEAT 死协议残留：发送端已删，接收分支/常量/javadoc/测试名四处未同步

**文件**
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamMessageEnvelope.java:47-56`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:642-648`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/transport/TestRemoteInputChannelHeartbeat.java:33`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:129-131,185-197,278-330,393`

**证据片段**

plan 366（65af207b07）删除了 RemoteResultPartition 的心跳发送面（startHeartbeat/sendHeartbeatIfIdle，-187 行）。但协议的接收侧与文档未同步：

```java
// StreamMessageEnvelope.java:48-55（javadoc）
/**
 * Stage 43: {@link #TYPE_CONTROL} payload values. {@code END_OF_STREAM}
 * signals producer completion; {@code HEARTBEAT} is an idle-liveness signal
 * sent by {@code RemoteResultPartition} when no data has flowed for the
 * configured {@code heartbeatInterval}. ...
 */
public static final String CONTROL_END_OF_STREAM = "END_OF_STREAM";
public static final String CONTROL_HEARTBEAT = "HEARTBEAT";   // :56
```

```java
// RemoteInputChannel.java:642-647（接收分支——仓库内已无任何生产者）
if (StreamMessageEnvelope.CONTROL_HEARTBEAT.equals(payload)) {
    // Pure liveness signal — already refreshed lastReceivedTime
    // above. ...
    LOG.debug("Received heartbeat on topic={}", topic);
    return null;
}
```

验证命令与结果：
- `grep -rn "CONTROL_HEARTBEAT" --include="*.java" nop-stream`（排除 target）→ 仅 3 处：常量定义（StreamMessageEnvelope:56）、javadoc 提及、接收分支（RemoteInputChannel:642）。**零发送者、零测试构造该载荷**（`TestRemoteInputChannelHeartbeat.java` 全文不含 `CONTROL_HEARTBEAT`，其 158 行实际测的是"生产者沉默/错 epoch/数据流刷新"三种通用读超时场景，行 63 注释自证 "producer exists but never sends anything"）。

**严重程度**：P2

**现状**：plan 366 删除心跳发送端后留下完整接收臂：常量 + 接收分支 + `StreamMessageEnvelope` javadoc 对已删除机制的详细描述（"sent by RemoteResultPartition when no data has flowed for the configured heartbeatInterval"——该机制已不存在）+ RemoteInputChannel 四处 javadoc 把 heartbeat 列为可能到达的消息类型（`:129-131`、`:185-197`、`:278-330`、`:393`）。`TestRemoteInputChannelHeartbeat` 类名也已名不副实。

**风险**：读者按 javadoc 理解会以为存在 producer 心跳保活机制并据此排查/配置（如寻找 heartbeatInterval 配置项——已无此物）；死接收臂让"unknown control payload 会 WARN"的防御分支永远只在理论上可达，掩盖协议面真实形状。

**建议**：三选一（建议 a）：(a) 删除 `CONTROL_HEARTBEAT` 常量 + RemoteInputChannel 接收分支 + 修正 `StreamMessageEnvelope:47-56` javadoc 只保留 END_OF_STREAM 语义；`TestRemoteInputChannelHeartbeat` 更名为 `TestRemoteInputChannelLiveness`。(b) 若有意保留协议槽位以备未来，则必须改写 javadoc 为"当前无发送者"并在接收分支注明。误删风险低：分支不可达由本轮 grep 证实。

**信心水平**：高（全仓 token 级 grep，无遗漏路径；测试文件逐行核对）。

**误报排除**：`RemoteResultPartition.close()` 发送的 EOS 是 `TYPE_CONTROL` + `CONTROL_END_OF_STREAM`（RemoteResultPartition.java:163-165），与 HEARTBEAT 无关；`TaskManager.DEFAULT_HEARTBEAT_INTERVAL_MS`（TaskManager.java:84）是 lease/失败检测的本地调度间隔，不产生 CONTROL_HEARTBEAT 载荷，两者同名不同物，未计入。

---

### [R5-RD-02] NopStreamErrors 9 个孤儿错误码（100 个中 9%，含归档整改记录声称"已接线"的 ERR_STREAM_INIT_ERROR）

**文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/NopStreamErrors.java:76,79,82,94,120,123,132,135,138`

**证据片段**

```java
76:    ErrorCode ERR_STREAM_INIT_ERROR =                                  // 零代码引用
79:    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_NOT_INITIALIZED =         // 零代码引用
82:    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_ALREADY_STARTED =         // 零代码引用
94:    ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_SNAPSHOT_FAILED =         // 零代码引用
120:    ErrorCode ERR_STREAM_CHAINING_OUTPUT_SNAPSHOT_FAILED =            // 零代码引用
123:    ErrorCode ERR_STREAM_CHAINING_OUTPUT_RESTORE_FAILED =             // 零代码引用
132:    ErrorCode ERR_STREAM_WINDOW_AGGREGATOR_NOT_INITIALIZED =          // 零代码引用
135:    ErrorCode ERR_STREAM_WINDOW_AGGREGATOR_INVALID_STATE =            // 零代码引用
138:    ErrorCode ERR_STREAM_WINDOW_AGGREGATOR_STATE_RESTORE_FAILED =     // 零代码引用
```

验证口径：`grep -o "ERR_STREAM_[A-Z_0-9]*" NopStreamErrors.java | sort -u` 得 100 个错误码；逐个 `grep -rw "$code" --include="*.java" -r nop-stream`（排除 target 与定义文件本体）计引用。9 个为零。再放宽到全仓全文件类型复核：仅 `ai-dev/audits/*`、`ai-dev/archived/*` 文档提及（文档提及不构成代码活性）。

特别注意 `ai-dev/archived/2026-05/83-nop-stream-deep-audit-2026-05-31-remediation.md` 声称 "SubtaskTask.openOperatorChains 已改用 ERR_STREAM_INIT_ERROR 错误码 —— PASS"，但当前 `SubtaskTask.java` 无此引用——后续重构换码后未回收错误码，且历史整改记录与 live code 脱节。

**严重程度**：P2

**现状**：错误码注册表（707 行）承载模块全部错误契约；9% 的码无任何抛出点。同文件内 `ERR_STREAM_CHAINING_OUTPUT_EXCEPTION/CLOSE_FAILED/FLUSH_FAILED` 有引用，说明孤儿不是"整族未用"而是重构遗留。

**风险**：错误码表是对用户的契约面（docs-for-ai/error-handling 指导用户按码检索）；孤儿码让排障者按码搜索落空，也让表体积虚胀、新增码时复用错相近孤儿码的风险上升。ChainingOutput 快照/恢复失败实际走 `ERR_STREAM_CHAINING_OUTPUT_EXCEPTION`（ChainingOutput.java:76,92,103），语义分辨率已经丢失。

**建议**：删除 9 个孤儿码（或：若视为"预留契约"，在 define 处注明"预留未接线"并记入 owner doc）；同时勘误 `ai-dev/archived/2026-05/83-*.md` 的对应条目（加"后续已换码"注）。删除前确认错误码 XML/properties 无字符串引用（本轮已覆盖全文件类型 grep）。

**信心水平**：高（双轮 grep：模块内 java + 全仓全类型）。

**误报排除**：反射/字符串拼接构造错误码的情况——错误码常量均为编译期 static final 引用，项目内无 `ErrorCode.valueOf` 类动态查找（grep `NopStreamErrors.` 动态拼接模式零命中）；测试对码的断言（如 `instanceof StreamException` + message 检查）不引用码名本身，不构成引用。

---

### [R5-RD-03] API javadoc 悬空/失实：IStateBackend 声称存在 RedisStateBackend 生产实现、WatermarkStatus 引用三个不存在的类

**文件**
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/IStateBackend.java:17-24`
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/streamrecord/watermark/WatermarkStatus.java:30-56`

**证据片段**

```java
// IStateBackend.java:21-24
 * <p>实现可以是：
 * <ul>
 *     <li>{@link MemoryStateBackend} - 内存实现，用于测试</li>
 *     <li>{@link RedisStateBackend} - Redis 实现，用于生产环境</li>   // :23
 * </ul>
```

`grep -rwl "RedisStateBackend" --exclude-dir=target -r .` → 全仓仅 2 类命中：本文件 + `docs/dev-guide/stream/state-backend-design.md`（历史遗留文档目录）。**代码库不存在 RedisStateBackend**；实际生产后端是 `RocksDBStateBackend`（nop-stream-rocksdb）。

```java
// WatermarkStatus.java:33-36（Flink 拷贝 javadoc）
 * ... a {@link SourceStreamTask} or {@link StreamTask} emits a
 * {@link WatermarkStatus#IDLE} if it will temporarily halt ...
 *       ... i.e. a {@link StreamSource}, will not emit watermarks ...
```

`SourceStreamTask` 全仓仅在本文件出现；`StreamTask`/`StreamSource` 在 nop-stream 中无对应类（实际类为 `StreamTaskInvokable`/`StreamSourceOperator`）。三处 `{@link}` 全部悬空，且叙述的是 Flink 的 SourceStreamTask 空闲语义，非本引擎的实现（本引擎 R3 已裁定并删除了双输入 WatermarkStatus 组合死面，plan 366 落地删除 processWatermarkStatus1/2）。

**严重程度**：P2

**现状**：模块对外接口（IStateBackend 是用户实现状态后端的入口接口）的 javadoc 声称一个不存在的生产实现；WatermarkStatus 的类文档整体为 Flink 原文拷贝，锚定不存在的类。

**风险**：用户按 IStateBackend javadoc 寻找/等待 Redis 实现选型误导；悬空 {@link} 在 IDE/javadoc 构建中显示为裸文本，降低接口可信度；WatermarkStatus 文档描述的"downstream task 全输入 idle 才 idle"组合语义与当前单 head-input 路由实现（StreamTaskInvokable:1017 只路由 headInput）的真实形状有偏差。

**建议**：IStateBackend javadoc 改为列举 MemoryStateBackend / RocksDBStateBackend（并指向 nop-stream-rocksdb）；WatermarkStatus javadoc 重写为本引擎语义（source idle 由 SourceReaderOperator/StreamSourceOperator 空闲判定驱动，经 head input 路由），删除对不存在类的引用。可顺带做一次"`{@link}` 目标全仓不存在"批量扫描（本轮脚本可复用，main 范围悬空链接除上述两簇外均验证为同文件内嵌套类或跨模块类，误报已排除）。

**信心水平**：高（全仓 -w grep + 类名索引双验证）。

**误报排除**：`RegionDecomposer.java:44` 的 `{@link EdgeClassification#WITHIN_REGION}` 初看悬空，实为同文件私有 enum（:67-71），javadoc 解析合法，已排除；跨模块 `{@link IMessageService}` 等为本仓其他模块类，非悬空。

---

### [R5-RD-04] KafkaStringWireCodec "zero logic duplication" 声明失实：fromWire+extractData 与 Pulsar 版逐字复制

**文件**
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/KafkaStringWireCodec.java:30-72`（声明在 :36）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/PulsarStringWireCodec.java:40-72`

**证据片段**

```java
// KafkaStringWireCodec.java:33-36
 * <p><strong>Why a separate class instead of reusing {@link PulsarStringWireCodec}.</strong>
 * ...
 * This is a thin parallel implementation that
 * shares {@link DataPlaneWireSupport} for the actual (de)serialization logic, so there is
 * zero logic duplication — only the backend-identifying class name differs.
```

实际两文件方法体逐字相同（SequenceMatcher：26 行归一化匹配块，f1:7..33 ↔ f2:7..33；两文件各 76 行，方法体差异为零）：

```java
// KafkaStringWireCodec.java:52-72 ≡ PulsarStringWireCodec.java:52-72（逐字）
@Override
public StreamMessageEnvelope fromWire(Object message) {
    Object data = extractData(message);
    if (data == null) { return null; }
    if (data instanceof StreamMessageEnvelope) { return (StreamMessageEnvelope) data; }
    if (data instanceof String) { return JsonTool.parseBeanFromText((String) data, StreamMessageEnvelope.class); }
    if (data instanceof Map) { return (StreamMessageEnvelope) JsonTool.jsonObjectToBean(data, StreamMessageEnvelope.class); }
    return null;
}
private static Object extractData(Object message) {
    if (message instanceof ApiMessage) { return ((ApiMessage) message).getData(); }
    return message;
}
```

**严重程度**：P2（注释与代码事实相反 + 真实漂移风险；纯重复本身可 P3，但该重复被一条失实声明掩盖，符合"风格问题掩盖真实逻辑错误"的升档口径边界——本轮按注释失实计 P2）

**现状**：两个 final 类，`toWire` 一行委托 DataPlaneWireSupport（该部分"共享支撑类"声明属实）；但 `fromWire` 的 4 分支类型分发链 + `extractData` 帮助方法（合计 ~20 行逻辑）是完整复制。javadoc 的 "zero logic duplication" 只对 toWire 成立。

**风险**：wire 解码是 exactly-once 数据面的入口——未来为 fromWire 增加新 wire 形态（如字节序列化、压缩信封）或修复解码缺陷时必须双改，漏改一侧即 Kafka/Pulsar 行为分歧，且该分歧会被"声明无重复"的注释掩护。

**建议**：把 `fromWire` + `extractData` 下沉为 `DataPlaneWireSupport.fromJsonWire(Object message)`（或抽象基类 `StringWireCodec`），两个 codec 的 `fromWire` 变一行委托；javadoc 改为如实陈述"仅 toWire 由支撑类共享，fromWire 自本提交起共享"。行为保持，两文件各 -18 行。

**信心水平**：高（逐行 diff 为零差异）。

**误报排除**：`SysDaoWireCodec`（SysDaoWireCodec.java:33）是另一条 wire 通道（SysDao 消息表），其编解码走 ApiRequest/JsonTool 不同形态，不属于本重复对；未计入。

---

## 三、P3 发现

### [R5-RD-05] 巨型文件盘点与 JobCoordinator（2355 行）剩余拆分线：R2 评估的"纳入"项仅 5/12 落地

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`（全文 2355 行）及下表 16 个 >800 行 main 文件

**证据片段**

>800 行手写 main 文件（wc -l，排除 _gen/target）：

| 文件 | 行数 |
|------|------|
| runtime/coordinator/JobCoordinator | 2355 |
| runtime/operators/windowing/WindowOperator | 1962 |
| runtime/checkpoint/CheckpointCoordinator | 1567 |
| runtime/execution/GraphModelCheckpointExecutor | 1467 |
| cep/operator/CepOperator | 1350 |
| core/execution/InputGate | 1289 |
| core/execution/task/StreamTaskInvokable | 1192 |
| cep/nfa/NFA | 1153 |
| cep/nfa/compiler/NFACompiler | 1109 |
| runtime/execution/SupervisionLoop | 968 |
| rocksdb/RocksDBKeyedStateBackend | 951 |
| runtime/checkpoint/storage/CheckpointSerDe | 945 |
| runtime/checkpoint/storage/JdbcCheckpointStorage | 929 |
| runtime/taskmanager/TaskManager | 910 |
| flow/builder/StreamModelDslBuilder | 810 |
| core/execution/GraphExecutionPlan | 806 |

JobCoordinator 现有分节注释：`==================== Lifecycle :469 / Task Assignment :676 / Checkpoint :746 / Failure Detection & Recovery :1177 / Termination :1868 / Status :2005`，44 个字段，另含内部类 `CoordinatorElectionListener`（:1683）。

R2（2026-09-27）评估表与 HEAD 落地状态核对：

| R2 建议切面 | R2 判定 | HEAD 状态 |
|---|---|---|
| TaskCheckpointWiring（GraphModelCheckpointExecutor 接线） | 纳入 | ✅ 已落地（已存在并经 plan 366 收敛） |
| RescaleStateAssembler | 纳入 | ✅ 已落地（独立文件） |
| RetentionCleaner（CheckpointCoordinator） | 纳入 | ✅ 已落地（独立文件） |
| CheckpointHistory | 纳入 | ✅ 已落地（独立文件） |
| CheckpointAckSender + RunningTask 顶层化（TaskManager 1268→910） | 纳入 | ✅ 已落地（两独立文件） |
| FencingEpochManager（fencing 派生/轮换） | 纳入 | ❌ 未动（EPOCH_SCALE :115-127 + rotateFencingEpochCoreLocked :1600-1673 + deriveHaFencingEpoch :1674-1710 仍在类内） |
| RestartBudget（双预算重启策略） | 纳入 | ❌ 未动（maxRestarts/stallRestarts/stallRecoveryCooldownMs 字段+访问器+计数逻辑散布 :193-230、:2233-2278、globalRecovery 内） |
| SubtaskLivenessTracker（存活跟踪） | 纳入 | ❌ 未动（subtaskLiveness/lastProgress 相关 :191-203、reportNodeTaskLiveness :984-1013、detectFailures 内消费） |
| WindowOperator NamespaceAware 适配器 5 内部类 | 纳入 | ❌ 未动（仍在 WindowOperator 尾部） |
| WindowSpec（溢出构造器 16/15/19 参） | 纳入 | ❌ 未动 |
| CheckpointRestoreService / BarrierScheduler / IncrementalCheckpointSupport / TaskSlotTable | 纳入/否 | ❌ 未动（grep 0 命中） |
| LeaderLifecycle / TerminationFlow / getter 块 | 否 | 未动（与判定一致） |

**严重程度**：P3

**现状**：JobCoordinator 从 R2 的 2317 → 2355 行（+38，plan 366 在其中做的 deployTask 守卫/注释收敛未减行）。清晰拆分线仍在且与 R2 结论一致：checkpoint 编排段（:746-1177，~430 行：triggerCheckpoint/collectAck/receiveCheckpointAck/abortCheckpoint/周期调度/分布式 commit forwarder/abort handler/sendBarrierToAllTaskManagers）可仿 TaskCheckpointWiring 先例抽 `CheckpointOrchestration` 协作者；终止段（:1868-2005，~140 行）；Status getter/setter 块（:2005-2345，~340 行）；fencing 派生+轮换（~150 行，R2 列"纳入"）。

**风险**：全模块最大文件持续为恢复/选主/终止三重高危并发语义的宿主；R2 已标注 recoveryPending CAS 协议注释时序敏感（拆分必须整段搬移），拖延拆分会继续抬高每次修改的上下文装载量。

**建议**：按 R2 表继续执行"纳入"项，优先级：(1) FencingEpochManager（纯派生函数族，风险中）；(2) RestartBudget（纯状态机，低）；(3) SubtaskLivenessTracker（低）；(4) WindowOperator 尾部 5 个 NamespaceAware 静态适配器外移为包级文件（零风险速赢）。不重复展开 R2 的"否"项。

**信心水平**：高（行号/分节/grep 双核）。

**误报排除**：AssignmentPlanner 已是独立协作者（JobCoordinator 委派 assignment），不计入未拆项；CheckpointCoordinator 与 JobCoordinator 的编排职责边界由 getCheckpointCoordinator() 委派保持，未混计。

---

### [R5-RD-06] EmbeddedDistributedExecutor ↔ RpcDistributedExecutor ~60 行三段重复（本轮新发现）

**文件**
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/EmbeddedDistributedExecutor.java:111-160,214-217,296-365,384-`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RpcDistributedExecutor.java:143-190,335-410,478-560`

**证据片段**（归一化 n-gram：9 个 20 行匹配窗；SequenceMatcher 三大块）

块 1（28 行，f1:214 ↔ f2:343）——TaskManager 停止循环逐字：
```java
for (TaskManager tm : taskManagers) {
    try { tm.stop(); } catch (Exception e) {
        LOG.error("Failed to stop task manager {}", tm.getNodeId(), e);
    ...
```
（Embedded:355-361 一处 vs Rpc:395-408、536-545 两处；Rpc 第二处多一个 "during failure teardown" 语境词）

块 2（16 行，f1:69 ↔ f2:64）——`supportsDeploymentMode` + `getExpectedNodeIds`/`determineNodeCount` 开头逐字。

块 3（15 行，f1:175 ↔ f2:288）——assignment→invokable 装配循环（`coordinator.getTaskAssignments()` → `plan.getSortedVertexIds()` → `findAssignment(vertexAssignments, subtask.getTaskIndex())`）逐字；且 `findAssignment` 私有帮助方法本身也是双份（Embedded:363 vs Rpc:547）。

**严重程度**：P3

**现状**：两实现同实现 `IStreamExecutionDispatcher`（embedded = 进程内 TaskManager 实例，rpc = 远端 RPC），部署语义不同但节点清点/分配映射/停止清扫三个横切段完全复制。历史轮次（R1/R2/R3 报告 grep 核对）均未登记此对，plan 366 未触及。

**风险**：分布式启动/停止路径修 bug 双改（如停止顺序、异常吞噬策略）；两份 `findAssignment` 已经出现日志措辞漂移（ Rpc :489 "subtaskIndex=" vs Embedded :311 同名不同参语境），是漂移已经开始的信号。

**建议**：抽 `AbstractDistributedExecutor`（或组合 `ExecutorSupport` 静态帮助类）承载块 2/3 与 `findAssignment`；块 1 抽 `stopAllQuietly(List<TaskManager>)`。行为保持（保持各自日志措辞可通过参数传入）。

**信心水平**：高（三块均为零差异逐字匹配）。

**误报排除**：import 块相似（f1:18 ↔ f2:14）不算逻辑重复，未计入三块。

---

### [R5-RD-07] Internal{Iterable,SingleValue}ProcessWindowFunction 的 Context 适配器 30 行重复（Flink 血统）

**文件**
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/functions/InternalIterableProcessWindowFunction.java:39-72`
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/functions/InternalSingleValueProcessWindowFunction.java`（对应内部类）

**证据片段**（归一化 30 行匹配块 f1:18..48 ↔ f2:19..49）

```java
private static class ProcessWindowContextAdapter<KEY, W extends Window>
        extends ProcessWindowFunction.Context {
    private final KEY key;
    private final W window;
    private final InternalWindowFunction.InternalWindowContext internalContext;
    ... // currentProcessingTime/currentWatermark/windowState/globalState/output 五方法委托，两文件逐字
}
```

**严重程度**：P3

**现状**：该结构是 Flink 上游同名类的移植（Apache License 头保留），Flink 本身也维持此重复；但 nop-stream 侧两个内部类完全一致，抽包级共享类无跨模块影响。

**风险**：低——Context 契约若加方法（如新增时间域访问器），双改漏一。

**建议**：抽包私有顶层类 `ProcessWindowContextAdapter<KEY, W extends Window>`（同包 functions 下），两个 Internal* 类删除各自私有副本。零行为风险速赢。

**信心水平**：高。

**误报排除**：`clear()` 空实现、类头 license 块不计入重复行。

---

### [R5-RD-08] fraud-example 双 Aggregate 的累加器/守卫/窗口起点恢复 ~50 行重复（参考示例是用户拷贝源）

**文件**
- `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/AlertCountAggregate.java:33-50,53-156`
- `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/TransactionWindowAggregate.java:32-49,50-130`

**证据片段**（归一化匹配块 24 行 + 15 行）

```java
// 两文件逐字（AlertCountAggregate:37-46 ≡ TransactionWindowAggregate:36-45）
private final long windowSizeMs;
public XxxAggregate(long windowSizeMs) {
    if (windowSizeMs <= 0) {
        throw new IllegalArgumentException("windowSizeMs must be positive: " + windowSizeMs);
    }
    this.windowSizeMs = windowSizeMs;
}
public long getWindowSizeMs() { return windowSizeMs; }

// Acc 累加器字段与 getter/setter 族（24 行匹配块）：
long count;  BigDecimal totalAmount = BigDecimal.ZERO;
long minTimestamp = Long.MAX_VALUE;  String userId;   // + pattern 仅 Alert 版
```

且两文件 javadoc 互引同一条"窗口起点由 minTs floor 恢复"不变式（AlertCountAggregate.java:22-27、TransactionWindowAggregate.java:20-23），add()/getResult()/merge() 的窗口起点恢复算式重复。

**严重程度**：P3

**现状**：S1/S2 两个场景（JDBC sink vs file sink）各持一份结构相同的 Aggregate；示例模块无复用基类。示例代码是用户脚手架拷贝源（quickstart 文档引导从 fraud-example 学 keyed state/窗口惯用法），重复会作为反模板被复制。

**风险**：低（示例正确性有测试矩阵保护），主要是教学面误导：用户学到"每个聚合都手写一份 Acc 样板"。

**建议**：抽 `BaseWindowAggregate` 持 windowSizeMs 守卫 + 窗口起点恢复算式（或至少把 Acc 公共字段提为共享 `WindowAcc` 基类）；若判定示例刻意自包含（便于单文件阅读），在两文件 javadoc 显式互指"结构复制自对方，差异仅 pattern 维度"以免读者误以为必须如此。

**信心水平**：高。

**误报排除**：`AlertSummaryRow`/`TxSummaryRow` 输出行 DTO 字段不同（含 pattern/告警数 vs 交易数），不属逐字重复，未计入。

---

### [R5-RD-09] 任务身份 DTO 三胞胎扁平委托样板 ~90 行（TaskAssignment/TaskDeploymentDescriptor/TaskStatusReport）

**文件**
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/TaskAssignment.java:42-110`（16 个访问器中 10 个纯委托）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/TaskDeploymentDescriptor.java:65-160`（22 个访问器，identity 委托 15 处）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/TaskStatusReport.java`（同构）
- 共同底座：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/TaskIdentity.java`

**证据片段**（31 行归一化匹配块，f1:27..58 ↔ f2:32..63；TaskStatusReport 与 TaskDeploymentDescriptor 另有 5 窗匹配）

```java
// TaskAssignment.java:43-49（注释自证三处同构）
/**
 * Shared identity tuple. Private and NOT a bean property: the flat
 * getters/setters below delegate to it so the @DataBean/JSON flat shape is
 * unchanged (plan 2278 Phase 2, see {@link TaskIdentity}).
 */
private TaskIdentity identity = new TaskIdentity();

public String getJobId() { return identity.getJobId(); }
public void setJobId(String jobId) { identity.setJobId(jobId); }
// ... getVertexId/setVertexId/getSubtaskIndex/setSubtaskIndex/getFencingEpoch/setFencingEpoch/getAttemptNumber/setAttemptNumber 同型
```

**严重程度**：P3

**现状**：plan 2278 Phase 2 为保 `@DataBean` JSON flat shape 有意选择的"组合+扁平委托"，三个 DTO 各自 ~30 行机械样板（合计 ~90 行），每处均有说明注释。这是**已文档化的设计取舍**，本轮不判为缺陷，仅登记为可选收敛点。

**风险**：TaskIdentity 增删字段时三处同步改样板（编译器可查，漏改即序列化面分歧）。

**建议**（可选，需 owner 裁定）：验证 Nop `@DataBean` 对继承的支持后，将三 DTO 改为 `extends TaskIdentity` 扁平继承；或保持现状但在 TaskIdentity javadoc 集中列出三个宿主，形成"改一处必查三处"的显式清单。若维持现状，本条不构成整改义务。

**信心水平**：高（匹配块 + 注释自证）。

**误报排除**：RunningTask（taskmanager 包）虽也携带 jobId/vertexId/subtaskIndex，但它是运行时对象非 DTO，无双 naming 样板问题。

---

### [R5-RD-10] TtlCleanupStrategy 退化为行为惰性旋钮：lazyEviction 标志主代码零读取

**文件**
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/TtlCleanupStrategy.java`（全文 61 行）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/StateTtlConfig.java:44,48,64,102,119`

**证据片段**

```java
// TtlCleanupStrategy.java:14-21（javadoc 已归真：cleanup is lazy-only，无后台线程）
 * <p>Cleanup is lazy-only: expired entries are detected and removed on access
 * (read/write eviction; {@code lazyEviction} is {@code true} by default). ...
 * ... there is no automatic background cleanup thread ...
public final class TtlCleanupStrategy implements Serializable {
    public static final TtlCleanupStrategy DEFAULT = new TtlCleanupStrategy(true);
    private final boolean lazyEviction;
```

验证（grep -rn，排除 target）：
- `isLazyEviction` → 主代码零调用（仅 TtlCleanupStrategy.java:39 定义 + 测试 TestStateTtlConfig.java:36,57）
- `getCleanupStrategy` → 主代码零调用（仅 StateTtlConfig.java:64 定义 + 同上测试）
- `new TtlCleanupStrategy(false)` → 仅测试 TestStateTtlConfig.java:43（断言序列化 round-trip 保 false）

**严重程度**：P3

**现状**：plan 366 删除 `backgroundCleanup` 后，`TtlCleanupStrategy` 唯一的字段 `lazyEviction` 在引擎内无任何消费者：用户 `setCleanupStrategy(new TtlCleanupStrategy(false))` 会被接受、序列化进 checkpoint、round-trip 还原——但行为与 true 完全相同（清理本来就是 lazy-only）。该类现已是"单布尔外壳"。

**风险**：与项目自身的 no-silent-no-op 精神（多处注释引用 guide #24）相悖：静默无操作配置项。用户设 false 以为关闭了 lazy 清理，实际什么都没变，TTL 过期数据仍按 lazy 语义在访问时清理。

**建议**：二选一：(a) 删除 `TtlCleanupStrategy` 与 `StateTtlConfig.setCleanupStrategy/getCleanupStrategy`（同步删 TestStateTtlConfig 对应用例），javadoc 的"lazy-only"语义上移到 StateTtlConfig；(b) 若视为未来扩展槽位，在 `lazyEviction` 字段与 Builder 方法 javadoc 显式注明"当前唯一支持值 true；false 被接受但无行为差异（预留）"。倾向 (a)：plan 366 已裁定无后台清理，槽位无近期occupant。

**信心水平**：高（调用图靠两个 getter，全仓 grep 已覆盖）。

**误报排除**：`RocksDBKeyedStateBackend.cleanupExpiredEntries()`（TtlCleanupStrategy javadoc :18 提及）是显式按需清扫，读的是 `TtlContext.expiredKeys()`（RocksDBKeyedStateBackend.java:716），不读 lazyEviction 标志——确认该标志无间接消费者。

---

### [R5-RD-11] 命名不一致：同一 0-based 并行索引在控制面两端双命名（taskIndex vs subtaskIndex）

**文件**
- 计划/执行侧：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/Subtask.java:32,45,55,76`（`taskIndex`，javadoc "the parallel subtask index (0-based)"）
- 注册/分配侧：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/TaskIdentity.java`、`TaskAssignment.java:70-79`（`subtaskIndex`）、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/RunningTask.java:38`（`subtaskIndex`）
- 转换点：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/EmbeddedDistributedExecutor.java:307-324`、`RpcDistributedExecutor.java:485-497`、`coordinator/AssignmentPlanner.java:137-194`

**证据片段**

```java
// EmbeddedDistributedExecutor.java:307-311 —— 同一语句混用两命名指同一数值
TaskAssignment ta = findAssignment(vertexAssignments, subtask.getTaskIndex());
... " subtaskIndex=" + subtask.getTaskIndex()      // 日志键叫 subtaskIndex，取值叫 taskIndex
// :322
targetTm.installInvokable(jobId, vertexId, subtask.getTaskIndex(), subtask.getInvokable());
// 而 installInvokable 入参链（TaskManager/RunningTask）内部字段名为 subtaskIndex（RunningTask.java:38,75）
```

**严重程度**：P3

**现状**：`taskIndex`（Subtask/TaskLocation.getTaskIndex/GraphModelCheckpointExecutor/ ERR_STREAM_CHECKPOINT_EXECUTOR_JOB_GRAPH_INVALID 的 ARG_TASK_INDEX）与 `subtaskIndex`（TaskIdentity/TaskAssignment/RunningTask/AssignmentPlanner/日志）指同一概念"顶点内 0-based 并行序号"，在 assignment 匹配与部署fan-out每一步互相转换。另注：`TaskLocation` 用 `taskIndex` 而 `RunningTask:79` 构造它时传 `subtaskIndex` 变量——同一值第三次换名。

**风险**：跨 API 面阅读需人工维护"两词同义"映射；错误码参数名（ARG_TASK_INDEX）与注册表键（"{vertexId}/{subtaskIndex}"，AssignmentPlanner.java:155）不一致，按参数名搜代码漏一半。

**建议**：统一为 `subtaskIndex`（注册表/数据库/日志已多数用此名，改动面更小）或 `taskIndex`（core API 侧），任一方向均为机械重命名；最小成本方案：TaskLocation/Subtask javadoc 显式声明 "taskIndex == registry 的 subtaskIndex"，并在 AssignmentPlanner 转换点加一行注释锚。完整统一可入 backlog。

**信心水平**：高（转换点三处逐一核对，无第三命名混入；attemptId/attemptNumber 是 id/序数两个概念，不属此家族）。

**误报排除**：`shard`（state/shard 包，keyed state 分片）与 `partition`（数据面 ResultPartitionType/Partitioner）是两个正交概念，虽有各自历史包袱（StateShard 同时携带 stateShardId 与 maxParallelism 两代模型字段，StateShard.java:34-49 已注释演进关系），不判为混用；epoch（fencing long）/recoveryGen（同 leader 恢复代）/LeaderEpoch（nop-cluster 选主代）三者在 JobCoordinator:104-160 有统一编码文档（EPOCH_SCALE 复合派生），属有意设计非双命名。

---

### [R5-RD-12] import 分组系统性偏离约定：274 文件 java.*-first vs 86 文件守约，92 文件静态导入居中

**文件**（样例，全量清单见统计口径）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:12-43`（旗舰文件最典型）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroup.java:15-25`（同文件三种顺序混排）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:130,198,412`（FQN 内联补充例）

**证据片段**

```java
// JobCoordinator.java:12-43：java.* 在最前 + 静态导入夹在 io.nop 块中间（双重偏离）
import io.nop.api.core.time.CoreMetrics;
import java.util.ArrayList;          // java.* 第二
...
import org.slf4j.Logger;             // 第三方在 java.* 之后
...
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;      // :41-42 静态导入
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import io.nop.stream.core.execution.plan.DeploymentAssignment;               // :43 普通导入又在静态之后
```

```java
// KeyGroup.java:15-25：io.nop 普通 → 静态 → java.* → 空行 → io.nop 普通，四段混排
import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import java.io.Serializable;
import java.util.Objects;

import io.nop.api.core.annotations.core.Internal;
```

统计口径（python 全量扫描 nop-stream `src/main`，排除 target/_gen；violation 判据：静态导入后出现普通导入 = 静态居中；`import java.*` 的位置先于首个非 java 非静态导入 = java-first）：
- 静态导入居中：**92 个 main 文件**（core 46 / runtime 23 / cep 13 / flow 8 / rocksdb 2）+ 2 test
- java.*-first：**274 个 main 文件**；java-last 守约（AGENTS.md 约定）：**86 个 main 文件**
- 手写 FQN 内联（`java.util.Map.Entry` 等，非生成文件）：~75 处（CheckpointSerDe 11、FileSource 7、StreamMaintenanceMain 5、RemoteInputChannel 4、StreamOpsHttpServer 4、SourceReaderOperator 4、GraphModelCheckpointExecutor 3 等）；WindowOperator:130 还有 `org.slf4j.Logger` FQN 内联、:198 `io.micrometer.core.instrument.Counter` FQN 内联
- System.out/err：33 处，全部位于 CLI main（StreamMaintenanceMain/StreamConfValidateCommand/TaskManagerMain/JobCoordinatorMain 的 usage/错误输出）、demo（FraudDetectionDemo）、benchmark 测试——**业务库零污染**（此项为干净核对，不立案）

**严重程度**：P3

**现状**：AGENTS.md 约定 io.nop.* → jakarta/third-party → java.*、静态最后；模块内约 3/4 main 文件实际是 java-first 顺序，且两种顺序长期并存。plan 366 全量重写的 TaskCheckpointWiring.java（12-50）守约（普通导入在前、静态殿后），证明新代码按约定执行，偏离是存量。

**风险**：无功能风险；影响 grep-based 导入审查与静态检查工具的可用性（两种顺序并存使任何一条 import-order 规则都会大面积误报）。

**建议**：先裁定后执行——(a) 若维持 AGENTS.md 约定：引入 spotless/importorder 或 google-java-format 自定义顺序对 nop-stream 一次性格式化（机械、行为保持，建议独立 PR 与本轮其余发现分开落地）；(b) 若接受 java-first 为既成事实：修订 AGENTS.md 注明 nop-stream 历史顺序豁免，避免后续审计反复报告同一条。FQN 内联建议随手清理（加 import 即可）。

**信心水平**：高（脚本口径明确，主/测试分开计数）。

**误报排除**：仅 import java.* 单独存在（无其他组）的文件不计入 java-first；_gen 目录 42+ 文件（StreamModel 等生成模型）已排除。

---

### [R5-RD-13] 考古注释再生产：plan 366 自身引入 "B6' (plan 366)"；Stage-NN 家族存量 273 处 main

**文件**
- `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSource.java:178-181`（新引入例）
- Stage-NN 家族分布：117 个 main 文件 273 处 + 测试 173 处（top：CheckpointConfig、TaskEpochSnapshot、FileSource/FileSplit*、EpochManifest、StreamMessageEnvelope:20 等）

**证据片段**

```java
// FileSource.java:178-181 —— plan 366 整改提交自身引入的编号注释
// B6' (plan 366): the directory path is the newline-delimited first
// section, so only line terminators can corrupt it — unlike split
// paths, '|' and ',' are legal here and must NOT be rejected.
```

R2 曾裁定"注释考古债再生产"为 P2（37 处 plan 358/359/360 编号注释），plan 366 commit message 声称 "plan-01 编号注释 16 处归真"（本轮验证：`grep "plan 01|plan-01" main` = 0 ✅），但同一提交在 FileSource 又落下 `B6' (plan 366)` 新编号（B6 是 R3 审计的缺陷编号）。plan 358-360/2277-2279 家族现存 21 处 main（R2 登记 37 处，部分清理）。

**严重程度**：P3（R2 曾按再生产判 P2；本轮存量主体 Stage-NN 是长期家族且 R1 已大口径登记过 307 处窄口径，plan 366 清理后净存 273，按"已登记家族的残余+1 处新再生产"计 P3 并点名新例）

**现状**：整改提交的注释写法仍是"缺陷编号 + 计划号"考古体（B6' 指向 R3 报告的 B6 发现），未来读者需考古 R3 报告才能理解。Stage-NN（31/34/35/39/40/42/43/44/45/49 + "successor N"）是更早的里程碑编号家族，R1 登记、plan 366 未纳入清理范围。

**风险**：与 plan 366 已完成的"编号注释归真"方向相反的增量；每个编号都把代码可读性抵押给一份审计文档的存在。

**建议**：(1) FileSource:178 立即去编号，改写为现状语义（"directory path 是换行分节的首节，仅换行符可破坏它……"——信息已完整，删前缀即可）；(2) Stage-NN 家族立项一次性归真（273 处，建议按 R1 的 top 文件清单批量处理，语义保留、编号剥离）；(3) 在 plan 模板/整改 checklist 加一条"新增注释禁止携带计划/缺陷编号"，阻断再生产循环。

**信心水平**：高（grep 计数 + 新例逐行核对）。

**误报排除**："Stage 49 Phase 3" 等出现在类 javadoc 首行属于同一家族一并计入；普通 "phase/stage" 英文词（如 "three main steps"）不计；测试 173 处单列不与 main 混算。

---

## 四、已知项核对（不立案，仅报告 HEAD 状态）

| 已知项 | 来源 | HEAD 状态 |
|---|---|---|
| 零引用 public 顶层类 | R3 P1-2（10 类已删） | ✅ **清零**。验证：全仓 token 索引（681 main 类 × java/xml/xpl/xlib/xmeta/json/yaml/properties/sh/mjs），真零引用 = 0（唯一命中 `package-info` 为扫描口径假象）；"仅文档引用"类 = 0。plan 366 删类后未暴露新孤儿类 |
| 零引用 public 常量 | R3 死常量清单 | ✅ 清零。全量 public static final 常量扫描（UPPER_SNAKE ≥4 字符）0 命中；`DEFAULT_COMMIT_RETRIES`/`CONSECUTIVE_FAILURE_THRESHOLD`/`DEFAULT_LEASE_EXPIRE_THRESHOLD_MS` grep=0 确认已删 |
| 单方法死 API 清单 | R3（asLatencyMarker/getEvictor/sideOutputLateData/JobCoordinator 死访问器×6 等） | ✅ 全部消失（grep=0）；`nextProcessingTimeTimer` 仅剩同名字段内部使用（HeapInternalTimerService.java:62,100-101,222,227），方法/字段双体混淆已消除 |
| plan 366 结构整改点复核 | commit 65af207b07 | ✅ TaskCheckpointWiring 缩进修复（全文 4 空格）；wire/unwire instanceof 镜像已收敛为 `visitCheckpointRegistrations`（:219-246，BiConsumer 注入动作）；MemoryKeyedStateBackend 缓存注释已合并（:425-444 单份+指针）；MaxParallelismReshardMigration 已拆四阶段（buildGlobalKeyedPools:212/recordKeyCounts:246/redistributePools:265/assembleNewSubtaskSnapshots:294，560 行）；LATE_ELEMENTS_DROPPED 已接线（CepOperator:344-345,803 / WindowOperator:412-413,678，注册+递增+2 个新测试）；TtlContext/TtlCleanupStrategy javadoc 已归真 |
| RemoteTaskDeploySupport.provisionStateBackends 镜像分歧 | R3"重复（新近引入）" | ⚠️ **仍在**：RemoteTaskDeploySupport.java:171-181 恒用 `new MemoryStateBackend()`，TaskCheckpointWiring.java:166-176 先取 `checkpointConfig.getStateBackend()`——行为分歧未裁决（javadoc 已不再称 mirror，但分歧本身未收敛）。建议 owner 按分布式语义裁决后统一 |
| GraphExecutionPlan/RemoteBuilder 语义分歧 | plan 2278 Deferred | ⚠️ 仍在（登记的 Deferred）：RemoteGraphExecutionPlanBuilder.java:388/422/457 三处 DELIBERATE DIVERGENCE 注释完整，未统一（resolveParallelism 忽略 parallelismLocked / resolvePartitionPolicy 不 fail-fast / topologicalSort 循环图返回部分序） |
| TestAwait 跨模块合并 | Deferred | 不变：5 模块 testsupport 各一份，md5 各异（connector/batch/runtime/debezium/core），跨模块合并维持 Deferred |
| windowing 测试家族进一步收敛 | Deferred | 未复查细节（Deferred 项），本轮新 metric 测试（TestCepOperatorLateRecordsDroppedMetric vs TestWindowOperatorLateRecordsDroppedMetric）相似度 0.203，非互抄 ✅ |
| Stage-NN 考古注释 | R1 大口径登记 | 净存 main 273 处/117 文件 + test 173 处（R1 窄口径 307 → plan 系列清理后 273）；本轮按"新再生产 1 处"立案为 R5-RD-13，家族批量归真待立项 |
| quickstart/fraud-example API 同步 | 本轮检查点 8 | ✅ 同步。quickstart 模板 API 签名逐一与 HEAD 核对通过（WatermarkStrategy.forBoundedOutOfOrderness:231、TumblingEventTimeWindows.of:101、RuntimeContext.getKeyedStateStore:29、PatternProcessFunction:44 均存在且签名一致）；fraud-example 四 Pattern 用当前 `Pattern.begin/where/next/within` API；DemoKeyedStateStore（fraud/state/DemoKeyedStateStore.java:22-38）有诚实的 demo-only 文档，不误处方；DirectoryFileSourceFunction javadoc 显式声明与 FileSource 的语义对应关系（刻意双形态，非失联） |
| beans.xml/_module/SPI/i18n/xdef 死配置面 | 本轮检查点 4 | ✅ 干净。7 个 beans.xml 的 13 个 bean 类全部有引用（含 SPI/反射入口，逐个 -w grep ≥5 文件）；runtime `_module` 为空标记文件（Nop 约定）；2 个 META-INF/services SPI 实现类存在且被测试消费；nop-stream 无 i18n 资源面（错误消息走 ErrorCode 内联英文，符合规范）；stream.xdef（nop-xdefs）与 fraud/quickstart/测试 .stream.xml 活跃互引 |
| System.out/System.err | 本轮检查点 7 | ✅ 33 处全部位于 CLI main usage/错误输出、demo 打印、benchmark 测试；业务库路径零污染（无需整改） |
| JobCoordinator 2355 行拆分线 | 本轮检查点 3 | 见 R5-RD-05（R2 纳入项 5/12 落地，剩余拆分线明确） |

---

## 五、环境备注（非 nop-stream 发现，建议顺手处理）

仓库根存在 9 个 `_tmp-*.log` 散落文件（`_tmp-audit-n41-code.log` 达 317MB，2026-09-28；`_tmp-n12-regression2.log` 38MB 等），未入 `.gitignore`、未放在项目规范要求的 `<root>/_tmp/` 目录内（AGENTS.md 临时文件规范）。系此前审计/回归轮次的输出残留，建议删除或移入 `_tmp/` 并补充 gitignore。（本轮自身的扫描脚本与中间产物均按规范写入 `_tmp/`。）

---

## 六、审计方法附录（复核指引）

- 零引用类：`python` token 索引（word_re `r'[A-Za-z_][A-Za-z0-9_]*'`），class 集来自 `find nop-stream -path "*/src/main/*" -name "*.java"` 的 681 个唯一类名；def 判据 `(class|interface|enum|record)\s+NAME`；use 判据为全仓 11 类扩展名文件 token 命中，排除定义文件本体。中间产物：`_tmp/r5-classes-uniq.txt`、`_tmp/zref-fast.txt`。
- 零引用常量：`public static final ... [A-Z][A-Z0-9_]{3,}` 提取，同 token 索引比对。
- 错误码：`grep -o "ERR_STREAM_[A-Z_0-9]*"` 提取 100 个，逐个 `grep -rw`（java 范围 + 全仓复核两轮）。
- 重复检测：20 行归一化滑窗（strip/去注释/去 import/@Override）md5 索引 + `difflib.SequenceMatcher` 块级核对（autojunk=False），main 范围。
- javadoc 悬空链接：`{@link X[#m]}` 提取 × 全仓类名索引，人工复核每个命中（区分同文件内嵌套类/跨模块类/JDK 类）。
- import 统计：python 顺序扫描，main/test 分列，口径见 R5-RD-12。
