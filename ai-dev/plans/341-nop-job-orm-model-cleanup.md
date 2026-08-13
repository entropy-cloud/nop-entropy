# 341 nop-job ORM 模型清理（死列 + 覆盖索引 + 字典描述入源）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Source: `ai-dev/plans/340-nop-job-dao-audit-remediation.md`（completed）Deferred But Adjudicated「ORM 模型结构清理（死列 + 索引）」+ Closure Follow-up；Protected Area `model/*.orm.xml` 变更（plan-first，证据要求 owner doc + test）
> Related: `ai-dev/plans/340-*.md`（completed）、`ai-dev/plans/339-nop-job-dispatch-routing-orthogonalization.md`（completed）
> Review: 两轮对抗审查通过（R1 ses_006d59690ffe2mJ2pwEaszrfqi：1 Blocker + 3 Major + 5 Minor 全部修复；R2 ses_006c69300ffeTOaxyK17M9KVjI：PASS，2 cosmetic 观察项不阻塞）

## Purpose

把 plan 340 裁定的 ORM 层清理收口到 live baseline：从 `nop-job/model/nop-job.orm.xml` 源模型删除 6 个无生产读写方的死列 + 1 个仅服务死列的索引，新增 2 个 cursor 扫描覆盖索引；把 executor-kind 字典的 option description 迁入模型源（根治 codegen 覆盖问题）；同步清理所有手工维护的保留层对死列的引用（view 布局、orm.java、owner docs、设计文档、E2E spec），并移除随之失效的 `IJobFireStore.updateRetryRecordId` 桥接口。不动任何 alive 列，不重排 propId，不改 H2 测试环境。

## Current Baseline

### 已成立的事实（live repo 核对 2026-08-13）

- **死列证据（无生产读写方）**：
  - fire `taskCostCpu`(28)/`taskCostMemory`(29)：快照列，生产代码无人写；dispatch 落库到 task 表的 `costCpu/Memory` 来自 **schedule**（`JobDispatcherScannerImpl.java:149-152`、`AdaptiveJobTaskBuilder.java:113-114`），worker 侧 reserved 聚合读 task 表（`JobTaskStoreImpl.sumReservedCost:147-156`）。
  - fire `retryRecordId`(18)：`IJobFireStore.updateRetryRecordId`（`IJobFireStore.java:63`、`JobFireStoreImpl.java:274-280`）**无生产调用方**；`NopRetryJobRetryBridge` 是异步 fire-and-forget（`callAsync(...).whenComplete(日志)`，`NopRetryJobRetryBridge.java:54-62`），从不回填；3 个测试 mock 仅因接口存在而空实现（`TestJobTimeoutChecker.java:1146`、`TestJobE2E.java:276`、`TestJobCompletionProcessor.java:495`）；`nop-job/model/nop-job.orm.java:118` 注释自证「当前 NopRetryJobRetryBridge 使用异步提交，此字段暂不可用」。
  - task `workerAddress`(6)：主代码零读写（cancel 读取 `targetHost` 而非 workerAddress，`TestDefaultJobCancelHandler.java:313` 注释确认）；保留层 view 引用（`NopJobTask.view.xml:22`）。
  - task `progress`(21)/`progressMessage`(22)：Java 主代码零引用；唯一"使用者"是 E2E `fault-tolerance.spec.ts:133-189` Phase 4（CRUD 盲持久化 roundtrip，断言被 `if (updateResp.ok)` 守卫——字段消失后 update 失败即静默跳过，测试空转但仍然绿）；`docs-for-ai/03-modules/nop-job.md:355` 关键字段列表含 `progress`。
  - 索引 `IX_NOP_JOB_FIRE_RETRY`（orm.xml:342-344）仅服务 retryRecordId。
