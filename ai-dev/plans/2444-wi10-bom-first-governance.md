# 2444 WI10 BOM-first version governance + divergence policy

> Plan Status: completed
> Last Reviewed: 2026-10-12
> Source: ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md WI10；ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md（风险旗 G1–G5 见该档 §4 风险旗区）
> Related: 2437（WI6 SB4 bump，testcontainers-bom why-note 先例）、2431（WI33 pulsar 族钉裁定）、2410（WI1 avro 单点钉）

## Purpose

让 `nop-kernel/nop-dependencies` 达成 roadmap WI10 的目标状态：**本地只钉两个基础 BOM 都不管理的 artifact**；两个 BOM 对同一 coordinate 的分歧由 import 顺序显式裁决并文档化（quarkus-bom 先 import 者胜）；每条保留的钉都带 "why not BOM-managed" 说明；README 版本表刷新为 live 事实。

## Current Baseline

（以下均为 2026-10-12 live repo / 本地 BOM pom 实测，脚本化比对见日志）

- `nop-kernel/nop-dependencies/pom.xml` import 顺序：quarkus-bom 4.0.0.Beta1 → spring-ai-bom 2.0.1 → spring-cloud-dependencies 2025.1.3 → spring-boot-dependencies 4.1.1 → testcontainers-bom 1.21.4（WI6 why-note）。BOM import 先声明者优先，本地 depMgmt 条目又优先于一切 import。
- 脚本比对两 BOM（直接管理条目 + 属性解析、剔除 BOM-import 条目，与分析档同口径）：共同管理 ~90 条，其中 **31 条版本分歧**（与 roadmap "~31" 及分析档实测一致；代表例：jakarta.persistence-api、hamcrest 2.2 vs 3.0、slf4j-api 2.0.19 vs 2.0.18、snakeyaml 2.7 vs 2.6、hibernate-core 8.0.0.Beta3 vs 7.4.5.Final、mysql-connector-j 26.7.0 vs 9.7.0）。分歧一律由 quarkus-bom 先 import 胜出，现状如此但从未写成显式政策。
- **BOM 已一致管理、本地钉属冗余（drop 即随 BOM）**：
  - `janino` 3.1.12 = SB4 3.1.12（纯 no-op drop）
  - `json-path` 钉 2.9.0 < SB4 2.10.0（drop 即 bump）；`json-smart` 钉 2.5.2 < SB4 2.6.0
  - `antlr4` / `antlr4-runtime` 钉 4.13.0 < Q4 4.13.2；`antlr4-maven-plugin` 的 depMgmt 条目为死条目（Maven 插件版本走 pluginManagement/本地声明，不走 depMgmt；全仓无依赖式声明）
  - `avro` 钉 1.12.1 < Q4 1.12.2；`javaparser-core` 钉 3.26.3 < Q4 3.28.2；`geolatte-geom` 钉 1.9.1 < Q4 1.12
  - `com.oracle.ojdbc:ojdbc8` 钉 19.3.0.0：错误 groupId（`com.oracle.ojdbc` 为废弃命名空间）+ 2019 年老驱动；`com.oracle.database.jdbc:ojdbc11` 由两 BOM 一致管理 @23.26.3.0.0（Q4 字面量 = SB4 ${oracle-database.version} 解析值）
- **分歧胜者有害、必须保留显式钉（roadmap 分歧条款 "keep an explicit pin only where the winner is harmful"）**：
  - `kafka-clients` 钉 4.3.1：两 BOM 一致 4.2.1（roadmap "(both agree)" 属实），但 nop-message-debezium 3.7.0.Final 的构建基线 debezium-build-parent `version.kafka=4.3.1`（本地 repo pom 实测），客户端 < 引擎构建版本的降级有 BrokerSecurityConfigs.NoSuchFieldError 前科（pom 注释在案）。drop 即回 4.2.1 → 有害。
  - pulsar 族钉 4.0.14：Q4 仅管理 pulsar-client-original @4.2.1；SB4 经其 import 的 `org.apache.pulsar:pulsar-bom` 4.2.4 管理全族 96 个 org.apache.pulsar 条目（spring-pulsar-bom 只管 spring-pulsar 自身模块，勿混淆）。丢族钉的实际后果：client-original=4.2.1（Q4 先 import 胜）+ 其余 artifact=4.2.4（SB4 pulsar-bom）→ 仍是混栈，recreate WI33 NoSuchMethodError 形态。
  - `javaparser-symbol-solver-core`：两 BOM 均不管（实测无条目）；其与 javaparser-core 必须同版（symbol-solver 传递依赖同版 core）。core 丢钉后由 Q4 管 3.28.2，故 symbol-solver 钉须同步 3.26.3 → 3.28.2 对齐。
