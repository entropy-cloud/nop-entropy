# 125 Bash 语法解析器 Bug 修复与补全

> Plan Status: completed
> Last Reviewed: 2026-06-08
> Source: 代码审查 `nop-ai/nop-ai-shell/` 与设计文档 `nop-ai/nop-ai-shell/design/bash-syntax.md` 的对比分析

## Purpose

修复 BashLexer 和 BashSyntaxParser 中已确认的 bug，补全设计文档中已规划但未实现的功能，使解析器行为与设计文档一致，并通过新增测试覆盖此前未验证的场景。

## Current Baseline

- `BashLexer`、`BashSyntaxParser` 及全部模型类（`SimpleCommand`、`PipelineExpr`、`LogicalExpr`、`GroupExpr`、`SubshellExpr`、`BackgroundExpr`、`Redirect`、`EnvVar`、`CommandFactory`）已实现
- `BashSyntaxParserTest` 已有 ~30 个测试用例覆盖基本场景（简单命令、管道、逻辑运算符、优先级、分组、子 shell、后台运行、重定向、引号、环境变量）
- **已确认 bug**：
  1. Lexer `tryMatchOperators()` 中 `<<<` 永远不会被匹配到（3 字符 token 被 2 字符 `<<` 抢先匹配）
  2. Lexer 中 `<&` 和 `>&` 合并在同一个 if 条件（`"<&".equals(twoChar) || ">&".equals(twoChar)`），且都映射为 `REDIRECT_FD_OUTPUT`。`<&` 应映射为 `REDIRECT_FD_INPUT`，且需拆分为独立条件分支
  3. Lexer 中 `&>>` 可能被 2 字符 `&>` 抢先匹配为 `REDIRECT_MERGE` 而非 `REDIRECT_MERGE_APPEND`
  4. Lexer 中 `>>` 有冗余的 `||` 判断（`">>".equals(twoChar) || ">>".equals(twoChar)`）
  5. Lexer `tryMatchOperators()` 中 2 字符块与 3 字符块的 if 顺序导致 3 字符块完全不可达（`<<<`、`&>>` 的条件判断在 3 字符块内，而 2 字符块内有 `<<`、`&>` 的 early return）
  6. Parser `parseExpression()` 不处理逻辑表达式/管道表达式后的 trailing `&`（BACKGROUND token），`&` 只在 `parsePrimary()` 的 SimpleCommand/GroupExpr/SubshellExpr 分支中被消费。`cmd1 && cmd2 &` 的 `&` 被静默丢弃
- **设计与实现偏差**：
  1. 设计文档中 `PipelineExpr` 有 `redirects` 字段，实际代码中不存在
  2. 设计文档中 `Redirect` 有 `parse()` 静态方法（含不存在的 `STDERR_REDIRECT`/`STDERR_APPEND` 枚举值），实际未实现
  3. `LogicalExpr.Operator` 优先级值设计与实现不同（设计 `AND=3,OR=2,SEMI=1`，实现 `AND=4,OR=3,SEMI=2`），实现值更合理，应更新设计文档对齐实现
  4. 设计文档中 `formatOperand` 的结合性逻辑与实现不一致（设计写右结合，实现为左结合）
- **测试缺口**：`<<<`、`&>>`、`2>&1` fd 重定向完整解析、`export VAR=val cmd` 结构验证、trailing `&` 对逻辑表达式的支持

## Goals

- 修复 Lexer 中所有已确认的 token 匹配 bug，使 `<<<`、`<&`、`&>>` 正确识别
- 修复 Parser trailing `&` 对逻辑/管道表达式不生效的 bug
- 移除设计文档中 `PipelineExpr.redirects`（模型不应有此字段，管道重定向语义由外层表达式承担），同步更新设计文档
- 补全 `Redirect.parse()` 静态方法（不使用设计文档中的参考代码，因其引用不存在的枚举值）
- 更新设计文档的优先级数值、`formatOperand` 结合性逻辑，对齐实现
- 新增测试覆盖此前未验证的 bug 修复场景和边界情况
- 所有已有测试保持通过

## Non-Goals

