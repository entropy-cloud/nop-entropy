# nop-stream 连接器目录与能力矩阵

> 定位：nop-stream 现有连接器族的**逐连接器能力矩阵**（方向 / 交付语义 / 并行度 / 恢复语义）+ **SPI 注册中心类型名清单**（item 19 / P-REQ-28）。
> 使用指引（语义组合规则、XDSL/Java 接线）见 `03-modules/nop-stream-user-guide.md`「连接器使用指引」；CDC 专项操作手册见 `03-modules/nop-stream-cdc-cookbook.md`。
> 本表每行语义标注均与 live 代码核对一致（核对锚点 = 行为级测试，2026-09-03 全量复核；SPI 能力声明的代码级单一事实源 = `nop-stream-runtime` 测试 `TestStreamConnectorRegistryDiscovery`，2026-09-03 起）。

## 模块清单

| 模块 | 组件 |
|---|---|
| `nop-stream-connector` | `FileSource`、`FileSourceReader`、`FileTwoPhaseCommitSink`、`MessageSource`（FLIP-27，工厂面）、`MessageSourceFunction`（legacy 程序化 API）、`MessageSinkFunction` |
| `nop-stream-connector-jdbc` | `JdbcTwoPhaseCommitSink`（+ `JdbcTwoPhaseCommitSinkBuilder`） |
| `nop-stream-connector-debezium` | `DebeziumCdcSource`（FLIP-27，工厂面）、`DebeziumCdcSourceFunction`（legacy 程序化 API/引擎宿主） |
| `nop-stream-connector-batch` | `BatchLoaderSource`（FLIP-27，工厂面）、`BatchLoaderSourceFunction`（legacy 程序化 API）、`BatchConsumerSinkFunction`（nop-batch 桥接） |
| `nop-stream-connector-kafka` | `KafkaSourceFunction`、`KafkaTwoPhaseCommitSink`（原生 Kafka 连接器，见「Kafka 原生连接器」节） |
| `nop-stream-format-avro` | `AvroSchemaRegistryClient`、`AvroWireFormat`（跨连接器 payload 格式层，见「格式层」节） |
| `nop-stream-rocksdb` | `RocksDBStateBackend`/`RocksDBKeyedStateBackend`/`RocksDBSnapshotSerDe`（状态后端）、`RocksDBMetricsRecorder`（metrics gauge，见「RocksDB 状态后端快照包络」节） |
| `nop-stream-cep` | `CepOperator`（CEP 算子，NFA 匹配引擎，见「CEP 并行 subtask 契约」节） |

## SPI 注册中心与类型名

连接器端点组件经 **SPI 工厂**注册（`nop-stream-core` `io.nop.stream.core.connector.registry` 包，item 19 / P-REQ-28）。类型名命名空间为**方向作用域**——`(方向, 类型名)` 唯一；本期 10 个内建工厂**均无别名**。注册载体 = 各连接器模块 `_vfs/nop/stream/beans/connector-*.beans.xml` 中的无状态工厂 bean（NopIoC 承载，仅显式装配时加载，不进全局 app 容器）。

| 方向 | 类型名 | 端点组件 | 工厂类（代码锚点） | beans.xml |
|---|---|---|---|---|
| source（FLIP-27 split） | `file` | `FileSource` | `io.nop.stream.connector.file.FileSourceConnectorFactory` | `connector-file.beans.xml` |
| source | `message` | `MessageSource`（FLIP-27 split，WI2 plan 2343；legacy `MessageSourceFunction` 保留程序化 API 不再由工厂生产） | `io.nop.stream.connector.MessageSourceConnectorFactory` | `connector-message.beans.xml` |
| source | `debezium-cdc` | `DebeziumCdcSource`（FLIP-27 split，WI2 plan 2343；表=split 承接 B 层路由，legacy `DebeziumCdcSourceFunction` 保留程序化 API 不再由工厂生产） | `io.nop.stream.connector.debezium.DebeziumCdcSourceConnectorFactory` | `connector-debezium.beans.xml` |
| source | `batch-loader` | `BatchLoaderSource`（FLIP-27 split，WI2 plan 2343；legacy `BatchLoaderSourceFunction` 保留程序化 API 不再由工厂生产） | `io.nop.stream.connector.batch.BatchLoaderSourceConnectorFactory` | `connector-batch.beans.xml` |
| sink | `file` | `FileTwoPhaseCommitSink` | `io.nop.stream.connector.file.FileTwoPhaseCommitSinkConnectorFactory` | `connector-file.beans.xml` |
| sink | `message` | `MessageSinkFunction` | `io.nop.stream.connector.MessageSinkConnectorFactory` | `connector-message.beans.xml` |
| sink | `jdbc-2pc` | `JdbcTwoPhaseCommitSink` | `io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSinkConnectorFactory` | `connector-jdbc.beans.xml` |
| sink | `batch-consumer` | `BatchConsumerSinkFunction` | `io.nop.stream.connector.batch.BatchConsumerSinkConnectorFactory` | `connector-batch.beans.xml` |
| source | `kafka` | `KafkaSourceFunction` | `io.nop.stream.connector.kafka.KafkaSourceConnectorFactory` | `connector-kafka.beans.xml` |
| sink | `kafka-2pc` | `KafkaTwoPhaseCommitSink` | `io.nop.stream.connector.kafka.KafkaTwoPhaseCommitSinkConnectorFactory` | `connector-kafka.beans.xml`（透传语义 sink：不实现 `CdcChangeAwareSink`——WI7 起，携带非 INSERT 变更元数据的记录在本 sink typed fail-fast；如需 CDC 落 kafka 请在管道内先展开 value） |

**限速与倾斜感知分配（WI5，2026-10-07，plan 2330）**：`file` source 参数面新增 `rateLimitRowsPerSecond` / `rateLimitBytesPerSecond`（INT optional，缺省 = 0 = 不限速；显式 <= 0 工厂期 typed 拒绝——descriptor INT kind 只管可解析性，正性是构造契约）与 `splitAssignmentPolicy`（`round-robin` 默认 / `size-aware`）。限速经 core `SourceRateLimiter`（token bucket，起始空桶，monitor 外有界分片睡眠响应中断）在 reader 发射路径消费——限速即背压（poll 阻塞），**代价 = checkpoint/cancel 时延随速率退化**（能力边界，接受）。size-aware 语义裁定（plan 2330 F1）：分配函数 = f(剩余未分配集, policy, parallelism) 纯函数，已分配 split 不归因负载（State 无归属字段，零 wire 变更）——投递结果随事件序列变化；倾斜场景下大文件归先注册 reader、剩余集在后续事件间均衡。单写入者/无速率契约的 source（batch-loader/message）描述符**无线速参数**：经校验面配置 = unknown-param typed 错误（程序化构造面不校验未知参数 = WI4 既有边界，如实声明）；Kafka source 限速已由 WI3 落地（plan 2335 消费 core `SourceRateLimiter`：`rateLimitRowsPerSecond`/`rateLimitBytesPerSecond` poll 批节流，负值 typed 拒绝，限速即背压，随分片拷贝携带——兑现 plan 2330 的 deferred 承接）。

**成熟度分级（WI4，2026-10-07，plan 2329）**：每个描述符携带 `maturity`（`PRODUCTION` / `EXPERIMENTAL`，默认 EXPERIMENTAL——PRODUCTION 须工厂显式声明）。分级判据 = **语义强度**：交付/恢复语义由平台引擎内建兑现（checkpoint 光标 / offset 进 checkpoint / 两阶段提交）= PRODUCTION；recovery=NONE 或语义由外部后端承载 = EXPERIMENTAL。10 内建端点分级：file source/sink、jdbc-2pc、debezium-cdc、batch-loader、batch-consumer、kafka source = PRODUCTION；message source/sink = EXPERIMENTAL（at-least-once 由后端承载，Local 后端 best-effort）；**kafka-2pc sink = EXPERIMENTAL（WI3/plan 2335 豁免说明：commit-at-barrier 事务携带一个文档化残余重复窗口 [barrier commitTransaction, checkpoint durable)——保证尚未完全引擎内建，不满足判据的全称；窗口关闭或被接受后按判据晋级 PRODUCTION；配对的 kafka source 无此窗口，offset 进 checkpoint 完全引擎内建 = PRODUCTION）**。分级展示于 `StreamConnectorCatalog.renderListing`（行尾 `maturity=` 列），逐项钉定于 `TestStreamConnectorRegistryDiscovery.testMaturityGradesMatchSemanticStrengthCriterion`；conf-validate connector 校验模式对 EXPERIMENTAL 端点出非阻断 warning。参数声明支持值级规则（`sensitive` / `allowedValues` / `pattern`，规则缺省 = 显式不校验），由 connector 校验模式消费，sensitive 值在任何报告/异常文本中脱敏。

