# 29 EQL WI11 — DAO 层批量 upsert 原语（`ERR_SQL_DUPLICATE_KEY` 复用 + nop-stream 两私有枚举迁移 + `MERGE` ledger 裁定）

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI11 节（`:357-362`）、Work Item Status WI11 行（`:95`）、依赖与门控矩阵 WI11 行（`:118`）、「明确延后项」`MERGE`/`UPDATE..FROM` 行（`:142`）、Cross-Cutting（`:380-390`）、关键约束与红线；`ai-dev/audits/check/nop-dao.md:393-415`（`IUpsertHandler` 删除先例）；Current Baseline 全部代码事实在本工作区（分支 `feat/eql-cross-db-compat`）逐文件实测
> Related: `ai-dev/plans/23-eql-wi10b-in-list-gate-and-dead-flags.md`（硬前置，`completed`，Completed: 2026-10-04，closure audit 通过）；`ai-dev/plans/25-eql-wi9a-cast-type-aliases.md`（差异登记 + 裁决人注记形态先例）；`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`（书面裁决授权模式先例）

## Purpose

把 roadmap WI11 收口为四个结果面：

1. **新 DAO 原语**：在 `nop-dao` 新增批量 upsert 原语（public 接口，形态 Phase 0/设计契约裁定），复用**已归一化**的 `ERR_SQL_DUPLICATE_KEY` 翻译链（不新增错误码、不动既有翻译注册），使"按方言生成单语句原子 upsert"成为 DAO 层的正路而非各消费方私有实现。
2. **两枚举迁移**：nop-stream 的两个私有 `UpsertDialect` 枚举（`nop-stream-runtime` 与 `nop-stream-connector-jdbc` 各一）改为消费新原语并删除，消除重复实现。
3. **测试证据**：新原语有"插入 / 更新 / 冲突"三类真库（H2）集成测试（roadmap EC ③）；DB2 `-803` 按 Phase 0 裁定的证据口径处理（结构证据 + 真库 `[未取证]` 登记，不伪造真库执行证据）；方言策略矩阵按 `[文档]`/`[未取证]` 两档登记。
4. **文档与裁定**：设计契约文档（`ai-dev/design/eql-cross-db-compat/`）+ 独立审查；owner doc（含 `IUpsertHandler` 2026-08-22 删除 → 重引入的迁移说明，roadmap 级 EC 第 6 条"新增 nop-dao public API 的 owner doc 已补"）；「明确延后项」`MERGE`/`UPDATE..FROM` 行在本 WI 裁定（EC ⑤b，不允许名义 owner）；`dialect.xdef:32` 残留 `<upsertHandler>` 元素处置落档。

**ask-first 门禁声明**：新增 `nop-dao` public 接口属「AI Must Ask Before」第 1 条（roadmap WI11 节 `:360` 与门控矩阵 `:118` 明文；`nop-dao` 不是 `nop-*-api`，非 AGENTS.md 字面 Protected Area，按**更严的自定门禁**处理，不得假借 plan-first 之名绕过询问）。授权依据与裁决人注记形态见「与 roadmap 的差异登记」第 2 条。

## Current Baseline

> 锚点一律符号定位（类#方法 / 常量名 / XML 元素名）；file:line 为 2026-10-03 起草时点实测参考。本 WI 不触碰 `.g4`/AST 生成管线，行号漂移风险低，但执行时仍以每 Phase 开工复核为准。

### 硬前置与门禁状态（实测）

- **WI10b 已 `done`**：`ai-dev/plans/23-eql-wi10b-in-list-gate-and-dead-flags.md` Plan Status `completed`（Completed: 2026-10-04，closure audit 通过，收口 commit `949531d93e`）；roadmap Work Item Status WI10b 行 = `done`（`:94`）。roadmap 依赖矩阵 WI11 行的硬前置**只有 WI10b**，**无排队门**（无其他 in-flight WI 前置）。
- roadmap WI11 行（`:95`）当前 `todo`：draft review 通过后 `todo`→`planned`；独立 closure audit 通过后 `planned`→`done`（**不得提前**，流转规则见 roadmap `:20/:77`）。
- Protected Area（roadmap `:118`）：**ask-first** —— 新增 `nop-dao` public 接口（`IUpsertHandler` 2026-08-22 删除先例）。Required Evidence（roadmap Cross-Cutting）：owner doc + migration plan + 回归测试。本 plan 的 Required Evidence 对应 = Phase 4 owner doc（含迁移说明）+ Phase 2/3 回归与真库测试。

### `IUpsertHandler` 删除先例（2026-08-22，本 WI 的直接背景）

- `IUpsertHandler`（包 `io.nop.dao.dialect.upsert`，接口单方法 `buildUpsert(tableName, columnNames)` 返回 `SQL.SqlBuilder`）与唯一实现 `MysqlUpsertHandler` 由 commit `3f1111078d`（2026-08-22）删除；处置注记在 `ai-dev/audits/check/nop-dao.md:415`（roadmap 引用行号实测命中），原文要点：唯一实现恒返回 null、全仓零调用方、零 xml/xdef 引用（dialect 模型的 `upsertHandler` 为 **String 字段**、无类型依赖），属误导性死代码；**"未来实现 MySQL upsert 时应连同集成测试重新引入"**。
- ⇒ 本 WI 的重引入验收由此**高于旧形态**：不得回到"接口存在但无行为"的空壳（Minimum Rules #8/#24）；必须**连同真库集成测试**一起重引入（roadmap EC ③ 明文 + 删除注记明文）。

### `dialect.xdef:32` 残留 `<upsertHandler>` 元素（WI10a 审查发现，本 WI Phase 0 裁定处置）

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef:32` 仍声明 `<upsertHandler>class-name</upsertHandler>` 元素。
- 冻结生成类 `_DialectModel`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/model/_gen/_DialectModel.java`，字段 `:196/:199`、getter/setter `:742-754`、序列化 `:811/:848`）仍有 `upsertHandler` String 字段；**全仓零消费者、零 XML 配置引用**（本次起草 grep 复测：仅 `_gen` 自身 + xdef 两处命中，与审计 `:410` 结论一致）。
- ⚠️ **`nop-dao` 无 codegen 管线**（roadmap Cross-Cutting `:384`）：`pom.xml:119` 跳过 precompile，`_DialectModel` 是冻结的。⇒ 硬删 xdef 元素**不会**同步删 `_gen` 字段（留下"schema 无元素、模型有字段"的失配；字段恒 null、无行为破坏）；保留元素 + javadoc 注记 = WI10b 软弃用先例（xdef 注释块加注、属性/元素零变化）。两候选 + "保持现状" 候选由 Phase 0 裁定。
- ⚠️ **不排除**的第三候选：新原语若裁定恢复"dialect 模型 handler 挂载"形态（见 Phase 0 接口落点三选一），该元素才有复活语义——届时处置随挂载裁定一并定。

### `ERR_SQL_DUPLICATE_KEY` 现状（roadmap EC ①：8 文件 / 11 处生产调用点，实测吻合）

