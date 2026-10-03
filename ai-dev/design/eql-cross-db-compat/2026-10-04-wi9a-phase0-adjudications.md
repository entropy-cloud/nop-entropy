# EQL 跨数据库兼容性：WI9a Phase 0 前置裁定

**日期**：2026-10-04
**范围**：`ai-dev/plans/25-eql-wi9a-cast-type-aliases.md`（plan Status: active）Phase 0 的 8 项裁定；涉及 `nop-kernel/nop-commons`（`StdSqlType`）、`nop-persistence/nop-dao`（方言 XML `<sqlDataType>` 新增条目）、`nop-persistence/nop-orm-eql`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `dataTypeName_` 收窄）
**状态**：active —— 全部 8 项已裁定；代码未动（本 Phase 纯裁定，零产品代码变更）
**授权模式**：同 G-3 模式（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` §裁决人注记）——平台负责人（用户）经 2026-10-04 对 WI9a plan 的执行指令授权、AI 基于证据链代拟、**人可推翻**；推翻时按本文件各裁定项的"回滚方式"撤销，并在本目录追加修订裁决。其中 UUID 形态涉及 `nop-kernel/nop-commons` 模型层变更，属「AI Must Ask Before」同级的 ask-first 项，**不得以 plan-first 之名替代询问**——其授权即本注记所述的执行指令授权，见 §四。

---

## 〇、开工前置回退防护复核（2026-10-04 实测）

| 复核项 | 结论 | 证据 |
|---|---|---|
| WI0 产物 1：语料基线快照 | **未回退**。`TestEqlCorpusBaseline` 1/1 PASS（surefire 报告 `Tests run: 1, Failures: 0`，mtime 2026-10-04 06:45，晚于本 WI 开工前最后 commit `949531d93e`） | nop-orm-eql 模块 surefire-reports 下 `io.nop.orm.eql.parse.TestEqlCorpusBaseline.txt` |
| WI0 产物 2：方言矩阵快照门禁 | **未回退**。`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（18 个 dialect XML、18 注册、15 可加载、3 不可加载：`default`/`geo-support`/`window-expr-support`；伪方言 `es` 单列不入矩阵） | 门禁输出与本节记录 |
| 硬前置 plan 状态 | WI0 `completed`、WI2 `completed`、G-3/G-1 已裁决（2026-10-03）——与 plan Current Baseline 记载一致，无排队门 | `ai-dev/plans/02-*`/`19-*`、`2026-10-03-wi0-gate-adjudications.md` |
| 锚点符号复核 | plan 所引全部符号仍存在，**行号整体漂移**（WI3/WI5 并行落地所致）：`dataTypeName_` `:381-390`→`:428-437`；`sqlCastExpr` `:292`→`:339`；`SqlTypeExpr_name` 手写闸门 `EqlASTBuildVisitor.java:321-326`→`:470-474`；h2 std DECIMAL→native `NUMERIC`（WI3 后 `:221`→现 `:242`）、h2 BOOLEAN `:227`→`:248`、oracle native `NUMBER`→std DECIMAL `:212`→`:252`。本文件与执行一律以符号定位为准 | 逐符号 grep 实测 |

## 一、G-3 零命中统计重跑（影响清单落地时重跑，G-3 落地门要求）

统计口径与 G-3 裁决（§四）一致：`git ls-files '*.orm.xml'`；扩展口径 = 全仓 `_vfs/**` + `*.xdef` + `*.xmeta` 资源的 `stdSqlType|returnType|argTypes|stdType` 属性取值。

| 统计项 | 2026-10-03（G-3 时点） | **2026-10-04 重跑（本裁定）** |
|---|---|---|
| git 追踪 `*.orm.xml` 总数 | 114 | **114**（含生成的 `_app.orm.xml` **19**） |
| `stdSqlType=` 属性总数 | 8332 | **8332** |
| `stdSqlType="TEXT"/"NUMBER"/"BOOL"/"UUID"`（大小写不敏感）命中文件数 | 0（前三项；UUID 未单测） | **0**（四值合并单测，0 文件命中） |
| 扩展口径（`_vfs`/`xdef`/`xmeta` 的四类属性取 `TEXT/NUMBER/BOOL/UUID`）命中文件数 | ——（G-3 未做扩展口径） | **0**（0 文件命中） |

**结论：影响清单 = 空清单（预期实证成立）**。"加别名会改变哪些既有模型解析结果"的答案是没有——别名/UUID 均为纯增量。若 Phase 1 落地后需终局复核，重跑同一命令即可（命令见 daily log 与本文件；回归门为组合判据 (a)）。

