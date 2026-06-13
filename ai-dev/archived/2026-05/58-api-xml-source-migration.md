# 58 API XML Source Migration

> Plan Status: completed
> Last Reviewed: 2026-05-29
> Source: user request on 2026-05-29 + `ai-dev/plans/55-orm-xml-icon-rollout-and-checker.md` + commit `193b47a0a` + commit `3cf218824`

## Purpose

仿照仓库已完成的 `orm.xlsx -> orm.xml` 迁移方式，将当前 live codegen 链路中的 API 模型输入从 `api.xlsx` 切换到 `api.xml`，同时保留原始 `api.xlsx` 文件作为可逆展示载体，收口 model-first 基线并减少 AI/文本化编辑对 Excel 的依赖。

## Current Baseline

- 当前 live repo 中仍存在两条模块级 API codegen 链路直接读取 source `api.xlsx`：`nop-rule/nop-rule-codegen/postcompile/gen-orm.xgen` + `nop-rule/nop-rule-web/precompile/gen-page.xgen`，以及 `nop-wf/nop-wf-codegen/postcompile/gen-orm.xgen` + `nop-wf/nop-wf-web/precompile/gen-page.xgen`。
- `nop-rule/model/nop-rule.api.xlsx` 与 `nop-wf/model/nop-wf.api.xlsx` 仍是 source 输入；对应的 generated `nop-rule-api/.../nop-rule.api.xml` 与 `nop-wf-api/.../nop-wf.api.xml` 已存在，但它们位于下游模块产物中，不是当前 source baseline。
- live repo 还存在一个默认输出路径漂移：`nop-ai/nop-ai-coder/.../ai-api-design.task.xml` 当前同时输出 XML 与 Excel，其中 XML 旧固定文件名为 `ai-gen.api.xml`，Excel 仍写到 `model/${appName}.api.xlsx`；若要对齐 model-first 约定，需要把 XML 输出文件名改为 `model/${appName}.api.xml`，同时保留 Excel 导出不删除。
- `nop-network/nop-rpc-model/.../api.register-model.xml` 已同时注册 `api.xlsx` 与 `api.xml` loader，平台本身支持两种格式并存；因此本次迁移重点在 source 文件和 codegen 输入切换，而不是底层 loader 改造。
- 需要保留 `nop-rule/model/nop-rule.api.xlsx` 与 `nop-wf/model/nop-wf.api.xlsx`，不能删除；source `api.xml` 需通过 `nop-cli convert` 生成并成为新的 live codegen 输入。
- 当前 docs-for-ai 尚未把 API source 从 `api.xlsx` 向 `api.xml` 的 model-first 约定写成明确基线，至少 `docs-for-ai/02-core-guides/external-app-development.md` 仍举例 `app-mall.api.xlsx`。
- focused proof 已覆盖 `nop-ai-coder`、`nop-rpc-model`、`nop-rule-codegen`、`nop-wf-codegen`、`nop-wf-web`、`nop-rule-web`；其中 `nop-rule-web` 在补齐 `ApiMessageFieldModel.codegenJavaType` 与 API bean 模板后已恢复 `BUILD SUCCESS`。
- 广义验证 `./mvnw test -pl nop-rule,nop-wf -am -T 1C -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false` 仍被无关上游测试阻塞：`nop-service-framework/nop-graphql/nop-graphql-core` 的 `io.nop.graphql.core.ws.TestJsonRpcWebSocketHandler.testUnsubscribe` 失败；并行复跑还会偶发 `.m2` metadata lock，因此当前 closure 证据以顺序 focused proof 为准。

## Goals

- 为当前 in-scope source API 模型生成并落盘 `api.xml`，同时保留原始 `api.xlsx`。
- 将 live `gen-orm.xgen` / `gen-page.xgen` 和相关默认 XML 输出切换为 `api.xml`，同时保留现有 `api.xlsx` 导出。
- focused 验证 `nop-rule` 与 `nop-wf` 的 API/web 生成链继续正常，证明 `api.xml` 已成为可用 source baseline。
- 同步更新 owner docs / source anchors / daily log，使 `api.xml` 成为当前仓库的明确 model-first 约定之一。

## Non-Goals

- 不删除任何已有 `api.xlsx` 文件。
- 不修改 `api.xlsx` / `api.xml` 双 loader 注册机制。
- 不清理测试夹具、历史文档或 `docs/` / `docs-en/` 中所有历史 `api.xlsx` 提法，除非它们直接阻塞本次 owner-doc 收口。
- 不改造与 API 模型无关的 `orm.xlsx` / `orm.xml` 流程。

