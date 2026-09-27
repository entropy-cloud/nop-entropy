# 05 N1.3 查询路径去全量 rebuild(全局算法查询物化优先)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N1.3;`01-architecture-baseline.md` §6.2;`graph-discovery-and-export-design.md` §3.0;live 调查(2026-09-27,见 Current Baseline)
> Related: N1.2(物化面)/N1.4(缓存语义收口)/N1.1(typed 边视图,结构查询保留原因)
> Draft Review: R1(agent_3611cf46,2 Blocker + 3 Major + 7 Minor)全修订:计算一次硬约束(Leiden 无种子 Random)/裸 indexDirectory 纳入失效/symbolCount 双口径/按方法必需族判定+按族降级/占位符约定/守卫复现/rankNo 排序/null 契约不变量/owner-doc 漏项/映射声明/dict value 怪癖登记

## Purpose

全局算法查询(社区/关键节点/图分析)从"每次查询重算 Leiden/介数/入口点"迁移为**物化优先读取**(N1.2 的 `nop_code_graph_metric`),查询路径不再必然触发全量 rebuild;结构查询(需要边集本身)保留 lazy 视图至 N6.2 数据库 IGraph 后端落地。

## Current Baseline

> 2026-09-27 live 调查(行号为起草快照,以符号锚定)。

- **查询面分层**(`CodeGraphService`,17 方法):全局算法查询 3 个纯物化可替代——`detectCommunities`(L80)/`getCriticalNodes`(L168)/`getGraphAnalysis`(L92);结构查询(需要边集,保留 lazy)——`exportGraph`/`diffGraph`/`getKnowledgeGaps`(社区分量可物化但分析器需图)/`getImpactAnalysis`/`getCallHierarchy`/hierarchy 族/deps 族;flow/change/deadcode 3 处在 `CodeIndexService` 内部(L1737/L1810/L1832 附近)。
- **物化缺口**(现 4 族行无法 1:1 重建 DTO):① `CommunityDTO.cohesion`/`averageCohesion`/`modularity`/`algorithmUsed` 无列;② hub 度数族(`CriticalNodeScoreDTO.in/out/totalDegree`、`GodNodeDTO.degree/callerCount/calleeCount`、cohesionBreakdown 的 extracted/inferred 计数)无对应 metric 族;③ `processingTimeMs` 对物化读取无意义。
- **lazy rebuild 依赖**:`TestNopCodeAnalysisBizModel`/`TestGraphAnalysisE2E`/`TestPhase1BugFixes`/`TestIndexNopEntropyProject`/`TestNopCodeHierarchyQueries`/`TestDependencyPersistence`/`TestFlowAnalysisE2E`/`TestNopCodeFlowBizModel` 全部 `indexDirectory` 后直接查询(不物化)——物化优先+自愈回退可保持全绿;若改为无回退只读则全红。
- **invariant 门禁**:`TestNopCodeIndexIdempotencyInvariant.testTableCompletenessGate` 对 `ICodeIndexService` public 方法穷举四表归位(QUERY_METHODS L118-131 枚举图查询方法名)——本 plan 不增删接口方法,门禁不受影响;`TestTarjanSccCycles` 反射钉住 `CodeGraphService.tarjanSCC` 私有签名(不动 deps 族则无碍)。
- **API**:计数用 `IEntityDao.countByQuery`;`CodeGraphService` package-private、由 `ensureSubServices()` 构造;`GraphMetricStore`(public,零计算)现仅测试调用。
- N1.2 已建立:物化管线(`GraphMetricMaterializer`,四族:COMMUNITY/BETWEENNESS/PAGE_RANK/ENTRY_POINT)、只读面(`GraphMetricStore`)、`triggerFullIndex` 端到端物化。

## Goals

