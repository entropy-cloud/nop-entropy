# format-misc 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-format/{nop-converter,nop-mermaid,nop-markdown-ext}
- 文件数: 约 105（src/main/java）
- 覆盖范围声明:
  - **nop-converter（36 手写文件，另 3 个 `_gen` 跳过）**: 全部手写文件逐一精读，含注册表配置 `_vfs/nop/converter/registry/default.convert.xml` 与 `converter-defaults.beans.xml`（用于可达性判定）。
  - **nop-mermaid（27 手写文件）**: `output/MermaidGenerator`、`parse/MermaidParseHelper`、`parse/MermaidASTBuildVisitor`、`parse/MermaidASTParser`、`parse/MermaidParseTreeParser`、`ast/` 下全部手写类精读；`_gen/` 17 个与 `_MermaidASTBuildVisitor` 跳过；`parse/antlr/` 6 个 ANTLR 生成物只做 token/literal 级核对（与 `model/antlr/Mermaid.g4`、`BaseRules.g4` 交叉验证），未逐行审。
  - **nop-markdown-ext（7 文件）**: 全部精读。
  - 外部依赖行为做实证/溯源而非臆断: `MessageFormat`（本地 JDK 实测）、`ReportEngine`/`XlsxReportRendererFactory`（读源码确认返回 `IBinaryTemplateOutput`）、`CodeBuilder`（读源码确认 `line(String)` 走 `MessageFormat.format`）、`ComponentModelConfig`/`RegisterModelDiscovery`（确认 xdsl-loader 的 saver/dslNodeLoader 装配）。
  - 不在范围: 测试代码、target/、`_` 前缀生成文件、nop-ooxml/nop-markdown/nop-report 等被依赖模块的实现。
  - 关键交叉验证: mermaid 模块在仓库内无任何生产消费方（独立库）；converter 的消费方 `DslToolImpl`（nop-ai-coder）、`DocConvertHelper.mergeAndConvertResources`（CLI 生成链路）均已定位。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 7 |
| P2 | 3 |
| P3 | 6 |

## 发现列表

---

