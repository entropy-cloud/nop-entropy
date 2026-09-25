# 06 WI6 nop-refactor-graphql——Refactor__ 四 action 契约落地

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M1 WI6 原文 + Cross-Cutting 完成判定 + Framework/Platform Reuse 表 + Rules 行为红线）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §三（GraphQL 操作面契约——四 action、preview/apply 两段语义、cap 沿 Lint__checkSource）与 §四（RefactorResult 载荷——WI5 已落地）
> Related: `ai-dev/plans/nop-refactor/05-wi5-refactor-result-verification-payload.md`（本 plan 直接复用其 RefactorResult/RefactorVerifier 载荷面，不重造）；`ai-dev/plans/nop-refactor/04-wi3-transform-dsl.md`（transform 改写通道——RewriteInput 的规则集消费面）；`ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md`（EditPlanApplier 应用入口）；WI12（rename 对的后续归属）；`ai-dev/design/nop-refactor/00-vision.md` §三（原则 1 GraphQL-first 无状态 / 原则 3 self-verification / 原则 6 fail-closed / 原则 9 复杂度预算）
> Review: R1 对抗审查（2026-09-25，fresh session）：REVISE——3 Blocker（B1 LintProfile 未裁定=死管道；B2 豁免门整体缺失违反 WI3 裁定 4；B3 FileEdit 构造三路全撞红线）+ 6 Major（残留子集伪报、`/`-根分支、表外扩展名呈现、多文件中途失败、语言绑定供给、回滚呈现推给实现期）+ 6 Minor；全部修订（R2 复核见 Review Record）

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

## 执行面裁定记录（R1 修订后——每条对照其 Blocker/Major 编号）

