# 04 WI3 transform 语义裁定与 DSL 落地——"只改不报"的规则形态

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: ai-dev/backlog/nop-refactor-roadmap.md（M1 WI3）；ai-dev/design/nop-refactor/01-architecture-baseline.md §三/§四；ai-dev/design/nop-lint/01-pattern-dsl.md、03-execution-engine.md、09-suppression.md、11-performance-profiles.md（fix 生成资源语义）
> Related: 03-wi4-edit-plan-apply-entry.md（WI4 已 completed，消费路径衔接：transform 修复经同一应用入口落盘）；05-wi5-refactor-result-verification-payload.md（WI5，已裁定与本 plan 串行：WI3 先、WI5 后）；06（WI6 GraphQL 面消费 transform 规则集）
> Review: R1 对抗审查（2026-09-25，fresh session）：REVISE——1 Blocker（裁定 2 与 CompiledRule.compileTreeSitter 的 severity/message 非空编译校验直接冲突）+ 2 Major（CLI 层豁免/baseline 对 transform 编辑的门控未裁定；autoFixable 三消费面不闭合）+ 6 Minor；全部修订后执行（R2 复核见 Review Record）

## Purpose

执行 roadmap WI3（Item Type: Decision + Fix）：在 nop-lint 规则 DSL 落地 transform 规则形态——"只改不报"（匹配即改写，不产生面向用户的诊断报告），fix 载体与 severity 脱钩，为 P0 codemod 面（WI6/WI8）提供批量安全的规则载体。既有组合面 fail-closed 矩阵保持不放松。

## Current Baseline

- **诊断即 fix 载体**（live）：`RuleSetRunner` 每 match 产 `Diagnostic(ruleId, severity, message, range, fix)`；`FixApplier.candidates` 经 `Diagnostic::fix` 提取候选——报告面与修复面共用同一条诊断流。
- **fix 解析既有裁定**（RuleDslParser ~:178-200）：fix 需要 xscript 的规则被拒（"xscript 规则的诊断永不消费模板 fix"）；description/template 必填非空；suggest 缺省 false（suggestion-only 报告、永不应用）。
- **编译期归属校验**（live，R1 Blocker 指认）：`CompiledRule.compileTreeSitter` ~:227-231——`model.getSeverity() == null || model.getMessage() == null` 即抛 NopLintException（"diagnostics must be attributable"）。transform 规则的 severity 处理必须与该校验协调（见裁定 2）。XML 语言路径的 fix 拒绝在**编译期**（`CompiledRule.compile` ~:182-188），非 parser 期。
- **CLI 层 fix 门控契约**（design 09 §4/§5 + CheckRunner.filterForPass ~:502-521）：规则集豁免（ExemptionFilter）与 baseline 决策集命中的诊断连带其 fix 在任何 pass 都不产不应用——fix 的安全门在 CLI 层由 applier 闭包执行。
- **xdef**（lint-rule.xdef）：`<fix description="!string" template="!string" suggest="boolean=false"/>`；`<message xdef:mandatory="true">`；lint-rule 级 `severity` 枚举含 off；metadata 块自己的 `severity` 属性同样声明（~:142）；metadata.autoFixable 缺省 false（62 条生产规则全部 false）。xdef 注释明确：XOR/atLeastOne/约束矩阵由 RuleDslParser 作为 runtime authority 执行。
- **autoFixable 消费面三处**（live，R1 Major-2 指认）：parser `parseMetadata` ~:807-828（`Boolean.TRUE.equals`——无法区分"显式 false"与"未声明"；无 metadata 块返回 null）；GraphQL `NopLintBizModel` ~:154（`rule.getMetadata() != null && isAutoFixable()`——metadata 缺失即显示 false）；目录工具 `ai-dev/tools/gen-lint-rule-catalog.mjs` ~:58（直读 YAML `meta.autoFixable === true`，带 `--check` 漂移门禁）。
- **资源语义**（design 11 §5，RuleSetRunner）：fix 生成 fast profile 永不开启、ladder 可关闭（`fixOpen && !budget.fixClosed()`），关闭/降级计 `fixesDegraded`（其 javadoc 语义 = "诊断仍报告、仅 fix 载体剥离"）。
- **抑制尾**（design 09，LintEngine）：诊断流经 SuppressionFilter（注释抑制 + meta 诊断），后于规则环。Baseline 条目由 CheckRunner 从报告型 kept diagnostics 构建。
- **合并优先级**（live）：`Fixer.merge` 贪心按**列表序**取优先级（`Fix.order` 字段不被 merge 消费）——多来源候选必须先按 fixOrder 归并再喂 merge。
- **缓存面**（live）：`--cache` 与 `--fix` 互斥；命中文件的 engine stats 全零是既有行为。
- **62 条生产规则**全部为报告型（无 transform 形态）；本 plan 为纯增量 DSL 面。
- WI1 门已过（plan 11 completed，roadmap WI1 已勾选）；WI4 已 completed（EditPlanApplier 入口在案）。

