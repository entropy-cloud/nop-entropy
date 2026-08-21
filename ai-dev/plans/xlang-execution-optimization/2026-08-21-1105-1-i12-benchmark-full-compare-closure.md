# I12 性能基准 + 全量三后端对拍收口 + 独立 closure audit

> Plan Status: active
> Last Reviewed: 2026-08-21
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I12 定稿条目（L82-85：范围/验收/复用——**全量三后端对拍套件直接运行全绿，不得以引用形式弱化** + 基准数据落 repo）；I11 移交六项 = `2026-08-21-0450-2-i11-build-integration-native-docs.md` Execution Notes §14（执行时以 live 落地为准复核）；Q1/Q4 设计锚点 = `ai-dev/design/xlang-truffle/02-architecture-baseline.md` §八（L142）/§九（L158/L161/L167-168——watch-only 行 + "量化标准在 I7[按映射读 I12] 基准计划中定义"移交：**量化口径载体 = 本 plan 及其产物，设计文档冻结不改**，roadmap L42 冻结纪律）；I8 调优移交 = 设计 truffle 02 §五"本层不发明数值"裁定 + 本 plan Current Baseline 配置锚点
> Mission: xlang-execution-optimization
> Work Item: I12
> Related: I11（前置：构建集成/双清单产物/e2e fixture 模块/移交六项——本 plan 直接消费其生产形态与移交记录，为 I12 唯一前驱）

<!-- Draft review: round-1（fresh session ses_fddb30c37ffe8G6zBb4iiWREaX，Verdict Not Ready：0 Blocker / 5 Major / 5 Minor——M1 三向对比语料集合与"java 生产绑定形态"供给矛盾（e2e main 仅 7 生产单元 vs corpus 48，D2 无必答）/ M2 benchmark main 域消费 corpus/harness 的依赖形态未裁定（仓内无 main 域 test-jar 消费先例）/ M3 Phase 3 回写"冻结"设计文档与 roadmap 冻结纪律冲突无裁定（设计 §九 L167 自述量化载体=I12 plan 非设计回写）/ M4 GraalVM 形态仅预置降级出口无环境获取尝试决策项（无替代下界）/ M5 D1 载体列驱动器可得性断层（列驱动器均在各模块 test 源码且无 test-jar 发布，"增 truffle test 依赖"不充分）；m1 exec-maven-plugin 先例误述（live=main() runner）/ m2 工作区无 JDK 21（live Zulu 26.0.1）/ m3 夹具计数 live=50 非 49 / m4 roadmap 状态机时序（planned 应在 draft review 通过时非 Phase 3）/ m5 池/缓存梯度参数化机制未约束 → 修复：D1 增列驱动器来源必答（载体内再实现：harness public API + 两后端 main API，生产绑定形态免 nop-javac；显式拒绝两内核模块增 test-jar 发布）；D2 增两条必答（语料集合=主批次 e2e 生产形态全集 + 可选扩充经 gen task 落盘路径；依赖形态=静态走 e2e main classpath、动态自带语料定义直连 XLang.execute，禁 main 域 compile-scope test-jar）+ 梯度机制约束（显式构造参数化，不走配置键多进程）；M3 裁定量化口径落基准报告专章 + roadmap I12 条目回链，设计 truffle 02 保持冻结（设计 §九 L167 既定载体 = I12 plan/其产物）；M4 增环境获取尝试决策项（ask-first 征得同意安装 GraalVM JDK，尝试后不可得才裁定缺席，Exit/Gate 区分"尝试后不可得"与"未尝试"）；m1-m5 全部顺手修复（JMH main() runner 先例 / live JDK 版本记录 / 夹具 50 / 状态机时序 todo→planned=本 plan active 时、done=closure audit 后 / 梯度机制约束并入 D2）→ 待 round-2 复审。 --> round-2（fresh session ses_fdda75809ffe5fIQCqS9gw6Z2w，Verdict Not Ready：1 Blocker / 0 Major / 6 Minor——B1 Phase 1 侧语料供给断层残留（D1 java 列生产绑定要求全 corpus，但 corpus 48 单元生成类不在任何 main classpath，夹具 50 类困在 nop-xlang-java test 源码，D1 又拒 test-jar 发布 → 严格执行则 java 列 41 单元缺席或兜底解释器，"静态 48 计数"不可满足）；m2' TranslationCache 无运行时注入缝（梯度机制表述半真）/ m3' 用例②"JIT 生效"括注误导 stock 形态 / m4' Q1/Q4 锚点在 Phase 2 无对应测量项 / m5' Targets 行"test-jar 消费"与拒绝发布裁定字面冲突 / m6' Gate 行 e2e 重复计入 / m7' Phase 1 Item Types 缺 Fix → 修复：B1 采方案(a)——D1 增"java 列全 corpus 供给"必答裁定 = corpus 48 单元拷贝至 e2e `_vfs`（main resources）+ I11 gen task 重跑产物提交（manifest 扩全集）+ 三列同资源路径驱动 + 反漂移字节一致断言（单一事实源留 nop-xlang）+ 物化前置 fail-fast 上报 + Non-Goals 澄清（"corpus 扩充"=新增语义单元，不禁既有单元物化）；Phase 2 D2 语料主批次改为物化后全集（原"条件扩充"转正）；m2' 梯度机制分列（池=open(int) 真实路径内 / 缓存=TranslationCache(Integer) 隔离基准 + in-situ 经配置键多实例）；m3' JIT 括注限定 GraalVM 形态 + stock 标注解释执行稳态；m4' 增用例⑤ Q1/Q4 锚点数据供给（占比可复算数据行 + 翻译成本/缓存收益）；m5'/m6'/m7' 文本与 Item Types 修正 → round-3（fresh session ses_fdd9b7741ffeD4QodvK4PWhdiM，Verdict Not Ready：0 Blocker / 1 Major / 3 Minor——前两轮 17 项修复全部复核到位；P1 套件三列驱动 output mode 供给未裁定（gen task 硬编码 html vs corpus per-unit mode：text×1/xml×2/node×2——per-unit 驱动则指纹失配红灯且会被物化 fail-fast 出口误分类、统一 html 则 5 单元变体树名实偏差未记录）；p2 e2e 计数断言（3 处 =7）物化后必红、适配项未预列；p3 JMH 1.33 与 live JDK 26 兼容未要求冒烟；p4 缓存 in-situ 梯度"多实例"误导（配置源 JVM 内全局快照，仅顺序 run 可行）→ 修复：D1 增"output mode 供给"必答裁定（三列统一生产形态 html 模式驱动 + 5 单元 html 变体树显式记录 + 原 per-unit mode 覆盖归既有列测试 + 不扩展 gen task per-unit mode）+ 物化前置失败两分类（转译 fail-fast vs 指纹失配降级，分别上报禁止混同）；Phase 1 预列计数断言机械适配（7→55 纯增量）+ Exit 增记录项；D2 先例措辞改为"JMH + main() runner 形态非版本数字" + 首跑冒烟/升版决策 + mode 口径同 D1 + in-situ 缓存梯度改"顺序 run、不可并行"→ round-4（fresh session ses_fdd912e48ffe4i6fI3paJHOcc4，Verdict Not Ready：0 Blocker / 1 Major / 3 Minor——前三轮 22 项修复全部复核到位；P1 html 统一模式裁定残留 expectation-oracle 缺口（harness 对每列硬断言声明 ExpectedOutcome 含输出缓冲调用序列，5 个非 html 单元既有预期为 per-unit mode 语义，html 变体树执行确定性失配且无适配预列——临场豁免即静默弱化三层断言）；p2 Related 行模板乱码残留；p3 基准解释器列路由旁路未裁定（静态语料经 choke point 被路由 java 后端，无身份核验则静默测错后端污染机会成本数据）；p4 Q1 数值判据未绑定 GraalVM 形态（stock 上占比平凡 ~100%，缺席时须显式降级）→ 修复：D1 增"html 变体单元预期值适配"必答预列（按生产形态 html 输出重声明预期，oracle=解释器列实测输出起草 + 三列交叉验证固化，禁止临场豁免/削弱）+ 适配预列扩为两项（计数 7→55 + 5 单元预期值）；Related 行改写；D2 增解释器列路由旁路裁定（force-interpreter/显式直驱 + 解释器列身份核验）+ Exit 端到端验证扩为三列身份核验；Q1 数值判据限定锚定 GraalVM 形态数据行 + D3 缺席时显式降级（维持生态判据 + 量化推迟记录）→ round-5（fresh session ses_fdd86d393ffeGSpkvNXHjmlUvb，Verdict Not Ready：0 Blocker / 1 Major / 2 Minor——前四轮 26 项修复全部复核到位；F1 预期值适配缺 loc 路径重映射维度（harness 对 errorLocation path 全等断言 + 输出调用 loc——套件以 e2e `_vfs` 路径驱动，5 个静态异常单元三列确定性红灯，I10 remapExpectation 先例即因路径差异而生）；F2 "仅 5 单元变体"经验前提未覆盖 3 个 TAG_NONE collect 单元外层模式变化（live 未验证）；F3 Phase 1 解释器列取树经 parseXpl 会命中 I10 D3 绑定 hook 拿 bound 树（静默执行生成类），且 Phase 1 Exit 身份核验缺解释器列（与 Phase 2 口径不一致）→ 修复：D1 增"loc 路径重映射"必答预列（复用/复刻 remapExpectation/remapOutputCall 先例）+ 适配预列扩为三项（计数/5 单元 oracle/loc 重映射）；适配集改为"以实测红/绿界定 + 3 个 TAG_NONE collect 显式核验点"；D1 增"解释器列取树路径"必答裁定（parseClean 干净解析绕开绑定 hook）+ Phase 1 Exit 接线验证扩为三列身份核验（与 Phase 2 同口径）→ round-6（fresh session ses_fdd7c304fffe6VO1YatbCAPuSM，Verdict **Ready**：0 Blocker / 0 Major / 2 Minor——前五轮 31 项修复经独立 live 复核全部真实到位，round-5 三项修复闭环完整（三项机械适配预列覆盖面专项验证：计数 7→55 / 5 单元 html oracle / loc 重映射无遗漏单元）；Mi-1 SCRIPT 单元等价性根据未记录（有兜底纪律覆盖）/ Mi-2 negatives 排除断言零改动未显式提及（零风险观察项）→ 两项 Minor 当场吸收（SCRIPT 等价性根据入 D1 + negatives 保持不变入 Exit）→ 共识达成 → active。 -->

