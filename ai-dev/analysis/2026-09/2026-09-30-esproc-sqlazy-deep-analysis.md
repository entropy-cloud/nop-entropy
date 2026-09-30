# esProc SPL 与 SQLazy 深度分析报告

> Status: open
> Date: 2026-09-30
> Scope: 外部项目 `SPLWare/esProc`（master `a52b0c06f`，2026-09-29）与 `SPLWare/SQLazy`（master `b832a1c0d`，2026-09-22）；对照 Nop XLang / `docs-for-ai` 知识库形态
> Conclusion: （研究性结论，无下游 plan）esProc 是一个**工程上重度失衡但运行时设计相当精深**的单体 Java 计算引擎——游标/管道延迟计算、外存算法、网格程序模型都值得读，但零测试、零 CI、god class 成灾，不适合作为可复制的工程范本；SQLazy 则是**"把语言规范写成 LLM 提示词"的罕见完整样本**——仓库本体是 prompt 层（MIT），编译器闭源，其 fail-closed 输出协议与 schema 约束生成的做法是 Nop `docs-for-ai` 最值得借鉴的点。两者是同一厂商（SCUDATA/Raqsoft）的双轨开源策略：一个开源引擎、一个开源规范。

## Context

- 用户要求克隆两个 GitHub 仓库到 `~/sources` 并产出深度分析报告（GitHub 网络不稳定，esProc 用重试脚本克隆成功，SQLazy 一次成功）。
- 两个项目同源：SQLazy README:525 明确写 "SQLazy is built on top of the esProc SPL engine"。esProc 是执行引擎，SQLazy 是 NL→分步 DSL→SQL 的开发工具，Desktop IDE 内部用 esProc 做分步调试。
- 分析动机：判断 (1) 二者架构成色；(2) 对 Nop 平台（XLang、`docs-for-ai`、nop-ai 的 NL→DSL 任务）有哪些可借鉴/需规避的点。
- 素材（本次全部拉到本地）：
  - `/Users/abc/sources/esProc`（`~/sources` 为指向 `/Volumes/data/sources` 的软链），master `a52b0c06f`（2026-09-29），2540 commits，工作区 1.9G / 10057 files
  - `/Users/abc/sources/SQLazy`，master `b832a1c0d`（2026-09-22），60 commits，255 个 md 文件
- 验证方式：源码通读 + git 历史统计 + GitHub API 元数据；**未执行 Maven 构建**（网络不稳定，且仓库自身无 CI 可参照）。

## 调研目标

1. esProc 的整体架构：语言内核、执行模型、存储、分布式、接入面各是什么形态？
2. esProc 的工程质量处于什么水平？哪些是结构性风险？
3. SQLazy 开源仓库里到底有什么？"AI 写步骤、编译器写 SQL" 的说法在仓库层面如何落地？
4. 两者的开源策略差异及内在关系？
5. 对 Nop（XLang / docs-for-ai / nop-ai）哪些可借鉴、哪些是反面教训？

---

## Part I: esProc SPL

### 1. 事实清单

| 维度 | 事实 | 证据 |
|------|------|------|
| 定位 | SPL（Structured Process Language）：面向结构化数据计算的网格编程语言 + 嵌入式计算引擎 | README.md |
| 许可 | Apache-2.0；NOTICE 声明中国专利 ZL 2010 1 0141747.0 由 RAQSOFT 持有并按 Apache 2.0 授权用户使用 | `LICENSE`, `NOTICE` |
| 历史 | 版权行 "Copyright 2008-2021 RAQSOFT Ltd. / 2021-2026 SCUDATA Ltd."，2021-11-09 首次上 GitHub，即有商业产品史 | `NOTICE`, `git log --reverse` |
| 规模 | `src/main/java` 1218 个 Java 文件 / 427,126 行；含 `ide/`（319 文件）与 importlibs 共 524,696 行 | `wc -l` 统计 |
| 体积 | 工作区 1.9G = `.git` 1.1G + `importlibs` 586M + `doc` 136M + `lib` 47M + `src` 14M + `ide` 4.6M + `demo` 3.1M | `du -sh` |
| 活跃度 | 4684 stars / 363 forks / 52 open issues；最后 push 2026-09-29；commits/年：247(21)→662(22)→664(23)→486(24)→332(25)→149(26 至 9 月)——**逐年下降但未停滞** | GitHub API, `git log` |
| 人力 | 16 位贡献者，前三：wunanraq(790) / RQWangXiaoJun(611) / liwei(428)，核心约 3-5 人 | `git shortlog` |
| 构建 | 单 Maven 模块 `com.scudata.esproc:esproc:20260507`，**target Java 1.8**，约 40 个第三方依赖（Spring 仅作集成、POI、log4j2、batik…），**pom 中无 JUnit** | `pom.xml` |
| 测试/CI | **无 `.github/`、无任何 CI 配置；全仓库仅 3 个 `*Test*.java`，且都在 importlibs、均非引擎测试** | `ls -a`, `find` |
| 版本 | git tag 每 2-3 个月一个（V20220922 … V20260507），与 pom 版本号一致 | `git tag` |

