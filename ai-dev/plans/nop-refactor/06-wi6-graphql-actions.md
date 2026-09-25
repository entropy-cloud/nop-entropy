# 06 WI6 nop-refactor-graphql——Refactor__ 四 action 契约落地

> Plan Status: draft
> Last Reviewed: 2026-09-25
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M1 WI6 原文 + Cross-Cutting 完成判定 + Framework/Platform Reuse 表 + Rules 行为红线）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §三（GraphQL 操作面契约——四 action、preview/apply 两段语义、cap 沿 Lint__checkSource）与 §四（RefactorResult 载荷——WI5 已落地）
> Related: `ai-dev/plans/nop-refactor/05-wi5-refactor-result-verification-payload.md`（本 plan 直接复用其 RefactorResult/RefactorVerifier 载荷面，不重造）；`ai-dev/plans/nop-refactor/04-wi3-transform-dsl.md`（transform 改写通道——RewriteInput 的规则集消费面）；`ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md`（EditPlanApplier 应用入口）；WI12（rename 对的后续归属）；`ai-dev/design/nop-refactor/00-vision.md` §三（原则 1 GraphQL-first 无状态 / 原则 3 self-verification / 原则 6 fail-closed / 原则 9 复杂度预算）
> Review: （draft review 记录占位——对抗性审查轮次、发现与裁定落此行后再转 active）

## Purpose

执行 roadmap WI6（Item Type: Fix）：把 baseline §三 裁定的 GraphQL 操作面契约为可执行模块——新建 nop-refactor/nop-refactor-graphql（baseline 三模块拓扑的第三块），`@BizModel("Refactor")` 契约面提供 Refactor__previewRewrite / Refactor__applyRewrite（RewriteInput：规则集 VFS prefix + 目标文件集合），复用 WI5 载荷（RefactorResult 等 record 直接经 GraphQL 返回）、WI3 transform 通道（规则集 → transformFixes）、WI4 应用入口（EditPlanApplier）。无状态重执行语义（apply 重算编辑计划后原子落盘，无会话/token）；资源 cap fail-closed 沿 Lint__checkSource 先例；GraphQLEngine RPC 端到端真调证明。v1 schema 只声明 rewrite 对，rename 对随 WI12 落地（裁定见 Goals）。

## Current Baseline

（live 已核对，2026-09-25；行号为核对时快照，执行时以 live 为准）

