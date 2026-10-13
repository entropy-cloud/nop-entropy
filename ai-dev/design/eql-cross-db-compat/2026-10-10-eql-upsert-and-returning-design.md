# EQL 写语句扩展设计 —— `upsert` 语句与 `insert/delete ... returning`

> Status: active
> Date: 2026-10-10
> 范围：`nop-persistence/nop-orm-eql`（文法/AST/语义分析/SQL 生成）、`nop-persistence/nop-dao`（方言能力位）、`nop-persistence/nop-orm`（语句种类与监听上报）
> Related Plan: `ai-dev/plans/2380-eql-upsert-and-returning-grammar.md`
> 上游裁决：`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（「明确不做的事」将 UPSERT 排除出语法层 + ledger `MERGE`/`UPDATE..FROM` 行的 WI11 终裁「显式移出」，successor = 本设计）
> 既有 upsert 设计：`2026-10-05-wi11-upsert-design.md`（`nop-dao` 批量 upsert 原语，与本文并存不互斥）

---

## 一、设计结论

1. **新增中立语法的 `upsert` 语句**，取 PG 家族习惯的 `on conflict ... do update set ...` 形态；方言差异全部下沉到 SQL 生成层，由**方言能力位**门控，取值范围限定为 **PostgreSQL 族 / MySQL·MariaDB 族 / H2 族**，其余方言**编译期响亮报错**，不做静默降级。
2. **`upsert` v1 不引入 `excluded` 伪表**。`do update set` 的右值是自足表达式；PG 上惯用的 `EXCLUDED.c` 在 EQL 里写成重复同一参数（`values (…, ?) … do update set name = ?`）。这是 v1 最主要的表达力取舍，理由见 §4.1。
3. **`INSERT ... RETURNING` 与 `DELETE ... RETURNING` 补齐**，与既有的 `UPDATE ... RETURNING` 并列；新增两个**独立能力位**，既有 `supportReturningForUpdate` **零改动**（保持 BC 与既有方言取值）。
4. **方言能力位新增 3 个、且仅 3 个**（`supportUpsert` / `supportReturningForInsert` / `supportReturningForDelete`），全部走 `nop-dao` 的**手写 retention 类**，零 codegen。
5. **EQL 语法层 upsert 与 `nop-dao` 批量 upsert 原语并存，互不替代**：前者服务于 sql-lib / BizModel 的声明式写路径并进入统一缓存与分片/监听链路；后者服务于绕过 ORM 的批量 JDBC 直写路径。两者共享同一套方言形态认知，但**不共享代码**（详见 §3.9）。

---

## 二、背景与动机

### 2.1 缺口

- EQL 文法顶层语句只有 `SELECT` / `INSERT` / `UPDATE` / `DELETE` / `WITH ... SELECT` / `COMMIT` / `ROLLBACK`（`nop-persistence/nop-orm-eql/model/antlr/Eql.g4:32-51`）。`INSERT` 语法上不接受任何冲突处理子句（`DMLStatement.g4:23-26`，`:22` 是注释行），撞键即抛异常。
- `RETURNING` 在文法里只挂在 `sqlUpdate` 上（`DMLStatement.g4:32`），`insert` 与 `delete` 都没有该子句位。
- 结果是两类需求无解：①"按自然键幂等写入"必须绕到 sql-lib 原生 SQL 或 `nop-dao` 原语；②"插入后拿回自增主键 / 删除后拿回旧值"必须先查再写，两次往返且非原子。

### 2.2 为什么现在做

- WI11 交付的 DAO 原语把 upsert 的**方言形态认知**（PG / MySQL / H2 三族分派、key-only 形态、重复键判定三段口径）沉淀到了 `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/UpsertStatements.java`，方言映射不再是未知量。
- roadmap 对 `MERGE` 的 WI11 终裁写明 successor 触发条件为"出现 EQL 层条件 upsert / `WHEN MATCHED THEN DELETE` / `BY SOURCE` 的真实产品需求"。**本设计只覆盖条件 upsert 之前的最小充分集**，不触碰那三类语义，因此不改变该终裁，只补上 successor 链条上的第一环。
- `RETURNING` 侧的既有能力（`supportReturningForUpdate` 门 + 投影展开 + 表达式类型解析）已在 `EqlTransformVisitor.visitSqlUpdate` 完整落地，扩展到 insert/delete 是同构复制而非新机制。

### 2.3 需求边界（用户侧口径）

- upsert 只做**最常用子集**：单语句 + 冲突键 + 覆盖式更新集，**不要求全部数据库支持**。
- returning 补 `INSERT` 与 `DELETE` 两个子句位。
- 方言范围锁定 PostgreSQL 族 / MySQL·MariaDB 族 / H2 族。

---

## 三、核心设计

### 3.1 `upsert` 语法契约

```
upsert into T (c1, ..., cn)
    values (v1, ..., vn)
    on conflict (k1, ..., km)
    do update set a = expr, ...
