# 2307 full-code-review 0.5 基线刷新：矩阵与模块分组文档对齐 v6 新增模块面

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 0.5（v6 增补）+ `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md`
> Related: `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/00-scope-and-dimension-matrix.md`
> Draft Review: agent_01207ebf（2026-10-03，独立对抗审查：4 Major + 5 Minor 全部修复后放行，见 daily log）

## Purpose

把 2026-09-22 编制基线当日或之后入库、未入 v5 覆盖的新顶层组与新子模块纳入全仓代码审查的审计维度矩阵，并同步 roadmap 交叉引用与 `docs-for-ai/01-repo-map/module-groups.md`，使矩阵 §1/§2/§6、roadmap、repo-map 三处与 live repo 一致。本计划是 roadmap 0.5 的执行载体，完成后 1.9/1.10/2.8/4.6 四个首审工作项的前置（0.5）即告成立。

## Current Baseline

以下事实已于 2026-10-03 在 live repo 实测核对；**Java 计数统一口径：`src/main/java` 与 `src/test/java` 下的 `.java` 文件（不含 test resources 与 target）**：

- roadmap v6（commit 20dd0fe9e7）已入库，工作项 0.5 为 `todo`，是串行执行顺序中第一个 todo 项。
- 新顶层组 4 个，live 计数：
  - `nop-jq`：main 71 / test 7，0 子模块（parent=nop-entropy，2026-09-28 自 nop-kernel 迁出）→ 量化评级 **B**（71∈50–99）。
  - `nop-refactor`：main 33 / test 16，3 子模块（core/java/graphql）→ **C**。
  - `nop-bytecode`：main 27 / test 13，0 子模块（扁平单模块）→ **C**。
  - `nop-duckdb`：main 6 / test 22，0 子模块 → **C**。
- 矩阵 §1 缺 `nop-core-framework` 行（§2 有该行）：live main 229 / test 124，6 子模块（boot/config/ioc/log/plugin/security，其中 plugin 为嵌套组 api/manager/support）→ **S**。
- 矩阵 §1 `nop-kernel` 行注记「jq 空壳」已过时（jq 已迁出）；live 重测 main 2788 / test 451，子模块 17（含 `nop-record-mapping`，不含 jq）。
- 矩阵 §1 子模块计数过时：
  - `nop-stream` 10→11（+`nop-stream-sql`，main 14/test 18，2026-10-02 入库）；行计数 live 实测 main 770 / test 784。
  - `nop-code` 12→15（+`nop-code-lang-go/rust/csharp` 各 main 3，2026-09-29 入库）；live main 261 / test 137。live 量化已双过 S 线（main≥200 且子模块≥5），编制期评 A——本计划维持 A 但加人工调整注记（已闭包、增量归 7.4）。
  - `nop-lint + nop-treesitter` 行的「6 子模块」口径过时：nop-lint 现 6 子模块（core/java/nop/js/maven-plugin/graphql，js/-maven-plugin/-graphql 于 2026-09-22/24/25 入库）+ nop-treesitter 单模块；live main 160+56=216 / test 133+71=204，量化过 S 线 → 评级 A→S。