## 二、WI3 落地后方言锚点与可加载方言清单重核

### 2.1 可加载方言清单（对 plan"db2 为 stub"表述的修订）

WI0 快照（`ai-dev/audits/eql-wi0/dialect-capability-matrix.json`）+ 门禁实测：**18 个 dialect XML，15 个可加载**——`db2`、`dm`、`duckdb`、`es`（伪方言，单列不入矩阵）、`h2`、`h2gis`、`mariadb`、`mssql`、`mysql`、`mysql5.7`、`oracle`、`oracle-reverse`、`postgis`、`postgresql`、`tdengine`；不可加载 3 个：`default`（缺 mandatory 子节点）、`geo-support`、`window-expr-support`（mixin）。

**修订**：plan 起草时"db2 为 stub、不可实例化、不入矩阵"的表述**已过时**——WI3 第 12 项已补 `db2.dialect.xml` 的 `driverClassName`/`jdbcUrlPattern` mandatory 子节点，db2 现在可加载（快照 `db2 → loadable: True`）。对本 plan 的影响：
- 测试载体**仍不使用 db2**（其 `<sqlDataTypes>` 缺失、无保留字表，且 DB2 无一手文档按红线不入覆盖率口径）；"不支持"负向载体改用**手工最小方言**（WI2 先例）与**真实方言未映射者**（mssql-TEXT、mysql/oracle-UUID）。
- `TestEqlCastBooleanCompile` javadoc 中"db2 不可实例化"的注释性表述随本裁定过时；本 plan 不改该 javadoc（非行为代码，避免噪声 diff），在此登记为文档性 follow-up。

### 2.2 方言 `<sqlDataTypes>` 逐方言实测（别名目标的 std→native 首选渲染，`SqlDataTypeMapping.stdToNativeSqlType` 链路：取该 std 键条目列表中首个非 deprecated 且 `isAllowPrecision` 匹配项；`code` 属性优先于 `name` 渲染，`buildSQLDataType` :116-124）

| 方言 | std CLOB | std DECIMAL | std BOOLEAN | std UUID（现值） |
|---|---|---|---|---|
| h2 | CLOB（`:231`） | **NUMERIC**（`:242`，唯一条目） | BOOLEAN（`:248`） | 无 |
| mysql | LONGTEXT（`:178`） | DECIMAL（`:185`） | BOOLEAN（`:191`，alias BIT） | 无 |
| mariadb | LONGTEXT（继承 mysql；自有段仅 merge VARCHAR/TEXT） | DECIMAL（继承） | BOOLEAN（继承） | 无 |
| oracle | CLOB（`:246`） | **NUMBER**（`:252`） | CHAR(1)（`:262`，code） | 无 |
| postgresql | TEXT（`:134`） | NUMERIC（`:141`） | BOOLEAN（`:143`） | 无 |
| duckdb（`x:override="replace"` 自有全量表） | **无** | DECIMAL（`:50`，alias NUMERIC） | BOOLEAN（`:52`） | 无 |
| mssql | **无** | DECIMAL（`:265`；NUMERIC `:266` deprecated 不参与） | BIT（`:262`） | 无 |
| dm | TEXT（`:83`，其后 LONG/CLOB） | NUMBER（`:90`，其后 DECIMAL/NUMERIC） | BOOLEAN（`:106`） | 无 |
| tdengine | **无** | DOUBLE（`:193`，code=DOUBLE） | BOOL（`:182`） | 无 |
| db2 | 无（无 `<sqlDataTypes>` 段） | 无 | 无 | 无 |

（`default` 无 `<sqlDataTypes>` 段、`es` 为伪方言，均不入载体表。oracle-reverse 继承 oracle、postgis 继承 postgresql、h2gis 继承 h2、mysql5.7 继承 mysql，随父表。）

### 2.3 "两闸门直调 `StdSqlType.fromStdName`、无独立白名单"结论复核

**结论不变**（WI3 只改方言 XML 值/函数注册，未触及两闸门）：闸门 A = 手写 `EqlASTBuildVisitor.SqlTypeExpr_name`（`EqlASTBuildVisitor.java:470-474`，`fromStdName` 判空抛 `ERR_EQL_INVALID_SQL_TYPE`，param `ARG_ALLOWED_NAMES = StdSqlType.getNames()`）；闸门 B = `SimpleStdDomainHandlers` 的 `StdSqlTypeType`（`:1630`）/`StdSqlTypeListType`（`:1667`），同调 `fromStdName`，未登记抛 `ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN`。⇒ 别名/UUID 入 `stdNameMap` 后两闸门自动放行，无第三处需要同步的白名单。

