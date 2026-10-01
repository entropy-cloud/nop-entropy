# 2282 DuckDB WI0 — duckdb_jdbc 选型与既有方言实测 spike

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI0；`ai-dev/analysis/2026-09/2026-09-30-esproc-sqlazy-deep-analysis.md` §19
> Related: 后续 WI1–WI9 计划（未创建）

## Purpose

在写任何生产代码之前，用真实实验回答 DuckDB 集成的全部可行性问题：duckdb_jdbc 依赖坐标与版本、既有 duckdb 方言（纸面支持）能否真实跑通、ORM 数据源接入路径是否成立、四个 spike 关键问题（进程内连接/外存/单写者锁/native 缺失语义）的真实行为。产出落月度目录的裁定报告，作为 WI1–WI9 的决策依据。

## Current Baseline

- 仓库已内置 DuckDB SQL 方言（commit c6c09978c8，2025-11-14）：`nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/duckdb.dialect.xml`（extends postgresql，driver `org.duckdb.DuckDBDriver`，jdbcUrlPattern `jdbc:duckdb:{filePath}`，forUpdate/lockHint 置空，supportReturningForUpdate=false）+ `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/selector/duckdb.selector.yaml`（productName=DuckDB 自动选择）+ `nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/ddl_duckdb.xlib`（152 字节 DDL 片段）
- 错误码翻译：`nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectSQLExceptionTranslator.java` 存在，duckdb.dialect.xml 定义了 1 条 bad-sql-grammar 模式；翻译器有测试 `TestDialectSQLExceptionTranslator.java`
- 多数据源通路已存在：`DaoConfigs.java` 有 `nop.datasource.jdbc-url/username/password/driver-class-name` 配置键；`nop-dao/.../txn/impl/DefaultTransactionManager.java:83` 有 `setDataSourceMap(Map<String, DataSource>)`；`nop-persistence/nop-orm-geo/src/test/java/io/nop/orm/geo/TestMultiDataSource.java` 是既有多数据源测试参照
- 数据源工厂：`nop-dao/.../jdbc/datasource/` 下有 `SimpleDataSource`/`DynamicDataSource`/`HikariDataSourceFactory`/`DataSourceConfig`
- 全仓无任何 `org.duckdb` 依赖坐标（`nop-kernel/nop-dependencies/pom.xml` 未收录）；无任何 `jdbc:duckdb` 实际连接测试
- 本地 `.m2` 已有 `io.github.entropy-cloud` 2.0.0-SNAPSHOT 全套快照（含 nop-dao/nop-orm），可供 spike 项目依赖
- Maven Central `org.duckdb:duckdb_jdbc` 最新版本 1.5.6.0（maven-metadata lastUpdated 2026-09-28）；本机 darwin-arm64（native 矩阵内）
- 本机 JDK 26（Zulu），项目编译目标 `maven.compiler.release=17`

## Goals

- 裁定 duckdb_jdbc 依赖坐标、版本（预期收敛 1.5.6.0，以实测为准）、许可（MIT 待验证）、native 平台矩阵（从 jar 根路径 libduckdb_java.so_{os}_{arch} 实测枚举；1.5.x 预期为 linux_amd64 / osx_universal / windows_amd64 / linux_arm64，macOS 为 universal 包不再分架构）、与 `maven.compiler.release=17` 的兼容性
- 四问实测：①进程内连接 + CSV/Parquet 读写；②memory_limit + temp_directory 外存溢出行为；③同文件单写者锁冲突行为（跨进程）；④不支持平台 native 加载失败的报错语义
- 既有方言转实测：selector 自动选择、ddl_duckdb 建表可执行、EQL/ORM 查询翻译在 DuckDB 上执行、错误码翻译命中
- ORM 接入路径实测：`nop.datasource.*` 指向 `jdbc:duckdb:` 可建池；命名 dataSourceMap 可否作实体路由（多数据源并存）；Hikari 池 + 单写者文件的连接策略结论
- 模块归属裁定确认或推翻：单一顶层模块 nop-duckdb（预设，可按实测推翻并记录理由）
- 交付裁定报告到 `ai-dev/analysis/2026-10/`，含依赖坐标裁定与全部实验证据

## Non-Goals

- 不写任何生产代码：不改根 pom、不动 nop-dependencies BOM（BOM/模块注册是 WI1 的执行项）
- 不做 nop-duckdb 模块骨架、连接管理、task step、文件数据面（WI1–WI3）
- 不做多源联邦、不做 Arrow 基础设施（roadmap Purpose 硬边界）
- 不在本 WI 内修复方言实测暴露的 dialect.xml 缺陷——缺陷记录进报告，修复带回归归 WI8（`Fix` 类）
- 不搭 CI、不建 JMH 基准（WI7）

