# RAG 向量库就绪门禁/upsert 原子性与合成错误交付并发契约（3.4 AI REST 批 4+1 项裁定）

> Status: active
> Source: WI 3.4 审计目录 `ai-dev/audits/2026-10/2026-10-20-1908-deep-audit-nop-ai-rest-concurrency/`（C6-resource-lifecycle[C6-001 + schemaReady 三登记裁定节]、C1-shared-mutable-state[C1-001/C1-003 + 复核节]、C3-round2[C3-005]、C3-async-chains 复核节[C3-005 升格裁定]、merge-adjudication[MA-01/IND-01~03]、C10-test-protection[C10-001~004]）；批次成员表、站点行号基线、PA 判定与勘误登记以执行计划 `ai-dev/plans/2520-cra-phase8.26-r2-17-ai-rest.md` 成员表为准（本文档为最终状态文档，勘误不落此处；行号 = live @ `88fb6d510f` 2026-10-31 亲读核实值，漂移以 live 为准）
> 实现锚：nop-ai-rag `vector/PgVectorStore`（schemaReady volatile :52 / ensureSchema 提前 return :83-85 / 单表置位 :102 / 三物理守卫点 store :116、delete :153、search :189 / update 委托 store :144-146 / collection 解析 :217-223 / SQLException→ERR_AI_CORE_INVALID_ARGUMENT 映射 :134-137/:164-167/:208-211）、`vector/InMemoryVectorStore`（buckets CHM<String,COWAL> :41 / bucket() computeIfAbsent :54-56 / store 复合 upsert removeIf+add :69-70 / delete 单 removeIf :82 / update 委托 :90-92 / search score 排序 :111 + topK 截断 :115 / javadoc 线程安全声明 :23）、`synthesize/RagSynthesizeService`（chatService.call :46 / outputText :47 / null→"" 折叠 :48 / 既有 ERR_AI_RAG_EMPTY_QUERY 先例 :28）、nop-ai-api `chat/ChatResponse`（**零触碰契约引用面**：isSuccess :283 / error 两工厂 :330/:346 / errorClassification / retryAfterMs / httpStatus 字段）、nop-ai-rag `biz/NopAiRagBizModel`（`@BizQuery synthesize` :70-76——GraphQL 错误面入口）、nop-ai-code-analyzer `maven/JavaSourceFileFinder`（getAllJavaSourceFiles :165-173 局部 ArrayList :166 + parallelStream :168 + forEach 写 :170 / 并行面真实写点 findJavaFiles results.add :236 / findFilesByName 写点 :221 非本路径 / 顺序安全入口 findJavaSourceFilesBySimpleName :127 / searchInAllModules :112 / findBySimpleNameInAllModules :153）
> Related: [agent-orchestration-session-lifecycle-concurrency.md](agent-orchestration-session-lifecycle-concurrency.md)（2519：错误以成功交付族 3.1-C7-002 先例载于其裁定面外 plan 2505——本文裁定④ fail-loud 模式对齐基座；CHM 载体化与惰性初始化惯例引用不重开）；plan 2505（3.1-C7-002 fixed——裁定④判级先例）；plan 2509（3.3-C3-001 fixed——裁定④家族对齐另一成员）；[orm-session-txn-atomicity-and-model-cache-generation.md](orm-session-txn-atomicity-and-model-cache-generation.md)（CHM/computeIfAbsent 惰性初始化平台惯例邻接）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 3.4-C6-001（P1，MA-01 主；←C1-002+C3-004 三登记；C10-001） | schemaReady 实例级 volatile 布尔 → per-collection ready set（`ConcurrentHashMap.newKeySet<String>()` + `add` CAS 语义[预设]）；contains 守卫前置 + DDL 成功后置位；并发首建窗 = IF NOT EXISTS 幂等 + CREATE TABLE SQLException 捕获重试一次（第二失败照常抛出，禁静默吞）；守卫面 = 三物理守卫点 store/delete/search + update 委托间接覆盖（矩阵 §3.2）；既有 `TestPgVectorStoreSql` 9 钉兼容核对表为必查项（§3.4）；错误码映射治理（INVALID_ARGUMENT 误导面）登记注记不代做 |
| ② | 3.4-C1-001（P1，潜伏「接线即生效」；←CL-010；C10-004 IND-01 半边） | getAllJavaSourceFiles 改函数式收集（预设）：parallelStream `map` 出 per-module 局部 List + `flatMap(List::stream)` 终端合并；`findJavaFilesInModule`/`findJavaFiles` 签名与递归链零变更（现签名已收 List 参数）；线程安全载体副作用保形备选拒绝；顺序安全入口四件零触碰；nop-ai 两模块 parallelStream 瑕疵用法仅此 1 处修复后归零（平台两处合法命中白名单，§3.3） |
| ③ | 3.4-C1-003（P1，条件化触发；←CL-008；C10-002） | bucket 载体 COWAL → `ConcurrentHashMap<Object(id), VectorData>`（预设）：`put` 单语句天然原子 upsert（ON CONFLICT 范式对齐）；search 对 `values()` 快照迭代（`new ArrayList<>(bucket.values())`）；排序影响核对结论 = 零影响（显式 score 排序 :111 先于 topK 截断，§3.5 未决②）；delete 改 map remove 键；tenant/collection 外层 bucket 结构不变；javadoc 线程安全声明随实现校正；per-bucket 锁备选拒绝；CL-012 不互吸接缝登记 |
| ④ | 3.4-C3-005（P1 升格；家族 3.1-C7-002/3.3-C3-001；C10-003 主半边） | :46-48 增 `!isSuccess()` 判据 → 抛 `NopException(ERR_AI_RAG_SYNTHESIZE_FAILED)`（nop-ai-rag 模块内新增错误码）；参数集一次定夺 = `ARG_DETAIL`（provider error 消息，既有）+ 新增 `ARG_ERROR_CODE`/`ARG_ERROR_CLASSIFICATION`/`ARG_RETRY_AFTER_MS`（null 者不挂 param）；判据边界 = 仅 `!isSuccess()` 触发，success + outputText null 的合法空回答保留 `""` 返回（设计内行为，机械钉住防过修，§3.6 未决③）；受控重试（errorClassification==RETRYABLE 消费）备选不预设；ChatResponse 契约零触碰（物理在 nop-ai-api，零 diff）+ 条件申报检查条款成文 |
| ⑤ | 测试缝与零回退钉 | 红①–红④驱动器（§5.1；红② trial+探针组合成文）+ 零回退钉全列（§5.4）+ C10 四条成员面集落名（§5.5，含两条半边口径） |

