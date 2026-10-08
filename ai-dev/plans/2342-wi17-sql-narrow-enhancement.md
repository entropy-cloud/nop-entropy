# 2342 WI17 SQL 窄补强（HAVING/DISTINCT 聚合 supersede + lookup 缓存接线 + changelog 复审）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI17（M5）+ 裁定 §一 P2 #16（显式 supersede 既有 DISTINCT fail-fast 钉码与 sql roadmap WI16 不支持清单；changelog 复审维持不做的裁定）
> Related: 裁定 §三 R5（WI11 形态）；plan 2338（CachingTableLookup）；`ai-dev/plans/nop-stream-sql/18-wi14-dim-lookup-verification.md`（WI14 基线）；sql-subset-and-semantics.md（§4a#6/§4b/§4d + D1=(a)）

## Purpose

WI17 三子交付：a. HAVING 支持 + DISTINCT 聚合支持（显式 supersede 既有 fail-fast 钉码与 sql-subset §4a#6/§4b 清单——roadmap 裁定授权）；b. lookup 缓存接线（CachingTableLookup 在维表 enrich 管线的 close() 落点落地——WI11 遗留的宿主函数 close() override 契约）；c. changelog/retract 维持不做的复审裁定（D1=(a) 确认，零代码变更）。

## 对抗性审查修订（agent_80b257b5，1B+5M+8Mn 全部落 plan 后置 active）

- **B-1 HAVING 聚合扫描**：`scanProjections` 只扫 SELECT projections，从不扫 HAVING——`GROUP BY a HAVING COUNT(b) > 1`（canonical 形态）的 COUNT(b) 不在槽位表 → aggregateRef 返 null → printer 抛 "not part of the compiled aggregate set"。**修订**：在 buildGroupedAggregate 前把 HAVING 表达式纳入 collectAggregates（HAVING-only 聚合获得槽位并进入 spec 计算；投影自然忽略多余槽位）。
- **M-1 分支守卫**：移除 :221-223 后两条 silent-drop 分支——join 路径 compileJoinQuery 从不读 HAVING；plain 路径 SELECT a FROM t HAVING 1=1 静默丢弃。**修订**：join 分支 + `having != null && groupBy == null` 统一提前 typed 拒绝。
- **M-2 DISTINCT 落点**：去重 seen-set 落在 `StreamSqlAggregation` 五个内部类的 acc 扩形 + merge 集合并（NOT SqlRowAggregateOps）；`StreamSqlAggregations.buildComposite` :87 需透传 `call.isDistinct()` 为 `desc.withDistinct(...)`，否则 distinct 字段是死数据。语义钉子：null 不入集；min/max DISTINCT ≡ 非 distinct。
- **M-3 restore 测试**：continuous 路径 accumulator 行经 JSON 往返（SnapshotCopyUtil.typedCopy），带 Set 的 acc 须加 snapshot→restore→继续聚合语义测试。
- **M-4 lookup 接线口径**：DimLookupEnrichFunction 现用手写 ValueState 未持有 CachingTableLookup——改造为包裹 CachingTableLookup + close() override；产品化宿主 out-of-scope（main 零 ITableLookup 调用方），交付 = 测试样例 + docs 契约。
- **M-5 docs-for-ai**：`nop-stream-sql.md` 不支持清单含 DISTINCT 聚合与 HAVING 行，须同步更新；HAVING 不在 sql-subset §4a 排除清单（是纳入面加法）。
- **Minor**：WindowOperator A_R fail-fast 实际 :590-595（非 :449）；distinctFailsFast 钉子必翻（第三颗）；continuous 行形状 List<Object> 非 Object[]；AggCall distinct 向后兼容（fromJson 缺省 false）。

## 执行发现（2026-10-08，实现期）

