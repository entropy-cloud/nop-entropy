# 查询/质量/导入正确性批量修复（AR-01 + AR-06~AR-10）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Draft Review: R1 `ses_02c112e3fffex1hWQptd64Wpdf`（0 Blocker / 3 Major：AR-09 负 limit 语义矛盾（normalizeQueryLimit 静默钳制 vs 目标显式拒绝）→ 已加 Decision 项（新错误码 ERR_PAGINATION_LIMIT_INVALID 或等价裁定 + queryTableData 差异处理 + long 运算）；P0 位置缺测试归属 → 已指定 `TestNopMetaJoinBizModel.testSameDbSqlTableJoinReturnsRealRows`；SqlPagination 调用方绑定契约与修复方向冲突 + `MetaJoinExecutor:291-297` 同构正确路径误改风险 → 已加"不得改动"声明与 javadoc 注记项。3 Major 已全修。5 Minor：AR-10 测试改为模板字符串断言（H2-only 基建 + `%x-%v` 非日期）、AR-06 无数据即 FAIL 裁定 + 失败消息可诊断、AR-07 javadoc 过期同步 + details.schema 断言核对、Phase 3 事务边界裁定项（REQUIRES_NEW/外层事务交互）、行号漂移修正——已并入）。R2 `ses_02c05c812ffewLThdbTPk9RrSu`（可执行，0 Blocker / 0 Major；5 Minor 已并入：M-1 week 表达式补午夜截断（`DATE_FORMAT(DATE_SUB(...),'%Y-%m-%d 00:00:00')`，对齐 H2/PG DATE_TRUNC 语义）、M-2 溢出 red 用例需 seed ≥1 行（空 rows 不抛异常）、M-3 拒绝点固定在 BizModel 入口（否则新错误码在 3/5 SQL 路径永不触发）+ 缺省有界不提供"无界"选项、M-4 MySQL offset-only `dialect=null` 注记入 Non-Blocking Follow-ups、M-5 新测试按行键断言防隐式行序——已并入；checkstyle 门禁改 "checkstyle N/A" 惯例 + 两个强制工具门禁已入 Closure Gates）。consensus 达成。
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-01/AR-06/AR-07/AR-08/AR-09/AR-10）
> Related: 执行顺序 `{3}` of 3 — `{1}`（SSRF host 归一化）、`{2}`（custom_sql 沙箱）无依赖关系；本计划含 P0（AR-01），按自身 Phase 顺序执行
> Mission: nop-metadata-audit-remediation

## Purpose

批量收口 2026-08-05-2157 审计中查询/质量/导入域的正确性缺陷：P0 项 JOIN 分页参数双重绑定（必炸）、查询 API limit 缺归一化（裸异常/无界）、SLA 无数据静默绿灯（虚假关闭）、检查点路径 metaSchema 默认值漂移、MySQL 粒度模板语义错误、导入路径事务/索引边界不一致。每项修复均带判别性回归测试。

## Current Baseline

2026-08-06 live repo 核对：

