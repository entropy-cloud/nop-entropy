# 2578 nop-git Wave 3: 权限策略引擎

> Plan Status: completed
> Last Reviewed: 2026-11-21
> Source: `ai-dev/backlog/nop-git-roadmap.md` Wave 3 (items 12-17)；`ai-dev/design/nop-git/03-policy-engine-design.md`、`01-architecture-baseline.md`
> Related: plan 2576（Wave 1）、plan 2577（Wave 2）已完成

## Purpose

落地 AI Git 操作权限策略引擎（roadmap items 12–17）：`IGitPolicyEngine` 对归一化 `IGitOperationRequest` 做 allow/deny/review 三态五维裁决，observe/enforce 两模式 + JSON Lines 审计，CLI 与 API 两个拦截点接入同一引擎实例，deny 不可绕过。

## Current Baseline

- Wave 1/2 已落地：门面 + CLI 八命令 + parity/golden 测试体系（plan 2576/2577）。CLI 当前直调门面，无裁决层。
- design 03：五维规则（操作/选项/分支/路径/远程）+ scopes 覆盖；deny 永远优先；`observe`（只记录+hypothetical_effect）/`enforce` 两模式；review 通道（交互确认 / `NOP_GIT_REVIEW_APPROVED=1` / 超时降级 deny，默认 30s）；审计 JSON Lines（`.nop-git/audit.log`）；分层配置 `.nop-git/policy.yaml`（项目）→ `~/.nop-git/policy.yaml`（用户）→ 内置默认，strict 合并；default_effect 保守默认 deny；选项归一化（-f→--force 等同族表）发生在评估前。
- 依赖裁定：nop-git-core 零仓库内部依赖（plan 2576）。**YAML 解析依赖需新引入**（snakeyaml 单一职责库，与 JGit 同类裁定），在 nop-git-bom 声明版本。
- gap：策略引擎全部待建；门面/CLI 拦截点待接。

## Goals

- `IGitOperationRequest`（子命令、归一化 flags、目标分支、路径集合、远程名/URL、原始命令行）+ 三态裁决枚举 + `IGitPolicyEngine` 接口与默认实现（`DefaultGitPolicyEngine`）。
- 分层 YAML 配置加载（项目/用户/内置默认）+ strict 合并（deny 不被低层 allow 覆盖）。
- 五维评估（操作级、选项级含同族 flag 归一化、分支级 protected_branches glob、路径级 deny/review paths、远程级）+ scopes 覆盖（最高优先级 deny）——评估算法按 design 03 §三伪代码。
- review 通道：交互式 stdin 确认 / `NOP_GIT_REVIEW_APPROVED=1` / `review_timeout_seconds` 超时降级 deny。
- observe/enforce 模式 + JSON Lines 审计（timestamp、repoPath、rawCommand、normalizedOperation、flags、paths、remote、matchedRuleIds、verdict、hypotheticalEffect（仅 observe）、outcome）。
- 两个拦截点同一引擎：CLI（argv 解析后）+ API 门面（每公开方法内）。deny 输出 `fatal: policy denied: <原因>`（CLI 退出码 1）。
- 全覆盖测试：每维度 × allow/deny/review × observe/enforce + 绕过尝试（flag 别名、路径遍历、refspec 变体）。

## Non-Goals

- 策略运行时编辑 UI（non-goal 见 design 00 §四）。
- Wave 4+ 的 P2/P3 子命令裁决面（引擎按请求模型工作，新子命令接入随 Wave 4 扩表）。
- **E2E 面边界（审查裁定 #1/#2）**：push/reset/branch -d/remote 等操作当前无 CLI/门面入口（Wave 4+ 扩表），其裁决只到**引擎级单测**；入口级 E2E 仅覆盖现存八命令/门面方法可达子集。"deny 不可绕过"在本 Wave 的成立面 = 现存操作面。
- `--version`/`rev-parse` 等纯只读探测以外的 help 类命令裁决（只读查询仍经裁决，命中 default_effect=deny 时由 operations.allow 显式放行——契约以 design 03 为准）。

## Scope

### In Scope

- core：`io.nop.git.policy`（request/verdict/engine/loader/rules/audit）+ `io.nop.git.api` 扩展（open/init 带 policy 参数的重载）。
- CLI：argv→IGitOperationRequest 解析（选项归一化表）、拦截点、deny 输出。
- snakeyaml 2.2 依赖引入（bom 唯一版本声明；本地 m2 已有 2.2-2.7）。支柱测试只禁 org.eclipse.jgit，不受影响。
- 测试：五维×三态×两模式矩阵、绕过尝试、审计内容断言、E2E（CLI 入口→裁决→审计文件出口 + API 入口→裁决→异常出口）。