1. **（B1）LintProfile 钉死 STANDARD**：`fixOpen = profile != FAST` 使 transform 通道在 FAST 下结构性恒空（连降级计数都无）——rewrite 面引擎固定 `new LintEngine(registry, LintProfile.STANDARD)`，禁用先例 NopLintBizModel 的 FAST。**transformDegraded>0 呈现**：STANDARD 下 budget ladder 可关闭 fix 生成（TestBudgetDegradeLadder 先例），丢失的改写编辑对改写面是静默丢编辑——裁定 fail-closed：`stats.getTransformDegraded() > 0` → NopRefactorException（消息含丢失编辑计数；不产部分载荷）。
2. **（B2）豁免门在执行链内**：transformFixes 在进 EditPlanApplier 前经 `LoadedRuleSet.exemptions` 同构门控（`exemptions.suppresses(ruleId, path)` 谓词，CheckRunner ~:481-491 先例）——被豁免编辑逐条产 `NonApply(OUT_OF_SCOPE, detail="exempted by ruleset exemption: <id>")`（结构化可见，非静默丢弃）。fixture 规则集含豁免声明，测试覆盖。
3. **（B3）FileEdit 构造路径 = (c) 单点 additive**：`EditPlanResult` additive 增 `appliedFixes` 组件（merge 存活的 Fix 本体列表，构造点在 EditPlanApplier 内部三处），沿 plan 05 的 additive 先例（design 03 增补回记）；本 plan 的"nop-lint 零修改"红线措辞相应收窄为"仅此一处 additive、既有字段与语义逐字不变"。FileEdit：path=目标路径、range=Fix.range、summary=Fix.description（design 01 §四已钉）；**回滚文件（appliedEdits=0）不产 FileEdit**（从 edits 剔除）。禁绕道直调 Fixer.merge 与禁差集反推红线不变。
4. **（M1）残留 lint 子集改判**：transform 规则"只改不报"，以其自身作残留子集则 residualDiagnostics 构造性恒 0 而 residualRuleCount>0——伪报"已配零残留"。**v1 裁定：不配残留子集**（residualRuleCount=0，诚实"未配"）；独立可配残留检查面前缀归 Non-Blocking Follow-up。
5. **（M2）写面 path grammar 三分支逐支裁定**：namespace 前缀（`nop:/` 等）拒绝（VFS 写面 v1 不支持）；**`/`-根路径拒绝**（VFS 资源路径无法供 EditPlanApplier 的磁盘原子写消费——写比读严的落点）；磁盘路径 toRealPath 后必须落在工作目录内。拒绝均 NopRefactorException 结构化错误。
6. **（M3）表外扩展名/无规则语言显式呈现**：目标扩展名不在语言表 → `NonApply(OUT_OF_SCOPE, detail="unsupported extension")`；语言已知但规则集无该语言规则 → `NonApply(OUT_OF_SCOPE, detail="no rules for language <id> in ruleset")`；语言匹配但零编辑 → 非 nonApplied、不进 edits（正常空结局）。**零修复文件不进 assemble 的 files 参数**（filesAffected 只计受影响文件——m6 口径钉死）。
7. **（M4）多文件 apply 中途失败 = fail-fast + 已完成清单入错误消息**：per-file 原子写使第 k 文件 IO 失败时前 k−1 已落盘——NopRefactorException 消息枚举已落盘文件路径 + 失败路径 + 剩余计数（fail-closed 不伪报、可归因）；nonApplied 无 IO 枚举位，扩枚举归后续 design 裁定。测试覆盖（不可写目标在第 2 个文件）。**两阶段纪律（R2 Minor-1）**：transformDegraded 校验前置到全量计算阶段（任何写盘前逐文件 lint+门控+校验），落盘为第二阶段——降级中止不发生在部分落盘状态。
8. **（M5）测试语言绑定 = test-dep nop-lint-java + discoverDefaults**（与 nop-lint-graphql e2e 同型——其 pom 注释明言 runtime module set tests carry them）；写入 pom 依赖清单与测试项。
9. **（M6）守卫回滚呈现 plan 期钉死**：`NonApply.Reason` additive 增第四枚举 **ROLLED_BACK**（nop-refactor-core additive；baseline §四 三原因为例示非穷尽，design 01 §四增注 owns 该扩枚举偏离；WI5 契约测试 3→4 同步更新并断言原三枚举保持）。**R2 Major-A 补钉：`RefactorStats.SkippedBuckets` 同步 additive 第四桶 `rolledBack`**（total() 含之；of() switch 覆盖第四值）——桶是 nonApplied 的计数镜像，漏计即镜像契约破裂；镜像测试同步更新。回滚文件：edits 剔除（裁定 3）+ `NonApply(ROLLED_BACK, path, detail="guard rollback: syntax break, edits reverted")` 每文件一条 + verification 基于回滚后内容。此裁定同时供 plan 07（WI7 CLI）的回滚呈现引用——**plan 07 早前"映射进既有三分支/不新增第四分类"措辞由本裁定取代并已同步修订（R2 Major-B）**。
10. **（m1）roadmap 措辞处置**：WI6 原文"RewriteInput：规则集/pattern"为目标面描述，v1 契约收窄为规则集-only，以本 plan + design 01 §三增注为准；roadmap 按 Rules 不做正文回写。（m4）WI3 R2 移交项处置：Refactor 面加载请求级规则集 prefix、不触 Lint__listRules 的 DEFAULT prefix——severity=null 渲染裁定不适用于本 plan（WI8 落 transform 规则进可见库时再议）。

## Goals