- **AR-01（P0，confirmed）**：`MetaJoinExecutor.executeSameDbTableJoin`（`.../query/MetaJoinExecutor.java`）先 `SqlPagination.appendLimitOffset(sql, limit, offset, null)` 拼 `LIMIT ? / OFFSET ?` 占位符（:358），调用方又把 limit/offset `params.add(...)` 进参数列表（:359-364），而 `executeJdbcQuery`（:669-679）内部又独立绑定 limit/offset——占位符数 < 绑定数，`limit != null` 或 `offset > 0` 时**必然**抛 SQLException（代码注释 :352-357 自述"占位符参数由 executeJdbcQuery 绑定"，params.add 是与自身注释矛盾的遗留 bug）。同形态另两处：`ExternalExternalJoinAggregationProcessor.java:116-124`、`MixedSameDbJoinAggregationProcessor.java:152-160`（`AggregationHelper.executeJdbcQuery:84-97` 内部同样自绑）。正确先例：`MetaJoinExecutor.java:455`（fetchTableRows）与 `ExternalAggregationProcessor.java:83-85` 不预加参数。**陷阱**：`MetaJoinExecutor:291-297`（entity-entity 路径）与缺陷代码视觉同构（appendLimitOffset + params.add）但**正确**（经 `orm().executeQuery(sql, params.toArray())` 单次绑定）——修复时**不得改动**。7 处 join 测试调用点全部传 `limit=null, offset=null`（`TestAggregationExternalJoinAndPagination.java:98,171,280,...`，包 `io.nop.metadata.service`），双绑错误零覆盖；`docs-for-ai/03-modules/nop-metadata.md` 的 `queryAggregation(limit: 100)` 即触发。
- **AR-09（confirmed）**：`NopMetaTableBizModel.queryJoinData`（:255-272）与 `queryAggregation`（:275-297）把原始 limit 直传（未走 `normalizeQueryLimit`，仅 `queryTableData` :239/:368-376 有）；`limit=-5` → `subList(from, from+(-5))` 裸 `IllegalArgumentException`；`limit=Integer.MAX_VALUE` 且 offset>0 → int 溢出为负 → 裸异常；`limit=null` → 同库 JOIN SELECT 无 LIMIT → 全量结果集。`AggregationHelper.truncateCrossDb`（:818-837）与 `CrossDbJoinMerger.truncate`（:230-249）为缺陷位置。**注意**：`normalizeQueryLimit`（:371-377）对 `limit<=0` 是**静默钳制到默认值**，与"负 limit 显式拒绝"目标语义不同——本计划的归一化语义需显式裁定（见 Phase 1 Decision 项）。`ERR_PAGINATION_LIMIT_TOO_LARGE` / `ERR_PAGINATION_OFFSET_TOO_LARGE` 存在于 `JoinErrors.java:95-98`（均为 _TOO_LARGE 语义，无负数适配）。`TestMetaJoinTruncateOverflow` 只测 limit > Integer.MAX_VALUE。
- **AR-06（confirmed，虚假关闭）**：`MetaContractChecker.evaluateSla`（`.../contract/MetaContractChecker.java:235-284`）——catalogAvailable=false 时 collectionStale/dataStale 均保持 false → `slaFresh = !false && !false = true` → 归并 PASS（静默绿灯）。arm-index 登记 MA7.6-05 "fixed（R3.14）+ closure audit PASS"，但 git 核对唯一修复 commit 9b769490e 对该文件的实际 diff 仅删 7 行版权头；`:404-407` 存在不可达死分支（evaluateSla 两分支恒写入 collectionStale/dataStale key）。承诺的 slaFresh=false 语义与死代码删除从未落地。
- **AR-07（confirmed）**：`MetaQualityCheckpointExecutor.executeSingleRule`（`.../quality/MetaQualityCheckpointExecutor.java:288-293`）把 schemaPattern 原样透传（cron 路径恒 null），无 `resolveDefaultSchema` 回退（对照 `NopMetaQualityRuleBizModel.java:148,247,304-309`——单规则路径回退 `table.getMetaSchema()`）。同一规则在"手动单规则执行"与"检查点/cron"入口评估不同的物理表。**连带影响**：修复后检查点路径将首次写出 `details.schema` 条目（judge 内 :134-135 仅非空时记入）——既有检查点测试若断言 details 需同步调整；`NopMetaQualityCheckpointBizModel.executeCheckpoint` javadoc（:174 一带）"null/空串表示依赖连接默认 schema"表述将过期，需同步修正。
- **AR-08（confirmed，代码顺序核对）**：`NopMetaModuleBizModel.importOrmModel`（:186-208）`flushSession()` → `addToIndex` ×N（R6.5 后 fail-closed 可抛）→ `publishEvent`；批量路径 `importOrmModels` catch（:364-380）`result.setSuccess(false)` + `orm().clearSession()`——clearSession 只清会话缓存，flush 已送出 SQL 在外部事务提交时照常落库：DB 行已提交但报 failed、索引部分写入、事件缺失，三态不一致；单路径失败则 DB 回滚但已写 Lucene 文档成幽灵（无对账清扫）；模块/实体级联删除不调 removeFromIndex（`NopMetaModuleBizModel.delete` 无索引清理；`NopMetaEntityBizModel.java:35-40` 只删 MetaEntity 不删 MetaEntityField）→ 搜索返回已删实体（注：`NopMetaTableBizModel.delete:129` 已对其自身文档 removeFromIndex——修复以 live 代码为准，不重复改正确路径）。
- **AR-10（confirmed，模板字符串核对）**：`GranularityBucketing`（`.../query/GranularityBucketing.java:42-47`）MySQL `quarter` 模板为 `DATE_FORMAT(%s,'%Y-%m-01 00:00:00')`（月首，非季度首）、`week` 模板与 day 逐字节相同（`%Y-%m-%d`）；H2/PG 用 DATE_TRUNC 正确（方言路由：仅 `"MySQL".equalsIgnoreCase(productName)` 走 MYSQL_TEMPLATES，调用点 `AggregationHelper:555/631/680`、`EntityAggregationProcessor:191`）。MySQL 数据源上 quarter 拆 3 桶、week 退化为天，聚合结果静默错误。**测试基建限制**：本模块测试为 H2-only（`@NopTestConfig(localDb=true)`），无法对 MySQL 模板做语义执行断言——按 `TestSqlPaginationOffsetOnly`（SqlPagination.java:119）先例做**模板字符串断言**。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → 923 tests / 0 failures（R6.6 收口口径；执行时以当前为准）。

