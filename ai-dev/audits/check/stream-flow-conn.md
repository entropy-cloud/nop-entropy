# stream-flow-conn 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-stream/{nop-stream-flow,nop-stream-connector,nop-stream-connector-batch,nop-stream-connector-jdbc,nop-stream-connector-debezium,nop-stream-fraud-example}
- 文件数: 约 143（src/main/java）
- 覆盖范围声明:
  - 实际逐文件深读了 6 个目标模块 src/main/java 下全部 69 个非生成源文件（任务所述 143 含 `_gen/` 生成文件与 target 产物，按规则排除；nop-stream-flow 的 model 包手写类均为继承 `_gen._XxxModel` 的空壳，逻辑在生成代码中，属范围外）。
  - 为验证两阶段提交状态机、多 subtask 部署模型与 region restart 生命周期，参照读取了范围外的 nop-stream-core（TwoPhaseCommitSinkFunction、StreamSinkOperator、StreamSourceOperator、OperatorChain、GraphExecutionPlan）与 nop-stream-runtime（CheckpointCoordinator、GraphModelCheckpointExecutor、SupervisionLoop）中的关键代码，仅作证据链支撑，未对这些模块本身展开审计。
  - 测试代码不在范围；ai-dev/ 历史记录未读取。
  - D5（SQL 拼接/凭证）专项检查：JDBC sink 所有值经 PreparedStatement 参数绑定，表名/列名经 `IDialect.escapeSQLName` 转义，无字符串拼接值；目标模块无硬编码凭证、无 IoC 注解（D7 无 private 注入/beans.xml 违规可查）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 2 |
| P1 | 3 |
| P2 | 4 |
| P3 | 6 |

## 发现列表

