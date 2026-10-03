# WI8c Closure Audit——15-wi8c-parameterized-aggregate.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**FAIL（有条件，单项补救）**——机制面、声明面、恰一裁定、SPI 全序、模块自动装配、两模块实跑（sql 34 + flow 139）全部成立；唯一阻塞项：Phase 2 Exit Criterion「逐项 fail-fast（…/无 provider）各有具名测试」已勾选，但 **「无 resolver provider」分支在两个模块内均无具名测试**（六项仅 5/6 有具名测试），与 Closure Gate「resolveAggregator 六项 fail-fast 各有具名测试」不符。补一个具名测试后可达 PASS（见 §4 M-1）。

## 1. 逐条审计核验（对应审计指令 1-11）

### 1.1 xdef 声明面（审计项 1）——PASS

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef` 实读：新增顶层 `<aggregators xdef:key-attr="aggregatorId" xdef:body-type="list">`（entry：`aggregatorId="!string"`、`fnId="!string"`、`expr="string"`、可选 `schemaId="string"`、`xdef:unique-attr="aggregatorId"` + `<description>`），与既有 `<windowingStrategies>`（:48-55，key-attr + unique-attr + description）逐属性同构；`<aggregate>` 增 `aggregatorRef="string"`（可选，无 `!`），既有 `bean="bean-name"` 在共享 `xdef:define StreamTransformModel`（:116-119）中本就可选——xdef 层二者均可选，恰一约束落构建期，与裁定一致。
- `_gen` 重生成证据（git diff 实读，非手改痕迹）：`_StreamAggregatorModel.java`（新增）类 javadoc 为 `generate from /nop/schema/stream/stream.xdef`，五个字段（aggregatorId/description/expr/fnId/schemaId）逐一携带 `xml name:` 注释，字段集与 xdef entry 属性一一对应；`_StreamAggregateModel.java` 新增 `aggregatorRef` getter/setter/outputJson/copyTo 且 javadoc 原样携带 xdef 新增的 WI8c 中文注释；`_StreamModel.java` 新增 `KeyedList<StreamAggregatorModel> _aggregators` + `getAggregators()/setAggregators()` + **key-attr 查找器 `getAggregator(String name)`**（与 windowingStrategies 的 `getStrategy` 同一生成模式）。三处均为典型 codegen 产物形态。
- wrapper：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/StreamAggregatorModel.java` 为非下划线手写保留文件（空构造 + javadoc 声明 retention 模式，同 `WindowingStrategyModel` 先例）——合法，不违 _gen 纪律。

### 1.2 恰一裁定（审计项 2）——PASS

- `AdvancedTransforms.buildAggregate`（git diff + live 实读）：`(m.getBean() == null) == (m.getAggregatorRef() == null)` 双缺与并存统一抛 `ERR_STREAM_INVALID_ARG`（ARG_DETAIL 明示 "exactly one of bean or aggregatorRef, found neither/both"），取代原 `ERR_STREAM_REQUIRED_ATTR`；bean 分支 `owner.resolveBean(t, m.getBean(), AggregateFunction.class)` 原样保留，随后 `windowed.aggregate(fn)` 零变化。
- 上游顺序前提遵守：`requireSingleInput` → WindowedStream 类型检查先于恰一检查——三个恰一用例均带 `<keyBy>` + `<window strategyRef>` 上游（`aggregateWithoutBeanOrRefFailsFast`/`aggregateWithBothBeanAndRefFailsFast`/`aggregateUnknownAggregatorRefFailsFast`，TestAdvancedTransforms 实读）。
- bean-only 回归：`aggregateDispatchesToWindowedAggregatePath`（bean 路径既有测试）未改动且随 flow 全量 139 绿实跑通过；改名仅 `aggregateWithoutBeanFailsFast` → `aggregateWithoutBeanOrRefFailsFast`（语义即裁定本身）。

### 1.3 aggregatorRef 全序（审计项 3）——PASS

`resolveAggregatorRef`（AdvancedTransforms live 实读）逐环核验：

