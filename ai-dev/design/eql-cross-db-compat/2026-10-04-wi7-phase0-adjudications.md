# EQL 跨数据库兼容性：WI7 Phase 0 前置裁定

**日期**：2026-10-04
**范围**：`ai-dev/plans/26-eql-wi7-pagination-and-nulls.md`（plan Status: active）Phase 0 的四项裁定 + COLLATE ledger 二选一 + 约束 4 两类用例清单 + EC ⑤ 满足形态登记；涉及 `nop-persistence/nop-orm-eql`（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4` 的 `sqlLimit`、`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlOrderByItem`/`unreservedWord_`、`EqlASTBuildVisitor`、`AstToSqlGenerator`）与 `nop-persistence/nop-dao`（`IDialect`/`DialectImpl` 的 `isSupportNullsFirst()` accessor）
**状态**：active —— 全部裁定项已裁定；代码未动（本 Phase 纯裁定，零产品代码变更）
**授权模式**：同 G-3/WI9a 模式（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` §裁决人注记）——平台负责人经 2026-10-04 对 WI7 plan 的执行指令授权、AI 基于证据链代拟、人可推翻；推翻时按各裁定项的"翻案条件"撤销并在本目录追加修订裁决。

---

## 〇、开工前置回退防护复核（2026-10-04 实测）

| 复核项 | 结论 | 证据 |
|---|---|---|
| 硬前置 plan 状态（roadmap 门控矩阵 WI7 行 = WI1、WI2、WI3、G-1） | **未回退**。plans 02（WI0）/03（WI1）/19（WI2）/20（WI3）均为 `completed`；G-1 已裁决（2026-10-03，承诺基线 Oracle 23ai / MySQL 8.0.31 / MariaDB 10.2 / PG 15 / MSSQL 2022 / SQLite 3.39） | 各 plan 文件头 `Plan Status`；`2026-10-03-wi0-gate-adjudications.md` |
| WI0 产物 1：语料基线快照 | **未回退**。`TestEqlCorpusBaseline` 1/1 PASS（surefire `Tests run: 1, Failures: 0`，报告 mtime 2026-10-04 08:01，晚于 nop-orm-eql 最后 commit `41bfbcba56` 2026-10-04 07:38） | `nop-persistence/nop-orm-eql/target/surefire-reports/io.nop.orm.eql.parse.TestEqlCorpusBaseline.txt` |
| WI0 产物 2：方言矩阵快照门禁 | **未回退**。`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（18 个 dialect XML、18 注册、15 可加载、3 不可加载：`default`/`geo-support`/`window-expr-support`） | 门禁输出与本节记录 |
| 漂移锚点复核（符号定位为准） | 全部符号存在，行号相对 plan 起草值（2026-10-03）继续漂移（WI9a Phase 2 文法收窄重生成所致）：① `sqlOrderByItem` plan `:369-371` → 现 `BaseRule.g4:416-418`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`，下同）；② `collateClause_` plan `:396-397` → 现 `:452-454`；③ `unreservedWord_` plan `:72-96` → 现 `:72-100`（WI5 五日期词 + 窗口词扩展后）；④ `EqlParser.java` plan 10927 行 → 现 **11260** 行；⑤ `_EqlASTBuildVisitor.java` plan 1819 行 → 现 **1871** 行；⑥ `ast/_gen/` 仍 **77** 个文件；⑦ `AstToEqlGenerator.visitSqlOrderByItem` 仍 `:618-633`（未漂移）；⑧ `sqlLimit` 仍 `DMLStatement.g4:217-219`（`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4`，下同）、`sqlSelect` 挂载仍 `:103`；⑨ 4 XML `supportNullsFirst`：`duckdb:21`/`h2:25`/`oracle:102`/`postgresql:94`（与 plan 一致）；⑩ 8 XML `defaultNullsFirst`：`mysql:7`/`mssql:5`/`dm:8`/`h2:7`=true，`tdengine:5`/`duckdb:9`/`postgresql:5`/`oracle:5`=false，`db2` 未配置（与 plan 一致）；⑪ `IDialect.isDefaultNullsFirst` javadoc（含对 `isSupportNullsFirst()` 的前向 `{@link}`）plan `:124-137` → 现 `:154-165`（WI10b 新增 3 个 accessor 使下移）；⑫ `DialectImpl.isDefaultNullsFirst` plan `:356-358` → 现 `:371`；⑬ `TestDefaultNullsFirstFlag` 三测完好（`testEightDialectAccessorContract`/`testInheritanceAndUnconfiguredDefault`/`testSupportNullsFirstUntouched`）。**本文件与执行一律以符号定位为准** | 逐符号 grep/sed 实测 |

