# 2582 nop-git 安全加固：策略绕过封堵 + HTTP 服务访问控制

> Plan Status: active
> Last Reviewed: 2026-11-21
> Adversarial Review: 2026-11-21 独立子代理 1 轮通过（2 Major 已吸收：CLI 入口归一、push -u 裁定）
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

Status: completed
Targets: `nop-tools/nop-git/nop-git-core/src/main/java/io/nop/git/policy/`、`nop-tools/nop-git/nop-git-core/src/main/java/io/nop/git/core/JGitRepositoryService.java`、`nop-tools/nop-git/nop-git-core/src/main/java/io/nop/git/core/Wave4Ops.java`

- Item Types: `Fix`

- [x] refspec `+` 前缀归一为 force 语义：单点在 GitRequests flags/arg 分类层（CLI 通道 GitCompatCli.policyGuard 与 facade 通道 JGitRepositoryService.push 两个入口都必须被覆盖；`+main:main` 当前被分到 path() 而非 flags）
- [x] `--force-with-lease` 归一为 force 语义：单点在 GitRequests flags 归一处（:178-180 已折叠裸 --force-with-lease），不重复改 engine
- [x] push 无 refspec 时 targetBranch 用 currentBranch 回填（facade 与 CLI 两路）；通配 refspec 按最严格处理（deny 或全模式匹配）
- [x] 裁定（已定）：`push -u origin` 无 refspec 为合法操作——回填 currentBranch 后 push 成功，不再 NPE
- [x] review env 通道加固：审计记录标注 `reviewSource=env`，并限定单次请求作用域（或改外部授予，随实现裁定）
- [x] CLI operation 命名与 facade 归一表统一（switch→checkout、worktree remove→worktree-remove 等）

Exit Criteria:

- [x] 新增绕过回归钉测试：每条绕过路径一条（refspec `+` / force-with-lease / 无 refspec / 通配 refspec / CLI 别名旁路），deny 断言成立（core `GitPolicyBypassTest` 8 例 + cli `CliPolicyTest` 新增 6 例）
- [x] 变异探针：注释绕过封堵代码 → 对应钉子必红 → 恢复（恢复后 grep 确认可执行行存在；探针全过程与结果记录进本 plan Closure Evidence）
- [x] `push -u origin` 场景不再 NPE：push 成功且 upstream 正确写入（回归钉 `pushUpstreamWithoutRefspecSucceedsOnUnprotectedBranch`）
- [x] `nop-tools/nop-ib/bin/ib test -pl :nop-git-core,:nop-git-cli` 全绿（执行注记：nop-ib 差分编译器对本模块误报 success:false 且 Maven 回退假红——plan 2583 缺陷面；按主控许可退避 mvnw 模块级等价命令：core 34 / cli 126 全绿，后续 nop-ib 修复后可复核）
- [x] `ai-dev/logs/2026/11-21.md` 已更新

### Phase 2 - HTTP 服务访问控制

Status: planned
Targets: `nop-tools/nop-git/nop-git-server/`、`nop-git-ioc/`

- Item Types: `Fix | Decision`

- [x] Decision：默认绑定 loopback（127.0.0.1），显式配置才放开；owner 裁定记录进 design 04-api-integration-design.md（HTTP server 归属；若涉策略语义也同步 design 03）。注意 GitServerConfig 是 record，加字段需同步 of() 工厂、Main 构造点与既有测试（已同步 of(port,root)/of(port,root,token) 与既有测试）
- [x] GitServerConfig 增加访问控制（token/basic auth 或 read-only/read-write 开关，二选一实现）（裁定：bearer token，`Authorization: Bearer <token>` 401 拒绝；git 客户端经 `-c http.extraHeader` 传递）
- [x] pre-receive hook 覆盖面扩展到 `refs/heads/` 之外的 refs 改写（delete tags 等）（`refs/tags/<protected>` 删除与非快进改写拒绝；其余命名空间不受限）
- [x] server/Main.java 端口解析错误按 CLI 规范输出（裸 NumberFormatException 修复：`fatal: invalid port '<v>'` exit 128；新增 `--bind-all`/`--token` 选项）
- [x] GitIocFactory.policyPath 配置生效或显式移除该配置项（生效：新增 `DefaultGitPolicyEngine.load(root, policyFile, reviewOut)` + `GitPolicyLoader.loadLayers(root, userFile, projectFile)`；openRepository/initRepository 均按 policyPath 加载）

Exit Criteria:

- [x] HTTP 集成测试新增：未授权写拒绝、非 heads ref 保护拒绝、loopback 默认绑定断言；读侧行为断言跟随本 Phase 的访问控制裁定形态（token 401 拒绝 clone/push；授权 clone/push 成功）
- [x] 端到端验证：真实 git 客户端 push 到受保护分支被拒 + push 到普通分支在授权下成功（`protectedBranchRulesRejectDirectForceAndDelete` + `tokenAuthRejectsUnauthorizedWritesAndAcceptsAuthorizedOnes`）
- [x] design 文档（`ai-dev/design/nop-git/`）与 owner doc `docs-for-ai/03-modules/nop-git.md` 已同步（design 03 归一表/review 通道、design 04 §四、nop-git.md §四/§五）
- [x] `ib test -pl :nop-git-server,:nop-git-ioc` 全绿（执行注记同 Phase 1：nop-ib 缺陷退避 mvnw 模块级等价：server 6 / ioc 3 全绿）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] B1/B2 两条 Blocker 修复并有回归钉
- [x] in-scope 5 个 Major（NPE/命名漂移/env 自绕/policyPath/端口解析）全部修复
- [x] focused tests 全绿（core/cli/server/ioc）（nop-ib 缺陷退避 mvnw 模块级等价：34+126+6+3 = 169 tests / 0 failures）
- [x] 无 in-scope live defect 被降级为 follow-up
- [x] owner docs 已同步
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件（留待主控）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（留给主控）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-git-core --severity high` 退出码 0（留给主控）

## Deferred But Adjudicated

### expandPathspec 忽略 .gitignore/嵌套仓库（JGitRepositoryService.java:818-830）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 语义偏差属 git parity 范畴而非安全绕过面（add 范围偏大但不破坏策略引擎防护）；与 stash/clean 等 parity 项同批处理
- Successor Required: `yes`
- Successor Path: nop-git parity 修复计划（待立，收纳 stash apply/pop、clean requireForce、worktree prune locked、expandPathspec）

### audit 文件并发追加无锁、shared-state volatile 化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 单进程 CLI 场景为主，多进程并发审计为低频残留风险，独立登记
- Successor Required: `no`

## Non-Blocking Follow-ups

- stash apply/pop 语义区分、clean requireForce、worktree prune locked（parity 偏差，属 2587 或后续 parity 计划）

## Closure

Status Note: 实施完成（2026-11-21，实施子代理）。Phase 1/2 全部 item 与 Exit Criteria 勾选；focused tests 全绿（core 34 / cli 126 / server 6 / ioc 3，mvnw 模块级等价，理由见 Phase 1 Exit Criteria 注记）。工具命令类 gate（closure audit / check-plan-checklist / scan-hollow）留给主控。

Completed: 变异探针一轮（下）；design 03/04 与 owner doc 同步；daily log 已写。

Closure Audit Evidence:

- Reviewer / Agent: 实施子代理（本轮为变异探针 evidence 草稿；独立 closure audit 待主控另开子代理）
- Evidence:
  - 变异探针（refspec `+` force 归一单点，GitRequests.refspec）：
    1. 注释封堵 `flags.addAll(normalizeFlag(operation, "--force"))`（GitRequests.java:77 前缀 `// probe2582:`）；
    2. `mvnw test -pl nop-tools/nop-git/nop-git-core -Dtest=GitPolicyBypassTest` → **红**：`refspecPlusPrefixNormalizesToForceAndDenies` 失败（Tests run 8 / Failures 1），断言 `request.flags().contains("--force")` 必红，deny 断言失效；
    3. 恢复代码；`grep -n 'flags.addAll(normalizeFlag(operation, "--force"))'` 命中 GitRequests.java:77，`probe2582` 计数 0；
    4. 复跑全模块 → 绿（core 34 / 0 failures）。结论：钉子对绕过封堵代码变异敏感（killed），探针关闭。
  - 测试数字：core 34 / cli 126 / server 6 / ioc 3 = 169 tests, 0 failures, 0 errors（surefire 报告为准）。
  - 执行环境注记：nop-ib 服务（0.1.0）经历 model refresh（`nop-kernel/pom.xml` 变更引发全仓 dependency:list 重建）后仍对本模块差分编译误报 `success:false`（诊断仅 NOTE 级），且 model-stale Maven 回退 0ms 即退出 1（未真实执行）——属 plan 2583 登记的 nop-ib 正确性缺陷面；按主控指示退避 mvnw 模块级等价命令（`mvnw test -pl nop-tools/nop-git/nop-git-{core,cli,server,ioc}`，仓库本地 repo `.m2-repo`），其间由真实编译器捕获并被修复的真实错误：`Wave4Ops.currentBranchOrNull` private 访问（nop-ib 差分编译曾将其掩盖为 NOTE-only 假失败）。
