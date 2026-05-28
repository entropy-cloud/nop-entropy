# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] WindowAggregationOperator 缺少版权头

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:1-3`
- **严重程度**: P2
- **现状**: 直接以 package 声明开始，缺少标准版权头。对比同目录其他文件都有版权头。
- **建议**: 添加标准版权头。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度17-02] CepOperator import 顺序不规范

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:21-66`
- **严重程度**: P2
- **现状**: java.* 分组内字母序不一致（function.BiConsumer 插在 Collection 和 HashSet 之间）。
- **建议**: 按 ASCII 字母序重新排列 import。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度17-03] NFACompiler static import 混在普通 import 中

- **文件**: `nop-stream-cep/.../nfa/compiler/NFACompiler.java:34-50`
- **严重程度**: P2
- **现状**: static import 混在 io.nop.commons.* 和 io.nop.stream.* 之间。
- **建议**: 将 static import 移到所有普通 import 之后。
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度17-04] StreamOperator 注释语言混用（部分英文部分中文）

- **文件**: `nop-stream-core/.../operators/StreamOperator.java:119-146`
- **严重程度**: P2
- **现状**: snapshotState() 和 initializeState() 使用中文注释，同接口其他方法用英文。
- **建议**: 统一注释语言。
- **误报排除**: 排除 MemoryKeyedStateBackend 等内部实现类的中文注释——对国内团队有正面价值。
- **复核状态**: 未复核

## 已验证合规项

- 命名规范全部符合：类名 PascalCase, 方法名 camelCase, 常量 UPPER_SNAKE_CASE
- System.out 使用全部合理（demo 输出和 print sink 功能）
- 无未使用的 import