## Goals

- JOIN/聚合分页契约修复：带 limit/offset 的 `queryJoinData` / `queryAggregation` 不再抛 SQLException，分页结果正确（P0）
- 查询 API limit 归一化：非法 limit（负数/超大导致溢出）显式拒绝且错误可诊断（显式错误码，不抛裸 IllegalArgumentException、不静默钳制——语义经 Decision 裁定并与 queryTableData 的差异显式处理）、缺省 limit 有界语义裁定并文档化
- SLA 检查：SLA 已配置但无 Catalog 数据 → 不再静默 PASS；死分支删除；arm-index 虚假关闭记录纠正
- 检查点路径与单规则路径的 schema 解析语义一致（回退 metaSchema）
- MySQL quarter/week 粒度分桶正确（与 H2/PG 语义一致）
- 导入路径三态一致：失败时 DB/索引/事件不分裂；级联删除清索引
- 每项修复带判别性回归测试（修复前 red / 修复后 green）

## Non-Goals

- 不重构 JOIN 执行器架构（仅修分页参数绑定与归一化）
- 不重写 MetaContractChecker 的 SLA 算法（仅修无 Catalog 分支语义 + 死码）
- 不引入 outbox/事件总线基础设施（AR-08 以事务模板 + 失败反向清理的既有模式落地；如执行中证明必须 outbox 则记录为 successor plan 的 scope，不在本计划扩展）
- 不处理 P2 批（AR-11~23 已登记 backlog）
- 不改 ORM 模型 / 公共 API 签名

## Scope

### In Scope

- 三处 JOIN 分页双绑修复（MetaJoinExecutor / ExternalExternalJoinAggregationProcessor / MixedSameDbJoinAggregationProcessor）（Fix）
- queryJoinData / queryAggregation limit 归一化 + truncate long 运算 + 负 limit 显式错误码（Fix）
- MetaContractChecker 无 Catalog + SLA 已配置 → slaFresh=false + 死分支删除 + 回归测试（Fix）
- arm-index MA7.6-05 虚假关闭纠正 + R3.x 声称 fixed 的 P2 项 diff 存在性抽查（Proof/Fix）
- MetaQualityCheckpointExecutor schema 默认值回退（与 resolveDefaultSchema 语义一致）+ 多 schema 检查点回归（Fix）
- GranularityBucketing MySQL quarter/week 模板修正 + 模板级单元测试（Fix）
- 导入路径事务边界/索引一致性 + 级联删除索引清理 + 三态一致性测试（Fix）
- `docs-for-ai/03-modules/nop-metadata.md` 分页契约说明（如与 live 不符）+ `ai-dev/logs/` 更新（Follow-up）

### Out Of Scope

- AR-11~23 P2 项（已登记 backlog）
- 其它 SSRF/沙箱面（`{1}`/`{2}` plan 承接）
- 检查点调度器 cpId 错误码（AR-12 P2）
- 血缘/搜索/对账域修复

## Execution Plan

### Phase 1 - JOIN/聚合分页参数修复（AR-01 P0 + AR-09）

Status: completed
Targets: `.../query/MetaJoinExecutor.java` + `.../query/ExternalExternalJoinAggregationProcessor.java` + `.../query/MixedSameDbJoinAggregationProcessor.java` + `.../query/AggregationHelper.java` + `.../query/CrossDbJoinMerger.java` + `.../entity/NopMetaTableBizModel.java` + `TestAggregationExternalJoinAndPagination.java` + `TestMetaJoinTruncateOverflow.java`