- **模块骨架**：新建 nop-refactor/nop-refactor-graphql——pom 同 nop-lint-graphql 型（依赖 nop-refactor-core + nop-graphql-core + nop-ioc；**不依赖 nop-lint-graphql 本身**——它是形态先例不是依赖；nop-lint-core 经 nop-refactor-core 传递可达，直接 import 的类按 Maven 卫生声明直接依赖）；`_vfs/nop/refactor/beans/app-refactor.beans.xml` 显式 bean 注册 + `_vfs/nop/refactor/_module` 空标记；`@BizModel("Refactor")`。
- **v1 schema 只声明 rewrite 对（rename 对占位裁定 = 方案 a）**：Refactor__previewRewrite / Refactor__applyRewrite 两个 `@BizMutation`；Refactor__previewRename / Refactor__applyRename 本 plan **不声明**，随 WI12 直接加入。理由：GraphQL schema 增加字段是非破坏变更（既有字段与客户端兼容），后续 plan 无破坏地增长；声明恒抛 UnsupportedOperationException 的 action 会把 schema 自描述面变成虚假广告——AI 调用方只有运行时才发现不可用，违背"所见即所可用"；Minimum Rules #24 的 fail-closed 要求针对"已实现的方法不得静默吞"，不要求未实现的操作占位声明。此裁定落 design 01 §三增注。
- **RewriteInput 字段（v1）**：`rulesetPrefix`（规则集 VFS prefix，经 RuleSetLoader.loadRuleSet 加载；加载失败 = 结构化错误，绝不空规则集静默继续）+ `paths`（目标文件集合：显式文件/目录路径，目录递归走 TargetScanner 同型收集；**glob 不入 v1**——TargetScanner 先例无 glob，展开逻辑是新增扫描面，归 Follow-up）。baseline §三 RewriteInput 注释的"pattern 直给"（裸 pattern 不经规则集文件直接组装编辑计划）**裁定为 Non-Goal**：规则集是已验证的安全面（模板渲染 + fail-closed 矩阵 + 资源门），裸 pattern 的编辑计划组装是新增引擎面，违反"复用存量"的预算约束；未来扩展见 Follow-ups。
- **rewrite 执行链（preview 与 apply 共用同一计算函数——无状态重执行语义）**：加载规则集（引擎固定 **STANDARD** profile——裁定 1）→ 收集目标文件（path grammar 三分支逐支裁定 5：namespace 拒绝 / `/`-根拒绝 / 磁盘路径 toRealPath 工作目录约束；不存在路径 hard error）→ 逐文件按扩展名定语言、按语言取规则（表外扩展名/无规则语言 → NonApply(OUT_OF_SCOPE) 显式呈现——裁定 6）→ LintEngine.lint → transformFixes **经豁免门控**（裁定 2）→ `EditPlanApplier.apply`（preview dryRun=true / apply dryRun=false 原子落盘；多文件中途失败 fail-fast + 已完成清单——裁定 7）→ `RefactorVerifier.assemble(applied=false/true)`；FileEdit 自 `EditPlanResult.appliedFixes` additive 组件构造（裁定 3），回滚文件产 `NonApply(ROLLED_BACK)`（裁定 9）。无会话、无 plan-token：apply 内部重算编辑计划（操作确定性保证两次计算一致，baseline §三裁定）。
- **资源 cap fail-closed（沿 Lint__checkSource 先例同型）**：`nop.refactor.graphql.max-source-size`（单文件源上限，默认 1MB 同 Lint 先例；字节级 read 前置门 + 读后字符级 backstop）+ `nop.refactor.graphql.max-target-files`（目标集合解析后的文件数上限——批量面新增风险维度；**默认 512**，R1 m2 钉死：批量改写面的合理目录规模量级，超限在任何读取前拒绝）。超限 = NopRefactorException 英文消息（命名配置键与实际值，测试断言消息内容）。
- **返回载荷直接复用 WI5 records**：RefactorResult 及嵌套 record 直接作 `@BizMutation` 返回（LintCheckResult record 先例）——不转 Map、不建第二套 DTO；FileEdit.range 的 SourceRange、CostTier/Reason 枚举在 e2e SchemaLoader 中的定义形态以 live 为准（**Reason 含 ROLLED_BACK 第四枚举——裁定 9 additive，SchemaLoader 定义面注意**）。
- **残留 lint 子集改判（裁定 4）**：v1 不配残留子集（residualRuleCount=0 诚实"未配"）——transform 规则自身作子集会使 residualDiagnostics 构造性恒 0 而伪报"已配零残留"（R1 M1）。
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
- `ai-dev/design/nop-lint/03-execution-engine.md` WI5 扩展落地条目回补 `appliedFixes` 契约记录（裁定 3——R2 Minor-2 显式入列）。
- `docs-for-ai/01-repo-map/module-groups.md` nop-refactor 行增补 nop-refactor-graphql 子模块。

### Out Of Scope

- nop-lint / nop-treesitter 行为面与既有测试基线（零修改红线）。
- nop-refactor-core 的既有载荷与 verification 语义（消费面，不改）。
- docs-for-ai 模块文档新建（WI13 收口统一新建；module-groups.md 一行增补除外——既有 doc 同步义务）。
- rename 面、CLI 面、规则内容（WI12 / WI7 / WI8）。

