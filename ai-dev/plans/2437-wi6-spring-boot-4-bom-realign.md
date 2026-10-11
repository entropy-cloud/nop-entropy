# 2437 WI6 Spring Boot 4.1.1 + Cloud 2025.1.x + AI 2.0.x BOM bump + sub-pom realign

> Plan Status: active
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI6；事实基座 `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`（第 1.5/2.5/3.2 节）
> Related: plan 2431（WI5 quarkus 4.0.0.Beta1 统一，已收口）；plan 2436（WI7 Jackson 3，后续）
> Draft Review: 2026-10-11 独立子代理对抗审查（agent_d8c37737）——Blocker-1（3 独立 pom 无 parent，删插件 version 会静默漂移 4.2.0-M2）已按方案 (a) 修入 Phase 1；Major-2/3、Minor-4/5/6 已同步修正

## Purpose

把 nop-spring 及 Spring-facing 模块解析到 Spring Boot 4.1.1 + Spring Cloud 2025.1.3 + Spring AI 2.0.1 的单一 BOM 训练线上；deprecated 组合 starter 迁移到 SB4 模块化 starter；spring-boot-maven-plugin 版本单点化；消化 SB4/Spring Framework 7 的版本性编译破损。用户裁定（2026-10-11 会话指令）：**deprecated 用法必须迁移，一律采用最标准做法**。

## Current Baseline

- `nop-kernel/nop-dependencies/pom.xml:27-29`：`spring-boot.version=3.5.14`、`spring-cloud.version=2025.0.2`、`spring-ai.version=1.1.5`；`:139-175` 依次 import spring-ai-bom / spring-cloud-dependencies / spring-boot-dependencies（quarkus-bom 在其前）。
- Maven Central 已核实（2026-10-11）：`spring-boot-starter-webmvc:4.1.1` 存在且依赖树与 `-web` 等价（jackson/tomcat/http-converter/webmvc）；`spring-boot-starter-web:4.1.1` 描述自述 "deprecated in favor of spring-boot-starter-webmvc"；`-security`/`-webflux`/`-websocket`/`-actuator`/`-batch`/`-jdbc`/`-logging`/`-test` 在 4.1.1 **无**废弃标记；`spring-cloud-dependencies:2025.1.3`、`spring-ai-bom:2.0.1`、`spring-ai-starter-mcp-server-webmvc:2.0.1`、`mybatis-spring-boot-starter:4.1.0`（mybatis 4.x = SB4 线）均存在。
- `spring-boot-starter-web` 消费方 5 处：`nop-spring/nop-spring-file/pom.xml:32`、`nop-spring/nop-spring-web-starter/pom.xml:62`、`nop-demo/nop-spring-report-demo/pom.xml:65`、`nop-demo/nop-rpc-server-demo/pom.xml:47`、`nop-demo/nop-spring-demo2/pom.xml:81`（**死模块**——已注释出 reactor，`nop-demo/pom.xml:25`）。
- `spring-boot-maven-plugin` 硬编码版本 6 处，按 parent 结构分两类（2026-10-11 审查实证）：
  - **独立 pom（无 `<parent>`，聚合≠继承，root pluginManagement 物理不可达）3 处**：`nop-demo/nop-spring-demo/pom.xml:196`(3.4.5)、`nop-demo/nop-spring-gateway/pom.xml:131`(3.4.5)、`nop-demo/nop-spring-report-demo/pom.xml:75`(3.5.14)——仅经 dependencyManagement import nop-bom；此类只能保留显式版本字面量（BOM import 不合并 pluginManagement；删 version 会被 Maven 静默解析到 latest=4.2.0-M2 milestone，实证见审查记录）。
  - **parent 链模块 3 处**：`nop-ai/nop-spring-mcp-server/pom.xml:50`(3.5.14)、`nop-demo/nop-spring-gateway-demo-service/pom.xml:91`(3.4.5)、`nop-demo/nop-spring-simple-demo/pom.xml:50`(3.5.14)——可继承 root pluginManagement，删本地 version。
  - demo2 经 `${spring-boot.version}`=3.2.0（死模块）。root pom 无 spring-boot 插件 pluginManagement。
