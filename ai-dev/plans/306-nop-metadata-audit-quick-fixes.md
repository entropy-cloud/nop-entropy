# 306 nop-metadata 审计快速修复

> Plan Status: completed
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-20-1816-open-audit-nop-metadata.md` (R-01, R-02)

## Purpose

修复 nop-metadata 审计中剩余的 2 个快速修复项：`SqlAggregationProcessor` 的 `IllegalArgumentException` 抛出和 `nop-metadata-api` 死模块清理。

## Current Baseline

- R-01: `SqlAggregationProcessor.execute()` 在 `_NopMetadataCoreConstants.TABLE_TYPE_SQL` 不匹配时抛出 `IllegalArgumentException`，而非使用 `NopException` + ErrorCode 体系
- R-02: `nop-metadata/nop-metadata-api/` 子模块 `src/` 目录不存在，打包产物仅 996 字节无 `.class`，且全仓库 0 个外部引用

## Goals

- 消除 `SqlAggregationProcessor` 中的裸 `IllegalArgumentException` 抛出
- 从 `nop-metadata/pom.xml` 中移除死模块 `nop-metadata-api` 并删除其目录
- 修复后 `./mvnw compile -pl nop-metadata -am` 通过

## Non-Goals

- 不涉及 DTO 返回类型迁移（见 `307-nop-metadata-dto-migration-data-auth.md`）
- 不涉及 `data-auth.xml` 覆盖面扩展（同上）
- 不涉及其他 BizModel 规范问题

## Scope

### In Scope

- 修复 `SqlAggregationProcessor.java:25-28` 中 `IllegalArgumentException` → `NopException`
- 从 `nop-metadata/pom.xml` 移除 `<module>nop-metadata-api</module>` 并删除子目录

### Out Of Scope

- `NopMetadataErrors` 中新增 ErrorCode 之外的其他 ErrorCode 增删
- 其他模块的死依赖清理

## Execution Plan

### Phase 1 - Fix SqlAggregationProcessor IllegalArgumentException

Status: completed
Targets: `nop-metadata/nop-metadata-service/`

- Item Types: `Fix`, `Proof`

- [x] 在 `NopMetadataErrors`（接口 `NopMetadataErrors.java`）中新增 `ERR_AGGR_UNSUPPORTED_TABLE_TYPE`（`ARG_TABLE_TYPE` 已存在，无需重复创建）
- [x] 将 `SqlAggregationProcessor.java:25-28` 的 `throw new IllegalArgumentException(...)` 改为 `throw new NopException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_TABLE_TYPE).param("tableType", tableType)`
- [x] 注意：`MetaAggregationExecutor.executeAggregation()`（104-115 行）已在 dispatch 层对非 `TABLE_TYPE_SQL` 抛出 `ERR_AGGR_EXEC_FAILED`，因此 `SqlAggregationProcessor` 的此分支在当前调用链上是死代码。此修复是防御性加固。测试必须**直接调用** `SqlAggregationProcessor.execute()`（绕过 `MetaAggregationExecutor`）才能触发该路径

Exit Criteria:

- [x] `SqlAggregationProcessor` 在不支持的 tableType 输入下抛出 `NopException` 而非 `IllegalArgumentException`
- [x] 测试覆盖：在已有 `TestSqlAggregationProcessor.java` 中新增 test 方法，通过**直接调用** `SqlAggregationProcessor.execute()` 验证不支持的 tableType 输入抛出 `NopException` 且携带正确 `ErrorCode`
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Remove nop-metadata-api dead module

Status: completed
Targets: `nop-metadata/pom.xml`, `nop-metadata/nop-metadata-api/`

- Item Types: `Fix`, `Proof`

- [x] 从 `nop-metadata/pom.xml` 的 `<modules>` 中移除 `<module>nop-metadata-api</module>`
- [x] 删除 `nop-metadata/nop-metadata-api/` 目录及其所有内容
- [x] 删除/更新 `nop-metadata/nop-metadata-meta/postcompile/gen-crud-api.xgen` 中对 `io.nop.metadata.api` 的引用（注释块中的 `apiPackageName: "io.nop.metadata.api"` 指向已删除模块）
- [x] 确认 `grep "nop-metadata-api"` 在全仓库 pom.xml 中无 `<module>` 或 `<dependency>` 引用（自身声明已移除后应为 0）
- [x] 确认无 Java 源文件 `import io.nop.metadata.api.*` 或引用 `nop-metadata-api` 类（已知为 0 匹配，显式验证）

Exit Criteria:

- [x] `nop-metadata/pom.xml` 不再声明 `nop-metadata-api` 模块
- [x] `nop-metadata/nop-metadata-api/` 目录已删除
- [x] `gen-crud-api.xgen` 中对 `io.nop.metadata.api` 的引用已清理
- [x] 全仓库 `grep -r "nop-metadata-api" --include="pom.xml" .` 结果为 0（无 `<module>` 或 `<dependency>` 引用）
- [x] 全仓库 `grep -r "io.nop.metadata.api" --include="*.java" --include="*.xgen" --include="*.xml" .` 结果为 0
- [x] `./mvnw compile -pl nop-metadata -am` 通过（无模块缺失错误）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 和 Phase 2 的 Exit Criteria 全部勾选
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：`SqlAggregationProcessor` 的防御性路径通过直接单元测试覆盖（非集成路径——因为 `MetaAggregationExecutor` 在 dispatch 层已拦截非 SQL tableType）

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

None.

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- No remaining plan-owned work.
