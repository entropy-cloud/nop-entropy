# C6 资源生命周期与泄漏 — nop-stream-orchestration-thin 第 2 轮深挖（盲审）

- 审计单元：`nop-stream/nop-stream-flow` + `nop-stream/nop-stream-sql` + `nop-stream/nop-stream-fraud-example`（src/main/java，98 files / 13,792 lines）。
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）。live code 为准；`_` 前缀生成文件不在发现对象内。
- 必要输入：第 1 轮 `C6-resource-lifecycle.md`（C6-001~005 + 16 项资源获取点配对表）与 grep 基线 `_tmp/cra-baseline-nop-stream-orchestration-thin.txt`，均已全文读取。
- 盲审纪律：除上述第 1 轮文件外，未读取 ai-dev/audits、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何文件；未读取其他维度审计输出。跨单元对照阅读（不作为发现对象）：`DebeziumCdcSourceFunction`、`EvalScopeImpl`（用于核定单元内代码的配对契约，与第 1 轮同口径）。

## 1. 本轮发现

### [nop-stream-orchestration-thin-C6-006] ReplayableCdcSourceFunction：emitter 线程取消不 join/interrupt——残留 emitter 的 finally-cancel 跨 run 打进复用实例，把重启 run 毒杀成静默 EOS

**文件:行号**：`nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java:126-131, 165-190, 213-216`（对照生产基类的实例复用契约 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:203-210, 226-237, 283-318, 331-344`）

**证据片段**：

```java
// ReplayableCdcSourceFunction.java:126-131 —— fire-and-forget：不保存线程引用，无人能 join/interrupt
@Override
public ICancellable subscribe(Consumer<ChangeEvent> action) {
    Thread emitter = new Thread(() -> runReplay(action), "fraud-s1-replay-cdc");
    emitter.setDaemon(true);
    emitter.start();
    return new ReplaySubscription();
}
```

```java
// ReplayableCdcSourceFunction.java:174-180, 185-189 —— park 不被打断；finally 无条件 cancel 外层 source
    if (emitDelayMs > 0) {
        Thread.sleep(emitDelayMs);            // cancel 不 interrupt，sleep 跑满剩余时长
    }
    ...
    if (finishLingerMs > 0 && !cancelled) {
        Thread.sleep(finishLingerMs);
    }
    ...
} finally {
    ReplayableCdcSourceFunction.this.cancel(); // 无论何种退出路径，最终都会执行
}
```

```java
// 生产基类声明的"同实例跨 restart 复用"契约与 cancel 的共享字段写入
// DebeziumCdcSourceFunction.java:230-237
// Reset lifecycle state: a region restart reuses this instance (the rebuilt
// operator chain shares the source function) after cancel() set running=false.
this.running = true;
this.pendingError = null;
initCompletionLatch();                        // :203-207 javadoc 明言防"上一轮 cancel 残留"
// DebeziumCdcSourceFunction.java:331-344
public void cancel() {
    running = false;
    if (completionLatch != null) { completionLatch.countDown(); }
    ICancellable sub = this.subscription;     // 读到的已是新 run 的订阅
    ...
}
```

**严重程度**：P1（取消/重启路径，罕见但可具体化竞态；泄漏对象为"重启 run 的存活与成功语义"）

**并发场景**：emitter 线程（`fraud-s1-replay-cdc`）在任务线程之外独立跑 replay。外部 `cancel()`、drain、或任务失败后的 region restart 均由任务/引擎线程执行；三者在共享字段 `running`/`completionLatch`/`subscription`/`source` 上无任何代际（generation）隔离。`ReplayEngine.cancelled`（volatile）只作用于旧引擎实例，而 `ReplayableCdcSourceFunction.this.cancel()`（finally 内）直接写**当前实例字段**——旧线程与新 run 在这些字段上交错。

**触发时序/泄漏链**：① run N 期间外部 `cancel()`（或 run N 因 `pendingError` 失败、基类 finally `source.stop()`）→ 旧引擎 `cancelled=true`；② 此刻旧 emitter 正 parked 在 `Thread.sleep(emitDelayMs)`/`finishLingerMs`（:174-180）或一个在途 `action.accept` 内——cancel 全链路无 `interrupt()`、subscribe 未保存线程引用（:126-131），残留存活时长 = 剩余 park 时长 + 至多一个在途事件；③ 基类 latch#N 已 countDown → run() N 返回、`runEntered` 复位；平台按声明契约**复用同一 source 实例**重启 region → run() N+1 置 `running=true`、建新 `completionLatch`、`source.subscribe` 装上**新** ReplayEngine（:234-237, :258-260）；④ 旧 emitter 醒来 → 循环顶看到旧 `cancelled=true` → break → **finally 无条件执行 `ReplayableCdcSourceFunction.this.cancel()`**（:185-189）→ 基类 `cancel()` 写共享字段：`running=false`（run N+1 的等待循环 :283-290 直接退出）、`completionLatch.countDown()`（读字段时已是 latch#N+1）、`this.subscription`/`this.source` 此刻已是 run N+1 的新订阅/新引擎 → 新订阅被 cancel、新引擎被 stop；⑤ run() N+1 以 `pendingError==null` 正常返回 → 重启 run 发出 0~个位数事件后**作为干净 EOS 成功收尾**，无失败信号、无重试——重启语义（从 checkpoint 恢复继续重放）被残留清理线程静默吞掉。泄漏的"资源"是基类用 fresh-latch + running 复位精心重建的第 N+1 轮生命周期本身（基类 :203-207 javadoc 恰好声明了它要防的就是"上一轮 cancel 残留"）。

**现状**：`runReplay` 的 finally-cancel 无任何代际守卫（不校验 `ReplayableCdcSourceFunction.this.source == ReplayEngine.this`）；`stop()`/`ReplaySubscription.cancel` 均只置标志，不打断 park；全类无线程引用、无 join。

**风险**：S1 场景在"失败重启 + emitter 残留 park 窗口重叠"时假成功（静默部分重放，与 C6-001 同族但机制不同：C6-001 是同 run 异常被吞，本条是**上一轮**残留清理线程杀死**下一轮**）。该类是 owner doc 点名的 S1 source 与"换 bean 即真实 CDC"升级样板，窗口由构造参数 emitDelayMs/finishLingerMs（为让 checkpoint 落盘而显式设为非零）决定，测试/演示接线常用数百毫秒——同 JVM 内 region restart 完全可能落入该窗口。纯同 run 内的双 cancel（残留 finally 与基类 finally 互怼）今日无害：基类 cancel 用 null 检查局部变量读 `subscription`/`source`（:336-343），回调表恒空——毒害面专指跨 run 重叠。

**建议**：最小修：`runReplay` finally 加代际守卫 `if (ReplayableCdcSourceFunction.this.source == ReplayEngine.this) cancel();`——run N 收尾后基类 finally 已置 `source=null`（:308-316），陈旧 cancel 变 no-op；自然完成路径 `source` 仍是本引擎，守卫放行。补强：`subscribe` 保存 emitter 引用，`stop()`/`ReplaySubscription.cancel` 中 `emitter.interrupt()`，把残留窗口从"一个 park 周期"压到≈0（InterruptedException → 立即进 finally）。

**失效面枚举**：`ReplayableCdcSourceFunction$ReplayEngine.subscribe/runReplay/stop`；`ReplayableCdcSourceFunction$ReplaySubscription.cancel`；基类 `DebeziumCdcSourceFunction.run/cancel/initCompletionLatch` 的复用契约面；所有依赖 region-restart 继续重放的 S1 测试与 `ReplayableCdcSourceFunctionTest` 族（若存在）——在"cancel/失败后立即重启"用例上会由红变绿失效。

**信心水平**：高（机制链逐字段核对：finally 无条件 cancel、无 interrupt、无 join、基类共享字段与新 latch 生命周期均有代码锚点；可达性依赖 restart 与残留 park 的时间重叠，已按 P1"罕见但可具体化"如实标注）。

**误报排除**：emitter daemon 属性与协作式取消模式本身已在第 1 轮 C6-005 误报排除中裁定为声明边界——本条立案对象不是"不 interrupt"的风格问题，而是 finally-cancel 缺代际守卫导致的跨 run 字段污染这一具体后果；`ReplayEngine.stop()` 的 synchronized（:214-216）与本链无交互，不重复立案；`runEntered` CAS 门（基类 :92/:317）两轮均已确认配对完整，不是本链入口。

**复核状态**： 已驳回（载重前提"平台按声明契约复用同一 source 实例重启 region"被 live code 证伪：ReplayableCdcSourceFunction 经继承 DebeziumCdcSourceFunction（implements SubtaskShardedSourceFunction，:59-61）落入 sharded 复制分支，全部建链/重建路径——GraphExecutionPlan:449-458、RemoteGraphExecutionPlanBuilder:313、SupervisionLoop:847——均经 `deepCopy(location)` → 接口默认 `copyForSubtask(location.getTaskIndex())`（StreamOperator:234-235）→ `StreamSourceOperator.copyForSubtask(int)` sharded 分支（:240-246）→ `DebeziumCdcSourceFunction.copyForSubtask(int)` 的 `serializationRoundTripCopy`（:524-530，DebeziumSourceSupport:188-204 为真 Java 序列化往返）逐 run 产出全新实例；残留 emitter 经内部类引用绑定的 `ReplayableCdcSourceFunction.this` 是旧实例，其 finally-cancel 只写旧实例字段（旧 running/旧 latch/已置 null 的旧 subscription/source），无法抵达新 run 的任何状态——毒杀链断在"实例复用"这一环。第 4 轮 §2.7 "单元内零实现者"系文本 grep 不可见继承接口的方法论错误（implements 子句在生产基类文件中，不在本单元）；基类 javadoc :230-231 "the rebuilt operator chain shares the source function" 对 sharded 基类自身不准确（单元外 docs bug，疑为初审误信来源，移交 docs 维护）。详见第 1 轮文件文末「维度复核结论」）

## 2. 排除项/定向核查

### 2.1 第 1 轮"配对健全面"抽查复核（4 项，全部维持原判）

| 抽查面 | 复核内容与异常分支 | 结论 |
|---|---|---|
| R1 `DirectoryFileSourceFunction.emitRemaining` TWR（:151-205） | 三类异常分支逐一核对：`in.skip` 短读 IOException（:160-163）、`emitLine` InterruptedException（:207-216，经 :189/:201 传播）、`ctx.collect` 运行时异常——全部沿 TWR 边界穿出并 close；`cancelled` 两处提前 return（:169-175）也在 TWR 内。单资源，无 suppressed 累积面 | 维持"配对完整" |
| R14 `cursors` 注册表（:218-298） | snapshot 拷贝在监视器内（:251-254）；restore 空 map 守卫（:275-280）+ clear/putAll 原子于监视器（:281-284）；两个非空 restore 并发时"同内容全量覆盖"最后写胜出，无丢失/滞留 | 维持"配对完整" |
| R8 per-call scope（XplFunctionSupport :35-52 + 全部消费点） | 重读平台 `EvalScopeImpl.java:96-107`：child 仅持 `parentScope` 引用，parent 从不登记 child；`SideOutputTapOperator.matches`（:75-79）等 8 个消费点全部 per-invocation `newCallScope()`，无实例级 scope 复用；`ROOT_SCOPE` 本身从不被直接绑定（绑定只落在 child） | 维持"配对完整，无泄漏" |
| R4/R5 基类承载的 source/失败监听器配对（DebeziumCdcSourceFunction :297-318） | **释放路径中间异常专项**：基类 finally 三步（unregisterFailureListener → subscription.cancel → source.stop）各自独立 try/catch（:298-315），任一步抛异常不跳过后续步骤，`runEntered.set(false)` 收尾必达——无"前一步异常吞掉后续释放"面 | 维持"配对完整"（含中间异常语义） |

### 2.2 释放路径中间异常（F8 定向）

单元内全部清理面枚举：① TWR ×1（单资源，JVM suppressed 语义天然覆盖）；② `runReplay.finally` → 单一 `cancel()` 调用，基类 `cancel()` 内部四步（running/latch/sub/source）均为不可抛操作（布尔写、countDown、volatile 读+接口调用，ReplaySubscription.cancel 迭代空表）——今日无"cleanup 自身抛异常中断后续释放"的具体链；③ fraud `FraudDetectionDemo.nfa.close()` 非 finally——第 1 轮已按演示 main 校准排除，不重复。**结论：单元内无新立案点。**

### 2.3 C6-003/004 兄弟站点扩展（F3 全单元静态可变面扫描）

对三模块做精确 grep（`static [volatile] [final] <类型> <字段>` 全量 + 逐条排除法），单元内静态可变状态全清单：

| 面 | 判定 |
|---|---|
| `StreamScalarFunctionResolver.defaultResolver`（sql :31） | 已立案 C6-003 |
| `RecordColumnAccess.GETTER_CACHE`（sql :36-37） | 已立案 C6-004 |
| `SqlRowAggregateOps.CACHE`（sql :27-38） | 已声明有界边界（第 1 轮） |
| `StreamSqlAggregations.BUILTINS`（:32-45） | static-init 构建 + `Collections.unmodifiableMap`，封闭 7 项集合——有界，非缺陷（本轮全文复核） |
| `StreamSqlExprCompiler.MANAGED_TYPE_BY_NAME`（:199）与 `StreamSqlCompiler.MANAGED_TYPES`（:96）、`StreamModelDslBuilder.FRAGMENT_ID_ATTRS`（:834） | static-init 固定内容（`Set.of`/字面集），有界 |
| `XplFunctionSupport.ROOT_SCOPE`（:35） | static 单例 scope，但永不直接写入（所有变量绑定落在 per-call child，见 2.1-R8）——非累积面 |
| `StreamSchemaRegistry`/`GlobalBeanFunctionResolver`/`InMemoryBeanFunctionResolver`/`StreamAggregatorFunctionResolver` | 无静态字段；`StreamSchemaRegistry` 实例不可变且 per-build（:34-38 javadoc 声明 + 代码核实）；`InMemoryBeanFunctionResolver.beans`（:24）为构建期实例 map |

**结论：F3 无新增只进不出静态面。**

### 2.4 fraud-example 临时文件/目录生命周期（定向）

对单元 src/main/java 全量 grep（`createTemp|File.create|deleteOnExit|.delete(|Files.walk/delete/createTemp|mkdir|FileOutputStream|FileWriter|RandomAccessFile|Paths.get` 及 `new File|listFiles|FileInputStream`）：**单元代码不创建、不写入、不删除任何文件**。唯一文件接触点是 `DirectoryFileSourceFunction`（:133-134 `listFiles` 只读枚举、:159 `FileInputStream` 只读流，目录由外部提供）；replay index 存于内存态 `NopStreamOffsetBackingStore`（`offsetStore.set`，:205-211），非文件。第 1 轮 C6-001 证据链中"writeReplayIndex IO/状态异常"实为内存 map 写入。**结论：无临时文件残留路径，方向关闭，无发现。**

### 2.5 listener/handler 注册对称性（第 1 轮未点名类）

fraud 运行期类逐一定性（本轮全文复读）：`UserHistoryEnricher`（stateless per-record，`lastCityState` 惰性初始化为幂等竞态、每 subtask 单任务线程访问，良性）、`UserTransactionHistory`（state handle 包装）、`CdcChangeDecoder`/`TransactionLineParser`/`FraudAlertPatternFunction`/`AlertCountAggregate`/`TransactionWindowAggregate`（无状态或纯累加器 POJO）、`DemoKeyedStateStore`（javadoc :22-37 显式声明 demo-only "fresh disconnected state" 语义——已声明边界）。flow 侧 `XplMap/Filter/FlatMap/Reduce/Sink/Source`、`EvalActionKeySelector`、`JoinSideKeySelector`（无状态单例）、`JoinSideTagFunction`、`SideOutputTapOperator`（stateless tap，`copyForSubtask` 每 subtask 新实例，:58-61）——全部无注册/监听器面。sql 侧 `StreamAggregatorFunctionResolver` 无静态注册表。**结论：无新注册不对称点。**

## 3. 终止声明

第 2 轮深挖按全部 5 个追加方向（配对面抽查、释放路径中间异常、F3 兄弟站点、临时文件生命周期、注册对称性）完成核查：新增 1 项 P1（C6-006），其余方向均为排除项（2.1~2.5）。第 1 轮配对表 16 项中抽查的 4 项健全面经异常分支重核全部维持原判；单元内静态可变面、文件生命周期面、注册对称面均已闭合。**未发现新的高价值问题。C6 维度深挖终止。**（唯一残留建议移交面：第 1 轮残留边界 3 的 owner doc "非 REPLAYABLE" 文档漂移，归 docs 维护，不属 C6 立案对象。）

## 4. 覆盖声明

- 本轮人工全文阅读：fraud 运行期 9 文件（`ReplayableCdcSourceFunction`、`DirectoryFileSourceFunction`、`DemoKeyedStateStore`、`UserHistoryEnricher`、`UserTransactionHistory`、`FraudAlertPatternFunction`、`TransactionWindowAggregate`、`CdcChangeDecoder`、`TransactionLineParser`）；flow 运行期 10 文件（`XplFunctionSupport`、`XplSinkFunction`、`XplSourceFunction`、`XplMapFunction`、`SideOutputTapOperator`、`JoinSideKeySelector`、`StreamSchemaRegistry`、`StreamSqlAggregations` 及 `XplFilter/FlatMap/Reduce/EvalActionKeySelector/JoinSideTagFunction/InMemoryBeanFunctionResolver` 的字段/结构级核对）；跨单元对照 2 文件（`DebeziumCdcSourceFunction` 全文、`EvalScopeImpl` 前 140 行）。
- 机器扫描：静态可变字段精确 grep（三模块全量）、文件生命周期操作 grep（fraud 全量）、第 1 轮基线三节（C4/C6/closeable）复核。
- 与第 1 轮合并后，C6 维度累计发现 C6-001（P1）、C6-006（P1）、C6-002~005（P2×4）；本单元 C6 面整体单薄的结构性结论（第 1 轮：唯一文件流/线程/AutoCloseable 均在 fraud-example）经第 2 轮独立复核成立。
- 未复核声明：本文件全部条目均为盲审初稿，复核状态"未复核"，待独立审计方核验。