1. **entry 查找**：`owner.model().getAggregator(m.getAggregatorRef())`（key-attr 生成查找器）→ null 抛 `ERR_STREAM_REF_UNKNOWN`（ARG_REF_TYPE=`<aggregators>/<aggregator>`、ARG_REF_NAME 点名）；
2. **A4 bean 前置**：`owner.beanResolver().contains(fnId)` 命中即 `owner.resolveBean(t, fnId, AggregateFunction.class)`——`BeanFunctionResolver.contains` 接口实存（BeanContainerFunctionResolver/GlobalBeanFunctionResolver/InMemoryBeanFunctionResolver 三实现，:39/:54/:35 实读），忠实 A4 全序；
3. **BeanContainer 护栏**：`!BeanContainer.isInitialized()` 先行（避免 instance() 抛 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED）；
4. **按 type 可选查找**：`BeanContainer.instance().tryGetBeanByType(IAggregatorFunctionResolver.class)`（缺失返回 null，非 getBeanByType 抛错语义）；
5. **委托/fail-fast**：命中委托 `resolver.resolve(fnId, expr, columnTypeLookup(schemaId))`；null 走 `noProvider` → `ERR_STREAM_INVALID_ARG` + ARG_DETAIL 点名 `nop-stream-sql` classpath 依赖。

`columnTypeLookup`（StreamModelDslBuilder :726-743）：null/未知 schemaId → 恒 null 函数（Unknown 跳过语义的 flow 侧源头）；已知 schemaId → 经 `StreamSchemaRegistry.resolveSchemas(model).resolveSchema(schemaId)` 构建 name→BasicTypeInfo map（WI8b FieldSpec 桥接）。

### 1.4 SPI 实现语义（审计项 4）——PASS

`StreamAggregatorFunctionResolver.resolve`（live 实读）全序与 plan 契约逐项一致：fnId 经 `StreamSqlAggregations.resolve` 未命中抛 invalid-arg（点名 builtinIds）→ expr 非空经 `EqlExprSupport.parseExpr` + `StreamSqlExprCompiler.compileScalar`，失败 wrap `ERR_STREAM_INVALID_ARG`（ARG_ARG_NAME=expr）→ 空表达式且 `!allowsNoArg()` 抛 arity invalid-arg（count 0..1 其余恰 1）→ `validateArgType`（sum/avg 数值六型、min/max 数值∪STRING∪BOOLEAN Comparable、count 任意早退、null/`UnknownTypeInformation` 跳过不误杀）→ `spec.create(evaluator)`。

**累加 100% 来自 WI9**：grep 实证 resolver 内零 `createAccumulator/add/getResult/merge` 自行实现（唯一命中为 javadoc 引用），返回值即 `StreamSqlAggregation.create(evaluator)` 的 WI9 累加器；flow 侧 SPI 接口 javadoc 亦明文「only parses and assembles, never re-implements aggregation」。`countWithoutArgumentIsLegal`/`resolvedFunctionCarriesRealSemantics`/`maxOfStringColumnIsLegal` 三用例实际驱动 add/getResult 断言 2L/5L/"b"——WI9 语义经 SPI 端到端实症。

### 1.5 模块发现机制 Anti-Hollow（审计项 5）——PASS

- 布局实读：`nop-stream-sql/src/main/resources/_vfs/nop/stream-sql/_module` + `_vfs/nop/stream/sql/_module`（双 `_module` 空标记文件，与 runtime `_vfs/nop/stream/_module` 单标记惯例对照——两段发现入口 + moduleId 连字符往返命中）+ `_vfs/nop/stream/sql/beans/app-aggregator.beans.xml`（`nopStreamAggregatorFunctionResolver` bean，class 指向 SPI 实现）；模块根旧 `_vfs` 已删（git staged deletion `nop-stream/nop-stream-sql/_vfs/nop/schema/.gitkeep`），模块根现仅 pom.xml/src/target。
- **实测**：`./mvnw test -pl nop-stream/nop-stream-sql -Dtest=TestStreamAggregatorFunctionResolver` 实跑绿；`resolverAutoAssemblesThroughModuleMechanism` 为 `CoreInitialization.initialize()` **全量**初始化（非 initializeTo(IOC-1)）后 `BeanContainer.instance().tryGetBeanByType(IAggregatorFunctionResolver.class)` assertNotNull + instanceof 断言——app 容器按模块机制装载 app-beans 的真实证明，非手工容器。测试 `resolver()` 辅助方法同样从容器按 type 取，后续 9 个语义用例全部消费同一自动装配实例。pom 仅增 `nop-ioc`(test) + `h2`(test) 两依赖支撑全量启动（D15 H2 默认），diff 干净。

### 1.6 实跑（审计项 6）——PASS

