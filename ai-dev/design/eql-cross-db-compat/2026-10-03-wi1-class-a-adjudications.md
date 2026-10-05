# EQL 跨数据库兼容性：WI1 A 类缺陷修法与方言能力裁定（BUG-14 / interval 载体 / supportLateral / ESCAPE）

**日期**：2026-10-03
**范围**：`ai-dev/plans/03-eql-wi1-class-a-defects.md`（下称 WI1 plan）Phase 0 要求的四项裁定——BUG-14 集合下标修法、`sqlIntervalLiteral` 的 xdef 载体、`supportLateral` 缺省值与已确认支持方言清单、ESCAPE/LATERAL 两张矩阵的证据分派表与 ESCAPE 无条件输出的证据面登记。影响面：`nop-persistence/nop-orm-eql`（生成器、文法）、`nop-persistence/nop-dao`（方言模型）、`nop-kernel/nop-xdefs`（`dialect.xdef`）
**状态**：active
**授权模式**：WI1 plan Phase 0 授权的执行期裁定；证据等级纪律沿用 roadmap「关键约束与红线 → 证据等级纪律」（DM8/TDengine 无一手文档，矩阵格一律 `unverified`）。人可推翻——推翻时按各节"回滚单位"撤销并追加修订裁决。

---

## 一、裁决总表

