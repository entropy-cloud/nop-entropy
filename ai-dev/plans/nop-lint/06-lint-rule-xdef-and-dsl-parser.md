# 06 规则 DSL 元模型与加载管线（roadmap item 8，M2 起点之一）

> Plan Status: completed
> Last Reviewed: 2026-09-21
> Source: ai-dev/backlog/nop-lint-roadmap.md Wave 2 item 8（deps: M1 ✓）；设计 10-xdef-metamodel.md（字段/管线权威）+ 01-pattern-dsl.md §2/§4（YAML 示例与 RuleCompiler 管线）+ 04-ast-grep-alignment.md §6（Rule 枚举语义）
> Related: plan 01–05（completed）

## Purpose

交付 YAML 规则的 XDSL 加载层：`lint-rule.xdef` 元模型、`lint.register-model.xml` 注册（YAML 经 DslJsonResourceLoader 进入 XDSL 管线）、`RuleDslParser` 匹配器唯一性校验、类型化 `RuleDslModel` 加载入口。完成后 `*.rule.yml` 可从 VFS 路径加载为经过 xdef 结构校验 + 唯一性校验的强类型模型，供 item 9 LintEngine / item 10 RuleTester 消费。这是 M2（items 8–13）的第一个未完成项。

## Current Baseline

- M1 已达成（items 1–7 done，plan 01–04）：`SourcePatternCompiler.compile → PatternMatcher.matchIn` 全链可用，`LintNode` 门面 + Java 语言适配完成。
- plan 05 已交付 JMH 基线（item 13 done）。Wave 2 剩余：8（本 plan）、9、10、11、12。
- `nop-lint-core/pom.xml` 已依赖 `nop-xlang`（XDSL/DslModelParser 管线可用），无新增模块依赖。
- **平台机制（2026-09-21 live repo 实测）**：
  - register-model 发现：`RegisterModelDiscovery` 扫描 `_vfs/nop/core/registry/*.register-model.xml`，`xdsl-loader` 且 fileType 为 json/yaml 后缀时挂 `DslJsonResourceLoader`（`RegisterModelDiscovery.java:166-179`）。
  - fileType 语义 = 文件名**第一个点**之后的整段（`ComponentModelConfig.java:33-34`），故 `foo.rule.yml` 的 fileType 是 `rule.yml`；`ResourceComponentManager.findModelTypeFromPath` 按它路由到 model type（`ResourceComponentManager.java:375-383`），`loadComponentModel` 返回 xdef 校验后的 `DynamicObject`。
  - YAML→XNode 根包装由 xdef 元数据驱动（`DslModelHelper.dslModelToXNode`），YAML 顶层键 = xdef 根的子字段，与 `dict.yaml` 同款（无根键、平铺）。
  - `xdef:check-mutex` 实际 schema：`id`/`props`/`atLeastOne` + 基类 `select`/`errorCode`/`message`（`xdef.xdef:118,149-151`）；xdef 加载期对 check 声明做 fail-fast 校验（id 唯一、select 可编译，`XDefinitionParser.validateConstraintChecks`），**实例级无运行时消费方**——唯一性强制由 RuleDslParser 承担（与设计 10 §2 注释①一致）。
  - `xdef:body-type="list"` 声明在父节点上、子标签可重复（register-model.xdef `<loaders>` 先例）；`utils`/`options`/`settings` 用 `body-type="map"` + `key-attr`。
- `NopLintException` 已存在（模块级 `NopException` 子类，英文消息，AGENTS 两层错误约定的模块内层）。
- 测试模式先例：`CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT)` + `ResourceComponentManager`（nop-credential `TestDefaultCredentialTypeRegistry`）。

### 语义裁定（执行依据，来源见括号）

- **rule 容器：恰好一个匹配器（XOR）**。依据：01 §2 "匹配器互斥（由上方 check-mutex 声明约束）" + ast-grep `Rule` 是枚举（04 §6 表：all/any/not/matches 是 Rule 的变体，组合发生在单个匹配器值内部）。
- **any/all 子项：至少一个匹配器**。依据：设计 10 §2 `any-child-matcher` check-mutex `atLeastOne="true"`；允许 pattern+kind 同项合取（ast-grep 超集，向后兼容）。
- Phase 1 xdef 字段范围 = 设计 10 §6 Phase 1 列表（pattern/kind/regex/单层 any/xscript/requires/metadata/options/files + settings 占位），关系匹配器/all/not/matches/utils/constraints/fix 随 Wave 4 扩展 xdef。

