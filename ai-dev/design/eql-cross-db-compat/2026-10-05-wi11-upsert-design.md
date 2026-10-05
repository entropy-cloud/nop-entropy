# WI11 设计契约 —— `nop-dao` 批量 upsert 原语

> Status: active（审查记录见 §8；Blocker 清零、发现同轮闭环）
> Date: 2026-10-05
> Plan: `ai-dev/plans/29-eql-wi11-dao-upsert-primitive.md`
> Phase 0 裁定: `2026-10-05-wi11-phase0-adjudications.md`（本文档与其零矛盾；语义点终裁以裁定文档为准）
> 纪律：本文只写**语义、职责与使用契约**（Minimum Rules #14）；精确类名/方法签名/参数类型以落地源码为唯一事实。章节中的"静态面/执行面/策略"是职责称谓，不是签名。

## 1. 职责与边界

**原语是什么**：`nop-dao` 的 JDBC 层批量 upsert 能力。给定目标表、有序列集、冲突键列集、更新列集与行批数据，按当前方言生成**单语句原子 upsert** 并执行；方言无原生 upsert 时按调用方显式选择的策略**响亮失败或两事务回落**，无静默默认。

**原语不是什么**：

- 不是 EQL 语法能力（roadmap「明确不做的事」：UPSERT 不进语法层；原语是 JDBC 语句文本生成与执行，零 `.g4` 触碰）。
- 不是 ORM 实体层语义（无 `IEntity`/`CrudOperator` 封装——ledger「批量 DML 抽象」行另行立项）。
- 不替代调用方的事务模型：执行面只在自身调用内开 REQUIRED 传播的单事务（native 路径）或两段独立事务（回落路径）；跨调用、跨批的事务编排归调用方。
- 不校验表 DDL：冲突键列集是否真有唯一约束由建表保证（缺唯一键时各库行为差异属 owner doc 文档项）。

**形态（Phase 0 §2 终裁）**：`IJdbcTemplate` 追加 default 方法承载**执行面**（消费方已有模板引用即可用，default 形态对实现方零破坏）；`io.nop.dao.jdbc` 下新增公共 final 工具类承载**静态面**三职责——方言 upsert 样式解析、原生 upsert SQL 文本生成、重复键异常判定谓词。静态面无状态、线程安全。

## 2. 接口契约（语义）

### 2.1 输入五元组（执行面与静态面共用同一概念集）

| 概念 | 语义 | 约束 |
|---|---|---|
| 目标表 | upsert 目标表名 | 经 `escapeSQLName` 转义后进语句文本 |
| 列集（有序） | 本批写入的全部列，顺序即占位符顺序 | 非空；冲突键列集 ⊆ 列集 |
| 冲突键列集 | 判定"同一行"的唯一键列 | 非空；须对应表上真实唯一约束 |
| 更新列集 | 撞键时被覆写的列 | 允许空集（key-only 形态，§2.4）；更新列集 ⊆ 列集 |
| 行批数据 | 按列集对齐的多行值 | 单行 = 批量为 1 的退化形态，不设独立方法 |

### 2.2 执行面语义

- 按 querySpace 解析方言 → 静态面生成语句文本 → REQUIRED 单事务执行（native 路径）；策略为回落且方言无原生支持时走 §4 回落形态。
- **策略参数是显式枚举、无默认值**（编译期强迫调用方表态，Phase 0 §3.2 终裁）：
  - `NATIVE_ONLY`：方言名无法解析、或方言不在原生支持集（postgresql/pg、mysql/mariadb、h2 三族前缀，大小写不敏感）→ `NopException(ERR_SQL_FEATURE_NOT_SUPPORTED)`（既有错误码 `nop.err.dao.sql.feature-not-supported`，**零新增错误码**；param 携带方言名与 querySpace 供定位）。
  - `FALLBACK_INSERT_UPDATE`：同场景转入 §4 回落形态；~~方言解析抛出的异常同样归入回落路径~~。
    ⚠️ **执行期勘误（2026-10-05，独立 closure audit 发现）**：方言解析失败的行为修订为**类型化错误 `nop.err.dao.unknown-query-space`（`ERR_DAO_UNKNOWN_QUERY_SPACE`）**——`JdbcTemplateImpl.executeUpdate`（`:239`）无条件解析方言，解析失败时后续执行必然失败，"回落"无意义；live 实现 `UpsertExecutors.java:48-55` 两策略下均响亮抛错（plan Phase 2 勘误注记 + owner doc 同步）。
