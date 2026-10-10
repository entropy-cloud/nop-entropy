# nop-find Architecture Baseline

**日期**：2026-10-10
**范围**：`nop-find/` 模块组、`nop-runner/nop-cli-core`（`CliFindCommand`）
**状态**：定稿（对应计划 `ai-dev/plans/2379-nop-find-tool.md`）
**灵感来源**：zcode `workspaceFileSearch` 模块（三级打分 + top-K 二分插入，经数十万候选规模实测；外部仓库，非本仓库路径）

---

## 一、模块结构

```
nop-find/
├── nop-find-core/       核心：打分器、top-K、查找协调器
├── nop-find-cli/        命令行入口（NopFindMain）
└── nop-find-bom/        统一版本管理
```

### 模块依赖

```
nop-find-bom            (管理所有模块版本)
nop-find-cli            → nop-find-core, picocli
nop-find-core           → nop-rg-core（复用 ParallelFileWalker / GlobMatcher）
                         → nop-core（直依赖：GitIgnoreFile / CoreInitialization）
                         → nop-api-core（经 nop-core 传递：NopException 父类所在模块）
nop-runner/nop-cli-core → nop-find-cli（新增 CliFindCommand 透传子命令）
```

**JDK 门控**：与 nop-rg 相同模式——`nop-find` 父 POM 通过 JDK ≥ 22 激活 profile 挂入根 reactor（`java22-modules`），JDK < 22 构建自动跳过。子模块 pom 直接指定 `maven.compiler.release=22`。

### 包结构

```
io.nop.find.core
├── io.nop.find.core.score       三级打分器（纯函数，无状态）
├── io.nop.find.core.topk        TopKCollector（二分插入 + 快速路径）
├── io.nop.find.core.coordinator FindCoordinator（遍历 → 打分 → 聚合编排）
└── io.nop.find.core.model       FindCommand / FindMatch 数据类

io.nop.find.cli                  CLI 入口（NopFindMain）
```

## 二、核心设计决策

### 决策 1：独立模块组，不并入 nop-rg

**选择**：`nop-find` 独立于 `nop-rg` 建模块组，通过依赖 `nop-rg-core` 复用 `ParallelFileWalker`，不通过子命令扩展 nop-rg。
**理由**：
- find 与 rg 是两个不同工具域（模糊查找 vs 内容搜索），CLI 面、输出格式、用户心智均不同
- zcode 同时增强 rg 与 find，但在产品面是两个独立能力；nop 生态对齐该分界
- 独立模块组便于独立演进（未来打分算法下沉、交互模式扩展）不牵动 nop-rg 版本节奏

**拒绝了什么**：
- 并入 nop-rg 作 `find` 子命令：污染 nop-rg 的 ripgrep 兼容命令面（`nop-rg PATTERN` 语义）
- 复用 `nop file find`（nop-cli-core）改造：该命令是单线程 regex/AntPath 过滤，无并行、无打分、无 top-K，且 nop-cli-core 位于更高依赖层，不适合承载核心算法

### 决策 2：复用 ParallelFileWalker（接受 JDK 22+ 门控）

**选择**：`nop-find-core` 依赖 `nop-rg-core`，遍历层直接复用 `ParallelFileWalker`（并行 + gitignore + 符号链接 + 错误快速失败契约）。
**理由**：
- walker 已是成熟组件（Wave 2 落地，多轮 audit 加固），gitignore 语义与 nop-cli、nop-ai-code-analyzer 一致
- 自实现 walker 需重复 gitignore 集成、剪枝、符号链接环防护（~170 行 + 边界测试）
- nop-cli-core 已要求 JDK 22+（承载 nop-rg），nop-find 同基线不增加生态摩擦

**拒绝了什么**：
- 自实现独立 walker：重复代码、gitignore 语义漂移风险
- 抽取 walker 到共享新模块：动 nop-rg 既有结构，v1 不必要；打分算法本身无 FFM 依赖，未来可独立下沉解耦
- JDK 17 无门控独立实现：接受重复 walker 代码换取低 JDK 兼容，对 dev-tool 场景收益低

**边界注记**：依赖仅限 `ParallelFileWalker` 一类；打分器/top-K/协调器均为纯 Java，无 FFM/`MemorySegment` 引用，未来若需解耦可将 `io.nop.find.core.score`/`topk` 下沉到无 JDK 门控模块。

