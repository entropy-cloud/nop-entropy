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
- **顶层唯一约束裁定（WI0(e)，2026-10-09）**：transform 级 SQL **不放宽** `<sql>` 顶层唯一约束——`<sql>` 顶层生成器是唯一 SQL 编译入口的单一事实源，模型内嵌 SQL 片段的第二编译路径被裁定排除（vision §六 #WI0-XDSL）。Java DataStream API 挂接 EQL/`<sql>` 编译产物是唯一的合法组合形态；重启裁定触发条件 = 真实多段 SQL 管线声明诉求出现。
- **展开机制**：`StreamModelDslBuilder.build()` 首步经 `ISqlStreamCompiler` SPI（`nop-stream-sql` 模块 app-beans 自动注册）把 `<sql>` 编译为完整流模型并替换父模型内容；classpath 无 nop-stream-sql 时 fail-fast（`nop.err.stream.invalid-arg`，错误文案点名 nop-stream-sql 模块）。

## 2. 纳入面与不支持清单

**纳入**：SELECT 投影、WHERE、五聚合（sum/count/avg/min/max，COUNT(*) 无参形态）、DISTINCT 聚合（WI17：参数值域精确去重，merge 重放保证恰一次；COUNT(*) DISTINCT 以整记录为去重域；精确去重内存代价 = 每组去重值数量——窗口聚合随窗清除，持续聚合随作业生命周期）、HAVING（WI17：GROUP BY + 聚合路径编译为 aggregate 后 filter，windowed + continuous 两路径；聚合仅出现在 HAVING 的 canonical 形态支持）、GROUP BY、`TUMBLE(t, INTERVAL)` 流时间窗口、双流等值 join（INNER/LEFT/RIGHT/FULL）、UNION ALL。

**不支持**——多数项构建期显式 fail-fast：全局 ORDER BY/LIMIT（`nop.err.eql.dialect-not-support-feature`）；非等值/范围 join；DATE/TIMESTAMP/DECIMAL 列绑定；SELECT DISTINCT 非聚合 dedup 与 UNION DISTINCT；子集外表达式（正则/IN 子查询等——CASE/CAST 已于 WI29 放行：简单/搜索 CASE 与 CAST 到九受管类型，DECIMAL 等闭集外目标仍 fail-fast）；FULL 窗口 join；无 GROUP BY 的 HAVING 与 join + HAVING（WI17 typed 守卫）；CTE/INTERSECT/EXCEPT（default-reject）；全局聚合 + HAVING（WI28 起无 GROUP BY 聚合本身已放行——合成单键 keyBy 单归约；窗口伪表源 + 全局聚合仍 fail-fast）。两项性质不同：retract/CDC 为 D1 裁定的语义降级（last-value-wins 终值语义，非 fail-fast 项）；Flink SQL 方言为非目标（编译器不解析）。语义边界：UNION 为 bag union 不去重；TUMBLE 为流目标专用（W2/T1：SQL 通道按原样回显 TUMBLE 语法——该文本在真实 RDBMS 上解析即失败，属预期边界而非缺陷；双目标一致性仅对非 TUMBLE 查询承诺精确集相等，聚合查询仅终态可比——D1 last-value-wins，流目标发射运行值）。

## 3. 执行语义标注（D1/D6）

- 聚合结果为 **last-value-wins 终值语义**：非 append-only、非 retract；持续聚合逐条 emit 当前归约值，窗口聚合按窗 fire。
- **流时间窗口伪表函数（D4 形态全集，WI26 补全）**：`TUMBLE(t, INTERVAL)`（尺寸窗口）/ `HOP(t, slide, size)`（Flink TVF 参数序的滑动窗口）/ `SESSION(t, gap)`（会话窗口，引擎 merge 管线）。时间列 t 用 **bigint epoch-millis** 承载（产物自动补 timestampsAndWatermarks + per-event assigner）；间隔单位为 SECOND/MINUTE/HOUR/DAY/WEEK 与 MICROSECOND（整毫秒倍数），非正时长、非字面量与日历单位（MONTH/QUARTER/YEAR）fail-fast（`nop.err.eql.invalid-interval-value`）——三原则对 HOP 的 slide 与 size 分别适用；本仓裁定 slide<=size（sql 层与 DSL 层双守卫，引擎 validateSliding 不校验）。HOP/SESSION 仅窗口聚合（GROUP BY）查询可用，plain 管道声明即 fail-fast。窗口列命名契约：window_start/window_end 为保留名，聚合输出暂未暴露（引用即 typed 拒绝并指向 FU-E2）。
- join 为事件时间 hash join：匹配对即时发射、outer 补齐在 watermark；匹配寿命止于 watermark 边界（有界状态）。

