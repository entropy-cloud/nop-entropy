# 2422 WI0 jdk25-sb4-q4 升级基线分析

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI0(升级基线分析,Work Item Status 第一个 `todo` 项)
> Related: `ai-dev/analysis/2026-10/2026-10-09-quarkus4-vs-springboot4-dependency-alignment.md`(上游 BOM 对比主来源);mission `missions/jdk25-sb4-q4.json`
> Draft Review: round 1 对抗审查(独立子代理 agent_52ce4532,2026-10-11)无 Blocker;4 Major(M1 cache finding 缺失/M2 族谱计数/M3 mail 锚点/M4 imported-BOM 局限未声明)+ 10 Minor 已全部修复,裁定可执行。

## Purpose

把 JDK 25 / Spring Boot 4.1.1 / Quarkus 4.0.0.Beta1 / GraalVM 25 平台升级的**上游事实、双 BOM 坐标级分歧、本仓 pin 现状(前照)**冻结为一份仓内分析文档,作为 WI1-WI16 全部执行计划的共同事实基座。纯研究项,零代码/pom 变更。

## Current Baseline

以下事实均已在 live repo 核实(2026-10-11,方法与命令见各条):

- roadmap `jdk25-springboot4-quarkus4-roadmap.md` 的 Work Item Status 中 WI0-WI16 全部为 `todo`;mission 配置 `missions/jdk25-sb4-q4.json` 已创建。
- 上游对比主来源 `ai-dev/analysis/2026-10/2026-10-09-quarkus4-vs-springboot4-dependency-alignment.md` 已存在(37 组共享库样本、Hibernate/Liquibase/MySQL Connector/J 三大未对齐点、Q4 Beta1 依赖跳变清单)。
- WI0 要求的分析文档(升级基线:事实冻结 + 坐标决策矩阵 + pin 前照清单)**尚不存在**。
- 本机工具链:macOS arm64,默认 JDK 26.0.1(Zulu),另装 JDK 25.0.1(Oracle);`--release 25` 可行。CI 全部 `setup-java` 为 21(maven.yml ×3、compliance.yml ×3)。
- 双 BOM 已抓取并脚本比对(`_tmp/wi0/compare_boms.py`,数据快照 2026-10-11):quarkus-bom 4.0.0.Beta1 直接管理 2161 坐标,spring-boot-dependencies 4.1.1 直接管理 647 坐标;交集 90,同版本 59,**分歧 31**——与 roadmap "~31 coords" 精确吻合,且 roadmap 列举的 7 个示例坐标全部在分歧清单中(方法有效性交叉验证)。
- 本仓 JDK pin 全量 98 行(`_tmp/wi0/jdk-pins-inventory.txt`):root/kernel/demo/quarkus=17;JDK22 簇 = nop-cli、nop-cli-core、nop-find core/cli、nop-rg core/vector/cli/benchmark(8 模块);21 = nop-ai-rag、nop-ai-skills、nop-commons-java21;`java.version=11` 恰好 9 个叶子(与 roadmap 计数一致)。
- FFM 直接用户核实为 nop-rg core/vector/benchmark 三模块(`java.lang.foreign` import);nop-rg-cli 源码零 FFM,pom 注释明示 22 门控是"与 nop-rg-core 一致";nop-find-core 注释明示因依赖 nop-rg-core 而 22 门控——roadmap 决策段"只有三个 nop-rg 模块是 FFM-based"的精确表述为 core/vector/benchmark。
- 框架 pin:quarkus 平台位点共 6 处(5 活 + 1 注释态)——3.35.1(root、nop-dependencies、nop-cli)、3.29.2(nop-kernel)、3.21.4(nop-all-for-spring)、注释态 3.21.4(nop-report-for-spring);spring-boot 3.5.14 + demo2 独立 3.2.0;spring-cloud 2025.0.2;spring-ai 1.1.5;graalvm 23.1.2(nop-js 的 js-community **与** polyglot 均硬编码 23.1.2);truffle 25.2.4(nop-xlang-truffle)。
- Dockerfile 基础镜像 87 个 FROM 行:openjdk-17:1.16 ×40、openjdk-17:1.11 ×1、openjdk-21:1.21 ×2、ubi-minimal:8.8 ×21、ubi-minimal:8.5 ×1、micro-image:2.0 ×21、micro-image:1.0 ×1;codegen 模板 4 个(.xgen)基座为 17:1.16 ×2、ubi-minimal:8.8 ×1、micro-image:2.0 ×1。
- mail/cache/jackson 现状与 roadmap 记载一致:com.sun.mail:jakarta.mail @1.6.8 活 pin(nop-dependencies DM :199-203,属性 `jakarta.mail.version` :90);cache-api 在 nop-commons + nop-hazelcast-cache;真实 jackson-databind 站点 8 个(4 eishay + 3 nop-ai-api test + 1 nop-api-core test)。
- nop-dependencies 活 pin(被 DM 条目引用)58 唯一坐标(DM 条目 59 条,nacos-client 重复声明一次);另存在**死属性** `slf4j-api.version`、`logback.version`、`commons-lang3.version`、`commons-collections4.version`、`opencsv.version`(全仓零消费者,含 DM 与模块 pom)。
- 2026-10-09 分析的 37 组样本与本次 90 组交集不矛盾(前者是人工抽样,后者是 DM 交集全集)。
- spring-cloud 2025.1.x 线与 spring-ai 2.0.x 线的存在性已在线核实(Maven Central maven-metadata,抓取日 2026-10-11):cloud 最新 2025.1.3(SB4 兼容起点 2025.1.2)、ai 最新稳定 2.0.1。

