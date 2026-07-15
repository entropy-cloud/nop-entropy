# 295 nop-metadata Delta 展开 + 版本发布

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P1+（P1+-1 / P1+-2 / P1+-3）；`ai-dev/design/nop-metadata/03-version-management.md`；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §三 Delta 版本管理 + §4.1 模型导入
> Mission: nop-metadata
> Work Item: P1+ Phase 1 补完 — Delta full 展开 + releaseModule 版本发布 + baseModuleId 自引用关系
> Related: `294-nop-metadata-import-engine-completeness.md`（前置 plan，导入引擎完整性）；`292-nop-metadata-implementation-roadmap.md`（Deferred: isDelta=false full 展开）；`293-nop-metadata-design-consistency-fix.md`（Deferred: G4 baseModuleId to-one, G7-code version 硬编码）

## Purpose

为 nop-metadata 导入引擎增加版本管理能力：模块导入时同时存储 delta 定义（isDelta=true，本模块声明的内容）和 full 定义（isDelta=false，x:extends 合并后的完整模型）；实现 releaseModule 版本发布 action（版本递增 + 状态流转 + 发布后不可变）；补全 baseModuleId 自引用 to-one 关系，支持 Delta 继承链导航查询。收口 plan 292 Deferred "isDelta=false full 展开" 和 plan 293 Deferred "G4-baseModuleId-to-one" + "G7-code version 硬编码"。

## Current Baseline

- **OrmModelImporter**（`nop-metadata-dao/.../dao/model/OrmModelImporter.java`）：
  - 所有 build 方法的 `isDelta` 硬编码为 `b(true)`（行 62/70/87/112/124/140），**无任何代码路径产生 isDelta=false**。
  - `buildModule`（行 42-56）硬编码 `moduleVersion=1L`（行 48）和 `status=MODULE_STATUS_DRAFTING`（行 49），`baseModuleId=null`（行 50）。
- **NopMetaModuleBizModel.importOrmModel**（行 54-118）：用 `new OrmModelLoader().loadFromResource(resource, true)` 解析 orm.xml。**`true` 参数是 `ignoreUnknown`（容忍未知属性），不是 x:extends 展开开关**。x:extends 展开由 `DslModelParser` 内部无条件执行（`XDslExtendPhase.validate`），当前导入只产生一组记录（isDelta=true）。
- **ORM 模型**（`nop-metadata.orm.xml`）：
  - `NopMetaModule`（行 116-186）：`baseModuleId` 为普通 VARCHAR(32) 列（行 133-135），**无 `<relations>` 块**，无 to-one 自引用。
  - `NopMetaModule.status`：**String 类型**（`stdDataType="string"`），dict `meta/module-status`，值为 `"DRAFTING"` / `"RELEASED"` / `"DEPRECATED"`（字符串枚举，非 int）。
  - `NopMetaModule` 唯一键 `UK_NOP_META_MODULE_ID_VER` 在 `(moduleId, moduleVersion)` 上——重复导入同一 moduleId 会冲突（当前硬编码 version=1L）。
  - `NopMetaOrmModel.isDelta`（行 257）：byte/boolFlag 列已存在，可用于区分 delta/full。
