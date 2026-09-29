# 04 N1.2 全局算法结果持久化(图度量物化)

> Plan Status: draft
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N1.2;`ai-dev/design/nop-code/01-architecture-baseline.md` §4.4.1/§6.2;`graph-discovery-and-export-design.md` §3.0 前置1/前置2
> Related: N1.3(查询路径迁移的本数据面消费方),N2.1/N2.2/N2.3(社区/中心性读数消费方)
> Protected Area: **ORM 模型变更 plan-first**——本 plan 即 owner-doc 审计载体;生成链已核实(2026-09-27)
> Draft Review: R1(agent_c9ad617d,1 Blocker + 3 Major + 6 Minor)全修订;Phase 1 生成链经审查实测放行

## Purpose

将查询期重复计算的全局图算法结果(社区/介数中心性/入口点评分/PageRank)在**索引期物化**到新 ORM 表 `nop_code_graph_metric`,提供**查询期只读面**,消除 N1.3 无状态化与 N2.1/N2.2 评分的数据障碍(graph-discovery §3.0 前置1"每次查询重算 Leiden"、前置2"介数每次重算且 >10000 节点跳过")。

## Current Baseline

> 行号为 2026-09-27 起草快照,以符号锚定。

- 查询期重算现状:`CodeGraphService.detectCommunities`(L80-91,每次 `cacheManager.getOrRebuildCallGraph` + `runCommunityDetection`)、`getCriticalNodes`(L168+,BetweennessCentrality.compute)、`getGraphAnalysis`(L94+,`new EntryPointScorer().scoreEntryPoints`);PageRank(nop-graph-core `PageRank.compute(IGraph,Set,int)`)当前 nop-code 无消费方。
- ORM 生成链(2026-09-27 live 核实):唯一手写源 `nop-code/model/nop-code.orm.xml`;`./mvnw install` 经 exec-maven-plugin+CodeGenTask 四站生成——站 A `nop-code-codegen/postcompile/gen-orm.xgen`(_app.orm.xml、实体类、BizModel、xbiz、dict yaml)、站 B `nop-code-meta/precompile/gen-meta.xgen`(xmeta)、站 C(gen-crud-api/gen-i18n + `_templates/*.json`)、站 D(web view/page)。`_` 前缀与 `_gen/` 每次覆盖,无前缀保留层仅缺失时生成。先例 commit `1508965cf2`(新增 nop_code_semantic_edge,23 文件)。
- dict 两先例:orm.xml `<dicts>` 定义(物化,如 `code/index_status`)或独立手写 yaml(`call_direction`);本 plan 的 `code/metric_type` 被 ORM 字段引用,走 orm.xml 先例 A。
- 集成测试范本:nop-code-service 测试 TestIncrementalIndexWithDb(`@NopTestConfig(localDb=true, initDatabaseSchema=TRUE)` + `JunitAutoTestCase`,H2 自动建表,GraphQL 驱动索引 + daoProvider 断言)。
- 入口点评分:`EntryPointScorer.scoreEntryPoints(CallGraph,SymbolTable) → List<EntryPointScore>`(含 entryPointType 枚举);介数:`BetweennessCentrality.compute(IGraph,Set<String>)`(现 >10000 节点跳过,`CodeGraphService` 内有该保护);社区:`LeidenDetector.detect(IGraph,Set,LeidenConfig) → CommunityResult`(CommunityInfo 含节点归属)。

## Goals