```

| 组成 | 语义 | 约束 |
|---|---|---|
| 目标表 `T` | 写入表，按实体名或表名解析 | 必须能解析为已注册实体；无别名（与 `insert` 对齐） |
| 列清单 `(c1..cn)` | 本次写入的列，顺序即占位符顺序 | 非空；顺序自由但**必写全**，无隐式补齐 |
| 值清单 `values (...)` | 与列清单一一对齐的值 | 非空；**仅支持 `VALUES` 形态**（不支持 `upsert into T (...) select ...`） |
| 冲突键 `(k1..km)` | 判定"同一行"的唯一键列 | 非空；**须 ⊆ 列清单**（编译期强校验，H2 的 `MERGE ... KEY` 要求键列在 `VALUES` 列清单内，PG/MySQL 不要求——不统一就会变成跨库行为分叉）；须命中实体声明的唯一键或主键（§3.5） |
| 更新集 `do update set` | 撞键时被覆写的列 | 非空；每个赋值目标列须属于目标表 |

**关键字契约**：`upsert` 与 `conflict` 是 EQL 新增的 unreserved 关键字——词法 token 定义在 `nop-persistence/nop-orm-eql/model/antlr/Keyword.g4`（与 `INSERT`/`UPDATE`/`ON`/`DO`/`SET` 同族；该文件的 token 因 import 顺序天然排在 `nop-persistence/nop-orm-eql/model/antlr/Literals.g4` 的 `IDENTIFIER_` 之前，位置不受 `IDENTIFIER_` 顺序约束），并登记进 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `unreservedWord_`，使 `upsert` / `conflict` 在列名、表名、别名位置仍可解析。登记不产生语句起始歧义：`sqlUpsert` 与 `sqlDelete` 的首 token 不同，`delete from T upsert` 走 delete 的别名分支。

**语义**：未撞键时按 `insert` 插入本行；撞键时只执行 `do update set` 中的赋值，其余列保持原值。整条语句在目标库侧是**单语句原子**的。

### 3.2 `returning` 语法契约

```
insert into T (c1, ..., cn) values (v1, ..., vn) returning * | p1, ..., pk
delete from T [as] a [where ...] returning * | p1, ..., pk
```

| 组成 | 语义 | 约束 |
|---|---|---|
| `returning *` | 返回该语句影响到的全部列 | 与 `update ... returning *` 同构：展开为实体的全部列投影 |
| `returning p1..pk` | 返回指定投影列表 | 投影可以是列或表达式；表达式类型在编译期解析（复用既有 `ExprTypeResolver`） |
| 位置 | 跟在值清单 / `where` 之后，语句末尾 | 每个语句至多一个 `returning` |

**投影别名的抑制契约**：`returning` 投影**不得**被赋予生成别名（生成别名的形态是 `cN` 序号，见 `SeqAliasGenerator.genColumnAlias`）。⚠️ 判定**只能**按 `SqlUpdate` / `SqlInsert` / `SqlDelete` 三种节点逐个列举——**禁止**写成 `instanceof SqlDmlStatement`，因为 `SqlSelect` 同样继承 `SqlDmlStatement`，那样会静默打掉普通 SELECT 投影的全部别名。既有实现按"父节点是 UPDATE 语句"判定，扩展到 insert / delete 时必须把判定条件放宽到"父节点是任一写语句"（`SqlUpdate` / `SqlInsert` / `SqlDelete`），否则会产出 `returning name as col_1` 这类与 `update ... returning` 不一致的文本。该判定以"父节点种类"为准而非"是否在 returning 位置"，因为写语句的直接子节点里只有 `returning` 投影列表会容纳表达式投影。

**边界**：`insert ... select ... returning` 组合**不支持**（与 §3.1 同款理由：`select` 源下右值不可引用源列）。

### 3.3 AST 模型契约

EQL 的 AST 模型由 `nop-persistence/nop-orm-eql/model/ast/io/nop/orm/eql/ast/EqlAST.xjava` 单文件声明，codegen 由此生成 `ast/_gen/` 全量节点类与 `EqlASTKind` / `EqlASTVisitor` / `EqlASTProcessor` / `EqlASTOptimizer`。本设计的模型面要求：

| 节点 | 变更 | 说明 |
|---|---|---|
| `SqlUpsert` | **新增**，`extends SqlDmlStatement` | 承载目标表、列清单、值清单、冲突键列清单、更新赋值列表五个部分 |
| `SqlInsert` | 追加两个字段 | 与 `SqlUpdate` 已有的 returning 字段**同名同型**（`returnAll` 布尔 + `returnProjections` 列表），使三语句的 returning 语义在 AST 层面完全同构 |
| `SqlDelete` | 追加同样两个字段 | 同上 |
| `SqlAssignment` | **零变更** | `do update set` 的赋值项复用既有赋值节点（列名 + 表达式），不新增节点 |
| `SqlStatementKind` | 追加枚举项 | 该枚举是**手写**文件（非生成物），需与 `EqlASTKind` 的自动 ordinal 机制分别处理 |

**ordinal 纪律**：`EqlASTKind` 的序号取 `EqlAST.xjava` 中非抽象类的声明顺序，**中间插入会重排全部后续序号**。新节点一律**追加在文件末尾**。

### 3.4 方言能力位契约

新增 3 个布尔能力位，**全部缺省 false（保守缺省，未配置即不支持）**：

| 能力位 | 语义（真实边界，不是"该库有没有 RETURNING"） |
|---|---|
| `supportUpsert` | 该方言能承载 EQL `upsert` 语句的**完整语义**（含 §3.5 的冲突键校验与 §3.6 的更新列集约束） |
| `supportReturningForInsert` | 该方言支持 `INSERT` 上返回**投影列表形式**（无 `INTO` 宿主变量）的 `RETURNING` |
| `supportReturningForDelete` | 该方言支持 `DELETE` 上返回**投影列表形式**的 `RETURNING` |

**方言继承语义（事实，非假设）**：`x:extends` 下 `<features>` 元素按**属性逐个合并**，子方言只覆盖自己显式声明的属性，其余继承父方言。证据：`mysql5.7.dialect.xml` 只声明 3 个属性却继承了 `mysql.dialect.xml` 的 `supportFullJoin="false"`（`TestDialectFullJoinFlag` 断言"mysql5.7 inherits mysql's explicit false"）；`duckdb.dialect.xml` 只声明 3 个属性却继承了 `default.dialect.xml` 的 `supportFullJoin="true"`。因此"在某方言上写/不写某属性"必须连同继承链一起推演，不能只看单个 XML。

**取值与方言**（`[文档]` = 厂商文档；`[实测]` = 本仓真执行证据）：

| 方言 | `supportUpsert` | `supportReturningForInsert` | `supportReturningForDelete` | 证据 |
|---|---|---|---|---|
| postgresql | true | true | true | PG 15 文档 `[文档]` |
| postgis | true（显式覆盖声明） | 继承 true | 继承 true | PG 语法等同 postgresql |
| mysql / mysql5.7 | true | false | false | `ON DUPLICATE KEY UPDATE` 文档 `[文档]`；MySQL 全文法无 `RETURNING` |
| mariadb | true | false | false | MariaDB 10.2 承诺基线无 `RETURNING`（自 13.0 起，且仅单表） |
| h2（h2gis 继承） | true（受限，见 §3.6） | false | false | H2 文档无 `RETURNING` |
| duckdb | true（显式覆盖声明） | true（显式覆盖声明） | true（显式覆盖声明） | DuckDB 文档 `ON CONFLICT (conflict_target) DO UPDATE SET` 与 `INSERT/DELETE ... RETURNING` `[文档]` |
| mssql / oracle / db2 / dm / tdengine / es | false | false | false | 见 §3.10 证据纪律 |

**`supportUpsert` 与 upsert 形态解析的一致性硬约束**：能力位的取值**必须与 `nop-dao` 已有的方言形态解析器 `UpsertStatements.resolveUpsertStyle` 的结论一致**——凡 `resolveUpsertStyle` 返回 null 的方言，`supportUpsert` 必为 false；反之为 true。理由：EQL 的 SQL 生成按形态分支，若形态解析器不认识某方言而能力位却为 true，就会出现"能力位说支持、生成器无对应分支"的静默不一致（这是本设计要消灭的那一类错误，不能自己引入）。

**形态前缀表的覆盖范围（Phase 0 裁定）**：`resolveUpsertStyle` 按方言**名前缀**匹配，本设计把 `postgis` 与 `duckdb` **一并纳入** PostgreSQL 族：

- `postgis` 继承 `postgresql` 的全部方言特性，其 upsert 形态与 PG 同构；
- `duckdb` 文档明确支持 `ON CONFLICT (conflict_target) DO UPDATE SET`（与 PG 形态一致），只是其实现**不覆盖"新插入数据内部同键互撞"**——该限制对 EQL 单语句 upsert 不构成影响（EQL upsert v1 只有 `VALUES` 形态，一次一行）。

纳入是 `nop-dao` 静态面的**缺口修复**：两个方言原本落到"无原生支持"分支，既有消费方（checkpoint 存储、两阶段提交 sink）走的是行级两事务回落，纳入后走单语句原生路径（行为严格更优，且既有测试不覆盖这两个方言，无回归面）。

⚠️ 危险在于**继承会静默带下 `true`**：任一"父方言为 true 而本方言 `resolveUpsertStyle` 为 null"者，必须显式写 `false`，否则违反上一段的硬约束。**不允许**出现"能力位为 true 但静态面不认识"的组合。

**注册面纪律**：能力位不足（缺 `supportUpsert`）时**不注册**，由能力位决定编译期响亮失败——沿用既有 `rollup` / `cube` / `valuesTableSource` / `supportLateral` 的同款形态（`EqlTransformVisitor.visitSqlGroupingOp`）。

**生成管线**：`nop-dao` 的 precompile codegen 管线**未接线**（`nop-persistence/nop-dao/pom.xml` 无 exec 插件），能力位走 `dialect.xdef` 属性声明 + 手写 retention 类（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/DialectFeatures.java`）+ `IDialect` accessor + `DialectImpl` 实现（统一 `Boolean.TRUE.equals(...)` 保守缺省），**本次零生成**。

