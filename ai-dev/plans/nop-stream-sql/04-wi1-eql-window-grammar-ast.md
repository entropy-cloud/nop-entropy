# 04 WI1 EQL 窗口 grammar 与 AST 补全

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/nop-stream-sql-roadmap.md（WI1 行、§3.3、D3 裁定行、Cross-Cuting 2/4、FU-2）
> Related: 先例 plan ai-dev/plans/2256-eql-arithmetic-precedence-fix.md（生成链路证据）
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 落点注（审查 B1 修正）：roadmap WI1 行写「BaseRule.g4 接受……命名窗口」，实测命名窗口子句的语法落点为 DMLStatement.g4 的 sqlQuerySelect 规则（BaseRule.g4 只含表达式层规则，无 SELECT 子句序列）；本 plan 按 live 代码修正落点，roadmap 行文本不回改（其「BaseRule.g4」应读作「EQL grammar」），WI24 收口时顺带核对。

## Purpose

让 EQL 语法层接受标准分析窗口的完整形态（`OVER (PARTITION BY ...)` 单子句、`OVER ()` 空参、frame 三单位 ROWS/RANGE/GROUPS、命名窗口 `WINDOW w AS (...)` 与 `OVER w` 引用），AST 模型同步扩展并重生成 parser。只做语法与 AST，不做 SQL 打印与方言开关（WI2 承接）。D3 分层原则下语法全集直增、不做方言条件分支。

## Current Baseline

- **分析窗口（OVER）**：`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` `sqlWindowExpr`（:221-225）——`function=sqlWindowFunction_ OVER LP_ partitionBy=sqlPartitionBy orderBy=sqlOrderBy RP_`，两子句均无 `?` 必填；无 frame 分支；无命名窗口引用。
- **命名窗口（WINDOW 子句）**：SELECT 子句序列在 `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` `sqlQuerySelect`（:94-104，WHERE→GROUP BY→HAVING→ORDER BY→LIMIT→FOR UPDATE），无 WINDOW 子句位。**BaseRule.g4 不含任何 SELECT 级规则。**
- **token 盘点（2026-10-02 实测）**：已有 `ROWS`（SQL92Keyword.g4:574，未使用）、`CURRENT`（Keyword.g4:457）、`OVER`（Keyword.g4:561）；**不存在** `RANGE`/`WINDOW`/`GROUPS`/`PRECEDING`/`FOLLOWING`/`ROW`（单数）/`UNBOUNDED`——frame 语法（`CURRENT ROW`、`UNBOUNDED PRECEDING`）需要 `ROW` 与 `UNBOUNDED`，缺一不可词法化。
- **标识符遮蔽面**：`sqlIdentifier_ = IDENTIFIER_ | unreservedWord_`（BaseRule.g4:69-94，先例 GROUP/ORDER/DATE 已登记 unreservedWord_）；新增关键字若不登记 unreservedWord_，`SELECT range FROM t`、别名 `AS window` 等既有可用写法将解析失败。快速扫描未发现仓内既有 EQL 用这些裸词做标识符，但仍需登记 + 抽查（见 Phase 2/3）。
- **AST 模型** `model/ast/io/nop/orm/eql/ast/EqlAST.xjava`：`SqlWindowExpr`（:449-453）字段 function/partitionBy/orderBy 皆可空；`SqlQuerySelect`（:149-164）无 window 字段。
- **生成链路**：`nop-persistence/nop-orm-eql/model/AntlrParserConfig.json`（antlrModelPath=antlr/Eql.g4 import BaseRule.g4、astModelPath=EqlAST.xjava、mainRule=sqlProgram）；根 pom exec-maven-plugin `precompile`/`precompile2` 绑 generate-sources 执行 `precompile/gen-eql-parser.xgen` 与 `gen-eql-ast.xgen`。**生成物清单（审查实证）**：`src/main/java/io/nop/orm/eql/parse/antlr/*`、`ast/_gen/_*.java`、`ast/Sql*.java`（**新节点类直接生成在 ast/ 根目录**）、`ast/EqlASTKind.java`、`ast/EqlASTVisitor.java`、`ast/EqlASTProcessor.java`、`ast/EqlASTOptimizer.java`、`parse/_EqlASTBuildVisitor.java`、`parse/EqlASTParser.java`（生成器输出，一律生成不手改；其中 parse/antlr 与 _gen 基类带 `__XGEN_FORCE_OVERRIDE__` 标记，ast/Sql*.java 包装类为纯模板输出）。生成器按 ruleName→AST 类名**硬映射并强校验**（`ERR_GRAMMAR_INVALID_AST_NODE_NAME`）：**每条新 grammar 规则必须有同名 AST 类**，标签名必须与 AST 字段名一一对应。
- **手写钩子**：`parse/EqlASTBuildVisitor`（手写子类）实现生成基类 `parse/_EqlASTBuildVisitor.java` 的抽象钩子（如 `SqlWindowExpr_function(ParseTree)` :1520）。枚举/String 型 prop 需手写 prop-parse 钩子；节点型字段装配（如 WINDOW 子句挂到 select）由生成器自动产出。
- **round-trip 不对称（防测试踩坑）**：WI1 后 `OVER w`/frame 可 parse，但 `AstToEqlGenerator.visitSqlWindowExpr`（:915）只打印两子句——新语法 AST 打印会静默丢失 frame/命名窗口（`OVER w` 打成 `over(  )`）。**WI1 的测试不得用 toSQL() 往返当 oracle**；打印修复归 WI2。
- 既有消费点（本计划不改）：`EqlTransformVisitor.java:1372-1383` 窗口函数方言校验；既有测试 `TestEqlCompiler`（nop-orm 模块，:182 附近 testWindowExpr 用 OVER 双子句）；AST 字段断言先例 `TestEqlAstParser`（nop-orm）/`TestEqlParser`（nop-orm-eql），API：`new EqlASTParser().parseFromText(null, sql)` → `SqlProgram` → cast `SqlQuerySelect`。
- nop-orm-eql 唯一直接下游是 nop-orm（pom 实证）；`EqlASTVisitor` 全部子类（EqlTransformVisitor、AstToEqlGenerator、AstToSqlGenerator、CollectionOperatorTransformer、SqlParamTypeResolver）都在 nop-orm-eql 内——新增默认 visit 方法无跨模块编译破坏。

