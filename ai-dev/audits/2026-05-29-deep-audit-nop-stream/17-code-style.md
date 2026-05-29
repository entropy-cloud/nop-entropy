# 维度17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] 大规模未使用 import（系统性）

- **文件**: `nop-stream/` 全模块 50+ 文件，共 95 个确定未使用的 import
- **证据片段**:
  ```java
  // OperatorChain.java:23-24 — 冗余 NopStreamErrors import（36 处同模式）
  import io.nop.stream.core.exceptions.NopStreamErrors;       // 非静态 import 未使用
  import static io.nop.stream.core.exceptions.NopStreamErrors.*; // 静态 wildcard 实际使用
  
  // TaskManager.java:11 — 无关联的未使用 import
  import java.util.UUID;            // 未使用
  ```
- **严重程度**: P2
- **现状**: 95 个未使用 import，其中 36 个属于 `NopStreamErrors` 非静态 import 与静态 wildcard import 同时存在的重复模式。
- **风险**: 增加代码噪声，影响可维护性。
- **建议**: 批量清理未使用 import。优先清理 `NopStreamErrors` 双重 import 模式（36 处）。
- **信心水平**: 高
- **误报排除**: 排除了 Javadoc-only 引用和注解 import。96 个 import 中有 36 个属于同一种重复模式，说明是系统性问题。
- **复核状态**: 未复核

---

### [维度17-02] import 分组顺序违规（4 个文件）

- **文件**: `ClassNameValidator.java:3-6`, `TwoPhaseCommitSinkFunction.java:14-18`, `FraudDetectionDemo.java:10-19`, `CepOperator.java:66-69`
- **证据片段**:
  ```java
  // ClassNameValidator.java:3-6 — io.nop.* 在 java.* 之前
  import io.nop.api.core.annotations.core.Internal;  // io.nop.* 在第3行
  import java.util.Arrays;                            // java.* 在第4行
  ```
- **严重程度**: P2
- **现状**: 4 个文件违反 `java.* → jakarta.* → third-party → io.nop.*` 的分组顺序。
- **风险**: 影响代码一致性。
- **建议**: 调整 4 个文件的 import 顺序。
- **信心水平**: 高
- **误报排除**: 仅 4/409 文件违反，但属于明确的规范偏离。
- **复核状态**: 未复核

---

### [维度17-03] FraudDetectionDemo 大量使用 System.out 替代已声明的 Logger

- **文件**: `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/FraudDetectionDemo.java:55-209`
- **证据片段**:
  ```java
  private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionDemo.class);
  
  public static void main(String[] args) {
      System.out.println("=== Fraud Detection Demo ===\n");  // 共17处 System.out.println
      // LOG 仅在 catch 块中使用
  }
  ```
- **严重程度**: P2
- **现状**: 声明了 `Logger LOG` 但几乎不使用，通过 17 次 `System.out.println` 输出。
- **风险**: System.out 无法被框架日志配置控制。作为模块入口示例应遵循日志规范。
- **建议**: 将 `System.out.println` 替换为 `LOG.info()`。
- **信心水平**: 高
- **误报排除**: 已排除 `PrintSinkFunction`/`PrintSink`（它们的职责就是向标准输出打印）。
- **复核状态**: 未复核

---

### [维度17-04] 34 个 Nop 原生接口未遵循 I 前缀命名规范

- **文件**: `nop-stream-core/`, `nop-stream-cep/`, `nop-stream-runtime/` 中 34 个 Nop 原生接口
- **证据片段**:
  ```java
  // 未遵循规范的 Nop 原生接口
  public interface SinkFunction<T> { ... }
  public interface DataStream<T> { ... }
  public interface Configuration { ... }
  
  // 遵循规范的接口
  public interface IKeyedStateBackend<K> { ... }
  public interface IStateBackend { ... }
  ```
- **严重程度**: P3
- **现状**: 100 个接口中仅 12 个遵循 I 前缀规范。其中 62 个来自 Flink 移植代码（保留原名合理），但 34 个 Nop 原生接口也未使用 I 前缀。
- **风险**: 新增接口时可能延续不规范命名。
- **建议**: 新增 Nop 原生接口时遵循 I 前缀规范。现有接口暂不改动。
- **信心水平**: 高
- **误报排除**: 已区分 Flink 移植代码（62 个，保留原名合理）和 Nop 原生接口（34 个）。
- **复核状态**: 未复核
