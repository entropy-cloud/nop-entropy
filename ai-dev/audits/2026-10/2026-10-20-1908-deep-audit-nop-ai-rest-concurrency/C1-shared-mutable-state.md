# C1 共享可变状态与可见性 — nop-ai-rest（WI 3.4）

- **审计日期**: 2026-10-20
- **审计员**: C1 维度盲审初审员（单批次）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（F1–F11 缺陷族画像 + C1 正文 + 附录 A 13 字段格式）
- **复核状态**: 未复核

## 审计范围与口径

单批次覆盖全部 8 个源码根，逐文件审读（约 190 个手写 Java 文件 / 约 12,900 行；`_` 前缀生成文件不计入审计对象）：

| ROOT | 文件数/行数（约） | C1 结论概要 |
|---|---|---|
| `nop-ai/nop-ai-api/src/main/java` | 29f / 3,917 | SPI 定义 + DTO；无 static 可变集合；ChatStreamAccumulator 为 per-response 实例（Reserved，无生产消费者）。稀疏如预期 |
| `nop-ai/nop-ai-coder/src/main/java` | 21f / 2,050 | 转换器（OrmModelToJava/ApiModelToJava/MetaToJava/DictsModelToJava）均为**每次调用 new 实例**，实例字段（StringBuilder 等）不跨调用复用——派发简报的"转换器实例字段跨调用复用"假设经逐点核实**不成立**；AiOrmSqlType.textMap 为 static-init 只读。工具型单线程触达站点按口径降档 |
| `nop-ai/nop-ai-dsl-orm/src/main/java` | 4f / 226 | GptOrmSqlType.textMap static-init 只读；GptOrmModelParser 无实例状态。稀疏如预期 |
| `nop-ai/nop-ai-rag/src/main/java` | 20f / 1,810 | 请求触达面，不降档。InMemoryVectorStore/PgVectorStore 为主要并发面（发现 C1-002/C1-003）；RagSearch/RagIngest/RagSynthesize 服务无实例状态 |
| `nop-ai/nop-ai-code-analyzer/src/main/java` | 33f / 3,450 | static HashMap（ExtensionLanguageMapper）+ parallelStream 共享 ArrayList（C1-001/C1-004）；String.intern 全局面（C1-008 + CL-003）。工具型；本模块内无外部消费者（grep 全仓 main 代码零 import），但并行面由其自身 parallelStream 引入，不降档 |
| `nop-ai/nop-ai-dao/src/main/java` | 46f / 614 | 实体外壳 + DTO；grep 证实零 static/非 final 可变字段、零 synchronized/volatile。稀疏如预期，如实登记 |
| `nop-ai/nop-ai-service/src/main/java` | 27f / 863 | 22 个 `*BizModel` 单例逐一核查：除注入字段（启动期 setter 注入，启动后只读）外均为无状态；NopAiModelBizModel 的 save/delete/deleteByQuery 读-后-写 TOCTOU 登记为 C2 集外线索 |
| `nop-ai/nop-ai-app/src/main/java` | 1f / 32 | main 入口 `static String[] globalArgs`（C1-006） |

**零源模块登记**：8 个源码根均有手写源码，无零源根；`nop-ai-dao` 为最稀疏根（仅实体外壳 + 1 个 DTO）。

**口径声明**：
- 按 C1 正文盘点五类载体（singleton 实例字段 / static 可变字段 / 全局注册表 / ThreadLocal / 参数逃逸）。ThreadLocal：基线 C1c 节与全仓 grep 双重证实 8 根内**零 ThreadLocal 使用**。
- 数量结论人工复现：`static` 非 final 字段经 grep + 逐文件人工确认仅 3 处真实字段（XDefSimplifier.INSTANCE、NopAiApplication.globalArgs、PgVectorStore/AiModelTextEmbedding 的 volatile 字段为 static 检索噪声）；`static final` 可变集合 4 处（ExtensionLanguageMapper HashMap、IgnoredItems×2 HashSet、AiOrmSqlType/GptOrmSqlType textMap×2），逐一定性见下表。
- 基线 `_tmp/cra-baseline-nop-ai-rest.txt` C1a/C1b/C1c 节全部条目已逐条核对；基线行多为 static 方法/常量的检索噪声，真实载体已全部纳入下表。
- 降档口径：code-analyzer/coder 工具型站点若仅单线程触达，按站点校准降档（如 JarResolverCollection/MavenProject 惰性初始化、FileLanguageStats.localExtensionMapping）；rag/service 请求触达面不降档；并发类证据全部给出 T1/T2 时序，写不出时序的标注定级理由。

