# 2505 nop-job cron 全站落地：锚点持久化 + 实体装配 + 保存/更新校验 + 前端字段出口

> Plan Status: completed
> Last Reviewed: 2026-10-23
> Source: 用户需求（2026-10-23「全站落地」）+ cron 轨道 C2/C3/C7 已交付事实（nop-chaos-flux 仓 cron 轨道 roadmap（跨仓文档））
> Related: 跨仓 cron 轨道（flux plans 535/536/537/542）；`ai-dev/plans/00-plan-authoring-and-execution-guide.md`

## Purpose

把 nop-entropy 侧 cron 能力从「引擎 + 程序化 `TriggerSpec`」收口为「标准实体可配置、可校验、可持久化、可端到端执行」：`repeatAnchor*` 三元组进入 `NopJobSchedule` ORM 模型与生成链，`TriggerSpecHelper` 完成实体→spec 装配，CRUD `save`/`update` 入口对 cron 表达式、锚点组合与时间窗口 fail-fast，管理页由生成链自动暴露新字段，owner 文档同步到 live baseline。

## Current Baseline

> **Outdated Note**：本节记录执行前基线（2026-10-23 上午）。执行后现状见下方「Post-execution state」；不得用「执行前基线」判断当前仓库。

执行前：

- C2/C3/C7 已交付并提交到 master（`cbfb66d53e`、`8ae1ac9499`）：`CronExpression` 支持 6/7 段方言（`L`/`W`/`#`/年段）；`TriggerSpec`/`ITriggerSpec` 已有 `repeatAnchorEvery`/`repeatAnchorUnit`/`repeatAnchorDate`；`AnchorRepeatTrigger` 已在触发链。
- **实体层无锚点列**：`nop-job/model/nop-job.orm.xml` 只有 `cronExpr`（propId=11）与范围三字段（`maxExecutionCount`=13、`minScheduleTime`=14、`maxScheduleTime`=15），没有 `repeatAnchor*`；`_gen/_NopJobSchedule.java` 无对应字段。
- **装配缺口**：`TriggerSpecHelper.toTriggerSpec` 未映射锚点三字段；数据库实体路径无法携带锚点配置（只有程序化 `TriggerSpec` 可达）。
- **校验缺口**：`NopJobScheduleBizModel` 没有 override `defaultPrepareSave`/`defaultPrepareUpdate`；非法 `cronExpr`/锚点组合/时间窗口只能等到调度构建期才由 `TriggerBuilder` 拒绝（planner 逐条吞异常 → 静默不触发）。
- 前端：nop-job 页面由 flux-web 从 xmeta/view 生成，`cronExpr` 是普通文本输入；flux 侧 `cron-editor` 的值为 `CronScheduleValue` 对象，nop-entropy 实体为平铺列，**无单列对象绑定通道**（详见 Non-Goals 与 Deferred）。

Post-execution state（执行后，含首轮独立 closure audit 后的修复）：

- 三列 + `job/repeat-anchor-unit` dict 已进入 model 与完整生成链（entity/xmeta/view/Input+OutputBean/i18n/dict/三方言 DDL）。
- `TriggerSpecHelper.toTriggerSpec` 已映射锚点三元组；实体→planner/fire/completion 全部经该唯一装配路径。
- `NopJobScheduleBizModel.defaultPrepareSave` + `defaultPrepareUpdate` 均做 fail-fast 校验；`TriggerBuilder` 增加 `minScheduleTime <= maxScheduleTime` 构建期校验（新错误码 `nop.err.job.trigger.invalid-range-config`）。
- 生成链：gen-orm 出 `_app.orm.xml`/entity/DDL/meta dict；`nop-job-meta/precompile` 出 xmeta；`nop-job-meta/postcompile/gen-crud-api` 出 Input/OutputBean；`nop-job-web/precompile` 出 view/page。

## Goals