- **批量语义按路径分档（自查修订，闭环混合批正确性）**：
  - **native 路径**：一次调用处理 N 行 = 同一 REQUIRED 事务内逐行执行同一条 upsert 语句文本（每行一组参数值）。行级"插入或更新"由单语句原子性保证；批级一致性由单事务提交/回滚保证（混合批——部分新键部分旧键——一次提交全部生效）。
  - **FALLBACK 路径**：**行粒度**两事务回落——逐行走"INSERT 单独事务 → 判重 → UPDATE 新事务"。不做整批单事务回落：整批 INSERT 中任一行撞键即整批回滚，随后的整批 UPDATE 只能触达已存在行、新键行将静默丢失（行丢失 = 静默错误，禁）。行粒度化的代价是批级原子性不保证（§4.3）。
  - 执行面**要求更新列集非空**：空更新集是静态面 key-only 专用形态（§2.4，sink 场景），执行面收到空更新集 + 任意策略 → `NopException(ERR_SQL_FEATURE_NOT_SUPPORTED)`（同码异参，零新增错误码）。
- SQL 文本带 `SQL` 名与 querySpace（可观测性：日志/错误 param 沿既有 `executeUpdate` 链）。
- 执行面**不回传行级结果**（upsert 无结果集）；失败以异常表达。

### 2.3 静态面语义

1. **样式解析**：输入 `IDialect`，输出该方言的原生 upsert 样式（PG / MYSQL / H2 三族前缀匹配）或"无原生支持"信号。解析只依赖 `IDialect.getName()`（既有两实现的解析面），不引入新配置点。
2. **SQL 文本生成**：输入方言 + 五元组（无行数据），输出该方言的单语句 upsert 文本；**无原生支持时返回 null 作为显式契约信号**（与既有 `buildNativeUpsertSqlText` 的 null 语义一致，测试断言迁移零摩擦）——null 不是静默缺省：两个消费面都必须显式处理（执行面按策略分流，sink 转类型化异常），该处理行为有测试断言。转义见 §2.5。
3. **重复键判定谓词**：输入方言 + 异常，输出 boolean（§3 三段判定）。供执行面回落路径使用，也是公共判定口径的唯一实现（原 stream 侧私有决策表废除）。

### 2.4 key-only 空更新集（Phase 0 §3.5 终裁）

更新列集为空时按方言生成 no-op 更新形态：PG = `ON CONFLICT (..) DO NOTHING`；MySQL/MariaDB = `ON DUPLICATE KEY UPDATE <首个键列> = <首个键列>`（自赋值）；H2 = `MERGE INTO .. KEY` 天然 key-only。

### 2.5 标识符转义（Phase 0 §3.3 终裁）

表名与全部列名（列清单、冲突键清单、更新列、`EXCLUDED.`/`VALUES()` 引用侧）一律经 `IDialect.escapeSQLName`。禁止裸拼接。

## 3. 错误翻译复用契约

### 3.1 `ERR_SQL_DUPLICATE_KEY` 路径不变

错误码定义（`DaoErrors`）与 `DialectSQLExceptionTranslator` 的注册/翻译行为**零改动**（plan Non-Goal）。既有 8 文件 / 11 处"INSERT 撞键后判错误码"调用点的两段式模式不受影响（EC ① 只要求回归）；翻译链照旧：真执行 `SQLException` → `executeUpdate` 内 `dialect.getSQLExceptionTranslator().translate(...)` → 按 XML `<errorCodes>` vendorCode/SQLState 查表归一化。

### 3.2 重复键判定统一口径（执行面回落路径专用，Phase 0 §3.4 终裁）

