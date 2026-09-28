# 21 N6.4 集群索引构建——原子发布与一致性

> Plan Status: completed(独立 closure audit APPROVE，agent_10697665)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N6.4；plan 20（N6.3 基座：分片/工作区/indexFileSet）；live 核对（2026-09-28）
> Related: N6.3（前序基座）、N6.1（存储裁定）、N1.2（全局算法物化=发布点的一部分）

## Purpose

定义并实现集群分片构建的**原子发布语义**：分片构建期间索引可辨识为 BUILDING；全部分片完成后由发布协调器原子翻转为 COMPLETED 并触发全局度量物化。交付物 = 协调器代码 + 测试 + **一致性语义文档**。

## Current Baseline（live 核对 2026-09-28）

- N6.3 基座：`indexFileSet`（单事务文件子集索引）、`IndexShardPlanner`（分片清单）、`ClusterWorkspaceManager`（工作区）。分片执行直接写共享 DB（同 indexId），**无构建状态辨识、无分片完成登记、无发布点**。
- `NopCodeIndex.status` 字段已存在（indexDirectory 置 "COMPLETED"），但增量/分片路径不触碰它——无 BUILDING 语义。
- 全局度量：`GraphMetricMaterializer.materialize(indexId)` 可独立触发；各写路径写后 `deleteByIndex` 失效（N1.2/N1.3）。
- **无分片持久登记面**：分布式编排器需要一个共享的完成登记（进程内 map 在多 worker 下无效）。
- ORM 加表流程：`nop-code/model/nop-code.orm.xml` 加 entity → 构建期 codegen 生成 `_gen` 实体/`_NopCodeDaoConstants`（N3.2 dict 变更先例）。**ORM 模型变更为 plan-first 保护区域——本 plan 即授权计划。**

## Goals

- **G1 分片登记面**：新增 ORM 实体 `nop_code_shard_ledger`（id/indexId/shardId/status[PENDING|COMPLETED]/completedAt；unique(indexId, shardId)）——分布式共享的分片完成登记。
- **G2 发布协调器**：`ShardBuildCoordinator`（nop-code-service）：
  - `beginShardBuild(indexId, shardIds)`：index.status=BUILDING + ledger 全量重建（PENDING）；
  - `completeShard(indexId, shardId)`：登记 COMPLETED（幂等），**全部 COMPLETED 时原子发布**：index.status=COMPLETED + `materializeGraphMetrics`（全局度量与状态翻转同一事务）→ 返回 published 标志；
  - `getShardBuildStatus(indexId)`：分片清单/完成度读回。
- **G3 一致性语义文档**：`ai-dev/design/nop-code/cluster-index-consistency.md`——分片事务边界（单分片原子，无跨分片事务）、发布点定义（状态翻转+物化同事务）、BUILDING 可见性语义（已写分片即时可见，状态可辨识）、故障恢复（分片重跑幂等、ledger 重放、僵尸 BUILDING 的恢复=重新 begin）、与 N6.1/N6.3 衔接。
- **G4 测试**：协调器集成测试（begin→部分完成→未发布；全部完成→published+status 翻转+物化行存在；completeShard 幂等；重复 begin 重建 ledger）。

## Non-Goals

- 不做 worker 协议/调度器（编排层归后续集群 WI）。
- 不改 N6.3 的 indexFileSet 语义（分片执行不变；发布协调是正交步骤）。
- 不做 BUILDING 索引的查询屏蔽（查询面语义=文档钉死的"即时可见+状态可辨识"；屏蔽属产品裁定）。

## Scope

### In Scope

- nop-code/model/nop-code.orm.xml：新增 `nop_code_shard_ledger` 实体 + codegen 生成物同步。
- `ShardBuildCoordinator`（nop-code-service）+ ICodeIndexService 或独立服务暴露（执行期按最小 diff 定）。
- 一致性语义文档 + 测试 + owner docs（baseline §6.2、缺口矩阵、roadmap）。

### Out Of Scope

