# EQL `IN` 列表长度上限策略设计（WI10a）

**日期**：2026-10-03
**范围**：`nop-persistence/nop-orm-eql`（`SqlInValuesExpr` 编译/打印路径）、`nop-kernel/nop-core`（`SQL.SqlBuilder` 集合展开路径）、`nop-persistence/nop-dao`（方言扩展点先例）；策略实现归属 WI10b
**状态**：active（定稿）—— 2026-10-03 经独立子 agent 对抗性审查 **approve-with-nits**（核心五项决策全部成立、代码事实 22 处零失真），审查发现的 2 Major + 6 Minor 已于同日定稿轮全部处置；作为 WI10b 实现的约束基准
**来源**：roadmap WI10a（`ai-dev/backlog/eql-cross-db-compat-roadmap.md`「WI10a / WI10b」节）；G-1 已裁决（`2026-10-03-wi0-gate-adjudications.md`，unverified 边界维持 unverified 纪律）

---

## 一、设计结论

1. **统一阈值 1000，全方言一致**：默认上限取 1000，唯一硬依据是 Oracle `IN` 表达式列表上限 1000（`ORA-01795`）。不按方言设不同阈值——除 Oracle 外没有任何方言取得一手引证的上限，per-dialect 取值是伪精度（见 §3.1）。
2. **超阈值行为 = 显式报错，不是改写**：EQL 路径在编译期报错，`SqlBuilder` 集合展开路径在 SQL 构建期报错；同一契约（模块自有错误码 + actual/max 参数 + 语句名），禁止静默截断。分批 OR / 临时表 / 按方言分化均被拒（见 §3.4、§四）。该行为是**有意的破坏性变更**，裁定与迁移姿态见 §3.2。
3. **检查点在编译/构建管线，不在打印机**：`toSqlString()` 等回显路径保持"忠实转储、零截断"不动——门禁不得引入非预期的回显变更（WI1/WI2 对回显的有意修正走语料基线再生成，与本门禁无关）；同时回显必须保持可用，让用户能 dump 出触发门禁的完整 SQL 用于诊断。门禁加在决定"这条 SQL 将要被执行"的管线上（EQL 编译 / SQL 构建），执行必然先过门禁（见 §3.4）。
4. **v1 不新增方言可插拔公共接口**：`IPaginationHandler` 先例证明了升级路径，但"没有第二个实现者的公共接口"违背本仓已确立的纪律（`IUpsertHandler` 2026-08-22 删除先例）。检查点实现为单一覆写点 + 全局可配置阈值；当且仅当出现 ≥2 个方言需要不同决策（报错 vs 改写 vs 不同阈值）时，才按 pagination 先例升级为 `dialect.xdef` 元素 + XML class-name + `DialectImpl.newInstance` 默认回落的可插拔 handler（见 §3.5）。
5. **临时表形态被拒**：其生命周期与事务语义要求（全局/会话临时表分叉、`ON COMMIT DELETE/PRESERVE ROWS` 分叉、回滚行为、连接池复用残留、清理时机、DDL 供给方）每一条都需要逐方言真库取证，而今天连"是否需要改写"这个前提都不成立。§3.6 保留完整分析作为将来重提此形态必须先回答的问题清单。

## 二、背景与动机

`IN` 列表长度上限是整个 EQL 跨库兼容 roadmap 唯一"日常 ORM 查询就会踩到"的硬失败类别：列表元素个数没有上界、SQL 文本随之无界增长，而失败只在真库执行时才暴露（且报错形态因方言而异）。

### 量化现状（代码锚点于 2026-10-03 在本工作区逐文件复核）

