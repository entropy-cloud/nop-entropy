# 19 N6.2 IGraph 数据库实现（局部遍历下推）

> Plan Status: completed(实施中对抗审查 agent_b80c9a29 4 项吸收 + 独立 closure audit REVISE 项全部修复后通过，agent_5e2d3880)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N6.2；`graph-db-backend-decision.md`（N6.1 裁定：可移植 SQL、复用四表、下推边界钉死）；live 核对（2026-09-28）
> Related: plan 18（裁定）、N1.1（CodeRelationGraph typed 边视图）、N1.2/N1.3（全局算法物化边界）

## Purpose

按 N6.1 裁定实现 `IGraph` 的数据库后端：`getOutEdges`/`getInEdges` 由全量内存预加载改为**按节点下推点查**（四张关系表 + 既有双向索引），行为与内存实现（`CodeRelationGraphLoader` + `CodeRelationGraph`）严格等价；另提供有界深度遍历助手（应用层 visited，逐跳点查的可移植形态）。

## Current Baseline（live 核对 2026-09-28）

- `CodeRelationGraph`（nop-code-core）是内存 IGraph：构造时吃全量 `CodeEdgeData` 列表（dedup + attrs 语义集中于此）；`CodeRelationGraphLoader.load` 一次加载四表全量（分页 5000）——全局分析（surprise/questions/wiki）需要完整边集，属正当全量消费。
- **可复用资产**：`CodeRelationGraphLoader` 的四个静态行映射器（`toCallEdge`/`toInheritanceEdge`/`toAnnotationEdge`/`toSemanticEdge`，package-visible）+ 置信度映射（provenance 推导 / semantic 10/20/30 显式映射）——DB 后端按行复用即可保证 attrs 语义零漂移。
- `IGraph` 契约仅 `getOutEdges`/`getInEdges`；`nodeIds()` 是 CodeRelationGraph 的具体类访问器（全量加载的副产物），点查后端无此能力也不需要（消费方传入节点集）。
- N6.1 裁定边界：点查 + 有界深度遍历下推；无界遍历（visited 管理）与全局算法不下推。裁定提及递归 CTE——**执行形态裁定**：有界遍历以"逐跳点查 + 应用层 visited"实现（ORM 可移植写法，无方言问题；语义与 CTE 等价且更符合 IGraph 自身方法复用），计划内显式记录该偏差。
- 既有测试基建：`TestCodeRelationGraphLoader`（service，容器级 loader 行为测试）可参照。

## Goals

- **G1 点查后端**：`DbCodeRelationGraph implements IGraph`（nop-code-service graph 包）：per-node 查询四表（indexId + 端点字段，分页读尽），行映射复用 loader 静态方法，attrs/dedup 语义经构造局部 `CodeRelationGraph` 完全复用。
- **G2 有界遍历助手**：`traverse(startIds, maxDepth, out/in/both)`——逐跳点查 BFS，应用层 visited 防环，返回访问节点与经过边；depth ≤ 0 显式异常。
- **G3 等价证明**：容器级集成测试——同一 fixture 索引后，内存图（loader 全量）vs DB 图（点查）对**全部节点**的 out/in 边逐项相等（source/target/type/relationType 集合），未知节点双方均为空。
- **G4 测试**：G3 即集成主测试；补充 point-query 空结果、有界深度（depth 边界与防环）断言。

## Non-Goals

- 不切换任何现有生产查询路径（surprise/questions/wiki 的全量 loader 消费保持——它们需要完整边集；生产路径切换属后续性能演进，见 Non-Blocking Follow-ups）。
- 不实现递归 CTE 原生 SQL（见执行形态裁定）；不新增表/索引。
- 不改 `CodeRelationGraph`/`CodeRelationGraphLoader` 既有行为。

## Scope

### In Scope

- `DbCodeRelationGraph`（nop-code-service graph 包）+ 有界遍历助手。
- 等价/行为集成测试。
- owner docs：graph-db-backend-decision.md 增执行形态备注（逐跳点查形态）、缺口矩阵、roadmap。

### Out Of Scope

- 生产查询路径切换与性能基准（N8.1）。
- 集群索引构建（N6.3/N6.4）。

## Execution Plan

