# Cycle 1 / I1 — 不变式沉淀：首批门禁（First-Batch Invariant Gates）

> Plan Status: active
> Last Reviewed: 2026-08-12
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1 发现 2 Blocker（门禁① 测试落点模块依赖错误、I0 catalog 不存在却声称已核对）+ 5 Major + 6 Minor，全部修复；round 2 确认 Fix 1/2/4/6/7 到位、verdict: approved（条件：Phase 3 补 pin 集比较 + 局部别名锁两条规则与正例 fixture），条件已全部落定）
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I1；`ai-dev/skills/invariant-loop-audit-prompt.md` 步骤 1b「门禁技术栈」；前置 I0 产出 `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`
> Related: 前置 `2026-08-12-1217-1-nop-stream-invariants-cycle1-I0-inventory-baseline.md`（I0，首批不变式定稿）；后续 I2（跑门禁 → red list）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 1 / I1. 不变式沉淀（首批门禁）

## Purpose

把 I0 定稿的首批 5 条不变式沉淀为**可执行门禁**：① JUnit 5 `@ParameterizedTest` 参数化穷举测试（方法/类表驱动，单文件集中）+ **表完备性门禁**（新增变更型类/方法不入表即红）；② 可静态 grep 的不变式补 `ai-dev/tools/check-nop-stream-invariants.mjs` 扫描器；③ committed 回归测试。门禁入 CI（JUnit 随 `./mvnw test` 运行；mjs 扫描器接入方式在 Phase 3 裁定）。每条门禁必须验证自身有效（正反 fixture），**不在此阶段修复 live defect**——门禁跑出的红归 I2 red list。

## Current Baseline

> 已核对 live repo（2026-08-12）。**重要：I0 catalog 尚不存在**（`ai-dev/audits/nop-stream-invariants/` 目录与 I0 plan 均为待产出状态）——本 plan 的 Current Baseline 对 I0 产出只作"前置依赖声明"，不声称已核对不存在的文件；I0 completed 后、本 plan 执行前，须按 I0 定稿内容复核本 plan 的假设（residual 分类、目标集范围）。

- **前置依赖（硬串行）**：I0（`2026-08-12-1217-1-...`）产出的 `ai-dev/audits/nop-stream-invariants/invariant-catalog.md`（首批 5 条不变式 + 五族审计目标集清单）为本 plan 的输入；**本 plan 执行前 I0 必须已 `completed`**。
- **门禁落点模块（live）**：门禁①（Window round-trip）的测试主体必须落在 **`nop-stream-runtime/src/test`**（`WindowOperatorFactoryImpl`/`WindowOperatorBuilder`/`WindowOperator` 均在 runtime；`nop-stream-core` **不依赖** runtime——`WindowedStreamImpl` 自己靠 `Class.forName("io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl")` 反射加载（`:163`），core 测试无法 import runtime 类）；门禁③ 在 `nop-stream-core`（`CheckpointIDCounter` 在 core）；门禁② 主体在 `nop-stream-core`（TwoPhaseCommitSinkFunction）+ mjs 静态扫描；门禁④ 在 `nop-stream-cep`（Lockable/SharedBuffer/NFA）；门禁⑤ 在 `nop-stream-runtime`（ClusterRegistry 两实现）。
- **测试基建（live）**：`nop-stream-core/pom.xml:26` 与 `nop-stream-runtime/pom.xml:76` 已依赖 `junit-jupiter`（聚合 artifact 含 `junit-jupiter-params`，`@ParameterizedTest` 可用）；`nop-stream-cep` 测试目录已存在（`cep/src/test/.../nfa/sharedbuffer` 等，**已有 7 个既有测试**：`TestLockable`、`TestLockableOverRelease`、`TestSharedBuffer` 等覆盖 over-release fail-fast——门禁④ 须声明与既有测试的关系：在既有反应式测试之上补参数化穷举 + SharedBuffer 条目生命周期对称性，不重复造轮子）。core/runtime 均已有测试包（`datastream`/`checkpoint`/`cluster`/`operators`/`windowing`）。
- **零门禁基线（已实测）**：`ai-dev/tools/check-nop-stream-invariants.mjs` 不存在；`check-nop-stream-audit-manifest.mjs`（1304 行）是 mjs 扫描器的格式/风格先例（仅 JSON/schema 校验，**不是 Java 源码解析器**——静态扫描能力需另行规格，见 Phase 3）；`scan-hollow-implementations.mjs` 亦是先例。
- **CI 现状（live）**：`.github/workflows/maven.yml` 仅 `mvn -B package`（build job）且另有 e2e job 的 `node` 环境先例（可作为 mjs 入 CI 的参考）；JUnit 门禁天然随 `mvn test` 入 CI；mjs 扫描器入 CI 需 Decision（新增 workflow step 或 Maven exec 插件绑定 test 阶段）。
- **live 修复状态与门禁红/绿语义（I0 定稿后复核）**：I0 定稿的 fixed/residual 分类是门禁红/绿的预期基线；**门禁②⑤ 对已知 residual 的语义 = pin-and-record**：测试断言（pin）当前行为，测试绿；red list 记录差异移交 I2；CI 红的触发条件 = 行为偏离被 pin 的当前行为（防回归），而非"已知违背即红"（否则 CI 首日起红，与 Phase 4 全绿矛盾）。已知 residual 候选（I0 定稿后确认）：`InMemoryClusterRegistry.renewLease` 忽略 per-renewal timeout（AR-18）、`TwoPhaseCommitSinkFunction.saveState` 无锁 copy（AR-1）、`JdbcClusterRegistry.registerNode` 写 lease=0（AR-9）。**本 plan 不修任何 residual**，差异项写入 `red-list` 记录移交 I2。

