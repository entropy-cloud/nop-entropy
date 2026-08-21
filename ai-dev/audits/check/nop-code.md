# nop-code 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-code（core/service/api/flow/lang 为主）
- 文件数: 实测 src/main/java 共 195 个（api 64、core 43、dao 35、service 32、flow 10、lang-java 4、lang-python 3、lang-typescript 3、app 1；任务说明的 281 与实测不符，按实测口径）
- 覆盖范围声明:
  - 逐行深读: core 的 graph(analyzer/incremental/entrypoint/util 关键类)、flow 全部 10 个文件、service 的 CodeIndexService/CodeGraphService/CodeQueryService/CodeSearchService/CodeCacheManager/OrmFingerprintStore/NopCodeIndexBizModel/NopCodeConfigs/NopCodeErrors、graph 子包全部 7 个、lang 三模块全部 10 个文件、core 的错误码/工具类/导入解析器（Java/Python）、beans.xml 注册文件（app-service.beans.xml、_lang-java.beans.xml）。
  - 抽查/跳过: api 模块的 64 个 Input/Output Bean、DTO 与 crud 接口为纯数据/生成式接口，无逻辑，仅抽查；dao 实体继承 `_gen` 生成基类（NopCodeFile 等），属生成产物仅看头部；`_` 前缀文件与 target/ 未读；测试代码不在范围。
  - 依赖的图算法库（io.nop.graph.algorithm 的 BetweennessCentrality/LeidenDetector/ImpactPropagator/GraphDiffer/GraphExporter）位于 nop-code 之外的模块，不在本次范围。
  - 检查方法: 先 grep（空 catch、bare RuntimeException、printStackTrace、synchronized/并发结构、文件遍历、外部进程、private @Inject）→ 每个命中点 Read 上下文验证 → 重点链路（索引构建、增量索引、调用图/依赖图、变更影响、死代码、语言适配器）深读。grep 未发现空 catch、bare RuntimeException、printStackTrace；未发现 private 字段 @Inject（D7 该项合规）。
  - 对 tree-sitter `getChildByFieldName` 的空值行为做了实际运行验证（返回非 null 的 isNull 节点），排除了 TypeScriptCodeFileAnalyzer `!typeNode.isNull()` 的 NPE 疑似误报。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 6 |
| P3 | 8 |

## 发现列表

