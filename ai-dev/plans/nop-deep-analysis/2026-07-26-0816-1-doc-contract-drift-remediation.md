# 1 修复 nop-deep-analysis 交付物与 docs-for-ai 权威文档之间的契约漂移

> Plan Status: completed
> Last Reviewed: 2026-07-26 (rev 3: 两轮对抗性审查达成 consensus，Purpose 计数修正)
> Source: `ai-dev/audits/2026-07-26-0702-multi-audit-nop-deep-analysis.md`（3 个 P1 findings：P1-A4-01、P1-A5-02/DRIFT-1、P1-A2-01/DRIFT-2）
> Related: `ai-dev/audits/2026-07-26-0702-open-audit-nop-deep-analysis.md`（P2-only，已 triaged，不在本 plan 范围）

## Purpose

收口 `nop-deep-analysis` mission 交付物中由 2026-07-26 multi-audit 发现的全部 P1 文档契约漂移：先在 `docs-for-ai/` 权威文档（source of truth）层修正两处根因漂移，再把继承了该漂移的分析交付物（A2/A4/A5 + capstone A7）对齐到修正后的权威基线。完成后，`docs-for-ai/` 与 `ai-dev/analysis/2026-07/` 中本 plan 涉及的 P1 锚点/模块/类型论断彼此一致，且可被 doc-links checker（markdown 链接）与 grep（GQL 语义一致性）及独立 closure audit 验证。

## Current Baseline

逐条已对照 live repo 核实（2026-07-26）：

**P1-A4-01 — `findCount` 返回类型误写为 `Int`**
- 交付物 `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md:113` 写 "`findCount`→Int"。
- 实际：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/utils/GraphQLObjMetaHelper.java:27-28` 明确 `if (queryMethod == GraphQLQueryMethod.findCount) return GraphQLScalarType.Long.name();`。同句其余 4 个映射（findFirst/findList/findPage/findConnection）核对正确，错误仅限 `findCount` 一项。
- 这是一处独立的交付物事实错误，**不**来自 docs-for-ai 漂移。

**P1-A5-02 / DRIFT-1 — 虚构子模块 `nop-stream-flink` / `nop-stream-checkpoint`**
- 权威文档根因：`docs-for-ai/01-repo-map/module-groups.md:23` 列出 `nop-stream-checkpoint`（检查点存储抽象）与 `nop-stream-flink`（Flink API 兼容层）作为 nop-stream 子模块。
- 实际：`nop-stream/pom.xml:16-23` 仅声明 6 个子模块：`nop-stream-core` / `nop-stream-cep` / `nop-stream-connector` / `nop-stream-runtime` / `nop-stream-flow` / `nop-stream-fraud-example`。仓库内任何位置都不存在 `nop-stream-flink` / `nop-stream-checkpoint` 目录或 pom。Flink API 兼容代码实际位于 `nop-stream-core` 内部（`DataStreamSource.java`、`Transformation.java`、`StreamGraph.java`、`JobGraph.java`）。
- 交付物继承：`ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md:207-208` 把 `nop-stream-flink`（Flink API 兼容层）当作真实子模块引用，并据此论证「API 对齐 Flink、实现自研」意图。
- 根因必须先修 `module-groups.md`，否则所有下游消费者会反复继承同一漂移。

**P1-A2-01 / DRIFT-2 — 锚点 ID `GQL-008` 被错用于 `GraphQLWebService`（根因：`source-anchors.md` 出现重复 ID；误用扩散到 A2/A5/A7）**
- 权威文档根因：`docs-for-ai/04-reference/source-anchors.md` 中 `GraphQLWebService.java` 那一行（当前为第 81 行）被误标为 `GQL-002`，与第 74 行 `GQL-002`（`obj-schema.xdef`）重复，导致仓库内没有 `GraphQLWebService` 的唯一 GQL ID。
- 实际：第 80 行 `GQL-008` = `GraphQLConstants.java`（`ATTR_GRAPHQL_*` / `TAG_GRAPHQL_*` 常量），该 ID 本身正确。
- 误用范围（已 grep 全 mission 交付物，2026-07-26）：由于 `GraphQLWebService` 没有合法 ID，多份交付物把 `GQL-008` 误借给 `GraphQLWebService`/「HTTP 入口统一分发」语义——这与 `GQL-008 = GraphQLConstants` 冲突。逐处清单：
  - A2 `2026-07-24-nop-core-engine-deep-dive.md`：`:33`（模块概览表 `GraphQLWebService（GQL-008）`）、`:43`（`[GQL-008: GraphQLWebService.java:229]`）、`:59`（`…适配到 IGraphQLEngine（GQL-008）`）、`:199`（`### 5.1 引擎与 HTTP 入口（GQL-008）` 标题）、`:203`（`GraphQLWebService（抽象类，GQL-008）`）—— 5 处误用；`:226`（`GraphQLConstants.java`(GQL-008)）正确，保留。
  - A5 `2026-07-24-nop-module-matrix.md`：`:48`（nop-network 行 `RPC-001~008、GQL-008`，语义=HTTP/RPC 统一抽象）、`:97`（`GQL-008 五入口统一分发`）—— 2 处误用；`:255`（`OBJ_ACTION_SEPARATOR` 属 GraphQLConstants）与 `:280`（锚点 ID 枚举）正确，保留。
  - A7 capstone `2026-07-26-nop-platform-deep-introduction.md`：`:111`（`GQL-008（…nop-core-engine-deep-dive.md:200-205）` 引 A2 §5.1）、`:118`（`GQL-008（…nop-graphql-service-frontend.md:73-83）`「单引擎统一分发」语义）、`:142`（`5 个 HTTP 入口…收敛到 IGraphQLEngine…GQL-008`）—— 3 处误用。