### [P1] getToFileTypes(allowChained=true) 对双向转换对无限递归导致 StackOverflowError

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/DocumentConverterManager.java:59-81`
- **维度**: D1
- **证据**:
```java
for (String directType : directTypes) {
    // 避免循环依赖导致的无限递归
    if (!directType.equals(fromFileType)) {
        Set<String> indirectTypes = getToFileTypes(directType, true);
        allTypes.addAll(indirectTypes);
    }
}
```
- **现状**: 递归仅排除了 `directType == fromFileType` 的一步自环，无法防御两步环。默认注册表 `default.convert.xml` 明确注册了双向对：`json→json5` 与 `json5→json`（第 42-46 行）、`xlsx→workbook.xml` 与 `workbook.xml→xlsx`（第 15-19 行）、`json↔yaml`。
- **风险**: `getToFileTypes("json", true)` → `getToFileTypes("json5", true)` → direct 集含 `json`（≠json5）→ `getToFileTypes("json", true)` → 无限互递归 → StackOverflowError。在默认注册表下，任何参与双向对的类型调用该 API（`IDocumentConverterManager` 的公开接口方法，专供查询链式可达类型）必然崩溃。
- **建议**: 增加已访问类型集合（visited set）做记忆化或环路剪枝；或改为对转换图做 BFS。
- **误报排除**: 已确认仓库内调用方 `ConvertModelBuilder.java:20` 只用 `allowChained=false`，因此当前仓库内部不会触发——定级 P1 而非 P0；但该参数组合是公开 API 契约的一部分，且默认注册表就含双向对，外部任何调用即崩。

---

### [P1] ExcelDocumentConverter.convertToText 对 xlsx 目标类型强转 ITextTemplateOutput 抛 ClassCastException

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/ExcelDocumentConverter.java:61`（同型问题 `ExcelDocHelper.java:23`）
- **维度**: D1/D4
- **证据**:
```java
ITextTemplateOutput renderer = (ITextTemplateOutput) ExcelDocHelper.getExcelRenderer(doc, renderType);
return renderer.generateText(newEvalScope(options));
```
- **现状**: 注册表 `default.convert.xml:18-19` 注册了 `workbook.xml→xlsx`（ExcelDocumentConverter）。`convertToText` 中 renderType=`"xlsx"` 不属于 xml/html/shtml/md，落入第 61 行；`getRendererForExcel(wk,"xlsx")` 经 `report-defaults.beans.xml`（`nopReportRenderer_xlsx`）返回 `XlsxReportRendererFactory.buildRenderer(...)`，其返回类型为 `IBinaryTemplateOutput`（`XlsxReportRendererFactory.java:19-22`），不实现 `ITextTemplateOutput`。
- **风险**: `DocumentConverterManager.convertText(path, text, "workbook.xml", "xlsx", options)` 必抛 ClassCastException（无业务错误信息）。该路径有现实触发方：`nop-ai-coder` 的 `DslToolImpl.convertText` 以任意注册类型对调用。另外 `ChainedDocumentConverter.convertToText:33` 对中间类型也走 `convertToText`，链式 `workbook.xml→md`（经中间类型 xlsx，`mergeAndConvertResources` 默认 allowChained）同样命中。
- **建议**: 转换前 `instanceof ITextTemplateOutput` 判断，不支持时抛 `NopException`（带 from/to 参数的错误码），或在注册表中将 `workbook.xml→xlsx` 限定为仅 stream/resource 转换。
- **误报排除**: 已核实 renderer 注册 bean、工厂返回类型、注册表条目与 manager 调用链；`convertToStream` 分支（第 73-81 行）因走 `ITemplateOutput.generateToStream` 不受影响，问题仅限 text 路径。

---

### [P1] DslDocumentConverter.convertToText 用目标格式 loader 解析源资源，跨格式 DSL 文本转换必失败

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/DslDocumentConverter.java:39-40`
- **维度**: D1/D8
- **证据**:
```java
if (toLoader != null && toLoader.getDslNodeLoader() != null)
    return toLoader.getDslNodeLoader().loadDslNodeFromResource(doc.getResource(), options.getDslNodeResolvePhase()).xml();
