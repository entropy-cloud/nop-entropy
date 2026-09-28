# 20 N6.3 集群索引构建——分发与工作区

> Plan Status: completed(独立 closure audit APPROVE，agent_c25fe12f)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N6.3；N6.1 裁定（plan 18）；live 核对（2026-09-28）
> Related: N6.4（原子发布与一致性，本 plan 不触及）；N2.4（git 命令基建先例：gitOutput/ref 校验/超时）

## Purpose

为集群化索引构建提供三块基座：**确定性分片计划**（ShardPlanner）、**repo checkout 工作区管理**（WorkspaceManager，基于 git，复用 N2.4 基建模式）、**分片执行**（`indexFileSet`——对显式文件子集的一次事务内索引）。使"多个 worker 各取一个分片、在工作区副本上分别构建、合并归 N6.4"成为可测试的现实路径。

## Current Baseline（live 核对 2026-09-28）

- 索引构建当前仅整目录：`indexDirectory(indexId, vfsPath, filePattern)`（VFS 扫描 + 全量 persist）与 `indexFile`（单文件）。无文件子集批量索引、无分片概念、无工作区管理。
- git 命令基建（CodeIndexService 私有）：`gitOutput(workdir, args...)`（30s 超时）、`validateGitRef`（GIT_REF_PATTERN 白名单）、`requireRepoRoot`——均为 private，工作区管理需复用其模式（提取或复制，执行期按最小 diff 定）。
- `allowedLocalRoot` 安全约束已存在（validateLocalPath 校验本地路径白名单）——工作区 root 需同款约束。
- `ICodeIndexService` 接口方法新增不影响既有不变量测试（IDEMPOTENCE_TABLE 为固定集合）。
- 序列化：`JsonTool.stringify/parse` 为平台惯例。

## Goals

- **G1 分片计划**：`IndexShardPlanner`（nop-code-core，纯计算）——`plan(List<String> sortedRelativePaths, int shardCount)` → 确定性、互斥、完备的分片（稳定哈希：路径 hash mod N；输出按输入排序分片内有序）；`shardCount<=0`、空输入显式处理。
- **G2 工作区管理**：`ClusterWorkspaceManager`（nop-code-service）——`prepare(repoPath, revision)`：workspace root（`@InjectValue nop.code.cluster.workspace-root`，默认 `<user.dir>/_tmp/...`？不——默认 target 外的配置路径，空值时显式报错）下 `indexId 无关` 的 `<root>/<repoName>/<revision>/` 工作区：已存在且 HEAD==revision 复用；存在但版本不符 → fetch+checkout；不存在 → clone。复用 GIT_REF_PATTERN 校验 revision；workspace root 经 canonical 路径校验。返回 `WorkspaceInfo(workspacePath, headCommit, reused)`。
- **G3 分片执行**：`ICodeIndexService.indexFileSet(indexId, vfsPath, List<String> relativePaths)`——一次事务内对显式子集 delete-before-reindex + persist（复用 `persistSingleFileInSession`/既有批量模式），返回分析结果数。
- **G4 测试**：分片确定性/互斥/完备/分布；工作区复用/切换 revision/非法 ref 显式失败（临时本地 git 仓）；分片执行子集落地 + 两分片并集 ≡ 全量索引（符号集等价）。

## Non-Goals

- 不做分布式调度/消息队列/worker 协议（执行编排归 N6.4 原子发布与一致性及其后）。
- 不做 GraphQL 暴露（service API 层交付；暴露随集群功能整体定型后统一裁定）。
- 不做远端 git（HTTP/SSH clone）——本地路径 repo 已覆盖测试与同机分发形态；远端认证归 N6.5（多租户隔离，ask-first）。

## Scope

### In Scope

- nop-code-core：`IndexShardPlanner` + shard DTO。
- nop-code-service：`ClusterWorkspaceManager`、`indexFileSet`（接口 + 实现）、workspace root 配置项。
- 测试（core 纯计算 + service 容器级）。
- owner docs：baseline §6.2 集群行增注、缺口矩阵、roadmap。

### Out Of Scope

- N6.4 原子发布/一致性；N6.5 多租户/凭据；远端 git 认证。

