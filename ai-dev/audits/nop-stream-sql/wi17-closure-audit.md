# WI17 Closure Audit——25-wi17-sql-compiler.md（r2）

- Audit 日期：2026-10-03（单轮，PASS）
- Auditor：独立子 agent（fresh session，与实现者非同一 session/task_id；全部结论来自 live repo 实读/实跑，未采信 plan 勾选、日志自述或 commit message）
- 审计对象：HEAD `996bb9c12a`（分支 add-stream-sql，审计起止工作树均 clean；实现提交 65 文件 +8384/-3820，父提交 `a8957472b1` 用于 invariants 既有态反事实）
- **最终裁定：PASS——Phase 1-3 全部核验项成立：TUMBLE grammar 一一对应且生成物零手改；StreamSqlCompiler 按 r2 五项 Blocker 裁定形态落地（复合聚合器不动 WI8c 面/表名即 bean/聚合 dispatch/`<sql>` 模型生成器/三层测试）；fail-fast 矩阵 30 用例钉码齐备；语义等价钉子与四具名 E2E 真实可执行且判别；五模块全量实跑零退化（eql 130 / core 1668 / flow 163 / sql 69 / orm 208）；门禁 doc-links 0、scan-hollow 全 0、ast-grep 0、invariants sync OK；无静默跳过；r2 忠实度高。5 项 Minor（无 Blocker/Major），不阻断 roadmap 翻转，处置路径见 §8。**

---

## 0. 裁定摘要

功能面与证据面成立：Phase 1 的 grammar/AST/parser 链条机械完整（`TUMBLE` token + `unreservedWord_` 登记 + `sqlTumbleTableSource` 规则 ↔ `SqlTumbleTableSource` AST 类字段一一对应，ANTLR/XGEN 生成物 diff 均为生成器产物形态）；Phase 2 的 `StreamSqlCompiler` 逐条落实 r2 B1/B2/B3 裁定（`sql-row-agg` 复合条目经既有 `IAggregatorFunctionResolver` SPI 扩展分支解析、WI8c 五 id 分发与 WI9 既有文件零改动；产物 `<source bean>` 属性 = FROM 表名且测试按表名注册 stub；TUMBLE→keyBy+window+aggregate / 无 TUMBLE GROUP BY→map+keyBy+reduce+map / 全局聚合 fail-fast）；事件时间产物含 `timestampsAndWatermarks` + inline assigner + per-event watermark generator；join 按 scope 归侧（自 join 同键文本有 E2E 实证）经 `event.left/right` 投影；§4a 九项 + default-reject 的钉码矩阵以 30 个 `@Test` 逐项落地；语义等价钉子经**产物 XML 回读执行**与 WI9 evaluator 对拍（判别力有反事实锚）；Phase 3 的 `<sql>` 声明面 + SPI + builder 预处理 + 三层测试全部在案，声明模型 E2E 从 xdef 校验入口一路 execute 到 sink 断言 `[a=4,b=2,b=4,c=1]`（与窗口聚合+水位线语义自洽）。五模块全量实跑计数与声称逐一相符且 0F/0E（skip 均为既有）。**发现全部为 Minor**（invariants 全扫描既有漂移、UNION bag 语义 design 文档层未落、两处 fail-fast 无测试钉、beans 注释指向、plain TUMBLE INTERVAL 未校验），均不构成行为缺陷或不变量违反。

---

## 1. Phase 1——TUMBLE grammar（审计项 1）——PASS

