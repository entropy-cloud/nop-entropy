# nop-stream 开放式对抗审查报告（open-ended adversarial audit）

> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-stream-productization

## 基本信息

- **审核目标**: `nop-stream/`（10 子模块 + quickstart，~1317 Java 文件）代码、配置、测试、脚本与公共契约
- **审计日期**: 2026-09-04（任务时间戳 2026-09-03-1951）
- **方法**: 按 `ai-dev/skills/open-ended-adversarial-review-prompt.md` 开放探索。切入点 = 姊妹 multi-audit（同时间戳，44 项发现）自报的四个盲区 + 执行验证：
  1. **实际执行 quickstart 脚手架**（P-REQ-25 验收物，此前从未被任何审计真实运行）；
  2. **热路径算法文件逐行深读**（WindowOperator 2208 行 / StreamTaskInvokable 1067 行 / InputGate / CheckpointBarrierTracker / CEP NFA 全家族 — multi-audit 明示未逐行审）；
  3. **连接器 + fraud-example + quickstart 模板全读**；
  4. 跨边界连锁追踪（job 命名 → checkpoint 存储 → 跨作业恢复）。
- **优先级口径**（mission 指定）: `[P0]` 阻断（契约断裂/错误行为/数据丢失/安全/变更行为缺测试）；`[P1]` 实质缺陷或契约漂移，必须修复；`[P2]` 非阻断修缮（文档行腐、措辞、命名、轻微缺陷记录在案）。
- **全部高危发现均经主审（本 session）对 live 代码二次直读核实**；测试基线：quickstart verify.sh 实跑 3 次（含 1 次故意污染复现）。

## 与既有审计的去重（已知未修复项，不重复展开）

姊妹 multi-audit `2026-09-03-1951-multi-audit-nop-stream-productization.md` 的 11 项 P1（F-01 悬挂 future、F-02 RocksDB 目录无回收、F-03 restore 无 hash 复验、F-04 watermarkInterval 静默丢弃、F-05 ListState Object.class、F-06 enableCheckpointing 静默跳过、F-07 multi-JVM gated 不在 CI、F-08 reset 工具路径穿越、F-09 ops REST 认证、F-10 反序列化无 filter、F-11 test-jar 启动类）**全部仍未修复，维持原报**，本报告不重复。本报告 28 项发现均经 `rg` 全库审计档案扫描确认**未被任何历史审计/bug/lesson 收录**（含 `stream-job`/`nop-stream-checkpoints`/`java.io.tmpdir` 关键词零命中）。

---

## P0 发现（1 项）

### [AR-1] 全部作业共享同一个 checkpoint 存储目录：JobGraph 名硬编码 "stream-job" + 机器级全局默认路径 + 无条件恢复 — 跨作业状态污染（已被实跑复现击穿 quickstart 验收物）

- **优先级**: `[P0]` — 默认配置下的错误行为 + 跨作业数据污染（状态完整性）+ 24 小时内击穿了 P-REQ-25 的验收证据；不修复则任何两台共享机器上的本地作业互踩。
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java:123`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1036-1040`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/PartitionedPlanGenerator.java:64`；`quickstart/template/src/main/resources/_vfs/quickstart/topology2-window-aggregation.stream.xml:20-21`
- **证据片段**:
  ```java
  // JobGraphGenerator.java:122-123 — 注释宣称“来自 streamGraph”，实际硬编码：
  // Create a new JobGraph with the job name from streamGraph
  JobGraph jobGraph = new JobGraph("stream-job");
  ```
  ```java
  // GraphModelCheckpointExecutor.java:1036-1040 — 无 path 配置时落到机器级全局目录：
  String basePath = config.getStorageProperty("path");
  if (basePath == null || basePath.isEmpty()) {
      basePath = System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoints";
  }
  ```
  ```xml
  <!-- quickstart 模板：storageType="local" 且无 path → 官方教材直接继承该默认 -->
  <checkpoint enabled="true" interval="1000" minPause="500"
              processingGuarantee="AT_LEAST_ONCE" storageType="local"/>
  ```
- **现状**: 完整因果链（全部 live 核实）：`env.execute("my-job")` 的作业名被忽略 → JobGraph 名恒为 `"stream-job"`（`JobGraphGenerator:123`）→ `PartitionedPlanGenerator:64` 以该名作为 jobId → checkpoint 计划/协调器/存储全部以 `"stream-job"` 为键 → 默认存储目录 = `${java.io.tmpdir}/nop-stream-checkpoints/stream-job/pipeline-0/`。**任何**本地模式作业（默认配置）在**同一台机器**上共享这一个目录，且 `restoreFromCheckpoint`（:1043-1093）在发现存量产物时无条件尝试恢复。
- **实跑复现（本审计 2026-09-04）**:
  1. 13:15 `bash nop-stream/quickstart/verify.sh` → **3 测试挂 2**（Topology2/Topology3 双双 `ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED`，stored fingerprint componentCount=2 vs current=7）——起因是 12:22（约 1 小时前）multi-audit 测试基线运行在全局默认路径留下的 `{0.checkpoint,0.epoch}` 残留（目录 mtime 12:22 实证）；
  2. `rm -rf ${TMPDIR}/nop-stream-checkpoints` 后 13:18 重跑 → **3/3 全绿**；
  3. 连续第三次重跑 → 仍绿（成功作业完成时清理自身产物；失败/被杀作业的产物**永久残留**继续污染后续无关作业）。
- **风险**: 两分支均真实：**指纹不同 → 无关作业硬失败**（上述 quickstart 案例，且 P-REQ-25 的 "met" 验收证据在脏机器上不可复现——验收报告所引 `_tmp/quickstart-verify` 留档即属“清洁机器运气”）；**指纹相同（同形拓扑）→ 静默恢复他人状态**——作业 B 从作业 A 的 offset/聚合状态续跑，数据重复或串号，无任何告警。这是默认配置（quickstart 模板与 user-guide 快速起步路径所教）下的数据完整性违约。
- **建议**: ① `JobGraphGenerator` 使用 `streamGraph` 真实作业名（修掉注释与实现相悖）；② 默认路径按 jobId 隔离仍不够——默认存储应在作业完成/失败终止后清理，或恢复前校验作业身份（如 manifest 记录启动 token）；③ quickstart 模板测试使用每运行独立的临时目录（`@TempDir`/jobId+时间戳）；④ 顺带统一分布式执行器的另一个默认目录 `nop-stream-checkpoint`（单数，`EmbeddedDistributedExecutor.java:179`、`RpcDistributedExecutor.java:240`）与本地路径（复数）的不一致。
- **信心水平**: 确定（三段实跑复现 + 五处代码直读全链核实）。
- **发现来源视角**: 代码生成受害者 + 执行验证（“验收物从未被真跑”这一 multi-audit 自报盲区的正面命中）。

---

## P1 发现（14 项）

### [AR-2] evictor 驱逐结果从不写回窗口状态，且 `evictAfter` 收到的是 `evictBefore` 之后的 size — 公共 API `countWindow` 状态无界增长、被驱逐元素永久重返聚合

- **优先级**: `[P1]` — 公共 API（`KeyedStream.countWindow`，`KeyedStream.java:84-96`）路径上的正确性 + 无界资源增长双重实害。
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:983-1016`
- **证据片段**:
  ```java
  evictor.evictBefore(wrapped, wrapped.size(), window, evictorContext);
  List<IN> evictedElements = new ArrayList<>();
  for (TimestampedValue<IN> tv : wrapped) { evictedElements.add(tv.getValue()); }
  userFunction.process(key, window, processContext, (ACC) (Iterable<IN>) evictedElements, ...);
  evictor.evictAfter(wrapped, wrapped.size(), window, evictorContext);  // size 已被 evictBefore 缩小
  ```