- **testcontainers 混栈实况（本计划新发现， WI6 why-note 事实前提有误）**：SB4 4.1.1 **确实 import 了** testcontainers-bom 2.0.5（WI6 注释 "不再 import" 与实测不符）；该传递 import 位于本 pom import 序第 4 位，先于本地第 5 位 testcontainers-bom 1.21.4 生效 → dependency:tree 实证 live 栈为 core（唯一双 BOM 共管 legacy 名件）@2.0.5 + 2.x 已移除/改名模块（jdbc 等）@1.21.4 的**混栈**（nop-orm 树实测）。另：5 个模块（nop-message-kafka/pulsar、nop-stream-runtime、nop-stream-connector-kafka/debezium）经 WI33 已在各自 pom 显式 import testcontainers-bom 2.0.5 并使用 2.x 改名 artifact（testcontainers-kafka 等，1.21.4 BOM 零管理）——子 pom 自带 import 优先于父链，不受本计划影响，自成 2.0.5 一致线。预期治理：testcontainers-bom import 保留（供 2.x BOM 不管理的 legacy 名模块）+ **core 显式本地钉 1.21.4**（执行期实测 Maven 4 rc 嵌套 import 压过顶层 import、提位单独无效，详见 Ruling 5），令 **nop-dependencies 管辖的 legacy 名消费线**（nop-dao/orm/orm-geo/nosql 等）恢复 1.21.4 一致（恢复 WI6 前的既定钉线，全平台 2.x 迁移另行裁定）。
- **死属性（nop-dependencies pom 内零引用/零使用）**：`logback.version`、`slf4j-api.version`、`commons-lang3.version`、`commons-collections4.version`、`opencsv.version`、`compiler-plugin.version`、`surefire-plugin.version`，以及 pom 内配套的注释死块（logback classic/core、slf4j、commons-lang3、collections4、opencsv、vertx/netty/tcnative/jackson2/junit/lettuce/h2/旧 testcontainers 等史前注释属性）。注意 `${surefire-plugin.version}` 在 root pom 与 23 个 app pom 有**各自的合法定义与使用**（root 3.0.0-M5），与本独立 BOM pom 无关，不在清理面内。commons-lang3 另有 Q4 3.20.0 / SB4 管理在案，无本地钉。
- antlr 族的实际版本源有两处：nop-dependencies（tool+runtime 钉）与 `nop-kernel/nop-antlr4/pom.xml` 本地属性 `antlr.plugin.version=4.13.0`（nop-antlr4 聚合 pom 的活动 antlr4-maven-plugin 用它）。family 对齐要求两处同 commit 内一起动。
- 消费方（全部 version-less 声明，依赖 depMgmt，无属性引用）：ojdbc → nop-cli-core（compile）、nop-dao（test）、nop-orm-geo（test）；javaparser core+symbol-solver → nop-utils/nop-java-parser、nop-code/nop-code-lang-java；json-path → nop-jpath、nop-kernel-cli、nop-report-for-spring；janino → nop-javac、nop-xlang；antlr runtime → 直接声明仅 nop-dependencies 与 nop-antlr4 聚合 pom，nop-xlang/nop-orm-eql/nop-codegen/nop-mermaid 经 nop-antlr4-common/tool 传递消费；avro → nop-stream-format-avro；geolatte → nop-orm-geo；kafka → nop-stream-connector-kafka、nop-message-kafka、nop-message-debezium。全仓 `oracle.jdbc` 字符串引用仅 4 处（dialect xml driverClassName、systemconfig、两处测试），均为驱动类名/URL，ojdbc11 类名不变，无需代码改动。
- `nop-bom`（reactor 内）只聚合 nop 自身模块 artifact，无第三方钉，不受影响。`tests/pom.xml` 与 nop-spring-demo2 一样已注释出 reactor。
- README 现状失真：引用已删除的 `${graalvm.version}`（WI8 删除）、示例仍用 starter-web（SB4 已 deprecated）、无治理政策章节。
- 保留钉中缺 why-note 的：nacos、sentinel、aws-sdk-s3、jsch、openpdf、semver4j、SparseBitSet、jjwt、protostuff、commons-csv、keycloak-common、lucene、pdfbox、tablesaw 族、duckdb、h2gis、jts-core、zxing javase、commonmark 族、jfreechart、java-dataloader（rocksdb/testcontainers/pulsar/kafka 已有）。

