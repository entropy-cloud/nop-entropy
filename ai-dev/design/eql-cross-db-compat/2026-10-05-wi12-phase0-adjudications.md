# WI12 Phase 0 裁定（plan 38 — P2 长尾五项 + WINDOW 回归）

> Status: final（Phase 0 执行轮落档，2026-10-04）
> Source: `ai-dev/plans/38-eql-wi12-p2-longtail.md` Phase 0 checklist；`ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI12 节 + 约束 1-4 + 红线（v7.2）
> 本文档只记录最终设计状态与裁定结论（含被拒备选及理由）；代码层面的类签名以源码为准。

## 0. 前置状态复核、H2 版本钉死与全量锚点符号重定位

**前置状态复核（2026-10-04 实测）**：

- plan 03（WI1）`Plan Status: completed` ✓；plan 26（WI7，`26-eql-wi7-pagination-and-nulls.md`）`Plan Status: completed` ✓；plan 37（WI7b）`Plan Status: completed` ✓（三 plan 头部逐一定位）。
- 矩阵快照门禁起点：`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` **exit 0**（18 个 dialect XML / 18 快照登记；loadable 15 / non-loadable 3）。
- 工作区无并行文法改动残留：`git status` 仅存与本 plan 无关的 `nop-ooxml-xlsx` sample 文件 M 状态与两个 untracked 项（`.zcodeignore`、`nop-code-web` 页面目录）——不触碰、不入本 plan 提交。

**H2 版本钉死（Phase 0 门）**：`./mvnw dependency:list -pl :nop-dao` 实测 CI 解析版本 = **com.h2database:h2:jar:2.4.240:test**（与 plan 起草期 jar 证据同版本，无需重估）。本地 m2 仓库 `~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar` 独立复验（javap，_tmp/wi12-h2probe）：

- `org.h2.command.query.ForUpdate` 含 `public static final ForUpdate DEFAULT / NOWAIT / SKIP_LOCKED` 三常量 + `ForUpdate$Type`（WAIT 形态经 `wait(int)` 工厂）；
- `getSQL(StringBuilder,int)` 常量池渲染串：`" FOR UPDATE"` + `" NOWAIT"` / `" SKIP LOCKED"` / `" WAIT"`（WAIT 另拼零填充秒数）。
- ⇒ **H2 `FOR UPDATE NOWAIT` / `SKIP LOCKED` [实测-jar] 成立**（2.4.240）；Phase 2 补真库执行探针升级为 `[实测]`。

**VALUES / AT TIME ZONE 官方文档取证（h2database.com，2026-10-04 实抓）**：

- **VALUES 表源 [文档]**：grammar「Query」= `select | explicitTable | tableValue`，官方示例 `VALUES (1, 2), (3, 4)`（"a table value constructor"）；「Table Expression」= `{ [schemaName.]tableName | ( query ) | ... } [ [AS] newTableAlias [ ( columnName [,...] ) ] ]` ⇒ `FROM (VALUES (1),(2)) AS T(C1)` 在 H2 文法上成立（query 含 tableValue；别名列清单是 Table Expression 既有形态）。
- **AT TIME ZONE [文档]**：grammar「Time zone」= `AT { TIME ZONE { intervalHourToMinute | intervalHourToSecond | string } | LOCAL }`（"Converts the timestamp with or without time zone into timestamp with time zone at specified time zone"）；且「Term」文法把 timeZone 挂在项级后缀位 ⇒ 后缀紧绑定语义。两探针真库执行在 Phase 4/5 落地。

**全量锚点符号重定位（照 plan 37 先例；全部符号定位复核，2026-10-04）**：

| 锚点 | plan 记载 | 实测现位 |
|---|---|---|
| `AstToSqlGenerator.printForUpdate` override | :355-363（javadoc :349-352、getForUpdateSql :356、报错 :358-360） | javadoc :349-353、override :355、`getForUpdateSql(null)` :356、空模板报错 :357-360 ✓（±1 行内） |
| `AstToEqlGenerator` 回显 `printForUpdate` | :480-481 调用点 / :505 方法 | 调用守卫 `if (node.getForUpdate()) printForUpdate(node)` :480-482、方法 :505 ✓ |
| `default.dialect.xml` `<forUpdate>for update</forUpdate>` | :39 | :39 ✓ |
| `duckdb.dialect.xml` `<forUpdate x:override="replace"/>` | :44 | :44 ✓ |
| `TestEqlForUpdateDialectGate` 三用例 | :168/:176/:184 | :168/:176/:184 ✓ 逐字命中 |
| plan 03 LockOption 遗留注记 | :324-325 | Non-Blocking Follow-ups 段命中（"WI12 接 FOR UPDATE NOWAIT/SKIP LOCKED 时回头补 LockOption 传递"）✓ |
| `DialectSqls` retention 先例（sqlIntervalLiteral/intersectKeyword） | :13-19 / :21-28 | 两 javadoc+字段块在文件头部 ✓（retention 手写、零生成） |
| `dialect.xdef` `<sqls>` 段 / `<forUpdate>` | :110-180 / :161 | **漂移**：`<sqls>` 段 :115-185、`<forUpdate>` **:166**（intersectKeyword :115-116、sqlIntervalLiteral :149） |
| `EqlAST.xjava` `forUpdate`/`nowait` | :166/:167 | :166/:167 ✓（`class SqlQuerySelect` :152） |
| `_SqlQuerySelect` nowait 消费点 | :31/:112-119/:275/:521/:597 | :31/:113/:119/:275/:521/:597 ✓（全部生成代码；手写侧 grep 0 命中不变） |
| `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` `forUpdate_` | :256-258 | :256-258 ✓（`: FOR UPDATE`，零备选） |
| `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` `sqlTableSource` 四备选 / `sqlSubqueryTableSource` / `sqlValues_` | :145-151 / :164-166 / :46-48 | :145-150 / :164-166（`LATERAL? LP_ query=sqlSelect RP_ AS? alias=sqlAlias`）/ :46-48（`VALUES LP_ e=sqlExpr (COMMA_ e)* RP_`，INSERT 单行）✓ |
| `nop-persistence/nop-orm-eql/model/antlr/Eql.g4` 60 行 / `sqlStatement` / `sqlTransactionStatement` | :28-30 / :53-61 | 60 行 ✓ / :28-30 ✓ / :53-61（`sqlCommit: COMMIT` :57-58、`sqlRollback: ROLLBACK` :60-61）✓ |
| `EqlASTVisitor` visitSqlCommit/Rollback no-op | :667-675 | :667-675 ✓（只 visit decorators、无 print 调用） |
| `EqlCompiler` 走 `AstToSqlGenerator.visit` | :76-79 | :76 ✓ |
| `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4` SAVEPOINT token | :349 | :349 ✓（零 parser 规则引用不变）；ROLLBACK :345 |
| `supportSavePoint` accessor | IDialect.java:75 / DialectImpl.java:262-263 | **漂移（WI10b 后形态）**：`IDialect`/`DialectImpl` 中 **0 命中**（accessor 不存在=软弃用「无消费点」口径）；存活面 = `_DialectFeatures`（冻结 `_gen`）+ `default.dialect.xml:17` 配置 + owner doc 死标志台账 |
| owner doc 死标志台账 `supportSavePoint` 行 | :716 | **漂移**：**:754**（`### 死标志台账` 节 :738 起） |
| `TestDialectDeadFlagDisposition` | 存在 | `nop-persistence/nop-dao/src/test/java/io/nop/dao/dialect/TestDialectDeadFlagDisposition.java` ✓ |
| `nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4` AT / `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` ZONE / TIME | :238 / :97 / :481 | :238 / :97 / :481 ✓（三 token 词法层齐备，零新 token 结论成立） |
| `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` `sqlWindowClause` / `sqlWindowExpr`+`windowName` | :295 / :256-262 | :295 / :256-262（`windowName=sqlIdentifier_` :259）✓ |
| `EqlTransformVisitor` 窗口三门 / unknown-function 抛点 | :378-394 / :1561-1564 | :378/:384/:390 三门 ✓；ERR_EQL_UNKNOWN_FUNCTION 抛点 :1564 ✓；`ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE` 抛点 :1365/:1486 |
| `TemplateSQLFunction` marker 越界校验 | :56-62 | :56-64 区 ✓（**实位 = `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/function/`**，plan 未给全路径，非 orm-eql） |
| `EqlTransformVisitor` 文件路径 | plan 记 `transform/` 包 | **漂移**：实位 `src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java` |
| `DialectImpl` 文件路径 / `getForUpdateSql(LockOption)` | `DialectImpl.java:522-523` | 实位 `io/nop/dao/dialect/impl/DialectImpl.java:522` ✓（LockOption 参数被忽略的现状不变） |
| `GenSqlHelper` getLockHintSql/getForUpdateSql | :196/:202 | :196/:202 ✓（实位 `nop-persistence/nop-orm`） |
| `JdbcTestCase` H2 内存库硬编码 | :112-113 | `createDataSource` :110-117（`jdbc:h2:mem:<uuid>`）✓ |
| `nop-persistence/nop-orm-eql/model/antlr/Literals.g4` NULLS/ROLLUP/CUBE 三件套先例 | 尾部追加、IDENTIFIER_ 之前 | :30/:40/:44 ✓（注释明文「显式 token 定义在 IDENTIFIER_ 之前」） |
| `unreservedWord_` 双登记位 | BaseRule.g4 | 规则 :72 起；ROLLUP/CUBE 双登记注释块在块尾 ✓（NOWAIT/SKIP/LOCKED 追加位） |
| `ast/_gen/` 78 文件 / `EqlASTKind` 68 常量尾部 SqlGroupingOp=67 | 78 / 67 | 78 ✓ / 68 常量、`SqlGroupingOp, // ordinal: 67` 尾部 ✓ |
| 窗口测试面四载体 | 见 plan ④ | `TestEqlWindowGrammarParse`（testOverNamedWindowRef :67 / testWindowClause :74）✓、`TestDialectWindowSqlSnapshot`（**实位 `compile/` 包**，plan 记 `sql/` 包——漂移）+ 10 个 `{dialect}.window.sql` golden ✓、`TestH2WindowFrameMatrix`（nop-dao）✓、`TestH2WindowFrameCompileEndToEnd`（compile/ 包）✓ |
| `_EqlASTBuildVisitor` forUpdate prop 赋值 | — | :1292-1293 `if(ctx.forUpdate != null){ ret.setForUpdate(SqlQuerySelect_forUpdate(ctx.forUpdate)); }`；抽象方法 :1791 `boolean SqlQuerySelect_forUpdate(ParseTree)`；手写实现 `EqlASTBuildVisitor:173` `return node != null` |

