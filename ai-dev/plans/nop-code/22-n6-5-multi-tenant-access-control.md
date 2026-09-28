# 22 N6.5 多租户隔离与访问控制

> Plan Status: completed(Ask-First 用户确认；独立 closure audit REVISE 项修复后通过，agent_d5577551)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N6.5；**用户已于 2026-09-28 明确授权执行（ask-first 人工确认，见会话记录）**；live 核对（2026-09-28）
> Related: N6.3（ClusterWorkspaceManager 前序）、N2.4（validateLocalPath/git 基建）

## Purpose

为 nop-code 提供多租户/访问控制基座：**per-index 访问策略 SPI**（源码读 + 写变更两类检查点）、**per-index allowedLocalRoot 细化**、**私有仓库凭据解析 SPI**（git 拉取认证）。

## Ask-First 边界裁定（人工已确认）

- **不修改** `nop-code-web` 的 action-auth 资源树与权限模型（不新增/不删除权限点）——权限模型边界保持不变。
- 访问控制以 **service 层策略 SPI**（`IndexAccessPolicy`）承载：检查点在 `ICodeIndexService` 公共入口；默认实现 Permissive（全放行）= 向后兼容零行为变化；部署方注入自有策略对接租户/权限系统。
- 该裁定意味着：本项交付**机制**而非具体租户策略；action-auth 资源面零改动是本裁定的核心承诺。

## Current Baseline（live 核对 2026-09-28）

- `validateLocalPath(path)`：全局 `allowedLocalRoot` 单值（`@InjectValue`），不区分 index；调用点 indexDirectory（L326）/triggerIncrementalIndex（L988）。
- 源码暴露面：`getFileSourceCode`/`getSymbolSourceCode`/`showSymbolSource`/`getFiles`/`getFile`/`getFileTree`/`getFileOutline`/`getModuleDigest`/`getPublicSurface`/`searchCode` 等读方法 + 全部写方法（indexDirectory/indexFile/indexFileSet/triggerIncrementalIndex/triggerRebuildFromCommit/batchDelete*/deleteIndex/triggerFullIndex 等）均在 `ICodeIndexService` 单一接口后——**天然检查点咽喉**。
- `ClusterWorkspaceManager.prepare(repoPath, revision)`：无凭据参数（N6.3 显式将远端 git 认证 deferred 到本项）。
- action-auth 资源树：`nop-code-web/_vfs/nop/code/auth/`（站点/菜单/FNPT 权限点），本项零改动。

## Goals

- **G1 访问策略 SPI**：`IndexAccessPolicy`（nop-code-service api 或 cluster 包）：`checkReadAccess(indexId)` / `checkWriteAccess(indexId)` / `getAllowedLocalRoot(indexId)`（返回 null 回退全局）。默认 `PermissiveIndexAccessPolicy`。`CodeIndexService` 持有策略（`@Inject @Nullable` setter），在**全部 ICodeIndexService 公共方法入口**插入检查点（读方法组 checkReadAccess、写方法组 checkWriteAccess；null indexId 的方法跳过——如 deleteIndex(indexId) 有 indexId 全覆盖，getFilesPage 等均有 indexId）。
- **G2 per-index allowedLocalRoot**：`validateLocalPath(indexId, path)` —— 策略返回非空 root 时优先于全局 root；全调用点传 indexId。
- **G3 私有仓库凭据**：`GitCredentialResolver` SPI（cluster 包）：`resolve(repoPath)` → username/password 或 null；`ClusterWorkspaceManager.prepare` 增加 resolver 注入，git clone/fetch 经 `-c http.extraHeader` 或 askpass 形态传递，**凭据零日志**（gitOutput 的输出已含命令行——凭据不得出现在命令行参数中，采用 env 传递 `GIT_ASKPASS`/`GIT_HTTP_LOWAUTH` 形态，执行期定）。默认 resolver 返回 null（本地仓零影响）。
- **G4 测试**：策略 deny-one 钉住读写检查点生效（ deny 的 index 调用任一读写方法显式抛错）；per-index root 覆盖全局（deny 路径/allow 路径对比）；凭据解析（stub resolver → 命令构造包含凭据通道且日志零泄漏断言）。

## Non-Goals

