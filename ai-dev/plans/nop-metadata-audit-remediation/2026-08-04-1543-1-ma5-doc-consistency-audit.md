# MA5 文档与一致性审计（全模块）

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Draft Review: 3 轮独立子 agent 对抗性审查通过（第 1 轮 1 Major + 3 Minor 全部修复；第 2 轮 0 Blocker / 0 Major / 3 Minor，其中 3 Minor 全部修复；第 3 轮最终验证 0 Blocker / 0 Major，裁定可执行）。Session: ses_0344241ddffesNNR65i8SvPVIG / ses_034398fb5ffe0NMt2eZ10UlrhM / ses_03430426affeQ1s8c6hoJKwe5z。
> Mission: nop-metadata-audit-remediation
> Work Item: MA5（5.1 设计文档-代码 drift / 5.2 docs-for-ai 一致性 / 5.3 命名与术语一致性 / 5.4 跨模块契约一致性）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MA5 里程碑）、`ai-dev/skills/design-doc-audit-prompt.md`、`ai-dev/skills/deep-audit-prompts.md`（维度 18/19）、`ai-dev/skills/cross-module-dependency-audit-prompt.md`
> Related: 执行顺序 `{1}` of 3 — 硬前置：M0（0.1-0.4）全部 done（含 M0.4 绿色基线 813/0）、MA1-MA4 全部 done、MR1 completed（其 Follow-up 登记的设计文档陈旧引用为本计划输入）；产物（P1 发现 + 经裁决归 MR3 的 P2 项；**P0 走即时通道不进 MR3**）是 MR3 批量修复的输入；本计划为纯审计零代码变更。

## Purpose

对 nop-metadata 的文档与一致性面执行审计（roadmap MA5 的 4 个工作项：设计文档-代码 drift、docs-for-ai 文档-代码一致性、命名与术语一致性、跨模块契约一致性），以 `01-architecture-baseline.md` 为权威基线核对 `ai-dev/design/nop-metadata/` 17 篇设计文档与 `docs-for-ai/03-modules/nop-metadata.md`、`docs-for-ai/01-repo-map/module-groups.md` 相对 live 代码的 drift，产出审计报告并更新 arm-index，为 MR3 批量修复提供输入。

## Current Baseline

经 2026-08-04 live repo 核对（引用均与 roadmap 一致，已二次确认文件与计数）：

- roadmap MA5 四行（5.1-5.4）状态为 `todo`；Deps（0.4 done）已满足；MA1-MA4 全部 completed（其审计报告为本计划的历史对照源：MA1.1 依赖图报告 / MA1.2 模块边界报告 / MA2.2 生成管线报告 / MA3.1 XDSL 报告 / MA4.5 风格报告）
- **`ai-dev/design/nop-metadata/` 目录（live）**：19 个 md 文件（roadmap 口径"17 篇设计文档"= 除 `README.md` 与 `nop-metadata-roadmap.md` 外 17 篇；实测含 `00-vision` / `01-architecture-baseline` / `02-dto-module-restructure-decision` / `02-gap-analysis` / `03-version-management` / `04-data-governance` / `05-metadata-import` / `06-data-quality-extended` / `07-ai-integration` / `08-reconciliation` / `09-gap-analysis-extended` / `10-event-model` / `11-enterprise-semantic-layer` / `12-data-contract-and-governance-workflow` / `aggctx-and-bizmodel-split` / `aggregation-processor-split` / `api-dto-spec`）——**审计范围以 17 篇设计文档为准，README/roadmap 不计入 drift 判定对象**
- **已知 drift 输入（MR1 Follow-up 登记，本计划承接）**：
  - `ai-dev/design/nop-metadata/02-dto-module-restructure-decision.md:73` 与 `api-dto-spec.md:213` 仍把 `io.nop.metadata.core.dto` 写成 DTO 归宿（DTO 已迁至 `io.nop.metadata.api.dto`，c3162d4da；MR1 已修复 xmeta 引用，docs 未同步）——两处均 live 复核确认为陈旧引用（`core/dto` 包已不存在）
  - 旧搜索 topic `nop-meta-metadata`（MR1 修复为 `nop_meta_metadata`）：live 复核 design/docs 无残留引用，`01-architecture-baseline.md:1487` 已正确记载修复史（有效记载非 drift）——仅待 5.1 顺手复核确认