- **现状**: 驱逐只作用于 `wrapped` 拷贝列表，底层 `newListWindowState`/`windowContentsState` 从不裁剪——被驱逐元素在后续每次 fire 中永久重返；`evictAfter` 的 `size` 参数在 `evictBefore` 之后求值（Flink 语义传原始 pre-eviction size）。`countWindow`（GlobalWindows + CountEvictor + CountTrigger）无清理定时器（GlobalWindow `maxTimestamp == Long.MAX_VALUE` 跳过 `registerCleanupTimer`），ACCUMULATING 模式下每 key 状态随输入总量线性增长。与 Flink 语义（evictor `iterator.remove()` 物理收缩 pane state）相悖。
- **风险**: 无界流上 O(总输入) 的内存/checkpoint 增长；自定义双回调 evictor 因 size 参数错误产生错误驱逐决策。
- **建议**: `evictAfter` 后将存活元素写回状态并同步裁剪 `elementTimestampsState`；两处 `wrapped.size()` 改传 pre-eviction 计数；为 GlobalWindows+evictor 组合补清理路径或 fail-fast。
- **信心水平**: 确定。
- **发现来源视角**: 热路径逐行深读（multi-audit 自报盲区）。

### [AR-3] 内部后端路径从不存储元素时间戳，evictor 元素全部被打上“当前水位”假时间戳 — TimeEvictor 永不驱逐、静默错聚合

- **优先级**: `[P1]` — 公共 builder API（`builder.evictor(TimeEvictor.of(...))`）上的静默错误输出。
- **文件**: `WindowOperator.java:986-997`（假时间戳回退）、`:1398-1411`（storeElementTimestamp）、`open():434-462`（elementTimestampsState 仅在非内部后端分支创建）
- **证据片段**:
  ```java
  // emitWindowContents 回退分支：
  if (storedTimestamps != null && idx < storedTimestamps.size()) {
      elementTimestamp = storedTimestamps.get(idx);
  } else {
      elementTimestamp = internalTimerService.currentWatermark();   // 假时间戳
  }
  ```
  ```java
  // open(): windowStateDescriptor 走内部后端时 elementTimestampsState 恒为 null，
  // storeElementTimestamp(:1399) 直接 return —— 时间戳从未落库
  ```
- **现状**: builder 总是提供 descriptor → Memory/RocksDB 内部后端路径 `elementTimestampsState == null` → 每个 `TimestampedValue` 的 stamp 都是 `currentWatermark()`；`TimeEvictor`（按元素时间戳与 `watermark - windowSize` 比较）因此永不驱逐、驱逐决策使用"现在"而非元素时间。次级问题：fallback 路径中 merging assigner 的时间戳按 `stateWindow` 写、按 `actualWindow` 读（:728 vs :986），同样恒 null。
- **风险**: 使用 TimeEvictor 的作业聚合结果静默错误（应驱逐元素被聚合），无任何异常或告警。
- **建议**: 内部后端路径同样持久化元素时间戳（或如 Flink 直接以 `TimestampedValue<IN>` 入 list state），并统一 stateWindow/actualWindow 的读写解析。
- **信心水平**: 确定。

