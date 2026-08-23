# 2256 EQL 算术优先级缺陷修复：g4 备选序对齐 SqlOperator 标准模型

> Plan Status: completed
> Last Reviewed: 2026-08-23
> Source: `ai-dev/analysis/2026-08/2026-08-23-direct-sql-usage-survey.md`（第二轮复核，Open Question"EQL 算术分组缺陷修复立项"）
> Related: `ai-dev/plans/2255-direct-sql-reduction.md`（发现该缺陷的 plan）
> Draft Review: 用户明确指示执行（"修改后重新 install nop-orm-eql 模块应该就可以重新生成。然后修正所有这些问题"），免子 agent 审查轮；根因与生成链路已实证。

## Purpose

修复 EQL 编译器算术表达式分组与标准 SQL 优先级不一致的框架缺陷：多个 `/` 夹 `+`/`-` 的表达式被静默重排（`A/B + C/D` → `(A/B+C)/D`），使 EQL 算术语义与 `SqlOperator` 打印优先级模型（MySQL 式标准）一致。

## Current Baseline

- 根因（生成代码实证）：`nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`（签入的生成物）中 `sqlExpr_bit` 的 ANTLR precedence 赋值为 `VERTICAL_BAR_`=11（最紧）… `PLUS_`=7 … `ASTERISK_`=5、`SLASH_`=4、`MOD_`=3、`CARET_`=2（最松）——源于 `model/antlr/BaseRule.g4:164-177` 的备选顺序 `| & << >> + - * / % ^` 在 ANTLR"前=紧"语义下正好与标准优先级（`^` > `* / %` > `+ -` > `<< >>` > `&` > `|`，即 `SqlOperator` 打印表 50/60/70/80/90/100）相反。
- 解析分组与 `AstToEqlGenerator` 打印括号模型脱节 → 执行文本（打印产物）静默改变语义。
- 实测缺陷矩阵（探针，已记入 analysis 第二轮复核）：`8/2+4/2`→`(8/2+4)/2`=4（标准 6）；`1*2/3+4*5/6`→`((1*2)/(3+4*5))/6`；`X/2+Y/2`→`(X/2+Y)/2`；短链/单除（`1+2*3`、`8/2+2`）当前恰好标准。
- 生成链路：`precompile/gen-eql-parser.xgen` 从 `model/antlr/Eql.g4`（import BaseRule.g4）+ `AntlrParserConfig.json` 生成解析器签入 `src/main/java/io/nop/orm/eql/parse/antlr/`；`./mvnw install -pl nop-orm-eql` 触发再生成。
- plan 2255 的 `buildSummarySql` 已用"每个含 `/` 子表达式整体括号"防御，本修复后该防御无害（分组本来就对）。

## Goals

- `sqlExpr_bit` 备选顺序改为标准优先级序（前=紧：`^`、`*`、`/`、`%`、`+`、`-`、`<<`、`>>`、`&`、`|`），重新生成解析器后 precpred 数字随之反转。
- 回归测试固化标准语义矩阵（编译分组 + H2 执行值双验证），修复前红、修复后绿。
- 依赖 EQL 的模块全量回归无破坏。

## Non-Goals

- 不修 XLang/其他 g4 的表达式优先级（仅 EQL，用户指令范围）。
- 不改 `SqlOperator` 打印优先级表与打印器（其模型本就标准）。
- 不做 MFA 家族 EQL 平移（独立 Open Question，另行裁定）。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（sqlExpr_bit 备选顺序）
- `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/*`（再生成产物，含 `.tokens`/`.interp`）
- `nop-persistence/nop-orm/src/test/java/io/nop/orm/eql/TestEqlArithmeticPrecedence.java`（新增回归测试）
- 文档：owner doc"EQL 书写注意"段、analysis Open Question、日志

### Out Of Scope

- 其他 g4 / XLang 语法、打印器、SqlOperator 表。

## Execution Plan

### Phase 1 - 红：回归测试先行

Status: completed
Targets: `nop-persistence/nop-orm/src/test/.../TestEqlArithmeticPrecedence.java`

- Item Types: `Proof`

- [x] 新增 `TestEqlArithmeticPrecedence`（继承 AbstractOrmTestCase，TestEqlCompiler 范式）：字面量与列引用（SimsClass.studentNumber）混合矩阵断言标准值（`8/2+4/2`=6、`1*2/3+4*5/6`、`10-2+3`、`X*2/10+X*3/10` 等）+ 编译 SQL 文本分组断言（无跨语义括号）
- [x] 修复前跑红，失败形态（非标准分组的编译文本/值）记录进 plan

红验证失败形态（HEAD，2026-08-23）：
- `8/2+4/2` → expected 6 but was **4**；编译文本 `select ( 8 / 2 + 4 ) / 2 ...`
- `10-2+3` → expected 11 but was **5**（`10-(2+3)`——`+` 错误紧于 `-` 的独立证据）
- `X*2/10+X*3/10`（列）→ expected 50 but was **0**
- `1 << 2 + 1` → H2 语法错（H2 无 `<<` 运算符），改为编译文本断言 `<< ( 2 + 1 )`

Exit Criteria:

- [x] 测试在 HEAD（未修复）状态下红，失败形态与 analysis 矩阵一致（5 用例 4 失败 + 1 H2 适配）
- [x] `ai-dev/logs/2026/08-23.md` 已更新

### Phase 2 - 修：g4 备选序 + 再生成 + 绿

Status: completed
Targets: `BaseRule.g4`、生成物、测试

- Item Types: `Fix`

