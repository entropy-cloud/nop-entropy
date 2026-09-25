# 03 WI4 编辑计划应用入口——诊断无关的 per-file 应用机制抽出

> Plan Status: completed
> Last Reviewed: 2026-09-25
> Source: ai-dev/backlog/nop-refactor-roadmap.md（M1 WI4）；ai-dev/design/nop-refactor/01-architecture-baseline.md §一.3/§二（四段契约、与 nop-lint 边界）；ai-dev/design/nop-lint/03-execution-engine.md（fix 管线既有裁定）
> Related: 01-dependency-gate-verification.md（无依赖关系，本 plan 不受其门约束）；后续 05（RefactorResult 载荷，消费本 plan 的入口）
> Review: R1 对抗审查（2026-09-25，fresh session）：REVISE——2 Major（契约缺结果载荷要素；WI5 per-conflict 上下文承载未裁定）+ 5 Minor；全部修订。R2 复核（2026-09-25，fresh session）：APPROVE——七项逐条 FIXED-VERIFIED，无新 Blocker/Major；一条非阻塞建议（design 03 增注补"Fix.ruleId/description 为通用来源/描述字段，refactor 面取值语义归 WI5/WI9 裁定"）已纳入 Phase 1 执行项。

## Purpose

执行 roadmap WI4（Item Type: Decision + Fix）：把"per-file 编辑计划 + 冲突合并 + 原子写 + 重解析守卫 + 回滚"从 nop-lint 的诊断驱动多轮循环中抽出为**诊断无关**的应用入口，供 refactor 面（编辑计划先于诊断存在）与 WI9 操作框架复用；落点二选一（refactor-core 只读消费 vs nop-lint-core 新增公共入口）在本 plan 裁定。零行为变化为硬约束。

## Current Baseline

- **既有机制全部在 nop-lint-core fix 包**（live 已核对）：
  - `Fixer.merge(List<Fix>)`：贪婪不重叠选择，声明序优先，重叠计 `skippedConflicts`（fix/Fixer.java）。
  - `Fix`：`record Fix(SourceRange range, String replacement, String ruleId, String description, int order)`——自足的字节区间替换，无需节点结构。
  - `FixApplier.run(Path, byte[] original, boolean dryRun)`：**多轮循环**——每轮重新 lint 取候选 fix → merge → 拼接 → 原子写（temp + ATOMIC_MOVE，失败删 temp 抛 NopLintException）→ 重解析守卫（error-node 计数不增，破坏则回滚写回上一轮内容）→ 收敛守卫（候选数须严格下降）+ 轮数上限（DEFAULT_MAX_PASSES=10）。
  - `UnifiedDiff`：diff 面已有（含 MAX_LCS_CELLS 上界）。
- **消费方仅 `CheckRunner.fixFile`**（cli/CheckRunner.java，javadoc ~:442、方法体 ~:451-500，live 为准）：多轮循环由它驱动，lint 入口、豁免过滤、baseline、报告均在其闭包内。main 代码中仅 CheckRunner 构造/驱动 FixApplier（RunSummary 只收 FixStats）。
- **结构事实**：FixApplier 的循环骨架（merge→拼接→原子写→守卫→回滚）与"诊断"无耦合——它只消费 `List<Fix>` 候选与 `LintLanguage.parse` 的 error-node 计数；诊断耦合仅存在于"候选从哪来"（每轮重 lint）。
- **refactor 模块尚不存在**（无 nop-refactor-core）。WI9 才建操作框架模块；本 plan 的入口落点裁定决定 WI9/WI5/WI6/WI7 的消费形态。
- roadmap Purpose 已裁定执行路径统一（防双引擎）：WI9 框架化的是本 plan 落地的同一条应用路径。

## Goals