### [AR-4] merging 窗口的 paneTracking 表按 actualWindow 登记、按 stateWindow 删除 — 会话窗口 pane 跟踪条目永久泄漏并随 checkpoint 持久化膨胀

- **优先级**: `[P1]` — 与已修复的 P1-INV-1（triggerAccumulators 同类泄漏，见 :948-965 自述）同族的无界增长缺陷，且泄漏进 checkpoint 载荷。
- **文件**: `WindowOperator.java:1055-1057`（paneKey 用 actualWindow）、`:1027-1033`（computePaneInfo 以 actualWindow 调用）、`:1457-1461`（clearWindowContents 以 stateWindow 删除）、清理调用点 `:743,839,853,916,930`、快照 `:571-573`
- **证据片段**:
  ```java
  private String paneKey(K key, W window) {
      return key + STATE_KEY_SEPARATOR + windowNamespace(window);   // 登记用 ACTUAL window
  }
  ...
  private void clearWindowContents(K key, W window) {              // 清理路径传入 STATE window
      String paneKey = paneKey(key, window);
      if (paneTracking != null) { paneTracking.remove(paneKey); }  // merging 下删错键
  ```
- **现状**: merging assigner（会话窗口）在 merge 后 `actualWindow != stateWindow`，actual-window 的 pane 条目永不删除；ACCUMULATING 模式（builder 默认）下根本没有路径删除它。TimeWindow 键条目还会被 `snapshotPaneTracking` 全量写入 checkpoint 并恢复 — 内存与 checkpoint 载荷随 distinct 会话窗口数单调增长。`TestWindowOperatorMergingCleanupInvariant` 不含 pane 断言（rg 零命中），测试未守住。
- **风险**: 长时会话窗口作业无界内存/checkpoint 增长（正是 P1-INV-1 修给 triggerAccumulators 的那类缺陷的姊妹漏网）。
- **建议**: 清理路径同时按 trigger 的 actual window 移除 pane 条目（或统一以 stateWindow 键控 pane 跟踪）；补 merging 场景 paneTracking 收缩断言测试。
- **信心水平**: 确定。
- **发现来源视角**: 死代码清道夫变体——读已修复缺陷的注释时发现同族残留。

### [AR-5] 单通道 InputGate 完全绕过 aborted-barrier 过滤与重复 barrier 去重 — 违反 Stage 45 书面契约，并为 AR-6 提供触发通道

- **优先级**: `[P1]` — 已文档化的分布式契约（“迟到的已中止 barrier 不得腐蚀后续 epoch”）在单输入拓扑上静默失守。
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:325-330,467-469`（对照多通道路径 `:736-755`）
- **证据片段**:
  ```java
  public Optional<StreamElement> read() {
      if (channels.size() == 1) { return readSingleChannel(); }
      return readMultiChannel();
  }
  ...
  // readSingleChannel:
  // Single-channel = trivially aligned, so barriers are returned
  // as-is without barrier alignment / handleBarrierNonRecursive.
  return Optional.of(element);
  ```
- **现状**: 多通道路径 `handleBarrierNonRecursive` 丢弃已中止 epoch 的迟滞 barrier（:736-744）并忽略同通道重复 barrier（:752-755）；单通道路径两者皆不做——生产 abort 路径（`GraphModelCheckpointExecutor` 本地 abort → `abortedBarriers` 集合）在单输入任务上完全失效：死 epoch 的 barrier 仍被算子快照、并被 `AbstractStreamOperator.processBarrier` 继续转发下游。
- **风险**: 死 epoch 迟滞 barrier 触发虚假快照 + 下游污染（Stage 45 契约违约）；重复 barrier 直接喂给 AR-6 的双重 ACK 窗口。
- **建议**: 单通道路径对 barrier 元素执行同样的 `abortedBarriers.contains` + 同通道重复 id 检查（1 通道的平凡对齐语义不变）。
- **信心水平**: 确定（两路径代码直读对照）。

### [AR-6] `CheckpointBarrierTracker.acknowledgeOperator` 无按算子去重 — 重复 ACK 双扣共享计数器，epoch 带缺失算子快照提前“完成”

- **优先级**: `[P1]` — exactly-once 完整性孔洞：不完整快照被当作完成 checkpoint 交付。
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:186-231`
- **证据片段**:
  ```java
  if (state.operatorsToAck.get() <= 0) {     // 仅拦截“完成后”的重复
      LOG.debug("Ignoring duplicate ACK ..."); return;
  }
  ...
  if (state.operatorsToAck.decrementAndGet() == 0) {   // 同一算子 ACK 两次各扣一次
      snapshotToDeliver = state.snapshot;
      callbackToFire = completionCallback;
      inFlight.remove(state.checkpointId);
  }
  ```