```
- **现状**: 用 **toFileType 的 loader** 去解析 **源资源**。同文件 `convertToResource:58` 的正确写法是 `toLoader.getDslNodeSaver().saveDslNodeToResource(resource, doc.getNode(options))`（先按源格式解析再按目标格式保存），两个方法行为不一致。
- **风险**: 以 prompt 模型为证（`prompt.register-model.xml` 声明 `prompt.xml` 与 `prompt.yaml` 两个 xdsl-loader，`RegisterModelDiscovery` 分别装配 `DslXmlResourceLoader`/`DslJsonResourceLoader`，二者均实现 saver，因此 `ConverterRegistrationBean.loadDslConverters` 会注册双向转换器）：`convertText(..., "prompt.yaml", "prompt.xml", ...)` 走到第 39-40 行，用 XML loader 解析 YAML 资源 → 必然解析异常。反向 `prompt.xml→prompt.yaml` 因命中第 33-34 行 serializeToYaml 而正常——同一注册对一方向可用一方向崩溃。触发方 `DslToolImpl`（AI 代码工具做 prompt 格式互转）现实存在。
- **建议**: 第 39-40 行改为 `doc.getNode(options)` 取源格式 DslNode，再用 toLoader 的 dslNodeSaver/serializer 输出，与 `convertToResource` 对齐。
- **误报排除**: 已核实 prompt 模型双 xdsl-loader 装配链（`RegisterModelDiscovery.java:166-180`、`AbstractDslResourcePersister implements IResourceObjectSaver`）、`newConverter` 的 saver 非空条件（`ConverterRegistrationBean.java:166`）、以及 yaml→json/json→yaml 等走 `JsonTool` 分支的类型不受影响。

---

### [P1] MermaidGenerator 所有 out.line(String) 调用经 MessageFormat 格式化：class/style 语句必抛异常，单引号被静默吞掉

- **文件**: `nop-format/nop-mermaid/src/main/java/io/nop/mermaid/output/MermaidGenerator.java:188,244`（及 84、238 等）；根因在 `nop-kernel/nop-commons/.../CodeBuilder.java:184-187`
- **维度**: D1
- **证据**:
```java
// MermaidGenerator
out.line("class " + node.getClassName() + " {");          // 第 188 行
out.line("style " + node.getTarget() + " {");            // 第 244 行
// CodeBuilder.line(String, Object...)
return append(MessageFormat.format(format, args)).line();
```
- **现状**: 本机 JDK 实测（Zulu 26，MessageFormat 语义长期稳定）：
  - `MessageFormat.format("class Foo {")` → `IllegalArgumentException: Unmatched braces in the pattern.`
  - `MessageFormat.format("pie \"don't\" : 1")` → `pie "dont" : 1`（单引号被吞）
  - `MessageFormat.format("%% don't panic")` → `%% dont panic`
- **风险**: 只要 AST 含 class 节点或 style 语句，`MermaidGenerator` 渲染必抛 IllegalArgumentException（崩溃）；任何含 `'` 的注释/pie 标签内容被静默篡改（丢字符）；任何含未配对 `{`/`}` 的文本直接抛异常。而 `escapeMermaidString` 恰恰把 `'` 转义为 `\'`，仍含 `'` 字符，防不住。
- **建议**: 生成器改用 `out.append(...).line()` 或 `CodeBuilder` 增加不做 MessageFormat 处理的 `printLine(String)`；对进入 `line(format,args)` 的内容不得含未转义的 `'`/`{`/`}`。
- **误报排除**: 已读 `CodeBuilder` 全文确认 `line(String)` 唯一入口是 `MessageFormat.format`；已用本地 JDK 运行实测验证上述三例行为；mermaid 模块无测试覆盖生成器（`src/test` 为空），不存在“既有 golden 断言”使该行为成为既定契约。

---

### [P1] MermaidGenerator 输出与模块自带文法系统性不匹配，生成文本无法被自身解析器往返

- **文件**: `nop-format/nop-mermaid/src/main/java/io/nop/mermaid/output/MermaidGenerator.java:72,89-99,104-131,134-143,156-183,212-217`；对照 `nop-format/nop-mermaid/model/antlr/Mermaid.g4` 与生成 lexer 的 literal 表
- **维度**: D1/D8
- **证据**（生成器输出 vs 文法 `Mermaid.g4`）:
```java
out.line(node.getType().name().toLowerCase() + " " + node.getType());  // 72: "flowchart FLOWCHART"
out.append(" : \"").append(escapeMermaidString(node.getLabel())).append("\""); // 127: 边标签 A --> B : "x"
out.append(":::").append(node.getShape().name().toLowerCase());       // 98: ":::round"
out.line("subgraph " + node.getId()); ... out.line("end");            // 135/142
out.append(" -> ");                                                    // 161: sequence ARROW
```
文法侧: `mermaidFlowEdge : from edgeType? label(StringLiteral)? to`（标签在 to 之前、无 COLON）；`mermaidFlowSubgraph : SUBGRAPH id title? LBRACE ... RBRACE`（`[`/`]`，无 `title`/`end` 关键字）；`mermaidClassNode/mermaidStyleStatement` 用 LBRACE/RBRACE=`[`/`]` 而生成器写 `{`/`}`；lexer 无 `->`、`--->`、`:::` token（literal 表实证：仅 `'-->'`、`'->>'`、`'-.->'`、`'==>'`）。
- **风险**: 逐项核对结论：
  1. 文档头 `flowchart FLOWCHART`: `SEQUENCE/CLASS/STATE` 的 `name().toLowerCase()`（"sequence"/"class"/"state"）不是 lexer 关键字（关键字为 `sequenceDiagram`/`classDiagram`/`stateDiagram`）→ 生成的 sequence/class/state 文档**第一个 token 即解析失败**；`flowchart/gantt/pie/git/er/journey` 文档可解析但第二个大写枚举名按 `Identifier_` 解析成**多余的伪造流程节点**（数据污染）。
  2. 带 label 的 flow 边（`A --> B : "x"`）与 sequence 消息（默认 `->`、`OPEN_ARROW` 映射为 `-->` 与文法 `->>` 相反）生成的文本不可解析或往返后边类型错乱。
  3. `subgraph ... title ... end`、`class X {`、`style t {`、`id("x"):::shape` 均不符合文法。
  仅 `participant`、`task`、`pie`（无单引号时）、`direction`（见下条，实际也不可解析）少数语句可往返。
