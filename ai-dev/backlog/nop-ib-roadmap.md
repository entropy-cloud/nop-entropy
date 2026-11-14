# nop-ib 实施 Roadmap — 用 javac 增量引擎替代 AI 开发期的 Maven 全量调用

> Created: 2026-11-13
> Last updated: 2026-11-14（Wave 0–5 实现与实测收口,M0–M5 达成;items 26/27/28/30 scoped defer 见 Deferred 段）
> 设计权威: [ai-dev/design/nop-incremental-build/05-nop-ib-engine-design.md](../design/nop-incremental-build/05-nop-ib-engine-design.md)（Architecture Baseline，规范性）
> 上游设计: [00-vision.md](../design/nop-incremental-build/00-vision.md) / [01-architecture-baseline.md](../design/nop-incremental-build/01-architecture-baseline.md) / [02-test-execution.md](../design/nop-incremental-build/02-test-execution.md) / [03-alternatives-and-roi.md](../design/nop-incremental-build/03-alternatives-and-roi.md)
> 参考实现分析: [04-gradle-incremental-compilation-analysis.md](../design/nop-incremental-build/04-gradle-incremental-compilation-analysis.md)
> 资产锚点: `nop-kernel/nop-javac`（文件式编译已完成，plan 40）、`nop-tools/nop-bytecode`（ASM 底座）、`ai-dev/tools/mvnq` + `.mvn/build.lock`（并发治理）
> 发起：owner 指令（2026-11-13）——"基于 javac 自行实现增量化编译引擎，替换 AI 开发时的 mvn build / mvn test"

## Purpose

AI 辅助开发当前每次都调用 `mvn build` / `mvn test` 全量命令。`00 §二` 实测表明单次成本几乎全部是构建骨架（本机空闲约 2.2s，四路并发约 4.7s，八路约 7.8s），与模块大小无关；`03` 的适用面实测表明 63.4% 的真实编辑可以走"绕开 Maven 骨架"的快路径。

本 roadmap 的目标是把 `nop-ib` 从零落成一个**可被 AI 默认为日常入口**的增量引擎：编译走 `nop-javac` + 字节码依赖闭包，测试走"模块图粗筛 + 类图细选"，冷路径仍由 `./mvnw` 承担。三句定位：

1. **替换的是反馈回路，不是构建系统**：CI/发布/生成/全量冷构建仍走 Maven；
2. **正确性优先于速度**：任何不确定一律显式回退，禁止假绿与静默截断；
3. **先只读、再编译、再测试、再常驻**：每一波都以前一波的对照数据为开工门槛。

## 准入判据与成功 oracle

1. **零假绿**：所选测试集合的漏选必须可被 oracle 检出；`partial` / `wideImpact` / `fallback` 必须出现在 JSON。
2. **产物一致**：抽样的 L0 模块，快路径产物与干净 `mvn compile` 的类集合 + 逐类 ABI digest 零差异（字节级差异另作增强证据）。
3. **成本可量化**：目标机器上 `noop`、`singleMethod`、`singleAbi`、`affectedTests` 四类指标有基线（见设计 §十一）；对外结论不得引用他机数字。
4. **可回滚**：任何阶段都可以一键切回 `mvnw`（配置开关 + 文档）。
5. **默认入口**：AI 会话内的日常编译/测试由 `nop-ib` 承担，`mvn` 只出现在冷路径与显式回退。

## Work Items

> **本块是唯一动态状态块。** 状态词表 `todo / planned / done`（`done` 须有独立 closure audit 证据）。
> 每个 item 标注类型：`Decision | Proof | Feature | Fix | Follow-up`。

### Wave 0 — 裁定与基线（开工前置）

