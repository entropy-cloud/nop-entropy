# C10 测试保护力 — nop-ai-rest（WI 3.4）

- **审计日期**: 2026-10-20
- **审计员**: C10 测试保护力审计员（最后批次，单轮维度）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文（变异推演 + 时序敏感测试审查 + 资源泄漏测试 + 大规模测试 + 保护力矩阵）
- **输入**: 本目录 C1/C3/C6 三主文件 + 三 round2 文件的 `## 最终保留项`（19 条，去重前口径 P1×4/P2×6/P3×7）

## 审计范围与口径

- **发现清单**: 19 条最终保留发现 = C1-001~009（9）+ C3-001~006（6）+ C6-001~004（4）。schemaReady 三登记（C1-002 = C3-004 = C6-001）按裁定并为一行（标注三 ID），矩阵共 **17 行覆盖 19 条**。
- **审计对象**: 缺陷面所属 8 源码根（nop-ai-api/coder/dsl-orm/rag/code-analyzer/dao/service/app，181 个手写 main 文件）的 `src/test` 测试树，共 50 个测试/支撑文件（rag 13、code-analyzer 9、coder 11、api 7、service 7、dao 1、dsl-orm 2、**app 0**）。
- **变异推演口径（双向）**: 对每条发现回答两问——(a)「补上正确实现后，现有哪个测试会红？」（修复安全性）；(b)「缺陷复归（或一直存在）时，现有哪个测试会红？」（回归保护力）。两问皆无测试红的，判「无测试」；存在断言了缺陷语义、修复必红的，判「假保护（反向钉死）」。
- **检索方法**: 全 8 根 `src/test` grep（缺陷类名 × 方法名 × 并发原语 `Thread.sleep|CountDownLatch|CyclicBarrier|ExecutorService|CompletableFuture` × 断言关键词 `isSuccess|setError|timeout|cancel|orTimeout`）+ 关键测试类逐文件全读。
- **时序敏感测试审查结论（C10 步骤 3）**: 8 根测试树 **零 `Thread.sleep`、零 `CountDownLatch`/`CyclicBarrier`、零自建线程池**——无平台性脆弱测试，也无「未等异步结果就断言」的偶发绿测试（`TestAiModelTextEmbedding.asyncPathsDelegate:53-65` 用 `toCompletableFuture().get()` 正确等待）。同步纪律良好，但这也等价于：**没有任何测试触碰过并发时序**。
- **资源泄漏测试审查结论（C10 步骤 4）**: 无任何泄漏检测钩子（无关闭后断言池空/句柄数/注册表空的测试形态）；`InMemoryVectorStore` 无容量观测 API、`JarResolverCollection` 无 close 语义可断言。
- **大规模测试审查结论（C10 步骤 5）**: 8 根无容量/上限配置面（CacheConfig/QueueOverflowPolicy 均未使用），有界边界测试 N/A；无界账本面（CL-004/CL-012）归 C5/C7 定审，不在此重复。

## 保护力矩阵

判定三值口径：**会红·有保护**（缺陷复归必有测试红）/ **假保护**（有测试但断言的正是缺陷语义，修复必红）/ **无测试**（缺陷面零测试触达，或仅有不构成保护的 smoke）。

