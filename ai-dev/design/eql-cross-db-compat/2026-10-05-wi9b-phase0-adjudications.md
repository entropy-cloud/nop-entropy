# EQL 跨数据库兼容性：WI9b Phase 0 前置裁定

**日期**：2026-10-05
**范围**：`ai-dev/plans/32-eql-wi9b-tz-types.md`（plan Status: active）Phase 0 的 7 项裁定；涉及 `nop-kernel/nop-commons`（`StdSqlType`）、`nop-persistence/nop-dao`（方言 XML `<sqlDataType>` 新增条目）、`nop-persistence/nop-orm-eql`（`nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4` 的 `dataTypeName_` 新增 + 手写闸门 A 的名称提取适配）
**状态**：active —— 全部 7 项已裁定；代码未动（本 Phase 纯裁定，零产品代码变更）
**授权模式**：同 WI9a UUID 形态（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md` 授权模式节）——平台负责人（用户）经 2026-10-05 对 WI9b plan（plan 32）的执行指令授权、AI 基于证据链代拟、**人可推翻**；推翻时按本文件各裁定项的"回滚方式"撤销，并在本目录追加修订裁决。其中时区成员落活触及 `nop-kernel/nop-commons` 模型层行为变更（`stdNameMap` 同时驱动 DDL 类型映射与 8 个 xdef 的 `std-sql-type` 域合法取值闸门），按 roadmap 门控矩阵 WI9b 行（`:115`）属「AI Must Ask Before」第 1 条 **ask-first**，**不得以 plan-first 之名替代询问**——其授权即本注记所述的执行指令授权，见 §三.4。`.g4` 侧的生成管线 plan-first 是独立成立的第二条门禁（本 plan 即其载体，draft review 已于 2026-10-04 通过：0 Blocker）。

---

## 〇、开工前置复核（2026-10-05 实测）

| 复核项 | 结论 | 证据 |
|---|---|---|
| WI0 产物 1：语料基线快照 | **未回退**。`TestEqlCorpusBaseline` 1/1 PASS（surefire 报告 `Tests run: 1, Failures: 0`，mtime 2026-10-04 18:46；HEAD `8be89b0bc8`（2026-10-04 18:54）为**纯文档提交**（4 个 ai-dev/docs 文件，`git diff --stat` 实证零代码 delta），最后一次代码提交 `cb918dd3cc`（WI8 Phase 3）早于报告 mtime ⇒ 报告反映当前代码态）。Phase 3 终局门按组合判据复跑 | `nop-persistence/nop-orm-eql/target/surefire-reports/io.nop.orm.eql.parse.TestEqlCorpusBaseline.txt` + git diff 实测 |
| WI0 产物 2：方言矩阵快照门禁 | **未回退**。`node ai-dev/tools/check-eql-dialect-matrix-snapshot.mjs` exit 0（18 个 dialect XML、18 注册、15 可加载、3 不可加载） | 门禁输出与本节记录 |
| 硬前置 plan 状态 | WI9a `completed`（closure 2026-10-04；提交链 `5154661e75`/`41bfbcba56` 经 `git cat-file -t` 实证在 history）、WI0/WI2 `completed`、G-1/G-3 已裁决（2026-10-03）——与 plan Current Baseline 一致，无排队门 | `git log` + 各 plan 文件 |
| roadmap WI9b 行状态 | `planned`（`:92`，执行前流转已发生）——依赖图 `WI9a --> WI9b` 已解锁 | roadmap Work Item Status |
| **并行 `.g4` 重生成债核验（plan Phase 2 开工前置项提前执行）** | **债已清零**：WI7b 的重生成产物已随 `7048cf1474` 提交（commit message 明示"parser/ast 全量重生成"）；WI8 两个文法提交（`777e2ec494`/`a2dab64b70`）各自携带重生成产物；当前工作区 `git status` 无任何 `.g4`/`EqlParser.java` 未提交改动（仅与本 plan 无关的 ooxml sample 与未跟踪目录）。⇒ Phase 2 的重生成 diff 审计不会把并行欠账误记为本 plan 改动 | `git log -- <.g4>` + `git status` 实测 |
| 锚点符号复核 | plan 所引符号全部存在；**行号漂移两处**：(1) roadmap 文本行号整体下移约 17 行（WI8 收口注记并入所致——WI9b 节实测 `:344-349`、门控矩阵 WI9b 行仍 `:115`、Cross-Cutting `:397-407`、EC `:461-473`、红线 `:475-486`；内容与 plan 引用一致）；(2) 方言 XML 时区条目行号因 WI9a UUID 条目并入而下移：h2 `TIMESTAMP WITH TIME ZONE` alias 条目现 `:285`（plan 记 `:238`）、duckdb 现 `:67`（plan 记 `:61`）、mysql 现 `:224`（plan 记 `:196`）、oracle LOCAL alias 现 `:283`（plan 记 `:249`）。闸门 A/B、三 map、`dataTypeName_` 等其余锚点行号与 plan 一致。执行一律以符号定位为准 | 逐符号 grep 实测 |

## 一、使用面 0 命中重跑（三口径，EC ② 证据基座；2026-10-05 实测）

| 统计项 | 起草时点（2026-10-04） | **2026-10-05 重跑（本裁定）** |
|---|---|---|
| 口径 ①：`git ls-files '*.orm.xml'` 总数 / 其中 `stdSqlType` 取时区值命中 | 114 / 0 | **114 / 0** |
| 口径 ②：全仓 git 追踪 `*.xdef`/`*.xmeta`/`*.xml` 中 `TIME_WITH_LOCAL_TIME_ZONE`/`TIMESTAMP_WITH_LOCAL_TIME_ZONE` 命中 | 0 | **0** |
| 口径 ② 扩展：全仓 `WITH LOCAL TIME ZONE`（带空格拼写）命中 | ——（起草未单列） | **9 个文件，全部为文档/注释/native 别名，零模型使用**：7 个 `ai-dev/` 与 `docs-for-ai/` 文档、BaseRule.g4 的 WI9a 边界注释、oracle.dialect.xml `:283` 的既有 native alias `TIMESTAMP(6) WITH LOCAL TIME ZONE`（native→std 方向，与本裁定 §六不冲突）、TestEqlCastTypeNarrowingParse javadoc |
| 口径 ③：Java 生产代码引用 | 仅 `StdSqlType.java` 注释本体 | **仅 `StdSqlType.java:38` 注释本体**（`git grep -i -- '*.java'` 排除 test 后唯一命中） |

**结论：影响清单 = 空清单（预期实证成立）**。EC ② 迁移裁定的证据基座成立；若后续 Phase 执行中重跑非 0，按 plan 纪律回到 ask-first 升级裁决。

## 二、映射裁定取证（H2 一手探测 + Oracle 一手文档 + 既有键集撞名核对）

### 2.1 H2 一手探测（替换 plan Current Baseline 的 `[未取证]` 标注——硬项）

测试载体 = 独立 JDBC 探针（`_tmp/wi9b/Wi9bH2Probe.java`， transcript `_tmp/wi9b/h2-probe-2.4.240.log`），H2 版本 = **2.4.240**（`./mvnw dependency:list -pl nop-persistence/nop-db-migration` 实测解析结果，spring-boot-dependencies BOM 托管），in-memory 建表：

| DDL 形态 | 建表结论 | JDBC 元数据读回 | `getObject` 值类型 |
|---|---|---|---|
| `TIMESTAMP WITH TIME ZONE` | **可建** | `TYPE_NAME="TIMESTAMP WITH TIME ZONE"`、`DATA_TYPE=2014`（`Types.TIMESTAMP_WITH_TIMEZONE`） | `java.time.OffsetDateTime` |
| `TIME WITH TIME ZONE` | **可建** | `TYPE_NAME="TIME WITH TIME ZONE"`、`DATA_TYPE=2013`（`Types.TIME_WITH_TIMEZONE`） | `java.time.OffsetTime` |
| `TIMESTAMP WITH LOCAL TIME ZONE` | **不可建**——语法错误（解析器期望 `WITH TIME ZONE`/`WITHOUT TIME ZONE`，无 LOCAL 分支） | —— | —— |
| `TIME WITH LOCAL TIME ZONE` | **不可建**——同上 | —— | —— |

**结论**：H2 2.x **已移除** LOCAL 时区类型（1.4.x 存在的历史类型，2.x 重构日期时间类型时删除）。两种 LOCAL 拼写在 CI 唯一真库（H2）上**无建表载体**，plan Current Baseline 的 H2 支持度 `[未取证]` 标注**已消除并改判为不可建**（一手证据等级）。按 plan 预授权，EC ① 按 WI5 EC ⑥ 同款"逐个列出不可验证方言 + 替代证据 + `[未取证]`"纪律收口，替代证据形态见 §六.4。

**附带一手事实（对裁定 1 的决定性输入）**：H2 把**非 LOCAL** 时区类型的 JDBC `DATA_TYPE` 报为 2013/2014。即 JDBC 4.2 的 `Types.TIME_WITH_TIMEZONE`/`TIMESTAMP_WITH_TIMEZONE` 两个标准码在真实驱动上表示的是 **WITH TIME ZONE（保偏移）** 类型，**不是** LOCAL 类型。

### 2.2 Oracle 一手文档（`TIMESTAMP WITH LOCAL TIME ZONE` 的原生类型依据）

Oracle 23ai SQL Language Reference（docs.oracle.com，`Data-Types.html`，2026-10-05 取证）：

> "TIMESTAMP [(fractional_seconds_precision)] **WITH LOCAL TIME ZONE** — All values of TIMESTAMP WITH TIME ZONE, with the following exceptions: Data is normalized to the database time zone when it is stored in the database. When the data is retrieved, users see the data in the session time zone."（fractional seconds precision defaults to 6, range 0–9）

同页明确 Oracle 的 datetime 类型族 = **DATE, TIMESTAMP, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH LOCAL TIME ZONE**——**无任何 TIME-only 类型**。⇒ 两个一手结论：(1) Oracle 有原生 `TIMESTAMP WITH LOCAL TIME ZONE`；(2) `TIME WITH LOCAL TIME ZONE` 在 Oracle **无原生宿主**。

### 2.3 其余可加载方言的 LOCAL 类型宿主核查（支撑 §六 按方言列表）

| 方言 | `TIMESTAMP WITH LOCAL TIME ZONE` 宿主 | `TIME WITH LOCAL TIME ZONE` 宿主 | 依据等级 |
|---|---|---|---|
| h2 | **无**（2.4.240 实测不可建，§2.1） | 无 | `[实测]` |
| oracle | **有**（原生类型） | 无（Oracle 无 TIME-only 类型，§2.2） | `[文档]`（一手） |
| postgresql / postgis | 无（只有 `timestamp with [out] time zone`，无 LOCAL 变体） | 无 | `[文档]`（PG 文档类型页无 LOCAL；本 plan 不新增未引用 URL，按"无 LOCAL 关键字"事实登记） |
| duckdb | 无（仅 `TIMESTAMP WITH TIME ZONE`，dialect XML `:67` 侧证 + duckdb docs 无 LOCAL） | 无 | `[文档]` |
| mysql / mariadb / mysql5.7 | 无 | 无 | `[文档]` |
| mssql | 无（`datetimeoffset` 是另一类型；无 LOCAL 拼写） | 无 | `[文档]` |
| dm / tdengine / db2 | 未取证（红线 `:482`：无可引用一手文档，一律 `unverified`，**不计入覆盖率**） | 同左 | `unverified` |

（`h2gis` 继承 h2、`oracle-reverse` 继承 oracle、`es` 为伪方言不入矩阵。）⇒ **`TIME WITH LOCAL TIME ZONE` 在全部已取证方言上无原生宿主，支持方言空集**；`TIMESTAMP WITH LOCAL TIME ZONE` 唯一原生宿主 = Oracle。

### 2.4 既有 `nativeTypes` 键集撞名核对（`SqlDataTypeMapping.register` last-wins 硬约束的核对记录）

`register`（`SqlDataTypeMapping.java:49-62`）以 native `name` + 全部 `alias`（`toUpperCase`）为键，同名 put **静默 last-wins 覆盖**。新条目键 `"TIMESTAMP WITH LOCAL TIME ZONE"`（裸名）与 oracle 既有键集逐一核对（`grep -o 'name=|alias=' oracle.dialect.xml` 全量枚举实测）：oracle 既有键 = {TIMESTAMP, TIMESTAMP(6), **TIMESTAMP(6) WITH LOCAL TIME ZONE**（alias，带 precision 限定）, VARCHAR2, VARCHAR, NVARCHAR2, CLOB, NCLOB, DATE, DATETIME, NUMBER, INT, INTEGER, SMALLINT, BIGINT, CHAR, NCHAR, BINARY_FLOAT, BINARY_DOUBLE, DOUBLE PRECISION, RAW, BLOB, BOOLEAN, JSON}——**裸名不在其中，零撞名**。其余 17 个 dialect XML 全量 grep 无任何裸 `TIMESTAMP WITH LOCAL TIME ZONE` 键。⇒ 新增条目为**纯新增键**，不改写任何既有键的值（候选 (a) 的可行性条件成立）。

## 三、裁定 1：时区成员形态——`Types.OTHER` + `StdDataType.TIME`/`TIMESTAMP` + 循环后三处显式重钉（偏离候选 (a) 的字面倾向，含新增证据）

**结论**：

```text
TIME_WITH_LOCAL_TIME_ZONE(false, false, Types.OTHER, StdDataType.TIME)
TIMESTAMP_WITH_LOCAL_TIME_ZONE(false, false, Types.OTHER, StdDataType.TIMESTAMP)
```

声明位置 = 现注释区（`:38-39` / `:42-43` 替换为活成员），保持 `TIME → TIME_WITH_LOCAL_TIME_ZONE → TIMESTAMP → TIMESTAMP_WITH_LOCAL_TIME_ZONE` 相邻声明序（plan Phase 1 第 1 项要求）。**三 map 不变量保持策略 = 循环后显式重钉三行**（既有重钉先例 `StdSqlType.java:179-180` 的 STRING/BYTES 同款形态，紧随其后追加）：

```java
jdbcTypeMap.put(Types.OTHER, StdSqlType.OTHER);   // 先到先得键：两个新成员声明序先于 OTHER
stdTypeMap.put(StdDataType.TIME, StdSqlType.TIME);       // last-wins 键：新成员晚于 TIME 声明
stdTypeMap.put(StdDataType.TIMESTAMP, StdSqlType.TIMESTAMP); // 同上
```

### 3.1 `jdbcType`：**候选 (b) 补偿形态（`Types.OTHER` + 重钉）**，**拒绝候选 (a)（2013/2014）**

| 候选 | 裁定 | 理由 |
|---|---|---|
| (a) `Types.TIME_WITH_TIMEZONE=2013` / `Types.TIMESTAMP_WITH_TIMEZONE=2014` | **拒绝** | 起草时点评估"键唯一零翻转"在 **map 层**成立，但 §2.1 一手探测揭示其**读回路径语义误标**：2013/2014 在真实驱动（H2 实测）上表示的是 **WITH TIME ZONE（保偏移）** 类型，不是 LOCAL 类型——把标准码挂到 LOCAL 成员上，`fromJdbcType(2013/2014)` 将从 `null` 变为返回语义不符的 LOCAL 成员，活消费方 `JdbcHelper.java:164`（读回路径）与 `JdbcMetaDiscovery.java:346`（逆向建模兜底）会把非 LOCAL 时区列静默归类为 LOCAL 成员（正是本 plan 要避免的"静默归类改变"，只是方向在 native→std 读回侧而非 plan 影响面 (a)-(d) 已列各条——plan 影响面 (d)"std 归类不受影响"的断言只在**本候选被拒**的前提下才成立）。同时把 2013/2014 烧给 LOCAL 成员，未来若非 LOCAL 成员入库（Deferred 段范围）将无标准码可用 |
| (b) `Types.OTHER` + `jdbcTypeMap` 循环后重钉 `OTHER` | **采纳** | 与注释体原始作者选择一致（`Types.OTHER`）；零可观测行为变化——`fromJdbcType(Types.OTHER)=OTHER` 经重钉保持、`fromJdbcType(2013/2014)` 保持 `null`（今日读回行为原样保留，H2 时区列的归类路径维持 name-keyed 既有条目 + 空兜底）；LOCAL 类型在 JDBC 无标准码，`OTHER` 是诚实的"无标准映射"表达；`getJdbcType()` 的唯一消费方 `IDataParameterBinder.getJdbcType()`（参数绑定 setNull 等）对 `OTHER` 有既有成员同款通路。重钉行把 `OTHER` 键从"循环 first-wins"显式化，机制先例 = `stdTypeMap` 的 STRING/BYTES 重钉（`:179-180`），非新机制 |

### 3.2 `stdDataType`：`TIME` / `TIMESTAMP`（归族），重钉保持；**拒绝"新增 StdDataType 成员"**

- `TIME_WITH_LOCAL_TIME_ZONE → StdDataType.TIME`：LOCAL 语义 = 无偏移存储、会话时区渲染，值域与 `LocalTime` 同族（`StdDataType.TIME(15, LocalTime.class)`）；convert/binder 通路复用 TIME 家族，无新增分支（WI9a UUID 的 STRING-家族复用先例同构）。
- `TIMESTAMP_WITH_LOCAL_TIME_ZONE → StdDataType.TIMESTAMP`：值域与 `Timestamp` 同族（`StdDataType.TIMESTAMP(14, Timestamp.class)`）。**不取 `DATETIME`**（plan 列出的第三候选）：DATETIME 的 `LocalDateTime` 语义偏"无时区戳"，TIMESTAMP 家族才是 JDBC 时间戳通路（且 plan 影响面 (b) 点名的翻转面 = TIME/TIMESTAMP 两键，重钉两行即可覆盖，不引入第三个键的翻转）。
- **被拒候选——新增 `StdDataType` 成员**（如 `TIME_WITH_LOCAL_TZ`）：supernatural scope（plan Non-Goals 明示不新增 StdDataType 成员，除非负责人推翻）；新成员会同时改 `StdDataType.fromJavaClass` 映射面、`StdDataType` ordinal 相邻关系（`isDateType` 等区间判断 `:198`）与参数 binder 注册面，成本与收益不成比例。人可推翻；推翻 = scope change，须显式裁决 + 补 binder/convert 通路评估。

### 3.3 `allowPrecision` / `allowScale`：`(false, false)`（同族先例优先）

- 同族成员 `TIME`/`TIMESTAMP` 均为 `(false, false)`（`StdSqlType.java:32`/`:40`）；时区成员是它们的直系变体。
- 注释体 `PrecScale.NO_NO | PrecScale.YES_NO` 的语义线索（precision 可选）**登记为被拒线索**：(i) 文法面裁定 bare 形态（§五），precision 在多词备选下不可达（`sqlTypeExpr` 的 `(precision[,scale])` 只紧跟 `dataTypeName_` 整体，plan 已登记的 precision 位置限制），`allowPrecision=true` 无可观测通路；(ii) `buildSQLDataType`（`SqlDataTypeMapping.java:121`）以 `sqlType.isAllowPrecision()` 门 precision 渲染，`(false,false)` 使渲染确定性收敛为裸名；(iii) 逆向侧 `JdbcMetaDiscovery.java:370-373` 的 precision/scale 回填随之关闭——与"bare 类型无精度语义"一致。
- 消费面核验：`isAllowPrecision/isAllowScale` 全部消费方 = `SQLDataType.toString`、`SqlDataTypeMapping.buildSQLDataType`、`JdbcMetaDiscovery:370-373`（2026-10-05 grep 实测，共三处），均随 `(false,false)` 收敛，无遗漏消费方。

### 3.4 三 map 不变量核验基线（2026-10-05 实测，`_tmp/wi9b/stdnames-probe.log`；Phase 1 聚焦测试的"修复前"锚）

| 不变量 | 修复前（实测值） | Phase 1 后要求 |
|---|---|---|
| `getNames()` | **SIZE=30**（26 活成员 + `INT`/`TEXT`/`NUMBER`/`BOOL` 四别名，无键冲突——全键清单见 §九附录） | **32**（+2 枚举名，经 `values()` 循环自动入表） |
| `fromStdName("TIME_WITH_LOCAL_TIME_ZONE")` | `null`（红锚） | 返回 `TIME_WITH_LOCAL_TIME_ZONE` |
| `fromStdName("TIMESTAMP_WITH_LOCAL_TIME_ZONE")` | `null`（红锚） | 返回 `TIMESTAMP_WITH_LOCAL_TIME_ZONE` |
| `fromJdbcType(Types.OTHER)` | `OTHER` | `OTHER`（重钉保持） |
| `fromJdbcType(Types.TIME_WITH_TIMEZONE=2013)` | `null` | `null`（保持——候选 (a) 被拒的直接结果） |
| `fromJdbcType(Types.TIMESTAMP_WITH_TIMEZONE=2014)` | `null` | `null`（保持） |
| `fromStdDataTYpe(TIME/TIMESTAMP/DATETIME)` | `TIME`/`TIMESTAMP`/`DATETIME` | 不变（重钉保持） |
| `fromJavaClass(LocalTime/LocalDateTime/Timestamp)` | `TIME`/`DATETIME`/`TIMESTAMP` | 不变（`fromJavaClass` 经 `StdDataType` 归类，不受影响；聚焦测试钉死） |
| `fromStdName` 既有 30 键 | 全部正确 | 不变（含 `INT`/`TEXT`/`NUMBER`/`BOOL`/`UUID` 别名与枚举键） |

`values()` 序依赖核验：全部消费方 = `stdNameMap/jdbcTypeMap/stdTypeMap` 三 map（序敏感，已由重钉策略覆盖）+ `SingleColumnExprMeta.s_meta`（HashMap 按常量键，序无关，`SingleColumnExprMeta.java:63-70`）+ `ExprOrmDataType.s_map`（EnumMap 按常量键，类加载重建，序无关安全，`ExprOrmDataType.java:34-39`）。中部插入（`:38/:42` 区）不移动任何既有成员的相对序，无持久化 ordinal 消费方（grep 实测）。闸门 A/B 错误消息 `ARG_ALLOWED_NAMES` 随 30→32 扩集——预期行为（WI9a 差异登记第 4 条同款）。

### 3.5 裁决人注记（ask-first，人可推翻）

`StdSqlType` 位于 `nop-kernel/nop-commons`，本裁定落两个活枚举成员 + 三 map 重钉，属**模型层行为变更**（`stdNameMap` 双重角色：DDL 类型映射 + 8 个 xdef 的 `std-sql-type` 域合法取值闸门，`SimpleStdDomainHandlers.java:1608/:1637` 实证）。按 roadmap 门控矩阵 WI9b 行（`:115`）走「AI Must Ask Before」第 1 条 **ask-first**——`nop-commons` 不是 `nop-*-api`，非 AGENTS.md 字面 Protected Area，亦不得以本 plan 自加的 plan-first（生成管线门）替代询问。**本项由平台负责人（用户）经 2026-10-05 对 plan 32 的执行指令授权（该指令明示"执行 plan 32"并载明 Phase 0 裁定项与 ask-first 形态）、AI 基于上述证据链代拟、人可推翻。** 推翻后果：`git revert` Phase 1 单独提交即可完全回滚（`stdSqlType` 时区值现存 0 命中，§一实测——无数据/模型依赖）；若推翻改采候选 (a)（2013/2014），须同步复核 §三.1 拒绝理由表中 `JdbcHelper`/`JdbcMetaDiscovery` 读回路径的新归类行为并补对应测试预期。

## 四、裁定 2：EC ② 迁移结论——`No migration required`

**结论**：`No migration required`。理由三段，**不留空**：

1. **0 使用实证**（§一三口径重跑）：全仓 114 个 orm.xml、全部 xdef/xmeta/资源 XML、全部 Java 生产代码对两个时区 std 类型**零引用**——取消注释/落活改变的是"未来可写什么"，不改变"既有部署已写了什么"。
2. **影响面 (a)-(d) 逐条**（plan Current Baseline"迁移影响面"清单）：
   - (a) `getNames()` 30→32 扩集：闸门 A/B 错误消息与合法取值集纯增量，无既有取值被拒；
   - (b) `fromJdbcType`/`fromStdDataTYpe` 潜在静默翻转：**已被裁定 1 的三处重钉消除**（零翻转）；
   - (c) migration.xml 的 `type`/`newType` 属性（`migration.xdef:166`/`:287`，`!std-sql-type` 域）合法取值集扩大：0 使用 ⇒ 纯增量（实测 `git grep` 无 migration.xml 引用时区类型）；
   - (d) 逆向建模路径（`SchemaToOrmModel.java:97`、`JdbcHelper.java:164`、`JdbcMetaDiscovery.java:346`）的 native→std 归类：**name-keyed 既有条目零改动**（§六只新增 oracle 裸名键，不改写既有键）；code-keyed 兜底路径因候选 (a) 被拒而保持 `fromJdbcType(2013/2014)=null`（原行为）。
3. **升级指引**（owner doc 登记义务）：若既有部署**自行**用了非标准手段（如以 `VARCHAR` 存时区数据），本变更不强制迁移；新部署要使用时区 std 类型时，`TIMESTAMP_WITH_LOCAL_TIME_ZONE` 仅在有原生宿主的方言（当前=oracle，§六）可生成 DDL，其余方言生成期响亮报 `nop.err.dao.dialect.std-data-type-not-supported`——该指引一句随 Phase 3 落 `orm-model-design.md`。

**owner doc 登记**：本结论 + 升级指引一句归 Phase 3 的 `orm-model-design.md` 类型面行（plan Phase 3 第 6 项），本 Phase 不改 owner doc。

## 五、裁定 3：拼写与文法形态（含闸门 A 名称提取适配——新增裁定项）

### 5.1 标准拼写名与文法备选形态

- **标准拼写** = `TIME WITH LOCAL TIME ZONE` / `TIMESTAMP WITH LOCAL TIME ZONE`（Standard SQL:1999 名称；大小写不敏感——闸门 A 对输入 `toUpperCase(Locale.ROOT)` 后匹配枚举名，§5.3 的适配保持这一性质）。
- **文法备选形态 = bare 多词备选**（在 `dataTypeName_` 显式备选区新增 `| TIME WITH LOCAL TIME ZONE` 与 `| TIMESTAMP WITH LOCAL TIME ZONE` 两序列备选，置于 `sqlIdentifier_` 兜底之前）。**接受 `sqlTypeExpr` 的 precision 位置限制并登记为已知能力边界**：`cast(x as timestamp(6) with local time zone)` 不在文法接受面（WI9a 删除前的非 LOCAL 备选同限；plan Deferred 段已登记 `watch-only residual`）。裁定不扩展文法形态（成本上浮无对应需求——precision 0 使用实证 §一口径覆盖）。
- 词法可行性（零 token 增删预期）：`WITH`/`LOCAL`/`ZONE`/`TIME`/`TIMESTAMP` 均既有 token（plan Current Baseline 实测，Phase 2 重生成 diff 审计复核）。
- **下划线拼写自动可用**：`time_with_local_time_zone`（单 token `IDENTIFIER_`）经 `sqlIdentifier_` 兜底 + 闸门 A 直查在 Phase 1 落活后即解析通过——与新增多词备选互不影响；穷举式用例（`TestEqlCastTypeNarrowingParse.testEveryRegisteredStdNameGrammarAcceptableOrExplicitlyUnspellable`）动态迭代 `getNames()`，键集 30→32 自动扩并验证下划线拼写路径。

### 5.2 非 LOCAL 变体不恢复——终裁

**终裁 = 不恢复** `TIME WITH TIME ZONE` / `TIMESTAMP WITH TIME ZONE` 的文法备选（与 plan 倾向一致）。理由：(1) `StdSqlType` 从未有这两个非 LOCAL 成员（注释态也没有，2026-10-05 复核），恢复文法备选即复活 BUG-11"解析可过 + 闸门 A 必拒"死备选，直接违背 WI9a 刚收口的收窄目标；(2) 其标准 JDBC 码 2013/2014 归属问题（§三.1）进一步表明非 LOCAL 成员化属独立 scope（plan Deferred 段 successor 路径不变）。**本终裁为本 plan 的 Deferred 段第一项的正式裁定记录**；人可推翻——推翻 = scope change，须补枚举成员 + 补映射评估 + 修订 Deferred 段。

### 5.3 闸门 A 名称提取适配（执行期新增裁定项——plan 未预见的机制缺口）

**发现（2026-10-05 实测）**：手写闸门 A `EqlASTBuildVisitor.SqlTypeExpr_name`（`EqlASTBuildVisitor.java:683-689`）以 `text(node).toUpperCase(Locale.ROOT)` 提取名称，而 `text` = `ParseTreeHelper.text` = `node.getText()`——ANTLR 的 `RuleContext.getText()` **将子 token 文本无分隔符拼接**（空白 token 被 lexer 跳过）。⇒ 仅加文法备选而不适配闸门 A，`cast(x as time with local time zone)` 会解析通过但以拼接名 `"TIMEWITHLOCALTIMEZONE"` 进入 `fromStdName` 必被拒——**恰是 WI9a 刚消灭的 BUG-11"文法放行 + 语义必拒"死备选形态**。plan Phase 2 的端到端绿锚（Goals 第 2 条"解析 → AST → 方言 native 文本全链可用"）因此要求名称提取适配。

**裁定**：修改**手写** `EqlASTBuildVisitor.SqlTypeExpr_name`（`parse/` 层手写文件，WI5/WI7b/WI8 多次修改先例，非生成物、非 Protected Area）：当 `dataTypeName_` 上下文跨多 token 时，从 token span 重建含空白原文（`ctx.getStart()/getStop()` + `getInputStream().getText(Interval)`），`trim` → `toUpperCase(Locale.ROOT)` → 空白串替换为 `_` 后查表；单 token 路径（现有全部键）行为逐字节不变。归一化结果 = 枚举名 `TIME_WITH_LOCAL_TIME_ZONE`/`TIMESTAMP_WITH_LOCAL_TIME_ZONE`，**AST `SqlTypeExpr.name` 存枚举名**（生成器 `AstToSqlGenerator.visitSqlCastExpr:713` 以 `fromStdName(expr.getName())` 反查——以归一化名入库是既有契约）。

**被拒备选**：(i) 在 `stdNameMap` 增设带空格键（`"TIME WITH LOCAL TIME ZONE"`）——会连带扩闸门 B 的 orm.xml 属性接受集与 `ARG_ALLOWED_NAMES` 错误消息，属性面拼写应收敛为枚举名，拒绝；(ii) 拼接名白名单（`"TIMEWITHLOCALTIMEZONE"→成员`）硬编码——脆弱且无一般性，拒绝；(iii) 仅靠下划线拼写（不加多词备选）——违背 roadmap WI9b"同步语法拼写"的字面交付与本 plan Goals 第 2 条，拒绝。

**回滚方式**：`.g4` + 重生成 + 闸门 A 适配同属 Phase 2 单独提交，整体 `git revert`（Phase 1 的枚举成员不依赖闸门 A 形态）。

### 5.4 对照表定稿列结构（Phase 2 实测终稿的承载结构）

列结构（plan Phase 0 第 5 项定稿要求）：`新增拼写 | 修复前行为（错误码字面值） | 修复后行为（错误码字面值） | 等价或受边界限制的拼写形态 → 生成什么 SQL（载体方言文本） | 备注`。

草表（Phase 2 实测后终稿化；修复前行为按当前 live 形态）：

| 新增拼写 | 修复前行为 | 修复后行为 | 等价/受限拼写 → 生成 SQL | 备注 |
|---|---|---|---|---|
| `time with local time zone` | 解析失败，`nop.err.antlr.*` 族（`sqlIdentifier_` 单词兜底承载不了多词；Phase 2 红锚实测具体码与 offending token） | 解析过 → 闸门 A 放行 → oracle 渲染 `TIME WITH LOCAL TIME ZONE`（宿主方言）；无宿主方言（含 h2）生成期响亮报 `nop.err.dao.dialect.std-data-type-not-supported` | `time_with_local_time_zone`（下划线，单 token）→ 同一渲染链；`time with local time zone(6)` / `time(6) with local time zone` → 解析失败（precision 位置限制，Deferred 登记） | **该 std 类型支持方言空集**（§2.3）——h2/oracle 均为负向载体，无正向渲染方言 |
| `timestamp with local time zone` | 同上 | 解析过 → oracle 渲染 `TIMESTAMP WITH LOCAL TIME ZONE`；h2 等无宿主方言响亮报同错误码 | `timestamp_with_local_time_zone` → 同链；`timestamp(6) with local time zone` → 解析失败（同上限制） | 唯一宿主 = oracle（§2.2/§六载体表） |
| （非 LOCAL 变体注记行）`time with time zone` / `timestamp with time zone` | 解析失败（WI9a 收窄后形态，`nop.err.antlr.common.parse-fail`，offending=with——WI9a §五对照表行 13/14 实测终稿） | **维持删除态不变**（§5.2 终裁；Phase 2 显式断言不复活） | 无（std 闭集无非 LOCAL 时区成员） | 回写 WI9a 裁定文档 §五对照表 `:152-153` 行的 WI9b 边界注记为落地态 |

## 六、裁定 4：DDL 映射载体——**候选 (a)，oracle 单条目**；`TIME_WITH_LOCAL_TIME_ZONE` 逐成员全负登记

**三选一结论：候选 (a)**（无撞名新键），落地清单**仅 1 条**：

| 文件 | 新增条目 | std→native 渲染 | 原生类型依据（一手文档） |
|---|---|---|---|
| `oracle.dialect.xml` | `<sqlDataType name="TIMESTAMP WITH LOCAL TIME ZONE" stdSqlType="TIMESTAMP_WITH_LOCAL_TIME_ZONE"/>` | `TIMESTAMP WITH LOCAL TIME ZONE`（裸名，无 code 属性；`(false,false)` ⇒ 无 precision 渲染） | Oracle 23ai SQL Language Reference（§2.2 引文；类型自 9i 即有，无 G-1 版本张力） |

- **撞名核对**：§2.4——零撞名，纯新增键；**逐条独立提交**、携带该方言正向用例自洽可绿（WI9a UUID 四方言先例）。
- **反向映射行为变化登记（纯新增键的连带效应，非撞名改写）**：oracle 方言对裸名 `"TIMESTAMP WITH LOCAL TIME ZONE"` 的反查从"未登记键 → `getNativeType` 抛 `ERR_DIALECT_DATA_TYPE_NOT_SUPPORTED`（或 `ignoreUnknown=true` 时落入 `fromJdbcType` 代码兜底）"变为"返回 LOCAL 成员"。既有键 `TIMESTAMP(6) WITH LOCAL TIME ZONE`（带 precision 的 alias）的归 std `TIMESTAMP` 行为**原样保留**——以带 precision 形态报告的 Oracle 驱动读回路径完全不变。裸名反查变化属预期改进（正确类型身份），Phase 3 测试双向断言（裸名 → LOCAL 成员；带 precision 名 → 仍 std TIMESTAMP）。
- **`TIME_WITH_LOCAL_TIME_ZONE` 映射 = 空集（逐成员全负，非候选 (b) 的集合级全负）**：§2.3 实证无任何已取证方言有 `TIME WITH LOCAL TIME ZONE` 原生宿主（Oracle 无 TIME-only 类型系一手文档明示；H2 2.x 实测不可建；PG/MySQL/MSSQL/DuckDB 无 LOCAL 变体；dm/tdengine/db2 红线 unverified 不计入）。**裁定**：不为它造降级映射（挂 `TIME`/`TIMESTAMP WITH TIME ZONE` 等近亲即 WI9a UUID 裁定 (b) 拒绝过的"丢失类型身份的静默降级"同型错误）——该成员在全方言解析过、生成期响亮报 `nop.err.dao.dialect.std-data-type-not-supported`，与 WI6 `lastday` 的 dm/tdengine 不注册格同款纪律。**plan Goals 第 3 条的"≥1 支持载体"前提由 oracle 条目满足（载体集非空，非候选 (b)）**；Goals 第 3 条"无映射方言保持生成期响亮报错"对两个成员在全部无宿主方言成立。
- **候选 (b)（集合级空集全负）**：不成立（oracle 条目可行），**无需触发其单独 ask-first**；预定义全负形态未被使用，登记于此备查。
- **候选 (c)（定向撞名覆盖）**：不采用（零撞名使其无必要；撞名 = last-wins 静默改写既有反查，§2.4 机制），**无需触发其单独 ask-first**。人若希望 oracle 的 `TIMESTAMP(6) WITH LOCAL TIME ZONE` alias 反查也翻为 LOCAL 成员，那是候选 (c) 形态的 scope change——须单独书面裁决，本 plan 不做。

### 6.1 测试矩阵载体表（期望文本按 `stdToNativeSqlType` 链路推导，非直觉填）

| std 类型 | "支持"载体 | 期望渲染文本 | "不支持"载体（负向响亮报错，错误码字面值 `nop.err.dao.dialect.std-data-type-not-supported`） |
|---|---|---|---|
| `TIMESTAMP_WITH_LOCAL_TIME_ZONE` | oracle（方言模型内存加载，`DialectManager` 路径；真实建表不可达见 §6.4） | `TIMESTAMP WITH LOCAL TIME ZONE` | h2（真方言，`§2.1` 实测无宿主）+ 手工最小方言（`TestEqlTranslationSemantics.buildDialect()` 先例）+ postgresql/duckdb/mssql 等可选加测 |
| `TIME_WITH_LOCAL_TIME_ZONE` | **空集**（逐成员全负） | —— | h2 + oracle + 手工最小方言（oracle 虽为 TIMESTAMP 变体宿主，TIME 变体无宿主——§2.2 一手文档） |

### 6.2 G-1 版本门评估

- oracle `TIMESTAMP WITH LOCAL TIME ZONE`：类型自 Oracle 9i 即存在（§2.2 一手文档为 23ai 版本，类型描述含 9i 以来语义），远低于 G-1 的 Oracle 23ai 承诺门——**无版本门张力**，不新增 unverified 版本边界。
- h2：LOCAL 形态 2.x 已移除（§2.1 `[实测]`）——本 plan 不在 h2 落映射，无门可触。
- 未取证边界维持 unverified 纪律：dm/tdengine/db2 的 LOCAL 类型支持度不取证、不计入覆盖率（红线 `:482`）；其平台侧行为 = 无映射 → 响亮报错（模型级可测，但该测试仅证明平台行为，不证明 DB 能力）。

### 6.3 往返测试设计（EC ① 断言链定稿）

按 plan Phase 3 第 2 项的断言链，载体与可达性分层：

1. **std→native 渲染层（CI 可达，正向）**：`DialectManager` 加载 oracle 方言模型 → `cast(x as timestamp with local time zone)` 经 EQL 编译端到端断言生成文本含 `TIMESTAMP WITH LOCAL TIME ZONE`（闸门 A → 生成器 → `stdToNativeSqlType` 全链，Anti-Hollow 主证据）。
2. **native→std 反查层（CI 可达，模拟往返回程）**：oracle 方言模型 `getNativeType("TIMESTAMP WITH LOCAL TIME ZONE")` → `stdSqlType == TIMESTAMP_WITH_LOCAL_TIME_ZONE`（往返归类同一性：出→入同一成员）；带 precision 名仍归 std `TIMESTAMP`（既有键不变断言）。
3. **负向响亮层（CI 可达）**：h2/最小方言对两个成员解析过、生成期断言错误码字面值；`nop-db-migration` 接线用例（Phase 1 第 4 项）在 h2 载体保持响亮报错形态。
4. **真库执行层（CI 不可达，`[未取证]`）**：真实 Oracle `CREATE TABLE` + JDBC 元数据读回——见 §6.4。

### 6.4 EC ① 满足形态（三选一裁定：**选项 2——不可验证方言逐个列出 + 替代证据**）

- **不可验证方言清单**：oracle（唯一"支持"载体；CI 真库仅 H2——plan Current Baseline 事实）；h2 对 LOCAL 拼写本身不可建表（§2.1 一手证据，`[未取证]` 已消除改判 `[实测-不可建]`）。
- **替代证据** = §6.3 第 1-3 层（渲染文本断言 + 反查归类同一性断言 + 负向响亮断言）+ 本文件 §2.1 H2 探针 transcript + §2.2 Oracle 文档引文。
- **`[未取证]` 标注**：真实 Oracle 引擎往返（建表 + 驱动元数据读回 + std 归类比对的物理层闭环）在本 CI 范围内不可达，标 `[未取证]`，不计入覆盖率，登记为 watch-only（successor = 有真实 Oracle CI 载体时补一轮物理往返）。
- **不采用**选项 1（H2 真库往返——无宿主，物理不可能）与选项 3（全负形态——载体集非空，不触发；且其前置 scope 裁决未被请求）。

## 七、裁定 5：各 Phase 提交切分

按 plan Protected Areas 表默认执行（无新增裁定）：

1. `nop-kernel/nop-commons/.../StdSqlType.java`（2 成员 + 3 重钉）+ `nop-commons`/`nop-db-migration`/`nop-orm-eql` 中不依赖方言 XML 与文法的聚焦测试 → **单独提交**（回滚单位；不含文法/方言/文档逻辑改动）。
2. `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（`dataTypeName_` 新增 2 备选）+ 重生成产物（`EqlParser.java`/`Eql.interp`）+ 手写闸门 A 适配（§5.3）+ parse/compile 测试 → **单独提交**。
3. `oracle.dialect.xml`（1 条 `<sqlDataType>`）+ 其正/负向用例 → **单独提交**（可单独 `git revert`）。
4. owner docs（`orm-model-design.md`、`eql-and-database-compatibility.md`）+ roadmap 勘误注记 + plan 勾选 + daily log + 本裁定文档 → 文档提交，与代码提交分离（roadmap 注记按 plan Phase 4 再单独成提交亦可）。