### 2. 工程形态：这是一个"发行版仓库"，不是常规源码仓库

```
esProc/
├── src/main/java/      # 引擎核心（427k 行）
├── ide/                # Swing IDE 独立源码树（319 文件，release profile 才并入构建）
├── bin/                # 启动脚本（SPL IDE / Esprocx CLI / ServerConsole）
├── config/             # raqsoftConfig.xml、HttpServer.xml、OdbcServer.xml、菜单/系统配置
├── lib/                # 47M 运行期三方 jar
├── importlibs/         # 586M：23 个数据源适配器（hbase/spark/hive/hdfs/es/kafka/mongo/
│                       #   redis/sap/salesforce/influx/cassandra/dynamodb/olap4j/…），jar 直接入库
├── doc/                # 136M HTML 帮助文档（SPL_Programming/Function_Reference/…）
├── demo/               # 40 个 .splx 示例（en/zh）
├── jdbc/ database/ classes/
└── pom.xml             # 单模块，release profile 才加 ide/ 源码树
```

关键判断：

- **仓库即安装包**：`bin/ + lib/ + config/ + doc/` 是给最终用户解压即用的形态，Maven 构建只是"额外能力"（发布到 Central 的 release profile）。这解释了 1.9G 体积与 `.git` 1.1G——大头是 `doc/Function_Reference/topics`（99,134 次文件变更）这类文档碎片进 git 历史。
- **`.splx` 是二进制网格格式**（magic `RQQR`，自定义 Externalizable 序列化），git 里不可 diff；`demo/en/Nonstructural/p01.splx` 解出来是 `=to(A1)`、`>A2(1)=0`、`for A2` 这类单元格公式 + 注释。
- importlibs 把 586M 第三方二进制 jar 直接提交进仓库，绕开了依赖管理——与 Nop"一切走 Maven 坐标"的路线正相反。

### 3. 运行时架构总览

| 层 | 包 | 规模 | 职责 |
|----|-----|------|------|
| 语言内核 | `com.scudata.expression` | 571 文件 | 表达式解析/求值、477 个函数实现类、运算符 |
| 程序模型 | `com.scudata.cellset` | 61 | 网格（CellSet）与单元格类型、程序网格执行器 |
| 数据计算 | `com.scudata.dm` | 198 | Sequence/Table/Record、游标(41 类)、管道算子(46 类)、SQL 解析/翻译 |
| 存储引擎 | `com.scudata.dw` | 84 | 行存/列存物理表、LZ4、索引、cuboid 预聚合 |
| 事务归档库 | `com.scudata.vdb` | 16 | vdbase() 文件系统式结构化存储（Section/Zone/锁/事务号） |
| 并行集群 | `com.scudata.parallel` | 41 | Cluster/UnitClient-Worker（Socket RPC）、分片、代理 |
| 接入面 | `com.esproc.jdbc` + `server` + `ide` | JDBC / HTTP / ODBC / CLI | 见 §7 |
| 支撑 | common/util/array/thread/excel/chart | — | 类型化数组、日志、i18n（en/zh/zh_TW properties） |

### 4. 语言内核与执行模型

#### 4.1 网格程序模型（程序 = 单元格矩阵）

- `PgmNormalCell.setExpString`（`cellset/datamodel/PgmNormalCell.java:66-107`）按首字符分派单元格类型：`=` 计算格、`>` 执行格、`//` 注释块、`/` 注释格、`:` **NLP 格**、语句格、否则常数格。
- 语句由 `Command`（`cellset/datamodel/Command.java:24`）定义，共 19 种：`if/else/elseif/for/next/break/func/return/end/result/$(SQL)/clear/fork/reduce/goto/channel/try/iff/forr`。注意 `fork/reduce/channel`——**并行与流水线是语言级语句**，不是库 API。
- 执行器是 `PgmCellSet`：
  - `runNext2()`（`PgmCellSet.java:2517`）是解释器主循环：取当前格 → 有 Command 走 switch 分派，否则 `cell.calculate()` → `setNext()` 决定下一格（行优先推进，代码块整体跳过）。
  - `execute()`（`:3275`）跑到第一个 `result` 语句止；`hasNextResult()/nextResult()` 把多次 `result` 变成**结果流**——这正是 JDBC 结果集与 HTTP 多结果输出的基础。
  - `executeFork()`（`:1697`）实现并行块；`interrupt()` 以单元格为单位中断执行（IDE 停止按钮的实现）。