## 3a. 标量 UDF（WI30）

应用通过 `StreamScalarFunctionResolver.register(fnName, fn)` 注册自定义标量函数后，可在 SQL 标量表达式（SELECT/WHERE/HAVING）中以函数名调用。SPI 接口 `IScalarFunctionResolver` 与聚合解析器同构（BeanContainer 按类型查找 + app-beans 注册；roadmap 字面 ServiceLoader 实为 IoC 同构——如实披露）。UDF 解析按声明面双路径：Java 内嵌编译路径编译期一次解析捕获闭包；`<sql>`/`<sqlTransform>` 声明面为逐记录静态查表（生成 xpl 体每记录经 `StreamScalarFunctionResolver.call` 按 defaultResolver 查表），运行期换绑（重复 `registerAsDefault`，WARN 留痕）对声明面后续记录即时生效。未注册函数名 fail-fast 列出已注册名。生命周期：provider 实现 AutoCloseable 由 bean 容器传播 close；default resolver 为进程级单例语义——`register` 任意线程任意时刻可调（注册表为 ConcurrentHashMap，注册新名即时生效，覆写同名即时影响后续逐记录查表）。

```java
StreamScalarFunctionResolver resolver = new StreamScalarFunctionResolver();
resolver.register("strLen", args -> args[0] == null ? 0
        : (long) String.valueOf(args[0]).length());
resolver.registerAsDefault();
// SQL: SELECT strlen(item) FROM orders
```

限制：函数名经 EQL 解析后统一小写；参数透传 null 三值语义；返回类型 Unknown（不参与类型推断）。

## 4. 错误码

全部复用既有 `nop.err.*` 码（§4d 零新增码）：编译期 `nop.err.stream.invalid-arg`（子集外/未知类型/SELECT DISTINCT 与 UNION DISTINCT/无 GROUP BY 的 HAVING 与 join + HAVING/全局聚合/default-reject）；ORDER BY/LIMIT `nop.err.eql.dialect-not-support-feature`；TUMBLE 间隔 `nop.err.eql.invalid-interval-value`；声明面 ref/边错误 `nop.err.stream.ref-unknown`/`invalid-arg`；SPI 缺失 `nop.err.stream.invalid-arg`（文案点名 nop-stream-sql 模块）。

## 5. 兼容与迁移

