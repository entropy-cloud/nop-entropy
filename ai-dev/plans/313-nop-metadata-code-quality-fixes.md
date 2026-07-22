# 313 Nop Metadata Code Quality Fixes

> Plan Status: draft
> Last Reviewed: 2026-07-22
> Source: Code review of nop-metadata 模块设计与实现 — `docs-for-ai/02-core-guides/ioc-and-config.md` 已更新
> Related: `292-nop-metadata-implementation-roadmap.md`, `306-nop-metadata-audit-quick-fixes.md`

## Purpose

修复 nop-metadata 模块中发现的 5 个已确认代码质量问题，不涉及新增功能。

## Current Baseline

- `NopMetadataDaoConstants` 在 `_NopMetadataCoreConstants` (由 dict YAML 生成) 已定义的基础上，重复定义了 `MODULE_STATUS_DRAFTING` / `RELEASED` / `DEPRECATED` 三个常量
- `NopMetadataConstants` (service 包) 为空接口，无任何用途
- `QualityAlertWorkflowService` 使用 `BeanContainer.instance().getBeanByType(IWorkflowManager.class)` 手动查找 bean，而非 `@Inject`
- `truncate()`、`join()`、`stringOf()`、`toSearchableDoc()`、`toErrorMessage()` 等辅助方法在 `NopMetaTableBizModel`、`NopMetaModuleBizModel`、`NopMetaDataSourceBizModel` 等多个 BizModel 中逐文件复制粘贴
- `NopMetadataErrors` 单文件 1082 行，ARGS 常量与 ErrorCode 定义混杂在同一接口中
- `NopMetadataException` 标注 `@Deprecated` 的 String 构造器（`NopMetadataException(String)`）声明废弃但仍有代码使用

## Goals

- [x] 消除所有已确认的代码重复和常量重复
- [ ] 消除 `BeanContainer.instance().getBeanByType()` 手工查找
- [ ] 对齐 `NopMetadataException` 实际使用模式与注解声明
- [ ] 拆分 `NopMetadataErrors` 到子域文件，减少单文件膨胀
- [ ] 每项变更有对应测试验证

## Non-Goals

- 不修改 ORM 模型或实体结构
- 不修改业务逻辑行为（pure refactoring）
- 不修改 `_service.beans.xml` / `_dao.beans.xml` 等生成文件
- 不涉及新功能或 API 变更

## Scope

### In Scope

- 常量重复消除（`NopMetadataDaoConstants`）
- 空接口清理（`NopMetadataConstants`）
- `BeanContainer` 手工查找改为 `@Inject`（`QualityAlertWorkflowService`）
- 公共辅助方法提取到工具类（`NopMetadataHelper`）
- `NopMetadataErrors` 按子域拆分为多个文件
- `NopMetadataException` 废弃构造器对齐清理

### Out Of Scope

- `_service.beans.xml` 中 BizProxy 注册的冗余性（此为 Nop 平台标准模式，非本模块问题）
- 业务逻辑重构或重写
- DTO 数量治理（`nop-metadata-core/dto/`）
- JDBC 替换为 `ISqlExecutor` 等 Nop 内置 API

## Execution Plan

### Phase 1 — 常量重复 & 空接口清理

Status: planned
Targets: `nop-metadata-dao`, `nop-metadata-service`

- Item Types: `Fix`

- [ ] 从 `NopMetadataDaoConstants` 中删除与 `_NopMetadataCoreConstants` 重复的 3 个常量（`MODULE_STATUS_DRAFTING` / `RELEASED` / `DEPRECATED`），所有引用处改为 `_NopMetadataCoreConstants`
- [ ] 删除 `NopMetadataConstants` (service 包) 空接口
- [ ] 确保编译通过 `./mvnw compile -pl nop-metadata-dao,nop-metadata-service -am`

Exit Criteria:

- [ ] `NopMetadataDaoConstants` 不再重复定义任何已在 `_NopMetadataCoreConstants` 中的常量
- [ ] `NopMetadataConstants` 空接口已删除
- [ ] 全模块编译通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — QualityAlertWorkflowService 注入重构

Status: planned
Targets: `QualityAlertWorkflowService.java`

- Item Types: `Fix`

- [ ] 将 `getWfManager()` 中 `BeanContainer.instance().getBeanByType(IWorkflowManager.class)` 改为可空字段注入 `@Inject @Nullable protected IWorkflowManager wfManager;`
- [ ] 添加测试：验证 `wfManager` 为 null 时 `createAlertWorkflow` 返回 null 且不抛 NPE
- [ ] 确保编译通过 `./mvnw compile -pl nop-metadata-service -am`

Exit Criteria:

- [ ] `QualityAlertWorkflowService` 不再调用 `BeanContainer.instance()`
- [ ] `@Inject @Nullable protected IWorkflowManager wfManager;` 已注入
- [ ] 无 `@Deprecated` 编译警告新增
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 公共辅助方法提取

Status: planned
Targets: `nop-metadata-service`

- Item Types: `Fix`

