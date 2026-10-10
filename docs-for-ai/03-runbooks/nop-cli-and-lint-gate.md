# 安装与使用 nop-cli（含 Java lint 门禁）

> 面向在 **nop-entropy 仓库内**开发平台本身的开发者与 AI。
> 在外部业务应用项目中，nop-cli 的用法见 [`bootstrap-new-application.md`](bootstrap-new-application.md)。

## 0. 结论（当前状态）

`nop-cli` **可以正常执行**。仓库内已实测通过的路径：

| 能力 | 命令 | 实测结果 |
|------|------|---------|
| 版本/帮助 | `nop-cli --version` / `--help` | 正常，列出全部 21 个子命令 |
| 代码生成 | `nop-cli gen -t=/nop/templates/orm -o=<dir> <model>` | 正常，`demo-ddd.orm.xml` 生成 92 个文件 |
| 模型校验 | `nop-cli validate <model>` | 正常 |
| 模型格式转换 | `nop-cli convert -o=<out.xlsx> <model>` | 正常 |
| 文本搜索 | `nop-cli rg <args>` | 正常（ripgrep 兼容子集） |
| JSON 处理 | `nop-cli jq <expr>` | 正常 |
| **Java lint** | `nop-cli lint check <paths>` | 正常，65 条规则，退出码 0/1/2 |

启动开销约 1.4s/次（JVM + Quarkus uber-jar 启动），适合交互式使用，不适合高频批量调用。

## 1. 什么代码在什么地方

| 路径 | 作用 |
|------|------|
| `nop-runner/nop-cli-core/` | 全部命令行代码（`io.nop.cli` 包）。`NopCliApplication` 是入口，`MainCommand` 注册全部子命令 |
| `nop-runner/nop-cli/` | Quarkus 打包壳，只负责产出 uber-jar，源码里仅有一个占位类 `io.nop.cli_app.EmptyMain` |
| `nop-runner/nop-cli/target/nop-cli-<version>.jar` | 构建产物，约 57MB |
| `nop-lint/` | lint 引擎与规则库（`nop-lint-nop` 提供 65 条规则） |
| `tools/install-cli.sh` | 注册脚本：复制到 `~/.nop/cli` 并注册全局 `nop-cli` 命令 |

**注意版本号**：`nop-runner/nop-cli/pom.xml` **不继承 reactor 的 parent**，版本独立固定（当前 `2.0.0-BETA.1`），且依赖 `nop-cli-core:2.0.0-SNAPSHOT`。因此 jar 名与 `2.0.0-SNAPSHOT` 无关，脚本一律用 glob 匹配，**不要硬编码版本号**。

### 构建

```bash
./mvnw install -pl nop-runner/nop-cli-core -DskipTests
./mvnw install -pl nop-runner/nop-cli        -DskipTests
```

第一条必须先跑：`nop-cli` 的 uber-jar 会把 `nop-cli-core` 打进去。

## 2. 注册为全局命令（推荐）

```bash
bash tools/install-cli.sh
```

脚本做四件事：

1. 定位本 worktree 已构建的 uber-jar（glob 匹配，兼容任意版本号）
2. 复制到 `~/.nop/cli/nop-cli.jar`（先写 `.new` 再 `mv`，避免中断留下半个 jar）
3. 生成启动器 `~/.nop/cli/bin/nop-cli`
4. 在 PATH 目录注册 `nop-cli` 命令

**为什么复制 jar 而不是直接软链 worktree**：仓库通常以 git worktree 形式存在，worktree 可能被删除或重建，而全局命令应当继续可用。复制后的安装是自包含的。

### PATH 注册顺序

脚本按「已在 PATH 中」优先挑选，**不会自动修改你的 shell rc 文件**：

1. `~/.local/bin`
2. `~/bin`
3. `/usr/local/bin`

如果第一个命中的目录不在 PATH 中，脚本会打印一行需要追加到 rc 文件的命令（按 `$SHELL` 分别给出 `~/.zshrc` / `~/.bashrc` / `~/.profile`），由你自己决定。

若目录不支持软链（部分 Windows 环境），脚本退化为写入一个转发脚本。

### 验证（在普通命令行窗口中）

```bash
command -v nop-cli        # 应输出 ~/.local/bin/nop-cli 或等价路径
nop-cli --version
nop-cli --help
```