- **既有 .stream.xml 模型**：`<sql>` 为纯增量顶层元素——不含 `<sql>` 的既有模型零影响；新增 `<sql>` 时模型必须以 `<sql>` 为唯一内容（不得与既有 transforms/edges 混写），且运行期 classpath 需含 nop-stream-sql（缺失时构建期 `nop.err.stream.invalid-arg` fail-fast，文案点名模块）。
- **schema 声明**：九受管类型名闭集（见 §1）；DATE/TIMESTAMP/DECIMAL 列绑定构建期 fail-fast（不静默近似）。
- **D11 emit 两档**（WI0a 裁定 + WI37 实施，2026-10-08）：`<sql emit="UPDATE|CLOSE">` 顶层属性——UPDATE 逐记录发射窗口当前聚合值（Kafka Streams ON_WINDOW_UPDATE 语义，隐含强制 ACCUMULATING；无 GROUP BY 的纯管道/连续聚合/join 查询声明 UPDATE 构建期 fail-fast）；CLOSE（缺省）维持窗口末单次发射。产物落 `<strategy emit>`，raw DSL 面亦可直接声明（join 引用的窗口 strategy 声明 UPDATE 时构建期 fail-fast）；triggerId 声明面保持 fail-fast（封闭枚举非自由 trigger）。
- **WI25 时间列 lateness**（Q1 裁定=列级形态，2026-10-08）：`<schemas><field name="ts" type="bigint" lateness="20000"/>` ——仅伪表函数（TUMBLE/HOP/SESSION）时间列生效（非时间列声明、负值、无窗口聚合路径声明均构建期 fail-fast；WI26 推广），产物映射 strategy allowedLateness（D9 管线零运行时改动）：迟到记录在窗口 end+lateness 界内更新已 fire 窗口并重发更新值；与 emit=UPDATE 正交可组合。查询级水位延迟声明未纳入（Follow-up FU-E6）。
- **D9 allowedLateness 放行**（WI10）：`<window>` 与 `<strategy>` 的 allowedLateness 自本 roadmap 起按语义消费（窗口关闭点推迟至 watermark 越过 lateness 界）。
- **D10 accumulationMode 接线放行**（WI0a 裁定 + WI38 修复，2026-10-08）：`<strategy accumulationMode="DISCARDING|ACCUMULATING">` 声明值真实到达窗口算子——DISCARDING 在窗口 fire 后清空内容（event-time + allowedLateness>0 时推迟清空以保迟到更新全量重发），ACCUMULATING 保留内容（迟到更新在既有归约上重发）；不声明时按 xdef 默认 DISCARDING。`ACCUMULATING_AND_RETRACTING` 构建期与运行期双层 fail-fast（spec-only）；triggerId 与窗口级 parallelism 仍保持 fail-fast（D11/D12）。
- **聚合语义（D1）**：SQL 聚合输出为 last-value-wins 终值语义——非 append-only、非 retract；持续聚合逐条 emit 运行值。
- **W2/T1 边界（WI20）**：`TUMBLE` 查询仅流目标可执行；SQL 通道按原样回显 `TUMBLE(...)` 语法（该文本在真实 RDBMS 解析即失败——预期边界而非缺陷）。

## 6. 编程入口（模块内部）

- `io.nop.stream.sql.compile.StreamSqlCompiler.compile(loc, sql, schema, sinkBean)`（四参：源位置/SQL 文本/schema 字段面/sink bean 名——sql 与 sinkBean 必填，schema 可为 null/empty 表示未声明 schema）：SQL 文本 → 流模型 XML（`<sql>` SPI 的底层实现）；产物可经 `DslModelParser` 回读后 `StreamModelDslBuilder` 构建。
- 求值原语（WI9）：`io.nop.stream.sql.eval.StreamSqlExprCompiler`（标量子集 → StreamRecordEvaluator）、`StreamSqlAggregations`（五聚合 id 目录）。

## 7. WI17 SQL 窄补强（plan 2342，2026-10-08 supersede）

- **HAVING 支持**（supersede 原 default-reject 钉码）：GROUP BY + 聚合路径编译为 aggregate 后 filter（windowed + continuous 两路径，AggregateRef 解析 `event[N]` 槽位）；canonical 形态（聚合仅出现在 HAVING）支持；无 GROUP BY 的 HAVING 与 join + HAVING typed 拒绝（`nop.err.stream.invalid-arg`）；全局聚合 + HAVING 维持排除。
- **DISTINCT 聚合支持**（supersede 4a#6 排除与 `distinctFailsFast` 钉子）：`DistinctAggregateWrapper` 按求值后参数值去重（null 不入集）；reduce 路径 merge 重放对侧未见过的新值，恰一次计数；`AggCall` spec 增 `distinct` 字段（JSON 向后兼容，缺省 false）；snapshot/restore 经 `SqlAccShapeUtil` 形状归一化。COUNT(*) DISTINCT 以整记录为去重域。
- **continuous 聚合 restore 修复**（M-3 测试暴露的既有缺陷）：JSON 往返把嵌套数组退化为 List、Long 标量退化为 Integer——五聚合 + composite 行全部经 `SqlAccShapeUtil` 归一化；`StreamReduceOperator` restore 对值按记录类型重建（失败仍 typed `nop.err.stream.type-mismatch`）。
- **lookup 缓存接线**（WI11 交接口径）：`DimLookupEnrichFunction` 样例持有 `CachingTableLookup`（per-key ValueState 快路径之上叠加跨 key TTL/后台刷新物化层），`close()` override 传播——close 后 cache-miss lookup typed 拒绝。
- SELECT DISTINCT 非聚合 dedup 维持排除（需 keyBy+keyed 去重执行语义，见承载 plan Deferred 记录）。
