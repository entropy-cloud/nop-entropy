# 2255 直接 SQL 收敛：nop-ai 聚合查询 EQL 化 + datav 会话后写实体化

> Plan Status: completed
> Last Reviewed: 2026-08-23
> Source: `ai-dev/analysis/2026-08/2026-08-23-direct-sql-usage-survey.md`（F-1/F-2 两组发现）
> Related: 无
> Draft Review: 独立子 agent 对抗性审查 1 轮（agent_7a983c4b，2026-08-23）——事实断言全部核实为真；发现 Blocker×1（camelCase 别名与 BeanRowMapper 大小写敏感查找冲突，改 snake_case 别名）+ Major×2（ORM DDL mandatory 列 NOT NULL 与 null-modelId 种子冲突→allNullable 建表；thumbnail 红验证需共享 ORM 会话内执行）+ Minor×3（afterEntityChange 空 hook 修正表述/dashboard jdbcTemplate 保留声明/dirty-only 等价性注记），已全部吸收进各 Phase。

## Purpose

把调研确认的 2 组共 6 处"不需要直接 SQL"的应用层代码收敛到平台规范路径（EQL / 实体写），消除 1 处生产级路径错配缺陷（F-1）与 5 处会话后写衍生缺陷（F-2），并把"直接 SQL 使用边界"沉淀为 owner doc 规则。

## Current Baseline

- `NopAiChatResponseBizModel.buildSummarySql`（`nop-ai/nop-ai-service/.../entity/NopAiChatResponseBizModel.java`）拼物理表名 SQL（`FROM nop_ai_chat_response r LEFT JOIN nop_ai_model m`），经 `orm().findAll(sql, ROW_MAPPER)` 执行。该路径按 EQL 编译，实体解析只认实体全名/短名，物理下划线名需 `allowUnderscoreName()`（缺省 false）→ 生产运行时抛 `ERR_EQL_UNKNOWN_ENTITY_NAME`，`summarizeByModel` 接口不可用。
- `TestNopAiChatResponseSummarizeByModel` 用 `IJdbcTemplate`（原生 SQL 路径）执行同一 `buildSummarySql`，掩盖了路径错配；两路径语义不同（原生 vs EQL 编译）。
- nop-ai-service 已有完整 ORM 会话工厂测试范式：`TestNopAiBizModelEntityCrud`（`OrmSessionFactoryBean` + H2 + `DdlSqlCreator` 从实体模型建表）。
- datav 两个 BizModel 存在 5 处 jdbcTemplate raw UPDATE（`updateDashboardPublishState`/`updateDashboardFields`/`updateScreenPublishState`/`updateScreenFields`/`setScreenThumbnail` 内联语句），写的是 ORM 会话中已加载实体对应的同一行：
  - `setScreenThumbnail` 手写 `VERSION=VERSION+1` + 审计列，其余 4 处不维护 version/审计列；
  - raw UPDATE 不更新会话实体 → `setScreenThumbnail` 返回值在**共享 ORM 会话**下是一级缓存旧实例（与注释"返回最新主表行"矛盾；当前 datav 测试基架无环境 session，`runInSession` 每次新开 session 时 `getEntityById` 会重读 DB，故既有测试未暴露）；publish/rollback 的 `afterEntityChange` 在共享会话下同样看到旧 publishState（注：`CrudBizModel.afterEntityChange` 基类实现当前为空 hook，两个 BizModel 未覆写——本修复修正的是未来覆写者的状态可见性，当前无行为差异）；
  - 物理列名硬编码（`NOP_DATAV_SCREEN.THUMBNAIL` 等）。
  - rollback 流程的 `restoreXxxFromSnapshot` 已把目标字段写进会话实体，随后却用 raw SQL 重复写库。
