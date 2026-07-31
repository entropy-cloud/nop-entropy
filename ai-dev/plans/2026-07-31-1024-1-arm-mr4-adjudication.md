# 1 MR4 — 跨维度 P1 裁决与索引一致性修复

> Plan Status: completed
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §MR4, `ai-dev/audits/arm-index.md`, MR1/MR2/MR3 closure plans
> Mission: audit-remediation
> Work Item: MR4

## Purpose

把 MR1/MR2/MR3 三个批量修复之间可能存在的 fix-surface 冲突、以及 arm-index 与 live repo 之间的状态漂移，逐一裁决并收敛，使 `arm-index.md` 的每一条 P0/P1 finding 状态都能被 live repo 证实，为 MV（全量验证与可追溯性）建立干净的基线。

## Current Baseline

- MR1/MR2/MR3 三个 plan 均已 `completed`（2026-07-31）；arm-index P1 表内全部行标记 `fixed`；P0 表内 P0-MA2-01、P0-MA6-01 均标记 `fixed`。
- **arm-index 头部的状态汇总行写的是「P0 未解决 1」，与 P0 表（全部 fixed）矛盾** — 汇总行陈旧，需要校正。
- **MA4.3 的 6 个 P1 finding（MA4.3-01/02/03/04/05/07）从未在 arm-index P1 表中展开为行**。MR2 plan Phase 1 Exit Criteria 声称"MA4.3 P1 findings are enumerated and expanded in arm-index.md P1 table"且已勾选 `[x]`，但 live `arm-index.md` 中不存在任何 `MA4.3` 行 — 属于 overclaimed closure。
- **live repo 测试覆盖现状（2026-07-31 核查）**：
  - `nop-ai-api`：仅 `TestChatOptions.java` 1 个测试文件（MR2 新增，覆盖 MA4.3-01 的一小部分）
  - `nop-ai-tools`：仅 `TestSequentialThinkingBizModel.java` 1 个文件（MR2 新增，覆盖 MA4.3-05 的一小部分）
  - `nop-ai-dao`：**0 个测试文件**（MA4.3-02 完全未处理）
  - `nop-ai-core`：13 个测试文件，但 `api/` 包（43 文件，MA4.3-04 核心范围）无测试
  - `nop-ai-service`：仅 1 个测试文件（MA4.3-03/-07 的 20 个 BizModel 未覆盖）
  - MR2 的"minimum 3 test methods per module"验收只落在 2 个模块（nop-ai-api、nop-ai-tools）上，7 个 zero-test 模块中 5 个未处理。
- 跨 MR 重叠 fix-surface（需逐一裁定是否冲突）：
  - `NopAiModel.apiKey`：MR1 限制 xmeta queryable/sortable → MR2 确认 Delta xmeta → MR3 增加 `@JsonIgnore` + xmeta `published=false` + codegen 重新生成 `NopAiModelOutputBean`（3 个 MR 触及同一字段）
  - `BashExecutor`：MR2 修空 catch（LOG.warn）+ MR3 加命令输入校验（P1-MA6.2-004）
  - `ChatServiceImpl`：MR2 注册 `nopChatService` bean；MR1 的废弃 API 清理（P1-MA1-002）与之相邻
  - `@Auth` 权限命名：MR2 引入 `ai:<entity>:<action>` 约定，需与 MR1 命名统一（P1-MA1-032）及平台 CRUD 权限惯例核对

## Goals

- 检测并裁决 MR1/MR2/MR3 之间的 fix-surface 冲突；对每个重叠面确认最终 live 状态一致，冲突项给出明确裁定并落地修复。
- 修复 `arm-index.md` 与 live repo 的状态漂移：补全 MA4.3 P1 行（6 条）、校正「P0 未解决 1」汇总行、逐行核验 P1 表状态可被 live 证据证实。
- 收敛 MR2 overclaimed 的 MA4.3 P1 测试覆盖 gap：`nop-ai-dao`、`nop-ai-core/api`、`nop-ai-service` BizModels 补齐最低测试覆盖，并核实 `nop-ai-api`/`nop-ai-tools` 是否达到已声称的最低标准。
- 产出 MR4 裁决记录并标记 roadmap R4.1 为 done。

## Non-Goals

- 不做任何新的审计维度或重新审计已完成维度（MA1-MA6 报告已 done）。
- 不修复 P2/P3 finding（roadmap 框架明确 deferred）。
- 不处理被排除的 MCP 模块（`nop-ai-mcp-server`、`nop-spring-mcp-server*`）。
- 不做 MV（全量验证）和 MG（guard 沉淀）的工作 — 由后续 plan 承接。

## Scope

### In Scope

- 跨 MR fix-surface 冲突检测与裁定（含 apiKey 三层暴露、BashExecutor、ChatServiceImpl、@Auth 命名）
- arm-index.md 一致性修复（MA4.3 P1 行补全、P0 汇总校正、P1 表逐行状态核验）
- MA4.3 遗留 P1 测试覆盖补强（nop-ai-dao、nop-ai-core/api、nop-ai-service BizModels；nop-ai-api/nop-ai-tools 核实）
- roadmap R4.1 标记 done