### [P0] tarjanSCC 迭代式 Tarjan 实现错误，findCycles 在常见图上返回错误环路

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:929-974`
- **维度**: D1
- **证据**:
```java
if (!returning && !nodeIndex.containsKey(v)) { /* 初始化 v */ }
...
for (int i = edgeIdx; i < neighbors.size(); i++) {
    String w = neighbors.get(i);
    if (!nodeIndex.containsKey(w)) {
        callStack.push(new Object[]{v, i + 1, true});
        callStack.push(new Object[]{w, 0, false});
        pushedChild = true;
        break;
    } else if (onStack.contains(w)) { lowLink.put(v, Math.min(lowLink.get(v), nodeIndex.get(w))); }
}
if (!pushedChild && returning) {
    if (edgeIdx > 0) {
        String w = neighbors.get(edgeIdx - 1);
        lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));
    }
    if (lowLink.get(v).equals(nodeIndex.get(v))) { /* 出栈弹 SCC */ }
}
```
- **现状**: 两处算法缺陷。(1) 从子节点回溯时，若当前帧还有后续未访问邻居（pushedChild=true），对刚完成子树的 `lowLink(w)` 传播被跳过，且 SCC 出栈块也只在 `returning` 帧执行，父帧的 lowLink 只会与"最后一个孩子"合并；(2) 无后续孩子/叶子节点的原始帧（returning=false）永远不执行 SCC 出栈，节点滞留在栈和 onStack 中，被祖先的 SCC 出栈错误吞入。手工推演验证：对图 `S→P, P→C1, P→C2, C1→S`（正确 SCC 为 {S,P,C1} 和 {C2}），该实现输出 `[{C2,C1,P}, {S}]`——C2（非环节点）被报成环成员，真正的环 {S,P,C1} 被拆散。依赖图中"一个文件 import 两个文件、其中一条链路回指"是极常见形态。
- **风险**: `findCycles` 通过 `@BizQuery` 对外暴露（NopCodeIndexBizModel.findCycles），在常见依赖图上静默返回错误环路数据（把无环节点标为环、漏报真环），误导循环依赖治理。
- **建议**: 回溯帧恢复时立即与 `edgeIdx-1` 对应子节点合并 lowLink（放在 for 循环前的 `if (returning && edgeIdx > 0)` 中），且 SCC 出栈条件去掉 `returning` 约束（原始帧也应出栈）；或改用递归 Tarjan/Kosaraju 并配独立栈。补一个含"多子节点+回边"图的单测。
- **误报排除**: 已按代码逐帧手工推演两个反例图并得出与正确 Tarjan 不同的结果；非并发或输入异常引起，纯算法逻辑错误。

### [P1] analyzeChanges 用 VFS 形式的 rootPath 作为 git 工作目录，变更影响分析永远返回空结果且异常被吞

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1762-1769`；`nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:136-142, 211-218`
- **维度**: D1、D4
- **证据**:
```java
// CodeIndexService.analyzeChanges
String workingDirectory = null;
NopCodeIndex indexEntity = indexDao.getEntityById(indexId);
if (indexEntity != null) {
    workingDirectory = indexEntity.getRootPath();   // 存的是 resolveVfsPath 的结果，如 "file:/abs/repo"
}
return analyzer.analyzeChanges(..., workingDirectory);

// ChangeAnalyzer.parseGitDiff
ProcessBuilder pb = new ProcessBuilder("git", "diff", baseline + ".." + target, "--unified=0");
if (workingDirectory != null) {
    pb.directory(new java.io.File(workingDirectory));  // File("file:/...") 非法目录
}
...
} catch (IOException e) {
    LOG.warn("Failed to parse git diff output", e);
    return new DiffResult(result, truncated);          // 吞掉，返回空结果且 truncated=false
}
```
- **现状**: indexDirectory 写入的 rootPath 恒为 `resolveVfsPath` 输出的 `file:/...` 形式（indexFile 路径则为默认 "/"）。`new File("file:/...")` 不是有效目录：ProcessBuilder.start() 抛 IOException（目录不存在）或 git 无法 chdir；两种情况 stderr 均不匹配 diff 头解析，结果为空。IOException/InterruptedException 只 LOG.warn 后返回空 DiffResult 且 `truncated=false`，上层把它当作"无变更"正常返回。手工核对 `resolveVfsPath`（CodeIndexService.java:2080-2098）：非空输入恒定产出 `file:` 前缀，无例外路径。
- **风险**: `analyzeChanges`（@BizQuery 暴露）在正常索引（indexDirectory 创建）下永远返回 0 个变更文件、0 个受影响符号，且不带任何错误标记——影响分析静默失真，正是该模块最核心的风险场景。
- **建议**: 存库前把 rootPath 规范化为本地绝对路径（或读取时剥离 `file:` 前缀再交给 ProcessBuilder）；parseGitDiff 对 git 非零退出码/IOException 应抛 NopException 或至少设置 truncated=true，禁止把"失败"折叠成"无变更"。
- **误报排除**: 已核对 rootPath 写入链（indexDirectory→ensureIndexEntity/persistInSession 均写 resolvedPath）与 resolveVfsPath 全部分支，确认无产生合法本地路径的分支；异常吞点已读全文确认无其他补偿逻辑。

### [P1] 增量索引重分析文件时，其他文件指向它的调用/继承/使用边被删除且永不重建（符号 ID 每次随机）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1508-1555（1540-1543）`；`nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:664-665`
- **维度**: D1
- **证据**:
```java
// deleteFileRecords：删除跨文件引用（其它文件 B 调用本文件 A 符号的边）
deleteRelationalBySymbolIds(NopCodeCall.class, "calleeId", symbolIds);
deleteRelationalBySymbolIds(NopCodeCall.class, "callerId", symbolIds);
deleteRelationalBySymbolIds(NopCodeInheritance.class, "superTypeId", symbolIds);
deleteRelationalBySymbolIds(NopCodeUsage.class, "symbolId", symbolIds);

// JavaFileAnalyzer.createSymbolFromTypeDecl：每次分析生成全新随机 ID
symbol.setId(UUID.randomUUID().toString());
```
- **现状**: triggerIncrementalIndex 只重新分析变更文件；变更文件符号的新 ID 与旧 ID（UUID 随机）完全不同。未变更文件 B 中"调用 A 的方法"的 NopCodeCall 行以 A 的旧符号 ID 为 calleeId，被 deleteFileRecords 的跨文件清理删除，而 B 不会被重新分析、resolveQualifiedNamesToIds 也不处理 calls，这些边永久丢失。
- **风险**: 每次增量索引后调用图系统性丢边（callers 反向边首当其冲）：死代码检测把仍被调用的方法误报为 dead（confidence 0.95 档）、getImpactAnalysis upstream 漏报、FlowDetector 流程断链。索引越增量越失真，只能靠全量重建恢复。
- **建议**: 符号 ID 改为确定性派生（如 sha256(indexId:filePath:qualifiedName)），增量时按 qualifiedName 建立 旧ID→新ID 映射并改写未变更文件的相关边；或删除前把跨文件边按 qualifiedName 重新绑定到新符号。
- **误报排除**: 已通读 triggerIncrementalIndex 与 persistSingleFileInSession/saveFileResultInSession 全文，确认无任何跨文件边重建或 ID 迁移逻辑；三种语言分析器均使用 UUID.randomUUID 生成 ID（Python/TS 同）。

