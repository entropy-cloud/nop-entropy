# JDK 25 / Spring Boot 4.1.1 / Quarkus 4.0.0.Beta1 平台升级基线分析(mission jdk25-sb4-q4 WI0)

> Status: resolved
> Date: 2026-10-11
> Scope: 平台升级的事实基座——上游迁移事实冻结、quarkus-bom 4.0.0.Beta1 × spring-boot-dependencies 4.1.1 坐标级比对、nop-entropy 全仓版本 pin 前照、nop-dependencies 决策矩阵。零代码/pom 变更。
> Conclusion: 双 BOM 直接管理交集 90 坐标,分歧恰 31 个(与 roadmap "~31" 精确吻合,方法有效性经 roadmap 示例坐标全部命中交叉验证);本仓 JDK pin 共 98 行、横跨 11/17/21/22 四个基线,框架 pin 分裂(quarkus 3.35.1/3.29.2/3.21.4,spring-boot 3.5.14/3.2.0);nop 58 个活 pin 中仅 1 个被双 BOM 同版本管理,升级面真实存在。执行计划按 `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI1-WI16 推进,本档为共同事实来源。
> Timeliness: Q4 侧数据为 4.0.0.Beta1(2026-10-01,pre-GA);GA 后 WI16 须按 GA BOM 重算第 2 节。仓库侧前照为 2026-10-11 工作区快照(不含后续 WI 的落地效果)。

## Context

- 回答问题:JDK25+SB4+Q4+GraalVM25 升级 roadmap(`ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md`)WI0 要求把迁移事实、双 BOM 依赖决策矩阵、本仓 pin 清单冻结为仓内分析文档,作为后续 16 个执行计划的共同基座。
- 涉及范围:全仓 ~470 个 pom、`.github/workflows/`、`.workflow/`、87 个 Dockerfile、`nop-kernel/nop-dependencies`。
- 约束:roadmap "Decisions already taken" 一节为人定裁决,本文只冻结事实与旗标,不做执行裁定。

## 方法与数据来源

- **BOM 抓取**:Maven Central 直接下载 `io.quarkus.platform:quarkus-bom:4.0.0.Beta1` 与 `org.springframework.boot:spring-boot-dependencies:4.1.1` 的 pom(抓取日 2026-10-11),脚本比对两 BOM `<dependencyManagement>` **直接条目**(脚本与中间数据在 `_tmp/wi0/`,不入库;全量结果落档于本文第 2、4 节,以本文为持久锚)。
- **方法局限(消费矩阵前必读)**:
  - quarkus-bom 4.0.0.Beta1 **零 `import`**(2161 条全为直接条目)——Q4 侧比对无盲区。
  - spring-boot-dependencies 4.1.1 导入 46 个 BOM(jackson-bom ×2/netty-bom/micrometer-bom/mockito-bom/pulsar-bom/spring-framework-bom/reactor-bom/junit-bom 等),未递归展开——SB4 侧判定为 `-`(无直接管理)的坐标**不排除**经 imported BOM 的间接管理(已知实例:`org.apache.pulsar:pulsar-client-original` 实为 Q4-only 判定,SB4 经 pulsar-bom 间接管理)。
  - 因此矩阵 verdict 语义为"**无直接管理**"而非"完全无管理";headline 结论(31 坐标分歧)不受影响——31 与 roadmap 自称 "~31" 精确吻合,roadmap 列举的 7 个示例坐标(jakarta.servlet-api/jakarta.persistence-api/slf4j-api/hamcrest/jakarta.json.bind/jakarta.websocket/jspecify)全部在清单中。
- **本仓前照**:grep 全仓 pom(Dockerfile/FROM、`java.version`、`maven.compiler.*`、`.version>` 属性)、`.github/workflows/*.yml`、`.workflow/*.yml`;排噪规则:排除 `target/` 与 `_tmp/`。
- **上游事实**:标注 [BOM-verified] 的条目由本次抓取的 BOM 原文直接验证;标注 [roadmap] 的来自 roadmap 及其 Sources(Quarkus Migration Guide 4.0 / Spring Boot 4.0 Migration Guide / system-requirements);标注 [2026-10-09] 的来自 `ai-dev/analysis/2026-10/2026-10-09-quarkus4-vs-springboot4-dependency-alignment.md`。

## 1. 上游事实冻结

### 1.1 Java 基线

- Quarkus 4.x 最低 Java 21([2026-10-09],PR #55147);Spring Boot 4.1 需 Java 17+、Spring Framework 7.0.9+([2026-10-09])。
- **目标基线:JDK 25(LTS)全仓统一**——compile `release=25`、构建与 CI 均在 25([roadmap] Decisions)。JDK 22 簇(nop-find/nop-rg)与 21 叶子(nop-ai-rag/skills、nop-commons-java21)被其收编。
- FFM 精确事实(本仓核实):`java.lang.foreign` 直接用户为 **nop-rg core/vector/benchmark 三模块**;nop-rg-cli 源码零 FFM(pom 注释:22 门控为"与 nop-rg-core 一致");nop-find-core 零直接 FFM(pom 注释:因依赖 nop-rg-core 而 22 门控)。FFM 在 22+ 为 final API,25 下继续可用([roadmap] Decisions)。
- 已知环境事实:本机默认 JDK 26.0.1(Zulu)+ 可选 25.0.1(Oracle),`--release 25` 可行;CI 现为 JDK 21(见 3.3)。日志在案的 JDK 26 构建 bug(gen-aop-proxy-fail,nop-job-dao/nop-tcc-dao 偶发)与本升级无依赖关系,但提示 JDK 26 环境下 codegen 插件内部 javac 兼容性脆弱——切 `release=25` 后如遇同类并发编译问题,roadmap 已预案 forked compilation([roadmap] Cross-cutting)。

### 1.2 容器基座

- UBI 10 是 Quarkus 4 的默认基座线;JDK 25 基础镜像坐标 `registry.access.redhat.com/ubi10/openjdk-25`([roadmap] Framework reuse 表)。
- 本仓 87 个 tracked Dockerfile 现状见 3.4;4 个 codegen 模板是 Protected Area(见 3.4 末)。

### 1.3 Hibernate DB 版本下限

[roadmap] WI0 Deliverables 转录(SB4 system-requirements):升级后各数据库的最低支持版本为 **Db2 12.1 / MariaDB 11.4 / MSSQL 16 / MySQL 8.4 / Oracle 23 / PostgreSQL 16**。影响面评估提示:本仓平台自身不依赖 JPA/Hibernate(nop-orm 为自有实现),该下限只约束 benchmark 对比面(WI13)与用户应用的 DB 选择,属于文档性事实。

### 1.4 Jackson 3(双框架共同的 Jackson-3-first)

- rename 面:`com.fasterxml.jackson.core`/`jackson-databind`(含 `ObjectMapper`)→ `tools.jackson.*`;**annotations 不改名**,继续解析在 Jackson-2 线 `com.fasterxml.jackson.annotation`([roadmap] Decisions/Cross-cutting)。
- [BOM-verified] 双轨并存:Q4 直接管理 `tools.jackson.core:jackson-databind` 3.1.4 **且** `com.fasterxml.jackson.core:jackson-databind` 2.22.2;SB4 管理 `tools.jackson:jackson-bom` 3.1.5 **且** `com.fasterxml.jackson:jackson-bom` 2.21.5。**两侧 Jackson 3 版本分歧(3.1.4 vs 3.1.5)**——按导入顺序 quarkus-bom 先,3.1.4 胜出。
- CDI/配置面:两框架的 Jackson 3 集成均提供 CDI `JsonMapper` 注入路径,Spring 侧为 `JsonMapper` bean + `spring.jackson.*` 配置命名空间迁移([roadmap] WI0 Deliverables 转录上游 guide)。
- 本仓真实面(核实):`jackson-databind`/`ObjectMapper` 仅 8 个文件(4×nop-benchmark-json/eishay、3×nop-ai-api 测试、1×nop-api-core 测试),`nop-spring`/`nop-quarkus` 零 databind 依赖——迁移面小(WI7)。

### 1.5 Spring Boot 4 变化面

[roadmap] WI0 Deliverables 转录 SB4 Migration Guide:

- starter 模块化拆分:`spring-boot-starter-web` 等组合 starter 拆为 `-webmvc`/`-restclient`/`-webservices`/`-security-oauth2-*` 等细粒度 starter;nop-spring 若引用组合 starter 需对齐(WI6)。
- 测试面:`@MockitoBean` 取代 `@MockBean`;`RestTestClient` 为新测试客户端。
- 本仓核实:`Jackson2ObjectMapperBuilderCustomizer`、`@JacksonComponent`、`@JacksonMixin`、`spring.jackson.parser.*` 全仓零命中([roadmap] WI7 预判成立)——SB4 Jackson 配置面迁移在本仓无实际落点,仅存 8 个 databind 站点。

### 1.6 OTel / Micrometer

- OTel:Q4 默认 OTLP exporter 改 `http/protobuf`、端口 4318(gRPC 4317 → HTTP 4318),默认 trace 采样率 10%([roadmap] Decisions:采纳,不回拨);Q4 "OpenTelemetry 默认开启"([2026-10-09] 4.x 跳变清单)。[BOM-verified] Q4 管 `io.opentelemetry:opentelemetry-api` 1.63.0。
- Micrometer:Q4 转 Prometheus client **v1**(`io.micrometer.prometheusmetrics`),指标命名随之变化([roadmap] WI15);[2026-10-09] Micrometer 1.17.1 两侧同版本、prometheus client v1 在 Q4 4.x 跳变清单。

### 1.7 GraalVM 25

- [BOM-verified] Q4 直接管理 `org.graalvm.sdk:graal-sdk` 与 `org.graalvm.sdk:nativeimage` @ **25.0.4.1**(版本下限即此)。
- **[BOM-verified 修正]** `org.graalvm.polyglot:js-community`、`org.graalvm.polyglot:polyglot`、`org.graalvm.truffle:*` **均不在** quarkus-bom 4.0.0.Beta1 管理范围(Q4 零 import,无间接路径)——roadmap 复用表 "js-community(Q4-managed @25.0.4.1)" 的管理方式表述不准,WI8 的"丢硬编码继承 Q4 管理"前提不成立:js-community 若要上 25 线需**显式 pin 25.0.4.1**(旗标见 4.3-G5)。`org.graalvm.js:js` 为死坐标,两 BOM 均不管理([roadmap],本仓该块已是注释态)。
- 本仓现状:`graalvm.version=23.1.2`(nop-dependencies :30),nop-js 的 js-community **与** polyglot 双硬编码 23.1.2;nop-xlang-truffle `truffle.version=25.2.4`(与 25 线一致,保持)。

### 1.8 no-Jakarta-Cache finding(WI14 事实基座)

- **不存在 `jakarta.cache` 发行版**:JSR-107(JCache)从未发布 jakarta 命名空间版本;`javax.cache:cache-api` 上游停滞。
- [BOM-verified] SB4 仍管理 `javax.cache:cache-api` 但仅 @ **1.1.1**(javax 线旧版);Q4 不管理。
- [BOM-verified] Ehcache 现状:SB4 管 `org.ehcache:ehcache` @ 3.12.0;Ehcache 3 的 jakarta 支持经 **classifier `jakarta`** 提供(同坐标双变体)——WI14 替代坐标即 `org.ehcache:ehcache:3.x:jakarta`(roadmap 决策),Nop 公共面继续走自有 `ICache` 抽象。
- 本仓现状:`javax.cache:cache-api` 消费方为 nop-commons 与 nop-hazelcast/nop-hazelcast-cache(`JavaxCache`/`HazelcastCache` 实现)。

## 2. 双 BOM 坐标比对(quarkus-bom 4.0.0.Beta1 × spring-boot-dependencies 4.1.1)

### 2.1 总览

| 指标 | 值 |
|---|---|
| quarkus-bom 直接管理坐标 | 2161 |
| spring-boot-dependencies 直接管理坐标 | 647 |
| 交集(两侧都直接管理) | 90 |
| 其中同版本 | 59 |
| **分歧** | **31** |

[2026-10-09] 的 37 组人工抽样与本 90 组全集不矛盾(抽样 ⊂ 全集方向);三关键件结论一致(Hibernate ORM 8.0.0.Beta3 vs 7.4.5.Final、Liquibase 4.33 vs 5.0.3、MySQL Connector/J 26.7 vs 9.7)。

### 2.2 31 坐标分歧全清单

族谱:Hibernate ORM 家族 ×8、Flyway 模块 ×6、Elasticsearch 客户端 ×2、其余单项 ×15(含 liquibase-core)。

| 坐标 | Q4 4.0.0.Beta1 | SB4 4.1.1 |
|---|---|---|
| `co.elastic.clients:elasticsearch-java` | 9.4.7 | 9.4.5 |
| `co.elastic.clients:elasticsearch-rest5-client` | 9.4.7 | 9.4.5 |
| `com.fasterxml:classmate` | 1.7.1 | 1.7.3 |
| `com.google.code.gson:gson` | 2.14.0 | 2.13.2 |
| `com.graphql-java:graphql-java` | 26.0 | 25.0 |
| `com.microsoft.sqlserver:mssql-jdbc` | 13.6.0.jre11 | 13.4.0.jre11 |
| `com.mysql:mysql-connector-j` | 26.7.0 | 9.7.0 |
| `commons-codec:commons-codec` | 1.22.1 | 1.21.0 |
| `jakarta.json.bind:jakarta.json.bind-api` | 3.0.3 | 3.0.2 |
| `jakarta.persistence:jakarta.persistence-api` | 4.0.0-M7 | 3.2.0 |
| `jakarta.servlet:jakarta.servlet-api` | 6.0.0 | 6.1.0 |
| `jakarta.websocket:jakarta.websocket-api` | 2.1.1 | 2.2.0 |
| `org.flywaydb:flyway-core` | 12.0.0 | 12.4.0 |
| `org.flywaydb:flyway-database-db2` | 12.0.0 | 12.4.0 |
| `org.flywaydb:flyway-database-oracle` | 12.0.0 | 12.4.0 |
| `org.flywaydb:flyway-database-postgresql` | 12.0.0 | 12.4.0 |
| `org.flywaydb:flyway-mysql` | 12.0.0 | 12.4.0 |
| `org.flywaydb:flyway-sqlserver` | 12.0.0 | 12.4.0 |
| `org.hamcrest:hamcrest` | 2.2 | 3.0 |
| `org.hibernate.orm:hibernate-community-dialects` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.hibernate.orm:hibernate-core` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.hibernate.orm:hibernate-envers` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.hibernate.orm:hibernate-graalvm` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.hibernate.orm:hibernate-jcache` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.hibernate.orm:hibernate-processor` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.hibernate.orm:hibernate-spatial` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.hibernate.orm:hibernate-vector` | 8.0.0.Beta3 | 7.4.5.Final |
| `org.jspecify:jspecify` | 1.0.0 | 1.0.1 |
| `org.liquibase:liquibase-core` | 4.33.0 | 5.0.3 |
| `org.slf4j:slf4j-api` | 2.0.19 | 2.0.18 |
| `org.yaml:snakeyaml` | 2.7 | 2.6 |

裁定输入提示(非裁定):导入顺序 quarkus-bom 在前,上表全部坐标的生效版本 = Q4 列;其中对 nop 有害与否由 WI10 逐坐标裁定(如 `jakarta.persistence-api` 4.0.0-M7 是 milestone 版,对 benchmark JPA 面(WI13)可能有害,SB4 的 3.2.0 反而更稳)。

### 2.3 59 组同版本参照(全文)

<details><summary>展开(59 行)</summary>

| 坐标 | 共同版本 |
|---|---|
| `com.github.ben-manes.caffeine:caffeine` | 3.2.4 |
| `com.github.ben-manes.caffeine:jcache` | 3.2.4 |
| `com.h2database:h2` | 2.4.240 |
| `com.ibm.db2:jcc` | 12.1.5.0 |
| `com.oracle.database.ha:ons` | 23.26.3.0.0 |
| `com.oracle.database.ha:simplefan` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ojdbc11` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ojdbc11-production` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ojdbc17` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ojdbc17-production` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ojdbc8` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ojdbc8-production` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:rsi` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ucp` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ucp11` | 23.26.3.0.0 |
| `com.oracle.database.jdbc:ucp17` | 23.26.3.0.0 |
| `com.oracle.database.nls:orai18n` | 23.26.3.0.0 |
| `com.oracle.database.security:oraclepki` | 23.26.3.0.0 |
| `com.oracle.database.xml:xdb` | 23.26.3.0.0 |
| `com.oracle.database.xml:xmlparserv2` | 23.26.3.0.0 |
| `io.micrometer:micrometer-registry-stackdriver` | 1.17.1 |
| `jakarta.activation:jakarta.activation-api` | 2.1.4 |
| `jakarta.annotation:jakarta.annotation-api` | 3.0.0 |
| `jakarta.inject:jakarta.inject-api` | 2.0.1 |
| `jakarta.json:jakarta.json-api` | 2.1.3 |
| `jakarta.mail:jakarta.mail-api` | 2.1.5 |
| `jakarta.transaction:jakarta.transaction-api` | 2.0.1 |
| `jakarta.validation:jakarta.validation-api` | 3.1.1 |
| `jakarta.ws.rs:jakarta.ws.rs-api` | 4.0.0 |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.5 |
| `net.bytebuddy:byte-buddy` | 1.18.11 |
| `net.bytebuddy:byte-buddy-agent` | 1.18.11 |
| `org.apache.commons:commons-lang3` | 3.20.0 |
| `org.apache.httpcomponents.client5:httpclient5` | 5.6.4 |
| `org.apache.httpcomponents.client5:httpclient5-cache` | 5.6.4 |
| `org.apache.httpcomponents.client5:httpclient5-fluent` | 5.6.4 |
| `org.apache.httpcomponents.core5:httpcore5` | 5.4.3 |
| `org.apache.httpcomponents.core5:httpcore5-h2` | 5.4.3 |
| `org.apache.httpcomponents.core5:httpcore5-reactive` | 5.4.3 |
| `org.apache.httpcomponents:httpasyncclient` | 4.1.5 |
| `org.apache.httpcomponents:httpcore` | 4.4.16 |
| `org.apache.httpcomponents:httpcore-nio` | 4.4.16 |
| `org.apache.kafka:kafka-clients` | 4.2.1 |
| `org.apache.kafka:kafka-streams` | 4.2.1 |
| `org.apache.kafka:kafka-streams-test-utils` | 4.2.1 |
| `org.apache.kafka:kafka_2.13` | 4.2.1 |
| `org.awaitility:awaitility` | 4.3.0 |
| `org.awaitility:awaitility-kotlin` | 4.3.0 |
| `org.crac:crac` | 1.5.0 |
| `org.eclipse.angus:angus-mail` | 2.0.5 |
| `org.glassfish.jaxb:jaxb-core` | 4.0.9 |
| `org.glassfish.jaxb:jaxb-runtime` | 4.0.9 |
| `org.glassfish.jaxb:txw2` | 4.0.9 |
| `org.hibernate.validator:hibernate-validator` | 9.1.3.Final |
| `org.jboss.logging:jboss-logging` | 3.6.3.Final |
| `org.mariadb.jdbc:mariadb-java-client` | 3.5.10 |
| `org.postgresql:postgresql` | 42.7.13 |
| `org.quartz-scheduler:quartz` | 2.5.2 |
| `org.reactivestreams:reactive-streams` | 1.0.4 |

</details>

WI11 直接事实:[BOM-verified] `org.eclipse.angus:angus-mail` **2.0.5** 与 `jakarta.mail:jakarta.mail-api` **2.1.5** 双侧同版本管理——roadmap 的 angus-mail 迁移坐标成立。

### 2.4 管理风格差异(理解分歧来源)

- quarkus-bom:零 import、全直接条目(2161 条),版本集中在 `<properties>` + 少量敏感版本直书;依赖家族联动靠条目内注释与 enforcer。
- spring-boot-dependencies:约 647 条直接条目 + 46 个 imported BOM(jackson/netty/micrometer/mockito/pulsar/spring-framework/reactor/junit 等);其"管理面"实际大于直接条目数。

### 2.5 spring-cloud / spring-ai 版本线(Maven Central metadata,抓取日 2026-10-11)

- Spring Cloud:`2025.1.x` 线存在,最新 **2025.1.3**;SB4 兼容起点 2025.1.2([roadmap])。本仓现 pin `2025.0.2`(3.5 线,nop-dependencies :28)。
- Spring AI:`2.0.x` 线存在,最新稳定 **2.0.1**(另有 2.0.0-M8/RC1/RC2)。本仓现 pin `1.1.5`(nop-dependencies :29)。

## 3. 本仓前照清单(2026-10-11 快照)

### 3.1 JDK pin 全量归类(98 行,`java.version` + `maven.compiler.source|target|release`)

| 类别 | 模块 | 锚点(文件:行) |
|---|---|---|
| **build-chain 17(5 pom)** | root | `pom.xml:25`(java.version)、`pom.xml:28-30`(source/target/release) |
| | nop-kernel | `nop-kernel/pom.xml:24,27-29` |
| | nop-dependencies | `nop-kernel/nop-dependencies/pom.xml:15-16`(source/target) |
| | nop-demo | `nop-demo/pom.xml:47-49` |
| | nop-quarkus | `nop-quarkus/pom.xml:38-40` |
| **其余 17 pin 模块** | nop-jdk17-demo | `nop-demo/nop-jdk17-demo/pom.xml:15-17` |
| | nop-spring-demo | `nop-demo/nop-spring-demo/pom.xml:14,17-19` |
| | nop-spring-demo2 | `nop-demo/nop-spring-demo2/pom.xml:12-13`(source/target) |
| | nop-spring-gateway | `nop-demo/nop-spring-gateway/pom.xml:12,15-17` |
| | nop-spring-report-demo | `nop-demo/nop-spring-report-demo/pom.xml:16,19-21` |
| | nop-all-for-spring | `nop-spring/nop-all-for-spring/pom.xml:12-13` |
| | nop-report-for-spring | `nop-spring/nop-report-for-spring/pom.xml:12-13` |
| | nop-js | `nop-frontend-support/nop-js/pom.xml:15-16` |
| | nop-graph-api / nop-graph-core | `nop-graph/nop-graph-api/pom.xml:16-17`、`nop-graph/nop-graph-core/pom.xml:16-17` |
| | nop-bytecode | `nop-tools/nop-bytecode/pom.xml:32` |
| | nop-treesitter | `nop-tools/nop-treesitter/pom.xml:35` |
| **JDK22 簇(8 模块)** | nop-cli | `nop-runner/nop-cli/pom.xml:55-58` |
| | nop-cli-core | `nop-runner/nop-cli-core/pom.xml:24-27` |
| | nop-find-core / nop-find-cli | `nop-tools/nop-find/nop-find-core/pom.xml:17-20`、`nop-find-cli/pom.xml:17-20` |
| | nop-rg-core / -vector / -cli / -benchmark | `nop-tools/nop-rg/*/pom.xml:17-21` 四处 |
| **21(3 模块)** | nop-ai-rag | `nop-ai/nop-ai-rag/pom.xml:15-16`(source/target) |
| | nop-ai-skills | `nop-ai/nop-ai-skills/pom.xml:17-18` |
| | nop-commons-java21 | `nop-utils/nop-commons-java21/pom.xml:15-18` |
| **11(9 叶子)** | nop-file-api / nop-dyn-api / nop-auth-api / nop-oauth-api / nop-ai-api / nop-task-api / nop-biz-auth-api / nop-credential-api / demo-ddd-api | 各 `pom.xml:15-20` 一处 `<java.version>11</java.version>` |
| **quickstart 模板** | nop-stream 模板 | `nop-stream/quickstart/template/pom.xml:14`(release=17) |

复核命令:`grep -rn --include="pom.xml" -E "<java\.version>|<maven\.compiler\.(release|source|target)>" --exclude-dir=target . | grep -v _tmp | wc -l` → 98。

### 3.2 框架版本 pin

| 属性 | 值 | 位点 |
|---|---|---|
| `quarkus.platform.version` | 3.35.1 | `pom.xml:80`、`nop-kernel/nop-dependencies/pom.xml:26`、`nop-runner/nop-cli/pom.xml:63` |
| | **3.29.2** | `nop-kernel/pom.xml:78`(分歧位点 ①) |
| | **3.21.4** | `nop-spring/nop-all-for-spring/pom.xml:17`(分歧位点 ②) |
| | 3.21.4(注释态) | `nop-spring/nop-report-for-spring/pom.xml:17` |
| `spring-boot.version` | 3.5.14 | `nop-kernel/nop-dependencies/pom.xml:27` |
| | **3.2.0** | `nop-demo/nop-spring-demo2/pom.xml:15`(全仓唯一 SB 覆盖) |
| `spring-cloud.version` | 2025.0.2 | `nop-kernel/nop-dependencies/pom.xml:28` |
| `spring-ai.version` | 1.1.5 | `nop-kernel/nop-dependencies/pom.xml:29` |
| `graalvm.version` | 23.1.2 | `nop-kernel/nop-dependencies/pom.xml:30`(`graalvm-js.version` :122 引用之) |
| js-community / polyglot 硬编码 | 23.1.2 / 23.1.2 | `nop-frontend-support/nop-js/pom.xml:27-36` |
| `truffle.version` | 25.2.4 | `nop-kernel/nop-xlang-truffle/pom.xml:18` |
| `org.graalvm.js:js` | (注释态死块) | nop-dependencies :556 一带 |

WI5 统一目标:全部 quarkus 位点 → 4.0.0.Beta1(含 3.29.2/3.21.4 两个分歧位点与 demo 应用)。WI6 目标:SB 4.1.1 / Cloud 2025.1.x / AI 2.0.x,并拆 demo2 独立 BOM(WI12 删)。

### 3.3 CI JDK 与 legacy pipeline

- `.github/workflows/maven.yml`:`setup-java` ×3(:39、:84、:114)全部 `java-version: '21'`(build / invariant-gate / e2e)。
- `.github/workflows/compliance.yml`:`setup-java` ×3(:26、:73、:112)全部 21。
- `sync-docs-to-community.yml`:无 Java setup。
- `.workflow/master-pipeline.yml` jdkVersion **11**;`pr-pipeline.yml` jdkVersion **8**;`branch-pipeline.yml` jdkVersion **8**(均 Maven 3.6.3/3.3.9 旧线)→ WI4 删除。
- nop-cli lint 门禁:现 CI 无该门禁位点(21/22 不匹配时期被搁置)→ WI3 重启时按 `docs-for-ai/03-runbooks/nop-cli-and-lint-gate.md` 落位。

### 3.4 Dockerfile 基础镜像分布(87 个 FROM 行,排除 target/_tmp)

| 基础镜像 | 数量 | 去向 |
|---|---|---|
| `registry.access.redhat.com/ubi8/openjdk-17:1.16` | 40 | WI9a → ubi10/openjdk-25(其中 2 个是 codegen 模板 → WI9c) |
| `registry.access.redhat.com/ubi8/openjdk-17:1.11` | 1 | WI9a |
| `registry.access.redhat.com/ubi8/openjdk-21:1.21` | 2 | WI9a |
| `registry.access.redhat.com/ubi8/ubi-minimal:8.8` | 21 | WI9b(其中 1 个是 codegen 模板 → WI9c) |
| `registry.access.redhat.com/ubi8/ubi-minimal:8.5` | 1 | WI9b |
| `quay.io/quarkus/quarkus-micro-image:2.0` | 21 | WI9b → Q4 micro 线(其中 1 个是 codegen 模板 → WI9c) |
| `quay.io/quarkus/quarkus-micro-image:1.0` | 1 | WI9b |

codegen 模板 4 个(Protected Area,nop-codegen plan-first):`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/{appName}-app/src/main/docker/` 下 `Dockerfile.jvm.xgen` + `Dockerfile.legacy-jar.xgen`(均 17:1.16)、`Dockerfile.native.xgen`(ubi-minimal:8.8)、`Dockerfile.native-micro.xgen`(micro-image:2.0)→ WI9c。

### 3.5 本机工具链(WI0 执行环境)

macOS arm64;默认 JDK 26.0.1(Zulu 26.30.11,CRaC);`/usr/libexec/java_home` 另有 JDK 25.0.1(Oracle)。Maven wrapper 4.0.0-rc-5。构建 `release=25` 可行;CI(21)与本机(26/25)不一致是 WI3 要消除的漂移。

## 4. nop-dependencies 决策矩阵

### 4.1 活 pin 矩阵(58 唯一坐标,DM 条目 59 条——`nacos-client` 重复声明一次)

判定分布:**48 UNMANAGED(keep-pin)/ 6 Q4-ONLY / 3 SB4-ONLY / 1 BOTH-AGREE**。判定语义见"方法与数据来源"局限声明(UNMANAGED = 无**直接**管理)。

<details><summary>全表(58 行)</summary>

| 坐标 | nop pin | Q4 | SB4 | 判定 |
|---|---|---|---|---|
| `com.alibaba.csp:sentinel-core` | 1.8.6 | - | - | UNMANAGED |
| `com.alibaba.csp:sentinel-transport-simple-http` | 1.8.6 | - | - | UNMANAGED |
| `com.alibaba.nacos:nacos-api` | 2.3.2 | - | - | UNMANAGED |
| `com.alibaba.nacos:nacos-client` | 2.3.2 | - | - | UNMANAGED |
| `com.alibaba.nacos:nacos-common` | 2.3.2 | - | - | UNMANAGED |
| `com.alibaba.nacos:nacos-config` | 2.3.2 | - | - | UNMANAGED |
| `com.amazonaws:aws-java-sdk-s3` | 1.12.638 | - | - | UNMANAGED |
| `com.github.javaparser:javaparser-core` | 3.26.3 | 3.28.2 | - | Q4-ONLY |
| `com.github.javaparser:javaparser-symbol-solver-core` | 3.26.3 | - | - | UNMANAGED |
| `com.github.librepdf:openpdf` | 1.3.30 | - | - | UNMANAGED |
| `com.google.zxing:javase` | 3.5.3 | - | - | UNMANAGED |
| `com.graphql-java:java-dataloader` | 3.2.2 | - | - | UNMANAGED |
| `com.jayway.jsonpath:json-path` | 2.9.0 | - | 2.10.0 | SB4-ONLY |
| `com.jcraft:jsch` | 0.1.55 | - | - | UNMANAGED |
| `com.oracle.ojdbc:ojdbc8` | 19.3.0.0 | - | - | UNMANAGED(见旗标 G4) |
| `com.sun.mail:jakarta.mail` | 1.6.8 | - | - | UNMANAGED(见旗标 G6/WI11) |
| `com.vdurmont:semver4j` | 3.1.0 | - | - | UNMANAGED |
| `com.zaxxer:SparseBitSet` | 1.2 | - | - | UNMANAGED |
| `io.jsonwebtoken:jjwt-impl` | 0.12.6 | - | - | UNMANAGED |
| `io.protostuff:protostuff-runtime` | 1.8.0 | - | - | UNMANAGED |
| `io.quarkus.platform:quarkus-bom` | 3.35.1 | - | - | 平台 BOM 自身(WI5 统一) |
| `net.minidev:json-smart` | 2.5.2 | - | 2.6.0 | SB4-ONLY |
| `org.antlr:antlr4` | 4.13.0 | 4.13.2 | - | Q4-ONLY |
| `org.antlr:antlr4-maven-plugin` | 4.13.0 | - | - | UNMANAGED(见旗标 G3) |
| `org.antlr:antlr4-runtime` | 4.13.0 | 4.13.2 | - | Q4-ONLY |
| `org.apache.avro:avro` | 1.12.1 | 1.12.2 | - | Q4-ONLY |
| `org.apache.commons:commons-csv` | 1.8 | - | - | UNMANAGED |
| `org.apache.kafka:kafka-clients` | 4.3.1 | 4.2.1 | 4.2.1 | BOTH-AGREE(见旗标 G1) |
| `org.apache.lucene:lucene-analysis-common` | 9.7.0 | - | - | UNMANAGED |
| `org.apache.lucene:lucene-highlighter` | 9.7.0 | - | - | UNMANAGED |
| `org.apache.lucene:lucene-queryparser` | 9.7.0 | - | - | UNMANAGED |
| `org.apache.pdfbox:pdfbox` | 3.0.3 | - | - | UNMANAGED |
| `org.apache.pulsar:pulsar-client-admin-api` | 4.0.14 | - | - | UNMANAGED(见旗标 G2) |
| `org.apache.pulsar:pulsar-client-api` | 4.0.14 | - | - | UNMANAGED(见旗标 G2) |
| `org.apache.pulsar:pulsar-client-original` | 4.0.14 | 4.2.1 | - | Q4-ONLY(见旗标 G2) |
| `org.apache.pulsar:pulsar-common` | 4.0.14 | - | - | UNMANAGED(见旗标 G2) |
| `org.apache.pulsar:pulsar-transaction-common` | 4.0.14 | - | - | UNMANAGED(见旗标 G2) |
| `org.codehaus.janino:janino` | 3.1.12 | - | 3.1.12 | SB4-ONLY(同版本,丢钉无版本位移) |
| `org.commonmark:commonmark` | 0.22.0 | - | - | UNMANAGED |
| `org.commonmark:commonmark-ext-autolink` | 0.22.0 | - | - | UNMANAGED |
| `org.commonmark:commonmark-ext-gfm-tables` | 0.22.0 | - | - | UNMANAGED |
| `org.duckdb:duckdb_jdbc` | 1.5.6.0 | - | - | UNMANAGED |
| `org.geolatte:geolatte-geom` | 1.9.1 | 1.12 | - | Q4-ONLY |
| `org.jfree:jfreechart` | 1.5.6 | - | - | UNMANAGED |
| `org.keycloak:keycloak-common` | 20.0.1 | - | - | UNMANAGED |
| `org.locationtech.jts:jts-core` | 1.20.0 | - | - | UNMANAGED |
| `org.orbisgis:h2gis` | 2.2.0 | - | - | UNMANAGED |
| `org.rocksdb:rocksdbjni` | 9.11.2 | - | - | UNMANAGED |
| `org.springframework.ai:spring-ai-bom` | 1.1.5 | - | - | 平台 BOM 自身(WI6 升线) |
| `org.springframework.boot:spring-boot-dependencies` | 3.5.14 | - | - | 平台 BOM 自身(WI6 升线) |
| `org.springframework.cloud:spring-cloud-dependencies` | 2025.0.2 | - | - | 平台 BOM 自身(WI6 升线) |
| `tech.tablesaw:tablesaw-aggregate` | 0.43.1 | - | - | UNMANAGED |
| `tech.tablesaw:tablesaw-beakerx` | 0.43.1 | - | - | UNMANAGED |
| `tech.tablesaw:tablesaw-core` | 0.43.1 | - | - | UNMANAGED |
| `tech.tablesaw:tablesaw-html` | 0.43.1 | - | - | UNMANAGED |
| `tech.tablesaw:tablesaw-json` | 0.43.1 | - | - | UNMANAGED |
| `tech.tablesaw:tablesaw-jsplot` | 0.43.1 | - | - | UNMANAGED |
| `tech.tablesaw:tablesaw-saw` | 0.43.1 | - | - | UNMANAGED |

</details>

### 4.2 死属性清单(全仓零消费者:无 DM 条目引用、无模块 pom 引用;消费方 WI10,清除属 no-op)

`slf4j-api.version`(2.0.6)、`logback.version`(1.4.9)、`commons-lang3.version`(3.8.1)、`commons-collections4.version`(4.2)、`opencsv.version`(4.6)——均定义于 `nop-kernel/nop-dependencies/pom.xml` properties,但全仓无任何 `${...}` 引用。注意:logback/slf4j 的**生效版本**由导入的 BOM 决定(升级后 slf4j-api 生效 2.0.19,Q4 列胜出),与这些死属性无关。roadmap "Current baseline" 中把它们列为 "redundant pins" 的表述应理解为死属性清理项。

### 4.3 风险旗标(本研究产出,交消费 WI 裁定;本文不做执行裁定)

- **G1 → WI10(kafka-clients)**:pin 4.3.1 的 DM 注释(nop-dependencies :52-61)记载硬约束"kafka-clients 必须 ≥ Debezium 3.7.0.Final 构建版本(4.3.1),低于即 NoSuchFieldError 类启动失败"。双 BOM 管理 4.2.1 < 4.3.1——机械丢钉将跌破 Debezium floor。WI10 丢钉前必须核实 Debezium 3.7 的 kafka 下限,或保留 pin 并写"why not BOM-managed"注记。
- **G2 → WI5(pulsar 混栈)**:Q4 直接管理 `pulsar-client-original` @ 4.2.1;nop 族钉为 4.0.14(client-original/client-api/common/transaction-common/client-admin-api)。若丢族钉,Q4 只抬 client-original 至 4.2.1、其余停 4.0.14 → 重建 WI33 裁定过的混栈 NoSuchMethodError 形态。另:SB4 侧经 imported pulsar-bom 间接管理(本档方法局限的活实例)。WI33 注释(nop-dependencies :290-302)按 roadmap WI5 要求对照 Q4 BOM 复核更新。
- **G3 → WI10(antlr 家族联动)**:Q4 管 `antlr4`/`antlr4-runtime` @ 4.13.2,不管 `antlr4-maven-plugin`;三者现同钉 4.13.0。只丢前两者的钉会造成 4.13.2(依赖)与 4.13.0(插件)族内分裂——须三者联动裁定(齐丢或齐留)。
- **G4 → WI10(ojdbc 坐标迁移)**:现 pin 坐标为旧 groupId `com.oracle.ojdbc:ojdbc8` @ 19.3.0.0(两 BOM 均不管理);新坐标 `com.oracle.database.jdbc:ojdbc11` @ 23.26.3.0.0 双 BOM 同版本管理。消费位点:nop-cli-core、nop-dao(test)、nop-orm-geo 等(WI10 盘点)。
- **G5 → WI8(GraalVM 管理方式修正)**:roadmap 复用表与 WI8 前提"js-community 由 Q4 管理 @25.0.4.1"不成立(Q4 零 import,js-community/polyglot/truffle 均不在 2161 条直接条目中)。WI8 落地时 js-community/polyglot 须**显式 pin** `25.0.4.1`(与 graal-sdk 同线),truffle 保持 nop-xlang-truffle 现行 25.2.4 线并核对与 25.0.4.1 的兼容矩阵;`graalvm.version`/`graalvm-js.version` 属性链与死注释块(`org.graalvm.js:js`)一并清理。
- **G6 → WI11(mail 迁移)**:现 pin `com.sun.mail:jakarta.mail` @ 1.6.8(javax 命名空间 republish,DM :199-203,属性 :90);目标双 BOM 同版本管理 `org.eclipse.angus:angus-mail` 2.0.5 + `jakarta.mail:jakarta.mail-api` 2.1.5(2.3 节);`nop-quarkus-file` 现有 angus-mail exclusion + com.sun.mail 声明需反向解除(roadmap WI11)。

## Conclusion

- 双 BOM 共享面高度对齐(90 组中 59 同版本),但分歧恰 31 组且含结构项(Hibernate 8-Beta vs 7.4、jakarta.persistence-api milestone、servlet-api 6.0 vs 6.1)——导入顺序(qarkus-bom 先)是当前生效规则,WI10 须将其显式化并逐坐标裁定有害项。
- 本仓升级面属实:JDK 基线横跨 11/17/21/22 四档(98 pin 行)、quarkus 三版本分裂、SB 双轨(3.5.14 + demo2 的 3.2.0)、GraalVM 23.1.2 滞后、CI 全 21、容器基座全 ubi8/quarkus-2.0 线。
- nop 58 活 pin 中 48 个无 BOM 直接管理(keep-pin + "why" 注记)、10 个可 BOM 化(6 Q4-only + 3 SB4-only + 1 both-agree),其中 4 个带联动/风险约束(G1-G4)。
- 被否决的解读:否决"js-community 可继承 Q4 管理"(G5,数据反证);否决"丢 pin 一律安全"(G1/G2/G3 反例)。
- 后续工作:WI1(WI0 即其硬依赖)按 roadmap 顺序推进;矩阵/分歧清单/旗标以本文为唯一持久锚,WI10/WI5/WI8/WI11/WI14 直接引用对应节,不得重新推导。

## Open Questions

- [ ] Debezium 3.7.0.Final 的 kafka-clients 实际下限是否确为 4.3.1(G1,WI10 核)。
- [ ] Ehcache `3.12.0:jakarta` classifier 变体在 SB4 管理下的实际可用性(WI14 落地核;SB4 直管基线 3.12.0 为本文新事实)。
- [ ] truffle 25.2.4 与 graal-sdk 25.0.4.1 的版本兼容矩阵(G5,WI8 核)。
- [ ] WI16(GA 后)重算第 2 节时,Q4 若调整 Hibernate 8 Beta 线,分歧清单族谱需同步。

## References

- `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md`(mission jdk25-sb4-q4 的 roadmap,WI0 交付物定义)
- `ai-dev/analysis/2026-10/2026-10-09-quarkus4-vs-springboot4-dependency-alignment.md`(上游 37 组抽样、Q4 4.x 依赖跳变)
- `ai-dev/plans/2422-wi0-jdk25-sb4-q4-upgrade-baseline-analysis.md`(本分析的执行计划)
- `missions/jdk25-sb4-q4.json`(mission 配置)
- BOM 原文:`https://repo.maven.apache.org/maven2/io/quarkus/platform/quarkus-bom/4.0.0.Beta1/quarkus-bom-4.0.0.Beta1.pom`、`https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom`(抓取 2026-10-11)
- 比对脚本快照:`_tmp/wi0/compare_boms.py`、`_tmp/wi0/pin_matrix.py`(不入库;结果已全量落档本文 2.2/2.3/4.1)
- `ai-dev/logs/2026/10-11.md`(执行日记录)
