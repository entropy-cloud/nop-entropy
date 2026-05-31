# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] Import 分组不符合 AGENTS.md 规范

- **文件**: 92 个主源文件 + 175 个测试文件
- **证据片段**:
```java
// WindowOperator.java — static import 穿插在 regular import 之间
import io.nop.api.core.annotations.core.Internal;
import static io.nop.api.core.util.Guard.checkArgument;  // static 穿插
import io.nop.commons.tuple.Tuple2;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;  // static 穿插
import io.nop.stream.core.checkpoint.TaskStateSnapshot;          // regular 继续
```
- **严重程度**: P3
- **现状**: static import 与 regular import 交错（92 个主源文件），测试中 io.nop.* 出现在 java/org.junit 之前（175 个测试文件）。这是全模块的统一风格。
- **风险**: 不影响功能，但不符合 AGENTS.md 规范的 java.* → jakarta.* → third-party → io.nop.* 分组。
- **建议**: 后续统一重构时按 AGENTS.md 规范整理 import。
- **信心水平**: 确定
- **误报排除**: 全项目范围的统一风格问题，非个例。
- **复核状态**: 未复核

---

### 无额外问题

| 检查项 | 结论 |
|--------|------|
| 命名规范 | PascalCase/camelCase/UPPER_SNAKE_CASE 均合规 |
| System.out/err | 仅 FraudDetectionDemo.java（示例），框架代码零违规 |
| bare RuntimeException | 零发现，全部使用 StreamException/StreamRuntimeException |

## 维度复核结论

（待复核）

## 最终保留项

（待复核后填写）