- 解析缓存：每格表达式以 `SoftReference` 挂在单元格上（`PgmNormalCell.java:28`），复用 AST。

#### 4.2 表达式解析：手写单遍扫描 + 优先级归约

链路是 `ParamParser.parse`（`expression/ParamParser.java:363`，按 `;` `,` `:` 三层分隔符递归切参数）→ 叶子进 `Expression`（1756 行）→ `create()`（`Expression.java:387`）**逐字符扫描**，靠上下文（前一节点是不是 Operator）区分 `A1(2)` 元素引用、`A1.(exp)` 循环计算、括号表达式；运算符优先级表硬编码在 `Node.java:27-58`（`PRI_CMA=1 … PRI_BRK=20`），归约后还有 `home.optimize(ctx)` 优化趟与 `checkValidity()` 校验。

- 没有 parser generator（无 ANTLR/javacc 痕迹），全部手写。
- 宏替换 `replaceMacros` 在解析前完成（`Expression` 构造器）。
- 函数注册在 `FunctionLib`（1073 行）：三个 **static HashMap**（`fnMap/mfnMap/dfxFnMap`，`FunctionLib.java:57-63`）在 static 块里 `loadSystemFunctions()`（`:242`）装载；支持运行期从 jar/dfx 追加，同名成员函数用链表 `ClassLink` 串起来。
- 函数实现类共 477 个（`expression/fn` + `mfn` 含子目录）：`mfn` 是成员函数（`A.sort()` 这类点号右侧），`fn` 是全局函数。

#### 4.3 执行上下文

`Context`（`dm/Context.java`）= 父链 + `JobSpace` + dbSessions + `ParamList` 变量表 + `ComputeStack` 计算栈 + 迭代变量；`JobSpace`（`dm/JobSpace.java`）持有全程变量、`ResourceManager`、dfx 函数映射与 `UnitClient` 列表（集群句柄）。线程并行时**重建 Context 并重新解析表达式**（`cursor/ICursor.java` 的 `resetContext` 注释直接写明此约束——表达式对象与 Context 强耦合，是这套解释器的线程模型代价）。

### 5. 数据计算内核（esProc 最有价值的部分）

#### 5.1 数据结构：序列/序表 + 类型化数组

- `Sequence`（`dm/Sequence.java:80`）：**14,724 行的 god class**，265 个 public 方法，既是"有序集合"又是几乎所有运算的宿主；`Table extends Sequence`（`dm/Table.java:26`，带 `DataStruct`），`Record extends BaseRecord`。
- `com.scudata.array`：**手写原始类型数组**——`IntArray` 9,902 行、`LongArray` 9,328、`DoubleArray` 8,174、`BoolArray` 4,819、`ObjectArray` 4,487、`StringArray` 3,730，统一实现 `IArray`（`array/IArray.java`，1-based）。这是 SPL 内存性能的底座：序列可以按列类型紧凑存放，而不是 `Object[]`。

#### 5.2 游标 + 管道的延迟计算（类关系数据库执行器）

这是与 SQL 优化器对应的那套东西，但以库而非编译器形态存在：

- `Operable`（`dm/op/Operable.java:21`）是可挂运算的基类，`select/filterJoin/diffJoin/join/derive/…` 全是 `addOperation(op, ctx)` 的语法糖（`:31-193`）。
- `Operation`（`dm/op/Operation.java`）三件套：`process(Sequence,Context)` 推数据（`:60`）、`isDecrease()` 告诉游标"这个运算会不会减少记录"以决定精确取数（`:67`）、`finish()` 处理流末状态（分组收尾，`:76`）——**这是标准的流式执行协议**。
- `ICursor`（`dm/cursor/ICursor.java:38`）抽象 `get/skipOver`，默认批量 `FETCHCOUNT=9999`（`:43`）、`INITSIZE=99999`（`:42`）；`opList` 挂附加运算，`SinglepathCursor` 把多路游标降为单路以**关闭**并行（`cursor/SinglepathCursor.java` 类注释）。
- 41 个游标实现覆盖外存算法：`BFileCursor/BFileSortxCursor`（文件游标）、`SortxCursor`（外排序）、`MergeJoinxCursor/JoinxCursor2/3/XJoinxCursor`（归并/哈希/主键连接）、`MultipathCursors`（多路并行取数）、`GroupxCursor/GroupxnCursor`（流式分组）、`DBCursor`（直接执行**用户写的 SQL**，`cursor/DBCursor.java:36`）。
- 超内存自动落盘：`FetchResult` 放不下就转 `FilePipe`（`op/FilePipe.java`，写"可分段集文件"）；`Groups` 分组中间结果同样是 `tempResult` Table（`op/Groups.java:54`）。
- 索引结构成套：`HashArrayIndexTable / SortIndexTable / SeqIndexTable / TimeIndexTable / CompressIndexTable / HashPrimaryJoin`（`dm/`）。

