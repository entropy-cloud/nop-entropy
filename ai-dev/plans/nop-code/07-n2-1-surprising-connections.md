# 07 N2.1 意外连接分析(Surprising Connections)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N2.1;`graph-discovery-and-export-design.md` §3.1(评分契约/降级原则/GraphQL 契约);N1.1-N1.3 交付面
> Related: N1.1(CodeRelationGraph typed 边视图——本项消费方)、N1.2/N1.3(物化社区/枢纽读数)
> Draft Review: R1(agent_8023bd77,5 Major + 7 Minor,无 Blocker)全修订:int 截断/sem_sim 大写存储值读取路径/confidence 缺失=0 不默认/逐边社区 miss 规则/夹具需增改 INHERITANCE 边/休眠槽位/自愈门控区分/topLevelDir 首段/度数口径不复用 loadHubs/tie-break 回写/§3.1 陈旧句修正/minScore 缺省 1;每查询全量加载裁定录入 Deferred

## Purpose

在 typed 边集上按复合惊奇评分找出"非显而易见"的连接并给出可解释原因,经 `NopCodeIndex__getSurprisingConnections` GraphQL 查询暴露——消除 graph-discovery §3.0 登记的能力缺口(意外连接发现)。

## Current Baseline

- **数据面已就绪**(N1.1-N1.3):`CodeRelationGraph`(四族 typed 边,attrs 含 relationType/confidence/provenance/directed/sourceFilePath/targetFilePath)+ `CodeRelationGraphLoader.load`(4 表分页加载)+ `GraphMetricStore`(物化社区映射 loadCommunities/HUB 度数 loadHubs)+ 自愈物化路径(materializeGraphMetrics)。
- **评分契约**(§3.1,运算顺序固定;**值口径已按 live 钉死**):
  - score 全程 **int**;`semantically_similar_to` 乘法为 `(int)(score * 1.5)` 截断;DTO.score=Integer;minScore=@Optional Integer(缺省视为 1——零信号边(score≤0)不入结果,与"无信号⇒空列表"一致)。
  - confBonus 读 **attrs.confidence**:AMBIGUOUS=3/INFERRED=2/EXTRACTED=1;**attr 缺失 ⇒ confBonus=0 且不写 reason,绝不默认 EXTRACTED**(loader 对 provenance null/未知 confidence 省略该 attr,生产路径真实存在)。
  - 跨文件类型维度 +2:当前单源代码语料无文档节点,恒跳过(§3.0 裁定;SurpriseConfig 保留休眠槽位)。
  - 跨顶层目录 +2:topLevelDir=filePath 首段;attrs 缺 filePath ⇒ 维度跳过。
  - 跨社区 +1:**仅当两端都在社区映射中且 communityId 不等;任一端 miss ⇒ 跳过该维度(无 reason,不得用哨兵值凑跨社区信号)**——物化 COMMUNITY 行只覆盖 call-graph 节点集,四族边端点大量 miss。
  - 相似乘法:**读 attrs.relationType == "SEMANTICALLY_SIMILAR_TO"(大写存储值),不读 Edge.getType()(那是边族 SEMANTIC)**;DTO.relation = attrs.relationType,缺细分回退族名。
  - 边缘→枢纽 +1:min(deg)≤2 && max(deg)≥5;度数=投影去重后边集的 in+out,directed=false 的 semantic 单行每端点计 1;**不得复用 loadHubs(call-graph 单族口径,不同域)**。
  - reasons[] 记录每个命中维度;排序稳定(score 降序,同分按 source+target symbolId 字典序——对 §3.1"同分按 symbolId"的细化,Phase 3 回写 §3.1)。
- **降级原则**(§3.0):社区映射缺失(未物化)⇒ 自愈物化一次后重读;已物化但无 COMMUNITY 行(GRAPH_SUMMARY 存在=图过小)⇒ 直接空 map 降级,不重复物化;**逐边社区 lookup miss ⇒ 跳过跨社区维度**;边 attrs 缺 filePath ⇒ 跳过跨目录维度;confidence attr 缺失 ⇒ confBonus=0;无信号 ⇒ 空列表。§3.1 的排除规则(imports/contains、文件级 hub、概念节点)对四族代码边语料空转(四族均非 imports/contains)。
- **GraphQL 契约**:`getSurprisingConnections(indexId, topN=20, minScore=?)` → `List<SurprisingConnectionDTO>`(sourceSymbolId/targetSymbolId/sourceLabel/targetLabel/sourceFilePath/targetFilePath/relation/confidence/score/reasons[]);鉴权 `NopCodeIndex:query`。
- `NopCodeIndexBizModel` 为保留层手写 action(detectFlows 等先例);新增 ICodeIndexService 方法需同步 invariant `QUERY_METHODS`/表完备门禁(N1.3 先例)。

