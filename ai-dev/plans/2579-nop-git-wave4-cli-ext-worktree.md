# 2579 nop-git Wave 4: CLI 扩展 + Worktree 一等支持（P2/P3）

> Plan Status: completed
> Last Reviewed: 2026-11-21
> Source: `ai-dev/backlog/nop-git-roadmap.md` Wave 4 (items 18-25)；`ai-dev/design/nop-git/05-worktree-design.md`、`02-cli-compat-design.md`
> Related: plan 2576/2577/2578 已完成

## Purpose

落地 Wave 4 全部 8 项（roadmap items 18–25）：P2 子命令（checkout/switch、reset、clean、restore/rm/mv、stash 子集）、worktree 一等支持（API listWorktrees/addWorktree/removeWorktree + CLI worktree add/list/remove/prune，元数据自建逐字节兼容真实 git，双向对照测试钉死）、P3 本地（init defaultBranch、remote -v/add/set-url）、P3 网络（push/fetch/pull/clone refspec 子集，测试用本地 file:// 远程）、P3 历史改写（merge/rebase/cherry-pick 子集），并接入策略引擎（worktree remove 默认 review）+ golden 对照扩展。

## Current Baseline

- Wave 1-3 完成：门面（status/diff/log/add/commit/branch/rev-parse/config/rename/unifiedDiff + policy 守卫）、CLI 八命令 + parity 42/golden 41、策略引擎五维 + 双拦截点（plan 2576-2578，121 tests）。
- design 05：JGit 7.x 只读 linked worktree；add/remove 在适配层按官方元数据格式自建（`$GIT_COMMON_DIR/worktrees/<id>/`：gitdir/commondir/HEAD/index/locked + 工作目录 `.git` 指针文件 `gitdir: <admin 绝对路径>`）；add 语义（-b 建分支、checkout 物化）、remove 语义（拒主 worktree 与 locked 除非 force、prune 随 remove）；元数据逐字节双向兼容真实 git。
- 策略引擎已对 push/reset/branch -d 等 request 形态有引擎级裁决单测（plan 2578 Deferred）；本 Wave 补入口级拦截与 policy 接入（worktree remove 默认 review）。
- gap：全部 P2/P3 命令与 worktree 自建实现待落。

## Goals

- P2：`checkout`（分支/`-b`/`-c`）、`switch`（`-c`）、`reset`（soft/mixed/hard、`HEAD~n`）、`clean`（`-f`/`-fd`/`-n`）、`restore`（worktree 侧）、`rm`（cached/force）、`mv`、`stash`（push/list/pop 子集）。
- worktree：API `listWorktrees/addWorktree/removeWorktree` + `NopGitWorktree` 模型；CLI `worktree add`（含 `-b`/`--detach`）、`worktree list`（含 `--porcelain`）、`worktree remove`（含 `--force`）、`worktree prune`。元数据自建与真实 git 逐字节双向兼容（双向对照测试：真实 git 建 → nop-git 读/操作；nop-git 建 → 真实 git 读/识别/操作）。
- P3 本地：`init`（defaultBranch 配置）、`remote -v/add/set-url/rm`。
- P3 网络：`push`/`fetch`/`pull`（refspec 子集 + `-u`）、`clone`（本地 file:// 路径）。策略引擎远程维/分支维入口级拦截落地。
- P3 历史：`merge`（fast-forward/no-ff 子集）、`rebase`（upstream 简单形态）、`cherry-pick`（单 commit）。
- 策略接入：`worktree remove` 默认进 review（内置默认策略更新）；`reset --hard`/`clean -f`/`push --force` 走既有引擎级规则的入口级 E2E。
- 新子命令 golden 对照扩展（能确定性录制的输出面）+ AI 工具回归冒烟。

## Non-Goals

- 冲突自动解决与复杂 merge/rebase 语义（冲突时报原生风格错误退出，不解决）。
- `stash` 完整语义（apply --index、branch、drop 子集不做，push/list/pop 即可）。
- 网络协议非 file:// 测试面（真实远端拉推不在测试范围；策略单测覆盖 URL 规则）。
- `worktree move`、`worktree lock/unlock` 显式子命令（locked 文件格式兼容即可）。

## Scope

### In Scope

- core 门面扩签（design 04 终态契约的 checkout/reset/clean/worktree/push 段）+ `JGitRepositoryService` 对应实现 + worktree 自建 helper（适配层内）。
- cli 新命令处理器 + formatter 扩展（worktree list、remote -v 等）。
- 内置默认策略更新（worktree remove → review）。
- 测试：worktree 双向对照（真实 git 进程）、各命令行为测试、golden 扩展、AI 冒烟回归、策略 E2E（reset --hard deny 等）。

