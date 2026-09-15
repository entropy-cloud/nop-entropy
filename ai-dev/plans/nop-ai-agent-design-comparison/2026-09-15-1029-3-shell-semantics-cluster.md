---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-SHELL-SEMANTICS
group: "2026-09-15-1029"
verify: [test]
---

# P2 round-4 nop-ai-shell 核心语义缺陷簇（cd 无效果 / group 表达式吞结果 / background 尾随命令丢弃）

## Current Baseline

- 来源：deep-audit round 4 登记、roadmap `## Follow-up Backlog` 未勾选项（`source: deep-audit round 4`），全部经 live repo 复核（HEAD `9cbf684d88`，2026-09-15，`ShellCommandExecutor.java` 629 行 / `BashSyntaxParser.java` 110 行）：
  1. **`cd` 对后续命令无效果**：`executeSimpleCommandWithContext`（`ShellCommandExecutor.java:355-383`）用调用方 `context.workingDirectory()` 构建每命令 `DefaultShellExecutionContext`（:376）；`updateContextFromResult`（:525-544）虽把 `currentWorkingDir` 字段更新为 cd 目标（:540），但从不回写上下文——`executeSequence`（:336-351）逐命令传递同一个调用方 context，后续命令仍读 `context.workingDirectory()` 旧值；`getCurrentWorkingDir()`（:617）暴露的也是孤立的 `this.currentWorkingDir`。即 `cd /tmp && pwd` 或 `cd /tmp; ls` 中 pwd/ls 仍在原目录运行。
  2. **`{ ... }` 组表达式丢弃退出码与全部输出**：`executeGroup`（:288-303）执行 `executeSequence(...)` 后无条件返回 `new ExecutionResult(0, "", "")`（:300）——组内命令的实际退出码/stdout/stderr 全部丢弃；`echo fail; exit 3` 组以 exit 0 空输出收场。
  3. **`a & b` 尾随命令被 parser 静默丢弃**：`BashSyntaxParser.parseSequence`（:24-33）解析一个表达式后若遇 `BACKGROUND` token 只消费一个并返回 `new BackgroundExpr(result)`——`&` 之后的内容（如 `b`）被静默丢弃，不报错；`a & b` 实际只执行 a 的后台启动，b 丢失（bash 语义：a 后台运行 + b 前台执行）。
- 归属模块：nop-ai-shell（3 项同一模块，parser + executor 两层）。
- 验证面：运行时行为修复，涉及 `./mvnw test -pl nop-ai/nop-ai-shell -am`；owner doc 检索确认（`ai-dev/design/nop-ai-agent/nop-ai-tool-filesystem-design.md` 等如涉及 shell 语义则同步，否则显式 `No owner-doc update required`）。

## Goals

- `cd` 的目录变更对同一 command line 中后续命令生效（`cd X && pwd` / `cd X; ls` 在 X 内运行）；组表达式/子 shell 的目录隔离语义不回归。
- `{ ... }` 组表达式返回组内最后一条命令的退出码与输出（bash 语义），不再恒为 exit 0 空串；组内 export 不泄漏语义（既有 `testGroupExprEnvironmentRestore`）不回归。
- `a & b` 解析为「a 后台 + b 前台继续」语义，尾随命令不再被静默丢弃；`a &` 单独后台语义不回归。
- 每项配套回归测试（正确结果断言，非仅"不报错"）；owner docs 同步或显式 No-op；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不引入完整 bash 兼容层（作业控制、wait 内建、`&>` 之外的复合重定向、`&&`/`||` 与 `&` 混排的完整优先级矩阵若超出当前 parser 结构则登记而非强做）。
- 不改 `BashSyntaxParser` 对 pipeline/logical/redirect 的既有解析语义。
- 不处置本轮 roadmap 其余未勾选项（service/coder/agent/gateway 相关 P2/P3 项，另开计划）。
- 不运行 mvn 全量构建。

## Phase 1 — cd 工作目录贯通

