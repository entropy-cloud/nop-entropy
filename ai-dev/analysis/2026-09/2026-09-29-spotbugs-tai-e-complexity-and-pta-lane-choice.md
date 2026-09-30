# SpotBugs 实现复杂度评估与指针分析通道选型（Tai-e 复用 / 参考重做 / 直接使用）

> Status: resolved（2026-09-30——Wave 5 倾向已被 plan 09 spike 实测消费并落终裁，见文末「Postscript」）
> Date: 2026-09-29
> Scope: nop-bytecode-analysis roadmap（Wave 0 底座裁定、Wave 5 跨过程扩展）的输入调研
> Conclusion: 尚未裁定（归 roadmap Wave 0 item 2 / Wave 5 item 10）。当前倾向：Wave 2/3 维持 ASM 自研 + 参考 SpotBugs 分析形态；Wave 5 指针分析倾向**直接使用 Tai-e 作为外部工具**（maven central 构件、插件系统承接入口点适配），否决"从零参考重做"。许可证约束：两框架均为 LGPL，**作为外部依赖/工具使用不阻塞**，**拷贝/移植源码进本仓被阻塞**（本仓 Apache-2.0）。

## Context

- owner 指令（2026-09-29）新增 [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) 后，提出后续要做**更复杂的指针分析**，要求下载 SpotBugs 与 Tai-e 源码实测其实现复杂度，并回答：复用、参考重做、还是直接使用。
- 两仓库已克隆至本机 ~/sources/lint/ 目录（spotbugs/ 与 Tai-e/，均为 master 浅克隆，2026-09-29）。以下全部数字为当日实测（find + wc）。
- 约束：本仓许可证 Apache-2.0（root pom `<license>`）；字节码专项 roadmap Hard constraint 1（纯增量，不动既有内容）；平台 self-contained 原则（ai-dev/design/self-contained-design.md）。

## 硬事实盘点

| 维度 | SpotBugs | Tai-e |
|---|---|---|
| 许可证 | LGPL-2.1 | GPL-3.0 + COPYING.LESSER（即 LGPL-3.0） |
| 主代码 | 1029 文件 / 202,880 行 | 1310 文件 / 120,698 行 |
| 测试 | 329 测试文件 + spotbugsTestCases 专用语料仓 | 552 文件 / 30,572 行 |
| 语言要求 | sourceCompatibility 11 | Java 17（badge + build.gradle.kts） |
| 分发 | Gradle 自建；Maven central 有 org.spotbugs 系构件 | maven central：net.pascal-lab/tai-e，v0.5.4（pre-1.0） |
| 成熟度 | FindBugs 血统（~2003 起），生产级 | 研究级但高活跃：PLDI'25 / OOPSLA'25 / ISSTA'25 ×2 / ICSE'25 ×2（含 Best Artifact、Distinguished Paper） |
| 分析模型 | 方法内 CFG 数据流 + interproc 属性数据库（预生成 .db 文件） | 全程序（-acp app jar + -cp 依赖 + JRE 建模 -java/-pp/useCurrentJRE）指针分析，on-the-fly 调用图 |

## Analysis

### 1. SpotBugs 到底有多复杂——复杂度归因

主模块 202.9k 行的结构分布（edu.umd.cs.findbugs 内）：

- **框架核其实不复杂**：ba/ 27.0k 行 = per-method 显式 CFG（CFG.java 644，含异常边）+ 迭代数据流引擎（Dataflow.java 579，前向/后向）+ Frame 抽象值。结构清晰，是教科书形态。
- **复杂度集中在三处**：
  1. **javac 语义编码**：AbstractFrameModelingVisitor 1280 行 + DismantleBytecode 1206 行——把每条字节码指令对抽象状态的效果逐一编码，这是"字节码层开发门槛"的本体；
  2. **npe 子系统状态组合**：ba/npe/ 27 个类 5,516 行（IsNullValueAnalysis 980 + IsNullValue 601 值格 + NullDerefAndRedundantComparisonFinder 902），实际检测器 FindNullDeref 再 1,924 行——nullness 抽象域的边角组合是它难出正确结果的原因；
  3. **detector 长尾**：detect/ 224 文件 64,350 行（~160+ bug pattern），加上 type/ 4.3k + jsr305/ 5.3k 类型限定符框架、预生成 interproc 数据库——典型的 20 年工程沉积。
- **对我们最有价值的参照恰恰很小**：资源泄漏机制本体 = ObligationAnalysis 476（obl/ 2,119）+ ResourceValueAnalysis 238 + ResourceValueFrame 119 + ResourceTracker 接口 138 + FindOpenStream 550 ≈ **1.5k 行**。这就是 Wave 3 acquire/release 配对要参考的形态：抽象 Frame 沿 CFG 传播，acquire/释放点做状态迁移，异常/return 路径聚合。
- 结论：**"参考 SpotBugs"的合理粒度是两个分析形态**（IsNullValueAnalysis 的 nullness 帧 ≈ Wave 2 null-flow；ResourceValueAnalysis ≈ Wave 3 资源配对），各约 1–1.5k 行的算法形态，不是它的 detector 库也不是它的 20 年沉积。注意清洁室纪律：读懂后自己实现（依赖 BSD 许可的 ASM 不受影响）；**逐行移植 LGPL 源码进本仓会让被移植文件变 LGPL**。

