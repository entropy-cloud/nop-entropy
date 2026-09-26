# 08 WI8 P0 内容首批——fix 模板补货 + 演示 transform 规则集 + GraphQL 闭环 e2e

> Plan Status: completed
> Last Reviewed: 2026-09-26
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M1 WI8 原文 + Cross-Cutting）；`ai-dev/design/nop-refactor/00-vision.md` §三（原则 1/3/6/9）；`ai-dev/design/nop-lint/01-pattern-dsl.md` §2（fix/transform 载体语法）、`02-rule-library.md`（规则库组织）
> Related: `ai-dev/plans/nop-refactor/04-wi3-transform-dsl.md`（transform 载体，completed）；05（RefactorResult 载荷，completed）；06（Refactor__ 契约面与 e2e harness，completed）；07（CLI 三态与 e2e，completed）
> Review: R1(2026-09-26, fresh session agent_0c7cf66c): REVISE — 1 Blocker + 2 Major + 4 Minor, all fixed：Blocker-1 三条语句级规则模板补尾分号（探针实证 matched range 含 `;`）；Major-1 rule-catalog 再生成 + --check 门禁纳入；Major-2 负例断言锚点更正为引擎编译期（非 loadRuleModel）；Minor-1 version 例外写入 design 02 增注清单；Minor-2 演示 (c) pattern 文本与 fixture 域边界钉死；Minor-3 演示前缀改独立兄弟前缀 /test/lint/refactor-p0/；Minor-4 Phase 1 Item Types 补 Decision。探针实证其余骨架（类/签名/harness/deps/裁定）全部成立。

## Purpose

执行 roadmap WI8（Item Type: Fix）：62 条生产规则中高价值机械可修规则的 fix 模板补货 + 演示 transform 规则集，并承担**终态闭环的 closure 责任**——验收必须含"演示规则集经 GraphQL 入口（Refactor__previewRewrite → Refactor__applyRewrite）端到端走通 AI 闭环"，CLI 入口为同型补充证明。选取准则随本 plan 发布，每条入选规则带 before/after fixture 与 verify 断言。

## Current Baseline

（live 已核对，2026-09-25；行号为核对时快照，执行时以 live 为准）

- **deps 全满足**：WI1/WI3/WI5/WI6/WI7 均已勾选 completed（live 核对）。
- **62 条生产规则**：`nop-lint/nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/` 下 6 类目（antipattern 9 / api 6 / exception 10 / nop 6 / quality 25 / security 6）。清单钉死：`TestProductionRuleCount`（62 条 census）+ `TestNopRuleSuites`（suite 发现 + version="1.0" 全库断言）。**全部规则无 `fix` 声明、`autoFixable: false`**。
- **fix 模板机制**：`lint-rule.xdef` 声明 `fix(description!, template!, suggest=false)`；`TemplateFix.compile` 编译期核对捕获引用——未声明的 `$TOKEN` 拒绝；`$$$VAR` 渲染首末捕获节点间原始源码切片。组合面：`fix`+`xscript` parse 期拒绝、`fix`+`transform` 互斥、XML 路径 `fix` compile 期拒绝。
- **fix 应用面**：`CheckRunner --fix` → `FixApplier`（multipass：merge → splice → EditPlanApplier 原子写）；`FixApplier.run(Path, byte[], dryRun=true)` 返回拼接后终态且不落盘——before/after 断言的现成机械核。
- **RuleTester**：`RuleTestRunner` suite 布局断言诊断面，`.expect` 无 fix 字段。fix 联动的引擎级证明先例 = `TestAutofixDemoRule`：loadRuleModel → LintEngine.lint(STANDARD) → 断言 `diagnostic.fix().replacement()` + `metadata.isAutoFixable()`。
- **Refactor 载入门（关键裁定依据）**：`RefactorRuleGates.verifyRewriteRuleset`——(a) `getTransform()==null` 的规则（含 fix 规则、纯 report 规则）fail-closed 拒绝；(b) transform 规则 requires 非空拒绝。**结论：fix 模板规则不能经 Refactor__previewRewrite / refactor CLI 消费**；fix 补货规则的消费路径 = nop-lint 既有 autofix 流；GraphQL 闭环演示规则集 = 纯 transform 规则集。
- **WI6 GraphQL e2e harness**：`TestNopRefactorGraphQL`——CoreInitialization + BeanContainer.getBeanByType（beans.xml 装配证明）+ BizModelSchemaLoader + newRpcContext(mutation, ...) + executeRpcAsync。fixture 前缀 `/test/lint/graphql-rewrite/`。
- **WI7 CLI e2e harness**：`TestRefactorCliEndToEnd`——Run(exitCode, stdout, stderr) record + runFull 进程内入口。
- gap：62 条规则零 fix 模板（autoFixable 全 false）；演示 transform 规则集与"同一演示规则集 × 双入口"的终态闭环证明缺失。

