---
status: active
mission: nop-lint
work-item: "item-21"
group: "2026-09-22-1045"
verify: [test]
---

# XNode Pattern 引擎（roadmap item 21）：XML 规则的属性/文本/命名空间语义 + ORM/xbiz fixtures

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- 依赖满足：item 21 deps = M1（done）。当前匹配内核（`nop-lint-core` `pattern/` 包全部 matcher）只支持 tree-sitter `LintNode` 输入；**不存在任何 XML/XNode 匹配路径**。
- xdef `lint-rule.xdef`（`nop-lint-core/src/main/resources/_vfs/nop/lint/schema/lint-rule.xdef`）的 `language` 枚举已含 `XML`（design 10 §2 落地形态），但向现有引擎加载一条 `language: XML` 的规则会在 `LanguageRegistry.resolve` 处 fail-closed（unknown lint language 'XML'）——XML 无 tree-sitter grammar blob，design 01 §1 明确"XML 规则不使用 tree-sitter（无 XML grammar blob），改用 Nop 自有 XNode 解析器；Pattern DSL 对 XNode 树做结构匹配，meta-var 语义一致"。
- 设计契约先立（design 01 §3.5 XNode Pattern 匹配语义表）：节点对应（XNode 标签+属性+文本+子节点 ↔ PatternNode；**标签名匹配相当于 kind 匹配**）；属性匹配（字面量→精确文本相等；meta-var（`$$$`）→捕获；pattern 中声明的属性必须存在，缺省属性不匹配，除非用 `$_`）；文本匹配（元素文本按 trimmed 比较；`$$$` 可匹配任意文本含空）；命名空间（`x:` 前缀的 XDSL 内部属性如 `x:extends` **不参与**匹配——匹配的是合并后的最终模型）；严格度（复用 Smart：忽略注释节点；无 CST 层——XNode 无括号/逗号类 trivial 节点）。
- design 01 §3.5 附两条示例 XML 规则（`nop-orm-mandatory-default`、`nop-xbiz-auth-not-sole-guard`，含 relational `has`/`inside` 组合形态）；roadmap item 21 明确要求 "incl. ORM/xbiz fixtures"。
- 引擎管线现状（全部 tree-sitter 耦合）：`LintEngine.lint` → `CompiledRule.compile(rule, language)` → `RuleSetRunner.run`（`KindIndex.collect` kind 位图过滤 → matcher → xscript 可选）→ `SuppressionFilter.evaluate(tree, candidates)`（`LintTree` 输入）。XML 路径的注入点（并行源模型入口 vs 其他接线形态）是本 plan Phase 1 的 **Decision**；硬边界：不修改 nop-treesitter 既有类、不修改 nop-core/nop-xlang/nop-xdef（XNode 解析器只作只读 API 消费）、每条规则恰一可观测出口（executed / skippedByProfile / 过滤类计数）——XML 规则不得无声绕过任何既有计数。
- RuleTester（`testing/` 包：`RuleTestRunner`/`ExpectParser` + `suites/<group>/<rule-id>/{valid,invalid}` 套件发现）同样经 language 解析——XML 套件支持是硬约束（"Every Wave-2+ rule lands with RuleTester fixtures"）的强制项。
- CLI 现状：`TargetScanner` v1 `extension == language id` 约定下 `.xml` 无对应语言 id → skipped summary 显式计数；本 plan 的前置 plan（item 19，group 2026-09-22-1045-1）落地显式扩展名表后，本 plan 追加 `xml→xml` 条目（依赖该表形态已就位；若 item 19 未先行落地，本 plan Phase 2 承接表形态裁定并记录两 plan 交接）。
- 抑制管线现状：`SuppressionFilter` = 内联注释扫描（恒开）+ `LintLanguage.suppressionProvider()`（XML 无绑定 → provider 恒 null）；XNode 注释节点是否参与内联抑制 = 本 plan **Decision**（落地或显式登记 v1 gap，不得静默）。
- 可复用资产：`MetaVarSyntax`/`MetaVarEnv`/`Match`/`Diagnostic`/`LintStats`/`Strictness` 中与树形态解耦的部分；meta-var 同名一致性与 `$$$` 序列语义必须与 tree-sitter 路径一致（design 01 §3.5 "meta-var 语义一致"）；relational 匹配器（item 23 交付的 `inside`/`has`）在 XNode 树上的对应语义按 §3.5 示例规则的需要裁定（relation 在 XNode = 祖先/后代结构关系，遍历语义复用 `StopBy` 契约或裁定等价简化，结论回写）。