- **WI3 已 completed**（commit f1c654c971，roadmap 已勾选）：transform"只改不报"规则通道落地——`LintEngine.lint(...)` 返回的 `LintResult` 携带 `transformFixes`（nop-lint-core engine/LintEngine.java ~:278/:323），改写载体 Fix 经抑制尾之外的独立改写通道产出（WI3 裁定 1/4）。
- **WI4 已 completed**（plan 03，roadmap 已勾选）：`EditPlanApplier.apply(Path file, byte[] source, List<Fix> edits, LintLanguage language, boolean dryRun)` → `EditPlanResult(finalSource, rolledBack, appliedEdits, skippedConflicts, skippedEdits)`（nop-lint-core fix 包）——dryRun=true 不落盘；原子写 + 守卫回滚（rolledBack 为返回结局非异常）+ skippedEdits 携带被跳过编辑本体（WI5 additive 扩展）。
- **WI5 已 completed**（plan 05 定稿 2026-09-25，commit 7af2d289cb，roadmap WI5 已勾选）：nop-refactor/nop-refactor-core 存在——RefactorResult / Verification / FileEdit / RefactorStats / NonApply / NopRefactorException / EditedFile / RefactorVerifier（io.nop.refactor.core 包）；`RefactorVerifier.assemble(applied, files, edits, nonApplied)` 组装完整载荷。若 landed 形态与本节记载不一致，以 landed 源码 + design 01 §四增注为准并回写本节。
- **规则集定位先例（只读消费）**：`RuleSetLoader`（nop-lint-core cli 包）`loadRuleSet(String prefix)` 递归扫描 VFS prefix 下 `*.rule.yml` / `*.ruleset.yml`，`DEFAULT_RULES_PREFIX = "/nop/lint/rules/"`（~:48）；fail-closed：prefix 零规则文件 = 部署错误、blank language 拒绝、rule id 双源漂移失败。
- **目标文件收集先例（只读消费）**：`TargetScanner`（nop-lint-core cli 包）——文件按路径直取、目录递归收集、扩展名→语言显式表（java/ts/tsx/xml/xbiz，~:41-47）、不存在路径 = hard input error、字典序稳定排序。**无 glob 展开面**。
- **GraphQL biz 形态先例**：nop-lint-graphql——pom 依赖 nop-lint-core + nop-graphql-core + nop-ioc（pom.xml :24-39）；`@BizModel("Lint")`（NopLintBizModel.java :58）+ `@BizQuery` + `@Name`/`@Optional` 参数；IoC 注册走显式 beans.xml（`_vfs/nop/lint/beans/app-lint.beans.xml` :7，**非注解扫描**，对齐 Nop IoC 约束）+ 空标记文件 `_vfs/nop/lint/_module`。变更类注解 `@BizMutation` 存在于 nop-api-core（io.nop.api.core.annotations.biz.BizMutation）。
- **返回载荷序列化先例**：LintCheckResult / LintDiagnosticView / LintRuleView 均为 **record 直接作 biz 返回**（如 LintCheckResult.java :8-10）——record 可直接经 biz 反射面序列化，无需转 Map 或专用 DTO。e2e 测试的最小 SchemaLoader（TestNopLintGraphQL.BizModelSchemaLoader :123-238）按引擎生成名 `g_<fqcn>` 手写对象定义、嵌套对象经 g_ 名引用（listField 先例）——**枚举与嵌套 record 在 loader 中的定义形态执行时以 live 为准**。
- **资源 cap fail-closed 先例**：`CFG_MAX_SOURCE_SIZE = AppConfig.varRef(..., "nop.lint.graphql.max-source-size", Integer.class, 1024*1024)`（NopLintBizModel.java :61-63）；`checkSourceCap`（:255-262，读后字符级）+ `checkPreReadCap`（:246-253，**字节级 read 前置门**——超大目标在内容进内存前拒绝，"rejected before read"）；checkFile 的 path grammar（:195-236：namespace 前缀拒绝 / `/` 根走 VFS / 磁盘路径 toRealPath 后限制在工作目录内）。测试形态：TestNopLintBizModel.oversizeSourceFailsClosed（:62-70）断言异常消息含配置键；vfsBranchOversizedResourceIsRejectedBeforeRead（:242-260）用 `AppConfig.getConfigProvider().updateConfigValue` 动态改 cap。
- **GraphQLEngine RPC e2e 先例**：TestNopLintGraphQL——`CoreInitialization.initialize()` → `BeanContainer.getBeanByType` 取容器 bean（**beans.xml 装配证明**）→ `new GraphQLEngine()` + `setSchemaLoader(new BizModelSchemaLoader(bizModel))` + `init()` → `engine.newRpcContext(GraphQLOperationType.query, "Lint__checkSource", ApiRequest)` → `FutureHelper.syncGet(engine.executeRpcAsync(context))` → ApiResponse；结构化错误面 = `!response.isOk()` + `response.getCode()` 携带消息内容（:85-112）。
- **nop-refactor 聚合与注册现状**：nop-refactor/pom.xml 已存在（modules 现仅 nop-refactor-core）；根 pom.xml 已注册 nop-refactor；`docs-for-ai/01-repo-map/module-groups.md` :26 已有 nop-refactor 模块组行（现仅列 nop-refactor-core，本 plan 增补 graphql 子模块）。
- gap：Refactor__ 契约面不存在（无 nop-refactor-graphql 模块）；baseline §三 四 action 中 rewrite 对无任何实现载体。

## Goals