- 矩阵 §1「其他」行（`nop-migration / nop-benchmark / nop-record / nop-utils`）两处失实：① `nop-record` 列为顶层组是幻影——实际位于 `nop-format/nop-record`、`nop-format/nop-record-netty`、`nop-kernel/nop-record-mapping`；② 计数格「<70 合计 / C」失真——live 实测 migration 3/1（1 子模块）+ benchmark 85/0（5 子模块）+ utils 119/24（10 子模块）= main 207 / test 25——量化过 S 线（main≥200 且子模块≥5），人工下调 A。
- `nop-benchmark/nop-benchmark-stream`（16 main JMH，2026-09-26 创建，commit c794cfce34）未在矩阵登记。
- 矩阵 §2 现有 18 行、❓ 格 55（v4/v5 审查已验证该基线计数）；§6 头部「四批并集 = 本矩阵 §2 全部 18 行」。
- roadmap「审计维度矩阵」节写「8 维度 × 18 模块组覆盖矩阵（❓ 未审计格 55 格）」；Details 6.5–6.8 写「四批并集 = 矩阵 §2 全部 18 行」。roadmap「增补审查记录」节（v4/v5/v6 行）中的「18 行/55 格」字样是历史审查证据，不得回写。
- `docs-for-ai/01-repo-map/module-groups.md`：已有 nop-jq/nop-duckdb/nop-refactor 行；缺 nop-bytecode 行；nop-code 行语言适配器清单缺 go/rust/csharp、缺 `nop-code-codegen`（`nop-code-dao/meta/web/app` 已在列）；nop-lint 行写「`nop-lint-js`（TS/TSX）与生态模块（maven-plugin/graphql/cli）按 roadmap 后续 Wave 落地」已过时——js（TS/TSX 语言适配+tsc 桥）/maven-plugin（CheckMojo）/graphql（NopLintBizModel lint 查询面）均已入库，cli 未建。
- owner docs 存在性已核对：`docs-for-ai/03-modules/nop-bytecode.md`、`nop-duckdb.md`、`nop-refactor.md`、`nop-stream-sql.md` 均存在；nop-jq 无 03-modules 页（1.9 owner doc 为 module-groups.md + nop-refactor.md，与 roadmap 一致）。
- check 系列成于 2026-08-19~21，早于全部 v6 新增组/子模块入库 → 新组/新子模块无 check 系列发现。

## Goals

- 矩阵 §1 纳入 4 个新顶层组行 + 补 nop-core-framework 行 + 修正 nop-kernel/nop-stream/nop-code/nop-lint/其他行过时计数与注记。
- 矩阵 §2 新增 4 行（❓ 格 24 个）+ 新增子模块登记表（逐子模块 8 行）；❓ 计数 55→79 刷新。
- 矩阵 §6 增注新组不参与 P1 抽样分批，头部措辞与 §2 新行数自洽。
- roadmap「审计维度矩阵」节与 Details 6.5–6.8 的计数/并集措辞同步。
- `module-groups.md` 补 nop-bytecode 行、勘正 nop-code/nop-lint 行子模块清单。

## Non-Goals

- 不执行 1.9/1.10/2.8/4.6 首审本身（0.5 只是它们的前置）。
- 不修改任何产品代码、生成文件或 `_` 前缀文件。
- 不改矩阵既有 18 行的任何既有格状态（§2 的 ✅/⚠️/❓ 基线判定保持不动；§1 仅更新本计划点名的行）。
- 不重算 §1 既有行中未点名行的历史 Java 计数。
- 不新增或修改 roadmap 工作项行（0.5 交付物范围内的同步不算增补；除 0.5 Status 回填外不做任何表变更）。
- 不回写 roadmap「增补审查记录」节的历史审查证据。

## Scope

### In Scope

- `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/00-scope-and-dimension-matrix.md`（§1/§2/§6）
- `ai-dev/backlog/full-code-review-roadmap.md`（「审计维度矩阵」节、Details 6.5–6.8 措辞、0.5 Status 回填）
- `docs-for-ai/01-repo-map/module-groups.md`（3 行变更）
- `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md`（0.5 行登记）
- `ai-dev/logs/2026/10-03.md`（执行日志）

### Out Of Scope

- 矩阵 §3/§4/§5（既有审计覆盖摘要、未闭包清单、热点清单无 v6 变更点）
- `docs-for-ai/` 其他文件、`ai-dev/design/`、产品代码

## Execution Plan

### Phase 1 - 矩阵 §1：新组行 + core-framework 行 + 过时行修正

Status: completed
Targets: `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/00-scope-and-dimension-matrix.md`

- Item Types: `Fix`

- [x] 新增 4 行（列格式 模块组 | main/test Java | 子模块 | 评级 | 备注；插入位置按 §1 评级区段惯例：nop-jq 入 B 档区段（nop-search 行后），nop-refactor/nop-bytecode/nop-duckdb 入 C 档区段（nop-entropy-e2e 行后、「其他」行前））：
  - `nop-jq`：71 / 7 ｜ — ｜ **B** ｜ 2026-09-28 自 nop-kernel 迁出的扁平单模块；官方 jq 1.7.1 套件 430/430 为语义基线；首审 1.9
  - `nop-refactor`：33 / 16 ｜ 3 ｜ **C** ｜ 在建 AI-first 代码修改工具链（core/java/graphql）；首审 1.9
  - `nop-bytecode`：27 / 13 ｜ — ｜ **C** ｜ 字节码分析通道（report-only 发现流）；首审 1.10
  - `nop-duckdb`：6 / 22 ｜ — ｜ **C** ｜ 进程内分析执行层（扁平单模块）；首审 1.10