Status: planned
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java`、`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `executeSimpleCommandWithContext`（:376 区域）：构建每命令 context 时以执行器当前工作目录（`this.currentWorkingDir`）为准（对齐 `CheckVisitor.workingDirectory()` :96 已用字段的口径），而非调用方 `context.workingDirectory()`；确保 `execute()` 入口（:72）以调用方 context 的 workingDirectory 初始化 `this.currentWorkingDir`（首次 cd 前 pwd 语义正确）。
- [x] `Fix` `updateContextFromResult`（:525-544）与 `executeSequence` 贯通：cd 更新后同 command line 后续命令读到新目录（确认 `currentWorkingDir` 变更在 `executeSequence` 循环内对后续迭代可见）；group/subshell 的 savedDir 恢复逻辑（:290-315）不回归。
- [x] `Fix` 回归测试：`cd <dir> && pwd`（或 `cd <dir>; ls`）断言输出为目标目录内容/路径；`cd` 不存在的目录 → 失败且目录不变；组表达式内 cd 不泄漏到组外（既有隔离语义对照）。
- [x] `Proof` 复核：`executePipeline`/`executeLogicalExpr`/`executeGroup`/`executeSubshell` 各路径的 context 传递核对——cd 语义在 pipeline 中（每 stage 独立执行器）与组/子 shell 中的边界与 bash 一致。

Exit Criteria:

- [x] `cd X && pwd` / `cd X; ls` 输出 X 相关内容（回归测试断言具体输出）。
- [x] cd 失败路径（目录不存在）不改变工作目录（断言失败 + 目录不变）。
- [x] group/subshell 内 cd 不泄漏到外层（既有 `testGroupExprEnvironmentRestore` 类断言不回归）。
- [x] **端到端验证**：`cd X && pwd` 从 parser → executor → 命令执行 → 输出收集的完整链路。
- [x] **接线验证**：cd 结果确实被同一 command line 的后续命令消费（非孤立字段）。
- [x] **无静默跳过**：无 cd 静默忽略路径（失败显式报错）。
- [x] owner doc 更新：检索确认无 owner doc 描述 cd/工作目录语义——显式写 `No owner-doc update required`。（实际检索命中 `ai-dev/design/nop-ai-shell/02-io-and-pipeline.md` §3.7 描述 cd 副作用处理，已同步 cd 目标校验与 currentWorkingDir 贯通语义；非 No-op）
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — group 表达式退出码与输出保真

Status: planned
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java`、`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `executeGroup`（:288-303）：返回组内最后一条命令的实际 `ExecutionResult`（退出码 + stdout + stderr）而非恒 `(0, "", "")`；若 `executeSequence` 返回 Void 不便取末值，调整为返回末命令结果或组内显式聚合（按 bash 语义 = 最后一条命令的状态）。
- [x] `Fix` 回归测试：`{ echo out; exit 3; }` → 断言退出码 3 + 输出 "out"；`{ false; }` → 退出码非 0；空/单命令组边界；组内 export 不泄漏既有断言（`testGroupExprEnvironmentRestore`）不回归。
- [x] `Proof` 复核：`executeSequence`（:336-351）与 `executeLogicalExpr` SEMICOLON 分支的返回值传递；group 与 subshell 的隔离语义对照（subshell 恒丢结果是否同样修复或登记为行为差异）。

Exit Criteria:

- [x] `{ echo out; exit 3; }` 返回 exit 3 + 输出 "out"（断言具体值）。
- [x] 组内 export 不泄漏语义不回归（既有测试通过）。
- [x] **端到端验证**：`{ ...; exit N; }` 从 parser → executor → 结果返回的完整链路。
- [x] **接线验证**：组结果确实来自组内最后命令的执行结果（非硬编码）。
- [x] **无静默跳过**：无输出/退出码被静默丢弃的路径残留。
- [x] owner doc 更新：如 `ai-dev/design/nop-ai-agent/nop-ai-tool-filesystem-design.md` 或 shell 语义文档涉及 group 行为则同步；否则显式写 `No owner-doc update required`。（`ai-dev/design/nop-ai-shell/03-executor-and-async.md` §3.4 与 §3.3 表格已声明"取决于最后一个内部命令"，同步为实际实现；subshell 复核确认其本就返回内部表达式结果，无丢弃路径，登记为行为差异说明）
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 3 — background 尾随命令保留（a & b）

