# WI11 Phase 0 前置裁定（plan 29 — DAO 层批量 upsert 原语）

> Status: active
> Date: 2026-10-05
> Plan: `ai-dev/plans/29-eql-wi11-dao-upsert-primitive.md`
> Roadmap: `ai-dev/backlog/eql-cross-db-compat-roadmap.md` WI11 节（Work Item Status 已 `planned`，draft review 已通过）
> 形态先例：`2026-10-03-wi0-gate-adjudications.md`（书面裁决授权模式）、`2026-10-04-wi10b-phase0-adjudications.md`（Phase 0 裁定文档形态）、plan 25 差异登记第 2 条（裁决人注记形态）
> 本文档只记最终决策与被拒理由（Minimum Rules #14）；方法级签名以落地源码为唯一事实（Phase 1 契约文档写语义不写签名）。

## 0. 开工前置核验（硬前置 + 现场状态）

- **WI10b 未回退**：`ai-dev/plans/23-eql-wi10b-in-list-gate-and-dead-flags.md` Plan Status `completed`（Completed: 2026-10-04）；roadmap Work Item Status WI10b 行 = `done`。roadmap WI11 行 = `planned`（draft review 已通过，`todo`→`planned` 流转已落）；WI11 硬前置只有 WI10b，无排队门。
- **工作树**：仅 2 个与本 WI 无关的 untracked 项（`.zcodeignore` 本地工具配置；`nop-code-web/.../NopCodeShardLedger/` 先前构建生成页面目录）+ 5 个构建副产物噪音已按执行指令还原（`git checkout --`，2026-10-05 实测）。无 `.g4`/`EqlParser` 未提交改动（本 WI 零文法触碰，该债与本 WI 无关）。
- **基线锚点复核（2026-10-05 实测，符号定位）**：
  - 两枚举：`JdbcCheckpointStorage.UpsertDialect`（四值含 GENERIC）与 `JdbcTwoPhaseCommitSink.UpsertDialect`（三值）与 plan Current Baseline 逐项一致；checkpoint 侧三使用点（`storeCheckPoint`/`storeSavepoint`/`storeEpochManifest`）与 sink 首批缓存点（`resolveKeyedColumns` 内 `upsertDialect`/`upsertSql` volatile 写入）实测命中。
  - `ERR_SQL_DUPLICATE_KEY`（`nop.err.dao.sql.duplicate-key`）定义于 `DaoErrors`，`DialectSQLExceptionTranslator` static 块注册；8 个方言 XML duplicate-key 登记面逐一复核（值行：h2 `23001,23505` / postgresql `23505` / db2 `-803` / dm `-6013,-6015` / tdengine `1062` / mysql `1062` / mssql `2601,2627` / oracle `1`）。⚠️ 行号漂移登记：h2 登记块现位 `:131`（plan 引 `:119-121`，WI7b 后整体下移）；ledger `MERGE`/`UPDATE..FROM` 行现位 roadmap `:146`（plan 引 `:142`）。内容一致，符号定位为准。
  - 11 处生产调用点（8 文件）grep 逐一命中，与 plan 清单零差异。
  - `dialect.xdef:32` `<upsertHandler>` 元素在位；同文件 javadoc 块已有 WI10b 落的软弃用/行为注记先例（`@supportSavePoint`/`@supportTransaction` 的 `@deprecated` 注记 + `supportUpdateTableAlias` 行为注记）。
  - 碰撞实例测试 `TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert` 在位（oracle 方言模型 + H2 `MODE=MySQL`，GENERIC 回落绿）。
  - `IJdbcTemplate` 实现方全仓仅 `JdbcTemplateImpl` 与 `JdbcFactory`（default 方法形态 BC 安全）。
  - `ERR_SQL_FEATURE_NOT_SUPPORTED`（`nop.err.dao.sql.feature-not-supported`，param `ARG_SQL_TEXT`）既有在案——STRICT 失败语义复用它，**零新增错误码**。

## 1. ask-first 门禁裁决人注记（Required Evidence 登记面）

