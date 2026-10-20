# C3 异步链路正确性 — nop-ai-rest（WI 3.4，仅 rag）

- **批次**: CRA WI 3.4 / C3 维度盲审初审（单批次，仅 nop-ai-rag——roadmap 3.4 裁定「rag 补 C3」）
- **审计员**: C3 初审子代理（盲审，未读 ai-dev/audits|plans|bugs|lessons|backlog，未查 git 历史）
- **日期**: 2026-10-20

## 审计范围与口径

**覆盖声明**：本批次仅覆盖 `nop-ai/nop-ai-rag/src/main/java`（20 文件 / 1,810 行）：`AiModelTextEmbedding`、`RagSearchService`、`RagIngestService`、`RagSynthesizeService`、`PgVectorStore`、`InMemoryVectorStore`、`EmbeddingModelBasedClassifier`、`NopAiRagBizModel` 及 SPI 数据类。nop-ai-rest 的其余子模块（api/coder/dsl-orm/dao/service/app/code-analyzer）由其他批次覆盖。为核对契约可读接口定义（`IEmbeddingModel`、`IChatService`、`ITextEmbedding`、`FutureHelper`、`ContextTaskQueue`——均按「只审使用方式、不重审平台设施自身」口径使用）。

**零 CompletableFuture 实测前提**：全模块穷举 grep（`CompletableFuture|CompletionStage|Executor|new Thread|Scheduled|Timer|Flow.|Publisher|Runnable|syncGet|thenApply|whenComplete|exceptionally|orTimeout|ICancelToken`）证实：rag 的唯一异步站点 = `AiModelTextEmbedding.embedAsync:71` / `embedAllAsync:77` 两处 `CompletionStage` 委托（各含一个 `thenApply`）；无自建线程、定时任务、消息监听器、ReactiveStreams（与基线 `_tmp/cra-baseline-nop-ai-rest.txt` C3a/C3b 一致：C3b 在 rag 分节为零行）。

**关键桥接事实（口径修正）**：`IEmbeddingModel.embed/embedAll` 是接口默认方法 = `FutureHelper.syncGet(embedAsync(...))`（`nop-ai-core IEmbeddingModel.java:29-35`）；`IChatService.call` 同构（`nop-ai-api IChatService.java:19-21`）。因此 rag 的三条「同步」业务链（search 的 `embed`、ingest 的 `embedAll`、synthesize 的 `chatService.call`）**实际都穿越异步边界并以无超时阻塞等待收尾**——这是本维度立案的真实异步面，而非仅两处无人调用的委托方法。

**焦点重锚**（按派单）：① 异步变体（embedAsync/embedAllAsync）相对同步版的边界契约一致性（死代码面也审契约，判级按零调用方条件化）→ C3-001；② 异步委托零调用方的死代码面（F3 修实例不修类别风险）→ C3-002；③ PgVectorStore/InMemoryVectorStore 同步链异常传播——其中**纯同步 F2 半个成功面按派单归 C9 overlay**（CL-006/CL-007），同步链上属于 C3 步骤 5「初始化时序假设」族的状态机缺陷立案 C3-004，请求路径 sync-over-async 的取消/超时语义立案 C3-003。

**上下文传播排除说明**：C3 步骤 4（IContext/ThreadLocal 跨线程传播）逐站点核对为零暴露：两个 `thenApply` 回调（`toFloatArray` / 列表转换）只做数组转换 + `volatile cachedDimension` 写（:110），不触 `ContextProvider`/`NamedThreadLocal`，运行在完成线程上无上下文读取需求。rag 无其他跨线程点，本项零发现（显式空排除，非遗漏）。

**降档口径**：rag 为请求触达面（`NopAiRagBizModel` 三 action 经 GraphQL 可达，`rag-defaults.beans.xml` 已装配）；`embedAsync/embedAllAsync` 为零生产调用方死代码面，相关判级按「零调用方条件化」降档并显式标注。

---

## 发现

