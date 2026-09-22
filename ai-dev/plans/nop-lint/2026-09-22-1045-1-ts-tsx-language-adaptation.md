---
status: active
mission: nop-lint
work-item: "item-19"
group: "2026-09-22-1045"
verify: [test]
---

# TS/TSX 语言适配（roadmap item 19）：nop-lint-js 模块 + typescript/tsx 绑定 + CLI 扩展名表

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- 依赖满足：item 19 deps = M1（2026-09-21 达成，plans 01–04 全 `done`）。语言适配先例完整：plan 02（LintNode 门面 + Java 语言适配）已交付 `JavaLanguage` 绑定、ServiceLoader 发现机制、`TreeSitterLanguageAdapter` 共享适配器。
- ts/tsx grammar blob 已随 nop-treesitter 出货：`/grammars/typescript/tree-sitter-typescript-blob.bin` 与 `/grammars/tsx/tree-sitter-tsx-blob.bin`（`nop-treesitter/src/main/resources/grammars/` 下实存）；`DefaultTreeSitterLanguageProvider` 以 id `typescript`/`tsx` 注册两 blob（命名先例）；`TsCorpusTest`/`TsxCorpusTest` 证明双 grammar 解析可用。
- 当前 nop-lint 模块组只有三个模块（`nop-lint/pom.xml` `<modules>`：nop-lint-core、nop-lint-java、nop-lint-nop）；无 nop-lint-js。design 01 §1 模块图已预留 `nop-lint-js/`（"TypeScript/TSX 支持（含 tsc bridge，Phase 2）"）。
- `LintLanguage` 契约（`lang/LintLanguage.java`）：id/treeSitter/parse/parseIncremental/preprocessPattern/kindId/suppressionProvider（default null）。`TreeSitterLanguageAdapter(id, language, expando)` 的 expando 为 null 时即 identity 预处理；`JavaLanguage` 用 identity，理由是 `$` 是合法 Java 标识符字符——ECMAScript/TS 同样允许 `$` 作标识符字符（含连续 `$$$`），故 TS/TSX 预期同样 identity，但该断言必须由两 grammar 的测试钉死，不得作为未验证假设落地。
- `LanguageRegistry`（`engine/LanguageRegistry.java`）：语言 id 大小写不敏感解析（规则 `language: TypeScript` → `typescript`）；同 id 冲突注册 fail-closed。kind 名映射由 adapter 的 `buildKindIds` 全名表（含 alias、named 优先）覆盖，TS kind 名（`call_expression`/`member_expression`/`variable_declarator` 等）无需额外映射层。
- CLI 现状：`cli/TargetScanner.java:19-22` v1 约定 `extension == language id`，javadoc **显式把更丰富的扩展名表指派给本 item**（"richer extension tables arrive with the language modules that need them (e.g. ts/tsx, roadmap item 19)"）——当前 `.ts`/`.tsx` 文件进 skipped summary（显式计数，不静默丢弃）。
- xscript/deadline/suppression 管线对语言无感知（消费 `LintLanguage` + `LintNode` 门面）；TS 无 v1 范围内的 `@SuppressWarnings` 等价注解 → 绑定 `suppressionProvider()` 返回 null → 走 `SuppressionFilter` 的纯内联注释扫描缺省路径（已文档化的行为分支，非静默）。
- 抑制/comment 形态差异：TS 行注释 `//`、块注释 `/* */` 与 Java 相同词法形态，`CommentSuppressionScanner` 的适配性需在测试中确认（若按语言参数化，改动落在 core 的 scanner 配置位，不触 nop-treesitter）。
- 硬约束适用：不修改 nop-treesitter 既有类；TSQuery 冻结（本 plan 不涉匹配内核）；零平台改动（全部工作在 nop-lint 模块组 + 父 pom 注册）；demo 规则同样按 Wave-2+ 纪律带 RuleTester fixtures。

## Goals

