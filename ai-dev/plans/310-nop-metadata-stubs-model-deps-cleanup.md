# 310 nop-metadata 空接口存根、ORM 模型约束与依赖清理

> Plan Status: active
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-20-1816-open-audit-nop-metadata.md` (NF-03, AR-04), `ai-dev/audits/2026-07-20-1816-multi-audit-nop-metadata.md` (03-F04, 03-F05, 03-F06, 03-F09, 03-F10, 04-P2-01, 04-P2-02, 07-F1, 01-02, 09-11)

## Purpose

清理 nop-metadata 模块中审计发现的 P2/P3 级技术债务：空接口存根、死模块、DTO 迁移未完成、xmeta retention 层空缺、ORM 模型缺少/冲突约束、未使用的编译依赖。与 error handling 和 interface contract 修复互补。

## Current Baseline

- `NopMetadataConstants.java` 和 `NopMetadataConfigs.java` 为空接口存根（无常量、无配置），此前审计认定为 P1/P3 仍存在（NF-03, 09-11）
- `nop-metadata-api` 死模块仍在 `pom.xml` 中声明，`src/` 目录不存在，打包产物仅 996 字节无 `.class`（AR-04）
- 15+ action 方法返回 `Map<String, Object>` 而非 `@DataBean` DTO，代码注释引用 "plan 307"（03-F04, 07-F1）
- 3 个 `@Deprecated` 方法在 BizModel 中标记但接口未标记（03-F05）
- `NopMetaDataSource.xmeta connectionConfig` 生成的 `queryable=true` 未被 retention 层覆盖（03-F06）
- `NopMetaTable.xmeta` retention 层完全为空（03-F09），`NopMetaLineageEdge.xmeta` retention 层为空（03-F10）
- `nop-metadata.orm.xml:NopMetaLineageEdge` 缺少业务唯一约束（04-P2-01）
- `nop-metadata.orm.xml:NopMetaTable` 存在两个重叠唯一键（04-P2-02）
- `nop-metadata-service/pom.xml` 声明了未使用的 `nop-sys-dao` 编译依赖（01-02）

## Goals

- 删除空接口存根 `NopMetadataConstants.java` 和 `NopMetadataConfigs.java`
- 从父 pom 移除 `nop-metadata-api` 死模块并删除子目录
- 修复 `connectionConfig` 的 `queryable` 字段在 retention xmeta 中的覆盖
- 为 `NopMetaLineageEdge` 添加业务唯一约束；修复 `NopMetaTable` 重叠唯一键
- 移除未使用的 `nop-sys-dao` 编译依赖
- `./mvnw compile && ./mvnw test -pl nop-metadata -am` 通过

## Non-Goals

- 不涉及 `Map<String, Object>` → DTO 迁移中的行为变更（属独立 scope，见 `307-nop-metadata-dto-migration-data-auth.md`）
- 不涉及接口契约修复（见 `308-nop-metadata-interface-contract-gaps.md`）
- 不涉及 error handling 修复（见 `309-nop-metadata-error-handling-fixes.md`）

## Scope

### In Scope

- 空接口存根删除（NF-03, 09-11）
- 死模块移除（AR-04）
- `connectionConfig` xmeta `queryable` 覆盖（03-F06）
- `@Deprecated` 接口标记（03-F05）
- Retention 层修复（03-F09, 03-F10） — 仅 xmeta 层，不涉及代码
- ORM 模型约束修复（04-P2-01, 04-P2-02）
- 未使用编译依赖移除（01-02）

### Out Of Scope

- `Map<String, Object>` → DTO 迁移（属 `plan 307` scope）
- 接口方法声明补全
- Error handling / logging 修复
- 框架 xmeta 生成器的通用改进

## Execution Plan

### Phase 1 — 清理空接口存根

Status: planned
Targets: `NopMetadataConstants.java`, `NopMetadataConfigs.java`

- Item Types: `Fix`

- [ ] 删除 `NopMetadataConstants.java` 和 `NopMetadataConfigs.java` 两个空接口文件
- [ ] 确认全仓库无其他引用（grep 检查 import）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 两个空接口文件已删除，无编译错误
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 移除 nop-metadata-api 死模块

Status: planned
Targets: `nop-metadata/pom.xml`, `nop-metadata/nop-metadata-api/`

- Item Types: `Fix`

- [ ] 从 `nop-metadata/pom.xml` 的 `<modules>` 列表中移除 `<module>nop-metadata-api</module>`
- [ ] 删除 `nop-metadata-api/` 子目录

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `nop-metadata-api` 已从 modules 中移除，目录已删除
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required（构建配置变更，不影响运行时行为）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 修复 connectionConfig xmeta queryable 覆盖

Status: planned
Targets: `NopMetaDataSource.xmeta` retention 层

- Item Types: `Fix`

- [ ] 在 `NopMetaDataSource.xmeta` retention 层中添加 `connectionConfig` 字段的 `queryable="false"` 覆盖（当前 retention 仅覆盖了 `published`/`insertable`/`updatable`）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `connectionConfig` 字段在 retention xmeta 中正确覆盖 `queryable="false"`
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required（xmeta 内部变更）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 修复 ORM 模型约束

Status: planned
Targets: `nop-metadata.orm.xml`

- Item Types: `Fix`

- [ ] 为 `NopMetaLineageEdge` 添加业务唯一约束（`(sourceEdgeId, targetEdgeId, type)` 或审计确定的合适组合）
- [ ] 修复 `NopMetaTable` 的两个重叠唯一键（统一命名规范，清理 `uk_meta_table_module_schema` 与 `UK_NOP_META_TABLE_MODULE_NAME` 的冲突语义）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetaLineageEdge` 有业务唯一约束
- [ ] `NopMetaTable` 无重叠唯一键
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required（ORM 模型内部变更，不改变 public API）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 修复 @Deprecated 接口标记 + 未使用依赖移除