### [C3-001] embedAsync/embedAllAsync 异步变体边界契约与同步版背离——输入校验缺失 + 同步裸 NPE 混入 stage 失败模型

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/core/search/AiModelTextEmbedding.java:71-90`（对照同步版 :39-68）
- **证据片段**:
  ```java
  // 异步变体（:71-81）——无输入校验
  public CompletionStage<float[]> embedAsync(String text) {
      return embeddingModel.embedAsync(new AiDocument(text), null)      // text==null 不校验（同步版 :40-43 抛 NopException）
              .thenApply(this::toFloatArray);
  }
  public CompletionStage<List<float[]>> embedAllAsync(List<String> texts) {
      List<AiDocument> docs = new ArrayList<>(texts.size());            // texts==null → 裸 NPE 在同步段抛出，不经 stage
      for (String text : texts) {                                       // 元素 null 不拒绝（同步版 :56-59 抛 NopException）
          docs.add(new AiDocument(text));
      }
  ```
- **严重程度**: P2（按零生产调用方条件化降档；若接入生产即升 P1 面——异步错误模型对消费方破裂）
- **并发场景**: 契约缺陷不依赖并发；异常注入点分布——同步段（调用方线程）抛裸 NPE，嵌入失败段由完成线程（`EmbedCall` IO/`nop-ai-embedding-retry` 调度线程）经 `thenApply` 完成为 exceptional stage。消费方无法用单一模型（`.exceptionally`/`handle`）捕获全部失败。
- **触发时序**: T1: 调用方 `embedAllAsync(null)` → :78 `texts.size()` 抛裸 `NullPointerException`——异常不走 CompletionStage，按 stage 写的恢复代码（`.exceptionally(...)`）永不执行；T2: `embedAllAsync(List.of("a", null))` → 元素 null 被装入 `AiDocument(null)`（:79-81，同步版 :56-59 对应位置抛 `NopException(ERR_AI_CORE_INVALID_ARGUMENT)`）→ 委托 `EmbeddingServiceImpl.embedAllAsync:161-164` 仅取 content 不做元素级校验 → null 文本进入 `EmbedCall` HTTP 请求体 → provider 侧报错或产出垃圾向量——违背类 javadoc :23-24 明文的 fail-loud 失败语义裁定。
- **现状**: 同一实现类内，同步版对 null 引用/ null 元素有完整 `NopException` 校验，异步变体两者皆缺；且参数形状错误以「调用点同步裸抛」与「stage 异常完成」两种互不兼容的失败模型存在。
- **风险**: 今日零生产调用方，无触发路径（降档依据）；一旦 nop-search 索引侧异步化或集成方调用，null/blank 输入的失败语义与同步版分叉，按 stage 模型写恢复逻辑的消费方将漏接同步段异常，索引侧退化为不可诊断的 NPE。
- **建议**: 异步变体入口原样复制同步版 :40-43 / :50-61 的 `NopException` 校验（与下游 `EmbeddingServiceImpl.embedAsync:147-151` 的同步抛 NopException 风格一致）；或统一改为 `CompletableFuture.failedFuture(...)` 完成失败——二选一后把选定语义写入 `ITextEmbedding` SPI javadoc；禁止同步段裸 NPE。
- **失效面枚举**（F3 同族清扫）: `embedAsync:71`（缺 text null 校验——缺陷）；`embedAllAsync:77`（缺 texts/元素校验 + 同步裸 NPE——缺陷）；同步 `embed:39`（有校验——正确）；同步 `embedAll:49`（有校验——正确）；`ITextEmbedding` 默认 `embedAsync/embedAllAsync`（nop-search-api:47-57，包装同步版故继承校验——正确）；`StubTestTextEmbedding`（nop-code-service，测试专用——N/A）；下游 `EmbeddingServiceImpl.embedAsync:147-151`（blank 校验补了单文档路径，批量路径 :161-164 元素级仍漏——nop-ai-core 批次范围，登记供交叉引用）。
- **信心水平**: 确定（同类同步/异步逐行对照可证）
- **误报排除**: 非「CompletableFuture 原语使用本身不是问题」类——报告的是同一类内同步/异步变体校验契约不一致 + 失败模型混用；亦非测试专用代码、非低频管理路径。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现·已知未修复（零生产调用方条件化成立：`embedAsync/embedAllAsync` 全仓 main grep 零调用复核属实；同步版 :40-43/:50-61 校验与异步版缺失对照亲证；下游 `EmbeddingServiceImpl.embedAsync:147-151` blank 校验、`:161-164` 批量元素级仍漏交叉引用亲证无误；接线即升 P1 面）

### [C3-002] 唯一异步面全仓生产零调用——死代码面契约漂移无生产护栏（F3 类别面）

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/core/search/AiModelTextEmbedding.java:71-90`；装配 `nop-ai/nop-ai-rag/src/main/resources/_vfs/nop/ai/rag/beans/rag-defaults.beans.xml:16-19`
- **证据片段**:
  ```java
  // rag-defaults.beans.xml:16-19 —— bean 是生产装配，但消费方只走同步
  <bean id="nopAiTextEmbedding" class="io.nop.ai.core.search.AiModelTextEmbedding" ioc:default="true"
        ioc:type="io.nop.search.api.ITextEmbedding">
      <constructor-arg index="0" ref="nopAiEmbeddingModel"/>
  </bean>
  // nop-search/nop-search-lucene/.../LuceneSearchEngine.java:650 —— 唯一生产消费点
  float[] vector = textEmbedding.embed(query);      // :1041 同为同步 embed
  ```
- **严重程度**: P3（死代码/结构面；C3-001 修复后本条可关闭为 watch-only）
- **并发场景**: 不适用——纯调用拓扑事实。全仓 grep（main 代码，排除 `_gen`/`target`）证实 `AiModelTextEmbedding.embedAsync/embedAllAsync` 零生产调用方；唯一实现消费者 `LuceneSearchEngine:650/1041` 只调用同步 `embed`。
- **触发时序**: 无运行时触发时序（死代码面）。风险触发时序 = 未来任一集成方首次调用 `embedAsync/embedAllAsync` 时，直接继承 C3-001 的契约缺陷，且该缺陷此前无任何生产回归面暴露——缺陷在首次生产调用当天即生效。
- **现状**: 类经 `nopAiTextEmbedding` bean 生产装配并按 by-type 注入 Lucene 引擎（beans.xml :13 注释自证），但 SPI 的异步一半是「仅接口覆盖、无消费者」状态；测试侧 `TestAiModelTextEmbedding:58-64` 对异步变体仅覆盖合法输入 happy-path，null/invalid 输入契约无测试。
- **风险**: F3「修实例不修类别」式演化的温床——后续只改同步版失败语义（例如加超时、改错误码）时，异步版继续漂移且无任何测试/生产路径报警；`getDimension:96` 同为零调用方死代码面（附带登记）。
- **建议**: 二选一：(a) 删除两个异步覆盖方法，回落 `ITextEmbedding` 默认实现（包装同步版，契约自动一致，与 SPI javadoc :44-45「Default implementation wraps sync call」合流）；(b) 保留覆盖并修复 C3-001 + 补 null-input 单测。倾向 (b)——显式覆盖通常有异步化意图，删除属产品决策需 plan。
- **失效面枚举**: `embedAsync:71` / `embedAllAsync:77`（零生产调用方——本条主体）；同步 `embed:39` / `embedAll:49`（活跃面，消费者 LuceneSearchEngine:650/1041）；`getDimension:96`（零调用方，随本条观察）；`ITextEmbedding` 默认异步实现（SPI 侧，非 rag 代码——正确）。
- **信心水平**: 确定（repo 级 grep 全量核实）
- **误报排除**: 非「无状态单例被并发调用属设计意图」类误报；本条报告调用拓扑 + 护栏缺口这一事实面，非指认任何运行时并发错误。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现·已知未修复（死代码面：零生产调用方 + LuceneSearchEngine L650/L1041 仅同步 `embed` 均复核属实；beans.xml 生产装配引用亲证；C3-001 修复后可关闭为 watch-only 的结构裁定采纳）

### [C3-003] synthesize/search/ingest 三条请求主链 sync-over-async 无界阻塞 + null 取消令牌——幽灵 LLM 任务与请求线程无界等待

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/synthesize/RagSynthesizeService.java:44-46`（主证据）；同构面 `RagSearchService.java:34-35`、`RagIngestService.java:68`；桥接定义 `nop-ai-api/.../IChatService.java:19-21`、`nop-ai-core/.../IEmbeddingModel.java:29-35`
- **证据片段**:
  ```java
  // RagSynthesizeService.java:8 有 import io.nop.api.core.util.FutureHelper（未使用）——桥接隐式发生在 SPI 默认方法
  ChatRequest request = new ChatRequest();
  request.withUserPrompt(prompt);
  ChatResponse response = chatService.call(request, null);   // :46 = FutureHelper.syncGet(callAsync(request, null))
  // IChatService.java:19-21
  default ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
      return FutureHelper.syncGet(callAsync(request, cancelToken));
  }
  ```
- **严重程度**: P2（阻塞机制确定；「线程池耗尽」后果依赖 nop-ai-core HTTP 客户端超时配置——本批范围外，故条件化 P2 而非 P1）
- **并发场景**: GraphQL/biz worker 线程（`NopAiRagBizModel.synthesize/search/ingestDocument` 三 action 均请求可达）阻塞等待由 HTTP IO 线程 / `nop-ai-embedding-retry` 调度线程（`EmbeddingServiceImpl:132-141`）完成的 `CompletionStage`；`cancelToken=null` → 平台取消信号无法传播到底层调用。
- **触发时序**: T1: 请求线程进入 `chatService.call` → `IChatService.java:20` `FutureHelper.syncGet:158-174` → `ContextTaskQueue.syncGet`（`ContextTaskQueue.java:46-90`，lock+condition 等待，**无超时参数**）；T2: LLM provider 网络黑洞/不响应 → stage 永不完成 → T1 线程无界挂起（rag 层无超时包装、无 cancel token 可驱动中止）→ 并发请求持续到达时 worker 线程逐个钉死、池耗尽；即使 T1 被中断，底层 HTTP 调用仍在 IO 线程继续跑且无处通知（幽灵任务——C3 步骤 3「超时后原任务是否仍在跑」命中）。
- **现状**: rag 全部三个请求触达 action 都经 SPI 默认方法把异步链同步化，且一律传 null 取消令牌；rag 层无任何 `orTimeout`/超时包装；`RagSynthesizeService.java:8` 残留未使用的 `FutureHelper` import 是该隐式桥接的痕迹。
- **风险**: provider 停滞时请求线程池被无界等待占满（负载上升型故障——终局严重度取决于 nop-ai-core 客户端超时，若客户端有超时则降为延迟放大）；取消语义在 rag 边界断裂——GraphQL 请求取消后 LLM 调用照常计费/占连接。
- **建议**: rag 三服务显式接受并透传 `ICancelToken`（Nop 请求上下文可取），替换硬编码 `null`；在 rag 层对 stage 套 `orTimeout`（或经 `FutureHelper` 超时包装后再 `syncGet`），超时值走配置；清理 :8 死 import。
- **失效面枚举**（F3 同族清扫）: `RagSynthesizeService:46`（`call(request,null)`——缺陷面）；`RagSearchService:34`（`embeddingModel.embed` → `IEmbeddingModel.java:29-31` syncGet——同构）；`RagIngestService:68`（`embedAll` → `:33-35` syncGet——同构）；跨模块同族（他批范围，仅登记）：nop-ai-agent `Layer3FullSummaryStrategy:179`、`LLMCurator:98`、`LlmCompletionJudge:90` 均为 `call(request, null)` 同模式；`AiCommand:339` 走 `callAsync(request, cancelToken)`（正确示范，可作修复参照）。
- **信心水平**: 确定（桥接与无界等待机制代码可证）；「池耗尽」后果为很可能（依赖外部超时配置）。
- **误报排除**: 非「低频路径粗粒度阻塞合理」——三处均为请求主路径；非重审平台设施——`FutureHelper`/`ContextTaskQueue` 实现不立案，立案对象是 rag 使用方式（null token + 无超时直等）；亦非已声明边界——无 owner 文档声明 rag 主链允许无超时无取消。
- **复核状态**: 未复核
- 复核状态: 保留(P2·校准事实补强)｜新发现·已知未修复——桥接链亲证（IChatService.call:19-21 / IEmbeddingModel embed/embedAll 默认方法 = FutureHelper.syncGet → ContextTaskQueue.syncGet `queueReady.await()` 无超时参数）；5 站点总账复核闭合（+classifier L66/EmbeddingExampleConfig L58）；复核补强事实：nop-ai-core HTTP 层存在 `nop.ai.service.read-timeout`（默认 60000ms，ChatServiceImpl:272/EmbeddingServiceImpl:288 设置）——「无界等待」在缺省配置下实为「以 60s 读超时 × 重试次数为界的长阻塞」，无界面残留于第三方 IChatService/IEmbeddingModel 实现与 read-timeout=0 配置；null 取消令牌断裂与幽灵任务面不变（中断等待线程不中止底层 fetchAsync(null)）；rag 请求触达面不降档，P2 维持；跨模块 agent 同族站点（call(request,null)）在 WI 3.1 C7-022/C3-011 取消契约族覆盖内，本条 rag 5 站点首立，零重复立项

### [C3-004] PgVectorStore schemaReady 实例级旗标与 per-collection DDL 作用域错配——第二个 collection 起永久 SQL 失败

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/PgVectorStore.java:52, 82-103`（旗标与 ensureSchema）；调用面 :115-116（store）、:152-153（delete）、:188-189（search）
- **证据片段**:
  ```java
  private volatile boolean schemaReady;                     // :52 实例级全局旗标（不分 collection）
  private void ensureSchema(Connection connection, String collection) throws SQLException {
      if (schemaReady) {                                    // :83-85 首个 collection 引导后，其余 collection 的 DDL 永久跳过
          return;
      }
      ...
      "CREATE TABLE IF NOT EXISTS " + collection + " (id text NOT NULL, embedding vector, ...)"
      schemaReady = true;                                   // :102 只反映「某一个」collection 已建表
  }
  ```
