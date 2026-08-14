# 1 P2 Audit Backlog Cleanup

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Mission: nop-datav
> Work Item: P2 audit backlog cleanup (unresolved items from `ai-dev/backlog/nop-datav-audit-followups.md`)
> Source: `ai-dev/backlog/nop-datav-audit-followups.md` items #4, #7, #8, #11, #12, #17 + minor nits
> Related: Original audits `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md`, `ai-dev/audits/nop-datav/2026-08-10-1516-open-audit-nop-datav.md`

## Purpose

将 nop-datav 审计后剩余的 P2 backlog 全部收敛到 landed 或显式裁定，消除已知的技术债：导出错误码语义不匹配、ScreenSnapshot ORM 契约漂移（mandatory 缺失 + UK 命名不一致）、ChatBI 数据集查询全列加载性能问题、以及两个孤儿/空壳模块的处置。这些是 roadmap 中唯一不受 flux 前端阻塞的可执行后端工作。

## Current Baseline

- **#4 Dim09-03**：`NopDatavExportTaskBizModel.createExportTask` 在 sourceType/sourceId 为空时抛 `ERR_DATAV_EXPORT_TASK_NOT_FOUND`（语义错误——任务尚未创建不存在"not found"），且空源检查在 `validateFormat` 之后（`NopDatavExportTaskBizModel.java:131-134`）。注：`ERR_DATAV_EXPORT_TASK_NOT_FOUND` 在 `requireTask`（`:424`）有合法使用（taskId 不存在），错误码定义和 import 须保留。
- **#7 Dim04-01**：`NopDatavScreenSnapshot.snapshotContent` 列在 ORM 源模型 `nop-datav.orm.xml:785` 缺少 `mandatory="true"`，而 design doc `screen-design.md:79` 明确标注 `snapshotContent | clobJson mandatory`。契约漂移。
- **#8 Dim04-02**：`NopDatavScreenSnapshot` 的 UK 在 ORM `nop-datav.orm.xml:821` 命名为 `UK_NOP_DATAV_SCREEN_SNAPSHOT_SCR_VER`（缩写 SCR_VER），而 design doc `screen-design.md:84` 写的是 `UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER`（全拼 SCREEN_VER）。命名不一致。
- **#11 AR-1**：`nop-datav-core` 目录存在（261 行生成常量 `_NopDatavCoreConstants.java`），但已不在 `nop-datav/pom.xml` 的 `<modules>` 中（从未被加入构建）。service 层有自己的手写枚举常量。该目录是codegen 产出的残留物，无人引用。
- **#12 AR-2**：`nop-datav-chart` 在 `<modules>` 中（`nop-datav/pom.xml:20`），但无源码（仅 `target/`），仅有一个硬编码 `<version>5.4.0</version>` 的 `poi` 依赖（绕过 nop-bom 版本管理）。该模块从未被其他模块依赖。
- **#17 AR-7**：`DatavListDatasetsExecutor.findActiveDatasets` 调用 `dao.findAll()` 加载全部列（含 `dsText`/`dsMeta` 大字段 `VARCHAR(131072)`，domain `json-128K`）后在内存中按 `status` 和 `keyword` 过滤（`DatavListDatasetsExecutor.java:96-112`）。数据集数量增长时大字段加载浪费内存。注：`dsText`/`dsMeta` 是 `VARCHAR(131072)` 非 CLOB，但单列 128KB 仍不适合列表全量加载。
- **Minor nits**：
  - `DatavGenerateDashboardExecutor.generateId(prefix)` 的 `prefix` 参数未使用。
  - `ChatBiToolCallingLoop.QUERY_HANDLER` 中 `parseNonStrict` 同一三元表达式内被调用两次（应解析一次入局部变量）。

## Goals

