# 审计 6A-2: surefire 模型事实完备性（EffectivePomParser vs surefire 运行时消费面）

> Status: open
> Date: 2026-11-16
> Scope: nop-tools/nop-ib — EffectivePomParser / IbModule / JUnitForkExecutor / JUnitWorkerPool / SurefireClassFilter / TestCommand
> Conclusion: 已解析 8 个 surefire 维度；对比 surefire 真实消费面还有 11 个维度未解析。2 个 Fix-candidate（groups 正向过滤、additionalClasspathElements/Dependencies 类路径缺口）具有确定性正确性影响；其余 9 个为 fallback-rule 或 documented-nonequivalence。

## 1. 方法

- 完整读取 `EffectivePomParser.java`（349 行），逐字段枚举 `<plugin artifactId=maven-surefire-plugin>` 分支的解析面。
- 完整读取 `JUnitForkExecutor.java`、`SurefireClassFilter.java`，抽查 `JUnitWorkerPool.java` / `TestCommand.java` / `IbJUnitRunner.java` 中 argLine、classpath、系统属性、环境变量、并行度的实际组装点。
- `git log --oneline -30 -- nop-tools/nop-ib` 复盘缺陷族 23–31 及更早的 argLine/占位符缺陷，标注哪些缺口已实际咬人。

## 2. 今天已解析的 surefire/compiler 事实（EffectivePomParser）

| # | 模型字段 | 解析来源 | 消费点 |
|---|---|---|---|
| 1 | `surefireArgLine` | `<configuration><argLine>` | `JUnitForkExecutor.execute` 逐 token 注入 fork 命令行；`@{...}` 丢弃、`${g:a:type}` 类路径解析（缺陷 4/7）、其余 `${...}` 不可解析即丢弃 |
| 2 | `surefireSkipTests` | `<skipTests>` / `<skip>` + 项目级 `<properties>` 兜底（缺陷 15） | `TestCommand` 模型事实跳过整模块 |
| 3 | `surefireIncludes` | `<includes><include>` | `SurefireClassFilter` 派发面（缺陷 14） |
| 4 | `surefireExcludes` | `<excludes><exclude>` | `SurefireClassFilter`（缺陷 30） |
| 5 | `surefireExcludedGroups` | `<excludedGroups>` | fork `-Dnop.ib.excludeTags` → runner TagFilter（缺陷 27） |
| 6 | `surefireSystemProperties` | `<systemPropertyVariables>`（仅扁平子元素形式） | fork `-Dk=v`（`JUnitForkExecutor:117`，worker 路径 `JUnitWorkerPool:486` 同） |
| 7 | `surefireEnvironmentVariables` | `<environmentVariables>`（含 key/value 与扁平形式） | `ProcessBuilder.environment()` 注入（缺陷 31） |
| 8 | compiler 侧 | `release/proc/annotationProcessorPaths/parameters/source/target` | javac 快路径（非本审计主对象） |

## 3. 未解析维度裁定表