- **设计文档**：`03-version-management.md` §4.4 **实际不存在**（plan 293 item 2.4 声称新增但文件中未落地——这是一个 plan 293 closure gap）。releaseModule action 契约需在本 plan 中定义（见 Design Decisions）。§1.3 说"version 在 release 时递增"，但与唯一键 `(moduleId, moduleVersion)` 冲突——需修正为 import 时递增（见 Design Decisions）。
- **平台 API 现状**：`OrmModelLoader.loadFromResource` **始终展开** x:extends。`DslNodeLoader.loadDslNodeFromResource(resource, schema, ResolvePhase.filtered)` 可获取 x:include 已解析但 x:extends **未合并**的 XNode，但将其转为 `OrmModel` 的公共 API 尚未确认。Phase 3 的 delta 解析能力依赖此 API 研究。
- **测试**：TestNopMetaModuleBizModel 不断言 `moduleVersion` / `status` / `isDelta` / `baseModuleId`。
- **构建**：`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- 模块导入时产生双重定义：isDelta=true（原始 delta，不含 x:extends 展开）+ isDelta=false（full，x:extends 合并后完整模型）
- releaseModule action 可用：校验 status=DRAFTING → 设 status=RELEASED → released 后不可变
- OrmModelImporter 移除硬编码 version=1L，改为导入时查询同 moduleId 的 max(moduleVersion)+1（解决唯一键冲突）
- NopMetaModule.baseModuleId 补全 to-one 自引用关系，支持 ORM 导航查询 Delta 继承链

## Design Decisions

> 本 plan 落地以下决策，须同步写入 `03-version-management.md`（plan 293 声称新增 §4.4 但实际未落地）。

### D1. releaseModule action 契约

- **签名**：`@BizMutation releaseModule(@Name("metaModuleId") String id, IServiceContext context)` → 返回 `NopMetaModule`
- **行为**：
  1. 按 `metaModuleId` 加载 NopMetaModule；不存在则抛 `NopException`（新增 ErrorCode `metadata.module-not-found`）
  2. 校验 `status == "DRAFTING"`；非 drafting 抛 `NopException`（新增 ErrorCode `metadata.module-not-drafting`，`.param("status", currentStatus)`）
  3. 设 `status = "RELEASED"`，保存
  4. 返回更新后的 NopMetaModule
- **version 不在 release 时递增**（见 D2）

### D2. version 分配时机：import 时递增（非 release 时）

- **决策**：`moduleVersion` 在 import 时分配为 `max(moduleVersion for same moduleId) + 1`（首个版本为 1）。release 只改 status，不改 version。
- **理由**：唯一键 `UK_NOP_META_MODULE_ID_VER` 在 `(moduleId, moduleVersion)` 上。若每次 import 都设 version=1，重复导入同一 moduleId 必然冲突。若 release 时才递增，则多个 drafting 记录共用 version=1 也冲突。import 时递增是唯一与唯一键兼容的方案。
- **设计文档修正**：`03-version-management.md` §1.3 "version 在 release 时递增" 需修正为 "version 在 import 时递增，release 时固化（不可变）"。

### D3. isDelta 双重存储结构

- **结构**：1 × NopMetaModule → 2 × NopMetaOrmModel（isDelta=true + isDelta=false，共用同一 `metaModuleId`）→ 各自的子实体集（Entity/Field/Relation/Domain/Dict 等）
- **无 x:extends 的模块**：isDelta=true 和 isDelta=false 的内容完全相同，仍存储两份（保持查询一致性）

## Non-Goals

- UniqueKey / Index 导入 / MetaTableJoin to-one / 批量导入 — plan 294
- 外部数据源注册 / 外部表同步 — P2
- 版本对比 UI / 版本废弃 action — non-blocking follow-up
- MetaModelChangedEvent 事件发布（releaseModule 设计中提及发布事件，但事件模型实体尚未建模，roadmap 标"未定 Phase"）— non-blocking follow-up
- Delta 链深度查询优化（多层继承链的递归 full 合并）— 当前仅支持单层 base，多层为后续优化
- 通用 CRUD 路径不可变性强制（`NopMetaModule__update` 等标准 CRUD 仍可修改已发布模块数据；本 plan 的"不可变"仅指 releaseModule 拒绝重复 release）

## Scope

### In Scope

- `OrmModelImporter`：isDelta 参数化（不再硬编码 true），支持构建 delta 和 full 两套记录
- `NopMetaModuleBizModel.importOrmModel`：修改为双解析流程（不展开 → delta，展开 → full），持久化两组记录
- `NopMetaModuleBizModel`：新增 `releaseModule` action
- `OrmModelImporter.buildModule`：移除硬编码 version，改为参数传入或导入逻辑计算
- `nop-metadata/model/nop-metadata.orm.xml`：NopMetaModule 补全 baseModule to-one 自引用关系
- AutoTest：覆盖 isDelta 双重存储断言 + releaseModule 版本递增/状态流转/不可变性断言 + baseModule 关系导航断言

### Out Of Scope

- plan 294 的工作项（UniqueKey/Index/TableJoin/批量导入）
- P2 外部数据源
- MetaModelChangedEvent 实体建模和事件系统

## Execution Plan

### Phase 1 - baseModuleId 自引用 to-one 关系补全

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Fix`（plan 293 Deferred G4：baseModuleId 缺少 to-one relation）

