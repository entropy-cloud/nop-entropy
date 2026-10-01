# Doris Nereids SQL 优化引擎独立性分析

> Status: resolved
> Date: 2026-09-30
> Scope: 外部仓库 Apache Doris（`~/sources/doris`，commit `b41856ab7e9`）的 FE 侧 SQL 优化引擎 Nereids，评估"能否独立出来"
> Conclusion: 仓库内模块化（口径 A）可行且 ROI 高；抽成跨引擎复用的优化器库（口径 B）仅核心算法骨架可移植（约 15-20% 代码量），语义层不可移植；完整独立产品（口径 C）不可行

## Context

- 决策点：Doris 的 SQL 优化引擎（Nereids）能否从 `fe-core` 巨石模块中独立出来，以及独立到什么程度。
- 涉及范围：仅做只读调研（外部仓库），不改动 Doris 代码。本地路径 `~/sources/doris`（2.1G，Apache-2.0）。
- 需要区分三种不同口径的"独立"，避免结论混淆：
  - **口径 A**：Doris 仓库内的 Maven 模块拆分（边界固化 + 编译隔离）。
  - **口径 B**：抽成可被**其他执行引擎**复用的优化器库。
  - **口径 C**：可独立运行的完整 SQL 优化产品（含解析、绑定、优化、物理化）。
- 分析方法：以 `import` 语句为耦合度量（distinct 被引用类数 = 耦合面），按 Nereids 子包分桶统计 LOC / 文件数 / 外部依赖数，再对高耦合桶做逐文件归因。

## Analysis

### 1. 分析对象定位

- 唯一优化器：`fe/fe-core/src/main/java/org/apache/doris/nereids/`，**2,678 文件 / 411,516 LOC**，占 fe-core（4,511 个 main Java 文件）的 59%。
- `nereids/README`：parser/expressions 借鉴 Spark，optimizer 借鉴 NoisePage。
- **Calcite 已移出代码**：全 `fe/` 下 `calcite` 引用数 = 0，仅 `fe/pom.xml:358` 残留 `<calcite.version>1.33.0</calcite.version>` 属性。
  - 旧的 `org.apache.doris.planner/`（75 文件）现在是**物理执行计划节点**（`PlanFragment`/`ScanNode`/`DataSink`），不是优化器。
  - 旧的 `org.apache.doris.analysis/`（76 文件）是被 Nereids 复用的旧 Expr AST（`fe-catalog` 模块内也有同名包）。
- 流水线（`nereids/NereidsPlanner.java:219` `planWithoutLock`）：
  `analyze` → `rewrite`（RBO）→ `optimize`（memo/Cascades）→ `physicalPlan` → `distribute`（分片/实例分配）→ `PhysicalPlanTranslator`（落地 `PlanFragment`）
- 规模资产：495 个 `RuleType`（`rules/RuleType.java`）、893 个函数类、658 个 nereids 单测（171k LOC）、regression-test `nereids_p0` 等套件。

### 2. 分层耦合量化（核心证据）

distinct = 该桶内文件 import 的 `org.apache.doris.*`（**排除 nereids 自身**）的去重类数。

| 层 | LOC | 文件数 | distinct 外部依赖 | 说明 |
|---|---|---|---|---|
| `rules/` 规则库 | 92,463 | 477 | 174 | 仅 **80/477** 个文件直接 import Doris 元数据 |
| `trees/expressions/functions/` 函数库 | 85,404 | 893 | 61 | 绑 `catalog.FunctionSignature` |
| `trees/plans/commands/` **语句执行（非优化器）** | 74,780 | 506 | **555** | 耦合离群值 |
| `trees/plans/{logical,physical,visitor,algebra}` 计划树 | 31,910 | — | 85 | |
| **`{memo,pattern,properties,cost,jobs}` Cascades 引擎核心** | **28,959** | **180** | **31** | 耦合最低 |
| `trees/expressions`（不含 functions）表达式树 | 23,668 | — | 34 | |
| `{parser,analyzer}` | 14,968 | 37 | 90 | grammar 已在独立模块 |
| `stats/` 代价与统计 | 7,486 | 15 | 64 | |
| `glue/` 翻译到 Doris 执行计划 | 6,260 | 5 | 138 | `PhysicalPlanTranslator.java` 4,097 行 / 50 个 visit |
| `trees/plans/distribute/` 分片并行化 | 5,613 | 44 | 63 | 直接构造 BE worker、`TScanRange` |
| 其余支撑（types/util/processor/hint/lineage/load 等） | 40,005 | — | 207 | |
| root（`NereidsPlanner`/`CascadesContext`/`StatementContext`） | 4,803 | 10 | 74 | |
| **全量 nereids** | **411,516** | **2,678** | **846** | |