| 项 | 内容 |
|---|---|
| 门禁 | 「AI Must Ask Before」第 1 条（新增 `nop-dao` public API；roadmap 门控矩阵 WI11 行明文 ask-first。`nop-dao` 非 AGENTS.md 字面 Protected Area，按更严自定门禁处理） |
| 授权依据 | ① **用户对 roadmap 的执行指令**（WI11 在 roadmap 执行队列中，本次执行指令明确要求执行 plan 29 = WI11）；② **roadmap WI11 节证据链**（`ai-dev/audits/check/nop-dao.md` 的删除注记明文"未来实现 MySQL upsert 时应连同集成测试重新引入"，即 roadmap 自身已表达重引入意图；G-1..G-4 均不涉及 upsert 原语，无未覆盖的 Gate） |
| 形态 | **平台负责人经执行指令授权、AI 基于证据链代拟结论、人可推翻**（照 plan 25 差异登记第 2 条 + WI0 gate 裁决文档 G-4 门先例） |
| 推翻方式 | revert 原语+测试捆绑提交（独立提交可回滚）+ revert 迁移提交（stream 恢复私有实现，删除是纯 revert、无数据迁移面），并在本设计目录追加修订裁决 |
| Required Evidence | owner doc（Phase 4，含 `IUpsertHandler` 2026-08-22 删除 → 重引入迁移说明）+ 回归测试（Phase 2/3：原语单测 + H2 三类真库集成测试） |

## 2. 接口形态与落点（三选一终裁）

**终裁：候选 (b) —— `IJdbcTemplate` 直接追加能力（default 方法承载执行面），配套 `io.nop.dao.jdbc` 下公共 final 工具类承载静态面（方言样式解析 / 原生 upsert SQL 文本生成 / 重复键判定谓词）。**

理由：

- 原语的全部依赖面（`getDialectForQuerySpace` / `txn()` / `executeUpdate` / `getSQLExceptionTranslator` 经 `IDialect`）都是 `IJdbcTemplate` 自身（或其可达）成员——原语本质是"模板层已知方言的批量写入语义"，不是独立组件。独立新接口（候选 (a)）没有可独立注入的实现语义：实现只能落 `JdbcTemplateImpl`，消费方（两 stream 组件）持有的都是 `IJdbcTemplate` 引用，独立接口只会强迫 cast 或二次注入。
- `IJdbcTemplate` 已有 default 方法先例（`existsTable`/`existsColumn`/`existsIndex` 族），default 形态对实现方零破坏（实现方全仓仅 `JdbcTemplateImpl`/`JdbcFactory`，已复核）。
- 静态面（SQL 文本生成/谓词）落工具类而非接口 static 方法：与既有测试形态对齐（`TestJdbcCheckpointStorage` 直接静态调用断言 SQL 文本，断言面迁移零摩擦），且不污染核心接口。
- 回滚单位不受影响：原语 + 测试捆绑提交可整体 revert（default 方法删除 = 接口恢复原样，零残留）。

**被拒候选理由（书面）**：

- **候选 (a) 新公共接口（与 `IJdbcTemplate` 平行、实现挂 `JdbcTemplateImpl`）**：如上——无独立实现语义、消费方被迫 cast/二次注入、多一个 public 类型却零接缝收益；且新接口若只被 nop-stream 两处消费，形成"窄接口零第三方消费者"的预死亡形态（与本 roadmap 反死 API 纪律相悖）。
- **候选 (c) 恢复 dialect 模型 `upsertHandler` handler 挂载形态**：维持起草预判，**书面否决**——① 空壳前科（唯一实现 `MysqlUpsertHandler` 恒返回 null、全仓零调用方，2026-08-22 删除注记在案）；② `nop-dao` 无 codegen 管线、handler 的装配通路不存在（`_DialectModel.upsertHandler` 是冻结 String 字段、无类型依赖、无反射装配消费者），复活它需要先发明装配机制，成本远超收益；③ handler 挂载把"方言能否原生 upsert"从代码契约降级为 XML 配置契约，18 个方言 XML 需逐个补配置才算接线——正是"看起来可配置、实则无人配置"的死标志制造模式（WI10b 刚收拢过 14 个）。