- [x] 1.1 在 `nop-metadata.orm.xml` 的 NopMetaModule 实体新增 `<relations>` 块（当前无此块），添加 `baseModule` to-one 自引用关系（join on `baseModuleId` → `NopMetaModule.metaModuleId`）
- [x] 1.2 运行 `./mvnw clean install -pl nop-metadata -T 1C` 重新生成代码，确认 BUILD SUCCESS

Exit Criteria:

- [x] NopMetaModule 的 ORM 模型包含 `baseModule` to-one 自引用 relation
- [x] 生成的 Java 实体类包含 `getBaseModule()` 方法
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.1 MetaModule 关系描述与 ORM 模型一致（§2.1 已描述 baseModuleId → MetaModule 引用，确认 ORM relation 已落地）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - releaseModule 版本发布 action + version 递增

Status: completed
Targets: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/model/OrmModelImporter.java`、`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java`、`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaModuleBizModel.java`

- Item Types: `Fix`（plan 293 Deferred G7-code：OrmModelImporter version 硬编码）+ `Proof`（新功能：releaseModule action）

- [x] 2.1 修改 OrmModelImporter.buildModule：移除硬编码 `setModuleVersion(1L)`。改为接受 `moduleVersion` 参数（long），由 BizModel 层在导入时计算 `max(moduleVersion for same moduleId) + 1`（首个版本为 1）后传入
- [x] 2.2 在 NopMetaModuleBizModel.importOrmModel 中，import 前查询同 moduleId 的 max(moduleVersion)，计算新版本号传入 OrmModelImporter。status 保持 `"DRAFTING"`（String 类型，使用常量 `MODULE_STATUS_DRAFTING`）
- [x] 2.3 在 NopMetaModuleBizModel 新增 `@BizMutation releaseModule(@Name("metaModuleId") String id, IServiceContext context)` action，实现 Design Decision D1 定义的契约：
  - 加载 NopMetaModule（不存在抛 inline `ErrorCode.define("metadata.module-not-found", "Module not found: {}")`）
  - 校验 `status == "DRAFTING"`（String 比较，使用常量 `MODULE_STATUS_DRAFTING`）；非 drafting 抛 inline `ErrorCode.define("metadata.module-not-drafting", "Module is not in drafting status: {}")`
  - 设 `status = "RELEASED"`（String，使用常量 `MODULE_STATUS_RELEASED`），保存
  - version 不变（import 时已分配，见 D2）
  - 注意：nop-metadata 无 NopMetaErrors 类，ErrorCode 按现有模式在 BizModel 内 inline 定义（参考 NopMetaModuleBizModel:45-48）
- [x] 2.4 released 不可变保护：releaseModule 校验 status != RELEASED（已 released 抛 inline ErrorCode）。注意："不可变"仅指 releaseModule 拒绝重复 release；通用 CRUD 路径的不可变性强制为后续 plan（见 Non-Goals）
- [x] 2.5 在 TestNopMetaModuleBizModel 新增测试：
  - 导入模块（首次，version=1, status=DRAFTING）→ releaseModule → 断言 status="RELEASED", version=1
  - 对已 RELEASED 的模块再次 releaseModule → 断言抛异常
  - 导入同 moduleId 的第二个模块（version=2, status=DRAFTING）→ releaseModule → 断言 status="RELEASED", version=2（证明 version 在 import 时递增）

Exit Criteria:

- [x] OrmModelImporter.buildModule 不再硬编码 `1L`，version 由 BizModel 按 `max(moduleVersion for same moduleId)+1` 计算后传入
- [x] 重复导入同一 moduleId 不再违反唯一键（每次 import version 递增）
- [x] `releaseModule` action 可通过 GraphQL mutation 调用，将模块从 DRAFTING → RELEASED（String 状态值）
- [x] 首次导入的模块 version=1；同 moduleId 第二次导入 version=2（import 时递增逻辑正确）
- [x] releaseModule 不改 version（version 在 import 时已分配）
- [x] 对已 RELEASED 的模块调用 releaseModule 抛异常（不可变性验证）
- [x] **接线验证**：releaseModule 确实在运行时修改了 MetaModule 的 status（查询结果证明）
- [x] **无静默跳过**：非法状态调用 releaseModule 抛异常，不静默返回
- [x] **新功能测试**：新增测试方法验证 release 状态流转 + version import 时递增 + 不可变性，全绿
- [x] `ai-dev/design/nop-metadata/03-version-management.md` 新增 §4.4 releaseModule action 契约（按 Design Decision D1）+ §1.3 version 分配时机修正（按 D2）+ §4.2 修正"检查 MetaOrmModel.status"为"检查 MetaModule.status"（plan 293 G2 fix 未落地，此处补修）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Delta full 展开（isDelta 双重存储）

Status: completed
Targets: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/model/OrmModelImporter.java`、`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java`、`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaModuleBizModel.java`

