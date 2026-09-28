# 18 N6.1 数据库图后端选型决策

> Plan Status: completed(合并审查有条件 APPROVE，条件项全部修复，agent_ed160a58)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N6.1（Item Type: Decision）；`00-vision.md` §一 架构定位（利用数据库图能力，待决策条目）；live 核对（2026-09-28）
> Related: N6.2（IGraph 数据库实现，本裁定的直接消费者）、N1.2/N1.3（全局算法物化边界已交付）

## Purpose

裁定 `IGraph` 第二实现（数据库图后端）的技术路径：PostgreSQL 专属扩展（ltree / Apache AGE）vs 可移植 SQL（普通表 + 递归 CTE），并钉死"局部下推 vs 全局物化"的边界。产出 = 裁定记录（`ai-dev/design/nop-code/graph-db-backend-decision.md`）+ baseline 增注。

## Current Baseline（live 核对 2026-09-28）

- **参考部署 DB**：nop-code-app 依赖 `quarkus-jdbc-mysql` + `quarkus-jdbc-h2`——MySQL/H2 是平台参考形态，无 PostgreSQL 依赖。
- **vision 约束**：拒绝外部图数据库部署（Neo4j Cypher 导出已否决并维持——`graph-analysis-design.md` L104）；"全局算法无法由递归 CTE 求得，只能局部遍历下推"为已记录事实。
- **IGraph 契约**：仅 `getOutEdges(nodeId)`/`getInEdges(nodeId)` 局部遍历 + `Edge.attrs`；无全局算法接口。
- **已落地边界**：全局算法（Leiden/Betweenness/PageRank/入口点）经 N1.2 在索引期物化至 `nop_code_graph_metric`，N1.3 查询物化优先——查询期无全局重算需求。
- **边数据面**：`CodeRelationGraphLoader` 从 calls/inheritance/annotation/semantic 四表加载 typed 边视图；`nop_code_call` 已有 callerId/calleeId 双向索引。
- **无现有 SQL IGraph 实现**（N6.2 未启动）。

## Goals

- 裁定记录文档（decision 文档格式，见 `ai-dev/design/00-design-writing-guide.md`）：选项对比（可移植 SQL CTE / PG ltree / Apache AGE / 外部图库）、裁定与理由、拒绝项及原因、局部下推边界定义（点查 + 有界深度 CTE 下推；无界遍历与全局算法不下推）、对 N6.2 的实现约束（复用四表，不新增专有图结构；CTE 不足时的升级路径声明）。
- baseline §6.2"数据库图后端"行由 ⏳ 未定 → 裁定落地增注。
- 缺口矩阵 N6.1 行 done；roadmap N6.1 todo→done + 汇总计数。

## Non-Goals

- 不实现 IGraph 数据库后端（N6.2）。
- 不做性能基准（选型基于约束与架构适配性；基准归 N8.1 评测框架或 N6.2 实现期）。

## Scope

### In Scope

- `ai-dev/design/nop-code/graph-db-backend-decision.md`（裁定记录）
- `ai-dev/design/nop-code/01-architecture-baseline.md` §6.2 增注
- 缺口矩阵 + roadmap 同步

### Out Of Scope

- 任何代码变更。

## Execution Plan

### Phase 1 - 裁定记录 + docs 同步

Status: completed
Targets: `ai-dev/design/nop-code/graph-db-backend-decision.md`、baseline §6.2、缺口矩阵、roadmap

- Item Types: `Decision | Proof`

- [x] 裁定文档：选项对比（含每项的采纳/拒绝理由，引用本 plan Current Baseline 事实）、裁定结果、下推边界定义、N6.2 实现约束、升级路径
- [x] baseline §6.2 行增注（裁定摘要 + 指向 decision 文档）
- [x] 缺口矩阵 N5.3→N6.1 行 done；roadmap N6.1 done + 计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档经独立子 agent 对抗审查（选项理由可追溯、拒绝项有明确依据、边界定义可操作——N6.2 可直接据其实现）
- [x] baseline/缺口矩阵/roadmap 三处与裁定一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 裁定文档落地且经独立对抗审查（Decision 质量门）
- [x] 受影响 owner docs（baseline/缺口矩阵/roadmap）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] （纯文档计划——构建验证条目按 guide 豁免）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- PG 专属扩展（ltree/AGE）若未来生产 DB 迁移至 PostgreSQL 且 CTE 性能不足，可按裁定文档中的升级路径重新评估。Classification: `optimization candidate`；Why Not Blocking Closure: 当前参考部署无 PG，CTE 路径满足 IGraph 契约全量语义。

## Closure

Status Note: Decision 型交付：裁定文档（四选项对比+下推边界+N6.2 约束+升级路径）落地并经合并审查（草案对抗+closure audit 一体）有条件 APPROVE，条件项（baseline §6.2 行同步等）全部修复后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_ed160a58，草案审查+closure audit 合并执行）
- Evidence:
  - 事实核对 9 项：8 PASS + 1 FAIL（baseline §6.2 行未同步——增注误落 §6.1）→ 已修复（§6.2 行 ⏳→✅ 裁定摘要），复检通过
  - 裁定质量：四选项拒绝理由均有事实支撑；下推边界四分（点查/有界深度/无界/全局）可操作；升级路径诚实（条件性增量优化、需新裁定）
  - Anti-Hollow（Decision 版）：裁定与三处文档（vision §一 回指/baseline §6.1+§6.2/缺口矩阵/roadmap 计数 18/21）一致；N6.2 可直接据 3.2 实现约束开工
  - 条件项全部修复：§6.2 行、log 表述、plan 源引用改 vision §一、vision bullet 回指
  - `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

Follow-up:

- (待填)
