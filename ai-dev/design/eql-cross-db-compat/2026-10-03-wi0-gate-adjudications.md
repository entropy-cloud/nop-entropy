# EQL 跨数据库兼容性：WI0 四门书面裁决（G-1..G-4）

**日期**：2026-10-03
**范围**：EQL 跨库兼容性 roadmap（`ai-dev/backlog/eql-cross-db-compat-roadmap.md`，下称 roadmap）前置决策门 G-1..G-4；影响面涉及 `nop-persistence/nop-dao`（方言 XML / selector）、`nop-persistence/nop-orm-eql`（文法与编译期报错形态）、`nop-kernel/nop-commons`（`StdSqlType`，仅 G-3 授权进 WI9a，本裁决不实施）
**状态**：active —— G-1 / G-3 / G-4 已裁决；G-2 维持 open（watch-only residual）
**授权模式**：平台负责人（用户）经 2026-10-03 对 roadmap 的执行指令授权；AI 基于证据链代拟结论，**人可推翻** —— 推翻时按各门"回滚方式"撤销，并在本目录追加修订裁决。G-4 的授权依据是「AI Must Ask Before」第 1 条（产品范围变更且 owner doc 有歧义）所需的书面裁决，非 AGENTS.md 字面 Protected Area。

---

## 一、裁决总表

| Gate | 结论 | 生效面 | 回滚单位 |
|---|---|---|---|
| G-1 最低支持版本 | **采纳** roadmap 已确认门为承诺基线；全部 unverified 边界维持 unverified 纪律（不计入覆盖率） | WI1、WI2（仅 BUG-8）、WI3（仅 2 项版本门）、WI7、WI8、WI9a/WI9b、WI10a | 各 WI 的版本门提交，逐个 git revert |
| G-2 `GREATEST`/`LEAST` NULL 策略 | **维持 open** —— 不阻塞任何 WI（仅 ledger 项）；successor = G-2 裁决后进 WI6 同批 | 无任何 WI；仅「明确延后项」ledger 首行 | 不适用（未裁决即未实施） |
| G-3 类型别名影响面 | **接受** `TEXT→CLOB` / `NUMBER→NUMERIC` / `BOOL→BOOLEAN` 进 WI9a；真实数据实证零既有模型受影响 | WI9a；WI9b 不受别名影响但共享回归门 | WI9a 的 `stdNameMap` 别名单独提交，git revert |
| G-4 方言契约变更 | **批准** WI3 按 roadmap WI3 表逐项纠错（11 项，含 7 项改值） | WI3 全部 11 项 | 每项独立提交，git revert 单项 |

---

## 二、G-1 最低支持版本

### 结论

采纳 roadmap「前置决策门」G-1 行已确认的最低版本为**承诺基线**：

| 数据库 | 承诺门 | 依据特性 |
|---|---|---|
| Oracle | 23ai | 原生 `BOOLEAN`（21c 文档无） |
| MySQL | 8.0.31 | `INTERSECT` / `EXCEPT` |
| MariaDB | 10.2 | 窗口函数 |
| MariaDB（UPDATE RETURNING） | 13.0 | `UPDATE … RETURNING` |
| PostgreSQL | 15 | `MERGE`（且 15 的 `MERGE` 无 `BY SOURCE`） |
| MSSQL | 2022 | `GREATEST` / `IS DISTINCT FROM` |
| SQLite | 3.39 | `RIGHT/FULL JOIN`、`IS DISTINCT FROM` |

全部 **unverified 边界维持 unverified 纪律，不计入覆盖率**（清单以 roadmap「关键约束与红线 → 证据等级纪律」为准，逐条列出）：