### 2.4 词法事实复核（决定收窄后失败形态）

- 单词项 `CHARACTER`（Keyword.g4:365）、`PRECISION`（:114）、`DOUBLE`（:357）、`INTERVAL`（:373）为 lexer 关键字 token；`BIT`（parser 词表 `BIT=233`）、`DEC`（`DEC=254`）、`NATIONAL`（`NATIONAL=296`）、`NCHAR`（`NCHAR=297`）、`VARYING`（`VARYING=347`）、`ZONE`（`ZONE=352`）在生成 parser/lexer 词表——均与 plan 起草实测一致（token 号未变，WI3/WI5 未重排）。
- `TEXT`/`BOOL`/`UUID` 在生成 `EqlLexer.java`/`EqlParser.java` 词表中**均不存在**（`TEXT=206` 确为 `MESSAGE_TEXT=206` 子串误读的澄清仍成立）⇒ 四个新拼写全部词法化为 `IDENTIFIER_`、经 `sqlIdentifier_` 兜底进入 `SqlTypeExpr_name`。
- **`StdSqlType` 25 个枚举名的"可拼写性"全量审计**（新增实测，供 Phase 2 接受集声明）：lexer 关键字 token 且**不在** `unreservedWord_`/显式备选者 = `DOUBLE`、`ARRAY`、`ANY` 三个——它们今天即无任何可解析拼写（`DOUBLE` 唯一可解析拼写是 `DOUBLE PRECISION`，见 §五）；其余成员或为显式备选（12 个活备选）或词法化为 `IDENTIFIER_` 经兜底可拼写。

## 三、裁定 1：NUMBER 别名目标——**候选 (a)，`NUMBER→DECIMAL`**（偏离 G-3 字面表，附 G-3 增补裁决）

**三选一结论：候选 (a)**。

| 候选 | 裁定 | 理由 |
|---|---|---|
| (a) `NUMBER→DECIMAL` | **采纳** | std DECIMAL 在 h2/mysql/mariadb/oracle/postgresql/duckdb/mssql/dm/tdengine **9 个可加载方言有非 deprecated 映射**，roadmap EC ① 的"支持"类用例有真方言载体；oracle 用户写 `cast(x as number)` 在 oracle 渲染原生 `NUMBER`（`oracle.dialect.xml:252`），语义与直觉一致 |
| (b) 保持 `NUMBER→NUMERIC` + 补方言映射 | 拒绝 | ① 全仓 0 个方言为 std NUMERIC 提供映射（本裁定 2.2 节逐方言实测），"支持"载体先要凭空造映射；② `dialect.xdef` key-attr + `SqlDataTypeMapping.register`（`nativeTypes.put` `:55`、alias put `:59`）对同名 native 条目 **last-wins 静默覆盖**——h2/mssql 的 native `NUMERIC`、oracle 的 native `NUMBER` 已被 std DECIMAL 占用，给已占用名挂第二个 std 目标会无信号改写既有 native→std 反向解析；只能新增未占用名，渲染文本反而不再叫 NUMERIC，别名失去意义；③ 方言契约新增超出 G-4 批准范围（其范围仅 WI3 的 11 项） |
| (c) 保持 `NUMBER→NUMERIC` 零映射落地 | 拒绝 | `cast(x as number)` 在**所有**方言解析过、生成期响亮报 `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED` ——"支持"类用例无载体，与 roadmap EC ① 字面冲突；且这正是本 WI Phase 2 要消灭的"文法/语义接受面说谎"形态在类型层的翻版 |

**期望渲染文本（按 2.2 实测链路推导，不按直觉）**：h2 → `NUMERIC`（**不是** DECIMAL——h2 std DECIMAL 唯一映射条目是 native `NUMERIC`）；oracle → `NUMBER`；mssql/mysql/mariadb/duckdb → `DECIMAL`；postgresql → `NUMERIC`；dm → `NUMBER`；tdengine → `DOUBLE`。

### G-3 增补裁决（带日期增补小节，不重写 2026-10-03 原文）

