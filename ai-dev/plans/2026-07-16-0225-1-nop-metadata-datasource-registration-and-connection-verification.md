# 1 nop-metadata 外部数据源注册 + 连接验证

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（P2-1）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.2 数据源
> Mission: nop-metadata
> Work Item: P2-1 — MetaDataSource CRUD + 连接验证
> Related: `292-nop-metadata-implementation-roadmap.md`、`294-nop-metadata-import-engine-completeness.md`、`295-nop-metadata-delta-expansion-and-version-release.md`（前置 Phase 1 / P1+ 已 done）

## Purpose

把 nop-metadata 的外部数据源从"只有 CRUD 表结构"推进到"可注册、可验证连通性、可被后续外部表同步/质量执行复用"。本 plan 是 P2 的基础设施层：连通性验证是 P2-2（外部表同步）、P2-4（Catalog 运行时收集）、P2-6（质量规则执行）、P2-7（数据剖析）共同依赖的前置能力。

## Current Baseline

- **NopMetaDataSource 实体已建模**（`nop-metadata/model/nop-metadata.orm.xml:201-246`）：字段 `dataSourceId`(PK) / `querySpace`(唯一) / `name` / `displayName` / `datasourceType`(dict `meta/datasource-type`：`jdbc`/`http`/`rest`/`file`) / `connectionConfig`(domain `json-4000`，自由 JSON) / `status`(dict `meta/datasource-status`：`DISABLED`/`ACTIVE`) + 审计字段。唯一键 `UK_NOP_META_DS_QUERY_SPACE` 在 `querySpace` 上。
- **CRUD 已自动暴露**：`NopMetaDataSourceBizModel`（`nop-metadata-service/.../entity/NopMetaDataSourceBizModel.java`）存在，通过 CrudBizModel 自动生成 findPage/findList/get/save/delete。**无任何自定义 action**。
- **无连通性验证**：不存在 `testConnection` / `verifyConnection` 类 action；`connectionConfig` 是自由 JSON，不按 `datasourceType` 校验必填字段；**无任何代码从 `connectionConfig` 构建真实 JDBC 连接**。
- **无方言检测**：注册数据源时不识别目标库类型（MySQL/PostgreSQL/ClickHouse 等），后续同步/收集无方言依据。
- **设计契约**：`01-architecture-baseline.md` §2.2 明确 MetaDataSource 是"纯元数据用途：描述数据源信息，不负责运行时查询路由（ORM 已承担此职责）"。即连接验证是"按需建连验证后即释放"，不注册长期连接池到 ORM 路由。
- **平台依赖**：`nop-metadata-dao` 依赖 `nop-orm`（`pom.xml:22`）。可用的 JDBC 建连 / 元数据 API 需在 item 1.1 研究确认（候选：JDK `DriverManager`、平台 `nop-orm` 内的 dialect/connection 工具）。
- **测试基建**：`TestNopMetaModuleBizModel`（`nop-metadata-service/.../test/.../TestNopMetaModuleBizModel.java`）使用 Nop AutoTest，测试用数据源可用。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- `testConnection` GraphQL mutation action 可用：按 `dataSourceId` 加载数据源 → 从 `connectionConfig` 构建连接 → 实际打开并释放 → 返回连通结果（成功/失败 + 数据库产品名/版本等基本元数据）
- `connectionConfig` 按 `datasourceType` 有明确的必填字段约定（至少 jdbc 类型：jdbcUrl / username / password），并在 action 入口做基本校验，非法配置快速失败而非静默
- `testConnection` 成功时识别目标库方言（至少区分 MySQL / PostgreSQL）并放入返回 Map（**不持久化**到任何 ORM 列；后续外部表同步由其自身运行时获取，不依赖本 plan 写入）
- 状态管理：`ACTIVE`/`DISABLED` 通过标准 CRUD 即可流转；`testConnection` 对 DISABLED 数据源显式拒绝（快速失败）

## Non-Goals

