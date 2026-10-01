# 04 WI1 EQL 窗口 grammar 与 AST 补全

> Plan Status: draft
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/nop-stream-sql-roadmap.md（WI1 行、§3.3、D3 裁定行、Cross-Cuting 2/4）
> Related: 先例 plan ai-dev/plans/2256-eql-arithmetic-precedence-fix.md（生成链路证据）
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

让 EQL 语法层接受标准分析窗口的完整形态（`OVER (PARTITION BY ...)` 单子句、`OVER ()` 空参、frame 三单位 ROWS/RANGE/GROUPS、命名窗口 `WINDOW w AS (...)` 与 `OVER w` 引用），AST 模型同步扩展并重生成 parser。只做语法与 AST，不做 SQL 打印与方言开关（WI2 承接）。D3 分层原则下语法全集直增、不做方言条件分支。

## Current Baseline

- `BaseRule.g4` 的 `sqlWindowExpr`（:221-225 附近，实测）：
  ```
  sqlWindowExpr
      : function=sqlWindowFunction_ OVER LP_
          partitionBy=sqlPartitionBy orderBy=sqlOrderBy
      RP_ ;
  ```
  两子句均无 `?` 必填；无 frame 分支；无命名窗口。`ROWS` token 已存在于 `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4:574`（未使用）；`PRECEDING`/`FOLLOWING`/`GROUPS`/`WINDOW` token 待确认或新增（计划执行时实测）。