### 决策 3：三级打分算法（zcode 验证）

**选择**：对每个候选，query 与三个文本字段分别打分，取最低分（分越低越优）；单字段打分三级递进。

**字段与权重偏移**：

| 字段 | 偏移 | 说明 |
|------|------|------|
| `name` | +0 | 文件名（含扩展名） |
| `relativePath` | +25 | 相对 root 路径 |
| `path` | +300 | 完整路径（关键词档，覆盖跨目录段子序列命中） |

**单字段三级打分**（伪代码，行为语义）：

```
score(text, query):
  text 为空 → 不匹配
  query 为空 → 拒绝（CLI 层校验非空）
  text 以 query 开头 → len(text) - len(query)                    // 前缀档
  text 包含 query → 100 + indexOf(query)                         // 子串档
  query 按序散布于 text（子序列）→ 200 + Σ(相邻间隔) + (len(text)-len(query))  // 子序列档
  否则 → 不匹配
```

子序列取贪心最左匹配位置 `p(0..m-1)`，相邻间隔为 `p(i+1) - p(i) - 1`；间隔越大命中越松散、分数越高。

**大小写**：默认不敏感。索引构建时预归一化（一次性小写），打分热路径零 `toLowerCase`（zcode 实测为卡顿主因之一）。`--case-sensitive` 关闭归一化。本实现预归一化落在遍历阶段（候选三字段各折叠一次），并对已是小写的文本零分配短路；无预建索引，故不存在独立的索引构建阶段。

**理由**：
- 三级排序语义（前缀最优先、子串次之、子序列兜底）是模糊 finder 的成熟设计，zcode 在数十万候选规模验证
- 三路取最优覆盖"只记得文件名片段"与"记得跨目录路径片段"两类 query
- 预归一化 + 纯函数打分器天然可并行

**拒绝了什么**：
- 单字段（仅文件名）打分：跨目录段 query（如 `noprgcli`）无法命中
- Levenshtein 编辑距离：O(n·m) 开销大，对子序列乱序命中语义不如 gap 惩罚自然
- 正则/ glob 当 query：用户输入短碎片时无意义，且构造成本高
- 与 zcode 数值逐位对齐：三级排序语义对齐即可，数值公式允许独立演进

### 决策 4：Top-K 二分插入 + 线程局部归并

**选择**：`TopKCollector` 维护按 `(score, index)` 严格全序的定长列表（默认 K=100）；offer 时先走快速路径（已满且不优于末尾则跳过），再二分查找插入点。多线程场景每线程一个 collector，最终 K 路归并。

**行为语义**（伪代码）：

```
offer(candidate):
  若列表已满 且 candidate ≥ 末尾 → 跳过          // 快速路径
  二分查找插入位置
  插入；超长则弹出末尾
```

**理由**：
- zcode 实测：top-K 用线性 findIndex 在 65k 候选 × K=100 时单键 155ms 阻塞主线程；二分插入降为 O(n log K)
- 线程局部 + 归并避免共享可变状态锁竞争，与 nop-rg 专用 ForkJoinPool 并行模式一致
- `(score, index)` 全序保证同输入同输出（确定性）

**拒绝了什么**：
- 全排序 O(n log n)：K << n 时浪费
- 共享 TopKCollector + 锁：高并发下锁竞争成瓶颈
- 基于 `TreeSet`/`PriorityQueue`：额外对象开销，且全序 tie-break 需自定义 comparator，二分插入更直接

### 决策 5：FindCoordinator 编排契约

**选择**：`FindCoordinator` 承担"遍历 → 打分 → top-K 聚合"编排，独立于 `SearchCoordinator`（nop-rg 内容搜索编排）。

**行为语义**：

```
find(FindCommand):
  1. ParallelFileWalker.walk(root)                    // 复用，并行 + gitignore
  2. glob 过滤 → 候选列表（遍历结果已按路径字典序，候选下标即确定性 tie-break 的 index）
  3. 候选列表按线程数分片
  4. 每线程：分片内逐文件打分 → offer 到线程局部 TopKCollector
  5. 归并各线程 collector → 全局 top-K
  6. 映射为 FindMatch（path + relativePath + score）排序输出
```

**确定性契约**：`index` 取自排序后候选列表的下标，与线程数无关；`(score, index)` 构成全序，
因此任意 `threads` 取值下返回结果逐元素一致。

