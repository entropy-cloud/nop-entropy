# 维度20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] ICodeIndexService 和 DTOs 放在 nop-code-service 而非 nop-code-api

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`
- **行号**: 全文件 178 行
- **证据片段**:
  ```java
  package io.nop.code.service.api;
  public interface ICodeIndexService { ... }
  ```
- **严重程度**: P2
- **现状**: ICodeIndexService 和 25+ 个 DTO 在 nop-code-service 中，nop-code-api 完全空壳。外部模块想依赖 nop-code 接口必须引入整个 service（包括 graph、flow、lang-* 等全部依赖）。
- **风险**: 违反 Nop 标准分层约定。跨模块依赖成本过高。
- **建议**: 将 ICodeIndexService 和 DTOs 迁移到 nop-code-api。
- **信心水平**: 确定
- **误报排除**: WIP 模块可能暂未执行此迁移。但 api 模块和 pom.xml 已存在。
- **复核状态**: 未复核

### [维度20-02] CodeIndexService 硬编码依赖语言适配器实现类

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L162-167
- **证据片段**:
  ```java
  this.registry.registerAdapter(new JavaLanguageAdapter());
  this.registry.registerAdapter(new PythonLanguageAdapter());
  this.registry.registerAdapter(new TypeScriptLanguageAdapter());
  ```
- **严重程度**: P3
- **现状**: 无参构造函数硬编码三个语言适配器。添加新语言需修改 CodeIndexService。
- **建议**: 通过 IoC 容器注入 LanguageAdapterRegistry。
- **信心水平**: 很可能
- **误报排除**: 有参构造函数已提供灵活性，硬编码仅在无参构造函数中。
- **复核状态**: 未复核

## 通过项

1. nop-search-api optional 依赖使用正确（@Nullable 注入 ISearchEngine）
2. 无跨模块事件依赖