### Out Of Scope

- MV/MG 工作项
- P2/P3 修复
- MCP 模块

## Execution Plan

### Phase 1 — 跨 MR fix-surface 冲突检测与裁定

Status: completed
Targets: `nop-ai/` 相关源码、MR1/MR2/MR3 plan、`arm-index.md`

- Item Types: `Proof | Decision | Fix`

- [x] 枚举 MR1/MR2/MR3 的全部重叠 fix-surface（至少包含 apiKey 处理链、BashExecutor、ChatServiceImpl bean、@Auth 命名约定）
- [x] 对每个重叠面，从 live repo 验证最终状态是否一致（例如：apiKey 在 ORM 源模型、Delta xmeta、生成的 `_NopAiModel.xmeta`、`NopAiModelOutputBean`、GraphQL schema 五层中均不再暴露）
- [x] 对发现的不一致项（若有），给出裁定并修复，附 focused test
- [x] 将每个重叠面的裁定结论写入 `arm-index.md`（新增「MR4 裁定」小节或扩展 P1 表备注）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 每个重叠 fix-surface 都有明确的最终状态结论，且与 live repo 一致
- [x] 不一致项已修复并有回归测试（如有发现）
- [x] `arm-index.md` 中记录了 MR4 裁定结果
- [x] `./mvnw compile -pl nop-ai -am` 通过
- [x] No owner-doc update required（裁定结论记录在 arm-index，属 ai-dev 范畴）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — arm-index 与 live repo 状态一致性修复

Status: completed
Targets: `ai-dev/audits/arm-index.md`

- Item Types: `Fix | Proof`

- [x] 在 P1 表中补全 6 条 MA4.3 P1 finding 行（MA4.3-01/02/03/04/05/07），每行标注真实状态（fixed 需引用 live 测试文件证据；未完成标注 open 并指向 Phase 3）
- [x] 校正头部状态汇总行（「P0 未解决 1」→ 与实际 P0 表一致）
- [x] 逐行核验 P1 表中所有 `fixed` 行的证据（提交/测试文件/代码路径），发现无法证实的行记录为待裁决
- [x] 核验通过后更新 `arm-index.md` 报告清单的状态列

Exit Criteria:

- [x] P1 表包含全部 MA4.3 P1 行，且每行状态可被 live repo 文件/测试证实
- [x] 状态汇总行与 P0/P1 表一致
- [x] 无法证实的 `fixed` 行数为 0（或已在 Phase 1 裁定并修复）
- [x] No owner-doc update required（arm-index 属 ai-dev 范畴）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — MA4.3 遗留 P1 测试覆盖收敛

Status: completed
Targets: `nop-ai/nop-ai-dao/src/test/`、`nop-ai/nop-ai-core/src/test/`、`nop-ai/nop-ai-service/src/test/`（必要时 `nop-ai-api`、`nop-ai-tools`）

- Item Types: `Fix | Proof`

MR2 声称「每个 zero-test 模块 ≥3 test methods」，实际仅 2 个模块达标。本 Phase 收敛剩余 P1 覆盖 gap，覆盖范围与最低标准以 MA4.3 报告建议为据：

- [x] `nop-ai-dao`（MA4.3-02）：新增 ≥3 个测试方法，覆盖 ORM 实体映射 smoke test 与 Biz 接口契约（使用 Nop ORM 测试工具）
- [x] `nop-ai-core/api`（MA4.3-04）：新增 ≥3 个测试方法，优先覆盖 `CosineSimilarity`/`RelevanceScore`（数学密集高风险）与 `DefaultAiChatFunctionTool`/`ToolSpecification`
- [x] `nop-ai-service`（MA4.3-03/-07）：按 `TestNopAiChatResponseSummarizeByModel` 模式为 ≥3 个 BizModel 新增测试（覆盖 CRUD 与业务方法）
- [x] 核实 `nop-ai-api`/`nop-ai-tools` 现有测试是否达到 MR2 声称的最低标准，不足则补齐
- [x] 为每个新增测试确认断言强度（验证正确结果，而非仅无异常）

Exit Criteria:

- [x] `nop-ai-dao`、`nop-ai-core/api`、`nop-ai-service` 各模块新增测试满足最低方法数与覆盖范围要求
- [x] `nop-ai-api`、`nop-ai-tools` 的覆盖状态经核实并记录
- [x] 新增测试均为行为断言（非空断言/仅无异常）
- [x] `./mvnw test -pl nop-ai-dao,nop-ai-core,nop-ai-service,nop-ai-api,nop-ai-tools -am` 通过
- [x] **无静默跳过**：新增测试路径无空断言、无 catch-and-pass
- [x] No owner-doc update required（测试内部收敛，不改公开契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — arm-index 更新、roadmap 状态与关闭

Status: completed
Targets: `ai-dev/audits/arm-index.md`、`ai-dev/backlog/audit-remediation-roadmap.md`

- Item Types: `Fix | Follow-up`

- [x] Phase 3 完成后将 MA4.3 P1 行状态更新为 `fixed`（引用具体测试文件）
- [x] 更新 roadmap：R4.1 标记 done
- [x] 独立子 agent closure audit 并记录证据

