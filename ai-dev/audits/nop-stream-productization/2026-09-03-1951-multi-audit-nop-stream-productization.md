# nop-stream 多维度深度审计报告

> Audit Status: closed
> Audit Type: multi-dimensional
> Mission: nop-stream-productization

## 基本信息

- **审核目标**: `nop-stream/`（10 个子模块 + quickstart，~1317 个 Java 文件，~231K 行）— 代码、配置、测试、公共契约（exports、API surface）
- **审计日期**: 2026-09-04（任务时间戳 2026-09-03-1951）
- **方法**: 按 `ai-dev/skills/deep-audit-prompts.md` 执行 — 11 个维度初审子 agent（并行两波）→ 3 个独立复核子 agent 对全部高危发现逐条重验（保留/降级/驳回 + 三级判级）
- **基线**: 全部 10 个子模块 surefire 报告（2026-09-04 12:21-12:22）0 failures / 0 errors；`./mvnw dependency:tree` 未运行（pom 直读分析，涉及时已标注"缺少工具基线"）
- **优先级口径**: `[P0]` 阻断（契约断裂/错误行为/数据丢失/安全/变更行为缺失测试）；`[P1]` 实质缺陷或契约漂移，必须修复；`[P2]` 非阻断修缮（文档行腐、措辞、命名、装饰性）

## 执行统计

| 维度 | 发现 | 复核结果 |
|------|------|---------|
| 01+20 依赖图/跨模块契约 | 6 | 保留（1×P2 其余P3级）；SPI 连接器契约零漂移 | 
| 02 模块职责/文件边界 | 5 | 保留（均 P3 级；超大文件均为固有复杂度，非问题） |
| 03 API 表面积/契约 | 4 | 1 项经复核升级 P1，1 项升级 P1（并入 Dim03-02），其余保留 |
| 09 错误处理 | 6 | 保留（1×P2 其余P3级；typed 体系完整，无 P0/P1 违规） |
| 10 XDSL/XLang | 3 | 1 项复核确认 P1（+新发现 root 级 =0 也被丢弃） |
| 13 安全 | 4 | 2 项升级 P1，1 项采纳 P1 侧裁定（与 Dim16-02 合并），1 项保留 P2 |
| 14 并发/checkpoint | 4 | 全部保留；1 项 P2 升级 P1（复核发现加重证据） |
| 15 类型安全 | 3 | 1 项复核确认 P1（爆炸半径精确化） |
| 16+21 测试覆盖/有效性 | 10 | 3 项 P1 保留（1 项与 Dim13-03 合并） |
| 18 文档-代码一致性 | 4 | 保留（1×P2 其余P3级；契约面大面积验证一致） |
| 04/05/06/07/08/11/12/17/19/22 适用性扫描 | 3 | 6 维度 N/A；05/08 正向通过；3×P3 级 |

**复核修正记录**（复核子 agent 对初审事实的修正，已并入下文）：
- R-08：`Path.of("/base", "/etc")` 前导 `/` 折叠为子路径，**绝对路径逃逸不成立**；仅 `../` 相对遍历成立（核心结论不变）。
- R-10：epoch manifest 的 SHA-256 **实际覆盖** inline `taskSnapshots`（含 `__java_bytes__` payload）且**先验后析**；无保护的面是主恢复路径 `.checkpoint` body（完全无 checksum）与 SST 段（restore 不复验内容 hash）。
- R-06：`META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory` **存在**（注册 `CheckpointExecutorFactoryImpl`）但全仓无任何 `ServiceLoader.load` 消费点 — 死配置，比初审"无 ServiceLoader 发现"更具体。
- R-02：RocksDB 增量 restore **依赖** task 本地 `cp-N/non-sst` 目录存在（`RocksDBIncrementalRestore.java:144`），故清理不能简单添加 — 是生命周期设计缺口；另发现 `LocalFileSegmentStore.storeSegment` 非原子直写终名 + coordinator `segmentExists` 短路（CheckpointCoordinator.java:743-745），崩溃可留下截断文件且永无检测。

---

## P1 发现（11 项，均经独立复核确认）