### [P1] 增量索引中单文件分析失败：记录先删后不补，且指纹照常保存，文件永久从索引消失

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:741-742, 756-775, 778-781`
- **维度**: D1、D4
- **证据**:
```java
deleteFileRecords(indexId, deletedFiles);
deleteFileRecords(indexId, changedFiles);      // 1) 先删旧记录（含符号/调用/依赖）
...
for (String changedFile : changedFiles) {
    try {
        ...
        CodeFileAnalysisResult fileResult = fileAnalyzer.analyze(relativePath, sourceCode);
        if (fileResult != null) { batchQueue.add(fileResult); }
    } catch (Exception e) {
        LOG.warn("Failed to re-analyze file: {}", changedFile, e);   // 2) 失败仅告警，事务继续提交
    }
}
batchQueue.flush();
List<FileFingerprint> newFingerprints = detector.computeResourceFingerprints(mappedResources); // 3) 全量资源指纹
store.saveFingerprints(indexId, newFingerprints);                    //    失败文件被标记为"当前状态"
```
- **现状**: 变更文件的旧记录先被删除；若该文件 analyze 抛异常或返回 null（Java 解析失败、读取 IO 错误），catch 吞掉异常后事务照常提交——旧记录已删、新记录未写。随后第 778 行基于全部当前资源（含失败文件）计算并保存指纹，该文件被记录为"当前内容已入库"，下次增量检测为 unchanged，永不重试。
- **风险**: 一个临时不可解析/读取失败的文件会永久、静默地从索引中消失（搜索、符号、调用图全部缺失），且无自愈路径，只能全量重建。
- **建议**: 分析失败的文件不纳入本次指纹保存（沿用其旧指纹），或记录失败列表并在结果/状态中暴露；analyze 失败时跳过 deleteFileRecords（先分析后删除-再写入）。
- **误报排除**: 已确认 catch(Exception) 在事务 lambda 内、异常不向外传播；已确认 newFingerprints 来自 mappedResources 全集而非成功集合。

### [P1] 删除文件记录时 NopCodeDependency 仅按 sourceFilePath 过滤，无 indexId 隔离，跨索引互相删数据

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1528`
- **维度**: D1
- **证据**:
```java
deleteEntitiesByFilter(NopCodeCall.class, "fileId", fileId);        // fileId 含 indexId 派生，安全
deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId);
deleteEntitiesByFilter(NopCodeUsage.class, "fileId", fileId);
deleteEntitiesByFilter(NopCodeDependency.class, "sourceFilePath", filePath);  // 无 indexId 条件
```
- **现状**: NopCodeDependency 行按 (indexId, sourceFilePath, targetFilePath) 保存（saveFileResultInSession:1439-1442），但删除时只按 `sourceFilePath` 全表过滤。索引路径经 buildPathMapper 剥离前缀后是相对路径（如 `com/Foo.java`），两个索引覆盖同一仓库/重叠目录时相对路径完全相同。
- **风险**: 对索引 A 增量更新/删除文件时，把索引 B 的依赖边一并删除；`diffGraph(baselineIndexId, targetIndexId)` 这一主打功能恰恰要求对同一仓库建两个并行索引，触发路径现实。依赖图/反向依赖/环检测随后在另一索引上静默缺数据。
- **建议**: 删除条件增加 `FilterBeans.eq("indexId", indexId)`；同类 helper（deleteRelationalBySymbolIds/deleteEntitiesByFilter 其它调用点）逐一核对是否需要 index 作用域。
- **误报排除**: 已读 deleteEntitiesByFilter 实现确认单字段过滤；已确认依赖行写入时带 indexId 且 filePath 为相对路径（buildPathMapper + analyzeProject 的路径剥离）。

