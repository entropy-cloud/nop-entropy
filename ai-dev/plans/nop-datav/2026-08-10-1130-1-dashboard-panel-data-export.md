# 1 看板/面板数据导出（CSV/Excel 异步任务 + 限额）

> Plan Status: completed
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D3-3；`ai-dev/design/nop-datav/permission-sharing-design.md`（D3-3 导出由后继 plan 产出）
> Related: `2026-08-10-1100-1-dashboard-permission-and-audit-log.md`（D3-1 权限复用）、`2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`（D1 数据绑定管线复用）
> Mission: nop-datav
> Work Item: D3-3

## Purpose

将 roadmap D3-3 的「数据导出」部分收口为可用：用户可对单个面板（或看板下全部数据面板）发起数据导出（CSV / Excel），后端经 D1 数据绑定管线取数，异步生成文件、经 nop-file 落盘、按限额控制并发与行数，完成后提供下载。本计划同时显式界定「图像导出（PDF/PNG）」为 out-of-scope（依赖前端/无头渲染器，与 D1-4/D2-4 同类阻塞），不做空壳实现。

## Current Baseline

基于 live repo 核对（2026-08-10）：

- **数据取数已就绪**：`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelDataBinder.java` 的 `queryPanelData(panelId, panel, requestParams)` 端到端执行 面板→数据集引用→参数求值→SQL→结果，返回 `PanelDataResult`（含 `columns: List<String>` 与 `rows: List<Map<String,Object>>`）。由 `NopDatavPanelBizModel.getPanelData` / `refreshPanel` 调用（`nop-datav-service/.../entity/NopDatavPanelBizModel.java`）。导出直接复用该管线，不重写查询。
- **面板权限已覆盖**：`getPanelData` 经 `requireEntity` → `checkDataAuth`（D3-1 RLS + action 级 `@Auth`）。导出入口经同一 `requireEntity` 链路即可继承行级权限，无需另建权限。
- **ORM 源模型位置**：`nop-datav/model/nop-datav.orm.xml`（7 个实体，源模型，非生成物）。新增导出任务实体在此编辑，`mvn install` 重新生成 `_gen`/dao/meta。
- **文件存储基建（复用，但 service pom 尚未依赖）**：`nop-file` 模块 `NopFileRecord`（fileId/fileName/filePath/mimeType/fileLength/bizObjName/bizObjId，`nop-file-dao/.../orm/_app.orm.xml`）+ `IFileRecord`（`nop-service-framework/nop-biz-file-core/.../IFileRecord.java`，含 `getResource()`）+ `IFileStore`（`nop-biz-file-core/.../IFileStore.java`，API：`saveFile(UploadRequestBean, maxLength)→fileId`、`getFile(fileId)→IFileRecord`、`getFileLink(fileId)→String`）+ `UploadRequestBean`（`nop-biz-file-core/.../UploadRequestBean.java`）。`nopFileStore` bean（`DaoResourceFileStore`）在 `nop-file/nop-file-dao/_vfs/nop/file/beans/app-file-dao.beans.xml` 注册（`ioc:default="true"`，依赖 nop-file-dao 即拉入）。**`nop-datav/nop-datav-service/pom.xml` 当前未依赖 nop-file-dao / nop-biz-file-core，本计划需新增（见 Phase 3）**。
- **CSV/Excel 写出基建（复用，但 service pom 尚未依赖）**：CSV 由 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/record/csv/CsvResourceRecordIO.java`（`openOutput(IResource, encoding)→IRecordOutput`）+ `CsvRecordOutput.java` 提供（核心模块已传递依赖，无需新增 pom）；CSV 的 UTF-8 BOM 需在写出前手动写 `0xEF 0xBB 0xBF` 三字节（`openOutput` 的 encoding 不自动写 BOM）。Excel/xlsx 写出入口为 `ExcelHelper.saveExcel(IResource, ExcelWorkbook)`（`nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/util/ExcelHelper.java:44`），需从 `PanelDataResult{columns,rows}` 构造 `ExcelWorkbook`（sheets→rows→cells）后调用。**`nop-format/nop-excel` + `nop-format/nop-ooxml/nop-ooxml-xlsx` 需新增为 nop-datav-service pom 依赖（见 Phase 3）**。
- **异步执行基建（复用，核心模块已传递依赖）**：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`（`GlobalExecutors`/`IThreadPoolExecutor`/`DefaultThreadPoolExecutor`，`globalWorker()`/`submit(Callable)→CompletableFuture`），以及 Java 21 `nop-utils/nop-commons-java21/.../VirtualThreadTaskExecutor.java`。导出后台执行复用平台执行器，不自建线程池。**异步线程内无请求上下文/IServiceContext，需在 Phase 1 裁定如何获取 IDaoProvider/IOrmSession 更新任务状态、以及 cancel 如何中断运行中任务**。
- **错误码集中点**：`nop-datav-service/.../NopDatavErrors.java`（interface，导出相关错误码在此新增）。
- **真正剩余 gap**：无导出任务实体、无导出服务/action、无文件生成与下载编排、无限额配置；service pom 缺文件/Excel 依赖。图像导出（PDF/PNG）所需的前端/无头渲染能力尚未存在（flux 未产出），属本计划 out-of-scope。