- **模块骨架**：新建 nop-refactor/nop-refactor-graphql——pom 同 nop-lint-graphql 型（依赖 nop-refactor-core + nop-graphql-core + nop-ioc；**不依赖 nop-lint-graphql 本身**——它是形态先例不是依赖；nop-lint-core 经 nop-refactor-core 传递可达，直接 import 的类按 Maven 卫生声明直接依赖）；`_vfs/nop/refactor/beans/app-refactor.beans.xml` 显式 bean 注册 + `_vfs/nop/refactor/_module` 空标记；`@BizModel("Refactor")`。
- **v1 schema 只声明 rewrite 对（rename 对占位裁定 = 方案 a）**：Refactor__previewRewrite / Refactor__applyRewrite 两个 `@BizMutation`；Refactor__previewRename / Refactor__applyRename 本 plan **不声明**，随 WI12 直接加入。理由：GraphQL schema 增加字段是非破坏变更（既有字段与客户端兼容），后续 plan 无破坏地增长；声明恒抛 UnsupportedOperationException 的 action 会把 schema 自描述面变成虚假广告——AI 调用方只有运行时才发现不可用，违背"所见即所可用"；Minimum Rules #24 的 fail-closed 要求针对"已实现的方法不得静默吞"，不要求未实现的操作占位声明。此裁定落 design 01 §三增注。
- **RewriteInput 字段（v1）**：`rulesetPrefix`（规则集 VFS prefix，经 RuleSetLoader.loadRuleSet 加载；加载失败 = 结构化错误，绝不空规则集静默继续）+ `paths`（目标文件集合：显式文件/目录路径，目录递归走 TargetScanner 同型收集；**glob 不入 v1**——TargetScanner 先例无 glob，展开逻辑是新增扫描面，归 Follow-up）。baseline §三 RewriteInput 注释的"pattern 直给"（裸 pattern 不经规则集文件直接组装编辑计划）**裁定为 Non-Goal**：规则集是已验证的安全面（模板渲染 + fail-closed 矩阵 + 资源门），裸 pattern 的编辑计划组装是新增引擎面，违反"复用存量"的预算约束；未来扩展见 Follow-ups。
- **rewrite 执行链（preview 与 apply 共用同一计算函数——无状态重执行语义）**：加载规则集 → 收集目标文件（path grammar 沿 checkFile 同型：namespace 前缀拒绝 + toRealPath 工作目录约束——**写操作比读更严，必须约束**；不存在路径 hard error）→ 逐文件按扩展名定语言、按语言取规则 → LintEngine.lint → transformFixes → `EditPlanApplier.apply`（preview dryRun=true / apply dryRun=false 原子落盘）→ `RefactorVerifier.assemble(applied=false/true)`。无会话、无 plan-token：apply 内部重算编辑计划（操作确定性保证两次计算一致，baseline §三裁定）。
- **资源 cap fail-closed（沿 Lint__checkSource 先例同型）**：`nop.refactor.graphql.max-source-size`（单文件源上限，默认同 1MB 量级；字节级 read 前置门 + 读后字符级 backstop）+ `nop.refactor.graphql.max-target-files`（目标集合解析后的文件数上限——批量面新增风险维度，Lint 先例只有单源 cap；默认值随实现钉死并落 design 增注）。超限 = NopRefactorException 英文消息（message-mode，命名配置键与实际值，测试断言消息内容）。
- **返回载荷直接复用 WI5 records**：RefactorResult 及嵌套 record 直接作 `@BizMutation` 返回（LintCheckResult record 先例）——不转 Map、不建第二套 DTO；FileEdit.range 的 SourceRange、CostTier/Reason 枚举在 e2e SchemaLoader 中的定义形态以 live 为准。
- **e2e 真调证明（GraphQLEngine RPC，沿 TestNopLintGraphQL 先例）**：previewRewrite 返回 diff+stats 且目标文件不落盘；applyRewrite 落盘且 applied=true；cap 超限结构化错误；规则集加载失败结构化错误。

## Non-Goals

- 不做 rename 对（Refactor__previewRename / Refactor__applyRename）与 RenameInput——WI9–WI12；v1 schema 不声明占位 action（裁定见 Goals）。
- 不做 pattern 直给（裸 pattern 输入直接组装编辑计划）——Non-Goal 裁定见 Goals，未来扩展见 Follow-ups。
- 不做 glob 目标展开——TargetScanner 显式路径/目录先例为准，glob 归 Follow-up。
- 不做 CLI 批处理形态（WI7）、不做操作框架四段化（WI9）、不做 P0 规则内容补货与演示规则集（WI8）。
- 不修改 nop-lint / nop-treesitter 任何既有行为（只读消费）；nop-refactor-core 原则上零改动，若 Phase 执行证明确需 additive 变更，逐项记录并保持既有契约不缩水。
- 不做规则集缓存（v1 每请求加载——规则集是入参不是部署面，缓存引入失效问题；prefix 键控缓存归 Follow-up）。
- 不做会话/plan-token 形态（baseline §六已拒绝）。

