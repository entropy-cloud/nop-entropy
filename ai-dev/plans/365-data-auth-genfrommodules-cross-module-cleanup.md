# 365 跨模块 data-auth GenFromModules 坏标签清理

> Plan Status: draft
> Last Reviewed: 2026-09-26
> Source: ai-dev/audits/2026-09/2026-09-25-1410-deep-audit-nop-task-quality/04-security.md [维度04-02] 及 07-verification-addendum-2026-09-26.md；同根因的 nop-task-app 一处由 364-nop-task-audit-confirmed-defect-fixes.md Phase 1 修复
> Related: 364-nop-task-audit-confirmed-defect-fixes.md

## Purpose

[维度04-02]（P1）确认平台级断裂：`app.data-auth.xml` 引用 2025-02 已改名的 `GenFromModules` 标签，gen-extends 上下文 `allowUnknownTag=true` 导致静默产出空 data-auth 模型，`DefaultDataAuthChecker` 对空模型 fail-open（无行级过滤、无角色校验）。nop-task-app 一处由 plan 364 修复；本计划承接其余跨模块批量修复与防复发守卫，使本计划关闭时主源码/资源口径下全仓不再存在该坏标签的引用。

## Current Baseline

- commit `8438c69a2f`（2025-02-21）将 `nop-service-framework/nop-biz-auth-core/.../_vfs/nop/auth/xlib/auth-gen.xlib` 中 `GenFromModules` 改名为 `GenDataAuthFromModules` / `GenActionAuthFromModules`；app 配置与 docs、codegen 模板未同步（断裂约 19 个月）。
- live 树带坏标签的 app.data-auth.xml 实测 12 个（nop-datav、nop-oauth、nop-wf、nop-metadata、nop-ai、nop-dyn、nop-retry、nop-code、nop-credential 及 demo 等；执行时以 `rg -l "GenFromModules" --glob '**/app.data-auth.xml'` 复核清单为准），其中 nop-task-app 由 plan 364 修复，本计划 scope 为其余 11 个；另含 2 份 auth docs（`docs/dev-guide/auth/auth.md` 与 `docs-en/dev-guide/auth/auth.md`）与 1 个 codegen 模板 `.xgen`。
- `XDslExtender.java:349` 在 gen-extends 上下文 `setAllowUnknownTag(true)` → 坏标签静默产出空 `<data-auth>`；`DefaultDataAuthChecker.java:181-182,200-201` 对 `objAuth == null` return true / return null（fail-open）。
- 惯例对照：sys/job/file/batch/rule/tcc/report/auth-app 等 8 个 app 已改为现行写法（纯 `<objs/>` + `x:extends` 或带 `xpl:lib` 的新标签）。
- nop-task 自身的 `nop-task-app/.../app.data-auth.xml` 不在本计划内（plan 364 Phase 1 修复并带回归测试）。

## Goals

- 全部带坏标签的 app 配置改为现行写法，逐模块断言 data-auth 模型解析成功且展开非空。
- codegen 模板与 `docs/dev-guide/auth/auth.md` 同步，杜绝新生成模块再次种下坏标签。
- 建立防复发守卫（全仓扫描或逐模块 parse 冒烟测试），进入常规测试可执行范围。

## Non-Goals

- 不改 `DefaultDataAuthChecker` 的 fail-open 语义（对未知 obj 放行是否收敛为 fail-closed 属独立安全裁定，另行开题）。
- 不做 data-auth 字段级裁剪、租户过滤等增强。
- 不重构 auth-gen.xlib 的标签体系。
- nop-task-app 的 `app.data-auth.xml`（plan 364 Phase 1 修复并带回归测试）；`nop-dev-tools/nop-maven-shaded-plugin` 测试夹具中自定义的同名 `GenFromModules` xlib 标签定义（自有标签定义而非引用断裂，不在守卫口径内）。

## Scope

### In Scope

- 11 个带坏标签的 `app.data-auth.xml`（nop-task-app 除外）；auth codegen 模板 `.xgen`；`docs/dev-guide/auth/auth.md` 与 `docs-en/dev-guide/auth/auth.md`；防复发守卫测试。

### Out Of Scope

- Non-Goals 所列项；各模块 data-auth 规则内容完备性（见 Non-Blocking Follow-ups）。

## Execution Plan

### Phase 1 - 批量修复与防复发守卫

Status: planned
Targets: 11 个 `app.data-auth.xml`（nop-task-app 归 364）、auth codegen 模板 `.xgen`、`docs/dev-guide/auth/auth.md` + `docs-en/dev-guide/auth/auth.md`、守卫测试落点（建议 nop-auth-service 或独立 tests 模块现有测试树）

- Item Types: `Fix | Proof`

- [ ] [Proof] `rg` 生成坏标签全量清单（主源码/资源口径，排除 ai-dev、audits、历史记录、`nop-dev-tools/nop-maven-shaded-plugin` 测试夹具的自定义同名标签定义），清单记入本计划执行记录
- [ ] [Fix] 逐个 app 配置改为现行写法（`GenDataAuthFromModules` + `xpl:lib="/nop/auth/xlib/auth-gen.xlib"`，或对照惯例模块的纯 `<objs/>` 写法，全仓统一一种）
- [ ] [Fix] codegen 模板 `.xgen` 同步现行写法
- [ ] [Fix] docs 与 docs-en 两份 auth.md 示例同步
- [ ] [Proof] 防复发守卫在档：全仓扫描断言主资源无 `GenFromModules` 引用命中（口径同上，含夹具排除），或逐模块 parse 冒烟测试（断言解析成功且 objs 非空），可在 `./mvnw test` 常规执行

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `rg "GenFromModules"`（主源码/资源口径，排除 ai-dev 与历史记录、`nop-dev-tools/nop-maven-shaded-plugin` 测试夹具）0 命中
- [ ] 每个修复模块的 data-auth 模型 parse 非空有测试或守卫证据
- [ ] 守卫测试纳入常规测试执行并通过
- [ ] 受影响模块 focused 测试通过（`./mvnw test -pl <affected> -am` 或等效）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] owner 文档裁定：auth 使用文档已同步即视为 doc-sync 完成；如无额外契约变化写明 No owner-doc update required

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 坏标签文件已修复且逐条对账无遗漏
- [ ] 防复发守卫在档并通过（接线验证：守卫真实扫描/加载到修复后的文件，而非仅单测自证）
- [ ] `./mvnw test`（受影响模块 focused）通过
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 365-data-auth-genfrommodules-cross-module-cleanup.md --strict` 退出码 0

## Deferred But Adjudicated

### DefaultDataAuthChecker fail-open 语义收敛

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: fail-open→fail-closed 属全局安全语义变更，影响所有未配 data-auth 的实体的默认行为，需独立设计与评审；坏标签清除后，空模型 fail-open 的触发面已消除。
- Successor Required: `no`（如需收敛另开计划）

## Non-Blocking Follow-ups

- 各模块 data-auth 规则内容本身的完备性审查（是否该配而未配行级规则）属各模块 owner 范畴。

## Closure

Status Note: （完成或关闭时填写）
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Audit Session: （关闭时填写）
- Evidence: （关闭时填写）

Follow-up:

- 见 Non-Blocking Follow-ups