- **token + unreservedWord**：`SQL92Keyword.g4` 新增 `TUMBLE : T U M B L E ;`（:650-652 区段，含登记理由注释）；`BaseRule.g4` `unreservedWord_` 追加 `| TUMBLE`。标识符兼容有专测（`TestEqlTumbleGrammarParse.testTumbleRemainsUsableAsIdentifier`：列名/表名/谓词三形态 parse 通过）。
- **规则 ↔ AST 类一一对应（WI1 硬约束）**：`DMLStatement.g4` `sqlTableSource` 第三备选 `sqlTumbleTableSource #SqlTumbleTableSource_ex`，规则标签 `tableName=/timeColumn=/interval=/alias=` 与 `EqlAST.xjava` 新类 `SqlTumbleTableSource`（extends SqlTableSource）四字段逐一对应；生成器产物齐备——`_gen/_SqlTumbleTableSource.java`（298 行，`__XGEN_FORCE_OVERRIDE__` 头）、`EqlASTKind`（ordinal 机械重排形态）、`EqlASTVisitor`/`EqlASTProcessor`/`EqlASTOptimizer`（模板化 case/方法插入）、`_EqlASTBuildVisitor`（`visitSqlTumbleTableSource` 四字段逐个 build + `normalize()/validate()`）、ANTLR `EqlParser`/`EqlLexer`（全量重生成 diff）。手写侧仅 `SqlTumbleTableSource.java`（33 行 wrapper）与 `EqlASTBuildVisitor` 两个 prop-parse 钩子（`SqlTumbleTableSource_tableName/timeColumn`，live :369/:374）。**全部生成物 diff 为生成器产物形态，零手改痕迹。**
- **隔离实跑**：`TestEqlTumbleGrammarParse` 14/14 绿（实跑日志 `_tmp/audit-wi17/test-eql.log`：`Tests run: 14, Failures: 0, Errors: 0`）。用例覆盖：parse 矩阵 6（basic/小写/alias/五固定单位/流查询形态/join 左源）、AST 字段+时长提取 2、fail-fast 4（非正含 -1 一元负号形态/非字面量含列引用/日历单位 MONTH-QUARTER-YEAR/亚毫秒 MICROSECOND）、语法错误 1、标识符兼容 1——与声称的 14 用例逐一相符。测试全程不用 toSQL 当 oracle（round-trip 不对称在案，类 javadoc 显式声明）。
- **interval fail-fast 钉码**：`EqlParseHelper.intervalDurationMillis`——非 `SqlNumberLiteral`/`Long.parseLong` 失败/`value <= 0`/unit null → `nop.err.eql.invalid-interval-value`；固定单位 SECOND/MINUTE/HOUR/DAY/WEEK 换算 + MICROSECOND 整毫秒限定（`% 1000 != 0` fail-fast）；default 分支 MONTH/QUARTER/YEAR fail-fast。新错误码 `ERR_EQL_INVALID_INTERVAL_VALUE` 落 `OrmEqlErrors`（家族既有风格）。编译期穿透有钉：`TestStreamSqlCompiler.nonPositiveTumbleIntervalFailsFast` 断言 `INTERVAL 0 SECOND` 在 `StreamSqlCompiler.compile` 内抛 eql 码。
- **ORM 通道 fail-fast（防静默跳过）**：`SqlTumbleTableSource.getSourceSelect()/getResolvedTableMeta()` 均抛 `ERR_EQL_TABLE_SOURCE_NOT_RESOLVED`（stream-only，T1 直通）；`EqlTransformVisitor.addAliasToScope` 显式 `instanceof SqlTumbleTableSource` 分支抛同码——作用域注册不静默跳过。（钉码测试缺失见 §3 MIN-3a。）
- **eql 全量 + nop-orm 回归**：实跑 nop-orm-eql **130/130**（0F/0E，+14 新增，与声称 116→130 相符）；nop-orm **208**（0F/0E，6 skip 为既有 docker opt-in——逐条核对该 skip 属 `TestContainers` opt-in 形态，非本提交引入）。
- **文档同步**：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 新增「TUMBLE 时间切片伪表函数（2026-10-03 WI17 扩展，stream-only）」小节——语法/固定单位/INTERVAL fail-fast 码/stream-only 范围与 `table-source-not-resolved`/toSQL 不对称警示/标识符兼容/HOP-SESSION 未进语法层，逐句与实现相符；原「时间切片窗口尚未进入 EQL 语法层」失实句已同步删除。

## 2. Phase 2——StreamSqlCompiler（审计项 2，r2 裁定逐项）——PASS

### 2.1 B1 复合聚合器