## Goals

- grammar：
  - `sqlWindowExpr` 接受 `OVER (spec)` 与 `OVER windowName` 两种形态；spec 内 partitionBy? orderBy? frame? 三件**内联**（**不设 sqlWindowSpec 中间规则**——生成器 ruleName↔AST 类名硬映射强校验，中间规则必须有同名 AST 类；内联方案不新增 SqlWindowSpec 类，AST 清单保持四类，审查 B2 裁定）。
  - frame = ROWS|RANGE|GROUPS BETWEEN bound AND bound 或 单 bound 简写；bound = UNBOUNDED PRECEDING|UNBOUNDED FOLLOWING|CURRENT ROW|expr PRECEDING|expr FOLLOWING。
  - `sqlQuerySelect`（DMLStatement.g4）挂 WINDOW 子句，位置钉死在 HAVING 之后、ORDER BY 之前（标准 SQL 位置，WI2 打印依赖此契约）。
- token：新增 RANGE/WINDOW/GROUPS/PRECEDING/FOLLOWING/ROW/UNBOUNDED 七个（ROWS 复用既有；CURRENT 复用 Keyword.g4 既有），并**逐一登记 `unreservedWord_`**（GROUP/ORDER/DATE 先例），防既有标识符写法回归。
- AST：`SqlWindowExpr` 加 frame 与 windowName 字段；新增 `SqlWindowFrame`（unit + start/end bound）、`SqlWindowFrameBound`（boundType + offset expr）、`SqlWindowDecl`（name + partitionBy + orderBy + frame）、`SqlWindowClause`（decls）；`SqlQuerySelect` 加 windowClause 字段。
- 手写钩子：frame 单位枚举、bound 类型枚举、`OVER w` 的 windowName、decl 的 name 共约 4 个 prop-parse 钩子（节点装配由生成器产出）。
- 测试：parse 矩阵（success/fail）+ **关键字段断言**（每能力至少一条断言 AST 字段值，不用 toSQL 往返）+ 全仓 EQL 字面量标识符碰撞抽查。
- 门禁：`./mvnw test -pl nop-orm-eql -am` 与 nop-orm 回归绿；scan-hollow 高危零发现；迁移说明落 owner doc。

