# 1 修复 nop-deep-analysis P2 审计发现（交付物事实性精度修正）

> Plan Status: draft
> Mission: nop-deep-analysis
> Work Item: P2 audit findings remediation（跨 A2/A4/A5/A6/A7 交付物）
> Last Reviewed: 2026-07-26
> Source: `ai-dev/audits/nop-deep-analysis-audit-followups.md`（multi-audit P2 × 12 + open-audit P2 × 4 = 16 项）
> Related: `ai-dev/plans/nop-deep-analysis/2026-07-26-0816-1-doc-contract-drift-remediation.md`（P1 已修复，completed）；`ai-dev/audits/2026-07-26-0702-multi-audit-nop-deep-analysis.md`；`ai-dev/audits/2026-07-26-0702-open-audit-nop-deep-analysis.md`

## Purpose

收口 `nop-deep-analysis` mission 交付物中由 2026-07-26 两轮审计发现并 triaged 到 P2 backlog 的全部 16 项事实性精度缺陷。这些缺陷均为分析文档中的行号偏移、计数错误、范围标注不准、标签复用等精度问题——P1 契约漂移已由 `doc-contract-drift-remediation` plan 修复完毕，本 plan 处理剩余 P2 项，使六份分析交付物 + capstone 的事实性论断与 live repo 完全一致。

## Current Baseline

逐条已对照 live repo 核实（2026-07-26）。**16 项 P2 finding 全部确认仍然存在**（未在此前被修复）。

### 来自 multi-audit 的 P2（12 项）

**P2-A2-02 — `CoreConstants` 行号 off-by-one**
- 交付物 `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md:280` 写 `CoreConstants.java:26`。
- 实际：`nop-kernel/nop-core/src/main/java/io/nop/core/CoreConstants.java:27` — `String FILE_POSTFIX_ANNOTATIONS = ".annotations";` 在 L27 非 L26。