1. Oracle 12.1 边界（`LATERAL` / `OFFSET..FETCH` 仅 21c 文档确认，11g/12.1 的 Oracle `sqlrf` SELECT 页返回 404）。
2. DB2 9.7 边界（DB2 i 7.5.0 文档亦含 `LATERAL`，削弱 9.7 假设；11.5 已确认）。
3. DuckDB 1.4 边界（`MERGE` 引入版本）。
4. MariaDB `INSERT…RETURNING` / `REPLACE…RETURNING` / `DELETE…RETURNING` 引入版本（仅 `UPDATE…RETURNING` 有明确 13.0 门）。
5. TDengine `LATERAL`（一手文档零提及；TDengine 整体无一手文档）。
6. DM8 全部矩阵格（无公开一手文档，含默认 `LIKE` escape 字符与 `RETURNING` 语义 → WI3 第 5 项只标注不改值）。
7. Oracle `IS TRUE`/`IS FALSE` 简写与 `UPDATE..FROM` 的引入版本（23ai 文档未见）、Oracle 无-`FROM` `SELECT`。
8. DB2 `greatest`/`least` NULL 语义与聚合 `FILTER`（IBM 文档直连 403）。
9. H2 聚合 `FILTER`（实践中支持但 h2database.com 五页零 `FILTER`）。
10. Oracle `GREATEST` 的 NULL 语义（行为真实但未被函数页明文）。
11. MySQL 8.4 的正则 operator 页。

**低于门的行为处置原则**：编译期报错（默认形态，遵循 `ERR_EQL_NOT_SUPPORT_ILIKE` 已确立的"静默降级改变语义必须显式报错"原则）或双档方言拆分（仅在既有 `mysql`/`mysql5.7` 拆分先例适用的场景），由各 WI 的 plan 逐项裁定并记录。

### 证据引用

- roadmap Gate 表 G-1 行（`ai-dev/backlog/eql-cross-db-compat-roadmap.md:65`）—— 七个已确认门与"未取得一手引证的边界"清单。
- roadmap 红线节「证据等级纪律」（同文件 `:462-463`）—— unverified 清单原文与"禁止用未取证支撑覆盖率"纪律。
- 版本门只在两处进 WI 范围：WI3 的 `INTERSECT`/`EXCEPT` 版本门与 mariadb `RETURNING` 版本门；WI2 仅 BUG-8（roadmap 依赖图注 `:439`）。

### 影响与回滚方式

- **影响**：G-1 是承诺声明，不直接产生代码；它把"低于门怎么办"的决定权收拢到各 WI plan（编译期报错 vs 双档方言），消除"每个 WI 自己假设版本"的分叉。依赖它的 WI1/WI2/WI3/WI7/WI8/WI9a/WI9b/WI10a 据此开工。
- **回滚**：本裁决本身无代码面。后续各 WI 落地的版本门各自独立提交，回滚 = `git revert` 对应提交；unverified 清单是登记而非删除，随时可整体复议。

### 裁决人注记

平台负责人（用户），经 2026-10-03 执行指令授权；AI 基于调研证据链代拟，人可推翻。推翻时的显式后果：受影响 WI 的版本门形态需重开裁定，已登记的 unverified 边界若被要求纳入承诺，必须先补一手取证，不得凭记忆入表。

---

## 三、G-2 `GREATEST`/`LEAST` 的 NULL 策略

### 结论

**维持 open，不裁决。** 该门不阻塞任何 WI —— `greatest`/`least` 已在 roadmap「明确延后项」ledger 登记，不属于任何 WI 交付范围。successor 路径：G-2 裁决后进 **WI6 同批**（若届时采纳"忽略 NULL"语义）。

### 证据引用

