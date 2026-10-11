# nop-find

Nop 平台的独立模糊文件查找 CLI（对标 VSCode Ctrl+P / fzf file mode / zcode `@` 面板）。
按 query 对文件名与路径做三级模糊打分，返回排序后的 top-K 命中。

- 遍历层复用 `nop-rg-core` 的 `ParallelFileWalker`（并行 + `.gitignore` + 符号链接 + 错误快速失败）
- 打分算法为纯 Java 实现，不依赖 fzf/fd/Lucene/JGit 等外部搜索库
- 不读文件内容——内容搜索用 `nop-rg`

## 用法

```
nop-find QUERY [PATH] [-n=<limit>] [-s] [-g=<glob>]... [--json] [--no-ignore] [--threads=<n>]
```

| 参数 | 语义 |
|------|------|
| `QUERY`（位置，必填） | 模糊查询串，非空 |
| `PATH`（位置，可选） | 搜索根目录，默认 `.` |
| `-n`, `--limit` | 返回条数上限，默认 `100` |
| `-s`, `--case-sensitive` | 大小写敏感（默认不敏感） |
| `-g`, `--glob` | 包含/排除 glob（可重复，`!` 前缀为排除；语义同 nop-rg `-g`） |
| `--json` | JSON 输出（默认文本输出） |
| `--no-ignore` | 关闭 `.gitignore` 过滤（隐藏文件仍默认隐藏） |
| `--threads` | 并行度，`0` 表示 CPU 核数 |
| `-h`, `--help` / `-V`, `--version` | 帮助 / 版本 |

## 输出

**默认文本**：每行 `SCORE<TAB>RELATIVE_PATH`，按 score 升序（同分按路径字典序）。
打印相对 root 的路径——模糊查找器的直接消费场景是"喂给编辑器/复制粘贴"。

```
7	src/main/java/NopFinder.java
106	plain-nopfind.txt
```

**`--json`**：单行 JSON 文档，同时给出绝对路径与相对路径。

```json
{"matches":[{"path":"/abs/root/src/main/java/NopFinder.java","relativePath":"src/main/java/NopFinder.java","score":7}],"count":1}
```

**stdout 是纯数据通道**：本模块自带 `logback.xml` 把 root 级别压到 `ERROR` 且日志一律走 stderr，
nop 核心初始化的 INFO/DEBUG 日志不会混入管道。
该配置只对带 logback 的独立 `nop-find` 进程生效；`nop-cli find` 走 jboss-logmanager，不读它。

## 退出码

| 码 | 含义 |
|----|------|
| `0` | 命中（≥1 条） |
| `1` | 未命中（0 条） |
| `2` | 错误（空 query、非法 PATH、非法 limit/threads、遍历失败、用法错误） |

错误路径显式向 stderr 报错并返回 2，不静默返回空结果。

## 打分语义

三级打分（分越低越优），query 与 `name` / `relativePath` / `path` 三个字段分别打分取最小值，
字段偏移 `+0` / `+25` / `+300`：

| 档位 | 分数 |
|------|------|
| 前缀（text 以 query 开头） | `len(text) - len(query)` |
| 子串（text 包含 query） | `100 + indexOf(query)` |
| 子序列（query 按序散布于 text） | `200 + Σ相邻字符间隔 + (len(text) - len(query))` |
| 不匹配 | 排除 |

默认大小写不敏感，query 与候选路径在遍历阶段各折叠一次，打分热路径不做大小写转换。

## 与 fzf / fd 的差异

| | nop-find | fzf / fd |
|---|---------|-----------|
| 交互式 TUI | 无（纯 CLI + 库 API） | fzf 有 |
| 匹配算法 | 三级模糊（前缀/子串/子序列）+ gap 惩罚 | fd 为子序列；fzf 默认扩展正则/模糊可配 |
| `.gitignore` | 默认尊重，`--no-ignore` 关闭 | 均为默认尊重 |
| 隐藏文件 | 默认跳过（无可关闭开关） | `--hidden` 可开启 |
| 内容搜索 | 不支持（归 `nop-rg`） | rg 支持 |

## 库用法

```java
FindCoordinator coordinator = new FindCoordinator(
        Runtime.getRuntime().availableProcessors(), /* respectGitignore */ true, /* includeHidden */ false);
List<FindMatch> matches = coordinator.find(
        FindCommand.builder(Path.of("."), "finder").limit(20).build());
```

结果按 `(score, index)` 全序排序，与并行度无关：`threads=1` 与 `threads=N` 返回逐元素相同的结果。

## 依赖

- JDK 22+（传递依赖 `nop-rg-core` 的 FFM API）——JDK < 22 的构建自动跳过本模块组
- `nop-rg-core`（遍历 + glob）、`nop-core`（gitignore / VFS）、`picocli`（CLI）

## 验证命令

```bash
./mvnw test -pl nop-tools/nop-tools/nop-find/nop-find-core
./mvnw test -pl nop-tools/nop-tools/nop-find/nop-find-cli
```