### 共享可变状态清单（字段 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| S1 | `JavaSourceFileFinder.getAllJavaSourceFiles` 局部 `ArrayList results`（经 parallelStream 跨线程共享） | ForkJoinPool 多 worker（`findJavaFiles` L236 `add`） | 同流归并后返回值 | **无**（parallelStream.forEach 内对普通 ArrayList 并发 add） | **缺陷 C1-001**（P1） |
| S2 | `PgVectorStore.schemaReady`（volatile boolean，实例字段，单例装配下跨请求共享） | `ensureSchema` L102（首个未命中线程） | `ensureSchema` L83 快路径 | volatile 可见性；**无互斥**（check-then-act 非原子），且 flag 作用域（实例）与 schema 作用域（collection 表）不匹配 | **缺陷 C1-002**（P1） |
| S3 | `InMemoryVectorStore.buckets`（ConcurrentHashMap → CopyOnWriteArrayList） | `store`/`delete`（removeIf+add 复合） | `search`/`delete` | 容器原语线程安全；**upsert 复合操作非原子** | **缺陷 C1-003**（P1） |
| S4 | `ExtensionLanguageMapper.GLOBAL_EXTENSION_TO_LANGUAGE`（static final HashMap） | static-init（安全）+ 公开 API `addCustomMapping(s)` L159-169（运行期可写） | `getLanguage` L138-140、`getSupportedExtensions/Languages` L145-154（拷贝构造遍历） | **无**（读侧遍历 vs 写侧 put） | **缺陷 C1-004**（P2，仓内无运行期写者） |
| S5 | `XDefSimplifier.INSTANCE`（public static **非 final**） | static-init；外部可重新赋值（public 非 final 字段） | 测试代码 `XDefSimplifier.INSTANCE.simplify` | 类初始化 HB；实例无状态 | 卫生问题 C1-005（P3） |
| S6 | `NopAiApplication.globalArgs`（static 非 final String[]） | `main` L29 | Quarkus StartupEvent 观察者 L21 | 依赖 Quarkus 内部线程启动 HB，语言层面无保证 | 卫生问题 C1-006（P3） |
| S7 | `MavenProject.moduleStructure` / `javaFileFinder`（实例字段，非 volatile 惰性初始化） | `loadModuleStructure` L47-57 / `getJavaFileFinder` L32-37 | 同类 getter | **无**（check-then-act；重复构造良性；被发布对象字段全 final → JMM final 字段保证兜底） | 理论风险 C1-007（P3，仓内无并发调用者） |
| S8 | `IgnoredItems.IGNORED_DIRECTORIES/EXTENSIONS`（static final HashSet） | 仅 static-init | `isIgnoredDirectory/Extension` contains | 类初始化 HB；运行期只读 | 无缺陷 |
| S9 | `AiOrmSqlType.textMap` / `GptOrmSqlType.textMap`（static final CaseInsensitiveMap） | 仅 static-init | `getStdSqlType` get | 类初始化 HB；运行期只读 | 无缺陷 |
| S10 | `NopAiChatResponseBizModel.ROW_MAPPER`（static final IRowMapper） | static-init（BeanRowMapper.of 工厂产物） | `summarizeByModel` | 类初始化 HB；mapper 无状态 | 无缺陷 |
| S11 | `NopAiModelBizModel.credentialProvider`、`AiModelCredentialResolverImpl.daoProvider/credentialProvider`（protected 可变注入字段，单例） | 启动期 @Inject setter | 请求线程 | IoC 装配先于服务可用；启动后无写者 | 无缺陷（注入生命周期） |
| S12 | `AiModelTextEmbedding.cachedDimension`（volatile int） | `toFloatArray`（同步/异步 thenApply 线程均可写） | `getDimension` | volatile + int 原子写；收敛写（同模型同值），-1 期语义已 javadoc 声明 | 无缺陷（良性竞态） |
| S13 | `CodeSymbolInterning` → `String.intern()` 全局字符串池 | 分析线程（CodeFileInfo.java:202-207 等对 md5/summary 等高基数串驻留） | 全 JVM | intern 原生表自身线程安全 | 无可见性缺陷；内存面登记 CL-003 |
| S14 | `ChatStreamAccumulator` 实例字段（StringBuilder/LinkedHashMap） | 单流消费线程 | 同一流 | per-response 实例，不共享（且 Reserved 无生产消费者） | 无缺陷 |
| S15 | coder 转换器实例字段（`OrmModelToJava.sb` 等） | 每次 `new` 后单线程 | 同实例 | 逐调用实例化（grep 证实无单例装配） | 无缺陷（简报假设证伪） |

---

## 发现