## 1. 裁定：LockOption 引入形态与文法接线形态（Decision，回补 plan 03 :325）

**选定形态 = 「复用 + 既有类加字段」**：

1. **AST 面**：`SqlQuerySelect.nowait`（既有 boolean，`EqlAST.xjava:167`）承载 NOWAIT；**新增 `boolean skipLocked` 字段**（既有类加字段，`EqlASTKind` ordinal 零变动——roadmap WI7 收口注记口径），仅重生成 `_SqlQuerySelect.java` 等既有 `_gen` 文件。**`LockOption`（nop-dao 类型）不进 AST**：AST 是方言无关模型；`getForUpdateSql(LockOption)` 的 EQL 路径继续传 null（`DialectImpl:522` 现状忽略该参数），`LockOption` 的 owner 仍是 `GenSqlHelper` ORM 实体锁路径（plan 03 :325 遗留注记据此回补结案：EQL 侧不接 LockOption 对象，接的是语义等价的 nowait/skipLocked 布尔对）。
2. **文本承载面**：`<sqls>` 新元素 **`<forUpdateNowait>`**（Phase 1）与 **`<forUpdateSkipLocked>`**（Phase 2）——presence = 能力 + 文本二合一（WI1 `sqlIntervalLiteral`/WI3 `intersectKeyword` retention 先例）：已配置 = 方言支持且输出该文本；未配置 = 编译期响亮 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（feature 参数 = `forUpdateNowait`/`forUpdateSkipLocked`）。`<forUpdate>` 主模板与 `getForUpdateSql(null)` 路径不动（WI1 交付面 BC）。
3. **文法接线形态 = (a) 变体区分上移 `sqlQuerySelect` 层分离标签**：
   `(<br> forUpdate=forUpdate_ (nowait=NOWAIT | skipLocked=skipLocked_)?)?`，其中 `skipLocked_: SKIP LOCKED;`。
   依据（codegen 实测形态）：AST boolean 字段 ↔ 同名 label 的映射生成 `ret.setXxx(SqlQuerySelect_xxx(ctx.xxx))` 直达 prop 赋值——token label 生成 `boolean SqlQuerySelect_xxx(Token)`（先例 `SqlQuerySelect_selectAll(Token)`），rule label 生成 `boolean SqlQuerySelect_xxx(ParseTree)`（先例 `SqlQuerySelect_forUpdate(ParseTree)`）；手写侧只需两个两行 trivial 实现（`return token/node != null`），**零聚合方法覆写**。
   **被拒 (b) `forUpdate_` 子树备选 + 手写整体覆写 `visitSqlQuerySelect`**：生成的 `SqlQuerySelect_forUpdate(ParseTree)` 只返回 boolean，子树内的 NOWAIT/SKIP LOCKED 变体信息无通道到达 `ret`——覆写体必须整体复制生成方法的全部字段赋值（from/where/groupBy/orderBy/limit/…约 12 项）再自行解 `ctx.forUpdate` 子树；后继任何 `sqlQuerySelect` 文法改动若只改 `.g4`，手写副本静默缺失新子句（重生成不覆写手写方法）——漂移面不可接受。WI7b wrapper 先例（`SqlGroupByItem_rollup`）是「label 子方法完全构建单节点」，本场景变体字段挂宿主聚合节点，结构不适用。
