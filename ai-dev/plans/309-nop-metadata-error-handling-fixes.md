# 309 nop-metadata 异常处理与日志修复

> Plan Status: active
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-20-1816-open-audit-nop-metadata.md` (NF-01, NF-02, NF-04), `ai-dev/audits/2026-07-20-1816-multi-audit-nop-metadata.md` (09-01, 09-02, 09-03, 09-05)

## Purpose

修复 nop-metadata 模块中审计发现的异常处理与日志记录缺陷：原始异常堆栈丢失、过度捕获 `Throwable`、以 `IllegalStateException` 作为文本容器、`.cause(e)` 链式风格不一致、静默吞异常、硬编码中文错误消息。

## Current Baseline

- `MetaModelChangedEventPublisher.buildSnapshot` 在 `catch (Throwable e)` 块中以 `.param("error", e.toString())` 传递异常，未调用 `.cause(e)`，丢失完整堆栈；且 `catch Throwable` 会捕获 `Error` 类型（P2）
- `SqlColumnLineageExtractor` 3 处在 `NopException.cause()` 中传入裸 `IllegalStateException` 作为文本容器，而非通过独立 ErrorCode 或 `.param("reason", ...)` 传递（P2）
- 7 处（open audit 发现）+ 18 处（multi-dim 发现）= 25 处 `.cause(e)` 链式调用，而非 `new NopException(ErrorCode, Throwable)` 两参构造器（P3/P2）
- `NopMetaModuleBizModel.LOG.warn("...{}", e.toString())` 丢失堆栈跟踪（P2）
- `AggregationContext.tryLoadEntityField` 在异常时 `catch` 并 return null，无日志记录（P2）
- `MetaContractChecker.java` 包含硬编码中文业务错误消息（P2）

## Goals

- `buildSnapshot` 改用 `catch (Exception e)` + 两参构造器保留 cause chain，不再丢失堆栈
- `SqlColumnLineageExtractor` 3 处用独立 ErrorCode 或 `.param("reason", ...)` 替代 `IllegalStateException` cause
- 25 处 `.cause(e)` 链式调用统一迁移到两参构造器
- `NopMetaModuleBizModel` 日志改用 `LOG.warn("...", e)` 保留堆栈
- `tryLoadEntityField` 在异常时至少记录 warning 日志，不再静默 return null
- `MetaContractChecker.java` 中文消息迁移到 ErrorCode 或英文消息
- `./mvnw compile -pl nop-metadata -am && ./mvnw test -pl nop-metadata -am` 通过

## Non-Goals

- 不涉及接口契约修复（见 `308-nop-metadata-interface-contract-gaps.md`）
- 不涉及 xmeta 或 ORM 模型变更
- 不涉及死模块清理

## Scope

### In Scope

- `buildSnapshot` 异常处理修复（NF-01）
- `SqlColumnLineageExtractor` 异常 cause 修复（NF-02）
- 25 处 `.cause(e)` → 两参构造器迁移（NF-04 + 09-01）
- `NopMetaModuleBizModel` 日志堆栈保留（09-02）
- `tryLoadEntityField` 静默吞异常修复（09-03）
- `MetaContractChecker` 中文消息迁移（09-05）

### Out Of Scope

- 接口契约修复
- DTO 返回类型迁移
- xmeta / ORM 模型变更
- 死模块或空接口清理

## Execution Plan

### Phase 1 — 修复 buildSnapshot 异常处理

Status: planned
Targets: `MetaModelChangedEventPublisher.java`

- Item Types: `Fix`

- [ ] 将 `catch (Throwable e)` 改为 `catch (Exception e)`
- [ ] 使用 `new NopException(NopMetadataErrors.ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED, e)` 两参构造器替代 `.param("error", e.toString())`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `buildSnapshot` catch 块使用 `Exception`（非 `Throwable`）且 cause chain 完整
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 修复 SqlColumnLineageExtractor cause 模式

Status: planned
Targets: `SqlColumnLineageExtractor.java`

- Item Types: `Fix`

- [ ] 将 3 处 `throw new NopException(...).cause(new IllegalStateException(msg))` 改为使用独立 ErrorCode 或 `.param("reason", msg)`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 3 处不再使用 `IllegalStateException` 作为 cause 文本容器
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 统一 .cause(e) → 两参构造器

Status: planned
Targets: `AggregationContext.java`, `MetaTableQueryExecutor.java`, `MetaDataSourceConnectionProcessor.java`, `DefaultFilterApplicator.java`, `MetaQualityRuleExecutor.java`, `EntityEntityJoinAggregationProcessor.java`, `MetaJoinExecutor.java` 及其他 09-01 涉及的 18 处

- Item Types: `Fix`

- [ ] 全模块搜索 `.cause(e` 模式，将所有链式 `.cause(e)` 调用改为 `new NopException(ErrorCode, e)` 两参构造器
- [ ] 确认合并后不改变 `param` 语义（两参构造器内部正确设置 cause 和 error code）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 全模块无 `.cause(e` 链式调用遗留（或每个遗留处有显式注释说明为何不能迁移）
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 修复 NopMetaModuleBizModel 日志堆栈丢失

Status: planned
Targets: `NopMetaModuleBizModel.java`

- Item Types: `Fix`

- [ ] 将 `LOG.warn("...{}", e.toString())` 改为 `LOG.warn("...", e)` 以保留堆栈

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetaModuleBizModel` 中无 `e.toString()` 模式调用 `LOG.warn`
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 修复 tryLoadEntityField 静默吞异常

Status: planned
Targets: `AggregationContext.java`

- Item Types: `Fix`

- [ ] 在 `tryLoadEntityField` 的 catch 块中添加 `LOG.warn("failed to load entity field", e)` 日志记录
- [ ] 或使用 NopException 包装并重新抛出（视业务上下文决定）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `tryLoadEntityField` 在异常时不再静默 return null（至少记录日志）
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] 无静默跳过：静默 return null 模式已消除，快速失败或日志记录
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 — 迁移 MetaContractChecker 中文消息

Status: planned
Targets: `MetaContractChecker.java`

- Item Types: `Fix`

- [ ] 将硬编码中文业务错误消息迁移到 ErrorCode（`NopMetadataErrors.java`）或英文消息
- [ ] 确认模块内无其他中文硬编码错误消息残留

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `MetaContractChecker.java` 无中文硬编码业务消息
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope error handling 缺陷（NF-01, NF-02, NF-04, 09-01, 09-02, 09-03, 09-05）已修复
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）所有修复方法在 try-catch 路径中被实际调用，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] **无静默跳过**：All silent swallow patterns (`tryLoadEntityField`, `catch(Throwable)`) 已消除
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

（无 — 所有 in-scope 项均为 Fix 类型，无延期项）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: （完成时填写）
Completed: （完成时填写）

Closure Audit Evidence:

- Reviewer / Agent: （完成时填写）
- Evidence: （完成时填写）

Follow-up:

- （完成时填写）
