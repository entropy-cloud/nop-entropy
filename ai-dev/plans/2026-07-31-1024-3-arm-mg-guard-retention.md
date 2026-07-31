# 3 MG — Guard 沉淀：lessons、skills 与文档同步

> Plan Status: completed
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §MG，MV closure audit 结果，MR1-MR4 修复记录
> Mission: audit-remediation
> Work Item: MG

## Purpose

把 nop-ai 审计-修复闭环中暴露的新失败模式沉淀为 `ai-dev/lessons/`，把反复使用的审计维度/检查项沉淀为 `ai-dev/skills/` 新 prompt，并同步 `docs-for-ai/` 与 `ai-dev/design/` 中与 MR1-MR4 修复后 live baseline 不一致的内容，完成 roadmap 的 MG 里程碑。

## Current Baseline

- MV 完成后整条审计-修复链路已验证：MA1-MA6 审计 done、MR1-MR4 修复 done、P0/P1 全部可追溯。
- 本次闭环暴露的可沉淀失败模式（候选）：
  1. overclaimed closure：MR2 声称「MA4.3 P1 已展开进 arm-index」且 Exit Criteria 勾选，但 live 文件中不存在对应行（fix-status 与 live repo 漂移）
  2. 凭证字段跨层暴露：`NopAiModel.apiKey` 在 ORM 源模型 → Delta xmeta → 生成的 xmeta → GraphQL DTO 多层需要逐层收敛（MR1/MR2/MR3 三个 MR 各自触及同一面，最终才闭合）
  3. zero-test 模块在 CI 中不可见：MA4.3 才发现 7 个模块 0 测试，构建从不失败
  4. SSRF/路径逃逸类安全缺陷集中出现在 tool executor 层（HttpRequestExecutor、GraphqlQueryExecutor、LocalToolFileSystem、BashExecutor 四个 P1 同源）
- `ai-dev/lessons/` 现有 4 条（01-04）；`ai-dev/skills/` 已有 21 维度审计 prompt 等；`docs-for-ai/02-core-guides/` 尚无 nop-ai 专属的 @Auth 权限命名与凭证字段处理约定章节；`ai-dev/design/nop-ai-agent/` 下文档未随 MR2 的 ChatOptions/TokenEstimators bridge 变更同步。

## Goals

- G.1：将新失败模式提升为 `ai-dev/lessons/` 条目（≥3 条，覆盖上述候选中的实际发生项）。
- G.2：将重复审计维度提升为 `ai-dev/skills/` 新 prompt 或对既有 prompt 的修订（≥1 个新 prompt，覆盖实际反复使用的检查项）。
- G.3：更新 `docs-for-ai/`（@Auth 权限命名约定、凭证字段多层收敛约定、nop-ai 模块组条目）与 `ai-dev/design/`（nop-ai-agent bridge 变更同步），全部与 live baseline 一致。
- 更新 roadmap：MG 标记 done。

## Non-Goals

- 不做任何代码行为变更（本计划纯文档/知识沉淀）。
- 不重写历史审计报告或历史 plan。
- 不新增审计维度矩阵条目之外的维度定义（G.2 仅沉淀 prompt 形态）。

## Scope

### In Scope

- lessons 条目编写
- skills prompt 新增/修订
- `docs-for-ai/` 与 `ai-dev/design/` 的 live-baseline 同步
- roadmap MG 状态更新

### Out Of Scope

- 代码修改
- 历史文档重写
- 新审计执行

## Execution Plan

### Phase 1 — G.1 失败模式提升为 lessons

Status: completed
Targets: `ai-dev/lessons/`

- Item Types: `Follow-up | Decision`

- [x] 从 MV closure audit 结果与本闭环记录中确认实际发生的失败模式清单（基于 live 证据，非候选臆测）
- [x] 每条新失败模式按 `ai-dev/lessons/` 既有格式编写条目（编号接续 05），包含发生场景、根因、预防规则
- [x] 更新 `ai-dev/lessons/README.md` 索引

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 lessons 条目均对应本闭环中实际发生且证据可查的失败模式
- [x] 每条条目格式与既有条目一致，索引已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — G.2 重复审计维度提升为 skills prompt

