# 2582 nop-git 安全加固：策略绕过封堵 + HTTP 服务访问控制

> Plan Status: draft
> Last Reviewed: 2026-11-21
> Source: `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md`（B1/B2 + nop-git Major/Minor 群）
> Related: `ai-dev/plans/2578-nop-git-wave3-policy-engine.md`、`2580-nop-git-wave5-http-server.md`

## Purpose

修复 nop-git 策略引擎的 force-push/直推绕过面与 HTTP 服务的无认证暴露面，使"守护 AI 的 git 操作"这一核心威胁模型真实成立。

## Current Baseline

- `DefaultGitPolicyEngine.java:204` 仅以 `--force` flag 判定 force push；refspec `+` 前缀、`--force-with-lease`、无 refspec、通配 refspec 四条路径均绕过保护分支 deny。
- `NopGitHttpServer.java:44-55` 显式启用 receive-pack，`new Server(config.port())` 默认绑所有网卡，`GitServerConfig` 无凭据字段；pre-receive hook 只拦 `refs/heads/`。
- 存在的 Major：`push -u` 无 refspec NPE（Wave4Ops.java:244-248）、CLI/facade operation 命名漂移（GitCompatCli.java:49-50）、review env 通道可自绕（DefaultGitPolicyEngine.java:251-252）。
- 现有测试全绿（core/cli/server 147+），策略引擎有 14 个引擎级测试但无绕过用例。

## Goals

- 四条 force/直推绕过路径全部被 deny，并有变异探针级回归钉。
- HTTP 服务默认 loopback + 显式访问控制（读/写开关或 token），非保护分支外的 refs 改写纳入防护。
- 5 个相关 Major（NPE、命名漂移、env 自绕）落地修复。

## Non-Goals

- 不重构策略引擎整体架构；不做 native git 二进制托管；不改 CLI 输出格式契约。

## Scope

### In Scope

- nop-git-core（policy 包、JGitRepositoryService refspec 解析、Wave4Ops）
- nop-git-cli（operation 归一表复用）
- nop-git-server（绑定、鉴权、hook 覆盖面）
- nop-git-ioc（policyPath 配置失效，随带修复）

### Out Of Scope

- stash/clean/worktree 等语义偏差修复（归 2587 或独立 parity 计划）；审计文件并发锁优化（watch-only）。

## Execution Plan

### Phase 1 - 策略绕过封堵

Status: planned
Targets: `nop-tools/nop-git/nop-git-core/src/main/java/io/nop/git/policy/`、`nop-tools/nop-git/nop-git-core/src/main/java/io/nop/git/core/JGitRepositoryService.java`、`nop-tools/nop-git/nop-git-core/src/main/java/io/nop/git/core/Wave4Ops.java`

- Item Types: `Fix`

- [ ] refspec `+` 前缀解析并归一为 force 语义进入 request.flags()
- [ ] `--force-with-lease` 归一为 force 语义（DefaultGitPolicyEngine + GitRequests）
- [ ] push 无 refspec 时 targetBranch 用 currentBranch 回填；通配 refspec 按最严格处理（deny 或全模式匹配）
- [ ] 修复 `push -u origin` 无 refspec NPE（Wave4Ops.java:244-248）
- [ ] review env 通道加固：审计记录标注 `reviewSource=env`，并限定单次请求作用域（或改外部授予，随实现裁定）
- [ ] CLI operation 命名与 facade 归一表统一（switch→checkout、worktree remove→worktree-remove 等）

Exit Criteria:

- [ ] 新增绕过回归钉测试：每条绕过路径一条（refspec `+` / force-with-lease / 无 refspec / 通配 refspec / CLI 别名旁路），deny 断言成立
- [ ] 变异探针：注释绕过封堵代码 → 对应钉子必红 → 恢复（恢复后 grep 断言可执行行存在）
- [ ] `push -u origin` 场景不再 NPE，返回 native 风格错误
- [ ] `nop-tools/nop-ib/bin/ib test -pl :nop-git-core,:nop-git-cli` 全绿
- [ ] `ai-dev/logs/2026/11-21.md` 已更新

### Phase 2 - HTTP 服务访问控制

Status: planned
Targets: `nop-tools/nop-git/nop-git-server/`、`nop-git-ioc/`

- Item Types: `Fix | Decision`

- [ ] Decision：默认绑定 loopback（127.0.0.1），显式配置才放开；owner 裁定记录进 design 03（若部署语义变化）
- [ ] GitServerConfig 增加访问控制（token/basic auth 或 read-only/read-write 开关，二选一实现）
- [ ] pre-receive hook 覆盖面扩展到 `refs/heads/` 之外的 refs 改写（delete tags 等）
- [ ] server/Main.java 端口解析错误按 CLI 规范输出（裸 NumberFormatException 修复）
- [ ] GitIocFactory.policyPath 配置生效或显式移除该配置项

Exit Criteria:

- [ ] HTTP 集成测试新增：未授权写拒绝、未授权读按裁定断言、非 heads ref 保护拒绝、loopback 默认绑定断言
- [ ] 端到端验证：真实 git 客户端 push 到受保护分支被拒 + push 到普通分支在授权下成功
- [ ] design 文档（`ai-dev/design/nop-git/`）与 owner doc `docs-for-ai/03-modules/nop-git.md` 已同步
- [ ] `ib test -pl :nop-git-server,:nop-git-ioc` 全绿
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] B1/B2 两条 Blocker 修复并有回归钉
- [ ] in-scope 5 个 Major（NPE/命名漂移/env 自绕/policyPath/端口解析）全部修复
- [ ] focused tests 全绿（core/cli/server/ioc）
- [ ] 无 in-scope live defect 被降级为 follow-up
- [ ] owner docs 已同步
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-git-core --severity high` 退出码 0

## Deferred But Adjudicated

### audit 文件并发追加无锁、shared-state volatile 化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 单进程 CLI 场景为主，多进程并发审计为低频残留风险，独立登记
- Successor Required: `no`

## Non-Blocking Follow-ups

- stash apply/pop 语义区分、clean requireForce、worktree prune locked（parity 偏差，属 2587 或后续 parity 计划）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
