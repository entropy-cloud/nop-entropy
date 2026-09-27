# 09 N2.3 图谱 Wiki 导出(Graph Wiki)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N2.3;`graph-discovery-and-export-design.md` §3.3(输出契约/slug 确定性/上限约束)
> Related: N1.1(typed 边视图)、N1.2/N1.3(物化社区)、N2.1/N2.2(同层先例)
> Draft Review: R1(agent_1130d605,8 Major,无 Blocker)全修订:index 字段语义裁定读法 (b)/枢纽阈值自有常量非跨类耦合/slug 截断 64+前缀+碰撞序号+保留字/index 列表仅含已导出项防断链/跨社区边归 source 端+审计轨迹定义/filePathResolver 入口/集成断言降结构级+枢纽触达移单测/全排序 tie-break/统计口径/非法归一/标签 dominantPackage;接线同构性经 R1 live 核实

## Purpose

将图结构渲染为互链 Markdown 文章集(目录+社区文章+枢纽文章),经 `NopCodeIndex__exportGraphWiki(indexId, maxCommunities?, maxHubNodes?)` 暴露——代码领域的文档化导出能力(非 Obsidian 双链)。

## Current Baseline

- 契约(§3.3,值口径已裁定):
  - `GraphWikiDTO.index` = **index.md 的正文内容**;`articles` 只含社区/枢纽文章(key 不含 index.md 本身)——裁定读法 (b),消除设计文档两处表述的歧义。
  - index.md = 统计(节点数=graph 节点集大小、边数=投影边行数)+ 已导出社区列表(按规模 desc、communityId asc)+ 已导出枢纽列表(按度 desc、symbolId asc);**被截断的社区/枢纽不列入 index.md**(防断链),列表标题注明总数与导出数。
  - 社区文章 = 成员按度 desc 排序为关键概念 + 跨社区关系(两端均在社区映射且 communityId 不等;列于 source 端社区文章;端点 miss 的边不呈现)+ 源文件清单(成员 filePath 去重,经 filePathResolver,缺失跳过)+ 置信度审计轨迹(每条列出边附 relationType+confidence)。
  - 枢纽文章 = **Wiki 自有阈值 HUB_MIN_DEGREE=5**(typed 图度数;不跨类引用 GraphQuestionGenerator 的 package-private 常量;且 §3.1 的 max(deg)≥5 是逐边判据不适用节点级),邻居按关系类型分组、每组内按 confidence 标记;external id(不在名称解析中)不具枢纽资格,邻居行回退原始 id。
  - slug:恒加前缀(社区恒 community-、枢纽恒 hub-,天然规避 index 保留字与跨组碰撞);标签=社区取 dominantPackage(成员 qualifiedName 多数派,经 extractPackage,回退 community_<id>)、枢纽取 qualifiedName;小写、非 [a-z0-9-] 连段替换为 -、截断 64 字符、**保留字 index 冲突时加 "community-"/"hub-" 类前缀天然规避,残余碰撞追加 -2/-3 序号**;文章排序确定(articles 为 LinkedHashMap)。
  - 上限:maxCommunities/maxHubNodes 默认 20,≤0 归一为 20(无仅 index 模式);cohesion 用于 index.md 社区行展示。
  - 非 Obsidian 双链、标准 Markdown 相对链接、不落盘(返回 DTO)。
- 数据面:N1.1 typed 边视图(CodeRelationGraphLoader,attrs relationType/confidence/filePaths)、N1.2/N1.3 物化社区(GraphMetricStore.loadCommunities/loadCommunityInfo)、N2.1 接线先例(BizModel/ICodeIndexService/invariant QUERY_METHODS/自愈门控)。
- `IGraphWikiExporter` 在 service/graph(与 KnowledgeGapAnalyzer 同层);N2.1/N2.2 的 analyzer 纯计算类先例。

## Goals

- `GraphWikiDTO`(@DataBean:index + articles Map)。
- `IGraphWikiExporter` + `GraphWikiExporter`(service/graph 纯计算:输入 typed 图+社区映射+cohesion+名称解析+config,输出 GraphWikiDTO;确定性:slug 规则固定、文章排序固定、上限截断)。
- `CodeGraphService.exportGraphWiki(indexId, maxCommunities, maxHubNodes)` + `ICodeIndexService` 透传 + BizModel @BizQuery(`NopCodeIndex:query`)+ invariant QUERY_METHODS 同步;自愈门控同 N2.1。
- 测试:单测(slug 确定性/互链格式/上限截断/社区与枢纽文章内容/跨社区关系与置信度标记)+ 集成(GraphQL 端到端)。
- owner docs:query-api-design §4.2 行、graph-discovery §3.3 状态、缺口矩阵 N2.3 行。

## Non-Goals

- 不用 Obsidian 双链语法(§四否决);不落盘文件系统(返回 DTO,消费方自行写文件);不改既有 GraphExporter(通用库,与代码领域 Wiki 分离)。

## Scope

### In Scope

- nop-code-api:`GraphWikiDTO`
- nop-code-service:`IGraphWikiExporter`/`GraphWikiExporter`、接线 ×3、invariant 同步
- 测试:单测 + 集成
- owner docs ×3

### Out Of Scope

- 文件系统写入、Obsidian 语法、通用 GraphExporter 扩展。

## Execution Plan

### Phase 1 - 导出器与单测

