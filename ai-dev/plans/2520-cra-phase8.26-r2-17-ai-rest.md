# 2520 CRA Phase 8.26 — R2-17 3.4 AI REST 批（4 项：PgVectorStore schemaReady per-collection 化（三登记一处闭合）+ JavaSourceFileFinder parallelStream 并发容器 + InMemoryVectorStore upsert 原子化（ON CONFLICT 范式）+ RagSynthesizeService isSuccess 检查（错误以成功交付族））

> Plan Status: active
> Draft Review R1: agent_ccbffaf0 @ 2026-10-31 —— 无 Blocker；2 Major（M1 ChatResponse 物理位置事实更正[物理在 nop-ai-api]、M2 TestK3PipelineE2E 幻影双文件更正）+3 Minor（勘误 3 行号回正、五入口→三物理守卫点+委托、红③ trial+探针组合成文）——协调方机械修复闭环，m5 转设计文档义务；修订转 active
> Last Reviewed: 2026-10-31
> 起草说明：本 plan 由独立起草子 agent 于 2026-10-31 在工作仓 `nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation` @ `9181fe64a6`）起草；全部站点行号为该基线 live 亲读回读值（勘误节逐条登记漂移核对结果），执行前如再漂移以 live 为准并登记。结构沿 plan 2519/2518 先例（Source 全文引用 / Draft Review 行预留 / PA 判定表 / 勘误节 / 对齐纪律条款 / 红绿判据 / C10 半边口径 / 环境三联预登记）。待独立 draft review 通过后修订转 active。
> Source: roadmap 工作项 8.26（R2-17，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L168，Status=todo[本 plan 立项时回写为「todo（plan 2520 drafted，待 draft review @ 2026-10-31）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-17 行（L91 全文：finding ID 清单 4 项[3.4-C6-001；3.4-C1-001；3.4-C1-003；3.4-C3-005] / 模块与验证命令 nop-ai `./mvnw test -pl nop-ai -am` / Owner Doc=`docs-for-ai/03-modules/nop-ai.md` / PA=无（nop-ai-api plan-first 零触碰声明） / 依赖「无前置（可并行）；C3-005 R11 与 R2-02/R2-06 族批对齐」 / exit criteria 增补：**schemaReady per-collection 化（三登记一处闭合）；parallelStream 并发容器；upsert 原子化（ON CONFLICT 范式）；isSuccess 检查（错误以成功交付族，IND-03 升格面闭合）**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 3.4 审计目录 `ai-dev/audits/2026-10/2026-10-20-1908-deep-audit-nop-ai-rest-concurrency/`（C6-resource-lifecycle.md[C6-001 + schemaReady 三登记裁定节 :137-147]、C1-shared-mutable-state.md[C1-001 :55-88 / C1-003 :115-160 + 复核节 :332-355]、C3-round2.md[C3-005 :21-60]、C3-async-chains.md 复核节[C3-005 升格裁定 :176-183]、merge-adjudication.md[§二 MA-01 :38-42 / 独立工单表 IND-01~03 :70-72 / §六 家族关联 :183 / PA=0 命中 :114]、C10-test-protection.md[C10-001 :67 / C10-002 :89 / C10-003 :112 / C10-004 :136]）；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` :492（WI 3.4 节）+ 小结行 :795[**3.4（8，P2×4/P3×4）**——本批仅承载 4 主 ID 对应成员面 4 条 C10：001→MA-01/C6-001、002→IND-02/C1-003、003→IND-03/C3-005 主半边、004→IND-01/C1-001 半边；其余 4 条（005/006/007/008）归 G60/G61/条化束/反向钉死各本体]；P1 台账 = cra-index `ai-dev/audits/cra-index.md` :262–265（live 2026-10-31 回读：R2-17 open 四行 = :262[C6-001（MA-01 主）]/:263[C1-001]/:264[C1-003]/:265[C3-005]——**3.4 单元 P1×4 全部且仅为本批成员，无已 fixed P1 行需排除**；P2 台账 G60 :444[条化束 5 条 deferred]、G61 :445[IND-04 dormant] 批外）；3.4 计数口径 = cra-index :64；执行基线 @ `9181fe64a6`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.25 已 done，plan 2495–2519 均已完成或收口）
> Related: plan 2505（completed——R2-02 流式取消/期限两侧同批：**3.1-C7-002 错误以成功交付族先例已 fixed**，C3-005 家族对齐另一成员，判级先例互证不重开）；plan 2509（completed——R2-06 流式缓冲批 A：3.3-C3-001 家族成员已 fixed，R2-17 行族批对齐两成员均已在案）；plan 2421（completed——[R1 勘误：ChatResponse 物理就在 nop-ai-api 模块 `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatResponse.java`，包名 io.nop.ai.api.chat，RagSynthesizeService import 亲读证实；原「物理在 rag 源树/包名保留」表述系审计虚构，删除]——零触碰 = 该文件零 diff，见勘误 7）；**下游**：G60 条化束（8.5x，IND-05/06/07/08/09，B1-12 终裁 deferred）——C10-003 共享面（StubChat 基建）本批落主半边，IND-06 慢速/取消注入面留 successor 搭车；结构模板 = plan 2519（PA 判定表/勘误登记/红绿判据/C10 半边口径/环境三联预登记）与 plan 2518
> Protected Area 授权：批次 PA=**无**（batch-proposal R2-17 行 PA 列显式「无（nop-ai-api plan-first 零触碰声明）」；§二.4 前案表 :302 明文 3.4 nop-ai-api 属「对应批零触碰声明」组）。**批内条件申报条款（一处，条件触发制）**：**C3-005 修复若需演进 `io.nop.ai.api.chat.ChatResponse` 契约（isSuccess/errorClassification 签名或新增字段）**——预设 = 纯消费既有契约（live `isSuccess` :283、`error` 两工厂 :330/:346、`errorClassification` javadoc :81-88 均已存在，rag 层新增模块内错误码 `ERR_AI_RAG_SYNTHESIZE_FAILED` 即足够）；若 Phase 1 裁定确需 nop-ai-api 契约演进，触发 AGENTS.md「跨模块公共 API（nop-*-api）plan-first」，须在本 plan PA 行 + 设计文档 + draft review 完成申报并列全消费方回归面方可实施。**头部预设：4/4 成员零 orm.xml 触碰、零 `_` 生成物触碰、零 nop-ai-api 源码触碰**（PA 判定表逐一核实；C3-005 修复点在 `nop-ai-rag` 的 `RagSynthesizeService`，`ChatResponse.java` 物理就在 nop-ai-api 模块[R1 勘误更正，见勘误 7]，零触碰 = 该文件零 diff）；站点分布于 `nop-ai/nop-ai-rag`（三单）与 `nop-ai/nop-ai-code-analyzer`（一单）两模块，E3 命令 `./mvnw test -pl nop-ai -am` 触达 nop-ai 聚合器及上游链。

## Purpose

关闭 R2-17 批的 4 项已确认缺陷（P1×4；cra-index open 四行 :262–265 批次列=R2-17，live 2026-10-31 回读全 open；WI 3.4 单元 15 工单 + watch 1 中本批 = P1×4 全集中仍 open 的全部 4 张工单——P2×6/P3×5 归 G60 条化束[deferred]/G61 dormant/条件化激活条款各本体，C10 缺保护 8 条中 4 条成员面随本批收口）。核心结果面有四：①**PgVectorStore schemaReady per-collection 化**（C6-001←C1-002/C3-004 三登记）——实例级 volatile 布尔门禁 per-collection 粒度 DDL，多 index 部署从第二个 collection 起永久确定性 SQL 失败（进程生命周期不可自愈）+ 并发首建 CTA 瞬态窗，三登记一处修复闭合。②**JavaSourceFileFinder parallelStream 并发容器**（C1-001←CL-010）——parallelStream.forEach 向普通 ArrayList 并发 add，多模块仓静默丢文件或 AIOOBE（并行面内生于方法体；全仓零外部调用方潜伏标注，接线即生效）。③**InMemoryVectorStore upsert 原子化**（C1-003←CL-008）——removeIf+add 复合 upsert 非原子，并发同 docId 摄取产生重复条目，与类 javadoc「线程安全」契约相悖（PgVectorStore ON CONFLICT 正确范式反证；触发需显式同 id 并发 upsert，条件化标注）。④**RagSynthesizeService isSuccess 检查**（C3-005，IND-03 升格面闭合）——不检查 ChatResponse.isSuccess，LLM provider 错误响应静默折叠为空串 200 成功交付（错误以成功交付族第 3 例，与 3.1 C7-002/3.3 C3-001 家族关联）。批末 roadmap 8.26 → done，cra-index 四行收敛 + C10 成员面注记。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-17 = 4 项成员全 P1（C6-001[MA-01 主]；C1-001[IND-01]；C1-003[IND-02]；C3-005[IND-03 升格 P2→P1，可达性三步证据链闭合，见勘误 5]）；cra-index live 2026-10-31 回读 R2-17 批次列 open 四行 = :262–265；roadmap 8.26 行 Status=todo。依赖核对（live 2026-10-31）：**无前置（可并行）**——batch-proposal R2-17 行依赖列明文「无前置（可并行）；C3-005 R11 与 R2-02/R2-06 族批对齐」，族批对齐两成员均已收口（3.1-C7-002 → plan 2505 fixed；3.3-C3-001 → plan 2509 fixed），对齐为判级/范式口径声明而非代码依赖，基线门禁满足。
- **同单元批外面登记（Non-Goal 零触碰）**：WI 3.4 其余工单归 G60 条化束（8.5x，IND-05/06/07/08/09 五条 B1-12 终裁 deferred，触发条款=接线/接管线）、G61（IND-04 dormant，零调用者）、4 张条件化 P2 裁决留 7.1、watch 1（C3-002）；C10 缺保护 8 条中 C10-005/006/007/008 随各 deferred/dormant 本体。本批 diff 不触碰其任何站点；C10-003 的 StubChat 共享基建落主半边（errorResponse 模式），neverComplete 慢速/取消注入面登记为 successor 搭车点不代做（半边口径见勘误 6）。
- **吸收成员（随主条目同 diff，不单列工单；merge-adjudication §一/§二）**：C6-001←C1-002（并发 check-then-act 首访竞态子面[时序 b]，C3-004 校准为瞬态面）+ C3-004（javadoc 契约锚点 + 失效面 5 入口枚举 + 「并发双跑 DDL 幂等无害」校准）——schemaReady 三登记（C1-002=C3-004=C6-001）一处修复闭合（merge-adjudication :40-42 修复结构明文）；C1-001←CL-010（同文件同行号同竞态的 C6 overlay 视角登记，吸收关闭）；C1-003←CL-008（同站点同竞态的 C3 overlay 登记，吸收关闭；CL-012 同站点不同缺陷面[无界账本]不互吸，移交 C5/C7 线索）。
- **C10 验收前置入批集（映射 SSOT = `inventory-p2-c10.md` :492 WI 3.4 节 + :795 小结行 + `C10-test-protection.md` 逐条；随本体同 diff 落地[E2]）**：
  - C10-001→C6-001（PgVectorStore 多集合/并发首建零回归保护：现有 9 测试全部 `newStore()` 新实例 + 单集合基线，对 flag 作用域完全不敏感——变异推演双向验证登记在案）；
  - C10-002→C1-003（并发同 id upsert 零测试 + 单线程 upsert 幂等断言弱化：`updateReflectedInSearch` 新旧版本 id 同为 "a"，removeIf 整行删除变异仍绿）；
  - C10-003→C3-005（synthesize 错误形态零故障注入：StubChat 恒成功恒即时，L47-48 折叠分支零执行；主半边=errorResponse 模式）；
  - C10-004→C1-001（code-analyzer 测试树 9 文件全单线程，`getAllJavaSourceFiles` 连收集完整性断言都不存在；本批仅承载 IND-01 半边——收集完整性测试，C1-004/C1-007/C1-009 覆盖面留各自本体）。
- **3.4 批前已收口面（live 形态登记，零触碰）**：WI 3.4 无已 fixed P1 行（P1×4 全 open 即本批）；同族先例 3.1-C7-002（plan 2505）与 3.3-C3-001（plan 2509）为跨单元家族关联非同站点，零触碰；PgVectorStore 前案 P1-7.1-002（pgvector search 回读缺失）已修复且与本批 schemaReady 不同面（merge-adjudication :177 前案排除在案），`TestPgVectorStoreSql` 9 测试为该修复面回归网——本批红①新增钉与既有 9 钉共居一文件，既有钉零回退。
- **平台设施与测试资产盘点（live 实读 @ `9181fe64a6`）**：
  - **先例范式可引用不重开**：PgVectorStore ON CONFLICT 原子 upsert（C1-003 正确兄弟范式，merge-adjudication :71 反证登记）；`ConcurrentHashMap.newKeySet`/`computeIfAbsent` 惰性初始化平台惯例（C6-001 建议面）；错误以成功交付族 fail-loud 判级先例（3.1-C7-002/3.3-C3-001 均 P1，plan 2505/2509）；`NopException` + `ErrorCode` + `.param(...)` 两层错误策略（AGENTS.md 约定 + `ERR_AI_RAG_EMPTY_QUERY` 模块内既有先例）。
  - **owner doc 现状**（2026-10-31 亲读）：`docs-for-ai/03-modules/nop-ai.md`——:54 nop-ai-rag 模块定位、:84 pgvector search 回读、:197 向量库后端节（含「owner doc 为 nop-ai 设计文档集的 vector-store.md（A1-A4 契约裁定）」指针）、:199 RAG 管线节；**无 schemaReady 作用域契约句、无 InMemoryVectorStore upsert 原子性契约句、无 synthesize 错误传播契约句、无 code-analyzer 收集契约句**——四成员 owner doc 补句主落点。
  - **测试资产（live 核实）**：`TestPgVectorStoreSql`（9 @Test，`newStore` :101、`ddlIsIdempotentWithRealTableName` :106，fake DataSource/Proxy Connection 基建在案）、`TestPgVectorIntegration`/`TestPgVectorSearchRowMapping`/`TestGraphRagIntegration`（vector/）；`TestInMemoryVectorStore`（10 @Test，`updateReflectedInSearch` :84）；`TestK3PipelineE2E`（rag 根，`StubChat` :123 恒成功）、`TestNopAiRagBizModelAnnotations`（biz/）；`TestTextEmbeddingWiring`/`TestAiModelTextEmbedding`/`TestAiTextEmbeddingVectorSearchE2E`（core/search/）、`TestCosineSimilarityAndRelevanceScore`（core/api/embedding/）；code-analyzer 侧 `MavenProjectTest`（1 @Test 单线程 smoke）/`MavenModuleStructureTest`/`MavenDependencyTreeParserTest`/`TestK3PipelineE2E`（maven/——**与 rag 根同名异站，防混登记见勘误 1**）。
- **模块聚合形态与勘误**：审计单元标签「nop-ai-rest」覆盖双 maven 工程——repo 实际 = `nop-ai/nop-ai-rag`（C6-001/C1-003/C3-005 三单）与 `nop-ai/nop-ai-code-analyzer`（C1-001 一单）；E3 命令 `./mvnw test -pl nop-ai -am` 触达两模块及上游链，与 batch-proposal 验证命令列一致（详见勘误 1）。

### 对齐纪律显式条款

- **MA-01 × 三登记一处闭合（C6-001 吸收 C1-002/C3-004）**：schemaReady 改 per-collection ready set（`ConcurrentHashMap.newKeySet` + `add` CAS 语义或 `computeIfAbsent`）一处修复同时闭合确定性面（时序 a：第二个 collection 起永久 SQL 失败）与并发瞬态子面（时序 b：pg_type duplicate key 首访竞态，重试可过）——三登记不立三工单、不三处改（merge-adjudication :40-42 修复结构明文）；store/delete/search 三入口（:116/:153/:189）同一守卫面同 diff 验证。
- **C1-003 × ON CONFLICT 范式对齐**：InMemoryVectorStore upsert 原子化参照同契约实现 PgVectorStore 的 `ON CONFLICT ... DO UPDATE` 单语句原子范式（merge-adjudication :71 反证：upsert-by-id 是 owner doc 登记的设计内契约[K2 META_ID 语义]，InMemory 实现疏漏而非契约模糊）——载体选型（CHM<String,VectorData> bucket vs per-bucket 锁）Phase 1 一次定夺，两方向均须兑现「search 快照读 + delete 语义不回退」。
- **C3-005 × 错误以成功交付族对齐（R2-02/R2-06 族批对齐声明）**：修复模式对齐已收口族先例——错误响应 fail-loud（抛 NopException + 模块内错误码 + `.param` 携 error/retryAfterMs 面），禁以 `""` 兜底错误响应；**合法空回答语义（success 响应 + outputText 为 null）保留 `""` 返回为设计内行为**（折叠判据只挂 `!isSuccess()`，不挂空内容——判据边界为 Phase 1 裁定项）；判级先例互证（3.1-C7-002/3.3-C3-001 均 P1），家族关联不并案（不同站点不同模块）。
- **C1-001 × 收集范式对齐（F3 全类清扫结论消费）**：修复为函数式收集（per-module 局部 List + `collect`/`reduce` 终端合并）或等价线程安全载体——家族内其余 4 个顺序安全入口（`findJavaSourceFilesBySimpleName` :132-148 / `searchInAllModules` :115-121 / `findBySimpleNameInAllModules` :153-160 / splitter/parser 局部 List）零触碰不「顺手统一」；全仓 `parallelStream|parallel()` 仅此 1 处（审计 F3 grep 结论，live 复核维持）。
- **同变更集绑定（C10 随本体，E2）**：C10-001/002/003/004[IND-01 半边] 四条与各自本体同 diff 落地，禁止先修本体后补测的拆分交付。
- **硬序条款**：本批无跨批硬依赖（无前置，可并行）；批内纪律 = 每成员先红后绿或变异/回退论证（E2），非批次次序约束。
- **跨单元同号防混**：3.4-C1-001/C1-003 与 2.5-C1-003（plan 2518 已收口[peri PubSub 面]）等同号异站零关联——cra-index 回填时逐行带 3.4 前缀核对；TestK3PipelineE2E 同名双文件（rag 根 / code-analyzer maven）防混见勘误 1。

### 4 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `9181fe64a6` 2026-10-31 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；吸收成员） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 3.4-C6-001（P1，MA-01 主登记；←C1-002+C3-004 三登记；C10-001） | `PgVectorStore.java`（nop-ai-rag `vector`） | `volatile boolean schemaReady` :52；`ensureSchema` 提前 return :83（:82 方法头）；单表置位 `schemaReady = true` :102；三入口调用点 store :116 / delete :153 / search :189；collection 解析 :217-223 | 门店级就绪标志误控 per-collection DDL：多 index 部署从第二个 collection 起永久确定性 SQL 失败（进程生命周期不可自愈、错误码误导为 INVALID_ARGUMENT）+ 并发首建 CTA 瞬态窗（pg_type duplicate key，重试可过） | 裁定①（C10-001 随同 diff；**三登记一处闭合**）：schemaReady 改 per-collection ready set（`ConcurrentHashMap.newKeySet` + `add` CAS[预设] vs `computeIfAbsent` 串行化——一次定夺）；并发首建窗处置（容忍 IF NOT EXISTS 幂等 + 并发 DDL 异常捕获重试一次 vs 平台 IResourceLockManager——一次定夺，瞬态面消解声明）；[R1 Minor-4 更正：物理守卫点三处 store/delete/search + update 经委托间接覆盖]守卫面同一验证；错误码误导面（INVALID_ARGUMENT 掩盖表未建根因）登记为错误映射治理注记不代做 |
| 2 | 3.4-C1-001（P1，潜伏标注「接线即生效」；←CL-010；C10-004 半边） | `JavaSourceFileFinder.java`（nop-ai-code-analyzer `maven`） | `getAllJavaSourceFiles` :165-173：局部 `ArrayList results` :166 + `parallelStream()` :168 + `forEach(moduleDir -> findJavaFilesInModule(moduleDir, results))` :170；并行面真实写点 `results.add(file)` :236（`findJavaFiles` 递归内；:221 为 `findFilesByName` 写点，非本路径——勘误 2） | parallelStream 多 ForkJoin worker 向普通 ArrayList 并发 add——多模块仓静默丢文件或 AIOOBE（并行面内生于方法体；全仓零外部调用方，任一首次接线即在多模块+多核环境走主路径触发） | 裁定②（C10-004 收集完整性半边随同 diff）：函数式收集一次定夺——(a) 预设=per-module 局部 List 收集 + 终端合并（`collect`/`reduce` 语义天然合并）；(b) 备选=线程安全载体（CopyOnWriteArrayList/synchronizedList——副作用 forEach 保形改造）；`findJavaFilesInModule`/`findJavaFiles` 签名与递归链适配面同 diff；零调用方潜伏标注维持（不因修复新增公共行为） |
| 3 | 3.4-C1-003（P1，触发条件化「需显式同 id 并发 upsert」；←CL-008；C10-002） | `InMemoryVectorStore.java`（nop-ai-rag `vector`） | `store` 复合 upsert :69-70（`bucket.removeIf(...META_ID...)` :69 + `bucket.add(data)` :70）；`update` 委托 `store` :91-93；类 javadoc 线程安全声明 :23；`delete` 单次 removeIf :82（COWAL 内部持锁原子，安全）；`search` :95-119 只读快照（安全）；`bucket()` computeIfAbsent :54-56（原子） | removeIf+add 复合 upsert 非原子——并发同 docId 摄取产生重复条目（search 返回同 id 两次、top-K 名额被占；与 javadoc「线程安全」+ upsert-by-id 契约相悖） | 裁定③（C10-002 随同 diff；**ON CONFLICT 范式对齐**）：upsert 原子化一次定夺——(a) 预设=bucket 载体改 `ConcurrentHashMap<String(id), VectorData>`（`put` 天然原子 upsert，search 对 values 快照迭代）；(b) 备选=per-bucket 锁收敛复合操作；tenant/collection 外层 bucket 结构不变；delete/search/单线程幂等语义零回退（`TestInMemoryVectorStore` 10 钉保持绿 + `updateReflectedInSearch` 补强钉同 diff） |
| 4 | 3.4-C3-005（P1 升格[P2→P1]；家族关联 3.1-C7-002/3.3-C3-001；C10-003 主半边） | `RagSynthesizeService.java`（nop-ai-rag `synthesize`） | `chatService.call(request, null)` :46（不看 isSuccess）；`response.outputText()` :47（错误响应 messages 为空常态返回 null）；`return answer != null ? answer : ""` :48（错误响应折叠 ""）；契约依据 `ChatResponse.isSuccess` :283 / error 两工厂 :330/:346 / `outputText` :129 | 不检查 ChatResponse.isSuccess——LLM provider 429/401/5xx 错误响应静默折叠为空串 200 成功交付（检索/调用已计费、无错误码无告警面；错误以成功交付族第 3 例，IND-03 升格面） | 裁定④（C10-003 主半边随同 diff；**族 fail-loud 模式对齐**）：:46-48 增 `!isSuccess()` 判据→抛 `NopException(ERR_AI_RAG_SYNTHESIZE_FAILED).param(...)`（错误码为 nop-ai-rag 模块内新增，携 error/errorClassification/retryAfterMs 参数面——Phase 1 定参数集）；**判据边界**：success + outputText null 的合法空回答保留 `""` 返回（设计内行为，同 diff 钉住防过修）；受控重试（errorClassification==RETRYABLE 消费）为备选不预设；nop-ai-api 契约零触碰（条件申报条款监控） |

### PA 判定表（4 成员逐一，orm/_生成物/跨模块公共 API/nop-api-core 触碰与否；live 2026-10-31 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 触 `_` 生成物？ | 触跨模块公共 API / nop-api-core？ | 判定依据与条件申报 |
|---|---|---|---|---|---|---|---|
| 1 | C6-001 | nop-ai-rag main java | 就绪标志 per-collection 化 | 否 | 否 | 否（PgVectorStore 模块内实现类，不注册 default bean，装配归消费方） | 纯 Java 修复；错误码映射治理面仅登记注记 |
| 2 | C1-001 | nop-ai-code-analyzer main java | parallelStream 收集容器 | 否 | 否 | 否（JavaSourceFileFinder 模块内工具类，零外部调用方） | 纯 Java 修复；潜伏标注维持不扩契约 |
| 3 | C1-003 | nop-ai-rag main java | bucket 载体原子化 | 否 | 否 | 否（IVectorStore 契约零签名变更，实现内改造） | 纯 Java 修复；javadoc 线程安全声明随实现校正（声明面非契约演进） |
| 4 | C3-005 | nop-ai-rag main java | isSuccess fail-loud | 否 | 否 | **否（预设纯消费 ChatResponse 既有契约零触碰；ChatResponse 物理在 nop-ai-api 模块[R1 勘误更正]，零触碰 = 该文件零 diff）** | 条件申报：若需 ChatResponse 契约演进→头部 PA 行申报（plan-first，列全消费方回归面）；预设仅 rag 层新增模块内错误码 |

**结论：头部零 orm 触碰、零 `_` 生成物触碰、零 nop-ai-api 源码触碰声明成立**（4/4 = `src/main/java` 纯代码修复；站点分布于 nop-ai-rag/nop-ai-code-analyzer 两模块，无 ORM 模型与生成物——live find 核实；merge-adjudication :114「15 张工单零工单触碰 nop-ai-api 源码」与本批一致）。一处条件申报条款见头部 PA 行（C3-005 ChatResponse 契约演进形态）。

### Baseline 勘误（输入不一致登记）

1. **模块归属与同名防混勘误**：审计单元标签「nop-ai-rest」为 RAG REST 面覆盖口径——四成员实际分布于 `nop-ai/nop-ai-rag`（C6-001/C1-003/C3-005）与 `nop-ai/nop-ai-code-analyzer`（C1-001）双 maven 工程；[R1 Major-2 更正：TestK3PipelineE2E.java 仅 rag 根一份，code-analyzer maven 侧测试树只有 MavenProjectTest/MavenModuleStructureTest/MavenDependencyTreeParserTest 三文件——原「同名双文件」为审计虚构，无防混面]红④钉落在 rag 根文件；E3 命令 `./mvnw test -pl nop-ai -am` 触达两模块，与 batch-proposal 一致。
2. **C1-001 写点勘误**：审计引「写点 `:228-241, 236`」——live 回读 `results.add` 两处：`:221`（`findFilesByName`，按文件名查找路径，**非 getAllJavaSourceFiles 调用链**）与 `:236`（`findJavaFiles`，parallelStream→findJavaFilesInModule 递归链真实写点）；并行面写点收窄为 :236 单点，缺陷机制（共享 ArrayList 并发 add）与修复面不变；「全仓 parallelStream 仅此 1 处」「getAllJavaSourceFiles 全仓零调用方」live grep 复核均属实（`grep -rn getAllJavaSourceFiles --include='*.java'` 除本文件零命中）。
3. **C1-003 行号微漂移[R1 Minor-3 更正]**：审计引 `update` 委托 `:90-92` 为准（live update 签名 :90、委托 return :91）；:69-70 复合 upsert、:82 delete removeIf、:23 javadoc 线程安全声明 live 亲读吻合；缺陷机制零漂移。
4. **C6-001/C3-005 行号零漂移**：PgVectorStore :52/:83/:102/:116/:153/:189 与 RagSynthesizeService :46/:47/:48、ChatResponse `isSuccess` :283/`outputText` :129/error 工厂 :330/:346 live 回读与审计引用逐项一致，无需修正。
5. **C3-005 升格事实链登记**：初审 P2 条件化（「取决于 core 返回 error ChatResponse 还是抛异常」）经复核解除升格 P1——三步证据链：方言产出 error 响应 → `ChatServiceImpl.callAsync` 非 200 与缺省 stream=true 路径均 `complete(errResponse)` 正常返回（响应级错误不抛异常）→ rag 折叠为 "" 成功交付（C3-round2 :21 + C3-async-chains :176-183）；本批以 P1 全力修复承接，判级先例对齐 3.1-C7-002/3.3-C3-001。
6. **C10 半边口径**：C10-003 本批仅承载 IND-03 主半边（errorResponse 模式 fail-loud 钉）——`neverComplete` 慢速/取消注入面属 IND-06（G60 deferred）共享基建面，successor 搭车不代做；C10-004 本批仅承载 IND-01 半边（收集完整性测试）——C1-004/C1-007/C1-009 契约钉留各自本体（G60/条化/deferred 各轨）；Phase 4 收敛注记按此书写。
7. **nop-ai-api 零触碰口径[R1 Major-1 事实更正]**：`ChatResponse.java` 物理路径 = `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatResponse.java`（原「物理在 rag 源树/包名保留 io.nop.ai.core.api.*」为审计虚构，live find 唯一命中且 RagSynthesizeService.java:4 import 证实）——零触碰 = 本批 diff 不得触碰该文件；C3-005 预设修复仅消费既有 `isSuccess`/error 字段 + rag 模块内新增错误码，live 核实契约面已足够（条件申报条款监控触发）。
8. **环境项预登记（批前既有，沿 plan 2512–2519 已知面）**：`./mvnw test -pl nop-ai -am` 触达上游模块链（nop-kernel/nop-api-core/nop-core/nop-commons/nop-orm 等）——上游环境抖动按「stash 基线同红 / solo 绿 / 恢复复跑全绿」**三联裁定**处理并留痕，不得静默重跑或跳过；已知三面预登记：**nop-core `TestResourceCacheInvalidationChain` 负载 flake**（2517/2518/2519 执行期留痕先例）、**surefire fork 启动 EOF 崩溃类**（2519 toolkit 面 r1 先例，复跑即绿但须留痕）、**recovery CME 间歇红**（2519 E3 先例）。
9. **docker-gated / 外部依赖测试面登记**：`TestPgVectorIntegration`/`TestGraphRagIntegration` 依赖真实 pgvector/docker 环境（默认不跑或环境门控）——本批 C6-001 红钉须走 `TestPgVectorStoreSql` fake DataSource/Proxy Connection 基建（SQL 文本断言，非 docker 可跑面）；执行时如遇环境缺位按三联口径登记。
10. **[设计文档起草增补条款]** C6-001 修复涉及 `ensureSchema` 守卫语义改动，设计文档须给出与 `TestPgVectorStoreSql` 既有 9 钉的兼容性核对表（单集合幂等钉 :106 防过修为「每次都建表」）；C1-003 载体重写须核对 `TestInMemoryVectorStore` 10 钉（tenant 隔离/topK/fail-loud）；既有钉零回退。

## Goals

- Phase 1 设计裁定文档一次裁决 4+1 项裁定（裁定①–⑤，含三个吸收族面与两组同变更集/范式对齐绑定）+ 测试缝（独立 draft review 通过），无临场发明空间：①schemaReady per-collection 化（C6-001 三登记一处闭合）②parallelStream 收集容器函数式化（C1-001）③InMemoryVectorStore upsert 原子化（C1-003，ON CONFLICT 范式）④synthesize isSuccess fail-loud（C3-005，错误以成功交付族对齐）⑤测试缝与零回退钉清单——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。设计文档拟名 `ai-dev/design/concurrency/rag-vector-schema-scoping-and-synthesis-error-concurrency.md`（Phase 1 产出物，前向引用——落盘前 doc-link warning 为预期已登记；命名裁定：向量库就绪门禁/upsert 原子性（存储域）+ 并行收集（工具域）+ 合成错误传播（交付域）三域合一，与现有 23 份命名风格一致；如内容演进更适可调，调整时在本 plan 登记命名裁定）。
- 4 项成员全部修复并各带回归测试（rule 25）；C10 收口门（C10-001/002/003 主半边/004 IND-01 半边 四条成员面）随本体同 diff 落地（E2）。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红④逐成员登记：红① 作用域红（同实例 store(collection a) 后 search(collection b)，capturedSql 断言含**两条** CREATE TABLE——缺陷态仅一条/关联 b 失败红→ready set 绿；单 collection 二次操作 DDL 仅一条防过修钉）；红② 丢文件红（临时目录 N 模块 × M 文件收集完整性断言 size==N*M 且无重复——缺陷态 parallel add 丢文件红[多 trial/变异探针]→函数式收集绿）；红③ 并发 upsert 红（两线程 latch 对齐同 id store，断言 search 同 id ≤1 条——缺陷态重复条目红→原子化绿；单线程 `updateReflectedInSearch` 补 `assertEquals(1, hits.size())` 强度钉同 diff）；红④ 错误折叠红（StubChat errorResponse 模式下 synthesize 断言抛 NopException——缺陷态返回 "" 红→isSuccess fail-loud 绿；合法空回答 "" 钉保持绿）。
- 联调纪律显式守约（本批七条）：**MA-01 三登记一处闭合**；**C1-003×ON CONFLICT 范式对齐**；**C3-005×错误以成功交付族对齐（R2-02/R2-06 族批声明，成员均 fixed）**；**C1-001×收集范式对齐（顺序安全入口零触碰）**；**C10 四条同 diff（E2）**；**nop-ai-api 契约零触碰（条件申报监控）**；**无跨批硬依赖（可并行）**。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-ai.md`（schemaReady per-collection 作用域契约句 + InMemoryVectorStore upsert 原子性契约句 + synthesize 错误传播契约句 + code-analyzer 收集契约句、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 G60 条化束成员**（IND-05/06/07/08/09，B1-12 终裁 deferred，触发条款=接线/接管线）——特别地 IND-06 sync-over-async 修复（透传 cancelToken/超时）不代做，其 C10-003 共享基建面（neverComplete 模式）登记 successor 搭车点。
- **不做 G61 dormant**（IND-04 ExtensionLanguageMapper static HashMap 公开 mutator，零调用者；触发条款=调用者接线即复核）；不做 C10-004 其余半边（C1-004/C1-007/C1-009 契约钉随各自本体）。
- 不做 C3-002（watch）、CL-006/007（C9 随机 id/无 delete-by-docId 线索）、CL-012（InMemoryVectorStore 无界账本线索，同站点不同缺陷面不互吸）、CL-013（=CL-002 合并建议）、4 张条件化 P2 裁决（留 7.1）；不做错误码映射治理本体（C6-001 站点 INVALID_ARGUMENT 误导面仅登记注记）。
- 不做平台层改码：nop-api-core/nop-commons/nop-core 平台设施零触碰（只消费惯例）；`io.nop.ai.api.chat.ChatResponse` 契约零触碰（条件申报除外）；`IVectorStore` SPI 零签名变更。
- 不触碰 `_` 前缀生成物；G60/G61/watch 批外成员本体；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-ai/nop-ai-rag/src/`：`io/nop/ai/rag/vector/`（PgVectorStore、InMemoryVectorStore）、`io/nop/ai/rag/synthesize/`（RagSynthesizeService）、rag 错误码登记面（模块内 Errors 常量）、`TestPgVectorStoreSql`/`TestInMemoryVectorStore`/`TestK3PipelineE2E`（rag 根）测试树。
- `nop-ai/nop-ai-code-analyzer/src/`：`io/nop/ai/code_analyzer/maven/`（JavaSourceFileFinder）main + 新增收集完整性测试。
- 设计文档：`ai-dev/design/concurrency/rag-vector-schema-scoping-and-synthesis-error-concurrency.md`（新建；裁定①–⑤+测试缝+零回退钉+ON CONFLICT/错误族对齐条款，最终状态写法；命名调整须登记裁定）。Owner doc：`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；G60/G61/watch/条件化 P2 批次工单；nop-api-core/nop-commons/nop-core 平台设施本体；`model/*.orm.xml` 与 `_` 前缀生成物（预设零触碰）；`ChatResponse`/`IVectorStore` 契约演进（条件申报除外）。

## Execution Plan

### Phase 1 - rag 向量库/工具收集/合成错误传播并发设计裁定文档 + 独立评审

Status: planned
Targets: `ai-dev/design/concurrency/rag-vector-schema-scoping-and-synthesis-error-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [ ] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [ ] **裁定① C6-001 schemaReady per-collection 化**：ready set 载体（`newKeySet` + add CAS[预设] vs `computeIfAbsent`）一次定夺；并发首建瞬态窗处置（幂等+异常捕获重试一次[预设] vs IResourceLockManager）一次定夺；五入口守卫面验证矩阵；C1-002/C3-004 吸收面闭环声明；错误码映射治理注记登记（不代做）
  - [ ] **裁定② C1-001 函数式收集**：per-module 局部 List + 终端合并（预设） vs 线程安全载体保形改造——一次定夺；`findJavaFilesInModule`/`findJavaFiles` 递归链适配面；顺序安全入口零触碰声明
  - [ ] **裁定③ C1-003 upsert 原子化**：bucket 载体 CHM<String,VectorData>（预设） vs per-bucket 锁——一次定夺；search 快照语义与 delete 行为保持声明；javadoc 线程安全声明校正；ON CONFLICT 范式对齐声明
  - [ ] **裁定④ C3-005 isSuccess fail-loud**：错误码 `ERR_AI_RAG_SYNTHESIZE_FAILED` 参数集（error/errorClassification/retryAfterMs）一次定夺；**判据边界**（仅 `!isSuccess()` 触发、合法空回答 "" 保留）成文；受控重试备选不预设声明；**条件申报（ChatResponse 契约演进）触发检查条款**
  - [ ] **裁定⑤ 测试缝与零回退钉清单**：红①–红④ 测试形态逐条（含缺陷态红判据 + 非 docker 可跑面约束[红①走 fake DataSource 基建]）；零回退钉 = `TestPgVectorStoreSql` 全部 9 钉（`newStore` :101/`ddlIsIdempotentWithRealTableName` :106）、`TestPgVectorSearchRowMapping`、`TestInMemoryVectorStore` 全部 10 钉（`updateReflectedInSearch` :84[补强度钉为唯一允许的同 diff 改写]）、`TestK3PipelineE2E`（rag 根，StubChat happy-path 钉）、`TestPgVectorIntegration`/`TestGraphRagIntegration`（环境门控面登记）、`TestNopAiRagBizModelAnnotations`、`TestTextEmbeddingWiring`/`TestAiModelTextEmbedding`/`TestAiTextEmbeddingVectorSearchE2E`/`TestCosineSimilarityAndRelevanceScore`、code-analyzer `MavenProjectTest`/`MavenModuleStructureTest`/`MavenDependencyTreeParserTest`；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑤全部项且每条有唯一预设默认分支，独立 draft review 记录在案（文件命名如调整已登记裁定）
- [ ] 裁定①含三登记一处闭合与既有 9 钉兼容核对表；裁定③含 ON CONFLICT 范式对齐与 10 钉零回退声明；裁定④含判据边界与条件申报检查
- [ ] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 4 成员实现 + 回归测试（按子域分组）

Status: planned
Targets: `nop-ai/nop-ai-{rag,code-analyzer}/src/`（各自 main + test 树）

- Item Types: `Fix`（4 项 P1 均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [ ] 组 A 向量库（nop-ai-rag vector）：C6-001（裁定①，三登记一处闭合 + C10-001 红①两钉）+ C1-003（裁定③，**ON CONFLICT 范式对齐** + C10-002 红③并发钉 + `updateReflectedInSearch` 强度钉同 diff 改写[唯一允许的既有钉改写，语义对照留痕]）
- [ ] 组 B 工具收集（nop-ai-code-analyzer maven）：C1-001（裁定②，C10-004 IND-01 半边红②收集完整性钉）
- [ ] 组 C 合成错误传播（nop-ai-rag synthesize）：C3-005（裁定④，C10-003 主半边红④——StubChat errorResponse 模式 + 合法空回答钉；条件申报触发检查留痕）
- [ ] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①–红④快照落 `_tmp/` 并登记；**变异探针纪律**：红②/红③触碰收集/容器路径，变异恢复动作必须以断言方式确认目标可执行行真实存在（grep/读取校验，防恢复半写入假绿——R5 教训）；红③并发钉设 trial 上界防负载噪声化
- [ ] **端到端验证**（rule 22）：≥1 条 rag 存取链场景（ingest→store 双 collection→search 两集合均命中，覆盖裁定①守卫面贯通）；≥1 条合成链场景（search→synthesize，StubChat error→fail-loud 异常携带错误码、StubChat success 空 output→"" 合法返回，覆盖裁定④正反两面）——C1-003/C1-001 为单点修复，由红③/红②并发/完整性钉承载（单点修复无链式入口，免端到端义务显式声明）
- [ ] **接线验证**（rule 23）：裁定① ensureSchema 三物理守卫点（store/delete/search，update 经委托）真实走 per-collection 守卫断言（capturedSql 证据）；裁定④ synthesize 真实消费 isSuccess 断言（error 响应必抛，非仅方法存在）
- [ ] **无静默跳过**（rule 24）：裁定①并发 DDL 异常重试上界显式（禁静默吞）；裁定②收集结果完整性断言（禁丢弃分支）；裁定④错误响应必抛（现状即静默折叠缺陷面反转）；新增公共分支无空方法体/TODO

Exit Criteria:

- [ ] 4 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 收口门（4 条成员面，含两条半边口径）同 diff 落地
- [ ] 必红证据（E2）：红①–红④均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [ ] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；唯一允许的钉改写 = `updateReflectedInSearch` 强度钉补 assert[语义对照留痕]，登记在案）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选（C1-001/C1-003 单点免端到端义务声明在案）
- [ ] `./mvnw test -pl nop-ai -am` BUILD SUCCESS；上游环境抖动按勘误 8 三联裁定处理并留痕；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-ai 聚合全量测试；`docs-for-ai/03-modules/nop-ai.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2519 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-ai -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径；nop-ai-rag 与 nop-ai-code-analyzer 两模块触达分别登记）；环境抖动按三联裁定留痕（勘误 8 三面重点核对）；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `_` 前缀生成物手改、不含 `model/*.orm.xml`、不含 `io.nop.ai.api.chat.ChatResponse`（契约归属零触碰[勘误 7]）、不含 nop-api-core/nop-commons/nop-core 平台设施、不含 G60/G61/watch 批外成员本体、既有钉面零 diff 核对登记
- [ ] Owner doc 更新：`nop-ai.md`——schemaReady per-collection 作用域契约句（:197 向量库后端节内）+ InMemoryVectorStore upsert 原子性契约句 + synthesize 错误传播契约句（:199 管线节内）+ code-analyzer 收集契约句 + §源码锚点补行；`source-anchors.md` 新增锚点（按实际）；check-doc-links --strict 退出码 0
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0（批前既有发现逐条裁定登记[2518/2519 先例口径]；code-analyzer 面按触达登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-ai.md owner 面 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（生成物/orm 模型/平台设施/ChatResponse 契约/G60/G61 批外成员本体/既有钉面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.26 行、cra-index P1 台账 :262–265 + C10 成员面注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（per-collection 守卫真实生效/收集完整性/upsert 原子性/isSuccess fail-loud 运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-17 四行状态收敛（open→fixed，注记 plan 2520；吸收成员[←C1-002/C3-004/CL-010/CL-008]随各主行括注）+ C10 成员面收敛注记（4 条按勘误 6 半边口径）；roadmap 8.26 行 Status → done；G60 条化束 C10-003 共享基建搭车点注记（successor 指向）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 四行收敛 + 吸收成员括注 + C10 半边注记、roadmap 8.26 = done、G60 搭车点注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 4 项成员全部修复：C6-001（schemaReady per-collection 化，三登记一处闭合）、C1-001（parallelStream 函数式收集）、C1-003（upsert 原子化，ON CONFLICT 范式）、C3-005（isSuccess fail-loud，错误以成功交付族闭合）
- [ ] batch-proposal R2-17 行 exit criteria 增补逐条达成并留证：schemaReady per-collection 化（三登记一处闭合）；parallelStream 并发容器；upsert 原子化（ON CONFLICT 范式）；isSuccess 检查（IND-03 升格面闭合）
- [ ] 对齐纪律守约：MA-01 三登记一处闭合兑现；ON CONFLICT 范式对齐兑现；错误以成功交付族对齐（R2-02/R2-06 族批声明）兑现；C10 四条同 diff 兑现；PA 条款闭环（零 orm/零生成物/零 nop-ai-api 触碰声明兑现或条件申报面落地）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红④均有先红后绿或变异/回退论证（E2）；C10 收口门（4 条成员面）同 diff 落地
- [ ] 设计文档裁定①–⑤成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证（唯一允许改写 = `updateReflectedInSearch` 强度钉补 assert，语义对照留痕）
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-ai -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-ai.md（向量库/管线 owner 面）+ source-anchors.md 按实际（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）per-collection 守卫/收集完整性/upsert 原子性/isSuccess 检查运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-ai 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index R2-17 四行收敛 + roadmap 8.26 → done（E6）

## Deferred But Adjudicated

### G60 条化束成员（IND-05/06/07/08/09，含 C10-003 neverComplete 共享面与 C10-004 其余半边）

- Classification: `out-of-scope improvement`（batch-proposal/G60 行 B1-12 终裁 deferred，条件化激活条款=接线/接管线，非本批降级）；Successor Required: `yes`；Successor Path: G60 条化束（触发条款所在批，roadmap 8.5x）
- Why Not Blocking Closure: 判级对齐零重复——cra-index G60 行批次列=deferred 且触发条款明文；本批已收敛 3.4 单元全部 open P1（4 张），下游实施以本批后的 live 形态为基线，且可引用本批 StubChat errorResponse 基建（C10-003 主半边）为既有裁定不重开体。

### G61 dormant（IND-04）与 watch（C3-002）、CL 线索（CL-006/007/012/013）、4 张条件化 P2（留 7.1）

- Classification: `watch-only residual`（批前裁定：dormant 零调用者/watch 级/线索移交各 C 维度/条件化裁决批前留档）；Successor Required: `yes`（触发条款各本体）；Successor Path: G61 触发条款/C9 线索轨/C5-C7 线索轨/7.1 裁决
- Why Not Blocking Closure: 批前裁定批外面；本批裁定③对 CL-012（同站点无界账本）已登记不互吸接缝，实施时消费不重建。

## Non-Blocking Follow-ups

- **错误码映射治理注记**：PgVectorStore SQLException 一律映射 ERR_AI_CORE_INVALID_ARGUMENT 的误导面（C6-001 站点伴生）——独立治理项，本批只登记注记。
- **G60 对接注记**：本批落地后向 G60 条化束作 C10-003 共享基建（StubChat errorResponse 模式）搭车点注记。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed: —

Closure Audit Evidence:

- Reviewer / Agent: （待独立 closure audit）
- Evidence: （待填）

Follow-up:

- （待填；或明确写 no remaining plan-owned work）

## Risks And Rollback

- **裁定①守卫粒度迁移的行为面风险**：ready set 化后「首个 collection 独占 DDL」行为改变为逐集合惰性建表——多集合部署从全红转全绿为修复目标，但单集合部署的 DDL 时序（首请求承担建表延迟）不变；防过修钉（单 collection 二次操作仅一条 DDL）同 diff 落地；回滚 = ready set 行 revert 单点可逆。
- **裁定③载体重写的语义风险**：COWAL→CHM 结构重写可能破坏 tenant 隔离/topK/fail-loud 既有语义——`TestInMemoryVectorStore` 10 钉零回退 + 强度钉同 diff；search 迭代顺序变化（CHM 无序 vs COWAL 插入序）对 topK 排序的影响须在设计中核对（排序面有显式 score 排序则无影响，否则登记）。
- **裁定④fail-loud 的调用方影响风险**：synthesize 从「永远 200」变为错误抛异常，既有消费方（前端/上游 agent）错误处理路径首次被激活——GraphQL 错误面语义与 3.1-C7-002 先例对齐；合法空回答 "" 边界钉防过修；回滚 = 判据行 revert 单点可逆。
- **红②并发红的可复现性风险**：parallelStream 丢文件为竞态红（多核+多模块近必然但非确定）——红证采用「缺陷态多 trial + 变异探针（删 add/限容）」双保险，变异探针纪律适用（恢复断言目标行存在）；修复后收集完整性钉为确定性绿。
- **上游链环境噪声风险**：`./mvnw test -pl nop-ai -am` 触达上游多模块，nop-core 负载 flake/surefire fork EOF/recovery CME 三面有先例（勘误 8）——三联裁定执行并留痕；本批红③并发钉自带负载面须设 trial 上界防噪声化。
