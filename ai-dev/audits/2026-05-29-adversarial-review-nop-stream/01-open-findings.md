# nop-stream 对抗性审查 — Round 7

> 审查日期: 2026-05-29
> 审查范围: nop-stream 全模块（10 个子模块），独立代码审查
> 审查方法: 开放式发现导向，4 个并行 agent 深入代码 + 手动交叉验证
> 去重: 已阅读以下已有报告，本报告不重复其中内容:
> - `2026-05-20-adversarial-review-nop-stream/`（Round 1+2, N1-N41）
> - `2026-05-22-adversarial-review-nop-stream/`（Round 1, N42-N72）
> - `2026-05-22-adversarial-review-nop-stream-r2/`（Round 3, N73-N93）
> - `2026-05-24-adversarial-review-nop-stream-r3/`（Round 4, N94-N105）
> - `2026-05-28-adversarial-review-nop-stream/report.md`（12 发现, Checkpoint/Window/CEP）
> - `2026-05-28-adversarial-review-nop-stream-r2/report.md`（Round 5, N106-N120）
> - `2026-05-28-adversarial-review-nop-stream-r3/report.md`（Round 6, N121-N132）
> - `2026-05-25-deep-audit-nop-stream-full/`（21 维度系统审计）
> - `2026-05-28-deep-audit-nop-stream-full/`（21 维度全量审计）
> 发现来源视角: 异常路径侦探 + 10x 规模运维者 + 死代码清道夫

---

## 发现分类

| 类别 | 数量 | 最严重 |
|------|------|--------|
| CEP NFA 正确性 | 4 | P0 |
| 执行引擎正确性 | 4 | P1 |
| Checkpoint/恢复 | 2 | P1 |
| Source 管理 | 1 | P1 |
| Watermark 语义 | 1 | P1 |
| 状态后端 | 2 | P2 |
| 代码质量 | 2 | P3 |

---

## P0: 运行时崩溃

### [AR-1] NFA.processMatchesAccordingToSkipStrategy 对空 extractPatterns 结果无保护 — IndexOutOfBoundsException

- **文件**: `nop-stream-cep/.../nfa/NFA.java:454`
- **证据片段**:
  ```java
  List<Map<String, List<EventId>>> matchedResult =
      sharedBufferAccessor.extractPatterns(
          earliestMatch.getPreviousBufferEntry(), earliestMatch.getVersion());

  afterMatchSkipStrategy.prune(partialMatches, matchedResult, sharedBufferAccessor);
  afterMatchSkipStrategy.prune(
      nfaState.getCompletedMatches(), matchedResult, sharedBufferAccessor);

  result.add(sharedBufferAccessor.materializeMatch(matchedResult.get(0)));  // ← BOOM
  ```
- **严重程度**: P0
- **现状**: `extractPatterns()` 在起始 entry 为 null 时返回空 `ArrayList`。后续 `matchedResult.get(0)` 抛 `IndexOutOfBoundsException`。
- **风险**: 与 N114（DFS 遍历中间节点 NPE）是不同代码路径。N114 是 DFS 内部 null 问题；此处是 DFS 完成后、结果消费端未做空列表检查。当一个 completed match 的 buffer entry 在处理前被超时或 stop-state 清理释放时触发。
- **建议**: 在 `matchedResult.get(0)` 前检查 `matchedResult.isEmpty()`，若空则跳过此 match 并 release 对应节点。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-2] NFA.createDecisionGraph / findFinalStateAfterProceed 无环检测 — 运行时无限循环

- **文件**: `nop-stream-cep/.../nfa/NFA.java:763-785` 和 `NFA.java:793-829`
- **证据片段**:
  ```java
  // findFinalStateAfterProceed (line 763-785):
  private State<T> findFinalStateAfterProceed(...) {
      final Stack<State<T>> statesToCheck = new Stack<>();
      statesToCheck.push(state);
      while (!statesToCheck.isEmpty()) {
          final State<T> currentState = statesToCheck.pop();
          for (StateTransition<T> transition : currentState.getStateTransitions()) {
              if (transition.getAction() == StateTransitionAction.PROCEED
                      && checkFilterCondition(...)) {
                  if (transition.getTargetState().isFinal()) {
                      return transition.getTargetState();
                  } else {
                      statesToCheck.push(transition.getTargetState());  // ← 无 visited set
                  }
              }
          }
      }
      return null;
  }

  // createDecisionGraph (line 793-829) 同样问题:
  case PROCEED:
      states.push(stateTransition.getTargetState());  // ← 无 visited set
      break;
  ```