WI4 无依赖声明复核：`IEqlAstTransformer`/`EqlCompiler` 接线面本 plan 零触碰（plan 硬前置声明维持）。

---

## 一、裁定 1：`SqlLimit` 语法形态方案 = 候选 A（归一化进既有字段）

**结论：候选 A**。`FETCH FIRST n ROWS ONLY` 与 `OFFSET n ROWS FETCH NEXT m ROWS ONLY` 在解析期归一化装入**既有** `SqlLimit{limit, offset}`，不新增"语法形态"字段。

**依据**（全部为 2026-10-04 live 实测）：

1. **输出形态由方言配置决定，AST 无输出侧消费点**：`IPaginationHandler.buildPageExpr(limitExpr, offsetExpr, sqlExpr)` 只有 limit/offset 两个表达式参数、无任何"形态"参数。`LimitOffsetPaginationHandler`（`default.dialect.xml:11` 显式配置，`es.dialect.xml:14` 同）输出 `LIMIT n OFFSET m`；`OffsetFetchPaginationHandler`（`mssql.dialect.xml:11`、`oracle.dialect.xml:12` 显式配置）输出 `OFFSET n ROWS FETCH FIRST/NEXT m ROWS ONLY`（offset 缺省时自动补 `OFFSET 0 ROWS` 前置桥接）。**`select … limit 5` 在 oracle 上今天已输出 `OFFSET 0 ROWS FETCH FIRST 5 ROWS ONLY`**——WI7 新增的只是输入侧文法接受。
2. **两形态无损分解**：`FETCH FIRST n ROWS ONLY` → `limit=n, offset=null`；`OFFSET n ROWS FETCH NEXT m ROWS ONLY` → `offset=n, limit=m`。语义与 SQL 标准逐字对应，无信息丢失。
3. **回显规范化到 EQL 既有正则形态** `limit x [offset y]`（`AstToEqlGenerator.visitSqlLimit` `:635-644` 既有行为，`TestEqlSelectAllLimitEcho` 同一口径）——两 FETCH 形态输入的回显是**归一化文本**而非源文本保真。AST 层语义等价性（两形态输入归一化后 `limit`/`offset` 字段值相同）由 Phase 1 用例锁定。
4. **候选 A 下 Protected Area 收窄**：`EqlAST.xjava`/`ast/_gen/`（77 文件）/`EqlASTKind` 零触碰；重生成只波及 parser 侧（`EqlParser.java`/`_EqlASTBuildVisitor.java`/`EqlLexer*`/`Eql.interp`/`*.tokens`）。`git status` 中 `ast/_gen/` 零 diff 是每 Phase 回归证据。
5. 给 `SqlLimit` 加形态字段等于给 roadmap 自己登记的"零生成器消费 AST 字段"清单（现 7 项，含 `SqlOrderByItem.collate`）扩员——形态字段在 SQL 输出侧零消费（依据 1），唯一潜在消费方是回显保真（依据 3 已裁定放弃源文本保真、采用规范化回显）。

**候选 B（被拒备选，记录在案）**：按 roadmap WI7 节原文给 `SqlLimit` 加"语法形态"字段（路径 ⓒ 定价）。唯一获益 = FETCH 形态输入的回显源文本保真（源代码生成场景）；代价 = `EqlAST.xjava` 改动 + `ast/_gen` 全量重生成 + Protected Area 扩大，且仍无 SQL 输出侧消费点。
**翻案条件**：出现真实的形态消费需求（如产品要求 EQL 源码往返逐字保真、或 `IPaginationHandler` 增加形态感知接口）。翻案须按 roadmap 约束 1 路径 ⓒ 重新定价（显式 scope change）并回滚 plan Non-Goals 首条。

**连带效果（roadmap 回写）**：本裁定成立则 roadmap 门控矩阵 WI7 行 Protected Area 单元格"生成管线（`SqlLimit` 增'语法形态'字段 → 77 文件 `_gen` 重写）"与 EC ⑥"77 个 `_gen`"措辞须按实况注记修订——**回写交付物由 plan Phase 4 显式认领**（本裁定为其依据）。

