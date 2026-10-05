# EQL WI8 Phase 0 裁定

**日期**：2026-10-04
**范围**：纯裁定，不改产品代码（产品代码属 plan 27 Phase 1-3）
**状态**：active
**来源**：`ai-dev/plans/27-eql-wi8-null-safe-predicates.md` Phase 0；`ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI8 节 + 共同约束 1-4 + 门控矩阵 WI8 行 + Cross-Cutting + 关键约束与红线（v7.2）；plan 27 独立审查（3 Major + 5 Minor）修订项全部吸收
**消费**：`ai-dev/design/eql-cross-db-compat/2026-10-04-wi4-phase0-adjudications.md` §二 2c（槽位与方言来源契约，本文 §四直接消费并按 plan 27 审查 Major-1 更正其 provisional 展开式）

---

## 〇、开工前置复核记录（plan 27 硬前置声明要求）

| 前置项 | plan 27 起草时（2026-10-04 上午）状态 | 本次执行时点实测（2026-10-04） | 结论 |
|---|---|---|---|
| plan 20（WI3） | `completed` | `Plan Status: completed`（head 确认） | 满足 |
| plan 21（WI4） | `active`（产品面已落地、audit 待执行） | **`Plan Status: completed`**；roadmap Work Item Status WI4 = `done` | **满足——代码 Phase 开工门已开**（plan 27 硬前置声明的唯一阻塞已解除） |
| roadmap WI8 行 | `planned` | `planned`（门控矩阵前置 WI3/WI4/G-1 均 done/已裁决） | 按状态流转规则保持，closure audit 后才翻 done |
| G-1 | 已裁决 | `2026-10-03-wi0-gate-adjudications.md`：承诺基线 = Oracle 23ai / MySQL 8.0.31 / MariaDB 10.2 / PG 15 / MSSQL 2022 / SQLite 3.39；unverified 边界（含 Oracle `IS TRUE/FALSE`、DB2 `DISTINCT` 谓词）维持 unverified 纪律 | 全程遵守（§三/§五） |

漂移锚点复核（plan 27 Current Baseline 写于 plan 21 收口前，行数已被 WI7/WI7b 推进——非实质偏离，符号锚点全部命中）：

| 锚点 | plan 27 记载 | 实测（2026-10-04） |
|---|---|---|
| `EqlParser.java` 行数 | 10927 | **11558**（路径 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`；WI7b SqlGroupingOp 备选推进） |
| `_EqlASTBuildVisitor.java` 行数 | 1819 | **1936** |
| `ast/_gen/` 文件数 | 77 | **78**（`_SqlGroupingOp.java` 由 WI7b 新增；plan 27 的"零 diff"红线改为"**不新增不删除文件、既有 78 文件零 diff**"） |
| `Symbol.g4:38` `SAFE_EQ_` | 保留红线 | 在位，`SAFE_EQ_: '<=>'`；BaseRule.g4（`nop-persistence/nop-orm-eql/model/antlr/` 下）唯一引用仍是 `:159` 注释态 |
| 关键字 token | TRUE `Keyword.g4:269` / FALSE `:273` / DISTINCT `:150` / UNKNOWN `SQL92Keyword.g4:684` | 全部在位；均**不在** `unreservedWord_`（`BaseRule.g4:72-87`）= 保留 token，不与 `IDENTIFIER_` 竞争 |
| `AstToSqlGenerator` dialect 字段 | `:77` | `:76`（1 行漂移） |
| owner doc「AST 改写管线」小节 | `:326` 起 / WI8 消费指引 `:345` | **`:433` 起 / `:452`**（WI5-WI7b 小节推进；符号定位命中） |
| `TestEqlLateralSubqueryTableSource.buildDialect` | `:78` | `:77`（1 行漂移） |
| WI0 基线 | 存在可跑 | `eql-corpus-baseline.md` 棘轮区间完好；`dialect-capability-matrix.json` 18 方言注册；`check-eql-dialect-matrix-snapshot.mjs` 实跑 exit 0 |

---

## 一、裁定 1：每谓词路径定价（约束 1 四路机制表逐行落结论）

### 1.1 `IS [NOT] UNKNOWN` —— 路径 ⓐ（解析期直通归一化既有节点），成本最低

- **规范 AST 形态**：归一化进既有 `SqlIsNullExpr`（直用其自带 `not` prop——`UNKNOWN` ≡ `NULL` 三值精确等价）。**零手写构建代码**：备选 `expr=sqlExpr_primary IS not=NOT? UNKNOWN # SqlIsNullExpr_isUnknown` 的生成方法体（建节点 + `setExpr` + `setNot` + normalize/validate）与既有 `visitSqlIsNullExpr` 逐字同构，生成即正确。
- **lowering**：零新渲染路径——全方言复用既有 `IS [NOT] NULL` 渲染（`AstToEqlGenerator.visitSqlIsNullExpr:809` 回显 + SQL 侧同款）。全方言安全（`IS NULL` 无方言分叉）。
- **被拒备选**：新 AST 节点 `SqlIsUnknownExpr`（ⓒ）——`EqlASTKind` ordinal 追加 + `_gen` 重生成，换来的只是回显文本 `is unknown` vs `is null` 的形态偏好；三值等价使正确性零增益。拒绝。

### 1.2 `IS [NOT] DISTINCT FROM` —— 路径 ⓐ（构建期归一化 `SqlBinaryExpr`+`SqlNotExpr`）+ 方言渲染三分支（必选件）+ Oracle 展开走 ⓓ（WI4 管线），成本中