## Goals

- `/nop/lint/schema/lint-rule.xdef`（落 `nop-lint-core/src/main/resources/_vfs/nop/lint/schema/`）：Phase 1 字段全集，camelCase 直写声明，check-mutex 声明式意图（平台 schema 语法）。
- `lint.register-model.xml`（落 `_vfs/nop/core/registry/`）：model name=`lint`，`rule.yml` → xdsl-loader → 本 xdef。
- `RuleDslParser`（`io.nop.lint.core.rule` 包）：对 xdef 校验后的模型做匹配器唯一性校验（rule 层 XOR、any 子项 atLeastOne），fail-closed 报错。
- `RuleDslModel` + 加载入口：`*.rule.yml` VFS 路径 → 类型化模型（承载 Phase 1 全部字段），一个调用完成。
- `x:extends` delta 合并在规则模型上可验证生效（平台自带，测试证明）。

## Non-Goals

- 关系匹配器（inside/has/follows/precedes）、all/not/matches、utils、constraints、fix 的 xdef 声明与编译（Wave 4，items 21–25）。
- `RuleCompiler`/`CompiledRule`（item 9 起）、`LintEngine`（item 9）、`RuleTester`（item 10）。
- `lint-ruleset.xdef`（Phase 2）、ruleset 级 settings 注入。
- 修改 nop-xlang/nop-core/nop-xdefs（Protected Area；本 plan 仅新增 nop-lint 资源与类）。
- xscript 编译执行（item 14）——本 plan 只保证 xscript 文本字段往返。

## Scope

### In Scope

- `nop-lint/nop-lint-core/src/main/resources/_vfs/nop/lint/schema/lint-rule.xdef`（新增）
- `nop-lint/nop-lint-core/src/main/resources/_vfs/nop/core/registry/lint.register-model.xml`（新增）
- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/rule/`：`RuleDslModel`、`RuleDslParser`、加载入口（新增）
- `nop-lint/nop-lint-core/src/test/resources/_vfs/` 下规则 YAML 夹具 + `src/test/java/io/nop/lint/core/rule/` 测试（新增）
- `ai-dev/design/nop-lint/10-xdef-metamodel.md` 中 RuleDslParser FQCN 的对齐修订（若包名与 §2 字面值不一致）
- `ai-dev/logs/`、roadmap item 8 回写

### Out Of Scope

- 上列 Non-Goals 全部
- `docs-for-ai/`（规则 DSL 是 nop-lint 内部交付，平台使用文档无涉及；docs-for-ai 属平台使用知识）

## Execution Plan

### Phase 1 - xdef 元模型 + register-model 注册

Status: completed
Targets: `_vfs/nop/lint/schema/lint-rule.xdef`、`_vfs/nop/core/registry/lint.register-model.xml`、测试夹具 YAML

- Item Types: `Decision`（xdef 字段形态与声明语法）、`Proof`（注册链路运行时生效）

- [x] 编写 `lint-rule.xdef`：根 `lint-rule`（`x:schema=/nop/schema/xdef.xdef`、`xdef:name=lint-rule`、`id=!string` 必填、`language=enum:Java|TypeScript|TSX|XML`、`severity=enum:hint|info|warning|error|off`、`xscriptTimeoutMs=int=100`）；子字段 `message=!string`、`rule`（匹配器容器：`pattern/kind/regex` + `any`（body-type=list，子项为 pattern/kind/regex 匹配器对象））、`xscript`（xpl 片段，文本往返）、`requires`（csv-set）、`metadata`（`category=!string`、`severity` 枚举、`autoFixable=boolean=false`、`version=string=1.0`、`source` 可多值 string）、`options`/`settings`（map + key-attr 占位）、`files`（`include`/`exclude` 可多值 string）；全 camelCase 直写（小写开头、避免第二字符大写）；两处 `xdef:check-mutex` 按 design 10 §2 声明（平台 schema 语法：props/atLeastOne）【落地形态偏差见日志：`xdef:name="LintRule"`（var-name 合法性）、message 显式 `xdef:mandatory`、any 子项 `<matcher>` 对象形态、files/source csv-set 属性、xscript=string、check-mutex 不写 atLeastOne（平台校验器仅查属性，避免误报）】
- [x] 编写 `lint.register-model.xml`：`<model name="lint">` + `<xdsl-loader fileType="rule.yml" schemaPath="/nop/lint/schema/lint-rule.xdef"/>`（照抄 dict.register-model.xml 模式）
- [x] 测试夹具 YAML：合法规则 ≥3（单 pattern；any 多分支；全字段含 metadata/requires/options/files/xscript）、非法规则 ≥4（缺 id；severity 越界；未知键；rule 内零匹配器）【实际 5 合法（含 x:extends 父子对）+ 6 非法（缺 id/缺 message/severity 越界/顶层未知键/rule 内未知键/零匹配器 + 双匹配器/any 空分支）】
- [x] xdef 本身可被平台加载（`SchemaLoader.loadXDefinition` 不抛错），check 声明过 fail-fast 校验【TestRuleDslModelLoading.xdefLoadsAndPassesLoadTimeFailFastChecks】

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `initializeTo(REGISTER_COMPONENT)` 后 `ResourceComponentManager.loadComponentModel("/.../xxx.rule.yml")` 返回模型且字段值与 YAML 一致（**接线验证**：register-model 发现 → ComponentModelConfig → DslJsonResourceLoader → xdef 校验链路运行时贯通，非仅文件存在）【TestRuleDslModelLoading.registeredLoaderWiringReturnsModelMatchingYaml 等 10 用例】
- [x] xdef 结构校验生效：缺必填（id/message）、severity 越界枚举、未知键三种非法输入均加载失败；若平台对未知键默认宽容，由 RuleDslParser 兜底拒绝（两条路径都 fail-closed，不允许静默丢弃）【平台对未知键/未知子标签即拒（ERR_XDSL_UNKNOWN_PROP / ERR_XDSL_UNDEFINED_CHILD_NODE），缺 id/缺 message/severity 越界各有独立测试】
- [x] `x:extends` 验证：子规则 YAML 通过 `x:extends` 继承父规则并覆盖 `message`，加载结果为合并后模型（证明 delta 合并对 rule 模型生效）【TestRuleDslModelLoading.xExtendsChildRuleMergesParentFields】
- [x] **无静默跳过**：全部非法夹具抛异常（xdef 校验异常或 `NopLintException`），无空返回/吞异常【8 个非法夹具：4 类 xdef 校验异常 + 3 类 NopLintException（零匹配器/双匹配器/any 空分支），均有断言；NopLintException 路径随 Phase 2 parser 落地（同次执行连续完成）】
- [x] No owner-doc update required（Phase 1 未改已交付契约；xdef 是新交付物，其形态记录在 design 10，Phase 3 统一核对）
- [x] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 2 - RuleDslParser 唯一性校验 + RuleDslModel + 加载入口

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/rule/`、对应测试

