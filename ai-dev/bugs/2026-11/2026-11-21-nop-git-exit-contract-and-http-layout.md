# 2026-11-21 nop-git 退出码契约与 HTTP 布局两缺陷修复

## Problem

黑盒实测（`~/nop-git/bin/git` 对照真实 git 2.54，`~/ibtest/gtest`）发现两个真实缺陷：

1. `git log badrev`：无任何输出且 **exit 0**。原生 git 输出 `fatal: ambiguous argument 'badrev': unknown revision or path not in the working tree.` 且 exit 128。AI 工具按退出码消费 git 输出，失败的 rev 查询被当成成功——fake-green 风险。
2. `git commit -qm msg`：`error: unknown option 'qm'` exit 129。原生接受组合短选项簇（`-qm`/`-am`/`-ma` 是 AI 工具最高频的调用形态）。

另有一个 HTTP 服务缺陷：注册表根下以标准 `<name>.git` bare 目录布局（git-daemon/git-http-backend 同款）摆放的仓库，URL `/<name>.git` 解析为磁盘 `<root>/<name>` 而直接 403——最常见的建仓布局不可服务。

## Diagnostic Method

- 逐命令双跑（nop-git vs native）比对 exit code；发现前几轮探测的 exit 值被 shell 管道（`| tail`）吞掉，改用无管道直跑复验（诊断过程教训：退出码断言必须在无管道形态下采样）。
- `log` 缺陷根因在 core 与 CLI 的契约缝隙：`JGitRepositoryService.doLog` 对不可解析 rev 返回空列表（API 契约「unborn HEAD 打印空」），CLI `LogCommand` 只特判了 `HEAD` unborn 形态，其他 rev 的空列表被静默渲染为成功。`GitCompatCli.nativeFatal` 已有 `unknown revision:` 消息映射，但没有任何地方抛出它。
- `commit` 缺陷：`CommitCommand` 只显式枚举了 `-m`/`-a`/`-am`/`-ma` 四种形态，其余簇直接走 unknown-option。
- HTTP 403：`RegistryResolver` 把 URL 名剥掉 `.git` 后只尝试 `<root>/<base>/.git` 与 `<root>/<base>` 两个磁盘候选，从不尝试 `<root>/<base>.git`。

## Root Cause

三个缺陷同族：**解析器只覆盖了自家测试里出现过的形态**——parity/golden 套件用 `-m`/`-am`，未测 `-qm`；unborn HEAD 特例覆盖了 HEAD，未覆盖任意 rev；HTTP 布局候选列表漏掉业界默认布局。

## Fix

- `LogCommand`：unborn-HEAD 特判后，`resolveRevision(rev)==null` 即输出原生 ambiguous-argument 三行 fatal 并 EXIT_FATAL（128）。core API「空列表」语义不变（`Wave4Commands.headSubject` 等调用方不受影响）。
- `CommitCommand`：通用短选项簇解析——`-a` 开关、`-q` 接受为 no-op（native quiet 子集契约）、`-m` 消费余字或下一参数、簇内未知字符报 `error: unknown option 'x'` exit 129。
- `NopGitHttpServer.RegistryResolver`：前两个候选不命中且 URL 以 `.git` 结尾时，追加 `<root>/<base>.git` 候选；HEAD 存在性校验与路径穿越拒绝保持不变。

## Tests

- `RealGitRepoBugTest.logUnknownRevNativeFatal`（真实 git 建仓：exit 128 + fatal 文案 + stdout 为空 + 合法 rev sanity）。
- `RealGitRepoBugTest.commitCombinedShortFlagClusters`（`-qm` 分离值、`-am` 附着值、簇内未知 flag 129；首版测试自身漏了暂存步骤，exit 1 语义是正确的「nothing to commit」——如实修正测试而非放宽断言）。
- `NopGitHttpServerIntegrationTest.servesDotGitBareDirectoryLayout`（`clone --bare` 造 `proj.git` → HTTP clone → commit → push 新分支 → sha 到达断言）。
- 黑盒复验（重打包后的 `~/nop-git`）：bare.git 布局 clone/push 往返通过、master force-push 被保护分支拒绝、`log badrev` 128、`commit -qm` 正确进入 nothing-to-commit 语义（exit 1）。
- 全模块回归 184/0（core/cli/server/ioc，含 parity 46 + golden 41 全绿不红）。