- **严重程度**: P1（生产装配路径——`rag-defaults.beans.xml:4-5` 注释明示生产经 Delta 换 PgVectorStore；设计内多 index 用法必然触发，进程生命周期内不可自愈）
- **并发场景**: 缺陷不依赖并发（单线程确定性复现）；`volatile` 使并发首访可能双跑幂等 DDL（无害，见误报排除）。跨请求共享的单例可变状态（NopIoC 无守卫约定）+ 请求线程并发访问。
- **触发时序**: T1: 首请求 `store(collection="a")`（`RagIngestService:79-80` 将用户可控 `indexId` 设为 collection）→ `ensureSchema` 执行 CREATE TABLE a → :102 `schemaReady=true`；T2: 其后任一时刻（无需并发、无需重启）`store/search/delete(collection="b")` → :83-85 直接 return（b 的 CREATE TABLE **永不执行**）→ :117-132 `INSERT INTO b` → PostgreSQL `relation "b" does not exist`（42P01）→ :134-136 包装 `NopException` 上抛；此后 b 的所有操作恒同失败——`schemaReady` 无复位路径、无 per-collection 重试，重启进程前 b 永久不可用。
- **现状**: 惰性 DDL 的「已完成」状态按实例记录，而 DDL 实际作用域按 collection——状态机作用域错配；javadoc :37-39 声明的「DDL 惰性幂等（首次访问执行 CREATE TABLE IF NOT EXISTS）」语义被实现违背（声明的 per-collection 首次访问，实现的 per-process 首次访问）。
- **风险**: 生产 pgvector 部署下，第二个及以后 index 的摄取/检索/删除全部确定性失败且无法恢复（只能重启进程并在首请求处手工指定目标 collection——无此入口）；单 collection 部署不触发，故缺陷潜伏于生产而非开发缺省（缺省 InMemoryVectorStore）。
- **建议**: `schemaReady` 改为 per-collection 集合：`ConcurrentHashMap.newKeySet<String>()` 记已建表 collection，`ensureSchema` 用 `if (created.add(collection))` 或 `computeIfAbsent` 原子化（同时消除并发首访双 DDL 的非必要重复）；三个调用点（store/delete/search）自动共享修复。
- **失效面枚举**: `store:115-116`（受影响）；`delete:152-153`（受影响，同旗标同根因）；`search:188-189`（受影响）；`IVectorStore` 单条重载 `:52-54/:82-84`（委托列表版，间接受影响）；`InMemoryVectorStore` 无 DDL（N/A）；`update:144-146` 委托 `store`（同受影响）。修复一处 `ensureSchema` 即覆盖全部五个面。
- **信心水平**: 确定（单线程确定性复现路径，无未具体化成分）
- **误报排除**: 不报「volatile 双检/双跑 DDL」——`CREATE TABLE IF NOT EXISTS` 并发双跑幂等无害，属误报校准排除项；本条立案的是旗标与作用域错配导致的**确定性初始化时序假设违约**（「首个请求即完成全部初始化」——C3 步骤 5 明文检查形态），非纯同步 F2 半个成功面（那类已按派单归 C9 overlay）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)·吸收进主登记 C6-001｜新发现·已知未修复——schemaReady 三登记（C1-002=C3-004=C6-001）同一缺陷的初始化时序/契约面视角，非独立工单；独立增量采纳两项：(a) javadoc 语义锚点（L37-38 原文为模糊的「首次访问执行 CREATE TABLE IF NOT EXISTS」，「per-collection 声明」系合理化解读——采纳为佐证而非独立证据）；(b)「并发双跑 DDL 幂等无害」校准排除——被主登记采纳，用于把 C1-002/C6-001 的并发首访时序约束为瞬态面；(c) 失效面 5 入口枚举（store/delete/search + IVectorStore 单条重载 + update 委托）经回码复核完整。修复归属唯一：per-collection ready set 一处修复闭合三登记