### [C1-001] getAllJavaSourceFiles 用 parallelStream 向普通 ArrayList 并发写入

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/maven/JavaSourceFileFinder.java:165-173`（写点 `:228-241, 236`）
- **证据片段**:
  ```java
  public List<File> getAllJavaSourceFiles() {
      List<File> results = new ArrayList<>();
      moduleStructure.getModules().values().parallelStream()
              .map(this::resolveModuleDirectory)
              .forEach(moduleDir -> findJavaFilesInModule(moduleDir, results));
      return results;
  }
  // findJavaFiles(...) 内：
  results.add(file);   // L236，多个 ForkJoin worker 并发执行
  ```
- **严重程度**: P1
- **并发场景**: 调用线程触发的 ForkJoinPool.commonPool 多个 worker 线程（模块数 > 1 时每模块一个任务，`findJavaFiles` 递归遍历各自目录树后向同一个 `results` add）。
- **触发时序**: T1（commonPool worker A）执行 `findJavaFiles` L236 `results.add(file)`，读到 `size=100` 尚未回写 → T2（worker B）同时执行 L236 `add`，也读到 `size=100` → 两个元素都写入 `elementData[100]`，`size` 最终 101：一个 File 静默丢失；或 T1 触发 `grow()` 期间 T2 持旧数组写入 / 越界 → `ArrayIndexOutOfBoundsException`。多核机器上模块数 ≥ ForkJoin 并发度（≈核数-1）且各模块均有 java 文件时近乎必然触发。
- **现状**: 收集容器按单线程假设使用，却在并行流中被多线程共享写入，无任何同步；结果集静默缺文件（统计/索引工具的语义违约）或偶发 AIOOBE。
- **风险**: 该方法是工具"获取全部 Java 源文件"的主入口，丢文件对下游（代码索引、统计）是静默数据损坏，且不可复现、随机器核数变化，排查成本高。
- **建议**: 改为函数式收集（`parallelStream().map(...).collect(Collectors.toList())` / `flatMap`），或每模块收集局部 List 后在终止操作合并（`reduce`/`Collector` 语义天然合并）；复用平台 `io.nop.commons.collections` 无需自造。
- **失效面枚举**（F3 全类清扫）: 全 8 根 grep `parallelStream|parallel()` 仅此 1 处。同族"共享可变收集容器"兄弟逐一核查：`findJavaSourceFilesBySimpleName` L132-148（顺序 forEach，单线程安全）；`searchInAllModules` L115-121（顺序 stream，安全）；`findBySimpleNameInAllModules` L153-160（顺序，安全）；`JavaFileSplitter.splitIntoChunks`（局部 List，安全）；`MavenDependencyTreeParser.parse`（局部，安全）。家族内仅本条违规。
- **信心水平**: 确定
- **误报排除**: 不是"原语使用即问题"的误报——ArrayList 本身合法，缺陷在复合用法（并行流 + 共享非线程安全容器）；也非测试专用简化模型，这是 main 代码的公共 API。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现·已知未修复（潜伏：`getAllJavaSourceFiles` 全仓零调用方复核属实——并行面内生于方法体，「仅单线程触达」工具型降档不适用，任一首次调用即在多模块+多核环境走主路径触发；接线即生效，附回升条款）——同站点线索 CL-010（C6 overlay）吸收并入本条，不重复计数

### [C1-002] PgVectorStore.schemaReady 标志作用域错位 + ensureSchema 检查-然后-行动无互斥

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/PgVectorStore.java:52, 82-103`（调用点 `:116, 153, 189`；集合解析 `:217-223`）
- **证据片段**:
  ```java
  private volatile boolean schemaReady;                    // L52，per-instance

  private void ensureSchema(Connection connection, String collection) throws SQLException {
      if (schemaReady) {                                   // L83：不区分 collection
          return;
      }
      ...
      try (PreparedStatement stmt = connection.prepareStatement(
              "CREATE TABLE IF NOT EXISTS " + collection + " ...")) {
          stmt.execute();
      }
      schemaReady = true;                                  // L102
  }
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker 线程池并发调用同一单例 `PgVectorStore`（RAG ingest/search/delete 三入口，`IVectorStore` 注入 NopAiRagBizModel）；首访 DDL 与快速路径读之间无互斥。
- **触发时序**（两部分）:
  1. **作用域错位（确定性）**：T1 请求 `store(options={collectionName:"docs_a"})` → `ensureSchema` 建 `docs_a` 表并置 `schemaReady=true` → T2 请求 `search(options={collectionName:"docs_b"})`（RagIngestService/RagSearchService 以 indexId 作 collectionName，多索引共享同一 store bean 是装配常态）→ L83 快路径跳过 DDL → `INSERT/SELECT INTO docs_b` 抛 `relation "docs_b" does not exist` → 被 L134 包装为 `ERR_AI_CORE_INVALID_ARGUMENT` 的 NopException，该索引永久不可用（每次都跳过建表）。
  2. **无互斥首访竞态**：T1、T2 同时首访同一 collection，均见 `schemaReady=false`，并发执行 `CREATE TABLE IF NOT EXISTS`——PostgreSQL 下并发同名 DDL 存在 pg_catalog 唯一键冲突（duplicate key on pg_type）的已知竞态，败者抛 SQLException → 包装为 NopException，尽管表实际已建成（首次请求伪失败，重试才成功）。
- **现状**: volatile 只解决了标志可见性，未解决 (a) check-then-act 原子性、(b) 标志（实例粒度）与资源（collection 粒度）的粒度匹配。
- **风险**: 生产装配（Delta 替换 InMemory → PgVectorStore）下，第二个及之后的索引/集合全部功能不可用且报错误导（INVALID_ARGUMENT 掩盖"表未建"根因）；首访并发窗内出现抖动性失败。
- **建议**: 按 collection 记就绪集（`ConcurrentHashMap<String, Boolean> schemaReadyByCollection`，`putIfAbsent` 后双检），或去掉快路径 + DDL 幂等直接执行；如需消除首访竞态，用 `computeIfAbsent(collection, k -> ddlResult)` 串行化每集合的 ensureSchema。同时 `store/delete/search` 的 SQLException 不应一律映射 ERR_AI_CORE_INVALID_ARGUMENT（错误处理维度另行跟进）。
- **失效面枚举**（F3 全类清扫）: `ensureSchema` 调用点 3 处——`store` L116、`delete` L153、`search` L189，三处同受标志作用域影响，状态一致。同族"惰性就绪标志"兄弟：`AiModelTextEmbedding.cachedDimension`（volatile + 收敛写，良性）；`MavenProject.moduleStructure/javaFileFinder`（见 C1-007）；`InMemoryVectorStore` 无就绪标志（per-bucket computeIfAbsent，正确范式）。仓内无第二个 IVectorStore 实现携带同类标志。
- **信心水平**: 确定（时序 1 为确定性缺陷，无需并发窗口；时序 2 为常规 DDL 竞态）
- **误报排除**: 不是"声明边界"——owner doc（vector-store.md A1-A4）声明的是契约语义而非单集合限制；构造器 `defaultCollection` + per-call `resolveCollection` 覆盖表明多集合是设计内路径。也不是"原语使用"误报：volatile 使用正确，缺陷在复合逻辑。
- **复核状态**: 未复核
- 复核状态: 保留(P1)·吸收进主登记 C6-001｜新发现·已知未修复——经裁定为 PgVectorStore.schemaReady 三登记（C1-002=C3-004=C6-001）同一缺陷的并发面视角，非独立工单；本条独立增量 = volatile check-then-act 首访竞态子面（时序 2，经 C3-004 校准修正为瞬态面：pg_type duplicate key 竞态实存但重试可过，非永久），随主登记同修闭合；时序 1 与 C6-001 时序(a)、C3-004 本体为同一确定性缺陷，行号/调用点/触发链逐项回码复核一致（L52/83/102、L116/153/189、L217-223 亲证）

### [C1-003] InMemoryVectorStore store() 的 removeIf+add 复合 upsert 非原子，并发同 id 写产生重复条目

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/InMemoryVectorStore.java:59-76`（关键行 `:69-70`；`update` 委托 `:90-92`）
- **证据片段**:
  ```java
  public VectorStoreResult store(List<VectorData> vectorDataList, VectorStoreOptions options) {
      CopyOnWriteArrayList<VectorData> bucket = bucket(options);   // computeIfAbsent，安全
      for (VectorData data : vectorDataList) {
          ...
          bucket.removeIf(existing -> id.equals(existing.getMetadata(META_ID)));  // L69
          bucket.add(data);                                                       // L70
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 `InMemoryVectorStore` 单例（K3 RAG 管线缺省装配，见 NopAiRagBizModel javadoc）被 HTTP worker 并发调用：同 docId 的重复 ingest（重试/并发提交）落在同一 bucket。
- **触发时序**: T1 执行 L69 `removeIf(id)`（bucket 空判，快照中无该 id）→ T2 执行 L69 `removeIf(id)`（同样判空通过）→ T1 执行 L70 `add(data1)` → T2 执行 L70 `add(data2)` → bucket 中存在两条同 `META_ID` 的 VectorData。后续 `search`（L104 迭代 COWAL 快照）将同 id chunk 返回两次（top-K 名额被占用、合成上下文重复引用）；`delete(id)` 的 `removeIf` 会把两条一并删除（删除自愈但中间窗口语义已破）。
- **现状**: 类 javadoc 明文声明"线程安全"（L23）；容器原语（ConcurrentHashMap + CopyOnWriteArrayList）选型正确，但按 id upsert 是跨两个容器调用的复合操作，无任何互斥。
- **风险**: 违反 store=upsert-by-id 的隐含契约：并发窗口内检索结果含重复 doc，RAG 合成上下文重复计权；无崩溃但检索质量与幂等语义静默劣化。
- **建议**: 用 `ConcurrentHashMap<String(id), VectorData>` 替代 COWAL 作 bucket 载体（`put` 天然原子 upsert，`search` 对 values 迭代快照），key 用 `tenant/collection` 外层 bucket 不变；或将 upsert 收敛为单把 per-bucket 锁。复用平台 `io.nop.commons.cache.ICache` 亦可。
- **失效面枚举**（F3 全类清扫）: 同类复合操作兄弟逐一核查——`delete` L79-87 仅单次 `removeIf`（COWAL removeIf 内部持锁、原子，安全）；`search` L95-119 只读快照（安全）；`bucket()` L54-56 `computeIfAbsent`（原子，安全）；`PgVectorStore.store` L106-141 走 SQL `ON CONFLICT ... DO UPDATE` 单语句 upsert（数据库层原子，正确范式——反证本条缺陷属实现疏漏而非契约要求）；`IVectorStore` 基类单元素重载最终委托上述实现（继承同一状态）。家族内仅 `store`/`update` 两入口违规（`update` 委托 `store`）。
- **信心水平**: 确定
- **误报排除**: 不是"COWAL/CHM 原语使用"误报——报的是复合操作原子性缺失（方法学第 4 步"检查-然后-行动"明确对照项），且类自身声明了线程安全契约，属契约违约而非品味问题。
- **复核状态**: 未复核
- 复核状态: 保留(P1·触发条件化)｜新发现·已知未修复——T1/T2 序列回码复核成立（COWAL removeIf 快照判定 + add 各自原子、组合非原子）；复核补充触达事实：rag 缺省链不触发（RagIngestService L62-77 从不设 META_ID，逐 chunk 随机 UUID），触发需调用方显式携同 id 并发 upsert（重试/多客户端幂等写）——PgVectorStore 的 ON CONFLICT 原子 upsert 反证 upsert-by-id 是 owner doc 登记的设计内契约（META_ID「K2 契约」）且类 javadoc L23 明文线程安全，契约违约面成立故维持 P1，附使用方式条件标注；同站点线索 CL-008（C3 overlay）吸收并入本条；CL-012 为同站点不同缺陷面（无界账本 F4），保留独立线索

### [C1-004] ExtensionLanguageMapper 全局 static HashMap 暴露运行期公开写入口，读侧无同步遍历

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/ExtensionLanguageMapper.java:12, 138-169`
- **证据片段**:
  ```java
  private static final Map<String, String> GLOBAL_EXTENSION_TO_LANGUAGE = new HashMap<>();

  public static String getLanguage(String extension) {
      return GLOBAL_EXTENSION_TO_LANGUAGE.get(extension.toLowerCase());       // L138-140
  }
  public static Set<String> getSupportedExtensions() {
      return new HashSet<>(GLOBAL_EXTENSION_TO_LANGUAGE.keySet());            // L145-147 遍历拷贝
  }
  public static void addCustomMapping(String extension, String language) {
      GLOBAL_EXTENSION_TO_LANGUAGE.put(extension.toLowerCase(), language);    // L159-161 运行期写
  }
  ```
