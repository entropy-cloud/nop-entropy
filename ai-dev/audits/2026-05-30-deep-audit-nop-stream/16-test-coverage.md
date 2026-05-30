# 维度 16：测试覆盖与质量

### [维度16-01] NFACompiler.canProduceEmptyMatches() 公共 API 零测试覆盖

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:97-132`
- **证据片段**:
  ```java
  public static boolean canProduceEmptyMatches(final Pattern<?, ?> pattern) {
      NFAFactoryCompiler<?> compiler = new NFAFactoryCompiler<>(notNull(pattern, "pattern"));
      compiler.compileFactory();
      // ... 图遍历检测 PROCEED -> Final 路径 ...
  }
  ```
- **严重程度**: P2
- **现状**: 公共静态方法，35 行逻辑，零测试覆盖。grep 验证 test 目录下零命中。
- **风险**: 该方法直接影响 `A*`、`A?`、`A* B?` 等模式的语义判断。任何重构都无法被测试捕获。
- **建议**: 添加针对空匹配模式（optional、oneOrMore、times(0,n)）和非空匹配模式的单元测试。
- **信心水平**: 确定
- **误报排除**: 不是测试文件缺失，而是核心公共 API 方法完全无测试。
- **复核状态**: 未复核

### [维度16-02] NFACompiler 关键错误路径未被测试约束

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:184-192,210-219`
- **证据片段**:
  ```java
  if (lastPattern.getQuantifier().getConsumingStrategy()
          == Quantifier.ConsumingStrategy.NOT_FOLLOW
      && (!windowTimes.containsKey(lastPattern.getName())
          || windowTimes.get(lastPattern.getName()) <= 0)
      && getWindowTime() == 0) {
      throw new MalformedPatternException(
          "NotFollowedBy is not supported without windowTime...");
  }
  ```
- **严重程度**: P2
- **现状**: NFAFactoryCompiler 内部两个关键 MalformedPatternException 抛出路径无测试覆盖。TestNFACompiler 仅 100 行 5 个方法，TestNFACompilerExtended 10 个方法但不涉及这两个路径。
- **风险**: 编译阶段的最后防线。如果重构破坏了校验逻辑，会静默产生错误的 NFA。
- **建议**: 添加 NotFollow 无 windowTime、事件间窗口时间大于全局窗口时间的负面测试。
- **信心水平**: 确定
- **误报排除**: TestPatternValidation 覆盖了 Pattern 构建阶段的验证，但编译器级别的校验是不同的检查阶段。
- **复核状态**: 未复核

### [维度16-03] NFACompiler.copyWithoutTransitiveNots 循环检测零直接测试

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:490-496`
- **证据片段**:
  ```java
  private State<T> copyWithoutTransitiveNots(final State<T> sinkState, final Set<String> visited) {
      if (!visited.add(sinkState.getName())) {
          throw new StreamException(ERR_STREAM_STATE_ERROR)
                  .param(ARG_DETAIL,
                      "Circular PROCEED dependency detected at state: " + sinkState.getName());
      }
      ...
  }
  ```
- **严重程度**: P2
- **现状**: 循环依赖检测是防止 Pattern 编译进入死循环的关键保护，但无任何测试直接触发此路径。
- **风险**: 如果循环检测被意外移除或弱化，恶意或错误的 Pattern 会导致编译时无限循环。
- **建议**: 构造一个会产生循环 PROCEED 依赖的 Pattern，验证是否能快速失败。
- **信心水平**: 确定
- **误报排除**: 不同于普通的错误路径测试——死循环防护是编译器安全性的关键。
- **复核状态**: 未复核

### [维度16-04] NFA 的 Pending State 超时处理路径未测试

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:289-294`
- **证据片段**:
  ```java
  if (getState(computationState).isPending()) {
      potentialMatches.add(computationState);
      continue;
  }
  ```
