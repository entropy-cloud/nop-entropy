# C6 资源生命周期与泄漏 — 初审报告

- **审计单元**: nop-code-rest（nop-code-lang-{java,python,typescript,go,rust,csharp} + nop-code-api + nop-code-dao + nop-code-app，src/main 排除 `_gen/`）
- **单元规模口径**: 126 files / 12,614 lines（`find <9 roots> -path "*/src/main/*" -name "*.java" ! -path "*_gen*" | xargs wc -l` = 126 / 12,614，与批次基线一致）
- **维度**: C6 资源生命周期与泄漏（并发·异步·资源审计提示词 C6 正文 + deep-audit-prompts.md 共享前缀）
- **批次**: 全单元单批·抽查模式（与 C1 同批；解析器资源面全量逐文件核对，api/dao/app 契约面抽样核对 + 全单元资源 grep 零命中复现）
- **盲审声明**: 本初审为盲审。未读取 `ai-dev/audits/`（含本维度目录既有文件、C1 批产出）、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件。初审输出仅为线索，最终结论以独立复核为准。
- **CL 前缀**: 本报告维度集外线索使用 CL-101 起编号。
- **基线**: `_tmp/cra-baseline-nop-code-rest.txt`（@ b8552f11d1）。基线标注的 C6 解析器资源面 6 个文件与本次核对范围一致；基线"并发原语五类=0"预注经抽查核实成立（本单元无自建线程/池/锁载体）。
- **只读声明**: 除本报告文件外未修改任何文件。

---

## 1. 结论摘要

**本单元 C6 维度零发现，显式终止。** 全单元资源获取点配对盘点 12 类（见 §3），无一对失败；无 OS 级资源（文件流/连接/池/锁/信号量/临时文件/listener 注册）获取点（§4 零命中 grep 可复现）。解析器资源面（六语言 *CodeFileAnalyzer）全部为纯堆内存对象，由 GC 回收；tree-sitter compat 桥接层的 `delete()` 为文档化 no-op，不构成释放义务。维度集外记录 2 条跨维度线索（CL-101 → C1、CL-102 → C5/C9），均为强信号，移交对应维度。

---

## 2. 方法与覆盖

1. 资源获取点全单元 grep（模式见 §4，含附录 C C6 基线模式扩展）→ 圈定解析器资源面 6 文件为唯一实质面。
2. 解析器面逐文件核对：JavaFileAnalyzer.java（969 行全文）+ TypeScript/CSharp/Go/Rust/Python 五个 tree-sitter 分析器的 analyze() 入口、nodeText 助手、实例字段清单（全量 grep）。
3. 资源语义判定依赖两处单元外只读核对（接口契约核对，非审计对象）：
   - `nop-tools/nop-treesitter/.../compat/TSParser.java`、`compat/TSTree.java`（delete()/copy() 语义）
   - `nop-tools/nop-treesitter/.../TSTree.java`（arena 所有权）、`subtree/SubtreeArena.java`（int[] 列存储）、`TSParser.parse`（每 parse 新建 arena）、`language/Language.java`（volatile 有界统计）
4. 消费方契约核对（CL-101 可达性判定）：`nop-code-core/.../LanguageAdapterRegistry.getAnalyzer`（每次解析返回新分析器实例）、`ProjectAnalyzer`（逐文件 `registry.getAnalyzer(relativePath)` 后立即 `analyze`）。
5. api/dao/app 契约面：抽查 NopCodeFileApi（XGEN 生成 CRUD 契约）、NopCodeFile/INopCodeFileBiz（ORM 空壳契约）、DTO/Bean（Serializable 数据载体，`java.io` 命中仅为 Serializable import）、NopCodeApplication（与 nop-metadata-app 同构的平台标准启动模板）。

---

## 3. 资源获取点配对表（逐项判定）