**数据采样（WI4，`sample` 命令）**：`SampleableSource` 契约 + `StreamDataSampler` 驱动，经 `StreamMaintenanceMain sample type=<typeName> param=value... [limit=<n>]` 使用（connector 校验模式，registry 解析工厂；classpath 上有哪些连接器模块就发现哪些）。副作用预算与探测红线同族：只读扫描——不分配 split（file 延续 H-1）、不创建订阅（message）、不拉嵌入引擎（debezium）、不消费批数据（batch-loader H-3）、sink 全族不采样。封闭分派：`sample` 仅接受 SOURCE 类型——SINK 类型名在 connector 校验模式即 typed direction-mismatch FAIL（exit 1，不进采样分派）；SOURCE 族中实现 `SampleableSource` 的端点返回真实记录（当前内建 = file source，`String` 文本行，limit 为全局上限、无跨文件排序承诺），未实现的三个内建 source（message/debezium-cdc/batch-loader）显式 SKIP（`nop.err.stream.sample-not-supported`），绝不以空列表冒充成功。CLI 运行需 classpath 含 nop-ioc（容器反射装配，缺失时 typed 报错指引）与目标连接器模块 jar。

**注册资格集**：仅端点组件（上表 8 个）。`FileSourceReader`（由 `FileSource.createReader()` 内部构造）、`JdbcTwoPhaseCommitSinkBuilder`（fluent builder，产物已注册）等非端点组件显式不注册（不注册理由：非独立实例化端点 / 非端点辅助物）。

**维护规则（R5-CON-01 防复发）**：描述符能力值（consistency/parallelism/recoverySemantic）翻转的提交，必须在同一提交内同步更新能力矩阵钉定测试 `TestStreamConnectorRegistryDiscovery.testCapabilityMatrixAlignedWithDesignAdjudication`，并核对其验证面覆盖该描述符的宿主模块（runtime 测试依赖 connector 模块，仅跑 core+connector 不够）。钉定测试对齐的是最新用户裁定，不是历史矩阵快照。

**单一事实源**：能力描述符的交付语义声明必须等于端点实例的 `getSourceConsistency()`/`getSinkConsistency()`；`TwoPhaseCommitSinkFunction` 端点必须声明 `PARALLEL`（CONN-01 successor 后的不变式——per-subtask 隔离为已落地能力；历史「`PLANNING_GATE_PARALLELISM_1` ⟺ 2PC 实例（与规划期门禁同键）」已随门禁解除废弃）。由发现测试与 catalog 探测运行期双重钉定。

**维护/探测工具入口**（`StreamConnectorCatalog`，落 core）：装配 + 枚举 + 探测用法：

```java
// 1. 装配：发现 classpath 上全部 connector-*.beans.xml 并构建注册容器
BeanContainerBuilder builder = new BeanContainerBuilder(null);
StreamConnectorCatalog.discoverConnectorBeansResources().forEach(builder::addResource);
IBeanContainerImplementor container = builder.build("stream-connectors");
container.start();

// 2. 枚举清单 + 能力（方向/交付语义/并行度/恢复语义/参数规格）
StreamConnectorCatalog catalog = StreamConnectorCatalog.of(container);
System.out.println(catalog.renderListing());

// 3. 探测：类型名 → 注册中心解析 → 工厂构造 → 能力一致性校验 → 关闭
Map<String, Object> params = Map.of("outputDir", "/tmp/out");
ConnectorProbeResult result = catalog.probe(ConnectorDirection.SINK, "file",
        new StreamConnectorConfig("file", params));
```

未知类型名 / 方向错配 = fail-fast typed error（含已注册清单）；字段级 conf 校验**不在**此处（item 20 conf-validate 边界：`StreamConfValidator.validateConnector` 在此之上叠加描述符参数字段级校验 + 可选连通性探测 `catalog.probeConnectivity`）。XDSL 声明形态（bean 引用 + 内联 xpl）本期不变，类型化声明暂未引入（引入时将一并裁定 `bean`/类型名冲突与 params 消费语义）。

## Source API 冻结政策（plan 369 Phase 5）

**两代归一（WI2 plan 2343，2026-10-08 完成）**：三个 legacy source（`MessageSourceFunction`/`DebeziumCdcSourceFunction`/`BatchLoaderSourceFunction`）的工厂面已全量迁 FLIP-27（`MessageSource`/`DebeziumCdcSource`/`BatchLoaderSource`，`Source`/`SplitEnumerator`/`SourceReader` 形态）——三个 legacy 类降级为程序化 API（测试/数据迁移编排器直构），工厂不再生产。**B 层表路由例外随迁移完成撤销（WI0 裁定 R2 兑现，2026-10-02 例外 → 2026-10-08 撤销）**：debezium 表路由由 FLIP-27 split 机制自然承接（一表一 split，enumerator 分配即路由），`SubtaskShardedSourceFunction` 不再出现在任何工厂 create 路径。**冻结剩余清单**：`KafkaSourceFunction`（SPI `kafka`，WI3 产物）为连接器面唯一剩余 legacy 代 source——其残余窗口 EO 语义钉定在 legacy 检查点契约上，迁移另立 successor（watch-only）。core 内核 `SourceFunction` 函数接口本身属算子 API 面（XPL 桥、集合源、程序化作业仍用它），不在归一范围。

## 维表本地物化缓存层（WI11/plan 2338）

`io.nop.stream.core.connector.lookup.CachingTableLookup`（实现 `ITableLookup` + AutoCloseable）是既有维表 lookup 桥接（WI14 基线）之上的本地物化层——per-record 处理保持同步（裁定 R5：不新增异步算子），异步性收敛在物化刷新层的函数内部后台资源：

- **per-record 路径**：TTL 内命中直接返回（零 DB IO）；miss/过期同步委派 delegate 并装载；null 结果同样缓存（防穿透，与正条目同 TTL 语义）。
- **后台刷新（扫描/执行分离）**：懒启动单线程 daemon 调度器（fixed-delay 不重叠扫描）+ 大小 = `refreshMaxConcurrency` 的 worker 池承接刷新 job——delegate 调用并发度真实 >1 且以池大小为硬上界。**刷新失败语义**：保留旧值 + WARN + 下轮重试（TTL 到期回落同步重查为自愈出口；移除条目会把 DB 抖动转化为同步查库风暴，已拒绝）。刷新续期 loadTime（刷新过的条目按新加载时间参与淘汰排序）。
- **失效策略**：TTL（age > `ttlMs` 访问时判 miss）+ `maxEntries` 最旧加载条目淘汰（可观测 victim：顺序加载 maxEntries+1 key → 最早加载者被逐出重委派）。
- **序列化边界**：cache/pool/scheduler 全 transient——反序列化后由同步 lookup 首次调用懒重建（线程安全防御）。
- **close 契约**：幂等（重复调用安全）；close 后 lookup typed 拒绝（`ERR_STREAM_INVALID_STATE`）而非静默回落 DB 查询。**生产调用落点** = 宿主函数 close() override——WI17 已在 `DimLookupEnrichFunction` 样例接线（per-key ValueState 快路径 + 本物化层生命周期，close 后 cache-miss lookup typed 拒绝，见 TestE2EDimLookupWithCheckpoint.closePropagatesToCachingLayerAndPostCloseMissFailsTyped）——或 bean destroy-method；本类只提供能力。
- **delegate 线程安全契约**：刷新池与任务线程会并发调用 delegate 的 lookup——既有只在任务线程调用的适配器接入本层前须确认并发安全。
- 配置：`ttlMs` / `refreshIntervalMs` / `refreshMaxConcurrency`（≥1）/ `maxEntries`（≥1），负值/越下界 typed 拒绝。
- 测试锚点：`TestCachingTableLookup`（9 用例：TTL/后台刷新/**并发双断言 ≥2 与 ≤池上界**/close 幂等+typed/淘汰 victim/null 防穿透/配置校验/序列化懒重建/刷新失败保留旧值）。