## Purpose

双后端 roadmap 的收口条目，两个交付面：**(1) 全量三后端对拍套件直接运行收口**——解释器/java/truffle 三列全 corpus（静态/动态列适用性 + 后端身份断言 + 列缺席显式记录）经单一可复跑入口**直接执行**全绿，不得以引用 I1-I11 历史结果的形式弱化（roadmap 验收原文）；**(2) 三后端性能基准与量化收口**——解释器/java/truffle × stock JVM/GraalVM JVM 两形态基准（含 Context 池创建/销毁成本与池大小调优实测、静态资源 JVM 形态生成物缺失不借道 truffle 的机会成本量化、Q1/Q4 watch-only 重评估触发口径量化定义）+ 基准数据落 repo（报告 + 可复跑入口）+ mission.json commands 汇总口径核验。I11 §14 移交六项逐项对账处置。plan 关闭经独立 fresh closure audit（roadmap 条目名内含）。

## Current Baseline

- **roadmap 状态（live）**：I1-I11 全部 `done`，I12 为阶段二唯一 `todo`；Milestone「双后端落地」`todo`（派生 I1-I12 全 done）——本 plan 为该 milestone 的最后实现条目。
- **全量对拍套件既有入口（live 测试类，均绿于 I11 收口基线 551+493+584+5）**：
  - 解释器基线列 ×3：`TestCorpusV1InterpreterBaseline` / `TestCorpusCoverageAInterpreterBaseline` / `TestCorpusCoverageBInterpreterBaseline`（`io.nop.xlang.compare`，随 nop-xlang test-jar 发布）
  - java 列 ×3：`TestCorpusV1JavaColumn` / `TestCorpusCoverageAJavaColumn` / `TestCorpusCoverageBJavaColumn`（nop-xlang-java）
  - truffle 列 ×3 + 并发：`TestCorpusV1TruffleColumn` / `TestCorpusCoverageATruffleColumn` / `TestCorpusCoverageBTruffleColumn` / `TestCorpusConcurrentTruffleColumn`（75 用例，nop-xlang-truffle）
  - 生产路径列：`TestProductionBindingRoutingScenarios`（I10，corpus 48 静态单元生产绑定）、`TestEndToEndGeneratedBinding`（I11 e2e，5/5）、`TestJavaBackendRoutingScenarios`/`TestTruffleBackendRoutingScenarios`（I9 五类场景）
  - 覆盖矩阵 ×2：`TestExecTranslationCoverageMatrix` / `TestTruffleCoverageMatrix`
  - **Gap**：上述入口分散于四个模块、无单一聚合入口，且无"新增 corpus 单元不得静默漏跑"的完整性防漏断言——"直接运行收口"即本 plan 验收主体。