- `NopJobSchedule` 实体持久化 `repeatAnchorEvery`/`repeatAnchorUnit`/`repeatAnchorDate` 三列，并进入 ORM → entity → xmeta → view → InputBean/OutputBean → i18n/dict → DDL 生成链。
- 实体 → `TriggerSpec` 装配包含锚点三元组；CRUD `save` 与 `update` 路径对 cron 语法、锚点组合、时间窗口组合做 fail-fast 校验（错误码化）。
- 生成的管理页表单自动暴露锚点字段；`cronExpr` 仍为表达式文本输入（见 Non-Goals）。
- focused tests 覆盖：实体列 → `TriggerSpec` → `AnchorRepeatTrigger` 端到端（含 `save → enableSchedule → nextFireTime` 的真实用户路径）；save/update 校验拒绝非法组合；生成产物存在性。
- `docs-for-ai/03-modules/nop-job.md` 与 `docs-for-ai/04-reference/source-anchors.md` 同步到 live baseline；`ai-dev/logs/` 记录证据。

## Non-Goals

- **不在本计划内**：把 flux `cron-editor` 控件嵌入 `NopJobSchedule` 生成表单。原因：控件值为 `CronScheduleValue` 对象，实体是平铺列；`nop-web-site` bundle 是跨仓预构建产物（flux → nop-chaos-next → nop-web-site），字符串适配需 flux 侧新增 valueMode 并重建 bundle。该工作按 `Deferred But Adjudicated` 显式移交跨仓 successor，不伪装成本计划内的完成项。
- 不新增 `timezone` 字段（`TriggerSpec` 无该字段，属后续阶段；当前语义仍为 JVM 默认时区）。
- 不实现 `@daily` 等别名（非 C2 范围）。
- 不改 `AnchorRepeatTrigger` / `CronExpression` 的既有语义（C2/C3 已收敛）。
- 不覆盖实体级 `saveEntity`/`updateEntity` 直调入口（平台 CrudBizModel 契约：这两个入口不经 prepare hook；调用方负责校验）。

## Scope

### In Scope

- `nop-job/model/nop-job.orm.xml`：三列 + `job/repeat-anchor-unit` dict（inline dict 源）。
- 生成链再生成产物（entity/xmeta/view/beans/dict/i18n/DDL），只提交生成物、不手改。
- `TriggerSpecHelper.toTriggerSpec` 锚点装配（day/week 等校验复用 `TriggerBuilder`，装配侧只搬运）。
- `NopJobScheduleBizModel` 的 `save`/`update` 期校验（`defaultPrepareSave` + `defaultPrepareUpdate`）；`TriggerBuilder` 时间窗口校验。
- dao focused test + service focused test（含 save→enable 端到端）。
- 文档 + 日志。

### Out Of Scope

- flux `cron-editor` valueMode / bundle 重建（successor）。
- 应用侧宿主装配代码（flux design §9 已给 worked example）。
- 浏览器 E2E（无 bundle 变更时旧页面行为不变；新字段由生成链覆盖，服务端 save→enable→nextFireTime 已由 service 测试覆盖）。

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
- [x] 触发再生成（执行环境为 warm local repo，实际命令：`./mvnw -o install -pl nop-job/nop-job-codegen -DskipTests` 生成 ORM/entity/DDL/meta dict，再 `./mvnw -o install -pl nop-job/nop-job-api,nop-job/nop-job-dao,nop-job/nop-job-meta,nop-job/nop-job-web -DskipTests` 生成 xmeta/view/beans/i18n；clean clone 的 canonical 命令为 `./mvnw install -pl nop-job -am -DskipTests`），确认生成物包含：`_app.orm.xml` 列、`_NopJobSchedule.java` getter/setter、`_NopJobSchedule.xmeta` prop、`_NopJobSchedule.view.xml` grid col + form layout、`NopJobScheduleInputBean/OutputBean` 字段、`nop-job/nop-job-meta/src/main/resources/_vfs/dict/job/repeat-anchor-unit.dict.yaml`、`nop-job/deploy/sql/*/_create_nop-job.sql` 列。
- [x] 确认没有手改 `_gen/`、`_*.xmeta`、`_*.view.xml`、`_app.orm.xml`（全部由 codegen 重生成）。

Exit Criteria:

- [x] 上述生成物在 live repo 中可见（逐文件 grep 证据：`_app.orm.xml` 200-205、`_NopJobSchedule.java` 197-198/355、xmeta 201-213、view grid col 145-151 + form layout 180-181/200-201、InputBean 407-438、dict yaml、三方言 DDL 46-48）。注：view 的 form 字段只在 `<layout>` 出现（控件由 xmeta 经生成链推导），grid 侧才是 `<col>`。
- [x] `./mvnw -o -pl nop-job/nop-job-dao,nop-job/nop-job-service compile -DskipTests` 通过；`./mvnw -o install -pl nop-job -DskipTests` 全模块 BUILD SUCCESS。
- [x] 生成链未引入非本计划的无关 diff（`git status` 只含 nop-job 生成物/源模型/本计划文档；仓库根另有执行前既存的 0 字节未跟踪文件 `_tmp_dump_ops.groovy`（2026-10-19），非本计划产物，未删除以免影响其他会话）。
- [x] **接线验证**：`_NopJobSchedule.xmeta` 中的新 prop 被生成的 view form/grid 消费（view grid col 145-151 + form layout 180-181/200-201）。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口提交时写入 10-23 日志）。

### Phase 2 - 实体装配与 save/update 校验

Status: completed
Targets: `nop-job/nop-job-dao/.../TriggerSpecHelper.java`、`nop-job/nop-job-core/.../TriggerBuilder.java`、`nop-job/nop-job-service/.../NopJobScheduleBizModel.java`、focused tests

- Item Types: `Fix`（实体路径无法携带锚点 = contract gap；update 路径绕过校验 = 首轮审计 Major）、`Feature`（save/update 校验）

- [x] `TriggerSpecHelper.toTriggerSpec` 映射 `repeatAnchorEvery/Unit/Date` 三字段。
- [x] `NopJobScheduleBizModel` override `defaultPrepareSave` 与 `defaultPrepareUpdate`：
  - `cronExpr` 非空时 `CronExpression.isValidExpression` 必须为 true，否则 NopException（错误码参数含 `cronExpr`）。
  - 锚点组合校验：复用 `TriggerBuilder.buildTrigger` 构建期校验（unit∈{day,week}、date `YYYY-MM-DD`、锚点仅限 cron、成对性）；非法 unit 在 API 层先被 dict 校验拒绝（`nop.err.biz.invalid-dict-option`），构成双层防线。
  - `TriggerBuilder` 增加时间窗口校验：`minScheduleTime`/`maxScheduleTime` 均为正时 `min <= max`，否则 `nop.err.job.trigger.invalid-range-config`（避免 `LimitTimeTrigger` 永不触发的静默失效）。
  - 校验失败不得落库（fail-fast，非静默忽略）。
- [x] dao focused test：`TestTriggerSpecAnchorWiring` 9 用例——实体三字段 → `TriggerSpec` → `TriggerBuilder.buildTrigger` → `nextScheduleTime` 序列（day/week 各一例），以及负例：非法 unit、锚点无 cron、every>0 缺 unit、every>0 缺 date、unit 存在但 every=0、min>max 时间窗口。
- [x] service focused test：`TestNopJobScheduleBizModel` 新增 11 用例（save 拒绝非法 cron/锚点日期/锚点无 cron/非法 unit dict/min>max；save 合法固定频率无 cron；save 合法锚点读回；update 拒绝非法 cron/锚点日期；update 合法锚点持久化；`save → enableSchedule → nextFireTime` 锚点对齐）。校验用例必须走 `scheduleBiz.save`/`update`（or `orm.runInSession` 包裹），**禁止**用 `saveEntityDirectly` helper（该 helper 绕过 prepare hook）。

Exit Criteria:

- [x] 新测试红绿证据（变异探针，恢复后均以 grep 断言位点存在）：①移除 `TriggerSpecHelper` 三行映射 → 当时 5 个 dao 用例 5/5 红；②移除 `defaultPrepareUpdate` 的 `validateTriggerConfig` 调用 → 2 个 update 拒绝用例 2/2 红；③移除 `TriggerBuilder` 的 `validateRangeConfig` 调用 → dao 窗口负例 1/1 红。
- [x] `./mvnw -o test -pl nop-job/nop-job-core,nop-job/nop-job-dao` → core 139/139、dao 98/98 全绿；`./mvnw -o test -pl nop-job/nop-job-service` → 60/60 全绿。
- [x] **端到端验证**：实体对象（含锚点三字段）→ `TriggerSpecHelper` → `TriggerBuilder` → 触发序列，day（每 2 天 01-05/07/09）与 week（每 2 周 01-05/19、02-02）序列断言通过（锚点过滤的确定性判别证据）；service 层 `save(DISABLED + 锚点) → enableSchedule → nextFireTime` 验证 persisted 实体在真实用户路径上接通锚点计算（路径接线证据；当前日期下奇偶断言不足以单独区分锚点生效，已在测试注释与复审记录中标注）。
- [x] **无静默跳过**：非法输入抛 `NopException`（`nop.err.job.trigger.parse-cron-expr-fail` / `...invalid-anchor-config` / `...invalid-range-config` / `nop.err.biz.invalid-dict-option`），无 catch 吞异常/空实现。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口提交时写入）。