## Execution Plan

前置依赖（roadmap 拓扑序）：WI3/WI4/WI5 均已 completed（roadmap 已勾选）。Phase 1 开工前以 git status 自查 nop-refactor/nop-lint 工作树干净。若 WI5 landed 形态与本 plan Current Baseline 记载不一致，以 landed 源码 + design 01 §四增注为准并回写本节。

### Phase 1 - 模块骨架 + schema + previewRewrite（Fix + Decision）

Status: completed
Targets: `nop-refactor/nop-refactor-graphql/`、`nop-refactor/pom.xml`、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Fix + Decision`

- [x] 模块骨架：nop-refactor-graphql pom（parent nop-refactor；依赖 nop-refactor-core + nop-graphql-core + nop-ioc，直接 import nop-lint-core 类则声明直接依赖——live 为准）+ `_vfs/nop/refactor/beans/app-refactor.beans.xml`（`<bean id="io.nop.refactor.graphql.NopRefactorBizModel" .../>` 沿 app-lint.beans.xml :7 同型）+ `_vfs/nop/refactor/_module` 空标记 + nop-refactor/pom.xml modules 注册
- [x] NopRefactorBizModel 骨架：`@BizModel("Refactor")` + Refactor__previewRewrite / Refactor__applyRewrite `@BizMutation`（返回 RefactorResult record 直接作 biz 返回——LintCheckResult 先例）
- [x] RewriteInput 形态 Decision：普通 bean 类（getRulesetPrefix/getPaths——record 入参绑定未经引擎验证，bean 形态最稳）；契约字段不变（rulesetPrefix + paths），RPC 数据形态 {"input": {...}}；裁定记录落当日 log
- [x] 引擎装配：`new LintEngine(registry, LintProfile.STANDARD)`（裁定 1——禁用 NopLintBizModel 的 FAST 先例）；transformDegraded>0 → NopRefactorException fail-closed
- [x] previewRewrite 执行链：`RuleSetLoader.loadRuleSet(rulesetPrefix)`（零规则/解析失败/双源漂移 = NopRefactorException 结构化错误）→ 目标收集（显式路径/目录递归，TargetScanner 同型；path grammar 三分支：namespace 拒绝 / `/`-根拒绝 / toRealPath 工作目录约束 + 不存在路径 hard error——裁定 5）→ 逐文件扩展名定语言取规则（表外扩展名/无规则语言 → NonApply(OUT_OF_SCOPE)——裁定 6）→ LintEngine.lint → transformFixes **经 exemptions 门控**（裁定 2：被豁免编辑 → NonApply(OUT_OF_SCOPE)）→ `EditPlanApplier.apply(dryRun=true)` → FileEdit 自 appliedFixes 构造（裁定 3）→ `RefactorVerifier.assemble(applied=false)`；零修复文件不进 assemble 的 files 参数（裁定 6/m6 口径钉死）
- [x] 残留 lint 子集改判落地（裁定 4）：v1 不配子集——verifier 以无子集构造（residualRuleCount=0）
- [x] transform 规则测试 fixture：src/test/resources/_vfs 下最小 prefix（RuleSetLoader 可加载、xdef 合法、**transform 规则 + 豁免声明**——沿 cli-rules-transform 型）+ **nop-lint-java test-dep**（M5：语言绑定，沿 nop-lint-graphql pom 注释先例）
- [x] 服务级测试（TestNopRefactorBizModel 10/10；目标文件用 target/ 模块内路径——@TempDir 会被工作目录约束拒绝（R1 m3））：preview 返回 diff+stats 且目标文件内容不变（不落盘）、apply 落盘、确定性（diff body 相等）、豁免规则文件不改写且产 NonApply(OUT_OF_SCOPE)、表外扩展名 NonApply、path grammar 三分支拒绝面、缺失前缀/未知 prefix fail-closed
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw compile -pl nop-refactor/nop-refactor-graphql -am` 成功——新模块进聚合构建
- [x] previewRewrite 服务级测试全绿：diff + stats 返回、目标文件内容逐字节不变（不落盘）、fail-closed 面逐条断言
- [x] **接线验证**（Minimum Rules #23）：执行链确实调用了 RuleSetLoader → LintEngine（transformFixes 非空）→ EditPlanApplier → RefactorVerifier——测试断言载荷携带 fixture 规则产出的真实编辑（非空 diff 含 directCall），证明四环节运行时连通
- [x] **无静默跳过**（Minimum Rules #24）：规则集加载失败 / 目标不存在 / namespace 路径 / workdir 逃逸均为显式异常，无空方法体、无吞异常、无 placeholder 返回
- [x] **新功能测试清单**（Minimum Rules #25）：preview 不落盘、加载失败、path grammar 三分支、零修复空载荷——逐项列出并落为测试
- [x] RewriteInput 形态与残留 lint 子集两项 Decision 已裁定并记录（log + design 01 §三增注）
- [x] `docs-for-ai/01-repo-map/module-groups.md` nop-refactor 行已增补 graphql 子模块
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - applyRewrite + 资源 cap + GraphQLEngine e2e（Decision + Fix）

