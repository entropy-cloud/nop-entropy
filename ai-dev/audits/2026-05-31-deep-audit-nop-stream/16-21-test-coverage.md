# 维度 16/21：测试覆盖与质量 + 单元测试有效性

## 维度 16：测试覆盖与质量

### 关键发现

- 16-01 (P2): WindowOperator 无直接命名的单元测试，间接测试覆盖不充分
- 16-02 (P2): TestOutput 重复 12 次，OperatorTestHarness 已存在但未被使用
- 16-03 (P2): CepOperator 的 state recovery 未走完整 snapshot/restore 管线
- 16-04 (P3): NFACompiler 编译后 NFA 运行时行为验证不足
- 16-05 (P3): runtime 中 14 个非平凡源文件无直接测试
- 16-06 (P3): checkpoint 测试同质化，缺少并发和中间状态恢复场景
- 16-07 (信息): 测试 setup 不过重，但缺少统一 Harness
- 16-08 (信息): core 289 源文件 / 117 测试文件比率合理（接口/枚举/数据类占大量源文件）

### 测试统计

| 子模块 | 主源文件 | 测试文件 | 比率 |
|--------|---------|---------|------|
| core | 289 | 117 | 1:2.5 |
| cep | 77 | 33 | 1:2.3 |
| runtime | 40 | 49 | 1:0.8 |
| connector | 7 | 8 | 1:0.9 |
| fraud-example | 10 | 4 | 1:2.5 |

## 维度 21：单元测试有效性

### 反模式命中

- **P-1 (getter/setter 往返)**: TestCheckpointConfig.testSettersAndGetters()、TestCheckpointBarrier、TestOperatorSnapshotResult、4个 Transformation 测试文件(~1500行)
- **P-2 (元数据属性/常量)**: TestCheckpointType(枚举数量)、TestDeweyNumber(常量)、NFAStateNameHandler(常量)、fraud-example testConstants()
- **P-3 (只测 happy path)**: TestPatternStreamBuilder 3个方法全是 assertNotNull
- **P-5 (过度 assertNotNull)**: PatternStreamBuilder 测试无法发现接线错误
- **P-4 (与实现耦合)**: fraud-example testPatternCreation() 断言节点名称

### 有效测试与低价值测试比例

- 有效测试方法：约 60-65%
- 低价值测试方法：约 35-40%（getter/setter、常量、assertNotNull）

### 高价值测试（确认有 bug 捕获能力）

TestOperatorSnapshot、TestBarrierPropagation、TestCheckpointParticipant、TestHeapInternalTimerService、TestMemoryStateBackend、TestNFA/TestPattern、TestPendingCheckpoint、TestCheckpointCoordinator 等。
