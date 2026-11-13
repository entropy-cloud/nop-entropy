# C1 深挖第 2 轮 — nop-ai-rest（WI 3.4）

- **审计日期**: 2026-10-20
- **审计员**: C1 深挖第 2 轮子代理（合并批 C1/C3[rag]/C6 之 C1 部分；盲审，仅读本轮派单指定的第 1 轮三文件）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀（F1–F11 + 口径纪律）+ C1 正文 + 附录 A 13 字段
- **编号**: 续接第 1 轮 C1-001…C1-008，本轮从 C1-009 起

## 深挖范围与方法

按派单四个深挖面逐一清点，全部以 live code + 全仓 grep 人工复核：

1. **code-analyzer 其余 29 文件**（第 1 轮仅立案 parallelStream/VectorStore/ExtensionLanguageMapper/intern 四面）：
   - `LanguageStatsAggregator` 聚合状态：`languageStats` 为实例字段（L85 `TreeMap`），唯一生产路径 `FileLanguageStats.getComprehensiveStats`（L130-151）**每次调用 new 一个 aggregator**，`FileTreeWalker.walk` 为单线程顺序回调——聚合期无跨线程共享；`LanguageStats.addFile`（L33-41）的 `+=` 复合递增在单线程下无竞态。**但 `getStats()`（L93-95）直接返回内部 TreeMap**，且 `getComprehensiveStats` 把该内部 map 原样作为公共 API 返回值（`FileLanguageStats.java:150`）——内部可变状态逃逸，立案 C1-009。
   - `MavenProject` 惰性初始化 C1 可见性面：派单所指可见性面即 `MavenModuleStructure.rootModule` 非 final 在 `load()` 后赋值（`MavenModuleStructure.java:14,81-83`）+ `modules` final TreeMap——第 1 轮 C1-007 触发时序已完整覆盖（JMM final 字段保证兜底 `modules`、`rootModule` 理论可见 null）；round 2 逐行复核**无新增字段、无新增写者**，判级维持 P3，不另立案。
   - `CodeSymbolInterning` 池语义：`internStringSet/internStringMap`（L22-38）每次返回**新建** LinkedHashSet/Map，除 `String.intern()` 本身外无共享池副作用；全部调用点经 grep 复核与 C1-008 失效面清单一致（CodeFileInfo/CodeCallInfo/CodeVariableInfo/CodeClassInfo/CodeFunctionInfo/CodeSymbol），**无新面**。
   - 其余文件（CodeAnalyzerConstants/NopAiCodeAnalyzerErrors/AccessModifier/Code*Info/ISourceCodeAnalyzer/JavaCodeFileInfoParser/JavaFileSplitter/MavenDependency*/MavenModule/MavenPomParser/MavenRepository/GitProject/CodeLineAnalyzer/FileTreeWalker/IgnoredItems）：`IgnoredItems`/`ExtensionLanguageMapper` 静态集合均为 static 块初始化（L14-16 / L10-12）+ 运行期只读（后者 mutator 面已 C1-004）；`MavenRepository.getDefault` 每次 new、无缓存；`JavaSourceFileFinder.classNameCache` ConcurrentHashMap 正确范式——零新增载体。
