# nop-code 不变式闭环 — ORM cascadeDelete 后继（AR-149/150）

> Plan Status: completed
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I4 Phase 4 gated successor — AR-149/150 ORM cascadeDelete
> Last Reviewed: 2026-08-14
> Source: I4 plan `2026-08-13-1059-5-nop-code-invariant-i4-fix-execution.md` Phase 4 `Deferred But Adjudicated`；I6 closure report `i6-cycle1-closure-report.md` §5 gated successor
> Related: 前驱 I4 Phase 4（service-layer 降级已落地）；roadmap `nop-code-invariant-loop-roadmap.md` Loop Rule T0（resolved）

## Purpose

把 I4 Phase 4 中因 **plan-first Protected Area**（ORM 模型结构变更）阻塞的 AR-149/150 cascadeDelete 收口：为 `NopCodeFile.usages` 和 `NopCodeSymbol.usages` 两个 ORM 关系补 `cascadeDelete="true"`，作为 service-layer 显式删除的声明式 belt-and-suspenders。本计划完成后，Cycle 1 的最后一个 gated successor 中的 ORM 部分关闭。

## Current Baseline

> 已对 live repo 核对（2026-08-14）。

- **service-layer 降级已落地（I4 Phase 4 completed）**：`CodeIndexService.deleteFileRecords` 已显式 `deleteEntitiesByFilter(NopCodeUsage,"fileId")` + `deleteRelationalBySymbolIds(NopCodeUsage,"symbolId",symbolIds)`；`deleteIndex` 已显式按 indexId 删 NopCodeUsage。correctness 已由 service 层保证。
- **ORM 缺口确认（live）**：
  - `nop-code/model/nop-code.orm.xml:256` — `NopCodeFile` entity 的 `<to-many name="usages" ...>` **无 `cascadeDelete="true"`**（对比同 entity 的 `symbols`/`calls` 也均无 cascadeDelete）
  - `nop-code/model/nop-code.orm.xml:391` — `NopCodeSymbol` entity 的 `<to-many name="usages" ...>` **无 `cascadeDelete="true"`**（对比同 entity 的 `annotations` :397 **有** `cascadeDelete="true"`，`flowMemberships` :409 **有**，`callees` :415 **有**，`callers` :421 **有**，`superTypes` :427 **有**，`subTypes` :433 **有**）
- **对比基线（NopCodeIndex.usages）**：`nop-code.orm.xml:164` NopCodeIndex.usages **有** `cascadeDelete="true"`——索引级删除已覆盖；本计划补的是 **文件级**（NopCodeFile.usages）和 **符号级**（NopCodeSymbol.usages）的声明式 cascade。
- **生成产物（`_app.orm.xml`）**：cascadeDelete 是运行时 ORM 模型属性（由 `CascadeFlusher.java:256` 的 `propModel.isCascadeDelete()` 从 `OrmReferenceModel` 读取），**不在 `_gen` Java 实体类中编码**。源模型 `nop-code/model/nop-code.orm.xml` 变更后，`./mvnw install -pl nop-code -am -DskipTests` 会重新生成 `_app.orm.xml`（`nop-code-dao/src/main/resources/_vfs/nop/code/orm/_app.orm.xml`），该文件 line 259 (NopCodeFile.usages) 和 line 409 (NopCodeSymbol.usages) 当前缺 cascadeDelete，与源文件一致。运行时通过 `_app.orm.xml` 的 ORM 模型感知 cascadeDelete。
- **已有测试参考**：`TestOrmRelationNavigation.testCascadeDeleteCompleteness()`（`nop-code-service/src/test/java/io/nop/code/service/TestOrmRelationNavigation.java:107-139`）已验证 index 级 cascade delete——通过 `session.delete(indexEntity)` + `session.flush()` 删除 NopCodeIndex，断言 usages/calls/inheritances 等全级联删除。本计划新增的 file/symbol 级测试与此互补。
- **真正剩余 gap**：仅 ORM 声明式 cascadeDelete 新增（2 处 `<to-many>` 加属性），无 service 代码变更。

## Goals

- `NopCodeFile.usages` 和 `NopCodeSymbol.usages` 在 ORM 模型中声明 `cascadeDelete="true"`。
- 重新生成 `_app.orm.xml` 并验证 cascadeDelete 标记已传播到生成产物。
- 通过 focused test 验证：ORM 导航删除 NopCodeFile/NopCodeSymbol 实体时，关联 NopCodeUsage 行被自动删除（declarative cascade 生效）。
- `./mvnw test -pl nop-code -am -T 1C` 全绿。

## Non-Goals