## Goals

- **DSL**：新顶层 `transform:` 声明（description + template，形态同 fix），一条规则 fix/transform 互斥；transform+xscript 拒绝；XML 语言 + transform 拒绝——拒绝面与 fix **同层同型**（XML 拒绝在编译期，随 fix 先例）。
- **语义**：transform 匹配**不产报告型 Diagnostic**——独立载荷通道分离；报告面（CLI reporter/GraphQL/LSP publish）、抑制尾、baseline 全部零感知；修复流（autofix/经 WI4 入口的消费方）照常消费其编辑，且**CLI 层规则集豁免对 transform 编辑同构门控**（见裁定 4）。
- **计数**：transform 匹配与 transform 降级各有独立 LintStats 计数（不进 `diagnostics`/`fixesDegraded` 计数）；可观测载体钉在 LintStats accessor（CLI 汇总渲染不动——归 WI7，报告字节面零风险）。
- **元数据语义**：transform 规则的 effective autoFixable 恒为 true（三消费面一致闭合，见裁定 5）。
- design 01/03 增注 + design 11 §5 覆盖声明。

## Non-Goals

- 不改既有 62 条规则与 fix 形态的任何行为（零行为红线：报告字节、诊断流、缓存指纹不变——不变量的可核对形态见 Phase 2 Exit Criteria）。
- 不做 RefactorResult 载荷（WI5）、GraphQL 面（WI6）、CLI 新形态（WI7）。
- 不扩展 RuleTester 断言面（transform 规则无可断言诊断；其行为验证经 WI8 的 GraphQL/CLI 闭环 fixtures 承载）。
- 不动 TemplateFix 渲染机制、捕获校验、约束面（transform 复用，零改动）。

## 语义裁定记录（Decision——执行按此落地；对抗审查若推翻须回写本节）