## Kafka 原生连接器与平台消息通道边界（WI3 R1，2026-10-07）

`nop-stream-connector-kafka`（`kafka` source / `kafka-2pc` sink，kafka-clients 4.3.1，与 nop-message-kafka 同版本线）与 `nop-message-kafka` 平台消息通道（`IMessageService` 的 Kafka 后端）是**双模块清晰边界**，替代方案（在 nop-message-kafka 上叠加 EO）已被裁定拒绝（语义边界混淆，双语义单实现面维护成本高于双模块）：

- **判据：是否需要流处理故障恢复语义（offset checkpoint / 事务提交）。需要 = 用 `nop-stream-connector-kafka` 连接器；仅平台消息收发 = 用 nop-message-kafka 通道**（`MessageSourceFunction`/`MessageSinkFunction` 适配器，AT_LEAST_ONCE 声明，语义由后端承载，无 checkpoint 状态）。
- 连接器语义：source offset map 入 checkpoint（REPLAYABLE）+ 事务 sink（commit-at-barrier epoch 事务）——语义进 checkpoint 边界；通道语义：at-least-once，由 Kafka 消费组 offset/重投递承载，不进流作业 checkpoint。
- 残余边界（如实声明）：kafka-2pc sink 的重复残余窗口见能力矩阵行；事务链 consume-transform-produce 同事务（sendOffsetsToTransaction）与分区再均衡（消费者组协议）为 Non-Goal（另立触发）。

## 提交前探测能力（dry-run，item 20 / P-REQ-13）

`conf-validate --connect` / `dry-run` 对每个 source/sink 端点按「能力接口 `ConnectivityCheckable` → FLIP-27 `Source` → `TwoPhaseCommitSinkFunction` 基契约 → 显式 SKIP」次序分派探测（驱动器 `io.nop.stream.core.connector.StreamConnectivityProber`）。逐族语义（含副作用红线）：

| 组件 | 探测 | 残留红线 |
|---|---|---|
| `FileSource` | H-1：`createEnumerator()+start()`（no-op 投递上下文）目录可达性 | 只读扫描，不分配 split |
| `FileTwoPhaseCommitSink` | H-5：`checkConnection()` = begin+rollback（构造期已建输出目录） | 无文件写入（目录 = 豁免幂等对象） |
| `JdbcTwoPhaseCommitSink` | H-5：`checkConnection()` = begin+幂等台账 DDL+rollback（物理连接） | 台账**表**豁免；台账**行**绝不产生 |
| `BatchLoaderSourceFunction` | H-3：`checkConnection()` = `loaderProvider.setup()` + 关闭 loader | 不消费批数据 |
| `BatchConsumerSinkFunction` | H-4：`checkConnection()` = 构造级结果断言（构造即 setup） | setup 语义由构造路径承担 |
| `DebeziumCdcSource` | 构造/参数级 + 凭据引用解密可达；**不拉引擎不连库** | 无 offset 写入、无订阅 |
| `MessageSource`/`MessageSinkFunction` | **显式 SKIP**（`connectivity-not-supported`；`IMessageService` 无平台 health-check，可达性如实呈现为不可判定） | —（不探测） |

错误配置（目录不存在/连接不可达/凭据不可解析）返回显式错误码 `nop.err.stream.connectivity-check-failed`（含端点与根因），绝不静默通过。命令用法与 exit code 契约见 owner doc `03-modules/nop-stream.md`「提交前校验」节；凭据引用语法见同页「凭据引用与明文边界」。探测行为级测试锚点：connector `TestPreSubmitConnectivityProbe`、jdbc `TestJdbcPreSubmitConnectivityProbe`、batch `TestBatchPreSubmitConnectivityProbe`、debezium `TestDebeziumPreSubmitConnectivityProbe` / `TestDebeziumCredentialIntegration`、flow `TestStreamConnectivityDryRun`、E2E `nop-stream-fraud-example` `TestConfValidatePreSubmitE2E`。

## 格式层（Avro / Schema Registry，WI18）

`nop-stream-format-avro` 提供跨连接器的 payload 格式层（当前主要消费面 = Kafka 连接器的
`valueDeserializer`/`valueSerializer` 注入点）：

- **`AvroSchemaRegistryClient`**：JDK HttpClient 最小 REST 客户端——`POST /subjects/{subject}/versions`（注册）、`GET .../versions/latest`、`GET /schemas/ids/{id}`；per-schema-id 与 per-subject 双缓存（同 subject 换 schema 后 encoder 侧不自动拾取新版本——已知边界，见下）。**请求级超时与锁外建连（C2-002）**：每个 HTTP 请求携带请求级 `.timeout()`（缺省 **10s**；约定配置键 `nop.stream.avro.registry-request-timeout`，`≤0` 装配期 fail-fast；配置化经构造器 Duration 参数承载——客户端为纯构造注入对象，不直读全局配置）；schema 缓存 miss 走 get→miss→**锁外 fetch**→`putIfAbsent` 幂等回填，HTTP I/O 不进 CHM bin 临界区——registry 挂死只延误发起 fetch 的线程至超时，不再毒化共享锁阻塞其他 subtask。
- **`AvroWireFormat`**：Confluent wire format（magic `0x0` + 4B BE schemaId + Avro binary）。`encode` 注册 schema 后写 wire；`decode` 按 header schemaId 取 writer schema、以 registry latest 为 reader schema 做演进读取；`readerSchemaFor` 兜底指定 reader schema。工厂 `avroDeserializer(subject)` / `avroSerializer(subject)` 直接可注入 `KafkaSourceFunction` / `KafkaTwoPhaseCommitSink`。
- **失败面全 typed**：registry 不可达、请求超时到期、magic byte 不符、schema 解析失败均抛 `StreamAvroException`（不静默降级、不静默重试）。**decode 回落面精确化（CRA 8.43 R4-03 W-02）**：`readerSchemaFor` 在负缓存窗口（`nop.stream.avro.reader-schema-fallback-ttl`，缺省 30s，≤0 构造期 fail-fast）内回落 writer schema，每窗口边界一条 WARN 恰一次——subject 未注册（HTTP 404 首次写竞态）为合法回落路径免 WARN；窗口到期重试探底。「不静默降级」语义=回落必带边界 WARN 信号+负缓存退避，非逐条失败抛出。
- **生命周期（W-03）**：`AvroSchemaRegistryClient`/`AvroWireFormat` 实现 `AutoCloseable`，wire-format `close()` 透传 client——**调用方义务**（当前生产装配零构造点；挂接归 multi-engine WI18，宿主接线届时承载 close 链）。
- **依赖**：avro 版本单点在 `nop-kernel/nop-dependencies`（`avro.version`，当前 1.12.1 = 实测基线）；无 Confluent 客户端依赖。
- **已知边界**：同 subject 多 schema 演进时 encoder 侧 schemaId 缓存不感知换版（首版边界；真实多 schema 写入诉求出现时升级缓存策略）。
- 锚点：`TestAvroWireFormat`（fake registry 真 HttpServer 用例：round trip / v1→v2 演进 / schemaId 缓存 / magic typed 拒绝 / registry 不可达 typed / 挂死注入请求级超时 typed + 并发 encode 不互阻 / 工厂注入）。