三段判定 + 双链遍历，判定顺序即优先级：

1. **翻译器优先**：异常链上每个 `SQLException` 经该方言翻译器翻译，结果错误码 == `ERR_SQL_DUPLICATE_KEY` → 判 duplicate（覆盖 8 方言 XML 登记面精确归一化，含原决策表会漏判的 h2 `23001`、db2 `-803`、dm `-6013/-6015`、tdengine `1062`）。
2. **SQLState-23505 无条件兜底**：翻译器未命中时，链上任一 `SQLException` 的 SQLState == `23505` → 判 duplicate（覆盖未登记方言；保住 oracle 方言模型 + H2 `MODE=MySQL` 碰撞路径）。
3. **23000+已知 vendorCode 交接带**：SQLState == `23000` 且 vendorCode ∈ {1062, 1022, 2627, 2601, 1} → 判 duplicate（方言模型缺 `<errorCodes>` 配置时判定不退化；**1022 为决策表独有语义，显式保留**）。
4. **链遍历**：`getCause()` 链与 `getNextException()` 链都走（JDBC batch 驱动语义）。
5. 其余 → 非 duplicate，原样上抛（false negative = 安全方向）。

**双向差集对账结论**：翻译器侧为净增（h2/db2/dm/tdengine 精确归一化），决策表侧全保留（1022 / 23505 兜底 / nextException 遍历）——零净丢失，无需替代证据清单。

**回归锚（硬约束）**：`TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert`（oracle 方言模型 + H2 `MODE=MySQL`，撞键 SQLState/vendorCode 23505 均不在 oracle XML `{1}` 登记面）不得删除、不得削弱；统一口径落地后必须保持绿。

## 4. 事务语义

### 4.1 native 路径（三族方言）

单语句原子 upsert，REQUIRED 传播单事务执行。原子性由数据库对单语句的保证承载；原语不追加批级承诺。

### 4.2 回落路径（`FALLBACK_INSERT_UPDATE` + 无原生方言）

行粒度执行（§2.2），每行：

1. INSERT 在**独立事务**执行；
2. 失败时经 §3.2 谓词判定：非 duplicate → 原样上抛（RuntimeException 包装语义照 `runInsertOrUpdateSeparateTxns` 现状）；
3. duplicate → UPDATE 在**另一个新事务**执行。

两事务分离的必要性（契约的一部分）：PostgreSQL 等引擎任一语句错误即中止整个事务，"同事务内先 INSERT 后 UPDATE"必失败（"current transaction is aborted"）——这是 `JdbcCheckpointStorage` javadoc 在案的设计动机，回落语义必须保持两事务形态。

### 4.3 不保证项（显式化，写入 owner doc）

- **native 路径批级原子**（单事务逐行）；**FALLBACK 路径仅行级原子**（行粒度两事务，批内部分行成功部分行失败的可能窗口存在）——两路径的批量保证不同档，调用方按需选路径；
- 不保证行级隔离或并发 upsert 的收敛顺序（最后写者胜由数据库定）；
- 回落路径的 INSERT→UPDATE 两事务之间存在窗口（非原子），无原生方言上这是能力上限而非缺陷；对一致性有硬需求的场景应使用原生三族方言；
- 冲突键列集与表上唯一约束的一致性由建表 DDL 保证（plan Non-Blocking Follow-up 登记的一致性检查工具化不在本 WI）。

## 5. 方言策略矩阵（证据等级列）