- 不修改 service-layer 删除代码（I4 Phase 4 已落地且测试通过）。
- 不对 NopCodeFile 的其他关系（symbols/calls）添加 cascadeDelete——这些无对应的 audit finding，不在 scope。
- 不处理 @Auth 契约（原 Plan 2 已 cancelled——经审查证实 Nop 平台已通过 `ReflectionBizModelBuilder` 自动派生 CRUD 方法权限，`service-layer.md:209` 明确规定 CRUD 方法不需要 @Auth）。
- 不做稳态判定或复触发评估（roadmap 已在 Cycle 1 I6-revisit 做出稳态暂停裁定）。

## Scope

### In Scope

- `nop-code/model/nop-code.orm.xml` 中 2 处 `<to-many name="usages" ...>` 新增 `cascadeDelete="true"` 属性。
- 重新生成 `_app.orm.xml`（`./mvnw install -pl nop-code -am -DskipTests`）。
- 新增 ORM cascadeDelete focused test（验证导航删除时 usage 行被级联删除）。

### Out Of Scope

- service-layer 代码变更（deleteFileRecords / deleteIndex 已覆盖 correctness）。
- NopCodeFile.symbols / NopCodeFile.calls 的 cascadeDelete（无 audit finding）。
- @Auth 契约一致性（原 Plan 2 已 cancelled——auto-derived 权限已覆盖，无工作需要执行）。
- Cycle 2 工作（§D 候选门禁 / §B P2/P3 后继）。

## Execution Plan

> 单 Phase（仅 ORM 声明变更 + regen + test）。⚠ **plan-first / 执行前人工确认**（AGENTS.md Protected Areas: ORM 模型结构 plan-first）。

### Phase 1 - ORM cascadeDelete 声明 + regen + test

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`（:256 NopCodeFile.usages / :391 NopCodeSymbol.usages）；生成产物 `nop-code-dao/src/main/resources/_vfs/nop/code/orm/_app.orm.xml`；测试 `nop-code-service/src/test/java/io/nop/code/service/TestCascadeDeleteOrmLevel.java`

- Item Types: `Fix | Proof`

- [x] **ORM 变更**：在 `nop-code/model/nop-code.orm.xml` 中为以下 2 处 `<to-many>` 新增 `cascadeDelete="true"`：
  - NopCodeFile.usages（:256-261）→ 加 `cascadeDelete="true"`
  - NopCodeSymbol.usages（:391-396）→ 加 `cascadeDelete="true"`
- [x] **Regen**：执行 `./mvnw install -pl nop-code -am -DskipTests` 重新打包，使 `_app.orm.xml` 包含新 cascadeDelete 属性（cascadeDelete 是运行时 ORM 模型属性，由 `CascadeFlusher` 从 `_app.orm.xml` 的 `OrmReferenceModel` 读取，不在 `_gen` Java 类中编码）
- [x] **类别清扫**：grep `nop-code.orm.xml` 中所有 `<to-many name="usages"`，确认仅 NopCodeFile.usages + NopCodeSymbol.usages 缺 cascadeDelete（NopCodeIndex.usages 已有），无遗漏
- [x] **test-first**：新增 `TestCascadeDeleteOrmLevel.java`（放在 `nop-code-service/src/test/java/io/nop/code/service/`，使用 `@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)` + `extends JunitAutoTestCase`，参照 `TestOrmRelationNavigation.testCascadeDeleteCompleteness()` 模式）：
  - **File 级 cascade 验证**：seed 一个 NopCodeFile + 关联 NopCodeUsage 行（注意 NopCodeFile.symbols/calls 无 cascadeDelete，测试中应通过 `session.delete(fileEntity)` + `session.flush()` 删除 file 并断言 **仅 usages** 被级联删除——不断言 symbols/calls 被删，因为它们无 cascadeDelete。或者构造不含 symbols/calls 的 file 实体以避免 FK 约束冲突）
  - **Symbol 级 cascade 验证**：seed 一个 NopCodeSymbol + 关联 NopCodeUsage 行，通过 `session.delete(symbolEntity)` + `session.flush()` 删除 symbol 并断言 usages 被级联删除（注意 NopCodeSymbol.children/members 无 cascadeDelete，测试应避免触发这些约束）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-code.orm.xml` 中 NopCodeFile.usages 和 NopCodeSymbol.usages 均有 `cascadeDelete="true"`（grep 确认）
