# 1 nop-ai-agent Harness Evolution 文档收口与 Roadmap L6 Phase Status

> Plan Status: completed
> Mission: nop-ai-agent-harness-evolution
> Work Item: Roadmap 完成定义收口（L6 Phase Status + 标记的设计文档漂移修复）
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` 完成定义；`ai-dev/plans/2026-08-01-1440-1` Non-Blocking Follow-ups
> Related: 17 份 harness evolution 落地计划（2026-08-01-1437-* / -1440-* / -1505-* / -1905-* / 2026-08-02-0421-* / -0656-* / -0900-*）

## Purpose

把 nop-ai-agent Harness Evolution（roadmap W1–W6）的文档状态收口到与其 live 实现一致：在 `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` 的 Phase Status 追加 `L6. Harness Evolution` 层（roadmap 完成定义的最后一项未满足条件），并修复一份被落地计划显式标记但尚未修复的设计文档内部矛盾。完成后 roadmap 的完成定义四项全部满足。

## Current Baseline

- **roadmap W1–W6 全部 `[x]` 且各自通过独立 closure audit**（见 roadmap 收口注记）：
  - W1 Plan 运行时门控 → plans `2026-08-01-1440-2`（W1-1/2/3）+ `2026-08-01-1505-2`（W1-4 partial）+ `2026-08-01-1905-1`（W1-4 full）
  - W2e LLM 错误规范化 → plan `2026-08-01-1440-1`（W2e-0..3 + RATE_LIMITED floor）+ `2026-08-01-1505-1`（W2e-4 余 + W2e-5）
  - W2 Reliability → `2026-08-01-1437-1`（W2-1）/ `2026-08-01-1905-2`（W2-2）/ `2026-08-01-1437-2`（W2-3）/ `2026-08-01-1905-3`（W2-4）
  - W3 Middleware → `2026-08-01-1437-3`（W3-1）/ `2026-08-01-1437-4`（W3-2）
  - W4 上下文工程 → `2026-08-02-0900-1`（W4-1）/ `2026-08-02-0900-2`（W4-2）
  - W5 Guardrail → `2026-08-02-0421-1`（W5-1）/ `2026-08-02-0421-2`（W5-2）/ `2026-08-02-0900-3`（W5-3）
  - W6 Recipe → `2026-08-02-0656-1`（W6-1）
- **`nop-ai-agent-roadmap.md` 状态区不一致（pre-existing + 缺 L6）**：
  - `## Phase Status` 当前止于 **L5**（line 22：`L5. Architecture Upgrades（AgentScope 审计驱动）: done`），无 L6 行。
  - **但 `## Work Items` 表（lines 56–62）与 `## Dependency Graph`（lines 68–88，mermaid）实际止于 L4**——L5 从未被回写到这两个区域（plan 296 落地时只改了 Phase Status）。`rg -n "L5"` 仅命中 line 22（Phase Status）与 line 103（Rule 文字）。
  - `Last Updated: 2026-07-17 (plan 296)`。`rg -l "nop-ai-agent-roadmap.md" ai-dev/plans/2026-08-0*.md` 为空 → 17 份 harness evolution 落地计划均未回写该状态索引。
  - **本计划裁定（Decision）**：为达成 Goals 中"表/图/header 彼此一致"，Phase 1 同时补 L5 与 L6 到 Work Items 表 + Dependency Graph（修复 pre-existing L5 缺口 + 追加 L6），而非只补 L6（否则产生 L4→L6 跳行 + `L5 --> L6` 边引用不存在节点的新不一致）。