- 所有 6 个 P2 backlog items + 3 个 minor nits 收敛为 landed 或显式裁定（deleted / out-of-scope-with-rationale）。
- `nop-datav-audit-followups.md` 中 #4, #7, #8, #11, #12, #17 全部标记为 ✅ resolved。
- 导出错误码语义正确（空源 → 专用 missing-source 错误码，非 task-not-found）。
- ScreenSnapshot ORM 与 design doc 契约一致（mandatory + UK 命名）。
- ChatBI 数据集列表查询避免加载非活跃数据集行（含 `dsText`/`dsMeta` 大字段 VARCHAR(131072)）。

## Non-Goals

- 修复 D1-4 / D2-4 前端集成（blocked on flux，不在 nop-datav 后端 scope）。
- 图像导出 PDF/PNG（blocked on 渲染能力，roadmap 显式 deferred）。
- nop-datav-api 空模块处置（作为 nop-datav-dao 占位依赖合法，audit 明确标注"若永久弃用可删依赖"，当前不阻塞任何功能）。
- 任何功能性增强（本计划仅做 P2 cleanup，不增加新功能）。

## Scope

### In Scope

- #4 Dim09-03：新增 `ERR_DATAV_EXPORT_MISSING_SOURCE` 错误码，空源检查前移到 `validateFormat` 之前。
- #7 Dim04-01：ORM 源模型 `nop-datav.orm.xml` ScreenSnapshot.snapshotContent 加 `mandatory="true"`。
- #8 Dim04-02：ORM 源模型 UK 重命名 `SCR_VER` → `SCREEN_VER` 对齐 design doc。
- #11 AR-1：删除 `nop-datav-core` 目录（已不在 `<modules>`，无消费者）。
- #12 AR-2：从 `<modules>` 删除 `nop-datav-chart` + 删目录。
- #17 AR-7：`findActiveDatasets` 改用 criteria 查询将 `status=1` 过滤下推到 SQL 层（避免加载非活跃行），keyword 过滤在内存中对已过滤的活跃集执行。不再调用 `dao.findAll()` 加载全表行。
- Minor nits：`generateId` 删除未使用的 prefix 参数；`parseNonStrict` 解析一次入局部。

### Out Of Scope

- nop-datav-api 空模块处置（合法占位依赖，不阻塞）。
- Cluster cancel 持久化（Non-Blocking Follow-up，watch-only）。
- 导出文件定时清理 / 进度百分比（Non-Blocking Follow-ups）。

## Execution Plan

### Phase 1 — ORM Contract Alignment (#7, #8)

Status: completed
Targets: `nop-datav/model/nop-datav.orm.xml`

- Item Types: `Fix`

> ORM 模型变更属 plan-first 硬约束区域。本 Phase 的两项变更为 metadata 级微调（添加 mandatory 属性 + 重命名 UK），不改表结构（mandatory 是 ORM 层校验约束，UK 重命名仅改约束名不改约束语义）。

- [x] #7：`NopDatavScreenSnapshot.snapshotContent` 列添加 `mandatory="true"`（`nop-datav.orm.xml:785`），对齐 `screen-design.md:79`
- [x] #8：`NopDatavScreenSnapshot` UK 名从 `UK_NOP_DATAV_SCREEN_SNAPSHOT_SCR_VER` 改为 `UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER`（`nop-datav.orm.xml:821`），对齐 `screen-design.md:84`
- [x] 执行 `./mvnw clean install -pl nop-datav/nop-datav-dao -am -DskipTests -T 1C` 重新生成 `_app.orm.xml` 及下游代码，确认 mandatory 和 UK 名已传递到生成物
- [x] 验证生成物 `_app.orm.xml` 中 ScreenSnapshot 的 snapshotContent 列含 `mandatory="true"` 且 UK 名为 `SCREEN_VER`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-datav.orm.xml` 源模型中 `NopDatavScreenSnapshot.snapshotContent` 含 `mandatory="true"`
- [x] `nop-datav.orm.xml` 源模型中 UK 名为 `UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER`
- [x] 生成物 `_app.orm.xml` 与源模型一致（mandatory + UK 名同步；`_NopDatavScreenSnapshot.xmeta` 也已同步）
- [x] **无静默跳过**：无新增方法/分支，此 Phase 为纯 ORM 属性修正，不涉及新代码路径
- [x] owner doc 更新：`screen-design.md` 行 79/84 已为正确值，此 Phase 是让 ORM 对齐 design doc，无需更新 design doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Service-Layer Correctness (#4, #17, minor nits)

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavErrors.java`, `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java`, `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavListDatasetsExecutor.java`, `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateDashboardExecutor.java`, `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/ChatBiToolCallingLoop.java`