- **原裁决**：接受 `TEXT→CLOB` / `NUMBER→NUMERIC` / `BOOL→BOOLEAN` 三别名进 WI9a（`2026-10-03-wi0-gate-adjudications.md` §四）。
- **增补修订**：`NUMBER` 别名目标由 `NUMERIC` 修订为 **`DECIMAL`**。
- **修订理由**：G-3 裁决论证的是"别名落既有闭集成员、零既有模型受影响"（该论证对 DECIMAL 同样成立——本裁定 §一重跑 0 命中），但未核验目标成员的方言映射覆盖。WI9a 起草实测（plan 差异登记第 1 条）+ 本裁定 2.2 节逐方言复核证实：**std NUMERIC 无任何方言映射**（支持方言空集），且其 native 名在 h2/mssql/oracle 已被 std DECIMAL 条目占用、`register` 对同名条目 last-wins 静默覆盖，补映射 = 无信号改写既有反向映射。改目标为 DECIMAL 后：别名仍是既有闭集成员的纯增量 put（不修改任何既有映射条目）、零既有 orm.xml 受影响（0 命中不变）、且 EC ① 两类载体齐全。
- **影响**：`cast(x as number)` 从"全方言生成期报错"变为"9 个方言渲染方言情 native 文本 + 无映射方言响亮报错"；`getNames()`/闸门放行集合不变（都是放行 "NUMBER" 这个键，与映射目标无关）。
- **回滚方式**：`git revert` `StdSqlType.java` 别名单独提交（G-3 回滚单位不变），并把目标改回 NUMERIC（如需）另走裁决。
- **人可推翻声明**：本增补由 AI 代拟、经 2026-10-04 执行指令授权；平台负责人可推翻——推翻形态 = 恢复 `NUMBER→NUMERIC` 并按候选 (b)/(c) 之一重新裁定（(b) 须先解决 last-wins 静默覆盖机制，(c) 须同步登记 roadmap EC ① 适用口径修正）。
- **roadmap 同步**：G-3 行（`:67`）已原地追加带日期注记（不翻原描述）；roadmap Work Item Status 的 WI9a 行保持 `planned` 不动。

## 四、裁定 2：UUID 形态——**候选 (a)，新增 `StdSqlType.UUID` 枚举常量 + 4 方言 XML 映射**

**三选一结论：候选 (a)**。

| 候选 | 裁定 | 理由 |
|---|---|---|
| (a) 新增 `StdSqlType.UUID` 枚举常量 + 为有原生 UUID 的方言补 `<sqlDataType>` 映射 | **采纳** | UUID 无语义相同的既有闭集成员，别名化必然扭曲类型身份；枚举形态保住 `stdSqlType="UUID"` 的类型身份，orm.xml DDL 方向可在原生支持 UUID 的方言渲染真原生类型 |
| (b) `UUID→VARCHAR` 纯别名 | 拒绝 | ① 丢失类型身份：h2/postgresql/duckdb 有原生 UUID 类型，别名化使 `stdSqlType="UUID"` 列在这些方言静默退化为 VARCHAR——方向上正是本 roadmap 反对的静默降级（虽不产生错误 SQL，但产生错误的 DDL 类型）；② 不在 G-3 授权表内，需与 (a) 同级的增补授权却得到更差的结果；③ "不支持"载体将不存在（VARCHAR 全方言可用），EC ① 的"不支持"类用例只能靠手工最小方言虚设 |
| (c) 移出 WI9a | 拒绝 | roadmap WI9a 节明文包含 UUID，移出属 roadmap 范围变更，需显式裁决 + successor；在候选 (a) 成本可控（4 个 XML 条目 + 1 个枚举常量）且证据齐备时移出是不必要的范围收缩 |

**落地形态（(a) 的细则）**：

1. **枚举常量**：`StdSqlType` 末尾（`GEOMETRY` 之后，**不移动既有常量位置**——`ExprOrmDataType.s_map`/`SingleColumnExprMeta.s_meta` 均为按枚举构建的 Map（`EnumMap`/`HashMap`，`values()` 环加值，纯增量安全），但保守起见不改变任何既有常量的 ordinal）新增 `UUID(false, false, Types.VARCHAR, StdDataType.STRING)`。
   - `Types.VARCHAR` + `StdDataType.STRING`：同 `JSON`（`StdSqlType.java:91`）的既有先例（`StdDataType` 无 UUID 成员——plan Current Baseline 第 4 条实测不变；STRING 家族的 convert/binder 通路可用，`DataParameterBinders.getDefaultBinder("UUID")` 返回 null 时 `SingleColumnExprMeta` 落 `ANY` 兜底，与 JSON 今天的行为一致，无新增分支）。
