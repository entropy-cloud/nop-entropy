# 2413 WI4 connector-jdbc provided+optional 依赖硬化

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（WI4，Phase B）
> Related: ai-dev/plans/2412-wi3-architecture-baseline-docs-rewrite.md

## Purpose

修复 connector-jdbc 的潜伏运行时缺陷：pom 声明 `nop-message-debezium` 为 provided+optional 并自述「pure INSERT-mode sinks never touch these types」，但 `JdbcTwoPhaseCommitSink` 在 main 作用域 import `ChangeEvent` 并在 INSERT 路径上 `instanceof` 解引用——纯 INSERT 部署（classpath 无该 jar）首次调用 `toDmlOp` 即 `NoClassDefFoundError`。本 WI 以窄接口隔离使 INSERT 路径零链接（方案 (b)），并落地 roadmap 原文要求的无 debezium classpath 防回归测试。

## Current Baseline

- `nop-stream/nop-stream-connector-jdbc/pom.xml:36-41` 注释自述「pure INSERT-mode sinks never touch these types」；:42-47 声明 `nop-message-debezium` `provided`+`optional`。
- `JdbcTwoPhaseCommitSink.java`（1130 行）`:27` main 作用域 `import io.nop.message.debezium.ChangeEvent`；`instanceof` 解引用 4 处：`:483`（**位于 `dmlMode==DmlMode.INSERT` 分支首行**——INSERT 路径即触发类解析）、`:500`（keyed 模式 op 推导）、`:546`（after 映射）、`:567`（key 映射）。JVM `instanceof` 触发类解析，类缺失即 NCDFE（roadmap 已实测）——pom 注释的安全假设不成立。
- **真实代码链接面**：main 侧仅 Sink 1 个文件有 ChangeEvent 编译链接（ConnectorFactory 仅字符串字面量提及、Builder 仅 Javadoc 提及——均不解析）；test 侧 TestJdbcTwoPhaseCommitSinkDml 1 文件。8 个测试类全部在带该依赖的 test classpath 下运行（现状无法暴露此缺陷）。
- **方案 (a) 体积实测（第一轮审查推翻初测后的修正值）**：把 nop-message-debezium 变为 compile 非 optional 后，下游净增传递面 **65 构件 / ~33.6MB**——其中 kafka-clients 4.3.1（10.2MB）+ zstd-jni 7.3MB + snappy 2.4MB + lz4 0.9MB、Kafka Connect 栈 connect-runtime 3.9.2（785KB）连带的 jersey 3.1.x 全族（~3.4MB）+ jetty 双代 12.0.x/9.4.x（~3.4MB）+ hk2 族 + jackson-databind 1.7MB 及模块族等。对 sink-only 部署，REST 服务器栈（jersey/jetty/hk2 ≈11MB）与 CDC 引擎为纯死重，且引入 jetty 双代并存。另披露既有事实：connector-jdbc 传递解析的 connect-* 为 3.9.2（debezium-embedded 裸声明；nop-dependencies 仅管理 kafka-clients），与 nop-dependencies 注释声称的 Debezium 3.7 构建线不一致——compile 化会把该解析面首次传给 benchmark-stream 等下游。
- 消费者：nop-benchmark-stream（唯一不含 debezium 族的 compile 消费者，方案 (a) 下净增 ~33.6MB）、nop-stream-runtime（test）、fraud-example（已含 debezium 族，边际≈0）、connector-debezium（已含）、nop-orm-migration、nop-bom。全仓无对 nop-message-debezium 的 exclusion。
- 既有测试构造形态：`JdbcTwoPhaseCommitSink.<ChangeEvent>builder()...dmlMode(mode)`（builder API，TestJdbcTwoPhaseCommitSinkDml 55 用例离线 ~5s 全绿）。
- 仓库先例：connector 基座模块（AR-2，`nop-stream-connector/pom.xml:14-29`）曾为同类 NCDFE 缺陷把 batch/debezium 类型物理外移——「运行时类缺失即炸」是该代码库的一等关注点。

## Goals