- **规范 AST 形态（钉死）**：
  - `a IS NOT DISTINCT FROM b` ⇒ `SqlBinaryExpr(left=a, operator=SAFE_EQ, right=b)`（左右操作数保序）。
  - `a IS DISTINCT FROM b` ⇒ `SqlNotExpr(expr=SqlBinaryExpr(SAFE_EQ))`。
- **前置裁定：`SqlOperator` 手写枚举补 `SAFE_EQ("<=>", 110)` 成员**（路径 ⓐ 成立的前提），四处边界逐一落档见 §六。被拒备选：新谓词 AST 节点 `SqlDistinctFromExpr`（ⓒ）——ordinal 红线 + 78 `_gen` + parser/visitor 重生成全量定价，且 `SqlBinaryExpr` 归一化已无正确性损失；拒绝。
- **lowering 分工**：渲染三分支（§四.1）+ Oracle 三节点展开（§四.2，ⓓ 管线消费）。

### 1.3 `IS [NOT] TRUE` / `IS [NOT] FALSE` —— 路径 ⓐ（构建期真值表驱动通用展开进既有节点），成本中

- **规范 AST 形态（钉死，展开式见 §二）**：
  - `a IS TRUE` ⇒ `SqlAndExpr(SqlIsNullExpr(a, not=true), SqlBinaryExpr(a, EQ, SqlBooleanLiteral(true)))`
  - `a IS NOT TRUE` ⇒ `SqlNotExpr(SqlAndExpr(SqlIsNullExpr(a, not=true), SqlBinaryExpr(a, EQ, true)))`
  - `a IS FALSE` / `a IS NOT FALSE` 同构（`SqlBooleanLiteral(false)`）。
- 全部只用既有节点（`SqlAndExpr`/`SqlNotExpr`/`SqlIsNullExpr`/`SqlBinaryExpr(EQ)`/`SqlBooleanLiteral`），`ast/_gen/` 零 diff。
- **被拒备选 ①（ⓒ 新谓词节点）**：保身份（原生方言直出 `IS TRUE` 文本）的唯一路径；`EqlASTKind` ordinal 追加 + 全量重生成定价，收益（形态偏好）不成比例——登记 plan 27 `Deferred But Adjudicated`（optimization candidate，触发条件 = 出现"必须输出原生 `IS TRUE` 文本"的真实消费场景）。
- **被拒备选 ②（生成器侧展开）**：把展开逻辑放 `AstToSqlGenerator.visitSqlXxx`——被拒理由：回显路径（`AstToEqlGenerator`）无方言概念但也必须能无损回显规范形态，构建期归一化使回显天然成立；且生成器展开无法被 WI4 缓存契约感知（transformer 管线先于生成器）。构建期归一化是唯一同时满足"回显确定性 + 全方言安全 + 零生成"的落点。
- **`IS [NOT] TRUE/FALSE` 无方言渲染门**：展开式只含安全词表构造（§二.2），全方言可执行——Oracle/DB2/dm/tdengine 上同样输出展开文本（`1`/`0` 布尔字面量经 `dialect.getBooleanValueLiteral`），不设编译期报错分支。unverified 纪律的落法 = owner doc 矩阵标 `[未取证]`（这些方言上"展开式可执行"是**EQL 侧构造事实**，不主张其原生 `IS TRUE` 行为等价），而非功能拒绝。

---

## 二、裁定 2：展开公式钉死（3VL 逐行验算，唯一裁定基准）

### 2.1 `IS [NOT] DISTINCT FROM` 正反两方向（plan 27 审查 Major-1）

**正向** `a IS DISTINCT FROM b` ≡ **主钉死公式（全二值 guard 三支式）**：

```sql
(a IS NOT NULL AND b IS NOT NULL AND a<>b)
OR (a IS NULL AND b IS NOT NULL)
OR (a IS NOT NULL AND b IS NULL)
```

| a\b | T | F | NULL |
|---|---|---|---|
| T | F | T | **T（二值）** |
| F | T | F | **T（二值）** |
| NULL | **T（二值）** | **T（二值）** | **F（二值）** |

**否定方向** `a IS NOT DISTINCT FROM b` ≡ **主钉死公式（全二值 guard 两支式）**：

```sql
(a IS NULL AND b IS NULL)
OR (a IS NOT NULL AND b IS NOT NULL AND a=b)
```

| a\b | T | F | NULL |
|---|---|---|---|
| T | T | F | **F（二值）** |
| F | F | T | **F（二值）** |
| NULL | **F（二值）** | **F（二值）** | **T（二值）** |

**验收不变式**：任一方向的展开式在单 NULL 与双 NULL 两行都不得产生 UNKNOWN（WHERE 语义与原生逐行一致）。

**被拒候选（裸式，作差异示例保留）**：正向裸三支式 `(a<>b) OR (a IS NULL AND b IS NOT NULL) OR (a IS NOT NULL AND b IS NULL)` 在双 NULL 行求值为 `NULL OR F OR F = NULL`（原生 FALSE）；否定方向裸两支式 `(a=b) OR (a IS NULL AND b IS NULL)` 在单操作数 NULL 行求值为 NULL（原生 FALSE）——WHERE 场合恰逢测试否弃（NULL 行不入选，与原生一致），但**投影场合值不同**（NULL vs FALSE），违反验收不变式。不得作为实现形态。

**禁止形态（P1 静默错误：展开式产生 UNKNOWN → WHERE 丢行）**：

1. 对 `IS DISTINCT FROM` 用 `NOT((a=b) OR (a IS NULL AND b IS NULL))`——单操作数 NULL 行 `NOT(NULL)`=NULL，原生 TRUE 的行被丢。
2. 对 `IS NOT DISTINCT FROM` 用三支式的 `NOT(...)` 包装——双 NULL 行 `NOT(NULL)`=NULL，原生 TRUE 的行被丢。

**方向互换更正（审查 Major-1，取代 WI4 裁定文档 §二 2c 的 provisional 式）**：WI4 文档 provisional 展开式 `NOT ((a = b) OR (a IS NULL AND b IS NULL))` 经 3VL 验算属**禁止形态 ①**（它实为 `IS DISTINCT FROM` 的 naive 包装）。该文档已留白"方言展开形态按 WI8 裁定"，本文钉死公式**取代**之；`NOT(two-way)` 恰是 `IS DISTINCT FROM` 的错误形态，正确正向 = 三支式、正确否定 = 两支式。plan 27 Phase 3 的 owner doc 同步此更正并回链 WI4 文档留白处。

### 2.2 `IS [NOT] TRUE/FALSE/UNKNOWN` 六式展开（真值表驱动）

标准 SQL 严格三值语义（EQL 语义钉死，见 §五），安全词表展开式：

| 谓词 | 钉死展开式 |
|---|---|
| `a IS TRUE` | `a IS NOT NULL AND a = TRUE` |
| `a IS NOT TRUE` | `NOT (a IS NOT NULL AND a = TRUE)` |
| `a IS FALSE` | `a IS NOT NULL AND a = FALSE` |
| `a IS NOT FALSE` | `NOT (a IS NOT NULL AND a = FALSE)` |
| `a IS UNKNOWN` | `a IS NULL`（归一化 `SqlIsNullExpr`，零展开） |
| `a IS NOT UNKNOWN` | `a IS NOT NULL`（同上，`not` prop） |

逐行验算（TRUE/FALSE/NULL 行 + 非布尔操作数行 `a=2`；布尔字面量按方言渲染 `1`/`0`（default 系）或 `true`/`false`（h2）——**渲染形态不影响行值**）：

| 操作数 a | `IS TRUE` 展开 | `IS NOT TRUE` 展开 | `IS FALSE` 展开 | `IS NOT FALSE` 展开 | `IS UNKNOWN` | `IS NOT UNKNOWN` | `a<>TRUE`（禁止的错误降级对照） |
|---|---|---|---|---|---|---|---|
| TRUE | T AND (T=T)=**T** ✓ | NOT(T)=**F** ✓ | T AND F=**F** ✓ | NOT(F)=**T** ✓ | **F** ✓ | **T** ✓ | F |
| FALSE | T AND F=**F** ✓ | NOT(F)=**T** ✓ | T AND T=**T** ✓ | NOT(T)=**F** ✓ | **F** ✓ | **T** ✓ | T |
| NULL | F AND NULL=**F** ✓ | NOT(F AND NULL)=NOT(F)=**T** ✓ | F AND NULL=**F** ✓ | NOT(F)=**T** ✓ | **T** ✓ | **F** ✓ | **NULL**（失配行=roadmap 警告） |
| 2（非布尔） | T AND (2=1)=**F** ✓钉死 | NOT(F)=**T** ✓钉死 | T AND (2=0)=**F** ✓钉死 | NOT(F)=**T** ✓钉死 | **F** ✓ | **T** ✓ | T |

非布尔行的关键机制：布尔字面量按方言渲染为 `1`/`0`（default 系含 mysql/oracle/mssql/db2/dm）后 `2=1` 在**任何**方言上都是 FALSE——展开式天然规避 MySQL 原生 `IS TRUE` 的数值真值语义（`2 IS TRUE`=TRUE）；`2 = 1` ≠ MySQL `2 = TRUE`（后者 MySQL 按 `2=1` 求值同为 FALSE，但原生 `IS TRUE` 走 truthy 通路得 TRUE——分叉登记见 §五）。h2 上 `2 = true` 为类型错误（响亮），与 H2 原生 `2 IS TRUE` 的行为一致方向（非布尔操作数不被静默接受）。

**禁止形态（truthy 裸条件，审查 Major-3）**：`a IS NOT TRUE` **不得**降级为 `a<>TRUE`/`a<>0`/`NOT a`/裸 `a`——NULL 行 `NOT a`=NULL（应为 TRUE）、`a<>0` 在 `a IS NULL` 时为 NULL（应为 TRUE）。`IS TRUE` 不得降级为裸 truthy 条件（MySQL 上 `2 IS TRUE` 静默得 TRUE，与钉死语义 FALSE 不一致）。

---

## 三、裁定 3：`IS [NOT] TRUE/FALSE/UNKNOWN` 逐方言矩阵（证据等级栏，约束 4）

证据等级：`[实测]` = 本仓库测试/探针执行过；`[文档]` = 一手文档引证（roadmap/裁定文档已登记）；`[未取证]` = 无一手引证，按红线不计入覆盖率。

**EQL 语义钉死 = 标准 SQL 严格三值**（§二真值表）。各格含义 = "该方言上 EQL 输出形态的行为主张"：