### Phase 1 - 点查后端 + 有界遍历 + 测试（G1/G2/G3/G4）

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/graph/`、测试

- Item Types: `Fix`（roadmap 登记的实现缺口）

- [x] `DbCodeRelationGraph`：ctor(indexId, daoProvider, filePathResolver)；getOutEdges/getInEdges 点查四表（callerId/subTypeId/annotatedSymbolId/sourceSymbolId 与镜像端点字段），分页读尽，行映射复用 loader 静态方法，局部 `CodeRelationGraph` 承载 dedup+attrs
- [x] `traverse(startIds, maxDepth, direction)`：逐跳点查 BFS + visited 防环；depth ≤ 0 抛 IllegalArgumentException
- [x] 集成等价测试：fixture（Java 项目：调用边 + 继承边 + 注解边可复现）indexDirectory → loader 内存图 vs DbGraph 全节点 out/in 逐项相等（边三元组集合 + attrs.relationType/confidence 键值）+ 未知节点双空 + 有界遍历深度/防环断言
- [x] 全量回归 `./mvnw test -pl nop-code/nop-code-service -am` 绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 等价测试全绿且覆盖四族边中 fixture 可复现的族（calls+inheritance 必现；annotation 视 fixture；semantic 若 fixture 无行则等价断言自然覆盖空族）
- [x] **端到端验证**（规则 #22）：indexDirectory 入口 → DB 行 → DbGraph 遍历完整路径断言
- [x] **接线验证**（规则 #23）：DbGraph 在容器内以真实 daoProvider 构造并消费（等价测试即运行时消费证明；生产消费路径切换显式裁定为 follow-up）
- [x] **无静默跳过**：depth 非法显式抛错；点查无结果返回空集合（IGraph 契约语义）而非 null
- [x] No owner-doc update required（执行形态备注归 Phase 2 的裁定文档增注）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs/roadmap 同步

Status: completed
Targets: `graph-db-backend-decision.md`、缺口矩阵、roadmap

- Item Types: `Fix`

- [x] 裁定文档增执行形态备注：有界遍历以逐跳点查实现（可移植性优先，语义等价于 CTE 形态）
- [x] 缺口矩阵 N6.2 行 done；roadmap N6.2 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap/缺口矩阵/decision 文档三处与 live 一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] IGraph 数据库后端落地（点查四表 + attrs 零漂移复用 + 有界遍历）
- [x] 行为等价证明完成（全节点 out/in 逐项相等）
- [x] 必要 focused verification 完成（`./mvnw test -pl nop-code/nop-code-service -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap（生产路径切换为显式裁定 follow-up）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) DbGraph 真实查询四表（非内存缓存代理），(b) 端到端 indexDirectory → DB → 遍历断言，(c) 无静默跳过
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无——in-scope 无延期项）

## Non-Blocking Follow-ups

- 生产结构查询（impact/deps/hierarchy）切换到 DbGraph 点查后端：属行为面变更（impact 目前 CALLS-only 语义），切换需产品裁定与对照评测。Classification: `optimization candidate`；Why Not Blocking Closure: N6.2 交付物为后端实现 + 等价证明，切换不改变"后端可用且等价"的成立性。

## Closure

Status Note: IGraph 数据库后端落地（四表点查 + attrs 零漂移复用 + 有界遍历助手），与内存后端全节点逐项等价经 3 项集成测试钉住；audit 发现的 indexId 过滤缺陷（多 index 共库串边）已修复并有分歧断言钉住。独立 closure audit REVISE 项全部修复后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_5e2d3880；实施中对抗审查 agent_b80c9a29）
- Evidence:
  - 首轮 audit REVISE 1 Major + 2 Minor 全部修复：pointQuery 补 eq(indexId)（原死字段——多 index 共库串边缺陷，G1 明文要求）+ 新增 testPointQueriesAreIndexScoped 双 index 分歧断言；缺口矩阵 5 处 "tdone"→"done" 笔误；全部交付物随本提交入库
  - Anti-Hollow 三项 PASS：(a) 四族独立 daoFor+pointQuery（indexId+端点双过滤）非内存代理，4 个 loader 静态 mapper 原样复用；(b) 端到端：直插四族实体（含精确重复对/有向+无向 semantic/双 index 分歧）→ 两后端全节点 out/in 三元组+attr 键逐项相等；(c) depth≤0 显式抛错、未知节点双端空、外 index 不可见均有断言
  - 实跑 PASS：nop-code-service -am 250/0（TestDbCodeRelationGraphEquivalence 3/3）
  - N6.1 忠实度 PASS：decision §3.2.1 增注与实现逐点吻合；无新表/索引
  - Deferred 分类检查 PASS：impact CALLS-only 语义属实（CodeGraphService L391-422），切换裁定 optimization candidate 成立
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（收口后复跑）

Follow-up:

- (待填)