## Goals

- `SurprisingConnectionDTO`(@DataBean,nop-code-api dto 包,字段如上)。
- `SurpriseConfig`(可配权重:confBonus 映射/跨文件类型(休眠槽位)/跨目录/跨社区/相似乘数/边缘-枢纽,默认值=§3.1 启发式初值)。
- `ISurprisingConnectionAnalyzer` + `SurprisingConnectionAnalyzer`(service/graph 包,纯计算类:输入 CodeRelationGraph+社区映射+符号名解析函数,输出按分排序的 DTO 列表;O(E))。
- `CodeGraphService.getSurprisingConnections(indexId, topN, minScore)` + `ICodeIndexService` 透传 + `NopCodeIndexBizModel` @BizQuery action;社区映射物化优先、缺失时自愈物化(N1.3 模式);符号 label 分块 IN 查询(N1.3 的 loadSymbolsByIds 复用)。
- invariant 门禁同步:`QUERY_METHODS` 增 `getSurprisingConnections`。
- owner docs:query-api-design §4.2 [目标] 行转已实现、graph-discovery §3.0/§3.1 状态、缺口矩阵 N2.1 行。

## Non-Goals

- 不实现跨文件类型维度(无文档/图片节点,恒跳过——§3.0 裁定保留)。
- 不做 LLM 驱动的问题生成/解释(§四否决)。
- 不迁结构查询;不动 CodeCacheManager。

## Scope

### In Scope

- nop-code-api:`SurprisingConnectionDTO`
- nop-code-service:`ISurprisingConnectionAnalyzer`/`SurprisingConnectionAnalyzer`/`SurpriseConfig`、`CodeGraphService`/`ICodeIndexService`/`NopCodeIndexBizModel` 接线、invariant QUERY_METHODS 同步
- 测试:`TestSurprisingConnections`(localDb:评分各维度/排序稳定性/minScore 过滤/降级路径/GraphQL 端到端)
- owner docs ×3

### Out Of Scope

- N2.2 问题生成、N2.3 Wiki、其它查询。

## Execution Plan

### Phase 1 - 分析器与评分

Status: completed
Targets: `nop-code-api/dto/SurprisingConnectionDTO`、`service/graph/ISurprisingConnectionAnalyzer`+`SurprisingConnectionAnalyzer`+`SurpriseConfig`

- Item Types: `Fix`

- [x] DTO(@DataBean,10 字段)
- [x] `SurpriseConfig`(权重可配 + 默认值=§3.1 初值,含休眠的跨文件类型槽位——前向兼容文档节点模型)+ `SurprisingConnectionAnalyzer.analyze(graph, communities, nameResolver, topN, minScore)`:遍历边→逐维度评分(顺序固定)→reasons 收集→过滤 minScore(缺省 1)→排序(score desc,同分 source+target 字典序)→截断 topN;度数由边集直接统计(in+out)
- [x] 单测 `TestSurprisingConnectionAnalyzer`:手工构造 CodeEdgeData 集合逐维度断言(confBonus 四档:三枚举+attr 缺失=0/跨目录/跨社区(含端点 miss 不计分)/相似乘法 `(int)(score*1.5)` 截断(加性 3→4)与顺序(先加后乘再加枢纽)/边缘-枢纽阈值边界 2 与 5/排序稳定性/minScore 过滤/社区缺失降级/空图空列表)

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 单测全绿,覆盖逐维度断言与乘法顺序(顺序错误会产出不同分值)
- [x] **无静默跳过**:minScore/topN 非法值(负数等)显式归一或抛错,不静默
- [x] `./mvnw test -pl nop-code/nop-code-service -am -Dtest=TestSurprisingConnectionAnalyzer` 全绿
- [x] Owner-doc:延后 Phase 2
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 接线与 GraphQL 暴露

Status: completed
Targets: `CodeGraphService`/`ICodeIndexService`/`NopCodeIndexBizModel`/invariant 测试

- Item Types: `Fix`