| 方言 | `IS [NOT] DISTINCT FROM` | `IS [NOT] TRUE/FALSE` | `IS [NOT] UNKNOWN` | 依据 |
|---|---|---|---|---|
| mysql 8.0.31（承诺基线） | `<=>`（**非标准语义超集**：`<=>` 恒二值，正向 `IS DISTINCT FROM` 以 `not(a<=>b)` 承载，§七差异 ①） | 输出严格语义展开式（`a=1` 形态）；**原生 `IS TRUE` 为数值真值语义（truthy），与 EQL 钉死语义不等价**——差异登记 §七 ② | `IS [NOT] NULL` | G-1 承诺基线 `[文档]`（MySQL 手册 `<=>`/`IS TRUE`）；展开式行为 `[实测]`（Phase 2 用例） |
| mariadb 10.2（继承 mysql） | 同 mysql | 同 mysql | 同 mysql | 继承别名随父 `[文档]` |
| mysql5.7（继承 mysql） | 同 mysql | 同 mysql | 同 mysql | 继承别名随父 `[文档]` |
| postgresql 15 / postgis（继承） | 原生 `IS [NOT] DISTINCT FROM` 直出 | 输出严格语义展开式（`a = true` 形态） | `IS [NOT] NULL` | PG 文档 `[文档]`（数据类型页 boolean 段 `IS TRUE`/谓词 `IS DISTINCT FROM`） |
| h2（承诺矩阵内）/ h2gis（继承） | 原生直出（H2 文法 Distinct Predicate："null-safe … never evaluates to UNKNOWN"） | 输出严格语义展开式（`a = true` 形态）；⚠️ 执行期实测修正（H2 探针 `_tmp/wi8/h2probe.log`）：H2 原生 `2 IS TRUE` 返回 **TRUE**（H2 亦为数值真值语义）且 `2 = true` 是**类型错误**（90110，INTEGER vs BOOLEAN 不可比）⇒ EQL 展开式在 h2 上对非布尔操作数 = **响亮类型错误**（不是原生 truthy，也不是钉死 FALSE）——h2 上钉死语义不可达，登记约束 4「响亮分叉」 | `IS [NOT] NULL` | roadmap 已引 H2 文法 `[文档]` + Phase 1/2 H2 真执行 `[实测]` |
| duckdb | 原生直出 | 输出严格语义展开式 | `IS [NOT] NULL` | DuckDB 文档 `[文档]`（roadmap WI8 矩阵） |
| mssql 2022（承诺基线） | 原生直出（MSSQL `IS DISTINCT FROM` 2022 已 GA） | 输出严格语义展开式（`a = 1` 形态） | `IS [NOT] NULL` | G-1 承诺基线 `[文档]` |
| oracle 23ai（承诺基线） | **三节点展开**（transformer，§四.2）——Oracle 无 `IS DISTINCT FROM` 谓词 | 输出严格语义展开式；**Oracle 原生 `IS TRUE/FALSE` 简写 unverified（G-1：23ai SELECT 页零 `IS TRUE`）**——不主张原生等价，输出展开式不依赖该边界 | `IS [NOT] NULL` | G-1 unverified 纪律 `[未取证]`（原生简写格）+ 展开式 `[实测]`（Phase 3 用例） |
| oracle-reverse（继承 oracle） | 同 oracle | 同 oracle | 同 oracle | 继承别名随父 |
| db2 | **原生格 unverified（IBM 文档 403，仅检索片段）⇒ 不进 native 集合，编译期响亮报错**（plan 27 Deferred：一手取证 watch-only residual） | 展开式（EQL 构造事实，不主张原生等价）`[未取证]` | `IS [NOT] NULL` | unverified 纪律 |
| dm / tdengine | 无一手文档 ⇒ 不进任何集合，编译期响亮报错 | 展开式 `[未取证]` | `IS [NOT] NULL` | unverified 红线 |
| sqlite 3.39（承诺基线，仓库无方言文件） | EC ④ 负向载体 = 手构 `DialectModel`（§八），<3.39 编译期报错 | 展开式（手构载体同样承载） | `IS [NOT] NULL` | G-1 `[文档]`（SQLite 3.39 引入 `IS DISTINCT FROM`） |
| es（伪方言） | 不参与矩阵（roadmap 明文） | 不参与矩阵 | 不参与矩阵 | roadmap |
| default（无方言名） | 报错分支兜底 | 展开式 | `IS [NOT] NULL` | 手构载体默认行为 |

MySQL 数值真值分叉为约束 4「不等价」类登记（§七 ②）。**本矩阵不新增任何方言能力标志/快照增量**（§六裁定 2）——矩阵是文档事实 + 代码内名称集合，`dialect-capability-matrix.json` 零增量（已实测 exit 0）。

---

## 四、裁定 4：展开载体分工（渲染三分支必选件 + transformer 槽位/方言来源）

### 4.1 SAFE_EQ 渲染三分支 = 必选件，归属 Phase 1（`AstToSqlGenerator` 覆写）

实测依据（plan 27 Current Baseline，本次复核命中）：`AstToEqlGenerator.printOperator`（现位 `:202-206`）直出 `operator.getText()`，`visitSqlBinaryExpr`（`:804`）无任何方言开关，`AstToSqlGenerator` 未覆写两者 ⇒ 不覆写则 `SqlOperator.SAFE_EQ` 全方言输出 `<=>`（PG 等直接产出非法 SQL = 静默错误）。

**三分支钉死**（覆写 `visitSqlBinaryExpr` + `visitSqlNotExpr`，识别 `operator==SAFE_EQ` 及其 `SqlNotExpr` 直接包装）：

