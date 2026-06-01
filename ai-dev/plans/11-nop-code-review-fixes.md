# nop-code 审查问题修正计划

> Plan Status: cancelled
> Cancellation Reason: Superseded by Plans 55, 58, 59, 69-95
> Created: 2026-05-09
> Owner: sisyphus
> Source: 5-role team review (architect / code-quality / design-pattern / test-coverage / performance)

## Goals

修正 nop-code 模块经 5 角色团队审查发现的真实问题，使代码符合 Nop 平台规范、消除正确性风险、补全接口契约、提升代码质量。

## Non-Goals

- 不重构整体架构（分层设计已合理）
- 不新增功能（如 Pipeline 模式、新语言支持）
- 不修改 ORM 模型（当前设计合理）
- 不调整分析器实例化策略（无状态微服务下每次 new 是正确的，已与用户确认）
- 不修改 CodeLanguage 枚举为 String（当前 4 种语言规模下枚举方式可接受）
- 不拆分 ICodeIndexService 接口（影响面广，需独立计划）

## Current Baseline

nop-code 模块 11 个子模块，98 个 Java 源文件。审查综合评分 7.1/10。架构骨架设计优秀（策略+适配器+注册中心），但存在 Core 层异常处理不规范、手写 JSON 解析器、资源泄漏风险、接口契约未落地、代码重复等具体问题。

## Scope Review — 审查发现的问题过滤

基于审查报告 + 用户反馈，对每个发现进行 in-scope / out-of-scope / downgrade 裁定：

| # | 问题 | 原优先级 | 裁定 | 理由 |
|---|------|---------|------|------|
| C1 | ManifestStore 手写 JSON 解析器 | P0 | **In-Scope P0** | 正确性风险 + 必须用平台 JsonTool |
| C2 | IProjectAnalyzer 返回 Object | P0 | **In-Scope P0** | 接口契约必须落地，用户明确要求纳入 |
| C3 | InputStream 资源泄漏 | P0 | **In-Scope P0** | 真实资源泄漏风险 |
| C4 | RuntimeException 而非 NopException (5处) | P1 | **In-Scope P1** | 违反 Nop 异常处理规范 |
| C5 | sha256Hex() 重复 3 次 | P1 | **In-Scope P1** | 消除代码重复 |
| C6 | countLines() 重复 3 次 | P1 | **In-Scope P1** | 消除代码重复 |
| C7 | CodeFileAnalysisResult 保留 sourceCode | P1 | **Out-of-Scope** | 功能变更，需要评估上层调用方影响 |
| C8 | 缺少 AbstractCodeFileAnalyzer 模板基类 | P1 | **Out-of-Scope** | 重构，风险大，非修正 |
| C9 | 3 个空标记接口 + 实现类未 implement | P1 | **In-Scope P1** | 接口契约必须落地，用户明确要求 |
| C10 | ICodeIndexService 接口过大 | P2 | **Out-of-Scope** | 影响面广，需独立计划 |
| C11 | CallGraph/SymbolTable 线程安全 | P2 | **Out-of-Scope** | 上层已通过串行收集保证安全 |
| C12 | PathFingerprintStore.deleteByPaths O(N*M) | P2 | **In-Scope P2** | 简单修复，一行代码 |
| C13 | LanguageAdapterRegistry 线性扫描 | P2 | **Out-of-Scope** | 当前 3 种语言扫描开销可忽略 |
| C14 | 边界条件测试缺失 | P2 | **In-Scope P2** | 补充关键边界测试 |
| C15 | 分析器实例未复用 | ~~P1~~ | **Out-of-Scope** | 无状态微服务下每次 new 是正确做法（用户确认） |
| C16 | CodeIndexService 硬编码 JavaLanguageAdapter | Major | **Out-of-Scope** | 需要调整 IoC 配置，影响启动流程 |
| C17 | entity-model 手动转换重复 | Minor | **Out-of-Scope** | 可用 MapTool 但属于重构 |
| C18 | NopCodeFile.sourceCode 字段类型 | Minor | **Out-of-Scope** | ORM 模型变更 |
| C19 | InMemoryFingerprintStore ConcurrentHashMap 不一致 | Minor | **In-Scope P2** | 简单修复 |