## Scope

### In Scope

- 新建 nop-refactor/nop-refactor-graphql（pom + main/test 包结构 + `_vfs` beans/_module 资源）+ nop-refactor/pom.xml modules 注册。
- NopRefactorBizModel：Refactor__previewRewrite / Refactor__applyRewrite（`@BizMutation`）+ RewriteInput + 执行链（规则集加载 / 目标收集 / transformFixes / EditPlanApplier / RefactorVerifier.assemble）+ cap fail-closed。
- 测试：服务级边界矩阵（TestNopRefactorBizModel 型：cap / path grammar / 规则集加载失败 / preview-apply 语义）+ transform 规则 fixture（src/test/resources VFS prefix）+ GraphQLEngine RPC e2e（TestNopRefactorGraphQL 型）。
- `ai-dev/design/nop-refactor/01-architecture-baseline.md` §三 增注：v1 schema 只落 rewrite 对（裁定 a）、RewriteInput v1 字段（pattern 直给 Non-Goal、glob 不入 v1）、cap 两配置键、残留 lint 子集裁定、守卫回滚呈现、模块落点与依赖形态。
- `docs-for-ai/01-repo-map/module-groups.md` nop-refactor 行增补 nop-refactor-graphql 子模块。

### Out Of Scope

- nop-lint / nop-treesitter 行为面与既有测试基线（零修改红线）。
- nop-refactor-core 的既有载荷与 verification 语义（消费面，不改）。
- docs-for-ai 模块文档新建（WI13 收口统一新建；module-groups.md 一行增补除外——既有 doc 同步义务）。
- rename 面、CLI 面、规则内容（WI12 / WI7 / WI8）。

## Execution Plan

前置依赖（roadmap 拓扑序）：WI3/WI4/WI5 均已 completed（roadmap 已勾选）。Phase 1 开工前以 git status 自查 nop-refactor/nop-lint 工作树干净。若 WI5 landed 形态与本 plan Current Baseline 记载不一致，以 landed 源码 + design 01 §四增注为准并回写本节。

### Phase 1 - 模块骨架 + schema + previewRewrite（Fix + Decision）