| # | 发现（ID · 严重度 · 站点） | 现有测试覆盖判定 | 证据（测试文件:line 或检索说明） |
|---|---|---|---|
| 1 | C1-001 · P1 · `JavaSourceFileFinder.getAllJavaSourceFiles` parallelStream 共享 ArrayList（L165-173/236） | **无测试** | 全 8 根 `src/test` grep `getAllJavaSourceFiles\|JavaSourceFileFinder` **零命中**；唯一邻接测试 `MavenProjectTest:11-16` 仅走 `findJavaFileByClassName`（不同入口）；修复（改 collect）与缺陷复归双向均无测试红 |
| 2 | **C1-002 = C3-004 = C6-001** · P1×3 · `PgVectorStore.schemaReady` 作用域错配（L52/83/102，调用点 L116/153/189） | **无测试** | `TestPgVectorStoreSql` 全部 9 测试**每测试 `newStore()`**（L101-103，实例级 flag 恒 false）且**只用单集合 "docs"**（L94-99）——无任何「同一实例先后两个 collection」序列；`ddlIsIdempotentWithRealTableName`（L105-120）只断言 DDL 文本含真实表名 + `IF NOT EXISTS`，缺陷/修复双向均绿；`TestPgVectorIntegration:22` env 门控（`NOP_TEST_PGVECTOR_JDBC_URL`，CI 不跑）且单集合 `nop_test_k2`（L42-44），开启也不会命中第二集合路径 |
| 3 | C1-003 · P1 · `InMemoryVectorStore` removeIf+add 复合 upsert 竞态（L59-76，关键 L69-70） | **无测试** | `TestInMemoryVectorStore` 10 测试**全单线程**，零并发同 id upsert；且 `updateReflectedInSearch`（L83-93）只断言 `hits.get(0)` 的 id（新旧版本 id 同为 "a"，**把 L69 `removeIf` 整行删除该测试仍绿**）、`singleItemDelegates`（L147-157）经 delete 全清后断言空——单线程 upsert 幂等语义本身也未真正钉住 |
| 4 | C1-004 · P2 · `ExtensionLanguageMapper` static HashMap 公开 mutator 无同步读者（L12/138-169） | **无测试** | grep `ExtensionLanguageMapper` 于全部 nop-ai 测试 **零命中**（仅 main 内 `FileLanguageStats` 引用）；`addCustomMapping(s)` 与三个读入口均无测试；`TestFileLanguageStats.testLocalMappingPriority`（L137-153）钉的是实例级姊妹 `addLocalMapping`，非本站点 |
| 5 | C1-005 · P3 · `XDefSimplifier.INSTANCE` public static 非 final（L6） | **无测试**（字段面） | `TestXDefSimplifier` 4 处只读引用（L21/40/54/62）作功能性 smoke——加 `final` 不会红（修复安全），复归/存在也无可观测差异；无任何测试断言字段不可变性 |
| 6 | C1-006 · P3 · `NopAiApplication.globalArgs` static 非 final（L16/21/29） | **无测试** | `nop-ai-app/src` 仅 `main` 目录（**无测试源码根**）；全 nop-ai 测试 grep `NopAiApplication` 零命中 |
| 7 | C1-007 · P3 · `MavenProject` 双字段非同步惰性初始化（L13-15/32-37/47-57） | **无测试**（并发面） | `MavenProjectTest:11-16` 单线程 smoke 经 `getJavaFileFinder/loadModuleStructure`——加 volatile/构造期加载不红（修复安全），并发双载/`rootModule=null` 窗口不可观测；`@Disabled` 的 `TestJavaCodeFileInfoParser` 不触达并发语义 |
| 8 | C1-008 · P3 · `CodeSymbolInterning` 高基数串（md5/summary）intern 全局驻留（L17-37 + CodeFileInfo L202-207） | **假保护（反向钉死）** | `TestCodeFileInfoStructure.testInterningDeduplicatesTopLevelFields`（L49-78）：javadoc L23 明言「interning 去重语义**与拆分前一致**」，L72 `assertSame("abc123".intern(), fileInfo.getMd5())`、L74 同断 summary、L69 同断 filePath——**按 C1-008 建议把 md5/summary 移出 intern 面必使本测试红**：测试钉死的正是缺陷语义 |
| 9 | C1-009 · P3 · `LanguageStatsAggregator.getStats` 活引用逃逸 + `FileLanguageStats:150` 升格为 API（L85/93-95） | **无测试** | `TestFileLanguageStats`（L63、L160）对 `getComprehensiveStats` 只断言**值**（计数/字节），从不改写返回 map、从不跨线程读——改快照拷贝后值相等仍绿（修复安全），逃逸面不可观测 |
| 10 | C3-001 · P2 · `AiModelTextEmbedding` embedAsync/embedAllAsync 无校验 + 失败模型混用（L71-90） | **无测试** | `TestAiModelTextEmbedding.asyncPathsDelegate`（L53-65）异步变体**仅合法输入 happy-path**；`nullTextRejected`（L105-110）只钉**同步** `embed(null)/embedAll(null)`；无 `embedAsync(null)/embedAllAsync(null)/embedAllAsync(List.of("a",null))` 断言——异步契约缺失双向无红 |
| 11 | C3-002 · P3 · 异步面死代码无护栏（L71-90 + beans.xml L16-19） | **无测试** | 异步覆盖零测试触达（同上）；`TestTextEmbeddingWiring`（L59-73）只钉 bean 按类型注入存在性、`TestAiTextEmbeddingVectorSearchE2E` 只走同步 embed 面——**删除两个异步覆盖方法现有测试全绿**（漂移无报警） |
| 12 | C3-003 · P2 · synthesize/search/ingest sync-over-async 无超时无取消（5 站点族） | **无测试** | rag 测试树 grep `timeout\|cancel\|orTimeout\|Thread.sleep\|CountDownLatch\|ExecutorService` **零命中**（仅 `StubHttpClient` 方法签名形参）；`StubChat`（TestK3PipelineE2E L123-145）/`StubEmbedding` 恒即时完成——慢速/挂起 provider 形态在测试基建中不存在，超时/取消语义双向无红 |
| 13 | C3-005 · P1 · synthesize 静默折叠 error ChatResponse 为 ""（RagSynthesizeService L44-48） | **无测试** | `synthesizeProducesNonEmptyAnswer`（L66-71）用**恒成功** StubChat（L123-138 恒返回带 messages 的响应）；rag+api 测试 grep `isSuccess\|ChatResponse.error\|setError` **零命中**——无任何测试让 synthesize 收到错误响应；修复（fail-loud）不红、缺陷复归不红 |
| 14 | C3-006 · P3 · classifier 空 examples 除零 NaN 标签静默消失（L69-84） | **无测试** | grep `EmbeddingModelBasedClassifier` 于全部 nop-ai 测试 **零命中**（Reserved 类零测试触达，NaN 路径与空 examples 配置面均无测试） |
| 15 | C6-002 · P2 · `JavaMethodReplacer` 非原子原地覆写（L19-51，写点 L50） | **无测试** | 唯一消费者测试 `TestCoderErrorCodeConversion` 仅覆盖**写前**失败路径（L51-75：签名未命中/花括号不平衡，均于 L50 写盘前抛出）；成功替换路径、写盘中断/失败路径、内容保全断言**全部为零**——改 temp+ATOMIC_MOVE 不红、缺陷在位也不红 |
| 16 | C6-003 · P2 · `JarResolverCollection`/`JavaParserBuilder` 注册后无释放生命周期（L20/34-46 + L17-19/60-65） | **无测试** | grep `JarTypeSolver\|JavaParserBuilder\|JarResolver` 于测试树：仅 `TestJavaCodeFileInfoParser:23/42`，且该两测试**全部 `@Disabled`**（L15/L30）——活跃测试对注册/释放面零触达；类无 close 语义也就无释放断言可存在 |
| 17 | C6-004 · P2 · `JavaCodeFileInfoGenerator` exists()-skip × 截断写（L48-66/82） | **无测试** | `TestJavaCodeFileInfoGenerator.testGenerateWithValidSummarySucceeds`（L82-107）单次 generate 后**只断言 `outFile.exists()`**（L103）——不验证 JSON 可解析、不重跑验证 skip 语义、无崩溃注入；两个失败注入测试（L38-80）在写盘前抛出。半写产物永久保留面双向无红 |

**矩阵统计：会红·有保护 0 ｜ 假保护 1 ｜ 无测试 16**（19 条发现，schemaReady 三登记并 1 行）。
P1×4 全部「无测试」——本单元对并发/资源/失败语义的回归保护力为零。

## 反向钉死检查

**命中 1 项。**

### 命中：C1-008 intern 高基数串 ← `TestCodeFileInfoStructure`（反向钉死，实锤）

