# C10 测试基建盘点（nop-code REST 审计单元 · 零缺陷面）

- **审计单元**: nop-code-rest（nop-code-lang-{java,python,typescript,go,rust,csharp} + nop-code-api + nop-code-dao + nop-code-app；C1/C6 口径 = src/main 126f/12,614 排除 `_gen/`；本报告盘点对象为同模块 **src/test** 全量 + 单元外直接锚点测试的登记）
- **形态**: C10 零缺陷面 · 测试基建盘点。C1 与 C6 两维最终保留项均为 **0**，本报告不产生任何发现条目，仅盘点测试保护现状并登记 watch-only 建议供未来立案/接线时引用。
- **输入**: 同目录 `C1-shared-mutable-state.md`（最终保留项=0，CL-001/002/003 + CL-101 裁定）与 `C6-resource-lifecycle.md`（最终保留项=0，CL-101/102 移交）。
- **盲审声明**: 本报告为盲审产出。除上述两份维度报告外，未读取 `ai-dev/audits/` 下任何其他文件，未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件。
- **只读声明**: 除本报告文件外未修改任何文件；未触碰产品代码、模型与生成物。

---

## 1. 测试文件清单（lang-*/api/dao/app，src/test 全量）

| 模块 | 测试文件数 | 行数 | 清单（相对 `src/test/java/io/nop/code/lang/<lang>/`） | 并发断言 | 资源断言 |
|---|---|---|---|---|---|
| nop-code-lang-java | 6 | 714 | TestJavaCodeFileAnalyzer(147L)、**TestJavaFileAnalyzerConcurrent(54L)**、TestJavaFileConversion(105L)、TestJavaLanguageAdapter(38L)、TestJavaSymbolConversion(230L)、TestProvenanceAndSpringRoutes(140L) | **有 1 个** | 无 |
| nop-code-lang-python | 6 | 419 | TestPythonCodeFileAnalyzer(170L)、TestPythonDecoratorExtractionFix(55L)、TestPythonLanguageAdapter(38L)、TestTreeSitterByteOffset(80L)、TestTreeSitterMemoryRelease(44L)、TreeSitterNativeAvailableCondition(32L) | 无 | 名义有，实无（§2） |
| nop-code-lang-typescript | 7 | 571 | TestTreeSitterByteOffset(65L)、TestTreeSitterMemoryRelease(49L)、TestTypeScriptCallExtraction(54L)、TestTypeScriptCallQualifiedName(138L)、TestTypeScriptCodeFileAnalyzer(193L)、TestTypeScriptLanguageAdapter(40L)、TreeSitterNativeAvailableCondition(32L) | 无 | 同上 |
| nop-code-lang-go | 6 | 311 | GoTreeDumpProbe(33L)、TestGoCodeFileAnalyzer(130L)、TestGoImportResolver(55L)、TestGoLanguageAdapter(31L)、TestGoTreeSitterMemoryRelease(41L)、TreeSitterNativeAvailableCondition(21L) | 无 | 同上 |
| nop-code-lang-rust | 5 | 299 | TestRustCodeFileAnalyzer(172L)、TestRustImportResolver(42L)、TestRustLanguageAdapter(31L)、TestRustTreeSitterMemoryRelease(36L)、TreeSitterNativeAvailableCondition(18L) | 无 | 同上 |
| nop-code-lang-csharp | 5 | 301 | TestCSharpCodeFileAnalyzer(174L)、TestCSharpImportResolver(42L)、TestCSharpLanguageAdapter(31L)、TestCSharpTreeSitterMemoryRelease(36L)、TreeSitterNativeAvailableCondition(18L) | 无 | 同上 |
| nop-code-api | 0 | — | **无 src/test 目录** | — | — |
| nop-code-dao | 0 | — | **无 src/test 目录** | — | — |
| nop-code-app | 0 | — | **无 src/test 目录** | — | — |
| **合计（单元内）** | **35** | **2,615** | | | |

单元外直接锚点（登记，不在单元内计数）：
- `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestFrameworkPatternDslEquivalence.java` —— C1 载体 I-04 `ConfigDrivenRouteConvention` 的唯一构造点测试（生产 0 构造点的对照面）。
- `nop-tools/nop-treesitter` 存在 src/test（85 个 java 文件）——CL-003 载体 `Language` 的归属单元自有测试面，本单元不盘点其内容（C1 已裁定该载体"归 nop-treesitter 单元自身审计"）。
- api/dao/app 三模块零测试与其 src/main 形态一致：api 为 XGEN 契约接口 + DTO/Bean 数据载体，dao 为空壳实体/标记接口，app 仅 `NopCodeApplication`（35L 启动器），均无行为逻辑可测（C1/C6 对该三模块逐项判定安全）。