---

## 二、裁定 2：`sqlLimit` 文法形态细节

**结论**：

1. **三个备选全部显式 altLabel**（`sqlIntervalExpr` 双备选先例，`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `sqlIntervalExpr`：`# SqlIntervalExpr_unit` / `# SqlIntervalExpr_string`）：
   - 备选 1（既有 LIMIT 形态）：label `# SqlLimit_limit`，prop 保持 `limit=`/`offset=` 不变——生成体（`visitSqlLimit_limit`）与今天的 `visitSqlLimit` 等价（逐字段赋值 + normalize/validate），**无需手写 override**，由既有 `TestEqlSelectAllLimitEcho`/`TestEqlSelectAllLimitCompile` 全量回归兜底。
   - 备选 2：`FETCH FIRST n ROWS ONLY` → label `# SqlLimit_fetchFirst`，prop 用独立名（如 `rowCount=`），**不复用** `limit=`/`offset=`（duplicate-prop-label 约束：同一 prop 不能出现在同一规则的不同备选）。
   - 备选 3：`OFFSET n ROWS FETCH NEXT m ROWS ONLY` → label `# SqlLimit_offsetFetch`，prop 用独立名（如 `offsetExpr=`/`limitExpr=`）。
   - altLabel 后缀按 astNodeName 规则截断后同为既有类 `SqlLimit`（`SqlIntervalExpr_string` 同构先例）——不新增 AST 节点。
2. **新备选的 visit 由生成体逐字段赋值，无需手写 override（执行期实测修订，2026-10-04 Phase 1）**：plan 起草时"新备选 prop 独立命名 + 手写 visit 完全重写"的前提与 antlr4-tool codegen 的真实契约不符，落地时实测修订——
   - `/nop/templates/antlr/@init.xrun` 的 `initRuleReturnType` 经 `ast.getDefine(astNodeName).getProp(propName).type` 解析 visit 方法返回类型：**备选内 rule-ref 的 prop 名必须是目标 AST 节点的既有字段**。`rowCount=`/`offsetExpr=`/`limitExpr=` 之类独立命名直接 `renderModel` 失败（`nop.err.xlang.exec.get-prop-on-null-obj`，`@@RuleRef[rowCount=sqlExpr_limitRowCount] @@AstRule[ruleName=sqlLimit,altLabel=SqlLimit_fetchFirst]` 实测）。
   - `AstGrammarBuilder.buildAstNode` 为**每个 altLabel 创建独立 `AstRule`**（各带独立 property map）⇒ `nop.err.antlr4.grammar.duplicate-prop-label` 是**备选内**检查，跨备选复用 `limit=`/`offset=` 合法（WI1/WI5 执行期记录的"同一 prop 不能出现在同一规则的不同备选"适用的是无 altLabel 共享单 rule 对象的形态，非 altLabel 形态）。
   - 因此新备选**复用** `limit=`/`offset=`，生成 `visitSqlLimit_fetchFirst`（只设 limit）、`visitSqlLimit_offsetFetch`（设 offset+limit）、`visitSqlLimit_limit`（与旧 `visitSqlLimit` 逐字节等价）——**逐字段赋值由 codegen 契约保证**，"空节点静默通过 validate"的风险不成立（文法内 rule-ref 非可选，context 字段恒非空；生成体无条件走既有 `visitSqlExpr_limitRowCount`/`visitSqlExpr_limitOffset` 转换链）。基方法 `visitSqlLimit` 改为 `ctx.accept(this)` 分派（`visitSqlIntervalExpr` 同构先例）。plan Phase 1 checklist 的"手写 visit"项按此修订为"生成体逐字段赋值核验"，响亮失败性质不变：marker 之外的空归一化在文法层不可能出现。
   - 两个 FETCH 备选的 offset/limit 语义（`FETCH FIRST n` → limit=n/offset=null；`OFFSET n ROWS FETCH NEXT m` → offset=n/limit=m）由备选结构直接表达，无手写逻辑即无手写错误面。