### [P1] 本地路径防护失效：validateLocalPath 的绝对路径/allowedLocalRoot 检查对 "file:" 前缀路径永远不触发，且 allowedLocalRoot 从未接线

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2056-2078, 2080-2098, 294-298`
- **维度**: D5
- **证据**:
```java
private void validateLocalPath(String path) {
    if (path.contains("..")) throw ...;
    if (path.startsWith("/") || (path.length() >= 2 && path.charAt(1) == ':'))  // "file:/x" 两条件均不命中
        throw new NopException(ERR_CODE_INVALID_PATH)...;
    java.io.File localFile = new java.io.File(path);
    if (localFile.isDirectory()) {            // File("file:/...").isDirectory() == false → 整段跳过
        if (allowedLocalRoot != null && !allowedLocalRoot.isEmpty()) { ... canonical 比对 ... }
    }
}
```
- **现状**: indexDirectory 先 `resolveVfsPath`（任何非空输入都变成 `file:/abs/...`）再 validateLocalPath；`file:/...` 不匹配 `startsWith("/")` 也不匹配 `charAt(1)==':'`，且 `new File("file:/...")` 永远不是目录，allowedLocalRoot 白名单检查整段成为死代码。唯一有效的是 `..` 检查。同时 `setAllowedLocalRoot` 在任何 beans.xml/配置中都没有被调用（全仓 grep 仅本文件），白名单根本未启用。即传入 `/etc` 或 `file:/etc` 均可索引任意本地目录（triggerIncrementalIndex 用原始路径校验，`/etc` 会被拒，但 `file:/etc` 同样绕过）。
- **风险**: `triggerFullIndex`/`indexDirectory` 是 `@BizMutation @Auth(roles="admin")` 的 GraphQL 接口，projectPath 完全由调用方提供；配合查询接口可读取服务器任意本地目录的源码内容（索引会把源码全文入库并可经 getFileSourceCode 取回）。受 admin 角色门禁缓解，但防护形同虚设。
- **建议**: 校验前先剥离 `file:` 前缀得到真实本地路径再做绝对路径拒绝与 canonical 白名单比对；allowedLocalRoot 用 @InjectValue 接入配置（如 `nop.code.allowed-local-root`）并在 beans.xml 中声明，未配置时默认拒绝绝对路径。
- **误报排除**: 已核对 resolveVfsPath 所有分支（相对路径也会转成绝对 `file:` 形式）；已用实际语义确认 `File("file:/x").isDirectory()` 为 false；已 grep 确认 allowedLocalRoot 无任何装配点。

### [P2] indexFile 对分析结果为 null（空源码/不可解析文件）直接 NPE

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:320-342`；`nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:126-134`
- **维度**: D1
- **证据**:
```java
// JavaFileAnalyzer.analyze
if (sourceCode == null || sourceCode.isBlank()) return null;
ParseResult<CompilationUnit> parseResult = new JavaParser(parserConfiguration).parse(sourceCode);
if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) return null;

// CodeIndexService.indexFile —— 无 null 检查
CodeFileAnalysisResult result = fileAnalyzer.analyze(filePath, sourceCode);
withIndexLock(indexId, () -> { ... persistSingleFileInSession(indexId, result, session); ... });
// persistSingleFileInSession → saveFileResultInSession → generateFileId(indexId, file.getFilePath()) → NPE
```
- **现状**: triggerIncrementalIndex 与 ProjectAnalyzer 都对 null 结果有守卫，唯独 indexFile 没有。analyze 返回 null（空字符串、纯空白、Java 解析失败）时，null 一路传进 saveFileResultInSession 触发 NPE（事务回滚，旧数据无损）；BizModel 层 `FileAnalysisDTO.fromCodeFileAnalysisResult(result)` 也会 NPE。
- **风险**: `@BizMutation indexFile` 以空 sourceCode 或含语法错误的源码调用即抛 NOP 内部 NPE（500），未走 NopException/ErrorCode 两档错误策略。
- **建议**: indexFile 对 null 结果抛 `NopException(ERR_CODE_ANALYZE_FAILED).param(...)` 或返回明确的空结果对象。
- **误报排除**: 已通读 indexFile→persistSingleFileInSession→saveFileResultInSession 链路确认无 null 守卫；BizModel 层 130-131 行同样无守卫。

### [P2] SymbolTable 写方法 synchronized 但读方法不同步，缓存符号表存在并发读写数据竞态

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/SymbolTable.java:26-51`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:147-161`
- **维度**: D3
- **证据**:
```java
public synchronized void add(CodeSymbol symbol) { ... }              // 写：synchronized
public CodeSymbol getByQualifiedName(String qualifiedName) {          // 读：无同步
    return byQualifiedName.get(qualifiedName);
}
public CodeSymbol getById(String id) { return byId.get(id); }         // 读：无同步
public int size() { return byId.size(); }                             // 读：无同步

// CodeCacheManager.addToSymbolTableCache：在 indexFile 路径向"已缓存"的表追加符号
entry.cache.symbolTable.add(sym);
```
- **现状**: 缓存的 SymbolTable 会被 addToSymbolTableCache（indexFile/triggerIncrementalIndex 路径，仅持 cacheManager 锁与 indexLock）并发追加，而所有查询路径（detectCommunities/getImpactAnalysis/getCallHierarchy 等）在同一实例上无锁调用 getById/getByQualifiedName/size。普通 HashMap 在无同步并发读写下的可见性/一致性无保证（resize 期间读取可能返回错误结果）。getAll() 是 synchronized 快照，部分路径安全，但 getById 路径（如 collectRelevantInheritances、buildCallHierarchy）裸读。
- **风险**: 增量索引进行中并发执行图分析查询时，读取到不一致/过期符号数据，理论上存在 HashMap 并发读写的未定义行为。
- **建议**: SymbolTable 全部读写 synchronized（或在缓存中改为不可变快照 + 原子替换）；至少为读路径加与写一致的锁。
- **误报排除**: 已确认 addToSymbolTableCache 的调用链在 indexLock/cacheManager 锁内，但读路径（ensureSubServices 后的各 BizQuery）不获取这两把锁，跨锁并发真实存在。