- `SqlRowAggregateSpec`（fnId 常量 `sql-row-agg`；JSON 手工发射带转义 + `fromJson` 全字段 fail-fast 校验）；`SqlRowCompositeFunction implements AggregateFunction`——**键重求值**：`add()` 每记录重算 `keyEvals[i].eval(record)` 写入键槽；**N 子累加器**：`createAccumulator` 产 `Object[]{Object[] keys, Object[] subAccs}`、`add` 逐子聚合转发（累加语义单源于 WI9 `StreamSqlAggregation`，零重实现）；**getResult 行形状**：`List<Object>` 组键在前、聚合按 SELECT 序（槽序由 `buildGroupedAggregate` 按 `collectAggregates` 的 `putIfAbsent` 发现代序排序后赋槽，代码注释与实现一致，防 IdentityHashMap 无序迭代）；`merge` 首见键胜出（keyed 分区内键恒同，语义安全）。
- **WI8c 五 id 分发零改动**：`StreamAggregatorFunctionResolver` diff 仅为 `resolve()` 头部插入 `sql-row-agg` 拦截分支（expr 空 → `invalidArg` fail-fast），既有 `StreamSqlAggregations.resolve(fnId)` 目录分发原样保留；`StreamSqlExprCompiler`/`StreamSqlAggregation`/WI8c 三测试类（`TestStreamSqlExprCompiler` 13、`TestStreamSqlAggregations` 11、`TestStreamAggregatorFunctionResolver` 10）**不在提交改动清单，隔离实跑全绿**（sql 模块 69 总数内可见）。`StreamSqlAggregations` 仅追加 `buildComposite`（未知子 fnId / 非 count 缺参 → `nop.err.stream.invalid-arg`）。
- 持续聚合 xpl 体经 `SqlRowAggregateOps` 静态 `begin/merge/toRow` 分派（spec JSON 缓存），产物 xpl 零重实现累加——与 compiler-contract §2.2 新增「持续聚合的 xpl 体经 SqlRowAggregateOps 静态分派（import + 静态调用形态）」一致。

### 2.2 B2 表名即 bean

- `sourceNode` 产物 `<source bean="表名">`（`StreamSqlCompiler.sourceNode` :857-859）；产物结构断言 `tumbleWindowedAggregateProductShape` 首断言即 `bean == "orders"`；测试基建 `sql-compile.beans.xml` 以表名注册 stub（`<bean id="orders" class="...OrdersSourceFunction"/>`、`items`、`testSink`）——B2 契约两端闭合，无发明命名约定、无空数据 source。

### 2.3 B3 dispatch 与事件时间（M1）

- **TUMBLE→keyBy+window+aggregate**：`compileWindowedAggregate` 产物链 source→timestampsAndWatermarks→[filter]→keyBy(groupKeys)→window(strategyRef)→aggregate(aggregatorRef=合成条目)→map 投影→sink；`windowingStrategies` 注册 `tumbling-event-time` + duration（`INTERVAL 5 SECOND`→`5000ms` 有断言）。产物断言与边拓扑逐项核过。
- **无 TUMBLE 有 GROUP BY→map+keyBy+reduce+map**：`compileContinuousAggregate` 产物 begin（`SqlRowAggregateOps.begin(spec,event)`）→ keyBy（`event[0][0]`——累加器键数组头，日志所称执行期缺陷修正后的形态，与 `rowHeadKeyExpr` 一致）→ reduce（`SqlRowAggregateOps.merge(spec,a,b)`）→ map（`toRow`）→ 投影。**last-value-wins 标注核验**：`StreamModelDslBuilder` 侧 D1 口径注释与 `SqlRowAggregateOps.merge` javadoc（「last-value-wins final-value semantics per D1」）在案；产物无 `<aggregate>` 节点有负断言（`continuousAggregateProductShape`）。
- **全局聚合 fail-fast**：`compileQuery` 聚合路径 `groupBu == null` → `nop.err.stream.invalid-arg`，有专测 `globalAggregationFailsFast`。
- **事件时间**：`timestampsNode` 产物含 `<timestampAssigner>return event['ts'];</timestampAssigner>`（t 列 bigint epoch-millis）+ `<watermarkGenerator>` per-event `emitWatermark(new Watermark(eventTimestamp))`——产物断言与 E2E 窗口触发行为双重印证。

