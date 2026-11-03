# nop-duckdb — 数据文件与本地库的进程内分析执行层

## 模块定位

nop-duckdb 以 [DuckDB](https://duckdb.org)（进程内列存分析引擎，`duckdb_jdbc` 1.5.6.0，MIT）为执行层，覆盖**数据文件（CSV/Parquet）与本地库场景**的分析计算，并接入 nop-task 步骤编排。

分层契约（范围硬边界，改动前先核对平台 AI 开发记录区的 roadmap Purpose 节——按文档边界规则此处不直接链接）：

- **持久层** = 开放数据文件（CSV/Parquet）；`.duckdb` 文件只作任务级工作集/结果缓存（单写者），不作长期归档
- **执行层** = 本模块（进程内 duckdb_jdbc）
- **编排层** = nop-task（`DuckDbSqlTaskStep`）
- **业务库写回** = 一律走 nop-dao/ORM；DuckDB 不作业务库写通道

不做多源联邦；JSON 结构化查询用 nop-jq。

## 依赖与安装

- Maven 坐标 `io.github.entropy-cloud:nop-duckdb`，版本收敛于 nop-dependencies BOM
- `duckdb_jdbc` 自带四平台 native（osx_universal / linux-amd64 / linux-aarch64 / windows）——**不支持平台在首次加载驱动时即显式报错**（`nop.err.duckdb.native-load-failed`，含平台信息），不会运行中诡异失败
- 与 nop-dao 方言体系零改动共存：`nop-dao` 内置 duckdb 方言（`duckdb.dialect.xml` + selector + `ddl_duckdb.xlib`），本模块只消费

## 配置项（IoC `@InjectValue`，bean 创建时一次性解析）

| 配置键 | 说明 | 实测档位提示 |
|--------|------|--------------|
| `nop.duckdb.memory-limit` | 如 `1GB`（DuckDB 规范化显示如 `953.6 MiB`）；空 = 不下发 SET | 64MB 过紧（小负载也会因 ~30MB 连续块分配失败）；强制外存档建议 128MB + 充足 `temp-directory` |
| `nop.duckdb.threads` | 整数线程数；非法值 fail-fast | — |
| `nop.duckdb.temp-directory` | 外存溢出目录；**不可用时溢出写失败 → 步骤层 io-failed**（非静默） | — |

配置键集是封闭的（无第四个键）；识别键 + 非法值 → `nop.err.duckdb.invalid-config`（bizFatal）。

注意：配置在 bean 创建时解析后固定。对同一 `.duckdb` 文件，JVM 内所有连接配置必须一致——本模块用 **config fingerprint 注册表**（+ DuckDB 原生 "different configuration" 翻译）拒绝配置漂移，`nop.err.duckdb.config-conflict`（bizFatal）。测试里裸 `new DuckDbEngine()` 与容器 bean 字段值不同（null vs 空串），指纹即不同。

## 执行层 API

`io.nop.duckdb.IDuckDbEngine`（AutoCloseable，默认实现 `DuckDbEngine`，bean `nopDuckDbEngine`）：

- `openMemory()`：私有内存库连接（多步管线共享表须改用 `openFile`）
- `openFile(filePath)`：打开/创建 `.duckdb` 文件连接（同 JVM 同文件多连接安全；跨进程单写者见硬约束）
- `close()`：关闭全部被跟踪连接并释放注册表引用（幂等兜底；正常消费方经连接自身 close 即完成账本释放，见下节）

`io.nop.duckdb.DuckDbFiles`（静态工具）：

- `readCsv(conn, csvPath, tableName)` / `readParquet(conn, parquetPath, tableName)`：外部文件摄入为表（CSV 走 `read_csv_auto` 嗅探）
- `writeCsv(conn, sql, params, csvPath)` / `writeParquet(...)`：结果导出（CSV 带 HEADER；返回行数）
- `readXlsx(conn, xlsxPath, tableName)`：xlsx 入口桥（复用 nop-tablesaw XlsxReader，经内部临时 CSV 中转；临时 CSV 成功/失败路径均在 finally 中删除，不注册 `deleteOnExit`——见「并发与资源契约」节）

类型语义实测（WI2）：空字段→NULL；前导零串保持 VARCHAR、纯数字串重推断 BIGINT（桥的类型漂移边界）；空串经 CSV 回读坍缩 NULL。**表生命周期 = 连接生命周期**（内存库跨连接不可见表；持久库跨连接可见表但 ATTACH 等实例态随实例销毁——步骤间需锚定连接，见 task step 节）。

### 连接生命周期与账本释放

`openMemory()` / `openFile()` 返回的连接是 **close 桥接包装**：连接 `close()` 时在锁内移出引擎 connections 账本，`openFile` 连接还同步释放 OPEN_FILES 注册表 refs（归零即条目移除）——双载体账本随连接生命周期闭合，消费方（含 `DuckDbSqlTaskStep` 的 try-with-resources 消费链）无需依赖 `engine.close()` 即完成释放。包装 close 幂等（重复 close 只记账一次）；`unwrap` 透传原连接。

`IDuckDbEngine.close()` 保留兜底语义且幂等：从不关闭返回连接的消费方由 `close()` 关闭全部残留连接并释放注册表引用；已走包装 close 的连接不会重复处理。包装 close 失败路径显式记日志（不静默吞掉）。

由此同路径跨 engine 重开（异配置）不再命中残留指纹——`nop.err.duckdb.config-conflict` 只在真实并存的不同配置间触发。本模块账本为**进程内生命周期配对形态**（清理形态 D），族级保留期/TTL/行数上限/批删参数不适用（族级 SSOT = 仓库 ai-dev/design/concurrency/ledger-retention-policy.md 差异条款①，平台内部文档——按 docs-for-ai 边界规则不直接链接）。

## 错误码（`NopDuckDbErrors`，消息全英文）

| 错误码 | 触发 | bizFatal（不自动重试） |
|--------|------|------------------------|
| `nop.err.duckdb.file-locked` | 跨进程单写者锁冲突（消息含路径与持锁者） | ✓ |
| `nop.err.duckdb.config-conflict` | 同文件配置指纹漂移（含 DuckDB 原生翻译路径） | ✓ |
| `nop.err.duckdb.invalid-config` | 配置键值非法 / SET 被拒 | ✓ |
| `nop.err.duckdb.file-not-found` | 数据文件不存在 | ✓ |
| `nop.err.duckdb.table-exists` | 摄入目标表已存在；materialize 的 resultTable 同名已存在（孤儿/陈旧工作集——不静默重建，要求显式清理） | ✓ |
| `nop.err.duckdb.invalid-step-input` | task step 输入校验失败 | ✓ |
| `nop.err.duckdb.engine-closed` | 引擎已关闭后使用（含 close 后迟到的 open——连接入账点世代守卫先关闭后拒绝） | ✓ |
| `nop.err.duckdb.native-load-failed` | 平台 native 缺失（驱动首触边界包装） | ✓ |
| `nop.err.duckdb.connect-failed` | 其余连接失败 | ✗（可重试） |
| `nop.err.duckdb.io-failed` | SQL 执行/文件 IO 失败（含外存溢出失败） | ✗（可重试） |

bizFatal 判定原则：重试无法改变的错误（配置/资源/输入）不重试；io-failed 维持可重试（外存溢出属运行态，可随负载消退）。task step 与 nop-task `<retry>` 的联动：bizFatal 错误即使配置了 retry 也不重试（RetryPolicy 读 `isBizFatal`）。

## task step（nop-task 编排）

`DuckDbSqlTaskStep`，bean `nopDuckDbSqlTaskStep`，经 task 系统官方 `<step type="simple" bean="...">` 扩展点接入：

- 输入（step 局部 scope）：`sql`（必填；参数经 PreparedStatement `?` 绑定防注入）、`params`、`ingestCsvPath`/`ingestParquetPath` + `ingestTable`、`exportPath` + `exportFormat(csv|parquet)`、`resultTable`（与 exportPath 互斥）、`dbPath`（缺省 = 私有内存库；**多步管线必须传同一 dbPath**）
- 输出摘要：`rowCount`/`rowsIngested`/`rowsExported`/`outputPath`（+ `outputTable`）；**步骤间只传路径/表名，不传数据**
- retry/timeout 用 nop-task 一等公民 `<retry>` 子节点 + timeout 属性；bizFatal 错误不被重试
- **跨步骤 ATTACH 等实例态**：步骤间连接顺序开闭，实例仅在连接存活期重叠时共享——需要挂载态时在任务执行期间持一条同 dbPath 锚定连接（WI6 实测）
- **跨重启续跑**：duckdb 步骤参与的任务经 nop-task suspend/ITaskStateStore 机制续跑（步骤状态持久化 + 任务重放启动输入）；`.duckdb` 工作集文件跨"重启"数据一致，resume 需重新打开同 dbPath（WI4 实测语义）。**重放幂等例外**：caller-SQL 不具重放幂等性时，步内提交窗口的 kill/重放将永久失败（表象为 io-failed，掩盖首因）；模块自带样例含非幂等 caller SQL（`CREATE TABLE res AS SELECT ...`），照抄样例组织持久任务须自查幂等性；`resultTable` 路径不受此例外约束（模块侧守卫拒绝重放，见「并发与资源契约」节）

## 并发与资源契约

本节是 nop-duckdb 并发与资源语义的规范声明。裁定权威 = AI 开发记录区 concurrency 设计集 duckdb-engine-race-ctas-guard-graph-envelope-and-edge-attrs-contract.md（按文档边界规则此处不直接链接）；实现锚点见 `../04-reference/source-anchors.md` 的 `DUCKDB-CC-001`。

**引擎 open/close 竞态世代守卫**：`closed` 关闭标志与连接登记共用同一引擎 monitor；`openMemory()`/`openFile()` 入口的关闭检查通过后，若 `close()` 与连接入账交错（含 connect 慢区/设置下发/文件注册窗口），迟到连接在入账点被**先关闭、后拒绝**——底层 JDBC 连接物理关闭、`openFile` 场景文件注册表 refs 归还、WARN 日志（`nop.duckdb.engine-closed-late-connection`）留痕，再抛 `nop.err.duckdb.engine-closed`（bizFatal）。守卫拒绝的连接不进入账本：双载体账本零滞留、单写者文件锁不泄漏（bare 驱动可立即重开同文件）。已知代价：守卫命中的物理关闭在引擎 monitor 内执行，慢关闭期间其他 open/close 调用被阻塞直至返回——接受为失败路径单连接代价。`IDuckDbEngine` 接口文本零改动（守卫语义在实现侧兑现）。

**resultTable 生命周期与 materialize 守卫**：`resultTable` 由唯一一次成功执行创建；materialize 启动时持久 dbPath 上同名表已存在（上次执行 CTAS 已提交但步完成态未落盘的孤儿，或陈旧工作集）→ 显式拒绝 `nop.err.duckdb.table-exists`（bizFatal，不重试），与摄入路径完全同口径——要求调用方显式清理（DROP 后重放或换新 dbPath）。模块**不静默重建**：静默重建会丢弃上次产物、掩盖非确定性 SQL 的重放漂移、并吞掉下游步正在引用的表。**resume/重试行为声明**：跨重启续跑或 `<retry>` 重放未完成步时命中该守卫将显式失败（首因可见、零重试风暴）；多步管线中各步 resultTable 互异（步骤间只传表名不传数据），同表重复物化不是合法用例。内存库路径表随连接消亡，守卫不可达。

**xlsx 桥接临时文件配对**：`readXlsx` 的临时 CSV 创建与生命周期合并——xlsx→CSV 转换段与读回段成功/失败路径均在同一 finally 中删除；**不注册 `deleteOnExit`**（JVM 级 hook 只增不删，且成功路径 finally 已覆盖）。兜底语义变化：进程硬杀（kill -9）时失败路径残留无进程退出兜底——残留为按失败次数有限的临时 CSV，步骤失败告警链是清理触发器；转换失败维持 io-failed 可重试分类（每次重试不再叠加泄漏）。

**caller-SQL 重放幂等契约**：持久 dbPath 下步骤可能被 `<retry>` 装饰器或任务恢复重放执行，调用方自带 `sql` 必须**重放幂等**（如 `CREATE TABLE ... IF NOT EXISTS`、调用方自有的 DROP+CREATE 对）；非幂等 DDL 在 kill/重放窗口内永久失败且表象被 io-failed 包装掩盖（见 task step 节 resume 例外）。`resultTable` 路径是模块侧唯一守卫面（同名即拒绝重放，而非要求调用方幂等）；`executeSql` 直通面零代码改动（模块级前置守卫原理性不可达）。

**Betweenness 超时语义边界（图算法消费面声明）**：`BetweennessCentrality.compute`（nop-graph-core；本平台图分析消费方经既有 2 参入口自动受约束，零调用方改动）墙钟包络配置键 `nop.graph.betweenness-timeout-ms`——未配置回落 **30000ms**（缺省即生产行为声明：现网无上界等待收敛为 30s 上界，合法超长计算以显式错误终止，补救 = 调大该键 / 显式 3 参 overload / 缩减节点集）；配置 >0 取该值（每次调用读取，变更即时生效）；**配置 ≤0 = 显式关闭档**（调用线程同步直跑、无墙钟包络，WARN 留痕——风险为 O(V·E) 无界占用）。显式 3 参传入 `timeoutMs ≤ 0` 非法（IllegalArgumentException，无静默禁用档）。**超时仅放弃等待**：底层 Brandes 计算不可中断，将在后台一次性 daemon 工作线程（`nop-graph-betweenness-` 前缀命名）继续至自然结束——不阻塞 JVM 退出、无线程池/队列驻留；放弃后计算自身的失败不再上报。超时错误 `nop.err.graph.compute-timeout`（bizFatal——同图重试必再超时且每轮空烧超时预算）；等待线程被中断则恢复中断标志 + 取消等待并抛 `nop.err.graph.compute-interrupted`；计算体异常一律解包透出（保留原错误码与 bizFatal，调用方面永不出现 ExecutionException 外壳）。nop-graph 无独立 owner doc（缺口声明随 R-G 治理）：图算法侧契约（Edge.attrs 可见性契约、PageRank 出度预计算、本节超时包络的实现锚点）以 `../04-reference/source-anchors.md` 的 `GRAPH-CC-001` 为准，裁定权威同本节。。**重放幂等例外**：caller-SQL 不具重放幂等性时，步内提交窗口的 kill/重放将永久失败（表象为 io-failed，掩盖首因）；模块自带样例含非幂等 caller SQL（`CREATE TABLE res AS SELECT ...`），照抄样例组织持久任务须自查幂等性；`resultTable` 路径不受此例外约束（模块侧守卫拒绝重放，见「并发与资源契约」节）

## 硬约束

1. **单写者**：同一 `.duckdb` 文件跨进程并行写必然 `file-locked`（bizFatal、可读、不假装可并行）；同 JVM 多连接安全
2. **内存预算**：DuckDB 默认按系统内存比例占用——生产必须经三配置键压低并配 `temp-directory`；测试含低限外存档先例（WI4/WI6）
3. **写边界**：业务表写回一律走 ORM；本模块只做分析计算与文件读写
4. **归档格式**：持久层用开放文件（CSV/Parquet），禁用 `.duckdb` 作长期归档

## 分析侧接入（第二数据源路由）

DuckDB 作为分析数据源与业务 H2/MySQL 并存（WI5 实测先例）：

1. 命名数据源 bean：`nopDataSource_<space>`（如 `nopDataSource_duck`，Hikari + `org.duckdb.DuckDBDriver`，**maxPoolSize=1** 对齐单写者）
2. 方言映射：`nop.dao.config.query-space-to-dialect=duck=duckdb`
3. ORM 实体声明 `querySpace="duck"` 路由到该数据源；`nop.orm.init-database-schema=true` 自动建表（幂等跳过已存在表）
4. 之后 EQL `QueryBean`（含 filter/aggFunc 聚合 + groupBy）即真实下推 DuckDB 执行。注意：QueryBean 聚合须用 `QueryFieldBean.setAggFunc` + `groupBy` 的**分组形态**（无 groupBy 的整体聚合会触发主键 dim 回退生成非法 SQL）；`QueryBean.joins` 是主子表内存对齐语义（NULL 键归一空串），SQL join 语义请走原生 SQL 通道

## 方言说明（nop-dao 内置）

- 生效函数集 50 个（WI8 全量验证：50/50 在真实 DuckDB 执行通过）；`rand`→`random`、`instr`→`strpos`、`current_timestamp`→`current_localtimestamp()`、`year` 覆盖、`uuid`/`uuidv7`/`cosh`/`sinh` 为 duckdb 层定义
- ST_* 空间函数**不在**生效集（原生 DuckDB 不可用，属 spatial 扩展能力；原生可用的 st_astext/st_asbinary 保留）——如需空间能力另行建立 duckdb-spatial 方言变体
- 无锁支持：forUpdate/lockHint 置空、`supportReturningForUpdate=false`——依赖悲观锁 / UPDATE...RETURNING 的路径不适用
- 错误码翻译：catalog "Table with name X does not exist" → `nop.err.dao.sql.bad-sql-grammar`

## 测试入口

`./mvnw test -pl nop-duckdb -am`（67 测：连接管理/连接生命周期账本/关闭竞态世代守卫/CTAS 守卫与重放/文件面与 xlsx 桥接临时文件配对/任务步骤/单写者与续跑/对拍矩阵/外存与端到端/性能基线/方言全量）。上游模块需已 install；`-Dtest` 过滤配 `-am` 时加 `-Dsurefire.failIfNoSpecifiedTests=false`。三方性能基线（duckdb/tablesaw/H2 同数据集聚合）实测记录与复现方式存于平台 AI 开发记录区的 analysis 当月目录（按文档边界规则此处不直接链接）。
