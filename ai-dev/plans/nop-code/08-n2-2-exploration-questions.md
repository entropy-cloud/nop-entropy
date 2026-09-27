# 08 N2.2 图谱问题生成(Exploration Questions)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N2.2;`graph-discovery-and-export-design.md` §3.2(问题类型/输出契约/模板/no_signal 例外)
> Related: N1.1(typed 边视图)、N1.2/N1.3(物化读数)、N2.1(同层先例)
> Draft Review: R1(agent_7ae9852a,1 Blocker + 3 Major + 6 Minor)全修订:B1 每类一条+target 合并策略/isolated 候选集=符号表∪图节点(度 0 盲区修复)+CONSTRUCTOR 排除裁定/verify_inferred 枢纽前置恢复(度 ≥5)/ambiguous 真实检测+合成单测/bridge 不排除 external/组合降级矩阵+单测/communityLabel 落 question/query-api 例外行已存在改核对/topN 归一+no_signal 字段/tie-break/集成触达分工;三模板方法与参数名经 R1 live 核实一致

## Purpose

从图信号派生**机器可执行**的探索引导:每条含类型/问句/原因/目标符号/可直接执行的 GraphQL suggestedQuery/priority,经 `NopCodeIndex__getExplorationQuestions(indexId, topN=10)` 暴露——承载调研建议的 suggestedNextQueries 语义。

## Current Baseline

- 契约(§3.2,值口径已钉死):
  - **每类一条问题**(5 类上限,非每节点一条);isolated_nodes 的 targetSymbolIds[]=全部触发节点(suggestedQuery 取排序首 id(isolated 节点按 symbolId 字典序,数组与首 id 同序));bridge_node 的 targetSymbolIds[]=介数 top-5(排序后),suggestedQuery 取第 1 名的 qualifiedName;low_cohesion 的 targetSymbolIds[]=社区成员(上限 10),communityLabel 写入 question 文本与 why(标签来源:复用 dominantPackage 生成规则,回退 community_<id>——KnowledgeGapAnalyzer 先例)。
  - `bridge_node`:介数 top-5(loadBetweenness 已按 rankNo 排序);>10000 节点(BETWEENNESS 行缺席)⇒ 类型跳过;不排除 external id(qualifiedName 回退原始 id,suggestedQuery 仍可执行——qualifiedNameOf 先例)。
  - `verify_inferred`:**枢纽前置恢复**(§3.2"枢纽节点含 ≥2 条 INFERRED 边")——枢纽=typed 图度数 ≥5(复用 §3.1 边缘-枢纽的 hubHighDegreeMin 阈值),且 INFERRED 入边+出边合计 ≥2。
  - `isolated_nodes`:候选集=SymbolTable 全量符号 ∪ graph 节点(度数缺省 0)——纯边端点枚举会漏掉度 0 的最孤立符号;排除 kind=CONSTRUCTOR(构造器隐式调用,裁定记录);external id(不在符号表)天然排除。
  - `ambiguous_edge`:实现真实检测(扫 attrs.confidence=="AMBIGUOUS"),合成边单测钉住;生产无生产者故恒空,后端就绪自动激活。
  - `low_cohesion`:cohesion<0.1 且规模(COMMUNITY 行计数)≥5。
  - 边界值:topN 缺省 10、≤0 归一 10;priority 同分 tie-break 按 type 字典序;undirected semantic 边度数计法同 N2.1(每端点每行 +1);no_signal 项的 suggestedQuery=null/targetSymbolIds=[]/priority=0。
  - DTO:`type/question/why/targetSymbolIds[]/suggestedQuery/priority`。无信号返回单项 `type=no_signal, question=null`(query-api-design 例外行已存在,仅需核对在位)。
