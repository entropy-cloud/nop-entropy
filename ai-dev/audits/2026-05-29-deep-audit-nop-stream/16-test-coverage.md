# 维度16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] CepOperator processing-time 代码路径完全无测试

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:294-321, 378-405`
- **证据片段**:
  ```java
  // CepOperator.java:294-321
  @Override
  public void processElement(StreamRecord<IN> element) throws Exception {
      if (isProcessingTime) {                        // <-- 从未进入此分支
          if (comparator == null) {
              NFAState nfaState = getNFAState();
              long timestamp = getProcessingTimeService().getCurrentProcessingTime();
              advanceTime(nfaState, timestamp);
              processEvent(nfaState, element.getValue(), timestamp);
              updateNFA(nfaState);
          } else {
              long currentTime = timerService.currentProcessingTime();
              bufferEvent(element.getValue(), currentTime);
          }
      } else { ... }  // <-- 所有测试仅覆盖 else 分支
  }
  ```
  所有 10 处 CepOperator 测试实例化均使用 `isProcessingTime=false`。
- **严重程度**: P1
- **现状**: processing-time 是流处理系统两种核心时间语义之一，此路径涉及直接事件处理、processing-time 定时器触发、comparator 排序等逻辑，完全无测试。
- **风险**: 如果 processing-time 路径存在 bug，只能通过生产环境暴露。
- **建议**: 添加至少一个 `isProcessingTime=true` 的 CepOperator 端到端测试。
- **信心水平**: 高
- **误报排除**: 不是"测试数量不够"的审美问题。核心控制流分支完全未覆盖是结构性缺陷。
- **复核状态**: 未复核

---

### [维度16-02] NFA 条件求值抛异常时的错误路径无测试

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:808-826, 354-361`
- **证据片段**:
  ```java
  // NFA.java:808-826 createDecisionGraph
  try {
      if (checkFilterCondition(context, stateTransition.getCondition(), event)) {
          // ...
      }
  } catch (Exception e) {
      throw new StreamException(ERR_CEP_NFA_FILTER_EXECUTION_FAILED, e);
      // ↑ 异常传播时 SharedBuffer 中已注册的事件和已锁定的节点是否被正确清理？
  }
  ```
  所有 30 个 CEP 测试均使用不会抛异常的 `SimpleCondition`。
- **严重程度**: P2
- **现状**: 用户自定义 `IterativeCondition` 可包含任意逻辑，条件抛异常是合理的边界条件。异常路径下 SharedBuffer 节点是否被正确清理完全没有测试保障。
- **风险**: 如果异常路径未正确释放 SharedBuffer 节点，会导致内存泄漏或状态不一致。
- **建议**: 添加使用抛异常的 `IterativeCondition` 的测试，验证 SharedBuffer 清理和状态恢复。
- **信心水平**: 高
- **误报排除**: 不是"测试数量不够"。异常路径涉及 SharedBuffer 的状态管理完整性，是真实的保护力缺失。
- **复核状态**: 未复核

---

### [维度16-03] CheckpointBarrierTracker extra-ACK 重复触发回调 bug 已知但未修复

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java:110`
- **证据片段**:
  ```java
  if (operatorsToAck.decrementAndGet() <= 0) {  // ← 可变为负数
      if (completionCallback != null && snap != null) {
          completionCallback.accept(snap);  // ← 重复触发
      }
  }
  ```
  测试明确标记为 "Known issue" 但未修复：
  ```java
  @Test
  void testExtraAckTriggersCallbackAgainKnownIssue() {
      // ...
      tracker.acknowledgeOperator(0, new OperatorSnapshotResult());  // 重复 ACK
      assertTrue(callbackCount.get() >= 1, "Known issue: extra ACK triggers callback again");
  }
  ```
- **严重程度**: P2
- **现状**: `operatorsToAck` 使用 `AtomicInteger`，`decrementAndGet() <= 0` 允许计数器变为负值，导致重复调用 `completionCallback`。测试已识别此 bug 但断言改为 `>= 1` 而非修复。
- **风险**: 在 exactly-once 语义下，checkpoint 完成回调重复触发可能导致 sink 端 2PC 提交逻辑收到两次 commit 信号，引发数据重复或状态不一致。
- **建议**: 将 `<= 0` 改为 `== 0`，或使用 `compareAndSet(1, 0)` 确保只触发一次。
- **信心水平**: 高
- **误报排除**: 测试自身已承认这是一个 bug（"Known issue"），不是审计误判。
- **复核状态**: 未复核

---

### [维度16-04] CEP 测试基础设施大量 copy-paste

- **文件**: 16 个 CEP 测试文件（6 个 feedEvents 重复，5 个 setProcessingTimeService 重复，5 个 TestOutput 重复）
- **证据片段**:
  ```java
  // 相同的 feedEvents 方法存在于 TestNFA.java:27-46, TestNFAExtended.java:33-61,
  // TestGreedy.java:36-55, TestNotPattern.java:36-55 等 6 个文件中
  
  // 相同的 setProcessingTimeService 反射方法存在于 5 个 CepOperator 测试文件中
  ```
- **严重程度**: P3
- **现状**: 三个测试辅助模式各被复制 5-6 次，每次约 30-50 行。修改一处逻辑需要在多个文件中分别修改。
- **风险**: 维护风险。如果 `feedEvents` 循环逻辑需要修复，需在 6 个文件中分别修改。
- **建议**: 提取为 `CepTestHelper` 基类或共享工具类。
- **信心水平**: 高
- **误报排除**: 不是"看起来不优雅"。量化：同一方法在 6 个文件中重复，每次修改的遗漏概率可量化。
- **复核状态**: 未复核

---

### [维度16-05] NFA null-pattern 编译结果测试过于浅薄

- **文件**: `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/compiler/TestNFACompiler.java:62-66`
- **证据片段**:
  ```java
  @Test
  public void testNullPatternReturnsEmptyNFA() {
      NFA nfa = NFACompiler.compileFactory(null, false).createNFA();
      assertEquals(0, nfa.getStates().size());
      // 未验证 createInitialNFAState() / process() / advanceTime() 是否安全
  }
  ```
- **严重程度**: P3
- **现状**: 仅验证空 NFA 状态数为 0，不验证后续使用是否安全。
- **风险**: 如果下游代码未对 null pattern 做防护，运行时会抛出 NPE。
- **建议**: 至少验证空 NFA 的 `createInitialNFAState()` 不会返回 null。
- **信心水平**: 中
- **误报排除**: null pattern 是非典型用法，但防御性编程要求验证。
- **复核状态**: 未复核
