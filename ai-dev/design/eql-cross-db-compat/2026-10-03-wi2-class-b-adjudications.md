# EQL 跨数据库兼容性：WI2 B 类缺陷修法与能力门裁定（BUG-4 字面量回显 / BUG-6 门消费点与方言值 / BUG-15 拒绝落点 / mariadb 显式配置）

**日期**：2026-10-03
**范围**：`ai-dev/plans/19-eql-wi2-class-b-defects.md`（下称 WI2 plan）Phase 0 要求的四项裁定——BUG-4 字面量一致化验收形态、BUG-6 能力门消费点（门方法定名与挂载点）、BUG-15 显式拒绝落点、`mariadb.dialect.xml` 是否显式重复配置 `supportFullJoin="false"`。影响面：`nop-persistence/nop-orm-eql`（生成器、文法、transform）、`nop-persistence/nop-dao`（方言 accessor 与方言 XML）
**状态**：active
**授权模式**：WI2 plan Phase 0 授权的执行期裁定；方言 XML 值变更的授权依据 = WI2 plan 差异登记第 2 条（roadmap WI2 EC 原文"draft review 已通过"），G-4 批准范围（WI3 的 11 项）不含本值。人可推翻——推翻时按各节"回滚单位"撤销并追加修订裁决。

---

## 一、裁决总表

| 裁定点 | 结论 | 回滚单位 |
|---|---|---|
| BUG-4 字面量一致化 | **候选 A：回显保持 EQL 语法形态，消除语义分歧**——字符串转义维持 `escapeSql(value,false)` 不变（已是 EQL 规范形、round-trip 等价）；布尔字面量回显由 `" 1 "/" 0 "` 改为 `" true "/" false "`（可重新解析回 `SqlBooleanLiteral`）。拒绝候选 B（注入 dialect） | `AstToEqlGenerator.visitSqlBooleanLiteral` 一处，单独 git revert |
| BUG-6 门消费点 | **编译期门，独立私有方法 `checkJoinTypeFeature(SqlJoinTableSource)`**，挂载于 `EqlTransformVisitor.visitTableSource` 的 `SqlJoinTableSource` 分支、`visitJoinLeft/visitJoinRight` 之前（先门后遍历，仿 `checkWindowFrameFeature`）；错误码复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + 新常量 `FEATURE_SUPPORT_FULL_JOIN` | 门方法 + 常量，单独 git revert |
| mariadb 显式配置 | **显式配置 `supportFullJoin="false"`**（继承 mysql 已可得 false；显式化换契约自文档性，与 WI1 mariadb `supportLateral="false"` 负向载体先例一致） | `mariadb.dialect.xml` 一行，单独 git revert |
| BUG-15 拒绝落点 | **候选 A：解析期拒绝**——手写 `SqlTypeExpr_characterSet`/`SqlTypeExpr_collate`（`EqlASTBuildVisitor`）改为无条件抛错（方法被调用即抛，即"分支可达"本身就是要拒绝的事件）；新错误码 `ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT`。拒绝候选 B（编译/生成期检查字段非空） | 两个 accessor + 新错误码，单独 git revert |

---

## 二、BUG-4 字面量一致化：候选 A（EQL 语法形态 + 消除语义分歧）

### 结论

回显路径（`AstToEqlGenerator`）继续输出 **EQL 语法形态**的字面量，收口点是"同一 AST 经回显文本重新解析后字面量语义不变"，不是"与执行路径的方言渲染逐字对齐"：

