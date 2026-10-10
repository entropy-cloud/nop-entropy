# Quarkus 4 与 Spring Boot 4 三方库依赖对齐度调研

> Status: resolved
> Date: 2026-10-09
> Scope: 外部框架 BOM 对比（Quarkus 4.0.0.Beta1 vs Spring Boot 4.1.1），不涉及本仓代码
> Conclusion: 共享三方库高度对齐（37 组样本中 15 组完全同版本、19 组仅差小版本），无系统性滞后；但 Hibernate ORM（8.0.0.Beta3 vs 7.4.5.Final）、Liquibase（4.33 vs 5.0.3）、MySQL Connector/J 版本线（26.7 vs 9.7）三个关键件未对齐，且 Quarkus 4 尚为 Beta，GA 前结论有时效性。

## Context

- 回答问题：Quarkus 4 目前依赖的三方库是否已与最新 Spring Boot 对齐（2026-10-09 提问，源自 quarkusio/quarkus releases 页面）。
- 数据基准：Quarkus `4.0.0.Beta1`（2026-10-01 发布，首个 4.x 预发布）vs Spring Boot `4.1.1`（2026-08-20 最新稳定版）。
- 约束：两者是各自独立维护的 BOM，**无任何对齐承诺**；"对齐"只在双方 BOM 都管理的共享三方库层面有意义；核心栈（HTTP、DI、安全）不可比。

## 调研目标

- 量化共享三方库的版本重合度（同版本 / 小版本差异 / 大版本差异）。
- 找出未对齐的关键件，并判断是"一方激进"还是"一方滞后"。
- 确认 Quarkus 4 相对 Quarkus 3.x 在依赖上的实质跳变（哪些是 4.x 的 breaking changes）。

## 调研方法与数据来源

- Quarkus 侧：raw 抓取 tag `4.0.0.Beta1` 的 `bom/application/pom.xml`（`<properties>` 段）+ 根 `pom.xml`（hibernate-orm / bytebuddy / grpc / kubernetes-client 等"敏感版本"按注释约定集中在根 pom）。
- Spring Boot 侧：`docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html`（4.1.1）。
- 只记录**两侧都能查到明确版本号**的坐标；单侧独有的不计入样本。
- 交叉验证：MySQL Connector/J 26.7.0 经 Maven Central 目录与 MySQL 官方文档核实（"26.7 supersedes the 9.7 series"，2026-07-24 发布）。
- 抓取与核对日期：2026-10-09。

## 调研结果

### 1. 版本基线现状

- **Quarkus**：main 分支已是 4.x；`4.0.0.Beta1` 于 2026-10-01 发布（pre-release，breaking changes feature freeze）；当前稳定线是 `3.40 LTS`（2026-09-30）。最低 Java 21（4.x 起，PR #55147）。
- **Spring Boot**：`4.1.1` stable（2026-08-20），`4.0.8` 维护线，`4.2.0-M1/M2` preview。需 Java 17+（兼容到 Java 26），Spring Framework 7.0.9+。
- 两边节奏：Quarkus 每 4-6 周一个 minor、每 6 个月一个 LTS；Spring Boot 每 6 个月一个 minor。都持续滚动升级三方库。

### 2. 对齐度总览（37 组共享库样本）

| 分组 | 组数 | 占比 |
|------|------|------|
| 严格同版本 | 15 | 41% |
| 小版本/patch 差异（双向） | 19 | 51% |
| 大版本/跨版本线差异 | 3 | 8% |

小版本差异的更新方向：**Quarkus 更新 11 组，Spring Boot 更新 8 组**——不存在单向滞后，双方都紧跟上游，只是发布窗口错位。

#### 表 1：完全同版本（15 组）

| 库 | 版本（两侧相同） |
|---|---|
| Micrometer | 1.17.1 |
| Hibernate Validator | 9.1.3.Final |
| Testcontainers | 2.0.5 |
| Caffeine | 3.2.4 |
| Quartz | 2.5.2 |
| H2 | 2.4.240 |
| PostgreSQL JDBC | 42.7.13 |
| MariaDB JDBC | 3.5.10 |
| Kafka clients | 4.2.1 |
| Reactor Core | 3.8.7 |
| commons-lang3 | 3.20.0 |
| Byte Buddy | 1.18.11 |
| Awaitility | 4.3.0 |
| Oracle JDBC | 23.26.3.0.0 |
| DB2 JCC | 12.1.5.0 |