### 2.4 join 映射（M3）与 scope 归侧正确性

- joinType→流形态：`JOIN/LEFT/RIGHT/FULL → INNER/LEFT/RIGHT/FULL`（hash 形态，无 windowStrategyRef——`nextJoinSpec` 产物仅 joinType/leftKeyExprs/rightKeyExprs；FULL hash 为 `EquiJoinOperator` 合法形态，与 WI13 口径一致）。
- **ON 合取等值对 → left/rightKeyExprs**：`decomposeOnCondition` 展开合取树，每个合取须为 `SqlOperator.EQ` 且两操作数分属两侧 scope，**按 scope 归侧而非操作数位置**（`SideKey lk = a.isLeft ? a : b`）。正确性验证：① 自 join 同键文本（`ON l.id = r.id`，两侧 keyExpr 文本同为 `event['id']`）有 E2E 实证（`TestJoinQueryE2E` 断言 `[1=x, 2=y]`）；② 反写形态 `ON r.k = l.k` 由同一赋值语句覆盖——代码读码确认顺序无关（scope 判定先于左右取用），无专测（见 §3 MIN-3b，不构成行为风险：与 ① 同一代码路径的对称形态）。
- **行=Map**：投影体 `let __r = {"i": event.left['item'], ...}; return __r;`；**JoinMatch 投影经 event.left/right**：`joinProjectionResolver` 按 qualifier 归侧发射 `event.left['col']`/`event.right['col']`，未知 scope fail-fast，非限定列 fail-fast（有专测 `unqualifiedJoinColumnFailsFast`）。

### 2.5 fail-fast 矩阵（M4）——逐项对账

§4a 九项射程内全部钉码：#1 ORDER BY/LIMIT→`nop.err.eql.dialect-not-support-feature`（`dialectNotSupport`，两专测断言码 + feature 参数）；#2 非等值 join（`!= EQ` fail-fast，专测）；#3 retract/CDC（D1=(a) 降级，无 retract 发射路径，产物/注释标注核验见 2.3）；#4 Flink 方言非目标；#5 受管九类型闭集（`validateSchema`，`decimal` 专测断言码与消息）；#6 DISTINCT 聚合（`scanProjections`，专测）；#7 标量子集外表达式（`SqlScalarXplPrinter` default→`unsupported`，CASE/CAST 专测断言 stream 码零 EQL 码泄漏）；#8 TUMBLE+join fail-fast（专测；非窗口 FULL join 不受限）；#9 CEP 级不在 roadmap。default-reject：HAVING/CTE-WITH/SELECT DISTINCT/INTERSECT/EXCEPT/UNION DISTINCT/括号选择+LATERAL/SELECT */全局聚合/GROUP BY 无聚合/WHERE 含聚合（filter printer 无 aggregate resolver → `SqlScalarXplPrinter` agg 分支 null ref fail-fast）/非 GROUP BY 列（`aggregatedRowResolver` fail-fast）/聚合+join/多语句/空 SQL/空 sinkBean/表源类型未知分支——`TestStreamSqlCompiler` 内逐项专测（恰 30 个 `@Test`，实跑 30/30 绿）。**AST 走查无静默分支**：`compileStatement` 末分支 throw、`compileQuery` 表源类型三分支后无漏网、`SqlScalarXplPrinter.printTo` default throw。

### 2.6 语义等价钉子（M2）——真实存在且判别

`filterEquivalenceNullPropagationAndThreeValuedLogic`：14 个表达式 × 5 记录矩阵（含 null amount、null item）——**编译产物**经 `StreamSqlCompiler.compile` → `XNode.parse` → `DslModelParser.parseFromNode` 取回 `StreamFilterModel.getSource()`（IEvalFunction）**实际执行**，与 WI9 `StreamSqlExprCompiler.compileScalar` evaluator 对拍，逐记录 `assertEquals`，null 结果双侧同时 `assertNull`。**反事实锚**：若 `SqlScalarXplPrinter` 未发射三值守卫，XLang 原生 null 比较→false ≠ evaluator null，MATRIX 第 2/3/5 记录立即红——三值守卫（比较/算术/AND-OR/NOT/一元负号/BETWEEN 组合/IN 三值）是等价成立的必要条件，非装饰。除法 double 另立钉子（`divisionIsDoubleThroughProductBody`：产物 map 体执行 `5/2→2.5` 与 evaluator 全等，行载体 Map 断言）。「等价成立而非分歧记录」的声称与测试形态相符。