- datav 模块内已有 `updateEntityDirectly` 惯例（`NopDatavReportTaskBizModel`/`NopDatavAlertRuleBizModel` 等 8+ 处），publish 流程同函数内已用 `saveEntityDirectly` 保存快照。
- 实体属性已核实：`NopAiChatResponse`（sessionId/modelId/aiProvider/aiModel/promptTokens/completionTokens/responseDurationMs）、`NopAiModel`（id/inputPricePer1m/outputPricePer1m）、`NopDatavScreen`（含 version 字段）。
- EQL 能力已背书：LEFT JOIN + ON、GROUP BY、SUM/COUNT、子查询、`?` 参数（`TestEqlQuery.java`）；别名原样成为结果集字段名（`EqlHelper.getFieldName`）。

## Goals

- `summarizeByModel` 经 `orm()` 路径可正常执行：`buildSummarySql` 改为实体名 EQL，聚合/定价 join/graceful degradation 语义与现测试断言逐项等价。
- `TestNopAiChatResponseSummarizeByModel` 改为经真实 EQL 路径（ORM 会话工厂）执行验证，不再用 jdbcTemplate 执行被测语句。
- datav 5 处 raw UPDATE 改为实体写（`updateEntityDirectly`），会话实体与库一致，version/审计列由 ORM 维护。
- `docs-for-ai/02-core-guides/model-first-development.md` 增补"直接 SQL 使用边界"规则（何时允许 raw SQL / 何时必须 EQL / 实体写）。

## Non-Goals

- 不改造调研第二节的"保留"清单（mfa/credential store 家族、share visit 统计、existsTable 探测、PanelDataBinder 查询引擎、框架基础设施）——它们有明确的单语句原子/会话绕过/产品语义裁定。
- 不引入 `allowUnderscoreName()` 方案保留物理表名写法（实体名 EQL 是规范路径）。
- 不改动 datav `recordShareVisit`（设计文档裁定的原子统计自增）。
- 不做 EQL update SET 算术表达式的新语法探索（mfa 家族平移评估属 watch-only，见 analysis Open Questions）。

## Scope

### In Scope

- `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiChatResponseBizModel.java`
- `nop-ai/nop-ai-service/src/test/java/io/nop/ai/service/entity/TestNopAiChatResponseSummarizeByModel.java`
- `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardBizModel.java`
- `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavScreenBizModel.java`
- datav screen/dashboard 相关测试的聚焦补强（thumbnail 返回值一致性断言）
- `docs-for-ai/02-core-guides/model-first-development.md`、`ai-dev/logs/2026/08-23.md`

### Out Of Scope

- 其余所有 raw SQL 使用点（理由见 analysis 第二节与 Non-Goals）。
- nop-ai / nop-datav 的模型（orm.xml）变更。

## Execution Plan

### Phase 1 - F-1：summarizeByModel 改 EQL + 真路径测试

Status: completed
Targets: `nop-ai/nop-ai-service`（main + test）

- Item Types: `Fix`（生产级路径错配）| `Proof`（红→绿验证）

- [x] 红验证：先在测试中经 `orm().findAll(buildSummarySql(...), ROW_MAPPER)`（ORM 会话工厂范式，参照 `TestNopAiBizModelEntityCrud`）执行现有物理表名 SQL，确认抛 `ERR_EQL_UNKNOWN_ENTITY_NAME`（证明缺陷真实存在，记录失败形态）
- [x] `buildSummarySql` 重写为实体名 EQL：`from NopAiChatResponse r left join NopAiModel m on r.modelId = m.id`，投影/聚合/定价算术/GROUP BY 语义不变。**全部 8 个投影（含 3 个分组键）都必须带 snake_case 别名**（`r.modelId as model_id`、`sum(...) as total_prompt_tokens` 等，即保留现有 SQL 的别名文本）——`ROW_MAPPER = BeanRowMapper.of(ModelUsageSummary.class, true)` 的 `StringHelper.camelCase(key,'_',false)` 会先整体小写，camelCase 别名/无别名投影将因大小写敏感的属性查找抛 `ERR_BEAN_UNKNOWN_PROP`（子 agent 审查 P1 结论，`ROW_MAPPER` 不动）。`where r.sessionId = ?` 用 `.param0(sessionId)` 传参；更新方法 javadoc（EQL 语义、graceful degradation 两情形不变）
- [x] `TestNopAiChatResponseSummarizeByModel` 改造：**建表改用 ORM 工厂 DDL `createTables(tables, true, false)`（allNullable=true）并删除手写 DDL 常量**——实体模型的 mandatory 列（model_id/version/审计列等）若按缺省 DDL 生成 NOT NULL，现有种子（尤其 `model_id` 故意为 null 的 r6 行）将无法插入（子 agent 审查 P2 结论）；种子数据与全部既有断言（分组数、m-x 定价计算 0.00105、m-y null 定价 → null、null model_id 独立分组、session 隔离、空 session、blank sessionId 抛错）保留，被测语句一律经 `orm().findAll` 执行；DDL/种子 insert 仍可用 jdbcTemplate（测试脚手架）