3. **备选间无 ANTLR LL(*) 冲突**：备选 1 首符集 = {`LIMIT`}、备选 2 = {`FETCH`}、备选 3 = {`OFFSET`}——FIRST 集两两不相交（备选 1 内的 `OFFSET` 仅出现在 `LIMIT expr` 之后，属备选内可选子句，不参与备选间预测竞争）。**保留 `limit 10 offset 5` 守护用例**锁定既有路径（防备选序调整引入回归）。
4. **token 面零改动**：`LIMIT`/`OFFSET`/`NEXT`（`nop-persistence/nop-orm-eql/model/antlr/Keyword.g4`）与 `FETCH`/`FIRST`/`ONLY`/`ROW`/`ROWS`（`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`）全部已是保留 token ⇒ 分页半区**只改 parser 规则、零 lexer 改动**、不触 token 重编号红线。`sqlExpr_limitRowCount`/`sqlExpr_limitOffset` = `sqlNumberLiteral | sqlParameterMarker` 既有规则原样复用（`?` 参数 marker 天然可用）。
5. **`OFFSET n ROWS`（无 FETCH 的独立 offset 形态）不进本 plan**——roadmap WI7 EC ① 只点名两形态；已登记 plan `Deferred But Adjudicated`（out-of-scope improvement，出现需求先登记 roadmap）。

---

## 三、裁定 3：NULLS 的门与默认序消费形态（三分）

**消费点**：`AstToSqlGenerator` 新增 `visitSqlOrderByItem` override（替代继承自 `AstToEqlGenerator:618-633` 的无门打印）。**三分形态**：

```
分支 0：node.getNullsFirst() == null（未指定）
    → 委托 super，输出与今天逐字节一致（BC 棘轮硬约束；缺省不主动补子句）
分支 1：dialect.isSupportNullsFirst() == true
    → 委托 super（继承行为 = expr + asc + 按请求打印 " nulls first "/" nulls last "，即请求什么打印什么）
分支 2：isSupportNullsFirst() == false 且显式请求
    → 缺省序推导（见下）；请求与该方向缺省序一致 → 省略子句（手打 expr + asc，行为等价、无损）；
      不一致或不可推导 → 编译期响亮报错 ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE
      （.param(ARG_DIALECT, 方言名).param(ARG_FEATURE, "nullsFirst(first|last)")，复用既有错误码，
      AstToSqlGenerator lateral/forUpdate/ilike+escape/INTERSECT 哨兵门先例 :272/:289/:538/:580）
```

**缺省序推导（分支 2）**：

- **ASC 方向**：缺省序事实 = `dialect.isDefaultNullsFirst()`（true→缺省 nulls first，false→缺省 nulls last）。
- **DESC 方向**：按"nulls 最小/最大"镜像模型推导 = `!isDefaultNullsFirst()`（缺省 nulls 最小 ⇒ ASC 在前、DESC 在后，反之亦然）。
- **一手文档准入（evidence gate）**：镜像推导与 ASC 事实的**省略**决策只在"该方言缺省 NULL 序已有一手文档引证"时成立。文档已证方言集（本裁定登记，实现以常量集围栏）：
  - `mysql` / `mysql5.7` / `mariadb`（继承 mysql 的已证行为）：MySQL 手册"NULL values are presented first if you do ORDER BY ... ASC and last if you do ORDER BY ... DESC"（ASC 缺省 FIRST、DESC 缺省 LAST）。
  - `mssql`：MSSQL 文档将 NULL 视为最低值（ASC 缺省 FIRST、DESC 缺省 LAST）。
  - `h2`：`LOW`/`HIGH` 方向相对（H2 文档"NULL values are considered as smaller than other values **during sorting**"，`h2.dialect.xml:9-12` 既有引文；LOW=ASC FIRST/DESC LAST）。注：h2 `supportNullsFirst=true`，实际不可达分支 2，登记于此仅为文档完备。
