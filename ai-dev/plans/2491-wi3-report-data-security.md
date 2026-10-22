# 2491 WI3 数据级安全：行级过滤 + 列级脱敏（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI3；`ai-dev/design/nop-report/auth-wiring.md`（边界：行/列级是数据级安全，另立设计）
> Related: 2489-wi1-report-auth-wiring.md

## Purpose

按角色/部门/当前用户对报表取数注入行级过滤谓词（取数前 SQL 改写），对敏感字段在展示层做列级脱敏；规则声明式（VFS 配置），与 WI1 授权协同。

## Current Baseline

- WI1 已接线报表/数据源授权（能否渲染/能否取数）；数据级行/列控制缺失。
- 取数标签 `UseJdbcDataSet`/`UseOrmDataSet` 构造 `SQL` 对象后直接 findAll；`SQL.extend()` 返回 SqlBuilder，支持 `append`/`param(Object)`（`?` 占位 + 参数收集）。
- `ExpandedSheetEvaluator.evaluateCell` 的三条 `cellModel.getField() != null` 路径是单元格取字段值的单一汇聚点（setValue 前），可作展示层脱敏挂点；cell model 携带 `ds`/`field`。
- yaml 解析用 `io.nop.core.lang.json.yaml.YamlParser`（nop-core）；service 已有 AutoTest 基座与现成测试模板资源（report/auth/ 下 xpt.xlsx/xpt.xml）。
- ORM 模型改动属 protected area（plan-first）——本计划用 VFS 配置承载规则，不改 ORM 模型。

## Goals

- core SPI `IReportDataSecurityProvider`：`getRowCondition(reportPath, dsName)`（返回条件 SQL 片段（`?` 占位）+ 绑定参数，null=不过滤）、`maskValue(dsName, field, value)`（返回脱敏值或原值）。
- core 行过滤执行：`xptRt.applyRowCondition(sql, dsName)`——有条件时把原 SQL 包装为 `select * from ( 原SQL ) _rpt_sec where <条件>`（子查询包装，避免 where 解析脆弱性；条件 `?` 参数经 SqlBuilder.sqlWithParams 绑定；包装保留原 SQL 语句级属性，复用 `SQL.begin(sql)` 模式而非 extend() 裸构造）；在 `UseJdbcDataSet`/`UseOrmDataSet`/`JdbcDataSet`（流式标签同样接线，防绕过）三个标签的 sql 构造后调用；**dsName 传归一化键 `dsName || 'ds1'`**（与标签内数据集注册键一致，防默认数据集规则静默失效）；xlib 脚本中 sql 变量为 `const`，改为 `let` 或链式内联。
- core 展示层脱敏：`ExpandedSheetEvaluator` 的字段取值路径上经 provider 脱敏（对所有渲染器生效；脱敏发生在取值展示时而非数据集构造时）。**dsName=null 的匹配语义（保守侧）**：仅无 dsName 约束的字段级 mask 规则生效（`ds!field` 语法才有 ds；`=field`/层级展开路径 ds 为 null——按字段名匹配全部数据集，避免漏脱敏）。**聚合裁定**：被公式（SUM 等）引用的字段不得配置 mask——公式对掩码文本求值的行为未定义（Excel 端导出公式还会重算），不做静态检测、写入 design/owner doc 作为模板作者约束；聚合正确性测试只对未被公式引用的脱敏字段断言其余合计保持原始值。
- service `NopReportDataSecurityProvider`：声明式规则来自 VFS 配置 `/nop/report/data-security.yaml`（规则：reportPath 前缀/dsName 精确匹配 → rowCondition（条件模板 + 上下文占位 `{userId}/{tenantId}/{deptId}` 取自 IUserContext，值经 `?` 参数绑定防注入；条件列名按 design 约定可加 `_rpt_sec.` 限定）；mask 规则：dsName 可选 + field → phone/name/idCard/full 掩码类型）；规则文件缺失 = 无规则放行；**加载语义：按 resource lastModified 缓存**（首用加载，文件变更后下次检查自动重读）。
- owner doc / design（`auth-wiring.md` 边界段指向新的数据级安全设计或在 design/nop-report 增 `data-security.md`）/ roadmap / logs 收口。

## Non-Goals