- 新建 `nop-lint-js` Maven 模块并注册进 nop-lint 父 pom：`TypeScriptLanguage`（id `typescript`）与 `TsxLanguage`（id `tsx`）两个 ServiceLoader 可发现绑定，经 `TreeSitterLanguageAdapter` 驱动已出货 blob（shared-adapter 惯例同 `JavaLanguage`：每 JVM 一次 blob 解码，public no-arg ctor 供 ServiceLoader）。
- **Decision**：`$`-expando 语义钉死——验证 `$VAR`/`$$$ARGS`/`$_X` 在两 grammar 中均按单标识符 lex；成立则 identity 预处理（同 Java）并回写设计增注，不成立则在本 plan 内裁定预处理规则经 adapter expando 位落地。
- CLI 扩展名表：`TargetScanner` 的 `extension == language id` 约定升级为显式扩展名→语言 id 表（v1 条目 `ts→typescript`、`tsx→tsx`、`java→java` 等价迁移），保持 skipped summary 显式计数纪律；**Decision** 裁定表形态与归属（`.mts`/`.cts` 不在 v1 表内）。
- 端到端可验证：demo TS 规则 + RuleTester 套件（pattern 匹配 + kind 匹配）+ CLI 对 `.ts`/`.tsx` fixture 的扫描链路断言（绑定被 CLI 运行时真实消费）。
- roadmap item 19 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）。

## Non-Goals

- tsc bridge（item 20）、L2 符号求解（item 26）——本 plan 只交付语法级适配，不做任何类型推导。
- TS 方向的生产规则批量落地（item 29 quality/security 批、item 35 规则库扩充）；本 plan 只落 demo/验证规则。
- React/JSX 组件分析、ImportTracker、exhaustive-deps（design 05 §6，Phase 3）。
- XNode/XML（item 21）、constraints（item 22）、autofix（item 25）、maven-plugin/graphql 生态（Wave 6）。
- 严格度、匹配器内核改动（TSQuery 冻结；内核在 item 23 已收敛，本 plan 零内核改动）。

## Phase 1 — nop-lint-js 模块骨架与双语言绑定（Fix + Proof）

Status: completed
Targets: `nop-lint/pom.xml`、`nop-lint/nop-lint-js/`（新模块：pom + `io.nop.lint.js` + `META-INF/services`）

- Item Types: `Fix | Decision | Proof`

- [x] 模块注册：nop-lint 父 pom `<modules>` 新增 `nop-lint-js`；模块 pom 依赖 nop-lint-core + JUnit 5（依赖口径照抄 nop-lint-java 先例）。
- [x] `TypeScriptLanguage`/`TsxLanguage` 绑定：`TreeSitterLanguageAdapter("typescript"/"tsx", Language.fromClasspath(blob), null)`，blob 路径 `/grammars/typescript/tree-sitter-typescript-blob.bin`、`/grammars/tsx/tree-sitter-tsx-blob.bin`；shared static adapter + public no-arg ctor（ServiceLoader 发现路径）+ `get()` 单例（同 `JavaLanguage` 结构）。
- [x] ServiceLoader 注册文件 `META-INF/services/io.nop.lint.core.lang.LintLanguage` 登记两绑定。
- [x] **Decision（$ expando 语义）**：以两 grammar 的解析实验钉死 `$$$ARGS`/`$VAR`/`$_X` 是否按单标识符 lex（`$$$ARGS` 含连续 `$`）；成立 → identity 预处理并在测试中断言 `preprocessPattern` 恒等；不成立 → 在本 phase 裁定预处理规则经 `TreeSitterLanguageAdapter` expando 位落地（不改 nop-treesitter），并回写 design 01 §4（预处理步骤）与 design 05 增注。**裁定：identity 成立**——两 grammar 解析级断言钉死（`$$$ARGS` 单标识符形参 / `$VAR` declarator name / `$_X` 恒等 pattern），design 01 §4 增注已回写。
- [x] 单元测试矩阵（Minimum Rules #25）：两绑定 ×（parse(String)/parse(byte[])、kindId 代表性 TS kind 名 + 未知 kind 返回 -1、parseIncremental smoke（复用 adapter 既有契约）、preprocessPattern identity 或裁定后 expando）。
- [x] 抑制适配断言：`suppressionProvider()` 返回 null 的契约断言 + `CommentSuppressionScanner` 对 `//`/`/* */` 注释形态在 TS 源上的行为确认（若需参数化改动，落 core，改动点记录在日志）。**结论：scanner 经 LintNode 门面天然语言无关（comment extras），`//` 与 `/* */` 双形态产 span 断言全绿，零参数化改动。**