## 并发守卫与失败路径契约（CRA 8.44 R4-04）

- **Debezium 同名守卫（W-02）**：同名双管线（`totalParallelism<=1` 裸名共驻 JVM）第二个 `run()` 以 typed `ERR_STREAM_CONFIG_ERROR` 拒绝（AR-03 同口径）——不再静默覆盖第一实例的失败 listener；被拒实例不注册、无 finally 副作用，修复后可重试。载体（`DebeziumEngineWrapper.FAILURE_LISTENERS`）零改动。
- **Debezium 重入 typed（W-04）**：`run()` 重入 CAS 失败从静默假 EOS 改为 typed `ERR_STREAM_STATE_ERROR`（恢复路径负责重试）。
- **双桥 close 终止契约精化（W-05）**：join 超时路径依次——runner 单发 interrupt（best-effort）→ `queue.clear()` 释放在途 put（生产分发线程解除的真实通道）→ typed `ERR_STREAM_JOB_EXECUTE_FAILED` 抛出（信号零弱化）；batch loader reader close 对 loader close 异常 typed 上抛（不再 WARN 吞掉）。
- **kafka/batch 取消时延声明（W-01/W-03）**：kafka cancel 单发 `wakeup()`——启动期元数据拉取阻塞的取消时延从 ≤max.block.ms 收敛为即时；稳态 ≤ poll 间隔 200ms（限速切片窗与 seek/assign 窗同受 wakeup 覆盖）。batch loader cancel 单发 interrupt——响应 interrupt 的 loader 在预算内退出，不响应的实现取消时延上界 = 单次 `loader.load` 时长（声明边界）；reader 面取消时延上界 = 单次 load 时长。
- **RocksDB native 清理与恢复残留（W-06/W-07/W-10）**：openDB 失败路径逆序关闭全部 native 句柄（不再依赖不可靠的 GC finalizer）；restore 恢复先关增量句柄后关源库；restore 入口清扫「id 严格小于当前恢复 id」的陈旧恢复目录（跨部署回滚残留 id 前移后自愈收敛——watch-only）。
- **file 2PC 孤儿清扫（W-08）**：beginTransaction（恢复链末位）扫描本 subtask 后缀的孤儿 epoch temp——manifest∪pending 双白名单之外的残留按 WARN 删除（磁盘包络从「失败次数×全量 epoch」收敛为在途上限×单 epoch）；他 subtask 在途 temp 结构性不命中；manifest 损坏时保守跳过清扫。
- **RocksDB TTL sweep（W-11）**：`setCurrentKey` 挂 nanotime 门控周期 sweep（`nop.stream.rocksdb.ttl.sweep-interval-millis`，缺省 60000，≤0 关闭）——设计文档声明的形态在生产后端兑现；keyed TTL 的过期状态不再依赖 checkpoint 节奏回收。

## 2PC 双旋钮与语句/锁超时（CRA 8.43 R4-03 W-04/W-06/W-11）

2PC 双载体（JDBC/file）新增上界旋钮：`maxEpochRecords`/`maxEpochLines`（缺省 100000）与 `maxPendingEpochs`（缺省 8）——invoke 监视器内 add 前判定 + saveState 转存前判定，超限 typed `ERR_STREAM_STATE_ERROR`（携带当前水位/上限/epochId 与处置指引）；≤0 构造期 `ERR_STREAM_INVALID_ARG`；连接器 descriptor `optional` 声明 + builder fluent 承载，不入 ledger face 空间。JDBC 提交链全部 5 语句创建点统一 `statement-timeout-seconds`（缺省 60）——闭合 checkpointTimeout 盲区。file manifest 双级锁（JVM+OS）统一 60s 期限（JVM `tryLock` 可中断 + OS `tryLock` 200ms 轮询，超时 typed；OverlappingFileLockException 按被占轮询处理）。保留策略语义零回退。

## 2PC epoch 台账保留策略（face `epochledger`，C3-002）

2PC 提交台账的两载体（JDBC `stream_epoch_ledger_v2` + file manifest）按族级保留策略统一收敛，face token = `epochledger`（键形 `nop.stream.ledger.epochledger.*`）。摘要（≤3 行）：JDBC 面为形态 A 周期清理（锚列 `committed_at`，有界批删，挂模块生命周期链）；file manifest 面为形态 B commit 顺带裁剪（epoch 号账龄窗口 `ceil(retention-days × 86400000ms ÷ checkpointIntervalMs)`，并入既有 manifest rewrite）；两面保留窗均 ≥ 可重提交 epoch 窗口（防误删踩坏恢复幂等守卫）。**统一参数集（retention-days 缺省 30 / cleanup-interval 缺省 1h 下限 60s / delete-batch-size 缺省 1000，非法值装配期 fail-fast）与键名的唯一事实源 = ledger-retention-policy.md（位于 ai-dev/design/concurrency/ 目录，按 docs-for-ai 边界规则不直接链接）的 §3.1/§3.2/§3.4 R01 族级 SSOT 成员传导行，以该文档为准，本文档不复制参数；参数修订只走 SSOT。** abort 不触台账语义与恢复幂等守卫零回退。锚点：`TestJdbcEpochLedgerRetention_C3_002`、`TestFileTwoPhaseCommitSinkManifestPrune_C3_002`、`JdbcEpochLedgerCleaner`、`EpochLedgerConfigs`。

## CEP 并行 subtask 契约（copyForSubtask 三面封闭，C1-001）

**CRA 8.43 R4-03 增补**：匹配缓冲上界 `nop.cep.pipeline.match-buffer.max-events`（缺省 100000，open 期 ≤0 fail-fast）——`bufferEvent` 前置计数判定 typed 拒绝（携带 key/计数/上限与「watermark 停滞/补 within」指引），桶移除点按桶大小递减；`reconciledTimerKeys` 在 NFA 全清位点同步移除（幂等——remove 后重对账为零 delta）。

并行 CEP（parallelism ≥ 2）下，`CepOperator.copyForSubtask()` 对 `nfaFactory` 与 userFunction 做序列化 round-trip 深拷贝——每 subtask 私有 NFA 条件图，一次封闭三面：条件 runtimeContext 槽位不再 last-writer-wins、条件 `open` 各 subtask 各一次（无 N 次泄漏）、一个 subtask 的 close 不再关闭其他 subtask 的条件资源。

- **xlang 编译叶子共享豁免**：XPL 脚本场景的条件经编译产出不可变、无 per-subtask 状态、不实现 RichFunction 生命周期的 `io.nop.xlang.*` 编译态 eval 函数（非 Serializable）——round-trip 经白名单以共享引用回填，脚本场景不受影响；条件对象仍深拷贝私有。
- **部署期行为变更声明**：白名单外非 Serializable 的用户函数/条件在部署展开期 typed 失败（指引实现 Serializable），不再以共享引用静默放行——脚本场景不受影响，Java 侧非 Serializable 函数为显式部署期失败。
- **代价**：每 subtask 一次、启动窗内一次性，运行期零开销。
- 锚点：`TestCepOperatorSubtaskCopy`（NFA 图实例不同 + ctx 各自正确 + xlang 叶子共享/非 xlang 非 Serializable typed 失败）、`TestCepOperatorLateRecordsDroppedMetric`（并行实例指标不串）。

## RocksDB 状态后端快照包络与 metrics gauge 语义（C5-002/C1-002）

**CRA 8.43 R4-03 增补**：gauge 以 `backend=<recorder 序号>` 单标签注册（per-subtask 互异，backend close 在监视器内先置空 dbRef 再逐 Meter.Id 幂等摘除）；个别属性不可用 WARN 一次、后续降 debug。迁移 helper 为单遍 RocksIterator 流式（一修盖 4 状态类，零跨行收集）。快照预算耦合校验 `nop.stream.rocksdb.snapshot.declared-entries`（声明分支，0=旁路；×速率常数 > timeout×0.5 → typed fail-fast）+ `nop.stream.rocksdb.snapshot.hash-policy`（new-only 缺省——restore 播种+逐 checkpoint 滚动+size-drift 重哈希；full 为 opt-out 档）。