Status: planned
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java`、`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/parser/BashSyntaxParserTest.java`、`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `parseSequence`（:24-33）：消费 `BACKGROUND` token 后继续解析后续表达式（`a & b` → 后台 a + 前台 b 的顺序语义），不再在 `&` 处截断丢弃；`a &`（无后续）保持纯后台语义；需确认 `BackgroundExpr`/`LogicalExpr` 模型表达（如 `LogicalExpr(SEMICOLON, BackgroundExpr(a), b)` 或序列包装，按现有模型最小改动）。
- [x] `Fix` 回归测试（parser 层）：`a & b` 解析产物含 b（断言 AST 形态）；`a &` 仍为纯 BackgroundExpr；`a && b &` 边界。
- [x] `Fix` 回归测试（executor 层）：`sleep 0.1 & echo after` → 断言 after 被输出（b 不再丢失）；后台 job 启动语义（`getBackgroundJobs`）不回归。
- [x] `Proof` 复核：`BackgroundExpr` 的 executor 执行路径（`executeExpression` 分支）与 sequence 语义；`CommandChecker/CheckVisitor` 对新增 AST 形态的 visit 覆盖（无 NPE/漏检）。

Exit Criteria:

- [x] `a & b` 的 AST 含 b（parser 断言具体形态）；executor 执行后 b 的输出可见（断言具体输出）。
- [x] `a &` 纯后台语义不回归（既有 `sleep 10 &` 测试通过）。
- [x] **端到端验证**：`a & b` 从 lexer → parser → executor → 输出收集的完整链路。
- [x] **接线验证**：`&` 之后的内容确实被解析并执行（非 parser 截断后 executor 兜底）。
- [x] **无静默跳过**：无尾随命令被丢弃的路径残留（parser 层 fail-fast 或完整消费）。
- [x] owner doc 更新：如 shell 语义文档涉及 background 解析则同步；否则显式写 `No owner-doc update required`。（`ai-dev/design/nop-ai-agent/nop-ai-shell-syntax-spec.md` §7.1 后台行已同步为 `parseSequence()` → `LogicalExpr(SEMICOLON, BackgroundExpr(a), b)`；`ai-dev/design/nop-ai-shell/04-bash-syntax.md` §6.5 模型描述与优先级表无需改动）
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件：仅当 Phase 1–3 全部 Exit Criteria 勾选、3 项 in-scope live defect 已修复并配套回归测试、owner docs 同步（或显式 `No owner-doc update required`）、`./mvnw test -pl nop-ai/nop-ai-shell -am` 通过、`node ai-dev/tools/check-doc-links.mjs --strict` 0 error 后，由独立子 agent 完成 closure-audit 并写入 `## Closure` 收口记录（含 Anti-Hollow 端到端调用链与无静默跳过核查）；本 section 不保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-1029-3-shell-semantics-cluster-1-c23e8a79 to opencode-pid-34297
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-1029-3-shell-semantics-cluster-1-c23e8a79

## Verification

- pass test 20260915-1229 exit=0

## Closure

- dispatch audit #audit-20260915-1229-2026-09-15-1029-3-shell-semantics-cluster-1-3993b90b to closer-session-2026-09-15-1229 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-1229-2026-09-15-1029-3-shell-semantics-cluster-1-3993b90b：审计通过——cd 贯通（`executeSimpleCommandWithContext` 以 `currentWorkingDir` 建 context + `execute()` 首跑初始化 + cd 失败 exit 1 目录不变，`cd subdir && ls`/`cd subdir; ls` 断言 inner.txt）、group 结果保真（`executeGroup` 返回 `executeSequence` 聚合结果，`{ echo out; exit 3; }` → exit 3+"out"）、`a & b` 尾随保留（`LogicalExpr(SEMICOLON, BackgroundExpr(a), b)` + parse() 残留 fail-fast，`sleep 200 & echo after` 输出 after）；`./mvnw test -pl nop-ai/nop-ai-shell -am` 全绿（14 套件 294 例 0 失败，新增 11 例）；doc-links 0 errors；scan-hollow 0 high/critical；3 份 owner doc + roadmap + `ai-dev/logs/2026/09-15.md` 已同步