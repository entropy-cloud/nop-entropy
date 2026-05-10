# Nop-Code 模块审计报告

**日期**：2026-05-10
**审计范围**：nop-code 全模块（12 个子模块）
**审计方法**：基于 `ai-dev/skills/nop-code-audit-prompt.md` 的 8 维度清单，5 个并行 explore agent + 直接代码审查

---

## 审计摘要

| 级别 | 数量 | 说明 |
|------|------|------|
| P0 架构断裂 | 6 | 影响正确性和可用性的严重问题 |
| P1 代码质量 | 5 | 影响可维护性和规范合规的问题 |
| P2 能力缺口 | 5 | 影响功能完整性的缺失 |
| P3 架构演进 | 4 | 影响长期可扩展性的改进 |

---

## P0 架构断裂

### P0-1：nop-code-api 模块为空壳

**问题描述**：`nop-code-api` 目录下没有任何 Java 源码（无 `src/` 目录），无法作为独立的 API 契约层使用。外部模块要使用 nop-code 接口时，被迫依赖整个 service 模块。

**具体证据**：`/nop-code/nop-code-api/` — 无 `src/` 目录

**影响分析**：模块间耦合度高，任何依赖方都拉入完整 service + dao 实现。违反 Nop 平台 api 模块独立原则。

**改进建议**：将 `ICodeIndexService` 接口和所有 DTO 类（`SymbolDTO`, `FileOutlineDTO`, `DepGraphDTO` 等）从 `nop-code-service` 迁移到 `nop-code-api`。

---

### ~~P0-2~~：core 层使用 Jakarta 标准注解（非问题）

**问题描述**：core 层使用了 `jakarta.inject.Inject` 和 `jakarta.annotation.Nonnull`。

**结论**：Jakarta 注解是 Java 标准规范（JSR-330 / JSR-250），Nop 平台 IoC 容器本身基于 Jakarta 标准。core 层使用标准注解不违反框架无关原则。**此项降级为非问题，从审计中移除。**

---

### ~~P0-3~~：BizModel 公开方法直接暴露 core 层内部模型（非问题）

**问题描述**：`NopCodeFileBizModel` 的多个公开方法（`@BizQuery`/`@BizLoader`）直接返回 `CodeFileAnalysisResult`、`CodeSymbol` 等 core 层模型，未经过 DTO 转换。

**结论**：**BizModel 方法返回实体对象是 Nop 平台的标准做法，不需要返回 DTO。** Nop 平台的序列化机制：

1. `CrudBizModel.get()` 直接返回 `T extends IOrmEntity`（平台基类就是这样设计的）
2. `GraphQLExecutor.fetchSelections()` 根据客户端 GraphQL selection 逐字段获取值（`BeanPropertyFetcher` → `BeanTool.getProperty()`）
3. 客户端只能看到 xmeta 中定义的、且在 selection 中请求的字段
4. **不会暴露整个实体对象**——序列化引擎按 selection 过滤

因此 BizModel 返回实体对象 ≠ 暴露内部结构。API 契约由 xmeta（控制字段可见性/类型）和 GraphQL selection（控制客户端实际获取的字段）共同决定，而非返回值的 Java 类型。

此项降级为非问题，从审计中移除。

---

### P0-4：SOURCE_CODE 和 IMPORTS 列定义但从未写入

**问题描述**：`nop_code_file` 表的 `SOURCE_CODE` 和 `IMPORTS` 列在 ORM 模型中定义，但 `saveFileResultInSession()` 从未写入这两个字段。

**具体证据**：
- `CodeIndexService.java:144` — 注释 `// sourceCode not stored in DB`
- `CodeIndexService.java:1460-1477` — `saveFileResultInSession()` 未调用 `fileEntity.setSourceCode()` 或 `fileEntity.setImports()`
- `nop-code.orm.xml:148-151` — 列定义存在

**影响分析**：`getFileSourceCode()` 永远返回 null。依赖图构建无法从 DB 读取 imports 数据。设计文档声称的能力与实际不符。

**改进建议**：在 `saveFileResultInSession()` 中添加 `fileEntity.setSourceCode()` 和 `fileEntity.setImports(JsonTool.stringify(file.getImports()))`。注意 SOURCE_CODE 列 domain 为 `jsonContent`，需改为纯文本类型。

