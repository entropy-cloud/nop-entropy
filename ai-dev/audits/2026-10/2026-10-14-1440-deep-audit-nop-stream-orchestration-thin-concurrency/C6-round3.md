# C6 资源生命周期与泄漏 — nop-stream-orchestration-thin 第 3 轮深挖（盲审·收口轮）

- 审计单元：`nop-stream/nop-stream-flow`（60 files / 6,202 lines）+ `nop-stream/nop-stream-sql`（17 / 4,334）+ `nop-stream/nop-stream-fraud-example`（21 / 3,256），src/main/java 非 `_` 前缀文件，合计 98 files / 13,792 lines。
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation，HEAD 8dcfa4cd0c——与第 1/2 轮相同，live code 无漂移）。
- 必要输入：第 1 轮 `C6-resource-lifecycle.md`（C6-001~005 + 16 项配对表）、第 2 轮 `C6-round2.md`（C6-006 + 5 组排除）、grep 基线 `_tmp/cra-baseline-nop-stream-orchestration-thin.txt`，均已全文读取。
- 盲审纪律：除上述文件外未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；未读取其他维度审计输出。跨单元对照阅读（不作为发现对象，仅核定可达性契约）：`DebeziumCdcSourceFunction`、`StreamSourceOperator`、`StreamTaskInvokable`、`SubtaskTask`、`OperatorChain`、`SupervisionLoop`。
- F8/F9/F3 定向对照已执行（见 §2.5）。

## 1. 本轮发现

### [nop-stream-orchestration-thin-C6-007] DirectoryFileSourceFunction：cancel 置位的 volatile cancelled 跨 run 永不复位——区域重启按声明契约复用同一实例时，重启 run 在循环顶静默退出，半成功收尾（C6-006 同族：代际守卫缺失的又一站点）

