# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] 编译管线术语不一致："四层编译" vs "五层执行管线"

- **文件**: `component-roadmap.md:28` vs `architecture.md:16`
- **严重程度**: P2
- **现状**: 两个文档使用不同的层数和术语描述同一管线。

### [维度19-02] connector 异常构造不一致：部分用 ErrorCode，部分用裸字符串

- **文件**: `connector/BatchLoaderSourceFunction.java:43` 等
- **严重程度**: P2
- **现状**: core 用 NopStreamErrors ErrorCode，connector 用裸字符串。共约 10 处不一致。

### [维度19-03] CEP 错误码前缀独立：nop.err.cep.* vs nop.err.stream.*

- **文件**: `NopCepErrors.java` vs `NopStreamErrors.java`
- **严重程度**: P3
- **现状**: CEP 使用独立前缀，可能是有意设计但缺少文档说明。

### [维度19-04] StreamComponents.getBean() 实际只搜索 windowingStrategies

- **文件**: `StreamComponents.java:136-142`
- **严重程度**: P3
- **现状**: 方法名暗示全局搜索，实际只查一个 map。