- [x] `sqlExpr_bit` 重写为六级分层（同级合并为 token-set label 备选，ANTLR 共享 precedence 实现同级左结合）：`^` → `(* / %)` → `(+ -)` → `(<< >>)` → `&` → `|`（前=紧，对齐 SqlOperator 打印表 50/60/70/80/90/100 与 MySQL）。**执行中发现**：仅反转备选顺序不够——`+`/`-`、`*`/`/`/`%` 相邻不同备选会产生不同 precedence（`10-2+3` 仍错为 `10-(2+3)`、`8/2*2` 会错为 `8/(2*2)`），必须同级合并；token-set label（`operator=(PLUS_ | MINUS_)`）生成的 `Token operator` 字段与 `_EqlASTBuildVisitor` 兼容
- [x] `./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests` 再生成；diff 验证 `EqlParser.java` precpred 反转（`CARET_`=7 最紧、`(* / %)`=6、`(+ -)`=5、`(<< >>)`=4、`AMPERSAND_`=3、`VERTICAL_BAR_`=2 最松——同级备选共享数字；closure audit 核正：token-set 合并后备选数变 6，数字区间随之为 7..2，初版误引旧区间 11..6）+ `Eql.interp` 同步；`.tokens`/Lexer 未变（token 集未动）
- [x] Phase 1 测试跑绿（**6/6**，含新增同级用例 `8/2*2`=8、`2*3/2`=3、`100/5%30`=20）
- [x] 回归全绿：`nop-orm-eql` 45 ✓ → `nop-orm` 175 ✓（含新测试）→ `nop-ai/nop-ai-service` 38 ✓ → `nop-datav/nop-datav-service` 630 ✓ → `nop-sys/nop-sys-dao` 43 ✓ → `nop-wf/nop-wf-core,nop-wf-service` BUILD SUCCESS ✓（含 TestDaoWorkflowEngine 26 DB 引擎测试；nop-wf-dao 无测试目录）

Exit Criteria:

- [x] g4 与生成物同步提交；precpred 数字反转有 diff 证据（见上）
- [x] 测试矩阵全绿（编译分组与 H2 值均标准语义）
- [x] 五+模块回归全绿；无既有测试依赖旧分组（全绿即证）
- [x] **端到端验证**：`TestNopAiChatResponseSummarizeByModel` 保持绿（ai-service 38 全绿内含；plan 2255 的防御括号在新优先级下无害——分组本就标准）
- [x] owner doc / analysis / 日志已更新
- [x] `ai-dev/logs/2026/08-23.md` 已更新

## Closure Gates

- [x] 缺陷修复有红→绿证据（修复前失败形态 vs 修复后标准值，见 Phase 1/2 执行记录）
- [x] EQL 依赖模块回归全绿（eql 45 / orm 175 / ai-service 38 / datav-service 630 / sys-dao 43 / wf-core+wf-service BUILD SUCCESS；audit 复跑 eql 与新测试均 exit 0）
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql` 与 `-pl nop-persistence/nop-orm` 通过
- [x] checkstyle/imports 通过（checkstyle 插件未绑定生命周期；按 import 约定核验合规——audit 附注）
- [x] owner doc 已同步（"已修复 + 升级注意"）
- [x] 独立子 agent closure-audit 完成、evidence 写入（agent_118c3818，见 Closure）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- MFA 家族 EQL 平移评估（analysis Open Question，独立立项）。
- 其他 g4（如 XLang 表达式语法）优先级审计（本次范围外）。
- 下游应用升级提示：owner doc"升级注意"已声明依赖旧错误分组的无括号表达式语义会变化（仓库内经 audit 启发式扫描未发现此类用法）。

## Closure

Status Note: EQL 算术优先级缺陷已从根因（g4 备选序）修复并再生成签入解析器，标准六级左结合语义经红→绿与六模块回归固化；防御性括号写法降级为推荐而非必需。框架核心变更，独立审计从严复核通过。
Completed: 2026-08-23

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（agent_118c3818，fresh session，2026-08-23）
- Audit Session: agent_118c3818-2fa4-4184-bd9c-467926201e03
- Evidence:
  - 修复本体 PASS：`BaseRule.g4:164-174` 六级分层 + token-set label；`EqlParser.java` `sqlExpr_bit` precpred `CARET_`=7 → `(* / %)`=6 → `(+ -)`=5 → 移位=4 → `&`=3 → `|`=2（同级共享；bitmask 50176=SLASH_+ASTERISK_+MOD_ 交叉验证）；g4/生成物/interp 三文件同步未提交；visitor `ctx.operator`（Token）与 token-set 兼容（`_EqlASTBuildVisitor.java:60-68`，经 `EqlParseHelper.operator(Token)` 按 type 分发，与备选序解耦）
  - 实跑 PASS：`-Dtest=TestEqlArithmeticPrecedence` 6/6 exit 0；`-pl nop-orm-eql` 45/45 exit 0
  - Anti-Hollow PASS：`value()` 走 `orm().findLong` → Hikari H2 真实执行；`@BeforeEach` 真实插数
  - 回归 PASS：surefire 六模块 0 失败（orm 175 / eql 45 / ai 38 含 summarize 7 / datav 630 / sys-dao 43 / wf-service 113 含 DB 引擎 26）
  - 风险 PASS：全仓库启发式扫描无依赖旧分组的无括号混合算术 EQL；`.tokens`/Lexer 未变合理（token 集未动）；`sqlExpr_bit` 仅此一份 g4
  - 审计发现的 2 项文本修正（precpred 数字区间误引 11..6→已核正 7..2；Closure 补齐）已落实；`check-doc-links.mjs --strict` 收尾复跑 exit 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2256-eql-arithmetic-precedence-fix.md --strict` 退出码 0

Follow-up:

- 见 Non-Blocking Follow-ups；无 confirmed live defect 遗留