4. **被拒备选（形态面）**：① AST 引用 nop-dao `LockOption` 类型——层级耦合（orm-eql 已依赖 nop-dao，但 AST 模型引入 ORM 锁语义类型破坏方言无关性）+ `EqlAST.xjava` 非 JDK 类型注册成本；② features 布尔门 + 「`<forUpdate>` 模板 + 固定后缀拼接」——能力门与文本面割裂，且假设锁后缀是 `<forUpdate>` 文本的前缀扩展（MSSQL 类无 forUpdate 基文本的方言表达不了「无基文本但有变体」）；③ 单 `<forUpdate>` 模板参数化承载三态——xdef string 模板无参数槽机制，且同一配置文本无法表达「裸/nowait/skipLocked」三态选择。
5. **互斥与矛盾输入处置（裁定）**：解析层 `( NOWAIT | SKIP LOCKED )?` 单选备选天然互斥（`for update nowait skip locked` 解析响亮失败）。程序化构造的矛盾输入（`nowait && skipLocked` 同真；`forUpdate=false && (nowait||skipLocked)`）在**生成器入口编译期响亮**：`IllegalArgumentException`（英文消息，模块内部契约违反——WI11 `UpsertStatements` 参数契约违规同款先例；非用户输入错误，不走 `NopException` 错误码，不新起新码）。落点 = `AstToSqlGenerator.visitSqlQuerySelect` 入口校验（`printForUpdate` 仅在 `forUpdate=true` 时被调用，校验必须在守卫之前才能覆盖 `forUpdate=false` 的矛盾）。回显路径（`AstToEqlGenerator`）不加校验（WI1 裁定回显保持硬编码不动；矛盾 AST 是程序化构造才可达的编程错误，执行路径已响亮）。
6. **`WAIT n` 缺位**：登记 Non-Blocking Follow-up（plan 已预登记，roadmap 未点名；H2 `ForUpdate.wait(int)`/Oracle `WAIT integer` 均支持，与 `LockOption.timeout` 的关系归未来 WI 裁定）。

