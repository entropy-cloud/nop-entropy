# 2490 WI2 声明式报表参数模型 + 查询表单（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI2；`ai-dev/design/nop-report/parameter-model.md`
> Related: 2489-wi1-report-auth-wiring.md

## Purpose

模板可声明参数（内嵌 XPT 模型），引擎统一校验/默认值/注入 `${param.*}`；平台暴露参数表单 schema 与候选项接口，前端可生成 AMIS 查询表单。

## Current Baseline

- 参数只能由调用方 `scope.setLocalValues(data)` 硬塞，模板 `${entity.field}`/`${表达式}` 取值；无声明、无校验、无表单。
- XPT workbook 模型（`workbook.xdef` 的 `XptWorkbookModel`）已内嵌 editors/viewers 等 model；xdef 扩展后经 precompile（generate-sources 阶段 exec-maven-plugin）重生成 `_gen` 类。
- 引擎入口：`generateFromXptModel(workbook, ctx)` → `ReportRenderHelper.renderModel` → `ExpandedSheetGenerator`；所有渲染器共享展开逻辑。
- `${param.xxx}` 当前无特殊语义——会按普通 scope 路径取值（取不到为 null），注入 `param` Map 即可生效。
- nop-report-web 有实体 CRUD AMIS 页面；无参数表单相关 API。
- 平台 AMIS schema 以 Map/XNode 形式由 BizModel action 返回（GraphQL 输出 JSON）。

## Goals

- `workbook.xdef` 的 `XptWorkbookModel` 新增 `<params>` 内嵌声明（name/type/label/required/defaultValue/controlType/multiple/dependsOn/候选项来源），precompile 重生成。
- 引擎在展开前解析参数：取 scope `param` 输入 → required/type 校验 → 默认值 → 注入解析后的 `param` Map；错误 `nop.err.report.param.*` 快速失败。
- `nop-report-service` 新增 BizModel：`getReportFormSchema(reportPath)`（参数声明 → AMIS form schema）、`getReportParamOptions(reportPath, paramName, dependsValues)`（静态/字典/SQL 三种候选来源）。
- 参数以「解析后的规范化值」参与缓存键的约定写入 owner doc（WI6 消费）。

## Non-Goals

- 查询表单的可视化设计器（WI13）；数据级权限（WI3）；缓存落地（WI6）；PDF/图表。
- 不改数据集标签取数语义；级联只影响候选集（dependsOn 声明 + options 接口按依赖值查询），不自动改已选值。

## Scope

### In Scope

- `nop-kernel/nop-xdefs` workbook.xdef、`nop-format/nop-excel`（重生成模型 + 提交 _gen）
- `nop-report/nop-report-core`：参数解析/校验/注入 + 错误码 + 测试
- `nop-report/nop-report-service`：表单 schema / 参数候选项 BizModel + 测试
- owner doc、runbook（参数声明语法与 `${param.*}` 用法）、design parameter-model.md 状态同步、roadmap、logs

### Out Of Scope

- nop-report-web 前端 AMIS 页面（schema API 供页面后续消费）
- `nop-report-demo` 模板示例（可选加一个 demo 模板，非必须）

## Execution Plan

### Phase 1 - 参数模型与引擎解析

Status: completed
Targets: `nop-kernel/nop-xdefs`、`nop-format/nop-excel`、`nop-report/nop-report-core`

- Item Types: `Feature`

- [x] workbook.xdef `XptWorkbookModel` 增 `<params>`：param(name/type/label/required/defaultValue/controlType/multiple/dependsOn) + 候选来源（静态 options 列表 / dictName / optionsSql，optionsSql 可选 querySpace 属性）；type 取值 string/int/float/boolean/date/datetime（string 落 xdef、校验落 resolver）；错误码消息英文；precompile 重生成 `_XptWorkbookModel`/`XptParamModel`
- [x] core `ReportParamResolver`：从 scope 取 `param` 输入 Map → required 校验（缺失报错）、类型转换（string/int/float/boolean/date）、defaultValue 回填 → 注入解析后 `param` Map 到 scope；未知输入（未声明的参数名）忽略；错误码 `nop.err.report.param.*`（missing-required/type-error，带 paramName）
- [x] 挂接 `ExpandedSheetGenerator.generate`（展开前执行，覆盖全部渲染器与 generateFromXptModel）；resolver 幂等（覆盖式写 `param`，同一 renderer 多次 generate 语义稳定）
- [x] core 测试：required 缺失报错、默认值回填、类型转换成功/失败、`${param.xxx}` 在单元格表达式中取到值（内存模板展开断言）