- **建议**: 以 `Mermaid.g4` 为单一事实源为生成器补 round-trip 测试（parse(generate(ast)) == ast），按文法修正各语句输出。
- **误报排除**: 所有结论均对照生成的 `Mermaid.tokens`/`MermaidLexer.java` literal 表逐 token 论证（lexer 大小写敏感、`Identifier_: [a-zA-Z_][a-zA-Z0-9_]*`），未依赖对真实 Mermaid 语法的假设——本模块是自定义 DSL，判定标准是其自带文法。

---

### [P1] Mermaid 文法自身缺陷：COMMENT 进 HIDDEN 通道使注释规则不可达；DIRECTION token 无 lexer 规则；BaseRules 关键字重复定义

- **文件**: `nop-format/nop-mermaid/model/antlr/BaseRules.g4:42-43,45,78`；`Mermaid.g4:47`；生成物 `MermaidLexer.java`（literalNames/ruleNames）
- **维度**: D1/D8
- **证据**:
```java
// BaseRules.g4
CLASS: 'classDiagram';   // 第 3 行
...
CLASS: 'class';          // 第 42 行，重复定义
STATE: 'stateDiagram';   // 第 7 行
STATE: 'state';          // 第 43 行，重复定义
COMMENT: '%%' ~[\r\n]* -> channel(HIDDEN);  // 第 78 行
// Mermaid.g4:47  mermaidDirectionStatement : DIRECTION direction=mermaidDirection_
```
- **现状**（以提交的生成物为准）: 生成 lexer 的 literalNames 只有 `'classDiagram'`/`'stateDiagram'`（重复定义的后一条未生效）；lexer ruleNames 共 50 条，**不存在名为 DIRECTION 的 lexer 规则**，但 parser 引用 `DIRECTION=52`。
- **风险**:
  1. `%%` 注释被丢入 HIDDEN 通道，parser 规则 `mermaidComment: COMMENT content=StringLiteral_` 永远匹配不到（parser 只见 default 通道）→ 注释在解析时**静默丢失**，`MermaidComment` AST 节点不可达；生成器又输出 `%% xxx`（不带引号也不符合该规则）。
  2. `DIRECTION` token 无任何 lexer 规则可产生 → `direction TB` 文本中 `direction` 按 Identifier_ 词法化 → direction 语句**永远解析失败**，生成器输出的 direction 语句同样不可往返。
  3. `class`/`state` 小写关键字不存在（被 `'classDiagram'` 占位）→ 文法中的 class/state 语句实际只能以 `classDiagram X [...]`/`stateDiagram ...` 触发，与生成器、与用户直觉均不符。
  4. 生成物与 .g4 源不同步（parser ruleNames 含 `"DIRECTION","Identifier"` 两条在当前 .g4 中不存在的规则），从当前 .g4 重新生成大概率失败，构建可复现性受损。