Status: completed
Targets: `ai-dev/skills/`

- Item Types: `Follow-up | Decision`

- [x] 识别本次闭环中反复使用且值得固化的检查项（如：fix-status 与 live repo 追溯核验、zero-test 模块扫描、凭证字段跨层暴露核查）
- [x] 新增 prompt 或修订既有 prompt（如 `deep-audit-prompts.md` 中增补对应维度）
- [x] 新 prompt 中明确可执行步骤与输出格式，供后续审计直接复用

Exit Criteria:

- [x] 新增/修订的 prompt 文件落地且内容可执行（非占位）
- [x] prompt 中引用的路径/命令在仓库中真实存在
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — G.3 文档同步：docs-for-ai 与 ai-dev/design

Status: completed
Targets: `docs-for-ai/`（module-groups.md、service-layer.md、api-and-graphql.md 等最小归属文档）、`ai-dev/design/nop-ai-agent/`

- Item Types: `Fix | Decision`

- [x] 核对 `docs-for-ai/01-repo-map/module-groups.md` 中 nop-ai 模块组描述与 MR1-MR4 后 live 结构一致（含废弃 API 清理、nopChatService bean、模块边界）
- [x] 在最小归属文档中补充 nop-ai 约定：`@Auth` 权限命名 `<BizObjName>:<action>`（MR2 计划文本的 `ai:<entity>:<action>` 前缀经 MR4 裁定为笔误，live 落地为平台 `bizObjName:action` 约定）、凭证字段（apiKey）多层收敛处理方式（ORM → xmeta → DTO）
- [x] 同步 `ai-dev/design/nop-ai-agent/` 中与 MR2 bridge 变更（ChatOptionsHelper、TokenEstimators）相关的设计文档，使文档描述与 live 实现一致
- [x] 运行文档链接检查并修复问题

Exit Criteria:

- [x] `docs-for-ai/` 中新增/修订内容与 live repo 一致（抽查 3 处：模块组条目、@Auth 约定、凭证处理）
- [x] `ai-dev/design/` 中 bridge 相关文档与 live 实现一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `docs-for-ai/INDEX.md`（若路由/锚点变化）与 `docs-for-ai/04-reference/source-anchors.md` 已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — roadmap 收口与关闭

Status: completed
Targets: `ai-dev/backlog/audit-remediation-roadmap.md`, `ai-dev/audits/arm-index.md`

- Item Types: `Follow-up`

- [x] 更新 roadmap：G.1/G.2/G.3 标记 done
- [x] 更新 `ai-dev/audits/arm-index.md` 状态汇总（若涉及）
- [x] 独立子 agent closure audit 并记录证据

Exit Criteria:

- [x] roadmap MG 全部工作项 done
- [x] 独立 closure audit 完成且证据写入本 plan Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] lessons 条目与 skills prompt 已落地且可复用
- [x] `docs-for-ai/` 与 `ai-dev/design/` 已同步至 live baseline
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope 文档漂移
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已抽查文档内容与 live repo 一致（非仅文件存在）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 纯文档计划：无需 `./mvnw` 构建验证；`No new test required: pure documentation plan, no code change`（见 plan guide 纯文档计划豁免）

## Deferred But Adjudicated

N/A — 本计划为纯文档沉淀，无延期项。

## Non-Blocking Follow-ups

- 后续平台级审计可直接复用本计划产出的 skills prompt 与 lessons 规则

## Closure

