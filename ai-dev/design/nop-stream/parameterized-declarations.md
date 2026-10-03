# parameterized-declarations——参数化声明面总纲（WI8b/WI8c/WI8d）

> Status: active
> 本文覆盖：schemas/aggregators/joins 三注册表的形状与校验义务、SPI 消费模式、Nop 模块发现机制的布局约束、D13 回改裁定的落点结论。
> 读者：WI17（SQL 编译器产出消费这些声明面）、WI21（注册与接线不变量核对）、WI24（兼容迁移说明）。
> 实现锚点：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`（三注册表声明面）；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/`（builder 校验 + SPI 接口 + 生成模型）；`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/`（eval 求值器 + resolver）与 `src/main/resources/_vfs/nop/stream-sql|stream/sql/`（模块发现布局）。
> 相关裁定：`sql-compiler-contract.md` §3.2（D13 回改注记）、`sql-landing-decision.md` §4（回改执行 + IJoinResolver 作废）、WI9 plan（聚合求值器）、`ai-dev/backlog/nop-stream-sql-roadmap.md`（A4 全序、A5）。

## 1. 定位

D14 裁定 (a) 参数化算子面后，stream 模型获得三个注册表声明面（原 `<schemas>`/`<coders>` 等 top-level 注册表为 fail-fast 空面）。本档是三者的总纲：形状契约、校验义务、消费路径，以及承载它们的模块布局机制。

### 1.1 落点决策（D13 回改裁定，2026-10-02）

声明面落 **base stream.xdef**（nop-kernel/nop-xdefs），不落新模块 delta schema。理由：flow 的 typed 模型类由其构建期 codegen 渲染 base xdef 生成，xdefs jar 是全仓唯一 schema 来源——delta 路径经实验证实无法让 typed 模型携带新字段（实验证据见 sql-compiler-contract.md §3.2 回改注记）。新模块（nop-stream-sql）承载**消费资产**（求值器与 resolver），经 SPI 由 flow 的 builder 查找。

## 2. 三注册表形状契约

| 注册表 | key-attr | entry 字段 | 构造期校验（fail-fast） | 消费方 |
|---|---|---|---|---|
| `<schemas>` | id | field(name/type/nullable/defaultValue)，type 为九受管类型名闭集 | 未知类型名首错即抛（`nop.err.stream.invalid-arg`，定位含 schema/field/type）；类型严格解析为 BasicTypeInfo 九实例 | WI8b StreamSchemaRegistry（builder build 链消费）；WI17 编译器列绑定 |
| `<aggregators>` | aggregatorId | fnId（WI9 五 id 闭集对齐 grammar）/expr（WI9 标量子集）/schemaId（可选，列类型来源） | 六项：未知 aggregatorId（ref-unknown）/未知 fnId/expr 编译失败/参数个数/参数类型（sum-avg 数值、min-max Comparable、count 任意、Unknown 跳过）/无 resolver provider（点名 nop-stream-sql） | buildAggregate 恰一分支经 IAggregatorFunctionResolver SPI |
| `<joins>` | joinId | joinType（INNER/LEFT/RIGHT/FULL）/leftKeyExprs/rightKeyExprs/windowStrategyRef/timeout | 八项：未知 joinRef/joinType 缺失/键集缺失/键数不等/恰两条上游边（按声明边计数）/HASH 边禁入/windowStrategyRef 命中且窗口限 INNER-LEFT/timeout 依赖窗口+格式 | WI13 buildJoin（运行时占位至 WI13 交付） |

共同契约：
- entry 是**纯参数描述符**，不是 bean——A4 全序（beanResolver.contains(fnId) 前置、目录兜底）只在显式声明的解析点执行。
- **恰一裁定**（aggregate 的 bean/aggregatorRef）与**上游基数**（join 恰两条边）类约束全部落 builder 构造期，构建期首错即抛。
- registerStreams 类重复键一律消歧或 fail-fast，不静默覆盖。

## 3. SPI 消费模式