- **零截断**：`AstToEqlGenerator.visitSqlInValuesExpr`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToEqlGenerator.java:802-819`）把 `node.getValues()` 交给 `printList(values, ",")`，而 `printList`（同文件 `:212-223`）是裸 for 循环——不做任何截断、分批或阈值判断。roadmap WI10a 节实测：10000 个 5 位数字面量 `toSqlString()` ≈ 13.0 万字符。
- **执行路径与回显路径共享同一段无界打印**：`AstToSqlGenerator`（同目录 `AstToSqlGenerator.java:67`）`extends AstToEqlGenerator` 且**没有**覆写 `visitSqlInValuesExpr`（该类内 `printList` 唯一消费点是投影列表 `:119`）。⇒ `EqlCompiler.java:73` 产出的执行 SQL 与 `EqlASTNode.toSqlString()` 回显走的是同一个继承方法，字面量全部内联进 SQL 文本。
- **回显硬编码无 pretty 开关**：`EqlASTNode.appendTo`（`ast/EqlASTNode.java:26-29`）硬编码 `new AstToEqlGenerator(sb)`，`toSqlString()`（`:31-35`）只是委托——回显语义是"忠实转储"，本设计据此把门禁放在管线而非打印机。
- **绑定参数机制存在但不对 IN 列表做集合展开**：见 §3.3。

## 三、核心设计

### 3.1 阈值来源（五要素之一）

| 方言 | 上限 | 证据等级 | 对本策略的意义 |
|---|---|---|---|
| Oracle | `IN` 表达式列表 1000（`ORA-01795`） | **硬依据**（roadmap WI10a 节已裁决的唯一硬锚点；一手出处为 Oracle Database Error Messages 的 ORA-01795 条目） | 阈值取值来源 |
| MSSQL | 2100 = "Parameters per stored procedure / user-defined function" | Microsoft Maximum Capacity Specifications 原文；**对字面量内联路径不构成约束**——EQL 生成的是纯文本 SQL，不发送参数 marker（见 §3.3） | 不作为字面量路径的阈值依据；对绑定参数路径的意义见 §3.3 |
| MySQL / MariaDB / PostgreSQL / H2 / DuckDB / SQLite / DB2 / TDengine | 未取得一手引证 | **unverified**（DM8、TDengine 按红线本就无一手文档） | 不支撑任何 per-dialect 阈值 |

**决策**：统一默认 1000。理由：

1. 只有一条硬依据，就只按硬依据取值——多方言差异没有证据支撑，写进方言 XML 是伪精度。
2. 超长 IN 列表在任何方言上都是反模式（解析/优化成本随长度增长、执行计划缓存污染），"Oracle 会失败、别的库勉强能跑"不是保留它的理由。
3. 统一阈值让契约可文档化、可测试：一条规则、一个错误码、一份 owner doc 表格。

**阈值可配置**：以命名常量为默认、经全局配置项允许部署方调高。这是给"部署知道自己的数据库变体"留的出口（例如代理/分叉方言上限不同），不是给应用代码绕过门禁的出口；不提供"关闭检查"的取值，配置调高即部署方自担其数据库的证据责任。**不做方言 XML 属性**——理由同上（无 per-dialect 证据）。⚠️ **配置载体的模块约束**：必须落 `nop-core`（`CoreConfigs`，`varRef` 模式先例 `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java:33`）或 `nop-commons`——`nop-core` 不能反向依赖 `nop-orm-eql`，`nop-dao` 的 `DaoConfigs` 与 `nop-orm-eql` 的 `OrmEqlConfigs` 都无法被两条检查路径（`nop-orm-eql` 编译路径与 `nop-core` 的 `SqlBuilder` 展开路径）同时读到（详见 §3.5）。

### 3.2 破坏性变更裁定与迁移姿态

**裁定：这是有意的破坏性变更。** 今天在 MySQL / PostgreSQL 等没有 1000 硬限的方言上正确可跑的 >1000 元素 `IN` 查询，升级后将在编译期（EQL 路径）或 SQL 构建期（filter 展开路径）硬失败。接受这一行为不是疏忽：这类查询正是本策略要拦截的对象——超长 IN 列表在无硬限方言上同样是解析/优化成本与计划缓存反模式（§3.1 决策理由 2），而"同一个查询换个库就从合法变非法"的静默差异正是本 roadmap 要消灭的可移植性缺陷。

**缓解手段**（按优先级）：

1. **全局配置调高阈值**（§3.1 的部署出口）：升级前知悉自身工作负载的部署把阈值调到覆盖既有合法查询，即可零代码迁移。
2. **报错自带定位信息**：错误 param 携带实际元素数、阈值与语句名，一次报错即可完成定位与改写评估（用 `toSqlString()` dump 完整 SQL，见 §3.4）。
3. **应用侧分批**：>1000 元素的列表由调用方拆分为多次查询或分批 OR（§3.4 后备形态的语义）。

**过渡期 warn-only：否，显式拒绝。** 理由：

1. roadmap 排序原则"只打印不设门 = 净回归"——warn-only 把硬失败换成日志噪音后，SQL 照常发往数据库，Oracle 上依旧在运行时失败且更难归因（错误从编译点漂移到执行点）。
2. warn-only 意味着"后续翻硬"的第二次行为变更，破坏性变更分两波发生，迁移说明与回归面反而翻倍。
3. 编译产物在缓存路径下复用（`useCache=true`），warn 日志会随每次重编译重复产生噪音，运维信号比错误更差。

**owner doc 同步清单（WI10b 落地时必含的迁移说明项，对齐 WI9b 的迁移姿态要求——"显式给出迁移说明或显式裁定 No migration required 并写明理由，不接受留空"）**：

- 哪些既有查询会开始失败：>1000 元素的 IN 列表（EQL 编译路径与 filter 展开路径两个面）。
- 升级前评估步骤：部署在预发环境按新错误码完成一次全量回归，盘点命中查询。
- 阈值配置项名称与调高方法（含"调高即自担证据责任"的约定）。
- 迁移说明的落点是 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`（与 §3.3/§五 的 owner doc 同步项合并交付）。