- ORM/CRUD 层的数据权限（平台已有 `IDataAuthChecker` 体系）；`UseQueryDataSet` 的 query bean 过滤（裁定：走平台 ORM 数据权限或后续 WI 增强，本计划覆盖 SQL 标签路径并文档化）；PDF 侧（渲染器无关，脱敏在求值层）。
- 不新建 ORM 实体（规则走 VFS 配置，规避 protected area）。

## Scope

### In Scope

- `nop-report/nop-report-core`：SPI、行过滤包装、脱敏挂点、xpt-rt 标签接线、错误码（如条件为空串等配置错误的 `nop.err.report.data-sec.*`）、测试
- `nop-report/nop-report-service`：provider 实现 + VFS yaml 规则解析 + 测试
- `ai-dev/design/nop-report/data-security.md` 新建；owner doc、roadmap、logs

### Out Of Scope

- 规则管理 UI/实体；`UseQueryDataSet` 行过滤；公式引用被脱敏字段的检测（裁定为模板作者约束，见 Goals）

## Execution Plan

### Phase 1 - core SPI 与行过滤/脱敏挂点

Status: completed
Targets: `nop-report/nop-report-core`

- Item Types: `Feature`

- [x] `IReportDataSecurityProvider` SPI（bean `nopReportDataSecurityProvider`，`tryGetBean` 可选查找，未注册=不启用）+ `IRowCondition`（conditionSql + params）
- [x] `IXptRuntime.applyRowCondition(SQL, dsName)` default：provider 有条件时子查询包装 + `?` 参数绑定；无 provider/无条件原样返回
- [x] `xpt-rt.xlib` `UseJdbcDataSet`/`UseOrmDataSet`/`JdbcDataSet` source 脚本在 sql 构造后调用 `applyRowCondition`（JdbcDataSet 无 dsName attr，行条件键固定按默认 `ds1`/无 dsName 约束规则匹配，与 mask 的 null 语义对齐）
- [x] `ExpandedSheetEvaluator` 字段取值路径（三处 setValue 前）经 provider.maskValue(dsName, field, value)
- [x] core 测试：fake provider——行条件包装后的 SQL 文本与参数断言、无条件原样、脱敏命中/不命中、无 provider 放行

Exit Criteria:

- [x] 行条件包装的 core 单测覆盖 SQL 文本、参数绑定与语句级属性保留
- [x] `./mvnw test -pl nop-report/nop-report-core` 绿
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - service 规则实现与收口

Status: completed
Targets: `nop-report/nop-report-service`、design、owner doc

- Item Types: `Feature + Decision`

