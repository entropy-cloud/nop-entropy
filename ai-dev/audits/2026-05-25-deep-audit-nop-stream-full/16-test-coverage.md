# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] CEP 模块 AfterMatchSkipStrategy 零测试覆盖

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipPastLastStrategy.java:28-46` 等 7 个实现类
- **证据片段**:
  ```java
  // SkipPastLastStrategy.java:37-46
  protected EventId getPruningId(final Collection<Map<String, List<EventId>>> match) {
      EventId pruningId = null;
      for (Map<String, List<EventId>> resultMap : match) {
          for (List<EventId> eventList : resultMap.values()) {
              pruningId = max(pruningId, eventList.get(eventList.size() - 1));
          }
      }
      return pruningId;
  }
  ```
  所有 NFA 测试都硬编码使用 `AfterMatchSkipStrategy.noSkip()`，无 skip strategy 测试。
- **严重程度**: P1
- **现状**: CEP 模块有 7 个 `AfterMatchSkipStrategy` 实现类，但没有任何测试验证非 noSkip 策略的剪枝行为。
- **风险**: 剪枝算法复杂且易出错，无覆盖意味着回归时无法发现漏匹配或重复匹配。
- **建议**: 新增 `TestAfterMatchSkipStrategies.java`，覆盖每种 skip strategy。
- **误报排除**: 非 N90 泛泛指出缺测试，而是精准定位到 AfterMatchSkipStrategy 这一类未测试策略。
- **复核状态**: 未复核

---

### [维度16-02] CEP 模块 NFACompiler 1090 行复杂编译逻辑仅 5 个浅层测试

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/compiler/TestNFACompiler.java:20-100`
- **证据片段**:
  ```java
  @Test public void testSimplePatternCompilation() { ... }      // 仅检查 states 非空
  @Test public void testPatternWithFollowedBy() { ... }         // 仅检查 states >= 3
  @Test public void testNullPatternReturnsEmptyNFA() { ... }    // null 输入
  @Test public void testStateTransitionsExist() { ... }         // 仅检查 transition 非空
  @Test public void testDuplicatePatternNamesThrow() { ... }    // 重复名字
  ```
- **严重程度**: P1
- **现状**: 1090 行的 NFA 编译器有 6 个主要编译分支（NOT_FOLLOW/NOT_NEXT/LOOPING/TIMES/GROUP/optional），但测试仅覆盖 begin→next 和 followedBy 两个最简场景。
- **风险**: times 量词、greedy/non-greedy、NOT_FOLLOW/NOT_NEXT 等核心路径无覆盖。
- **建议**: 新增覆盖 times(3,5)、oneOrMore、optional+followedByAny、GroupPattern 等场景的测试。
- **误报排除**: 精准对比 NFACompiler 的分支复杂度与现有测试的覆盖面。
- **复核状态**: 未复核

---

### [维度16-03] TestCepOperatorStateRecovery 的"恢复"测试未验证独立实例恢复

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorStateRecovery.java:129-153`
- **证据片段**:
  ```java
  NFAState capturedState = op.getNFAStateForTesting();
  op.updateNFAStateForTesting(capturedState);  // 写回同一实例!
  ```
- **严重程度**: P1
- **现状**: 测试名为"快照恢复"但未创建新实例、未做序列化 round-trip，仅在同一实例上写回状态。
- **风险**: 无法捕获序列化/反序列化丢失、SharedBuffer 引用完整性等真实恢复路径问题。
- **建议**: 创建全新 CepOperator 实例，从序列化后的快照恢复，验证行为正确。
- **误报排除**: 精准定位到 `op.updateNFAStateForTesting(capturedState)` 写回同一实例。
- **复核状态**: 未复核

---

### [维度16-04] CEP Pattern API 和 CepPatternBuilder 零集成测试

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:28-212`
- **证据片段**:
  ```java
  // PatternStreamBuilder.java:140 — inputSerializer 被硬编码为 null!
  final TypeSerializer<IN> inputSerializer = null;
  ```