#### 表 2：小版本差异（19 组，标注更新方向）

| 库 | Quarkus 4 Beta1 | Spring Boot 4.1.1 | 更近一方 |
|---|---|---|---|
| Jackson 3 (`tools.jackson`) | 3.1.4 | 3.1.5 | SB |
| Jackson 2 | 2.22.2 | 2.21.5 | Q |
| Netty | 4.2.18.Final | 4.2.17.Final | Q |
| Protobuf | 4.35.0 | 4.35.1 | SB |
| classmate | 1.7.1 | 1.7.3 | SB |
| Elasticsearch 客户端 | 9.4.7 | 9.4.5 | Q |
| MSSQL JDBC | 13.6.0.jre11 | 13.4.0.jre11 | Q |
| log4j2 | 2.26.1（api） | 2.25.5 | Q |
| Infinispan | 16.0.15 | 16.1.4 | SB |
| JUnit Jupiter | 6.1.3 | 6.0.3 | Q |
| Mockito | 5.21.0 | 5.23.0 | SB |
| Flyway | 12.0.0 | 12.4.0 | SB |
| commons-codec | 1.22.1 | 1.21.0 | Q |
| Gson | 2.14.0 | 2.13.2 | Q |
| SnakeYAML | 2.7 | 2.6 | Q |
| GraphQL Java | 26.0 | 25.0 | Q |
| gRPC Java | 1.81.0 | 1.83.1 | SB |
| MongoDB driver | 5.6.5 | 5.8.1 | SB |
| Kotlin | 2.4.10 | 2.3.21 | Q |

#### 表 3：关键差异（3 组）

| 库 | Quarkus 4 Beta1 | Spring Boot 4.1.1 | 解读 |
|---|---|---|---|
| **Hibernate ORM** | **8.0.0.Beta3**（配套 Jakarta Persistence 4.0.0-M7、hibernate-reactive 5.0.0.Beta1、hibernate-search 9.0.0.Beta1） | **7.4.5.Final**（Persistence 3.2） | 最大未对齐点。Quarkus 4 直接集成 Hibernate 8（PR #55765）且整条 Hibernate 家族一起进 Beta；Spring Boot 4.1 留在 7.4 稳定线 |
| **Liquibase** | 4.33.0 | 5.0.3 | Spring Boot 已跟大版本，Quarkus 未跟 |
| **MySQL Connector/J** | 26.7.0 | 9.7.0 | 同一 GAV 的不同版本线。MySQL 官方声明 26.7 取代 9.7 系列；Quarkus 跟新线，Spring Boot 留在 9.x |

### 3. 4.x 的依赖面实质跳变（相对 Quarkus 3.x）

来自 `4.0.0.Beta1` release notes 的 major changes 中与依赖直接相关的项：

- Jackson 升到 3（`tools.jackson`，PR #55297），同时 BOM 继续强制管理 Jackson 2（`2.22.2`，注释明确"部分组件尚未迁移到 3.x"）——**与 Spring Boot 4 的双轨策略一致**。
- Hibernate 8 集成（PR #55765）、CDI 5.0（ArC，PR #55926）、Jakarta REST 4（PR #55515/#55768）。
- Vert.x 5.1.8 / Netty 4.2（3.x 线是 Vert.x 4.5.x / Netty 4.1.x）。
- Kubernetes Client 8.0.0、Dekorate 5.0.0、SmallRye Config 4.0.0、prometheus client v1、Micrometer Observation API、OpenTelemetry 默认开启。
- 最低 Java 21。

### 4. 结构性差异（不可比项，"对齐"不适用）

| 维度 | Quarkus 4 | Spring Boot 4.1.1 |
|---|---|---|
| HTTP/并发栈 | Vert.x 5.1.8 + Mutiny 3.3.0 + RESTEasy 7.0.5（Quarkus REST） | Reactor 3.8.7 + reactor-netty / Tomcat 11（Servlet 6.1）/ Jetty 12.1 |
| DI/配置 | ArC（CDI 5.0）+ SmallRye Config 4.0.0（MicroProfile Config 3.1 API） | Spring IoC + Spring Context |
| 安全 | SmallRye JWT 4.6.4 / OIDC + WildFly Elytron 2.9.2 | Spring Security 7 |
| 日志 | JBoss LogManager 3.2.2 + log4j2-api 2.26.1 | Logback 1.5.38（+ log4j 2.25.5 被管理） |
| 连接池 | Agroal 3.2.1（另管理 Hikari） | HikariCP 7.0.2 |
| 事务 | Narayana 7.3.4.Final | Spring Transaction + JTA |
| Spring 兼容层 | `quarkus-spring-boot-api 4.1.SP1` / `quarkus-spring-api 7.0.SP1` / `quarkus-spring-security-api 7.1.SP1` | — |