Status: planned
Targets: `nop-refactor/nop-refactor-graphql/`、`nop-refactor/pom.xml`、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Fix + Decision`

- [ ] 模块骨架：nop-refactor-graphql pom（parent nop-refactor；依赖 nop-refactor-core + nop-graphql-core + nop-ioc，直接 import nop-lint-core 类则声明直接依赖——live 为准）+ `_vfs/nop/refactor/beans/app-refactor.beans.xml`（`<bean id="io.nop.refactor.graphql.NopRefactorBizModel" .../>` 沿 app-lint.beans.xml :7 同型）+ `_vfs/nop/refactor/_module` 空标记 + nop-refactor/pom.xml modules 注册
- [ ] NopRefactorBizModel 骨架：`@BizModel("Refactor")` + Refactor__previewRewrite `@BizMutation`（返回 RefactorResult）
- [ ] RewriteInput 形态 Decision：优先 record + 单一 `@Name("input")` 入参（对齐 baseline §三 `input: RewriteInput!` 文本）；若 live 入参绑定对 record 不支持则退化普通 bean 类——契约字段不变（rulesetPrefix + paths），wire schema 不变；裁定记录落当日 log
- [ ] previewRewrite 执行链：`RuleSetLoader.loadRuleSet(rulesetPrefix)`（零规则/解析失败/双源漂移 = NopRefactorException 结构化错误）→ 目标收集（显式路径/目录递归，TargetScanner 同型；path grammar：namespace 前缀拒绝 + toRealPath 工作目录约束 + 不存在路径 hard error）→ 逐文件扩展名定语言取规则 → LintEngine.lint → transformFixes → `EditPlanApplier.apply(dryRun=true)` → `RefactorVerifier.assemble(applied=false)`；零修复文件不进 edits/diff（changed()=false 既有语义，stats 只计受影响文件——结构化结果而非静默跳过）
- [ ] 残留 lint 子集 Decision：RefactorVerifier 的规则子集 = 载入规则集本体（同引擎同规则，不引入第二输入面；residualRuleCount 机器可判读——WI5"未配 vs 配了零残留"语义保持）——裁定与理由落 design 01 §三增注
- [ ] transform 规则测试 fixture：src/test/resources/_vfs 下最小 prefix（RuleSetLoader 可加载、xdef 合法、至少一条 transform 规则——沿 nop-lint 既有 rule fixture 同型改写）
- [ ] 服务级测试（TestNopRefactorBizModel 型）：preview 返回 diff+stats 且目标文件内容/mtime 不变（不落盘）、规则集加载失败 fail-closed、目标路径 grammar 三分支拒绝面、零修复目标 = 空载荷非错误
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw compile -pl nop-refactor/nop-refactor-graphql -am` 成功——新模块进聚合构建
- [ ] previewRewrite 服务级测试全绿：diff + stats 返回、目标文件内容逐字节不变（不落盘）、fail-closed 面逐条断言
- [ ] **接线验证**（Minimum Rules #23）：执行链确实调用了 RuleSetLoader → LintEngine（transformFixes 非空）→ EditPlanApplier → RefactorVerifier——测试断言载荷携带 fixture 规则产出的真实编辑（非空 diff），证明四环节运行时连通
- [ ] **无静默跳过**（Minimum Rules #24）：规则集加载失败 / 目标不存在 / namespace 路径 / workdir 逃逸均为显式异常，无空方法体、无吞异常、无 placeholder 返回
- [ ] **新功能测试清单**（Minimum Rules #25）：preview 不落盘、加载失败、path grammar、零修复空载荷——逐项列出并落为测试
- [ ] RewriteInput 形态与残留 lint 子集两项 Decision 已裁定并记录（log + design 增注准备）
- [ ] `docs-for-ai/01-repo-map/module-groups.md` nop-refactor 行已增补 graphql 子模块
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - applyRewrite + 资源 cap + GraphQLEngine e2e（Decision + Fix）

Status: planned
Targets: `nop-refactor/nop-refactor-graphql/src/main|test/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Decision + Fix`