**判断**：这套"算子下推到游标 + 落盘 + 多路归并"的设计是 esProc 对标数据库查询执行器的正经实现，工程质量明显高于仓库其余部分。

#### 5.3 存储引擎 `dw` 与 归档库 `vdb`

- `dw`（84 类）：`RowPhyTable/ColPhyTable` 行存列存双形态、`LZ4Util` 压缩、`BlockLink` 块链、`Cuboid`（`dw/Cuboid.java`）做**预聚合立方体**（把 And/Or/Between/Year/Month 过滤条件解析进维度）、索引齐全（`PhyTableIndex` 5,873 行、`TableKeyValueIndex` 5,186 行、`TableFulltextIndex` 全文索引）、过滤下推类（`JoinFilter/TopFilter/ColumnFilter/…`）。
- `vdb`（16 类）：`vdbase()` 产生的"数据库连接"即根目录，`Library/Section/Zone/ArchiveDir` 组织物理库，带**锁（S_LOCKTIMEOUT/S_LOCKTWICE）与事务号（`VDB.java` `LATEST_TX_SEQ` 读最新区位）**——一个嵌入式、可归档的结构化存储。
- `parallel`（41 类）：`Cluster/UnitClient/UnitWorker` 用 Socket 做请求分发，`PartitionManager` 分片，`TableProxy/CursorProxy/RemoteFileProxy` 把远端资源本地化代理，`PerfMonitor/ProxyMonitor` 监控。配合 `fork/reduce/channel` 语句与 `raqsoftConfig.xml` 的 `parallelNum/cursorParallelNum` 使用。

**判断**：esProc 实际上是"单机引擎 + 可选集群层 + 自有存储 + 23 种数据源直连"的小型数据库，卖点（README）"轻量多数据源混合计算替代重型逻辑数仓"与代码结构一致。

### 6. 接入面

| 通道 | 实现 | 证据 |
|------|------|------|
| JDBC | `jdbc:esproc:local:`（`com/esproc/jdbc/InternalDriver.java:191`），Statement 执行 splx，`ResultSet` 4,103 行，靠 `hasNextResult/nextResult` 流式吐结果 | `InternalDriver.java`, `PgmCellSet.java:3308+` |
| HTTP | JDK 内置 `com.sun.net.httpserver`，`SplxHttpHandler.handle`（`server/http/SplxHttpHandler.java:82`）；URL 文法 5 种（`:52-56`），`.splx/.spl/.dfx` 自动补 `()`（`:217`），有 `/shutdown` | `SplxHttpHandler.java` |
| ODBC | 自研 ODBC 服务端（`server/odbc` 2,168 行，`OdbcServer/OdbcWorker/DataTypes`）——让 BI 工具把 SPL 当数据库连 | `server/odbc/*` |
| SQL 翻译 | `sql.sqlparse/sql.sqltranslate`（`dm/sql/SQLUtil.java:17`）做方言互译；**注意：引擎不把 SPL 自动编译成 SQL 下推**，`db.cursor("select …")` 的 SQL 由用户自己写 | `SQLUtil.java`, `DBCursor.java` |
| CLI/IDE | `Esprocx` 命令行跑 splx、`SPL` Swing IDE、`ServerConsole`（`bin/startup.sh`） | `bin/*`, `ide/main/java/.../spl/` |
| 配置 | `config/raqsoftConfig.xml`：`parallelNum/cursorParallelNum/bufSize=65536/blockSize=1M/fetchCount=9999` | 同名文件 |

### 7. AI 时代的动作：NLP 格，但模块闭源

- 2026-09-16 提交 `ef4e166d3 "Add nlp command"`：`: ` 开头的单元格被识别为 NLP 语句（`PgmNormalCell.java:89-90`），执行时**反射加载** `com.scudata.nlp.cmd.Command.toSPL(...)`（`:220-228`，另一路径 `:353-361`），把自然语言**转成 SPL 字符串**再交给 `Expression` 执行。
- 该类**不在开源仓库内**（全仓库无 `nlp` 包、importlibs 也无）——即 NL→SPL 能力是闭源插件，开源侧只留了反射挂点。
- 这与 SQLazy 构成同一战略：**自然语言入口都是闭源的，开源的是确定性执行/规范部分**。

### 8. esProc 工程质量评估

#### P0 级