**P2-A2-03 — `graphql:*` 常量范围不准**
- 交付物 `...-nop-core-engine-deep-dive.md:226` 写 `GraphQLConstants.java(GQL-008, L26-43)`。
- 实际：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/GraphQLConstants.java` 中 `ATTR_GRAPHQL_*` / `TAG_GRAPHQL_*` 常量范围是 L23–L45（首条 `ATTR_GRAPHQL_DICT_VALUE_PROP` 在 L23，末条 `TAG_GRAPHQL_TRANS_FILTER` 在 L45），非 L26-43。

**P2-A2-04 — `AppBeanContainerLoader` 范围混淆**
- 交付物 `...-nop-core-engine-deep-dive.md:240` 写 `L170-185：/nop/autoconfig 下的 .beans 资源 + nop.ioc.app-beans.files 配置补充`。
- 问题：把两段不同逻辑（autoconfig `.beans` 资源加载 vs `nop.ioc.app-beans.files` 配置读取）混在一个行号范围下，读者无法区分。

**P2-A4-02 — `INopJobScheduleBiz` 计数自相矛盾**
- 交付物 `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md:50` 写「声明 5 个额外 `@BizMutation`」，但同句列出 6 个名字：`enableSchedule`/`disableSchedule`/`pauseSchedule`/`resumeSchedule`/`triggerNow`/`archiveSchedule`。

**P2-A5-01 — `nop-plugin` 嵌套层级误述**
- 交付物 `ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md:5`（Scope 行）和 `:52`（模块表行）把 `nop-plugin` 列为顶层基础设施模块。
- 实际：`nop-plugin` 嵌套在 `nop-core-framework/nop-plugin/` 下（已 `ls -d` 确认），不是顶层独立模块。

**P2-A6-01 — `ai-dev/` 知识层计数错误**
- 交付物 `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md` 多处（`:5, :6, :13, :29, :67, :69`）写「七层知识层」。
- 实际：`ai-dev/` 目录有 8 个子目录（`logs/plans/design/analysis/discussions/bugs/audits/skills`），`AGENTS.md` 的 `ai-dev/ Directory Roles` 表也列 8 行（含 `lessons/` 则为 9；以 live `ls ai-dev/` 为准）。当前表述「七层」少计。

**P2-A6-02 — `docs-for-ai/` 结构计数错误**
- 交付物 `...-nop-engineering-dx-ai-dev.md:158` 复制 `docs-for-ai/INDEX.md` 的结构表时计 8 项。
- 实际：`INDEX.md` 实际有 9 项（含 `90-maintenance/`），A6 复制时漏掉一项。

**P2-A6-03 — `source-anchors` 锚点计数严重少计**
- 交付物 `...-nop-engineering-dx-ai-dev.md:200` 写「~90 个锚点」。
- 实际：`rg -c "^\| \`[A-Z]" docs-for-ai/04-reference/source-anchors.md` ≈ 185 条锚点条目。少计约一半。

**P2-A6-04 — `events.jsonl` 步骤计数过时**
- 交付物 `...-nop-engineering-dx-ai-dev.md:91` 写 `EXECUTE(11)、CLOSURE_SCRIPT_CHECK(10)、BUILD_VERIFY(10)、DRAFT_PLANS(4)、CHECK(2)、REVIEW_PLANS(6)；marker: pass(37)、all_complete(10)、created(4)`。
- 问题：审计指出至少 3 个数字有误（EXECUTE=12 非 11、CLOSURE_SCRIPT_CHECK=12 非 10、pass=39 非 37，且漏 fail=2）。**注意**：`events.jsonl` / `run-state.json` 为运行时产物，未提交到仓库（已 `find` 确认不存在），因此计数无法在 live repo 中重新验证——修正时应标注数据来源为历史快照，或以不可复现为由移除精确数字改用定性描述。

**P2-A7-01 — capstone §8.5 算术不一致**
- 交付物 `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md:335-337` 的 deferred-items 汇总算术自相矛盾（写 22/27，实际按 §8.1–§8.4 逐项加总为 24/29；括号内 5+5+4+5+4+5+1=29 非 27）。

**P2-A7-02 — mermaid 节点覆盖声明略夸大**
- 交付物 `...-nop-platform-deep-introduction.md:102` 声称「23 行覆盖全部 25 节点」，但 `API` 与 `OPS` 节点缺显式 provenance 行。

**P2-A7-03 — `A2 §8(c)` 标签复用于两个不同条目**
- 交付物 `...-nop-platform-deep-introduction.md` 中 `A2 §8(c)` 被用于两个不同语义：L167/L316 指「启动性能量化」（对应 `nop-core-engine-deep-dive.md:368`），L329 指「`@BizAction` 与 AOP 关系」（对应 `:367`）。标签歧义导致交叉引用不可靠。

### 来自 open-audit 的 P2（4 项）

**OA-1 — capstone §8.5 以 `_tmp/` 草稿作为 load-bearing provenance**
- 交付物 `...-nop-platform-deep-introduction.md:279, :337, :365` 的 §8.5 完整性计数以 `_tmp/a7-phase1-working-draft.md` §3 为 provenance。`_tmp/` 目录会被清理，导致计数不可复现。
- 建议修正：将关键计数摘要内联到 capstone §8 正文（使 §8 自包含），或在引用处标注「`_tmp/` 为执行期草稿，计数摘要已内联于本节」。

**OA-2 — A6 误引 `INDEX.md` 模块骨架，漏 `codegen` 步骤**
- 交付物 `...-nop-engineering-dx-ai-dev.md:150` 写 `model → dao → meta → service → web → app → api`。
- 实际：`docs-for-ai/INDEX.md:207` 为 `model -> codegen -> dao -> meta -> service -> web -> app -> api`——A6 漏掉了 `codegen` 步骤。

**OA-3 — A2 结论自相矛盾：「六大」vs 列出 7 个**
- 交付物 `...-nop-core-engine-deep-dive.md:373` 写「六大引擎模块」，括号内列出 7 个：`nop-core / nop-xlang / nop-xdef / nop-dao / nop-orm / nop-graphql / NopIoC`。

**OA-4 — mission `commands.test` 结构上是 no-op**
- `missions/nop-deep-analysis.json:15` 的 `commands.test` 用 `; echo` 掩盖了 `check-doc-links.mjs` 的退出码。
- 性质：审计明确标注「设计如此，analysis mission；记录以防下次重新发现」。**这不是缺陷，是已知设计选择**——本 plan 仅补充注释说明，不改变行为。

### 质量门基线

`node ai-dev/tools/check-doc-links.mjs --strict` 当前对本 mission 交付物报 0 broken link（doc-contract-drift-remediation plan 已验证）。本 plan 不得引入新的 broken link。

## Goals

- 修正全部 16 项 P2 finding（12 multi-audit + 4 open-audit），使 A2/A4/A5/A6/A7 五份分析文档 + capstone 的事实性论断与 live repo 完全一致。
- 每项修正都经源码/文件交叉核对（非仅引用审计结论）。
- 保持 doc-links checker 在本 mission 文件集上 0 broken link。

## Non-Goals

- 不处理 A7 capstone 的 `Non-Blocking Follow-ups` 中 14 项文档治理任务（如 `x:override` 8 模式补全、source-anchors 新增锚点等）——这些是分析建议的执行，pending human decision。
- 不处理 capstone §6 演进建议 E1–E8（pending human decision on roadmap adoption）。
- 不重写任何分析文档的论述结构，只做行号/计数/范围/标签的定点精度修正。
- 不改任何 Java 代码、ORM/生成物。
- 不改变 mission `commands.test` 的行为（OA-4 为设计选择，仅补注释）。

## Scope

### In Scope

- `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md`（P2-A2-02 `:280`、P2-A2-03 `:226`、P2-A2-04 `:240`、OA-3 `:373`）
- `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md`（P2-A4-02 `:50`）
- `ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md`（P2-A5-01 `:5, :52`）
- `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md`（P2-A6-01 多处、P2-A6-02 `:158`、P2-A6-03 `:200`、P2-A6-04 `:91`、OA-2 `:150`）
- `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md`（P2-A7-01 `:335-337`、P2-A7-02 `:102`、P2-A7-03 `:167/:316/:329`、OA-1 `:279/:337/:365`）
- `missions/nop-deep-analysis.json`（OA-4 `:15` 补注释）

### Out Of Scope

- A1/A3 交付物（multi-audit 确认 0 P2 findings）
- P1 findings（已由 doc-contract-drift-remediation 修复）
- 文档治理 follow-ups（14 项，pending human decision）
- 演进建议 E1–E8（pending human decision）

## Execution Plan

### Phase 1 - 修正 A2/A4/A5/A6 交付物事实性精度缺陷

Status: planned
Targets: `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md`、`...-nop-graphql-service-frontend.md`、`...-nop-module-matrix.md`、`...-nop-engineering-dx-ai-dev.md`

- Item Types: `Fix`

- [ ] **Fix [P2-A2-02]** `...-nop-core-engine-deep-dive.md:280`：`CoreConstants.java:26` → `CoreConstants.java:27`（源码 `nop-kernel/nop-core/.../CoreConstants.java:27` 已确认）。
- [ ] **Fix [P2-A2-03]** `...-nop-core-engine-deep-dive.md:226`：`L26-43` → `L23-45`（源码 `GraphQLConstants.java` 中 `ATTR_GRAPHQL_*`/`TAG_GRAPHQL_*` 首条 L23 末条 L45 已确认）。
- [ ] **Fix [P2-A2-04]** `...-nop-core-engine-deep-dive.md:240`：将 `L170-185` 拆分为两段独立行号范围（autoconfig `.beans` 资源 vs `nop.ioc.app-beans.files` 配置），各自标注对应逻辑；精确行号以 `AppBeanContainerLoader.java` live 源码为准。
- [ ] **Fix [OA-3]** `...-nop-core-engine-deep-dive.md:373`：「六大引擎模块」→「七大引擎模块」（括号内已列 7 个：nop-core/nop-xlang/nop-xdef/nop-dao/nop-orm/nop-graphql/NopIoC）。
- [ ] **Fix [P2-A4-02]** `...-nop-graphql-service-frontend.md:50`：「5 个额外 `@BizMutation`」→「6 个」（同句已列 6 个名字）。
- [ ] **Fix [P2-A5-01]** `...-nop-module-matrix.md:5, :52`：将 `nop-plugin` 从「顶层基础设施模块」修正为「`nop-core-framework` 下嵌套模块」（已 `ls -d nop-core-framework/nop-plugin/` 确认）。
- [ ] **Fix [P2-A6-01]** `...-nop-engineering-dx-ai-dev.md:5, :6, :13, :29, :67, :69`：「七层知识层」→ 正确计数（以 `ls ai-dev/` live 目录数为准；`AGENTS.md` Directory Roles 表为权威参照）。
- [ ] **Fix [P2-A6-02]** `...-nop-engineering-dx-ai-dev.md:158`：`docs-for-ai/` 结构计数修正为与 `INDEX.md` 一致的项数（以 live `INDEX.md` 实际条目数为准）。
- [ ] **Fix [P2-A6-03]** `...-nop-engineering-dx-ai-dev.md:200`：「~90 个锚点」→ 实际数量（以 `rg -c "^\| \`" docs-for-ai/04-reference/source-anchors.md` live 计数为准，当前 ≈185）。
- [ ] **Fix [P2-A6-04]** `...-nop-engineering-dx-ai-dev.md:91`：`events.jsonl` 步骤计数——因运行时产物未提交到仓库（`find` 确认不存在），无法重新验证精确数字。修正方式：标注「计数来自执行期历史快照，运行时产物未入库，数字可能随 mission 进展变化」，或移除不可复现的精确数字改用定性描述。
- [ ] **Fix [OA-2]** `...-nop-engineering-dx-ai-dev.md:150`：模块骨架补 `codegen` 步骤（`model → dao → ...` → `model → codegen → dao → ...`，与 `INDEX.md:207` 一致）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] P2-A2-02：`rg "CoreConstants.java:2[67]" ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md` — 命中行显示 `:27` 不再含 `:26`。
- [ ] P2-A2-03：`rg "L2[36]-4[35]" ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md` — GQL-008 行范围改为 `L23-45`。
- [ ] P2-A2-04：`...-nop-core-engine-deep-dive.md:240` 区域不再把 autoconfig `.beans` 与 `nop.ioc.app-beans.files` 混在同一行号范围；两段逻辑各自有独立行号标注。
- [ ] OA-3：`rg "六大引擎模块" ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md` 无命中（已改为「七大」或等价表述）。
- [ ] P2-A4-02：`...-nop-graphql-service-frontend.md:50` 的 `@BizMutation` 计数与同句列出的名字数量一致（6 个）。
- [ ] P2-A5-01：`rg "nop-plugin" ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md` — 不再把 `nop-plugin` 表述为顶层模块；明确标注嵌套在 `nop-core-framework` 下。
- [ ] P2-A6-01：`rg "七层" ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md` 无命中（已改为正确计数）；正确计数与 `ls ai-dev/` 目录数一致。
- [ ] P2-A6-02：`...-nop-engineering-dx-ai-dev.md:158` 的 `docs-for-ai/` 结构项数与 live `INDEX.md` 一致。
- [ ] P2-A6-03：`...-nop-engineering-dx-ai-dev.md:200` 的锚点计数与 `rg -c` live 计数一致（不再写「~90」）。
- [ ] P2-A6-04：`...-nop-engineering-dx-ai-dev.md:91` 的 events.jsonl 计数已标注「历史快照/不可复现」或改为定性描述。
- [ ] OA-2：`...-nop-engineering-dx-ai-dev.md:150` 的模块骨架含 `codegen` 步骤（与 `INDEX.md:207` 一致）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码不因本 Phase 变差（mission 文件集仍 0 broken link）。
- [ ] No owner-doc update required: 本 Phase 仅修改分析交付物（`ai-dev/analysis/`），不修改 `docs-for-ai/`。
- [ ] `ai-dev/logs/2026/07-26.md` 已追加本 Phase 的变更记录。

### Phase 2 - 修正 capstone A7 精度缺陷 + mission config 注释 + 终检

Status: planned
Targets: `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md`、`missions/nop-deep-analysis.json`、`ai-dev/audits/nop-deep-analysis-audit-followups.md`

- Item Types: `Fix | Decision | Follow-up`

- [ ] **Fix [P2-A7-01]** `...-nop-platform-deep-introduction.md:335-337`：§8.5 deferred-items 汇总算术修正——逐项重数 §8.1（迁移决策）+ §8.2（文档治理任务）+ §8.3（residual）+ §8.4（排除），使小计、总计、括号内分项加法三者自洽。
- [ ] **Fix [P2-A7-02]** `...-nop-platform-deep-introduction.md:102`：mermaid 节点覆盖声明——要么为 `API`/`OPS` 节点补 provenance 行使「23 行覆盖全部 25 节点」成立，要么修正声明为实际覆盖数。
- [ ] **Fix [P2-A7-03]** `...-nop-platform-deep-introduction.md:167, :316, :329`：`A2 §8(c)` 标签歧义消解——区分 `A2 §8(c)` 启动性能（`:368`）与 `A2 §8(b)` 或行号引用 `@BizAction`-AOP（`:367`），使每个标签唯一对应一个 A2 open-question 条目。
- [ ] **Fix [OA-1]** `...-nop-platform-deep-introduction.md:279, :337, :365`：§8.5 `_tmp/` provenance 脆弱性——将关键计数摘要内联到 capstone §8 正文（使该节自包含），或在引用处显式标注「`_tmp/` 为执行期草稿，计数已内联/可从 §8.1–§8.4 逐项复核」。
- [ ] **Decision [OA-4]** `missions/nop-deep-analysis.json:15`：为 `commands.test` 的 `; echo` 模式补注释（如 JSON `//` comment 或相邻文档注记），说明「analysis mission 的 test 命令为 doc-links 检查，退出码被 `; echo` 吸收是已知设计选择，非 bug」。
- [ ] **Follow-up** 在 `ai-dev/audits/nop-deep-analysis-audit-followups.md` 中，将全部 16 项 P2 finding 行尾标注 `→ done in 2026-07-26-1000-1-p2-audit-findings-remediation`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] P2-A7-01：§8.5 的小计、总计、括号内分项加法三者自洽（逐项重数验证，非仅改数字）。
- [ ] P2-A7-02：mermaid 节点覆盖声明与实际 provenance 表行数一致（要么补行使声明成立，要么修正声明数字）。
- [ ] P2-A7-03：`rg "A2 §8\(c\)" ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md` — 每处命中的语义唯一，不再有两个不同条目共用同一标签。
- [ ] OA-1：§8.5 不再 load-bearing 依赖 `_tmp/` 文件——关键计数可从 §8 正文自包含复核，或 `_tmp/` 引用处有显式不可复现标注。
- [ ] OA-4：`missions/nop-deep-analysis.json` 的 `commands.test` no-op 设计选择有文档记录（注释或相邻注记）。
- [ ] `nop-deep-analysis-audit-followups.md` 全部 16 项 P2 finding 行尾标注 `→ done in ...`。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码不因本 Phase 变差（mission 文件集仍 0 broken link）。
- [ ] No owner-doc update required: 本 Phase 仅修改分析交付物 + mission config + audit backlog，不修改 `docs-for-ai/`。
- [ ] `ai-dev/logs/2026/07-26.md` 已追加本 Phase 的变更记录。