1. **mysql 族**（`dialect.getName()` ∈ `{mysql, mysql5.7, mariadb}`，名称集合先例 = WI7 `DOCUMENTED_DIRECTIONAL_NULL_ORDER`）：`SqlBinaryExpr(SAFE_EQ)` → `a <=> b`；`SqlNotExpr(SAFE_EQ)` → `not (a <=> b)`（MySQL 3VL 下 `<=>` 恒二值，`NOT` 良定义，§七差异 ①）。
2. **native 集合**（`{postgresql, postgis, h2, h2gis, duckdb, mssql}`）：`SqlBinaryExpr(SAFE_EQ)` → `a is not distinct from b`；`SqlNotExpr(SAFE_EQ)` → `a is distinct from b`（**方向文本直出、禁止 `not (a is not distinct from b)` 包装**——审查 Major-2）。
3. **其余全部方言**（oracle 无/有 transformer 到达生成器前已被展开故不会到达、db2、dm、tdengine、default、手构方言）：响亮抛 `nop.err.eql.dialect-not-support-feature`（`ARG_DIALECT`/`ARG_FEATURE="is[not] distinct from"`）——**全仓不存在向不支持方言静默输出 `<=>` 的路径**。

回显路径（`AstToEqlGenerator`）不受影响：规范回显 `a <=> b` / `not (a <=> b)`（三态断言「输入→规范回显→方言输出」登记见 plan 27 Phase 1）。

**"生成器分支作为 Oracle 展开载体"记录为被拒候选**：生成器无法访问语句级 querySpace 的已解析表源（其构造只持 `IDialect`——虽可判方言但展开需在**编译产物进缓存前**以 AST 形态发生，生成器是文本面）；且渲染三分支必选 ≠ 展开载体选生成器（两者职责不同：渲染 = 同一规范 AST 的方言文本映射；展开 = AST → 等价 AST 改写，必须走 WI4 管线以获得缓存正确性）。

### 4.2 Oracle 三节点展开 = 生产 `IEqlAstTransformer`，放 `transformAfterAnalyze` 槽位

- **载体**：nop-orm-eql 侧新增生产 transformer（实现 `IEqlAstTransformer`，普通手写类，不改接口——WI4 Non-Goal 延续）。
- **职责边界**：**仅** `IS [NOT] DISTINCT FROM` 在 oracle/oracle-reverse 上的三节点展开（§二.1 主钉死公式：`SqlNotExpr(SAFE_EQ)` → 三支式替换整节点；`SqlBinaryExpr(SAFE_EQ)` → 两支式替换整节点）。`IS TRUE/FALSE` 已被构建期通用展开全覆盖（§一.3），**transformer 不重复处理**（plan 27「若 Phase 2 通用展开已覆盖则本项按裁定收敛」→ 裁定：收敛，transformer 无 TRUE/FALSE 职责）。mysql 族/native 方言直通不改写（AST 原样返回，零改动）。
- **槽位**：`transformAfterAnalyze`（WI4 §二 2c 契约：需要方言判定的纯语法改写放 after）。参数保持性论证直接引用 WI4 §二 2c：展开式只重排/包装既有操作数节点 + 新增对**同一操作数**的 null 谓词——不新增 `SqlParameter`、不新增实体引用、相对参数顺序不变。
- **方言来源（钉死 = 与渲染侧同源的语句级 querySpace）**：已解析 AST → 收集 `SqlSingleTableSource.getResolvedTableMeta().getQuerySpace()` → `context.getDialectForQuerySpace(querySpace)` → `IDialect.getName()`。多 querySpace 场景：EQL 管线本身已强制单 querySpace（`ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE`，`EqlTransformVisitor.addResolvedEntity:859-864`）⇒ transformer 对收集到多个不同 querySpace 的病态输入响亮抛同款错误（防御性、不静默取主表方言）；无任何已解析表源的语句（无 FROM）不可能含 SAFE_EQ 实体列谓词，直通不改写。
- **展开公式**：§二.1 主钉死公式（**禁止**两个 naive NOT 包装形态；`SqlNotExpr` 包装的 SAFE_EQ 按三支式**替换**，不产生 `NOT(展开)`）。
- **错误码边界**：transformer 只做展开、不新增报错分支——不支持的方言由渲染三分支第 3 支响亮（无 transformer 时 oracle 亦落该分支）。

### 4.3 无 transformer 配置时的默认正确性（Minimum Rules #24 + 排序原则 P1）

裁定 = **"规范 AST 全方言安全 + 渲染侧响亮兜底"双保险**，两者同时成立：

1. `IS [NOT] TRUE/FALSE/UNKNOWN` 的规范形态（展开式/`SqlIsNullExpr`）只含安全词表构造，**任何方言可执行**——无 transformer 也正确。
2. `IS [NOT] DISTINCT FROM` 的规范形态含 `SAFE_EQ` 算子——渲染三分支对不识别它的方言响亮报错（第 3 支），**不静默输出裸 `<=>`/裸 `IS DISTINCT FROM` 文本**；oracle 由 Phase 3 的默认注入 transformer 展开（beans.xml 显式 `<bean>`，Nop IoC 规则），注入后 oracle 不再到达报错分支。

⇒ 不存在"无 transformer 时静默输出不支持方言裸语法"的路径；也不允许"无 transformer 时响亮"之外的第三态（不回退原文、不丢谓词）。

---

## 五、裁定 5：`IS TRUE/FALSE` 操作数类型域（审查 Major-3 ①）

**二选一裁定：任意表达式（域外不报错、按 §二钉死语义求值）。** 拒绝"仅布尔域 + 域外编译期报错"：

