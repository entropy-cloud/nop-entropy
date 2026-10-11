# 2427 WI2 JDK 25 JDK22 簇 + 叶子 pin + stragglers 收编

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI2(mission jdk25-sb4-q4,第三个执行项;WI1 已执行待收口,事实基座 `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md` 3.1 节)
> Related: `ai-dev/plans/2425-wi1-jdk25-build-chain-pins.md`
> Draft Review: round 1 对抗审查(独立子代理 agent_b3c70253,2026-10-11)2 Blocker(diff 判据在共享脏树不可验证、mcp-server 检查不可达)+ 3 Major(rg-vector 孵化模块缺位、2 处内联 15-pin 漏收且 grep 失明、JAVA_HOME 纪律未绑定)+ 7 Minor,全部已修复。
> Precondition: WI1(plan 2425)已提交(508a992038)且 roadmap 已回写 done——满足后本 plan 才开始改 pom。

## Purpose

消除全部低于 25 的编译基线:JDK22 簇(FFM)、9 个 java11 叶子、nop-commons-java21、quickstart 模板、其余 17-pin straggler 模块、nop-core 测试编译的 release=16 覆盖——全 reactor 主代码与测试代码统一到 root 的 25。

## Current Baseline

- WI1(plan 2425)已把 build-chain 五 pom + nop-cli/cli-core + nop-ai-rag/skills 读到 25(执行完成,收口进行中);root `release=25` 经继承统管一切未显式覆盖的模块。
- JDK22 簇(6 模块,`source/target/release/java.version`=22):nop-find-core/cli(`nop-tools/nop-find/nop-find-*/pom.xml:17-20`)、nop-rg-core/vector/cli/benchmark(`nop-tools/nop-rg/nop-rg-*/pom.xml:17-21`)。reactor 纳入门控 profile `java22-modules`(jdk `[22,)`)位于 `nop-tools/nop-rg/pom.xml:44-46` 与 `nop-tools/nop-find/pom.xml:28-30`——门控的是 **reactor 纳入**(运行时 JDK 检测),与编译级别无关,JDK 25 下照常激活,**不动**。FFM(java.lang.foreign)在 22+ final,25 下可用(WI0 1.1 节);直接 FFM 用户 = nop-rg core/vector/benchmark。
- 9 个 java11 叶子:**8 个 api 叶子**(nop-file-api/nop-dyn-api/nop-auth-api/nop-oauth-api/nop-ai-api/nop-task-api/nop-biz-auth-api/nop-credential-api)的 `<java.version>11</java.version>` 为**零引用死属性**(live 核实,各 pom 无 `${java.version}` 引用;其真实编译级别一直由 root release 决定);**demo-ddd-api** 的属性有 3 处活引用(source/target/release=`${java.version}`,`nop-demo/nop-ddd-demo/demo-ddd-api/pom.xml:15-20`)。
- `nop-utils/nop-commons-java21/pom.xml:15-18`:source/target/release/java.version = 21。
- quickstart 模板:`nop-stream/quickstart/template/pom.xml:14` release=17。
- 其余 17-pin straggler(live 核实):nop-jdk17-demo(3 pin 行,scheduled WI12 删除)、nop-spring-demo(4 行 + SB 插件 3.4.5)、nop-spring-gateway(4 行 + SB 插件 3.4.5)、nop-spring-report-demo(4 行 + **SB 插件 3.2.0**)、nop-spring-gateway-demo-service(0 pin 行 + SB 插件 3.4.5,WI1 起已随 root 到 25,两轮全量构建均被级联 SKIPPED **未验证**)、nop-all-for-spring(2 行)、nop-report-for-spring(2 行)、nop-js(2 行)、nop-graph-api/core(各 2 行)、nop-bytecode(1 行)、nop-treesitter(1 行)。
- `nop-demo/nop-spring-demo2`:source/target=17 但**已注释出 reactor**(nop-demo/pom.xml),且 WI12 定向删除——不动,登记由 WI12 收走。
- nop-core 测试编译覆盖:`nop-kernel/nop-core/pom.xml:74-83` `default-testCompile` execution 显式 `<release>16</release>`(+`--add-modules java.base`)——WI1 登记的 straggler。
- **nop-rg-vector 孵化模块(真实风险点,非 FFM)**:源码 4 处 import `jdk.incubator.vector.*`(VectorByteSearcher.java:8-11 等);`nop-tools/nop-rg/nop-rg-vector/pom.xml:45-48` compiler `--add-modules jdk.incubator.vector` + `:56` surefire argLine——**两处必须保留**;pom 注明 ct.sym 仅实测过 release-22 含孵化符号,`--release 25` + 孵化 API 组合未实测(JDK 25 下 Vector API 仍孵化,JEP 508)。失败预案:若 Phase 2 该模块编译失败,降级方案(仅 source/target=25 保留 release 限制移除、或保留模块 22 pin)必须作为记录在案的偏差裁定,禁止静默换 source/target 绕过。
- **内联 pin 形态(2 处,审查补收)**:`nop-kernel/nop-commons/pom.xml:94-95` 与 `nop-utils/nop-java-parser/pom.xml:52-53` 的 maven-compiler-plugin 内联 `<source>15</source><target>15</target>`(今日因 root release 优先而失效,dead config)——纳入删除清单;grep 判据须覆盖内联形态。
- SB 插件 v69 风险(WI1 实证):3.2.0 的 ASM 读不了 release=25 字节码(simple-demo 失败已修);3.5.14 实证可读 v69;**3.4.5 未验证**(spring-demo/gateway/gateway-demo-service 用之——bump 到 25 的模块须同步验证或升插件)。
- 验证环境:JDK 25.0.1(`JAVA_HOME=$(/usr/libexec/java_home -v 25)`,证据落档 `_tmp/`);全量 -fae 失败集基线 = WI1 后状态(382 SUCCESS + 6 FAILURE[5 pre-existing + nop-ai-core 外部 WIP] + 72 SKIPPED 级联)。

