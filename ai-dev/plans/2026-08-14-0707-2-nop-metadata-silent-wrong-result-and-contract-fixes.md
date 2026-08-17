# 02 — nop-metadata 静默错算与契约缺口修复（SLA 截断/NFE 逃逸 / group-key 碰撞 / selection 丢弃）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Mission: nop-metadata-invariant-loop
> Source: `ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md`（AR-01、AR-02、AR-03）、`ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md`（F4）
> Related: AR-22（AR-01/02 同方法的未知 unit fail-fast 先例）、R8.3（AR-03 cross-DB 内存聚合同族）、CrudBizModel（F4 `selection` 正确消费的参照实现）

## Purpose

把 2026-08-14 审计中 **4 个 P1 "silent-wrong-result / 契约缺口"** 缺陷收口为：结果正确（带错误信号或正确数值）、公开参数契约收敛、每项均有回归测试钉死。这四个缺陷共享同一特征——**无错误信号、结果静默错误**，正是本 mission silent-swallow 不变式想消灭、但落在该不变式扫描范围之外（数值/字符串语义静默错算、输入静默丢弃）的模式。

## Current Baseline

> 行级读证见 Source 审计 evidence 段。

- **AR-01（P1，SLA 分数截断）**：`MetaContractChecker.toDurationMillis` 把 `amount` 按 `double` 解析，但每次转毫秒都 `(long) amount` 截断向零。`{"interval":0.5,"unit":"hour"}` → `0` ms → 目录被恒判永久过期；`1.5 day` → `1 day`（过严）。AR-22 已为"未知 unit"加 fail-fast，但未覆盖分数 amount 截断。
- **AR-02（P1，SLA NFE 逃逸）**：同方法 `:336-342`，`catch` 仅捕 `ClassCastException`；`amountObj` 为非数字 String 时 `Double.parseDouble("oops")` 抛未捕获 `NumberFormatException`，逃逸出 `toDurationMillis` 与顶层 `check`，违反两层错误处理契约（应映射 `ERR_CONTRACT_SLA_INVALID`）。
- **AR-03（P1，group-key 碰撞）**：`AggregationHelper.memoryGroupBy`（cross-DB 内存聚合）用 `\u0001` 连接维度值、`\u0000` 作 null 哨兵拼 String key。`("a","\u0001b")` 与 `("a\u0001","b")` 拼出相同 key → 行被错误合并；`null` 与字面量 `"\u0000"` 碰撞。SQL 侧路径不受影响，故内存与 SQL 路径对同一数据**不一致**，破坏 R8.3 建立的 `testCrossDbMemoryHavingMatchesSameDbSqlPath` 不变式。本模块显式摄取外部表/SQL 视图数据，控制字符入参非纯理论。
- **F4（P1，selection 静默丢弃）**：`NopMetaTableBizModel` 的 `queryTableData`/`queryJoinData`/`queryAggregation` 三方法声明 `FieldSelectionBean selection` 参数（且在 `INopMetaTableBiz` 接口中），但方法体从不读取、也不转发给任何 executor；参照 `CrudBizModel` 中同参数真实驱动 `fetchResultWithSelection`。注意区别于 P2-24 carve-out（覆盖的是 `items` 返回类型，非被忽略的参数）。
- **构建/测试命令**（mission 配置）：`./mvnw test -pl nop-metadata -am -T 1C`。

## Goals

- AR-01：SLA 分数 amount 正确换算毫秒（`0.5h` → 1_800_000 ms），不再静默截断。
- AR-02：非数字 SLA amount 映射 `ERR_CONTRACT_SLA_INVALID`，不再逃逸裸 `NumberFormatException`。
- AR-03：内存 group-by 用结构性 key（值级 equals/hashCode 或长度前缀），控制字符不再导致碰撞；内存路径与 SQL 路径对脏数据产出一致。
- F4：`selection` 要么被真实消费（转发 executor 或后置 key 过滤），要么显式移除/标 deprecated 并在 owner-doc 声明——不再静默接受又丢弃。
- 每项均有回归/对抗测试。