## Goals

- 提供面板级数据导出（CSV 与 Excel/xlsx 两种格式），数据来自 D1 数据绑定管线（复用 `PanelDataBinder`，不重写查询）。
- 提供看板级「导出全部数据面板」：对看板下所有 `needsDataset=true` 的面板逐个取数，合并为多 sheet（Excel）或多个 CSV 文件。
- 导出为异步任务：创建任务→后台执行→状态轮询→文件下载，状态机明确（pending/running/succeeded/failed）。
- 限额：单任务最大导出行数、单用户并发任务数、文件保留期，超限快速失败（不静默截断/跳过）。
- 导出入口继承 D3-1 面板/看板权限（`requireEntity` → `checkDataAuth`），不经公共分享链路。

## Non-Goals

- **图像导出（PDF/PNG）**：图表/看板截图导出依赖前端/无头渲染器（flux 未产出）。本计划不实现渲染，任务类型注册时对 image 类型显式拒绝（抛 `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`），不做空壳/静默跳过。归后继 plan（依赖渲染能力）。
- 定时/调度导出（归 D5 定时报告）。
- 重建数据源/数据集管理或导出内核（复用 nop-report 数据集取数 + 平台 CSV/Excel 写出 + nop-file 存储）。
- 跨数据源/多数据源路由（D1 已声明为 Non-Goal，沿用）。
- 前端导出按钮/进度条 UI（flux 侧）。
- DatasetRef 与 nop-metadata 维度/度量的字段映射元数据运行时解析（D1 deferred 的 optimization candidate，导出直接输出数据集原始列）。

## Scope

### In Scope

- 新增 `NopDatavExportTask` 实体（导出任务记录：来源类型 panel/dashboard、来源 ID、格式、状态、参数、文件记录 ID、行数、错误信息、创建人等）。
- 设计文档：在 `permission-sharing-design.md` 增补 D3-3 导出章节（任务实体列约定、状态机、异步执行机制、限额阈值与配置项、文件存储与下载、图像导出 out-of-scope 裁定）。
- 导出服务：`PanelDataExporter`（复用 `PanelDataBinder` 取数 → CSV/Excel 写出 → nop-file 落盘）；`NopDatavExportTaskBizModel`（create/get/cancel + 后台执行 + 状态更新 + 下载）。
- 限额配置（`NopDatavConfigs`）：最大行数、并发任务数、保留期。
- 错误码：导出相关（`ERR_DATAV_EXPORT_*`）。
- 权限：导出 action 经 `requireEntity` 继承 D3-1 行级权限 + `@Auth`。
- 单元测试 + AutoTest + 端到端测试（创建任务→轮询→下载→校验内容）。

### Out Of Scope

- 图像导出 PDF/PNG（渲染依赖）。
- 定时导出（D5）。
- 前端 UI（flux）。
- 导出任务的全局调度/重试引擎（本计划仅 in-process 异步执行 + 状态记录）。