---

## 维度集外线索（overlay C2/C4/C5/C7/C8/C9）

（编号续接 nop-ai-rest-CL-006——CL-001…005 已被 C1 占用）

### nop-ai-rest-CL-006（归 C9）ingest 批量链半个成功 + 随机 id 重试重复累积（F2）

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/ingest/RagIngestService.java:62-82`；`PgVectorStore.java:115-133, 225-232`；`InMemoryVectorStore.java:59-76`
- RagIngestService 从不设置 `id` 元数据 → `PgVectorStore.resolveId:225-232` 为每个 chunk 生成随机 UUID → `ON CONFLICT(tenant_id, id)` upsert 永不命中上一次调用的行。触发时序：T1 store 批量 JDBC 执行中途 SQLException（autocommit 下已执行的批元素已持久化——半个成功）→ :134-136 NopException 上抛；T2 调用方重试 ingestDocument → 全部新随机 id → 半截旧 chunk 与全量新 chunk 并存，无中间态标记、无恢复语义。纯同步 F2，按派单归 C9；建议 chunk id 确定性派生（docId+chunkIndex 哈希）+ store 前 delete-by-docId。

### nop-ai-rest-CL-007（归 C9）同 docId 重复摄取不替换旧 chunk——过期内容永久可检索

- **文件**: `RagIngestService.java:44-83`（无任何 delete 调用）；`InMemoryVectorStore.java:65-71`；`PgVectorStore.java:123-131`
- 无需失败路径即触发：对同一 docId 第二次 ingestDocument（文档更新场景），旧版本 chunk 因随机 id 不被 removeIf/upsert 命中，新旧两版本内容同时可检索，旧内容持续污染 synthesize 上下文（检索投毒面）。两 store 实现对称受影响。与 CL-006 同根因（id 非确定性 + 无 delete-by-docId 前置），修复方案同条。归 C9 数据正确性。

### nop-ai-rest-CL-008（归 C2）InMemoryVectorStore.store 的 removeIf+add 复合操作非原子

- **文件**: `InMemoryVectorStore.java:69-71`
- `bucket.removeIf(existing -> id.equals(...))` 与 `bucket.add(data)` 是 CopyOnWriteArrayList 上的两步复合操作，无复合原子性。触发时序：T1 removeIf(id=X) 完成 → T2 removeIf(id=X)（此时为空）→ T1 add(v1) → T2 add(v2) → 同 id 双条目，search 双计。前提是并发 store 且调用方显式提供相同 id 元数据（rag 链路随机 id 不触发）——理论风险未具体化，交 C2 定级；建议 store 方法级同步或改 `ConcurrentHashMap<id, VectorData>` 结构消除 check-then-act。

### nop-ai-rest-CL-009（归 C9）RagSearchService.cosine 维度不匹配静默 0 分——姊妹实现 fail-loud 语义相反（F9 同族）

- **文件**: `RagSearchService.java:62-65`；对照 `InMemoryVectorStore.java:145-149`
- `if (b == null || b.length != a.length) return 0;`——维度不匹配静默给 0 分沉底，无日志无异常，违背模块 fail-loud 基调（AiModelTextEmbedding javadoc :23-24 明文反对静默退化）。同仓姊妹实现 `InMemoryVectorStore.cosine` 同条件抛 `NopException`——同族两实现语义相反。PgVector 链路因 store 期 `checkDimension`（:113/234-240）维度恒等而不可达；InMemory 链路 store 不校验维度，混维度数据进入 RagSearch 链路即命中静默分支。归 C9（静默降级/F9 族）。

---

## 维度复核结论（WI 3.4 独立复核，2026-10-20）

- **复核员**: WI 3.4 独立复核员（合并批 C1+C3+C6 三维度）
- **复核范围**: 本文件 C3-001~C3-004 + C3-round2.md C3-005/C3-006 + 扩展 C3-003，共 6 条发现 + 1 扩展；CL-006~009/CL-016 线索同批核对
- **台账查重结论**: 与 cra-index WI 1.1–3.3 零同站点重复。C3-005 与 WI 3.1 C7-002 / WI 3.3 C3-001 同族（错误以成功形态交付）不同站点；C3-003 的 agent 侧同族站点在 WI 3.1 C7-022/C3-011 取消契约族覆盖内，rag 5 站点为本单元首立；`AiModelTextEmbedding` 虽自 nop-ai-core 迁入（plan 2421），WI 3.1 审计目录经 grep 未登记过该类任何并发面，首审于此成立。

### 逐条判定表

| 发现 | 初审判级 | 判定 | 最终严重度 | 现状标注 | 复核要点 |
|---|---|---|---|---|---|
| C3-001 | P2 | 保留 | P2（零调用方条件化） | 新发现·已知未修复（死代码面） | 同步/异步校验对照亲证；`EmbeddingServiceImpl` 交叉引用行号亲证无误；接线即升 P1 |
| C3-002 | P3 | 保留 | P3 | 新发现·已知未修复（死代码/结构面） | 零生产调用方 + Lucene 仅同步消费亲证 |
| C3-003 | P2 | 保留 | P2 | 新发现·已知未修复 | syncGet 无超时等待链亲证；5 站点总账闭合；复核补强：core HTTP read-timeout 默认 60s 使「无界」降为「有界长阻塞」，取消断裂/幽灵任务面不变；rag 面不降档 |
| C3-004 | P1 | 保留·吸收进 C6-001 | P1（随主登记） | 新发现·已知未修复 | 三登记同缺陷（裁定见 C6 文件）；javadoc「per-collection 声明」降为佐证；并发双跑幂等校准采纳 |
| C3-005（round2） | P2 | **保留并升格** | **P1** | 新发现·已知未修复 | 条件化解除（见下） |
| C3-006（round2） | P3 | 保留 | P3（Reserved 条件化） | 新发现·已知未修复（Reserved 面，接线升 P2） | NaN 路径亲证（L69-84）；Reserved 裁定 javadoc 亲证（L36-40） |

### C3-005 升格裁定（P2 → P1）

初审 P2 附「可达性条件化——取决于 nop-ai-core 返回 error ChatResponse 还是抛异常」。复核以三步证据链闭合该条件，结果为**可达性确定**：

1. **错误响应是设计内常态返回形态**：`ChatServiceImpl.callAsync` 非 200 分支明文注释「响应级错误走 ChatResponse，传输级错误才抛异常」（ChatServiceImpl.java:146-149），`future.complete(errResponse)` 正常完成返回给调用方——无重试包装、无异常化。
2. **缺省路径即命中**：`callAsync` 在 `request.getOptions()` 为 null 时默认 `stream=true`（ChatServiceImpl.java:117-123），rag 的 `chatService.call(request, null)` 走 `aggregateStreamToResponse`，其 `onError` 对响应级错误同样 `future.complete(errResponse)`（L417-427）；5 个方言均有 `ChatResponse.error(...)` 产出点（OpenAiDialect:189 等 NULL_RESPONSE + AbstractLlmDialect:121 parseErrorResponse）。
3. **rag 折叠为成功**：RagSynthesizeService L46-48 不检查 `isSuccess()`，`outputText()` 对空 messages 返回 null（ChatResponse.java:129-148 亲证）→ 兜底 `""` → GraphQL 200 成功返回。

provider 429/401/5xx 或 NULL_RESPONSE 时 synthesize 确定性以成功形态返回空串，检索已计费、失败零信号——「检索成功、合成失败无中间态标记」命中判级表 F2 半个成功（P1）与 F9 静默吞失败条款；同族判级先例 WI 3.1 C7-002（截断以成功交付，P1）、WI 3.3 C3-001（成功截断，P1）对齐。rag 请求触达面不降档。**终裁 P1。**

### 复核裁定说明（其余）

1. **C3-004 吸收裁定**：见 C6-resource-lifecycle.md 复核节「schemaReady 三登记裁定」。
2. **CL-008 = C1-003 同站点吸收**：以 C1-003 为唯一立案，CL-008 关闭为吸收。
3. **CL-006/CL-007（归 C9）**：随机 id + 无 delete-by-docId 事实面亲证成立（RagIngestService L62-77 不设 META_ID），移交 C9 定审维持；与 C1-003 触达条件标注互补（CL-006/007 解释了为什么缺省链不触发 C1-003）。
4. **C3-003 校准事实补强**：复核发现 core HTTP 层 `nop.ai.service.read-timeout` 默认 60000ms（AiCoreConfigs.java:31-32）并在 ChatServiceImpl:272 / EmbeddingServiceImpl:288 实际设置——初审「无界阻塞」在缺省配置下有界，维持 P2 的条件化论证被复核证据反向确认（初审降 P2 的判断正确，但机制描述中「无界挂起」应读作「缺省 60s 有界 + 配置与第三方实现面无界」）。

## 最终保留项（复核后）

| # | 发现 | 最终严重度 | 工单结构 |
|---|---|---|---|
| 1 | C3-005 synthesize 静默折叠错误响应 | **P1**（升格） | 独立工单 |
| 2 | C3-001 异步变体契约背离 | P2（条件化） | 独立工单（与 C3-002 同 diff 可修） |
| 3 | C3-003 sync-over-async 无取消无超时 | P2 | 独立工单（5 站点族） |
| 4 | C3-004 schemaReady | P1 | **吸收进 C6-001 主登记** |
| 5 | C3-002 死代码面 | P3 | watch（随 C3-001 修复关闭） |
| 6 | C3-006 classifier NaN | P3（Reserved 条件化） | 独立登记 |

C3 维度复核后：6 条全部保留、0 驳回、1 升格（C3-005 P2→P1）、1 吸收（C3-004→C6-001）；去重后独立工单 5 条（P1×1 / P2×2 / P3×2）。