## Non-Goals

- 不把不变式门禁扩展到"silent-wrong-result"检测（那是 Cycle 2 / I1；本计划只登记于日志，提示下轮）。
- 不处理 P2 精度/类型族（AR-10 `toBigDecimal` Long>2^53 精度、String 数值静默跳过）——入 Follow-up Backlog。
- 不重写 cross-DB 聚合的 SQL 侧路径（其行为正确）。
- **同批其余 P0/P1 由 sibling plans 覆盖**：F1/F2/AR-04 由 `2026-08-14-0707-1-...`（安全攻击面）；F3 由 `2026-08-14-0707-3-...`（INV-LIMIT 默认构建）。本计划仅处理 AR-01/02/03/F4 的静默错算与契约缺口。

## Scope

### In Scope

- AR-01 SLA 分数截断修正 + 回归测试。
- AR-02 SLA NFE 逃逸 → ErrorCode 映射 + 测试。
- AR-03 group-key 结构化 + 对抗测试 + 与 SQL 路径一致性验证。
- F4 `selection` 契约收敛（消费或显式 deprecate/remove）+ 测试/文档。
- 受影响 owner-doc 同步。

### Out Of Scope

- P2 精度/数值族（AR-10）、profiler/lineage/manifest/reconciliation 的 P2。
- cross-DB 聚合 SQL 路径重写。

## Execution Plan

### Phase 1 — SLA 正确性（AR-01 + AR-02，同方法合并）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/contract/MetaContractChecker.java`

- Item Types: `Fix | Proof`

- [x] AR-01：分数 amount 正确换算毫秒（不再 `(long) amount` 截断；须保留亚单位精度换算——注意单位级取整会错，如 `0.5h` 须得 1_800_000 ms 而非 3_600_000/0 ms）——**须覆盖两条 early-return 路径**：通用 `:382` 与 week 分支 `:373`（二者均有 `(long)` 截断）
- [x] AR-02：扩大 catch 覆盖 `NumberFormatException`（或 `Exception`），映射 `ERR_CONTRACT_SLA_INVALID`（带 `contractId` + 错误值）
- [x] 新增回归测试：`{"interval":0.5,"unit":"hour"}` → 1_800_000 ms，对 10 分钟前采集的目录判定**未过期**；并覆盖 week 分支分数（如 `0.5w` 不被截断为 0）
- [x] 新增回归测试：`{"interval":"oops","unit":"min"}` → `getErrorCode() == ERR_CONTRACT_SLA_INVALID`，无 `NumberFormatException` 逃逸

Exit Criteria:

- [x] 分数 SLA 产出正确毫秒（测试断言数值）
- [x] 非数字 amount 映射 ErrorCode，无裸 unchecked 逃逸
- [x] **无静默跳过**：未知 unit（AR-22 已覆盖）与不可解析 amount 均显式失败
- [x] 既有合法 SLA 检查用例全绿
- [x] owner-doc（SLA 契约：分数/非法值的语义）同步至 `docs-for-ai/03-modules/nop-metadata.md`
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 2 — cross-DB 内存 group-key 结构化（AR-03）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java`

- Item Types: `Fix | Proof`

- [x] 用结构性 key 替换分隔符拼接 String（值级 equals/hashCode 的包装类型，或长度前缀编码），消除 `\u0001`/`\u0000` 碰撞
- [x] 新增对抗测试：维度值 `("a","\u0001b")` 与 `("a\u0001","b")` 产出**两个**不同分组；`null` 与字面量 `"\u0000"` 不碰撞
- [x] 复跑/增强 `testCrossDbMemoryHavingMatchesSameDbSqlPath`：内存路径与 SQL 路径对含控制字符的脏数据产出一致

Exit Criteria:

- [x] 对抗载荷不再碰撞合并分组（断言分组数 = 期望）
- [x] 内存路径与 SQL 路径对脏数据一致（R8.3 不变式重新成立）
- [x] **无静默跳过**：分组 key 构造不再依赖值不含分隔符的隐含假设
- [x] 既有 cross-DB 聚合测试全绿
- [x] owner-doc 同步（cross-DB 内存聚合 group-key 语义）至 `docs-for-ai/03-modules/nop-metadata.md`
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 3 — `selection` 参数契约收敛（F4）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java`（**条件性 target**：若"转发 executor"→ 可能含 `NopMetaTableQueryAction.java` / `MetaJoinExecutor.java` / `MetaAggregationExecutor.java`（三者方法签名现均不接受 `selection`，需改签名或 overload，经 live repo 核实）；若"deprecate/remove"→ 含 `nop-metadata/nop-metadata-dao` 的 `INopMetaTableBiz.java`）

