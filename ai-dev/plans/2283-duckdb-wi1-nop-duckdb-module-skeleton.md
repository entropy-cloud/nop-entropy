# 2283 DuckDB WI1 — nop-duckdb 单模块骨架与连接管理

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI1；`ai-dev/analysis/2026-10/2026-10-01-duckdb-jdbc-selection-and-spike-adjudication.md`（WI0 裁定）
> Related: 2282（WI0，已完成）

## Purpose

落地 DuckDB 集成的第一个生产模块 nop-duckdb：依赖坐标收敛进 BOM、模块注册进根 pom、连接/会话管理 bean（含 WI0 实测发现的配置一致性纪律与 native 显式报错）、模块级异常体系与基础单测。完成后 nop-duckdb 成为一个可依赖、可启动、测试全绿的最小执行层模块，为 WI2/WI3 提供承载点。

## Current Baseline

- WI0 已裁定：org.duckdb:duckdb_jdbc:1.5.6.0，MIT，零传递依赖，native 4 平台（linux_amd64 / osx_universal / windows_amd64 / linux_arm64），release=17 兼容；模块归属确认单一顶层模块 nop-duckdb
- 全仓尚无 org.duckdb 坐标：`nop-kernel/nop-dependencies/pom.xml` 无 duckdb 条目（版本属性区有 tablesaw.version 等先例，约 L58；dependencyManagement 有 tablesaw 先例约 L382）；根 `pom.xml` modules 区（L503-552）无 nop-duckdb
- 同文件连接纪律（WI0 实测）：同 JVM 同文件多连接共享实例可池化；连接配置不一致报 "Can't open a connection to same database file with a different configuration"；跨进程锁冲突为 SQLException（WI0 报告问③）——WI1 落配置一致性 fail-fast，锁的任务级语义归 WI4
- native 加载失败语义（WI0 实测）：首触 DuckDBDriver 抛 ExceptionInInitializerError（Error），因果链 RuntimeException→IllegalStateException→FileNotFoundException——必须在首次使用边界按 Throwable 捕获并转显式英文错误
- memory_limit 会被 DuckDB 归一化（256MB→244.1 MiB）；`SET memory_limit/threads/temp_directory` 为 per-connection 设置（WI0 报告问②）
- 错误处理两档惯例（error-handling.md）：新模块族错误码从首个起用英文描述（nop-stream 先例）；模块异常类提供 (String)/(ErrorCode) 双构造器，字符串消息英文
- IoC 规则（ioc-and-config.md）：bean 发现纯靠 `_vfs/**/*.beans.xml`；自动装载 `/{moduleId}/beans/app-*.beans.xml`；放置 app-\*.beans.xml 的模块 JAR 必须在 `_vfs/{moduleId}/_module` 提供零字节标记；`@Inject` 字段不可 private；`@InjectValue("@cfg:key|default")` 注入配置
- WI0 spike 踩坑沉淀：模块 beans 文件名必须 app-\* 前缀；ORM 模型固定 orm/app.orm.xml（本 WI 不涉 ORM 模型）；`@cfg:` 无默认值时为硬性必填（engine 全部配置键带默认值）

## Goals

- duckdb_jdbc 1.5.6.0 收敛进 nop-dependencies BOM（版本属性 + dependencyManagement），nop-duckdb 模块注册进根 pom modules，对齐 nop-jq 扁平形态（parent=nop-entropy，无子模块）
- 连接/会话管理 bean：@InjectValue 配置（memory_limit / threads / temp_directory / 连接策略），按文件路径打开连接并对每连接应用 SET；同文件连接配置一致性 fail-fast；连接生命周期可关闭
- native 缺失/加载失败显式报错：首次驱动触达边界 catch Throwable 转 NopDuckDbException（英文消息 + 错误码）
- 模块异常体系：NopDuckDbException（双构造器）+ NopDuckDbErrors（英文描述错误码：native-load-failed / config-conflict / connect-failed / invalid-config）
- 基础单测全绿：连接生命周期、配置注入与 SET 应用、配置冲突 fail-fast、native 包装路径、IoC 装配（真实 beans.xml 经 CoreInitialization）、引擎级端到端（文件写入→关闭→重开数据仍在）