2. **static 块三个 map 不变量核验**（plan Phase 0 第 5 项要求）：
   - `stdNameMap`：UUID 为枚举名，经 `values()` 循环自动入表（`:154-155`），**无需显式 put**；键集新增 `"UUID"` 一项（`getNames()` 键集变化登记为本文件附录，对应 plan 差异登记第 4 条——两闸门错误消息的 `ARG_ALLOWED_NAMES` 同步变大，属预期行为）。
   - `jdbcTypeMap`（先到先得 `:157-158`）：`Types.VARCHAR` 已被 `VARCHAR`（枚举序先于 UUID）占位 ⇒ `fromJdbcType(Types.VARCHAR)` 仍返回 `VARCHAR`，**不变量保持**。
   - `stdTypeMap`（循环 last-wins `:160` + `:162-163` 重钉）：循环后 `STRING` 键可能短暂指向 UUID（若 UUID 为最后一个 STRING 家族成员），但 `:162` 的显式重钉 `stdTypeMap.put(StdDataType.STRING, VARCHAR)` 在循环之后执行 ⇒ **最终值不变**（`fromStdDataTYpe(STRING) = VARCHAR`）。`BYT ES` 重钉同理不受影响。
3. **方言 XML 新增条目（4 个文件，逐条独立提交，每条可单独 `git revert`）**——授权依据 = roadmap WI9a EC ① 原文（WI2 差异登记 #2 的授权形态先例）+ 本裁定显式 Decision 项 + §四裁决人注记；G-4 批准范围仅 WI3 的 11 项、不含本项，故**必须**依赖本裁定而非外推 G-4：

   | 文件 | 新增条目 | 渲染 | 原生类型依据（一手文档） |
   |---|---|---|---|
   | `h2.dialect.xml` | `<sqlDataType name="UUID" stdSqlType="UUID"/>` | `UUID` | H2 数据类型文档（h2database.com/html/datatypes.html，该文件头部即引此页）列有 `UUID` 类型 |
   | `postgresql.dialect.xml` | `<sqlDataType name="UUID" stdSqlType="UUID"/>` | `UUID` | PostgreSQL 文档 8.17 UUID Type（postgresql.org/docs） |
   | `duckdb.dialect.xml` | `<sqlDataType name="UUID" stdSqlType="UUID"/>` | `UUID` | DuckDB 文档 UUID 类型（duckdb.org/docs/sql/data_types/uuid；该文件内已引用 duckdb.org docs） |
   | `mssql.dialect.xml` | `<sqlDataType name="UUID" code="UNIQUEIDENTIFIER" stdSqlType="UUID"/>` | `UNIQUEIDENTIFIER`（code 优先渲染，`buildSQLDataType:116-119`） | MSSQL `uniqueidentifier`（learn.microsoft.com T-SQL 数据类型） |

   - **反向映射安全**：四文件现网均无 native 名 `UUID` 的既有条目（本裁定 2.2 节实测"std UUID 现值=无"且全量 grep 0 命中）⇒ `register` 的 `nativeTypes.put` 无 last-wins 覆盖风险，纯新增键。
   - **不加映射的方言**：mysql/mariadb/mysql5.7、oracle/oracle-reverse、tdengine、db2（均无原生 UUID 类型/无映射）⇒ `cast(x as uuid)` 在这些方言解析过、生成期响亮报 `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED` ——这就是 UUID 的**真实方言"不支持"载体**（比手工方言更强）。
   - **矩阵快照口径**：`dialect-capability-matrix.json` 记载的是方言可加载性 + 函数集（`functions` 键），**不含 `<sqlDataTypes>`** ⇒ 新增条目不改快照内容，门禁继续 exit 0（Closure Gates"若方言 XML 有新增条目，快照口径同步"之判断：无需同步，登记于此）。

4. **裁决人注记（ask-first 级授权形态，同 G-3 模式）**：`StdSqlType` 位于 `nop-kernel/nop-commons`，模型层行为变更（`stdNameMap` 双重角色：DDL 映射 + 8 个 xdef 的 `std-sql-type` 域合法取值闸门），按 roadmap Cross-Cutting 属「AI Must Ask Before」同级、**不是** AGENTS.md 字面 Protected Area，也不得以 plan-first 之名替代询问。本项由平台负责人（用户）经 2026-10-04 对 WI9a plan 的执行指令授权（该指令明示"UUID 按 Phase 0 裁定的形态落地"并授权本注记形态）、AI 基于上述证据链代拟、**人可推翻**。推翻后果：删除枚举常量 + revert 4 个方言 XML 提交即可完全回滚（无数据/模型依赖——`stdSqlType="UUID"` 现存 0 命中）；若推翻改采候选 (b)/(c)，按被拒候选列的理由表重新走书面裁决。

## 五、裁定 3：`dataTypeName_` 收窄边界——**候选 A**（删 14 个必拒显式备选、保留 `sqlIdentifier_` 兜底）