## Goals

- 产出 `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`:升级事实基座文档,Status: resolved。
- 文档内含四块可核查交付物:①上游事实冻结(Java 基线/UBI 10/Hibernate DB 版本下限/Jackson 3/SB4 模块化 starter/OTel/Micrometer/GraalVM 25/no Jakarta Cache finding);②双 BOM 31 坐标分歧全清单;③本仓 pin 前照清单(JDK pin 全量归类、框架 pin、CI JDK、Dockerfile 分布);④nop 活 pin × 双 BOM 决策矩阵(含风险旗标与方法局限声明)。
- 为 WI1-WI16 提供可直接引用的事实来源:WI10 不得重新推导分歧清单(31 坐标);矩阵的 UNMANAGED/Q4-only/SB4-only 判定按文档声明的方法局限边界消费(见 Phase 1 方法节)。

## Non-Goals

- 任何代码、pom、CI、Dockerfile 变更(WI1+)。
- 版本升级动作本身的裁定(丢 pin/迁坐标由 WI10/WI11/WI13/WI14 执行)。
- Quarkus 4.0 GA 后的复核(WI16;届时本清单需按 GA BOM 重算)。
- imported BOM 的递归展开(方法局限已在文档显式声明,不改变 31 坐标 headline 结论的可用性)。

## Scope

### In Scope

- 新建 1 个分析文档(`ai-dev/analysis/2026-10/`)。
- 双 BOM 抓取与比对脚本运行(脚本与中间数据留在 `_tmp/wi0/`,不入库;文档自身落档全量清单为持久锚)。
- roadmap Work Item Status 中 WI0 `todo` → `done` 回写(closure audit 通过后)。

### Out Of Scope

- roadmap 其余 16 个 WI 的任何实施。
- `docs-for-ai/` owner doc 更新(研究项不改 live baseline;WI1+ 各自负责)。
- 上游版本的未来演进追踪(2026-10-11 快照,时效性写入文档头部)。

## Execution Plan

### Phase 1 - 上游事实冻结与分歧清单落档

Status: completed
Targets: `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`

- Item Types: `Proof`

- [x] 写入文档头(Status: resolved、Date、Scope、Conclusion、时效性声明:Q4 为 Beta1,GA 后 WI16 重算)
- [x] 方法节:判定方法 = 两 BOM **dependencyManagement 直接条目**交集(imported BOM 未递归展开:SB4 导入约 44 个 BOM、Q4 约 14 个);verdict 语义限定 = "UNMANAGED/Q4-only/SB4-only" 指**无直接管理**,不排除经 imported BOM 的间接管理(pulsar 行为已知受限实例);headline 结论(31 坐标)不受该局限影响——执行修正:实测 Q4 quarkus-bom **零 import**(2161 条全直接),Q4 侧无盲区,已写入文档方法节
- [x] 上游事实节:Java 基线(Q4 min 21 / SB4 min 17 / 目标 25)、UBI 10 默认、Hibernate DB 版本下限(Db2 12.1 / MariaDB 11.4 / MSSQL 16 / MySQL 8.4 / Oracle 23 / PG 16)、Jackson 3 rename(`com.fasterxml.jackson.core/databind`→`tools.jackson.*`,annotations 不动)+ CDI `JsonMapper`、SB4 模块化 starter + `@MockitoBean`/`RestTestClient`、OTel `http/protobuf:4318` + 10% 采样、Micrometer Prometheus v1、GraalVM 25——来源标注 roadmap + 2026-10-09 分析 + 上游 migration guide
- [x] no-Jakarta-Cache finding:`jakarta.cache` 无任何发行版;SB4 仅管 `javax.cache:cache-api` 1.1.1(javax 线,未跟进);WI14 替代坐标 = Ehcache 3 `jakarta` classifier(roadmap 决策)——作为 WI14 的事实基座落档(文档 1.8 节;新增 [BOM-verified] 事实:SB4 直管 ehcache 3.12.0 基线)
- [x] 双 BOM 31 坐标分歧全清单落档(Hibernate 家族 ×8、Flyway ×6、Elasticsearch ×2、其余 ×15 单项含 liquibase-core),标注判定方法引用与 59 组同版本参照(文档 2.2/2.3 节全量落档)
- [x] spring-cloud 2025.1.x 线(最新 2025.1.3,SB4 兼容起点 2025.1.2)与 spring-ai 2.0.x 线(最新 2.0.1)存在性核实记录(来源:Maven Central maven-metadata,抓取日 2026-10-11)(文档 2.5 节)