## 二、背景与动机

R2-17 批 4 项（P1×4）覆盖 nop-ai 双工程三类缺陷面：**存储域**（PgVectorStore 实例级 volatile schemaReady 误控 per-collection DDL——多 index 部署从第二个 collection 起永久确定性 SQL 失败且错误码误导为 INVALID_ARGUMENT[时序 a]，并发首建 pg_type duplicate key 瞬态窗[时序 b]；InMemoryVectorStore removeIf+add 复合 upsert 非原子——并发同 docId 摄取产生重复条目，与 javadoc「线程安全」+ upsert-by-id 契约相悖）、**工具域**（JavaSourceFileFinder parallelStream.forEach 向普通 ArrayList 并发 add——多模块仓静默丢文件或 AIOOBE，全仓零外部调用方潜伏）、**交付域**（RagSynthesizeService 不检查 ChatResponse.isSuccess——LLM provider 429/401/5xx 错误响应经 `@BizQuery synthesize` 静默折叠为空串 200 成功交付，错误以成功交付族第 3 例）。本文档一次裁定 4+1 项，使实施只有一种合法形态。

## 三、核心设计契约

### 3.1 裁定①：PgVectorStore schemaReady per-collection 化（C6-001，三登记一处闭合）

- **载体与置位协议（预设分支）**：`volatile boolean schemaReady`（:52）删除，替换为 `private final Set<String> readyCollections = ConcurrentHashMap.newKeySet()`。ensureSchema（:82-103）改形：方法头 `if (readyCollections.contains(collection)) return;`——**contains 守卫前置**（快路径无锁）；执行 CREATE EXTENSION + CREATE TABLE IF NOT EXISTS 后 `readyCollections.add(collection)` **成功置位**。`newKeySet` 的 `add` 底层为 putIfAbsent：幂等不抛错，返回值忽略（并发双线程同 collection 双跑 DDL 由 IF NOT EXISTS 幂等承载正确性，无需外层互斥）。备选（`computeIfAbsent` 串行化——mapping 函数内执行 DDL）被拒：(a) mapping 内执行阻塞 IO 违反 ConcurrentHashMap compute 体内禁长阻塞纪律（bin 锁 hold 住阻塞同 bin 全部操作）；(b) DDL 失败不应被缓存为「已处理」而 computeIfAbsent 成功返回即语义缓存，失败重试面被结构性封死。
- **MA-01 三登记一处闭合声明**：本裁定同时闭合 C1-002（时序 b 并发首建 check-then-act 子面——ready set add CAS 使双跑 DDL 幂等无害）与 C3-004（javadoc 契约锚点 + 失效面 5 入口枚举——守卫面矩阵 §3.2 即其枚举兑现，「并发双跑 DDL 幂等无害」校准由 ready set 化结构性成立）。三登记不立三工单、不三处改；store/delete/search 三入口同一守卫面同 diff 验证。
- **并发首建瞬态窗处置（一次定夺 = 幂等 + 异常捕获重试一次）**：CREATE TABLE 语句保持 IF NOT EXISTS 幂等形态；重试包裹对象**钉死为 CREATE TABLE 的 `stmt.execute()`（:95-101）**——其 SQLException 在**首次建表窗内**（即 `readyCollections.contains(collection)` 为 false 的路径）捕获后**重试一次**（pg_type duplicate key / unique violation 类瞬态）；CREATE EXTENSION 段（:89-93）维持既有形态零改动；重试仍失败照常向上抛出（禁二次吞——rule 24）。置位只在最终成功后执行。备选（平台 IResourceLockManager 分布式锁串行化首建）被拒：PgVectorStore javadoc :38-39 明文「集成方注入其平台 DataSource（本模块不耦合 nop-dao）」——引入平台锁设施打破模块解耦声明；瞬态窗本身 benign（幂等 + 单次重试承载），锁的复杂度无对价。重试上界显式 = 1（无静默跳过，rule 24）。
- **守卫面矩阵（R1 review 转来义务：按物理站点成文）**：

