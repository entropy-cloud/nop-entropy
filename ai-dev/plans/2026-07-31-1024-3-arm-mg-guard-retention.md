# 3 MG — Guard 沉淀：lessons、skills 与文档同步

> Plan Status: active
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

Status: planned
Targets: `ai-dev/lessons/`

- Item Types: `Follow-up | Decision`

- [ ] 从 MV closure audit 结果与本闭环记录中确认实际发生的失败模式清单（基于 live 证据，非候选臆测）
- [ ] 每条新失败模式按 `ai-dev/lessons/` 既有格式编写条目（编号接续 05），包含发生场景、根因、预防规则
- [ ] 更新 `ai-dev/lessons/README.md` 索引

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增 lessons 条目均对应本闭环中实际发生且证据可查的失败模式
- [ ] 每条条目格式与既有条目一致，索引已更新
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — G.2 重复审计维度提升为 skills prompt

Status: planned
Targets: `ai-dev/skills/`

- Item Types: `Follow-up | Decision`

- [ ] 识别本次闭环中反复使用且值得固化的检查项（如：fix-status 与 live repo 追溯核验、zero-test 模块扫描、凭证字段跨层暴露核查）
- [ ] 新增 prompt 或修订既有 prompt（如 `deep-audit-prompts.md` 中增补对应维度）
- [ ] 新 prompt 中明确可执行步骤与输出格式，供后续审计直接复用

Exit Criteria:

- [ ] 新增/修订的 prompt 文件落地且内容可执行（非占位）
- [ ] prompt 中引用的路径/命令在仓库中真实存在
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — G.3 文档同步：docs-for-ai 与 ai-dev/design

Status: planned
Targets: `docs-for-ai/`（module-groups.md、service-layer.md、api-and-graphql.md 等最小归属文档）、`ai-dev/design/nop-ai-agent/`

- Item Types: `Fix | Decision`

- [ ] 核对 `docs-for-ai/01-repo-map/module-groups.md` 中 nop-ai 模块组描述与 MR1-MR4 后 live 结构一致（含废弃 API 清理、nopChatService bean、模块边界）
- [ ] 在最小归属文档中补充 nop-ai 约定：`@Auth` 权限命名 `ai:<entity>:<action>`、凭证字段（apiKey）多层收敛处理方式（ORM → xmeta → DTO）
- [ ] 同步 `ai-dev/design/nop-ai-agent/` 中与 MR2 bridge 变更（ChatOptionsHelper、TokenEstimators）相关的设计文档，使文档描述与 live 实现一致
- [ ] 运行文档链接检查并修复问题

Exit Criteria:

- [ ] `docs-for-ai/` 中新增/修订内容与 live repo 一致（抽查 3 处：模块组条目、@Auth 约定、凭证处理）
- [ ] `ai-dev/design/` 中 bridge 相关文档与 live 实现一致
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `docs-for-ai/INDEX.md`（若路由/锚点变化）与 `docs-for-ai/04-reference/source-anchors.md` 已同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — roadmap 收口与关闭

Status: planned
Targets: `ai-dev/backlog/audit-remediation-roadmap.md`, `ai-dev/audits/arm-index.md`

- Item Types: `Follow-up`

- [ ] 更新 roadmap：G.1/G.2/G.3 标记 done
- [ ] 更新 `ai-dev/audits/arm-index.md` 状态汇总（若涉及）
- [ ] 独立子 agent closure audit 并记录证据

Exit Criteria:

- [ ] roadmap MG 全部工作项 done
- [ ] 独立 closure audit 完成且证据写入本 plan Closure 段
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] lessons 条目与 skills prompt 已落地且可复用
- [ ] `docs-for-ai/` 与 `ai-dev/design/` 已同步至 live baseline
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope 文档漂移
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已抽查文档内容与 live repo 一致（非仅文件存在）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 纯文档计划：无需 `./mvnw` 构建验证；`No new test required: pure documentation plan, no code change`（见 plan guide 纯文档计划豁免）

## Deferred But Adjudicated

N/A — 本计划为纯文档沉淀，无延期项。

## Non-Blocking Follow-ups

- 后续平台级审计可直接复用本计划产出的 skills prompt 与 lessons 规则

## Closure

Status Note: 待关闭时填写。
Completed: 待关闭时填写。

Closure Audit Evidence:

- Reviewer / Agent: 待关闭时由独立子 agent 填写
- Evidence: 待关闭时填写

Follow-up:

- 待关闭时填写

## Optional Sections

## Risks And Rollback

- 文档同步仅为文本变更，逐文件可回滚；`check-doc-links.mjs --strict` 保证链接完整性。
- 若 G.3 核对时发现比预期更大的文档漂移，超出本 plan scope 的漂移项记录为 follow-up，不扩大本 plan。