### F-01 [P1] checkpoint persist 失败路径悬挂 PendingCheckpoint future — savepoint/terminate 调用方阻塞满超时后收到误导性 TimeoutException
- **理由**: 公共 future 契约断裂 + 用户可见 API（savepoint/DRAIN/SUSPEND/EXPORT_SAVEPOINT）在存储故障时固定阻塞最长 10 分钟且报错掩盖真实根因 — 必须修复。
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:935-953`；`checkpoint/PendingCheckpoint.java:175-186`；`execution/GraphModelCheckpointExecutor.java:317-318,449-450`；`coordinator/JobCoordinator.java:2131-2132`
- **证据**: `onCompletePersistFailure` 直接 `pending.getStatus().set(Status.FAILED)`（:948，已直读确认），不调 `pending.fail(...)`；`PendingCheckpoint.fail()`（唯一 `completeExceptionally` 点）生产代码零调用方（rg 验证）；超时兜底 `abortPendingCheckpoint` 的 CAS `RUNNING→ABORTED`（:974）因状态已 FAILED 失败提前返回 — future 永不完成。三个阻塞调用方均 `future.get(checkpointTimeout)`（默认 600000ms）。
- **风险**: 每次存储写入故障（JDBC 不可达/磁盘满）→ savepoint/DRAIN/SUSPEND/EXPORT 全部阻塞满超时后抛 `TimeoutException`，真实故障只存在于 coordinator 日志；依赖 future 的 whenComplete 回调永不触发。
- **建议**: 段3b 改用 `pending.fail(reason, cause)`（等价状态转移 + 完成 future），补回归测试断言 persist 失败后 future 短时 exceptionally 完成。测试 `TestAsyncSnapshotPipeline.java:382-392` 注释自证作者已知该缺口（"不等待 future"绕过），属未登记 residual。
- **复核**: R-01 保留 P1（有界延迟、不损数据、内部簿记一致 → 非 P0）。

### F-02 [P1] RocksDB 增量 checkpoint 的 task 本地 `cp-N/{native,non-sst}` 目录无任何回收路径 — 无界磁盘增长，且 restore 依赖本地 non-sst 目录使清理成为设计缺口
- **理由**: 长运行增量 checkpoint 作业的 task 节点磁盘线性增长直至写满（含每次全量拷贝的 WAL/MANIFEST 双份），磁盘耗尽拖垮作业/节点 — 必须修复。
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:42-44,60-112`；`RocksDBKeyedStateBackend.java:120-123,724-725`；`CheckpointCoordinator.java:1423-1459`
- **证据**: 每次快照新建 `{dbPath}-checkpoints/cp-{N}/`（单调计数器）；non-SST 文件每 checkpoint 完整拷贝一份（:98-102）；全仓删除路径穷尽搜索：coordinator GC 只清共享 `shared-state/`，`StreamStateResetTool` 清的是另一棵树（checkpoint storage 布局），`close()` 只关句柄。javadoc（:42-44）自认"lifecycle owned by caller/coordinator"但两侧均未实现。
- **风险**: 磁盘耗尽 → checkpoint 失败/RocksDB 写失败雪崩。复核加重：restore 依赖本地 `cp-N/non-sst` 目录（`RocksDBIncrementalRestore.java:144`，coordinator 只物化 SST 句柄），故"补个删除"不可行，需先把 non-sst 伴生物持久化。
- **建议**: durable 持久化成功后回调 task 侧清理对应 cp 目录（先将 non-sst 内容入共享存储）；或 backend 内保留最近 K 个本地目录滚动清理。
- **复核**: R-02 由 P2 升 P1（缓解因素：增量模式当前仅测试启用，无生产接线 — blast radius 受限但功能已交付且有 fail-fast 校验与设计文档）。

### F-03 [P1] RocksDB 增量 restore 不校验物理文件完整性 — 损坏 SST/MANIFEST 以 untyped native 异常延迟爆发；伴生 `storeSegment` 非原子直写使截断文件可达且永无检测
- **理由**: 恢复路径违反模块自身"corrupt data fails fast as typed StreamException"不变式（TestRocksDBRestoreGuards 已立的基线在物理层失守），且存在进程内可造出损坏段的现实路径 — 必须修复。
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java:94-117,164`；`nop-stream-runtime/.../checkpoint/storage/LocalFileSegmentStore.java:38-49`；`CheckpointCoordinator.java:743-745`
- **证据**: restore 仅 `Files.exists`（:98）后直接拷贝，不复验内容 hash（写入侧已算 `SstFileChecksum.sha256Hex`，验证廉价）；`RocksDB.openReadOnly`（:164）未包 typed 包装；epoch manifest checksum 只覆盖 manifest JSON 文本，不覆盖 SST 字节。`storeSegment` 非原子直写终名 `{hash}.sst` + coordinator `segmentExists(hash)` 短路 — 崩溃半写留下永久截断文件。rocksdb 全部 17 个测试文件零物理损坏注入。
- **风险**: 坏盘/半写 → 恢复窗口内 untyped `RocksDBException`/native 崩溃，排障成本高；损坏段可能长期潜伏后才爆发。
- **建议**: restore 时对 segment 重算内容 hash 比对；`storeSegment` 改 temp+atomic-move；补截断 MANIFEST/翻转 SST 字节的 typed fail-fast 测试。
- **复核**: R-03 保留 P1（RocksDB 块级 CRC 使其非静默错数据 → 非 P0）。

### F-04 [P1] XDSL 节点级 `watermarkInterval` 声明被 builder 静默忽略 — quickstart 模板自证的"逐事件推进水位"承诺实际未生效（root 级 `=0` 亦被 `>0` 守卫丢弃）
- **理由**: schema 声明的调优旋钮有真实且不同的运行时语义（per-event vs 200ms 周期），声明即被静默丢弃 = 已交付 DSL 契约的静默违约 — 必须修复。
- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:124-126`；`nop-stream/nop-stream-flow/.../builder/AdvancedTransforms.java:476-497`；`nop-stream-core/.../datastream/DataStreamImpl.java:224-236`；`nop-stream/quickstart/template/src/main/resources/_vfs/quickstart/topology2-window-aggregation.stream.xml:30-33`、`topology3-cep-pattern.stream.xml:20-22`
- **证据**: xdef 将节点级 `watermarkInterval="!long=200"` 声明为必填带默认；`getWatermarkInterval()` 全部 4 个消费点均为 root/env 级链路（`StreamModelDslBuilder.java:157-158` → env；`DataStreamImpl.java:231`；`StreamGraphGenerator.java:368`），节点级字段零消费、零 fail-fast（对照同 builder 对 checkpoint 字段的非默认比较 fail-fast 纪律）。运行时语义确实不同：`TimestampsAndWatermarksOperator.java:110-127` — `0` → 逐事件发射，`>0` → 限频+周期 timer。quickstart 两拓扑声明 `watermarkInterval="0"` 并注释承诺逐事件推进，实际生效 env 默认 200ms。
- **风险**: 声明的 per-node 水位节奏静默失效；窗口触发时机/迟到分类/CEP within 语义与声明不符（有界演示因 `MAX_WATERMARK` 兜底结构性假绿）。
- **建议**: 对齐 builder 自身策略 — 非默认值 fail-fast（同 `barrierAlignmentTimeout` 模式）或实现带 interval 的重载接线；同步修 root 级 `>0` 守卫吞掉 `=0` 的问题；更新 quickstart 注释与 user guide。
- **复核**: R-01' 保留 P1，确认无任何补偿路径（`TestWatermarkIntervalConfig` 只测 env 级）。

