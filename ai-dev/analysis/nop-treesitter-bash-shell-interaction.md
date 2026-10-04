# nop-ai-shell 手写 bash parser 与 nop-treesitter shell 语义层的衔接裁定

> Date: 2026-10-04
> Plan: `ai-dev/plans/nop-treesitter/2026-10-04-1430-1-bash-grammar-and-shell-analysis.md` Phase 7
> Design: `ai-dev/design/nop-treesitter/01-bash-shell-analysis.md`

## 结论

**并行，不迁移，不融合。** 两者解决的不是同一个问题，因此不存在"用谁替换谁"的
选择；把其中一个塞进另一个的形状里只会同时损失两者的长处。

不设 successor 迁移计划。若将来出现下列任一信号，再重新评估（信号本身即触发条件，
不需要预先排期）：

- `nop-ai-shell` 需要处理手写 parser 明确不支持的语法（进程替换、`[[ ]]` 复杂条件、
  `${x//pat/rep}`、brace expansion），且当前策略是"报错拒绝"而不是"降级按字面处理"；
- 出现第二个消费方需要**证明型**分析结果（而不只是"执行前能不能跑"）；
- 手写 parser 的 fail-fast 行为在真实 agent 输出上产生误拒，且误拒率可测量。

## 两者各自在做什么

| | `io.nop.ai.shell.parser.BashSyntaxParser`（595 行手写） | `io.nop.treesitter.shell.ShellAnalysis`（tree-sitter 之上） |
|---|---|---|
| 目的 | 驱动**模拟 shell 环境**：拿到表达式树后逐条执行 `CdCommand` / `EchoCommand` 等 | 产出**结构化事实**与「可证明 / 不可证明」状态 |
| 输入 | 必须是可执行的命令序列 | 任意 shell 文本，包括 agent 生成的、语法有误的 |
| 语法覆盖 | 设计上的子集（Tier 1/2/3 支持矩阵，`ai-dev/design/nop-ai-agent/nop-ai-shell-syntax-spec.md`） | 全量 bash 语法（上游 corpus 100/100） |
| 遇到不认识的东西 | **抛异常**（`ParseException`，`parse()` 在未消费完输入时 fail-fast） | **不抛**，返回带封闭原因的拒绝状态 |
| 输出 | 表达式树（`SimpleCommand` / `PipelineExpr` / `LogicalExpr` / `SubshellExpr` / `BackgroundExpr` / `GroupExpr` / `Redirect`），随后被求值 | 6 项能力的记录，**从不求值** |
| 依赖 | 无（自有 `BashLexer`） | `nop-treesitter`（bash blob + 29 外部 token 的 `BashScanner`） |

## 三条路的代价

### 1. 替换（用语义层产物驱动 shell 执行）

不可行，方向就是错的。语义层**故意不产出可执行的表达式树**：它的 6 项能力里没有一项
负责求值，`UnprovenScript` 甚至不提供返回命令列表的访问器。要用它驱动执行，就得先把
"命令 + 字符串参数 + 重定向 + 管道顺序"重新组装成 `CommandExpression` —— 那等于把
tree-sitter 的树再降级一次，丢掉 `nop-ai-shell` 已经建好的 `CommandFactory` /
`CommandVisitor` / `ICommandChecker` 体系，并让执行路径依赖一个"证明失败即拒绝"的
分析层。同时手写 parser 的优先级表（`ai-dev/design/nop-ai-shell/04-bash-syntax.md` §3）
与 bash 真实优先级并不完全一致，替换会顺带改变现有行为。

### 2. 双 parser 融合（先 tree-sitter 解析，失败再回退手写 parser，或反之）

不可行，且会引入一个真实的正确性问题：**两个 parser 对同一段文本给出不同结构时，
系统无法知道该信谁**。它们对 `&`、`2>&1`、引号内 `;`、空命令（`A= cmd`）的处理都
不同，而差异点是静默的（不抛异常），因此回退逻辑只能靠"哪个先成功"，那等于把
"能否证明"和"能否解析"混成一件事——正是本设计花力气分开的两件事。

融合还会让 `nop-ai-shell` 依赖 `nop-treesitter`，从而把一个模拟 shell 的执行引擎
绑到 tree-sitter 运行时上：29 个外部 token 的手译 scanner、GLR 恢复、blob 解码都会
进入 shell 模块的升级面，而这些与执行引擎本身无关。

### 3. 并行（本次裁定）

- `nop-ai-shell` 继续用自己的 parser 解析**它要执行**的脚本，行为零变化；
- 需要"这段文本会做什么 / 能否证明"的消费方走 `io.nop.treesitter.shell`；
- 两者之间**不共享数据结构**，也不试图把一方的输出喂给另一方。

代价是仓库里存在两个 bash 解析器。这个代价是可控的，条件是各自的定位被写清楚：
`io.nop.treesitter.shell` 的包 Javadoc 与 design doc 已声明"本层不做危险判定、不执行"，
`ai-dev/design/nop-ai-shell/04-bash-syntax.md` 的定位是"驱动模拟 shell 环境"。
两者都不宣称是"bash 解析器"，因此不存在需要裁决的重复。

## 一个具体的可复用点（记录，不在本计划实施）

两者的**空命令**处理值得互相参照：`A= cmd`（只赋值不执行）在 tree-sitter 里是
`command` 节点内含一个 `variable_assignment` 且无 `command_name`，手写 parser 有
`EMPTY_VALUE` 外部 token 专门处理。若将来 `nop-ai-shell` 要支持这一形态，
语义层的"命令名前缀赋值必须判为不可证明"这条规则可作为参考——但这是两个模块之间的
一次显式改动，不在本计划范围。