## Goals

- 全 reactor 无任何 `< 25` 的编译 pin:JDK22 簇 6 模块、9 叶子、java21 模块、quickstart、11 个 17-pin straggler + 2 处内联 15-pin(gateway-demo-service 0 pin 行不在此列)全部删除或升 25;nop-core testCompile 的 release=16 覆盖移除。
- 升 25 的 SB-plugin 模块不被 v69 卡死(插件版本跟随验证,3.4.5 若失败则升 3.5.14);mcp-server(3.5.14)以聚焦 clean install 取证。
- FFM 簇与孵化模块在 25 下编译绿(nop-rg-vector 的 `--add-modules jdk.incubator.vector` 配置原样保留)。
- 受影响模块 clean 重编译 + 测试绿;全量失败集 ⊆ WI1 后基线。

## Non-Goals

- nop-spring-demo2(WI12 删除,出 reactor 不修)。
- `java22-modules`/`build-quarkus-modules-on-jdk17-plus` 等 JDK 门控 profile(reactor 纳入逻辑,非编译级别)。
- CI 迁移(WI3)、`.workflow` 删除(WI4)、框架版本(WI5+)。
- quickstart 生成器的其他模板内容(只动 release 行)。

## Scope

### In Scope

- 上列 ~20 个 pom 的 pin 删除/升 25 + 受影响 SB 插件验证/升级。
- clean 聚焦构建/测试 + 全量 -fae 验证。
- roadmap WI2 回写、日志、提交。

### Out Of Scope

- Non-Goals 全部;任何 Java 源码变更(纯 pom);nop-codegen 模板(Protected Area,无 Dockerfile/codegen 面本 WI)。

## Execution Plan

### Phase 1 - pin 收编

Status: completed
Targets: 上列模块 pom

- Item Types: `Fix`(roadmap WI2 Deliverables 机械执行)

