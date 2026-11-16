# nop-ib 外部项目资格矩阵（~/sources 全量枚举）

> Status: derived view — **派生视图，以各项目 daily log 条目为准**（`ai-dev/logs/{year}/{month}-{day}.md`）
> Last Reviewed: 2026-11-16
> Source: [external-project-support-campaign.md](external-project-support-campaign.md) Phase 2；机器化枚举脚本输出见 `_tmp/qualify-sources.json`（重建方式：对 `~/sources` 下全部含顶层 `pom.xml` 的目录枚举 pom 数 / junit5 检出 / jdk target / test 源存在性）
> 判定口径：`junit5` = 任意 pom 出现 junit-jupiter/junit-platform 依赖；`jdkTarget` = pom 中 compiler source/target/release 检出值（取首个命中，多模块仓库仅作量级参考）；`testSrc` = 任意模块存在 `src/test/java`

## 统计

| 分类 | 数量 |
|---|---|
| measured-parity | 7 |
| measured-preexisting | 2 |
| qualified-todo | 14（含 zeebe in progress） |
| excluded:junit4-only | 19 |
| excluded:no-test-sources | 2 |
| **合计** | **44**（`~/sources` 下全部含顶层 pom.xml 的项目，无遗漏） |

## 矩阵

| 项目 | poms | junit5 | jdkTarget | testSrc | 分类 | 备注 |
|---|---|---|---|---|---|---|
| OpenPDF | 8 | yes | 21 | yes | measured-parity | 2306/0，类集合 89/89 零漏；差 3=skipped 计数口径 |
| liteflow | 108 | yes | 17 | yes | measured-parity | 808/0 逐数精确 |
| pdfbox | 14 | yes | 11 | yes | measured-parity | 997/0 零差异 |
| spring-batch | 8 | yes | 17 | yes | measured-parity | 465/465 字节级全同 |
| Activiti | 68 | yes | 25 | yes | measured-parity | 1759/1（顺序敏感类，watch-only） |
| flink | 177 | yes | ? | yes | measured-parity | 已测范围 parity（wave1 472/0、runtime-web 88/0）；core/runtime/streaming-java/yarn 预存（SecurityManager/Thread.stop）；fs-tests 缺陷 20 已修（2026-11-16） |
| liquibase | 10 | yes | 17 | yes | measured-parity | 6494/0=6517−23skipped 精确同数 |
| syncope | 131 | yes | ? | yes | measured-preexisting | 真基线 355 errors（无 PG/Docker），两侧同红 |
| flowable-engine | 138 | yes | ? | yes | measured-preexisting | 内置 Groovy 不支持 class major 70，两侧同红 |
| zeebe | 149 | yes | ? | yes | measured-parity | gateway-grpc 462/0 精确（wave6 缺陷 29 已修：Enclosed 双跑 +18 归零，UnitIndex $-嵌套去重守卫） |
| BroadleafCommerce | 13 | yes | ? | yes | qualified-todo | |
| camunda-bpm-platform | 181 | yes | ? | yes | qualified-todo | |
| eclipse.jdt.ls | 102 | yes | 9 | yes | qualified-todo | jdkTarget=9：JDK26 javac 对 source 9 尚支持（8 已移除），资格门首验 |
| flink-agents | 36 | yes | 17 | yes | qualified-todo | Phase 3 优选候选（新框架混编风险） |
| jsr352 | 44 | yes | ? | yes | qualified-todo | |
| Juggle | 5 | yes | 21 | yes | qualified-todo | 小规模，可作快速验证面 |
| maven | 2039 | yes | 11 | yes | qualified-todo | apache maven 自身（pom 计数含大量子工件）；自举构建复杂度高 |
| mvnd | 79 | yes | 17 | yes | qualified-todo | |
| pentaho-kettle | 334 | yes | ? | yes | qualified-todo | 规模最大候选之一；框架混编风险高 |
| PowerJob | 23 | yes | 8 | yes | qualified-todo | **资格门首验**：JDK26 javac 是否仍接受 source/target 8 |
| seatunnel | 318 | yes | 8 | yes | qualified-todo | 同 PowerJob 资格门首验 |
| shardingsphere | 446 | yes | 21 | yes | qualified-todo | 规模最大候选之一 |
| spark | 52 | yes | 17 | yes | qualified-todo | Scala 混编（ib 仅 Java 面，需资格门确认边界） |
| Addax | 72 | no | 17 | no | excluded:junit4-only | owner 裁定：只承接 JUnit5/6 |
| bonita-platform | 3 | no | ? | yes | excluded:junit4-only | |
| esProc | 2 | no | ? | no | excluded:junit4-only | |
| fastjson | 1 | no | ? | yes | excluded:junit4-only | |
| flowlong | 9 | no | 17 | yes | excluded:junit4-only | gradle 混合（模型来源=effective-pom，仍以 Maven 为主，不额外排除） |
| fortress-json | 1 | no | 17 | yes | excluded:junit4-only | |
| jasperreports | 112 | no | ? | yes | excluded:junit4-only | |
| java-language-server | 2 | no | ? | yes | excluded:junit4-only | |
| jbpm | 83 | no | ? | yes | excluded:junit4-only | |
| jooq | 23 | no | 25 | no | excluded:junit4-only | |
| litemall | 7 | no | ? | yes | excluded:junit4-only | |
| magic-api | 18 | no | ? | no | excluded:junit4-only | |
| magic-script | 1 | no | ? | yes | excluded:junit4-only | |
| mall | 8 | no | 17 | yes | excluded:junit4-only | |
| shopizer | 6 | no | 11 | yes | excluded:junit4-only | |
| snail-job | 29 | no | 21 | yes | excluded:junit4-only | |
| tis | 25 | no | 17 | yes | excluded:junit4-only | |
| warm-flow | 33 | no | 8 | no | excluded:junit4-only | |
| x-easypdf | 4 | no | ? | yes | excluded:junit4-only | |
| data-platform-open | 6 | yes | 21 | no | excluded:no-test-sources | 全仓仅 src/main（机器判定 + 人工复核 src 树） |
| flyway | 49 | yes | 17 | no | excluded:no-test-sources | 检出无 src/test/java（测试在独立 CI 面），`mvn install -DskipTests` 为官方口径 |