1. **字符串字面量：不改动**。`visitSqlStringLiteral` 维持 `" '" + StringHelper.escapeSql(value, false) + "' "`。执行路径（`AstToSqlGenerator:449` → `DialectImpl.getStringLiteral`）= `"'" + escapeSql(value, dialectModel.sqls.escapeSlash) + "'"`：对 `escapeSlash=false` 方言（h2/mssql/es）两者逐字相同；对 `escapeSlash=true` 方言（default 继承链/mysql/dm）差异仅在反斜杠转义，而 `''` 双写是 SQL 标准形态、`EqlParseHelper.stringLiteralValue` 可无损逆解析。回显无方言可得（见被拒候选 B），EQL 规范形是唯一确定的 round-trip 安全形态 ⇒ 维持现状即满足候选 A 的验收口径（含单引号字符串各有断言：回显含 `''` 双写、重解析后值还原）。
2. **布尔字面量：改回显形态**。`visitSqlBooleanLiteral` 由 `print(node.getValue() ? " 1 " : " 0 ")` 改为输出 `" true "`/`" false "`。理由：`1`/`0` 重解析为 `SqlNumberLiteral`——**类型语义分歧**（boolean→integer），是两条路径间唯一的真实语义分歧；`true`/`false` 是 EQL 关键字（`Keyword.g4:269/273`、`sqlBooleanLiteral` 文法），重解析回 `SqlBooleanLiteral` 同值。执行路径已正确消费 `dialect.getBooleanValueLiteral`（`trueString`/`falseString` 配置），不需要动。
3. **`selectAll`/`LIMIT` 回显补齐**（BUG-4 本体）：`printSelect` 在 projections 为空且 `selectAll` 为真时打印 `*`；`limit` 非空时经新 protected 钩子 `printLimit(SqlQuerySelect)` 输出（复用 `visitSqlLimit`，不删除它——`_SqlQuerySelect` 遍历链仍依赖）。**执行期精确化**：`selectAll && !projections.isEmpty()`（transform 展开后的 AST，如分页内嵌 `PrintSqlSelect` 渲染路径）必须继续打印展开投影，否则 `AstToSqlGenerator` 分页路径会把 `select *` 文本嵌进外层 LIMIT 包装 SQL、破坏实体列展开 ⇒ 条件写成 `selectAll && projections.isEmpty()` 才打印 `*`。`printLimit` 在 `AstToSqlGenerator` override 为空体——执行路径的 limit 全部在 `visitSqlQuerySelect` 经 `dialect.getPaginationHandler().buildPageExpr(...)` 消费（在外层追加 LIMIT/OFFSET 文本），`printSelect` 内再打印会**双份 LIMIT**（该路径经 `printWrappedNode` → `printSelect` 到达）。
4. **"是否改变简单字符串/布尔字面量的回显渲染"的明确回答**（plan Phase 0 第 2 项挂钩项）：简单字符串——**不改变**；布尔字面量——**改变**（`1`/`0` → `true`/`false`）。WI0 语料快照 34 条 echo 实测零布尔字面量、零内嵌引号字符串（执行当日复核，见第四节）⇒ 两项改动**均不触碰任何语料 echo** ⇒ **无需重生成快照**，Phase 1 BC 判据以 WI0 既有快照为基准。

### 被拒候选与原因

- **候选 B（回显生成器注入 dialect，使字面量渲染逐字对齐 `dialect.getStringLiteral`/`getBooleanValueLiteral`）**：被拒。`EqlASTNode.appendTo`（`EqlASTNode.java:26-29`）硬编码 `new AstToEqlGenerator(sb)`，`toSqlString()`/`toSQL()`/`appendTo(SQL.SqlBuilder)` 整条公开调用链无 dialect 可得；注入意味着改 AST 公开签名链或引入"默认方言回退"语义含糊形态，且与 roadmap 修法原文（"也走 dialect"）在无 dialect 的回显路径上不可直接落地（WI2 plan 差异登记第 6 条的定价结论）。收益（逐字对齐方言渲染）本身不可取：回显文本必须方言无关才能 round-trip。
- **候选"布尔回显维持 1/0"**：被拒。boolean→integer 的类型分歧是本缺陷要消除的语义分歧本体，维持即不收口。

### 边界

- `interval`/datetime/hex/bit/null 字面量的回显形态本缺陷不动（corpus 已锁定其既有形态；datetime 回显 `DATE '…'` 在 Phase 3 的双断言中作为钉死文本消费）。
- `left outer join` 回显丢 `OUTER`、`inner join` 回显丢 `INNER` 为既有 BUG-4 族形态，本 plan 不扩scope（WI1 `TestEqlCrossJoinParse.testLegacyJoinFormsUnchanged` 已按现状锁定；`full outer join` 回显 `full join` 与之同族）。

---

## 三、BUG-6 能力门消费点：编译期 `checkJoinTypeFeature(SqlJoinTableSource)`

### 结论