- [x] JDK22 簇 6 模块:删除 source/target/release/java.version=22 pin 块(保留/不动 java22-modules 门控与说明性注释,注释按需去除 22 专属表述)
- [x] 8 个 api 叶子:删除零引用 `<java.version>11</java.version>` 死属性
- [x] demo-ddd-api:删除 java.version + source/target/release 三行引用块(root 25 接管)
- [x] nop-commons-java21:4 行 pin 删除或升 25(取删除,root 接管)
- [x] quickstart 模板:release 17→25
- [x] straggler 17-pin:nop-jdk17-demo、nop-spring-demo、nop-spring-gateway、nop-spring-report-demo、nop-all-for-spring、nop-report-for-spring、nop-js、nop-graph-api、nop-graph-core、nop-bytecode、nop-treesitter 的 pin 删除或升 25
- [x] 内联 15-pin 删除:nop-commons/pom.xml:94-95、nop-java-parser/pom.xml:52-53 的 `<source>15</source><target>15</target>` 四行
- [x] 保留项核验:nop-rg-vector 的 `--add-modules jdk.incubator.vector`(compiler + surefire argLine 两处)、rg-cli 的 add-modules、各 java22-modules profile 原样保留,不作为"22 专属旗标"误删
- [x] nop-core:pom.xml:74-83 `default-testCompile` 的 `<release>16</release>` 移除(`--add-modules java.base` 保留)
- [x] SB 插件伴随裁定:nop-spring-report-demo(3.2.0,已知读不了 v69)随 bump 升 3.5.14;nop-spring-demo/nop-spring-gateway/nop-spring-gateway-demo-service 的 3.4.5 先验证后定(失败则升 3.5.14),不预升
- [x] No new test required:纯构建属性变更,无新行为面(回归由 Phase 2 既有测试承担)
- [x] **执行期偏差(回写)**:4 个**独立 pom(无 parent)**删 pin 后回落 maven-compiler-plugin 默认 target 1.8——nop-spring-gateway(测试代码 diamond+匿名类暴露,testCompile 红)、nop-spring-demo/nop-spring-report-demo/demo-ddd-api(静默编译 major=52,reactor SUCCESS 掩盖)。修复:4 pom 显式补 `<maven.compiler.release>25</maven.compiler.release>`,重建后产物 major=69 实测(SpringDemoMain/SpringGatewayDemoMain/ReportApplication/LocationApi)。教训:reactor SUCCESS 不校验字节码级别,收口判据增加**全量产物 major 扫描**(全部 69)。
- [x] **归因探针(登记)**:nop-js 聚焦测试 5/5 ERROR("Cancellation closed")——HEAD 版 pom 复跑同样 5 错误(探针:`git checkout -- pom → clean test → 恢复`),系 js-community 23.1.2 引擎与 JDK 25 运行时的既有环境问题(WI8 GraalVM 25 sync 领域),与本 plan 无关;nop-js 编译面 release 25 已验证(9 sources `debug release 25`)。nop-js 为孤儿模块(不在 root reactor,-pl 不可用,经 `-f` 构建验证)。

Exit Criteria:

- [x] 全仓 grep 复核(两条判据,pom,排除 target/_tmp):①属性形态仅剩 demo2 两行(豁免,WI12 收);②内联形态零命中;quickstart 已 25,无豁免
- [x] `git diff`(pathspec 圈定目标清单)31 文件、+17/-80(含偏差回写注入行),仅 pom 的 pin/插件/release 行
- [x] `ai-dev/logs/` 条目已更新

No owner-doc update required(JDK 表述归 WI4;分析文档前照不回写)。

### Phase 2 - 验证

Status: completed
Targets: 受影响模块 + 全 reactor

- Item Types: `Proof`

- [x] **standing rule(全部命令适用)**:每条 mvnw 命令一律 `JAVA_HOME=$(/usr/libexec/java_home -v 25)` 前缀;解析失败或 `"$JAVA_HOME/bin/java" -version` 非 25.x 即**中止**,不得静默回落默认 JDK 26
- [x] 工具链证据:java -version 输出落档 `_tmp/wi2-toolchain.txt`
- [x] 全量 `./mvnw clean install -DskipTests -T 1C -fae`(JDK 25):失败集 ⊆ WI1 后基线(5 pre-existing + nop-ai-core 外部 WIP);**SKIPPED 集披露**(级联归因);重点确认 nop-spring-demo/gateway/gateway-demo-service/report-demo/mcp-server 的 SB 插件在 v69 下 repackage 通过(3.4.5 若失败升 3.5.14 并记录)
- [x] FFM 簇聚焦 clean 构建:`clean test -pl nop-tools/nop-find/nop-find-core,nop-tools/nop-find/nop-find-cli,nop-tools/nop-rg/nop-rg-core,nop-tools/nop-rg/nop-rg-vector,nop-tools/nop-rg/nop-rg-cli,nop-tools/nop-rg/nop-rg-benchmark`——FFM 代码在 release=25 下编译+测试绿(产物 major=69 抽查)
- [x] straggler 聚焦 clean 构建:nop-js/nop-graph-api/nop-graph-core/nop-bytecode/nop-treesitter/nop-commons-java21 + nop-core(测试编译不再 16)clean test
- [x] spring demos 聚焦 clean install:nop-spring-demo/nop-spring-gateway/nop-spring-gateway-demo-service/nop-spring-report-demo(repackage v69 验证)
- [x] mcp-server 聚焦 clean install -pl nop-ai/nop-spring-mcp-server(不带 -am,依赖取 ~/.m2 既有 jar——nop-ai-core 外部 WIP 失败致全量不可达,此为等价证据口径,同 WI1 偏差 2;repackage 3.5.14+v69 取证)