| 方言 | 原生样式 | upsert 语句形态 | 重复键登记（翻译面） | 证据等级 |
|---|---|---|---|---|
| PostgreSQL（pg 前缀） | PG | `INSERT INTO t (cols) VALUES (?..) ON CONFLICT (kcols) DO UPDATE SET c = EXCLUDED.c`；key-only → `DO NOTHING` | `23505` | 形态单测 `[实测]`；语法 `[文档]`（PG 15）；真库 `[未取证]` |
| MySQL/MariaDB（mysql/mariadb 前缀） | MYSQL | `INSERT INTO t (cols) VALUES (?..) ON DUPLICATE KEY UPDATE c = VALUES(c)`；key-only → 首键自赋值 | `1062` | 形态单测 `[实测]`；语法 `[文档]`（MySQL 8.0）；真库 `[未取证]` |
| H2（h2 前缀） | H2 | `MERGE INTO t (cols) KEY (kcols) VALUES (?..)`（Phase 0 §5 终裁载体；ON CONFLICT 已知坑否决） | `23001,23505` | **真库实测**（CI H2 2.4.240 载体，三类测试） |
| 其余全部方言（mssql/oracle/db2/dm/tdengine/duckdb/es/...） | 无原生支持 | 按策略：NATIVE_ONLY 响亮 / FALLBACK 两事务 | 逐方言 XML 登记面（8 XML） | DB2 = 结构证据 `[实测]`（翻译链单测）；MSSQL/Oracle 登记面翻译断言 `[实测]`；DM/TDengine `[未取证]`（红线：无一手文档） |

- 样式解析按方言**名前缀**而非 selector/dbProduct（与既有两实现一致）；新增方言样式属原语演进，不在本 WI。
- "无原生支持"不是能力缺陷登记：MSSQL 有 `MERGE` 但强分叉（强制 `;`、需 `;` 终止符 error 10713），本 WI 三族范围即两既有实现的并集，不做扩面。

## 6. nop-stream 消费方契约

### 6.1 `JdbcCheckpointStorage`（nop-stream-runtime）

- 三使用点（`storeCheckPoint`/`storeSavepoint`/`storeEpochManifest`）改为一次原语调用，策略 = `FALLBACK_INSERT_UPDATE`（现行为等价：三族 native，否则两事务回落）。
- 删除私有 `UpsertDialect`/`resolveUpsertDialect`/`buildNativeUpsert`/`buildNativeUpsertSqlText`/`runNativeUpsert`/`runInsertOrUpdateSeparateTxns`/`isDuplicateKeyException`/`isDuplicateVendorCode`；GENERIC 分支的裸 SQL 字符串拼接一并废除（insert/update 文本由原语生成，转义统一）。
- 行为保持承诺：四方言 SQL 形态、H2 真执行结果、GENERIC 回落两事务边界、异常上抛语义全部不变（§4.3 测试迁移口径锚定）。

### 6.2 `JdbcTwoPhaseCommitSink`（nop-stream-connector-jdbc）

- `resolveKeyedColumns` 首批建立时改调静态面生成 upsert 文本（传入 sink 缓存的 `IDialect`）；**volatile 缓存语义原样保持**：`keyedColumns`/`upsertSql` 首批确定、后续批漂移 = `ERR_STREAM_STATE_ERROR` 类型化错误；`upsertDialect` 字段随迁移删除（职责被静态面吞并）。
- 三族白名单响亮拒绝保留在 sink 调用点：静态面"无原生支持"信号 → sink 抛 `StreamException(ERR_STREAM_STATE_ERROR)`（detail 文案保持"built-in sync scope targets mysql/mariadb, postgresql; h2 for tests"语义）。
- 批执行路径（`connection.prepareStatement(upsertSql)` 逐行绑定、批量分段）**不变**；sink 是静态面消费方，不走执行面（执行面的单发 `executeUpdate` 不替代 JDBC batch 语义）。

### 6.3 模块依赖方向

两 stream 模块均已是 nop-dao 下游（runtime=provided、connector-jdbc=compile），迁移零新依赖边。

## 7. 被拒替代方案（只记最终决策的对照面）