- 新 ORM 实体 `NopCodeGraphMetric`(表 `nop_code_graph_metric`):通用度量行模型(indexId + metricType + symbolId + communityId/score/rankNo + computedAt + extData),dict `code/metric_type` 定义于 orm.xml(物化)。
- 物化写入器:计算逻辑放 `io.nop.code.service.impl.GraphMetricMaterializer`(package-private,与 CodeGraphService/CodeSearchService 同模式——由 `CodeIndexService.ensureSubServices()` 构造注入、**共享同一 CodeCacheManager 实例**;严禁独立 bean 自建缓存,否则 invalidateAnalysisCache 失效不到会物化陈旧图);`service/graph` 包只放接收已算好输入的纯计算部分。对外经 `ICodeIndexService` 暴露 `materializeGraphMetrics(indexId)`。一次物化 = 重建内存图 → Leiden 社区 + Betweenness + PageRank + EntryPointScorer;先删后写(按 indexId 整批替换,幂等);事务用 `transactionTemplate.runInTransaction(REQUIRED) + ormTemplate.runInSession`,批量删除复用 `deleteEntitiesPaged`。
- 索引期接线:`NopCodeIndexBizModel.triggerFullIndex` 完成索引后经 `ICodeIndexService.materializeGraphMetrics` 调用(try/catch,失败仅 error 日志不影响索引返回——物化是派生数据;BizModel 注入经非生成的 `app-service.beans.xml`);增量索引**仅当实际发生变更(changedFiles+deletedFiles>0)时**删除物化行(no-op 增量不失效);删行在增量同一事务内,失败随事务回滚不静默——失效失败残留陈旧行比缺行更糟;`CodeIndexService.deleteIndex` 的按 indexId 清除清单内加 `deleteEntitiesPaged` 删物化行(同一事务)。
- 查询期只读面 `GraphMetricStore`(nop-code-service):`loadCommunities(indexId)` / `loadScores(indexId, metricType)` / `hasMaterialized(indexId, metricType)`,只读不计算;消费方(既有查询 API)在 N1.3 迁移,本 plan 不改既有查询行为。
- **行覆盖语义(钉死)**:COMMUNITY/BETWEENNESS/PAGE_RANK 行仅覆盖 **call-graph 节点集**(出现过调用边的符号;孤立符号无行);ENTRY_POINT 行仅覆盖 **METHOD/CONSTRUCTOR** kind。`hasMaterialized=true` ≠ 全符号覆盖;消费方 lookup miss 按设计降级原则处理(N2.1 的全边集社区维度与 CALLS-only 社区归属的覆盖差,由消费方显式裁定)。
- 测试:localDb 集成测试(索引 → materialize → DB 断言 → 只读面断言)+ 失效语义测试。

## Non-Goals

- 不迁移既有查询 API 到物化结果(N1.3);不改 CodeCacheManager(N1.4)。
- 不暴露新 GraphQL query/mutation(N2.x 按需暴露;triggerFullIndex 行为增量对调用者透明,返回值不变)。
- 不做集群/分布式物化、不调优 Leiden 参数(沿用 runCommunityDetection 现有 config)。
- 不实现 N2.4 的重建触发。

## Scope

### In Scope

- `nop-code/model/nop-code.orm.xml`(新实体 + dict,唯一手写模型源)
- 生成物(构建自动产出,一并提交):dao 实体/_app.orm.xml、meta xmeta/i18n/_templates、dict yaml、service BizModel/xbiz/beans、api/web 生成层
- `nop-code-service`:`GraphMetricMaterializer`、`GraphMetricStore`、triggerFullIndex/triggerIncrementalIndex/deleteIndex 接线
- 测试:`TestGraphMetricMaterialization`(localDb)
- owner docs:01-architecture-baseline §6.2 该行状态、graph-discovery §3.0 前置1/前置2 状态、缺口矩阵 N1.2 行

### Out Of Scope

- 既有 GraphQL API 行为变更;CodeCacheManager/查询路径;增量物化策略(失效即重建由 N1.3/N1.4 语义收口)。

## Execution Plan

### Phase 1 - ORM 模型与生成链

Status: completed
Targets: `nop-code/model/nop-code.orm.xml` + 生成物

- Item Types: `Fix`

