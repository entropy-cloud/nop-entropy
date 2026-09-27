# 03 N1.1 IGraph 边属性投影增强(typed edges)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N1.1;`ai-dev/design/nop-code/graph-discovery-and-export-design.md` §3.0 前置3/前置4、§3.1 输入投影契约;`01-architecture-baseline.md` §4.4.1
> Related: N1.2/N1.3(消费方),N2.1/N2.2(下游评分/问题生成)
> Draft Review: R1(agent_3127c162,2 Major + 8 Minor)全修订;两项专项裁定(不改 CodeCallGraph 偏离成立/加载器暂无生产调用方不构成 hollow)支持核心决策

## Purpose

为 `IGraph` 提供**带领域属性的 typed 边视图**:relationType/confidence/provenance/sourceFilePath/targetFilePath 经 `Edge.attrs` 承载,覆盖 calls/inheritance/annotation/semantic 四族边——消除 graph-discovery 设计 §3.0 登记的前置3(边投影无属性)与前置4(INFERRED 边未投影)缺口,为 N2.1 惊奇评分与 N2.2 问题生成提供数据面。

## Current Baseline

- `CodeCallGraph implements IGraph`(`nop-code-core/.../core/graph/CodeCallGraph.java`)仅投影 `CallGraph` 的 CALLS 拓扑(Edge type="CALLS",weight=1.0,无 attrs)。
- `CallGraph` 内存结构(`Map<String,List<String>>`)是**有损**的——只有 callerId/calleeId,无元数据;富数据在 DB 实体:
  - `NopCodeCall`:callerId/calleeId/callType/provenance/fileId/line/context/metadata(**无 confidence 列**,provenance 表达 AST_EXTRACTION/HEURISTIC 等来源)
  - `NopCodeInheritance`:subTypeId/superTypeId/relationType(EXTENDS/IMPLEMENTS)/provenance
  - `NopCodeAnnotationUsage`:annotationTypeId/annotatedSymbolId/provenance/attributes
  - `NopCodeSemanticEdge`:sourceSymbolId/targetSymbolId/directed/relationType(语义关系)/confidence(EXTRACTED/INFERRED/AMBIGUOUS)/confidenceScore/rationale/extractorId/provenance
- 实体为 `DynamicOrmEntity` 生成类,测试中可无 DB 直接构造并 propSet。
- `Edge.attrs` 可变 Map(`nop-graph-api` Edge.java,equals/hashCode 不含 attrs——跨图集合运算安全)。
- `IGraph` 只有 `getOutEdges`/`getInEdges`,无节点枚举;算法消费方(CodeGraphService)显式传节点集。
- INFERRED 调用边现状:合成器(`InterfaceImplSynthesizer`/`SpringEventSynthesizer`)产出的 calls 持久化时 provenance=HEURISTIC(L892-902 CodeIndexService),confidence 不落库;故 calls 族 confidence 需由 provenance 映射(映射为设计可接受的信息保留方案,原始 provenance 同入 attrs 不丢失)。

## Goals

- 新 `CodeRelationGraph implements IGraph`(nop-code-core):四族 typed 边投影,Edge.type=边族(CALLS/INHERITANCE/ANNOTATION/SEMANTIC),Edge.attrs 承载 relationType/confidence/provenance/directed/sourceFilePath/targetFilePath(非 null 才放)。
- 新 `CodeEdgeData`(nop-code-core):typed 边数据载体。
- 新 `CodeRelationGraphLoader`(nop-code-service):批量(分页,对齐 CodeCacheManager 的 BATCH_SIZE 模式,无 MAX 上限——全局评分需完整边集,OOM 防护归 N1.2 物化与 invariant-loop 门禁)加载 4 表 → `List<CodeEdgeData>`。**attrs 值口径(钉死)**:attrs.confidence 一律取 `EdgeConfidence` 枚举名(semantic 族经显式 switch 10→EXTRACTED/20→INFERRED/30→AMBIGUOUS,其余值含 0/unknown 返回 null 并省略该属性——不复用 `EdgeConfidence.fromValue` 的静默 EXTRACTED 缺省;calls/inheritance/annotation 族由 provenance 映射:AST_EXTRACTION/SYMBOL_SOLVER→EXTRACTED,HEURISTIC/FRAMEWORK_INFERENCE→INFERRED,其余(含 MANUAL)→EXTRACTED,原始 provenance 始终单独保留在 attrs.provenance;provenance 为 null 时 confidence=null 省略该属性,与 semantic 族 unknown 处理对称);attrs.relationType 透传 DB 存储值(semantic 族为大写枚举名如 `SEMANTICALLY_SIMILAR_TO`,graph-discovery §3.1 伪码的小写形式仅为示意,N2.1 实现按大写口径匹配);weight:calls/inheritance/annotation=1.0,semantic=confidenceScore 非空取之否则 1.0;directed 按存储方向单向投影,Directed 标志入 attrs。sourceFilePath/targetFilePath 经可选 symbolId→filePath resolver 解析(无 resolver 或符号未知时为 null,不失败)。
- 测试:core 图行为 + service 映射/分页/降级行为。
- owner docs 同步:01-architecture-baseline §4.4.1 与 graph-discovery §3.0 前置3/前置4 状态更新。