- root pom 已有 `<quarkus.platform.version>` 属性先例（WI5 统一，root/nop-dependencies/nop-cli 三点同值）。
- `nop-spring/nop-spring-delta/pom.xml:23`：`mybatis-spring-boot-starter` 硬编码 3.0.2（SB3 线）。
- `nop-demo/nop-rpc-server-demo/pom.xml`：`spring-cloud-alibaba-dependencies:2022.0.0.0` 硬编码 import + `spring-cloud-starter-openfeign`（版本来自 nop-dependencies 的 Cloud 训练线）+ `spring-cloud-starter-alibaba-nacos-discovery`。SCA 2022.0.0.0 与 SB3.5 时期即已错配，属 pre-existing；SB4 兼容的 SCA 训练线尚不存在（SCA 官方 2025.x 尚未发布 SB4 对齐版）。
- nop-spring 无本地 spring-boot/spring-cloud 版本覆盖（`nop-all-for-spring` 只有 quarkus.platform.version 本地属性；`nop-report-for-spring` 的 quarkus 块是注释态）——roadmap WI6 "stop local SB/Cloud overrides" 在活模块中零落点，唯一 SB 覆盖在死模块 demo2（WI12 删）。
- Java 源码面：全仓 67 文件 import org.springframework.*（剔出 scope 的 nop-benchmark-orm 占 10；WI6 target 范围约 44；其余为字符串字面量误命中），无 `@MockBean`/`Jackson2ObjectMapperBuilderCustomizer`/`@JacksonComponent`（WI0 已核）；`@SpringBootTest` 实际载体仅 3 文件：nop-spring-demo 的 `TestSpringBatch`/`TestDeltaMyBatis`（Batch 6 主版本跳变真实爆点）、nop-spring-gateway 的 `LmStudioGatewayTest`（LocalServerPort）；`nop-ai/nop-spring-mcp-server` 与 `nop-demo/nop-spring-security-demo` 无 src/test（compile-only）；Spring AI 面 = `nop-spring-mcp-server-support` 的 `ToolCallback`/`FunctionToolCallback`/`ToolCallbackProvider`/`ToolContext`。
- 构建环境：`JAVA_HOME=/Users/abc/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home`（WI1-WI5 确立）；WI5 收口基线 = 全量 install 绿 + 恰 5 pre-existing + 57 SKIPPED。
- 工作区在途未提交改动（2026-10-11 git status，属于 plan 2380 EQL 任务与本 plan 无关）——**本 plan 提交白名单仅为下列之外的 ai-dev/plans/2435、roadmap、logs 与本 plan 实际触碰的 spring 文件，严禁卷入**：`ai-dev/bugs/2026-09/2026-09-28-jq-halt-catchable-by-try-catch.md`、`ai-dev/design/eql-cross-db-compat/`、`ai-dev/logs/2026/10-10.md`、`ai-dev/plans/2380-eql-upsert-and-returning-grammar.md`、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`nop-datav/nop-datav-service/pom.xml`、`nop-demo/nop-spring-demo/src/main/resources/META-INF/native-image/.../reflect-config.json`、`nop-persistence/nop-orm-eql/*`（4 文件）、`nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/zh-CN/nop-cli-errors.i18n.yaml`、未跟踪 `nop-persistence/nop-orm/src/test/java/io/nop/orm/TestOrmUpsertWiring.java` 与 `sbta.jar`。注意 nop-spring-demo 的 native-image reflect-config.json 在本 plan 要构建的模块内——构建产物差异如实忽略，不提交不还原。

## Goals

- 单一 Spring 训练线：spring-boot 4.1.1 / spring-cloud 2025.1.3 / spring-ai 2.0.1，全部经 nop-dependencies 单点 import，活模块零本地覆盖。
- deprecated 面清零：`spring-boot-starter-web` → `spring-boot-starter-webmvc`；`spring-boot-maven-plugin` 硬编码版本清除（root pluginManagement 单点）；mybatis-spring-boot-starter 上 SB4 线（4.1.0）。
- 版本性编译/测试破损全部以标准新 API 消化（Spring Framework 7 / Boot 4 / Security 7 / Batch 6 / AI 2.0 的废弃替换），不做降级 hack。
- 全 reactor 在 JDK 25 工具链下保持 WI5 基线（绿 + 恰 5 文档化 pre-existing）。

## Non-Goals

- Jackson 3 迁移（WI7）；删除 demo2/jdk17-demo（WI12）；benchmark JPA 栈（WI13）；BOM-first pin 治理（WI10）；Quarkus 侧（WI5 已收口）。
- 升级 Spring 系依赖到训练线之外的自选版本（一切以三 BOM 解析结果为准）。
- nop-rpc-server-demo 的 SCA 训练线换代（无 SB4 兼容上游版本，保持现状并裁定，见 Deferred）。