| 维度 | 已解析? | mvn 行为 | ib 未解析时的行为 | 分歧风险 | 裁定 |
|---|---|---|---|---|---|
| **groups**（正向 tag 过滤） | 否 | 仅执行匹配 `<groups>` tag 表达式的测试 | ib 派发全量匹配类，无 tag 过滤 → **over-dispatch**：mvn 排除在外的慢/不稳定/外部依赖测试会被执行且结果计入 | **高**（确定性超集；zeebe 缺陷 27 已证明 tag 过滤可翻转 56→462 类的结果面，方向相反的那一半还没做） | **Fix-candidate**（与 excludedGroups 对称：fork 传 `-Dnop.ib.includeTags`，runner 已有 TagFilter 基建） |
| **additionalClasspathElements** | 否 | 向 test classpath 追加任意目录/jar | fork classpath 只含物化依赖 → 测试找不到资源/辅助类，`ClassNotFoundError` 或静默 resource 缺失假红 | **高**（声明即必需，缺了必分歧） | **Fix-candidate**（解析 `<additionalClasspathElements>` 追加到 cp；mvn 语义为路径前置，追加通常等价，严格起见按 mvn 顺序插前部） |
| **additionalClasspathDependencies** | 否 | 将坐标解析为构件插入 test classpath | 同上，且需要走依赖解析器，比路径版成本高 | **中高**（使用频率低于路径版，但一旦出现同样必分歧） | **Fix-candidate**（可分期：先检测到即告警 + 回退 surefire 通道，再补解析） |
| **forkCount / reuseForks** | 否 | fork 数与 JVM 复用（reuseForks=true 时同类/跨类共享 JVM，静态状态跨类泄漏） | ib 恒为 per-class 独立 fork，隔离严格强于 mvn → mvn 里靠泄漏状态"通过"的测试在 ib 可能红；反向（ib 污染）不存在 | 中（结果面可分歧，方向单向：ib 更干净） | **documented-nonequivalence**（写进 parity 文档：分歧优先怀疑 mvn 侧状态泄漏；无需解析） |
| **parallel / threadCount** | 否（仅注释提及） | surefire 并发方案 concurrent/pools，类内/类间并发交错 | ib fork 内单线程顺序执行、跨 fork 并行由 `nop.ib.forkParallelism` 控制，与 pom 声明无关 → 依赖交错时序的 flaky 测试结论可能与 mvn 基线不同 | 中（本质是 flaky 复现面差异，非确定性缺口） | **fallback-rule**（不解析；失败集对拍时对"mvn 绿/ib 红"的并发敏感类标注 `parallel-divergence` 观察，不作为 parity 失败归因 ib） |
| **runOrder** | 否 | 测试类执行顺序（默认 filesystem/alphabetical） | per-class fork + 类间无共享状态 → 顺序只影响日志呈现与 (reuseForks 泄漏) 场景 | 低 | **documented-nonequivalence** |
| **workingDirectory** | 否（硬编码 `cwd=basedir`） | 默认 `${basedir}`；可配置为其他目录 | 声明非默认目录的模块中按相对路径读文件的测试会找不到文件 | 低中（配置极罕见；但一旦配置必分歧） | **fallback-rule**（解析该字段，非 `${basedir}` 即记 warning + 该模块强制走 surefire 回退；不值得在 fork 引擎内实现任意 cwd 语义） |
| **systemPropertyVariables 中的 `${...}`** | 部分（值按 effective-pom 原样入库） | surefire 配置在 Maven 插件化阶段完成插值；effective-pom dump 已是插值后产物，故 pom 属性引用已解析 | ib 收到的即 mvn 插值后值 → 与 mvn 一致；真正未解析的（来自 -D 用户属性）两边同为字面量，parity 成立。**例外**：`<systemPropertyVariables>` 内 `<property><name/><value/></property>` 结构化形式被 `parseSurefire` 显式跳过（`EffectivePomParser:285-288`），属性静默丢失 | 低中（结构化形式罕见但丢失是静默的） | **Fix-candidate**（低severity：在 systemPropertyVariables 分支补 `<property>` 形式解析，2 行级改动；顺带在值含 `${` 时打 warning 提示人工核对） |
| **profile 条件激活** | N/A | `<profiles>` 由 Maven 激活后合并进 effective-pom | effective-pom dump 本身就是激活后的模型 → 解析天然正确。**残余风险**：模型哈希不包含"激活集"，同一工作树用 `-Pxxx` 跑 mvn 基线、用默认集跑 ib 时事实面不同 | 低（使用纪律问题而非解析缺口） | **documented-nonequivalence**（文档写明：ib 模型对应"无额外 -P 的 effective-pom"；带 profile 的对照实验需同 profile 重建模型） |
| **failIfNoSpecifiedTests** | 否（回退路径硬编码 `-DfailIfNoSpecifiedTests=false`，`TestCommand:778`） | surefire 按定制的 `-Dtest` 选集为空时决定失败与否 | ib fork 路径不做 -Dtest 选择，派发面由索引+过滤器决定，此开关无对应语义；回退路径已显式放宽避免假失败 | 低 | **documented-nonequivalence**（现状行为正确且有意） |
| **reportFormat / reportNameSuffix 等报告系** | 否 | 只影响 surefire 输出报告形态 | ib 自有 JSON 汇总；不影响测试通过/失败判定 | 无（纯表现层） | **documented-nonequivalence** |
| **skip（maven.test.skip 语义）** | 部分 | `maven.test.skip=true` 连编译带执行全跳；surefire `<skip>` 已解析 | ib 只读 surefire 配置与 `skipTests` 属性，不读 `maven.test.skip` 属性 → 属性声明而配置未内联时（缺陷 15 同形）可能多执行 | 低中（与缺陷 15 同构，已有兜底模式可复制） | **fallback-rule**（把 `<properties>` 兜底从 `skipTests/skip` 扩展到 `maven.test.skip`，模型侧一行改动） |

## 4. 缺陷族 23–31 对照（git log 证据）

- 缺陷 14/15（includes、属性型 skipTests）、27（excludedGroups/vintage）、30（excludes）、31（environmentVariables）、4/7（argLine `@{}`/`${}` 占位符）：**每一个都是"某 surefire 维度未入模型 → 实测语料上翻车 → 事后补解析"的同一条轨迹**。本审计据此把剩余未解析维度按"是否确定性改变执行面/类路径"分级：确定性影响（groups、additionalClasspath*）必须前置修，其余按观测驱动补。
- 缺陷 23/24/25（test-jar 丢弃、wrapper 回退、本地仓布局）属依赖解析而非 surefire 配置面，不在此表。

## 5. 处置汇总（Fix-candidate 清单，按 severity）

1. **[High] `groups` 正向 tag 过滤**：`parseSurefire` 加 `<groups>`/`<test>`？只需 `<groups>` 文本；fork/worker 传 `-Dnop.ib.includeTags`，`IbJUnitRunner` TagFilter 扩展 include 分支。风险证据：缺陷 27 的镜像场景。
2. **[High] `additionalClasspathElements`**：解析并插入 fork classpath（含 worker 路径 `JUnitWorkerPool` 组装点）。
3. **[Medium] `additionalClasspathDependencies`**：首期"检测到即 warning + surefire 回退"，次期接依赖解析。
4. **[Low-Medium] `<systemPropertyVariables><property>` 结构化形式静默丢弃**（`EffectivePomParser:285-288`）：补解析；这是当前解析代码里唯一的"读到了却不收"的缺口。
5. **[Low] `workingDirectory` 非默认即回退 surefire**；`maven.test.skip` 属性并入 skip 兜底 — 两项为防御性小改。

documented-nonequivalence（不改代码，落 `docs-for-ai/02-core-guides/incremental-build.md` 或 runbook）：forkCount/reuseForks、parallel/threadCount、runOrder、profile 激活纪律、failIfNoSpecifiedTests、reportFormat。

## 6. 边界与未覆盖项

- `includes/excludes` 模式匹配采用 basename 近似（`SurefireClassFilter.matchesAnyPattern`），`**/pkg/**/Name.java` 中间路径段语义未完整复刻 — 未在实测中咬人，登记为 watch-only。
- surefire `runOrder` 的 `hourly/random` 等只影响顺序，随 forkCount 裁定一并文档化。
- 未审计 JUnit5 `<configurationParameters>`（junit-platform.properties 注入）— surefire 3.x 支持，当前 ib 无对应机制；本语料未见使用，登记 follow-up。
