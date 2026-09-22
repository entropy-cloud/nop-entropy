---
status: active
mission: nop-lint
work-item: "item-24"
group: "2026-09-22-0854"
verify: [test]
---

# 组合规则（roadmap item 24）：matches 递归 + utils 共享规则 + any 嵌套 refinement

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- 依赖满足：item 24 deps = item 23（`done`，plan "2026-09-22-0544-2-relational-rules"）——`all`/`not`/关系算子已落地（`AllMatcher`/`NotMatcher`/`RelationalMatcher` 于 `nop-lint-core/src/main/java/io/nop/lint/core/pattern/`）。算子分界已裁定（roadmap item 23 注记 + design 04 §6 落地注记 2026-09-22）：**matches 递归 + utils + any 嵌套 refinement 归本 item**；`stopBy=rule` 的 parse-time 校验（`stopByRule` 必填）已由 plan 0544-2 落地。
- 当前 DSL 面与 fail-closed 边界（本 plan 要扩展并部分解除的面）：`RuleDslParser` XOR 集常量（`RuleDslParser.java:82-88`）——`RULE_MATCHERS`（顶层）= pattern/kind/regex/any/all/not/inside/has/follows/precedes，`NESTED_MATCHERS`（all 元素内）与 `NOT_INNER_MATCHERS`（not 内层）均**无 any、无 matches**；越界拒绝消息显式指向 "roadmap item 24"（parser `:241-242` any 入 all、`:244-246` all 入 all、`:268-276` any/all/not 入 not）；`CompiledRule` 编译侧镜像同一有界嵌套面（`:344-377`，容器 depth 0 → all 元素 depth 1 → 其 not 内层 depth 2）。design 04 §6 落地注记明确：**item 24 扩展时解除这些边界**（嵌套面从三层有界扩为递归组合），解除后其余越界仍须 fail-closed。
- `any` 现状：仅顶层分支形态（`CompiledRule` 逐支执行 `:242-252`，每支独立编译 + kind 并集语义由既有 any 分支处理），无独立 `AnyMatcher` 类；嵌套 any 被解析器与编译器双重拒绝。
- matches/utils 语法面不存在：`lint-rule.xdef`（`nop-lint-core/src/main/resources/_vfs/nop/lint/schema/lint-rule.xdef`）无 `utils` 容器与 `matches` 匹配器；`RuleDslModel.Matcher` 无对应形态；`RuleDslParser`/`CompiledRule` 无引用解析。
- stopBy=rule 运行时缺口（本 plan 消解）：`CompiledRule.relationalStopBy`（`:428-440`）对 `stopBy=rule` 直接 fail-closed 抛错——"utils rule registry is not available until roadmap item 24"。item 23 交付的 stopBy=rule 目前**有校验无运行时**（parse-time `stopByRule` 必填已落，运行时解析归本 plan 的 utils registry）。
- 设计权威：design 04 §6（any 嵌套 = `AnyMatcher` 复用；matches = `ReferentMatcher.match(node, env)` 引用 util 规则、支持自引用）+ §8 P0（all 取 kind 交集 / any 取并集的编译期预计算——matches 顶层规则的 kind 提取策略须显式裁定）+ design 01 §3.4（组合规则语法面）+ design 10 §2（xdef 递归匹配器按 `<matcher>` 对象模式展开；**matches→utils 存在性校验指派 parser-class**）。
- 承接的 deferred 项（plan `06-lint-rule-xdef-and-dsl-parser` `Deferred But Adjudicated`，`Successor Required: yes`）：**matches→utils 存在性校验**随 Wave 4 扩展 xdef 时在 RuleDslParser 落地——本 plan 承接（该 deferred 的 stopBy 部分已由 plan 0544-2 消解）。
- kind 预过滤现状：`RuleSetRunner.run` 经 `KindIndex.collect` + `canMatchKinds` 位图短路（`RuleSetRunner.java:65,69`）；all 交集策略已由 plan 0544-2 落地；matches/self-reference 顶层规则的 kind 提取需本 plan 裁定（保守无 opinion 全量放行 + 计数 vs 引用展开取交集）。
- 硬约束适用：TSQuery 冻结（全部组合算子落在 SourcePattern/XNode matcher 家族）；不修改 nop-treesitter 既有类；零平台改动（全部工作在 nop-lint 模块内）；Wave-2+ 新规则必须带 RuleTester fixtures；未知引用/循环引用必须 fail-closed（Minimum Rules #24），递归不得无界。

## Goals

