# 1306-1 nop-ai-coder ai-gen.orm.xml 模板/任务引用裁定与清理

> Plan Status: completed
> Last Reviewed: 2026-08-01
> Source: `ai-dev/plans/2026-07-30-2130-arm-fix-p0-ma2-01.md` Deferred But Adjudicated 段（"nop-ai-coder template/task references to ai-gen.orm.xml"，Successor Required: yes，Successor Path: TBD）；`ai-dev/backlog/audit-remediation-roadmap.md` §P2/P3 Deferred Successors（第十二批后唯一未承接的 Successor Required: yes 项）
> Related: `2026-07-31-0000-1-arm-mr1-fix.md`（P1-MA1-019 仅修了 nop-ai-codegen 的 postcompile 引用，未触及 nop-ai-coder 模板）
> Mission: audit-remediation
> Work Item: P0-MA2-01 successor — nop-ai-coder ai-gen.orm.xml 模板/任务引用裁定

## Purpose

承接 2130 plan 登记的唯一未落地 successor：对 `nop-ai-coder/` 中 6 个引用 `ai-gen.orm.xml` / `ai-gen.action-auth.xml` 的 codegen 任务/模板文件做出**有证据的裁定**（是否应改为引用 `nop-ai.orm.xml`），并清理被该裁定暴露出的误导性 archive header 与 stale 空文件（0 字节 `ai-gen.orm.xlsx`）。裁定结论与证据落盘 roadmap / arm-index，关闭 2130 遗留 successor。

## Current Baseline

（全部 live 核验于 2026-08-01）

- **2130 遗留项**：`2026-07-30-2130-arm-fix-p0-ma2-01.md` Deferred But Adjudicated 登记"nop-ai-coder template/task references to `ai-gen.orm.xml`"（Classification: out-of-scope improvement；Successor Required: `yes`；Successor Path: TBD）；Non-Blocking Follow-ups 注明"Assess codegen templates in `nop-ai-coder/` (6 files) referencing `ai-gen.orm.xml` and update to use `nop-ai.orm.xml` if appropriate"。12 个批次 successor（1446-2/3、1834-1/2/3、2248-1/2/3、0206-1/2/3、0441-1、0746-1/2、0936-1/2/3、1223-1）均未承接该项。
- **6 个引用文件（live）**：
  - `nop-ai/nop-ai-coder/src/main/resources/_vfs/nop/ai/tasks/ai-api-design.task.xml:47` — `loadOrmModel` 读 `model/ai-gen.orm.xml`（`inputDir=${outputDir}`，即目标项目目录）
  - `ai-service-design.task.xml:33` — 同上
  - `ai-menu-design.task.xml:55,77,84` — 读 `model/ai-gen.orm.xml`、`model/ai-gen.action-auth.xml`；写 `${appName}-web/.../auth/ai-gen.action-auth.xml`
  - `ai-coder.task-lib.xml:25,32` — `saveOrmModel` **写入** `model/ai-gen.orm.xml`（AI 简化版）与 `${appName}-dao/.../orm/ai-gen.orm.xml`（完整版）到目标项目
  - `templates/ai-project/{appName}-dao/.../orm/app.orm.xml:2` — `x:extends="ai-gen.orm.xml"`
  - `templates/ai-project/{appName}-web/.../{appName}.action-auth.xml:2` — `x:extends="ai-gen.action-auth.xml"`