- **严重程度**: P0
- **现状**: 两个方法遍历 PROCEED 边时均不维护 visited set。如果 NFA 状态图中存在 PROCEED 环（NFACompiler 不应产生但无运行时保护），方法将无限循环。`createDecisionGraph` 对每个输入事件调用，畸形 NFA 会在第一个事件处挂起。
- **风险**: 这与 N120（`copyWithoutTransitiveNots` 编译期递归无环检测）是不同的代码路径。N120 在 NFA 构建期；此处是 NFA 运行时执行引擎。两处需独立修复。`NFACompiler.canProduceEmptyMatches()` 在 line 112 使用了 `visitedStates` set，说明平台意识到了这个问题，但这两个运行时方法遗漏了。
- **建议**: 添加 `Set<State<T>> visited = new HashSet<>()` 并在 push 前检查。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-3] NFA.doProcess O(n^2) 死代码 — 每个事件浪费 CPU 且无效

- **文件**: `nop-stream-cep/.../nfa/NFA.java:459-460`
- **证据片段**:
  ```java
  nfaState.getPartialMatches()
      .removeIf(pm -> pm.getStartEventID() != null && !partialMatches.contains(pm));
  ```
- **严重程度**: P0（性能） / P3（正确性）
- **现状**: 两个独立问题叠加：
  1. **O(n^2) 复杂度**: `partialMatches` 是 `PriorityQueue`，`.contains()` 是 O(n)。在 `removeIf`（O(n) 迭代）内调用 → O(n^2)。
  2. **死代码**: 此 `removeIf` 操作 `nfaState.getPartialMatches()`（旧队列），下一行（416）即被 `nfaState.setNewPartialMatches(newPartialMatches)` 替换。移除无持久效果，也不调用 `releaseNode` 释放资源。
- **风险**: 大量活跃 partial match 的模式（如 `followedByAny` + `oneOrMore`），每次事件处理都有 O(n^2) 无用计算。
- **建议**: 删除此行，或确认意图后改为在新队列上操作。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-4] NFAState.equals 使用 PriorityQueue.toArray() — 逻辑等价对象判断不等

- **文件**: `nop-stream-cep/.../nfa/NFAState.java:124-125`
- **证据片段**:
  ```java
  public boolean equals(Object o) {
      // ...
      return Arrays.equals(partialMatches.toArray(), nfaState.partialMatches.toArray())
              && Arrays.equals(completedMatches.toArray(), nfaState.completedMatches.toArray());
  }
  ```
- **严重程度**: P0（影响测试和状态比较）
- **现状**: `PriorityQueue.toArray()` 返回内部堆序（非排序序）。两个包含相同元素的 PriorityQueue，如果元素插入顺序不同，`toArray()` 结果不同，`equals()` 返回 `false`。
- **风险**: 所有断言 NFA 状态的测试都是 flaky 的。checkpoint 恢复时如果依赖状态比较，也会产生误判。
- **建议**: 先排序再比较，或使用 `new ArrayList<>(queue)` 排序后比较。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## P1: 正确性问题