1. **通道分离裁定**：transform 编辑不经 Diagnostic 流——`LintResult` 增独立 transform 编辑列表通道（既有双参构造保持可用，既有调用点零改动），`RuleSetRunner` 在规则环内分流，抑制尾只处理报告型列表。理由：报告/抑制/baseline 三个消费面**结构性**零感知（无需逐点记标志过滤，漏一处即行为破坏），行为不变证明面最小；对比方案"Diagnostic 加标志位"要求每个消费点记得检查，违反 fail-closed 结构纪律。
2. **severity 脱钩裁定（R1 Blocker 修订后）**：transform 规则**拒绝声明 severity——顶层与 metadata 块两处都拒绝**（fail-closed：歧义声明不存在）。配套编译期协调：`CompiledRule.compileTreeSitter` 的 severity/message 非空归属校验改为 **transform 感知放行**——规则携带 transform 时 severity/message 允许为 null（校验理由"诊断须可归属"对 transform 不成立：其永不产诊断），非 transform 规则的校验逐字保持。`message` 保持 xdef mandatory 不动（schema 稳定优先），语义钉死为"规则自描述，不出现在任何诊断/报告面"——xdef 注释与 design 01 增注记录。拒绝在 parser 期完成 → 合法 transform 规则到编译期时 severity/message 均可为 null（message 由声明保证存在）。
3. **资源门与降级计数裁定（R1 Minor-1 修订后）**：transform 生成与 fix 生成**同一资源门**（fast 不开、ladder 可闭）；降级用**独立计数 `transformDegraded`**（一个 int，语义 = "transform 编辑因资源门关闭而整体丢失、无任何报告"）——不复用 `fixesDegraded`（其既有语义 = "诊断仍报告、仅载体剥离"，两事件不可混同）；LintStats javadoc 显式改写两计数语义，design 03 增注声明对 design 11 §5 的 transform 侧覆盖。
4. **门控与抑制交互裁定（R1 Major-1 修订后）**：
   - **规则集豁免同构门控**：CLI 层 ExemptionFilter 对 transform 编辑与 fix 面同构——被豁免规则的 transform 编辑不应用（applier 闭包对 transform 通道应用同一 rule-id/path 谓词）；杜绝"豁免了规则、文件仍被改写"的分歧。
   - **baseline 无交互（结构性）**：baseline 决策集只含"已报告 findings"，transform 规则永不产报告 → baseline 对 transform 编辑天然无约束，无需过滤（非裁定的 opt-out，是构造性无交集）。
   - **内联抑制无局部 opt-out**：`nop-lint-disable` 注释只作用于诊断流，v1 不门控 transform 编辑（批量机械变更的 opt-out = 配置级规则豁免；逐处 disable 是人类编辑形态）；连带语义显式接受：仅覆盖 transform 匹配的 disable 指令会以 `unused-disable-directive` meta 诊断浮出（既有 scanner 行为，不为其改面）——design 03 增注记录该交互并声明对 design 09 §4/§5 的 transform 侧边界。
5. **autoFixable 元数据裁定（R1 Major-2 修订后）**：transform 规则的 effective autoFixable 恒为 true，三消费面闭合：
   - **解析区分**（"显式 false" vs "未声明"）：parser 对 transform 规则读 metadata 原始键——键存在且值为 false → 拒绝（矛盾声明 fail-closed）；键缺省或 true → 生效 true。
   - **载体合成**：transform 规则无 metadata 块时，parser **合成 Metadata（category="transform", autoFixable=true, version 缺省）**——GraphQL `Lint__listRules` 零改动即显示 autoFixable=true；有 metadata 块时校验后保留（autoFixable 强制 true 语义、metadata severity 拒绝、category 保留声明值）。
   - **目录工具同步（本 plan in-scope 最小改动）**：`gen-lint-rule-catalog.mjs` 对声明 `transform:` 的规则派生 autoFixable=true（YAML 直读面与 parser 语义对齐），`--check` 门禁保持绿——WI8 落地 transform 规则时目录不漂移。
6. **XML+transform 拒绝层裁定（R1 Minor-2 修订后）**：与 fix 先例**同层**——编译期拒绝（`CompiledRule.compile` 的 XML+fix 检查处泛化为 fix/transform 两种模板载体），非 parser 期；parser 期矩阵只含语言无关项。
7. **合并优先级序（R1 Minor-5 补充）**：诊断 fix 列表与 transform 编辑列表汇入单一合并点（FixApplier 候选提取）前，必须按 fixOrder **归并排序**（Fixer.merge 按列表序取优先级、不读 Fix.order 字段）——声明序优先在全引擎范围保持全序唯一（同一规则 fix/transform 互斥，无并列歧义）。

## Scope

### In Scope

- `nop-lint/nop-lint-core/src/main/resources/_vfs/nop/lint/schema/lint-rule.xdef`：transform 声明。
- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/rule/`：RuleDslModel/RuleDslParser 扩展（transform 解析 + parser 期互斥矩阵 + severity/autoFixable/suggest 矛盾拒绝 + metadata 合成）。
- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/`：CompiledRule（transform 感知归属校验放行 + XML+transform 编译期拒绝 + transform 模板携带）、RuleSetRunner（分流 + 计数）、LintResult（通道扩展，兼容构造）、LintStats（两个新计数器 + javadoc 语义改写）。
- 修复流衔接：FixApplier 候选提取归并合并 transform 通道（一处）；CheckRunner 的 applier 闭包透传 + 豁免谓词同构门控。
- `ai-dev/tools/gen-lint-rule-catalog.mjs`：transform 规则 autoFixable 派生（一行语义 + `--check` 保持绿）。
- 测试：parser 矩阵 + 引擎分流 + autofix 端到端（transform 规则经 CLI autofix 落盘）+ 豁免门控。
- `ai-dev/design/nop-lint/01-pattern-dsl.md`、`03-execution-engine.md` 增注（03 增注含 design 11 §5 覆盖声明与 design 09 transform 侧边界声明）。