## Execution Plan

### Phase 1: P0 修复（正确性 + 资源安全 + 接口契约）

#### 1.1 替换 ManifestStore 手写 JSON 解析器为 JsonTool

**Files**:
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/ManifestStore.java`

**What**: 删除手写的 `findMatchingBrace()`、`parseJsonArray()`、`extractStringValue()`、`extractLongValue()` 等方法（~200行），替换为使用 Nop 平台内置的 `JsonTool`。

**平台规范**: `docs-for-ai/02-core-guides/dto-json-and-message-beans.md` 明确规定：**不要默认散用第三方 JSON 库。当前仓库的统一入口是 `JsonTool`。**
- `JsonTool.stringify(obj)` — 序列化
- `JsonTool.parseBeanFromText(text, Target.class)` — 反序列化

**How**:
1. 读取当前 ManifestStore.java 完整实现
2. 识别 `FileFingerprint` 数据类，确保它有无参构造器（JsonTool 需要）
3. `save()` 方法：`JsonTool.stringify(fingerprints)` 替换手写序列化
4. `load()` 方法：`JsonTool.parseBeanFromText(text, List.class)` 或使用 TypeReference 替换手写解析
5. 保持文件格式兼容（新代码能读取旧 manifest）
6. 运行 `TestManifestStore` 验证

**QA**: `TestManifestStore` 全部通过；`grep -r "findMatchingBrace\|parseJsonArray\|extractStringValue" nop-code/` 返回 0 结果

#### 1.2 修复 InputStream 资源泄漏

**Files**:
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/IncrementalDetector.java`

**What**: 将 `computeFingerprint(IResource)` 和 `detectResourceChanges` 中的 `getInputStream()` 调用改为 try-with-resources；`Files.newInputStream(file)` 也改为 try-with-resources

**How**:
1. 定位所有 `resource.getInputStream()` 和 `Files.newInputStream()` 调用
2. 改为 `try (InputStream is = ...) { return computeSha256FromStream(is); }`

**QA**: `TestIncrementalDetector` 全部通过

#### 1.3 补全 IProjectAnalyzer 接口 + 让 ProjectAnalyzer 实现

**Files**:
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/IProjectAnalyzer.java`
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`

**What**: 将 `IProjectAnalyzer` 的 `Object` 返回类型替换为 `ProjectAnalysisResult`，让 `ProjectAnalyzer implements IProjectAnalyzer`

**How**:
1. 将 `ProjectAnalyzer.ProjectAnalysisResult` 提取为独立顶层类（或保持为内部类但在接口中引用）
2. 修改 `IProjectAnalyzer` 接口：
   ```java
   ProjectAnalysisResult analyzeProject(Path projectRoot);
   ProjectAnalysisResult analyzeProject(Path projectRoot, Set<CodeLanguage> languages);
   ProjectAnalysisResult analyzeIncremental(Path projectRoot, List<String> changedFilePaths);
   ```
3. `ProjectAnalyzer` 声明 `implements IProjectAnalyzer`
4. 检查所有调用方（`CodeIndexService`）是否需要调整（`Object` → `ProjectAnalysisResult`，更有类型安全）

**QA**: 编译通过；`TestProjectAnalyzerWithStore` 和 `TestProjectAnalyzerIncremental` 通过

### Phase 2: P1 修复（规范遵循 + 接口契约 + 代码质量）

#### 2.1 RuntimeException → NopException + ErrorCode