1. **零测试、零 CI**：pom 无 JUnit，无 `.github/`，3 个 `*Test*.java` 均在 importlibs 且非引擎测试（`TTest_p.java` 是统计函数本体）。一个 42 万行、含外存算法与并发的解释器**没有任何回归安全网**——任何"参考 esProc 写法"的移植都必须自备测试，不能假设其行为被锁定。
2. **`.splx` 二进制不可审查**：程序本体无法 code review / diff / 合并，与 git 工作流天然冲突；这直接导致仓库里大量"文档/网格"以碎片文件形式反复提交（`doc/Function_Reference/topics` 99,134 次变更）。

#### P1 级

3. **god class**：`Sequence` 14,724 行、`DatabaseUtil` 6,554、`PhyTableIndex` 5,873、`ColPhyTable` 5,495、`CharEncodingDetectEx` 4,675、`ResultSet` 4,103。改动成本与理解门槛极高。
4. **全局可变静态注册表**：`FunctionLib` 三个 static HashMap（`FunctionLib.java:57-63`），扩展靠运行期塞类，无接口契约、无并发约束说明。
5. **反射挂点无契约**：NLP 模块用 `Class.forName` 硬编码字符串（`PgmNormalCell.java:222`），开源仓库无法编译验证其存在。
6. **单模块单 jar**：包间无边界（expression 可直接 import dw/cellset/dm），依赖方向靠约定；Java 1.8 target。
7. **许可检查被注释**：`PgmCellSet.java:2747` `// if (curLct == null) checkLicense();`——开源版放开，但商业版开关混在同一行代码里，边界不清。
8. **注释以中文为主、国际化靠 properties**（`commonMessage_{en,zh,tw}` 等），对非中文贡献者不友好；commit message 质量两极（既有规范的 `db.update(...)` 说明，也有 `Modify nlp command`）。

#### 结构性观察

- 提交量 2023 年见顶后逐年下滑（664→486→332→149/9 个月），与核心 3-5 人的结构叠加，**巴士系数低**。
- 但架构本身没有停滞：2026 年仍在加 NLP 格、新 DB 类型、spl 文件格式修改（`a52b0c06f`）。

---

## Part II: SQLazy

### 9. 事实清单

| 维度 | 事实 |
|------|------|
| 仓库内容 | **255 个 md + 2 个 .keep，零代码**；docs（en-US/zh-CN 双语）+ examples + README（545 行） |
| 历史 | 创建 2026-05-09，首提交 2026-05-14 `init: import from sqlazy_test`；60 commits；**单一作者 minimaomi** |
| 社区 | 22 stars / 1 fork / 0 issues / 0 subscribers |
| 许可 | 文档/示例/"AI-oriented SKILLs" 为 MIT；**产品本体（sqlazy.com 与 IDE 安装包）闭源专有**（README:7, :521） |
| 规模 | action 文档 19 个（en 1,525 行 / zh 1,397 行）+ `Action_Common.md`；function 文档 87 个（en 671 行 / zh 741 行）；examples 10 个场景目录 29 个文件 |
| 引擎关系 | README:443-445："No runtime engine is required. SQLazy uses the **esProc SPL engine internally for step debugging only**" |

### 10. 定位与数据流

```
自然语言 ──LLM──> SQLazy 分步语句（19 种 action 流水线，t1..tn 变量锚定）
                     │
                     ├──确定性编译器──> 原生 SQL（19 种方言，README 列出 MySQL/PG/Oracle/
                     │                  Snowflake/BigQuery/MSSQL/DuckDB/Hive/SparkSQL/
                     │                  DAMENG/ClickHouse/DORIS/KingBase/OPENGAUSS/
                     │                  OceanBase×2/PolarDB_PG 等）
                     ├──（Desktop IDE）──> SPL 代码（极端场景的逃生舱）
                     └──分步调试──> 中间表逐步骤执行（借 esProc 引擎）
```

README 的核心主张："The final SQL is generated by **a compiler, not an LLM**"——AI 只写可审计的步骤，最终 SQL 由编译器产出，解决 AI 生成 SQL 的幻觉/不可审查问题。**但编译器本身不在仓库内**，仓库无法独立验证该主张。

### 11. 仓库本体 = 一份形式化的"提示词知识库"

这是本报告对 SQLazy 最重要的定性：**SQLazy 开源的不是软件，是把形式语言规范逐条写成 LLM 可执行的约束**。

#### 11.1 `Action_Common.md`（约 120 行）= 系统提示词

结构与技术要点（均为原文可查）：

1. **角色锁定**："Your sole task is to strictly, normatively, and without deviation convert… Under no circumstances provide explanations"——单任务、零自由发挥。
2. **fail-closed 输出协议**（机器可判定）：
   - 参数缺失 → `0; error, action <name> missing required parameter <param>`（`:6`）
   - 焦点表结构不符 → `0; error, focus table in instruction is incorrect`（`:10`）
   - 引用表未定义 → `0; error, reference table in instruction is incorrect`（`:19`）
   - 全部通过 → `1; <SQLazy_statement>`  
   **错误分支是结构化短码，不是自然语言**——上层程序可以像解析协议一样解析 LLM 输出。