- INSERT 纯记录路径零链接：classpath 无 nop-message-debezium 时 `dmlMode=INSERT` 全路径（open → write → prepareCommit/commit）正常工作。
- CDC 能力在场时行为逐字节保持：probe 命中后既有 4 处分支语义（含 :483-491 对 CDC 事件流入 INSERT 的友好报错）不变，既有 8 测试类零改动全绿。
- 防回归钉持久化：surefire 独立 execution + `classpathDependencyExcludes` 排除 nop-message-debezium 跑新测试类——回归（任何人重新引入链接）即 CI 红。

## Non-Goals

- 不把 nop-message-debezium 改为 compile（方案 (a) 被裁定否决，见 Adjudication）。
- 不改 builder/factory 对外 API 签名（探测为类内自动行为，无新配置面）。
- 不处理 runtime 测试侧 connector fan-in（WI5 对象）。

## Adjudication（方案二选一，plan 阶段裁定）

**裁定：方案 (b)——ChangeEvent 分支隔离，INSERT 路径零链接。**

roadmap 原文倾向 (a)（改动面小、语义直白），并给 (b) 设了唯一触发条件「(a) 实测引入不可接受的依赖体积」。**实测结果触发该条件**：(a) 的真实边际为 65 构件/~33.6MB（初轮计划曾误测为 2.5MB/12 构件，独立审查以同款 dependency:tree 对比证伪并给出修正值），其中约 11MB 是 sink 部署永不使用的 REST 服务器栈、21MB 是为 CDC 引擎服务的 kafka-clients+编解码器——对纯 INSERT JDBC sink 部署不可接受，jetty 双代并存的冲突面亦为负资产。方案 (b) 的实现形态：

- `CdcEventOps` 窄封装类集中全部 ChangeEvent 链接（isChangeEvent/operation/afterOf/keyOf，方法接 Object——IN 擦除即 Object；内部 checked narrowing cast 产生零 unchecked 警告）。
- `JdbcTwoPhaseCommitSink` 以 `static final boolean CDC_PRESENT` 探测：`Class.forName("io.nop.message.debezium.ChangeEvent", false, JdbcTwoPhaseCommitSink.class.getClassLoader())`——**钉死定义方 classloader（禁 TCCL）**、`initialize=false`（消除对 ChangeEvent 未来静态初始化的耦合）、`catch (ClassNotFoundException | LinkageError)`（防任何 Throwable 逃逸 clinit 报废整个 Sink 类）；4 处分支改写为 `CDC_PRESENT && CdcEventOps.xxx(value)` 短路形态——按 JVMS 5.4.3.1，短路时 invokestatic 符号引用**从不解析**（验证阶段对 CdcEventOps 的加载无害：加载 ≠ 解析其 ChangeEvent 引用）。
- probe 字符串 `"io.nop.message.debezium.ChangeEvent"` 是类名即契约：注释钉住 probe 字符串 ↔ CdcEventOps ↔ pom 依赖三方联动（改名/搬包 = 静默能力丢失）；字符串常量从 CdcEventOps 的 `static final String` 引入（编译期常量折叠进 Sink，不产生 CP 链接）。
- 语义保持论证：友好报错路径（:483-491 等）仅在 ChangeEvent 类在场的部署可达，而类在场 ⇒ probe 命中 ⇒ 行为与现状逐字节一致；类不在场的部署现状为 NCDFE 崩溃，(b) 后 INSERT 正常工作、CDC 模式缺 opMapper 时得到干净 StreamException——不存在「从好变坏」的路径。
- 与 core→runtime 反射软耦合（WI7 消除对象）的性质区分：此处是可选能力探测（optional capability probe），探测失败有明确定义语义（能力缺席），非结构性分层违例。
- 防回归测试即 roadmap 原文形态：独立 surefire execution 排除该 jar 跑 INSERT 全路径断言绿（修复前红、修复后绿），自动化持久门禁。

## Scope

### In Scope