- **门形态**：`EqlTransformVisitor` 新增独立私有方法 `checkJoinTypeFeature(SqlJoinTableSource source)`：`source.getJoinType() == SqlJoinType.FULL_JOIN && !dialect.isSupportFullJoin()` 时抛 `NopException(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE).param(ARG_DIALECT, dialect.getName()).param(ARG_FEATURE, FEATURE_SUPPORT_FULL_JOIN)`。新常量 `OrmEqlConstants.FEATURE_SUPPORT_FULL_JOIN = "supportFullJoin"`（照 `FEATURE_SUPPORT_LATERAL`/三个 window frame 常量先例）。
- **挂载点（执行期修订）**：`visitTableSource` 的 `SqlJoinTableSource` 分支内，**`visitJoinLeft` 之后、`visitJoinRight` 之前**——裁定原拟"分支头部（先于 visitJoinLeft/Right）"，落地时发现方言实例要到首个实体解析（`addResolvedEntity` → `context.getDialectForQuerySpace`，:776-782）才确定，头部触发时 `dialect` 尚为 null（实測 NPE）；左源（实体/嵌套 join/子查询/CTE）遍历完成后方言必然就绪且与后续生成用同一实例，语义分叉为零。保留的性质：每个 join 节点恰过门一次（嵌套 join 经 `visitJoinLeft` 递归回本分支；`sqlTableSource_joinRight` 文法禁右侧为 join）、仍先于 `visitJoinRight` 的 `ERR_EQL_JOIN_NO_CONDITION` 条件校验与右侧任何解析错误。
- **accessor 接线**：`IDialect.isSupportFullJoin()`（照 `IDialect.java:89-93` window frame 三连先例）+ `DialectImpl` 实现 `Boolean.TRUE.equals(dialectModel.getFeatures().getSupportFullJoin())`（照 `DialectImpl.java:287-297` 先例）。**零生成**：`dialect.xdef:55` 已声明、`_DialectFeatures.java:290` 已有 getter，均不动。
- **方言 XML**：`mysql.dialect.xml` 显式 `supportFullJoin="false"`；`mariadb.dialect.xml` 显式 `supportFullJoin="false"`（裁定见第五节）。`default.dialect.xml:23` 的 `true` 不动（TDengine ≥3.3.0.0 / MSSQL 确认支持者继承它，roadmap WI7b 节证据）。
- **测试载体分层**（与 WI1 `TestDialectLateralFlag` + `TestEqlForUpdateDialectGate` 同款双模块链）：① nop-dao 侧经 `DialectManager` 真实加载 `mysql`/`mariadb` XML 断言 accessor 返回 false（XML→模型→accessor 链）；② nop-orm-eql 侧手构 `DialectFeatures` 两值方言（`supportFullJoin` true/false），同一 `full outer join` 查询断言行为分叉——false 报错误码与 feature param、true 输出含 `full join` 文本（flag 值真实改变编译行为，Minimum Rules #23 接线验证）。两测试合起来即"mysql 配置值 → 门行为"的运行时连通证明。执行期若 nop-orm-eql 测试环境可直接 `DialectManager.instance().getDialect("mysql")`（`TestH2WindowFrameCompileEndToEnd` 先例证明 h2 可），则 ①②可合并为真 mysql 全链负向用例，以实测为准。

### 选择理由

1. **编译期门是 roadmap WI2 EC 原文要求**（"编译期报错负向用例"），生成器侧消费（备选）只能拦 SQL 产出、拦不住 `@dump`/缓存等更早出口，且与本 roadmap 既有全部能力门（window frame 三位、lateral、returning、ilike）的"compile 期 fail-fast"纪律不一致。
2. **独立方法而非挂进 `markMainSource`**：`markMainSource` 职责是主源标记（LEFT/RIGHT/FULL 的 main 语义），在只读标记遍历里埋编译期报错会混淆职责、且它对非 join 表源也执行；门挂在 `visitTableSource` 的转换遍历点与 `checkWindowFrameFeature` 挂在窗口 decl 遍历点同形。
3. **错误码复用而非新增**：`ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` + `ARG_DIALECT`/`ARG_FEATURE` param 已精确表达"方言不支持该特性"，feature 常量即机器可读的标志名，负向断言可同时钉死错误码与 param 值。

### 被拒候选与原因

