# nop-code 不变式闭环 — @Auth 契约一致性后继（AR-146/155/170）

> Plan Status: cancelled
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I4 Phase 9 gated successor — AR-146/155/170 @Auth contract consistency
> Last Reviewed: 2026-08-14
> Source: I4 plan `2026-08-13-1059-5-nop-code-invariant-i4-fix-execution.md` Phase 9（ask-first gate，调查清单已交付）；I6 closure report `i6-cycle1-closure-report.md` §5 gated successor
> Related: 前驱 I4 Phase 9（8 个空 BizModel 已识别）；roadmap `nop-code-invariant-loop-roadmap.md` Loop Rule T0（resolved）

## Supersession Note

**本计划已取消（cancelled）——核心前提经独立子 agent 审查证实为误判。**

审查发现 Nop 平台已通过 `ReflectionBizModelBuilder.buildActionField()`（`nop-graphql-core/.../reflection/ReflectionBizModelBuilder.java:330-335`）为所有 BizModel 方法（含继承的 CrudBizModel CRUD 方法）自动派生 `ActionAuthMeta`，权限格式为 `{bizObjName}:{opType}|{bizObjName}:{actionName}`（OR 语义）。8 个空 BizModel 的 CRUD action **已经有 auto-derived 权限**（如 `NopCodeFlow:query|NopCodeFlow:findPage`），与 `_nop-code.action-auth.xml` 声明的 `{BizObjName}:query` / `{BizObjName}:mutation` 匹配。

`docs-for-ai/02-core-guides/service-layer.md:209` 明确规定：

> `@Auth` 只放在自定义方法上；标准 CRUD 方法（`CrudBizModel` 继承的 `findPage`/`save` 等）由平台按 BizObjName 自动派生权限，**不需要也不应该重复标注**。

因此，audit finding AR-146(r8)/155(r10)/170 中「8 个空 BizModel 缺 @Auth」的 gap 描述基于对 Nop auth 机制的误解——CRUD 方法的 @Auth 不应添加。I4 Phase 9 的 ask-first gate 正确阻止了基于此误解的变更。

**处置**：AR-146(r8)/155(r10)/170 应改判为 `stale / false-premise`（auto-derived 权限已覆盖）。此改判记录在 daily log 中，不在此 plan 执行。Cycle 1 的 @Auth gated successor 正式关闭（cancelled，非 completed——因为不存在需要执行的工作）。

## Purpose

把 I4 Phase 9 中因 **ask-first Protected Area**（权限/认证模型）阻塞的 @Auth 契约一致性收口：为 nop-code 的 8 个空 CRUD BizModel 补 `@Auth` 注解，使 CRUD action 的权限检查与 `action-auth.xml` 声明一致。本计划完成后，Cycle 1 的最后一个 gated successor 完全关闭。

> **⚠ ask-first 门禁说明**：本计划涉及权限模型变更（新增 @Auth 会使先前无保护的 action 要求权限），属于用户可见行为变更。Plan Status 维持 `draft` 直到人工确认以下决策：
> 1. 8 个空 BizModel 的 CRUD action 是否需要权限保护（建议：是——当前 action-auth.xml 已声明 `query`/`mutation` permissions，但 BizModel 方法无 @Auth 导致声明未生效）
> 2. 只读查询使用 `permissions` 还是 `roles`（AR-155(r10) 建议 permissions）
> 3. mutation action 的权限粒度（建议沿用 action-auth.xml 的 `{BizObjName}:mutation` 约定）

## Current Baseline

> 已对 live repo 核对（2026-08-14）。

- **BizModel @Auth 现状（live grep 确认）**：
  - **有 @Auth 的 3 个 BizModel**（自定义 @BizQuery/@BizLoader 方法上有 `@Auth(permissions="NopCodeFile:query")` 等）：
    - `NopCodeFileBizModel` — 6 个自定义方法均有 @Auth
    - `NopCodeSymbolBizModel` — 自定义方法有 @Auth
    - `NopCodeIndexBizModel` — 自定义方法有 @Auth
  - **缺 @Auth 的 8 个空 CRUD BizModel**（仅继承 `CrudBizModel<T>`，无任何自定义方法，无 @Auth）：
    1. `NopCodeFlowBizModel`
    2. `NopCodeAnnotationUsageBizModel`
    3. `NopCodeFlowMembershipBizModel`
    4. `NopCodeUsageBizModel`
    5. `NopCodeDependencyBizModel`
    6. `NopCodeCallBizModel`
    7. `NopCodeSemanticEdgeBizModel`
    8. `NopCodeInheritanceBizModel`
