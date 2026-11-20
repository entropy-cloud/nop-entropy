# 2577 nop-git Wave 2: git 兼容 CLI（P0/P1）

> Plan Status: completed
> Last Reviewed: 2026-11-21
> Source: `ai-dev/backlog/nop-git-roadmap.md` Wave 2 (items 7-11)；`ai-dev/design/nop-git/02-cli-compat-design.md`、`01-architecture-baseline.md`
> Related: plan 2576（Wave 1 已完成）

## Purpose

落地 nop-git-cli：git 兼容命令行入口（roadmap items 7–11），P0/P1 子命令输出与原生 git 纯文本格式逐字节对齐（golden 对照），PATH 覆盖部署包装可用，AI 工具兼容冒烟清单测试化。

## Current Baseline

- Wave 1（plan 2576）已落地：nop-git-parent/bom/core/cli 四模块（release=21、core 零仓库内部依赖）、`IGitRepositoryService` 门面 + `JGitRepositoryService` 适配层、自有模型、7 测试全绿。CLI 模块仅 package-info 占位。
- 本机有真实 git 2.54.0（golden 录制/对比可行）。
- 设计契约（02-cli-compat-design）：输出格式第一契约；退出码 0/1/128；无 ANSI 色；未实现子命令 `git: 'xxx' is not a nop-git command. See 'git --help'.` 退出码 1；`commit` 输出 `[branch sha] message`；`--version` 输出 `git version <配置版本号>`；`-C <path>` 全局选项；`--nop-git-internal` 隐藏命名空间；选项归一化（Wave 3 策略评估用，本 Wave 先在 CLI 解析层建立规范形式）。
- gap：CLI 全部待建；门面需增补（toplevel 查询、config 读写、branchList 全模式、rename 检测）。

## Goals

- `GitCompatCli` main + git 风格 argv 解析（全局 `-C`、`--version`、`--nop-git-internal` 命名空间保留、未知子命令按原生报错退出 1）。
- P0 子命令：`status`（`--porcelain`/`-s`，含 rename `R old -> new`）、`diff`（无参/`--cached`/`<rev>`/`--stat`）、`add`（路径/`.`/`-A`）、`commit`（`-m`/`-a`）、`--version`。
- P1 子命令：`log`（`--oneline`/`-n`）、`branch`（`--show-current`/`-a`）、`rev-parse`（`HEAD`/`--abbrev-ref HEAD`/`--show-toplevel`）、`config`（`--get`、user.name/email 读写，含 `--global`）。
- `GitOutputFormatter`：全部输出经 formatter 产出，无 ANSI 色；porcelain v1、unified diff（含 index 行）、oneline、diffstat 与原生逐字节一致。
- golden 对照测试框架：确定性 fixture 仓库（固定身份+固定 GIT_*_DATE → 确定性 SHA），golden file 与 live-parity 双模式（有 git 时同时对拍，无 git 时 golden file 兜底）。
- PATH 覆盖部署包装（`bin/git` sh 脚本 + Windows `.cmd`）+ 冒烟清单测试（模拟 Claude Code 只读探测 / Aider add+commit 输出解析 / Codex doctor --version 解析的输出契约断言）。

## Non-Goals

- 策略引擎接入（Wave 3；本 Wave CLI 直调门面，无裁决层——在 main 入口留 `IGitPolicyEngine` null 判空直通的单点 seam）。
- P2/P3 子命令、worktree（Wave 4）；HTTP 服务（Wave 5）。
- porcelain v2、ANSI/pager、`--pretty=tformat:` 全量支持。
- shell 补全、man page。

## Scope

### In Scope

- `nop-git-cli` 源码：`GitCompatCli`、argparse、`GitOutputFormatter`、退出码映射、错误格式化（fatal:/error: 前缀，无堆栈）。
- `nop-git-core` 门面增补：`topLevel()`、`configGet/configSet`（含 global 层）、`branchListAll()`（local+remote）、status/diff 的 rename 检测（RenameDetector，R 状态与 `old -> new` 路径对）、diff 的 unified 文本与 stat 数据产出（formatter 数据源）。
- 测试资源：确定性 fixture 构建器 + golden files（committed）+ live-parity 测试（git 缺失时自动 skip  parity、golden 仍跑）。
- 部署包装：`nop-git-cli/bin/git`、`nop-git-cli/bin/git.cmd` + packaging 说明。

### Out Of Scope

- `docs-for-ai/` 使用文档（Wave 6 统一收口）。
- 平台 beans.xml（Wave 6）。

## Execution Plan

### Phase 1 - 门面增补（CLI 数据源）

Status: completed
Targets: `nop-git-core`

- Item Types: `Feature`