### Out Of Scope

- HTTP 服务端保护（Wave 5 PreReceiveHook）。
- nop-ai-shell 接入评估（Wave 6 item 30）。

## Execution Plan

### Phase 1 - 请求模型与引擎骨架

Status: completed
Targets: `nop-git-core`（policy 包）

- Item Types: `Feature`

- [x] `IGitOperationRequest` + `GitOperationRequest`（builder）、`GitVerdict`（ALLOW/DENY/REVIEW + matchedRuleIds + reason）、`IGitPolicyEngine` 接口
- [x] `DefaultGitPolicyEngine`：分层配置加载 + strict 合并 + evaluate 主流程骨架（先只做操作级 + default_effect）。**评估语义钉死（审查 #5）**：verdict 偏序 DENY>REVIEW>ALLOW；scope 命中但非 deny 时 fall-through 继续；review 累积发生在 allow 白名单之前（allow 命中但已累积 review → 最终 REVIEW，有意语义）
- [x] snakeyaml **2.2** 依赖（bom 唯一版本声明）+ 内置默认策略。**引擎非激活裁定（审查 #9）**：项目与用户层均无 policy.yaml 时引擎处于 inactive——不拦截、不写审计文件（保证 Wave 2 parity/golden 与 fixture 仓库零污染）；存在任一 policy.yaml 时按其 mode（默认 observe）激活
- [x] 配置模型类（operations/option_rules/protected_branches/path_rules/remote_rules/scopes/mode/audit_log/review_timeout/git_version_string）

Exit Criteria:

- [x] 配置加载 + 操作级裁决单测（三层合并优先级、deny 优先）
- [x] 非 yaml 畸形配置显式报错（NopGitException，非静默跳过）
- [x] `ib test` 绿；`ai-dev/logs/` 已更新

### Phase 2 - 五维评估 + 选项归一化

Status: completed
Targets: `nop-git-core`

- Item Types: `Feature`

- [x] 选项级（deny_flags/review_flags + design 03 归一化表：-f/--force、--force-with-lease[=x]、-fd→--force+--directories、-D→--delete --force（branch 上下文））
- [x] 分支级（protected_branches patterns glob；push refspec 目标解析、branch -d/-D 目标解析）——引擎级单测（无 CLI 入口，见 Deferred）
- [x] 路径级（deny_paths/review_paths glob；**/.env* 类双星模式）。**涉及路径集合裁定（审查 #6）**：仅 add/rm 展开用户 pathspec 到 worktree 实际匹配集（`add -A` 视为全工作树命中）；路径先 getCanonicalPath 归一再 glob（封 ../ 遍历）
- [x] 远程级（allow_remotes/deny_protocols/allow_url_patterns）——引擎级单测（同上）
- [x] scopes 覆盖（gitdir: 前缀语义，最高优先级 deny）
- [x] evaluate 全流程按 design 03 §三顺序：scope→操作→选项→分支→路径→远程→review 累积→allow 白名单→default_effect

Exit Criteria:

- [x] 每维度 × allow/deny/review 单测
- [x] 绕过尝试测试（flag 别名归一、路径 ../ 遍历规范化、refspec 变体）
- [x] `ib test` 绿；`ai-dev/logs/` 已更新

### Phase 3 - review 通道 + 审计 + observe/enforce

Status: completed
Targets: `nop-git-core`

- Item Types: `Feature`

- [x] review：交互 stdin 确认（yes） / `NOP_GIT_REVIEW_APPROVED=1` / 超时降级 deny（timeout 可配，测试一律配 0-1 秒快超时防挂起，审查 #8）；input/clock 可注入
- [x] `GitAuditLogger` JSON Lines（字段契约见 Goals）。**outcome 回填协议（审查 #7）**：单行一次写入、不 pending 不改写；由入口 wrapper 在操作完成后同步回填（CLI=命令分发处，API=门面方法公共包装）；进程被杀留无 outcome 字段的行（可接受、文档化）；E2E 行数断言只数含 outcome 的完整行
- [x] observe 模式全放行 + hypothetical_effect 记录；enforce 真实拦截

Exit Criteria:

- [x] review 三路径单测（确认/预批/超时 deny）
- [x] 审计文件内容断言（每字段；observe 模式含 hypotheticalEffect）
- [x] observe/enforce 行为矩阵测试
- [x] `ib test` 绿；`ai-dev/logs/` 已更新

### Phase 4 - 双拦截点接入 + E2E

Status: completed
Targets: `nop-git-core`、`nop-git-cli`

- Item Types: `Feature`