## 选取准则与入选清单（roadmap 硬性要求：随 plan 发布）

### 选取准则

**性价比 = AI 使用频率 × pattern 可表达性 ÷ 模板复杂度**

- **AI 使用频率**：3 = 高频习语；2 = 常见；1 = 低频
- **pattern 可表达性**：3 = 现有捕获即可承载模板；2 = 多捕获重排；1 = 需语义知识
- **模板复杂度**：1 = 单捕获或纯字面；2 = 多捕获组合；3+ = 需改匹配器形态

### 硬性排除面

- xscript 载体规则——fix+xscript parse 期互斥（8 条）
- 修复需 logger 上下文——no-system-out、print-stack-trace
- 修复需类型清单等语义知识——no-star-import、no-return-null
- 修复需改匹配器形态——以最小 diff 为原则，不动既有匹配器

### 入选清单（6 条，写死）

| # | 规则 id | fix 模板方案 | before → after | 分数 |
|---|---------|-------------|----------------|------|
| 1 | `quality/use-collection-isempty` | `$COL.isEmpty()` | `list.size() == 0` → `list.isEmpty()` | 9 |
| 2 | `exception/no-throw-npe` | `throw new IllegalStateException($$$ARGS);` | `throw new NullPointerException("msg");` → `throw new IllegalStateException("msg");` | 9 |
| 3 | `quality/replace-hashtable` | `Map $X = new HashMap($$$ARGS);` | raw `Hashtable m = new Hashtable();` → `Map m = new HashMap();` | 6 |
| 4 | `quality/replace-vector` | `List $X = new ArrayList($$$ARGS);` | `Vector v = new Vector();` → `List v = new ArrayList();` | 6 |
| 5 | `quality/random-mod` | `$R.nextInt($N)` | `rnd.nextInt() % 10` → `rnd.nextInt(10)` | 6 |
| 6 | `exception/equals-null` | `$X == null` | `if (s.equals(null))` → `if (s == null)` | 6 |

> **模板尾分号裁定（R1 Blocker-1 实证）**：#2/#3/#4 三条规则的 pattern 匹配有效节点是含 `;` 的语句节点（throw_statement / local_variable_declaration——探针实证 matched range 含分号），模板必须携带尾分号，否则替换会吃掉源码分号、autofix 路径被 syntax-break 守卫每次回滚。#1/#5/#6 匹配表达式节点（不含 `;`），模板不带分号。

### 落选与候补记录

- **次批候补**：`exception/throw-null`（频率低）、`quality/biginteger-instantiation`（频率低）
- **结构性落选**：`antipattern/new-primitive-boxing`（7 分支类型名字面非捕获，单模板不可分支映射）；`quality/simplify-boolean-expression`（== true / == false 需两模板）

## Goals

