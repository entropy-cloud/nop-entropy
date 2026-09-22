---
status: active
mission: nop-lint
work-item: "item-23"
group: "2026-09-22-0544"
verify: [test]
---

# 关系匹配器与关系规则（roadmap item 23）：inside/has/follows/precedes + StopBy + field 约束

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- 依赖满足：item 23 deps = item 5（ChildMatcher lockstep + trivial-node skipping，plan 04 `done`）；M1 已达成。当前 nop-lint 内核（`pattern/` 包：PatternMatcher/ChildMatcher/MetaVarEnv/Strictness/SourcePattern）只支持单 pattern/kind/regex/单层 any 匹配器，无任何关系算子。
- 设计权威（design 04 §5 关系规则算法表 + StopBy.find 伪代码）：`inside`（祖先）/`has`（后代）/`follows`（后兄弟）/`precedes`（前兄弟）四算子；`stopBy: neighbor`（仅直接邻接）/`end`（全遍历）/`rule`（take-while 含 stop 节点）；`field` 约束限定子节点字段名。
- 归属澄清（design 01 §3.3）：`inside/has/follows/precedes/not` 是 **`rule:` 内的匹配器**（与 pattern/kind/regex 同级，可组合于 any/all/not）；`constraints:` 层只放 capture 值约束（sameText/regex/typeOf/inList/notExists/withinDepth，归 item 22）。
- 当前 DSL 面缺口：`lint-rule.xdef`（`nop-lint-core/src/main/resources/_vfs/nop/lint/schema/lint-rule.xdef`）的 rule 容器仅 `pattern|kind|regex|any`；`RuleDslParser` XOR 集常量 `RULE_MATCHERS = {"pattern","kind","regex","any"}`（`RuleDslParser.java:54`，fail-closed 消息 ：117-126）。`RuleDslModel.Matcher` 仅 pattern/kind/regex/any 四形态（`RuleDslModel.java:150-183`，手写模型，无 codegen 产物）。design 10 §2 注记：`stopBy` 三档对齐 04 §5，`stopByRule` 在 `stopBy=rule` 时必填（由 parser-class 校验），Wave 4 扩展时在 RuleDslParser 同处追加。
- 承接的 deferred 项（plan `2026-09-21-2137-3-core-rules-first-batch` `Deferred But Adjudicated`，均 `Successor Required: yes`，Successor Path = 本 item 对应 plan）：**exception/silent-swallow**（faithful 语义 = catch 块七信号缺失判定，需 `has` + `not` + 基 pattern 组合挂载）与 **exception/no-log-getmessage**（`has: $E.getMessage()` 且 `not: has: throw $$$`）。v1 xscript text.contains 近似存在注释/字符串旁路，被裁定不得落地；两不变式当前分别由 `ai-dev/tools/check-silent-swallow.mjs`（含 comment-bypass fixture）与 `ai-dev/tools/rules/java-lint-getmessage-only.yml`（ast-grep CLI）继续承担。
- 承接的 deferred 项（plan `06-lint-rule-xdef-and-dsl-parser` `Deferred But Adjudicated`，`Successor Required: yes`）：`stopBy=rule` 必填 `stopByRule` 校验随 Wave 4 扩展 xdef 时在 RuleDslParser 落地——本 plan 承接其 stopBy 部分；`matches`→utils 存在性校验归 item 24 对应 plan。
- kind 预过滤现状：`RuleSetRunner.run` 经 `KindIndex.collect` + `canMatchKinds` 做位图短路（`RuleSetRunner.java:59,63`）；design 04 §8 P0 要求 all 取 kind 交集、any 取并集——relational/all/not 顶层规则的 kind 提取策略需显式裁定。
- 硬约束适用：TSQuery 冻结（关系算子全部落在 SourcePattern matcher 家族）；不修改 nop-treesitter 既有类；`nop-xlang/nop-core` 零平台改动（本 plan 全部工作在 nop-lint 模块内）；每条 Wave-2+ 规则必须带 RuleTester fixtures。
- 前置未阻塞：xscript/deadline/suppression 管线（design 03 §1.1）对 matcher 家族无感知，关系规则落在匹配层，管线无需改动。