- roadmap Gate 表 G-2 行（`:66`）：阻塞列原判即"仅阻塞「明确延后项」中的 `greatest`（不属于任何 WI）"；依赖图注（`:438`）明写 G-2 不指向任何 WI。
- roadmap ledger 首行（`:127`）：重新评估触发条件 = **G-2 裁决后**；successor = 若采纳"忽略 NULL"进 WI6 同批。
- 语义证据（本裁决独立复核后重述）：各库 NULL 语义分三类 —— N1"任一 NULL 即 NULL"（Oracle / MySQL / DB2）；N2"忽略 NULL"（PG（官方文档自认偏离标准）/ MSSQL 2022+）；H2 原生（`REGULAR`/`STRICT` 签名带 `RESPECT NULLS`/`IGNORE NULLS` 变体，默认 `RESPECT`），忽略 NULL 仅其 `LEGACY`/`MS SQL Server`/`PostgreSQL` 三种兼容模式。SQLite **永无**该函数（只有标量 `min`/`max`）。DuckDB/MariaDB/DM8 未文档化，DB2 无一手引证。

### 影响与回滚方式

- **影响**：零 WI 阻塞影响。plan WI0 的「Deferred But Adjudicated」已将其归类为 `watch-only residual`。本裁决把 roadmap Gate 表 G-2 行的"阻塞"列改为明确的"不阻塞任何 WI（仅 ledger 项）"，消除"仍阻塞但已裁决"的表述矛盾。
- **回滚**：不适用（未裁决即未实施）。若产品侧后续裁决，按 ledger successor 路径进 WI6 同批，届时本目录追加 G-2 修订裁决并同步 ledger 行。

### 裁决人注记

本门 Gate 表标注裁决人为**产品负责人**（非平台负责人），涉及查询语义的产品决策（NULL 传播 vs 忽略 NULL 会改变用户可见结果）。AI 不代拟该决策 —— 这正是本门维持 open 的原因：没有产品需求输入时强行裁决即是 AGENTS.md「AI Must Ask Before」第 1 条要阻止的行为。

---

## 四、G-3 类型别名影响面

### 结论

**接受**给 `StdSqlType.fromStdName` 的 `stdNameMap` 加 `TEXT→CLOB` / `NUMBER→NUMERIC` / `BOOL→BOOLEAN` 三条别名，进入 **WI9a** 实施。影响分析用全仓真实数据实证（见下），结论是**纯增量、零既有模型受影响**。WI9b 的时区类型不受 G-3 别名影响，但共享同一回归门（114 个 orm.xml 全量 `clean install`）。

### 影响分析（真实数据，2026-10-03 实测）

统计口径：`git ls-files '*.orm.xml'`（即 roadmap 的 114 口径；磁盘上另有 4 个 `_dump/` 下未追踪构建产物不在口径内）。

| 统计项 | 实测值 | 与 roadmap 基线核对 |
|---|---|---|
| git 追踪 `*.orm.xml` 总数 | **114** | ✅ 一致 |
| 其中生成的 `_app.orm.xml` | **19** | ✅ 一致 |
| 其余（手写模型） | **95** | ✅ 一致 |
| `stdSqlType=` 属性总数（114 文件） | **8332**（生成物内 3414 + 非生成 4918） | ✅ 一致 |
| `stdSqlType="TEXT"` 出现次数 | **0** | —— 纯增量实证 |
| `stdSqlType="NUMBER"` 出现次数 | **0** | —— 纯增量实证 |
| `stdSqlType="BOOL"` 出现次数 | **0** | —— 纯增量实证 |

三个别名值在 114 个文件（含 19 个生成物）与 95 个非生成文件中出现次数均为 **0**；并做了值部分大小写不敏感复查，同样零命中。⇒ "加别名不改任何既有 orm.xml 的解析结果"不是推断，是全量实证。

机制层面（为什么加别名是安全的增量）：

