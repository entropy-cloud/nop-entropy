# 维度 02：模块职责与文件边界 — nop-code 模块

## 第 1 轮（初审）

### [维度02-01] CodeIndexService 是 God Object（1573 行，7 个关注点混合）

- **文件**: `nop-code-service/.../impl/CodeIndexService.java`（1573 行）
- **证据片段**: ICodeIndexService 接口定义 30+ 方法，横跨 7 个功能域。类直接 import 3 个具体语言适配器并硬编码注册。
- **严重程度**: P1
- **现状**: 核心索引 + 持久化 + 增量索引 + 流程持久化 + 批量记录 + 转换 + 管理逻辑全在一个类中。语言适配器硬编码注册破坏了注册表模式的扩展性。
- **风险**: 可维护性差，新增语言需修改此文件。
- **建议**: 拆分持久化、增量索引、流程相关逻辑到独立类。语言适配器通过 IoC 注入。
- **信心水平**: 确定
- **误报排除**: 1573 行 7 个关注点，已超出合理的单一职责范围。
- **复核状态**: 未复核

### [维度02-02] _NopCodeDaoConstants.java 手写 226 行常量且从未被引用

- **文件**: `nop-code-dao/.../dao/_NopCodeDaoConstants.java`（226 行）
- **证据片段**: 文件使用 `_` 生成前缀但包含完全手写的常量（SYMBOL_KIND_CLASS 等），且整个代码库中从未被引用。
- **严重程度**: P2
- **现状**: 违反 Nop 平台 `_` 前缀文件约定。常量未被使用。
- **风险**: mvn install 时会被覆盖。无用代码增加维护负担。
- **建议**: 删除手写内容或移到别处，让文件回归空壳。
- **信心水平**: 确定
- **误报排除**: 部分常量可能已存在于 core 模块的枚举中。
- **复核状态**: 未复核

### [维度02-03] entityToCodeSymbol 转换方法在 3 个文件中重复

- **文件**: `CodeIndexService.java:209`, `CodeQueryService.java:35`, `CodeGraphService.java:304`
- **证据片段**: 约 30 行/份的 entity-to-model 转换代码在 3 处完全相同。
- **严重程度**: P2
- **现状**: DRY 违反，模型变更需同步 3 处。
- **建议**: 提取到共享 Converter 类。
- **信心水平**: 确定
- **误报排除**: 三份代码完全相同，明显是复制粘贴。
- **复核状态**: 未复核

### [维度02-04] CodeIndexService 硬编码注册语言适配器，绕过 IoC

- **文件**: `CodeIndexService.java:167-200`
- **证据片段**:
  ```java
  this.registry.registerAdapter(new JavaLanguageAdapter());
  this.registry.registerAdapter(new PythonLanguageAdapter());
  this.registry.registerAdapter(new TypeScriptLanguageAdapter());
  ```
- **严重程度**: P2
- **现状**: 注册表模式设计初衷是支持插件式注册，但被硬编码绕过。nop-code-service 直接依赖所有 lang-* 模块。
- **风险**: 添加新语言必须修改 CodeIndexService。
- **建议**: 通过 IoC 容器注入 List<ILanguageAdapter>。
- **信心水平**: 确定
- **误报排除**: _lang-*.beans.xml 已定义 bean 但未被加载（见维度08-01），说明 IoC 注册路径已设计但未使用。
- **复核状态**: 未复核

## 正面发现

- 生成文件边界干净，无手写修改痕迹
- 模块依赖方向正确
- lang-* 模块职责单一
- 测试文件未混入 main