### Phase 3 - 文档与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-job.md`、`docs-for-ai/04-reference/source-anchors.md`、`ai-dev/logs/2026/10-23.md`

- Item Types: `Fix`（owner doc 与 live baseline 不一致）、`Proof`

- [x] `docs-for-ai/03-modules/nop-job.md`：CRON 表达式改为 6/7 段 Quartz 兼容方言（`L`/`W`/`#`/可选年段）；补锚点三元组语义、save/update 期校验（含 min<=max）、DDL 列名与存量库迁移说明；明确「管理页 `cronExpr` 为文本输入，cron-editor 由宿主页面装配」。
- [x] `docs-for-ai/04-reference/source-anchors.md`：新增 `JOB-002`（cron 语法权威 + 实体→spec→trigger→anchor 过滤链 + save/update 期校验锚点）。
- [x] `ai-dev/logs/2026/10-23.md` 追加本计划证据段。

Exit Criteria:

- [x] 文档中描述的字段/语法与 live code 一致（6/7 段与 L/W/#、锚点三元组、save/update 校验、窗口校验、DDL 列名、时区口径逐条对照 `CronExpression`/`TriggerSpec`/`TriggerBuilder`/`NopJobScheduleBizModel`/DDL grep）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本 plan> --strict` 在 `Plan Status: completed` 后退出码 0（该工具对 active plan 仅告警不硬失败，故必须以 completed 状态复核）。
- [x] `ai-dev/logs/` 条目与 plan 状态一致（同收口提交）。

## Closure Gates

- [x] 所有 in-scope confirmed live gap（实体锚点列缺失、实体→spec 装配缺失、save/update 校验缺失、owner doc drift）已修复（初版 `49a0e928b3` + 审计修复 `736e880acf`）。
- [x] 三 Phase Exit Criteria 全部勾选。
- [x] focused tests 全绿（core 139/139、dao 98/98、service 60/60），并有红绿变异探针证据（映射移除 5/5 红、update hook 移除 2/2 红、窗口校验移除 1/1 红）。
- [x] 生成链产物完整性验证（entity/xmeta/view/beans/dict/DDL）——model → 各生成物逐字段一致。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect / contract drift；flux cron-editor 控件嵌入与 timezone 按 successor 显式裁定（首轮审计已确认 deferral 诚实、未隐藏 M1）。
- [x] 独立子 agent closure-audit 完成并记录证据（首轮 REJECTED：1 Major M1；修复后复审 APPROVED-WITH-MINORS，0 Major）。
- [x] Anti-Hollow Check：实体→spec→trigger 运行时调用链连通（`save/update → entity → TriggerSpecHelper → TriggerBuilder → AnchorRepeatTrigger` 逐段核对 + dao 序列测试）；无空方法体/静默跳过；`scan-hollow-implementations.mjs --module nop-job --severity high` 退出码 0。
- [x] `./mvnw compile`（受影响模块）通过（`./mvnw -o install -pl nop-job -DskipTests` BUILD SUCCESS）。
- [x] `./mvnw test`（受影响模块）通过（core 139/139、dao 98/98、service 60/60）。

## Deferred But Adjudicated