- **物化族扩展**(orm dict + materializer):新增 3 个 metric 族——`HUB`(每符号行,score=totalDegree,extData 持 inDeg/outDeg)、`COMMUNITY_INFO`(每社区行,communityId,score=cohesion)、`GRAPH_SUMMARY`(每 index 单行,extData 持 modularity/averageCohesion/algorithmUsed/symbolCount)。
- **三个全局算法查询迁移为物化优先 + 自愈回退**:`detectCommunities`/`getCriticalNodes`/`getGraphAnalysis` 先读物化(齐备则装配 DTO,符号名/qualifiedName/dominantPackage 经 NopCodeSymbol 分块 IN 查询解析,不全量加载符号表);任一**该方法必需族**缺失 → **计算一次并物化 → 走同一物化装配路径返回读取结果**。**计算一次是硬约束**:`LeidenDetector` 内部 `new Random()` 无种子,两次独立计算结果可能不同——返回值必须等于落库值,否则首次查询(算)与二次查询(读)不一致(TestPhase1BugFixes 偶发红)。生产 full index 后查询恒读物化。
- **行为迁移证明**:物化行被人工改动后,查询返回改动值(证明读取自物化而非重算)。
- 结构查询不动(边集视图 lazy 保留);`processingTimeMs` 物化读取时为 0(DTO 语义收缩,owner doc 记录)。
- 全部既有测试保持绿(自愈回退保证 indexDirectory→查询 语义不变)。

## Non-Goals

- 不迁结构查询(exportGraph/diffGraph/getKnowledgeGaps/impact/hierarchy/deps/flow 族)——N6.2 DB IGraph 后端落地后再收敛。
- 不增删/改名 `ICodeIndexService` public 方法(invariant 门禁稳定)。
- 不动 CodeCacheManager 结构与失效策略(N1.4);不实现 deps 族物化。
- 不改 GraphQL 契约(DTO 字段不变,processingTimeMs 语义收缩除外)。

## Scope

### In Scope

- `nop-code/model/nop-code.orm.xml`(dict 增 3 选项)+ 生成物(dict yaml/constants)
- `GraphMetricMaterializer`(3 个新族行构建)+ `GraphMetricStore`(读扩展:loadHubs/loadCommunityInfo/loadSummary/hasAllFamilies)
- `CodeGraphService` 3 个方法迁移 + 符号名分块解析辅助
- 测试:迁移行为测试(物化优先/自愈回退/改动可读出)+ 既有全绿
- owner docs:baseline §6.2/§4.4.1 查询路径行、graph-discovery §3.0、缺口矩阵

### Out Of Scope

- 结构查询迁移、deps 物化、CodeCacheManager 改造、GraphQL 契约变更、CodeRelationGraph 进查询路径。

## Execution Plan

