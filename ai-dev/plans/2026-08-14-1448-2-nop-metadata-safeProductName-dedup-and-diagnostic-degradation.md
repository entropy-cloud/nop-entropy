# nop-metadata safeProductName 去重与诊断退化清扫（F17 + AR-14）

> Plan Status: completed
> > Last Reviewed: 2026-08-14
> > Mission: nop-metadata-invariant-loop
> > Work Item: Cycle 2 / 再审计 follow-up backlog — safeProductName 去重族 + 死码/诊断退化批次
> > Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（F17 + AR-14）；审计源 `ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md`（F17）、`ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md`（AR-14）
> > Related: 前置 `2026-08-14-1448-1-...`（代码卫生，不触碰 safeProductName，可并行但本计划 N=2 排在其后）；`2026-08-14-1448-3-...`（ORM，独立）。F17（去重）与 AR-14（null→误归因）都触碰 `safeProductName`，合并为一份计划避免冲突。

## Purpose

把 `safeProductName` 7 份重复实现（含 1 死实例）收敛为单一规范入口（`AggregationHelper.safeProductName`），并在收敛的同时修复 AR-14 的"metadata 失败被误归因为方言不支持"反模式；顺带清扫 AR-14 批次的其余诊断退化/死码项（死 LOG、死参数、错误 param、脆弱日期格式）。收口后该 helper 族 DRY、失败语义诚实（infra 失败 fail-loud 而非伪装成 unsupported dialect）。

## Current Baseline

> 事实为 2026-08-14 live repo 实测。

- **F17 safeProductName 7× 重复**（同签名 `static String safeProductName(DatabaseMetaData)`，catch SQLException → log + return null）：
  - **死实例**：`NopMetaProfilingRuleBizModel.java:190-198`（无调用点）。
  - **活实例（5 个私有副本）**：`NopMetaQualityRuleBizModel.java:395`、`NopMetaDataSourceBizModel.java:456`、`NopMetaTableQueryAction.java:237`、`TableReferenceExecutor.java:138`、`SqlViewFieldTypeInferrer.java:204`。
  - **已 public 可复用规范入口**：`AggregationHelper.java:469-476`（`public static`，catch `SQLException` → `LOG.warn(ERR_AGGR_TYPE_PROBE_FAILED...)` → return null）。
  - 各副本 ErrorCode 散乱（`ERR_QUALITY_RULE_TYPE_PROBE_FAILED` / `ERR_AGGR_TYPE_PROBE_FAILED` 等），DRY 漂移风险。
- **AR-14a safeProductName null→误归因**（`AggregationHelper.java:469-476`）：`getDatabaseProductName()` 抛 `SQLException` 时 → log + **return null**。**每个调用方**把 null 当作"方言不支持"并抛 `ERR_AGGR_UNSUPPORTED_DIALECT`。一次 metadata/基础设施层失败（连接/驱动异常）被误标为"方言不支持"——logged-then-returns-wrong-result 反模式。与已修 AR-06（`probeNumeric` 把 infra 失败塌缩为 string stats）同族语义。
- **AR-14b 死 LOG**：`MetaAggregationExecutor.java:47` — `private static final Logger LOG` 声明但文件内零 `LOG.` 引用（死）。
- **AR-14c 死参数 + 无效工作**：`AggregationHelper.resolveEntityFieldColumn(..., Map<String,String> propToCol)`（:212-227）签名含 `propToCol`，方法体从不读取它（按 `entityFieldId` PK 重载 field）；调用方（`EntityAggregationProcessor:260/273`）构造并传入该 map 属浪费。
- **AR-14d 错误 param**：`SqlSelectFieldExtractor.java:114-115` — 异常 `.param("sql", ...)` 被赋为 Java 类名字符串（`"unhandled SELECT statement class: ..."`）而非 SQL 文本，与文件内其它错误点（`.param("sql", sql)`）不一致。
- **AR-14e 脆弱日期格式**：`MetaManifestBuilder.java:144-146` — `new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")` 的 `'Z'` 是字面量；当前仅因下一行 `setTimeZone(UTC)` 才"碰巧正确"；重构易破。

## Goals