## 2. 断言形态扫描（grep 口径 + 命中数，可复现）

工作目录 = 仓库根；范围 = 6 个 lang 模块 `src/test`：

1. **并发关键字**：`grep -rn -cE "Thread|Concurrent|Executor|CountDownLatch|Atomic|parallel|synchronized|CompletableFuture" <6 模块 src/test>` → 命中 3 文件：TestJavaFileAnalyzerConcurrent(8)、TestJavaSymbolConversion(2)、TestJavaCodeFileAnalyzer(2)。后两文件为**误报**——命中的是被分析 fixture 源码中的 `synchronized void syncMethod()` 文本及其 `isSynchronizedFlag` 断言（Java 修饰符语义测试，非并发测试）。
2. **内存/释放断言**：`grep -rn "freeMemory\|totalMemory\|gc()\|WeakReference\|outOfMemory" <6 模块 src/test>` → **0 命中**。五个 `Test*TreeSitterMemoryRelease` 命名含 "MemoryRelease" 但实为**冒烟测试**（连续 15 次 analyze + 大源文件 analyze 断言无错不空，如 `TestTreeSitterMemoryRelease.java:22-43`），无任何内存度量断言。
3. **实例契约断言**：`grep -rn -c "assertNotSame\|assertSame" <6 模块 src/test>` → **0 命中**；`getFileAnalyzer` 在测试中恰 **6 命中**（每 adapter 测试 1 处，仅 `assertInstanceOf(XxxCodeFileAnalyzer.class, adapter.getFileAnalyzer())`，单次调用，未断言两次调用返回不同实例）。
4. **字节偏移断言**：`TestTreeSitterByteOffset` 存在于 python、typescript 2/5 个 tree-sitter 模块（go/rust/csharp 无对应物）。
5. 唯一真并发测试内容：`TestJavaFileAnalyzerConcurrent.java:26-53`——**单个共享 JavaFileAnalyzer 实例** + 10 线程 `ExecutorService` + `CountDownLatch`，断言零 Throwable 且 symbols 非空（错误冒烟口径，无交错序列断言）。

## 3. CL 线索测试面现状（5 条，供未来立案时引用）

| CL | 载体（file:line 见 C1/C6） | 现有测试锚 | 保护现状与缺口 |
|---|---|---|---|
| CL-001（JavaFileAnalyzer 构造器重建 type-solver 栈，→C9） | `nop-code-lang-java/.../JavaFileAnalyzer.java:87-95,140` | 正确性锚 4 个：TestJavaCodeFileAnalyzer、TestJavaSymbolConversion、TestJavaFileConversion、TestProvenanceAndSpringRoutes；**并发回归面已存在**：TestJavaFileAnalyzerConcurrent | 正确性与"共享实例并发 analyze 不炸"均有锚；无分配量断言。若 C9 立案并将 type-solver 栈提为共享单例，TestJavaFileAnalyzerConcurrent 即现成的线程安全金丝雀（该测试本就按共享实例设计），无需新增测试即可立案 |
| CL-002（Go/Rust/C# ImportResolver 线性扫描，→C9） | `GoImportResolver.java:70-79` 等 | **三语言锚齐备**：TestGoImportResolver（4 @Test，C10 复核勘误：原记 3，`languageKeyIsGo`/`quotedImportPathExtractedAndResolved`/`barePathAccepted`/`unmatchedPathUnresolved` 共 4 个）、TestRustImportResolver（3）、TestCSharpImportResolver（3），断言 resolved/unresolved 路径正确 | 若按 F3 同族三连修复（前缀 Map 预索引），3 个测试文件即现成回归锚，直接引用；无复杂度断言（合理，性能归 C9 量化） |
| CL-003（nop-treesitter `Language` 良性竞争，跨单元 watch-only） | `nop-tools/nop-treesitter/.../Language.java:762-784` | 单元内**无专属测试面**（lang-* 测试仅经 `TS_LANGUAGE` 间接消费，不断言 Language 内部）；归属单元 nop-treesitter 自有 85 个测试文件（存在性登记，未盘点） | 已裁定良性、无需行动；未来若 nop-treesitter 单元开 CRA，以 C1 复核节"已判定良性"结论为前置，无需本单元补测试 |
| CL-101（CSharpCodeFileAnalyzer 实例字段 per-call 状态，watch-only） | `CSharpCodeFileAnalyzer.java:41-42,69,76` | 正确性锚 1 个：TestCSharpCodeFileAnalyzer（单线程单调用）；**并发锚为 0** | **全单元唯一并发测试在 java 模块，CSharp 无任何并发测试**——CL-101 依赖的两条结构性契约今日全部无测试锁定（见 §4）。安全现状 = 契约约束而非测试保护；若发生 analyzer 缓存/并行复用接线，无测试会失败 |
| CL-102（nodeText 每调用全文重编码 UTF-8，→C9） | Python/CSharp/Go/Rust 四分析器 nodeText 助手 | 正确性锚：四语言 TestXxxCodeFileAnalyzer + `TestTreeSitterByteOffset`（仅 python/typescript） | 若按 TS 参照（analyze 入口一次编码、byte[] 下传）修复，**字节偏移是首要回归风险面**：go/rust/csharp 缺 byte-offset 专项断言（python 有），建议修复前置补齐后再动（见 §5） |

## 4. 结构性契约（C1 watch-only 两项）的测试锚现状

- **契约① analyzer 每文件新建**（CL-101 今日安全的根依据，8 获取点 + 6 adapter 工厂）：**测试锚缺失**。6 个 `TestXxxLanguageAdapter` 各仅调用 `getFileAnalyzer()` 一次做 `assertInstanceOf`；`assertSame/assertNotSame` 全测试面 0 命中。若任何 adapter 改为缓存单例，现有 35 个测试无一失败——契约①目前**仅由实现现状保证，无回归保护**。
- **契约② registry 装配期一次写、运行期只读**：registry 本体在单元外 nop-code-core，单元内无直接测试；契约若被打破（运行期热注册）属于 nop-code-core 单元的测试责任面，本单元登记不立案。

## 5. 零缺陷面声明与 watch-only 建议

**零缺陷面声明**：C1 与 C6 两维最终保留项均为 0，本 C10 盘点形同——不产生发现条目（立案数=0），不修改产品代码。测试基建现状与两维"零缺陷"结论相容：现有 35 个测试文件全部为正确性/语义断言（含 1 个共享实例并发冒烟），单元内不存在"测试已暴露但未修"的并发或资源问题。

**watch-only 建议（未来接线/重构/立案时的测试先置条件，均不要求本轮行动）**：

1. **W-01（对应 CL-101 / 契约①）**：任何"analyzer 缓存化 / 增量索引并行化"接线前，先置测试：(a) 各 adapter 双调用 `getFileAnalyzer()` 断言 `assertNotSame`（锁定每文件新建契约，成本≈每语言 2 行）；(b) CSharp（及同族）双文件交错并发 analyze 断言符号文本不串（现有 java 共享实例测试不能替代——CSharp 的 `source`/`lastNamespace` 在共享实例下即竞争，java 版反而证明 java 分析器无此形态）。今日两者皆缺，接线失败将是静默数据损坏。
2. **W-02（对应 CL-102）**：C9 若立案并以 byte[] 下传方式修复，go/rust/csharp 先补 `TestTreeSitterByteOffset` 对应物（python/typescript 已有参照实现可复制）再动 nodeText；四语言 TestXxxCodeFileAnalyzer 作正确性回归锚。
3. **W-03（对应 CL-001）**：C9 若立案并共享 type-solver 栈，TestJavaFileAnalyzerConcurrent 为既有线程安全回归面，无需新增测试；如需量化收益另立基准测试（归 C9 口径，本报告不展开）。
4. **W-04（对应 CL-002）**：修复直接以 3 个既有 ImportResolver 测试为回归锚；无测试先置条件。
5. **W-05（命名诚实性，观察项）**：五个 `Test*TreeSitterMemoryRelease` 名实不符（无内存断言，实为重复/大源冒烟）。C6 已裁定 compat 桥 `delete()` 为文档化 no-op、无释放义务，现状冒烟口径可接受；若未来 tree-sitter 资源语义变化（如恢复 JNI 配对语义），先把这些测试升级为真实断言或更名，避免假保护。

---

## 6. 自我核对

- 与 C1 复核节"最终保留项：0（watch-only 备注 3 项不属保留缺陷）"及 C6"最终保留项：0"一致；本报告立案数=0。
- §1 清单与 §3/§4 引用的全部测试路径经 `find` 实存核对（35 个单元内文件 + TestFrameworkPatternDslEquivalence）；file:line 引用经读取核实（TestJavaFileAnalyzerConcurrent.java:26-53、TestTreeSitterMemoryRelease.java:22-43、各 ImportResolver/Adapter 测试）。
- grep 命中数均附于 §2（并发 3 文件、内存断言 0、assertSame/assertNotSame 0、getFileAnalyzer 6、ByteOffset 2 模块）。
- 盲审声明有效：未读取 plans/bugs/lessons/backlog 及两份输入之外的 audits 文件；未修改任何产品代码与两维文件。