- [x] `topLevel()`（--show-toplevel）、`configGet(key, scope)`/`configSet(key, value, scope)`（repo/global，user.name/email 主用例）
- [x] status/diff rename 检测：RenameDetector 接入，`NopGitStatus` 增 stagedRenames（old,new 对），`NopGitDiffEntry` 已有 RENAMED changeType（Wave 1 已建模）
- [x] `branchListAll()`：local + remote refs（`branch -a` 数据源）
- [x] `NopGitDiffEntry` 扩字段：oldBlobSha/newBlobSha（abbrev 前 image blob SHA，index 行数据源）、fileMode（100644 等，新增文件 old 侧 000000）、binary 标记（--stat 与 unified 的 Binary files 行）
- [x] unified diff 文本与 stat 数据：hunk `@@` 行与正文由 core 产出 EditList/文本行，formatter 拼装（core 零内部依赖不变）；diff --stat 宽度缩放按 git 非 tty 固定 80 列复刻，逻辑归 formatter，golden 以非 tty 录制为准

Exit Criteria:

- [x] 新增方法各有 JUnit 测试（rename 场景、config 读写、topLevel）
- [x] `ib test -pl :nop-git-core` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - golden 对照框架与确定性 fixture（前移，其余 Phase 的质量门）

Status: completed
Targets: `nop-git-cli/src/test`

- Item Types: `Feature` + `Proof`

- [x] 确定性 fixture 仓库构建器：固定 author/committer 身份 + PersonIdent 显式 tz 偏移（SHA 含 tz，必须 pin）+ 固定 GIT_*_DATE；fixture 放 `_tmp/` 下 realpath 路径（规避 macOS /tmp→/private/tmp 的 --show-toplevel 漂移）；形态：空仓库、clean、未跟踪（含新目录折叠 `?? dir/`）、已修改、已暂存、staged rename（`git mv` 后未 commit；worktree 侧 rename 明确 out of scope）
- [x] 含 remote 形态 fixture：手工写 `refs/remotes/origin/*` + symbolic `origin/HEAD`（branch -a 数据源，含 symbolic ref 信息）
- [x] golden files（committed test resources）覆盖 P0/P1 全部输出面（录制自本机 git 2.54 非 tty 输出）
- [x] live-parity 测试：git 可执行存在时对同一 fixture 跑真实 git 与 nop-git 逐字节对拍；缺失时 Assumptions skip
- [x] `nothing to commit` golden 覆盖三形态：clean、unstaged 修改、untracked（多段输出 + hint 行，以录制为准）

Exit Criteria:

- [x] fixture 双次构建 SHA 稳定（确定性自测）
- [x] live-parity 对拍绿（或 skip 记录）
- [x] `ai-dev/logs/` 已更新

### Phase 3 - CLI 骨架与 P0

Status: completed
Targets: `nop-git-cli`

- Item Types: `Feature`

- [x] `GitCompatCli` main：JDK 校验、`-C`、退出码契约（0 成功/1 一般失败含 nothing-to-commit 与策略类/128 致命——枚举：非 git 仓库、rev 无法解析、repo 打不开 → `fatal:`+128；子命令执行失败/无变更可提交/未实现子命令 → 1；错误消息 `fatal:`/`error:` 前缀无堆栈）、未实现子命令报错退出 1、`--nop-git-internal` 命名空间保留
- [x] `--version`（版本号从配置：系统属性/环境变量/默认 2.45.0）
- [x] `status` porcelain v1 格式（XY 两字符 + 路径、`R  old -> new`、untracked `??`）
- [x] `diff` 无参/`--cached`/`<rev>`/`--stat`（unified diff 与 diffstat 格式）
- [x] `add`（成功无输出）
- [x] `commit -m/-a`（`[branch sha] message` 输出；nothing to commit 原生多段格式以 golden 录制为准，覆盖 clean/unstaged/untracked 三形态 + hint 行，commit 场景退出码 1——status 的 clean 退出码是 0，两者不同；`-a` = 先 stage tracked 修改再提交，untracked 不含）

Exit Criteria:

- [x] 每个子命令至少一个端到端测试（argv → stdout 逐字节断言 golden）
- [x] 未实现子命令与 fatal 错误路径测试（退出码 + 消息格式断言）
- [x] 无 ANSI 转义字节（golden 对拍天然保证）
- [x] `ib test` 全绿；`ai-dev/logs/` 已更新

### Phase 4 - P1 子命令

Status: completed
Targets: `nop-git-cli`

- Item Types: `Feature`