### [AR-5] CepPatternBuilder.buildCondition 返回非序列化匿名类 — 分布式部署序列化失败

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java:134-141`
- **证据片段**:
  ```java
  private IterativeCondition buildCondition(IEvalFunction action) {
      return new IterativeCondition() {
          @Override
          public boolean filter(Object value, Context ctx) {
              return ConvertHelper.toTruthy(action.call2(null, value, ctx, null));
          }
      };
  }
  ```
- **严重程度**: P1
- **现状**: 匿名内部类隐式捕获 `CepPatternBuilder.this`（非 `Serializable`）。当 Pattern 流入 `NFACompiler.compileFactory()` → `NFAFactoryImpl`（`implements Serializable`），序列化时抛 `NotSerializableException`。
- **风险**: 通过 `CepPatternBuilder` 构建的任何 CEP 模式在分布式部署或 checkpoint 序列化时失败。这是 CEP 模型驱动开发路径的核心问题。
- **建议**: 提取为静态内部类或 standalone 类，显式管理 `IEvalFunction` 的序列化。
- **信心水平**: 很可能
- **发现来源视角**: 代码生成受害者

---

### [AR-6] OperatorChain.close() 正向迭代 — 与 Javadoc 声明的"反向关闭"矛盾

- **文件**: `nop-stream-core/.../jobgraph/OperatorChain.java:175-193`
- **证据片段**:
  ```java
  /**
   * ... The operators are closed in reverse order. ...
   */
  public void close() {
      Exception firstException = null;
      for (io.nop.stream.core.operators.StreamOperator<?> operator : operators) {
          // ↑ 正向迭代 (0 → n-1), 不是反向！
          try {
              operator.close();
          } catch (Exception e) { ... }
      }
  }
  ```
- **严重程度**: P1
- **现状**: Javadoc 明确声明"反向关闭"，但代码正向遍历 `operators` 列表。`SubtaskTask.closeOperatorChains()` 正确反向关闭（`for (int i = operatorChains.size() - 1; i >= 0; i--)`），但 `OperatorChain.close()` 未遵循。
- **风险**: 先关闭上游算子再关闭下游算子。如果下游 `close()` 依赖上游资源（如 flush buffer），可能 NPE 或数据丢失。
- **建议**: 改为 `for (int i = operators.size() - 1; i >= 0; i--)` 或使用 `Collections.reverse`。
- **信心水平**: 确定

---

### [AR-7] StreamTaskInvokable invokeSource/invokeMiddle 无 try/finally 保护 outputWriter.close() — 异常时下游永久阻塞

- **文件**: `nop-stream-core/.../execution/StreamTaskInvokable.java:259-286`
- **证据片段**:
  ```java
  private void invokeSource() throws Exception {
      // ...
      sourceOp.run();               // ← 可抛异常
      sourceOp.processWatermark(Watermark.MAX_WATERMARK);
      // ...
      if (outputWriter != null) {
          outputWriter.close();      // ← 异常时跳过
      }
  }

  private void invokeMiddle() throws Exception {
      if (headInput != null) {
          processInputGate(headInput);   // ← 可抛异常
          headInput.processWatermark(Watermark.MAX_WATERMARK);
      }
      if (outputWriter != null) {
          outputWriter.close();          // ← 异常时跳过
      }
  }
  ```
- **严重程度**: P1
- **现状**: `outputWriter.close()` 不在 try/finally 中。如果 `run()` 或 `processInputGate()` 抛异常，`ResultPartition` 永不关闭，不写 `END_OF_STREAM`。下游 `InputGate` 在 `queue.take()` 永久阻塞。
- **风险**: 任何 source/middle 算子的运行时异常导致所有下游 task 级联挂起。Job 永远无法完成。
- **建议**: 用 try/finally 包裹，确保 `outputWriter.close()` 始终执行。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-8] StreamGraphGenerator.propagateKeySelectors 只设置直接下游 — 链式算子丢失 key context

- **文件**: `nop-stream-core/.../graph/StreamGraphGenerator.java:344-361`
- **证据片段**:
  ```java
  private void propagateKeySelectors() {
      for (Map.Entry<Integer, List<StreamEdge>> entry : streamGraph.getAllStreamEdges().entrySet()) {
          // ...
          KeySelector<?, ?> keySelector = partitionKeySelectors.get(sourceId);
          for (StreamEdge edge : entry.getValue()) {
              StreamNode target = streamGraph.getStreamNode(edge.getTargetId());
              if (target != null && target.getKeySelector() == null) {
                  target.setKeySelector(keySelector);  // 只设置直接下游节点
              }
          }
      }
  }
  ```
- **严重程度**: P1
- **现状**: `keyBy()` 后只有紧邻的下游算子获得 `KeySelector`。如果链中有 [partition → map → window]，window 算子没有 `KeySelector`。`StreamTaskInvokable.wireOperators()` 只在有 `keySelector` 时包装 `KeyExtractingOutput`，否则 key context 不设置。
- **风险**: `keyBy().map().window()` 等多步链式管道中，window 算子访问 keyed state 时使用错误的 key（null 或 stale key），导致不同 key 的数据混入同一状态。
- **建议**: `propagateKeySelectors` 应沿算子链向下传播 `KeySelector`，直到遇到下一个 `keyBy()` 为止。
- **信心水平**: 很可能
- **发现来源视角**: 新人开发者

---

### [AR-9] TimestampsAndWatermarksOperator idle 状态不可逆 — markActive() 存在但从未被调用

- **文件**: `nop-stream-core/.../operators/TimestampsAndWatermarksOperator.java:110-131`
- **证据片段**:
  ```java
  private class OperatorWatermarkOutput implements WatermarkOutput {
      @Override
      public void emitWatermark(Watermark watermark) {
          if (idle) return;        // idle 后所有 watermark 被永久抑制
          // ...
      }

      @Override
      public void markIdle() {
          idle = true;
      }

      @Override
      public void markActive() {
          idle = false;            // 存在但从未被调用
      }
  }
  ```
- **严重程度**: P1
- **现状**: `markIdle()` 设置 `idle = true` 后，`markActive()` 虽然存在但无任何代码路径调用它。所有后续 watermark 被永久抑制。`onPeriodicEmit` 也通过 `OperatorWatermarkOutput` 间接检查 `idle`。
- **风险**: 如果 source 调用 `ctx.markAsTemporarilyIdle()`（如临时数据间隔），watermark 生成永久停止。所有下游窗口算子永不触发。
- **建议**: 在 `processElement` / `processWatermark` 路径中调用 `markActive()` 恢复活跃状态。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-10] SourceEnumerator.assignSplits 在第一个不匹配的 split 处 break — 后续 split 永远饥饿

- **文件**: `nop-stream-runtime/.../source/SourceEnumerator.java:121-146`
- **证据片段**:
  ```java
  public List<String> assignSplits(int subtaskIndex) {
      List<String> assigned = new ArrayList<>();
      Iterator<String> it = unassignedSplits.iterator();
      while (it.hasNext()) {
          String splitId = it.next();
          int target = nextSubtaskIndex % totalParallelism;
          if (target == subtaskIndex) {
              it.remove();
              assignedSplits.put(splitId, subtaskIndex);
              assigned.add(splitId);
              nextSubtaskIndex++;
          } else {
              break;  // ← 在第一个不匹配的 split 处停止
          }
      }
      return assigned;
  }
  ```
- **严重程度**: P1
- **现状**: 假设 3 个 subtask，队列 `[s0, s1, s2, s3, s4, s5]`，`nextSubtaskIndex=0`。Subtask 2 调用 `assignSplits(2)`：s0 → target=0（不是 2）→ break。s2 本应分配给 subtask 2，但永远无法到达。注意 `assignAllSplits()` 方法正确实现了轮询分配。
- **风险**: 并行度 > 2 时，大部分 subtask 永远分配不到 split，除非恰好轮到它时队列头部就是它的。
- **建议**: 去掉 `break`，改为 `continue` 或用 `assignAllSplits` 替代。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-11] JobCoordinator.globalRecovery 不向 TaskManager 传播新 fencing token — 恢复后所有新任务被拒绝

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:380-414`
- **证据片段**:
  ```java
  public void globalRecovery() {
      String newToken = UUID.randomUUID().toString();
      String oldToken = fencingToken.getAndSet(newToken);
      clusterRegistry.registerCoordinator(jobId, coordinatorId, newToken);
      // ... 清理旧分配 ...
      assignTasks();  // 使用新 token 发送分配
  }
  ```