- `nop-ai/nop-ai-code-analyzer/src/test/java/io/nop/ai/code_analyzer/code/TestCodeFileInfoStructure.java:49-78`
- 类 javadoc L23 明言测试目的即「interning 去重语义**与拆分前一致**（引用相等性）」；L72 `assertSame("abc123".intern(), fileInfo.getMd5())`、L74 `assertSame("demo summary".intern(), fileInfo.getSummary())`、L69 同断 `filePath`。C1-008 的缺陷正是 md5/summary 这类高基数字段进入全局 StringTable；**按建议修复（收窄 intern 面）会使这三条 assertSame 必然失败**——测试断言的正是缺陷语义，属「测试即规格」纪律缺位：结构固化测试在固化重构契约时未做并发/资源口径审查，把缺陷语义一并钉死。修复 C1-008 必须同步改写本测试（缺陷工单应携带测试改造项）。
- 对照面：`testInterningRecursesSymbolTypes`（L81-121）钉的是 CodeSymbol/CodeClassInfo 等**低基数符号名** intern——与 C1-008 认定的正确用法一致，修复后应保留，不需要动。

### 任务指定三项定向排查（均未命中反向钉死，但暴露断言弱化）

1. **InMemoryVectorStore upsert 测试**：无测试断言「同 id 允许重复」或「DDL/去重不必发生」——无反向钉死。但 `updateReflectedInSearch`（L83-93）断言弱到 `hits.get(0)` 的 id（新旧同 id 恒真）：**删除 L69 `removeIf` 整行（破坏单线程 upsert 幂等）该测试仍绿**；`singleItemDelegates`（L147-157）经 delete 全清后亦不可分辨。属「保护力弱化」而非钉死。
2. **embedAsync 契约测试**：`TestAiModelTextEmbedding` 对异步变体仅 happy-path（L53-65），无任何测试断言异步变体「容忍 null/缺校验」——无反向钉死；同步侧 `nullTextRejected`（L105-110）钉的是正确语义（fail-loud），修复 C3-001（异步版补校验）不会使其红。
3. **PgVectorStore 测试（docker-gated 注意项）**：`TestPgVectorStoreSql` 用 Proxy 假 Connection（L29-92），无反向钉死——没有任何测试断言「DDL 每实例只执行一次」或 schemaReady 语义；但该文件**每测试新建 store 实例 + 单集合**的基线形态使缺陷触发序列（同实例第二集合）在测试空间中不存在；`TestPgVectorIntegration` 为 env 门控（L22 `@EnabledIfEnvironmentVariable NOP_TEST_PGVECTOR_JDBC_URL`，CI 缺省跳过）且单集合（L42-44），即使本地开启也不覆盖缺陷面。**门控不构成假保护，但使唯一真实 DDL 语义层常态零回归**。

**反向钉死命中数：1**（另记「断言弱化不足以红」3 处：矩阵第 3/15/17 行证据栏）。

## 缺保护登记

（按同案保护面合并：8 条覆盖全部 P1×4/P2×6；C1-005/C1-006 为 P3 字段声明级卫生项，测试性保护成本大于收益，不单独立项，随缺陷本体一行修复即可，见矩阵第 5/6 行。判级口径：对应缺陷 P1 → 缺保护 P2；对应缺陷 P2/P3 → 缺保护 P3。）

### [C10-001] PgVectorStore schemaReady 多集合/并发首建零回归保护

- **文件**: 缺口在 `nop-ai/nop-ai-rag/src/test/java/io/nop/ai/rag/vector/TestPgVectorStoreSql.java:101-120`（每测试 `newStore()` + 单集合基线）；缺陷站点 `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/PgVectorStore.java:52,82-103`
- **证据片段**:
  ```java
  private PgVectorStore newStore() {
      return new PgVectorStore(fakeDataSource(), 2, "docs");   // 每测试新实例 → schemaReady 恒 false
  }
  @Test void ddlIsIdempotentWithRealTableName() { ... assertTrue(ddl.contains("docs"), ...) }
  // 无任何测试对同一实例先后使用两个 collectionName
  ```
- **严重程度**: P2（对应缺陷 C6-001+C1-002+C3-004 为 P1）
- **并发场景**: 待保护场景 = 同一 store 单例跨请求多 collection（确定性时序 a）+ 并发首访同 collection（瞬态时序 b）——正是生产 Delta 换 PgVectorStore 后的设计内用法。
- **触发时序**: 回归/存量缺陷下：T1 测试（若有）store("a") → T2 search("b") → L83-85 提前 return → "relation b does not exist"；现有全部测试在 T1 前即重建实例并只用 "docs"，该序列不可表达，缺陷恒绿。
- **现状**: SQL 文本形状（upsert/tenant 谓词/LIMIT）有良好单测钉住（P1-7.1-002 修复面），但初始化状态机的**作用域语义**零覆盖。
- **风险**: 修复（per-collection ready set）本身无回归网；后续任何重构（如误把 ready set 键改回实例级）静默复活 P1。
- **建议**: 复用现有 fake DataSource/Proxy Connection 基建新增两测试：(1) 同一实例 `store(opts("a"))` 后 `search(opts("b"))`，断言 capturedSql 含**两条** `CREATE TABLE IF NOT EXISTS`（分别含 a、b）——变异即红；(2) 同 collection 二次 store 断言 DDL 只一条（防过修为每次都建表）。
- **失效面枚举**: PgVectorStore store/delete/search/update/IVectorStore 单条重载 5 入口同受 ensureSchema 保护（C3-004 枚举）；测试侧保护面 = TestPgVectorStoreSql 全部 9 测试 + TestPgVectorIntegration 1 测试，均无跨集合序列——缺口为该测试文件整体，非单方法。
- **信心水平**: 确定
- **误报排除**: 非「已有 ddlIsIdempotent 测试即有保护」——该测试在新实例上运行，对 flag 作用域完全不敏感（变异推演双向验证）。
- **复核状态**: 未复核

### [C10-002] InMemoryVectorStore 并发同 id upsert 零测试 + 单线程 upsert 幂等断言弱化

- **文件**: 缺口在 `nop-ai/nop-ai-rag/src/test/java/io/nop/ai/rag/vector/TestInMemoryVectorStore.java:83-93, 147-157`；缺陷站点 `InMemoryVectorStore.java:69-70`
- **证据片段**:
  ```java
  @Test void updateReflectedInSearch() {
      store.store(vec("a", null, 1, 0), opts("c1", null));
      store.update(vec("a", null, 0, 1), opts("c1", null));
      ...
      assertEquals("a", hits.get(0).getMetadata("id"));   // 新旧版本 id 同为 "a"——删除 removeIf 仍绿
  }
  ```
