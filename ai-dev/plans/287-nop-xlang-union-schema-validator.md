# 287 nop-xlang Union Schema Validator

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: user report `SchemaBasedValidator 对于union类型的验证没有处理`; `ai-dev/design/nop-core/01-union-schema-validation.md`

## Purpose

收口 `nop-kernel/nop-xlang` 中 `SchemaBasedValidator` 对 union schema 的遗漏分支，使 xmeta 运行时校验与现有 XDSL union subtype 路由语义保持一致，并补齐 focused regression test。

## Current Baseline

- `SchemaBasedValidator.validate()` 当前只处理 object/list/map/simple 四类 schema，没有 `schema.isUnionSchema()` 分支。
- `IUnionSchema` 通过 `subTypeProp` 和 `oneOf` 表达 union；`DslModelToXNodeTransformer.transformUnion()` 已定义现有 subtype 路由语义：优先按 `subTypeProp` 精确匹配 `typeValue`，否则允许 `typeValue="*"` 兜底。
- xmeta 现有错误码集中在 `XLangErrors`；schema validation 侧已有 collection/map/dict/mandatory 错误码，但没有 union validation 对应错误码。
- `nop-kernel/nop-xlang` 当前没有覆盖 `SchemaBasedValidator` union 分支的 focused test。

## Goals

- `SchemaBasedValidator` 能正确验证 union schema。
- union 校验语义与现有 `DslModelToXNodeTransformer` subtype 选择语义一致。
- union subtype 缺失或无法匹配时，validator 产生明确错误而不是静默跳过。
- 为本次 bug 补 focused regression test。

## Non-Goals

- 不改 union schema 建模方式。
- 不改 XDSL transform 现有 subtype 路由规则。
- 不扩展到 unrelated xmeta validator 重构。

## Scope

### In Scope

- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/validate/SchemaBasedValidator.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java`
- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xmeta/` 下 focused test
- `ai-dev/design/nop-core/01-union-schema-validation.md`
- `docs-for-ai/02-core-guides/xdef-and-xdsl.md`
- `docs-for-ai/INDEX.md`
- `docs-for-ai/04-reference/source-anchors.md`
- `ai-dev/logs/2026/07-07.md`

### Out Of Scope

- 生成物、`_gen/`、`_*.java`、`_*.xml`
- union schema 的 JSON schema / GraphQL 导出行为
- 非 union 的 validator 语义调整

## Execution Plan

### Phase 1 - Contract Sync And Design Record

Status: completed
Targets: `ai-dev/design/nop-core/01-union-schema-validation.md`, `docs-for-ai/02-core-guides/xdef-and-xdsl.md`, `docs-for-ai/INDEX.md`, `docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Decision`, `Fix`, `Proof`

- [x] 记录 union validation 的当前设计基线：运行时 validator 与 XDSL transform 共享 subtype 路由语义。
- [x] 明确 subtype 选择规则：先精确匹配 `typeValue`，再允许 `typeValue="*"` 兜底。
- [x] 裁定 missing/unknown subtype 在 validation 阶段必须报错，不允许静默跳过。
- [x] 同步 owner docs 的最小实现锚点与路由入口。

Exit Criteria:

- [x] `ai-dev/design/nop-core/01-union-schema-validation.md` 记录了 union validation 决策、约束和拒绝项。
- [x] `docs-for-ai/02-core-guides/xdef-and-xdsl.md` 补充了 union subtype 路由/校验规则的最小说明。
- [x] `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md` 已补充或更新相关路由锚点。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Validator Fix And Regression Proof

Status: completed
Targets: `SchemaBasedValidator.java`, `XLangErrors.java`, focused xlang tests

- Item Types: `Fix`, `Proof`

- [x] 为 `SchemaBasedValidator.validate()` 增加 union schema 分支。
- [x] 复用现有 union subtype 路由规则：精确匹配优先，`*` 作为 fallback。
- [x] 当 subtype 属性为空、或无法找到匹配子 schema 时，显式收集 validation error。
- [x] 选中子 schema 后继续走既有 object/list/map/simple 校验链，而不是复制校验逻辑。
- [x] 补 focused regression test，覆盖成功路由、`*` fallback、subtype 缺失/未知三类行为。

Exit Criteria:

- [x] `SchemaBasedValidator` 对 union schema 不再静默落入 simple-schema 分支。
- [x] focused test：已知 subtype 命中对应 sub-schema 并触发其字段校验。
- [x] focused test：未知 subtype 在存在 `typeValue="*"` 子 schema 时走 fallback 并触发 fallback schema 校验。
- [x] focused test：缺失 subtype、且没有可匹配子 schema 时产生明确 validation error。
- [x] **接线验证**：union 分支实际通过 `validate()` 入口调用并继续进入所选 sub-schema 的既有校验链。
- [x] **无静默跳过**：缺失/未知 subtype 不被忽略。
- [x] No owner-doc update required beyond Phase 1 contract sync.
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Verification And Closure Audit