## Scope

### In Scope

- `nop-kernel/nop-dependencies/pom.xml`：三属性升线。
- root `pom.xml`：`spring-boot.version` 属性 + `spring-boot-maven-plugin` pluginManagement（标准做法：父链单点管理版本，模块声明不带 version）。
- 6 个模块 pom 删插件硬编码版本。
- starter `-web`→`-webmvc` ×4（活模块/活 demo）。
- `nop-spring-delta` mybatis-spring-boot-starter 3.0.2→4.1.0。
- SB4/SF7 编译破损修复（nop-spring/*、nop-ai/nop-spring-mcp-server*、nop-demo spring demos）。
- 受影响模块测试修复与聚焦验证 + 全量 install 门。

### Out Of Scope

- `nop-demo/nop-spring-demo2`（死模块，不在 reactor；其 3.2.0 覆盖随 WI12 删除消灭——本轮裁定不动死文件）。
- `nop-benchmark-orm` 的 spring/spring-data-jpa 面（WI13）。
- SCA（spring-cloud-alibaba）训练线换代。
- Quarkus 侧任何改动。

## Execution Plan

### Phase 1 - BOM 升线 + 插件版本单点化

Status: completed
Targets: `nop-kernel/nop-dependencies/pom.xml`, root `pom.xml`, 6 个插件消费 pom

- Item Types: `Fix`（版本升线与硬编码清除均为已确认的版本漂移缺陷修复）

- [x] nop-dependencies：`spring-boot.version` 3.5.14→4.1.1、`spring-cloud.version` 2025.0.2→2025.1.3、`spring-ai.version` 1.1.5→2.0.1
- [x] root pom：`<spring-boot.version>4.1.1</spring-boot.version>` 属性（镜像 WI5 quarkus 先例）+ pluginManagement 增加 `spring-boot-maven-plugin`（version=`${spring-boot.version}`）
- [x] parent 链模块 3 处删插件 `<version>`（继承 root pluginManagement）：nop-spring-mcp-server / nop-spring-gateway-demo-service / nop-spring-simple-demo
- [x] 独立 pom 3 处保留显式字面量并升到 4.1.1（附裁定注释）：nop-spring-demo(3.4.5→4.1.1) / nop-spring-gateway(3.4.5→4.1.1) / nop-spring-report-demo(3.5.14→4.1.1)
- [x] BOM import 顺序复核：quarkus-bom 仍在 spring 三 BOM 之前（WI5 裁定保持）

Exit Criteria:

- [x] `grep -n "spring-boot.version\|spring-cloud.version\|spring-ai.version" nop-kernel/nop-dependencies/pom.xml` 显示 4.1.1 / 2025.1.3 / 2.0.1
- [x] `grep -rn "spring-boot-maven-plugin" --include=pom.xml`：parent 链 3 位点无本地 `<version>`；独立 pom 3 位点显式 4.1.1；root pluginManagement 存在唯一属性化版本定义
- [x] `./mvnw help:evaluate -Dexpression=spring-boot.version`（root 上下文）= 4.1.1（实测 stdout 4.1.1）
- [x] `ai-dev/logs/` 对应日期条目已更新（10-11.md WI6 节）

### Phase 2 - starter 模块化迁移 + mybatis SB4 线

Status: completed
Targets: `nop-spring/nop-spring-file`, `nop-spring/nop-spring-web-starter`, `nop-demo/nop-spring-report-demo`, `nop-demo/nop-rpc-server-demo`, `nop-spring/nop-spring-delta`

- Item Types: `Fix`（deprecated starter 与 SB3 线 mybatis 是已确认的漂移）

- [x] `spring-boot-starter-web` → `spring-boot-starter-webmvc`：nop-spring-file、nop-spring-web-starter、report-demo、rpc-server-demo
- [x] nop-spring-delta：mybatis-spring-boot-starter 3.0.2→4.1.0（SB4 官方线）
- [x] rpc-server-demo 的 SCA 2022.0.0.0 保持不动，构建验证后在 Deferred 节裁定（build3/4 rpc-server-demo install 绿——见 Phase 3 归因）

Exit Criteria:

- [x] 全仓活 pom `grep "spring-boot-starter-web<"` 零命中（demo2 死文件除外，实测唯一残留 demo2:81）
- [x] `grep "mybatis-spring-boot-starter" -A1 nop-spring/nop-spring-delta/pom.xml` = 4.1.0
- [x] `ai-dev/logs/` 对应日期条目已更新（10-11.md WI6 节）

### Phase 3 - 编译破损消化 + 测试门

Status: completed
Targets: `nop-spring/*`, `nop-ai/nop-spring-mcp-server*`, `nop-demo/nop-spring-*`, `nop-demo/nop-rpc-*`

- Item Types: `Fix`（SB4/SF7 废弃 API 移除引发的破损；一律用官方标准替代）

- [x] 执行期新发现（build1 实证）：SB4 的 spring-boot-dependencies 不再 import testcontainers-bom（SB3.5 最后管理 1.21.4），Q4 从不管理——nop-dao/nop-orm/nop-orm-geo/nop-orm-tdengine/nop-nosql-lettuce 的 testcontainers 依赖失去版本管理，模型构建期即红。修复 = nop-dependencies 显式 import testcontainers-bom 1.21.4（SB 最后管理线，不跳 2.x 大版本）+ "why not BOM-managed" 注记。
- [x] `JAVA_HOME=$JDK25 ./mvnw clean install -DskipTests -T 1C -fae` 全量跑通；对照 WI5 基线归因每一处非绿（build5 终门 395 绿；非绿 4 = 3 慢性 gen-aop + 1 跨会话在途，逐条归因见下）
- [x] 编译破损逐点修复（全部 SB 官方标准替代，零降级 hack）：
  - `NopPlatformReadyIndicator`：`org.springframework.boot.actuate.health.{Health,HealthIndicator}` → `org.springframework.boot.health.contributor.{Health,HealthIndicator}`（SB4 actuator 模块化，新家 spring-boot-health，经 starter-actuator 传递可用）
  - `SpringSecurityConfig`：`http.authorizeRequests().requestMatchers(...)` → `http.authorizeHttpRequests(auth -> auth.requestMatchers(...))`（Spring Security 7 移除已废弃 authorizeRequests）
  - `TestSpringBatch`：Batch 6 包迁移 `org.springframework.batch.core.{Job,JobExecution}` → `core.job.*`、`JobParameters` → `core.job.parameters.*`（JobLauncher 位点不变）
  - `MyReader/MyWriter/MyProcessor`：Batch 6 infrastructure 改名 `org.springframework.batch.item.*` → `org.springframework.batch.infrastructure.item.*`
  - `TestSpringBatch/TestDeltaMyBatis`：`@AutoConfigureTestDatabase` `org.springframework.boot.test.autoconfigure.jdbc` → `org.springframework.boot.jdbc.test.autoconfigure`（新家 spring-boot-jdbc-test，demo pom 补 test 依赖，BOM 管理）
- [x] 归因排除（非 WI6）：`nop-ai-core` 编译红 = 并发 nop-ai 会话在途改动（getString→getStringByPath 中途态，mtime 实证；**该会话提交 a789e359ae 落库后 build5 转绿——归因闭环**）；`nop-stream-runtime` testCompile 红 = 并发 nop-stream 会话 plan 2435 C1 已提交中间态（terminalReportExecutorForTest 测试钩子未落地，C2 待落——非本 plan 可修）；`nop-auth-service` gen-aop 红 = **worktree 二分实证（WI5 提交 c01d6c42c3 无 SB 升线原样复现）与 sys-dao/job-dao/tcc-dao/retry-engine 同属既有 in-process javac 缺陷族**（roadmap 跨切面预警项），成员逐轮漂移，非 SB4 归因
- [x] `@SpringBootTest` 载体聚焦测试：nop-spring-core-starter 13/13 绿、web-starter 22/22 绿（审计独立复跑确认）；gateway 8 run 0 fail（LmStudioGatewayTest 3 例需外部 LLM 端点环境跳过——LocalServerPort 迁移经编译+上下文加载验证）；spring-demo：TestSpringBatch compile 绿（pre-existing `@Disabled`，Batch 6 迁移为 compile-only 覆盖——验证强度如实记录）、TestDeltaMyBatis compile 绿 / 运行时被 v70 陈旧 jar 阻断（显式裁定见 Deferred 节第 3 条）
- [x] rpc-server-demo（build3+4 双绿）/ 各 spring demo install 绿：report-demo、simple-demo、demo-no-orm、security-demo 于 build4 SUCCESS；spring-demo / gateway / gateway-demo-service 于级联解除后单模块补验 install 绿

Exit Criteria:

- [x] 全量 install 与 WI5 基线同态（build5：395 绿；非绿恰 = WI5 已文档化的慢性 gen-aop 族成员 + 跨会话在途 stream-runtime，逐条归因入 plan 与日志；scope-change 裁定见 Deferred 节第 3 条）
- [x] 聚焦测试全绿，输出含 "Tests run: N, Failures: 0, Errors: 0"（core-starter 13、web-starter 22、gateway 8；spring-demo 运行态阻断已显式裁定——见 Deferred 节第 3 条）
- [x] 修复点清单（文件 → 旧 API → 标准 API）已写入 `ai-dev/logs/2026/10-11.md`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（deprecated starter/插件硬编码/版本漂移清零；testcontainers 失管修复）
- [x] 所有 in-scope confirmed contract drifts 已收敛（单一 Spring 训练线 4.1.1/2025.1.3/2.0.1，依赖树实检）
- [x] 行为/契约结果已达成（全 reactor JDK25 下编译保持基线态，非绿全部归因为既有缺陷族/跨会话在途）
- [x] 必要 focused verification 已完成（受影响模块 clean test；运行态阻断项显式裁定）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（4 项 deferred 全部为 out-of-WI6-scope 的既有/跨会话项，附裁定理由）
- [x] 受影响的 owner docs 已同步到 live baseline——审计 grep 实证 docs-for-ai 无 SB 版本表述，docs-for-ai/03-modules 下无 nop-spring 模块文档：**No owner-doc update required**
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（agent_3ba2ad71，首轮 DENY 4 项修复清单已全部执行）
- [x] Anti-Hollow Check：closure audit 已验证版本解析真实性——(a) dependency:tree 实检 spring-boot 4.1.1 + spring-core/spring-web/spring-webmvc 7.0.9 训练线真实解析；(b) 审计独立复核 6/6 插件消费模块 effective-pom 均解析 spring-boot-maven-plugin 4.1.1，零 latest 漂移
- [x] `./mvnw clean install -DskipTests -T 1C -fae`（JDK 25）通过（build5：395 绿 + 4 非绿逐条归因 = 容差内）
- [x] 受影响模块 `./mvnw test` 通过（core-starter/web-starter/gateway 绿；spring-demo 运行态裁定见 Deferred 节第 3 条）
- [x] checkstyle / 代码规范检查通过（imports 分组保持；全部改动 +47/-24 行，最小 diff）

## Deferred But Adjudicated

### nop-rpc-server-demo 的 spring-cloud-alibaba 2022.0.0.0 硬编码

- Classification: `watch-only residual`
- Why Not Blocking Closure: SCA 上游尚无 SB4 兼容训练线（2025.x 系列未发布 SB4 对齐版本）；该 import 为 pre-existing 错配（SB3.5 时期即存在），本轮 Cloud 2025.1.3 升线若构建绿即未引入回归。真修复依赖上游 SCA 发版。
- Successor Required: `no`
- Successor Path: SCA 发布 SB4 兼容线后由后续 mission 跟进

### nop-spring-demo2 的本地 SB 3.2.0 BOM 覆盖

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: demo2 已注释出 reactor（nop-demo/pom.xml:25），不参与构建、不影响训练线解析；WI12 整目录删除时自然消灭，提前改死文件违反最小 diff 原则。
- Successor Required: `yes`
- Successor Path: WI12（删除 obsolete demos）

### TestDeltaMyBatis 运行态验证（scope-change 裁定，非 SB4 阻断）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该测试的 SB4 迁移面（`@AutoConfigureTestDatabase` 新坐标 + Batch 6 包迁移）已编译级验证；运行态被与 SB4 无关的缺陷链阻断——(a) m2 中 auth-service v70 陈旧 `AuditServiceImpl__aop` jar（Oct 10 JDK26 时代产物，WI2 日志已记录同族 v70 问题）；(b) 刷新链被跨会话缺陷封死：full build 中 auth-service 因上游 nop-stream-runtime（并行 nop-stream 会话 plan 2435 C1 已提交的坏测试，C2 待落）级联 SKIPPED；standalone 构建中 auth-service 的 gen-aop 平台缺陷 3 连确定性失败（worktree 二分实证 WI5 状态同样失败，见上方归因项）。三项均非本 plan scope 内缺陷，修复归属：gen-aop 平台缺陷（roadmap 跨切面"forked compilation"预案，登记 follow-up）+ nop-stream 会话 C2。
- Successor Required: `no`
- Successor Path: gen-aop 缺陷修复归入后续平台治理（见 Non-Blocking Follow-ups）；nop-stream-runtime 由其会话 plan 2435 C2 收口后，auth-service 级联自然解除

## Non-Blocking Follow-ups

- surefire 版本碎片治理（非 SB4 引入，不阻塞训练线）：root 实际生效的是 build/plugins 硬编码 3.2.2（`pom.xml:173` 一带），`:61 maven-surefire-plugin.version=3.0.0-M4` 与 `:82 surefire-plugin.version=3.0.0-M5` 是孤儿属性；模块级硬编码 3.1.2 另有 3 处（nop-spring-mcp-server、nop-spring-demo:220、nop-spring-gateway:146）。后续插件治理统一收口。
- CI（GitHub Actions）在 push 后首跑验证全链路（本机 JDK 25 与 CI JDK 25 同版，风险低）。
- **gen-aop 平台缺陷治理**（本轮实证收窄）：`GenAopProxy` 经 `JdkJavaCompiler.getDefaultClassPaths()` 取 `java.class.path`（exec:java 上下文下仅 maven-wrapper.jar，本模块 target/classes 类不可解析）——auth-service standalone 3 连确定性失败、全量构建中成员逐轮漂移（build2 绿/build3 红/build4 skip/build5 skip）。对症修复 = roadmap 跨切面预案的 forked compilation 或 exec realm classpath 显式注入 `nop-codegen`/`nop-javac`；独立 successor 立项。

## Closure

Status Note: WI6 三 deliverable 全部落地并经独立审计复核：单一 Spring 训练线 4.1.1/2025.1.3/2.0.1（依赖树实检）、deprecated starter 清零（-web→-webmvc ×4 + actuate 零残留）、插件版本单点化（parent 链继承 + 独立 pom 字面量，6/6 effective-pom 解析 4.1.1 零漂移）、SB4 破损面 5 处全部官方标准替代、testcontainers 失管新缺陷修复（首个 "why not BOM-managed" 钉，为 WI10 政策先例）。全量门 build5 395 绿，非绿 4 项全部归因为既有 gen-aop 缺陷族/跨会话在途（worktree 二分实证非 SB4 归因）。审计首轮 DENY 的 4 项修复（文本一致性/scope-change 裁定/demo 补验/验证强度记录）已全部执行。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 closure auditor（fresh session，与实现者不同 task）
- Audit Session: agent_3ba2ad71-2860-4051-bb4f-474192a960eb
- Evidence:
  - Phase 1 Exit Criteria 4/4 PASS（版本属性/插件结构/help:evaluate=4.1.1/log 在档）
  - Phase 2 Exit Criteria 3/3 PASS（starter-web 残留仅 demo2 死文件/mybatis 4.1.0/log 在档）
  - Anti-Hollow (a)：dependency:tree 实检 spring-boot:4.1.1 + spring-core/spring-web/spring-webmvc:7.0.9、spring-ai 族 2.0.1、mybatis 4.1.0 真实解析
  - Anti-Hollow (b)：审计独立复核 6/6 插件消费模块 effective-pom 解析 spring-boot-maven-plugin=4.1.1，零 4.2.0-M2 漂移
  - 全量门：`_tmp/wi6-build5.log` Reactor Summary 实测 395 SUCCESS；非绿 4（job-dao/tcc-dao/retry-engine gen-aop + stream-runtime 跨会话）逐条归因
  - 修复点真实性：NopPlatformReadyIndicator/SpringSecurityConfig/TestSpringBatch/MyReader/MyWriter/MyProcessor 源码逐文件核对 PASS；全仓 actuate 导入零残留
  - 归因诚实性：worktree 二分日志落盘（`_tmp/wi6-bisect-wi5*.log`）；并发会话落库提交（a789e359ae/547be576f8）与归因吻合——nop-ai-core 在 build5 转绿形成归因闭环
  - Deferred 分类：4 项均在 guide Allowed Deferred Classifications 清单内，附 non-blocking 理由；scope 溢出检查 PASS（35 项 git status 中本 plan 14 文件全在 In Scope，plan 2380 文件零卷入）
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0（本 Closure 段写入后复跑）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --severity high` 退出码 0（无 high/critical 空壳发现）

Follow-up:

- gen-aop 平台缺陷治理 successor（见 Non-Blocking Follow-ups 第 3 条，含本轮实证数据）
- surefire 碎片治理 / CI push 后首跑观察（原 Follow-up 保持）