## Non-Goals

- 不改 `CodeCallGraph`/`CallGraph` 现有行为(算法消费的 CALLS 拓扑投影保持不变,避免 Leiden/Betweenness/Impact/Export 结果漂移)——typed 视图是**增量新增**,分析路径迁移归 N1.3。
- 不做图分析结果持久化(N1.2)、不迁移查询路径(N1.3)、不建缓存(N1.4)。
- 不新增 ORM 表、不改 ORM 模型(只读既有 4 表)。
- 不实现惊奇评分/问题生成(N2.1/N2.2)。

## Scope

### In Scope

- `nop-code-core/.../core/graph/CodeEdgeData.java`、`CodeRelationGraph.java`(新增)
- `nop-code-service/.../service/graph/CodeRelationGraphLoader.java`(新增)
- 单测:core `TestCodeRelationGraph`、service `TestCodeRelationGraphLoader`
- owner docs:01-architecture-baseline §4.4.1、graph-discovery §3.0 前置表
- 缺口矩阵 N1.1 行状态更新(closure 时)

### Out Of Scope

- CodeCallGraph/CallGraph 改造、CodeCacheManager 缓存集成、GraphQL 暴露。

## Execution Plan

### Phase 1 - core typed 边视图

Status: completed
Targets: `nop-code-core/.../core/graph/`

- Item Types: `Fix`

- [x] `CodeEdgeData`:字段 **edgeType(边族,必填:CALLS/INHERITANCE/ANNOTATION/SEMANTIC)**/sourceId/targetId/**relationType(细分,可空:EXTENDS/IMPLEMENTS/SEMANTICALLY_SIMILAR_TO 等)**/confidence/provenance/directed/sourceFilePath/targetFilePath/weight;builder 或全参构造;不可变
- [x] `CodeRelationGraph implements IGraph`:构造入参 `List<CodeEdgeData>`;内部邻接表(out/in);`getOutEdges/getInEdges` 返回 Edge(**type=edgeType**,weight 透传);Edge.attrs 仅放非 null 属性(**relationType=细分非空取细分、否则取族**/confidence/provenance/directed/sourceFilePath/targetFilePath);**去重键=(sourceId,targetId,edgeType,relationType)**,重复保留首条;`nodeIds()` 返回全部出现过的节点(具体类方法,不进 IGraph)
- [x] `TestCodeRelationGraph`:out/in 方向与对称性、attrs 内容(全属性/部分属性 null 省略)、四族混合、去重语义、空图、nodeIds 集合正确、Edge.equals 不含 attrs(跨图可集合运算)

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

Exit Criteria:

- [x] `TestCodeRelationGraph` 全绿,覆盖上列 7 类断言(10/10)
- [x] **无静默跳过**:edgeType 为 null/空时抛 IllegalArgumentException(fail-fast),不静默生成无类型边(relationType 细分可空,缺省取族,不校验)
- [x] `nop-code-core` 既有测试零回归(`./mvnw test -pl nop-code/nop-code-core,nop-code/nop-code-service -am` BUILD SUCCESS,core 413/0)
- [x] Owner-doc:owner doc 更新延后至 Phase 3 统一收口(新增公共类为 live baseline 变化,§4.4.1/§3.0 在 Phase 3 更新)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - service 加载器与映射

Status: completed
Targets: `nop-code-service/.../service/graph/CodeRelationGraphLoader.java`

- Item Types: `Fix`

- [x] 加载器:4 表分页加载(对齐 CodeCacheManager 分页模式),实体→CodeEdgeData 映射(calls: edgeType=CALLS,attrs.relationType 取族 "CALLS";inheritance: edgeType=INHERITANCE,attrs.relationType=EXTENDS/IMPLEMENTS;annotation: edgeType=ANNOTATION,attrs.relationType 取族(实体无细分列);semantic: edgeType=SEMANTIC,attrs.relationType=DB 大写枚举名);confidence 映射规则如 Goals 所述;provenance 原样入 attrs;filePaths 经 `Function<String,String>` resolver(可空)
- [x] **映射与 DAO 读取分离**:实体→CodeEdgeData 映射实现为与 DAO 分页读取分离的 package-private 静态方法,供无 DB 单测直接调用;`TestCodeRelationGraphLoader`:DynamicOrmEntity 无 DB 构造 4 类实体 → 直接单测映射函数,断言 confidence 映射矩阵逐格(10/20/30/0/unknown、provenance 五值含 null)/directed/weight/filePath 解析与 null 降级/空 index 返回空图
- [x] 无新 GraphQL/BizModel 暴露(纯服务内组件)

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

Exit Criteria:

- [x] `TestCodeRelationGraphLoader` 全绿(confidence 映射矩阵逐格断言,10/10)
- [x] **接线验证**:加载器产出的 `CodeRelationGraph` 可作为 `IGraph` 被既有 nop-graph 算法消费(测试中经 `LeidenDetector.detect` 或 `Bfs` 跑通一次,证明 IGraph 契约兼容)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿(service 163/0)
- [x] Owner-doc:owner doc 更新延后至 Phase 3 统一收口(加载器为服务内组件,无 API 面)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner docs 同步与收口