| 入口 | 物理守卫点（live 行号） | 覆盖方式 | 验证载体 |
|------|------------------------|---------|---------|
| store | :116 `ensureSchema(connection, collection)` | 直接 | 红① capturedSql |
| update | :144-146 委托 `return store(...)` | **间接（经 store 守卫）** | 接线验证断言 update 首触发 DDL |
| delete | :153 `ensureSchema(connection, collection)` | 直接 | 红① capturedSql |
| search | :189 `ensureSchema(connection, collection)` | 直接 | 红① capturedSql |

  五入口（含 update）全覆盖；接线验证（rule 23）以 capturedSql 证据断言三物理守卫点真实走 per-collection 判定，update 委托路径断言经 store 间接触发——「方法存在性」不够，必须「真实走」。
- **错误码映射治理注记（登记不代做）**：PgVectorStore 三处 catch SQLException 一律映射 ERR_AI_CORE_INVALID_ARGUMENT（:135/:165/:209）——「表未建/连接失败」被误导为调用方参数错误。属独立治理项（Non-Blocking Follow-up），本批零触碰；ready set 化后「表未建」类确定性失败结构性消失，误导面收窄但不改映射本体。
- **C10-001 前置核对（双向变异推演已登记，随同 diff 兑现）**：`TestPgVectorStoreSql` 9 测试全部 `newStore()` 新实例 + 单集合基线——每测试新实例 readyCollections 空，首操作必发 DDL，对 flag 作用域完全不敏感；9 钉零改写保持绿。兼容核对表 = §3.4（必查项）。

### 3.2 裁定①既有 9 钉兼容核对表（勘误 10 义务，必查项；Phase 2 落 `_tmp/` 留痕）

| # | 钉（TestPgVectorStoreSql） | ready set 化后兼容性判定 |
|---|---------------------------|------------------------|
| 1 | `ddlIsIdempotentWithRealTableName` :106 | **防过修锚点**：新实例首 store 仍发 DDL（contains docs/UNIQUE/IF NOT EXISTS 断言照常命中）；**不得过修为「每次都建表」**——由新增防过修钉（单 collection 二次操作零新增 CREATE TABLE）钉住 |
| 2 | `storeProducesUpsertWithTenantPredicate` :123 | store 主路径零变化（守卫仅包裹 DDL 段），ON CONFLICT 断言照常命中 |
| 3 | `deleteHasTenantPredicate` :137 | delete 守卫发 DDL 后执行 DELETE；钉过滤 `startsWith("DELETE")` 与 DDL 并存无冲突 |
| 4 | `searchProducesCosineWithTenantPredicate` :149 | search 守卫发 DDL 后执行 SELECT；`<=>`/LIMIT/tenant 谓词断言照常命中 |
| 5 | `conditionTriggersWarnNotCrash` :170 | search 路径 DDL + WARN + SELECT 全保留 |
| 6 | `illegalCollectionNameFailsLoud` :181 | 构造器校验零变化（readyCollections 与白名单正交） |
| 7 | `dimensionMismatchFailsLoud` :186 | 连接前置校验零变化 |
| 8 | `queryVectorRequired` :193 | search 前置校验零变化（先于守卫） |
| 9 | `vectorLiteralFormat` :202 | 静态工具方法零变化 |

  9/9 零改写；唯一的新增断言 = 红①两条新钉（见 §5.1），缺陷态红 → 修复后绿。

