# 2026-11-21 nop-git CLI 真实 git 建仓 parity 审计（12 项行为缺陷修复）

## Problem

nop-git CLI 的既有测试基线（CliParityTest 46 例 + GoldenFileTest 41 例）全绿，但把 `~/nop-git/bin/git` 当系统 git 在 6 个真实测试项目（线性历史/改名删文件/脏工作区/merge+stash/裸远端/冲突材料）里与真实 git 对拍时，出现 14 项行为差异，其中 12 项为真实缺陷。典型症状：

- `git log` 每个 commit 块后多一个空行（真实 git 建的仓库上）
- merge commit 的 log 缺 `Merge: <a> <b>` 行
- `git add nonexistent.txt` **静默成功 exit 0**（原生 git fatal exit 128）
- `git status .`（带 pathspec）报 `error: unknown option ''` exit 129
- `git status --porcelain` 对含空格路径不加引号
- `checkout nonexistent`/`switch nonexistent` 错误消息与退出码都不对（128 vs 1/128）
- `stash pop`（空）带 `fatal:` 前缀 exit 128（原生无前缀 exit 1）；`merge <坏引用>` 同类
- `reset --hard` 缺 `HEAD is now at ...`；`push`/`pull` up-to-date 无输出
- `init` 对已有仓库打 `Initialized empty ...` 且路径带 `././`；缺省分支不读 `init.defaultBranch` 配置链

## Diagnostic Method

关键定位难点：**为什么 87 例既有测试全没抓到**。对拍 harness（`_tmp/nop-git-audit/parity.sh`：同一 fixture 克隆成 real/nop 孪生仓库，同一命令分别用真实 git 与 nop-git 执行，stdout/stderr/exit 三通道 diff，可再加落盘状态比对）跑出差异后回溯测试基线，发现：

1. **Golden fixture 由 JGit 提交**——JGit 存 commit message **不带**原生 git 写入的尾随 `\n`。`GitOutputFormatter.logLong` 用 `split("\n", -1)` 切消息，尾随 `\n` 会多产一个空行；JGit 建仓永远触发不了。Golden/parity 的 log 用例因此被钉在错误基线的「无空行」一侧，与真实 git 建仓场景恰好相反。
2. parity 用例未覆盖：merge commit 的 log、带 pathspec 的 status、含空格文件名、up-to-date 的 push/pull、对已有仓库的 init、空 stash 的 pop。

## Root Causes

- `logLong` 未剥离尾随换行；未输出 merge 父行（`NopGitCommit.parentIds` 有数据但格式化没用）
- `AddCommand` 只解析不校验 pathspec
- `StatusCommand` 把任何非已知选项一律当选项报错，positional pathspec 直接拒收
- 错误通道混乱：`nativeFatal` 对一切 `NopGitException` 加 `fatal:` 并退 128，而原生 git 多处用裸消息 + exit 1
- `init` 无「已存在仓库」检测、路径用 `getAbsolutePath()`（保留 `./`）、缺省分支硬编码且不读配置链（且 macOS Apple-git 的系统级配置在 JGit 配置链之外——记录为已知平台限制）

## Fix

CLI 层为主（`GitOutputFormatter`/`StatusCommand`/`AddCommand`/`CommitCommand`/`Wave4Commands`/`RevParseCommand`）：

1. logLong 剥尾随换行 + merge commit 输出 `Merge:` 行
2. porcelain/long status 统一 `quotePath`（C 风格转义）
3. status pathspec 过滤（canonical 路径解决 `/var`↔`/private/var` symlink 错位）
4. add 未命中 pathspec → `fatal: pathspec ... did not match any files` exit 128
5. checkout/switch 未知引用分流：checkout `error: pathspec ...` exit 1 / switch `fatal: invalid reference:` exit 128
6. stash pop/apply 空仓裸消息 exit 1；merge 坏引用裸消息 exit 1
7. reset --hard 打 `HEAD is now at`；push tracking==HEAD 打 `Everything up-to-date`；pull HEAD 不动打 `Already up to date.`
8. init：已有仓库 `Reinitialized existing` + `warning: re-init: ignored --initial-branch`；canonical 路径；缺省分支走 `JGitRepositoryService.defaultBranchFromConfig()`（system→user 链；经 core 适配层实现——首版 CLI 直接 import `org.eclipse.jgit.util.SystemReader` 被 `ArchitecturePillarTest` 拦下，架构钉子起作用）
9. commit 无 message 且 clean tree → 先打 status exit 1（与原生一致），消息检查后置
10. `rev-parse --is-inside-work-tree` 补实现

## Regression Tests

新增 `RealGitRepoBugTest`（12 例，全部用**真实系统 git 建仓**，专钉「JGit fixture 探测不到」的缺陷面）：log 尾空行、Merge 行、add pathspec、status pathspec、porcelain 引号、checkout/switch 错误分流、stash/merge 裸消息、reset/push/pull 消息、commit clean 输出、init reinit。验证：nop-ib `ib test -pl :nop-git-cli -pl :nop-git-core` 后 surefire 报告 138（cli）+ 34（core）全绿 0 failed 0 errors（含 46 parity + 41 golden 无回归）。

对拍 harness 复跑：batch1 12→9 fail（余为已记录功能缺口与 harness 目录名差异）、batch2 11→1（`rm -r` 缺口）、batch3b 5→3（空仓缺省分支为 JGit 配置链平台限制 + harness 目录名差异）。

## Lessons

- **测试 fixture 与生产写入方不同源时，字节级基线可能钉在错误一侧**：JGit 与真实 git 对 commit message 尾随换行、引用存储等细节不一致，golden 必须（也）用真实 git 建仓的用例兜底。
- 架构支柱测试（`org.eclipse.jgit` 禁止外泄）在本次修复中即时拦截了一次越界 import，验证了钉子的价值。

## Known Gaps（记录，未在本轮实施）

`tag`、`show`、`ls-files`、`branch -v/-a/-r`、`log -<num>`/`--graph`/`--stat`、`rm -r`、`remote get-url`、`config` 全局链读取；macOS Apple-git 系统级 `init.defaultbranch` 不在 JGit 链内。已写入用户手册「已知功能缺口」。