## Scope

### In Scope

- `_tmp/duckdb-spike/` 下独立 spike Maven 项目（gitignored，不提交）：依赖 duckdb_jdbc + 本地平台快照 + JUnit5，承载全部实验
- Maven Central 元数据核查、jar 内 native 库枚举、许可文件核查
- 四问实验、方言四项实测、数据源接入三问实测
- 裁定报告 `ai-dev/analysis/2026-10/2026-10-01-duckdb-jdbc-selection-and-spike-adjudication.md`
- 当日 `ai-dev/logs/2026/10-01.md` 记录

### Out Of Scope

- 一切生产模块/BOM/根 pom 变更（WI1 承接）
- 方言缺陷修复（WI8 承接，报告中登记）
- docs-for-ai 使用文档（WI9 承接）

## Execution Plan

### Phase 1 - 依赖坐标与 native 矩阵实测

Status: completed
Targets: `_tmp/duckdb-spike/`（spike pom + 依赖解析）

- Item Types: `Decision`

- [x] 建立 spike Maven 项目（release=17），解析 org.duckdb:duckdb_jdbc:1.5.6.0（或实测确认的更优稳定版），记录许可证与传递依赖
- [x] 枚举 jar 内 native 库清单（jar 根路径 libduckdb_java.so_{os}_{arch}，1.5.6.0 预期含 linux_amd64 / osx_universal / windows_amd64 / linux_arm64），对照 roadmap 预设并记录差异
- [x] 本机（darwin-arm64, JDK 26）经 release=17 编译的最小类完成进程内连接 + `SELECT 42` 验证
- [x] 核查许可：jar 内 LICENSE/NOTICE 或上游仓库声明，确认为 MIT

Exit Criteria:

- [x] spike 项目 `mvn -q test` 在本机全绿，编译目标 release=17
- [x] native 平台矩阵清单（实测枚举结果）已记录，与 roadmap 预设一致或差异已说明
- [x] 许可结论（预期 MIT）有证据出处
- [x] No owner-doc update required：Phase 1 为依赖实测，不改 live 行为
- [x] `ai-dev/logs/2026/10-01.md` 已更新本 Phase 结果

### Phase 2 - DuckDB 引擎四问 spike

Status: completed
Targets: `_tmp/duckdb-spike/`（spike 测试类）

- Item Types: `Proof`

- [x] 问①：进程内 JDBC 连接上完成 CSV read_csv_auto 摄取 + COPY TO CSV/Parquet 导出 + 回读比对
- [x] 问②：SET memory_limit（压低档，如 128MB）+ SET temp_directory 后执行超内存聚合/排序，验证外存溢出可完成且不 OOM；记录行为与配置语法
- [x] 问③：跨 JVM 进程打开同一 `.duckdb` 文件（ProcessBuilder 双进程），记录第二个写者得到的异常类型/消息/SQLState；同 JVM 内两次连接同文件路径的行为一并实测（共享实例 or 冲突）
- [x] 问④：核查 duckdb-java tag v1.5.6.0 源码的 native 加载路径（全部在 org.duckdb.DuckDBNative：classpath bundled 资源 → System.loadLibrary → jar 同目录 fallback → 失败抛 IllegalStateException 包 FileNotFoundException "DuckDB JNI library not found"；该版本无 NativeLoader 类、无专用 SQLException 子类），记录不支持平台加载失败的异常语义（异常类型与消息形态），作为 WI1 显式报错包装的依据。取证：raw.githubusercontent.com/duckdb/duckdb-java/v1.5.6.0/... 或 jar 内 javap 反编译兜底
- [x] 记录 `.duckdb` 文件作任务级工作集的可用性证据（打开-写入-关闭-重开-数据仍在）

Exit Criteria:

- [x] 四问各有可复现实验证据（命令/测试类 + 观察到的行为记录），写入裁定报告
- [x] 问①②在本机实测通过（读回数据一致；低 memory_limit 下完成且无 OOM）
- [x] 问③锁冲突异常的完整形态（异常类/消息片段/SQLState）已记录，供 WI4 定义任务级错误语义
- [x] 问④有源码级出处（类名/行为描述）
- [x] No owner-doc update required：Phase 2 为引擎行为实测，不改 live 行为
- [x] `ai-dev/logs/2026/10-01.md` 已更新本 Phase 结果