## Goals

- nop-dependencies depMgmt 中不再存在"两 BOM 已一致管理"的冗余钉；drop 名单：janino、json-path、json-smart、antlr4、antlr4-runtime、antlr4-maven-plugin（死条目）、avro、javaparser-core、geolatte-geom。
- ojdbc 坐标迁移到正确 groupId：`com.oracle.ojdbc:ojdbc8` → `com.oracle.database.jdbc:ojdbc11`（无钉，两 BOM 一致管理 23.26.3.0.0），3 个消费方 pom 同步。
- 分歧裁决显式化：BOM import 区政策注释 + README 政策章节，写明 quarkus-bom 先 import 者胜 31 条实测分歧；有害胜者的例外钉（kafka-clients、pulsar 族、javaparser-symbol-solver-core、testcontainers）各带 why-note。
- testcontainers 混栈收口：**nop-dependencies 管辖的 legacy 名消费线**恢复 1.21.4 一致（core 2.0.5 → 1.21.4；WI33 已立的 5 个模块级 2.0.5 单点不在管辖面且不受影响），why-note 改写为实测机制（SB4 4.1.1 import testcontainers-bom 2.0.5、嵌套 import 压过顶层 import 故 core 需显式钉）。
- 全部保留钉带 "why not BOM-managed" 说明（族级注释可覆盖同族多条）。
- 死属性及配套注释死块删除（7 个属性 + 史前注释块）。
- antlr 族版本一致性保持：nop-antlr4 `antlr.plugin.version` 4.13.0 → 4.13.2，与 Q4 管理的 tool/runtime 同步。
- README 版本表刷新为 live 事实并移除僵尸引用。

## Non-Goals

- 不升级任何无 BOM 管理的保留库的版本（nacos/sentinel/tablesaw/… 原样保留，只补注释）。
- 不动 jakarta.mail / com.sun.mail（WI11 angus-mail 迁移）。
- 不升级 testcontainers 1.x → 2.x 大版本（迁移另行裁定；本计划只恢复 1.21.4 线一致性，不动 API 面）。
- 不引入新的版本钉；除 Ruling 5 的 testcontainers-bom 提位外，不改 BOM import 顺序。
- 不处理 WI13（benchmark-orm 的 hibernate 栈）与 WI15（运行时默认值）。

## Scope

### In Scope

- `nop-kernel/nop-dependencies/pom.xml`：drop 9 条钉/死条目、删 7 个死属性 + 注释死块、kafka/pulsar/symbol-solver why-note 重写、BOM import 区政策注释、保留钉补 why-note。
- `nop-runner/nop-cli-core/pom.xml`、`nop-persistence/nop-dao/pom.xml`、`nop-persistence/nop-orm-geo/pom.xml`：ojdbc8 → ojdbc11 坐标迁移。
- `nop-kernel/nop-antlr4/pom.xml`：`antlr.plugin.version` 4.13.0 → 4.13.2。
- `nop-kernel/nop-dependencies/README.md`：版本表 + 治理政策重写。
- `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md`：WI10 状态翻转。
- `ai-dev/logs/2026/10-12.md`：执行日志。

### Out Of Scope

- 任何 Java 源码变更（ojdbc8→ojdbc11 驱动类名不变 `oracle.jdbc.OracleDriver`，无需代码改动；全仓 `oracle.jdbc` 字符串引用仅 4 处类名/URL，均不受坐标迁移影响，已验证）。
- docs-for-ai/ 其他文档（模块文档不涉及依赖治理政策；nop-dependencies 的 owner doc 就是其 README）。

## Rulings（roadmap 文本 vs 实测的偏差裁定）

