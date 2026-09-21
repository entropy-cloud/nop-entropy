---
status: active
mission: nop-lint
work-item: "item-9"
group: "2026-09-21-1420"
verify: [test]
---

# LintEngine 最小引擎：kind 位过滤 + fast/standard 档位 v1 + skippedByProfile 统计（roadmap item 9）

## Current Baseline

以下事实均已对照 live repo（2026-09-21）核实：

- 规则加载层已就绪（plan 06，item 8 done）：`RuleDslParser.loadRuleModel(resourcePath)` 把 `.rule.yml` 加载为强类型 `RuleDslModel`（字段：id/language/severity/message/matcher/xscript/xscriptTimeoutMs/requires/options/settings/metadata/files；matcher 为 pattern/kind/regex 单一形态或 `any` 分支列表，分支内 pattern+kind 可合取）。xdef 位于 `nop-lint/nop-lint-core/src/main/resources/_vfs/nop/lint/schema/lint-rule.xdef`，注册文件为 `_vfs/nop/core/registry/lint.register-model.xml`。
- 匹配内核已就绪（plans 02–04，items 1–7 done，M1）：`SourcePatternCompiler.compile` → `SourcePattern`（`matchIn(LintNode)`、`mayMatchKind(int)`、`possibleKindIds()`）→ `Match`；`LintNode` 门面提供 `kind()/kindId()/text()/childByField()/range()` 等。
- 语言绑定：`LintLanguage` SPI 与 `TreeSitterLanguageAdapter` 在 nop-lint-core；`JavaLanguage`（nop-lint-java，`JavaLanguage.get()` 单例，`id()` 返回小写 `"java"`）已存在，但**没有** `META-INF/services` ServiceLoader 注册文件——语言发现机制是 plan 02 收口时显式移交本 plan（item 8/9 规则引擎 plan）承接的 deferred 项。
- **尚不存在**的类：`LintEngine`、`Diagnostic`、`LintStats`、CompiledRule（规则编译产物）、语言注册表。`io.nop.lint.core` 下现有包为 node/lang/pattern/rule。
- 测试基建：core 测试可用 `TreeSitterLanguageAdapter` 直接构造 java 语言（`src/test/java/io/nop/lint/core/bench/BenchLanguage.java` 模式）；core 测试资源已有规则夹具 `_vfs/test/lint/rules/*.rule.yml`（含 `valid-any`（language: `Java`）、`valid-simple`、`valid-full`）。
- **已知对齐陷阱**：规则夹具中 `language: Java`（首字母大写），而 `JavaLanguage.id()` 为小写 `"java"`——引擎的语言解析必须显式定义大小写归一语义，否则规则静默失配。
- 设计口径（design 11 为性能唯一权威）：§8 Phase 1 只交付 fast + standard 两档（L2 未到，standard 暂等于 fast + 约束空集）；§1 解法 2 与 §3 规定管线第 1 步为 kind 位过滤（design 03 §1.1 定义单文件流程，管线顺序以 design 11 为准：kind 过滤 → pattern 匹配 → 约束[P2] → xscript → 抑制 → Diagnostic）；§1 解法 1 规定 `requires` 不满足档位能力时规则计入 `skippedByProfile`（绝不静默丢失）；硬约束（roadmap）禁止 profile 降级静默跳过、禁止拿 L1 冒充 L2。design 03 §2.3 给出 Diagnostic/LintStats 的对外形态。
- xscript 引擎属 item 14（Wave 3）；本 plan 落地时平台内**没有任何带 xscript 的规则**（规则夹具均为 pattern/kind 形态），但 DSL 已允许该字段，引擎必须对其有显式（非静默）处置。
- Wave 2 剩余：9（本 plan）、10、11、12；item 9 是 items 10/14/15/17/18 的共同依赖。

## Goals