执行记录（2026-08-23）：
- 红验证失败形态：`NopException errorCode=nop.err.eql.unknown-entity-name, entityName=nop_ai_chat_response`（经 `orm().findAll` EQL 编译路径执行物理表名 SQL，1 个用例即触发）。
- **执行中发现（超出审查预见）**：EQL 编译器算术优先级非标准——`a*b/1000000 + c*d/1000000` 被编译为 `((a*b)/(1000000+c*d))/1000000`（`/` 未比 `+` 结合更紧，经编译 SQL dump 实证），estimatedCost 初版绿验证失败（实际值 9e-10 量级）。修复：定价算术显式括号 `((p*ip)/1000000) + ((c*op)/1000000)`，编译 SQL 复核正确。该发现已回填 analysis 报告，并将在 Phase 3 写入 owner doc（EQL 算术必须显式括号）。
- 种子值偏差：手写 DDL 时代码的 `openai/anthropic` provider 值不满足实体模型 `provider precision=4`（dict 码列）——工厂 DDL（模型真实精度）下改为 `open/anth/unk`，断言 key 同步。原语义（按 provider+model 分组）不变。
- [x] 绿验证：`./mvnw test -pl nop-ai/nop-ai-service` 通过（37 tests 全绿，含改造后 7/7 EQL 真路径用例）；EQL 路径与既有断言逐项等价（`-am` 形式因上游 nop-ioc `TestAop.testDynamicGen` 分支既有 reactor 失败而豁免，见 Closure Gates 注记）

Exit Criteria:

- [x] `buildSummarySql` 文本中不再出现物理表名 `nop_ai_chat_response`/`nop_ai_model`，实体以短名引用，全部 8 个投影带 snake_case 别名，`ROW_MAPPER` 不变，执行路径 `orm().findAll` 不变
- [x] 测试中被测聚合语句经 ORM 会话（EQL 编译）执行——测试与生产同路径（Anti-Hollow：组件级 jdbcTemplate 执行不再冒充生产路径）
- [x] 红验证记录：改造前 EQL 路径抛 `ERR_EQL_UNKNOWN_ENTITY_NAME` 的失败形态已留档（见本 Phase 执行记录）
- [x] 既有 6 组断言语义全部保留并通过（聚合值/定价 join/null 传播/会话隔离/空集/参数校验）：`TestNopAiChatResponseSummarizeByModel` 7/7 绿；模块 37 tests 全绿
- [x] No owner-doc update required（本 Phase 不改平台约定；边界规则统一在 Phase 3 落档，含新发现的 EQL 算术括号规则）
- [x] `ai-dev/logs/2026/08-23.md` 已更新（Phase 3 统一收口时落档）

### Phase 2 - F-2：datav 5 处 raw UPDATE 改实体写

Status: completed
Targets: `nop-datav/nop-datav-service`（main + test）

- Item Types: `Fix`（会话后写三缺陷）| `Proof`（返回值一致性断言）