### [P2] 跨文件调用边解析能力缺口：Java 仅有 ReflectionTypeSolver，Python/TS 不产出被调方限定名，大量调用边在持久层被丢弃

- **文件**: `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:78-86, 590-606`；`nop-code/nop-code-lang-python/src/main/java/io/nop/code/lang/python/PythonCodeFileAnalyzer.java:414-443`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1251-1254`
- **维度**: D1、D8
- **证据**:
```java
// JavaFileAnalyzer 构造器：typeSolver 只有反射（JDK 类型），没有源码/类路径 solver
this.typeSolver = new CombinedTypeSolver();
this.typeSolver.add(new ReflectionTypeSolver(true));
...
} catch (Exception e) {
    if (LOG.isDebugEnabled()) { LOG.debug("Failed to resolve method call: ...", e); }
    // 降级：calleeQualifiedName 保持 null
}

// CodeIndexService.saveFileResultInSession：calleeId 为空的调用直接跳过
if (call.getCalleeId() == null || call.getCallerId() == null) continue;
```
- **现状**: Java 侧项目内跨文件调用经 symbol solver 解析时因无 SourceTypeSolver 必然 UnsolvedSymbolException（降级仅填 argumentTypes），calleeQualifiedName 为 null → resolveCalls 无法解析 → calleeId 为 null → 持久层跳过；Python/TS 的 walkNodeForCalls 从不设置 calleeQualifiedName，同样全被跳过。入库的调用边基本只剩同文件 TYPE_OF/INSTANTIATES 引用与启发式合成边。
- **风险**: CallGraph 系统性稀疏，getCallHierarchy/getImpactAnalysis/DeadCodeDetector/FlowDetector 建立在近乎空的反向边上，死代码误报与影响漏报是常态；与接口宣称的"调用图分析"能力不符（契约漂移）。
- **建议**: 为 Java 增加基于索引文件集合的内存/源码 TypeSolver（或用 javaparser 的 SymbolSolverCollection），Python/TS 至少按"模块限定名 + 方法名"生成 calleeQualifiedName 交由 resolveCalls 模糊解析；在文档中如实标注当前边覆盖率。
- **误报排除**: 已核对 CombinedTypeSolver 注册内容、异常降级路径、resolveCalls 只处理非空 calleeQn、持久层 skip 逻辑；同文件 ObjectCreationExpr/TYPE_OF 边确实能入库（symbolMap 同文件匹配），故定级 P2 而非"完全无调用图"。

### [P2] ProjectAnalyzer 声明的文件大小/数量上限从未实施，readText 无界读入内存

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:53-55, 176-186`
- **维度**: D6
- **证据**:
```java
private static final int DEFAULT_BATCH_SIZE = 1000;
private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;
private static final int MAX_FILE_COUNT = 50000;
...
String sourceCode = resource.readText();      // 无 length() 预检
CodeFileAnalysisResult result = fileAnalyzer.analyze(relativePath, sourceCode);
```
- **现状**: grep 全模块确认 `MAX_FILE_SIZE_BYTES`、`MAX_FILE_COUNT` 无任何引用（死常量）。analyzeProject/analyzeIncremental/analyzeFiles 都直接 readText，文件大小与总数均无上限（BatchQueue 只控制批量入库，不控制读入）。对比 BizModel 层 indexFile 有 1MB 校验（MAX_SOURCE_CODE_BYTES），目录索引路径无对应防护。
- **风险**: 对包含超大文件（如几十 MB 的生成代码/数据文件恰以 .java/.py/.ts 结尾）的目录建索引时整文件进内存，且 CodeFileAnalysisResult 还持有 sourceCode 引用随 fileResults 全量驻留，大仓库下内存峰值不可控。
- **建议**: readText 前检查 `resource.length()`，超限跳过并 LOG.warn（计入 stats）；对 fileCount 施加 MAX_FILE_COUNT 截断并标记 truncated。
- **误报排除**: 已 grep 确认两个常量零引用；已确认 BizModel 的 1MB 校验只覆盖单文件 API。

