# 05 WI5 RefactorResult verification 载荷——self-verification 契约落地

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M1 WI5 原文 + Cross-Cutting 完成判定 + Framework/Platform Reuse 表 + Current Baseline）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §四（RefactorResult 载荷契约——字段不得缩水）与 §一.3（check/plan/apply/verify 四段契约）
> Related: `ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md`（WI4 入口契约六要素 + per-conflict 扩展点裁定，本 plan 消费其结果载荷）；`ai-dev/design/nop-refactor/00-vision.md` §三（原则 3 self-verification 载荷）；WI6（GraphQL 面将消费本载荷）；WI9（操作框架复用本载荷与 verification 计算面）
> Review: R1 对抗审查（2026-09-25，fresh session）：REVISE——4 Major（conflict 上下文路由未裁定且与红线措辞矛盾；与 WI3 并行冲突未声明；Phase 1 owner-doc 裁定不成立——module-groups.md 漂移；WI4→WI5 的 Fix.ruleId/description 交接裁定遗失）+ 5 Minor；全部修订。R2 复核（2026-09-25，fresh session）：APPROVE——九项逐条 FIXED-VERIFIED（conflict 路由 live 可实现性证实：MergeResult 构造点仅 Fixer.java:61 一处、EditPlanResult 仅 EditPlanApplier 内部 3 处，全在允许变更文件内）；WI3/WI4 双前置 completed 且 nop-lint 基线干净；载荷契约逐字段无缩水；一条非阻塞措辞观察（行 20"经语言适配暴露"按两文件红线合规读法执行——本模块内复刻计数逻辑）。进入执行。

## Purpose

执行 roadmap WI5（Item Type: Fix）：把 baseline §四 的 self-verification 载荷契约为可执行 Java 面——被改文件重解析（parseOk / errorNodeCount，经 nop-treesitter）+ 残留 lint（可配规则子集，经 nop-lint 引擎）+ stats + nonApplied 分类。字段契约 = baseline §四，不得缩水（逐字段契约测试把"缺一即缩水"可执行化）。本 WI 同时裁定并创建 nop-refactor 模块骨架（聚合目录 + nop-refactor-core），为载荷与 verification 计算面提供落点，WI9 在其中建操作框架。

## Current Baseline

（live 已核对，2026-09-25；行号为核对时快照，执行时以 live 为准）