- **语义关键事实（live 追踪）**：coder 管线中 `ai-gen.orm.xml` 是**目标项目内生成的中间产物名**（`ai-gen` = "AI 为新项目设计的模型"）——`task-lib saveOrmModel` 先写入 outputDir，后续 design 任务再从 outputDir 读回，生成的 `app.orm.xml` 再 `x:extends` 它。这些引用**不指向** `nop-ai/model/ai-gen.orm.xml` 归档文件。
- **归档文件真实消费面（grep 全仓核验）**：`nop-ai/model/ai-gen.orm.xml`（archive header，15 个 `<entity>` 标签，header 注释自述 14 个）在仓库内唯一真消费方是 **@Disabled 测试** `AiGenCodeTaskManual.saveOrmModel()`（`AiGenCodeTaskManual.java:80` 读 `../model/ai-gen.orm.xml` 重生成 `nop-ai.orm.xlsx`）。coder 模板引用的只是**名字** `ai-gen.orm.xml`（管线生成物），非归档文件本身。
- **误导性 header**：归档文件 header 声称 "Retained in repo because nop-ai-coder codegen templates reference it for scaffolding new projects"——与上面语义事实不符（模板不读该文件，仅 @Disabled 测试读）。
- **stale 空文件**：`nop-ai/model/ai-gen.orm.xlsx` **0 字节**（git 跟踪中）——空占位。
- **其他引用**：`TestAiApiModel.java:19-20` 读的是 demo fixture `ai-gen.api.xml`（API 模型，非 orm，且为 demo 测试夹具，不在本 plan scope）；`nop-ai/nop-ai-coder/_dump/` 为 gitignore 产物；demo `_gen/ai-gen.orm.xml` + `ai-gen.action-auth.xml` 为管线的生成快照（git 跟踪，不改）；`docs/user-guide/ai-coder/ai-coder-task.md:189,202` 与 `docs-en/user-guide/ai-coder/ai-coder-task.md:188,201` 共 4 处命中描述管线语义（恰好支持"不改"裁定，属文档引用，非消费方）。
- **绿色基线**：`ai-dev/logs/2026/08-01.md` 全量 `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS；scan-hollow exit 0；check-doc-links exit 0（1223-1 时点）。

## Goals

- 对 6 个 coder 模板/任务文件的 `ai-gen.orm.xml` / `ai-gen.action-auth.xml` 引用做出**可复核裁定**（预期结论：不改为 `nop-ai.orm.xml`——管线语义自洽，证据见 Baseline），裁定证据落盘。
- 修正归档文件 `nop-ai/model/ai-gen.orm.xml` 的误导性 header（真实保留理由：仅 @Disabled 手动测试消费），或按裁定移除 stale 归档文件。
- 清理 0 字节 `nop-ai/model/ai-gen.orm.xlsx` 空文件。
- 处置 @Disabled 测试 `AiGenCodeTaskManual.saveOrmModel()`（其唯一输入源为被移除的归档 AI-result 格式模型）：裁定为**删除该方法**（转换逻辑由 `AiConverterTest.testConvertOrm` 覆盖且输出写 target 目录；该方法输出写 git 跟踪的 canonical `nop-ai.orm.xlsx`，保留即污染 footgun）——禁止把输入改指 `nop-ai.orm.xml`（canonical 格式喂给 `buildFromAiResult → fixNameForOrmNode → fixDictProp("ai/")` 的 AI-result 管线会改写列名/重置 precision，语义损坏）也禁止运行该测试。
- 更新 roadmap（登记 2130 successor 关闭）与 arm-index，写 daily log。

## Non-Goals

- 不重命名 coder 管线内部生成的 `ai-gen.orm.xml` 中间产物名（`saveOrmModel`/design 任务/模板三者间自洽约定，改名无收益且有破坏管线风险；仅在 Phase 1 裁定认为确需改名时才例外，且需单独 successor plan）。
- 不触碰 demo fixtures（`demo/.../_gen/ai-gen.orm.xml`、`ai-gen.api.xml`）与 `_dump/`。
- 不处理 `TestAiApiModel`（api 模型夹具，与 orm 归档无关）。
- 不删除 `nop-ai.orm.xml` / `nop-ai.orm.xlsx`（canonical 源）。

## Scope

### In Scope

- 6 个 coder 任务/模板文件的引用裁定（Decision）。
- `nop-ai/model/ai-gen.orm.xml` archive header 修正或文件移除（Fix）。
- `nop-ai/model/ai-gen.orm.xlsx`（0 字节）清理（Fix）。
- `AiGenCodeTaskManual.saveOrmModel()` 方法删除（Fix，@Disabled，见 Goals 裁定理由）。
- roadmap / arm-index / daily log 登记（Follow-up）。

### Out Of Scope

- 管线生成物改名、demo fixture、api 模型夹具、其他模块组错误码语言问题（1223-1 已另行登记）。

## Execution Plan

### Phase 1 — 引用语义裁定（Decision + Proof）

Status: completed
Targets: `nop-ai/nop-ai-coder/src/main/resources/_vfs/nop/ai/tasks/`（6 文件）、`nop-ai/model/ai-gen.orm.xml`

- Item Types: `Decision | Proof | Fix`

- [x] 逐一追踪 6 个引用的运行时解析路径（`inputDir/outputDir` 语义 + `ResourceHelper.resolveResourceInDir` + task-lib 写读顺序），确认全部指向目标项目内生成物而非 `nop-ai/model/` 归档文件
- [x] 全仓 grep 复核归档文件 `nop-ai/model/ai-gen.orm.xml` 的真实消费面（预期仅 @Disabled 测试；若发现新消费方，修正裁定并纳入清理面；grep 命中预分类：docs 4 处为文档引用非消费方，`_dump/`/`target/` 为 gitignore 产物）
- [x] 删除 `AiGenCodeTaskManual.saveOrmModel()` 方法后，同步清理该文件因此产生的未使用 import（预期 7 个：AiOrmConfig/AiOrmModel/XNode/XNodeParser/FileResource/OrmModel/ExcelReportHelper，以删除后编译警告为准），并确认 `runCodeGen`/`runCodeGenMock` 不受影响
- [x] 裁定并落盘：6 个 coder 文件 **不改为** `nop-ai.orm.xml`（理由：语义自洽——新项目不应 x:extends nop-ai 自身模型；`nop-ai.orm.xml` 是 nop-ai 模块源模型，与 AI 为新项目生成的中间模型是不同概念）
- [x] 裁定并落盘归档文件命运：移除 `nop-ai/model/ai-gen.orm.xml` + 0 字节 `ai-gen.orm.xlsx`，并删除 `AiGenCodeTaskManual.saveOrmModel()` 方法（唯一消费方；其输入是 AI-result 格式——dict 无 `ai/` 前缀的 15 实体简化模型，与 canonical `nop-ai.orm.xml`（21 实体、`ai/*` dict、precision/tagSet）格式不兼容，normalizer 会做实体/列名规范化，且其输出直接写 git 跟踪的 canonical `nop-ai.orm.xlsx`——禁止改指 canonical 源、禁止运行该测试；转换语义由 `AiConverterTest.testConvertOrm` 覆盖，输出写 target 目录），或保留归档但修正 header——二选一，证据齐备（默认路径：移除，消除归档层名字冲突与误导保留理由）

Exit Criteria:

- [x] 6 个引用的解析链追踪结果记录（哪个文件、哪一步、解析到哪个目录、消费方是谁）
- [x] 全仓 grep 输出展示归档文件消费面（0 个 main/生产引用 + 1 个 @Disabled 测试引用，或按实际）
- [x] 裁定结论以可复核证据形式写入 plan Closure Evidence 与 daily log（"不改 6 文件"的理由链完整）
- [x] 归档文件处置（移除或 header 修正）执行完毕，git 状态干净
- [x] `AiGenCodeTaskManual.saveOrmModel()` 方法已删除（移除路径下；源码中无残留对归档文件的引用）
- [x] **无静默跳过**：不存在空 catch / 空方法体 / placeholder 注释；若保留归档文件则 header 改写为真实理由
- [x] No owner-doc update required（本 Phase 无 live baseline 行为变更；档案性登记在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 验证（Proof）

Status: completed
Targets: `nop-ai` 模块组构建

- Item Types: `Proof`

- [x] `./mvnw compile -pl nop-ai -am -q` 通过（确认 6 个 XML 无 xdef 校验破坏、删除文件与删除方法无编译引用）
- [x] `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS（0 failures 0 errors；@Disabled 测试不在运行面，普通测试无回归；**禁止运行 AiGenCodeTaskManual 手工测试类**——其输出会覆写 git 跟踪的 canonical `nop-ai.orm.xlsx`）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] 最终 grep 复核（显式排除 `target/`、`_dump/`）：`ai-gen.orm.xml` 在生产 main/resources 中仅剩 6 个 coder 引用（裁定保留面），`nop-ai/model/` 下无 stale 归档残留

Exit Criteria:

- [x] 构建 + 测试全绿（命令输出记录）
- [x] scan-hollow / check-doc-links 双 exit 0
- [x] **端到端验证不适用（显式声明）**：本 plan 不改管线代码（6 个 XML 引用保持原样，仅裁定），无入口点→出口点行为变更，符合 Minimum Rules #22 的 N/A 情形
- [x] **接线验证不适用（显式声明）**：无新组件引入，6 个引用解析链在 Phase 1 已静态追踪验证连通，符合 Minimum Rules #23
- [x] No new test required: 本 plan 为裁定 + 清理，唯一代码改动是删除 @Disabled 手工测试方法（不参与构建运行面），转换语义已由既有 `AiConverterTest.testConvertOrm` 覆盖；不做新功能
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 登记收口（Follow-up）

Status: completed
Targets: `ai-dev/backlog/audit-remediation-roadmap.md`、`ai-dev/audits/arm-index.md`

- Item Types: `Follow-up`

- [x] roadmap §P2/P3 Deferred Successors 表新增一行登记本 plan 承接并关闭 2130 successor（含裁定摘要：6 文件不改、归档移除/修正）
- [x] arm-index P0-MA2-01 行追加 successor 关闭注记（指向本 plan）；**同步更新 arm-index:188 P0 可追溯性表的证据文本**（追加"归档文件已按 successor plan 移除/修正"或改写为 live 一致状态）
- [x] 2130 plan 的 Deferred 段状态无需改写（历史计划不回写），但在本 plan Closure 记录承接链

Exit Criteria:

- [x] roadmap 与 arm-index 文本与 live 状态一致（check-doc-links 覆盖链接有效性）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 裁定证据落盘（6 文件解析链 + 归档文件消费面 grep + 处置决定）
- [x] 6 个 coder 引用保持自洽（无断链、无指向不存在的文件）
- [x] stale 归档（`ai-gen.orm.xml` / 0 字节 `ai-gen.orm.xlsx`）已按裁定清理，或 header 已修正为真实理由
- [x] `AiGenCodeTaskManual.saveOrmModel()` 已按裁定处置（默认删除；禁止改指 canonical 源/禁止运行）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] roadmap / arm-index 登记完成（含 arm-index:188 证据文本同步）；No owner-doc update required（纯档案性登记，无行为契约变更）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）6 个引用的运行时解析链确实连通（无指向不存在的路径），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai -am` 通过
- [x] `./mvnw test -pl nop-ai -am -T 1C` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
- [x] check-doc-links / scan-hollow 通过

## Deferred But Adjudicated

（无——本 plan scope 内无延期项；管线生成物改名属明确 Non-Goal，若 Phase 1 裁定反转则作为 successor 登记）

## Non-Blocking Follow-ups

- 无（本 plan 为 2130 successor 的收口；若 Phase 1 发现新的归档文件消费方，转为 in-scope Fix 处理）

## Closure

Status Note: 全部 3 个 Phase 的 execution items 与 Exit Criteria 已勾选；6 个 coder 引用裁定"不改"证据链完整（解析链追踪 + 全仓 grep），归档文件与 @Disabled 测试方法已按裁定移除/删除，构建与测试全绿，roadmap / arm-index / daily log 登记完成，独立子 agent closure audit 通过。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，task id `ses_0442f33f4ffePVZSERoWHAfW4X`）
- Audit Session: `ses_0442f33f4ffePVZSERoWHAfW4X`（fresh session，非实现会话）
- Evidence:
  - **Phase 1 Exit Criteria**：
    - 6 文件解析链追踪 [PASS]：写侧 `ai-coder.task-lib.xml:25,32`（saveOrmModel 写 `model/ai-gen.orm.xml` + `${appName}-dao/.../orm/ai-gen.orm.xml` 到 outputDir）；读侧 `ai-api-design.task.xml:47` / `ai-service-design.task.xml:33` / `ai-menu-design.task.xml:55` 经 `ai-coder.xlib:209` `LoadOrmModel`（`ResourceHelper.resolveResourceInDir(inputDir=${outputDir}, "model/ai-gen.orm.xml")`）；模板 `app.orm.xml:2` / `{appName}.action-auth.xml:2` 相对生成项目目录 `x:extends` 命中 task-lib 写入的完整版与 `ai-menu-design` 写入的 `${appName}-web/.../auth/ai-gen.action-auth.xml`；管线顺序 `ai-coder.task.xml:24-91`（createProject → designOrm → designMenu → designApi → designService）写→读→extends 连通。全部解析到目标项目内生成物，**不指向** `nop-ai/model/` 归档文件。
    - 归档文件消费面 [PASS]：全仓 grep 唯一真消费方为 @Disabled `AiGenCodeTaskManual.saveOrmModel()`（原 :80）；docs 4 处（`docs/user-guide/ai-coder/ai-coder-task.md:189,202`、`docs-en/...:188,201`）为管线语义描述非消费方；demo `_gen/` 快照 git 跟踪保留；`_dump`/`target` gitignore 产物。
    - 方法删除 + import 清理 [PASS]：`AiGenCodeTaskManual.java` 删除 `saveOrmModel()` 与 7 个未使用 import（AiOrmConfig/AiOrmModel/XNode/XNodeParser/FileResource/OrmModel/ExcelReportHelper），`runCodeGen`:30 / `runCodeGenMock`:51 零变更；audit 复核 `git diff` 为纯删除。
    - 裁定"不改 6 文件" [PASS]：`nop-ai.orm.xml` 是 nop-ai 模块源模型，与 AI 为新项目生成的中间模型是不同概念，新项目不应 x:extends nop-ai 自身模型。
    - 归档处置 [PASS]：`git rm nop-ai/model/ai-gen.orm.xml` + 0 字节 `ai-gen.orm.xlsx`；`nop-ai/model/` 现仅存 canonical `nop-ai.orm.xml`/`nop-ai.orm.xlsx` + 文档。
  - **Phase 2 Exit Criteria**：
    - 构建 [PASS]：`./mvnw compile -pl nop-ai -am -q` exit 0（audit 复核）。
    - 测试 [PASS]：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS，3602 tests 0 failures 0 errors（surefire 汇总）；@Disabled 类未运行（`AiGenCodeTaskManual` 不在运行面）。
    - scan-hollow [PASS]：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` exit 0（0 findings 全 severity，audit 复核）。
    - check-doc-links [PASS]：`node ai-dev/tools/check-doc-links.mjs --strict` exit 0（plan 文件反引号代码路径引用对 active plan 降级为 warning，completed 后跳过）。
    - 最终 grep [PASS]：main/resources 中 `ai-gen.orm.xml` 仅剩 6 个 coder 引用（裁定保留面）；`nop-ai/model/` 无 stale 残留（audit 复核 `rg "ai-gen"` main/ 恰 6 文件 9 命中，test/ 仅 TestAiApiModel demo fixture）。
  - **Phase 3 Exit Criteria**：roadmap `audit-remediation-roadmap.md:267` 新增 1306-1 行 ✅（closed 2026-08-01）+ header v16 + 尾段叙事补 2130 收口；arm-index:52 行追加 successor 注记 + arm-index:188 证据文本改写为 live 一致（归档已移除）；2130 历史计划不回写（guide #20），承接链记录于本 Closure。
  - **Closure Gates**：roadmap / arm-index / logs 文本一致性 [PASS]；stale 归档清理 [PASS]；`saveOrmModel()` 处置 [PASS]；无 in-scope 项降级 [PASS]（Deferred 段为空、Non-Blocking Follow-ups 为空）；独立 closure audit [PASS]（本证据）；Anti-Hollow [PASS]（6 引用解析链运行时连通性静态追踪验证，写→读→extends 链完整；scan-hollow 0 findings；无空方法体/静默跳过）；`./mvnw compile -pl nop-ai -am` [PASS]；`./mvnw test -pl nop-ai -am -T 1C` [PASS]；check-plan-checklist --strict [PASS]（exit 0，见下）；check-doc-links / scan-hollow [PASS]。
  - Deferred 项分类检查：无（plan 无 deferred 项；管线生成物改名明确 Non-Goal，未降级任何 in-scope 项）。

Follow-up:

- no remaining plan-owned work。后续提交（git commit）遵循项目 Git Workflow 执行，非本 plan scope。

## Optional Sections

## Risks And Rollback

- 移除 `nop-ai/model/ai-gen.orm.xml` 的风险：@Disabled 测试 `AiGenCodeTaskManual.saveOrmModel()` 若被重新启用会读不到文件——本 plan **删除该方法本身**（其转换语义由 `AiConverterTest.testConvertOrm` 覆盖），彻底消除该风险；git 历史保留原文件，可随时回滚。
- **禁止把该测试改指 `nop-ai.orm.xml`**：AI-result 格式（dict 无前缀、15 实体简化模型）与 canonical 格式（21 实体、`ai/*` dict、precision/tagSet）不兼容，normalizer 会对实体/列名做规范化处理，且其输出写 git 跟踪的 canonical `nop-ai.orm.xlsx`——一旦运行即污染 canonical 源。
- 移除 0 字节 `ai-gen.orm.xlsx` 无风险（空文件，无内容可失）。
- 6 个 coder XML 引用零改动：不动即无回归面；若未来有人误以为这些引用指向归档文件，本 plan 的裁定证据（解析链追踪）即为防误读的持久记录。