Status Note: 本计划为纯文档/知识沉淀计划。G.1 四条 lessons（05-08）基于本闭环 live 证据编写并索引；G.2 新增可复用的修复-闭环核验 skill prompt；G.3 将 docs-for-ai 与 ai-dev/design 同步至 MR1-MR4 后 live baseline。roadmap MG 全部 done、arm-index 状态收口。所有 in-scope 项落地，无降级项。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_049243cffffeN4a8knAfMdaYTc`）
- Evidence:
  - Phase 1（lessons）：PASS — 05-08 文件存在且格式与 04 一致；README 索引已更新（`ai-dev/lessons/README.md:20-23`）；L05 证据（arm-index §P1-MA2-005 裁定 `:153-157`、§P1-MA6.5-002 overclaim `:159-163`、MV 纠正 P1-MA5-003 `:151/180/197`）；L06 证据（`nop-ai/model/nop-ai.orm.xml:299-300` tagSet + `_NopAiModel.xmeta:38-40` 不暴露 + `TestNopAiModelApiKeyXmeta.java` 3 方法）；L07 证据（MA4.3 报告 7 个零测试模块）；L08 证据（`HttpRequestExecutor.java:78/104` validateUrl/isPrivateIp、`BashExecutor.java` DESTRUCTIVE_COMMAND、`LocalToolFileSystem.java:40/53` isPathAllowed 定义且被调用）
  - Phase 2（skills）：PASS — `ai-dev/skills/audit-remediation-verification-prompt.md` 三类检查项含可执行步骤 + 输出格式（VERIFIED/OVERCLAIM/PARTIAL、ZERO-TEST/OK、LEAK/CONVERGED/FRAGILE）；引用的路径/命令（`ai-dev/audits/arm-index.md`、`git log --oneline -- <file>`、`model/*.orm.xml`、交叉引用 3 个既有 prompt）全部真实存在
  - Phase 3（docs）：PASS — module-groups nop-ai 条目与 live 结构一致（`IChatService.java`、`IAiChatService.java:15` @Deprecated、`ai-defaults.beans.xml:8-9` nopChatService、`nop-ai-agent/pom.xml:29,34` test scope）；service-layer @Auth 约定 `<BizObjName>:<action>` 与 live 4 类 19 方法一致；model-first-development 凭证收敛与 live orm.xml/xmeta 一致；bridge 文档（llm-layer §4.0、01-architecture-baseline、reliability §7.3/7.4、usage-and-billing §五、glossary）与 live `ChatOptionsHelper.java:8`/`TokenEstimators.java:9`/`CalibratedTokenEstimator.java:29-31,73-74`（EMA alpha=0.3, clamp [0.25,4.0]）一致；无新增锚点，INDEX/source-anchors 无需变更
  - Phase 4（roadmap）：PASS — `audit-remediation-roadmap.md:131-133` G.1/G.2/G.3 done、`:151` MG ✅ closed、`:254` 复用表登记新 prompt；`arm-index.md:7` "MV+MG 已收口"
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors / 0 warnings）
  - Anti-Hollow 检查：audit 逐条对照文档内容与 live 代码/测试（上述行号证据），非仅文件存在性；纯文档计划，无运行时调用链，scan-hollow-implementations 不适用（无代码变更）
  - Audit 发现 3 项问题，已全部修复：(1) daily log 缺 MG 条目 → 已补 `ai-dev/logs/2026/07-31.md` 顶部条目；(2) plan 文本 `ai:` 前缀 → 更正为 `<BizObjName>:<action>`（MR4 裁定笔误）；(3) module-groups 补全 nop-ai-coder/nop-ai-maven/nop-ai-dsl-orm 模块
  - Deferred 项分类检查：本计划 N/A（无 deferred 项），非阻塞 follow-up 仅"后续审计复用产出"，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（后续平台级审计复用本计划产出的 skill prompt 与 lessons，属治理建议非本计划遗留）

## Optional Sections

## Risks And Rollback

- 文档同步仅为文本变更，逐文件可回滚；`check-doc-links.mjs --strict` 保证链接完整性。
- 若 G.3 核对时发现比预期更大的文档漂移，超出本 plan scope 的漂移项记录为 follow-up，不扩大本 plan。