- 外部表结构扫描 / 同步（P2-2，本 plan 的 successor `2026-07-16-0225-2-...`）
- Manifest 快照 / Catalog 运行时收集（P2-3 / P2-4）
- 血缘采集 / 质量规则执行 / 数据剖析（P2-5 / P2-6 / P2-7）
- 长期连接池注册到 ORM querySpace 路由（架构基线 §2.2 明确拒绝，连接验证是按需建连）
- 连接密码加密存储 / 密钥管理（见 Design Decision D2，首版处理策略见 Decision，加密体系为 non-blocking follow-up）
- HTTP/REST/File 类型数据源的连通性验证（首版只实现 jdbc 类型；非 jdbc 类型 `testConnection` 显式抛 `UnsupportedOperationException`，不静默返回成功）

## Design Decisions

> 本 plan 落地以下决策；其中 D2/D3 涉及行为契约，须同步写入 `01-architecture-baseline.md` §2.2。

### D1. testConnection action 契约

- **签名**：`@BizMutation testConnection(@Name("dataSourceId") String id, IServiceContext context)` → 返回 `Map<String,Object>`（`{connected: boolean, databaseProductName: string, databaseProductVersion: string, driverName: string, error: string(失败时)}`）
- **行为**：加载 NopMetaDataSource → 校验 status != DISABLED（DISABLED 抛异常）→ 按 datasourceType 分派 → jdbc：解析 connectionConfig → 建连 → 读 `DatabaseMetaData` → 关闭 → 返回。失败 catch 后返回 `connected=false` + `error`（不向上抛，使 GraphQL 调用方能拿到结构化失败结果，而非异常中断）
- **仅支持 jdbc**：非 jdbc 类型（http/rest/file）抛 `UnsupportedOperationException("testConnection not yet implemented for datasourceType: ...")`，不静默成功

### D2. connectionConfig 约定（jdbc）

- jdbc 类型必填：`jdbcUrl`、`username`、`password`。可选：`driverClassName`（不提供时由 `DriverManager` 按 url 自动匹配）
- 首版 password 以**明文**存于 `connectionConfig` JSON（与现有 `connectionConfig` free-form 存储一致）。密码加密/脱敏为独立 follow-up（见 Non-Goals），不在本 plan 引入加密体系以免范围蔓延
- 入口校验：缺必填字段时抛 inline `ErrorCode`（`metadata.datasource-config-invalid`），快速失败

### D3. 方言识别（不持久化）

- 方言**不在 testConnection 中持久化**：成功时从 `DatabaseMetaData.getDatabaseProductName()` 识别方言，**仅放入返回 Map**（不写任何 ORM 列）。
- 后续 P2-2 同步需要方言时，由 P2-2 在建连后**自行**调用 `DatabaseMetaData.getDatabaseProductName()` 运行时获取（不依赖本 plan 写入的任何字段）。

## Scope

### In Scope

- `NopMetaDataSourceBizModel`：新增 `testConnection` action（jdbc）
- 新增连接服务（callback 式建连 / 元数据读取 / 释放，按需建连不池化），落点（service/dao 层）由 item 1.1 裁定
- `connectionConfig` jdbc 必填字段校验 + 缺失快速失败
- `testConnection` 对 DISABLED 数据源拒绝、对非 jdbc 类型显式 `UnsupportedOperationException`
- AutoTest：覆盖成功连通 + DISABLED 拒绝 + 非 jdbc 显式失败 + 配置缺失快速失败

### Out Of Scope

- 外部表同步（P2-2）
- Catalog/Manifest（P2-3/P2-4）
- 连接池化 / ORM 路由注册（架构拒绝）
- 密码加密存储（follow-up）
- http/rest/file 连通性验证（首版仅 jdbc）

## Execution Plan

### Phase 1 - 连接服务 + testConnection action（jdbc）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java`、新增连接服务类、`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaDataSourceBizModel.java`

- Item Types: `Proof`（新功能：连通性验证）+ `Decision`（API/建连方案裁定）

> **硬前置门禁（item 1.1）**：可用的 JDBC 建连 / 元数据 API 需先研究确认。若研究表明平台已有按需建连工具则复用；否则用 JDK `DriverManager`。研究结论写入 item 1.1 后再实现 1.2+。

