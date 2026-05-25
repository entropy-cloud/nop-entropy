# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] import 分组顺序系统性违反：`io.nop.*` 排在 `java.*` 之前

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:10-20`（代表 140+ 文件）
- **证据片段**:
  ```java
  import io.nop.stream.core.common.functions.KeySelector;
  
  import java.io.ByteArrayInputStream;
  import java.io.ByteArrayOutputStream;
  import java.io.IOException;
  ```
- **严重程度**: P2
- **现状**: 约 140 个非生成源文件的 import 声明将 `io.nop.*` 放在 `java.*` 之前，与仓库约定方向相反。
- **风险**: 系统性偏离导致约定永远无法收敛。
- **建议**: 一次性通过 IDE 的 "Optimize Imports" 统一修正。
- **误报排除**: 覆盖约 36% 源文件的系统性偏离，AGENTS.md 明确定义了 import 分组规范。
- **复核状态**: 未复核

---

### [维度17-02] import 组内未按字母序排列

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStream.java:10-17`
- **证据片段**:
  ```java
  import io.nop.stream.core.common.eventtime.WatermarkStrategy;
  import io.nop.stream.core.common.functions.KeySelector;
  import io.nop.stream.core.common.functions.FilterFunction;  // 非字母序
  ```
- **严重程度**: P3
- **现状**: import 组内无序排列。
- **建议**: 随 import 排序修复一起统一处理。
- **误报排除**: 跨多个文件的系统性无序。
- **复核状态**: 未复核

---

### [维度17-03] 代码中使用完全限定名替代 import 声明

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:66-105`
- **证据片段**:
  ```java
  private final List<io.nop.stream.core.operators.StreamOperator<?>> operators;
  public void processElement(io.nop.stream.core.streamrecord.StreamRecord<?> record) {
  ```
- **严重程度**: P3
- **现状**: 代码体中使用完全限定名引用约 13 处，而这些类型未在文件头部 import。
- **建议**: 添加 import 并替换为短名。
- **误报排除**: 在同仓库的 nop-core 等模块中极其罕见。
- **复核状态**: 未复核

---

### [维度17-04] FraudDetectionDemo 使用 System.out.println 替代日志框架

- **文件**: `nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/FraudDetectionDemo.java:47-88`
- **证据片段**:
  ```java
  System.out.println("=== Fraud Detection Demo ===\n");
  ```
  共 20 处 `System.out.println()`。
- **严重程度**: P3
- **现状**: 示例代码使用 `System.out` 而非 SLF4J。
- **建议**: 引入 Logger 替换为 `LOG.info()`。
- **误报排除**: 不是 PrintSink 的合理 System.out 用法，是演示类的临时输出。
- **复核状态**: 未复核

---

### [维度17-05] Javadoc 过度冗长，包含实现细节和模板化描述

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskExecutor.java:26-59`
- **证据片段**:
  ```java
  /**
   * Manages the execution of streaming tasks using a thread pool.
   * <p>TaskExecutor is responsible for:
   * <ul>
   *   <li>Managing a pool of worker threads for task execution</li>
   *   <li>Submitting tasks for execution based on JobVertex parallelism</li>
   *   ...（共 30+ 行）
   */
  ```
- **严重程度**: P3
- **现状**: 多个核心类的类级别 Javadoc 长达 30-50 行，呈现 AI 生成典型模式。
- **建议**: 保留一句话摘要 + 核心不变量即可。
- **误报排除**: 同模块内的 `MemoryKeyedStateBackend` 等类 Javadoc 仅 1-3 行，说明过度冗长不是仓库整体风格。
- **复核状态**: 未复核
深挖第 2 轮追加完成

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 17-01 | **保留 P2** | 实测约110个文件（审核称140+略高但结论成立）。系统性违反AGENTS.md import分组规范确认。 |
| 17-02 | **降级至 P4** | 组内字母序问题与17-01高度关联，IDE "Optimize Imports"可同时修复。独立评级P4。 |
| 17-03 | **保留 P3** | 约13处FQN使用确认。在同仓库其他模块中罕见，是nop-stream特有风格问题。 |
| 17-04 | **降级至 P4** | Demo/Example模块中System.out.println是合理的演示代码实践。实际计数17处（审核称20处略高）。 |
| 17-05 | **保留 P3** | 33行类级Javadoc确认，含AI生成典型模式（多级列表、章节标题）。 |