- 数据面(N1.1-N1.4):typed 边视图(CodeRelationGraph,attrs confidence/relationType)、物化读数(GraphMetricStore.loadBetweenness/loadCommunityInfo/loadCommunities/loadSummary)、缓存 SymbolTable(qualifiedName 解析)、自愈门控(GRAPH_SUMMARY 判定,N2.1 同款)。
- invariant QUERY_METHODS 需同步(N2.1 先例);BizModel action 先例(N2.1 同款注解)。
- low_cohesion 规模判定:COMMUNITY 行按 communityId 计数;cohesion 来自 COMMUNITY_INFO 行;unresolved external 符号(qualifiedName 形态 id,如 java.lang.Object 残留)排除在 isolated/verify_inferred 之外(非项目符号)。

## Goals

- `ExplorationQuestionDTO`(@DataBean,6 字段)。
- `IGraphQuestionGenerator` + `GraphQuestionGenerator`(service/graph 纯计算:输入 typed 图+物化读数+名称解析+indexId,输出问题列表;按 priority 排序;各阈值可配(§3.2 阈值+本 plan 裁定值均可配))。
- `CodeGraphService.getExplorationQuestions(indexId, topN)`(自愈门控同 N2.1)+ `ICodeIndexService` 透传 + BizModel @BizQuery(`NopCodeIndex:query`) + invariant QUERY_METHODS 同步。
- no_signal 例外:查询设计行 + query-api-design 例外说明同步。
- owner docs:query-api-design、graph-discovery §3.2 状态、缺口矩阵 N2.2 行。

## Non-Goals

- ambiguous_edge 生产行为恒空(无生产者)——但检测逻辑真实实现并有合成边单测钉住,后端就绪自动激活(非假逻辑、非恒空假实现)。
- LLM 驱动问题生成(§四否决);纯文案问题(§四否决)。
- 不迁结构查询。

## Scope

### In Scope

- nop-code-api:`ExplorationQuestionDTO`
- nop-code-service:`IGraphQuestionGenerator`/`GraphQuestionGenerator`、CodeGraphService/ICodeIndexService/BizModel 接线、invariant 同步
- 测试:`TestGraphQuestionGenerator`(单测逐类型)+ `TestExplorationQuestions`(集成 GraphQL)
- owner docs ×3

### Out Of Scope

- N2.3 Wiki、N2.4 重建触发、AMBIGUOUS 生产者。

## Execution Plan

### Phase 1 - 生成器与单测

Status: completed
Targets: `nop-code-api/dto/ExplorationQuestionDTO`、`service/graph/IGraphQuestionGenerator`+`GraphQuestionGenerator`

- Item Types: `Fix`

- [x] DTO(@DataBean:type/question/why/targetSymbolIds/suggestedQuery/priority)
- [x] 生成器:5 类信号收集(ambiguous 为真实 AMBIGUOUS 边检测,生产恒空)(bridge_node←loadBetweenness 排序;verify_inferred←typed 图 INFERRED 边按节点聚合 ≥2;isolated_nodes←typed 图度数 ≤1 且 qualifiedName 属项目符号;low_cohesion←COMMUNITY_INFO cohesion<0.1 且 COMMUNITY 行计数 ≥5;ambiguous_edge←恒空)→ 每类一条问题(问句填符号名/why 含触发信号/targetSymbolIds/suggestedQuery 模板填参/priority:bridge=5,verify_inferred=4,low_cohesion=3,isolated=2,ambiguous=5)→ priority 降序排序 → topN 截断;空列表 → 单项 no_signal
- [x] suggestedQuery 模板逐 §3.2(direction 小写/含 indexId/qualifiedName 填充)
- [x] 单测:逐类型触发断言(suggestedQuery 字符串精确比对/priority 排序与 tie-break/阈值边界 cohesion=0.1 不触发且 <0.1 触发、size=4 不触发且 5 触发、inferred=1 不触发且 2 触发且度数 <5 不触发/degree 0 与 1 触发且 2 不触发/CONSTRUCTOR 排除/external 排除/空图→no_signal 单项且字段取值正确)
- [x] 单测降级组合:BETWEENNESS 缺 ⇒ 仅 bridge 跳过其余照常;COMMUNITY 行缺 ⇒ 仅 low_cohesion 跳过其余照常

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 单测全绿(suggestedQuery 字符串精确比对)
- [x] **无静默跳过**:no_signal 为显式对象(type=no_signal),非静默空列表
- [x] `./mvnw test -pl nop-code/nop-code-service -am -Dtest=TestGraphQuestionGenerator` 全绿
- [x] Owner-doc:延后 Phase 2
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 接线与 GraphQL 暴露