- **严重程度**: P2
- **现状**: CEP 模块公共 API 层（`CepPatternBuilder`、`CEP.pattern()`、`PatternStream.select`）完全没有测试。`PatternStreamBuilder.build` 中 `inputSerializer` 硬编码为 null。
- **风险**: `inputSerializer = null` 在生产环境下可能导致 NPE。
- **建议**: 新增 API 层测试，确认 `inputSerializer = null` 是否为已知 TODO。
- **误报排除**: 精准定位到未测试的 API 层和 `inputSerializer = null` 硬编码。
- **复核状态**: 未复核

---

### [维度16-05] DebeziumCdcSourceFunction 仅 2 个测试且全部是 happy path

- **文件**: `nop-stream-connector/src/test/java/io/nop/stream/connector/TestDebeziumCdcSourceFunction.java:16-33`
- **证据片段**:
  ```java
  @Test
  void testNullConfigRejected() {
      assertThrows(IllegalArgumentException.class, () -> new DebeziumCdcSourceFunction(null));
  }
  @Test
  void testCancelBeforeRun() {
      DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
      assertDoesNotThrow(source::cancel);
  }
  ```
- **严重程度**: P1
- **现状**: CDC 源连接器仅验证 null 配置拒绝和 cancel 不抛异常，未覆盖 `run()`、`snapshotState()`/`initializeState()`、`truncateForDrain()`。
- **风险**: CDC 连接器是数据管道关键入口，checkpoint 恢复路径零覆盖。
- **建议**: 至少新增 mock run 测试和 snapshot/restore round-trip 测试。
- **误报排除**: 非 N90 重复，精准针对 connector 模块的 DebeziumCdcSourceFunction。
- **复核状态**: 未复核

---

### [维度16-06] TestConnectorConsistencyCapability 大量测试仅验证枚举序号而非实际行为

- **文件**: `nop-stream-connector/src/test/java/io/nop/stream/connector/TestConnectorConsistencyCapability.java:29-189`
- **证据片段**:
  ```java
  @Test
  void testBatchLoaderSourceDeclaresAtLeastOnce() {
      assertEquals(SourceConsistencyCapability.AT_LEAST_ONCE,
              SourceConsistencyCapability.AT_LEAST_ONCE);  // 永远为 true
  }
  ```
- **严重程度**: P2
- **现状**: 17 个测试方法中前 8 个本质上是 `assertEquals(X, X)`，永远不会失败。未验证实际连接器类的一致性能力声明。
- **风险**: 连接器 capability 被错误修改时无法发现。
- **建议**: 替换为直接构造连接器并验证其声明 capability 的契约测试。
- **误报排除**: `assertEquals(X, X)` 形式的测试是假阳性，提供零回归保护力。
- **复核状态**: 未复核
深挖第 2 轮追加完成

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 16-01 | **保留 P1** | 7个 skip strategy 实现零测试 |
| 16-02 | **降级至 P2** | 有10+间接测试覆盖基本路径，但复杂路径确实缺失 |
| 16-03 | **驳回** | 不是 bug，是测试策略不足。建议与 16-10 合并 |
| 16-04 | **保留 P2** | CepPatternBuilder 零测试 |
| 16-05 | **保留 P1** | CDC source 核心路径零覆盖 |
| 16-06 | **保留 P2** | tautological 断言 |
| 16-07 | **保留 P1** | 分布式测试实为单线程同步 |
| 16-08 | **降级至 P2** | restoreFromCheckpoint 被调用但状态未被应用 |
| 16-09 | **保留 P1** | @Disabled 故障测试类 |
| 16-10 | **保留 P2** | StateBackend 未测 snapshot/restore |
| 16-11 | **保留 P2** | 类名误导，实际未测 WindowOperator |
| 16-12 | **保留 P2** | 与 16-06 非重复，覆盖面问题 |