- [x] `NopReportDataSecurityProvider`：VFS yaml 规则加载（reportPath 前缀/dsName 精确匹配；rowCondition 模板占位 `{userId}/{tenantId}/{deptId}` 从 IUserContext 解析、值绑定为 `?` 参数；mask 类型 phone/name/idCard/full）+ beans 注册
- [x] service AutoTest 测试：行级过滤端到端（模板 UseJdbcDataSet 取数 → 包装 → 规则命中后只返回过滤行；无规则全量）；脱敏端到端（渲染 HTML 中手机号形如 `138****`；未被公式引用的脱敏字段之外，其余合计单元格数值 = 原始数据合计，断言对象为合计 cell 值）；无用户上下文 + 行规则引用 userId → fail-closed 报 `nop.err.report.data-sec.*`
- [x] `ai-dev/design/nop-report/data-security.md`（规则 schema、子查询包装语义、聚合不受脱敏影响说明、UseQueryDataSet 裁定）；owner doc 能力面/已知限制更新（聚合与脱敏的约束说明：公式引用字段禁止 mask；测试经 delta 目录覆盖验证变更重读）；roadmap WI3 勾选；logs 收口

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] 行过滤与脱敏端到端测试均绿；`node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 端到端：规则 → 取数前 SQL 改写 → 结果行被过滤（service e2e）；规则 → 展示层脱敏 → 渲染输出含掩码且聚合正确
- [x] 无静默跳过（配置错误 fail-closed；无规则/无 provider 明确放行语义）
- [x] owner doc + design 同步（含 UseQueryDataSet 裁定与聚合说明）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录

## Deferred But Adjudicated

### UseQueryDataSet 行过滤

- Classification: `watch-only residual`
- Why Not Blocking Closure: query bean 路径走平台 ORM 层，可复用既有 `IDataAuthChecker` 数据权限；roadmap WI3 的「SQL/query 重写」主体由 Jdbc/Orm 标签承载，裁定记录于 design/owner doc
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- 规则热更新/管理界面（规则管理归后续产品化）
- mask 类型扩展（银行卡等）

## Closure

Status Note:
WI3 已按计划完成并由独立 closure auditor 核验通过。行级过滤经子查询包装（含尾分号裁剪与语句级属性保留）接线三个取数标签（含流式 JdbcDataSet，dsName 归一化 `dsName||'ds1'`）；列级脱敏挂 ExpandedSheetEvaluator 三条 field 路径；service provider 走 VFS yaml + lastModified 缓存 + `?` 参数绑定 + fail-closed。Minor 残留（watch-only，不阻塞）：Phase 2 文本所述「合计单元格数值=原始数据合计」未以 SUM cell 形式断言，等价保障由 testDatasetUntouchedByMask（数据集原始值不被修改，2 行 + 原始 fldA）+ 存活行 fldB 断言承载（模板无公式聚合 cell，与「公式引用字段禁止 mask」约束一致）。

Completed:
2026-10-22

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（新会话 subagent，model: GLM-5.3-Flash / account:bigmodel-individual-coding-plan）
- Evidence:
  1. Phase 1 PASS：SPI `IReportDataSecurityProvider`（bean 名常量 + `tryGetBean` 可选查找）与 `RowCondition`（`nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/IReportDataSecurityProvider.java`）；`IXptRuntime.applyRowCondition` 子查询包装 + `SQL.begin` 属性保留 + `trimTrailingSemicolon`（`IXptRuntime.java` diff）；`xpt-rt.xlib` 三标签接线（UseJdbcDataSet 固定 `'ds1'`、UseOrmDataSet/UseJdbcDataSet2 `dsName||'ds1'`、`const`→`let`）；`ExpandedSheetEvaluator` 三处 field 路径 maskValue 挂点（diff 确认）。`./mvnw test -pl nop-report/nop-report-core -Dtest=TestReportDataSecurityHook`：Tests run 5, Failures 0, BUILD SUCCESS。
  2. Phase 2 PASS：`NopReportDataSecurityProvider`（yaml 解析/lastModified 双检缓存/`?` 绑定/fail-closed `nop.err.report.data-sec.no-user-context`/phone-name-idCard-full 掩码）；bean 注册于手写 `report-auth.beans.xml`（id=nopReportDataSecurityProvider，与 SPI BEAN_NAME 一致）；测试资源 `data-security.yaml` 与 `sec-rows.xpt.xlsx`（SQL 含尾分号，覆盖裁剪路径）齐备。`./mvnw test -pl nop-report/nop-report-service -Dtest=TestReportDataSecurity`：Tests run 6, Failures 0；并复核 `-pl ... -am`（上游 17 模块 + service）BUILD SUCCESS。
  3. Anti-Hollow PASS：调用链闭合——xlib 标签脚本 `xptRt.applyRowCondition` → `IReportDataSecurityProvider.get()`（BeanContainer.tryGetBean）→ beans.xml 同 id provider → SQL 包装 → 标签内 findAll（包装发生在取数前）；evaluator → `xptRt.maskValue` → provider.mask；fail-closed 在 `getRowCondition`（取数标签 findAll 之前）抛出。无空实现。
  4. 文档 PASS：`ai-dev/design/nop-report/data-security.md`（schema/包装语义/聚合约束/UseQueryDataSet 裁定）与实现一致；owner doc `docs-for-ai/03-modules/nop-report.md` 数据级安全段已更新；roadmap WI3 勾选（line 54）；`ai-dev/logs/2026/10-22.md` 有 WI3 条目。`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）。
  5. 契约一致 PASS：Goals 四项裁定（归一化键/null 保守语义/聚合约束/JdbcDataSet 接线）实现与 design、owner doc 三处一致。
  6. 文本一致 PASS：Phase 1/2 Status=completed 与 Exit Criteria 勾选与 live 代码/测试事实相符（唯一偏差为 Minor 聚合断言形态，见 Status Note）。

Issues: Minor×2 —— (a) 聚合断言形态偏差（watch-only，见 Status Note）；(b) 仓库根有计划外未跟踪文件 `_tmp_dump_ops.groovy`（与本计划无关，建议清理）。
Conclusion: 可标 completed。
