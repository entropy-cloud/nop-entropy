# DuckDB 集成选型与 spike 实测裁定（WI0）

> Status: resolved
> Date: 2026-10-01
> Scope: duckdb_jdbc 依赖坐标、DuckDB 引擎行为、既有 duckdb 方言/ORM 接入路径
> Conclusion: 依赖坐标裁定为 org.duckdb:duckdb_jdbc:1.5.6.0（MIT，零传递依赖）；四问全部实测回答；既有方言/DDL/EQL/错误码翻译在真实 DuckDB 上全部实测通过；确认单一顶层模块 nop-duckdb；模块归属、分层契约不变。

## Context

- 决策来源：ai-dev/backlog/duckdb-integration-roadmap.md WI0（Plan 2282）；§19 引擎选型裁定的落地实测
- 仓库既有 duckdb 方言（duckdb.dialect.xml + selector + ddl_duckdb.xlib）此前是纸面支持，本 spike 转实测
- 实测环境：darwin-arm64（Apple Silicon）、Zulu JDK 26、本地 Maven、duckdb_jdbc 1.5.6.0
- spike 项目位于 _tmp/duckdb-spike（gitignored，一次性）：standalone Maven 工程，release=17，依赖 duckdb_jdbc + 平台 2.0.0-SNAPSHOT 快照（nop-core/nop-config/nop-ioc/nop-xlang/nop-dao/nop-orm/nop-orm-eql）+ h2 + junit5

## 依赖坐标裁定

| 项 | 裁定 | 证据 |
| --- | --- | --- |
| 坐标 | org.duckdb:duckdb_jdbc:1.5.6.0 | Maven Central maven-metadata lastUpdated 2026-09-28，1.5.6.0 为最新 |
| 许可 | MIT（pom licenses，上游 duckdb/LICENSE） | jar 内无 LICENSE/NOTICE 文件，以 POM 为准 |
| 传递依赖 | 零（单 jar 自包含 native） | Central POM 无 dependencies 段 |
| native 矩阵 | jar 根路径 4 个：libduckdb_java.so_linux_amd64 / osx_universal / windows_amd64 / linux_arm64 | jar 枚举实测；macOS 为 universal 包不分架构（与 roadmap 预设"darwin-aarch64"命名不同，已更正认知） |
| JDK 兼容 | release=17 编译 + JDK 26 运行全绿 | spike 全部测试 release=17 编译；JDK 26 有 System::load restricted-method 警告（--enable-native-access 可消除；JDK 17 目标无此问题） |
| 驱动自报 | productName=DuckDB，productVersion=v1.5.6，driverName=DuckDBJ | DatabaseMetaData 实测 |

## 引擎四问结论

**问① 进程内连接与 CSV/Parquet 读写：通过。** `jdbc:duckdb:`（内存）与 `jdbc:duckdb:{filePath}`（文件）均可直接 DriverManager 连接；read_csv_auto 摄取 + COPY TO PARQUET/CSV 导出 + read_parquet 回读一致。

**问② memory_limit + temp_directory 外存溢出：通过，但语义有边界。**

- `SET memory_limit` 会被归一化（256MB → 244.1 MiB），`SET temp_directory` 原样生效，均 per-connection 设置
- 数值负载溢出健壮：640MB 排序在 64MB 预算下完成（观测到 4 个溢出临时文件）；hash join 同样完成
- **宽字符串 CSV 扫描排序在 256MB 预算下仍 OOM**（reader 的 pinned block 占满缓冲池；2/2 次观测）——重排序应落在物化表/Parquet 上，或提高预算
- 宽字符串 DISTINCT 聚合：64MB OOM、256MB 完成（各 1 次观测）——溢出需要预算高于工作集的余量
- 所有 OOM 均以 SQLException（"Out of Memory Error: could not allocate block ..."）显式浮出，不会变成 JVM OOM

**问③ 同文件单写者锁冲突：语义明确。**

