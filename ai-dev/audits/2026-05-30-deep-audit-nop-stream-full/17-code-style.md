# 维度 17：代码风格与规范

## 审计范围

nop-stream 全部 5 个有代码的子模块。

## 第 1 轮（初审）

### [维度17-01] Import 排序违反分组约定：org.slf4j 在 java.* 之前

- **文件**: `nop-stream-fraud-example/.../FraudDetectionDemo.java:10-17`
- **证据片段**:
```java
import org.slf4j.Logger;        // 第三方
import org.slf4j.LoggerFactory; // 第三方
import java.math.BigDecimal;    // java.*
import java.util.ArrayList;     // java.*
```
- **严重程度**: P3
- **现状**: java.* imports 应在 org.slf4j 之前。
- **建议**: 调整 import 顺序。
- **信心水平**: 确定
- **误报排除**: import 排序是 AGENTS.md 明确要求的规范。
- **复核状态**: 未复核

### [维度17-02] Import 排序违反分组约定：io.nop.* 在 org.* 之前

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:14-16`
- **严重程度**: P3
- **现状**: 第三方 (org.*) 应在 io.nop.* 之前。
- **建议**: 调整 import 顺序。
- **信心水平**: 确定
- **误报排除**: import 排序是 AGENTS.md 明确要求的规范。
- **复核状态**: 未复核

### [维度17-03] Import 组内未按字典序 + import static 穿插

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:8-9,17-19`
- **严重程度**: P3
- **现状**: io.nop.commons 应在 io.nop.core 之前。import static 应放在所有普通 import 之后。
- **建议**: 调整 import 顺序。
- **信心水平**: 确定
- **误报排除**: import 排序是 AGENTS.md 明确要求的规范。
- **复核状态**: 未复核

### [维度17-04] 常量命名不符合 UPPER_SNAKE_CASE

- **文件**: `nop-stream-core/.../transformation/Transformation.java:27`
- **证据片段**:
```java
private static final AtomicInteger idCounter = new AtomicInteger(0);
```
- **严重程度**: P3
- **现状**: static final 字段应使用 UPPER_SNAKE_CASE。
- **建议**: 改为 ID_COUNTER。
- **信心水平**: 确定
- **误报排除**: 是代码规范要求。
- **复核状态**: 未复核

### 通过项

- 类名 PascalCase: 全部合规
- 接口 I+PascalCase: 12 个接口均以 I 开头
- 方法名 camelCase: 未发现违规
- 包名 io.nop.stream.*: 全部 621 个文件正确
- 无 org.apache.* 残留 import
- System.out 仅出现在设计预期位置
- Logger 统一使用 SLF4J