- **`dm` / `tdengine`：无一手文档（红线 `[未取证]`）⇒ 两个方向的显式 `nullsFirst` 请求一律编译期报错**——其 XML 配置的 `defaultNullsFirst` 值是登记事实但**不作等价性主张**（省略子句 = 声称"缺省行为与请求一致"，无文档支撑即为静默语义赌注）。plan Phase 2/3 负向用例的"`dm`/`tdengine` 显式请求一律报错"按此执行。
- **`db2`（显式列名，裁定纪律）**：`defaultNullsFirst` 未配置 → 模型 null → accessor `false`（保守缺省，`TestDefaultNullsFirstFlag.testInheritanceAndUnconfiguredDefault` 已钉死该语义）。db2 文档在本仓证据面不可得（IBM 文档 403，roadmap 多处登记）⇒ **db2 不入文档已证方言集，两个方向的显式 `nullsFirst` 请求一律报错**——这是"按 ASC=false 保守语义处置"的最严格形态：不做任何"缺省序一致"的等价性主张、不在执行期临时裁量放行或补配置（plan Phase 0 明文禁止）。 accessor `false` 的事实保留，供未来取得一手文档后把 db2 纳入已证集时复用。
- **`oracle`/`postgresql`/`duckdb`**：`supportNullsFirst=true`，永远走分支 1（请求即打印），不进入缺省序推导——单布尔无法表达的方向相关差异（EC ⑤）在分支 1 内自然消解。
- **其余/未来方言（未入已证集且 support=false）**：与 dm/tdengine 同纪律，一律报错（响亮失败优于静默赌注；新方言取得一手文档后加入已证集即可放开）。

**被拒备选（记录在案）**：
- 一律报错不做一致省略——误伤 mysql/mssql 上与缺省序一致的合法请求（plan Phase 0 明文拒绝）。
- 一律打印含缺省补全（`nullsFirst==null` 也补子句）——打破既有语料逐字一致（BC 棘轮硬约束，plan Phase 0 明文拒绝）。
- 接受语法后对不支持方言静默丢请求——BUG-1 同型静默降级，排序原则 P1 禁止。
- 表达式改写 lowering（MySQL `f IS NULL, f` 模拟）——xdef javadoc 设想、全仓零实现；引入即扩大结果面，Non-Goals 显式排除。

**回显侧不动**：`AstToEqlGenerator.visitSqlOrderByItem`（`:618-633`）的无门打印保持原样——回显是源文本规范化通道，不做方言能力判断（与 lateral/forUpdate 门只设在执行路径的先例一致）。

---

## 四、裁定 4：`supportNullsFirst` accessor 落地形态

**结论**：手写零生成。

- `IDialect` 新增 `boolean isSupportNullsFirst();`（javadoc：是否原生接受 `NULLS FIRST/LAST` 子句；与 `isDefaultNullsFirst()` 正交；消费点 = `AstToSqlGenerator` 分支 1 的唯一门；未配置返回 false 保守缺省）。`IDialect.java:157`（WI3 预写的前向 `{@link #isSupportNullsFirst()}`）随 accessor 落地**转正**。
- `DialectImpl` 实现：`return Boolean.TRUE.equals(dialectModel.getFeatures().getSupportNullsFirst());`（与 `isSupportFullJoin`/`isSupportRightJoin` 等 features accessor 同款惯例）。
- **零 xdef 改动**（`dialect.xdef:54` features 属性已声明 `supportNullsFirst`）、**零 retention 改动**、**零生成**（`_DialectFeatures.getSupportNullsFirst()` 冻结生成类已有 getter，`:348-350`）。
- 测试升级：`TestDefaultNullsFirstFlag.testSupportNullsFirstUntouched` 从模型层断言（`getDialectModel().getFeatures().getSupportNullsFirst()`）升级为 accessor 断言（`dialect.isSupportNullsFirst()`），**原三测断言值不变、不弱化**；新增 accessor 契约**双侧**断言——true 侧：duckdb/h2/oracle/postgresql（显式 true）；false 侧：mysql/tdengine（features 未配置 `supportNullsFirst` → 模型 null → accessor false，`Boolean.TRUE.equals` 保守缺省语义钉死，防"缺配置被读成 true"回归）。javadoc 中"accessor 归 WI7"移交措辞随升级顺带更新（plan Non-Blocking Follow-ups 第 4 条）。

---

## 五、裁定 5：`ORDER BY … COLLATE` ledger 二选一 = **显式移出 + successor**

**结论：显式移出**（EC ⑦ 的两种合法结局之一），不做落地。