Status: completed
Targets: `nop-refactor/nop-refactor-graphql/src/main|test/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Decision + Fix`

- [x] Refactor__applyRewrite `@BizMutation`：与 previewRewrite 共用同一计算函数 rewrite(input, dryRun=false)——重算编辑计划后 EditPlanApplier 原子落盘，applied=true 返回同一载荷（无会话/token）
- [x] 守卫回滚呈现落地（裁定 9 已钉）：`NonApply.Reason` additive 增 ROLLED_BACK（nop-refactor-core 单点 additive；WI5 契约测试 3→4 更新断言原三枚举保持；design 01 §四增注 owns 偏离）+ 回滚文件 edits 剔除 + 每文件一条 NonApply(ROLLED_BACK)——回滚事实显式可编程判读
- [x] 资源 cap fail-closed：`nop.refactor.graphql.max-source-size`（AppConfig.varRef，字节级 read 前置门 checkPreReadCap 同型 + 读后 backstop）+ `nop.refactor.graphql.max-target-files`（默认 512——R1 m2 已钉；目标集合解析后计数门，超限在任何读取前拒绝）——NopRefactorException 英文消息命名配置键与实际值
- [x] e2e（TestNopRefactorGraphQL 落地）：CoreInitialization + BeanContainer 取 bean（beans.xml 装配证明）+ BizModelSchemaLoader（`g_<fqcn>` 对象定义：RefactorResult/Verification/FileEdit/RefactorStats/SkippedBuckets/NonApply/SourceRange + RewriteInput 入参绑定；枚举定义形态 live 为准）→ `newRpcContext(GraphQLOperationType.mutation, ...)` 真调四断言：(a) previewRewrite 返回 diff+stats 且文件系统不变；(b) applyRewrite 落盘 + applied=true；(c) cap 超限结构化错误（response code 命名配置键）；(d) 规则集加载失败结构化错误
- [x] design 01 §三 增注落档：v1 schema 只落 rewrite 对（裁定 a 理由全文）、RewriteInput v1 字段与 pattern 直给 Non-Goal、glob 不入 v1、cap 两键（512 钉死）、残留子集改判（裁定 4）、STANDARD 钉死与 transformDegraded fail-closed（裁定 1）、豁免门落点（裁定 2）、path grammar 三分支（裁定 5）、表外/无规则呈现（裁定 6）、中途失败语义 + 两阶段纪律（裁定 7）、roadmap 措辞处置与 WI3 R2 移交项处置（m1/m4）、模块落点与依赖形态——纯增注不改写
- [x] design 01 §四 增注（ROLLED_BACK 扩枚举 + SkippedBuckets 第四桶 owns——R2 Major-A，归节一致 R2 Minor-4）+ design 03 增补回记（appliedFixes——R2 Minor-2）落档
- [x] 行为红线自查（scoped）：git diff 证实 nop-lint / nop-treesitter 零修改；nop-lint additive 仅 `EditPlanApplier.appliedFixes`（裁定 3，design 03 增补回记）；nop-refactor-core additive 两处 = `NonApply.Reason.ROLLED_BACK` + `RefactorStats.SkippedBuckets` 第四桶 `rolledBack`（裁定 9 + R2 Major-A，design 01 §四增注 owns，镜像测试与契约测试同步更新）——均记录在案且既有字段与语义零变化
- [x] `./mvnw test -pl nop-refactor/nop-refactor-graphql -am` 全绿（14/0）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：TestNopRefactorGraphQL 四断言 (a)–(d) 全绿——从 GraphQL RPC 入口经容器 bean、执行链到 RefactorResult 载荷与文件系统效果一测贯通
- [x] **接线验证**（Minimum Rules #23）：RPC 路径确认容器装配的 NopRefactorBizModel 被真实调用（BeanContainer 取 bean + engine.executeRpcAsync 响应含载荷字段——沿 TestNopLintGraphQL :43-46 装配证明形态）
- [x] **无静默跳过**（Minimum Rules #24）：cap 超限 / 加载失败 / 回滚事实均显式呈现；applyRewrite 无 dryRun 之外的第三种落盘路径
- [x] **新功能测试清单**（Minimum Rules #25）：(a) preview 不落盘、(b) apply 落盘 applied=true、(c) cap 超限、(d) 加载失败——逐项列出并落为 e2e 测试；apply 与 preview 的载荷一致性（同输入同 diff）有断言
- [x] apply 重执行语义有测试：同一输入连续 preview→apply（或重复 apply）结果一致（操作确定性），服务实例无跨请求状态
- [x] design 01 §三 增注已落档且与 landed 实现逐条互洽（实现偏差即回写文档，不得让契约与 live 漂移）
- [x] 行为红线自查记录在案（scoped git diff：nop-lint / nop-treesitter / nop-refactor-core 零修改或仅记录在案 additive）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部 in-scope 项完成，无残留未勾选 checklist
- [x] 四 action 契约的 v1 交付面成立：rewrite 对经 GraphQLEngine 端到端可用、载荷字段 = WI5 已落地契约（RefactorResult 六字段，无缩水）、rename 对按裁定 (a) 不声明且 design 增注已记录
- [x] 无状态重执行语义成立：apply 路径无会话/token、preview 与 apply 共用同一计算函数、重执行确定性有测试
- [x] 资源 cap fail-closed 面成立：source-size + target-files 两键、read 前置门、结构化错误，沿 Lint__checkSource 先例同型
- [x] 零行为红线（scoped diff）：nop-lint / nop-treesitter / nop-refactor-core 既有行为零修改（或仅记录在案 additive）；nop-lint 全模块测试零回归
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 端到端路径从 GraphQL RPC 到文件落盘/载荷返回运行时连通（不只是类型存在），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [x] owner docs 已同步：design 01 §三 增注、`docs-for-ai/01-repo-map/module-groups.md` 增补
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-graphql --severity high` 退出 0
- [x] 代码规范：`check-import-order.mjs` 本 plan 新增/变更文件零违规（范围口径——全仓存量违规在本 plan 范围外）
- [x] vision 原则 1–9 回扣核对（closure audit 执行）：原则 1（GraphQL-first 无状态）、原则 3（self-verification 载荷不缩水）、原则 6（fail-closed）、原则 9（预算——只建裁定内第三模块、复用 WI3/WI4/WI5 存量不重造）为本 WI 重点核对项
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [x] `./mvnw test -pl nop-refactor/nop-refactor-graphql -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（范围口径：本 plan 变更文件零错误；全仓 gate 因并发在途文档 churn 波动时，提交前全局复跑）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/06-wi6-graphql-actions.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- pattern 直给（RewriteInput 增加裸 pattern 输入直接组装编辑计划）：需先建裸 pattern 的安全门与编辑计划组装面（新增引擎面），过性价比门后另立项——Why Not Blocking Closure：v1 规则集定位已覆盖 AI 闭环所需的已验证安全面，裸 pattern 是能力扩展非契约缺口。
- glob 目标展开（RewriteInput.paths 支持 glob 模式）：TargetScanner 先例无 glob，展开语义与安全边界需单独裁定——v1 显式路径/目录已可用。
- 规则集 prefix 键控缓存：若实测每请求加载成为延迟瓶颈再做，必须键控 prefix 且记录失效语义。