- 1. **[Decision]** 模块落位、命名与依赖方向裁定（IB-G1 / IB-G5）：默认候选 `nop-tools` 下的 `nop-ib`，依赖 `nop-javac` + `nop-bytecode`，不新增 `-api`、不依赖 `nop-code-service`；同步 `docs-for-ai/01-repo-map/module-groups.md` 等登记面: `done`（2026-11-13，见 plans/nop-ib/wave0-adjudications.md IB-G1） — deps: none
- 2. **[Decision]** 构建事实导出形态 POC（IB-G2）：对比"开发期小 mojo/extension 一次 reactor 导出"与"effective-pom 批量 dump"在 465 个 pom 上的耗时、完整度、维护面；产出 `model.json` 草案 schema: `done`（裁定 effective-pom 批量 dump 形态,22.4s/457 project 实测,见 wave0-adjudications.md IB-G2） — deps: none
- 3. **[Proof]** Phase 0 成本与适用面实测：目标机器上的骨架/编译/测试三段基线；L0/L1/L2/L3 模块占比；以 20–30 次真实编辑为例的适用面复算（对齐 `00`/`03` 口径）: `done`（本机 p50≈3.42s 骨架/L0 343·75.1%、L1 92·20.1%、L2 12、L3 10；30 commit 复算见 wave0-adjudications.md） — deps: none
- 4. **[Decision]** 测试执行路线与切换门（IB-G3）：首版委托 `mvn surefire:test`，或直接自研 JUnit Platform 启动器；给出对照验收标准: `done`（首版委托 surefire:test,自研启动器须同语料对照通过后切换,见 IB-G3） — deps: 1, 2
- 5. **[Decision]** 锁与并发协议（设计 IB-D9 / IB-G6）：确认 `nop-ib` 写产物与 `mvnq` 共用 `.mvn/build.lock`，只读命令不取锁；确认 `--max-tests`/partial 的默认策略: `done`（共用 .mvn/build.lock,只读不取锁,--max-tests 默认拒绝,partial 须显式,见 IB-G5） — deps: 1
- 6. **[Decision]** 动态覆盖是否进入 v1（IB-G4）：默认不做，静态图优先；给出后续收窄的触发条件: `done`（v1 不做,静态图优先,见 IB-G4） — deps: 3

- ✅ **Milestone M0：六项裁定落档，设计 05 与实现基线对齐**（unlocks when 1–6 done）

### Wave 1 — 只读内核（零产物写入）

- 7. **[Feature]** 模型导出器：按 item 2 的裁定实现 `model.json`（模块/依赖边/编译事实/插件 execution/测试配置/工具链），带内容哈希与版本；提供 `nop-ib model refresh|show` 与 schema validator: `done`（456 模块 model.json+pom 哈希失效检测,formatVersion 校验） — deps: 2, 6
- 8. **[Feature]** 字节码依赖索引：基于 `nop-bytecode` 实现 `class→contentHash`、`class→dependents(private/accessible)`、`ABI digest`、`inlineConstantHashes`、`source→classes`、`testClass→referencedClasses`；定义 `.nop-ib/units/<module>.bin` 格式与版本迁移: `done`（版本化 JSON 载荷+formatVersion,383 模块 4s 全量索引） — deps: 1, 7
- 9. **[Feature]** 模块级粗筛：reactor 图的反向依赖闭包（含 `test-jar`、parent→child、pom 变化扩散），模型缺失时不剪裁: `done`（ModuleGraph 反向闭包+parent 传播） — deps: 7
- 10. **[Feature]** 类级闭包与回退矩阵：编译集/测试集计算（设计 §6.3/§6.4），L0–L3 能力分级与 §6.5 放宽矩阵；索引健康闸门: `done`（indexMissing→不剪裁+full test） — deps: 8, 9
- 11. **[Feature]** 只读命令面：`status` / `plan` / `explain` / `cache size` + `--json` 契约（设计 §九）+ 退出码；`plan` 必须给出编译集、测试集、回退原因、`wideImpact`/`partial` 预测: `done`（§九契约 JSON,退出码 0/1/2/3/6/75） — deps: 10
- 12. **[Proof]** 只读预测复算：用 20–30 次真实编辑回放，比对 `plan` 与实际 `mvn` 影响面；对差异逐条裁定（模型缺口 / 索引缺口 / 规则缺口）: `done`（25 commit 复算,1 告警=复算脚本扩展集窄于分类器,裁定引擎正确） — deps: 11

- ✅ **Milestone M1：`plan` 能在零写入前提下预测范围；差异逐条裁定**（unlocks when 7–12 done）

### Wave 2 — 编译快路径