- [x] `log --oneline/-n <num>`（`短SHA 提交信息` 每行）
- [x] `branch`（无参列表 `* current`、`--show-current`、`-a` 含 remotes 分节）
- [x] `rev-parse`（`HEAD`/`--abbrev-ref HEAD`/`--show-toplevel`；空仓库 HEAD 按原生 fatal 报错）
- [x] `config`（`--get key`（缺 key 退出码 1，以本机 git 2.54 实测为准）、`key [value]` 读写、`--global` 作用域；身份写入供 commit 用）。测试隔离：--global 写入经 `GIT_CONFIG_GLOBAL`/`GIT_CONFIG_SYSTEM` env 重定向到 `_tmp/`，禁止写真实 `~/.gitconfig`

Exit Criteria:

- [x] 每个子命令 golden 断言测试
- [x] 空仓库 rev-parse 错误路径测试
- [x] `ib test` 全绿；`ai-dev/logs/` 已更新

### Phase 5 - 变异探针、部署包装与冒烟清单

Status: completed
Targets: `nop-git-cli/bin`、测试

- Item Types: `Feature` + `Proof`

- [x] 变异探针：临时改动 formatter 输出一个字符 → golden/对拍变红 → 恢复（红→绿记录在 log）
- [x] `bin/git` sh 包装（classpath=shade 打包产物或 mvn 生成 classpath 文件，含 core + JGit 全依赖树；支持 NOP_GIT_JAVA 可配）+ `bin/git.cmd` Windows 版
- [x] 冒烟清单测试（模拟 AI 工具输出契约）：Claude Code 只读探测（`status`/`diff`/`log` 退出码 0 + 无 ANSI）、Aider add+commit 解析（`[branch sha] message` 正则）、Codex doctor（`git version N.N.N` 正则）

Exit Criteria:

- [x] sh 包装可执行冒烟（本机实际跑通 `bin/git --version`）
- [x] 三个冒烟测试绿
- [x] roadmap items 7-11 置 done、Milestone「AI 工具可当系统 Git 使用（P0/P1）」置 done
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] items 7-11 全部落地且测试覆盖
- [x] P0/P1 输出与原生 git 逐字节一致（live-parity 或 golden 全绿 + 变异探针记录）
- [x] 端到端：`GitCompatCli.main` argv 入口到 stdout/exit code 出口全链路测试
- [x] 无静默跳过：未实现子命令显式报错退出 1
- [x] roadmap 状态块更新
- [x] No owner-doc update required（Wave 6 统一收口；roadmap 状态块除外）
- [x] 独立子 agent closure-audit 完成并写入证据
- [x] `ib test` 全 nop-git 模块绿
- [x] check-style：imports 分组、英文错误消息（CLI fatal 消息为原生 git 风格英文，属契约内）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- porcelain v2、`--format` 更多模板、`diff --stat` 宽度自适应与原生重命名检测阈值（rename 检测阈值差异可能导致个别 golden 场景与 git 的 rename 判定不同——发现时以真实 git 行为为准调参，不影响 P0/P1 主面）。

## Closure

Status Note: Wave 2（roadmap items 7-11）全部落地：CLI 八命令 + formatter，P0/P1 输出与原生 git 逐字节对齐（live-parity 42 例全绿 + 确定性 fixture golden 41 例双保险，变异探针红→绿由审计方独立复现）；bin/git shade 包装本机实跑全链路通过；AI 工具三契约冒烟绿。总计 102 测试全绿（cli 90 + core 12），hollow 扫描 0 findings，checklist 49/49。无 in-scope live defect；porcelain v2/--format/rename 阈值为 Wave 4 item 25 successor ownership。
Completed: 2026-11-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_7b1d682e-04c4-42e3-9dec-0d4b40fa1825）
- Audit Session: agent_7b1d682e-04c4-42e3-9dec-0d4b40fa1825
- Evidence:
  - 独立复跑 mvn test：102 run / 0 fail / 0 skip（cli parity 42 + golden 41 + smoke 7；core lifecycle 6 + wave2 5 + pillar 1）
  - 独立变异探针：GitOutputFormatter "clean"→"cleen" → GoldenFileTest 红（1F）→ 恢复绿（41/0）
  - bin/git 冒烟独立复现：--version / -C status --porcelain / add / commit（[main (root-commit) sha] 输出）/ 未知命令原生报错 exit 1
  - parity 抽查：确认 spawn 真实 git 进程 + 空仓库 rev-parse HEAD 三方（stdout 回显/stderr 逐字节/exit 128）一致；ANSI 零命中
  - Anti-Hollow：scan-hollow exit 0（0 findings）；5 输出命令均经 formatter；未实现子命令显式报错有测试
  - check-plan-checklist --strict 复跑 exit 0（49/49）
  - Deferred 分类检查：无降级；porcelain v2 等 successor ownership 成立
  - 审计修正已落地：Phase 5 Status → completed；测试计数勘误入 daily log