- **严重程度**: P2（理论风险，仓内无运行期写者，未具体化——按纪律第 4 条降档并标注）
- **并发场景**: 假想集成方在分析进行中调用 `addCustomMapping(s)`（public API，语义上即"运行期扩展"）：写线程 vs 分析线程（`FileLanguageStats.getLanguageByExtension` → `getLanguage`，或 `getSupportedExtensions/Languages` 的遍历拷贝）。
- **触发时序**: （需仓外写者，未具体化）T1 集成线程执行 L160 `put` 触发 HashMap resize → T2 分析线程同时执行 L146 `new HashSet<>(keySet())` 遍历 → 遍历读到的可能是迁移中的桶结构：丢条目、或（JDK8+ 无死循环但）哈希桶链接断裂导致漏读/重复；`get` 与 `put` 并发同理可能读到未完全发布的 Entry。
- **现状**: static-init 路径安全（类初始化 HB），缺陷仅在公开的运行期 mutator 与无同步读者并存；仓内 main/test 代码 grep 证实当前**零调用者**，风险由集成方触发。
- **风险**: 集成方按 API 语义在多线程环境使用时产生难复现的漏语言/异常；作为公共 API 面长期暴露。
- **建议**: 三选一：(a) mutator 改为返回新快照的 copy-on-write（读者持不可变 Map 引用）；(b) 载体换 `ConcurrentHashMap`；(c) 初始化后包装 `Collections.unmodifiableMap` 并把自定义映射移到实例级（对齐同模块 `FileLanguageStats.localExtensionMapping` 的实例化范式——该类同形 mutator 因实例级生命周期而风险更低）。
- **失效面枚举**（F3 全类清扫）: 模块内 static 可变集合全清单及判定——`IgnoredItems.IGNORED_DIRECTORIES/EXTENSIONS`（static-init only，无 mutator，安全）；`JavaSourceFileFinder.EXCLUDED_DIRS`（`Set.of` 不可变，安全）；`AiOrmSqlType.textMap`/`GptOrmSqlType.textMap`（static-init only，无 mutator，安全）；`FileLanguageStats.localExtensionMapping`（实例字段 + 同形公开 mutator `addLocalMapping(s)` L69-79，单线程触达站点降档，风险形态与本条同族，若多线程共享实例则同缺陷——登记为 watch-only 兄弟）。
- **信心水平**: 确定（风险形态确定）/ 触发依赖仓外调用者（很可能）
- **误报排除**: 不是"无状态工具类被并发调用"误报——本类有公开 mutator，属真实可变全局状态；不是"有界声明缓存"误报——无任何同步/容量声明。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现·已知未修复（潜伏：`addCustomMapping/addCustomMappings` 全仓 grep 零调用者复核属实；工具型站点单线程触达降档已由初审正确援引）