### [P2] persistSingleFileInSession 每文件触发全索引级扫描与全表合并，增量索引呈 O(N×M)

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1085-1127, 975-1023`
- **维度**: D6
- **证据**:
```java
SymbolTable fileSymbolTable = buildSymbolTableFromResult(result);
SymbolTable globalTable = getOrRebuildSymbolTable(indexId);       // 首个文件触发全量符号重建/缓存读取
...
SymbolTable mergedTable = new SymbolTable();                       // 每文件复制合并整个全局表
for (CodeSymbol sym : globalTable.getAll()) { mergedTable.add(sym); }
...
resolveQualifiedNamesToIds(indexId, mergedTable, session);         // 分页扫全部 inheritance + annotationUsage 行
```
- **现状**: triggerIncrementalIndex 对每个变更文件调用 persistSingleFileInSession；其中 mergedTable 构建是 O(全局符号数)，resolveQualifiedNamesToIds 分页遍历该索引全部 NopCodeInheritance 与 NopCodeAnnotationUsage 行。M 个变更文件即 O(N×M) 次 DB 读取与对象复制，且发生在事务+会话内。
- **风险**: 大索引（10 万符号）大批量增量（如分支切换后上千文件）时索引耗时与事务持有时间急剧膨胀，锁（indexLock）长时间占用。
- **建议**: 把 resolveQualifiedNamesToIds 提到批量末尾一次执行；mergedTable 只在本文件符号 + 受影响 QN 范围内构建，或直接复用 cacheManager 缓存表 + 增量合并。
- **误报排除**: 已确认 BatchQueue 回调按文件逐一调用 persistSingleFileInSession（triggerIncrementalIndex:749-776），无批级去重。

### [P2] getSymbolSourceCode 不按 indexId 过滤，可跨索引读取符号源码（与已修复的 AR-41 同型缺陷残留）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:548-557`
- **维度**: D8、D5
- **证据**:
```java
String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter) {
    if (daoProvider == null) return null;
    NopCodeSymbol entity = daoProvider.daoFor(NopCodeSymbol.class).getEntityById(symbolId);  // 无 indexId 条件
    if (entity == null || entity.getFileId() == null) return null;
    NopCodeFile file = daoProvider.daoFor(NopCodeFile.class).getEntityById(entity.getFileId());
    ...
    return extractLines(file.getSourceCode(), Math.max(1, startLine), endLine);
}
```
- **现状**: 同文件的 getSymbolById 已按 WP-7 AR-41 加上 indexId 过滤（446-455 行注释明确说明 getEntityById 会跨索引泄漏），但 getSymbolSourceCode 与 findReferencedBy 的 enclosing 符号查询（786-793 行按 id in 查询）仍是裸 getEntityById/无 indexId 条件。
- **风险**: 传入其它索引中存在的 symbolId 即可取回该索引文件源码片段，绕过"按索引隔离"的契约。实际可触达性受制于 symbolId 为随机 UUID（需先从另一索引获知），故为契约违规而非高频泄漏。
- **建议**: 与 getSymbolById 一致，加 `FilterBeans.eq("indexId", indexId)` 查询；findReferencedBy 的 enclosing 查询同理。
- **误报排除**: 已比对同文件已修复方法的过滤条件，确认本方法缺失；非误读 API 语义（接口签名含 indexId 参数，契约要求隔离）。

