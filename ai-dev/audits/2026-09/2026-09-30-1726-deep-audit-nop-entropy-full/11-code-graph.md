# G11: nop-code + nop-graph 深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计范围**: nop-code/ 全部子模块（core/flow/lang-java/lang-python/lang-typescript/lang-go/lang-csharp/lang-rust/service/api/dao/meta/web/app/codegen）、nop-graph/ 全部子模块（api/core）
- **执行维度**: 09 错误处理与错误码、15 类型安全与泛型使用、16 测试覆盖与质量、03 API 表面积与契约一致性
- **方式**: 纯静态审计（未运行 mvn/test），live code 为准

## 审计范围

### 深读的关键文件

- **nop-graph-core 算法全量逐行**：`TarjanSCC`、`PageRank`、`LeidenDetector`、`ImpactPropagator`、`BetweennessCentrality`、`Bfs`、`TopologicalSort`、`PathQueryExecutor`、`LabelPropagation`、`GraphDiffer`、`GraphExporter`、`InMemoryGraph`、`DirectedGraphAdapter`；api 类型 `IGraph`/`Edge`/`PathQuery`/`ImpactConfig`/`LeidenConfig`。
- **nop-code-core 图模型**：`CallGraph`、`SymbolTable`、`CodeCallGraph`、`CodeRelationGraph`、`CodeEdgeData`、`LanguageAdapterRegistry`、`ProjectAnalyzer`（analyzeFiles/incremental 路径）、`NopCodeCoreErrors`。
- **nop-code-flow**：`FlowDetector`（traceForward/criticality）、`DeadCodeDetector`（入口排除与置信度）、`ChangeAnalyzer`（git diff 解析、超时与中断处理）。
- **nop-code-service**：`CodeIndexService`（装配、缓存、错误路径，2991 行抽样深读）、`CodeGraphService`（私有 tarjanSCC、findCycles、detectCommunities、getDeps/getReverseDeps）、`CodeQueryService` 与 `CodeCacheManager`（缓存同步模型）、`NopCodeIndexBizModel`（BizModel 公开面、@Auth、错误码使用）、`NopCodeErrors`、`ShardBuildCoordinator`/`ClusterWorkspaceManager`/`IndexShardPlanner`（cluster 包）。
- **beans 装配**：`nop-code-service/app-service.beans.xml`、`_service.beans.xml`、各 `nop-code-lang-*` 的 `_lang-*.beans.xml`、`nop-code-flow/app-flow.beans.xml`、nop-ioc 自动装载规则（`IocConfigs`：仅自动装载 `beans/app-*.beans.xml`）。
- **测试面**：`TestAlgorithms`、`TestCommunityAlgorithms`、`TestApiTypes`（nop-graph 全部 3 个测试类）；nop-code 141 个测试文件的清单分布；`TestSymbolTable`、`TestCallGraphImmutability`、`TestConcurrentIndexing` 的覆盖范围核对。

### 机械基线核实结论（口径：src/main 与 src/test 分开，grep 模式见命令）

- **裸异常（throw new RuntimeException/IAE/ISE/UOE/AssertionError）**：nop-graph main 19 处全部为算法入参前置校验的 `IllegalArgumentException`（英文消息、arg 抽象库场景，判为可接受的内部前置条件，不逐条立项）；nop-code main 34 处集中在 `FrameworkPatternDsl`（DSL 解析）、`ClusterWorkspaceManager`/`ShardBuildCoordinator`/`IndexShardPlanner`（cluster 包——见 [G11-03-02]，这些类本身无生产调用方）、`DeletedResourceStub`（UOE stub 语义）、`CodeEdgeData.Builder` 校验。未发现裸 `RuntimeException`、未发现中文异常消息、异常链保留良好（catch 后 rethrow 均带 cause）。
- **System.out/printStackTrace**：main 代码 0 处；14 处全部位于测试代码（eval/probe 类），按口径不立项。
- **@Inject private**：0 处，全部为 protected 字段或 setter 注入，合规。
- **ErrorCode 体系**：`NopCodeCoreErrors`/`NopCodeErrors` 均为 `ErrorCode.define` + 英文描述 + ARG_* 常量，`NopException` + `.param(...)` 用法规范（`ERR_NO_ANALYZER_FOR_FILE`、`ERR_CODE_SOURCE_CODE_TOO_LARGE` 等）；两档策略整体遵循良好，偏差见 [G11-09-02]。
- **零发现维度说明**：四个维度均有发现，无零发现维度。以下检查项为阴性结论：BizModel 全部 13 个实体服务均继承 `CrudBizModel` 且构造器调用 `setEntityName`、公开方法均带 `@Auth`（roles/permissions）、无 `Map<String,Object>` 入参反模式、无死 @BizQuery；`GraphDiffer`/`InMemoryGraph`/`CodeRelationGraph`/`CodeCallGraph`/`ChangeAnalyzer` 逐行检查未发现正确性问题；`ProjectAnalyzer.analyzeFiles` 的 per-file 故障隔离（LOG.warn + throwable）符合 error-handling 文档。

---

## 发现

### 第 1 轮（初审）

---