### 3.3 字面量 vs 参数化的取舍（五要素之二）

先回答技术事实——**EQL 今天是否会发参数化 `IN` 列表，何时发**：

1. **文法层**：`sqlInValues_: e=sqlExpr (COMMA_ e=sqlExpr)*`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4:163-164`；`# SqlInValuesExpr` 标签在 `:156`）——IN 列表元素是任意 `sqlExpr`：字面量（`SqlStringLiteral`/`SqlNumberLiteral` 等，打印时内联为文本）或 `:name` 参数（`SqlParameterMarker`）。
2. **参数打印**：`AstToEqlGenerator.visitSqlParameterMarker`（`AstToEqlGenerator.java:488-490`）经 `sb.markValue("?", value, masked)` 输出一个 `?` 并把值登记进 marker 列表；`SQL.getParams()` 即 `getMarkerValues()`（`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java:143`）。⇒ EQL 编译产物 = 带 `?` 占位的 SQL 文本 + 参数列表。
3. **参数个数在编译期固定，无集合展开**：`EqlTransformVisitor.collectNames`（`compile/EqlTransformVisitor.java:220-236`）按 AST 出现顺序给每个 `SqlParameterMarker` 编 `paramIndex`；`SqlParamTypeResolver.visitSqlParameterMarker`（`compile/SqlParamTypeResolver.java:69-130`）为每个 marker 生成恰好一个 `ISqlParamBuilder`。`SimpleParamBuilder.buildParams`（`param/SimpleParamBuilder.java:24-28`）每次绑定恰好 1 个值；`EntityPropParamBuilder`（`param/EntityPropParamBuilder.java:40-89`）对 component/to-one-relation 拆成**固定元数**（由实体模型决定、与运行时数据无关）的多个值。**没有任何路径把一个集合参数展开成 N 个 `?`。** `CompiledSql.buildParams`（`compile/CompiledSql.java:134-137`）与消费点 `JdbcQueryExecutor.java:148` 只按 marker 个数产出绑定值。

**结论**：

- EQL 自身**不会**发"元素个数随运行时数据增长"的参数化 IN 列表。`where o.id in (:a,:b,:c)` 的 marker 个数是编译期常量；`in (1,2,3,…)` 的字面量全部内联。**字面量内联是 EQL IN 列表的既定形态，本设计不改它**——改成"自动参数化"属于另一个维度的行为变更（执行计划、SQL 文本稳定性、BC 语料），且不解决本问题（1000 是 Oracle 对**表达式列表**的硬限，参数化并不能让字面量列表合法）。
- **仓库里真正会发参数化 IN 列表的是 filter-bean 路径**：`FilterBeanToSQLTransformer.visitCompareOp`（`nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/FilterBeanToSQLTransformer.java:167-170`，`FILTER_OP_IN`/`FILTER_OP_NOT_IN`）→ `SqlBuilder.in(owner,name,value)`（`SQL.java:527-546`）→ `paramCollection`（`:724-726`）→ `spreadParams`（`:728` 起）——**运行时集合的每个元素各产出一个 `?` 绑定参数**（`SQL.java:421-433` 的 `param` → `?` + marker），元素个数随数据增长。
- **MSSQL 2100 案例何时回来、约束哪个维度**：本门禁约束的是**单个 IN 列表的元素个数**（阈值 1000）；**语句级参数总数**（一条 SQL 中全部 `?` 的合计个数）**不受本门禁约束**——多个各含 ≤1000 元素的 IN 列表足以让语句级总数超过任何参数上限。MSSQL 的 2100 在 Microsoft 文档中的原文位置是 stored procedure/UDF 参数规格；它对**参数化请求语句级参数总数**的实际约束形态与确切阈值，本设计**未取得一手引证，按 unverified 纪律处理**，列为真库取证项（WI10b 及其后）——取证结论只影响 owner doc 的证据等级列，不改变本门禁行为。

