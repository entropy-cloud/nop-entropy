# 2026-07-22-0900-2 nop-metadata AggregationContext & BizModel Splitting

> Plan Status: active
> Last Reviewed: 2026-07-22
> Source: `ai-dev/plans/2026-07-21-1200-2-nop-metadata-code-quality-and-docs.md` Deferred But Adjudicated (B1/B2/B3)；`ai-dev/design/nop-metadata/aggregation-processor-split.md`
> Related: `04-nop-metadata-aggregation-processor-split.md`（MetaAggregationExecutor 拆 7 Processor 已完成）

## Purpose

执行 plan 2026-07-21-1200-2 中 deferred 为 `optimization candidate` 的 B1-B3 三项拆分工作：将 `AggregationContext`（1854 行）、`NopMetaTableBizModel`（931 行）、`NopMetaLineageEdgeBizModel`（878 行）拆分为聚焦单一职责的子文件。这三项是当前 nop-metadata 模块中最大的单文件，拆分后可显著提升可维护性、测试覆盖能力和 AI token 效率。

## Current Baseline

- `AggregationContext.java` 当前 1854 行，混合了聚合共享状态（`AggregationContext` 数据结构）、helper 方法（34+ 个 private static）、内部类型定义（10+ 个 inner class/interface）以及入口路径分派的残余逻辑。除 `executeAggregation` 外的所有公共静态方法（`preprocessHavingArithmetic`、`substituteAndValidateHavingExpr` 等）仍驻留在此类，外部 5 个文件引用它们
- `NopMetaTableBizModel.java` 当前 931 行，承载两类职责：CRUD（findPage/get/save/delete 由 CrudBizModel 超类提供）和自定义 action（`queryAggregation`、`profileTable`、`createSqlTable`、`queryEntityData`、`queryExternalData`、`querySqlData`）。Plan 06 已移除 3 个 `@Deprecated` 私有包装方法，但剩余自定义 action 仍集中在单类中
- `NopMetaLineageEdgeBizModel.java` 当前 878 行，承载血缘 action（`getUpstream`、`getDownstream`、`getImpactAnalysis`、`getPath`、`extractMeasureLineage`）和 LineageEdge CRUD 约束逻辑（`save`/`delete` override）
- 三个文件均无针对 BizModel 拆分的设计文档。拆分方案需在执行前先在 `ai-dev/design/nop-metadata/` 中完成设计决策（设计-first 但本 plan 可产出设计 → 执行，不要求先有独立 design plan）
- Plan 04 的 `aggregation-processor-split.md` 仅覆盖 `MetaAggregationExecutor` 拆 7 Processor，不涉及 `AggregationContext` 或 BizModel
- 现有测试覆盖这三个文件的关键路径。本 plan 执行前将通过 `./mvnw test -pl nop-metadata -am` 确认当前基线，以此作为回归检测基准

## Goals

- `AggregationContext` ≤ 800 行（共享数据结构 + 内部类型 + 构造器 + 公共静态方法 + 不可再拆的通用 helper；可独立抽取的 helper 和 inner type 拆分到包级工具类）
- `NopMetaTableBizModel` ≤ 500 行（CRUD 委托超类，自定义 action 拆分到 `NopMetaTable*Action` 或 BizModel 子包）
- `NopMetaLineageEdgeBizModel` ≤ 500 行（血缘 action 拆分到 `NopMetaLineageEdge*Action` 或同名子包）
- 三项拆分均不改变 public API 签名或 GraphQL schema 行为
- 无新增 method/action——纯拆分重构

## Non-Goals

- 不改造 `MetaJoinExecutor`（55064 字节，独立模块）
- 不重构 `FilterToSqlTranslator`/`GranularityBucketing`/`MemoryFilterEvaluator`/`MemoryOrderByComparator` 等已独立组件
- 不优化聚合 SQL 生成逻辑（功能等价拆分）
- 不修改 `ERR_AGGR_*` 常量位置（保留 `MetaAggregationExecutor` 中）
- 不修改 `I*Biz` 接口或 GraphQL schema
- 不新建 design doc contract 之外的 API

## Scope

### In Scope