**重生成 diff 审计口径**（Phase 2 用，WI2/WI9a/WI7 同款）：仅 `EqlParser.java`/`Eql.interp` ATN 变化；token 表零增删（`EqlLexer*`/`*.tokens` 预期零 diff——若非零逐 token 解释）；`ast/_gen`/`EqlAST.xjava`/`_EqlASTBuildVisitor.java` 零 diff（不新增 AST 节点/字段，`SqlTypeExpr` 复用 `name` 字段；§5.3 的手写适配在 parse 层手写 EqlASTBuildVisitor（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/`），不在生成链）。偏差即停。

## 八、WI9a 裁定文档 §九 键集计数的勘误移交（plan Phase 0 EC 第 1 条）

- **事实**：`2026-10-04-wi9a-phase0-adjudications.md` §九记载"现值（30 键）→ Phase 1 后（34 键）"，其中 Phase 1 后值 34 系**算术误记**（25 活成员误按 30 计）。2026-10-05 实测（`_tmp/wi9b/stdnames-probe.log`）：WI9a 落地后 `getNames()` 实际 = **30 键**（26 活枚举成员含 `UUID` + `INT`/`TEXT`/`NUMBER`/`BOOL` 四别名，无键冲突）。其消费面结论（`ARG_ALLOWED_NAMES` 扩集为预期行为、`Collections.unmodifiableSet` 无可变性风险）不受该计数误记影响，仍然成立。
- **移交形态**：照 G-3 增补裁决先例（WI9a 裁定文档 §三"带日期增补小节，不重写原文"）与 roadmap 原地勘误注记先例（WI7/WI7b/WI10b 执行终态注记），本勘误**落在本裁定文档 + roadmap WI9b 节的勘误注记内**（plan Phase 4 第 2 项），**不重写 WI9a 裁定文档原文**。
- **对本 plan 的传导**：plan 32 修订记录第 2 条已按实测口径更正（键集 30→32，非 34→36）；Phase 1 完成后以聚焦测试实测值复核（预期 32）。

## 九、附录：`StdSqlType.getNames()` 键集 30→32 实测登记（plan 差异登记第 4 条的执行时点归档）

2026-10-05 实测 30 键全清单（字典序）：

```text
ANY, ARRAY, BIGINT, BINARY, BLOB, BOOL, BOOLEAN, CHAR, CLOB, DATE, DATETIME,
DECIMAL, DOUBLE, FLOAT, GEOMETRY, INT, INTEGER, JSON, NUMBER, NUMERIC, OTHER,
REAL, SMALLINT, TEXT, TIME, TIMESTAMP, TINYINT, UUID, VARBINARY, VARCHAR
```

（26 枚举名 + `INT`/`TEXT`/`NUMBER`/`BOOL` 四别名；无键冲突。）

Phase 1 后预期 32 键 = 上清单 + `TIME_WITH_LOCAL_TIME_ZONE` + `TIMESTAMP_WITH_LOCAL_TIME_ZONE`（枚举名经 `values()` 循环自动入表，无须显式 put）。消费面：闸门 A 错误 param `ARG_ALLOWED_NAMES`（`EqlASTBuildVisitor.java:474`）、闸门 B 错误 param（`SimpleStdDomainHandlers.java:1634`）同步扩集——预期行为。