## Non-Goals

- 不改 `AstToEqlGenerator`/`AstToSqlGenerator` 的 frame 与命名窗口打印（WI2）；**测试不得以 toSQL 往返为新语法 oracle**（round-trip 不对称，见 Baseline）。
- 不改 `EqlTransformVisitor` 窗口函数方言校验逻辑（新节点默认 visit 继承即可，无行为变化）。
- 不做流侧任何内容（窗口 assigner 映射归 WI10/WI17）。
- 不动既有 token 语义；仅新增七个缺失 token + unreservedWord_ 登记。
- `docs-for-ai/04-reference/source-anchors.md` 裁定：本计划新增的 AST 类与枚举属 nop-orm-eql 内部实现锚点，若该文件现有锚点粒度未覆盖 ast/ 目录则**不新增**（记录裁定即可）；若已有 ast/ 锚点则同步。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（sqlWindowExpr 扩展 + frame/decl 规则 + unreservedWord_ 登记）
- `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`（仅 sqlQuerySelect 挂 WINDOW 子句一处；审查 B1 修正的落点）
- `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`（新增 token）
- `model/ast/io/nop/orm/eql/ast/EqlAST.xjava`
- 生成物再生成（一律生成，不手改）：`parse/antlr/*`、`ast/_gen/*`、`ast/Sql*.java`（含 4 个新文件）、`EqlASTKind/Visitor/Processor/Optimizer`、`parse/_EqlASTBuildVisitor.java`、`parse/EqlASTParser.java`
- 手写 `EqlASTBuildVisitor` 新 prop-parse 钩子实现
- 新枚举（SqlWindowFrameType/SqlWindowFrameBoundType）放 `io.nop.orm.eql.enums`
- 新增测试类（parse 矩阵 + 字段断言），与既有 EQL 测试同模块（TestEqlCompiler 在 nop-orm；TestEqlParser 在 nop-orm-eql——执行时按矩阵用例依赖选择落位并记录）
- 文档：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 窗口口径最小更新 + 新关键字迁移说明；check-import-order 检查

### Out Of Scope

- WI2/WI3/WI5/WI12 的全部内容；`dialect.xdef`/`*.dialect.xml`。
- 除上述三处外的其它 g4 规则改动（DMLStatement.g4 仅 sqlQuerySelect 一处）。
- `EqlParser.java` 等生成物手改（禁止）。

## Execution Plan

### Phase 1 - 红：parse 矩阵测试先行

Status: completed
Targets: EQL 测试模块（新增 TestEqlWindowGrammarParse）

- Item Types: `Proof`

- [x] parse 矩阵用例：OVER 仅 PARTITION BY / 仅 ORDER BY / OVER () 空参 / OVER w 命名引用 / WINDOW 声明 / **WINDOW 与 ORDER BY 同现（钉子句顺序）** / ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW / RANGE BETWEEN expr PRECEDING AND expr FOLLOWING / GROUPS 单 bound 简写 / fail-fast（BETWEEN 缺 AND、OVER 缺括号且非名字、WINDOW 声明缺 spec）
- [x] 修复前运行确认新语法用例红（10 个新语法用例解析失败、3 个 fail-fast 与 1 个既有形态用例通过）；既有窗口用例基线绿（nop-orm TestEqlCompiler 在 nop-orm 回归绿）

Exit Criteria:

- [x] 矩阵用例全部就位且修复前按预期失败（修复前：Tests run 14, Errors 10——新语法用例全部红）
- [x] 既有窗口用例基线绿

### Phase 2 - grammar 与 AST 模型变更

Status: completed
Targets: BaseRule.g4、DMLStatement.g4、SQL92Keyword.g4、EqlAST.xjava