- **定义**：`nop-persistence/nop-dao/src/main/java/io/nop/dao/DaoErrors.java:131` —— `nop.err.dao.sql.duplicate-key`（param `ARG_SQL_TEXT`）。
- **翻译注册**：`DialectSQLExceptionTranslator.java:70`（`register(ERR_SQL_DUPLICATE_KEY)`，static 块；import `:54`）。翻译机制 = `#translateByVendorCode`（`:176`）按 `SQLException.getErrorCode()`/`getSQLState()` 查表，表来源 = 各方言 XML `<errorCodes>`（`#buildVendorCodeToErrorCodes` `:107`）。**本 WI 复用、不改动**该定义与注册。
- **方言侧 duplicate-key 登记（实测，共 8 个方言 XML）**：h2 `23001,23505`（`h2.dialect.xml:119-121`）、postgresql `23505`（`postgresql.dialect.xml:75-77`，roadmap 引 `:76` 命中值行）、db2 `-803`（`db2.dialect.xml:23-25`；⚠️ roadmap 引 `db2:13` **行号已漂移**——WI3 第 12 项为 db2 补 mandatory 子节点使行号下移，见差异登记第 3 条）、dm `-6013,-6015`（`dm.dialect.xml:50-52`，roadmap 引 `:51` 命中值行）、tdengine `1062`（`tdengine.dialect.xml:153-155`，roadmap 引 `:154` 命中值行）、mysql `1062`（`mysql.dialect.xml:119-121`）、mssql `2601,2627`（`mssql.dialect.xml:86-88`）、oracle `1`（`oracle.dialect.xml:80-82`）。
- **11 处生产调用点（8 文件，2026-10-03 grep 实测，与 roadmap EC ① 字面一致）**：
  | 文件 | 行 | 处数 |
  |---|---|---|
  | `nop-auth/nop-auth-service/.../login/DbLoginAttemptStore.java` | `:74`、`:107` | 2 |
  | `nop-auth/nop-auth-service/.../ratelimit/DbSendCodeRateLimiter.java` | `:127`、`:149`、`:162` | 3 |
  | `nop-auth/nop-auth-service/.../mfa/store/DbEmailCodeStore.java` | `:85` | 1 |
  | `nop-auth/nop-auth-service/.../mfa/store/DbSmsCodeStore.java` | `:92` | 1 |
  | `nop-batch/nop-batch-dao/.../store/DaoBatchStateStore.java` | `:295` | 1 |
  | `nop-retry/nop-retry-engine/.../impl/RetryEngineImpl.java` | `:240` | 1 |
  | `nop-sys/nop-sys-dao/.../seq/SysSequenceGenerator.java` | `:173` | 1 |
  | `nop-sys/nop-sys-dao/.../lock/SysDaoResourceLockManager.java` | `:197` | 1 |
  （另有测试消费 `nop-auth-service/src/test/.../TestDbCodeStoreSendRace.java`，不计入生产口径。）
- **全部调用形态** = "执行 INSERT → 捕获异常 → 判错误码 == `ERR_SQL_DUPLICATE_KEY` → 决定 UPDATE/重试/幂等放行"。它们依赖的契约面只有两条：错误码定义不变、vendorCode→错误码翻译链不回归。⇒ **本 WI 不改写这 11 个调用点**（EC ① 只要求回归不受影响）；新原语只是新增一个消费该契约的正路（把"INSERT 撞键后补救"升级为"单语句原子 upsert 不撞键"）。

### 两个私有 `UpsertDialect` 枚举现状（roadmap EC ②，2026-10-03 逐文件实测）

**① `JdbcCheckpointStorage.UpsertDialect`**（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:800`，package-private）：

- 四值：`POSTGRESQL` / `MYSQL` / `H2` / `GENERIC`。
- `resolveUpsertDialect()`（`:810`）：`jdbcTemplate.getDialectForQuerySpace(querySpace).getName()` 小写前缀匹配（`postgresql`→PG、`mysql`/`mariadb`→MYSQL、`h2`→H2），**异常吞掉回落 GENERIC**。
- `buildNativeUpsertSqlText(...)`（`:859`，static）：PG = `INSERT ... ON CONFLICT (conflictCols) DO UPDATE SET c = EXCLUDED.c`；MYSQL = `INSERT ... ON DUPLICATE KEY UPDATE c = VALUES(c)`；H2 = `MERGE INTO t (cols) KEY (conflictCols) VALUES (?...)`；GENERIC = 返回 null → 调用方回落 `runInsertOrUpdateSeparateTxns`（`:895`）：INSERT 单独事务 → `isDuplicateKeyException`（`:937`，static，R5-CON-08 决策表：SQLState `23505`，或 `23000` + 已知 duplicate vendorCode（MySQL 1062/1022、SQL Server 2627/2601、Oracle 1）→ 判重）→ UPDATE 在**新事务**（规避 PostgreSQL "current transaction is aborted"）。**无标识符转义**（表名/列名裸拼接）。
- 使用点 3 处：`storeCheckPoint`（`:91`）、`storeSavepoint`（`:319`）、`storeEpochManifest`（`:520`），三处同构（columns/conflictColumns/updateColumns/values 四数组 + GENERIC 分流）。
- **消费测试**：`TestJdbcCheckpointStorage`（nop-stream-runtime test，`:353-489`）：四方言 SQL 形态断言（`testUpsertSqlShapePerDialect`）+ H2 `MERGE` **真执行**更新断言（`testDuplicateKeyUpsertUpdatesData`）+ GENERIC 回落语义断言。

**② `JdbcTwoPhaseCommitSink.UpsertDialect`**（`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:167`，private）：

- 三值：`MYSQL` / `POSTGRESQL` / `H2`（**无 GENERIC**——不支持的方言由 `resolveUpsertDialect()`（`:1039`）**抛 `StreamException(ERR_STREAM_STATE_ERROR)` 响亮拒绝**（`:1050`），javadoc 明写 built-in sync scope 只目标 MySQL/PostgreSQL、H2 供测试）。
- `buildUpsertSql(...)`（`:1061`）：经 `d.escapeSQLName` **转义**全部标识符；支持 **key-only 空更新集**形态（PG = `ON CONFLICT .. DO NOTHING`；MYSQL = `ON DUPLICATE KEY UPDATE key = key` 自赋值 no-op；H2 = `MERGE INTO .. KEY`）。
- 使用点：`ensureKeyedColumns` 首批建立时（`:1004-1005`，结果缓存于 volatile 字段 `upsertSql`/`upsertDialect` `:237-239`）。
- **消费测试**：`TestJdbcTwoPhaseCommitSinkDml`、`TestJdbcConnectorFactory`（connector-jdbc）。

**两枚举的四项分叉（Phase 0 迁移方案的裁定输入）**：① 方言名解析失败行为（吞异常回落 GENERIC vs 响亮抛错）；② 标识符转义（裸拼 vs `escapeSQLName`——**统一为转义**无合理反方）；③ key-only 空更新集形态（仅 sink 需要）；④ 重复键检测（GENERIC 回落路径的 `isDuplicateKeyException` 决策表 vs 翻译器 vendorCode/XML 表——**两套并存的判定口径，差集是双向的**，见差异登记第 5 条：决策表宽在 {MySQL 1022、无条件 SQLState-23505 兜底（对未登记方言生效）、`getNextException` 链遍历}；翻译器/XML 宽在 {H2 23001、DB2 -803、DM -6013/-6015、TDengine 1062}。⚠️ **碰撞实例已入库**：现有绿测试 `TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert`（`:480`）用 **oracle 方言模型 + H2(`MODE=MySQL`)**（`:489-492` 强制 querySpace→"oracle"），撞键异常 SQLState 23505 / vendorCode 23505 均不在 oracle XML 登记的 `{1}` 里——决策表靠"无条件 23505 兜底"判 duplicate（测试绿的前提）；同一异常在翻译器口径下经 `#translateByType`（`SQLIntegrityConstraintViolationException` 分支）译成 `ERR_SQL_DATA_INTEGRITY_VIOLATION` 而非 duplicate-key ⇒ **机械"统一为翻译器口径"会直接破坏该现有 GENERIC 回落绿测试**，任何统一方案必须把它列为回归锚）。

- 模块依赖方向：nop-stream-runtime 对 `nop-dao` = **provided** scope（`nop-stream-runtime/pom.xml:83-85`）；nop-stream-connector-jdbc = compile（`:33`）。两模块均已是 nop-dao 下游，迁移不引入新依赖边。

### H2 真库集成测试载体（roadmap EC ③，实测）

