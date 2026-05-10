# 开发计划：Symbol 类型字段扩展 + P0-4/P0-5/P1-3 修复

**关联审计报告**：`ai-dev/audits/nop-code-audit-2026-05-10.md`
**日期**：2026-05-10

---

## 改动范围

### 1. Symbol 类型字段扩展（P1-5 rawType）

**目标**：在 `CodeSymbol` 模型和 `nop_code_symbol` 表中增加 `rawReturnType` / `rawFieldType` 字段，存储去掉泛型参数的原始类型名。现有 `returnType` / `fieldType` 保持完整泛型文本不变。

**设计决策**：
- `returnType` / `fieldType`：完整泛型文本，如 `"List<String>"`（保持不变）
- `rawReturnType` / `rawFieldType`：原始类型名，如 `"List"`（新增）
- 需要泛型结构化分析时，用平台已有的 `GenericTypeParser.parseFromText(returnType)` 按需解析
- 不存储 `IGenericType` 结构化对象，避免 DB 复杂度

**涉及文件**：

#### 1.1 ORM 模型（nop-code/model/）
- `nop-code.orm.xml` — `nop_code_symbol` 表增加两列：
  - `RAW_RETURN_TYPE` domain="qualifiedName" propId=28
  - `RAW_FIELD_TYPE` domain="qualifiedName" propId=29

#### 1.2 Core 模型（nop-code-core）
- `CodeSymbol.java` — 增加 `rawReturnType` / `rawFieldType` 字段 + getter/setter

#### 1.3 代码解析器（nop-code-lang-*）
- `JavaFileAnalyzer.java` — 提取 rawType：从 `decl.getType().asString()` 中提取原始类型名
  - 例如 `"List<String>"` → rawType=`"List"`
  - 简单实现：取第一个 `<` 之前的部分；无 `<` 则与 returnType 相同
- `TypeScriptCodeFileAnalyzer.java` — 同上
- `PythonCodeFileAnalyzer.java` — 同上

#### 1.4 持久化层（nop-code-service / nop-code-dao）
- `CodeIndexService.saveFileResultInSession()` — 写入 `symEntity.setRawReturnType()` / `symEntity.setRawFieldType()`
- `CodeIndexService.entityToSymbol()` — 读回时填充 rawType 字段
- `_NopCodeSymbol.java` — 由 codegen 重新生成（修改 orm.xml 后 mvn install）

#### 1.5 DTO 层（nop-code-service）
- `SymbolDTO.java` — 增加 `rawReturnType` / `rawFieldType` 字段

#### 1.6 xmeta（nop-code-meta）
- `_NopCodeSymbol.xmeta` — 增加 `rawReturnType` / `rawFieldType` prop 定义（codegen 生成）

---

### 2. P0-4：修复 SOURCE_CODE / IMPORTS 列写入

**目标**：在 `saveFileResultInSession()` 中写入 `SOURCE_CODE` 和 `IMPORTS` 数据。

**涉及文件**：
- `nop-code.orm.xml` — `SOURCE_CODE` 列 domain 从 `jsonContent` 改为纯文本类型（`VARCHAR` 或 `CLOB`）
- `CodeIndexService.saveFileResultInSession()` — 添加：
  ```java
  fileEntity.setSourceCode(file.getSourceCode());
  fileEntity.setImports(JsonTool.stringify(file.getImports()));
  ```

---

### 3. P0-5：增量索引指纹改 DB 存储

**目标**：将默认指纹存储从 `InMemoryFingerprintStore` 改为 DB 存储。

**涉及文件**：
- `CodeIndexService.java` — 修改默认 `fingerprintStore` 实现为 DB 级别
  - 已有 `batchSaveFileRecords()` / `batchLoadFileRecords()` 方法
  - 需要实现 `IFingerprintStore` 的 DB 版本（或复用已有 DB 方法）

---

### 4. P1-3：ProjectAnalyzer 改为流式持久化

**目标**：每分析完一个批次即持久化到 DB 并释放内存，内存中最多保留一个批次数据。

**涉及文件**：
- `IProjectAnalyzer.java` — 增加回调接口定义
- `ProjectAnalyzer.java` — 重构 `analyzeProject()` 支持批次回调
- `CodeIndexService.indexDirectory()` — 在回调中即时调用 `persistInSession` 的单批次版本

---

## 执行顺序

| Step | 内容 | 依赖 | 预估 |
|------|------|------|------|
| 1 | P0-4: SOURCE_CODE/IMPORTS 写入 | 无 | 0.5 天 |
| 2 | P0-5: 指纹改 DB 存储 | 无 | 0.5 天 |
| 3 | P1-5: rawType 字段（ORM + core 模型 + 解析器 + 持久化） | 无 | 0.5 天 |
| 4 | P1-3: 流式持久化 | Step 1 | 1 天 |
| 5 | mvn install + 全量测试验证 | Step 1-4 | 0.5 天 |

Step 1/2/3 互相独立，可并行执行。