- [x] 新增 nop-core-framework 行（插入 nop-persistence 行后的 S 档区段）：229 / 124 ｜ 6 ｜ **S** ｜ boot/config/ioc/log/plugin/security（plugin 嵌套 api/manager/support）；CORE-02 defer 未修
- [x] nop-kernel 行：main/test 更新为 2788/451；备注「jq 空壳」改为「jq 已于 2026-09-28 迁出为顶层组 nop-jq」（同时删去备注中过时的子模块百分比明细）
- [x] nop-stream 行：main/test 更新为 770/784；子模块 10→11；备注补「nop-stream-sql 2026-10-02 入库（首审 2.8）」
- [x] nop-code 行：main/test 更新为 261/137；子模块 12→15；备注补「nop-code-lang-go/rust/csharp 2026-09-29 入库」；评级 A 加人工调整注记（量化已过 S 线，维持 A：审计已闭包、增量归 7.4 巡检）
- [x] nop-lint + nop-treesitter 行：main/test 更新为 216/204；子模块口径改为「6+1（nop-lint 6 子模块 core/java/nop/js/maven-plugin/graphql + nop-treesitter 单模块）」；评级 A→S 并备注原因（js/-maven-plugin/-graphql 入库后合计过 200 线；新增面首审归 4.6）
- [x] 「其他」行勘正：行名改为「nop-migration / nop-benchmark / nop-utils」；计数格改为 207 / 25；子模块格改为「16（migration 1、benchmark 5、utils 10）」；评级改为 **A**（备注注明量化达标、人工注记：均为迁移工具/基准/工具库性质）；备注改为「零专项审计；nop-record 非顶层组（实位于 nop-format/nop-record、nop-format/nop-record-netty、nop-kernel/nop-record-mapping）；nop-benchmark 含 nop-benchmark-stream（16 main JMH，2026-09-26）」

Exit Criteria:

- [x] §1 表含上述 5 个新行与 5 处行修正，计数与本计划 Current Baseline 实测值一致（统一 src/test/java 口径）
- [x] 既有行（除点名 5 行外）未被改动；既有格状态（§2）零改动
- [x] `ai-dev/logs/2026/10-03.md` 已更新

### Phase 2 - 矩阵 §2/§6：新行 24 ❓ 格 + 子模块登记表 + 批次注记

Status: completed
Targets: 同 Phase 1 文件

- Item Types: `Fix`

- [x] §2 表末（nop-credential 行后）新增 4 行（维度列 ①–⑧；❓ 后括注锚定首审工作项，未锚定者为兜底格）：
  - nop-jq：①❓（1.9）②N/A ③N/A ④❓（1.9）⑤❓（1.9）⑥❓ ⑦❓ ⑧❓
  - nop-refactor：①❓（1.9）②N/A ③❓（1.9）④❓（1.9）⑤❓（1.9）⑥❓ ⑦❓ ⑧❓
  - nop-bytecode：①❓（1.10）②N/A ③N/A ④N/A（report-only，仅读自家构建产物，无鉴权/求值面）⑤❓（1.10）⑥❓ ⑦❓ ⑧❓
  - nop-duckdb：①❓（1.10）②N/A ③N/A ④❓（文件读写面，兜底）⑤❓（1.10）⑥❓ ⑦❓ ⑧❓