- 13. **[Feature]** 编译作用域引擎：内容哈希 → ABI digest → 反向闭包；同文件多类处理；删除类按旧索引反查；常量/module-info/源保留注解的保守规则: `done`（inlineConstants+abiDigest 实测辨识签名 vs 方法体差异） — deps: 10
- 14. **[Feature]** 增量编译执行器：`nop-javac` 文件式编译；`-implicit:none`、默认 `-proc:none`、按 execution 取 `--release`；先删后编 + stash/rollback；结构化诊断透传: `done`（fail-loud 实测） — deps: 13
- 15. **[Feature]** classpath 物化与拓扑调度：per-phase classpath（main compile / test compile）；只使用 reactor 目录与外部 jar，禁止 intra-project jar；按依赖序执行、同层有界并行、输出目录互斥: `done`（v1 模块串行,拓扑序执行） — deps: 13
- 16. **[Feature]** `compile` 命令：`--paths` / `-pl` / `--changed` / `--all` / `--on-fallback=maven|fail`；写产物取 `.mvn/build.lock`；冷路径委托必须走 `mvnq`: `done` — deps: 14, 15
- 17. **[Proof]** 产物对拍 harness：抽样 L0 模块，快路径 vs 干净 `mvn compile` 的类集合 + 逐类 ABI digest 零差异；差异 → 状态失效并报告；记录抽样集与命令: `done`（nop-javac 单文件+整模块、nop-core L1 整模块 byte 级零差异;tools/ab_compare.py） — deps: 16
- 18. **[Fix]** 并发与锁回归：与 `mvnq`/`mvn` 交错执行的压力用例（并发构建、锁超时、异常退出），确认无产物踩踏与死锁: `done`（TestBuildLock 3 用例:互斥/超时 75/与 MvnqLock 跨进程双向互通;修复外层锁×mvnq 自死锁） — deps: 16

- ✅ **Milestone M2：L0 模块编译快路径可用、对拍零差异；默认 `--on-fallback=maven`**（unlocks when 13–18 done）

### Wave 3 — 测试选择

- 19. **[Feature]** 测试索引：测试类发现（JUnit 5 注解/继承/参数化/动态）、抽象基类、`test-jar` 边、测试数据/快照/资源消费规则: `done`（unit index testClass refs+ChangeClassifier 数据/资源规则,数据变更→跨模块全量测试） — deps: 8
- 20. **[Feature]** 测试选择引擎：行为闭包（含方法体变化）、放宽矩阵、`partial`/`wideImpact`；编译回退后重新判定: `done`（check 内 compile 后 re-plan） — deps: 10, 19
- 21. **[Feature]** 测试执行委托：`mvn -pl <modules> surefire:test -Dtest=<classes>`；cwd/classpath 定序/`argLine`/系统属性从 `model.json` 对齐；超时与进程看门狗: `done`（MavenFallback timeout+destroyForcibly,锁超时 75） — deps: 4, 16
- 22. **[Feature]** `test` / `check` 命令：`--affected` / `--class` / `--rerun-failed`；统一 JSON 与退出码；失败摘要（类/方法/文件/行/消息）: `done`（失败退出 1 实测;`--rerun-failed` 落地:失败类入档 `.nop-ib/last-failed.json` 重跑） — deps: 20, 21
- 23. **[Proof]** 漏测 oracle：构造/回放语料，比较"选择集"与"全量测试"的失败集；漏选必须为零或逐条裁定；覆盖反射/DI/数据驱动/资源变更场景: `done`（行为探针 oracle:全量失败集{TestJavaCompileTool}⊆选择集,漏选=0） — deps: 22
- 24. **[Fix]** 与既有测试契约对齐：对照 `02-test-execution.md` 的 cwd/classpath/快照禁用/超时逐条验证，差异即修: `done`（委托形态:cwd/argLine/属性由 Maven 按模块 pom 解释=保真;不注入录制属性） — deps: 21

- ✅ **Milestone M3：`check` 可承担 AI 日常；漏测 oracle 在档**（unlocks when 19–24 done）

### Wave 4 — 常驻与规模化

- 25. **[Feature]** `serve` JSONL 常驻协议：`plan/compile/test/final` 事件、生命周期、崩溃恢复（状态在磁盘）、版本握手: `done`（hello 事件;状态全落盘=崩溃恢复结构性成立） — deps: 22
- 26. **[Feature]** 变更检测优化：文件监听（支持平台）或 stat 扫描；跨 worktree 的 jar ABI/内容哈希缓存共享；构建期写入与用户写入区分: `deferred`（v1 无文件监听,git 变更集为主,见 Deferred） — deps: 25
- 27. **[Feature]** 有界并行与资源治理：模块间并行、测试 fork 复用、`ResourceGuard`/CPU 配额、取消与超时: `deferred`（串行正确性优先,见 Deferred） — deps: 26
- 28. **[Follow-up]** 动态覆盖收窄（仅当 item 6 裁定进入时）：覆盖率映射版本一致性校验、缺失即回退静态图: `deferred`（IB-G4 裁定 v1 不做,触发条件见 Wave0） — deps: 23, 27
- 29. **[Feature]** `bench` 与采用切换：四类指标基线、与 `mvnw` 端到端对照；四类指标基线实测+文档切换: `done`（bench 命令:noop 0.8-1.2s,singleMethod 0.7-1.5s vs 骨架 3.4s;指引=`docs-for-ai/02-core-guides/incremental-build.md`+INDEX/AGENTS.md 路由行;回滚=直接用 mvnw/mvnq） — deps: 27
- 30. **[Feature]** 周期对拍接入 CI（report-only）：不改 CI 门禁，只产出差异报告: `deferred`（仓内无 CI workflow 基础设施,见 Deferred） — deps: 17, 29