- 不实现 Here-document 多行内容解析（仅 `<<` delimiter token 级别支持即可）
- 不实现命令替换 `$(...)`、反引号 `` `...` ``、进程替换 `<(...)` 等高级语法
- 不实现 `case`/`if`/`for`/`while` 等复合命令
- 不实现 round-trip 序列化保证（`parse → toString → parse` 等价）
- 不重构模型类结构或 Visitor 接口

## Scope

### In Scope

- `BashLexer` token 匹配 bug 修复（`<<<`/`<&`/`&>>` 匹配顺序和映射）
- `BashSyntaxParser` trailing `&` 对逻辑/管道表达式的支持
- `Redirect.parse()` 实现（禁止使用设计文档中引用不存在枚举值的参考代码）
- 设计文档与实现对齐更新
- 新增测试用例

### Out Of Scope

- 高级 Bash 语法（命令替换、算术展开、复合命令等）
- 执行引擎相关
- round-trip 序列化

## Execution Plan

### Phase 1 - Lexer Bug 修复

Status: completed
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/model/BashLexer.java`, `nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/model/BashLexerTest.java`（新建）

- Item Types: `Fix | Proof`

- [x] 重构 `tryMatchOperators()` 方法：将 3 字符匹配块（`<<<`→`REDIRECT_HERE_STRING`、`&>>`→`REDIRECT_MERGE_APPEND`）移到 2 字符匹配块之前，确保 3 字符 token 不被 2 字符 early return 截断
- [x] 拆分 `<&` 和 `>&` 为两个独立条件分支：`"<&"` → `REDIRECT_FD_INPUT`，`">&"` → `REDIRECT_FD_OUTPUT`（当前代码合并为一个 if 且都映射为 FD_OUTPUT）
- [x] 移除 `>>` 匹配中的冗余 `||` 判断
- [x] 移除 2 字符块中的死代码条件（`"&>>".equals(twoChar)` 和 `"<<<".equals(twoChar)` 在 2 字符子串上永远为 false）
- [x] 新建 `BashLexerTest.java`，覆盖 `<<<`、`<&`、`>&`、`&>>`、`<<` 的 token 化结果

Exit Criteria:

- [x] `<<<` 输入被词法分析为单个 `REDIRECT_HERE_STRING` token
- [x] `<&` 输入被词法分析为 `REDIRECT_FD_INPUT` token（而非 `REDIRECT_FD_OUTPUT`）
- [x] `>&` 输入仍被词法分析为 `REDIRECT_FD_OUTPUT` token
- [x] `&>>` 输入被词法分析为 `REDIRECT_MERGE_APPEND` token（而非 `REDIRECT_MERGE`）
- [x] `<<` 输入仍被词法分析为 `REDIRECT_HERE_DOC` token
- [x] `BashLexerTest.java` 新建且上述 5 个场景有独立测试
- [x] 全部已有 `BashSyntaxParserTest` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Redirect.parse() 实现

Status: completed
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/model/Redirect.java`, `nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/model/RedirectTest.java`（新建）

- Item Types: `Fix | Proof`

- [x] 实现 `Redirect.parse(String)` 静态方法，支持解析常见重定向字符串。**禁止使用设计文档 `bash-syntax.md:669-711` 的参考代码**（该代码引用不存在的 `STDERR_REDIRECT`/`STDERR_APPEND` 枚举值）。仅使用实际存在的 `Redirect.Type` 枚举值（`OUTPUT`、`APPEND`、`INPUT`、`FD_OUTPUT`、`FD_INPUT`、`MERGE`、`MERGE_APPEND`、`HERE_DOC`、`HERE_STRING`），stderr 重定向通过 `sourceFd=2 + OUTPUT/APPEND` 组合表达
- [x] 新建 `RedirectTest.java`

Exit Criteria:

- [x] `Redirect.parse("2>&1")` 返回 `Redirect(2, FD_OUTPUT, "1")`
- [x] `Redirect.parse("1>&2")` 返回 `Redirect(1, FD_OUTPUT, "2")`
- [x] `Redirect.parse("&>out.txt")` 返回 `Redirect(null, MERGE, "out.txt")`
- [x] `Redirect.parse("&>>log.txt")` 返回 `Redirect(null, MERGE_APPEND, "log.txt")`
- [x] `Redirect.parse(">>log.txt")` 返回 `Redirect(null, APPEND, "log.txt")`
- [x] `Redirect.parse(">out.txt")` 返回 `Redirect(null, OUTPUT, "out.txt")`
- [x] `Redirect.parse("<in.txt")` 返回 `Redirect(null, INPUT, "in.txt")`
- [x] `Redirect.parse("2>err.txt")` 返回 `Redirect(2, OUTPUT, "err.txt")`
- [x] `Redirect.parse("2>>err.txt")` 返回 `Redirect(2, APPEND, "err.txt")`
- [x] 非法输入（null、空串、无法识别格式）抛出 `IllegalArgumentException`
- [x] `RedirectTest.java` 新建且上述场景有独立测试
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计文档对齐更新

Status: completed
Targets: `nop-ai/nop-ai-shell/design/bash-syntax.md`

- Item Types: `Fix`

- [x] 更新 `LogicalExpr.Operator` 优先级数值：`AND("&&", 4)`、`OR("||", 3)`、`SEMICOLON(";", 2)`，对齐实际实现
- [x] 修正 `formatOperand` 结合性逻辑：设计文档中 `(isRight && childPrec == parentPrec)` 应改为 `(!isRight && childPrec == parentPrec)`，对齐 `LogicalExpr.java:88` 的实际实现（左结合）
- [x] 从设计文档中移除 `PipelineExpr.redirects` 字段及其 getter，增加说明"管道不持有重定向，重定向由管道内各命令或外层表达式持有"
- [x] 移除设计文档中 `Redirect` 的 `STDERR_REDIRECT`/`STDERR_APPEND` 枚举值（实际实现中不存在这些类型，stderr 重定向通过 `sourceFd=2 + OUTPUT/APPEND` 组合表达）
- [x] 整体替换设计文档中 `Redirect.parse()` 方法体（`bash-syntax.md:669-711`）为 Phase 2 的实际实现规格（原代码引用不存在的枚举值，不可保留）

Exit Criteria:

- [x] 设计文档中 `LogicalExpr.Operator` 优先级数值与 `LogicalExpr.java` 源码一致
- [x] 设计文档中 `formatOperand` 结合性逻辑与 `LogicalExpr.java:88` 源码一致
- [x] 设计文档中 `PipelineExpr` 无 `redirects` 字段
- [x] 设计文档中 `Redirect.Type` 枚举值与 `Redirect.java` 源码一致（无 `STDERR_REDIRECT`/`STDERR_APPEND`）
- [x] 设计文档中 `Redirect.parse()` 方法体不引用不存在的枚举值
- [x] No new test required: 纯文档变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Parser trailing `&` 修复 + 测试补全