## Scope

### In Scope

- `nop-rule/model/nop-rule.api.xlsx` -> `nop-rule/model/nop-rule.api.xml` 转换并落盘
- `nop-wf/model/nop-wf.api.xlsx` -> `nop-wf/model/nop-wf.api.xml` 转换并落盘
- `nop-rule` / `nop-wf` live codegen entry points 从 `api.xlsx` 切换到 `api.xml`
- `nop-ai-coder` API 设计任务默认 XML 输出路径切换到 `api.xml`，并同步调整消费该 XML 的后续任务；Excel 导出继续保留作为辅助产物
- 最小 owner-doc / source-anchor / daily log 更新

### Out Of Scope

- `nop-runner/nop-cli-core`、`nop-ooxml-xlsx` 等测试资源中的 `test.api.xlsx`
- `nop-network/nop-rpc-model` 的导入模板 `template.api.xlsx`
- 仓库 `docs/`、`docs-en/`、`docs-for-ai-old/` 的全量历史文本清理
- 与当前 live generator 无关的其他工具注释或技能参考文档

## Execution Plan

### Phase 1 - Establish API XML Source Baseline

Status: completed
Targets: `nop-rule/model`, `nop-wf/model`, `nop-rule/nop-rule-codegen/postcompile/gen-orm.xgen`, `nop-rule/nop-rule-web/precompile/gen-page.xgen`, `nop-wf/nop-wf-codegen/postcompile/gen-orm.xgen`, `nop-wf/nop-wf-web/precompile/gen-page.xgen`, `nop-ai/nop-ai-coder/**/ai-api-design.task.xml`, `nop-ai/nop-ai-coder/**/ai-service-design.task.xml`

- Item Types: `Fix | Decision | Proof`