- [x] 1.1 **API 研究（硬前置门禁）**：确认从 `connectionConfig` 构建按需 JDBC 连接的可用 API。候选：JDK `DriverManager.getConnection(url, user, pass)`；平台 `nop-orm` 内 dialect/connection 工具。**连接服务接口形态裁定**：连接服务需同时服务本 plan（testConnection 一次性读元数据）和 P2-2/P2-4/P2-6（"open → 执行 N 条查询 → close"），故服务须提供 **callback 式接口**（如 `withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData>)`），而非只返回单次结果。**连接服务落点裁定**：在 service 层还是 dao 层（研究结论一并裁定）。**测试连通目标库裁定**：确认 AutoTest 测试库类型与连接信息来源（查看测试用的 `@NopTestConfig`/数据源配置）；**fallback**：若 AutoTest 环境无法提供稳定可连通的外部 JDBC 目标，成功路径测试改用 **H2 内存库**（`jdbc:h2:mem:meta_test`）动态构造一条 connectionConfig 记录来验证真实建连 + DatabaseMetaData 读取。研究结论记录在本 plan 或对应 daily log
- [x] 1.2 新增连接服务：提供 **callback 式接口**（item 1.1 裁定）`withConnection(datasourceType, connectionConfig, BiConsumer<Connection, DatabaseMetaData> action)`——在内部建连、执行 action、finally 关闭。另提供便捷方法 `testConnect(...)`（内部用 callback 读 DatabaseMetaData 返回结果）供 testConnection 使用。jdbc 类型解析 jdbcUrl/username/password → 建连；非 jdbc 抛 `UnsupportedOperationException`；缺必填字段抛 inline `ErrorCode.define("metadata.datasource-config-invalid", ...)`
- [x] 1.3 在 `NopMetaDataSourceBizModel` 新增 `@BizMutation testConnection(@Name("dataSourceId") String id, IServiceContext context)`，实现 Design Decision D1：按 id 加载实体 → **实体不存在抛 inline ErrorCode `metadata.datasource-not-found`（参考 `NopMetaModuleBizModel.releaseModule` 的 null 检查模式，不 NPE）** → 校验 status != DISABLED（DISABLED 抛 inline ErrorCode `metadata.datasource-disabled`）→ 调连接服务 testConnect → 成功返回 `Map{connected:true, databaseProductName, ...}`，失败 catch 返回 `Map{connected:false, error:msg}`
- [x] 1.4 方言信息：成功时从 DatabaseMetaData 识别 productName，放入返回 Map（不写 ORM 列，见 D3）
- [x] 1.5 错误码按现有模式在 BizModel 内 inline 定义（参考 `NopMetaModuleBizModel` 内 inline ErrorCode 用法）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `testConnection` 可通过 GraphQL mutation 调用，对 jdbc 数据源返回 `connected=true` + 数据库产品名/版本
- [x] 对不存在的 dataSourceId 调用 `testConnection` 抛 `metadata.datasource-not-found`（不 NPE）
- [x] 对 DISABLED 数据源调用 `testConnection` 抛异常（不静默通过）
- [x] 对非 jdbc 类型（http/rest/file）调用 `testConnection` 抛 `UnsupportedOperationException`（不静默返回成功）
- [x] connectionConfig 缺 jdbc 必填字段（jdbcUrl/username/password）时抛 `metadata.datasource-config-invalid`（快速失败）
- [x] 方言信息仅出现在成功返回 Map 中，**testConnection 不回写任何 ORM 列 / extConfig**（与 D3 一致）
- [x] **端到端验证**：从 GraphQL mutation 入口 → BizModel → 连接服务 → 真实建连 → DatabaseMetaData → 返回 Map 的完整路径已验证
- [x] **接线验证**：BizModel 确实调用了连接服务，且连接服务运行时确实打开了真实连接并读取了 DatabaseMetaData（返回 Map 含真实 productName 证明），而非空壳
- [x] **无静默跳过**：非 jdbc / DISABLED / 配置缺失 / dataSourceId 不存在 四条路径均显式失败，无空方法体或吞异常返回成功
- [x] **新功能测试**：新增测试覆盖 成功连通（真实库或 H2 内存库 fallback）+ dataSourceId 不存在 + DISABLED 拒绝 + 非 jdbc 显式失败 + 配置缺失快速失败，全绿
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.2 补充 testConnection 契约 + connectionConfig jdbc 必填字段约定 + 方言识别说明（按 D1/D2/D3）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] testConnection（jdbc）端到端可用（GraphQL 入口 → 真实建连 → 元数据返回）
- [x] dataSourceId 不存在 / DISABLED / 非 jdbc / 配置缺失 四条非法路径均显式快速失败
- [x] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常返回成功）
- [x] 必要 focused verification 已完成（5 类测试路径全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.2 testConnection 契约）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 testConnection 运行时确实建连并读取元数据（非空壳）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0225-1-nop-metadata-datasource-registration-and-connection-verification.md --strict` 退出码 0

## Deferred But Adjudicated

### 连接密码加密存储

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版 connectionConfig 与现有 free-form JSON 存储一致（明文）。连接验证的核心行为（建连 + 读元数据）不依赖加密。引入加密体系需密钥管理设计，超出本 plan 结果面
- Successor Required: no

## Non-Blocking Follow-ups

- http/rest/file 类型数据源的连通性验证（当前显式抛 UnsupportedOperationException）
- 连接密码加密 / 脱敏 / 密钥管理
- 连接服务 callback 接口在 P2-2/P2-4/P2-6 的复用验证（本 plan 仅 testConnection 路径用到）

## Closure

Status Note: P2-1 完成。新增 callback 式连接服务 `MetaDataSourceConnectionService`（service 层，`withConnection` + `testConnect`）+ `NopMetaDataSourceBizModel.testConnection` GraphQL mutation（jdbc）。落点 decision：连接 API 用平台 `SimpleDataSource`（`nop-dao`，内部 `DriverManager.getConnection`，不注册 ORM 路由），service 层 package `io.nop.metadata.service.connection`，bean 注册在源 `app-service.beans.xml`。设计决策 D1/D2/D3 已写入 `01-architecture-baseline.md` §2.2.1。方言不持久化（D3），后续 P2-2 自行运行时获取。5 类测试路径全绿（成功路径用真实 H2 建连，断言 `databaseProductName=H2` 证明非空壳）。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（ses_098d76f8fffew6G675zaX1sI74，独立 session，非 plan 执行者）
- Evidence: 全部 9 项目标 PASS，0 defect。逐条证据：
  1. `testConnection` 为 `@BizMutation`（`NopMetaDataSourceBizModel.java:50-63`）→ `MetaDataSourceConnectionService.testConnect` 真实建连 + 读 `getDatabaseProductName/Version`（`MetaDataSourceConnectionService.java:69-79`），测试断言 `connected=true` + `databaseProductName=H2`（`TestNopMetaDataSourceBizModel.java:62-68`）。
  2. 不存在 ID：`BizModel.java:53-55` null-check 抛 `metadata.datasource-not-found`，无 NPE。
  3. DISABLED：`BizModel.java:57-60` 抛 `metadata.datasource-disabled`，不静默通过。
  4. 非 jdbc：`ConnectionService.java:114-119` `requireJdbcType()` 抛 `UnsupportedOperationException`。
  5. 缺必填字段：`ConnectionService.java:94-101` 抛 `metadata.datasource-config-invalid` 快速失败。
  6. **D3 确认**：`testConnection`/`testConnect` 零回写——无 `set/save/update` 任何 ORM 列/extConfig，方言仅在返回 `LinkedHashMap`。
  7. callback `withConnection(type, config, BiConsumer<Connection, DatabaseMetaData>)` 已声明+实现（`ConnectionService.java:48-61`），bean 注册 `app-service.beans.xml:13-14`。
  8. **Anti-Hollow 确认**：`testConnect` 调 `dataSource.getConnection()` → `conn.getMetaData()` → 读 4 个真实元数据字段 → `finally safeCloseObject(conn)`；底层 `SimpleDataSource.java:49` 走真实 `DriverManager.getConnection`。非 stub。
  9. 5 个 `@Test` 覆盖全部路径（成功 + 4 失败），构建报告 Tests run: 5, Failures: 0, Errors: 0。
  10. 文档同步：`01-architecture-baseline.md` §2.2.1（lines 90-113）记录 D1/D2/D3 契约。
- Build/gate：`./mvnw clean install -pl nop-metadata -am -T 1C` BUILD SUCCESS；`scan-hollow-implementations.mjs --severity high` 0 findings；`check-plan-checklist.mjs --strict` exit 0。

Follow-up:

- http/rest/file 类型数据源的连通性验证（当前显式抛 UnsupportedOperationException）
- 连接密码加密 / 脱敏 / 密钥管理
- 连接服务 callback 接口在 P2-2/P2-4/P2-6 的复用验证（本 plan 仅 testConnection 路径用到）