- **严重程度**: P2（对应缺陷 C1-003 为 P1）
- **并发场景**: 待保护场景 = 两请求线程对同一 bucket 并发同 id upsert（rag 重试/多客户端幂等写）。
- **触发时序**: 缺陷时序（C1-003 T1/T2 removeIf 判空交叠）在单线程测试中不可表达；且「removeIf 整行删除」的单线程变异亦无测试红——修复所依赖的 upsert-by-id 前提语义本身未被钉住。
- **现状**: 10 测试覆盖排序/topK/tenant 隔离/fail-loud，质量良好，唯 upsert 幂等断言强度不足 + 并发面为零。
- **风险**: C1-003 修复（改 ConcurrentHashMap 或 per-bucket 锁）后，结构重写无回归网；`bucket()` 语义变更可能静默破坏 tenant 隔离与幂等（虽有 tenantIsolation 测试兜底部分面）。
- **建议**: (1) 先钉单线程语义：`updateReflectedInSearch` 补 `assertEquals(1, hits.size())`（removeIf 删除变异即红）；(2) 补并发测试：两线程 `CountDownLatch` 对齐后各自 `store(同 id 不同向量)`，断言 search 同 id 至多 1 条（容忍两版本之一，重复即红）。
- **失效面枚举**: store/update 两入口（C1-003 F3 枚举：delete/search/bucket 安全）；测试侧该文件 10 测试中 2 个涉及 upsert、0 个并发。
- **信心水平**: 确定
- **误报排除**: 非「单线程测试即并发保护」——变异推演显示连单线程幂等语义都未钉住，登记的是双层缺口。
- **复核状态**: 未复核

### [C10-003] synthesize 错误形态与超时/取消契约零故障注入（rag 请求链 stub 基建缺失败/慢速模式）

- **文件**: 缺口在 `nop-ai/nop-ai-rag/src/test/java/io/nop/ai/rag/TestK3PipelineE2E.java:66-71, 123-145`（StubChat 恒成功）；缺陷站点 `RagSynthesizeService.java:44-48`（C3-005）+ 三服务 syncGet 站点（C3-003）
- **证据片段**:
  ```java
  static class StubChat implements IChatService {
      public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken token) {
          ChatResponse resp = new ChatResponse();
          resp.setMessages(List.of(new ChatAssistantMessage("Synthesized answer ...")));
          return CompletableFuture.completedFuture(resp);   // 恒成功、恒即时——error/挂起形态不存在
      }
  }
  ```
- **严重程度**: P2（对应缺陷 C3-005 为 P1、C3-003 为 P2）
- **并发场景**: 待保护场景 = provider 429/401/5xx 折叠为 error ChatResponse（C3-005）；provider 网络停滞 + cancelToken=null 无界等待（C3-003）。
- **触发时序**: 缺陷时序在现有 stub 下不可表达：StubChat 永不产出 error 响应 → L47-48 折叠分支零执行；永不挂起 → syncGet 等待语义零执行。
- **现状**: rag 请求链仅有 happy-path E2E；`TestChatResponse`（api 根）钉了 outputText/isSuccess 的**值语义**，但 rag 消费侧（是否检查 isSuccess）零覆盖。
- **风险**: C3-005 修复（fail-loud）与 C3-003 修复（透传 cancelToken + 超时）均无回归网；后续重构可静默把异常路径改回 ""。
- **建议**: StubChat 增加两种模式：(1) `errorResponse` 模式返回 `ChatResponse.error(...)`——修复后断言 `synthesize` 抛 NopException 而非返回 ""；(2) `neverComplete` 模式返回不完成的 future——修复后配合超时参数断言在期限内以异常收尾（测试自身用 `assertTimeoutPreemptively` 或注入超时值，避免真实等待）。空 answer 语义（合法空回答 vs 错误折叠）应一并钉住。
- **失效面枚举**: synthesize:46（C3-005 本体）；search:34/ingest:68/classifier:66/EmbeddingExampleConfig:58（C3-003 五站点族）共享同一 stub 基建缺口——一条登记覆盖五站点的失败注入面。
- **信心水平**: 确定
- **误报排除**: 非「E2E happy-path 即契约保护」——error 响应是 nop-ai-api 设计内返回形态（复核已升格确认），失败形态零注入即零保护。
- **复核状态**: 未复核

### [C10-004] code-analyzer 并发收集/全局映射/惰性初始化/逃逸面零并发测试

- **文件**: 缺口在 `nop-ai/nop-ai-code-analyzer/src/test/`（9 测试文件全单线程）；缺陷站点 `JavaSourceFileFinder.java:165-173`（C1-001，P1）、`ExtensionLanguageMapper.java:138-169`（C1-004）、`MavenProject.java:32-57`（C1-007）、`LanguageStatsAggregator.java:93-95`（C1-009）
- **证据片段**:
  ```java
  // 全模块测试树 grep：Thread.sleep|CountDownLatch|CyclicBarrier|ExecutorService → 0 命中
  // grep：getAllJavaSourceFiles|JavaSourceFileFinder|ExtensionLanguageMapper → 0 命中
  public class MavenProjectTest {
      public void testFindJavaFile() {           // 唯一触达 MavenProject 的测试，单线程 smoke
          MavenProject project = new MavenProject(new File(getModuleDir(), ".."));
  ```