⚠️ **已登记的技术债**：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectFeatures.java` 是 `dialect.xdef` 早年的**入库生成物**，其字段集已落后于 xdef（不含 lateral / valuesTableSource / rollup / cube）。新增属性后它只会**更加陈旧**（不重生成、不报错）。风险是：未来一旦有人接线 precompile，`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectFeatures.java` 被重生成时会**丢掉** retention 类上的字段。处理约定：本次**不接线管线、不手改 `_gen` 文件**；若将来接线，必须先完成"把 retention 类字段迁回 `_DialectFeatures` 并删除 retention 类"的迁移。

### 3.5 冲突键的编译期校验

**EQL 编译期强制：冲突键列集必须等于目标实体声明的某个唯一键列集，或主键列集**，否则编译期报 `nop.err.eql.unknown-entity-name` 之外的**专用错误码**（见 §3.7）。

理由（这是本设计中最重要的一条安全约束）：

- PostgreSQL 族对不存在的冲突约束会在**运行期**报错；
- MySQL 族的 `ON DUPLICATE KEY UPDATE` **完全忽略**冲突键列表，改为查表上任意唯一键。若声明的冲突键在表上并非唯一键，MySQL 会静默退化为**普通插入**（不报错、语义与用户意图不符）；
- H2 族的 `MERGE ... KEY` 同样按表上实际约束判定。

⇒ 在编译期用 ORM 模型已声明的唯一键做一致性校验，是把一个**跨库不一致的静默错误**提前成响亮失败。

**唯一键的读取路径（受保护接口变更，需 plan-first 登记）**：`ISqlTableMeta.getEntityModel()` 的静态返回类型是 `IEntityModel`（`nop-persistence/nop-orm-model`），而**唯一键只存在于具体类**——`getUniqueKeys()` 只定义在 `_OrmEntityModel`（`_gen/` 生成物，不可手改）上，`IEntityModel` 上**没有**该方法。因此需要一个读取契约：

- 在 `IEntityModel` 上增加 **`default List<List<String>> getUniqueKeyPropNames()`**，返回"已声明唯一键的 prop 名列表"（每个唯一键一组），默认实现返回空列表。**签名在此钉死**，Phase 1 只做确认不做设计。契约：①**只含唯一键，不含主键**（主键走接口上既有的 `getPkColumnNames()`）；②不含隐式追加的租户列；③内层列表不可变；④`orm.xml` 声明了 `<unique-key>` 却未给 `<columns>`（`getColumns()` 返回 null）时**跳过该键**，不得 NPE；
- 覆盖点落在手写类 `OrmEntityModel`（`extends _OrmEntityModel implements IEntityModel`），从继承来的 `getUniqueKeys()` 读取；
- 取列名**必须**用唯一键模型的 `getColumns()`（prop 名，由 `OrmEntityModelInitializer` 归一），**不得**用 `getColumnModels()`（瞬态字段，**不在 `_OrmUniqueKeyModel.copyTo()` 的复制集内**，因此 clone / JSON 反序列化 / 缓存装载路径恒为 null——用它会让这些路径上的校验静默失效）；
- 唯一键列集**不含**隐式追加的租户列（那是 `getColumnModelsWithTenant` 的语义，属于查询期而非 DDL 声明）；
- 选 `default` 方法而非抽象方法，是为了不给其他实现方（视图模型、动态模型）带来破坏性变更；实现方不声明唯一键时返回空列表 ⇒ 该实体**只允许主键作冲突键**，这是响亮拒绝而非静默放行。

**Protected Area 登记**：改动 `IEntityModel`（`nop-persistence/nop-orm-model` 的公共模型接口）属 AGENTS.md「跨模块公共 API」范畴，需在 plan 中以 `plan-first` 登记（owner doc + 兼容性说明 + 回归测试）。**被拒备选**：在 EQL 层 `instanceof` 强转具体类——视图模型/动态模型不是该类子类，行为无法定义，且测试模型会恒 null 导致这条安全约束形同虚设。**次选备选**：v1 只支持主键作冲突键（零接口变更），代价是自然键 upsert 不可用。

**校验不做的事**：不校验 DDL 与 ORM 声明是否一致（DDL 不在 EQL 编译期可见性内）；这与 `nop-dao` 原语的同款约定一致，建表一致性归 DDL 工具链。

**无主键实体**：实体声明 `isNoPrimaryKey()` 时只接受声明的唯一键作为冲突键；既无主键又无唯一键的实体上使用 upsert 属建模错误，编译期拒绝。

**比较域的一致性**：冲突键校验的全部比较元素一律是 **prop 名**（`IColumnModel.getName()`），与唯一键的 `getColumns()`（初始化器已归一为 prop 名）、主键的 `getPkColumnNames()` 同域。列 code（物理列名）不参与比较——同一 prop 在不同方言下 code 可以不同。

### 3.6 方言 lowering 矩阵与 H2 形态约束

| 目标方言 | 输出形态 | 附加约束 |
|---|---|---|
| PostgreSQL 族 | `insert into T (cols) values (...) on conflict (kcols) do update set c = expr` | 无 |
| MySQL / MariaDB 族 | `insert into T (cols) values (...) on duplicate key update c = expr` | 冲突键列表被丢弃（§3.5 的编译期校验正是为了保证丢弃是安全的） |
| H2 族 | `merge into T (cols) key (kcols) values (...)` | **更新集的列名集合必须等于列清单的列名集合**（§下） |
| 其余全部（含 `postgis` / `duckdb`，除非 §3.4 的裁定纳入形态前缀表） | — | 编译期 `nop.err.eql.dialect-not-support-feature`，`feature=upsert` |

形态分支**以 `UpsertStatements.resolveUpsertStyle` 的结论为准**（§3.4 一致性硬约束），不在生成器内另建第二张按方言名前缀匹配的表。

**H2 约束的由来**：H2 的 `MERGE INTO ... KEY (...) VALUES (...)` 文法**没有 SET 子句**——命中 `KEY` 时按 `VALUES` 的列清单**整列更新**。本仓既有的 `nop-dao` 原语也是这样生成的：H2 分支直接忽略 `updateColumns`，只用 `columns`（见 `UpsertStatements.buildNativeUpsertSqlText` 的 H2 分支）。

⇒ H2 实际更新的列集 = **列清单**；而 PG / MySQL 实际更新的列集 = **更新集**。因此 H2 分支要求两个**必要条件**各自独立成立：

> **条件 1（列集相等）**：更新集的列名集合 == 列清单的列名集合
> **条件 2（右值无列引用）**：`do update set` 的任一右值**不得包含列引用**（不得引用目标表的旧值）

把冲突键列一并写进更新集是**允许的**（写入的值与原值相同，是 no-op）。以下情形在 H2 上都会产出与 PG / MySQL **语义不同**的 SQL，必须在编译期响亮失败（`feature=upsertPartialUpdate`）：

- 更新集 ⊊ 列清单（少写了一列）⇒ H2 会把未列出的列一并覆盖为新值 —— **静默语义分歧**，禁；
- 更新集 ⊋ 列清单（引用了未被插入的列）⇒ H2 无法表达该赋值（PG / MySQL 可以）；
- 右值引用了列（如 `do update set b = b + 1`）⇒ H2 的 `MERGE ... KEY ... VALUES` 没有更新子句，`VALUES` 行内无法引用被更新行的旧值（PG / MySQL 都可以，MySQL 的裸列名指旧行值）。**这一条单靠条件 1 拦不住**——更新集与列清单完全相同时仍会通过条件 1，因此必须作为独立的编译期条件存在。

条件 2 的判据：右值表达式子树中不含列引用；参数与字面量函数调用不受限。

这与 roadmap 排序原则第 2 条"只打印不设门 = 净回归"一致。

### 3.7 错误面契约

**错误码清单**（方言能力类一律复用既有码 + `feature` 参数；结构错误用本设计新增的 1 个专用码）：

| 场景 | 错误码 | `feature` 参数 |
|---|---|---|
| 方言不支持 upsert | `nop.err.eql.dialect-not-support-feature`（既有码） | `upsert` |
| 方言不支持该 upsert 更新列集形态（H2） | 同上（既有码） | `upsertPartialUpdate` |
| 方言不支持 `insert ... returning` | 同上（既有码） | `supportReturningForInsert` |
| 方言不支持 `delete ... returning` | 同上（既有码） | `supportReturningForDelete` |
| 冲突键未命中任何声明的唯一键 / 主键 | `nop.err.eql.invalid-conflict-columns`（**本设计唯一新增码**，参数 `conflictColumns` / `entityName`） | — |
| 冲突键未落在列清单内 | 同上（唯一新增码，参数同） | — |
| 语法错误（列清单为空、更新集为空等） | 走 ANTLR 解析错误既有链路 | — |
| `upsert` / `conflict` 被用作列名、表名或别名 | **不支持报错**——二者已登记为 unreserved 关键字（见 §3.1 关键字契约），该形态可正常解析 | — |

新增码的理由：冲突键校验失败是**用户输入的结构性错误**（缺一个唯一键声明，或冲突键不在写入列清单内），与"方言能力不足"是完全不同的诊断类别；复用 `dialect-not-support-feature` 会让用户朝错误方向排查。

**新增码的落地要求**：新增码定义在 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/OrmEqlErrors.java`，**消息语言按 AGENTS.md 的强制约定用英文**（该文件现存历史码串为中文，属既有遗留，新码不继承）。定位参数固定两个：冲突键列清单（逗号拼接的 prop 名）与目标实体名。

