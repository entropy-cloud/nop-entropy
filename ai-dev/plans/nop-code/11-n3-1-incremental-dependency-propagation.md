# 11 N3.1 增量依赖传播(2-hop 受影响面)

> Plan Status: draft(R2 后二次重写;B1 裁定采用方案 ii——裁掉边恢复,传播+可观测交付,边丢失缺陷立 successor)
> Last Reviewed: 2026-09-27
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N3.1;01-baseline §6.2;R1(agent_7aa0d235,1B+4M+7m)+ R2(新 Blocker B1:M1-M4)两轮对抗审查
> Related: N1.2/N1.4(失效语义)、N3.1-successor(增量 callee 解析,见 Deferred)

## Purpose

增量索引时,在删除依赖边**之前**沿 call 边传播变更影响面到 **2-hop 依赖方符号与文件**,并经 `getLastIncrementalAffectedFiles(indexId)` 与 `IncrementalStatus.affectedFiles` 可观测——为 AI/适配器回答"这次变更影响了谁"提供确定性数据。

## Current Baseline

- `IncrementalDetector`/`ChangeSet`:纯文件级指纹对比,无依赖传播。`triggerIncrementalIndex` 流程:检测(L908)→ deleteFileRecords(L925/926)→ 重索引 → 指纹保存(L965)→ 失效(L971)。
- **时序事实(R1)**:deleteFileRecords 删除变更文件符号与跨文件 call 边(按 calleeId∈变更符号集、按 callerId)——传播必须在删除**之前**快照,否则 1-hop 即空。
- **边生成根因事实(R2 B1)**:跨文件 calleeId 仅由全量索引流填充(`ProjectAnalyzer.resolveCalls` 用全局符号表回填;仅 indexDirectory 调用);增量重分析无符号表、无 resolveCalls,且符号 id 每次分析随机(UUID);persist 跳过空 calleeId。**推论:依赖方文件重分析无法恢复 B→C 边,反而会连带删掉仍有效的 A→B 边(净效果更差)——故本 plan 裁掉"依赖方重索引/边恢复"(见 Deferred:增量 callee 解析 successor)。**
- `nop_code_call` 符号级 callerId/calleeId,有双向索引——2-hop 逐跳索引点查(不全量加载,超出 MAX_QUERY_RESULTS=10000 显式抛错)。
- `nop_code_dependency` 为文件级 sourceFilePath/targetFilePath(本项不消费)。
- 物化度量读数:GraphMetricStore(N1.2);`IncrementalStatus`(BizModel)无 affectedFiles;QUERY_METHODS 穷举门禁要求新查询方法登记。
- **roadmap 落地裁定**:"增强 IncrementalDetector" 落地为 `DependencyPropagator`(纯计算)+ `getLastIncrementalAffectedFiles`/Status 字段;Detector 本体只见指纹/资源、无 DB 边访问,无法承载传播。

## Goals

- `DependencyPropagator`(nop-code-core 纯计算):种子符号 id + 逐跳边查询函数,输出 1..2-hop **受影响符号面**(caller/callee 双向、visited 防环、TreeSet 排序);空种子→空集。
- `CodeIndexService.triggerIncrementalIndex` 集成(时序钉死):deleteFileRecords **之前**——变更/删除文件→种子符号(findSymbolIdsByFileId)→ 2-hop 逐跳点查(caller/callee 双向;单跳结果超 10000 抛 NopException 不静默截断;**运营裁定:大图 hot 方法触发该上限时,该文件增量将持续失败,恢复路径为全量重建(indexDirectory 重建完整 call 图后本机制不再受阻)——显式裁定 fail-fast,不做静默截断)→ 受影响符号 → 文件集(NopCodeSymbol.filePath 主键查,排除已变更/已删除文件)→ **deleteFileRecords/重索引/指纹保存/失效照常** → affectedFiles 存入有界 service map(BizModel 同款 MAX_STATUS_ENTRIES=100 LRU;deleteIndex 时清理)。
- 可观测:`ICodeIndexService.getLastIncrementalAffectedFiles(indexId)`(QUERY_METHODS 同步)+ `IncrementalStatus.affectedFiles`(BizModel 装配)。
- 测试:传播器单测(core)+ 集成测试(A→B→C 链,改 C 后 affectedFiles 读回 A、B 文件)。
- owner docs:baseline §6.2 行、缺口矩阵 N3.1 行。

## Non-Goals

- **不做依赖方文件重分析与边恢复**(R2 B1 裁定:增量路径无 callee 解析机制,重分析会净恶化——缺陷本体立 successor,见 Deferred);不做细粒度物化失效;不改 fingerprint 逻辑;语义边/跨 index 传播不做。