**遍历配置归属**：`threads` / `respectGitignore` / `includeHidden` 由 `FindCoordinator` 构造参数持有
（与 `SearchCoordinator`/`SearchCommand` 分层一致），`FindCommand` 只承载 query/root/limit/caseSensitive/globs。

**与 SearchCoordinator 的边界**：不读文件内容、不涉及字节搜索策略（LITERAL/VECTOR/REGEX）。两者仅共享 walker 与线程池模式，属平行编排器，互不依赖。

### 决策 6：CLI 契约（NopFindMain + nop-cli 透传）

**独立 CLI**：`nop-find QUERY [PATH]`（`NopFindMain`，picocli）。

| 参数 | 语义 |
|------|------|
| `QUERY`（位置，必填） | 模糊查询串，非空（非空校验在 CLI 层与 `FindCommand` 构造器双重把关） |
| `PATH`（位置，可选） | 搜索根目录，默认 `.` |
| `-n/--limit` | 返回条数上限，默认 100 |
| `-s/--case-sensitive` | 大小写敏感（默认不敏感） |
| `-g/--glob` | 包含/排除 glob（复用 nop-rg GlobMatcher 语义，`!` 前缀排除） |
| `--json` | JSON 输出 |
| `--no-ignore` | 关闭 .gitignore 过滤（隐藏文件仍默认隐藏） |
| `--threads` | 并行度，`0` 表示 CPU 核数 |
| `-h/--help`、`-V/--version` | picocli 标准选项（`mixinStandardHelpOptions`） |

**输出**：
- 默认文本：每行 `SCORE\tRELATIVE_PATH`（score 升序，同分按路径字典序）。打印相对 root 的路径——
  模糊查找器的直接消费场景是喂给编辑器/复制粘贴，绝对路径仅在 `--json` 中提供。
- `--json`：`{"matches":[{"path":"...","relativePath":"...","score":N},...],"count":N}`

**退出码**：命中 0 / 未命中 1 / 错误 2（对齐 nop-rg）。

**stdout 契约**：stdout 是纯数据通道。`nop-find-cli` 自带 `logback.xml`，root 级别压到 `ERROR`
且日志一律走 stderr，使 nop 核心初始化（VFS/ClassPathScanner/ModuleManager）的 INFO/DEBUG 日志
不污染管道。该文件仅对带 logback 的独立进程生效；嵌入 `nop-cli` 的场景使用 slf4j-jboss-logmanager
绑定，不读该文件，因此不改变 `nop-cli` 的日志行为。

**nop-cli 透传**：新增 `CliFindCommand`（`name = "find"`），采用与 `CliRgCommand` 相同的 `@Unmatched` 透传模式——不复述参数定义，全部交给 `NopFindMain` 自己的 picocli 解析；注册进 `MainCommand.subcommands`。

**错误路径**：空 query、非法 PATH、非正 limit、负 threads、遍历失败均显式报错（exit 2），不静默降级。

## 三、分层架构

```
┌─────────────────────────────────────────────┐
│            CLI 入口层                        │
│   nop-find-cli (NopFindMain) / CliFindCommand│
├─────────────────────────────────────────────┤
│         FindCoordinator                     │
│  （任务分发、结果聚合、并行控制）              │
├──────────────────┬──────────────────────────┤
│  FuzzyFileScorer │     TopKCollector        │
│  （三级打分）     │  （二分插入 top-K）       │
├──────────────────┴──────────────────────────┤
│         ParallelFileWalker（复用 nop-rg-core）│
│    专用 ForkJoinPool + gitignore 过滤         │
├─────────────────────────────────────────────┤
│         GitIgnoreFile（nop-core）            │
└─────────────────────────────────────────────┘
```

## 四、与外部系统的关系

- **zcode workspace-file-search**：打分算法参考对象（三级语义 + 预归一化 + top-K 二分），数值公式不强制逐位对齐
- **ripgrep / fd / fzf**：不做运行时委托；fzf 的交互 TUI 不在 scope，仅对齐"模糊 file finder"产品域
- **nop-rg**：依赖其 `ParallelFileWalker`（编译期依赖 `nop-rg-core`），不依赖其 search/coordinator
- **nop-search**：不交互。nop-find 是实时文件系统扫描，nop-search 是预构建索引检索，场景互补
- **nop-cli-core**：新增 `CliFindCommand` 透传子命令，模式同 `CliRgCommand`