2. **service 22 BizModel 实例字段逐一清点**：grep 全部 `*BizModel.java` 的字段声明，22 文件中**仅 `NopAiModelBizModel.credentialProvider`（L56，protected 非 final）一个非 final 实例字段**——启动期 `@Nullable` setter 注入（L66-69），启动后无写者（第 1 轮 S11 已定性，无缺陷）；`AiModelCredentialResolverImpl.daoProvider/credentialProvider` 同属注入字段（S11）；`OrmModelSwitchedMessageWriter.daoProvider` 为 final。全部 BizModel 无缓存/集合/计数器实例字段，`AiModelCredentialResolverImpl.resolveApiKeyByCredential` 无 apiKey 缓存（明文即取即还，L118-146）。**清点结论：零新增载体。**
3. **api credential/mcp 面**：`IAiModelCredentialResolver`（SPI 接口）、`McpConstants`（常量接口）、`SecureDefault`（注解）、`IToolDefinition`/`IChatLogger`（接口）——全部无可变状态；DTO（ChatRequest/ChatOptions/ChatResponse/messages）为 per-request 实例，无 static 可变字段（基线 C1a 全噪声复核）。`ChatStreamAccumulator` 复核维持第 1 轮 S14 结论（Reserved per-response 实例，单订阅线程累积，`computeIfAbsent` L110 单线程内使用）。**零新增载体。**
4. **coder static 工具方法状态**：`AiCoderHelper` 全部静态方法为纯函数（无 static 可变字段、无 SimpleDateFormat/Random 类共享工具）；转换器 per-call 实例化经调用点逐一证实——`new MetaToJava()`（AiCoderHelper.java:156）、`new OrmModelToJava(...)`（AiOrmModel.java:155）、`new ApiModelToJava()`（AiApiModel.java:147）、`new AiOrmModelNormalizer()`（AiOrmModel.java:36/96/139）、`new DictsModelToJava()`（AiOrmModel.java:262）——`MetaToJava.svcCtx`（非 final 实例字段）随实例单线程使用；`AiOrmModel` 的 `node/normalizedNode/ormModel` 惰性 check-then-act 为 per-call 实例（工厂方法创建，无单例装配），不构成跨线程共享。`AiCoderConfig` 为 @DataBean 配置 Bean（装配期填充）。`XDefSimplifier.INSTANCE`（C1-005）、`AiOrmSqlType/GptOrmSqlType.textMap`（S9）维持原判。**零新增载体。**

### 共享可变状态增量清单（round 2 复核补充行）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| S16 | `LanguageStatsAggregator.languageStats`（实例 TreeMap） | 单线程 `addFile`（walk 回调内） | 同线程 + `getStats()` 返回后任意持有者 | 聚合期单线程；**逃逸后无** | 卫生问题 C1-009（P3） |
| S17 | `MavenModuleStructure.modules/rootModule`（final TreeMap / 非 final） | `load()`（构建期一次性） | `getModules()` 返回活引用的全部读者 | 构建期单线程；发布面归 C1-007 | 无新缺陷（C1-007 覆盖） |
| S18 | `MetaToJava.svcCtx/useDictCode`、`AiOrmModel.node/normalizedNode/ormModel` | per-call 实例构造/惰性填充 | 同实例单线程 | 逐调用实例化（调用点 grep 证实） | 无缺陷（维持第 1 轮证伪结论） |

## 新增发现