## Scope

### In Scope

- nop-code-core:`DependencyPropagator`(纯计算)
- nop-code-service:`CodeIndexService` 集成(快照时序+有界 map)、`ICodeIndexService.getLastIncrementalAffectedFiles`、`IncrementalStatus.affectedFiles`(BizModel)、invariant QUERY_METHODS 同步
- 测试:`TestDependencyPropagator`(core)、`TestIncrementalDependencyPropagation`(service 集成)
- owner docs:baseline §6.2、缺口矩阵 N3.1

### Out Of Scope

- 增量 callee 解析与边恢复(successor,见 Deferred);语义边/跨 index;细粒度物化失效。

## Execution Plan

### Phase 1 - 传播器与增量集成

Status: planned
Targets: `nop-code-core/incremental/DependencyPropagator`、`CodeIndexService`、`ICodeIndexService`、`NopCodeIndexBizModel`

- Item Types: `Fix`

- [x] `DependencyPropagator.propagate(seedIds, hopQuery, hops)`:hopQuery(symbolId 集合)→邻接符号 id 集;BFS hops 轮、visited 防环、TreeSet 输出;空种子→空集
- [x] `CodeIndexService` 集成(时序):deleteFileRecords 之前——种子符号(findSymbolIdsByFileId × 变更/删除文件)→ 逐跳点查(caller/callee 双向;单跳 >10000 抛 NopException)→ 受影响符号 → 文件集(NopCodeSymbol.filePath 主键查,排除已变更/已删除文件)→ 存有界 map(100 LRU,deleteIndex 清理)→ 后续删除/重索引/失效照常
- [x] `ICodeIndexService.getLastIncrementalAffectedFiles(indexId)` + BizModel `IncrementalStatus.affectedFiles` 装配 + invariant QUERY_METHODS 同步
- [x] 单测 `TestDependencyPropagator`(core):1-hop/2-hop 链、环防、去重排序、空种子、hops=1
- [x] 集成测试 `TestIncrementalDependencyPropagation`:A→B→C call 链夹具;改 C 后 triggerIncrementalIndex——①getLastIncrementalAffectedFiles 读回含 A、B 文件且**不含 C**(变更文件自身排除——负断言);②getIncrementalStatus 读回 affectedFiles(端到端);③deleteIndex 后查询清空

Exit Criteria:

> 每个 Phase 完成后,必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 单测与集成测试全绿(含③deleteIndex 清理)
- [x] **接线验证**:triggerIncrementalIndex 真实调用传播器(①②读回断言)
- [x] **无静默跳过**:单跳查询超 10000 显式抛错;传播失败随事务抛错
- [x] invariant 门禁全绿(QUERY_METHODS 同步)
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] Owner-doc(Phase 1 内完成):baseline §6.2 行、缺口矩阵 N3.1 行
- [x] **roadmap 缺口矩阵已新增 N3.1-s 行**(confirmed live defect,Item Type: Fix;增量 callee 解析与依赖方边恢复——Deferred successor 落地)
- [x] `check-doc-links --strict` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 传播器与增量集成落地且有测试钉住(1-hop/2-hop/环防/时序/读回)
- [x] 必要 focused verification 完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect(边丢失缺陷已立 successor,见 Deferred)
- [x] 受影响 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**:closure audit 验证(a)增量路径真实调用传播器(读回断言),(b)时序正确(删除前快照),(c)有界 map 清理真实
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 增量跨文件 callee 解析与依赖方边恢复(confirmed live defect)

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 修复需在增量路径补齐 calleeQualifiedName→符号 id 解析机制(对齐 resolveCalls 的精确/模糊/多候选语义+层序),机制量级构成独立 WI;**该缺陷为预存行为**(历次增量均丢失跨文件 call 边),非本 plan 新引入——本 plan 的传播快照在删除前完成,不受该缺陷影响。guide 禁止 confirmed live defect 入 Non-Blocking Follow-ups,故以 successor ownership 正式登记,并回灌 roadmap 缺口矩阵。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` 缺口矩阵新增"N3.1-s 增量 callee 解析与依赖方边恢复"(confirmed live defect,Fix)

## Non-Blocking Follow-ups

- 受影响面扩张上限(超大 hub 的 2-hop 文件数):逐跳点查有 10000 抛错护栏,整集规模由 call 图固有结构决定;扩传播上限裁定归 successor。

## Closure

Status Note: (待 closure audit 后填写)
Completed: (待填)

Closure Audit Evidence:

- Reviewer / Agent: (待独立子 agent closure audit 后填写)
- Evidence: (待填)