## Goals

- 关系匹配器四算子按 design 04 §5 全量落地：`inside`/`has`/`follows`/`precedes` × `stopBy` 三档（neighbor/end/rule）+ `field` 约束；`stopBy=rule` 缺 `stopByRule` fail-closed（承接 plan 06 deferred）。
- 最小组合算子面：`all`（合取：kind 交集预过滤 + scratchpad env + 全部命中才提交）与 `not`（否定：probe env 隔离，xor 语义，绑定不泄漏）按 design 04 §6 落地——这是两条 successor 规则 faithful 表达的最小需求；`matches`/utils 递归与 any 嵌套 refinement 仍归 item 24。
- xdef/`RuleDslModel`/`RuleDslParser` 扩展：rule 容器新增 relational 匹配器与 all/not 语法面（含 `stopBy`/`stopByRule`/`field`），XOR 集同步扩展并保持 fail-closed 纪律（未知匹配器、多重顶层、非法嵌套显式报错）。
- 两条 successor 规则 faithful 落地（exception/silent-swallow、exception/no-log-getmessage）+ RuleTester fixtures + 与现有 mjs/ast-grep 门禁的语料行为对照记录（现有门禁保持不动，切换归 item 28）。
- 算子分界 Decision（本 plan vs item 24）与注释/字符串掩码语义结论回写 design 01 §3.3/§3.4、design 04 §5/§6。
- roadmap item 23 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）。

## Non-Goals

- `matches` 递归 + `utils` 共享规则 + `any` 嵌套 refinement（item 24；plan 06 deferred 的 matches→utils 存在性校验随 item 24 落地）。
- constraints 层（sameText/differentText/regex/inList/typeOf/notExists/withinDepth/controlFlow）= item 22（constraint evaluator）。
- autofix（item 25）、TS/TSX 适配（item 19）、XNode 引擎（item 21）、tsc bridge（item 20）。
- CST/Signature/Template 严格度（plan 04 deferred，需要时另行立项，与本 plan 无关）。
- check-\*.mjs / ast-grep 门禁的下线或切换（item 28 manifest 决定；本 plan 只做行为对照）。
- 严格度新增（TypeAware）与 L2 类型信息接入（typeOf 约束归 item 22 + item 26）。

## Phase 1 — 内核：关系匹配器 + StopBy + field 约束（Decision + Fix + Proof）

Status: completed

Targets: `nop-lint/nop-lint-core`（main + test，`pattern/` 包）

- Item Types: `Decision | Fix | Proof`

- [x] 实现 `inside`/`has`/`follows`/`precedes` 四算子：按 design 04 §5 StopBy.find 语义——neighbor 仅直接邻接一次、end 全遍历 find_map、rule 档 take-while 含 stop 节点本身（inclusive_until）。
- [x] `field` 约束：关系匹配器限定子节点字段名（fieldId 过滤），未声明 field 时不过滤。
- [x] **Decision（注释/字符串掩码语义）**：验证树级匹配下注释/字符串节点的天然隔离边界（pattern 形态不匹配注释/字符串节点），如仍需显式掩码则在本 phase 裁定实现位；结论回写 design 01 §3.3 增注（消解 plan 2137-3 deferred 登记的"注释/字符串掩码"关切）。
- [x] **Decision（relational/all/not 的 kind 预计算策略）**：裁定顶层为 relational/all/not 组合时 `canMatchKinds` 位图如何提取（自 pattern 子树取 kinds 的保守交集 vs 组合顶层退化为全量放行 + 计数），不得以 L1 结果冒充、不得静默全量；结论回写 design 01 §4（编译步骤 5）增注。
- [x] 单元测试矩阵：每算子 × 每 stopBy 档 ×（命中/不命中/stop 短路含 stop 节点本身/field 过滤）+ 与既有 Strictness/trivial skipping 的交互（Minimum Rules #25）。

Exit Criteria:

- [x] 矩阵全格有断言（Minimum Rules #25）。（每算子 × stopBy 档 × 边界形态逐格断言。）
- [x] StopBy=rule 档的 inclusive 语义（stop 节点本身参与匹配）有显式断言。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 01 §3.3/§4、design 04 §5 增注（掩码语义 + kind 策略）与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 组合算子 all/not + xdef/DSL 扩展（Decision + Fix + Proof）