- [x] `_app.orm.xml` 已重新生成且对应 `<to-many>` 包含 `cascadeDelete="true"`（grep `nop-code-dao/src/main/resources/_vfs/nop/code/orm/_app.orm.xml` 确认）
- [x] `TestCascadeDeleteOrmLevel` 验证：ORM 导航删除（`session.delete(entity)` + `session.flush()`）NopCodeFile/NopCodeSymbol 后关联 NopCodeUsage 行为零（覆盖 File 级 + Symbol 级两个场景）
- [x] **功能验证**：cascadeDelete 在 ORM entity-level delete 路径实际生效（非仅声明存在）——test 通过 ORM session delete 验证行被级联删除（参照 `TestOrmRelationNavigation.testCascadeDeleteCompleteness()` 模式）
- [x] **无静默跳过**：若 ORM 变更导致编译错误或 regen 失败，抛出异常而非跳过
- [x] `./mvnw test -pl nop-code -am -T 1C` 全绿（无 regression）
- [x] 若该 Phase 改变 live baseline：相关 `ai-dev/design/` / `docs-for-ai/` 已更新；ORM cascadeDelete 为内部数据完整性增强，不改公开 API 契约——`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-149/150 ORM cascadeDelete gap 已修复（2 处 `<to-many name="usages">` 均有 `cascadeDelete="true"`）
- [x] service-layer 降级路径保持不变（I4 Phase 4 产出未回退）
- [x] ORM 导航删除 cascade 有 focused test 覆盖
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs：`No owner-doc update required`（ORM 内部 cascade，不改公开 API 契约）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：cascadeDelete 在运行时 ORM delete 路径实际生效（TestCascadeDeleteOrmLevel 断言行被删）
- [x] `./mvnw test -pl nop-code -am -T 1C`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（无——本计划 scope 极小，仅 2 处 ORM 属性新增 + regen + test）

## Non-Blocking Follow-ups

- NopCodeFile.symbols / NopCodeFile.calls 的 cascadeDelete 缺失——无 audit finding 驱动，watch-only residual。若后续发现导航删除 NopCodeFile 后 symbols/calls 残留，可单独派生 successor。
- Cycle 2 / I1 候选门禁中「删除路径一致性门禁」（INV-02 扩展）可考虑将 cascadeDelete 完备性纳入静态检查范围。

## Closure

Status Note: AR-149/150 ORM cascadeDelete gap 已收口——NopCodeFile.usages 和 NopCodeSymbol.usages 两处 `<to-many>` 均已声明 `cascadeDelete="true"`，`_app.orm.xml` 已重新生成传播，TestCascadeDeleteOrmLevel 验证 ORM 导航删除 file/symbol 时 usage 行被级联删除。service-layer 降级路径（I4 Phase 4）保持不变。本计划完成后，Cycle 1 的最后一个 ORM gated successor 关闭。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session ses_002811d27ffeRjm9jMf7mzBTAP), verdict CLOSURE_APPROVED
- Evidence:
  - **Exit Criterion 1** (PASS): `rg 'cascadeDelete' nop-code/model/nop-code.orm.xml` — NopCodeFile.usages (:257) + NopCodeSymbol.usages (:392) 均有 `cascadeDelete="true"`；NopCodeIndex.usages (:165) 已有（对比基线）
  - **Exit Criterion 2** (PASS): `rg 'cascadeDelete.*name="usages"' nop-code-dao/.../orm/_app.orm.xml` — 三处 usages (:183, :259, :409) 均含 cascadeDelete="true"
  - **Exit Criterion 3** (PASS): `TestCascadeDeleteOrmLevel` — 2 tests (file-level + symbol-level), 0 failures, 0 errors. surefire report: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
  - **Exit Criterion 4** (PASS): test 通过 `session.delete(entity)` + `session.flush()` 验证行被级联删除（非仅声明存在）——Anti-Hollow 验证
  - **Exit Criterion 5** (PASS): ORM 变更 + regen 无编译错误；`./mvnw install -pl nop-code -am -DskipTests` exit 0
  - **Exit Criterion 6** (PASS): `./mvnw test -pl nop-code -am -T 1C` BUILD SUCCESS（全模块测试全绿，含 TestCascadeDeleteOrmLevel + 全部回归测试）
  - **Exit Criterion 7** (PASS): No owner-doc update required — ORM cascadeDelete 为内部数据完整性增强，不改公开 API 契约
  - **Exit Criterion 8** (PASS): `ai-dev/logs/2026/08-14.md` 已更新
  - **Closure Gate: Anti-Hollow** (PASS): cascadeDelete 在运行时 ORM delete 路径实际生效——TestCascadeDeleteOrmLevel 断言 NopCodeUsage 行为零；`scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0
  - **Closure Gate: scan-hollow** (PASS): exit code 0
  - **Closure Gate: check-plan-checklist** (PASS): `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
  - **Deferred 项分类检查** (PASS): 无 deferred 项；Non-Blocking Follow-ups 仅为 watch-only residual（NopCodeFile.symbols/calls cascadeDelete 缺失无 audit finding），不包含 in-scope live defect

Follow-up:

- watch-only: NopCodeFile.symbols / NopCodeFile.calls 的 cascadeDelete 缺失——无 audit finding 驱动
- Cycle 2 / I1 候选门禁可考虑将 cascadeDelete 完备性纳入静态检查范围
- Phase 9 @Auth 契约仍为 gated 阻塞（原 Plan 2 已 cancelled——auto-derived 权限已覆盖）