- **严重程度**: P1
- **现状**: `globalRecovery` 生成新 fencing token，但从不调用任何 `taskManager.updateFencingToken(newToken)`。TaskManager 仍持有旧 token。新 assignment 携带新 token，TaskManager 检查 `currentFencingToken` 不匹配后拒绝所有新分配（N103 已报告了类似问题在 EmbeddedDistributedExecutor 中，但此处是 JobCoordinator 自身的逻辑缺陷，独立于嵌入执行器）。
- **风险**: 全局恢复后，无法重新分配任何任务。Job 永久卡住。
- **建议**: 在 `assignTasks()` 前调用所有 TaskManager 的 `updateFencingToken(newToken)`，或在 assignment 中携带 token 更新指令。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探

---

### [AR-12] EmbeddedDistributedExecutor 不调度 checkpoint — 分布式执行无容错能力

- **文件**: `nop-stream-runtime/.../execution/EmbeddedDistributedExecutor.java:87-92`
- **严重程度**: P1
- **现状**: `CheckpointConfig` 使用默认值（`checkpointEnabled` 默认 false），`CheckpointCoordinator.startCheckpointScheduler()` 从不被调用，`restoreFromCheckpoint()` 从不被调用。虽然创建了 `CheckpointCoordinator` 实例，但完全未使用。
- **风险**: 任何 task 失败，所有进度丢失。分布式执行无任何容错保障。
- **建议**: 配置 `CheckpointConfig`，调用 `startCheckpointScheduler()`，在 task 分配前尝试 `restoreFromCheckpoint()`。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

## P2: 维护成本 / 局部缺陷

### [AR-13] DeweyNumber.increase int 溢出 — 长时间运行流作业的版本号错乱