Status: completed

Targets: `nop-lint/nop-lint-core`（main + test：`pattern/`、`rule/` 包、`_vfs/nop/lint/schema/lint-rule.xdef`）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（item 23/24 算子分界）**：裁定本 plan 交付 all/not（successor 规则最小组合面）、item 24 承接 matches/utils/any 嵌套 refinement；结论回写 design 01 §3.4、design 04 §6 与 roadmap item 23/24 注记，消除 roadmap item 文本（not 归 24）与 plan 2137-3 deferred 指针（not 组合归 23）的歧义。
- [x] `all` 合取：kind 交集预过滤 + scratchpad env + 全部子匹配器命中才提交（design 04 §6 语义）。
- [x] `not` 否定：probe env 隔离（inner 绑定不泄漏到真实 env，xor 语义）。
- [x] xdef 扩展：rule 容器新增 `all`（list）/`not`/`inside`/`has`/`follows`/`precedes`（各含 pattern + `stopBy` enum:neighbor|end|rule + `stopByRule` + `field`），按 design 10 §2「递归匹配器按 `<matcher>` 对象模式展开」落地；`RuleDslModel`/`RuleDslParser` 同步（XOR 集扩展 + 嵌套深度/未知匹配器 fail-closed）。
- [x] **承接 plan 06 deferred**：`stopBy=rule` 缺 `stopByRule` → fail-closed 抛 `NopLintException`（英文消息含规则 id）；在 RuleDslParser 既有 XOR 校验同处落地。
- [x] 单元测试：xdef 加载合法/非法组合（XOR 扩展后仍恰一顶层、非法 stopBy 值、stopBy=rule 缺 stopByRule、not/all 嵌套边界）+ all/not 内核语义（kind 交集、env 隔离、xor）（Minimum Rules #25）。

Exit Criteria:

- [x] 矩阵全格有断言（Minimum Rules #25）。
- [x] 无静默跳过（Minimum Rules #24）：未知匹配器/非法组合/stopBy 校验失败路径全部显式抛错并有断言。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 01 §3.4、design 04 §6、design 10 §2 增注（算子分界 + xdef 扩展形态）与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — successor 规则落地与对照收口（Fix + Proof）

Status: completed

Targets: `nop-lint/nop-lint-nop`（规则 + fixtures）、`ai-dev/design/nop-lint/02-rule-library.md`、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Fix | Proof`

- [x] `exception/silent-swallow` faithful 落地：catch 块七信号缺失判定以 `all` + `has` + `not` 组合表达（落 `nop-lint-nop` 主资源 `_vfs/nop/lint/rules/exception/`），+ 1 valid + ≥2 invalid + `.expect` fixtures（fail-closed 纪律同既有套件）。
- [x] `exception/no-log-getmessage` faithful 落地：`has: $E.getMessage()` 且 `not: has: throw $$$` 语义 + fixtures（同上）。
- [x] 行为对照记录：两规则 × 现有门禁语料对照表（`check-silent-swallow.mjs` 含 comment-bypass fixture 语义、`java-lint-getmessage-only.yml`），逐条记录命中一致性与 delta 裁定（先例 = plan 2137-3 cross-check 对照表）；mjs/ast-grep 门禁保持不动。
- [x] **端到端验证**（Minimum Rules #22）：两规则套件经真实管线全绿——`.rule.yml` VFS 加载 → `RuleDslParser`（含扩展校验）→ `LintEngine.lint`（kind 过滤 → matcher → xscript 可选 → suppression 管线尾）→ `TestNopRuleSuites` 断言。
- [x] 收口项：roadmap item 23 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 items 21/22/24/28 与 M4 未受扰动（M4 需 19–29 全 done，本 item 单项完成不翻转）；plan 2137-3/plan 06 的 deferred 承接关系记入日志。
- [x] Exit Criteria 汇总：`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0；owner-doc 增注与 live 一致；`ai-dev/logs/` 对应日期条目已更新。