3. **schema 约束生成（防幻觉核心）**（`:3`, `:12-16`, `:21-24`）：示例里的表结构"must not be used to infer actual input"；焦点表/引用表必须命中 `focus_table=XX(*OrderID,OrderDate,…)` 定义；三条"Strictly prohibited"：禁用示例表结构、禁按字段名猜测、禁用常识补全字段。宁可报错，不许编造。
4. **表达式"原样拷贝"规则**：自然语言里被 "expression/formula/表达式" 限定的部分必须连括号原样复制，**"you must never parse, analyze, or process their specific content"**——把表达式当黑盒透传，让 LLM 只做"非表达式部分"的词汇归一化（action 名、参数名、枚举值）。这是对 LLM 最擅长也最易出错的部分做的外科手术式隔离。
5. **简单变量识别**：带 "specified/particular/parameter/variable" 限定的标识符是变量，保留限定词不转换。
6. **算子语义全量灌输**：`F` / `F@` / `T.F` / `#i` / `#` / `F[i]` / `F[a:b]` / `[#i:#j]` 逐条定义（继承自 SPL 的序列号、跨行取值语义），并规定何时必须用 `@` 消歧。

#### 11.2 每个 action 文档 = 规范文档（语法 + 判定规则 + 样例）

- 首节固定为 **`Mode Determination (Highest Priority, Must Execute First)`**（`summarize.md:2`、`pivot.md:3`）：用关键词判定互斥模式（如 summarize 单聚合 vs 双聚合 argmax/argmin；pivot row_to_column vs inverse），并写死失败分支——"If both appear or semantics conflict: output error, **guessing is not allowed**"。
- 参数逐个声明：必选/可选、**参数名省略还是保留**、类型（expression/enum/identifier）、是否支持跨行计算（filter 支持 `F[i]`，summarize 的被聚合表达式不支持）。
- 每个规则配"输入表 → `SQLazy: …` 代码 → 结果表格 → Analysis"四段式样例。
- **规范版本考古直接写进提示词**：`join.md:25`——"the 20260916 spec struck through the Chinese enum value 对位 but left the English word align unstruck; align is nevertheless removed from this language version and **must never be output**"。规格演进史以负约束形式沉淀，避免 LLM 复活已删除的语法。

#### 11.3 语言形态速览

- 19 个 action：`align, calculate, compute, const, derive, distinct, expand, filter, join, list, match, pivot, rank, recurse, segment, set, sort, SQL, summarize`——即"一次一个变换"的关系算子流水线，步骤间用 `t1..tn` 变量与隐式焦点表衔接。
- 87 个函数（数学/日期/字符串/窗口类），文档很薄（en 合计 671 行，平均 7.7 行/函数）。
- 例：`segment ClosePrice; down; as UpFlag`（连续涨跌分段）、`summarize concat Client; with ","; as ClientList; group SellerId`——语义与 SPL 的 `groups/segment` 一脉相承，跨行算子 `F[i]`、序列号 `#` 直接来自 SPL 数据模型。

#### 11.4 examples = 外链库

29 个 md，每个只有两行：`SQLazy Demo: https://www.sqlazy.com/?xxx` + 原题来源（多为 Stack Overflow 真题）。仓库内**无法离线复现或验证任何一个示例**——示例的价值依赖产品在线可用。

### 12. SQLazy 的局限与风险

1. **规范与实现双盲**：仓库只有"前端规范（提示词）"，没有编译器、没有 SQL 生成器、没有测试。读者无法验证"编译器保证无幻觉"这一核心卖点，也无法本地跑通 NL→SQL。
2. **单作者、4.5 个月、22 stars**：活跃度与影响力尚属早期；0 issues 可能意味着没人在用这个 repo 本身（用户直接用 sqlazy.com）。
3. **双语规范漂移已现**：`join.md:25` 的对位/align 事件就是一次中英不同步事故的化石；19+87 个文档 × 2 语言的同步纯靠人工。
4. **示例不可自证**（见 §11.4）、**函数文档过薄**（7.7 行/函数，无边界条件/方言差异说明）。
5. **商业依赖闭源件**：Desktop IDE = esProc 商业版 + 闭源编译器 + 闭源 NLP；开源部分不能独立站立——这是"开源规范吸引生态、闭源产品变现"的刻意设计，不是缺陷，但引用其结论时要清楚验证边界。

---

## Part III: 两者对照与内在关系

### 13. 对照表