| 被拒方案 | 拒绝理由（详见 Phase 0 裁定文档对应节） |
|---|---|
| 新公共接口与 `IJdbcTemplate` 平行（候选 a） | 无独立实现语义；消费方被迫 cast/二次注入；窄接口零第三方消费者的预死亡形态（§2） |
| 恢复 dialect 模型 `upsertHandler` 挂载（候选 c） | 空壳前科 + `nop-dao` 无 codegen、装配通路不存在 + 把代码契约降级为无人配置的 XML 契约（§2） |
| 方言解析失败全局单一行为 | 必迫使某消费方保留私有分流，重复实现收不拢（EC ②）；策略默认值 = 隐式行为（§3.2） |
| H2 载体用 `INSERT .. ON CONFLICT` | 2.3.232 已知语法坑在案，入选须先真执行自证；MERGE..KEY 已有 2.4.240 绿跑实证，零收益风险（Phase 0 §5） |
| 重复键判定机械照搬翻译器单边口径 | 破坏 oracle 碰撞实例回归锚（23505 兜底语义丢失，绿测试转红）；照搬决策表单边 = 丢 h2 23001/db2 -803 等精确归一化（§3.2 双向差集） |
| GENERIC 回落留在 nop-stream | nop-dao 的 FALLBACK 策略成空壳（Minimum Rules #24 禁令直接命中）；回落与方言判定耦合，是原语契约而非 stream 私有知识（Phase 0 §3.6） |
| DB2 结构证据独立测试文件 | 与 EC ④ 字面"TestDialect 的真库执行路径"同文件对齐更优；独立文件多一层间接零收益（Phase 0 §6.2） |
| xdef 元素硬删 | plan-first + `_gen` 失配（冻结模型），零收益负资产置换（Phase 0 §7） |

## 8. 审查记录（Phase 1 放行门证据）

### 8.1 审查形态与偏差声明（如实登记）

plan 要求"独立子 agent（不同 task_id）对抗性审查"。执行时点环境内**全部独立委派机制不可用**（2026-10-05 实测）：① dynamic workflow 执行不可用（仅编译通过，"workflow execution is not available in this session"）；② `opencode run` 所有 provider（zhipuai-coding-plan/deepseek/opencode zen/`--pure`）持续 `UnknownError: Unexpected server error`（4 次尝试，ref err_9926f496/err_6005b78c/err_677905c9/err_b2da9566，transcript `_tmp/wi11/review-round*.md`）；③ `claude -p` 未登录（Invalid API key）；④ 本地 ollama/lmstudio 未运行（端口探测无响应）。已按「AI Must Ask Before」第 2 条向用户发起询问，未获应答；按既定执行指令（执行 plan 29）继续，**降级为结构化对抗性自查**。

**偏差实质**：自查不满足"不同 task_id"字面要求。补偿面：① 本节逐主张 live 核验 + 想象性执行推演全记录在案（可复核）；② 收口时 EC ⑦ 的独立 closure audit（**不同 task_id 硬门，本 plan Closure 段按留白纪律未填**）将对设计契约与落地的零矛盾性做真正的独立复核；③ 人可推翻——对审查结论有异议时按 Phase 0 §1 同一推翻路径处理（revert 对应提交 + 修订裁决）。

### 8.2 逐主张 live 核验（2026-10-05 实测证据）