Exit Criteria:

- [x] 绑定矩阵全格有断言（Minimum Rules #25）：两绑定 ×（parse/kindId/parseIncremental/preprocessPattern/suppression）。
- [x] 无静默跳过（Minimum Rules #24）：expando 结论为显式 identity 断言或显式 expando 实现，无未验证假设；未知 kind 显式 -1 契约有断言。
- [x] `./mvnw -pl nop-lint/nop-lint-js -am test -T 1C` 退出码 0（显式验证：mission `test` 键不覆盖本模块，按 plan Exit Criteria 显式运行）。
- [x] owner-doc：design 01 §1（nop-lint-js 模块就位）增注与 live 一致；expando 裁定结论已回写（如适用）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — CLI 扩展名表 + 端到端规则验证（Decision + Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-core`（`cli/TargetScanner` + 测试）、`nop-lint/nop-lint-js`（test resources：demo 套件）、`ai-dev/design/nop-lint/03-execution-engine.md`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（扩展名表形态）**：`TargetScanner` 从 `extension == language id` 约定升级为显式扩展名→语言 id 表（v1 条目：`ts→typescript`、`tsx→tsx`，`java→java` 等价迁移保持既有行为）；未命中扩展名照旧进 skipped summary（显式计数，不静默）；表归属（静态 map 于 TargetScanner vs LanguageRegistry 提供查询）随裁定确定；结论回写 design 03 §2.4 增注（替换"richer extension tables arrive with item 19"前瞻注记为落地形态）。**裁定：静态 map 于 TargetScanner（双向查询 API `languageIdForExtension`/`extensionsForLanguage`，LanguageRegistry 保持纯 id 解析器）；lintable = 扩展名命中表 且 映射 id 已注册；`.mts`/`.cts` v1 不入表（skipped 分桶显式计数）**。附带 core 改动（记录）：`RuleTestRunner` 夹具扩展名自同表反查（`*.java`/`*.ts`/`*.tsx`），规则语言无表项 = 套件显式失败——夹具面与 CLI 扫描面单一事实源，防漂移。
- [x] CLI 单元测试：`.ts`/`.tsx` fixture 经 TargetScanner → CheckRunner → 诊断/汇总输出全链断言（**接线验证** Minimum Rules #23：TS 绑定被 CLI 运行时真实消费，非仅注册存在）；未命中扩展名分桶计数断言。
- [x] demo TS 规则 + RuleTester 套件（nop-lint-js test resources，结构仿 nop-lint-core `_vfs/test/lint/suites/demo/no-console`：nop-lint-js 侧同放 `src/test/resources/_vfs/test/lint/suites/` 下，RuleTester 经 VFS 加载）：≥1 valid + ≥2 invalid + `.expect`；规则 `language: TypeScript`（验证大小写不敏感解析链）。**落地两套件**：`demo/ts-no-console`（pattern 匹配，1 valid + 2 invalid）、`demo/ts-no-enum`（kind 匹配，1 valid + 1 invalid）。
- [x] **端到端验证**（Minimum Rules #22）：`.rule.yml`(language: TypeScript) → VFS 加载 → `RuleDslParser` → `LintEngine.lint`（kind 过滤 → matcher → suppression 尾）→ 诊断断言全链绿。
- [x] TSX 专项：TsxLanguage 解析含 JSX 元素的源码 ≥1 例断言；typescript 绑定对 TSX 语法的失败/降级路径按 grammar 实际行为显式断言（ERROR 节点或解析状态，钉死不猜测）。**实测钉死：tsx grammar 解析 JSX 零 ERROR（jsx_element 家族 kind 全命中）；typescript grammar 对同源产 ERROR 节点且 `jsx_element` kindId=-1**（`TsxSpecificsTest`）。

Exit Criteria:

- [x] 端到端 + 接线验证有断言证据（Minimum Rules #22/#23）。
- [x] demo 套件全绿（Minimum Rules #25：新规则带 fixtures 的硬约束）。
- [x] `./mvnw -pl nop-lint/nop-lint-js -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 03 §2.4 增注与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — 收口（Proof）