裁定（收口回 design parameter-model.md）：契约原「未声明即引用 → 展开期报错」**不实现**——XPL 动态取值无法静态识别 `${param.x}` 引用，静态校验必产生误报，裁定为运行时取 null（文档化）；契约原「options 越界 → 快速失败」**不实现**——候选集可动态（SQL/字典），后端不做枚举闭合校验，输入合法性由控件与下游语义约束。两处裁定回写 design。

Exit Criteria:

- [x] xdef 改动经 precompile 重生成，无手改 `_gen`（git diff 显示 _gen 变更由生成器产生）
- [x] `./mvnw test -pl nop-report/nop-report-core -am` 绿（含 nop-excel 重生成后编译）
- [x] `${param.xxx}` 端到端：模板单元格表达式经展开输出取到参数值（测试断言）
- [x] design parameter-model.md 两条裁定回写（或明确记录由 Phase 2 收口承接）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 表单 schema 与候选项接口

Status: completed
Targets: `nop-report/nop-report-service`（BizModel 与既有 8 个 @BizModel 同域，复用其 JDBC/AutoTest 测试基建；web 前端页面后续消费）

- Item Types: `Feature`

- [x] `NopReportFormBizModel`（`@BizModel`，service 模块）：`getReportFormSchema(reportPath)`——**经 `getXptModel(reportPath)` 加载模板**（自动触发 WI1 报表级授权，未授权 `nop.err.report.auth.*`），参数声明 → AMIS form schema Map；`getReportParamOptions(reportPath, paramName, dependsValues)`——同样先过 getXptModel 授权，再按参数声明的候选来源返回 [{label,value}]：静态 / 字典（`IDictProvider`）/ SQL（`IJdbcTemplate`；声明了 querySpace 的先 `checkDataSourceAccess`，未声明走默认数据源由报表级检查覆盖——与 WI1 口径一致）
- [x] service beans 注册（report-auth.beans.xml 同目录新建或复用手写文件）；service AutoTest 测试：schema 生成断言、三种候选来源、**未授权调 schema/options 接口被拒（deny 路径）**
- [x] controlType→AMIS 控件映射表写入 owner doc `docs-for-ai/03-modules/nop-report.md`（规范性内容，以 owner doc 为准）；runbook 增参数声明语法与 `${param.*}` 用法；design parameter-model.md 落两条裁定 + 状态 active；roadmap 勾选；logs 收口

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] schema/候选项接口经测试断言（AMIS 结构关键键存在；三种来源各一用例；未授权 deny 用例）
- [x] owner doc + runbook 增参数用法；`node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端：声明参数的模板 → getRenderer 渲染 → `${param.xxx}` 生效（core 测试）；表单 schema/options 接口授权先行（service 测试 deny 路径）
- [x] 参数校验无静默跳过（required 缺失/类型错误必抛 `nop.err.report.param.*`）
- [x] owner doc + runbook + design 同步（含 controlType→AMIS 映射表、缓存键参与约定、两条不实现裁定）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿（覆盖 core/excel 重生成）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- demo 模板示例（report-with-params 已存在，可在 runbook 引用）
- 级联 options 的依赖值类型校验增强（WI13 设计器消费时细化）

## Closure

Status Note:
WI2 两个 Phase 均已实现并经独立 closure audit 核验通过（2026-10-22）。审计中发现一处 doc-sync 缺口——design `parameter-model.md` 未实际回写两条「不实现」裁定（且残留「展开期报错」「nop-report-web 暴露」两条与实现矛盾的旧契约文本），审计当场修正（scope 改 nop-report-service；运行时契约改「未声明引用运行时取 null」「候选集不做枚举闭合校验」两条裁定）。修正后 check-doc-links --strict 退 0。产品代码零改动。

Completed:
- Phase 1：workbook.xdef `<params>` 段 + precompile 重生成 `_XptWorkbookModel`/`XptParamModel`；`ReportParamResolver`（required/type/default/未知输入保留/幂等覆盖写回）；挂接 `ExpandedSheetGenerator.generate` 顶部；`ReportParamErrors` 三错误码；core 8 用例全绿（含 xpt.xml 模板端到端 `${param.pname}`/`${param.count*2}`）。
- Phase 2：`NopReportFormBizModel`（getXptModel 授权先行、querySpace checkDataSourceAccess 先于 JDBC、静态/字典/SQL 三候选、unknown 报 `nop.err.report.param.unknown`）；bean 手写注册于 `report-auth.beans.xml`；service 7 用例全绿（schema、三来源、报表 deny 双 action、数据源 deny、unknown）。
- 契约三处对齐：design `parameter-model.md`（active + 两条裁定）、owner doc `nop-report.md` 参数模型段 + AMIS 映射表 + 能力面移出缺失清单、实现行为一致。
- roadmap WI2 已勾选；`ai-dev/logs/2026/10-22.md` WI2 条目与事实相符。

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（新会话 subagent，ZCode / GLM-5.3-Flash，2026-10-22）
- Evidence（逐项核验，live repo）:
  1. Phase 1 PASS：`workbook.xdef` L194-204 `<params>`（`type="!std-data-type"`）；`_gen/_XptParamModel.java` 与 `_gen/_XptWorkbookModel.java` 字段与 xdef 一致（无手改痕迹，git log 中 _gen 最后变更仅历史拼写修正，本次变更为未提交工作区改动由生成器产生）；`ReportParamResolver.resolveAndInject` 幂等覆盖式 `setLocalValue`，未知输入 putIfAbsent 保留；挂接点 `ExpandedSheetGenerator.java` L74（展开前）；`ReportParamErrors` 三错误码（missing-required/unknown/type-error，英文消息）。core 测试 8 用例实跑 `./mvnw test -pl nop-report/nop-report-core -Dtest=TestReportParamResolver` → Tests run: 8, Failures: 0, Errors: 0。
  2. Phase 2 PASS：`NopReportFormBizModel` 两个 action 均先 `reportEngine.getXptModel(reportPath)`（WI1 报表级授权先行）；`getReportParamOptions` 中 `checkDataSourceAccess`（L84-88）先于 `jdbcTemplate.findAll`（L212）；bean 手写注册于 `report-auth.beans.xml`（非生成物）。service 测试 7 用例实跑 `-Dtest=TestReportFormBizModel` → Tests run: 7, Failures: 0, Errors: 0（含报表级 deny 双 action、querySpace 数据源 deny、unknown）。
  3. 契约一致性 PASS（审计修正后）：design `parameter-model.md` active + 两条不实现裁定已落；owner doc `docs-for-ai/03-modules/nop-report.md` 参数模型段 + controlType→AMIS 映射表 + L42 能力面；与实现行为无矛盾。
  4. runbook PASS：`docs-for-ai/03-runbooks/generate-report.md` L173-200 参数段——错误码（missing-required/type-error/unknown）、接口名（`NopReportForm__getReportFormSchema`/`getReportParamOptions`）、传参方式（`scope.setLocalValue(null,"param",…)`）均与实现一致。
  5. roadmap/logs/link-check PASS：roadmap WI2 已勾选；`ai-dev/logs/2026/10-22.md` WI2 条目在；`node ai-dev/tools/check-doc-links.mjs --strict` 实跑两次（修正前后）均退出码 0。
  6. Anti-Hollow PASS：声明（xdef）→ 解析校验（resolver）→ 注入 scope（`param` 变量）→ 模板取值（`param-model.xpt.xml` 单元格 `${param.pname}`/`${param.count*2}`，测试断言「报表参数值」「6」）链路真实连通；resolver 幂等（覆盖式写回）；deny 在 JDBC 前拒绝（代码顺序 + `testSqlOptionsDeniedForUnauthorizedQuerySpace` 用 named querySpace 无真实数据源验证）。
  7. 文本一致性 PASS：两 Phase Status: completed 与事实相符；Exit Criteria 全部勾选且逐条可证；Closure Gates 本轮核验后全部满足；logs 与实现一致。
- Minor（已当场修复）：design doc 裁定回写在实现者处被遗漏（plan Exit Criteria 勾选与事实不符）；审计以 reconcile-on-touch 原则直接补齐，未改产品代码。