### 3.3 裁定②：JavaSourceFileFinder 函数式收集（C1-001）

- **预设分支（函数式收集）**：getAllJavaSourceFiles（:165-173）改——`moduleStructure.getModules().values().parallelStream().map(this::resolveModuleDirectory).map(moduleDir -> { List<File> local = new ArrayList<>(); findJavaFilesInModule(moduleDir, local); return local; }).flatMap(List::stream).collect(Collectors.toList())`（终端合并**唯一合法形态 = `flatMap(List::stream)` 后 `collect(toList())`，或合并型 collect（`ArrayList::new, List::addAll, List::addAll`）——直接 `collect(toList())` 于 `Stream<List<File>>` 产嵌套 `List<List<File>>` 不合法，[R2 Minor-3 更正]）。per-module 局部 List 天然线程封闭：并行面内每个 worker 只写自己的局部 List，terminal 合并单线程完成——竞态结构性消除，非「缩小窗口」式缓解。
- **递归链适配面**：`findJavaFilesInModule`（:200-206）/`findJavaFiles`（:228-241）签名（`(File, List<File>)`）与递归结构**零变更**——写点 :236 `results.add` 保留，但其所属 List 由共享降为 per-module 局部。零公共签名演进、零外部行为变化（全仓零外部调用方，潜伏标注维持——不因修复新增公共行为）。
- **备选消解（线程安全载体保形改造）被拒**：CopyOnWriteArrayList 写放大（递归逐文件 add，多模块仓高频写低频读）；synchronizedList 保形则 forEach 副作用共享形态保留——缺陷机制从「竞态」降级为「锁竞争串行化」，且并行收集的意图（模块间真并行）被锁粒度吞噬；函数式形态同时兑现并行收益与线程安全，为唯一合法形态。
- **顺序安全入口零触碰声明（对齐纪律）**：`findJavaSourceFilesBySimpleName`（:127-148）/`searchInAllModules`（:112-122）/`findBySimpleNameInAllModules`（:153-160）/`findFilesByName` 写点（:221，非本调用链）四件顺序流入口零触碰——禁「顺手统一」；[R2 Major-1 更正：批末核对项收窄]nop-ai 两模块 product 树 `parallelStream|parallel()` 瑕疵用法修复后 grep 归零为批末核对项——全仓 main 树另有两处**合法白名单命中**（`nop-api-core ICollectionView.java:42-43` default 方法委托形态、`nop-commons CollectionHelper.java:914` 条件转发形态），均为设施定义/转发非共享可变竞态面，且属 plan-first 保护 + 本批 Non-Goal 零触碰面，仅登记不代做。
- **红②测试缝（trial+探针组合成文）**：见 §5.1 红②。

### 3.4 裁定③：InMemoryVectorStore upsert 原子化（C1-003，ON CONFLICT 范式对齐）