| # | 获取点 | 位置 | 资源类型 | 释放配对 | 判定 |
|---|--------|------|----------|----------|------|
| 1 | `new JavaParser(parserConfiguration).parse(sourceCode)` | `JavaFileAnalyzer.java:140` | 解析器实例 + ParseResult/CompilationUnit（纯堆） | 无释放义务（无 Closeable）；每次 analyze 新建实例，实例生命周期=单次调用，正确的线程语义（JavaParser 实例非线程安全，per-call 新建即规避） | 安全 |
| 2 | `new TSParser()` + `parseString(null, source)` | `TypeScriptCodeFileAnalyzer.java:61-64` | 解析树 arena（`SubtreeArena` int[] 列，纯堆） | `tree = null`（:93，纯修饰性死存储）；compat `TSTree.delete()` 为文档化 no-op；GC 回收 | 安全（见 §5 误报排除） |
| 3 | 同上 | `CSharpCodeFileAnalyzer.java:60-63`（`tree = null` :80） | 同上 | 同上 | 安全 |
| 4 | 同上 | `GoCodeFileAnalyzer.java:55-58`（`tree = null` :76） | 同上 | 同上 | 安全 |
| 5 | 同上 | `RustCodeFileAnalyzer.java:52-55`（`tree = null` :68） | 同上 | 同上 | 安全 |
| 6 | 同上 | `PythonCodeFileAnalyzer.java:45-48`（`tree = null` :62） | 同上 | 同上 | 安全 |
| 7 | `private static final TreeSitterXxx TS_LANGUAGE` ×5 | 各分析器类头（如 `CSharpCodeFileAnalyzer.java:39`） | 语法 blob（不可变；含一个 volatile 有界 max 统计 `observedNodesPerByte`，`Language.java:80,762-769`） | 进程生命周期持有，属设计意图；无 acquire/release 配对 | 安全 |
| 8 | 文件/网络流（InputStream/Reader/Writer/File/channel） | 全单元 | — | — | 零命中（§4），无获取点 |
| 9 | JDBC/JMS/Kafka 连接、`IPool` 借出、`LocalFileLock`、`ISemaphore` 许可、临时文件 | 全单元 | — | — | 零命中（§4），无获取点 |
| 10 | listener/handler 注册（add/removeListener、register/unregister） | 全单元 | — | — | 零命中（§4），注册表对称性无对象可查 |
| 11 | JavaFileAnalyzer 内部 walk 状态（`IndexVisitor` :206-212 的 `symbolMap`/`currentTypeSymbol`/`currentMethodSymbol`） | `JavaFileAnalyzer.java:162`（per-call `new IndexVisitor`） | 单次调用内的普通对象 | 调用结束即可达性终止，GC 回收 | 安全 |
| 12 | api/dao/app 契约面（12 个 `NopCode*Api` CRUD 接口、11 个实体子类、11 个 biz 接口、DTO/Input/Output Bean、`NopCodeApplication`） | `nop-code-api`、`nop-code-dao`、`nop-code-app` | 数据载体 / 生成契约接口 | 无资源获取（预期成立，如实记录） | 安全 |

异常/取消/超时路径核对：#1–#6 的所有提前 return（`sourceCode` 空白、`tree == null`、`root.isNull()`）与 walk 抛错路径均无释放义务——全部对象为堆可达性回收，不存在"获取后抛错泄漏"形态（F8 不适用）。#12 中 `NopCodeApplication` 的 `StartupEvent→QuarkusIntegration.start()/NopApplication.run` 与 `ShutdownEvent→CoreInitialization.destroy()` 成对，与平台标准模板（nop-metadata-app 同构）一致。

---

## 4. 零命中声明（计数口径可复现）

以下计数结论的 grep 口径（工作目录 = 仓库根；roots = 9 个 src/main，排除 `_gen/`）：

1. **文件流/IO 获取点 = 0**：`grep -rn --include="*.java" -E "new FileInputStream|new FileOutputStream|Files\.(newInputStream|newBufferedReader|newBufferedWriter|readAllBytes)|RandomAccessFile|PrintWriter|FileReader|FileWriter" <roots>` → 0 个非零命中文件。
2. **连接/池/锁/信号量 = 0**：`grep -rn --include="*.java" -E "getConnection|\.acquire\(|\.borrow|LocalFileLock|ISemaphore|IPool|createTempFile|deleteOnExit" <roots>`（排除 io.nop.code.core 前缀路径）→ 0 命中。
3. **注册表对称性对象 = 0**：`grep -rn --include="*.java" -E "addListener|removeListener|register\(|unregister\(|addObserver" <roots>` → 0 命中（exit 1）。
4. **Closeable/AutoCloseable 实现 = 0**：`grep -rn --include="*.java" -E "implements.*(Closeable|AutoCloseable)|extends.*(InputStream|OutputStream|Reader|Writer)" <roots>` → 0 命中。
5. `java.io` 命中复核：api DTO 的 `import java.io.Serializable`（36 个 DTO）与 Bean 的 `getFile()`/`setFile()`（字段名含 file 的 Map 属性，非 java.io.File）——非资源。

---

## 5. 误报排除（为什么以下形态不立案）

