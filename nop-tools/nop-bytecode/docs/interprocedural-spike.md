# Wave 5 跨过程调用图 spike 数据与终裁（Interprocedural Spike）

> 日期: 2026-09-30
> 状态: active（plan: [ai-dev/plans/nop-bytecode/09-wave5-interprocedural-spike.md](../../ai-dev/plans/nop-bytecode/09-wave5-interprocedural-spike.md)）
> 定位: roadmap Wave 5 item 10（跨过程调用图 spike，候选 C 复评）+ item 11（taint 面重估条件分支）的 spike 数据与 adopt-or-skip 终裁记录
> 判据权威: plan 09「裁定判据」节（执行前钉死，本文件 §二 逐条对照）
> 原始数据: `_tmp/nop-bytecode-w5-spike/`（gitignore；复现命令随各腿记录）

## 一、Spike 数据（三腿）

### 腿 1 — G4 注解契约读取探针（ASM tree 9.7.1）

复现：`javac -cp asm:asm-tree -d out src/Leg1Annotations.java && java -cp asm:asm-tree:out Leg1Annotations <classes-dir>`（原始输出 `leg1-jq.txt` / `leg1-toy17.txt`）。

| 语料 | classes/methods/fields | 类级注解 | 方法级 | 字段级 | 参数注解槽 |
|---|---|---|---|---|---|
| nop-jq target/classes（v61） | 97 / 811 / 262 | visible 3（全部 `@FunctionalInterface`）| **0** | **0** | **0** |
| toy（v61 对照） | 4 / 5 / 0 | 6 | visible 1 + invisible 1 | 0 | visible 1 |

- **读取能力全形态证实**：runtime-visible 类/方法/参数注解 + runtime-invisible（CLASS retention）方法注解均正确读出；参数注解槽定位到 `toy/ToyService.consume(Ljava/lang/String;)V` param0（`leg1-toy17.txt` 样例行）。
- **jq 语料诚实负结果**：现网语料不携带任何 nullness 契约注解（无 jsr305/jetbrains/平台自有 @Nullable 系）——字节码层注解契约面在本仓当前为**空集**；读取能力成立，可读对象缺位。
- ASM 9.7.1 API 事实：参数注解属性类型为 `List<AnnotationNode>[]`（按参数索引）。

### 腿 2 — SootUp 1.3.0 调用图最小工程（候选 C 复评）

依赖闭包：`org.soot-oss:sootup.callgraph:1.3.0` 补入 POC 三构件后 **34 jars / du 9,084 KB**（POC 33 jars / 9,056 KB——净增仅 callgraph 构件自身，jgrapht 等传递依赖已在闭包）。

复现：`mvn -f pom-sootup.xml dependency:copy-dependencies`；`javac -cp libs-sootup -d out src/Leg2Callgraph.java`；`java -Xmx1g -cp libs-sootup:out Leg2Callgraph <mode> <corpus>`（原始输出 `leg2-*.txt`）。

**toy 注解入口 demo（判据 2/5 产物，v61 与 v65 双跑同构）**：入口 = `@FrameworkEntry` 注解方法 `toy.ToyService.handle`；结果 `TOY-ENTRY-DEMO=PASS`——调用图以注解方法为根含 `handle→helper`、`handle→consume` 边，`helper→leaf` 传递可达；`callsTo(consume)` 返回 `handle`（参数契约 × 调用边 join 的调用者侧事实）。**v65（Java 21 产物）解析+调用图构建通过**（与 ADR §二 v65 ✅ 一致，复确认）。

**jq 语料（97 classes / 811 methods，v61）两种入口集口径**：

| 口径 | entries | reachable（app） | edges | wall real（3 跑） | phase：view+entries / initialize（中位） | max RSS |
|---|---|---|---|---|---|---|
| API 面（io.nop.jq 顶层类 public 具体方法） | 20 | 792（597） | 2601 | 0.34 / 0.35 / 0.42 s（中位 0.35 s） | 93.2 ms / 220.9 ms | ~176 MB（中位 184,532,992 B；三跑 177,438,720 / 184,532,992 / 184,713,216 B） |
| 全量 public 具体方法 | 473 | 956（744） | 2836 | 0.36 s | 98.3 ms / 223.9 ms | ~170 MB |