**A/B 二选一结论：候选 A**。

- **理由**：本 plan 的收窄目标 = "文法不再承诺它注定拒绝的拼写"（消除 BUG-11 的说谎接受面），而非"消灭兜底"。候选 B 会把 `nosuchtype` 类未知拼写从带 `ARG_ALLOWED_NAMES` 高质量语义错误（`nop.err.eql.invalid-sql-type`）退化为解析失败（`nop.err.antlr.*`），**直接破坏 WI2 `TestEqlCastBooleanParse:69-78` 的存活断言**（plan 红线），且损失错误质量、无对应收益。
- **兜底保留后的完整接受面声明**（Phase 2 Exit Criteria 1 的"穷举式证明"口径）：收窄后文法接受集 = 12 个活显式备选 ∪ `sqlIdentifier_` 兜底集（全部 `IDENTIFIER_`/`unreservedWord_` 拼写）。语义层对文法放行的每个拼写**必过或响亮拒**：`stdNameMap` 键 → 方言映射渲染或 `ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED`；非键 → `ERR_EQL_INVALID_SQL_TYPE`。无静默路径。三个枚举成员 `DOUBLE`/`ARRAY`/`ANY` 为关键字 token 且无显式备选 ⇒ 文法不放行（解析失败），属"语义接受集 ⊃ 文法放行集"方向的已知缺口，**不违反"文法放行 ⇒ 语义必过或响亮拒"判据**（判据只约束放行方向）。
- **`DOUBLE` 拼写缺口登记（新发现，不顺手修）**：`DOUBLE` 是枚举成员但其唯一可解析拼写 `DOUBLE PRECISION` 属本次删除的 14 个必拒项——删除后 `DOUBLE` 家族完全无拼写入口（今天 `cast(x as double)` 本就解析失败，`DOUBLE` 是关键字 token 不在 `unreservedWord_`）。**本 plan 不新增 `DOUBLE` 备选**（Out Of Scope：roadmap 未登记的文法扩展不顺手修），登记为 follow-up（见 plan Non-Blocking Follow-ups 增补行与 daily log），successor = roadmap 登记后入 WI9b 或后续批次。

### roadmap EC ③ 对照表（定稿列结构 + 草表；Phase 2 落地后按实测错误码字面值终稿）

列结构：`被移除的拒绝项 | 收窄前行为（错误码字面值） | 收窄后行为（错误码字面值，候选 A） | 现在接受的等价拼写 → 载体方言生成 SQL | 备注（WI9b 边界/等价性登记）`。

收窄前行为统一 = 解析通过 + `SqlTypeExpr_name` 语义拒绝，错误码字面值 **`nop.err.eql.invalid-sql-type`**（`ERR_EQL_INVALID_SQL_TYPE`，`OrmEqlErrors.java:121`，param `ARG_SQL_TYPE` + `ARG_ALLOWED_NAMES`）。收窄后行为统一 = 解析失败，错误码为 **`nop.err.antlr.*` 前缀字面值**（候选值：`nop.err.antlr.common.parse-fail` / `nop.err.antlr.not-end-properly`，`AntlrErrors.java:22/25`——具体落点以 Phase 2 红→绿测试实测为准逐行终稿，测试断言用 `startsWith("nop.err.antlr")` 与 `TestEqlSqlTypeClauseGuard` 同形态）。