- ✅ **Milestone M4：AI 默认走 `nop-ib`；基线在档、可回滚**（unlocks when 25–30 done）

### Wave 5 — L1/L3 扩张（触发式，非默认路线）

- 31. **[Feature]** L1 代码生成模块的"手写源码"快路径：生成基线指纹 + 变更落点判定（生成产物/模型 → 回退；手写源码 → 快路径）: `done`（L1 路由实测:nop-auth-meta xgen→Maven 回退,手写→快路径;_gen 过滤已入 -pl 编译） — deps: 10, 17
- 32. **[Decision]** AOP/注解处理模块的精确路由或永久回退裁定：以当前 138 个激活 `exec-maven-plugin` 的模块、10 个 AOP 叶模块、4 个 `annotationProcessorPaths` 模块为输入；给出每类模块的处置表: `done`（L2 12/L3 10 模块枚举处置=恒回退,见 wave0 evidence） — deps: 31
- 33. **[Proof]** 扩张后对拍与漏测回归：L1 模块抽样对拍 + 选择集漏测 oracle 增量对照: `done`（nop-core 整模块 841/842+1 已知偏差;oracle 见 Wave3） — deps: 31, 32

- ✅ **Milestone M5：L1/L3 处置有数据背书；默认边界稳定**（unlocks when 31–33 done）

## Status values

| Status | Meaning |
|---|---|
| `todo` | 未开始，无 plan |
| `planned` | 已有执行 plan 且通过 draft review |
| `done` | 完成，通过独立 closure audit |

## Hard constraints

1. **零假绿**：未执行的测试不得计为通过；`partial` / `wideImpact` / `fallback` 必须显式出现在人类输出与 JSON；`--max-tests` 默认拒绝。
2. **模型权威**：构建事实只来自 Maven 自身；模型过期即全量，不得"继续但不校验"。
3. **产物语义**：禁止 intra-project jar 进入 fast-path classpath；`-implicit:none`；注解处理器/AOP/生成模块按 L1–L3 回退。
4. **锁纪律**：写产物与测试必须共用 `.mvn/build.lock`；不得绕开 `mvnq` 的并发治理；锁超时退出码与 mvnq 对齐（75）。
5. **不替换构建系统**：CI/发布/`install`/`deploy`/全量冷构建仍由 Maven 承担；`nop-ib` 不得写 `~/.m2`。
6. **证据纪律**：每一波的验收必须有对照数据（对拍/复算/漏测 oracle）；禁止无对照的能力宣称。
7. **文档纪律**：设计变化改 `05` 原地更新；执行状态只在本 roadmap 维护；收口必须走独立 closure audit。
8. **模块边界**：不新增 `-api`；不依赖 `nop-code-service`；不进入发布型模块运行时 classpath。

## Current baseline

- **规模**：465 个 `pom.xml`；11,620 个主源码 `.java`；5,168 个测试类；294 个含 `src/test/java` 的模块；root 44 个直接 `<module>`。
- **生成期耦合**：138 个 pom 激活 `exec-maven-plugin`（根/`nop-kernel` 提供 pluginManagement 配置）；设计 `01 §六` 记录 AOP 产物落在 10 个叶模块；4 个 pom 配 `annotationProcessorPaths`；1 个 `-proc:only`。
- **测试执行现状**：Surefire `forkCount=4`、`reuseForks=true`、`parallel=classes`、`threadCount=1`；测试 JVM `-Xmx1G`、`argLine` 含 `${jacocoArgLine}`；`forkedProcessExitTimeoutInSeconds=120`。
- **并发治理**：`ai-dev/tools/mvnq` + `.mvn/build.lock`（`FileChannel` 排他锁）；per-worktree 本地仓库（`2026-09-21a` 分析）。
- **编译底座**：`nop-javac` 文件式编译已具备（`JavaCompileOptions` + `compileFiles` + 结构化诊断；plan 40 completed）。
- **字节码底座**：`nop-tools/nop-bytecode` 已有 ASM/asm-tree 采集与 CFG，零 nop 运行时依赖。
- **既有 ROI 证据**：`00 §二` 骨架成本、`03 §三` 适用面 63.4%、`04` 的 Gradle 机制分析（ABI/闭包/事务化输出/分析产物持久化）。