## Goals

- `nop-lint-core` 新增 `xml/` 包（design 01 §1 模块图预留位）：XNode 结构匹配器——标签名匹配（=kind 语义）、属性匹配（字面量精确相等 / meta-var 捕获 / 声明属性必须存在）、trimmed 文本匹配（`$$$` 含空通配）、`x:` 前缀内部属性排除、Smart 严格度（注释节点跳过、无 CST 层）。
- **Decision（接线方案）**：`language: XML` 规则从加载到诊断的管线注入点裁定（满足上述硬边界），使 XML 规则端到端可执行且诊断/统计计数语义与 v1 管线一致（如需新计数口径，显式定义进 `LintStats`）。
- RuleTester/套件发现支持 XML 语言套件；CLI 支持 `.xml` 目标文件（扩展名表追加条目）。
- **Decision（抑制语义）**：XML 规则诊断的内联注释抑制落地形态或显式 v1 gap 登记。
- ORM/xbiz fixtures：design 01 §3.5 两条示例规则的落地裁定（生产规则进 nop-lint-nop vs 测试套件 fixtures）+ 每条 ≥1 valid + ≥2 invalid + `.expect` 套件全绿。
- 语义结论回写 design 01 §3.5、design 03（接线章节）、design 09 §2（抑制裁定）、design 02 §1（规则清单）；roadmap item 21 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）。

## Non-Goals

- 9 条 XNode 生产规则批量落地（item 29 "quality/security/XNode rules (9 of the first-20 batch)"）；本 plan 只落 fixture 规则。
- `lint-ruleset.xdef`/exemptions/settings 注入（design 10 §3，item 27 范围）、autofix（item 25）。
- check-\*.mjs 中 XML 类脚本（如 check-bean-naming）的迁移或切换（item 28 manifest 决定）。
- 复杂 XML 语义：命名空间前缀重绑定、XPath、CDATA/PI 的专门匹配语义（不支持形态按 Minimum Rules #24 显式报错或显式裁定，不静默）；`x:` 排除语义仅按 design 01 §3.5 表格字面执行。
- tsc/TS 适配（item 19/20）、constraints（item 22）、dataflow（Wave 5）。

## Phase 1 — XNode 结构匹配器与接线裁定（Decision + Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-core`（新 `xml/` 包 main + test）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（接线方案）**：裁定 `language: XML` 规则从规则加载到诊断输出的管线注入点（并行源模型执行入口 / LintLanguage 适配层扩展 / 其他形态），必须满足：(a) 不修改 nop-treesitter 与 nop-core/nop-xlang/nop-xdef；(b) 诊断/统计计数语义不静默旁路（每条规则恰一可观测出口；过滤类行为有显式计数）；(c) 对 `Diagnostic`/`LintStats`/`SuppressionFilter` 等管线尾部资产的复用程度显式化；(d) xdef 是否需要变更随裁定确定（language 枚举已含 XML，预期零 xdef 变更）。结论回写 design 03 §1.1（或最贴近章节）+ design 01 §3.5 增注。—— 裁定：binding 钩子 `LintLanguage.compileRule`（default null = 既有 tree-sitter 矩阵；XmlLanguage 覆写经 `CompiledRule.precompiled` 装配），下游管线零分支零计数旁路；XNode 解析器只读消费，xdef 零变更。增注见 design 03 §1.1 + design 01 §3.5。
- [x] **Decision（XNode pattern 编译形态）**：XML pattern 文本（design 01 §3.5 示例为 XML snippet）→ 结构化 PatternNode 的编译路径；meta-var 语法位（属性值/文本中的 `$VAR`/`$$$VAR`）与转义边界；meta-var 同名一致性、`$$$` 序列语义与 tree-sitter 路径对齐的验证方式。结论回写 design 01 §3.5。—— 裁定：`XNodeParser` 解析 → `XNodePattern`（tag/attrs/text/children）；meta-var 经共享 `MetaVarSyntax` 分类 + 共享 `MetaVarEnv`（同名一致性/`$$$` 语义由共享实现保证）；标量位不支持形态 fail-closed。增注见 design 01 §3.5。
- [x] **Decision（relation 算子在 XNode 的语义）**：`inside`/`has`（及 all/not 组合）在 XNode 树上的对应语义（祖先/后代结构遍历；`StopBy` 契约复用或裁定等价简化）；§3.5 两条示例规则所需的组合面必须完整可用。结论回写 design 01 §3.5（与 design 04 §5 的对应关系注明）。—— 裁定：`RelationalMatcher`/`StopBy`/`AllMatcher`/`NotMatcher` 经 LintNode 门面原样复用（neighbor/end），`stopBy=rule`、`field`、`context+selector` 在 XML 路径编译期 fail-closed。见 design 01 §3.5 + design 04 §5 增注。
- [x] XNode 匹配器落地：标签名匹配、属性匹配（字面量精确相等 / meta-var 捕获 / pattern 声明属性必须存在——缺省属性不匹配）、文本 trimmed 匹配（`$$$` 匹配任意文本含空）、`x:` 前缀内部属性排除、Smart 严格度（注释节点跳过）。—— `xml/` 包：XmlTagKinds / XNodeLintNode / XmlSourceParser / XNodePattern / XNodePatternCompiler / XNodePatternMatcher / XmlValueNode。
- [x] 单元测试矩阵（Minimum Rules #25）：标签/属性/文本/子序列 × 命中/不命中 × meta-var 捕获一致性 + 缺省属性不匹配 + `x:` 属性排除 + 注释节点跳过，逐格断言。—— `XNodePatternMatcherTest` 47 用例 + `XmlSourceParserTest` 11 用例。

