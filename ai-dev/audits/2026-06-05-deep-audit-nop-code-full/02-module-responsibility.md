# 维度 02：模块职责与文件边界 — nop-code 模块

## 第 1 轮（初审）

### [维度02-01] CodeIndexService 是 1904 行的"上帝类"，混合 8+ 职责域

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1-1904`
- **证据片段**: 单个类混合索引编排、文件查询、符号查询、图分析委托、流分析、ORM 持久化（saveFileResultInSession ~200行）、实体转换、增量索引、搜索、导入解析等职责。
- **严重程度**: P2
- **现状**: CodeIndexService 是 1904 行的门面类，包含大量直接持久化逻辑和实体转换方法。
- **风险**: 维护成本随代码增长持续增加。任何修改可能意外破坏不相关功能。
- **建议**: 将 ORM 持久化提取到 `CodePersistenceService`，将实体转换移到 `CodeSymbolConverter` 和新转换工具类。CodeIndexService 应纯粹作为协调器。
- **信心水平**: 确定
- **误报排除**: 文件行数已验证。六个不同职责域通过检查完整文件内容确认。
- **复核状态**: 未复核

### [维度02-02] 重复的 entityToCodeSymbol — CodeIndexService 死代码重写了 CodeSymbolConverter

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:231-258`
- **证据片段**: 私有 `entityToCodeSymbol` 方法与 `CodeSymbolConverter.toCodeSymbol()` 完全相同，但在 CodeIndexService 中从未被调用。
- **严重程度**: P2
- **现状**: 死代码副本。所有调用点使用 `CodeSymbolConverter::toCodeSymbol`。
- **风险**: 如果有人添加字段只更新了 CodeSymbolConverter，死代码副本会静默过时。
- **建议**: 删除 231-258 行的死代码方法。
- **信心水平**: 确定
- **误报排除**: grep 验证 `entityToCodeSymbol` 仅在 CodeIndexService 中定义，从未被调用。
- **复核状态**: 未复核

### [维度02-03] 重复的 entityToInheritance 方法在 CodeIndexService 和 CodeGraphService 中

- **文件**: `CodeIndexService.java:260-268` 及 `CodeGraphService.java:353-361`
- **证据片段**: 两个完全相同的 `entityToInheritance` 私有方法。
- **严重程度**: P2
- **现状**: 相同逻辑在两个文件中重复。
- **风险**: 转换逻辑变更可能只更新一个副本。
- **建议**: 移到共享转换器工具类。
- **信心水平**: 确定
- **误报排除**: grep 验证两个文件中方法体相同。
- **复核状态**: 未复核

### [维度02-04] 重复的 extractLines 工具方法

- **文件**: `CodeIndexService.java:1850-1862` 及 `CodeQueryService.java:68-80`
- **严重程度**: P3
- **现状**: 相同的 `extractLines` 方法在两个文件中重复。
- **建议**: 提取到共享工具类 `CodeTextUtils.extractLines`。
- **信心水平**: 确定
- **误报排除**: grep 验证两处匹配且方法体相同。
- **复核状态**: 未复核

### [维度02-05] 硬编码的 ImportResolver 实例化违反开放/封闭原则

- **文件**: `CodeIndexService.java:209-218`
- **证据片段**: `registerImportResolvers()` 使用 `new` 直接实例化三个语言特定的 ImportResolver，与同类的 ILanguageAdapter IoC 发现模式不一致。
- **严重程度**: P2
- **现状**: 添加新语言模块需要修改 CodeIndexService，违反开放/封闭原则。
- **建议**: 通过 `BeanContainer.instance().getBeansOfType(IImportResolver.class)` 发现实现。
- **信心水平**: 很可能
- **误报排除**: `new JavaImportResolver()` 在第 211 行确认，第 177 行的 `getBeansOfType(ILanguageAdapter.class)` 确认模式不一致。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 02-01 | P2 | CodeIndexService.java | 1904行上帝类混合8+职责域 |
| 02-02 | P2 | CodeIndexService.java:231 | 死代码重复 entityToCodeSymbol |
| 02-03 | P2 | CodeIndexService+CodeGraphService | 重复 entityToInheritance |
| 02-04 | P3 | CodeIndexService+CodeQueryService | 重复 extractLines |
| 02-05 | P2 | CodeIndexService.java:209 | 硬编码 ImportResolver 实例化 |