> **Protected Area 提示**：`INopMetaTableBiz` 属跨模块公共 API（`nop-metadata-dao`），AGENTS.md 定义为 Protected Area（"跨模块公共 API：plan-first"）。若裁定选 "remove"，须先获 plan-first clearance。**推荐非 breaking 路径**：consume（后置 key 过滤）或 `@Deprecated` 标注 + owner-doc 声明当前为 no-op。**注意**：`CrudBizModel` 用 `graphQLEngine.fetchResultWithSelection(entity,...)` 消费 selection，作用于 ORM 实体；nop-metadata 查询结果为 `List<Map<String,Object>>`（P2-24 carve-out），`fetchResultWithSelection` 不直接适用，仅供"selection 语义"参照，勿照搬实现。

- Item Types: `Fix | Decision | Proof`

- [x] 裁定并执行（`Decision`，推荐非 breaking）：consume `selection`（对结果 Map 后置 key 过滤，`FieldSelectionBean.getFields()` 取字段名集）**或** `@Deprecated` 标注 + owner-doc 声明当前 no-op；裁定需记录理由。若选 remove，须先获 Protected Area plan-first clearance
- [x] 若消费：新增测试验证字段过滤生效（目标测试文件 `TestNopMetaTableQueryBizModel.java`）；若 deprecate：owner-doc 明确声明 + 接口 `@Deprecated` 标注
- [x] 确保三方法（queryTableData / queryJoinData / queryAggregation）处理一致

Exit Criteria:

- [x] `selection` 不再"接受即丢弃"——要么生效要么显式声明 no-op/deprecated
- [x] 若消费：字段过滤测试断言敏感/宽表列被过滤
- [x] **无静默跳过**：参数要么驱动行为要么显式失败/声明，不静默接受
- [x] 与 P2-24 carve-out（`items` 返回类型）的边界在 owner-doc 中写清（`docs-for-ai/03-modules/nop-metadata.md`）
- [x] owner-doc 同步
- [x] `ai-dev/logs/2026/08-14.md` 已追加

## Closure Gates

- [x] AR-01/AR-02 SLA 正确性修复 + 测试
- [x] AR-03 group-key 结构化 + 对抗测试 + 内存/SQL 一致性
- [x] F4 `selection` 契约收敛 + 测试/文档
- [x] 四项均为 confirmed live defect / contract drift，未降级为 follow-up
- [x] 受影响 owner-doc 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证修复在运行时路径生效（SLA 检查器/内存聚合器/BizModel 被真实调用），非仅类型存在
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 4 条不变式门禁仍零命中

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- AR-10 `toBigDecimal` Long>2^53 精度丢失 + String 数值静默跳过（P2，精度族）——见 mission roadmap Follow-up Backlog
- 建议下轮（Cycle 2 / I1）评估：把不变式从"catch 块"扩展到"silent-wrong-result"检测（`(long)double` 截断、`contains` 类型分类、分隔符 key）——见日志登记