- Item Types: `Feature`

- [x] SQL92Keyword.g4 新增 RANGE/WINDOW/GROUPS/PRECEDING/FOLLOWING/ROW/UNBOUNDED 七 token，放正确关键字分区；**逐一登记 BaseRule.g4 `unreservedWord_`**（先例 GROUP/ORDER/DATE）
- [x] BaseRule.g4：sqlWindowExpr 扩展为 `OVER (spec)` / `OVER name` 两形态，spec 三件**内联**；新增 sqlWindowFrame / sqlWindowFrameUnit_ / sqlWindowFrameBound / sqlWindowFrameBoundType_ / sqlWindowClause / sqlWindowDeclItems_ / sqlWindowDecl 规则。执行偏差（见日志）：frame bound 因生成器「同一 prop 不得跨备选分支 + 节点规则子元素必须有标签」双重约束，改为 `offset=sqlExpr? boundType=sqlWindowFrameBoundType_` 单序列形态 + 手写归一化
- [x] DMLStatement.g4：sqlQuerySelect 在 HAVING 之后 ORDER BY 之前挂 windowClause=sqlWindowClause
- [x] EqlAST.xjava：SqlWindowExpr 加 frame/windowName；新增 SqlWindowFrame/SqlWindowFrameBound/SqlWindowDecl/SqlWindowClause；SqlQuerySelect 加 windowClause
- [x] 新枚举 SqlWindowFrameType 与 SqlWindowFrameBoundType

Exit Criteria:

- [x] `./mvnw generate-sources -pl nop-persistence/nop-orm-eql` 生成成功（历经 4 次试错：duplicate-prop-label、alt label 与规则同名冲突、rule-alternative-is-not-ast-node，最终形态见上）
- [x] 生成物 diff 白名单核对：仅 `parse/antlr/*`、`ast/_gen/*`、`ast/Sql*.java`（4 新文件）、`EqlASTKind`、`EqlASTVisitor`、`EqlASTProcessor`、`EqlASTOptimizer`、`parse/_EqlASTBuildVisitor`、`parse/EqlASTParser` 及 `.tokens`/`.interp`（git status 实测 27 文件全部在白名单内）
- [x] 本 Phase 无 owner-doc 更新（owner doc 更新归 Phase 3 一并落）

### Phase 3 - 手写钩子实现与转绿

Status: completed
Targets: EqlASTBuildVisitor、测试、owner doc

- Item Types: `Feature`

- [x] 手写 EqlASTBuildVisitor 实现新 prop-parse 钩子：SqlWindowFrame_unit（枚举）、SqlWindowFrameBound_boundType（枚举）、SqlWindowExpr_windowName、SqlWindowDecl_name + 两个行为覆盖（visitSqlWindowFrameBound 的 unbounded 归一化、visitSqlWindowFrame 的 BETWEEN/end 成对 fail-fast 校验，新增错误码 nop.err.eql.invalid-window-frame）
- [x] **字段断言扩展**：7 个字段断言用例（OVER 双子句/空参/frame 三单位/unbounded 归一化/单 bound 简写/命名窗口/子句顺序），经 parseFromText 全链路，未使用 toSQL 往返
- [x] **标识符碰撞抽查**：.sql/.eql 资源与 _vfs 内嵌 EQL grep 无既有查询以七个新关键字作标识符（唯一命中为新增测试自身）
- [x] TestEqlWindowGrammarParse 21/21 绿；`./mvnw test -pl nop-persistence/nop-orm-eql` 80 测试全绿；nop-orm 208 测试全绿（6 skip 为 docker opt-in）
- [x] owner doc 更新：eql-and-database-compatibility.md 新增「分析窗口语法」小节（语法层 vs 方言层口径 + 七关键字迁移说明）；source-anchors.md 裁定 = 现有锚点粒度是特定用途类（如 TNT-003 EqlTransformVisitor），新增 AST 节点类与枚举属实现内部不新增锚点；check-doc-links --strict 退出码 0
- [x] import 人工核对：新增 import 按 io.nop.* → 第三方 → java.* 分组、静态导入置尾，符合仓库惯例（check-import-order 工具为全仓 advisory，既有 2153 处违规非本计划引入）