- Item Types: `Fix | Proof`

- [x] **先写失败用例（Proof，red）**：`TestAggregationExternalJoinAndPagination`（包 `io.nop.metadata.service`）补 `limit=2` / `offset=1` 调用点断言（覆盖 external↔external 与 mixed 两条聚合路径，**按行键/分组名断言结果而非位置，避免隐式行序依赖**）；**P0 位置（同库 table-table 路径）在 `TestNopMetaJoinBizModel.testSameDbSqlTableJoinReturnsRealRows`（:165 一带，经 `INopMetaTableBiz.queryJoinData` 真实入口）补 `limit=2, offset=1` 断言（既有 :185 断言 items.size()==2，limit=2/offset=1 后应为 1——需同步调整该行断言）**——三处缺陷各有一个测试归属，不遗漏；`TestMetaJoinTruncateOverflow` 补 `limit=-5`、`limit=Integer.MAX_VALUE, offset>0` 用例——**溢出用例必须 seed ≥1 行**（空 rows 时 `min(0, 0+MAX)` 为 0，subList(0,0) 不抛异常，red 不会触发），确认当前全部失败（red，SQLException / IllegalArgumentException）
- [x] **双绑修复（Fix）**：`MetaJoinExecutor.executeSameDbTableJoin`（:359-364）、`ExternalExternalJoinAggregationProcessor`（:116-124）、`MixedSameDbJoinAggregationProcessor`（:152-160）删除调用方的 `params.add(limit/offset)`，占位符参数统一由 executeJdbcQuery 绑定（对照先例 `MetaJoinExecutor:455` / `ExternalAggregationProcessor:83-85`）。**`MetaJoinExecutor:291-297`（entity-entity 路径）不得改动**（其 params.add 是唯一绑定，删除将破坏正确路径）
- [x] **SqlPagination 契约注记（Fix）**：`SqlPagination.appendLimitOffset` javadoc（:20-29/:46-53 一带，自述"占位 ? 仍由调用方按原有条件绑定"）与实际两种绑定模式（调用方绑定 / executeJdbcQuery 内部绑定）不符——更新 javadoc 或在本计划三处调用点加注说明，消除文档-代码漂移（修复后三处不再跟随"调用方绑定"约定）
- [x] **limit 归一化裁定（Decision + Fix）**：裁定并落地——(a) 负 limit / 非法 limit 的处理语义：**显式拒绝 + 错误码**（既有 `ERR_PAGINATION_LIMIT_TOO_LARGE` 不适配负数，新增 `ERR_PAGINATION_LIMIT_INVALID` 或在既有 Errors 类裁定等价码）；(b) `queryTableData` 既有静默钳制语义（`normalizeQueryLimit`）与 queryJoinData/queryAggregation 新语义的差异处理：统一为新语义或文档化差异，裁定写入执行记录，不得让两入口行为分裂而不加说明；(c) **拒绝点固定在 BizModel 入口（`queryJoinData` / `queryAggregation` 参数归一化处）**——三条 SQL 路径（table-table / external↔external / mixed）的 LIMIT 占位符直接把负值绑给 DB（`appendLimitOffset` 对 `limit=-5` 按 hasLimit=true 生成 `LIMIT ?`，DB 层报错被包装为 ERR_JOIN_TABLE_EXEC_FAILED / ERR_AGGR_EXEC_FAILED），若只在 truncate 层拒绝则新错误码在 3/5 路径永远不触发、且行为不统一；truncate 层 long 运算与负值拒绝作为 defense-in-depth；(d) `limit=0` 语义与 `limit=null` 缺省上限：**沿用 queryTableData 默认值（有界）**——本计划 Goal 明确"缺省有界"，不提供"无界"选项；裁定写入执行记录
- [x] **判别性复核（Proof，green）**：Phase 1 新用例全绿；`TestAggregationExternalJoinAndPagination` / `TestMetaJoinTruncateOverflow` / `TestNopMetaJoinBizModel` 全量通过；既有 join 测试（limit=null 路径）不回归

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 带 limit/offset 的 queryJoinData/queryAggregation 在三条 JOIN 路径（同库 table-table / external↔external / mixed）均成功返回且分页正确（red→green 有记录）
- [x] 负 limit / int 溢出不再抛裸 IllegalArgumentException；非法 limit 走显式错误码（裁定 (a) 落地）
- [x] **端到端验证**：经 BizModel 入口（GraphQL 语义层）调用 queryJoinData(limit=2, offset=1) 得到预期行集——从 API 参数到结果集完整路径
- [x] limit 语义裁定（负值/零值/null 缺省/queryTableData 差异）已记录并落地
- [x] SqlPagination javadoc 与实际绑定模式一致（无新文档漂移）
- [x] `No owner-doc update required`（分页契约修复至文档既有声明；如模块文档分页说明与 live 不符则同步，以执行核对为准）——执行核对：模块文档未描述 queryJoinData/queryAggregation limit 语义 → 已同步新增「查询分页契约」段（docs-for-ai/03-modules/nop-metadata.md）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 质量引擎契约修复（AR-06 + AR-07 + AR-10）