- **nop-refactor 模块尚不存在**：仓库根无 nop-refactor 目录，根 `pom.xml` modules 清单无注册。模块拓扑由 baseline §二 裁定：nop-refactor-core + nop-refactor-java + nop-refactor-graphql 三模块。**本 plan 裁定：创建 nop-refactor/ 聚合目录 + nop-refactor-core 模块骨架（pom + 包结构），承载载荷与 verification 计算面；WI9 在其中建操作框架**。拓扑不超出 baseline 裁定的三模块（复杂度预算内）；nop-refactor-java / nop-refactor-graphql 留给后续 WI。
- **aggregator 形态先例**：`nop-lint/pom.xml`（parent io.github.entropy-cloud:nop-entropy 2.0.0-SNAPSHOT、packaging pom、module 清单）——nop-refactor 聚合 pom 沿同型；根 pom modules 清单需新增注册。
- **WI4 已 completed（2026-09-25）**：plan 03 定稿 completed、独立 closure audit PASS、roadmap WI4 已勾选。入口 `EditPlanApplier.apply(Path, byte[], List<Fix>, LintLanguage, boolean dryRun)` → `EditPlanResult(finalSource, rolledBack, appliedEdits, skippedConflicts)`（nop-lint-core fix 包；FixApplier 每轮机械核已经它执行）。**conflict 上下文路由裁定（R1 Major-1）**：被跳过编辑本体经 **`Fixer.MergeResult` additive 扩展**获得（新增被跳过 Fix 列表组件，既有构造点仅 Fixer.java:61 一处），`EditPlanApplier` 结果载荷随之 additive 透传——**允许变更范围 = `Fixer.MergeResult` + `EditPlanResult` 两处 additive 扩展**（既有字段与语义逐字不变），design 03 增补同步覆盖两者；**红线：除此之外 nop-lint 既有类零修改，禁止绕道直调 `Fixer.merge` 自行组装第二条合并路径，禁止 input−applied 差集反推被跳过列表（把"谁被跳过"的知识复制到 merge 权威之外）**。
- **nop-treesitter（只读消费）**：重解析经 `LintLanguage` 抽象消费——`LintLanguage.parse(byte[]) → LintTree` 底层即 nop-treesitter 纯 Java GLR 引擎（blob 内置 java/ts/tsx）。error-node 计数形态参照 `EditPlanApplier.countErrorNodes`（私有静态，同型复刻到本模块或经语言适配暴露）：遍历树、计 kind=="ERROR" 或 isMissing 的节点（注意经 kindId 判定不可行——恢复节点的 kindId 为 -1，按 kind 字符串/isMissing 判定）。
- **nop-lint 引擎规则子集入口（只读消费）**：`LintEngine.lint(List<RuleDslModel> rules, LintLanguage language, String filePath, String source)`（engine/LintEngine.java ~:155，另有 lintCompiled 等 overload）——显式规则子集对单文件/单内容运行，返回 `LintResult`（diagnostics 即 residualDiagnostics 来源）。规则子集以显式列表传入，复用既有引擎/profile/统计面（roadmap Reuse 表约束）。
- **diff 面**：`UnifiedDiff.of(String path, String original, String fixed)`（nop-lint-core fix 包，public static）——直接复用，不新建第二 diff 实现。
- **包名与错误处理惯例**：包名沿 io.nop.&lt;module-name&gt;；错误处理沿两层策略（`docs-for-ai/02-core-guides/error-handling.md`）：模块内部异常用英文消息，公共契约面结构化错误。
- roadmap Purpose 已裁定执行路径统一（防双引擎）：codemod 面复用 WI4 应用入口 + 本 WI 载荷；WI9 框架化同一路径。

## Goals

- **载荷类型面**（Java 形态对应 baseline §四 GraphQL 契约，字段清单逐项对照、缺一即缩水）：
  - RefactorResult：applied / edits / diff / verification / stats / nonApplied（baseline §四六字段全量）
  - Verification：parseOk / errorNodeCount / residualDiagnostics / symbolIntact——**symbolIntact 本 plan 置 null 语义：仅 rename 类操作填，codemod 面不产**（baseline §四 nullable Boolean 的 v1 具象，正式计算归 WI9–WI12）
  - FileEdit：path + 范围 + 变更摘要（baseline §四注释三要素）
  - RefactorStats：影响文件数 / 编辑数 / 跳过分桶 / 耗时档位（baseline §四注释四要素；耗时档位边界属实现裁定，随 design 01 增注落档）
  - NonApply：原因枚举 conflict / out-of-scope / unresolved-target + 上下文（baseline §四"原因枚举 + 上下文"）
- **verification 计算面**：输入 = 编辑后文件三元组集合（path + **原内容** + 编辑后内容——原内容供 diff 组装，R1 Minor-2）+ 可配规则子集 → 重解析计数（经 nop-treesitter）+ 残留 lint 计数（经 nop-lint 引擎规则子集；**规则子集为空/未配时 residualDiagnostics=0 且该状态显式可判读——不是静默跳过**，"未配"与"配了零残留"机器可区分，呈现形态为 Phase 2 Decision 项）+ 耗时档位
- **diff 面**：复用 nop-lint-core 的 UnifiedDiff，逐文件组装进 RefactorResult.diff
- **WI4 per-conflict 扩展（路由已裁定）**：`Fixer.MergeResult` additive 扩展被跳过 Fix 列表 + `EditPlanResult` additive 透传 + design 03 增补两者；nonApplied(conflict) 上下文实产消费该列表
- **FileEdit 取值语义（WI4→WI5 交接裁定，R1 Major-4）**：FileEdit 的来源标识/变更摘要取自编辑载体的通用字段——`Fix.ruleId` → 来源（lint 规则 id 或 refactor 操作/编辑类别 id）、`Fix.description` → 变更摘要；落 design 01 §四增注，闭合 design 03 的语义留白
- **零行为红线**：nop-lint / nop-treesitter 既有行为零修改（只读消费）

