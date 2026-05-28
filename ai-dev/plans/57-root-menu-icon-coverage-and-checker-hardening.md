# 57 Root Menu Icon Coverage And Checker Hardening

> Plan Status: completed
> Last Reviewed: 2026-05-28
> Source: user request on 2026-05-28 + `ai-dev/plans/55-orm-xml-icon-rollout-and-checker.md` + `ai-dev/plans/56-orm-root-icon-top-menu-propagation.md`

## Purpose

把 source ORM 的模块级 root icon 覆盖补齐到当前仓库基线，并修正 checker 只校验 entity、不校验 root `<orm ext:icon>` 的缺口；同时把 source `action-auth.xml` 中显式声明为 `TOPM` / `SUBM` 的手写菜单 icon 也纳入校验，确保标准与手写菜单都具备明确 icon 来源。

## Current Baseline

- `ai-dev/tools/check-orm-icons.mjs` 当前只校验 source `model/*.orm.xml` 中的 `entity ext:icon`，不会检查根 `<orm ext:icon>`，因此模块级 TOPM icon 缺失不会被发现。
- live repo 中 19 个 source ORM 文件里，当前只有 `nop-auth/model/nop-auth.orm.xml` 已设置 root `ext:icon`；其余 source ORM 根节点均未设置。
- Plan 56 已把 TOPM 图标传播链改为 `root <orm ext:icon> -> *-meta/module-meta.json -> *-web/_*.action-auth.xml`，因此 root icon 缺失会直接表现为模块菜单 fallback 到默认图标。
- 现有 `check-orm-icons.mjs` 仍返回 `OK: all source ORM entities have ext:icon (19 files checked)`，说明实体级 icon 基线已满足，但它不能证明模块级 icon 覆盖完整。
- `nop-report` 当前手写 `nop-report.action-auth.xml` / `app.action-auth.xml` 不包含额外缺 icon 的 `TOPM` / `SUBM` 资源；`nop-report` 菜单顶部缺 icon 的 live 原因是 source ORM root 未配置 icon，生成结果回退为默认值。
- 当前仓库还没有固定规则去校验 source `action-auth.xml` 中的 `TOPM` / `SUBM` 手写菜单必须带 icon，因此这类问题也可能被静默遗漏。

## Goals

- 为当前 in-scope source ORM 根 `<orm>` 补齐 `ext:icon`，让所有模块菜单具备明确的 TOPM icon。
- 保持并验证 source `model/*.orm.xml` 中所有实体继续具备 `ext:icon`。
- 修正 checker，使其覆盖 source ORM 的 root icon、entity icon，以及 source `action-auth.xml` 中 `TOPM` / `SUBM` 资源的 icon 契约。
- 验证 `nop-report` 生成菜单不再使用默认 TOPM fallback icon，且 `nop-report` 手写菜单资源保持 icon 完整。

## Non-Goals

- 不改动生成模板的 icon 传播机制；Plan 56 已完成该链路。
- 不重命名现有 checker 脚本。

## Scope

### In Scope

- source `model/*.orm.xml` 根 `<orm ext:icon>` 覆盖补齐
- `ai-dev/tools/check-orm-icons.mjs` root + entity + source action-auth menu 校验增强
- `nop-report` focused 生成验证与菜单 icon 结果核对
- owner docs / daily log / plan 收口

### Out Of Scope

- 非 source ORM 的 generated `_app.orm.xml` 手改
- 与菜单 icon 无关的模板重构
- 仓库级所有手写菜单资源风格统一

## Execution Plan

### Phase 1 - Patch Root Coverage And Checker

Status: completed
Targets: `**/model/*.orm.xml`, `ai-dev/tools/check-orm-icons.mjs`

- Item Types: `Fix | Proof`