Status: completed
Targets: `ai-dev/design/nop-code/01-architecture-baseline.md`、`graph-discovery-and-export-design.md`、缺口矩阵

- Item Types: `Fix | Proof`

- [x] 01-architecture-baseline §4.4.1:"CodeCallGraph 只投影 CALLS 边且未填 attrs 是待增强点" 更新为现状(CALLS 拓扑投影保留 + CodeRelationGraph typed 视图已实现,含 attrs 契约)
- [x] graph-discovery §3.0 前置3/前置4 行状态更新(已落地 + confidence 映射口径;AMBIGUOUS 无生产者的说明保留);**消费方指引**:N1.3/N2.1/N2.2 等需要 typed 边属性的下游必须消费 `CodeRelationGraph`,不得消费 `CodeCallGraph`(否则静默退化为 CALLS-only 无 attrs 视图)
- [x] 缺口矩阵 N1.1 行状态更新
- [x] `check-doc-links --strict` exit 0

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

Exit Criteria:

- [x] 两个 owner doc 段落与 live 实现一致(closure audit 核对含 AMBIGUOUS 无生产者如实保留)
- [x] `ai-dev/logs/` 对应日期条目已更新

> 空 index 返回空图:loader 层因映射/DAO 分离退化为 CodeRelationGraph 构造器空行路径,由 core `testEmptyGraph` 等价覆盖(closure audit 认可);DAO-stub 直测留 N1.2 物化时一并评估。

## Closure Gates

- [x] 所有 in-scope 缺口(typed 边视图 + attrs 契约)已落地并有测试钉住
- [x] 既有图分析行为零漂移(CodeCallGraph/CallGraph 未改,git diff 零输出,仅新增 4 文件)
- [x] 必要 focused verification 完成(core 413/0 + service 163/0,含新测试各 10/10)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步(§4.4.1、§3.0 前置表、§3.5 置信度行、缺口矩阵)
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_ee88f592,2026-09-27,APPROVE 附 1 项文本记账修正已执行)
- [x] **Anti-Hollow Check**:closure audit 验证 LeidenDetector 接线测试真实调用 detect 且断言 attrs;loader 无空方法体/吞异常/静默 no-op
- [x] `./mvnw test -pl nop-code/nop-code-core,nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0(0 findings)
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

(无)

## Non-Blocking Follow-ups

- Edge.attrs 的 filePath 解析在符号缺失时为 null——N2.1 评分按设计 §3.0 降级原则跳过对应维度,不在本 plan 处理。

## Closure

Status Note: N1.1 交付物(typed 边视图 + attrs 契约)全部落地。core 新增 CodeEdgeData/CodeRelationGraph,service 新增 CodeRelationGraphLoader;CodeCallGraph/CallGraph 零改动(增量新增,算法行为零漂移);owner docs(§4.4.1/§3.0/§3.5)与缺口矩阵同步。独立 closure audit APPROVE,唯一必修项为 plan 记账一致性(8 个已有证据的 Exit Criteria 补勾),已执行。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_ee88f592(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_ee88f592-7b3a-41e6-9026-078166d3229f
- Evidence:
  - 代码真实性 PASS:CodeEdgeData fail-fast(L136-138)/CodeRelationGraph attrs 六键与去重键(L45-47,L66-75)/Loader 分页(BATCH_SIZE=5000 对齐 CodeCacheManager)/confidenceFromValue 显式 switch 拒绝 fromValue 静默缺省/全文件无空方法体无吞异常
  - 测试真实性 PASS:TestCodeRelationGraph 10/10 + TestCodeRelationGraphLoader 10/10(surefire 双源核对);confidence 矩阵 13 格逐格断言;LeidenDetector 接线测试真实调用 detect 并断言 attrs
  - 行为零漂移 PASS:CodeGraphService 6 处算法消费点仍用 CodeCallGraph,零改动
  - Owner docs PASS:§4.4.1/§3.0 前置3/前置4/§3.5 与 live 逐点一致,AMBIGUOUS 无生产者如实保留
  - 一致性:audit 指出 8 个 Exit Criteria 未勾(证据已齐),已按 audit 要求补勾并注明空 index 等价覆盖口径
  - 工具门禁:check-doc-links 0 errors;scan-hollow 0 findings;check-plan-checklist exit 0
  - 回归证据:_tmp-n11-regression.log BUILD SUCCESS/EXIT=0(core 413/0、service 163/0)
  - Deferred 诚实性:唯一 follow-up(filePath null 降级归 N2.1)有 design §3.0 降级原则背书
  - 审计裁定:APPROVE(2026-09-27)

Follow-up:

- Edge.attrs 的 filePath 解析在符号缺失时为 null——N2.1 评分按设计 §3.0 降级原则跳过对应维度(successor 已登记)
- 分页无 ORDER BY(继承 CodeCacheManager 既有模式)——N1.2 物化时一并评估分页确定性