- 跨进程第二个写者：SQLException，消息 `IO Error: Could not set lock on file "...": Conflicting lock is held in <java path> (PID xxx) by user xxx. See also https://duckdb.org/docs/stable/connect/concurrency`，SQLState=null、errorCode=0 → WI4 据此映射显式任务级错误码与 retry 裁定
- 同 JVM 内同文件多连接共享实例，池化无障碍（Hikari maxPoolSize=1 与 8 均实测工作）
- **同 JVM 同文件不同连接配置（如 username 不同）实测报 "Can't open a connection to same database file with a different configuration than existing connections"**（配置不一致的另一种表现形态未获证据、不作断言）——WI1 必须把同文件的连接配置钉死为一致
- 任务级工作集可用：写入→全部关闭→重开，已提交数据持久；进程被杀后未提交事务回滚（实测仅已提交行存活）

**问④ 不支持平台 native 加载失败语义（源码核查 duckdb-java tag v1.5.6.0）。**

- 加载链全部在 org.duckdb.DuckDBNative：classpath bundled 资源解包 System.load → System.loadLibrary("duckdb_java") → jar 同目录 fallback → 失败抛 IllegalStateException 包 FileNotFoundException("DuckDB JNI library not found, path: ...")
- static 块把一切异常包成 RuntimeException → **首次触达 DuckDBDriver 时以 ExceptionInInitializerError（Error，非 SQLException）浮出**
- 1.5.6.0 无专用 SQLException 子类，全部原生 java.sql.SQLException
- WI1 必须在首次使用边界 catch Throwable/ExceptionInInitializerError 并转换为显式英文错误，不能只 catch SQLException

## 既有方言与 ORM 接入路径实测（全部通过）

| 项 | 结果 | 细节 |
| --- | --- | --- |
| selector 自动选择 | 通过 | productName=DuckDB → duckdb 方言（name/driver/jdbcUrlPattern 断言） |
| ddl_duckdb 建表 | 通过 | init-database-schema 经 DdlSqlCreator 生成 CREATE TABLE 并在 duckdb 文件库执行成功（jdbc.existsTable=true、information_schema 可见） |
| EQL/ORM 查询 | 通过 | QueryBean(sourceName+fields+filter) 经 EQL 编译在 DuckDB 上执行，CRUD + 条件查询正确 |
| 错误码翻译 | 通过 | 引用不存在表 → nop.err.dao.sql.bad-sql-grammar。机制：duckdb 首层异常是通用包装（"Invalid Input Error: Attempting to execute an unsuccessful or closed pending query result"，SQLState=null/errorCode=0），真实 Catalog Error 在 cause 链上，翻译器沿 cause 链做消息正则匹配命中 |
| 数据源默认位 | 通过 | nop.datasource.* 全套（driver/jdbc-url/username/max-size）指向 jdbc:duckdb:{filePath}，Hikari 池正常工作 |
| 命名数据源实体路由 | **可行** | bean 名 nopDataSource_<querySpace> 自动收集为该 querySpace 的事务工厂；实体 querySpace="duck" + query-space-to-dialect 配置 → 实体 CRUD 落 duckdb 文件库，与默认 h2 业务实体同 JVM 并存。roadmap §10 的"业务库 + 分析 DuckDB 双库并存"路由能力成立 |
| 单写者池策略 | 结论 | 进程内多连接共享实例无冲突（池大小 1 或 8 均可）；单写者约束只在跨进程生效。部署形态为多进程时才需要 maxPoolSize=1 或文件独占分配 |

## 模块归属裁定

**确认预设：单一顶层模块 nop-duckdb**（对齐 nop-jq 扁平先例）。证据：全部能力（连接/会话管理、文件数据面、task step）内聚于一个执行层模块；无第二消费者；不拆、不叫 nop-dao-duckdb——职责是执行层而非 DAO/dialect，方言留在 nop-dao 只消费。

## 缺陷与风险清单（后续 WI 输入，不在本 spike 修复）