## Goals

- **门禁 ①（Window 构造参数完备性）**：参数化测试验证 `WindowedStreamImpl` 的每个 call-site（apply/aggregate/reduce/process）经 `IWindowOperatorFactory` 到 `WindowOperatorBuilder` 最终 round-trip 到 `WindowOperator` 构造器的字段。**断言映射以 call-site 实参表为准（8 实参）**：windowAssigner→构造器 windowAssigner、trigger→trigger、evictor→evictor、allowedLateness→allowedLateness、function→wrapped userFunction、elementType→accClass/ListStateDescriptor 类型、keySelector→keySelector、keyClass→keyClass（构造器本身 14 参，断言映射表在测试内明示）；测试落在 `nop-stream-runtime/src/test`；表完备性门禁保证新增 `create*Operator` 方法不入表即红。
- **门禁 ②（synchronized 集合迭代）**：JUnit 测试 + mjs 静态扫描验证所有 `Collections.synchronizedMap/List` 字段的迭代点（entrySet/values/keySet/for-each/copy 构造器）都在 `synchronized` 块内；`TwoPhaseCommitSinkFunction` 的 `saveState`/`finishCommit`/`restoreFromEpoch` 迭代点全量入表。**对已知 residual（saveState 无锁 copy）语义 = pin-and-record**（断言当前行为 + 登记 red list 移交 I2），非"首日即红"。
- **门禁 ③（CheckpointIDCounter 原子性）**：参数化并发测试验证多线程 `getAndIncrement` 无重复、无丢失、恢复 `set` 后继续递增（覆盖 I0 定稿的原子性不变式）。
- **门禁 ④（CEP 释放对称性）**：参数化测试验证 `Lockable.release/releaseOrDetach` over-release 必须 fail-fast、双重释放对称性、`SharedBuffer` 条目生命周期（lock 数对称）。
- **门禁 ⑤（ClusterRegistry 多实现一致性）**：参数化测试对 `JdbcClusterRegistry`/`InMemoryClusterRegistry` 两实现跑同一语义场景（**registerNode 后 getActiveNodes 可见性（AR-9 缺陷点）**、renewLease per-renewal timeout（AR-18）、eviction），行为差异**显式断言（pin）并登记（差异本身 → I2 red list）**。
- **表完备性门禁**：断言 = **双向精确相等**（门禁表 == live 反射/源码枚举的变更型方法集，分类器按 I0 定稿标准）；新增类/方法不入表即红，表中有代码不存在的方法亦红。
- **mjs 静态扫描器**：`ai-dev/tools/check-nop-stream-invariants.mjs` 覆盖门禁 ② 的静态部分 + 表完备性检查，风格对齐 `check-nop-stream-audit-manifest.mjs`（静态扫描能力按 Phase 3 规格实现，不套用 schema 校验能力）。
- **门禁入 CI**：JUnit 门禁随 `./mvnw test -pl nop-stream -am` 全量运行；mjs 扫描器接入方式经 Decision 定稿（`.github/workflows/maven.yml` 新增 node step——e2e job 已有 node 20 环境先例，或 Maven exec 插件绑定 test 阶段）。
- **设计文档补「不变式」节**：`ai-dev/design/nop-stream/` 相关文档（`window-design.md`、`checkpoint-design.md`、`cep-design.md`、`01-architecture-baseline.md` ClusterRegistry 节）按 guide Minimum Rules #14 补最终设计的「不变式」陈述。