- **真库 CI 只有 H2**：`JdbcTestCase`（`nop-persistence/nop-dao/src/test/java/io/nop/dao/jdbc/JdbcTestCase.java:109-116`）硬编码 `org.h2.Driver` + `jdbc:h2:mem:<uuid>`；本地解析 H2 = **2.4.240**（`.nop/repository`）。`TestDialect` 的"真库执行路径"即该 H2 载体（roadmap EC ④ 字面在现 CI 下只能落到 H2，DB2 部分见差异登记第 1 条）。
- **H2 upsert 语法**：两个既有实现均已用 `MERGE INTO t (cols) KEY (kcols) VALUES (..)`，且 `TestJdbcCheckpointStorage.testDuplicateKeyUpsertUpdatesData` 已在 H2 上**真执行通过**（现有绿测试 = 语法可行性先例，不需本 WI 重新证明）；H2 另支持 `INSERT ... ON CONFLICT`（PG 兼容形态）——⚠️ **已知坑（`TestJdbcCheckpointStorage` javadoc `:432-435` 记录在案）**：H2 2.3.232 对 PG 形态 `ON CONFLICT .. DO UPDATE SET col = EXCLUDED.col` **抛语法错误**（真库验证不可得的实证）；`MERGE INTO .. KEY` 在本仓解析的 H2 2.4.240 **仍支持**（该测试类 29/29 绿跑实证，含 H2 MERGE 真执行用例）。**用哪条语法做原语的 H2 载体属 Phase 0 载体裁定**（已知坑入裁定输入，以 Phase 3 真执行为准）。
- **冲突类测试可端到端断言错误码**：`h2.dialect.xml:119-121` 已登记 duplicate-key `23001,23505` ⇒ H2 真执行撞唯一键 → `SQLException` → `DialectSQLExceptionTranslator` → `ERR_SQL_DUPLICATE_KEY` 全链可断言（**无需改任何方言 XML**）。三类用例落法：插入（首次 upsert 落库且行数 +1）/ 更新（同键二次 upsert 值更新且行数不增）/ 冲突（重复键触发翻译链，断言 `ERR_SQL_DUPLICATE_KEY` 而非裸 `SQLException`）+ 批量语义真执行（多行批、部分新部分旧）。
- **端到端口径（Minimum Rules #22）**：从新原语入口（public 接口调用）到 H2 库中行状态/异常错误码，完整走通，不经 mock。

### DB2 `-803` 与方言矩阵证据等级（roadmap EC ④ + 红线）

- 真库 CI 无 DB2/PG/MSSQL/DM/TDengine ⇒ **真库执行证据一律 `[未取证]`**，不得计入覆盖率、不得用文本断言冒充真库执行（对齐 roadmap 红线"证据等级纪律"与 WI5 EC ⑥"无法真库验证的方言逐个列出并给出替代证据"）。
- **DB2 `-803` 的结构证据可落**：`db2.dialect.xml` 经 WI3 第 12 项修复已可实例化（补 `driverClassName`/`jdbcUrlPattern`，selector 已注册）⇒ 用 db2 方言模型构造 `DialectSQLExceptionTranslator` + 合成 `SQLException(vendorCode=-803, sqlState=23505)` 的单测可断言翻译出 `ERR_SQL_DUPLICATE_KEY`——证明**登记映射 + 翻译链**成立；DB2 真库行为仍 `[未取证]`。是否采纳该结构证据形态 = Phase 0 裁定（roadmap EC ④ 字面"集成测试覆盖 DB2 的 -803（TestDialect 的真库执行路径）"在无 DB2 真库 CI 的现实下的唯一诚实落法）。

### `MERGE`/`UPDATE..FROM` ledger 行（roadmap `:142`，EC ⑤b 要求本 WI 裁定）

- v6 已更正的输入事实（**不得回引调研报告**）：Oracle **有** `WHEN MATCHED THEN DELETE`（19/21/23 三版文档确认）；真正的语义分歧只有两条——**PG 15 的 `MERGE` 无 `BY SOURCE`**（需 PG 17）+ **MSSQL `MERGE` 是唯一强制终止 `;` 的 T-SQL 语句**（error 10713）；`postgresql.dialect.xml` 无版本门。
- roadmap「明确不做的事」（`:51`）已裁定 **UPSERT 不做进语法层**（本 WI 即"改走 DAO 原语"的落地）；EQL 语法层的 `MERGE` 语句同样不在任何 WI 范围。⇒ ledger 行不能再名义挂在 WI11（EC ⑤b 禁止名义 owner），Phase 0 裁定方向：**显式移出**（EQL 语法层不做 `MERGE` 语法；DAO 原语的 H2 分支内部生成 `MERGE INTO .. KEY` 语句文本与"语法层不做 MERGE"不矛盾——一个是 JDBC 语句生成、一个是 EQL 文法），successor 与触发条件书面化（候选：未来 EQL `MERGE` 语法 WI（plan-first）/ 永久否决登记）。注意 ledger 行同时含 `UPDATE..FROM`（`supportUpdateFromJoin` 已由 WI10b 改档为契约登记 + 文法响亮失败，本 WI 不重开）。

### 全绿命令与 stream 豁免口径

- roadmap EC ⑤ = `./mvnw test -pl :nop-dao,:nop-orm,:nop-orm-eql -am -T 1C`。⚠️ `-am` 只构建**上游依赖闭包**：nop-stream-runtime/nop-stream-connector-jdbc 是 nop-dao 的**下游**，**不在该命令闭包内**。本 WI 改这两个 stream 模块 ⇒ 本 plan 自加 `./mvnw test -pl :nop-stream-runtime,:nop-stream-connector-jdbc -am -T 1C`（见 Closure Gates；stream 既有失败豁免口径照 roadmap Cross-Cutting：`TestStreamConnectorRegistryDiscovery` 1 项与本 WI 零关联时按基线豁免，**以收口日实跑为准**，不预写结论）。

## 与 roadmap 的差异登记（起草时实测发现）

1. **EC ④ "DB2 -803（TestDialect 的真库执行路径）" 与真库 CI 现实冲突**：`TestDialect` 的真库 = H2（`JdbcTestCase` 硬编码），仓库无 DB2 真库 CI。字面落地不可能。处置（Phase 0 裁定书面化）：DB2 部分落**结构证据**（db2 方言模型翻译链单测）+ 真库执行 `[未取证]` 登记（红线纪律），H2 侧承载"真库执行路径"的实际验证。这不削弱 EC ④ 的意图（-803 映射链被验证），只是把"真库"限定词按证据等级如实落档。
2. **ask-first 门禁的授权依据（裁决人注记形态，照 WI9a UUID 处置先例）**：`IUpsertHandler` 的重引入未被任何 Gate 裁决覆盖（G-1..G-4 均不涉及）。授权依据 = **用户对 roadmap 的执行指令**（WI11 在 roadmap 执行队列中）+ **WI11 节证据链**（`:360` 删除注记明文"连同集成测试重新引入"，即 roadmap 自身已表达重引入意图）。形态 = **平台负责人经执行指令授权、AI 基于证据链代拟结论、人可推翻**——推翻时 revert 原语提交（独立提交可回滚），并在设计目录追加修订裁决；书面裁定的"裁决人注记"小节落 Phase 0 裁定文档（形态同 `ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md` 的 G-4 门与 plan 25 差异登记第 2 条）。⚠️ 不得以本 plan 的 draft review 替代该门——draft review 是 plan-first 载体，ask-first 需要的是书面授权登记。
3. **行号漂移**：roadmap WI11 节引 `db2:13` → 实测 `-803` 登记在 `db2.dialect.xml:23-25`（WI3 第 12 项补 mandatory 子节点所致下移）；`postgresql:76`/`dm:51`/`tdengine:154` 命中值行 ✓；`JdbcCheckpointStorage.java:800` ✓；`JdbcTwoPhaseCommitSink.java:167` ✓（v7.1 已更正过的锚点本次复核仍准）；`ai-dev/audits/check/nop-dao.md:415` ✓。执行时一律符号定位。
4. **EC ⑤ 命令不含 stream 下游**（非冲突，登记）：roadmap EC ⑤ 的 `-pl` 清单是 roadmap 级底线；本 WI 改 nop-stream 两模块 ⇒ plan 自加 stream 命令（见 Current Baseline 末节），roadmap 文本不需为此修订。
5. **roadmap 未点名两套重复键判定口径并存，且差集是双向的**（翻译器 vendorCode/XML 表 vs `isDuplicateKeyException` 决策表）：登记面实测为 **8 个方言 XML**（含 mysql `1062`/mssql `2601,2627`/oracle `1`，见 Current Baseline 清单），**不是"决策表单方面更宽"**——决策表宽在 {MySQL 1022、无条件 SQLState-23505 兜底（对未登记方言生效）、`getNextException` 链遍历}；翻译器/XML 宽在 {H2 23001、DB2 -803、DM -6013/-6015、TDengine 1062}。**具体碰撞实例（裁定输入，必须可回归验证）**：现有绿测试 `TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert` 用 oracle 方言模型 + H2(`MODE=MySQL`)，撞键异常 SQLState 23505 / vendorCode 23505 均不在 oracle XML 登记的 `{1}` 里——决策表按 23505 兜底判 duplicate（该测试绿的前提）；同一异常在翻译器口径下经 `translateByType` 的 `SQLIntegrityConstraintViolationException` 分支译成 `ERR_SQL_DATA_INTEGRITY_VIOLATION` ⇒ 统一方案若照搬翻译器口径会直接破坏该测试。迁移时"以哪套为准/如何叠加"是设计决策不是机械搬移：Phase 0 裁定的候选必须含"**翻译器优先 + SQLState-23505 兜底**"形态（翻译器按方言 XML vendorCode 精确归一化，未命中时以无条件 SQLState-23505 兜底判 duplicate，保住 oracle 方言模型碰撞路径），并把该碰撞测试列为回归锚；不允许迁移时静默丢掉任一语义。