- **载体一次定夺 = bucket 值域 CHM 化（预设）**：`Map<String, CopyOnWriteArrayList<VectorData>> buckets`（:41）的**值域**由 COWAL 改为 `ConcurrentHashMap<Object, VectorData>`（键 = META_ID 值，`Object` 型以承接 store 生成的 UUID String 与调用方自带任意 id 元数据）——外层 `buckets`（tenant/collection 组合键，:43-52）与 `bucket()` computeIfAbsent（:54-56，原子）结构不变。store 复合 upsert（:69-70 removeIf+add）改单语句 `bucket.put(id, data)`：**单语句原子**，与同契约实现 PgVectorStore 的 `ON CONFLICT(tenant_id,id) DO UPDATE`（PgVectorStore :119-120）范式对齐——upsert-by-id 是 owner doc 登记的设计内契约（K2 META_ID 语义），本修复为 InMemory 实现疏漏闭合而非契约演进。**id 非空不变量声明（[R2 Minor-4 补]）**：store 路径 id 非空由 UUID 回退保证（:64-67——META_ID 缺失时生成 UUID 回写），`put` 无 NPE 风险；delete 路径对 null id 显式 no-op（`if (id == null) return;` 前置守卫）——保持现 COWAL `removeIf` 对 null id 恒 no-op 的既有边际语义（CHM `remove(null)` 会 NPE，守卫消除该漂移）。
- **search 快照语义声明（未决②核对结论）**：search 改对 `new ArrayList<>(bucket.values())` 快照迭代（CHM values 为弱一致视图，快照拷贝消除迭代中途 put 的可见性摆动）。**topK 排序影响核对 = 零影响**：排序由显式 score 比较器完成（:111 `hits.sort(Comparator.comparingDouble(Hit::score).reversed())`）且先于 topK 截断（:115）——迭代序只影响排序前遍历序，排序后结果序由 score 唯一决定。**tie 序登记**：List.sort（TimSort）稳定，但 CHM 快照输入序无确定性 → 等分 tie 的相对序从「COWAL 插入序」变为「未指定」；既有 10 钉 fixture 全部 score 互异（逐一核对：a/b/c = 1.0/≈0.994/0.0，其余钉单条目），无一依赖 tie 序——零影响成立；tie 序确定性需求若未来出现，须另立次级比较器（本批零预设）。
- **delete/update 语义保持**：delete（:82 removeIf）改 `bucket.remove(id)` 键移除（幂等不变——对不存在 id 返回 null 即 no-op；null id 前置守卫 no-op，见上条不变量声明）；update 委托 store（:90-92）零变化（经 put 原子 upsert 间接闭合）；单线程幂等语义零回退。
- **javadoc 契约校正**：类 javadoc（:23「线程安全」声明）随实现校正为可兑现实态：「线程安全；同 id upsert 单语句原子（并发同 id 摄取不产生重复条目）」——声明面非契约演进（PA 判定表口径）。javadoc 与实现同 diff 对齐。
- **备选消解（per-bucket 锁收敛复合操作）被拒**：removeIf+add 两步收敛入锁需新锁域（锁对象生命周期/锁序登记/与 computeIfAbsent 内锁的交互），复杂度高于 CHM put 的零锁单语句原子；且锁形态下 search 仍需锁外快照（或读锁），收敛收益为零。CHM put 为 ON CONFLICT 范式的内存等价形态，范式一致性最优。
- **CL-012 接缝登记（不互吸）**：同站点无界账本线索（bucket 数量无上界）属 C5/C7 线索轨，本裁定零触碰不代做——实施时消费不重建。
- **既有 10 钉兼容核对（勘误 10 条款）**：`TestInMemoryVectorStore` 全部 10 钉——tenant 隔离（:107）、topK（:51）、minScore（:69）、fail-loud（:123/:131）、delete（:96）、singleItemDelegates（:148）等语义零变化；`updateReflectedInSearch`（:84）补强度钉 `assertEquals(1, hits.size())` 为**唯一允许的同 diff 改写**（语义对照：原钉只断首条 id，未断旧条目被替换——强度钉补「update 后同 id 仅 1 条」，防 removeIf 整行删除变异仍绿的弱化面；改写留痕随 diff）。

### 3.5 裁定④：RagSynthesizeService isSuccess fail-loud（C3-005，错误以成功交付族对齐）

- **判据与载体（预设分支）**：synthesize :46 `chatService.call(request, null)` 之后、:47 outputText 之前，插入——`if (response == null || !response.isSuccess()) throw new NopException(ERR_AI_RAG_SYNTHESIZE_FAILED).param(...)。` null 响应与错误响应同判据处理（call 契约正常路径不返回 null，防御性归并同面）。错误码 `ERR_AI_RAG_SYNTHESIZE_FAILED` 为 nop-ai-rag 模块内新增（`NopAiRagErrors` 新常量，`define("nop.err.ai.rag.synthesize-failed", ...)` 英文描述，沿 ERR_AI_RAG_EMPTY_QUERY 既有先例形态）。
- **参数集一次定夺**：`ARG_DETAIL`（既有常量，载 provider error 消息）+ 三个新增 ARG 常量 `ARG_ERROR_CODE`（provider errorCode）/`ARG_ERROR_CLASSIFICATION`（ErrorClassification 名）/`ARG_RETRY_AFTER_MS`（retryAfterMs 毫秒值）——对应 ChatResponse 既有字段逐一消费；**null 者不挂 param**（errorClassification/retryAfterMs 对非 RETRYABLE 或无 Retry-After 头的 provider 为 null，属正常形态非缺省）。两工厂形态（:330 简式无 classification / :346 全参式）均被参数面覆盖。
- **判据边界（合法空回答保留，机械断言成文）**：折叠判据**只挂 `!isSuccess()`，不挂空内容**——success 响应 + messages 空/assistant 文本为 null → outputText null → `""` 返回**保留为设计内行为**（LLM 合法返回无文本内容的成功响应不构成错误，fail-loud 过修将把「无回答」误升为故障）。防过修钉 = 红④伴生钉（§5.1）：StubChat success 模式下 outputText null → 断言返回 `""` 且**不抛异常**——机械钉住该边界。
- **GraphQL 消费方错误面影响边界（未决③定夺）**：synthesize 经 `NopAiRagBizModel.@BizQuery synthesize`（:70-76）暴露 GraphQL——修复后错误面从「静默空串 200 成功」变为**类型化 GraphQL error**（error code `nop.err.ai.rag.synthesize-failed` + detail/errorCode/classification/retryAfterMs 参数面）。影响边界判定：(a) 这是**缺陷反转的目标行为**而非回归——检索/调用已计费却交付空回答且无告警面，正是 C3-005 缺陷本体；消费方错误处理路径首次被激活为预期，与 3.1-C7-002/3.3-C3-001 族先例（均 P1 fail-loud）语义对齐；(b) 合法空回答 `""` 成功返回面**零变化**（钉住），消费方对「真无回答」的既有处理不受影响；(c) 受控重试（errorClassification==RETRYABLE 消费）为备选**不预设**——重试策略归可靠性层（IRetryPolicy 面），rag 层只做 fail-loud 单点，不内联重试循环；登记为 Non-Blocking Follow-up。
- **条件申报检查条款（ChatResponse 契约）**：预设 = 纯消费 ChatResponse 既有契约（isSuccess/error/errorCode/errorClassification/retryAfterMs 字段与 getter live 在案），`ChatResponse.java`（物理在 nop-ai-api 模块）**零 diff**、rag 层零契约演进。**实施期触发检查**：若修复确需 ChatResponse 契约演进（新签名/新字段），须先在 plan PA 行 + 本文档修订 + draft review 完成申报并列全消费方回归面方可实施（AGENTS.md nop-*-api plan-first）；预设分支不触，检查记录随实施留痕。