### Phase 3 - 既有方言与 ORM 接入路径实测

Status: completed
Targets: `_tmp/duckdb-spike/`（依赖本地平台快照的测试类）

- Item Types: `Proof`

- [x] 最小依赖与脚手架：spike 项目引入 nop-core/nop-config/nop-ioc/nop-xlang/nop-dao/nop-orm/nop-orm-eql（EQL 是独立模块 nop-persistence/nop-orm-eql，缺它 EQL 实测无法编译）+ h2 + duckdb_jdbc + junit-jupiter；CoreInitialization 配置照抄 nop-persistence/nop-orm-geo/src/test/java/io/nop/orm/geo/TestMultiDataSource.java（setTestConfig("nop.orm.init-database-schema", true) + initialize/destroy），测试资源结构（application.yaml + _vfs/nop/test/_module + orm.xml）照抄 nop-orm-geo 测试资源；先跑方言级实测（selector/DDL/错误码翻译，仅 nop-dao 即可）再跑 ORM 级实测，分步降险
- [x] selector 实测：以 productName=DuckDB 触发方言自动选择，断言选中的是 duckdb 方言（driver/jdbcUrlPattern/forUpdate 置空等特征）
- [x] ddl_duckdb 实测：对样例 ORM 实体经 duckdb 方言生成 DDL 并在 jdbc:duckdb（临时文件库）上执行建表成功
- [x] EQL/ORM 查询实测：样例实体经 ORM 插入 + EQL 查询（含 where/聚合）在 DuckDB 上执行并返回正确结果
- [x] 错误码翻译实测：在 DuckDB 连接上执行引用不存在表的 SQL，经 `DialectSQLExceptionTranslator` 翻译命中 `nop.err.dao.sql.bad-sql-grammar`。前置条件：消息正则仅在 getErrorCode()==0 且 getSQLState()==null 时参与（DialectSQLExceptionTranslator.java:188），1.5.6.0 无专用异常子类；若实测未命中，先核对 SQLState/errorCode 是否非空再定性为方言缺陷
- [x] 数据源接入实测：`DataSourceConfig`/Hikari 工厂以 `jdbc:duckdb:` 建池并取连接成功；记录单写者文件下连接池的正确策略结论（maxPoolSize=1 或等价约束的必要性）
- [x] 实体路由实测：dataSourceMap 挂 h2 + duckdb 双库，将一个实体路由到 duckdb 数据源并完成 CRUD，回答"命名 dataSourceMap 可否作实体路由"
- [x] 实测暴露的方言/ORM 缺陷逐条登记（不修复），形成 WI8/WI4 输入清单

Exit Criteria:

- [x] selector/ddl/EQL/错误码四项实测各有测试类与断言，本机全绿；若有失败项，逐条登记缺陷清单并注明对应失败测试类名（不得静默跳过）
- [x] 数据源接入三问（池可建、单写者策略、实体路由可否）各有明确结论
- [x] 若本地 .m2 快照与 worktree 源码漂移导致实测失真：先在 worktree `./mvnw install -DskipTests` 重建所需模块再测（命令记录进报告）
- [x] No owner-doc update required：Phase 3 为 spike 实测，不改 live 行为；docs-for-ai 归 WI9
- [x] `ai-dev/logs/2026/10-01.md` 已更新本 Phase 结果

### Phase 4 - 裁定报告与坐标裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/2026-10-01-duckdb-jdbc-selection-and-spike-adjudication.md`

- Item Types: `Decision`

- [x] 按 analysis writing guide 写裁定报告：Status: resolved；含依赖坐标裁定（groupId/artifactId/version/许可/native 矩阵/JDK 兼容）、四问结论、方言实测结论、数据源接入结论、模块归属裁定（确认 nop-duckdb 单模块或推翻+理由）、WI8/WI4 缺陷输入清单、全部实验复现命令；报告内嵌一次 spike 测试运行摘要（测试类清单 + 结果计数）
- [x] 报告明确"被否决的方案"（如其他坐标/版本/拆分方案）与理由
- [x] 报告中引用未来交付物（WI1–WI9 计划、未来模块路径）一律用普通文本，不加反引号（check-doc-links 严格模式会校验反引号路径存在性；仅已存在文档可加反引号）
- [x] （post-audit 动作）closure audit 通过后：勾选 roadmap WI0 checkbox，同步当日日志与本 plan Closure 段