- **文档 drift 定级与 MR3 归属裁定通道（本计划强制，防 roadmap 规则 1 冲突）**：MA5 审计发现的每条 owner-doc drift 按以下通道定级——（a）P1（影响运行时契约/用户可见行为）：归 MR3 修复；（b）P2/P3（纯文档陈旧、无运行时影响，如 core.dto 归宿陈述）：经审计报告显式裁定后归 MR3（沿用 MA2.1"裁决例外"先例：MA2.1 报告开辟裁决通道，P2-MA2-01/02/03 由 MR1 修复），或裁定 deferred（须记录 Why Not Blocking Closure）——**不得悬空、不得静默降级**；R3.0 展开器以本计划报告为准展开修复行
- **`docs-for-ai/` 目标文档（live 存在）**：`docs-for-ai/03-modules/nop-metadata.md` + `docs-for-ai/01-repo-map/module-groups.md`（后者为全模块组文件，本计划只核对 nop-metadata 相关段落）
- 跨模块契约面（5.4 对象）：nop-sys/nop-auth/nop-wf/nop-code 依赖面、dict 跨模块引用、querySpace 路由；**依赖面现状（live 复核）：nop-sys/nop-code 在 nop-metadata 全部 pom 与源码中零引用（无依赖面，审计时标 N/A 即可）、nop-auth 仅 app pom 运行时依赖（nop-metadata 源码无 `io.nop.auth` 类型引用，仅 `MetaManifestBuilder.java:137` Javadoc 示例字符串 `nop/auth`，非类型引用）、真实依赖面 = nop-wf（compile 级 + QualityAlertWorkflowService + 3 个 xwf）+ nop-search + nop-job + nop-biz**（与 MA1.1 依赖图报告结论一致）——5.4 只做一致性核对，复用 MA1.1/MA1.2 结论不重复审计
- **审计方式约束（沿用 MA1-MA4 先例）**：纯审计计划零代码变更；发现一律使用轮次限定 ID 或 `P<级别>-<里程碑>-<序号>` 并标注修复归属（MR3 / 即时通道 / 非阻塞）；**已确认的 owner-doc drift 不允许静默降级**——每条 drift 必须有明确归属（本计划登记为 MR3 输入；若该 drift 与既有 MR2 修复项同源（如 P2-MA4-501 命名修复连带文档陈述），归属 MR2 对应修复项）
- 绿色基线：813 tests / 0 failures（M0.4，2026-08-04 实测；MA4 收口复测 812，差值属测试演化非回归；范围 `-pl nop-metadata -am -T 1C`）

## Goals

- 产出 MA5 审计报告（4 份：5.1 设计文档 drift / 5.2 docs-for-ai 一致性 / 5.3 命名与术语 / 5.4 跨模块契约）
- 每个发现标注 P 级 + 修复归属（MR3 / MR2 同源修复 / 即时通道 / 非阻塞 watch-only）
- 更新 arm-index-nop-metadata.md 报告清单与 P0/P1 追踪；roadmap 5.1-5.4 → done
- 对 MR1 Follow-up 登记的已知 drift（core.dto 陈旧引用）给出确认结论并归入修复归属
- 无 P0 时保持绿色基线；发现 P0 走即时通道

## Non-Goals

- 不修复审计发现的文档 drift（修复归 MR3 批量修复，P0 例外走即时通道；本计划只产出含精确 file:line 修复规格的报告）
- 不审计代码质量/运行时行为（MA1-MA4 已覆盖，MA6/MA7 承接残留与域特有风险）
- 不改任何 `src/` 代码或 `docs-for-ai/` 文档内容（纯审计计划）
- 不处理 P3（deferred successor，roadmap 规则 1）

## Scope

### In Scope