- **F-A DISTINCT 去重域修正（M-2 落点偏离，语义增强）**：`SqlRowCompositeFunction.add` 传入子聚合的是**原始记录**而非已求值参数——M-2 原文「seen-set 落在五个内部类的 acc 扩形」若按字面在 wrapper 内以记录为键会去重整条记录（同 amount 不同 ts 不去重）。修正：wrapper 注入参数求值器，去重键 = `String.valueOf(求值后参数值)`（JSON 往返稳定），并持有引入该值的原始记录。min/max DISTINCT ≡ 非 distinct 性质由 wrapper 保真（先去重再聚合）。
- **F-B merge 双计数缺陷（M-2「merge 集合并」方案否决）**：continuous reduce 路径 `SqlRowAggregateOps.merge(state, freshAcc)` 中 freshAcc 是单记录新 accumulator——若 merge 做集合并 + inner.merge，同值跨侧出现即双计数（COUNT(DISTINCT) 输入 x,x 得 2）。修正：merge 改为**重放**——遍历 b 侧 seen 表，仅对 a 侧未见过的键经 `inner.add(原始记录)` 重放（seen 表值域存原始记录即为此设计）。有 merge-replay 钉子测试（count + sum 双断言）。
- **F-C M-3 测试暴露既有潜伏缺陷：continuous 聚合 restore 全线损坏**（非本 WI 引入）：`SnapshotCopyUtil.typedCopy` 只保顶层 runtime class——嵌套 `Object[]` 退化为 `ArrayList`、嵌套 Long 标量退化为 Integer（探针实证）。continuous SQL GROUP BY 的 composite accumulator 行 restore 后首个记录即 CCE（count 的纯 Long acc 亦不能幸免）；持久化路径则 ERR_STREAM_TYPE_MISMATCH。**修复**：(1) sql 模块新增 `SqlAccShapeUtil`（asArray/asLong/asDouble），五聚合 + composite 行 + DISTINCT wrapper 全部经归一化；(2) MinMaxAgg 比较改 `compareValues`（整型对按 long、混合数值按 double，防 restore 后 Integer vs Long 的 Comparable CCE）；(3) core `StreamReduceOperator.restoreState` 对值侧启用按 `valueTypeName` 重建（镜像 R5 ST-13 键侧先例），重建失败仍 typed `nop.err.stream.type-mismatch`（原钉子 `testReduceOperatorRestoreTypeMismatch` 保持绿）。新增 core 测试 `testReduceOperatorRestoreRematerializesDegradedContainers`（Long reducer 经 JSON 降级 → 重建 → 继续聚合）。
- **F-D E2E 容器隔离发现**：sql-compile 测试容器内 `OrdersSourceFunction` 为单例 bean，`cancel()` 后 `running=false` 永久——每容器仅支持一次 execute。HAVING/DISTINCT E2E 各自独立测试类（`TestContinuousHavingQueryE2E`/`TestCountDistinctQueryE2E`/`TestTumbleHavingQueryE2E`），不与既有 E2E 共容器。
- **F-E changelog 复审裁定（Decision，零代码变更）**：D1=(a) last-value-wins 终值语义**维持不变**。复审理由：retract 面的唯一入口 `AccumulationMode.ACCUMULATING_AND_RETRACTING` 在 WindowOperator :590-595 spec-only fail-fast（实测锚点，修正 Minor 所记 :449）；continuous 路径 `StreamReduceOperator` 类头 D1 语义钉注在位；无真实用户诉求（survey Tier B D1 档位亦仅提案待裁定，归属 nop-stream-sql-enhancement roadmap WI0a，不在本 WI 重开）。supersession 语义记录：持续聚合逐条 emit 当前归约值 = 下游以最新值为准，本裁定与 sql-subset §1 一致。

## Current Baseline（2026-10-08 研究核实）