Exit Criteria:

- [x] 文档存在且包含上述各项内容,每项事实带来源标注;31 坐标全表 + 59 同版本参照**全量落档在文档内**(持久锚,不依赖 `_tmp`)——`ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md` 2.2/2.3 节
- [x] 31 坐标清单与脚本输出一致(抽查 3 个坐标版本号可复算:hibernate-core 8.0.0.Beta3/7.4.5.Final、jakarta.servlet-api 6.0.0/6.1.0、snakeyaml 2.7/2.6 均与 bom-maps.json 一致)
- [x] 无代码/pom/CI/Dockerfile 变更(`git status` 仅新增分析文档与本 plan、及日志/roadmap 的收口期改动)
- [x] `ai-dev/logs/` 对应日期条目已更新(2026-10-11 WI0 条目)

No owner-doc update required(纯研究项,不改 live baseline;WI1+ 各自负责 owner doc)。

### Phase 2 - 本仓前照清单与决策矩阵落档

Status: completed
Targets: `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`

- Item Types: `Proof`

- [x] JDK pin 前照:98 行归类表(build-chain 17 系 / 其余 17 pin 模块(nop-graph、nop-bytecode、nop-treesitter、nop-js、各 spring demo 等)/ JDK22 簇 8 模块 / 21 三模块 / 11 九叶子 / quickstart 模板),含文件:行号锚点(文档 3.1 节)
- [x] 框架 pin 前照:quarkus 平台 6 位点(5 活 + 1 注释态)、spring-boot 2 处、cloud/ai/graalvm/truffle/js 硬编码点,含文件:行号锚点(文档 3.2 节)
- [x] CI JDK 前照:6 个 setup-java 位点 + legacy `.workflow/*` 三个 pipeline 的 JDK/Maven;本地工具链(26 默认 + 25 可选)记录(文档 3.3/3.5 节;新增事实:maven.yml 无 nop-cli lint 门禁位点,WI3 重启时按 runbook 落位)
- [x] Dockerfile 前照:87 FROM 行分布表 + 4 个 codegen 模板基座(文档 3.4 节)
- [x] 决策矩阵:58 活 pin 坐标 × {nop-pin, Q4, SB4, 判定}全量表落档(持久锚),附 5 个死属性清单(标注消费方 WI10);判定分布实测:48 UNMANAGED / 6 Q4-ONLY / 3 SB4-ONLY / 1 BOTH-AGREE(文档 4.1/4.2 节)
- [x] 风险旗标注(供对应 WI 消费,不做裁定):G1 kafka/Debezium floor(WI10)、G2 pulsar 混栈 + imported-BOM 活实例(WI5)、G3 antlr 插件联动(WI10)、G4 ojdbc groupId 迁移(WI10)、G5 js-community/polyglot/truffle **均不被 Q4 管理**——roadmap "Q4-managed@25.0.4.1" 前提不成立,须显式 pin(WI8)、G6 mail 迁移坐标双 BOM 同版本佐证(WI11)(文档 4.3 节)

Exit Criteria:

- [x] 前照清单各计数与 live repo 复核命令一致(JDK pin 98 行 / 9 个 java11 / Dockerfile 87 FROM / CI 6 位点)
- [x] 矩阵 58 坐标全量表落档在文档内(持久锚),抽查 3 行与脚本输出一致(kafka-clients 4.3.1/4.2.1/4.2.1、antlr4 4.13.0/4.13.2/-、janino 3.1.12/-/3.1.12)
- [x] 6 个风险旗标 + 5 个死属性各自标注消费 WI 编号,未做任何执行裁定
- [x] `ai-dev/logs/` 对应日期条目已更新

No owner-doc update required(同 Phase 1)。

### Phase 3 - 收口:closure audit 与 roadmap 状态回写

Status: completed
Targets: roadmap、本 plan、`ai-dev/logs/2026/10-11.md`

- Item Types: `Proof` / `Decision`(roadmap 状态回写为管理裁定)