- [ ] Refactor__applyRewrite `@BizMutation`：与 previewRewrite 共用同一计算函数，dryRun=false——重算编辑计划后 EditPlanApplier 原子落盘，applied=true 返回同一载荷（无会话/token，preview 与 apply 之间无任何服务端状态）
- [ ] 守卫回滚呈现 Decision：`EditPlanResult.rolledBack=true` 的文件——载荷呈现按 live WI4/WI5 语义裁（edited=回滚后内容、无 diff；是否产 NonApply 条目及 reason 映射实现时裁定），回滚事实不得静默吞——裁定落 design 01 §三增注
- [ ] 资源 cap fail-closed：`nop.refactor.graphql.max-source-size`（AppConfig.varRef，字节级 read 前置门 checkPreReadCap 同型 + 读后 backstop）+ `nop.refactor.graphql.max-target-files`（目标集合解析后计数门，超限在任何读取前拒绝；默认值钉死）——NopRefactorException 英文消息命名配置键与实际值
- [ ] e2e（TestNopRefactorGraphQL 型，沿 TestNopLintGraphQL 先例）：CoreInitialization + BeanContainer 取 bean（beans.xml 装配证明）+ BizModelSchemaLoader（`g_<fqcn>` 对象定义：RefactorResult/Verification/FileEdit/RefactorStats/SkippedBuckets/NonApply/SourceRange + RewriteInput 入参绑定；枚举定义形态 live 为准）→ `newRpcContext(GraphQLOperationType.mutation, ...)` 真调四断言：(a) previewRewrite 返回 diff+stats 且文件系统不变；(b) applyRewrite 落盘 + applied=true；(c) cap 超限结构化错误（response code 命名配置键）；(d) 规则集加载失败结构化错误
- [ ] design 01 §三 增注落档：v1 schema 只落 rewrite 对（裁定 a 理由全文）、RewriteInput v1 字段与 pattern 直给 Non-Goal、glob 不入 v1、cap 两键、残留 lint 子集裁定、守卫回滚呈现、模块落点与依赖形态——纯增注不改写
- [ ] 行为红线自查（scoped）：git diff 证实 nop-lint / nop-treesitter 零修改、nop-refactor-core 零修改（或仅记录在案 additive）
- [ ] `./mvnw test -pl nop-refactor/nop-refactor-graphql -am` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**（Minimum Rules #22）：TestNopRefactorGraphQL 四断言 (a)–(d) 全绿——从 GraphQL RPC 入口经容器 bean、执行链到 RefactorResult 载荷与文件系统效果一测贯通
- [ ] **接线验证**（Minimum Rules #23）：RPC 路径确认容器装配的 NopRefactorBizModel 被真实调用（BeanContainer 取 bean + engine.executeRpcAsync 响应含载荷字段——沿 TestNopLintGraphQL :43-46 装配证明形态）
- [ ] **无静默跳过**（Minimum Rules #24）：cap 超限 / 加载失败 / 回滚事实均显式呈现；applyRewrite 无 dryRun 之外的第三种落盘路径
- [ ] **新功能测试清单**（Minimum Rules #25）：(a) preview 不落盘、(b) apply 落盘 applied=true、(c) cap 超限、(d) 加载失败——逐项列出并落为 e2e 测试；apply 与 preview 的载荷一致性（同输入同 diff）有断言
- [ ] apply 重执行语义有测试：同一输入连续 preview→apply（或重复 apply）结果一致（操作确定性），服务实例无跨请求状态
- [ ] design 01 §三 增注已落档且与 landed 实现逐条互洽（实现偏差即回写文档，不得让契约与 live 漂移）
- [ ] 行为红线自查记录在案（scoped git diff：nop-lint / nop-treesitter / nop-refactor-core 零修改或仅记录在案 additive）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 项完成，无残留未勾选 checklist
- [ ] 四 action 契约的 v1 交付面成立：rewrite 对经 GraphQLEngine 端到端可用、载荷字段 = WI5 已落地契约（RefactorResult 六字段，无缩水）、rename 对按裁定 (a) 不声明且 design 增注已记录
- [ ] 无状态重执行语义成立：apply 路径无会话/token、preview 与 apply 共用同一计算函数、重执行确定性有测试
- [ ] 资源 cap fail-closed 面成立：source-size + target-files 两键、read 前置门、结构化错误，沿 Lint__checkSource 先例同型
- [ ] 零行为红线（scoped diff）：nop-lint / nop-treesitter / nop-refactor-core 既有行为零修改（或仅记录在案 additive）；nop-lint 全模块测试零回归
- [ ] **Anti-Hollow Check**：closure audit 已验证 (a) 端到端路径从 GraphQL RPC 到文件落盘/载荷返回运行时连通（不只是类型存在），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [ ] owner docs 已同步：design 01 §三 增注、`docs-for-ai/01-repo-map/module-groups.md` 增补
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-graphql --severity high` 退出 0
- [ ] 代码规范：`check-import-order.mjs` 本 plan 新增/变更文件零违规（范围口径——全仓存量违规在本 plan 范围外）
- [ ] vision 原则 1–9 回扣核对（closure audit 执行）：原则 1（GraphQL-first 无状态）、原则 3（self-verification 载荷不缩水）、原则 6（fail-closed）、原则 9（预算——只建裁定内第三模块、复用 WI3/WI4/WI5 存量不重造）为本 WI 重点核对项
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [ ] `./mvnw test -pl nop-refactor/nop-refactor-graphql -am` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（范围口径：本 plan 变更文件零错误；全仓 gate 因并发在途文档 churn 波动时，提交前全局复跑）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/06-wi6-graphql-actions.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- pattern 直给（RewriteInput 增加裸 pattern 输入直接组装编辑计划）：需先建裸 pattern 的安全门与编辑计划组装面（新增引擎面），过性价比门后另立项——Why Not Blocking Closure：v1 规则集定位已覆盖 AI 闭环所需的已验证安全面，裸 pattern 是能力扩展非契约缺口。
- glob 目标展开（RewriteInput.paths 支持 glob 模式）：TargetScanner 先例无 glob，展开语义与安全边界需单独裁定——v1 显式路径/目录已可用。
- 规则集 prefix 键控缓存：若实测每请求加载成为延迟瓶颈再做，必须键控 prefix 且记录失效语义。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