- **corpus live**：`nop-kernel/nop-xlang/src/test/resources/xlang-compare/` = `static/` 11 + `static-a/` 20 + `static-b/` 17 = **48 静态单元** + 动态形态单元（v1/A/B 列测试内构造，非独立文件）；I10 D6 夹具经 I11 增 xlib 标签夹具后 live = **50 类**（48 静态 + 租户 + 标签）。注意：夹具类在 nop-xlang-java **test 源码**，corpus 48 单元的生成类/清单条目**不在任何 main classpath**——main classpath 生产形态单元 = e2e 模块 7 单元（见下）。
- **列驱动器 live 事实**：`JavaBackendColumn`/`JavaBackendIdentityRule`（nop-xlang-java）与 `TruffleBackendColumn`/`TruffleBackendIdentityRule`（nop-xlang-truffle）均在各模块 **test 源码**，两模块均无 test-jar 发布；`nop-javac` 为 nop-xlang-java test-scope 依赖（不传递）；`ExecCompareHarness.registerColumn` 为 public API 随 nop-xlang test-jar 发布；仓内 test-jar 消费者全部为 test scope（**无 main 域 compile-scope test-jar 消费先例**）。
- **三后端可用形态（I11 §14(1) 移交口径，live 核验）**：解释器 = 缺省；java = 构建任务产物 + classpath 双清单自动装载（`nop-xlang-java-e2e` 参考实现——main jar 含已提交生成类 `io/nop/xlang/gen/` 7 单元（template/expr/control/exception/xlib 标签等）+ `META-INF/nop-xlang/` 双清单，live 证实）；truffle = 模块在场即注册（I8 池运行时，缺省池大小 `availableProcessors`）。
- **mission.json commands（live）**：`test`/`build`/`lint`/`typecheck` 四条均为 `-pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle,:nop-xlang-java-e2e -am` 口径——**汇总口径核验对象**；如本 plan 落盘 benchmark 模块，须同次变更追加（W3"模块落盘即切换"裁定）。
- **基准先例（live）**：`nop-benchmark/nop-benchmark-xpl` JMH 1.33——接线形态 = **main() 方法 JMH runner**（`TestTemplateEngine`/`TestReflection` 先例，无 exec-maven-plugin）；根 pom reactor 含 `nop-benchmark` 模块。**Gap**：无任何三后端（解释器/java/truffle）基准模块与数据。
- **Q1/Q4 watch-only 锚点（设计冻结文本）**：truffle 02 §九 L158（Q1 Bytecode DSL——"官方 bytecode_dsl 生态成熟或 AST 解释开销占比显著时重评"，"显著"未量化）、§八 L142 + §九 L161（Q4 与 nop-js 共享 Engine——前置条件=编译线程预算基准数据）、L167-168（量化标准"**在 I7[=I12] 基准计划中定义**"——既定载体 = I12 plan 及其产物，**非设计文档回写**；roadmap L42 设计冻结纪律）。**Gap**：触发口径无数值定义、无承载文档。
- **I8 调优移交锚点（live 配置）**：`CFG_TRUFFLE_CONTEXT_POOL_MAX_SIZE`（`nop.xlang.truffle.context-pool.max-size`，缺省 `availableProcessors`）、`CFG_TRUFFLE_TRANSLATION_CACHE_MAX_ENTRIES`（缺省 1024）——创建/销毁成本实测与数值调优显式归 I12（设计 §五"本层不发明数值"）。池与缓存均可显式构造参数化（`XLangContextPool.open(int)` / `TranslationCache(Integer)` 形态），梯度基准无需多进程。
- **环境事实（live）**：工作区无 GraalVM 发行版（无 graalvm JDK/`native-image`/`GRAALVM_HOME`，I11 live 核验在案 + 本 plan 起草复核）；live JDK = Zulu 26.0.1（另有 OpenJDK 25.0.1，无 JDK 21）——stock JVM 形态以运行时实际 JDK 记录（vendor/version 入报告）；GraalVM JVM 形态需环境获取尝试（Phase 2 D3）或显式裁定。
- **前任 plan follow-up 链（本 plan 输入）**：I5-I11 Q1/Q4 watch-only 量化归 I12（延续链）；I8 池/缓存调优归 I12；I11 §14(6) rollout 前置条件为 **I12+ 治理输入**（记录移交去向，非本 plan 实施）；I3/I4 优化候选（`XLangSemantics.invokeGlobalFunction` display 急切构造、`ClassModel.getConstructorForArgs` 多候选）维持原分类，仅可消费本 plan 数据。

## Goals

- 全量三后端对拍套件有**单一可复跑入口**，直接执行三列全 corpus（三层断言 + 身份断言 + 列适用性 + 列缺席显式记录）并全绿；含完整性防漏机制（corpus 新增单元不得静默漏跑）。
- 三后端性能基准落地：JMH 载体 + stock JVM 全量数据 + GraalVM JVM 形态（真实运行或环境不可得显式裁定 + 复跑入口）；基准报告与可复跑入口落 repo。
- Context 池创建/销毁成本、池大小与翻译缓存容量敏感性实测数据在案；缺省值裁定（保持或调整）有数据支撑。
- 静态资源 JVM 形态"生成物缺失不借道 truffle"的机会成本有量化数据支撑。
- Q1/Q4 watch-only 重评估触发口径量化定义落入基准报告专章（数据锚定）+ roadmap I12 条目回链；设计文档保持冻结（设计 §九 L167 既定载体 = I12 plan 及其产物）。
- mission.json commands 汇总口径核验通过；I11 §14 六项移交逐项对账处置。

## Non-Goals

- 全仓 `_vfs` rollout 治理（I11 §14(6)：分模块推进顺序、xpl 片段排除机制、`@node` 变体键、全量生成产物落盘）——I12+ 平台推广治理，超出本 roadmap 范围（移交去向显式记录）。
- 转译器/翻译器支持集扩展（含 `ExecutableFunction` 内联调用节点形态——rollout 前置条件之一，归后继裁定）。
- 性能优化实施（含 I3/I4 共享 helper 优化候选——本 plan 仅产出量化数据作为其输入，不实施优化）。
- Q1/Q4 重评估的实施本身（本 plan 仅定义触发口径；若实测数据满足触发条件，重评属后继决策）。
- native-image 真机构建基准（I11 已裁定环境不可得 + 复跑入口在案；本 plan 基准域 = JVM 两形态）。
- 不修改生产路由/决策树语义、对拍不变式口径、模块依赖方向（roadmap 纪律 3/5 保持）。