1. **kafka-clients 保留钉**：roadmap 括注 "(both agree)" 指两 BOM 一致 4.2.1——属实，但 drop 有害（Debezium 3.7.0.Final 构建基线 4.3.1 实测在案 + NoSuchFieldError 前科）。按 roadmap 自身分歧条款（"keep an explicit pin only where the winner is harmful"）保留 4.3.1 并重写 why-note 为分歧裁决口径。G1 收口。
2. **pulsar 族保留钉**：roadmap 列 "Spring-side pulsar-*（Spring-managed）" 与实测机制不符——SB4 经其 import 的 pulsar-bom 4.2.4 管理全族（96 条），Q4 仅管 client-original 4.2.1；drop 的真实形态是 4.2.1/4.2.4 混栈而非失管。WI33 族一致性裁定优先，保留 4.0.14 族钉，why-note 按此机制改写。G2 收口。
3. **antlr 族 drop（G3 收口）**：三条 depMgmt 条目全删；tool/runtime 随 Q4 → 4.13.2；nop-antlr4 本地 `antlr.plugin.version` 同 commit bump 4.13.2 保持族内一致；antlr4-maven-plugin depMgmt 条目为死条目直接删。4.13.0→4.13.2 为同 minor 补丁位 bump，antlr runtime 对 4.13.x 生成代码向后兼容。
4. **ojdbc（G4 收口）**：迁移坐标 + **不设任何条目**（两 BOM 一致管理 23.26.3.0.0，经 import 链直接生效）。ojdbc11 = JDK11+ 驱动，JDK25 平台正确选择；19.3.0.0 ojdbc8 为 2019 年 Java 8 驱动。**执行期实测修正**：原拟放"无版本占位条目"，实测 Maven 4 rc（wrapper 4.0.0-rc-5）下本地 depMgmt 条目按条目级优先压制 imported BOM 版本——占位条目反而致消费方 "version is missing"，故条目整体不设，教训写入 pom 注释。
5. **testcontainers 混栈收口（本计划新裁定，含执行期实测修正）**：原拟仅靠 import 提位（testcontainers-bom 1.21.4 移到 spring-boot-dependencies 之前）；实测 Maven 4 rc 的跨 import 胜负规则为**嵌套 import（深度 2，SB4 内部的 tc-bom 2.0.5）压过顶层直接 import（深度 1）且与位次无关**，提位无效。修正方案：保留 testcontainers-bom import（供 2.x BOM 已不管理的 legacy 名模块 jdbc/mysql 等）+ **对 core（唯一双线共管 legacy 名 artifact）显式本地钉 1.21.4**（本地直接条目实测压过一切 import，ojdbc11 占位条目事件同源佐证）。实测后 nop-orm 树 core/jdbc/mysql 全部 1.21.4，混栈消除。管辖面限定：5 个模块（nop-message-kafka/pulsar、nop-stream-runtime、nop-stream-connector-kafka/debezium）经 WI33 已在各自 pom 显式 import 2.0.5 并用 2.x 改名 artifact——子 pom import 优先于父链，不受影响，自成 2.0.5 一致线。WI6 why-note 的 "SB4 不再 import" 事实错误一并改正。不选择顺从 SB4 的 2.0.5（全平台 2.x 迁移另裁，非 WI10 目标）。

## Execution Plan

### Phase 1 - nop-dependencies pom 治理编辑

Status: completed
Targets: `nop-kernel/nop-dependencies/pom.xml`

- Item Types: `Fix`（死条目/死属性清理）、`Decision`（分歧政策成文）、`Feature`（无——本 Phase 不新增能力）