Exit Criteria:

- [x] 矩阵全格有断言（Minimum Rules #25）。
- [x] meta-var 语义一致性有显式断言：同名变量一致性与 `$$$` 序列语义在 XML 路径与 design 01 §3.5 "meta-var 语义一致"契约对照成立。—— 共享 `MetaVarSyntax`/`MetaVarEnv` + same-name 一致性（attr-attr 与 attr-text 跨位）与 `$$$` 通配断言（`XNodePatternMatcherTest`）。
- [x] 无静默跳过（Minimum Rules #24）：不支持的 XNode 形态（PI/CDATA 等，若不支持）显式抛错或显式裁定记录，不静默忽略。—— body 内 PI 解析期 fail-closed（`processingInstructionInsideBodyFailsClosedAtParse`）；CDATA 按文本参与（显式裁定）；混合内容 pattern/标量位 `$$$VAR`/`$$`/裸 `$` 编译期拒绝（各有用例）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。（506 tests / 0 failures）
- [x] owner-doc：design 01 §3.5、design 03 增注与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 引擎/RuleTester/CLI 集成（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-core`（`engine/`、`testing/`、`cli/`）

- Item Types: `Fix | Decision | Proof`

- [x] `language: XML` 规则的执行路径接通（按 Phase 1 裁定方案）：`.rule.yml` 加载（xdef 按裁定，预期零变更）→ XNode 源解析 → 匹配 → `Diagnostic` → 统计计数。—— `XmlLanguage`（ServiceLoader 注册于 nop-lint-core main resources）+ `XmlRuleCompiler` 经 `LintLanguage.compileRule`/`CompiledRule.precompiled` 接入同一引擎管线。
- [x] RuleTester/套件发现支持 XML 语言套件（`suites/<group>/<rule-id>/{valid,invalid}` + `.expect` 同构复用）。—— core 测试套件 `suites/xml/demo-xml-driver`（valid 1 + invalid 1 + `.expect`）经 `TestRuleSuites`/`TestRuleTestRunner` 真实管线全绿；item 19 的 `no-extension-language` 占位 fixture 按其自述前提翻转更新为 `no-fixtures-for-language`（XML 现有扩展名表项，无夹具仍 fail-closed）。
- [x] CLI 扩展名表追加 `xml→xml`（承接 item 19 的显式表形态；item 19 已先行合入，直接承接，无两 plan 交接负担），`.xml` 目标文件端到端可 lint；console 输出格式与退出码语义不变。—— `TestXmlCliWiring`：TargetScanner → CheckRunner → LintEngine 全链 + skipped 分桶断言。
- [x] **Decision（XML 抑制语义）**：XNode 注释内联抑制的落地形态，或显式登记 v1 gap（`SuppressionFilter` 对 XML 路径的行为 + 计数/文档化说明）；结论回写 design 09 §2 增注。—— 裁定：**落地**（非 gap）——XNode 注释经 facade `#comment` extra trivia child 进入既有 `CommentSuppressionScanner`（零 XML 特判），`-->` 终止符与 `*/` 同位剥离；注解 provider 恒 null。见 design 09 §2 增注。
- [x] 单元测试（Minimum Rules #25）：XML 规则 e2e（加载→诊断）、XML 套件发现与断言、CLI `.xml` 扫描链路、抑制裁定路径（落地或 gap 登记对应行为）断言。—— `TestXmlRuleEngineEndToEnd` 10 用例 + `TestXmlCliWiring` 2 用例 + 套件发现 2 处断言更新。

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：`language: XML` 的 `.rule.yml` → XNode 引擎匹配 → 诊断断言全链绿。（`TestXmlRuleEngineEndToEnd`：xdef 加载 → compileRule → 匹配 → 诊断行号断言；`TestRuleSuites` XML 套件全绿）
- [x] **接线验证**（Minimum Rules #23）：XNode 匹配器被引擎运行时真实调用——invalid fixture 命中断言 + 计数断言，非仅类型存在。（kind 过滤计数互斥断言 `rulesExecuted=1/kindFiltered=0` vs `rulesExecuted=0/kindFiltered=1` + CLI 全链 `rulesExecuted=2`）
- [x] 无静默跳过（Minimum Rules #24）：XML 路径的失败模式（不可解析源、不支持形态、规则加载失败）显式报错，不静默。（`unparseableXmlSourceFailsClosed`/`unsupportedFormsFailClosedAtCompile` regex+unknown-kind+bad-pattern/`incrementalParsingIsRejectedFailClosed`）
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。（520 tests / 0 failures）
- [x] owner-doc：design 09 §2 增注（抑制裁定）与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — ORM/xbiz fixtures 与收口（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-nop`（规则或套件资源，按裁定）、`ai-dev/design/nop-lint/02-rule-library.md`、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（fixture 规则归属）**：design 01 §3.5 两条示例（`nop-orm-mandatory-default`、`nop-xbiz-auth-not-sole-guard`）落地为生产规则（nop-lint-nop 主资源 `_vfs/nop/lint/rules/`）或测试套件 fixtures，裁定结论回写 design 02 §1 规则清单（标注 item 21 落地状态，与 item 29 的 9 条 XNode 规则批量明确区分）。—— 裁定：**生产规则**（`rules/nop/nop-orm-mandatory-default.rule.yml` + `rules/nop/nop-xbiz-auth-not-sole-guard.rule.yml`）：两条是真实平台约定检查，生产化使 XML 链路受真实规则库约束（与 item 11/23 同口径）；与 item 29 批量的区分注记见 design 02 §1 增注。
- [x] fixtures 落地：每条规则 ≥1 valid + ≥2 invalid + `.expect`（真实 ORM/xbiz 语义样本：mandatory/defaultValue 列形态、xbiz action auth 声明形态），套件全绿（硬约束：Wave-2+ 规则必带 RuleTester fixtures）。—— `TestNopRuleSuites` 14/14（12 套件全绿，含两个 XML 套件经 x:extends 真实管线）。
- [x] dogfood（有界）：对仓库内有界 `.orm.xml`/xbiz 切片实跑一次 CLI，结果记入日志；发现项逐条裁定（by-design 豁免 / 真问题 / 规则修正），不静默。—— `nop-auth/model/nop-auth.orm.xml`（200 warning，orm 规则真阳性，整改/baseline 归 item 27 与平台侧）；`_dump` 生成 xbiz 两份（2 warning，auth 声明命中，_dump 为生成物 = by-design 豁免候选）。全程计数可观测（executed/kindFiltered 与 skipped 分桶一致）。
- [x] roadmap item 21 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 item 28 依赖注记（deps: 18, 21——本项完成即解锁 28 的最后依赖）与 M4 未受扰动（19–29 未全 done，M4 不翻转）。—— item 21 已置 `planned`（行内注明 done 待 closure audit，successor trigger 注记）；item 20/22/23/24/28 注记与 M4 `todo` 未受扰动。
- [x] Exit Criteria 汇总：core+nop 双模块测试退出码 0；owner-doc 增注一致；`ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [x] ORM/xbiz fixtures 套件全绿并已入 design 02 §1 清单（Minimum Rules #25 + 硬约束）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0。
- [x] roadmap 回写正确，周边 item（尤其 item 28 解锁状态）与 M4 未受扰动。
- [x] owner-doc：design 02 §1 与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：三个 Phase（XNode 结构匹配器与接线裁定、引擎/RuleTester/CLI 集成、ORM/xbiz fixtures 与收口）执行项与 Exit Criteria 全数勾选（32/32 计数域 checklist），文件内无未勾选的 in-scope 项残留；五项 Decision（接线方案、XNode pattern 编译形态、relation 算子语义、XML 抑制语义、fixture 规则归属）均有显式落地结论，无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up，无未裁定项残留；行为契约达成——`language: XML` 规则从 `.rule.yml` 经 `XmlLanguage`（ServiceLoader 登记）+ `XmlRuleCompiler`（`LintLanguage.compileRule` → `CompiledRule.precompiled`）端到端可执行、RuleTester XML 套件可发现（`suites/xml/demo-xml-driver` + nop 模块两个 XML 套件经 x:extends 真实管线）、CLI 扩展名表 `xml→xml`/`xbiz→xml` 生效、ORM/xbiz 生产规则 fixtures 套件全绿（`TestNopRuleSuites` 14/14）；必要 focused verification 完成——Phase 1 匹配矩阵（`XNodePatternMatcherTest` 47 用例 + `XmlSourceParserTest` 11 用例，含 meta-var 同名一致性与 `$$$` 通配断言）与 Phase 2 端到端/接线断言（`TestXmlRuleEngineEndToEnd` 10 用例 + `TestXmlCliWiring` 2 用例，kind 过滤计数互斥断言 `rulesExecuted=1/kindFiltered=0` vs `rulesExecuted=0/kindFiltered=1`）全绿；Anti-Hollow Check 通过——XNode 匹配器被引擎运行时真实调用（invalid fixture 命中 + 计数断言，非仅类型存在），无空方法体/静默跳过/no-op 作为正常实现（PI body 解析期 fail-closed、不支持标量形态编译期拒绝、incremental parse 显式拒绝均有用例；`scan-hollow-implementations.mjs --module nop-lint-core/nop-lint-nop --severity high` 均 0 发现）；owner docs 已同步（design 01 §3.5、design 03 §1.1、design 09 §2、design 02 §1 增注与 live 一致，roadmap item 21 回写正确且 M4/item 28 依赖注记未受扰动）；`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（520 tests / 0 failures）且 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0（15 tests / 0 failures）；checkstyle 按 mission `commands.lint` 既有裁定记录；独立子 agent / 独立审阅者 closure-audit 已完成并记录证据到 `## Closure`；`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选 in-scope 项 + Closure Evidence 已写入）——本 section 不再保留可写 checkbox（计数域纪律，01-file-ledger §2.5），机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

