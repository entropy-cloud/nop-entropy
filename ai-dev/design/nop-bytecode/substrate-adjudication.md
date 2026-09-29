# nop 字节码通道底座终裁（Substrate Adjudication）

> 日期: 2026-09-29
> 状态: active
> 裁定性质: [roadmap Wave 0 item 2](../../backlog/nop-bytecode-analysis-roadmap.md) 的终裁 ADR——以 POC 数据证据化（roadmap Hard constraint 3）
> 关联: [00-overview.md](./00-overview.md) §2.5 外部能力使用形态、[gap-ledger.md](../../../nop-bytecode/docs/gap-ledger.md)（缺口归属）

## 一、终裁

**Wave 1–3（解析 / 内核 / 两大分析器）采候选 A：ASM 自研底座**——asm-tree 9.7.1 + 自建方法内 CFG + 自建抽象解释框架。模块组定名维持默认候选 **`nop-bytecode`**（与 nop-treesitter 同型定位：底座库）。

- 候选 B（SpotBugs plugin）**拒绝**：SPI 集成实测未跑通 + 框架级成本三倍劣 + 内部 API 稳定性风险实证。
- 候选 C（SootUp）**不入选 v1，保留 Wave 5 复评资格**：API 可用性已实证（同语义探针 254 行跑通），但依赖体积与吞吐全面劣势，且 v1 面均为方法内分析、用不到其跨过程能力。Wave 5 item 10 跨过程 spike 以 SootUp 为首选外部框架候选（外部工具/窄桥接形态，§2.5）。

## 二、五维数据对照（POC 实测 2026-09-29）

语料 = nop-jq 的 target/classes 编译产物目录（97 classes / 811 methods，class file v61）；吞吐口径：A/C = 解析+CFG+探针总 wall time 三跑取中位；B = 既有 qa profile 接线全 run wall time（**框架级替代口径，含 maven 启动，不与 A/C 直读对比**）。内存 = `/usr/bin/time -l` max RSS，`-Xmx1g`。依赖体积 = 实际解析的运行时 classpath jar 字节求和；工程成本行数 = `wc -l` 总行数（含空行注释）。

| 维度 | A（ASM 自研） | B（SpotBugs） | C（SootUp 1.3.0） |
|---|---|---|---|
| 吞吐（中位） | **79 ms** | 8.42 s（框架级口径） | 416 ms |
| 内存峰值（max RSS） | **~118 MB** | ~1.72 GB | ~289 MB |
| 依赖体积 | **3 jars / 213 KB** | 23 jars / 16,572 KB（du -sk 口径） | 33 jars / 9,056 KB（du -sk 口径） |
| 工程成本（POC） | 730 行 / 20 API 触点 | （SPI 骨架，未跑通，见 §四） | 254 行 / 27 API 触点 |
| v65（Java 21 class file） | ✅ 解析+分析通过 | ✅ 独立 CLI 解析通过 | ✅ 解析+分析通过 |

辅助数据：toy 语义探针（守卫分支/requireNonNull 门控零误报 + 真 2 阳性）A/C 两侧同题跑通且命中数同量级（jq 语料 A=2791 / C=2835，差异源于 Jimple 临时变量形态）——自研探针与 SootUp 探针互为交叉验证。A 的逐 opcode 栈形状经 ASM `tree.analysis`（Analyzer+BasicVerifier）作 oracle 全量复核：语料上 72 个出现的 opcode 线性形状 100% 一致。

POC 脚手架与原始数据：`_tmp/nop-bytecode-poc/`（gitignore；`poc-asm/`、`poc-sootup/`、`poc-spotbugs-plugin/`、`poc-spotbugs-standalone/`、`results/`——ADR 数字以 results 留档为准）。

## 三、各候选裁定理由

### A（ASM 自研）——采纳