- [x] drop 钉/死条目 9 条：janino、json-path、json-smart、antlr4、antlr4-runtime、antlr4-maven-plugin、avro、javaparser-core、geolatte-geom（连同各自属性：antlr.version、avro.version、geolatte-geom.version、janino.version、json-path.version、json-smart.version；javaparser.version 属性保留给 symbol-solver）
- [x] testcontainers 混栈收口：testcontainers-bom import 移位（紧随 quarkus-bom）+ **core 显式本地钉 1.21.4**（执行期实测：嵌套 import 深度压过顶层 import 且与位次无关，提位单独无效；本地直接条目压过一切 import），why-note 整体改写（SB4 4.1.1 import tc-bom 2.0.5 实测、混栈 core 2.0.5 + 模块 1.21.4 记录、WI33 的 5 个模块级 2.0.5 单点不受影响照旧）
- [x] ojdbc 处置：`com.oracle.ojdbc:ojdbc8` depMgmt 条目 + `ojdbc8.version` 属性删除；**不新增任何 ojdbc 条目**（执行期实测：Maven 4 rc 下无版本占位条目会压制 imported BOM 版本致解析失败，教训入 pom 注释）
- [x] javaparser.symbol-solver 保留：`javaparser.version` 3.26.3 → 3.28.2，条目 why-note（unmanaged + 与 Q4 管理的 core 同版对齐）
- [x] kafka-clients why-note 重写为分歧裁决口径（BOM 4.2.1 vs Debezium 3.7 floor 4.3.1，保留钉）；pulsar 族 why-note 按 Ruling 2 机制改写（SB4 pulsar-bom 4.2.4 全族管理）
- [x] 删死属性 7 个（logback/slf4j-api/commons-lang3/commons-collections4/opencsv/compiler-plugin/surefire-plugin）及配套注释死块（含 vertx/netty/tcnative/jackson2/junit/lettuce/h2/旧 testcontainers 史前注释属性、logback/slf4j/commons/opencsv 注释依赖块）；**不触碰** root/app pom 中合法的 `${surefire-plugin.version}` 定义
- [x] BOM import 区新增政策注释：import 顺序即分歧裁决（终态顺序：quarkus-bom → testcontainers-bom → spring-ai → spring-cloud → spring-boot-dependencies），31 条实测分歧由先 import 者胜，drop 钉前必须核对两 BOM 是否真的一致管理
- [x] 保留钉逐条/逐族补 "why not BOM-managed" 注释：nacos、sentinel、aws-sdk-s3、jsch、openpdf、semver4j、SparseBitSet、jjwt、protostuff、commons-csv、keycloak-common、lucene、pdfbox、tablesaw 族、duckdb、h2gis、jts-core、zxing javase、commonmark 族、jfreechart、java-dataloader

Exit Criteria:

- [x] `nop-dependencies/pom.xml` 中不存在 janino/json-path/json-smart/antlr4*/avro/javaparser-core/geolatte-geom 的 depMgmt 条目与对应属性定义（对该 pom grep 验证）
- [x] `nop-dependencies/pom.xml` 内不存在死属性**定义**（grep `<logback.version>|<slf4j-api.version>|<commons-lang3.version>|<commons-collections4.version>|<opencsv.version>|<compiler-plugin.version>|<surefire-plugin.version>` 限定该文件零命中）及配套注释死块；root/app pom 的合法 `${surefire-plugin.version}` 定义未被触碰
- [x] testcontainers-bom import 位于 spring-boot-dependencies 之前 + `org.testcontainers:testcontainers` 显式钉 1.21.4 存在；why-note 与 Ruling 5（含执行期实测修正）一致
- [x] BOM import 政策注释存在且与实际 import 顺序一致；kafka/pulsar/symbol-solver/ojdbc11 why-note 与 Rulings 一致
- [x] 全仓 `${antlr.version}|${avro.version}|${geolatte-geom.version}|${janino.version}|${json-path.version}|${json-smart.version}|${ojdbc8.version}` 引用为零（消费方全部 version-less 已验证，此条为回归确认）
- [x] No owner-doc update required（本 Phase 纯 pom 内部；README 属 Phase 3 交付）
- [x] No new test required: 纯依赖治理编辑，行为验证由 Phase 4 构建门承担
- [x] `ai-dev/logs/2026/10-12.md` WI10 条目（可与 Phase 4 合并写入）

### Phase 2 - 消费方迁移

Status: completed
Targets: `nop-runner/nop-cli-core/pom.xml`、`nop-persistence/nop-dao/pom.xml`、`nop-persistence/nop-orm-geo/pom.xml`、`nop-kernel/nop-antlr4/pom.xml`

- Item Types: `Fix`（坐标迁移 + 族版本对齐）

- [x] 3 个 pom：`com.oracle.ojdbc:ojdbc8` → `com.oracle.database.jdbc:ojdbc11`（scope 保持：cli-core compile、dao test、orm-geo test）
- [x] `nop-kernel/nop-antlr4/pom.xml`：`antlr.plugin.version` 4.13.0 → 4.13.2，附一行 why（与 quarkus-bom 管理的 tool/runtime 对齐，WI10 plan 2444）