### 3.8 执行侧契约与模块边界

**编译产物接线**：

| 位置 | 动作 | 不做的后果 |
|---|---|---|
| `SqlStatementKind` | 追加 upsert 枚举项 | 语句种类识别失败 |
| DAO 查询执行器的语句种类分派 | 追加 upsert 分支 → 上报**保存**语义监听事件 | 监听器**静默不上报**（最小规则 #24 命中） |
| 分片标记注入 | **不加分支**（upsert 无 `where`，与 `insert` 同款，不从条件推导分片值） | — |
| 加密检索改写 | **不加分支**（写路径，零影响，与 `insert` 同款） | — |

**返回行的消费**：sql-lib 的执行方式探测按 SQL 文本是否以 `select` 开头分流（`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlItemModel.java:108-119`）。`upsert` 与带 `returning` 的 `insert` / `delete` 都不以 `select` 开头，会落到"执行"分支并丢弃返回行。

⇒ **契约**：要取回 `returning` 的行，必须在 sql-lib 上**显式声明** `sqlMethod` 为取值型；这是既有行为，本设计**不改探测逻辑**（改探测等于改变所有既有非 select 语句的执行方式，属独立决策）。

**模块依赖方向**：改动全部落在 `nop-orm-eql`（消费 `nop-dao`）、`nop-dao`、`nop-orm`（消费 `nop-orm-eql`）。**不新增任何模块间依赖边**。