- **严重程度**: P2（对应缺陷 C1-001 为 P1；C1-004/C1-007/C1-009 为 P2/P3 同模块同保护面）
- **并发场景**: 待保护场景 = ForkJoinPool 多 worker 向共享 ArrayList add（C1-001）；运行期 mutator 与读遍历并发（C1-004）；多线程共享 MavenProject（C1-007）；跨线程消费 getter 返回值（C1-009）。
- **触发时序**: C1-001 缺陷为「任一首次调用 + 模块数 ≥ FJ 并发度即近乎必然触发」——连**非并发**的单线程结果正确性断言都不存在（丢文件变异在单线程下部分可观测的成分也未被钉）；其余三缺陷的并发时序在零并发基建下不可表达。
- **现状**: 模块测试全部为值级 smoke（解析/统计/切分），工具面的入口契约（收集完整性、映射稳定性）零覆盖。
- **风险**: C1-001 修复（改 collect）无回归网；该模块为工具库，集成方接线后缺陷直接暴露于生产且无测试报警。
- **建议**: (1) 新增 `getAllJavaSourceFiles` 收集完整性测试：临时目录造 N 模块 × M 文件，断言 `results.size() == N*M` 且无重复（丢文件/重复变异即红，单线程即可红——parallelStream 在该方法体内，测试无需自建线程池）；(2) C1-004 修复后以契约测试 pin `addCustomMapping` 可见性语义；(3) C1-009 修复后 pin 返回 map 不可变性（`assertThrows(UnsupportedOperationException.class, () -> map.put(...))`，视修复选型）。
- **失效面枚举**: 本模块 4 条保留发现同保护面（测试树整体）；姊妹站点 `findJavaSourceFilesBySimpleName` L132-148 等顺序安全入口可一并纳入收集完整性测试的参数化用例。
- **信心水平**: 确定
- **误报排除**: 非「工具模块可豁免并发测试」——C1-001 的并行面内生于方法体，单线程调用即走多线程路径（ForkJoin commonPool），收集完整性测试即可触发；不适用「测试专用简化模型」豁免。
- **复核状态**: 未复核

### [C10-005] AiModelTextEmbedding 异步变体负例契约零测试

- **文件**: 缺口在 `nop-ai/nop-ai-rag/src/test/java/io/nop/ai/core/search/TestAiModelTextEmbedding.java:53-65`（异步仅 happy-path）；缺陷站点 `AiModelTextEmbedding.java:71-90`
- **证据片段**:
  ```java
  @Test void asyncPathsDelegate() throws Exception {
      float[] vector = embedding.embedAsync("deep").toCompletableFuture().get();      // 合法输入
      List<float[]> vectors = embedding.embedAllAsync(List.of("machine"))...get();    // 合法输入
  }
  // 无 embedAsync(null) / embedAllAsync(null) / embedAllAsync(List.of("a", null)) 断言
  ```
- **严重程度**: P3（对应缺陷 C3-001/C3-002 为 P2/P3）
- **并发场景**: 不适用（契约面）——待保护的是同步/异步变体失败语义一致性（fail-loud NopException vs 同步段裸 NPE/stage 异常混用）。
- **触发时序**: 缺陷时序（`embedAllAsync(null)` 在同步段抛裸 NPE 绕过 `.exceptionally`）无测试触达；`asyncPathsDelegate` 的等待方式正确（future.get()），但输入全为合法值。
- **现状**: 同步版校验契约已被 `nullTextRejected`（L105-110）正确钉住——对称的异步负例完全缺失，与 C3-002「死代码面无护栏」互为表里。
- **风险**: C3-001 修复后契约无回归网；F3 式漂移（只改同步版）无报警。
- **建议**: 补三条负例断言与同步版对齐（视 C3-001 选型）：`assertThrows(NopException.class, () -> embedding.embedAsync(null).toCompletableFuture().get())` 或断言 stage 异常完成 + 异常类型为 NopException；`embedAllAsync(null)` / `embedAllAsync(List.of("a", null))` 同理。删除异步覆盖（选型 a）的场合，本条随 C3-002 关闭为 watch-only。
- **失效面枚举**: embedAsync:71 / embedAllAsync:77（缺陷面）；同步 embed:39 / embedAll:49（已钉，正确）；`getDimension:96`（零调用方，随 C3-002 观察）。
- **信心水平**: 确定
- **误报排除**: 非「已有同步负例即覆盖」——同步/异步变体是不同入口，失败模型分叉正是缺陷本体。
- **复核状态**: 未复核

### [C10-006] 非原子文件写面（覆写/写-跳过组合）零故障注入测试

- **文件**: 缺口在 `nop-ai/nop-ai-coder/src/test/java/io/nop/ai/coder/code/TestCoderErrorCodeConversion.java:51-75` + `nop-ai/nop-ai-code-analyzer/src/test/java/io/nop/ai/code_analyzer/code/TestJavaCodeFileInfoGenerator.java:82-107`；缺陷站点 `JavaMethodReplacer.java:50`（C6-002）、`JavaCodeFileInfoGenerator.java:56-58,82`（C6-004）
- **证据片段**:
  ```java
  // TestCoderErrorCodeConversion：仅覆盖写前失败（signature/braces 在 L50 写盘前抛出）
  // TestJavaCodeFileInfoGenerator：成功路径仅断言存在性
  assertTrue(outFile.exists(), "info json should be generated for valid summary input");  // 不验 JSON 可解析
  ```
- **严重程度**: P3（对应缺陷 C6-002/C6-004 为 P2·条件化）
- **并发场景**: 待保护场景 = 写盘中途磁盘满/kill -9（单线程失败路径）与双任务并发写同一文件（时序 b）；两缺陷同族（F2/F6 非原子写）同批修，保护面合并。
- **触发时序**: 崩溃/中断时序无法在常规 JUnit 中直接注入，但**失败注入可以**：包装抛异常的输出流/断言写后原文件字节不变，即可覆盖「失败不损毁源文件」契约（现零覆盖）。
- **现状**: C6-002 仅有错误码契约测试（写前路径），成功替换路径本身零测试；C6-004 仅有存在性断言（半写 JSON 也满足）。
- **风险**: 两案修复（temp+ATOMIC_MOVE / skip 改「存在且可解析」）后无回归网；`FileHelper.writeText` 截断写实现变更（平台侧）亦无本模块测试可感知。
- **建议**: (1) JavaMethodReplacer：补成功替换测试（断言替换后内容精确）+ 失败注入测试（使写盘抛 IOException，断言原文件内容完整保留）；(2) JavaCodeFileInfoGenerator：补「预置半写 .info.json → generate → 断言被重新生成且可解析」（修复后语义）与「正常生成两次内容一致」。
- **失效面枚举**: 8 根内直接写点全清单 = JavaMethodReplacer:50 + JavaCodeFileInfoGenerator:82 两个缺陷站点 + AiCoderHelper:126/147/151（平台封装 watch-only）——本条覆盖两个缺陷站点，watch-only 站点不单独立项。
- **信心水平**: 确定
- **误报排除**: 非「零 main 消费者即无需测试」——两案均带「接线即回升 P1」条款，测试应在修复批次一并落地而非等待接线。
- **复核状态**: 未复核