## 风险与收缩

| 风险 | 缓解 | 收缩退路 |
|---|---|---|
| 测试选择漏选（假绿） | 静态图保守 + 放宽矩阵 + 漏测 oracle + 首版委托 Surefire | 只做编译快路径，测试仍全量 |
| 模型与 Maven 实际漂移 | 只用 Maven 导出的事实 + 版本哈希 + 过期即全量 | 关闭模型快路径，回退 `mvnw` |
| 与 Maven 并发写产物 | 共用 `.mvn/build.lock` + 压力用例 | 只读模式运行（`plan`） |
| 生成/AOP 语义复刻错误 | L1–L3 回退 + 生成指纹 + 抽样对拍 | 生成模块永久回退 |
| 维护第二套构建语义 | 只补"构建侧没有的依赖分析"；不复制 Maven 配置模型 | 缩小到测试选择单点（`03 §五` 的既有收缩形态） |
| 常驻进程状态腐化 | 状态全部落盘 + 版本握手 + 崩溃恢复 + `cache verify` | 只用一次性 CLI（`--no-daemon`） |


## Deferred（2026-11-14 收口裁定）

| Item | 裁定 | Why Not Blocking | Successor |
|---|---|---|---|
| 26 跨 worktree jar 哈希缓存共享 / 文件监听 | deferred（optimization candidate） | 变更检测走 git 变更集（优于 stat 扫描）,缓存只影响性能 | 无（性能痛点再立项） |
| 27 有界并行与 ResourceGuard | deferred（optimization candidate） | v1 模块串行=输出目录互斥平凡满足,正确性完整 | 无 |
| 28 动态覆盖收窄 | done-as-adjudicated（IB-G4 v1 不做） | 触发条件未满足 | 触发条件见 Wave0 IB-G4 |
| 30 CI 周期对拍 report-only | deferred（out-of-scope improvement） | 仓内无 CI workflow 基础设施 | CI 立项后 |

## 实测效果汇总（更新 2026-11-14 二批）

- **JUnit 执行取代 mvn test**: 自研 fork 启动器（每类隔离 fork+4 并行,对齐 surefire forkCount=4/parallel=classes）默认启用;第三方 junit-platform-launcher 版本自 classpath 推导并自动匹配注入;cwd=模块 basedir/argLine 对齐/快照开关显式禁用/批超时看门狗;nop-javac 全模块 2.4s（vs mvn test 4.3s,无 Maven 骨架）;失败探针退出 1。
- **仓库分层**: 第三方=公共仓只读,项目依赖=reactor 目录,fast path 零仓库写入。
## 实测效果汇总旧（2026-11-14,目标机器 zulu-26/macOS arm64）

- **编译**: 单文件方法体变更 0.7–1.5s（vs `mvnw compile` 骨架 3.4s）;ABI 变更扩散 5 下游模块 11 源文件 1.37s。
- **对拍**: nop-javac 单文件+整模块、nop-core（L1,842 类）整模块 byte 级与 `mvn compile` 零差异（1 个已声明 package-info 轮次偏差,ABI 集合等价,对拍 harness 如实报告）。
- **测试**: `check` 端到端 4.5s（编译+选择集 24 测试）;漏测 oracle 漏选=0;失败退出码 1。
- **noop**: status+plan 0.8–1.2s。
- **正确性门**: 零假绿、I1 禁 intra-project jar、I2 产物缺失失效、I4 删除反查清扫、I6 索引缺失不剪裁。

## 非目标（本 roadmap 不承接）

- 替代 CI/发布构建、远程构建缓存、跨语言构建；
- 复刻 Maven 的全部插件语义（只消费 `model.json` 中与编译/测试相关的事实）；
- 重新实现 `nop-lint` 的实时诊断；
- 修改既有 Maven 插件、codegen 或 AOP 生成链（扩张只在 Wave 5 以"路由/回退裁定"形式出现）。
