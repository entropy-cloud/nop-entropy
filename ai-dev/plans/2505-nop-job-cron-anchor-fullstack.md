# 2505 nop-job cron 全站落地：锚点持久化 + 实体装配 + 保存校验 + 前端字段出口

> Plan Status: active
> Last Reviewed: 2026-10-23
> Source: 用户需求（2026-10-23「全站落地」）+ cron 轨道 C2/C3/C7 已交付事实（nop-chaos-flux 仓 cron 轨道 roadmap（跨仓文档））
> Related: 跨仓 cron 轨道（flux plans 535/536/537/542）；`ai-dev/plans/00-plan-authoring-and-execution-guide.md`

## Purpose

把 nop-entropy 侧 cron 能力从「引擎 + 程序化 `TriggerSpec`」收口为「标准实体可配置、可校验、可持久化、可端到端执行」：`repeatAnchor*` 三元组进入 `NopJobSchedule` ORM 模型与生成链，`TriggerSpecHelper` 完成实体→spec 装配，保存入口对 cron 表达式与锚点配置 fail-fast，管理页由生成链自动暴露新字段，owner 文档同步到 live baseline。

## Current Baseline

- C2/C3/C7 已交付并提交到 master（`cbfb66d53e`、`8ae1ac9499`）：`CronExpression` 支持 6/7 段方言（`L`/`W`/`#`/年段）；`TriggerSpec`/`ITriggerSpec` 已有 `repeatAnchorEvery`/`repeatAnchorUnit`/`repeatAnchorDate`；`AnchorRepeatTrigger` 已在触发链。
- **实体层无锚点列**：`nop-job/model/nop-job.orm.xml:117-127` 只有 `cronExpr`（propId=11）与范围三字段（`maxExecutionCount`=13、`minScheduleTime`=14、`maxScheduleTime`=15），没有 `repeatAnchor*`；`nop-job/nop-job-dao/.../_gen/_NopJobSchedule.java` 无对应字段。
- **装配缺口**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/helper/TriggerSpecHelper.java:23-37` 未映射锚点三字段；数据库实体路径无法携带锚点配置（只有程序化 `TriggerSpec` 可达）。
- **保存校验缺口**：`NopJobScheduleBizModel`（`nop-job-service`）没有 override `defaultPrepareSave`；非法 `cronExpr` 与非法锚点组合只能等到调度构建期才由 `TriggerBuilder` 拒绝。
- 生成链：`nop-job-codegen/postcompile/gen-orm.xgen` → dao entity / `_app.orm.xml` / api beans / meta xmeta / web view（`model-first-development.md`「真实链路」）；`nop-job-meta/precompile/gen-meta.xgen` 出 xmeta，`nop-job-web/precompile/gen-page.xgen` 出 view/page，`nop-job-meta/postcompile/gen-crud-api.xgen` 出 Input/OutputBean。
- 前端：nop-job 页面由 flux-web 从 xmeta/view 生成，`cronExpr` 当前是普通文本输入；flux 侧 `cron-editor` 的值为 `CronScheduleValue` 对象（`CronScheduleValue {cron, repeat, range}`），nop-entropy 实体为平铺列，**无单列对象绑定通道**（详见 Non-Goals 与 Residual）。

## Goals

- `NopJobSchedule` 实体持久化 `repeatAnchorEvery`/`repeatAnchorUnit`/`repeatAnchorDate` 三列，并进入 ORM → entity → xmeta → view → InputBean/OutputBean → i18n/dict → DDL 生成链。
- 实体 → `TriggerSpec` 装配包含锚点三元组；保存路径对 cron 语法、锚点组合、范围组合做 fail-fast 校验（错误码化）。
- 生成的管理页表单自动暴露锚点字段；`cronExpr` 仍为表达式文本输入（见 Non-Goals）。
- focused tests 覆盖：实体列 → `TriggerSpec` → `AnchorRepeatTrigger` 端到端；保存校验拒绝非法组合；生成产物存在性。
- `docs-for-ai/03-modules/nop-job.md` 与 `docs-for-ai/04-reference/source-anchors.md` 同步到 live baseline；`ai-dev/logs/` 记录证据。

## Non-Goals

- **不在本计划内**：把 flux `cron-editor` 控件嵌入 `NopJobSchedule` 生成表单。原因：控件值为 `CronScheduleValue` 对象，实体是平铺列；现有 `nop-web-site` 预构建 bundle 的 `cron-editor` 只接受对象值，字符串适配需 flux 侧新增 valueMode 并跨仓重建 bundle（三仓链路 flux → nop-chaos-next → nop-web-site）。该工作已作为显式 successor 记录在 `Deferred But Adjudicated`，不伪装成本计划内的完成项。
- 不新增 `timezone` 字段（`TriggerSpec` 无该字段，属后续阶段；当前语义仍为 JVM 默认时区）。
- 不实现 `@daily` 等别名（非 C2 范围）。
- 不改 `AnchorRepeatTrigger` / `CronExpression` 的既有语义（C2/C3 已收敛）。

## Scope

### In Scope

- `nop-job/model/nop-job.orm.xml`：三列 + `job/repeat-anchor-unit` dict（inline dict 源）。
- 生成链再生成产物（entity/xmeta/view/beans/dict/i18n/DDL），只提交生成物、不手改。
- `TriggerSpecHelper.toTriggerSpec` 锚点装配（含 day/week 校验已在 `TriggerBuilder`，装配侧只搬运）。
- `NopJobScheduleBizModel` 保存期校验（`defaultPrepareSave`），错误码进 `JobApiErrors` 或复用 `nop.err.job.trigger.invalid-anchor-config`。
- dao focused test + service focused test。
- 文档 + 日志。

### Out Of Scope

- flux `cron-editor` valueMode / bundle 重建（successor）。
- 应用侧宿主装配代码（flux design §9 已给 worked example）。
- E2E 浏览器用例（无 bundle 变更时旧页面行为不变；新字段由生成链覆盖，页面生成正确性由生成产物存在性 + 管理页字段检查证明）。

## Execution Plan

### Phase 1 - ORM 模型与生成链

Status: completed
Targets: `nop-job/model/nop-job.orm.xml`、codegen 产物（dao/meta/web/api/deploy sql）

- Item Types: `Feature`

- [x] `nop-job/model/nop-job.orm.xml` 的 `NopJobSchedule` 增加三列（propId 44/45/46）：
  - `repeatAnchorEvery`（INTEGER，默认 0；>0 时启用锚点）
  - `repeatAnchorUnit`（VARCHAR(10)，dict `job/repeat-anchor-unit`：day/week）
  - `repeatAnchorDate`（VARCHAR(10)，YYYY-MM-DD；与引擎 `TriggerSpec.repeatAnchorDate` 字符串契约一致）
  并把 `job/repeat-anchor-unit` inline dict 加入 `<dicts>`。
- [x] 触发再生成（实际命令：`./mvnw -o install -pl nop-job/nop-job-codegen -DskipTests` 生成 ORM/entity/DDL，再 `./mvnw -o install -pl nop-job/nop-job-api,nop-job/nop-job-dao,nop-job/nop-job-meta,nop-job/nop-job-web -DskipTests` 生成 xmeta/view/beans/i18n），确认生成物包含：`_app.orm.xml` 列、`_NopJobSchedule.java` getter/setter、`_NopJobSchedule.xmeta` prop、`_NopJobSchedule.view.xml` form 字段、`NopJobScheduleInputBean/OutputBean` 字段、`nop-job/nop-job-meta/src/main/resources/_vfs/dict/job/repeat-anchor-unit.dict.yaml`、`nop-job/deploy/sql/*/_create_nop-job.sql` 列。
- [x] 确认没有手改 `_gen/`、`_*.xmeta`、`_*.view.xml`、`_app.orm.xml`（全部由 codegen 重生成）。

Exit Criteria:

- [x] 上述生成物在 live repo 中可见（逐文件 grep 证据：`_app.orm.xml` 200-205、`_NopJobSchedule.java` 197-198/355、xmeta 201-213、view 145-151/180-181/200-201、InputBean 407-438、dict yaml、三方言 DDL 46-48）。
- [x] `./mvnw -o -pl nop-job/nop-job-dao,nop-job/nop-job-service compile -DskipTests` 通过；`./mvnw -o install -pl nop-job -DskipTests` 全模块 BUILD SUCCESS。
- [x] 生成链未引入非本计划的无关 diff（`git status` 只含 nop-job 生成物/源模型/本计划文档；另有并发会话的无关提交已隔离）。
- [x] **接线验证**：`_NopJobSchedule.xmeta` 中的新 prop 被生成的 view form 消费（`_gen/_NopJobSchedule.view.xml:145-151` 三 cell + form layout 180-181/200-201）。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口提交时写入 10-23 日志）。

### Phase 2 - 实体装配与保存校验

Status: completed
Targets: `nop-job/nop-job-dao/.../TriggerSpecHelper.java`、`nop-job/nop-job-service/.../NopJobScheduleBizModel.java`、`nop-job-api` 错误码、focused tests

- Item Types: `Fix`（实体路径无法携带锚点 = contract gap）、`Feature`（保存校验）

- [x] `TriggerSpecHelper.toTriggerSpec` 映射 `repeatAnchorEvery/Unit/Date` 三字段。
- [x] `NopJobScheduleBizModel` override `defaultPrepareSave`：
  - `cronExpr` 非空时 `CronExpression.isValidExpression` 必须为 true，否则 NopException（错误码参数含 `cronExpr`）。
  - 锚点组合校验：复用 `TriggerBuilder.buildTrigger` 构建期校验（unit∈{day,week}、date `YYYY-MM-DD`、锚点仅限 cron、成对性）；非法 unit 在 API 层先被 dict 校验拒绝（`nop.err.biz.invalid-dict-option`），构成双层防线。
  - [x] 校验失败不得落库（fail-fast，非静默忽略）。
- [x] dao focused test：新增 `TestTriggerSpecAnchorWiring`（5 用例）——实体三字段 → `TriggerSpec` → `TriggerBuilder.buildTrigger` → `nextScheduleTime` 序列与黄金向量 repeat 节一致（至少 day/week 各一例 + 非法组合拒绝）。
- [x] service focused test：`TestNopJobScheduleBizModel` 增加 4 用例（非法 cron 拒绝、非法锚点日期拒绝、锚点无 cron 拒绝、合法锚点保存并读回三列）。

Exit Criteria:

- [x] 新测试红绿证据（变异探针）：移除 `TriggerSpecHelper` 三行映射 → dao 用例 5/5 红（恢复后 5/5 绿，grep 确认 3 处映射行已恢复）；移除 `defaultPrepareSave` 中 `validateTriggerConfig` 调用 → service 3 个拒绝用例 3/3 红（恢复后全绿）。
- [x] `./mvnw -o test -pl nop-job/nop-job-dao` 94/94 全绿；`./mvnw -o test -pl nop-job/nop-job-service` 53/53 全绿。
- [x] **端到端验证**：实体对象（含锚点三字段）→ `TriggerSpecHelper` → `TriggerBuilder` → 触发序列，day（每 2 天 01-05/07/09）与 week（每 2 周 01-05/19、02-02）序列断言通过；非法锚点/无 cron 锚点从实体路径被拒绝。
- [x] **无静默跳过**：非法输入抛 `NopException`（`nop.err.job.trigger.parse-cron-expr-fail` / `nop.err.job.trigger.invalid-anchor-config`），无 catch 吞异常/空实现。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口提交时写入）。

### Phase 3 - 文档与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-job.md`、`docs-for-ai/04-reference/source-anchors.md`、`ai-dev/logs/2026/10-23.md`

- Item Types: `Fix`（owner doc 与 live baseline 不一致）、`Proof`

- [x] `docs-for-ai/03-modules/nop-job.md`：CRON 表达式改为 6/7 段 Quartz 兼容方言（`L`/`W`/`#`/可选年段）；TriggerSpec 通用字段补 `repeatAnchor*` 三元组语义；明确「管理页 `cronExpr` 为文本输入，cron-editor 由宿主页面装配」。
- [x] `docs-for-ai/04-reference/source-anchors.md`：新增 `JOB-002`（cron 语法权威 + 实体→spec→trigger→anchor 过滤链 + 保存期校验锚点）。
- [x] `ai-dev/logs/2026/10-23.md` 追加本计划证据段。

Exit Criteria:

- [x] 文档中描述的字段/语法与 live code 一致（6/7 段与 L/W/#、锚点三元组、保存校验、时区口径逐条对照 `CronExpression`/`TriggerSpec`/`TriggerBuilder`/`NopJobScheduleBizModel` grep）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` 退出码 0（收口时）。
- [ ] `ai-dev/logs/` 条目与 plan 状态一致。

## Closure Gates

- [ ] 所有 in-scope confirmed live gap（实体锚点列缺失、实体→spec 装配缺失、保存校验缺失、owner doc drift）已修复。
- [ ] 三 Phase Exit Criteria 全部勾选。
- [ ] focused tests 全绿（dao + service），并有 Proof 先行/红绿证据。
- [ ] 生成链产物完整性验证（entity/xmeta/view/beans/dict/DDL）。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect / contract drift；UI 控件嵌入按 successor 显式裁定。
- [ ] 独立子 agent closure-audit 完成并记录证据。
- [ ] Anti-Hollow Check：实体→spec→trigger 运行时调用链连通（测试断言锚点实际过滤）；无空方法体/静默跳过。
- [ ] `./mvnw compile`（受影响模块）通过。
- [ ] `./mvnw test`（受影响模块）通过。

## Deferred But Adjudicated

### flux cron-editor 嵌入 NopJobSchedule 生成表单（对象值绑定）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 控件值模型是 `CronScheduleValue` 对象，实体是平铺列；绑定需 flux 侧新增字符串适配（valueMode）并重建三仓 bundle。当前 nop-entropy 侧已有后端全链路与宿主装配契约（flux design §9），管理页以文本表达式 + 平铺锚点/范围字段可用，不构成 nop-entropy 数据/触发语义缺口。
- Successor Required: `yes`
- Successor Path: 建议在 nop-chaos-flux 仓新立 cron 轨道 C8（cron-editor string valueMode + bundle 升级 + nop-job view 控件接入 + e2e）。

### `timezone` 字段

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: C2/C3 既定语义为 JVM 默认时区；引入时区需要 TriggerSpec/引擎/实体三处同步设计与迁移，超出本轮 cron 全站落地范围。
- Successor Required: `yes`
- Successor Path: cron 轨道后续阶段（跨仓）。

## Non-Blocking Follow-ups

- `@daily` 等 cron 别名支持（原基线缺口，未纳入 C2 范围）。
- nop-job 管理页的 cron 表达式创作期 schemaValidator（当前仅保存期校验）。

## Closure

Status Note: 执行中
Completed: -