- **覆盖索引缺口**（plan 340 P2-1 修复后的真实扫描排序）：
  - `fetchRunningTasks`（`JobTaskStoreImpl.java:102-122`）：filter `taskStatus IN RUNNING_LIKE_STATUSES` + partition，order `startTime DESC, jobTaskId DESC`；现存 `IX_NOP_JOB_TASK_RUN_SCAN (taskStatus, partitionIndex, createTime)` 不覆盖此排序 → filesort。
  - `fetchDispatchingFires`（`JobFireStoreImpl.java:237-258`）：filter `fireStatus=DISPATCHING` + partition，order `startTime DESC, jobFireId DESC`；现存 `IX_NOP_JOB_FIRE_DISPATCH_SCAN (fireStatus, partitionIndex, scheduledFireTime)` 不覆盖此排序 → filesort。
  - 其余扫描索引已覆盖既有查询（resetStaleWaitingTasks→RUN_SCAN、fetchWaitingFires→DISPATCH_SCAN、findTasksByFireId→IX_NOP_JOB_TASK_FIRE、countInFlightTasks/sumReservedCost→IX_NOP_JOB_TASK_STATUS_WORKER、planner→IX_NOP_JOB_SCHEDULE_SCAN），不在本 plan 范围。
- **字典描述漂移根因**：`nop-job/nop-job-meta/src/main/resources/_vfs/dict/job/executor-kind.dict.yaml` 是 codegen 生成物（`__XGEN_FORCE_OVERRIDE__`，模板 `templates/orm/{appName}-meta/.../dict/{dict.name}.dict.yaml.xgen`，由 `nop-job-codegen/postcompile/gen-orm.xgen` 第 1 步 renderModel 触发）；plan 339 手工补在生成物上的 rpcBroadcast description 已被 codegen 覆盖过一次（commit 706c936f2 又手工恢复）。**正确位置是 orm.xml `<dict>` option 的 `description` 属性**（`nop-wf.orm.xml:16-43`、`nop-batch.orm.xml:10-15` 已有先例，生成物 `description:` 即来自该属性）。
- **生成物清单（只靠 codegen 重生成，不手改）**：`_app.orm.xml`、`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/_gen/`、`_NopJob*.xmeta`、`_*.view.xml`、`nop-job/deploy/sql/{mysql,oracle,postgresql}/_create_nop-job.sql`、`nop-job-api/.../beans/NopJob{Fire,Task}{Input,Output}Bean.java`（`gen-crud-api.xgen` → `/nop/templates/crud-api`）。
- **保留层（需手改的引用点）**：`NopJobFire.view.xml:25`（retryRecordId 布局项）、`NopJobTask.view.xml:22`（workerAddress 布局项）、**`nop-job-meta/.../model/NopJobFire/NopJobFire.xmeta:17`（`<prop name="retryRecordId" updatable="false"/>`）、`nop-job-meta/.../model/NopJobTask/NopJobTask.xmeta:10`（`<prop name="workerAddress" insertable="false" updatable="false"/>`）**（保留层 xmeta，删列后不清理会在合并时产生幽灵 prop）、`nop-job/model/nop-job.orm.java:118,154,190,192`、`docs-for-ai/03-modules/nop-job.md:355`（progress）、`ai-dev/design/nop-job/retry-integration-design.md:70-74,106,151`（声称桥返回并回填 retry_record_id —— 与 live `void onFireFailed` 矛盾，属既有 doc-code drift）、`nop-entropy-e2e/packages/nop-job-e2e/tests/fault-tolerance.spec.ts`（FireItem:32 / TaskItem:42-43 / Phase 4:133-189）。
- **生成物中会自愈的引用**：`_NopJobFire/_NopJobTask.xmeta`、`_NopJob*.view.xml`、`_nop-job.i18n.yaml`、`_templates/*.json`（`nop-job-meta/.../_templates/NopJobTask.json` 含 progress 等死字段）——均经 gen-i18n.xgen / orm 模板随 codegen 自动清洗，不手改，但须在断言中验证。

### 真正剩余的 gap

- orm.xml 仍含 6 死列 + 1 冗余索引；2 个 cursor 扫描缺覆盖索引（filesort）。
- executor-kind dict 的 rpcBroadcast description 存在于生成物（会被下次 codegen 覆盖），不在源模型。
- `updateRetryRecordId` 接口/实现/测试 mock 残留无行为死代码。
- 保留层 view / orm.java / nop-job.md / retry-integration-design.md / E2E spec 引用将因删除而失效或已漂移。