### 2.7 自校验闭合（Anti-Hollow）——四具名 E2E 实跑且别力成立

四类均走 `compile → DslModelParser 回读 → StreamModelDslBuilder.build → env.execute → sink 断言` 全链：

| E2E 类 | 查询形态 | 断言 | 语义别力判定 |
|---|---|---|---|
| TestTumbleAggregateQueryE2E | TUMBLE 窗口聚合（WHERE+GROUP BY） | `[a=4,b=2,b=4,c=1]` | **与窗口+水位线语义自洽**：FIXED_DATA ts∈{1s,2s,3s,4s,11s,12s,17s}，[0,5s) 窗 a=1+3（null 跳过）b=2 由 11s 记录推水位触发；[10,15s) b=4 由 17s 记录触发；[15,20s) c=1 由 EOS MAX_WATERMARK 触发——逐窗推演与断言完全一致 |
| TestContinuousGroupByQueryE2E | 持续 GROUP BY（无 TUMBLE） | 逐元素序 `[a=1,b=2,a=1,a=4,b=6,a=-1,c=1]` | **与 D1 last-value-wins 一致**：七记录逐条累加和 1,2,1(null 跳),4,6,-1,1，`<reduce>` 逐条 emit 语义精确复现（WI11 口径）；非 append-only 非 retract |
| TestJoinQueryE2E | 自 join 同键文本 | `[1=x,2=y]` | hash join 配对 + `event.left/right` 投影正确 |
| TestUnionQueryE2E | UNION ALL 跨表 | 7 元素 bag（左 5 + 右 2，不去重） | UNION ALL=bag union 无去重 |
| TestSqlModelDeclarationE2E（Phase 3） | `<sql sinkBean>` 声明模型（TUMBLE 窗口聚合） | `[a=4,b=2,b=4,c=1]` + `getSql()` 被消费断言 + provider 反空壳钉 | 声明入口→xdef 校验→builder 展开→execute→sink 全链闭合（该序列与**窗口聚合**语义一致，见 §2.7 首行推演） |

sql 模块全量实跑 **69/69 绿**（0F/0E）。

## 3. Phase 3——`<sql>` 声明面与 SPI（审计项 3）——PASS

- **stream.xdef**：顶层 `<sql sinkBean="!string" xdef:name="StreamSqlModel">`（`<schemas><field name type/></schemas>` + `<source>string</source>`，CDATA 由调用方书写）——落在 base xdef（D13 回改通路），带裁定注释。
- **flow `_gen` 重生成 + wrapper**：`_StreamSqlModel`（168 行）/`_StreamSqlFieldModel`（107 行）生成物与既有 `_StreamModel.java` 同构（无 `__XGEN_FORCE_OVERRIDE__` 头与该目录生成器模板一致）；手写 wrapper `StreamSqlModel`/`StreamSqlFieldModel` 为保留形态；`_StreamModel` 增 `sql` 属性（getter/setter/注记为模板产物形态）。diff 共 5 文件零越界。
- **SPI 与 builder 预处理（B4）**：`ISqlStreamCompiler`（flow/spi，javadoc 载明模型生成器契约与 Anti-Hollow 义务）；`StreamModelDslBuilder.build()` 首语句 `expandSqlModel()`——`<sql>` 与 transforms/edges/aggregators/joins/schemas/windowingStrategies 并存 → `nop.err.stream.invalid-arg`（"must be the model's only content"）；`BeanContainer.isInitialized()` 双检查 + `tryGetBeanByType` 无 provider → fail-fast **点名 nop-stream-sql**（消息含坐标 `io.github.entropy-cloud:nop-stream-sql`）；产物 `XNode.parse` 失败与 `DslModelParser` xdef 校验失败均 fail-fast 定位到 `<sql>`；回读成功后**全量替换** transforms/edges/schemas/aggregators/joins/windowingStrategies + `setSql(null)`。sql 侧 `StreamSqlCompilerProvider` 薄委托 `StreamSqlCompiler`，经 `_vfs/nop/stream/sql/beans/app-sql-compiler.beans.xml` 注册（无 provider 点名、无 nop-stream-sql 编译期依赖反向泄漏——SPI 仅 flow 类型引用）。
- **三层测试（B5）**：flow `TestSqlModelExpansion` 3/3 绿（fake 产物替换父模型 + `getSql` 消费断言 / 并存 fail-fast 断言码与 "only content" 文本 / 空容器无 provider 点名 nop-stream-sql）；sql `TestSqlModelDeclarationE2E` 1/1 绿（`tryGetBeanByType` 非空且 instanceof Provider 反空壳钉 + 声明模型 execute 断言）。红→绿声称（stash SPI 后 flow 测试构建失败）与 `FakeSqlStreamCompiler`/`test-sql-expansion.beans.xml`/`test-empty.beans.xml` 测试基建在案相符。
- 装配机制核验：provider 与 WI8c resolver 同目录同 `app-*.beans.xml` 机制；WI8c 的 full-init 自动装配钉（`TestStreamAggregatorFunctionResolver.resolverAutoAssemblesThroughModuleMechanism`）覆盖同目录发现机制，provider 的反空壳钉经显式加载 + by-type 查找（机制差异极低，见 §3 MIN-4 注）。

