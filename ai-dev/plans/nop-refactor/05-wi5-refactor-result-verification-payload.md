# 05 WI5 RefactorResult verification 载荷——self-verification 契约落地

> Plan Status: draft
> Last Reviewed: 2026-09-25
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M1 WI5 原文 + Cross-Cutting 完成判定 + Framework/Platform Reuse 表 + Current Baseline）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §四（RefactorResult 载荷契约——字段不得缩水）与 §一.3（check/plan/apply/verify 四段契约）
> Related: `ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md`（WI4 入口契约六要素 + per-conflict 扩展点裁定，本 plan 消费其结果载荷）；`ai-dev/design/nop-refactor/00-vision.md` §三（原则 3 self-verification 载荷）；WI6（GraphQL 面将消费本载荷）；WI9（操作框架复用本载荷与 verification 计算面）
> Review: R1 对抗审查（2026-09-25，fresh session）：REVISE——4 Major（conflict 上下文路由未裁定且与红线措辞矛盾；与 WI3 并行冲突未声明；Phase 1 owner-doc 裁定不成立——module-groups.md 漂移；WI4→WI5 的 Fix.ruleId/description 交接裁定遗失）+ 5 Minor；全部修订（R2 复核后执行）

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

Status: planned
Targets: `nop-refactor/pom.xml`、`nop-refactor/nop-refactor-core/`、根 `pom.xml`、`nop-refactor/nop-refactor-core/src/test/`

- Item Types: `Fix`

