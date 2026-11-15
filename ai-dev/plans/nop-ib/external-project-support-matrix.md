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
| zeebe | 149 | yes | ? | yes | qualified-todo | in progress：install 遇 -T1C artifact 锁竞态，需串行 install（见 campaign Risks） |
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