## 3. 语义点终裁（两枚举四分叉 ①-④ 逐项 + 批量签名语义 + GENERIC 回落归属）

### 3.1 批量方法签名语义

- 输入五元组：**目标表名 + 列集（有序）+ 冲突键列集 + 更新列集 + 行批数据**（行批 = 按列集对齐的多行值）。单行是批量为 1 的退化形态，不设独立方法。
- 语义：按方言生成**单语句原子 upsert** 并执行；批内多行在一条 JDBC 语句文本下执行（逐行参数绑定，保持调用方事务边界——原语自身只包 REQUIRED 传播的单事务执行，不发明跨批事务承诺）。
- 批量原子性边界（**不保证项**，写入 Phase 1 契约与 owner doc）：原语不承诺"批内部分行失败时整批回滚"超出底层 JDBC/数据库事务的天然语义；不承诺行级隔离；冲突键列集必须真实对应表上唯一约束（原语不校验 DDL——建表缺唯一键时各库 upsert 行为差异属 owner doc 文档项，照 plan Non-Blocking Follow-up 登记）。

### 3.2 分叉 ①：方言名解析失败行为 → **双显式策略，调用方必选其一，原语无静默默认**

终裁：原语契约内置**显式策略参数**（枚举，无 default 值，编译期强迫调用方表态）：

- **`NATIVE_ONLY`（响亮失败）**：方言名无法解析或不属于原生 upsert 支持集（postgresql/pg、mysql/mariadb、h2 三族前缀）→ 抛 `NopException(ERR_SQL_FEATURE_NOT_SUPPORTED)`（复用既有错误码 `nop.err.dao.sql.feature-not-supported`，**零新增错误码**）。
- **`FALLBACK_INSERT_UPDATE`（显式回落）**：同场景不抛错，走"INSERT 单独事务 → 撞键判定 → UPDATE 新事务"两事务回落（语义见 3.4）；方言解析异常（如 querySpace 无方言注册）在回落策略下同样归入回落路径（debug 日志），**不吞不瞒**（日志 + 行为 = 显式契约的一部分，与 Minimum Rules #24 的"吞异常"禁令不冲突：吞的是"用哪种方言"的解析失败，行为是文档化的回落而非静默跳过）。

被拒备选：全局单一行为（只响亮或只回落）——必迫使某一消费方在原语之外保留私有分流（重复实现收不拢，违背 WI11 EC ②）；策略默认值——默认值即隐式行为，违反"不允许静默"。

消费方映射：`JdbcCheckpointStorage` 三使用点 = `FALLBACK_INSERT_UPDATE`（保持现行为：native 可用则 native，否则两事务回落）；`JdbcTwoPhaseCommitSink` = 静态面取 SQL 文本 + 自有 `StreamException(ERR_STREAM_STATE_ERROR)` 响亮守卫（保持现行为：非三族方言建批期即报错，报错语义留在 sink 不上移）。

### 3.3 分叉 ②：标识符转义 → **统一 `escapeSQLName`，无例外**

静态面生成 SQL 文本时表名/全部列名（含冲突键列、更新列、`EXCLUDED.`/`VALUES()` 引用侧）一律经 `IDialect.escapeSQLName` 转义。checkpoint 侧的裸拼接形态废除。回归底线：`TestJdbcCheckpointStorage` 既有 H2 真执行测试必须保持绿（H2 对全小写无名保留字标识符的转义引用不改变语义——既有 `JdbcTwoPhaseCommitSink` 全转义形态在 H2 上已有绿跑实证）。

### 3.4 分叉 ④：重复键判定口径统一 → **翻译器优先 + SQLState-23505 无条件兜底 + 23000+已知 vendorCode 交接带（三段判定，双向差集零丢失）**

终裁形态（ nop-dao 公共谓词，GENERIC 回落路径唯一判定口径）：