- [x] API：`IGitRepositoryService.open/init` 带 policy 重载；每公开方法执行前裁决，deny 抛 NopGitException（含命中规则原因）
- [x] CLI：argv → IGitOperationRequest（含选项归一化）→ 裁决。归一化/提取逻辑收敛在 core 共享 request-builder（审查 #10），CLI 与门面只喂原始输入，禁止两套实现 → deny 输出 `fatal: policy denied: <原因>` 退出 1；audit outcome 回填
- [x] 同一引擎实例贯穿（wiring：E2E 断言 CLI 与 API 路径审计落到同一 audit.log 且引擎配置同源）
- [x] E2E：临时仓库写 policy.yaml（deny push/review reset --hard）→ CLI 调用 → 输出/退出码/审计文件断言；API 调用 → 异常/审计断言

Exit Criteria:

- [x] 端到端测试（CLI 入口→审计出口；API 入口→审计出口）绿
- [x] **接线验证**：DefaultGitPolicyEngine 确实被两条路径运行时调用（审计文件行数断言）
- [x] parity 42 例 + golden 41 例回归不红（默认内置 observe 模式不改变既有输出）
- [x] `ib test` 全模块绿；`ai-dev/logs/` 已更新

## Closure Gates

- [x] items 12-17 全部落地且测试覆盖：**引擎级**五维×三态×两模式全矩阵 + 绕过尝试 + 审计断言；**入口级** E2E 覆盖现存八命令/门面可达子集（边界见 Deferred）
- [x] 端到端：CLI 与 API 两入口 → 引擎裁决 → 审计出口全链路绿（Anti-Hollow：调用链运行时连通）
- [x] 无静默跳过：deny 路径显式非零退出/抛异常；畸形配置显式报错
- [x] deny 输出 `fatal: policy denied:` 契约测试
- [x] roadmap 状态块更新；owner doc（design 03 若有落地修订）reconcile
- [x] 独立子 agent closure-audit 完成并写入证据
- [x] `ib test` 全模块绿；check-doc-links / check-plan-checklist / scan-hollow 退出码 0
- [x] 代码规范：imports 分组、英文错误消息

## Deferred But Adjudicated

### push/reset/clean/branch -d 等操作的 CLI/API 入口级拦截

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 这些子命令本身在 Wave 4 才落地（roadmap items 18-24）；引擎已对其请求形态完成五维单测裁决，入口拦截随子命令扩表自然接入，不构成现存操作面的策略缺口。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-git-roadmap.md` Wave 4（plan 2579+）

## Non-Blocking Follow-ups

- 配置热重载（当前每次引擎构造时加载一次；热重载为优化项）。
- policy.yaml 的 JSON Schema 校验提示（畸形键名 warning 增强）。

## Closure

Status Note: Wave 3（roadmap items 12-17）全部落地：策略引擎五维评估（语义钉死）、review 三通道、observe/enforce + JSON Lines 审计、CLI+门面双拦截点同一引擎实例；引擎级五维×三态×两模式矩阵 + 绕过尝试 14 例 + CLI 入口 E2E 5 例全绿；Wave 2 parity/golden 回归不红（引擎非激活裁定）。总计 121 测试全绿，hollow 扫描 0 findings，checklist 39/39。push/reset 等无入口子命令的入口级拦截按 Deferred 归 Wave 4 successor ownership。
Completed: 2026-11-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_b4799000-bf62-4ae6-ba3b-9d468b72271a）
- Audit Session: agent_b4799000-bf62-4ae6-ba3b-9d468b72271a
- Evidence:
  - 独立复跑 mvn test：121 run / 0 fail / 0 skip
  - 独立 E2E 冒烟（自建临时仓库）：enforce deny add → exit 1 + `fatal: policy denied:` + audit outcome denied；observe → exit 0 + hypotheticalEffect DENY + outcome ok；无 policy.yaml → 零拦截零审计；deny_paths **/.env* basename/../遍历/-A 三形态全拒
  - Anti-Hollow：policy 包 8 文件 + guarded + policyGuard 无空壳；同一引擎实例接线实证（CliContext.openRepo → IGitRepositoryService.open(workDir, policy)）；审计单行（CLI allow 不审计、facade 一次）
  - 一致性：roadmap items 12-17 + Milestone done (plan 2578)；daily log Wave 3 段一致；design 03 reconcile 四处核对；check-plan-checklist --strict exit 0（39/39）、check-doc-links exit 0、scan-hollow exit 0
  - Deferred 分类检查：入口级拦截归 Wave 4 successor ownership 成立，无降级
  - 审计修正已落地：Phase 3 Status → completed；Wave 3 成果随本提交落库