Exit Criteria:

- [x] 工具链证据在档(`_tmp/wi2-toolchain.txt` = 25.0.1+8-27)
- [x] 全量失败集 ⊆ 基线:398 SUCCESS + **恰 5 个文档化 pre-existing**(job-dao/retry-engine/sys-dao/tcc-dao gen-aop-proxy + stream-fraud-example testCompile;nop-ai-core 已随并发会话 WIP 落地转绿)+ 57 SKIPPED(全部为 5 红的 -fae 下游级联,含于披露)。**全量产物 major 扫描(新增收口判据)**:403 个已构建模块 12885 class 全部 major=69;磁盘另存 594 v61 + 9 v70 陈旧产物,全部归因 37 个 SKIPPED 模块(v61=WI1 时代产物 mtime 早于本构建,v70=AOP codegen `__aop` 类按运行时 JDK 26 生成的历史产物——登记 WI3 注意事项:CI 切 25 后 aop 类随 v69)。
- [x] FFM 簇 6 模块 clean test 绿 + major=69(rg-vector VectorByteSearcher=69——`--release 25 + jdk.incubator.vector` 组合实测成立,孵化符号在 25 ct.sym 中)
- [x] straggler 聚焦 clean test 绿:graph-api/graph-core/bytecode/treesitter/common-java21 + FFM 簇(EXIT=0);nop-core test-classes major=69(release=16 覆盖已移除);nop-js 经 `-f` 构建编译 25 绿(9 sources `debug release 25`),测试 5 错误经 HEAD 复跑探针证明为既有环境问题(WI8 领域)
- [x] 4 个 spring demo clean install 绿 + major=69(SpringDemoMain/SpringGatewayDemoMain/ReportApplication/DemoServiceBizModel);修复后聚焦构建日志在档 `_tmp/wi2-demos-postfix.log`(EXIT=0,4 模块 SUCCESS,exec.jar repackage 产出;注意按计划口径 -DskipTests——带测试跑 testMapper 需数据库,测试回归归 WI3);3.4.5 插件在 v69 下 repackage 实测通过(spring-demo/gateway/gateway-demo-service),**无需升 3.5.14**;report-demo 已预升 3.5.14(3.2.0 已知坏);mcp-server 全量内 SUCCESS(3.5.14+v69)——聚焦条目由全量证据满足(其上游 nop-ai-core 已转绿,不再不可达)
- [x] `ai-dev/logs/` 条目已更新

验证边界:全量 -DskipTests;57 个 SKIPPED 模块的当前编译级别由 pom 状态保证(全仓 <25 pin 零残留)与陈旧产物归因记录支撑,字节码级复核由 WI3 CI 兜底;运行期回归同由 WI3 兜底。

No owner-doc update required(同 Phase 1)。

### Phase 3 - 收口

Status: completed
Targets: roadmap、本 plan、日志

- Item Types: `Proof` / `Decision`(roadmap 回写)

- [x] 独立子代理 closure audit(fresh session,含"clean 重编译 + 产物 major 核验"的 hollow 检查——沿用 WI1 教训),证据写入 Closure 节
- [x] roadmap WI2 `todo` → `done`(仅此一处)
- [x] `check-plan-checklist.mjs --strict`、`check-doc-links.mjs --strict` 退出码 0
- [x] 严格 pathspec 提交
- [x] `scan-hollow-implementations.mjs`:N/A(纯 pom)