1. **翻译器优先**：异常链上每个 `SQLException` 先经该方言 `IDialect.getSQLExceptionTranslator().translate(...)`，翻译结果错误码 == `ERR_SQL_DUPLICATE_KEY` → 判 duplicate（覆盖 8 个方言 XML 登记面：h2 `23001,23505`、db2 `-803`、dm `-6013,-6015`、tdengine `1062`、mysql `1062`、mssql `2601,2627`、oracle `1`、postgresql `23505` 的精确归一化）。
2. **SQLState-23505 无条件兜底**：翻译器未命中时，链上任一 `SQLException` 的 `getSQLState() == "23505"` → 判 duplicate（覆盖未登记方言——**保住 oracle 方言模型 + H2(`MODE=MySQL`) 碰撞路径**：撞键异常 SQLState/vendorCode 均 23505，不在 oracle XML `{1}` 登记面内）。
3. **23000+已知 vendorCode 交接带**：SQLState `23000` 且 vendorCode ∈ {1062, 1022, 2627, 2601, 1} → 判 duplicate（原决策表全量保留：1062/2627/2601/1 虽已在登记面内、但步骤 1 依赖方言模型装配正确，此段保证方言模型缺 `<errorCodes>` 配置时判定不退化；**1022 是决策表独有语义，显式保留防静默收窄**）。
4. 链遍历：`getCause()` 链与 `getNextException()` 链都走（JDBC batch 驱动语义，原决策表既有行为保留）。
5. 非上述形态 → 非 duplicate，原样上抛（false negative 方向失败 = 安全方向，照原决策表 javadoc 纪律）。

**碰撞测试回归锚（硬约束）**：`TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert` 不得删除、不得削弱；迁移后该测试必须保持绿（它就是"统一方案未丢 23505 兜底语义"的活证明）。**双向差集对账**：翻译器侧独有增量（h2 `23001`、db2 `-803`、dm、tdengine 的精确归一化——原决策表会把 h2 `23001` 漏判为非 duplicate）由本终裁**净增**，无收窄；决策表侧独有语义（1022、23505 兜底、nextException 遍历）全部显式保留。零净丢失，无需替代证据清单。

### 3.5 分叉 ③：key-only 空更新集 → **进原语契约**

`updateColumns` 为空集时的方言形态进静态面契约（sink 的 keyed 唯一键场景需要）：PG = `ON CONFLICT (..) DO NOTHING`；MySQL = `ON DUPLICATE KEY UPDATE <首个键列> = <首个键列>` 自赋值 no-op；H2 = `MERGE INTO .. KEY` 天然 key-only。原 checkpoint 侧无此场景（更新集恒非空），零影响。

### 3.6 GENERIC 两事务回落归属 → **下沉 nop-dao**

回落路径（INSERT 单独事务 → 谓词判定 → UPDATE 新事务）整体下沉 nop-dao（原语 `FALLBACK_INSERT_UPDATE` 策略的实现体），`JdbcCheckpointStorage` 的 `runInsertOrUpdateSeparateTxns`/`isDuplicateKeyException`/`isDuplicateVendorCode` 一并迁走删除。理由：回落语义与方言判定/翻译器耦合，是原语契约的一部分而非 stream 私有知识；留 stream 则 nop-dao 原语的 FALLBACK 策略是空壳（Minimum Rules #24 禁令直接命中）。下沉后 checkpoint 侧 GENERIC 分支的 INSERT/UPDATE SQL 文本也由原语生成（转义统一，3.3）——三个使用点的裸 SQL 字符串拼接同步废除。

## 4. 两枚举逐消费面迁移方案

### 4.1 `JdbcCheckpointStorage`（nop-stream-runtime，三使用点同构）