- **严重程度**: P2
- **现状**: Pending 状态超时处理从未被测试触发。所有超时测试都不涉及 NotFollow + windowTime 组合。
- **风险**: Pending 状态的超时处理与 after-match skip 策略交互，如果此路径有 bug，NOT 模式在窗口超时后行为错误。
- **建议**: 添加 NotFollow + windowTime + 超时的端到端测试。
- **信心水平**: 很可能
- **误报排除**: grep 验证测试目录中 `isPending` 和 `State.StateType.Pending` 零命中。
- **复核状态**: 未复核

### [维度16-05] WindowOperator.snapshotState/restoreState 端到端路径仅间接测试

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:305-323`
- **证据片段**:
  ```java
  public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
      OperatorSnapshotResult result = super.snapshotState(context);
      if (triggerAccumulators != null) {
          result.putOperatorState("trigger-accumulators", new HashMap<>(triggerAccumulators));
      }
      return result;
  }
  ```
- **严重程度**: P2
- **现状**: 测试使用 TestableWindowOperator 子类而非直接测试 WindowOperator 本身。restoreState → open() → applyPendingRestoreState 生命周期顺序依赖未被直接验证。
- **风险**: 如果生命周期顺序被破坏，窗口状态在 checkpoint 恢复后可能丢失。
- **建议**: 添加直接针对 WindowOperator snapshot/restore 生命周期的测试。
- **信心水平**: 很可能
- **误报排除**: 这是核心算子（1090 行）的关键生命周期路径。
- **复核状态**: 未复核

### [维度16-06] TestNFACompiler 仅 100 行，测试深度不足

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/compiler/TestNFACompiler.java`
- **证据片段**: 全文件仅 100 行、5 个测试方法。
- **严重程度**: P3
- **现状**: NFACompiler 是 1098 行的核心文件，但测试仅 5 个方法。Greedy + untilCondition、嵌套 GroupPattern、updateWithGreedyCondition 等关键路径未直接测试。
- **风险**: 编译器内部的图结构正确性没有被直接验证。
- **建议**: 补充针对复杂模式组合的编译器级测试。
- **信心水平**: 确定
- **误报排除**: 虽然 TestNFAExtended 和 TestGreedy 有端到端行为测试，但编译器内部的图结构正确性没有被直接验证。
- **复核状态**: 未复核

### [维度16-07] 大量测试共享重复的 TestOutput 基础设施代码

- **文件**: `nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/` 下 5+ 个测试文件
- **证据片段**:
  ```java
  static class TestOutput<T> implements Output<StreamRecord<T>> {
      private final List<StreamRecord<T>> records = new ArrayList<>();
      @Override public void collect(StreamRecord<T> record) { records.add(record); }
      @Override public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) { throw new UnsupportedOperationException(); }
      // ... 8 个方法 ...
  }
  ```
- **严重程度**: P3
- **现状**: TestOutput 在 5+ 个测试文件中独立重复定义，每个约 50-60 行。
- **风险**: 如果 Output 接口新增方法，所有独立定义都需要逐一更新，容易遗漏。
- **建议**: 提取为共享的测试工具类。
- **信心水平**: 确定
- **误报排除**: 不是功能 bug，但增加了维护成本。
- **复核状态**: 未复核

### [维度16-08] NFA 测试中 advanceTime 调用未验证其独立行为

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/TestNFA.java:27-46`
- **证据片段**:
  ```java
  Tuple2<...> pending = nfa.advanceTime(accessor, state, timestamp, AfterMatchSkipStrategy.noSkip());
  Collection<Map<String, List<Event>>> matches = nfa.process(accessor, state, events.get(i), ...);
  matches.addAll(pending.f0);  // advanceTime 的结果被合并
  ```
- **严重程度**: P3
- **现状**: 所有 NFA 测试的 feedEvents helper 将 advanceTime 的结果与 process 的结果合并，无法区分匹配来源。
- **风险**: 如果 advanceTime 在非超时场景下错误地产生了匹配（或遗漏了应该产生的匹配），测试无法感知。
- **建议**: 添加独立验证 advanceTime 行为的测试。
- **信心水平**: 很可能
- **误报排除**: TestNFAWindowTimeout 单独检查了 timeoutResults，但 advanceTime 的非超时产出从未被独立验证。
- **复核状态**: 未复核