- 判据 1 对照（SootUp 腿）：wall 0.35 s ≤ 120 s ✓；RSS ~0.17 GB ≤ 2 GB ✓。
- API 摩擦记录（工程成本证据）：`IdentifierFactory.getMethodSignature(String,String,String,List)` 四串重载参数序陷阱——误用会**静默**产出转置签名（`<handle: void toy.ToyService(...)>`）且调用图为空、无报错；稳妥路径 = `view.getClass()` reify 后取 `SootMethod.getSignature()`。1.3.0 调用图算法在独立构件 `sootup.callgraph`（POC 三构件不含），CHA 算法 `initialize(List<MethodSignature>)` 即用，`CallGraph` 契约（reachable/callsFrom/callsTo/containsCall/exportAsDot）齐备。
- SootUp **无内置指针分析/taint**：1.3.0 提供调用图（CHA/RTA）+ Jimple IR + 图工具；参数契约传播或 taint 均需在 IR 上自建分析——与 ADR §三「跨过程能力开箱即用」的预设相比，实际开箱面 = 调用图构建，taint 面无对应构件。

### 腿 3 — Tai-e 外部工具最小实跑

**获取路径（判据 4 前提证据）**：

- maven 构件 `net.pascal-lab:tai-e:0.5.4` 在档（52-jar 闭包含 soot 4.4.1 / FlowDroid / ASM 9.8 / kryo 等），但 **POM 无 main-class、无 fatJar 发行物——构件不可直接作为工具运行**（复现：`mvn -f pom-taie.xml dependency:copy-dependencies` + manifest 直查）。
- 可运行发行物 = 仓外源码构建：master 克隆 `~/sources/lint/Tai-e`（8f22e41，版本 0.5.5-SNAPSHOT，GPL-3.0 源码不出仓外）`./gradlew fatJar` → `build/tai-e-all-0.5.5-SNAPSHOT.jar`。构建成本：gradle 9.7.1 发行包 ~165 MB 下载 + 首次构建（网络依赖，本机约 15 分钟量级）。
- **0.5.4 jre-dir 缺陷实证**：JIMAGE 布局下 jrt-fs.jar 推导路径错误（一律找 `<modules>/lib/jrt-fs.jar`），JAVA_HOME 式 / side-by-side / 手工 mock 三种布局均复现同一 `IOException`；master 已以「current-JRE 默认」绕开（`-java` 与 `--jre-dir` 均省略时用运行中 JVM 的 `jrt:/`）。API churn 实锤（0.5.4 → master 行为差异）。
- **运行时版本边界（判据 5 边界）**：current-JRE 模式要求运行时 JRE 镜像 ≤ v69（前端 ASM 9.8 上限；zulu-26/Java 26 镜像实证报 `Unsupported class file major version 70`）→ 本机以 openjdk-25.0.1（v69 镜像）运行时跑通。被测应用产物 v61/v65 均正常解析。

**实跑数据**（原始输出 `leg3-taie-*.txt`；命令随文件内 Tai-e options 回显可复现）：

| 跑 | 语料 / 入口 | 结果 | 资源 |
|---|---|---|---|
| CHA toy v61 | out-toy17，`-m toy.ToyMain` | **28 reachable / 31 edges**，exit 0 | wall 0.68 s，RSS ~572 MB（WorldBuilder 0.26 s + cg 0.04 s） |
| CHA toy v65 | out-toy21，同上 | **28/31 同构——v65 ✓** | 同上 |
| CHA jq | jq 语料 + `jqdriver.JqDriver`（自写 entry driver，入口点适配的现实形态） | **22,595 reachable / 277,262 edges**（全程序闭包含 JRE——与 SootUp app-scope 图不同量纲）；已知噪音 = JRE invokedynamic lambda `CHA cannot resolve` 警告多条 | Tai-e 总耗时 1.35 s，wall 1.57 s，RSS ~1.13 GB（-Xmx2g） |
| taint 探针 | toy source→sink（call-source `result` → sink param0；另设常量对照） | **`Detected 1 taint flow(s)`**，`TaintFlow{…source/result -> …sink/0}` 含 source 方法定位与 sink 方法定位两个可解析元素；常量对照未产生流（无误报形态） | wall 13.36 s，RSS ~2.30 GB（全程序 PTA；**PTA 资源量级 = successor 实施期第一实测项**） |

**入口点机制盘点**（判据 2 评估面）：Tai-e 入口 = main-class 为主（`-m`）；库形态适配路径 = entry driver（已 demo）/ `--input-classes` / `EntryPointHandler` 插件 / taint-config 式声明文件——机制存在但均需适配层；对照 SootUp = `List<MethodSignature>` 显式入口集直传。

**analysis 文档四 Open Questions 结案**：(1) v65 支持 = ✓（应用产物 v61/v65；前置约束 = 工具运行时 JRE 镜像 ≤ v69）；(2) 全仓 PTA 性能 = 未测（Non-Goals；jq 单模块 CHA 1.57 s / 1.13 GB 为中间锚点，PTA 量级见 taint 探针行）；(3) 入口点建模成本 = main-class 直用 + driver/plugin/config 三条适配路径（见上）；(4) API 稳定性 = pre-1.0 churn 实锤（0.5.4 maven 构件不可运行 + jre-dir 缺陷 + master 行为变更）——pin 版本 + 隔离适配层为必要条件。

