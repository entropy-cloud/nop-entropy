# 2258 XLang try/catch/switch 修复：finally 可选 + catch 可吞异常 + switch 语句体与 break

> Plan Status: active
> Last Reviewed: 2026-09-01
> Source: `ai-dev/analysis/2026-09/2026-09-01-xlang-js-superset-vs-compat-mode.md`（Option C 首期行动项，用户 2026-09-01 拍板：暂不动 var，修 finally/catch/switch）
> Related: `ai-dev/analysis/2026-08/2026-08-21-xlang-js-compat-for-dsh-ptc-mode.md`

## Purpose

把 XScript 的 try/catch/finally 与 switch 从"半成品/JS 陷阱"修到"完整可执行、三后端语义一致、JS 直觉友好"。本计划不追求全面 JS 兼容（见 source analysis）。

## Current Baseline

以下事实均经本轮源码核实（2026-09-01）：

- **文法限制**：`tryStatement` 的 `finally` 块是文法必选（`nop-kernel/nop-xlang/model/antlr/XLangParser.g4:239-241`）；`switchCase` 的 consequent 必须是 `blockStatement?`（`:223-225`），无花括号语句体是语法错误。
- **try/catch 当前完全不可执行**：`BuildExecutableProcessor.processTryStatement` 调 `super` → `XLangASTProcessor.processTryStatement`（`XLangASTProcessor.java:393`）→ `defaultProcess` → `BuildExecutableProcessor.defaultProcess` 抛 `ERR_EXEC_NOT_SUPPORTED_AST_NODE`（`BuildExecutableProcessor.java:289-291,604-611`）。`LexicalScopeAnalysis` 无 `visitTryStatement`，catch 变量无声明机制。`TryExecutable` 类存在但全仓库无 AST→Executable 构造点。
- **catch 重抛语义（三后端一致）**：解释器 `TryExecutable.java:47-48` catch 体执行后 `throw NopException.adapt(e)`；java 后端 `ExecToJavaTranslator.genTry`（`:1188-1225`，`:1213` 无条件重抛）；truffle `XTryNode.java:42-53`（`:39-41` 放行 `XLControlFlowException`）。
- **break 是 ExitMode 标志**：`BreakExecutable.execute` 设 `rt.setExitMode(ExitMode.BREAK)`；循环节点轮询消费（`WhileExecutable.java:60-76`）；`BlockExecutable.execute` 见标志提前返回但不消费（`BlockExecutable.java:65-75`，检查点 `:70`）；`SwitchExecutable.execute`（`:63-79`）不读标志（case 内 break 标志穿透）；`fallthroughs` 数组存在但解析器恒置 false（首匹配即返回，无贯穿）。
- **break 静态检查**：`LexicalScopeAnalysis.java:902-904` 要求 `scope.isInLoop()`，switch 不计入；错误码 `ERR_XLANG_BREAK_STATEMENT_NOT_IN_LOOP`（`XLangErrors.java:312`）。scope 层为 `XLangCompileScope.enterLoop/leaveLoop/isInLoop`（`:464-477`）→ `XLangBlockScope.loopLevel` 计数（`:165-185`）。
- **java 后端 break 翻译**：`genJump`（`ExecToJavaTranslator.java:1367-1395`）循环内发原生标签 break；生成方法内循环外直接 unsupported("break statement outside loop")。`genSwitch`（`:1043-1090`）用 `$swK:{...break $swK;}` 标签块模拟。
- **语料影响面（迁移审计结论）**：全平台模型文件（排除 target/_delta）仅 `nop-dev-tools/nop-idea-plugin/src/test/resources/_vfs/test/reference/a.xlib` 含 `try`（语法参考 fixture）；`switch(` 仅 2 个测试 fixture（`nop-kernel/nop-xlang/src/test/resources/xlang-compare/static-b/ctrl-switch.xpl` 及 `nop-xlang-java-e2e` 同名副本）。现有 braced switch 的 Executable 树形状在新机制下不变（`BlockExecutable.valueOf` 单语句简化），树指纹稳定，**无需迁移存量模型**。
- **代码生成链**：nop-xlang 的 pom 未声明 precompile 插件（`nop-kernel/nop-xlang/pom.xml:67-81` 仅 test-jar）；生成入口 `precompile/gen-xlang-parser.xgen`（渲染 `model/AntlrParserConfig.json` + `/nop/templates/antlr`）与 `gen-xlang-ast.xgen`（渲染 `model/ast/io/nop/xlang/ast/XLangAST.xjava` + `/nop/templates/ast`）；生成物（`parse/antlr/*`、`parse/_XLangASTBuildVisitor.java`、`ast/_gen/*`、`ast/XLangASTVisitor.java`）均提交入库。模板对可选语法元素自动生成 null 检查（`_XLangASTBuildVisitor.java:1901` 已有 `if(ctx.finalizer != null)`）。precompile 插件配置在父 POM pluginManagement（`nop-kernel/pom.xml:287-373`，phase=generate-sources），接入先例 `nop-record-mapping/pom.xml`。
- **AST 模型**：`TryStatement.finalizer` 无 mandatory 标注（可选，无需改模型）；`SwitchCase.consequent` 是单 `Expression`（`XLangAST.xjava:127-135`，需改为语句列表）。`XLangASTVisitor.java` 为 XGEN 生成（`__XGEN_FORCE_OVERRIDE__` 头）。
- **双轨解析器**：try/switch 是语句级语法，仅 ANTLR 轨涉及；`SimpleExprParser`（`${}` 表达式轨）不需要同步。