（C10 完 · 盲审 · 测试基建盘点形态 · 零缺陷面）

---

## 复核结论（C10 独立复核）

- **复核方式**：独立复核子代理对 C10 盘点逐项抽样重演（find/grep/读取，工作目录 = 仓库根），未读取 plans/bugs/lessons/backlog，未触碰产品代码与 C1/C6 两维文件。
- **完整性核对**：C1 复核节"最终保留项：0"、C6"最终保留项：0"与本报告"零缺陷面、立案数=0"一致；§3 的 5 条 CL（CL-001/002/003/101/102）测试面现状逐条在案，载体归属与两维 CL 编号一一对应。
- **抽样清单（7 项）与结果**：
  1. **测试文件计数复算（全 6 模块）** — 成立。逐模块 `find -name "*.java" | wc -l` 与 `cat | wc -l` 实测：java 6f/714L、python 6f/419L、typescript 7f/571L、go 6f/311L、rust 5f/299L、csharp 5f/301L，合计 **35f/2,615L**，与 §1 表逐格吻合（含每个文件的行数标注）。
  2. **TestJavaFileAnalyzerConcurrent 定性重演** — 成立。全文 54 行核读：单共享实例（:27 `new JavaFileAnalyzer()` 一次）、10 线程（:28-29 `newFixedThreadPool(10)`）、`CountDownLatch`（:30）、`CopyOnWriteArrayList` 收集 Throwable、`assertTrue(errors.isEmpty())`（:52）+ `assertNotNull`/symbols 非空（:39-40）；无任何交错序列断言——"错误冒烟口径"定性准确，测试体 :26-53 引用无误。
  3. **`Test*TreeSitterMemoryRelease` 名实不符判定重演** — 成立。python 版 :22-29 连续 15 次 analyze（`assertNotNull` + symbols 非空）、:31-43 大源文件（100 类）analyze，全部断言仅无错不空；六个模块 src/test grep `freeMemory|totalMemory|gc()|WeakReference|outOfMemory` 复跑 **0 命中**（exit 1）。
  4. **`assertSame/assertNotSame` 0 命中复跑** — 成立。6 个 lang src/test 复跑 exit 1、0 命中；`getFileAnalyzer` 恰 **6 命中**（TestJavaLanguageAdapter.java:27 等六个 adapter 测试各 1 处 `assertInstanceOf(..., adapter.getFileAnalyzer())`，均为单次调用，无双调用不同实例断言）——§4 契约①"测试锚缺失"成立。
  5. **api/dao/app 无 src/test 确认** — 成立。`nop-code-api`、`nop-code-dao`、`nop-code-app` 三模块下均不存在 `src/test` 目录。
  6. **并发关键字扫描口径复跑** — 成立。命中恰 3 文件且逐文件计数 TestJavaFileAnalyzerConcurrent(8)、TestJavaSymbolConversion(2)、TestJavaCodeFileAnalyzer(2)；后两文件命中确为被测 fixture 文本 `synchronized void syncMethod()` 及 `isSynchronizedFlag` 断言（TestJavaSymbolConversion.java:179,186、TestJavaCodeFileAnalyzer.java:27,105），"误报"归因正确。附带核实：csharp src/test 并发关键字 0 命中，支撑 §3 CL-101"并发锚为 0"；`TestTreeSitterByteOffset` 仅 python/typescript 存在；nop-treesitter src/test 实测 85 个 java 文件；TestFrameworkPatternDslEquivalence.java 实存于 nop-code-service。
  7. **CL-002 ImportResolver 测试计数** — **发现 1 处错误并已修正**：TestGoImportResolver 实为 **4 @Test**（:15/:20/:34/:45，`languageKeyIsGo`/`quotedImportPathExtractedAndResolved`/`barePathAccepted`/`unmatchedPathUnresolved`），原报告记"3 @Test"系计数笔误；Rust/CSharp 各 3 @Test 无误（:15/:20/:32）。该错误不影响 CL-002 行实质结论（三语言锚齐备、resolved/unresolved 断言俱在），已在 §3 表内就地勘误。
- **勘误汇总**：1 处（CL-002 行 Go @Test 计数 3→4，见上第 7 条）。其余抽样 6 项全部与原文一致。
- **整体判定**：**C10 盘点报告可信**。零缺陷面声明与两维"最终保留项=0"相容；35 文件/2,615 行计数、并发/内存/实例契约三类 grep 口径、5 条 CL 测试面现状与 5 条 watch-only 建议经抽样重演全部成立（1 处计数笔误已修正）。

（C10 复核完 · 独立复核 · 抽样 7 项 · 勘误 1 处已就地修正 · 维持零缺陷面结论）