- 5.1 设计文档-代码 drift 审计（`design-doc-audit-prompt.md`）：`ai-dev/design/nop-metadata/` 17 篇设计文档 vs live 代码，以 `01-architecture-baseline.md` 为权威基线；核对文档陈述的架构决策、模块边界、接口契约与当前实现的一致性
- 5.2 docs-for-ai 文档-代码一致性（维度 18）：`docs-for-ai/03-modules/nop-metadata.md` + `docs-for-ai/01-repo-map/module-groups.md`（nop-metadata 段落）vs live 代码
- 5.3 命名与术语一致性（维度 19）：跨模块同概念同一名称、nop-metadata 内部术语一致性（如 `*Service` 命名规范、topic 命名 `nop_meta_*`、DTO/实体命名对应）、i18n displayName 术语；**命名违规的代码修复归 MR2（P2-MA4-501 等既有归属项），本计划只审计与登记**
- 5.4 跨模块契约一致性（`cross-module-dependency-audit-prompt.md`）：nop-sys/nop-auth/nop-wf/nop-code 依赖面、dict 跨模块引用、querySpace 路由；复用 MA1.1/MA1.2 报告结论，只做一致性核对
- 审计报告（`ai-dev/audits/2026-08-04-{HHmm}-arm-MA5.<n>-nop-metadata-<dimension>.md`）+ arm-index 更新 + roadmap 5.1-5.4 → done

### Out Of Scope

- MA6/MA7 审计（后续计划）
- 任何修复（MR2/MR3 承接；已知 MR2 同源项如命名修复在报告中注明归属但不执行）
- `docs-for-ai/` 文档修改（本计划审计不修改；修复归 MR3）

## Execution Plan

### Phase 1 - MA5.1 设计文档-代码 drift 审计

Status: completed
Targets: `ai-dev/design/nop-metadata/`（17 篇设计文档）+ `nop-metadata/` live 代码

- Item Types: `Proof`

- [x] **启动门禁核查**：确认 M0/MA1-MA4 已 done、MR1 completed（roadmap 对应行 + arm-index 报告清单）；未满足则不启动并上报
- [x] 执行设计文档 drift 审计：以 `01-architecture-baseline.md` 为权威基线，逐篇核对 17 篇设计文档的架构决策/模块边界/接口契约与 live 代码一致性（`design-doc-audit-prompt.md` 流程）；**特别核对 MR1 Follow-up 登记项**：`02-dto-module-restructure-decision.md:73` + `api-dto-spec.md:213` 的 `io.nop.metadata.core.dto` 陈旧引用（预期 drift，确认后归属 MR3 修复）
- [x] 历史对照：MA1-MA4 报告已登记的文档相关 finding（如 MA4.5 版权头/命名、MA3.1 假 javadoc）与本阶段发现的交叉核对，避免重复登记
- [x] 每条 drift 记录：精确 file:line + 期望修正 + P 级 + 修复归属（MR3 / MR2 同源 / 非阻塞）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1600-arm-MA5.1-nop-metadata-design-drift.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 报告包含：17 篇设计文档逐篇核对表（每篇有结论：一致 / drift 明细 / 不属于 drift）、drift 清单（file:line + 期望修正 + P 级 + 归属）
- [x] MR1 Follow-up 登记的 core.dto 陈旧引用（2 处）有明确确认结论与归属
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`（本计划为纯审计，drift 修复归 MR3）
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MA5.2 docs-for-ai 文档-代码一致性审计

Status: completed
Targets: `docs-for-ai/03-modules/nop-metadata.md` + `docs-for-ai/01-repo-map/module-groups.md`（nop-metadata 段落）+ `nop-metadata/` live 代码

- Item Types: `Proof`

- [x] 执行维度 18 审计（文档-代码一致性）：逐节核对 `docs-for-ai/03-modules/nop-metadata.md` 描述的功能/API/模块边界/命令与 live 代码一致（模块清单、实体数、BizModel 数、GraphQL 面、构建命令）；`module-groups.md` 中 nop-metadata 相关段落与 MA1.1/MA1.2 报告结论一致
- [x] 历史对照：MA1-MA4 审计揭示的事实性变化（如 xmeta 位置不可达 P2-MA3-01、topic 改名、DTO 迁移）在 docs-for-ai 中是否有陈旧陈述
- [x] 每条 drift 记录：精确 file:line + 期望修正 + P 级 + 修复归属（MR3）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1605-arm-MA5.2-nop-metadata-doc-consistency.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 报告包含：`nop-metadata.md` 逐节核对结论 + `module-groups.md` nop-metadata 段落核对结论 + drift 清单（file:line + 期望修正 + P 级 + 归属）
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`（纯审计，修复归 MR3）
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MA5.3 命名与术语一致性审计