### [G11-15-01] nop-graph-core TarjanSCC 迭代版丢失"非最后一个子节点"的 lowLink 合并，特定图形状下 SCC 结果被错误拆分

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/TarjanSCC.java:80-110`
- **证据片段**:
  ```java
  for (int i = edgeIdx; i < outEdges.size(); i++) {
      String w = outEdges.get(i).getTargetId();
      if (!index.containsKey(w)) {
          callStack.push(new Object[]{v, i + 1, true});
          callStack.push(new Object[]{w, 0, false});
          pushedChild = true;
          break;                       // <-- 恢复帧若又发现未访问子节点，直接 break
      } else if (onStack.contains(w)) {
          lowLink.put(v, Math.min(lowLink.get(v), index.get(w)));
      }
  }
  if (!pushedChild) {
      if (returning && edgeIdx > 0 && edgeIdx - 1 < outEdges.size()) {
          String w = outEdges.get(edgeIdx - 1).getTargetId();
          if (lowLink.containsKey(w)) {
              lowLink.put(v, Math.min(lowLink.get(v), lowLink.get(w)));   // 只合并在 (!pushedChild) 分支里
          }
      }
  ```
- **严重程度**: P1
- **现状**: 从子节点 w 返回的恢复帧 `{v, i+1, true}` 中，`lowLink[v] = min(lowLink[v], lowLink[w])` 的合并只在 `!pushedChild` 分支执行。若该恢复帧在扫描 `i = edgeIdx` 时又发现另一个未访问子节点（pushedChild=true 后 break），则刚返回的子节点 w 的 lowLink 永远不会合并进 `lowLink[v]`——只有"最后一个子节点"被合并。
- **风险**: 可构造最小反例：边 `r→v, v→a, v→b, a→r, b（汇点）`，出边顺序 `[a,b]`。真值 SCC 为 `{r,v,a}, {b}`；本实现因 `lowLink[v]` 未通过 a 合并到 0，`lowLink[v]==index[v]` 提前成立，输出 `{a,v}, {r}, {b}`——单个强连通分量被拆成两个。环检测是图算法库的核心契约（module-groups.md 声明该库供 nop-wf/nop-task/nop-stream 复用），错误 SCC 会向未来消费方传递错误的循环依赖结论。决定性佐证：nop-code-service `CodeGraphService.tarjanSCC`（`CodeGraphService.java:1309-1382`）复制了一份迭代 Tarjan，并在 `CodeGraphService.java:1327-1332` 用注释明确修复了同一缺陷——"propagate lowLink from the child subtree that just completed **before scanning further neighbors; otherwise only the last child's lowLink would be merged**"——说明该缺陷已被本仓库作者在实际场景中发现并局部修复，但未回改 nop-graph-core 的库版本。
- **建议**: 将 `CodeGraphService.tarjanSCC` 的做法回移植到 `TarjanSCC.strongconnect`：恢复帧在进入邻居扫描**之前**执行 `if (returning && edgeIdx > 0) { merge lowLink[outEdges[edgeIdx-1]] }`；并在 `TestAlgorithms` 补充"一个节点多个子节点 + 首个子节点指回祖先"的回归用例（现有 `testTarjanSCCWithCycle` 的三角形环不触发该缺陷路径）。
- **信心水平**: 确定（手工 trace 反例逐步验证；且存在作者自留的修复注释交叉印证）
- **误报排除**: 不是"迭代式 Tarjan 的通用写法差异"——标准迭代 Tarjan 要求在恢复帧顶部无条件合并刚完成子树的 lowLink；本仓库另一份实现（CodeGraphService）已按正确写法修复并留下原因注释，证明这不是风格分歧而是已确认的缺陷类别。当前仓内 nop-graph TarjanSCC 无生产调用方（nop-code 用的是自带的修复版），故判 P1 而非 P0。
- **复核状态**: 未复核

---

### [G11-03-01] Go/C#/Rust 三个 ILanguageAdapter bean 从未被任何 app-*.beans.xml 装载，多语言索引在组装后的运行时静默失效

- **文件**: `nop-code/nop-code-service/src/main/resources/_vfs/nop/code/beans/app-service.beans.xml:13-17`
- **证据片段**:
  ```xml
  <import resource="_dao.beans.xml"/>
  <import resource="_service.beans.xml"/>

  <import resource="_lang-java.beans.xml"/>
  <import resource="_lang-typescript.beans.xml"/>
  <import resource="_lang-python.beans.xml"/>
  ```
  而 `nop-code-lang-go/_lang-go.beans.xml`、`nop-code-lang-csharp/_lang-csharp.beans.xml`、`nop-code-lang-rust/_lang-rust.beans.xml` 各自定义了 `GoLanguageAdapter`/`CSharpLanguageAdapter`/`RustLanguageAdapter`（`ioc:type="io.nop.code.core.analyzer.ILanguageAdapter"`），且无任何其他文件 import 它们。
- **严重程度**: P1
- **现状**: NopIoC 仅自动装载 `beans/app-*.beans.xml`（`nop-core-framework/nop-ioc/.../IocConfigs.java:30`："缺省情况下所有模块下的 beans/app-*.beans.xml 文件都被装载"）；`_lang-*.beans.xml` 必须被显式 import。`app-service.beans.xml` 只 import 了 java/typescript/python 三个。`CodeIndexService.setRegistry`（`CodeIndexService.java:259-268`）通过 `BeanContainer.instance().getBeansOfType(ILanguageAdapter.class)` 注册适配器——Go/C#/Rust 适配器不在容器中，`LanguageAdapterRegistry.getAnalyzer()` 对 `.go/.rs/.cs` 文件返回 null。运行时证据：`nop-code-app/_dump/nop-app/nop/main/beans/merged-app.beans.xml`（实际组装产物的 dump）中只有 `_lang-java/_lang-python/_lang-typescript` 三个 beans 文件，无 go/csharp/rust。
- **风险**: 与明确声明的产品能力直接矛盾：`nop-code-service/pom.xml:58-68` 依赖全部六个语言模块，`CodeIndexService.registerImportResolvers()`（`CodeIndexService.java:293-303`）手工注册了 Go/Rust/CSharp 的 `IImportResolver`——import 解析器在、语言适配器不在，形成"半接线"状态。结果：`.go/.rs/.cs` 文件在全量/增量索引中被 `registry.getAnalyzer() == null` 跳过（`ProjectAnalyzer.java:445-448`），不报错、不计数、无日志，索引"成功完成"但三类语言的符号与调用图整体缺失；下游死代码检测、影响分析在该语言子集上给出错误结论。语言模块自身的单测（5-7 个/模块）直接 new 适配器，不经过 IoC，因此测试全绿也发现不了。
- **建议**: 在 `app-service.beans.xml` 补齐 `<import resource="_lang-go.beans.xml"/>`、`_lang-csharp.beans.xml`、`_lang-rust.beans.xml`；并增加一个装配级测试（构造 IoC 容器后断言 `registry.getSupportedLanguages()` 覆盖 `CodeLanguage` 全部枚举值），防止后续新增语言模块时再次漏 import。
- **信心水平**: 确定（IocConfigs 装载规则 + merged-app.beans.xml dump 双重证据）
- **误报排除**: 不是"lang 模块可选、按需启用"的设计——service pom 已无条件依赖全部六个语言模块，且 import resolver 侧已无条件注册 Go/Rust/CSharp；若是有意裁剪，resolver 注册与 pom 依赖应同步收缩。dump 文件仅作为运行时证据引用，发现对象是 `app-service.beans.xml` 装配源文件（非 `_` 前缀生成产物）。
- **复核状态**: 未复核

---

### [G11-15-02] SymbolTable 读方法未同步，而写方法 synchronized——CallGraph 已按 WP-5 AR-145 修复的同一缺陷在 SymbolTable 上复发

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/SymbolTable.java:26-51`
- **证据片段**:
  ```java
  public synchronized void add(CodeSymbol symbol) {          // 写：持有 this 监视器
      if (symbol.getQualifiedName() != null) {
          byQualifiedName.put(symbol.getQualifiedName(), symbol);
      }
      ...
  }

  public CodeSymbol getByQualifiedName(String qualifiedName) { // 读：无同步
      return byQualifiedName.get(qualifiedName);
  }

  public int size() {                                          // 读：无同步
      return byId.size();
  }
  ```
- **严重程度**: P2
- **现状**: `add()` 是 synchronized 的，但 `getByQualifiedName`/`getById`/`size` 直接读底层 `HashMap` 且不持同一监视器。按 JMM 这是数据竞争：并发写（尤其触发 resize）期间的读未定义。对照同包 `CallGraph.java:46`——"WP-5 AR-145: read methods must be synchronized to match the write methods"——同一竞态类别已在 CallGraph 上被认定为本仓缺陷并修复，SymbolTable 未同步修复。
- **风险**: 竞态窗口真实存在：`CodeCacheManager.getOrRebuildSymbolTable`（`CodeCacheManager.java:113-124`）把 SymbolTable 按 indexId 缓存并跨请求共享（GraphQL 并发请求读同一实例），而 `CodeCacheManager.addToSymbolTableCache`（`CodeCacheManager.java:157-171`，注释自证场景："callers iterating a cached table are immune to later mutations (e.g. addToSymbolTableCache adding symbols)"）会在增量索引后向**已缓存、正被并发读**的表调用 `add()`。后果区间从读到陈旧/null 到 resize 期间读线程行为异常，属低频、难复现、日志无凭据类缺陷。现有 `TestConcurrentIndexing` 只测"不同 indexId 并行"（各自独立缓存），未覆盖共享表并发读写。
- **建议**: 与 CallGraph 对齐：为 `getByQualifiedName`/`getById`/`size` 加 synchronized（读多写少场景后续可再换 ConcurrentHashMap），或在类 Javadoc 显式声明"单线程使用"并让 CodeCacheManager 串行化所有访问；补一个共享表并发读写回归测试。
- **信心水平**: 很可能（数据竞争由代码结构直接可证；实际触发概率取决于增量索引与查询并发）
- **误报排除**: 不是"NopIoC protected @Inject"类平台惯例——这是纯 Java 集合的可见性/竞态问题；也不是"getAll 已防御快照所以安全"——`getAll` 确实修复了（AR-155/158），但点查方法（查询 API 每次调用都要走的 `getByQualifiedName`/`getById`）仍是裸读。
- **复核状态**: 未复核

---

### [G11-15-03] LeidenDetector 回退路径的结果元数据失真：兜底 LabelPropagation 的结果被标为 "LEIDEN"，且 modularity 与社区来源错配

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:76-85, 124-140`
- **证据片段**:
  ```java
  CommunityResult result = runLeiden(edgeList, indexNodeMap.size(), indexNodeMap, graph, config);

  return new CommunityResult(result.getCommunities(),
          nodes.size(),
          result.getTotalCommunities(),
          result.getAverageCohesion(),
          result.getModularity(),
          "LEIDEN",                                  // <-- 无条件硬编码，即使 result 来自 LabelPropagation 回退
          CoreMetrics.currentTimeMillis() - startTime);
  ```
  以及 `runLeiden` 中两处回退：`communities.isEmpty()` 时（:124-128）用 Leiden 算出的 modularity 配 LP 的社区列表；`catch (Exception e)` 时（:136-140）整体回退 LP。
- **严重程度**: P2
- **现状**: `detect()` 外层包装把 `algorithmUsed` 硬编码为 "LEIDEN"。`runLeiden` 内部有两条回退路径都会改用 LabelPropagation 产生社区（LP 自身返回的 algorithmUsed="LABEL_PROPAGATION" 会被外层覆盖）。空社区回退分支还会把 Leiden 的 modularity 与 LP 的社区/内聚度拼在同一个结果里。下游 `CodeGraphService.convertCommunityResult`（`CodeGraphService.java:761`）将 `getAlgorithmUsed()` 原样透传到 `CommunityDetectionResultDTO` 并经 GraphQL 暴露。
- **风险**: 消费方（以及基于 `nop_code_graph_metric` 的物化数据）无法区分拿到的是 Leiden 还是降级算法的结果：两类算法的社区粒度、modularity 语义不同，把 LP 社区标成 LEIDEN 会让"为什么这批社区质量差/结构不同"的排查方向完全错误；同时异常被 `catch (Exception)` 吞为静默降级（有 LOG.warn，合规），算法真实失败率在数据面上不可见。
- **建议**: 让 `runLeiden` 返回携带真实 algorithmUsed 的结果（LP 回退时填 "LABEL_PROPAGATION" 或 "LEIDEN_FALLBACK_LP"），外层透传而非硬编码；空社区回退分支不要复用 Leiden 的 modularity（置 0 或 NaN 并注明）；可选：在 CommunityResult 增加 `fallbackReason` 字段。
- **信心水平**: 确定
- **误报排除**: 不是"CommunityResult 只是展示字段"——该值被 `GraphMetricStore` 物化并作为算法选型审计依据（`GRAPH_SUMMARY` 的 algorithmUsed）；也不是回退机制本身的问题（回退 + LOG.warn 符合 error-handling 文档的 per-element 隔离），问题只在回退后元数据不诚实。
- **复核状态**: 未复核

---

### [G11-16-01] PathQueryExecutor 三重语义缺陷且零测试：edgeType 过滤硬编码 start 节点、过滤失败节点仍进入结果集、minHops 被完全忽略

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PathQueryExecutor.java:33-53`（配合 `Bfs.java:102-138`、`PathQuery.java:13`）
- **证据片段**:
  ```java
  Predicate<String> effectiveFilter = nodeFilter != null ? nodeFilter : nodeId -> true;

  return Bfs.traverseFiltered(graph, start, maxHops,
          node -> effectiveFilter.test(node) && edgeMatches(graph, start, node, edgeType));
      //                                                        ^^^^^ from 参数被硬编码为 start
  ...
  private static boolean edgeMatches(IGraph graph, String from, String to, String edgeType) {
      ...
      for (Edge edge : graph.getOutEdges(from)) {   // 永远只查 start 的出边
  ```
  以及 `Bfs.traverseFiltered`（`Bfs.java:127-131`）：
  ```java
  if (!visited.contains(target)) {
      visited.add(target);                          // 未通过 filter 的节点也加入返回集合
      if (nodeFilter == null || nodeFilter.test(target)) {
          queue.add(...);                           // filter 只阻止"扩展"，不阻止"入结果"
      }
  }
  ```
- **严重程度**: P2
- **现状**: 三个独立缺陷：(1) `edgeMatches` 的 `from` 参数被传入常量 `start` 而非当前遍历节点，导致 edgeType 过滤只对深度 1 的节点可能为真，深度 ≥2 的节点永远无法通过过滤、也不会被扩展；(2) `traverseFiltered` 把未通过谓词的节点加入 `visited` 并最终随结果返回——PathQuery 的"匹配的节点 ID 集合"实际包含不匹配 edgeType/nodeFilter 的节点；(3) `PathQuery.minHops`（api 契约字段，默认 1，有 getter 与 `TestApiTypes.testPathQuery` 断言）在执行器中从未被读取。
- **风险**: 该类是 nop-graph-core 对外发布的查询原语（`PathQuery` javadoc 明确"执行由 nop-graph-core 的 PathQueryExecutor 完成"），未来消费方（如 nop-code 的关系图查询、nop-wf 复用）一旦接入，将得到"多跳路径查不全、结果混入不匹配节点、minHops 配置无效"的错误结果，且因 API 形态正常而难以察觉。当前仓内尚无 main 代码调用（仅此一点使其未升级为 P1）。
- **建议**: 将 filter 签名改为携带来源边的二元谓词（或改为在 Bfs 内部逐边判定 `edgeType`），使 edgeType 判定作用于遍历实际经过的边；将"结果收集"与"扩展许可"拆成两个谓词；实现 minHops（深度 < minHops 的节点不入结果但可扩展）；补齐 executor 级行为测试。
- **信心水平**: 确定（`from` 硬编码为 `start` 与参数名/方法语义直接矛盾）
- **误报排除**: 不是"filter 语义就是只控扩展"——`PathQuery` javadoc 声明这是"路径表达式查询"返回"匹配的节点集合"，且 `edgeMatches(graph, start, ...)` 若真意是"只匹配起点的直接邻居"就不会再接受 maxHops 参数；三处证据互相独立、方向一致。
- **复核状态**: 未复核

---

### [G11-09-01] 三个服务实现中 49 处 `daoProvider == null → return null/emptyList` 静默降级，违反 fail-fast 契约，持久层配置错误伪装成"无数据"

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java:136,149,160,...`、`CodeGraphService.java:99,185,307,...`、`CodeIndexService.java`（grep 口径：`if (daoProvider == null)`，三文件分别 19/17/13 处，合计 49 处）
- **证据片段**:
  ```java
  // CodeGraphService.java:97-99（detectCommunities，经 @BizQuery detectCommunities 暴露）
  CommunityDetectionResultDTO detectCommunities(String indexId) {
      if (daoProvider == null) return null;
      ...
  // CodeQueryService.java:148-150
      if (daoProvider == null) return null;
  ```
- **严重程度**: P2
- **现状**: 查询面公开方法在 `daoProvider` 未注入时统一返回 null 或空集合。`docs-for-ai/02-core-guides/error-handling.md` 反模式表明确禁止"内部函数 catch 异常转返回值（return null / Optional.empty()）把系统故障伪装成业务结果"。当前唯一对 null 的显式防御是 `NopCodeIndexBizModel.detectCommunities`（`NopCodeIndexBizModel.java:223-234`，手工把 null 转成空 DTO）；其余 @BizQuery（getTypeHierarchy、findReferencedBy、getDeps、exportGraph 等）把 null/空集原样透传给 GraphQL。
- **风险**: `daoProvider == null` 只可能是装配缺陷/未配置数据源，属于应当 fail-fast 的系统级故障；静默降级后，所有代码索引查询 API 表现为"索引为空"，运维零日志零告警，用户侧表现为"功能坏了但不报错"，且 BizModel 层的 null 防御只覆盖 13 个方法中的 1 个，其余方法的行为取决于调用方是否记得判 null——契约漂移已发生。
- **建议**: 反转默认：`daoProvider == null` 时抛 `NopException(ERR_CODE_DAO_NOT_AVAILABLE).param(ARG_INDEX_ID, ...)`（NopCodeErrors 新增一个 ErrorCode），或在 Bean 初始化后置校验（afterPropertiesSet 语义）一次性失败；若"无 DAO 轻量模式"是真实产品形态，则必须在接口 Javadoc 声明返回 null/空的语义，并让所有 BizModel 方法显式文档化，而非 49 处各自沉默。
- **信心水平**: 很可能（模式本身确定存在；"是否故意设计为无 DAO 模式"未在任何 owner 文档中找到声明）
- **误报排除**: 不是对 Nop 平台"BizModel 返回实体"等惯例的误读——这是 error-handling.md 点名禁止的 return-null 降级，且降级源（依赖注入缺失）属系统故障而非业务空态；`detectCommunities` 的 null 防御代码自证调用方确会把 null 当异常路径处理。
- **复核状态**: 未复核

---

### [G11-03-02] cluster 分片子系统整体未接线：三个核心类零生产调用方，NopCodeShardLedger 实体 + BizModel + GraphQL CRUD 为休眠特性暴露公开面

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/ShardBuildCoordinator.java`、`nop-code/nop-code-service/src/main/java/io/nop/code/service/cluster/ClusterWorkspaceManager.java`、`nop-code/nop-code-core/src/main/java/io/nop/code/core/cluster/IndexShardPlanner.java`
- **证据片段**:
  ```
  $ grep -rln "ShardBuildCoordinator" nop-code --include="*.java"   （排除 target）
  nop-code/nop-code-service/src/test/.../TestShardBuildCoordinator.java
  nop-code/nop-code-service/src/main/.../ShardBuildCoordinator.java     # 仅自身 + 测试
  $ grep -rln "ClusterWorkspaceManager" ...
  .../TestIndexAccessPolicyEnforcement.java, TestClusterShardingE2E.java, 自身
  $ grep -rln "IndexShardPlanner" ...
  .../TestIndexShardPlanner.java, TestClusterShardingE2E.java, 自身
  ```
  同时 `NopCodeShardLedgerBizModel`（标准 CrudBizModel）+ `NopCodeShardLedgerApi` + xbiz/xmeta/page 生成物已完整发布 GraphQL 面。
- **严重程度**: P2
- **现状**: 分片构建的三个核心类（协调器、工作区管理器、分片规划器）在 main 代码中没有任何调用点，只有测试引用；app-service.beans.xml 也未注册 `ShardBuildCoordinator`。而配套的 `NopCodeShardLedger` 表、CRUD BizModel、web 页面已全部生成并暴露。`IndexShardPlanner` 的裸 IAE（机械基线中 nop-code 34 处裸异常的主要来源之一）与 `ClusterWorkspaceManager` 的 git 进程管理代码处于"有测试、无调用方"状态。
- **风险**: (1) 公开 GraphQL 面包含一个没有任何生产写入路径的实体——外部调用方可对 `NopCodeShardLedger` 做增删改，产生与真实分片状态无关的脏数据；(2) 未接线的复杂代码（git 进程、超时、路径校验）持会被持续重构但从未真正运行，维护成本与"看似有覆盖"的假象（E2E 测试直接 new 这些类，而非通过装配路径）并存；(3) 未来接线时容易绕过现有测试验证装配正确性。
- **建议**: 二选一并记录决策：要么补齐接线（注册 bean、从某个 @BizMutation 触发分片构建、pom/web 生成物对齐），要么把 cluster 包 + NopCodeShardLedger 实体降级为实验分支/移出 reactor，避免休眠代码占据公开 API 面。
- **信心水平**: 确定（调用方检索为全仓 grep，含 xml/beans 装配文件）
- **误报排除**: 不是"尚未开发完的在建功能所以不报"——这些类已带完整测试与错误处理（说明自认为完成），但装配层缺失使其为死代码；WIP 模块口径允许内部实现粗糙，不允许"已发布公开面 + 无生产路径"的组合。
- **复核状态**: 未复核

---

### [G11-15-04] GraphExporter JSON 导出使用 locale 敏感的 String.format("%.4f")，逗号小数 locale 下产出非法 JSON

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphExporter.java:165`
- **证据片段**:
  ```java
  sb.append("    {\"id\":\"comm_").append(c.getId())
          .append("\",\"size\":").append(c.getNodeCount())
          .append(",\"cohesion\":").append(String.format("%.4f", c.getCohesion()))
          .append("}");
  ```
- **严重程度**: P2
- **现状**: `String.format("%.4f", ...)` 未传 `Locale.ROOT`，跟随 JVM 默认 locale。在默认 locale 为逗号小数（fr、de、it、es 等）的 JVM 上，cohesion=0.85 会被格式化为 `0,8500`，产出 `"cohesion":0,8500`——非法 JSON。该方法经 `ICodeIndexService.exportGraph(indexId, "JSON", communityView)` → `NopCodeIndexBizModel` @BizQuery 暴露。
- **风险**: 导出结果按部署主机的 locale 而非数据格式规范变化：同一索引在中文/英文主机导出合法 JSON，在法/德主机导出解析失败的字符串；客户端解析报错时与索引数据无关，排查方向被误导。社区视图导出（`exportGraph(indexId, format, true)`）在上述 locale 的主机上必现。
- **建议**: 改为 `String.format(Locale.ROOT, "%.4f", ...)`；同文件 `:296` 的 `String.format("\\u%04x", (int) c)` 无小数点不受影响，但建议统一约定。补一个 `Locale.setDefault(new Locale("fr"))` 下的导出-解析往返测试。
- **信心水平**: 确定
- **误报排除**: 不是"JSON 由前端格式化"——这是服务端导出 API 的字符串输出；也不是理论风险——locale 敏感格式化是 Java 明确文档化的行为，非猜测。
- **复核状态**: 未复核

---

### [G11-16-02] nop-graph 算法测试保护力不足：Tarjan 缺陷形状零覆盖、介数中心性仅平凡断言、Bfs 两个 traverse 契约不一致且无测试锁定

- **文件**: `nop-graph/nop-graph-core/src/test/java/io/nop/graph/algorithm/TestAlgorithms.java:74-105`、`TestCommunityAlgorithms.java:88-107`
- **证据片段**:
  ```java
  // Tarjan 仅有两个用例：三角形环 + 无环链，均不触发"一节点多子节点、首子节点指回祖先"形状
  @Test
  void testTarjanSCCWithCycle() {
      g.addEdge("a", "b"); g.addEdge("b", "c"); g.addEdge("c", "a"); g.addEdge("d", "e");
      ...
  // Betweenness 唯一断言是 scores.get("bridge") > 0 —— 恒真级保护力
  assertTrue(scores.get("bridge") > 0);
  ```
  另：`Bfs.traverse` 返回集**不含起点**（`Bfs.java:58` `visited.remove(start)`），`Bfs.traverseWithDepth` 返回集**含起点**（`Bfs.java:74` reachable.add(start)），两契约不一致且 `traverseWithDepth` 无行为断言锁定（仅 `testBfsWithDepth` 查 depth 值，不查 reachable 是否含起点）。
- **严重程度**: P2
- **现状**: 对一个以算法正确性为核心价值的库模块：`PathQueryExecutor` 0 测试（见 [G11-16-01]）、`DirectedGraphAdapter` 0 测试且 0 消费方、Tarjan 缺陷形状（[G11-15-01] 反例）无用例、自环节点在任何算法下均无用例、介数中心性唯一断言无法区分"正确实现"与"全 1 常数实现"。核心算法改成错误实现后，现有测试大概率仍然全绿（"测试仍通过 → 无保护力"判据）。
- **风险**: 算法类模块的测试保护力是回归防线；当前防线对 SCC 拆分错误、路径查询语义错误、介数错误全部门洞大开。[G11-15-01] 的缺陷能存活至今正是该缺口的直接后果——同一算法在本仓存在一 buggy 一 correct 两份实现而无任何测试能分辨。
- **建议**: 按风险排序补测：(1) Tarjan 多子节点+回边反例（锁定 [G11-15-01] 修复）；(2) 介数中心性换 assert 桥点得分为精确值（该图为手工可算的两条最短路）；(3) traverse/traverseWithDepth 的起点语义各加一条断言并统一或文档化；(4) 自环输入下 Tarjan/TopologicalSort/PageRank 的预期行为各锁定一条。
- **信心水平**: 确定
- **误报排除**: 不是"测试数量少"——nop-code 全域 141 个测试文件，缺口特指 nop-graph 算法正确性维度；也不是 AutoTest 快照机制问题（nop-graph 未使用快照，是纯 JUnit 断言）。
- **复核状态**: 未复核

---

### [G11-09-02] GraphExporter 抛出未注册错误码字符串 "nop.err.graph.export-failed"，与 nop-code-core 已注册的 "nop.err.code.graph-export-failed" 构成同一失败双码；git-ref 错误码在两个 Errors 容器中重复定义

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/GraphExporter.java:80`；`nop-code/nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java:15-16,19-21`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java:40-41`
- **证据片段**:
  ```java
  // nop-graph-core：裸字符串错误码，无 ErrorCode.define、无 i18n 条目、无 ARG 参数
  throw new NopException("nop.err.graph.export-failed", e, false, true);

  // nop-code-core：已注册的等价码（不同 ID）
  ErrorCode ERR_GRAPH_EXPORT_FAILED =
          define("nop.err.code.graph-export-failed", "Graph export failed");
  // 另有 git-ref 双码：NopCodeCoreErrors "nop.err.code.invalid-git-ref"
  //                与 NopCodeErrors    "nop.err.code.rebuild-invalid-git-ref"
  ```
- **严重程度**: P3
- **现状**: nop-graph 全模块没有 Errors 容器，唯一一处异常抛出使用未注册的错误码字符串：`getDescription()` 查不到定义与 i18n，错误响应只能回显码本身，丢失描述与参数化能力；同时 nop-code-core 又为同一失败定义了另一个码。`invalid-git-ref` 与 `rebuild-invalid-git-ref` 两个码语义重叠，消费方按码匹配时必须同时处理两个。
- **风险**: 低：功能可用（异常链保留、码字符串可读），主要是错误码治理问题——程序化消费方（按 `nop.err.*` 码做匹配/告警）面对未注册码与双码时行为不一致；后续为 nop-graph 补 i18n 时容易遗漏未注册码。
- **建议**: 在 nop-graph-api 增加 `GraphErrors` 容器并 `ErrorCode.define("nop.err.graph.export-failed", "Graph export failed")`；与 nop-code-core 约定主从关系（或让 nop-code 复用 graph 码）；合并两个 git-ref 码为一个并迁移。
- **信心水平**: 确定（未注册与双码均为 grep 可证事实）
- **误报排除**: 不按 P2 报是因为该路径有 ExportException cause 保留、消息英文、不影响主流程；WIP 模块口径下错误码治理属低优先但真实问题。
- **复核状态**: 未复核

---

### [G11-09-03] JavaFileAnalyzer 符号解析失败降级仅 LOG.debug，低于 error-handling 文档为 per-element 隔离规定的 LOG.warn 底线

- **文件**: `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:598-607`
- **证据片段**:
  ```java
  } catch (Exception e) {
      if (LOG.isDebugEnabled()) {
          LOG.debug("Failed to resolve method call: {} at {}",
                  expr.getNameAsString(),
                  expr.getRange().map(r -> r.begin.line + ":" + r.begin.column).orElse("unknown"),
                  e);
      }
      // 降级处理：提取参数表达式作为参数类型
  ```
- **严重程度**: P3
- **现状**: 方法调用符号解析（`expr.resolve()`）失败是 per-element 故障隔离场景（单次失败不中断整文件，符合隔离语义），但日志级别为 DEBUG（且包在 isDebugEnabled 内），低于文档"必须 LOG.warn 及以上且 throwable 作为末参数"的要求。同类还有 `:228`（extData sealed 解析）与 `:910`（Spring route 注解解析）。
- **风险**: 调用解析失败直接决定 CallGraph 边的完整性（calleeQualifiedName 为 null 的调用在后续图构建中被降权/丢失，进而推高死代码检测的假阳性），是本模块产品质量的核心信号；DEBUG 级别在生产默认日志配置下完全不可见，"为什么死代码报告里出现大量实际在用的方法"将无从排查。
- **建议**: 将该 catch 提升为 `LOG.warn`（保留 throwable 末参），或按文件聚合解析失败计数并在 `analyzeFiles` 汇总日志中输出 `unresolvedCalls` 比率（现仅有 resolved 总数，见 `ProjectAnalyzer.java:615`）。
- **信心水平**: 确定
- **误报排除**: 不是普通 debug 噪音问题——该 catch 属于文档"per-element 故障隔离"明确表项，规范底线是硬约束；降级 fallback 逻辑本身（提取参数表达式）是合理设计，不在发现范围。
- **复核状态**: 未复核

---

### [G11-15-05] FlowDetector 文件路径合成的多语言分支为死逻辑：guessExtension 恒返回 ".java"，合成的伪路径与真实 VFS 路径永不相等

- **文件**: `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java:214-255`
- **证据片段**:
  ```java
  private static final List<String> SOURCE_EXTENSIONS = List.of(".java", ".py", ".ts", ".tsx");

  private String guessExtension(String qualifiedName) {
      ...
      if (lower.endsWith("service") || lower.endsWith("controller") || ...) {
          return ".java";
      }
      return SOURCE_EXTENSIONS.get(0);        // 除第一个元素外全部不可达
  }
  ```
  以及 `isFlowAffected`（`:190-193`）用 `qualifiedNameToFilePath(entryQn)` 合成的 `com/foo/Bar.java` 与真实变更路径做 `changedFilePaths.contains(file)` 精确匹配。
- **严重程度**: P3
- **现状**: `guessExtension` 的三个分支全部返回 ".java"（`SOURCE_EXTENSIONS` 的 .py/.ts/.tsx 永不返回）；`qualifiedNameToFilePath`/`symbolIdToFilePath` 把全限定名合成成 `com/foo/Bar.java` 形态，而 `getAffectedFlows` 的 `changedFilePaths` 来自真实 VFS 路径（含 `src/main/java/` 前缀），精确 contains 永不命中——入口点文件匹配分支实际失效（pathNodeIds 分支用 extData 真实路径，仍可用）。另 `isExternalCall`（`:439-441`）为无调用方的私有死方法。
- **风险**: 入口符号所在文件的变更检测依赖兜底分支，行为"碰巧可用"；多语言声明性代码误导后续维护者以为 .py/.ts 已支持按限定名定位；若未来有调用方信任合成路径（如展示 flow→file 映射），将输出不存在的路径。
- **建议**: 删除 guessExtension/SOURCE_EXTENSIONS 合成分支与 isExternalCall，入口文件匹配统一走 extData 的真实 filePathMap；确需限定名→路径推断时，改为与 filePathMap 的已知路径集合做后缀匹配。
- **信心水平**: 确定（两个分支返回值恒等可静态证明；合成路径与真实路径形态差异由 indexDirectory 的 VFS 路径入参保证）
- **误报排除**: 不是风格类"代码不优雅"——恒返回常量的多分支函数与永不命中的路径匹配属于功能性死代码，会主动误导语言支持范围的判断；控制在 P3 因当前有真实路径兜底分支，未造成用户可见错误。
- **复核状态**: 未复核

---

### [G11-03-03] ICodeIndexService 公开接口存在孤儿 Javadoc：triggerRebuildFromCommit 的文档悬挂在 getLastIncrementalAffectedFiles 上

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:205-215`
- **证据片段**:
  ```java
  /**
   * Triggers an incremental rebuild from a commit range: validates HEAD consistency and
   * runs git diff as an early-exit check, then reuses the fingerprint incremental pipeline
   * against the worktree (graph-discovery-and-export-design.md §3.4).
   */
  /**
   * Returns the affected dependency files recorded by the last incremental index run
   * (N3.1 2-hop propagation), or an empty list.
   */
  List<String> getLastIncrementalAffectedFiles(String indexId);

  io.nop.code.api.dto.RebuildFromCommitResult triggerRebuildFromCommit(String indexId, String projectPath, ...);
  ```
- **严重程度**: P3
- **现状**: 第一个 Javadoc 块（描述 triggerRebuildFromCommit 的 HEAD 校验 + git diff 早退语义）实际悬挂在 `getLastIncrementalAffectedFiles` 声明之上，真正的 `triggerRebuildFromCommit` 方法无文档。两个连续 Javadoc 块中只有紧邻方法的那个生效。
- **风险**: 阅读 `getLastIncrementalAffectedFiles` 契约的开发者会误以为它做 HEAD 校验/git diff；`triggerRebuildFromCommit` 作为 admin 级 @BizMutation 的关键前置语义（HEAD 一致性校验）在接口层缺失，实现漂移时接口契约无从对照。
- **建议**: 将第一个 Javadoc 块移到 `triggerRebuildFromCommit` 声明上方。
- **信心水平**: 确定
- **误报排除**: 不是注释风格偏好——这是公开服务接口上文档与方法错位的结构性错误，会直接误导契约理解。
- **复核状态**: 未复核

---

### [G11-15-06] nop-graph-core 遗留类型安全与死代码杂项：BFS 深度以 String[] 编码、无用的 @SuppressWarnings、永不生效的收敛阈值、no-op 适配方法

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/Bfs.java:37-44,72-80,112-119`；`ImpactPropagator.java:63-74`；`BetweennessCentrality.java:53-55`；`PageRank.java:17`；`LeidenDetector.java:192-194`
- **证据片段**:
  ```java
  // Bfs / ImpactPropagator：深度作为字符串塞进 String[]，出队后 Integer.parseInt
  Queue<String[]> queue = new LinkedList<>();
  queue.add(new String[]{start, "0"});
  ...
  int depth = Integer.parseInt(current[1]);

  // BetweennessCentrality：类型参数已显式给定，unchecked 压制无对象
  @SuppressWarnings("unchecked")
  org.jgrapht.alg.scoring.BetweennessCentrality<String, DefaultEdge> bc =
          new org.jgrapht.alg.scoring.BetweennessCentrality<>(jgraph, false);

  // PageRank：定义了收敛阈值但 compute() 固定跑满 iterations，从不判敛
  private static final double CONVERGENCE_THRESHOLD = 1e-6;   // 全文件唯一出现处

  // LeidenDetector：参数与返回值完全相同的 no-op
  private static IGraph toIGraph(IGraph graph, List<String> nodes) {
      return graph;
  }
  ```
- **严重程度**: P3
- **现状**: 四处独立的类型安全/死代码瑕疵：深度信息经字符串编码-解析（三处复制，易错且徒增开销）；`@SuppressWarnings("unchecked")` 压制了不存在的检查；`CONVERGENCE_THRESHOLD` 暗示了从未实现的判敛早退（PageRank 恒跑满 `iterations` 轮，javadoc 未说明）；`toIGraph` 是参数即返回值的 no-op（两处调用点制造"已做子图转换"的错觉）。
- **风险**: 单独看均为局部维护成本；组合效果是 nop-graph-core 的可读性税——新算法贡献者需要逐个甄别哪些"看起来做了"的代码（判敛、子图转换）实际是空操作。
- **建议**: BFS 队列改用 record/小载体类（或 `Map<String,Integer>` 深度表驱动）；删除无用压制注解；删除 CONVERGENCE_THRESHOLD 或实现判敛早退；删除 toIGraph 及其调用包装。
- **信心水平**: 确定
- **误报排除**: 不是纯格式偏好——String[] 编码深度是可静默出错的类型安全缺陷（`Integer.parseInt` 对污染数据抛 NumberFormatException 而非编译期可查），其余三项为可机械证明的死代码/无效注解。
- **复核状态**: 未复核

---

## 基线口径备注

- 裸异常计数复核（grep `throw new (RuntimeException|IllegalArgumentException|IllegalStateException|UnsupportedOperationException|AssertionError)`，排除 target/）：nop-code main 34 处、nop-graph main 19 处，与主 agent 基线一致；全部逐条看过上下文，无需在 main 代码立项的个体项已并入 [G11-03-02]（cluster 包死代码）与「审计范围」基线核实结论。
- System.out/printStackTrace：main 0 处、test 14 处，与基线一致，按规则不立项。
- @Inject private：0 处，确认。
- 本次审计未运行 mvn/test（约束要求），测试保护力结论基于测试源码静态评估，未取得运行时覆盖率基线——已在 [G11-16-02] 中以"核心逻辑改成错误实现后测试是否仍通过"的推演口径替代，复核 agent 如有条件可运行 `./mvnw test -pl nop-graph -am` 补充。

## 子项复核结论

复核人：独立复核代理 R2（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G11-15-01] | 保留（维持 P1） | (1) 独立读 `TarjanSCC.java:80-110` 并手工 trace 报告的反例（r→v, v→a, v→b, a→r，v 出边序 [a,b]）：恢复帧 {v,1,true} 因发现未访问的 b 而 break，子节点 a 的 lowLink(0) 未合并进 lowLink[v]；b 完成后恢复帧 {v,2,true} 只合并了 b 的 lowLink(3)，lowLink[v]=1=index[v] 提前弹出 {a,v}，随后 r 单独成 SCC——真值 SCC {r,v,a} 被拆分，缺陷可复现。(2) 首轮声称的"决定性证据"属实：`CodeGraphService.java:1309-1382` 存在自带迭代 Tarjan，`tarjanSCC` 在 returning 帧进入邻居扫描前无条件合并 child lowLink，注释原文（:1344-1345）与报告引用一致；唯一勘误是报告标注注释位于 :1327-1332，实际在 :1344-1349，不影响结论。(3) 全仓 grep 确认 TarjanSCC 零生产调用方（仅自测），module-groups.md:22 确实声明该库供 nop-wf/nop-task/nop-stream 复用。库级核心算法正确性缺陷 + 仓内作者自证，P1 恰当（非 P0 因当前无生产调用方）。 |
| [G11-03-01] | 保留（维持 P1） | 五重证据独立重验：(1) `nop-ioc/IocConfigs.java:30-36` 确认缺省仅自动装载 `beans/app-*.beans.xml`；(2) `nop-code-service/app-service.beans.xml:13-17` 只 import _lang-java/_lang-typescript/_lang-python；(3) `_lang-go/_lang-csharp/_lang-rust.beans.xml` 均定义 `ioc:type="io.nop.code.core.analyzer.ILanguageAdapter"` 的 bean 且全仓无任何其他文件 import 它们；(4) 组装产物 `nop-code-app/_dump/nop-app/nop/main/beans/merged-app.beans.xml` 中 ILanguageAdapter bean 只有 Java/Python/TypeScript 三个；(5) `CodeIndexService.setRegistry` 经 `BeanContainer.getBeansOfType(ILanguageAdapter.class)` 注册，而 `registerImportResolvers()`（:293-303）无条件注册 Go/Rust/CSharp 的 ImportResolver，pom.xml:43-68 依赖全部六个语言模块，`ProjectAnalyzer.java:441-445` 对 getAnalyzer()==null 静默 continue。声明的产品能力与运行时行为直接矛盾且失败完全静默，P1 恰当。 |
| [G11-15-02] | 保留（维持 P2） | 重开 `SymbolTable.java:26-51`：add/findAllByQualifiedNamePrefix/getAll 为 synchronized，getByQualifiedName/getById/size 裸读 HashMap；同包 `CallGraph.java:46` 确有 "WP-5 AR-145: read methods must be synchronized" 修复注释（同缺陷类别已被本仓认定）。补充核实共享链路：`CodeCacheManager.getOrRebuildSymbolTable`（:113-124）按 indexId 缓存并外发表引用，`addToSymbolTableCache`（:157-171）在缓存管理器锁内向已外发的表 add——该锁不保护持有引用的外部读线程，数据竞争成立。低频难复现，P2 恰当。 |
| [G11-15-03] | 保留（维持 P2，一处细节勘误） | 核心缺陷属实：`LeidenDetector.detect()`（:78-85）外层无条件硬编码 algorithmUsed="LEIDEN"，`runLeiden` 两条回退路径（:124-128 空社区、:136-140 异常）返回 `LabelPropagation.detect(...)` 的结果后被外层覆盖为 LEIDEN——回退元数据失真成立。勘误：报告称"空社区回退分支把 Leiden 的 modularity 与 LP 的社区拼在同一结果里"不成立——该分支是整体返回 LP 结果（含 LP 自身 modularity），不存在 modularity/社区错配，只是 algorithmUsed 被覆盖。核心结论不变，P2 维持。 |
| [G11-16-01] | 保留（维持 P2） | 重开 `PathQueryExecutor.java:33-53`：`edgeMatches(graph, start, node, edgeType)` 的 from 参数确为常量 start（深度 ≥2 的节点对非空 edgeType 永远 false 且不被扩展）；`Bfs.java:127-131` 确认未通过 filter 的节点仍加入 visited 并随结果返回；`PathQuery.minHops`（api:13,26-31）在主代码零读取（grep 全模块仅 api 文件命中）。三重缺陷独立证实；因该执行器当前无 main 调用方，维持 P2（若接入消费方应升 P1）。 |
| [G11-09-01] | 保留（维持 P2） | 独立重跑计数：`grep -c "daoProvider == null"` 于 CodeQueryService=19、CodeGraphService=17、CodeIndexService=13，合计 49 与报告一致；`CodeGraphService.java:97-99` 确认 detectCommunities 返回 null；`NopCodeIndexBizModel.java:208-215` 确认唯一一处 null→空 DTO 手工防御，其余 @BizQuery（getGraphAnalysis/getImpactAnalysis 等，:217-240）原样透传。系统故障伪装成空态、违反 error-handling.md return-null 反模式，P2 恰当。 |
| [G11-03-02] | 保留（维持 P2） | 全仓 grep 独立复验：ShardBuildCoordinator 仅自身+TestShardBuildCoordinator；ClusterWorkspaceManager 仅自身+2 个测试；IndexShardPlanner 仅自身+2 个测试；app-service.beans.xml 无 coordinator 注册。同时 `_service.beans.xml:23-28` 确认 NopCodeShardLedgerBizModel + BizProxyFactoryBean 代理已发布 GraphQL CRUD 面。"已发布公开面 + 零生产写入路径"组合成立，P2 恰当。 |
| [G11-15-04] | 保留（维持 P2） | 重开 `GraphExporter.java:165`：`String.format("%.4f", c.getCohesion())` 确未传 Locale.ROOT（:296 的 `\u%04x` 不受影响，与报告判断一致）；暴露链核实 `NopCodeIndexBizModel.java:336` exportGraph @BizQuery。逗号小数 locale 下产出非法 JSON 为 Java 文档化行为，P2 恰当。 |
| [G11-16-02] | 保留（维持 P2） | 独立盘点 nop-graph 测试：仅 TestAlgorithms/TestCommunityAlgorithms/TestApiTypes 3 个文件；`TestAlgorithms.java:77-88` testTarjanSCCWithCycle 确为三角形环+孤立链，不触发 [G11-15-01] 的"一节点多子节点+首子回边"形状（该反例下现有测试全绿可证）；`Bfs.java:58` traverse `visited.remove(start)` 与 `:74` traverseWithDepth `reachable.add(start)` 起点语义不一致属实；Betweenness 仅 bridge 单用例。算法库测试保护力缺口与 [G11-15-01] 存活至今互为印证，P2 恰当。 |

**R2 统计**：复核 9 条（P1×2、P2×7）——保留 9（其中 1 条带细节勘误）、降级 0、驳回 0。

### 复核中发现的附带线索

- 核对 [G11-03-01] 时发现 `nop-code-lang-go/csharp/rust` 三模块各自带 5-7 个单测且 pom 已进 reactor，但运行时装配缺失——与 [G11-03-02] 的 cluster 子系统属同一"有测试、无装配"形态；若后续统一处理休眠特性，建议两类一并决策（不影响本批判定）。
- `CodeCacheManager.addToSymbolTableCache` 的 `getByQualifiedName(...) == null` 判重后 add 属 check-then-act，与 [G11-15-02] 同源竞态，修复时宜一并处理（已含于该发现修复范围，不另立项）。