## 2. 裁定：nowait / SKIP LOCKED 方言矩阵与逐方言配置值（Decision）

| 方言 | `<forUpdateNowait>` / `<forUpdateSkipLocked>` | 证据等级 |
|---|---|---|
| h2 | `for update nowait` / `for update skip locked` | **[实测-jar]** 2.4.240 `ForUpdate` javap（§0）+ Phase 2 真库探针升 `[实测]` |
| h2gis | 继承 h2（`x:extends="h2.dialect.xml"`） | 继承 [实测-jar] |
| oracle / oracle-reverse | `for update nowait` / `for update skip locked` | **[文档]** Oracle 19c SQLRF SELECT `for_update_clause` = `FOR UPDATE [OF …] [ { NOWAIT \| WAIT integer \| SKIP LOCKED } ]`（2026-10-04 实抓） |
| postgresql | `for update nowait` / `for update skip locked` | **[文档]** PG current docs locking clause = `FOR lock_strength [ OF … ] [ NOWAIT \| SKIP LOCKED ]`（2026-10-04 实抓） |
| postgis | 继承 postgresql | 继承 [文档] |
| mysql | `for update nowait` / `for update skip locked` | **[文档]** MySQL 8.0 手册「Locking Reads」：NOWAIT/SKIP LOCKED 可与 `SELECT … FOR UPDATE` 同用（8.0.1+，承诺基线 8.0.31 之上；2026-10-04 实抓） |
| **mysql5.7** | **显式空覆盖** `<forUpdateNowait x:override="replace"/>` + skipLocked 同 | [文档] 5.7 无 NOWAIT/SKIP LOCKED（8.0.1 才引入）——继承 mysql 后必须显式关断（duckdb 空 `<forUpdate>` 同机制，加载后 = null = 未配置） |
| mariadb | 继承 mysql（值同形） | **[文档]** MariaDB 官方 SELECT 页 `lock_option` = `[WAIT n \| NOWAIT \| SKIP LOCKED]`；NOWAIT 10.3.0（MDEV-11379/11388）/ SKIP LOCKED 10.6.0（MDEV-18311）引入——承诺基线之上 |
| duckdb | **显式空覆盖**（继承自 postgresql 后必须关断） | [文档] DuckDB 无悲观锁；WI1 已置空 `<forUpdate>`（:44），**负向用例方言** |
| mssql | 不配置（继承 default 无该元素） | [文档] T-SQL SELECT 无 `FOR UPDATE` 子句变体（锁提示 `WITH(UPDLOCK,READPAST)` 体系）——**负向用例方言** |
| db2 | 不配置 | [未取证]（IBM 文档门禁，红线不计覆盖率） |
| dm / tdengine | 不配置 | **[未取证] 红线**，不计覆盖率 |
| sqlite / es | 不在矩阵（无方言文件 / 伪方言） | roadmap 既档 |