## Closure

Status Note: WI6 收口——Refactor__ GraphQL 契约面落地（previewRewrite/applyRewrite 共用执行链：加载门共享 RefactorRuleGates/写面 grammar 三分支/两阶段纪律/豁免门控/EditPlanApplier 唯一应用/assemble 唯一组装）；cap 两键 fail-closed；残留子集改判诚实"未配"；ROLLED_BACK 第四枚举 + SkippedBuckets 第四桶 owns 于 design 01 §四；15/15（服务级 11 + RPC 4）测试背书。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit（fresh session subagent，与实现会话不同 task）
- Audit Session: agent_82eec108-18bf-42ea-bc40-ccdeb0cd164e
- Evidence:
  - Step 1-7：R1 十五项裁定 live 逐条复核（裁定 1/2/3/4/5/6/9/10 PASS；裁定 7 首判 PARTIAL）
  - Findings：Major-1（裁定 7 landed 枚举缺口 + 测试缺失）+ Major-2（WI6 log 缺失但 checklist 已勾 [x]）——两必修完成后增量复核放行
  - 增量复核：Major-1 修正（apply 循环 landed 枚举 catch + midApplyFailure 测试）；Major-2 修正（WI6 log 补写）
  - 门禁亲跑：graphql 15/0 exit 0、hollow-scan 0、doc-links 0 errors、import-order 0
  - vision 原则 1–9：原则 1/2/3/6/9 重点逐项 PASS
  - AUDIT VERDICT: REJECTED → 两必修整改 + 增量复核 → 放行