> **执行期裁定增注（2026-09-22，Phase 3 执行中）**：首跑发现裸 `A.B` snippet 被 java grammar 消歧为类型引用（`scoped_type_identifier`），表达式位置的字段读信号无法用既有 `pattern` 形态表达（前序中断 session 的 scratch 调试遗留亦印证卡点）。修复 = 把 design 01 §1 既有 contextual pattern 能力（`SourcePatternCompiler.contextual`，Phase 1 已实现）接入关系匹配器 DSL 面：xdef 关系匹配器新增 `context`/`selector` 属性（与 `pattern` 互斥，parser fail-closed 三向校验），`pattern` 属性 `!string` → `string`（XOR 权威在 parser）。属 Phase 3 Targets 之外的 nop-lint-core 文件改动，为满足本 Phase"夹具绿"Exit Criteria 所必需的执行期 scope 延伸；语义无新增（编译能力既有），已回写 design 10 §2/02 §1/01 §3.3 与日志，parser +4、compile +2 测试钉死。附带清理：删除前序中断遗留的无断言 scratch 类 `ScratchDebug3Test.java`。

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：两条 successor 规则套件经 ServiceLoader 真实绑定全绿。
- [x] **接线验证**（Minimum Rules #23）：关系/组合匹配器在 `RuleSetRunner` 运行时被真实调用——由套件 invalid 夹具命中断言 + all/not 顶层规则的 kind 预过滤计数断言证明，非仅类型存在。
- [x] 对照表完整：两规则 × 现有门禁逐条记录，delta 有裁定。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0。
- [x] roadmap item 23 状态回写正确，周边 item 与 M4 未受扰动。
- [x] owner-doc：design 02 §1（规则清单状态）、design 01/04/10 增注与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Draft Review Record

- dispatch review #review-2026-09-21-142035-mission-driver-2026-09-22-0544-2-relational-rules-1-d6c42a58 to w0t1p0:1E2957B5-0546-42C5-BB14-484529CD402C
- 2026-09-22：iteration 1，共识 approved #review-2026-09-21-142035-mission-driver-2026-09-22-0544-2-relational-rules-1-d6c42a58

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：三个 Phase（内核关系匹配器 + StopBy + field 约束、组合算子 all/not + xdef/DSL 扩展、successor 规则落地与对照收口）执行项与 Exit Criteria 全数勾选（34/34 计数域 checklist），文件内无未勾选的 in-scope 项残留；无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up——未知匹配器/多重顶层/非法嵌套/`stopBy=rule` 缺 `stopByRule` 均 fail-closed 抛 `NopLintException` 且有测试断言（Minimum Rules #24）；行为契约达成——四关系算子 `inside`/`has`/`follows`/`precedes` × `stopBy` 三档（neighbor 仅直接邻接 / end 全遍历 / rule take-while 含 stop 节点本身）+ `field` 过滤 + `all` 合取（kind 交集预过滤 + scratchpad env + 全部命中才提交）+ `not` 否定（probe env 隔离、绑定不泄漏）按 design 04 §5/§6 落地；端到端验证（Minimum Rules #22）与接线验证（Minimum Rules #23）通过——两条 successor 规则（exception/silent-swallow、exception/no-log-getmessage）经 `.rule.yml` VFS 加载 → `RuleDslParser`（含扩展 XOR/stopBy 校验）→ `LintEngine.lint` → `TestNopRuleSuites` 断言全链路全绿，关系/组合匹配器经 `CompiledRule.compileNodeMatcher` 在运行时被真实消费（invalid 夹具命中断言，非仅类型存在）；owner docs 已同步（design 01 §3.3/§3.4/§4、design 02 §1、design 04 §5/§6、design 10 §2 增注与 live 一致）；roadmap item 23 状态回写正确（closure audit 通过置 `done`，2026-09-22 本 closure visit 翻转），周边 item 与 M4 未受扰动；Anti-Hollow Check 通过——`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出码 0，无空方法体/静默跳过/no-op 作为正常实现；`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（443 tests / 0 failures）且 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0（`TestNopRuleSuites` 12/12）；checkstyle 按 mission `commands.lint` 既有裁定记录（`checkstyle:check` 命中 nop-api-core 9226 处全仓遗留基线，走 `|| echo 'lint not configured'` 路径，与 1420-1/2137-*/0128-*/0544-1 一致）；独立子 agent / 独立审阅者 closure-audit 已完成并记录证据到 `## Closure`（#audit-20260922-1024-2026-09-22-0544-2-relational-rules-1-8841a5ad，2026-09-22）——本 section 不再保留可写 checkbox（计数域纪律，01-file-ledger §2.5），机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Verification