**Files**:
- NEW: `nop-code/nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java`（core 层的 ErrorCode 定义）
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/IncrementalDetector.java`
- `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java`（service 层的补充）

**What**: 将 Core 层 5 处 `RuntimeException` 替换为 `NopException` + 预定义 ErrorCode

**依赖分析**: core 模块已依赖 `nop-api-core`（含 NopException + ErrorCode），可直接在 core 中定义 ErrorCode 接口。

**How**:
1. 在 core 模块创建 `NopCodeCoreErrors.java`：
   ```java
   public interface NopCodeCoreErrors {
       String ARG_PATH = "path";
       ErrorCode ERR_CODE_ANALYZE_PROJECT_FAILED = ErrorCode.define(
           "nop.err.code.analyze-project-failed",
           "项目分析失败:{path}", ARG_PATH);
       ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE = ErrorCode.define(
           "nop.err.code.digest-not-available",
           "SHA-256摘要算法不可用");
   }
   ```
2. `ProjectAnalyzer.java:331,342` → `throw new NopException(NopCodeCoreErrors.ERR_CODE_ANALYZE_PROJECT_FAILED).param(ARG_PATH, projectRoot).cause(e)`
3. `IncrementalDetector.java:212` → `throw new NopException(NopCodeCoreErrors.ERR_CODE_DIGEST_NOT_AVAILABLE).cause(e)`
4. `ProjectAnalyzer.java:551` → 同上（sha256Hex 中的 RuntimeException）
5. `CodeIndexService.java:1216` → 使用 `NopCodeCoreErrors.ERR_CODE_DIGEST_NOT_AVAILABLE`

**QA**: `grep -r "new RuntimeException" nop-code/nop-code-core/` 返回 0 结果

#### 2.2 补全空标记接口 + 实现类 implement

**Files**:
- `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java`
- `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java`
- `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/entrypoint/EntryPointScorer.java`

**What**: 从实现类的 public 方法中提取核心方法签名到接口，实现类 `implements` 对应接口

**How**:

**IImpactAnalyzer** — 提取核心分析方法：
```java
public interface IImpactAnalyzer {
    ImpactResult analyzeImpact(String symbolId, CallGraph callGraph, SymbolTable symbolTable, int maxDepth);
    ImpactResult analyzeImpact(String symbolId, CallGraph callGraph, SymbolTable symbolTable, ImpactConfig config);
}
```

**ICommunityDetector** — 提取核心检测方法：
```java
public interface ICommunityDetector {
    CommunityDetectionResult detectCommunities(CallGraph callGraph, SymbolTable symbolTable);
    CommunityDetectionResult detectCommunities(CallGraph callGraph, SymbolTable symbolTable, CommunityConfig config);
}
```

**IEntryPointScorer** — 提取核心评分方法：
```java
public interface IEntryPointScorer {
    List<EntryPointScore> scoreEntryPoints(CallGraph callGraph, SymbolTable symbolTable);
    List<EntryPointScore> scoreEntryPoints(CallGraph callGraph, SymbolTable symbolTable, ScoringConfig config);
}
```

然后三个实现类加上 `implements IXxx`。

**QA**: 编译通过；三个接口的测试（TestImpactAnalyzer、TestCommunityDetector、TestEntryPointScorer）全部通过

#### 2.3 提取 DigestHelper 工具类消除 sha256Hex 重复

**Files**:
- NEW: `nop-code/nop-code-core/src/main/java/io/nop/code/core/util/DigestHelper.java`
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/IncrementalDetector.java`
- `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`

**What**: 提取 `sha256Hex(byte[])`、`sha256Hex(InputStream)`、`bytesToHex(byte[])` 到 core 模块共享工具类。service 模块依赖 core，可直接使用。

**How**:
1. 创建 `DigestHelper.java`，包含：
   - `static String sha256Hex(byte[] data)` — 计算 SHA-256 并返回十六进制字符串
   - `static String sha256Hex(InputStream is)` — 从流计算 SHA-256
   - `static String bytesToHex(byte[] bytes)` — 字节数组转十六进制
2. 从三个文件中删除重复实现，统一调用 `DigestHelper.sha256Hex()`

**QA**: 编译通过；`TestIncrementalDetector` 通过；`grep -r "HEX_CHARS\|bytesToHex" nop-code/ --include="*.java" | grep -v DigestHelper` 返回 0 结果

#### 2.4 提取 countLines 到 ICodeFileAnalyzer default 方法