- `AggregationContext.java` 拆分：将公共静态 helper 方法抽取到 `AggregationHelper.java` 包级工具类；将 `AggregationContext` 保留为 pure data class + 构造器 + inner type 定义
- `NopMetaTableBizModel.java` 拆分：抽取 `profileTable`、`createSqlTable`、`queryEntityData`、`queryExternalData`、`querySqlData` 到 `NopMetaTableQueryAction.java`（名称由拆分时设计决策定）；BizModel 保留 dispatch 和 CRUD
- `NopMetaLineageEdgeBizModel.java` 拆分：抽取 `getUpstream`、`getDownstream`、`getImpactAnalysis`、`getPath`、`extractMeasureLineage` 到 `NopMetaLineageEdgeQueryAction.java` 或按 action 分组
- 设计决策文档：在 `ai-dev/design/nop-metadata/` 下创建 `aggctx-and-bizmodel-split.md` 记录拆分决策（拒绝方案、最终结构）
- 集成测试回归验证：`./mvnw test -pl nop-metadata -am` 保持通过

### Out Of Scope

- `NopMetaSearchBizModel` xmeta（B4，watch-only residual，utlity BizModel 无需 xmeta）
- `NopMetadataErrors.java` 拆分（1001 行，P3 optimization candidate）
- ORM 列排序（C3，optimization candidate）
- `nop-metadata-core` 模块重命名
- 空 entity stub 删除
- Flat BizModel 目录治理

## Execution Plan

### Phase 1 — 设计决策 + AggregationContext 拆分

Status: planned
Targets: `AggregationContext.java` (in `.../service/query/`) → `AggregationHelper.java` (same package)

- Item Types: `Decision`, `Fix`, `Proof`

- [ ] 创建 `ai-dev/design/nop-metadata/aggctx-and-bizmodel-split.md`，记录拆分决策：
  - AggregationContext 拆分边界（纯 state class vs helper 工具类）
  - `AggregationHelper` 包含的 helper 方法清单
  - 各拆分后文件的责任和调用关系
- [ ] IoC/bean 注册分析：确认 Nop IoC 中 `@BizModel("NopMetaTable")` 可以被多个 Java 类共享（BizModel 类 + dispatch action class），不需要额外 bean 注册配置。新抽取的 action class 不在 runtime 通过 IoC 注入，而是由 BizModel 通过 `@Inject` 或构造函数注入持有——确认此模式与现有 `MetaAggregationExecutor` 拆分 Processor 的模式一致（verified via Plan 04 precedent）
- [ ] 执行 `./mvnw test -pl nop-metadata -am` 记录当前测试基线（expected ≥ 706 pass），作为后续各 Phase 回归验证基准
- [ ] 将 `AggregationContext.java` 中的公共静态 helper 方法抽取到 `AggregationHelper.java`（包级工具类，无状态）
- [ ] `AggregationContext` 保留：所有 inner type（`MeasureSpec`/`DimensionSpec`/`JoinMeasureSpec` 等 10+ 类型）、构造器、per-request 字段——维持 processor 和外部测试类的 import 不失效
- [ ] 更新所有引用 `AggregationContext.xxxHelperMethod` 的调用点到 `AggregationHelper.xxxHelperMethod`
- [ ] 验证 `./mvnw compile -pl nop-metadata -am` 通过
- [ ] 验证 `./mvnw test -pl nop-metadata -am` 通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `AggregationContext.java` ≤ 800 行（通过 `wc -l` 验证）
- [ ] `AggregationHelper.java` 存在并包含所有被抽取的 helper 方法
- [ ] 0 编译错误
- [ ] 706 测试基线保持通过（或 ≥ 基线，允许预存 flaky test 波动）
- [ ] **端到端验证**：`TestNopMetaAggregationBizModel` 及 3 个拆分后的 aggregation test 文件全部通过，覆盖所有 7 条执行路径
- [ ] **接线验证**：`MetaAggregationExecutor` 分派器调用 processor 时传入的 `AggregationContext` 构造正确，helper 方法调用链未断裂（通过 test 覆盖）
- [ ] **无静默跳过**：拆分后无空方法体/continue/吞异常。AggregationContext 构造函数中的校验逻辑保持原样
- [ ] `ai-dev/design/nop-metadata/aggctx-and-bizmodel-split.md` 设计决策文档已创建
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — NopMetaTableBizModel 拆分

Status: planned
Targets: `nop-metadata/nop-metadata-service/.../entity/NopMetaTableBizModel.java`

- Item Types: `Decision`, `Fix`, `Proof`