## Non-Goals

- 不做文件数据面 CSV/Parquet 封装与 XLSX 桥（WI2）
- 不做 nop-task SQL 步骤（WI3）
- 不做锁冲突的任务级错误码/retry 语义（WI4——WI1 只落"同文件配置一致性"这一连接层纪律）；roadmap WI1 配置清单中的"单文件锁策略"经 WI0 问③裁定无进程内配置需求（进程内多连接共享实例无冲突），跨进程语义归 WI4，此为有证据的显式裁定而非遗漏
- 不做 Hikari 池管理（数据源池仍由 nop-dao 既有 HikariDataSourceFactory 承担；本模块管理直连分析连接）
- 不写 docs-for-ai 使用文档（WI9）

## Scope

### In Scope

- 根 pom.xml：modules 增 nop-duckdb
- nop-kernel/nop-dependencies/pom.xml：duckdb.version 属性 + dependencyManagement 条目
- 新模块 nop-duckdb/（pom + src/main/java/io/nop/duckdb/* + src/main/resources/_vfs/nop/duckdb/[_module, beans/app-duckdb.beans.xml]）
- 模块测试（src/test/java + 必要的 test 资源）
- 当日 ai-dev/logs/ 更新

### Out Of Scope

- nop-dao 方言改动（WI8）
- 任何消费方模块接入（后续 WI）
- CI / benchmark

## Execution Plan

### Phase 1 - 模块注册与依赖收敛

Status: completed
Targets: `pom.xml`、`nop-kernel/nop-dependencies/pom.xml`、`nop-duckdb/pom.xml`

- Item Types: `Fix`（本分支 guide 规则 15 为四分类，新增能力按 plan 2263 先例标 Fix；roadmap 所写 Item Type: Feature 对应 master 未提交的 guide 增补，不导入本分支以免截留他处未提交工作）

- [x] nop-dependencies BOM：新增 duckdb.version=1.5.6.0 属性与 org.duckdb:duckdb_jdbc dependencyManagement 条目
- [x] 新模块 nop-duckdb：parent=nop-entropy，依赖 nop-core（compile）+ duckdb_jdbc（compile，经 BOM）+ 测试引导最小集（junit-jupiter 与 nop-ioc 均 test scope——CoreInitialization 只挂 nop-core 可启动但不起 IoC 容器，IoC 容器来自 nop-ioc 的 IocCoreInitializer，nop-ioc 传递带入 nop-xlang；nop-config 非必需，@cfg: 在 IoC 侧由 nop-ioc 自带 resolver 处理）
- [x] 根 pom modules 注册 nop-duckdb
- [x] `./mvnw install -DskipTests -pl nop-duckdb -am` 编译通过（首次全链构建，验证模块装配）

Exit Criteria:

- [x] BOM 含 duckdb_jdbc 版本收敛（无散写版本号）
- [x] `./mvnw install -DskipTests -pl nop-duckdb -am` 退出码 0
- [x] No owner-doc update required：模块注册即 live baseline 本身，使用文档归 WI9
- [x] `ai-dev/logs/2026/10-01.md` 已更新

### Phase 2 - 连接/会话管理实现

Status: completed
Targets: `nop-duckdb/src/main/java/io/nop/duckdb/`、`nop-duckdb/src/main/resources/_vfs/nop/duckdb/`

- Item Types: `Fix`（本分支 guide 规则 15 为四分类，新增能力按 plan 2263 先例标 Fix；roadmap 所写 Item Type: Feature 对应 master 未提交的 guide 增补，不导入本分支以免截留他处未提交工作）

- [x] NopDuckDbException（extends NopException，(String)/(ErrorCode) 双构造器，英文消息）+ NopDuckDbErrors（英文描述错误码：native-load-failed / config-conflict / connect-failed / invalid-config）
- [x] 引擎实现（接口 + 实现）：@InjectValue 注入三个封闭配置键 nop.duckdb.memory-limit / nop.duckdb.threads / nop.duckdb.temp-directory（全部默认空）。语义三元组：已识别键 + 空/缺省 → 不执行 SET（合法默认，非错误）；已识别键 + 非法值 → invalid-config fail-fast（英文消息含键名与值）；键集封闭于这三个，不存在第四个"未实现配置项"
- [x] 同文件配置一致性（两条触发路径，防 WI0 实测的 "different configuration" 失败）：(a) 引擎内按文件路径登记配置指纹（内容 = memory-limit + threads + temp-directory + 连接 user；登记范围 = JVM 级静态注册表，覆盖同 JVM 两个不同配置引擎实例打开同一文件的场景），指纹不一致时抛 config-conflict（英文消息含文件路径）；(b) DuckDB 原生 SQLException 消息含 "different configuration" 时翻译为 config-conflict 错误码（英文消息含文件路径与原始消息），覆盖同 JVM 其他组件（如 Hikari 池）以不同配置先开连接的场景——@InjectValue 使单实例配置创建期定死，故 (a) 防实例间漂移、(b) 防外部组件
- [x] native 显式报错：驱动首触边界（Class.forName / DriverManager.getConnection 外层）catch Throwable，识别 ExceptionInInitializerError/UnsatisfiedLinkError 链转 native-load-failed（英文消息含平台 os.name/os.arch 与原始异常消息）；不再以 Error 裸穿
- [x] IoC 装配：_vfs/nop/duckdb/_module 零字节标记 + _vfs/nop/duckdb/beans/app-duckdb.beans.xml 定义引擎 bean（engine 生命周期 close 关闭已开连接）
- [x] 连接关闭语义：引擎 close 释放全部由它打开的连接（IoHelper.safeClose 惯例）；连接的宿主关闭不重复关闭

Exit Criteria:

- [x] 上述行为全部有对应实现且无空壳/静默跳过（每个错误分支可触发：invalid-config 由非法值触发、config-conflict 由指纹不一致或原生 SQLException 触发，符合 plan guide 规则 24）
- [x] 接线验证：引擎 bean 经真实 app-duckdb.beans.xml 装配（验证测试由 Phase 3 承载：测试从 IoC 容器取 bean 使用，非直 new）
- [x] **端到端验证**：从引擎 API（openFile）到真实 .duckdb 文件写表→关闭→重开读回数据的完整路径有测试覆盖（测试由 Phase 3 承载）
- [x] No owner-doc update required：使用文档归 WI9
- [x] `ai-dev/logs/2026/10-01.md` 已更新

### Phase 3 - 测试与全量验证

Status: completed
Targets: `nop-duckdb/src/test/java/io/nop/duckdb/`

- Item Types: `Proof`

- [x] 连接生命周期测试：openMemory/openFile 打开→使用→引擎 close 全释放；close 后再使用抛显式异常
- [x] 配置注入测试：@InjectValue 配置（memory-limit/threads/temp-directory）经 beans 装配生效，SET 在连接上可读回（memory_limit 归一化行为按 WI0 口径断言"非默认值即生效"而非字面相等）；非法值触发 invalid-config
- [x] 配置冲突 fail-fast 测试：两条触发路径各一——(a) 同 JVM 内以不同 user 的裸连接先打开同一文件，再经引擎 openFile 同文件，断言原生 "different configuration" 失败被翻译为 config-conflict；(b) 第二个不同配置的引擎实例打开同文件，断言指纹检查抛 config-conflict
- [x] native 包装测试：不依赖目标平台缺失 native 即可在受支持平台（darwin）上触发并断言包装路径——异常类型为 NopDuckDbException（不再以 Error 裸穿）、英文消息含 os.name/os.arch 与原始原因链（触发形式由实现选择，可观察契约以本条为准）
- [x] 引擎级端到端测试：openFile 写表→close→重开→数据一致（任务级工作集语义在引擎层成立）
- [x] 全量验证：`./mvnw test -pl nop-duckdb -am` 退出码 0（roadmap Cross-Cutting #1 收口门；内环迭代可用 Phase 1 的 install 产物跑 `./mvnw test -pl nop-duckdb`，上游从本地仓库解析，收口时仍以 -am 全链为准）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（涉及 ai-dev 文件）

Exit Criteria:

- [x] 新增功能每个公共行为至少一个测试（plan guide 规则 25：新功能必有测试）
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] `No owner-doc update required`（WI9 承接）
- [x] `ai-dev/logs/2026/10-01.md` 已更新 WI1 收口记录

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（本 plan 无已知 live defect 输入）
- [x] 行为/契约结果已达成：engine 经 IoC 装配可用，BOM/根 pom 注册完成
- [x] 必要 focused verification 已完成（Phase 3 全部测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required（本 plan 显式声明归 WI9）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）engine bean 从 IoC 容器装配并消费（接线验证测试），（b）openFile→写→重开→读回端到端路径贯通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] checkstyle / 代码规范检查通过（imports 分组、英文消息、无裸 RuntimeException）

## Deferred But Adjudicated

（无——WI1 范围内无延期项）

## Non-Blocking Follow-ups

- engine 的文件连接若未来需要池化，可接入 Hikari（当前直连已满足分析负载；实测 per-connection 开销可接受后再评估）

## Closure

Status Note: nop-duckdb 模块完整落地并通过独立 closure audit：BOM/根 pom 注册、引擎三配置键封闭语义、配置一致性双触发路径、native 首触显式包装、IoC 装配与 10 用例全绿；Anti-Hollow 三项（真实装配/端到端数据一致/0 hollow findings）全 PASS；无 Blocker/Major，4 条 Minor 中 2 条已随手修复（-am 结果补记、端到端引擎关闭卫生），其余登记为 follow-up。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，fresh session）
- Audit Session: agent_d04ab985-1e6a-4d73-9520-f2f3372af6bb
- Evidence:
  - Phase 1–3 全部 Exit Criteria：PASS（审计方逐文件核对源码 + 逐测试方法核对断言）
  - Anti-Hollow：(a) TestDuckDbBeans 经 BeanContainer.getBeanByType 从真实容器取 bean（非直 new）PASS；(b) testFileWorkingSetEndToEnd 写 2 行→关→重开→count=2/sum=3 精确断言 PASS；(c) scan-hollow-implementations --module nop-duckdb --severity high 实测 0 findings、exit 0 PASS
  - 命令复证（审计方实跑）：./mvnw test -pl nop-duckdb 10/10 绿；./mvnw test -pl nop-duckdb -am BUILD SUCCESS exit 0；clean test 复跑绿；check-doc-links --strict 0 errors
  - git 范围核对：变更恰为根 pom/BOM/nop-duckdb 模块/plan/日志，无越界
  - `node ai-dev/tools/check-plan-checklist.mjs` 收口后退出码 0
  - Minor 处置：Minor1（日志 -am 措辞）已在 audit 前修正；Minor3（e2e 引擎未 close）audit 后修复并复跑 10/10 绿；Minor2/4 登记 follow-up
  - audit 裁定原文：PLAN CAN CLOSE（无 Blocker/Major）

Follow-up:

- temp-directory 正向 SET 读回无专测（三键同一 applySettings 路径，memory-limit 已验证 quote 路径；WI2 外存场景自然覆盖）
- 指纹中 user 分量固定为 default（引擎不设 user），文档口径在 WI9 使用文档中写明
- 除此之外 no remaining plan-owned work
