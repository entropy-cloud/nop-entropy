# 2451 testcontainers 全平台 2.x 合线（用户指令：整体升级）

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: 用户指令"testcontainers 应该整体升级"；plan 2450 后平台具备全绿基线
> Related: 2444（WI10 混栈收口与两线并存的由来）、2436（nop-stream WI33 单点 import 的来历）

## Purpose

消除 testcontainers 双线并存（legacy 名消费线 1.21.4 + 5 个 message/stream 模块的 2.0.5 线）：全平台统一到 **2.0.5**，撤除模块级 tc-bom import，npm 式单线治理。

## Current Baseline

（2026-10-13 实测）

- 2.0.5 BOM 对现有测试面**近乎 drop-in**（jar 实测）：`org.testcontainers.junit.jupiter.*`（Testcontainers/@Container 包名不变）、`org.testcontainers.jdbc.ContainerDatabaseDriver`（jdbc:tc: URL 方案不变，mysql 2.0.5 pom compile 传递 testcontainers-jdbc）、core 的 GenericContainer/Network/DockerImageName/DockerClientFactory 包名全部不变。唯二变化：**artifact 改名**（junit-jupiter→testcontainers-junit-jupiter、mysql→testcontainers-mysql、postgresql→testcontainers-postgresql、mariadb→testcontainers-mariadb、mssqlserver→testcontainers-mssqlserver、oracle-xe→testcontainers-oracle-xe）与 MySQLContainer 类迁 `org.testcontainers.mysql` 包（平台现有测试零直接 import，不受影响——dialect 测试全部走 jdbc:tc: URL）。
- **legacy 消费方 5 模块**（仅 pom 层改动）：nop-dao / nop-orm（junit-jupiter/mysql/postgresql/oracle-xe/mariadb/mssqlserver 全集）、nop-orm-geo（5 个 DB 工件，无 org.testcontainers:junit-jupiter——用 org.junit.jupiter 的）、nop-nosql-lettuce（junit-jupiter + GenericContainer/Network）、nop-orm-tdengine（junit-jupiter + GenericContainer/DockerImageName；注意其 TestTdEnginePersist @Disabled 且零 @Test——不在验证面）。
- **已自持 2.0.5 的 5 模块**：nop-message-kafka/pulsar、nop-stream-runtime、nop-stream-connector-kafka/debezium（模块级 tc-bom 2.0.5 import + 2.x 改名 artifact）。
- nop-dependencies：testcontainers.version=1.21.4 属性 + tc-bom import（紧随 quarkus-bom）+ core 显式钉 1.21.4（plan 2450 前为压 SB4 嵌套 import 的 2.0.5；统一后两线同版，压制理由消失）。
- 改名雷区：`<artifactId>postgresql</artifactId>`/`<artifactId>junit-jupiter</artifactId>` 会撞 org.postgresql 驱动与 org.junit.jupiter 同名 artifactId——**只能在 org.testcontainers groupId 块内改名**（脚本按 dependency 块处理，禁全局 sed）。
- docker daemon v29.4.3 可用；dialect 测试开关 `nop.test.docker.enabled=true`（@EnabledIfSystemProperty 门控，6 类 nop-dao + lettuce/tdengine/orm 各面）。

## Goals

- nop-dependencies：testcontainers.version → 2.0.5；删除 core 显式钉（统一后无分歧可压，WI10 Ruling 5 的"胜者有害"前提失效）；tc-bom import 保留（位次不变，why-note 改写为合线后事实）。
- 5 个 legacy 模块 pom：org.testcontainers 块内 artifact 改名（上表全集）。
- 5 个 message/stream 模块：删除模块级 tc-bom import（平台单线后冗余）。
- 真实容器验证：nop-dao dialect 测试（docker 开关打开）在 2.0.5 上真实起容器跑通。

## Non-Goals

- 不升级到 2.0.5 之后的版本线（3.x 若存在另行裁定）；不动容器镜像版本。
- 不改 jdbc:tc: 测试写法（2.0.5 完全兼容）。

## Rulings

1. **core 显式钉删除而非改 2.0.5**：plan 2444 Ruling 5 钉 core 的唯一理由是"SB4 嵌套 import 的 2.0.5 有害"（相对 1.21.4 线）。全平台裁定升级 2.0.5 后，本地钉与 SB4 嵌套 import 版本一致，压制对象消失——按 WI10 政策"分歧消除即撤钉"。why-note 改写为合线记录。
2. **版本权威链如实记载（审查 M3 实证修正）**：平台所需全部 2.x 工件实际由 **quarkus-bom 4.0.0.Beta1 管理**（62 条 testcontainers 条目、含全部所需 11 工件 @2.0.5，且先 import 者胜），tc-bom 与 SB4 嵌套恰巧同为 2.0.5——"单线治理"当前是三源数值巧合。未来单 bump 本 BOM 的 testcontainers.version 对 quarkus 管的工件**静默不生效**，须 bump quarkus-bom 或按 README 规则 2b 加本地钉（登记 follow-up）。模块级 tc-bom import 撤除：2.x 改名 artifact 由平台供给。
2b. **tdengine 不进验证面**：其测试类 @Disabled 且零 @Test（审查 M1 实证），Gate B 采信其"通过"即假绿。
3. **改名限 org.testcontainers 块**：postgresql/junit-jupiter 与其他 groupId 同名，全局替换会误伤驱动与 JUnit 依赖（脚本逐 dependency 块处理）。
4. **Java 源码零改动**：现有 import 面（junit.jupiter 注解、GenericContainer/Network/DockerImageName）与 jdbc:tc: URL 在 2.0.5 逐 jar 实测兼容。

## Execution Plan

### Phase 1 - 合线迁移