Exit Criteria:

- [x] 全仓 `com.oracle.ojdbc` 引用为零；`com.oracle.database.jdbc:ojdbc11` 在 3 个消费方 + nop-dependencies 各出现一次
- [x] antlr 族单一版本源核对：nop-antlr4 antlr.plugin.version=4.13.2，depMgmt 无 antlr 条目（Q4 4.13.2），族内一致
- [x] No owner-doc update required（行为面由 Phase 4 构建门验证；README 属 Phase 3）
- [x] No new test required: 坐标迁移无新功能面，验证由 Phase 4 构建门承担
- [x] `ai-dev/logs/2026/10-12.md` WI10 条目（可与 Phase 4 合并写入）

### Phase 3 - README 治理重写

Status: completed
Targets: `nop-kernel/nop-dependencies/README.md`

- Item Types: `Fix`（失真文档修复）

- [x] 移除僵尸引用（`${graalvm.version}` 已于 WI8 删除、starter-web 示例）、版本表刷新为 live 值
- [x] 新增 "Version Governance Policy" 章节：BOM import 顺序即裁决顺序、本地钉仅限（a）两 BOM 均不管理（b）分歧胜者有害（kafka-clients/pulsar 族/symbol-solver/testcontainers 现例）、why-not-BOM-managed 注释义务、drop 钉前的核对清单

Exit Criteria:

- [x] README 无 `${graalvm.version}`/`starter-web` 残留；版本表数值与 pom live 值一致（抽查 quarkus/spring-boot/antlr 不再列出、pulsar/kafka/tablesaw/testcontainers 在列）
- [x] 政策章节存在且与 pom 注释口径一致
- [x] No owner-doc update required 之外的其他文档裁定：docs-for-ai 无依赖治理 owner doc（grep 实证），nop-dependencies README 即 owner doc——本 Phase 即文档收口
- [x] No new test required: 纯文档编辑
- [x] `ai-dev/logs/2026/10-12.md` WI10 条目（可与 Phase 4 合并写入）

### Phase 4 - 验证门

Status: completed
Targets: 全仓构建 + 受影响模块 focused tests

- Item Types: `Proof`

- [x] Gate A：`./mvnw clean install -DskipTests -T 1C -fae` 全绿（容忍面仅 chronic gen-aop 家族 {job-dao, tcc-dao, sys-dao, retry-engine} 且签名归因）。Gate B 依赖本地 repo 已装 2.0.0-SNAPSHOT，故 Gate A 先行；新增解析版本（ojdbc11 23.26.3.0.0、geolatte 1.12、avro 1.12.2、javaparser 族 3.28.2）首次解析需网络
- [x] Gate B focused tests（执行记录：`_tmp/wi10-gateb2.log`/`_tmp/wi10-gateb3-codelangjava.log`；两处清单外修正——① nop-spring/nop-report-for-spring 不在 nop-spring 聚合 pom module 列表（out-of-reactor，无法 -pl 构建），json-path 解析已由 BOM 级 probe 证据覆盖，从 Gate B 移除；② nop-runner/nop-cli-core 全部 10 测试 Errors=1/类，错误码统一 nop.err.ioc.multiple-bean-with-type-when-for-prop（IChatService 双候选：nop-ai-core ChatServiceImpl vs nop-ai-gateway ChatServiceFailoverAdapter）——按本 Phase 归因规则在 HEAD worktree 跑 pre-WI10 基线复现同签名（`_tmp/wi10-baseline-clicore-test.log`，30 处同错误码），裁定为 pre-existing red、与 WI10 依赖变更无关（WI10 零 beans.xml/零 nop-ai 改动），登记 Non-Blocking Follow-ups 不做超范围修复）。原定消费面（drop/bump/迁移命中）：`./mvnw test -pl nop-kernel/nop-antlr4/nop-antlr4-common,nop-kernel/nop-antlr4/nop-antlr4-tool,nop-kernel/nop-jpath,nop-kernel/nop-kernel-cli,nop-spring/nop-report-for-spring,nop-utils/nop-java-parser,nop-code/nop-code-lang-java,nop-stream/nop-stream-format-avro,nop-persistence/nop-orm-geo,nop-persistence/nop-dao,nop-runner/nop-cli-core,nop-kernel/nop-xlang`（antlr plugin 生成路径、json-path 2.10 全部三消费方、javaparser 3.28.2 双消费方、avro 1.12.2 wire/演进、geolatte 1.12、ojdbc11 三消费方、antlr runtime 4.13.2 传递面）。第一轮已绿模块直接用；若新增四模块（nop-cli-core/nop-dao/nop-kernel-cli/nop-report-for-spring）出现与 WI10 无关的失败，先跑 pre-WI10 基线（独立 worktree 对照；本仓共享 worktree 禁用 stash）登记既有状态再归因，不做超范围修复。取舍不得项：nop-javac（janino 纯 no-op drop，nop-xlang 测试同源覆盖）、nop-orm-eql/nop-codegen/nop-mermaid（antlr 传递面，nop-antlr4+nop-xlang 已覆盖生成与运行两段）
- [x] Gate C 解析证据：对 nop-orm（testcontainers legacy 名 artifact 全部 @1.21.4 一致线复核）、nop-orm-geo（geolatte+ojdbc11）、nop-jpath（json-path/json-smart）、nop-java-parser（javaparser 族）跑 `dependency:tree` 抽查，记录新解析版本（testcontainers core/jdbc 等 legacy 名 1.21.4、ojdbc11 23.26.3.0.0、geolatte 1.12、json-path 2.10.0、json-smart 2.6.0、javaparser 3.28.2 同版双件）
- [x] `ai-dev/logs/2026/10-12.md` WI10 条目已写（含 Drop/Keep 清单与裁决；注明是 jdk25-springboot4-quarkus4-roadmap 的 WI10，与 nop-ai roadmap WI10/plan 2441 区分）