## Non-Goals

- 不做 GraphQL 面（WI6）、CLI 批处理形态（WI7）。
- 不做 rename / symbolIntact 计算（WI9–WI12）——本 plan 只锁定其可空类型位与 null 语义。
- 不做操作框架 check/plan/apply/verify 四段框架化（WI9）。
- 不修改 nop-lint / nop-treesitter 任何既有行为（只读消费；唯一例外 = `Fixer.MergeResult` 与 WI4 入口结果载荷 `EditPlanResult` 的 additive 扩展字段，per plan 03 扩展点裁定与本 plan Current Baseline 路由裁定，既有字段与语义逐字不变）。
- 不建 nop-refactor-java / nop-refactor-graphql 模块（后续 WI）。
- 不做批量操作编排（多文件编辑计划的产生归消费方；本 plan 的 verification 计算面只消费"编辑后文件集合"）。

## Scope

### In Scope

- 新建 `nop-refactor/pom.xml`（聚合）+ `nop-refactor/nop-refactor-core/`（pom + main/test 包结构）。
- 载荷类型面（RefactorResult / Verification / FileEdit / RefactorStats / NonApply）+ 逐字段契约测试。
- verification 计算面（重解析 / 残留 lint / stats / diff 组装）+ 焦点测试 + 端到端测试。
- `nop-lint/nop-lint-core` fix 包：`Fixer.MergeResult` 与 WI4 入口结果载荷 `EditPlanResult` 的 additive 扩展（被跳过编辑列表）——既有字段与语义零变化；扩展契约增补进 `ai-dev/design/nop-lint/03-execution-engine.md`。
- `ai-dev/design/nop-refactor/01-architecture-baseline.md` §四：v1 语义细化增注（多文件聚合口径、parseOk per-file 谓词、规则子集未配的判读形态、symbolIntact null 语义、FileEdit 取值语义、耗时档位 v1 钉进程内）。
- `docs-for-ai/01-repo-map/module-groups.md`：nop-refactor 模块组一行增补（R1 Major-3——活体 routing owner doc 不得失真）。
- 根 `pom.xml` modules 注册 nop-refactor。

### Out Of Scope

- `ai-dev/design/nop-lint/03-execution-engine.md` 的既有内容（只增补 WI5 扩展契约记录，不改写）。
- nop-lint / nop-treesitter 的任何行为面与既有测试基线（除裁定允许的两处 additive 扩展）。
- docs-for-ai 模块文档新建（按 roadmap WI13 收口统一新建；module-groups.md 的一行增补不属此类，是既有 doc 的同步义务）。

## Execution Plan

前置依赖（roadmap 拓扑序）与执行顺序（R1 Major-2/Minor-4）：

- **WI4（plan 03）已 completed + roadmap WI4 已勾选**——本 plan 的开工门槛已满足。
- **与 WI3（plan 04）串行**：两者都改 nop-lint-core fix/engine 面且都向 design 03 增注。裁定执行顺序 = **WI3 先、WI5 后**：WI5 Phase 2 开工前置 = WI3 已 completed 且 nop-lint 基线干净（git status 无 nop-lint 在途改动）；红线自查与全测 gate 因此可判读（并行在途时不得开工 Phase 2，保持 blocked 等待）。

若 WI4 landed 形态与本节记载不一致，以 landed 源码 + design 03 增注为准并回写本节 baseline。

### Phase 1 - 模块骨架 + 载荷类型面（Fix）

Status: completed
Targets: `nop-refactor/pom.xml`、`nop-refactor/nop-refactor-core/`、根 `pom.xml`、`nop-refactor/nop-refactor-core/src/test/`

- Item Types: `Fix`