| 维度 | esProc SPL | SQLazy |
|------|-----------|--------|
| 本质 | 解释执行的语言 + 计算引擎（运行时） | 分步 DSL 的规范 + SQL 编译工具（开发期，无运行时） |
| 开源物 | 完整引擎源码（Apache-2.0），427k 行 | 完整规范/提示词（MIT），255 个 md，零代码 |
| 闭源物 | IDE 深度功能、NLP 模块（反射挂点）、商业版 | 编译器、Web 产品、Desktop IDE |
| 用户动作 | 写/跑网格程序（`.splx`） | 写/审步骤，拿走编译好的 SQL |
| 验证方式 | 源码可读，但**无测试可跑** | **无法本地验证**（编译器不在） |
| 数据源 | 23+ 直连适配器 + 自有存储 + 集群 | 不碰数据（调试才借 esProc） |
| 语言血缘 | 本体 | 语法/算子（segment、F[i]、#）移植自 SPL |
| 社区 | 4.6k stars，2021 起，年 150-660 commits | 22 stars，2026-05 起，单作者 60 commits |
| 共同点 | 同厂商 SCUDATA/Raqsoft；同为"结构化数据计算应超越 SQL"的立场；NL 入口都闭源 | 同左 |

### 14. 同一厂商的双轨开源策略

- **esProc：开源运行时**——引擎全开（Apache-2.0 + 专利授权），靠 IDE/服务端/NLP/支持服务变现；用"可嵌入、多数据源"切数据库与数仓的边。
- **SQLazy：开源规范**——把"语言怎么定义、LLM 怎么被约束"全部开源（MIT），编译器与产品闭源；用"可审计的 NL→SQL"切 AI SQL 工具赛道。
- 两轨互补：SQLazy 的提示词规范**依赖** esProc 引擎做分步调试，也依赖读者对 SPL 语义的认同；esProc 则借 SQLazy 把 SPL 语义以"SQL 步骤"的形式重新传播一次。

---

## Part IV: 与 Nop 的关系

### 15. 概念对照

| 议题 | esProc SPL | Nop（XLang/XDSL） |
|------|-----------|-------------------|
| 程序载体 | 二进制网格 `.splx`（不可 diff） | 纯文本 XDSL（xml/json/yaml/xlsx 同构，可 delta） |
| 定制机制 | 代码/选项（`@` 选项串） | 可逆计算：Delta/_x 扩展覆写 |
| 计算位置 | 内嵌引擎自算（游标+外存） | 下推数据库为主，引擎做编排与扩展 |
| 语言入口 | 闭源 NLP 格（`:` 前缀 → `toSPL`） | nop-ai / docs-for-ai 规则（开源文档） |
| 调试 | 网格逐步执行、中间格即时可见 | XLangDebugger + 文本级调试 |
| 分发 | 安装包式仓库（lib/importlibs 入库） | Maven 坐标 + 模块分组 |

### 16. 可借鉴（按价值排序）

1. **SQLazy 的 spec-as-prompt 范式（最高价值）**：把 `docs-for-ai` 的规则改写成"语法 + 判定规则 + fail-closed 输出协议 + 四段式样例"的结构——尤其是三条可移植原则：
   - **结构化错误输出**（类 `0; error, <code>` / `1; <结果>`），让 nop-ai 的 NL→XDSL/ORM/接口生成任务输出可被程序判定；
   - **schema 约束生成**：先声明 `focus_table=...`，禁示例结构/字段名猜测/常识补全，宁报错不编造；
   - **黑盒透传**：要求 LLM 原样复制受限片段（如 XLang 表达式），只归一化外壳词汇，隔离 LLM 最易改写语义的部分。
   - 落点建议：nop-ai 相关 skill、`docs-for-ai/` 高频规则（先小范围试点，另开 plan）。
2. **SQLazy 的分步可调试中间态**：步骤间显式中间表 + 逐步执行，与其"AI 写步骤、编译器写 SQL"同构；Nop 若做 NL→编排类生成（工作流/接口编排），"生成可分步验证的中间表示再编译"比"一步生成终产物"更可控——与既有 magic-api 调研（`ai-dev/analysis/2026-09/2026-09-26-magic-api-online-debug-analysis.md`）结论互证。
3. **esProc 游标/管道执行协议**（`Operation.process/isDecrease/finish` + 自动落盘 `FilePipe` + 多路 `MultipathCursors`）：对 nop-stream / 任何需要外存流式处理的模块是教科书式的接口设计（三个钩子的职责划分尤其干净）。
4. **类型化原始数组**（`IArray` 家族）：若 Nop 未来做内存分析/列式缓存，这套（1-based、Externalizable、类型不兼容抛错）是现成设计参考。

### 17. 不可借鉴 / 反面教训