- [x] ❓ 计数行（§2 表后）更新：55 格 → **79 格**（基线 18 行 55 格 + v6 新组 4 行 24 格），并注明「新组行未锚定专项首审的 ⑥⑦⑧ 格由全仓扫描类工作项（4.1/4.2/4.3、6.1/6.2）与文档审计（5.1/5.2）天然覆盖」
- [x] §2 计数行后新增「### v6 新增子模块登记（0.5）」小节，**逐子模块 8 行**：

  | 新增子模块 | 入库日期 | 所属模块组（§1 行） | 审计归属 |
  |---|---|---|---|
  | nop-stream-sql | 2026-10-02 | nop-stream（11 子模块） | 2.8 首审 |
  | nop-code-lang-go | 2026-09-29 | nop-code（15 子模块） | 7.4 增量巡检 |
  | nop-code-lang-rust | 2026-09-29 | nop-code（15 子模块） | 7.4 增量巡检 |
  | nop-code-lang-csharp | 2026-09-29 | nop-code（15 子模块） | 7.4 增量巡检 |
  | nop-lint-js | 2026-09-22 | nop-lint（6 子模块） | 4.6 首审 |
  | nop-lint-maven-plugin | 2026-09-24 | nop-lint（6 子模块） | 4.6 首审 |
  | nop-lint-graphql | 2026-09-25 | nop-lint（6 子模块） | 4.6 首审 |
  | nop-benchmark-stream | 2026-09-26 | nop-benchmark（其他行） | 4.2 测试有效性（JMH 基准为测试基建、非生产代码） |

- [x] §6 头部措辞改为「四批并集 = 本矩阵 §2 全部 18 个基线行（v6 新增 4 行不参与，见下方注记）」，并在 §6 表后增注：「v6 基线刷新注记：check 系列（2026-08-19~21）早于全部 v6 新增组/子模块入库，4 个新顶层组（nop-jq/nop-refactor/nop-bytecode/nop-duckdb）无 check 系列发现，不参与 6.5–6.8 的 P1 抽样分批。」

Exit Criteria:

- [x] §2 新 4 行逐格与上述定义一致，新组行合计 ❓ 恰为 24（6+7+5+6），总计数 55+24=79
- [x] 子模块登记表恰为 8 行（逐子模块），审计归属与 roadmap Details（2.8/7.4/4.6/4.2）一致
- [x] §6 注记与头部措辞自洽；既有 18 行格状态零改动
- [x] `ai-dev/logs/2026/10-03.md` 已更新

### Phase 3 - roadmap 交叉引用同步

Status: completed
Targets: `ai-dev/backlog/full-code-review-roadmap.md`

- Item Types: `Fix`

- [x] 「审计维度矩阵」节：`8 维度 × 18 模块组覆盖矩阵（❓ 未审计格 55 格 → MA1–MA6 工作项来源）` 改为 `8 维度 × 22 模块组覆盖矩阵（❓ 未审计格 79 格 = 基线 55 + v6 新组 24 → MA1–MA6 工作项来源；v6 新增子模块另见矩阵 §2 登记表）`
- [x] Details 6.5–6.8：「四批并集 = 矩阵 §2 全部 18 行」改为「四批并集 = 矩阵 §2 全部 18 个基线行（v6 新增行无 check 发现、不参与，见矩阵 §6 注记）」
- [ ] 0.5 行 Status `todo` → `done`（在 Phase 5 closure audit 通过后回填）

Exit Criteria:

- [x] roadmap「审计维度矩阵」节与 Details 6.5–6.8 条目中不再有过时的「18 行/55 格」表述（**验证范围仅限这两处目标段，排除「增补审查记录」节——该节是规则 #5 要求保留的历史审查证据，不作回写**）
- [x] 除 0.5 Status 回填外 roadmap 工作项表无任何其他变更
- [x] `ai-dev/logs/2026/10-03.md` 已更新

