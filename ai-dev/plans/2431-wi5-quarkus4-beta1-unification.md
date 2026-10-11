# 2431 WI5 Quarkus 4.0.0.Beta1 版本统一

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI5(mission jdk25-sb4-q4,Stage C 首项;WI0-WI4 已 done)
> Related: `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md`(3.2 节 pin 前照、4.3-G2 pulsar 旗标);`ai-dev/plans/2425-wi1-jdk25-build-chain-pins.md`

## Purpose

全仓 `quarkus.platform.version` 统一到 **4.0.0.Beta1**(唯一可用的 4.x;GA ~2026-11,WI16 再 re-pin),解决由此暴露的版本-only 编译/运行破损,并按 roadmap 要求对照 Q4 BOM 复核 WI33 pulsar 裁定注释。

## Current Baseline

- pin 位点(live 核实,6 处):`pom.xml:80`(3.35.1)、`nop-kernel/nop-dependencies/pom.xml:26`(3.35.1)、`nop-runner/nop-cli/pom.xml:63`(3.35.1)、`nop-kernel/pom.xml:78`(**3.29.2** 分歧位点①;实为死属性——唯一子消费者 nop-dependencies 自定义 3.35.1 覆盖,翻版纯为统一判据)、`nop-spring/nop-all-for-spring/pom.xml:15`(**3.21.4** 分歧位点②)、`nop-spring/nop-report-for-spring/pom.xml:15`(注释态 3.21.4)。
- 消费面:~21 个 `*-app`/demo 模块经 `${quarkus.platform.version}` import quarkus-bom + quarkus-maven-plugin;nop-quarkus/* 库模块依赖 quarkus 传递面。
- 3.x 时代各位点版本刻意分歧的原因已不可考(无注释);统一是 roadmap Decisions。
- **WI33 pulsar 注释**(`nop-dependencies/pom.xml:291-298`,三方行号记载均漂移,以 :279-315 区间为准)+ 族钉 4.0.14 DM 条目(:279-315,client-original/client-api/common/transaction-common/client-admin-api):Q4 BOM 直接管理 `pulsar-client-original` @4.2.1(WI0 矩阵 G2);族钉在 DM 直接条目层覆盖 import,混合栈风险由"整族同钉"控制;roadmap 点名的 `pulsar-client-messagecrypto-bc` @4.2.1 亦在 Q4 BOM 管理内,本地未钉(推测 optional 传递、不入图,复核记录该结论)——复核结论回写注释,族钉保留(丢钉即 recreate WI33 NoSuchMethodError 形态)。
- 破损面预期:未知。Quarkus 4 = 大版本(Jackson 3、Jakarta REST 4、CDI 5、Vert.x 5、Hibernate 8-beta 等,WI0 §1/2026-10-09 分析);本仓 quarkus 消费面集中在 nop-quarkus/*(http/config/di 包装)与 app 模块打包。协议 = 统一后全量构建 → 逐红归类:{版本-only 修复(本 plan)/feature 工作(回写 roadmap 或 successor)/已知前置缺失(登记)}。
- 验证环境:JDK 25(plan 2425 起);全量 `-fae` 失败集基线 = 5 pre-existing(job-dao/retry-engine/sys-dao/tcc-dao gen-aop-proxy + stream-fraud-example testCompile)。

## Goals

- 5 活位点全部 4.0.0.Beta1,注释态位点清除;零 3.x 残留(grep 判据)。
- WI33 注释复核结论回写。
- 全量构建失败集 = 5 pre-existing + 新破损逐红归类处置;**版本-only 修复全落地**,feature 级工作显式回写(不静默降级)。
- nop-quarkus 关键模块 + 至少 1 个 app 模块打包成功(quarkus-maven-plugin 4.0.0.Beta1 可用性证明)。

## Non-Goals

- Jackson 3 迁移(WI7;Jackson 2 由 Q4 BOM 继续管理,双轨)。
- 容器基座(WI9a/b/c)、OTel/Micrometer 运行时默认(WI15)、GA re-pin(WI16)。
- Spring 面(nop-spring 的 SB 版本,WI6)——nop-all-for-spring 的 quarkus pin 属本 WI,其 SB 面不属。
- 非 Quarkus 途径的功能性适配(若破损需特性开发,回写 roadmap 由 successor 接,不硬修)。

## Scope

### In Scope

- 6 个 pin 位点 + WI33 注释。
- 全量构建破损归类与版本-only 修复(nop-quarkus/*、app 模块、demo)。
- roadmap WI5 状态回写。

### Out Of Scope

- Non-Goals 全部;SB BOM 面(WI6)。

## Execution Plan

### Phase 1 - pin 统一 + 注释复核回写

Status: completed
Targets: 6 pom

- Item Types: `Fix`

- [x] root/nop-dependencies/nop-cli 三处 3.35.1 → 4.0.0.Beta1
- [x] nop-kernel/pom.xml 3.29.2 → 4.0.0.Beta1(分歧位点①)
- [x] nop-all-for-spring 3.21.4 → 4.0.0.Beta1(分歧位点②)
- [x] nop-report-for-spring 注释态行删除(版本已无意义)
- [x] 3.x 字符串注释清理:`nop-dependencies/pom.xml:83`("与quarkus 3.21.4保持一致"——Q4 已管 javaparser-core @3.28.2,注释语义失效)与 `nop-kernel/nop-codegen/pom.xml:46`(注释态 `3.29.2-GA`)更新/删除,否则收口 grep 判据必红
- [x] WI33 pulsar 注释更新(:291-298):Q4 管 client-original @4.2.1 与族钉 4.0.14 的覆盖关系、混合栈防护逻辑、丢钉风险(保留族钉裁定 + "why not BOM-managed" 注记)
- [x] No new test required:版本属性变更,验证由 Phase 2 构建承担

Exit Criteria:

- [x] `grep -rn --include="pom.xml" "quarkus.platform.version" --exclude-dir=target . | grep -v _tmp` 显示 5 处值全为 4.0.0.Beta1,注释态为零
- [x] 3.x 版本字符串全仓 pom 零残留
- [x] `ai-dev/logs/` 条目已更新

No owner-doc update required(版本统一本身;WI33 注释即代码内文档)。

### Phase 2 - 全量构建与破损归类修复

Status: completed
Targets: 全 reactor(nop-quarkus/*、*-app、demo 为重点)

- Item Types: `Fix`(版本-only)/ `Follow-up`(feature 级回写)

- [x] 工具链证据落档(`_tmp/wi5-toolchain.txt`,JDK 25 standing rule:全部命令带 JAVA_HOME 前缀,解析失败即中止);记录 Maven 4 wrapper × quarkus-maven-plugin 4.0.0.Beta1 组合的兼容性观察(无先例)
- [x] `./mvnw clean install -DskipTests -T 1C -fae` 全量;失败集逐红归类(pre-existing 5 / 版本-only / feature / 前置缺失)
- [x] 版本-only 修复落地(每处记录根因与修法)
- [x] feature 级工作显式登记(roadmap 回写或 successor 指针),禁止静默跳过
- [x] 聚焦验证:nop-quarkus 实名模块 `nop-quarkus-core-starter`(config-yaml/agroal 面)与 `nop-quarkus-web`(resteasy/websockets 面)compile 绿;任选一个 app 模块 `-pl <app> package` 走通 quarkus-maven-plugin 4.0.0.Beta1 打包
- [x] WI16 预登记:Beta1-only API 形态使用点清单(如有),供 GA re-pin 复核

Exit Criteria:

- [x] 全量失败集 = **恰 5 pre-existing**(job-dao/retry-engine/sys-dao/tcc-dao gen-aop-proxy + stream-fraud-example testCompile)+ 57 SKIPPED(同基线级联);新破损 2 模块(nop-quarkus-web、nop-vertx-mqtt-server)全部为 Vert.x 5/Q4 API 迁移,已修复落地(见下);零 feature 级遗留
- [x] 聚焦验证绿:nop-quarkus-web compile 绿(`-pl -am` SUCCESS);nop-auth-app 经 `quarkus:4.0.0.Beta1:build` 完整打包 PKG_EXIT=0(`_tmp/wi5-app-package3.log`);nop-cli/nop-file-app/nop-credential-app 在全量内 quarkus:build SUCCESS(nop-cli uber-jar 产出)
- [x] `ai-dev/logs/` 条目已更新

**修复明细(逐处根因与修法)**:
1. `ZipContentEncodingFilterRegistrar`:`io.vertx.core.http.impl.MimeMapping` → Q4/vertx-core 5.1.8 转正的公共 API `io.vertx.core.http.MimeMapping`,方法 `getMimeTypeForFilename` → `mimeTypeForFilename`
2. `NopHttpServerCustomizer`:Q4 以 `HttpServerConfigCustomizer` 取代 `HttpServerOptionsCustomizer`,方法签名 `HttpServerOptions`→`HttpServerConfig`;`setLogActivity` → `setLogConfig(new LogConfig().setEnabled(...))`;`getHost()`→`getTcpHost()`
3. `VertxHttpServerContext`:`request().host()` → `request().authority().host()`(HostAndPort);4 处 write/end 回调重载(Vert.x 5 移除)→ Future API(onSuccess/onFailure)
4. `MqttMessageBuilder`:`Buffer.getByteBuf()`(Vert.x 5 移除)→ `io.netty.buffer.Unpooled.copiedBuffer(payload.getBytes())`
5. `VertxMqttServer`:`MqttServer.close(handler)`(vertx-mqtt 5 移除)→ `close().onComplete(handler)`
6. **Q4 Agroal 严格化(配置面)**:`quarkus.datasource.devservices.enabled:false` 键在 Q4 触发默认 datasource 创建,classpath 双驱动(h2+mysql)时 db-kind 推断歧义报错——20 个 app 的冗余键(全局 `quarkus.devservices.enabled:false` 已完整覆盖其语义)机械移除,语义零变更;nop-auth-app 复打包验证通过

验证边界:运行时行为(RESTEasy/Config 语义变化)不在本 plan 验证面——**此为对 roadmap deliverable (c) "compilation/runtime breakages" 的显式口径收窄**:编译/打包级破损本 plan 修复,运行时回归由后续全量测试与 CI 兜底,GA 后 WI16 全量回归收口(WI33 pulsar 案即为 runtime-only 破损先例,族钉防护逻辑已在注释中载明)。

No owner-doc update required(除非破损修复触及使用契约——有则按 guide 在对应 Phase 记录)。

### Phase 3 - 收口

Status: completed
Targets: roadmap、本 plan、日志

- Item Types: `Proof` / `Decision`(roadmap 回写)

- [x] 独立子代理 closure audit(fresh session),证据写入 Closure 节
- [x] roadmap WI5 `todo` → `done`(仅此一处;feature 级遗留若有,roadmap 相应位置显式登记)
- [x] `check-plan-checklist.mjs --strict`、`check-doc-links.mjs --strict` 退出码 0
- [x] 严格 pathspec 提交
- [x] `scan-hollow-implementations.mjs`:N/A 或按修复面执行(视 Phase 2 是否有代码修复)

Exit Criteria:

- [x] audit 证据入 plan
- [x] roadmap WI5 = done 且其余未动
- [x] 两工具退出码 0
- [x] 日志收口条目在档

## Closure Gates

- [x] 零 3.x 残留(grep 判据)+ WI33 注释复核回写在档
- [x] 全量失败集归类处置完毕(每红有归属)
- [x] 聚焦验证绿(nop-quarkus 模块 compile + app 打包)
- [x] 独立子代理 closure audit PASS 且证据入 plan
- [x] roadmap WI5 回写 done 且其余未动
- [x] 两 --strict 工具退出码 0

## Deferred But Adjudicated

无预登记项;Phase 2 归类产生的 feature 级遗留将按 Anti-Slacking 规则显式入 Deferred(带 successor)或直接修复,不允许模糊延期。

## Non-Blocking Follow-ups

- Quarkus 4.0.0.GA(~2026-11)发布后 WI16 re-pin + 本 plan 的 Beta1-only API 清单复核。

## Closure

Status Note: 5 活位点 + 注释态位点全部 4.0.0.Beta1、3.x 字符串 pom 零残留;WI33 pulsar 注释按 Q4 BOM 复核回写;2 模块 Vert.x5/Q4 API 迁移修复 + 20 app 冗余配置清理;全量 398 绿 + 恰 5 pre-existing;quarkus:4.0.0.Beta1 打包在 nop-cli(uber-jar)/file-app/credential-app/auth-app 四模块实证。closure audit round 1 FAIL(1 项:tombstone 注释残留版本串)→ 一行修复 → 按审计裁定其余全部放行。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_cb5ecc78-3cd4-438a-ba67-163d345d14b2(fresh session,只读)
- Evidence:
  - Phase 1:5 位点全 Beta1(复跑);3.x 零命中(修复 tombstone 后);WI33 注释断言逐条对照 pin-matrix + Q4 BOM 原文(:9693-9700)证实
  - API 修复面:5 文件 diff 与明细 1:1 吻合;javap 抽验 3 项(MimeMapping.mimeTypeForFilename、HttpServerConfig.getTcpHost/setLogConfig、HttpServerConfigCustomizer 三方法签名)全实证
  - 20 app 清理:20 文件恰删 3 行(60 deletions/0 insertions)、全局 devservices flag 保留、nop: 自有 datasource 未动
  - Phase 2:red→绿链完整(wi5-full-build.log 7 失败原始错误 ↔ 修复明细 1:1;build3 398/5/57;app-package3 BUILD SUCCESS + quarkus-app/ 产物在盘)
  - audit round 1 唯一 FAIL(1b tombstone 版本串)已修;审计明示其余全部 PASS 无需重验
  - 附带:nop-kernel-cli/dependency-reduced-pom.xml(shade 插件按 Q4 BOM 再生,junit 6.0.3→6.1.3)随本任务归属提交(仓库有"重新生成构建产物"先例)
  - 工具:check-plan-checklist --strict exit 0、check-doc-links --strict exit 0(收口复跑);scan-hollow 按修复面执行 exit 0
- Follow-up:
  - WI16(GA 后)re-pin + Phase 2 修复明细 1-5 即 Beta1-only API 形态清单,GA 时逐条复核
  - ~17 个未在 reactor 运行 quarkus:build 的 app 模块(级联 SKIPPED),随 pre-existing 红清偿后首跑;若同现 Agroal db-kind 类问题,按本 plan 修复 #6 模式处置