### Out Of Scope

- nop-refactor-* 模块（WI9+）；GraphQL/CLI 消费面（WI6/WI7）；规则内容（WI8）；ConsoleReporter/RunSummary 渲染面（字节面不动）。
- nop-lint-java/js/nop/maven-plugin/graphql 模块行为。

## Execution Plan

### Phase 1 - DSL 与解析矩阵（Fix + Decision 落档）

Status: completed
Targets: lint-rule.xdef、rule/RuleDslModel.java、rule/RuleDslParser.java

- Item Types: `Fix`（解析矩阵）+ `Decision`（裁定落档）

- [x] lint-rule.xdef：`<transform description="!string" template="!string"/>`（形态同 fix 去 suggest）；注释记录裁定 2 的 message 语义与裁定 5 的 autoFixable 合成语义
- [x] RuleDslParser：transform 解析（description/template 必填非空，同 fix 标准）；**parser 期互斥矩阵** fail-closed——transform+fix、transform+xscript、transform+suggest、transform+顶层 severity、transform+metadata severity、transform+显式 autoFixable=false（原始键存在且 false）全部拒绝，消息风格与既有拒绝面一致；transform 规则无 metadata 块时合成 Metadata（category="transform", autoFixable=true）（注：transform+suggest 由 transform+fix 拒绝结构性覆盖——suggest 是 fix 的子键）
- [x] RuleDslModel：携带 transform 声明（description/template）与 effective autoFixable 语义（Transform 嵌套类 + getTransform + 16 参主构造，短构造保持兼容）
- [x] 焦点测试：TestTransformRuleParser 10/10（矩阵逐项拒绝 + 合法加载 + metadata 合成 + plainFixRuleUnaffected 既有面零扰动）
- [x] design 01/03 增注落档（裁定 1-7 全部落正文；03 增注含 design 11 §5 覆盖声明、design 09 transform 侧边界声明、缓存面增注）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] parser 矩阵测试全绿（6 项拒绝 + 合法路径 + metadata 合成 + 既有矩阵零回归）
- [x] 裁定 1-7 与实现一致（parser 消息文本可逐条对照）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 引擎分流与修复流衔接（Fix）

Status: completed
Targets: engine/CompiledRule.java、engine/RuleSetRunner.java、engine/LintResult.java、engine/LintStats.java、fix/FixApplier.java、cli/CheckRunner.java、ai-dev/tools/gen-lint-rule-catalog.mjs

- Item Types: `Fix`