### F-05 [P1] 窗口 apply/process/evictor/reduce 路径 ListState 元素类型恒为 `Object.class` — bean 元素在 RocksDB 后端运行期即 CCE、Memory 后端 JSON checkpoint 恢复后错型（ACC 有三层守卫，IN 路径零守卫）
- **理由**: 公共 DataStream API（`WindowedStream.apply/process/evictor/reduce`）对 bean 元素确定性崩溃或恢复后类型污染 — 已支持路径上的确定性错误行为。
- **文件**: `nop-stream-core/.../datastream/WindowedStreamImpl.java:186-241`；`nop-stream-runtime/.../windowing/WindowOperatorBuilder.java:116-117,163-179`；`nop-stream-rocksdb/.../RocksDBValueSerDe.java:80-95`；`nop-stream-core/.../MemoryStateSerDe.java:183-190,206,452-478,799-813`；`WindowOperator.java:976-1025,1441-1443`
- **证据**: 4 个调用点均传 `(Class<T>)(Class<?>) Object.class`；`RocksDBValueSerDe.deserializeList` 对 `Object.class` 保留 JSON-native（bean → LinkedHashMap），`RocksDBListState.get/add` 每次往返（add 也 round-trip 旧元素 → RocksDB 后端立即坏，非仅恢复后）；`ContainerValueCodec` 只覆盖 MapState（`writeMapPayload:206` 包装、`writeListPayload:183-190` 不包装）；`registerRestoreElementType` 全仓零命中。爆炸半径（复核精确化）：RocksDB 后端 bean IN 在 apply/process/evictor-aggregate/reduce 首次窗口触发即 CCE；Memory 后端活路径 OK、JSON 持久化恢复后坏；**非 evictor aggregate 安全**（IN 立即折叠进被推断的 ACC）。
- **风险**: 用户函数内 CCE 或 LinkedHashMap 静默条件求值错误；指纹校验不拦截（valueType 稳定 `java.lang.Object`，"恢复成功"）。仓库内示例/测试均用标量 → 对 bean 用户为潜伏陷阱。
- **建议**: 复制 ACC 方案到 IN（工厂层从流入元素/TypeInformation 推断并写入描述符），或为 ListState 快照引入 ContainerValueCodec 包装；至少 `deserializeList` 收到 Object.class 且元素为 Map 时 warn（对齐 P1-21-01 No-Silent 规则）。
- **复核**: R-02' 保留 P1（失败响亮非静默腐化、旗舰 aggregate 路径受护 → 非 P0）。

### F-06 [P1] `enableCheckpointing()` 在 checkpoint 执行器工厂未接线时静默跳过 checkpoint — 用户指南文档化路径在默认 LOCAL 模式下静默失效；META-INF/services 注册文件存在但零 ServiceLoader 消费（死配置）
- **理由**: 官方文档化的配置入口（`enableCheckpointing` / XDSL `<checkpoint>`）在实现 jar 已在 classpath 的默认形态下被静默忽略，无警告、下游报错误导 — 必须修复。
- **文件**: `nop-stream-core/.../environment/StreamExecutionEnvironment.java:75,104,146-148,296-301,310-314,380-424`；`nop-stream-runtime/src/main/resources/META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory`；`docs-for-ai/03-modules/nop-stream-user-guide.md:22-31,39,82`
- **证据**: 门控 `isCheckpointEnabled() && checkpointExecutorFactory != null`（:296，已直读确认）静默落入无 checkpoint LOCAL 执行；该文件零日志。静态 setter（:146）main-scope 调用方仅 quickstart 脚手架与测试；services 文件存在但全仓无 `ServiceLoader.load(ICheckpointExecutorFactory...)`（自动发现意图明确、加载侧从未实现）。同文件内 DISTRIBUTED 无 dispatcher fail-fast（:310）、三个 savepoint API null factory fail-fast（:380-424）— 唯独此门静默。用户指南快速起步（:22-31）直接 `enableCheckpointing(60_000)` 且全指南零字提及工厂。
- **风险**: 用户以为有 exactly-once/状态持久化，实际零 checkpoint（进程故障即丢状态）；keyed ProcessFunction 作业报误导性 "Keyed state is only available on a keyed stream"，WindowOperator 则自备内存后端零持久化运行。
- **建议**: 实现 ServiceLoader 消费已有 services 文件，或在门控处 fail-fast/warn。
- **复核**: R-03' 由 P2 升 P1（"quickstart 已接线"辩护被驳 — 用户指南主路径不接线不提及；失败是容错缺失而非错结果 → 非 P0）。