新开的终端窗口里也应当直接可用。若不可用，通常是 rc 文件改动未生效——重开窗口或 `source` 一次 rc 文件。

### 其他子命令

```bash
bash tools/install-cli.sh --build        # jar 不存在时先构建再安装
bash tools/install-cli.sh --unregister   # 移除 PATH 注册（保留 jar）
```

`--unregister` 会删除 `~/.local/bin`、`~/bin`、`/usr/local/bin`、`~/.nop/cli/bin` 下的注册项；删除 `~/.nop/cli` 目录才会真正移除 jar。

## 3. 缺省输出已经是安静的

nop-cli 每次启动会打印两块 banner（Quarkus 自带的 duck 图，以及 Nop 自己的 banner 文本）以及 Quarkus 的 INFO 启动日志。这些内容会污染管道（`nop-cli jq` 的结果、git hook 的输出），因此**已经写进 `nop-cli-core` 的缺省配置**，日常使用不需要传任何额外参数：

| 配置项 | 文件 | 值 |
|--------|------|-----|
| `quarkus.banner.enabled` | `nop-runner/nop-cli-core/src/main/resources/application.yaml` | `false` |
| `quarkus.log.level` | 同上 | `WARN` |
| `nop.banner.enabled` | 同上 | `false` |

实测：`nop-cli jq '.'` 的 stdout 只有 `{"x":1}`，无任何噪声。

> 若你在 fork 或旧版本 jar 上仍看到 banner，说明用的是没有上述配置的旧 jar，重跑构建即可。

`NOP_CLI_OPTS` 环境变量可追加 JVM 参数；`NOP_CLI_HOME` 可覆盖安装目录。

## 4. Java lint 门禁（已从 ast-grep 迁移到 nop-cli）

### 门禁规则

pre-commit hook 与 Java lint 包装脚本默认执行三条规则，它们就是原 ast-grep 规则集的迁移落点（规则 YAML 的 `metadata.source` 字段指回原 ast-grep 文件，血缘可机检）：

| 原 ast-grep 规则（规则 YAML 在仓库 ai-dev 目录下的 tools 子目录） | nop-lint 规则 | severity |
|------------------------------------------------------|--------------|----------|
| `java-lint-bare-runtimeexception.yml` | `nop/no-raw-exception` | warning |
| `java-lint-empty-catch.yml` | `nop/no-empty-catch` | warning |
| `java-lint-getmessage-only.yml` | `nop/no-log-getmessage` | warning |

### 为什么门禁只跑这三条

全量 65 条规则在存量代码上已有约 **69 处 error/warning 命中**（例如 `nop-stream/nop-stream-flow` 有 10 处 `nop/silent-swallow` error），直接全量开会立刻卡死正常提交。因此先按迁移前的等价范围守住门禁。

**扩大范围是明确的待办**，前置条件是先偿还存量命中。

### 为什么不用 `--max-warnings 0`

nop-lint 除规则诊断外，还会输出引擎级元诊断 `unused-disable-directive`（severity 固定为 `warning`）。它对**任何含失效抑制指令的文件**都会触发——而这些是存量债，提交里只是顺带碰到了该文件。若用 `--max-warnings 0`，这类与本次改动无关的存量问题会误杀提交。

因此 Java lint 包装脚本改为：用 `--format json` 跑一次，再**按 `ruleId` 精确过滤**只统计门禁规则自身的诊断。

### 入口

包装脚本位于仓库 ai-dev 目录下的 Java lint 包装脚本（**不在** docs 目录内，属平台自身开发工具）：

```bash
bash ai-dev/tools/run-java-lint.sh                     # 扫描全部 git 追踪的 .java
bash ai-dev/tools/run-java-lint.sh nop-ai nop-stream   # 扫描指定模块
bash ai-dev/tools/run-java-lint.sh --rules nop/no-empty-catch some/dir   # 自定义规则集
```

脚本按以下顺序解析 nop-cli，任一命中即可：

1. PATH 上的 `nop-cli`（要求**可执行**，见下方陷阱）
2. `~/.nop/cli/bin/nop-cli`
3. 本 worktree 的 `nop-runner/nop-cli/target/nop-cli-*.jar`

都不存在时，报错并给出 `bash tools/install-cli.sh` 指引。