**Files**:
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ICodeFileAnalyzer.java`
- `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java`
- `nop-code/nop-code-lang-python/src/main/java/io/nop/code/lang/python/PythonCodeFileAnalyzer.java`
- `nop-code/nop-code-lang-typescript/src/main/java/io/nop/code/lang/typescript/analyzer/TypeScriptCodeFileAnalyzer.java`

**What**: 将三个分析器中完全相同的 `countLines(String source)` 方法提取到 `ICodeFileAnalyzer` 的 `default` 方法

**How**:
1. 在 `ICodeFileAnalyzer` 中添加：
   ```java
   default int countLines(String source) {
       if (source == null || source.isEmpty()) return 0;
       return source.split("\n", -1).length;
   }
   ```
2. 从三个分析器中删除各自的 `countLines()` 方法

**QA**: 编译通过；三语言分析器测试全部通过

### Phase 3: P2 修复（简单改进 + 测试补充）

#### 3.1 PathFingerprintStore.deleteByPaths 性能修复

**Files**:
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/PathFingerprintStore.java`

**How**: `Set<String> pathSet = new HashSet<>(filePaths);` 然后 `pathSet.contains()` 替代 `filePaths.contains()`

**QA**: `TestPathFingerprintStore` 通过

#### 3.2 InMemoryFingerprintStore 一致性修复

**Files**:
- `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/InMemoryFingerprintStore.java`

**How**: `saveFingerprints` 中已经使用 `store.put(indexId, newMap)` 原子替换，没问题。但 `loadFingerprints` 的 `new ArrayList<>(map.values())` 在无并发写入时是安全的。检查后如果确认不需要 ConcurrentHashMap 则降级为 HashMap 保持一致性。

**QA**: `TestInMemoryFingerprintStore` 通过

#### 3.3 补充关键边界测试

**Files**:
- NEW: nop-code/nop-code-lang-java/src/test/java/io/nop/code/lang/java/analyzer/TestJavaCodeFileAnalyzerEdgeCases.java
- NEW: nop-code/nop-code-lang-typescript/src/test/java/io/nop/code/lang/typescript/analyzer/TestTypeScriptCodeFileAnalyzerEdgeCases.java
- NEW: `nop-code/nop-code-lang-python/src/test/java/io/nop/code/lang/python/TestPythonCodeFileAnalyzerEdgeCases.java`

**What**: 为每个语言分析器补充空输入和语法错误输入的测试

**How**:
1. 每个测试类包含：
   - `testAnalyzeEmptyString()` — 空字符串输入
   - `testAnalyzeSyntaxError()` — 语法错误的源码（如不匹配的大括号）
2. 断言：不抛出未捕获异常，返回非 null 结果

**QA**: 新测试全部通过

### Phase 4: 验证

#### 4.1 全量构建验证
`./mvnw clean install -DskipTests -T 1C -pl nop-code -am`

#### 4.2 测试验证
`./mvnw test -pl nop-code`

## Closure Gates

- [ ] Phase 1.1: ManifestStore 使用 JsonTool，无手写 JSON 解析代码
- [ ] Phase 1.2: 无 InputStream 泄漏风险（所有 getInputStream/newInputStream 在 try-with-resources 中）
- [ ] Phase 1.3: IProjectAnalyzer 返回 ProjectAnalysisResult，ProjectAnalyzer implements IProjectAnalyzer
- [ ] Phase 2.1: Core 层无 RuntimeException（全部替换为 NopException + ErrorCode）
- [ ] Phase 2.2: 三个空标记接口已补全方法签名，实现类已 implement
- [ ] Phase 2.3: sha256Hex/bytesToHex 统一到 DigestHelper，三处重复消除
- [ ] Phase 2.4: countLines 统一到 ICodeFileAnalyzer default 方法，三处重复消除
- [ ] Phase 3.1-3.3: deleteByPaths O(N) 修复、边界测试通过
- [ ] Phase 4: `./mvnw clean install -pl nop-code -am` BUILD SUCCESS + 全部测试通过
- [ ] `grep -r "new RuntimeException" nop-code/nop-code-core/ --include="*.java"` 返回 0 结果
- [ ] `grep -r "findMatchingBrace\|parseJsonArray\|extractStringValue" nop-code/ --include="*.java"` 返回 0 结果

## Cancellation Note

Plan 11 描述的审查问题修正工作已由 Plans 55（深度审计 78 findings）、58（P0 bug 修复）、59（语义边模型）、69-95（8 轮审计修复）全面覆盖。本计划被取消。