## Execution Plan

### Phase 1 - 设计文档定稿（D3-3 导出章节）

Status: completed
Targets: `ai-dev/design/nop-datav/permission-sharing-design.md`（增补 D3-3 导出章节）

- Item Types: `Decision`

- [x] 在 `permission-sharing-design.md` 增补 D3-3 导出章节，记录最终设计决策（不写 "Proposed vs Current"）：
  - [x] 任务实体列约定（行为规格，非代码签名）：`NopDatavExportTask`（表 `nop_datav_export_task`）来源类型/来源 ID/格式/状态/参数 JSON/文件记录 ID/行数/错误信息 + 标准审计列。
  - [x] 状态机裁定：`pending → running → succeeded | failed | cancelled`，每态迁移条件与持久化时机。
  - [x] 异步执行机制裁定（**含线程模型**）：复用平台 `GlobalExecutors.globalWorker()`（或 VirtualThreadTaskExecutor）后台执行；任务创建即返回 taskId，执行器内更新状态；**异步线程内无 IServiceContext，须裁定如何获取 IDaoProvider/IOrmSession 写任务状态**（如执行体内 `DaoProvider.instance()`/`OrmTemplate` 新开 session，或经注入的 daoProvider 在提交时捕获）；**cancel 裁定**（`CompletableFuture.cancel(true)` 仅置标志，执行体内轮询中断标志位主动中止，`IThreadPoolExecutor` 无强中断）。
  - [x] 进程重启 running 任务清理裁定（**含触发机制**）：启动时将 running 标记 failed 附 reason——**须裁定触发 bean**（如 `NopDatavExportTaskBizModel` 的 `@PostConstruct`，或实现 `IInitializer`/`IStartupListener`，在 IoC 启动后扫描 running→failed），不静默挂起。
  - [x] 限额裁定（**含行数限额的防 OOM 策略**）：单用户并发任务数（超出发 `ERR_DATAV_EXPORT_CONCURRENCY_LIMIT`）、文件保留期——阈值经 `NopDatavConfigs` 配置。**关键**：`PanelDataBinder.queryPanelData` 当前一次性把结果全量加载到 `List<Map>`（`PanelDataBinder.java:148-155`），"取数后"校验无法防 OOM。须裁定行数限额的执行点：(a) 在 SQL 层加 `LIMIT (maxRows+1)` 探测（改/包装 `PanelSqlBuilder`，超出即 `ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED`，不拉全量），或 (b) 流式写出时计数达到上限即中止。裁定须明确防 OOM 而非仅防文件过大。
  - [x] 文件存储与下载裁定（**含 IFileStore 集成链路**）：生成的文件经 `IFileStore.saveFile(UploadRequestBean, maxLength)` 落盘（`bizObjName=nopDatavExportTask`、`bizObjId=taskId`），返回 fileId 存入任务记录；**UploadRequestBean 构造**（从写出的 IResource 取 inputStream/length/mimeType/fileName）。下载裁定用 `IFileStore.getFile(fileId)→IFileRecord.getResource()` 还是 `getFileLink`，以及 owner 校验落点（仅任务 createdBy 可下载，下载 action 内比对 `task.createdBy == 当前用户`）。
  - [x] 格式裁定：CSV（`CsvResourceRecordIO.openOutput`，UTF-8 + 手写 BOM 兼容 Excel 中文）与 Excel/xlsx（构造 `ExcelWorkbook` 后 `ExcelHelper.saveExcel(IResource, ExcelWorkbook)`）；**columns+rows → ExcelWorkbook 的映射方式**（单 sheet 表头+数据行；看板级多面板：Excel 多 sheet 或多 CSV）在设计内定稿。
  - [x] 看板级导出裁定（**含面板遍历与 needsDataset 判定**）：按 `dashboardId` 经 DAO 查询其下 panel 列表，逐个经 `PanelTypeMapping.toComponentType(panelType)` + `PanelComponentRegistry.requireComponent(ct).getMetadata().isNeedsDataset()` 过滤（仅导出需数据集的面板，跳过 text/iframe/container）。
  - [x] 权限边界裁定：导出 action 在请求线程内对来源 panel/dashboard 经 `requireEntity` → `checkDataAuth`（继承 D3-1 RLS）；**异步执行体不再做 RLS**（来源已在请求线程校验，dashboard 级导出校验的是 dashboard ownership，其下 panel 同属该 dashboard）。下载仅任务 owner。
  - [x] 图像导出 out-of-scope 裁定：注明 PDF/PNG 依赖前端/无头渲染器（flux 未产出），任务对 image 类型显式拒绝，归后继 plan。
  - [x] 管理页可见性裁定：导出任务实体默认暴露标准 CRUD + 管理页（与既有 7 实体同模式，依赖 codegen 生成物），本计划不自定义前端。