**裁定输入（2026-10-04 live 实测复核）**：
- `SqlOrderByItem.collate` 字段已在冻结模型（`EqlAST.xjava` SqlOrderByItem 第二字段）但**全仓零生产读取**（`getCollate()` 生产读取方 0 处，roadmap"零生成器消费 AST 字段 7 项"之一）。
- `sqlOrderByItem` 文法（`BaseRule.g4:416-418`，全路径见 §〇①）无 collate 赋值路径；`collateClause_`（`:452-454`）只被 `sqlTypeExpr`（`:425`）引用且是 WI2 BUG-15 登记的**永久不可达**分支（期望 `STRING_` 隐式 token，`utf8` 等词法为 `IDENTIFIER_`）。
- `COLLATE` 保留 token 存在（`nop-persistence/nop-orm-eql/model/antlr/SQL92Keyword.g4`）。
- ledger 行自身理由（roadmap「明确延后项」）：MySQL 子句位置不同 + MSSQL collation 是库级作用域而非表达式属性；Oracle 走 `NLSSORT` 体系——**跨 mysql/mssql/oracle/pg 无损归一不成立**。

**裁定依据**：roadmap 约束 3（不满足"能无损 lowering"的项一律不做）+ 排序原则（P1 静默错误 > 响亮失败：接受语法却无法可靠 lowering = 制造 BUG-1 同型静默错误风险）。

**被拒备选（落地）**：仅在能给出逐方言无损 lowering 矩阵时成立——当前证据面不支持（Oracle 语义等价物是 `NLSSORT(expr)` 函数改写而非子句、MSSQL 的表达式级 COLLATE 与列/库级 collation 作用域纠缠、MySQL 子句位置与标准不同）。若未来翻案为落地，须同步回滚 plan Non-Goals 第四条，并按"文法 + `SqlOrderByItem.collate` 接线 + 各库作用域文档"三件套重排。

**Successor**：roadmap「明确延后项」新增单独调研项（**触发条件 = 出现"按表达式 collation 排序"的真实产品需求**）；调研产出逐方言作用域与 lowering 矩阵后再议落地。roadmap ledger 行在本 plan Phase 4 收口时改写为已裁定状态（不再"挂账无人认领"）。

**`SqlOrderByItem.collate` 字段处置**：**保留不动**——冻结生成模型，删除属 `EQLAST.xjava` 变更且无行为收益；字段继续留在 roadmap 7 项零消费清单，不由本 plan 清理。owner doc 落"各库 collation 作用域差异"小节（Phase 4）。

---

## 六、约束 4 两类用例清单 + EC ⑤ 满足形态登记

### 6.1 "一致性 + 不等价"两类用例清单（roadmap 约束 4）

| 结果面 | 一致性用例 | 不等价登记 |
|---|---|---|
| 分页 | 同语义两形态归一化后 AST 等价：`fetch first 5 rows only` 与 `limit 5` 装入相同 `SqlLimit{limit=5, offset=null}`；回显同为规范化 `limit 5` | 同一 LIMIT 语义在双族 handler 下输出形态不同：h2（LimitOffset 族）`LIMIT 5` vs oracle（OffsetFetch 族）`OFFSET 0 ROWS FETCH FIRST 5 ROWS ONLY`——输出形态由方言配置决定（裁定 1 依据 1），Phase 1 两族输出断言即该登记的用例化 |
| NULL 序 | "一致省略"= 一致性：mysql `asc nulls first`（与缺省序一致）省略子句、行为等价 | "冲突报错"= 响亮失败：mysql `asc nulls last` 编译期报错；DuckDB-vs-PG `DESC` 缺省差异 = 不等价登记（见 6.2） |

### 6.2 EC ⑤ 满足形态登记（本 plan 起草时已识别、在此正式落档）

DuckDB 与 PG 的 `defaultNullsFirst` 均 `false`（`duckdb.dialect.xml:9`/`postgresql.dialect.xml:5`），且本裁定三分形态对 `nullsFirst == null` 缺省不补子句（分支 0 BC 硬约束）⇒ **"缺省输出文本差异"的编译期用例不可能存在**（两库在该场景下输出逐字节相同）。EC ⑤ 的"差异用例"按 roadmap 原文的并列要求以**两件**满足：

1. **显式子句 parity 用例**（Phase 3）：`order by … desc nulls last` 在 duckdb 与 postgresql 上**都**输出 `nulls last` 子句（两者 `supportNullsFirst=true`）——证明两库均可经显式子句表达同一语义（表达力 parity），而缺省时两库行为不同。
2. **owner doc 差异表行**（Phase 2/3 文档面）：`eql-and-database-compatibility.md` 记录"缺省时两库行为不同——DuckDB keeps `NULLS LAST` even for `DESC` ordering, whereas PostgreSQL places `NULL`s first on `DESC`（DuckDB 官方文档引文，`duckdb.dialect.xml:18` 既有注释同引）——且单个 `defaultNullsFirst` 布尔不可表达方向相关差异（H2 `LOW`/`HIGH` 方向相对 vs `FIRST`/`LAST` 方向无关是更强证据，WI3 表第 11 行登记）"。本 plan 不解决该表达力缺口（roadmap EC ⑤ 明文），只锁定证据与文档（plan Deferred：watch-only residual）。