### [C1-009] code-analyzer 统计/结构容器经公开 getter 逃逸内部可变状态——返回值即内部账本

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/LanguageStatsAggregator.java:85, 93-95, 33-41`；逃逸放大点 `stats/FileLanguageStats.java:130-151`（L150 返回内部 map）；同族 `maven/MavenModuleStructure.java:13, 36-38`
- **证据片段**:
  ```java
  private final Map<String, LanguageStats> languageStats = new TreeMap<>();   // L85 内部账本

  public Map<String, LanguageStats> getStats() {
      return languageStats;                                                   // L93-95 活引用直接返回
  }
  // FileLanguageStats.getComprehensiveStats（公共 API）：
  return aggregator.getStats();                                               // L150 内部 map 即返回值
  // LanguageStats 值对象另带公共 mutator：
  public void addFile(long fileBytes, ...) { this.bytes += fileBytes; ... }   // L33-41
  ```
- **严重程度**: P3（C1 盘点类目 5「getter 泄漏内部集合」登记；工具库零生产调用者，理论风险，未具体化——按纪律第 4 条标注）
- **并发场景**: 假想集成方将 `getComprehensiveStats` 返回值交给另一线程读取（如统计展示线程），同时原线程继续对同一 `FileLanguageStats` 实例发起第二次统计并误用旧返回值，或调用方直接改写返回 map/`LanguageStats.addFile`。
- **触发时序**: 理论风险，未具体化——仓内 main/test 零并发消费者（`FileLanguageStats` javadoc 自证仅测试直调）。可写出的最近似交错：T1 持有 `getComprehensiveStats` 返回的内部 TreeMap 并迭代（`getStatsByType` 的 `entrySet().stream().sorted()`）→ T2 经 `LanguageStats.addFile` 修改值对象字段（TreeMap 结构不变故无 CME，但读到中途变更的长整型组合，统计口径撕裂）。
- **现状**: 聚合器自身单线程使用正确；缺陷形态是「公共 API 的返回值就是内部可变账本」——`getStats()` 无快照拷贝、`getComprehensiveStats` 把逃逸面直接升格为 API 契约；同族 `MavenModuleStructure.getModules()`（L36-38）同样返回活 TreeMap（其读者 `JavaSourceFileFinder` 只读流式消费，未恶化）。
- **风险**: 集成方多线程消费统计结果时得到无同步保证的读面；误改返回值静默污染后续 `getStatsByType` 输入。无正确性级后果（P3 卫生）。
- **建议**: `getStats()` 改为返回深拷贝或 `Collections.unmodifiableMap` 包装（值对象去 mutator 或改 builder）；`getComprehensiveStats` 返回前 `new LinkedHashMap<>(aggregator.getStats())` 快照。对齐同模块 `ExtensionLanguageMapper.getSupportedExtensions`（L145-147 拷贝构造）的既有正确范式。
- **失效面枚举**（F3 全类清扫）: code-analyzer 内「getter 返回内部集合」全清单 grep 复核——`LanguageStatsAggregator.getStats`（缺陷本体）；`MavenModuleStructure.getModules`（同族，读者只读，watch-only）；`MavenDependencyNode.getChildren`（L25 stream clone 用，`MavenDependencyNode.java` 返回活 list，同族 watch-only）；`ChatStreamAccumulator.getAccumulatedToolCalls`（api 根，返回新建 list，正确）；`InMemoryVectorStore`/`PgVectorStore` 返回新建集合（正确）；`FileLanguageStats` 其余 getter 返回新建结果（正确）。家族内 1 缺陷 + 2 watch-only。
- **信心水平**: 确定（形态确定）；并发触发为有趣的猜测（零生产消费者）
- **误报排除**: 不按「无状态单例被并发调用」误报处理——本条不主张存在现成竞态，而是按 C1 盘点义务登记 getter 逃逸载体并留痕；不违反「原语使用非问题」——TreeMap/TreeMap 本身合法，缺陷是发布面缺口。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现·已知未修复（卫生级，零并发消费者；getStats 活引用 L93-95 与 FileLanguageStats L150 逃逸点亲证；MavenModuleStructure.getModules/MavenDependencyNode.getChildren 两 watch-only 兄弟核对无误）

## 维度集外线索（overlay）

（本轮 C1 面内未产生跨维度新线索；全维度共享流水 CL-015 见 C6-round2、CL-016 见 C3-round2。）

## 继续判定

- 新增发现：1 条（P3×1，P0/P1/P2 均 0）；无扩展立案（C1-007/C1-008 经复核维持原状，无新增证据并入）。
- 四个指定深挖面（code-analyzer 其余 29 文件 / service 22 BizModel 逐字段 / api credential+mcp / coder static 工具方法）全部清点收敛，除 C1-009 外零新增载体。
- **判定：终止信号**——新增全为 P3 卫生登记且深挖面已收敛，无需第 3 轮 C1。

---

## 复核结论（WI 3.4 独立复核，2026-10-20）

- C1-009 复核判定：**保留(P3)**——getter 逃逸形态亲证成立，按 C1 盘点义务登记的定位正确；零生产消费者、聚合期单线程使用的事实使实际风险为卫生级，P3 校准成立。
- 本轮「MavenProject 惰性初始化无新增字段、无新增写者，判级维持 P3 不另立案」的收敛结论经回码复核（MavenModuleStructure L13-14 + MavenProject L13-15/32-37/47-57）确认无误。
- 本轮 S16/S17/S18 三行增量清单与「零新增载体」总账结论复核确认；C1 维度深挖终止信号成立（复核后无重启深挖事由）。
- 全维度复核裁定与工单结构见 C1-shared-mutable-state.md「维度复核结论」。