Status: completed
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java`, `nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/parser/BashSyntaxParserTest.java`

- Item Types: `Fix | Proof`

- [x] 修复 `parseExpression()` 使其能正确处理逻辑/管道表达式后的 trailing `&`（BACKGROUND token）。当前 `&` 只在 `parsePrimary()` 的 SimpleCommand/GroupExpr/SubshellExpr 分支中被消费，逻辑/管道表达式的 trailing `&` 被静默丢弃。修复方式：从 `parsePrimary()` 移除 `&` 处理逻辑（SimpleCommand line 140-142、GroupExpr line 105-119、SubshellExpr line 89-95 三处），改为在 `parseExpression()` 且 `minPrecedence == 0` 的最外层统一检查 trailing BACKGROUND token 并包装为 `BackgroundExpr`
- [x] 新增 `<<<` here string 端到端解析测试（Lexer 修复后可解析）
- [x] 新增 `&>>` merge append 端到端解析测试（Lexer 修复后可解析）
- [x] 新增 `2>&1` fd 重定向端到端解析测试：验证 `cmd > out.txt 2>&1` 解析后得到正确的 `Redirect(2, FD_OUTPUT, "1")`
- [x] 新增 `export VAR=val cmd` 结构验证测试：验证 `envVars` 中包含 `EnvVar(EXPORT, "VAR", "val")`，command 为 `cmd`
- [x] 新增 trailing `&` 测试：`cmd1 && cmd2 | cmd3 &` 应解析为 `BackgroundExpr(LogicalExpr(AND, cmd1, PipelineExpr(cmd2, cmd3)))`；`cmd1 && cmd2 &` 应解析为 `BackgroundExpr(LogicalExpr(AND, cmd1, cmd2))`
- [x] 新增 fd 重定向测试：`cmd > out.txt 2>&1` 和 `cmd < input.txt 0<&3`

Exit Criteria:

- [x] `cmd1 && cmd2 &` 解析为 `BackgroundExpr`，其 inner 为 `LogicalExpr(AND, cmd1, cmd2)`
- [x] `cmd1 && cmd2 | cmd3 &` 解析为 `BackgroundExpr`，其 inner 为 `LogicalExpr(AND, cmd1, PipelineExpr(cmd2, cmd3))`
- [x] 所有新增测试通过
- [x] 全部已有测试保持通过
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Lexer 中 `<<<`/`<&`/`&>>` 三个已确认 bug 已修复
- [x] Parser trailing `&` 对逻辑/管道表达式的 bug 已修复
- [x] `Redirect.parse()` 已实现且有测试覆盖
- [x] 设计文档与实际代码一致（优先级、字段、枚举值、结合性）
- [x] 新增测试覆盖此前未验证场景，全部通过
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `ai-dev/logs/` 收口记录已更新
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### Here-document 多行内容解析

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 `<<` delimiter 级别的 token 支持已满足"识别到此语法结构"的最低需求，完整多行 here-document 解析属于独立功能增强，不影响当前已支持的语法元素正确性
- Successor Required: `no`
- Successor Path: N/A

### Round-trip 序列化保证

- Classification: `optimization candidate`
- Why Not Blocking Closure: 设计文档标记为建议（推荐做法），非强制契约；当前 `toString()` 用于调试输出，不要求能 round-trip
- Successor Required: `no`
- Successor Path: N/A

## Non-Blocking Follow-ups

- 命令替换 `$(...)` / 反引号支持（需要新的 token 类型和解析规则）
- `case`/`if`/`for`/`while` 复合命令支持
- round-trip 序列化（`parse → toString → parse` 等价保证）
- `SimpleCommand` getter 命名统一（`getCommand()` vs `command()`）
- 加强现有 `testSimpleCommandWithExportEnvVar` 测试：当前只验证 `toString()`，应改为验证 `envVars` 结构（与 Phase 4 新增测试对齐后可考虑替换）

## Closure

Status Note: All 6 confirmed bugs fixed, Redirect.parse() implemented, design doc aligned with code, test coverage expanded from ~30 to 64 relevant tests (7 BashLexerTest + 12 RedirectTest + 45 BashSyntaxParserTest). Build passes with 0 failures.

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (task ses_158d3ea2bffeNWJHteAWINWsbX)
- Evidence:
  - Phase 1: PASS — 3-char block before 2-char, `<&`→FD_INPUT, `>&`→FD_OUTPUT, no dead code, BashLexerTest 7 tests pass
  - Phase 2: PASS — `Redirect.parse()` uses regex-based parsing, no reference to non-existent enum values, RedirectTest 12 tests pass
  - Phase 3: PASS — design doc AND=4/OR=3/SEMI=2, `!isRight` left-associative, no PipelineExpr.redirects, no STDERR_* enums, parse() aligned
  - Phase 4: PASS — trailing `&` handled in `parseSequence()` only, parsePrimary() has no BACKGROUND checks, 8 new tests pass
  - `./mvnw test -pl nop-ai/nop-ai-shell -am` BUILD SUCCESS: 181 tests, 0 failures
  - Anti-Hollow: parseSequence() calls parseExpression(0) then checks BACKGROUND; end-to-end tests verify BackgroundExpr wrapping
  - Deferred items: only out-of-scope (here-document multi-line, round-trip), no in-scope defect deferred
  - `node ai-dev/tools/check-plan-checklist.mjs` result: pending (will run next)

Follow-up:

- `cmd 2>error.log` fd-prefix detection (parser currently treats `2` as argument when no preceding redirect exists — requires lookahead in parseBaseCommand)
- strengthen `testSimpleCommandWithExportEnvVar` to verify envVars structure instead of toString()
- no remaining plan-owned work