- **文件**: `nop-stream-cep/.../nfa/DeweyNumber.java:112`
- **证据片段**:
  ```java
  public DeweyNumber increase(int times) {
      int[] newDeweyNumber = Arrays.copyOf(deweyNumber, deweyNumber.length);
      newDeweyNumber[deweyNumber.length - 1] += times;  // ← int 溢出
      return new DeweyNumber(newDeweyNumber);
  }
  ```
- **严重程度**: P2
- **现状**: `int` 溢出后变为负数。`isCompatibleWith()` 比较时 `deweyNumber[lastIndex] >= other.deweyNumber[lastIndex]` 产生错误结果。
- **风险**: 多日运行的高事件率 `followedByAny` 模式，branching 操作达到 ~2.1 billion 时触发。导致 stale edge 不释放（内存泄漏）或 active edge 被过早释放（数据丢失）。
- **建议**: 使用 `long[]` 或溢出检测。
- **信心水平**: 很可能

---

### [AR-14] Lockable.lock refCounter int 溢出 — 节点被过早删除

- **文件**: `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java:39`
- **证据片段**:
  ```java
  public void lock() {
      refCounter += 1;  // ← 无溢出保护
  }

  boolean release() {
      if (refCounter <= 0) {   // 溢出后立即为 true
          return true;          // 节点被删除
      }
      // ...
  }
  ```
- **严重程度**: P2
- **现状**: `Integer.MAX_VALUE` 次 lock 后溢出为 `Integer.MIN_VALUE`（负数）。下一次 `release()` 看到 `refCounter <= 0`，返回 `true`，触发节点删除。但此节点仍被活跃 computation state 引用。
- **风险**: 循环模式（`oneOrMore().allowCombinations()`）处理百万级事件时可能触发。NPE 或匹配结果损坏。
- **建议**: 使用 `AtomicLong` 或在 lock 时检查溢出。
- **信心水平**: 很可能

---

### [AR-15] CepPatternBuilder.addQualifier 无量词互斥验证 — 模型错误产生误导性异常

- **文件**: `nop-stream-cep/.../model/builder/CepPatternBuilder.java:143-181`
- **证据片段**:
  ```java
  private Pattern addQualifier(Pattern pattern, CepPatternPartModel partModel) {
      if (partModel.isOneOrMore()) {
          pattern = pattern.oneOrMore(partModel.getWindowTime());   // 设置 LOOPING
      }
      if (partModel.getTimes() != null) {
          pattern = pattern.times(begin, last, windowTime);         // 设置 TIMES → 冲突
      }
      if (partModel.getTimesOrMore() != null) {
          pattern = pattern.timesOrMore(n, windowTime);             // 设置 LOOPING → 冲突
      }
  }
  ```
- **严重程度**: P2
- **现状**: 模型层（`CepPatternPartModel`）允许 `oneOrMore=true` 和 `times != null` 同时存在。`Pattern.times()` 内部调用 `checkIfQuantifierApplied()` 抛出 `MalformedPatternException`，但错误信息是通用的"Already applied quantifier"，不指明是哪两个量词冲突。
- **风险**: 模型配置错误时调试困难。应该在模型层验证互斥，给出清晰的字段名。
- **建议**: 在 `addQualifier` 入口添加互斥检查，抛出明确的模型验证错误。
- **信心水平**: 很可能

---

### [AR-16] MemoryKeyedStateBackend.snapshotState 无防御性拷贝 — 并发修改损坏快照

- **文件**: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java:304-306`
- **证据片段**:
  ```java
  for (Map.Entry<TypedNamespaceAndKey, ?> e : state.storage.entrySet()) {
      // ...
      entry.put("value", e.getValue());  // ← 捕获的是 live 引用
  }
  ```
- **严重程度**: P2
- **现状**: 快照直接捕获 state map 中 value 对象的引用。如果算子在快照后修改了可变值对象（如 List 或 Map），快照数据被静默损坏。
- **风险**: 使用 `MemoryListState` 或 `MemoryMapState` 时，算子修改 state 后快照不一致。恢复时状态损坏。
- **建议**: 对可变类型做深拷贝。
- **信心水平**: 很可能

---

### [AR-17] JdbcCheckpointStorage.storeCheckPoint 过宽异常捕获 — 隐藏真实错误原因

- **文件**: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java:74-94`
- **证据片段**:
  ```java
  try {
      jdbcTemplate.executeUpdate(sql); // INSERT
  } catch (Exception e) {  // ← 捕获所有异常
      LOG.debug("INSERT failed, attempting UPDATE...");  // DEBUG 级别
      jdbcTemplate.executeUpdate(updateSql); // UPDATE
  }
  ```