1. `stdNameMap` 同时驱动 **DDL 类型映射**与**合法 `stdSqlType=` 取值闸门**（roadmap G-3 行 `:67` 的原判，v3 修正后影响面比初版描述更大）。但别名是**新增映射条目，不修改任何既有映射** —— 既有合法取值的映射结果逐字不变。
2. 合法取值闸门有两处消费点：`nop-persistence/nop-orm-eql/.../parse/EqlASTBuildVisitor.java:270-272`（手写类，`SqlTypeExpr_name` 经 `StdSqlType.fromStdName` 判空后抛 `ERR_EQL_INVALID_SQL_TYPE`，param 携带 `ARG_ALLOWED_NAMES = StdSqlType.getNames()`）；`nop-kernel/nop-xlang/.../xdef/domain/SimpleStdDomainHandlers.java:1630/1667`（orm.xml 的 std 域校验，未登记 stdName 抛 `ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN`）。别名入表后这两处自动放行新值，既有值的判定路径不变。
3. 实施位置：`nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java` 的 `stdNameMap`（声明 `:146`，static 块灌值 `:152-161`）—— WI9a 按既有惯例在此加三条 put。

**落地门**：WI9a 落地别名时必须通过 114 个 orm.xml 全量回归（`./mvnw clean install -T 1C`），并把"加别名会改变哪些既有模型解析结果"的影响清单列入 WI9a 交付（本裁决的实证结果 = 空清单，落地时须重跑同一统计核对仍为 0 命中后归档）。

### 证据引用

- roadmap Gate 表 G-3 行（`:67`）与 WI9a 节（`:318-324`）：`stdNameMap` 双重角色、两处闸门锚点、114/8332 口径。
- 调研结论重述（作为本设计文档的独立章节内容，不回引过程文档）：`TEXT`/`NUMBER`/`BOOL` 在各主流库是常见类型拼写（如 PG 的 `TEXT`、Oracle 的 `NUMBER`），而 EQL 的 `CAST` 目标今天拒绝它们（解析期即报 `ERR_EQL_INVALID_SQL_TYPE`）；采纳的映射为 `TEXT→CLOB`、`NUMBER→NUMERIC`、`BOOL→BOOLEAN` —— 三者都落到既有 `StdSqlType` 闭集成员，不新增枚举值。

### 影响与回滚方式

- **影响**：本裁决只授权 WI9a 实施，不改任何现有行为（实证 0 命中）。对既有用户唯一可见的变化发生在 WI9a 落地后：原先报 `ERR_EQL_INVALID_SQL_TYPE` / `ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN` 的三种拼写变为接受。
- **回滚**：WI9a 的 `stdNameMap` 别名改动**单独提交**，回滚 = `git revert` 该提交；别名删除后，使用新拼写的模型将恢复"解析期报错"，属可发现的响亮失败，无静默数据风险。

### 裁决人注记

平台负责人（用户），经 2026-10-03 执行指令授权。AI 代拟的关键判断是"纯增量"由全量 grep 实证背书，而非由"看起来无害"背书；若未来统计口径变化（如新增模块带入 `TEXT` 取值的 orm.xml），WI9a 落地时重跑统计即可发现，不依赖本裁决过期。

---

## 五、G-4 方言契约变更确认

### 结论

**批准 WI3 按 roadmap WI3 表逐项纠错**（11 项待结论项，其中 7 项改值 + 1 项只标注不改值 + 1 项确认无需改动 + 2 项版本门/selector 修复）。方言 XML 是平台对外声明的能力契约，本裁决即该项契约变更的书面批准；批准范围严格限于 WI3 表所列 11 项，超出清单的方言契约变更须重新走询问。版本门形态与 G-1 裁决一致（编译期报错为默认、双档方言为例外且须记录理由）。

### 证据引用（逐项，对应 roadmap WI3 表 `:208-223`）

