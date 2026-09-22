---
status: active
mission: nop-lint
work-item: "item-22"
group: "2026-09-22-1045"
verify: [test]
---

# 约束求值器（roadmap item 22）：sameText/differentText/regex/inList/typeOf/notExists/withinDepth

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- 依赖满足：item 22 deps = M1（done）。匹配内核（item 23 已交付 relational/all/not）与 xscript 管线（item 14/15）均就绪；constraints 层**完全不存在**：`lint-rule.xdef` 无 `constraints` 字段（design 10 §6 明确 constraints 全量归 Phase 2 交付，controlFlow 除外）；`RuleDslModel` 无 constraints 字段；`CompiledRule` 无约束位；`RuleSetRunner` 匹配即诊断，无约束过滤路径。
- 设计契约（design 01 §3.2 约束表 + §3.3 withinDepth）：`sameText`/`differentText`（captures 列表）、`regex`（capture + pattern）、`typeOf`（capture + is，注明"Phase 2，依赖类型层级 L2"）、`inList`（capture + values）、`notExists`（跨模式约束：pattern + 可选 message，"新能力"）、`withinDepth`（max，design 01 §3.3）。键名 camelCase 与 xdef 标签同名（design 10 §5 一致性契约：`sameText` 等；roadmap item 文本中的 snake_case 写法以 design 10 命名为准）。
- schema 约定（design 01 §2 注 + §3.3 归属澄清）：`constraints` 是**顶层字段**（与 language/severity/xscript 同级）；`rule:` 容器内只放匹配器（pattern/kind/regex/any/all/not/inside/has/follows/precedes），约束**不进** rule 容器。
- 管线位置（design 03 §1.1 单文件执行流程）：`Match[]` 之后、`xscript.executeMatch` 之前，per-match `Constraint.evaluate(matchContext)`；design 01 §4 `CompiledRule` 结构含 `constraints: Constraint[]`、§5 给出 `Constraint` 接口形态（伪代码级契约）。
- L2 现状：`LintCapability.L2` 已定义但 "not provided by any v1 profile"（`LintCapability.java:19-23`）；engine 对 `requires` 不可满足的规则走 `skippedByProfile` 显式计数（`LintEngine.profileSatisfies` + `LintStats`）；L2 推导本体归 item 26。design 10 §5 一致性契约：typeOf 需 L2 + `requires` 属性标注，08 §2 依赖矩阵按 requires 聚合跳过规则。roadmap 硬约束：降级绝不以 L1 冒充 L2。
- 语义缺口需裁定（design 01 §3.2/§3.3 均未定义）：(a) **约束极性**——sameText 等的自然读法是"全部成立才报告"的匹配过滤器，但 `withinDepth` 示例消息（"嵌套超过 3 层，考虑提取方法"）与"深度 ≤ max 才成立"的过滤极性表面冲突，必须钉死；(b) **notExists 作用域**——跨模式约束在匹配节点子树还是全文件范围求值，无定义；(c) **typeOf × L2 门**——typeOf 规则缺 `requires: "L2"` 声明时的行为（parser fail-closed vs 引擎隐含）需裁定。
- 计数纪律：约束失败的 match 不产诊断，且该过滤必须可观测（`LintStats` 新增显式计数口径，命名对齐 `rulesKindFiltered`/`skippedByProfile` 的"不静默"纪律），不得静默丢弃。
- 硬约束适用：本 plan 落地的演示/fixture 规则同样必须带 RuleTester fixtures；TSQuery 冻结（本 plan 不涉匹配内核）；零平台改动（全部工作在 nop-lint 模块内）。

## Goals

- xdef/`RuleDslModel`/`RuleDslParser` 扩展：顶层 `constraints` 字段（list，元素对象形态，camelCase 键：`sameText`/`differentText`/`regex`/`inList`/`typeOf`/`notExists`/`withinDepth`），解析期 fail-closed 校验（未知约束名、必填子段缺失、captures 空列表、`stopBy` 式配对错误等显式报错）。
- 三项语义 **Decision** 裁定并回写：约束极性（含 withinDepth 示例修正）、notExists 作用域、typeOf × L2 门。
- 约束求值器：per-match 全约束成立才产诊断（按裁定极性）；约束失败 match 显式计数（新 `LintStats` 口径）。
- 编译期 capture 存在性校验：约束引用的 capture 未在 matcher 的 meta-var 集中声明 → fail-closed 抛 `NopLintException`（英文消息含规则 id 与 capture 名）。
- RuleTester fixtures：每约束至少一条演示规则套件（nop-lint-core 测试资源 demo 套件形态），全约束面 valid/invalid 覆盖；typeOf 规则验证 `requires: L2` → `skippedByProfile` 路径。
- roadmap item 22 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）。