### [C1-005] XDefSimplifier.INSTANCE 为 public static 非 final 可变引用

- **文件**: `nop-ai/nop-ai-coder/src/main/java/io/nop/ai/coder/xdef/XDefSimplifier.java:6`
- **证据片段**:
  ```java
  public class XDefSimplifier {
      public static XDefSimplifier INSTANCE = new XDefSimplifier();
  ```
- **严重程度**: P3
- **并发场景**: 任何线程重新赋值 `XDefSimplifier.INSTANCE = ...`（public 非 final，编译器与平台均不阻止）与其他线程读取该引用之间，无 HB 保证；读者可能见到旧实例或（非法干预下）未完全发布的实例。
- **触发时序**: 理论风险，未具体化——仓内 grep 证实除 static-init 外零写入者，消费仅测试代码（TestXDefSimplifier 4 处读引用）；当前无运行期写者即无具体交错。
- **现状**: 单例引用以最宽可见性 + 最弱不可变性声明；实例本身无状态，故当前实际风险为零，属防御性卫生问题。
- **风险**: 集成方替换/代理 INSTANCE（如加装饰器）时引入静默的跨线程可见性问题；或反序列化/反射工具误改。
- **建议**: 加 `final`（一行修改）；同族对照平台惯例 `XDslCleaner.INSTANCE`（nop-xlang，final 惯例）。
- **失效面枚举**（F3 全类清扫）: 8 根内 `public static` 非 final 字段全量 grep——本条 + `NopAiApplication.globalArgs`（C1-006）共 2 处；其余 static 命中均为方法或 final 常量。同族"public static 单例"兄弟 `XDslCleaner.INSTANCE`/`XDslExtracter.INSTANCE`（AiCoderHelper 引用）在 nop-xlang 内声明为 final，状态良好。
- **信心水平**: 确定（问题存在）；影响为卫生级
- **误报排除**: 不按"低频路径粗粒度同步"类误报处理——本条为 P3 卫生登记，未夸大为并发缺陷；不违反"原语使用非问题"校准，因为非 final static 引用是真实的发布面缺口。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现·已知未修复（卫生级；INSTANCE 写者零、消费仅 TestXDefSimplifier 复核属实，实际风险为零）

### [C1-006] NopAiApplication.globalArgs static 可变数组跨线程发布无语言级保证

- **文件**: `nop-ai/nop-ai-app/src/main/java/io/nop/ai/app/NopAiApplication.java:16, 21, 29`
- **证据片段**:
  ```java
  static String[] globalArgs;                 // L16

  public void start(@Observes StartupEvent event) {
      QuarkusIntegration.start();
      new NopApplication().run(globalArgs);   // L21，Quarkus 启动线程读
  }
  public static void main(String... args) {
      globalArgs = args;                      // L29，main 线程写
      Quarkus.run(args);
  }
  ```
- **严重程度**: P3
- **并发场景**: main 线程（写）与 Quarkus 容器启动/事件分发线程（StartupEvent 观察者回调）之间，仅靠 Quarkus 内部实现（线程 start/executor 提交）间接建立 HB，语言层面无保证。
- **触发时序**: 理论风险，未具体化——Quarkus.run 内部必然创建/提交工作线程（JMM Thread.start HB 链），实践上从不复现；但正确性论证依赖框架实现细节而非本类代码。
- **现状**: static 非 final 可变字段承载"启动参数"这一事实不可变数据，写后无 volatile/final 保护。
- **风险**: 若 Quarkus 版本改变事件分发时机（如已有池化线程先行）或该类被非 Quarkus 路径复用，`run(null)` 静默启动；启动参数丢失属启动期难排查故障。
- **建议**: 改为 `private static volatile String[] globalArgs`（最小改动），或在 `start()` 内改用 `Quarkus.getCurrent()`/构造期注入参数；亦可将 args 经 `NopApplication` 构造器传递消除静态字段。
- **失效面枚举**（F3 全类清扫）: 8 根内 static 非 final 字段仅 C1-005/C1-006 两处（grep 证实）；nop-ai-app 根仅此 1 个文件，无其他兄弟。
- **信心水平**: 确定（形态）/ 实际触发为有趣的猜测（依赖框架内部时序）
- **误报排除**: 启动路径低频，不按 P1/P2 报；保留为 P3 是因为 static 非 final 可变字段是 C1 盘点的五类载体之一，须留痕而非静默豁免。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现·已知未修复（卫生级，启动期低频；L16/21/29 亲证）