- Quarkus 的 Spring 兼容层版本号本身就表明：**API 兼容面按 Spring Boot 4.1 / Spring Framework 7 对齐**（这是 API 层对齐，不是依赖对齐）。

## Conclusion

- **大体已对齐**：37 组共享库样本中 34 组（92%）处于同版本或同 minor 线内，且更新方向双向均衡（Q 11 组更近 / SB 8 组更近）——说明两边都在积极跟进上游，"Quarkus 落后 Spring Boot"或反向的系统性偏差都不存在。
- **三个关键件未对齐**，且都是单侧选择差异而非信息滞后：
  - Hibernate ORM：Quarkus 4 用 8.0.0.Beta3（激进，接受 Beta 进主线），Spring Boot 4.1 用 7.4.5.Final（保守）。
  - Liquibase：Spring Boot 已上 5.0.3，Quarkus 停在 4.33.0。
  - MySQL Connector/J：Quarkus 跟 26.x 新线，Spring Boot 留 9.x。
- **"对齐"仅限共享三方库**：HTTP 栈、DI、安全、日志等核心是两套独立体系，本来就不可比。
- **时效性**：Quarkus 4 还在 Beta（Beta1 → RC → GA），Spring Boot 4.2 在 preview，本报告是 2026-10-09 快照。
- 否决的解读：否决"两边依赖已完全对齐"的表述——Hibernate ORM 大版本差异会影响 JPA 生态兼容性判断，不能被小版本统计平均掉。

## 与当前项目的关系

- **可借鉴（BOM 治理手法）**：
  - Quarkus 把"牵一发动全身"的版本（hibernate-orm、bytebuddy、grpc、jakarta.persistence）集中放根 pom，并逐条注明 update 影响面与联动检查方式；BOM 内用 maven-enforcer 规则强制家族版本联动。
  - 大量 `keep in sync with ...` 行内注释（micrometer↔hdrhistogram、smallrye-config↔microprofile-config-api、testcontainers↔docker-java）——版本不是孤立数字而是约束图。
  - BOM import 顺序有明确契约（Jackson BOM 必须在 RESTEasy BOM 之前），避免传递依赖覆盖。
  - Jackson 2/3 双轨过渡：先双 BOM 并存 + 注释声明迁移状态，再切默认——对 Nop 未来做同类大迁移有参考价值。
- **参照价值**：Nop 升级 micrometer / netty / jackson / testcontainers 等主流库时，可把"Quarkus + Spring Boot 两边同版本"当作上游已稳定的信号；两边出现分歧时（如 MySQL 版本线）需自行查上游支持策略。
- **不可借鉴**：Hibernate 8 Beta 进主线（Nop 稳定优先）；Vert.x / 原生镜像 / build-time 栈与 Nop 运行模型不同。

## Open Questions

- [ ] Quarkus 4.0 GA 后复核：Hibernate 8 是否转正（非 Beta）、表 3 三项差异是否收敛。
- [ ] Spring Boot 4.2 GA 后复核：是否跟进 Liquibase 5 以外的大版本（Hibernate 8 / MySQL 26.x）。
- [ ] Spring Boot 4.1.1 停在 `mysql-connector-j` 9.7.0 是有意保守（9.x 维护线）还是尚未跟进——影响"Nop 该跟哪条线"的判断。

## References

- Quarkus BOM（4.0.0.Beta1）：https://raw.githubusercontent.com/quarkusio/quarkus/4.0.0.Beta1/bom/application/pom.xml
- Quarkus 根 pom（敏感版本集中处）：https://raw.githubusercontent.com/quarkusio/quarkus/4.0.0.Beta1/pom.xml
- Quarkus releases：https://github.com/quarkusio/quarkus/releases （4.0.0.Beta1 major changes 列表）
- Quarkus 4 Progress Report - July 2026：https://github.com/quarkusio/quarkus/discussions/55320
- Spring Boot 4.1.1 依赖清单：https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html
- MySQL Connector/J 26.7 文档（"supersedes the 9.7 series"）：https://dev.mysql.com/doc/connector-j/en
- Maven Central `com.mysql:mysql-connector-j` 版本目录：https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/