Status: completed
Targets: `.../contract/MetaContractChecker.java` + `.../quality/MetaQualityCheckpointExecutor.java` + `.../query/GranularityBucketing.java` + `ai-dev/audits/arm-index-nop-metadata.md` + 对应测试类

- Item Types: `Fix | Proof`

- [x] **AR-06 失败用例（Proof，red）**：MetaContractChecker 现有测试（或新增）补"SLA 已配置 + catalogAvailable=false → 结果 FAIL（slaFresh=false）"断言，确认当前 red（静默 PASS）
- [x] **AR-06 修复（Fix + Decision）**：`evaluateSla` 在 `catalogAvailable=false` 时置 `slaFresh=false`（**契约语义裁定：无 Catalog 数据即不满足任何已配置 SLA——无论 slaMap 内容，简化为无数据即 FAIL；仅当 slaMap 为空（无 SLA 配置）才保持既有 pass 语义**，该裁定避免"仅含 retention 等其它键的 map 静默 pass"的残余面）；删除 `:404-407` 不可达死分支；**失败消息裁决**：死分支删除后无 Catalog 路径的失败消息需保留可诊断信息（避免 `SLA not satisfied ()` 空括号——补"no catalog data"类原因文本，具体实现执行时裁定）；回归测试 green
- [x] **AR-06 治理纠正（Proof + Fix）**：arm-index MA7.6-05 登记状态纠正为"R3.14 虚假关闭 → 本次实际修复"（注明 commit 9b769490e diff 仅版权头的事实）；对 roadmap 中 R3.x 声称 fixed 的 P2 项做 diff 存在性抽查（git log + diff 逐项核对，抽查范围执行时以 arm-index 清单为准，至少覆盖全部"fixed"标记项；R3.x 修复主体集中在 commit 9b769490e，实质为单 commit diff 核验），结果记录——存在同样虚假关闭的项列入本 Phase 修复或显式登记 backlog（不得静默放过）
- [x] **AR-07 失败用例（Proof，red）**：多 schema 场景检查点执行用例（table.metaSchema 非空、schemaPattern 为空 → 检查点应回退 metaSchema），确认当前 red
- [x] **AR-07 修复（Fix）**：`MetaQualityCheckpointExecutor.executeSingleRule` 复用与单规则路径一致的 `resolveDefaultSchema` 语义（回退 `table.getMetaSchema()`；实现方式：下沉共享或检查点路径补齐，执行时裁定并保持两路径语义一致）；**同步修正 `NopMetaQualityCheckpointBizModel.executeCheckpoint` javadoc（:174 一带）"null/空串表示依赖连接默认 schema"过期表述 + executor 相关注释**；**核对既有检查点测试对 `details.schema` 的断言**（修复后检查点路径将首次写出 schema 条目，需要时同步测试断言）；回归测试 green
- [x] **AR-10 失败用例（Proof，red，模板字符串断言）**：新测试类（或既有测试扩展，沿 `TestSqlPaginationOffsetOnly` 字符串断言先例）——断言 MySQL quarter 模板**字符串**产出季度首日语义（`DATE_FORMAT(%s,'%Y-%m-01')` 当前为月首 → red），MySQL week 模板字符串与 day 相同（`%Y-%m-%d` → red，要求 ISO 周语义）；H2/PG 模板断言不回归（测试基建 H2-only，无法执行 MySQL 语义，故为模板级字符串断言 + 表达式正确性核对）
- [x] **AR-10 修复（Fix）**：MySQL quarter 模板改为季度首日表达式（如 `CONCAT(YEAR(%s),'-',LPAD((QUARTER(%s)*3-2),2,'0'),'-01')` 或等价 MAKEDATE 形式，执行时以可验证的字符串断言为准）；week 模板改为 ISO 周语义——**周一 00:00:00 为桶键**（如 `DATE_FORMAT(DATE_SUB(%s, INTERVAL WEEKDAY(%s) DAY), '%Y-%m-%d 00:00:00')`；`%x-%v` 产出 "2026-33" 周数字符串而非日期，不得直接作为 bucket 键；**必须含午夜截断**——H2/PG 的 `DATE_TRUNC('week')` 产出周一 00:00:00，若 MySQL 模板保留原时间分量，同周不同时刻的两行在 MySQL 分两桶、H2/PG 一桶，仍属静默错桶）；模板字符串测试 green（断言含午夜截断），H2/PG 路径不回归
- [x] 文档同步：`docs-for-ai/03-modules/nop-metadata.md` 如描述 SLA/粒度语义与 live 不符则修正（Fix）——执行核对：模块文档无 SLA 评估/粒度模板语义描述（grep 实证仅实体表格行）→ `No owner-doc update required`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] SLA 无 Catalog 不再静默 PASS（无 SLA 配置时保持 pass）；死分支已删除；失败消息含可诊断原因；回归测试判别性有效
- [x] arm-index MA7.6-05 记录纠正；R3.x claimed-fixed 抽查完成、无未处置的虚假关闭（发现的全部登记/修复）
- [x] 检查点路径与单规则路径 schema 解析语义一致（同一规则两入口评估同一物理表）；executeCheckpoint javadoc 已同步；details.schema 相关既有断言已核对（grep 实证无既有断言冲突）
- [x] MySQL quarter/week 模板字符串断言 green（修复前 red 记录），分桶语义与 H2/PG 对齐
- [x] **接线验证**：CheckpointExecutor → judge → 表解析的运行时调用链在修复后生效（测试走真实入口）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 导入事务/索引一致性（AR-08）