1. **五分析器的 `tree = null;`**：不是"未调用 delete 的泄漏"。`nop-tools/nop-treesitter` compat 桥接层明确文档化：`compat/TSParser.delete()` "no native resources; GC reclaims"、`compat/TSTree.delete()` no-op（迁移桥接，镜像 JNI API 签名以保持源兼容）。核心 `TSTree` 为不可变 Java 对象 + 私有 `SubtreeArena`（int[] 并行列），每 parse 新建 arena（`TSParser.parse`:60 `new SubtreeArena(...)`）无跨树共享。堆对象无释放义务——这与 JNI 版 tree-sitter（必须配对 delete）的语义差异是本单元的既定架构决策，不是缺陷。
2. **`new JavaParser(parserConfiguration)` 每次 analyze 新建**：JavaParser 实例非线程安全，per-call 新建正是正确的资源生命周期与线程语义选择；共享的 `parserConfiguration`/`typeSolver` 为 init 后只读配置。不按"未复用实例"报浪费（冷/中频路径，且为线程安全代价）。
3. **`static final` TS_LANGUAGE 进程级持有**：不可变语法 blob，平台"声明存在的全局只读共享"不是泄漏。

---

## 6. 发现条目

**无。C6 维度零发现，显式终止声明：本维度在本审计单元无 P0/P1/P2/P3 立案项。** 全部资源获取点配对判定安全（§3），无一处需要修复。

---

## 7. 维度集外线索（CL 附记）

### [CL-101] CSharpCodeFileAnalyzer 实例字段承载单次调用状态——六分析器中唯一的非常量实例状态，无线程契约背书（→ C1）

- **文件**: `nop-code/nop-code-lang-csharp/src/main/java/io/nop/code/lang/csharp/CSharpCodeFileAnalyzer.java:41-42,69,76`
- **证据片段**:
  ```java
  private String source;
  private String lastNamespace;
  ...
  TSNode root = tree.getRootNode();
  this.source = sourceCode;          // :69，analyze() 每次调用写实例字段
  ...
  lastNamespace = null;              // :76
  walkNode(root, namespaceStack, null, result);   // walk 全程经 nodeText() 读 this.source
  ```
- **疑似维度**: C1（共享可变状态与可见性）；C6 侧核 realization：非资源泄漏，纯堆。
- **强信号理由（F3 同族清扫）**: 六语言分析器实例字段全量 grep（口径：`^\s+(private|protected|public|static)\s+...` 排除方法行）显示 Java 分析器仅 init 期配置字段（`typeSolver`/`parserConfiguration`/`methodCallFilter`/`routeConvention`/`enableSymbolResolution`）+ per-call 内部类状态，TypeScript/Go/Rust/Python 分析器**零实例字段**——仅 CSharp 用实例字段承载 per-call 数据（`source`、`lastNamespace`），是同族中的孤例。
- **当前可达性（为何今天不是 P1）**: 唯一消费链 `LanguageAdapterRegistry.getAnalyzer`（nop-code-core `LanguageAdapterRegistry.java:19-28`）每次调用 `adapter.getFileAnalyzer()` 返回**新实例**，`ProjectAnalyzer` 四处均为逐文件 `getAnalyzer(relativePath)` 后立即 `analyze`，无缓存复用 → 字段实际等效方法局部状态，当前接线无交错序列可写，按本领域纪律标"理论风险，未具体化"。
- **风险**: `ICodeFileAnalyzer` 接口（nop-code-core）无"分析器须无状态/线程安全"契约，`ILanguageAdapter.getFileAnalyzer()` 无"每次须返回新实例"契约——任一后续消费方按直觉缓存"每语言一个分析器"并行分析（如增量索引并行化），`nodeText()`（:603-611 读 `this.source`）即读到并发兄弟文件的源码，产出错乱符号文本且无报错（静默数据损坏）。
- **建议**: 将 `source`/`lastNamespace` 降为 walk 上下文参数或局部上下文对象（Go 分析器的 `WalkContext` 模式即同族正确参照），或在 `ICodeFileAnalyzer` javadoc 写明线程安全契约；二选一即可，无需改行为。
- **信心水平**: 确定（字段存在与同族孤例为事实；竞态本身为理论级）。
- **移交**: C1 维度复核时裁定是否立案及判级。

### [CL-102] 四个 tree-sitter 分析器的 nodeText() 每次调用全文重编码 UTF-8——O(文件大小 × 调用次数) 分配放大，TS 分析器已在同族给出正确参照（→ C5/C9）

- **文件**:
  - `nop-code/nop-code-lang-python/src/main/java/io/nop/code/lang/python/PythonCodeFileAnalyzer.java:73-84`（助手），11 处调用
  - `nop-code/nop-code-lang-csharp/src/main/java/io/nop/code/lang/csharp/CSharpCodeFileAnalyzer.java:603-611`，10 处调用
  - `nop-code/nop-code-lang-go/src/main/java/io/nop/code/lang/go/GoCodeFileAnalyzer.java:607-618`，18 处调用
  - `nop-code/nop-code-lang-rust/src/main/java/io/nop/code/lang/rust/RustCodeFileAnalyzer.java:523-534`，16 处调用