Status: completed
Targets: `ai-dev/backlog/nop-lint-roadmap.md`、`ai-dev/design/nop-lint/`（01/03/05）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] roadmap item 19 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 item 20/26 依赖注记未受扰动；M4 不因单项完成翻转（19–29 未全 done）。**已置 `planned`（successor trigger：closure audit pass → `done`，注记在 roadmap 行内）；item 20/26 依赖注记、M4 `todo` 未受扰动。**
- [x] owner-doc 收口核对：design 05 §6（TSX 语法行状态更新）、design 01 §1/§4 增注与 live 一致；expando/扩展名表两项 Decision 的回写落点齐全。
- [x] Exit Criteria 汇总：`./mvnw -pl nop-lint/nop-lint-js -am test -T 1C`、`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0；`ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [x] roadmap 回写正确，周边 item 与 M4 状态未受扰动。
- [x] owner-doc 与 live 一致（无未回写的 Decision）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：三个 Phase（nop-lint-js 模块骨架与双语言绑定、CLI 扩展名表 + 端到端规则验证、收口）执行项与 Exit Criteria 全数勾选（27/27 计数域 checklist），文件内无未勾选的 in-scope 项残留；无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up——expando 与扩展名表两项 Decision 均有显式落地结论（identity 经两 grammar 解析级断言钉死；静态 map 于 TargetScanner 经双向查询 API 收敛单一事实源），无未裁定项残留；行为契约达成——`typescript`/`tsx` 绑定经 ServiceLoader 可发现（`JsBindingDiscoveryTest`）、CLI 扩展名表生效（`TestTargetScanner` + `TestJsCliWiring`）、demo TS 规则端到端绿（`TestJsRuleSuites`：`ts-no-console` pattern 匹配 + `ts-no-enum` kind 匹配）；必要 focused verification 完成——Phase 1 绑定矩阵（parse/kindId/parseIncremental/preprocessPattern/suppression）与 Phase 2 端到端/接线断言全绿；Anti-Hollow Check 通过——TS/TSX 绑定被 CLI 运行时真实消费（`TestJsCliWiring` 经 TargetScanner → CheckRunner 全链断言，非仅 ServiceLoader 注册存在），无空方法体/静默跳过/no-op 作为正常实现（未知 kind 显式 -1 契约有断言，未命中扩展名显式分桶计数）；owner docs 已同步（design 01 §1/§4、design 03 §2.4、design 05 §6 增注与 live 一致，roadmap item 19 回写正确）；`./mvnw -pl nop-lint/nop-lint-js -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0；checkstyle 按 mission `commands.lint` 既有裁定记录；独立子 agent / 独立审阅者 closure-audit 已完成并记录证据到 `## Closure`；`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选 in-scope 项 + Closure Evidence 已写入）——本 section 不再保留可写 checkbox（计数域纪律，01-file-ledger §2.5），机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

- dispatch review #review-2026-09-22-085436-mission-driver-2026-09-22-1045-1-ts-tsx-language-adaptation-1-558aa2c2 to w0t1p0:1E2957B5-0546-42C5-BB14-484529CD402C
- 2026-09-22：iteration 1，共识 approved #review-2026-09-22-085436-mission-driver-2026-09-22-1045-1-ts-tsx-language-adaptation-1-558aa2c2

## Verification

- pass test 20260922-1152 exit=0