## 四、对齐纪律条款（逐条成文）

1. **MA-01 三登记一处闭合**：裁定①一处修复同时闭合确定性面（时序 a）与并发瞬态面（时序 b）+ C3-004 javadoc 锚点——三登记不立三工单不三处改（§3.1）；守卫面矩阵（§3.1）为 5 入口枚举兑现。
2. **C1-003 × ON CONFLICT 范式对齐**：裁定③ CHM put 与 PgVectorStore `ON CONFLICT ... DO UPDATE` 同契约单语句原子范式（§3.4）；两实现 upsert 语义互证，不另立第三形态。
3. **C3-005 × 错误以成功交付族对齐（R2-02/R2-06 族批声明）**：裁定④ fail-loud 模式对齐 plan 2505（3.1-C7-002 fixed）/plan 2509（3.3-C3-001 fixed）先例；判级先例互证不重开；家族关联不并案（不同站点不同模块）；合法空回答 "" 判据边界与族先例口径一致（判据挂错误不挂空内容）。
4. **C1-001 × 收集范式对齐（F3 结论消费）**：裁定②函数式收集；顺序安全入口四件零触碰；nop-ai 两模块 product 树瑕疵用法批末 grep 归零（平台 ICollectionView/CollectionHelper 两处合法命中白名单登记，见 §3.3）。
5. **C10 四条同 diff（E2）**：C10-001/002/003 主半边/004 IND-01 半边随各自本体同 diff 落地，禁先修本体后补测拆分交付（§5.5）。
6. **nop-ai-api 契约零触碰（条件申报监控）**：ChatResponse.java 零 diff 为硬条款；条件申报条款见 §3.5（预设不触）。
7. **无跨批硬依赖**：族批对齐（2505/2509）为判级/范式口径声明非代码依赖；本批可并行实施。

## 五、裁定⑤：测试缝与零回退钉

### 5.1 红①–红④驱动器（先红后绿，E2；缺陷态红判据 → 修复后绿口径）