- [x] 为所有 in-scope source ORM 根 `<orm>` 补齐语义化 `ext:icon`
- [x] 扩展 `check-orm-icons.mjs`，使其检查 source ORM 的 root `<orm ext:icon>`、`entity ext:icon`，以及 source `action-auth.xml` 中 `TOPM` / `SUBM` 资源的 `icon`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] live repo 中不再存在缺失 root `ext:icon` 的 in-scope source ORM 文件
- [x] `node ai-dev/tools/check-orm-icons.mjs` 通过，且输出语义与 root + entity + source menu 三层覆盖一致
- [x] **端到端验证**：No new end-to-end path required beyond existing generation path; focused generation proof is handled in Phase 2
- [x] **接线验证**：No new wiring beyond existing Plan 56 propagation path; this phase only restores required source metadata inputs
- [x] **无静默跳过**：checker 对缺失 root、entity 或 source TOPM/SUBM menu icon 会以非零退出码失败，不会静默忽略
- [x] `docs-for-ai/` owner doc 已更新以反映 root + entity icon 的必备约束
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Verify Generated Menus And Close

Status: completed
Targets: `nop-report`, affected generated outputs, docs/logs/plan`

- Item Types: `Proof | Follow-up`

- [x] focused 验证 `nop-report` 的 generated TOPM icon 不再回退到默认值，且 source 自定义菜单资源未出现缺 icon 项
- [x] 复核当前 source ORM entity icon 基线仍保持完整
- [x] 完成 docs / log / closure evidence 收口

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw install -pl nop-report/nop-report-meta,nop-report/nop-report-web -am -DskipTests -T 1C` 通过
- [x] `nop-report/nop-report-meta/.../_module-meta.json` 已输出非空 `icon`
- [x] `nop-report/nop-report-web/.../_nop-report.action-auth.xml` 的 TOPM icon 不再是 `ion:grid-outline`
- [x] `nop-report` source `action-auth.xml` 中显式 `TOPM` / `SUBM` 资源无缺失 `icon`
- [x] **端到端验证**：从 source `nop-report/model/nop-report.orm.xml` 根 `ext:icon` 到 generated `_nop-report.action-auth.xml` TOPM icon 的完整传播路径已验证
- [x] **接线验证**：Plan 56 的 `module-meta.json -> orm-web` 接线在 `nop-report` live 生成结果中已验证
- [x] **无静默跳过**：No new runtime branch added; not applicable beyond checker failure semantics validated in Phase 1
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（若 docs/log/plan 更新）
- [x] 独立 closure audit 已完成并记录

## Closure Gates

- [x] Phase 1 和 Phase 2 均标记为 `completed`
- [x] source ORM 的 root + entity icon 覆盖已满足当前仓库基线
- [x] `nop-report` 菜单 icon 已从默认 fallback 收口到 source root icon
- [x] `docs-for-ai/`、`ai-dev/logs/2026/05-28.md`、本计划文件文本一致
- [x] 独立 closure audit 确认本计划可关闭

## Deferred But Adjudicated

- 本计划不把 `FNPT` 或未声明 `resourceType` 的普通资源纳入 closure gate；只覆盖 source `action-auth.xml` 中显式 `TOPM` / `SUBM` 菜单资源。

## Non-Blocking Follow-ups

- 如需把手写 `action-auth.xml` icon 覆盖纳入固定仓库规则，可在后续计划中引入独立 checker，而不是在本计划里扩大范围。

## Closure Evidence

- `node ai-dev/tools/check-orm-icons.mjs` -> `OK: all source ORM roots/entities and source TOPM/SUBM menus have icons (19 ORM files, 37 action-auth files checked)`
- `./mvnw install -pl nop-report/nop-report-meta,nop-report/nop-report-web -am -DskipTests -T 1C` -> passed
- `node ai-dev/tools/check-doc-links.mjs --strict` -> passed
- Independent closure audit: `ses_190ebfca7ffeYQYTI1ok586ZYH` (implementation PASS; closure text synchronized and plan closed)
