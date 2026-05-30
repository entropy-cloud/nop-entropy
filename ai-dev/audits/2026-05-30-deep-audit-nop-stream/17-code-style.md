# 维度 17：代码风格与规范

### [维度17-01] NFACompiler.java / CepPatternBuilder.java：泛型 Pattern 类型被擦除为原始类型

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java:246-260,724-727`, `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:28-138`
- **证据片段**:
  ```java
  Pattern patternToCheck = currentPattern;       // 应为 Pattern<T, ?>
  while (patternToCheck != null) {
      checkPatternNameUniqueness(patternToCheck);
      patternToCheck = patternToCheck.getPrevious();
  }
  ```
- **严重程度**: P2
- **现状**: NFACompiler.java 中 4 处、CepPatternBuilder.java 中 10+ 处 raw type 使用。同类文件中其他方法使用了正确的泛型形式，说明原始类型并非设计意图。
- **风险**: 在 NFA 编译器中，类型擦除意味着错误类型的事件对象可能在运行时通过并导致 `ClassCastException`。
- **建议**: 将所有 `Pattern` 替换为 `Pattern<?, ?>` 或 `Pattern<T, ?>`。
- **信心水平**: 确定
- **误报排除**: 不是风格偏好。同类文件中有正确使用泛型的例子，说明这是遗漏而非设计选择。
- **复核状态**: 未复核

### [维度17-02] 3 个文件 import 分组顺序违反规范

- **文件**: `nop-stream-cep/.../CepOperator.java:36-69`, `nop-stream-fraud-example/.../FraudDetectionDemo.java:10-19`, `nop-stream-core/.../TwoPhaseCommitSinkFunction.java:14-18`
- **证据片段**:
  ```java
  // CepOperator.java: org.slf4j 出现在 io.nop.* 之后
  import io.nop.stream.core.util.OutputTag;
  import org.slf4j.Logger;
  ```
- **严重程度**: P3
- **现状**: 3 个文件的 import 分组违反了 java.* → jakarta.* → third-party → io.nop.* 规范。
- **风险**: 使依赖审计更困难。
- **建议**: 调整 import 顺序。
- **信心水平**: 确定
- **误报排除**: 是具体的排序错误，不是整体风格偏好。
- **复核状态**: 未复核

### [维度17-03] NFA.java import 字母序错乱 + static import 散布

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:49-54`
- **证据片段**:
  ```java
  import io.nop.stream.core.util.FunctionUtils;  // core
  import io.nop.stream.cep.NopCepErrors;          // cep (回退)
  import static io.nop.stream.cep.NopCepErrors.*;  // static 夹在中间
  import io.nop.stream.core.util.FunctionUtils;    // core (又跳回)
  ```
- **严重程度**: P3
- **现状**: cep/core 子包 import 字母序错乱，static import 散布在非 static import 中。
- **风险**: 增加维护成本。
- **建议**: 重新排序 import。
- **信心水平**: 确定
- **误报排除**: 具体的排序错误。
- **复核状态**: 未复核

### [维度17-04] 16 个文件 static import 散布在非 static import 中

- **文件**: nop-stream-core (13个文件)、nop-stream-runtime (2个文件)、nop-stream-cep (1个文件)
- **证据片段**:
  ```java
  import static io.nop.stream.core.exceptions.NopStreamErrors.*;  // static 夹在中间
  import io.nop.stream.core.streamrecord.StreamRecord;              // non-static 继续
  ```
- **严重程度**: P3
- **现状**: 16 个 src/main 文件中 static import 与非 static import 交错。
- **风险**: reviewer 检查 "这个 static 常量来自哪里？" 时需要搜索整个 import 区。
- **建议**: 将所有 static import 移到非 static import 之后。
- **信心水平**: 确定
- **误报排除**: 全局性模式，影响多个文件。
- **复核状态**: 未复核

### [维度17-05] StreamOperator.java 源码中存在中文注释

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamOperator.java:125-128,136-138`
- **证据片段**:
  ```java
  // 默认实现：返回空快照
  return OperatorSnapshotResult.empty();
  ```
- **严重程度**: P3
- **现状**: 核心接口中的中文注释。
- **风险**: 降低非中文贡献者的可读性。
- **建议**: 翻译为英文。
- **信心水平**: 确定
- **误报排除**: 项目规范要求英文。
- **复核状态**: 未复核

### [维度17-06] Transformation.java static final 可变对象命名未遵循 UPPER_SNAKE_CASE

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/transformation/Transformation.java:27`
- **证据片段**:
  ```java
  private static final AtomicInteger idCounter = new AtomicInteger(0);
  ```
- **严重程度**: P3
- **现状**: `idCounter` 应为 `ID_COUNTER`。
- **风险**: reviewer 可能误以为它是实例字段。
- **建议**: 改为 `ID_COUNTER`。
- **信心水平**: 确定
- **误报排除**: 唯一的 static final 可变对象命名违规。
- **复核状态**: 未复核