### [P3] 每个文件都新建语言分析器实例（JavaFileAnalyzer 含新 TypeSolver/ParserConfiguration）

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/adapter/LanguageAdapterRegistry.java:19-28`；`nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/JavaLanguageAdapter.java:18-20`；`nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:173`
- **维度**: D6
- **证据**:
```java
public ICodeFileAnalyzer getAnalyzer(String filePath) {
    for (ILanguageAdapter adapter : adapters.values()) {
        for (String ext : adapter.getFileExtensions()) {
            if (filePath.endsWith(ext)) { return adapter.getFileAnalyzer(); }  // 每次调用 new
```
- **现状**: JavaLanguageAdapter.getFileAnalyzer() 每次 `new JavaFileAnalyzer()`（构造 CombinedTypeSolver + ReflectionTypeSolver + ParserConfiguration），ProjectAnalyzer 分析循环中每个文件调用一次 getAnalyzer。万级文件索引即万次 solver/parser 构建。分析器本身无状态（visitor 为每文件新建），可安全复用。
- **风险**: 大仓库全量索引的 CPU/分配开销放大，无正确性影响。
- **建议**: 适配器缓存单例 analyzer（分析器无状态），或在 ProjectAnalyzer 循环外按扩展名取一次。
- **误报排除**: 已确认 JavaFileAnalyzer 字段（typeSolver/parserConfiguration/methodCallFilter）均为不可变配置，IndexVisitor 每文件新建，复用安全。

### [P3] 解析失败的文件在目录索引中被静默跳过，无任何日志或痕迹

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:176-186`
- **维度**: D4
- **证据**:
```java
try {
    String sourceCode = resource.readText();
    CodeFileAnalysisResult result = fileAnalyzer.analyze(relativePath, sourceCode);
    if (result != null) {           // JavaFileAnalyzer 对解析失败返回 null → 这里静默丢弃
        batchQueue.add(result);
        fileCount[0]++;
    }
} catch (Exception e) {
    LOG.warn("Failed to analyze resource: {}", relativePath, e);   // 异常有日志，null 没有
}
```
- **现状**: 抛异常的文件有 WARN 日志；analyze 返回 null（Java 解析失败、空白文件）既不入索引也无日志。Python/TS 因 tree-sitter 容错基本不受影响，Java 受 JavaParser 严格性影响。
- **风险**: 含语法错误的源文件从索引中静默消失（搜索结果缺失），且无排查线索。
- **建议**: null 结果同样 LOG.warn（含路径与原因），或分析器返回带错误信息的结果对象。
- **误报排除**: 已确认 JavaFileAnalyzer 两个 null 返回点（blank/解析失败）与该处无日志路径。

### [P3] 多处分页查询不带 ORDER BY，offset 翻页可能漏行/重行

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:512-527`（getIndexStats kindCounts；同型：buildInheritanceIndex:918-938、loadExistingEdgeKeys:942-962、OrmFingerprintStore.loadFileIdMapByIndex:81-103 等）
- **维度**: D1
- **证据**:
```java
while (true) {
    kindQuery.setOffset(kindOffset);
    kindQuery.setLimit(BATCH_SIZE);
    List<Map<String, Object>> kindResults = symbolDao.selectFieldsByQuery(kindQuery);
    ...
    if (kindResults.size() < BATCH_SIZE) break;
    kindOffset += BATCH_SIZE;
}
```
- **现状**: offset 递增翻页但查询无排序字段，数据库返回顺序不保证稳定，跨页可能重复或漏行。删除类循环（deleteEntitiesPaged 等）因"删完再查"影响较小；统计类（kindCounts、fingerprint 全量加载、继承索引）可能得到偏差数据。
- **风险**: 统计数字偏差、指纹映射偶发漏项（漏项会被当作新增文件重分析，可自愈），影响有限。
- **建议**: 翻页查询统一追加按主键排序；或改用游标式（lastId > cursor）分页。
- **误报排除**: 已核对 QueryBean 未设置任何 orderBy 字段。

### [P3] deleteIndex 在持锁状态下移除 indexLocks 条目，存在锁失效竞争窗口；indexLocks 只增不减

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:113-133, 546-577`
- **维度**: D3
- **证据**:
```java
public void deleteIndex(String indexId) {
    withIndexLock(indexId, () -> {
        ...
        indexLocks.remove(indexId);    // 持有旧锁实例时移除映射
    });
}
```
- **现状**: 线程 A 持锁执行 deleteIndex、并发线程 B 已 computeIfAbsent 拿到同一把旧锁并等待；remove 后线程 C 进来 computeIfAbsent 创建新锁并直接执行——B 与 C 对同一 indexId 并发执行。另外 indexLocks 仅 deleteIndex 清理，正常存在的索引数量多时 Map 持续增长（每项一个空 ReentrantLock，量级小）。
- **风险**: 竞态窗口窄、后果是并发索引操作互斥失效（结合上文数据一致性问题放大）；内存增长幅度小。
- **建议**: 不在持锁时 remove（改为空闲清理或定期驱逐），或锁条目加引用计数/tryLock 探活后移除。
- **误报排除**: 已读 withIndexLock 两实现与 deleteIndex 全文确认 remove 位于锁内。

### [P3] InterfaceImplSynthesizer 前缀匹配无边界，接口方法 save 会误连实现类 saveAll

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/graph/InterfaceImplSynthesizer.java:52-58`；`nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/SymbolTable.java:53-61`
- **维度**: D1
- **证据**:
```java
String implMethodPrefix = implType.getQualifiedName() + "." + calleeSymbol.getName();
List<CodeSymbol> implMethods = symbolTable.findAllByQualifiedNamePrefix(implMethodPrefix);
// findAllByQualifiedNamePrefix: entry.getKey().startsWith(prefix) —— 无边界检查
```
- **现状**: 前缀 `com.x.Impl.save` 同样匹配 `com.x.Impl.saveAll(...)`、`com.x.Impl.save2(...)`，为不存在覆写关系的方法合成 HEURISTIC 调用边。方法同名重载（无参数签名的 QN）本就无法区分，可接受；但前缀越界匹配是额外噪声。
- **风险**: 启发式调用图混入错误边（已标注 INFERRED/HEURISTIC，使用者可过滤），影响可控。
- **建议**: 匹配后校验候选名的下一字符为 `(` 结束或与名称完全一致（QN 体系无签名时取"完全等于或以 name+'(' 开头"）。
- **误报排除**: 已确认 findAllByQualifiedNamePrefix 是纯 startsWith。

### [P3] CodeCacheManager 用单一全局锁串行化所有索引的缓存重建，重建期间阻塞全部图查询

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:61-63, 103-133`
- **维度**: D6、D3
- **证据**:
```java
private final Map<String, CacheEntry> analysisCacheMap = new LinkedHashMap<>(16, 0.75f, true);
private final ReentrantLock lock = new ReentrantLock();      // 全局单锁

SymbolTable getOrRebuildSymbolTable(...) {
    lock.lock();
    try { ... entry.cache.symbolTable = rebuildSymbolTable(indexId, daoProvider, converter); ... }
```
- **现状**: rebuildSymbolTable/rebuildCallGraph 在锁内分页加载至多 10 万符号/50 万边，期间任何索引的任何缓存读写（getAll 查询前置的 getOrRebuild*）都被阻塞。
- **风险**: 大索引冷启动（或 TTL 过期后首个请求）造成全模块图分析接口集体卡顿秒级。
- **建议**: 按索引分段锁（如 locks stripped by indexId），全局锁只保护 map 结构；或重建在锁外完成后原子放入。
- **误报排除**: 已确认所有读写路径均经同一 lock。

### [P3] 搜索引擎路径忽略 searchType 参数，SYMBOL_NAME/FULL_TEXT 语义漂移

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeSearchService.java:48-66`
- **维度**: D8
- **证据**:
```java
List<CodeSearchResultDTO> searchCode(String indexId, String query, String searchType, ...) {
    ...
    if (searchEngine != null) {
        return searchViaEngine(indexId, query, language, filePattern, limit);  // searchType 未传入
    }
    String type = searchType != null ? searchType : "COMBINED";
    switch (type) { case "SYMBOL_NAME": ... case "FULL_TEXT": ... }
}
```
- **现状**: 配置了搜索引擎时，调用方指定的 searchType（SYMBOL_NAME/FULL_TEXT/COMBINED）被忽略，统一走 TEXT 检索；仅 DB 降级路径尊重该参数。
- **风险**: 同一 API 两条实现路径行为不一致，调用方难以预期结果构成。
- **建议**: 引擎路径按 searchType 映射到对应字段检索（如 SYMBOL_NAME → title/name 字段权重），或在文档/返回中标注实际采用的检索方式。
- **误报排除**: 已读 searchViaEngine 全文确认未使用 searchType。

### [P3] 死代码与未接线配置：NopCodeConfigs 空接口、updateIndexStats(String,ProjectAnalysisResult) 无调用、analyzeBatch/partition/matchesFilePattern 未使用、allowedLocalRoot 无装配点

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeConfigs.java:3-5`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1055-1064, 2041-2047`；`nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:668-703, 783-791`
- **维度**: D7、D6
- **证据**:
```java
public interface NopCodeConfigs {          // 空接口，无任何配置项
}

private void updateIndexStats(String indexId, ProjectAnalysisResult result) { ... }  // 无调用点

public void setAllowedLocalRoot(String allowedLocalRoot) { ... }  // 无 beans.xml/配置装配（见 P1 路径防护项）
```
- **现状**: grep 确认上述方法/类零引用。Nop 平台惯例配置经 @InjectValue + beans.xml 装配，本模块没有任何 @InjectValue 使用，安全相关的 allowedLocalRoot 因此从未生效。
- **风险**: 维护噪声；更重要的是配置项"存在但不可用"造成安全假象（与 P1 路径防护项互为因果）。
- **建议**: 删除死方法；为 allowedLocalRoot、缓存 TTL/容量等建立真实配置项并接入 beans.xml。
- **误报排除**: 全模块 grep 确认零引用（allowedLocalRoot 的调用点仅存在于本类内部定义）。

## 补充说明（非缺陷的正面确认）

- 错误处理规范（D4/D7）：未发现 bare RuntimeException、空 catch、printStackTrace；公共路径使用 NopException + ErrorCode + .param(...)，符合平台两档策略（个别吞异常点已在上文列出）。
- IoC 规范（D7）：@Inject 均为 protected 字段或 setter 注入，无私有字段注入；CodeIndexService/FlowDetector/DeadCodeDetector/ChangeAnalyzer 均在 `_vfs/nop/code/beans/app-service.beans.xml` 显式注册，语言适配器在各自 `_lang-*.beans.xml` 注册，符合 Nop IoC 约定。
- git 外部调用（D5）：ChangeAnalyzer 的 baseline/target 在入口经 `GIT_REF_PATTERN` 白名单校验后以参数数组传给 ProcessBuilder，无 shell 拼接，无命令注入风险；进程有 redirectErrorStream + finally destroyForcibly，waitFor 有 30s 超时（超时位置在 stdout EOF 之后属轻微缺陷，未单列）。
- tree-sitter 资源（D2）：bonede 绑定无公开 close API，native 内存经 Cleaner 回收；每文件新建 TSParser 依赖 GC 释放，属绑定设计的已知代价，未按泄漏列报。