## undecided 裁定

无 undecided 行。原候选疑问两项已裁定：
- `data-platform-open`：人工复核 `src/` 树确无测试源 → `excluded:no-test-sources`（非 blocked）。
- `flyway`：全仓 `find -type d -name test`（maxdepth 4）无命中，官方构建口径自带 `-DskipTests` → `excluded:no-test-sources`（非 blocked）。

## 与 daily log 一致性抽查（2026-11-16）

- liteflow 808/0：`ai-dev/logs/2026/11-15.md`（阶段总结表）一致。
- spring-batch 465/465 字节级：同上表一致。
- liquibase 6494/0=6517−23 skipped：同上表一致。

## 规则备忘

- 本矩阵行分类随实测推进更新；实测结论（命令/计数/差分/裁定）一律落在 daily log，本文件只做导航汇总。
- `excluded:junit4-only` 的 owner 裁定来源：campaign Non-Goals（"不为 JUnit4-only 项目做支持"）。
- jdkTarget 为机器启发值（多模块仓库取首个命中）；入实测前以资格门第一步（install）的实测为准。

## 实测 Runbook（Phase 4 固化，2026-11-16）

> 可被任一 agent 独立执行的标准流程。每步命令可直接复制；裁定分级见末节。

### 0. 资格判定

- 项目含顶层 `pom.xml` 且为 Maven 主构建（Gradle-only 不承接，模型来源=effective-pom）。
- JUnit5/6 主栈（junit-jupiter/junit-platform 检出）；JUnit4-only 项目**不承接**（owner 裁定）。
- 有测试源（`src/test/java`）；`excluded:no-test-sources` 直接登记。
- JDK26 兼容门：Lombok 旧版、`source/target 8`、SecurityManager 依赖等**遇到即登记预存，不调查**（见速查表）。

### 1. Install 前置（冷路径，串行）

```bash
cd ~/sources/<project>
mvn install -DskipTests -Daether.syncContext.named.time=3600 \
  -Dspotless.check.skip=true -Drevapi.skip=true -Denforcer.skip=true
```

**陷阱表（遇到过即按此处理）**：
- `Could not find artifact g:a:jar:tests` → 有模块依赖 tests-jar：**不能用 `-Dmaven.test.skip=true`**（不产 tests-jar），改 `mvn install -DskipTests` 重装（flink-agents/shardingsphere 均踩过）。
- `-T1C` 并行 install 触发 `Could not acquire shared/exclusive lock ... in 900 SECONDS` → 环境竞态：改串行 + `-Daether.syncContext.named.time=3600`（zeebe）。
- enforcer 锁 Maven 版本（flink 锁 3.8.6）→ 所有后续 mvn 命令加 `-Denforcer.skip=true`。
- 大仓库网络/空间瞬时不足 → 单独补装缺失 artifact（flink s3 test-jar/dist pom 先例）。

### 2. Model refresh

```bash
CP="<nop-ib-target-classes>:$(cat <nop-ib>/target/ib-cp.txt)"
java -Xmx6g -cp "$CP" io.nop.ib.NopIbMain model refresh
```

ib 通过 mvnw/mvnq/PATH mvn 三级调用（缺陷 24 修复）；本地仓支持 plain/cached/installed 分段布局（缺陷 25，zeebe branch-scoped-local-repository）。

### 3. mvn 基线（真 oracle）

```bash
mvn test -fae -Dmaven.test.failure.ignore=true -Dmaven.build.cache.skipCache=true \
  -Denforcer.skip=true -Dspotless.check.skip=true -Drevapi.skip=true \
  [-pl :moduleA,:moduleB]   # 大项目按波次选模块
```

- **硬约束：禁 build-cache**（Syncope 证伪缓存基线）。
- `-fae` + failure.ignore 拿全 reactor 各模块计数；failure.ignore 不改变 oracle（失败仍记录在日志/reports）。
- **权威计数=surefire-reports `TEST-*.xml` 的 testcase 元素数**（日志切片分析不可靠：文件含孤立 `\r`，sed/grep 与 python 行号会错位；只信单工具贯通或 reports）。

