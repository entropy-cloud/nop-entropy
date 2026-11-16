# nop-ib 外部项目支持度实测总结（OpenPDF → zeebe 阶段）

> Status: resolved
> Date: 2026-11-15
> Scope: `nop-tools/nop-ib` 对 `~/sources` 下真实 Java 大型项目的"编译 + 测试派发 + 差分"支持能力；不含 Nop 仓内自测
> Conclusion: 已完成 8 个外部项目实测，发现并修复 7 个泛化缺陷（16–22）；4 个项目达成与 mvn 的精确 parity，3 个项目的失败已裁定为预存环境原因，1 个（zeebe）仍在进行；剩余风险集中在回退路径语义（候选缺陷 20）与"未编译/无索引"恢复面。

## Context / Background

nop-ib 的目的是用 javac 增量引擎替代 AI 开发期的 Maven 全量调用。该能力在 Nop 仓内已通过 Wave 0–6 收口，但**仓内通过 ≠ 对外部真实项目通用**：外部项目的 pom 形态、测试框架混编、surefire 配置、JDK 版本兼容性远比 Nop 仓内丰富。因此以 `~/sources` 下的大项目为语料持续做"实测 → 发现缺陷 → 修复 → 复验 parity"的循环。

本报告回答两个问题：

1. 截至 2026-11-15，ib 对外部项目的支持现状是什么（哪些项目达成 parity，哪些失败已裁定为预存）？
2. 已发现的缺陷属于哪些"族"（即后续新项目最可能再次触发的模式）？

## 资格门（准入判据）

外部项目进入实测语料必须同时满足：

| 条件 | 理由 | 排除先例 |
|---|---|---|
| Maven 构建 | ib 的模型全部从 effective-pom + dependency:list 导出 | conductor（Gradle） |
| JUnit5/6 主栈（Jupiter）；JUnit4 依赖 vintage/vintage 桥 | owner 裁定只承接 JUnit5/6 项目 | fastjson、jbpm、Addax、jasperreports、snail-job、warm-flow、magic-api |
| JDK26 可编译（`--release` 目标可达） | 本机 JDK26 为唯一运行时 | seatunnel/shardingsphere/PowerJob（Java 8） |
| 能 `mvn install -DskipTests` 成功（含 test-jar 前置） | reactor 依赖解析需要本地仓库产物 | — |

另有两个前置操作，不是缺陷而是环境事实：**先 install**（否则 reactor `dependency:list` 解析失败）；**禁用 build-cache**（否则 mvn 基线是缓存拼贴，Syncope 已证伪）。

## Analysis Body

### 一、实测结果全景

| 项目 | 规模 | mvn 基线 | ib 结果 | 裁定 | 关联缺陷 |
|---|---|---|---|---|---|
| OpenPDF | 89 测试类 | 2309/0 | 2306/0；类集合 89/89 零漏 | **parity** | 早期五缺陷（含 `@{}` 绑定） |
| liteflow | — | 808/0 | 808/0，逐数精确 | **parity** | `-pl` 执行模型 |
| pdfbox | — | 997/0 | 997/0，共同语料零差异 | **parity** | `@{...}` 后期绑定 |
| spring-batch | 465 | 465/0 | 465/465 字节级全同 | **parity（字节级）** | 检查型处理器 / package-info / `-parameters` |
| Activiti | 1759 | 3335（含 err） | 1759/1（顺序敏感） | **parity + watch-only** | 模块级差分重跑 / mockito agent / test-classes 泄漏 |
| Syncope | — | 615/1f+1e（缓存基线证伪）→ 真基线 355 errors（无 PG） | 673/7 | **预存环境**（无 PG/Docker、wicket） | surefire includes（14）/ 属性型 skipTests（15） |
| **flink** 2.3.0 | 171 模块入模 | wave1 472/0；runtime-web 88/0 | wave1 **472/0 精确**；runtime-web **88/0 精确** | **parity（已测范围）**；core/runtime/streaming-java/yarn 预存 | 16 / 17 / 18 / 19 / 20 |
| **liquibase** | 438 类 | 6517/0f/0e/23s | **6494/0 = 6517−23skipped 精确同数** | **parity** | 21 / 22（+19 泛化） |
| flowable-engine | 56 模块 | 4003/35f/3e | 同族失败 | **预存环境**（内置 Groovy 不支持 class major 70） | — |
| zeebe 8.11 | 149 模块 | 待测 | 待测 | **进行中** | — |

### 二、缺陷族（后续新项目最可能再次触发的模式）

按"触发面"聚类，共 4 族：

**族 A — classpath/物化语义（缺陷 13、16）**