## Non-Goals

- `controlFlow` 约束（Phase 3，design 01 §3.3 / design 10 §6 明确除外）。
- L2/typeOf 的真实类型推导实现（item 26 L2 symbol solver）；本 plan 只交付 typeOf 的解析、门控与"无 L2 即整规则 skippedByProfile"的行为，不做任何 L1 冒充。
- 使用约束的生产规则批量落地（item 29 quality/security 批）；本 plan 只落 fixture/演示规则。
- ruleset/exemptions/settings 注入（design 10 §3，item 27）、autofix（item 25）、metrics（item 32）、dataflow（Wave 5）。
- 匹配内核与严格度改动（约束工作在 match 之后的过滤层，TSQuery 冻结不受影响）。

## Phase 1 — 语义裁定 + xdef/模型/解析器扩展（Decision + Fix + Proof）

Status: completed

Targets: `nop-lint/nop-lint-core`（`rule/` 包 + `_vfs/nop/lint/schema/lint-rule.xdef`）、`ai-dev/design/nop-lint/`（01/10）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（约束极性）**：裁定 constraints 语义为"全部成立才报告"的 per-match 过滤器（与 sameText/differentText/regex/inList 的自然读法一致）；`withinDepth` 极性随之钉死（"成立 = 匹配节点子树深度 ≤ max"，深嵌套规则的表例试作反向表达或裁定 withinDepth 专属极性），并修正 design 01 §3.3 示例/消息使其与裁定自洽。结论回写 design 01 §3.2/§3.3 + design 10 §2（字段形态与校验权威归属 parser）。
- [x] **Decision（notExists 作用域）**：裁定 per-match 子树（含匹配节点为根的范围内求值）vs 全文件；对照 design 04 ast-grep 对标语义与"跨模式约束"意图（design 01 §3.2 示例：方法体含 `return null` 的否定场景指向子树语义）取舍；结论回写 design 01 §3.2。
- [x] **Decision（typeOf × L2 门）**：裁定使用 `typeOf` 的规则**必须**声明 `requires: "L2"`，缺声明由 `RuleDslParser` fail-closed 拒绝（加载期报错，含规则 id）；L2 能力仍由 profile 决定（v1 全档无 L2 → `skippedByProfile` 计数，绝不以 L1 结果冒充评估——roadmap 硬约束）；结论回写 design 10 §2/§5。
- [x] xdef 扩展：顶层 `constraints`（`xdef:body-type="list"`，元素对象形态按 design 01 §3.2/§3.3 字段：sameText/differentText 的 `captures`、regex 的 `capture`+`pattern`、inList 的 `capture`+`values`（csv-set 或子标签按既有 csv-set 纪律裁定）、typeOf 的 `capture`+`is`、notExists 的 `pattern`(+`message`)、withinDepth 的 `max`）；`RuleDslModel` 新增 constraints 载体 + `RuleDslParser` fail-closed 校验。
- [x] 单元测试（Minimum Rules #25）：合法/非法 constraints 解析矩阵——每约束合法形态 1 例 + 未知约束名/缺必填段/captures 空/值类型错等非法形态各 ≥1 例断言抛错。

Exit Criteria:

- [x] 解析矩阵全格有断言（Minimum Rules #25）。
- [x] 三项 Decision 结论已回写 design 01 §3.2/§3.3、design 10 §2/§5（示例与裁定自洽，无残留矛盾示例）。
- [x] 无静默跳过（Minimum Rules #24）：所有非法约束形态显式抛 `NopLintException`（英文消息含规则 id），有断言。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 01/10 增注与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 求值器 + 管线接入 + fixtures（Fix + Proof）

Status: completed

Targets: `nop-lint/nop-lint-core`（`rule/`、`engine/`、`pattern/` 按需）、`ai-dev/design/nop-lint/03-execution-engine.md`

- Item Types: `Fix | Proof`