- 类型检查点不存在：构建期归一化发生在 parse 阶段（visitor），`resolvedExprMeta` 尚未赋值（analyze 才产生）；编译期报错只能在 transformer/生成器面做，而展开已在构建期完成，"展开式中的 `a = TRUE` 对非布尔 a"与"用户手写 `a = TRUE`"在既有类型通路上行为一致（h2/PG 等强类型方言上数据库响亮、弱类型方言上按钉死语义求值）——为谓词语法单独造一条跨阶段类型检查通路成本高且与既有 `a = TRUE` 直写行为不一致。
- 钉死语义由构造保证：展开式 `a IS NOT NULL AND a = TRUE` 对任意操作数不产生 truthy 误判（§二验算行）；方言侧类型拒绝是响亮失败，非静默错误。

**展开式词表钉死（审查 Major-3 ②）**：`=` / `<>` / `IS [NOT] NULL` / `AND` / `OR` / `NOT` + 布尔字面量。禁止 truthy 裸条件形态（§二禁止形态段）。每式展开文本已按真值表（含非布尔操作数行）逐行验算（§二.2 表）。

---

## 六、裁定 6：`SqlOperator.SAFE_EQ` 成员边界清单 + 能力标志形态

### 6.1 `SqlOperator` 补 `SAFE_EQ("<=>", 110)` 成员（手写枚举 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/enums/SqlOperator.java`，零生成）

| 边界点 | 裁定 | 理由 |
|---|---|---|
| text / 优先级 | `"<=>"` / `110`（与 EQ/NE 同级） | null-safe 相等是比较级算子；文本 = MySQL 词法形态（`Symbol.g4:38` `SAFE_EQ_` 同源），回显直出合法 |
| `isCompareOp()` | **纳入**（追加 `\|\| this == SAFE_EQ`） | 全仓唯一生产消费点 `SqlParamTypeResolver.java:101`（参数类型回退解析：`? <=> 列` 应与 `? = 列` 同路）；null-safe 相等语义上就是等值比较 |
| `getLeftSqlType`/`getRightSqlType`/`getResultSqlType` | **显式确认落 `StdSqlType.OTHER`**（与 EQ 一样走默认分支，**不改代码**，加注释钉死） | EQ 现值即 OTHER（`:89`/`:103`/`:116` 默认分支）；`<=>` 操作数类型域与 `=` 相同（任意可比较类型），OTHER = 不约束，参数 binder 回退 ANY——与 EQ 行为逐字一致 |
| `reverse()` | **显式返回自身**（`case SAFE_EQ: return SAFE_EQ`） | `a<=>b ⇔ b<=>a` 自反；不得静默落 `default → null`（未来消费点的静默错误陷阱；当前全仓无 `SqlOperator.reverse()` 生产调用点，实测唯一 `reverse()` 调用在 `OrmQueryHelper` 属 `OrderFieldBean`） |
| `EqlASTOptimizer` 遍历安全性 | **确认安全，不改代码**（实测 `optimizeSqlBinaryExpr`/`optimizeSqlNotExpr`/`optimizeSqlIsNullExpr` 均为纯子树递归、无 operator 特判） | 新枚举成员不新增 AST kind，不触生成链 |
| 序列化 | 担忧关闭（无 `valueOf` 消费、无 ordinal 持久化；查询计划缓存存 `CompiledSql` 非 AST——WI4 契约） | plan 27 Current Baseline 结论维持 |
| `EqlParseHelper:124` / `CollectionOperatorTransformer:698` | 不动（各自构造 `EQ` 的局部路径，与 SAFE_EQ 无交） | 实测确认无枚举穷举 switch 会因新成员编译失败（`SqlOperator` 无 switch 穷举消费者；`reverse()` 是唯一 switch，新增 case 显式处理） |

### 6.2 能力标志形态 = 不新增方言标志，按 G-1 固定矩阵 + 渲染侧名称集合（裁定）

**拒绝四件套方案**（`dialect.xdef` 新属性 + `DialectFeatures` retention + `IDialect` accessor + 18 个 XML 取值 + 快照棘轮同步）：证据面与维护成本对比——(a) 方言矩阵在 WI8 已由 roadmap + G-1 + 本文 §三钉死，运行时可配置性引入"XML 声称支持但语义未取证"的假阳性通路（WI10b 对 `supportILike` 的同类教训：标志与真实语义脱节引入假阴性/假阳性）；(b) 四件套 + 18 XML + 快照重生成的全部成本只为表达一张静态矩阵；(c) WI7 已有名称集合先例（`DOCUMENTED_DIRECTIONAL_NULL_ORDER`，含继承别名 mysql5.7/mariadb/postgis/h2gis 显式列举）。

⇒ **渲染三分支与 transformer 各持私有名称集合**（mysql 族 / native 集合 / oracle 集合），集合成员与 §三矩阵逐格对应。**`dialect-capability-matrix.json` 零增量**（快照门禁实测 exit 0 维持）；未来若需运行时可配（如 dm 取证后放行），按 roadmap 变更登记再立项，届时名称集合迁 `DialectFeatures` 是纯重构。

---

## 七、裁定 7：约束 4 用例清单（一致性 + 不等价）

「一致性」类（同一 EQL 在各支持方言语义一致）——各 Phase 正向用例即承载：六谓词在 h2/mysql/pg/oracle（transformer 后）等方言上的输出形态不同但**行值语义**逐行一致（§二验算表即规格）。

「不等价」类（至少四项，用例形态逐项标注）：