- [x] `NopDatavDashboardBizModel`：`publishDashboard` 改为在已加载实体上 set publish 字段 + `updateEntityDirectly(dashboard)`；`rollbackDashboard` 在 `restoreDashboardFromSnapshot` 后直接 `updateEntityDirectly(dashboard)`；删除 `updateDashboardPublishState`/`updateDashboardFields` 两个 raw SQL helper。**保留该类的 `jdbcTemplate` 注入**（`getDashboardData` 经 `new PanelDataBinder(daoProvider(), jdbcTemplate)` 仍依赖它，子 agent 审查 P5 结论）
- [x] `NopDatavScreenBizModel`：同法处理 `publishScreen`/`rollbackScreen`；`setScreenThumbnail` 改为 `screen.setThumbnail(...)` + `updateEntityDirectly(screen)` 并直接返回该实体（兑现"返回最新主表行"承诺）；删除 `updateScreenPublishState`/`updateScreenFields`；`jdbcTemplate` 字段仅这 3 处使用，一并清理注入与 import
- [x] 等价性注记（供 closure audit 对照）：`updateEntityDirectly` 只写 dirty 列且对无 dirty 实体为 no-op——rollback 流程中快照缺 `screenWidth/Height/adaptorMode` 键时实体不 dirty、对应列不重写，与旧 raw SQL"重写原值"净效果等价（子 agent 审查 P6 结论）
- [x] 聚焦测试补强：thumbnail 断言在**调用方开启的共享 ORM 会话内**执行 biz 方法（`orm().runInSession(s -> {...})`，模拟生产请求级 session）——此写法下旧实现返回一级缓存旧实例（红）、新实现返回已改实体（绿）；无环境 session 的直调用例下旧实现也会从 DB 重读而绿，不能作为缺陷证据（子 agent 审查 P3 结论）。断言：返回实体携带新 thumbnail 与递增后的 version；publish/rollback 回归断言实体 publishStatus/publishedVersion 与库一致
- [x] 回归：`./mvnw test -pl nop-datav/nop-datav-service` 全绿（630 tests，覆盖 TestNopDatavDashboardBizModel / TestNopDatavScreenBizModel / LayoutRoundtripE2E / SharedDashboardAccess / AuditLog 等既有用例；`-am` 形式因上游 nop-ioc 既有 reactor 失败豁免，见 Closure Gates 注记）

执行记录（2026-08-23）：
- 红验证失败形态（git stash 旧实现跑新断言）：`expected: <file-record-shared-456> but was: <null>`——共享会话下旧实现返回一级缓存旧实例，F-2 缺陷精确复现；恢复新实现后同一断言绿。
- **行为修正的测试适配（3 个用例）**：publish 现在实体写 bump version（旧 raw SQL 数据变更不 bump version，乐观锁账目失真），`testRollbackScreenRestoresFromHistoricalSnapshot` / `testRollbackDashboardRestoresFromHistoricalSnapshot` / `testPublishAndRollbackPreservesParamConfig` 持跨会话脱管实体（旧 version）再 update 时乐观锁失败（`update-entity-not-found`）——测试改为 publish 后重读实体再修改（带注释说明）。生产路径每请求重读实体，无此模式。
- 回归结果：`nop-datav-service` 全套 630 tests 0 failures 0 errors；`grep NOP_DATAV_DASHBOARD|NOP_DATAV_SCREEN` 主代码无残留（退出码 1）。

Exit Criteria:

- [x] 两文件内不再有针对 `NOP_DATAV_DASHBOARD`/`NOP_DATAV_SCREEN` 的 jdbcTemplate UPDATE；5 处调用点全部走 `updateEntityDirectly`
- [x] 行为变更已核实并记录：发布/回滚现在维护 version 与 updatedBy/updateTime（旧 raw SQL 不维护）；乐观锁冲突显式报错（旧为静默覆盖）——均属修正方向，测试佐证无回归
- [x] thumbnail 聚焦断言（共享 ORM 会话内执行）：返回实体 thumbnail/version 为新值（改造前红、改造后绿，失败形态记录）
- [x] datav 既有测试套全绿；`NopDatavDashboardBizModel` 的 `jdbcTemplate` 注入保留（PanelDataBinder 依赖）
- [x] No owner-doc update required（模块内行为对齐既有 `updateEntityDirectly` 惯例，无新约定；边界规则在 Phase 3）
- [x] `ai-dev/logs/2026/08-23.md` 已更新