## Goals

- `nop-job/model/nop-job.orm.xml` 删除：fire `taskCostCpu`、`taskCostMemory`、`retryRecordId` + 索引 `IX_NOP_JOB_FIRE_RETRY`；task `workerAddress`、`progress`、`progressMessage`。
- `nop-job/model/nop-job.orm.xml` 新增：`IX_NOP_JOB_FIRE_DISPATCH_TIME (fireStatus, partitionIndex, startTime, jobFireId)`、`IX_NOP_JOB_TASK_RUN_TIME (taskStatus, partitionIndex, startTime, jobTaskId)`。
- executor-kind dict 3 个 option 补 `description`（rpcBroadcast 承载 plan-339 语义原文），codegen 重生成后 description 仍存在（双构建稳定性断言）。
- 移除 `IJobFireStore.updateRetryRecordId`（接口 + impl + 3 个测试 mock override）。
- 全部保留层引用与 owner docs 收敛：view 布局、orm.java、`nop-job.md:355`、`retry-integration-design.md`（§3.1/§3.3/§4.1 改为 live 异步语义）、`store-contract-design.md`（补死列移除 + 覆盖索引裁定）、E2E spec（FireItem/TaskItem 接口 + 删 Phase 4）。
- 重生成验证：`_app.orm.xml` / `_gen` entity / `nop-job/deploy/sql` / api beans 自动清洗死列；全 nop-job mock 回归不回归；H2-blocked baseline 不变。

## Non-Goals

- **不重排剩余 propId**（保留缺口，最小 diff）；不改 3 实体任何其他列；**不动 schedule `taskCostCpu`/`taskCostMemory`/`priority`**（alive，dispatcher/bestFit 消费）。
- 不增删其他索引（RUN_SCAN / DISPATCH_SCAN / STATUS_WORKER / SCHEDULE_SCAN / IX_NOP_JOB_FIRE_SCHEDULE 等保留）。
- 不修 H2 测试环境（plan 340 已移 successor plan）；不做真 DB 集成验证（schema/index/字典为本 plan 非运行时行为，以重生成 + grep 断言 + compile 为 repo-observable 证据）。
- **不改 retry 桥接行为本身**（如"改回同步回填 retryRecordId"属新功能，需独立 plan；本 plan 只删除死物 + 收敛文档）。
- 不运行 E2E（无运行环境/后端，仅做 spec 编辑收敛）。

## Scope

### In Scope

- orm.xml 死列/索引删除 + 2 覆盖索引新增 + dict description 入源（Fix）。
- `IJobFireStore.updateRetryRecordId` 移除及 3 mock、`TestDefaultJobCancelHandler.java:339` 清理（Fix）。
- 保留层与 owner docs 同步（Fix）：view ×2、orm.java、`nop-job.md`、`retry-integration-design.md`、`store-contract-design.md`、E2E spec。
- codegen 重生成 + grep 断言 + compile + mock 回归 + 独立 closure audit。

### Out Of Scope

- 任何 live 列/其他索引变更；propId 重排。
- H2 修复、真 DB 集成验证、E2E 实际运行。
- retry 桥接行为重构；worker invoker / reserved-capacity；前端 UI 样式重构。

## Execution Plan

### Phase 1 - 源模型 + 保留层 + owner docs 编辑

Status: done
Targets: `nop-job/model/nop-job.orm.xml`、`nop-job/model/nop-job.orm.java`、`NopJobFire.view.xml`、`NopJobTask.view.xml`、`NopJobFire.xmeta`、`NopJobTask.xmeta`、`docs-for-ai/03-modules/nop-job.md`、`ai-dev/design/nop-job/retry-integration-design.md`、`ai-dev/design/nop-job/store-contract-design.md`、E2E spec