- **增量 checkpoint 缺省 true**：`RocksDBKeyedStateBackend.incrementalCheckpointEnabled` 缺省翻转——默认路径产出增量 SST 快照，不再把整个状态库逐值反序列化物化为堆内对象图；显式 opt-out 全量（`setIncrementalCheckpointEnabled(false)`）时受规模阈值守卫：键数超 `nop.stream.rocksdb.snapshot-materialize-max-entries`（缺省 100000，`≤0`=禁用守卫显式自担）typed `StreamException` 拒绝并指引回增量——显式失败优于 OOM。
- **迁移校验双路径生效**：全量与增量两条恢复路径均执行 schema-mismatch migrate-or-fail-fast——增量快照内嵌逐 state schema header，恢复侧重建 restored descriptor 并走与全量路径同判据的迁移校验；JSON 往返后丢失的增量 marker（全 null bean）typed fail-fast，不静默穿透为空态恢复。恢复面双形态兼容，round-trip 语义等价。
- **runtime 引擎形态一致**：任务侧 RocksDB 后端快照形态钉到 coordinator 增量模式（引擎增量缺省关时任务侧产出全量形态）；携带增量 marker 的 keyed 快照进 rescale 恢复路径 typed fail-fast（附处置指引）——增量 coordinator + rescale 恢复的完整集成不在当前能力面。
- **metrics gauge close 语义**：`RocksDBMetricsRecorder` gauge 取值在监视器内求值（`readCurrent`）——close 窗口内 scrape 读到非 null 引用即 native 未关闭、读到 null 返 `NaN`（detached 语义），任意交错零 native use-after-free。
- 锚点：`TestRocksDBSnapshotRestore`（round-trip + 键数参数化驻留上界 + 全量 opt-out leg）、`TestStateFamilyBackendMatrix`、`TestRocksDBRestoreKeyRematerialization`、`TestRocksDBMetricsRecorder`（close×scrape 交错压窗）。

## Debezium 已发射位点协议（last-emitted，C8-001）

`DebeziumCdcSourceReader` 的 checkpoint 快照位点取**已发射边界**而非引擎 offset store 当前值：发射点逐事件从 `ChangeEvent.metadata` 提取位点（优先级序 `lsn`（PostgreSQL）→ `commitLsn`（SQL Server）→ `binlogPosition + binlogFile`（MySQL，两字段须同时在位））维护 per-server last-emitted 位点表，`snapshotState` 抬入该表合成的引擎 offset map——队列在途（≤256 条）不再形成静默丢失窗口。

- **typed 失败判据**：`metadata` 为 null、`serverName` 为空、或所选键形字段缺失/不全 = typed `StreamException`（指明 split 与事件序），不回退旧 offset、不静默跳过。
- **restore 取 min 保守重放**：恢复跨 offset-carrying split 取位点数值最小值（同 binlogFile 内比 pos）——保证不越过任一 split 视角的已发射边界，**at-least-once：宁可重复不丢失**；下游幂等不再是正确性前提。legacy `DebeziumCdcSourceFunction` 同机制随修。
- `NopStreamOffsetBackingStore` 与 nop-message-debezium 引擎 commit 策略零改动（只消费 `metadata` 特化字段、按既有序列化格式写回）。
- 锚点：`LastEmittedOffsetTracker`、`TestDebeziumLastEmittedOffsets_C8_001`、`TestDebeziumCdcSource`、`TestDebeziumCdcCheckpoint`。

## 能力矩阵