- **fix 模板补货（Phase 1）**：6 条入选规则各增 `fix` 块 + 翻转 `autoFixable: true`；每条带 before/after fixture 断言；既有诊断面零变化
- **演示 transform 规则集（Phase 2）**：3 条 transform 规则（日志门面迁移 / deprecated 构造器替换 / 冗余 String 消除）；每条带 before/after fixture
- **GraphQL 闭环 e2e（Phase 2，closure 责任本体）**：previewRewrite → applyRewrite 两段真调，全载荷字段断言
- **CLI 同型补充证明（Phase 2）**：`NopRefactorCli.runFull` 对同一演示规则集跑通
- **选取准则发布**：本 plan"选取准则与入选清单"节即 roadmap 所指的准则发布件

## Non-Goals

- 不把 fix 模板规则接进 Refactor rewrite 面（RefactorRuleGates 拒绝非 transform 规则是 fail-closed 裁定；放宽 = 引擎面扩展须过 design 裁定——Follow-up）
- 不把演示 transform 规则落进生产规则库（62 条 census 钉死；rewrite-only 进 check 面属行为扩张——fixture 域定位）
- 不新增引擎面：不改 TemplateFix/Fixer/FixApplier/EditPlanApplier/LintEngine/RuleDslParser/RuleTestRunner 任何行为；预期 main Java 零新增
- 不做次批补货候选与结构性落选面的 DSL 支持扩展
- 不修改 nop-treesitter / nop-code

## Scope

### In Scope

- nop-lint-nop 6 条入选规则的 YAML 变更（增 fix 块 + autoFixable 翻转 + 注释行）
- nop-lint/docs/rule-catalog.md 再生成（autoFixable 列随 6 条翻转；gen-lint-rule-catalog.mjs 产出 + --check 门禁）
- nop-lint-nop 新增 TestProductionRuleFixes（表驱动引擎级 before→after 断言）
- nop-refactor-graphql test resources 新增演示规则集 fixture（独立前缀 `/test/lint/refactor-p0/`：3 条 transform + 1 ruleset 含豁免 + before/after Java fixture；不嵌套于既有 graphql-rewrite 前缀）
- nop-refactor-graphql 新增闭环 e2e 测试类（GraphQL RPC 两段真调 + 全载荷断言 + CLI 同型证明）
- design/nop-lint/02-rule-library.md 增注 + design/nop-refactor/01-architecture-baseline.md 增注
- ai-dev/logs/ 对应日期条目

### Out Of Scope

- nop-lint / nop-treesitter / nop-code 的任何 main Java 行为面
- RefactorRuleGates / NopRefactorBizModel / NopRefactorCli / RefactorVerifier / EditPlanApplier 的代码变更
- rename 面（WI9–WI12）、操作框架（WI9）、预算审计（WI13）
- docs-for-ai 模块文档新建（WI13）

## Execution Plan

前置依赖（roadmap deps: WI1, WI3, WI5, WI6, WI7）：五项均已 completed。Phase 1 开工前 git status 自查 nop-lint/nop-refactor 干净。

### Phase 1 - fix 模板补货（Fix）

Status: completed
Targets: `nop-lint/nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/`、`nop-lint/nop-lint-nop/src/test/java/`

- Item Types: `Fix + Decision`