## Non-Goals

- **不修复门禁跑出的 red list**（属 I4；本 plan 只做 pin-and-record 登记红项移交 I2）。
- **不做对抗探查/盲区探查**（属 I2）。
- **不改公共 API、Operator 接口、模块边界**（结构性重构执行前需人工确认）。
- **不引入 ArchUnit**（当前 pom 未配置；首批门禁用 JUnit + mjs 覆盖即可，ArchUnit 留作后续 Decision）。
- **不改被测类代码**：除门禁测试自身文件外，被测类一律不动（即使已知 residual 如 saveState 无锁 copy、InMemoryClusterRegistry per-renewal timeout，也只 pin 记录不移改）。

## Scope

### In Scope

- 五族首批门禁的 JUnit 参数化测试 + 表完备性门禁（门禁 ① 放 nop-stream-runtime / 门禁 ②③ 放 nop-stream-core / 门禁 ④ 放 nop-stream-cep / 门禁 ⑤ 放 nop-stream-runtime 对应测试包）。
- `ai-dev/tools/check-nop-stream-invariants.mjs` 扫描器（静态部分 + 表完备性）。
- 门禁自验证（正反 fixture：故意违背的样例必须被门禁抓住；JUnit 侧反例经判定 helper 演示）。
- pin-and-record red list 记录文件（已知差异移交 I2 的载体）。
- CI 接入（Decision）+ 设计文档「不变式」节 + `ai-dev/logs/`。

### Out Of Scope

- red list 修复（I4）、裁决（I3）、对抗探查（I2）、全量验证收口（I5/I6）。
- ArchUnit 引入。
- 任何公共 API 变更。

## Execution Plan

### Phase 1 - 门禁基座与表完备性机制

Status: planned
Targets: `nop-stream-runtime/src/test`、`nop-stream-core/src/test`、`nop-stream-cep/src/test`；`ai-dev/tools/check-nop-stream-invariants.mjs`

- Item Types: `Decision | Proof`
- [ ] 裁定门禁测试组织：五族门禁按模块分布（**runtime：①**（round-trip 必须经 `WindowOperatorFactoryImpl` 真实类，测试落 runtime）、core：②JUnit/③；cep：④；runtime：⑤；mjs：②静态/表完备性），每族一个 `TestXxxInvariant` 参数化测试类，方法表用 `@MethodSource` 从静态表驱动
- [ ] **表完备性机制规格落定（双向精确相等）**：门禁表数据源 = I0 审计目标集清单（"变更型"分类器按 I0 定稿标准：public/protected 且改变内部状态的方法，getter/只读不入表）；断言 = **表 == 反射枚举的变更型方法集**（双向精确相等：代码新增方法不入表 → 红；表中有代码不存在的方法 → 红）；JUnit 侧与 mjs 侧共用同一份清单文件（单一事实源，防双表漂移）
- [ ] 建立 mjs 扫描器骨架（`check-nop-stream-invariants.mjs`），先实现"清单完整性 diff"（diff I0 清单 vs live 反射/源码枚举）与"同步检查"两个最小命令，风格对齐 `check-nop-stream-audit-manifest.mjs`
- [ ] 编写门禁自验证 fixture：每个门禁至少 1 个正例（合规代码路径通过）+ 1 个反例（故意违背被抓住）；**JUnit 侧反例通过可测试的判定辅助类（helper）演示**（把"同步性/完备性判定"提取为可单测组件，反例 fixture 作用于 helper，避免在真实类上注入违背代码）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 五族门禁测试类骨架存在（runtime/core/cep 对应测试包内，repo-observable；门禁 ① 在 `nop-stream-runtime/src/test`）
- [ ] 表完备性机制双向验证：a) 向门禁表插入代码中不存在的方法/类 → 红；b) 代码侧模拟新增方法不入表 → 红（用 helper 层 fixture 验证方向 b）
- [ ] mjs 扫描器可运行：`node ai-dev/tools/check-nop-stream-invariants.mjs` 退出码 0（清单一致时）
- [ ] **无静默跳过**：门禁表缺项、扫描器命令缺失均显式报错（非零退出），非静默忽略
- [ ] No owner-doc update required（门禁基座不改变被测行为）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 门禁 ①（runtime 模块）