## 4. 全量实跑（审计项 4，本轮全跑）

| 模块 | 实跑结果 | 声称 | 判定 |
|---|---|---|---|
| nop-orm-eql | 130/130，0F/0E | 130 | ✓ |
| nop-stream-core | 1668，0F/0E，1 skip（`TestEventTimeWindowE2E` @Disabled flaky，`d79259fa96` 既有） | 1668 | ✓ |
| nop-stream-flow | 163/163，0F/0E | 163 | ✓ |
| nop-stream-sql | 69/69，0F/0E | 69 | ✓ |
| nop-orm | 208，0F/0E，6 skip（docker opt-in 既有） | 208 | ✓ |

日志存 `_tmp/audit-wi17/test-*.log`（审计后清除，摘要留本报告）。五计数与 commit message/plan 勾选逐一相符，零退化成立。

## 5. 门禁（审计项 5）

- `node ai-dev/tools/check-doc-links.mjs --strict` → **exit 0**（"No errors found"）。
- `check-nop-stream-invariants.mjs sync` → **"sync: OK" exit 0**；全量扫描（无参）exit 1——7×`scan-wiring` V3 stale（`StreamTaskInvokable.java` 5 处 + `GraphModelCheckpointExecutor.java` 1 处 + 1）+ 4×`scan-output-contract` V5 stale（`WindowOperator.java` 2 处、`CepOperator.java` 2 处）。**既有漂移非 WI17 引入**：`git worktree` 检出父提交 `a8957472b1` 与更早的 `fe091bb2c6`（WI21 翻转点）复跑同样 exit 1；涉事文件零个在 `996bb9c12a` 改动清单（WI17 未触 core/runtime/cep）；漂移源于 master 侧早期提交（`StreamTaskInvokable` 最近改动 `b738d61212`，registry 最后钉版 `524446eee8`）。见 §3 MIN-1。
- `scan-hollow-implementations.mjs nop-stream-core nop-stream-flow nop-stream-sql --severity high` → **Critical 0 / High 0 / Medium 0 / Low 0**。
- ast-grep：`sg scan`（ai-dev/tools/sgconfig.yml 三规则 bare-runtimeexception/empty-catch/getmessage-only）对受影响模块 **0 findings**，`run-java-lint.sh` 干净退出。

## 6. git/_gen 纪律 与 plan/日志一致性（审计项 6/7）

