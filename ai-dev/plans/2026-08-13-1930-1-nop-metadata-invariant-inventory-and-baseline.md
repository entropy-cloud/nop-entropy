# I0 — nop-metadata 不变式盘点与基线（Invariant Inventory & Baseline）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 1 / I0（不变式盘点与基线）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I0）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: 后继 `2026-08-13-1930-2-...`（I1 沉淀门禁）；历史线性审计 `nop-metadata-audit-remediation-roadmap.md`（MA1-MA7 + MR1-MR8 全 done）

## Purpose

把 nop-metadata 经历的 5 轮 multi+open 审计 + ARM MA1-MA7（21 维）+ MR1-MR8（含 R6.1-R6.6/R7.3/R8.1-R8.4b 子轮）散落在数十个 audit 文件中的**反复复发的失败模式族**，收口为一份**结构化的不变式目录**，并枚举出**审计目标集**（全部 service/processor/bizmodel 方法 + 39 ORM 实体 + 37 unique-key）。这是闭环飞轮的入口（I0），其产出（不变式目录 + 目标集）直接为 I1 的门禁实现提供"要沉淀什么"。

## Current Baseline

> 以下事实均为 2026-08-13 live repo 实测（见 Source 锚点），非记忆。

- **模块规模**：nop-metadata 含 8 子模块（`nop-metadata-{api,app,codegen,core,dao,meta,service,web}`）；`nop-metadata/model/nop-metadata.orm.xml` 含 **39 个 `<entity>`**。
- **unique-key 现状**：`nop-metadata/model/nop-metadata.orm.xml` 含 **37 个 `<unique-key name=` 元素**，其中 **36 个带 `constraint=` 属性，1 个缺失**（Lesson 09 记录 R3.19 修复了 36 个，本次实测确认仍余 1 个 —— 该 1 个是 I2 red-list 的预期条目之一）。
- **catch 块面**：`nop-metadata-service/src/main/java` 下实测 **130 个 catch 块（分布在 46 个文件）**（静默吞异常族的目标扫描面）。注：46 是文件数，130 是 catch 块数 —— 本计划及 I1 均以"块数 130"为穷举目标集口径。
- **limit 入口面**：`nop-metadata-service/src/main/java` 下约 **29 个文件引用 `limit`**（limit 负值校验族的穷举目标集）。
- **已知失败族（来自 roadmap 诊断）**：
  - 静默吞异常族（silent-swallow / fail-loud）：≥7 兄弟实例横跨多轮（P2-06/07/09、P2-01/02/04、AR-21）。
  - Limit 负值校验族：AR-09（R6.6）→ AR-23④（R8.2，显式"沿 AR-09 先例"）。
  - 敏感字面量脱敏族：R6.2 P2-12（`ARG_RAW_JDBC_URL` 移除）→ R8.2 AR-16（SQL 字面量 → sqlHash，显式跨引用）。
  - DDL / unique-key 静默缺失族：Lesson 09（36 个 unique-key 曾缺 constraint，R3.19 修复；但未自动化为 CI 门禁，仍余 1 个）。
- **CI 门禁基线**：**零**代码不变式门禁。`ai-dev/tools/` 下无 `check-silent-swallow.mjs` / `check-orm-unique-key-constraint.mjs` / `check-sensitive-literal-leak.mjs`；无 invariant 相关 JUnit 参数化穷举测试；`ai-dev/audits/nop-metadata-invariants/` 目录**不存在**。
- **已有可复用工具模式**：`ai-dev/tools/*.mjs` Node 脚本（如 `scan-hollow-implementations.mjs`、`check-import-order.mjs`）+ `ai-dev/tools/rules/` ast-grep YAML 规则（已有 3 条 Java lint 规则）。
- **虚假关闭先例**：arm-index MR7 R7.3 核查发现 R3.14 P2-MA7.6-05（AR-06）声称已修复（commit `9b769490e`），但该文件 diff real lines = 0（只有版权头）—— 修复从未落地。这是"接口/commit 声称已修 ≠ 行为已落地"的实证，本计划审计目标集必须以 live 实测为准。

## Goals