### 3.9 与 DAO 批量 upsert 原语的关系

| 维度 | EQL `upsert` 语法层 | `nop-dao` 批量 upsert 原语 |
|---|---|---|
| 面向 | sql-lib / BizModel 声明式写路径，单语句 | 绕过 ORM 的批量 JDBC 直写，逐行批量 |
| 事务 | 沿用 ORM / sql-lib 既有事务模型 | 自行开单事务（native）或两事务（回落） |
| 跨分片 / 实体监听 / 查询缓存 | 进入既有链路 | 不进入 |
| 批量语义 | 无（单语句） | 有（native 批级原子 / 回落仅行级） |
| 方言范围 | PG / MySQL·MariaDB / H2 三族 | 同三族 + 无原生方言时的策略分流 |

**决策**：不合并、不互相委托。两者服务不同执行形态，合并会让语法层背上批量策略枚举，或让 DAO 层背上 AST 依赖。

### 3.10 证据等级纪律

| 断言 | 可得的最高证据 | 说明 |
|---|---|---|
| upsert 三族方言语法形态 | 单测断言 SQL 文本 + H2 真库真执行 | CI 载体为 H2（`TestUpsertPrimitiveH2` 已是既有范式） |
| `insert/delete ... returning` 方言语法形态 | 单测断言 SQL 文本 + 编译期门控负向用例 | **真库执行证据不可得**：CI 无 PostgreSQL/Testcontainers 默认开启 |
| `insert/delete ... returning` 真执行（JDBC 取回行） | Testcontainers 载体，**opt-in 默认跳过** | 与既有 6 条 docker opt-in skip 同款口径，须在文档中标注为"默认 CI 不覆盖" |
| 厂商语法断言（PG 15 / MySQL 8.0 / MariaDB 10.2 / H2 2.4.240） | `[文档]` | 一手文档引用，随方言 XML 注释落档 |