- dispatch review #review-2026-09-22-085436-mission-driver-2026-09-22-1045-2-xnode-pattern-engine-1-10ac6ec8 to ses_f38f68be4ffe1klzJdjLaixpmM
- 2026-09-22：iteration 1，共识 approved #review-2026-09-22-085436-mission-driver-2026-09-22-1045-2-xnode-pattern-engine-1-10ac6ec8

## Verification

- pass test 20260922-1337 exit=0

（2026-09-22 closure audit 独立复核运行：`./mvnw -pl nop-lint/nop-lint-core -am test-compile -T 1C` exit=0；`./mvnw -pl nop-lint/nop-lint-core -am clean package -DskipTests -T 1C` exit=0；`./mvnw -pl nop-lint/nop-lint-core -am checkstyle:check -q` 走 mission `commands.lint` 既有裁定 `|| echo 'lint not configured'` 路径（nop-api-core 9226 处全仓遗留基线，与 1420-1/2137-*/0128-*/0544-*/1045-1 一致）；`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` exit=0（520 tests / 0 failures，含 `XNodePatternMatcherTest` 47 用例、`XmlSourceParserTest` 11 用例、`TestXmlRuleEngineEndToEnd` 10 用例、`TestXmlCliWiring` 2 用例）；`./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` exit=0（15 tests / 0 failures，含 `TestNopRuleSuites` 14/14 两个 XML 套件经 x:extends 真实管线）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 与 `--module nop-lint-nop --severity high` 均 0 发现 exit=0。）