### 3.4 超阈值行为与改写形态（五要素之三）

**推荐方案：显式报错（v1 唯一行为）**

- **EQL 路径**：编译管线对 `SqlInValuesExpr.values` 计数（字面量与 marker 统一计数，不做逐元素类型区分），超过阈值抛 `NopException` + 新错误码（归属 `OrmEqlErrors`，英文消息），`.param(...)` 携带：实际元素数、阈值、语句名（如有）、方言名（仅 EQL 编译路径可提供——`SqlBuilder` 路径无 `IDialect` 上下文，该 param 缺省）。检查点在**编译管线**（缓存路径（`useCache=true`）下检查随首次编译只跑一次；非缓存路径逐次编译确定性重查——检查本身无状态、幂等，两种路径行为一致），不在 `AstToEqlGenerator`/`AstToSqlGenerator` 打印方法内。
- **SqlBuilder 集合展开路径**：在集合展开入口计数并报同一契约的错误（错误码归属 `nop-core` 的 `CoreErrors`——模块边界决定错误码归属，两处不允许共用一个模块的码）。落点（`SqlBuilder.spreadParams` 全局拦 vs `FilterBeanToSQLTransformer` 仅 filter 路径拦）由 WI10b plan 按"影响面 × 防护面"裁定，契约约束不变：不静默、错误码语义一致、默认阈值同一配置项。
- **`NOT IN` 同阈值同行为**：超长 `NOT IN` 的优化器代价与 NULL 三值语义风险比 `IN` 更高，不单独放宽。
- **不提供静默截断、不提供"超限自动改写"开关**。空 IN 列表语义（各路径现有的 `alwaysFalse()`/`(NULL)` 行为）不在本策略范围，维持现状。

**改写形态评估（被拒项的正面分析，供将来重提时引用）**：

| 形态 | 语义影响 | 事务/状态影响 | 工程成本 | 裁定 |
|---|---|---|---|---|
| 编译期/构建期报错 | 无——失败在执行前，信号明确 | 无 | 最低（计数 + 抛错） | **采纳（v1）** |
| 分批 OR（`a IN (…≤1000) OR a IN (…)`） | 对 `IN`/`NOT IN` 在三值逻辑下与单列表等价（NULL 侧 UNKNOWN 的 OR/AND 组合保持一致），但 SQL 文本仍随 N 线性增长（列名逐批重复），只推迟不消除失控 | 无新增状态 | 中（生成器级改写 + 批大小取整 + 各库括号/优先级回归） | **后备形态**：当出现"产品要求在 Oracle 上跑 >1000 元素 IN 且应用侧不分批"的真实需求时，作为可插拔 handler 的第一个第二种实现重提；v1 不做 |
| 临时表（先插入元素集，再 `JOIN`/`IN (SELECT …)`） | 结果等价，但引入跨语句状态 | **重**——见 §3.6 | 高（逐方言 DDL + 生命周期 + 事务语义取证） | **拒绝** |
| 按方言差异化（Oracle 报错、其余放行 / 各库不同阈值） | 契约碎片化：同一 EQL 在不同库一个失败一个放行，行为不可移植 | — | 高（每个格子都要一手取证） | **拒绝**（unverified 纪律：无证据的 per-dialect 差异不得进入方言契约） |