## 3. 终局裁定：SAVEPOINT / ROLLBACK TO = 移出 EQL 语法层（Decision，分支 A）

**裁定 = 移出 + ledger + successor**（plan「差异登记」第 3 条五条证据链全部成立，2026-10-04 逐条复核）：

1. 现状**响亮失败**（`SAVEPOINT` 零 parser 规则引用、`ROLLBACK TO` 解析失败）——排序原则 P1「静默错误 > 响亮失败」不触发；
2. 事务语句 owner 是 **JDBC Connection API**（`Connection.setSavepoint()`/`rollback(Savepoint)`）与 Nop 事务管理器，不是 SQL 文本编译层——EQL 执行模型（CompiledSql 缓存 + querySpace + PreparedStatement）不服务于事务边界控制；
3. `supportSavePoint` 已被 WI10b **软弃用**并明令「勿新增消费依赖」（owner doc 死标志台账实位 **:754**，锚点漂移已登记 §0）——落地 EQL SAVEPOINT 语法即新增消费依赖，直接冲突；
4. EQL 既有事务语句层（`Eql.g4:53-61`）打印侧是 **no-op**：结构探针证据 = `EqlASTVisitor:667-675` 的 `visitSqlCommit`/`visitSqlRollback` 方法体仅 `visitDecorators(...)` 调用（无任何 print）；`AstToSqlGenerator`/`AstToEqlGenerator` 均无 commit/rollback override（grep 0 命中）——`visit` 默认分发只走 decorators ⇒ 编译产物必然不含语句文本，**结构上封闭，无需执行探针**（plan 预留的「一行探针」判定为不必要：方法体穷尽可得）。在其上扩展 `ROLLBACK TO` 只会产生「可解析但编译产物为空」的新 hollow 面（违反 Minimum Rules #24）；
5. roadmap「明确不做的事」已把 DDL/事务边界类语句划出 EQL（`TRUNCATE` 永久否决先例）。