**禁止**：不得用"方言 XML 配了 true"当作"该库真跑通过"的证据。

---

## 四、拒绝了什么

### 4.1 为 `do update set` 引入 `excluded` 伪表

**这是 v1 最实质的取舍**。PG 上 `ON CONFLICT ... DO UPDATE SET c = EXCLUDED.c` 是事实标准写法，不提供它意味着 EQL 用户要写 `set c = ?`——同一个参数在 `values` 与 `set` 中各出现一次。

**仍然拒绝的理由**：`excluded` 需要注册一个合成表源进列解析作用域、每个列引用要携带"指向插入值表达式"的额外信息、且 **MySQL 族必须 lower 成 `VALUES(c)`、H2 族根本无法表达**（H2 的 `MERGE ... VALUES` 不能在更新子句里引用被插入的值）⇒ 引入它会把 H2 分支从"受限但可用"降级为"一律不可用"，代价大于收益。

**Successor 条件**：出现"插入值与更新值不同源"或"必须引用被插入值"的真实需求时立项；届时 MySQL 族可支持、H2 族维持编译期报错。

### 4.2 给现有 `insert` 加可选 `on conflict` 子句

拒绝。理由：① `insert` 的两条产出方言形态完全一致，加子句后同一语句在不同方言下语义分叉（MySQL 需丢弃冲突键列表）；② 撞键是**显式的错误恢复动作**，用户写下 `insert` 时的默认预期就是"撞了会失败"，把失败语义做成可选后缀会让默认行为与显式行为难以区分；③ 独立语句让 `upsert` 在语法层面即可被工具静态识别（IDE 高亮 / 静态检查 / 代码审计）。