---

### P0-5：增量索引指纹仅存内存，重启丢失

**问题描述**：`CodeIndexService` 默认使用 `InMemoryFingerprintStore`，应用重启后指纹数据全部丢失，必须全量重建索引。

**具体证据**：
- `CodeIndexService.java:77` — `protected IFingerprintStore fingerprintStore = new InMemoryFingerprintStore();`

**影响分析**：虽然已有 DB 级别的 `batchSaveFileRecords()`/`batchLoadFileRecords()` 方法，但默认实现仍用内存存储。生产环境需要手动配置 DB 存储。

**改进建议**：将默认实现改为 DB 存储（已有 `IFingerprintStore` 接口和 DB 方法，只需修改默认赋值）。

---

### P0-6：子模块测试覆盖不足

**问题描述**：`nop-code-service` 测试有限，`nop-code-dao`、`nop-code-web` 没有测试文件。

**不受影响**：
- `nop-code-api`：空壳模块，无代码可测
- `nop-code-app`：打包工程（仅含 `NopCodeApplication` 启动类），不需要测试
- `nop-code-codegen`：代码生成辅助功能，`NopCodeCodeGen` 是调试工具，不需要测试
- `nop-code-meta`：无 Java 源码，仅含 xmeta 资源文件和代码生成模板，不需要测试

**影响分析**：DAO 层的 ORM 映射无验证。service 层关键流程无测试保护。

**改进建议**：优先为 service 层添加测试。

---

### P0-7：Web 页面缺少关系图可视化

**问题描述**：调用链和类型层级页面使用 JSON 文本展示数据，无交互式图可视化。后端已有完整的图分析能力（社区检测、调用图、依赖图），但前端无法利用。

**具体证据**：`nop-code-web` 中 call-hierarchy 和 type-hierarchy 页面使用 `"type": "json"` 渲染。

**影响分析**：用户无法直观理解代码结构关系，降低了模块的实用性。

**改进建议**：引入 vis.js 或 Cytoscape.js 等图可视化库，为调用图、继承图、依赖图提供交互式可视化。

---

## P1 代码质量

### ~~P1-1~~：业务接口定义在 DAO 层（非问题）

**问题描述**：`INopCodeIndexBiz`、`INopCodeSymbolBiz` 等接口定义在 `nop-code-dao` 模块。

**结论**：这些接口是代码生成的，继承 `ICrudBiz<Entity>`，直接绑定 DAO 实体。它们是 DAO 层的 CRUD 契约，不是业务接口，放在 dao 模块是正确的——service 层 BizModel 实现这些接口，需要返回实体对象时可以直接返回。**此项降级为非问题，从审计中移除。**

---

### P1-2：Java 14+ 特性不支持且静默忽略

**问题描述**：`JavaFileAnalyzer` 不支持 Java Record、sealed class、pattern matching 等特性。分析结果中这些结构被忽略，无日志或标记。

**具体证据**：`JavaFileAnalyzer.java` — 无 `visit(RecordDeclaration)` 方法，无 `permits` 处理。

**改进建议**：添加对 Record/sealed class 的基本支持（至少提取类型名和字段），并添加日志记录不支持的特性。

---

### ~~P1-3~~：内存管理——分析结果全量驻留内存 → 应改为流式持久化

**问题描述**：`ProjectAnalyzer.analyzeProject()` 将所有文件的 `CodeFileAnalysisResult`（含 `sourceCode`）全部累积到 `List<CodeFileAnalysisResult> fileResults` 中，分析完成后才一次性传给 `persistInSession()` 持久化。对于大项目（1000+ 文件），所有文件的源码和分析结果同时驻留内存。

**具体证据**：
- `ProjectAnalyzer.java:124` — `List<CodeFileAnalysisResult> fileResults = new ArrayList<>();`
- `ProjectAnalyzer.java:138` — `fileResults.addAll(future.get());` 所有批次结果累积
- `ProjectAnalyzer.java:204` — `return new ProjectAnalysisResult(fileResults, ...)` 返回全量
- `CodeIndexService.java:208-214` — `indexDirectory()` 拿到完整 result 后才调用 `persistInSession()`