Exit Criteria:

- [x] 报告存在于 `ai-dev/analysis/2026-10/`，含上文全部裁定项与证据出处
- [x] 报告经 `node ai-dev/tools/check-doc-links.mjs --strict` 校验通过（本 plan 涉及 ai-dev/ 文件变更）
- [x] `ai-dev/logs/2026/10-01.md` 已有 WI0 收口记录
- [x] `No owner-doc update required`：WI0 为决策 spike，不改 live 行为；docs-for-ai 归 WI9

## Closure Gates

> WI0 无生产代码变更，验证门按 roadmap Cross-Cutting #1 适配为：spike 测试全绿证据 + 报告可复现命令，替代 `./mvnw test -pl`（无受影响生产模块）。此适配已在 Gate 中显式声明，非遗漏。

- [x] Phase 1–4 全部 Exit Criteria 勾选
- [x] spike 四问与三类接入路径实测证据完整且可复现（报告含命令）
- [x] 依赖坐标裁定、模块归属裁定、单写者策略结论三项均有明确结论与理由
- [x] 实测缺陷清单已登记（WI4/WI8 输入），无 in-scope 缺陷被静默降级
- [x] 无生产代码变更：`No production code changed`（git diff 仅含本 plan、报告、日志、roadmap 状态）
- [x] 独立子 agent closure audit 已完成并记录证据（含 Anti-Hollow：报告结论与 spike 证据一一对应，无凭空断言）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 不适用：本 plan 零 Java 生产源码变更（spike 在 gitignored _tmp/）；该声明即裁定记录
- [x] `No new test required: WI0 为 Decision+Proof spike，无生产功能新增；持久化验证由 WI1+/WI8 计划的测试承载`
- [x] checkstyle 不适用：无 Java 源码变更（spike 项目在 gitignored `_tmp/`）

## Deferred But Adjudicated

（无——WI0 范围内无延期项；实测缺陷登记属交付物本身，修复归属 WI4/WI8 的 plan scope，不属本 plan 的 deferred）

## Non-Blocking Follow-ups

- 若 spike 发现 1.5.6.0 之外存在更合适的版本（如紧急修复版），以实测为准裁定并在报告中说明——这不影响 closure，属 Decision 的自然产出

## Closure

Status Note: WI0 为 Decision+Proof spike，零生产代码变更（git diff master 为空，独立 audit 硬证）。三项裁定（依赖坐标 1.5.6.0 / 模块归属 nop-duckdb 单模块 / 单写者仅跨进程生效）均有实测证据；四问与三类接入路径 16/16 用例全绿且报告结论与测试断言一一对应；实测发现（锁冲突形态、配置纪律、溢出算子边界、通用首层异常包装）已全部登记为 WI1/WI4/WI6/WI8/WI9 输入，无 in-scope 缺陷被降级。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，fresh session）
- Audit Session: agent_9671bc84-a1b3-48d7-81fa-8dc0e7287c35
- Evidence:
  - Phase 1–4 全部 Exit Criteria：PASS（audit 逐条核对，native 矩阵经审计方独立 unzip 枚举复证 4 平台、MIT 经审计方独立核 .m2 POM、问④经审计方 WebFetch v1.5.6.0 DuckDBNative.java 逐字核实）
  - Anti-Hollow：报告结论 → 测试断言对照表 20+ 项全对应；唯一无出处观测（"静默第二实例"）已在收口前从报告移除（实为 ORM 模型未注册的替代解释，无证据不作断言）
  - 零生产代码变更：`git diff master --stat` 为空（audit 独立验证）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；plan 自身 0 issue）
  - `node ai-dev/tools/check-plan-checklist.mjs` 收口后确认退出码 0
  - `scan-hollow-implementations.mjs` 不适用（零 Java 生产源码变更，git 证实）
  - spike 复现命令：按报告依赖清单于 _tmp/duckdb-spike/（gitignored）执行 `mvn test` → 5 类 16 用例全绿；审计时点 surefire 输出与报告引用逐字一致
  - Deferred 分类检查：`Deferred But Adjudicated` 为空且属实；5 条 Minor 发现（q3 无硬断言、maxPoolSize=1 证据在路由 bean 配置中、stale 注释等）均不阻塞且已记录，WI4 以显式测试承接锁冲突断言
  - audit 裁定原文：PLAN CAN CLOSE（无 Blocker/Major）

Follow-up:

- roadmap WI0 checkbox 已按 post-audit 约定勾选；除此之外 no remaining plan-owned work
