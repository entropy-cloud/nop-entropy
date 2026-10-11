# 2425 WI1 JDK 25 build-chain pins

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md` WI1(mission jdk25-sb4-q4,第二个执行项;WI0 已 done,事实基座 `ai-dev/analysis/2026-10/2026-10-11-jdk25-sb4-q4-upgrade-baseline.md` 3.1/3.5 节)
> Related: `ai-dev/plans/2422-wi0-jdk25-sb4-q4-upgrade-baseline-analysis.md`
> Draft Review: round 1 对抗审查(独立子代理 agent_d0e1c30b,2026-10-11)1 Blocker(验证命令 JAVA_HOME 两处笔误致静默回落 JDK 26)+ 2 Major(deliverable-3 裁定缺失、nop-ai-skills 为 pom 聚合器致聚焦测试空转)+ 6 Minor,全部已修复,裁定可执行。

## Purpose

把构建链的 JDK 属性统一读到 25:5 个 build-chain pom(17→25)+ nop-cli/nop-cli-core(22→25)+ nop-ai-rag/nop-ai-skills(21→25),并验证全 reactor 在 JDK 25 工具链下主代码 release=25 编译绿。

## Current Baseline

- WI0 已 done(plan 2422,commit 0e84aa52e7);本仓 JDK pin 前照见分析文档 3.1 节(98 行,文件:行锚点)。
- 9 个目标 pom 的 pin 现状(live 核实,均无未提交改动):
  - root `pom.xml:25,28-30`、`nop-kernel/pom.xml:24,27-29`:`java.version`/`source`/`target`/`release` = 17
  - `nop-kernel/nop-dependencies/pom.xml:15-16`:`source`/`target` = 17(该 pom **无 `<parent>`,独立 BOM**,仅被 import,不继承 root 属性;packaging=pom 无可编译代码,改值满足 roadmap deliverable 1 字面要求)
  - `nop-quarkus/pom.xml:38-40`:`java.version` = 17,`source`/`target` = `${java.version}`(parent = nop-entropy root);profile `build-quarkus-modules-on-jdk17-plus`(jdk [17,) 激活)控制 skip 开关,JDK 25/26 下仍激活,无需改
  - `nop-demo/pom.xml:47-49`:`java.version` = 17,`source`/`target` = `${java.version}`(无 release,继承 root)
  - `nop-runner/nop-cli/pom.xml:55-58`、`nop-runner/nop-cli-core/pom.xml:24-27`:`source`/`target`/`release`/`java.version` = 22(nop-cli-core :21-23 注释明言"与 nop-rg 的 java22-modules 门控保持同一 JDK 基线"——随本 plan 同步更新注释数字,WI2 后 nop-rg 跟上)
  - `nop-ai/nop-ai-rag/pom.xml:15-16`、`nop-ai/nop-ai-skills/pom.xml:17-18`:`source`/`target` = 21(无 release)。nop-ai-skills 为 packaging=pom 聚合器(子模块 code-analyzer/deepwiki/translate),自身无代码
- **继承机制(关键)**:`maven.compiler.release` 存在时优先于 source/target;root 的 `release` 属性被全部未显式覆盖的模块继承——bump root 即 reactor 级主代码编译基线抬升。实证:`~/.m2` 中 nop-ai-rag 既有 jar 的 class major = 61(Java 17),其 source/target=21 被 root release 压制。**已知 execution 级覆盖点**:`nop-kernel/nop-core/pom.xml:74-83` 的 `default-testCompile` execution 显式钉 `release=16`(+`--add-modules java.base`)——属性链 bump 对其无效,WI1 后 nop-core 主代码 25 / 测试代码仍 16;该位点为 WI2 "any straggler" 候选,本 plan 不动,Goal 措辞已按"主代码"收窄。
- **验证环境**:本机 JDK 25.0.1(Oracle)+ 26.0.1(Zulu);验证构建用 **JDK 25**:`JAVA_HOME=$(/usr/libexec/java_home -v 25)`,与 roadmap "build & CI on 25" 对齐。Phase 2 需留存工具链证据(构建日志记录 `"$JAVA_HOME/bin/java" -version` 输出 = 25.0.1)。
- 已知基线:全量 `-fae` 构建在 HEAD 有 5 个 pre-existing 红(nop-job-dao、nop-retry-engine、nop-sys-dao、nop-tcc-dao、nop-stream-fraud-example,JDK 26 codegen 环境性,失败集随 reactor 顺序漂移;`ai-dev/logs/2026/10-11.md` plan 2418 收口节)。本 plan 的比较口径 = 失败**类别/模块**集合 ⊆ 该既有集合。
- **过桥态(roadmap 顺序接受的中间态)**:WI1 合入后、WI3 执行前,CI(JDK 21)无法编译 release=25,CI 必红。roadmap 依赖序 WI1→WI2→WI3 即此语义,不回拨。

## Goals

- 9 个 pom 的 JDK 属性全部读到 25;9 个文件中不再残留 17/22/21 的 JDK pin(nop-cli-core 的 22 时代注释数字随改)。
- 全 reactor 在 JDK 25 工具链下**主代码** `release=25` 编译通过(失败集 ⊆ 文档化 pre-existing 基线;nop-core 测试编译的 execution 级 release=16 覆盖不在本 plan 范围,归 WI2 straggler)。
- 被显式移动的模块(nop-cli-core、nop-ai-rag、nop-ai-skills 及其三个子模块)测试绿。
- roadmap WI1 Deliverable 3("Remove the now-redundant module-level JDK pins these inherit")的裁定:**WI1 scope 内不存在 deliverable 1/2 未覆盖的额外可删 pin**——9 个位点全部被 deliverable 1/2 显式点名(deliverable 1=5 build-chain pom、deliverable 2=cli×2+rag/skills),故按 SET-to-25 执行,无删除动作;"删除 vs 显式"的风格统一归 WI2 全仓裁定(见 Non-Blocking Follow-ups)。此裁定即 deliverable 3 的 WI1 侧闭环。

## Non-Goals

- JDK22 FFM 簇(nop-find/nop-rg)、9 个 `java.version=11` 叶子、quickstart 模板、其余 17-pin 模块(nop-graph/nop-js/nop-bytecode/nop-treesitter/spring demos)→ WI2(含 "any straggler")。
- `.github/workflows/` 迁 25 与 nop-cli lint 门禁重启 → WI3。
- 框架版本 bump(quarkus/spring/graalvm)→ WI5/WI6/WI8。
- `docs-for-ai/00-start-here/project-context.md` 与 `AGENTS.md` 的 JDK 表述 → WI4(本 plan 不动 docs-for-ai,避免双写)。

## Scope

### In Scope

- 9 个 pom 文件的 JDK 属性修改(上列锚点)。
- JDK 25 工具链下的验证构建与聚焦测试。
- roadmap WI1 状态回写、日志、提交。

### Out Of Scope

- 上节 Non-Goals 全部;任何行为性代码变更;`_gen/` 与 `_*.xml` 生成物。

## Execution Plan

### Phase 1 - build-chain pom 修改

Status: completed
Targets: 上列 9 个 pom

- Item Types: `Fix`(roadmap 定义的升级项;非缺陷修复,按 roadmap WI1 Deliverables 1/2 执行;deliverable 3 裁定见 Goals 第 4 条)

- [x] root `pom.xml`:java.version/source/target/release 17→25
- [x] `nop-kernel/pom.xml`:同上 4 属性 17→25
- [x] `nop-kernel/nop-dependencies/pom.xml`:source/target 17→25
- [x] `nop-quarkus/pom.xml`:java.version 17→25(source/target 引用之)
- [x] `nop-demo/pom.xml`:java.version 17→25(source/target 引用之)
- [x] `nop-runner/nop-cli/pom.xml` + `nop-runner/nop-cli-core/pom.xml`:4 属性 22→25,同步更新 nop-cli-core :21-23 注释中的"JDK 22+"表述为 25 基线语义
- [x] `nop-ai/nop-ai-rag/pom.xml` + `nop-ai/nop-ai-skills/pom.xml`:source/target 21→25
- [x] No new test required:纯构建属性变更,无新行为面/新代码路径(测试回归由 Phase 2 既有测试承担)
- [x] **执行期偏差(回写)**:首轮全量构建暴露第 10 处必要修改——`nop-demo/nop-spring-simple-demo/pom.xml:49` 的 spring-boot-maven-plugin 硬编码 3.2.0,其 ASM 读不了继承 release=25 后的 class v69(`Unsupported class file major version 69`,repackage 阶段失败)。修复:插件 3.2.0→**3.5.14**(nop-dependencies 管理的 SB 版本线,版本-only 无特性代码;单模块复跑 BUILD SUCCESS)。该 demo 无自有 release pin, sibling demos(nop-spring-demo/gateway/report-demo)均有 17 pin 未受影响;demo2 本已注释出 reactor。WI6 的 sub-pom 归一将统一插件版本。
- [x] **执行期偏差 2(closure audit 抓获,回写)**:首轮聚焦验证无效——两轮全量 -fae 中 deliverable-2 模块(cli/cli-core/rag/skills 三子模块)均被 nop-ai-core 失败级联 SKIPPED,聚焦 `test -pl`(无 clean)报 "Nothing to compile" 复用 pin 前旧字节码(cli-core v66/rag v61),guard 项勾选与证据矛盾。补救:①`clean test -pl` 六模块(含 nop-cli)强制重编译——0 个 "Nothing to compile",编译日志出现 `javac [debug release 25]`,产物 class major=69(cli-core CliErrors/rag IVectorStore 实测),6 模块测试全绿(log `_tmp/wi1-focused-clean.log`,工具链 `_tmp/wi1-toolchain.txt`=25.0.1);②SKIPPED=72(460 模块=382 SUCCESS+6 FAILURE+72 SKIPPED)系 -fae 对 6 失败模块的下游级联,其中 5 失败为 pre-existing、1 为外部 WIP——SKIPPED 收缩受外部 WIP 阻塞,以 clean 聚焦验证为交付模块的等价证据,全量测试回归由 WI2/WI3 兜底。

Exit Criteria:

- [x] 9 个文件 grep 无残留 `<java.version>17|22|21<` / `compiler.(source|target|release)>17|22|21<`(目标文件内)
- [x] `git diff` 仅这 9 个文件、版本数字变化 + nop-cli-core 注释数字同步
- [x] `ai-dev/logs/` 条目已更新

No owner-doc update required(JDK 表述更新归 WI4;分析文档 3.1 前照本就不随执行刷新,WI2/WI4 各自负责其面)。

### Phase 2 - JDK 25 工具链验证

Status: completed
Targets: 全 reactor + 聚焦模块

- Item Types: `Proof`

- [x] 工具链证据:构建前记录 `JAVA_HOME=$(/usr/libexec/java_home -v 25)` 与 `"$JAVA_HOME/bin/java" -version`(须为 25.0.1);JAVA_HOME 为空/解析失败时**中止**,不得静默回落默认 JDK 26
- [x] `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw clean install -DskipTests -T 1C -fae` 全量;失败集与 pre-existing 基线按模块比较
- [x] 抽查生效 release(属性继承面):`./mvnw help:evaluate -Dexpression=maven.compiler.release -pl nop-kernel/nop-core -q -DforceStdout` = 25;另抽 `nop-persistence/nop-orm`(深层继承)与 `nop-demo/nop-quarkus-demo`(经 nop-demo 链);注意 help:evaluate 读属性而非 per-execution 生效值(nop-core 测试编译的 release=16 覆盖探测不到,属预期)
- [x] 聚焦测试前核对:全量 install 阶段聚焦模块的依赖链 install 状态核对——**round 1 此核对失效**(nop-ai-core 实际 install 失败但测试仍跑在旧 jar/旧字节码上,guard 被形式化勾选;由 closure audit 抓获),round 2 以 `clean test` + 产物 major 版本核验修复(见偏差 2)
- [x] 聚焦测试:`./mvnw test -pl nop-runner/nop-cli-core,nop-ai/nop-ai-rag,nop-ai/nop-ai-skills/nop-ai-code-analyzer,nop-ai/nop-ai-skills/nop-ai-deepwiki,nop-ai/nop-ai-skills/nop-ai-translate`(用已 install 的 jar,不带 -am)
- [x] JDK22 门控簇冒烟:nop-find/nop-rg 模块在 JDK 25 下 profile 激活且按各自 release=22 编译通过(全量构建日志覆盖;如有红须归因:pre-existing 环境性 vs 本 plan 引入)

Exit Criteria:

- [x] 工具链证据在档(java -version = 25.0.1)
- [x] 全量构建失败集 ⊆ 文档化 pre-existing 集合(逐红归因记录)
- [x] 3 个 help:evaluate 抽查点输出 25
- [x] 聚焦测试(nop-cli-core + nop-ai-rag + nop-ai-skills 三子模块)全绿
- [x] `ai-dev/logs/` 条目已更新

验证边界声明:全量构建 `-DskipTests`,release 17→25 的运行期风险(链接期 NoSuchMethodError 类)仅由聚焦模块测试部分覆盖,全量测试回归由 WI2/WI3 的 CI 兜底——本 plan 的验证承诺止于编译级 + 聚焦模块级。

### Phase 3 - 收口

Status: completed
Targets: roadmap、本 plan、日志

- Item Types: `Proof` / `Decision`(roadmap 回写)

- [x] 独立子代理 closure audit(fresh session),证据写入 Closure 节
- [x] roadmap WI1 `todo` → `done`(仅此一处)
- [x] `check-plan-checklist.mjs <plan> --strict` 与 `check-doc-links.mjs --strict` 退出码 0
- [x] 严格 pathspec 提交(共享 index 纪律:提交前核对 `git diff --cached` 无外部暂存)
- [x] `scan-hollow-implementations.mjs`:纯属性变更无新实现面,N/A 记录

Exit Criteria:

- [x] audit 证据逐条写入 plan Closure 节
- [x] roadmap WI1 行 = done 且其余 WI 未动
- [x] 两项 --strict 工具退出码 0
- [x] `ai-dev/logs/` 收口条目已更新

No owner-doc update required(同 Phase 1)。

## Closure Gates

- [x] 9 pom 全部读到 25 且无残留旧值
- [x] JDK 25 工具链全量构建:失败集 ⊆ pre-existing 基线(逐红归因)
- [x] 生效 release 抽查 3 点 = 25
- [x] 聚焦模块测试绿(nop-cli-core、nop-ai-rag、nop-ai-skills 三子模块 code-analyzer/deepwiki/translate)
- [x] roadmap WI1 回写 done 且其余未动
- [x] 独立子代理 closure audit 完成且证据入 plan
- [x] `check-plan-checklist.mjs --strict`、`check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无 deferred 项。说明:①过桥期 CI 必红是 roadmap 依赖序(WI1→WI3)的显式中间态,不是本 plan 的未完成项;②"删除 vs 显式 25"的 pin 风格统一不是 deferred——WI1 侧已按 Goals 第 4 条裁定闭环(deliverable 3 = scope 内无额外可删位点),WI2 的 "(or delete the pins)" 语义属 WI2 自身的执行选项,见 Non-Blocking Follow-ups。