- [x] CompiledRule：transform 感知归属校验放行（携带 transform 的规则 severity/message 允许 null——裁定 2；非 transform 校验逐字保持）+ XML+transform 编译期拒绝（XML+fix 检查处泛化——裁定 6）+ transform 模板携带（templateFix 字段复用同一 TemplateFix 渲染机制 + transformCarrier 标志/accessor）
- [x] RuleSetRunner：transform 规则的 match 不产 Diagnostic——进独立编辑列表（fixOrder 单计数器跨 fix/transform 分配，分流后按 fixOrder 归并——裁定 7）；transform 匹配计数 + transformDegraded 计数（同资源门，LintStats javadoc 语义改写——裁定 3）；新 API 形态：保留 run(...) 返回 List<Diagnostic>（既有测试调用点零改动），新增 runWithRewrites(...) 返回 RunResult(diagnostics, transformFixes)——LintEngine 两调用点迁移
- [x] LintResult：新 transform 编辑通道（record 三组件 + 双参兼容构造，既有调用点零改动）；抑制尾只处理报告型列表（裁定 1/4）
- [x] FixApplier 候选提取：诊断 fix 与 transform 编辑按 fixOrder 归并后喂 merge（裁定 7）；CheckRunner applier 闭包与 report lint 路径透传，**豁免谓词对 transform 通道同构门控**（裁定 4）；report 型统计不含 transform
- [x] gen-lint-rule-catalog.mjs transform 派生 + `--check` 门禁复绿（裁定 5——62 行 in sync）
- [x] 焦点测试：TestTransformEngine 4/4（改写通道/FAST 降级口径/跨通道归并优先级/非重叠双应用）；TestTransformExemptions 3/3（豁免 e2e：被豁免 transform 规则不改文件、非豁免文件双改写、报告面零诊断+计数正确）；TestBudgetDegradeLadder 扩展 transform 规则（ladder 关闭→transformDegraded=1、无诊断无改写）；既有全量零回归
- [x] design 01/03 增注按 landed 实现回填互洽核对（含 11 §5 覆盖声明与 09 边界声明的最终文本）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 新测试全绿且可证伪（transform 匹配在报告面不可见、在修复面生效、被豁免时不生效三向断言）
- [x] **端到端验证**：transform 规则 YAML（cli-rules-transform 测试资源）→ CheckRunner APPLY → 文件内容落盘正确（TestTransformExemptions 双例），全链路一测贯通
- [x] **接线验证**：transform 编辑确实流经 WI4 应用入口的同一机械核（FixApplier 候选归并后走 merge→apply→guard→write 单份实现——TestTransformEngine 归并/双应用两例为运行时证据）
- [x] 行为不变（可核对不变量形态——R1 Minor-4 修订）：诊断流内容、CLI 渲染字节、既有 getter 返回值对无 transform 规则的运行逐项不变（LintResult/LintStats 新组件的 toString/equals 面显式豁免）；6 模块测试绿（core 816/0 全量 + 矩阵全 SUCCESS；两个 JMH smoke 因并发会话 benchmark 持锁临时排除后已补跑全绿：TreeSitterBenchmarkSmokeTest 2/0、BenchmarkSmokeTest 1/0——矩阵证据完整）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] transform 规则形态落地：只改不报（报告面/抑制尾/baseline 结构性零感知）、计数独立可观测（LintStats 载体，CLI 渲染面不动）
- [x] fail-closed 矩阵不放松：parser 期 6 项拒绝 + 编译期 XML+transform 拒绝逐项有测试；既有 fix 编译校验对非 transform 规则逐字保持
- [x] 零行为红线：62 条既有规则行为不变（可核对不变量形态）；豁免门控同构（被豁免规则的 transform 编辑不应用）
- [x] Anti-Hollow：transform 编辑流经 WI4 入口机械核（无第二应用路径）；无空方法体/静默跳过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0
- [x] 代码规范：`check-import-order.mjs` 全仓 exit 1 系 2102 处既有存量（均在本 plan 范围外）；**本 plan 变更文件零违规（audit 实测 grep 0 命中）**；gen-lint-rule-catalog `--check` 门禁绿（62 行 in sync）
- [x] vision 原则 1–9 回扣核对（closure audit 执行）：原则 6（fail-closed 矩阵）、原则 9（预算——复用 TemplateFix/Fixer 零重造）为本 WI 重点（audit Step 6 逐项 PASS）
- [x] design 01/03 增注（含 11 §5 覆盖声明、09 边界声明）与 live 实现一致
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-java,nop-lint/nop-lint-js,nop-lint/nop-lint-nop,nop-lint/nop-lint-maven-plugin,nop-lint/nop-lint-graphql -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/04-wi3-transform-dsl.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- （若有执行中发现的优化项，关闭时填写；in-scope live defect 不得入列）

## Closure