| 消费面 | 现状 | 迁移后 |
|---|---|---|
| `storeCheckPoint` | 四数组 + GENERIC 分流 + 裸 SQL insert/update 文本 | 一次原语调用（FALLBACK_INSERT_UPDATE 策略），insert/update/upsert 文本全部由原语生成 |
| `storeSavepoint` | 同构（9 列含 `savepoint_path`） | 同上 |
| `storeEpochManifest` | 同构（epoch 表，冲突键 `job_id,pipeline_id,epoch_id`） | 同上 |
| 删除物 | `UpsertDialect`/`resolveUpsertDialect`/`buildNativeUpsert`/`buildNativeUpsertSqlText`/`runNativeUpsert`/`runInsertOrUpdateSeparateTxns`/`isDuplicateKeyException`/`isDuplicateVendorCode` | 全部删除（语义下沉 nop-dao，见 3.6/3.4） |
| 行为保持 | `resolveUpsertDialect` 吞解析异常回落 GENERIC | FALLBACK 策略内化同语义（3.2） |

### 4.2 `JdbcTwoPhaseCommitSink`（nop-stream-connector-jdbc，keyed 路径）

- `ensureKeyedColumns`/`resolveKeyedColumns` 首批建立时改调原语静态面取 upsert SQL 文本（传入 sink 已缓存的 `IDialect`）；**volatile 缓存语义保持**：`keyedColumns`/`upsertSql` volatile 字段与"首批确定、漂移即 `ERR_STREAM_STATE_ERROR`"语义原样保留；`upsertDialect` volatile 字段随迁移删除（其唯一消费就是参与建文本，静态面吞并该职责）。
- sink 的批执行路径（`connection.prepareStatement(upsertSql)` 逐行绑定）**不变**——原语执行面走 `jdbcTemplate.executeUpdate` 单发，不替代 sink 的 JDBC batch 语义；sink 只消费静态面的文本生成。
- 删除物：私有 `UpsertDialect`/`resolveUpsertDialect`/`buildUpsertSql`。sink 的三族白名单响亮拒绝（`StreamException(ERR_STREAM_STATE_ERROR)` + detail 文案）保留在 sink 调用点（静态面返回不支持信号 → sink 抛原语义异常）。

### 4.3 既有测试迁移口径（覆盖面不得删除）

| 既有测试 | 断言面 | 迁移口径 |
|---|---|---|
| `TestJdbcCheckpointStorage#testUpsertSqlShapePerDialect` | 三方言 SQL 形态 + GENERIC null | 断言迁移到 nop-dao 原语单测（静态面对等断言；GENERIC null 断言改为 NATIVE_ONLY 策略响亮断言——原语契约无 null 返回） |
| `TestJdbcCheckpointStorage#testDuplicateKeyUpsertUpdatesData` | H2 MERGE 真执行更新 | **原地保留**（`storeCheckPoint` 经新原语的 H2 真执行 = Phase 2 接线验证锚） |
| `TestJdbcCheckpointStorage#testStoreLoadRoundTripOnDuplicateKey` | 撞键往返 | 原地保留 |
| `TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert` | oracle 模型 + H2 两事务回落（碰撞实例） | **原地保留 = 回归锚**（3.4 硬约束） |
| `TestJdbcCheckpointStorage#testPostgreSqlUpsertVerifiedViaSqlShape` | PG 形态文档锚 | 断言随 4.3 第一行迁移（引用点改指原语静态面），锚测试保留 |
| `TestJdbcTwoPhaseCommitSinkDml`/`TestJdbcConnectorFactory` | sink keyed 真执行 | 原地保留（经新静态面的真执行 = 接线验证锚） |

## 5. H2 三类真库测试形态裁定