- **建议**: 修正 BaseRules.g4（COMMENT 留在 default 通道、补 `DIRECTION: 'direction';`、删除重复的 CLASS/STATE 定义、统一 class/state 关键字策略），重新生成并提交配套生成物与 round-trip 测试。
- **误报排除**: 全部基于已提交生成物（`Mermaid.tokens`、`MermaidLexer.java` 的 literalNames/ruleNames、`MermaidParser.java` 的 token 常量）逐条核对，非仅凭 .g4 推断。

---

### [P1] MarkdownNormalizer 强制把代码围栏长度改为 3 并原地覆写源文件，含 ``` 行的代码块被不可逆破坏

- **文件**: `nop-format/nop-markdown-ext/src/main/java/io/nop/markdown/ext/MarkdownNormalizer.java:101-105,47-51`
- **维度**: D1
- **证据**:
```java
public void visit(FencedCodeBlock fencedCodeBlock) {
    fencedCodeBlock.setOpeningFenceLength(3);
    fencedCodeBlock.setClosingFenceLength(3);
    ...
}
public void normalizeResource(IResource resource) {
    String text = ResourceHelper.readText(resource);
    text = normalizeText(text);
    ResourceHelper.writeText(resource, text);   // 无备份原地覆写
}
```
- **现状**: CommonMark 规定围栏长度必须大于内容中任何反引号行；作者写 4+ 个反引号正是因为代码示例里含 ` ``` ` 行。归一化把长度压到 3 后，MarkdownRenderer 输出 3 个反引号，重解析时代码块在内容中的 ` ``` ` 行处提前闭合，其后内容泄漏为正文/错误结构。
- **风险**: `normalizeDir` 批量遍历目录原地改写 .md（无备份、无 dry-run）——对任何包含"markdown 讲 markdown"代码示例的文档（本仓库 docs 大量存在）造成**不可逆内容损坏**。
- **建议**: 不缩短围栏，或改为 `max(3, 内容中最长反引号串长度 + 1)`；`normalizeResource` 先写临时文件/保留 .bak。
- **误报排除**: 已确认 commonmark 的 MarkdownRenderer 按 `openingFenceLength` 输出围栏、代码内容逐字保留（该行为是 CommonMark 规范要求）；模块内测试未覆盖此场景（`TestMarkdownNormalizer` 存在但无围栏嵌套用例的断言依据，未发现与其冲突）。

---