### F-07 [P1] 10 个多 JVM 真实集群 gated 测试在 CI 永久跳过，而 docs-for-ai 引用其为能力"已证明"锚点且不记录启用方法
- **理由**: 分布式 exactly-once/fencing/coordinator-failover 是产品核心主张，其唯一真实进程验证载体在默认验证门禁中不存在（验证自动化缺失）— 必须修复。
- **文件**: `nop-stream-fraud-example/.../scenario/TestParallel2PcMultiJvmE2E.java:79` 等 6 类 + `nop-stream-runtime/.../multijvm/` 4 类（均 `@EnabledIfSystemProperty(named="nop.stream.test.multi-jvm.enabled")`）；`.github/workflows/maven.yml:43`；`docs-for-ai/04-reference/source-anchors.md:268`、`03-modules/nop-stream-user-guide.md:149`、`nop-stream-connectors.md:81`
- **证据**: 全仓 3 个 workflow（maven/compliance/sync-docs）无 `schedule:`/nightly、无属性启用（复核确认）；`rg "nop.stream.test" docs-for-ai/` 0 命中（docs-for-ai 无启用文档；`fraud-example/README.md:104` 与 `distributed-runbook.md:65-69` 有但属 ai-dev/示例侧）。
- **风险**: 真实多进程故障注入路径（SIGTERM 杀 TM、JDBC HA 接管、进程间 fencing）行为回归无机器门禁感知；文档能力声明所引证据链在 CI 中不可复现。
- **建议**: CI 增加 nightly/低频 lane 启用该属性；或 docs 将"真实多 JVM 已证明"降级为"手动验证"并记录启用命令。人工实跑证据在案（复核确认 2026-09-03/04 多次绿），故为验证自动化缺失而非证据伪造 → 非 P0。
- **复核**: R-04' 保留 P1。

### F-08 [P1] `StreamStateResetTool.reset` 的 jobId 无任何校验即递归删除 — `jobId=../other-job` 静默删除兄弟作业全部 checkpoint 状态（姊妹类有全套防护+专测，本工具双缺）
- **理由**: 破坏性且不可恢复的数据丢失路径（一次 CLI 笔误即触发），违背工具自身"typo must not succeed silently"契约（javadoc :32-33）— 必须修复。
- **文件**: `nop-stream/nop-stream-runtime/.../maintain/StreamStateResetTool.java:85-118`；对照 `checkpoint/storage/LocalFileCheckpointStorage.java:56,325-340`；`maintain/StreamMaintenanceMain.java:82-93`；`TestStreamStateResetTool.java`（5 测试无穿越用例）
- **证据**: `Path.of(checkpointBaseDir, jobId)` 仅 blank 检查后 `deleteRecursively`（:108-118）；姊妹类同目录布局有 `SAFE_ID_PATTERN=[a-zA-Z0-9_-]+` + canonical `startsWith` 双防护并有 `TestLocalFileCheckpointStoragePathTraversal` 专测。CLI kv 原样透传。owner doc（nop-stream.md:125-132）将 `reset(...)` 记录为文档化编程 API（非仅 CLI）。**复核修正**: 绝对路径逃逸不成立（前导 `/` 折叠），`../` 相对遍历完全成立。
- **风险**: 合法存储的 job 目录必经 SAFE_ID_PATTERN 写入 → 非合规 jobId 永不对应本作业状态，但可命中**其他**路径；若被删作业 source 不可重放即不可恢复数据丢失，且该作业不享有任何 fail-fast 守卫。
- **建议**: 复用 storage 侧两段校验；补 `refusesDotDotJobId` 测试。零合法用例损失。
- **复核**: R-05' 采纳 P1 侧裁定（operator 信任域 → 非 P0）。

### F-09 [P1] Ops REST 全端点零认证 + `POST /jobs` 请求体 FQCN 经 `Class.forName`（initialize=true）在接口检查前执行任意类静态初始化器，官方模板引导绑定 0.0.0.0
- **理由**: 信任边界穿越（HTTP 请求体 → 进程内任意类加载）叠加零认证原语、零 ClassNameValidator 纪律、模板主动引导跨机暴露 — 必须修复。
- **文件**: `nop-stream-runtime/.../ops/OpsJobManager.java:191-197`（已直读确认）；`ops/StreamOpsHttpServer.java:264-293`；`ops/StreamOpsConfig.java:26-28`；`_vfs/nop/stream/conf/metrics.properties.template:14-18`
- **证据**: 单参 `Class.forName(factoryClass)`（:191，initialize=true）先于 :192 接口检查执行任意静态初始化器（classpath 含 debezium/rocksdb/micrometer/JDBC 驱动）；全 server 零认证原语（rg token/auth/Authorization 仅命中 Accept 头注释）；模板"跨机采集时改为 0.0.0.0 并确保网络访问受控"仅为注释警示，产品内无任何机制承接；OpsJobManager 不用 ClassNameValidator 而同库所有持久化数据反射加载点均用（纪律不对称）。
- **风险**: 端点可达（0.0.0.0 配置/同机进程）即 RCE（静态初始化器/工厂构造）+ 作业停止 DoS + threaddump/指标泄露。默认 disabled+127.0.0.1 限制触达概率。
- **建议**: 工厂类名过 ClassNameValidator + `Class.forName(name, false, loader)` 先检查后加载；bind≠127.0.0.1 时强制最小 token 认证；模板补硬约束。
- **复核**: R-06' 由 P2 升 P1（REST 调用方按设计非 operator — 端点有结构化坏输入错误码；"模板引导进入脆弱配置 + 零可用认证原语"组合的实害被 P2 低估 → 非 P0，因默认关闭）。