Exit Criteria:

- [x] Gate A/B/C 全部通过且证据（日志文件/输出摘要）可追溯
- [x] 失败处置规则：任何非 chronic 家族失败 → 修复后重跑；gen-aop 家族失败 → 签名归因后按 WI6 先例容忍

### Phase 5 - 收口

Status: completed
Targets: roadmap、plan、log

- [x] roadmap WI10 状态 `todo` → `done`
- [x] 白名单提交（nop-dependencies pom+README、3 个消费方 pom、nop-antlr4 pom、plan 2444、log 10-12、roadmap；**严禁**混入 plan 2380 EQL 文件集）
- [x] 独立子 agent closure audit（对照本 plan Exit Criteria + Closure Gates 逐条回看 live repo）
- [x] 三门全过：`check-plan-checklist.mjs ai-dev/plans/2444-wi10-bom-first-governance.md --strict`、`scan-hollow-implementations.mjs --module nop-dependencies --severity high`、`check-doc-links.mjs --strict` 均 exit 0
- [x] 文本一致性核对（Plan Status / Phase Status / Exit Criteria / Closure Gates / log 互证）后置 `Plan Status: completed`

Exit Criteria:

- [x] roadmap WI10 行显示 done；提交哈希记录于本 plan Closure 段
- [x] Closure Audit Evidence 写入本 plan（独立 agent 标识 + 逐条验证结果）
- [x] 三门命令退出码 0 留痕

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（死钉/死属性/错误 groupId 坐标）
- [x] 所有 in-scope confirmed contract drifts 已收敛（pom 注释口径 = README 政策 = 实际 import 顺序三者一致）
- [x] 行为/契约结果已达成：drop 钉后解析版本 = BOM 管理版本（Gate C 证据）
- [x] 必要 focused verification 已完成（Gate B 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline（README 重写即 owner-doc 收口；docs-for-ai 无需更新已裁定）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 pom 编辑真实生效（drop 条目 grep 不存在、新坐标解析成功），无注释式假清理
- [x] `./mvnw clean install -DskipTests -T 1C -fae` 全绿（Gate A）
- [x] Gate B focused tests 全绿
- [x] 代码规范：新增注释遵循仓内 why-note 惯例（中文、带 plan 引用）；无 Java 代码变更故无 checkstyle 新面

## Deferred But Adjudicated

（无——WI10 交付面内无 deferred 项）

## Non-Blocking Follow-ups