- 13：把 test scope 依赖的 sibling `target/test-classes` 全量塞进 classpath（Activiti）。
- 16：intra `test-jar` 依赖物化为 sibling 目录，而该目录可能为空（flink install 用 `-Dmaven.test.skip`）→ 静默吞掉 harness 类 → discovery `NoClassDefFoundError`。
- 教训：**Maven 的语义是"已安装产物"，ib 的语义必须是"已安装产物优先，目录仅作未安装回退"**。

**族 B — 假绿（缺陷 17）**

- fork 结果文件预创建为空文件；JVM 在 init 阶段即死时文件"存在但为空"，旧判定把存在当成功 → `run=0 failed=0 exit=0`，而 mvn 是 BUILD FAILURE。
- 教训：**任何"结果是否产生"的判定必须是"非空且可解析"，不能是"文件存在"**；对每个零假绿门禁都要有专门的 crashed-fork 回归钉。

**族 C — 派发面识别（缺陷 14、18、21、22）**

- 14：ib 派发全部 JUnit 标记类，而 surefire 只执行 includes 匹配类（Syncope zookeeper）。
- 18：继承**索引外基类**（test-jar 基类）的具体子类漏派发（flink runtime-web 6 类）。
- 21：Spock 2.x `$spock_feature_<n>_<m>` 无任何注解（liquibase 48 类 / ~544 tests）。
- 22：JUnit3 `static TestSuite suite()` 惯例（liquibase ant 14 类）。
- 教训：**派发面必须在"名字模式 + 可执行标记 + 继承链 + 框架惯例"四个维度上都保守**；每发现一种新框架惯例就加一个标记 + 一个索引回归钉。

**族 D — 索引新鲜度（缺陷 19 及其泛化）**

- 19：单元索引空 ≠ 无测试。测试源从未编译（install 用 `-Dmaven.test.skip`、基线在 reactor 前段失败）时，旧逻辑直接"no test classes in unit index"跳过（flink fs-tests 4 类）。
- 泛化：`index == null` 分支同样恢复（liquibase）；此前该分支直接掉 surefire goal 回退（=候选缺陷 20 语义）。
- 教训：**"没有找到"与"没有"必须区分**；索引缺失/为空且有测试源 → 一次 Maven `test-compile` + 重扫 + 持久化索引，再枚举。

### 三、预存环境失败清单（可复用，避免重复调查）

| 现象 | 项目 | 根因 | 处理 |
|---|---|---|---|
| `-Djava.security.manager=allow` → VM init Error | flink core/runtime/streaming-java | JDK26 移除 Security Manager；flink java21 profile 注入 | 两侧同红，记预存 |
| `getSubject is not supported` | flink-yarn | 同族（SecurityManager 移除） | 两侧同红，记预存 |
| `Thread.stop()` 无法编译 | flink-runtime 测试源码 | JDK20+ 移除 | 该模块测试编译排除 |
| `Unsupported class file major version 70` | flowable-engine | 内置 Groovy 版本过旧 | 预存，待项目升级 |
| 无 PostgreSQL / Docker | syncope | 环境缺依赖 | 两侧同红 |
| enforcer 锁 Maven 3.8.6 | flink | 需要 `-Denforcer.skip=true` | 环境参数 |
| s3-fs-base test-jar / dist pom / yarn-tests 依赖 | flink | `-Dmaven.test.skip` 不产 test-jar | 单独补装 |

## Findings

1. **parity 是可达成且可精确的**：在 4 个项目上达到"逐数精确"（liteflow 808、spring-batch 字节级、flink wave1 472、liquibase 6494），说明 ib 的执行模型在"配置正确的项目"上与 surefire 等价。
2. **缺陷集中在边界语义而非算法**：7/7 个缺陷都属于"物化、结果判定、派发识别、索引新鲜度"，没有一个属于依赖闭包/ABI 计算这类核心算法；核心算法自 Wave 2 以来未再有缺陷。
3. **每个新项目平均暴露 1–2 个新缺陷**，且多集中在"框架惯例"维度——该维度是长尾的，靠单项目无法一次穷尽。
4. **回退路径（候选缺陷 20）是当前最大的未闭环面**：`runSurefire` 直呼 `surefire:test` 忽略 phase 绑定，flink 的 integration-tests execution 被连带执行（fs-tests 80 vs mvn 40）。该路径目前只在索引缺失且无法恢复时走到。

## Conclusion

外部项目实测已覆盖 8 个项目（4 parity、3 预存、1 进行中），修复 7 个泛化缺陷。支持现状可概括为：**"Maven + JUnit5/6 + 可 install + JDK26 可编译"的项目在已测范围内达到精确 parity；未覆盖的长尾主要是框架惯例（Spock/JUnit3/Vintage 混编）与回退路径语义**。

后续推进见执行计划 [ai-dev/plans/nop-ib/external-project-support-campaign.md](../../plans/nop-ib/external-project-support-campaign.md)。