- [ ] nop-refactor 聚合 pom + nop-refactor-core 模块 pom：沿 nop-lint aggregator 同型（parent / packaging pom / module 清单）；nop-refactor-core 依赖 nop-lint-core（UnifiedDiff / LintLanguage / LintEngine / RuleDslModel 消费面随之可用，nop-treesitter 经其传递可达）；包结构沿 io.nop.refactor.core 惯例
- [ ] 载荷类型面：RefactorResult / Verification / FileEdit / RefactorStats / NonApply（含三分支原因枚举 + 上下文承载）——形态（record/类、可空性表达）归源码与 design，字段契约按 Goals 清单逐项落
- [ ] 逐字段契约测试（新功能测试，Phase 1 内完成）：baseline §四 字段清单逐项断言存在与类型语义——RefactorResult 六字段、Verification 四字段（含 symbolIntact 可空位与 codemod 面置 null 语义）、FileEdit 三要素、RefactorStats 四要素、NonApply 三枚举 + 上下文——"不得缩水"的可执行化
- [ ] 根 `pom.xml` 注册 `<module>nop-refactor</module>`
- [ ] `docs-for-ai/01-repo-map/module-groups.md` nop-refactor 模块组一行增补（模块名 + 一句话定位——与 nop-lint 行同型）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw compile -pl nop-refactor/nop-refactor-core -am` 成功——新模块进聚合构建，根 pom 注册生效
- [ ] 逐字段契约测试全绿，覆盖清单与 baseline §四 逐项可对照（closure audit 复核无缺字段）
- [ ] **无静默跳过**（Minimum Rules #24）：NonApply 构造的 fail-closed 语义有测试——缺上下文/未知原因的构造显式失败（异常形态沿 error-handling 两层策略的模块内英文消息惯例），不是静默接受
- [ ] **新功能测试清单**（Minimum Rules #25）：载荷类型逐字段断言 + NonApply fail-closed 构造，均已列出并落为测试
- [ ] owner-doc 更新：`docs-for-ai/01-repo-map/module-groups.md` 已增补 nop-refactor 模块组行（design 01 §二 三模块拓扑裁定已先行覆盖模块骨架本身；docs-for-ai 模块文档新建归 WI13）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - verification 计算面 + WI4 扩展点衔接（Decision + Fix）

Status: planned
Targets: `nop-refactor/nop-refactor-core/src/main|test/`、`nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/`（仅 WI4 入口载荷 additive 扩展）、`ai-dev/design/nop-refactor/01-architecture-baseline.md`、`ai-dev/design/nop-lint/03-execution-engine.md`

- Item Types: `Decision + Fix`

- [ ] WI4 per-conflict additive 扩展（路由已裁定）：`Fixer.MergeResult` 新增被跳过 Fix 列表组件（构造点仅 Fixer.java:61 一处）+ `EditPlanApplier.EditPlanResult` additive 透传（既有字段与语义逐字不变），design 03 增补覆盖两者；nonApplied(conflict) 上下文实产消费该列表——**禁止绕道直调 Fixer.merge、禁止差集反推**
- [ ] verification 计算面：输入 = 编辑后文件三元组集合（path + 原内容 + 编辑后内容）+ 可配规则子集 → 逐文件重解析（经 LintLanguage.parse → nop-treesitter；error-node 计数同型 EditPlanApplier.countErrorNodes：kind=="ERROR" 或 isMissing）+ 逐文件残留 lint（LintEngine 规则子集入口）→ 聚合 parseOk / errorNodeCount / residualDiagnostics + RefactorStats（含耗时档位）
- [ ] 多文件聚合口径 + parseOk per-file 谓词 Decision：parseOk 对文件集合的量词语义；**单文件谓词钉死 = 重解析后无 ERROR/missing 节点**（非"parse 不抛异常"——tree-sitter 恢复式解析几乎不抛）；pre-existing 恢复节点源文件的立场（per-file parseOk 反映该文件自身状态，不与编辑前比较——与守卫回滚的增量判据分属两个语义面）；errorNodeCount / residualDiagnostics 的聚合口径；解析异常路径 fail-closed（异常不吞、不伪报 parseOk=true）——落 design 01 §四增注
- [ ] 规则子集空/未配呈现形态 Decision："未配规则集"与"配了规则集且零残留"机器可判读的区分形态（如 stats 显式携带规则子集规模/标志）——落 design 01 §四增注 + 契约测试断言
- [ ] symbolIntact null 语义落档（codemod 面不产、仅 rename 类操作填，WI9–WI12 接管）——并入 design 01 §四增注
- [ ] FileEdit 取值语义落档（交接裁定）：来源标识 ← Fix.ruleId、变更摘要 ← Fix.description——并入 design 01 §四增注，闭合 design 03 留白
- [ ] 耗时档位语义落档：baseline §四档位语义 = 外部桥调用成本档位（进程内/进程外）；v1 codemod 全链路纯进程内、恒标"进程内"档——并入 design 01 §四增注
- [ ] diff 组装：逐文件复用 UnifiedDiff.of（path, 原内容, 编辑后内容），聚合进 RefactorResult.diff
- [ ] 新功能测试清单（显式）：(a) 语法破坏样本 → parseOk=false + errorNodeCount>0（可证伪）；(b) 干净样本 → parseOk=true + errorNodeCount=0；(c) 已知残留规则命中的规则子集 → residualDiagnostics 精确计数；(d) 规则子集未配 → residualDiagnostics=0 且"未配"状态可判读（区别于"配了零残留"）；(e) NonApply 三枚举构造 + conflict 分支经 WI4 skipped 列表实产；(f) diff 非空与内容正确性；(g) RefactorStats 各计数与耗时档位可判读
- [ ] 端到端测试：per-file 编辑计划经 WI4 入口应用（含守卫回滚路径）→ 立刻计算 verification → 完整 RefactorResult 一测贯通
- [ ] 行为红线自查（scoped）：git diff 按 file scope 证实 nop-lint / nop-treesitter 既有类零修改——唯一允许变更 = `Fixer.java`（MergeResult additive）与 `EditPlanApplier.java`（载荷 additive 透传）两文件；开工前断言 nop-lint 基线干净（无他人在途改动）
- [ ] 实现与 design 01 增注 / design 03 增补互洽核对（实现偏差即回写文档，不得让契约与 live 漂移）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新功能测试清单 (a)–(g) 全绿，且逐项可在测试源码中定位
- [ ] **端到端验证**（Minimum Rules #22）：从"per-file 编辑计划经 WI4 入口应用后内容"到"完整 RefactorResult"一测贯通——含一条守卫回滚路径样本（rolledBack=true 且 verification 基于回滚后内容计算）
- [ ] **接线验证**（Minimum Rules #23）：verification 计算确实调用了 nop-treesitter 解析与 nop-lint 引擎——以断言或调用链证明（(a)(b) 的 errorNodeCount 变化本身即 treesitter 解析路径的运行时证据；(c) 的残留计数即引擎路径证据）
- [ ] **无静默跳过**（Minimum Rules #24）：规则子集未配 ≠ 静默跳过（(d) 可判读区分）；解析异常 fail-closed 不伪报 parseOk；NonApply 无"缺原因/缺上下文"的静默构造；verification 计算无空方法体/no-op 分支
- [ ] design 01 §四增注与 design 03 增补已落档，且与 landed 实现逐条互洽（纯增注/增补，不改写既有内容）
- [ ] 零行为红线（scoped diff）：既有类零修改（除 `Fixer.java`/`EditPlanApplier.java` 两处 additive 扩展）；`./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-java,nop-lint/nop-lint-js,nop-lint/nop-lint-nop,nop-lint/nop-lint-maven-plugin,nop-lint/nop-lint-graphql -am` 全绿（golden 字节面不变）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 项完成，无残留未勾选 checklist
- [ ] 载荷字段契约 = baseline §四 逐字段成立（closure audit 逐项对照：RefactorResult 六字段 + Verification 四字段 + FileEdit 三要素 + RefactorStats 四要素 + NonApply 三枚举 + 上下文；缺一即缩水，判定失败）
- [ ] 零行为红线（scoped diff）：nop-lint / nop-treesitter 既有行为零修改（唯一允许变更 = `Fixer.MergeResult` + `EditPlanResult` 两处 additive 扩展，per 路由裁定）；nop-lint 全 6 模块测试绿、golden 字节面不变
- [ ] **Anti-Hollow Check**：closure audit 已验证 (a) 端到端路径从 WI4 入口应用到完整 RefactorResult 运行时连通（不只是类型存在），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [ ] owner docs 已同步：design 01 §四增注、design 03 增补、`docs-for-ai/01-repo-map/module-groups.md` 模块组行落档（否则不得关闭）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-core --severity high` 退出 0
- [ ] 代码规范检查通过：`node ai-dev/tools/check-import-order.mjs` 退出 0（本 plan 新增/变更文件零违规——范围口径）
- [ ] vision 原则 1–9 回扣核对（closure audit 执行）：原则 3（self-verification 载荷不缩水）、原则 6（fail-closed）、原则 9（预算——只建裁定内模块、只读复用 nop-lint 存量不重造）为本 WI 重点核对项
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [ ] `./mvnw test -pl nop-refactor/nop-refactor-core -am` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/05-wi5-refactor-result-verification-payload.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- （若有执行中发现的优化项，关闭时填写；in-scope live defect 不得入列）

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