- 产出不变式目录 `ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`：每条不变式含「陈述 / 覆盖的失败族 / 历史 audit-finding-ID 证据 / 检测方法」四要素。
- 产出审计目标集：nop-metadata 全部 service/processor/bizmodel **方法**的枚举清单 + 39 ORM entity / 37 unique-key 清单（作为 I2 审计与 I1 表完备性门禁的"全集"基准）。
- 确认 CI 门禁基线 = 当前零不变式门禁，并记录 I1 之后将以此为零点单调棘轮。

## Non-Goals

- **实现门禁**（脚本 / JUnit / ArchUnit）—— 那是 I1（`2026-08-13-1930-2`）。
- **跑门禁产出 red list / 裁决违规** —— 那是 I2/I3（`2026-08-13-1930-3`）。
- **修复任何代码或 ORM 模型** —— 那是 I4。
- **重新审计**已完成的线性管道（MA1-MA7 + MR1-MR8）范围；本计划只做"盘点提取"，不做新审计。

## Scope

### In Scope

- 不变式目录文件（首批至少覆盖 roadmap I1 所列 4 族：静默吞异常、unique-key constraint、limit 负值、敏感字面量脱敏）。
- 审计目标集枚举（方法全集 + ORM entity/unique-key 全集）。
- 基线确认（零门禁 + 验证命令记录）。

### Out Of Scope

- 门禁实现（I1）。
- red list 产出与裁决（I2/I3）。
- 任何源码 / ORM 模型 / DDL 修改（I4）。
- 非 nop-metadata 模块。

## Execution Plan

### Phase 1 — 审计目标集枚举（Audit Target Enumeration）

Status: completed
Targets: `ai-dev/audits/nop-metadata-invariants/audit-target-set.md`（新建目录与文件）

- Item Types: `Proof | Decision`

- [x] 枚举 service/processor/bizmodel **方法全集**：BizModel 全部位于 `nop-metadata-service`（web/core/app 实测零 `@BizModel`，不扫）。枚举口径 = **变更型入口面**（`@BizAction` / `@BizQuery` / `@BizModel` 类的 public action/query 方法 + 显式接受 limit 入参的 public 方法），**不是**全部 `public` 符号（service 下实测 723 个 `public`，多为 helper/getter，不属于审计目标面）。产出"类 → 方法 → 是否 limit-taking / 是否含 catch"三列表
- [x] 枚举 ORM 全集：39 entity × 37 unique-key 的"entity / unique-key-name / columns / 是否带 constraint"表（live 实测，标记那 1 个缺失 constraint 的）
- [x] 标注每个目标方法的"已审计轮次"归属（尽量回溯到具体 audit-finding-ID；无法回溯的标"未单独审计"）

Exit Criteria:

- [x] `audit-target-set.md` 存在且含两节：方法全集表（可被 `rg` 复核计数）与 ORM 全集表（39 entity + 37 unique-key，其中 1 个标 missing-constraint）
- [x] 方法全集计数可由独立 `rg`/`grep` 复现：可复现指标 = `@BizAction`+`@BizQuery`+`@BizModel`-类 public 入口方法数 + limit-taking 方法数（每个口径配一条记录的 `rg` 命令）；**不**要求复现全部 723 个 `public`
- [x] ORM 全集的"missing-constraint"条目数 = live 实测值（1），不是照抄历史 Lesson 09 的"36 全缺"
- [x] **端到端验证**（不适用 — 本 Phase 为文档产出；以"独立 `rg` 复核计数与文档一致"替代）
- [x] **接线验证**（不适用）
- [x] **无静默跳过**（不适用）
- [x] No owner-doc update required（本 Phase 仅在 `ai-dev/audits/` 下产出；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

> Phase 1 实测结果：方法全集 = 40 `@BizModel` 类 + 13 `@BizQuery` + 30 `@BizMutation` + 0 `@BizAction`（变更型入口面合计 43）+ 4 limit-taking 方法；catch 目标集 = 130 块 / 46 文件。ORM 全集 = 39 entity + 37 unique-key。**基线校正**：live XML-aware 核对显示 unique-key missing-constraint = **0**（非 plan baseline 沿用的旧值 1）——已写入 `audit-target-set.md`「基线校正记录」段并纳入 INV-UK 元规则。

### Phase 2 — 不变式目录编写（Invariant Catalog）

Status: completed
Targets: `ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`

- Item Types: `Decision | Proof`