- **严重程度**: P2
- **现状**: `catch (Exception e)` 捕获连接错误、权限拒绝、磁盘满等所有异常，而非仅捕获主键冲突。原始错误以 DEBUG 级别记录，实际被隐藏。
- **风险**: 存储故障诊断极其困难。非主键冲突的 INSERT 失败会被误导为"正常 upsert 流程"。
- **建议**: 只捕获特定的 duplicate-key 异常，其余传播。
- **信心水平**: 确定

---

## P3: 低优先级

### [AR-18] NFA.doProcess shouldDiscardPath 过度激进剪枝 — 丢弃合法的 sibling 状态

- **文件**: `nop-stream-cep/.../nfa/NFA.java:370-400`
- **严重程度**: P3
- **现状**: 如果一个 computation state 产生多个新状态（TAKE + PROCEED），且任何一个到达 stop state，所有 sibling 状态（包括合法的匹配延续路径）都被释放和丢弃。设计意图是"如果事件匹配 NOT 条件则丢弃该事件"，但在 NOT + optional 分支的复杂模式中会消除有效的替代匹配。
- **风险**: 复杂 NOT 模式可能丢失合法匹配结果。
- **信心水平**: 有趣的猜测

---

## 总评

### 最值得关注的 3 个方向

1. **NFA 运行时无环检测（AR-2）是与 N120（编译期无环检测）平行的独立风险**。N120 在 NFA 构建路径中，AR-2 在 NFA 执行路径中。即使编译期的环检测被修复，运行时仍需独立保护。之前 6 轮审查都未发现运行时路径的这一缺陷——`canProduceEmptyMatches` 使用了 visited set，但 `findFinalStateAfterProceed` 和 `createDecisionGraph` 没有。这说明 NFA 的运行时遍历逻辑是审查盲区。

2. **执行引擎异常路径的级联故障（AR-6, AR-7）是 P1 级别的可靠性问题**。`OperatorChain.close()` 的正向迭代与 Javadoc 矛盾，`StreamTaskInvokable` 缺少 try/finally 保护 `outputWriter.close()`。两者叠加：算子异常 → outputWriter 不关闭 → 下游永久阻塞。这两个问题在之前审查中未被报告，可能因为它们在"正常路径"审查中不明显，只有在异常路径分析中才会暴露。

3. **Key context 传播缺陷（AR-8）影响所有多步链式 keyed 管道**。`propagateKeySelectors` 只设置直接下游，使得 `keyBy().map().window()` 这种最常见的流处理模式中 window 算子的 keyed state 完全错乱。之前审查关注了 `KeyExtractingOutput` 的缺失（N75，已修复），但未追踪到 `StreamGraphGenerator` 层面的 key selector 传播不完整问题。

### 本次审查的盲区自评

1. **没有验证 AR-8 在实际管道中的触发条件**: `propagateKeySelectors` 是否只设置直接下游取决于 `StreamGraph` 中节点如何构建。如果 `map` 和 `window` 被合并到同一个 chain node，key selector 可能在 chain 级别已正确设置。
2. **没有审查 `nop-stream-flow` 和 `nop-stream-flink` 模块的 Java 源代码**: 之前的审计也报告这些是空壳模块，本次确认仍然如此。
3. **没有运行测试验证任何发现**: 特别是 AR-2（无限循环）和 AR-1（IndexOutOfBounds）应该可以通过构造畸形 NFA 或特定 buffer 释放时序的单元测试快速确认。
4. **没有深入审查 CEP Pattern 的条件评估（`checkFilterCondition`）的异常处理对 NFA 状态的影响**: 如果 condition filter 抛异常，`createDecisionGraph` 的 try/catch 会包装为 `StreamException`，但此时 `outgoingEdges` 可能只有部分填充。
5. **TimestampsAndWatermarksOperator（AR-9）的 `markActive()` 是否真的无调用路径未做全局搜索验证**: 仅从该类内部代码确认。

### 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 4    | NFA 运行时崩溃/性能/状态比较 |
| P1      | 8    | 执行引擎正确性、checkpoint 恢复、watermark 语义 |
| P2      | 5    | 溢出、模型验证、状态后端、存储错误处理 |
| P3      | 1    | NFA 剪枝策略 |