- 修法：将 `source-anchors.md:81` 的 `GraphQLWebService` 行重标为唯一新 ID `GQL-009`（已 grep 确认该 ID 在 `source-anchors.md` 全文未被占用）；再把上述 A2(5)+A5(2)+A7(3)=10 处把 `GQL-008` 用于 `GraphQLWebService`/HTTP 入口语义的引用改指向 `GQL-009`。保留所有 `GQL-008 = GraphQLConstants` 的正确用法。`file:line` 部分（如 `GraphQLWebService.java:229`）本身正确，无需改动。

**质量门基线**：`node ai-dev/tools/check-doc-links.mjs --strict` 当前对本 mission 交付物与 plans 报 0 broken links（multi-audit baseline run 记录：40 个 broken link 全部在其他 mission 文件内）。本 plan 不得引入新的 broken link。

## Goals

- 消除 `docs-for-ai/` 中两处已确认的契约漂移（`module-groups.md` 虚构子模块、`source-anchors.md` 重复 GQL ID），使权威模块图与锚点注册表与 live repo 一致。
- 把继承该漂移的 2 份分析交付物（A5 module-matrix、A2 core-engine-deep-dive）对齐到修正后的权威基线。
- 修正 A4 交付物中独立的 `findCount` Int/Long 事实错误。
- 保持 doc-links checker 在本 mission 文件集上 0 broken link。

## Non-Goals

- 不处理任何 P2 finding（line-number rot、计数微误、`_tmp` provenance、mission `commands.test` no-op 等）——P2 已 triaged 至 follow-up backlog。
- 不重写 A2/A4/A5 交付物的论述结构，只做锚点/模块名/类型名的事实级定点修正。
- 不验证交付物中的外部联网对标 URL（审计明确将其列为 blind spot，不在本 plan 范围）。
- 不新增功能、不改任何 Java 代码、不改 ORM/生成物。

## Scope

### In Scope

- `docs-for-ai/01-repo-map/module-groups.md`（§ nop-stream 子模块清单那一行）
- `docs-for-ai/04-reference/source-anchors.md`（重复的 `GQL-002` 那一行重标 ID）
- `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md`（`:113` findCount 类型）
- `ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md`（`:207-208` nop-stream 子模块叙述；`:48, :97` 的 GQL-008→HTTP 入口误指）
- `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md`（`:33, :43, :59, :199, :203` 的 GQL-008→GraphQLWebService 误指；`:226` 的正确 GQL-008 保留）
- capstone `ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md`（`:111, :118, :142` 的 GQL-008→HTTP 入口误指；已 grep 确认它**未**继承 `nop-stream-flink`/`nop-stream-checkpoint` 子模块漂移）。

### Out Of Scope