- **现状**: 守卫只拦“计数已到零”的迟到 ACK，不追踪算子身份——同一算子的快照回调对同一 epoch 触发两次时（AR-5 的单通道重复 barrier 即活体通道；远程传输 at-least-once 重投递同理），N-1 个真实 ACK + 1 个重复即让计数归零，epoch 以**缺一个算子状态**的快照宣告完成。
- **风险**: 恢复时静默缺失算子状态 — exactly-once 被无声破坏（无错误、无告警，restore "成功"）。
- **建议**: 每 epoch 记录 `Set<Integer> acknowledgedOperators`，已见算子直接忽略。
- **信心水平**: 机制确定；触发需要重复投递（AR-5 已提供在库通道）— 很可能。
- **发现来源视角**: 异常路径侦探。

### [AR-7] MIDDLE/SINK 任务把 cancel/中断退出当作成功完成 — finish() + MAX_WATERMARK + 无条件 EOS，被取消任务以“有界完整”姿态提交截断数据

- **优先级**: `[P1]` — 与 SOURCE 路径已文档化的 Stage-44 策略（失败不关输出）直接相悖；取消路径把截断流伪装成完整流。
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/StreamTaskInvokable.java:828-853`（循环出口无差异 break）、`:715-731`（invokeMiddle 成功终态化 + finally 无条件 `closeOutputWriters()`）、`:738-762`（invokeSink 同形）；对照 `:673-700`（invokeSource 的失败保留输出策略及其注释）
- **证据片段**:
  ```java
  if (inputGate.isAllFinished()
          || Thread.currentThread().isInterrupted()
          || mailboxExecutor.isCancelled()) {
      break;                                   // 与 EOS 同一个 break
  }
  ...
  if (inputError == null) {                    // cancel/interrupt 下仍为 null
      headInput.processWatermark(Watermark.MAX_WATERMARK);   // 成功终态语义
      operatorChain.finish();                  // 连接器 flush/commit
  }
  } finally {
      closeOutputWriters();                    // 无条件向下游发 EOS
  ```
- **现状**: `processInputGate` 对 EOS、中断、协作取消三种出口无差别 break；`invokeMiddle`/`invokeSink` 随即执行成功路径（终态水位 + `finish()` + EOS 关闭输出）。而 `invokeSource` 的注释（:681-688）明确说明失败时关闭输出会强制 EOS、毁掉 producer-region restart——同一设计意图没有传导到 MIDDLE/SINK。
- **风险**: checkpoint-abort 取消链路中未被取消的下游任务收到 EOS + finish → sink 以有界完整语义提交截断数据，早于任何恢复动作；2PC sink 在错误窗口 commit。
- **建议**: `processInputGate` 返回退出原因枚举；CANCELLED/INTERRUPTED 时跳过 finish/MAX_WATERMARK 并按失败语义保留输出（镜像 invokeSource）。
- **信心水平**: 行为确定；非意图（与自身注释/源路径设计矛盾）— 很可能。
- **发现来源视角**: 事务边界追踪者。

### [AR-8] MIDDLE/SINK 在 `finish()` 之前先发 MAX_WATERMARK — P1-5 修复在源路径成立、中下游路径反向

- **优先级**: `[P1]` — 缓冲型算子/连接器的尾批记录错过最终窗口，静默丢尾部数据（正是 P1-5 要修的缺陷类，只修了一半路径）。
- **文件**: `StreamTaskInvokable.java:722-726`（invokeMiddle）、`:750-754`（invokeSink）；正确对照 `:677-679/:694`（invokeSource：先 finish 后水位）、`:787-788`（invokeSelfContained：先 finish 后水位）
- **证据片段**:
  ```java
  // P1-5: finish() before MAX_WATERMARK and close so connectors flush.   <- 注释
  if (inputError == null) {
      headInput.processWatermark(Watermark.MAX_WATERMARK);   // 代码先做了这个
      operatorChain.finish();                                 // ...然后才是 finish
  }
  ```
- **现状**: MIDDLE/SINK 两条路径先向链头灌 MAX_WATERMARK（触发本链窗口算子的最终窗口）再 `finish()`（链内缓冲算子此刻才吐尾批）——尾批记录到达窗口算子时最终窗口已发射完毕。SOURCE 与 SELF_CONTAINED 路径顺序正确，代码与自己的 P1-5 注释直接矛盾。
- **风险**: 中下游链中任何在 `finish()` 里 flush 的算子（P1-5 点名的 BatchConsumerSinkFunction 类）尾部贡献静默丢失。
- **建议**: 交换两行（与 SOURCE/SELF_CONTAINED 对齐）；补一条 MIDDLE 链带缓冲算子的尾批断言测试。
- **信心水平**: 确定（代码 + 注释自相矛盾，四路径直读对照）。
- **发现来源视角**: 新人开发者（“为什么这四条路径两两不一样？”）。

### [AR-9] WatermarkStatus 在任务边界被整体丢弃 + InputGate 最小值合并不排除 idle 通道 — 上游一旦空闲，下游事件时间永久停摆，`WatermarkStrategyWithIdleness` 跨任务静默失效

- **优先级**: `[P1]` — core 公开提供并文档化的 idleness 机制（`WatermarksWithIdleness`/`WatermarkStrategyWithIdleness`、算子级 `processWatermarkStatus` 逻辑俱全）在跨任务链路上静默失效；owner doc / user-guide 无任何限制声明（rg `idle|idleness` 零命中）。
- **文件**: `StreamTaskInvokable.java:974-977`（RecordWriterOutput.emitWatermarkStatus 空实现）、`:1043-1044`（广播输出同样为空）；`InputGate.java:403-411`（getCurrentWatermark 对所有通道取 min、无 idle 追踪）、`:554-560`（status 元素原样透传给算子）
- **证据片段**:
  ```java
  @Override
  public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
      // Not forwarded across task boundaries
  }
  ...
  public long getCurrentWatermark() {
      long min = Long.MAX_VALUE;
      for (long wm : currentWatermarks) { if (wm < min) min = wm; }   // idle 通道钉死 min
  ```
- **现状**: `StreamSourceOperator` 会发射 `WatermarkStatus.IDLE`（含 WatermarksWithIdleness 路径），链内算子也有 idle 感知合并逻辑——但跨任务输出把它整体吞掉，且 `InputGate`（真正的跨任务阀门）水位合并不追踪/不排除 idle 通道：某上游子任务空闲后其最后水位永久钉住下游合并水位。
- **风险**: 分区不均/静默分区的真实场景（多子任务 source 的常态）下，下游窗口/timer 永久停转——“作业活着但无进展”，只能靠 liveness 启发式发现；用户按文档启用 idleness 策略得到静默 no-op。
- **建议**: `RecordWriter.emitWatermarkStatus` 跨任务透传 + `InputGate` 按 Flink `StatusWatermarkValve` 语义维护 per-channel idle 状态并在合并时排除；短期至少在 owner doc 声明“idleness 仅链内有效”的限制。
- **信心水平**: 停摆机制确定；是否“已接受的范围外”无文档证据 — 很可能。

### [AR-10] CepOperator 以 `Object.class` 创建 keyed 后端，关闭平台 AR-01 键重物化 — 非 String 键作业恢复后 CEP 状态整体不可寻址（静默从零开始）

- **优先级**: `[P1]` — 平台为这类缺陷专门修过 AR-01/AR-22（WindowOperator `setKeyType(keyClass)`，:466-471 注释自述“否则静默丢窗口输出”），CEP 恰好把两个修复都旁路了。
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:269,280`；对照 `WindowOperator.java:421,471`；旁路机制 `MemoryStateSerDe.java:717`（`keyType != Object.class` 守卫）
- **证据片段**:
  ```java
  this.keyedStateBackend = this.stateBackend.createKeyedStateBackend(Object.class);  // L269
  ...
  keyedStateStore = new MemoryKeyedStateBackend<>(Object.class);                     // L280
  // MemoryStateSerDe.deserializeKey 的重物化修复对 Object.class 显式不生效
  ```
- **现状**: 本地 JSON checkpoint 路径会把 `Long(123)` 键往返成 `Integer(123)`，`TypedNamespaceAndKey#equals` 类敏感 — 平台修复要求后端带真实键类才能重物化；CepOperator 传 `Object.class`（无任何生产调用方注入后端键类型，operator 恒自建），恢复出的 `nfaState/eventQueues/SharedBuffer` 全部落类漂移键下，运行时以原始 `Byte/Long` 键访问永远 miss。默认非 keyed 路径（`CEP.pattern(stream, pattern)` → `NullByteKeySelector` → `Byte` 键）与数值键作业全部命中。
- **风险**: checkpoint 恢复后 CEP 状态静默清零（在途部分匹配、排队事件、共享缓冲全丢）——正是 AR-01 当年修掉的“恢复成功但状态不可见”。
- **建议**: 把 KeyedStream 的键类（非 keyed 全局路径回落 `Byte.class`）传入 `createKeyedStateBackend`，镜像 WindowOperator 的做法。
- **信心水平**: 确定（全链直读：无 setter 调用方 + 守卫代码 + 对照修复）。

### [AR-11] CEP 事件时间 timer 台账的键对象经 JSON 往返裸存 — 恢复后水位排水把真实键的 timer “消费掉”却从未为其发射

- **优先级**: `[P1]` — 恢复正确性：missed timeout / 事件滞留 / SharedBuffer 泄漏，非 String 键（含默认非 keyed 路径）全中。
- **文件**: `CepOperator.java:470-481`（snapshot 裸存 key）、`:501-509`（restore 原样 computeIfAbsent）、`:536-543`（排水切到漂移键）、`:727-736`（onEventTime STEP5 删除台账）
- **证据片段**:
  ```java
  form.put("key", entry.getKey());                       // 裸对象 → JSON
  ...
  TreeSet<Long> timers = registeredEventTimeTimersByKey
          .computeIfAbsent(form.get("key"), k -> new TreeSet<>());   // 回来可能是 Integer
  ...
  setCurrentKey(entry.getKey());                         // 用漂移键排水
  onEventTime(currentWatermark);                         // 真实键的 timer 被删但从未触发
  ```
- **现状**: 台账经 `CheckpointSerDe` JSON 路径持久化，小数值键回来变 `Integer`（`Byte` 键 → `Integer` 同理）；代码自己承认值层面的 Integer/Long 二义性（:503 注释、`addAllTimerTimestamps` :655-664 专门归一）却没对键做同样处理。恢复后排水对空桶跑 `onEventTime` 并把真实键的 pending timer 删除——排队事件永不被处理、部分匹配永不全时、buffer 状态泄漏到该键下一个事件偶然到达。
- **风险**: 与 AR-10 同根（JSON 键类敏感）但独立修复点（台账序列化 vs 后端键类）。
- **建议**: 键以类型化形式持久化（类名 + 规范串，恢复时重物化），或恢复时与已见活键归一。
- **信心水平**: 确定。

### [AR-12] 数据面 topic 名内嵌 `->` 与未消毒 jobId — `>` 在 Kafka topic 名中非法，Kafka/Pulsar 后端在首条消息即 `InvalidTopicException`

- **优先级**: `[P1]` — 文档化生产后端（distributed-runbook 的 Kafka/Pulsar 数据面）无条件失败；被 gated 测试（不连真 broker）掩盖。
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java:154,383`；`StreamTopicNaming.java:31-35`
- **证据片段**:
  ```java
  String edgeId = edge.getSourceVertex() + "->" + edge.getTargetVertex();   // 含 '>'
  ...
  return TOPIC_PREFIX + "." + jobId + "." + edgeId + "." + sourceSubtask + "." + targetSubtask;
  ```
- **现状**: Kafka 合法 topic 字符集 `[a-zA-Z0-9._-]`、≤249 字符；`>` 无条件出现，jobId 原样拼接（CJK/空格/`:` 均可能）。`KafkaMessageService` 将 topic 直通 `ProducerRecord`/`consumer.subscribe`（:206,:276，子代理直读），`DataPlaneMessageServiceAdapter` 的 subscribeName 同样嵌入裸 topic。gated e2e `TestDataPlaneKafkaBackendE2E` 自己就用 `EDGE_ID="src->tgt"`——真 broker 下同样会挂，被门控掩盖。
- **风险**: 任何真实 Kafka/Pulsar 部署第一条消息即失败（与 items 28/31 的已知数据面缺陷叠加，但这是独立、确定性的硬失败）。
- **建议**: `buildTopic` 内统一消毒（非法字符映射 + hash 后缀消歧 + 长度上限），或 edgeId 改用合法分隔符。
- **信心水平**: 确定（Kafka 校验规则 + 代码直读）。
- **发现来源视角**: 10x 规模运维者。

### [AR-13] `BatchLoaderSourceFunction.seek()` 是计数器 no-op — 恢复后整库从 0 重放而计数器谎报偏移，二次恢复再翻倍

- **优先级**: `[P1]` — `ReplayableSourceFunction` 恢复契约违约：大规模重复发射 + 后续 checkpoint 谎报 offset。
- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:63-92,124-132`；消费方 `StreamSourceOperator.java:367`
- **证据片段**:
  ```java
  public void run(SourceContext<S> ctx) throws Exception {
      ...
      IBatchLoaderProvider.IBatchLoader<S> loader = loaderProvider.setup(taskContext); // 永远从 0
      List<S> batch = loader.load(batchSize, chunkContext);   // 无 skip/reposition
      ...
  @Override
  public void seek(long offset) {
      this.currentOffset = offset;                            // 只动计数器
  }
  ```
- **现状**: `StreamSourceOperator.restoreState` 对 Replayable 源调 `seek(offset)` 后重跑 `run()`——run 无条件从 provider 首记录重放；计数器却从恢复点继续。一次恢复 = 全量重复发射 + checkpoint 谎报（约 2N），下次恢复按谎报 offset 再全量重放，循环放大。负 offset 也不校验；与姊妹实现 `CollectionReplayableSource` 的 offset 约定还差一（next-index vs last-emitted-index）。
- **风险**: 恢复即数据风暴 + offset 语义失真（AT_LEAST_ONCE 声明下的极端违约：重复量 = 全库）。
- **建议**: `run()` 按 offset 跳过已发记录；或老实降级为普通 `SourceFunction`（不谎报 Replayable）；校验 `offset >= 0`。
- **信心水平**: 确定。

### [AR-14] `FileTwoPhaseCommitSink` 并行 subtask 在共享的固定名 `manifest.properties.tmp` 上竞写 — 幂等台账丢条目/伪提交失败（item 35 刚“e2e 证明”的并行形态）

- **优先级**: `[P1]` — exactly-once sink 的幂等台账完整性缺陷；时间窗竞态，e2e 通过属运气而非保证。
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:400-419`（对照 `subtaskSuffix:434-436` 只后缀化数据文件与 manifest 键）
- **证据片段**:
  ```java
  private void updateManifestAtomically(Properties manifest) throws IOException {
      Path tempManifest = outputDirPath.resolve(MANIFEST_TEMP);   // 固定名，无 subtask 后缀
      ...
      try (OutputStream out = Files.newOutputStream(tempManifest)) {  // 共享文件 CREATE+TRUNCATE
      Files.move(tempManifest, finalManifest, ATOMIC_MOVE, REPLACE_EXISTING);
  ```
- **现状**: `copyForSubtask` 后缀化了数据文件（`.sK`）和 manifest 键，唯独 manifest **临时文件**路径全 subtask 同名。sink parallelism > 1（`fraud-parallel-2pc-file.stream.xml` 即声明 parallelism=2）时两个 subtask 的 commit() 在不同 task 线程并发执行 load→写同一 tmp→move：一方的 move 可能消费另一方半写的 tmp（丢条目、`NoSuchFileException` 伪失败）。
- **风险**: 台账丢已提交 epoch 记录 → 恢复后重放“已提交”epoch（重复输出）或伪失败；`finalPath`-exists 修复路径只能部分自愈，幂等保证静默降级。
- **建议**: tmp 名加 `subtaskSuffix(subtaskIndex)`（或 outputDir 文件锁串行化 manifest 更新）；补并行 commit 交错测试。
- **信心水平**: 结构性竞态确定；实际触发概率取决于 commit 线程交错 — 很可能。

### [AR-15] `FileSourceReader` 恢复游标两处缺陷：静默接受部分 skip（从错误位置读）+ 游标推进在 monitor 外写（与 snapshotState 的 JMM 竞态）

- **优先级**: `[P1]` — 文档承诺“per-split 精确字节游标”（connectors 能力矩阵），两条静默路径破坏它。
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:163-168`（静默 partial skip）、`:117-141` vs `:248-259`（锁外写 activeSplit）
- **证据片段**:
  ```java
  long skip = split.getCurrentOffset();
  while (skip > 0) {
      long skipped = fis.skip(skip);
      if (skipped <= 0) break;      // 部分/失败 seek：无异常、无告警
      skip -= skipped;
  }
  ...
  synchronized (this) { ... line = readNextLine(); }   // 锁内读
  if (line != null) {
      if (activeSplit != null) {                        // 锁外读/写非 volatile 字段
          activeSplit = activeSplit.withCurrentOffset(newOffset);   // 无 happens-before
  ```
- **现状**: ① 恢复游标越过 EOF（文件被截断/轮转）或 skip 停滞时静默从落点继续读——对照同库正确范式 `DirectoryFileSourceFunction.emitRemaining`（skip 不等即抛 IOException）；② `activeSplit` 的游标推进写在 `snapshotState` 所用 monitor 之外，checkpoint 线程可合法读到过期游标。次级：`readNextLine` 不以 `split.getEndOffset()` 封顶，发现后增大的文件会越界发射。
- **风险**: 恢复后静默跳记录/读到断行中段；非确定性游标回退 → 重复记录——全部无信号。
- **建议**: 累计 skipped 并 `!= cursor` 即抛（镜像 DirectoryFileSourceFunction）；游标推进移入 synchronized 块；readNextLine 封顶 endOffset。
- **信心水平**: 两处静默行为确定；现实触发（截断/轮转）— 很可能。

---

## P2 发现（13 项，非阻断）

- **[AR-16] [P2]** `catch (Exception)` 漏 `Error`：OOM/LinkageError 穿透后 `sourceError==null`，finally 仍执行成功终态（MAX_WATERMARK + EOS）。`StreamTaskInvokable.java:645-656,715-721`。理由: 与 AR-7 同类放大器，但触发依赖 Error 类异常。
- **[AR-17] [P2]** `ResultPartition.injectFront` 对 EOS 哨兵释放它从未获取的 bufferPool 许可（close/read 均不为其取还）→ 每次含 EOS 分区的恢复注入凭空 +1 许可；中断分支丢弃已排水元素的许可。`ResultPartition.java:457-466,482-486`。理由: 恢复路径内存上界缓慢侵蚀。
- **[AR-18] [P2]** 广播 emit 循环只处理 InterruptedException，任一分区 RuntimeException 即刻中止 — 后续分区永不收到该记录/barrier（无 close() 那样的 attempt-all 语义）。`RecordWriter.java:154-166` 等四处。理由: 独立看可恢复，与 AR-7 组合才致命。
- **[AR-19] [P2]** DISCARDING 模式在 merging 窗口清 actualWindow 命名空间而内容存于 stateWindow — 清除为静默 no-op（重复发pane）；另 xdef 默认 `accumulationMode=DISCARDING`（stream.xdef:49）实际从未生效（builder 接受默认值但不接线，运行恒 ACCUMULATING）。`WindowOperator.java:1022-1024,735-739` + `AdvancedTransforms.java:211-218`。理由: DSL 无多 fire trigger 路径，当前影响受限；builder 公共 API 可达。
- **[AR-20] [P2]** `inferWindowSerializer` 对 processing-time 的 TimeWindow assigner 返回 `GlobalWindowSerializer`（以 isEventTime 推断窗口类型）— 类型谎言，当前无运行时消费者（上膛的枪）。`WindowOperatorFactoryImpl.java:191-197`。
- **[AR-21] [P2]** CEP `ProcessingTimeService` 空值 fail-fast 只覆盖 3 个调用点之一（:552 有守卫；:568、:310-312、:959-961 仍 NPE）。`CepOperator.java`。理由: 仅影响无任务接线的直连单测用法。
- **[AR-22] [P2]** `SharedBufferAccessor.close()` 每事件清空两个 LRU 缓存 — 跨事件命中率结构性为 0，cache-slots 配置与 30 分钟统计 timer 观测的是死缓存。`SharedBufferAccessor.java:370-372`、`CepOperator.java:826-876`。理由: 性能/可观测性误导，无正确性影响。
- **[AR-23] [P2]** 文件 2PC 与本地 checkpoint 存储在“持久”声明前无 fsync（`FileChannel.force`/目录 fsync 双缺）— 电源故障窗口内 rename 后内容可能为空/截断；`LocalFileCheckpointStorage.storeCheckPoint` 同模式（系统性）。`FileTwoPhaseCommitSink.java:408-418,465-474`。理由: 取决于接受的崩溃模型（进程崩溃安全成立）。
- **[AR-24] [P2]** `FileSplitEnumerator.discoverSplits` 的 `Files.walk` 流未关闭（API 契约要求 try-with-resources）— 每次发现/恢复泄一个目录 FD。`FileSplitEnumerator.java:82-84`。
- **[AR-25] [P2]** 生产接线的 `WebhookAlertChannel.delivered`（CopyOnWriteArrayList）无界累积每条成功投递的告警且永不修剪（`pending` 有界 256、`delivered` 无界）。`WebhookAlertChannel.java:56,110-112,156-158`。理由: 告警风暴/重启抖动场景的慢性内存增长。
- **[AR-26] [P2]** quickstart 模板把 keyed-state 富化产出的业务字段 `seq` 声明为 `transient` — 教材教了反模式；任何 Java 序列化路径（跨 JVM envelope、序列化后端状态）静默清零，topology2 断言字段恰好是最脆弱的。`quickstart/template/.../TradeEvent.java:20-21,41-47`。理由: 当前单 JVM 引用传值下潜伏。
- **[AR-27] [P2]** `generate.sh:89` 用 `sed -i ''`（BSD/macOS 专用语法）— GNU sed（Linux）下直接报错退出，脚手架生成在 Linux 不可用；verify.sh 恰好只在 macOS 跑过。理由: 新用户主流平台不可用的一次性失败。
- **[AR-28] [P2]** `verify.sh:26-32` 以“jar 存在即跳过安装”判 freshness — 本次实跑用的是 09-02 18:45 的过期构件（落后 HEAD 的 item-29 编译器变更约 2 天），脚手架“验证通过”不代表 HEAD 代码可用；验收证据会静默腐烂。理由: 脚本设计缺陷，AR-1 的掩盖因素之一。

---

## 正向确认（本轮验证通过，无需动作）

- quickstart 脚手架在清洁机器上 3/3 全绿（13:18/13:18 两次实跑），模板占位符/依赖/拓扑 XML 契约（strategyRef/windowFnId/patternRef/within/followKind/checkpoint 属性）全部被 builder 消费（F-04 之外的属性无静默丢弃）。
- fraud-example 主代码字符集/解析/fail-fast/offset 语义、JDBC 2PC sink 事务边界与资源清理、Debezium offset 持久化、metrics 模板配置键 — 子代理全读无新发现。
- CEP NFA/SharedBuffer/after-match-skip 热路径为 Flink 1.15/1.17 忠实移植（Dewey 版本算术、引用计数配对、剪枝门控逐行核验无误）——缺陷集中在 nop 特有的 checkpoint/restore 层（AR-10/11）。
- 仓库 git 卫生：`_tmp/`、`target/` 均未入库。

## 总评

本轮开放审查最有价值的发现不在任何单一算法，而在**两条系统性主线**：

1. **“成功路径思维”的系统性缺位**（AR-5/6/7/8/16/18 + AR-2/3）：正常数据面正确性被大量测试钉得很死，但取消/中断/Error/重复投递/驱逐持久化这些“非成功出口”几乎条条漏水，且相互放大（AR-5 喂 AR-6；AR-7 放大 AR-18）。multi-audit 盛赞的“并发设计有先行修复”在 MIDDLE/SINK 终态化路径上恰好缺席。
2. **恢复路径的身份与键类敏感性**（AR-1/10/11/13/15）：平台已经两次修过“JSON 往返把数字键变 Integer → 类敏感等价 miss”（AR-01/AR-22），但同一根因在 CEP 键ed 后端、CEP timer 台账、batch 源 offset、文件游标四个角落各复发一次；而 AR-1 表明最深的一层——**作业身份本身**（硬编码 "stream-job"）从未被纳入恢复正确性的视野。建议把“恢复身份 = jobId + 键类 + offset 语义”提升为一条可复用检查进入 invariant-loop 清单。

## 审计盲区自评

- 未重跑全量 `./mvnw test -pl nop-stream -am`（沿用 multi-audit 09-04 12:21 绿基线；AR-1 的 12:22 残留即来自该次运行，其写入者测试未逐一定位——机制已实跑证明，归属测试是收尾小事）。
- AR-1 的“指纹相同 → 静默跨作业恢复”分支未构造正例实证（结构性推断 + 恢复代码无其他守卫的直读；构造同形双作业 PoC 属修复验证阶段工作）。
- RocksDB 后端路径（36 文件）信任 multi-audit 与 rocksdb 专项审计覆盖，本轮仅经子代理抽样；WindowOperator/StreamTaskInvokable 之外的超大文件（StreamModelDslBuilder 613 行等）未逐行。
- 性能/背压行为（AR-22 缓存空转的实际开销、AR-17 许可侵蚀的到达速率）未负载实测。
- P2 项未逐条独立复核（子代理直读 + 主审抽查 AR-23/24/25/26/27/28 六项）。

## 按严重程度分布表

| 优先级 | 数量 | 主要类别 |
|--------|------|----------|
| P0 | 1 | 默认配置跨作业 checkpoint 状态污染（含 quickstart 验收物实跑击穿） |
| P1 | 14 | 非成功出口终态化(4)、恢复键类/offset 身份(4)、窗口 evictor/pane 状态(2)、barrier 完整性(2)、topic 合法性(1)、2PC 台账竞态(1) |
| P2 | 13 | Error 穿透/许可侵蚀/广播部分写等健壮性(3)、latent 类型谎言与模式 no-op(3)、fsync/FD/内存卫生(3)、quickstart 模板与脚本(3)、CEP 死缓存(1) |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