- **CrudBizModel 默认方法无 @Auth**：`CrudBizModel.java`（nop-biz 框架基类）的 `findPage`/`save`/`delete`/`findList`/`get`/`findCount` 等方法均无 @Auth 注解——继承者不 override 则不触发权限检查。
- **action-auth.xml 声明完备**：`_nop-code.action-auth.xml`（generated base）已为全部 11 个实体声明 `{BizObjName}:query` 和 `{BizObjName}:mutation` permissions。但 `@Auth` 缺失意味着这些 permissions 声明在 API 调用层面未生效（仅在 UI 菜单层面起作用）。
- **真正剩余 gap**：8 个空 BizModel 的 CRUD action（继承自 CrudBizModel）无 @Auth → 无 API 级权限检查 → 与 action-auth.xml 声明不一致。

## Goals

- 8 个空 BizModel 的 query 类 action（findPage/findList/findCount/get/findFirst/deleted_findPage/deleted_get）有 `@Auth(permissions="{BizObjName}:query")`。
- 8 个空 BizModel 的 mutation 类 action（save/update/delete/saveOrUpdate/deleteByQuery 等）有 `@Auth(permissions="{BizObjName}:mutation")`。
- `@Auth` permissions 与 `action-auth.xml` 声明匹配（零漂移）。
- 通过 focused test 验证：无权限的调用被拒绝（403），有权限的调用通过。
- `./mvnw test -pl nop-code -am -T 1C` 全绿。

## Non-Goals

- 不修改 CrudBizModel 框架基类（nop-biz 框架核心，Protected Area plan-first）——仅在 nop-code 子类中 override 或通过其他机制补 @Auth。
- 不修改 NopCodeFileBizModel / NopCodeSymbolBizModel / NopCodeIndexBizModel（已有 @Auth 的 3 个）。
- 不引入新的权限模型或角色体系。
- 不修改 action-auth.xml 的 permission 声明（声明已完备）。
- 不处理 ORM cascadeDelete（Plan 1 scope）。

## Scope

### In Scope

- 8 个空 BizModel 的 CRUD action 补 @Auth 注解（override CrudBizModel 方法或使用其他 Nop 机制）。
- AR-155(r10)：确认只读查询使用 `permissions` 而非 `roles`。
- AR-170：验证 @Auth permissions 与 action-auth.xml 声明匹配。
- @Auth 契约一致性 focused test（验证权限检查生效）。
- `docs-for-ai/02-core-guides/service-layer.md` 若涉及 @Auth 约定则同步更新。

### Out Of Scope

- CrudBizModel 框架基类修改（Protected Area）。
- NopCodeFileBizModel / NopCodeSymbolBizModel / NopCodeIndexBizModel 变更（已有 @Auth）。
- action-auth.xml 变更（声明已完备）。
- 新增角色或权限体系设计。
- ORM cascadeDelete（Plan 1 scope）。

## Execution Plan

> 顺序执行（Phase）。⚠ **ask-first / 执行前人工确认**（AGENTS.md Protected Areas: 权限/认证模型 ask-first）。
>
> **实现方式调研**：CrudBizModel 默认方法无 @Auth，子类需要补。可能的实现路径：
> 1. **Override + @Auth**：在每个空 BizModel 中 override 需要保护的方法（findPage/save/delete 等），加 @Auth 注解，方法体调用 `super.xxx()`。缺点：样板代码多。
> 2. **Nop 约定机制**：调研 Nop 是否有 BizModel 级或 action 级的默认 @Auth 机制（如 xmeta 中的 `auth` 配置、或 GraphQL engine 根据 bizObjName + actionType 自动映射 permission）。若有，优先使用约定机制，避免样板代码。
> 3. **xpl/aop 注入**：调研 Nop 是否支持通过 xpl 模板或 AOP 为 CrudBizModel 方法批量注入 @Auth。
>
> Phase 1 先做实现方式调研（Decision），Phase 2 执行实施。

### Phase 1 - @Auth 实现方式调研与裁定

Status: planned
Targets: nop-biz CrudBizModel；Nop GraphQL engine auth 机制；现有 BizModel @Auth 模式（NopCodeFileBizModel / NopCodeSymbolBizModel）

- Item Types: `Decision`

- [ ] **调研 Nop auth 机制**：阅读 `docs-for-ai/02-core-guides/service-layer.md` 中 @Auth 相关内容；grep CrudBizModel 和 GraphQL engine 中 @Auth 的处理逻辑；确认是否存在 BizModel 级默认 @Auth 机制
- [ ] **裁定实现方式**：从 Override+@Auth / 约定机制 / xpl-aop 三条路径中选定一条，记录裁定理由（写入本 Phase Exit Criteria 或 `ai-dev/design/`）
- [ ] **确认权限粒度**：query 类 action → `permissions="{BizObjName}:query"`；mutation 类 action → `permissions="{BizObjName}:mutation"`（与 action-auth.xml 约定一致）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 实现方式已裁定（选定路径 + 理由记录）
- [ ] 权限粒度已确认（query/mutation 二分，与 action-auth.xml 一致）
- [ ] `No owner-doc update required`（调研裁定阶段不改代码/行为）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - @Auth 实施与测试