### F-10 [P1] 原生 Java 反序列化无 JEP 290 过滤器直达 `ObjectInputStream.readObject`，主 checkpoint 恢复路径（`.checkpoint` body）无任何完整性保护 — 绕过平台自身 ClassNameValidator 白名单纪律的唯一持久化数据→对象实例化通道
- **理由**: 跨信任边界（checkpoint 存储写权限集 > operator 集：DBA/同库应用/备份还源/SQL 注入横移）的反序列化 gadget RCE 面，且为平台既有防线（全库白名单纪律）的唯一旁路 — 标准且廉价的 JEP 290 修复缺位 — 必须修复。
- **文件**: `nop-stream-core/.../typeutils/JavaStreamSerializer.java:94-105`；`MemoryStateSerDe.java:799-813`；`CheckpointSerDe.java:85-179,286-292`；`nop-stream-runtime/.../JdbcCheckpointStorage.java`（共享 CheckpointSerDe）
- **证据**: 裸 `readObject()` 无 filter；全仓 `rg ObjectInputFilter` 0 命中（已直读复核）；`__java_bytes__` base64 marker 与裸 `byte[]` 两分支直达 readObject。**复核修正后的精确事实**: epoch manifest checksum 覆盖 inline payload 且先验后析（真实补偿，防损坏）；但主恢复路径 `.checkpoint` body（`restoreFromCheckpoint:1017` → `deserializeCheckpoint`）**完全无 checksum**，SST 段字节无恢复期验证；manifest hash 为无密钥 SHA-256（可重算，不防主动篡改）。
- **建议**: `JavaStreamSerializer.deserialize` 加 `ObjectInputFilter`（白名单与 ClassNameValidator 对齐）；`.checkpoint` body 增 checksum/MAC，实现 checksum-before-deserialize。
- **复核**: R-07' 由 P2 升 P1（单租户本地部署下信任域≈operator 为削弱因素，但 JDBC 共享存储使其成为现实横移路径 → 非 P0）。