- **fail-fast 钉码四处**：StreamSqlCompiler :218-220（SELECT DISTINCT）、:221-223（HAVING）、:650-658（DISTINCT 聚合）、StreamSqlAggregation :91-96（withDistinct(true).create 抛）；测试钉子四颗：TestStreamSqlCompiler havingFailsFast :286 / distinctSelectFailsFast :297 / distinctAggregateFailsFast :335 / TestStreamSqlAggregations.distinctFailsFast。
- **sql-subset-and-semantics.md 三处**：§4a#6 :86（DISTINCT 聚合排除）、§4b :97（纳入表排除行）、§4d :132（错误码对账行）。
- **HAVING 落点**：编译器 :221-223 抛后即可走 aggregate→filter 路径；windowed 路径 aggregate 节点→projection 之间插 filter；continuous 路径 toRow map→projection 之间插 filter（行形状 Object[]，keys 先聚合槽位后，`aggregateRef(grouped)` :820-827 生成 `event[keyCount+slot]`）；HAVING 打印需 AggregateRef 解析器（SqlScalarXplPrinter ctor :70 已支持，不能走 printWhere :829-831 的 null aggregates 通路）。
- **lookup 缓存接线**：CachingTableLookup（plan 2338 已 done）的 close() 生产调用点 = 宿主函数 close() override——DimLookupEnrichFunction 等宿主函数需补 override；process 路径 FunctionUtils.closeFunction 只对 RichFunction 生效。
- **changelog 复审素材**：D1=(a) last-value-wins 裁定链 = sql-subset §1 :10-23 + StreamReduceOperator :13 钉注 + TestContinuousGroupBy 三用例；ACCUMULATING_AND_RETRACTING spec-only fail-fast（WindowOperator :449）。

## Goals

- **a1 HAVING 支持（B-1/M-1 修订后）**：移除编译器 :221-223 fail-fast；在 buildGroupedAggregate 前把 HAVING 表达式纳入 collectAggregates（canonical 形态 COUNT(b) 不在 SELECT 也可编译）；HAVING 条件编译为 aggregate 后 filter（windowed + continuous 两路径），带 AggregateRef 解析（聚合引用 `event[keyCount+slot]` 映射）；**分支守卫**：join 路径 HAVING typed 拒绝 + `having != null && groupBy == null` 统一提前 typed 拒绝（防 silent-drop 违反 :82 编译器契约）。
- **a2 DISTINCT 聚合支持（M-2 落点修正）**：移除 :650-658 与 StreamSqlAggregation :91-94 fail-fast；`AggCall` 增 distinct 字段（toJson 增量 / fromJson `Boolean.TRUE.equals` 缺省 false 向后兼容）；`StreamSqlAggregations.buildComposite` :87 透传 `call.isDistinct()` 为 `desc.withDistinct(...)`；**去重 seen-set 落在 StreamSqlAggregation 五个内部类的 acc 扩形 + merge 集合并**（null 不入集；min/max DISTINCT ≡ 非 distinct）。SELECT DISTINCT（非聚合 dedup）维持排除。
- **supersede 面**：测试钉子翻转（havingFailsFast→havingFilterShape、distinctAggregateFailsFast→distinctAggregateShape）；sql-subset-and-semantics.md §4a#6/§4b/§4d 更新（DISTINCT 聚合 + HAVING 从排除清单移入纳入面，标注 WI17 supersede 版本注记）；SELECT DISTINCT + UNION DISTINCT 维持排除（零改动）。
- **b lookup 缓存接线（M-4 口径修正）**：DimLookupEnrichFunction 改造为包裹 CachingTableLookup（现手写 ValueState 未持有）+ close() override 调 close()（幂等已保证）；**产品化宿主 out-of-scope**（main 零 ITableLookup 调用方——process 桥接形态下宿主由应用自备），交付 = 测试样例 + docs 契约 + 2338 交接口径对齐。
- **c changelog 复审裁定**：Decision 型 item——D1=(a) last-value-wins 维持不变（零代码变更），复审理由记录进 plan（ACCUMULATING_AND_RETRACTING spec-only fail-fast + WindowOperator :449 门禁现状已封死 retract 面，无真实诉求不改）。
- 测试：HAVING shape（windowed+continuous 两路径 filter 插入 + **canonical 形态 COUNT(b) 不在 SELECT** 的双路径钉子）+ 语义断言；DISTINCT 聚合 shape + 去重语义断言（COUNT(DISTINCT x)）；**continuous 路径 DISTINCT accumulator 的 snapshot→restore→继续聚合语义测试**（M-3）；supersede 钉子翻转（havingFailsFast、distinctAggregateFailsFast、**TestStreamSqlAggregations.distinctFailsFast** 三颗必翻）；lookup close 接线断言。