累计依赖面：

- 引擎 + IR + rules + parser + stats + functions = **455** 个外部类
- 再加 `glue` + `distribute` = **538** 个外部类
- 加上 `commands` = **846** 个外部类

### 3. 关键发现：耦合是"集中"的，不是"弥散"的

这是本次分析最重要的结论——**绝大部分外部依赖来自两个与"优化"无关的桶**：

1. **`trees/plans/commands/`（74.8k LOC）是语句执行层，不是优化器**：
   - 555 个外部依赖（全 nereids 的 66%）；
   - `Env.*` 引用 683 处、`ConnectContext.get()` 452 处；
   - `mysql.privilege.PrivPredicate` **245 处全部在 commands 内**（规则库内仅 2 处）→ **权限检查几乎不在优化器里**；
   - `qe.StmtExecutor` 引用 331 处在 commands，优化器内仅 `util/Utils`、`stats/SimpleAggCacheMgr`、`lineage/LineageUtils` 各 1 处 + `distribute` 1 处。
2. **`glue/` + `distribute/`（11.9k LOC）是"落地到 Doris BE"层**，必然不可移植。
3. **真正的优化器核心**（引擎 29k + IR 55.6k + 规则 92k + 函数 85k + parser/stats）外部依赖 455 个类，其中引擎核心只有 **31 个**。

`Env` / `ConnectContext.get()` 按桶分布（正则 `\bEnv\.getCurrent|\bEnv\.get[A-Z]` 与 `ConnectContext\.get\(\)`）：

| 桶 | Env 引用 | ConnectContext.get() |
|---|---|---|
| commands / load / lineage | 683 | 452 |
| 优化核心 + rules + IR | **59** | **299** |
| functions | 16 | 18 |
| glue + distribute | 14 | 16 |

→ 优化核心内 `Env` 静态单例只有 59 处（可收敛为 SPI），但 `ConnectContext` 299 处（会话变量读取到处都是）是更顽固的耦合。

### 4. 五处硬绑定（含 file:line 证据）

1. **全局单例元数据**：`Env.getCurrentEnv()` 共 772 处（nereids 内）。优化路径 59 处集中在 `stats/StatsCalculator.java`(6)、`LogicalCatalogRelation.java`(4)、`PhysicalCatalogRelation.java`(4)、`rules/rewrite/CollectPredicateOnScan.java`(4) 等。同时 `OlapTable`/`Column` 等**具体类**直连（`catalog.OlapTable` 在 94 处 import 中出现），接口 `TableIf`/`DatabaseIf`/`CatalogIf` 也都在 fe-core 内，未下沉。
2. **统计与代价无 SPI**：
   - `nereids/stats/StatsCalculator.java:1197` → `Env.getCurrentEnv().getStatisticsCache().getColumnStatistics(...)`
   - `nereids/cost/CostModel.java:24/70/111` 直接读 `catalog.OlapTable`、`qe.SessionVariable`、`Env.getCurrentEnv().getHboPlanStatisticsManager()`
   - `cost/` 仅 5 个文件、988 LOC，`CostModel.java` 单文件 33KB —— 代价模型是硬编码，不是可注入实现。
3. **函数库绑定**：893 个函数类中 **806 个** import `org.apache.doris.catalog.FunctionSignature`（该类在 fe-core，已是 nereids-aware：`FunctionSignature.ret(DoubleType.INSTANCE)`）；另有 `catalog.FunctionRegistry`、`BuiltinAggregateFunctions`、22 个 `tablefunction.*`。函数名 ↔ BE 内建实现的语义无法剥离。
4. **会话状态**：优化核心内 `ConnectContext.get()` **299 处**；`StatementContext.java` 是 400+ 行胖上下文（MVCC 快照、表锁、MTMV 缓存、`ScanNode` 列表、`relationIdToStatisticsMap`、SqlCache 等）。
5. **物理化后端不可替换**：
   - `glue/translator/PhysicalPlanTranslator.java` 4,097 行、50 个 `visitPhysical*` → `PlanFragment`/`ScanNode`/`DataSink`；
   - `trees/plans/distribute/DistributePlanner.java` + `worker/*` → BE 实例分配、`TScanRange`、`TRuntimeFilterType` 等 thrift；
   - `trees/plans/physical/RuntimeFilter.java`、`TopnFilter.java` 直接持有 `planner.RuntimeFilterId` 与 thrift 枚举。

### 5. 仓库已有的解耦先例（说明方向正确）