### Phase 4 - module-groups.md 同步

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Fix`

- [x] 新增 nop-bytecode 行：编译产物之上的字节码分析通道（与源码通道 nop-lint 零依赖并行）；采集层/内核层（CFG+槽位抽象解释）/nullflow 分析器/发现流 CLI 四层；report-only 退出码语义（0=完成、2=失败）；`nullflow/` 规则命名空间独立于 nop-lint
- [x] nop-code 行：语言适配器清单 `nop-code-lang-java/python/typescript` 扩为 `java/python/typescript/go/rust/csharp`（go/rust/csharp 2026-09-29 入库）；清单补 `nop-code-codegen`
- [x] nop-lint 行：js/maven-plugin/graphql 从「按 roadmap 后续 Wave 落地」改为已入库（js=TS/TSX 语言适配与 tsc 类型桥、maven-plugin=CheckMojo 构建 check goal、graphql=NopLintBizModel lint 查询面）；注明 cli 未建

Exit Criteria:

- [x] 三处变更与 live 子模块清单一致（`ls nop-code/`、`ls nop-lint/` 验证）
- [x] `docs-for-ai/INDEX.md` 无需变更（无新增文档文件，仅行内容更新）
- [x] `ai-dev/logs/2026/10-03.md` 已更新

### Phase 5 - 一致性验证与收口

Status: completed
Targets: 全部上述文件 + index + log

- Item Types: `Proof`

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 三方一致性核对：矩阵 §2 行数（22）/❓ 计数（79）= roadmap「审计维度矩阵」节计数；§6 与 6.5–6.8 并集措辞一致；module-groups.md 与 §1 新行计数一致
- [x] index.md 报告清单登记 0.5 行（报告 = 矩阵文件，状态、closure audit 结论）
- [x] roadmap 0.5 Status 回填 `done`；依赖 0.5 的 1.9/1.10/2.8/4.6 解除阻塞（状态仍 todo，不动）
- [x] 独立子 agent closure audit 通过后，本 plan 走 plan guide 关闭流程（checklist 工具 + Closure Evidence 写入）

Exit Criteria:

- [x] check-doc-links --strict 退出码 0
- [x] index/roadmap/log 三处登记一致
- [x] 独立 closure audit 证据写入本 plan Closure 段

## Closure Gates

> 纯文档计划（仅 `docs-for-ai/`、`ai-dev/` 下文件变更），按 plan guide 免除 `./mvnw` 构建验证项；引用当前绿色基线（2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS）。

- [x] 矩阵 §1/§2/§6 全部变更落地且与 live repo 实测一致（统一 src/test/java 口径）
- [x] roadmap 与 module-groups.md 交叉引用全部同步；「审计维度矩阵」节与 Details 6.5–6.8 无过时「18 行/55 格」残留（排除「增补审查记录」节）
- [x] 矩阵既有 18 行的既有格状态零改动（git diff 验证）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项（0.5 交付物 ①②③④ 全部落地）
- [x] 独立子 agent closure audit 已完成并记录证据（含 index 登记与实际变更一一对应核对）
- [x] `ai-dev/logs/2026/10-03.md` 收口记录已写

## Deferred But Adjudicated

（无——0.5 交付物四组全部 in scope）

## Non-Blocking Follow-ups

- 矩阵 §1 未点名行的历史 Java 计数未重算（编制期口径残留，如部分行是否含 test resources 不一）——不影响 0.5 交付物；后续 Wave 1 各组审计时以实测为准。

## Closure

Status Note: roadmap 0.5 四组交付物（①矩阵 §1/§2 纳入+❓ 刷新+§1 对齐、②§6 注记、③roadmap 交叉引用同步、④module-groups.md 同步）全部落地并经独立子 agent 逐条验证与 live repo 一致；纯文档计划，按 guide 免除构建验证（引用 2026-09-22 绿色基线）；Non-Blocking Follow-ups 诚实（未点名行保留编制期近似值，未被伪装为已重算）。依赖 0.5 的 1.9/1.10/2.8/4.6 解除阻塞。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: agent_9eb9fd42-a49c-4ee2-88d3-0000215b0b7c（独立 fresh-session closure auditor，只读）
- Evidence:
  - Phase 1–5 全部 Exit Criteria：PASS（审计报告逐条附 matrix:line/roadmap:line 证据；10 项数值独立复测全中：jq 71、stream 770/784、lint 6 子模块、基线 18 行 ❓=55（HEAD 与 live 双侧）、增补审查记录 v4/v5/v6 未回写、四新组计数、登记表 8 个入库日期经 `git log --diff-filter=A` 逐一验证、benchmark-stream 16 main、code 15 子模块、check-doc-links=0）
  - Closure Gates 7/7：PASS（git diff 证明被修改 §1 行恰为点名 5 行、§2 基线格零改动；交付物 ①②③④ 全落地；Deferred 段为空）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（3 个 warning 为 `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md` 既有问题，不在本次变更集）
  - Anti-Hollow（文档计划变体）：交付物无静默缺口；index 0.5 行登记与实际变更一一对应
  - Deferred 项分类检查：无 in-scope live defect 被降级；审计发现的 2 个 Minor（plan 叙述层「量化达 A」措辞、§2 计数行增量澄清）前者已就地修复、后者为无害超集
  - 纯文档计划：`./mvnw` 构建验证按 guide 免除