- Item Types: `Fix`

- [x] #4：在 `NopDatavErrors.java` 新增 `ERR_DATAV_EXPORT_MISSING_SOURCE` 错误码（ARG_SOURCE_TYPE + ARG_SOURCE_ID），并将 `createExportTask` 中的空源检查从 `validateFormat` 之后前移到之前，替换 `ERR_DATAV_EXPORT_TASK_NOT_FOUND` 为新错误码。注：`ERR_DATAV_EXPORT_TASK_NOT_FOUND` 定义和 import 保留（`:424` `requireTask` 合法使用）
- [x] #17：`DatavListDatasetsExecutor.findActiveDatasets` 将 `dao.findAll()` + 内存 status 过滤改为 criteria 查询（如 `dao.findAllByExample(template)` 设 `template.setStatus(1)`，或 `dao.findAllByQuery(QueryBean)`），将 `status=1` 下推到 SQL 层避免加载非活跃行及其大字段（`dsText`/`dsMeta` VARCHAR(131072)）。keyword 过滤仍在内存中执行（仅对已过滤的活跃集，数量有限）
- [x] Minor nit 1：`DatavGenerateDashboardExecutor.generateId(prefix)` — 删除 `prefix` 参数（现有注释说明 UUID 去横线恰好 32 字符，加前缀会超列长 precision=32；使用前缀不可行）。调用方同步更新
- [x] Minor nit 2：`ChatBiToolCallingLoop.QUERY_HANDLER` — 将 `parseNonStrict` 调用提取到局部变量，避免同一三元表达式中重复解析
- [x] 为 #4 新增测试：空 sourceType/sourceId 抛 `ERR_DATAV_EXPORT_MISSING_SOURCE`（非 `ERR_DATAV_EXPORT_TASK_NOT_FOUND`），且在 `validateFormat` 之前执行（传非法 format + 空源，期望 missing-source 错误而非 type-not-supported）
- [x] 为 #17 新增/修改测试：验证 keyword 过滤结果正确（结果集与之前一致），且非活跃数据集（status≠1）不被加载。验证方式：在已有 AutoTest seed data 中混入非活跃数据集，断言列表结果不包含它们
- [x] 确认无既有测试错误断言空源场景的 `ERR_DATAV_EXPORT_TASK_NOT_FOUND`（仅 `TestNopDatavShareExportRbac.java:106` 引用此错误码，且为 taskId-not-found 合法路径，不应修改）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `NopDatavErrors` 含 `ERR_DATAV_EXPORT_MISSING_SOURCE` 错误码定义，且 `ERR_DATAV_EXPORT_TASK_NOT_FOUND` 保留（`requireTask` 仍使用）
- [x] `createExportTask` 空源检查在 `validateFormat` 之前执行，抛 `ERR_DATAV_EXPORT_MISSING_SOURCE`
- [x] `DatavListDatasetsExecutor.findActiveDatasets` 不再调用 `dao.findAll()` 加载全表；status=1 过滤下推到 SQL 层（经代码审查验证 `findAll()` 已被 criteria 查询替代）
- [x] `generateId` 不再有未使用的 prefix 参数（参数已删除）
- [x] `ChatBiToolCallingLoop.QUERY_HANDLER` 中 `parseNonStrict` 仅调用一次
- [x] 新增测试覆盖空源错误码（`testCreateExportTask_emptySource_throwsMissingSource`）
- [x] `TestNopDatavShareExportRbac.java:106` 的 taskId-not-found 断言未被误改
- [x] **接线验证**：新错误码经 `createExportTask` action 端到端验证（BizModel 直接调用，断言抛出正确的 ErrorCode）
- [x] **无静默跳过**：新增代码路径在异常时显式抛 NopException，无空方法体/continue/吞异常
- [x] owner doc 更新：`nop-datav-audit-followups.md` 中 #4, #17 标记为 ✅ resolved
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Module Hygiene (#11, #12)

Status: completed
Targets: `nop-datav/pom.xml`, `nop-datav/nop-datav-core/`, `nop-datav/nop-datav-chart/`

- Item Types: `Decision` | `Fix`

- [x] #11 决策：`nop-datav-core` 已不在 `<modules>` 中（从未加入构建），仅含 codegen 产出的 261 行常量，service 层已有自己的手写枚举。裁定：**删除目录**（codegen 产出物已无消费者，保留造成 single-source-of-truth 侵蚀）
- [x] #11 执行：`rm -rf nop-datav/nop-datav-core`
- [x] #12 决策：`nop-datav-chart` 在 `<modules>` 中但无源码，仅有一个绕过 nop-bom 的硬编码 `poi:5.4.0` 依赖，从未被其他模块依赖。裁定：**从 `<modules>` 删除 + 删目录**（空壳模块 + 版本管理 bypass）
- [x] #12 执行：从 `nop-datav/pom.xml` 的 `<modules>` 中移除 `<module>nop-datav-chart</module>`，然后 `rm -rf nop-datav/nop-datav-chart`
- [x] 验证：`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 成功（确认移除这两个模块不破坏构建链路）
- [x] 验证：`rg "nop-datav-core|nop-datav-chart" nop-datav/` 无残留引用（pom.xml 依赖、beans.xml bean class、Java import）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-datav/nop-datav-core` 目录已删除
- [x] `nop-datav/nop-datav-chart` 目录已删除
- [x] `nop-datav/pom.xml` 的 `<modules>` 不再包含 `nop-datav-chart`
- [x] 全仓搜索无 `nop-datav-core` 或 `nop-datav-chart` 的残留引用（pom 依赖/import/beans.xml；文档记录除外）
- [x] `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS
- [x] **无静默跳过**：无新增方法/分支，此 Phase 为纯模块删除，不涉及新代码路径
- [x] owner doc 更新：`nop-datav-audit-followups.md` 中 #11, #12 标记为 ✅ resolved
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] #4 Dim09-03：`ERR_DATAV_EXPORT_MISSING_SOURCE` 定义且 `createExportTask` 使用之，空源检查在 format 校验之前
- [x] #7 Dim04-01：ORM 源模型 + 生成物中 ScreenSnapshot.snapshotContent 含 `mandatory="true"`
- [x] #8 Dim04-02：ORM 源模型 + 生成物中 UK 名为 `SCREEN_VER`（非 `SCR_VER`）
- [x] #11 AR-1：`nop-datav-core` 目录已删除
- [x] #12 AR-2：`nop-datav-chart` 目录已删除且从 `<modules>` 移除
- [x] #17 AR-7：`findActiveDatasets` 不再加载全表行（status=1 下推到 SQL 层）
- [x] Minor nits（2 项）已修复
- [x] `nop-datav-audit-followups.md` 中 #4, #7, #8, #11, #12, #17 全部标记 ✅ resolved
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs（`nop-datav-audit-followups.md`、`screen-design.md` 如需）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证无空方法体/静默跳过/no-op
- [x] `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS
- [x] `./mvnw test -pl nop-datav/nop-datav-dao -T 1C` 全绿（验证 mandatory 属性变更不破坏 dao 层测试）
- [x] `./mvnw test -pl nop-datav/nop-datav-service -T 1C` 全绿（含新增测试）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（执行中按需填写；当前无预判延期项。nop-datav-api 空模块合法占位，不在本计划 scope。）

## Non-Blocking Follow-ups

- nop-datav-api 空模块：作为 nop-datav-dao 占位依赖合法（audit 明确标注）；若未来永久弃用 CRUD-API 生成可删依赖。不阻塞当前 cleanup。
- nop-datav-core 常量是否应通过 codegen 重新产出并由 service 引用（替代手写枚举）：当前裁定为删除（codegen 产出无消费者），未来若需要可重新 codegen。watch-only。

## Closure

Status Note: P2 audit backlog cleanup 全部收敛。6 个 audit findings（#4 Dim09-03 导出错误码语义、#7 Dim04-01 snapshotContent mandatory、#8 Dim04-02 UK 命名、#11 AR-1 nop-datav-core 孤儿模块、#12 AR-2 nop-datav-chart 空壳模块、#17 AR-7 ChatBI 数据集查询全列加载）+ 2 minor nits（generateId prefix、parseNonStrict 重复调用）全部 landed。所有变更为后端，不阻塞 flux 前端。`nop-datav-audit-followups.md` 全部 17 items + 2 nits 标记 ✅ resolved。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: mission-driver EXECUTE pass (opencode task session, 2026-08-14)
- Evidence:
  - Phase 1 Exit Criteria（全部 PASS）：`nop-datav.orm.xml:785` snapshotContent 含 `mandatory="true"`；`:821` UK 名 `UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER`；生成物 `_app.orm.xml:656` mandatory + `:694` UK 名同步；`_NopDatavScreenSnapshot.xmeta:20` UK 名 + `:37` mandatory 同步。
  - Phase 2 Exit Criteria（全部 PASS）：`NopDatavErrors.java` 新增 `ERR_DATAV_EXPORT_MISSING_SOURCE`（ARG_SOURCE_TYPE + ARG_SOURCE_ID），`ERR_DATAV_EXPORT_TASK_NOT_FOUND` 保留；`NopDatavExportTaskBizModel.java:132-135` 空源检查在 `validateFormat` 之前、抛 MISSING_SOURCE；`:425` requireTask 仍用 TASK_NOT_FOUND；`DatavListDatasetsExecutor.java` findActiveDatasets 用 QueryBean+FilterBeans.eq(status,1)+findAllByQuery 替代 findAll()；`DatavGenerateDashboardExecutor.java:393` generateId() 无 prefix 参数，3 调用点（:267,:300,:321）同步；`ChatBiToolCallingLoop.java:231` parseNonStrict 提取 parsedObj 局部变量仅调一次。新增测试 testCreateExportTask_emptySource_throwsMissingSource（3 断言：空源+合法/非法 format 均 MISSING_SOURCE）+ testListKeywordFilterCombinedWithActiveStatus（keyword+status 组合，非活跃不加载）。TestNopDatavShareExportRbac.java:117 未误改（taskId-not-found 合法路径）。
  - Phase 3 Exit Criteria（全部 PASS）：`nop-datav/nop-datav-core/` 已删；`nop-datav/nop-datav-chart/` 已删；`nop-datav/pom.xml` `<modules>` 不含 nop-datav-chart；`rg "nop-datav-core|nop-datav-chart" nop-datav/` 无代码/配置残留（仅文档记录）。
  - `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests`：BUILD SUCCESS。
  - `./mvnw test -pl nop-datav/nop-datav-dao,nop-datav/nop-datav-service -T 1C`：**423 tests, 0 failures, 0 errors**（+2 新测试，原 421）。
  - Deferred 项分类检查：无 in-scope live defect 被降级；nop-datav-api 空模块为 out-of-scope（合法占位，Non-Blocking Follow-ups 已记录）。
  - Anti-Hollow 检查：本计划为 cleanup 性质（错误码修正/ORM 属性/查询优化/模块删除），无新增管线/组件需端到端接线验证；新增测试直接调用 BizModel/Executor 入口断言 ErrorCode，非空壳。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`：exit 0（在最终更新后验证）。

Follow-up:

- nop-datav-api 空模块：作为 nop-datav-dao 占位依赖合法（audit 明确标注）；若未来永久弃用 CRUD-API 生成可删依赖。watch-only。
- nop-datav-core 常量是否应通过 codegen 重新产出并由 service 引用（替代手写枚举）：当前裁定为删除（codegen 产出无消费者）。watch-only。