- nop-lint-core 新增 `LintEngine` v1：输入规则模型集合 + 语言 + 源码/`LintTree`，输出诊断列表与统计（design 03 §1.1 单文件管线在 Phase 1 能力范围内的落地）。
- kind 位过滤真实生效：规则编译期提取目标 kind 集合（any→并集），运行期与文件的 kind 出现集合求交，不相交的规则零匹配调用且计数可见。
- `LintProfile` fast/standard v1 + `requires` 能力检查：能力不满足的规则计入 `skippedByProfile`（含规则 id），不产出诊断、不静默。
- 新增值模型 `Diagnostic`（ruleId/severity/message/range）与 `LintStats`，与 design 03 §2.3 形态对齐。
- 语言发现机制落地（承接 plan 02 deferred 项）：`LintLanguage` 经 ServiceLoader 注册发现，规则 `language` 字段解析到绑定实例；未知语言 id 显式报错。
- 对 DSL 中 v1 不执行的形态（xscript 字段、regex 匹配器）作出 fail-closed 裁定并有测试，杜绝静默跳过。
- 完成后 roadmap item 9 具备翻 `done` 条件（M2 的组成项之一）。

## Non-Goals

- xscript 执行与 deadline executor（items 14/15；本 plan 对带 xscript 的规则 fail-closed）。
- 约束/关系/复合匹配器、regex 匹配器执行（items 22–24；本 plan 对 regex 形态 fail-closed）。
- 抑制判定（item 17）、autofix（item 25）、CLI/Maven/GraphQL 出口（items 18/37/38）。
- deep 档、降级阶梯、预算熔断、xscript 时间片（Wave 5 items 30–31 与 design 11 §5）。
- 多文件驱动、`files` include/exclude 过滤、并行与增量解析（item 16 与 design 03 §1.2；单文件入口不涉及文件路径，`files` 字段由首个多文件消费 plan 承接）。
- CompiledRule 序列化缓存与规则集内容 hash 缓存（design 11 §4 的 CI 优化，有性能证据后按需立项）。
- 消息模板的捕获插值（如 `{{VAR}}`）：v1 消息按 `RuleDslModel.message` 字面输出；是否引入插值由 item 11 规则落地时裁定。
- 性能数字：不新设 JMH 口径（plan 06 follow-up 提出的"规则加载口径基准评估"在本 plan 内只做裁定记录，不实施）。

## Phase 1 — 值模型与语言发现（Diagnostic / LintStats / LanguageRegistry）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/{engine,lang}`、`nop-lint/nop-lint-java/src/main/resources/META-INF/services`

- Item Types: `Fix | Decision | Proof`

- [x] 新增值模型：`Diagnostic`（ruleId、severity、message、range（复用 `io.nop.lint.core.node.SourceRange`）；不可变）与 `LintStats`（至少含：rulesLoaded、rulesExecuted、rulesSkippedByProfile、rulesKindFiltered、diagnostics；不可变或构建器形态）。
- [x] 新增 `LanguageRegistry`：支持 (a) ServiceLoader 发现 `io.nop.lint.core.lang.LintLanguage` 实现，(b) 测试/手动 `register(LintLanguage)`，(c) 按语言 id 解析；**Decision**：id 归一语义（建议大小写不敏感，理由：现存夹具用 `Java`、绑定 id 为 `java`），解析失败显式抛 `NopLintException`（英文消息含原始 language 值）。
- [x] nop-lint-java 增加 `META-INF/services/io.nop.lint.core.lang.LintLanguage` 注册文件指向 `io.nop.lint.java.JavaLanguage`。
- [x] 单元测试：值模型不可变性与字段完整性；registry 手动注册/ServiceLoader 发现/id 归一/未知 id 抛错（core 测试用测试专用 services 资源或手动注册覆盖）。
- [x] 跨模块发现测试（nop-lint-java 模块内）：`ServiceLoader.load(LintLanguage)` 能发现 `JavaLanguage`，其 `id()` 与注册一致。

Exit Criteria:

- [x] `Diagnostic`/`LintStats`/`LanguageRegistry` 存在且有焦点测试；全部新增公共行为均有断言（含 id 归一与未知 id 抛错路径，无静默跳过——Minimum Rules #24/#25）。
- [x] **接线验证**：nop-lint-java 测试证明 ServiceLoader 真实发现 `JavaLanguage`（而非仅注册文件存在）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] `./mvnw -pl nop-lint/nop-lint-java -am test -T 1C` 退出码 0（注册文件在该模块，使命 verify 键 `-pl nop-lint/nop-lint-core` 不覆盖下游模块，须显式验证并记录输出）。
- [x] No owner-doc update required（语言发现机制是 plan 02 显式移交本 plan 的 deferred 项，现有 design 文档未定义其契约（`ai-dev/design/nop-lint/` 无 ServiceLoader/LintLanguage 表述），本次落地即首次定义；落地时若引入新的使用契约，在 `ai-dev/logs/` 记录裁定，必要时回填 design 03 §1.1 的语言绑定说明）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 规则编译与 kind 位过滤（CompiledRule + 形态执行矩阵）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine`

- Item Types: `Fix | Decision | Proof`

- [x] 新增 `CompiledRule`：由 `RuleDslModel` 编译产生，携带 ruleId/severity/message、目标 kind 集合（pattern → `SourcePattern.possibleKindIds()`；kind 匹配器 → 该 kind 的 kindId；any → 分支集合并集）、可执行的匹配体。
- [x] **Decision（形态执行矩阵，v1）**：pattern 执行；kind 执行（遍历树按 kindId 命中产出诊断）；any 分支执行（分支内 pattern+kind 合取；分支集合任一命中即产出）；**regex 匹配器与含 xscript 字段的规则 → 编译期 fail-closed**，抛 `NopLintException`（英文消息含规则 id 与原因；regex 语义归 item 22 约束求值器、xscript 执行归 item 14）。矩阵每一格必须有测试（执行格断言行为、拒绝格断言抛错消息）。
- [x] 文件级 kind 出现集合：一次遍历收集文件内出现过的 kindId 集合，供规则级 O(1) 相交判断；不相交规则跳过匹配且 `rulesKindFiltered` 计数 +1。
- [x] 单元测试：矩阵全格覆盖；kind 集合并集正确性（用 `valid-any.rule.yml` 夹具编译后断言目标 kind 集合）；kind 过滤跳过路径的计数正确性。

Exit Criteria:

- [x] `CompiledRule` 与形态矩阵落地，每个矩阵格有对应测试断言（Minimum Rules #25）。
- [x] **无静默跳过**：regex/xscript 规则在编译期显式失败（消息含规则 id），不存在空实现或忽略分支（Minimum Rules #24）。
- [x] **接线验证**：kind 位过滤在运行时真实短路——构造目标 kind 不在文件中的规则，断言 `rulesKindFiltered` 递增且诊断为空（证明过滤发生在匹配调用之前，而非匹配后过滤）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] No owner-doc update required（若形态矩阵与 design 10/04 的 DSL 语义描述存在偏差，则修订对应 design 行并在日志记录）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — LintEngine 档位执行 v1 + skippedByProfile（端到端收口）

Status: planned
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [x] 新增 `LintProfile`（fast/standard）与 v1 能力集：两档能力均为 `{L1}`（design 11 §8：L2 未到，standard 暂等于 fast + 约束空集）；能力集为枚举/常量，供 items 14/15/17/25/26 后续扩挂。
- [x] 新增 `LintEngine`：构造注入 `LanguageRegistry` 与 `LintProfile`；入口接收 `RuleDslModel` 集合 + `LintLanguage`（或语言 id）+ 源码/`LintTree`；按 design 03 §1.1 顺序执行 v1 子集：语言解析 → 逐规则 requires 检查 → 编译（Phase 2 矩阵）→ kind 过滤 → 匹配 → `Diagnostic`（range 取 match 节点 `SourceRange`，severity/message 按模型字段输出）→ `LintStats` 汇总。
- [x] `requires` 不满足能力集的规则：不编译不执行，`rulesSkippedByProfile` 计数并记录规则 id（**Decision**：id 列表进入 LintStats 或日志，保证可观测；绝不静默）。禁止用 L1 结果冒充更高层结果（roadmap 硬约束）。
- [x] 端到端测试：`.rule.yml` 夹具文本 → `RuleDslParser.loadRuleModel` → `LintEngine.lint` → 断言诊断的 ruleId/severity/message/range（构造一段确定命中的 Java 源码与确定不命中的源码各一）。
- [x] 档位测试：同一规则集在 fast 与 standard 下 v1 行为一致（能力集相同）；`requires: [L2]` 规则在两档下均计入 `skippedByProfile` 且不产出诊断。
- [x] **Follow-up（裁定，承接 plan 06 Non-Blocking Follow-ups）**：在 plan 05 基线体系语境下裁定"是否为规则加载口径补 JMH 基准"，结论（补/不补 + 理由）写入 `ai-dev/logs/` 与 plan 05 perf doc 的后续记录处；只裁定不实施。
- [x] 收口项：roadmap item 9 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 M2 剩余项（10/11/12）状态未受扰动。

Exit Criteria:

- [x] `LintEngine`/`LintProfile` 落地且 Phase 3 全部测试通过（端到端 + 档位 + skippedByProfile 计数）。
- [x] **端到端验证**（Minimum Rules #22）：从 `.rule.yml` 资源到 `Diagnostic` 断言的完整路径一条测试走通，不依赖任何手工步骤。
- [x] **接线验证**（Minimum Rules #23）：`skippedByProfile` 与 `rulesKindFiltered` 计数在运行时真实发生（递增断言），规则 id 可从统计/日志观测到。
- [x] **无静默跳过**：requires 不满足、语言未知、形态矩阵拒绝三类路径全部显式（计数或抛错），无一处静默忽略（Minimum Rules #24）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] `./mvnw -pl nop-lint/nop-lint-java -am test -T 1C` 退出码 0（Phase 1 注册的回归）。
- [x] owner-doc：若执行顺序/管线与 design 03 §1.1 或能力集与 design 11 §8 表述有偏差，修订对应 design 行；`docs-for-ai/` 无 nop-lint 专属 owner doc，无需更新（裁定记录于日志）。
- [x] `ai-dev/logs/` 对应日期条目已更新（含 plan 06 follow-up 的 JMH 裁定结论）。

## Draft Review Record

- dispatch review #review-2026-09-21-142035-mission-driver-2026-09-21-1420-1-lint-engine-minimal-1-c6572505 to ses_f3c3a28e2ffealOFBCVYMZfyhn
- 2026-09-21：iteration 1，共识 approved #review-2026-09-21-142035-mission-driver-2026-09-21-1420-1-lint-engine-minimal-1-c6572505

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 Plan Status 改为 `completed`。关闭流程详见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [ ] 所有 in-scope confirmed live defects 已修复（起草时未知悉此类缺陷）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（起草时未知悉此类漂移）
- [ ] 行为/契约结果已达成：LintEngine v1 端到端可用（`.rule.yml` + 语言 + 源码 → `Diagnostic`/`LintStats`），kind 位过滤真实短路，fast/standard 档位与 `skippedByProfile` 统计可观测
- [ ] 必要 focused verification 已完成：三个 Phase 全部测试通过，含端到端/接线/无静默跳过断言
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Phase 3 的 JMH 项为裁定型 Follow-up，其裁定动作在本 plan 内完成并写入日志，不得把裁定本身延期）
- [ ] 受影响的 owner docs 已同步到 live baseline，或各 Phase Exit Criteria 已明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入下方 Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）LintEngine → CompiledRule → SourcePattern → Diagnostic 调用链运行时真实连通（不只是类型存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [ ] `./mvnw -pl nop-lint/nop-lint-java -am test -T 1C` 退出码 0
- [ ] checkstyle / 代码规范检查通过（`./mvnw -pl nop-lint/nop-lint-core -am checkstyle:check -q`）

## Verification

- `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C`（mission `commands.test`，每个 Phase 执行）
- `./mvnw -pl nop-lint/nop-lint-java -am test -T 1C`（Phase 1/3 显式执行，覆盖 nop-lint-java 模块内 ServiceLoader 注册与发现）
- `./mvnw -pl nop-lint/nop-lint-core -am checkstyle:check -q`（mission `commands.lint`，Closure Gates 执行）

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<closure 时填写；或明确写 no remaining plan-owned work>>