| # | 项 | 证据链要点（均已在 roadmap WI3 表登记） |
|---|---|---|
| 1 | `mysql.dialect.xml:137` `supportReturningForUpdate` → `false` | MySQL 8.4 `SELECT`/`INSERT`/`UPDATE` 文法均无 `RETURNING`；`mysql5.7.dialect.xml:5` 已写 `false`，`:137` 是遗漏（锚点已实测复核） |
| 2 | `oracle.dialect.xml:100` → `false` | Oracle `UPDATE … returning_clause` 只允许 `INTO` 宿主变量形式，生成器的投影列表形式非法（锚点已实测复核） |
| 3 | `h2.dialect.xml:18` → `false` | H2 五页文档零 `RETURNING`（锚点已实测复核） |
| 4 | `duckdb.dialect.xml:21` | **保持 `false` 不动**（DuckDB `RETURNING` 仅在 `INSERT`/`DELETE`/`MERGE`，`UPDATE` 文档零出现）—— 确认无需改动项（锚点已实测复核） |
| 5 | `dm.dialect.xml:67` | 只显式标注 `unverified`，不改值 —— DM8 无一手文档，按 G-1 红线不得伪造"已修正"（锚点已实测复核） |
| 6 | `mariadb`（继承 `mysql` 的 `true`） | 随第 1 项连带变 `false`，但需版本门（`UPDATE…RETURNING` 自 MariaDB 13.0），形态与 `INTERSECT` 版本门一致，受 G-1 约束 |
| 7 | `mysql5.7` | 移除继承的 `window-expr-support` mixin；**不顺手改 mariadb**（MariaDB 10.2 确有窗口函数） |
| 8 | `postgresql`/`duckdb` 窗口 mixin | ✅ 已由 `add-stream-sql` 合并完成，本项为"已完成"记录，不占桶 |
| 9 | h2 / postgresql / duckdb 注册 `ilike` 函数 | `supportILike` 标志正确而函数表缺失；PG 原生 ILIKE；照 `oracle.dialect.xml:174` 模板 |
| 10 | `defaultNullsFirst` 接线 | 7 个 XML 已配、零 Java 消费方；与 `supportNullsFirst` 是正交事实，接线时保留后者 |
| 11 | `h2` 补 `defaultNullsFirst="true"` | H2 默认 NULL 序 `LOW`（ASC 时 NULL 在前） |
| 12 | `db2.selector.yaml` 断链 | selector 已注册而 `db2.dialect.xml` 缺 mandatory 子节点 ⇒ DB2 自动选择当前是坏的；先按 WI0 复现步骤确认再修 |

每项依据的完整原文（含官方文档引语）见 roadmap WI3 表"依据"列，本裁决不复制、以 roadmap 为准。

### 影响与回滚方式

- **影响**：WI3 是方言能力契约的纠错批次。7 项改值中，第 1/2/3 项把"会生成非法 SQL 的 `true`"改为 `false`（后续配编译期报错负向用例）；第 9/10/11 项是补齐缺失注册/接线；第 12 项修复自动选择断链。全部是"让声明的能力与数据库真实能力一致"，无一项放松现有保证。
- **回滚**：roadmap 红线已要求每个 WI 单独成 plan、单独提交（`:464`）。WI3 的 11 项按项独立提交，回滚 = `git revert` 对应单项提交；方言 XML 值在 git 历史与 WI0 的方言能力矩阵快照（`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`）中双份留痕，可 diff 审计。

### 裁决人注记

平台负责人（用户），经 2026-10-03 对 roadmap 的执行指令授权 —— 该授权满足「AI Must Ask Before」第 1 条要求的"人裁决"（产品范围与对外契约变更）。AI 代拟的边界：裁决范围 = WI3 表 11 项，逐项均有调研证据链且可独立回滚；若执行中发现证据链不成立（如官方文档口径变化），该项停下重新询问，不得凭"已批准 WI3"外推。

---

## 六、与既有设计的关系

- 本文档是 roadmap「前置决策门」节的裁决记录载体；roadmap Gate 表的"阻塞"列已按本裁决回写（G-2 改为不阻塞任何 WI，G-1/G-3/G-4 追加已裁决注记并保留原描述作溯源）。
- 平台使用侧的语法溯源事实（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 注释态规则从未生效）已记入 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 的「语法溯源」小节。
- G-3 的实施契约（别名表、必拒项收窄、影响清单）由 WI9a 的 plan 承接；G-4 的执行契约由 WI3 的 plan 承接。本文档只记录"批准与否 + 依据 + 回滚"，不重复实施细节。