| 组件 | 方向 | 交付语义（含依据） | 并行度 | 恢复语义（cursor/offset checkpoint 路径） |
|---|---|---|---|---|
| `FileSource`（SPI `file` source） | source（有界，FLIP-27 风格 split-based） | **at-least-once**（source 边界）：per-split 字节光标 checkpoint，恢复后从光标重放；无事务读。`notifyCheckpointComplete` 为 no-op（文件 offset 天然幂等） | **支持并行**（split 按 discovery index 轮询分配到 subtask，`FileSplitEnumerator`） | 双通道状态：reader 侧 split 列表 + 光标入 operator state（key `source-reader-splits`）；enumerator 侧发现/分配/完成状态经 `SourceEnumeratorSnapshot` 随 epoch manifest 持久化 |
| `FileSourceReader` | source reader | 同 `FileSource`（字节精确光标：`currentOffset` = 下一读取字节位，按 LF/CRLF/CR 精确计字节，跨平台一致） | 随 source | 恢复后 `openSplit` seek 到 checkpoint 字节位，`activeBytesConsumed` 以恢复基线播种——首个快照不回退光标（`TestFileSourceAuditFixes.snapshotAfterFirstPostRestorePollAdvancesFromRestoredBase`）。**恢复契约三收口（AR-15，plan 2026-09-04-1326-3）**：① seek 用 `FileChannel` 精确定位 + size 校验，cursor/endOffset 超文件长度（截断）→ typed `IOException` 而非静默错位读（`FileInputStream.skip` 可越 EOF 谎报已跳过）；② 光标推进与 `snapshotState` 同 monitor（无 JMM 撕裂读）；③ 读取以 `split.getEndOffset()` 封顶（split 枚举后文件增大不越界发射）——三路径由 `TestFileSourceReaderRecovery` 钉定 |
| `FileTwoPhaseCommitSink`（SPI `file` sink） | sink（文件） | **exactly-once（两阶段提交）**：`getSinkConsistency()=TWO_PHASE_COMMIT`；每 epoch 写 `.epoch-N.tmp` → `saveState` 先落盘 → checkpoint durable 后 `ATOMIC_MOVE` 到终文件 + 原子更新 `manifest.properties`；commit 幂等（manifest 键守卫，**按 pending 条目来源判别**：经恢复入口 `setPendingCommits` 还原的条目撞键 = recover-safe 跳过；本 run 新建条目撞键 = fresh-run 复用目录 → typed fail-fast（R6 CON-01，temp 数据保留，改用新目录或走恢复启动））；崩溃在 rename 与 manifest 之间只修复 manifest（同样仅对恢复条目） | **支持并行**（per-subtask 隔离：`copyForSubtask(int)` 独立拷贝，subtask>0 的 per-epoch 文件与 manifest 键带 `.sK` 后缀，subtask 0 保持 legacy 无后缀名——并行 subtask 互不覆盖；manifest 读改写经输出目录锁（JVM 内 ReentrantLock + 跨 JVM FileLock）串行化 + per-subtask tmp 名（AR-14：并行 commit 交错无丢条目/伪失败，`TestFileTwoPhaseCommitSink.testParallelSubtaskCommitsInterleaveWithoutManifestLoss` 钉定）；LOCAL e2e 证明 `TestParallel2PcFileE2E`） | pending 事务（tempPath/recordCount/subtaskIndex）入 checkpoint（key `pending-commits`）；恢复时 durable-未提交 epoch 重提交、未 durable epoch abort；杀恢复 exactly-once 由 `TestFileTwoPhaseCommitSink.testFileSinkKillRecoverExactlyOnce` 钉定 |
| `MessageSource`（SPI `message` source，FLIP-27 split；WI2 plan 2343 自 legacy `MessageSourceFunction` 迁移——push 订阅经有界队列转 pull，首错 typed 失败不假成功） | source（无界） | **AT_LEAST_ONCE（声明）**：实际语义由注入的 `IMessageService` 后端承载——Kafka（手动 commitSync、失败 seek 回退）= at-least-once；Pulsar/SysDao 同级；**LocalMessageService 无订阅者即丢弃 = 实际 best-effort**。类型不符/collect 失败经 `pollNext` typed 失败（P1-9 语义等价） | **支持并行**（消费者组语义）：每 reader 一枚订阅 token（`topic#subtaskIndex`），真实数据分布由后端承载；工厂不再做 `{topic}-{subtaskIndex}` 逐分区主题改写（legacy 行为，需要分区主题时由 producer 侧直写） | **无 offset checkpoint**（recovery=NONE）——重启后重投递行为完全由后端订阅语义决定（Kafka group offset / Pulsar subscription / SysDao 租约） |
| `MessageSinkFunction`（SPI `message` sink） | sink（消息） | **AT_LEAST_ONCE（声明）**：每条记录同步 `messageService.send`（阻塞 send future）；无 checkpoint 参与、无去重。实际语义后端承载（Kafka `acks=all,retries=3` = at-least-once；Local 后端无订阅者丢弃 = best-effort） | 无并行限制 | 无状态（per-record 直发）；不参与 checkpoint |
| `JdbcTwoPhaseCommitSink`（SPI `jdbc-2pc` sink） | sink（JDBC 表） | **exactly-once（两阶段提交）**：`TWO_PHASE_COMMIT`；每 epoch = 一个独立 JDBC 事务（数据行 batch + `stream_epoch_ledger_v2` 台账行同事务提交）；`saveState` 先把缓冲移入 pendingCommits（本 checkpoint 捕获本 epoch，防滞后一档）；commit 幂等（台账三元键 `(sink_namespace, epoch_id, subtask_id)` 守卫，namespace=部署身份 `jobId|vertexId`——同库多 sink 支路/跨作业不互踩守卫，R5-CON-01 修复）；guard 命中记 **WARN**（含完整键上下文）；失败回滚 | **支持并行**（per-subtask 隔离：`copyForSubtask(TaskLocation)` 独立拷贝 + 台账命名空间键——并行 subtask 同 epoch 提交互不碰撞；LOCAL e2e `TestParallel2PcJdbcE2E`（CI 可复现）；真实多 JVM `TestParallel2PcMultiJvmE2E` 为 **gated 手动验证**（非 CI 常跑，启用命令见 user guide「多 JVM gated 测试（手动启用）」，F-07 诚实化）） | pending 事务入 checkpoint；恢复时 durable-未提交重提交（台账幂等）、未 durable abort（"data was never written"）；`TestJdbcTwoPhaseCommitSinkDeep.testRestoreFromEpochDurableReCommit` / `testRestoreFromEpochNonDurableAbort` 钉定；v2 命名空间/跨支路互不误伤/同版本恢复自洽由 `TestJdbcTwoPhaseCommitLedgerNamespace` 钉定。**dmlMode（cdc-design.md §3.5）**：`insert`（默认追加；ChangeEvent u/d 事件 fail-fast）/ `upsert`（c/r/u 按 `keyColumns` 方言覆盖写）/ `upsert_delete`（d 按 key 删除）——键控模式方言生成器（MySQL `ON DUPLICATE KEY UPDATE` / PG `ON CONFLICT DO UPDATE`）+ 批内同 key last-write-wins 折叠 + ledger 复用，恢复重放结构性安全；列集首记录钉定、漂移类型化拒绝；`TestJdbcTwoPhaseCommitSinkDml` 钉定 |
| `JdbcTwoPhaseCommitSinkBuilder` | （构建器） | fluent builder：`jdbcTemplate/tableName/ledgerTableName/columns/addColumn/recordMapper/dmlMode/keyColumns/opMapper/deleteKeyMapper/maxBatchSize/build`（build 恒 subtaskIndex=0）；SPI 工厂 `jdbc-2pc` 经此 builder 构造 | — | — |
| `DebeziumCdcSource`（SPI `debezium-cdc` source，FLIP-27 split；WI2 plan 2343 自 legacy `DebeziumCdcSourceFunction` 迁移；Debezium **3.7.x.Final** 嵌入式引擎，connectorType 白名单 mysql/postgres） | source（CDC，无界） | **REPLAYABLE（offset checkpoint，可重放）**：Debezium engine offset 经 `NopStreamOffsetBackingStore` 桥接（nop checkpoint 为 offset 唯一真相源，`offset.storage` 覆盖类型化拒绝）；offset map 以**实例级副本**随 split state 走 FLIP-27 检查点面，恢复经嵌入引擎宿主的 `initializeState` 合约重放。collect/引擎终态失败经 `pollNext` typed 重抛（R5-CON-02/03 等价，宿主函数内建捕获） | **PARALLEL（表 = split，enumerator 分配即路由——B 层 hash 路由的 split 机制承接，R2 例外撤销）**：每 reader 一台引擎实例（实例名 `{name}-{subtaskIndex}`/offset 全隔离），注册时整份额分配；启动期校验 tableIncludeList 非空、条目数≥parallelism（比 legacy hash 空分片更粗的确定性等价）；schema history 必配（jdbc 默认/file 回退，无静默内存）；启动后新表分配 typed 拒绝（引擎 include-list 不可中途改写）；恢复分配 = 恢复态与新鲜份额求差集，每表恰一桶/恰一引擎（跨 reader 双登记不可再现——详见 CDC cookbook「offset 恢复与重放」节） | offset map（base64）入 reader split state（FLIP-27 `source-reader-splits` 信封）+ enumerator 快照入 `EpochManifest.sourceEnumeratorSnapshots`；快照位点 = **已发射边界（last-emitted，per-server 键形，位点不可得 typed 失败；restore 取 min 保守重放 at-least-once）**，非引擎 store 当前值——见「Debezium 已发射位点协议」节；首次启动走 fresh snapshot（实例名级 `clearConnector` + WI9 首跑种子契约），有状态恢复从位点续读；跨大版本位点不恢复（升级=重快照，键控 upsert 兜底）。锚点：`TestDebeziumCdcSource`（split/serializer/份额分配/实例装配/offset 快照恢复/迟到分配 typed）、legacy 语义面 `TestDebeziumCdcCheckpoint`/`TestDebeziumCdcSharding`/`TestDebeziumEngineProperties`、gated `TestDebeziumRealMysqlCdc`/`TestDebeziumRealPostgresCdc`（真库）。详见 CDC cookbook |
| `KafkaSourceFunction`（SPI `kafka` source，WI3/plan 2335，nop-stream-connector-kafka） | source（Kafka topic，无界 + 可 DRAIN） | **REPLAYABLE（offset checkpoint，可重放）**：`getSourceConsistency()=REPLAYABLE`；partition→offset map 入 operator state（key `kafka-offsets`，JSON 安全 String 键），恢复后逐分区 seek 到 checkpoint 位点；consumer 自动提交强制关闭并 typed 拒绝重启用（checkpoint 是 offset 唯一真相源）；冷启动位 `startFrom=earliest`（缺省）/`latest`；value 反序列化函数注入（缺省 UTF-8 String）；record 以 broker 时间戳 `collectWithTimestamp` 发射 | **PARALLEL（分区 round-robin 独占分片）**：`SubtaskShardedSourceFunction`——`validateParallelism` 记录部署并行度，`copyForSubtask(int)` 按 `partitionId % total` 独占分片（无消费者组协调/再均衡；分片可为空 = 无分区可分配、空 map 快照，非 watermark-idle）；恢复按 TaskLocation 1:1 严格查找，跨并行度 typed 拒绝 | 锚点：`TestKafkaSourceFunctionSharding`（分片互斥且并集=全部分区/路由顺序无关/offset 往返/typed 守卫）、gated `TestKafkaConnectorExactlyOnceE2E`（真 broker kill-recover 零重复，见下「Kafka 连接器边界」节）。**与 nop-message-kafka 通道的边界见「Kafka 原生连接器与平台消息通道边界（WI3 R1）」节** |
| `KafkaTwoPhaseCommitSink`（SPI `kafka-2pc` sink，WI3/plan 2335） | sink（Kafka topic） | **两阶段提交（commit-at-barrier epoch 事务，EXPERIMENTAL——见成熟度豁免说明）**：transactional producer，`transactional.id = prefix 与 jobId/vertexId/taskIndex 以竖线连接`（`copyForSubtask(TaskLocation)` 稳定跨重启，initTransactions fence 同身份僵尸代）；epoch 提交融合在 checkpoint barrier 的 saveState 点（commitTransaction+beginTransaction(next)+pendingCommits 标记，标记仅在提交成功后写）。**事务裕度守卫（C8-006）**：producer 显式缺省 `transaction.timeout.ms=900000`（配置键 `nop.stream.connector.kafka.transaction-timeout-ms`，须为正数且 ≤ Kafka 客户端 32-bit INT 配置上界——超界装配期 typed fail-fast；上界另受 broker `transaction.max.timeout.ms` 约束，配置超 broker 上界时 `initTransactions()` 运行期失败）；构造期校验 `checkpointIntervalMs × 3 ≤ transaction.timeout.ms`，用户显式配置冲突 typed fail-fast **拒配不告警放行**（防「typed 失败→重启→超限→job FAIL」触界循环）。**零丢失全窗口可证**（barrier 前崩溃 = 悬挂事务 abort + source 重放补齐；barrier 后崩溃 = 已可见）；**零重复在残余窗口 [barrier commitTransaction, checkpoint durable) 外成立**——崩溃落入该窗口时恢复点早于本 barrier，source 重放 + sink 重发产生重复（一次 broker RPC + 持久化时延；同步快照下 ≈ RPC 时长；公共 API 无事务 resume 不可关闭，pin 为 watch-only residual）。可见性时延 = checkpoint 边界（非 invoke 即可见）。commit 对 epoch ≤ 高水位幂等 no-op（高水位自恢复的 pendingCommits 重放），超过高水位 typed 拒绝（契约违约非可恢复态） | **支持并行**（per-subtask 隔离：`copyForSubtask(TaskLocation)` 独立实例 + 独立 transactional.id + 独立高水位/pendingCommits） | 恢复：`recover()` = rollback 悬挂事务 + 新 begin（initTransactions 每 producer 代恰一次）；`restoreFromEpoch` 先从重建的 pendingCommits 重放高水位再走基类 re-commit（命中幂等分支）/abort。锚点：`TestKafkaTwoPhaseCommitSinkLifecycle`（epoch 生命周期全路径/mock producer 调用序列/typed 守卫）、gated `TestKafkaConnectorExactlyOnceE2E` | source
| `BatchLoaderSource`（SPI `batch-loader` source，FLIP-27 split；WI2 plan 2343 自 legacy `BatchLoaderSourceFunction` 迁移——offset/seek/单写入者契约原样承接） | source（有界，nop-batch 桥接） | **AT_LEAST_ONCE（恢复=客户端跳过续读，D1 裁定方案 a）**：适配 `IBatchLoaderProvider`；offset 为 next-index 约定（=已发射记录数，对齐 `CollectionReplayableSource`），checkpoint 保存该计数；恢复 `seek(offset)` 后 `run()` 在遍历内**客户端跳过前 offset 条已发记录**再续发——一次恢复=一次续读，无全量重放、offset 不谎报（跳过实现收敛在 `BatchLoaderSourceFunction` 内，不改 `IBatchLoaderProvider`/`IBatchLoader` 平台接口）。**确定性前提（必读）**：客户端跳过仅当 `IBatchLoader` 恢复后重新遍历的前 N 条与崩溃前一致（确定性遍历序，如带 ORDER BY 的查询）才正确；违时（loader 排空仍不足 offset）typed fail-fast（`ERR_STREAM_STATE_ERROR`，skip shortfall）而非静默错位。负 offset typed 拒绝 | **单写入者（R5-CON-05）**：无 per-subtask 分片——`parallelism > 1` 时每副本会全量重复投递，实现 `ParallelismCheckable` 在部署期（execution plan 构建）fail-fast typed 拒绝（`ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH`），只允许 parallelism=1；钉定于 `TestBatchLoaderParallelismGate` | 发射计数 offset 入 operator state（key `source-offset`，`volatile` 可见性 AR-10）；恢复时 `restoreState` 解析并调 `seek(offset)`；seek-续读/负 offset 拒绝/跳空 typed 三路径由 `TestBatchLoaderSourceFunction.testSeekThenRunResumesFromOffsetWithoutFullReplay` / `testSeekNegativeOffsetRejectedTyped` / `testSkipShortfallFailsTypedInsteadOfSilentMisposition` 钉定，算子级 round-trip 由 `testStreamSourceOperatorCheckpointRestoreWithBatchLoader` 钉定 |
| `BatchConsumerSinkFunction`（SPI `batch-consumer` sink） | sink（nop-batch 桥接） | **IDEMPOTENT（声明）**：缓冲 + 批 flush 到 `IBatchConsumerProvider`；非事务——exactly-once 责任在消费端幂等。flush 失败抛错且**保留缓冲重试**；`close()` flush 失败显式报「data may be lost」 | **单实例（R6-CON-03）**：共享非同步缓冲为单消费者契约——实现 `ParallelismCheckable` 在部署期（execution plan 构建）对 `parallelism > 1` fail-fast typed 拒绝（`ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH`），只允许 parallelism=1；描述符 `SINGLE_INSTANCE`，钉定于 `TestStreamConnectorRegistryDiscovery` / `TestBatchConnectorFactories` | 无 checkpoint 参与（非 `CheckpointParticipant`）；失败重试靠缓冲保留 |