### [C10-007] 资源释放生命周期无泄漏检测钩子（@Disabled 测试为唯一引用面）

- **文件**: 缺口在 `nop-ai/nop-ai-code-analyzer/src/test/java/io/nop/ai/code_analyzer/code/TestJavaCodeFileInfoParser.java:15,30`（唯一引用 `JavaParserBuilder` 的测试全部 `@Disabled`）；缺陷站点 `JarResolverCollection.java:20,34-46` + `JavaParserBuilder.java:17-19,60-65`
- **证据片段**:
  ```java
  @Disabled @Test public void testParse() {
      ...
      JavaParser javaParser = new JavaParserBuilder().addReflection().addModuleJars(structure.getRootModule()).build();
  // 全模块无任何 close/句柄数/注册表空断言——类本身也无 close 语义可断言
  ```
- **严重程度**: P3（对应缺陷 C6-003 为 P2·条件化）
- **并发场景**: 待保护场景 = 长驻分析服务多任务累积 JarTypeSolver 句柄（F8 清理不对称）。
- **触发时序**: 泄漏为生命周期缺失型（非竞态），触发需「注册→任务终态→断言释放」的测试形态——当前类无 close，测试树亦无此形态，缺陷不可观测也不可防。
- **现状**: 平台惯例的泄漏测试形态（关闭后断言池空/句柄数）在 8 根测试树中零存在（C10 步骤 4 全局结论）；本站点是唯一资源句柄持有者，故单列。
- **风险**: C6-003 修复（AutoCloseable）后无回归网；若无 close-then-assert 测试，修复可能只加接口不闭合语义。
- **建议**: 随 C6-003 修复补测试：`JarResolverCollection.close()` 后断言内部 `jarSolvers` 空（或对 mock Closeable solver 断言 `close()` 被调用）；`JavaParserBuilder` 双重记账面断言与 collection 共享同一 solver 集合。同时评估把 `TestJavaCodeFileInfoParser` 两个 @Disabled 用例改造为可在 CI 跑的小样本形态（现依赖真实 mvn/文件系统）。
- **失效面枚举**: `JarResolverCollection.jarSolvers`（本体）+ `JavaParserBuilder.solver/jarSolvers`（同根同修，C6-003 枚举）；其余 8 根无资源持有型注册表。
- **信心水平**: 确定
- **误报排除**: 非「Cleaner 兜底即有保护」——兜底依赖不可达，与长驻使用方式矛盾（C6-003 已裁定），测试保护缺位独立成立。
- **复核状态**: 未复核

### [C10-008] CodeSymbolInterning 反向钉死解除与 intern 契约重钉（测试侧工作项）

- **文件**: `nop-ai/nop-ai-code-analyzer/src/test/java/io/nop/ai/code_analyzer/code/TestCodeFileInfoStructure.java:19-24, 49-78`（钉死点 L69/72/74）
- **证据片段**:
  ```java
  * 3) interning 去重语义与拆分前一致（引用相等性）        // javadoc L23：把现状固化为契约
  assertSame("abc123".intern(), fileInfo.getMd5());        // L72：钉死高基数 md5 必须 intern
  assertSame("demo summary".intern(), fileInfo.getSummary()); // L74：钉死高基数 summary 必须 intern
  ```
- **严重程度**: P3（对应缺陷 C1-008 为 P3；登记的是测试改造工作项）
- **并发场景**: 不适用——登记对象是「测试即规格」缺陷：结构固化测试以「与拆分前一致」为契约，未经并发/资源口径审查即把 C1-008 缺陷语义（高基数串驻留全局 StringTable）写入断言。
- **触发时序**: 修复时序：T1 按 C1-008 建议将 md5/summary 移出 intern 面 → T2 `TestCodeFileInfoStructure` 三处 assertSame 立即红 → 修复被测试**反向阻止**；若修测试不改实现则缺陷复归无红。
- **现状**: 本单元唯一反向钉死命中；姊妹测试 `testInterningRecursesSymbolTypes`（L81-121）钉的低基数符号 intern 为正确语义，须保留。
- **风险**: 不解除则 C1-008 不可修（或修复者顺手改弱断言，丢掉低基数面的真实保护）；解除时若整类断言改 equals，则同时失去低基数字段的 intern 契约保护。
- **建议**: 修复 C1-008 同一工单内分级改写：md5/summary/filePath 改 `assertEquals`（值契约）+ 新增「高基数字段不在全局池」的语义断言（如 `assertNotSame(fileInfo.getMd5(), fileInfo.getMd5().intern())` 视实现选型）；CodeSymbol/CodeClassInfo/CodeFunctionInfo 等低基数字段保留 assertSame 并更新 javadoc 为按字段基数分级的契约声明。
- **失效面枚举**: 钉死断言全清单 = L69（filePath，修复时酌情）/L72（md5）/L74（summary）三处；L75-77（imports/metadata）为低基数正确面保留；L81-121 符号类型面保留。
- **信心水平**: 确定
- **误报排除**: 非「测试红即测试错」的一般化主张——仅此一处经变异推演证实测试断言的正是已立案缺陷语义；其余 16 行双向推演均为「双向不红」型缺口，非钉死。
- **复核状态**: 未复核

## 系统性观察