- worker 编排协议、查询屏蔽、跨分片事务。

## Execution Plan

### Phase 1 - 登记面 + 协调器 + 一致性文档 + 测试（G1-G4）

Status: completed
Targets: nop-code/model/nop-code.orm.xml、nop-code-service、ai-dev/design/nop-code/cluster-index-consistency.md

- Item Types: `Fix`

- [x] ORM 实体 `nop_code_shard_ledger` + codegen 生成物同步（_gen/_NopCodeDaoConstants/dict 如涉及）
- [x] `ShardBuildCoordinator`：begin/complete/status 三方法；发布点=同事务内 status 翻转 + materialize
- [x] 一致性语义文档（分片事务边界/发布点/BUILDING 可见性/故障恢复/衔接）
- [x] 集成测试：begin→1/2 完成→published=false + status=BUILDING；全部完成→published=true + status=COMPLETED + nop_code_graph_metric 行存在；completeShard 幂等（重复调用不二次发布副作用）；重复 begin 重置 ledger
- [x] 全量回归 `./mvnw test -pl nop-code/nop-code-service -am` 绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 测试全绿且发布点原子性有断言（未全完成不发布；全完成同事务翻转+物化）
- [x] **端到端验证**（规则 #22）：begin → indexFileSet(分片) → completeShard ×N → published + 查询面 status 读回
- [x] **接线验证**（规则 #23）：发布真实触发 materialize（nop_code_graph_metric 行存在断言）
- [x] **无静默跳过**：completeShard 对未知 shardId 显式报错；status 非法迁移显式失败
- [x] ORM 生成物与模型一致（构建零 diff）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs/roadmap 同步

Status: completed
Targets: baseline §6.2、缺口矩阵、roadmap

- Item Types: `Fix`

- [x] baseline §6.2 集群行增注原子发布/一致性语义（指向 consistency 文档）
- [x] 缺口矩阵 N6.4 行 done；roadmap N6.4 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap/缺口矩阵/baseline/consistency 文档四处与 live 一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 原子发布语义落地（协调器 + ledger + 发布点同事务）且有测试钉住
- [x] 一致性语义文档完成（四类语义边界可操作）
- [x] 必要 focused verification 完成（`./mvnw test -pl nop-code/nop-code-service -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) ledger 真实持久化（DB 行断言非内存），(b) 发布点同事务性（未全完成不翻转/全完成必物化），(c) 无静默跳过
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- worker 编排协议、查询屏蔽产品裁定、多 worker 并发 completeShard 的行级竞争（唯一键 upsert 已保证安全，吞吐优化归后续）。

## Closure

Status Note: 原子发布语义落地（ledger + 发布点同事务翻转/物化）+ 一致性语义文档；独立 closure audit APPROVE（含同事务机制经框架 flush-on-commit 链路核实）后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_10697665）
- Evidence:
  - Phase 1/2 Exit Criteria 全 PASS；Closure Gates 27/27 勾选（checklist --strict exit 0）
  - Anti-Hollow 三项 PASS：(a) ledger 真实持久化（orm.xml:1051 + localDb DB 级断言）；(b) 发布点同事务（单 runInSession；未全完成不翻转；materialize 经 OrmTransactionListener flush-on-commit 与 ledger/翻转一并提交，无半发布）；(c) unknown shardId IAE 断言
  - ORM 生成物一致：_NopCodeShardLedger 字段逐一对应；全量 -am 构建（含 codegen regen）后 git status 零新增 diff
  - 实跑 PASS：nop-code-service -am 256/0（TestShardBuildCoordinator 3/3）
  - Deferred 分类检查 PASS：worker 编排/查询屏蔽/跨分片事务三裁定在 plan Non-Goals + consistency 文档 §6 记录且理由成立
  - 收尾备注（audit 建议，已记录）：协调器未注册 beans.xml、未暴露 ICodeIndexService——plan 已裁定"执行期按最小 diff 定"，唯一调用方（编排层）属后续集群 WI

Follow-up:

- (待填)