（2026-09-22 closure audit 独立复核运行：`./mvnw -pl nop-lint/nop-lint-js -am test-compile -T 1C` exit=0；`./mvnw -pl nop-lint/nop-lint-js -am clean package -DskipTests -T 1C` exit=0；`./mvnw -pl nop-lint/nop-lint-js -am checkstyle:check -q` 走 mission `commands.lint` 既有裁定 `|| echo 'lint not configured'` 路径（nop-api-core 9226 处全仓遗留基线，与 1420-1/2137-*/0128-*/0544-1/0544-2 一致）；`./mvnw -pl nop-lint/nop-lint-js -am test -T 1C` exit=0（nop-lint-core 449 tests / 0 failures + nop-lint-js 33 tests / 0 failures，含 `JsBindingDiscoveryTest`、`TypeScriptLanguageTest`、`TsxLanguageTest`、`TsxSpecificsTest`、`TsSuppressionContractTest`、`TestJsRuleSuites`、`TestJsCliWiring`）；`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` exit=0（449 tests / 0 failures）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-js --severity high` 与 `--module nop-lint-core --severity high` 均 0 发现 exit=0。）

## Closure

- dispatch audit #audit-20260922-1152-2026-09-22-1045-1-ts-tsx-language-adaptation-1-ec33f00c to opencode-2026-09-22-closure models={exec:build/zhipuai-coding-plan-glm-5.3-flash,aud:build/zhipuai-coding-plan-glm-5.3-flash}
- accepted #audit-20260922-1152-2026-09-22-1045-1-ts-tsx-language-adaptation-1-ec33f00c：审计结论 APPROVE-CLOSURE——item 19 TS/TSX 语言适配三 Phase 全部落地且语义真实：CLOSURE_SCRIPT_CHECK 报 3 项（`ledger-structure-invalid` + `missing-pass:test` + `no-audit-receipt`），本 visit 全部消解——`## Closure Gates` 11 个 checkbox 位于 ledger 计数域外（01-file-ledger §2.5），按 §4.3 消解先例改为纯 prose 记录（同 1420-1/2137-1/2137-2/2137-3/0128-1/0544-1/0544-2 处置），计数域回到 27/27 勾选、0 unchecked；`## Verification` 补 pass 行、`## Closure` 补 dispatch/accepted 收口对；`node tools/mission-driver/src/plan-check.mjs --strict` exit=0。语义复核：Phase 1 双绑定（`TypeScriptLanguage`/`TsxLanguage`，shared static `TreeSitterLanguageAdapter` + public no-arg ctor + `META-INF/services` 登记，结构同 `JavaLanguage`）经 `JsBindingDiscoveryTest` ServiceLoader 发现断言；expando identity 裁定由两 grammar 解析级断言钉死（`$$$ARGS`/`$VAR`/`$_X` 单标识符，`TypeScriptLanguageTest`/`TsxLanguageTest.metaVarTokensParseAsSingleIdentifiers`）；`suppressionProvider()` null 契约 + `//`/`/* */` 双注释形态 span 断言（`TsSuppressionContractTest`）。Phase 2 扩展名表静态 map 于 `TargetScanner`（`languageIdForExtension`/`extensionsForLanguage` 双向查询，`.mts`/`.cts` v1 不入表、skipped 分桶显式计数）+ `RuleTestRunner` 夹具扩展名同表反查 fail-closed（`TestRuleTestRunnerFailures`/`TestTargetScanner`）；端到端 + 接线验证（Minimum Rules #22/#23）——`TestJsRuleSuites`（`ts-no-console` pattern 匹配 + `ts-no-enum` kind 匹配经 VFS → `RuleDslParser` → `LintEngine` 全链）与 `TestJsCliWiring`（`.ts`/`.tsx` fixture 经 TargetScanner → CheckRunner 全链，TS 绑定被 CLI 运行时真实消费）全绿；TsxSpecificsTest 钉死 tsx grammar 解析 JSX 零 ERROR、typescript grammar 对同源产 ERROR 节点。Phase 3 roadmap item 19 `done` 已由本 closure visit 翻转（周边 item 与 M4 未受扰动），owner-doc 增注（design 01 §1/§4、design 03 §2.4、design 05 §6）与 live 一致。本 visit 实跑验证套件全绿（test-compile / clean package -DskipTests / js test 449+33 / core test 449 均 exit=0，checkstyle 走 mission 既有裁定路径，hollow scan 两模块 0 发现）；无 in-scope live defect 被降级。