- `fe/pom.xml:229-246` 已拆出 18 个模块：`fe-foundation`、`fe-common`、`fe-catalog`、`fe-type`、`fe-sql-parser`、`fe-connector-*`、`fe-authorization-spi`、`fe-authentication-spi` 等。
- **`7fc8e276284 [feat](sql-parser) Split SQL grammar into standalone fe-sql-parser (#63823)`**：把 antlr grammar（`DorisLexer.g4`/`DorisParser.g4`）+ 解析异常 + `DorisSqlParserCli` 独立成模块，是可直接套用的手法。
- `906b706c579 [feat](authorization) introduce an authorization plugin SPI and move the Ranger sources out of fe-core (#66770)`：先定 SPI 再搬代码的先例。
- `fe-connector/fe-connector-spi` 是已经存在的元数据 SPI（`connector.spi.ConnectorMetadata`/`ConnectorWritePlanProvider` 等，被 nereids 的 IR 直接引用）。
- **依赖方向已经单向**：fe-core 依赖 fe-sql-parser/fe-catalog/fe-type/fe-connector-spi/fe-authorization-spi…，几乎没有模块反向依赖 fe-core → "再往下切一刀"是顺水推舟。

### 6. 三条口径的评估

#### 口径 A：仓库内拆成 `fe-nereids` 模块 —— 可行，ROI 高

- 可切分层：`fe-nereids-ir`（trees/memo/pattern/properties/cost，引擎 31 依赖）→ `fe-nereids-rules` → `fe-nereids-glue-doris`（glue+distribute）。
- 前置条件：先抽 4 个 SPI（见 §7），否则 `fe-nereids` 仍要依赖 fe-core 的 `catalog.Env`/`qe.ConnectContext`，模块化只是形式。
- 验证资产充足：658 个 UT（仅 67 个需要完整启动 FE/Env）+ regression-test。测试入口 `PlanChecker.from(ConnectContext)` 可直接沿用。

#### 口径 B：抽成跨引擎可复用的优化器库 —— 仅骨架可移植，不推荐全抽

- **能带走的**：引擎核心（29k LOC / 31 依赖）、表达式与计划 IR（55.6k / ~100 依赖）、规则框架（495 RuleType、pattern matching、property derivation/enforcement）、parser（grammar 已独立）。
- **带不走的**：
  - 85k 函数库（`FunctionSignature`/`FunctionRegistry`/BE 语义）；
  - Doris 方言规则 —— 索引选择、分桶裁剪、MTMV/IVM 改写（`mtmv` 包被 18 个规则文件引用）、runtime filter、外裁剪；
  - 统计来源（`Env.getStatisticsCache()`，实际是对内部表跑 SQL）与代价参数（`SessionVariable` 里 30+ 个调优开关）；
  - 全部物理化（glue + distribute）。
- **量化**：可移植部分约占 nereids 代码量的 15-20%（引擎 29k + IR 55.6k + 规则框架中通用部分 ≈ 70-85k / 411k）。
- 换引擎的真实成本 ≈ 重写绑定层 + 重写规则 + 重写函数语义 = **重新实现一个优化器**。
- **若目标是给另一个引擎加 SQL 优化能力，直接用 Calcite 更划算**；Nereids 的价值在经验与规则设计，不在可移植性。

#### 口径 C：完整独立产品 —— 不可行

`distribute` + `glue` + `catalog.Env` 共同决定了它只为 Doris 的 BE 服务（tablet 分桶、实例分配、runtime filter 下推都是 BE 语义）。

### 7. 若推进的四步路线与 SPI 清单

1. **搬移 `trees/plans/commands/`（74,780 行 / 506 文件）出 nereids 包** → 纯搬迁、零语义变更，一次性消掉 555 个外部依赖中的绝大部分，让 `nereids` 包名真正等于"优化器"。
2. **抽 4 个 SPI**（先在 nereids 内部改为接口调用，实现留在 fe-core）：
   | SPI | 替代对象 | 证据锚点 |
   |---|---|---|
   | `MetadataProvider` | `Env` + `OlapTable`/`Column`/`TableIf` | 优化路径 59 处 Env、IR 32 个 catalog 依赖 |
   | `StatisticsProvider` | `Env.getStatisticsCache()` | `StatsCalculator.java:1197` |
   | `SessionConfig` | `ConnectContext`/`SessionVariable` | 优化路径 299 处 `ConnectContext.get()` |
   | `CostModel`（可注入实现） | 硬编码 `CostModel.java` | `CostModel.java:24/70/111` |
   - 可复用已有 `connector.spi` 作为 `MetadataProvider` 的设计参考。
3. **切出后端**：`glue/` + `trees/plans/distribute/` → `fe-nereids-doris-backend`，把 `planWithoutLock` 的输出边界（optimized `PhysicalPlan`）固化为 `PhysicalPlanSink` SPI。
4. **生成 `fe-nereids` 模块**，CI 加依赖方向检查（禁止 `fe-nereids` 引用 `org.apache.doris.planner/qe/thrift/mysql`），沿用 `#63823` 的拆分手法。