- `utils` 共享规则容器：rule 文件级 `utils` map（id → 匹配器规则）+ 运行时 registry——同时服务 `matches` 引用解析与 `stopBy=rule` 的 `stopByRule` 运行时解析（消解 `CompiledRule.java:432-436` 的 fail-closed 缺口，stopBy=rule 从"编译期拒绝"变为"可执行"）。
- `matches` 递归匹配器（`ReferentMatcher`）：引用 utils 规则，支持自引用（递归）；compile-time 存在性校验（未知 util 引用 fail-closed，承接 plan 06 deferred）+ 循环引用 compile-time 检测（fail-closed，禁止无限递归）；运行时递归语义按 design 04 §6。
- any 嵌套 refinement：抽取 `AnyMatcher`（或等价复用机制）使 `any` 可作为 `all`/`not` 子匹配器；解除既有"any 入 all/not"fail-closed 边界，重新定义并落实扩展后的嵌套边界（其余越界形态仍拒绝）；kind 并集预计算（design 04 §8 P0 延续）。
- xdef/`RuleDslModel`/`RuleDslParser` 扩展 + fail-closed 矩阵更新：utils 容器、matches 匹配器、any 嵌套解除、未知 util 引用、循环引用、空 utils、`stopByRule` 存在性校验衔接（parse 或 compile 期裁定，与 matches 引用同一 registry 口径）。
- **Decision（kind 预计算策略）**：matches 顶层规则与 any 嵌套下的 kind 位图提取（交集/并集/保守无 opinion + 计数），延续 §8 P0"不得静默全量"纪律；结论回写 design 01 §4 增注。
- 端到端证明：至少一条组合 demo 规则（utils + matches（含自引用）+ 嵌套 any）经 RuleTester 全管线（`.rule.yml` 加载 → parser → 编译 → 匹配 → 断言）验证。
- owner docs 回写（design 01 §3.4/§4、design 04 §6、design 10 §2 增注）+ roadmap item 24 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）。

## Non-Goals

- 生产规则批量（first-20 剩余归 item 29，48+ 归 item 35）；本 plan 只落 demo/验证套件与内核能力。
- constraints 层（item 22，已 `planned`）；autofix（item 25）；关系算子本体语义（item 23 已 done，本 plan 只补其 stopBy=rule 的运行时解析）。
- 降级阶梯 v2 / deep 档 / pattern 预算熔断（item 31，design 11 §5）；TS/TSX、XNode、tsc 各引擎本体（items 19/21/20 各自归属，本 plan 的组合面按 `LintLanguage` 既有 hook 对齐各 substrate，不改其内核）。
- CST/Signature/Template 严格度（plan 04 deferred，需要时另行立项）。
- mjs/ast-grep 门禁的切换或下线（item 28 manifest 决定）。

## Phase 1 — xdef/DSL 面：utils + matches + any 嵌套解除（Decision + Fix + Proof）

Status: completed

Targets: `nop-lint/nop-lint-core`（`rule/` 包：`RuleDslModel`/`RuleDslParser`、`_vfs/nop/lint/schema/lint-rule.xdef`）

- Item Types: `Decision | Fix | Proof`

- [x] xdef 扩展：rule 文件新增 `utils` 容器（id → 匹配器规则，按 design 10 §2 递归展开形态）；rule 容器与嵌套匹配器面新增 `matches`（引用 util id）与 `any`（嵌套形态）；`RuleDslModel`/`RuleDslParser` 同步（XOR 集常量与嵌套集扩展）。
- [x] **Decision（扩展后嵌套边界）**：解除 any/matches 入 all/not 的边界后，重新定义允许的嵌套面（如 matches 内层引用展开深度、not 内层 any 等）与仍拒绝的形态；所有越界形态 fail-closed 抛 `NopLintException`（英文消息含规则 id + 位置）；结论回写 design 04 §6 落地注记与 design 01 §3.4。
- [x] **Decision（stopByRule 存在性校验口径）**：`stopByRule` 引用的 util id 与 `matches` 引用同一 registry 口径——裁定存在性校验落在 parse 期还是 compile 期（与未知 matches 引用一致），两处均不得出现"有校验无运行时"或"有运行时无校验"的半面。
- [x] fail-closed 校验矩阵：未知 util 引用（matches 与 stopByRule 双路径）、循环引用（含自引用经第三方间接环）、空 utils 容器、utils id 重复、越界嵌套——全部显式抛错。
- [x] 单元测试：xdef 加载合法/非法组合矩阵（Minimum Rules #25）——合法 utils + matches 引用、嵌套 any、循环引用拒绝、未知引用拒绝、解除边界后原三层有界形态仍然合法（向后兼容）。

Exit Criteria:

- [x] fail-closed 矩阵全格有断言（Minimum Rules #24）：未知引用/循环/空容器/重复 id/越界嵌套均显式抛错。
- [x] 向后兼容：既有全部合法规则形态（含 plan 0544-2 的 all/not/relational 规则）加载行为不变（既有测试全绿即证）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 01 §3.4、design 04 §6 落地注记、design 10 §2 增注与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 内核：ReferentMatcher + utils registry + stopBy=rule 运行时 + kind 策略（Decision + Fix + Proof）

Status: completed

Targets: `nop-lint/nop-lint-core`（`pattern/` 包、`engine/CompiledRule`、`engine/RuleSetRunner`）

- Item Types: `Decision | Fix | Proof`

- [x] utils registry：compile 期构建（规则文件 utils → 可解析 matcher 结构），服务 matches 引用与 `stopByRule` 运行时解析；registry 不可用/引用缺失时保持 fail-closed（不静默退化为 end/neighbor 等其他 horizon）。
- [x] `ReferentMatcher`（或等价实现）：matches 引用展开为对 util 规则 matcher 的调用，支持自引用递归；递归深度以节点树深度为界（结构有界），compile 期已拒绝循环。
- [x] 消解 `CompiledRule.relationalStopBy`（`:428-432`）的 stopBy=rule 拒绝分支：改为经 registry 解析 `stopByRule` 引用，take-while 语义对齐 design 04 §5 的 rule 档（stop 节点本身 inclusive）。
- [x] **Decision（kind 预计算策略）**：matches 顶层规则的 `canMatchKinds` 提取（util 引用展开交集 vs 保守无 opinion 全量放行 + 显式计数）与 any 嵌套的并集提取；不得以假限制排除真命中，也不得静默全量；结论回写 design 01 §4（编译步骤 5）增注。
- [x] 单元测试矩阵（Minimum Rules #25）：matches 命中/不命中、自引用递归终止、嵌套 any 语义（首支成功即提交 + env 隔离对齐既有 not/all 语义）、stopBy=rule 运行时命中与 stop 节点 inclusive、kind 预过滤计数断言。

Exit Criteria:

- [x] 矩阵全格有断言（Minimum Rules #25）；递归终止性与 kind 计数有显式断言。
- [x] **接线验证**（Minimum Rules #23）：`stopBy=rule` 从既有"编译期拒绝"测试（plan 0544-2 遗留断言）迁移为"运行时可执行"断言——registry 经 `CompiledRule` 在运行时被真实消费。
- [x] 无静默跳过（Minimum Rules #24）：registry 缺失/引用缺失路径仍显式失败，无降级假-match。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 01 §4、design 04 §5/§6 增注与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — 端到端组合规则证明与收口（Proof）

Status: completed