- [x] 为首批 4 族各编写一条不变式，每条含四要素：① 不变式陈述（可判定的"每个 X 必须/不得 Y"；**通用陈述**，不绑死 nop-metadata —— 如 unique-key 族陈述为"每个 `<unique-key>` 必须带 `constraint=` 属性"，使 I1 扫描器可按需扩到全仓）；② 覆盖的失败族名称；③ 历史 audit-finding-ID 证据（带 `文件:行` 或 audit 文件名）；④ 检测方法（静态扫描器 / JUnit 参数化 / ArchUnit —— 与 I1 落地形式对齐）
- [x] 对每条不变式，回填"目标集覆盖率"：该不变式作用于 Phase 1 目标集的哪个子集（如"全部 service-tier catch 块 = 46 处"）
- [x] 记录"虚假关闭"教训作为目录的元规则：不变式判定必须以 live 实测为准，commit message / 旧 completion note 不足为据（引 AR-06 先例）
- [x] 记录 CI 门禁基线段：当前零门禁 + I5 收口时的目标门禁清单（与 4 条不变式一一对应）+ 棘轮规则引用

Exit Criteria:

- [x] `invariant-catalog.md` 存在且含 ≥4 条不变式，每条四要素齐全
- [x] 每条不变式的"检测方法"与 I1 roadmap 所列落地形式一致（不发明目录外的检测手段）
- [x] 每条不变式引用的 audit-finding-ID 在 `ai-dev/audits/` 下可定位（独立 grep 复核无悬空引用）
- [x] 目录含"基线 = 零门禁"显式声明 + 棘轮规则引用（指向 invariant-loop-audit-prompt.md 关键机制 #2）
- [x] **端到端验证**（不适用 — 文档产出）
- [x] **接线验证**（不适用）
- [x] **无静默跳过**（不适用）
- [x] No owner-doc update required（产出在 `ai-dev/audits/` 下；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

> Phase 2 实测结果：产出 4 条不变式（INV-SILENT-SWALLOW / INV-UK / INV-LIMIT / INV-SENSITIVE），每条四要素 + 覆盖率齐全；元规则段引 AR-06 + INV-UK live 校正实证；CI 门禁基线段含零点声明 + I5 目标门禁清单 + 棘轮规则引用。13 个 audit-finding-ID 引用经独立 grep 复核全部非悬空。

## Closure Gates

> 本计划为纯文档/盘点计划（不改产品代码 / ORM 模型 / DDL），故 `./mvnw test` / `checkstyle` 等构建验证条目可删除。保留文档链接与一致性检查。

- [x] 不变式目录（≥4 条）与审计目标集均已落盘于 `ai-dev/audits/nop-metadata-invariants/`
- [x] 所有计数声明（39 entity / 37 unique-key / 46 catch / ~29 limit-file）经独立 `rg` 复核与文档一致
- [x] 无悬空 audit-finding-ID 引用
- [x] 基线"零门禁"声明存在，为 I1/I5 提供单调棘轮零点
- [x] 受影响的 owner docs：No owner-doc update required（本计划不动 `docs-for-ai/`；I1 实现门禁后再考虑 owner-doc 同步）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**（不适用 — 无代码变更；以"目录条目可被独立 grep 复现"替代）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本计划产出（`audit-target-set.md` / `invariant-catalog.md`）引入 **0** 新增断裂链接（checker "Files with Issues" 表未含两文件；且创建 `invariant-catalog.md` 已消除 nop-metadata roadmap 对该路径的潜在悬空引用）。checker 报 20 errors / 21 warnings 均为**预先存在**的跨文件前向引用（6 个其他模块 roadmap → `docs/backlog/ai-invariant-loop-roadmap.md`、各模块 → 尚未创建的 invariant-catalog、successor plan `2026-08-13-1930-2/-3` → I1/I2/I3 将创建的 check 脚本与 red-list），非本计划引入、非本计划可闭合（多数将在 I1/I2/I3 执行时自然 resolve）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前必跑，见 Minimum Rules #26）— 实测 exit 0

## Deferred But Adjudicated

（本计划为闭环入口，无 deferred 项；4 族之外的新族如需沉淀留待 I6 触发 Cycle 2 / I1）

## Non-Blocking Follow-ups

- 若 Phase 1 枚举发现超出首批 4 族的高频复发模式，记录为"候选不变式"附在目录末尾，但不展开 —— 留待 I6 裁定是否派生 Cycle 2。

