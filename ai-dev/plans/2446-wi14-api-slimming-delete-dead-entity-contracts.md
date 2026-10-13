# 2446 WI14 nop-ai-api 瘦身（应用实体契约迁出——裁定删除）

> Plan Status: completed
> Last Reviewed: 2026-10-12
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI14；死代码普查 = 2026-10-12 live HEAD 全仓实测
> Draft Review: 2026-10-12 独立子代理对抗审查 round 1（agent_9b089783）——1 Blocker（生成链断言为假：gen-crud-api.xgen 活跃指向 api + postcompile 自动执行 + FORCE_OVERRIDE=强制回写非断开，删除必复活；须按 owner doc 先打 no-api 标签并以 nop-ai-meta 构建验证）+ 4 Major（须显式推翻 2026-09-15 在录裁定 / 验证门漏 5 消费者且误列 agent / 文档同步缺 nop-ai.md 与 api-model-and-codegen.md / roadmap 验证命令降级）+ 4 Minor，全部修入正文（A1 重写为生成面处置 + 裁定推翻、Phase 1 重写）后转 active
> Related: WI18（dao 双限界上下文裁定，未启动）——beans 与 dao 实体的协同条款；本裁定为"直接删除"（非搬迁），与 WI18 的 dao 实体归属无二次搬迁交集；WI13/plan 2445（completed）——extension-matrix 计数方法先例

## Purpose

删除 `nop-ai-api` 中的应用实体契约死岛：`api/beans/`（44 文件）+ `api/crud/`（22 文件）——66 个文件全仓零消费者（main/test/xml/yaml/xbiz 反射全查），使 api 回归纯 AI 能力契约 jar（chat/credential/exceptions/mcp/secure/tool，29 文件）。

## Current Baseline

> 2026-10-12 live HEAD 全仓实测。

- `api/beans/` = 44 文件（NopAiSession/NopAiChatRequest 等 Input/Output Bean，均带 `__XGEN_FORCE_OVERRIDE__` 头标记）；`api/crud/` = 22 文件（13 行接口，import `CrudInputBase` 与 beans）。
- **消费者普查 = 0**：①全仓 java `io.nop.ai.api.beans.|io.nop.ai.api.crud.` 引用排除 nop-ai-api 自身后零命中（含 nop-ai-service/web/gateway/agent/toolkit 全部下游）；②api 模块内部 chat/mcp/secure/tool/credential/exceptions 六包零引用；③类名反射面（xml/yaml/xbiz）零命中（NopAiSessionInputBean 等 3 个代表名抽查）；④包间关系封闭——crud 22 文件引用 beans，beans 不引用 crud，无外部扇入。
- **生成链（活跃，删除前置处置的对象）**：`nop-ai-meta/postcompile/gen-crud-api.xgen` 活跃指向 `../nop-ai-api/src/main/java/`（targetDir + apiPackageName=io.nop.ai.api），经 exec-maven-plugin postcompile execution（root pom pluginManagement 绑定 generate-test-resources 阶段）在每次 nop-ai-meta test/install 自动执行；22 个实体 xmeta（`nop-ai-meta/_vfs/nop/ai/model/NopAi*/`，与 22 个 crud API 一一对应）均未带 `no-api` 标签。`__XGEN_FORCE_OVERRIDE__` 语义 = 重新生成时强制回写（XCodeGenerator.java:46），**不是断开点**——66 文件删而不处置生成器必全量复活。owner doc `api-model-and-codegen.md` 删除前置明文："生成面删除需修改 codegen 模板/生成管线 + 实体级 no-api 标签——不允许只删单个模块的生成文件"。
- **在录裁定冲突（须显式推翻）**：`docs-for-ai/03-modules/nop-ai.md` 与 `api-model-and-codegen.md` 记录 2026-09-15 P2 round-4 裁定——66 个生成物"全仓零直接 import 属预期（客户端契约定位），裁定保留为生成面"。本 plan 推翻该裁定的理由：①2026-10-12 重测零消费者（含反射面/短形态/类名字符串五形态全查）；②roadmap WI14 立项即"瘦身" mandate 且用户 goal 明示执行至彻底完成；③契约 jar 携带 66 文件死重违背 api"纯能力契约"定位；④git 历史永久可回溯。
- 删除后 api 存量：chat 24 + credential 1 + exceptions 1 + mcp 1 + secure 1 + tool 1 = **29 文件**（符合 roadmap"约 25-30 文件"预期）。
- Protected Areas：`nop-ai-api` 公共 API 变更（删 66 个 public 类型）→ plan-first（本 plan）+ owner doc（module-groups api 行）+ 迁移说明（本 plan Closure：删除类清单 + 无消费者证据）。XGEN 生成面处置：删除死产物无需改模型/模板（模型在其他模块且生成目标已不指向 api）——以"install 后零复活"实证闭环。