- **载体**：`JdbcTestCase`（H2 in-mem，`org.h2.Driver` + `jdbc:h2:mem:<uuid>`；本仓解析 H2 **2.4.240**）。
- **H2 upsert 语法终裁：沿用 `MERGE INTO t (cols) KEY (kcols) VALUES (..)`（候选 (a)）**。理由：① 两枚举既有实现一致选择 + `TestJdbcCheckpointStorage.testDuplicateKeyUpsertUpdatesData` 在 2.4.240 真执行绿跑实证（29/29）；② `ON CONFLICT` 候选携带已知坑（`TestJdbcCheckpointStorage` javadoc 在案：H2 2.3.232 对 PG 形态 `ON CONFLICT .. DO UPDATE SET col = EXCLUDED.col` 抛语法错误），入选须先在本仓 H2 版本真执行自证不触坑——为零收益的额外风险（MERGE..KEY 已有实证），**否决**；③ `dialect.xdef` 无 H2 方言语法位问题——原语是 JDBC 语句文本生成，与 EQL 文法无关。
- **建表形态**：测试表带复合唯一约束（覆盖冲突键列集），DDL 由测试自管（`JdbcTestCase` 的 init.sql 机制或测试内执行 DDL）。
- **三类用例**：
  1. **插入**：首次 upsert → 行数 +1、值逐列正确（真执行）。
  2. **更新**：同键二次 upsert → 值更新、行数不增（真执行，H2 MERGE 路径）。
  3. **冲突**：真执行撞唯一键 → 断言翻译链产出 `ERR_SQL_DUPLICATE_KEY`（+ param），而非裸 `SQLException`。落法 = 两腿：① 原语生成的 INSERT 语句对已存在键真执行 → `JdbcException` 错误码断言（端到端翻译链）；② 强制非原生方言（oracle 方言模型 + H2 `MODE=MySQL`，同碰撞测试手法）下 FALLBACK 策略撞键 → 谓词判重 → UPDATE 新事务落库（行状态断言）——即统一判定口径的真执行证明。
- **批量语义真执行**：多行批（部分新键部分旧键）一次调用后逐行断言（新键插入、旧键更新、总行数正确）。
- **GENERIC 回落可 H2 实测**：可（3 类用例第 3 条腿② 即其形态——oracle 方言模型 + H2 是既有测试先例，非 mock）。

## 6. DB2 `-803` 证据口径 + 逐方言证据等级表初稿

### 6.1 结构证据采纳：**是**

db2 方言模型（WI3 第 12 项修复后可实例化）+ 合成 `SQLException(vendorCode=-803, sqlState=23505)` 经 `DialectSQLExceptionTranslator` 断言译出 `ERR_SQL_DUPLICATE_KEY`。这证明登记映射 + 翻译链成立；**DB2 真库执行 `[未取证]`**（真库 CI 无 DB2，红线纪律），登记随测试 javadoc 与 owner doc 落档，不伪造真库证据。

### 6.2 测试落点二选一：**`TestDialect` 内新增独立用例**

理由：与 roadmap EC ④ 字面"（TestDialect 的真库执行路径）"同文件对齐——真库执行路径（H2 载体的三类测试同样落 `JdbcTestCase` 派生面）与翻译链结构证据同宿；`TestDialect` 是 JdbcTestCase 派生类，新增独立 `@Test` 用例不混入既有 `testFunctions` 真库矩阵循环，零既有断言扰动。被拒备选：独立测试文件——须在 owner doc 注记与 EC ④ 字面的映射关系，多一层间接而无收益。

### 6.3 逐方言证据等级表初稿

| 方言 | duplicate-key 登记 | upsert 语句形态证据 | 真库执行 |
|---|---|---|---|
| H2 | `23001,23505`（XML） | 形态单测 `[实测]` + MERGE..KEY 真执行 `[实测]` | **真库实测**（CI 载体） |
| PostgreSQL | `23505`（XML） | 形态单测 `[实测]`（ON CONFLICT 文本断言）；语法 `[文档]`（PG 15 文档） | `[未取证]` |
| MySQL/MariaDB | `1062`（XML） | 形态单测 `[实测]`（ON DUPLICATE KEY 文本断言）；语法 `[文档]`（MySQL 8.0 手册） | `[未取证]` |
| DB2 | `-803`（XML） | **结构证据 `[实测]`**（翻译链单测，6.1） | `[未取证]` |
| MSSQL | `2601,2627`（XML） | 无原生分支（三族外 → 策略行为） | `[未取证]`（翻译链登记面随 8 XML 面覆盖断言） |
| Oracle | `1`（XML） | 无原生分支 | `[未取证]` |
| DM8 | `-6013,-6015`（XML） | 无原生分支 | `[未取证]`（红线：无一手文档） |
| TDengine | `1062`（XML） | 无原生分支 | `[未取证]`（红线：无一手文档） |