## Execution Plan

### Phase 1 - 分片计划 + 工作区 + 分片执行 + 测试（G1-G4）

Status: completed
Targets: nop-code-core、nop-code-service、测试

- Item Types: `Fix`（roadmap 登记的实现缺口）

- [x] `IndexShardPlanner.plan`：确定性（同输入同输出）、互斥（无重复路径）、完备（并集=全输入）、空输入→空分片、shardCount<=0 抛 IAE
- [x] `ClusterWorkspaceManager.prepare(repoPath, revision)`：clone/fetch+checkout/复用三分支；ref 白名单校验；workspace root 显式配置（未配置显式报错）；canonical 路径防逃逸
- [x] `ICodeIndexService.indexFileSet` + 实现：单事务、delete-before-reindex、分析器未注册的文件跳过计数（显式返回值区分）
- [x] core 单测（分片性质）+ service 容器测试（临时本地 git 仓：工作区三分支；indexFileSet 子集落地；两分片并集 vs indexDirectory 全量符号集等价）
- [x] 全量回归 `./mvnw test -pl nop-code/nop-code-service -am` 绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全部新测试绿；分片三性质（确定性/互斥/完备）有显式断言
- [x] **端到端验证**（规则 #22）：本地 git 仓 → prepare → plan → 两分片各自 indexFileSet → DB 符号集 ≡ indexDirectory 全量（并集等价断言）
- [x] **无静默跳过**：未配置 workspace root、非法 revision、shardCount<=0、分析器缺失均显式失败或显式计数返回
- [x] owner docs：baseline §6.2 集群行增注（Phase 2 一并裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs/roadmap 同步

Status: completed
Targets: baseline、缺口矩阵、roadmap

- Item Types: `Fix`

- [x] baseline §6.2 集群行增注（分发/工作区/分片基座落地；执行编排归 N6.4+）
- [x] 缺口矩阵 N6.3 行 done；roadmap N6.3 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap/缺口矩阵/baseline 三处与 live 一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 分片/工作区/分片执行三基座落地且有测试钉住
- [x] 并集等价证明完成（分片执行 ≡ 全量索引）
- [x] 必要 focused verification 完成（`./mvnw test -pl nop-code/nop-code-service -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) 工作区真实 clone/checkout（git 状态断言非仅目录存在），(b) 端到端并集等价断言真实，(c) 显式失败路径（root 未配置/非法 ref）有断言
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无——in-scope 无延期项）

## Non-Blocking Follow-ups

- 分布式执行编排（worker 协议/调度/进度聚合）与原子发布（N6.4）；GraphQL 暴露；远端 git 认证（随 N6.5）。Classification: `moved to explicit successor ownership`（N6.4/后续集群 WI）。

## Closure

Status Note: 分片/工作区/分片执行三基座落地并有测试钉住（含并集等价端到端与幂等门禁归类）；独立 closure audit APPROVE 后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_c25fe12f）
- Evidence:
  - Phase 1/2 Exit Criteria 5/5+4/4 PASS；Closure Gates 9/9 PASS（工具门禁 exit 0 实跑：checklist 26/26、hollow 0、doc-links 0 errors）
  - Anti-Hollow 三项 PASS：(a) 工作区真实 clone（.git 目录断言 + reused 标志语义）；(b) 并集等价真实（两分片 indexFileSet 后 DB app.* 符号集 == indexDirectory 全量）；(c) 显式失败路径断言（root null IAE/路径穿越 IAE/未知分析器 skip 上报/core shardCount 0/-1 IAE）
  - 关键实现抽查 PASS：indexFileSet 单事务 + delete-before-reindex + N3.1-s 解析（L455）；VFS file: 前缀（N2.4 怪癖）；幂等门禁归类正确（IDEMPOTENCE_TABLE + verify 分支真实双调用断言）
  - 实跑 PASS：core 141/0；service -am 253/0；TestClusterShardingE2E 2/2；TestNopCodeIndexIdempotencyInvariant 10/10
  - Deferred 分类检查 PASS：编排/原子发布→N6.4 successor、GraphQL→集群定型后、远端 git 认证→N6.5（ask-first）

Follow-up:

- (待填)
