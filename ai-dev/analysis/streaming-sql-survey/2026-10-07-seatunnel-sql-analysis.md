# SeaTunnel SQL Transform 能力分析
> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能参考调研（streaming-sql-survey 子项）
> Conclusion: 最值得借鉴的两点——(1) `SQLEngine` SPI 把"SQL 方言求值器"做成可插拔 seam，Zeta(解释器)与 Calcite(编译一次 Bindable、按行复用)双引擎共存给出低成本→高保真的演进路径；(2) `typeMapping` 在 open 时从 SQL 静态推导输出 schema 并保留输入列元数据映射，schema 变更时失效重建引擎。次优先：UDF SPI（ServiceLoader + open/close 生命周期 + rowContext）与行级错误分类（ROW_ERROR 走脏数据通道）。

## Context
- Apache SeaTunnel 是数据集成平台（ELT：source → transform → sink），其 SQL 能力是 **transform 算子内的"单行表达式求值器"**，不是完整流 SQL 引擎；列入本调研是作为"SQL 嵌入配置/算子"形态与函数集的参照。
- 本地源码：`/Users/abc/sources/seatunnel`（3.0.0-SNAPSHOT，dev @ a1084015，2026-10-01）。重点模块：`seatunnel-transforms-v2` 下的 `transform/sql`（Zeta 引擎）与 `transform/calcite`（Calcite 引擎），以及 `seatunnel-e2e/seatunnel-transforms-v2-e2e` 的 SQL 用例。

## 项目概览
- 定位：海量数据集成平台，主打 connector 生态与批流一体 pipeline；transform 层轻量（filter/copy/rename/jsonpath/sql 等约 25 个）。
- 语言/许可证：Java，Apache-2.0。
- 演进状态：活跃（2026-10 仍有功能性提交）；SQL transform 近期发生了 **Zeta → Calcite 的双引擎并存**（`Sql` 插件默认 ZETA，另有独立 `Calcite` 插件）。
- SQL 能力入口模块：`seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/`（`sql/`、`calcite/`、`common/`、`table/`）。

## 十维度分析

### 1. SQL 前端
- **Zeta 引擎（默认，插件名 `Sql`）**：parser 是 **JSqlParser**（`CCJSqlParserUtil.parse`，非 Calcite），见 `ZetaSQLEngine.parseSQL`（sql/zeta/ZetaSQLEngine.java:145-156）。`validateSQL`（同文件 158-212 行）白名单式裁剪：仅允许单表 `PlainSelect`，显式拒绝 JOIN、GROUP BY、ORDER BY、LIMIT/OFFSET、子查询（"Unsupported sub table syntax"）、schema 前缀、表别名。支持的子集 = `SELECT 投影（*/表达式/别名） + WHERE 过滤 + LATERAL VIEW EXPLODE`。
- **Calcite 引擎（插件名 `Calcite`）**：完整 Calcite planner 流水线 parse → validate → `planner.rel` → `Programs.standard()` 优化到 `EnumerableConvention` → `EnumerableInterpretable.toBindable` 编译为可复用 `Bindable`（calcite/engine/CalciteSQLEngine.java:89-157）。SQL 子集由 Calcite 决定（含 UNNEST 产生 0-N 行，同文件 161 行注释），但 input 侧只注册**一张当前行表**，无多表 join 语义。
- 依赖：`com.github.jsqlparser:jsqlparser` 与 shaded `seatunnel-shade-calcite`（seatunnel-transforms-v2/pom.xml:65-67、102-106）。

### 2. Table & Changelog 模型
- 数据模型为 `SeaTunnelRow`（字段数组 + `tableId` + `RowKind` + `options`，seatunnel-api/.../SeaTunnelRow.java:32-40）；`RowKind` 支持 INSERT/UPDATE_BEFORE/UPDATE_AFTER/DELETE，CDC 源头产生的 changelog 会流经 transform。
- **两个引擎都保留输入行的 RowKind/tableId/options**（ZetaSQLEngine.java:306-308；CalciteSQLEngine.java:203-205），即 SQL transform 不改变 changelog 语义，只改列。
- schema 声明：源端在 HOCON 里 `schema = { fields { ... } }`（docs/en/transforms/sql.md:133-141）；transform **不声明输出 schema**，由 SQL 的 `typeMapping` 在 open 时静态推导（见维度 9）。输出表名由 `plugin_output` 指定。