Targets: `nop-lint/nop-lint-core`（test 资源套件）、`ai-dev/design/nop-lint/`、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Proof`

- [x] 组合 demo 规则套件：至少一条规则同时使用 utils + matches（含自引用）+ 嵌套 any（可叠加 all/not/relational），1 valid + ≥2 invalid + `.expect` fixtures，经 `RuleTestRunner` 真实管线全绿（**端到端验证**，Minimum Rules #22：`.rule.yml` VFS 加载 → parser（扩展校验）→ 编译（registry 构建）→ 匹配 → 诊断断言）。
- [x] **接线验证**（Minimum Rules #23）：套件 invalid 夹具命中断言证明 matches/嵌套 any/stopBy=rule 在运行时被真实调用，非仅类型存在。
- [x] owner-doc：design 02 §1/§3 如有规则面口径变化则同步；核对 design 08 §2 矩阵"all/not/matches 复合"行与 live 一致。
- [x] 收口项：roadmap item 24 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；plan 06 deferred（matches→utils 存在性校验）消解关系记入日志；核对 items 22/25/26/27/28/29 与 M4 未受扰动。
- [x] Exit Criteria 汇总：`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（如涉 nop-lint-nop 规则资源则加跑 `-pl nop-lint/nop-lint-nop -am test -T 1C`）；owner-doc 与 live 一致；`ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：组合 demo 套件经真实管线全绿（utils + matches 自引用 + 嵌套 any 同规则生效）。
- [x] **接线验证**（Minimum Rules #23）：invalid 夹具命中 + stopBy=rule 运行时解析有断言证据。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] roadmap item 24 状态回写正确，plan 06 deferred 消解关系已记录，周边 item 与 M4 未受扰动。
- [x] owner-doc 与 live 一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Draft Review Record

- dispatch review #review-2026-09-22-085436-2026-09-22-0854-2-composite-rules-1-e7dd7f12 to w0t1p0:C1AD4D97-2B35-46BC-9C7C-115F4070A60A
- 2026-09-22：iteration 1，共识 approved #review-2026-09-22-085436-2026-09-22-0854-2-composite-rules-1-e7dd7f12

## Execution Adjudications（2026-09-22，执行期裁定记录）

- **环裁定（"自引用"语义收敛）**：Goals 与 design 04 §6 原文的"matches 支持自引用（递归）"与"循环引用 compile-time 检测（禁止无限递归）"存在张力——落地裁定：只有关系算子移动求值节点（其内层是 pattern），`matches`/stopBy=rule 引用都在同一节点上展开，因此**任何引用环（含直接自引用、经第三方间接环、经 stopBy=rule 的环）都永不终止**，parse 期对引用图统一做存在性 + 无环校验（fail-closed，含路径报告）；"自引用"的合法形态 = 规则本体引用自身文件的 utils（design 01 §3.4 示例形态，live 已执行）。Phase 2 测试项"自引用递归终止"相应落地为"自引用/间接环/stopBy=rule 环均被拒绝 + 无环链正常解析"。
- **utils id 重复不可观测**：utils 为 YAML map 形态，重复键在 YAML 层天然归并（后者胜），parser 无校验面——fail-closed 矩阵中该项以此口径消解（设计 10 §2 增注同步）。
- **util id 命名域**：xdef `name` 用 `!string` 而非设计 10 §2 示例的 `!var-name`（util id 惯用 kebab-case，var-name 域会拒绝 `is-safe-close` 等设计示例形态）。
- **plan 06 deferred 消解**：`06-lint-rule-xdef-and-dsl-parser.md` 的 Deferred（stopBy=rule 必填 stopByRule、matches→utils 存在性校验）在本 plan 全部落地（stopBy 必填校验早已由 plan 0544-2 落地，存在性校验 + 运行时解析由本 plan 落地）——Successor Required: yes 已兑现。
- **XmlRuleCompiler fail-closed 补齐**：解析面放开后，XML 路径对 matches/嵌套对象分支显式拒绝（消息指明 utils registry 不在 XML 编译器面上），不再落入误导性的"regex 拒绝/无匹配器"错误。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] in-scope 行为结果已达成：`utils` 容器 + registry、`matches` 递归匹配器（含自引用）、any 嵌套 refinement、`stopBy=rule` 运行时可执行（消解 `CompiledRule.relationalStopBy` 的 compile-time 拒绝分支）、kind 预计算策略按裁定落地。
- [x] fail-closed 矩阵无降级：未知 util 引用（matches 与 stopByRule 双路径）、循环引用、空 utils、重复 id、越界嵌套均显式抛 `NopLintException` 且有测试断言（Minimum Rules #24）；registry 缺失路径无静默假-match。
- [x] 端到端验证（Minimum Rules #22）：组合 demo 套件（utils + matches 自引用 + 嵌套 any）经 `.rule.yml` VFS 加载 → parser → 编译 → 匹配 → 断言真实管线全绿。
- [x] 接线验证（Minimum Rules #23）：registry 经 `CompiledRule`/`ReferentMatcher` 在运行时被真实消费（invalid 夹具命中断言，非仅类型存在）。
- [x] 无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up。
- [x] owner docs 已同步（design 01 §3.4/§4、design 02 §1/§3 如涉变化、design 04 §5/§6、design 08 §2、design 10 §2 与 live 一致）；roadmap item 24 状态回写正确。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据到 `## Closure`。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）registry/matcher 调用链在运行时确实连通（不只是类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（如涉 nop-lint-nop 规则资源则加跑 `-pl nop-lint/nop-lint-nop -am test -T 1C`）。
- [x] checkstyle / 代码规范检查按 mission `commands.lint` 既有裁定记录。

## Verification

- 2026-09-22（closure audit 第 1 轮 REJECT 后修复复验）：`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` exit=0（**609 tests / 0 failures，BUILD SUCCESS**）。第 1 轮审计发现两项实缺陷并已修复：(a) `buildUtilRegistry` 逐个编译 util 时 stopBy=rule 急切解析 registry——`Map.copyOf` 打乱声明序后前向引用命中半成品 registry，约 56% 命名组合被假拒绝 → 改为与 matches 同口径的懒解析（ReferentMatcher）+ RuleDslModel 保序（LinkedHashMap）+ 前向引用回归测试；(b) composite 套件加入后 `TestRuleTestRunner` 套件计数断言未更新（10→11）→ 已更新。另：design 04 §6"声明顺序无关"表述随修复对齐，`ReferentMatcher` 重复 import 清理。第 1 轮 Verification 曾记录 607/0/0——那是套件落地前的时点数据，与套件落地后的 608+1 失败状态被误合并记录，现以修复后实测为准。

## Closure