- Item Types: `Fix`、`Decision`

 - [x] **1.1**（Fix）`nop-job.orm.xml`：NopJobFire 删 `taskCostCpu`(28)/`taskCostMemory`(29)/`retryRecordId`(18) 三列 + `IX_NOP_JOB_FIRE_RETRY`；NopJobTask 删 `workerAddress`(6)/`progress`(21)/`progressMessage`(22) 三列；新增 `IX_NOP_JOB_TASK_RUN_TIME (taskStatus, partitionIndex, startTime, jobTaskId)` 与 `IX_NOP_JOB_FIRE_DISPATCH_TIME (fireStatus, partitionIndex, startTime, jobFireId)`（索引放各自 entity `<indexes>` 内，unique=false）。不重排 propId。
 - [x] **1.2**（Fix, Decision）`nop-job.orm.xml` `<dict name="job/executor-kind">`：3 个 option 补 `description`：
  - `test`："仅用于测试/调试的本地执行器"
  - `rpc`："将 fire 投递给 worker 侧 RPC 执行器（nopJobInvoker_rpc）"
  - `rpcBroadcast`："仅 worker 侧 invoker 选择键（nopJobInvoker_rpcBroadcast），与 task 拆分无关：task 拆分只由 dispatchMode 决定，广播需配 dispatchMode=broadcast（plan 339）"
  （i18n-en:label 保留不变）
 - [x] **1.3**（Fix）`nop-job/model/nop-job.orm.java`：删 `retryRecordId`(:118)、`workerAddress`(:154)、`progress`(:190)、`progressMessage`(:192) 四行。
 - [x] **1.4**（Fix）`NopJobFire.view.xml:25`：runtimeSummaryForm 布局去 `retryRecordId`（重排为 `retryPolicyId[重试策略ID] errorCode[错误码]` / `errorMessage[错误消息]`）；`NopJobTask.view.xml:22`：去 `workerAddress`（重排为 `workerInstanceId[执行节点ID] startTime[开始时间]` / `endTime[结束时间] durationMs[执行时长(毫秒)]` / `partitionIndex[分区索引] errorCode[错误码]` / `errorMessage[错误消息]`）。
 - [x] **1.5**（Fix）`docs-for-ai/03-modules/nop-job.md:355`：NopJobTask 关键字段列表删 `progress`。
 - [x] **1.6**（Fix）`ai-dev/design/nop-job/retry-integration-design.md`：§3.1 接口签名由 `String onFireFailed` 改为 live 的 `void onFireFailed`；§3.3 调用时机图删「将返回的 retry_record_id 回填」步骤（改为异步提交 + 日志回执，无回填）；§4.1 删 `retry_record_id` 行并注明该列已移除（本 plan 裁决：异步提交不回填 → 列是死物，删除；若未来需回填，需先改桥接为同步返回语义，属新功能）。
 - [x] **1.7**（Fix）`ai-dev/design/nop-job/store-contract-design.md`：补一小节「索引布局与死列裁定」——(a) 死列移除清单与依据（无读写方 + retryRecordId 因异步桥不回填而删除）、(b) 两个覆盖索引的定位（服务 fetchRunningTasks / fetchDispatchingFires 的 DESC 游标扫描，替代 filesort）与保留索引不变的理由、(c) dict description 入源约定（option description 属性才是不被 codegen 覆盖的位置）。
 - [x] **1.8**（Fix）`nop-entropy-e2e/packages/nop-job-e2e/tests/fault-tolerance.spec.ts`：`FireItem` 删 `retryRecordId`(:32)；`TaskItem` 删 `progress`/`progressMessage`(:42-43)；删除 Phase 4 测试(:133-189)。不改其他 phase（Phase 2/5/完整生命周期 与死列无关）。
 - [x] **1.9**（Fix）保留层 xmeta：`NopJobFire.xmeta:17` 删 `<prop name="retryRecordId" updatable="false"/>`；`NopJobTask.xmeta:10` 删 `<prop name="workerAddress" insertable="false" updatable="false"/>`（不删的后果：重生成后合并出幽灵 prop，运行时 GraphQL/xmeta 校验报错）。

Exit Criteria:

- [x] `nop-job.orm.xml` grep：`retryRecordId|taskCostCpu|taskCostMemory|workerAddress|progress|progressMessage|IX_NOP_JOB_FIRE_RETRY` 在 NopJobFire/NopJobTask 实体与索引区**零残留**（注意 schedule 的 taskCostCpu/Memory 必须保留——断言限定实体范围：fire 段禁 taskCostCpu/taskCostMemory/retryRecordId，task 段禁 workerAddress/progress/progressMessage）；`IX_NOP_JOB_TASK_RUN_TIME`、`IX_NOP_JOB_FIRE_DISPATCH_TIME` 存在；executor-kind dict 3 个 option 均有非空 `description`
- [x] 保留层/文档/E2E grep：`NopJobFire.view.xml`、`NopJobTask.view.xml`、`NopJobFire.xmeta`、`NopJobTask.xmeta`、`nop-job.orm.java`、`nop-job.md`、`fault-tolerance.spec.ts` 对 6 死列名**零引用**；`retry-integration-design.md` 以语义断言代替零引用（§3.1/§3.3 无回填声称、§4.1 无 retry_record_id 表行，仅允许"列已移除"裁定注）；`store-contract-design.md` 例外（新增的裁定小节按语义提及）
- [x] `retry-integration-design.md` §3.1/§3.3/§4.1 与 live `void onFireFailed` 语义一致
- [x] **No new test required**：纯删除 + 布局/文档收敛，无新增行为；删除性变更以 Phase 2 重生成 + grep 断言 + compile 验证（若 orm.xml 校验器/生成器对 propId 缺口报错，则在 Phase 2 处理并记录日志）
- [x] **No `./mvnw` 适用**（纯源编辑 phase，构建验证在 Phase 2）
- [x] `ai-dev/logs/2026/08-13.md` 已更新（本 Phase 完成后）

### Phase 2 - Java 清理 + codegen 重生成 + compile + mock 回归

Status: active
Targets: `IJobFireStore.java`、`JobFireStoreImpl.java`、`TestJobTimeoutChecker.java`、`TestJobE2E.java`、`TestJobCompletionProcessor.java`、`TestDefaultJobCancelHandler.java`、codegen 产物（`nop-job-dao`/`nop-job-meta`/`nop-job-api`/`nop-job-web`/`nop-job/deploy/sql`）

- Item Types: `Fix`、`Proof`