### 2. 许可证对三个选项的实际约束

本仓 Apache-2.0，SpotBugs LGPL-2.1，Tai-e LGPL-3.0：

- **作为外部依赖 / CI 工具使用：不受阻**。LGPL 允许 Apache-2.0 代码以"独立可替换的库"方式链接（保留许可声明 + 允许用户替换该库）；更关键的是，字节码专项是 **CI/构建期工具，不随 nop-entropy 制品分发**——CI 里跑一个 LGPL 乃至 GPL 的分析工具，对发布的 Apache-2.0 制品零污染（Sonar/SpotBugs 现状同理）。
- **拷贝 / 移植源码进本仓：受阻**。移植即衍生作品，LGPL-3.0 与保持 Apache-2.0 身份冲突。"参考重做"必须停留在算法/架构级学习（论文 + 文档 + 读码理解），不得行级翻译。
- 这一条直接把选项空间切成两半：Tai-e 只能"整库用"或"不碰"，没有"搬一块进来"的中间态。

### 3. 指针分析通道三选项对比

**Option A：直接使用 Tai-e（依赖 net.pascal-lab/tai-e 或外部进程调用）**

- 优点：
  - PTA 正是它的中心能力：analysis/pta 29.1k 行（DefaultSolver 927 行 Andersen on-the-fly 核 + cs/ 上下文选择器族：insensitive / k-obj / k-type / k-call / selective / guided）；
  - 我们要的下游面现成：调用图（on-the-fly）、**taint 分析 3,152 行**（正是统一账本 Sonar 行 not-replaceable 的跨过程 taint 面）、reflection 2,339 行、invokedynamic/lambda 建模、native 方法模型；
  - 插件系统（Plugin SPI + EntryPointHandler）就是为"框架型代码库入口点适配"准备的扩展点；
  - 文档完备（docs/en：pointer-analysis-framework / taint-analysis / develop-new-analysis），NJU 课程 assignments 即学习路径；
  - 前端是它 2025 年刚重写的（OOPSLA'25），对大代码库的性能有论文背书（ISSTA'25 微服务全程序 PTA）。
- 缺点 / 风险：
  - **pre-1.0（v0.5.4）**：API 跨版本 churn，须 pin 版本 + 隔离适配层；
  - 本仓是框架不是应用：**无 main 入口**，入口点 = 注解驱动的 bean/BizModel/XService 面——Tai-e 不知道 Nop 语义，入口点发现与 Nop 语义建模是集成工作的主体（但这是"写配置 + 写插件"，不是"写指针分析"）；
  - 全程序 scope 对 100 模块 monorepo 的性能/内存需 spike 实测；
  - Spring DI 插件对 Nop 无直接用处（Nop 用自有 IoC），该面的等价物要自己以插件形态补。
- 适用场景：Wave 5 跨过程面（调用图 / 参数 nullness 契约 / taint）。

**Option B：参考 Tai-e 重做**

- 算法核有欺骗性的小（Andersen 求解器 ~1k 行，Tai-e ISSTA'23 论文的核心卖点就是"好设计让它变薄"），但工程尾巨大：反射/lambda/native/JRE 建模、上下文敏感变体族、堆抽象调优、外加 Nop 语义面——Tai-e 用 6 年研究做到 29k 行 pta + 12k 行前端，重做即多年量级。
- 且重做只对"拥有 PTA 引擎本身"有战略价值；本专项的 mandate 是**产出缺陷发现流**，不是研究分析器引擎。
- 结论：**否决为默认路径**；仅当 Option A 实测（精度 / 规模 / 维护）不达标时再议，且届时也应先评估"fork Tai-e 精简"而非从零造。

**Option C：复用 SpotBugs 引擎做指针分析**

- 直接否决：SpotBugs **没有指针分析**——它是方法内数据流 + 预生成 interproc 属性数据库（jdkBaseNonnullReturn.db 等），没有调用图构建、没有 points-to。它的 keep-tool 价值在 qa profile 现有接线，与指针分析通道无关。

### 4. 与字节码专项 roadmap 波次的对接