### F-11 [P1] Owner doc 运维手册的分布式模式命令（`JobCoordinatorMain`/`TaskManagerMain`）仅存在于 test-jar，owner doc 不携带该限制 — 权威 runbook 与发布产物之间契约漂移
- **理由**: 运维人员按权威速查用发布 runtime jar 拉起 JC/TM 直接 `ClassNotFoundException`；ops 端点/TM 指标暴露在真实部署中实际不可达 — 两份用户面文档契约不一致且操作失败模式真实。
- **文件**: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/launch/JobCoordinatorMain.java`、`TaskManagerMain.java`（test scope）；`docs-for-ai/03-modules/nop-stream.md:90-91,112,231,254`；`docs-for-ai/03-modules/nop-stream-user-guide.md:174`（唯一披露处）
- **证据**: 全仓 main-scope `public static void main` 仅 `StreamMaintenanceMain`、`FraudDetectionDemo`、quickstart 模板；user guide 明言"生产部署如需独立进程入口，当前需消费该 test-jar 或自建 launch 类"，owner doc 运维手册照常给命令且自称权威速查。
- **风险**: runbook 主命令在生产 jar 上不可用；「main-scope 启动入口」follow-up 未落地前，P-REQ-3/5 的 TM 侧启用路径（`opsHttpPort` launch 参数）对生产用户不可达。
- **建议**: owner doc runbook 首行补 test-scope 限制与 workaround（与 user guide 对齐），或推动 main-scope 启动入口落地。
- **复核**: 事实经 Dim03 初审全仓穷举验证（未单列复核 agent；证据自足）。

---

## P2 发现（33 项，非阻断；按主题分组）

### 代码/健壮性

- **F-12 [P2]** runtime 对 `nop-dao` 用 provided 作用域但 3 个 main 类直接 import（`JdbcCheckpointStorage`/`JdbcClusterRegistry`/`JdbcLeaderElector`），与 connector-jdbc 的 compile 不对称 — 下游启用 JDBC 存储未手工补依赖时运行期 NoClassDefFoundError（`nop-stream-runtime/pom.xml:81-85`）。理由: 条件性运行期失败模式，需统一裁定。
- **F-13 [P2]** Ops 提交错误分类依赖异常消息子串 `"already hosted"`（`StreamOpsHttpServer.java:290`，已直读确认；消息源 `OpsJobManager.java:116`），服务端 ISE 会被误报 400 而非 500 — 消息重构即让 409 契约静默退化；同表面 9 处裸 IAE/ISE 属 tier-1 表面用 tier-2 手段。理由: 契约脆弱性（当前行为正确）。
- **F-14 [P2]** `CheckpointBarrierTracker.getCurrentCheckpointId()` 无锁迭代非线程安全 `LinkedHashMap inFlight`（`nop-stream-core/.../CheckpointBarrierTracker.java:66,283-290`）— 当前零生产调用，一旦接入控制面即 CME 风险。理由: 潜伏竞态，一行修复。
- **F-15 [P2]** 分布式 abort handler 持 coordinator monitor 扇出阻塞 `cancelTask` RPC（`CheckpointCoordinator.java:1004-1010` + `JobCoordinator.java:2258-2259`）— 慢 TM 可把 checkpoint 协调停摆拉长到 RPC 超时×节点数（无死锁，与既定取舍一致）。理由: 控制面延迟窗口加固项。
- **F-16 [P2]** `WindowOperatorFactoryImpl` 两处静默吞异常（:68-79 类型推断回退无日志、:165-171 `createInstance` 静默 null）且 `inferAccumulatorType` 三份实现行为漂移（工厂副本静默、恢复期副本 LOG.warn）+ 构建期采样 `createAccumulator()` 使 schema 指纹依赖用户函数确定性 — 非确定性时恢复报 SCHEMA_MISMATCH。理由: 同族场景两种标准 + 指纹脆弱性。
- **F-17 [P2]** `FileTwoPhaseCommitSink.deleteIfExistsQuiet` 完全吞 IOException 无 rethrow 无 LOG（`nop-stream-connector/.../FileTwoPhaseCommitSink.java:476-483`）— 违反"丢弃异常前必须留证"。理由: 留证缺口（提交后清理路径，影响小）。
- **F-18 [P2]** `RocksDBIncrementalRestore` 将任意 `RocksDBException`（含 MANIFEST 损坏/IO 错）一律解释为"仅默认列族"（`RocksDBIncrementalRestore.java:153-158`）— 非默认列族状态可能被静默丢弃。理由: 恢复路径留证缺口。
- **F-19 [P2]** `TypeSerializer<T>` 契约为死 API 且实现自相矛盾（`copy` 恒等、`isImmutableType` 两默认实现相反声明、dummy serializer 对未知类型谎称不可变、主代码零调用点，`TypeSerializer.java:54-115`、`WindowOperatorFactoryImpl.java:150-189`）— 撒谎的公共 API 是未来别名共享 bug 陷阱。理由: 无人调用的误导性契约。
- **F-20 [P2]** `<custom>` 的 `<source>` xpl 函数体在 xdef 声明但 builder 既不消费也不 fail-fast；`bean` 属性在 custom 上同样失效（`stream.xdef:188-193`、`AdvancedTransforms.java:374-408`）— 写内联 xpl 被静默丢弃，同 builder 自身 fail-fast 纪律漏网。理由: 与 F-04 同类的窄面声明-消费缺口。
- **F-21 [P2]** 测试夹具 `CollectionReplayableSource` 置于生产源码集（`nop-stream-runtime/src/main/java/.../source/CollectionReplayableSource.java`，消费者全在 test）— 随生产 jar 发布无持久化语义的内存源。理由: 误用风险与 API 面噪音。
- **F-22 [P2]** 死代码两处：`TaskAssignmentMessage`（全仓零引用的旧控制 topic 遗物，`runtime/coordinator/TaskAssignmentMessage.java:21-31`）与 `NopCepConstants`（零引用常量接口，`cep/NopCepConstants.java:10-13`）。理由: 协调器热区误导 + 序列化面无谓扩大。
- **F-23 [P2]** `GlobalKeyedStateStore` 与 `PerWindowKeyedStateStore` 逐行克隆，唯一差异是固定 namespace（`WindowOperator.java:1675-1761`）— 可由带参构造表达，未来 namespace 语义分叉风险。理由: 内部类克隆维护险。
- **F-24 [P2]** 跨子模块同名类簇：`core.model.StreamModel` vs `flow.model.StreamModel`（完全不同抽象，生成头注释自称 canonical 互相冲突）+ `core State` vs `cep nfa State`、`core TimerService` vs `cep TimerService`（javadoc 已自认碰撞）。理由: 跨模块阅读混淆（当前无同文件双导入）。
- **F-25 [P2]** `/jobs/{jobId}/threaddump` 解析后完全忽略 jobId（任意/不存在 jobId 均 200 全进程线程栈，`StreamOpsHttpServer.java:199-206,347-359`）与"未知作业 404"总则形成未注明例外，与 F-09 组合时是未认证信息泄露点。理由: 契约读者预期不一致（进程级语义本身是刻意的）。

### 构建/BOM

- **F-26 [P2]** runtime pom Item 19 注释宣称 connector/connector-jdbc "already main deps"，实际两者已降 test scope（Stage 49/52）— 同文件两处注释互相矛盾（`nop-stream-runtime/pom.xml:63-67` vs `143-161`）。理由: 依赖契约事实被旧注释证伪。
- **F-27 [P2]** nop-bom 存在幽灵条目 `nop-stream-api`/`nop-stream-checkpoint`（仓库无此模块，`nop-bom/pom.xml:1221-1224,1292-1295`；module-groups.md 明文否认存在）— 拼写错误在 dependencyManagement 层静默通过。理由: BOM 卫生 + 跨文档矛盾。
- **F-28 [P2]** nop-bom 未覆盖 5/10 个 nop-stream 子模块（rocksdb/connector-{batch,jdbc,debezium}/fraud-example）及 `nop-message-kafka`，消费方被迫 `${project.version}` 字面量；rocksdbjni 9.11.2 为 nop-stream 内唯一硬编码三方版本。理由: BOM 单一版本源契约对 nop-stream 仅覆盖一半。
- **F-29 [P2]** cep 的 guava 无版本声明，版本经 nop-dependencies→quarkus-bom 链裁决（`nop-stream-cep/pom.xml:26-29`）— 平台升级静默移动 CEP 热路径缓存原语版本（缺少 dependency:tree 基线，结论为排除法推断）。理由: 版本归属漂移风险。

### 文档漂移

- **F-30 [P2]** `docs-for-ai/INDEX.md:235` nop-stream 子模块清单漂移：缺 `rocksdb`/`connector-jdbc`，`flow` 被错误描述为"流控"（实为 XDSL 声明式编排；module-groups.md 与 owner doc 均正确）— INDEX 是 authoritative navigation baseline。理由: 导航基线行腐。
- **F-31 [P2]** `NopStreamErrors` 全部 84 个 ErrorCode 描述为英文且有测试强制（`TestErrorCodeMessagesEnglish`），但 `docs-for-ai/02-core-guides/error-handling.md` 英文例外清单未收录 nop-stream — 遵循文档的 AI 会"修复"为中文并破坏 `getMessage()`/failureCause 语义。理由: 文档-代码权威性冲突（有测试保护，代码是有意为之）。
- **F-32 [P2]** 设计文档模块清单滞后：`ai-dev/design/nop-stream/01-architecture-baseline.md:14-44`（active 状态、含 2026-09-01 更新）模块树仅 6 项、runtime 依赖表缺 flow 等 4 项、方向规则仍引用已并入 core 的 checkpoint/api 历史模块名；`README.md:73-96` 缺 3 模块。理由: 后续 AI/开发者按基线会误判合法依赖边。
- **F-33 [P2]** Owner doc `GET /jobs` 成功响应字段表缺代码实际返回的 `health` 字段（doc:116 vs `StreamOpsHttpServer.java:252-262`；详情行 L119 已有）— 纯增量、复用同一序列化器。理由: 文档遗漏非越权添加。
- **F-34 [P2]** User-guide trigger 家族"10 文件"计数过时（实际 11，多出 package-private 抽象基类 `ContinuousIntervalTrigger`；公开类型表仍准确）。理由: 文件计数行腐，会制造假阳性审计。
- **F-35 [P2]** fail-fast delta fixture 注释宣称 `UnsupportedOperationException`，实际抛 `StreamException(ERR_STREAM_NOT_IMPLEMENTED)` 且测试断言后者（`test-delta-failfast-extends.stream.xml:7-10`）。理由: 注释漂移无行为影响。
- **F-36 [P2]** 仓库唯一永久 `@Disabled` 测试 `TestDebeziumCdcSourceCompletion`（skip 理由合理且已文档化）仍被 `docs-for-ai/03-modules/nop-stream-connectors.md:111` 列为 Debezium 恢复验证锚点，未标注 disabled；`cancel() 后 run() 退出`的真实合同无替代测试。理由: 文档锚点失真。
- **F-37 [P2]** `_module` VFS 标记仅在 nop-stream-runtime，而 4 个 connector 子模块共同向 `/nop/stream/` 贡献资源 — 独立部署 connector 而无 runtime 时模块发现行为不同（所有权属偶然）。理由: 一致性缺口，无实际故障场景实证。
- **F-38 [P2]** Flink 移植模块（core/runtime/cep 大文件）import 顺序为 java-first，与仓库规范（io.nop→jakarta→三方→java）及 flow 模块并存两套惯例。理由: 建议文档化例外而非重写 ~90K 行移植代码。

### 测试质量

- **F-39 [P2]** Ops `/metrics` 的 `METRICS_DISABLED` 显式 404 分支（代码注释自声明契约"Explicit-off, not silent empty output"）无测试（`metricsEnabled` 全测试树零引用）。理由: 自声明设计契约未钉定。
- **F-40 [P2]** stop-during-RECOVERING 的 409 `JOB_STATE_CONFLICT` HTTP 映射无 ops 层测试（状态机层拒绝已测；该错误码全仓仅此一处 main 命中）。理由: 薄映射层契约未钉定。
- **F-41 [P2]** `StreamMaintenanceMain` 的 reset-state/reshard 子命令分发与 kv 参数解析零测试（仅 usage 文本被断言）；`sourceReplayable=ture` 之类拼写错误静默变 false 走拒绝分支且报错文案误导。理由: CLI 装配层 exit-code 契约与 conf-validate 不对等。
- **F-42 [P2]** `testFencing_OldAttemptRejected` 名不副实：死变量 token + 用 unknown checkpointId 冒充 fencing（`TestDistributedExactlyOnce.java:553-598`）— 删掉真 fencing 逻辑测试仍过（真 fencing 已被其他强测试覆盖，属冗余误导）。理由: 命名与内容不符的假覆盖。
- **F-43 [P2]** 27 处裸 `assertThrows(Exception.class)`（jdbc 5 处完全裸断言，batch 5 处有 message 断言半有效）— 能证明"抛了异常"不能证明"抛了正确异常"。理由: 防御路径负面测试降级但非零价值。
- **F-44 [P2]** 33 处/20 文件 P-1 类 getter/setter 往返测试已自标 `@Tag("low-value")` 但既不删除也不过滤，仍占每次 CI — 其中有行为价值的用例（不可变性断言）混在 `testGet*` 命名里易被误清理。理由: 已识别未收敛的测试卫生。

---

## 契约面验证一致清单（正向保证，审计核验通过）

以下经逐项核验**与 owner docs/设计文档一致，无漂移**（摘要，详细证据在维度底稿）：

- **指标名表**: owner doc 五层 24 个指标名（engine 7/task 4/operator 3/io 4/state 6）与代码注册点逐名逐层一致，含标签、D2b 仅激活订阅注册、D2c 重绑/释放语义；main scope 无未记载 meter。
- **REST 六端点**: 方法/路径/错误码/状态码（404/400/409、TM 503 家族、RECOVERING 409）、结构化错误体、`JobSubmissionSpec` 字段、fail-fast 无 trivial 回落、201/409 语义全部一致。
- **配置键**: `nop.stream.ops.http.*`、`nop.stream.metrics.log.*`、告警 5 键、治理 4 键全部实现且默认值逐值一致（8901/127.0.0.1/false/true/5000/2/200ms/100/1440/1440/300000）。
- **事件契约**: `StreamJobEvent` 10 事件类型全部从真实生命周期路径发射，负载字段一致；告警路由与日志锚点一致。
- **CLI**: `StreamMaintenanceMain` 四子命令参数名/exit code（0/1/2）逐字一致；`StreamStateResetTool` 三条拒绝语义一致。
- **凭据**: `credential:{id}#{field}` 语法、fail-closed 三错误码、CDC 瞬态解密语义一致；全模块无凭据明文日志。
- **Checkpoint 版本/校验**: `CheckpointFormatVersions`（CURRENT=2/LEGACY=1）、manifest checksum 咽喉写入/先验后析、legacy 容忍、两个 typed 错误码携带完整 param。
- **SPI 连接器契约**: 8 个工厂 TYPE_NAME、5 个 beans.xml 载体、能力矩阵、`META-INF/services` 方向全部一致，零漂移。
- **安全基线**: JDBC 面全参数化、LocalFileCheckpointStorage 路径防护完整、bean 解析器无按类名实例化、webhook scheme 校验、fencing 纪律、默认安全姿态（disabled+loopback）。
- **XDSL**: 26 个 `.stream.xml` x:schema 引用全部正确；checkpoint 13 属性全消费；edge/窗口/CEP fail-fast 完备（除 F-04/F-20 两处）；conf-validate 三层与文档一一对应；`_gen` 与 stream.xdef 字段级一致（19 属性含默认值）；quickstart 脚手架链路验证类/方法签名全部存在。
- **并发设计**: fencing epoch 统一、两阶段提交顺序、恢复互斥、多 pending 正确性、async persist 三段式、InputGate epoch-precise abort、zombie fail-loud、slot 替换原子性、资源管理/中断纪律 — 对照 checkpoint-design.md/failover-design.md 逐不变式核验正确（F-01 除外）。
- **测试基线**: 全模块 0 failures；CEP（58 文件）/checkpoint 失败路径/failover/JDBC 2PC/Debezium offset/ops 契约钉定覆盖"强"~"很强"；P-5/P-7 类高害测试反模式几乎绝迹。
- **模块边界**: 依赖图严格无环、分层规则全部成立、6 组反向 import 扫描（main+test）零命中、`_gen` 干净、fraud-example 单向、无 Spring/Quarkus 泄漏、版本一致（${project.version}/BOM）。