- **working tree clean**（审计起止 `git status --porcelain` 空）；提交 65 文件（任务卡记 66，实际 `git show --name-only | wc -l` = 65，0 删除——计数笔误在任务卡侧，非实现问题）。
- **_gen 改动均为重生成产物形态**（§1 逐文件核验）；新文件无探针残留（新增非测试文件 12 个全为交付物：grammar 手写 wrapper、SPI、provider、编译器与聚合器、beans、wrapper 模型类）；`_tmp/` gitignored。
- **plan 25 live 状态**：Phase 1-3 全勾 + Status: completed；Phase 4（7 项）与 Closure Gates（10 项）按待审计态未勾——与本审计时点一致；`check-plan-checklist --strict` exit 0（非 completed 计划仅警告）。
- **roadmap 权威判定**：WI17 行仍 `todo`（翻转前正确基线）；解析器断言（`parseRoadmapMarkdown`）：**items=38（31 工作项 + 7 里程碑）、done=24/31、WI17=todo、progress=0.77**——与 plan Phase 4 预设的翻转后断言基线（31/7/done→25）衔接成立。
- **日志一致性**：`ai-dev/logs/2026/10-03.md` WI17 三段条目逐句对照 live 相符——含两处执行期发现的实锤（keyBy `event[0][0]` 形态在 `rowHeadKeyExpr`；ON scope 归侧在 `sideColumnRef`/`decomposeOnCondition`）；xpl 能力 spike 结论（import+静态调用、let 声明）与产物 xpl 形态一致；计数勾稽（Phase 2 68 → Phase 3 69）一致。

## 7. 无静默跳过 与 r2 忠实度（审计项 8/9）

- **编译器全分支**：statement/表源/投影/表达式四级 dispatch 均有 final fail-fast 分支（§2.5）；`expandSqlModel` 全路径（无 sql 返回/并存/容器未初始化/无 provider/解析失败/校验失败/成功替换+setSql(null)）无 no-op；UNION ALL bag 语义与产物及 E2E 一致，UNION DISTINCT fail-fast。
- **五项裁定忠实度**：B1（复合聚合器经既有 SPI、WI8c 面零改动——§2.1 逐文件 diff 级核验）✓；B2（表名即 bean、stub 按表名注册）✓；B3（dispatch 三分叉 + last-value-wins D1 口径 + 全局聚合 fail-fast）✓；B4（`<sql>` 模型级生成器、buildTransforms 前编译、唯一内容并存 fail-fast、sinkBean 派生 sink、回读替换）✓；B5（三层测试形态与 WI8c 先例对齐）✓。**无静默偏离**；唯一的字面偏差是 B3「TUMBLE 在场→keyBy+window+aggregate」仅在聚合查询面成立——非聚合 TUMBLE 查询走 pass-through+watermarks 管线（`plainPipelineWithTumbleProductShape` 显式断言无 window 节点），该形态有测试钉与产物断言、非静默，但其 INTERVAL 未校验与语义定位见 §3 MIN-5。

## 8. 发现分级与处置路径

无 Blocker / 无 Major。5 项 Minor（均不阻断翻转，前 3 项建议随 Phase 4 收口或紧随的 chore 处理）：