全量扫描通过 `git ls-files '*.java'` 枚举后分批（每批 400 个）调用——15k+ 文件超出 argv 上限，且直接传仓库根目录会让 nop-lint 走到非 Java 夹具上。

**nop-lint 门禁过滤掉 `src/test/resources/_vfs/test/lint/`**：那里的 `invalid/*.java` 是故意写脏的测试数据，其违规由 nop-lint 自己的 RuleTestRunner 套件门禁断言。nop-lint 没有 ignore 机制，`--rules` 也挡不住（这些夹具违反的是真实规则），所以过滤必须发生在文件列表进 CLI 之前。

### pre-commit hook

`.githooks/pre-commit` 对暂存的 `.java` 文件（ACMR）执行门禁，退出码：0 通过 / 1 有门禁命中 / 2 用法或解析错误。

启用：

```bash
git config core.hooksPath .githooks
```

绕过：`git commit --no-verify`。

## 5. 已知问题与陷阱

### PATH 上的失效注册会赢下查找

`command -v nop-cli` 只看「是否存在」，不验证「是否可执行」。若某个早期 PATH 目录里留有指向已删除 worktree、或指向**非可执行**文件的软链，查找会命中它，随后 exec 失败。

`run-java-lint.sh` 用 `[ -x "$found" ]` 做了防护。本仓库历史上就有一个这样的例子：`/usr/local/bin/nop-cli` 指向 `.opencode/script/nop-cli.sh`，而该文件当时是 644 权限。

`.opencode/script/nop-cli.sh` 现已改为：优先转发到 `~/.nop/cli` 的安装副本，其次回退到本 worktree 的 jar，且 jar 名改为 glob 匹配（原先硬编码 `2.0.0-BETA.1`，版本一变即失效）。

### 目录扫描可能被非 Java 夹具打断

`nop-cli lint check <目录>` 会递归遍历目录。当目录里存在**故意写坏的**非 Java 夹具时会抛异常并以退出码 2 中断，例如 `nop-format/nop-ooxml/nop-ooxml-docx/src/test/resources/a.xml`（多根 XML）。因此：

- 全量扫描走 `git ls-files '*.java'` 而不是传目录根
- 指定目录时尽量指向 `src/main/java`

### `lint --help` 不可用

`nop-cli lint` 没有自己的 help 选项，`--help` 会被当成子命令名并报用法错误。参数面见 `CliOptions.USAGE`：

```
usage: nop-lint check <path>... [--profile fast|standard|deep] [--fix|--fix-dry-run]
       [--baseline <file>|--baseline-check <file>|--write-baseline <file>]
       [--format console|sarif|checkstyle-xml|json|junit-xml]
       [--max-warnings <n>] [--rules <id,id,...>] [--cache <file>]
```

### `--version` 显示的是硬编码字符串

`nop-cli --version` 输出 `nop-cli 2.0`，来自 `MainCommand` 上 picocli 的 `version` 属性，与 jar 的真实版本 `2.0.0-BETA.1` 无关。查真实版本看 jar 文件名。

### `jq` 数字输出为小数

`echo '[1,2,3]' | nop-cli jq 'map(.*2)'` 输出 `[2.0,4.0,6.0]` 而非 `[2,4,6]`。管道下游若做精确数值比较需注意。

### `diff` 子命令未注册

`CliDiffCommand` 已实现但没有加入 `MainCommand` 的 `subcommands`，因此 `nop-cli diff` 不可用；且其 `loadModel` 有恒定加载 `newModelPath` 的缺陷（diff 结果恒为空）。已知 P2 缺陷，修复前不要依赖。

### CI 未构建 nop-cli

`.github/workflows/maven.yml` 使用 JDK 21，而 `nop-cli` 以 `--release 22` 编译。CI 目前无法构建 nop-cli，lint 门禁只在本机 git hook 中生效，不在 CI 中生效。

## 6. 相关文档

- [`bootstrap-new-application.md`](bootstrap-new-application.md) — 外部应用项目里的 nop-cli 用法
- [`../01-repo-map/where-things-live.md`](../01-repo-map/where-things-live.md) — 仓库结构索引
- [`../01-repo-map/module-groups.md`](../01-repo-map/module-groups.md) — 模块分组与依赖
- [`../02-core-guides/model-first-development.md`](../02-core-guides/model-first-development.md) — 模型优先开发与生成链路