## Closure Gates

> **纯文档计划**：本 plan 不涉及任何 Java 代码变更（仅修改 `ai-dev/analysis/`、`missions/`、`ai-dev/audits/` 下的文件），故 `./mvnw compile` / `./mvnw test` / checkstyle 等构建验证条目不适用，已删除。质量门为 doc-links checker + grep 验证 + 独立 closure audit。

- [ ] 全部 16 项 in-scope P2 confirmed accuracy defects 已修复（12 multi-audit + 4 open-audit）。
- [ ] 每项修正都经源码/文件 live 交叉核对（非仅引用审计结论）。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope P2 项。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 在本 mission 文件集上 0 broken link（仅保证 markdown 链接不断）。
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（本 plan 为分析交付物精度修正，不修改 docs-for-ai/）。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**（文档计划适配版）：closure audit 已通过 grep 抽查每项修正——不存在「文档说改了但实际 grep 仍命中旧值」的空壳修正。

## Deferred But Adjudicated

（无。本 plan 不延期任何 in-scope P2 项。）

## Non-Blocking Follow-ups

- A7 capstone `Non-Blocking Follow-ups` 中的 14 项文档治理任务（如 `x:override` 8 模式补全、source-anchors 新增锚点等）pending human decision，不在本 plan 范围。
- capstone §6 演进建议 E1–E8 pending human decision on roadmap adoption。

## Closure

Status Note: （完成后填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （完成后填写）
- Audit Session: （完成后填写）
- Evidence: （完成后填写）

Follow-up:

- （完成后填写）