- **roadmap 完成定义（4 项 prose 条件）**：前三项（W1–W6 done / nop 原生实现 / 无外部依赖）已满足；第四项（追加 L6 Phase Status）未满足。
- **一份已标记未修复的设计文档内部矛盾（三方）**：`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`
  - §3.3 line 123："`<errorMappings>` 必须**省略 `xdef:key-attr`**"；line 160 对比表："`<errorMapping ...>` ... **不带 key**"（line 104 注释 `<!-- 故意不带 xdef:key-attr -->` 同向）
  - §6.3 line 355："`<errorMappings>` 顺序与 first-match-wins：实现采用 `xdef:key-attr="id"`"
  - §3.6 line 254（旁证）："`<accounts>` 用 `xdef:key-attr="id"`...与 `<errorMappings>` 同款"——已声明 errorMappings 用 key-attr=id，与 §6.3 一致
  - **live schema 证据**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/llm.xdef:144` = `<errorMappings xdef:body-type="list" xdef:key-attr="id">`；`:145` = `<errorMapping id="!string" xdef:name="LlmErrorMappingModel"`（即 `errorMapping` 元素**携带 `id`**）；`llm.xdef:119/38` 注释亦明确 key-attr="id"。故 §3.3（line 123/160/104）是错误/陈旧文本，须统一为 live 现状（key-attr=id）。`TestLlmErrorMapping` 断言 first-match-wins 成立。plan `2026-08-01-1440-1` 标注此矛盾为"doc 维护时统一，不阻塞本计划"，至今未修。

## Goals

- `nop-ai-agent-roadmap.md` Phase Status 出现 `L6. Harness Evolution: done`，使 roadmap 完成定义第四项满足。
- `nop-ai-agent-roadmap.md` 的 Work Items 表、Dependency Graph、header 与 Phase Status 一致（补齐 pre-existing 的 L5 缺口 + 追加 L6，消除"Phase Status 有 L5/L6 而表/图止于 L4"的不一致）。
- `nop-ai-llm-error-normalization-design.md` 关于 `<errorMappings>` key-attr 的描述内部自洽且与 live `llm.xdef` 一致（消除 §3.3 vs §3.6/§6.3 三方矛盾）。

## Non-Goals

- 不实现任何新代码、新功能、新配置（本计划纯文档）。
- 不处理被各落地计划显式 deferred 的 out-of-scope 增强（§14.5 nop-task 执行层迁移、归档持久化后端、可观测性指标面板、LLM 动态攻击生成等）——这些超出 harness evolution roadmap（W1–W6）scope，属后续 successor/mission。
- 不重写已 `completed` 历史计划的文本（Rule #20）；本计划只在 roadmap 状态索引与一份设计文档上做收口写入。

## Scope

### In Scope

- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`：Phase Status 追加 L6 行 + Work Items 表补 L5/L6 两行（修 pre-existing L5 缺口 + L6）+ Dependency Graph 补 L5/L6 节点与边 + header `Last Updated` / 来源更新。
- `ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`：消除 `<errorMappings>` key-attr 的三方内部矛盾（统一为 live 现状：采用 `xdef:key-attr="id"`，`errorMapping` 携带 `id`，合并保序，first-match-wins 仍成立）。
- roadmap backlog 文件 `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md`：顶部 `> Status: active` 调整为 `> Status: done`（完成定义四项在本计划后全满足），并注明收口来源。
- `ai-dev/logs/` 收口条目。

### Out Of Scope

- L6 之下任何代码/测试变更。
- 其他 design 文档的漂移扫描与修复（仅修已标记的这一处；新发现的漂移记为 follow-up，不在本计划扩张修复）。
- `docs-for-ai/` 更新（harness evolution 是 nop-ai-agent 内部演进，非平台用户面向 API；如需 owner-doc 裁定，明确写 `No docs-for-ai update required`）。

## Execution Plan

### Phase 1 - Roadmap L6 Phase Status 收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`, `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md`

- Item Types: `Fix | Decision`