Status: planned
Targets: `nop-stream-runtime/src/test`（Window 粘合层 round-trip，与 `TestWindowOperatorUnificationE2E` 同包）

- Item Types: `Proof`
- [ ] 门禁 ①：参数化验证 4 个 call-site 经 `WindowOperatorFactoryImpl`（真实类）到 builder 到 `WindowOperator` 构造器的字段 round-trip（8 实参断言映射见 Goals；正例：当前代码全字段传递；反例：任一字段缺失必须被抓住，反例经判定 helper 演示）；`IWindowOperatorFactory` 新增 create 方法必须入表
- [ ] 自验证 fixture：门禁 ① 的反例（如模拟漏传 allowedLateness 的桩 factory）必须被抓住
- [ ] 门禁 ③（core 侧顺带落位）：多线程 `CheckpointIDCounter.getAndIncrement` 并发测试（无重复/无丢失）+ `set` 恢复后递增正确性（`CheckpointIDCounter` 在 core，测试放 `nop-stream-core/src/test/.../checkpoint`）

Exit Criteria:

- [ ] 门禁 ① 测试通过且反例 fixture 可抓住漏传字段（repo-observable）
- [ ] **接线验证**：测试真实经 `IWindowOperatorFactory` 接口调用 `WindowOperatorFactoryImpl`（runtime 类，非 mock 绕过），从 `WindowedStreamImpl` call-site 到 `WindowOperator` 构造器的调用链连通
- [ ] 门禁 ③ 并发测试通过（固定线程数 × 固定迭代数，结果集无重复无缺口）
- [ ] No owner-doc update required（门禁不改被测行为；设计文档「不变式」节在 Phase 4 统一补）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 门禁 ②④⑤（core 静态 + cep + runtime）

Status: planned
Targets: `nop-stream-cep/src/test`（Lockable/SharedBuffer）；`nop-stream-runtime/src/test`（ClusterRegistry）；`nop-stream-core`（TwoPhaseCommitSinkFunction 迭代点）；`ai-dev/tools/check-nop-stream-invariants.mjs`