**理想行为**：每分析完一个批次就持久化到 DB 并释放内存，内存中最多只保留一个批次的数据。`ProjectAnalyzer` 应提供回调接口，让调用方（`CodeIndexService`）控制持久化时机。

**改进建议**：重构 `ProjectAnalyzer` 添加 `onBatchComplete` 回调机制，`CodeIndexService` 在回调中即时持久化，避免全量累积。

---

### P1-4：无内存边界检查

**问题描述**：`ProjectAnalyzer` 的批处理机制仅为并行度优化，无内存限制。超大项目可能导致 OOM。

**改进建议**：添加内存监控 + 文件大小/数量阈值，超限时降级为串行处理或拒绝。

---

### P1-5：类型系统仅有字符串表示

**问题描述**：`CodeSymbol.returnType` 和 `fieldType` 都是纯 String，泛型参数无法程序化解析。

**改进建议**：P3 级别，当前阶段可接受。如果未来需要类型感知分析（如查找 `List<String>` 的元素类型），需要引入结构化类型表示。

---

### P1-6：E2E 测试覆盖不足

**问题描述**：`nop-code-e2e` 模块存在但无活跃测试。`TestIndexNopEntropyProject` 被 `@Disabled` 标注（手动 30 秒测试）。

**改进建议**：添加关键流程的 E2E 测试（索引→搜索→层级查询→依赖图）。

---

## P2 能力缺口

### ~~P2-1~~：跨语言分析不支持（不需要）

**结论**：不需要跨语言分析能力。每种语言的分析器独立工作即可。

---

### P2-2：外部符号引用（JDK/第三方库）不可索引

**问题描述**：分析器只能索引项目内源码，JDK 类和第三方库的符号无法追踪。

**改进建议**：支持从 JAR/source jar 提取符号摘要，或利用 LSP 的 workspace/symbol 能力。

---

### P2-3：Python/TypeScript import 解析为 Phase A（基础版）

**问题描述**：`PythonImportResolver` 和 `TypeScriptImportResolver` 仅处理基本 import 模式。TypeScript 不支持 tsconfig paths、barrel files、re-exports。

**改进建议**：Phase B 添加 tsconfig paths 解析和 `index.ts`/`__init__.py` barrel file 支持。

---

### P2-4：依赖图持久化后未验证实际写入

**问题描述**：新增的 `nop_code_dependency` 表在 `saveFileResultInSession()` 中写入依赖，但缺少集成测试验证完整流程（索引→依赖写入→查询）。

**改进建议**：添加依赖图构建的端到端测试。

---

### P2-5：无并发安全测试

**问题描述**：`ProjectAnalyzer` 使用 `ExecutorService` 并行处理，但无并发安全测试验证。

**改进建议**：添加并发测试场景：多线程同时索引同一项目。

---

## P3 架构演进

### P3-1：结构化类型系统

**改进方向**：将 `returnType`/`fieldType` 从 String 升级为结构化类型对象，支持泛型参数解析。

### P3-2：SourceCodeProvider 抽象

**改进方向**：实现设计文档中的 `ISourceCodeProvider` 接口，支持从 DB/文件系统/远程存储读取源码。

### P3-3：图可视化前端

**改进方向**：引入图可视化库（vis.js/Cytoscape.js），为调用图、依赖图、继承图提供交互式前端。

### P3-4：性能基准

**改进方向**：添加 JMH 基准测试，测量不同项目规模（1K/10K/100K 文件）的索引性能和内存占用。

---

## 改进优先级路线图

| 优先级 | 任务 | 预估工时 |
|--------|------|----------|
| **立即** | P0-4: 修复 SOURCE_CODE/IMPORTS 列写入 | 0.5 天 |
| **立即** | P0-5: 增量索引改为 DB 存储 | 0.5 天 |
| **立即** | P1-5: 增加 rawReturnType/rawFieldType 字段（ORM+解析器+持久化） | 0.5 天 |
| **本周** | P1-3: ProjectAnalyzer 改为流式持久化（每批次即时写入 DB 并释放内存） | 1 天 |
| **下周** | P0-1: 迁移接口到 api 模块 | 1 天 |
| **下周** | P0-6/P1-6: 补充测试 | 2 天 |
| **后续** | P2/P3: 图可视化、性能基准 | 按需 |