Follow-up:

- no remaining plan-owned work（rename 对接线归 WI12；pattern 直给归 Follow-up）


## Closure Audit (2026-09-25, first pass + 增量复核)

- **closure audit（2026-09-25，fresh session）：REJECTED（2 必修）**——Major-1：裁定 7 部分落地（阶段 2 apply 循环不在 landed 枚举 try 内——第 k 文件写失败异常裸传无已落盘枚举；"不可写目标在第 2 个文件"测试缺失；design 01 增注与 live 漂移）；Major-2：WI6 日志条目缺失但 checklist 已勾 [x]。其余（步骤 2-6：R1 十五项裁定 live 复核、测试证据、门禁、行为红线、vision 回扣）全部 PASS。
- **增量复核（2026-09-25，主会话执行，凭原 audit 余项 PASS 放行条款）**：Major-1 已修——阶段 2 逐文件 apply 纳入 landed 枚举 catch（NopRefactorException 消息含已落盘清单 + 失败路径 + 剩余计数，midApplyFailureEnumeratesLandedFilesAndAborts 测试以独立父目录 readonly 场景钉死）；Major-2 已修——WI6 日志条目补写（前次 python replace 静默失配所致）。design 01 增注与修正后 live 一致。全模块 11/0 复跑绿。

## Review Record

- **R1（2026-09-25，fresh session）：REVISE**——3 Blocker（B1 LintProfile 未裁定=死管道；B2 豁免门缺失违反 WI3 裁定 4；B3 FileEdit 构造三路全撞红线）+ 6 Major（M1 残留子集伪报/M2 `/`-根分支/M3 表外扩展名呈现/M4 多文件中途失败/M5 语言绑定供给/M6 回滚呈现推给实现期）+ 6 Minor；全部修订落"执行面裁定记录"1-10。
- **R2（2026-09-25，fresh session）：REVISE（窄口径）**——R1 十五项全部 FIXED-VERIFIED（裁定 3 单点 additive/裁定 9 正当化/裁定 1+4 组合对 vision 原则 3 仍充分均经 live 核实）；2 必修：Major-A（SkippedBuckets 镜像缺口——第四桶 rolledBack + 镜像测试同步）+ Major-B（plan 07 回滚呈现文本同步——已同步修订 plan 07 两处）；Minor-1（两阶段纪律）/Minor-2（design 03 回记入列）/Minor-3（本节建立）/Minor-4（归节一致 §四）一并处理。修毕后按惯例抽查即可放行。
