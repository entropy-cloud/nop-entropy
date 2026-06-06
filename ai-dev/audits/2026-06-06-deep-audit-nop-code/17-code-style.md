# 维度 17+18+19+20：代码风格/文档/命名/跨模块

## 维度 17：代码风格与规范

### [维度17-01] CodeGraphService import 分组违反约定

- **文件**: `CodeGraphService.java:3-36`
- **严重程度**: P2
- **现状**: org.slf4j.* 放在 io.nop.* 之后。应为 java.* → third-party → io.nop.*。
- **复核状态**: 未复核

### [维度17-02] CodeIndexService 两个 import 挤在同一行

- **文件**: `CodeIndexService.java:6`
- **严重程度**: P2
- **证据**: `import java.util.concurrent.ConcurrentHashMap;import java.util.concurrent.locks.ReentrantLock;`
- **复核状态**: 未复核

### [维度17-03] CodeIndexService 重复注释块和空注释段

- **严重程度**: P2
- **现状**: 多处空分隔注释和完全重复的注释块。
- **复核状态**: 未复核

### [维度17-04] entityToCodeSymbol 逻辑与 CodeSymbolConverter.toCodeSymbol 完全重复

- **文件**: `CodeIndexService.java:256-283` vs `CodeSymbolConverter.java:7-34`
- **严重程度**: P2
- **现状**: 完全相同的转换逻辑存在于两个文件。
- **建议**: 删除 CodeIndexService 中的重复方法。
- **复核状态**: 未复核

### [维度17-05] NopCodeException/Constants/Configs 死代码

- **严重程度**: P2
- **现状**: 已定义但从未使用（NopCodeException）或为空壳接口（Constants/Configs）。
- **复核状态**: 未复核

### [维度17-06] saveFileResultInSession 约300行应拆分

- **严重程度**: P2
- **现状**: 单方法依次处理文件/符号/调用/继承/注解/路由/用法/依赖持久化。
- **建议**: 按实体类型拆分为子方法。
- **复核状态**: 未复核

## 维度 18：文档-代码一致性

### [维度18-01] docs-for-ai/ 中无 nop-code 模块使用文档

- **严重程度**: P2
- **现状**: 仅 module-groups.md 一行描述。缺少 API 清单、BizModel 操作清单、实体关系图等。
- **建议**: 创建 docs-for-ai/03-modules/nop-code.md 初步文档。
- **复核状态**: 未复核

## 维度 19：命名与术语一致性

### [维度19-01] ExecutionFlow 与 NopCodeFlow 字段名不一致

- **严重程度**: P2
- **现状**: criticality vs overallScore, entryPointSymbolId vs entryPointId。
- **复核状态**: 未复核

### [维度19-02] SymbolDTO 等3个 DTO 放在 service 而非 api

- **严重程度**: P2（同维度02-04）
- **复核状态**: 未复核

## 维度 20：跨模块契约一致性

### [维度20-01] ICodeIndexService 放在 service 而非 api

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`
- **严重程度**: P2
- **现状**: 37个方法的服务接口放在 service 模块，其他模块需依赖整个 service 实现。
- **建议**: 迁移到 nop-code-api 模块。
- **复核状态**: 未复核

### [维度20-02] ISearchEngine 可选降级缺少文档

- **严重程度**: P3
- **现状**: 搜索引擎不可用时退化为 DB 搜索，但接口 JavaDoc 未说明。
- **复核状态**: 未复核