## Debezium 引擎线程模型与终结预算（1.2-C4-004，plan 2498）

引擎宿主链（`DebeziumCdcSourceReader.launchEngine` → `nop-message-debezium` 的 `DebeziumEngineWrapper`）的引擎任务体**不在平台共享 `nop-global-worker` 池执行**——Debezium 嵌入引擎为长驻阻塞任务体，常驻共享 worker 会按 CDC subtask 数永久钉死共享线程：

- **专用载体**：每引擎实例一台专用单线程执行器（平台 `NamedThreadFactory` daemon 形态，线程名前缀 `debezium-engine-<connector>`——线程 dump 与共享 worker 可辨识）；载体有独立容量预算与关闭归属，不经 globalWorker/globalTimer 执行任务体。
- **stop 有界终结预算**：`stop()` 在既有 close 链（reader close→cancel→stop→engine.close→join(5s)）之后对专用载体 `shutdown()` + `awaitTermination(5s)`（`STOP_TERMINATION_BUDGET_SECONDS`），无无界等待；超时 WARN、中断路径恢复中断位 + ERROR，不吞异常。
- **边界（已续作闭合，1.2-C3-014，plan 2503）**：stop 终结 latch 契约与启动守卫已由 R1-08 同文件续作落地——停止契约终止性见下节；本专用载体即其 `shutdownNow` 升级形态的载体前提（形态零改动）。
- 测试锚点：`nop-message-debezium` `TestDebeziumEngineDedicatedCarrier_C4_004`（引擎体执行线程 = 专用载体非 `nop-global-worker`；stop 有界终结预算内返回）。

## Debezium 停止契约终止性（1.2-C3-014，plan 2503）

引擎宿主停止契约从「stop 已发出」升级为「**引擎已终止**」——幽灵引擎（活 binlog 连接 + 引擎线程组 + offset 持续覆写同名 registry 桶）在停止路径上不可静默再现：

- **wrapper 面（`DebeziumEngineWrapper`，nop-message-debezium）**：
  - **STARTING_TASKS 关闭拒绝有预算重试**：debezium-embedded 3.7 在引擎启动窗口对 `engine.close()` 抛依赖方 `IllegalStateException`——stop 路径对该拒绝做有预算重试（等窗口离开后重试直至 close 成功），预算耗尽 typed 失败（`ERR_DEBEZIUM_ENGINE_STOP_FAILED`，不吞）；typed 失败**不锁** `stopped` 一次性守卫（守卫仅在契约完成后置位），后续 `stop()` 可重试 close——修复前「单次尝试 + 吞异常 + 一次性守卫永久 no-op」即审计认定的永久幽灵机制。
  - **终结 latch / 终止可观测**：每次 start 建引擎体终结 latch，载体任务退出即 countDown（无论自然退出/失败/守卫跳过）；`isTerminated()` / `awaitEngineTermination` 使「引擎已终止」对停止面可查询，不再从「runner 线程已 join」间接推断（runner 经 latch 退出时 ghost 可存活的窗口已闭合）。
  - **stuck 引擎升级**：latch 预算内未终止 → 专用载体 `shutdownNow()` 强制升级；再超预算 → 显式 ERROR ghost 风险声明（`nop.message.debezium.engine-ghost-risk`），绝不静默。
  - **worker 入口守卫**：引擎任务体入口检查 stop 请求——stop 已完成后才被调度的 worker 不再驱动引擎。