### [C1-007] MavenProject 双字段惰性初始化为非同步 check-then-act

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/maven/MavenProject.java:13-15, 32-37, 47-57`
- **证据片段**:
  ```java
  private MavenModuleStructure moduleStructure;
  private JavaSourceFileFinder javaFileFinder;

  public JavaSourceFileFinder getJavaFileFinder() {
      if (javaFileFinder == null) {
          javaFileFinder = new JavaSourceFileFinder(loadModuleStructure());
      }
      return javaFileFinder;
  }

  public MavenModuleStructure loadModuleStructure() {
      if (this.moduleStructure != null)
          return this.moduleStructure;
      MavenModuleStructure structure = new MavenModuleStructure();
      structure.load(getProjectDir());            // 递归扫全仓 pom，成本高
      if (structure.getModuleCount() > 0)
          this.moduleStructure = structure;
      return structure;
  }
  ```
- **严重程度**: P3（理论风险，未具体化：仓内无并发调用者——`new MavenProject` 全仓 main 代码零外部调用点，工具库由集成方驱动）
- **并发场景**: 假想集成方多线程共享同一 `MavenProject` 并并发调用 `findJavaFileByClassName` 等入口。
- **触发时序**: T1 执行 L48 判空通过进入 `structure.load(...)`（重 IO）→ T2 同时判空通过，重复整仓扫描 → 两者各自发布实例，`this.moduleStructure` 后写者胜；若无并发且仅单线程，另见：T1 未加 volatile，T2 读到非 null 引用但对象字段未完全发布——本例被 `JavaSourceFileFinder` 字段全 final（L35-36）的 JMM final 字段保证兜底，`MavenModuleStructure.modules` 为 final TreeMap、`rootModule` 非 final 在 `load` 后赋值，理论上 T2 可见 rootModule=null。
- **现状**: 双重检查无锁、无 volatile；后果上限是重复构建与 `getRootModule()` 在窄窗口返回 null，无互斥破坏。
- **风险**: 集成方多线程使用时偶发 NPE（`getModuleNode()` on null root）或整仓重复扫描的性能毛刺。
- **建议**: 字段加 `volatile` + 同步惰性初始化，或改为构造期加载（`load` 本就是构造后一次性数据）；复用平台惰性初始化设施（`io.nop.commons.util.LazyValue` 类基础设施）。
- **失效面枚举**（F3 全类清扫）: code-analyzer 内惰性初始化全清单——本条 2 字段；`JarResolverCollection.addJar` L37-45（containsKey/put check-then-act，实例级 + 单线程触达降档，watch-only 兄弟）；`JavaSourceFileFinder.classNameCache`（ConcurrentHashMap + get/put，正确范式）；`PgVectorStore.schemaReady`（见 C1-002，rag 面不降档）；`AiModelTextEmbedding.cachedDimension`（volatile，良性）。仓内无第二处"跨线程共享的非 volatile 惰性单例"。
- **信心水平**: 确定（形态）；后果为很可能
- **误报排除**: 未按 P1/P2 报正因写不出仓内可触发的具体时序（校准第 4 条），但作为 C1 惰性初始化盘点项登记，非"看到共享字段就报"。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现·已知未修复（理论风险标注成立：`new MavenProject` 全仓 main 零调用复核属实，仅 3 处测试引用；L13-15/32-37/47-57 与 MavenModuleStructure L13-14 亲证）

### [C1-008] CodeSymbolInterning 对高基数字符串（md5/summary）执行 String.intern，全局驻留池被唯一值灌入

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/CodeSymbolInterning.java:17-37`（高基数调用点 `code/CodeFileInfo.java:202-207`）
- **证据片段**:
  ```java
  public static String internString(String str) {
      return str != null ? str.intern() : null;
  }
  // CodeFileInfo.intern()（每文件信息对象归一化时）：
  filePath = CodeSymbolInterning.internString(filePath);
  md5 = CodeSymbolInterning.internString(md5);          // md5 几乎全库唯一
  summary = CodeSymbolInterning.internString(summary);  // AI 生成的摘要文本，长且唯一
  ```
- **严重程度**: P3
- **并发场景**: 全 JVM 共享的 native StringTable 被 N 个分析线程/多次分析运行反复写入；intern 原生表自身线程安全（HotSpot 串行化），**无可见性缺陷**——本条是全局共享池的使用方式问题。
- **触发时序**: 无竞态时序（非并发正确性缺陷）：每次分析大仓时，每个 CodeFileInfo 的 md5（全库唯一）与 summary（AI 文本，唯一长串）逐一进入全局 StringTable；表中条目随 live 引用存活，分析对象集存活期间全表膨胀，intern 查找退化为全局锁竞争点。
- **现状**: interning 的目的是去重压缩，但被施加在基数≈样本数的字段上，去重率为零、副作用（全局表膨胀 + 原生表锁竞争）为纯负。
- **风险**: 大仓分析时字符串驻留表与堆占用上升；与 C1-001 的 commonPool 并发叠加时形成原生表热点。无正确性后果。
- **建议**: intern 面收窄到低基数字段（packageName、language、artifactId、ownerClassName 等，CodeClassInfo/CodeFunctionInfo 中的用法即此类）；`md5`/`summary`/`filePath` 改为普通引用。或换用自管的 `Map<String,String>` 去重表（平台 `MapCache`）限定生命周期。
- **失效面枚举**（F3 全类清扫）: `internString` 全部调用点——CodeFileInfo.java:202-207（含 md5/summary，违规面）、CodeCallInfo.java:42-43、CodeVariableInfo.java:29-30、CodeClassInfo.java:115-117、CodeFunctionInfo.java:88-94、CodeSymbol.java:42-44（以上均为低基数符号名，intern 语义正确）；`internStringSet/internStringMap` 本身仅转发 intern（继承调用点语义）。需修的兄弟字段：`CodeFileInfo.md5`、`CodeFileInfo.summary`（filePath 视分析规模酌情保留）。
- **信心水平**: 确定（使用形态确定，后果为性能/内存面）
- **误报排除**: 明确不作为并发正确性缺陷报告（intern 线程安全，校准第 2 条"原语使用不是问题"）；按 C1 盘点义务登记全局池使用方式并转 C5 内存面线索（CL-003）。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现·已知未修复——intern 调用点失效面枚举逐行亲证（CodeFileInfo L202-207 含 md5 L204/summary L207；CodeCallInfo 42-43、CodeVariableInfo 29-30、CodeClassInfo 115-117、CodeFunctionInfo 88-94、CodeSymbol 42-44 全部属实）；跨单元同族对照：WI 3.1 C5-005（Embedding 响应装箱放大 P3，nop-ai-core EmbeddingServiceImpl）为同族内存放大、不同站点不同机制，无双立，判级校准一致