Status: completed
Targets: `nop-code-api/dto/GraphWikiDTO`、`service/graph/IGraphWikiExporter`+`GraphWikiExporter`

- Item Types: `Fix`

- [x] DTO(@DataBean:index String + articles Map<String,String>)
- [x] `GraphWikiExporter.exportWiki(graph, communities, cohesion, nameResolver, filePathResolver, config)`:两遍度数→index.md(统计+已导出社区/枢纽列表)→每社区一篇→每枢纽一篇(自有常量 HUB_MIN_DEGREE=5)→slug 确定性(前缀规避保留字+碰撞序号)→上限截断(≤0 归一 20)
- [x] 单测:slug 确定性+保留字 index+碰撞序号/index.md 统计与截断后列表(无断链)/社区文章互链(key 存在于 articles)+跨社区边归 source 端+置信度轨迹/枢纽文章邻居分组与 confidence 标记/上限截断/空图仅 index.md/两次导出逐字节一致

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 单测全绿(互链 key 必须存在于 articles)
- [x] **无静默跳过**:空图/无社区输入显式产出仅 index.md 的合法 Wiki
- [x] `./mvnw test -pl nop-code/nop-code-service -am -Dtest=TestGraphWikiExporter -Dsurefire.failIfNoSpecifiedTests=false` 全绿(裸 -Dtest 在上游模块报 no-matching-tests,需带 failIfNoSpecifiedTests——closure audit I4 更正)
- [x] Owner-doc:延后 Phase 2
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 接线与 GraphQL 暴露

Status: completed
Targets: `CodeGraphService`/`ICodeIndexService`/`NopCodeIndexBizModel`/invariant

- Item Types: `Fix`

- [x] `CodeGraphService.exportGraphWiki(indexId, maxCommunities, maxHubNodes)`:自愈门控(N2.1 同款)→ SymbolTable 名称解析 → loader 4 表 → 导出器;maxCommunities/maxHubNodes 默认 20
- [x] `ICodeIndexService` 透传 + invariant QUERY_METHODS 同步 + BizModel @BizQuery(`NopCodeIndex:query`)
- [x] 集成测试 `TestGraphWikiExport`(localDb,N2.1 夹具+extends):GraphQL 端到端——Wiki 含 index 正文、articles 互链自洽(key 均存在)、≥1 社区文章(图节点 ≥2 时 Leiden 必产);**枢纽文章触达由单测承载**(合成 hub 图),集成不断言枢纽文章存在
- [x] query-api-design §4.2 行转已实现

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 集成测试全绿含 GraphQL 端到端
- [x] **接线验证**:GraphQL 真实到达导出器
- [x] invariant 门禁全绿
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] Owner-doc:延后 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner docs 同步与收口

Status: completed
Targets: `query-api-design.md`、`graph-discovery-and-export-design.md`、缺口矩阵

- Item Types: `Fix | Proof`

- [x] graph-discovery §3.3 状态更新(已实现;slug 规则/枢纽阈值自有常量 5/默认上限 20/index 字段语义=正文);closure audit I1 修复:external id 不具枢纽资格已实现并加测试钉住
- [x] 缺口矩阵 N2.3 行更新
- [x] `check-doc-links --strict` exit 0

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] owner docs 与 live 一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 导出器与 GraphQL 暴露落地且有测试钉住(slug/互链/上限/内容)
- [x] 必要 focused verification 完成(单测 9/9 + 集成 1/1 + service 回归)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_e251b432,2026-09-27;初裁 REJECT——I1 external 枢纽资格未排除,修复+钉住测试后 delta 终裁 APPROVE)
- [x] **Anti-Hollow Check**:closure audit 验证(a)GraphQL→导出器→DTO 端到端真调 engine,(b)互链自洽由构造保证+截断不列未导出,(c)确定性双导出断言
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

(无)

## Non-Blocking Follow-ups

(无)

## Closure

Status Note: N2.3 交付物(DTO/导出器/接线/GraphQL 暴露)全部落地;closure audit 初裁 REJECT(I1 external 枢纽资格未按 plan 裁定排除),修复(枢纽筛选补名称解析过滤+钉住测试)后 delta 终裁 APPROVE。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_e251b432(独立 fresh-session closure auditor,2026-09-27;含 delta 复审)
- Audit Session: agent_e251b432-94d5-4498-9133-364d96f9a615
- Evidence:
  - 代码真实性 PASS:slug 前缀/截断 64/碰撞序号、index 只列已导出、跨社区边归 source 端、HUB_MIN_DEGREE=5 自有常量、置信度标记、确定性排序——逐行核实
  - I1 修复 PASS:枢纽筛选补名称解析过滤(label==id 即 miss→排除),testExternalIdDoesNotQualifyAsHub 钉住(external 度 5 不产枢纽文章);原回退测试保留
  - 测试真实性 PASS:单测 10/10 + 集成 1/1(auditor 独立实跑);全模块回归 209 tests/0 failures
  - 工具门禁:doc-links 0 errors / scan-hollow 0 findings / check-plan-checklist exit 0(auditor 复跑)
  - I3:query-api-design 状态句陈旧部分已更新(§4.2 探索/导出 API 全部落地)
  - 审计裁定:初裁 REJECT → 修复后 delta APPROVE(2026-09-27)

Follow-up:

- no remaining plan-owned work(I5 置信度轨迹措辞为非阻塞 nit)