## Closure

Status Note: 四项 P1 silent-wrong-result / 契约缺口缺陷全部修复并钉死回归测试。AR-01/AR-02 修正 SLA 分数截断与 NFE 逃逸；AR-03 用结构性 key 消除内存 group-by 控制字符碰撞；F4 经 live 验证裁定为显式 no-op（GraphQL 自动注入 DTO 级响应选择集，不透明 Map 结果无行级 selection 语义），以 Javadoc + owner-doc 显式声明取代"静默接受又丢弃"。全模块测试 1103 全绿。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 执行 agent（mission-driver EXEC_PLANS，本 session）
- Evidence:
  - Phase 1（AR-01/AR-02）：`MetaContractChecker.toDurationMillis` 两条换算路径均改为 `(long)(amount * unitMillis)` 先乘后取整（不再 `(long)amount` 截断）；不可解析 amount 映射 `ERR_CONTRACT_SLA_INVALID`（带 cause + 错误值）。回归测试 `TestNopMetaDataContractBizModel`：`testCheckContractSlaFractionalHourFresh/Stale`、`testCheckContractSlaFractionalWeekFresh`、`testCheckContractSlaNonNumericAmountFailsLoud`。PASS（26 tests, 0 fail）。
  - Phase 2（AR-03）：`AggregationHelper.memoryGroupBy` 改用 `LinkedHashMap<List<Object>,...>` 结构性 key（值级 equals/hashCode）。对抗测试 `TestCrossDbInMemoryAggregationProcessor`：`testMemoryGroupByControlCharDelimiterNoCollision`、`testMemoryGroupByNullVsLiteralNulCharNoCollision`。既有 `testCrossDbMemoryHavingMatchesSameDbSqlPath`（R8.3 内存/SQL 一致性）仍绿。PASS（24 + 28 tests, 0 fail）。
  - Phase 3（F4 裁定）：live 验证发现 GraphQL 引擎（`ReflectionBizModelBuilder:392-393`）自动注入**响应字段选择集**（DTO 级 `{ tableType items }`）到 `selection` 参数——非调用方传入的行列过滤。consume（后置 key 过滤）会错误清空行内列（实测 3 个 GraphQL 路径测试 red）。故裁定选**显式 no-op + owner-doc 声明**（非 breaking）：三方法 Javadoc 显式声明 selection 为 no-op（不透明 Map 结果，无字段级选择语义），回归测试 `testQueryTableDataSelectionIsExplicitNoOp` / `testQueryAggregationSelectionIsExplicitNoOp` 钉死 no-op 契约（非 null selection 不裁剪列）。PASS（23 tests, 0 fail）。
  - `./mvnw test -pl nop-metadata -am -T 1C`：1103 tests, 0 failures, 0 errors（1 skipped 预存）。
  - `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C`：BUILD SUCCESS（8 模块全绿）。
  - owner-doc `docs-for-ai/03-modules/nop-metadata.md` 已同步三段（selection no-op / SLA 分数与非法值语义 / cross-DB 内存 group-key 结构化）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（38 items 全勾选）。
  - Anti-Hollow：SLA 检查器经 `NopMetaDataContractBizModel.checkContract → contractChecker.check` 真实调用；内存聚合器经 `CrossDbInMemoryAggregationProcessor.execute → memoryGroupBy` 真实调用；BizModel 三方法经 GraphQL + 直接调用测试真实执行。
  - 注：本 closure 由执行 agent 自验；mission 下一轮 OPEN_AUDIT/CLOSEURE_VERIFY 可独立复核（fresh session）。

Follow-up:

- AR-10 `toBigDecimal` Long>2^53 精度 + String 数值静默跳过（P2，已登记 Non-Blocking Follow-ups）
- 建议 Cycle 2/I1 评估：不变式从"catch 块"扩展到"silent-wrong-result"检测（`(long)double` 截断、分隔符 key）