### 4. ib 执行

```bash
java -Xmx6g -cp "$CP" io.nop.ib.NopIbMain test -pl <modules>            # 主路径 junit-fork；-pl 只接受裸 artifactId（路径/`:id` 均拒绝）
java -Xmx6g -cp "$CP" io.nop.ib.NopIbMain test --runner maven -pl <m>   # 回退路径（lifecycle test 语义，缺陷 20 修复后=忠实 oracle）
```

- 大模块单 JVM 分档重跑可能超 1800s watchdog 或 fork abort（watch-only 残留，见 campaign Deferred）；此时**以回退路径结果为 parity oracle**（flink-agents-runtime 1209/270e/25s=standalone mvn 精确同数实证）。
- `--class` 单类探针用于失败定位；`nop.ib.debugPerClass=true` 输出逐类计数（诊断用）。

### 5. 类级差分口径

1. mvn 侧：surefire-reports 逐 XML testcase 数，按 `$` 前聚合到 top-level。
2. ib 侧：`nop.ib.debugPerClass=true` 重跑取逐类计数。
3. 对比三类差异：UNDER（ib<mvn：新缺陷,修复）、OVER（ib>mvn：检查 excludes/excludedGroups/派发面,缺陷 30 先例）、IB-ONLY（0-test helper,登记即可）。
4. skipped 计数口径：ib 不把 assume/skip 计入 run 数（OpenPDF 先例），对比用"执行数=tests−skipped"。

### 6. 裁定分级

- `parity`：执行面逐数精确（含已知 skipped 口径注记）。
- `预存`：mvn/ib 同红 + 根因明确（速查表命中或两侧同红证据）。
- `新增缺陷已修`：本项目内修复+回归钉+复验，不留待修残留。
- 未达以上任一 → watch-only 登记进 campaign Deferred（必须带 Why Not Blocking）。

### 预存环境失败速查表（遇到即登记，不调查）

| 症状 | 根因 | 实例 |
|---|---|---|
| VM init Error / `java.security.manager` | JDK26 移除 SecurityManager，项目 profile 注入 allow | flink core/runtime/streaming-java/yarn |
| `Unsupported class file major version 70` | 内置 Groovy 过旧 | flowable-engine |
| `Thread.stop()` 编译失败 | JDK20+ 移除 | flink-runtime 测试源 |
| 无 PostgreSQL/Docker/云模拟器 | 环境缺依赖，两侧同红 | syncope、zeebe backup-stores、spring-batch Mongo 集成测试 |
| Lombok `找不到符号: 变量 log` | Lombok 版本不支持 JDK26 | BroadleafCommerce |
| javac `source/target 8` 拒绝 | JDK26 移除 source 8 | seatunnel/PowerJob（资格门首验） |
| test 阶段绑定 provision/容器（wildfly-maven-plugin、testcontainers compose） | 基线即 FAILURE，两侧同不执行 | jsr352 test-deployment |

### fork 失败根因定位（试跑缺口补齐，jsr352 先例）

- junit-fork 不产 surefire-reports 等价物，`debugPerClass` 只给计数与摘要——失败根因需复现探针：用与 fork 相同顺序的 classpath（test-classes → classes → deps）直接反射实例化/执行失败类。
- 环境类失败先查 surefire `<environmentVariables>`（缺陷 31 已透传）与 `<systemPropertyVariables>`；再对比 `--runner maven` 回退结果定位"是否 fork 装配差异"。

### 框架惯例 → 索引标记对照表（族 C）

| 惯例 | 索引/派发标记 | 回归钉 |
|---|---|---|
| Jupiter 注解（@Test/@ParameterizedTest） | UnitIndexer 注解标记 | TestUnitIndexer |
| Spock 2.x（`$spock_feature_<n>_<m>` 方法，无注解） | 方法名标记 | （liquibase 缺陷 21，见 UnitIndexer） |
| JUnit3 `static TestSuite suite()` | 方法名标记 | （liquibase 缺陷 22） |
| 继承外部基类（test-jar 基类） | 缺陷 18 边界：具体类超类出界即派发 | UnitIndex.testClasses javadoc |
| `@Nested`-only 顶层容器 | 缺陷 28 提升（ownsDispatchableNested） | TestNestedOnlyContainerPromotion |
| JUnit4 类（junit:junit 在 classpath） | 缺陷 27a vintage 组装 | TestExcludedTagFilter（语义）；E2E=zeebe-gateway-grpc 462 |
| surefire `<excludes>`（failsafe 族） | 缺陷 30 排除 | TestSurefireClassFilterExcludes |
| `<excludedGroups>` 标签排除 | 缺陷 27b TagFilter 透传 | TestEffectivePomParser.testSurefireExcludedGroupsParsed |
| surefire `<includes>` 覆盖默认 | 缺陷 14 过滤 | SurefireClassFilter javadoc |