### 4.3 完整 SQL 标准 `MERGE` 语义

拒绝（`WHEN MATCHED` / `WHEN NOT MATCHED` / `BY SOURCE` / `WHEN MATCHED THEN DELETE`）。跨库分叉最大：PG 15 的 `MERGE` 无 `BY SOURCE`（需 17）、MSSQL 强制终止 `;`（error 10713）、Oracle 独有 `WHEN MATCHED THEN DELETE`。这正是 roadmap WI11 终裁把 `MERGE` 留在 ledger 的原因，本设计不重开该判断。

### 4.4 把 `nop-dao` 批量 upsert 原语提升为 EQL 语句

拒绝。批量策略枚举（原生/回落）、行粒度两事务语义、JDBC batch 分段都是**执行期**概念，映射进声明式语句会让语法层承担它无法表达的保证（§3.9）。

### 4.5 复活 `supportOutputForUpdate` 死标志以覆盖 MSSQL

拒绝。本次 returning 范围限定为 `insert` / `delete` 两个子句位；MSSQL 的 `OUTPUT` 是独立语法族（`OUTPUT inserted./deleted.`），与投影列表形式 `RETURNING` 不是同一能力，且需额外取证。留在既有死标志台账中作为独立 successor。

### 4.6 改 sql-lib 的执行方式探测（按语句种类而非文本前缀分流）