### [P0] 2PC 连接器 sink 无 per-subtask 隔离：parallelism>1 时 pendingCommits 互相覆盖导致批次数据丢失

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:172-182`、`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:131-142`
- **维度**: D1（+D3、D8）
- **证据**（JDBC sink，File sink 同构）:
```java
public TaskStateSnapshot saveState(long epochId) throws Exception {
    ensureInitialized();
    synchronized (currentBuffer) {
        if (!currentBuffer.isEmpty()) {
            List<Map<String, Object>> batch = new ArrayList<>(currentBuffer);
            getPendingCommits().put(epochId, batch);   // 同一 epochId，多个 subtask 各 put 一次，后写覆盖先写
            currentBuffer.clear();
        }
    }
    return super.saveState(epochId);
}
```
File sink 在同一方法中还会先 `writeLines(tempPath(epochId), ...)` 覆盖写同一个 `.epoch-{id}.tmp` 文件再 `put`。
- **现状**: 运行时在 parallelism>1 时为每个 subtask 生成独立的 operator 副本但**共享同一个 userFunction 实例**（`OperatorChain.deepCopy`，nop-stream-core `OperatorChain.java:231-248` 注释明确 "sinks ... are shared across subtasks"；`GraphExecutionPlan.java:377` 对每个 taskIndex 调 `deepCopy()`）。每个 subtask 的 `StreamSinkOperator.processBarrier` 都会对同一 epochId 调用共享 sink 的 `saveState(epochId)`（`StreamSinkOperator.java:78`），`pendingCommits.put(epochId, ...)` 以 epochId 为单键，后处理的 subtask 覆盖先处理 subtask 的批次。File sink 中两个 subtask 还写同一个 tempPath（`.epoch-N.tmp`），后写截断先写。此外 `JdbcTwoPhaseCommitSink.invoke`（第 150-160 行）对共享 `currentBuffer`（非线程安全 ArrayList）无锁 `add`，与 saveState/rollback 的 `synchronized(currentBuffer)` 不一致，多 subtask 并发 invoke 有丢失元素/损坏风险。
- **风险**: 只要 sink 并行度 > 1（DSL `parallelism` 属性/env 并行度即可触达），每个 checkpoint epoch 只有最后写入 pendingCommits 的批次被 commit，其余 subtask 的数据被静默丢弃——直接违背 exactly-once 承诺。JDBC ledger 表主键也只有单列 `epoch_id`（`JdbcTwoPhaseCommitSink.java:306-308`），无 subtask 维度，多 subtask 同 epoch commit 还会撞主键。
- **建议**: 在 sink 中引入 subtask 维度（key 用 `subtaskIndex:epochId`，ledger 表加 subtask 列），或在 sink 内部为每条输入附带来源 subtask 隔离缓冲；`invoke` 与 saveState/rollback 使用同一把锁。
- **误报排除**: 已核实运行时确会为同一 vertex 构建 parallelism 个 subtask 且共享 udf（GraphExecutionPlan + OperatorChain.deepCopy + StreamSinkOperator.copyForSubtask 三处源码）；barrier 经各 subtask 输入通道独立触发，非单次调用。非误报。

### [P0] 连接器 source 的 run() 不重置 running/failed 标志：region restart 复用实例后 source 静默 EOS、数据流停摆

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:170`、`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:145`、`nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:67`
- **维度**: D1（+D8）
- **证据**（三个类的 run() 入口均未重置标志，以 Debezium 为例）:
```java
public void run(SourceContext<ChangeEvent> ctx) throws Exception {
    if (!runEntered.compareAndSet(false, true)) return;
    this.draining = false;          // 只重置了 draining
    initCompletionLatch();
    ...
    while (running && !draining) {  // running 在旧实例被 cancel() 后已是 false，且 serializable 字段跨复用保留
        ...
    }
}
```
`MessageSourceFunction.run` 的 `while (running && !failed)` 同理（`failed` 也不重置）；`BatchLoaderSourceFunction.run` 的 `while (running)` 同理。
- **现状**: region restart 流程（nop-stream-runtime `SupervisionLoop.java:462-489`）：Phase 1 `cancelTaskWithMailbox` → 任务关闭 → `StreamSourceOperator.close()`（`StreamSourceOperator.java:288`）调 `sourceFunction.cancel()` 将 `running=false`；Phase 3 `rebuildTask` 用 `deepCopy()` 重建 operator 链——**sourceFunction 是共享引用，running 仍为 false**——重新提交后新任务调用同一实例的 `run()`，循环条件立即为假，run 正常返回，source 被视为数据耗尽（EOS）。
- **风险**: 任一区域重启后 source 静默停止产出：CDC 场景即"漏变更"（Debezium 停摆且无报错），消息/批源场景数据流中断，监控视角却是"重启成功、任务正常完成"。触发路径现实：任何触发 region restart 的任务失败（SupervisionLoop 生产配置的失败检测）。
- **建议**: 三个 `run()` 入口统一重置生命周期标志（`running=true; failed=false;`，MessageSource 还需重建 shutdownLatch 语义）；或运行时在 rebuildTask 时对 Shareable 语义的 source udf 做重置回调。
- **误报排除**: 已核实 rebuildTask 对 SOURCE 角色（producer role，`SupervisionLoop.java:749-771`）同样走 `deepCopy()` 共享 udf，且 cancel 在 rebuild 之前必然发生（Phase 1 cancel → Phase 2 waitForTerminal → Phase 3 rebuild）。`XplSourceFunction` 不受此条影响（其 run 不读 running）。非误报。

### [P1] BatchLoaderSourceFunction.seek 无效 + currentOffset 差一：checkpoint 恢复后从 loader 头部全量重放

- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:61-105`
- **维度**: D1、D8
- **证据**:
```java
public void run(SourceContext<S> ctx) throws Exception {
    ...
    while (running) {
        List<S> batch = loader.load(batchSize, chunkContext);  // 永远从 loader 头部加载，不消费 currentOffset
        ...
        ctx.collect(item);
        currentOffset++;                                        // 初始 -1，计数比实际位置少 1
    }
}
...
public void seek(long offset) {
    this.currentOffset = offset;                                // 只写字段，run() 从不读取
}
```
- **现状**: 该类实现 `ReplayableSourceFunction`，接口契约（nop-stream-core `ReplayableSourceFunction.java:12-27`）明确 "calls seek(long) before run(SourceContext), so the source re-emits from the correct position"，且运行时恢复路径确实调用 seek（`StreamSourceOperator.restoreState:321-331`）。但 `run()` 完全忽略 `currentOffset`，`seek` 是空转；`getCurrentOffset()` 从 -1 起算，返回的是最后一条已发记录的下标而非"下一条位置"（对照实现 `CollectionReplayableSource` 从 0 起且语义为下一位置）。
- **风险**: checkpoint 恢复后批源从头全量重放，AT_LEAST_ONCE sink 收到成倍重复数据；offset 差一使快照值本身也不可作恢复位置。触发条件：启用 checkpoint + 任务失败恢复 + 本源参与。
- **建议**: 要么让 loader 按-offset 读取（load 支持从 offset 继续），要么明确该源为不可重放（`getSourceConsistency` 声明与 seek 语义对齐），并修正计数起点。
- **误报排除**: 已核实 seek 在生产代码中有真实调用点（StreamSourceOperator.restoreState），且 run() 全文无任何消费 currentOffset 的逻辑。非误报。

### [P1] FileSplitEnumeratorState 序列化以 ','/'|' 为分隔符：文件名含逗号时状态损坏，恢复后静默漏读文件

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSource.java:144-191`
- **维度**: D1、D8
- **证据**:
```java
sb.append(String.join(",", obj.getDiscoveredFiles())).append('\n');   // 文件名含 ',' 即被拆散
sb.append(String.join(",", obj.getAssignedFiles())).append('\n');
...
String[] parts = line.split("\\|", -1);
if (parts.length < 4) continue;                                        // 畸形条目被静默丢弃
```
- **现状**: discovered/assigned/finished 三个集合用逗号 CSV 序列化，splitById 条目用 `|` 分隔。逗号与竖线在 POSIX/Windows 文件名中均合法（如 `report,final.txt`）。反序列化时含逗号的路径被拆成两个错误路径：discovered 集合损坏导致 `assignAvailableSplitsTo` 遍历不到真实文件路径（不再分配）；splitById 行含 `|` 时解析错位，`parts.length < 4` 的条目被 continue 静默丢弃。
- **风险**: 文件名含逗号/竖线的输入目录 + 一次 checkpoint 恢复，即出现漏读文件（数据丢失）或重复分配。splitById 用 `\n` 分隔部分本身安全（文件名不含换行），但三个集合不安全。
- **建议**: 序列化改用长度前缀或 Base64/转义编码（如 URL-encode 路径），或逐条 `\n` + 转义分隔符；解析失败应抛 IOException 而非 continue。
- **误报排除**: 已核对 `FileSplitEnumerator.restoreState` 直接采用反序列化结果、`assignAvailableSplitsTo` 以 `discoveredFiles` 为分配依据（`FileSplitEnumerator.java:115-130`），损坏路径必然漏分配。非误报。