### 3. 时间语义
不支持事件时间/watermark/RETRACT 时序语义（如实：集成工具的无状态 transform）。仅提供 `CURRENT_DATE/CURRENT_TIMESTAMP/NOW` 等墙钟系统函数（docs/en/transforms/sql-functions.md:709-741）。

### 4. 窗口与聚合
- **不支持聚合/窗口**：`validateSQL` 直接抛 "Unsupported GROUP BY syntax"（ZetaSQLEngine.java:200-202）。
- 函数集为**行内标量/嵌套类型函数**，按官方文档共 7 大类约 130+ 个（docs/en/transforms/sql-functions.md，全文 1390 行，标题目录已核）：
  - String（§1，ASCII/BIT_LENGTH/CHAR_LENGTH/CONCAT/CONCAT_WS/HEXTORAW/RAWTOHEX/TO_BASE64/FROM_BASE64/INSERT/LOWER/UPPER/LEFT/RIGHT/LOCATE/LPAD/RPAD/LTRIM/RTRIM/TRIM/REGEXP_REPLACE/REGEXP_LIKE/REGEXP_SUBSTR/REPEAT/REPLACE/SPLIT/MURMUR64/SOUNDEX/SPACE/SUBSTRING/TO_CHAR/TRANSLATE）
  - Numeric（§2，ABS/三角函数/ARRAY_MAX/ARRAY_MIN/MOD/CEIL/EXP/FLOOR/LN/LOG/LOG10/PI/POWER/RAND/ROUND/SIGN/TRUNC/TRIM_SCALE）
  - Time and Date（§3，CURRENT_DATE/CURRENT_TIME/CURRENT_TIMESTAMP/DATEADD/DATEDIFF/DATE_TRUNC/EXTRACT/FORMATDATETIME/IS_DATE/PARSEDATETIME/AT TIME ZONE/FROM_UNIXTIME 等）
  - System（§4，CAST/TRY_CAST/COALESCE/IFNULL/NULLIF/MULTI_IF/CASE WHEN/UUID/ARRAY…）
  - 另有 Array/Map/Vector 类（docs 目录后部 + `zeta/functions/` 下 ArrayFunction/MapFunction/VectorFunction 实现）。
- "ARRAY_MAX/ARRAY_MIN" 等是行内数组聚合，不是跨行聚合。
- **Calcite 引擎白得一批函数**：JSON_VALUE 等 JSON 操作直接来自 Calcite 核心算子（e2e `calcite_json.conf:42`），向量距离/归一化/掩码/DES 加解密以 CalciteUdf SPI 补齐（calcite/udf/ 下 13 个实现类，目录已核）。

### 5. Join
- Zeta：显式拒绝 join（"Unsupported table join syntax"，ZetaSQLEngine.java:192-194），无维表 lookup。
- Calcite：planner 虽具备 join 语法能力，但 input 侧只注册当前行一张表（`registerTable` 单表，CalciteSchemaFactory.java:45-66），SQL 引用其他表无数据来源，实际仍是单表语义；e2e 无 join 用例。
- 多表能力 = **路由而非 join**：`AbstractMultiCatalogTransform` 按 `table_match_regex`/`table_transform` 把每个输入 CatalogTable 路由到独立的 SQLTransform 实例，各自独立处理（common/AbstractMultiCatalogTransform.java:67-101；e2e `sql_transform_multi_table.conf:92-101`）；`SQLMultiCatalogFlatMapTransform`/`CalciteMultiCatalogTransform` 分别为 Sql/Calcite 插件做多表入口（sql/SQLMultiCatalogFlatMapTransform.java:34-86；calcite/CalciteMultiCatalogTransform.java:30-52）。另有 `TableMerge` 插件仅改写 tableId 把多表"并"到同一路径（table/TableMergeTransform.java:56-62），等价 UNION 语义，仍非 join。

### 6. CEP/模式匹配
不支持。无 MATCH_RECOGNIZE、无 PATTERN 语法。