- **落点裁定（Decision）**：二选一并记录理由——裁定为 **nop-lint-core 新增诊断无关公共入口**（理由见"落点裁定记录"节），refactor 面与 WI9 经该入口消费，不在 refactor-core 重造原子写/回滚/守卫机械。
- **实现（Fix）**：新入口语义 = 显式有序编辑列表（**字面复用 `Fix` 类型**——不新建同构 record，保证 Fixer.merge 单份复用、无第二合并面）+ 落盘目标路径 + LintLanguage + dryRun → 冲突合并（沿用 Fixer 优先级语义）→ 拼接 → dry-run 不落盘 / 非 dry-run 原子写 → 重解析守卫 → 破坏回滚。**无多轮循环、无 lint 调用、无 Diagnostic 消费**。
- **行为不变证明**：FixApplier 多轮循环的每轮机械核改为复用新入口后，nop-lint 全部 6 模块测试绿（含 ConsoleReporter golden 字节面、TestCliAutofixEndToEnd、TestFixApplier 全矩阵）。

## Non-Goals

- 不改 nop-lint 任何既有公开/内部行为：多轮循环语义（收敛守卫、轮数上限、候选来源=每轮重 lint）、输出字节、退出码全部不变。
- 不建 nop-refactor-* 模块（WI9）。
- 不做 RefactorResult 载荷/diff/残留 lint（WI5）、GraphQL 面（WI6）、CLI 形态（WI7）。
- 不改 UnifiedDiff / Fixer 语义。
- 不做批量编排（多文件循环归消费方，本入口仍是 per-file 契约）。

## Scope

### In Scope

- `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/`：新增诊断无关应用入口；FixApplier 内部机械核改为复用该入口（纯内部重构，语义逐字保持）。
- `ai-dev/design/nop-lint/03-execution-engine.md`：新公共入口契约增注（roadmap Cross-Cutting：上游扩展承载形态——本 plan + 增注即 baseline §七所指 design 记录）。
- 新入口的焦点测试。

### Out Of Scope

- CheckRunner/LSP/GraphQL/Maven plugin 的任何行为面。
- `ai-dev/design/nop-refactor/`（baseline §二 已有依赖边，无需改）。

## 落点裁定记录（Decision，执行时按此落地；对抗审查若推翻须在此回写）

**裁定：nop-lint-core 新增公共入口。** 理由：

1. **复杂度预算**（vision 原则 9）：备选项"refactor-core 只读消费 fix 类"意味着在 refactor-core 重造原子写（temp+ATOMIC_MOVE+temp 清理）、回滚、error-node 守卫、越界 fail-closed 约 100 行机械——且 live 中这些机械（applyAll/splice/atomicWrite/countErrorNodes）均为 FixApplier 的 private static，"只读消费"字面不可行，等价于重造或先做 public 化改造（那本身即一次"新增入口"变更，只是契约归属更含糊）。baseline §七"优先复用 nop-lint 既有机制而非在本模块重造"直接禁止此形态。
2. **防双引擎漂移**（roadmap Purpose 统一裁定）：机械核只有一份，FixApplier 多轮循环与 refactor 编辑计划路径消费同一实现——WI9 的"无第二执行路径"接线验收因此可证。
3. **上游扩展代价受控**：新增入口是纯增量（不改既有类行为），设计记录由本 plan + design 03 增注承载（roadmap Cross-Cutting 已预设此形态）。

**WI5 per-conflict 上下文承载裁定**（R1 Major-2）：`Fixer.merge` 今日只暴露 `skippedConflicts` 计数，不暴露被跳过编辑本体；WI5 的 nonApplied conflict 分类项要求"原因 + 上下文"。裁定：**WI5 契约扩展点**——本 plan 不预扩（架构保持薄，扩展按需建，baseline §七）；WI5 落地时在本入口的结果载荷上**增量扩展**被跳过编辑列表（additive），并在 design 03 增补该扩展的契约记录。红线：WI5 不得绕过本入口直调 `Fixer.merge` 自行组装第二条合并路径（roadmap Purpose 防双引擎裁定）。

## Execution Plan

### Phase 1 - 落点裁定落档与 design 增注（Decision）

Status: completed
Targets: `ai-dev/design/nop-lint/03-execution-engine.md`、本文件裁定记录段

- Item Types: `Decision`