## Goals

- `api/beans/` 与 `api/crud/` 目录删除（66 文件）；全仓零编译破裂、零 resurrection。
- api 模块文件数 95→29（登记 before/after）；api 回归纯 AI 能力契约 jar。
- 全部 nop-ai 下游模块（toolkit/agent/core/gateway/tools/mcp 等）编译与测试不回归。

## Non-Goals

- 不动 dao/service 侧实体（WI18 scope）。
- 不改 api 存留的 29 文件（chat/credential/exceptions/mcp/secure/tool）。
- 不新增替代契约（死代码无消费者即无迁移需求）。

## Adjudications

### A1 处置 = 生成面处置 + 直接删除（推翻 2026-09-15 在录裁定）

- **显式推翻 2026-09-15 P2 round-4 "保留为生成面"裁定**，理由见 Current Baseline 在录裁定冲突段（零消费者重测 + WI14 mandate + 契约 jar 定位 + git 可回溯）。删除序列按 owner doc 删除前置：
  1. 22 个实体 xmeta（`nop-ai-meta/_vfs/nop/ai/model/NopAi*/⟨Name⟩.xmeta`）根元素加 `tagSet="no-api"`（先例：nop-rule/NopRuleDefinition.xmeta:3）——gen-crud-api.xgen 跳过全部实体，生成面清零；
  2. `git rm` 66 文件；
  3. 构建 nop-ai-meta（触发 postcompile 生成器）→ 复查 api 目录零复活（真实验证，替代原 vacuous 的 api-only install）。
- 三选项裁决：搬迁/独立 jar 均以存在消费者为前提——实测零消费者；删除 + git 可回溯为唯一诚实选项。WI18 协同：api/beans（NopAiSessionInputBean 等）与 dao 实体（nop-ai-dao entity/NopAiSession.java 等）为不同模块/类名/文件集，无二次搬迁交集。

## Execution Plan

### Phase 1 - 删除与 resurrection 验证

Status: completed
Targets: `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/{beans,crud}/`

- Item Types: `Fix`

- [x] 22 个实体 xmeta 根元素加 `tagSet="no-api"`（nop-ai-meta/_vfs/nop/ai/model/NopAi*/）
- [x] `git rm -r` 两目录（66 文件）
- [x] **resurrection 真实验证**：`./mvnw install -pl nop-ai/nop-ai-meta -DskipTests`（触发 postcompile gen-crud-api.xgen）→ 复查 `nop-ai-api/src/main/java/io/nop/ai/api/` 零复活
- [x] 下游编译门（8 个直接 compile 消费者）：`./mvnw test-compile -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-core,nop-ai/nop-ai-gateway,nop-ai/nop-ai-service,nop-ai/nop-spring-mcp-server-support,nop-datav/nop-datav-service,nop-wf/nop-wf-ai,nop-code/nop-code-service -o -q`（注：nop-ai-agent 无 api 直接依赖，不在门内）
- [x] roadmap 验证命令：`./mvnw test -pl nop-ai/nop-ai-api,nop-ai/nop-ai-toolkit -am` 全绿
- [x] `./mvnw test -pl nop-ai/nop-ai-api -o` api 自身测试全绿

Exit Criteria:

- [x] api 目录 = 6 包 29 文件（find 计数）；beans/crud 零复活
- [x] 下游四模块 test-compile 0 错误；api 测试绿
- [x] No new test required：死代码删除，无新行为（guide Rule #25 注记）
- [x] owner-doc 更新落 Phase 2（module-groups api 行）
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - 文档同步与收口

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`、roadmap、log、plan closure

- Item Types: `Decision` + `Proof`

- [x] module-groups nop-ai-api 行更新（纯能力契约 jar，29 文件：chat/mcp/credential/secure/exceptions/tool；beans/crud 实体契约已删——WI14/plan 2446）
- [x] `docs-for-ai/03-modules/nop-ai.md` 66 文件生成面段落改写（删除 + 裁定推翻记录）
- [x] `docs-for-ai/02-core-guides/api-model-and-codegen.md` 逐模块登记行更新 + gen-crud-api.xgen 现状注记（22 实体 no-api 后该生成器空转，保留脚本作为再启用开关）
- [x] 迁移说明（Protected Areas）：删除类清单 = git 历史可查（本 plan Closure 记 66 文件/零消费者证据指针）；如存在外部使用方，按 git 历史恢复
- [x] roadmap `## Work Item Status` WI14 → `done`；状态块解析核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2446-wi14-api-slimming-delete-dead-entity-contracts.md --strict` 0
- [x] pathspec 限定单一原子提交

Exit Criteria:

- [x] roadmap 更新后解析核对通过（状态块 21 行 `- WI` 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：删除摘要、验证、A1、commit hash、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] 66 文件删除且零复活（nop-ai-meta 构建触发生成器后复查）
- [x] api = 6 包 29 文件；全仓无编译破裂（下游 8 消费者编译门）
- [x] api 测试全绿
- [x] 不存在被静默降级的 in-scope live defect（A1 为显式裁定，WI18 协同条款已满足）
- [x] owner docs 已同步：module-groups api 行
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] scan-hollow-implementations：N/A（纯删除型 plan，无新实现面）
- [x] `./mvnw test -pl nop-ai/nop-ai-api -o` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2446-wi14-api-slimming-delete-dead-entity-contracts.md --strict` 退出码 0

## Deferred But Adjudicated

### WI18 dao 双限界上下文裁定

- Classification: `watch-only residual`（ WI14 无涉——api/beans 与 dao 实体为不同文件集）
- Why Not Blocking Closure: A1 已证无二次搬迁交集
- Successor Required: `no`（WI18 独立执行）

## Non-Blocking Follow-ups

- `ai-dev/analysis/2026-10/coverage-baseline-wi*.json` 含 25 个被删类名（历史覆盖率基线，无工具消费）——漂移已登记，随下轮覆盖率刷新自然收敛。

## Closure

Status Note: 66 文件（beans 44 + crud 22）删除 + 22 实体 xmeta tagSet=no-api 生成面处置；推翻 2026-09-15 保留裁定的四要素理由在录；零复活经 nop-ai-meta 构建（生成器触发）实证；api 95→29 文件回归纯能力契约 jar；下游 8 消费者编译门 + roadmap 命令 268 测试绿；三处 owner doc 同步。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（agent_d087265d，fresh session，2026-10-13）
- Evidence:
  - 9/9 项 PASS（含两项现场重跑）：零复活（install nop-ai-meta 触发生成器后 api 29 文件 + git status 无新增）；下游 8 消费者 test-compile exit 0；api 测试 exit 0；22/22 xmeta tagSet 抽查读回 + xmllint 0 错误；在录裁定推翻记录三处一致（四要素核对）
  - 计数全对：66 = 44+22（git D）/29（工作区）/95（HEAD）；状态块 21 行
  - 收尾必办（审计 (a)-(d)）：pathspec 原子提交 + hash 回填、Phase 2 勾选、本证据段写入、Status completed 后复跑 checklist——均于本收尾序列完成
  - 附注登记：plan 编号撞号（2446×2 等，并行会话）留 WI21 收口处置
  - 裁定：**APPROVE closure**
- `node ai-dev/tools/check-plan-checklist.mjs 2446-wi14-api-slimming-delete-dead-entity-contracts.md --strict` 退出码 0（closure 前末次确认）