- [x] nop-refactor 聚合 pom + nop-refactor-core 模块 pom：沿 nop-lint aggregator 同型（parent / packaging pom / module 清单）；nop-refactor-core 依赖 nop-lint-core（UnifiedDiff / LintLanguage / LintEngine / RuleDslModel 消费面随之可用，nop-treesitter 经其传递可达）；包结构沿 io.nop.refactor.core 惯例
- [x] 载荷类型面：RefactorResult / Verification / FileEdit / RefactorStats / NonApply（含三分支原因枚举 + 上下文承载）+ NopRefactorException（模块异常，沿 NopLintException 同型）+ EditedFile（path/original/edited/editCount 三元组——R1 Minor-2）
- [x] 逐字段契约测试（TestRefactorPayload 6/6）：record 组件数断言钉死字段集（RefactorResult 6、Verification 4、FileEdit 3、RefactorStats 4+residualRuleCount、NonApply 3 枚举）+ symbolIntact null 语义 + NonApply/FileEdit fail-closed 构造 + SkippedBuckets 镜像
- [x] 根 `pom.xml` 注册 `<module>nop-refactor</module>`
- [x] `docs-for-ai/01-repo-map/module-groups.md` nop-refactor 模块组一行增补（模块名 + 一句话定位——与 nop-lint 行同型）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw compile -pl nop-refactor/nop-refactor-core -am` 成功——新模块进聚合构建，根 pom 注册生效
- [x] 逐字段契约测试全绿，覆盖清单与 baseline §四 逐项可对照（closure audit 复核无缺字段）
- [x] **无静默跳过**（Minimum Rules #24）：NonApply 构造的 fail-closed 语义有测试——缺上下文/未知原因的构造显式失败（异常形态沿 error-handling 两层策略的模块内英文消息惯例），不是静默接受
- [x] **新功能测试清单**（Minimum Rules #25）：载荷类型逐字段断言 + NonApply fail-closed 构造，均已列出并落为测试（TestRefactorPayload）
- [x] owner-doc 更新：`docs-for-ai/01-repo-map/module-groups.md` 已增补 nop-refactor 模块组行（design 01 §二 三模块拓扑裁定已先行覆盖模块骨架本身；docs-for-ai 模块文档新建归 WI13）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - verification 计算面 + WI4 扩展点衔接（Decision + Fix）

Status: completed
Targets: `nop-refactor/nop-refactor-core/src/main|test/`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/`（仅 WI4 入口载荷 additive 扩展）、`ai-dev/design/nop-refactor/01-architecture-baseline.md`、`ai-dev/design/nop-lint/03-execution-engine.md`

- Item Types: `Decision + Fix`