### flux cron-editor 嵌入 NopJobSchedule 生成表单（对象值绑定）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 控件值模型是 `CronScheduleValue` 对象，实体是平铺列；绑定需 flux 侧新增字符串适配（valueMode）并重建三仓 bundle。跨仓事实在本仓不可验证（`nop-web-site` bundle 来自 flux → nop-chaos-next 链路，nop-chaos-flux 仓 cron 轨道 C1-C7 已 done）。当前 nop-entropy 侧已有后端全链路与宿主装配契约（flux design §9），管理页以文本表达式 + 平铺锚点/范围字段可用，不构成 nop-entropy 数据/触发语义缺口。
- Successor Required: `yes`
- Successor Path: 建议在 nop-chaos-flux 仓新立 cron 轨道 C8（cron-editor string valueMode + bundle 升级 + nop-job view 控件接入 + e2e）。

### `timezone` 字段

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: C2/C3 既定语义为 JVM 默认时区；引入时区需要 TriggerSpec/引擎/实体三处同步设计与迁移，超出本轮 cron 全站落地范围。
- Successor Required: `yes`
- Successor Path: cron 轨道后续阶段（跨仓）。

## Non-Blocking Follow-ups

- `@daily` 等 cron 别名支持（原基线缺口，未纳入 C2 范围）。
- nop-job 管理页的 cron 表达式创作期 schemaValidator（当前仅保存/更新期校验）。
- 行为注记：保存/更新期校验复用 `TriggerBuilder`，因此 `pauseCalendarSpec` 的 JSON 解析错误从「调度计算期」前移到「保存/更新期」（fail-fast，非新增缺陷）。

## Closure

Status Note: 完成。首轮独立 closure audit 为 REJECTED（1 Major：save 校验未覆盖管理页 edit 走的 `update` 路径），已按审计修复（`736e880acf`：`defaultPrepareUpdate` 复用校验、`TriggerBuilder` 时间窗口校验、update/负例/端到端补测、baseline 与文档校正）；复审 APPROVED-WITH-MINORS（0 Major；剩余为测试强度注记与 DDL 大小写措辞，均已原地处理/注记）。

Completed: 2026-10-23

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（首轮，REJECTED）+ 独立复审子 agent（APPROVED-WITH-MINORS）；均为 fresh session，未复用实现会话。
- Audit Session: 首轮 `d6e40f9f-ec01-4cd3-ae41-6d21d7825b7a`；复审 session 为最终收口轮 fresh 子 agent，覆盖 HEAD `736e880acf`。
- Evidence:
  - M1 修复：`NopJobScheduleBizModel:246-258` 双 hook（save/update）均调 `validateTriggerConfig`；`CrudBizModel.update→doUpdate→invokeDefaultPrepareUpdate` 调用链已核对；update 拒绝/持久化用例走真实 `scheduleBiz.update`（非 `saveEntityDirectly`）。
  - 窗口校验：`TriggerBuilder.buildTrigger` 首行 `validateRangeConfig`，新错误码 `nop.err.job.trigger.invalid-range-config`；dao+service 各自窗口负例断言。
  - 测试计数：`./mvnw -o test -pl nop-job/nop-job-core,nop-job/nop-job-dao` → 139/139、98/98；`-pl nop-job/nop-job-service` → 60/60（`TestNopJobScheduleBizModel` 26）。
  - 红绿探针：移除锚点映射 → 5/5 红；移除 update hook 校验 → 2/2 红；移除窗口校验 → 1/1 红；恢复后全绿（grep 断言位点）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`scan-hollow-implementations.mjs --module nop-job --severity high` exit 0。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2505-nop-job-cron-anchor-fullstack.md --strict` → exit 0（status=completed 后复核，0 未勾选/0 结构问题）。
  - Anti-Hollow：`save/update → entity → TriggerSpecHelper → TriggerBuilder → AnchorRepeatTrigger.nextScheduleTime` 调用链逐段核对；无空方法体/静默 catch/TODO 实现。
  - Deferred 分类：flux cron-editor 嵌入与 `timezone` 均为 out-of-scope + successor；首轮/复审均确认未隐藏 in-scope defect（M1 未借 deferred 掩盖，已实修）。
  - 端到端：service 层 `save→enable→nextFireTime` 为路径接线证据；锚点过滤语义由 dao 确定性序列用例（day 01-05/07/09、week 01-05/19、02-02）证明。

Follow-up:

- 无剩余 plan-owned work。跨仓后续：flux cron-editor string valueMode + bundle 升级 + nop-job view 控件接入 + e2e（建议 cron 轨道 C8）；`timezone` 字段（TriggerSpec/引擎/实体同步设计）。