| 命令 | 结果 |
|---|---|
| `./mvnw test -pl nop-stream/nop-stream-sql` | `Tests run: 34, Failures: 0, Errors: 0`（11 Aggregations + 13 Compiler + **10 AggregatorFunctionResolver**），BUILD SUCCESS |
| `./mvnw test -pl nop-stream/nop-stream-flow` | `Tests run: 139, Failures: 0, Errors: 0`，BUILD SUCCESS（含 TestAdvancedTransforms 28、TestParameterizedAggregateModel 1） |

### 1.7 测试判别性抽查（审计项 7）——部分 PASS（M-1 阻塞项在此）

| 语义点 | 断言 | 判别性 |
|---|---|---|
| SPI 真实被调用 | `TestParameterizedAggregateModel`：beans.xml 注册 `CountingFakeResolver`，`CALLS.incrementAndGet()` 于 resolve 内，build 抛 runtime-gap 后 `assertEquals(1, CALLS.get())` + fnId/expr 精确断言 | 强：counter 只能经真实构建链路递增；runtime-gap 断言证明 builder 携带解析结果越过解析点 |
| 恰一码串钉住 | 双缺/并存 `nop.err.stream.invalid-arg` + "exactly one of bean or aggregatorRef"；未知 ref `nop.err.stream.ref-unknown` + 点名 "missing" | 强 |
| bean 缺失旧码消灭 | 改写用例断言 invalid-arg（原 required-attr 断言已被替换） | 强 |
| 未知 fnId | sql `unknownFnIdFailsFast` 码串 + "median" 点名 | 强 |
| expr 编译失败 | `exprCompileFailureFailsFast`（CASE 子集外）码串 | 强 |
| arity | `missingArgumentOnNonCountFailsFast`（sum null）码串 + "exactly one argument"；`countWithoutArgumentIsLegal` 正例对照 | 强 |
| 类型不符 | `sumOfNonNumericColumnFailsFastWithSchema`（STRING 列 sum）码串 + "numeric"；`maxOfStringColumnIsLegal` 正例对照；`unknownColumnTypesSkipTypeCheck` 跳过语义 | 强（resolver 层；见 M-2） |
| 对齐钉子 | `builtinIdsAlignWithGrammarKeywords` assertEquals 闭集 {sum,count,avg,min,max}（BaseRule.g4 :273 五关键字对齐在 WI9 audit 已核，小写归一与 parser 一致） | 强 |
| **无 provider** | **无具名测试**（两模块 grep 实证：flow 测试中唯一合法 aggregatorRef 用例注入了 fake resolver；sql 侧恒有 provider） | **缺失，见 M-1** |

### 1.8 生成物纪律（审计项 8）——PASS

`git status` 全量实读：改动面 = stream.xdef（源模型）、flow 两个 builder、`_gen` 三文件（二改一增，均为重生成产物）、flow 测试两文件 + 测试资源两个、sql 模块（pom/src 全新 + 旧 `_vfs/.gitkeep` staged 删除）、ai-dev 四文档 + 新 plan。**零手改既有 `_` 前缀生成物**（两个修改的 _gen 文件内容均为 xdef 派生形态，见 §1.1）；wrapper 非下划线合法。

### 1.9 三文档回改注记一致性（审计项 9）——PASS

四处 diff 实读：

- `sql-compiler-contract.md` §3.2 末新增「回改裁定执行（WI8c，2026-10-02）」段：delta 两轮实验证据 + 回改执行（声明面落 base stream.xdef、消费面经 SPI、D13 (a) 不变）；
- `sql-landing-decision.md` §4 条目 1 增同款注记（含「本条『不直改 nop-kernel/nop-xdefs』的前半句自此修订」+ WI8d IJoinResolver 同构预告）；
- roadmap D13 行改写为「声明面落点经 WI8c 回改裁定（2026-10-02）落 base stream.xdef、消费面经 SPI 由新模块承载」；
- roadmap Cross-Cutting 4 条目 WI8c 括注「（执行 2026-10-02：落 base stream.xdef——D13 回改裁定……）」，WI8d 同步括注。

四处口径一致（base xdef 落声明面 + SPI 承载消费面 + D13 (a) 方向不变 + delta 不可行结论同源），无相互矛盾。

