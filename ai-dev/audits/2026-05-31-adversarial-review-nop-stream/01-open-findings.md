# nop-stream 对抗性审查 — Round 17

> 审查日期：2026-05-31
> 审查范围：nop-stream 全模块（6 个活跃子模块，423 个 Java 源文件），开放式发现导向
> 审查方法：聚焦前 16 轮审查的盲区自评中明确列出的未覆盖区域：SourceEnumerator、nop-stream-flow/checkpoint/flink（验证为空占位模块）、MemoryStateSerDe 序列化往返、fraud-example 完整运行路径、WindowOperator.namespace 序列化一致性。逐文件精读代码，追踪跨文件数据流。
> 发现来源视角：新人开发者 + 异常路径侦探 + 死代码清道夫
> 去重：已阅读以下已有报告，本报告不重复其中已精确描述且未变化的内容：
> - `2026-05-20-adversarial-review-nop-stream/` ~ `2026-05-31-adversarial-review-nop-stream-r16/`（Round 1~16）
> - `2026-05-25-deep-audit-nop-stream-full/` ~ `2026-05-31-deep-audit-nop-stream-full/`

---

## 模块结构确认

以下子模块为**空占位模块**（无 `src/main/java/` 目录），无需审查：
- `nop-stream-api` — 纯 POM 依赖聚合器
- `nop-stream-checkpoint` — 占位（pom.xml 注释 "placeholder, planned but not implemented"）
- `nop-stream-flow` — 占位（pom.xml 注释 "placeholder, planned but not implemented"）
- `nop-stream-flink` — 占位

活跃子模块：nop-stream-core（289 文件）、nop-stream-cep（77 文件）、nop-stream-runtime（40 文件）、nop-stream-connector（7 文件）、nop-stream-fraud-example（10 文件）。

---

## 新发现

### [AR-1] SourceEnumerator.assignSplits() 在 ConcurrentLinkedQueue 迭代器上调用 remove() — 运行时必定崩溃

- **文件**: `nop-stream-runtime/.../source/SourceEnumerator.java:131-144`
- **证据片段**:
  ```java
  // line 41: unassignedSplits 是 ConcurrentLinkedQueue
  private final Queue<String> unassignedSplits;  // = new ConcurrentLinkedQueue<>()

  // line 131-144: assignSplits() 方法
  Iterator<String> it = unassignedSplits.iterator();
  while (it.hasNext()) {
      String splitId = it.next();
      int target = nextSubtaskIndex % totalParallelism;
      if (target == subtaskIndex) {
          it.remove();  // ← UnsupportedOperationException!
          assignedSplits.put(splitId, subtaskIndex);
          pendingAcknowledgements.add(splitId);
          assigned.add(splitId);
          nextSubtaskIndex++;
      } else {
          continue;
      }
  }
  ```
- **严重程度**: P1
- **现状**: `ConcurrentLinkedQueue.iterator()` 的 Javadoc 明确说："The iterator does not support the remove() method."。`it.remove()` 会抛出 `UnsupportedOperationException`。`assignSplits(int subtaskIndex)` 方法被调用时**必定崩溃**。
- **风险**: 任何使用 `assignSplits(subtaskIndex)` 进行定向分配的调用路径都会失败。`assignAllSplits()` 方法使用 `poll()` 而非迭代器，不受影响。如果框架依赖 `assignSplits` 进行 per-subtask 恢复式分配（如 failover 后重新分配特定 split），将导致任务无法恢复。
- **建议**: 替换为循环内 `unassignedSplits.poll()` 并在非目标元素时重新入队；或将 `unassignedSplits` 改为 `LinkedBlockingQueue`/`ArrayDeque`（但需注意并发安全）；或直接用 `poll()` + 匹配逻辑重写。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（ConcurrentLinkedQueue 文档契约违反）

---

### [AR-2] WindowOperator.windowNamespace() 使用 System.identityHashCode() — 自定义 Window 类型在 checkpoint/restore 后状态丢失

- **文件**: `nop-stream-runtime/.../operators/windowing/WindowOperator.java:839-848`
- **证据片段**:
  ```java
  private String windowNamespace(W window) {
      if (window == null) {
          return "_null_window_";
      }
      if (window instanceof TimeWindow) {
          TimeWindow tw = (TimeWindow) window;
          return "TW:" + tw.getStart() + "," + tw.getEnd();
      }
      return window.getClass().getName() + "@" + System.identityHashCode(window);
      //                                        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      //                                        每次反序列化后不同
  }
  ```
- **严重程度**: P2
- **现状**: `windowNamespace()` 为 TimeWindow 使用确定性字符串（`"TW:start,end"`），为 GlobalWindow 单例使用固定 identityHashCode（JVM 内稳定）。但对**任何非 TimeWindow 的自定义 Window 子类**，namespace 基于 `System.identityHashCode()`，这是一个 JVM 实例相关的值。checkpoint 写入时 window namespace 为 `MyWindow@12345`，restore 后新 MyWindow 对象的 identityHashCode 为 `67890`，namespace 不匹配 → keyed state backend 查询不到旧状态 → **窗口内容丢失**。
- **风险**: 当前没有非 TimeWindow/GlobalWindow 的 Window 子类在使用，因此**当前无实际影响**。但这是一个**地雷**：如果未来引入自定义 Window 类型（如 Spatio-temporal Window、Session Window with custom merge key），状态恢复会静默失败。`WindowOperator` 是 `nop-stream-runtime` 模块中最复杂的算子之一（1099 行），此类 latent bug 极难定位。
- **建议**: 对所有 Window 类型使用基于字段值的确定性 namespace（类似 TimeWindow 的 `start + "," + end`），或要求自定义 Window 实现 `toString()` 返回确定性标识。
- **信心水平**: 确定（代码路径验证清晰，但当前无实际使用）
- **发现来源视角**: 异常路径侦探（序列化往返一致性）