- Item Types: `Fix`（缺失的加载层，属 M2 阻塞项）、`Proof`（校验分支与端到端链路）

- [x] `RuleDslModel`：类型化承载 Phase 1 字段全集（id/language/severity/message/matcher 树/xscript 文本/xscriptTimeoutMs/requires/options/settings/metadata/files），不可变
- [x] `RuleDslParser`：rule 容器恰好一个匹配器（pattern/kind/regex/any XOR，多出或为零均报错）；`any` 每个子项至少一个匹配器；违规报错含规则 id 与冲突/缺失字段名（英文）
- [x] 加载入口：VFS 路径/资源 → xdef 校验（复用 XDSL 管线）→ `RuleDslParser` → `RuleDslModel`，单方法完成；`RuleDslParser` 的 FQCN 写入 xdef `xdef:parser-class` 属性（声明式意图，与 design 10 §2 注释②一致）【`RuleDslParser.loadRuleModel(resourcePath)` 单方法：RCM 注册链路 → parseRuleModel；FQCN 已写入 lint-rule.xdef 根属性】
- [x] 单元测试：唯一性各分支（rule 层双匹配器 pattern+kind、rule 层零匹配器、any 子项零匹配器、any 子项 pattern+kind 合取**合法**）；全字段往返断言；错误消息含 id 与字段名【TestRuleDslParser 13 用例】

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述每个校验分支有独立命名测试用例，全部通过【ruleLevelTwoMatchersRejected / ruleLevelZeroMatchersRejected / missingRuleContainerRejected / anyBranchWithZeroMatchersRejected / anyBranchConjunctionIsLegal / emptyAnyMatcherRejected / nonDynamicModelRejected】
- [x] 校验失败抛 `NopLintException`，消息为英文且含规则 id 与冲突字段名
- [x] **端到端验证**：一条测试从 `.rule.yml` 测试夹具路径出发，经注册链路 + xdef 校验 + RuleDslParser，最终断言 `RuleDslModel` 的 matcher 结构（any 分支数、pattern 文本等）——组件级单测不能替代本条【endToEndFromAnyFixtureToTypedMatcherStructure + endToEndFullFieldRoundTrip + 3 个 endToEnd 非法夹具用例】
- [x] **无静默跳过**：`RuleDslModel` 无未实现字段占位（Phase 1 字段全部真实填充）；新增公共方法无空体/TODO【可选字段（metadata/files/xscript 等）YAML 未声明时为 null/空集合，属"未声明"而非"未实现"；全部 Phase 1 字段在 endToEndFullFieldRoundTrip 逐项断言】
- [x] `./mvnw test -pl nop-lint/nop-lint-core -am` 全绿（含 plan 05 bench smoke 不腐坏）【104 tests，0 failures】
- [x] No owner-doc update required（本 Phase 无契约变更；Phase 3 做设计文档对齐核对）
- [x] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 3 - 文档对齐 + roadmap 回写 + 收口