### Phase 1 - 物化族扩展

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`、`GraphMetricMaterializer`、`GraphMetricStore`

- Item Types: `Fix`

- [x] dict `code/metric_type` 增 HUB(50)/COMMUNITY_INFO(60)/GRAPH_SUMMARY(70);重新生成 dict 生成物
- [x] materializer 增 3 个行构建:hubRows(全 call-graph 节点,score=totalDegree,extData 含 inDegree/outDegree)、communityInfoRows(每社区一行,symbolId 占位符= community:<communityId>,score=cohesion)、summaryRow(symbolId 占位符= __summary__,extData 含 modularity/averageCohesion/algorithmUsed/callGraphNodeCount(图节点数)/symbolCount(全符号数,含孤立)双计数);随 materialize() 一起落库。占位符约定必须钉死:symbolId 列 mandatory + 唯一键 (indexId,metricType,symbolId),无占位则插入失败或社区行互相覆盖
- [x] GraphMetricStore 读扩展:loadHubs(indexId)(symbolId→(total,in,out))、loadCommunityInfo(indexId)(communityId→cohesion)、loadSummary(indexId)(extData 反序列化)、按方法必需族判定(替代全局 hasAllFamilies):detectCommunities 必需 {COMMUNITY, COMMUNITY_INFO, GRAPH_SUMMARY};getCriticalNodes 必需 {HUB, GRAPH_SUMMARY}(BETWEENNESS 缺席 ⇒ bridgeNodes 空,按族降级——>10000 大图的既定裁定,不得因此全局回退);getGraphAnalysis 必需 {ENTRY_POINT, HUB, GRAPH_SUMMARY}。每族一次 LIMIT 1 点查
- [x] 测试:物化后新族行存在且数值与 DTO 语义一致(cohesion∈[0,1]、totalDegree=in+out)
- [x] 空 index null 契约不变量:GRAPH_SUMMARY 单行不得翻转必需族判定(空/单节点图无 COMMUNITY/HUB 行 ⇒ 判定失败 ⇒ 回退,保持现 symbolTable.size()==0 → null 与 <2 节点廉价重算语义;<2 节点图永久回退可接受,注明即可)

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] dict 生成物再生成(_NopCodeDaoConstants/dict yaml)且测试断言 7 族行存在
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] Owner-doc:延后 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 全局算法查询迁移

Status: completed
Targets: `CodeGraphService`

- Item Types: `Fix`

- [x] 新增私有装配路径(物化读取→DTO):detectCommunities 从 COMMUNITY+COMMUNITY_INFO+GRAPH_SUMMARY 行装配(communityId 分组→CommunityDTO,totalSymbols=callGraphNodeCount;label/dominantPackage 分块查符号后派生);getCriticalNodes 从 HUB+GRAPH_SUMMARY 行装配(hubNodes,totalNodes=symbolCount 全符号口径;bridgeNodes=BETWEENNESS 行,缺席⇒空;symbolCount>10000 ⇒ hub/bridge 双空,复现现守卫语义);getGraphAnalysis 从 ENTRY_POINT+HUB+GRAPH_SUMMARY 行装配(isolated=ENTRY_POINT 行 entryPointType=ISOLATED,cohesionBreakdown extracted=HUB 行数、inferred=symbolCount-HUB 行数)
- [x] 三方法改为:必需族齐备? 装配返回(读侧按 rankNo 排序取 topN,不得按 score 重排——并列序以物化生成序为准) : 计算一次 → 物化 → 走同一装配路径返回读取结果(物化失败 warn 日志并返回计算结果,保持可观测)
- [x] B2 修复:裸 indexDirectory 重索引纳入失效——indexDirectory 完成后删除该 indexId 的 metric 行(与增量同模式),保证重索引后查询自愈重物化为新内容;补测试:full index → 查询(落行) → 修改源文件后重 indexDirectory → 查询结果反映新内容(旧行不得残留)
- [x] 符号名解析辅助:分块(每批 500)IN 查询 NopCodeSymbol 的 id/qualifiedName/kind(实体无 packageName 列,dominantPackage 由 qualifiedName 经既有 extractPackage 派生),零全量加载
- [x] 测试(新增):①物化优先——full index 后人工 UPDATE 一条 BETWEENNESS 行 score,查询返回改动值(行为迁移证明);②自愈回退——仅 indexDirectory 未物化时查询返回正确结果且随后物化行出现;③processingTimeMs=0 口径
- [x] 既有测试回归全绿(TestNopCodeAnalysisBizModel/TestGraphAnalysisE2E/TestPhase1BugFixes 等依赖 lazy 的用例经自愈回退保持语义)

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 行为迁移证明测试通过(改动物化行 → 查询返回改动值)
- [x] 自愈回退测试通过(未物化 index 查询正确且落物化行)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿(既有 lazy 依赖用例零修改)
- [x] **无静默跳过**:物化读取路径的装配失败不吞错(缺失族→回退计算;装配异常→回退计算并 warn)
- [x] Owner-doc:延后 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner docs 同步与收口

Status: completed
Targets: `01-architecture-baseline.md`、`graph-discovery-and-export-design.md`、缺口矩阵

- Item Types: `Fix | Proof`

- [x] baseline §6.2"查询路径无状态化"行更新:全局算法查询已物化优先(3 方法),结构查询保留 lazy 边集视图至 N6.2;§6.1 模块状态表查询路径无状态行同步(部分达成口径);§4.4.1 查询路径描述同步,改写"N1.3 必须消费 CodeRelationGraph"句(N1.3 全局算法查询不消费边视图,该指引移至 N6.2/N2.1)
- [x] graph-discovery §3.0 补查询读取口径(物化优先/自愈回退/processingTimeMs=0 语义)
- [x] 缺口矩阵 N1.3 行更新
- [x] `check-doc-links --strict` exit 0

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] owner docs 与 live 一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全局算法查询(3)行为迁移完成且有测试证明(改动物化行可读出)
- [x] 全部既有 lazy 依赖测试零修改回归全绿(closure audit 记录两处非 lazy 适配:TestTarjanSccCycles 构造器第三参适配、TestGraphMetricMaterialization +16 行断言增强,覆盖均保持/增强)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**:closure audit 验证(a)迁移方法在物化齐备时确实走读取路径(人工改动行被返回),(b)回退路径真实可达且自愈落库,(c)无空方法体/吞异常
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 结构查询迁移(exportGraph/diffGraph/getKnowledgeGaps/impact/hierarchy/deps/flow 族)

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 这些查询需要边集本身(非聚合度量),在 N6.2 数据库 IGraph 后端落地前任何迁移都是"换更贵的全量加载";roadmap N1.3 的"移除全量 rebuild 主路径"按全局算法查询(重算成本主体)收口,结构路径的 lazy 视图由 N1.4 缓存语义对齐、N6.2 后端收敛。结构查询的收敛经 N9.1 的硬依赖 N6.2 覆盖,移交不产生 N9 可达性缺口。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N6.2/N1.4
- roadmap 文本映射声明:"移除 CodeCacheManager 内全量 rebuild 主路径"交付为"全局算法查询不再 rebuild(物化优先),重建降级为自愈回退";"改用 N1.1 边视图"随结构查询整体移交 N6.2(全局算法查询不消费边视图)。缺口矩阵登记 done 时按此口径,不得过度声明。

## Non-Blocking Follow-ups

- processingTimeMs 物化读取恒 0——DTO 语义收缩,若 M9 验收发现消费者依赖该字段再评估(现无消费者)。
- 预存怪癖:metricType 列存 dict code 字符串("COMMUNITY")而 dict option value 为数字("10"),_NopCodeDaoConstants.METRIC_TYPE_* 与实际存储值不一致(display 翻译解析不到)——本 plan 沿用现有存储模式保持一致,治理归 N3.2 dict 扩展时统一。
- PAGE_RANK 族不参与 hasAllFamilies 判定(现无查询消费方)——N2.x 接入时再纳入。

## Closure

Status Note: N1.3 交付物落地:三个全局算法查询物化优先+自愈回退(计算一次,返回值=落库值),B2 重索引同事务失效,行为迁移证明(突变行原样返回)。closure audit 初裁 REJECT(窄口径:GodNodeDTO.kind 收缩未登记),已按放行条件修复(分块查询补 kind 列回填 + processingTimeMs=0 断言 + 两处文本修正)。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_e3103343(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_e3103343-82d5-482f-8bc9-c9cc46e37d5c
- Evidence:
  - 代码真实性 PASS:计算一次(Leiden 单次共享)/必需族判定/装配语义(totalSymbols 双口径、>10000 双空、按族降级、rankNo)/分块查询/B2 同事务失效——逐行核实
  - 测试真实性 PASS:TestMaterializedQueryMigration 3/3(突变 1234.5 原样返回=重算不可见);回归全绿且日志晚于全部源修改(验证当前代码);幂等门禁 8/8;接口零 diff
  - 审计修复项:①kind 契约恢复(loadSymbolsByIds 查 id/qualifiedName/kind,GodNodeDTO.kind 回填)②processingTimeMs=0 断言补入 ③两处 plan 措辞修正 ④程序性勾选
  - Deferred 诚实性:CodeRelationGraphLoader 全量加载语义核实,"换更贵的全量加载"成立;N9 可达性经 N6.2 硬依赖覆盖
  - 工具门禁:doc-links 0 errors / scan-hollow 0 findings / check-plan-checklist exit 0
  - 审计裁定:初裁 REJECT(窄口径,问题 #1)→ 修复后按审计预期直接放行

Follow-up:

- processingTimeMs 物化读取恒 0——已有测试断言钉住,语义收缩已入 owner doc
- 预存怪癖:metricType dict value 不一致(display 翻译)——归 N3.2
