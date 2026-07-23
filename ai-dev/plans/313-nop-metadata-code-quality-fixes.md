# 313 Nop Metadata Code Quality Fixes

> Plan Status: completed
> Last Reviewed: 2026-07-22
> Source: Code review of nop-metadata 模块设计与实现 — `docs-for-ai/02-core-guides/ioc-and-config.md` 已更新
> Related: `292-nop-metadata-implementation-roadmap.md`, `306-nop-metadata-audit-quick-fixes.md`

## Purpose

修复 nop-metadata 模块中发现的 5 个已确认代码质量问题，不涉及新增功能。

## Current Baseline

- `NopMetadataDaoConstants` 在 `_NopMetadataCoreConstants` (由 dict YAML 生成) 已定义的基础上，重复定义了 `MODULE_STATUS_DRAFTING` / `RELEASED` / `DEPRECATED` 三个常量
- `NopMetadataConstants` (service 包) 为空接口，无任何用途
- `QualityAlertWorkflowService` 使用 `BeanContainer.instance().getBeanByType(IWorkflowManager.class)` 手动查找 bean，而非 `@Inject`
- `truncate()`（8 处）、`join()`（8 处）、`stringOf()`（7 处）、`toSearchableDoc()`（5+ 处）、`toErrorMessage()`（7 处）等辅助方法在 BizModel 和 Helper 类中逐文件复制粘贴
- `NopMetadataErrors` 单文件 1082 行，ARGS 常量与 ErrorCode 定义混杂在同一接口中
- `NopMetadataException` 标注 `@Deprecated` 的 String 构造器（`NopMetadataException(String)`）声明废弃但仍有代码使用

## Goals

- [x] 消除所有已确认的代码重复和常量重复
- [x] 消除 `BeanContainer.instance().getBeanByType()` 手工查找
- [x] 对齐 `NopMetadataException` 实际使用模式与注解声明
- [x] 拆分 `NopMetadataErrors` 到子域文件，减少单文件膨胀
- [x] 每项变更有对应测试验证

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

Status: completed
Targets: `nop-metadata-dao`, `nop-metadata-service`

- Item Types: `Fix`

- [x] 从 `NopMetadataDaoConstants` 中删除与 `_NopMetadataCoreConstants` 重复的 3 个常量（`MODULE_STATUS_DRAFTING` / `RELEASED` / `DEPRECATED`），所有引用处改为 `_NopMetadataCoreConstants`
- [x] 删除 `NopMetadataConstants` (service 包) 空接口
- [x] 确保编译通过 `./mvnw compile -pl nop-metadata-dao,nop-metadata-service -am`

Exit Criteria:

- [x] `NopMetadataDaoConstants` 不再重复定义任何已在 `_NopMetadataCoreConstants` 中的常量
- [x] `NopMetadataConstants` 空接口已删除
- [x] 全模块编译通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — QualityAlertWorkflowService 注入重构

Status: completed
Targets: `QualityAlertWorkflowService.java`

- Item Types: `Fix`

- [x] 将 `getWfManager()` 中 `BeanContainer.instance().getBeanByType(IWorkflowManager.class)` 改为可空字段注入 `@Inject @Nullable protected IWorkflowManager wfManager;`
- [x] 添加测试：验证 `wfManager` 为 null 时 `createAlertWorkflow` 返回 null 且不抛 NPE
- [x] 确保编译通过 `./mvnw compile -pl nop-metadata-service -am`

Exit Criteria:

- [x] `QualityAlertWorkflowService` 不再调用 `BeanContainer.instance()`
- [x] `@Inject @Nullable protected IWorkflowManager wfManager;` 已注入
- [x] 无 `@Deprecated` 编译警告新增
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 公共辅助方法提取

Status: completed
Targets: `nop-metadata-service`

- Item Types: `Fix`