**门禁与回显的关系**：`toSqlString()`/`appendTo` 回显路径不加检查——回显是忠实转储，**门禁不得引入非预期的回显变更**（WI0 语料基线 `ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 的逐字棘轮只应被 WI1/WI2 这类**有意**的回显修正触发并再生成登记，本门禁不得成为棘轮噪音源）。回显同时承担**报错可诊断性**：`toSqlString()` 必须保持可用，让用户能 dump 出触发门禁的完整 SQL 文本用于定位与改写评估。执行必然经过编译/构建管线，门禁放在那里即"执行必过门禁"。

### 3.5 与 `IPaginationHandler` 同类扩展点的关系（五要素之四）

**先例结构**（`nop-persistence/nop-dao`，实测锚点）：`dialect.xdef:25` 声明 `<paginationHandler>class-name</paginationHandler>` 元素；`default.dialect.xml:11`、`mssql.dialect.xml:11`、`oracle.dialect.xml:12` 等按方言配置实现类；`DialectImpl` 构造时 `newInstance(dialectModel.getPaginationHandler(), LimitOffsetPaginationHandler.INSTANCE)`（`DialectImpl.java:89-90`；`newInstance` 反射加载 + 默认回落 `:120-125`）；`IDialect.getPaginationHandler()`（`IDialect.java:52`，实现 `DialectImpl.java:200-202`）暴露给消费方——`AstToSqlGenerator.java:93`（`buildPageExpr`，分页语法形态）、`JdbcTemplateImpl.java:367`、`JdbcHelper.java:190`（真库执行侧 `prepareStatement`）。同构先例还有 `geometryTypeHandler`/`jsonTypeHandler`（`DialectImpl.java:103`，xdef `:28/:30`）。

**为什么 pagination 需要可插拔而 IN 列表 v1 不需要**：

- pagination 的本质是**SQL 形态整形**——每个方言输出结构不同的分页子句（`LIMIT/OFFSET` vs `OFFSET…FETCH` vs 方言私有形态），实现天然 per-dialect 分叉，且分叉点从第一天就存在（至少两种实现同时在用）。
- IN 列表上限的本质是**一个决策**（报错 vs 放行 vs 改写），今天全方言的正确决策相同（报错），没有第二种实现者。
- 本仓已确立的纪律：没有第二个消费者的 `nop-dao` public 接口不进 API 面（`IUpsertHandler`/`MysqlUpsertHandler` 2026-08-22 删除先例，roadmap WI11 行引用的 `ai-dev/audits/check/nop-dao.md:415`）。新增 `nop-dao` public 接口属「AI Must Ask Before」第 1 条。

**决策**：v1 把检查实现为编译/构建管线内的**单一覆写点**（不新增公共接口），阈值经全局配置暴露。**配置载体的模块约束**：必须落 `nop-core`（`CoreConfigs`）或 `nop-commons`——依赖方向是 `nop-orm-eql` → `nop-core`（nop-core 不能反向依赖 nop-orm-eql），因此 `nop-dao` 的 `DaoConfigs` 与 `nop-orm-eql` 的 `OrmEqlConfigs` 都无法被两条检查路径（`nop-orm-eql` 编译路径与 `nop-core` 的 `SqlBuilder` 展开路径）同时读到；`CoreConfigs`（或 nop-commons）是两者共同可见的最底层。**升级触发条件（写入本设计作为契约）**：当出现 ≥2 个方言需要不同决策（例如某方言需要分批 OR 改写、或需要不同默认阈值）时，按 pagination 先例升级为 `dialect.xdef` 新元素 + 方言 XML class-name 配置 + `DialectImpl.newInstance` 默认回落的可插拔 handler；升级只改变实现的挂载方式，不改变错误契约（错误码、param 集合、默认阈值来源）。WI10b 若在实现中发现单一覆写点无法覆盖两条路径（EQL 编译在 `nop-orm-eql`、`SqlBuilder` 在 `nop-core`，两模块方向是 orm-eql 依赖 nop-core），允许按同一契约在两处各设检查点——这不是两个策略，是同一策略的两个执行面。

### 3.6 临时表生命周期与事务语义（五要素之五；形态被拒，分析保留为重提前置清单）

临时表形态被拒（§3.4），但它出现在 roadmap 点名的五要素里，因为它不是"想一下就能否决"的形态——以下问题清单是**将来重提此形态时必须逐条取得真库证据才能开工**的前置条件，今天全部 **unverified**（本设计不为被拒形态投入取证）：

1. **临时表种类**：Oracle 全局临时表（GTT）与 PG/MSSQL/H2/MySQL 的会话级临时表语义互不相同；"建表"由谁供给（平台启动 DDL？应用 migration？首次使用时建？）没有跨方言一致答案。
2. **行可见性生命周期**：Oracle GTT `ON COMMIT DELETE ROWS` vs `ON COMMIT PRESERVE ROWS` 两条路：前者在"插入元素 → 提交 → 再查询"的常见编排下数据已消失；后者则要求显式清理，而清理时机（语句后 / 事务后 / 会话后）没有统一契约。
3. **事务回滚行为**：插入临时表的 DML 参与外层事务，回滚会一并回滚——这一点是安全的；但"查询失败后重试"与"同一事务内多次复用同一临时表"叠加 `ON COMMIT` 语义时，残留数据会**静默改变下一次查询的结果集**。
4. **连接池复用污染**：物理连接归还池后 reuse，`ON COMMIT PRESERVE` + 未清理 = 跨业务请求的数据污染。这是临时表形态最危险的一条，任何实现都必须有会话级清理钩子与池归还钩子，而当前 ORM 会话/连接管理层没有这样的契约位。
5. **优化器与统计**：临时表的统计信息与执行计划行为逐方言不同，JOIN 转换可能造成比超长 IN 更差的计划。

结论：以上 5 条使临时表成为本策略中**成本最高、风险最集中**的形态，而它解决的问题是"应用侧不分批地传超大列表"——这个需求本身应先被产品层拒绝。被拒不移除登记：若将来重提，按本节清单逐条取证（全部当前 unverified），并作为新的设计文档评审，不在 WI10b 范围内。

## 四、拒绝了什么

1. **静默截断（超限只取前 N 个元素）**：直接产出错误结果集，是本 roadmap 全篇要消灭的 A 类"静默错误 SQL"的翻版。永不采纳。
2. **自动分批 OR 改写作为 v1 默认**：语义虽等价，但(a)动态分批能消除"超过固定阈值即硬失败"的门槛，**不能**消除 SQL 文本随 N 线性膨胀的成本——列名逐批重复、解析/优化成本不变（与 §3.4 改写形态表的裁定一致）；(b)为不存在的需求预先支付生成器改写 + 全方言回归成本；(c)一旦默认开启，用户失去"这个查询本来就不该这么大"的信号。留作后备形态，触发条件见 §3.4。
3. **临时表改写**：见 §3.6。
4. **按方言差异化阈值**：除 Oracle 外无一手证据；契约碎片化损害可移植性；见 §3.1/§3.4。
5. **EQL IN 列表自动参数化**（把字面量列表改写为 N 个 `?`）：不解决 Oracle 1000 硬限（那是表达式列表限制），改变执行计划与 SQL 文本稳定性，破坏 BC 语料；且 §3.3 已证实现有编译期参数机制没有集合展开语义，引入它属于新增能力而非缺陷修复。
6. **v1 新增 `IInListLimitHandler` 之类方言可插拔公共接口**：单一实现者不进公共 API（`IUpsertHandler` 删除先例）；升级路径已写入契约（§3.5）。
7. **把门禁加在 `AstToEqlGenerator`/`AstToSqlGenerator` 打印方法内**：打印器同时服务回显路径，`toSqlString()` 必须保持忠实转储与随时可用——门禁不得引入非预期的回显变更（有意回显修正走语料基线再生成），且报错可诊断性依赖用户能 dump 触发门禁的完整 SQL；门禁放在编译/构建管线，执行必经。

## 五、与已有设计的关系

- **上游**：roadmap「WI10a / WI10b」节（问题定义与零截断实测）与 G-1 裁决（`2026-10-03-wi0-gate-adjudications.md`）——unverified 纪律与"低于版本门编译期报错为默认形态"的原则由 G-1 确立，本设计的报错形态与 unverified 标注沿用同一纪律。
- **同级先例**：`ERR_EQL_NOT_SUPPORT_ILIKE`（`AstToSqlGenerator.java:495`）确立的"静默降级改变查询语义必须显式报错"原则；BUG-3 修复方向（`getForUpdateSql` 空模板编译期报错）。本设计的报错行为是该原则在 IN 列表维度的应用。
- **下游**：WI10b（按策略实现 + BUG-13 死标志三档处置）是本策略的唯一实现 owner。本设计裁决的是策略与契约；方法级落点（编译管线内具体挂载点、`spreadParams` vs `FilterBeanToSQLTransformer` 的取舍、错误码最终命名）由 WI10b plan 在本设计的契约约束内裁定。
- **定稿动作（已完成）**：同步修订 roadmap `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI10b Exit Criteria ② 的措辞——原稿"阈值内正常 / 超阈值改写 / 超阈值且方言不支持时报错"与本设计"报错-无改写-方言无关"的结论矛盾；2026-10-03 定稿轮已改为"阈值内正常 / 超阈值编译·构建期报错（新错误码）/ 门禁方言无关、不做改写"并在 roadmap 原地留了修订注记。
- **owner doc 同步清单（WI10b 落地时）**：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 写入阈值契约表、各方言上限证据等级列、以及 §3.2 定义的迁移说明必含项（破坏性变更裁定 + 阈值调高出口 + 升级前评估步骤；对齐 WI9b 的"迁移说明或显式裁定，不接受留空"姿态）。
- **依赖约束**：roadmap 红线——本设计所有 unverified 标注不得支撑覆盖率或"已修正"结论；MSSQL 绑定参数路径取证、其余方言上限取证均为 WI10b 及其后的真库取证项，不是本设计的阻塞项。