## Goals

- `try {} catch(e) {}`（无 finally）合法且完整可执行：catch 变量正确绑定异常对象，catch 体正常完成后**吞掉异常继续执行**（JS 语义）；显式 `throw e` 可重抛；finally 语义不变（可选、必执行）。
- `switch` case 体支持语句序列（无花括号）；`break` 在 case 内合法且语义=终止整个 switch（不引入 JS 贯穿）；现有"首匹配即返回"语义不变。
- 解释器 / nop-xlang-java / nop-xlang-truffle 三后端语义一致（经 xlang-compare 对拍验证）。
- 存量零回归：现有模型语料行为不变、`ctrl-switch.xpl` 树指纹不变。

## Non-Goals

- `var` 支持（用户裁定暂缓，另行立项）。
- switch JS 贯穿语义（`case 2: case 3:` 多标签贯穿）——明确不支持；遇到时由用户改写为 `if (x == 2 || x == 3)`。
- 模板字符串插值、调用点 spread 等其他 JS 兼容项（后续独立 plan）。
- `SimpleExprParser` 表达式轨的任何改动。
- parse error hint 机制（P0-a 另行处理）。

## Scope

### In Scope

- `nop-kernel/nop-xlang`：g4 文法、XLangAST.xjava 模型、重生成产物（antlr parser/_XLangASTBuildVisitor/_gen/XLangASTVisitor 等）、LexicalScopeAnalysis、BuildExecutableProcessor、TypeInferenceProcessor、`functions/GlobalFunctions.java`（SWITCH 宏随模型迁移）、`exec/TryExecutable`、`exec/SwitchExecutable`、`IXLangCompileScope`/`XLangCompileScope`/`XLangBlockScope`（switch 层级计数）、`XLangErrors`（break 检查条件与描述调整，错误码 id 不变）
- `nop-kernel/nop-xlang-java`：`ExecToJavaTranslator.genTry/genSwitch/genJump`
- `nop-kernel/nop-xlang-truffle`：`XTryNode`、`XSwitchNode`（及其翻译器如需）
- 测试：`nop-xlang` exprs 语料 + xlang-compare fixture；java/truffle 覆盖率测试对齐新语义
- 文档：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`、`docs/dev-guide/xlang/xscript.md`、`ai-dev/logs/`

### Out Of Scope

- 所有其他 JS 兼容语法（见 Non-Goals）
- nop-xlang pom 永久接入 precompile 插件（仅临时用于重生成；是否永久接入另行裁定）
- idea-plugin 的 a.xlib fixture 内容更新（仅为语法参考，不参与执行链路验证；若语法兼容则不动）

## Execution Plan

### Phase 1 - 文法、AST 模型与重生成

Status: planned
Targets: `nop-kernel/nop-xlang/model/antlr/XLangParser.g4`、`model/ast/io/nop/xlang/ast/XLangAST.xjava`、生成产物、`scope/*`

- Item Types: `Fix`

- [ ] g4：`tryStatement` 的 `finalizer=blockStatement_finally` 改为可选（`(...)?`）
- [ ] g4：新增中间 list rule `caseStatements_`（带 `elementAstNodeName=Statement` 选项，模式对齐 `statements_`/`variableDeclarators_` 的既有惯例）；`switchCase` 与 `statement_defaultClause` 的 consequent/default 体均改用 `caseStatements_`。注意：不可直接写 `consequent=statement*`（codegen 模型走 list-rule 路径），也不可复用 `statements_`（其 `elementAstNodeName=Expression`，Java 泛型不型变，无法赋给 `List<Statement>`）；`default:` 分支必须与 `case:` 同步支持语句序列，否则行为不一致
- [ ] XLangAST.xjava：`SwitchCase.consequent: Expression` → `List<Statement>`、`SwitchStatement.defaultCase: Expression` → `List<Statement>`（保持字段名）
- [ ] 模型迁移连带修复（同模块编译依赖，Blocker）：`TypeInferenceProcessor.processSwitchCase`（`:339-345` 单节点假设）改为遍历语句列表；`GlobalFunctions.SWITCH` 宏将 `caseExpr.setConsequent(...)`（`:377` 附近）与 `stm.setDefaultCase(...)`（`:383` 附近）的表达式包装为语句列表并保持 `asExpr` 返回值语义；`TestTypeInferenceProcessor`（`:1150` 附近）同步迁移
- [ ] 重生成 ANTLR parser + `_XLangASTBuildVisitor` + ast 类族（`_SwitchCase`、`_SwitchStatement`、`XLangASTVisitor` 等）；生成物 diff 仅含本次预期变更；重跑生成验证幂等（二次生成无 diff）
- [ ] LSA（LexicalScopeAnalysis）：新增 `visitCatchClause`——在进入 catch body 前用 `makeVarDeclaration(name, IdentifierKind.VAR_DECL, false)` 声明 catch 变量（模式对齐 `visitForOfStatement:461-473`；声明放在 visitCatchClause 而非 visitTryStatement，保证 `try{let e}catch(e){}` 作用域正确）
- [ ] LSA：新增 `visitSwitchCase`/default 子句处理——进入块作用域（模式对齐 `visitBlockStatement:171`）遍历语句列表，保证 case 内 `let` 的块级作用域与重声明检查
- [ ] scope 层增加 switch 层级：`IXLangCompileScope.enterSwitch/leaveSwitch/isInSwitch` + `XLangCompileScope` 委托 + `XLangBlockScope.switchLevel` 计数（模式对齐 loopLevel `:165-185`）
- [ ] LSA break 检查放宽：`visitBreakStatement`（`:901-904`）改为 `isInLoop() || isInSwitch()`；`visitContinueStatement` 保持仅循环

Exit Criteria:

- [ ] `./mvnw compile -pl nop-kernel/nop-xlang` 通过（含重生成产物）
- [ ] 重生成幂等：连续两次生成，git status 无新增 diff
- [ ] `case 1: doA();`（无花括号）与 `try{}catch(e){}`（无 finally）均可通过解析并产出正确 AST（临时验证手段：单测或现有解析入口）
- [ ] 生成产物 diff 审查：无与本计划无关的意外变更（尤其 gen-xlang-xdsl 触发的其他文件）
- [ ] `ai-dev/logs/` 条目已更新

### Phase 2 - 解释器执行语义

Status: planned
Targets: `compile/BuildExecutableProcessor.java`、`exec/TryExecutable.java`、`exec/SwitchExecutable.java`

- Item Types: `Fix`

- [ ] 实现 `BuildExecutableProcessor.processTryStatement`：构建 `TryExecutable`（body/catch/finally 可空），exceptionSlot 取自 catch 变量的槽位（模式参考 `processForOfStatement:723-735` 的 `getVarDeclaration().getVarSlot()`；无 catch 时传 -1，`TryExecutable.java:44` 已处理）
- [ ] 重写 `BuildExecutableProcessor.processSwitchStatement`（`:575-595`）：case 体与 default 体按 `List<Statement>` 用 `buildBlock` 构建（`BlockExecutable.valueOf` 单语句经 `simplifySimpleBlock` 还原，保持既有 braced 写法的 Executable 树形状与指纹稳定）
- [ ] `TryExecutable.execute`：catch 体正常完成 → 吞异常（删除无条件 `throw NopException.adapt(e)`）；catch 体自身抛出（含显式 `throw e`）→ 传播新异常；finally 语义不变
- [ ] `SwitchExecutable.execute`：case 体执行后检查 `rt.getExitMode()==ExitMode.BREAK` → 消费标志（置 null）并返回；RETURN/CONTINUE 标志行为不变（穿透）
- [ ] `xlang-compare` 语料中现有 `ctrl-switch.xpl` 期望值保持不变（回归哨兵）

Exit Criteria:

- [ ] 新增 exprs 语料 `try-catch.test.md` 通过：catch 吞异常、catch 变量绑定异常对象、`throw e` 重抛、finally 必执行、无 finally 合法、try 内 return 正常返回、**catch/finally 体内 break/continue/return 语义与体外一致**（跨后端一致性的关键用例）
- [ ] 新增 exprs 语料 `switch.test.md` 通过：无花括号 case 体、case 内 break 终止 switch、break 后续语句不执行、case 内 let 块作用域、**嵌套最内层优先**（case 体内循环的 break 仍终止循环；循环内 switch 的 break 终止 switch 而非循环）、switch 后代码继续执行
- [ ] 既有全部 exprs 语料（`TestXScript`）与 xlang-compare 解释器侧测试通过
- [ ] `ai-dev/logs/` 条目已更新

### Phase 3 - java / truffle 后端一致

Status: planned
Targets: `nop-xlang-java …/ExecToJavaTranslator.java`、`nop-xlang-truffle …/XTryNode.java`、`XSwitchNode.java`、`ExecToTruffleTranslator.java`（如需）

- Item Types: `Fix`

- [ ] `genTry`：catch 分支不再无条件发射 `throw NopException.adapt($eN)`（`:1213`）；吞异常语义与解释器一致（含 $tN 赋值路径与 jumpCtx 处理的正确性）
- [ ] `genJump` + `genSwitch`：翻译器 GenContext 维护 switch 标签栈（模式对齐现有 `loopDepth()/currentLoopLabel()/currentIterLabel()`）；break 分派按**最近进入的最内层构造**（比较 loop 栈与 switch 栈进入深度，不可固定 loop 优先），与解释器"最内层优先"语义一致；cellMode 分支放在 loop/switch 之后（`break $swK` 在 cellMode 下同样合法）；case 内 break（非循环内）发射 `break $swK;` 终止 switch 标签块；循环内 break 行为不变
- [ ] truffle `XTryNode`：删除 catch 体执行后必然重抛（`:53`）；**catch 体与 finally 体的 `XLControlFlowException` 从吞噬（`:48-52`、`:56-60`）改为放行重抛**——否则 catch/finally 体内的 break/continue/return 在 truffle 下静默丢失（body 的放行逻辑 `:39-41` 保留）
- [ ] truffle `XSwitchNode`：case 体执行后捕获 `XLBreakException` → 终止 switch 返回（先例 `XWhileNode.java:26`）；循环内 break 仍由 `XWhileNode` 等先消费
- [ ] java/truffle 现有覆盖率测试（`TestExecToJavaTranslatorCoverageB`、`TestTranslatorCoverageB`/`CoverageNodes`）中对旧重抛语义的断言更新为新语义（如 `TestTranslatorCoverageB.java:234/243/433`）

Exit Criteria:

- [ ] `./mvnw test -pl nop-kernel/nop-xlang-java -am` 通过
- [ ] `./mvnw test -pl nop-kernel/nop-xlang-truffle -am` 通过
- [ ] xlang-compare 对拍：新增 ctrl-try.xpl、扩展 ctrl-switch.xpl（含 break/无花括号 case/**循环内嵌套 switch 内 break 终止 switch** 用例）fixtures，解释器与 java 后端结果一致（`static-b` corpus 测试通过）
- [ ] `ai-dev/logs/` 条目已更新

### Phase 4 - 全量验证、文档与收口

Status: planned
Targets: 全仓受影响模块、`docs-for-ai/`、`docs/dev-guide/xlang/`、`ai-dev/`

- Item Types: `Proof | Follow-up`

- [ ] 受影响模块全量验证：`./mvnw install -pl nop-kernel/nop-xlang,nop-kernel/nop-xlang-java,nop-kernel/nop-xlang-truffle -am`；再以 `-DskipTests` 全仓 install 验证下游模块编译（含 nop-wf/nop-orm 等重 xlib 使用方）
- [ ] 语料迁移终审：确认 nop-idea-plugin a.xlib 与两处 ctrl-switch.xpl 在新语法下解析/执行不变
- [ ] `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`：新增/更新 try/catch/finally 与 switch 语义说明（catch 默认吞异常、`throw e` 重抛、break 语义、无贯穿）
- [ ] `docs/dev-guide/xlang/xscript.md`：更新"从 JavaScript 语法中去除的特性"清单中相关条目
- [ ] `ai-dev/logs/2026/09-01.md` 收口记录；analysis 报告补充实施状态链接
- [ ] commit（grammar+生成物 / 执行语义 / 后端 / 测试文档 分批或合逻辑提交）

Exit Criteria:

- [ ] 全部构建与测试命令退出码 0
- [ ] 文档链接检查 `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 收口记录含验证证据
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码 0

## Closure Gates

- [ ] try/catch/finally 在解释器下完整可执行且 catch 默认吞异常（exprs 语料验证）
- [ ] switch 语句体 + break 在解释器下符合 Goals 描述（exprs 语料验证）
- [ ] 三后端对拍一致（xlang-compare static-b fixtures 通过，含 java 后端）
- [ ] 存量零回归：全部既有 exprs 语料 + xlang-compare 既有 fixtures + ctrl-switch 期望值不变
- [ ] 无被静默降级的 in-scope 项；Non-Goals 明确记录（var、贯穿语义、hint 机制）
- [ ] owner docs 已同步（xlang-and-xpl-basics.md、xscript.md）
- [ ] 独立子 agent closure audit 完成并写入 Closure 段落
- [ ] `./mvnw install -pl nop-kernel/nop-xlang,nop-kernel/nop-xlang-java,nop-kernel/nop-xlang-truffle -am` 通过
- [ ] checkstyle / 代码规范：import 分组、错误处理规范（NopException + 错误码）符合 AGENTS.md 约定

## Deferred But Adjudicated

### var 声明支持

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户 2026-09-01 明确裁定"暂时不动 var"；本计划的 JS 兼容目标不含 var。
- Successor Required: `no`（将来按 analysis 报告 var≡let 方案另行立项）

### switch JS 贯穿语义（case 2: case 3:）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 贯穿会改变存量"首匹配即返回"行为，属破坏性变更；多标签场景可用 `if (x==2||x==3)` 等价表达。AST 的 fallthrough 字段保留不动。
- Successor Required: `no`

### parse error hint 机制（修正建议文本）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 属 AI 工作流体验优化，非语法/语义修复本体；本次修复后高频误写大多直接合法化，hint 价值下降。
- Successor Required: `no`

## Non-Blocking Follow-ups

- catch 变量的闭包捕获（useRef）场景未专项测试——解释器经槽位直写，与 for-of index 变量同类；如后续发现闭包捕获 catch 变量缺陷再立项。

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:
