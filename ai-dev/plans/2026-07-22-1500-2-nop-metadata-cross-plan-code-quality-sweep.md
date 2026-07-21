# 2026-07-22-1500-2 nop-metadata Cross-Plan Code Quality Refinement

> Plan Status: active
> Last Reviewed: 2026-07-22
> Source: 跨计划 deferred 优化项收口——`2026-07-21-1200-2-nop-metadata-code-quality-and-docs.md`（NopMetadataException tier-2 推广 09-02、ErrorCode 一致 09-06、@SuppressWarnings 09-07）、`2026-07-22-0900-2-nop-metadata-aggregation-context-bizmodel-split.md` Non-Blocking Follow-ups、`2026-07-22-0900-1-nop-metadata-baseline-finalization.md` Non-Blocking Follow-ups、`04-nop-metadata-aggregation-processor-split.md` Non-Blocking Follow-ups
> Related: `2026-07-22-1500-1-nop-metadata-semantic-layer-phase4-propagation-impl.md`（Semantic Layer Phase 4 实现，本 plan 的执行依赖不相关）
> Mission: nop-metadata
> Work Item: 跨计划代码质量和异常处理优化

## Purpose

收口多个已完成 nop-metadata plan 中累积的 deferred 优化项。这些项分散在 4+ 个 plan 的 `Deferred But Adjudicated` 和 `Non-Blocking Follow-ups` 中，均为 `optimization candidate`。本 plan 将它们纳入统一 scope 执行，减少基底技术债务。

## Current Baseline

- `NopMetadataErrors.java` 1040 行，集中所有 nop-metadata ErrorCode 定义（~201 条 `define` 调用）
- `NopMetadataException`（tier-2 异常类）已定义。服务层 ~63 个文件使用 `new NopException(NopMetadataErrors.ERR_...)`（共 ~322 抛点）而非 `new NopMetadataException(NopMetadataErrors.ERR_...)`。涉及子包：query/、quality/、reconciliation/、datasource/、contract/、connection/、manifest/、catalog/、field/、lineage/、profiling/、search/、sync/、tableref/ 等。仅 `MetaManifestBuilder.java` 已使用 `NopMetadataException`。无统一自动检测机制。
- ErrorCode 变量名（如 `ERR_AGGR_JOIN_SELF_JOIN`）与字符串值（如 `"nop.err.metadata.aggr-join-self-join-unsupported"`，含多余 `-unsupported` 后缀）之间存在 ~20 处已知不一致。主要模式：
  - 多余后缀：`-deferred`（2处）、`-unsupported`（1处）、`-failed`（1处）
  - 子域不匹配：变量 `ERR_FILTER_*` 但字符串 `query-filter-*`（6处）、`ERR_SQL_VIEW_*` 但字符串 `sql-*`（丢失 `view`，6处）
  - 语义不匹配：`no` vs `not-found`（1处）、`GRANULARITY` 字符串缺 `aggr-` 子域前缀（1处）
- `@SuppressWarnings("unchecked")` 在 service 模块 ~41 处（含 `{"unchecked", "rawtypes"}` 复合标记），高于历次 plan 协定的目标阈值。但 ~16 处位于 Map/JSON 数据访问路径（BizModel 中的 GraphQL 响应、`MetaJoinExecutor` 等），属类型安全边界——不可消除而不改变 public contract；~25 处可通过重构消除。
- `CrossDbJoinMerger` 硬编码 `this.maxCrossDbRows = 10000`（构造函数 `this(10000)`）。`MetaJoinExecutor` 有独立硬编码 `static final int MAX_CROSS_DB_ROWS = 10000`，用于控制跨库拉取上限（line 430/462）。两者通过 `MetaJoinExecutor` line 83 `new CrossDbJoinMerger()` 实例化（非 IoC 管理）。
- 测试 baseline 待执行前确认。

## Goals

- 服务层 NopMetadataException 推广：将 service 子包下（含 query/、quality/、reconciliation/、datasource/、contract/、connection/、manifest/、catalog/、field/、lineage/、profiling/、search/、sync/、tableref/ 等）所有 `new NopException(NopMetadataErrors.ERR_...)` 替换为 `new NopMetadataException(NopMetadataErrors.ERR_...)`。统一后 service 子包零 `new NopException(NopMetadataErrors.ERR_` 模式。
- ErrorCode 变量名与字符串值系统性对齐：修复 ~20 处不一致（多余后缀、子域不匹配、语义不匹配）。使用脚本辅助验证。
- `@SuppressWarnings("unchecked")` 数量降至 ≤25（保留 ~16 处类型安全边界，消除 ~25 处可消除的）。
- 为 `CrossDbJoinMerger` 和 `MetaJoinExecutor.MAX_CROSS_DB_ROWS` 引入外部管理，统一从静态配置持有者读取。

## Non-Goals

- 不在本 plan 中拆分 NopMetadataErrors.java（1000+ 行，拆分需 subdomain 命名约定和跨 ~30 文件的 import 变更，视为独立的 successors）
- 不做 Flat BizModel 目录治理（39 文件单目录，功能透明）
- 不做 AutoTest snapshot 覆盖扩展
- 不修改 nop-metadata-api 或 nop-metadata-dao 模块（仅限 service 模块）
- 不修改 NopMetadataException 类本身的签名
- 不修改测试中的 `new NopException(ErrorCode)` 模式（`assertThrows(NopException.class, ...)` 匹配子类 `NopMetadataException`，功能正确）

## Scope

### In Scope

- Phase 1: NopMetadataException tier-2 推广覆盖 service 所有子包（63+ 文件，~322 抛点）
- Phase 2: ErrorCode 变量名一致性治理（~20 处）+ @SuppressWarnings 缩减（41→≤25）
- Phase 3: CrossDbJoinMerger.maxCrossDbRows + MetaJoinExecutor.MAX_CROSS_DB_ROWS 配置化（通过静态配置持有者 `CrossDbConfigHolder`）

### Out Of Scope

- NopMetadataErrors.java 拆分
- Flat BizModel 目录治理
- 模块重命名（`nop-metadata-core`）
- 测试文件中 `new NopException(ErrorCode)` 的使用

## Execution Plan

### Phase 1 — NopMetadataException tier-2 推广（全 service 子包）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/`（含全部子包：query/、quality/、reconciliation/、datasource/、contract/、connection/、manifest/、catalog/、field/、lineage/、profiling/、search/、sync/、tableref/ 等）

- Item Types: `Fix`

- [ ] 搜索 service 子包下所有 `new NopException(NopMetadataErrors.ERR_` 模式（~63 文件，~322 抛点）
- [ ] 逐一替换为 `new NopMetadataException(NopMetadataErrors.ERR_...)`，同时更新 import：
  - 移除 `import io.nop.api.core.exceptions.NopException;`（如果该文件不再使用 catch(NopException)）
  - 添加 `import io.nop.metadata.service.NopMetadataException;`
- [ ] 注意：`catch (NopException e)` 保持不变（13 处跨~7 文件）——这些块捕获而非抛异常，NopMetadataException 继承自 NopException，catch 语义不变。受影响文件（MetaJoinExecutor、ExternalAggregationProcessor、MetaAggregationExecutor、AggregationHelper、MetaTableFieldResolver、MetaContractChecker 等）需要同时保留双 import。
- [ ] 修复测试文件中因 import 变更引发的编译错误

Exit Criteria:

- [ ] `grep -r "new NopException(NopMetadataErrors\.ERR_" --include="*.java" nop-metadata/nop-metadata-service/src/ | wc -l` = 0
- [ ] 13 处 `catch (NopException e)` 全部保留未修改
- [ ] `./mvnw compile -pl nop-metadata/nop-metadata-service -am` 通过
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过（零 regression）
- [ ] No owner-doc update required（纯内部重构）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — ErrorCode 变量名一致性 + @SuppressWarnings 缩减

Status: planned
Targets: `NopMetadataErrors.java` + service 模块各文件

- Item Types: `Fix`

- [ ] 系统比对 `NopMetadataErrors.java` 中每条 `static final ErrorCode ERR_* = define("nop.err.metadata....")`：
  - 提取变量名最后一段（`ERR_A_B_C` → `A_B_C`）和字符串最后一段（`a-b-c`）
  - 蛇形→连字符转换后对比：`A_B_C` → `a-b-c` vs 实际字符串最后一段
  - 标记不一致处（~20 处，比照 Current Baseline 中的七大模式）
  - 修复：调整字符串值以匹配变量名语义（优先改字符串，不改 ERR_ 常量名以保持 import 兼容）
- [ ] 如变量名本身含语义错误（如拼写），可双改（变量名 + 字符串值），但必须同步更新所有引用该 ErrorCode 的代码
- [ ] 搜索 `@SuppressWarnings("unchecked")`（含复合 `{"unchecked", "rawtypes"}` 等），逐处评估：
  - 可消除：引入类型化包装器、中间类型化变量、提取方法返回具体类型（~25 处）
  - 不可消除：Map/JSON 数据访问、反序列化边界（~16 处）
- [ ] 保留不可消除处，消除可消除处，总数降至 ≤25

Exit Criteria:

- [ ] 使用以下辅助脚本验证不一致零残留（在 `nop-metadata-service` 目录执行）：
  ```
  awk -F'["= ]+' '/ERR_.*define\("nop\.err\.metadata\./{var=$2; str=$4; gsub(/^ERR_/, "", var); gsub(/^nop\.err\.metadata\./, "", str); gsub(/_/, "-", var); if(var!=str && var != str "_failed" && var != str "_deferred" && str != var "_unsupported") print var, "vs", str}' NopMetadataErrors.java
  ```
  （注意：少数合理不一致如 `_deferred`/`_failed`/`_unsupported` 后缀符合工程语义，手动审查后可作为白名单）
- [ ] `grep -r "@SuppressWarnings.*unchecked" --include="*.java" nop-metadata/nop-metadata-service/src/ | wc -l` ≤ 25
- [ ] `./mvnw compile -pl nop-metadata/nop-metadata-service -am` 通过
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — CrossDbJoinMerger + MetaJoinExecutor 最大行数配置化

Status: planned
Targets: `CrossDbJoinMerger.java` + `MetaJoinExecutor.java`（均在 `service/query/` 下）

- Item Types: `Fix`

- [ ] 创建 `CrossDbConfigHolder` 类（包级 static config holder，可选标记 `@IocBean`）：
  - `public static int maxCrossDbRows = 10000;`
  - 若 `MetaJoinExecutor` 为 IoC 管理的 bean，则通过 `@InjectValue("nop-metadata.aggregation.cross-db-max-rows")` 注入并赋值
- [ ] `CrossDbJoinMerger`：当前已有 `CrossDbJoinMerger(int maxCrossDbRows)` 构造函数（line 38）；no-arg 构造函数 `this(10000)` 改为 `this(CrossDbConfigHolder.maxCrossDbRows)`
- [ ] `MetaJoinExecutor`：
  - 移除 `static final int MAX_CROSS_DB_ROWS = 10000`
  - line 430 `q.setLimit(MAX_CROSS_DB_ROWS + 1)` → `q.setLimit(CrossDbConfigHolder.maxCrossDbRows + 1)`
  - line 462 `Long fetchLimit = (long) MAX_CROSS_DB_ROWS + 1` → `(long) CrossDbConfigHolder.maxCrossDbRows + 1`
  - line 83 `this.crossDbMerger = new CrossDbJoinMerger()` → `this.crossDbMerger = new CrossDbJoinMerger(CrossDbConfigHolder.maxCrossDbRows)`

Exit Criteria:

- [ ] `CrossDbJoinMerger` no-arg 构造函数使用 `CrossDbConfigHolder`（非字面量 `10000`）
- [ ] `MetaJoinExecutor` 无 `MAX_CROSS_DB_ROWS` 常量或字面量 `10000` 的行限制使用（除配置默认值 fallback）
- [ ] `CrossDbConfigHolder.maxCrossDbRows` 默认值 = 10000，零行为变更
- [ ] `./mvnw compile -pl nop-metadata/nop-metadata-service -am` 通过
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] Phase 1：service 子包 `new NopException(NopMetadataErrors.ERR_` 模式零残留；13 处 `catch (NopException e)` 未误改
- [ ] Phase 2：ErrorCode 变量名与字符串值不一致零残留（脚本辅助验证）+ @SuppressWarnings("unchecked") ≤ 25
- [ ] Phase 3：CrossDbJoinMerger + MetaJoinExecutor 硬编码阈值已配置化
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am` 通过
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] No owner-doc update required（全部为内部重构）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：本 plan 仅重构既有代码的异常类型、变量名和配置方式，不引入新组件——端到端路径不变
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### NopMetadataErrors.java 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 1040 行单文件管理所有 ErrorCode。拆分需要 subdomain 命名约定和跨 ~30 文件 import 变更，独立于一次性替换工作。已在 2026-07-21-1200-2 和 0900-2 中明确裁定为 non-blocking。本 plan Phase 2 仅做变量名一致性修复，引入拆分。
- Successor Required: no

### @SuppressWarnings("unchecked") 边界保留

- Classification: `watch-only residual`
- Why Not Blocking Closure: ~16 处位于 Map/JSON 数据访问路径（GraphQL response 解析、MetaJoinExecutor 通用 Map 强转、BizModel 入参反序列化），属 Java 类型系统已知边界，不可消除而不改变 contract。目标 ≤25 已为此留有裕量。
- Successor Required: no

## Non-Blocking Follow-ups

- Flat BizModel 目录治理（39 文件同一目录）
- AutoTest snapshot 覆盖扩展到 BizModel CRUD 路径
- NopMetadataErrors.java 拆分
- `NopMetadataException` 使用 checkstyle 规则防退化

## Closure

Status Note: （完成时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- Flat BizModel 目录治理
- NopMetadataErrors.java 拆分