## Protected Areas 与准入声明（roadmap Cross-Cutting 强制）

| 触及面 | 定性 | 门禁 | Required Evidence |
|---|---|---|---|
| `nop-dao` 新增 public 接口/原语 | 「AI Must Ask Before」第 1 条（非 `nop-*-api`，非 AGENTS.md 字面 Protected Area；**比 plan-first 更严的自定门禁**） | **ask-first**：差异登记第 2 条的书面授权登记（裁决人注记落 Phase 0 裁定文档） | owner doc（Phase 4，含 `IUpsertHandler` 删除→重引入迁移说明）+ 回归测试（Phase 2/3） |
| `nop-stream-runtime` / `nop-stream-connector-jdbc` 私有实现改动 | 普通手写代码（私有枚举/私有方法，无对外契约） | 无额外门禁；既有 stream 测试是回归底线不得削弱 | Phase 2 命令全绿 |
| `dialect.xdef:32`（若裁定加注记） | 仅 javadoc 注释行（不硬删元素——硬删 = plan-first 且造成 `_gen` 失配） | 按 WI10b 软弃用先例：注释块加注，元素/绑定零变化 | 配置加载回归（既有测试随命令绿） |
| `nop-persistence/nop-dao/.../model/_gen/_DialectModel.java` 等冻结生成物 | **禁止手改**（AGENTS.md Hard Stop） | 本 plan **零手改**（`upsertHandler` String 字段随裁定保留，无类型依赖） | — |
| 方言 XML（`*.dialect.xml`） | 平台对外契约 | **计划内零改值**（h2 duplicate-key 已登记，无需改；若 Phase 3 实测暴露必须补某方 errorCode，走差异登记 + 独立提交 + 矩阵快照复核） | 矩阵快照门禁 |

⚠️ 本 plan 不触碰：`.g4`/`EQLAST`/任何生成管线（零文法变更）、`model/*.orm.xml`、`nop-auth` 认证模型、`nop-*-api`、11 个既有调用点的业务语义。

## Goals

- `nop-dao` 批量 upsert 原语按 Phase 0 裁定 + Phase 1 设计契约落地：给定表/列集/冲突键列集/更新列集/行批数据，**支持原生单语句 upsert 的方言生成并执行该语句**；无原生 upsert 的方言行为**按终裁显式失败或显式回落**（不允许静默）；重复键相关异常统一经既有 `ERR_SQL_DUPLICATE_KEY` 翻译链（零新增错误码、零翻译注册改动）。
- nop-stream 两个私有 `UpsertDialect` 枚举**下线**：`JdbcCheckpointStorage` 三使用点与 `JdbcTwoPhaseCommitSink` keyed 路径改为消费新原语，私有枚举与配套私有方法删除，全仓无重复 upsert 实现分叉（转义/名称解析/key-only 形态按终裁统一）。
- 测试证据链：H2 真库"插入 / 更新 / 冲突"三类集成测试 + 批量语义真执行（冲突类端到端断言 `ERR_SQL_DUPLICATE_KEY`）；DB2 `-803` 按 Phase 0 裁定口径（结构证据 + `[未取证]` 登记）；方言策略矩阵带证据等级列落 owner doc。
- 文档与裁定收口：设计契约文档（独立子 agent 审查 approve）；owner doc（含 2026-08-22 删除 → 重引入迁移说明）；「明确延后项」`MERGE`/`UPDATE..FROM` 行裁定落档（显式移出 + successor + 触发条件，不允许名义 owner）；`xdef:32` 残留处置落档。

## Non-Goals

- 不把 UPSERT/`MERGE` 做进 EQL 语法层（roadmap `:51` 明确不做）；**零 `.g4`/AST/生成管线触碰**。
- 不改写 11 个既有 `ERR_SQL_DUPLICATE_KEY` 调用点的两段式模式（EC ① 只要求回归不受影响；把它们迁移到新原语属优化，见 Deferred）。
- 不新增错误码、不改 `DialectSQLExceptionTranslator` 的既有注册与翻译行为。
- 不做 nop-orm 实体层 upsert 语义（`IEntity`/`CrudOperator` 级封装——ledger「批量 DML 抽象」行：单独 roadmap）。
- 不扩真库 CI（PG/MSSQL/DB2/DM/TDengine 真库执行取证不在本 WI）。
- 不改任何既有方言 XML 能力值；不手改 `_gen` 冻结生成物。
- 不处理 `MERGE`/`UPDATE..FROM` 的 EQL 文法可行性（ledger 裁定只决定"行归谁、何时复议"，不做语法设计）。

## Scope

### In Scope

- `nop-persistence/nop-dao`：新 public 批量 upsert 原语（接口落点与形态按 Phase 0 三选一终裁）+ 实现 + 单元测试（SQL 形态/名称解析/转义/无原生方言行为）+ H2 真库集成测试 + DB2 `-803` 结构证据测试。
- `nop-stream/nop-stream-runtime`：`JdbcCheckpointStorage` 三使用点切换到新原语；删私有 `UpsertDialect`/`resolveUpsertDialect`/`buildNativeUpsertSqlText`（GENERIC 回落归属按终裁）；既有测试迁移。
- `nop-stream/nop-stream-connector-jdbc`：`JdbcTwoPhaseCommitSink` keyed 路径切换；删私有 `UpsertDialect`/`resolveUpsertDialect`/`buildUpsertSql`；既有测试迁移。
- `ai-dev/design/eql-cross-db-compat/`：Phase 0 裁定文档（含裁决人注记）+ Phase 1 设计契约文档（含独立审查证据）。
- `docs-for-ai/`（Phase 4）：owner doc 新节（按 Phase 0 裁定落点）+ `INDEX.md`/`source-anchors.md`（如路由/锚点变化）。
- `ai-dev/backlog/eql-cross-db-compat-roadmap.md`（Phase 4）：ledger `:142` 行裁定回写（原地注记）+ WI11 状态行流转。
- `ai-dev/logs/`：各 Phase 对应日期条目。

### Out Of Scope

- 11 个既有调用点的迁移（watch-only / optimization，见 Deferred）。
- WI6-WI10b 之外的任何 roadmap WI 范围；WI12 的 `MERGE` 相关长尾（ledger 裁定的 successor 若指向未来语法 WI，另行立项）。
- 真库 CI 扩容、Docker 化多库测试基建。
- nop-orm 实体层/`orm.xml` 模型层任何变更。

## Execution Plan

> 顺序 Phase：Phase 0（纯裁定）→ Phase 1（设计契约 + 独立审查）→ Phase 2（实现）→ Phase 3（真库测试）→ Phase 4（文档落档）。Phase 0/1 是 Phase 2 的硬前提；Phase 3 依赖 Phase 2 的原语落地；Phase 4 收文档面。每个代码 Phase 开工前复核锚点符号仍存在，漂移以符号重定位并在本 plan 勘误。

### Phase 0 - 前置裁定（纯裁定，不改产品代码）

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增一份裁定文档，含裁决人注记小节）；`ai-dev/logs/`

- Item Types: `Decision`、`Proof`