**为什么不构成名义 owner**：ledger 行带显式触发条件与 successor 路径（见 §3.1 回写措辞）；`Deferred But Adjudicated` 段同步登记；WI12 交付 3 原地注记指向裁定文档本节。三处对齐由 Phase 3 落地。

**`SqlCommit`/`SqlRollback` 打印 no-op 面 = watch-only 观察项**（本 plan 不修，Non-Goals 既档）：若未来任何 WI 使事务语句可经 EQL 编译执行，此面立即升级为 live defect。

**`supportSavePoint` 软弃用档位零触碰**：`TestDialectDeadFlagDisposition` 回归随 Phase 3 确认未受扰。

### 3.1 roadmap 回写措辞（Phase 3 执行稿）

- WI12 节交付 3 行后原地注记（不删原文）：
  `<!-- ⚠️ 2026-10-04 WI12 收口注记（原地注记，裁定正文 = ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §3）：SAVEPOINT/ROLLBACK TO 显式移出 EQL 语法层。五条证据链：响亮失败现状（P1 不触发）；事务 owner = JDBC Connection API/Nop 事务管理器；supportSavePoint WI10b 软弃用「勿新增消费依赖」；既有 COMMIT/ROLLBACK 打印 no-op（EqlASTVisitor:667-675 结构封闭）上扩展 = 新 hollow 面；TRUNCATE 永久否决先例同族。Successor = 单独 roadmap 立项（触发条件 = 出现"在 sql-lib 声明事务语句"的真实产品需求；须同时解决 SqlCommit/SqlRollback 文本打印 + supportSavePoint 恢复接线裁定 + 事务管理器集成）。 -->`
- 「明确延后项」ledger 新增一行（理由 / 触发条件 / successor 同上浓缩）。

## 4. 裁定：`FROM (VALUES …)` = 落地（Decision，分支 A，ⓒ 定价）