## Scope

### In Scope

- 全量对拍套件聚合入口 + 完整性防漏断言 + 新鲜直跑证据（三列全 corpus，非引用形式）。
- 三后端 JMH 基准载体落盘（`nop-benchmark` 下新模块或既有模块扩展，形态由 Phase 2 D2 裁定）与全部基准用例：静态单元三向对比（语料 = e2e 生产形态全集，Phase 1 物化供给）、动态单元对比（truffle 稳态）、机会成本量化、池创建/销毁成本、池大小与缓存容量敏感性、Q1/Q4 锚点数据（AST 解释开销占比 + 翻译成本/缓存收益）。
- 基准报告（数据 + 环境 + 结论 + Q1/Q4 量化口径专章）与可复跑入口落 repo；GraalVM 形态环境获取尝试（D3）与裁定（如适用）。
- 池/缓存缺省值裁定（保持或数据支撑的调整 + 回归）。
- mission.json commands 汇总口径核验（含落盘模块同次切换）；I11 §14 六项移交逐项对账处置；roadmap I12 条目回链。

### Out Of Scope

- rollout 全仓铺开及其前置治理项（见 Non-Goals，移交去向记录于 Non-Blocking Follow-ups）。
- 支持集扩展、优化实施、native 真机基准、Q1/Q4 重评实施。
- corpus 语义扩充与既有列测试语义变更（套件聚合不改各列断言语义；**"corpus 扩充"指新增语义单元——不禁止 Phase 1 D1 对既有 corpus 单元的生产形态物化落盘（拷贝 + gen task 产物，原件零改动）**）。

## Execution Plan

### Phase 1 - 全量三后端对拍套件直接运行收口

Status: completed
Targets: `nop-kernel/nop-xlang-java-e2e`（载体 + corpus 生产形态物化落点）、`nop-kernel/nop-xlang` test-jar 消费 + `nop-kernel/nop-xlang-java`/`nop-kernel/nop-xlang-truffle` 模块依赖（main API）、`missions/xlang-execution-optimization.json`

- Item Types: `Decision | Fix | Proof`

- [x] **D1 套件形态、载体与 java 列全 corpus 供给裁定**：单一可复跑入口（聚合测试类或显式套件定义）落现有四模块内（不新建运行模块，经 mission.json `test` 命令直达）。硬约束：①三列（解释器/java/truffle）全 corpus **直接执行**——逐单元经 I1 对拍 harness 三层断言 + 后端身份断言（java 列=生成类实例入口、truffle 列=翻译 AST 经 CallTarget），不得引用既有列测试结果替代；②静态/动态列适用性 + 列缺席显式记录（缺席不算通过；动态单元 java 列缺席为既定适用性约定）；③完整性防漏——套件枚举 corpus 全部单元（48 静态 + 动态形态清单）断言逐单元被各适用列执行，corpus 新增单元漏跑即红灯；④java 列 = 生产绑定路径形态（双清单装载 + `Class.forName`，非测试直驱/非内存编译）。**java 列全 corpus 供给（必答裁定）**：corpus 48 单元的生成类不在任何 main classpath（夹具 50 类在 nop-xlang-java test 源码）——裁定为**corpus 生产形态物化落盘**：将 corpus 48 静态单元拷贝至 e2e `_vfs`（如 `_vfs/test/xlang/e2e/corpus/` 下保持 static/static-a/static-b 结构，main resources），经 I11 gen task（e2e 模块 generate-test-resources 既有 execution）重跑生成 + 产物提交（`_gen` 类 + 双清单扩至全集，manifest 覆盖全部 corpus 单元；物化不手改 `_` 前缀产物）；套件内**三列同资源路径**驱动（解释器直 parse / java 列生产绑定 / truffle 列翻译，均指向 e2e `_vfs` corpus 资源，动态单元仍为测试内构造语料）——辅以**反漂移断言**（e2e corpus 拷贝与 nop-xlang test-jar 原件逐文件字节一致，漂移红灯；单一事实源保持在 nop-xlang）。物化前置：全部 corpus 单元经 gen task 可生成——**失败两分类显式区分**：(i) 转译 fail-fast（单元含不支持形态）与 (ii) 指纹失配降级（stale/mode 失配——WARN + 解释器兜底，身份断言红灯）根因不同、处置路径不同，如发生分别显式上报裁定，禁止混同或静默剔除。**套件三列驱动 output mode 供给（必答裁定）**：live 事实——gen task 对全部 xpl 统一 `XLangOutputMode.html` 取树（`XPL_OUTPUT_MODE` 常量），而 corpus 测试侧为 per-unit mode（TAG_TEXT→text ×1 / TAG_XML→xml ×2 / TAG_NODE→node ×2，其余 43 单元 none）；裁定 = **三列统一以生产形态（html 模式，与 gen task/运行时生产取树一致）驱动物化资源**——物化套件的 java 列绑定指纹与解释器/truffle 列取树同模式、内部自洽；5 个非 html 语义单元在套件中为 **html 变体树**（显式记录于套件/报告，原 per-unit mode 语义覆盖由既有列测试保持——全家桶同轮运行仍在）；3 个 TAG_NONE collect 单元（外层 none 模式 → html 文档模式的行为变化 live 未验证）为**显式核验点**——适配集以实测红/绿界定，预列集合之外的失配单元按同纪律适配并记录（43 单元中 40 个 SCRIPT 单元以 `<c:script>` 为根、编译期标签与文档 outputMode 无关，html 统一驱动下树等价——如个别单元标签外存在非空白文本则落入兜底纪律按实测红/绿处置）；**不扩展** gen task 为 per-unit mode（改 I11 main 代码，超出本 plan 最小变更）。**html 变体单元预期值适配（必答预列）**：I1 harness 对每列执行均断言单元声明的 `ExpectedOutcome`（含输出缓冲完整调用序列）——5 个非 html 单元的既有预期为 per-unit mode 语义，html 变体树执行必失配；裁定 = 套件内为这 5 单元**按生产形态 html 输出重新声明预期值**（oracle 产生方式 = 解释器列 html 模式执行的实测输出起草，经三列一致性交叉验证后固化于套件代码，适配过程记录于 Execution Notes/报告）；**禁止**临场豁免或削弱三层断言任何一层。**loc 路径重映射适配（必答预列）**：harness 异常语义断言含 errorLocation **path 全等**比对、输出调用序列含 loc——套件以 e2e `_vfs` 路径驱动，与 corpus 原路径（`xlang-compare/...`）必不相同；裁定 = loc 承载预期（异常 errorLocation + 输出调用 loc）按套件驱动路径**重映射**（复用/复刻 I10 `ProductionBindingCorpus.remapExpectation/remapOutputCall` 先例——该先例存在即因"预期 loc path 须与驱动资源 path 一致"），非削弱、适配理由记录于 Execution Notes。**列驱动器来源（必答裁定）**：java/truffle 列驱动器在各自模块 test 源码且无 test-jar 发布——裁定为**载体内再实现**（载体 `nop-xlang-java-e2e` test scope：经 nop-xlang test-jar 的 `ExecCompareHarness` public API 注册三列 + 两后端 **main** API（生产绑定 binder / truffle engine+池）驱动；e2e 增 nop-xlang-truffle test 依赖；生产绑定形态免 `nop-javac`；再实现的列驱动器必须复刻身份断言语义）；**解释器列取树路径（必答裁定）**：`XLang.parseXpl` 为 I10 D3 绑定 hook 单点（清单成员返回 bound 包装树）——套件 JVM 内 java 后端启用且双清单装载，解释器列必须经**干净解析**取树（`parseClean` 先例）或等价绕开绑定 hook，并附解释器列身份核验（非 bound 执行体）；**显式拒绝**为 nop-xlang-java/nop-xlang-truffle 新增 test-jar 发布（内核模块发布面扩大，违反最小变更）。
- [x] 按 D1 实现聚合入口与防漏断言（新代码遵循 No Silent No-Op：防漏枚举失败显式失败，非跳过）。
- [x] 按 D1 执行 corpus 生产形态物化（拷贝 + gen task 重跑 + 产物提交 + 反漂移断言测试；重生成幂等以 I11 check 哨兵核验）。**可预见机械适配预列**：(i) e2e 既有 `TestEndToEndGeneratedBinding` 的 3 处计数断言（scan 清单/units/sources 各 =7）随物化 7→55 纯增量同步；(ii) 5 个 html 变体单元的套件预期值按 D1 裁定重声明；(iii) loc 承载预期（异常 errorLocation + 输出调用 loc）按套件驱动路径重映射——均非削弱，适配理由记录于 Execution Notes。
- [x] **新鲜直跑证据**：mission.json `test` 命令原样运行，聚合套件 + 既有矩阵/路由/生产绑定/e2e/并发全家桶同轮全绿，逐入口计数与命令、环境记录（Phase 2 报告承载）。
- [x] mission.json 现行四条 commands 汇总口径核验（live 可运行 + 覆盖套件入口；benchmark 模块追加归 Phase 2 同次变更）。