- AST 模型 `model/ast/io/nop/orm/eql/ast/EqlAST.xjava`：`SqlWindowExpr`（:449-453）字段 function/partitionBy/orderBy，三者皆可空（模型层本就无必填约束，语法层强制了两者）；`SqlQuerySelect`（:149-164）无 WINDOW 子句字段。
- 生成链路（plan 2256 实证 + 2026-10-02 实测）：
  - `nop-persistence/nop-orm-eql/model/AntlrParserConfig.json`：antlrModelPath=antlr/Eql.g4（import BaseRule.g4）、astModelPath=ast/.../EqlAST.xjava、mainRule=sqlProgram。
  - 根 pom 绑定 exec-maven-plugin `precompile`（generate-sources 阶段）执行 `precompile/gen-eql-parser.xgen` 与 `gen-eql-ast.xgen`，生成物签入：`src/main/java/io/nop/orm/eql/parse/antlr/*`（EqlParser/EqlVisitor/EqlBaseListener 等）与 `src/main/java/io/nop/orm/eql/ast/_gen/_*.java`、`EqlASTKind.java`、`EqlASTVisitor.java`、`EqlASTProcessor.java`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java`。
  - `_EqlASTBuildVisitor` 为生成基类（抽象钩子如 `SqlWindowExpr_function(ParseTree)`），手写子类 `EqlASTBuildVisitor` 实现钩子；grammar 变更后新增钩子须在手写类补实现。
- 既有窗口消费点（本计划不改，防破坏面）：`EqlTransformVisitor.java:1372-1383` 窗口函数方言校验（按函数名，不感知 frame）；`AstToEqlGenerator.visitSqlWindowExpr` 打印现有两子句。
- 既有测试：`TestEqlCompiler.java:183` 附近有窗口用例（OVER 双子句形态）；全仓无 frame/命名窗口用例。

## Goals

- grammar：`sqlWindowExpr` 接受 `OVER (spec)` 与 `OVER windowName` 两种形态；spec = partitionBy? orderBy? frame?（三者全可省 → `OVER ()`）；frame = ROWS|RANGE|GROUPS BETWEEN bound AND bound 或 单 bound 简写；bound = UNBOUNDED PRECEDING|UNBOUNDED FOLLOWING|CURRENT ROW|expr PRECEDING|expr FOLLOWING。
- grammar：SELECT 语句支持命名窗口子句 `WINDOW w AS (spec) (, w2 AS (spec2))*`；`SqlQuerySelect` AST 挂对应字段。
- AST：`SqlWindowExpr` 增加 frame 与 windowName 引用字段；新增 `SqlWindowFrame`（unit + start/end bound）、`SqlWindowFrameBound`（boundType + offset expr）、`SqlWindowDecl`（name + spec 三件）、`SqlWindowClause`（decls）模型类；`EqlASTKind`/visitor/processor/build-visitor 基类随生成器更新。
- 手写 `EqlASTBuildVisitor` 补新钩子实现（frame 单位枚举、bound 类型、命名窗口名）。
- 新增 parse-success/fail 矩阵测试；`TestEqlCompiler` 既有用例回归绿。

## Non-Goals

- 不改 `AstToEqlGenerator`/`AstToSqlGenerator` 的 frame 与命名窗口打印（WI2）；不改方言能力位与 dialect.xml（WI2/WI3）。
- 不改 `EqlTransformVisitor` 的窗口函数方言校验逻辑（仅当编译新增节点导致其必须感知 frame 时做最小空实现，行为与现状一致）。
- 不做流侧任何内容（窗口 assigner 映射归 WI10/WI17）。
- 不动 `SQL92Keyword.g4` 中既有 token 语义；仅新增缺失 token（FU-2 的 token 归位登记在此一并落实：新增 token 放正确分区）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`、`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`（新增 token）
- `model/ast/io/nop/orm/eql/ast/EqlAST.xjava`
- 生成物再生成：`src/main/java/io/nop/orm/eql/parse/antlr/*`、`ast/_gen/*`、`EqlASTKind/Visitor/Processor`、`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/_EqlASTBuildVisitor.java`（一律生成，不手改）
- 手写 `EqlASTBuildVisitor` 新钩子实现
- 新增测试类（parse 矩阵），位于 nop-orm-eql 或既有 EQL 测试所在模块（执行时按既有测试分布实测——TestEqlCompiler 在 nop-orm，窗口 parse 测试与其同处）
- 文档：改 EQL 语法 → `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 窗口口径（最小改动：语法层接受度变化一句话；完整口径校正归 WI5）

### Out Of Scope

- WI2/WI3/WI5/WI12 的全部内容；`dialect.xdef`/`*.dialect.xml`。
- `EqlParser.java` 手改（禁止）；其它 g4。

## Execution Plan

### Phase 1 - 红：parse 矩阵测试先行

Status: planned
Targets: EQL 测试模块（新增 TestEqlWindowGrammarParse）

- Item Types: `Proof`

- [ ] 新增测试类，用例矩阵：OVER 单子句（仅 PARTITION BY）、OVER 单子句（仅 ORDER BY）、OVER () 空参、OVER w 命名引用、WINDOW 子句声明、ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW、RANGE BETWEEN expr PRECEDING AND expr FOLLOWING、GROUPS 单 bound 简写、fail-fast 用例（frame 残缺如 BETWEEN 缺 AND、OVER 缺括号且非名字）
- [ ] 修复前运行确认新语法用例红（解析失败）

Exit Criteria:

- [ ] 矩阵用例全部就位且修复前按预期失败
- [ ] 既有 TestEqlCompiler 基线绿（记录基线结果）

### Phase 2 - grammar 与 AST 模型变更

Status: planned
Targets: BaseRule.g4、SQL92Keyword.g4、EqlAST.xjava

- Item Types: `Feature`

- [ ] SQL92Keyword.g4 补缺失 token（PRECEDING/FOLLOWING/GROUPS/WINDOW，RANGE 若缺一并补），放正确关键字分区
- [ ] BaseRule.g4：sqlWindowExpr 扩展（OVER 两种形态 + spec 三件全可选 + frame 分支）；新增 sqlWindowFrame/sqlWindowFrameBound/sqlWindowSpec/sqlWindowClause 规则；SqlQuerySelect 对应规则挂 WINDOW 子句
- [ ] EqlAST.xjava：SqlWindowExpr 加 frame/windowName 字段；新增 SqlWindowFrame/SqlWindowFrameBound/SqlWindowDecl/SqlWindowClause；SqlQuerySelect 加 windowClause 字段

Exit Criteria:

- [ ] `./mvnw generate-sources -pl nop-orm-eql`（或 install）生成成功，无 grammar 冲突告警
- [ ] 生成物 diff 仅限预期文件（parse/antlr、ast/_gen、EqlASTKind、Visitor/Processor、_EqlASTBuildVisitor）

### Phase 3 - 手写钩子实现与转绿

Status: planned
Targets: EqlASTBuildVisitor、测试

- Item Types: `Feature`

- [ ] 手写 EqlASTBuildVisitor 实现新钩子：frame 单位（ROWS/RANGE/GROUPS→枚举）、bound 类型（UNBOUNDED PRECEDING/CURRENT ROW/UNBOUNDED FOLLOWING/expr PRECEDING/expr FOLLOWING）、命名窗口名、SqlQuerySelect 的 WINDOW 子句装配
- [ ] 枚举新增（如 SqlWindowFrameType/SqlWindowFrameBoundType）放 `io.nop.orm.eql.enums`，随既有枚举风格
- [ ] Phase 1 矩阵全绿；`./mvnw test -pl nop-orm-eql -am` 绿；TestEqlCompiler 与 EQL 既有测试回归绿；依赖 EQL 的 nop-orm 模块 `-pl nop-orm -am` 测试绿（语法变更可能波及既有解析性能/行为，实测为准）

Exit Criteria:

- [ ] parse 矩阵用例全绿（含 fail-fast 用例断言具体错误）
- [ ] `./mvnw test -pl nop-orm-eql -am` 与既有窗口用例回归绿
- [ ] 无静默跳过：新钩子对非法 token 组合抛错而非吞掉
- [ ] 新增公共行为有显式测试覆盖（parse 矩阵即覆盖）
- [ ] eql-and-database-compatibility.md 窗口口径最小更新；check-doc-links --strict 退出码 0
- [ ] ai-dev/logs/ 当日条目更新

### Phase 4 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（不同 task_id）：核验矩阵用例真实断言、生成物未手改（git diff 检查生成文件内容与 xgen 一致性抽查）、回归结果；证据落 ai-dev/audits/nop-stream-sql/wi1-closure-audit.md 与 plan Closure 段
- [ ] audit 通过后 roadmap WI1 `todo` → `done`（括注单层非嵌套）；解析器 31 + 7 复核
- [ ] Plan Status → completed；check-plan-checklist --strict 退出码 0

Exit Criteria:

- [ ] 独立 audit 证据落档两处
- [ ] roadmap WI1 = done，解析器 31 + 7
- [ ] check-plan-checklist --strict 退出码 0

## Closure Gates

- [ ] grammar 四能力（单子句/空参/frame 三单位/命名窗口）各有 parse 用例证明
- [ ] 生成物全部来自生成器（无手改生成文件）
- [ ] `./mvnw test -pl nop-orm-eql -am` 绿；受波及下游模块回归绿
- [ ] 无静默跳过、无空壳实现
- [ ] owner doc（eql-and-database-compatibility.md）已更新
- [ ] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [ ] Anti-Hollow Check：从 SQL 文本→EqlASTParser→AST 字段断言的完整链路有测试证明（非仅类型存在）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/04-wi1-eql-window-grammar-ast.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- FU-2（token 归位登记）在 Phase 2 一并落实，不另立计划。

## Closure

Status Note: （待 closure audit 后填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent: （待填写）
- Evidence: （待填写）

Follow-up:

- 无 plan-owned 剩余工作