- [x] **硬前置 + 门禁登记**：复核 WI10b plan `completed` 与 roadmap WI10b `done` 未回退；把差异登记第 2 条的 ask-first 授权依据（用户对 roadmap 的执行指令 + WI11 节证据链）以**裁决人注记**形态写入裁定文档（照 plan 25 差异登记第 2 条 + WI0 gate 裁决文档 G-4 门的形态：授权来源、AI 代拟、人可推翻、推翻即 revert 原语提交）。
- [x] **裁定接口形态与落点（三选一，书面记录被拒候选理由）**：(a) `io.nop.dao.jdbc` 新 public 接口（与 `IJdbcTemplate` 平行，实现挂 `JdbcTemplateImpl`）；(b) `IJdbcTemplate` 直接追加方法；(c) 恢复 dialect 模型 `upsertHandler` handler 挂载形态（String 字段 + 反射/装配——⚠️ 空壳前科 + `nop-dao` 无 codegen、装配通路不存在，原则上不利，Phase 0 须给书面否决理由或推翻该预判）。同时终裁语义点：批量方法签名语义（行批 + 列集 + 冲突键列集 + 更新列集）、方言名解析失败行为（**吞异常回落 GENERIC vs 响亮抛错**——两枚举分叉 ①）、key-only 空更新集是否入原语契约（分叉 ③）、标识符转义统一 `escapeSQLName`（分叉 ②）、GENERIC 两事务回落的归属（下沉 nop-dao vs 留 nop-stream）与**重复键判定口径的统一形态**（分叉 ④，差集双向见差异登记第 5 条——**候选必须含"翻译器优先 + SQLState-23505 兜底"形态**：翻译器按方言 XML vendorCode 精确归一化，未命中时以无条件 SQLState-23505 兜底判 duplicate，保住 oracle 方言模型 + H2 的 GENERIC 回落路径；无论采纳哪一候选，`TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert` 均为回归锚，不得删除或削弱）。
- [x] **裁定两枚举迁移方案**：逐消费面点名（`storeCheckPoint`/`storeSavepoint`/`storeEpochManifest` 三处同构调用；sink 的 `ensureKeyedColumns` 首批缓存形态——原语调用点从"建 SQL"移到"执行"，volatile 缓存语义如何保持）；既有 stream 测试（`TestJdbcCheckpointStorage` 四方言形态断言 / `TestJdbcTwoPhaseCommitSinkDml`）的迁移口径（断言迁移到原语测试，不得删除覆盖面）。
- [x] **裁定 H2 三类测试形态**：`JdbcTestCase` 载体 + 建表形态（含唯一键）+ 三类用例设计（插入/更新/冲突，冲突类断言 `ERR_SQL_DUPLICATE_KEY` 端到端）+ H2 载体 upsert 语法——**带入 `TestJdbcCheckpointStorage` javadoc（`:432-435`）已记录的已知坑：H2 2.3.232 对 PG 形态 `ON CONFLICT .. DO UPDATE SET col = EXCLUDED.col` 抛语法错误**；`MERGE INTO .. KEY` 在本仓解析的 H2 2.4.240 仍支持（该测试类 29/29 绿跑实证，含 H2 MERGE 真执行用例）。候选 = 沿用 `MERGE INTO .. KEY` 先例（有绿跑实证）vs `ON CONFLICT`（须先在本仓解析的 H2 版本上真执行验证 PG 形态不触发该语法坑，方可入选）；以 Phase 3 真执行为准记账。另裁定批量语义用例 + GENERIC 回落（若终裁下沉 nop-dao）是否可 H2 实测及其形态。
- [x] **裁定 DB2 `-803` 与方言矩阵证据口径**：结构证据形态（db2 方言模型 + 合成 `SQLException` 翻译链单测）采纳与否；逐方言证据等级表初稿（H2=真库实测 / PG·MySQL=文档+形态单测 / DB2·DM·TDengine·MSSQL·Oracle=[文档] 或 [未取证]，DM8/TDengine 按红线无一手文档）。
- [x] **裁定 `xdef:32` 残留处置**：候选 (a) javadoc 注记指向新原语（最小侵入，WI10b 软弃用先例）；(b) 硬删元素（plan-first + `_gen` 失配证据）；(c) 保持现状 + 理由。**倾向性推荐候选 (a)**——先例同文件在案（WI10b 已在同一 `dialect.xdef` javadoc 块落 3 行 `@deprecated` 与 5 条行为注记）、全仓零消费（`_DialectModel.getUpsertHandler` 零调用方）、零行为变化零回归面；推荐不预决，最终以 Phase 0 书面裁定为准。若 Phase 0 裁定接口落点为 (c) handler 挂载，则本项随挂载裁定一并定。
- [x] **裁定 `MERGE`/`UPDATE..FROM` ledger 行方向（EC ⑤b）**：EQL 语法层继续不做；行的处置 = 显式移出 + successor + 触发条件（候选：未来 EQL `MERGE` 语法 WI（plan-first，plan-first 理由 = `.g4` 生成管线）/ "语义由 DAO upsert 原语承载、语法层永久否决"登记）；`UPDATE..FROM` 半行与 WI10b 改档结论的衔接（不重开）。产出 ledger 回写文本草案（Phase 4 落档）。
- [x] **裁定 owner doc 落点**：候选 (a) `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 新节（本 roadmap 既有 owner doc，跨库契约同宿）；(b) 新建 DAO/JDBC 层 guide（需 `INDEX.md` 路由更新）。按"最小 owning doc"规则裁定。
- [x] **裁定提交切分**：stream 迁移 / 文档 各自独立提交，可单独 revert；**原语提交与 H2 三类真库集成测试提交捆绑交付（或三类测试并入原语同一提交）**——`IUpsertHandler` 删除注记"应连同集成测试重新引入"的 commit 粒度硬要求：**不允许出现"原语已提交、H2 集成测试未提交"的中间提交态**（执行形态：Phase 3 的 H2 三类测试与 Phase 2 原语同批完成则并一个提交；分批时原语提交推迟到三类测试就绪一并落）。接口被裁决人推翻时的回滚单位 = 原语+测试捆绑提交 + 迁移提交。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定文档已落 `ai-dev/design/eql-cross-db-compat/`，含：ask-first 裁决人注记（授权依据 + 人可推翻 + 回滚方式）、接口形态三选一终裁（含被拒候选理由）、全部语义点终裁（分叉 ①-④ 逐项）、两枚举逐消费面迁移方案、H2 测试形态、DB2 `-803` 证据口径与逐方言证据等级表初稿、`xdef:32` 处置、ledger 行裁定方向与回写文本草案、owner doc 落点、提交切分。
- [x] 每个 Phase 2/3 执行点都能回答"执行者照此能直接写代码吗"——无留给执行期再发明的设计开口（接口语义、失败行为、回落归属、测试断言口径均已闭）。
- [x] 硬前置核验记录在案（WI10b 未回退）；roadmap WI11 状态行流转已登记（`todo`→`planned` 在本 plan draft review 通过后执行，Phase 4 落档）。
- [x] 本 Phase 纯裁定不改产品代码：`No owner-doc update required`（裁定文档本身即交付物）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 1 - 设计契约文档 + 独立审查

Status: completed
Targets: `ai-dev/design/eql-cross-db-compat/`（新增设计契约文档）；`ai-dev/logs/`

- Item Types: `Decision`（契约定稿）、`Proof`（独立审查）

- [x] 设计契约文档（按 Phase 0 终裁撰写，落 `ai-dev/design/eql-cross-db-compat/`）：接口契约（语义/职责/批量原子性边界——**写语义与方法职责，不写实现代码；精确签名以落地源码为唯一事实**，Minimum Rules #14）；方言策略（逐方言 upsert 语句形态 + 证据等级列）；错误翻译复用契约（`ERR_SQL_DUPLICATE_KEY` 路径 + 两套重复键判定口径的统一结论与**双向差集**处置——差异登记第 5 条，含"翻译器优先 + SQLState-23505 兜底"候选的采纳结论与碰撞测试回归锚）；事务语义（单语句原子 / GENERIC 回落两事务形态的边界与**不保证项**显式化）；nop-stream 消费方契约（volatile 缓存语义、失败行为）；被拒替代方案及理由（只记最终决策，无 "Proposed vs Current"——Minimum Rules #14）。
- [x] **独立子 agent 审查**（不同 `task_id`，对抗性审查含想象性分析：逐 Phase 想象执行、引用路径核验、Exit Criteria 可观测性核验）；Blocker 清零、作者与审查者对可执行性达成共识后文档转 `active`；审查结论（approve / approve-with-nits + 遗留 nits 清单）落档。⚠️ **执行偏差注记（2026-10-05，如实登记）**：环境内全部独立委派机制不可用（workflow 执行不可用 / opencode run 全 provider 服务端错误 ×4 / claude 未登录 / 本地模型服务未运行；证据 `_tmp/wi11/review-round*.md`），已按「AI Must Ask Before」第 2 条询问用户未获应答，按执行指令降级为**结构化对抗性自查**（逐主张 live 核验表 + 想象性执行 3 发现同轮闭环，记录在设计契约 §8）；独立门后移至 EC ⑦ 收口 audit（不同 task_id 硬门，Closure 段按留白纪律未填）+ 人可推翻（推翻路径同 Phase 0 §1）。
- [x] 审查通过 = Phase 2 放行门；未通过则修订重审（迭代对抗性审查流程见 plan guide `When Drafting` 节）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 设计契约文档 active，六项内容齐全（接口契约/方言策略/错误翻译复用/事务语义/消费方契约/被拒替代方案），与 Phase 0 终裁零矛盾。
- [x] 独立审查证据在案（审查者 task_id、结论、Blocker 清零记录）；无"设计开口"残留（凡 Phase 2 需要的决策，文档均给出唯一答案）。⚠️ 审查者 task_id 项按 §8.1 偏差注记 = 结构化自查（独立委派机制不可用，证据在案）；Blocker 清零 = 3 发现（1 Major/2 Minor）同轮闭环。
- [x] `No owner-doc update required`（设计文档本身即交付物；owner doc 落点已由 Phase 0 裁定，Phase 4 执行）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现：原语 + 两枚举迁移

Status: completed
Targets: `nop-persistence/nop-dao/src/main/java/io/nop/dao/`（按 Phase 0 落点）、`nop-stream/nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java`、`nop-stream/nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSink.java`、`nop-dao`/`nop-stream-runtime`/`nop-stream-connector-jdbc` 测试、`ai-dev/logs/`

- Item Types: `Feature`（新原语）、`Fix`（重复实现收拢）、`Proof`（单测红→绿 + 接线验证）

> ⚠️ **执行期勘误（2026-10-05，两处，均已落 owner doc/契约）**：① FALLBACK 策略下方言解析失败的行为由"归入回落路径"修订为**类型化错误 `ERR_DAO_UNKNOWN_QUERY_SPACE`**——`JdbcTemplateImpl.executeUpdate` 无条件解析方言（`:239`），解析失败时后续执行必然失败，"回落"无意义；零新增错误码（既有码）。② plan §4.3 迁移表漏列 `TestJdbcDuplicateKeyDetection`（stream 侧经反射直测已删包私有 `isDuplicateKeyException(Exception)`）——三模块命令 run1 拦截（9 errors NoSuchMethod，`_tmp/wi11/phase2-tri-module.log`），断言面全量迁移至 nop-dao `TestUpsertStatements`（补 2601/1451/消息字样不判定/真错误组/localized 消息 5 组，9→10 用例）后删除该失效文件，覆盖面不减。

- [x] 新原语实现（按 Phase 0/1 终裁）：方言原生单语句 upsert 的生成（PG/MySQL/H2 三形态 + 转义统一 `escapeSQLName`）与执行、批量语义、无原生方言的显式行为（终裁：显式报错或显式回落，**禁静默**——Minimum Rules #24：不允许空方法体/吞异常/placeholder）；重复键判定口径按 Phase 1 契约统一。
- [x] `JdbcCheckpointStorage` 切换：`storeCheckPoint`/`storeSavepoint`/`storeEpochManifest` 三使用点改消费新原语；删除私有 `UpsertDialect`/`resolveUpsertDialect`/`buildNativeUpsert`/`buildNativeUpsertSqlText`/`runNativeUpsert`/`runInsertOrUpdateSeparateTxns`/`isDuplicateKeyException`/`isDuplicateVendorCode`（GENERIC 回落已终裁下沉 nop-dao，全部迁走删除）。
- [x] `JdbcTwoPhaseCommitSink` 切换：keyed upsert 路径改消费新原语（`buildKeyedUpsertSql` 消费静态面，`ensureKeyedColumns` 首批建立时确定策略，volatile 缓存语义按契约保持——`keyedColumns`/`upsertSql` 缓存与漂移即 `ERR_STREAM_STATE_ERROR` 不变，`upsertDialect` 字段删除）；删除私有 `UpsertDialect`/`resolveUpsertDialect`/`buildUpsertSql`。
- [x] 单元测试红→绿：原语侧（三方言 SQL 形态断言——迁移自 `TestJdbcCheckpointStorage#testUpsertSqlShapePerDialect` 的断言面 + 新增 key-only 空更新集/转义/名称解析失败行为/key-only 在 PG `DO NOTHING` 与 MySQL 自赋值两形态的分支覆盖）；stream 侧既有测试迁移后全绿（覆盖面不减）。
- [x] **接线验证（Minimum Rules #23）**：stream 两侧组件在运行时**真实调用**新原语（迁移后的既有行为测试即证明——H2 真执行路径经新原语产出 upsert 并落库；不得只验证原语存在 + stream 存在）。
- [x] `./mvnw test -pl :nop-dao,:nop-stream-runtime,:nop-stream-connector-jdbc -am -T 1C` 全绿（stream 既有失败豁免口径见 Current Baseline 末节，以实跑为准）。**run2 实测**：nop-dao 239|0、nop-stream-connector-jdbc 55|0、nop-stream-runtime 1223 中唯一失败 = `TestStreamConnectorRegistryDiscovery#testCapabilityMatrixAlignedWithDesignAdjudication`（debezium-cdc 期望 SINGLE_INSTANCE 实为 PARALLEL）——与 roadmap Cross-Cutting 登记的基线失败逐字一致，按口径豁免并登记（`_tmp/wi11/phase2-tri-module-run2.log`）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dao --severity high` 零命中；`--module nop-stream-runtime`、`--module nop-stream-connector-jdbc` 同样零命中（三模块 exit 0，日志 `_tmp/wi11/hollow-*.log`）。
- [x] owner-doc 更新归 Phase 4 统一执行，本 Phase 标注 `owner-doc update deferred to Phase 4`；`ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两枚举 grep 零残留：`grep -rn "UpsertDialect" --include="*.java"` 在 nop-stream 两模块 main 源码零命中（私有枚举与配套私有方法已删）；全仓无第二处 upsert 方言分叉实现（test 侧唯一命中 = `TestJdbcCheckpointStorage` javadoc 迁移说明文字）。
- [x] 新原语单元测试落地且绿（三方言 SQL 形态/key-only/转义/解析失败行为），红证据在案（修复前跑失败的输出，`_tmp/` 留档）——`_tmp/wi11/red-testcompile.log`（stash main 后测试编译"找不到符号"）。
- [x] **接线验证**（#23）：`JdbcCheckpointStorage` 与 `JdbcTwoPhaseCommitSink` 的 H2 真执行测试经新原语完成 upsert（调用链从 stream 组件入口到原语到 JDBC，非 mock 断言）——`TestJdbcCheckpointStorage` 28/28（`testDuplicateKeyUpsertUpdatesData` H2 MERGE 真执行经 `storeCheckPoint`→`upsertBatch`；`testGenericDialectFallbackSeparateTransactionUpsert` 碰撞回归锚绿）+ `TestJdbcTwoPhaseCommitSinkDml` 8/8（keyed 真执行经 `buildKeyedUpsertSql`→静态面文本）。
- [x] **无静默跳过**（#24）：无原生 upsert 方言在原语上显式失败或按终裁显式回落（有测试断言该行为——`testNativeOnlyFailsLoudlyOnUnsupportedDialect`/`testFallbackConsumesDuplicateAndUpdatesInFreshTransaction`/`testExecutionFaceRejectsKeyOnlyForm`）；新增每个公共方法/分支不存在空实现/吞异常/placeholder（scan-hollow ×3 exit 0 佐证）。
- [x] `./mvnw test -pl :nop-dao,:nop-stream-runtime,:nop-stream-connector-jdbc -am -T 1C` 全绿（BUILD 口径：本 WI 模块全绿 + 唯一失败与 roadmap 登记基线逐字一致，豁免登记不静默；surefire 汇总在案）。
- [x] `scan-hollow-implementations.mjs` 三模块（nop-dao/nop-stream-runtime/nop-stream-connector-jdbc）`--severity high` 全部退出码 0。
- [x] owner-doc 更新 `deferred to Phase 4`；`ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - H2 真库集成测试 + DB2 `-803` 结构证据

Status: in progress（H2 三类/批量/端到端/DB2/EC① 零改动已落地并随 C3 捆绑提交；唯一未勾项 = 消费方模块专项命令，按 mtime 纪律排在终局收口日实跑——surefire 证据必须晚于最后 commit）
Targets: `nop-persistence/nop-dao/src/test/java/io/nop/dao/`（`JdbcTestCase` 派生集成测试 + 翻译链单测）、`ai-dev/logs/`

- Item Types: `Proof`（真库集成测试）、`Feature`（结构证据测试，若 Phase 0 裁定采纳）

- [x] **H2 三类真库集成测试**（`JdbcTestCase` 载体，按 Phase 0 形态）：插入（首次 upsert 落库、行数 +1、值正确）/ 更新（同键二次 upsert、值更新、行数不增）/ 冲突（真执行撞唯一键 → 翻译链 → 断言 `ERR_SQL_DUPLICATE_KEY` 与 param，而非裸 `SQLException`）——`TestUpsertPrimitiveH2` 9/9（三类 + 回落两腿 + NATIVE_ONLY + key-only 拒绝 + check 约束不误判）。
- [x] **批量语义真执行**：多行批（部分新键部分旧键）一次调用后库中行状态断言（新键插入、旧键更新、总行数正确）；批量原子性边界按设计契约的不保证项断言（`testUpsertBatchMixedNewAndOldKeys`——native 路径单事务批量，契约 §4.3 分档不夸大）。
- [x] **端到端验证（Minimum Rules #22）**：上述测试从新原语 public 入口到 H2 库中行状态/异常错误码完整走通（无 mock、无手工构造中间态）；`JdbcCheckpointStorage`（经 `storeCheckPoint`）与 `JdbcTwoPhaseCommitSink`（keyed 路径）各自至少一条端到端在 Phase 2/3 测试中在位。
- [x] **DB2 `-803` 结构证据**（按 Phase 0 裁定）：**测试落点先显式裁定（本项内完成，二选一并记录理由）——`TestDialect` 内新增用例 vs 独立测试文件**：前者与 roadmap EC ④ 字面"（TestDialect 的真库执行路径）"同文件对齐（倾向形态：真库执行路径与翻译链证据同宿）；后者须在 owner doc 注记与 EC ④ 字面的映射关系，不得留下"EC ④ 字面落空"的口径空窗。裁定后落地：db2 方言模型 + 合成 `SQLException(vendorCode=-803)` 经 `DialectSQLExceptionTranslator` 断言翻译出 `ERR_SQL_DUPLICATE_KEY`；DB2 真库执行 `[未取证]` 登记随测试 javadoc/owner doc 落档。**裁定 = `TestDialect` 内独立用例 `testDb2DuplicateKeyTranslationChain`**（Phase 0 §6.2 终裁；翻译链断言 + 统一谓词一致性断言，绿）。
- [ ] **EC ① 回归复核**：11 个生产调用点 grep 复核零改动；消费方模块（nop-auth-service/nop-batch-dao/nop-retry-engine/nop-sys-dao）既有测试随 Phase 2/3 命令闭包或专项命令全绿（若不在 `-pl` 闭包内，补一次 `./mvnw test -pl :nop-auth-service,:nop-batch-dao,:nop-retry-engine,:nop-sys-dao -am -T 1C` 并留证）。**grep 零改动已完成（11/11 命中零 diff，Phase 0 §0 复核在案）；消费方四模块命令按 surefire mtime 纪律排在终局实跑留证**。
- [x] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 三类测试齐且绿，断言具体结果（行状态/错误码 + param），无"没抛异常"式敷衍断言；冲突类端到端断言 `ERR_SQL_DUPLICATE_KEY` 在案。
- [x] **端到端验证**（#22）：H2 真库从原语入口到行状态完整走通的测试在案；stream 两侧经新原语的端到端在案。
- [x] DB2 `-803` 按 Phase 0 裁定口径处理完毕（结构证据测试绿 + 真库 `[未取证]` 登记；不伪造真库执行证据）。
- [ ] EC ① 回归证据：11 调用点零改动（grep 输出）+ 消费方模块测试绿证据（消费方命令在终局收口日实跑后勾选）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - owner doc + ledger 裁定落档 + 状态流转

Status: completed
Targets: `docs-for-ai/`（按 Phase 0 落点：owner doc 新节 + `INDEX.md`/`docs-for-ai/04-reference/source-anchors.md` 如路由/锚点变化）、`ai-dev/backlog/eql-cross-db-compat-roadmap.md`（ledger `:142` 行 + WI11 状态行 `:95`）、`ai-dev/logs/`

- Item Types: `Fix`（owner doc 与 live baseline 对齐）、`Decision`（ledger 裁定落档）、`Proof`（doc-links 门禁）

- [x] owner doc 按 Phase 0 裁定落点写入新节：原语契约（语义/方言策略表含证据等级列/错误翻译复用/事务语义与不保证项）+ **迁移说明**（`IUpsertHandler` 2026-08-22 删除（commit `3f1111078d`，`ai-dev/audits/check/nop-dao.md:415`）→ 本 WI 重引入：为什么旧 handler 挂载形态不再采用（空壳前科 + 无装配通路）、新原语与旧接口的差异、升级影响 = 旧接口零既有消费者故零迁移面、无原生 upsert 方言的行为变化点）。错误码、方法名逐一可 grep 到 live code。（「DAO 批量 upsert 原语」+「MERGE/UPDATE..FROM：显式移出」两小节；唯一门禁修正：ai-dev 路径引用按 docs-for-ai 边界规则改为 commit 号表述，doc-links 0 error）
- [x] `xdef:32` 处置落地（按 Phase 0 裁定；若裁 javadoc 注记则该注记在本 Phase 与 owner doc 同批落地，元素/绑定零变化，配置加载回归证明）。（XML 注记块落地，元素零变化；回归 = `TestDialectCapabilityMatrix` 2/2 + `TestDialectDeadFlagDisposition` 9/9 + `TestUpsertStatements` 10/10 绿——xdef 解析/方言加载/配置绑定全链）
- [x] **roadmap 修订**（照 WI10a/WI10b 原地注记先例）：ledger `:142` `MERGE`/`UPDATE..FROM` 行按 Phase 0 裁定回写（显式移出 + successor + 触发条件，原地留 2026-10 修订注记）；WI11 状态行流转——`todo`→`planned` 在本 plan draft review 通过后即落，`planned`→`done` 在独立 closure audit 通过后落（**不得提前**）。（ledger 行现位 `:146`，HTML 注记回写；WI11 保持 `planned` 待独立 audit）
- [x] 若 `docs-for-ai/` 路由或实现锚点变化，同步 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`。（新增锚点行 `DB-005`；无新文档故 `INDEX.md` 零变化）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0（当前基线 3 项 pre-existing：`ai-dev/plans/nop-bytecode/06-resource-leak-v1.md:54,83,91`；不顺手修无关文件，只比对新增量）。（实测 0 error / 3 warning = 基线 3 项，新增 0；执行中拦截 1 项自造 BOUNDARY 错误并已修复）
- [x] 方言 XML 零改值时，矩阵快照门禁显式记录"无需更新"；若有改值（Phase 0/3 差异登记触发的例外），按棘轮规则同步快照并独立提交。（**方言 XML 零改值，无需更新**；`TestDialectCapabilityMatrix` 2/2 绿为证）
- [x] `ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] owner doc 新节与 live code 逐项对账无差异（原语方法/错误码/方言策略行均可 grep 到）；迁移说明三要素（旧形态为何弃/新形态差异/升级影响）无留空。
- [x] roadmap ledger 行修订落地且与 Phase 0 裁定一致（原地注记可查）；WI11 状态行流转符合规则（closure audit 前保持 `planned`）。
- [x] `xdef:32` 处置落地且配置加载回归通过（若裁注记）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 新增 broken link = 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] **roadmap WI11 EC ①**：`ERR_SQL_DUPLICATE_KEY` 的 8 文件 / 11 处生产调用点回归不受影响（grep 复核零改动 + 消费方模块测试绿证据）。
- [ ] **roadmap WI11 EC ②**：两个 nop-stream 私有枚举已下线（两模块 main 源码 `UpsertDialect` grep 零命中；无重复实现——全仓 upsert 方言分叉实现仅新原语一处）。
- [ ] **roadmap WI11 EC ③**：新原语有"插入 / 更新 / 冲突"三类**真库（H2）**集成测试且绿；冲突类端到端断言 `ERR_SQL_DUPLICATE_KEY`。
- [ ] **roadmap WI11 EC ④**：DB2 `-803` 按 Phase 0 裁定口径处理（结构证据在案 + 真库 `[未取证]` 登记；`TestDialect` 真库执行路径 = H2 的实际验证不因 DB2 部分降级而缺失）。
- [ ] **roadmap WI11 EC ⑤**：`./mvnw test -pl :nop-dao,:nop-orm,:nop-orm-eql -am -T 1C` 全绿（"全绿"口径见 roadmap Cross-Cutting；⚠️ 该命令闭包不含 stream 下游——stream 侧由 Phase 2 命令 `./mvnw test -pl :nop-stream-runtime,:nop-stream-connector-jdbc -am -T 1C` 覆盖，两条命令在收口日实跑留证；若收口日发现闭包实际包含 stream 模块，其既有失败按 roadmap Cross-Cutting 豁免口径处理，不预写结论）。
- [ ] **roadmap WI11 EC ⑤b**：「明确延后项」`MERGE`/`UPDATE..FROM` 行已在本 WI 裁定并落档（显式移出 + successor + 触发条件；不允许名义 owner）。
- [ ] **roadmap WI11 EC ⑥**：新原语 owner doc 已补，含 `IUpsertHandler` 删除（2026-08-22）→ 重引入的迁移说明。
- [ ] **roadmap WI11 EC ⑦**：独立 closure audit 由**不同 `task_id`** 的子 agent 执行，evidence 写入本 plan 的 `Closure` 段。
- [ ] 所有 in-scope 裁定（接口形态/枚举迁移/H2 形态/xdef 处置/ledger 方向/证据口径）全部落地且与裁定文档一致；不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [ ] 受影响的 owner docs 已同步到 live baseline（Phase 4 全勾）。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）新原语被 stream 两侧组件在运行时真实调用（不只 import/类型存在），（b）端到端路径从原语入口到真库行状态/错误码完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现（无原生方言行为显式化）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/29-eql-wi11-dao-upsert-primitive.md --strict` 退出码 0。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dao --severity high` 退出码 0（`--module nop-stream-runtime`、`--module nop-stream-connector-jdbc` 同样为 0）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict`：本次改动新增 broken link = 0。
- [ ] 方言能力矩阵快照门禁不红（本 plan 方言 XML 零改值则记录"无需更新"；有改值则快照同步 + 门禁绿）。
- [ ] 文本一致性核对：`Plan Status`、各 Phase `Status`、各 Phase `Exit Criteria`、本 `Closure Gates`、`ai-dev/logs/` 收口记录五处一致。