Exit Criteria:

- [x] 聚合入口存在且单一（repo-observable：具体测试类/套件定义 + 文档化复跑命令），直接执行三列全 corpus，非引用形式——断言结构可审查（三层/身份/列缺席）。
- [x] corpus 生产形态物化 repo-observable：e2e `_vfs` corpus 资源集 + 双清单条目覆盖全部 48 静态单元 + 生成产物提交（gen task 产出，check 哨兵零漂移）+ 反漂移断言测试在案（与 nop-xlang 原件字节一致）+ **5 个非 html 语义单元按 html 变体树驱动 + 3 个 TAG_NONE collect 核验点的事实与适配集显式记录**（套件/报告内；原 per-unit mode 覆盖由既有列测试保持）+ loc 路径重映射落地 + e2e 计数断言适配（7→55）落地（物化只增 xpl 资源，negatives 排除断言零改动、保持不变）。
- [x] 完整性防漏断言有效并有红/绿对照证明（人为增删 corpus 单元清单 → 套件红灯；恢复 → 绿灯）。
- [x] 新鲜直跑记录：三列全 corpus 计数（静态 48 + 动态形态单元数）+ 同轮全家桶入口计数，落基准报告或当日 log。
- [x] **接线验证**：聚合套件真实调用三个后端执行体且**三列各有身份核验**（java 列执行体=生成类、truffle 列=CallTarget、解释器列=非 bound 执行体（干净解析取树）），非仅类型存在。
- [x] **无静默跳过**：列缺席/防漏枚举异常路径显式失败（FAIL-not-SKIP），有负测试或红/绿对照。
- [x] mission.json 现行 commands 核验记录在案。
- [x] owner-doc 裁定：套件入口若改变使用者可感知的开发/验证流程 → docs-for-ai 对应小节更新；否则显式 `No owner-doc update required`（裁定写入 Execution Notes）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 三后端性能基准 + 两形态运行 + 数据落 repo

Status: planned
Targets: `nop-benchmark/`（新模块或扩展，D2 裁定）、`missions/xlang-execution-optimization.json`、基准报告（`ai-dev/analysis/2026-08/`，路径 D2 定稿）

- Item Types: `Decision | Fix | Proof`