- `safeProductName` 全仓仅 `AggregationHelper` 一处实现；5 个活副本删除、调用点改走规范入口；死实例删除。
- `AggregationHelper.safeProductName` 区分"metadata/infra 失败"（fail-loud：抛 metadata-infrastructure 错误并传播 cause）与"产品名确属未知"（返回值由调用方按 unsupported-dialect 处理）——infra 失败不再被误归因为方言问题。
- AR-14b-e 清扫：删死 LOG、删死参数 `propToCol`（含调用方清理）、修正 `SqlSelectFieldExtractor` 错误 param、用不可变 ISO 格式器替换脆弱 `SimpleDateFormat`。

## Non-Goals

- **重设计方言映射逻辑** —— 本计划只让失败语义诚实，不改"产品名 → 方言"映射表本身。
- **F14/F15/F16/F18/F19** —— 归 `2026-08-14-1448-1`。
- **F10-F13 ORM 索引 / dict** —— 归 `2026-08-14-1448-3`。
- **`MetaTableReferenceResolver.resolveDataSourceOrThrow`（同名不同类）** —— 不在本计划范围（它是另一个 helper，有调用）。

## Scope

### In Scope

- safeProductName 去重（F17）：删 5 活副本 + 1 死副本，调用点改走 `AggregationHelper.safeProductName`。
- safeProductName 失败语义修正（AR-14a）：infra 失败 fail-loud。
- AR-14b-e：死 LOG、死参数、错误 param、脆弱日期格式。

### Out Of Scope

- 方言映射表重设计。
- 其它 follow-up backlog 项（→ 计划 1 / 计划 3）。

## Execution Plan

### Phase 1 — safeProductName 失败语义修正（AR-14a）+ 规范入口定稿

Status: completed
Targets: `AggregationHelper.java:469-476`（规范入口）、相关 `NopMetadataErrors`（如需新增 infra-failure ErrorCode）

- Item Types: `Fix | Decision`

> **设计裁定（Decision）**：`safeProductName` 的 `SQLException` 来自 `DatabaseMetaData.getDatabaseProductName()`，本质是连接/驱动/基础设施失败（非"方言不支持"）。修正语义：
> - catch `SQLException` → 抛 `NopMetadataException`（复用或新增一个 metadata-infrastructure ErrorCode，如 `ERR_META_DB_PRODUCT_NAME_FAILED`，`.cause(e)` 传播），**不再 return null**。
> - 调用方原本"null → 抛 ERR_AGGR_UNSUPPORTED_DIALECT"路径**仅保留给**"产品名获取成功但方言映射表不含该名"的真·不支持场景。逐调用方核对：当前是否有调用方依赖 null 表示 unsupported？若是，改为：`safeProductName` 成功返回名后，方言解析仍 unknown 才抛 unsupported-dialect。
> - 与 AR-06（已修，`probeNumeric` infra 失败 → WARN + return false 区分类型不匹配）语义对齐：infra 失败 fail-loud，类型/方言不匹配走降级。

- [x] 裁定 ErrorCode：复用现有 metadata-infrastructure ErrorCode 或在 `NopMetadataErrors` 新增 `ERR_META_DB_PRODUCT_NAME_FAILED`（含 `.cause` 传播）
- [x] `AggregationHelper.safeProductName` 改为 catch `SQLException` → 抛 infra 错误（fail-loud，传播 cause），不再 return null
- [x] 新增 focused test：mock `DatabaseMetaData.getDatabaseProductName()` 抛 `SQLException` → 断言抛出 infra 错误（含 cause + ErrorCode），不再误归因为 unsupported-dialect
- [x] 新增 focused test：getDatabaseProductName 成功返回已知/未知名 → 不抛 infra 错误（未知名仍由调用方按 unsupported-dialect 处理）

Exit Criteria:

- [x] `AggregationHelper.safeProductName` 在 `SQLException` 时抛 infra 错误（code 可核），不 return null
- [x] focused test 覆盖 infra-失败路径（mock SQLException → 断言 infra 错误）+ 正常路径
- [x] **无静默跳过**：infra 失败显式抛错，不以 return null 静默伪装成 unsupported dialect
- [x] 若新增 ErrorCode：已登记到 `NopMetadataErrors` 且 owner-doc 如有 ErrorCode 表已同步（否则写 No owner-doc update required）
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — safeProductName 去重（F17）：删副本，调用点改走规范入口

