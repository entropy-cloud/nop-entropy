# 审核维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] import 分组顺序违反约定：io.nop.* 排在 java.* 之前

- **文件**: 9 个文件（CodeIndexService、CodeGraphService、CodeQueryService 等）
- **严重程度**: P2
- **现状**: 约定为 java.* → jakarta.* → third-party → io.nop.*，但几乎所有文件将 io.nop.* 放在前面。
- **建议**: 重新排列 import 顺序。
- **复核状态**: 未复核

### [维度17-02] 单行包含两个 import 语句

- **文件**: `CodeIndexService.java:80`
- **证据**: `import java.util.concurrent.ConcurrentHashMap;import java.util.concurrent.locks.ReentrantLock;`
- **严重程度**: P3
- **复核状态**: 未复核

### [维度17-03] 通配符 import 与同名包的具体 import 混用

- **文件**: CodeGraphService.java、CodeIndexService.java 等
- **严重程度**: P3
- **复核状态**: 未复核