拒绝。改动会波及所有既有非 select 语句的执行路径，属独立决策；本次以文档 + 显式 `sqlMethod` 声明的最小方式闭合。

---

## 五、与已有设计的关系

| 文档 | 关系 |
|---|---|
| `2026-10-05-wi11-upsert-design.md` | 并存不互斥（§3.9）。本文档是 roadmap ledger `MERGE` 行的 successor 第一环，不改变 WI11 的"显式移出"终裁 |
| `2026-10-05-wi11-phase0-adjudications.md` §8 | 触发条件"出现 EQL 层条件 upsert 语义的真实产品需求"**未满足**（本文只做无条件 upsert），故终裁继续有效；successor WI 由本设计承接 |
| `ai-dev/backlog/eql-cross-db-compat-roadmap.md:51` | 「明确不做的事」中"不把 UPSERT 做进语法层"被本设计**按 successor 路径**修订；修订记录写回 roadmap ledger 行，不删原文 |
| `ai-dev/design/eql-cross-db-compat/README.md` | 本文档登记进目录清单 |
| `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` | 实施落地后同步 upsert 语法、returning 能力矩阵、死标志台账三处 |
| `ai-dev/plans/29-eql-wi11-dao-upsert-primitive.md` | 既有 DAO 原语执行计划，本设计的 Plan 2380 不重复其范围 |

### 落地时的 roadmap 回写义务

`eql-cross-db-compat-roadmap.md:51`（明确不做的事）与 `:146-147`（ledger `MERGE` 行 + WI11 收口注记）**必须追加原地注记**说明本 successor 已开工，否则 roadmap 与 owner doc 会与代码冲突。注记须写明：本 successor 只覆盖无条件 upsert + insert/delete returning，条件 upsert / `WHEN MATCHED THEN DELETE` / `BY SOURCE` 仍未实现。