## Conclusion

- **口径 A（仓库内模块化）可行且值得做**：耦合集中、依赖方向已单向、已有 `fe-sql-parser`/`fe-authorization-spi` 两个成功先例、测试资产充足（658 UT + regression-test）。
- **口径 B（跨引擎复用库）不推荐整体做**：只有"优化算法骨架"可移植（约 15-20% 代码量）；85k 函数库、Doris 方言规则、统计来源、物理化全部不可移植。
- **口径 C（完整独立产品）不可行**：优化结果必须翻译为 `PlanFragment`/`TScanRange` 才能被 BE 消费。
- **被否决的方案**：
  - "整体搬迁 nereids 出 fe-core" —— 否决理由：`commands`(555 依赖) + `glue/distribute`(138+63 依赖) 占了外部依赖面的多数，不先做切分的搬迁等于把巨石换个位置。
  - "改用 Calcite 作为优化核心" —— 否决理由：Doris 已主动移除 Calcite（代码中 0 引用），回退等于放弃多年积累的 495 条规则与 Cascades 实现。
  - "抽成跨引擎优化器库复用" —— 否决理由：可移植比例过低，重写成本 ≈ 重新实现优化器；外部需求若存在，Calcite 是更经济的选择。
- **后续工作**：暂无对应 plan/design。若推进口径 A，需先起草 `ai-dev/plans/`（第一步应是 §7-1 的 commands 搬移清单，属纯机械改动，可独立验收）。

## Open Questions

- [ ] 真实诉求是什么——是"给 Nop 平台/其他引擎加 SQL 优化能力"，还是"Doris 社区式模块治理"？两者最优解不同（前者倾向 Calcite，后者倾向 §7 路线）。
- [ ] `jobs/` 包内混入了 `jobs/load/LabelProcessor`（依赖 `org.apache.doris.job.*`）等非 Cascades 代码，是否说明 `jobs` 命名已失真、需一并重新归位？
- [ ] `StatementContext` 的胖上下文（MVCC/表锁/MTMV 缓存/ScanNode）能否拆成 `OptimizationContext` + `ExecutionSession` 两层？未做逐字段归因。
- [ ] 函数库（85k LOC）若要独立，`FunctionSignature` 需要先下沉到 `fe-type` 或新模块——这一步的级联影响未评估。
- [ ] 455/538/846 这些 distinct 依赖数中，有多少属于"只在 explain/print 路径"的弱耦合（未做调用链级区分，仅按 import 计数，可能高估）。

## References

### 被分析代码（外部仓库 `~/sources/doris`，commit `b41856ab7e9`）

- `fe/fe-core/src/main/java/org/apache/doris/nereids/` —— 优化引擎本体
- `fe/fe-core/src/main/java/org/apache/doris/nereids/NereidsPlanner.java:219` —— 流水线入口
- `fe/fe-core/src/main/java/org/apache/doris/nereids/stats/StatsCalculator.java:1197` —— 统计读取的全局单例耦合
- `fe/fe-core/src/main/java/org/apache/doris/nereids/cost/CostModel.java:24,70,111` —— 代价模型硬编码耦合
- `fe/fe-core/src/main/java/org/apache/doris/nereids/glue/translator/PhysicalPlanTranslator.java`（4,097 行）—— 物理化后端
- `fe/fe-core/src/main/java/org/apache/doris/nereids/rules/RuleType.java` —— 495 个规则类型
- `fe/pom.xml:229-246, 358` —— 模块清单与残留的 calcite 版本属性
- `fe/fe-core/pom.xml` —— fe-core 依赖的下游模块清单
- 提交 `7fc8e276284`（#63823 拆分 fe-sql-parser）、`906b706c579`（#66770 authorization SPI）

### 本仓库相关文档

- `ai-dev/analysis/00-analysis-writing-guide.md` —— 本文件遵循的写作规范

### 方法复现

```bash
# 分层 LOC / distinct 外部依赖：按子包收集 import 后去重（排除 org.apache.doris.nereids 前缀）
cd ~/sources/doris/fe/fe-core/src/main/java/org/apache/doris/nereids
grep -rh "^import " <dir> --include="*.java" | grep -v "org\.apache\.doris\.nereids" \
  | grep "org\.apache\.doris\." | sed -E 's/^import (static )?//; s/;$//' | sort -u | wc -l
# Env / ConnectContext 调用密度
grep -rho "Env\.getCurrent\|Env\.get[A-Z]" <dir> --include="*.java" | wc -l
grep -rho "ConnectContext\.get()" <dir> --include="*.java" | wc -l
# Calcite 残留
grep -rl "calcite" --include="*.java" fe | wc -l   # 0
```