- [ ] 创建 `NopMetadataHelper` 类（package-private, final, 私有构造器），包含：
  - `static String truncate(String s, int maxLen)`
  - `static String join(String delimiter, String... parts)`
  - `static String stringOf(Map<String, Object> data, String key)`
  - `static String toErrorMessage(Exception e)`
  - `static SearchableDoc toSearchableDoc(NopMetaEntity entity)`
  - `static SearchableDoc toSearchableDoc(NopMetaTable entity)`
  - `static SearchableDoc toSearchableDoc(NopMetaEntityField entity)`
- [ ] 替换 `NopMetaTableBizModel`、`NopMetaModuleBizModel`、`NopMetaDataSourceBizModel` 中的重复实现
- [ ] 添加 `NopMetadataHelperTest` 验证各方法边界情况
- [ ] 确保编译通过 + 已有测试全部通过

Exit Criteria:

- [ ] `truncate()` / `join()` / `stringOf()` / `toErrorMessage()` 不再出现在多个 BizModel 中（仅 `NopMetadataHelper` 一处）
- [ ] `toSearchableDoc` 各重载版本在 helper 中唯一实现
- [ ] `NopMetadataHelperTest` 覆盖 null 入参、空串、边界长度
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — NopMetadataErrors 按子域拆分

Status: planned
Targets: `NopMetadataErrors.java`

- Item Types: `Fix | Decision`

- [ ] 创建子域 ErrorCode 文件（每个约 50-100 行），按 `NopMetadataErrors` 现有注释分组：
  - `AggregationErrors.java` (aggr / granularity)
  - `JoinErrors.java` (join)
  - `QualityErrors.java` (quality / checkpoint / score / quality-rule)
  - `DataSourceErrors.java` (datasource / tableref / table)
  - `SqlErrors.java` (sql / sql-module / sql-type-inference)
  - `FieldErrors.java` (field / dimension / measure)
  - `LineageErrors.java` (lineage / col-lineage / propagation)
  - `ModuleErrors.java` (module / manifest / orm-resource)
  - `ReconErrors.java` (recon)
  - `MiscErrors.java` (contract / tag-label / catalog / filter-definition / search / event / profiling / profiling-rule / sync)
- [ ] `NopMetadataErrors` 接口改为继承上述子域接口（或直接 import 各子域），保持向后兼容
- [ ] 将子域共用的 `ARG_*` 常量迁移到各子域文件
- [ ] 添加测试：验证至少一个旧引用路径（`NopMetadataErrors.ERR_*`）在新结构下仍可正常编译
- [ ] 确保全模块编译通过

Exit Criteria:

- [ ] 每个子域有独立 ErrorCode 文件，平均不超过 120 行
- [ ] `NopMetadataErrors` 接口仍保留（作为统一入口）但内容大幅精简
- [ ] 不存在被删除的旧 `NopMetadataErrors.ERR_*` 引用（编译通过）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — NopMetadataException 废弃构造器对齐

Status: planned
Targets: `NopMetadataException.java` + 调用处

- Item Types: `Fix`

- [ ] grep 所有使用 `new NopMetadataException(String)` 或 `new NopMetadataException(String, Throwable)` 的代码
- [ ] 将每一处替换为 `new NopMetadataException(ErrorCode)` 或 `new NopMetadataException(ErrorCode, Throwable)`，必要时新增对应 `ErrorCode`（若原有 String 消息有独立语义）
- [ ] 确认无旧构造器调用残留后，删除 `@Deprecated` 构造器及 `toInlineErrorCode` 方法
- [ ] 确保编译通过 + 测试通过

Exit Criteria:

- [ ] `NopMetadataException` 仅保留 `(ErrorCode)` 和 `(ErrorCode, Throwable)` 构造器
- [ ] 所有调用处使用 ErrorCode 常量而非 inline String
- [ ] 原有 String 消息中的语义已迁移到对应 ErrorCode 定义
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 所有 in-scope 已确认代码缺陷已修复
- [ ] `./mvnw compile -pl nop-metadata-service,nop-metadata-dao -am` 通过
- [ ] `./mvnw test -pl nop-metadata-service -am` 通过
- [ ] 各 Phase Exit Criteria 全部标记 `[x]`
- [ ] 独立子 agent closure audit 已完成

## Deferred But Adjudicated

### `NopMetadataErrors` 原始引用路径兼容性

- Classification: `watch-only residual`
- Why Not Blocking Closure: Phase 4 保留 `NopMetadataErrors` 接口作为兼容入口，不删除旧引用路径
- Successor Required: `no`

### DTO 数量治理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于优化而非缺陷修复，已有 `311-nop-metadata-dto-module-restructure.md` 覆盖
- Successor Required: `yes`
- Successor Path: `311-nop-metadata-dto-module-restructure.md`

## Non-Blocking Follow-ups

- 检查 `docs-for-ai/04-reference/source-anchors.md` 中是否需更新 `NopMetadataErrors` 锚点

## Closure

Status Note: <<待完成>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<待补>>
- Evidence: <<待补>>

Follow-up:

- <<待补>>