Status: planned
Targets: 8 个空 BizModel；`docs-for-ai/02-core-guides/service-layer.md`（若 @Auth 约定需更新）；测试 `TestAuthContractConsistency`

- Item Types: `Fix | Proof`

- [ ] **补 @Auth（8 个 BizModel）**：按 Phase 1 裁定的实现方式，为以下 8 个 BizModel 的 CRUD action 补 @Auth：
  - `NopCodeFlowBizModel` → `@Auth(permissions="NopCodeFlow:query")` / `@Auth(permissions="NopCodeFlow:mutation")`
  - `NopCodeAnnotationUsageBizModel` → `NopCodeAnnotationUsage:query` / `NopCodeAnnotationUsage:mutation`
  - `NopCodeFlowMembershipBizModel` → `NopCodeFlowMembership:query` / `NopCodeFlowMembership:mutation`
  - `NopCodeUsageBizModel` → `NopCodeUsage:query` / `NopCodeUsage:mutation`
  - `NopCodeDependencyBizModel` → `NopCodeDependency:query` / `NopCodeDependency:mutation`
  - `NopCodeCallBizModel` → `NopCodeCall:query` / `NopCodeCall:mutation`
  - `NopCodeSemanticEdgeBizModel` → `NopCodeSemanticEdge:query` / `NopCodeSemanticEdge:mutation`
  - `NopCodeInheritanceBizModel` → `NopCodeInheritance:query` / `NopCodeInheritance:mutation`
- [ ] **AR-170 一致性验证**：grep 全部新增 @Auth 的 permissions 值，与 `_nop-code.action-auth.xml` 中的 `<permissions>` 声明逐条比对，零漂移
- [ ] **test-first**：新增 `TestAuthContractConsistency`——对至少 2 个代表性 BizModel（如 NopCodeUsageBizModel + NopCodeCallBizModel），验证：
  - 无权限用户调用 findPage → 被拒绝（权限检查生效）
  - 有 `NopCodeUsage:query` 权限用户调用 findPage → 通过
- [ ] **类别清扫**：grep 全部 `*BizModel.java`（11 个），确认无 BizModel 的 CRUD action 仍缺 @Auth（3 个已有 @Auth 的 BizModel 的自定义方法 + 继承方法全覆盖）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 8 个空 BizModel 的 query action 有 `@Auth(permissions="{BizObjName}:query")`
- [ ] 8 个空 BizModel 的 mutation action 有 `@Auth(permissions="{BizObjName}:mutation")`
- [ ] AR-170：@Auth permissions 与 action-auth.xml 声明逐条匹配（零漂移，清单可追溯）
- [ ] AR-155(r10)：只读查询使用 permissions 而非 roles
- [ ] `TestAuthContractConsistency` 验证权限检查生效（无权限→拒绝，有权限→通过，`Tests run: ≥4, Failures: 0`）
- [ ] **接线验证**：@Auth 注解在 GraphQL action 调用链中被实际检查（测试断言 403 或权限拒绝异常）
- [ ] **无静默跳过**：@Auth 未覆盖的 action 若存在，抛出异常或显式标记，不静默放过
- [ ] **新功能测试覆盖**：权限拒绝路径有 focused test
- [ ] 若该 Phase 改变 live baseline（权限契约变更）：`docs-for-ai/02-core-guides/service-layer.md` 中 @Auth 约定已同步（若约定变化）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] AR-146(r8)：8 个空 BizModel 的 CRUD action 有 @Auth 保护
- [ ] AR-155(r10)：只读查询使用 permissions 而非 roles
- [ ] AR-170：@Auth permissions 与 action-auth.xml 声明匹配（零漂移）
- [ ] 权限检查有 focused test 覆盖（拒绝路径 + 通过路径）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步（若 @Auth 约定变化则更新 service-layer.md，否则明确 `No owner-doc update required`）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：@Auth 注解在运行时 GraphQL action 调用链中被实际检查（TestAuthContractConsistency 断言权限拒绝）
- [ ] `./mvnw test -pl nop-code -am -T 1C`
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（无——本计划 scope 明确：8 个 BizModel 补 @Auth + 一致性验证 + test）

## Non-Blocking Follow-ups

- Cycle 2 / I1 候选门禁「@Auth 系统性门禁」（I3 §D）——可在 Cycle 2 将 @Auth 一致性纳入 ORM/API 交叉检查静态门禁，长期防止回归。
- CrudBizModel 框架基类是否应默认提供 @Auth（框架级改进）——超出 nop-code scope，watch-only residual。

## Closure

Status Note: (待 closure 时填写)
Completed: (待 closure 时填写)

Closure Audit Evidence:

(待 closure 时填写)