---

## 七、裁定与 plan 各 Phase 的对应

| 裁定 | 消费 Phase |
|---|---|
| 裁定 1（候选 A） | Phase 1 文法与归一化；Phase 4 roadmap WI7 行定价回写的依据 |
| 裁定 2（文法细节） | Phase 1 `.g4` 三备选 + 手写 visit |
| 裁定 3（三分门） | Phase 2 `AstToSqlGenerator` override + 8/4 方言断言；Phase 3 文法激活后的行为面 |
| 裁定 4（accessor） | Phase 2 `IDialect`/`DialectImpl` + `TestDefaultNullsFirstFlag` 升级 |
| 裁定 5（COLLATE 移出） | Phase 4 roadmap ledger 行收口 + owner doc COLLATE 小节 |
| §6 用例清单 | Phase 1/2/3 的用例设计与断言文本 |

---

## 八、Phase 3 执行期实测修订：NULLS 必须是显式大小写不敏感 lexer token（2026-10-04）

plan 起草时"NULLS 走 `unreservedWord_` 加词 = WI5 遮蔽放开同款 = 零 lexer 改动"的前提**被实测推翻**：

1. **WI5 五词（DAY/HOUR/MINUTE/SECOND/QUARTER）全部是 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4` 既有保留 token**——"遮蔽放开"只是把既有 token 收进 `unreservedWord_`（标识符位置可匹配）。`NULLS` 在全部 9 个 antlr `.g4` 文件中**零 token**，往 `unreservedWord_` 里写 `NULLS` 只会创建**隐式 token**（antlr warning 125，同 `characterSet_` 的 `STRING`）。
2. **隐式 token 无 lexer 规则可产生 ⇒ 永不可达**（BUG-15 登记的 "`STRING` 永久不可匹配" 同形态实测）：red-phase 文法落地后小写/大写 `nulls` 文本仍解析失败（`nop.err.antlr.not-end-properly`，offendingToken=nulls）。
3. **修正 = `nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 显式新增 `NULLS : N U L L S;`**（fragment 组合 = 大小写不敏感，Keyword 惯例）：定义在 `IDENTIFIER_` 之前（lexer 优先级 = 定义序，否则 `nulls` 文本被 `IDENTIFIER_` 抢走）；`Literals` 是最后 import 的 lexer grammar ⇒ `NULLS=353`，仅其后 5 个 Literals token（`IDENTIFIER_`/`STRING_`/`NUMBER_`/`HEX_DIGIT_`/`BIT_NUM_`）序号顺延 +1，**Keyword/SQL92Keyword 全部 352 个 token 序号零变化、零 token 删除**（"不删除任何 lexer token"红线不触；token 均按名引用，`.tokens`/`EqlLexer`/`Eql.interp` 同批重生成）。
4. `unreservedWord_` 收编 `NULLS` **仍然必要且保留**（plan 该半句成立）：真实 NULLS token 产生后，`nulls` 作列名/别名等标识符位置须经 `sqlIdentifier_ = IDENTIFIER_ | unreservedWord_` 匹配（守护用例 `TestEqlNullsFirstSyntax.testNullsAsIdentifierStillParses`/`testNullsAsAliasStillParses`）。
5. `sqlOrderByItem` 扩展为 `expr=sqlExpr asc=(ASC | DESC)? (NULLS nullsFirst=(FIRST | LAST))?`，生成体新增 `SqlOrderByItem_nullsFirst(Token)` 抽象转换方法（Boolean 三态，`SqlOrderByItem_asc` 同构），手写实现 `return isToken(token, EqlParser.FIRST)`；无 NULLS 子句时生成体 null 检查保证不赋值（保持 Boolean null 三态）。
6. **语料基线棘轮**：WI0 快照唯一 drift = 2 条 parse-error 诊断转储的 expected-token 集合新增 `NULLS`（解析结论与回显文本逐字不变，WI5 Phase 5 同型），快照已照棘轮规程重生成并复核。