Status: completed
Targets: `ai-dev/design/nop-lint/10-xdef-metamodel.md`、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Proof`（一致性核对）、`Follow-up`（roadmap 状态回写）

- [x] design 10 与 live 交付对齐核对：RuleDslParser 实际包名（§2 字面值 `io.nop.lint.core.RuleDslParser` vs 实际 `io.nop.lint.core.rule.RuleDslParser`，不一致则修订 §2/§4 字面值）；xdef Phase 1 实际字段形态与 §6 交付清单一致（files/source 多值声明的落地形态若与 §2 示例有差异，修订并注明）【§2 示例重写为落地形态（含落地形态说明块：xdef:name=LintRule、message mandatory、check-mutex 无 atLeastOne 及平台校验器 attr-only 实测、any 子项 matcher 对象、files/source csv-set 属性、xscript string、options/settings YAML 形态），§2 说明 csv-set 形态更正、FQCN 更正，§4 管线补 loadRuleModel 入口，§5 两行更新】
- [x] roadmap item 8 → `done`（plan 06）；M2 保持 `todo`（9/10/11/12 未完成，里程碑不提前翻）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 每日日志收口记录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] design 10 无与 live 代码/资源冲突的字面描述（FQCN、字段清单核对记录写入日志）
- [x] roadmap Work Items 段 item 8 状态为 `done`（plan 06），其余状态未误改
- [x] check-doc-links 退出码 0
- [x] `ai-dev/logs/2026/09-21.md` 有收口条目

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复（本 plan 无已知 live defect；新发现随执行处理）
- [x] 所有 in-scope confirmed contract drifts 已收敛（design 10 字面值与 live 一致或已修订）【§2 重写为落地形态并注明平台 check-mutex attr-only 实测；独立审计确认无剩余冲突】
- [x] `.rule.yml` → `RuleDslModel` 加载链路完整可用（含唯一性校验、x:extends）【端到端测试：valid-any/valid-full/child-rule 夹具 → 类型化模型断言】
- [x] 必要 focused verification 已完成（Phase 1/2 全部测试）【104 tests，0 failures】
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift【Deferred 区仅 Wave 4 范围项（stopByRule/matches-utils 校验），已声明 successor】
- [x] 受影响 owner docs 已同步（design 10 对齐修订完成；roadmap 状态回写）
- [x] 独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow 检查）【task ses_f3d2f6af6ffeHJeLWJlC1Uemrl：APPROVE-CLOSURE】
- [x] **Anti-Hollow Check**：closure audit 已验证（a）注册链路运行时连通（loadComponentModel 实际走通而非仅资源存在），（b）校验失败路径全部抛异常【(a) maven 日志 `unregisterComponentModelConfig:modelType=lint` 实证注册发现；(b) 8 个非法夹具各有抛异常断言】
- [x] `./mvnw test -pl nop-lint/nop-lint-core -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint/nop-lint-core --severity high` 退出码 0

## Deferred But Adjudicated

### stopBy=rule 必填 stopByRule、matches→utils 存在性校验

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Phase 1 xdef 不含关系匹配器与 matches/utils 语法面，校验无真实输入路径；随 Wave 4（items 21–24 扩展 xdef 时）在同处落地，设计 10 §2 已将两者指派给 parser-class。
- Successor Required: `yes`
- Successor Path: Wave 4 对应执行计划（items 21–24 立计划时显式纳入）

## Non-Blocking Follow-ups

- 性能裁定：规则加载属冷路径（plan 03/04 已裁定编译为冷路径），本 plan 不设 JMH 基准；item 9 LintEngine 落地后在现有基准体系中评估是否补加载口径基准（现 plan 05 基线仅测内核）。

## Closure

Status Note: 三个 Phase 全部完成：lint-rule.xdef（Phase 1 字段全集，落地形态与平台实测机制一致）+ lint.register-model.xml 注册 + RuleDslParser 唯一性校验（fail-closed）+ 不可变 RuleDslModel + loadRuleModel 单方法加载入口；`.rule.yml` → 类型化模型链路（含 x:extends delta 合并、8 类非法输入 fail-closed）经端到端测试与独立审计证实可用。design 10 §2/§4/§5 已对齐落地形态；roadmap item 8 → done（M2 保持 todo）。
Completed: 2026-09-21

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task `ses_f3d2f6af6ffeHJeLWJlC1Uemrl`），verdict **APPROVE-CLOSURE**
- Audit Session: `ses_f3d2f6af6ffeHJeLWJlC1Uemrl`
- Evidence:
  - Phase 1 Exit Criteria 全 PASS：xdef 逐属性核对（xdef:name=LintRule、parser-class FQCN、id/language/severity/xscriptTimeoutMs、message mandatory、rule 容器 + any body-type=list matcher 对象、xscript string、requires/metadata.source/files csv-set、options/settings map+key-attr、两处 check-mutex id+select）；register-model `model name="lint"` + `rule.yml`/`rule.json` → schemaPath 接线；`TestRuleDslModelLoading` 10 用例（接线验证/xdef 加载/结构校验拒绝/x:extends 合并）全绿。
  - Phase 2 Exit Criteria 全 PASS：`RuleDslModel` final + 全 final 字段 + 无 setter，12 字段 + Matcher/Branch/Metadata/Files；`RuleDslParser` XOR/atLeastOne 各失败路径抛 NopLintException（英文消息含 id + 字段名）；7 个分支用例 + 4 个端到端用例（`endToEndFromAnyFixtureToTypedMatcherStructure`、`endToEndFullFieldRoundTrip`、2 个非法夹具端到端）全绿。
  - Phase 3 Exit Criteria 全 PASS：design 10 §2 重写为落地形态、roadmap diff 仅 item 8 与框架复用表行两处、check-doc-links --strict 退出码 0。
  - `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C`：104 tests，0 failures，0 errors（独立审计复跑确认；审计后 RuleDslParser 内联 FQN → import 的 Minor 修复，TestRuleDsl* 23 用例复跑全绿）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint/nop-lint-core --severity high` 退出码 0（0 findings）。
  - Anti-Hollow 检查结果：(a) 注册链路运行时连通实证——maven 测试日志出现 `ResourceComponentManager.unregisterComponentModelConfig:modelType=lint`（lint model type 被真实发现注册）且 `DslModelParser` 解析 `lint-rule[@id="demo/no-console"]`；(b) 端到端路径从 `.rule.yml` 夹具路径到 `RuleDslModel` matcher 结构断言完整；(c) 新增类无空方法体/TODO/吞异常（唯一 catch 重抛 NopLintException）。
  - Deferred 项分类检查：Deferred But Adjudicated 仅含 Wave 4 范围项（stopBy=rule 必填、matches→utils 存在性校验），Phase 1 xdef 无对应语法面、校验无真实输入路径，已显式 successor（items 21–24）；无 in-scope live defect 或 contract drift 被降级。
  - 审计 Minor 4 项均 non-blocking：plan baseline 历史措辞（design 10 已为准）、rule.json 额外注册（与 javadoc 一致）、内联 FQN（已修复并复测）、本 plan 文件 2 个 doc-link warning（shorthand 引用，非 error）。

Follow-up:

- Wave 4（items 21–24 扩展 xdef 时）在同处落地 stopBy=rule 必填 stopByRule 与 matches→utils 存在性校验（见 Deferred But Adjudicated）。
- item 9 LintEngine 落地后在现有基准体系中评估是否补规则加载口径的 JMH 基准（见 Non-Blocking Follow-ups）。