Status: completed
Targets: 5 活副本（`NopMetaQualityRuleBizModel:395`、`NopMetaDataSourceBizModel:456`、`NopMetaTableQueryAction:237`、`TableReferenceExecutor:138`、`SqlViewFieldTypeInferrer:204`）+ 1 死副本（`NopMetaProfilingRuleBizModel:190`）；**另含 4 个已走规范入口的 null 消费调用方**（见下方 M2 说明）

- Item Types: `Fix`

> **去重后失败语义统一**：5 个活副本原本各自 catch + log + return null（各自 ErrorCode）。改走 `AggregationHelper.safeProductName` 后，infra 失败统一由 Phase 1 的 fail-loud 语义处理（抛 infra 错误）。逐调用方核对：原副本的调用点是否在 catch 内消费 null？若是，移除该 null 分支（infra 现在直接抛，不到达调用方的 null 处理）。保留调用方对"产品名未知 → unsupported-dialect"的处理（若存在）。
>
> **已走规范入口的 null 消费调用方（审查 M2，必须一并清扫）**：除 5 个私有副本外，**另有 4 个类已通过 static import 调用 `AggregationHelper.safeProductName`**，其 `dialect/productName == null` 分支在 Phase 1 fail-loud 后语义变化，必须纳入核对：
> - `ExternalAggregationProcessor.java:69` — `if (dialect == null || !SUPPORTED_DIALECTS.contains(dialect))`
> - `MixedSameDbJoinAggregationProcessor.java:124` — 同上
> - `ExternalExternalJoinAggregationProcessor.java:90` — 同上
> - `EntityAggregationProcessor.java:177` — `if (productName == null || ...)`（bypass EQL 路径，productName 来自 `TableReferenceExecutor` → safeProductName）
>
> 裁定：Phase 1 后 `safeProductName` 在 `SQLException` 时直接抛 infra 错误，**不再返回 null**。因此上述 `== null` 分支仅剩"driver 返回 null 产品名"这一罕见情形会触达——逐处核对：若该情形确需保留（按 unsupported-dialect 处理），保留并加注释说明 null 的语义已从"infra 失败"收窄为"driver 返回空名"；若该情形在实际中不可能发生（driver 契约保证非 null），裁定为移除该死分支。不得留下"看起来还在处理 infra 失败但实际不可达"的误导性代码。
>
> **类别清扫（强制）**：删任一副本前 `rg -n "safeProductName" nop-metadata/` 全仓核对，确认无遗漏的私有副本。去重后该 grep 应仅命中 `AggregationHelper` 定义 + 各调用点（无残留 `private static ... safeProductName`）。

- [x] 删除 `NopMetaProfilingRuleBizModel` 死副本（:190，无调用，直接删）
- [x] 逐个删除 5 个活副本的私有 `safeProductName` 方法
- [x] 各调用点改为 `AggregationHelper.safeProductName(metaData)`（5 个类当前零 AggregationHelper import，须新增 `import static ...AggregationHelper.safeProductName` 或限定调用；注意 TableReferenceExecutor / NopMetaTableQueryAction 各有 2 条调用语句）
- [x] 逐调用方核对：移除已失效的 null 消费分支（infra 现 fail-loud）；保留 genuine unsupported-dialect 处理
- [x] **4 个已走规范入口的 null 消费调用方**（ExternalAggregationProcessor:69 / MixedSameDbJoinAggregationProcessor:124 / ExternalExternalJoinAggregationProcessor:90 / EntityAggregationProcessor:177）逐处裁定 null 分支去留（移除死分支 or 保留+注释收窄语义）
- [x] 类别清扫：`rg -n "private static String safeProductName" nop-metadata/` → 零命中（仅 `AggregationHelper` public 一处）
- [x] 连带死 LOG 清理：删私有副本后，若某文件的 `LOG` 字段/`Logger` import 仅被该副本使用（grep 各文件 `LOG.` 引用数），一并清理（防 unused field/import；compile 门禁兜底）

Exit Criteria:

- [x] 全仓仅 `AggregationHelper.safeProductName` 一处定义；`rg -n "private static String safeProductName" nop-metadata/` 零命中
- [x] 死实例（NopMetaProfilingRuleBizModel）已删
- [x] 5 调用点改走规范入口，编译通过
- [x] 逐调用方 null 分支已清理（无残留死分支）
- [x] **接线验证**：调用点确实调用 `AggregationHelper.safeProductName`（grep 调用点 + 编译），非保留旧副本
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service` 通过（含已有 `TestAggregationOrderByDialectWiring` 等方言接线测试无回归）
- [x] No owner-doc update required（内部 helper 去重，不改对外契约——执行时复核 `docs-for-ai/03-modules/nop-metadata.md` 不依赖各副本）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — AR-14 诊断退化/死码批次清扫（AR-14b/c/d/e）

Status: completed
Targets: `MetaAggregationExecutor.java:47`（死 LOG）、`AggregationHelper.java:212-227`（死参数 `propToCol`）+ `EntityAggregationProcessor.java:260/273`（调用方）、`SqlSelectFieldExtractor.java:114-115`（错误 param）、`MetaManifestBuilder.java:144-146`（脆弱日期格式）

- Item Types: `Fix`

> **AR-14c 死参数移除注意（审查 M4）**：`resolveEntityFieldColumn` 删 `propToCol` 参数后，两个调用点（`EntityAggregationProcessor:260/273`）须同步去掉传参。**关键：`propToCol` 这个 Map 本身必须保留**——它在 `EntityAggregationProcessor` 中由 `resolveEntityColumns(entity, ctx)`（:61）构造，被 `rewriteFilterToColumns`（:121/:214）、列集合构造（:247 `propToCol.values()`）、以及 `executeEntityAggregationBypassEql` / `executeEntityAggregationViaEql`（:85/:88）大量使用。**只删 `resolveEntityFieldColumn` 签名中的 `propToCol` 形参 + 两个调用点的对应实参，绝不删 `propToCol` Map 的构造与其它用途。** 删参数属 public 方法签名变更——`rg -n "resolveEntityFieldColumn"` 确认调用点仅此两处。
>
> **AR-14d 错误 param 设计裁定（审查 M3）**：`SqlSelectFieldExtractor.resolveProjections(SqlStatement stmt)`（:101）**没有 `sql` 参数**——`sql` 只存在于调用方 `extract(String sql)`（:64）。当前 :114 `.param("sql", "unhandled SELECT statement class: " + stmt.getClass().getName())` 把类名塞进名为 `sql` 的 param，与文件内其它 `.param("sql", sql)` 不一致且误导。**裁定方案：给 `resolveProjections` 新增 `String sql` 形参**，从 `extract` 调用处（:91 `resolveProjections(stmt)` → `resolveProjections(stmt, sql)`）传入；:114 改为 `.param("sql", sql)`（真实 SQL 文本，与兄弟错误点一致）。（备选：把该 throw 上移到 `extract` 内——但会拆散 resolveProjections 的职责，不采用。）
>
> **AR-14e 日期格式**：`MetaManifestBuilder.java:146-148` 的 `SimpleDateFormat` 是方法内局部变量（每次调用新建），**线程安全不是主因**（审查 m2 纠正）；真正风险是 `'Z'` 字面量脆弱（当前仅因下一行 `setTimeZone(UTC)` 碰巧正确）。用 `DateTimeFormatter.ISO_INSTANT` 或预构造的不可变 `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)` 替换。输出格式须与现有 manifest 消费方期望一致——若有测试断言该格式字符串，保持等价输出。

- [x] AR-14b：删 `MetaAggregationExecutor` 死 `LOG` 字段（确认零 `LOG.` 引用后删 import + 字段）
- [x] AR-14c：`resolveEntityFieldColumn` 移除 `propToCol` 参数 + 两个调用点（:260/273）同步去掉实参；**保留 `propToCol` Map 本身**（被 filter 重写/列集合/bypass-via-eql 使用）
- [x] AR-14c：`rg -n "resolveEntityFieldColumn" nop-metadata/` 确认全部调用点已同步（仅 :260/273 两处 + 定义）
- [x] AR-14d：`resolveProjections` 新增 `String sql` 形参，从 `extract` 调用处（:91）传入；:114 错误 param 改为 `.param("sql", sql)`（真实 SQL 文本）
- [x] AR-14e：`MetaManifestBuilder` 用不可变 ISO 格式器替换 `SimpleDateFormat`（:146-148），输出格式等价
- [x] 每项新增/强化 focused test：AR-14d（错误 param 含真实 SQL 的断言）、AR-14e（输出格式字符串断言）

Exit Criteria:

- [x] AR-14b：`MetaAggregationExecutor` 无死 `LOG`（`rg -n "LOG" MetaAggregationExecutor.java` 零命中或仅活引用）
- [x] AR-14c：`resolveEntityFieldColumn` 签名无 `propToCol`；全部调用点同步；**`propToCol` Map 构造与 filter 重写/列集合用途保留无破坏**
- [x] AR-14d：`SqlSelectFieldExtractor:114` 错误 param 为真实 SQL（`resolveProjections` 已接受 `sql` 形参；test 断言）
- [x] AR-14e：`MetaManifestBuilder` 用不可变格式器，输出格式与原 `SimpleDateFormat` 等价（test 断言）
- [x] **无静默跳过**：AR-14d 不再把类名当 SQL 文本；AR-14e 不保留脆弱 `SimpleDateFormat`
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service` 通过（1174 tests, 0 failures；全 reactor `-pl nop-metadata -am -T 1C` 亦 BUILD SUCCESS）
- [x] 若改了 public 方法签名（resolveEntityFieldColumn）：No owner-doc update required（内部 helper，执行时复核 owner-doc 不引用该签名——`docs-for-ai/03-modules/nop-metadata.md` 仅引用 `MetaAggregationExecutor` 分派层与 `preprocessHavingArithmetic`，均未触碰）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/08-15.md`）

## Closure Gates

> 本计划含 helper 去重 + 失败语义行为变更（infra fail-loud）+ 诊断清扫。保留构建 + 测试 + 门禁验证。

- [x] `safeProductName` 全仓仅 `AggregationHelper` 一处定义（grep 证据：`rg -n "private static String safeProductName" nop-metadata/` exit 1 零命中；定义唯一 `AggregationHelper.java:469`）
- [x] infra 失败 fail-loud（SQLException → 抛 infra 错误，非 return null 误归因）（`AggregationHelper.java:469-479` + `TestAggregationHelperTableVisibility` 3 例）
- [x] AR-14b-e 全部清扫（死 LOG / 死参数 / 错误 param / 脆弱日期格式）（closure audit Item 3 PASS）
- [x] `./mvnw compile -pl nop-metadata -am -T 1C` 通过（closure audit 复跑 exit 0）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）（BUILD SUCCESS，nop-metadata-service 1174 tests 0 failures + web 1/1；前两轮 reactor 的 `nop-auth TestChannelScanBindLoginE2E` VarCollector NPE 为并发 flake——单跑 4/4 通过、08-09 由其它 mission 引入、与本计划无关，第三轮全绿）
- [x] `node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata` → exit 0（防回退）（**字面成立**：本收口 session 修复扫描器 javadoc 误报（`maskCommentsAndStrings` + 2 fixture，规则零放松）并将 `AggregationHelper.toBigDecimal` NFE catch 按 AR-06 benign-miss 先例形式化（新 `ERR_AGGR_VALUE_NOT_NUMERIC` + DEBUG 信号）→ 125 blocks / 0 hits / exit 0；详见 `ai-dev/logs/2026/08-15.md`）
- [x] 不存在被静默降级到 deferred 的 in-scope 项（`Deferred But Adjudicated` 为空；follow-up 仅 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（`docs-for-ai/03-modules/nop-metadata.md` 不引用 `safeProductName`/`resolveEntityFieldColumn`/`resolveProjections`/manifest 日期格式，已复核——No owner-doc update required）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（fresh session `ses_ffd97de3cffeggfn7jrIla5Juw`，`CLOSURE_AUDIT: PASS`，证据见下方 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证（a）5 调用点确实改走 `AggregationHelper.safeProductName`（接线证据：static import + 可执行调用语句逐文件核实，非保留旧副本）；（b）infra-失败 test 确实触发并断言 infra 错误（`testSafeProductNameInfraFailureThrows` 含 cause `assertSame`）；（c）无空方法体/静默跳过（diff 14 文件全量复核）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure audit 复跑：全 severity 0 findings）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（收口后复跑）

## Deferred But Adjudicated

（本计划无 deferred 项。safeProductName 收敛后各副本的散乱 ErrorCode 统一为单一 infra ErrorCode，属预期收敛非 deferred。）

## Non-Blocking Follow-ups

- 方言映射表（产品名 → 方言）的覆盖度增强（如新增 driver 产品名支持）属独立增强，不在本计划。
- F14/F15/F16/F18/F19 → 归 `2026-08-14-1448-1`；F10-F13 → 归 `2026-08-14-1448-3`。

## Closure

Status Note: 3 个 Phase 全部执行完毕并经独立 closure audit 核验。Phase 1（AR-14a infra fail-loud）+ Phase 2（F17 去重 7×→1×）代码由前一 session（2026-08-14）落地；Phase 3（AR-14b/c/d/e）代码亦已落地但 checklist 未勾选即中断，本收口 session（2026-08-15）逐项对照 live repo 核验后勾选。收口 session 额外完成两件 gate 字面成立工作：(1) 修复 `check-silent-swallow.mjs` 扫描器 javadoc 误报（等长掩码 + 2 个 fixture 回归锁，规则零放松）；(2) 将 `AggregationHelper.toBigDecimal` NFE catch 按 AR-06 benign-miss 先例形式化（新 `ERR_AGGR_VALUE_NOT_NUMERIC` + DEBUG 信号，null 语义不变）——两处 pre-existing 命中清零后 gate 字面 exit 0（125 blocks / 0 hits），不再沿用 08-14 的"pre-existing 豁免"记法（本计划改动文件含 `AggregationHelper.java`，豁免不成立）。4 个保留的 `dialect == null` 分支按 Phase 2 裁定补注释收窄语义（null 仅剩 driver 返回空名情形）。plan 可以关闭：F17 全仓单一规范入口 + 失败语义诚实 + AR-14 诊断退化批次全清 + 全部门禁绿。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，opencode general subagent）
- Audit Session: `ses_ffd97de3cffeggfn7jrIla5Juw`（2026-08-15，mission nop-metadata-invariant-loop）
- Evidence:
  - Phase 1 Exit Criteria：全 PASS——`AggregationHelper.java:469-479` throw infra 错误（非 return null）；`AggregationErrors.java:176-181` ErrorCode 注册（`NopMetadataErrors` 继承可达）；`TestAggregationHelperTableVisibility` 3 例（:94 infra 抛错含 `assertSame(cause)` / :107 "MySQL" 返回 / :116 "ExoticDB" 不抛 infra），surefire 8/8 green。
  - Phase 2 Exit Criteria：全 PASS——`rg "private static String safeProductName" nop-metadata/` 零命中；5 个原副本类 static import + 可执行调用语句逐文件核实（QualityRule :238 / DataSource :310 / TableQueryAction :120+:140 / TableReferenceExecutor :87+:111 / SqlViewFieldTypeInferrer :140）；ProfilingRule 死副本已删（diff 16 deletions）；4 个 null 消费方仅剩 unsupported-dialect 语义（audit 核实），收口 session 补注释收窄。
  - Phase 3 Exit Criteria：全 PASS——MetaAggregationExecutor 零 LOG/Logger；`resolveEntityFieldColumn` 无 propToCol（调用点 :260/:273 同步，`propToCol` Map 本体 :61/:63/:64/:85/:88/:121/:214/:247 用途保留）；`resolveProjections(stmt, sql)` + `.param("sql", sql)` 真实 SQL（`TestSqlSelectFieldExtractor` 3 例断言 `getParam("sql")`，3/3 green；unhandled-class 分支本身为防御性不可达点，以 inspect + 同文件相邻错误点测试覆盖）；`MetaManifestBuilder` 不可变 `DateTimeFormatter`（`TestMetaManifestBuilder.generatedAtIsoUtcFormat` 断言 `"1970-01-01T00:00:00Z"`，6/6 green）。
  - Closure Gates：全 PASS——compile exit 0；`test -pl nop-metadata -am -T 1C` BUILD SUCCESS（service 1174/0 + web 1/1）；`check-silent-swallow --fixture` 8/8 + `--module nop-metadata` 125 blocks/0 hits/exit 0；`scan-hollow-implementations --severity high` 0 findings/exit 0；`check-plan-checklist --strict` exit 0。
  - Anti-Hollow 检查：PASS——diff 14 文件（+134/−97）全量复核，纯删除副本/死码 + 接线 + 签名收窄，无空方法体/静默跳过/TODO 占位；接线为运行时可执行调用（非仅 import）。
  - Deferred 项分类检查：PASS——`Deferred But Adjudicated` 为空；`Non-Blocking Follow-ups` 仅显式 Non-Goals（方言映射覆盖度增强、F14-F19/F10-F13 归兄弟计划），无 in-scope live defect 降级。

Follow-up:

- no remaining plan-owned work（方言映射表覆盖度增强属独立增强，已在 Non-Goals/Non-Blocking Follow-ups 登记）