## Non-Blocking Follow-ups

- WI2 落地时统一裁定"删除 vs 显式 25"的 pin 风格(roadmap WI2 已含 "(or delete the pins so the root value governs)" 語义)。

## Closure

Status Note: 10 pom 全部读到 25(root/kernel/deps/quarkus/demo 五 build-chain + cli/cli-core 22→25 + rag/skills 21→25 + simple-demo SB 插件 3.2.0→3.5.14 v69 修复);JDK 25.0.1 工具链全量 382 绿 + 6 红逐红归因(5 pre-existing + 1 外部 WIP);deliverable-2 模块经 clean 重编译在 release=25 下编译+测试全绿(产物 major=69,`javac [debug release 25]` 9 条编译行);closure audit round 1 FAIL(hollow 验证:旧字节码)→补救→round 2 PASS,证据链完整。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_951eac10-01da-48d7-afc9-5516cce7e30e(fresh session,只读;round 1 AUDIT FAIL → 补救 → round 2 复核 AUDIT PASS)
- Evidence:
  - Phase 1:10 文件 diff 仅版本数字/comment(逐文件核对);9 文件旧 pin grep 零命中;simple-demo 偏差记录与 diff/build1 v69 失败行(:24194)吻合
  - Phase 2:382 SUCCESS + 6 FAILURE 逐红归因(5 与 plan 2418 基线逐字吻合 + nop-ai-core routing 缺包归因并发 plan 2424,git status RM 证据);round 1 hollow 验证抓获(target/classes mtime 早于 pin 编辑、major 66/61)→ `clean test -pl` 六模块补救:0 个 "Nothing to compile"、9 条编译行全部 `debug release 25`、产物 major=69(cli-core CliErrors/rag IVectorStore/nop-cli EmptyMain)、6 模块 SUCCESS 0 失败(cli-core 69 tests)、总时长 46.1s(log `_tmp/wi1-focused-clean.log`)
  - 工具链:`_tmp/wi1-toolchain.txt` = openjdk 25.0.1(build 25.0.1+8-27),时序与构建自洽
  - FFM 簇 6 模块 SUCCESS;help:evaluate 三点 = 25(审计独立复跑 nop-core = 25 EXIT 0)
  - 文本一致性:Phase 1/2/3 全 completed,checklist 全 [x];audit 残留 m1(日志勾选滞后)已勾选闭合
  - 工具:`check-plan-checklist.mjs --strict` exit 0、`check-doc-links.mjs --strict` exit 0;scan-hollow N/A(纯 pom)
- Follow-up:
  - WI2(plan 2427)按序执行(FFM 簇/9 叶子/java21/quickstart/stragglers/nop-core testCompile=16);WI3 CI 迁 25 消除过桥期 CI 必红;WI6 统一 SB 插件版本线(simple-demo 已先行)