| 红 | 成员 | 载体形态 | 缺陷态红 → 修复后绿 |
|----|------|---------|-------------------|
| ① | C6-001 | 同一 `PgVectorStore` 实例（fake DataSource/Proxy Connection 基建，**非 docker 可跑面**——勘误 9 口径落实）：`store(collection a)` 后 `search(collection b)`，capturedSql 断言含**两条** `CREATE TABLE IF NOT EXISTS`（每 collection 一条）。伴生防过修钉：单 collection 二次操作后新增 CREATE TABLE 计数为 **0**（ready set 快路径生效，防「每次都建表」过修） | 缺陷态：b 操作被实例级 schemaReady 短路 → capturedSql 仅一条 → 关联 b 失败红；修复后两条绿。防过修钉两态均绿（单 collection 二次操作 DDL 仅一条） |
| ② | C1-001 | 临时目录 N 模块 × M 文件收集完整性断言：`size == N*M` 且无重复（Set 去重核对）。**红证 = trial + 变异探针组合**（双保险，成文）：(a) **多 trial**——缺陷基线连跑 ≥10 trial，任一 trial 失败（丢文件/AIOOBE）即红证成立（多核 + N≥8 模块下丢文件近必然但非确定，多 trial 覆盖竞态触发面）；(b) **变异探针**——探针件在 findJavaFiles 的 add 前注入 `Thread.yield()`（放宽竞态窗使丢失确定性复现）+ 负向探针（注释 :236 add 行）证明测试真实检测丢失。**探针纪律**：变异恢复后必须以断言方式确认目标可执行行真实存在（grep/读取校验 `results.add(file)` 在位，防恢复半写入假绿——R5 教训）；探针件备份 `_tmp/`，产品树零残留 | 缺陷态：trial 红或探针红任一成立 → 函数式收集修复后收集完整性钉确定性绿（全 trial 通过） |
| ③ | C1-003 | 两线程 latch 对齐同 id 并发 `store`，完成后 `search` 断言同 id 条目 ≤1。**trial 上界**（防负载噪声化）：设 trial 上界（≥10 trial），红证 = 任一 trial 出现重复条目；**探针纪律适用**（触碰容器路径，恢复后断言 `bucket.put(id, data)` 单语句在位）。伴生强度钉：`updateReflectedInSearch` 补 `assertEquals(1, hits.size())` 同 diff 改写（§3.4，唯一允许改写，语义对照留痕） | 缺陷态：removeIf+add 复合交错 → 重复条目红；修复后 put 原子全 trial 绿；单线程强度钉两态均绿（幂等） |
| ④ | C3-005 | StubChat 增 **errorResponse 模式**（C10-003 主半边载体）：返回 `ChatResponse.error(...)` → synthesize 断言抛 `NopException` 且错误码 = `nop.err.ai.rag.synthesize-failed`、param 携 provider error 面。伴生**合法空回答钉**（防过修）：StubChat success 模式 + 空 messages/outputText null → 断言返回 `""` 且不抛异常——机械钉住判据边界（§3.5） | 缺陷态：错误响应折叠 `""` 正常返回红 → `!isSuccess()` fail-loud 绿；合法空回答钉两态均绿 |

### 5.2 测试资产落位

新增/扩展测试按站点归位：nop-ai-rag——`vector/TestPgVectorStoreSql`（红①两新钉扩展，fake DataSource 基建复用）、`vector/TestInMemoryVectorStore`（红③并发钉新建或扩展 + `updateReflectedInSearch` 强度钉）、rag 根 `TestK3PipelineE2E`（红④：StubChat errorResponse 模式 + 合法空回答钉 + 端到端合成链场景）。nop-ai-code-analyzer——maven 侧新建收集完整性测试（红②；测试树现有 MavenProjectTest/MavenModuleStructureTest/MavenDependencyTreeParserTest 三文件零改写）。补测类声明：新增断言即新钉、缺陷态红 → 修复后绿；红绿快照落 `_tmp/` 并登记。端到端义务（rule 22）：≥1 条 rag 存取链（ingest→双 collection store→双 collection search 均命中，覆盖裁定①守卫面贯通）+ ≥1 条合成链（search→synthesize，StubChat error→fail-loud 携错误码 / success 空 output→"" 合法返回，覆盖裁定④正反两面）；C1-001/C1-003 单点修复由红②/红③承载，免端到端义务（声明在案）。

### 5.3 既有钉兼容与改写边界

唯一允许的既有钉改写 = `TestInMemoryVectorStore.updateReflectedInSearch` 强度钉补 assert（§3.4，语义对照留痕）；`TestPgVectorStoreSql` 9 钉零改写（兼容核对表 §3.2 为 Phase 2 必查项，留痕 `_tmp/`）；code-analyzer 三测试与 rag 侧其余测试零改写。

### 5.4 零回退钉全列（钉零回改逐条 git diff 为空核对；唯一允许改写见 §5.3）