## 7. `dialect.xdef:32` 残留 `<upsertHandler>` 处置

**终裁：候选 (a) —— javadoc 注记（WI10b 软弃用先例，同文件同手法）。**

在 `:32` 元素所在位置的 javadoc 注释块加注：该元素为死配置（全仓零消费者、`_DialectModel.getUpsertHandler` 零调用方），新 DAO upsert 原语**不经** dialect 模型 handler 挂载（Phase 0 §2 候选 (c) 否决），元素保留仅 为配置兼容、勿新增消费依赖。

- 被拒候选 (b) 硬删元素：plan-first 且造成"schema 无元素、`_gen` 有字段"失配（`nop-dao` 无 codegen、`_DialectModel` 冻结），零收益负资产置换。
- 被拒候选 (c) 保持现状：零成本但留误导性死配置（正是 2026-08-22 删除 `IUpsertHandler` 的同型问题），注记成本一行。
- 落地时点：Phase 4 与 owner doc 同批（plan Phase 4 第 2 项）；元素/绑定零变化，配置加载回归 = 既有测试随命令绿。

## 8. `MERGE`/`UPDATE..FROM` ledger 行裁定方向（EC ⑤b）

**终裁：显式移出（EQL 语法层不做 `MERGE` 语法）+ successor + 触发条件书面化。**

- EQL 文法层：不做 `MERGE` 语句语法。roadmap「明确不做的事」已裁定 UPSERT 不进语法层（本 WI 即"改走 DAO 原语"的落地）；EQL 语法层 `MERGE` 不在任何 WI 范围。
- **无矛盾声明**：DAO 原语的 H2 分支内部生成 `MERGE INTO .. KEY` 语句文本与"语法层不做 MERGE"不矛盾——一个是 JDBC 语句文本生成（运行时、不经 EQL 文法），一个是 EQL 文法备选（`.g4` 生成管线）。
- **successor**：未来 EQL `MERGE` 语法 WI（plan-first——`.g4` 生成管线 Protected Area）。
- **触发条件**：出现需要 EQL 层表达 `MERGE`（条件 upsert / `WHEN MATCHED THEN DELETE` / `BY SOURCE`）语义的真实产品需求，且 G-1 已裁决目标方言最低版本（已知分叉：PG 15 `MERGE` 无 `BY SOURCE` 需 17；MSSQL `MERGE` 强制终止 `;`，error 10713；`postgresql.dialect.xml` 无版本门）。
- **`UPDATE..FROM` 半行**：与 WI10b 改档结论衔接（`supportUpdateFromJoin` 已改档为契约登记 + 文法响亮失败），本 WI 不重开；ledger 回写文本中该半行注记指向 WI10b 终裁文档。
- **回写文本草案**（Phase 4 落档用，原地注记不删原文）：

> ⚠️ 2026-10-05 WI11 收口注记（原地注记，不删原文；裁定正文 = `ai-dev/design/eql-cross-db-compat/2026-10-05-wi11-phase0-adjudications.md` §8）：本行已由 WI11 裁定 = **显式移出**。`MERGE`：EQL 语法层不做（roadmap「明确不做的事」UPSERT 不进语法层的同批裁定；跨库 upsert 语义由 WI11 落地的 `nop-dao` 批量 upsert 原语承载——JDBC 语句文本生成，非 EQL 文法）。Successor = 未来 EQL `MERGE` 语法 WI（plan-first：`.g4` 生成管线）；触发条件 = 出现 EQL 层 `MERGE` 语义的真实产品需求 + G-1 目标方言版本门裁决（已知分叉：PG 15 无 `BY SOURCE` 需 17 / MSSQL 强制 `;` / oracle `WHEN MATCHED THEN DELETE` 三版文档确认存在）。`UPDATE..FROM`：维持 WI10b 改档终裁（语法不存在 = 文法响亮失败 + 契约登记，见 `2026-10-04-wi10b-phase0-adjudications.md` §1-§3），不重开。