- **使用面终止性验证（nop-stream-connector-debezium）**：四个 in-scope 停止面在 `source.stop()` 后调用 `verifyEmbeddedEngineTerminated`（`ENGINE_TERMINATION_VERIFY_BUDGET_MILLIS` = 5s 有界轮询消费 wrapper 终止性信号）——`DebeziumCdcSourceReader.close` 与 `truncateForDrain` 以 typed `StreamException` 上抛 ghost 风险（该两面签名本就向调用方携带失败）；function `run` finally 与 `cancel` 以 ERROR ghost 声明呈现（清理路径不掩蔽主结果，双形态均显式可见）。既有 runner-join typed 超时零回退。
- **边界**：REGISTRY 同名桶互覆残余（ghost offset 覆写新一代恢复位点）的契约归宿 = REGISTRY 生命周期契约（R4-24 实现契约，stop 侧配对释放 + 条件移除——registry-lifecycle-contract.md，位于 ai-dev/design/concurrency/ 目录，按 docs-for-ai 边界规则不直接链接，本文档不重复）；Phase 3 新落的终止性信号按「谁先落地谁不得假设对方存在」纪律供 R4-24 自由取用。
- 恢复分配语义（恢复态与新鲜份额求差集、每表恰一桶）见 `03-modules/nop-stream-cdc-cookbook.md`「offset 恢复与重放」节。
- 测试锚点：`nop-message-debezium` `TestDebeziumEngineStopContract_C3_014`（STARTING_TASKS 窗口重试至终止 / typed 可重试 / worker 守卫 / stuck 升级 + ghost 声明 / cancel→stop→终止→信号可查询入口到出口链）；`nop-stream-connector-debezium` `TestDebeziumCdcStopVerification_C3_014`（truncate typed / cancel ERROR / reader close typed）。零回退钉：`TestDebeziumEngineDedicatedCarrier_C4_004`（载体形态）+ 既有 connector 套件。

## 并行 2PC 能力说明（原「硬门禁」节 supersession）

2PC sink（file/jdbc 内建）支持任意有效并行度，exactly-once 在 P=N 成立（CONN-01 successor 已落地，原规划期并行度门禁 `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 已随门禁移除删除）：

- **机制**：`TwoPhaseCommitSinkFunction.copyForSubtask(int)` 逐 subtask 独立拷贝（独立 buffer/pendingCommits）；JDBC 台账复合键 `(epoch_id, subtask_id)`、File 输出 `.sK` 后缀——subtask 身份进入提交键
- **第三方子类契约**：未 override `copyForSubtask(int)` 的用户子类在 P>1 部署期构建 subtask 拷贝时 **fail-fast**（基类默认抛 typed `StreamException(ERR_STREAM_UNSUPPORTED)`，No-Silent-No-Op；规划期不做 opt-in 声明，见 平台内部 checkpoint 设计文档（ai-dev/design/nop-stream/ 目录，按 docs-for-ai 边界规则不直接链接） §6.4.3）
- **跨并行度恢复**：2PC sink 顶点恢复时并行度与快照不一致被 typed 拒绝（`ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED`，D1 裁定 平台内部 checkpoint 设计文档（ai-dev/design/nop-stream/ 目录，按 docs-for-ai 边界规则不直接链接） §8.5.2）；same-parallelism kill/recover 为支持路径
- **钉定测试**：`TestStreamGraphGenerator.testTwoPhaseCommitSinkAtParallelism2BuildsAndSplitsIntoIndependentSubtaskCopies`（P=2 全管线 + per-subtask 拆分）/ `testTwoPhaseCommitSinkRaisedToParallelism3ViaSetParallelismBuildsPerSubtaskCopies`（P=3）；运行时 D1 拒绝 `TestTwoPhaseCommitSinkParallelismChangeRestoreE2E`（runtime）+ `TestParallel2PcJdbcE2E`（场景级）；子类 fail-fast 契约 `TestOperatorSubtaskIsolation`

## 语义组合速查

| 目标 | source | sink | 结果 |
|---|---|---|---|
| 端到端 exactly-once | `REPLAYABLE`（Debezium CDC）/ cursor-checkpoint（File） | `TWO_PHASE_COMMIT`（File/JDBC 2PC sink） | exactly-once（`StreamRequirementValidator` 对 `STRICT_EXACTLY_ONCE` 管线 build 期校验此组合，违配 fail-fast） |
| at-least-once | message/batch source | message/batch sink | at-least-once / 幂等消费（组合不满足 STRICT_EXACTLY_ONCE，校验器显式拒绝而非静默降级） |

## 恢复验证锚点索引（行为级测试）

| 组件 | 测试（模块 `src/test/java` 下） |
|---|---|
| FileSource/Reader | `TestFileSourceAuditFixes`（kill-recover exactly-once、恢复后光标推进、CR/LF 字节精确）、`TestFileSourceCheckpointRestore`（enumerator/reader 状态 round-trip）、`TestFileSource` |
| FileTwoPhaseCommitSink | `TestFileTwoPhaseCommitSink`（kill-recover exactly-once、幂等 commit、manifest 修复、abort、coordinator 驱动 commit）、`TestFileTwoPhaseCommitSinkManifestPrune_C3_002`（manifest 保留裁剪双面） |
| JdbcTwoPhaseCommitSink | `TestJdbcTwoPhaseCommitSinkDeep`（saveState 捕获本 epoch、幂等、durable/未 durable 恢复、subsuming commit、独立事务）、`TestJdbcTwoPhaseCommitSinkSkeleton`、`TestJdbcTwoPhaseCommitSinkParallelIsolation`、`TestJdbcEpochLedgerRetention_C3_002`（台账保留清理/未到期保留/幂等/接线） |
| MessageSource/Sink | `TestMessageAdapters`、`TestMessageSourceFunctionRestart`、`TestMessageSourceFunctionThreadSafety`、`TestConnectorResourceManagement` |
| DebeziumCdcSource（FLIP-27） | `TestDebeziumCdcSource`（split/serializer round-trip、份额分配、实例装配、offset 快照/恢复、迟到分配 typed）+ legacy 语义面回归：`TestDebeziumCdcCheckpoint`（offset round-trip ×3、注入链、kill-recover 零重复、首启不吃陈旧 offset）、`TestDebeziumLastEmittedOffsets_C8_001`（last-emitted 位点协议：键形/typed 判据/min 重放）、`TestDebeziumCdcSourceFunction`、`TestDebeziumResourceManagement`；`TestDebeziumCdcSourceCompletion` 为永久 `@Disabled`（run() 无界源契约失配钉注）不作为回归锚点——F-36 |
| BatchLoader/BatchConsumer | `TestBatchLoaderSourceFunction`（offset/seek/算子 round-trip）、`TestBatchConsumerSinkFunction*`（缓冲/flush/close/失败保留重试）、`TestConnectorConsistencyCapability`（声明矩阵 + STRICT 校验） |
| **SPI 注册中心** | `nop-stream-core` `TestStreamConnectorRegistry`（注册机制：枚举/别名优先级/重复/描述符校验/未知类型/方向错配/必填参数）；各连接器模块 `Test*ConnectorFactor*`（工厂构造 + 实例级单一事实源对齐）；`nop-stream-runtime` `TestStreamConnectorRegistryDiscovery`（**全量注册发现 + 能力矩阵逐项断言——本页矩阵的代码级单一事实源**）、`TestStreamConnectorRegistryE2E`（registry 构造端点 → `env.execute()` → 输出，Anti-Hollow 接线证明） |