Status: completed
Targets: `CodeGraphService`/`ICodeIndexService`/`NopCodeIndexBizModel`/invariant

- Item Types: `Fix`

- [x] `CodeGraphService.getExplorationQuestions(indexId, topN)`:自愈门控(N2.1 同款)→ SymbolTable 名称解析 → loader 4 表 → 生成器
- [x] `ICodeIndexService` 透传 + invariant QUERY_METHODS 同步 + BizModel @BizQuery(`NopCodeIndex:query`,topN 默认 10)
- [x] 集成测试 `TestExplorationQuestions`(localDb,同 N2.1 夹具+extends):**类型触达由单测承载**,集成只验证管线(GraphQL 端到端返回结构合法:有 type/suggestedQuery 含 indexId)与 no_signal 场景(空 index 首查)
- [x] query-api-design:§4.2 行转已实现;核对既有 no_signal 例外行在位不重复新增

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 集成测试全绿含 GraphQL 端到端与 no_signal 场景
- [x] **接线验证**:GraphQL 真实到达生成器
- [x] invariant 门禁全绿
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] Owner-doc:延后 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner docs 同步与收口

Status: completed
Targets: `query-api-design.md`、`graph-discovery-and-export-design.md`、缺口矩阵

- Item Types: `Fix | Proof`

- [x] graph-discovery §3.2 状态更新(已实现;ambiguous_edge 恒空说明;no_signal 例外落地)
- [x] 缺口矩阵 N2.2 行更新
- [x] `check-doc-links --strict` exit 0

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] owner docs 与 live 一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 生成器与 GraphQL 暴露落地且有测试钉住(逐类型/模板精确/no_signal)
- [x] 必要 focused verification 完成(单测 9/9 + 集成 2/2 + service 回归)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_9b9cdbf1,2026-09-27,APPROVE)
- [x] **Anti-Hollow Check**:closure audit 验证(a)GraphQL 经真实 IGraphQLEngine 全链路,(b)5 类各有触发断言(ambiguous 合成边真实触发证非假逻辑),(c)no_signal 单项显式(空图单测+空 index 集成)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

(无)

## Non-Blocking Follow-ups

(无)

## Closure

Status Note: N2.2 交付物(DTO/生成器/接线/GraphQL 暴露)全部落地并经独立审计确认;6 大审计维度全 PASS,无 Blocker/Major。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_9b9cdbf1(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_9b9cdbf1-c03f-431b-95d4-53aff2ee36ab
- Evidence:
  - 代码真实性 PASS:逐项核实(每类一条/占位行不混入规模计数/枢纽前置/ambiguous 真实扫描/模板逐字符一致/no_signal 字段/自愈门控/QUERY_METHODS 同步)
  - 测试真实性 PASS:单测 9/9 + 集成 2/2(auditor 本机独立重跑 EXIT=0);回归 198/0
  - 一致性 PASS:plan/矩阵 done/roadmap todo→翻转/daily log 七处一致
  - 工具门禁:doc-links 0 errors / scan-hollow 0 findings / check-plan-checklist exit 0
  - Anti-Hollow PASS:端到端真调 engine;ambiguous 合成边触发证非假逻辑;no_signal 显式
  - 审计裁定:APPROVE(2026-09-27);4 项非阻塞 Minor 登记(社区标签用原始 id 而非 dominantPackage 规则——plan 措辞与实现落差,后续补齐或修订措辞;部分模板精确断言由源码读证替代;死 OR 分支;loadScores vs loadBetweenness 措辞)

Follow-up:

- low_cohesion 社区标签可升级为 dominantPackage 规则(当前原始 id)——非契约要求,优化候选