- nop-ai-rag vector：`TestPgVectorStoreSql`（9 钉全列 §3.2）/ `TestInMemoryVectorStore`（10 钉，强度钉为唯一受控改写）/ `TestPgVectorSearchRowMapping`。
- nop-ai-rag 其余：`TestK3PipelineE2E`（rag 根，StubChat happy-path 钉）/ `TestNopAiRagBizModelAnnotations`（@BizQuery synthesize 存在性钉——fail-loud 不改 biz 签名）/ `TestTextEmbeddingWiring` / `TestAiModelTextEmbedding` / `TestAiTextEmbeddingVectorSearchE2E` / `TestCosineSimilarityAndRelevanceScore`。
- docker-gated 面（环境门控登记，非本批可跑义务）：`TestPgVectorIntegration` / `TestGraphRagIntegration`。
- nop-ai-code-analyzer：`MavenProjectTest` / `MavenModuleStructureTest` / `MavenDependencyTreeParserTest`——零回退。
- 上游环境抖动按执行计划勘误 8 三联裁定（stash 基线同红 / solo 绿 / 恢复复跑全绿）处理留痕，不得静默重跑或跳过。

### 5.5 C10 集落名（成员面四条，随本体同 diff，E2；半边口径按执行计划勘误 6）

- **C10-001 → C6-001**：多集合/并发首建零回归保护（红①两钉 + 9 钉兼容核对表）。
- **C10-002 → C1-003**：并发同 id upsert 钉（红③）+ 单线程幂等强度钉（`updateReflectedInSearch` 补强）。
- **C10-003 → C3-005（主半边）**：synthesize 错误形态 fail-loud 钉（红④，StubChat errorResponse 模式）。**半边口径**：`neverComplete` 慢速/取消注入面属 IND-06（G60 deferred）共享基建面，successor 搭车不代做。
- **C10-004 → C1-001（IND-01 半边）**：收集完整性测试（红②）。**半边口径**：C1-004/C1-007/C1-009 契约钉留各自本体（G60/条化/deferred 各轨）。

其余 3.4 全量映射成员归 G60 条化束/G61 dormant/条件化激活条款各本体（inventory-p2-c10.md :492/:795 口径）。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① computeIfAbsent 串行化守卫 | mapping 内 DDL 阻塞 IO 违反 compute 短临界纪律；DDL 失败被缓存封死重试面 |
| ① IResourceLockManager 首建锁 | 打破「模块不耦合 nop-dao/平台设施」解耦声明（javadoc :38-39）；幂等+单次重试已承载 benign 瞬态窗 |
| ② synchronizedList/COWAL 保形改造 | 共享副作用形态保留 + 锁竞争吞噬并行收益；函数式收集结构性消除竞态且保真并行 |
| ③ per-bucket 锁收敛复合操作 | 新锁域（生命周期/锁序）复杂度高于 CHM put 零锁原子；范式一致性劣于 ON CONFLICT 等价形态 |
| ③ 保留 COWAL + 全 bucket 锁 | search 仍需锁外快照，收敛收益为零 |
| ④ 以空内容为判据（outputText null 即抛） | 过修：LLM 合法无文本成功响应被误升故障；判据只挂 `!isSuccess()`（族先例口径） |
| ④ rag 层内联 RETRYABLE 受控重试 | 重试策略归可靠性层；rag 单点 fail-loud，内联重试循环违反层职责 |
| ④ ChatResponse 契约演进承载错误面 | 触发 nop-*-api plan-first + 全消费方回归面；既有 isSuccess/error/classification/retryAfterMs 面已足够 |

## 七、与已有设计的关系

- [agent-orchestration-session-lifecycle-concurrency.md](agent-orchestration-session-lifecycle-concurrency.md)：CHM/newKeySet/computeIfAbsent 惰性初始化平台惯例邻接（引用不重开）；错误以成功交付族 fail-loud 判级先例链（3.1-C7-002 → plan 2505 fixed）为裁定④对齐基座。
- plan 2505（3.1-C7-002 fixed）/ plan 2509（3.3-C3-001 fixed）：族先例互证——裁定④模式与判级对齐，零代码依赖。
- PgVectorStore 前案 P1-7.1-002（pgvector search 回读缺失，plan 2398 已修）：同文件不同面，其回归钉（readSearchMetadata 面）在本批零回退钉清单内。
- **下游 SSOT**：G60 条化束（IND-05~09）到达时 (a) StubChat errorResponse 基建（C10-003 主半边）为其消费不重建体（IND-06 慢速/取消注入面 successor 搭车）；(b) CL-012 无界账本线索以裁定③后 live 形态为基线消费本裁定接缝。就绪注记归执行计划收口段。
- owner doc 落点（Phase 3 统一落账，本 Phase 零 owner doc 变更）：`docs-for-ai/03-modules/nop-ai.md`——schemaReady per-collection 作用域契约句（:197 向量库后端节内）+ InMemoryVectorStore upsert 原子性契约句 + synthesize 错误传播契约句（:199 管线节内）+ code-analyzer 收集契约句 + 源码锚点补行；`docs-for-ai/04-reference/source-anchors.md` 按实际。