- 不修改 action-auth 资源树/权限模型（Ask-First 裁定）。
- 不内置具体租户策略实现（部署方对接）。
- 不做远端 git 真实认证 E2E（无测试凭据源；SPI + 命令构造钉住）。

## Scope

### In Scope

- `IndexAccessPolicy` SPI + Permissive 默认 + CodeIndexService 检查点；`validateLocalPath` per-index 化。
- `GitCredentialResolver` SPI + ClusterWorkspaceManager 集成（env 传递、零日志）。
- 测试 + owner docs（baseline §4.5/§6.2 访问控制增注、缺口矩阵、roadmap）。

### Out Of Scope

- action-auth 资源树变更；租户策略实现；远端 git 认证 E2E。

## Execution Plan

### Phase 1 - SPI/检查点/凭据 + 测试（G1-G4）

Status: completed
Targets: nop-code-service（api/cluster/impl）、测试

- Item Types: `Fix`（roadmap 登记的实现缺口）

- [x] `IndexAccessPolicy` SPI + Permissive 默认实现 + CodeIndexService setter（@Nullable）+ 全公共方法检查点插入（读组/写组）
- [x] `validateLocalPath(indexId, path)` per-index root 优先、全局回退；调用点更新
- [x] `GitCredentialResolver` SPI + WorkspaceManager 集成（env 传递凭据、命令行与日志零泄漏）
- [x] 测试：deny-one 策略下读/写/本地路径校验/凭据解析与零泄漏
- [x] 全量回归 `./mvnw test -pl nop-code/nop-code-service -am` 绿（默认 Permissive 零行为变化）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 测试全绿：检查点生效（deny 显式失败）、per-index root 优先级、凭据零泄漏断言、默认策略零回归
- [x] **接线验证**（规则 #23）：策略检查点在容器装配下被公共方法真实调用（deny 断言即证明）
- [x] **无静默跳过**：deny 抛错非静默；凭据不落任何日志有断言
- [x] action-auth 资源树零改动（git diff 证明）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs/roadmap 同步

Status: completed
Targets: baseline、缺口矩阵、roadmap

- Item Types: `Fix`

- [x] baseline 访问控制增注（SPI 机制 + Ask-First 裁定记录：action-auth 零改动）
- [x] 缺口矩阵 N6.5 行 done；roadmap N6.5 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap/缺口矩阵/baseline 三处与 live 一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] per-index 访问策略/本地路径细化/凭据解析三面落地且有测试钉住
- [x] action-auth 资源树零改动（Ask-First 裁定履行）
- [x] 必要 focused verification 完成（`./mvnw test -pl nop-code/nop-code-service -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) 检查点真实拦截（deny 断言），(b) 凭据零泄漏断言真实，(c) 默认策略零回归
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 具体租户策略实现（对接 nop-auth 角色体系）属部署集成，机制已就绪。

## Closure

Status Note: per-index 访问策略/本地路径细化/凭据解析三面落地且有测试钉住；Ask-First 裁定（action-auth 零改动）履行并经审计核实；首轮 audit REVISE 项（覆盖面缺口/凭据测试/文档同步）全部修复后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_d5577551；首轮 REVISE 4 项全部修复后复审通过）
- Evidence:
  - 首轮 REVISE 修复：(1) 16 个遗漏读入口补 checkReadAccess（diffGraph 双 indexId 各查）+ materializeGraphMetrics 归写检查点——ICodeIndexService 全部带 indexId 公共入口已覆盖；(2) 凭据零泄漏测试落地（env 含凭据/命令行不含，applyCredential 提取为包可见）；(3) baseline 增注核实 + docs-for-ai/03-modules/nop-code.md 访问控制段补齐 + 矩阵 tdone 笔误修复；(4) 全部交付物随本提交入库
  - NOP_GIT_* 通道惰性语义如实标注（javadoc + 文档）：git 原生不消费该约定，认证由部署方 credential helper 消费——零泄漏保证不受影响
  - Anti-Hollow 三项 PASS：deny 拦截（写/读入口断言）、凭据零泄漏断言、默认策略零回归
  - 实跑 PASS：nop-code-service -am 全量绿
  - action-auth 零改动：git diff 核实 nop-code-web 无变更
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（收口后复跑）

Follow-up:

- (待填)