- [ ] **D2 基准载体与语料裁定**：载体 = `nop-benchmark` 下新 JMH 模块（复用 `nop-benchmark-xpl` JMH 先例——**先例 = JMH + main() runner 接线形态，非版本数字**；首次运行前在 live JDK 上做 JMH 冒烟，1.33 不兼容则升版钉线并记录决策）或既有模块扩展。**语料集合（必答裁定一）**：静态单元三向对比语料 = e2e 模块 main classpath 生产形态单元**全集**（Phase 1 D1 物化后的 corpus 48 单元 + e2e 原生 7 单元；与套件同一供给，I11 §14(5) 移交口径"e2e fixture 单元的 java 列 vs 解释器列耗时对比"据此超集满足）；**mode 口径同 D1 裁定**（全部按生产形态 html 模式，5 个非 html 语义单元为 html 变体树、显式记录）；如个别单元形态不适合耗时基准（如异常单元以抛异常为主路径），基准语料子集裁选显式记录（套件侧仍全量，不受裁选影响）。**依赖形态（必答裁定二）**：benchmark 为 main 域 JMH 载体——静态语料经 e2e 模块 main classpath 消费（生产形态闭环）；动态语料 = benchmark 载体**自带语料定义**、直连 `XLang.execute` choke point 真实出口；**禁止 main 域 compile-scope test-jar 消费**（corpus/harness 均随 nop-xlang test-jar 发布、仓内无 main 域消费先例——静态语料复用经 Phase 1 物化路径供给，不走 test-jar）。**解释器列路由旁路裁定**：静态语料为清单成员、经 choke point 会被决策树路由到 java 后端——基准中"解释器执行"列必须显式旁路（`force-interpreter`（I9 诊断开关）或等价显式直驱取树执行）并附**解释器列身份核验**（执行体非生成类入口、非翻译 AST CallTarget——防静默测错后端污染三向对比数据）。**梯度机制约束（分列，机制不同）**：池大小梯度经 `XLangContextPool.open(int)` 在真实运行时路径内参数化（单 JVM 多 run 可行）；翻译缓存容量梯度经 `TranslationCache(Integer)` 显式构造做**隔离基准**（直接驱动翻译/装载测容量-LRU-再翻译成本——live 事实：`XLangLanguage` 内 cache 为无参构造私有字段、无运行时注入缝），如需 in-situ 形态则经配置键构造独立 Language/Engine 实例（**顺序 run**：改配置→新实例→跑一轮；配置源为 JVM 内全局快照，不同容量实例不可并行）；两者分别支撑 Phase 3 缺省值裁定。依赖方向约束：benchmark → 被测模块单向，禁止内核模块新增依赖边（roadmap 纪律 5）。
- [ ] **JMH 基准用例落地**（按 D2）：①静态单元三向对比——同批语料（D2 裁定一全集，必要时含子集裁选记录）：解释器执行 vs java 生产绑定形态执行（I11 §14(5) Q1 量化输入）vs truffle 执行（机会成本三向量化——"静态资源 JVM 形态生成物缺失不借道 truffle"的依据数据）；②动态单元对比——解释器 vs truffle 经池运行时稳态（预热后；**JIT 生效形态仅在 GraalVM 形态成立**——stock JVM 上 truffle 为解释执行稳态，报告数据行按形态显式标注）；③Context 池创建/销毁成本 + 池大小敏感性梯度（现缺省 `availableProcessors` 邻域）；④翻译缓存容量敏感性（现缺省 1024 邻域，隔离基准 + 翻译单成本）——③④为 I8 移交调优实测；⑤Q1/Q4 锚点数据供给——truffle 稳态 AST 解释开销占比测量（经 JMH profiler 或等价采样产出**可复算的占比数据行**，支撑 Q1"显著"数值化）+ 编译线程预算口径数据（翻译成本与缓存命中收益，衔接 ④），支撑 Q4 前置条件操作化。
- [ ] **stock JVM 形态全量基准运行**（本工作区 live JDK——运行时实际 JDK vendor/version 记录入报告），原始 JMH 结果 + 汇总表落 repo。
- [ ] **D3 GraalVM 环境获取尝试决策（必答）**：GraalVM JVM 形态是 truffle 后端核心价值载体（roadmap 范围项"× GraalVM 与 stock JVM 两形态"、truffle 列稳态数据必须在此形态补齐）——**优先尝试获取 GraalVM JDK**（向用户提出安装请求，ask-first；本 plan 不自行下载安装）。获取成功 → 双形态全量运行；**尝试后仍不可得**才按 I11 先例显式裁定（live 核验记录 + 复跑入口命令 + 报告显式标注缺席），不静默降级；Exit/Gate 记录区分"尝试后不可得"与"未尝试"（后者不允许作为缺席理由）。
- [ ] **基准数据落 repo**：报告（`ai-dev/analysis/2026-08/2026-08-xx-xlang-backend-benchmark.md`——**既知前向引用**，Phase 2 执行时以实际日期落盘；含环境/JDK/参数/原始数据/结论/复跑命令）+ 可复跑入口（模块 README 或 docs 落点 D2 定稿）；Phase 1 直跑证据并入同报告。
- [ ] mission.json commands 同次变更追加 benchmark 模块（`test`/`build`/`lint`/`typecheck` 四条一致口径；"模块落盘即切换"裁定）——JMH 基准经 main 入口运行而非 surefire，commands 追加仅影响编译域，口径记录在案。

Exit Criteria:

- [ ] 基准载体落盘（repo-observable：模块/用例类 + JMH 接线 + README 复跑命令），五类用例（三向对比/动态稳态/池成本与梯度/缓存敏感性/Q1-Q4 锚点数据）各自有可复跑入口。
- [ ] stock JVM 全量数据在 repo（JMH 原始输出 + 汇总表 + 环境）；机会成本量化结论有数据行支撑（同单元三向对比）。
- [ ] GraalVM 形态（D3）：真实运行数据在 repo；或"尝试获取后仍不可得"显式裁定 + live 核验记录 + 复跑入口（二选一，报告显式标注，"未尝试"不构成缺席理由）。
- [ ] **端到端验证**：基准的 java 列经生产绑定路径（双清单装载）执行、truffle 列经池运行时执行、解释器列经显式旁路（force-interpreter/直驱）执行且**三列各有身份核验**（java=生成类入口 / truffle=CallTarget / 解释器=非 bound 执行体）——与用户真实路径一致或显式等价，非静默测错后端。
- [ ] **无静默跳过**：环境探测失败/用例前置缺失显式失败（JMH error），无吞异常空结果。
- [ ] mission.json commands 四条追加后 live 可运行（原样执行记录）。
- [ ] **New test required 清单**：基准载体为 main 域 JMH（非 surefire 用例）——防漏/身份类断言若为 Phase 1 测试域已覆盖则此处显式引用；载体自身新增的纯 JMH 用例不强制 surefire 测试，注明 `No new test required: JMH main-entry benchmark`（如另有测试域新增，逐条列出）。
- [ ] owner-doc 裁定：docs-for-ai 若需补"基准复跑入口"小节（I11 已有 native 复跑入口章节，视 D2 落点裁定追加或引用）；裁定显式记录。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Q1/Q4 触发口径量化 + 调优裁定 + 移交对账与收口记账

Status: planned
Targets: 基准报告（Phase 2 产出的 `ai-dev/analysis/2026-08/` 文件——增 Q1/Q4 量化口径专章）、`ai-dev/backlog/xlang-execution-optimization-roadmap.md`（I12 条目回链）、`io.nop.xlang.truffle`（配置缺省，仅当裁定调整）、mission.json/当日 log

- Item Types: `Decision | Proof | Fix`