### 7. 状态与容错
- transform 本体**无状态、无内部缓冲**：每行进、每行出（Zeta）/每行 0-N 出（Calcite UNNEST、LATERAL VIEW EXPLODE）。checkpoint/恢复由执行引擎（Zeta/Flink/Spark 翻译层）承担，transform 只需可序列化、可重建——`ZetaUDF` 接口显式 `extends Serializable`（ZetaUDF.java:19），引擎实例随算子序列化分发后在 open 阶段重建，这是"表达式引擎可嵌入任意执行器"的前提。
- 与状态的唯一交点是 **schema 容错**：收到 `AlterTableEvent` 后将缓存引擎置空、下一行懒重建（`tryOpen`），并对 `RestoreTableSchemaEvent` 回填 change-after（SQLTransform.java:238-263，注释明确说明是为 CDC failover 后 `select *` 防数组越界）；CalciteTransform 同样 closeEngine 后懒重建（calcite/CalciteTransform.java:166-201）。
- 行级错误分类：`classifyRowError` 区分 ROW_ERROR（表达式/WHERE 求值错，可走脏数据通道）与 SYSTEM_ERROR（SQLTransform.java:125-140）。

### 8. 声明形态与可嵌入性
- 声明形态：HOCON 配置内嵌 SQL 字符串——`transform { Sql { plugin_input = "fake"; plugin_output = "fake1"; query = "select ... from dual where id>0"; engine = "ZETA" } }`（docs/en/transforms/sql.md:48-56；`query`/`engine` 选项定义在 SQLTransform.java:61-68）。Calcite 插件用 `sql` 键（calcite/CalciteTransformConfig.SQL，CalciteTransform.java:59）。
- 引擎解耦：`SQLEngine` 接口（init/typeMapping/transformBySQL，sql/SQLEngine.java:25-37）+ `SQLEngineFactory`（EngineType ZETA/INTERNAL，sql/SQLEngineFactory.java:24-40），上层 `SQLTransform` 不感知方言实现；同一份 HOCON 换 `engine` 值即可换引擎。
- 对 Zeta/Flink/Spark 的解耦：transform 是 SeaTunnel API 层的 `SeaTunnelFlatMapTransform`，由各引擎翻译层落到各自算子，SQL 逻辑本身不 import 引擎类型。
- e2e 证据（`seatunnel-e2e/.../part-2/src/test/resources/`，均存在）：`sql_transform.conf`（全类型字段 round-trip）、`sql_transform/{binary_expression,criteria_filter,case_when,func_string,func_numeric,func_datetime,func_system,func_array,func_split,func_array_max_min,func_multi_if,func_null_return,func_vector,sql_all_columns,inner_query,nested_type,explode_transform*}.conf`、`sql_transform_multi_table.conf`（多表路由）、`calcite_transform/{calcite_select_where,calcite_builtin_functions,calcite_json,calcite_mask_udf,calcite_des_udf}.conf`。测试入口 `TestSQLIT.java` 与 `TestCalciteIT.java`（路径见引用 24/25）；TestSQLIT 中 vector 用例标注 Spark 引擎不支持（:93-97），inner_query/nested_type 标注 Spark/Flink 翻译层对嵌套类型有问题（:112-132）——即 SQL transform 的嵌套类型支持在 Zeta 引擎完整、但**引擎翻译层有缺口**。

### 9. 架构要点
SQL → transform 链路：`SQLTransform(config, catalogTable)` → open() 时 `SQLEngineFactory` 建引擎 → `init`（解析+校验 SQL，加载 UDF）→ `transformTableSchema()` 调 `typeMapping` 静态推导输出 `SeaTunnelRowType` → 每行 `transformRow` = scan(原样字段) → filter → project → 包成新 SeaTunnelRow（ZetaSQLEngine.java:284-347，注释自称 "Physical Query Plan Execution: Scan/Filter/Project"）。

**Zeta vs Calcite 引擎对照**（源码核实）：

| | Zeta（`Sql`，默认） | Calcite（`Calcite`） |
|---|---|---|
| parser | JSqlParser | Calcite SqlParser |
| 执行模型 | 解释求值：每行遍历 AST filter+project | 编译一次 `Bindable`，每行 bind 复用 |
| 类型推导 | 自研 `ZetaSQLType`（含 DECIMAL 精确规则） | Calcite 推导 + `OutputRowTypeDeriver` 映射回 SeaTunnel 类型 |
| 1→N 行 | LATERAL VIEW EXPLODE | UNNEST（planner 原生） |
| 函数扩展 | ZetaUDF SPI（手写 dispatch 进 ZetaSQLFunction） | CalciteUdf SPI（静态 eval 反射注册）+ Calcite 内置算子 |
| e2e 覆盖 | 17 个 conf（TestSQLIT.java:33-133） | 5 个 conf（TestCalciteIT.java:30-66） |