1. **全单元并发/资源回归保护力为零（会红 = 0/17）**：8 根 181 个 main 文件对约 50 个测试文件，并发原语（sleep/latch/pool）零命中、失败注入（error response/慢速 stub/写盘故障）零命中。本单元全部 P1×4 与 P2×6 的正确性语义完全无回归网——与 WI 3.1（会红 0）、3.2（会红 0）、3.3（会红 0）一致，**nop-ai 系列四个单元的 C10 会红全部为 0**，是该产品线测试基建的系统性形态而非单元偶发。
2. **测试基建立即排除缺陷触发条件（fresh-instance × 单集合 × 恒成功 stub 三联）**：P1×4 全部无测试且非随机遗漏——`TestPgVectorStoreSql` 每测试新建 store（schemaReady 恒 false）且单集合、`StubChat`/`StubEmbedding` 恒成功恒即时、`TestInMemoryVectorStore` 全单线程。三个 P1 的触发面（第二集合 / 错误响应 / 并发同 id）在测试空间中**结构性不存在**：修复这些缺陷需要先建「失败/多集合/并发」三类 stub 形态，属测试基建工程而非补断言。
3. **结构契约测试无差别固化现状（反向钉死的成因族）**：`TestCodeFileInfoStructure` 以「与拆分前一致（引用相等性）」为契约把 intern 缺陷语义钉死（本单元唯一反向钉死命中）。同族风险面还包括注解级/结构级固化测试（如 `TestNopAiRagBizModelAnnotations`）——「重构保护」类测试在固化良构契约的同时会一并固化当时未识别的缺陷语义，修复需先改测试。纪律缺口：缺陷语义进入测试断言前未经并发/资源口径审查。
4. **门控与 @Disabled 构成保护力黑洞**：唯一真实 DDL 语义面（`TestPgVectorIntegration`，env 门控 `NOP_TEST_PGVECTOR_JDBC_URL`）与唯一真实 jar 解析/生成链面（`TestJavaCodeFileInfoParser` 双 `@Disabled`）均不在 CI 路径——即使补齐单测层，pgvector 行为与文件系统崩溃语义仍无常态回归面；coder 根另有 4 个 `@Disabled` 手动测试类，其覆盖的 wiring 面同样悬空。
5. **断言强度不足以钉住单线程语义（并发保护的前置缺口）**：`updateReflectedInSearch`（仅断 `get(0)` 的 id，removeIf 整行删除仍绿）、`testGenerateWithValidSummarySucceeds`（仅断 `exists()`，半写 JSON 也满足）——在补并发测试之前，连缺陷修复所依赖的单线程前提语义（upsert 幂等、产物可解析）都未被钉住。补测试的正确顺序：先钉单线程语义（单线程变异即红），再叠并发/失败形态。

## 终止判定

**C10 为单轮维度（`concurrency-resource-audit-prompt.md` 附录 B 第 3 条：C10 放最后批次，依赖前序发现清单，不设深挖轮次）——显式声明：本维度单轮终止，不设 round 2。**

依据：对全部 19 条最终保留发现完成双向变异推演（修复安全 × 回归保护），检索口径穷举（缺陷类名 × 方法名 × 并发原语 × 断言关键词，覆盖 8 根全部测试文件，关键测试类逐文件全读）；保护力矩阵 17 行全部落定（会红 0/假保护 1/无测试 16），缺保护登记按同案保护面合并后收敛为 8 条（P2×4/P3×4），无未决保护面、无第 2 轮事由。

## C10 复核结论（独立复核员，2026-10-20）

复核方法：C10 正文与口径全文比对；三维度 `## 最终保留项`（C1×9 + C3×6 + C6×4 = 19 条）逐条对账矩阵 17 行；抽查 9 行回测试源码验证（假保护 1 行 + 无测试 8 行，含全部 docker/env 门控面与反向钉死行）；抽查 5 条缺保护登记（C10-001/002/004/005/008）验格式与检索纪律；8 根测试树全量 grep 复现（并发原语、断言关键词、@Disabled/@EnabledIf 面共 50 个测试文件清点）。

### 对账结果

- **19 条 ↔ 17 行闭合**：C1-001~009（9）+ C3-001~006（6）+ C6-001~004（4）逐条入矩阵，无遗漏、无多记；并行行（矩阵第 2 行）三 ID 标注 C1-002 = C3-004 = C6-001 与三维度最终保留项的吸收裁定一致。
- **行内严重度与最终保留项全一致**：含 C3-005 的 P2→P1 升格（矩阵第 13 行标 P1）、C6-002/003/004 条件化标注；矩阵统计行（会红 0 / 假保护 1 / 无测试 16 = 17 行）自洽。
- **对账差异 3 处（均为标注性勘误，不动判定）**：
  1. **文件头部第 6 行严重度口径标签错位**：「19 条，去重前口径 P1×4/P2×6/P3×7」自相矛盾——去重前 19 条实为 P1×6/P2×6/P3×7（C1-002/C3-004/C6-001 各计一次 P1）；P1×4/P2×6/P3×7（=17）是去重后口径，且正文（矩阵统计、缺保护登记前言）一贯按去重后口径使用。应读作「去重前 19 条（P1×6/P2×6/P3×7），去重后 17 条（P1×4/P2×6/P3×7）」。正文各处判定不受影响。
  2. **矩阵第 5 行引用计数**：「`TestXDefSimplifier` 4 处只读引用（L21/40/54/62）」实为 **5 处**（L21/40/54/62/68）。判定「无测试（字段面）」不受影响（均为功能性 smoke，加 `final` 不红）。
  3. **矩阵第 11 行措辞**：「异步覆盖零测试触达（同上）」字面与第 10 行证据（`asyncPathsDelegate:53-65` 触达异步 happy-path）矛盾；经核实其本意为 C3-002 选型 (a) 语义——**删除两个异步覆盖（override）方法后测试仍全绿**，属实：`AiModelTextEmbedding implements ITextEmbedding`，SPI 侧 `ITextEmbedding.java:47/55/64` 提供包装同步版的 default 异步实现，删除 override 后 `asyncPathsDelegate` 改经接口 default 解析，编译与断言均通过——「漂移无报警」结论成立。另该行 `TestTextEmbeddingWiring` 引用范围 L59-73 宜更正为 L50-68（`engineTextEmbeddingInjectedByType` 方法体）。

### 抽查结果（9/17 行 + 反向钉死 + 门控，全部实测复现）

