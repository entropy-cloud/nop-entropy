# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] nop-stream-cep 测试文件系统性 import 排序违规

- **文件**: `nop-stream-cep/src/test/java/io/nop/stream/cep/` 下约 27 个文件
- **证据片段**:
  ```java
  import io.nop.commons.tuple.Tuple2;           // io.nop.* - 应在最后
  import io.nop.stream.cep.Event;
  import org.junit.jupiter.api.Test;            // org.* - 应在中间
  import java.util.ArrayList;                   // java.* - 应在最前
  ```
- **严重程度**: P2
- **现状**: io.nop.* 放在 java.* 和 org.* 之前，违反 java.* → jakarta.* → third-party → io.nop.* 顺序。仅限测试文件，主源代码全部合规。
- **建议**: 统一修复测试文件 import 排序。
- **信心水平**: 确定
- **误报排除**: core 和 runtime 测试文件合规。
- **复核状态**: 未复核

### [维度17-02] FraudDetectionDemo 使用 System.out

- **文件**: `nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/FraudDetectionDemo.java:54-211`
- **严重程度**: P3
- **现状**: Demo 类用 System.out 输出到控制台。PrintSinkFunction/PrintSink 的 System.out 是设计目的。
- **建议**: Demo 类可接受。
- **信心水平**: 确定
- **误报排除**: PrintSink 的 System.out 是设计目的。
- **复核状态**: 未复核

### [维度17-03] WatermarksWithIdleness 缩进错误

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/eventtime/WatermarksWithIdleness.java:50-53, 83`
- **严重程度**: P3
- **现状**: 构造器和内部类声明缩进为 8 空格而非 4 空格。
- **建议**: 修正缩进。
- **信心水平**: 确定
- **误报排除**: 非 tab/space 混用。
- **复核状态**: 未复核

## 正面发现

- 命名规范整体合规
- 主源代码 import 分组规范
- 未使用 import 已清理

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 17-01 | P2 | cep 测试文件 (27个) | import 排序违规 |
| 17-02 | P3 | FraudDetectionDemo.java | System.out |
| 17-03 | P3 | WatermarksWithIdleness.java | 缩进错误 |