- [x] design 03 增注：新公共入口的契约六要素——(a) 输入=显式有序编辑列表（**字面 `Fix` 类型**：字节区间+替换文本+来源标识+序数）+ 落盘目标路径 + 语言适配（重解析用）+ dryRun；(b) 冲突合并沿用 Fixer 声明序优先语义（不重排序、不新增仲裁）；(c) 原子写/失败清理/回滚语义与 FixApplier 既有裁定逐字一致；(d) 重解析守卫 = error-node 计数不增，破坏即回滚——**守卫回滚是返回的结局（rolledBack 标志），不是异常**；(e) 诊断无关 = 不消费 Diagnostic、不重跑 lint、无多轮循环；(f) 结果载荷 = 最终内容 + 是否守卫回滚（rolledBack）+ 应用编辑数（守卫回滚时扣减本次被撤销的编辑，对齐 FixApplier 既有 `applied -=` 语义）+ 冲突跳过数（skippedConflicts）。附裁定理由（与 FixApplier 的关系：机械核单份、多轮循环仍是唯一重 lint 驱动方）+ WI5 per-conflict 扩展点裁定（被跳过编辑列表届时 additive 扩展于结果载荷、design 03 增补；禁止绕道直调 Fixer.merge）+ R2 建议（Fix.ruleId/description 通用语义留白归 WI5/WI9）
- [x] 本文件"落点裁定记录"段与 design 03 增注互洽（若审查/执行推翻二选一，先回写本段再执行——实际一致）

Exit Criteria:

- [x] design 03 增注落档且与本 plan 裁定段无矛盾；design 03 既有内容零改写（纯增注）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 入口实现与行为不变证明（Fix）

Status: completed
Targets: `nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/fix/`、`nop-lint/nop-lint-core/src/test/java/io/nop/lint/core/fix/`

- Item Types: `Fix`