- 五维全占优：吞吐/内存/依赖体积均为三候选最优；213 KB 零传递依赖贴合平台 self-contained 原则（ASM 属"适配层内收敛的单一职责工具库"豁免形态，见 00-overview §2.5）。
- per-file 模型天然贴合：`ClassReader`/`ClassNode` 直接消费 target/classes 采集产物。
- 自研成本实测可控：方法内 CFG + 槽位精确抽象解释 + 分支敏感 nullness 传播 = 730 LOC（含调试设施），核心机制约 500 LOC。
- 代价（如实登记）：字节码指令级开发门槛已被 POC 实证——初版探索中出现 6 类栈形状/边传播缺陷（cat2 双槽、xASTORE/LSTORE 族、比较族 push 缺失、AALOAD 取数顺序、ATHROW handler 边、IFNULL 精化语义），经 oracle 复核逐一定位修复。Wave 1 内核须携带等价 oracle 复核纪律（详见 §五）。

### B（SpotBugs plugin）——拒绝

- **SPI 集成实测未跑通**（Hard constraint 3 下的关键反证，详见 §四）："以 SpotBugs plugin SPI 写自定义 detector 补缺口最快"的预设被实证推翻。
- 框架级成本：全 run 8.42 s / 1.72 GB RSS / 16.5 MB 依赖——在 CI 分钟级预算内可接受，但为 A 的 5–100 倍，且发现流形态被 224 个内置 detector 框架约束。
- 内部 API 稳定性风险（`edu.umd.cs.findbugs` 跨大版本不稳定）无对冲手段——SPI 路径本身已实证有摩擦。

### C（SootUp）——v1 不入选，Wave 5 首选外部候选

- v1 不入选：依赖 8.8 MB / 33 jars（携带 dex 工具链、antlr、guava 等与 class-file 分析无关面）；RSS 与吞吐均数倍于 A；其核心价值（Jimple IR、调用图、跨过程入口）对方法内面无增益。
- **保留 Wave 5 复评资格**：POC 实证其 API 可用性良好——Jimple 三地址码使探针实现比字节码版更简（254 行 / 27 API 触点），`StmtGraph`/`Body` 契约清晰，自带 `DominanceFinder`/`BasicBlock` 等图工具；1.3.0 为三构件（sootup.core / sootup.java.core / sootup.java.bytecode）共同版本（点分坐标，无聚合构件）。若 item 10 跨过程 spike 采纳外部框架，按 00-overview §2.5 以外部工具/窄桥接形态使用（独立进程、结果报告消费、pin 版本），不作为平台内嵌引擎。

## 四、失败腿处置记录（证据纪律）

**POC-B(b)：最小 plugin SPI detector 未跑通（10 次尝试）**。

- 已达成部分：detector 插件工程编译打包成功；`-Dspotbugs.pluginList` 挂载后 SpotBugs 4.9.8.3 输出 `Loading poc.poc-spotbugs-detector` → `Registering detector: poc.PoCDetector` → `Adding plugin ... to execution plan`（trace 在档）；既有接线只读实跑基线（(a) 项）成功产出 6 findings + spotbugsXml。
- 失败点：自定义 detector 的 `visitClassContext`/`visitJavaClass` 从未被调度执行——maven-plugin（含 trace/debug 多轮）与独立 `FindBugs2 -pluginList` CLI 两条路径一致复现；detector 类反射实例化正常（ctor 可达）；同 run 内 core detectors 正常出报告。根因未定位（执行计划对第三方 XML 插件 detector 的激活条件），超出 POC 时间盒。
- 处置：按统一失败处置降格——B 腿的"集成成本"结论依赖 (a) 框架成本基线 + 本失败记录 + 既有复杂度归因（`edu.umd.cs.findbugs` 内部 API 依赖面），不宣称"SPI 可行"。
- **重估触发**：SpotBugs 官方插件示例在 4.9.x + 现代构建链上复验通过（detector 实际执行）时，B 的集成成本结论重估；若届时 A 已落地内核，重估仅作对照记录，不自动翻转终裁。