- Item Types: `Proof | Decision`
- [ ] 门禁 ② JUnit 部分：`TwoPhaseCommitSinkFunction` 全部迭代点（saveState/finishCommit/restoreFromEpoch）入表；**红/绿语义 = pin-and-record**：saveState 无锁 copy（已知 residual）pin **行为语义**（快照内容完整），锁状态交由 mjs 扫描器侧 pin；其余迭代点断言在 synchronized 块内；静态部分由 mjs 扫描器实现（规格见下）
- [ ] **门禁 ② 表范围定稿**：live 中持有 `Collections.synchronized*` 集合的类共 4 个——`TwoPhaseCommitSinkFunction`（core）、`SourceReaderOperator:98`（core）、`StreamSinkOperator:157`（core）、`LocalSourceCoordinator:167`（core）；JUnit 侧点名 2PC 迭代点表，其余三类以 mjs 全量扫描兜底（防止新增 synchronized 集合字段漏网，同时避免 scope 歧义）
- [ ] **mjs 静态扫描规格落定**：① 字段声明识别（`Collections.synchronizedMap/List` 初始化的字段名，扫描范围 = nop-stream-core + nop-stream-cep + nop-stream-runtime 的 `src/main`，不扫测试/示例）；② 迭代语句识别（for-each / iterator / entrySet / values / keySet / copy 构造器 `new TreeMap<>(f)`）；③ **锁对象匹配语义**：`synchronized (字段名)`、`synchronized (this)`、或经**局部变量/方法返回值别名持有同一集合对象**的锁（如 `TwoPhaseCommitSinkFunction` 中 `pending = getPendingCommits()` 局部别名后 `synchronized (pending)`——:101/:158 范本）均合规；④ **pinned-residual 豁免机制**：扫描器输出违规集与 pin 记录文件做集合比较——`违规集 ⊆ pin 集 → 绿`、新增违规 → 红、pin 项消失 → stale-pin 提示（保证 saveState 无锁 copy 首日不红、行为漂移才红，与 pin-and-record 语义一致）；⑤ 误报边界（仅限直接迭代点，不追传递调用）；⑥ 扫描器以 fixture 反例（故意注入无锁迭代样例文件）验证"能红"、**以 live 2PC 正例验证"零误报"**
- [ ] 门禁 ④：**在既有测试之上扩展**（`TestLockable`/`TestLockableOverRelease`/`TestSharedBuffer` 等 7 个已存在，先读后补）：`Lockable` release/releaseOrDetach 参数化序列测试（over-release fail-fast、对称 lock/release）+ `SharedBuffer` 条目生命周期对称性（lock 数与 release 数守恒）
- [ ] 门禁 ⑤：`JdbcClusterRegistry`/`InMemoryClusterRegistry` 同语义场景参数化（**registerNode 后 getActiveNodes 可见性**（AR-9 缺陷点）、renewLease 超时语义（AR-18）、evictExpiredNodes）；两实现行为差异**显式断言（pin）差异存在并登记 red list 移交 I2，不修复**
- [ ] 裁定 mjs 扫描器入 CI 方式（Decision）：a) `.github/workflows/maven.yml` 新增 node step（e2e job 已有 node 先例）；或 b) Maven exec 插件绑定 test 阶段；裁定结果 committed
- [ ] 门禁 ②⑤ 已知 residual 的 pin-and-record 记录文件建立（red list 移交 I2 的载体，含 `文件:行` 证据）

Exit Criteria:

- [ ] 门禁 ② 静态扫描器能抓住故意注入的无锁迭代样例（fixture 反例）**且对 live 2PC 迭代点零误报（正例）**；JUnit 部分迭代点表完整（4 个 synchronized 集合类全部在表或 mjs 扫描范围内）；**saveState 已知 residual 以 pin 语义（行为 pin）登记 red list（未修复）**
- [ ] 门禁 ④ 全部序列测试通过（在既有 `TestLockable*`/`TestSharedBuffer` 之上扩展）；over-release 必须抛 `StreamRuntimeException`（fail-fast 验证）
- [ ] 门禁 ⑤ 两实现参数化场景运行结果登记在案（含 registerNode 可见性、renewLease 语义）；差异项（若有）显式 pin 并标记为 I2 red list，**未在 I1 内修复**
- [ ] **无静默跳过**：门禁 ②⑤ 的已知差异在测试/记录中显式断言（pin）或标注，无吞掉差异的路径
- [ ] mjs 入 CI 方式已裁定并落地（workflow step 或 exec 插件，repo-observable）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 设计文档「不变式」节 + 全量验证

Status: planned
Targets: `ai-dev/design/nop-stream/{window-design,checkpoint-design,cep-design,01-architecture-baseline}.md`；nop-stream 全模块测试

