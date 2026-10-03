# nop-stream-sql — SQL 窄范围接口

> Status: active
> 覆盖：`nop-stream/nop-stream-sql` 模块的 `<sql>` 声明式 SQL 接口（WI17/WI18 交付）。
> 上位文档：`03-modules/nop-stream-user-guide.md`（流作业用户指南）。

## 1. `<sql>` 声明元素

在 `.stream.xml` 模型中以顶层 `<sql>` 元素声明一条 SQL 查询，构建期自动展开为等价的流管线（source→window/keyBy→聚合→sink）：

```xml
<stream x:schema="/nop/schema/stream/stream.xdef" name="demo" version="1">
    <sql sinkBean="mySink">
        <schemas>
            <field name="ts" type="bigint"/>
            <field name="item" type="string"/>
            <field name="amount" type="int"/>
        </schemas>
        <source><![CDATA[SELECT item, sum(amount) AS total
FROM TUMBLE(orders.ts, INTERVAL 5 SECOND) WHERE amount > 0 GROUP BY item]]></source>
    </sql>
</stream>
```

要素：

- **schema（D7）**：`<schemas>/<field name type>` 声明查询的列面。受管类型名九个闭集：`string/int/bigint/smallint/tinyint/float/double/boolean/bytes`；集合外类型（DATE/TIMESTAMP/DECIMAL 等）构建期 `nop.err.stream.invalid-arg` fail-fast（不静默近似）。
- **sql 文本**：EQL 语法（与 ORM 同一 parser，单一口径）；CDATA 内嵌。
- **sinkBean**：产物 sink 绑定的 `SinkFunction` bean 名——应用需在 beans 中注册该 bean。`<sql>` 必须是模型的唯一内容（与其他 transforms/edges 并存即 fail-fast）；查询的 FROM 表名即 source 的 **bean 名**（应用以表名注册数据源 bean）。
- **展开机制**：`StreamModelDslBuilder.build()` 首步经 `ISqlStreamCompiler` SPI（`nop-stream-sql` 模块 app-beans 自动注册）把 `<sql>` 编译为完整流模型并替换父模型内容；classpath 无 nop-stream-sql 时 fail-fast（`nop.err.stream.invalid-arg`，错误文案点名 nop-stream-sql 模块）。

## 2. 纳入面与不支持清单

**纳入**：SELECT 投影、WHERE、五聚合（sum/count/avg/min/max，COUNT(*) 无参形态）、GROUP BY、`TUMBLE(t, INTERVAL)` 流时间窗口、双流等值 join（INNER/LEFT/RIGHT/FULL）、UNION ALL。

**不支持**——多数项构建期显式 fail-fast：全局 ORDER BY/LIMIT（`nop.err.eql.dialect-not-support-feature`）；非等值/范围 join；DATE/TIMESTAMP/DECIMAL 列绑定；DISTINCT 聚合；子集外表达式（CASE/CAST/正则/IN 子查询等）；FULL 窗口 join；HAVING/CTE/INTERSECT/EXCEPT（default-reject）；全局聚合（无 GROUP BY 的聚合）。两项性质不同：retract/CDC 为 D1 裁定的语义降级（last-value-wins 终值语义，非 fail-fast 项）；Flink SQL 方言为非目标（编译器不解析）。语义边界：UNION 为 bag union 不去重（UNION DISTINCT fail-fast）；TUMBLE 为流目标专用（W2/T1：SQL 通道按原样回显 TUMBLE 语法——该文本在真实 RDBMS 上解析即失败，属预期边界而非缺陷；双目标一致性仅对非 TUMBLE 查询承诺精确集相等，聚合查询仅终态可比——D1 last-value-wins，流目标发射运行值）。

## 3. 执行语义标注（D1/D6）

- 聚合结果为 **last-value-wins 终值语义**：非 append-only、非 retract；持续聚合逐条 emit 当前归约值，窗口聚合按窗 fire。
- `TUMBLE(t, INTERVAL)` 的时间列 t 用 **bigint epoch-millis** 承载（产物自动补 timestampsAndWatermarks + per-event assigner）；间隔单位为 SECOND/MINUTE/HOUR/DAY/WEEK 与 MICROSECOND（整毫秒倍数），非正时长、非字面量与日历单位（MONTH/QUARTER/YEAR）fail-fast（`nop.err.eql.invalid-interval-value`）。
- join 为事件时间 hash join：匹配对即时发射、outer 补齐在 watermark；匹配寿命止于 watermark 边界（有界状态）。

## 4. 错误码

全部复用既有 `nop.err.*` 码（§4d 零新增码）：编译期 `nop.err.stream.invalid-arg`（子集外/未知类型/DISTINCT/全局聚合/default-reject）；ORDER BY/LIMIT `nop.err.eql.dialect-not-support-feature`；TUMBLE 间隔 `nop.err.eql.invalid-interval-value`；声明面 ref/边错误 `nop.err.stream.ref-unknown`/`invalid-arg`；SPI 缺失 `nop.err.stream.invalid-arg`（文案点名 nop-stream-sql 模块）。

## 5. 兼容与迁移

- **既有 .stream.xml 模型**：`<sql>` 为纯增量顶层元素——不含 `<sql>` 的既有模型零影响；新增 `<sql>` 时模型必须以 `<sql>` 为唯一内容（不得与既有 transforms/edges 混写），且运行期 classpath 需含 nop-stream-sql（缺失时构建期 `nop.err.stream.invalid-arg` fail-fast，文案点名模块）。
- **schema 声明**：九受管类型名闭集（见 §1）；DATE/TIMESTAMP/DECIMAL 列绑定构建期 fail-fast（不静默近似）。
- **D9 allowedLateness 放行**（WI10）：`<window>` 与 `<strategy>` 的 allowedLateness 自本 roadmap 起按语义消费（窗口关闭点推迟至 watermark 越过 lateness 界）；accumulationMode/triggerId/窗口级 parallelism 仍保持 fail-fast。
- **聚合语义（D1）**：SQL 聚合输出为 last-value-wins 终值语义——非 append-only、非 retract；持续聚合逐条 emit 运行值。
- **W2/T1 边界（WI20）**：`TUMBLE` 查询仅流目标可执行；SQL 通道按原样回显 `TUMBLE(...)` 语法（该文本在真实 RDBMS 解析即失败——预期边界而非缺陷）。

## 6. 编程入口（模块内部）

- `io.nop.stream.sql.compile.StreamSqlCompiler.compile(loc, sql, schema, sinkBean)`（四参：源位置/SQL 文本/schema 字段面/sink bean 名——sql 与 sinkBean 必填，schema 可为 null/empty 表示未声明 schema）：SQL 文本 → 流模型 XML（`<sql>` SPI 的底层实现）；产物可经 `DslModelParser` 回读后 `StreamModelDslBuilder` 构建。
- 求值原语（WI9）：`io.nop.stream.sql.eval.StreamSqlExprCompiler`（标量子集 → StreamRecordEvaluator）、`StreamSqlAggregations`（五聚合 id 目录）。