| # | 被移除的拒绝项 | 收窄前 | 收窄后（候选 A） | 现在接受的等价拼写 → 生成 SQL（h2 载体） | 备注 |
|---|---|---|---|---|---|
| 1 | `CHARACTER` | 解析过 + `nop.err.eql.invalid-sql-type` | `nop.err.antlr.*` 解析失败 | `char` → `cast(o.SID as CHAR)` | CHAR 显式备选存留 |
| 2 | `CHARACTER VARYING` | 同上 | 同上 | `varchar` → `cast(o.SID as VARCHAR)` | |
| 3 | `NATIONAL CHARACTER` | 同上 | 同上 | 无（std 闭集无 national charset 成员；语义近似 `varchar`，不声明等价） | 术语不对称登记，非等价替换 |
| 4 | `NATIONAL CHARACTER VARYING` | 同上 | 同上 | 无 | 同上 |
| 5 | `NCHAR` | 同上 | 同上 | 无 | 同上 |
| 6 | `NATIONAL CHAR` | 同上 | 同上 | 无 | 同上 |
| 7 | `NATIONAL CHAR VARYING` | 同上 | 同上 | 无 | 同上 |
| 8 | `BIT` | 同上 | 同上 | `boolean` → `cast(o.SID as BOOLEAN)`（BIT 的布尔语义等价；bit-varying 语义无成员） | 等价性按"BIT=布尔"通用语义登记 |
| 9 | `BIT VARYING` | 同上 | 同上 | 无（std 闭集无 bit varying 成员） | |
| 10 | `DEC` | 同上 | 同上 | `decimal` → `cast(o.SID as DECIMAL)`；别名 `number` → `cast(o.SID as NUMERIC)`（h2 链路推导） | DEC=DECIMAL 缩写，等价 |
| 11 | `DOUBLE PRECISION` | 同上 | 同上 | **无拼写入口**（`DOUBLE` 为关键字 token 且不在 `unreservedWord_`——既有缺口随删除显形，follow-up 登记，不顺手修） | §五 |
| 12 | `INTERVAL` | 同上 | 同上 | 无（INTERVAL 走 WI1 的 interval 字面量语法，不是 cast 目标类型） | 与 `sqlIntervalExpr`/`sqlIdentifier_func_` 共用 token 不触碰 |
| 13 | `TIME WITH TIME ZONE` | 同上 | 同上 | 无 | **WI9b 边界注记**：`… WITH LOCAL TIME ZONE` 拼写归 WI9b，与本删除项是两个名字集 |
| 14 | `TIMESTAMP WITH TIME ZONE` | 同上 | 同上 | 无 | **WI9b 边界注记**：同上（方言 XML native 名 `TIMESTAMP WITH TIME ZONE` 属另一张 native→std 表，不受文法收窄影响，已核实无冲突） |

**收窄 BC 面复核**（执行时重申 plan Current Baseline 的 grep 结论）：7 个含 `<eql>` 的 `sql-lib.xml` 语料 `cast(` 零命中；114 个 orm.xml 不经此文法；全仓资源无 `stdSqlType|returnType|argTypes|stdType` 取删除项拼写的命中（2026-10-04 重跑 0）。⇒ 删除 14 备选对既有模型与语料零影响。

## 六、裁定 4：测试矩阵载体表（逐行含载体方言 + 期望渲染文本，`stdToNativeSqlType` 链路推导）

测试位置：`nop-persistence/nop-orm-eql`（parse 层 + compile 层，WI2 `TestEqlCastBooleanParse`/`TestEqlCastBooleanCompile` 先例；真方言经 `DialectManager` + `CoreInitialization`，`TestH2WindowFrameCompileEndToEnd` 先例；**禁用 es 伪方言**；db2 按本裁定 §2.1 不入载体）。

| 类型（别名→目标） | "支持"载体 | 期望渲染文本（链路推导，非直觉） | "不支持"载体 | 期望行为（错误码字面值） |
|---|---|---|---|---|
| `text`→CLOB | h2 | `cast(o.SID as CLOB)` | mssql（真方言，无 CLOB 映射）+ 手工最小方言 | 解析过、生成期 `nop.err.dao.dialect.std-data-type-not-supported`（`ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED`） |
| `clob`（对照，既有拼写） | h2 | `cast(o.SID as CLOB)` ——与 `text` 同方言同输出（**约束 4 一致性**用例） | | |
| `text` 跨方言（**约束 4 不等价**） | postgresql | `cast(o.SID as TEXT)`（pg std CLOB 首选条目是 native TEXT）——h2 `CLOB` vs pg `TEXT` 渲染不同 | | |
| `number`→DECIMAL | h2 | `cast(o.SID as NUMERIC)`（**不是 DECIMAL**——h2 std DECIMAL 唯一条目 native NUMERIC，plan 明示警示） | 手工最小方言 | 同上错误码 |
| `number` 跨方言（不等价） | oracle | `cast(o.SID as NUMBER)`——h2 `NUMERIC` vs oracle `NUMBER` | | |
| `decimal`（对照） | h2 | `cast(o.SID as NUMERIC)`——与 `number` 同输出（一致性用例） | | |
| `bool`→BOOLEAN | h2 | `cast(o.SID as BOOLEAN)`（WI2 用例口径扩展） | 手工最小方言（WI2 先例复用） | 同上错误码 |
| `boolean`（对照） | h2 | 同输出（一致性用例） | | |
| `uuid`（枚举常量） | h2 / postgresql / duckdb | `cast(o.SID as UUID)`（三方言同渲染） | mysql + oracle（真方言，无原生 UUID/无映射）+ 手工最小方言 | 同上错误码 |
| `uuid` 跨方言（不等价） | mssql | `cast(o.SID as UNIQUEIDENTIFIER)`（code 属性渲染）——支持面差异断言 | | |