- **备选（生成器侧消费）**：被拒。理由 1；且 AstToSqlGenerator 侧 full join 无独立 visit 方法（复用基类 `visitSqlJoinTableSource` 的打印），消费点反而更远。
- **备选（挂 `markMainSource`）**：被拒（理由 2）。
- **备选（不落 mysql/mariadb false，仅文法放开）**：被拒——这正是 roadmap 定义的反模式"只打印不设门 = 净回归"（anti-hollow 扫描的目标形态）：文法放开后 mysql 上 `full outer join` 从解析失败（响亮但报错形态差）变为静默产出 mysql 不支持的 SQL。

---

## 四、WI0 产物状态核验（Phase 0 第 1/2 项）

- **语料基线快照**：`ai-dev/audits/eql-wi0/eql-corpus-baseline.md` 在位；`TestEqlCorpusBaseline` 于本 Phase 执行当日（2026-10-03，WI1 已收口后的代码基线）运行通过——WI1 六项落地未触碰任何语料回显文本，快照无 drift。
- **方言矩阵快照**：`ai-dev/audits/eql-wi0/dialect-capability-matrix.json` 在位；快照 entry 键 = `extends`/`functions`/`loadable`/`pseudo`，**不含 features 字段** ⇒ BUG-6 的 `mysql`/`mariadb` 值变更不触碰快照口径（`extends` 链、functions 表、loadable 均不变），`check-eql-dialect-matrix-snapshot.mjs` 预期 exit 0（终局验证再实测）。
- **零 diff 前提的两个字面量事实**（差异登记第 5 条）复核成立：语料 0 条 `select *`、0 条 limit/offset 语句；13+ 处简单字符串字面量（`'Fall'`×12 / `'Spring'`×6 / `'Biology'`×6，normalized source 与 echo 合计 24 处命中）全部无内嵌引号（`''` 双写 0 命中、`\'` 转义 0 命中）；布尔字面量 0 条（`TRUE`/`FALSE` 仅出现在 parse-error 的 expected token 集合）。⇒ 第二节裁定（字符串不改变渲染）+ 布尔语料零命中 ⇒ **Phase 1 无需快照重生成**。

---

## 五、mariadb 显式配置裁定

### 结论

`mariadb.dialect.xml` **显式**配置 `supportFullJoin="false"`（与 `mysql.dialect.xml` 的显式 false 同批、同一独立提交）。

### 理由

1. **自文档化价值**：mariadb 是 roadmap 确认的两家无 `FULL JOIN` 数据库之一、WI2 负向用例的点名载体；显式值让"该方言不支持"在方言文件自身可读，而不是经 `mariadb → mysql → default` 两跳继承推断。WI1 已为同一目的给 mariadb 落了显式 `supportLateral="false"`，先例一致。
2. **与 WI3 第 7 项"不要顺手改 mariadb"精神的关系**：该约束针对的是*改变 mariadb 配置语义*（如移除它确有资格的 window-expr mixin）；本项两方案（显式 false vs 继承 false）**运行时值完全相同**，行为零变化，只涉及显式与否——不属于"顺手改语义"。
3. **回滚单位最小**：一行 XML，独立提交可单独 revert。

### 被拒候选与原因

- **候选"仅 mysql 显式、mariadb 继承"**：被拒。功能上成立（继承链可得 false），但负向用例的读者需要追继承链才能确认 mariadb 行为，契约可见性差；且与 WI1 mariadb 显式 false 先例不一致。

---

## 六、BUG-15 拒绝落点：候选 A（解析期，手写 accessor 无条件抛错）

### 结论

`EqlASTBuildVisitor` 的手写 `SqlTypeExpr_characterSet`/`SqlTypeExpr_collate`（现 :370-379）由"读 token 文本返回"改为**无条件抛错**：方法被调用即抛 `NopException(ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT)`（新错误码，`OrmEqlErrors`；param `ARG_VALUE` 携带分支文本）。生成物写入点（`_EqlASTBuildVisitor.java:1345/1349` 的 `ret.setCharacterSet(...)`/`ret.setCollate(...)`）在 accessor 抛错时永不完成 ⇒ `SqlTypeExpr.characterSet`/`collate` 字段被结构性钉死为 null，直到有人有意修文法+实现语义（届时必先删除/改写这两个 accessor 的抛错并通过本 plan 的守护测试审视）。

