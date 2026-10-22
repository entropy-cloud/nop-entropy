# 2499 WI11 模板 i18n（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI11

## Purpose

模板文本按 locale 切换：单元格文本支持 i18n 键约定 + 表达式 i18n 帮助函数；字典翻译已按 locale（既有）。

## Current Baseline

- 字典翻译已 locale 感知（`IXptRuntime.getDict` 用 `ContextProvider.currentLocale()`）；模板静态文本无 i18n 机制。
- 平台 i18n 基座：`I18nMessageManager.instance().getMessage(locale, key, default)` + `normalizeLocale`（site-map 消费先例）。
- XptModelInitializer 将模板静态文本转为单元格 value/表达式。

## Goals

- 文本 i18n 约定：**复用平台既有 `@i18n:` 约定**（CoreConstants.I18N_VAR_PREFIX + `I18nMessageManager.resolveI18nVar`：多 key 逗号回退 + `|缺省值`）——模板单元格静态文本以 `@i18n:` 开头时，XptModelInitializer.normalizeValueExpr 包装为 valueExpr → 求值期 `xptRt.i18nVar(text)`（= resolveI18nVar(currentLocale, text)）解析；无 `|` 缺省时回退原文本。非 `@i18n:` 文本不处理。
- 表达式帮助函数：`xptRt.i18n(key, default)`（IXptRuntime default 方法，供 formatExpr/表达式使用）。
- locale 来源：`ContextProvider.currentLocale()`（与会话一致）。
- 测试：`@i18n:` 文本在 locale 消息存在/缺失两态、xptRt.i18n 回退、非 i18n 文本不回归。
- docs/design `template-i18n.md`、owner doc、roadmap、logs。

## Non-Goals

- 格式（数字/日期）的 locale 化重写（formatExpr 已可表达）；模板文件级 locale 变体（residual 登记）。

## Scope

### In Scope

- `nop-report/nop-report-core`（Initializer 识别、XptRuntime.i18n、测试）、docs、roadmap、logs

### Out Of Scope

- 模板文件 locale 变体加载；设计器。

## Execution Plan

### Phase 1 - i18n 文本约定与帮助函数

Status: completed
Targets: `nop-report/nop-report-core`

- Item Types: `Feature`

- [x] `IXptRuntime.i18n(key, default)`（getMessage + currentLocale，避开 normalizeLocale 的 enabled-locales 静默回退）与 `i18nVar(text)`（resolveI18nVar）default 方法
- [x] XptModelInitializer.normalizeValueExpr 增 `@i18n:` 分支：包装为 valueExpr → 求值期经 i18nVar 解析；缺失回退原文本（注册消息跨用例泄漏在测试中登记：registerMessages 不随 clear 清理）
- [x] core 测试：locale 消息存在/缺失、xptRt.i18n、非 i18n 回归

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-core -am` 绿（分层验证口径：Closure Gate 的 service -am 覆盖下游）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 文档收口

Status: completed
Targets: docs

- Item Types: `Decision`

- [x] design `template-i18n.md`（约定/回退/模板文件变体 residual）；owner doc；roadmap WI11 勾选；logs 收口

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端：locale 消息切换渲染文本（测试）
- [x] 无静默跳过
- [x] owner doc + design 同步
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录
- [x] `check-plan-checklist.mjs --strict` 收口运行

## Deferred But Adjudicated

### 格式（数字/日期）locale 化帮助函数

- Classification: `watch-only residual`
- Why Not Blocking Closure: formatExpr 表达式已可按 locale 表达格式逻辑；roadmap WI11 勾选口径=文本/字典（字典既有 locale 感知）
- Successor Required: no
- Successor Path: —

### 模板文件 locale 变体加载

- Classification: `watch-only residual`
- Why Not Blocking Closure: 平台无 VFS locale 变体通用机制（site-map 为应用层显式命名）；文本级 i18n 覆盖主场景
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- formatExpr 的 locale 敏感格式帮助函数

## Closure

Status Note:
 WI11 已实现并独立审计通过：`@i18n:` 模板文本经 XptModelInitializer 包装为 `xptRt.i18nVar` valueExpr，求值期按 currentLocale 经 resolveI18nVar 解析（复用平台约定，缺失回退原文本）；`xptRt.i18n(key,default)` 帮助函数避开 normalizeLocale。两项 residual（格式 locale 化、模板文件变体）已登记 watch-only。核验测试与文档检查全部绿，plan 标 completed。

Completed:
- 2026-10-22 Phase 1：IXptRuntime.i18n/i18nVar + XptModelInitializer `@i18n:` 分支 + TestTemplateI18n（2 用例）
- 2026-10-22 Phase 2：design template-i18n.md、owner doc、roadmap WI11 勾选、logs 收口

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（新会话 subagent，model account:bigmodel-individual-coding-plan/GLM-5.3-Flash），2026-10-22
- Evidence:
  1. 实现 PASS：`IXptRuntime.java:73-84`（default i18n=getMessage+ContextProvider.currentLocale；i18nVar=resolveI18nVar，均不经 normalizeLocale）；`XptModelInitializer.java:153-158`（CoreConstants.I18N_VAR_PREFIX 分支包装 `xptRt.i18nVar("...")` valueExpr）。
  2. 测试 PASS：`./mvnw test -pl nop-report/nop-report-core -Dtest=TestTemplateI18n` → Tests run: 2, Failures: 0；core 全量 → Tests run: 125, Failures: 0, BUILD SUCCESS。
  3. Closure Gate service -am PASS：`./mvnw test -pl nop-report/nop-report-service -am` → BUILD SUCCESS（各模块 0 failures）。
  4. 文档 PASS：design `ai-dev/design/nop-report/template-i18n.md` 与实现一致（复用 @i18n:/resolveI18nVar、回退原文本、两项 residual）；owner doc `docs-for-ai/03-modules/nop-report.md` 含文本 i18n 段且缺失清单登记模板文件 locale 变体 residual；roadmap WI11 已勾选（L74）；`ai-dev/logs/2026/10-22.md` WI11 条目在。
  5. 链接检查 PASS：`node ai-dev/tools/check-doc-links.mjs --strict` → 0 issues，退出码 0。
  6. 契约一致性 PASS：复用 resolveI18nVar、格式 residual、模板文件变体 residual 三处（plan/design/实现）口径一致。
  7. Anti-Hollow PASS：TestTemplateI18n.testI18nTextResolved 断言渲染 HTML 含「测试报表标题」且不含 `@i18n:`（模板加载→包装→求值期解析全链路连通）；非 i18n 静态文本不回归。