### [P2] DslDocumentConverter 用 from 模型的 config 查找 toFileType 的 loader（toConfig 取而未用）

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/DslDocumentConverter.java:20-23,52-55`
- **维度**: D8
- **证据**:
```java
ComponentModelConfig toConfig = ResourceComponentManager.instance().getModelConfigByFileType(toFileType);
ComponentModelConfig.LoaderConfig toLoader = null;
if (toConfig != null) {
    toLoader = config.getLoader(toFileType);   // config 是 doc.getFileType() 的模型配置
```
- **现状**: `toConfig` 仅用于判空，loader 实际取自 `config`（源文件类型的模型配置）。
- **风险**: 当前注册链路（`loadDslConverters` 只在同一 model 配置内的 loader 两两注册）下二者恰好相同，掩盖了问题；一旦通过自定义 `*.convert.xml` 把 `DslDocumentConverter` 注册到跨模型类型对（该类是公开类，注册表按 class 名反射实例化），`config.getLoader(toFileType)` 返回 null 或错误 loader，静默落入"按源模型 xdef 序列化"的兜底分支，产出与 toFileType 语义不符的内容。
- **建议**: 改为 `toConfig.getLoader(toFileType)`，并对二者不等时明确警告。
- **误报排除**: 已确认现有 default.convert.xml 未注册 DslDocumentConverter、`loadDslConverters` 注册范围内 from/to 同 config，故当前无错误行为——属潜在契约漂移，定级 P2。

---

### [P2] MermaidGenerator 对标识符类字段不校验不转义，非 ASCII/特殊字符 ID 直接破坏图文本

- **文件**: `nop-format/nop-mermaid/src/main/java/io/nop/mermaid/output/MermaidGenerator.java:90,105,124,148,157,176,188,213,223,244`
- **维度**: D5/D1
- **证据**:
```java
out.append(node.getId());                 // 90, 未做任何校验
out.append(node.getFrom()); ... out.append(node.getTo());  // 105/124
out.line("class " + node.getClassName() + " {");           // 188
```
- **现状**: 仅 text/label/title/alias/description/value 走 `escapeMermaidString`；id/from/to/className/name/target 原样拼接。而文法 `Identifier_: [a-zA-Z_][a-zA-Z0-9_]*` 只接受 ASCII 字母数字下划线。
- **风险**: 该模块的典型用途是把业务模型（实体名、状态名）渲染成图，业务命名常含中文、空格、`-`、`.` 等；这类 ID 生成的文本超出词法范围，轻则解析失败，重则 `A --> B` 之类内容被词法器切分出错误的边结构（注入式结构破坏）。字符串字面量转义本身（引号/反斜杠/换行）实现正确。
- **建议**: 生成器入口对标识符做白名单校验并抛 `NopException(ERR_MERMAID_...)`（模块已定义多个相关错误码却未使用），或提供 ID 合法化（sanitization）+ 冲突消解。
- **误报排除**: 已核对文法 Identifier_ 定义与生成器全部 append 点；确认为程序化 API 输入面（解析器产出的 ID 必然合法），非解析路径漏洞。

---

### [P2] 异常处理策略违背平台两档规范：impl 层大量裸 JDK 异常，模块错误码闲置

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/WordDocumentConverter.java:22`、`PptDocumentConverter.java:22`、`JsonDocumentConverter.java:31`、`DslDocumentConverter.java:44`、`ExcelDocHelper.java:33`、`DslDocumentObjectBuilder.java:43,69,107`、`DslToExcelDocumentConverter.java:37,65`、`XlsxDocumentObjectBuilder.java:19`、`ConverterRegistrationBean.java:178` 等
- **维度**: D7/D4
- **证据**:
```java
throw new UnsupportedOperationException("Unsupported file type: " + toFileType);  // WordDocumentConverter:22
throw new IllegalArgumentException("fileType no xdef:" + toFileType);              // DslDocumentConverter:44
throw new IllegalArgumentException("Document format must be xlsx");                // ExcelDocHelper:33
```
- **现状**: `DocumentConverterManager` 与 `MermaidParseHelper` 正确使用 `NopException + ErrorCode + .param(...)/.loc()`；但 IDocumentConverter 公开接口的绝大多数实现类用 `IllegalArgumentException`/`UnsupportedOperationException`（其中 `XlsxDocumentObjectBuilder.buildFromText`、`DocxDocumentObjectBuilder.buildFromText` 甚至无消息）。模块已定义 `DocConvertErrors`（仅 2 个码）与 `MermaidErrors`（15 个码，几乎全部未使用）。
- **风险**: 接口抛出类型不可预期（未在接口声明受检语义），调用方无法按 NopException 统一提取 errorCode/param；违反 AGENTS.md 错误处理两档策略的精神（公共接口应可用 NopException 或模块异常类）。未发现 bare `RuntimeException`、未发现异常吞噬或丢 cause（`NopException.adapt` 使用正确）。
- **建议**: 为 convert 不支持类型定义 `ERR_CONVERT_UNSUPPORTED_FILE_TYPE` 等错误码，替换 JDK 异常；至少给所有 `UnsupportedOperationException()` 补消息。
- **误报排除**: 逐条读过上述抛出点的上下文，确认非第三方库 rethrow、非 unreachable 防御分支（如 `WordDocumentConverter` 的 else 分支在注册表配置错误时真实可达）。

---

### [P3] SameTypeDocumentConverter.convertToText 两分支完全相同（死条件）

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/SameTypeDocumentConverter.java:20-25`
- **维度**: D1（疑似逻辑缺失）/维护性
- **证据**:
```java
if (keepRaw(doc, options))
    return doc.getText(options);
return doc.getText(options);
```
- **现状**: `keepRaw` 判断无任何作用；对照同类 `convertToStream`（raw 走 `saveToStream`，非 raw 走 dslNodeSaver 序列化），text 路径疑似漏写 raw 分支或本应删除条件。
- **风险**: 行为上无差异，但掩盖意图；对 raw 阶段的 DSL 文档，`getText` 读原文与"归一化文本"的差异被吞掉。
- **建议**: 明确语义：raw 时返回原文（当前 getText 即原文，可删条件），或与 stream 路径对齐。
- **误报排除**: 已核对 `ResourceDocumentObject.getText`/`DslDocumentObject` 均无 options 分支行为差异，确认两分支确实等价。

---

### [P3] 死代码：WordDocHelper 空类、MathNodeProcessor 未注册、MathNodeParser 注册被注释

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/WordDocHelper.java:3-4`；`nop-format/nop-markdown-ext/src/main/java/io/nop/markdown/ext/math/MathExtension.java:21`、`MathNodeProcessor.java`（全文件）
- **维度**: 维护性
- **证据**:
```java
public class WordDocHelper {
}
// MathExtension.extend(Parser.Builder):
// parserBuilder.inlineParserFactory(new MathNodeParser.Factory());
```
- **现状**: `WordDocHelper` 空类无引用；`MathNodeProcessor`（DelimiterProcessor）全仓库无注册点（内联 `$..$` 数学实际由 `MarkdownNormalizer.NormalizeVisitor.visit(Text)` 手工转换兜底）。
- **风险**: 误导后续维护者以为内联数学走 delimiter 处理器；空类残留。
- **建议**: 删除 WordDocHelper；要么注册 MathNodeProcessor 并移除 NormalizeVisitor 的手工转换，要么删除 MathNodeProcessor。
- **误报排除**: grep 全仓库确认三者均无生产引用/注册。

---

### [P3] DocumentConverterManager 静态可变单例与无同步注册表

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/DocumentConverterManager.java:29,31-36`
- **维度**: D3
- **证据**:
```java
static DocumentConverterManager _instance = new DocumentConverterManager();
private final Map<String, Map<String, IDocumentConverter>> converters = new HashMap<>();
```
- **现状**: `_instance` 非 volatile，`registerInstance` 写 / `instance()` 读无同步；converters/builders 为普通 HashMap，`registerConverter`/`registerDocumentObjectBuilder` 与并发 `getConverter` 之间无 happens-before。
- **风险**: 当前注册只发生在启动期（`@PostConstruct` 的 `ConverterRegistrationBean`），读多写少下风险低；但该类标 `@GlobalInstance`，任何运行期动态注册（接口支持）都可能导致脏读甚至 HashMap 结构破坏。
- **建议**: 改 `ConcurrentHashMap`，`_instance` 加 volatile 或改用平台统一 GlobalInstance 机制。
- **误报排除**: 已确认仓库内无运行期注册调用方，按潜在并发风险定级 P3。

---

### [P3] XlsxDslDocumentObjectBuilder 用可空 get 获取模型配置，失败时裸 NPE

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/XlsxDslDocumentObjectBuilder.java:34-35,56-57`
- **维度**: D4
- **证据**:
```java
ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(fileType);
if(config.getXdefPath() != null)   // config 可能为 null
```
- **现状**: 同类 `DslDocumentObjectBuilder.getXdefPathFromFileType:39` 使用 `requireModelConfigByFileType`（抛带类型的 NopException），此处用可空版本直接解引用。
- **风险**: fileType 未注册模型时抛 NPE 而非可诊断错误。当前注册链路下该 builder 只会注册到有模型配置的类型上，故为低概率路径。
- **建议**: 统一改用 `requireModelConfigByFileType`。
- **误报排除**: 已比对两处 builder 的注册来源（`loadDslConverters` 仅对 modelConfig 的 loader 类型注册），确认当前配置下 config 非空。

---

### [P3] MarkdownNormalizer 每个文件重建 Parser/Renderer

- **文件**: `nop-format/nop-markdown-ext/src/main/java/io/nop/markdown/ext/MarkdownNormalizer.java:39-45,26-37`
- **维度**: D6
- **证据**:
```java
public String normalizeText(String text) {
    Parser parser = buildParser();          // 每次新建（含两个 Extension 实例）
    ...
    String normalized = buildRenderer().render(document);
```
- **现状**: `normalizeDir` 批量处理时每个文件构建一次 Parser 与 MarkdownRenderer（commonmark 的 builder 构建有可观的正则/表初始化开销），二者均线程安全可复用。
- **风险**: 大目录归一化时的无谓 CPU/分配开销；无正确性问题。
- **建议**: Parser/Renderer 做成实例字段或懒加载单例。
- **误报排除**: 已确认 Parser 与 MarkdownRenderer 在 commonmark 中线程安全（无状态渲染），复用不会引入并发问题。

---

### [P3] NormalizeVisitor 把同一 Text 节点内任意两个 `$` 之间内容一律转为数学节点（货币文本误转）

- **文件**: `nop-format/nop-markdown-ext/src/main/java/io/nop/markdown/ext/MarkdownNormalizer.java:108-117`
- **维度**: D1（轻微）
- **证据**:
```java
int pos = literal.indexOf('$');
if (pos >= 0 && literal.length() > pos + 2 && literal.indexOf('$', pos + 1) > 0) {
    normalizeMathNode(text);
```
- **现状**: 判定条件不区分货币与数学，`price is $5, fee is $10` 这类文本会把 `5, fee is ` 包装为 MathNode，渲染回 `$5, fee is $`（文本层面恰好往返，但下游按 KaTeX 数学渲染时语义错误且空 MathNode 渲染为 `$`）。
- **风险**: 含货币符号的文档语义漂移；因输出文本不变，极难被发现。
- **建议**: 收紧识别（如要求 `$` 紧邻非空白、内容无空格开头结尾，或只处理成对 `$...$` 无逗号/长度限制的场景）。
- **误报排除**: 已手工推演 `normalizeMathNode` 的 TextScanner 状态机（含转义、尾部 `$`、`$$` 情况），确认无死循环/越界，仅语义误判问题。

---

## 未发现问题的重点检查项（误报排除汇总）

- **D2 资源管理**: `IDocumentConverter.convertToResource` 默认实现、`ResourceDocumentObject.saveToStream` 均 finally `safeCloseObject`；未发现流/文档泄漏。
- **D5 XSS**: `nop-markdown-ext` 的 `MathNodeRenderer` 基于 commonmark 的 **Markdown** 渲染器（输出 `$..$` 文本），全模块不产出 HTML，无 HTML 转义缺失面；converter 的 HTML 输出走 nop-report 模板引擎（不在范围）。未发现 XSS 路径。
- **D7 IoC 约定**: 三模块无任何 `@Inject`/`@InjectValue`/private 注入；converter 的 bean 在 `converter-defaults.beans.xml` 显式定义，符合 Nop IoC 约定。
- **空 catch / printStackTrace / bare RuntimeException / System.out**: grep 全量扫描零命中。
- **`_` 前缀文件手改痕迹**: 未发现（`_gen` 与 antlr 生成物与平台模板风格一致，但存在生成物与 .g4 源不同步问题，见 P1 文法条目）。
- **ExcelWorkbookToMarkdownConverter → TableToMarkdownConverter**: 表格单元格经 `MarkdownHelper.escapeCell` 转义 `|` 与换行（nop-markdown 模块，越界核实），xlsx→md 表格保真度无问题。