- [x] 6 条入选规则 YAML 逐条变更：增 `fix: { description, template }` 块；`metadata.autoFixable: false` → `true`；匹配器/message/severity/约束/version 字段零变化；#2/#3/#4 模板带尾分号（见入选清单尾分号裁定）
- [x] 版本字段裁定：version 保持 "1.0" 不递增（TestNopRuleSuites 全库钉死；版本定档归 WI13）——该例外显式写入 design 02 增注
- [x] TemplateFix 编译期核对在规则编译进引擎时生效（R1 Major-2 锚点更正：拒绝点 = `CompiledRule.compile` → `TemplateFix.compile`，`LintEngine.lint` 首次编译即抛 `NopLintException`；`loadRuleModel` 只解析不编译、不触发拒绝）
- [x] 新增 TestProductionRuleFixes（表驱动：规则 id → before 源 / after 源 / 期望 replacement）：每条断言 (a) getFix() 非空 + isSuggest()==false + isAutoFixable()==true；(b) LintEngine.lint(STANDARD) 产 1 诊断且 diagnostic.fix().replacement() 逐字符相等；(c) FixApplier.run(dryRun=true) finalSource 与 after 源逐字节相等——after 源必须为语法完整 Java（分号保留），不得为迁就错误模板写破损 fixture
- [x] 负例断言：模板引用未声明捕获的规则，断言锚在 LintEngine.lint 首次编译抛 NopLintException（TestAutofixDemoRule 同款装载路径）
- [x] 再生成 `nop-lint/docs/rule-catalog.md`（`node ai-dev/tools/gen-lint-rule-catalog.mjs`）并跑 `--check` 退出 0——autoFixable 翻转必致目录漂移，fail-fast 门禁不可降级（R1 Major-1）
- [x] 既有诊断面零回归：TestNopRuleSuites / TestProductionRuleCount / 全部 RuleTester suites 零修改保持绿
- [x] design/nop-lint/02-rule-library.md 增注：入选 6 条清单 + autoFixable 语义 + version 不递增例外裁定 + 次批/落选清单
- [x] ai-dev/logs/ 对应日期条目已更新

Exit Criteria:

- [x] ./mvnw test -pl nop-lint/nop-lint-nop -am 全绿
- [x] TestProductionRuleFixes 覆盖全部 6 条，(a)(b)(c) 三类断言逐条可定位
- [x] **接线验证**：fix 模板确实经 TemplateFix 渲染路径产出——(b) 的 replacement 即渲染产物、(c) 的终态即 FixApplier 产物
- [x] **诊断面零变化**：全部既有 suites 与 census 测试零改动通过
- [x] **无静默跳过**：模板引用未声明捕获时引擎编译期 fail-closed（LintEngine.lint 首次编译抛 NopLintException，负例断言锚定；非 loadRuleModel 锚点——R1 Major-2 更正）
- [x] **新功能测试清单**：TestProductionRuleFixes 全表 + 负例加载断言
- [x] rule-catalog 再生成后 `node ai-dev/tools/gen-lint-rule-catalog.mjs --check` 退出 0
- [x] design 02 增注已落档且与 landed YAML 逐条互洽（含 version 例外裁定）
- [x] ai-dev/logs/ 对应日期条目已更新

### Phase 2 - 演示 transform 规则集 + GraphQL 闭环 e2e + CLI 同型证明（Fix + Proof）

Status: completed
Targets: `nop-refactor/nop-refactor-graphql/src/test/resources/_vfs/test/lint/refactor-p0/`、`nop-refactor/nop-refactor-graphql/src/test/java/`

- Item Types: `Fix + Proof`

