# 维度 16：测试覆盖与质量

**审计日期**: 2026-05-27

## 测试统计

| 模块 | 主代码文件数 | 测试文件数 | 测试/代码比 |
|------|------------|-----------|-----------|
| nop-stream-core | 263 | 91 | 0.35 |
| nop-stream-cep | 82 | 20 | 0.24 |
| nop-stream-connector | 6 | 7 | 1.17 |
| nop-stream-runtime | 42 | 32 | 0.76 |
| nop-stream-fraud-example | 9 | 4 | 0.44 |
| **总计** | **406** | **174** | **0.43** |

## 发现

### D16-01: nop-stream-cep 测试覆盖率不足
- **严重程度**: P1
- **现状**: 82 个主代码文件仅 20 个测试文件（覆盖率 24%）。以下关键类缺少直接测试：
  - `CepOperator.java` (593 行) — 无直接单元测试
  - `PatternStream.java` — 无测试
  - `PatternStreamBuilder.java` — 无测试
  - `CepPatternBuilder.java` — 仅通过 `TestCepPatternBuilderModel` 间接测试
  - `SharedBufferAccessor.java` (380 行) — 无直接测试
- **风险**: CEP 引擎的核心操作符没有独立测试，状态恢复和超时处理可能存在未发现的 bug。
- **建议**: 优先添加 `TestCepOperator` 单元测试和 `TestPatternStreamBuilder` 测试。

### D16-02: nop-stream-core 多个关键运算器缺少独立测试
- **严重程度**: P2
- **现状**: 以下具体类没有对应的 Test 文件：
  - `StreamSourceOperator.java` — 虽然在集成测试中有间接覆盖
  - `WindowAggregationOperator.java` (658 行) — 有 `TestWindowAggregationOperator*` 系列测试（SnapshotRestore, LateData, ProcessingTimeTimer），但无基础正确性测试
  - `MemoryKeyedStateBackend.java` (1251 行) — 有 `TestMemoryKeyedStateBackendFix` 和 `TestStateSnapshotRoundTrip`
  - `ChainingOutput.java` — 无测试
  - `KeyExtractingOutput.java` — 无测试
  - `GraphExecutionPlan.java` — 有 `TestGraphExecutionPlan`
- **风险**: 核心运算器缺少独立单元测试意味着只依赖集成测试验证正确性。
- **建议**: 对 `StreamSourceOperator` 和 `ChainingOutput` 添加单元测试。

### D16-03: 约 125 个测试文件使用了 assertThrows
- **严重程度**: —
- **现状**: 174 个测试文件中约 49 个使用了 `assertThrows`，覆盖率 ~28%。
- **风险**: 部分 ~126 个测试文件可能缺少异常路径测试。
- **建议**: 在审计中不要求全面覆盖，但核心运算器应有异常路径测试。

### D16-04: 接口/抽象类/枚举无测试 — 可接受
- **严重程度**: —
- **现状**: 大量无测试的文件是接口、抽象类或枚举。这些通常通过实现类的测试间接覆盖。
- **结论**: 合理

### D16-05: 测试覆盖了关键功能领域
- **严重程度**: — ✅
- **现状**: 以下关键领域有良好测试：
  - ✅ Checkpoint 全流程（触发、恢复、持久化、Exactly-Once）
  - ✅ Window Operator 正确性（Session/Tumbling/Sliding, EventTime/ProcessingTime）
  - ✅ NFA 状态机（基础、扩展、贪婪、NOT 模式）
  - ✅ Barrier 传播和对齐
  - ✅ Watermark 传播
  - ✅ 分布式执行
  - ✅ StateBackend（Memory, Snapshot, Shard）
  - ✅ Connector（Batch, Message, CDC）

### D16-06: 状态分片 (StateShard) 测试充分
- **严重程度**: — ✅
- **现状**: `TestStatePath`, `TestStateShard`, `TestStateShardRouting` 覆盖了分片路由的核心逻辑。

### D16-07: Fraud Detection Example 测试覆盖
- **严重程度**: — ✅
- **现状**: 4 个 Pattern 实现都有对应测试，覆盖了正常路径。

## 优先添加测试建议

1. **P1**: `TestCepOperator` — CEP 核心运算器的独立单元测试
2. **P2**: `TestStreamSourceOperator` — Source 运算器的基础测试
3. **P2**: `TestChainingOutput` — Operator chain 的异常处理测试
4. **P3**: `TestPatternStreamBuilder` — Pattern API 构建测试
