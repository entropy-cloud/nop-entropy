# 06 N1.4 分析缓存语义对齐(读缓存 + 失效策略对齐增量)

> Plan Status: completed
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N1.4;live 核对(2026-09-27)
> Related: N1.2/N1.3(物化与失效语义),N1.1(typed 边视图——结构查询仍消费 lazy 视图,本 plan 明确其读缓存定位)
> Draft Review: R1(agent_cb5f037e,1 Blocker + 3 Major + 4 Minor)全修订:失效移分支终点(防 persist 重建抵消)+回滚语义裁定+owner-doc 归 Phase 1+术语映射声明+deleteByIndex 插入点先于 persist+测试预物化前置+§6.1/§6.2 对齐;失效点穷尽性与 invariant 不触红经 R1 live 核实

## Purpose

将 `CodeCacheManager.AnalysisCache` 明确定位为 **DB 派生视图(SymbolTable/CallGraph/dependencies)的读缓存**(DB 是唯一事实源,缓存只是读穿透加速),并使**失效策略与增量索引语义对齐**:实际内容变更才失效,失效面覆盖分析缓存与物化度量行两个层面。

**roadmap 术语映射声明**:roadmap 措辞"明确为物化结果的读缓存"按 live 语义裁定为——AnalysisCache 缓存的是 DB 内已物化行(symbol/call/dependency)的派生视图;度量行(`nop_code_graph_metric`)的读取经 `GraphMetricStore` 直查 DB,不经 AnalysisCache。缺口矩阵 N1.4 行引用 baseline §6.1(读缓存语义)与此一致。

## Current Baseline

- 失效调用点(`CodeIndexService`,`invalidateAnalysisCache` → cacheManager 移除 entry + FlowDetector.invalidateCache):
  - `indexDirectory` L301(重索引前)——N1.3 B2 已在同事务删物化行 ✓
  - `indexFile` L349(单文件索引后)——**只失效分析缓存,不删物化行**:单文件内容变更使全局度量陈旧,但 `nop_code_graph_metric` 行残留 → N1.3 物化优先读取会返回陈旧聚合(正确性缺口)
  - `deleteIndex` L564——缓存失效 + 物化行在清除清单 ✓
  - `triggerIncrementalIndex` L725——**在 no-op 早返回之前无条件失效分析缓存**:no-op 增量(0 变更,物化行正确保留)却 drop 了缓存,下个结构查询白白重建(与 N1.2 确立的"no-op 不失效"语义不一致,浪费但无害)
  - `batchDeleteFileRecords` L2013——**只失效分析缓存,不删物化行**(同 indexFile 缺口)
- 缓存语义现状:`AnalysisCache{SymbolTable, CallGraph, dependencies}` + LRU(20 entries)/TTL(1h),均为 DB 表的内存投影;无任何文档/注释声明"DB 是事实源、缓存只读加速"。
- 既有测试基线:`TestCodeCacheManager`(LRU/TTL/access-order,直接构造 CodeCacheManager)、lazy 依赖 E2E 若干(N1.3 后经自愈回退保持绿)。

## Goals

- **失效策略对齐**(正确性+一致性):
  1. `triggerIncrementalIndex`:分析缓存失效移入实际变更分支(no-op 增量不再 drop 缓存,与物化行保留语义对齐)。
  2. `indexFile` 与 `batchDeleteFileRecords`:补物化行失效(`deleteByIndex` 同事务)——任何实际内容变更都使物化行与缓存同步失效。
- **语义成文**:`CodeCacheManager`/`AnalysisCache` javadoc 声明读缓存定位(DB 事实源、读穿透、LRU/TTL 为内存护栏、失效由索引写路径触发)。
- 测试:①no-op 增量保留缓存(缓存条目存活);②实际变更增量失效缓存;③indexFile/batchDeleteFileRecords 后物化行清空;④既有全绿。

## Non-Goals

- 不改 LRU/TTL 参数、不改 CodeCacheManager 结构、不迁结构查询(N6.2)。
- 不做物化行的细粒度失效(全局度量的性质决定按 indexId 整批失效)。
- 不增删 `ICodeIndexService` public 方法(invariant 门禁稳定)。

## Scope

### In Scope

- `CodeIndexService`(失效点调整 ×3)、`CodeCacheManager`(javadoc)、测试 `TestCacheInvalidationAlignment`(impl 包)、baseline §6.1+§6.2
- owner docs:baseline §6.2 查询路径行补一句失效对齐、缺口矩阵 N1.4 行

### Out Of Scope

- FlowDetector 内部缓存策略(已随 invalidateAnalysisCache 联动,保持现状)。

## Execution Plan

### Phase 1 - 失效对齐与语义成文

Status: completed
Targets: `CodeIndexService`、`CodeCacheManager`

- Item Types: `Fix`