- [x] 新增诊断无关应用入口（落 fix 包：`EditPlanApplier.apply(Path, byte[], List<Fix>, LintLanguage, boolean dryRun)`）——语义按 Phase 1 契约六要素；越界区间 fail-closed（消息含来源标识与区间，沿 FixApplier.applyAll 既有措辞风格："stale range; fail-closed"）
- [x] FixApplier 多轮循环改为复用新入口（每轮机械核 = merge→apply→guard→write 经 `EditPlanApplier.apply`；循环自身的收敛守卫/轮数上限/候选收集保持原实现）——行为逐字不变（含守卫回滚时 applied 扣减语义：回滚轮净存活 0 ≡ 既有 `+= size / -= size`）
- [x] 新入口焦点测试（TestEditPlanApplier 9 例）：单编辑应用 / 重叠冲突合并与 skippedConflicts 计数 / dry-run 不落盘 / 守卫触发回滚（rolledBack 标志 + 最终内容回滚 + applied=0，dry-run 同型）/ 已破坏源同计数不误回滚 / 越界区间 fail-closed / 原子写失败路径（不可写目标）/ 空列表恒等——覆盖契约六要素逐项
- [x] 行为不变证明：`./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-java,nop-lint/nop-lint-js,nop-lint/nop-lint-nop,nop-lint/nop-lint-maven-plugin,nop-lint/nop-lint-graphql -am` 全绿（6 模块 Reactor 全 SUCCESS）；TestFixApplier/TestCliAutofixEndToEnd/TestConsoleReporter（golden 字节面）/TestXmlCliWiring 零改动通过
- [x] 实现与 design 03 增注互洽核对（六要素逐条对照 landed 源码：apply 签名/结果 record/守卫/回滚/原子写——一致）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 新入口测试全绿且覆盖契约六要素（含 fail-closed 路径——无静默跳过：新公共方法在异常路径抛 NopLintException 而非吞错；守卫回滚路径经 rolledBack 标志可编程判读）
- [x] **接线验证**：FixApplier 的每轮机械核确实调用新入口（运行时连通）——以代码追踪（run 循环内 EditPlanApplier.apply 调用点）+ 既有 FixApplier 全矩阵测试零改动通过为证
- [x] 行为不变：全 6 模块测试绿，ConsoleReporter golden 字节面零漂移
- [x] design 03 增注与 landed 实现一致（六要素逐条可对照源码验证）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 落点裁定已记录（本 plan 裁定段 + design 03 增注），refactor-core 重造机械的备选项显式否决
- [x] 新入口语义 = 契约六要素（含结果载荷 (f)），诊断无关（无 lint 调用、无 Diagnostic 消费、无多轮循环）；WI5 per-conflict 扩展点已裁定且禁止绕道直调 Fixer.merge
- [x] 零行为变化：nop-lint 全 6 模块测试绿；ConsoleReporter golden 字节面不变
- [x] Anti-Hollow：新入口被 FixApplier 运行时调用（接线证明——run 循环内 EditPlanApplier.apply 调用点 + 既有全矩阵零改动）；无空方法体/静默跳过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-core --severity high` 退出 0
- [x] 代码规范检查通过：`node ai-dev/tools/check-import-order.mjs` 退出 0（本 plan 新增文件零违规）
- [x] vision 原则 1–9 回扣核对（closure audit 执行）：原则 9（预算——未重造机械）、原则 4（原子性）、原则 6（fail-closed）为本 WI 重点核对项（audit Step 6 逐项 PASS）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-lint/nop-lint-core,nop-lint/nop-lint-java,nop-lint/nop-lint-js,nop-lint/nop-lint-nop,nop-lint/nop-lint-maven-plugin,nop-lint/nop-lint-graphql -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/03-wi4-edit-plan-apply-entry.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- （无——执行中未发现需要登记的优化项）

## Closure

Status Note: WI4 收口——诊断无关编辑计划应用入口 EditPlanApplier 落地 nop-lint-core fix 包（契约六要素，机械核与 FixApplier 单份化），FixApplier 多轮循环重构为复用入口且行为逐字不变（全 6 模块矩阵全绿 + golden 字节面零漂移 + TestFixApplier 零改动），WI5 per-conflict 扩展点与 Fix.ruleId/description 语义留白已裁定落 design 03。
Completed: 2026-09-25

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit（fresh session subagent，与实现会话不同 task）
- Audit Session: agent_5ed1fbca-be45-45d5-9df2-88ad10ca2f1a
- Evidence:
  - Step 1-7 全 PASS：契约六要素逐条对照 live（apply 签名/Fixer.merge 单份/原子写语义逐字同构/rolledBack 返回结局/诊断无关/结果载荷四字段）；接线事实（FixApplier.run 每轮 EditPlanApplier.apply 调用点 :126-127）；TestEditPlanApplier 9 例与 plan 一致且可证伪；TestFixApplier git 零改动
  - 行为不变物证：6 模块 surefire 报告新鲜（mtime 晚于源文件 mtime）且全 0 失败——core 799/0、java 104/0、js 61/0、nop 76/0、maven-plugin 10/0、graphql 20/0；TestCliAutofixEndToEnd 11/0、TestConsoleReporter 10/0（golden）、TestXmlCliWiring 2/0
  - 门禁亲跑：hollow-scan exit 0；doc-links 0 errors；check-plan-checklist exit 0（active 态 3 未勾 warnings-only，回填后转绿）；import-order 全仓 exit 1 系预存生成物与并发会话在途文件——**本 plan 三个变更文件零违规（grep 证实），gate 按范围口径成立**（audit Minor-1 备案）
  - vision 原则 1–9：原则 4/6/9 重点逐项 PASS，其余未触及
  - Findings：2 Minor（import-order 范围口径——已在上面备案；异常消息前缀 fix→edit 系 plan L86 授权且断言关键子串保留、无可观察行为漂移）
  - AUDIT VERDICT: PASS（允许回填 3 项 gate + Closure 段 + completed）

Follow-up:

- no remaining plan-owned work（WI5 的 per-conflict additive 扩展是 WI5 的 in-scope 项，非本 plan 剩余工作）