Status Note: WI3 收口——transform"只改不报"规则形态全链路落地：DSL（xdef + parser 矩阵 6 项 fail-closed 拒绝 + metadata 合成三消费面闭合）、引擎（LintResult 独立改写通道 + transformMatches/transformDegraded 独立计数 + transform 感知编译放行 + XML 编译期拒绝）、应用面（FixApplier fixOrder 归并 + CheckRunner 豁免同构门控）、目录工具同步；既有行为可核对不变量零漂移（core 816/0 全量 + 6 模块矩阵全 SUCCESS + JMH smoke 补跑全绿）。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit（fresh session subagent，与实现会话不同 task）
- Audit Session: agent_0c4474c1-d11a-4517-8163-00d874246540
- Evidence:
  - Step 1-7 全 PASS：裁定 1-7 逐条对照 live（文件:行号证据齐备——parser 矩阵/合成 metadata/编译放行条件精确/XML 同层拒绝/RunResult 双 API/通道计数语义/归并排序/豁免谓词同构/目录派生）
  - 测试物证：TestTransformRuleParser 10/0、TestTransformExemptions 3/0、TestTransformEngine 4/0、TestBudgetDegradeLadder 9/0（扩展例）、core 全量 816/0/0；报告 mtime 晚于源码 mtime；豁免 e2e 资源文件与断言逐条吻合
  - 行为不变：改动集零越界生产文件；TestBudgetDegradeLadder 为唯一被改既有测试（扩展性改动）；非 transform 分支/无 rewrites 快路径/非 transform 编译校验 diff 逐行保持
  - 门禁亲跑：hollow-scan exit 0、catalog --check exit 0（62 rows in sync）、doc-links 0 errors、check-plan-checklist exit 0（active 态 warnings-only）；import-order 全仓 exit 1 系既有存量、本 plan 变更文件零违规（实测）
  - JMH smoke 补跑：审计窗口内锁释放后完成且全绿（TreeSitterBenchmarkSmokeTest 2/0、BenchmarkSmokeTest 1/0 落盘）——与 WI3 改动无关性经 git/mtime 确认，矩阵证据完整
  - vision 原则 1–9：原则 6/9 重点逐项 PASS，其余未触及
  - Findings：3 Minor（import-order 范围口径措辞——已照办回填；Review Record 标题重复——已删；Phase 2 Targets 漏列 LintEngine——枚举瑕疵，checklist 已明文授权）+ 1 观察（autoFixable 非 TRUE 值一律拒绝为 fail-closed 超集，符合裁定 5 意图）
  - AUDIT VERDICT: PASS（允许回填剩余 3 gates + Closure 段 + completed）

Follow-up:

- no remaining plan-owned work（transform 规则在 GraphQL listRules 的 severity=null 渲染裁定归 WI6 plan 携带）

## Review Record

- **R1（2026-09-25，fresh session）：REVISE**——1 Blocker（裁定 2 的 severity 拒绝使合法 transform 规则撞 CompiledRule.compileTreeSitter 非空归属校验，必须裁定放行方式 + metadata.severity 组合面）+ 2 Major（CLI 层豁免/baseline 门控与内联抑制 opt-out 未裁定；autoFixable 在 parser/GraphQL/catalog 三消费面不闭合且解析无法区分显式 false）+ 6 Minor（fixesDegraded 语义混同与 design 11 归属；XML 拒绝层位与 fix 先例不同层；计数载体与 CLI 渲染面边界；"逐字等价"不可字面核对；merge 列表序归并；缓存面计数空洞）。全部修订落正文：裁定 2 改 transform 感知编译放行 + metadata severity 拒绝；裁定 4 增 CLI 豁免同构门控/baseline 构造性无交集/内联抑制无 opt-out + unused-directive 接受；裁定 5 重写（解析区分 + metadata 合成 + catalog 工具同步 in-scope）；裁定 6 XML 拒绝归编译层；裁定 3 改独立 transformDegraded 计数 + design 11 覆盖声明；裁定 7 补归并序；Minor-3/4/6 落 Phase 2 与 Closure Gates 措辞。
- **R2（2026-09-25，fresh session）：APPROVE**——R1 九项逐条 FIXED-VERIFIED（含四项专项对抗：编译放行条件足够窄、豁免落点与 transform 通道数据流自洽、合成 Metadata 消费面仅 GraphQL+测试、LintStats 为 immutable class+Builder 无 equals/hashCode 覆写故计数器增量安全）；2 条非阻塞观察（transform 规则在 GraphQL listRules 显示 severity=null——归 WI6 携带渲染裁定；Review Record 一处出处描述不精确）。进入执行。