- [x] `CodeGraphService.getSurprisingConnections`:load 4 表(CodeRelationGraphLoader)→ 社区映射(GraphMetricStore;GRAPH_SUMMARY 缺失→materializeGraphMetrics 自愈一次后重读;GRAPH_SUMMARY 在但 COMMUNITY 缺——图过小→直接空 map 降级,不重复物化)→ 符号名分块解析(复用 loadSymbolsByIds)→ analyzer → DTO(label=qualifiedName)
- [x] `ICodeIndexService` 透传 + invariant `QUERY_METHODS` 同步(防表完备门禁红)
- [x] `NopCodeIndexBizModel.getSurprisingConnections` @BizQuery + `@Auth(permissions = "NopCodeIndex:query")`,topN 默认 20/minScore 可空
- [x] 集成测试 `TestSurprisingConnections`(localDb,基于 App/Greeter/NameUtil 夹具**增改产出 INHERITANCE 边**:现有三文件无 extends——如 `GreeterService extends NameUtil` 以获得可解析继承边;存在外部 superType(如 java.lang.Object)残留 qualifiedName 形态 id 的边,label 回退原始 id(qualifiedNameOf 先例)、跨目录/跨社区维度按 miss 跳过):triggerFullIndex → GraphQL 查询 → 断言返回边含 relation/confidence/filePaths、score≥minScore 且排序正确、reasons 非空;minScore 过滤;未物化 index 首查自愈

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 集成测试全绿含 GraphQL 端到端
- [x] **接线验证**:GraphQL mutation 真实到达 analyzer(端到端断言)
- [x] invariant 门禁全绿(QUERY_METHODS 同步后 table-completeness 通过)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] Owner-doc:延后 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner docs 同步与收口

Status: completed
Targets: `query-api-design.md`、`graph-discovery-and-export-design.md`、缺口矩阵

- Item Types: `Fix | Proof`

- [x] query-api-design §4.2:`getSurprisingConnections` [目标] → 已实现(契约行)
- [x] graph-discovery §3.1 状态行更新(已实现;跨文件类型维度恒跳过保留说明);回写 tie-break 细化(同分 source+target 字典序);修正"不适用维度"陈旧句(置信度维度已可用——N1.1 投影;跨目录单项目内可判)
- [x] 缺口矩阵 N2.1 行更新
- [x] `check-doc-links --strict` exit 0

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] owner docs 与 live 一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 分析器与 GraphQL 暴露落地且有测试钉住(逐维度评分/排序/降级)
- [x] 必要 focused verification 完成(单测 9/9 + 集成 2/2 + service 回归)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_9fd5f6a1,2026-09-27,APPROVE 有条件——收尾三件事已执行)
- [x] **Anti-Hollow Check**:closure audit 验证运行时调用链全连通(GraphQL→BizModel→ICodeIndexService→CodeGraphService→Loader+loadCommunities→analyzer);reasons 可观测(逐维度前缀断言);降级路径真实可达(空社区/端点 miss/未物化自愈)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### relationGraph 查询路径缓存

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: N1.3 已裁定"CodeRelationGraph 进查询路径"随结构查询移交 N6.2;新增 relationGraph 缓存需扩 AnalysisCache 并接入 N1.4 失效链路,扩 scope 不值。每查询 lazy 全量 4 表加载一次可接受(analyzer 单次消费)。自愈门控区分"未物化(materialize)"与"已物化但图过小(GRAPH_SUMMARY 存在 ⇒ 空 map 降级)",防小图每次查询重复物化。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N6.2

## Non-Blocking Follow-ups

(无)

## Closure

Status Note: N2.1 交付物(DTO/配置/分析器/接线/GraphQL 暴露)全部落地并经独立审计确认;审计的有条件收尾(提交/补勾/Closure 记录)已全部执行。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_9fd5f6a1(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_9fd5f6a1-6b07-40d1-ba7f-4b46527e04ee
- Evidence:
  - 代码真实性 PASS:固定顺序/int 截断/attrs.relationType 大写/缺失 confidence=0/社区 miss 跳过/两遍扫描度数/自愈门控/QUERY_METHODS 同步——逐行核实
  - 测试真实性 PASS:单测 9/9(截断 3→4/阈值边界/tie-break/端点 miss)+ 集成 2/2(真调 graphQLEngine 端到端);auditor 独立重跑 BUILD SUCCESS
  - 一致性 PASS:七处口径一致(plan/矩阵 done/roadmap todo→后翻转/daily log)
  - 工具门禁:doc-links 0 errors / scan-hollow 0 findings / check-plan-checklist exit 0
  - Anti-Hollow PASS:端到端连通/reasons 可观测/降级可达
  - 审计条件执行:①全部变更提交 ②Phase 1 logs 勾选 ③本 Closure 记录
  - 审计裁定:APPROVE(2026-09-27)

Follow-up:

- no remaining plan-owned work