## 二、adopt-or-skip 终裁

**裁定：adopt（item 10 成立）——跨过程面以「外部工具窄桥接」形态承接**（design 00-overview §2.5 形态；HC5 独立 plan + spike 数据 + 终裁齐备）。

**判定首选 = Tai-e**（taint/PTA 面现成、全程序闭包、source/sink 声明式配置即误报控制面雏形）；**SootUp = 调用图面轻量候选**（app-scope 图、34 jars、无 PTA/taint 构件——下游面需在 Jimple IR 自建，与「从零重做」拒绝项同向）。

判据逐条对照（判据全文见 plan 09「裁定判据」节）：

| # | 判据 | 结果 | 证据 |
|---|---|---|---|
| 1 | 规模预算（wall ≤ 120 s 且 RSS ≤ 2 GB，jq 语料调用图） | **PASS** | SootUp 0.35 s / ~0.17 GB（三跑中位）；Tai-e 1.57 s / ~1.13 GB。阈值推导 = SpotBugs 框架级基线 8.42 s / 1.72 GB 留余量 |
| 2 | 入口点适配（注解方法为根的可达边集非空） | **PASS** | SootUp toy 注解入口 demo `TOY-ENTRY-DEMO=PASS`（v61+v65）；Tai-e main+driver demo 22.6k reachable；非 main 入口机制在档 |
| 3 | 下游价值（(a) 契约×调用边 join 或 (b) taint 探针） | **PASS** | (b) Tai-e taint `Detected 1 taint flow(s)`（source/sink 双定位可解析，常量对照零误报）；(a) join 记录 `join-contract-caller.txt`（toy；**jq 语料诚实负结果 = 零注解契约**，可读对象缺位） |
| 4 | 窄桥接形态可行（外部进程 + 机读结果消费） | **PASS** | 两框架均独立进程 CLI 运行、stdout 机读（`Call graph has N reachable` / `TaintFlow{…}`）；依赖全部留在 `_tmp`/构建工具边界，`nop-bytecode/pom.xml` 零改动 |
| 5 | v65 支持 | **PASS** | SootUp v61+v65 toy ✓；Tai-e v65 应用产物 ✓，附运行时镜像 ≤ v69 边界（记录在 §一.3，入 successor 前置约束） |

**四要素**：(a) **裁定 + 理由** = 五判据全过；跨过程面（taint/参数契约）自建为多年量级（设计权威 §4 已拒），外部窄桥接是唯一可行承接形态且实测可行；(b) **机制面证据** = §一三腿数据；(c) **重估触发** = ① Tai-e/SootUp 版本线重大变化（1.0 GA、构件可运行化）时资源与获取成本重评；② PTA 模块级资源实测超 CI 分钟级预算时回退裁定（调用图面与 taint 面可拆分裁定——调用图面资源余量大）；③ 平台落地字节码注解契约约定时 G4 面从输入面升发现面重评；(d) **误报控制面** = taint source/sink 注册表白名单（`taint-config` YAML 声明式，即 gap-ledger G3 预设的最小控制面实现形态）。

**资源风险如实登记**：全程序 PTA 在 toy+JRE 已达 13.4 s / 2.30 GB——CI 分钟级预算内单模块可行，但模块级实测是 successor 立项的第一验收项；超预算时按重估触发 ② 拆分裁定。

## 三、successor 面清单（adopt 分支）与注记

1. **模块级 taint/PTA 窄桥接实施 plan（G3 successor）**：单模块 PTA 资源实测（jq 语料先行）→ taint source/sink 注册表白名单（平台方法/注解约定）→ report-only 输出对齐通道诊断格式（HC6：report-only 起步，升 hard gate 须独立 plan + 误报数据）。
2. **参数 nullness 契约面（G4）**：读取能力已证（腿 1），当前语料契约空集——可读对象缺位是平台约定问题而非通道能力问题；successor 需与「平台字节码注解契约约定」（如 @Nullable 系编译保留策略）同案裁定，不独立立项。
3. **G5 注记（重估触发兑现）**：Tai-e 无 NullAway 式全程序注解推导能力（PTA ≠ 注解推断）——G5「不承接」维持，重估触发继续有效（承接形态仍限窄桥接，不改自建禁令）。

**禁改项重申**：`nop-bytecode/pom.xml` 运行时依赖维持 JDK + ASM（窄桥接 = 构建工具边界外部进程）；LGPL 整库仅限构建工具边界；**禁止源码移植**（GPL/LGPL → Apache-2.0）；HC1 纯增量——nop-lint 资产 / 统一账本 / SpotBugs 接线 / CI workflow 零改动（本 spike 全程未触碰）。