值得记录的 3 个设计：
1. **"行即表"的 Calcite 嵌入法**：`CalciteSchemaFactory.registerTable` 把单表注册为 `SeaTunnelScannableTable`，执行时 `setCurrentRow` + `DataContext.refreshNow`，把当前行伪装成一行表；plan 在 init 编译一次，每行 bind 复用（CalciteSQLEngine.java:92-100、163-186；CalciteSchemaFactory.java:45-66）。这让批式 SQL 编译器变成流上逐行表达式求值器，不需要真表扫描。
2. **typeMapping + inputColumnsMapping**：open 时遍历 SelectItem 推导输出列名/类型（`*` 展开、别名、表达式类型走 `ZetaSQLType.getExpressionType`），并记录"输出列 ← 输入列"映射，`transformTableSchema` 用它把输入列的 length/scale/nullable/comment 元数据透传给输出列，纯表达式列用默认可空（ZetaSQLEngine.java:214-275；SQLTransform.java:167-229）。
3. **UDF SPI 双轨**：Zeta 侧 `ZetaUDF`（functionName/resultType/evaluate(+rowContext)/open/close，ZetaUDF.java:19-74）由 ServiceLoader 加载（ZetaSQLEngine.java:101-106）；Calcite 侧 `CalciteUdf` 的静态 `eval` 方法经 `ScalarFunctionImpl.create` 注册进 schema，向量/加解密/掩码函数即此实现（calcite/udf/BuiltinFunctions.java:42-88）。

已知限制/坑（源码可见）：
- 表名不匹配只 log.warn 不报错（"SQL table name ... is not equal to input table name"，ZetaSQLEngine.java:179-187），SQL 写错表名静默按单行表跑。
- WHERE 过滤掉的行以返回 `null` 表达（transformBySQL 返回 null，ZetaSQLEngine.java:294-297），上层 FlatMap 负责丢弃——"SQL 返回 null = 行被过滤"是接口隐含约定，`SQLEngine` javadoc 未写明。
- `countColumnsSize` 缓存 `allColumnsCount` 依赖输入列数（ZetaSQLEngine.java:349-364），因此 schema 变更必须连引擎一起重建（SQLTransform.java:253-256 注释即为此 bug 的修复说明）。

### 10. 与 nop-stream-sql 的对照与可借鉴点
见下节。

## 对照 nop-stream-sql 已交付子集
基线：nop-stream-sql 是 EQL 语法（与 ORM 共用 parser）→编译为流模型的窄范围流 SQL：SELECT 投影/WHERE/五聚合/GROUP BY/TUMBLE/双流等值 join/UNION ALL/静态维表 lookup/持续 GROUP BY/OVER(ROW_NUMBER,FRAME_SLIDING_AGG)；不支持全局 ORDER BY/LIMIT、非等值 join、DATE/TIMESTAMP/DECIMAL 列、CASE/CAST/IN 子查询、DISTINCT、HAVING/CTE、全局聚合、Flink 方言；无代价优化器。