1. WI4：锁冲突异常形态（见问③）需映射为显式错误码 + retry 语义；同文件连接配置纪律需在连接管理中钉死
2. WI8：duckdb 首层异常为通用包装（问④/错误码翻译节），errorCodes 必须维持消息正则 + cause 链匹配路径；duckdb.dialect.xml 现有单条模式实测命中
3. WI6：低限外存档的边界（问②）——测试矩阵需含"数值溢出通过档"与"宽字符串扫描受限档"两个真实档位
4. WI9 文档：JDK 21+ 运行时的 native access 提示（--enable-native-access=ALL-UNNAMED）
5. 撤回项：spike 中途怀疑 JdbcTemplate.existsTable 在 DuckDB 上失真——复测证实为模型未加载（见下节偏差），existsTable 在 DuckDB 上工作正常，不立项

## 偏差记录

- spike 期间踩到三个 Nop 模块文件约定坑（一次性排查成本，已沉淀给 WI1）：模块 beans 只自动装载 beans/app-\*.beans.xml 前缀；ORM 模型只认 orm/app.orm.xml 固定文件名；@cfg 无默认值时为硬性必填
- 计划预期"memory_limit + temp_directory 外存溢出"为单一通过结论，实测发现算子级边界（宽字符串扫描档），已如实分级记录

## 测试运行摘要

spike 全套 5 测试类 16 用例全绿（darwin-arm64 / JDK 26 / release=17）：

- Phase1DependencyTest：4（连接/native 矩阵/许可入口/release17 类加载）
- Phase2EngineQuestionsTest：6（CSV/Parquet、预算档、设置回读、跨进程锁、文件重开）
- Phase3DialectLevelTest：3（selector、错误码翻译、无锁语义）
- Phase3OrmDuckDefaultTest：2（默认 duckdb 数据源 DDL+CRUD+EQL、进程内多连接）
- Phase3OrmNamedRoutingTest：1（命名数据源实体路由 + h2 并存）

复现方式：按本文档依赖清单重建 standalone 工程（duckdb_jdbc 1.5.6.0 + 平台 2.0.0-SNAPSHOT + h2 + junit5），模块资源需遵守 app-\* 文件名约定；或以 WI1 起的模块内测试为准（持久化验证由后续 WI 承载）。

## 被否决的方案

- 坐标/版本取 1.3.1.0（Maven Central search API 返回的 stale "latest"）：实际 metadata 最新为 1.5.6.0，旧版无理由
- 模块名 nop-dao-duckdb 或挂 nop-dao 子模块：职责是分析执行层而非 DAO/dialect，方言已内置且只消费不搬运（roadmap WI0 预设，实测确认成立）
- 绕过 BOM 在使用模块散写版本：违反 roadmap 依赖管理约束（版本收敛进 nop-dependencies 是 WI1 执行项）
- 试用 read_csv 之外的 Arrow 直连通路：全仓无 Arrow 基础设施，超出本 roadmap 范围硬边界（Purpose 节）

## Conclusion

- 依赖坐标：org.duckdb:duckdb_jdbc:1.5.6.0，MIT，零传递依赖，native 4 平台，release=17 兼容——WI1 按此收敛进 nop-dependencies BOM
- 四问全部有实测答案；既有方言四项实测通过（纸面支持转实测完成）
- 数据源三问：默认位可用、命名路由可行、单写者语义明确
- 模块归属确认 nop-duckdb 单模块
- 后续工作：WI1（模块骨架，按本报告裁定落地 BOM 与模块注册）→ WI2/WI3 → WI4（锁语义，按问③）→ WI5/WI8（验证矩阵，按缺陷清单）

## References

- Plan：ai-dev/plans/2282-duckdb-wi0-jdbc-selection-and-spike.md
- Roadmap：ai-dev/backlog/duckdb-integration-roadmap.md
- 选型来源：ai-dev/analysis/2026-09/2026-09-30-esproc-sqlazy-deep-analysis.md §19
- 既有方言：nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/duckdb.dialect.xml
- native 加载源码：https://raw.githubusercontent.com/duckdb/duckdb-java/v1.5.6.0/src/main/java/org/duckdb/DuckDBNative.java
- duckdb_jdbc POM：https://repo1.maven.org/maven2/org/duckdb/duckdb_jdbc/1.5.6.0/duckdb_jdbc-1.5.6.0.pom