### 1.10 工具门禁（审计项 10）——PASS（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 warnings（均为 nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-flow --severity high` | 0 | Critical/High/Medium/Low 全 0 |
| `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-sql --severity high` | 0 | 全 0 |
| `node ai-dev/tools/check-nop-stream-invariants.mjs sync` | 0 | `sync: OK` |
| `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs 实跑） | — | **items 31 + milestones 7，done 14，无静默丢弃**；WI8c 行仍 `todo`（预期，audit 未过不翻转）；D13/CC4 行为表格注记列非状态 token，不受 BULLET_RE 影响 |
| `check-plan-checklist --strict`（备案） | 0 | 非 completed plan 且 Phase 3 未勾 → warnings only，符合收口前预期态 |

### 1.11 plan 文本一致性（审计项 11）——**一项不符（M-1）**

Phase 1 四项勾选全部 live 落地（三文档注记/xdef/重生成+wrapper/日志条目）。Phase 2 五项勾选中四项全部 live 落地；唯一不符：Exit Criterion「逐项 fail-fast（未知 aggregatorId/未知 fnId/expr 失败/参数个数/参数类型/无 provider）各有具名测试」——**「无 provider」无具名测试**（六项 5/6）。Phase 3 未勾、Closure Gates 未勾属预期（本 audit 即其第一项）。

## 2. 实跑证据汇总

- `./mvnw test -pl nop-stream/nop-stream-sql`：34/34 绿（exit 0）
- `./mvnw test -pl nop-stream/nop-stream-flow`：139/139 绿（exit 0）
- 五个门禁工具退出码全 0；roadmap 解析 31+7 实测

## 3. Minor 发现

- **M-1（唯一阻塞项）**：`noProvider` fail-fast 分支（BeanContainer 未初始化 / `tryGetBeanByType` 返回 null → `ERR_STREAM_INVALID_ARG` 点名 nop-stream-sql 依赖）无具名测试。代码实读确认逻辑正确，但 plan Phase 2 Exit Criterion 已勾「各有具名测试」构成「已勾选未做（部分）」，且 Closure Gate「resolveAggregator 六项 fail-fast 各有具名测试」要求六项齐备。**补救（最小）**：TestAdvancedTransforms 增一用例——`CoreInitialization` 后 flow 测试类路径无 sql 模块（无 provider bean），声明合法 aggregatorRef（entry + fnId=sum + keyBy/window 上游）断言 invalid-arg 且 message 含 `nop-stream-sql`；随后重审计仅复核该项。
- **M-2**：schemaId → `columnTypeLookup` → SPI 类型校验的 flow↔sql 端到端链路无单一用例覆盖（sql 侧类型不符用例以 `columns::get` 模拟列类型函数；flow 侧 fake resolver 未断言 columnTypes）。两半各自有测试（WI8b StreamSchemaRegistry 5 用例 + sql resolver 类型用例），桥接为薄 map 构建，风险低；建议补一个声明 `schemaId` + 非数值列的端到端用例（可与 M-1 同批）。
- **M-3**：`validateArgType` 对 expr 二次 `parseExpr`（resolve 主体已 parse 一次）；`columnTypeLookup` 两次调用 `StreamSchemaRegistry.resolveSchemas(model)`。纯效率问题，无正确性影响。
- **M-4**：`resolveAggregatorRef`/`noProvider` 内 `io.nop.api.core.ioc.BeanContainer` 以全限定名内联而非 import（两处）；风格级，不影响语义。

## 4. 无静默跳过检查

新增主代码 4 文件（IAggregatorFunctionResolver / StreamAggregatorFunctionResolver / StreamAggregatorModel wrapper / columnTypeLookup）+ buildAggregate 改动：grep TODO/FIXME/XXX 零命中；无空方法体；无吞异常（compile 失败 wrap 后重抛保留 cause）；所有失败路径显式抛 `StreamException` 码串。

## 5. 结论

WI8c 的声明面（base xdef 同构注册表）、typed 模型重生成管线、恰一裁定、aggregatorRef 五环全序、SPI 语义契约、模块 app-beans 自动装配（全量初始化实症）与文档回改四处注记全部在 live repo 成立；sql 34 + flow 139 实跑绿；门禁全 0；roadmap 31+7 解析无丢弃；_gen 纪律干净。

**裁定 FAIL（有条件）**：唯一阻塞项为「无 provider」fail-fast 缺具名测试（§3 M-1），与已勾选的 Phase 2 Exit Criterion 及 Closure Gate「六项各有具名测试」不符。补齐该单个用例（建议连同 M-2 的 schemaId 端到端用例）并复跑 flow 全量后，本审计其余全部结论继续有效，可达 PASS 并进入 Phase 3 剩余收口（roadmap WI8c → done 单层无嵌套、parseRoadmapMarkdown 31+7 复核、plan Closure 段落、check-plan-checklist --strict）。