| # | 差异 | 用例形态 | 归属 |
|---|---|---|---|
| ① | MySQL 三值逻辑：三节点展开 vs `<=>`——**嵌入外层 NOT 时**单操作数 NULL 行分叉：`NOT(a IS NOT DISTINCT FROM b)` 以展开式承载（oracle 公式错用即 `NOT(三支式)`）在 `a=NULL,b=X` 行得 `NOT(T)`=F；而 mysql 直出 `not(a<=>b)` 同行得 `NOT(F)`=T。两方向在 EQL 中**各自钉死正确形态**（oracle 三支式替换、mysql not 包装 `<=>`），差异登记为「`<=>` 恒二值 vs 标准谓词嵌套语义」的固有分叉——用例断言 mysql 输出 `not (a <=> b)` 而 oracle 输出三支式，注释标注该行差异 | 文本断言（mysql vs oracle transformer 后双方言对照） | Phase 3（roadmap EC ①） |
| ② | MySQL 数值真值 vs 严格布尔：原生 `2 IS TRUE`=TRUE（truthy）vs EQL 展开式 `2 is not null and 2 = 1` 得 FALSE（钉死语义） | 文本断言 + owner doc 差异行（H2 真库若接受 `2=1` 可加执行断言） | Phase 2 |
| ③ | `IS NOT TRUE` NULL 行：展开式得 TRUE vs 二元比较降级 `a<>TRUE`/`NOT a` 得 NULL——roadmap 警告的失配行 | 负向对照断言：`IS NOT TRUE` 展开文本**不是** `a<>…`/`NOT a`/裸 truthy 形态 + 真值表 NULL 行断言（H2 可执行则执行断言） | Phase 2 |
| ④ | **禁止形态负向对照（非"方言差异"，静默错误守护）**：`IS [NOT] DISTINCT FROM` 两方向展开式在单 NULL/双 NULL 行均二值——断言 oracle transformer 产出文本**不是** `not ((a = b) or (a is null and b is null))`（禁止形态①）也**不含** `not (` 包裹三支式/两支式（禁止形态②），且正向文本含全部三个 guard 支、否定文本含两个 guard 支（全二值构造逐字核对） | 文本断言（Phase 3 oracle 用例内嵌） | Phase 3 |

---

## 八、裁定 8：负向错误码口径与 EC ④ 载体

- **错误码**：复用既有 `nop.err.eql.dialect-not-support-feature`（`OrmEqlErrors.java:200`，`[dialect]/[feature]` 参数），**不新增错误码**。feature 取值：渲染分支 = `is[not] distinct from`；与 WI7b `rollup|cube` 的 feature 字符串先例同型。全 WI 不出现裸 `RuntimeException`/`IllegalStateException`。
- **EC ④ 载体**：手构 `DialectModel`（先例 = `TestEqlLateralSubqueryTableSource.buildDialect`，实测 `:77`）：`features` 不含任何 distinct 标志 + 名称不在任何集合 ⇒ 走渲染第 3 支报错。**"SQLite <3.39"语义** = 手构方言名 `sqlite-low`（或 default 无名方言），矩阵语义由 §三 sqlite 行与 G-1 版本门承载——**不新建 `sqlite.dialect.xml`**（plan 27 Non-Goal）。手构载体同时覆盖"无 transformer 配置 + 不支持方言"的静默输出守护负向用例（#24）。
- **版本门形态**：与 G-1 结论一致——SQLite 3.39 是承诺下限，无运行时版本探测（仓库无 sqlite 方言文件，无生产通路），EC ④ 以手构载体 + 文档矩阵表达；不引入假版本检查代码。

---

## 九、裁定 9：文法备选分支形态（altLabel 三约束 + NOT 消费 + 歧义论证）

### 9.1 新备选清单（分 Phase 落地，各自单独提交）

**Phase 1**（`sqlExpr_primary`，紧跟 `SqlIsNullExpr` 备选之后、`SqlBinaryExpr_compare` 之前插入；⚠️ 执行期机制修订——操作数用**无 label 规则引用**，见 9.2.5）：

```antlr
| sqlExpr_primary IS NOT DISTINCT FROM sqlExpr_predicate # SqlBinaryExpr_isNotDistinctFrom
| sqlExpr_primary IS DISTINCT FROM sqlExpr_predicate # SqlNotExpr_isDistinctFrom
```

**Phase 2**（同规则续插；`isUnknown` 保留 `expr=`/`not=` label——SqlIsNullExpr 两 prop 俱在，生成体即正确零覆盖；TRUE/FALSE 四备选操作数无 label）：

```antlr
| expr=sqlExpr_primary IS not=NOT? UNKNOWN # SqlIsNullExpr_isUnknown
| sqlExpr_primary IS TRUE # SqlAndExpr_isTrue
| sqlExpr_primary IS NOT TRUE # SqlNotExpr_isNotTrue
| sqlExpr_primary IS FALSE # SqlAndExpr_isFalse
| sqlExpr_primary IS NOT FALSE # SqlNotExpr_isNotFalse
```

### 9.2 altLabel 三约束逐条落实（WI1 interval / WI5 position / WI7b rollup 先例）

1. **label 前缀 = 目标 AST 类名**（`AstGrammarBuilder.getAstNodeName` = 首段下划线前缀 capitalize，实测 `AstGrammarBuilder.java:259-262`）：`SqlBinaryExpr_isNotDistinctFrom`→`SqlBinaryExpr`、`SqlNotExpr_isDistinctFrom`/`_isNotTrue`/`_isNotFalse`→`SqlNotExpr`、`SqlIsNullExpr_isUnknown`→`SqlIsNullExpr`、`SqlAndExpr_isTrue`/`_isFalse`→`SqlAndExpr`——全部是既有 AST 类，**不新增节点**。
2. **备选内 prop 赋值边界**：`IS NOT? TRUE/FALSE` 的 `NOT` **不带 prop**——`SqlBinaryExpr`/`SqlNotExpr`/`SqlAndExpr` 无 `not` prop 且方向已由备选拆分承载（`# SqlNotExpr_isNotTrue` vs `# SqlAndExpr_isTrue`），无需 ctx 访问器；`IS not=NOT? UNKNOWN` 保留 `not` prop（`SqlIsNullExpr` 有该 prop，生成体直接可用——**零手写覆盖**）。DISTINCT FROM 两备选同样不带 `not` prop（方向 = label）。
3. **同规则既有备选 label 全显式化**：`sqlExpr_primary` 现有四备选（`SqlIsNullExpr`/`SqlBinaryExpr_compare`/`SqlCompareWithQueryExpr`/`SqlExpr_predicate2`）**已全部带 label**（实测 `BaseRule.g4:142-148`），无需补登记；新备选插入位置不改变既有备选次序。
4. **手写 visit 覆盖范围**：Phase 1 两备选 + Phase 2 的 `SqlAndExpr_isTrue`/`SqlNotExpr_isNotTrue`/`SqlAndExpr_isFalse`/`SqlNotExpr_isNotFalse` 共 6 个方法**完全重写、不调 super**（生成体只建空节点 + validate 必失败——WI5 position javadoc 先例同款）；`SqlIsNullExpr_isUnknown` **零覆盖**（生成体即正确）。
5. **⚠️ 执行期机制修订（2026-10-04 Phase 1 实测，gen 失败 `get-prop-on-null-obj`）**：codegen 的 `AstGrammar.initRuleReturnType`（`/nop/templates/antlr/@init.xrun:24-25`）对备选内**每个带 label 元素**做 `ast.getDefine(label前缀).getProp(label名)` 校验——`SqlNotExpr` 仅 `expr` prop，`left=`/`right=` 双 label 必炸。正确形态 = 操作数用**无 label 规则引用**（`ctx.sqlExpr_primary()`/`ctx.sqlExpr_predicate()` 访问器进手写 visit；先例 = `SqlGroupByItem_rollup` 的无 label `functionArgs_`）。本条取代上文 9.2 第 2 点对 DISTINCT FROM 两备选的 prop 形态描述（`SqlIsNullExpr_isUnknown` 的 `expr=`/`not=` label 因 prop 俱在而保留）。

### 9.3 ANTLR 歧义消解论证（登记）

- **保留 token 无竞争**：`DISTINCT`（Keyword.g4:150）/`TRUE`（:269）/`FALSE`（:273）/`UNKNOWN`（SQL92Keyword.g4:684）均为具名保留 lexer token，**不在** `unreservedWord_`（`BaseRule.g4:72-87` 实测无此四词）⇒ 在 `expr=sqlExpr_primary IS <token>` 位置不可能与 `IDENTIFIER_`/列引用竞争（列名 `true` 等本就因保留词不可裸引用——既有行为，非本 WI 改变）。
- **备选间不相交**：`IS [NOT] NULL` vs `IS [NOT] DISTINCT FROM` vs `IS [NOT] TRUE/FALSE/UNKNOWN`——首 token 之后第二 token（NULL/DISTINCT/TRUE/FALSE/UNKNOWN）互斥，ANTLR adaptivePredict 线性消解；`IS NOT DISTINCT FROM` 与 `IS DISTINCT FROM` 由 NOT token 消解。
- **左递归形态不变**：新备选沿用 `expr=sqlExpr_primary ...` 左递归形态（与 SqlIsNullExpr 同款），ANTLR 左递归变换自动处理；`right=sqlExpr_predicate` 与 `:145` 比较右臂同级（`a is distinct from b in (1,2)` 的吞并行为与 `a = b in (1,2)` 一致——既有语义面，登记不改变）。
- **优先级/括号**：`SqlOperator.SAFE_EQ` 优先级 110 与比较算子同级 ⇒ `printBinaryExpr` 的 `requireParentheses` 行为与 `=` 逐字一致（回显确定性）。

---

## 十、Phase 序与提交纪律（裁定汇总）

| Phase | 交付 | `.g4` 提交 |
|---|---|---|
| Phase 1 | `SqlOperator.SAFE_EQ` 成员 + DISTINCT FROM 两备选（文法提交①）+ 6 个手写 visit + 渲染三分支覆写 + h2/mysql 正向 + 手构方言负向 | 文法与逻辑分开：`.g4`+重生成单独提交，枚举/visitor/生成器逻辑次提交 |
| Phase 2 | TRUE/FALSE/UNKNOWN 五备选（文法提交②）+ 4 个手写 visit（isUnknown 零覆盖）+ 真值表 6×4 用例 + 错误降级负向对照 | 同上，与 Phase 1 提交分离 |
| Phase 3 | 生产 transformer（oracle 集合展开）+ beans.xml 默认注入 + nop-orm 两向缓存回归 + oracle/mysql 对照（EC ①②）+ 手构 sqlite-low 负向（EC ④） | 无 `.g4` 改动 |

生成纪律：重生成 `./mvnw generate-sources -pl :nop-orm-eql -am`；每代码 Phase `git status` 中 `ast/_gen/` 78 文件零 diff、无 lexer token 删除（`Symbol.g4:38` 原样）；语料棘轮 `TestEqlCorpusBaseline` 每 Phase 实测。