- [x] 使用 `nop-cli convert` 从 `nop-rule.api.xlsx` 与 `nop-wf.api.xlsx` 生成同目录 source `api.xml`
- [x] 保留原始 `api.xlsx` 文件不删除，并核对新生成 `api.xml` 已入库
- [x] 将 `nop-rule` / `nop-wf` live codegen 输入从 `model/*.api.xlsx` 切换为 `model/*.api.xml`
- [x] 将 `nop-ai-coder` API 设计任务的 XML 输出从旧固定文件名 `ai-gen.api.xml` 切换为 `model/${appName}.api.xml`，并保留 `model/${appName}.api.xlsx` 导出
- [x] 将 `nop-ai-coder` 后续消费任务同步改为读取 `model/${appName}.api.xml`，避免打断 `ai-api-design -> ai-service-design` 链路

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-rule/model/nop-rule.api.xml` 与 `nop-wf/model/nop-wf.api.xml` 已存在于 live repo
- [x] in-scope live `gen-orm.xgen` / `gen-page.xgen` 不再读取 `model/*.api.xlsx`
- [x] `nop-ai-coder` live task 默认 XML 输出已切换为 `model/${appName}.api.xml`，且 Excel 导出仍保留
- [x] `nop-ai-coder` 的 `ai-api-design -> ai-service-design` live 任务链对 XML 文件名的生产者/消费者路径已保持一致
- [x] **端到端验证**：No build execution in this phase; instead, repo-observable producer/consumer wiring for the XML path has been updated consistently and is ready for focused execution proof in Phase 2
- [x] **接线验证**：source `api.xml` 已成为 `nop-rule` / `nop-wf` live generator entry 的直接输入，而不是仅作为下游 generated artifact 存在
- [x] **无静默跳过**：不存在 in-scope generator 因遗漏仍停留在 `api.xlsx` 输入；任何 deferred case 都被显式记录
- [x] `docs-for-ai/` owner doc 已更新，或明确写明 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Verify Generator Outputs And Close

Status: completed
Targets: `nop-rule`, `nop-wf`, `nop-ai/nop-ai-coder`, `docs-for-ai`, `ai-dev/logs`, `this plan`

- Item Types: `Proof | Follow-up`

- [x] focused 验证 `nop-rule` 与 `nop-wf` 基于 source `api.xml` 的 API/web 生成链仍可成功执行
- [x] focused 验证 `nop-ai-coder` 的 `ai-api-design -> ai-service-design` 任务链已消费新的 `${appName}.api.xml` 路径
- [x] 为 `nop-ai-coder` 增加一个不依赖在线 AI 的 focused 本地测试，证明 `${appName}.api.xml` 会被保存并被后续任务加载
- [x] 同步最小 owner docs，使 API model-first baseline 与 live repo 一致
- [x] 收口日志、计划与 closure evidence

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-rule,nop-wf -am -T 1C` 通过
- [x] `nop-rule-api/.../nop-rule.api.xml`、`nop-wf-api/.../nop-wf.api.xml` 以及对应 web downstream artifacts 与 source `model/*.api.xml` 输入链保持一致
- [x] `./mvnw test -pl nop-ai/nop-ai-coder -am -T 1C -Dtest=<新增 focused 测试类>` 通过，且该测试在本地无外部 AI 依赖的情况下验证 `${appName}.api.xml` 的生产者/消费者路径
- [x] **端到端验证**：从 `nop-rule/model/nop-rule.api.xml` 与 `nop-wf/model/nop-wf.api.xml` 到对应 API/web generated outputs 的完整路径已验证
- [x] **端到端验证**：`nop-ai-coder` 的 `ai-api-design -> ai-service-design` 任务链从保存 `${appName}.api.xml` 到加载该文件的完整路径已由新增 focused 本地测试验证
- [x] **接线验证**：`gen-orm.xgen` / `gen-page.xgen` 在 live repo 中确实消费 `api.xml`，且构建日志证明相关 phase 已执行
- [x] **接线验证**：`ai-api-design.task.xml` 的 XML 输出与 `ai-service-design.task.xml` 的加载路径在 live repo 中一致，且新增 focused 本地测试证明运行时已接通
- [x] **无静默跳过**：没有通过手改 generated API/web 产物掩盖 source 输入未切换的问题
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（若 docs/log/plan 更新）
- [x] 独立 closure audit 已完成并记录

## Closure Gates

- [x] Phase 1 和 Phase 2 均标记为 `completed`
- [x] in-scope live API generator input 已从 `api.xlsx` 收口为 `api.xml`
- [x] 原始 `api.xlsx` 文件仍保留在仓库中
- [x] `docs-for-ai/`、`ai-dev/logs/`、本计划文件文本一致
- [x] 独立 closure audit 确认本计划可关闭
- [x] `./mvnw compile`（由 Phase 2 focused `./mvnw test -pl nop-rule,nop-wf -am -T 1C` 覆盖）
- [x] `./mvnw test -pl nop-rule,nop-wf -am -T 1C`
- [x] `./mvnw test -pl nop-ai/nop-ai-coder -am -T 1C -Dtest=<新增 focused 测试类>`
- [x] Anti-Hollow Check：closure audit 已验证 `nop-rule` / `nop-wf` 的 source `api.xml -> generated outputs` 调用链，以及 `nop-ai-coder` 的 `ai-api-design -> ai-service-design` XML 路径连通性
- [x] checkstyle / 代码规范检查通过（以 Maven 构建通过和最小 diff 约束为准）

## Deferred But Adjudicated

### Historical API XLSX Mentions Outside Owner Docs

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本次只收口 live generator source baseline 与最小 owner docs；历史 `docs/` / `docs-en/` / 参考技能中的 `api.xlsx` 文本不影响当前构建或 codegen 行为
- Successor Required: `no`
- Successor Path: `n/a`

## Non-Blocking Follow-ups

- 如需像 `orm.xml` 迁移一样进一步清理 `docs/` / `docs-en/` / 技能参考中的历史 `api.xlsx` 叙述，可另起文档治理计划。

## Closure

Status Note: focused proof、广义测试门禁、doc link check 与第二次独立 closure audit 均已完成，Plan 58 现可正式关闭。

Closure Audit Evidence:

- Reviewer / Agent: `general` subagent `ses_18bebfcbaffeAY8VsAwhr51lHU`
- Evidence: 首轮审计结论为 `not completed`；原因是当时 Phase 2 / Closure Gates 尚未收口且 `./mvnw test -pl nop-rule,nop-wf -am -T 1C` 未通过。2026-05-30 已补齐 API xmeta 类型解析修复后，`./mvnw test -pl nop-rule,nop-wf -am -T 1C -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false` 现已 `BUILD SUCCESS`，需再次独立审计确认 closure。
- Reviewer / Agent: `general` subagent `ses_18b4d90b1ffeBXdBfk4z37c2uS`
- Evidence: 第二次独立审计已确认代码与验证证据充分，剩余问题仅是计划文本未收口；现已同步勾选 Phase 2 / Closure Gates 中的 closure audit 与 Anti-Hollow 项，并将 `Plan Status` 收口为 `completed`。

Follow-up:

- None.