- pass test 20260922-1024 exit=0

（2026-09-22 closure audit 独立复核运行：`./mvnw -pl nop-lint/nop-lint-core -am test-compile -T 1C` BUILD SUCCESS；`./mvnw -pl nop-lint/nop-lint-core -am clean package -DskipTests -T 1C` BUILD SUCCESS；`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` BUILD SUCCESS **443 tests / 0 failures**（含 `TestRelationalMatcher`、`TestCompositeMatcher`、`TestRuleDslParser`、`TestCompositeRuleCompile`）；`./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` BUILD SUCCESS（`TestNopRuleSuites` 12/12 端到端套件全绿，含两条 successor 规则）；`./mvnw -pl nop-lint/nop-lint-core -am checkstyle:check` 走 mission 既有裁定 `|| echo 'lint not configured'` 路径（nop-api-core 9226 处全仓遗留基线，与 1420-1/2137-*/0128-*/0544-1 一致）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出码 0。）

## Closure

- dispatch audit #audit-20260922-1024-2026-09-22-0544-2-relational-rules-1-8841a5ad to opencode-2026-09-22-closure models={exec:build/zhipuai-coding-plan-glm-5.3-flash,aud:build/zhipuai-coding-plan-glm-5.3-flash}
- accepted #audit-20260922-1024-2026-09-22-0544-2-relational-rules-1-8841a5ad：审计结论 APPROVE-CLOSURE——item 23 关系规则三 Phase 全部落地且语义真实：CLOSURE_SCRIPT_CHECK 报 3 项（`ledger-structure-invalid` + `missing-pass:test` + `no-audit-receipt`），本 visit 全部消解——`## Closure Gates` 8 个 checkbox 位于 ledger 计数域外（01-file-ledger §2.5），按 §4.3 消解先例改为纯 prose 记录（同 1420-1/2137-1/2137-2/2137-3/0128-1/0544-1 处置），计数域回到 34/34 勾选、0 unchecked；`## Verification` 补 pass 行、`## Closure` 补 dispatch/accepted 收口对；`node tools/mission-driver/src/plan-check.mjs --strict` exit=0。语义复核：Phase 1 四算子（`RelationalMatcher` + `StopBy` neighbor/end/rule inclusive 语义 + `field` 过滤）与 Phase 2 `all`（kind 交集预过滤 + 全部命中才提交）/`not`（probe env 隔离）+ xdef 扩展（`lint-rule.xdef` rule 容器新增 relational/all/not + `context`/`selector` 执行期延伸）+ `stopBy=rule` 缺 `stopByRule` fail-closed（`RuleDslParser.java:314-319`，XOR 权威在 parser）经 `TestRelationalMatcher`/`TestCompositeMatcher`/`TestRuleDslParser`/`TestCompositeRuleCompile` 全格断言；Phase 3 两条 successor 规则（silent-swallow、no-log-getmessage）+ fixtures 在 `nop-lint-nop` 落地，`TestNopRuleSuites` 12/12 经真实管线（VFS 加载 → RuleDslParser → LintEngine → 套件断言）端到端全绿——接线验证由 invalid 夹具命中 + `CompiledRule.compileNodeMatcher` 运行时消费证明；本 visit 实跑验证套件全绿（test-compile / clean package -DskipTests / test 443 tests 0 failures / nop-lint-nop test 13 tests 0 failures 均 exit=0，checkstyle 走 mission 既有裁定路径，hollow scan 0 发现）；roadmap item 23 回写 `done`（closure-audit trigger 2026-09-22 触发），周边 item 与 M4 未受扰动；行为对照表与 owner-doc 增注（design 01/02/04/10）在案；无 in-scope live defect 被降级。