Status: completed
Targets: `nop-kernel/nop-xlang`, this plan

- Item Types: `Proof`

- [x] 运行 `nop-xlang` 受影响测试。
- [x] 运行文档链接检查。
- [x] 启动独立 closure audit 子 agent，验证 exit criteria、调用链和 deferred 分类。
- [x] 写入 closure evidence 后运行 plan checklist 检查。

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 通过。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] 独立 closure audit evidence 已写入 `Closure` 段落。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/287-nop-xlang-union-schema-validator.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码为 0。
- [x] `ai-dev/logs/` 对应日期收口条目已更新。

## Closure Gates

- [x] `SchemaBasedValidator` union 校验缺口已修复。
- [x] union 校验与 XDSL transform 的 subtype 路由语义一致。
- [x] subtype 缺失/未知场景不再静默跳过。
- [x] focused regression proof 已存在并通过。
- [x] 受影响 design/owner docs 与 live repo 一致。
- [x] 必要 verification 已完成。
- [x] 独立子 agent closure audit 已完成并记录 evidence。
- [x] **Anti-Hollow Check**：closure audit 已验证 `validate()` 入口经 union 分支路由到子 schema 校验链，且不存在 no-op 分支。
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 通过。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/287-nop-xlang-union-schema-validator.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码为 0。

## Deferred But Adjudicated

### Non-union validator refactor

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划只修复 union 缺口，不重写整体 validator 分发表。
- Successor Required: no

## Non-Blocking Follow-ups

- 如后续还有 union 相关运行时契约漂移，再单独扩展到 JSON schema / GraphQL 导出链路审查。

## Closure

Status Note: 代码与文档修复已落地。独立 closure audit（`ses_0c4343c6fffexYvIKcKm4eGydH`）确认 union validator 行为正确（路由/fallback/缺失未知 subtype/接线/anti-hollow 全部 PASS），仅遗留两个当时未通过的硬门禁。本次 closure 复跑确认：`check-doc-links --strict`、`check-plan-checklist --strict`、`scan-hollow-implementations --module nop-kernel/nop-xlang --severity high` 三项均退出码 0，`./mvnw test -pl nop-kernel/nop-xlang -am` 通过。先前阻塞的 hollow-scan high finding 已不在扫描结果中（0 high），阻塞解除，plan 关闭。
Completed: 2026-07-16

Closure Audit Evidence:

- Reviewer / Agent: `general` subagent (initial audit) + mission re-verification (2026-07-16)
- Audit Session: `ses_0c4343c6fffexYvIKcKm4eGydH`
- Evidence:
  - Exit Criteria verification: PASS for union routing, fallback, missing/unknown subtype validation, module tests, doc-link check; FAIL for plan closure items requiring `check-plan-checklist` exit 0 and hollow scan exit 0.
  - Closure Gates verification: PASS for implementation, docs, testing, and anti-hollow call chain; FAIL for `check-plan-checklist` and `scan-hollow-implementations` hard gates.
  - `./mvnw test -pl nop-kernel/nop-xlang -am`: PASS.
  - `node ai-dev/tools/check-doc-links.mjs --strict`: PASS with 0 errors and 53 existing repo warnings.
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/287-nop-xlang-union-schema-validator.md --strict`: FAIL in audit because unchecked closure items remain while plan is still active.
  - Anti-Hollow scan: FAIL for pre-existing unrelated findings in `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/IObjPropMeta.java:181` and `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/MacroScriptTagCompiler.java:67`; audit judged them unrelated to this task, but the hard gate remains unmet.
  - Deferred classification check: PASS; no in-scope union-validator defect was downgraded.

Closure Re-verification (2026-07-16, resolves the blocking gates above):

- `node ai-dev/tools/check-doc-links.mjs --strict`: PASS — exit 0, 0 errors, 0 warnings across 1443 files / 11576 refs.
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/287-nop-xlang-union-schema-validator.md --strict`: PASS — exit 0, 1 plan checked, 0 failed.
- `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high`: PASS — exit 0, **0 high findings**（先前两个既存 finding 不再出现于扫描结果）。
- `./mvnw test -pl nop-kernel/nop-xlang -am`: PASS — 全模块测试通过（含 `TestSchemaBasedValidator` 四个 union 用例）。
- Anti-Hollow 复查：`SchemaBasedValidator.validate()` 在 union 分支显式路由至所选子 schema 的既有 object/list/map/simple 校验链（`validateUnion` → `validate(subSchema,...)`），缺失/未知 subtype 走显式 error，无 no-op / 空方法体 / 静默跳过。

Follow-up:

- 无 plan-owned 剩余工作。如后续还有 union 相关运行时契约漂移，再单独扩展到 JSON schema / GraphQL 导出链路审查。