## Non-Goals

- SELECT DISTINCT（非聚合 dedup）；UNION DISTINCT；全局聚合 + HAVING；ORDER BY/LIMIT；changelog/retract 实现；LOOKUP JOIN SQL 语法。

## Scope

### In Scope

- 编译器 HAVING/DISTINCT 聚合支持 + AggCall spec 扩形 + supersede 钉子翻转 + sql-subset docs 更新 + lookup close 接线 + changelog 复审 Decision。

### Out Of Scope

- SELECT DISTINCT dedup 执行语义；UNION DISTINCT；全局聚合；ORDER BY/LIMIT；LOOKUP JOIN 语法。

## Execution Plan

### Phase 1 - HAVING + DISTINCT 聚合 + supersede + 接线

Status: completed
Targets: nop-stream-sql（编译器+聚合+eval）、测试、connector/lookup close 接线、docs

- Item Types: `Feature + Decision + Proof`

- [x] HAVING：编译器 fail-fast 移除 + **HAVING 纳入聚合扫描（B-1）** + 两路径 filter 插入 + AggregateRef 打印通路 + join/plain 分支守卫（M-1）
- [x] DISTINCT 聚合：AggCall 扩形 + StreamSqlAggregation 门禁移除 + **StreamSqlAggregation 内部类 acc/merge 去重扩形 + buildComposite distinct 透传（M-2）**（执行修正：统一 DistinctAggregateWrapper + 参数求值器 + merge 重放，见 F-A/F-B）
- [x] supersede：测试钉子翻转（havingFailsFast、distinctAggregateFailsFast、TestStreamSqlAggregations.distinctFailsFast）+ sql-subset-and-semantics.md 三处更新（DISTINCT）+ §4b 纳入面加法（HAVING）+ WI17 版本注记 + **nop-stream-sql.md 不支持清单同步（M-5）**
- [x] lookup close 接线：DimLookupEnrichFunction 改造为包裹 CachingTableLookup + close() override + docs 契约（M-4 口径对齐；WI14 per-key ValueState 快路径保持，物化层叠加其上）
- [x] changelog 复审 Decision 记录（F-E：D1=(a) 维持，零代码变更）
- [x] 测试：shape + 语义 + supersede 翻转（三颗钉子）+ close 接线 + **restore 语义测试（M-3）**（另含 merge-replay 双断言、E2E 三条、core 重建测试；执行发现 F-C/F-D 一并落测试）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-sql,nop-stream/nop-stream-runtime -am` 全绿
- [x] **接线验证**：HAVING filter 在编译产物 XML 中真实出现（windowed+continuous 两路径）并被 flow builder 消费（三条 E2E：compile → DslModelParser → StreamModelDslBuilder → execute → sink 行断言，TestContinuousHavingQueryE2E/TestTumbleHavingQueryE2E/TestCountDistinctQueryE2E）
- [x] **无静默跳过**：无 GROUP BY 的 HAVING typed 拒绝 + join 路径 HAVING typed 拒绝（M-1 两分支）；UNION DISTINCT 维持排除
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: sql-subset docs、plan/roadmap

- Item Types: `Proof`

- [x] docs：sql-subset-and-semantics.md supersede 版本注记 + **nop-stream-sql.md 不支持清单同步（M-5）** + sql roadmap WI16 交叉更新
- [x] 独立子 agent closure audit；roadmap WI17 勾选；git commit

Exit Criteria:

- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI17 已勾选、提交完成

## Closure Gates

- [x] HAVING/DISTINCT 聚合落地且 supersede 面完整（钉码+测试+docs 三处一致）
- [x] `./mvnw test -pl nop-stream/nop-stream-sql,nop-stream/nop-stream-runtime -am` 全绿（sql 91/0/0 + runtime 1320/0/0，BUILD SUCCESS，`_tmp/wi17-fulltest.log`；audit 后补钉 distinctMinMaxMatchesNonDistinct 复跑绿）
- [x] **Anti-Hollow Check**：HAVING filter 与 DISTINCT 去重在编译产物和执行语义中被真实消费（含 canonical 形态 COUNT(b) 不在 SELECT 的钉子 + restore 语义测试）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-sql --severity high` 退出码 0（0 findings）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### SELECT DISTINCT（非聚合 dedup）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需新增 keyBy+keyed 去重状态执行语义；roadmap WI17 范围仅 DISTINCT 聚合 + HAVING
- Successor Required: `no`