---

## 维度集外线索（overlay C2/C4/C5/C7/C8/C9 + 非 rag C3）

### [nop-ai-rest-CL-001] NopAiModelBizModel save/delete/deleteByQuery 的读-后-写 TOCTOU 可复活 D6-02 凭证引用死锁（C2）

- **文件**: `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiModelBizModel.java:78-83, 110-131, 151-160, 174-181`
- **线索**: 三个 mutation 均采用"事务内先读旧 credentialId → 持久化 → 事后 reconcile"结构，读与写之间无行锁/版本校验（`findFirstByExample`/`getEntityById` 均为普通读）。T1 `delete(id)` 读到 `credentialId=A` → T2 `save(id,{credentialId:B})` 提交（register(B)）→ T1 `super.delete` 删行 + `unregister(A)` → usage(B) 行残留指向已删模型，`NopCredentialBizModel.delete` 的引用计数拦截即 javadoc 自述已被 D6-02 关闭的"运维死锁"在并发下复活。`deleteByQuery` 的 `findList` 快照与 `super.deleteByQuery` 之间同窗更宽（整查询时长）。
- **同族清单**: save（L78-83）、delete（L151-160）、deleteByQuery（L174-181）三对读-后-写；batchDelete 经虚分派复用 delete（同受影响）。
- **建议方向**: reconcile 前在事务内重读行版本（乐观锁已在 ORM 管线内），或以 `SELECT ... FOR UPDATE` 语义取旧值。

### [nop-ai-rest-CL-002] PgVectorStore.store 批量写入无事务包裹，executeBatch 中途失败为"半个成功"（C2，F2）

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/PgVectorStore.java:106-141`
- **线索**: `dataSource.getConnection()` 默认 auto-commit；批量 `addBatch` + `executeBatch`（L129-132）中途失败时，已执行的批内语句已各自提交，无回滚、无部分成功标记，调用方仅得一个 NopException；重试 `store` 因 upsert（ON CONFLICT DO UPDATE）幂等可自愈，但 `delete` + `store` 组合或含维度冲突批的中间态对外可见（search 窗口内见到半批数据）。失败还统一包装为 ERR_AI_CORE_INVALID_ARGUMENT，掩盖根因。
- **建议方向**: `connection.setAutoCommit(false)` + try-catch 回滚；错误码按 SQLState 分类（至少与参数校验失败区分）。

### [nop-ai-rest-CL-003] CodeSymbolInterning 的 String.intern 全局池随分析规模无界增长（C5，F7 近亲）

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/CodeSymbolInterning.java:17-37`；`code/CodeFileInfo.java:202-207`
- **线索**: 见 C1-008。md5/summary 等唯一值持续进入 JVM 全局 StringTable，驻留条目与存活分析结果集同生命周期，无清理/上限/TTL 通路（F4/F7 家族的内存形态：只增不删的全局账本）。分析超大仓或常驻进程反复分析时堆占用单调上升。
- **建议方向**: intern 面收窄至低基数字段；或自管带容量上限的去重 Cache（平台 `CacheConfig`/`GlobalCacheRegistry`）。

### [nop-ai-rest-CL-004] InMemoryVectorStore.buckets 按 tenant/collection 无界分裂，条目仅显式 delete 才回收（C5/C8）

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/InMemoryVectorStore.java:41-56`
- **线索**: `buckets` ConcurrentHashMap 的 key 空间由调用方 `bucketKey(options)`（tenant + collection 拼接）决定，无容量声明、无淘汰；每个 bucket 为 COWAL，`store` 只增。定位是"开发/测试后端"（javadoc K2 裁定），但无装配守卫阻止生产误用（不注册 default bean 是唯一防线）；生产误接时内存随索引数 × 文档数线性增长且无信号。
- **建议方向**: javadoc 或装配处加"非生产"显式声明 + 容量上限（复用平台 `CacheConfig` 上限语义），或提供 bucket 级 `drop(collection)` 管理动作。

### [nop-ai-rest-CL-005] FileTreeWalker 对读取失败文件静默跳过，统计结果无信号地少算（C9，F9）

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/FileTreeWalker.java:39-58`
- **线索**: `visitFile` 内 catch(IOException) 后直接 CONTINUE（无日志），`visitFileFailed` 同样静默 CONTINUE——权限收缩/文件竞争删除/编码异常的文件从统计中无声消失（"吞异常 → 状态不一致"族：最终统计值看起来合法但系统性偏小）。同文件 `CodeLineAnalyzer.analyzeFileLines`（CodeLineAnalyzer.java:60-63）对任意 Exception 兜底为 `totalLines=1, codeLines=1`，同样是静默降级。
- **建议方向**: 至少 LOG.warn 计数汇总（walk 结束时输出 skipped=N）；与 `JavaCodeFileInfoGenerator` 的 `nop.ai.parse-java-file-fail`（error 级）口径对齐。

---

## 数量小结

| 严重度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 3 | C1-001（parallelStream 共享 ArrayList 竞态）、C1-002（PgVectorStore schemaReady 作用域+CTA）、C1-003（InMemoryVectorStore upsert 复合操作竞态） |
| P2 | 1 | C1-004（ExtensionLanguageMapper 全局可变映射，理论风险降档） |
| P3 | 4 | C1-005（INSTANCE 非 final）、C1-006（globalArgs）、C1-007（MavenProject 惰性初始化）、C1-008（intern 高基数串） |
| CL 线索 | 5 | CL-001~005（C2×2、C5×2、C9×1） |