- [x] **2.1**（Fix）删 `IJobFireStore.updateRetryRecordId`（接口 :63）与 `JobFireStoreImpl.updateRetryRecordId`（:274-280）；删 3 个测试 mock 的 `updateRetryRecordId` override（`TestJobTimeoutChecker:1146`、`TestJobE2E:276`、`TestJobCompletionProcessor:495`）；`TestDefaultJobCancelHandler.java:339` 删 `task.setWorkerAddress(...)`。
 - [x] **2.2**（Proof）触发重生成：`./mvnw install -pl nop-job/nop-job-codegen -am -DskipTests`（gen-orm.xgen 渲染 orm 模板 → dao `_app.orm.xml`/`_gen`、meta dict/xmeta/i18n、web 视图、deploy sql）+ `./mvnw install -pl nop-job/nop-job-meta -am -DskipTests`（gen-crud-api.xgen → nop-job-api beans）。**若 -am 因父 reactor 依赖未安装而失败，退回全仓 `./mvnw clean install -DskipTests -T 1C`（plan 339 先例）并在日志记录所用路径。**
 - [x] **2.2b**（Proof）安装被 codegen 重写的兄弟模块：`./mvnw install -pl nop-job/nop-job-dao,nop-job/nop-job-api,nop-job/nop-job-web -am -DskipTests`。**否则后续 `-pl coordinator` 测试会解析到本地仓库的陈旧 SNAPSHOT jar（删除前的 dao/api 类），回归静默失效。**
 - [x] **2.3**（Proof）grep 断言生成物：
  - `_app.orm.xml`：fire 段无 `retryRecordId`/`taskCostCpu`/`taskCostMemory`，task 段无 `workerAddress`/`progress`/`progressMessage`；含 `IX_NOP_JOB_TASK_RUN_TIME`、`IX_NOP_JOB_FIRE_DISPATCH_TIME`（**索引断言只落在此文件**——`nop-job/deploy/sql/_create_*.sql` 的 DDL 渲染（`ddl.xlib` CreateTables）不输出 CREATE INDEX，任何"SQL 含新索引"断言都注定失败）
  - `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/_gen/_NopJobFire.java` 无 `retryRecordId`/`taskCostCpu`/`taskCostMemory`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/_gen/_NopJobTask.java` 无 `workerAddress`/`progress`/`progressMessage`
  - `executor-kind.dict.yaml` 三 option description 非空（含 rpcBroadcast 语义词——注意文案与手工恢复版略有措辞差异属预期 diff，见 2.3b）
  - `nop-job/deploy/sql/{mysql,oracle,postgresql}/_create_nop-job.sql`：**大小写不敏感**（pg 小写 / mysql 大写 / oracle 混合）且**按表范围限定**（schedule 段的 `TASK_COST_CPU`/`TASK_COST_MEMORY` 必须保留）检查 nop_job_fire/nop_job_task 表内无死列
  - `NopJobFireInputBean/OutputBean`、`NopJobTaskInputBean/OutputBean` 无死字段；`_NopJobFire.xmeta`/`_NopJobTask.xmeta`/`_NopJob*.view.xml`/`_nop-job.i18n.yaml`/`_templates/*.json` 无死字段（i18n/json 由 gen-i18n.xgen 自愈，此处验证而非手改）
 - [x] **2.3b**（Proof）提交说明标注：`executor-kind.dict.yaml` 与 `_gen`/视图/i18n 均为重生成产物，与 706c936f2 手工恢复版存在措辞级 diff（如"（plan 339：…）"位置、空 description 填充），属预期变更，不做回归处理。
 - [x] **2.4**（Proof）**双构建稳定性**：再次执行 2.2 的 codegen 构建一次，重跑 2.3 断言（证明 description 源在 orm.xml，不再依赖生成物手工内容）。
 - [x] **2.5**（Proof）`./mvnw test -pl nop-job/nop-job-coordinator -am`（已含 2.2b 安装的新 dao/api；mock/focused 全绿；H2-blocked 维持 plan 340 baseline 27 errors 全 42S04 不恶化）；`./mvnw compile` 覆盖 dao/api/worker 受影响模块。

Exit Criteria:

- [x] git diff 确认生成物自动清洗且无手改生成物（`_gen`/`_app.orm.xml`/`_*.view.xml`/`_create_*.sql`/`_nop-job.i18n.yaml`/`_templates/*.json` 仅通过 codegen 变更）
- [x] 2.3 全部 grep 断言通过（含 `_app.orm.xml` 索引断言、SQL 表范围+大小写断言、i18n/json 自愈验证）；2.4 双构建后断言仍通过
- [x] compile 全过（证明无残留 Java 引用）；测试：mock 套件全绿（`-pl coordinator -am`，消费 2.2b 安装的新 dao/api jar）；H2-blocked 错误数与 plan 340 baseline 一致（27，全 42S04）
- [x] **No new test required**：删除性变更，compile + grep 断言即证明；无新行为可测
- [x] `ai-dev/logs/2026/08-13.md` 已更新（本 Phase 完成后）

### Phase 3 - 回归 + 工具 + 独立 closure audit

Status: active
Targets: 全量受影响模块、`ai-dev/plans/341-*.md` 自身

- Item Types: `Proof`、`Follow-up`

- [x] **3.1**（Proof）全量回归：受影响模块 mock/focused 全绿；H2-blocked 数量与 baseline 一致（不恶化）。
- [x] **3.2**（Proof）工具三件套 exit 0：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/341-*.md --strict`、`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high`、`node ai-dev/tools/check-doc-links.mjs --strict`。
- [x] **3.3**（Proof）独立子 agent closure audit（fresh session），证据写入本 plan `## Closure` 段。
- [x] **3.4**（Follow-up）`source-anchors.md` 核对（本 plan 无 API 锚点变化——`updateRetryRecordId` 为 dao 模块内部接口，无锚点条目；如有多余条目则清理）。

Exit Criteria:

- [x] mock 全绿 + H2 baseline 未恶化；3 工具 exit 0
- [x] closure audit PASS 且证据含每条 EC/Gate 的 PASS/FAIL 与 live 证据
- [x] `check-plan-checklist --strict` exit 0（无未勾选项 + Closure Evidence 已写入）
- [x] Anti-Hollow：无新组件（本 plan 纯删除/重生成，无空壳引入面——closure audit 需确认 grep 断言真实执行、无静默跳过生成步骤）
- [x] `ai-dev/logs/2026/08-13.md` 收口记录与 Plan Status / Phase Status / Closure Gates 一致

## Closure Gates

> 关闭条件：本 section 与每个 Phase 的 Exit Criteria 全部勾选后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部 in-scope 死列/索引已从源模型删除且生成物清洗（grep 断言 + 重生成证明）
- [x] 2 个覆盖索引已入源模型并体现在生成物（索引断言以 `_app.orm.xml` 为准——`nop-job/deploy/sql` 的 DDL 渲染器（`ddl.xlib` CreateTables）不输出 CREATE INDEX，SQL 只断言死列清除）
- [x] executor-kind dict description 入源且经双构建稳定（迭代两轮 codegen 后仍存在）
- [x] `IJobFireStore.updateRetryRecordId` 已移除（接口/impl/3 mock 零残留）
- [x] 保留层 view / orm.java / nop-job.md / retry-integration-design.md / store-contract-design.md / E2E spec 已与 live baseline 收敛；无 in-scope 死列引用残留
- [x] 不存在被静默降级到 deferred 的 in-scope 项（死列已裁定删除而非保留；H2/E2E 运行按 Non-Goals 显式排除）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 grep 断言真实执行、codegen 步骤真实运行（非声称）、无静默跳过的生成/删除步骤
- [x] `./mvnw compile` 受影响模块 BUILD SUCCESS
- [x] `./mvnw test` 受影响模块 mock/focused 全绿（H2-blocked 除外，与 baseline 一致）
- [x] checkstyle / 代码规范检查通过（父 pom checkstyle 非活跃门禁，按 AGENTS.md 规范人工核对：imports 分组、无裸 RuntimeException 等）

## Deferred But Adjudicated

### H2 测试环境修复（空库初始化）

- Classification: `out-of-scope improvement`（plan 340 已裁决）
- Why Not Blocking Closure: `ai-dev/logs/2026/08-11.md:121` 证实为 AutoTest/H2 单模块隔离运行下 schema 未初始化，非本 plan 改动引入；本 plan 以重生成 + grep 断言 + mock + compile 完成 repo-observable 验证。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/`（后续 NN-nop-job-h2-test-init，research-first 定位 init skip 点）

### retry 桥接改为同步回填 retry_record_id（恢复列语义）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需改 `IJobRetryBridge` 契约（void→返回 record id）+ adapter 同步提交 + 新列——是功能变更不是清理；当前异步提交语义工作正常（幂等 id 已用 jobFireId，`retry-integration-design.md` 更新后文档一致）。
- Successor Required: `no`

### E2E 套件实际运行验证

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要完整后端 + 数据库运行环境（H2 亦 blocked），本环境不可执行；spec 编辑为纯收敛（删 Phase 4 + 接口字段），语法正确性由 TS 结构简单 + review 保证。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `nop-job/` 外任何模块若引用已删 GraphQL 字段（搜索 `retryRecordId|workerAddress|progress|taskCostCpu` 于 `nop-*`/`nop-entropy-e2e` 非生成文件）——本 plan 全仓扫过：非生成引用仅 view/xmeta/orm.java/文档/E2E spec 七处保留层（全部在 Phase 1 清理），无其他消费者；未来新增消费者需回归。

## Closure

Status Note: 2026-08-13 执行完成，closure audit 通过后设置 completed
Completed: 2026-08-13

Closure Audit Evidence: 独立子 agent ses_006a1ce8bffe2UdMnaLe4GH77J 全量核验：6 条技术主张（源模型/生成物/接口移除/保留层/测试基线/编译）全部 PASS 附 live 证据；工具三件套 exit 0；发现 3 项 plan 维护项（A: 08-13.md 日志缺失、B: Phase 1 checkbox 未勾、C: 未提交）——本 Closing 收口已全部解决（日志已写、勾选完成、git 提交按 nop-git-master 执行）；marker of interest：coordinator 27 errors（21+6 H2 42S04）= plan 340 baseline 精确一致；dao -am 下 31 errors 亦全为 H2 空库（08-12 log 同口径），SQL 引用零死列。

Reviewer / Agent: 独立审计子 agent（general, ses_006a1ce8bffe2UdMnaLe4GH77J）；执行 agent（main session）

Evidence:
- PASS(1) 源模型 orm.xml：fire/task 实体零死列；IX_NOP_JOB_FIRE_RETRY 消失；IX_NOP_JOB_TASK_RUN_TIME/IX_NOP_JOB_FIRE_DISPATCH_TIME 存在且列序正确；schedule 段保留 taskCostCpu/Memory/priority；dict description 与 orm.xml 源逐字一致（grep + live 文件核对）
- PASS(2) 生成物：_app.orm.xml/_gen/api beans/xmeta/view/i18n/_templates/deploy sql 零死字段；3 库 SQL 无死列、schedule 保留；CREATE INDEX 计数 0（ddl.xlib 不渲染，plan 已注明属预期）
- PASS(3) 接口移除：IJobFireStore/JobFireStoreImpl diff 纯删除；全仓非 ai-dev 零 updateRetryRecordId/setWorkerAddress/setRetryRecordId 残留；4 测试类零残留
- PASS(4) 保留层/文档/E2E：view×2/xmeta×2/orm.java 零死列；nop-job.md 无 progress；retry-integration-design.md §3.1 void/§3.3 无回填/§4.1 无行；spec.ts 无 Phase 4；source-anchors.md 无锚点条目
- PASS(5) 测试基线：coordinator 163/0/27/2（27=21+6 全 42S04 H2 空库=plan 340 baseline）；dao 31 errors 全空库 42S04 零死列引用（08-12 log 同口径）→ 未恶化
- PASS(6) 编译：JobFireStoreImpl 零 setRetryRecordId 调用；受影响模块 compile BUILD SUCCESS
- PASS(7) Anti-Hollow：closure audit 独立复核 grep 断言与 codegen 步骤（双构建迭代后断言仍通过），无静默跳过

Follow-up: 见 ## Deferred But Adjudicated（H2 测试环境修复 → successor NN-nop-job-h2-test-init）；Non-Blocking Follow-ups 已核（全仓非生成文件零残留引用）

## Optional Sections

### Risks And Rollback

- **GraphQL API 字段删除（breaking change）**：`NopJob{Fire,Task}{Input,Output}Bean` 删除 6 字段 → 运行时 GraphQL 不可再 select/写入这些字段。回滚：git revert 本 plan 单 commit。缓解：全仓已确认无独立消费者（前端 `_gen` 视图同步重生成；E2E spec 已同步；无第三方契约）。
- **codegen 重生成产生意外 diff**：`_gen`/视图/部署 SQL 由模板渲染，若模板版本与 model 属性差异导致额外变更，以 git diff 审查为准，排除非本 plan 引入的变更（不手改生成物）。
- **propId 缺口**：删除后 fire 缺 18/28/29，task 缺 6/21/22；ORM/生成器允许非连续 propId（schedule 无此先例但有历史 gap 先例，如 340 前 fire 快照列 28/29 即在 27 之后）；若 codegen 报错则按报错信息处理并记录。
- **`retry-integration-design.md` 语义修订**：文档从"同步回填"改为"异步无回填"，若未来实现同步语义需按 §Deferred 反向修订文档。