| 波次 | 本分析的影响 |
|---|---|
| Wave 0 item 2（底座裁定） | 不变：A（ASM 自研）/ B（SpotBugs plugin）/ C（SootUp）三候选照裁；SpotBugs 复杂度归因给 A 的成本侧提供了量化参照（两个目标分析形态各 ~1.5k 行 + AbstractFrameModelingVisitor 形态的语义编码层） |
| Wave 2/3（null-flow / 资源配对 v1） | 参照物钉死：IsNullValueAnalysis 帧 / ResourceValueAnalysis 帧（各 ~1.5k 行算法形态，清洁室重实现） |
| Wave 5 item 10（调用图 spike） | **候选应具体化为 Tai-e 优先、SootUp 作对比项**（本分析的 Option A）；入口点适配（Nop bean/BizModel 注解面 → Tai-e EntryPointHandler/Plugin）是 spike 的主要工作量；taint（item 11）直接复用其 3.2k 行 taint 插件验证 |

## Conclusion

- 未裁定（Status: open，归 roadmap Wave 0 / Wave 5 的 plan 流程）。（时态注记 2026-09-30：本节为 2026-09-29 原文快照；Wave 5 已由 plan 09 结案——见 header Status 与文末 Postscript）
- 当前倾向：**Wave 5 指针分析直接使用 Tai-e**（外部工具/依赖形态，pin 版本 + 适配插件），**否决从零参考重做**（多年量级、 mandate 错位、许可证使"搬源码"本就不可行）；SpotBugs 与指针分析通道无关（无 PTA），其价值 = Wave 2/3 两个分析形态的参考 + 既有 qa 接线维持。
- 后续工作：Wave 0 item 2 / Wave 5 item 10 plan 立项时消费本分析；若 owner 接受 Tai-e 倾向，建议在 nop-bytecode-analysis roadmap Wave 5 item 10 增注"候选 = Tai-e 优先，SootUp 对比"（纯增量原则下由 owner 拍板后改）。

## Open Questions

- [x] Tai-e 对 Java 21 产物（class file v65）的分析支持范围与 JRE 建模完备度（-java 支持版本上限）——**已结（plan 09 spike）**：应用产物 v61/v65 均解析通过；current-JRE 模式要求运行时 JRE 镜像 ≤ v69（Java 25，ASM 9.8 前端上限；Java 26 镜像 v70 报 Unsupported）
- [x] 本仓全量（100 模块 + 依赖闭包）PTA 的耗时/内存——spike 采集——**按 plan 09 Non-Goals 未做全仓**；中间锚点在档：jq 单模块 CHA 1.57 s / 1.13 GB（22.6k reachable），全程序 PTA toy 已达 13.4 s / 2.30 GB（模块级实测 = successor 第一验收项）
- [x] 框架型库（无 main）的入口点建模成本——**已结（plan 09 spike）**：main-class 直用 + entry driver（已 demo）/ `--input-classes` / EntryPointHandler 插件 / taint-config 式声明文件四条适配路径；SootUp 侧 = 显式 `List<MethodSignature>` 直传（已 demo）
- [x] Tai-e v0.5.x → 1.0 的 API 稳定性承诺（是否声明 semver）——**已结（plan 09 spike）**：无 semver 声明且 churn 实锤（0.5.4 maven 构件不可直接运行[POM 无 main-class、无 fatJar] + jre-dir JIMAGE 路径缺陷 + master 默认改 current-JRE）——pin 版本 + 隔离适配层为必要条件

## Postscript（2026-09-30，plan 09 消费记录）

- **Wave 5 终裁**：adopt 窄桥接（五判据全过）——判定首选 Tai-e（taint/PTA 面）、SootUp 候选（调用图面）；数据与 successor 面清单见 [interprocedural-spike.md](../../../nop-bytecode/docs/interprocedural-spike.md)。
- **坐标勘误**：`net.pascal-lab:tai-e` 在 central 的版本列表实查 = 0.2.2 / 0.5.1 / 0.5.2 / **0.5.4**（本文原句「maven central 有 v0.5.4」属实；maven search API 的 latestVersion 字段显示 0.5.1 系陈旧索引，非 central 实况）。补充事实：0.5.4 构件是裸 jar，**不可直接作为工具运行**——可运行发行物须自源码构建（master fatJar，0.5.5-SNAPSHOT）。
- **本机环境注记**（影响复现）：ms-17.0.17 为空壳安装（Contents/Home 为空——jrt-fs.jar 与 modules 均缺）；可用的真实 JDK = zulu-26.0.1（v70 镜像）与 openjdk-25.0.1（v69 镜像）。

## References

- 本机源码（仓外）：~/sources/lint/spotbugs、~/sources/lint/Tai-e（2026-09-29 浅克隆，数字均实测自当日 HEAD）
- Tai-e: ISSTA'23 框架论文、docs/en/*.adoc、README（论文列表）；GitHub: pascal-lab/Tai-e
- SpotBugs: spotbugs/spotbugs 子模块（engine）；GitHub: spotbugs/spotbugs
- [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md)（Wave 0/5、Hard constraints）
- [nop-lint 工具替代统一账本](../../../nop-lint/docs/tool-replacement-ledger.md)（Sonar taint not-replaceable 行）
- ai-dev/design/self-contained-design.md（依赖纪律输入）