- [x] orm.xml 新增 `<dict>` `code/metric_type`(COMMUNITY/BETWEENNESS/PAGE_RANK/ENTRY_POINT)与新实体 `NopCodeGraphMetric`(列:id PK、indexId、metricType(ext:dict)、symbolId、communityId INTEGER 可空、score DOUBLE 可空、rankNo INTEGER 可空、entryPointType VARCHAR(20) 可空(入口点类型,普通列不挂 dict——EntryPointScorer 枚举名直存)、computedAt TIMESTAMP、extData JSON 可空 + 显式声明 createTime/updateTime(两列先例为 NopCodeIndex;useStdFields 不自动加列);唯一约束 indexId+metricType+symbolId)
- [x] `./mvnw clean install -DskipTests -T 1C -pl nop-code -am` 触发生成链,核对生成物清单(dao 实体/_app.orm.xml/xmeta/i18n/_templates/dict yaml/BizModel/xbiz/web view+page);禁止手改 `_` 前缀文件
- [x] 生成物与 `git status` 逐项核对后一并提交(先例 1508965cf2 惯例)

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop_code_graph_metric` 在 `_app.orm.xml` 中且 H2 initDatabaseSchema 建表成功(集成测试可建行)
- [x] dict `code/metric_type` 物化为 _vfs 下的 dict/code/metric_type.dict.yaml 字典(force-override 头)
- [x] `./mvnw clean install -DskipTests -T 1C -pl nop-code -am` 通过(Maven 4 reactor 已含 nop-code-codegen,generate-test-resources 绑定在 install 生命周期内,生成链必跑——R1 审查实测核实)
- [x] Owner-doc:生成链无文档更新需求(既有 runbook `create-new-entity.md` 已覆盖);实体变更本身在 Phase 3 记入 baseline
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 物化写入器与只读面

Status: completed
Targets: `nop-code-service/.../service/impl/GraphMetricMaterializer.java`、`service/graph/`(纯计算)、`GraphMetricStore.java`、`ICodeIndexService`/`CodeIndexService`、`NopCodeIndexBizModel.java`、`app-service.beans.xml`

- Item Types: `Fix`

- [x] `GraphMetricMaterializer.materialize(indexId)`(impl 包,经 ensureSubServices 共享 cacheManager)→ Leiden(社区行)+ Betweenness(score+rank;>10000 节点跳过并 warn 日志,与现查询保护一致)+ PageRank(score+rank,iterations 取 nop-graph-core 测试惯用值,实现时以该模块测试为准并注释来源)+ EntryPointScorer(score+rank,entryPointType 列);删旧插新在 `transactionTemplate.runInTransaction + ormTemplate.runInSession` 内,批量删复用 `deleteEntitiesPaged`,幂等
- [x] `GraphMetricStore` 只读面:`hasMaterialized`/`loadCommunities`(symbolId→communityId)/`loadScores`(symbolId→score);零计算
- [x] 接线:triggerFullIndex 成功后调 materializer(try/catch,失败仅 error 日志不影响索引返回);triggerIncrementalIndex 与 deleteIndex 按 indexId 删除物化行(失效语义:下次 full index 重建)
- [x] `TestGraphMetricMaterialization`(localDb,JunitAutoTestCase 模式)。**夹具规格**:测试项目必须含真实调用边(≥3 个类,类 A 的方法调用类 B/C 的方法,足够 METHOD 符号——无调用边的 `class Foo{int x;}` 夹具会产生零行使断言空转)+ 跨目录文件以获得非平凡社区结构
- [x] 断言链:索引 → 经 graphQLEngine 调 triggerFullIndex → DB 断言(四类 metricType 行存在、community 行 communityId 非空、betweenness/pagerank 行 score 有值、entrypoint 行 entryPointType 非空)→ 只读面读取与 DB 一致 → **无变更增量(返回 0)不删行** → **有变更增量删行** → deleteIndex 后行清空

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 集成测试全绿且断言真实 DB 行(非 mock)
- [x] **接线验证**:triggerFullIndex GraphQL mutation 端到端跑通后 `nop_code_graph_metric` 出现行(测试经 graphQLEngine 调 mutation,断言 mutation 返回后行存在)
- [x] **无静默跳过**:materialize 失败路径显式 error 日志+不写部分行(先删后写在同一事务);>10000 跳过有 warn 日志
- [x] 只读面零计算:GraphMetricStore 不 import 任何算法类
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] Owner-doc:延后 Phase 3
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - owner docs 同步与收口

Status: completed
Targets: `ai-dev/design/nop-code/01-architecture-baseline.md`、`graph-discovery-and-export-design.md`、缺口矩阵

- Item Types: `Fix | Proof`

- [x] baseline §6.2"全局算法结果持久化"行 ⏳→✅(含表名/失效/覆盖语义;物化表详见 §6.2 行文本,表名 nop_code_graph_metric)
- [x] graph-discovery §3.0 前置1/前置2 → ✅(注明失效语义:仅实际变更的增量失效、no-op 不失效;**行覆盖语义**:COMMUNITY/BETWEENNESS/PAGE_RANK=call-graph 节点集,ENTRY_POINT=METHOD/CONSTRUCTOR;消费方 hasMaterialized + lookup miss 降级)
- [x] 缺口矩阵 N1.2 行更新
- [x] `check-doc-links --strict` exit 0

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] owner docs 与 live 一致(含失效语义与降级指引)
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope 项落地:ORM 表/物化写入器/只读面/索引期接线/失效语义
- [x] 必要 focused verification 完成(localDb 集成测试 4/4 + invariant 门禁 8/8 + service 回归 168/0)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步(§6.2/§4.4.1/§3.0、缺口矩阵)
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_79aee7cb,2026-09-27,APPROVE)
- [x] **Anti-Hollow Check**:closure audit 验证(a)triggerFullIndex→materialize→DB 行由 GraphQL 端到端测试真实连通,(b)GraphMetricStore 被测试真实读取且零算法 import,(c)无空方法体,唯一 catch 为 adjudicated error-log 非静默吞
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿(168 tests/0 failures)
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0(0 findings)
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

(无)

## Non-Blocking Follow-ups

- 增量索引后的自动重物化策略(现失效等待 full index)——N1.3/N1.4 缓存语义收口时统一裁定。
- 增量路径预存怪癖:裸 OS 路径(非 `file:` URI)调 triggerIncrementalIndex 时 VFS 资源收集为 0,全部指纹判 deleted(changed 返回 0 但失效触发)——预存行为,与本 plan 无关,successor 归 N3.1 增量语义治理。
- Betweenness 的 >10000 节点跳过与空节点集跳过在物化期同样生效——大图/无边项目信号缺失由 N2.2 按降级原则处理(与 graph-discovery §3.0 原语义一致)。

## Closure

Status Note: N1.2 交付物(ORM 表/物化写入器/只读面/索引期接线/失效语义)全部落地并经独立审计确认。生成链自动产出全栈生成物;invariant-loop 门禁(幂等表完整性)抓住新方法未登记,按门禁要求补登 verify 分支——质量闭环与功能闭环协作正常。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_79aee7cb(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_79aee7cb-505e-4f6e-bb13-384c5669b12f
- Evidence:
  - ORM/生成物 PASS:orm.xml 实体+dict 与 plan 逐项一致;_app.orm.xml/_gen 实体/dict yaml/xmeta/BizModel/api/web 全栈生成物存在,无手改 `_` 前缀痕迹
  - 代码真实性 PASS:四族物化/单事务替换/跳过守卫/幂等;Leiden 配置与既有 runCommunityDetection 逐值一致;PageRank iterations=20 与 nop-graph-core TestAlgorithms 一致;GraphMetricStore 零算法 import;接线四点(构造共享 cacheManager/门面/增量先判 no-op 再失效/deleteIndex 同事务清除)+biz try/catch 全部核实
  - 测试真实性 PASS:TestGraphMetricMaterialization 4/4(GraphQL 测试真调 engine 且未显式调 materialize——真实覆盖 biz 接线)、幂等门禁 8/8(retry 替换不重复真断言);回归 168/0
  - 工具门禁:doc-links 0 errors / scan-hollow 0 findings / check-plan-checklist exit 0
  - Deferred 诚实性:裸 OS 路径怪癖经 live 走查确属预存(逐字吻合 follow-up 描述),successor N3.1 归类合理
  - 审计裁定:APPROVE(2026-09-27),closure 动作(统一提交含 _cases 快照、清理调试日志)已执行

Follow-up:

- 增量索引后的自动重物化策略——N1.3/N1.4 统一裁定
- 增量路径裸 OS 路径怪癖(VFS 收集 0→全判 deleted)——successor N3.1