---

## 维度复核结论（WI 3.4 独立复核，2026-10-20）

- **复核员**: WI 3.4 独立复核员（合并批 C1+C3+C6 三维度；证据核验 + 时序论证核验 + 严重度校准 + cra-index 台账查重逐条回码）
- **复核范围**: 本文件 C1-001~C1-008 + C1-round2.md C1-009，共 9 条；CL-001~005 线索同批核对
- **台账查重结论**: 与 cra-index WI 1.1–3.3 P1/P2 台账零同站点重复（本单元 nop-ai-rest 8 根为 CRA 首审；与 2026-07 ARM、2026-09 code-review/conformance 等前案的重叠命中均为风格/类型安全/正确性域非并发域——JavaMethodReplacer 的 ARM MA4.2-11 仅为 demo main 删除、AI-19/20 为 readString/charset 风格项；PgVectorStore 前案 P1-7.1-002 列读取缺失已修复且与本轮缺陷不同面）

### 逐条判定表

| 发现 | 初审判级 | 判定 | 最终严重度 | 现状标注 | 复核要点 |
|---|---|---|---|---|---|
| C1-001 | P1 | 保留 | P1（零调用方潜伏+接线回升条款） | 新发现·已知未修复 | 并行面内生于方法体，「仅单线程触达」工具型降档不适用；T1/T2 时序与 L165-173/228-241 亲证；`parallelStream` 全 8 根 grep 仅此 1 处，F3 清扫属实；CL-010 吸收 |
| C1-002 | P1 | 保留·吸收进 C6-001 主登记 | P1（随主登记） | 新发现·已知未修复 | 三登记同缺陷裁定（见 C6 文件复核节）；独立增量 = 首访 CTA 竞态子面（瞬态，重试可过） |
| C1-003 | P1 | 保留（触发条件化） | P1 | 新发现·已知未修复 | rag 缺省链不触发（随机 id），显式同 id 并发 upsert 触发；upsert-by-id 为 owner doc 登记契约 + javadoc L23 线程安全声明，契约违约面成立；CL-008 吸收 |
| C1-004 | P2 | 保留 | P2 | 新发现·已知未修复（潜伏：mutator 零调用者） | 公开 mutator + 无同步读者形态亲证；降档标注成立 |
| C1-005 | P3 | 保留 | P3 | 新发现·已知未修复（卫生级） | L6 亲证；测试直调、零写者 |
| C1-006 | P3 | 保留 | P3 | 新发现·已知未修复（卫生级） | L16/21/29 亲证；启动期低频 |
| C1-007 | P3 | 保留 | P3 | 新发现·已知未修复（潜伏：零 main 调用方） | `new MavenProject` 仅 3 处测试引用复核属实；final 字段兜底论证成立 |
| C1-008 | P3 | 保留 | P3 | 新发现·已知未修复 | intern 失效面枚举逐行亲证全部属实；与 WI 3.1 C5-005 同族不同站，判级校准一致（均 P3），无双立 |
| C1-009（round2） | P3 | 保留 | P3 | 新发现·已知未修复（卫生级，零并发消费者） | getStats 活引用逃逸 L93-95 + FileLanguageStats L150 亲证；状态行见 C1-round2.md |

### 复核裁定说明

1. **CL-010 = C1-001 同站点吸收**：C6 round1 的 CL-010 为 C1-001 的 C6 视角重复登记（同文件同行号同竞态），以 C1-001 为唯一立案，CL-010 关闭为吸收。
2. **C1-003 触达面条件标注**：初审未注明「rag 链路随机 id 不触发」这一前置事实（该事实存在于 C3 维度 CL-006/007 的证据中）。复核补充：缺陷触发需显式同 id 并发 upsert，缺省 rag 链不触发；但 store=upsert-by-id 是 `IVectorStore` 契约（owner doc 登记的 META_ID 语义）且同类 PgVectorStore 以 ON CONFLICT 原子实现同一契约，故契约违约定性维持、P1 维持，附使用方式条件标注。
3. **C1-002 吸收裁定**：见 C6-resource-lifecycle.md 复核节「schemaReady 三登记裁定」。
4. **CL-004 与 CL-012 同面重复（线索层，均不在 19 条工单内）**：两条均为 InMemoryVectorStore.buckets 无界账本（C5/C7·F4 族），CL-012 事实面更全（beans.xml 缺省装配推翻 javadoc「不注册 default bean」声明 + biz 无 delete 入口——装配真相亲证成立），建议以 CL-012 为主登记、CL-004 吸收标注；移交 C5/C7 定审时合并。与 WI 3.1 MA-03（F4 引擎终态清理族，agent 侧 29 站点）为同族不同模块，无重复立项。
5. **降档口径核对**：C1-001 未被降档的论证成立（并行面自生）；C1-004/C1-007 的「零调用方/单线程触达」条件化降档论证成立；rag 面（C1-002/C1-003）未违规降档。

## 最终保留项（复核后）

| # | 发现 | 最终严重度 | 工单结构 |
|---|---|---|---|
| 1 | C1-001 parallelStream 共享 ArrayList | P1（潜伏标注） | 独立工单（CL-010 吸收） |
| 2 | C1-002 schemaReady | P1 | **吸收进 C6-001 主登记**（并发子面随同修复） |
| 3 | C1-003 InMemoryVectorStore upsert 竞态 | P1（触发条件化） | 独立工单（CL-008 吸收） |
| 4 | C1-004 ExtensionLanguageMapper | P2 | 独立工单 |
| 5 | C1-005~C1-009 | P3×5 | 卫生/盘点登记 |

C1 维度复核后：9 条全部保留、0 降级、0 驳回、1 吸收；去重后独立工单 8 条（P1×2 / P2×1 / P3×5）。