- [x] `triggerIncrementalIndex`:`invalidateAnalysisCache(indexId)` 从方法入口(L725)移到**实际变更分支终点**(`batchQueue.flush()`/`saveFingerprints` 之后、`return` 之前)——不能放分支起点:persist 过程会经 `getOrRebuildSymbolTable` 重建缓存条目,先失效会被重建抵消,方法返回时条目又存在。该位置在 withIndexLock 内+事务 session 内:缓存失效是纯内存操作不随事务回滚(回滚场景=多失效一次,下次查询重建同样数据,无害);反向的"commit 成功但漏失效"才会造成最长 TTL 1h 的陈旧读,分支终点放置天然杜绝。顺带修复现隐患:现行入口失效+persist 中途重建,会把 in-flight session 数据的缓存存活到 commit
- [x] `indexFile`:在其事务 session 内、`persistSingleFileInSession` **之前**补 `ensureSubServices(); graphMetricMaterializer.deleteByIndex(session, indexId);`(deleteByIndex 内含 session.clear(),必须先于 persist;缓存失效 L349 留在 commit 后不动——commit 后失效不会漏已提交变更,事务前失效有并发读者旧数据回填竞窗)
- [x] `batchDeleteFileRecords`:同上,在其事务 session 内、持久化操作之前补物化行失效
- [x] `CodeCacheManager`/`AnalysisCache` javadoc:读缓存定位声明
- [x] 测试 `TestCacheInvalidationAlignment`(impl 包,反射取 CodeIndexService 内部 `cacheManager` 字段——反射先例 TestCodeCacheManager;`getValidEntry` package-private 直调)。**③④必须预物化**:indexDirectory(含真实调用边的夹具,同 TestGraphMetricMaterialization 的 Alpha/Beta 模式)+ `materializeGraphMetrics` + 断言行数 >0,再触发失效断言归 0——防删 0 行的空洞通过。断言:①indexDirectory+查询预热缓存 → 无变更增量(file: URI)→ 缓存条目仍存活;②实际变更增量 → 缓存条目被移除(注意 persist 中途会重建条目,断言在方法返回后做);③indexFile 后物化行清空;④batchDeleteFileRecords 后物化行清空;⑤deleteIndex 后缓存与物化行均空

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新测试 5 断言全绿
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿(既有回归含 invariant 幂等门禁)
- [x] **无静默跳过**:物化行失效随事务回滚(事务性);缓存失效为内存操作,失败即抛出不吞错(不随事务回滚,over-invalidation 无害)
- [x] **接线验证**:indexFile/batchDelete 的 deleteByIndex 真实被调用(测试经 DB 行数断言)
- [x] Owner-doc(Phase 1 内完成):baseline §6.1"查询路径无状态"行与 §6.2 查询路径行各补失效对齐一句(与缺口矩阵 N1.4 行引用的 §6.1 对齐);缺口矩阵 N1.4 行更新
- [x] `check-doc-links --strict` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 失效策略对齐完成:实际变更路径(增量实际变更/indexFile/batchDelete/deleteIndex/indexDirectory)同时失效缓存与物化行;no-op 路径两者都保留
- [x] 必要 focused verification 完成(新测试 5/5 + service 回归)
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据(agent_f9614574,2026-09-27,APPROVE)
- [x] **Anti-Hollow Check**:closure audit 验证失效行为真实(DB 行数断言 + 缓存条目断言),非仅 javadoc;失效点穷尽性核实(5 处写路径一一对应,无第 6 处遗漏)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

(无)

## Non-Blocking Follow-ups

(无)

## Closure

Status Note: N1.4 交付物落地:失效策略对齐(no-op 增量保留缓存与物化行;实际变更路径双失效)、读缓存语义成文。审计唯一 Minor(§6.2 行字面未改但实质已由 N1.2 时的失效句+新增 §6.1 句覆盖)记录裁定不阻塞。
Completed: 2026-09-27

Closure Audit Evidence:

- Reviewer / Agent: agent_f9614574(独立 fresh-session closure auditor,2026-09-27)
- Audit Session: agent_f9614574-c08f-4ab8-9d0f-d1d4e4c3effd
- Evidence:
  - 代码真实性 PASS:①增量失效移分支终点 L812(saveFingerprints 后/return 前)+入口 NOTE 注释;②indexFile L347-348 deleteByIndex 先于 persist;③batchDelete L2027-2028;④javadoc L29-37
  - 测试真实性 PASS:TestCacheInvalidationAlignment 5/5(反射取 cacheManager+预物化前置防空洞)
  - 回归 PASS:_tmp-n14-regression.log BUILD SUCCESS;幂等门禁 8/8(内存失效位置移动不影响 DB 写序列)
  - 穷尽性 PASS:5 处失效点与接口写路径一一对应;batchSaveFileRecords 仅写指纹元数据正确免失效
  - 工具门禁:doc-links 0 errors / scan-hollow 0 findings / check-plan-checklist exit 0(auditor 本机复跑)
  - Minor 裁定:baseline §6.2 字面未改,但 N1.2 时已写的失效句+新增 §6.1 句实质覆盖——记录不阻塞
  - 审计裁定:APPROVE(2026-09-27)

Follow-up:

- no remaining plan-owned work