- [x] 在 `nop-ai-agent-roadmap.md` 的 `## Phase Status` 列表追加一行 `- L6. Harness Evolution（10 类增量共识收敛）: done`，并按文件内 `> 状态流转` 注记规则（closure audit 通过 → done）标注依据（17 份计划均通过独立 closure audit）。
- [x] **[Decision] 补齐 Work Items 表**：在 `## Work Items` 表追加 **L5 与 L6 两行**（L5 行 = Architecture Upgrades / Middleware 洋葱链 + Tag-based Tool Visibility / plan 296 / done；L6 行 = Harness Evolution / W1–W6 / 17 份计划编号 / done），消除"Phase Status 有 L5/L6 而表止于 L4"的不一致。
- [x] **[Decision] 补齐 Dependency Graph**：在 mermaid 追加 `L5` 与 `L6` 节点定义（含 label）+ 依赖边（`L4 --> L5`、`L5 --> L6`；harness evolution 建立在 L1–L5 之上）+ `style L5/L6 fill:#dfd,stroke:#3a3`，使图节点闭合（当前图止于 L4，无 L5 节点）。
- [x] 更新 `nop-ai-agent-roadmap.md` header `> Last Updated:`（日期晚于 2026-07-17）与 `> Source:`，记录 L5 补齐 + L6 收口来源（本计划 + roadmap 完成定义）。
- [x] [Decision] 核对 `nop-ai-agent-roadmap.md` 的 `## Platform Reuse` 表与 `## Current Baseline`（line 46 "已实现：L0–L4 全部工作项"）是否需补 harness evolution 新增 schema（如 `recipe.xdef`、`llm-failover.xdef`、`<filter-chain>`、guardrail rule/test 包路径）并将 "L0–L4" 范围描述更新为 "L0–L6"；按裁定补写或在 Closure Follow-up 显式注明不补理由。
- [x] 更新 `## Rule` section line 103 "可标记单位是 Layer（L0–L4）"为 "L0–L6"，消除与 Phase Status（L0–L6）的范围矛盾。
- [x] [Decision] 将 backlog roadmap `nop-ai-agent-harness-evolution-roadmap.md` 顶部 `> Status: active` 改为 `> Status: done`（目标值 = `done`，与 design roadmap.md Status Values 一致），并加一行收口注记（完成定义四项全满足，本计划补齐第四项）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-roadmap.md` 的 `## Phase Status` 存在 L6 done 行（`rg -n "L6\. Harness Evolution.*done" ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` 命中）。
- [x] Work Items 表同时含 **L5 与 L6 两行**（`rg -n "\| L5 \|" ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` 与 `rg -n "\| L6 \|" ...` 均命中），且 L6 行 plan 范围列覆盖 W1–W6 对应计划——验证方式：`rg -o "2026-08-0[12]-[0-9]{4}" ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md | sort -u | wc -l` 返回值 ≥ 5（统计**不同**日期-时间前缀数，非匹配行数；涵盖 1437/1440/1505/1905/0421/0656/0900 七个不同时间戳中的至少 5 个）。
- [x] Dependency Graph mermaid 含 `L5[` 与 `L6[` 节点定义、`L5 --> L6` 边、两个 style 行（`rg -n "L5\[|L6\[|L5 --> L6|style L5|style L6" ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` 全部命中），图节点闭合无悬空边。
- [x] header `Last Updated` 日期晚于 2026-07-17。
- [x] `## Rule` section 范围描述为 L0–L6（`rg -n "L0.L6" ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` 命中，且不再有孤立的 "L0–L4" 范围声明）。
- [x] backlog roadmap 顶部为 `> Status: done`（`rg -n "^> Status: done" ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` 命中）。
- [x] roadmap 完成定义第四项（追加 L6）现可判定为满足；前三项未被本计划破坏。
- [x] 本计划为纯文档计划，无需 `./mvnw` 验证；明确写 `No mvnw test/lint required (pure-doc plan)`。
- [x] **No `docs-for-ai/` update required**：harness evolution 是 nop-ai-agent 内部演进，非平台用户 API；本计划目标文件（design/backlog）即 owner doc 本身。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - LLM 错误规范化设计文档矛盾修复

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`

- Item Types: `Fix`

- [x] 消除 `<errorMappings>` key-attr 描述的三方内部矛盾（§3.3 line 123/160/104 vs §6.3 line 355 vs §3.6 line 254）：将 line 123（"必须**省略** `xdef:key-attr`"）、line 160 对比表（"**不带 key**"）、line 104 注释统一为 live 现状——`<errorMappings>` 采用 `xdef:key-attr="id"`（与 dialect `<errorCodes xdef:key-attr="name">` 同款），`id` 用于 `x:extends` 合并时按 id 区分条目（replaceChild 原位替换 + 新 id 追加末尾，合并后顺序保持），first-match-wins 仍成立（与 line 355/254 及 `llm.xdef:144` 一致）。同时删除 line 123 "与 dialect 模式的**刻意偏离点**"定性——修正后 errorMappings **采用** key-attr=id，与 dialect 同款，不再偏离。
- [x] 修正 line 160 对比表中"`<errorMapping ...>` **不带 key**"一列：live `llm.xdef:145` 为 `<errorMapping id="!string" ...>`——`errorMapping` 元素**携带 `id`**，该 `id` 即 `xdef:key-attr="id"` 指定的合并键（与 dialect `<errorCode name=...>` 对称：name 是 errorCode 的 key）。改为反映此 live 现状的表述，删除"不带 key/不带 id"的错误描述。
- [x] 核对 line 104 注释 `<!-- ⚠️ 故意不带 xdef:key-attr -->`：该注释与 live（key-attr=id）矛盾，改为说明"`<errorMappings>` 用 key-attr=id 保序合并、first-match-wins"或删除该误导性注释（以 live `llm.xdef:144/119` 为准）。
- [x] **修正 §3.3 示例 XML 块（line 145–153）**：示例中 `<errorMappings>` 现无 `xdef:key-attr`、4 个 `<errorMapping>` 元素缺 live 必填的 `id="!string"` 属性。按 live `llm.xdef:144-145` 补齐：`<errorMappings>` 加 `xdef:key-attr="id"`，每个 `<errorMapping>` 加 `id="<唯一标识>"`（如 openai-quota-exceeded 等，与 `llm.xdef:123` 注释示例一致），使示例可直接通过 schema 校验。
- [x] 保留 first-match-wins 的测试固化要求——本 Phase 只改文档不改测试；在文档中引用 `TestLlmErrorMapping`（断言两条同 classification 规则按合并后位置先后分别命中）作为现状佐证。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-llm-error-normalization-design.md` 全文不再出现与 key-attr=id 矛盾的表述：`rg -n "省略.*key-attr|必须.*省略|故意不带|不带 key|不带 id|刻意偏离" ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md` 无命中（覆盖 §3.3 line 123/160/104 三处 + "刻意偏离点"定性）。
- [x] §3.3 示例 XML 块与 live 一致：`<errorMappings>` 行含 `xdef:key-attr="id"`（`rg -n '<errorMappings.*key-attr="id"' ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md` 命中），且示例中每个 `<errorMapping` 开头行均含 `id=`（无缺 id 的 errorMapping 元素）。
- [x] 文档关于 key-attr 的描述与 live `llm.xdef` 一致：读取 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/llm.xdef` 的 `<errorMappings>`（:144 key-attr=id）与 `<errorMapping>`（:145 携带 id），确认文档表述与之一致；不一致则停在 Phase 内修正文档至一致（不改 schema）。
- [x] §3.3、§3.6（line 254）、§6.3（line 355）三处对 `<errorMappings>` key-attr 的描述相互自洽（统一为 key-attr=id），无残留三方矛盾。
- [x] first-match-wins 语义在文档中保持成立且与 `TestLlmErrorMapping` 一致（引用测试名作现状佐证）。
- [x] 本 Phase 只改文档，不引入任何 `.java`/`.xml` schema 变更（`git diff --stat` 仅含该 design 文档）。
- [x] 明确写 `No mvnw test/lint required (pure-doc plan)`；明确 `No docs-for-ai update required`（ai-dev/design 内部一致性修复）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **纯文档计划**：本计划不涉及任何代码变更（仅修改 `ai-dev/design/` 与 `ai-dev/backlog/` 下文件），故按 guide 规则删除 `./mvnw` 构建验证条目，不执行构建。

- [x] roadmap 完成定义四项全部满足（W1–W6 done / nop 原生实现 / 无外部依赖 / L6 Phase Status 已追加）
- [x] `nop-ai-agent-roadmap.md` 的 Phase Status、Work Items 表、Dependency Graph、header 彼此一致（均反映 L6 done）
- [x] `nop-ai-llm-error-normalization-design.md` 不再存在 `<errorMappings>` key-attr 的内部矛盾
- [x] 受影响的 owner docs 已同步到 live baseline（本计划 targets 即 owner doc，已更新；`docs-for-ai/` 显式 No update required）
- [x] 不存在被静默降级到 deferred 的 in-scope 项（已标记的 §3.3/§6.3 漂移已被 Fix 收口，未降级为 follow-up）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（修改了 docs/design 文件，须过链接检查）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据

## Deferred But Adjudicated

无。本计划 scope 窄（两项已确认的文档收口），不引入需延期项。各落地计划中显式 deferred 的 out-of-scope 增强（§14.5 nop-task 迁移、归档持久化、可观测性面板、LLM 动态攻击生成、执行级 BAIL/scope filter 等）均属后续 successor/mission，不在本计划裁定范围——它们已在各自计划的 `Deferred But Adjudicated` 中带 `Why Not Blocking Closure` 记录。

## Non-Blocking Follow-ups

- 若 Phase 1 核对中发现 harness evolution 新增 schema（recipe.xdef / llm-failover.xdef / guardrail rule+test 包）值得在 `Platform Reuse` 表单列，可顺手补；不补则记为 follow-up（不影响 L6 done 判定）。
- 其他 design 文档的漂移扫描（除本计划修复的这一处）——本计划不做全量扫描，新发现漂移记为 follow-up。
- `nop-ai-llm-error-normalization-design.md` §3.3 示例块（:106）`ErrorClassification` 枚举包路径陈旧（`io.nop.ai.core.model` vs live `io.nop.ai.api.chat`，已迁至 nop-ai-api 最低层）——属 key-attr 之外的另一处漂移，超出本 plan scope，后续 doc 维护时统一。

## Closure

Status Note: 纯文档收口计划，两 Phase 均 completed。Phase 1 把 nop-ai-agent Harness Evolution（W1–W6）的 L6 状态写入 `nop-ai-agent-roadmap.md`（Phase Status / Work Items 表 / Dependency Graph / header / Rule 一致，并回填 pre-existing L5 缺口），backlog roadmap 置 `done`，roadmap 完成定义第四项满足、四项全满足。Phase 2 消除 `nop-ai-llm-error-normalization-design.md` 关于 `<errorMappings>` key-attr 的三方内部矛盾（§3.3/§3.6/§6.3 统一为 live 现状 key-attr=id）。无代码/schema 变更；Anti-Hollow / `./mvnw` 检查对纯文档计划 N/A（Closure Gates 已按 guide 删除构建条目）。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（opencode `explore` subagent，fresh session，task_id `ses_040303bcbffeALvitQBB31t4yi`），非本 plan 执行 session。
- Audit Session: `ses_040303bcbffeALvitQBB31t4yi`
- Evidence:
  - Gate 1（完成定义四项）PASS：backlog W1-1..W6-1 全 `- [x]`（零 `- [ ]`）；backlog 仍声明"不引入外部依赖"；backlog 顶部 `> Status: done` + 收口注记。
  - Gate 2（roadmap 一致性）PASS：Phase Status line 23 `L6. Harness Evolution ... done`；Work Items line 66/67 L5+L6 行均 `done`；Dependency Graph `L5[`/`L6[`/`L5 --> L6`/`style L5`/`style L6` 全命中；header `Last Updated: 2026-08-02` > 2026-07-17；date-prefix count = 8（≥5）；`rg "L0.L4|L0–L4"` 无残留。
  - Gate 3（key-attr 矛盾消除）PASS：`rg "省略.*key-attr|必须.*省略|故意不带|不带 key|不带 id|刻意偏离"` 无命中；`<errorMappings.*key-attr="id"` 命中 line 104/145；§3.3 示例块 4 个 `<errorMapping>` 元素 + 伪 schema 均含 `id=`；§3.3/§3.6/§6.3 与 live `llm.xdef:144-145`（`xdef:key-attr="id"` + `errorMapping id="!string"`）一致。
  - Gate 4（owner docs 同步）PASS：`git diff --name-only` 本计划仅触 3 个目标 doc + log + plan 文件；预存 e2e/.ts 改动属另一 session，已忽略；Out Of Scope 显式 `No docs-for-ai update required`。
  - Gate 5（无静默降级）PASS：§3.3/§6.3 漂移已被 Fix 收口；`Deferred But Adjudicated` 为 `无`；Non-Blocking Follow-up 仅 ErrorClassification 枚举包路径漂移（design :106），显式 out-of-scope，非静默降级。
  - Gate 6（`check-doc-links.mjs --strict`）PASS：exit code 0（0 errors，1 warning 为 plan 自身 Source 行 shorthand 引用，非目标 doc 缺陷）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit code 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - Anti-Hollow 检查：N/A（纯文档计划，无代码/运行时调用链；`git diff` 零 `.java`/`.xml`/schema 变更；plan Closure Gates 已按 pure-doc 规则删除 `./mvnw` 条目）。
  - Deferred 项分类检查：唯一 deferred（ErrorClassification 枚举包路径）为 `out-of-scope improvement`，已在 plan Non-Blocking Follow-ups 显式注明，非 in-scope live defect 降级。

Follow-up:

- ErrorClassification 枚举包路径漂移（`nop-ai-llm-error-normalization-design.md` §3.3 伪 schema :106 `io.nop.ai.core.model` vs live `io.nop.ai.api.chat`）——key-attr 之外的另一处 doc 漂移，后续 doc 维护时统一（非本 plan scope，non-blocking）。
- 无其他 plan-owned 剩余工作。>