## Closure

Status Note: I0（不变式盘点与基线）完成——产出不变式目录（4 条）+ 审计目标集（方法全集 + ORM 全集）+ CI 门禁零点基线。本计划为纯文档/盘点（无代码变更），已剔除 `./mvnw test`/checkstyle 等构建验证条目。live 实测发现 plan baseline 的「unique-key 1 missing」已过时（实际 0 missing），按虚假关闭元规则以 live 值收口，为 I1/I2 提供可信零点。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit（fresh session 子 agent，task: closure-audit-I0）
- Audit Session: opencode fresh subagent session（与执行 session 不同）
- Evidence:
  - **Phase 1 Exit Criteria（全 PASS）**：`ai-dev/audits/nop-metadata-invariants/audit-target-set.md` 存在，含方法全集表（40 `@BizModel`/13 `@BizQuery`/30 `@BizMutation`/0 `@BizAction`/4 limit-taking/130 catch 块-46 文件）+ ORM 全集表（39 entity + 37 unique-key）。每项计数配独立 `rg` 命令，复核实测一致（见文档内 §1.1/§1.3/§1.4/§2.2）。
  - **Phase 2 Exit Criteria（全 PASS）**：`invariant-catalog.md` 存在，含 4 条不变式（INV-SILENT-SWALLOW/INV-UK/INV-LIMIT/INV-SENSITIVE），每条四要素齐全（陈述/失败族/audit-finding-ID 证据/检测方法）+ 覆盖率回填。检测方法与 I1 roadmap 落地形式一致（静态扫描器/JUnit 参数化/JUnit 表完备性）。13 个 audit-finding-ID 引用经独立 grep 复核（`sed -n` + `rg`）全部在 `ai-dev/audits/arm-index-nop-metadata.md` 对应行命中，无悬空。目录含「基线=零门禁」显式声明 + I5 目标门禁清单 + 棘轮规则引用（指向 `invariant-loop-audit-prompt.md` 关键机制 #2）。
  - **Closure Gates（全 PASS）**：计数声明经独立 `rg` 复核一致（39 entity / 37 unique-key / 130 catch / 46 文件 / 29 limit-文件 / 0 missing-constraint）；基线零门禁声明存在；无悬空引用。
  - **基线校正**（虚假关闭元规则实证）：plan baseline「unique-key 1 missing」经 live XML-aware 核对为 0 missing——已写入 `audit-target-set.md`「基线校正记录」+ `invariant-catalog.md` 元规则段，以 live 值收口（INV-UK 当前 red list = 0）。
  - `node ai-dev/tools/check-doc-links.mjs --strict`：本计划产出引入 0 新增断裂链接（两产出文件不在 checker "Files with Issues" 表）；checker 报 20 errors / 21 warnings 均为预先存在的跨文件前向引用（其他模块 roadmap + successor plan `1930-2/-3` 指向 I1/I2/I3 将创建的 check 脚本与 red-list），非本计划引入、非 I0 可闭合。创建 `invariant-catalog.md` 已消除 nop-metadata roadmap 对该路径的潜在悬空引用。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（实测 passed: 1 / failed: 0，确认无未勾选项 + Closure Evidence 已写入）。
  - **Anti-Hollow 检查**（不适用 — 无代码变更）：以「目录条目可被独立 grep/脚本复现」替代——`audit-target-set.md` §3 提供完整 XML-aware 复现脚本（预期 matched 37 / missing 0），所有计数配独立 rg 命令。
  - **Deferred 项分类检查**：本计划无 deferred 项；Non-Blocking Follow-ups 仅记录 2 个候选不变式（类型/方言族、并发竞态族），均声明 Why Not Blocking（单点命中未见同族复发 / 已裁定 watch-only），无 in-scope live defect 被降级。

Follow-up:

- I1（plan `2026-08-13-1930-2`）消费本目录落地 4 族门禁；I2/I3（plan `2026-08-13-1930-3`）消费目标集跑门禁 + 对抗探查。
- INV-SILENT-SWALLOW 落地时需裁定与现有通用 ast-grep 规则（`java-lint-empty-catch.yml`/`java-lint-getmessage-only.yml`）的关系（扩展 vs 新增）。
- 无剩余 plan-owned work。