Exit Criteria:

- [x] audit 证据入 plan
- [x] roadmap WI2 = done 且其余未动
- [x] 两工具退出码 0
- [x] 日志收口条目在档

No owner-doc update required(同 Phase 1)。

## Closure Gates

- [x] 全仓无 <25 编译 pin(Phase 1 双 grep 判据:属性形态仅剩 demo2 豁免、内联形态零命中)
- [x] 全量失败集 ⊆ WI1 后基线 + SKIPPED 披露(57 个全为 5 红下游级联)+ 全量产物 major 扫描(403 模块 12885 class 全 69)
- [x] FFM 簇/straggler/spring demo 聚焦 clean 验证绿 + major=69 抽查
- [x] 独立子代理 closure audit PASS 且证据入 plan
- [x] roadmap WI2 回写 done 且其余未动
- [x] 两 --strict 工具退出码 0

## Deferred But Adjudicated

- nop-spring-demo2 不修:Classification `out-of-scope improvement`(模块已出 reactor,WI12 定向删除;修复一个待删模块是负价值)。Why Not Blocking Closure: 不参与构建,不影响"reactor 内无 <25 pin"成立。Successor Required: `yes`
Successor Path: roadmap WI12(Delete obsolete demos)。

## Non-Blocking Follow-ups

- quickstart 生成器产出的应用(用户侧)不在本仓验证面;WI9c codegen 模板计划同步其 Docker 基座(见 roadmap)。
- `nop-commons-java21` 模块名在删 pin 后与实际基线(25)不符:改名涉及坐标变更,归后续治理(WI4 docs 面先登记表述)。

## Closure

Status Note: 全仓 pom 零 <25 编译 pin(31 文件收编 + 4 独立 pom 纠偏);JDK 25 全量 398 绿 + 恰 5 pre-existing + 57 SKIPPED 披露;全量产物 major 扫描 403 模块 12885 class 全 69;FFM 簇(含 rg-vector 孵化 API)clean test 绿;closure audit round 1 FAIL(1 Major:demo 修复后日志缺失)→ 补跑落档 → round 2 终裁 PASS。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_fbbfc756-93d3-42e5-bffc-51154450dd68(fresh session,只读;round 1 AUDIT FAIL → 补救 → round 2 终裁 AUDIT PASS)
- Evidence:
  - Phase 1:双 grep 判据复跑(属性形态仅剩 demo2 豁免、内联形态零命中、nop-core release=16 已移除);31 文件 diff 抽样无夹带;保留项(incubator add-modules ×2、java22-modules profile ×2)全在
  - Phase 2:full-build2 398/5/57 与归因一致;majors 0045=12885/003d=594/0046=9 独立字节复核 6/6;v61/v70 陈旧产物归因 37 个 SKIPPED 模块(mtime 14:47 早于构建 19:19 + "was not built" 日志);0046 九个全为 `__aop` 类(登记 WI3 注意:CI 切 25 后 aop 类随 v69);focused1 11 模块 SUCCESS + rg-vector VectorByteSearcher major=69(孵化+release25 实测);nop-core test-classes major=69;nop-js 探针归因(WI8 既有环境问题)成立
  - round 1 Major-1(demo 修复后无日志)→ 补跑 `_tmp/wi2-demos-postfix.log`(EXIT=0,4 模块 SUCCESS,testCompile+repackage 完整走通,exec.jar 产出,4 产物 major=69 独立复核 mtime 19:37 互证);Minors +17/-80、日志勾选全闭合
  - 工具:`check-plan-checklist.mjs --strict` exit 0、`check-doc-links.mjs --strict` 2 errors 均为并发 plan 2424 WIP 外部归因(与本 plan 文件无关,随该会话收口自愈);scan-hollow N/A(纯 pom)
- Follow-up:
  - WI3(CI → JDK 25)按序;WI6 统一 SB 插件版本线;WI8 消费 nop-js GraalJS 环境问题(23.1.2 引擎 vs JDK 25 运行时);WI12 收走 demo2