## Closure

- dispatch audit #audit-20260922-1337-2026-09-22-1045-2-xnode-pattern-engine-1-30dbcf70 to opencode-2026-09-22-closure models={exec:build/zhipuai-coding-plan-glm-5.3-flash,aud:build/zhipuai-coding-plan-glm-5.3-flash}
- accepted #audit-20260922-1337-2026-09-22-1045-2-xnode-pattern-engine-1-30dbcf70：审计结论 APPROVE-CLOSURE——item 21 XNode Pattern 引擎三 Phase 全部落地且语义真实：CLOSURE_SCRIPT_CHECK 报 3 项（`ledger-structure-invalid` + `missing-pass:test` + `no-audit-receipt`），本 visit 全部消解——`## Closure Gates` 11 个 checkbox 位于 ledger 计数域外（01-file-ledger §2.5），按 §4.3 消解先例改为纯 prose 记录（同 1420-1/2137-1/2137-2/2137-3/0128-1/0544-1/0544-2/1045-1 处置），计数域回到 32/32 勾选、0 unchecked；`## Verification` 补 pass 行、`## Closure` 补 dispatch/accepted 收口对；`node tools/mission-driver/src/plan-check.mjs --strict` exit=0。语义复核：Phase 1 XNode 匹配器内核落地（`xml/` 包 9 类：XmlTagKinds/XNodeLintNode/XmlSourceParser/XNodePattern/XNodePatternCompiler/XNodePatternMatcher/XmlValueNode + XmlLanguage/XmlRuleCompiler；标签=kind、属性字面量精确相等/meta-var 捕获/声明属性必须存在、trimmed 文本 `$$$` 含空通配、`x:` 前缀排除、注释节点经 `#comment` trivia 跳过），meta-var 语义一致性由共享 `MetaVarSyntax`/`MetaVarEnv` 保证并有跨位断言；五项 Decision 均有显式落地结论（binding 钩子 `LintLanguage.compileRule` default null 零下游分支、`XNodeParser→XNodePattern` 编译形态、relational 经 LintNode 门面复用 + XML 不支持形态编译期 fail-closed、抑制落地非 gap——XNode 注释经 facade `#comment` 进入既有 `CommentSuppressionScanner` 零 XML 特判、fixture 规则生产化入 nop-lint-nop 主资源）。Phase 2 接线验证（Minimum Rules #22/#23）——`TestXmlRuleEngineEndToEnd`（xdef 加载 → compileRule → 匹配 → 诊断行号 + kind 过滤计数互斥断言）与 `TestXmlCliWiring`（TargetScanner → CheckRunner → LintEngine 全链 `rulesExecuted=2` + skipped 分桶）证明 XNode 匹配器被引擎运行时真实调用，非仅类型存在；RuleTester XML 套件发现同构复用 + item 19 占位 fixture 按其自述前提翻转为 `no-fixtures-for-language`；无静默跳过（PI body 解析期 fail-closed、unsupported forms 编译期拒绝、incremental parse 显式拒绝各有用例）。Phase 3 两条生产规则（`nop-orm-mandatory-default`/`nop-xbiz-auth-not-sole-guard`）真实平台约定检查 + `TestNopRuleSuites` 14/14 全绿 + nop-auth.orm.xml 200 warning 有界 dogfood 计数可观测；roadmap item 21 `done` 已由本 closure visit 翻转（周边 item 28 依赖解锁注记与 M4 `todo` 未受扰动），owner-doc 增注（design 01 §3.5、design 03 §1.1、design 09 §2、design 02 §1）与 live 一致。本 visit 实跑验证套件全绿（test-compile / clean package -DskipTests / core test 520 / nop test 15 均 exit=0，checkstyle 走 mission 既有裁定路径，hollow scan 两模块 0 发现）；无 in-scope live defect 被降级。