| 裁定点 | 结论 | 回滚单位 |
|---|---|---|
| BUG-14 集合下标 | **候选 B：显式报错**（拒绝候选 A 机械回显） | 生成器 override + 新错误码，单独 git revert |
| `sqlIntervalLiteral` 载体 | **按 roadmap 口径：`<sqls>` 子元素，retention 落 `DialectSqls.java`** | xdef 元素 + retention 字段 + accessor，单独 git revert |
| `supportLateral` 缺省值 | **未配置 = 不支持（编译期报错）**；显式开启者 = postgresql / mysql / duckdb；mariadb 显式 `false` 作负向载体 | 各方言 XML 一行 + 四层接线，单独 git revert |
| BUG-1 ESCAPE | **执行路径无条件输出 `ESCAPE <literal>`**（无方言门）；ILIKE+ESCAPE 组合显式报错（复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`） | `AstToSqlGenerator.visitSqlLikeExpr`，单独 git revert |

---

## 二、BUG-14 集合下标：候选 B（显式报错）

### 结论

`SqlCollectionAccessExpr`（`select o.roles[1].x()` 一类输入）在两个生成器中**显式报错**：`AstToEqlGenerator` override `visitSqlCollectionAccessExpr` 抛新错误码（echo 与执行路径同门），错误码新增 `nop.err.eql.collection-access-not-support`（`OrmEqlErrors`，参数 `ARG_EXPR` 携带表达式文本）。目标形态：`select o.roles[1].x() from T o` 编译期报错，断言该错误码；不再产出 `select o.roles 1 from T o` 垃圾文本。

### 选择理由

1. **候选 A 没有正确的输出目标**。文法 `sqlCollectionAccessExpr` 的方括号内容是 `where=sqlExpr? (ORDER BY orderBy=sqlOrderBy)?`——它是**集合过滤语义**（Hibernate 集合过滤风格的残留），不是下标取值。`collFuncName` 全仓零读取、`collFuncArgs` 只有遍历器与优化器的子节点遍历，编译管线（`EqlTransformVisitor`）对该节点没有任何 lowering。候选 A"机械回显"产出 `o.roles[1].x()` 形态的文本——**没有任何 RDBMS 接受它**，按 WI1 plan 自己的 Goal（"不再静默产出错误 SQL——或输出正确 SQL 文本，或编译期响亮报错"）它仍是错误 SQL，只是从"乱码"变成"格式良好的不可执行文本"，不满足收口标准。
2. **显式失败先例一致**：`nop-metadata-service/SqlSelectFieldExtractor.java:124-127`（空 projections 抛 `ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED`，注释"显式失败、不静默跳过"）与 `AstToSqlGenerator` 的 ILIKE 门（"静默降级会改变语义必须显式报错"）同一原则。
3. ** WI7b/WI12 之外的后续 WI 如需真正实现集合访问语义**（如数组下标 lowering），届时按新能力走 roadmap 登记新增 WI，而不是在本 WI 伪造一个语义未定义的打印。

### 被拒候选与原因

- **候选 A（补齐打印：collection + 下标语义 + `. collFuncName(collFuncArgs)` 机械回显）**：被拒。理由 1（无正确输出目标）+ 打印侧"语义边界"无法定义（`[1]` 打成什么？WHERE 过滤文本？数组下标？两者语义相反）。机械回显只改善 echo 层观感，执行路径依旧错误。
- **候选"维持现状"**：被拒。这正是 A 类缺陷本体（静默产出垃圾 SQL、零信号）。

### 边界

- 今天已失败的输入（`o.roles[]`、`o.roles[order by a].x()`——antlr 文法层即失败）保持响亮失败，失败点不变（parse 期）。
- 集合操作符 `_some`/`_all` 走的是另一条 AST 路径（集合操作符 transform），不产生 `SqlCollectionAccessExpr`，不受影响。
- 回显路径同门：`AstToEqlGenerator` override 使 echo 与执行路径行为一致；WI0 语料 7 文件均不含该语法，基线 BC 不受影响。

---

## 三、`sqlIntervalLiteral` 载体：`<sqls>` 子元素 + `DialectSqls` retention

### 结论

按 roadmap 口径：`dialect.xdef` 的 `<sqls>`（`DialectSqls`）内新增 `<sqlIntervalLiteral>string</sqlIntervalLiteral>` 子元素（与 `<dateLiteral>`/`<forUpdate>` 同构）；retention 手写字段落 `nop-dao/.../model/DialectSqls.java`（现为空 retention 子类）；`IDialect` 新增 `getSqlIntervalLiteral()` accessor，`DialectImpl` 实现直读 `dialectModel.getSqls().getSqlIntervalLiteral()`（未配置返回 null/空——与 `getForUpdateSql` 同契约）。

### 理由

- interval 输出形态是**文本模板**（含 value/unit 槽位），与 `<dateLiteral>`（`DATE '{value:...}'`）同族；`<features>` 布尔位在类型上就装不下模板文本。
- `DialectSqls` 是 xdef 绑定 `<sqls>` 的 retention 类，元素按属性名反射 setter，零生成、不触 Protected Area（window frame 三属性同一先例）。
- 模板内容为空时生成器行为：WI1 骨架**不消费**该模板做改写（WI7b 唯一 owner），所以"未配置返回空"即为终态契约；WI7b 接线时对空模板的行为（回退默认形态或报错）由 WI7b 裁定并在 owner doc 登记。

### 被拒候选

- **features 布尔位（retention 落 `DialectFeatures.java`）**：被拒。布尔位只能表达"是否支持"，装不下模板；且与 `<dateLiteral>` 族的组织惯例相悖。
- **新增顶层元素（`<sqlIntervalLiteral>` 平级于 `<sqls>`）**：被拒。roadmap 明文"`<sqls>` 子元素，与 `<forUpdate>` 同构"；顶层元素会扩大 xdef 契约面。

---

## 四、`supportLateral`：缺省保守值与已确认支持清单

### 结论

- **`default.dialect.xml` 不显式配置**（xdef 属性可省略）⇒ `DialectImpl.isSupportLateral()` 对未配置方言返回 `false` ⇒ 编译期报错（复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`）。理由：按 G-1 裁决的 unverified 纪律，未获一手确认的边界必须保守；`default` 是多数方言的继承根，误开会在全体未显式配置方言上静默产出错误 SQL——比"保守报错+显式开启"危险得多。
- **显式开启（`supportLateral="true"`）**：`postgresql.dialect.xml`、`mysql.dialect.xml`、`duckdb.dialect.xml`。
- **显式关闭（`supportLateral="false"`）**：`mariadb.dialect.xml`（roadmap 已登记 MariaDB 无 LATERAL；该显式 false 同时是 WI1 plan Phase 2 负向用例的实配载体与文档化证据）。
- **其余方言不配置**（行为 = false）：h2 / mssql / sqlite 有"不支持"的文档证据（见矩阵），但按"只登记显式确认项"的最小配置原则不写显式 false；oracle / db2 / tdengine / dm8 维持 unverified ⇒ 保守 false。
- `es` 是伪方言，不参与能力矩阵（roadmap 红线）。

### LATERAL 能力矩阵（证据分派表）