**transform 层可行性结论（裁定硬依据）= 可行**，逐点核验（2026-10-04 实测 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`）：

1. **querySpace**：实体现解析路径（`SqlSingleTableSource` → `:859-871` addResolvedEntity）对 VALUES 表源不触发 ⇒ FROM 存在但零实体贡献时 `this.querySpace`/`this.dialect` 保持 null，`EqlCompiler` 将以 null dialect 构造生成器（NPE 面）。**解法（纯增量）**：`visitSqlQuerySelect` 的 from 分支补「实体无关 FROM 回落」——from 访问后 `querySpace == null` 时复用 `findQuerySpaceWhenNoFrom`（decorator 显式指定优先，否则 `DEFAULT_QUERY_SPACE`）。该回落今天不可达（现行所有 FROM 表源要么解析实体要么响亮失败），零既有行为变化。
2. **表作用域**：`addAliasToScope(SqlTableSource)` 增 `SqlValuesTableSource` 分支（注册 alias，`SqlSubqueryTableSource` 分支同形）。
3. **列解析**：`source.getResolvedTableMeta()` 消费面是 `ISqlSelectionMeta`（`SelectResultTableMeta` 即实现）⇒ VALUES 源元数据 = `SelectResultTableMeta`（列名 = 显式列别名清单；类型 = 首行表达式 best-effort：字面量/列引用取已解析类型，其余按 STRING 兜底）——无实体导航前提，`ERR_EQL_UNKNOWN_FIELD_IN_SELECTION` 对 `t.x` 未知列照常响亮。
4. **`select *` 展开**：`getSourceSelectItems` 的 else 抛点（:1365）**增 `SqlValuesTableSource` 分支**（从其 meta 构建投影），既有 `ERR_EQL_ONLY_SUPPORT_SINGLE_TABLE_SOURCE` 守卫对其余未知源类型原样保留（不拆门）。
5. **`SqlTumbleTableSource` 先例**：transform 对 stream-only 表源响亮拒绝的形态（`ERR_EQL_TABLE_SOURCE_NOT_RESOLVED`）证明表源类型分派点是显式 instanceof 链——新增分支不触碰既有分支语义。

**AST/文法定价（ⓒ，单新类）**：

- 新 AST 类 **`SqlValuesTableSource extends SqlTableSource`**，落 `EqlAST.xjava` **尾部**（WI7b 教训）：`EqlASTKind` diff = 尾部 +1 行（ordinal 68），`ast/_gen/` 78→79。字段 = `List<SqlMultiValueExpr> rows;`（**复用既有 `SqlMultiValueExpr`**——`LP_ expr (COMMA_ expr)*` 行结构与 IN 值列表同构，`_SqlMultiValueExpr.values: List<SqlExpr>` 实测）+ `SqlAlias alias;` + `List<String> columns;`（别名列清单，可空）。
- 文法：`sqlTableSource` 新备选 `| LP_ VALUES rows+=sqlMultiValueExpr (COMMA_ rows+=sqlMultiValueExpr)* RP_ AS? alias=sqlAlias (LP_ cols+=sqlIdentifier_ (COMMA_ cols+=sqlIdentifier_)* RP_)? #SqlValuesTableSource_ex`（label 首段 = 目标 AST 类名，`SqlTumbleTableSource_ex` 同款；alias 必填——列名清单缺省时列名取 `C1..Cn` H2 惯例，**EQL 比 H2 收紧 alias 必填**，属子集化约束、登记 owner doc 不等价行）。零新 token（`VALUES` token 已在 `Keyword.g4:138`）。
- **ragged rows（行内元素数不一致）= 解析期响亮**：手写 visit 内比对行元数，不一致抛新错误码 **`nop.err.eql.values-row-arity-mismatch`**（`OrmEqlErrors` 手写 inline define，零 codegen；新起码理由 = 既有码无「行元数不一致」语义可复用，WI0 为 function template 单独立码同款判断）。
- 生成器：`AstToSqlGenerator.visitSqlValuesTableSource` 输出 `(values (…),(…)) alias (cols)`；**能力门** = transform 期 `dialect.isSupportValuesTableSource()`（`dialect.xdef` `<features>` 新属性 + `DialectFeatures` retention 字段 + `IDialect`/`DialectImpl` accessor，nop-dao 零生成管线，roadmap Cross-Cutting 口径）；不支持方言编译期 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（feature=`valuesTableSource`）。
- 方言矩阵（逐格一手取证结论）：**落地注册** = h2/h2gis [文档+Phase 4 真库探针]、postgresql/postgis [文档 Table Expression]、duckdb [文档]（duckdb 继承 postgresql，天然获得）、mssql [文档]（`FROM (VALUES …) AS t(c1,c2)`，别名必带——与 EQL alias 必填约束同向）、mysql 8.0.19+ [文档]（**行形态是 `VALUES ROW(…)` 硬分叉 ⇒ mysql 不注册**，响亮 + owner doc 不等价行）、oracle [未取证 23ai 边界 → 不注册响亮；UNION ALL 链 lowering = Non-Goal 登记不损失]、db2 [未取证 → 不注册]、mariadb [文档 ❌ 不注册]、sqlite/sqlite 系 [compound-SELECT 边界未取证 → 不注册]、dm/tdengine [未取证 红线]。

## 5. 裁定：AT TIME ZONE 文法方案（Decision，WI5 同款）

**选定 = 构建期归一化进 `SqlRegularFunction(name="atTimeZone", args=[expr, tz])`**：