- Item Types: `Proof | Decision`
- [ ] 按 guide Minimum Rules #14（design doc 只记最终设计状态）为门禁 ①②③④⑤ 对应的设计文档补「不变式」节（window-design.md：门禁 ①；checkpoint-design.md：门禁 ②③；cep-design.md：门禁 ④；**门禁 ⑤ 分层落笔**：01-architecture-baseline.md ClusterRegistry 节写架构语义契约（两实现必须语义一致、per-renewal timeout 生效），checkpoint-design.md 只写 lease 与 checkpoint 交互侧）
- [ ] 设计文档「不变式」节与 invariant-catalog.md 交叉引用一致（双向核对，无悬空引用）
- [ ] 全量验证：`./mvnw test -pl nop-stream -am -T 1C` 全绿（含新增门禁测试）
- [ ] 运行 mjs 扫描器全量扫描，输出登记（red list 移交 I2）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 四份设计文档的「不变式」节存在（门禁 ⑤ 按架构/checkpoint 分层落笔，无重复冗余），且与 catalog 交叉引用双向可核
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿（构建命令级验证）
- [ ] mjs 扫描器全量扫描结果登记在案（含已知差异清单移交 I2）
- [ ] **端到端验证**：至少一条既有端到端测试（如 `TestWindowOperatorUnificationE2E` 或 `TestWindowEndToEnd`）在门禁加入后仍通过，证明门禁未破坏从 `addSource` 到 sink 的完整链路
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 五族首批门禁（①②③④⑤ + 表完备性）全部落地为可执行测试/扫描器，且各自反例 fixture 验证有效
- [ ] `ai-dev/tools/check-nop-stream-invariants.mjs` 存在且可运行（清单 diff + 静态迭代点扫描）
- [ ] 门禁跑出的全部差异/红项显式登记（pin-and-record red list）并移交 I2（**无 in-scope defect 被静默降级或忽略**）
- [ ] 表完备性门禁验证：新增变更型方法/类不入表即红（双向精确相等，方向 b 经 helper fixture 验证）
- [ ] 设计文档「不变式」节已补并交叉核对
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿；端到端既有测试通过（Anti-Hollow：门禁未破坏入口→出口链路）
- [ ] **Anti-Hollow Check**：closure audit 验证门禁确实被测试/CI 运行时调用（非只存在不运行），无空方法体/静默跳过
- [ ] 独立子 agent closure-audit 已完成并记录证据（`ai-dev/logs/`）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 受影响 owner docs（设计文档）已同步；`ai-dev/logs/` 已更新

## Deferred But Adjudicated

### 门禁 ②⑤ 的已知行为差异修复（saveState 无锁 copy / registerNode lease=0 / renewLease per-renewal timeout）

- Classification: `watch-only residual`
- Why Not Blocking Closure: I1 的契约是"沉淀门禁 + pin-and-record 登记差异"，差异修复属 I4 且必须经 I2 red list / I3 裁决；若在 I1 内修复会越界 I1/I4 边界。门禁 ②⑤ 对已知 residual 采用 pin 语义（断言当前行为 + 登记），行为漂移才会触发 CI 红，不影响 I1 全绿验收。
- Successor Required: `yes`
- Successor Path: I2（red list 生成）→ I3（裁决）→ I4（修复）

### 门禁表首次跑红（I0 清单遗漏）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 若 I0 目标集清单有枚举遗漏，表完备性门禁首次对 live 代码跑必然红；**首次红 = 补表，属 I1 内职责**（门禁表以 live 代码为准自洽补全并回写 catalog），非表缺失的代码违背才进 red list 移交 I2。
- Successor Required: `no`

### ArchUnit 架构约束门禁

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 pom 未配置 ArchUnit，首批不变式全部可用 JUnit + mjs 覆盖；ArchUnit 引入涉及依赖变更，留作后续 Decision。
- Successor Required: `no`（触发时另立）

## Non-Blocking Follow-ups

- I2 阶段以门禁全量扫描 + 对抗探查验证门禁覆盖盲区（如 refactor 引入新方法、跨 Operator 参数遗漏）。
- I6 收口统计时把门禁数/red list 数作为 Cycle 1 度量。

## Closure

Status Note: 完成后填写。
Completed: （待定）

Closure Audit Evidence:

- Reviewer / Agent: （待定，独立 fresh session）

Follow-up:

- no remaining plan-owned work（I1 产出移交 I2；差异清单见 Deferred But Adjudicated）