- [x] WI4 per-conflict additive 扩展（路由已裁定）：`Fixer.MergeResult` 新增被跳过 Fix 列表组件（构造点仅 merge 尾一处；skippedConflicts() accessor 保持，既有消费方零改动）+ `EditPlanApplier.EditPlanResult` 增 skippedEdits 组件 additive 透传（既有字段与语义逐字不变），design 03 增补落地；nonApplied(conflict) 上下文实产消费该列表（TestRefactorVerifier.conflictNonAppliesComeFromTheEntrySkippedList）——**未绕道直调 Fixer.merge、未差集反推**
- [x] verification 计算面（RefactorVerifier）：输入 = EditedFile 三元组集合（path + 原内容 + 编辑后内容 + editCount）+ 可配规则子集 → 逐文件重解析（经 LintLanguage.parse → nop-treesitter；error-node 计数本模块内同型复刻——两文件红线合规读法）+ 逐文件残留 lint（LintEngine 规则子集入口 :155）→ 聚合 parseOk / errorNodeCount / residualDiagnostics + RefactorStats（IN_PROCESS 档位）
- [x] 多文件聚合口径 + parseOk per-file 谓词 Decision：parseOk = AND；单文件谓词 = 重解析后无 ERROR/missing 节点；pre-existing 恢复节点 = 文件自身状态（alreadyBrokenSourceReportsItsOwnStateNotADelta 测试钉死）；errorNodeCount/residualDiagnostics 求和；解析异常 fail-closed 抛 NopRefactorException——落 design 01 §四增注
- [x] 规则子集空/未配呈现形态 Decision：RefactorStats.residualRuleCount 区分"未配"（0）与"配了零残留"（>0 且 residual=0）——unconfiguredSubsetIsDistinctFromConfiguredClean 测试钉死 + design 01 §四增注
- [x] symbolIntact null 语义落档（codemod 面不产、仅 rename 类操作填，WI9–WI12 接管）——design 01 §四增注
- [x] FileEdit 取值语义落档（交接裁定）：来源标识 ← Fix.ruleId、变更摘要 ← Fix.description——design 01 §四增注，闭合 design 03 留白
- [x] 耗时档位语义落档：v1 codemod 恒标 IN_PROCESS（vision 原则 7），OUT_OF_PROCESS 为未来预留——design 01 §四增注
- [x] diff 组装：逐文件复用 UnifiedDiff.of（path, 原内容, 编辑后内容），仅 changed 文件入 diff，聚合进 RefactorResult.diff
- [x] 新功能测试清单（显式）：(a) 语法破坏 → parseOk=false + errorNodeCount>0（brokenSyntaxFailsParseWithCountedRecoveryNodes）；(b) 干净 → parseOk=true + 0（cleanSyntaxParsesWithZeroRecoveryNodes）；(c) 残留子集精确计数（residualSubsetCountsExactFindings）；(d) 未配 vs 配了零残留可判读（unconfiguredSubsetIsDistinctFromConfiguredClean）；(e) NonApply 三枚举 + conflict 经 skipped 列表实产（conflictNonAppliesComeFromTheEntrySkippedList）；(f) diff 非空含 +/- 行（assembleProducesTheCompleteResultFromAnAppliedPlan）；(g) stats 计数 + IN_PROCESS 档位 + residualRuleCount 可判读（同 assemble 例）
- [x] 端到端测试：assembleProducesTheCompleteResultFromAnAppliedPlan（WI4 入口 dryRun → EditedFile → verify → assemble 全链）+ rollbackFaceVerifiesTheRestoredContent（守卫回滚路径：verification 基于回滚后内容、file.changed()=false 无 diff）
- [x] 行为红线自查（scoped）：git diff 按 file scope 证实 nop-lint / nop-treesitter 既有类零修改——仅 `Fixer.java`（MergeResult additive）与 `EditPlanApplier.java`（载荷透传）两文件；开工时 nop-lint 基线干净（WI3 已提交）
- [x] 实现与 design 01 增注 / design 03 增补互洽核对（实现偏差即回写文档，不得让契约与 live 漂移）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新功能测试清单 (a)–(g) 全绿，且逐项可在测试源码中定位（TestRefactorVerifier 8/8）
- [x] **端到端验证**（Minimum Rules #22）：assembleProducesTheCompleteResultFromAnAppliedPlan + rollbackFaceVerifiesTheRestoredContent——从 WI4 入口应用到完整 RefactorResult 一测贯通，含守卫回滚样本
- [x] **接线验证**（Minimum Rules #23）：verification 计算确实调用了 nop-treesitter 解析与 nop-lint 引擎——(a)(b) 的 errorNodeCount 变化为 treesitter 路径运行时证据；(c) 的残留计数为引擎路径证据
- [x] **无静默跳过**（Minimum Rules #24）：规则子集未配 ≠ 静默跳过（(d) 可判读区分）；解析异常 fail-closed；NonApply/FileEdit/EditedFile 无静默构造；verification 无空方法体/no-op
- [x] design 01 §四增注与 design 03 增补已落档，且与 landed 实现逐条互洽（纯增注/增补，不改写既有内容）
- [x] 零行为红线（scoped diff）：既有类零修改（除 `Fixer.java`/`EditPlanApplier.java` 两处 additive 扩展）；nop-lint 既有 fix/transform 25 测试 additive 后零回归 + core 816 面保持（6 模块矩阵命令在 closure gates 复跑）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部 in-scope 项完成，无残留未勾选 checklist
- [x] 载荷字段契约 = baseline §四 逐字段成立（record 组件数契约测试钉死：RefactorResult 六字段 + Verification 四字段 + FileEdit 三要素 + RefactorStats 四要素+residualRuleCount + NonApply 三枚举 + 上下文——TestRefactorPayload.refactorResultCarriesTheFullBaselineContract）
- [x] 零行为红线（scoped diff）：nop-lint / nop-treesitter 既有行为零修改（唯一允许变更 = `Fixer.MergeResult` + `EditPlanResult` 两处 additive 扩展——skipConflicts accessor 保持、构造点仅在允许文件内）；nop-lint 全 6 模块矩阵 SUCCESS、既有 fix/transform 25 测试 additive 后零回归、golden 字节面不变
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 端到端运行时连通有 14/0 证据（assemble 测试真实从 EditPlanApplier.apply 起步），(b) 全部构造 fail-closed、无静默跳过（audit Step 6 PASS）
- [x] owner docs 已同步：design 01 §四落地增注、design 03 WI5 扩展回补、`docs-for-ai/01-repo-map/module-groups.md` 模块组行落档
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-core --severity high` 退出 0
- [x] 代码规范：`check-import-order.mjs` 本 plan 新增/变更文件零违规（范围口径——全仓存量违规在本 plan 范围外）
- [x] vision 原则 1–9 回扣核对（closure audit 执行）：原则 3（载荷逐字段全量+契约测试）、原则 6（五处 fail-closed）、原则 9（两模块+9 类型只读消费+约 260 行预算内）——audit Step 6 逐项 PASS
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [x] `./mvnw test -pl nop-refactor/nop-refactor-core -am` 全绿（14/0；另无 -am 单模块跑在 .m2 快照刷新后同样 14/0）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（audit REJECTED 整改后复跑退出 0：audit Finding-1 指出的本 plan 引入的 2 处 BOUNDARY 违规——module-groups.md 新行的 ai-dev 路径反引号直引——已改为 nop-lint 行 25 同型的声明式表述）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/05-wi5-refactor-result-verification-payload.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- （若有执行中发现的优化项，关闭时填写；in-scope live defect 不得入列）

## Closure

Status Note: WI5 收口——nop-refactor-core 模块骨架 + baseline §四 载荷契约（record 组件数契约测试钉死不缩水）+ verification 计算面（重解析/残留 lint/diff 组装/可判读 stats）+ 两处 additive 扩展（Fixer.MergeResult.skipped + EditPlanResult.skippedEdits，既有消费方零改动）+ module-groups.md owner-doc；14/0 测试 + 6 模块矩阵 SUCCESS + vision 三重点原则 PASS。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit（fresh session subagent，与实现会话不同 task）
- Audit Session: agent_a4bb3f81-f438-483a-b7b4-6b819e356925
- Evidence:
  - Step 1-8 全 PASS（除 Finding-1）：载荷逐字段对照 baseline §四 无缩水；additive 红线 git diff 证实仅两文件 +29/-9 且 merge 判定/排序本体零变化；端到端调用链真实（assemble 测试从 EditPlanApplier.apply 起步）；无第二套 lint/diff 实现（imports 全部 io.nop.lint.core.*）
  - 门禁亲跑：refactor-core 14/0 exit 0（无 -am）、hollow-scan 0 findings、check-plan-checklist warnings-only、import-order refactor/core 0 命中
  - vision 原则 1–9：原则 3/6/9 重点 PASS，Anti-Hollow PASS
  - Finding-1（Major，唯一必修）：module-groups.md 新行 2 处 BOUNDARY 违规（ai-dev 路径反引号直引违反 docs-for-ai 边界规则）+ plan L147 范围口径注记失实——已整改（改为 nop-lint 行 25 同型声明式表述），doc-links 复跑退出 0
  - Finding-2（Minor）：RefactorVerifier 未用 import——已顺手清理
  - AUDIT VERDICT: REJECTED（必修项 Finding-1）→ 整改完成、doc-links exit 0 → audit 声明"必修项完成并复跑 doc-links 确认后，允许主会话回填；Anti-Hollow 与 vision 两项 gate 结论及步骤 2–8 证据继续有效，无需重审"——回填即本段

Follow-up:

- no remaining plan-owned work（pattern 直给（RewriteInput 的 pattern 选项）已裁为 WI6/后续计划的扩展点，非本 plan 剩余工作）