- **零新 AST 节点、零新 token**：`AT`（SQL92Keyword:238）/`TIME`（Keyword:381，datatype 词表）/`ZONE`（SQL92Keyword:724，`unreservedWord_` :97 在列）三 token 词法层齐备（§0 复核）。
- **文法挂载点 = `sqlExpr_bit` 新后缀备选**（非 plan 预判的 `sqlExpr_special`——实测 `sqlExpr_special` 的备选全部 keyword 起头且位于 `sqlExpr_simple` 下链，若加 `e=sqlExpr_simple AT …` 备选将构成 `sqlExpr_special ⇒ sqlExpr_simple ⇒ sqlExpr_functionCall ⇒ sqlExpr_special` **间接左递归**，ANTLR 拒绝生成；`sqlExpr_bit` 层无此环，且 `# SqlExpr_simple2` 备选共享前缀由 ALL(*) 前瞻消解——与 `sqlExpr_predicate` 的 LIKE 备选同结构）：
  `| left=sqlExpr_simple AT TIME ZONE zone=sqlExpr_simple # SqlRegularFunction_atTimeZone`。
  绑定紧度 = 项级后缀（H2 Term 文法同位），`a + ts AT TIME ZONE 'UTC'` 语义与主流方言一致。备选序登记：置于算子备选之后、`SqlExpr_simple2` 回落之前；`AT` 当前零规则引用、`AT_`（decorator `@`）是不同 token，冲突面实测为零——重生成后以 ANTLR 无 ambiguity warning + 语料棘轮为证。
- **归一化**：手写 `visitSqlRegularFunction_atTimeZone` 完全重写不调 super（WI5 `visitSqlRegularFunction_extract` 先例），构建 `SqlRegularFunction(name="atTimeZone", args=[left, zone])`。
- **逐方言 `<template name="atTimeZone"><source>{0} AT TIME ZONE {1}</source></template>`**：注册 = h2/h2gis、postgresql/postgis、oracle/oracle-reverse、mssql、db2、duckdb（全部 `[文档]`；`AT LOCAL` 变体不在范围）。**不注册** = mysql/mysql5.7/mariadb（MySQL 仅 UTC-only `CAST(ts AT TIME ZONE 'UTC' AS DATETIME)` 8.0.22+，不无损——响亮 + owner doc 不等价行）、sqlite（永无）、dm/tdengine（[未取证] 红线）。未注册方言 = `ERR_EQL_UNKNOWN_FUNCTION(atTimeZone)`（WI6 datediff/dateadd 不落先例，transform :1564 既有抛点）。
- **三态断言设计**：① 输入 `ts at time zone 'UTC'` 可解析；② 归一化回显 = `atTimeZone(ts, 'UTC')` 函数形态（`toSqlString()`；7 语料 0 命中已证无 BC 风险，断言仍须落）；③ 方言 lowering = `{0} AT TIME ZONE {1}` 渲染文本（h2 实测 + 至少一族 [文档] 方言）。**`{0}`/`{1}` marker 与实参顺序经 `TemplateSQLFunction` 加载期越界校验兜底（WI0 交付 2，nop-dao function 包 :56-64 实测）。**
- **真库证据**：`atTimeZone` 在 h2 经 `TestDialect.testFunctions` 真库执行全绿（补 `<testSql>`）；其余注册方言逐个列 [文档] 证据；dm/tdengine 标 `[未取证]`。

## 6. 窗口命名 WINDOW 回归验证面清单（Proof，Phase 6 输入）

四层载体全部实测存在于当前树（§0 表）：

| 层 | 载体 | 关键用例 |
|---|---|---|
| parse | `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/parse/TestEqlWindowGrammarParse.java` | `testOverNamedWindowRef`(:67)、`testWindowClause`(:74) |
| golden | `nop-persistence/nop-orm-eql/src/test/java/io/nop/orm/eql/compile/TestDialectWindowSqlSnapshot.java` + `src/test/resources/__snapshot/{dm,duckdb,h2gis,mariadb,mssql,mysql,mysql5.7,oracle,postgis,postgresql}.window.sql` | 10 方言 golden（5 frame 方言）+ `testOverWithoutPartitionText`（WI7b 补） |
| transform 门 | `compile/EqlTransformVisitor` 三道 frame 能力门 :378/:384/:390 + `ERR_EQL_UNKNOWN_WINDOW_NAME` :421 | `TestEqlWindowGrammarParse`/快照内覆盖 |
| 真库 | `nop-dao TestH2WindowFrameMatrix` + `compile/TestH2WindowFrameCompileEndToEnd` | H2 真执行 |

Phase 6 在全部生成物重生成后全量重跑；结论 =「已由 add-stream-sql 建成、本 plan 仅回归收口」记 daily log（roadmap 回写素材）。
