# R8.3 导入与内存过滤正确性组修复（AR-18, AR-19）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Mission: nop-metadata-audit-remediation
> Work Item: MR8（R8.3 导入与内存过滤正确性组）
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-18、AR-19）、`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR8 段 R8.3 行 + R8.0 裁决记录）
> Related: 执行顺序 `{3}` of 3（与 R8.1/R8.2 无共享代码文件——三份 plan 的 Phase 4 均更新 arm-index/roadmap 与条件性触及 `NopMetadataErrors.java`，顺序执行下无冲突）；启动门禁：R8.0 done。

## Purpose

修复导入与内存过滤链路的 2 个已确认正确性缺陷（AR-18 手拼 JSON 不转义 + buildSql 未类型化、AR-19 内存过滤与 SQL 路径语义漂移，R8.0 全部提级为 P1 修复）：导入产物不再静默产生非法 JSON、buildSql 解析显式失败而非裸 ClassCastException、跨库内存过滤与同库 SQL 路径结果一致。产出 = 代码修复 + 判别性回归测试 + docs 复核 + arm-index/roadmap 终态。

## Current Baseline

经 2026-08-06 live repo 核对（finding 描述以审计报告为准；行号以 live 复核为准）：

- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` **970/0**（R8.0 收口口径）
- **AR-18**（`nop-metadata-dao/.../OrmModelImporter.java:208-216/:218-227` + `MetaTableFieldResolver.java:340-359`）：
  - `buildJoinConditionsJson` 手拼字符串 JSON（left/right 值含 `"`/`\` 时产出非法 JSON 静默入库）；`buildIndexColumnsJson` 同型（:218-227）
  - `MetaTableFieldResolver.java:346-348` `Object parsed = JsonTool.parse(buildSql)` 后 `(List<Map<String, Object>>) columnList` 未类型化——元素非 Map 时 `:367 col.get(...)` 裸 ClassCastException，违反显式 ErrorCode 契约；**既有错误码 `ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID` 已在 :343/:350/:370 使用**（复用，不新增）；追加"元素下标/原因"参数需改 ErrorCode 消息模板（小型变更，可降级为不加参数）
- **AR-19**（`MemoryFilterEvaluator.java`，对照 `FilterToSqlTranslator` 语义）：
  - `:101-110` LIKE：`%`→`.*`、`_`→`.` 后直接 `matches`——字面量元字符（`.`/`(`/`+`）未转义，匹配面扩大；**实现约束**：`Pattern.quote(literal)` 后整体 replace 会把 `%`/`_` 的替换包进 `\Q...\E` 变成字面量（通配失效）——正确顺序是**先转义正则元字符（保留 `%`/`_` 不做转义），再替换 `%`→`.*`、`_`→`.`**，或逐字符构造
  - `:223-246` `compareValues`：null 与任何值比较返回 -1/1 → `HAVING x < 100` 聚合为 NULL 时内存路径保留行、SQL 路径排除（语义相反）；**波及面**：`IN`（:110-122）与 `BETWEEN` 也经 `compareValues` 路径——IN 列表含 null / BETWEEN 边界 null 需与 SQL 对齐（**基准 = `FilterToSqlTranslator` 行为**：rowVal=null → 比较 false；单边 null 边界 → 改写为单边比较（如 `col <= max`），边界 null 本身不产生 false——实现须逐条对照 translator 钉死）；**NOT 包装**（:83-88 `evalNot`）同族漂移风险：`NOT (x < 100)` 遇 x=NULL → SQL UNKNOWN 排除、内存 not(false)=true 保留，需一并核对
  - `:171-174` `evalAny` 空子节点 → false，SQL 路径空 or 节点 → TRUE（无过滤，语义相反）
  - `TestMemoryFilterAndOrderBy` / `TestFilterToSqlTranslator` / `TestCrossDbInMemoryAggregationProcessor` 既有测试存在；`FilterToSqlTranslator.java` 为 SQL 路径语义基准（空 or → 无过滤、LIKE 直通、NULL 比较 UNKNOWN→排除）
- 模块文档 `docs-for-ai/03-modules/nop-metadata.md`：「查询分页契约」段（AR-09 裁定）——**该段描述的是 limit/offset 分页语义**（三条 JOIN 路径与跨库内存合并路径的分页一致），与 filter/HAVING 求值语义无关；AR-19 修复不影响该段 → docs 复核后大概率 `No owner-doc update required`，不预设立场

## Goals

- AR-18：join/index JSON 构造改结构化序列化（如 `JsonTool.stringify`），任何输入值不再产出非法 JSON；buildSql 反序列化逐元素类型校验，非法元素 → 复用既有错误码 `ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID`（fail-fast，不再裸 ClassCastException；是否追加元素下标参数按需裁定）；判别性测试 red→green
- AR-19：内存过滤与 SQL 路径语义对齐——(a) LIKE 字面量元字符转义（**先转义正则元字符再替换 `%`/`_`**，通配语义保持），(b) 比较含 null 一律 false（仅 is-null/not-null 判空，与 SQL 一致；IN/BETWEEN 波及面一并核对），(c) 空 or 节点 → true（无过滤语义）；判别性测试 red→green + **通配符保持回归用例**（`%xx%` 仍匹配、`_` 仍单字符）
- docs-for-ai 查询分页契约相邻段/内存过滤语义表述与 live 行为一致（复核后明确无变更或同步真正受影响段）
- 每个修复带判别性回归测试（red 先于修复实测或至少行为断言可捕获回归）

## Non-Goals

- 不改 `FilterToSqlTranslator`（SQL 路径是语义基准，只改内存路径对齐它）
- 不重构 OrmModelImporter 的 JSON 存储结构（字段/格式不变，只改构造方式）
- 不处理 R8.1/R8.2/R8.4 组 finding
- 不做内存过滤引擎的性能优化（LIKE 前缀匹配优化等）
- 不改 buildSql JSON 的既有结构契约（columnName/dataType 等键不变）

## Scope

### In Scope

- `nop-metadata-dao/.../OrmModelImporter.java`（AR-18 JSON 构造 + 相关测试）
- `MetaTableFieldResolver.java`（AR-18 buildSql 类型化校验 + 相关测试）
- `MemoryFilterEvaluator.java`（AR-19 三处语义对齐 + IN/BETWEEN 波及 + 相关测试）
- `NopMetadataErrors.java`（若 AR-18b 裁定追加错误码参数模板——否则无变更）
- `docs-for-ai/03-modules/nop-metadata.md`（AR-19 复核——预期 No owner-doc update required，除非复核发现 filter 求值语义相关表述漂移）
- `ai-dev/audits/arm-index-nop-metadata.md` §P2 + roadmap MR8 段终态更新

### Out Of Scope

- R8.1 组（AR-11/12/13/14/15）、R8.2 组（AR-16/AR-23③⑤④）、R8.4 组（AR-20/21/22/17 + AR-23①②⑨⑩）
- 内存过滤性能优化（如 LIKE 前缀索引化、候选池剪枝）
- `FilterToSqlTranslator` 行为变更

## Execution Plan

### Phase 1 - AR-18 JSON 构造与 buildSql 类型化

Status: completed
Targets: `OrmModelImporter.java`（nop-metadata-dao）+ `MetaTableFieldResolver.java` + 相关测试

- Item Types: `Fix | Proof`

- [x] AR-18a：`buildJoinConditionsJson` / `buildIndexColumnsJson` 改结构化序列化（`JsonTool.stringify` 或等效），含特殊字符（`"`/`\`）的 left/right/index 字段名不再产出非法 JSON；持久化格式不变（同为 JSON 文本）
- [x] AR-18b：`MetaTableFieldResolver` buildSql 反序列化——逐元素 `instanceof Map` 校验（含 null 元素），非法元素 → 复用既有错误码 `ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID` + 元素下标/原因参数（如裁定追加则同步 ErrorCode 消息模板；否则不加参数，保持既有模板），不再裸 ClassCastException
- [x] 判别性测试：AR-18a —— join 条件/索引列名含 `"`/`\` → 导入后 JSON 可重新解析（`JsonTool.parse` 不抛）+ 值完整保留（修复前非法 JSON 实测 red）；AR-18b —— buildSql 为 `[123, {...}]` 混合类型 → 显式错误码（非 ClassCastException）；修复前 red 实测
- [x] 回归：`OrmModelImporter` 既有导入测试（含 TestNopMetaModuleImportConsistency）/ `MetaTableFieldResolver` 相关测试全绿

Exit Criteria:

- [x] 特殊字符输入下 JSON 合法可解析（判别性测试实证）；非法 buildSql 元素显式错误码（判别性测试实证）
- [x] **无静默跳过**：非法 JSON/类型不再静默入库；非法 buildSql 不再裸 CCE（fail-fast + 显式错误码）（Minimum Rules #24）
- [x] 持久化格式兼容（既有库内 JSON 数据可正常读取）
- [x] 若错误码模板变更：arm-index / 错误码清单同步
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 2 - AR-19 内存过滤与 SQL 路径语义对齐

Status: completed
Targets: `MemoryFilterEvaluator.java` + 相关测试 + `docs-for-ai/03-modules/nop-metadata.md`（复核）

- Item Types: `Fix | Proof`

- [x] AR-19a：LIKE 实现修正——**先转义正则元字符（`.`、`(`、`+` 等，`%`/`_` 保留不转义），再替换 `%`→`.*`、`_`→`.`**（或逐字符构造等效实现）；`LIKE 'a.b'` 不再匹配 `aXb`，同时 `%xx%`/`a_b` 通配语义保持
- [x] AR-19b：比较含 null 一律 false（`eq/ne/gt/ge/lt/le` 中任一侧 null → false），仅 is-null/not-null 判空；**IN/BETWEEN 波及面（逐条对照 `FilterToSqlTranslator` 行为钉死）**：IN 列表含 null 元素时与 SQL UNKNOWN 语义对齐（null 不参与匹配）、rowVal=null → false、BETWEEN 单边 null 边界 → 单边比较（不产生假 false）、BETWEEN rowVal=null → false；**NOT 包装复核**：`NOT (x < 100)` 遇 x=NULL → SQL UNKNOWN → 排除，内存路径 not(false)=true 会保留——与 SQL 三值逻辑对齐（:83-88 evalNot 现状核对）；`HAVING x < 100` 聚合 NULL 组内存路径与 SQL 路径一致（排除）
- [x] AR-19c：空 or 节点 → true（与 SQL 无过滤语义一致）；空 and 节点语义与 SQL 对照复核（保持一致）
- [x] 判别性测试：LIKE 字面量元字符（`a.b`/`a(b)` 不扩匹配）+ **通配保持回归**（`%xx%` 仍匹配中间、`a_b` 仍单字符）；NULL 比较（HAVING x<100 NULL 组排除 / IN 含 null / BETWEEN 单边 null 边界与 rowVal=null 逐项对照 translator / **NOT 包装下 NULL 三值语义**）；空 or 节点（结果集不缩水）四组，对照 `FilterToSqlTranslator` 语义；修复前 red 实测
- [x] docs 复核：核对 `docs-for-ai/03-modules/nop-metadata.md` 查询分页契约段（:139-144）与其它 filter/HAVING 求值相关表述——该段为 limit/offset 分页语义，AR-19 不影响 → 记录 `No owner-doc update required`；如复核发现 filter 求值语义表述漂移则同步
- [x] 回归：`TestMemoryFilterAndOrderBy` / `TestFilterToSqlTranslator` / `TestCrossDbInMemoryAggregationProcessor` / 跨库聚合 e2e 全绿

Exit Criteria:

- [x] 三处语义漂移实测收敛（判别性测试 red→green；与 FilterToSqlTranslator 行为对照一致）+ LIKE 通配保持回归通过
- [x] **端到端验证**：跨库内存聚合路径（外部↔外部 join + HAVING/LIKE）与同库 SQL 路径对同一数据产出一致结果集（Minimum Rules #22）
- [x] **无静默跳过**：语义对齐不引入静默降级（如 null 比较直接 false 是显式语义，非吞异常）（Minimum Rules #24）
- [x] docs 复核结论记录（预期 `No owner-doc update required`，如发现漂移则已同步）+ check-doc-links --strict exit 0
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 3 - 收口

Status: completed
Targets: roadmap MR8 段 + arm-index §P2 + 全量验证

- Item Types: `Fix | Proof`

- [x] roadmap MR8 段 R8.3 行 → done（注明 2 项 finding 终态 + 测试计数基线变化）
- [x] arm-index §P2 AR-18 / AR-19 → fixed（含修复 commit 引用）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（记录计数基线）
- [x] 独立子 agent closure audit（fresh session）PASS + Closure 段证据写入

Exit Criteria:

- [x] roadmap MR8 段与 arm-index §P2 双向一致（AR-18/19 逐条可追溯）
- [x] 全量测试通过（0 failures/errors/skipped）+ 工具验证 exit 0
- [x] 独立 closure audit READY_TO_CLOSE（含 Anti-Hollow 调用链追踪）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

## Closure Gates

> 关闭条件：本 section 所有条目与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] AR-18 + AR-19 两个已确认 live defect 全部修复（判别性测试 red→green 证据在案）
- [x] 无已确认 live defect / contract drift 被降级到 deferred / follow-up
- [x] docs-for-ai 内存过滤/查询语义表述复核完成（无漂移则显式记录 `No owner-doc update required`）
- [x] 必要 focused verification 已完成（每项 finding 至少一条判别性测试 + 通配保持回归）
- [x] 独立子 agent / 独立审阅者 closure-audit 完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）内存过滤路径在跨库聚合执行链上真实被调用（不只是类型存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] checkstyle / 代码规范检查通过（历史惯例：插件仅 -Pqa profile，按仓库惯例）

## Deferred But Adjudicated

（无 —— 本 plan 2 项 finding 全部 in-scope 修复，无归类项）

## Non-Blocking Follow-ups

- 内存过滤 LIKE 前缀匹配等性能优化（watch-only，无正确性缺陷，随跨库查询性能批次）
- AR-19 修复后如发现 FilterToSqlTranslator 侧残留语义差（本 plan 对齐后复核发现的新面），记录为 successor 观察项

## Closure

Status Note: 2026-08-06 全 3 Phase 完成 + 独立 closure audit READY_TO_CLOSE 后关闭。修复 commit `c8ccf3c44`（9 文件 +657/-57）。
Completed: 2026-08-06（Plan Status → completed；roadmap MR8 段 R8.3 → done（v30 header + 里程碑表 MR8 行）；arm-index MR8 R8.3 收口记录段（AR-18/AR-19 → fixed 含 commit 引用）+ §P2 两行 fixed 标注；日志收口条目）
Reviewer / Agent: 独立子 agent closure audit（fresh session `ses_02abed2b0ffeIJeMqehNmQgIJ9`，只读复验，未修改任何文件）——VERDICT PASS（READY_TO_CLOSE）

Closure Audit Evidence:

- 独立子 agent closure audit（fresh session `ses_02abed2b0ffeIJeMqehNmQgIJ9`，只读复验，未修改任何文件）：**VERDICT PASS（READY_TO_CLOSE）**
- 逐项 live code 证据：AR-18a PASS（OrmModelImporter.java:216-224/:227-237 JsonTool.stringify + 键名不变 + 普通值格式兼容；TestOrmModelImporterJsonEncoding 5/5）；AR-18b PASS（MetaTableFieldResolver.java:355-372 逐元素 instanceof Map + elementIndex :369 + cause 保留 + FieldErrors.java:18-21 模板同步 + NopMetadataArgs.java:57 ARG_ELEMENT_INDEX；TestMetaTableFieldResolverBuildSql 5/5，判别性：旧代码 CCE/NPE 会 fail）；AR-19 PASS（MemoryFilterEvaluator.java:63 行保留=恰 TRUE；:94-102 NOT 3VL；:138-153 IN null 语义；:163-173 BETWEEN；:190-226 evalAll/evalAny 空节点恒真 + 3VL 聚合；:326-345 toLikeRegex 元字符转义 %/_ 保留；与 FilterToSqlTranslator.java:155-158/:176-187/:238-256 逐点对照一致）；判别性测试 PASS（TestMemoryFilterAndOrderBy +9，旧实现逐项可证 fail——audit 独立复跑 35/35 green）；Anti-Hollow PASS（CrossDbInMemoryAggregationProcessor.java:85-87 构造 MemoryFilterEvaluator + filter 调用 + MetaAggregationExecutor.java:136/144 分派；e2e testCrossDbMemoryHavingMatchesSameDbSqlPath 经真实 queryAggregation RPC 双路径一致结果集断言——内存过滤若静默跳过则组 B 残留 size=2 测试必 fail；OrmModelImporter 经 NopMetaModuleBizModel.persistModelGraph:404-469 导入链真实调用）；无空方法体/静默跳过/no-op；无 live defect 降级（Deferred 段显式「无」）
- 工具验证（audit 独立复跑）：check-plan-checklist --strict exit 0（unchecked 20 项为 Phase 3/Closure Gates 勾选前的待办，收口后 0）+ check-doc-links --strict exit 0（No errors found）
- 非阻塞观察 2 条（audit 指出，已处置）：① 日志缺 R8.3 条目——本收口已写入 `ai-dev/logs/2026/08-06.md`；② 本地 ~/.m2 存在修复前 nop-metadata-dao 快照——closure 前已以 `-am` 全量重跑刷新（1016/0 全绿实证）

Follow-up:

- 内存过滤 LIKE 前缀匹配等性能优化（watch-only，Non-Goal 非缺陷，随跨库查询性能批次）
- AR-19 修复后若发现 FilterToSqlTranslator 侧残留语义差 → 记录为 successor 观察项（当前复核无）