- [x] 演示规则集 fixture（**独立兄弟前缀 `/test/lint/refactor-p0/`**——R1 Minor-3 裁定：不嵌套在 `/test/lint/graphql-rewrite/` 之下，因 `RuleSetLoader.collectPaths` 递归扫描会令既有 e2e 的有效规则集静默扩容）：3 条 transform `.rule.yml`——(a) pattern `System.out.println($$$ARGS)` → template `log.info($$$ARGS)`；(b) pattern `new Integer($$$ARGS)` → template `Integer.valueOf($$$ARGS)`；(c) pattern `new String($LIT)` → template `$LIT`（**R1 Minor-2 钉死**：`$!` 保留标记未实现，pattern 无法结构性钉死字面量参数；fixture 域内参数恒为字符串字面量，plan 边界 = 仅 fixture 域演示，不进生产库）；均无 severity/metadata——transform 与 severity 脱钩。+ 1 个 `p0-demo.ruleset.yml`（含豁免声明：(a) 对 `**/legacy/**` 豁免）+ 每条一对 before/after Java fixture
- [x] 演示规则合规自检：3 条均通过 RefactorRuleGates.verifyRewriteRuleset
- [x] GraphQL 闭环 e2e（复用 TestNopRefactorGraphQL harness 形态）：(a) previewRewrite → applied=false + diff 携带全部 3 类改写 + 文件不变；(b) applyRewrite → applied=true + 文件逐字节 after；(c) 全载荷字段断言（edits path/range/summary、verification 四子段、stats 六面、nonApplied 豁免一条）；(d) 无状态重执行确定性
- [x] CLI 同型补充证明（同一演示前缀，NopRefactorCli.runFull）：preview exit 0 + diff 含三类改写 + 不落盘；apply exit 0 + 文件终态 after；含豁免 exit 1 + nonApplied(OUT_OF_SCOPE)；--json 同载荷
- [x] design/nop-refactor/01-architecture-baseline.md 增注：演示规则集 fixture 域定位（不进生产库）+ WI3 m4 再议项保持 deferred
- [x] 行为红线自查（scoped）：nop-lint main Java / nop-treesitter / nop-code / nop-refactor main Java 零修改
- [x] ai-dev/logs/ 对应日期条目已更新

Exit Criteria:

- [x] **端到端验证**（roadmap WI8 终态闭环验收本体）：演示规则集经 Refactor__previewRewrite → Refactor__applyRewrite 端到端走通 AI 闭环——(a)–(d) 四断言全绿
- [x] **CLI 同型证明绿**：同一演示规则集经 NopRefactorCli.runFull 跑通
- [x] **接线验证**：载荷确实产自 WI5 assemble 与 WI4 单一路径——全载荷字段断言中值与事实一一对应
- [x] **无静默跳过**：豁免文件不改写且结构化入 nonApplied；preview 不落盘、apply 原子落盘有逐字节断言
- [x] **新功能测试清单**：(a)–(d) + CLI 四断言 + 合规自检，逐项列出并落为测试
- [x] 演示规则集 3 条的 before/after fixture 断言全部落地
- [x] design 01 增注已落档且与 landed fixture/测试互洽
- [x] 行为红线自查记录在案（scoped git diff）
- [x] ai-dev/logs/ 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部 in-scope 项完成，无残留未勾选 checklist
- [x] **终态闭环验收（roadmap WI8 硬性）**：演示规则集经 GraphQL 入口端到端走通 AI 闭环（全载荷字段断言绿）；CLI 入口同型补充证明绿
- [x] 选取准则与入选清单 = 本 plan 节逐条兑现（6 条 fix + before/after + 落选记录）；无静默换清单
- [x] 6 条 fix 规则的 autofix 路径可用性成立（TestProductionRuleFixes (c) 承载）
- [x] 零行为红线（scoped diff）：nop-lint / nop-treesitter / nop-code / nop-refactor 的 main Java 零修改；诊断面 golden 不变
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 端到端路径从 GraphQL RPC / CLI 入口到文件落盘与载荷返回运行时连通，(b) 无空方法体/静默跳过/no-op（本 plan 预期零 main Java 新增——重点核验测试断言非自算伪造）
- [x] ./mvnw test -pl nop-lint(全模块) -am 全绿
- [x] ./mvnw test -pl nop-refactor(两模块) -am 全绿
- [x] scan-hollow-implementations --module nop-refactor-graphql --severity high 退出 0
- [x] 代码规范：check-import-order.mjs 范围口径退出 0
- [x] vision 原则 1–9 回扣核对（closure audit 执行）：原则 1/3/6/9 重点
- [x] owner docs 已同步：design 02 增注（Phase 1）+ design 01 增注（Phase 2）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] rule-catalog 门禁：`node ai-dev/tools/gen-lint-rule-catalog.mjs --check` 退出 0（目录与 landed YAML 同步）
- [x] check-doc-links --strict 退出 0
- [x] check-plan-checklist --strict 退出 0