| 方言 | LATERAL | 默认 escape 之外的证据 | 证据等级 | WI1 动作 |
|---|---|---|---|---|
| postgresql | 支持（9.0 引入；承诺基线 PG 15） | PG 文档 SELECT/LATERAL | [文档] | 显式 true |
| mysql | 支持（8.0.14 引入 lateral derived tables；承诺基线 8.0.31） | MySQL 手册 §13.2.11.9 | [文档] | 显式 true |
| duckdb | 支持（Friendly SQL：LATERAL 关键字可省略、自动识别相关子查询；PG lateral 测试套件移植） | duckdb.org docs/sql/dialect/friendly_sql | [文档] | 显式 true |
| mariadb | **不支持**（无 LATERAL 关键字） | roadmap WI1 表已登记 | [roadmap 登记] | 显式 false（负向载体） |
| h2 | 不支持（官方 grammar 无 LATERAL；Hibernate H2Dialect 明文 "H2 doesn't support … lateral"） | h2database.com grammar + Hibernate javadoc | [文档] | 不配置（=false） |
| mssql | 无 `LATERAL` 关键字（等价能力为 CROSS/OUTER APPLY，形态不同） | MS Learn T-SQL FROM | [文档] | 不配置（=false） |
| sqlite | 无 LATERAL 关键字（官方 grammar 无；相关子查询可替代） | sqlite.org lang grammar | [文档] | 不配置（=false） |
| oracle | 12.1+ 应支持，但 12.1 边界未获一手确认（G-1 已登记） | — | [unverified] | 不配置（=false，保守） |
| db2 | 11.5 支持，9.7 边界未获一手确认（G-1 已登记；DB2 i 7.5.0 文档亦含 LATERAL） | — | [unverified] | 不配置（=false，保守） |
| tdengine | 文档零提及（G-1 已登记） | — | [unverified] | 不配置（=false，保守） |
| dm8 | 无一手文档 | — | [unverified] | 不配置（=false，保守） |
| es | 伪方言 | — | — | 不参与 |

---

## 五、BUG-1 ESCAPE：无条件输出与证据面登记

### 结论（无条件输出已定案的证据面）

WI1 plan 差异登记第 4 条裁定 `AstToSqlGenerator` **无条件输出** `ESCAPE <literal>`（AST 携带 escape 时）。证据面：

1. 全仓不存在"该方言是否支持 LIKE ESCAPE"的字段/标志载体——"按方言改写"没有实现对象。
2. 证据面没有"不支持 ESCAPE 子句"的方言成员：下表已取证方言全部支持该子句，分叉只在**默认 escape 字符**。
3. 因此"不支持者改写为函数调用"（roadmap BUG-1 门控列后半句）当前不可实现，移入 Deferred But Adjudicated（触发条件 = 发现真实不支持者，DM8 优先复核）；BUG-1 无方言门、无负向错误码用例。
4. ILIKE+ESCAPE 组合：ilike 经方言函数改写（如 oracle `regexp_like({0},{1},'i')`）后 ESCAPE 子句无挂载点——静默丢弃会改变匹配语义，按 `AstToSqlGenerator:495` 原则**显式报错**，复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（feature=`ilike+escape`）。这是 BUG-1 唯一的报错路径，且是"组合不可降级"而非"方言不支持 ESCAPE"。

### ESCAPE 支持矩阵（证据分派表；owner doc 同步落档）

| 方言 | `ESCAPE` 子句 | 默认 escape 字符 | 证据等级 |
|---|---|---|---|
| oracle | 支持 | **NULL**（无默认 escape 字符） | [文档] |
| db2 | 支持 | **NULL**（无默认 escape 字符） | [文档] |
| h2 | 支持 | **backslash**（"The default escape character is a backslash. To select no escape character, use ESCAPE ''"） | [文档] |
| postgresql | 支持 | **backslash**（PG 文档自述偏离 SQL 标准——标准是无默认） | [文档] |
| mysql | 支持 | **backslash**（C escape 语法；`NO_BACKSLASH_ESCAPES` 开启时失效） | [文档] |
| mssql | 支持 | 无默认（用 `[]` 括号转义或显式 ESCAPE） | [文档] |
| sqlite | 支持 | 无默认（必须显式 ESCAPE） | [文档] |
| duckdb | 支持 | [unverified]（检索结果互相矛盾：一说 PG 风格 backslash，一说 LIKE 内无隐式 escape；不取证不计入） | [unverified] |
| mariadb | 支持 | [unverified]（MySQL 家族推测 backslash，未取一手文档） | [unverified] |
| dm8 | [unverified] | [unverified]（roadmap 红线明令） | [unverified] |
| tdengine | [unverified] | [unverified] | [unverified] |

ⓘ 三分类口径（roadmap WI8 节明令保留）：{Oracle, DB2} = NULL / {H2, PG} = backslash / {DM8} = 未取证。mysql 的 backslash 与 H2/PG 同属"隐式 backslash"组（本表按证据自然呈现，不改动 roadmap 三分类的措辞）。`ESCAPE '!'` 的输出文本本身（无条件输出）在所有已取证方言上语义一致——显式 escape 字符覆盖默认值差异，这是"无条件输出"在证据面上成立的第 4 条理由。

---

## 六、与已有设计的关系

- 上游：`2026-10-03-wi0-gate-adjudications.md`（G-1 承诺基线与 unverified 纪律，本文件全部 unverified 格的纪律来源）。
- 下游：WI2（BUG-4 回显治理将消费 BUG-1 的"回显侧 escape"遗留）、WI7b（`sqlIntervalLiteral` 模板内容唯一 owner）、WI12（LockOption/`for update` 的 NOWAIT/SKIP LOCKED）。