Status: completed
Targets: `.../entity/NopMetaModuleBizModel.java` + `.../entity/NopMetaEntityBizModel.java` + `.../entity/NopMetaTableBizModel.java` + 导入/删除相关测试类 + `docs-for-ai/03-modules/nop-metadata.md`（失败路径语义）

- Item Types: `Fix | Proof`

- [x] **失败用例（Proof，red）**：导入路径三态一致性测试——构造 `addToIndex`（或事件发布）失败场景，断言当前三态分裂（DB 已提交 + 结果 failed 或索引幽灵），确认 red（执行时按模块既有测试基建选择注入点：如 `TestNopMetadataSearchIntegration:47-61` 先例直接构造 `NopMetaSearchProcessor` 注入 searchEngine）
- [x] **事务边界裁定（Decision）**：per-path 独立事务与既有外层事务/REQUIRES_NEW 的交互裁定（模块先例：`NopMetaDataSourceBizModel:512-513` 用 REQUIRES_NEW、`TableReferenceExecutor:74-75` 用 SUPPORTS）——内层事务边界、索引写入时机（提交后写入 vs 失败反向 removeDocs）、事件发布顺序三者组合裁定写入执行记录；若裁定需 outbox 级方案则记录为 successor plan，不阻塞本计划
- [x] **事务边界修复（Fix）**：`importOrmModel` / `importOrmModels` 按裁定改为 per-path 独立事务（`ITransactionTemplate.runInTransaction` 或等价机制，按模块既有事务模式）；索引写入移到提交成功后或失败时反向清理（removeDocs 对账），事件发布在持久化成功后；批量 catch 不再以 `clearSession` 制造"报失败但已落库"
- [x] **级联删除索引清理（Fix）**：`NopMetaModuleBizModel.delete` / `NopMetaEntityBizModel.delete` 删除前收集子实体 id（entity fields / tables 等），删除后 removeFromIndex；`NopMetaTableBizModel` 相关删除路径核对（其 :129 已自清索引，保持不动）
- [x] **判别性复核（Proof，green）**：三态一致性测试 green（失败时 DB/索引/事件一致回滚或一致提交）；删除后搜索不再命中已删实体；既有导入测试不回归
- [x] 文档同步：模块文档失败路径语义更新（per-path 事务、索引清理行为）（Fix）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 导入失败时 DB 行/索引/事件三态一致（测试断言）
- [x] 级联删除后索引无残留（搜索不返回已删实体，测试断言）
- [x] **端到端验证**：导入（成功 + 失败两路径）→ 搜索命中的完整链路测试通过
- [x] **无静默跳过**：失败路径返回明确错误且不留部分状态；不存在"报失败但数据已提交"的静默分裂
- [x] 模块文档失败路径语义已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-01（P0，JOIN 分页双绑）已修复并全路径验证
- [x] AR-06（SLA 虚假关闭）已修复 + arm-index 纠正 + R3.x claimed-fixed 抽查完成
- [x] AR-07（检查点 metaSchema 漂移）已修复
- [x] AR-08（导入事务/索引一致性）已修复
- [x] AR-09（limit 归一化）已修复
- [x] AR-10（MySQL 粒度模板）已修复
- [x] 全部回归测试落地且判别性有效（red 先于修复）；无 in-scope live defect 被降级
- [x] 受影响 owner docs 已同步或明确 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）各修复点的运行时调用链连通（BizModel → 执行器 → 结果），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"——根 pom 的 checkstyle 插件仅存在于 `-Pqa` profile）