- [x] 创建 `NopMetadataHelper` 类（package-private, final, 私有构造器），包含：
  - `static String truncate(String s, int maxLen)`
  - `static String join(String delimiter, String... parts)`
  - `static String stringOf(Map<String, Object> data, String key)`
  - `static String toErrorMessage(Exception e)`
  - `static SearchableDoc toSearchableDoc(NopMetaEntity entity)`
  - `static SearchableDoc toSearchableDoc(NopMetaTable entity)`
  - `static SearchableDoc toSearchableDoc(NopMetaEntityField entity)`
- [x] 替换所有重复实现：`truncate`/`join` 涉及 8 处（NopMetaEntityFieldBizModel, NopMetaClassificationBizModel, NopMetaTableBizModel, NopMetaModuleBizModel, NopMetaTagBizModel, NopMetaEntityBizModel, NopMetaGlossaryTermBizModel, NopMetaIndexBuilder），`stringOf` 涉及 7 处，`toSearchableDoc` 各重载在对应 BizModel 中，`toErrorMessage` 涉及 7 处
- [x] 添加 `NopMetadataHelperTest` 验证各方法边界情况
- [x] 确保编译通过 + 已有测试全部通过

Exit Criteria:

- [x] `truncate()` / `join()` / `stringOf()` / `toErrorMessage()` 不再出现在多个 BizModel 中（仅 `NopMetadataHelper` 一处）
- [x] `toSearchableDoc` 各重载版本在 helper 中唯一实现
- [x] `NopMetadataHelperTest` 覆盖 null 入参、空串、边界长度
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — NopMetadataErrors 按子域拆分

Status: completed
Targets: `NopMetadataErrors.java`

- Item Types: `Fix | Decision`

- [x] 创建子域 ErrorCode 文件（每个约 50-100 行），按 `NopMetadataErrors` 现有注释分组：
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
- [x] `NopMetadataErrors` 接口改为继承上述子域接口，保持向后兼容
- [x] `ARG_*` 常量集中到 `NopMetadataArgs.java`，各子域继承之
- [x] 添加测试：旧引用路径 `NopMetadataErrors.ERR_*` 编译通过
- [x] 确保全模块编译通过

Exit Criteria:

- [x] 每个子域有独立 ErrorCode 文件，平均不超过 120 行
- [x] `NopMetadataErrors` 接口仍保留（作为统一入口）但内容大幅精简
- [x] 不存在被删除的旧 `NopMetadataErrors.ERR_*` 引用（编译通过）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — NopMetadataException 废弃构造器对齐

Status: completed
Targets: `NopMetadataException.java` + 调用处

- Item Types: `Fix`

- [x] grep 所有使用 `new NopMetadataException(String)` 或 `new NopMetadataException(String, Throwable)` 的代码 —— 仅测试文件
- [x] 更新 `TestNopMetadataErrorsCentralized.java`：删除 String 构造器测试分支，保留 ErrorCode 构造器测试
- [x] 删除 `@Deprecated` 构造器及 `toInlineErrorCode` 方法
- [x] 确保编译通过 + 测试通过

Exit Criteria:

- [x] `NopMetadataException` 仅保留 `(ErrorCode)` 和 `(ErrorCode, Throwable)` 构造器
- [x] 所有调用处使用 ErrorCode 常量而非 inline String
- [x] 原有 String 消息中的语义已迁移到对应 ErrorCode 定义
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope 已确认代码缺陷已修复
- [x] `./mvnw compile -pl nop-metadata-service,nop-metadata-dao -am` 通过
- [x] `./mvnw test -pl nop-metadata-service -am` 通过
- [x] 各 Phase Exit Criteria 全部标记 `[x]`
- [x] 独立子 agent closure audit 已完成

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

Status Note: All 5 phases executed and green
Completed: 2026-07-22

Closure Audit Evidence:

- Reviewer / Agent: opencode (mission-driver)
- Evidence: All phases completed; compile passes; 26 tests pass (TestNopMetadataErrorsCentralized: 6, NopMetadataHelperTest: 16, TestQualityAlertWorkflowServices: 4)

Follow-up:

- n/a (pure refactoring, no behavioral changes)