| 维度 | SeaTunnel SQL transform | nop-stream-sql | 差异/启示 |
|---|---|---|---|
| 定位 | 单行表达式求值器（无跨行算子） | 真流算子（聚合/窗口/join） | 两者正交：SeaTunnel 强在表达式丰富度，nop 强在时间维度 |
| parser | Zeta=JSqlParser / Calcite=Calcite parser | EQL（与 ORM 共用） | SeaTunnel 换引擎连 parser 一起换；nop 单 parser 双编译目标，一致性更好 |
| 标量函数/表达式 | ~130+ 函数；CASE/CAST/TRY_CAST/COALESCE/MULTI_IF/IN/LIKE/IS NULL；DECIMAL 精确算术（ZetaSQLType.java:229 起；单测 SQLDecimalArithmeticTest）；嵌套 struct/map/array 导航 `c_row.c_inner_row.x`（docs/en/transforms/sql.md:99-121） | 无 CASE/CAST，无 DECIMAL/DATE/TIMESTAMP 列 | **最大差距在表达式层**：nop 缺的恰是 SeaTunnel 已证明可"引擎无关"实现的行内求值部分 |
| UDF | ServiceLoader SPI，带 open/close 生命周期 + rowContext（tableId/rowKind） | （调研范围内未交付 UDF SPI） | SPI+生命周期接口可直接借鉴 |
| schema 推导 | typeMapping 静态推导输出 RowType + 列元数据透传 | 内嵌 `<schemas>` 声明 | SeaTunnel "SQL 自描述 schema、元数据回填"比手写 schema 少一份数据源，可评估对 `<sql>` 元素做推导兜底 |
| 配置形态 | HOCON 内嵌 `query` 字符串，多表靠 regex 路由 | XML `<sql>` 元素 | 同构：SQL-as-string 嵌入声明式配置；SeaTunnel 的 table_match_regex 多表路由在 nop 多流场景可参考 |
| changelog | 保留 RowKind 透传 | 以流模型自身表达 | SeaTunnel 证明"表达式 transform 不碰 changelog 语义"是干净边界 |
| 聚合/join/窗口 | 全部拒绝 | 已交付（等值 join/五聚合/TUMBLE/OVER） | 反向：SeaTunnel 把这类需求留给"接 Flink/Spark 引擎跑真 SQL"，nop 自研窄子集的路线选择得到旁证 |

可借鉴点（按优先级）：
1. **SQLEngine SPI + "行即表"编译一次模式**：nop-stream-sql 若要补 CASE/CAST/DECIMAL 表达式，可参照 `SQLEngine{init/typeMapping/transformBySQL}` seam 与 `SeaTunnelScannableTable.setCurrentRow` 套路，把 EQL 标量子集逐步替换/补充为编译一次、按行求值的引擎，而不必引入流引擎。
2. **typeMapping 的输出 schema 静态推导与列元数据透传**：`<sql>` 元素可先 SQL 推导、`<schemas>` 显式声明覆盖，减少双份维护；输入列元数据（nullable/comment）透传规则直接可抄。
3. **schema 变更失效重建**：CDC/上游 schema 演进时置空引擎、`tryOpen` 懒重建，是流 transform 处理 `select *` 的最小实现。

附录：nop-stream-sql 缺失表达式 → SeaTunnel 对应实现定位（供后续 spike 直接对照）：

| EQL 缺失 | SeaTunnel Zeta 实现位置 | 备注 |
|---|---|---|
| CASE WHEN | ZetaSQLFunction.java:404（dispatch）、:436（`executeCaseExpr`） | 纯表达式层，无状态，移植性最好 |
| CAST/TRY_CAST | ZetaSQLFunction.java:413（dispatch）、:703/:716（`executeCastExpr`/`executeTryCastExpr`），实现在 zeta/functions/CastFunction.java | TRY_CAST 失败返 null 与 nop 脏数据处理契合 |
| COALESCE/IFNULL/NULLIF | zeta/functions/CommonFunction.java + dispatch ZetaSQLFunction.java:460+ | 三参数语义需对齐 SQL 标准 |
| IN（值列表） | ZetaSQLFilter.java:74-75、145-148（`inExpr`，`ParenthesedExpressionList`） | nop 需求为 IN 子查询，SeaTunnel 亦未做子查询，同为空白 |
| DECIMAL 算术 | ZetaSQLType.java:193-229（类型提升）+ SQLDecimalArithmeticTest（除法就近取整断言） | nop DECIMAL 列缺失时的类型规则可直接参考 |
| 嵌套 struct 导航 | ZetaSQLEngine typeMapping + docs/en/transforms/sql.md:99-121 | 限制：map 不能作为最外层继续下钻 |

## 引用出处
以下路径均已在本仓库逐条验证存在；行号来自实际读取内容。

1. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/SQLEngine.java:25-37`（SQLEngine 接口）
2. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/SQLTransform.java:61-68`（query/engine 选项）；`:125-140`（classifyRowError）；`:167-229`（transformTableSchema 元数据透传）；`:238-263`（schema 变更失效重建）
3. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/SQLEngineFactory.java:24-40`（EngineType）
4. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/SQLMultiCatalogFlatMapTransform.java:34-86`（多目录 Sql 路由）
4a. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/calcite/CalciteMultiCatalogTransform.java:30-52`（Calcite 多表入口）
5. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/zeta/ZetaSQLEngine.java:101-106`（UDF ServiceLoader）；`:145-156`（JSqlParser parse）；`:158-212`（validateSQL 拒绝 join/group/order/limit）；`:214-275`（typeMapping）；`:284-347`（scan→filter→project）
6. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/zeta/ZetaSQLFilter.java:64-125`（WHERE 算子分派：=,<>,>,>=,<,<=,IN,LIKE,IS NULL,AND,OR,括号）
7. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/zeta/ZetaSQLFunction.java:397-435`（EXTRACT/CASE/CAST 分派）；`:460+`（函数 dispatch）；`:1023`（multiIf）
8. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/zeta/ZetaSQLType.java:82,229`（表达式类型推导、DECIMAL）
9. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/zeta/ZetaUDF.java:19-74`（UDF SPI 接口）
10. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/sql/zeta/functions/`（String/Numeric/DateTime/System/Array/Map/Vector/Common/Cast Function 共 9 个实现文件，已列目录验证）
11. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/calcite/CalciteTransform.java:51,59,74-77,166-201`（Calcite 插件与 schema 失效）
12. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/calcite/engine/CalciteSQLEngine.java:89-157`（compile-once Bindable）；`:163-186`（按行执行、UNNEST 0-N 行）
13. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/calcite/engine/CalciteSchemaFactory.java:45-66`（registerTable）
14. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/calcite/udf/BuiltinFunctions.java:42-88`（CalciteUdf SPI 注册）；`.../udf/MaskFunction.java:30-33`（实现示例）
15. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/calcite/udf/`（L2Distance/CosineDistance/Mask/DesEncrypt 等 UDF 实现，已列目录验证）
16. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/table/TableMergeTransform.java:27-63`（TableMerge=改 tableId 的并表）
17. `seatunnel-transforms-v2/src/main/java/org/apache/seatunnel/transform/common/AbstractMultiCatalogTransform.java:67-101`（regex 路由多表）
18. `seatunnel-transforms-v2/pom.xml:65-67,102-106`（jsqlparser 与 shade-calcite 依赖）
19. `pom.xml:61`（revision 3.0.0-SNAPSHOT）
20. `seatunnel-api/src/main/java/org/apache/seatunnel/api/table/type/SeaTunnelRow.java:32-40`（tableId/rowKind/options）
21. `docs/en/transforms/sql.md:13-33`（选项）、`:48-56`（示例）、`:99-121`（struct 导航）、`:133-141`（源端 schema 声明）
22. `docs/en/transforms/sql-functions.md`（函数目录：String/Numeric/Time/System/Array/Map/Vector，全文 1390 行）
23. `docs/en/transforms/sql-udf.md:11-60`（ZetaUDF API 文档）
24. `seatunnel-e2e/seatunnel-transforms-v2-e2e/seatunnel-transforms-v2-e2e-part-2/src/test/java/org/apache/seatunnel/e2e/transform/TestSQLIT.java:33-133`（17 个 SQL e2e 用例清单）
25. `.../transform/TestCalciteIT.java:30-66`（Calcite e2e：select_where/builtin/json/mask/des）
26. `.../resources/sql_transform.conf:50-59`（Sql 块示例）；`.../resources/sql_transform_multi_table.conf:92-101`（多表路由）；`.../resources/sql_transform/explode_transform.conf:61`（LATERAL VIEW EXPLODE）；`.../resources/calcite_transform/calcite_builtin_functions.conf:40` 与 `calcite_json.conf:42`（Calcite sql 键与 JSON_VALUE）

git log：dev HEAD `a1084015eeb390a6c7f53d3ce7391217e427e4d9`（2026-10-01，#12537）。

## 优先参考的已有分析（不重复其内容）
- `../2026-09/2026-09-01-seatunnel-productization-analysis.md`：产品化评分与 P-REQ 条目，本篇只做 SQL transform 纵切。
- `../2026-10/2026-10-02-seatunnel-core-vs-nop-stream-gap-analysis.md`：支柱级 gap 判定，本篇补充其未覆盖的双 SQL 引擎细节。
- `../2026-05-19a-seatunnel-vs-nop-stream-comparison.md`：架构总览与 transform 章节概览（存在，已核实），本篇以源码行级证据刷新其结论。