---

### [AR-3] SourceEnumerator 与 Thread Safety 矛盾 — Javadoc 声称单线程但使用并发集合

- **文件**: `nop-stream-runtime/.../source/SourceEnumerator.java:31-76`
- **证据片段**:
  ```java
  // line 31-32: Javadoc
  // "Thread Safety: This class is designed to be used from a single
  //  coordinator thread. Concurrent access from multiple threads requires
  //  external synchronization."

  // line 69-74: 实际使用并发集合
  this.discoveredSplits = ConcurrentHashMap.newKeySet();
  this.unassignedSplits = new ConcurrentLinkedQueue<>();
  this.assignedSplits = new ConcurrentHashMap<>();
  this.finishedSplits = ConcurrentHashMap.newKeySet();
  this.pendingAcknowledgements = ConcurrentHashMap.newKeySet();
  this.splitMetadata = new ConcurrentHashMap<>();
  ```
- **严重程度**: P3
- **现状**: Javadoc 声称"单线程设计"，但所有字段都使用 `ConcurrentHashMap`/`ConcurrentLinkedQueue`。这种矛盾有两种解释：(1) 文档过时，实际已支持多线程；(2) 过度防御性编程。更严重的是，`nextSubtaskIndex`（int）**没有**使用线程安全类型，是唯一非并发字段。如果实际存在并发访问（文档声称不会），`nextSubtaskIndex` 会有可见性问题。
- **风险**: 误导未来开发者——看到 Javadoc 会认为不需要外部同步，但 `nextSubtaskIndex` 的非原子性意味着实际上不安全。如果文档正确（单线程），则并发集合的开销是浪费。
- **建议**: 统一：(1) 如果确实单线程，改用普通集合 + volatile nextSubtaskIndex；(2) 如果需要并发安全，将 `nextSubtaskIndex` 改为 `AtomicInteger` 并更新 Javadoc。
- **信心水平**: 确定
- **发现来源视角**: 新人开发者（文档与实现矛盾）

---

### [AR-4] BatchLoaderSourceFunction 每次 run() 都创建新的 BatchTaskContextImpl 但不关闭它

- **文件**: `nop-stream-connector/.../connector/BatchLoaderSourceFunction.java:55-78`
- **证据片段**:
  ```java
  public void run(SourceContext<S> ctx) throws Exception {
      IBatchTaskContext taskContext = new BatchTaskContextImpl();
      IBatchLoaderProvider.IBatchLoader<S> loader = loaderProvider.setup(taskContext);
      try {
          IBatchChunkContext chunkContext = taskContext.newChunkContext();
          while (running) {
              List<S> batch = loader.load(batchSize, chunkContext);
              if (batch == null || batch.isEmpty()) break;
              for (S item : batch) {
                  if (!running) return;
                  ctx.collect(item);
              }
          }
      } finally {
          if (loader instanceof AutoCloseable) {
              ((AutoCloseable) loader).close();
          }
      }
  }
  ```
- **严重程度**: P3
- **现状**: `BatchTaskContextImpl` 在每次 `run()` 调用时创建，但从未关闭。如果 `BatchTaskContextImpl` 持有资源（如数据库连接、文件句柄），它们会泄漏。同时，`loader.close()` 在 finally 块中处理，但 `taskContext` 没有对应的清理。
- **风险**: 资源泄漏。在流式作业中 `run()` 通常只调用一次，但如果作业重启或 failover 后重新调用 `run()`，旧的 taskContext 资源可能不被释放。
- **建议**: 检查 `BatchTaskContextImpl` 是否实现 `AutoCloseable`，如果是，在 finally 块中关闭。
- **信心水平**: 很可能（取决于 BatchTaskContextImpl 是否持有资源）
- **发现来源视角**: 异常路径侦探（资源生命周期）

---

## 总评

### 最值得关注的 1 个方向

**SourceEnumerator.assignSplits() 是本轮唯一的高优先级发现。** 它不是一个竞态条件或边界情况——而是一个**确定性的运行时崩溃**。`ConcurrentLinkedQueue.iterator().remove()` 在 Java 标准库文档中明确标注为不支持。这意味着 `assignSplits(int subtaskIndex)` 方法从未被成功调用过（或调用时必定抛异常），否则这个 bug 早就被发现了。这引出一个更深层的问题：**这个方法是否有测试？是否有调用者？** 如果没有，它是死代码；如果有，测试覆盖了什么？

### 本次审查的盲区自评

1. **没有验证 BarrierAligner（runtime 模块独立实现）与 InputGate barrier 对齐的行为一致性**。BarrierAligner 标注为"当前未使用"，但未来启用时需要独立审查。
2. **没有审查 CheckpointBarrierTracker 的完整并发场景**（triggerCheckpoint 与 acknowledgeOperator 的 synchronized 语义是否足够）。
3. **没有审查 WindowOperator 的完整 1099 行**（仅阅读了关键方法 windowNamespace、mergeWindowContents、addWindowElement），中间处理逻辑可能有遗漏。
4. **没有验证 fraud-example 中 NFA.advanceTime + NFA.process 的组合语义**（事件时间处理是否与 CepOperator 一致）。
5. **没有对 nop-stream-cep 的 NFACompiler 编译结果做状态机完备性检查**（如是否有不可达状态、是否有遗漏的 StateTransition）。

---

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 1    | ConcurrentLinkedQueue iterator.remove() 崩溃 |
| P2      | 1    | WindowOperator namespace 非确定性 |
| P3      | 2    | 文档矛盾(1) + 资源泄漏(1) |