Status: completed
Targets: nop-dependencies（pom+README）、5 legacy pom、5 message/stream pom

- Item Types: `Fix`（双线合一）

- [x] nop-dependencies：testcontainers.version → 2.0.5；core 显式钉条目删除（Ruling 1）；tc-bom import 位次不变、why-note 改写（2.0.5 合线记录 + WI10 混栈历史归档）
- [x] 5 个 legacy pom：org.testcontainers 块内 6 种 artifact 改名（脚本处理，禁全局 sed）
- [x] 5 个 message/stream pom：删除模块级 tc-bom import 块
- [x] nop-dependencies README：规则 1/2b 的 core 钉 1.21.4 叙事改写为 2.0.5 合线事实 + 版本权威链（quarkus-bom 先 import 者胜）+ 未来 bump 注意项（审查 M2）

Exit Criteria:

- [x] 全仓 `artifactId>junit-jupiter<` 等旧名在 org.testcontainers 块内零残留；模块级 tc-bom import 仅剩 nop-dependencies 一处
- [x] No owner-doc update required（测试基建内部迁移）
- [x] No new test required: 既有 docker-gated 测试即验证面（本 plan 零新增能力）
- [x] 当日 log 条目

### Phase 2 - 验证门与收口

Status: completed
Targets: 全仓构建、真实容器验证

- Item Types: `Proof`

- [x] Gate A：`./mvnw clean install -DskipTests -T 1C -fae` 全 SUCCESS（2450 后基线 459/0 保持）
- [x] Gate B（真实容器）：`./mvnw test -pl nop-persistence/nop-dao -Dnop.test.docker.enabled=true`（6 个 dialect 测试类真实起容器，跑前/跑后 `docker images` 快照留证）+ `./mvnw test -pl nop-persistence/nop-nosql/nop-nosql-lettuce -Dnop.test.docker.enabled=true`（2 类）；tdengine 不进验证面（Ruling 2b）
- [x] roadmap 无状态翻转项（testcontainers 2.x 不在 roadmap WI 清单，属 follow-up 兑现）；log + plan 记录
- [x] 独立子 agent closure audit
- [x] 三门：check-plan-checklist --strict / scan-hollow-implementations --module nop-persistence/nop-dao --severity high / check-doc-links --strict
- [x] 文本一致性核对后置 `Plan Status: completed`

Exit Criteria:

- [x] Gate A 全绿 + Gate B 真实容器通过；commit hash 记录 Closure；审计证据写入；三门留痕

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（双线并存 = 本 plan defect 面）
- [x] 所有 in-scope confirmed contract drifts 已收敛（全平台单线 2.0.5）
- [x] 行为/契约结果已达成：真实容器在 2.0.5 上起停跑通（Gate B）
- [x] 必要 focused verification 已完成（Gate B）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（No owner-doc update required 已裁定）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：docker ps 可见容器真实创建/销毁或测试断言通过（非跳过假绿）
- [x] Gate A 全 SUCCESS
- [x] Gate B 真实容器验证通过
- [x] 代码规范：不适用（零 Java 变更，pom 层迁移）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- testcontainers 未来升版路径：单 bump testcontainers.version 对 quarkus-bom 管的工件静默不生效（版本权威链见 Ruling 2），须 bump quarkus-bom（WI16 时机）或按 README 2b 加本地钉。
- **nop-dao dialect 能力矩阵 11 失败**（docker-gated 首次系统运行暴露的既有偏差：postgres:9.6.12 类型上限/VARBINARY/MONTH()、oracle-xe JSON ORA-00902、mariadb 10.3.6 NVARCHAR+st_dimension、mssql 2017 NVARCHAR 8000 上限+now()、mysql st_dimension——SQL 能力面，与 tc 版本无关）：归 nop-dao 测试维护面，随镜像/能力预期逐项对齐。

## Closure

Status Note: testcontainers 全平台合线 2.0.5 落地并经独立 closure audit APPROVE（9 项验证 7 PASS + 1 Major 证据引用错位已修正 + 2 项回填完成）：单线无残留、lettuce 77/77 与 dialect 40/51 均经审计 fresh 复跑证实、1.x 线本环境不可运行的 A/B 裁定成立、提交面恰 14 文件干净。
Completed: 2026-10-13
Commit: edb2b10817

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_e34a0792-1aec-4b0c-99fd-2577fb09ccf1（fresh session）
- Evidence:
  - 9 项验证 7 PASS（nop-dependencies 合线+权威链独立实证 quarkus-bom 62 条 2.0.5 / 5 legacy pom 改名无误伤 / 5 模块 import 撤除零残留 / lettuce 77/77 + TestMySQLDialect 8/10 复跑 / Gate A 459/0 + A/B baseline 证据 / Anti-Hollow 真实容器生命周期 / 文本一致性 / 提交恰 14 文件 / Deferred 诚实性）
  - 1 Major（Gate B 证据引用错位）已修正：40/51 复跑日志以审计留档 `_tmp/wi2451-audit-dialect.log` 为准
  - Minor 修复：pom 两处陈旧 WI10 注释清理、11 失败 follow-up 收录、log 示例归属以审计实测更正（JSON 902 实为 Oracle、postgres 失败为 VARCHAR(4001)/VARBINARY/MONTH()）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2451-testcontainers-2x-unification.md --strict` 退出码 0（回填后复跑）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-dao --severity high` 退出码 0
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - Anti-Hollow：lettuce 真实容器生命周期（创建/连接/ResourceReaper 清理）实测，非 assume-skip 假绿
  - Deferred 项分类检查：11 个能力矩阵失败（既有偏差，归 nop-dao 维护面）——无 in-scope live defect 被降级

Follow-up:

- 见 Non-Blocking Follow-ups（无）