- [ ] **Q1 触发口径量化定义**（锚定 Phase 2 用例⑤实测数据行）：Bytecode DSL 重评触发 = 生态判据（官方 bytecode_dsl 版本/兼容性状态）+ 数值判据（truffle 列 AST 解释开销占比阈值——以⑤的可复算占比数据定义"显著"）；**数值判据仅锚定 GraalVM 形态数据行**（stock JVM 上 truffle 全解释执行、占比平凡趋近 100%，不构成判据基础）——D3"尝试后不可得"时数值判据显式降级（维持生态判据 + 量化推迟记录于报告），禁止以 stock 数据冒充。量化定义**落入基准报告专章**（watch-only 状态不变，触发条件数值化）。
- [ ] **Q4 触发口径量化定义**（锚定 Phase 2 用例⑤实测数据行）：与 nop-js 共享 Engine 重评前置条件 = 编译线程预算/翻译缓存收益数据口径（⑤翻译成本与缓存命中收益数据操作化 I8 移交口径），同落基准报告专章。
- [ ] **量化口径落点裁定（既定）**：设计 truffle 02 **保持冻结不改**（roadmap L42 纪律；设计 §九 L167 自述量化载体 = "I7[=I12] 基准计划中定义"——即本 plan 及其产物）；量化口径承载 = 基准报告专章 + roadmap I12 条目 done 记录回链报告路径（I5-I11 Follow-up 链的量化归属就此闭合，链上各 plan 不回写）。
- [ ] **池/缓存缺省值裁定**：依据梯度数据裁定 `availableProcessors` 与 1024 保持或调整——保持则记录数据依据；调整则改配置缺省 + 全量回归全绿（不改路由决策树语义；调整值必须有明确数据支撑，禁止无数据拍脑袋）。
- [ ] **I11 §14 六项移交逐项对账处置表**：(1) 三后端可用形态核验（基准/套件两侧实测）(2) 命令口径核验结论 (3) 全量对拍套件输入消费记录（本 plan Phase 1）(4) native 验证证据指针引用确认（I11 在案，本 plan 不重做）(5) Q1/Q4 量化输入消费（Phase 2 ①用例即其落地）(6) rollout 前置条件移交去向显式记录（Non-Blocking Follow-ups，successor 声明）——逐项落 Execution Notes。
- [ ] **收口记账**：roadmap I12 条目 `done` + `Last updated` 头刷新 + 回链基准报告路径（注意状态机时序：`todo→planned` 已在本 plan 通过 draft review 转 active 时同步，非本 Phase 动作；`planned→done` 在独立 closure audit 通过后）+ Milestone「双后端落地」派生状态核对（I1-I12 全 done 后同步）；当日 log 收口条目；`node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0。

Exit Criteria:

- [ ] 基准报告含 Q1/Q4 量化触发口径专章（repo-observable：具体数值阈值 + 数据锚点指向报告内实测数据行），状态仍为 watch-only（重评实施不在本 plan）；设计 truffle 02 零改动（冻结保持）。
- [ ] 缺省值裁定 repo-observable：Execution Notes 裁定记录 + （如调整）配置代码 diff + 回归全绿证据；（如保持）数据依据行引用。
- [ ] I11 §14 六项对账表完整（六行逐项状态：consumed/核验通过/移交去向），无未处置项。
- [ ] roadmap I12 `done`（closure audit 后）+ 回链报告 + 移交记账完成；check-doc-links --strict EXIT=0。
- [ ] owner-doc 裁定：设计文档冻结保持（零改动）；触发口径为 governance 信息（预期 No behavior change）→ docs-for-ai 显式裁定 `No owner-doc update required`（基准复跑入口的 docs 落点已在 Phase 2 裁定，此处不重复）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Execution Notes

### Phase 1（2026-08-21 执行）

- **物化与 gen task 重跑**：corpus 48 静态单元拷贝至 `nop-kernel/nop-xlang-java-e2e/src/main/resources/_vfs/test/xlang/e2e/corpus/{static,static-a,static-b}`（cp 字节保真）；`./mvnw generate-test-resources -pl :nop-xlang-java-e2e` 真实运行 gen task——**48/48 单元全部生成成功，零转译 fail-fast、零指纹失配降级**（失败两分类区分机制在案而未触发：55 manifest 条目（7 e2e 原生 + 48 corpus）、二轮运行 check 零漂移（幂等）、产物提交（`Gen__test_xlang_e2e_corpus_*` 48 源文件 + 双清单 + reflect-config 增量））。
- **聚合入口（单一）**：`nop-kernel/nop-xlang-java-e2e/src/test/java/io/nop/xlang/e2e/suite/TestFullCompareSuite.java`（74 单元参数化直跑三列：48 静态物化 + 26 动态（V1 11 + A 13 + B 2），corpus 计数钉线断言 48+26=74——增删未同步即红）；辅助类 `E2eCorpusUnits`（供给 + html 变体 oracle）/`SuiteColumns`（三列 + 身份规则再实现）/`TestCorpusMaterializationAntiDrift`（反漂移）。
- **列驱动器再实现（D1 裁定）**：java 列 = 生产绑定路径（`XLang.parseXpl` 绑定 hook → `EvalStaticBoundExecutable` 断言（降级即列 FAIL 非 skip）→ `Class.forName` 产物类实例 + EvalMethod 约定入口身份规则 → `XLang.execute` choke point 直通）；truffle 列 = `XLangTruffleEval` main API（sourceKey：静态=VFS 物化路径 / 动态=内容哈希键）+ XLangRootNode sourceTree 同一性身份规则；解释器列 = harness 基线列（干净解析取树——`XplModelParser` 直驱不经绑定 hook，身份核验 = 非 bound 执行体）。e2e pom 增 `nop-xlang-truffle` **test** 依赖（内核模块零 test-jar 发布、零新增依赖边——D1 拒绝项保持）。
- **三项机械适配（预列兑现，均非削弱）**：(i) `TestEndToEndGeneratedBinding` 3 处计数 7→55（scan/units/sources）+ 物化目录计数断言（corpus 子树 =48、negatives 排除断言零改动）；(ii) 5 个 html 变体单元预期值重声明——oracle 起草 = 解释器列 html 模式实测（临时探针输出落 Execution 记录），三列交叉验证一致后固化于 `E2eCorpusUnits.htmlVariantExpectation`：tpl-text = TEXT 扁平化（原 text 模式 VALUE Integer → html TEXT String，4 调用）；tpl-xml/tpl-xml-extattrs = TEXT 序列与 xml 模式同构（仅 loc path 重映射）；tpl-node/tpl-node-simple = 整树 XML 序列化单 TEXT 调用（原 node 模式 BEGIN/END_NODE 序列）；(iii) loc 重映射 = errorLocation path + 输出调用 loc path 统一 `xlang-compare/...` → `/test/xlang/e2e/corpus/...`（I10 remapExpectation 先例复刻）。
- **3 个 TAG_NONE collect 核验点（实测结果）**：html 统一驱动下返回值不变——tpl-collect-text `"a3c"` / tpl-collect-node XNode（内容相等口径）/ tpl-collect-sql SQL("select 1")，三列一致（`testHtmlVariantAndCollectVerifyUnitsRecorded` 显式记录 + 参数化直跑绿）。
- **防漏与红/绿对照**：`corpusIntegrityViolations` 双向 diff（VFS 物化文件集 ↔ 套件静态单元集）+ 目录计数（11/20/17）；负测试 `testAntiLeakRedGreenTamperedListing`（孤儿物化 / 幻影供给 / 剔除漏跑 三红 + 恢复绿）；双清单成员资格 + 产物类 `Class.forName` 可加载断言（java 列供给下界）。无静默跳过：java 列非 bound 即 `IllegalStateException`（harness 记 column-crashed FAIL）；skipRecords 空断言（列缺席红）。
- **新鲜直跑证据（mission.json `test` 命令原样，2026-08-21）**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle,:nop-xlang-java-e2e -am -T 1C` → **BUILD SUCCESS，1713/1713 全绿**（nop-xlang 551（2 skip 既有基线）+ nop-xlang-java 493 + nop-xlang-truffle 584 + nop-xlang-java-e2e 85（e2e 5 + 套件 79 + 反漂移 1））；I11 基线 551+493+584+5=1633 全保持（+80 纯新增，零削弱）。环境：Zulu 26.0.1（live JDK，运行时实测）、macOS arm64。计数并入 Phase 2 基准报告。
- **mission.json 四条 commands 汇总口径核验（live）**：`test`（上述 1713 绿）/`build`（clean install -DskipTests BUILD SUCCESS）/`typecheck`（compile EXIT=0）/`lint`（原样命令 EXIT=0——含 fallback echo 形态，与命令定义一致）；另 `-Pqa` checkstyle 四模块 EXIT=0。benchmark 模块追加归 Phase 2 同次变更（未触发——Phase 1 无模块落盘）。
- **owner-doc 裁定**：`No owner-doc update required`——套件入口为 roadmap QA 基础设施（测试域，经 mission `test` 命令消费），不改变平台使用者可感知的开发/验证流程；对拍断言口径与既有 docs 描述无冲突。Phase 2 基准复跑入口 docs 落点归 Phase 2 裁定。
- **hollow scan**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang-java-e2e --severity high` EXIT=0（0 findings）。

## Closure Gates

> 关闭条件：本 section 及各 Phase Exit Criteria 全部 `[x]` 后，经独立 fresh closure audit 方可将 `Plan Status` 改为 `completed`。

- [ ] **全量三后端对拍套件直接运行全绿**（roadmap 验收第一项）：三列全 corpus + 静态/动态列适用性 + 后端身份断言 + 列缺席显式记录，单一入口直接执行、非引用形式；防漏红/绿对照在案。
- [ ] **基准数据落 repo**（roadmap 验收第二项）：报告 + 可复跑入口；stock JVM 数据齐备；GraalVM 形态数据齐备或"尝试获取后不可得"显式裁定 + 复跑入口（D3；"未尝试"不构成缺席理由）。
- [ ] 机会成本量化（三向对比）、池创建/销毁成本、池大小与缓存容量敏感性数据在案。
- [ ] Q1/Q4 watch-only 触发口径量化定义落入基准报告专章并锚定实测数据 + roadmap I12 条目回链；设计 truffle 02 冻结保持（零改动）；重评实施未越界（仍 watch-only）。
- [ ] 池/缓存缺省值裁定有数据支撑；如调整，全量回归全绿且不改路由语义。
- [ ] mission.json commands 汇总口径核验通过（含 benchmark 模块同次切换，四条 live 可运行）。
- [ ] I11 §14 六项移交逐项对账处置，无未处置项。
- [ ] 既有测试基线全保持：四模块（xlang/java/truffle/e2e，+ benchmark 如落盘）`./mvnw test` 全绿，无既有断言削弱（roadmap 纪律 3）。
- [ ] `./mvnw compile`（或 `-pl` 指定模块）通过；checkstyle / `-Pqa` 通过。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <affected-module> --severity high` EXIT=0。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0。
- [ ] 模块依赖方向保持（benchmark → 被测模块单向；内核模块零新增依赖边；org.graalvm.* 不泄漏）。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（rollout 项为 I12+ 既定归属显式移交，非缺陷降级）。
- [ ] 独立 fresh 子 agent closure audit 完成且 evidence 写入 `Closure` 段落（含 Anti-Hollow 检查：套件三列真实调用三后端执行体 + 基准用例经生产路径）。