### LOOKUP JOIN SQL 语法

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: SQL 面无 LOOKUP JOIN 语法基线；lookup 缓存经 process 桥接（WI14 基线）已可组合，专用语法留真实诉求
- Successor Required: `no`

## Closure

Status Note: WI17 三子交付全落地：a. HAVING（两路径 aggregate 后 filter + canonical 形态 + join/plain typed 守卫）与 DISTINCT 聚合（DistinctAggregateWrapper 参数值域去重 + merge 重放防双计数 + AggCall spec 扩形向后兼容）supersede 既有钉码与 sql-subset §4a#6/§4b/§4d（三处一致，钉子三颗翻转）；b. DimLookupEnrichFunction 持有 CachingTableLookup + close() override（WI14 判别断言零削弱）；c. changelog 复审 D1=(a) 维持（零代码变更，F-E）。执行发现三项修复入档：F-A 去重域修正、F-B merge 双计数否决集合并方案、F-C M-3 暴露的既有 continuous restore 缺陷（SqlAccShapeUtil 归一化 + MinMax 数值感知比较 + StreamReduceOperator 值侧重建，MISMATCH 钉子保持绿）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_260c2e8f-44d1-4716-a7c5-9ee413860132），全程只读
- Evidence:
  - 实现 vs Goals 逐条 PASS（A 组 a1/a2/supersede/b/c 六面 file:line：守卫 :229-236、扫描 :663-673、两路径插入 :382/:441、wrapper 去重键 :442-445、merge 重放 :475-487、AggCall :60-64/:111-112/:165、透传 :87-89）
  - 测试真实覆盖 PASS（钉子翻转 grep 确认；merge-replay count+sum 双断言 :165-187；typedCopy restore 续聚合 :195-215；三条 E2E 全链路断言；core 重建测试 :291-330 + MISMATCH 钉子零削弱 :239-262）
  - 运行证据 PASS：BUILD SUCCESS——sql 91/0/0 + runtime 1320/0/0（`_tmp/wi17-fulltest.log`，mtime 晚于全部源文件）
  - Anti-Hollow PASS（E2E 执行期证据；SqlAccShapeUtil/重放/重建均被测试消费；无空体/静默跳过）
  - gate 工具实测全 0：check-doc-links --strict / check-plan-checklist --strict / scan-hollow-implementations --severity high
  - 文本一致性 PASS（F-A..F-E 与代码一致；F-E 锚点 :590-595 实测复核）
  - Deferred 分类 PASS（SELECT DISTINCT 与 LOOKUP JOIN 语法均 out-of-scope，main 零 ITableLookup 调用方实证）
  - 审计 3 Minor 全部处置：M-1 日志测试数修正（90→91 实测，剔除探针陈旧报告）、M-2 编译器类头 javadoc fail-fast 矩阵 WI17 更新、M-3 补 min/max DISTINCT 恒等式钉子（distinctMinMaxMatchesNonDistinct，复跑绿）
- Audit Session: agent_260c2e8f-44d1-4716-a7c5-9ee413860132

Follow-up:

- no remaining plan-owned work

## Non-Blocking Follow-ups

- continuous COUNT(DISTINCT) 的精确去重内存随作业生命周期增长（窗口路径随窗清除）——owner doc 已声明代价，属精确语义固有成本；压缩/近似去重留真实诉求触发（out-of-scope improvement）。