### Phase 3 - 文档落档与收尾

Status: completed
Targets: `docs-for-ai/02-core-guides/model-first-development.md`、`ai-dev/`

- Item Types: `Decision`（使用边界规则）| `Follow-up`

- [x] `model-first-development.md` 增补章节"直接 SQL 的使用边界"：三条判定规则（raw SQL 仅限框架基建/单语句原子契约/会话绕过语义；普通实体读写必须实体写或 EQL；经 orm() 执行的文本即 EQL，物理表名需 allowUnderscoreName 否则报错）+ 指向保留清单（受 docs-for-ai→ai-dev 边界规则约束，以文字指引而非链接）
- [x] 核对 `docs-for-ai/INDEX.md` / `04-reference/source-anchors.md`：INDEX 新增"判断业务代码能否直接写 SQL"路由行；source-anchors 新增 `PERSIST-001`（直接 SQL 边界规范锚点：SysDaoResourceLockManager EQL 样例 + DbMfaChallengeStore raw 豁免样例 + JdbcQueryExecutor allowUnderscoreName）
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（初版 owner doc 引用 ai-dev 路径触发 BOUNDARY 错误 1 处，已改为文字指引后复跑通过）
- [x] 运行 `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2255-direct-sql-reduction.md --strict` 退出码 0（closure 时执行：文本整固后复跑 Passed: 1 / Failed: 0）
- [x] 运行 `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-service --severity high` 与 `--module nop-datav/nop-datav-service` 退出码 0
- [x] `ai-dev/logs/2026/08-23.md` 收口条目（关键事实/验证命令/commit 待补于提交后）

Exit Criteria:

- [x] owner doc 新章节与 live 代码行为一致（规则可对照 analysis 第二/三节复核）
- [x] 链接检查、空壳扫描全部退出码 0（checklist 检查于 closure 时执行）
- [x] 日志含验证命令与结果、doc-sync 裁定
- [x] INDEX/source-anchors 已裁定（均更新：路由行 + PERSIST-001）

## Closure Gates

> **nop-ioc `-am` 豁免裁定（closure audit 核实）**：`./mvnw test -pl <module> -am` 在上游 `nop-core-framework/nop-ioc` 的 `TestAop.testDynamicGen`（AOP 代码生成 golden 测试）失败——干净 HEAD（stash 本 plan 全部改动后重跑）同样失败、`-pl nop-ioc` 单模块运行 3/3 绿、依赖方向为 ai/datav → nop-ioc（本 plan 改动不可能影响其结果）。裁定为分支既有 reactor/环境敏感失败，非本 plan 引入；两模块验证以不带 `-am` 的全量测试（37/630 全绿）为准。

- [x] F-1 缺陷修复：`summarizeByModel` 经 `orm()` EQL 路径可执行且有测试证明（改造前红/后绿）
- [x] F-2 缺陷修复：5 处 raw UPDATE 全部实体写化，会话一致性断言落地
- [x] 必要 focused verification 已完成（两模块 `./mvnw test -pl ...` 全绿 37/630；`-am` 形式按上述豁免裁定记录）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（第二节保留项均有 analysis 裁定，不属本 plan scope；deferred 两项经 closure audit 复核分类诚实）
- [x] owner doc 已同步（新章节），INDEX/source-anchors 已裁定
- [x] 独立子 agent closure-audit 已完成且 evidence 写入本 plan（见 Closure 段）
- [x] **Anti-Hollow Check**：EQL 改造经生产同路径（orm()）测试验证；datav 改造经既有 E2E 套验证；无空方法体/静默跳过（closure audit 实读两 main 文件 + hollow 扫描双模块 0 findings）
- [x] `./mvnw test -pl nop-ai/nop-ai-service` 通过（37 tests；`-am` 按豁免裁定）
- [x] `./mvnw test -pl nop-datav/nop-datav-service` 通过（630 tests；`-am` 按豁免裁定）
- [x] checkstyle/代码规范（imports 分组等）通过（编译零警告增量；两模块 BUILD SUCCESS）