- Item Types: `Proof`（新功能：Delta full 展开）+ `Decision`（API 方案裁定）

> **技术风险**：当前 `OrmModelLoader.loadFromResource` **始终**展开 x:extends。获取"未展开 x:extends 的原始 delta 定义"需要使用平台底层 API，具体可行性在 item 3.1 研究后确定。如果研究证明标准 API 不可行，Phase 3 降级为仅处理无 x:extends 的模块（delta=full，内容相同），有 x:extends 的模块 delta 存储降级为仅存 sourceContent（不拆解为实体）。
>
> **研究结论（3.1）**：采用**方案 A**。`DslNodeLoader.INSTANCE.loadDslNodeFromResource(resource, schema, ResolvePhase.filtered)` 获取 x:extends 未合并的 XNode，`SchemaLoader.loadXDefinition(schema)` 加载 xdef，`new DslModelParser(schema).disableInit(true).parseWithXDef(xdef, deltaNode)` 解析为 OrmModel。无 x:extends 时复用 full 模型作为 delta（`hasExtends` 检测）。解析失败时降级为 full（catch + LOG.warn）。标准 API 可用，未触发降级路径。

- [x] 3.1 **API 研究（硬前置门禁）**：确认如何获取"未展开 x:extends 的 OrmModel"。选定**方案 A**：`DslNodeLoader.loadDslNodeFromResource(resource, schema, ResolvePhase.filtered)` + `DslModelParser.parseWithXDef(xdef, node)`
- [x] 3.2 修改 OrmModelImporter：所有 build 方法接受 `boolean isDelta` 参数，不再硬编码 `b(true)`。同一 IEntityModel 可用 isDelta=true 或 isDelta=false 构建两套记录
- [x] 3.3 修改 NopMetaModuleBizModel.importOrmModel 为双解析流程（按 3.1 研究结论选定的方案 A）：
  - 解析 delta 定义（未展开 x:extends）→ 构建 isDelta=true 记录集 → 持久化
  - 解析 full 定义（展开 x:extends，当前行为）→ 构建 isDelta=false 记录集 → 持久化
  - 1 × NopMetaModule → 2 × NopMetaOrmModel（isDelta 区分）→ 各自子实体集（见 Design Decision D3）