1. **网格二进制格式**：esProc 的 `.splx` 不可 diff/review，直接牺牲了 git 协作与 AI 可读性——反证 Nop 文本优先（XDSL）的正确性；**不要**为"Excel 交互体验"把程序本体二进制化。
2. **零测试零 CI 的 42 万行解释器**：P0 教训。esProc 的游标算法值得读，但**不可直接抄**——没有回归网的行为不可信；任何移植必须自配测试（符合本仓库 Bug Fix Test Coverage Rule）。
3. **发行版式仓库**（586M jar 入库、1.1G `.git`、doc 碎片 99k 次变更）：与 Maven 化、仓库瘦身的路线相反，不学。
4. **`Class.forName` 硬编码插点 + 无接口契约**（NLP 模块）：扩展点应有接口与失败语义，而不是反射字符串。
5. **SQLazy 的"仓库不可自证"**：只开源提示词、不开放编译器与测试，导致所有主张不可复现——Nop 若做同类"规范开源"，应把**可执行的校验器/样例回归**一并开源，避免沦为纯营销物。

### 18. 一句话定位

esProc 值得**读**（游标/外存/存储引擎的实现细节），不值得**抄**（工程基线太低）；SQLazy 值得**抄思路**（spec-as-prompt、fail-closed 协议、schema 约束），不可**引为证据**（核心不可验证）。

## Conclusion

- esProc：架构上是一台完整的小型数据库（语言+执行器+存储+集群+23 数据源），运行时设计（延迟计算游标协议、外存算法、类型化数组、流式结果）是其精华；工程上是反面典型（零测试/零 CI/god class/二进制程序格式/1.9G 发行版式仓库）。活跃度逐年下滑但仍在演进，2026-09 新增闭源 NL→SPL（NLP 格）。
- SQLazy：255 个 md 的"提示词即规范"仓库，fail-closed 输出协议 + schema 约束生成 + 表达式黑盒透传是三处真正有技术含量的设计；但编译器闭源、示例外链、单作者早期项目，其"编译器保证无幻觉"主张在仓库层面不可验证。
- 关系：同厂双轨开源（开源引擎 vs 开源规范），SQLazy 语义承自 SPL、运行时借 esProc。
- 对 Nop：优先借鉴 SQLazy 的 spec-as-prompt 三原则用于 docs-for-ai/nop-ai；借鉴 esProc 的执行协议设计读思路；坚决规避其二进制网格与无测试工程基线。
- 被否决的方案：把 esProc 作为 Nop 数据计算的参照实现整体移植——原因：无测试保障 + 与 Nop"下推数据库 + 文本 DSL"的架构分工冲突。

## Open Questions

- [ ] SQLazy 闭源编译器的 SQL 生成质量（方言覆盖、优化程度）只能通过 sqlazy.com 实测评估，本次未测
- [ ] esProc 的 NLP 模块（`com.scudata.nlp.cmd.Command`）是否随商业发行版分发、其提示词与 SQLazy 的 Action_Common 是否同源，仓库内无线索
- [ ] esProc `dw` 的 cuboid 预聚合与集群层的实际性能水平（无 benchmark、无测试，无法从源码断言）
- [ ] 是否为"spec-as-prompt 三原则在 nop-ai 的落地"单独开 plan（需用户决策）

## References

- 本地克隆：`/Users/abc/sources/esProc`（`a52b0c06f`, 2026-09-29）、`/Users/abc/sources/SQLazy`（`b832a1c0d`, 2026-09-22）
- esProc 关键锚点：`src/main/java/com/scudata/expression/Expression.java:387`（手写解析）、`expression/ParamParser.java:363`、`expression/FunctionLib.java:57`、`cellset/datamodel/PgmCellSet.java:2517,3275`、`cellset/datamodel/PgmNormalCell.java:66,89,220`、`dm/op/Operation.java:60,67,76`、`dm/cursor/ICursor.java:38,43`、`dm/op/FilePipe.java`、`server/http/SplxHttpHandler.java:52,82,217`、`com/esproc/jdbc/InternalDriver.java:191`、`pom.xml`、`NOTICE`
- SQLazy 关键锚点：`README.md:7,443,445,521`、`docs/en-US/Action_Common.md:3,6,10,12,19,21`、`docs/en-US/action/{summarize,pivot}.md:2-3`、`docs/en-US/action/join.md:25`
- GitHub API：`/repos/SPLWare/esProc`、`/repos/SPLWare/SQLazy`（stars/forks/created_at/pushed_at/contributors，2026-09-30 取数）
- 关联分析：`ai-dev/analysis/2026-09/2026-09-26-magic-api-online-debug-analysis.md`
- 外部链接：https://github.com/SPLWare/esProc 、https://github.com/SPLWare/SQLazy 、https://sqlazy.com 、https://c.esproc.com/