### Out Of Scope

- docs-for-ai 使用文档（Wave 6）、beans.xml（Wave 6）、HTTP 服务端（Wave 5）。

## Execution Plan

### Phase 1 - 门面扩签与 P2 命令

Status: completed
Targets: `nop-git-core`、`nop-git-cli`

- Item Types: `Feature`

- [x] 门面：checkout(branch, create)/reset(mode, rev)/clean(options)/restore(paths)/rm(paths, cached, force)/mv(old, new)/stashPush/StashList/StashPop
- [x] CLI：checkout/switch/reset/clean/restore/rm/mv/stash 处理器 + 退出码/错误契约（未跟踪冲突等原生风格报错）
- [x] 策略 request 映射：reset（flags 含 --hard→既有 deny 规则生效）、clean（-f→--force --directories）、checkout→review 默认、branch -d 走分支维

Exit Criteria:

- [x] 每命令行为测试（init→变更→命令→断言 worktree/index/HEAD 状态）
- [x] 策略 E2E：enforce + option_rules deny reset --hard → CLI exit 1 `fatal: policy denied:`；worktree remove 默认 review 路径测试
- [x] `ib test` 全绿；`ai-dev/logs/` 已更新

### Phase 2 - worktree 自建实现（一等能力）

Spike（技术前提，先行）：

- [x] JGit 打开自建 linked worktree 冒烟 spike：写最小元数据（gitdir/commondir=`../..`/HEAD=`ref: refs/heads/x` + 工作目录 `.git` 指针）→ `Git.open(worktree)` 成功解析 → `checkout().setAllPaths(true)` 物化 index+工作区 → status 干净。spike 不过则回到设计层重新裁定，不得带病续行

Status: completed
Targets: `nop-git-core`（适配层 helper）、`nop-git-cli`

- Item Types: `Feature`

- [x] `NopGitWorktree` 模型（path、head、branch、bare、detached、locked、prunable）+ `listWorktrees()`（JGit 读 + 自建元数据合并）
- [x] `addWorktree(AddWorktreeOptions)`：id sanitize 冲突 -1/-2 后缀、gitdir（工作目录 .git 绝对路径）/commondir（固定 `../..`）/HEAD（`ref: refs/heads/x`）写入、工作目录 `.git` 指针文件、checkout 物化（`Git.open(worktree)` + `checkout().setAllPaths(true)`，index 由物化生成——design 05 已修订：空 index 是事实错误）；可选文件（ORIG_HEAD/logs/refs）不创建（真实 git 容忍）、`-b` 建分支、`--detach`
- [x] `removeWorktree(path, force)`：拒主 worktree/locked（除非 force）；删除前强校验 `<worktree>/.git` 指针回指该 admin 目录（防 gitdir 被篡改后误删任意目录）；删工作目录 + admin 目录；`prune`（失效指针清理）
- [x] CLI：worktree add/list/--porcelain/remove/--force/prune
- [x] 内置默认策略：worktree remove → review

Exit Criteria:

- [x] **双向对照测试**：真实 git worktree add → nop-git listWorktrees 正确读 + removeWorktree 可删、真实 git worktree list 确认；nop-git addWorktree → 真实 git worktree list 识别 + 可 checkout/commit 到该 worktree；元数据文件逐字节断言（gitdir/commondir 内容格式）
- [x] locked/主 worktree 拒删测试；prune 测试
- [x] worktree list --porcelain 与真实 git 输出逐行对照（实测格式锚点：首行主 worktree 恒第一条目；每条目 `worktree <abs>` → `HEAD <sha>` → `branch refs/heads/x`/`detached`/`bare` 恰一行 → 可选 `locked [reason]`/`prunable <reason>`；条目间空行、末条目后空行；路径 realpath 化；prunable/locked reason 文案逐字对齐）
- [x] `ib test` 全绿；`ai-dev/logs/` 已更新

### Phase 3 - P3 本地 + 网络 + 历史命令

Status: completed
Targets: `nop-git-core`、`nop-git-cli`

- Item Types: `Feature`

- [x] `init`（defaultBranch 配置项 + CLI `init -b <branch>`）、`remote -v/add/set-url/rm`
- [x] `push`（refspec 子集 + `-u`；策略分支维/远程维入口级 E2E：protected branch 直推 deny、--force deny）、`fetch`、`pull`（fetch+ff merge）、`clone`（本地路径）
- [x] `merge`（ff/no-ff；冲突报原生风格错误 exit 1）、`rebase`（upstream 简单形态）、`cherry-pick`（单 commit）
- [x] 测试全部走本地 file:// 远程（无网络依赖）

Exit Criteria:

- [x] push/fetch/pull/clone 往返测试（本地远程：clone→commit→push→另一 clone fetch/pull 到达）
- [x] merge/rebase/cherry-pick 行为测试 + 冲突报错测试
- [x] 策略 E2E：protected branch 直推 deny（enforce）、default allow 远程 push 通过；**fetch/pull request 构造非空壳验证（审查 M7）**：fetch/pull 的 IGitOperationRequest 进入引擎并有引擎级裁决单测（承接 plan 2578 Deferred）
- [x] `ib test` 全绿；`ai-dev/logs/` 已更新

### Phase 4 - golden 扩展 + 冒烟回归 + 收口

Status: completed
Targets: `nop-git-cli/src/test`

- Item Types: `Proof`

- [x] 新子命令确定性输出面 golden 扩展 + live-parity 扩展用例。**确定性裁定（审查 B5）**：可直录=checkout/switch（`Switched to branch 'x'`）、clean -n（`Would remove <path>`）/clean -fd（`Removing <path>`）、reset（静默）、remote -v、worktree list/--porcelain；含 SHA 面（merge ff `Updating a..b`+diffstat、worktree add stderr `HEAD is now at <sha>`、stash `WIP on x: <sha> <subj>`）用确定性 fixture（plan 2577 的 pinned ident）录制或 SHA 归一化模板，禁裸录
- [x] AI 冒烟回归（既有 7 例不红）+ 新增 worktree 场景冒烟
- [x] 变异探针一轮（新 golden 面）
- [x] roadmap 状态块更新、独立 closure audit

Exit Criteria:

- [x] golden/parity 扩展全绿 + 变异探针红→绿
- [x] 既有 121 测试回归全绿
- [x] 独立子 agent closure audit 证据写入 Closure 段
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] items 18-25 全部落地且测试覆盖
- [x] worktree 双向兼容对照测试双向绿 + 元数据逐字节断言（Anti-Hollow：自建实现真实可被真实 git 消费）
- [x] 端到端：clone→commit→push→fetch/pull 链路绿；策略入口级 E2E（protected 直推 deny / worktree remove review）绿
- [x] 无静默跳过：冲突/不支持形态显式报错退出
- [x] roadmap 状态块更新；design 05 若有落地修订 reconcile
- [x] 独立子 agent closure-audit 完成并写入证据
- [x] `ib test` 全模块绿；check-doc-links / check-plan-checklist / scan-hollow 退出码 0

## Deferred But Adjudicated

### merge/rebase 复杂冲突语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: roadmap 明确 P3 历史改写最后实现且只覆盖子集；冲突自动解决超出 AI 工具实际调用面（AI 工具遇冲突读 status 自行处理）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- stash apply --index / branch 子命令。
- worktree lock/unlock 显式 CLI。
- fetch/pull 的 prune/depth 选项。

## Closure

Status Note: Wave 4（roadmap items 18-25）全部落地：P2 五组命令、worktree 自建实现（元数据与真实 git 逐字节双向兼容，双向对照测试双向绿）、P3 本地/网络（file 远程往返）/历史命令、策略入口级 E2E（reset --hard deny、worktree remove 默认 review timeout 拒绝）、parity +5 例与变异探针红→绿。142 测试独立绿（core 26 + cli 116）。无 plan-owned 剩余工作；follow-ups 诚实归类。
Completed: 2026-11-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_29fdab18-cc64-4ca4-83bf-337feb32812e）
- Audit Session: agent_29fdab18-cc64-4ca4-83bf-337feb32812e
- Evidence:
  - 独立复跑 mvn test：142 run / 0 fail / 0 skip（core 26 + cli 116）
  - worktree 双向兼容独立复验：真实 git 建 → nop 读/删（真实 git 不再列出）；nop 建 → 真实 git porcelain 识别 + worktree 内 commit 主仓 branch 推进；commondir=`../..`/gitdir=realpath/.git 逐字节（od）
  - 独立 E2E：bare origin clone→commit→push→fetch/pull 往返；merge ff；策略 reset --hard deny exit 1 契约行；worktree remove review 无批准不执行
  - 变异探针独立复现："Would remove"→"Would delete" → parity 红（87/1）→ 恢复绿（87/0）
  - Anti-Hollow：Wave4Commands/Wave4Ops/WorktreeSupport 无 TODO/空壳；scan-hollow exit 0
  - 一致性：roadmap items 18-25 + Milestone done (plan 2579)；daily log Wave 4 段一致；check-doc-links exit 0
  - Deferred 分类检查：dirty checkout stdout 摘要/stash 子命令/worktree lock/unlock 均明示 non-blocking，无降级
  - 审计修正已落地：Phase 1-4 Status → completed、39/39 checklist 勾选、B1（本提交落库）