## Deferred But Adjudicated

### 11 个既有 `ERR_SQL_DUPLICATE_KEY` 调用点迁移到新原语

- Classification: `optimization candidate`
- Why Not Blocking Closure: roadmap EC ① 只要求这些调用点**回归不受影响**；它们的"INSERT 撞键后 UPDATE/重试"两段式在语义上是自洽的（部分场景幂等重试与 upsert 不等价，如 `SysSequenceGenerator` 的分配语义），盲目迁移反而引入行为变化。迁移收益评估属后续优化立项。
- Successor Required: `no`
- Successor Path: 优化立项时逐点评估（owner：调用点所属模块）。

### DB2/PG/MSSQL/DM/TDengine 真库执行取证

- Classification: `watch-only residual`
- Why Not Blocking Closure: 真库 CI 只有 H2 是仓库基建现状（非本 WI 可解）；roadmap 红线已定义 `[未取证]` 纪律，证据等级登记使缺口可见而非被掩盖。真库行为取证依赖真库 CI 扩容立项。
- Successor Required: `no`
- Successor Path: 真库 CI/多库测试基建立项时并入（owner doc 证据等级列届时升级）。

### `MERGE` EQL 语法层的上游需求（若 ⑤b 裁定为"显式移出 + 未来语法 WI"）