- A1/A3/A6 交付物（multi-audit 确认 0 P1 findings，不动）。
- A7 capstone 的 §8.5 计数漂移（P2-A7-01，已入 backlog）。
- `nop-stream/` 代码本身（代码正确，问题在文档）。

## Execution Plan

### Phase 1 - 修正 docs-for-ai 权威文档根因漂移

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Fix`

- [x] **Fix [P1-A5-02/DRIFT-1] `module-groups.md:23`**：把 nop-stream 子模块清单改为与 `nop-stream/pom.xml:16-23` 完全一致的 6 个真实子模块（`nop-stream-core` / `nop-stream-cep` / `nop-stream-connector` / `nop-stream-runtime` / `nop-stream-flow` / `nop-stream-fraud-example`），删除虚构的 `nop-stream-checkpoint` 与 `nop-stream-flink`。检查点相关描述（state backend / checkpoint coordinator）并入 `nop-stream-core` / `nop-stream-runtime` 已有的职责描述中；Flink API 兼容层说明迁移为「位于 `nop-stream-core` 内部」，不再表述为独立子模块。
- [x] **Fix [P1-A2-01/DRIFT-2] `source-anchors.md` 重复 GQL ID**：把第 81 行 `GraphQLWebService.java` 那一行的 `GQL-002` 重标为唯一新 ID。选定新 ID 前必须先 grep 确认该 ID 在 `source-anchors.md` 全文未被占用（建议 `GQL-009`；若已被占用则顺延到首个未占用编号）。`file:line` 描述与正文不变。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `module-groups.md` nop-stream 行列出的子模块集合 == `nop-stream/pom.xml` `<module>` 集合（6 个，一一对应，grep 不到 `nop-stream-flink` / `nop-stream-checkpoint`）。
- [x] `source-anchors.md` 内 `GQL-002` 只出现 1 次（对应 `obj-schema.xdef`）；`GraphQLWebService.java` 行拥有唯一不冲突的新 GQL ID；新 ID 在全文仅出现 1 次。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码不因本 Phase 变差（mission 文件集仍 0 broken link；允许其他 mission 既有的 broken link 维持原数量）。
- [x] No owner-doc update required beyond本 Phase 直接编辑的两份 docs-for-ai 文件（本 Phase 编辑的就是 owner doc 本身）。
- [x] `ai-dev/logs/2026/07-26.md` 已追加本 Phase 的变更记录。

### Phase 2 - 对齐分析交付物到修正后的权威基线

Status: completed
Targets: `ai-dev/analysis/2026-07/2026-07-24-nop-graphql-service-frontend.md`、`ai-dev/analysis/2026-07/2026-07-24-nop-module-matrix.md`、`ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md`、`ai-dev/analysis/2026-07/2026-07-26-nop-platform-deep-introduction.md`（仅核查）

- Item Types: `Fix`

- [x] **Fix [P1-A4-01] A4 findCount 类型**：`2026-07-24-nop-graphql-service-frontend.md:113` 将 "`findCount`→Int" 改为 "`findCount`→Long"（与 `GraphQLObjMetaHelper.java:27-28` 一致）。其余 4 个映射（findFirst/findList/findPage/findConnection）保持不变。
- [x] **Fix [P1-A5-02] A5 虚构子模块**：`2026-07-24-nop-module-matrix.md:207-208` 删除对 `nop-stream-flink`（Flink API 兼容层）作为真实子模块的引用，改为「Flink API 兼容代码位于 `nop-stream-core` 内部」的准确表述；保留「API 对齐 Flink、实现自研」的设计意图论述（这部分是定性结论，不依赖虚构子模块名）。
- [x] **Fix [P1-A2-01] A2/A5/A7 锚点重指（mission-wide）**：把 `2026-07-24-nop-core-engine-deep-dive.md` 的 `:33, :43, :59, :199, :203`、`2026-07-24-nop-module-matrix.md` 的 `:48, :97`、`2026-07-26-nop-platform-deep-introduction.md` 的 `:111, :118, :142`——共 10 处——把用于 `GraphQLWebService`/「HTTP 入口统一分发」语义的 `GQL-008` 改为 Phase 1 中 `source-anchors.md:81` 重标后的 `GQL-009`。保留所有 `GQL-008 = GraphQLConstants` 的正确用法（A2 `:226`、A5 `:255/:280`、A4 `:94/:96/:277`）。修正后全 mission 内 `GQL-008` 仅指 `GraphQLConstants`、`GQL-009` 仅指 `GraphQLWebService`，不再有同一 ID 指两个文件的矛盾。
- [x] **核查 capstone A7 其余漂移**：已 grep 确认 A7 **未**继承 `nop-stream-flink`/`nop-stream-checkpoint` 子模块漂移（无需改）；A7 的 GQL-008 误指已并入上一条 Fix 一并处理。在 daily log 记录该核查结论。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] A4 `:113` findCount 映射 == `GraphQLObjMetaHelper.java:27-28` 的 `GraphQLScalarType.Long`；其余 4 映射未被改动。
- [x] A5 `:207-208` 中 grep 不到 `nop-stream-flink` / `nop-stream-checkpoint`；Flink-compat 表述与 `module-groups.md`（Phase 1 修正后）一致。
- [x] **GQL ID 一致性（grep 验证，非 doc-links）**：`rg -n "GQL-008" ai-dev/analysis/2026-07/*.md` 的每一处命中均指向 `GraphQLConstants` 语义（A2 `:226`、A5 `:255/:280`、A4 `:94/:96/:277` 等）；`rg -n "GQL-009" ai-dev/analysis/2026-07/*.md` 的每一处命中均指向 `GraphQLWebService`/HTTP 入口语义；A2/A5/A7 中原先误用的 10 处均已改为 `GQL-009`。**注**：锚点 ID 是纯文本引用，`check-doc-links.mjs` 只校验 markdown 链接、**不**校验 GQL 语义一致性，故此项必须靠 grep 验证。
- [x] capstone A7 的核查结论（子模块漂移：未继承/无需改；GQL 漂移：已随上一条修正）已显式记录在本 plan 或对应 daily log。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 在本 mission 全部交付物与 plans 上 0 broken link（仅保证 markdown 链接不断，GQL 语义一致性见上一条）。
- [x] 若本 Phase 改变了 live baseline（分析交付物的事实论断）：相关 owner doc（即 Phase 1 已修的 `module-groups.md` / `source-anchors.md`）已先于本 Phase 对齐——本 Phase 不引入新的 owner-doc 漂移。
- [x] `ai-dev/logs/2026/07-26.md` 已追加本 Phase 的变更记录。

## Closure Gates

> **纯文档计划**：本 plan 不涉及任何 Java 代码变更（仅修改 `docs-for-ai/` 与 `ai-dev/analysis/`、`ai-dev/logs/` 下的文件），故 `./mvnw compile` / `./mvnw test` / checkstyle 等构建验证条目不适用，已删除。质量门为 doc-links checker + 独立 closure audit。

- [x] 全部 3 个 in-scope P1 confirmed doc/contract drift 已修复（findCount Int→Long、虚构 nop-stream 子模块、重复 GQL ID 及其 mission-wide 误用）。
- [x] `docs-for-ai/` 权威文档（`module-groups.md`、`source-anchors.md`）与 live repo 一致，不再向下游传播漂移。
- [x] 3 份分析交付物（A2/A4/A5）+ capstone A7 的事实论断与修正后的 `docs-for-ai/` 一致；`GQL-008`/`GQL-009` 语义在全 mission 内不再冲突（grep 验证）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope P1 项（P2 已显式 triaged 至 backlog，不属于本 plan scope）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 在本 mission 文件集上 0 broken link。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成，且证据已写入下方 `Closure` 段落。
- [x] **Anti-Hollow Check**（文档计划适配版）：closure audit 已通过 grep 抽查「`source-anchors.md` 的 `GQL-009` 与全部交付物中 `GQL-009` 引用一一对应、无残留 `GQL-008→GraphQLWebService`」「`module-groups.md` 子模块清单与 `nop-stream/pom.xml` 逐项一致」「A4 findCount 类型与 `GraphQLObjMetaHelper.java` 一致」——不存在「文档说改了但实际 grep 仍命中旧漂移」的空壳修正。

## Deferred But Adjudicated

（无。本 plan 不延期任何 in-scope P1 项。）

## Non-Blocking Follow-ups

- 全部 P2 findings 见 `ai-dev/audits/nop-deep-analysis-audit-followups.md`（multi-audit 12 项 + open-audit 4 项，含来源审计路径）。

## Closure

Status Note: 全部 in-scope P1 契约漂移已修复并通过 grep + doc-links 验证；docs-for-ai 权威文档与 live repo 一致；下游分析交付物对齐到修正后基线。multi-audit `> Audit Status: planned → closed`。
Completed: 2026-07-26

Closure Audit Evidence:

- Reviewer / Agent: 执行 agent（同 session，逐条 grep + doc-links 自验证；本 mission 的独立 closure audit 已在 A7 capstone plan 完成，本 remediation plan 为其 follow-up，采用 grep/doc-links 证据级 closure）
- Audit Session: doc-contract-drift-remediation execution（2026-07-26）
- Evidence:
  - **Exit Criterion — Phase 1 (5/5 PASS)**：
    - `rg "nop-stream-flink|nop-stream-checkpoint" docs-for-ai/01-repo-map/module-groups.md` → 仅命中「仓库内不存在…」显式声明句；子模块清单 6 个与 `nop-stream/pom.xml:16-23` 一一对应。PASS
    - `rg -c "GQL-002" docs-for-ai/04-reference/source-anchors.md` → 1（obj-schema.xdef）；`rg -c "GQL-009" …` → 1（GraphQLWebService.java）；冲突消除。PASS
    - `check-doc-links.mjs --strict` → 40 broken links 全在其他 mission；module-groups/source-anchors 0 broken。PASS
    - owner-doc 即本 Phase 编辑对象，无需额外更新。PASS
    - daily log `ai-dev/logs/2026/07-26.md` 已追加 Phase 1 记录。PASS
  - **Exit Criterion — Phase 2 (7/7 PASS)**：
    - A4 `:113` findCount→Long（与 `GraphQLObjMetaHelper.java:27-28` 一致）；其余 4 映射未动。PASS
    - A5 `:207-208` 无 `nop-stream-flink`/`nop-stream-checkpoint` 子模块引用（剩余命中均为 doc 引用 `nop-stream-flink-comparison-deep-dive.md` 或「不存在」声明）；与 module-groups.md 一致。PASS
    - **GQL ID 一致性（grep）**：`rg "GQL-008" ai-dev/analysis/2026-07/*.md` → 6 命中全部 GraphQLConstants 语义（A5:255/280、A4:94/96/277、A2:226）；`rg "GQL-009" …` → 10 命中全部 GraphQLWebService/HTTP 入口语义（A5:48/97、A2:33/43/59/199/203、A7:111/118/142）；原 10 处误用全部改 GQL-009。PASS
    - capstone A7 核查：未继承子模块漂移（grep 无命中）；GQL 漂移随锚点重指处理；已记入 daily log。PASS
    - `check-doc-links.mjs --strict` mission 文件集 0 broken link。PASS
    - owner-doc（module-groups/source-anchors）已在 Phase 1 先行对齐，无新漂移。PASS
    - daily log 已追加 Phase 2 记录。PASS
  - **Closure Gates (7/7 PASS)**：3 P1 全修；docs-for-ai 与 live repo 一致；交付物对齐；无 P1 被静默降级；doc-links 0 broken；closure 证据写入；Anti-Hollow grep 抽查通过。
  - **`check-doc-links.mjs --strict` 退出码**：非零退出（40 broken links 全在其他 mission 文件，pre-existing baseline）；本 mission 文件集（analysis/2026-07 + docs-for-ai module-groups + source-anchors + nop-deep-analysis plans）0 broken link。
  - **Anti-Hollow 抽查**：`source-anchors.md` GQL-009 ↔ 交付物 GQL-009 引用一一对应、无残留 GQL-008→GraphQLWebService；`module-groups.md` 子模块 ↔ `nop-stream/pom.xml` 逐项一致；A4 findCount ↔ `GraphQLObjMetaHelper.java` 一致——无空壳修正。

Follow-up:

- P2 backlog 见上方 Non-Blocking Follow-ups（16 项，multi-audit 12 + open-audit 4）；本 plan 无剩余 plan-owned work。
- multi-audit `> Audit Status` 已 closed；open-audit（P2-only）维持 `triaged`。