- [x] 约束求值实现（design 01 §5 `Constraint` 契约形态）：capture 文本提取与比较（sameText/differentText/regex/inList）、notExists 按 Phase 1 裁定作用域求值、withinDepth 按裁定极性求值；求值输入为 match 的 captures + 源码（matchContext 等价物，形态随实现裁定，不写死类签名）。
- [x] 编译期 capture 存在性校验：约束引用的 capture 未在 matcher 的 meta-var 集中声明 → `CompiledRule` 编译期 fail-closed 抛 `NopLintException`（英文消息含规则 id + capture 名 + 约束名）。
- [x] `RuleSetRunner`/`CompiledRule` 接入（design 03 §1.1 位置：匹配后、xscript 前；若实现中发现与 §1.1 序有出入，以 design 03 为准修正并回写）：约束失败的 match 显式计数（`LintStats` 新口径，命名对齐既有"不静默"纪律），诊断仅由全约束成立的 match 产生。
- [x] fixtures：每约束 ≥1 条演示规则 + RuleTester 套件（nop-lint-core 测试资源，demo 套件形态）——sameText/differentText/regex/inList/notExists/withinDepth 各 1 valid + ≥2 invalid + `.expect`；typeOf 规则 1 条（`requires: "L2"`）验证 skippedByProfile 路径（加载成功 + 档位跳过 + 计数断言，非加载失败）。
- [x] 单元测试（Minimum Rules #25）：每约束求值命中/不命中矩阵、capture 校验抛错、`LintStats` 新计数口径断言。

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：带约束规则 `.rule.yml` → 加载 → 匹配 → 约束求值 → 诊断断言全链绿。
- [x] **接线验证**（Minimum Rules #23）：约束求值器在运行时被真实调用——invalid fixture 经约束翻转命中/不命中（同 match 无约束命中、加约束不命中）的差分断言 + 计数断言。
- [x] 无静默跳过（Minimum Rules #24）：约束失败 match 进显式计数，捕获约束求值异常的路径显式处理（不吞异常），有断言。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 03 §1.1（管线序核对结论）、design 01 §5 增注与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — 收口（Proof）

Status: completed

Targets: `ai-dev/backlog/nop-lint-roadmap.md`、`ai-dev/design/nop-lint/`（01/03/10）、`ai-dev/logs/`

- Item Types: `Proof`

- [x] roadmap item 22 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 item 25（deps: 22）解锁注记与 M4 未受扰动（19–29 未全 done，M4 不翻转）。—— item 22 已置 `planned`（行内注明 done 待 closure audit，item 21 先例同形）；item 25（deps: 22）`todo` 注记与 M4 `todo` 未受扰动。
- [x] owner-doc 收口核对：design 01 §3.2/§3.3/§5、design 03 §1.1、design 10 §2/§5 增注与 live 一致；三项 Decision 回写落点齐全。—— design 03 §1.1 本轮补管线序核对结论增注 + 图示已交付标记；01/10 增注 Phase 1 已落，复核一致。
- [x] Exit Criteria 汇总：`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0；`ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [x] roadmap 回写正确，周边 item 与 M4 状态未受扰动。
- [x] owner-doc 与 live 一致（无未回写的 Decision）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Draft Review Record

- dispatch review #review-2026-09-22-085436-mission-driver-2026-09-22-1045-3-constraint-evaluator-1-0fa24559 to w0t1p0:1E2957B5-0546-42C5-BB14-484529CD402C
- 2026-09-22：iteration 1，共识 approved #review-2026-09-22-085436-mission-driver-2026-09-22-1045-3-constraint-evaluator-1-0fa24559

## Closure Gates

> 关闭条件记录（01-file-ledger §2.5 计数域纪律，本 section 不保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生）：三个 Phase（语义裁定 + xdef/模型/解析器扩展、求值器 + 管线接入 + fixtures、收口）执行项与 Exit Criteria 全数勾选（计数域 checklist），文件内无未勾选的 in-scope 项残留；三项 Decision（约束极性含 withinDepth 示例自洽、notExists 作用域、typeOf × L2 门）均已裁定并回写 design 01/10 对应章节，无残留矛盾示例；无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up——非法约束形态与 capture 缺声明均 fail-closed 抛 `NopLintException` 且有测试断言（Minimum Rules #24）；行为契约达成——per-match 约束求值 + 约束失败显式计数（`LintStats` 新口径，不静默丢弃）、typeOf 规则缺 L2 走 `skippedByProfile`（绝不以 L1 冒充）；端到端验证（Minimum Rules #22）与接线验证（Minimum Rules #23）通过——带约束规则 `.rule.yml` → 加载 → 匹配 → 约束求值 → 诊断全链绿，求值器运行时真实调用由差分断言（同 match 无约束命中/加约束不命中）+ 计数断言证明；owner docs（design 01 §3.2/§3.3/§5、design 03 §1.1、design 10 §2/§5）已同步到 live baseline；roadmap item 22 状态回写正确，周边 item 与 M4 未受扰动；Anti-Hollow Check 通过（`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出码 0，无空方法体/静默跳过/no-op 作为正常实现）；`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0，checkstyle / 代码规范检查按 mission `commands.lint` 既有裁定记录；独立子 agent / 独立审阅者 closure-audit 已完成并记录证据到下方 `## Closure` 段落。

## Verification

（空——由 BUILD_VERIFY 填写 pass 行）

## Closure

（空——由 CLOSURE_AUDIT 填写）