Status: planned
Targets: `INopMetaDataContractBiz.java`, `nop-metadata-service/pom.xml`

- Item Types: `Fix`

- [ ] 在 `INopMetaDataContractBiz` 中为 BizModel 中已标记 `@Deprecated` 的 3 个方法添加对应接口层 `@Deprecated` 注解
- [ ] 从 `nop-metadata-service/pom.xml` 移除 `nop-sys-dao` compile 依赖（确认无代码引用后）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `@Deprecated` 方法在接口层有注解对应
- [ ] `nop-sys-dao` 编译依赖已移除
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 — 修复空 retention xmeta 层

Status: planned
Targets: `NopMetaTable.xmeta`, `NopMetaLineageEdge.xmeta` retention 层

- Item Types: `Fix | Decision`

- [ ] 评估 `NopMetaTable.xmeta` 空 retention 层的含义：决定是否添加字段级权限覆盖，或在 xmeta 注释中明确说明此为核心实体、retention 有意为空
- [ ] 评估 `NopMetaLineageEdge.xmeta` 空 retention 层：决定是否添加字段级覆盖或注释说明
- [ ] 实施决定的方案（添加覆盖或注释）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetaTable.xmeta` 和 `NopMetaLineageEdge.xmeta` 的 retention 层不再是无理由的空状态
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope 空存根/死模块已清理（NF-03, 09-11, AR-04）
- [ ] xmeta retention/queryable 覆盖已修复（03-F06, 03-F09, 03-F10）
- [ ] ORM 模型约束已修复（04-P2-01, 04-P2-02）
- [ ] `@Deprecated` 接口标记已同步 + 未使用依赖已移除（03-F05, 01-02）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）清理/修复操作确实生效，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

### Map<String, Object> → DTO 迁移（03-F04, 07-F1）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 此迁移已在 `307-nop-metadata-dto-migration-data-auth.md` 中规划，不移入本 plan scope 以避免 scope 过大
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/307-nop-metadata-dto-migration-data-auth.md`

## Non-Blocking Follow-ups

- 无 — 所有本 scope 内的 P2/P3 项已通过 Phase 覆盖

## Closure

Status Note: （完成时填写）
Completed: （完成时填写）

Closure Audit Evidence:

- Reviewer / Agent: （完成时填写）
- Evidence: （完成时填写）

Follow-up:

- （完成时填写）