- Classification: `moved to explicit successor ownership`（若 ⑤b 终裁为"永久否决"则本条不适用，ledger 行直接落否决登记）
- Why Not Blocking Closure: EC ⑤b 只要求 ledger 行**有归属、有触发条件**；`MERGE` 语法是否立项是产品决策（plan-first：`.g4` 生成管线），不由本 WI 代拟。
- Successor Required: `yes`（以 Phase 0 裁定为准）
- Successor Path: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` ledger 行回写文本中的 successor 字段。

## Non-Blocking Follow-ups

- nop-stream 两侧迁移后，`stream_checkpoint`/`stream_epoch_manifest` 建表 DDL 与原语冲突键契约的一致性检查工具化（防建表缺唯一键导致 upsert 退化为多行插入的静默错误——H2/PG 上无唯一键时 `ON CONFLICT`/`MERGE` 行为差异属文档项，owner doc 登记）。
- `TestDialectCapabilityMatrix` 快照中 db2 可加载状态与 WI3 修复后 live 状态的对齐复核（若矩阵测试仍在用旧"不可加载"断言则顺手修正——以 Phase 4 执行时实测为准，属既有测试维护非本 WI 契约面）。

## Risks And Rollback

- **新 public 接口被裁决人推翻（ask-first 门）**：原语与迁移是独立提交——回滚 = revert 原语提交 + revert 迁移提交，stream 侧恢复私有实现（删除是纯 revert，无数据迁移面）。
- **迁移波及 stream checkpoint/sink 稳定路径**：`TestJdbcCheckpointStorage`/`TestJdbcTwoPhaseCommitSinkDml` 既有覆盖面是回归底线，断言只许迁移不许删除；GENERIC 回落语义（两事务边界）若有行为变化必须在 Phase 0/1 契约中显式裁定并落 owner doc 不保证项。
- **两套重复键判定口径统一时的双向语义风险**（差异登记第 5 条）：duplicate-key 登记面实测 **8 个方言 XML**（mysql `1062`/mssql `2601,2627`/oracle `1` 均已登记），差集**双向**——决策表宽在 {MySQL 1022、SQLState-23505 无条件兜底（对未登记方言生效）、`getNextException` 链遍历}，翻译器/XML 宽在 {H2 23001、DB2 -803、DM -6013/-6015、TDengine 1062}。**机械照搬任一单边口径都会静默丢语义**：照搬决策表 = 丢 H2 23001/DB2 -803/DM/TDengine 的精确归一化；照搬翻译器 = 破坏 oracle 方言模型碰撞实例（`testGenericDialectFallbackSeparateTransactionUpsert`：撞键异常 SQLState/vendorCode 23505 不在 oracle XML 的 `{1}`，翻译器口径经 `translateByType` 译成 `ERR_SQL_DATA_INTEGRITY_VIOLATION`，GENERIC 回落绿测试转红）。Phase 0 候选须含"翻译器优先 + SQLState-23505 兜底"，该碰撞测试为回归锚；统一方案若仍有收窄，必须在契约中列明受影响方言与替代证据，不允许静默收窄。
- **`_gen`/xdef 失配风险**：不硬删 xdef 元素、不手改 `_DialectModel`——两约束使该风险结构性消除。

## Closure

Status Note: （完成或关闭时填写：为什么这个 plan 可以关闭）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent，不同 task_id）
- Audit Session: （session ID）
- Evidence:
  - （每条 Exit Criterion 的验证结果 PASS/FAIL + live code path / test name）
  - （每条 Closure Gate 的验证结果 + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/29-eql-wi11-dao-upsert-primitive.md --strict` 退出码
  - Anti-Hollow 检查结果；`node ai-dev/tools/scan-hollow-implementations.mjs`（三模块）退出码
  - Deferred 项分类检查（确认无 in-scope live defect 被降级）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