- **MIN-1（既有漂移备案）**：`check-nop-stream-invariants.mjs` 无参全量 exit 1（7×scan-wiring V3 + 4×scan-output-contract V5 stale，core/runtime/cep 行号漂移）。非 WI17 引入（父提交与 WI21 翻转点同等失败；涉事文件 WI17 零触碰）；`sync` 子命令 OK 与 WI21/WI13 日志「sync OK」口径一致。处置：WI17 收口后以独立 chore 提交按现行行号重钉 `wiring-registry.json`/`output-contract` registry（勿混入 WI17 翻转提交）。
- **MIN-2（文档义务缺口）**：UNION=bag union 不去重 / UNION DISTINCT fail-fast 的语义裁定仅在代码 javadoc、测试与 commit message 落档，design 文档层（compiler-contract §2.2 或 subset §4b）未记录——plan Goals M4 明文「文档记录」。处置：Phase 4 第 4 项「r2 裁定回写复核」时在 compiler-contract §2.2 补一句。
- **MIN-3（fail-fast 无测试钉）**：a) ORM 通道 TUMBLE 表源 `nop.err.eql.table-source-not-resolved`（`SqlTumbleTableSource.getSourceSelect/getResolvedTableMeta` + `addAliasToScope` 分支）代码在案但零测试钉；b) 反写 `ON r.k = l.k` 的 scope 归侧无专测（同键文本自 join 已有 E2E；代码路径对称，风险低）。处置：小测试补钉（a 归 eql 模块、b 归 sql 模块各一条），可随收口提交。
- **MIN-4（注释指向失准）**：`app-sql-compiler.beans.xml`/`app-aggregator.beans.xml` 注释写「module marker at _vfs/nop/stream-sql/_module」，而该 beans 目录实际由 `_vfs/nop/stream/sql/_module` 标记承载（WI8c 双标记遗留；日志已记录「沿双标记布局不变」但注释指向另一标记）。处置：顺手改注释一行；另注 provider 的自动装配证明经显式加载 + by-type 钉（同目录机制已被 WI8c full-init 钉覆盖），如求完备可在 Phase 4 补一条 full-init 自动装配断言。
- **MIN-5（plain TUMBLE INTERVAL 未校验）**：非聚合 TUMBLE 查询走 plain 管线时不调用 `intervalDurationMillis`——`INTERVAL 0 SECOND`/`INTERVAL 1 MONTH` 在该路径被静默接受（产物无 window 节点、行语义不受影响，但与「非正/日历单位 fail-fast」钉码纪律不一致，且 B3 字面仅覆盖聚合面）。处置：二选一——plain 管线同样调用校验（一行），或在 subset 文档显式登记「非聚合 TUMBLE = 事件时间标注、窗口时长不消费」语义；倾向前者（fail-fast 纪律统一）。

## 9. 结论与翻转解锁

**最终裁定：PASS。** WI17 完成判定（roadmap 行：「EQL AST 编译为 D8 选定形态，WHERE 与投影走 filter 与 map 内联 xpl，聚合落 aggregatorRef 与 join 落 joinRef 面；宿主模块按 D13；产物通过既有 xdef 校验与 builder，错误码映射 stream 码」）全部成立——其中持续聚合按 r2 B3 裁定走 map+keyBy+reduce 持续管线而非 `<aggregate>` 节点（B1 复合条目经 resolver 服务窗口路径），属裁定级形态而非偏离；错误码零新增码 + ORDER BY/LIMIT 钉死 `nop.err.eql.dialect-not-support-feature`。

**翻转条件已满足，收口动作清单（Phase 4，由实现侧执行）**：

1. roadmap WI17 行 `todo` → `done`（括注单层一对：承载 plan + 本 audit）；随后解析器断言 items=31 工作项（解析态 38=31+7 里程碑）、milestones=7、WI17=done、退出码 0。
2. plan 25：Phase 4 勾选、Plan Status → `completed`、Closure Status Note/Reviewer/Evidence 回填（evidence 引用本报告 + 五模块实跑计数）、Closure Gates 10 项逐项勾选；`check-plan-checklist.mjs 25-wi17-sql-compiler.md --strict` exit 0。
3. `check-doc-links.mjs --strict` exit 0（本轮已 0，回填后复跑）。
4. MIN-2/3/5 的收口修正随翻转提交或紧随 chore（UNION bag 一句入 compiler-contract §2.2；ORM 通道与反写 ON 两条测试钉；plain TUMBLE INTERVAL 校验一行或文档登记）；MIN-1 invariants 重钉走独立 chore；MIN-4 注释顺手修。
5. B4 裁定的 compiler-contract §2.2 回写已随实现提交落档（本轮复核相符），r2 五项裁定无需再回改 roadmap/设计文档。

---

### 附：审计探针与证据清单（审计后 `_tmp/audit-wi17/` 清除，不留仓库残留）

- 实跑日志：`test-{eql,orm,core,flow,sql}.log`（五模块全量）；`invariants.log`/`invariants-sync.log`/`inv-stdout.log`/`inv-stderr.log`；`hollow.log`。
- 反事实 worktree：`a8957472b1`、`fe091bb2c6` 各复跑 invariants（exit 1 同构，证明既有漂移）——已 remove。
- 解析器断言：`parseRoadmapMarkdown` → items=38（31+7）、done=24、WI17=todo、progress=0.77。