## Deferred But Adjudicated

### 次批 fix 补货候选（throw-null、biginteger-instantiation）

- Classification: `optimization candidate`
- Why Not Blocking Closure: WI8 交付口径为首批 5–8 条；两条候补已达模板门槛但频率分低
- Successor Required: no

### 结构性落选面的 DSL 支持

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 6 条入选已覆盖首批价值面；分支模板属 DSL 能力扩展
- Successor Required: no

## Non-Blocking Follow-ups

- fix 模板规则接入 Refactor rewrite 面（放宽 RefactorRuleGates 或建独立载荷通道）：引擎面扩展须过 design + 性价比门
- 演示 transform 规则进生产可见库：WI3 R2 m4 再议项，随 WI13 统一裁定
- 入选规则 suite fixture 增补 fix 专属 valid/ 反例

## Closure

Status Note: WI8 全部交付落地——6 条生产规则 fix 模板补货（语句级 3 条带尾分号，TestProductionRuleFixes (a)(b)(c)+负例 4/4 绿）+ 演示 transform 规则集（/test/lint/refactor-p0/ 独立前缀，3 规则 + 豁免 ruleset + fixture 对）+ roadmap 终态闭环验收本体（TestRefactorP0ClosedLoop 5/5：Refactor__previewRewrite → Refactor__applyRewrite 经真实 GraphQLEngine RPC + 全载荷字段断言 + 豁免 OUT_OF_SCOPE + 无状态确定性）+ CLI 同型证明（TestRefactorP0CliSameShape 3/3，退出码 0/1）。零 main Java 新增，行为红线成立。审计 Minor 记录：CLI "apply exit 0" 子场景由既有 WI7 TestRefactorCliEndToEnd 承载（本 plan 新增测试覆盖含豁免 exit 1 场景）；fixture 实际 2 对 4 文件（sample 对承载 3 规则改写、legacy 对承载豁免），断言面完整等效。
Completed: 2026-09-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_b9189444-1ce3-4a9d-81d5-ea5e5c41cf3b
- Audit Session: agent_b9189444-1ce3-4a9d-81d5-ea5e5c41cf3b（2026-09-26）
- Evidence:
  - Phase 1 Exit Criteria 9/9 PASS：mvn test nop-lint-nop 80/80（TestProductionRuleFixes 4/4、TestNopRuleSuites 59、census 1）；接线验证锚定生产 API（diagnostic.fix().replacement() / FixApplier.run dryRun）；6 条 YAML diff 仅注释/fix 块/autoFixable；负例引擎编译期 NopLintException；catalog --check EXIT 0（62 rows in sync）；design 02 §4 互洽
  - Phase 2 Exit Criteria 9/9 PASS：ClosedLoop 5/5 + CliSameShape 3/3（既有 BizModel 11/GraphQL 4/core 29 零修改全绿）；豁免结构化 nonApplied 断言；design 01 增注在案
  - Anti-Hollow：断言值全部产自真实引擎/RPC/CLI 产物（逐三元组抽查无永真断言——模板缺尾分号则 (b)(c) 必失败）；scoped main diff 空；scan-hollow High=0 EXIT 0
  - vision 原则回扣：1/3/6/9 逐条 PASS（原则 9：零 main Java 新增）
  - Deferred 分类诚实性：次批候补/结构性落选/3 项 follow-ups 均 non-blocking，无 in-scope defect 降级
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/08-wi8-p0-content-first-batch.md --strict` 退出 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors；check-import-order 全仓口径本 plan 新增 3 测试文件零违规

Follow-up:

- fix 模板规则接入 Refactor rewrite 面（放宽 RefactorRuleGates 或建独立载荷通道）：引擎面扩展须过 design + 性价比门
- 演示 transform 规则进生产可见库：WI3 R2 m4 再议项，随 WI13 统一裁定
- 入选规则 suite fixture 增补 fix 专属 valid/ 反例