## 总评

nop-stream 的工程质量在同类引擎项目中处于高位：模块边界与依赖纪律严格、typed 错误体系完整、契约面（指标名/REST/配置键/SPI/版本）与文档几乎逐字一致、并发设计有大量带注释与测试锚点的先行修复。本次 44 项发现中无 P0；11 项 P1 集中在四条主线：(1) **故障路径收尾**（future 悬挂、磁盘增长、物理完整性）；(2) **声明-消费失配的静默违约**（watermarkInterval、custom source、enableCheckpointing 工厂接线）；(3) **信任边界加固**（ops REST 认证/类加载、反序列化 filter、reset 工具路径校验）；(4) **验证自动化缺口**（多 JVM gated 测试不在 CI）与 **runbook-产物漂移**。33 项 P2 为文档行腐、BOM/命名卫生与测试修缮，可随后续维护批量清理。

## 优先修复建议

1. **F-01**（一行修复 + 回归测试）：persist 失败路径改 `pending.fail(...)` — 消除 savepoint/terminate 10 分钟级误导性阻塞。
2. **F-08 + F-09 + F-10**（安全三件套，均可低成本收敛）：reset 工具复用 SAFE_ID_PATTERN；ops 提交走 ClassNameValidator + 延迟初始化 + bind≠loopback 时强制 token；JavaStreamSerializer 加 JEP 290 filter。
3. **F-06**（消费既有死 services 文件或 fail-fast）：让文档化的 `enableCheckpointing` 恢复其实际含义。
4. **F-05 + F-04**（DSL/类型面）：IN 元素类型推断对齐 ACC 方案；watermarkInterval 非默认 fail-fast 或接线。
5. **F-02 + F-03**（增量 checkpoint 生命周期）：non-sst 持久化 + 本地目录滚动清理 + restore 期 hash 复验 + storeSegment 原子化。
6. **F-07 + F-11**（验证与文档对齐）：multi-JVM nightly lane 或文档降级；runbook 补 test-jar 限制。
7. P2 项按 F-30（INDEX.md）、F-31（error-handling.md 例外清单）、F-27/F-28（BOM 卫生）顺序顺手清理。

## 审计盲区自评

- `./mvnw dependency:tree` 与全量 `./mvnw test` 未在本审计内重跑（依赖结论基于 pom 直读 + 既存绿色 surefire 基线；guava 版本归属为排除法推断，已标注）。
- 算子数据面热路径（WindowOperator 2208 行、StreamTaskInvokable 1067 行）只做了结构级与并发级审查，未做逐行语义对照 Flink 原版。
- quickstart 脚手架（generate.sh/verify.sh）未实际执行验证。
- CEP NFA 语义（1109 行 NFACompiler）信任其 58 个测试文件的强覆盖，未独立重验算法正确性。
- 性能/背压行为（emit.time 抬升、channel queue 水位）仅静态审查语义，未负载实测。
- P2 项未逐条派发独立复核（仅 P1 与争议项经 3 个复核 agent 逐条重验）；P2 证据均来自维度初审 agent 的文件级引用。

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