Exit Criteria:

- [x] parse 矩阵全绿（21/21，含 fail-fast 断言与字段断言）
- [x] nop-orm-eql 80 测试全绿；nop-orm 回归绿
- [x] 标识符碰撞抽查完成且无回归
- [x] 无静默跳过：BETWEEN/end 残缺形态显式抛 nop.err.eql.invalid-window-frame
- [x] owner doc 更新含迁移说明；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/ 当日条目更新

### Phase 4 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：核验矩阵用例真实断言（含字段断言）、生成物未手改（diff 白名单核对 34 文件零越界）、unreservedWord_ 登记、回归结果；裁定 PASS；证据落 ai-dev/audits/nop-stream-sql/wi1-closure-audit.md 与 plan Closure 段
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0（执行与 audit 双重复核）
- [x] ai-dev/logs/ 当日条目更新（roadmap 状态变更三处同步）
- [x] audit 通过后 roadmap WI1 `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符）；解析器翻转后实测 items 31 milestones 7
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] scan-hollow 高危零发现；roadmap WI1 = done，解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0

## Closure Gates

- [x] grammar 四能力（单子句/空参/frame 三单位/命名窗口）各有 parse 用例 + 字段断言证明（audit 逐用例比对）
- [x] 七个新 token 全部登记 unreservedWord_，标识符碰撞抽查无回归（audit 独立重跑 grep）
- [x] 生成物全部来自生成器（audit 程序化白名单匹配 34 文件零越界、无手工痕迹）
- [x] nop-orm-eql 80 测试绿；nop-orm 208 测试绿（audit 双复跑）
- [x] scan-hollow --module nop-orm-eql --severity high 退出码 0
- [x] 无静默跳过、无空壳实现（BETWEEN/end 双方向显式抛错，audit 读码确认）
- [x] owner doc（eql-and-database-compatibility.md）已更新且含迁移说明；source-anchors 裁定已记录（不新增）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，裁定 PASS）
- [x] Anti-Hollow Check：SQL 文本→EqlASTParser→AST 字段断言链路有测试证明（非仅类型存在）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/04-wi1-eql-window-grammar-ast.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- FU-2（token 归位登记）在 Phase 2 一并落实；roadmap FU-2 行的 token 列表不全（缺 ROW/UNBOUNDED/RANGE），以本 plan 实际 token 集为准，WI24 收口时顺带回写说明。

## Closure

Status Note: EQL 语法层现接受完整分析窗口形态（OVER 单子句/空参、frame 三单位、命名窗口），AST 同步扩展并重生成，21 矩阵用例 + 7 字段断言全绿，双模块回归绿，生成物白名单零越界。执行偏差（frame bound 单序列 + 手写归一化）已如实记录且不改变外部行为。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi1-closure-audit.md
- Evidence:
  - 四能力 21/21 实测绿且字段断言真实（audit 逐用例比对测试源码，零 toSQL 往返）
  - fail-fast 双方向显式抛 nop.err.eql.invalid-window-frame，无静默跳过
  - 生成物纪律：34 变更文件程序化白名单匹配零越界、无手工痕迹
  - 回归：nop-orm-eql 80 绿、nop-orm 208 绿（audit 双复跑）；scan-hollow high 退出码 0
  - 七 token 登记 + unreservedWord_ 实证；标识符兼容性由 288 个既有测试全绿背书
  - owner doc / 日志 / check-doc-links --strict 0 全部实测通过
  - 3 Minor（白名单计数快照、AND 缺 BETWEEN 用例转 WI2、ARG_VALUE 载荷语言张力）记录备查不阻塞
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/04-wi1-eql-window-grammar-ast.md --strict` 退出码 0

Follow-up:

- Minor-2 转 WI2：补「AND 第二边界缺 BETWEEN」方向专项用例
- roadmap FU-2 行 token 列表不全（缺 ROW/UNBOUNDED/RANGE），WI24 收口时回写