**接线验证（Minimum Rules #23）落点**：正向文本用例全部经 `EqlCompiler.compile` 端到端（闸门 A `SqlTypeExpr_name` 放行 → `AstToSqlGenerator.visitSqlCastExpr` 反查 `fromStdName` → `dialect.stdToNativeSqlType` 渲染）——即"别名从 EQL 文本 → 闸门 A → 生成器 → 方言文本"的运行时连通证明，不是 `fromStdName` 单元返回值断言。**闸门 B** 按 plan 给定的替代口径：以"两闸门共调 `fromStdName` 的代码事实（§2.3 锚点）+ 0 命中统计（§一）"登记替代 orm/xdef 域校验用例（闸门 B 的放行是 `stdNameMap` 键集的纯函数，无独立分支可测）。

**非法类型守护**：`cast(x as nosuchtype)` 仍响亮报 `nop.err.eql.invalid-sql-type` 且 `ARG_ALLOWED_NAMES` 含新别名（`TestEqlCastBooleanParse:69-78` 存活 + 新增键集包含断言）。

## 七、裁定 5：各 Phase 提交切分

**按 plan Protected Areas 表默认执行**（无新增裁定）：

1. `nop-kernel/nop-commons/src/main/java/io/nop/commons/type/StdSqlType.java`（3 别名 put + UUID 枚举）+ 不依赖方言 XML 的测试 → **单独提交**（G-3 回滚单位）。
2. 4 个方言 XML 的 UUID 条目 → **每文件一条独立提交**（h2/postgresql/duckdb/mssql），各携带该方言的正向用例（每条自洽可绿、可单独 revert）。
3. `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` 删 14 备选）+ 重生成产物（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java`/`Eql.interp` 等）→ **单独提交**；删备选不删 token，无重编号。
4. owner docs / 裁定文档 / roadmap 注记 / plan 勾选 / daily log → 文档提交，与代码提交分离。

**重生成 diff 审计口径**（Phase 2 用）：仅 ATN/解析逻辑变化（`EqlParser.java`、`Eql.interp`），token 表零增删（`EqlLexer.java`/`Eql.tokens` 预期零 diff——若非零 diff 须逐 token 解释）、`EqlAST.xjava`/`ast/_gen` 零 diff、`_EqlASTBuildVisitor.java` 预期零 diff（不新增 AST 节点/字段）。变为无 parser 引用的 token（`NATIONAL`/`NCHAR`/`BIT`/`DEC`/`VARYING`/`PRECISION`/`DOUBLE`/`CHARACTER`——`CHARACTER` 仍被 `characterSet_` 引用，`INTERVAL` 仍被 `sqlIntervalExpr`/`sqlIdentifier_func_` 引用，实际失引用的是 `NATIONAL`/`NCHAR`/`BIT`/`BIT VARYING` 的 `VARYING`/`DEC`/`PRECISION`/`DOUBLE` 与 `CHARACTER VARYING` 组合）登记进提交说明；词表保留、无重编号。

## 八、WI9b 边界确认（防 scope creep）

- 本 plan 不取消 `StdSqlType.java:36-43` 的时区类型注释、不引入 `TIME/TIMESTAMP WITH LOCAL TIME ZONE` 拼写、不做时区 DDL 迁移评估。
- 删除的 `TIME WITH TIME ZONE`/`TIMESTAMP WITH TIME ZONE`（非 LOCAL）与 WI9b 将引入的 `… WITH LOCAL TIME ZONE` 是两个名字集，互不替。
- 方言 XML 的 native 名 `TIMESTAMP WITH TIME ZONE`（duckdb `:57`、mysql 别名、h2 `TIMESTAMP` 条目 alias）属 native→std 输入解析表，**不在** `dataTypeName_` 文法面上，收窄不触碰、无需改动。

## 九、附录：`StdSqlType.getNames()` 键集变化登记（plan 差异登记第 4 条）

- 现值（30 键）：25 个枚举名 + `INT` 别名（`StdSqlType.java:152`）。
- Phase 1 后（34 键）：+ `TEXT`、`NUMBER`、`BOOL`（别名 put）+ `UUID`（枚举名自动入表）。
- 消费面：闸门 A 错误 param `ARG_ALLOWED_NAMES = StdSqlType.getNames()`（`EqlASTBuildVisitor.java:474`）与闸门 B 错误 param（`SimpleStdDomainHandlers`）的提示集合同步变大——预期行为，非缺陷；`getNames()` 返回不可变视图（`Collections.unmodifiableSet`），无外部可变性风险。