## Deferred But Adjudicated

### mfa/credential 家族平移 EQL 评估

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该家族有单语句原子 + REQUIRES_NEW + 会话绕过的成文裁定（见 analysis 第二节），现状正确；平移收益仅为去物理列名耦合，且依赖 EQL update SET 算术表达式的无背书语法。
- Successor Required: `no`

### EQL update SET 自引用算术表达式支持背书

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属框架能力测试补充，不阻塞任何当前缺陷。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 若未来需要重估 mfa 家族平移，先为 EQL update SET 算术表达式补框架级测试。

## Closure

Status Note: F-1/F-2 两组共 6 处"不需要直接 SQL"的代码已全部收敛到平台规范路径（实体名 EQL / 实体写），红→绿证据齐全；必须保留 SQL 的场景已在 analysis 逐点写明原因并沉淀 owner doc 边界规则。三个 Phase 全部 completed，deferred 两项分类经独立审计复核诚实，无 in-scope live defect 遗留。
Completed: 2026-08-23

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（agent_0de3d19f，fresh session，2026-08-23）
- Audit Session: agent_0de3d19f-cbc3-4339-9186-637343dc504b
- Evidence:
  - Phase 1 Exit Criteria 逐条 PASS：`buildSummarySql` 无物理表名/实体短名 EQL/8 投影 snake_case 别名/ROW_MAPPER 未改（`NopAiChatResponseBizModel.java:83-97`）；测试经 `orm().runInSession + orm.findAll` 执行被测语句（`TestNopAiChatResponseSummarizeByModel.java:249-253`）；实跑 7/7 绿 BUILD SUCCESS
  - Phase 2 Exit Criteria 逐条 PASS：`grep NOP_DATAV_DASHBOARD|NOP_DATAV_SCREEN` 主代码退出码 1；5 调用点 `updateEntityDirectly`（Screen:132/169/298、Dashboard:252/289）；共享会话断言落地（`TestNopDatavScreenBizModel.java:763-788`）；实跑 Screen 25/25、Dashboard 12/12 绿
  - Phase 3 Exit Criteria 逐条 PASS：owner doc 边界章节与 `JdbcQueryExecutor` 行为一致；INDEX:74 路由行、source-anchors:189 PERSIST-001
  - Closure Gates 验证结果：全部 PASS（`-am` 三处按豁免裁定记录）；`check-doc-links.mjs --strict` exit 0（0 errors）；`scan-hollow-implementations.mjs` nop-ai/nop-datav 双模块 exit 0（0 findings）；nop-ioc `TestAop` 单模块 3/3 绿（豁免可信度抽查，依赖方向论证成立）
  - Anti-Hollow 检查：生产同路径测试 + 既有 E2E 套 + 双 main 文件实读无空方法体/静默跳过
  - Deferred 项分类检查：两项均诚实（watch-only residual / out-of-scope improvement），无 in-scope live defect 被降级
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2255-direct-sql-reduction.md --strict` 退出码 0（文本整固后复跑留档，见日志）
  - 审计发现的文本不一致（Phase 2 Exit Criteria 未勾选、-am 表述矛盾、豁免未落档）已在关闭前整固完毕

Follow-up:

- 见 Deferred But Adjudicated 与 Non-Blocking Follow-ups（EQL update SET 算术表达式框架级测试）；无 confirmed live defect 遗留