- **nop-cli-core 全量 app 容器测试 pre-existing red（Gate B 归因发现，非本计划引入）**：`nop.err.ioc.multiple-bean-with-type-when-for-prop`——NopAiRagBizModel 构造器按类型取 IChatService 时双候选（nop-ai-core `ChatServiceImpl`@ai-defaults.beans.xml + nop-ai-gateway `nopChatServiceFailoverAdapter`@ai-gateway-defaults.beans.xml）。failover bean 自 c7e604bb60（nop-ai WI5）在库，WIs 6-9 全部 `-DskipTests` 故从未暴露；HEAD 基线 worktree 复现同签名（`_tmp/wi10-baseline-clicore-test.log`）。归属 nop-ai 栈（bean 语义冲突），不在 WI10 依赖治理 scope。Classification: `out-of-scope improvement`（对 WI10 而言）。Why Not Blocking Closure: 与依赖版本面无关、基线即红、修复属 nop-ai bean 装配裁定。Successor Required: `yes`（移交 nop-ai 栈 owner 处理）。
- testcontainers 全平台 1.x → 2.x 统一迁移裁定：out-of-scope improvement。现状双线并存且各自自洽——legacy 名消费线（本计划钉 core 后归 nop-dependencies 管，1.21.4）+ WI33 已立的 5 个模块级 2.0.5 单点线（message/stream 模块，自带改名 artifact）；剩余迁移面 = 前者升 2.x 并与后者合线，涉及 legacy API 面逐文件核对，另行立项，不阻塞本计划。
- WI16 GA 后 pulsar 真实 client 构造运行时回归（WI33 遗留运行时面）：watch-only residual，归 roadmap WI16，与本计划钉位决策无关。

## Closure

Status Note: WI10 交付面全部落地并经独立 closure audit APPROVE（0 Blocker / 0 Major / 2 Minor，均为收口流程步骤本身）。drop 9 冗余钉、ojdbc11 迁移（不设条目 + Maven 4 占位条目压制教训入注释）、testcontainers 混栈收口（core 显式钉 1.21.4，审计实测 dependency:tree 全家 1.21.4 复核）、31 条分歧裁决政策 pom+README 双落、死属性 7 清理且 root pom 未误伤。Gate A 399 SUCCESS + 恰 4 chronic 家族签名归因；Gate B 11 绿 + cli-core pre-existing red 经 HEAD worktree 基线复现裁定非 WI10 引入（登记 follow-up 移交 nop-ai 栈）。执行期两项实测修正（ojdbc 占位条目压制、嵌套 import 胜顶层）已回写 Rulings 4/5 与 pom/README 口径。
Completed: 2026-10-12
Commit: e6d46a2d46

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_68317192-0ead-4c0f-a367-404fbaac9e8d（fresh session，非实现 session）
- Evidence:
  - 12 项验证全部 PASS（Drop 钉 grep 零存在 / ojdbc 三消费方坐标+零占位条目 / testcontainers core 钉+注释口径 / 死属性零命中且 root pom 不在 diff / 政策注释+README 口径一致无僵尸引用 / antlr 族三方同版 4.13.2 / dependency:tree 实测重跑 org.testcontainers 全家 1.21.4 / Gate 证据文件 4 份在账（399 SUCCESS+4 chronic、gateb2 10 模块、gateb3 38 tests 绿、baseline 30 处同错误码）/ Anti-Hollow 以 live 文件系统+实跑验证非 diff 采信 / 文本一致性 / Deferred 诚实性 / 提交白名单恰 9 文件零 2380 EQL 混入）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2444-wi10-bom-first-governance.md --strict` 退出码 0（勾选完成后复跑确认）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-dependencies --severity high` 退出码 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（No errors found）
  - Anti-Hollow：pom 编辑经审计员 live grep + dependency:tree 实跑验证真实生效，非注释式假清理
  - Deferred 项分类检查：cli-core red（out-of-scope，基线复现有据）、testcontainers 2.x 迁移（Non-Goals 开局排除）、WI16 pulsar（watch-only）——无 in-scope live defect 被降级

Follow-up:

- 见 Non-Blocking Follow-ups（cli-core IChatService 双候选 pre-existing red 移交 nop-ai 栈；testcontainers 全平台 2.x 合线迁移另行立项；WI16 pulsar 真实 client 运行时回归）