- `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java`：移除直接 import；4 处 instanceof 分支改写为 probe 短路形态；相关 javadoc 同步。
- 新增 `CdcEventOps`（包私有，集中 ChangeEvent 链接）。
- `nop-stream/nop-stream-connector-jdbc/pom.xml`：注释块（:36-41）改写为如实描述（CDC 分支经 probe + 窄封装隔离，INSERT 纯记录路径零链接；依赖保持 provided+optional）；surefire 增独立 execution（id `insert-no-debezium`，classpathDependencyExcludes = io.github.entropy-cloud:nop-message-debezium，**`<includes>` 限定仅 `TestJdbcTwoPhaseCommitSinkInsertNoDebezium`——无 includes 时该 execution 会套默认 include 跑全部 8 测试类，TestJdbcTwoPhaseCommitSinkDml 构造 ChangeEvent 必红**）。自定义 execution 不取消 default-test（两者都跑）；新测试类在 default execution 亦会执行（特性：顺带覆盖 probe 命中路径的 INSERT 语义）。
- 新增测试 `TestJdbcTwoPhaseCommitSinkInsertNoDebezium`（仅进该 execution）。

### Out Of Scope

- Builder/Factory 行为变更（仅 javadoc 如有需要）；connector-debezium 模块；runtime test fan-in（WI5）。

## Execution Plan

### Phase 1 - 隔离重构与防回归门禁

Status: completed
Targets: `JdbcTwoPhaseCommitSink.java`、新 `CdcEventOps.java`、pom、新测试类

- Item Types: `Fix`（实证潜伏运行时缺陷）；防回归测试为 `Proof`