| 契约主张 | 核验证据 |
|---|---|
| §6.1 checkpoint 侧枚举/方法清单与三使用点 | `JdbcCheckpointStorage.java`：枚举 `:800`（POSTGRESQL/MYSQL/H2/GENERIC）、`resolveUpsertDialect` `:810`（吞异常回落）、`buildNativeUpsertSqlText` `:859`、`runInsertOrUpdateSeparateTxns` `:895`、`isDuplicateKeyException` `:937`、使用点 `:91/:319/:520` |
| §6.2 sink 侧枚举/方法/缓存 | `JdbcTwoPhaseCommitSink.java`：枚举 `:167`（三值）、`resolveUpsertDialect` `:1039`（响亮 StreamException）、`buildUpsertSql` `:1061`（escapeSQLName + key-only 三形态）、`resolveKeyedColumns` 首批 volatile 写 `:1004-1005`、消费 `:930` |
| §3.2 stage1 与翻译器机制自洽（translateByType 不干扰） | `DialectSQLExceptionTranslator.java:153-239` 逐行推演：`translate` 永不返回 null（vendorCode 未命中→`translateByType`→`UNCATEGORIZED_FAILURE`）；`ERR_SQL_DUPLICATE_KEY` 只可能经 vendorCode/SQLState 表命中产生，`translateByType` 的 `INTEGRITY_VIOLATION` 分支（`:226-227`）产出不同错误码 ⇒ stage1 判等安全、stage2 兜底可达。`translate(action, ex)` 变体在位（`ISQLExceptionTranslator.java:18`） |
| §2.5 统一转义零行为变化 | `DialectImpl.escapeSQLName`（`:547-570`）：合法简单变量名且非保留字 → **原样返回**；仅保留字/非法名/下划线头加引号 ⇒ 现有测试全部标识符（`stream_checkpoint`/`job_id` 等）输出逐字节不变；保留字标识符获得正确引用（净增修复） |
| §3.2 碰撞路径保住（逐步推演） | `TestJdbcCheckpointStorage#testGenericDialectFallbackSeparateTransactionUpsert`（`:480-509`，oracle 方言模型 + H2 `MODE=MySQL`）：撞键 SQLState/vendorCode 23505 → stage1 oracle 翻译器 `{1}` 未命中 → `INTEGRITY_VIOLATION` ≠ DUPLICATE_KEY → stage2 SQLState 23505 命中 ⇒ duplicate。回归锚保持绿 |
| §3.2 异常输入形态 | 撞键异常经 `executeUpdate` 翻译为 `JdbcException`（`io/nop/dao/exceptions/JdbcException.java:12`，`extends DaoException`），原 SQLException 在 cause 链 ⇒ 谓词的 `getCause()` 链遍历可达 |
| §5 方言矩阵登记值 | 8 个 `*.dialect.xml` 逐个开文件比对（h2 `23001,23505`/postgresql `23505`/db2 `-803`/dm `-6013,-6015`/tdengine `1062`/mysql `1062`/mssql `2601,2627`/oracle `1`）——零差异 |
| §2 形态可行性 | `IJdbcTemplate` 既有 default 方法族（`existsTable`/`existsColumn`/...）；实现方全仓仅 `JdbcTemplateImpl`/`JdbcFactory`；`IDialect.getName()`（`IDialect.java:34`）在位 |
| §4.3 测试迁移口径不丢覆盖面 | `TestJdbcCheckpointStorage` 六个 upsert 相关测试逐个对照（形态断言/H2 MERGE 真执行/往返/GENERIC 回落/PG 文档锚）→ §6.3 迁移表逐条映射，零删除；sink 侧 `TestJdbcTwoPhaseCommitSinkDml` 等原地保留 |
| §5 H2 MERGE 转义形态 | sink 既有绿测试已在 H2 上真执行全转义 MERGE（`buildUpsertSql` 全程 `escapeSQLName`）——转义 H2 分支有实证，不需新证明 |

### 8.3 想象性执行发现（同轮闭环）

| # | 严重度 | 发现 | 处置 |
|---|---|---|---|
| 1 | **Major** | FALLBACK 路径混合批（部分新键部分旧键）若整批单事务回落：任一行撞键 → 整批 INSERT 回滚 → 整批 UPDATE 只触达已存在行 → **新键行静默丢失**（静默错误，禁） | **已闭环**：§2.2/§4.2 终裁 FALLBACK = 行粒度两事务（行级正确性优先）；§4.3 不保证项分档（native 批级原子 / FALLBACK 行级原子）；Phase 3 批量测试按此断言 |
| 2 | Minor | §2.3 原文"无原生支持时返回'无原生支持'信号（不返回 null 语义的第三态）"与 §4.3"测试断言迁移零摩擦"矛盾（既有断言是 `assertNull`） | **已闭环**：§2.3 修正为 null = 显式契约信号，两消费面必须显式处理且有测试断言 |
| 3 | Minor | 执行面空更新集（key-only）+ FALLBACK 的行为未定义（UPDATE 无 SET 子句非法） | **已闭环**：§2.2 终裁执行面要求更新列集非空，违反 → `ERR_SQL_FEATURE_NOT_SUPPORTED` 同码异参（零新增错误码） |

### 8.4 结论

**approve**（3 项发现全部同轮闭环，无遗留 nit；Blocker = 0）。放行 Phase 2。