- [x] 独立子代理 closure audit(非本会话自审,fresh session),证据写入本 plan Closure 节
- [x] roadmap Work Item Status:WI0 `todo` → `done`(仅此一处状态变更,不触碰其他 WI)
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2422-wi0-jdk25-sb4-q4-upgrade-baseline-analysis.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] 提交采用严格 pathspec(仅本任务文件;共享 index 中他人暂存不带入)
- [x] `scan-hollow-implementations.mjs`:N/A(纯文档计划,无代码实现面)

Exit Criteria:

- [x] closure audit 证据(每条 Exit Criterion PASS/FAIL + 来源)已写入 plan Closure 节
- [x] roadmap 中 WI0 行状态为 `done`,其余 WI 行未被修改
- [x] check-plan-checklist --strict 与 check-doc-links --strict 退出码均 0
- [x] `ai-dev/logs/` 收口条目已更新

No owner-doc update required(同 Phase 1)。

## Closure Gates

> 纯文档/研究计划(零代码变更),Maven 构建验证条目按 guide 规则省略;脚本比对的可复算性由 Phase 1/2 Exit Criteria 的抽查项承担。

- [x] 分析文档存在、Status: resolved、四块交付物齐全且与 live repo/BOM 快照可复算(31 坐标全表 + 58 矩阵全表以文档自身为持久锚)
- [x] 零代码/pom/CI/Dockerfile 变更(git pathspec 圈定核实)
- [x] roadmap WI0 状态回写 `done` 且其余 WI 未动
- [x] 独立子代理 closure audit 完成且证据已写入 plan
- [x] 不存在被静默降级的 in-scope 项(本研究项无 deferred 候选:风险旗标全部显式挂到消费 WI)
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无 deferred 项。风险旗标不是 deferred:它们是本研究的**产出**(交付给 WI5/WI8/WI10 消费的事实),不是本计划未完成的工作。

## Non-Blocking Follow-ups

- imported BOM 的递归展开比对(若 WI10 执行中发现 UNMANAGED 判定不足以裁定某坐标,可在 WI10 plan 内做单点展开,无需重开本研究)

## Closure

Status Note: WI0 交付物(升级基线分析文档)四块齐全且经独立子代理全量复算(31/59/58 三表零失配、98/87/6×21 计数复现、G1-G6 断言与 BOM 原文一致);audit 4 Minor(M1-M4 文字精度)已全部修复;roadmap WI0 已回写 done;研究结论作为 WI1-WI16 共同事实基座,后续 WI 按文档引用节消费,不得重新推导。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_244da64a-98f9-4299-9a4e-1a794d363971(fresh session,只读)
- Evidence:
  - Phase 1 Exit Criteria 4/4 PASS:文档 396 行四块交付物齐;31 坐标表与 bom-maps.json **全量**比对零失配(程序化);59 参照表零失配;`git status` 无代码/pom/CI/Dockerfile 变更;日志条目在档
  - Phase 2 Exit Criteria 4/4 PASS:JDK pin 98/9 叶子/Dockerfile 87(40+1+2+21+1+21+1)/CI 6×21 全复现;矩阵 58 行全量比对零失配,分布 48 UNMANAGED/6 Q4-ONLY/3 SB4-ONLY/1 BOTH-AGREE 双向复算自洽;旗标 G1-G6 + 死属性 ×5 全部挂消费 WI
  - 专项核验:G5 勘误级断言成立(quarkus-bom 原文 import scope 计数 0,js-community/truffle 零命中,polyglot 仅现于 graal-sdk 条目的 exclusion);10+ 行号锚点全中(nop-kernel:78=3.29.2、demo2:15=3.2.0、mail DM :199-203、nop-js:27-36 等);Jackson 8 站点、FFM 三模块、WI33 注释、Maven Central 2025.1.3/2.0.1 均复核成立
  - Audit 发现 4 Minor 全部修复:M1 build-chain "4 pom"→"5 pom";M2 "约 44 imported BOM"→46(两处);M3 plan Phase 2 EC "5 个风险旗标"→6;M4 WI33 注释锚点 :292-303→:290-302
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0;`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0(completed 后复查)
  - scan-hollow-implementations.mjs:N/A(纯文档计划,无代码实现面,Phase 3 checklist 显式记录)
  - Anti-Hollow:不适用代码调用链;等效检查 = 交付物四块与 live repo/BOM 快照可复算(独立子代理全量程序化比对,非仅抽查)

Follow-up:

- WI1-WI16 按 roadmap 顺序执行;WI10 消费 4.1 矩阵 + G1/G3/G4、WI5 消费 G2、WI8 消费 G5、WI11 消费 G6/2.3 节、WI14 消费 1.8 节
- imported BOM 递归展开留作 WI10 执行中的单点按需动作(Non-Blocking Follow-ups 已记录)