Status: completed
Targets: nop-metadata 8 子模块 + 跨模块命名面

- Item Types: `Proof`

- [x] 执行维度 19 审计（命名与术语一致性）：跨模块同概念同一名称、`*Service`/`*BizModel`/`*DTO`/`*Entity` 命名规范遵循（与 MA4.5 报告 P2-MA4-501 `*Service` 违规 2 处核对——命名违规代码修复归 MR2，本阶段只审计），topic 命名（`nop_meta_*`）、i18n displayName 术语、错误码术语（NopMetadataErrors）一致性
- [x] 每条不一致记录：精确位置 + 建议名称 + P 级 + 归属（代码改名归 MR2/MR3，纯文档术语归 MR3）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1610-arm-MA5.3-nop-metadata-naming.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 报告包含：命名规范核对表（规范来源 + 违规清单 file:line + P 级 + 归属）
- [x] P2-MA4-501（`*Service` 违规 2 处）有交叉核对记录（不重复登记为 MA5 新发现）
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`（纯审计，修复归 MR2/MR3）
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - MA5.4 跨模块契约一致性审计

Status: completed
Targets: nop-sys/nop-auth/nop-wf/nop-code 依赖面 + dict 跨模块引用 + querySpace 路由

- Item Types: `Proof`

- [x] 执行跨模块依赖契约审计（`cross-module-dependency-audit-prompt.md`）：nop-metadata 对 nop-sys/nop-auth/nop-wf/nop-code 的依赖使用是否符合各自模块契约（dict 引用、querySpace 名、权限/角色约定、工作流集成面）；**预置依赖面现状：nop-sys/nop-code 零依赖（标 N/A）、nop-auth 仅 app pom 运行时依赖（源码无类型引用，仅 MetaManifestBuilder.java:137 Javadoc 示例字符串）、nop-wf 为唯一 compile 级契约面——核对面收敛到 nop-wf + dict/querySpace 实际引用**；复用 MA1.1 依赖图报告与 MA1.2 模块边界报告结论，只做契约一致性核对
- [x] 跨模块契约 drift 记录：精确位置 + 期望修正 + P 级 + 归属（代码契约修复归 MR2/MR3，文档陈述归 MR3）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1615-arm-MA5.4-nop-metadata-cross-module.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 报告包含：跨模块契约面核对表（每个依赖面有结论）+ drift 清单（file:line + 期望修正 + P 级 + 归属）
- [x] 复用 MA1.1/MA1.2 结论的部分有显式引用，不重复审计
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`（纯审计，修复归 MR2/MR3）
- [x] `No new test required`: 纯审计计划零代码变更
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 4 份 MA5 审计报告产出（5.1-5.4），每份含 P 级标注 + 修复归属 + 可追溯 file:line 引用
- [x] arm-index-nop-metadata.md 报告清单 +4 行、P0/P1 追踪更新；roadmap 5.1-5.4 → done
- [x] MR1 Follow-up 登记的 2 处 core.dto 陈旧引用有确认结论与归属
- [x] 所有 in-scope confirmed owner-doc drift 均有明确归属（MR3 修复 / MR2 同源 / watch-only + Why Not Blocking Closure），无静默降级
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（evidence 写入本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 已验证 4 份报告为真实审计产物（实际 drift 清单 + 可追溯 file:line + 历史对照），非模板空壳
- [x] `./mvnw compile -pl nop-metadata -am -q` 通过（纯审计零代码变更，确认无回归）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 绿色基线保持（813/0 或重新实测记录）
- [x] checkstyle / 代码规范检查通过（无代码变更，以 mvn 默认检查为准；历史计划惯例记 "checkstyle N/A"）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/` 下文件后执行）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时，Minimum Rule #26）

## Deferred But Adjudicated

### MA5 经显式裁定 deferred 的 P2/P3 drift（若有；被裁定归 MR3 的 P2 项不在此列，见 Current Baseline 裁定通道）

- Classification: `watch-only residual` 或 `out-of-scope improvement`
- Why Not Blocking Closure: 该项经审计报告显式裁定为 deferred（roadmap 规则 1 只处理 P0/P1 的 P2/P3 范畴），记录实际理由（见 Current Baseline 裁定通道）。
- Successor Required: `no`（后续批次另行规划，非本批 scope）

## Non-Blocking Follow-ups

- watch-only 项复核维持原裁定者，登记结论即可（不产生修复债务）
- **MR2 执行交叉对账声明**：MA5.3 报告的命名陈述（如 `*Service` 改名目标名）是 MR2（执行顺序 `{3}`）P2-MA4-501 修复的输入——MR2 执行时应读取 MA5.3 报告交叉对账，避免改名修复与文档陈述脱节（本 plan 不改 MR2 文本，由 MR2 执行者按 Related 顺序读取）
- 若 5.4 审计发现跨模块契约需要 nop-sys/nop-auth/nop-wf/nop-code 侧联动修改，作为 MR4 跨维度裁决输入

## Closure

Status Note: 纯审计计划完成——4 份 MA5 报告（5.1 设计文档 drift / 5.2 docs-for-ai 一致性 / 5.3 命名术语 / 5.4 跨模块契约）产出并登记 arm-index，roadmap 5.1-5.4 → done；0 P0 / 0 P1 新增 / 26 P2 + 44 P3，全部有明确修复归属（文档 drift → MR3，命名/契约代码项 → MR2），MR1 Follow-up 2 处 core.dto 陈旧引用确认归 MR3；无 P0 无需即时通道。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session `ses_033f496e0ffe1yrVywDqg2wq37`）
- Audit Session: ses_033f496e0ffe1yrVywDqg2wq37
- Evidence:
  - 8 项实质 Gate 中 7 项 PASS + 1 项纯簿记（executor 收尾，本段已补）；全部 Exit Criterion 与 Closure Gate 已勾选（见上）
  - Anti-Hollow 检查：4 份报告 10 项 spot-check 与 live 代码 100% 吻合（core.dto 陈旧 / META_SCHEMA:1308 / SqlErrors.java:27 错误码 / core 无 dto 包 / source-anchors META-001 陈旧 / DATA_SOURCE_ID:DATASOURCE_TYPE 双拼写 / 2 个 *Service 存活 / getProp("wf:wfName") / wf/approve-status dict 存在），无模板空壳
  - 无静默降级检查：54 + 6 + 7 + 3 项 drift 全部显式归属（MR3 为主，P2-MA5-301/401 归 MR2）；Deferred 段无条目（无裁定 deferred 项）；Non-Blocking Follow-ups 仅 watch-only 维护 + MR2 交叉对账声明 + MR4 联动备注，无 confirmed defect
  - `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0（闭口前复核）
  - 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → BUILD SUCCESS **825 tests / 0 failures / 0 errors / 0 skipped**（91 报告文件；基线 813 差值为测试演化，MR1 修复后 814+，全绿）；`./mvnw compile -pl nop-metadata -am -q` → exit 0
  - checkstyle N/A（零代码变更；`checkstyle:check` 为 pre-existing 上游模块 sun 配置基线失败，历史惯例记录）
  - `node ai-dev/tools/check-doc-links.mjs --strict` → exit 0（5 个 BROKEN_LINK 警告全部 pre-existing 于 2249/2250/nop-stream 计划文件，非本轮引入）
  - Deferred 项分类检查：无 in-scope live defect 被降级

Follow-up:

- MR2 执行交叉对账声明（plan Non-Blocking Follow-ups 既有项）：MR2 执行时应读取 MA5.3 报告（P2-MA5-301 dataSource 双拼写裁决输入）与 MA5.4 报告（P2-MA5-401 getProp 修复）
- watch-only 复核维持：MA5.1 3 项（changeSource dict 化已完成 / 11 Phase 4 已实现 / 12 §3.4 事件叙事自指正确）登记结论即完成，不产生修复债务
- 4 份报告的 drift 修复归 MR3（R3.0 展开器输入，含 MR1 Follow-up 2 处 core.dto）
- 无 remaining plan-owned work