- [x] **先钉后拆（E1）**：先写 `TestJdbcTwoPhaseCommitSinkInsertNoDebezium`（复用 TestJdbcTwoPhaseCommitSinkDml 的 HikariCP + 内存 H2 MODE=MySQL + CoreInitialization fixture；Map 记录 INSERT 全路径：open → write 多条 → prepareCommit → commit → 断言落库），pom 加独立 surefire execution `insert-no-debezium`（classpathDependencyExcludes + `<includes>` 限定），在**未修代码**状态下跑——命令形态 `./mvnw -o test -pl nop-stream/nop-stream-connector-jdbc -am -Dtest=TestJdbcTwoPhaseCommitSinkInsertNoDebezium -Dsurefire.failIfNoSpecifiedTests=false`（红相 NCDFE 精确浮现于 invoke → toDmlOp 的 instanceof），留档输出
- [x] 新建 `CdcEventOps`：集中 ChangeEvent 的 isChangeEvent/operation/afterOf/keyOf 四个静态方法（全部 main 侧链接收拢于此）
- [x] `JdbcTwoPhaseCommitSink`：删除 `:27` 直接 import；加 `static final boolean CDC_PRESENT` 探测（规格见 Adjudication：定义方 loader + initialize=false + catch CNFE/LinkageError）；`:483`/`:500`/`:546`/`:567` 四处改写为 `CDC_PRESENT && CdcEventOps.xxx(value)` 短路形态（四处 else 分支的错误文案均为纯字符串字面量、零类型链接，实测无需改文案）；`:181-189`/`:289-291` 等处的 `{@link ChangeEvent}` javadoc 改为纯文本或 {@code} 形态（删 import 后 {@link 断链）；注释语义保持（友好报错文案仅在 CDC 在场时可达，与现状一致）
- [x] pom :36-41 注释改写为如实描述
- [x] 复跑防回归测试——预期绿；复跑默认 execution 全部既有测试——预期 55+ 用例绿（probe 命中路径行为保持）

Exit Criteria:

- [x] **红绿证据**：修复前 `insert-no-debezium` execution 红（NCDFE at toDmlOp，输出留档于 plan 或 log）；修复后同 execution 绿
- [x] `grep -n "import io.nop.message.debezium" nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java` 零命中；常量池校验按可执行形态：`javap -v -p` 遍历模块全部 main class，`grep 'io/nop/message/debezium'` 在 Sink 中期望 0 行（错误文案字符串不匹配斜杠形态，初测 26 行命中均为源码形态字符串、不落入该 grep）、在 CdcEventOps 中承接全部链接（现状 Sink 17 条 CP 条目的迁移闭环）
- [x] **回归验证**：`./mvnw -o test -pl nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector-debezium -am` 全绿（既有 8 测试类合计 55 用例零改动全绿——Dml 类自身 8 用例；default execution 用例数因新测试类加入而增长，双 execution 各跑一份新类用例；connector-debezium 无回归）
- [x] **防回归门禁持久性**：`insert-no-debezium` execution 属默认生命周期（`mvn test` 即执行，非 profile 隐藏）——以 `./mvnw -o test -pl nop-stream/nop-stream-connector-jdbc` 输出含该 execution 的 Tests run 行为证
- [x] **行为保持**：CDC 在场路径逐字节语义不变（既有测试类零改动全绿为证；4 处分支的报错文案与条件结构对照 diff 复核）
- [x] No new test required 不适用：本 WI 交付含新测试（TestJdbcTwoPhaseCommitSinkInsertNoDebezium，验证 INSERT 无 debezium classpath 全路径 + 静默能力探测语义）
- [x] No owner-doc update required（cdc-design.md §3.1 D1 表的 provided+optional 表述对象是 debezium-connector-mysql/postgres，与本 WI 无涉不构成漂移；全仓无 owner doc 记载 connector-jdbc→nop-message-debezium 的 scope；cdc-design.md §3.5 dmlMode 语义不变）
- [x] `ai-dev/logs/2026/10-11.md` 已追加 WI4 条目

## Closure Gates

- [x] 红绿证据完整（修复前 NCDFE 红、修复后绿，均已留档）
- [x] Sink 无 ChangeEvent 常量池链接，CDC 链接收拢于 CdcEventOps
- [x] connector-jdbc 全测试绿（默认 + insert-no-debezium 双 execution）且 connector-debezium 绿
- [x] 防回归门禁为默认生命周期持久自动化（非一次性人工验证）
- [x] 无 in-scope live defect 被降级为 follow-up（方案 (a)→(b) 的裁定翻转为实测驱动的 scope 内决策，非降级）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2413-wi4-connector-jdbc-debezium-dependency-hardening.md --strict` 退出码 0

## Deferred But Adjudicated

### connect-* 3.9.2 与 Debezium 3.7 构建线的一致性

- Classification: `watch-only residual`
- Why Not Blocking Closure: 既有解析状态（非本 WI 引入）；(b) 下该解析面不向新下游扩散；kafka-clients 已由 nop-dependencies 钉 4.3.1 兜底
- Successor Required: no
- Successor Path: 如需收口，归 nop-message-debezium 的版本管理条目（非本 roadmap 范围）

## Closure

Status Note: 全部技术面经独立审计 PASS（审计者亲手复现双 execution 门禁并以 -X+booter MANIFEST 实证排除 classpath 生效）；审计发现 1 项文档阻断——roadmap WI4 括注内半角括号违反 roadmap 自钉规则且实测破坏 mission-driver 解析器（12 项被丢成 11 项），已按审计建议改写并复跑解析器确认 12 项 / WI4=done / progress 0.33 后关闭。方案 (a)→(b) 裁定翻转为两轮对抗审查驱动的实测决策（初测 2.5MB 被证伪为 33.6MB）。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，closure-audit fresh session，2026-10-11）
- Audit Session: agent_12b0ddeb-2b92-4ba0-b644-47ce5b203a08
- Evidence:
  - Sink import 零命中；javap 全 6 main 类斜杠形态 debezium=0（Sink 17→0）、CdcEventOps=12 承接全部链接、probe 字符串点分折叠（ldc，无类引用）
  - 审计者亲手复现双 execution 全绿（default-test 1/1 + insert-no-debezium 1/1），-X + booter jar MANIFEST 实证 insert-no-debezium classpath 的 debezium 计数=0、h2/HikariCP 仍在
  - 全模块 56/56（default）+ 1/1（门禁 execution）绿；git diff 仅 instanceof→gated 与 javadoc，错误文案与非 CDC 逻辑零改动
  - 红相 NCDFE at :105 留档 _tmp/wi4_red_evidence.txt（时间线自洽：红 10:09 → 修复 10:10-10:11 → 产物 10:13）
  - Anti-Hollow：CdcEventOps 四方法 8 个真实调用点 + scan-hollow-implementations exit 0
  - 发现并阻断后修复：roadmap:33 括注半角括号违反 :5 规则、解析器实测丢弃该行（11 项/0.27）——改写后复跑解析器 12 项/4 done/0.33

Follow-up:

- no remaining plan-owned work