## Deferred But Adjudicated

（无——本计划六项 finding 均为 confirmed live defect / contract drift，全部 in-scope 修复。）

## Non-Blocking Follow-ups

- AR-11~AR-23（2026-08-05-2157 审计 P2 批）已登记 `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md` `## Follow-up Backlog`，不属本计划范围
- AR-08 若执行中裁定需 outbox/事件总线级方案（超出事务模板+反向清理），以 successor plan 形式提出，不阻塞本计划 closure（除非该裁定改变三态一致目标本身）
- 执行记录注记（R2 审查发现，non-blocking）：`MetaJoinExecutor.executeSameDbTableJoin` 的 `appendLimitOffset(sql, limit, offset, null)` 固定传 `dialect=null`——MySQL 数据源上 `limit=null, offset>0` 的 offset-only 形态仍会生成 MySQL 非法 SQL（H2-only 测试不可见）。本计划修复双绑后此面保持原状，作为执行记录注记 + 后续 backlog 候选（如需修复需方言判定注入，超出本计划双绑修复范围）

## Closure

Status Note: 六项 finding（AR-01 P0 / AR-06 / AR-07 / AR-08 / AR-09 / AR-10）全部 in-scope 修复并落地判别性回归测试（red 先于修复，修复前 focused 实测 red：P0 双绑 SQLException ×3、截断层裸 IllegalArgumentException ×4、SLA 静默 PASS ×2、检查点 schema 传透 ×2、MySQL 模板 ×3、导入三态分裂 ×2）；独立 closure audit（fresh session）READY_TO_CLOSE；全量 `./mvnw test -pl nop-metadata -am -T 1C` 970/0 全绿；工具门禁全 0。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session）
- Audit Session: `ses_02b95ac6affekrnpZrVP2KjuBh`
- Evidence:
  - **Phase 1 Exit Criteria 全 PASS**：三条 JOIN 路径带 limit/offset 分页正确（MetaJoinExecutor.executeSameDbTableJoin:356-368 / ExternalExternalJoinAggregationProcessor:116-121 / MixedSameDbJoinAggregationProcessor:152-157 无 params.add，executeJdbcQuery 统一绑定；entity-entity 正确路径 :290-297 未动——params.add 仍在）；负 limit → ERR_PAGINATION_LIMIT_INVALID（JoinErrors.java:101-103 + NopMetaTableBizModel.normalizeJoinQueryLimit:402-411 入口拒绝 + truncateCrossDb:833-841 / CrossDbJoinMerger.truncate:245-252 long 运算 defense-in-depth）；端到端 GraphQL 测试 testSameDbSqlTableJoinPaginationViaGraphQL:330-366（limit=2/offset=1 → 1 行）；SqlPagination javadoc 双绑定模式 :19-38/:55-67 无漂移；docs「查询分页契约」nop-metadata.md:139-144
  - **Phase 2 Exit Criteria 全 PASS**：MetaContractChecker.evaluateSla:288 slaFresh=catalogAvailable && !stale（slaMap 空早退 pass :242-250）；死分支删除；失败消息 "no Catalog data" :416-423；arm-index「MR7 R7.3 收口记录」:8-12（MA7.6-05 R3.14 虚假关闭纠正 + 18 文件 diff 抽查 17 REAL_DIFF / 1 COPYRIGHT_ONLY）+ roadmap R3.14 行标注；MetaQualityCheckpointExecutor.executeSingleRule:293 resolveDefaultSchema 回退 + javadoc 同步 :174-175 + details.schema 既有断言 grep 零冲突；TestMetaQualityCheckpointExecutorSchemaResolution 3/3（ArgumentCaptor 证明 judge 收到 metaSchema）；GranularityBucketing:45/:50 MySQL quarter/week 模板 + :90 tpl.replace 文本替换（顺带修复旧模板 String.format 运行时必炸的 %Y 格式符问题）；TestGranularityBucketingTemplates 4/4；接线验证 CheckpointExecutor→tableRefExecutor→judge 调用链真实连通
  - **Phase 3 Exit Criteria 全 PASS**：importOrmModel:252-279 REQUIRES_NEW 事务单元（save+flush+indexImportedDocs:267+publishEvent:273-277）；indexImportedDocs:284-315 失败反向 removeDocs 清理 + 重抛；importOrmModels catch:484-492 注释修正（clearSession 不再承担回滚）；级联删除索引清理（Module.delete:145-168 collectModuleIndexedIds:172-211 / Entity.delete:41-52 收集字段 id + 清理；Table.delete:129 保持不动）；TestNopMetaModuleImportConsistency 4/4（修复前 git checkout 旧代码实测 2/2 red）；docs「导入失败路径语义」nop-metadata.md:146-152
  - **Closure Gates 全 PASS**：check-plan-checklist --strict exit 0（本 plan 0 未勾选）+ Closure Evidence 已写入；scan-hollow-implementations --severity high exit 0（0 findings）；`./mvnw compile -pl nop-metadata -am` exit 0；`./mvnw test -pl nop-metadata -am -T 1C` **970 tests / 0 failures / 0 errors / 0 skipped**（基线 923 + 47 新增：Phase1 +7 / Phase2 +10 / Phase3 +4，余为 R7.1/R7.2 新增）；checkstyle N/A（历史惯例，插件仅 -Pqa profile）；check-doc-links --strict exit 0（0 errors，12 warnings 均为其他历史 plan 文件，非本 plan 引入）
  - **Anti-Hollow 检查（审计逐链追踪）**：normalizeJoinQueryLimit 在 queryJoinData:267/queryAggregation:293 真实调用 → executeJoin → 各执行器 → executeJdbcQuery（入口到结果完整链路）；indexImportedDocs 在 REQUIRES_NEW lambda 体内（与 save/flush/publishEvent 同一事务单元）；truncate 层在活路径（crossDbMerge:93 / CrossDbInMemoryAggregationProcessor:96）；新测试走真实入口（GraphQL 引擎 / BizModel 直调 / executeRpc / executor.execute + action 实参捕获），无反射-only 覆盖；无空方法体/静默跳过——负 limit 显式抛、indexImportedDocs 清理后重抛、safeRemoveFromIndex LOG.warn（best-effort 明示理由）、批量路径 per-path success=false + 内层回滚
  - **Deferred 分类检查**：Deferred But Adjudicated =（无）；Non-Blocking Follow-ups 仅 P2 backlog（out of scope）+ outbox 条件 + offset-only/dialect=null 注记（BizModel 入口 null→1000 归一化后不可达）；无 in-scope live defect 被降级
  - **Minor 观察（审计记录，非阻塞）**：① TestMetaJoinTruncateOverflow 反射私有 truncateCrossDb（主要拒绝点已有真实入口测试覆盖，CrossDbJoinMerger.truncate 主路径同包直测）；② 工作树未提交（closure 后按 git 工作流提交）；③ check-doc-links 12 条 BROKEN_LINK 为仓库既有（其他 plan 文件），非本 plan 引入

Follow-up:

- no remaining plan-owned work（关闭时确认）；Non-Blocking Follow-ups 三项维持（P2 backlog AR-11~23 / AR-08 outbox 条件 / offset-only dialect=null 注记）