Exit Criteria:

- [x] `permission-sharing-design.md` 新增 D3-3 导出章节，全部子项已写最终决策（无 "Proposed"/"待定"）
- [x] 状态机、限额阈值、异步机制、图像导出裁定均有明确结论
- [x] 该 Phase 改变 live baseline（design doc）：`docs-for-ai/` 无需更新（无新平台 API 约定）；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ORM 模型与代码生成（NopDatavExportTask）

Status: completed
Targets: `nop-datav/model/nop-datav.orm.xml`（新增实体）、`nop-datav/nop-datav-dao/_gen/`（生成物）

- Item Types: `Decision | Proof`

- [x] 在 `nop-datav/model/nop-datav.orm.xml` 新增 `NopDatavExportTask` 实体（表 `nop_datav_export_task`），列设计遵循 Phase 1 裁定；按需新增 dict（导出格式 csv/xlsx、导出来源 panel/dashboard、任务状态），复用既有 domains（json-4000/version/createdBy 等）
- [x] `./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 触发 codegen，确认 `_gen` dao/entity/meta/api/beans 已生成（不手改生成物）
- [x] 确认 xmeta（`_gen` 或保留层）暴露该实体的标准 CRUD；导出任务实体的 web 可见性按管理面需要裁定（默认暴露管理页）

Exit Criteria:

- [x] `nop-datav/model/nop-datav.orm.xml` 含 `NopDatavExportTask` 实体（源模型，非生成物）
- [x] `./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 成功，`_gen` 下出现对应 dao/entity/api/beans 生成物
- [x] **无静默跳过**：codegen 生成物存在即可，本 Phase 不涉及运行时分支
- [x] 该 Phase 改变 live baseline（ORM 结构）：属 plan-first 区域，本 plan 即其 plan；`docs-for-ai/` 无需更新（无新平台约定）；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 导出服务实现（取数 + 写出 + 异步任务 + 限额 + 下载）

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/export/`（新建）、`.../entity/NopDatavExportTaskBizModel.java`、`NopDatavConfigs.java`、`NopDatavErrors.java`

- Item Types: `Fix | Decision`

- [x] 新增错误码（`NopDatavErrors.java`）：`ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`、`ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED`、`ERR_DATAV_EXPORT_CONCURRENCY_LIMIT`、`ERR_DATAV_EXPORT_TASK_NOT_FOUND`、`ERR_DATAV_EXPORT_NOT_OWNER`、`ERR_DATAV_EXPORT_FAILED`、`ERR_DATAV_EXPORT_NOT_FINISHED`（下载未完成任务时）；均英文 + `.param(...)`
- [x] **新增 `nop-datav/nop-datav-service/pom.xml` 依赖**：`nop-file-dao`（拉入 `nopFileStore` bean=`DaoResourceFileStore`、`NopFileRecord` 实体、传递 `nop-biz-file-core` 的 `IFileStore`/`IFileRecord`/`UploadRequestBean`）、`nop-format/nop-excel`、`nop-format/nop-ooxml/nop-ooxml-xlsx`（`ExcelHelper.saveExcel`）。`./mvnw compile -pl nop-datav/nop-datav-service -am` 通过
- [x] 新增 `NopDatavConfigs` 限额项（最大行数、并发任务数、保留期）
- [x] 实现 `PanelDataExporter`：复用 `PanelDataBinder.queryPanelData` 取数 → 按 Phase 1 行数限额裁定执行防 OOM 校验（SQL LIMIT 探测 或 流式计数中止）→ CSV（`CsvResourceRecordIO.openOutput` + 手写 UTF-8 BOM）或 xlsx（构造 `ExcelWorkbook` → `ExcelHelper.saveExcel(IResource, ExcelWorkbook)`）写出临时 IResource → 经 `IFileStore.saveFile(UploadRequestBean, maxLength)` 落盘为 `NopFileRecord`（`bizObjName=nopDatavExportTask`），返回 fileId；看板级按 Phase 1 裁定遍历 needsDataset 面板
- [x] 实现 `NopDatavExportTaskBizModel`（`@BizModel("NopDatavExportTask")`）：
  - [x] `createExportTask(sourceType, sourceId, format, params, context)`（`@BizMutation` + `@Auth`）：经 `requireEntity` 对来源 panel/dashboard 做行级校验 → 并发限额校验 → INSERT 任务(pending) → 提交平台执行器异步执行 → 返回 taskId
  - [x] `getExportTask(taskId, context)`（`@BizQuery`）：返回任务状态 + 文件信息（owner 校验）
  - [x] `cancelExportTask(taskId, context)`（`@BizMutation`）：owner 校验 → 置 cancelled（运行中则标记取消，执行体轮询中断标志位中止）
  - [x] `downloadExportFile(taskId, context)`（`@BizQuery`）：owner 校验（`task.createdBy == 当前用户`，非 owner 抛 `ERR_DATAV_EXPORT_NOT_OWNER`）+ 任务 succeeded 校验 → 经 `IFileStore.getFile(fileId)→IFileRecord.getResource()` 返回文件
  - [x] 后台执行体：按 Phase 1 线程模型裁定获取 daoProvider/session，running→取数写出→succeeded(记 fileId/rowCount)/failed(记 errorMsg)；异常捕获记 failed（不吞异常）；按裁定轮询 cancel 标志位
- [x] 图像格式（pdf/png）请求在 `createExportTask` 显式抛 `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`（rule #24，不静默跳过）
- [x] 进程重启 running 任务清理：按 Phase 1 裁定的触发 bean（`@PostConstruct`/`IInitializer`）在启动时扫描 running→failed 附 reason，不静默挂起
- [x] 导出任务实体经 codegen 暴露标准 CRUD + 管理页（默认暴露，与既有 7 实体同模式）；本计划不为管理页写自定义前端，仅依赖 codegen 生成物

Exit Criteria:

- [x] `createExportTask` 对 panel/dashboard 来源经 `requireEntity` → `checkDataAuth`（继承 D3-1 RLS），无权用户被拒
- [x] **pom 依赖已新增**（nop-file-dao / nop-excel / nop-ooxml-xlsx），`./mvnw compile -pl nop-datav/nop-datav-service -am` 通过（IFileStore/ExcelHelper 等可正常 import）
- [x] **接线验证**：`createExportTask` 内部确实调用 `PanelDataBinder.queryPanelData`（运行时调用连通，非仅类型存在）——由端到端测试断言取数发生；`IFileStore.saveFile` 确实被调用（断言 NopFileRecord 落库）
- [x] **无静默跳过**：image 格式请求显式抛异常；行数/并发超限快速失败；后台异常记 failed 非吞掉；新增公共方法无空方法体/continue/吞异常
- [x] CSV 与 xlsx 两种格式均能生成有效文件（见 Phase 4 测试）
- [x] 状态机迁移 pending→running→succeeded/failed/cancelled 均持久化到任务记录
- [x] 该 Phase 改变 live baseline（行为/API）：在 `permission-sharing-design.md`（Phase 1）已覆盖设计；`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试与端到端验证

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/`

- Item Types: `Proof`

- [x] 单元测试 `PanelDataExporter`：CSV 与 xlsx 各一例，断言生成文件含期望列与行（复用 AutoTest 数据集 fixture）
- [x] 单元测试 限额：超行数 → `ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED`；超并发 → `ERR_DATAV_EXPORT_CONCURRENCY_LIMIT`；image 格式 → `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`
- [x] 单元测试 owner/权限：非 owner 下载 → `ERR_DATAV_EXPORT_NOT_OWNER`；下载未完成任务 → `ERR_DATAV_EXPORT_NOT_FINISHED`
- [x] 端到端测试（rule #22）：`createExportTask(panel, csv)` → 轮询 `getExportTask` 至 succeeded（带超时，非盲等）→ `downloadExportFile` → 断言文件内容与 `getPanelData` 同源数据一致；dashboard 级多面板导出一例（xlsx 多 sheet 或多 CSV）
- [x] 状态机测试：failed 路径（构造查询失败，断言任务记 failed + errorMsg）；cancelled 路径
- [x] 测试基建：复用 D3 鉴权测试配置（`nop-auth-service` test 依赖 + action-auth/data-auth 路径）；异步轮询用带超时 poll，不用 `Thread.sleep` 盲等

Exit Criteria:

- [x] 新增导出功能（Exporter / 4 个 action / 状态机 / 限额 / image 拒绝）每个均有对应测试（rule #25）
- [x] **端到端验证**：从 `createExportTask` 入口到 `downloadExportFile` 输出文件完整链路跑通，断言文件内容正确
- [x] **接线验证**：端到端测试断言 `PanelDataBinder.queryPanelData` 被实际调用（如对取数计数/标志位断言）
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过
- [x] 该 Phase 不改变 live baseline；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 面板/看板数据导出（CSV + xlsx）异步可用，状态机完整
- [x] 限额（行数/并发）超限快速失败，不静默截断
- [x] 图像导出（PDF/PNG）显式 out-of-scope，请求时显式拒绝（非空壳）
- [x] 导出入口继承 D3-1 权限，下载仅 owner
- [x] 端到端测试（创建→轮询→下载→内容校验）通过
- [x] `permission-sharing-design.md` D3-3 章节与 live 实现一致
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 createExportTask→PanelDataBinder→写出→nop-file 调用链运行时连通，无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 图像导出 PDF/PNG

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 图像导出需已渲染的看板/图表快照，依赖前端或无头浏览器渲染器（nop-chaos-flux 未产出，与 D1-4/D2-4 同类阻塞）。后端无可复用渲染内核（`nop-report-pdf` 是 XPT 表格渲染，非图表图像渲染）。数据导出（CSV/Excel）可独立成立，不阻塞 D3-3 数据导出 closure。请求 image 类型时显式拒绝（非静默跳过）。
- Successor Required: `yes`
- Successor Path: 渲染能力（flux 大屏/看板截图 或 服务端无头渲染）落地后的后继导出 plan

## Non-Blocking Follow-ups

- 导出文件的定时清理（按保留期清理过期 NopFileRecord + 任务记录）——可由运维或后继治理项实现，非 closure 阻塞
- 导出任务进度百分比（当前仅状态机，无分片进度）——优化项
- 看板级导出的批量面板查询优化（逐面板取数，可批量化）——与 D1 deferred 的批量查询 follow-up 同类

## Closure

Status Note: D3-3 数据导出（CSV/xlsx 异步任务 + 限额 + owner-only 下载）已全部 landing 并经独立 closure audit 复核。所有 Phase (1-4) 的 Exit Criteria 已逐条对 live 代码与测试验证 PASS；Closure Gates 12 项均通过；唯一延后的「图像导出 PDF/PNG」属 out-of-scope（渲染能力缺失，非 in-scope live defect），且已显式快速失败（抛 `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`），无空壳/静默跳过。Plan 可关闭。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor subagent (mission-driver closure-audit, fresh session, different task_id from implementation)
- Audit Session: MISSION_DRIVER:2026-08-09-225537-mission-driver closure-audit pass
- Evidence:
  - **Phase 1 (设计文档)** PASS：`ai-dev/design/nop-datav/permission-sharing-design.md` 第 8/277/370/389 行确认含 D3-3 章节（实体列约定、状态机、异步执行、限额、文件存储、图像导出 out-of-scope 裁定），状态 `final`，无 "Proposed/待定" 残留。
  - **Phase 2 (ORM/Codegen)** PASS：`nop-datav/model/nop-datav.orm.xml:512` 含 `NopDatavExportTask` 实体定义（源模型，非生成物）；codegen 产物在 `nop-datav/nop-datav-dao/_gen/` 已生成（`./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 通过，见实施日志）。
  - **Phase 3 (服务实现)** PASS：
    - 错误码：`nop-datav-service/.../NopDatavErrors.java:199-241` 含 8 个 `ERR_DATAV_EXPORT_*`（含 type/row/concurrency/not-owner/not-finished/failed/no-exportable-panels/task-not-found）。
    - pom 依赖：`nop-datav-service/pom.xml:48/53/58` 已新增 `nop-file-dao` / `nop-excel` / `nop-ooxml-xlsx`。
    - `PanelDataExporter.java:83/131` 确认调用 `PanelDataBinder.queryPanelData(panelId, panel, params, maxRows+1)` —— 接线连通（行数限额防 OOM 经 `maxRows+1` 探测）。
    - `NopDatavExportTaskBizModel.java:115-178` 含 4 个 action（createExportTask/getExportTask/cancelExportTask/downloadExportFile），均带 `@Auth(permissions="NopDatavExportTask:*")`；`:185` 经 `fileStore.getFile` 取下载文件，`:273` 经 `fileStore.saveFile(bean, CFG_DATAV_EXPORT_FILE_MAX_LENGTH.get())` 落盘 —— nop-file 调用链运行时连通。
    - `NopDatavExportTaskRecovery.java`（启动时清理 running→failed）存在，对应 Phase 1 进程重启裁定。
    - `NopDatavExportTaskStatus.java` 状态机枚举存在。
  - **Phase 4 (测试)** PASS：`TestPanelDataExporter.java`（CSV/xlsx/限额/type/owner 等 6+ @Test）、`TestNopDatavExportE2E.java`（端到端：createExportTask→downloadExportFile 全链路 + 图像拒绝 + 并发限额 + owner 校验 + 任务失败路径）均存在；端到端测试断言文件内容与 `getPanelData` 同源（rule #22 端到端 + rule #23 接线验证同时满足）。
  - **Closure Gates** 全 12 项 PASS：见上方逐条勾选，每条均有对应 live 代码或测试证据。
  - **`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`**：修复后退出码为 0（无未勾选项 + Closure Evidence 已写入）。
  - **Anti-Hollow 检查**：经代码追踪，运行时调用链 `NopDatavExportTaskBizModel.createExportTask` → `PanelDataExporter.exportPanel/exportDashboard` → `PanelDataBinder.queryPanelData` → `CsvResourceRecordIO.openOutput` / `ExcelHelper.saveExcel` → `IFileStore.saveFile`（落盘 NopFileRecord）→ `downloadExportFile` → `IFileStore.getFile → IFileRecord.getResource()` 完整连通，无空方法体、无 `continue` 静默跳过、无吞异常；图像格式经显式抛 `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`（rule #24）。
  - **Deferred 项分类检查**：唯一 deferred 项「图像导出 PDF/PNG」确为 `out-of-scope improvement`（依赖未产出的前端/无头渲染器），非 in-scope live defect 降级；Non-Blocking Follow-ups 三项均为优化项（定时清理/进度百分比/批量查询），非缺陷。
  - **`ai-dev/logs/2026/08-10.md`** 已记录 D3-3 实施与收口（实施侧已记录，见当日日志条目）。

Follow-up:

- 图像导出（PDF/PNG）归后继 plan，待 flux 大屏/看板截图或服务端无头渲染能力落地后启动（已记入 `Deferred But Adjudicated`）。
- 导出文件定时清理 / 进度百分比 / 看板级批量查询优化（Non-Blocking Follow-ups，不阻塞 closure）。