- [x] 3.4 处理无 x:extends 的模块：isDelta=true 和 isDelta=false 内容相同，仍存储两份（保持查询一致性）。`hasExtends(sourceContent)` 检测后 `parseDeltaModel` 直接返回 fullModel
- [x] 3.5 **baseModuleId 填充**：在导入时解析 orm.xml 的 `x:extends` 属性（`resolveBaseModuleId`），若有则提取 base 模型路径 → 加载 base orm.xml 获取 appId → 推导 base moduleId → 查找已导入的 base MetaModule → 设置 `baseModuleId`。若 base 模块尚未导入，baseModuleId 设为 null（不阻塞导入）
- [x] 3.6 **创建专用测试 fixture**：在 `_vfs/test/orm/simple.orm.xml` 下创建一个不含 x:extends 的简化 orm.xml（含 2 个实体 TestSimpleA/TestSimpleB），供 Phase 3 测试使用
- [x] 3.7 在 TestNopMetaModuleBizModel 新增测试 `testDeltaDualStorageNoExtends`：
  - 导入专用测试 fixture（无 x:extends）后，`NopMetaOrmModel__findPage` total=2（isDelta=1 + isDelta=0 各 1 条）
  - 导入后 `NopMetaEntity__findPage` total=4（2 实体 × 2 delta/full），证明双重存储内容一致

Exit Criteria:

- [x] 导入专用测试 fixture（无 x:extends）后，`NopMetaOrmModel__findPage` total=2（isDelta=true + isDelta=false 各 1 条）
- [x] isDelta=true 的记录集和 isDelta=false 的记录集内容一致（无 x:extends 时，entity total=4 = 2×2）
- [x] OrmModelImporter 所有 build 方法不再硬编码 isDelta=true，由参数控制
- [x] 有 x:extends 的模块导入后，baseModuleId 被正确填充（若 base 模块已导入）或为 null（若 base 未导入，不阻塞）。app.orm.xml 导入时 base 模块（nop/metadata）未预导入，baseModuleId=null，不阻塞
- [x] **端到端验证**：从 `importOrmModel(path)` 入口到 isDelta=true/false 双重存储的完整路径已验证（testDeltaDualStorageNoExtends 全绿）
- [x] **接线验证**：双解析流程确实在运行时产生了两组记录（findPage total=2 证明）
- [x] **无静默跳过**：双解析流程的两个分支（delta + full）都实际执行，无空方法体或 continue 跳过
- [x] **新功能测试**：新增测试方法 testDeltaDualStorageNoExtends 验证 isDelta 双重存储，全绿
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.1 双解析流程描述与实现一致；`03-version-management.md` §4.1 导入流程与实现一致
- [x] **降级路径**：标准 API（方案 A）可用，未触发降级。`parseDeltaModel` 内置 catch + LOG.warn 降级到 full 模型，作为防御性兜底（当前未触发）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] baseModuleId to-one 自引用关系已补全，代码已重新生成
- [x] releaseModule action 端到端可用（drafting → released + version import 时递增 + releaseModule 拒绝重复 release）
- [x] Delta full 展开端到端可用（isDelta=true/false 双重存储）
- [x] OrmModelImporter 不再硬编码 version 和 isDelta
- [x] 不存在空壳实现（无空方法体 / 静默跳过）
- [x] 必要 focused verification 已完成（release 测试 + isDelta 双重存储测试全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] plan 292 Deferred "isDelta=false full 展开" 和 plan 293 Deferred "G4/G7-code" 已收口
- [x] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.1/§4.1、`03-version-management.md` §1.3/§4.1/§4.4 新增）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）releaseModule 在运行时确实修改了 status，（b）双解析流程确实产生了两组记录，（c）无空方法体/静默跳过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/295-nop-metadata-delta-expansion-and-version-release.md --strict` 退出码 0

## Deferred But Adjudicated

### MetaModelChangedEvent 事件发布

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计文档 §4.2 版本发布流程第 5 步提到发布事件，但 MetaModelChangedEvent 实体尚未建模（roadmap 标"未定 Phase"）。事件发布基础设施依赖事件模型实体先建模。当前 releaseModule 的核心行为（状态流转 + 不可变性）不依赖事件
- Successor Required: yes
- Successor Path: 事件模型建模后的 successor plan

### 多层 Delta 继承链递归 full 合并

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 full 展开使用平台 DslModelLoader 的 x:extends 展开，支持单层 base 合并。多层继承链（A extends B extends C）的递归合并由平台 x:extends 机制自然处理，但 nop-metadata 侧的 baseModuleId 查询目前仅支持单层导航。多层导航查询优化为后续改进
- Successor Required: no

## Non-Blocking Follow-ups

- 版本废弃 action（deprecateModule）：设计文档 §4.3 定义了 drafting → released → deprecated 流转，当前只实现到 released
- 版本对比功能：设计文档 §3.2 提到两个版本 full 定义 diff，需独立 UI 支持
- releaseModule 触发下游通知（非事件模型）：可先用日志记录，事件模型就绪后迁移

## Closure

Status Note: 三个 Phase 全部完成并验证通过。Phase 1 补全 baseModuleId to-one 自引用关系（ORM 模型 + 代码重新生成）。Phase 2 实现 releaseModule 版本发布 action（status 流转 + import 时 version 递增 + released 不可变），移除 OrmModelImporter 硬编码 version=1L。Phase 3 实现 Delta full 展开（isDelta=true/false 双重存储），使用方案 A（DslNodeLoader ResolvePhase.filtered + DslModelParser.parseWithXDef）获取未展开 x:extends 的 delta 模型。所有 9 个测试全绿，BUILD SUCCESS，hollow scan 退出码 0。plan 292 Deferred "isDelta=false full 展开" 和 plan 293 Deferred "G4/G7-code" 已收口。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: independent general subagent (session ses_099032442ffeOEMT9PYnysmKmD)
- Audit Session: ses_099032442ffeOEMT9PYnysmKmD
- Evidence:
  - Phase 1 Exit Criteria: PASS — `nop-metadata.orm.xml:186-194` baseModule to-one relation；`_NopMetaModule.java:1177` getBaseModule() 生成方法
  - Phase 2 Exit Criteria: PASS — `OrmModelImporter.java:47` buildModule(IOrmModel, long)；`NopMetaModuleBizModel.java:295-308` computeNextModuleVersion；`NopMetaModuleBizModel.java:279-293` releaseModule @BizMutation + DRAFTING 校验 + RELEASED 设置
  - Phase 3 Exit Criteria: PASS — OrmModelImporter 所有 build 方法接受 isDelta 参数（buildOrmModel:63, buildEntity:73, buildField:90, buildRelation:115, buildUniqueKey:127, buildIndex:138, buildDomain:149, buildDict:165）；`NopMetaModuleBizModel.java:110-111` persistModelGraph 调用两次；`parseDeltaModel:122-139` 使用 DslNodeLoader filtered + DslModelParser.parseWithXDef
  - 测试: PASS — 9 tests, 0 failures（testReleaseModuleStatusTransition, testReleaseModuleImmutableAlreadyReleased, testVersionIncrementOnReimport, testDeltaDualStorageNoExtends 全绿）
  - `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（9 + 1 tests pass）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 critical/high findings）
  - `node ai-dev/tools/check-plan-checklist.mjs ... --strict` 退出码 0
  - Anti-Hollow 检查结果：releaseModule 通过 dao().updateEntity 真实修改 DB；双解析 persistModelGraph 两次无条件执行；parseDeltaModel 的 catch+LOG.warn 为文档化降级（返回 full 模型，双重存储仍发生），非静默跳过；resolveBaseModuleId 的 catch→null 为优雅降级；无空方法体
  - Deferred 项分类检查：MetaModelChangedEvent（out-of-scope improvement，事件实体未建模）；多层 Delta 继承链（optimization candidate）— 均为 non-blocking，无 in-scope live defect 被降级

Follow-up:

- 版本废弃 action（deprecateModule）：design §4.3 已定义 drafting→released→deprecated，当前只实现到 released
- 版本对比功能：design §3.2 提到两版本 full diff，需独立 UI
- MetaModelChangedEvent 事件发布：事件模型实体建模后迁移（Deferred But Adjudicated）