- flow 定义 SPI 接口（`IAggregatorFunctionResolver`：fnId + expr + 列类型函数 → AggregateFunction），实现落在 nop-stream-sql（复用 WI9 目录/编译器，**不复制聚合语义**）。
- builder 查找全序：`BeanContainer.isInitialized()` 护栏（未初始化视为无 provider）→ `tryGetBeanByType(SPI)`（缺失返回 null，不用会抛的 getBeanByType）→ null 即 fail-fast `nop.err.stream.invalid-arg`，ARG_DETAIL 点名 classpath 缺 nop-stream-sql。
- joins 无 SPI（IJoinResolver 作废，见 sql-landing-decision §4.1 注记）：join 无构建期函数解析需求，运行时求值由 WI13 直接消费 WI9 编译器。

## 4. 模块发现布局约束（实测机制）

app-beans 自动装配经 Nop 模块机制，两个硬约束（实测于 WI8c）：

1. **模块发现通配是 `*/*/_module`（恰好两段）**——`*` 不跨段；三段目录（如 nop/stream/sql/_module）不被发现。
2. **moduleId 往返必须命中**：moduleName 由路径前两段合成（`/`→`-`），查 beans 时按 moduleName 还原（`-`→`/`）——**目录段含连字符（如 stream-sql）会被往返拆裂**（nop/stream-sql → moduleName nop-stream-sql → moduleId nop/stream/sql）。

因此 nop-stream-sql 的布局为双标记：

```
_vfs/nop/stream-sql/_module          ← 两段，模块发现入口（moduleName=nop-stream-sql）
_vfs/nop/stream/sql/_module          ← 三段，moduleId 往返命中点（loadModuleById 落点）
_vfs/nop/stream/sql/beans/app-*.beans.xml  ← app-beans 装载查询路径（getModuleAppResources）
```

其它约束：
- `_vfs` 必须在 `src/main/resources` 下（模块根 `_vfs` 不参与 Maven 打包，且会被 DeltaResourceStoreBuilder 当作 override-fs-dir 误用）。
- app-beans 文件命名必须为 `app.beans.xml` 或 `app-*.beans.xml`（`isAppBeans` 过滤）。
- SPI 自动装配的证明义务：全量 CoreInitialization 后按 type 查找非空（测试 `TestStreamAggregatorFunctionResolver.resolverAutoAssemblesThroughModuleMechanism` 钉住）。

## 5. 边界与义务归属

| 项 | 归属 |
|---|---|
| coders/sideInputs/requirements/checkpointParticipants 注册表消费者 | FU-3 |
| join 运行时消费 joinRef | WI13（已交付） |
| 编译器产出这些声明面（SQL → stream.xml） | WI17 |
| 声明面变更的用户兼容迁移说明 | WI24 |
| xdef/dialect 声明面的 delta 扩展机制 | 已裁定不可行（D13 回改），如需重启须先改 contract §3.2 |

## 6. 注册表进入 StreamComponents（WI21 落地）

三注册表（aggregators/joins/schemas）的声明在 core `StreamComponents.declarativeRegistries` 有注册条目：flow 模型对象在 builder 内规范化为 flow-free 的 `StreamComponentEntry`（registry/id/attributes，attributes 为描述性键值，不含可执行语义），经 `StreamExecutionEnvironment.declareRegistry` 累积、合并进 env 物化的每个 StreamModel（buildStreamModel 与 graph 附着模型两处同源）。合并点口径：graph 附着模型的 requirements 填充（StreamGraphGenerator.detectRequirements）保持既有路径不动；纯 DataStream API 路径（无 DSL 声明源）的注册表为结构性空，不强行穿线。schemas 条目是 WI8b 授权声明面的注册实现，00-vision §六 #1 授权原文仅点名 aggregators/joins，延伸授权在 WI21 收口时回写注记。

§八 12 的运行时二道校验（WI21）：门 A——core execute() 在图构建后检测 TWO_PHASE_COMMIT_SINK requirement 而 checkpoint 未声明即 fail-fast（2PC sink 无 checkpoint = 静默不提交）；门 B——runtime 图模型 checkpoint 执行器（execute 与 savepoint 两个入口）在解引用 config 前校验 2PC 图必须携带 CheckpointConfig。RemoteTaskDeploySupport 部署路径显式降级：deploy 侧无 CheckpointConfig 访问权（该类 javadoc 在案），不发明 descriptor 扩展，留 owner 裁定。