**文件:行号**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/DirectoryFileSourceFunction.java:82, 110-130, 230-233`（对照同单元生产基类的复位契约 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:225-237`；运行时复用链 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSourceOperator.java:226-231, 392-400`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:875-911`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/SubtaskTask.java:142-145, 219-233`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:253-259`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:545-574, 847`）

**证据片段**：

```java
// DirectoryFileSourceFunction.java:82, 230-233 —— 置 true 后全类无任何复位写点
private volatile boolean cancelled;
...
@Override
public void cancel() {
    cancelled = true;          // 唯一写点；无任何 cancelled = false
}
```

```java
// DirectoryFileSourceFunction.java:110-130 —— run() 入口不复位 cancelled；
// 循环顶 + 两处内层检查遇 true 即 return（0 事件退出）
public void run(SourceContext<String> ctx) throws Exception {
    if (!runEntered.compareAndSet(false, true)) {
        return;
    }
    try {
        for (File file : discoverFiles()) {
            if (cancelled) {
                return;        // 重启 run 在此静默退出 → 上层视为 bounded-source EOS
            }
            emitRemaining(file, ctx);
        }
        ...
    } finally {
        runEntered.set(false); // 只复位 runEntered，不复位 cancelled
    }
}
```

```java
// 生产基类对同一"region restart 复用实例"契约的显式复位（DebeziumCdcSourceFunction.java:230-237）
// Reset lifecycle state: a region restart reuses this instance (the rebuilt
// operator chain shares the source function) after cancel() set running=false.
this.running = true;
this.draining = false;
this.pendingError = null;
initCompletionLatch();
```

**严重程度**：P1（取消/重试路径；半个成功——重启 run 以干净 EOS 假成功收尾）

**并发场景**：不是多线程竞态，而是跨 run 代际（generation）状态残留——与 C6-006 同族但机制不同：C6-006 是上一轮残留 emitter 线程的 finally-cancel 打进下一轮；本条是**本类自己的 cancel 标志在 run() 入口缺失复位**。运行时契约（跨单元核定，全部有代码锚点）：① 任务任一终态路径都调用 `sourceFunction.cancel()`——成功 EOS 路径 `StreamTaskInvokable.invokeSource` finally `closeChainAndGate`（:884-904）→ `OperatorChain.close()`（:181-194）→ `StreamSourceOperator.close()`（:392-400 `sourceFunction.cancel()`）；失败路径同一 finally 无条件执行（:904），且 `SubtaskTask.run` finally `closeOperatorChains`（:142-145, :219-233）二次兜底；② 区域重启由 `SupervisionLoop.restartRegion` 对所有非 COMPLETED 任务（FAILED/CANCELED）rebuild + resubmit（:545-574）；③ 重建链 `jobVertex.getOperatorChains().get(0).deepCopy(copyLocation)`（SupervisionLoop :847）→ `StreamSourceOperator.copyForSubtask` **原样共享同一 sourceFunction 实例**（StreamSourceOperator :226-231；OperatorChain deepCopy javadoc :231-232 明言 "user functions ... are shared"）。即：基类 javadoc :230-231 声明并防御的"重启复用实例"契约，DirectoryFileSourceFunction 只复位了 `runEntered`，漏掉了 `cancelled`。

**触发时序/泄漏链**：① S2 源任务运行中因任意瞬态错误 FAILED（如 `discoverFiles` 对目录抖动抛 `IllegalStateException`，:132-139），或同区域下游任务失败触发 Phase 1 `cancelTaskWithMailbox` 把运行中的源任务打到 CANCELED——两条路径的 teardown 都已把共享实例置 `cancelled=true`；② SupervisionLoop 判定区域可重启（drainable producer-region restart，:503-510），源任务非 COMPLETED → rebuild + resubmit（:569-572）；③ 重启 run 进入 `run()`：`runEntered` CAS 成功（上一轮 finally 已复位），但 `cancelled` 仍为 true → 文件循环顶 `if (cancelled) return`（:119-121）立即退出，**零事件发射**；④ 上层 `sourceError == null` → `operatorChain.finish()` + MAX_WATERMARK + EOS（StreamTaskInvokable :868-874, :885-903）→ 重启任务 COMPLETED；⑤ 监督循环见全区域终态 → 作业对外**成功**。后果：故障点之后未消费的文件/文件尾从未发出（S2 场景假成功、静默数据缺失），`maxRestartsPerRegion`（默认 3）的重试预算一次都未消耗——本应"重试即自愈"的瞬态故障被降级为无声的数据截断。泄漏的对象与 C6-006 相同：基类用 fresh-latch/running 复位精心重建的下一轮生命周期，这里是 `runEntered` 之外的第二个生命周期标志。

**现状**：全类唯一 `cancelled` 写点是 `cancel()`（:232），grep 全类证实无复位；`runEntered`（transient AtomicBoolean）有 finally 复位 + readObject 重初始化（:98-104），两标志处置不对称；同单元 `XplSourceFunction.running` 同样不复位但其 `run()` 从不读取（javadoc :34-37 声明为 test surface，无功能影响，见 §2.1 排除）。

**风险**：S2 场景（`src/main/resources/_vfs/nop/stream/demo/fraud-s2-file.stream.xml` 与 `_file-delta` 变体、`fraud-parallel-2pc-file/jdbc.stream.xml` E2E、`TestParallel2PcFileE2E`）在"任务失败 + 区域重启"路径上输出截断且作业假成功。该类 javadoc :236-243 向平台声明 `getSourceConsistency() == REPLAYABLE`（"a recovered run resumes from the checkpointed position"）——本链中"恢复的 run"根本不 resume，属于违反本类已声明恢复契约 + 违反运行时 region-restart 复用契约（基类已按该契约编码防御）的双重违约，符合 fraud-example 立案标准。现有单测 `TestDirectoryFileSourceFunction`（8 用例）全部在 cancel 前的新鲜实例上验证 resume，未覆盖"cancel 后同实例重跑"，故测试面不会拦截回归。

**建议**：与基类对齐——`run()` 在 `runEntered` CAS 成功后增加 `this.cancelled = false;`（写 volatile 于循环读取之前，本方法此后均由本任务线程读，无可见性缺口）；或按代际守卫思路比较 run 代次后再清位。补一条回归测试：cancel 一次后对**同一实例**再 run，断言全量重发（当前会得到 0 事件红转绿的用例）。同时建议基类复位块的模式（running/draining/pendingError/latch 四件套）成为所有实现 `SourceFunction` 的跨 run 可复用 UDF 的检查单。

**失效面枚举**：`DirectoryFileSourceFunction.run/cancel`（:110-130, :230-233）；运行时 `StreamSourceOperator.close`、`SubtaskTask.closeOperatorChains`、`SupervisionLoop.restartRegion/rebuildTask` 的复用契约面；S2 全部模型与 E2E（`fraud-s2-file*.stream.xml`、`fraud-parallel-2pc-file.stream.xml`、`fraud-parallel-2pc-jdbc.stream.xml`、`TestParallel2PcFileE2E`）；`TestDirectoryFileSourceFunction` 族在"cancel 后同实例重跑"用例上会由红变绿失效。

**信心水平**：高（机制链每一环均有行号锚点：唯一写点、无复位、deepCopy 共享实例、失败路径 teardown 必经 cancel、重启 resubmit 非 COMPLETED 任务、立即 return 后按 EOS 成功收尾；唯一衰减项是触发依赖"失败/取消 + 区域重启"叠加，非主路径，按 P1 如实标注）。

**误报排除**：`runEntered` CAS 门与 readObject 重初始化配对完整（第 1 轮已判，本轮复核维持），不是本条入口；`Thread.sleep(lineDelayMs/finishLingerMs)`（:125, :212）运行在任务线程上，`SubtaskTask.cancel()`（:163-167）对执行线程 `interrupt()` 可打断，cancel 延迟有界，不属 fire-and-forget 面（§2.1）；`advanceCursor` 在 `ctx.collect` 之后推进（:189-191, :201-202）是 checkpoint 边界内 at-least-once 的声明语义，与 REPLAYABLE resume 契约一致，非 half-success 新面（§2.5-F9）；演示校准不适用——本条违反的是类自身 REPLAYABLE 声明与平台区域重启复用契约，非演示性简化。

**复核状态**： 已保留（P1——全部证据链逐环独立复核成立：cancelled 非 transient 唯一写点 :232 无复位、run() 读点 :119/:124/:169/:173 无入口复位、teardown 必经 sourceFunction.cancel（SubtaskTask.run finally closeOperatorChains → StreamSourceOperator.close :392-400）、本类非 SubtaskShardedSourceFunction → 全部 deepCopy 路径共享同一实例（GraphExecutionPlan:449-458 + SupervisionLoop:847）、重启 run 循环顶零事件返回按 EOS COMPLETED；与 C1-003 复核修正不冲突且互为佐证——C1-003 被修正的是"新旧 attempt 交叠窗口"，本条不需交叠，Phase 2 waitForTerminal 的先行关系恰恰保证 teardown cancel() happens-before 新 run() 读 cancelled，纯顺序陈旧即可触发。详见第 1 轮文件文末「维度复核结论」。现状：新发现，未修复）

## 2. 排除项/定向核查

### 2.1 C6-006 失效面扩展：fire-and-forget 线程 / executor 提交不等待 / 无 interrupt 关闭站点（全单元穷举）

- **线程/executor 全量清单**：基线 C4 节 + 本轮补充 grep（`Executor|Executors\.|CompletableFuture|runAsync|supplyAsync|\.submit\(|\.execute\(|ForkJoin|parallelStream|new Timer|ScheduledThread|LockSupport|\.join\(`）证实：三模块 src/main/java 中线程创建**唯一**（`ReplayableCdcSourceFunction.java:127`，已立案 C6-005/006），executor/异步提交形态**零**。C6-006 的 fire-and-forget 面无第二站点。
- **无 interrupt 关闭站点**：`Thread.sleep` 共 4 处。ReplayableCdcSourceFunction :175/:179（emitter 线程，无 interrupt——已立案 C6-006）；DirectoryFileSourceFunction :125/:212 运行在**任务线程**上，`SubtaskTask.cancel()`（nop-stream-core :163-167）对 executingThread `interrupt()`，sleep 可被打断、异常沿 run 传播使任务 CANCELED——有界且平台中断可达，非缺陷。
- **cancel/stop 代际守卫缺失同族——单元内生命周期标志全清单**（精确 grep `volatile|running|cancelled|draining|stopped|started|subscribed|finished`）：
  | 标志 | 判定 |
  |---|---|
  | `DirectoryFileSourceFunction.cancelled`（:82） | **本轮立案 C6-007**（跨 run 永不复位 + 实例复用） |
  | `XplSourceFunction.running`（flow :45） | cancel 后不复位，但 `run()` 从不读取（javadoc :34-37 声明 cancel 仅服务 operator close 路径 + `isRunning()` 为 test surface；全仓 grep 证实无生产消费方）→ 无功能影响，排除 |
  | `ReplayableCdcSourceFunction$ReplayEngine.cancelled`（:116） | 引擎实例每 run 新建（`createMessageSource` 每 run 调用一次，:97-105）→ 无跨 run 面；跨 run 毒害已由 C6-006（stale emitter finally-cancel）承载 |
- **C6-006 窗口内的 offset 写入**：残留 emitter 在 restore 之后落一拍 `writeReplayIndex(i+1)`（:173, :205-211）的交错已含在 C6-006 的"残留存活 = 剩余 park + 至多一个在途事件"窗口内；写入值单调且不超前已 accept 的事件序号，落在 REPLAYABLE at-least-once 声明包络内，不构成独立立案面。

### 2.2 第 1/2 轮"当前不可达"判定复核（C6-002 / C6-005 可达性前提）

- **C6-002（onCancelTasks 注册表）**：全仓 grep `appendOnCancel|removeOnCancel`（java + xml/xpl/xlib/js/ts）——Java 命中均为其他模块各自的 `ICancellable` 实现/消费（nop-ai、nop-dao、nop-batch、rpc-demo、connector 测试桩），**无一调用 `ReplaySubscription` 的注册 API**；脚本资源文件零命中（无 XLang 动态反射路径，订阅对象也未暴露给脚本面）。维持"注册表恒空、当前不可达"。
- **C6-005（subscribe 重复订阅）**：全仓 grep `\.subscribe(` 限定 nop-message / nop-stream-connector-debezium / nop-stream-core 的 src/main——`DebeziumMessageSource.subscribe` 生产调用方**唯一**（`DebeziumCdcSourceFunction.run` :260，每 run 每 subtask 恰一次，`runEntered` CAS 门挡同实例双 run）。维持"契约面缺口、当前接线不可达"。

### 2.3 fraud-example ReplaySubscription/Cancellable 注册表对称性独立核（第 2 轮 C6-002 点名面的本轮独立验证）

对 `ReplayEngine.stop()` 与 `ReplaySubscription.cancel()` 的**全部**调用点逐一枚举（`DebeziumCdcSourceFunction`）：

| 入口 | 路径 | 注册表（onCancelTasks）是否被执行 |
|---|---|---|
| run() 正常/异常收尾 | finally :299-307：`subscription.cancel()` **先于** `source.stop()`（:310） | 执行（先于 stop） |
| 外部 cancel() | :336-343：`sub.cancel()` **先于** `msgSource.stop()`（:342） | 执行（先于 stop） |
| truncateForDrain() | :354-362：`sub.cancel()` **先于** `msgSource.stop()`（:361） | 执行（先于 stop） |
| subscribe() 抛异常 | run() :278-281 catch 调 `source.stop()` | 不执行——但此时 `subscription` 尚未赋值（:260 赋值未完成），注册表还不存在，无泄漏对象 |

**结论**：今日每一条 stop() 可达路径之前注册表都已被 `sub.cancel()` 清空（或注册表尚不存在）；`stop()` 绕过注册表的不对称（C6-002 已立案部分）维持为潜在契约缺口，无新增可达泄漏路径。注册表本体缺陷（非幂等/无同步/无逐回调隔离）仍以 C6-002 为准，不重复立案。

### 2.4 前两轮未点名小文件兜底清查（对照覆盖声明）

- **fraud-example（9 个前两轮未全文阅读文件）**：`FraudDetectionDemo`（全文复读：NFA/SharedBuffer 生命周期与第 1 轮残留边界 2 一致，演示 main 校准维持）；`MockTransactionGenerator`（纯 POJO 工厂）；`RapidTransactionPattern`/`UnusualAmountPattern`/`GeographicAnomalyPattern`/`AccountTakeoverPattern`（静态 Pattern 构建器 + 常量，无状态无资源）；`FraudAlert`/`TransactionEvent`/`AlertSummaryRow`/`TxSummaryRow`/`EnrichedTransaction`（Serializable DataBean POJO，无资源字段）。无发现。
- **flow（model 35 文件）**：逐一核实为 `_gen._*` 生成基类的空壳子类（如 `StreamEnvironmentModel` 全文 9 行）——字段全部承载于 `_` 前缀生成父类，按"生成文件不作为发现对象"规则出清；`validate/` 4 文件实例字段仅不可变 `ValidationIssue`（5 个 final 字段）与 static final 常量；`spi/` 3 文件为纯接口。无发现。
- **sql（12 个前两轮未全文阅读文件）**：`CompileErrors`/`EqlExprSupport`/`SqlAccShapeUtil`（私有构造 + 纯静态方法）；`StreamSqlCompilerProvider`（无状态委托，无 static 持有）；`StreamRecordEvaluator`（@FunctionalInterface Serializable）；`SqlRowCompositeFunction`（final 字段三元组，per-specJson 缓存已判 C6-004 同批的已声明边界）；`SqlCastHelper`/`SqlRowAggregateSpec`/`SqlScalarXplPrinter`/`StreamSqlExprCompiler` 运行期面/`StreamAggregatorFunctionResolver`（无静态可变面——第 2 轮 §2.3 表已覆盖，本轮 grep 复核无漂移）。无发现。

### 2.5 F8/F9/F3 定向对照

- **F8（释放路径中间异常）**：第 2 轮 §2.2 三面维持。本轮新增核对 1 处：emitter finally 的 `ReplayableCdcSourceFunction.this.cancel()`（:185-189）若自身抛出（经 `ReplaySubscription.cancel` 的回调迭代，C6-002③）→ 异常穿出 runReplay 致 emitter 线程未捕获终止，但 `cancelled=true` 已先于迭代置位、基类 `run()` 等待循环仍正常退出；若经基类 `cancel()`（:336-339，无 try/catch）则被 `StreamSourceOperator.close` :395-399 的 try/catch 兜底为 warn 日志——今日回调表恒空（§2.2），无可抛源。无新立案点。
- **F9（半个成功）**：单元内 half-success 面全清单 = C6-001（replay 异常吞成 EOS）+ **C6-007（重启 run 零事件 EOS，本轮新增）**。定向核查 `DirectoryFileSourceFunction` 游标推进次序：`ctx.collect`（:189/:201）先于 `advanceCursor`（:191/:202）——崩溃恢复从旧游标重发，at-least-once，与 REPLAYABLE resume 声明一致，非缺陷。`stale writeReplayIndex` 归 C6-006 窗口（§2.1）。无其他面。
- **F3（静态可变面）**：第 2 轮 §2.3 全清单经本轮独立 grep 复核无漂移（`defaultResolver`/`GETTER_CACHE`/`SqlRowAggregateOps.CACHE` 已立案或已判边界；`BUILTINS`/`MANAGED_TYPES`/`MANAGED_TYPE_BY_NAME`/`FRAGMENT_ID_ATTRS` static-final 有界；无新增只进不出静态面）。HEAD 未变（8dcfa4cd0c），前轮判定全部有效。

## 3. 终止声明

第 3 轮（收口轮）按全部 4 个追加方向完成穷尽核查：C6-006 失效面扩展（§2.1，单元内线程/executor/生命周期标志三清单全部枚举）、"当前不可达"项可达性复核（§2.2，C6-002/005 前提均维持）、ReplaySubscription 注册表对称性独立核（§2.3，所有可达路径注册表被清空）、小文件兜底清查（§2.4，前两轮未全文阅读的 fraud 9 / flow model 35+validate 4+spi 3 / sql 12 文件全部出清）。

本轮新增 1 项：**C6-007（P1）**——C6-006 同族的代际守卫缺失站点（DirectoryFileSourceFunction 跨 run 永不复位的 cancelled 标志 × 区域重启实例复用契约），其同族清单（§2.1 三标志全表）已在本轮内穷尽闭合，无遗留未知站点。

**未发现 C6-007 之外的新增高价值问题。C6 维度盲区清单已穷尽，深挖终止。**（程序性说明：C6-007 为收口轮新增 P1，是否按 campaign 继续条件派独立复核轮由 campaign owner 裁定；自本维度盲审侧，上述方向均已闭合，无第 4 轮待查项。残留移交面不变：第 1 轮残留边界 3 的 owner doc "非 REPLAYABLE" 文档漂移，归 docs 维护。）

## 4. 覆盖声明

- 本轮人工全文/定点阅读：fraud 11 文件（`ReplayableCdcSourceFunction`、`DirectoryFileSourceFunction`、`FraudDetectionDemo`、`MockTransactionGenerator` 结构 + 4 pattern 结构 + 5 POJO 结构）；flow 全量字段级扫描（model 35 + validate 4 + spi 3 + builder 残余）；sql 12 文件结构/定点阅读；跨单元对照 7 文件（`DebeziumCdcSourceFunction` 全文、`StreamSourceOperator` 全文、`StreamTaskInvokable` :770-1016、`SubtaskTask` :90-234、`OperatorChain` :179-291、`SupervisionLoop` :470-700/:780-900、`XplSourceFunction` 全文）——仅用于核定可达性与复用契约，不作为发现对象。
- 机器扫描：基线全文复核 + 8 组补充 grep（executor/async、interrupt/sleep/join、closeable/IO/shutdown-hook、生命周期标志、静态字段复核、注册 API 全仓含非 Java 资源、subscribe 调用方全仓、DirectoryFileSourceFunction/S2 模型接线）。
- 与前两轮合并：C6 维度累计 **C6-001（P1）、C6-006（P1）、C6-007（P1，本轮）、C6-002~005（P2×4）**；16 项资源获取点配对表经第 2 轮抽查（4 项维持）+ 本轮残余方向闭合，整体维持。
- 未复核声明：本文件全部条目均为盲审初稿，复核状态"未复核"，待独立审计方核验。
