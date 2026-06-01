# 开发计划：代码索引查询 API 扩展

> Plan Status: completed
**设计文档**：`ai-dev/design/nop-code/query-api-design.md`
**日期**：2026-05-09

---

## 现状核查（设计 vs 实际）

设计文档中部分 P0 功能已实现：

| 设计特性 | 已有实现 | 缺口 |
|----------|----------|------|
| P0-2.1 文件大纲 | `batchGetOutlines` BizQuery + `TypeOutlineDTO` + `FileOutlineDTO` | ✅ 已有基础，但缺少**单文件按 filePath 查大纲**的入口 |
| P0-2.2 模块摘要 | 无 | ❌ 缺：按目录汇总公共符号 |
| P0-2.3 符号源码定位 | `sourceCode` BizLoader + `getSymbolSourceCode()` | ✅ 已有，但 BizLoader 绑定到 SymbolDTO 上下文，无独立 Query |
| P0-2.4 implements 查询 | `getTypeHierarchy` BizQuery（direction=sub, maxDepth） | ✅ 已有完整实现 |
| P0-2.5 反向引用 | `usages` BizLoader（按 symbolId 查引用） | ⚠️ 有正向引用查询，但反向引用=按被引用符号查谁引用它，目前是通过 symbolId 查的，不是按 qualifiedName |
| P1-2.6 AST 模式搜索 | 无 | ❌ 缺 |
| P1-2.7 依赖图 | 无 | ❌ 缺 |

**结论**：P0 中只有 2 个真正需要新增的功能（模块摘要 + 按文件路径查大纲），其余是已有功能的补充入口或验证。计划重心应放在 P1（AST 模式搜索 + 依赖图）。

---

## Phase 1：P0 补全（预估 1-2 天）

### Step 1.1：单文件大纲 Query

**目标**：新增 `NopCodeSymbolBizModel` 的 `fileOutline` BizQuery

**改动**：
- `NopCodeSymbolBizModel.java`：新增 `fileOutline(@Name("indexId"), @Name("filePath"))` 方法
- `ICodeIndexService.java`：新增 `getFileOutline(String indexId, String filePath)` 方法签名
- `CodeIndexServiceImpl.java`：实现该方法，调用已有的 `getFileSymbols()` + 按 parentId 组装嵌套树
- 返回已有的 `FileOutlineDTO`

**验收**：`TestCodeIndexService` 或新测试中调用 `fileOutline`，验证返回正确的符号树

### Step 1.2：模块摘要 Query

**目标**：新增 `moduleDigest` BizQuery，按目录汇总所有文件的公共符号

**改动**：
- `NopCodeSymbolBizModel.java`：新增 `moduleDigest(@Name("indexId"), @Name("dirPath"), @Name("includePrivate"))` 方法
- `ICodeIndexService.java`：新增 `getModuleDigest()` 方法签名
- `CodeIndexServiceImpl.java`：实现：按 filePath LIKE `dirPath%` 查询 nop_code_file + nop_code_symbol，过滤 PUBLIC 符号
- 新增 `ModuleDigestDTO` 数据类（或复用 `FileOutlineDTO` 列表）

**验收**：新测试验证 `moduleDigest("test", "nop-code/nop-code-core/src/main/java/io/nop/code/core")` 返回正确的公共符号汇总

### Step 1.3：符号源码独立 Query（补充入口）

**目标**：补充独立的 `showSymbol` BizQuery（不依赖 BizLoader 上下文）

**改动**：
- `NopCodeSymbolBizModel.java`：新增 `showSymbol(@Name("indexId"), @Name("qualifiedName"), @Name("includeBody"))` 方法
- 内部调用已有的 `findSymbolByQualifiedName()` + `getSymbolSourceCode()`
- 返回已有的 DTO 或新增 `SymbolSourceDTO`

**验收**：新测试验证独立查询

---

## Phase 2：P1 核心新能力（预估 5-8 天）

### Step 2.1：依赖图数据模型

**目标**：新增 `CodeFileDependency` 模型 + `nop_code_dependency` ORM 表

**注意**：`nop_code_file.IMPORTS` 列虽存在于 ORM 模型，但当前 `saveFileResultInSession()` 未写入该列。Step 2.1 需要在持久化依赖关系时一并修复此问题（或直接在新表中存储依赖，不依赖 IMPORTS 列）。

**改动**：
- `nop-code/nop-code-core/`：新增 `CodeFileDependency` 数据类
- `nop-code/model/nop-code.orm.xml`：新增 `nop_code_dependency` 表定义（INDEX_ID, SOURCE_FILE_PATH, TARGET_FILE_PATH, IMPORT_STATEMENT, RESOLVED）
- 运行 `mvn install` 让 DAO 层自动生成实体类
- `CodeIndexServiceImpl.java`：在 `saveFileResultInSession()` 中，从 `imports` JSON 解析并持久化依赖关系

### Step 2.2：import → 文件路径解析

**目标**：实现按语言规则的 import 路径解析

**改动**：
- `nop-code/nop-code-core/`：新增 `IImportResolver` 接口 + `JavaImportResolver` / `PythonImportResolver` / `TypeScriptImportResolver` 实现
- 每个 resolver 接收项目文件列表 + raw import → 解析为 (targetFilePath, resolved) 对
- 在 `ProjectAnalyzer.analyzeProject()` 或 `CodeIndexServiceImpl` 中调用

### Step 2.3：依赖图查询 API

**目标**：暴露 GraphQL 查询

**改动**：
- `NopCodeIndexBizModel.java` 或 `NopCodeSymbolBizModel.java`：新增 `deps()` / `reverseDeps()` / `findCycles()` / `depGraph()` BizQuery
- `ICodeIndexService.java`：新增对应方法签名
- `CodeIndexServiceImpl.java`：实现依赖图查询 + Tarjan SCC 循环检测
- 新增 `DepGraphDTO` / `DepNodeDTO` / `DepEdgeDTO`

**验收**：集成测试验证依赖图构建 + 循环检测

### Step 2.4：结构感知搜索（Phase A）

**目标**：基于已有索引的结构化搜索

**改动**：
- `NopCodeSymbolBizModel.java`：新增 `searchCode(@Name("indexId"), @Name("query"), @Name("searchType"), @Name("language"), @Name("limit"))` BizQuery
- `ICodeIndexService.java`：新增 `searchCode()` 方法签名
- `CodeIndexServiceImpl.java`：实现 SYMBOL_NAME（模糊匹配 symbol name）+ FULL_TEXT（源码全文搜索）+ COMBINED 模式
- 新增 `SearchResultDTO`

**验收**：测试验证搜索功能

**实现说明**：
- SYMBOL_NAME：基于已有 `findSymbols()` 的模糊匹配
- FULL_TEXT：从源码文件全文搜索（`getSourceCode()` 后 in-memory 匹配，后续可优化为 SQL LIKE）
- COMBINED：先 SYMBOL_NAME 再 FULL_TEXT 补充，合并去重

---

## Phase 3：验证（预估 0.5 天）

### Step 3.1：全量 mvn test

### Step 3.2：提交

---

## 执行策略

- Phase 1 三个 Step 互相独立，可并行委派
- Phase 2 的 Step 2.1 是 2.2/2.3 的前置条件，必须先完成
- Phase 2 的 Step 2.4 与 2.1-2.3 独立，可并行
- 每个 Step 完成后立即验证 + 提交