错误码形态（模块惯例：`NopException` + `ErrorCode` + `.param(...)`，消息模板与 `OrmEqlErrors` 邻行同语言）：

```java
// WI2 BUG-15：SqlTypeExpr 的 characterSet/collate 零消费点（AstToEqlGenerator 只打印
// name/precision/scale，AstToSqlGenerator.visitSqlCastExpr 绕过 SqlTypeExpr），字段有值即静默丢弃。
// 访问器被调用 = 分支可达 = 显式拒绝，防止未来文法修复后退化为 BUG-1 同型静默丢弃。
ErrorCode ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT = define("nop.err.eql.sql-type-clause-not-support",
        "CAST类型表达式的[{value}]子句不支持翻译为SQL（无任何方言消费其语义），显式拒绝", ARG_VALUE);
```

### 选择理由

1. **离写入点最近、零生成**：accessor 是生成访问器调用的手写末端，拒绝在此处即字段写入的结构性闸门；不触生成管线。
2. **守护面最窄且完备**：字段非空的唯一通路就是这两个 accessor（生成代码 `if(ctx.characterSet != null)` 守卫的正是 accessor 调用）；accessor 无条件抛错 ⇒ 不存在绕过形态。候选 B 需要在 `AstToEqlGenerator.visitSqlTypeExpr` 与 `AstToSqlGenerator` 的 cast 路径（且 `visitSqlCastExpr` 根本不经过 `SqlTypeExpr` 节点的渲染入口）两处分别设防，面更宽、且只保护"今天的两个读取点"——未来新增消费者还得记得带检查。
3. **"不可达防护"语义最诚实**：accessor 被调用本身等价于"文法分支已可达"，在此抛错的语义正是 plan 所述"一旦可达即失败"。

### 被拒候选与原因

- **候选 B（编译期/生成期对字段非空抛错）**：被拒（理由 2/3）。另注：`EqlTransformVisitor` 挂检查还需找到 `SqlTypeExpr` 的遍历点（它在 cast expr 内部，transform 侧无独立 visit），实现面反而更绕。

### 守护测试形态（与裁定绑定，plan Phase 5 第 3 项的候选 A 版本）

- **不可达固化**（今天的现状）：`cast(a as varchar(10) character set utf8)` 与 `cast(a as varchar(10) collate xx)` 解析失败（antlr 错误码）——`STRING` token 无 lexer 规则（`EqlParser.java` token 表以 `STRING=358` 结尾、`EqlLexer.java:81` 到 `BIT_NUM_=357` 即止的 token 分叉证据）；`collate` 的 unquoted 形态词法为 `IDENTIFIER_` ≠ `STRING_`。若未来文法修复使这两条可解析，守护测试转红、强制实现者面对 accessor 的显式拒绝。
- **拒绝路径**（未来形态的等价触发）：手构 `EqlParser.CharacterSet_Context`/`CollateClause_Context`（生成类的 `characterSet`/`collate` 字段为 public Token，可注入 `CommonToken`），直调两个 accessor，断言抛 `ERR_EQL_SQL_TYPE_CLAUSE_NOT_SUPPORT`。证明"一旦可达即显式失败"。
- **执行期待核实事实**：quoted 形态 `collate 'xx'`（`STRING_` 可词法化）今天是否可解析——若可解析，则 collate 分支今天就有一条活的最小可达通路，accessor 守卫把它即刻转为响亮拒绝（守卫价值更高，测试同时锁定该输入的解析前/后行为）。

---

## 七、对 plan 文本的同步裁定记录

- Phase 1 的 BC 判据按第二节第 4 条结论执行：**不重生成快照**，以 WI0 快照为逐字基准。
- Phase 2 的门方法名/挂载点按第三节；方言 XML 按 mysql+mariadb 双显式 false、与文法提交/接线提交的拆分关系见 plan 生成物纪律（XML 值变更与 accessor 接线同批、独立于 `.g4` 提交）。
- Phase 5 的落点按第六节（候选 A），错误码与消息模板如上，守护测试形态按第六节"守护测试形态"。
- 本裁定不翻转 plan 状态、不勾 Closure Gates 的独立审计项；daily log 已同日记条。
