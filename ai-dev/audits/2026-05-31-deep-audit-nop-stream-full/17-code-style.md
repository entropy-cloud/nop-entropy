# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] 通配符 import 泛滥（21 个主代码文件）

- **文件**: 21 个主代码文件使用 `import java.util.*;` 或 `import java.io.*;`
- **证据片段**:
  ```java
  // WindowAggregationOperator.java:3
  import java.util.*;
  // SimpleTypeSerializer.java:10
  import java.io.*;
  ```
- **严重程度**: P3
- **现状**: 21 个主代码文件使用通配符 import，降低可读性。
- **风险**: 影响代码可读性和 IDE 自动导入维护。
- **建议**: 逐步替换为显式 import。
- **信心水平**: 确定
- **误报排除**: 不是误报，但影响有限。
- **复核状态**: 未复核

### [维度17-02] static import 与常规 import 交叉混排（8+ 个文件）

- **文件**: `WindowOperator.java:25-35`, `TaskExecutor.java:26-29`, `TaskManager.java:30-31` 等
- **证据片段**:
  ```java
  // WindowOperator.java:25-35
  import io.nop.api.core.annotations.core.Internal;
  import static io.nop.api.core.util.Guard.checkArgument;   // static 夹在常规 import 之间
  import io.nop.commons.tuple.Tuple2;
  import static io.nop.stream.core.exceptions.NopStreamErrors.*;  // static 再次夹在中间
  ```
- **严重程度**: P3
- **现状**: static import 应置于所有常规 import 之后，但多处交叉混排。
- **建议**: 统一 import 分组格式。
- **信心水平**: 确定
- **误报排除**: 不是误报但影响有限。
- **复核状态**: 未复核

## 合规确认

- 类名 PascalCase、方法名 camelCase、常量 UPPER_SNAKE_CASE 全部合规
- 包名 io.nop.stream.* 全部合规
- System.out 仅出现在 PrintSink（功能需求）和 Demo 代码中
- 未发现 e.printStackTrace()
- 未发现未使用的 import