## 五、对 Wave 1 内核的裁定输入（tree.analysis 复用 vs 自建）

roadmap item 4 预留"`tree.analysis` 复用 vs 自建随 item 2 数据裁"。POC 数据裁定：**自建 CFG + 抽象解释框架；`tree.analysis` 仅作 oracle 测试参考**。

- `BasicValue` 无 nullness 刻度（无 NULL_VALUE 常量），nullness lattice 必须自定义 Interpreter 携带；
- `Analyzer` 的 join-only 合并不提供分支敏感边传播，IFNULL/IFNONNULL 精化需边状态注入——自建 worklist 引擎已实现该形态；
- 自建引擎的正确性纪律：以 `Analyzer+BasicVerifier` 逐 opcode 栈形状 oracle 复核纳入内核测试基建（POC 已验证该方法可定位全部形状缺陷）。

**POC 探针降配口径注明**（与 Wave 2 正式口径差异）：POC 分支敏感面限定 IFNULL/IFNONNULL（A 侧经 ALOAD 槽位来源追踪精化局部变量；C 侧 Jimple local 直接精化），其余分支 join-insensitive MAYNULL 合并。Wave 2 item 6 正式口径（2026-09-29 落地）= IFNULL/IFNONNULL + ACMP-with-NULL 常量精化（仅能携带 null 事实的条件分支；数值分支无 null 事实，保持 join-insensitive）+ $assertionsDisabled 断言恒启用建模——归因经 javac 实验修正。POC 数据不构成对正式口径的能力宣称。


## 五点五、复现命令（POC 原始数据同 `_tmp/nop-bytecode-poc/results/` 留档）

toy 语料编译（v65，A/C 共用）：`javac --release 21 -d toy/out toy/ToyNull.java`（本机 JDK zulu-26；toy 源码 = `_tmp/nop-bytecode-poc/poc-asm/toy/ToyNull.java`）。

- **A**：classpath = 本地 .m2 的 asm/asm-tree/asm-analysis 9.7.1 三 jar；`javac -cp "$CP" -d out src/PoCA.java` 后 `java -Xmx1g -cp "$CP:out" PoCA <nop-jq>/target/classes`（v65 复验：同命令换 toy/out）；内存 = `/usr/bin/time -l` 取 maximum resident set size。
- **B(a) 基线**：`/usr/bin/time -l ./mvnw -pl nop-jq -Pqa spotbugs:spotbugs`（root pom 内建 failOnError=false + excludeFilterFile + threshold=low，零 pom 改动）；**B(b) SPI**：`./mvnw -pl nop-jq -Pqa spotbugs:spotbugs -Dspotbugs.pluginList=<detector.jar>` + 独立 `java -cp <libs> edu.umd.cs.findbugs.FindBugs2 -pluginList <detector.jar> -low -xml <out> <toy>`（spotbugs 4.9.8 运行时闭包经 maven `dependency:copy-dependencies` 解析，23 jars）；v65 复验 = 独立 CLI 跑 toy/out。
- **C**：`org.soot-oss:sootup.core / sootup.java.core / sootup.java.bytecode` pin 1.3.0（maven 解析闭包 33 jars）；`javac -cp "$CP" -d out src/PoCC.java` 后 `java -Xmx1g -cp "$CP:out" PoCC <nop-jq>/target/classes 绝对路径`（SootUp 要求 class container 绝对路径）；v65 复验：同命令换 toy/out（results/pocC-v65.txt）。

## 六、重估触发（本 ADR）

- ASM 大版本破坏性变更（9.x → 10+）导致 class file 解析适配成本超阈值时重评版本 pin；
- Wave 5 item 10 spike 结论（外部框架采纳与否）不回翻本 ADR 的 Wave 1–3 裁定；
- SootUp 版本线收敛（java.bytecode 构件恢复与 core 同步发版）或依赖闭包显著瘦身时，Wave 5 候选评估数据更新；
- POC-B SPI 失败的重估触发见 §四。
