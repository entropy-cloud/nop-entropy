# nop-git CLI 兼容契约

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. `nop-git-cli` 的可执行入口伪装为 `git`：部署时在 PATH 优先位置放置名为 `git` 的包装脚本（`exec java -jar nop-git-cli.jar "$@"`，Windows 用 `.cmd`），AI 编程工具将其当作系统 Git 调用。
2. 输出格式是第一契约：`status --porcelain`、`diff`、`log --oneline` 等机器可读输出**严格对齐原生 git 纯文本格式**——无 ANSI 颜色码、无美化对齐、分隔符与原生逐字节一致。格式正确性比功能覆盖面优先级更高。
3. 子命令按 AI 工具实际调用频率分 P0/P1/P2 三级实现；未实现子命令输出 `git: 'xxx' is not a nop-git command. See 'git --help'.` 并退出码 1。

## 二、AI 工具调用特征与兼容要求

| AI 工具 | 调用特征 | 对 nop-git 的要求 |
|---|---|---|
| Claude Code | 将 `git status` / `git diff` / `git log` 视为只读命令自动放行 | 只读命令输出格式稳定；`status --porcelain` 格式错误会直接导致 AI 做出错误编辑/提交决策 |
| Aider | 默认每次 AI 修改后 `git add` + `git commit -m` 自动提交（编辑前还会先提交 dirty 工作区） | `add`+`commit` 组合稳定；`commit` 输出必须含提交 SHA 且格式为 `[branch sha] message`，否则 Aider 解析失败 |
| Codex CLI | `codex doctor` 检测 Git 可用性，解析 `git --version` | `--version` 输出真实存在的 git 版本语义（如 `git version 2.45.0`），否则 doctor 判定环境不满足 |

## 三、子命令优先级与输出契约

| 优先级 | 子命令 | 关键参数 | 输出要求 |
|---|---|---|---|
| P0 | `status` | `--porcelain`、`-s` | porcelain v1 格式：两字符状态码 + 单空格 + 路径；重命名 `R  old -> new` |
| P0 | `diff` | `--cached`、`HEAD`、`--stat` | unified diff 或 `--stat` 格式 |
| P0 | `add` | 路径、`.`、`-A` | 成功无输出 |
| P0 | `commit` | `-m`、`-a` | `[branch sha] message`；无变更时输出原生风格 nothing to commit |
| P0 | `--version` / `-v` | — | `git version <配置版本号>` |
| P1 | `log` | `--oneline`、`-n`、`--format`（常用子集） | 每行 `短SHA 提交信息` |
| P1 | `branch` | `--show-current`、`-a`、`-d` | 当前分支名 / `* branch` 列表 |
| P1 | `rev-parse` | `HEAD`、`--abbrev-ref HEAD`、`--show-toplevel` | 解析结果；空仓库 HEAD 按原生报错 |
| P1 | `config` | `--get`、`--global user.name/email`（读写子集） | 供 AI 工具设置提交身份 |
| P2 | `checkout` / `switch` | 分支名、`-b`、`-c` | 切换结果 |
| P2 | `reset` | `--soft`、`--mixed`、`--hard`、`HEAD~n` | 受策略约束执行 |
| P2 | `clean` | `-f`、`-fd`、`-n` | 受策略约束执行 |
| P2 | `restore` / `rm` / `mv` | 常用子集 | 对齐原生行为 |
| P2 | `stash` | `push` / `list` / `pop`（子集） | 对齐原生行为 |
| P3 | `worktree` | `add` / `list` / `remove` | 供 Cursor 等工具 |
| P3 | `remote` | `-v`、`add`、`set-url` | 远程管理 |
| P3 | `push` / `fetch` / `pull` | refspec 子集 | 需网络；受策略与远程规则约束 |
| P3 | `clone` / `init` | — | init 本地必需；clone 需网络 |
| P3 | `merge` / `rebase` / `cherry-pick` | 子集 | 冲突语义复杂，最后实现 |

实现纪律：每实现一个子命令，先录制原生 git 的实际输出作为 golden file（本地开发机有真实 git），测试断言逐字节一致；golden file 覆盖典型仓库形态（空仓库、含未跟踪/已修改/重命名、含 UTF-8 与非常规文件名）。

## 四、全局行为约定

- **退出码**：0 成功；1 一般失败（含策略 deny）；128 致命错误（对齐原生 `fatal:` 语义）；未实现子命令 1。
- **错误消息**：`fatal:` / `error:` / `warning:` 前缀 + 英文消息，格式对齐原生；不输出 Java 堆栈。
- **无颜色**：等同原生 `--no-color`；不接受/忽略 `--pretty=tformat:…` 之外的 ANSI 生成路径。
- **JDK 门槛**：main 入口首先校验 `Runtime.version().feature() >= 17`，不满足输出中文提示并退出。
- **`--nop-git-internal` 隐藏命名空间**：以该前缀开头的选项走平台内部通道（如审计开关、策略覆盖路径），不会与 git 选项冲突；AI 工具不会生成它们。
- **`-C <path>` 全局选项**：支持（AI 工具常用），等价切目录后执行。
- **选项归一化**：解析阶段将 `-f`/`--force` 等同族选项归一化为规范形式再传给策略引擎与门面（详见 03-policy-engine §选项归一化），防止换写法绕过策略。

## 五、拒绝了什么

- **支持 git 的 ANSI 颜色与 pager**：AI 工具全部机器消费输出，颜色码只有害处；固定无色。
- **porcelain v2 优先**：主流 AI 工具探测与解析的是 v1（`--porcelain` 默认）；v2 可作后续补充。
- **shell 脚本转发真实 git（混合模式）**：见 architecture baseline §六——单执行路径优先于双路径兼容。