| 抽查对象 | 抽查方式 | 结果 |
|---|---|---|
| 矩阵 8（假保护·反向钉死） | 全读 `TestCodeFileInfoStructure.java`；核对缺陷侧 `CodeFileInfo.intern()`（L205 md5 / L207 summary 确经 `CodeSymbolInterning.internString` 入全局池） | **维持**。javadoc L23、L69/72/74 三条 `assertSame(...intern())` 断言原文逐字一致；按 C1-008 收窄 intern 面（md5/summary 移出）三断言必红——反向钉死判定与钉死方向正确；对照面 `testInterningRecursesSymbolTypes`（L80-121，低基数符号名）保留裁定正确 |
| 矩阵 2（schemaReady） | 全读 `TestPgVectorStoreSql`（9 测试逐个核对）+ `TestPgVectorIntegration` + `PgVectorStore` | **维持**。`newStore():101-103` 每测试新实例（另两 fail-loud 测试直接 `new PgVectorStore`，同为新实例）、单集合 "docs"（L94-99）、`ddlIsIdempotentWithRealTableName:106-120` 只断言 DDL 文本；`TestPgVectorIntegration:22` env 门控（`NOP_TEST_PGVECTOR_JDBC_URL`）+ 单集合 `nop_test_k2`（L42/44）；`ensureSchema` 调用点 L116/153/189 属实。第三测试类 `TestPgVectorSearchRowMapping`（:40/:53）同为新实例×单集合，无跨集合序列——行判定在全部三个 pgvector 测试类上成立 |
| 矩阵 3（InMemory upsert） | 全读 `TestInMemoryVectorStore`（10 测试、零并发原语）+ 缺陷侧 L69-70 | **维持**。`updateReflectedInSearch:84-93` 只断 `hits.get(0)` 的 id（L92，新旧同 id 恒真），删除 `removeIf`（main L69）该测试仍绿——变异推演复核成立；`singleItemDelegates:148-157` 经 delete 全清亦不可分辨 |
| 矩阵 1（parallelStream） | 全 8 根 grep `getAllJavaSourceFiles\|JavaSourceFileFinder` | **维持**（零命中复现）；`MavenProjectTest:11-16` 确走 `findJavaFileByClassName`（经 `getJavaFileFinder()` 转入，MavenProject:40→32）——邻接但不触达 `getAllJavaSourceFiles` |
| 矩阵 4（ExtensionLanguageMapper） | 全 nop-ai 测试 grep | **维持**（零命中复现）；main 引用仅 `FileLanguageStats` + 本类属实；`testLocalMappingPriority`（L137-153）确钉实例级 `addLocalMapping`（L145），非本站点 |
| 矩阵 10（embedAsync 契约） | 全读 `TestAiModelTextEmbedding`（7 测试） | **维持**。`asyncPathsDelegate:54-65` 全合法输入且 `future.get()` 正确等待；`nullTextRejected:106-110` 只钉同步 `embed(null)/embedAll(null)`；异步负例零断言 |
| 矩阵 12（sync-over-async 超时） | rag 测试树 grep `timeout\|cancel\|orTimeout` | **维持**（仅 `StubHttpClient:32/40/48` 方法签名形参命中，复现）；`StubChat:123-145` 恒成功恒即时属实 |
| 矩阵 15（JavaMethodReplacer） | 全读 `TestCoderErrorCodeConversion` + `JavaMethodReplacer` | **维持**。两个 Replacer 测试（L51-63/L65-75）分别于 main L31/L41 抛出，均在 L50 `Files.write` 写盘前——成功替换路径、写盘失败注入零覆盖属实 |
| 矩阵 16/17（@Disabled / exists-skip） | 读 `TestJavaCodeFileInfoParser`（@Disabled L15/L30，`JavaParserBuilder` 引用 L23/42）+ `TestJavaCodeFileInfoGenerator:82-107` + 生成器 L57/L82 | **维持**。`testGenerateWithValidSummarySucceeds:103` 只断 `outFile.exists()`，不验 JSON 可解析、不重跑验证 skip 语义 |

- **docker/env 门控核实**：8 根测试树全量扫描，`@EnabledIfEnvironmentVariable` 全单元仅 `TestPgVectorIntegration:22` 一处、`@Disabled` 仅 code-analyzer 2 处 + coder 4 处——与矩阵第 2/16 行及系统性观察 4 的门控描述一致；「门控不构成假保护，但使唯一真实 DDL 语义层常态零回归」的裁定准确。

### 缺保护登记抽查

- **格式与编号**：8 条（C10-001~008）编号连续；每条含附录 A 全部 12 字段（含并发场景/触发时序/失效面枚举三个领域字段），均 ≥10 行。
- **判级口径一致性**：P1→缺保护 P2（C10-001/002/003/004）、P2/P3→缺保护 P3（C10-005/006/007/008）逐条核对无违例，合计 P2×4/P3×4 与终止判定一致。
- **证据抽查**：C10-001（newStore/单集合引用行号属实）、C10-002（`updateReflectedInSearch` 片段与实测一致）、C10-004（三条 grep 零命中复现，`MavenProjectTest` 单线程 smoke 属实）、C10-005（异步 happy-path/同步负例分野属实）、C10-008（钉死断言原文逐字一致，分级改写建议与 `ITextEmbedding` 无关的自洽方案）全部通过。
- **覆盖闭合 1 处缺口（需修订前言，非新增条目）**：前言豁免句只列 C1-005/C1-006，**C3-006（P3，Reserved 条件化，矩阵第 14 行判无测试）既未登记也未豁免**，在本文件自设的「P3 逐条处置」枚举逻辑下漏计。按技能正文口径 P0/P1 登记为强制、P2/P3 为裁量，故不构成违规；最小修订为将豁免句扩为「C1-005/C1-006/C3-006 为 P3 卫生/Reserved 条件化项，测试性保护成本大于收益，不单独立项，随缺陷本体修复即可（见矩阵第 5/6/14 行）」。

### 判定统计

- **矩阵行三态判定**：17 行全部**维持**（0 修正、0 驳回）；随行勘误 2 处（第 5 行引用计数 4→5、第 11 行措辞与引用范围），均不动「无测试」判定本体；文件头 1 处口径标签勘误。
- **登记条目**：8 条全部**保留**（0 驳回、0 新增）；1 处前言覆盖闭合修订（C3-006 显式豁免）。
- **反向钉死**：命中 1 项（C1-008）复核成立，断言原文与修复方向（必红路径）逐字核实；3 处定向排查「未命中钉死」结论均复核成立。
- **最终保留缺保护登记数：8（P2×4 / P3×4）**。
- **复核结论：C10 审计通过**——矩阵完整闭合、抽查零误判、判定口径与 C10 正文一致；上述 4 处勘误/修订为标注级，随本节归档，原审计内容未改动。