### [P1] FileSourceReader 游标按 '\n' 单字节累加：CRLF 文件恢复后从行中间重读，产生重复/断裂记录

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:127-138`
- **维度**: D1
- **证据**:
```java
line = activeReader.readLine();
...
if (line != null) {
    activeBytesConsumed += line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 假定换行符恰为 1 字节
    if (activeSplit != null) {
        long newOffset = activeSplit.getStartOffset() + activeBytesConsumed;
        activeSplit = activeSplit.withCurrentOffset(newOffset);              // cursor 进入 checkpoint
    }
```
- **现状**: `BufferedReader.readLine` 会吃掉 `\r\n` 两个字节，但游标只加 1。CRLF 文件下 currentOffset 系统性偏小且偏差逐行累积。恢复路径真实存在：`SourceReaderOperator.snapshotState:365-368` 把 reader 的 split（含 currentOffset）写入 operator state，`restoreState:381-391` 恢复后 `openSplit` 用 `fis.skip(currentOffset)` 定位（`FileSourceReader.java:160-165`）。
- **风险**: CRLF 输入文件 + checkpoint 恢复 → skip 落在行中间，产出半行记录并重复此前部分行，下游数据损坏。BOUNDED 源平时跑完不恢复，但 region restart/失败恢复路径会触发。
- **建议**: 记录"已读行数"而非推算字节，或恢复后跳到下一行首；至少按 `readLine` 前后流位置差计算（如自行缓冲按字节读行）。
- **误报排除**: 已确认 snapshot/restore 调用链（SourceReaderOperator 365/381 行）与 openSplit 的 skip 逻辑；UTF-8 多字节字符本身计算正确（getBytes(UTF_8)），问题仅在行结束符假定。非误报。

### [P2] FileSplitEnumerator.discoverSplits 的 Files.walk 未关闭：目录句柄泄漏

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSplitEnumerator.java:82-84`
- **维度**: D2
- **证据**:
```java
List<Path> files = Files.walk(dir)
        .filter(Files::isRegularFile)
        .collect(Collectors.toList());
```
- **现状**: `Files.walk` 返回的 Stream 持有目录句柄，JDK 契约要求调用方 close（javadoc 明示 "If not closed ... resource leak"）。此处未用 try-with-resources。
- **风险**: 每次 enumerator start 泄漏一个目录句柄；Windows 上会锁住源目录导致外部清理失败；region restart 反复重建 source 时累积。
- **建议**: `try (Stream<Path> s = Files.walk(dir)) { ... }`。
- **误报排除**: 全文件无 close/finally 兜底；`discoverSplits` 在每次 start 且 discoveredFiles 为空时执行（含恢复后 start）。非误报。

### [P2] 2PC sink 幂等守卫按裸 epochId 判重：复用输出目录/ledger 表的全新运行会静默丢弃当前数据

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:168-181`、`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:218-225`
- **维度**: D1、D8
- **证据**（File sink；JDBC 同构）:
```java
Properties manifest = loadManifest();
if (manifest.containsKey(manifestKey(checkpointId))) {   // manifestKey = String.valueOf(epochId)
    getPendingCommits().remove(checkpointId);
    return;                                             // 跳过 rename —— 本轮数据被静默丢弃
}
```
- **现状**: 幂等守卫的 key 是全局 checkpointId（从 1 计数）。epoch 计数器的持久化依赖 coordinator 恢复路径（CheckpointCoordinator.restoreFromCheckpoint / advanceCheckpointIdCounterAfterRestore 有防护），但**全新部署复用同一 outputDir / 同一 ledger 表**（不清目录、jobId 变更或无 durable checkpoint）时计数器从 1 重新开始，manifest/ledger 中旧记录命中同号 epoch：File sink 丢弃当前批次且 `.epoch-N.tmp` 残留磁盘；JDBC sink 跳过数据写入仅补一条日志。
- **风险**: 运维常见操作（换 job 重跑同目录/同库表）触发当前数据静默丢失，且无错误暴露。
- **建议**: 幂等 key 混入 jobId/pipelineId 或运行实例标识；或 commit 遇到同号 epoch 但 pending 数据存在时报错而非跳过。
- **误报排除**: 已核对 CheckpointIDCounter 为内存实现、全新进程/无恢复时从初始值计数（CheckpointCoordinator.java:401），File/JDBC sink 的守卫均为裸 epochId。存在"同目录重跑"这一现实触发方式。非误报（严格说属于设计与实现组合的边界，按"特定条件触发的正确性问题"定 P2）。

### [P2] StreamModelDslBuilder 静默忽略 transform 级 parallelism / maxParallelism / consistencyCapability 属性

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:393-520`
- **维度**: D8（+D7 违背该类自述规范）
- **证据**:
stream.xdef 声明（`nop-kernel/nop-xdefs/.../stream.xdef:104-112`）:
```xml
<xdef:define xdef:name="StreamTransformModel" id="!string" name="string" bean="bean-name" parallelism="int">
<source maxParallelism="!int=0" consistencyCapability="!enum:...=AT_LEAST_ONCE" ...>
```
builder 中对 transform 模型仅有（第 151-153 行）:
```java
if (model.getParallelism() > 0) {     // 仅全局 stream 级
    env.setParallelism(model.getParallelism());
}
```
- **现状**: `StreamTransformModel.getParallelism()`、`StreamSourceModel.getMaxParallelism()/getConsistencyCapability()`、`StreamSinkModel.getConsistencyCapability()` 在生成模型中均存在（`_StreamTransformModel.java:52` 等），但 builder 的任何 build* 方法都不读取它们，也不 fail-fast。示例 DSL `fraud-detection.stream.xml` 中 `<source parallelism="2">`、`<sink consistencyCapability="IDEMPOTENT">` 均被静默丢弃。这与该类自述的 "Anti-Hollow guarantees: Declared attributes are either consumed or rejected at build time — never silently ignored"（类注释 105-116 行，对 edge/window 属性确实做到了）直接矛盾。
- **风险**: 用户声明的并行度与一致性能力不生效：并行度静默回退全局值；source/sink 一致性声明与实际函数能力无校验（例如声明 EXACTLY_ONCE 但 bean 是 at-least-once 函数也不会报错）。
- **建议**: 仿照 `validateEdgeDeclarations` 的矩阵：能映射的（per-transform parallelism → DataStream.setParallelism 若 core 支持）落实，不能映射的 fail-fast 指出属性名；consistencyCapability 与解析出的函数实例 `getSinkConsistency()/getSourceConsistency()` 做一致性校验。
- **误报排除**: 已 grep 整个 builder 包确认无 `getConsistencyCapability`/transform 级 `getParallelism` 消费点；xdef 属性真实声明且 fraud 示例真实使用。非误报。

### [P2] XplSourceFunction.cancel 的 running 标志是死代码：内联 xpl source 的取消仅能依赖 collect 点协作异常，长阻塞 body 无法取消

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplSourceFunction.java:34-58`
- **维度**: D1、D8
- **证据**:
```java
private volatile boolean running = true;       // 除 isRunning() 外无任何读取者

public void run(SourceFunction.SourceContext<T> ctx) {
    // The xpl body is responsible for honouring the cancel flag and exiting its loop.
    body.call1(null, ctx, XplFunctionSupport.newCallScope());   // body 只收到 ctx，拿不到 running
}
public void cancel() {
    running = false;                            // 对 body 无任何效果
}
```
- **现状**: 注释声称 body 应检查 cancel 标志，但 body 的唯一入参是 `SourceContext`，而 `SourceContext` 接口（nop-stream-core `SourceFunction.java:60-96`）没有任何取消查询方法；`running` 字段对 body 不可见，cancel() 实际是 no-op。当前唯一的停止途径是 `SourceContext.collect` 内部的 `drainControlMails()` 抛 `ERR_STREAM_CHECKPOINT_ABORTED`（`StreamSourceOperator.java:203-208, 252-261`）。
- **风险**: body 若在两次 collect 之间长时间阻塞（外部拉取慢、sleep、阻塞 IO），任务无法在取消预算内停止（SupervisionLoop 的 zombie-task 超时会把任务判为僵尸并失败）；语义上 DSL 内联 source 的 cancel 契约（SourceFunction 接口要求）未实现。
- **建议**: 将取消标志绑定进 xpl 调用作用域（如 `scope.setLocalValue("running", ...)`）或为 SourceContext 增加取消查询；至少修正注释避免误导。
- **误报排除**: 已核实 SourceContext 接口无取消方法、`running` 全模块无其他读取点、collect 点协作取消机制存在于 StreamSourceOperator。非误报。

### [P3] AdvancedTransforms.resolveWindowAssigner 对 null windowFnId 直接 switch 触发 NPE（仅程序化构造可达）

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:218-238`
- **维度**: D1
- **证据**:
```java
String windowFnId = strategy.getWindowFnId();
if (owner.beanResolver().contains(windowFnId)) { ... }
switch (windowFnId) {            // windowFnId == null 时 NPE
```
- **现状**: xdef 中 `windowFnId="!string"` 必填，DSL 路径不可达 null；但 `WindowingStrategyModel` 是普通 bean，程序化构造 StreamModel（测试/嵌入用法）传入 null 时 `contains(null)` 后 `switch(null)` 抛 NPE，报错信息无定位上下文。
- **风险**: 低；仅程序化路径，错误表现是无上下文 NPE 而非带 `strategyRef` 的友好错误。
- **建议**: 入口处判空并抛 `ERR_STREAM_REQUIRED_ATTR`（带 strategyRef 定位）。
- **误报排除**: 已确认 xdef 必填声明使 DSL 主路径安全，故定 P3 而非更高。

### [P3] connector 构造器混用 IllegalArgumentException，与模块内 StreamException+ErrorCode 约定不一致

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FilePendingCommit.java:27-29`、`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSource.java:49-51`、`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/pattern/*.java`（多处）
- **维度**: D7、D4
- **证据**:
```java
// FilePendingCommit / FileSource
throw new IllegalArgumentException("tempPath must not be null");   // 同包 FileTwoPhaseCommitSink 用 StreamException(ERR_STREAM_NULL_ARG).param(...)
// fraud patterns
throw new IllegalArgumentException("Match must contain both 'first' and 'second' events");
```
- **现状**: 同一 connector 模块内构造器校验风格分裂：FileTwoPhaseCommitSink/JdbcTwoPhaseCommitSink/MessageSourceFunction 用 `StreamException + ERR_STREAM_NULL_ARG + .param()`，FilePendingCommit/FileSource 用裸 IllegalArgumentException。fraud-example 的 pattern 校验同样使用 IllegalArgumentException（示例模块，影响更小）。错误消息均为英文（合规）。
- **风险**: 错误处理约定漂移，调用方难以统一捕获与定位（无 ErrorCode、无 param 上下文）。
- **建议**: 统一为 `StreamException(ERR_STREAM_NULL_ARG/ERR_STREAM_INVALID_ARG).param(...)`。
- **误报排除**: 非 bare RuntimeException 直接形态，且平台规范对模块内部允许模块异常类，此处只是同模块内不一致，定 P3。

### [P3] applyCheckpointConfig：enabled=true 但 interval<=0 时 checkpoint 被静默关闭

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:167-174`
- **维度**: D8
- **证据**:
```java
if (cfg.isEnabled() && cfg.getInterval() > 0) {
    env.enableCheckpointing(cfg.getInterval());
}
// enabled=true 且 interval<=0：无 checkpoint、无告警
```
- **现状**: 用户显式声明 `enabled="true"` 但未配 interval（xdef 中 interval 默认 0）时，管线以无 checkpoint 方式运行，2PC sink 的 exactly-once 保障随之失效，且无任何构建期提示。
- **风险**: 静默降级；与该类 fail-fast 哲学不一致。
- **建议**: `enabled && interval<=0` 时抛 `ERR_STREAM_CONFIG_ERROR` 类错误，提示必须配置 interval。
- **误报排除**: 已核对 xdef 默认值与 builder 逻辑，无其他兜底路径。定 P3（配置错误场景）。

### [P3] BatchConsumerSinkFunction.close()：finally 内 throw 使 flushError 的最终异常不可达，flush 失败信息降级

- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:116-145`
- **维度**: D4
- **证据**:
```java
} finally {
    if (consumer instanceof AutoCloseable) {
        try {
            ((AutoCloseable) consumer).close();
        } catch (Exception e) {
            if (flushError != null) {
                flushError.addSuppressed(e);
            }
            throw new StreamException(ERR_STREAM_STATE_ERROR, e) ...;   // finally 中 throw
            // ↑ 此 throw 使方法立即终止，下面第 141-144 行对 flushError 的
            //   ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED 抛出永远不可达
        }
    }
}
if (flushError != null) {
    throw new StreamException(ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED, flushError) ...
}
```
- **现状**: 当 flush 与 consumer.close 同时失败时，最终抛出的是 close 失败异常（flushError 仅作为局部变量被丢弃，连 suppressed 都加不进去——addSuppressed 加在原始 flushError 上但该异常未被引用到新异常链中），丢失"数据可能丢失"的语义标记；仅 flush 失败时路径正常。另注：`flush()` 的注释称 "data retained for retry"，但异常向上抛出导致任务失败重启，buffer 为内存态必然丢失，注释误导。
- **风险**: 双失败场景诊断信息降级，调用方看不到 flush 失败根因。
- **建议**: 在 finally 中收集 close 异常为局部变量，方法尾部统一组装（flush 优先，close 作 suppressed）。
- **误报排除**: 已通读 close() 全部分支；单失败路径正常，双失败路径证据如上。定 P3。

### [P3] MessageSourceFunction.run 异常退出路径不取消订阅（依赖 close 兜底的窗口期泄漏）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:170-183`
- **维度**: D2、D4
- **证据**:
```java
while (running && !failed) { shutdownLatch.await(1, TimeUnit.SECONDS); }
if (pendingError != null) {
    if (pendingError instanceof Exception) {
        throw (Exception) pendingError;      // 直接抛出，subscription 未 cancel
    }
```
- **现状**: onMessage 捕获的类型不匹配/collect 失败通过 pendingError 使 run 抛异常退出，但该路径不取消 `subscription`；订阅线程可能继续收消息并向已失效的 ctx collect。最终依赖运行时调 `StreamSourceOperator.close() → cancel()` 兜底取消（`StreamSourceOperator.java:285-292`），失败路径下存在泄漏窗口；对比 `DebeziumCdcSourceFunction.run` 的 finally 中有完整的取消清理。
- **风险**: 窗口期内重复投递/无效 collect、日志噪音；若宿主未调用 close 则持续泄漏。
- **建议**: run 的 try/finally 中在退出时取消订阅（与 Debezium 实现对齐）。
- **误报排除**: 已核实 close 兜底存在（故仅 P3），以及 DebeziumCdcSourceFunction 的对照实现。非误报。

### [P3] DebeziumCdcSourceFunction.run 中 `if (!draining)` 恒为真的死代码

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:131-143`
- **维度**: D1（维护性）
- **证据**:
```java
this.draining = false;      // 第 131 行刚置 false
initCompletionLatch();
try {
    if (!draining) {        // 第 135 行恒真
        source = createMessageSource(config, offsetStore);
        ...
```
- **现状**: run 入口先重置 draining=false 再立即判断 `!draining`，条件永真，原意（跳过 drain 模式下的引擎重建）失效。当前 drain 路径靠 `truncateForDrain` 先行停止 source、run 再空转退出，行为凑巧正确，但判断逻辑与设计意图脱节。
- **风险**: 低；后续维护者按条件语义修改时易引入错误。
- **建议**: 删除该条件或恢复其真实语义（如在 drain 触发后 run 不重建引擎）。
- **误报排除**: 已核对 draining 的全部写点（run 重置、truncateForDrain 置 true）；run 入口到判断之间无并发窗口可依赖（即使有也是竞争而非逻辑）。定 P3。

---

## 补充说明（非发现项，供参考）

- `JdbcTwoPhaseCommitSink.commit` 的 ledger 命中分支提前 return 跳过自身 remove（`JdbcTwoPhaseCommitSink.java:222-224` vs 265 行），经核基类 `TwoPhaseCommitSinkFunction.finishCommit` 在 commit 返回后统一 remove（`TwoPhaseCommitSinkFunction.java:126-128`），由调用方兜底，不构成问题。
- `FileTwoPhaseCommitSink` 的 crash 修复路径（rename 成功但 manifest 未写 → commit 时补 manifest）经推演正确；epoch 计数器跨进程恢复有 `advanceCheckpointIdCounterAfterRestore` 防护（见 P2 复用重跑条目的边界说明）。
- `FileTwoPhaseCommitSink.deleteIfExistsQuiet` 吞 IOException 有注释声明 best-effort 且发生在清理场景，不计为发现。
- D5 安全专项：JDBC sink 全参数化 + escapeSQLName，未发现拼接注入与凭证处理问题；Debezium 凭证经 DebeziumConfig 传递（范围外模块）。