- [ ] 在 Phase 1 创建的 design doc 中追加 NopMetaTableBizModel 拆分决策
- [ ] 从 `NopMetaTableBizModel` 抽取非 CRUD action 到独立的 action class（e.g. `NopMetaTableQueryAction` or per-action files）
- [ ] `NopMetaTableBizModel` 保留：CrudBizModel 自动继承的方法 + 极少量须在当前类中 dispatch 的自定义 action（可选 dispatch 入口）
- [ ] 拆分后所有 action 的 `@BizMutation`/`@BizQuery` 注解参数签名不变，GraphQL schema 不受影响
- [ ] 验证 `./mvnw compile -pl nop-metadata -am` 通过
- [ ] 验证 `./mvnw test -pl nop-metadata -am` 通过

Exit Criteria:

- [ ] `NopMetaTableBizModel.java` ≤ 500 行
- [ ] 所有原暴露的自定义 action 在 GraphQL schema 中仍然可用（`findPage/get/save/delete/queryAggregation/profileTable/createSqlTable/queryEntityData/queryExternalData/querySqlData`）
- [ ] 0 编译错误
- [ ] 706 测试基线保持通过
- [ ] **端到端验证**：BizModel action 测试通过 GraphQL executeRpc 或 BizModel 注入 > 拆分后的 action class 被调用
- [ ] **接线验证**：meta 文件中引用的 action 名到拆分后的 BizModel/dispatch 方法确实连通（通过 grep 检查 action 名在新 class 中存在）
- [ ] **无静默跳过**：无空方法体/continue/吞异常
- [ ] `No owner-doc update required`: 纯内部重构，无 public API 变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — NopMetaLineageEdgeBizModel 拆分

Status: planned
Targets: `nop-metadata/nop-metadata-service/.../entity/NopMetaLineageEdgeBizModel.java`

- Item Types: `Decision`, `Fix`, `Proof`

- [ ] 在 Phase 1 创建的 design doc 中追加 LineageEdgeBizModel 拆分决策
- [ ] 从 `NopMetaLineageEdgeBizModel` 抽取血缘 action 到独立的 action class(s)
- [ ] `NopMetaLineageEdgeBizModel` 保留 CRUD override + 极少量 dispatch
- [ ] 拆分后所有 action 的 `@BizMutation`/`@BizQuery` 注解参数签名不变
- [ ] 验证 `./mvnw compile -pl nop-metadata -am` 通过
- [ ] 验证 `./mvnw test -pl nop-metadata -am` 通过

Exit Criteria:

- [ ] `NopMetaLineageEdgeBizModel.java` ≤ 500 行
- [ ] 所有血缘 action（`getUpstream`/`getDownstream`/`getImpactAnalysis`/`getPath`/`extractMeasureLineage`/`recordLineage`）在 GraphQL schema 中仍然可用
- [ ] 0 编译错误
- [ ] 706 测试基线保持通过
- [ ] **端到端验证**：TestNopMetaLineageEdgeBizModel 测试通过
- [ ] **接线验证**：lineage action 的实际执行类被正确 dispatch（BizModel 或 action class 内方法连通）
- [ ] **无静默跳过**：无空方法体/continue/吞异常
- [ ] `No owner-doc update required`: 纯内部重构
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 三个目标文件均 ≤ 800（AggregationContext）/ ≤ 500（BizModels）行
- [ ] 0 编译错误
- [ ] 706 tests baseline pass（或 ≥ baseline）
- [ ] GraphQL schema 无变化（所有 action 名称和签名不变）
- [ ] 设计决策文档 `aggctx-and-bizmodel-split.md` 已创建并记录所有拆分决策
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）拆分后的 action class 在运行时被 BizModel dispatch 调用；（b）无空方法体/静默跳过；（c）端到端测试覆盖拆分后路径
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

### NopMetaSearchBizModel xmeta (B4)

- Classification: `watch-only residual`
- Why Not Blocking Closure: Utility BizModel（3 search methods, no backing entity），当前 `@BizModel("NopMetaSearch")` 注解已暴露 GraphQL schema，xmeta 对于无实体的 utility BizModel 无实际意义。
- Successor Required: no

### NopMetadataErrors.java 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 1001 行文件包含全部 ErrorCode 定义，拆分需约定子域命名规范并修改大量 import，单次批量变更风险高。当前所有 ErrorCode 集中可检索。
- Successor Required: no

## Non-Blocking Follow-ups

- `NopMetadataException` tier-2 使用推广（09-02, P3）
- Flat BizModel 目录治理（39 文件在同目录，optimization candidate）

## Closure

Status Note: （预留，closure audit 时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （预留）
- Evidence: （预留）

Follow-up:

- no remaining plan-owned work