Exit Criteria:

- [x] `arm-index.md` 无未裁决的状态漂移
- [x] roadmap R4.1 = done
- [x] 独立 closure audit 完成且证据写入本 plan 的 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有跨 MR fix-surface 冲突已裁定且 live 状态一致
- [x] arm-index 与 live repo 的状态漂移已收敛（含 MA4.3 P1 行补全、P0 汇总校正）
- [x] MA4.3 遗留 P1 测试覆盖 gap 已收敛且测试通过
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）新增测试确实执行了被测代码路径（接线连通），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai -am`
- [x] `./mvnw test -pl nop-ai-dao,nop-ai-core,nop-ai-service,nop-ai-api,nop-ai-tools -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### P2/P3 findings（MA1-MA6 全维度）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 由 roadmap 框架明确排除在 P0/P1 修复范围之外，各审计报告已记录；无已确认 live defect 被降级。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MV（全量 build + test + 独立 closure audit + P0/P1 可追溯）由下一个 plan 承接
- MG（lessons + skills + docs）在 MV 之后承接

## Closure

Status Note: MR1/MR2/MR3 的全部重叠 fix-surface 已裁定且 live 状态一致；arm-index 状态漂移已收敛（MA4.3 P1 行补全、P0 汇总校正、3 条无法证实行全部裁定/修复）；MA4.3 遗留 P1 测试覆盖 gap 已收敛（dao/core-api/service 新增 21 个测试方法）；roadmap R4.1 = done。独立 closure audit 通过（见下）。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（session: ses_049708508ffeITonF1A576yaYG）
- Audit Session: ses_049708508ffeITonF1A576yaYG
- Evidence:
  - Phase 1（裁定）：7/7 PASS — arm-index §MR4 裁定 + §MR4 P1 表逐行核验存在；ORM 源 apiKey `tagSet="enc,not-query,not-sort,not-pub" ui:show="X"`（nop-ai.orm.xml:299-300）；生成 `_NopAiModel.xmeta:38-39` queryable/sortable/published=false + internal；Delta xmeta insertable/updatable=false；`NopAiModelOutputBean` 无 apiKey 字段；`TestNopAiModelApiKeyXmeta` 3 方法真实断言；重新生成一致性（_app.orm.xml diff）
  - Phase 2（索引一致性）：7/7 PASS — 汇总行 P0 未解决 0；P1 表 6 条 MA4.3 行；逐行核验裁定 MA2-005/MA6.5-002/MA6.5-003/MA6.5-001；persister 加密机制（ENCRYPTED_MARKER/ITextCipher/maybeEncrypt/maybeDecrypt/@InjectValue）；AiCoreConfigs `nop.ai.persist.exchange-encrypt`；persister 测试 3 个加密方法；git log 证实 MR3 从未触及 persister（overclaim 属实）
  - Phase 3（测试覆盖）：6/6 PASS — TestNopAiOrmEntityMapping 5 方法（24 断言，含 enc 绑定器 DB 明文检查、21 Biz 接口契约）；CosineSimilarity/RelevanceScore 8 方法 + DefaultAiChatFunctionTool 4 方法；TestNopAiBizModelEntityCrud 4 方法覆盖 3 BizModel；TestChatOptions 3 方法；TestSequentialThinkingBizModel 3 方法；无空断言/catch-and-pass（catch 均 rethrow 为 IllegalStateException）
  - Phase 4（关闭）：audit 时 plan 文件尚为 planned（预期，关闭动作在 audit 后完成）；roadmap R4.1 done + MR4 ✅ 已落地；本 Closure 段已按 audit 要求补齐
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1024-1-arm-mr4-adjudication.md --strict` 退出码为 0（audit 时 active 状态 exit 0；关闭后重跑仍为 0，见日志）
  - Anti-Hollow 检查：新增测试均执行真实代码路径（ORM 栈 H2 落库 + 加密绑定器 + 反射调用工具函数 + JDBC 聚合 SQL）；`scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 1 为 24 条**既有** high 发现（nop-ai-agent Phase-2 接口 UOE、DefaultAiChatService 废弃 API UOE、nop-ai-shell），全部为 MR4 之前已裁定模式，无一触及 MR4 新增/修改文件
  - Deferred 项分类检查：P2/P3 全维度 deferred 由 roadmap 框架明示；无 in-scope live defect 被降级（MA6.5-002 真实 defect 已在 MR4 内修复）

Follow-up:

- MV（全量验证 + 独立 closure audit + P0/P1 可追溯）由 `2026-07-31-1024-2-arm-mv-validation.md` 承接
- MG（lessons + skills + docs）由 `2026-07-31-1024-3-arm-mg-guard-retention.md` 承接
- 其余 no remaining plan-owned work

## Optional Sections

## Risks And Rollback

- Phase 3 新增测试可能暴露既有行为缺陷：若发现 live defect，就地修复并保持在本 plan scope 内；若属 P2/P3 或 MCP 模块，记录后移交对应路径。
- `arm-index.md` 修改仅为状态校正，可逐行回滚。