## 9. owner doc 落点

**终裁：候选 (a) —— `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 新节。**

理由：本 roadmap 既有 owner doc（WI0-WI10b 的方言契约/能力门/文档引导全宿于该文件），跨库契约同宿；"最小 owning doc"规则下新建 DAO/JDBC guide（候选 (b)）意味着 `INDEX.md` 路由新增 + 单节文档撑起一个顶层文件，成本高零收益。被拒候选 (b) 书面理由如上；若未来 DAO 层契约增殖到单节装不下，再立项拆分（非本 WI 范围）。

新节内容面（Phase 4 落地）：原语契约（语义/方言策略表含证据等级列/错误翻译复用/事务语义与不保证项）+ `IUpsertHandler` 2026-08-22 删除（commit `3f1111078d`）→ 重引入迁移说明（旧 handler 挂载形态为何不再采用/新原语差异/升级影响 = 旧接口零既有消费者故零迁移面）。

## 10. 提交切分（捆绑纪律）

| 提交 | 内容 | 约束 |
|---|---|---|
| C1（Phase 0） | 裁定文档 + plan Phase 0 勾选 + daily log | 纯文档 |
| C2（Phase 1） | 设计契约文档 + 审查证据 + plan Phase 1 勾选 + daily log | 纯文档 |
| **C3（Phase 2+3 捆绑）** | **nop-dao 原语 + 单测 + H2 三类真库集成测试 + DB2 -803 结构证据测试** | **硬要求：原语与 H2 三类测试同批交付，禁止"原语已提交、集成测试未提交"中间态**（`IUpsertHandler` 删除注记的 commit 粒度要求）。单模块可编译（nop-stream 尚未迁移，编译不受影响） |
| C4（Phase 2 收尾） | nop-stream 两枚举迁移 + 既有测试迁移 + plan Phase 2/3 勾选 + daily log | 独立可 revert（回滚 = revert C3 + revert C4，stream 恢复私有实现） |
| C5（Phase 4） | owner doc + xdef 注记 + roadmap ledger 回写 + WI11 状态流转（`planned` 保持，`done` 待独立 audit）+ plan Phase 4 勾选 + daily log | 纯文档 |

Phase 3 的 H2 测试与 Phase 2 的原语实现按 C3 捆绑落地（实现顺序：先原语实现 + 单测，紧接 H2 三类测试，两步完成合为 C3 一个提交；DB2 结构证据测试同批并入 C3）。执行期若发现方言 XML 必须补值（Phase 0 预判为零改值），走差异登记 + 独立提交 + 矩阵快照复核。

## 11. 可执行性自查（Exit Criteria 第 2 条）

Phase 2/3 每个执行点的"照此能直接写代码吗"逐项回答：

- 接口语义：五元组输入 + 策略枚举 + 静态面三职责（§2/§3.1/§3.2）——**闭**。
- 失败行为：NATIVE_ONLY 用 `ERR_SQL_FEATURE_NOT_SUPPORTED`（既有码，§0 复核在案）；FALLBACK 内化吞解析异常语义（§3.2）——**闭**。
- 回落归属：nop-dao（§3.6），stream 删除清单逐方法点名（§4.1/§4.2）——**闭**。
- 重复键判定：三段判定 + 链遍历 + 回归锚（§3.4）——**闭**。
- key-only 形态：三方言逐个定义（§3.5）——**闭**。
- 转义：统一 `escapeSQLName`（§3.3）——**闭**。
- H2 载体：MERGE..KEY，语法坑输入在案（§5）——**闭**。
- DB2 落点：TestDialect 内独立用例（§6.2）——**闭**。
- 测试迁移：逐测试点名（§4.3）——**闭**。
- 提交粒度：C3 捆绑硬约束（§10）——**闭**。
