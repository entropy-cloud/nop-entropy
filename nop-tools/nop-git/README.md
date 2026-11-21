# nop-git 用户手册

nop-git 是一个 git 兼容命令行工具 + Java Git 门面库，供 AI 编码工具把它当作系统 git 使用（PATH 覆盖部署），同时提供策略引擎（危险操作拦截/审计）与可选 HTTP Git 服务。

本手册对应 `~/nop-git` 部署布局（由 `nop-tools/nop-git/bin/package-git.sh` 打包，与 nop-ib 打包方式一致）。

## 一、部署布局

```
~/nop-git/
├── nop-git-cli.jar       # 自包含 CLI jar（shade，git 兼容命令行）
├── nop-git-server.jar    # 可选 HTTP Git 服务 jar（shade）
├── bin/git               # PATH 覆盖 wrapper
├── bin/nop-git-server    # HTTP 服务启动器
└── README.md             # 本手册
```

环境变量：
- `NOP_GIT_HOME`：jar 所在目录（默认取 wrapper 自身目录）
- `NOP_GIT_JAVA`：java 启动器（默认 PATH 中的 java）
- `NOP_GIT_VERSION`：`--version` 伪装版本（默认 2.45.0）

## 二、把 nop-git 当系统 git 用（AI 工具场景）

```bash
export PATH="$HOME/nop-git/bin:$PATH"
git --version          # git version 2.45.0
git status
```

无 ANSI 颜色；退出码契约与原生 git 一致：0 成功 / 1 一般失败（含 nothing-to-commit、未实现子命令）/ 128 fatal（非 git 仓库、未知 revision）/ 129 用法错误（未知选项）。未实现子命令输出 `git: 'xxx' is not a nop-git command.`。

### 已支持子命令

| 组 | 命令 |
|---|---|
| P0/P1 | `status`（含 `--porcelain`/`-s`/pathspec 过滤）、`diff`（`--cached`/`--stat`/`HEAD`）、`add`、`commit -m`、`log`（`--oneline`、`-n <num>`、`--max-count=`）、`branch`（`--show-current`）、`rev-parse`（`HEAD`/rev、`--abbrev-ref`、`--show-toplevel`、`--is-inside-work-tree`）、`config`（仓库内读/写） |
| P2 | `checkout`、`switch -c`、`reset [--soft/--mixed/--hard]`、`clean [-n/-f/-d]`、`restore`、`rm [--cached]`、`mv`、`stash push/list/pop/apply` |
| P3 | `init [-b] [--bare]`、`remote [-v/add/set-url/rm]`、`push [-u]`、`fetch`、`pull`、`clone`、`merge [--no-ff]`、`rebase`、`cherry-pick`、`worktree add/list/remove/prune` |

关键输出已与原生 git 逐字节对齐（live-parity 46 例 + golden 41 例钉死；2026-11-21 实测审计新增真实 git 建仓回归 12 例，见 `RealGitRepoBugTest`）：`log` 无多余空行、merge commit 带 `Merge:` 行、`add` 未命中 pathspec 报 `fatal: pathspec ... did not match any files`、`reset --hard` 打 `HEAD is now at ...`、`push` up-to-date 打 `Everything up-to-date`、`pull` 打 `Already up to date.`、`init` 对已有仓库打 `Reinitialized existing ...` 并忽略 `-b`、porcelain 对含空格/非 ASCII 路径输出 C 风格引号等。

### 已知功能缺口（未实现，调用方需走真实 git）

`tag`、`show`、`ls-files`、`branch -v/-a/-r`、`log -<num>`/`--graph`/`--stat`、`rm -r`（递归）、`remote get-url`、`config` 全局链读取（仅仓库内）、merge 冲突的 `git status` 双方标记展示为简化形态。

已知平台限制：macOS Apple-git 的系统级 `init.defaultbranch`（Xcode gitconfig）不在 JGit 配置链内，`git init` 缺省分支可能为 `master`（显式 `-b <branch>` 可避开）。

`commit` 仅支持 `-m/--message`（无交互编辑器）；无 message 且工作区有已暂存变更时报 `fatal: no commit message given`。

## 三、策略引擎（AI 安全护栏）

- 配置查找：`<repo>/.nop-git/policy.yaml` → `~/.nop-git/policy.yaml` → 内置默认；两层都缺省时引擎**非激活**（零拦截零审计，行为与原生 git 等价）。
- 五维规则（操作/选项/分支/路径/远程）+ scopes；`deny` 优先；`observe` 只审计（审计行带 hypotheticalEffect），`enforce` 真实拦截；`review` 三通道：`NOP_GIT_REVIEW_APPROVED=1` 环境变量 / 交互确认 / 超时降级 deny。
- deny 输出 `fatal: policy denied: ...` 退出 1。
- 审计：`<repo>/.nop-git/audit.log`（JSON Lines）。
- 选项归一化（`-f`→`--force`、refspec `+` 前缀、`--force-with-lease` 归一）在评估前完成，CLI 与 Java 门面同一张表。

## 四、Java API（IGitRepositoryService）

```java
try (IGitRepositoryService svc = IGitRepositoryService.open(workDir, policyEngine)) {
    svc.add(".");
    NopGitCommit c = svc.commit("msg", null, null); // 身份走 git config 链
}
```

签名全自有类型（`NopGitStatus/Commit/DiffEntry/Ref/Worktree`），JGit 不外泄；worktree 一等支持（`list/add/remove/pruneWorktrees`）。详见 `docs-for-ai/03-modules/nop-git.md`。

## 五、HTTP Git 服务（可选）

```bash
~/nop-git/bin/nop-git-server <port> <repoRoot> [--bind-all] [--token <value>]
```

- 端点 `http://127.0.0.1:<port>/<name>.git`（智能协议；浏览器 GET 404 属正常）。
- **`<repoRoot>` 是注册表父目录**（非单个仓库）：URL `/<name>.git` 依次解析磁盘 `<root>/<name>/.git`（工作仓）、`<root>/<name>`（bare 仓）、`<root>/<name>.git`（标准 bare 目录布局，git-daemon/git-http-backend 同款）。名称只取末段，路径穿越拒绝。
- 默认只绑 loopback；`--token` 启用 bearer 访问控制（客户端 `-c http.extraHeader="Authorization: Bearer <token>"`）；`--bind-all` 全网卡。
- 服务端分支保护：main/master/release/* 直推/force/删除拒绝；受保护 tag 的删除/改写拒绝。

## 六、重新打包

```bash
nop-tools/nop-git/bin/package-git.sh [target-dir]   # 默认 ~/nop-git
```

构建（`mvn package -DskipTests`， shade 自包含 jar）+ 部署 wrapper 脚本，幂等可重复执行。改了 nop-git 源码后重跑即可。

## 七、质量门

改动 nop-git 后的验证（经 nop-ib）：

```bash
nop-tools/nop-ib/bin/ib test -pl :nop-git-cli -pl :nop-git-core -pl :nop-git-server -pl :nop-git-ioc
```

测试体系：`CliParityTest`（与真实 git 逐字节对拍 46 例）、`GoldenFileTest`（41 例）、`RealGitRepoBugTest`（真实 git 建仓场景回归 12 例）、`Wave4CliTest`/`CliPolicyTest`/`AiToolSmokeTest`/`GitPolicyEngineTest`/`NopGitHttpServerIntegrationTest`。