## Deferred But Adjudicated

（起草时无新 deferred 项。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- **rollout 治理项移交**（I11 §14(6) 四项：含标签调用单元不可转译（支持集扩展前置）/xpl 片段排除机制/`@node` 变体键生成/分模块推进顺序）——Classification: `out-of-scope improvement`；Why Not Blocking Closure: roadmap 交付面 = 平台双后端能力落地（已由 I1-I11 + 本 plan 收口），全仓铺开为后续平台推广治理，不影响 supported baseline 成立；Successor Required: `no`（mission 于 I12 收口，后续由用户/后继 roadmap 决策——移交记录 repo-observable 于本 plan Execution Notes 对账表）。
- Q1/Q4 维持 watch-only（本 plan 落地量化触发口径后仍 watch-only——是否触发重评由数据判定，重评实施属后继决策；设计 §九归属链 I5-I11→I12 就此闭合）。
- I3/I4 优化候选维持原分类（`XLangSemantics.invokeGlobalFunction` display 急切构造 = 优化候选；`ClassModel.getConstructorForArgs` 多候选 = watch-only nop-core 域）——本 plan 基准数据可为其输入，实施归触及对应域的后继 plan。

## Closure

Status Note: （待关闭时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （待独立 fresh closure audit）
- Evidence: （待补）

Follow-up:

- （见 Non-Blocking Follow-ups；确认无 plan-owned 剩余工作后显式写明）