- **证据片段**（四文件同构，以 Go 为例）:
  ```java
  private static String nodeText(TSNode node, String source) {
      int startByte = node.getStartByte();
      int endByte = node.getEndByte();
      if (startByte >= endByte) { return ""; }
      byte[] bytes = source.getBytes(StandardCharsets.UTF_8);   // 每次调用全文重编码
      if (endByte > bytes.length) { endByte = bytes.length; }
      return new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
  }
  ```
- **疑似维度**: C9（热路径分配放大）/ C5（内存churn）；C6 侧核实：GC 可达性回收，非泄漏，故不立案于本维度。
- **强信号理由**: 调用点计数口径 `grep -c "nodeText("`（含 1 行定义）：Python 12 / Rust 17 / Go 19 / CSharp 11，即每次符号/继承/装饰器/调用提取都分配一个**整文件大小**的 byte[] 再切片。同族正确参照已存在：TypeScript 分析器在 analyze() 一次性编码（`TypeScriptCodeFileAnalyzer.java:80` `byte[] sourceBytes = sourceCode.getBytes(...)`）并逐层下传，其助手 `getNodeText(node, sourceBytes)` 零重复编码。量化估算链：100KB 源文件 × 单文件约 50 次 nodeText 调用 ≈ 5MB/文件的瞬时分配churn，× 索引批文件数。
- **风险**: 索引热路径（批量分析仓库文件）GC 压力与分配速率放大；大文件（MB 级）单文件内即产生数百 MB 分配流量。无正确性问题（截断护栏 `endByte > bytes.length` 齐备）。
- **建议**: 统一采用 TS 分析器的"analyze 入口编码一次、byte[] 下传"模式（同族内已有参照实现，机械迁移即可）。
- **信心水平**: 确定。
- **移交**: C9/C5 维度复核时裁定判级（预期 P2 性能候选）。

---

## 8. 复核状态

| 条目 | 状态 |
|---|---|
| C6 发现 | 无（零发现终止） |
| CL-101 | 未复核（移交 C1） |
| CL-102 | 未复核（移交 C5/C9） |

---

## 9. 维度复核结论（C6 独立复核）

- **复核方式**: 独立复核子代理从 6 个解析器资源面配对中抽样 3 项 + compat 桥语义与零命中口径专项核验；以下 file:line 为复核者独立读取所得。
- **抽样清单**:
  1. **配对 #2 TypeScriptCodeFileAnalyzer** — 成立。:61-64 `new TSParser()`+`parseString(null, source)`；:80 analyze 入口一次性 UTF-8 编码下传（同族正确参照存在）；:93 `tree = null` 纯修饰性死存储。
  2. **配对 #3 CSharpCodeFileAnalyzer** — 成立。:60-63 获取点、:80 `tree = null`。
  3. **配对 #4 GoCodeFileAnalyzer** — 成立。:55-58 获取点、:76 `tree = null`、:73-74 per-call `WalkContext`（同族状态封装正确参照）。
  4. **专项：compat 桥 delete() no-op 判定** — 成立。独立核读 `nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/compat/TSTree.java:48-50` 与 `compat/TSParser.java:44-46`：两者 delete() 均为空实现且文档化 "no native resources; GC reclaims"（迁移桥接镜像 JNI 签名）。§5 误报排除第 1 条成立——五分析器无 delete() 释放义务，`tree = null` 非泄漏。
  5. **专项：零命中口径抽查** — 分析器输入经调用方 `resource.readText()` 提供，分析器自身无流/连接/池获取点；全单元无 Closeable/AutoCloseable 实现（grep 复现）。§4 与源码现状一致。
- **抽样「安全」判定失败数：0，无需立案 C6-R-001。初审零发现结论可信。**
- **CL 裁定**:
  - **CL-101 确认**（CSharpCodeFileAnalyzer.java:41-42 字段、:69/:76 每调用写、:602-612 nodeText 实例方法读 `this.source`；Python/Rust/Go/TS 四同族 grep 零实例字段，"同族孤例"成立）。按 C1 复核裁定为 watch-only 结构性脆弱、不立案，详见 C1 复核节。本维度核心移交项"可达性判定"经独立核验成立：registry getAnalyzer（LanguageAdapterRegistry.java:19-28）每次转发六 adapter 的 `new` 工厂，全库 8 个调用点（初审写 7，实为 8，勘误已记录于 C1 复核节）均逐文件获取后立即 `analyze`，无缓存复用——CL-101 今日不构成竞争。
  - **CL-102 确认**（nodeText 计数独立复核：Python 12 / CSharp 11 / Go 19 / Rust 17 / TS 0——TS 助手命名为 `getNodeText` 故 0 命中，与初审"含 1 行定义"口径逐字吻合）；归 C5/C9，维持移交。

## 最终保留项

最终保留项：0
