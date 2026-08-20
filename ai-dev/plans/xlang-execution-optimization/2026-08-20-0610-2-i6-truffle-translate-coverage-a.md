# I6 truffle 翻译覆盖 A（数据与作用域族，类别同 I3）+ 帧映射推进与 Q3 残余路径

> Plan Status: completed
> Last Reviewed: 2026-08-20
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I6（类别同 I3 = 设计 `ai-dev/design/xlang-java/01-architecture-baseline.md` §三分类表）；设计冻结于 `ai-dev/design/xlang-truffle/02-architecture-baseline.md`（§四对象/帧映射与 Q3 残余路径、§七翻译器与语义一致性）；对拍口径 = 设计 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五
> Mission: xlang-execution-optimization
> Work Item: I6
> Related: I5（前置：truffle 骨架/帧映射/子集翻译/翻译缓存，其移交两项——Phase 2 Exit Criteria 注记的 kind 全量覆盖率实测 + 设计 Q3 残余按名访问路径——由本 plan 承接闭合）；I3（执行顺序在前：corpus 覆盖 A 单元与矩阵口径的正常供给方，见 Phase 1 fallback 裁定）；后继 I7（覆盖 B + 两级内联缓存 + truffle 侧矩阵闭环）

<!-- Draft review: round-1（fresh session ses_fe3ea0d2fffeZ6bi7GEqQ3Xog1，1 Major+6 Minor：TreeFingerprints 白名单弱哈希兜底对新节点类的载荷覆盖缺口/移交出处措误/fallback 触发态定义/Phase 2 Targets 缺 nop-xlang/统计口径未定/XLangSemantics 排除/checkstyle 覆盖）→ 修复 → round-2（fresh session ses_fe3e112a7ffee7ILZKyTfTULX5，Ready 0 Blocker，5 Minor 措辞与收紧项——移交措辞/子树结构载荷/Phase 级 nop-xlang 回归门/fallback corpus 同规格/checkstyle 括注——已当场修复）→ 共识达成 → active。 -->

## Purpose

把 truffle 翻译器从 I5 表达式子集推进到覆盖 A（数据与作用域族，类别同 I3）全族可翻译：五类节点族逐具体节点类翻译（EXCLUSIVE 形态不变），corpus 覆盖 A 单元 truffle 列 vs 解释器列对拍全绿，truffle 侧覆盖矩阵与 java 侧同基线同口径推进；同时闭合 I5 移交的两项：帧 kind 推断覆盖率全量实测、按名访问残余路径（设计 Q3：context 持有的 scope 链对象查找节点）落地与覆盖率记录。

## Current Baseline

- I5 产物存在（前置断言——执行本 plan 前须核验 I5 已 `completed`）：`nop-kernel/nop-xlang-truffle` 模块——`XLangLanguage`（id `xl`、EXCLUSIVE 过渡形态，SHARED 切换归 I8）、`XLangContext`（求值窗口协议，输出缓冲线程绑定）、`FrameLayoutMapper`（`CallFuncExecutable.getSlotNames()` slot 布局 → `FrameDescriptor`/`FrameSlot`，kind 仅可推断处标注不虚构，访问模式按用法声明，MATERIALIZE 归 I7）、`ExecToTruffleTranslator`（子集与 I2 同口径 + 结构性载体；语义敏感操作走 `XLangSemantics`（D3 裁定）；子集外 fail-fast 报节点类名 + SourceLocation）、`TranslationCache`（键 = sourceKey + 树指纹；动态源 = 内容哈希键（D2））、`TruffleBackendColumn` + 强身份规则（翻译 AST 经 CallTarget 执行）。
- corpus v1 truffle 列 live 全绿：`TestCorpusV1TruffleColumn` 22/22（静态 + 动态；三层断言 + 身份断言 + 列缺席/不适用记录）。
- I5 移交本 plan 的两项（出处 = I5 Phase 2 Exit Criteria 注记 + 设计 truffle 02 §四 Q3；注意 I5 plan 的 `Deferred But Adjudicated` 区仅含归 I8 的 SHARED/缓存淘汰两项，与本 plan 无关）：
  - 帧映射 kind 推断**全量覆盖率实测**（I5 Phase 2 Exit Criteria 注记"子集内可推断比例记录落 log，全量覆盖率归 I6 后续实测"——子集实测：corpus v1 4 slot 全部推断为 Int）；
  - 设计 truffle 02 §四 Q3：**残余按名访问路径**——翻译期 slot 化优先，无法 slot 化的按名访问走 context 持有的 scope 链对象查找节点，"覆盖率实测归实现计划验收"。作用域链访问族属覆盖 A，随本 plan 落地。
- I3 为本 plan 的执行顺序前置（同时间戳 `{N}` 编号 1 在前）：corpus 覆盖 A 单元、覆盖矩阵基线/排除清单/归属裁定（含四分区无无主裁定）为 I3 产物，本 plan 正常路径直接消费。roadmap 依赖仅 I5——**fallback 裁定**（Phase 1 定稿记录）：**触发条件 = 执行本 plan 时 I3 plan 不处于 `completed` 状态（含 active 进行中/延期/被拒）**；触发时对 I3 已落地部分做**增量对账**（已存在的 corpus 单元/口径清单/共享 helper 直接消费，仅补缺失部分），记录 I3 当时状态与对账结果，"先落地方为事实源"（I5 D3 先例）；I3 后续执行时消费本 plan 产物。
- `missions/xlang-execution-optimization.json` commands 已是三模块口径；本 plan 不新增模块、不加依赖、不改 commands。
- 真正剩余的 gap：A 五族节点翻译全部缺失（遇即 fail-fast，`TestTranslatorFailFast` 以 `GetPropertyExecutable` 为反例即证）；truffle 侧无覆盖矩阵；Q3 残余路径与 kind 全量覆盖率未落地。

## Goals

- 覆盖 A 五族具体节点类（类别同 I3，含 I3 裁定并入的数据面残余算子族——truffle 侧同口径对齐）全部可翻译：fail-fast 边界收缩；语义敏感操作 generic 路径统一走共享 helper（`XLangSemantics`，D3 口径延续），禁止在翻译节点内重写语义等价实现（设计 truffle 02 §七）；本 plan 不新增语义特化 fast-path（特化与两级内联缓存归 I7，覆盖计划与优化计划分离）。
- Q3 残余路径落地：作用域链访问族翻译期 slot 化优先；无法 slot 化的按名访问翻译为 context 持有的 scope 链对象查找节点（节点不存 context 数据，context-independent 准则不变）；slot 化覆盖率与残余路径使用记录 repo-observable。
- 帧 kind 推断覆盖率全量实测：覆盖 A corpus 全部单元实测落 log（I5 移交闭合）；kind 标注规则不变（仅可推断处标注、不虚构）。
- corpus 覆盖 A 单元 truffle 列 vs 解释器列对拍全绿（三层断言 + 身份断言 = 翻译 AST 经 CallTarget 执行；静态/动态按单元适用性）。
- truffle 侧覆盖矩阵落地并推进：与 java 侧**同基线同口径**（同一基线清单/排除清单/归属裁定，Phase 1 定稿共享方式）；A 族 + I2 子集全绿；B 族显式 pending；新增未注册节点类红灯（注入验证）。
- `org.graalvm.*` 不泄漏口径保持（本 plan 不引入新依赖，收口复跑不泄漏断言）。

## Non-Goals

- 覆盖 B 族（函数/闭包/控制流/输出族）翻译、两级内联缓存、语义特化 fast-path（I7）；`MaterializedFrame` 闭包捕获（I7）。
- SHARED 形态切换、Context 池、并发对拍、翻译缓存淘汰（I8）。
- 生产代码路径的后端注册 SPI 接入与路由（I9）。
- java 侧转译与矩阵机制建立（I3——fallback 触发时仅按 Phase 1 裁定补 corpus 与口径盘点，不建 java 侧矩阵）。
- native image 兼容（I11）。

## Scope

### In Scope

- A 五族（+I3 并入残余同口径）节点类翻译落地（translator 注册 + fail-fast 收缩 + 翻译级单测 + 缓存键语义对新节点保持）。
- Q3 残余按名访问路径（context 持有 scope 链查找节点）与 slot 化覆盖率记录。
- 帧 kind 推断覆盖率全量实测（log 记录）。
- corpus 覆盖 A 单元 truffle 列对拍（正常路径消费 I3 corpus；fallback 按 Phase 1 裁定）。
- truffle 侧覆盖矩阵（同基线同口径 + 红灯注入验证）。

### Out Of Scope

- 同 Non-Goals。

## Execution Plan

### Phase 1 - 口径对齐、fallback 裁定与矩阵同基线定稿

Status: completed
Targets: 本 plan 与当日 log（裁定记录）；矩阵基线共享位（落点 Phase 1 定稿，候选：nop-xlang test-jar 侧共享清单或两侧清单 + 一致性断言）

- Item Types: `Decision | Proof`

- [x] 核验前置与供给方状态：I5 已 `completed`；I3 状态检查（fallback 触发条件 = I3 不处于 `completed`），据此定稿 fallback 裁定并记录（正常路径消费 I3 产物；fallback = 增量对账 I3 已落地部分 + 补齐缺失的 corpus 覆盖 A 单元与类别口径盘点，"先落地方为事实源"，I3 后续消费）
- [x] 消费/对齐 I3 口径产物：五族具体节点类清单、抽象/辅助排除清单、四分区归属裁定（含残余算子族与函数邻接类归属）——truffle 侧逐项对齐为**同一清单**（同基线同口径的实体；共享方式定稿：单一事实源或两侧清单 + 一致性断言，记录决策与理由）；共享排除清单须覆盖 `XLangSemantics`（live `exec/` 包内非节点具体类，包文件数已因 I2 增至 138）
- [x] 矩阵机制 truffle 侧定稿（决策记录）：基线源 = live 扫描 `io.nop.xlang.exec` 包具体类；注册证据 = 翻译器支持声明显式化 + 每族代表性节点真实翻译验证 + fail-fast 反证；pending 集与 java 侧一致；注入红灯验证口径与 I3 同构
- [x] 产生路径复核：对 fallback 路径（若触发），执行 I3 Phase 1 同口径盘点（产生路径 + 无法经前端产生节点记录）；正常路径复核消费的清单与 live 目录一致

Execution Notes（Phase 1 裁定记录，repo-observable）：

1. **前置核验（2026-08-20）**：I5 plan `> Plan Status: completed`（Closure 段有独立 fresh audit verdict）；I3 plan `> Plan Status: completed`（roadmap I3 已标 done，live 四分区计数 138 文件复核通过）。**fallback 未触发**——正常路径：corpus 覆盖 A（`CorpusCoverageA` 20 静态 + 13 动态）与类别口径直接消费 I3 产物，无增量对账需求。fallback 裁定记录在案（未触发态）。
2. **同基线共享方式定稿：单一事实源**。truffle 侧不建独立清单，直接消费 `io.nop.xlang.compare.ExecNodeBaseline`（nop-xlang test-jar 发布，java 侧矩阵 `TestExecTranslationCoverageMatrix` 已同实体消费）。理由：四分区清单/排除清单/归属裁定由同一 Java 实体承载，一致性由结构保证（无两侧漂移可能，无需额外一致性断言）；live 目录一致性由 nop-xlang 侧 `TestExecNodeBaselineFreshness` 新鲜度红灯（新增未分类文件即 FAIL）持续保证。排除清单复核：`XLangSemantics` 在 `EXCLUDED`（理由条目在案），`exec/` live 138 文件 = 44 A + 15 残余 + 28 I2 + 35 B + 16 排除，无无主残留。
3. **矩阵机制 truffle 侧定稿（与 I3 同构）**：基线源 = 同一 `ExecNodeBaseline`；注册证据 = ①翻译器支持集可编程枚举（`ExecToTruffleTranslator.getSupportedNodeClasses()`）与基线 `registeredTarget()` 双向 set 相等断言 + ②registeredTarget 87 类逐类最小实例**真实翻译**验证（非清单自证；slot 依赖节点程序入口帧包装）+ ③B 族 35 类逐类 pending fail-fast 反证；pending 集 = `ExecNodeBaseline.bFamily()`（与 java 侧同一实体）；红灯注入 = 测试域合成未注册具体节点类（`FutureExecutable` 同构）→ 支持集不含 + 翻译 fail-fast + 基线 `isClassified=false` 红/绿对照。
4. **产生路径复核（正常路径）**：corpus 覆盖 A 单元的产生出口（标准编译前端/MUTABLE_SCOPE_VAR/TEMPLATE）与不可经前端产生的节点族清单（引用族/InitRef/EnhanceRef/BindVar/Cast/Getter 族/Setter/MakeProperty/VarStatus/DebugIdentifier/GuardNotEmpty/CloneLiteral/BetweenOp/AssertOp/Range/ResolvedObjFunction——合成树覆盖）直接沿用 I3 plan Phase 1 产生路径盘点（`CorpusCoverageA` javadoc 在案），本 plan 不重复推导；矩阵最小实例工厂在 truffle 测试域独立构造（与 java 侧同构、不同实体——最小实例构造属各侧矩阵脚手架，基线口径仍同一事实源）。

Exit Criteria:

- [x] fallback 裁定 + 同基线共享方式 + 矩阵机制 truffle 侧口径，全部 repo-observable（本 plan Execution note 或当日 log）
- [x] truffle 侧矩阵基线与 java 侧（或共享单一事实源）一致性有断言或由同一实体保证（"同基线同口径"可验证）
- [x] No owner-doc update required（docs-for-ai 同步归 I11）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 覆盖 A 翻译落地、Q3 残余路径与帧映射推进

Status: completed
Targets: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/`（translate/nodes/frame/lang）；`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java`（共享 helper 增量，按"缺哪个提取哪个"）与 `nop-kernel/nop-xlang/src/test/`（corpus，仅 fallback 补齐时）

- Item Types: `Proof`

Execution Notes（Phase 2）：

- **共享 helper 增量 = 零提取**：I3 已提取的 `XLangSemantics` 33 方法族覆盖本 plan 翻译所需的全部操作（缺哪个提取哪个 → 无缺口），解释器无改调，`nop-xlang` 主代码零变更（回归基线不动）。
- 新增翻译节点 47 类（nodes 包 48 新文件，含绑定赋值辅助 `XBindingAssign`）：作用域链 5（Q3 scope 查找族）+ 引用族 5 + 类型操作 4 + 一元合并 1 + 对象/集合 13 + 绑定/守卫/调试 8 + slot 写 2 + 残余算子 9（Null 检查/一元算子/InitRef-EnhanceRef 各自按模式合并同构变体）；`ResolvedObjFunctionExecutable` 经既有 `AbstractObjFunctionExecutable` 分派分支覆盖。
- `TreeFingerprints` 白名单扩至全部可翻译类（标量载荷 + 子树皆混入），兜底分支对可翻译类 fail-fast（`isNodeClassSupported` 守卫，防未来新增可翻译类静默弱哈希）；不可翻译类允许弱哈希（翻译必然 fail-fast，不进缓存）。
- `FrameLayoutMapper` READ/WRITE 声明扩展至 A 族全部 slot 用法来源（引用族读写/绑定写/自增自减/VarStatus/DebugIdentifier 按名解析）+ `KindReason` 五分类（INFERRED/MIXED_FAMILY/NON_LITERAL_WRITE/NON_PRIMITIVE_LITERAL/ZERO_WRITE）。

- [x] A 五族（+并入残余同口径）逐具体节点类翻译：语义敏感操作 generic 路径走 `XLangSemantics` 共享 helper（与 java 侧/解释器同一实现来源；**提取规则 = 本 plan 翻译所需而 nop-xlang 尚未共享的操作，无论 I3 状态如何都由本 plan 提取所需最小集（D3 先例）**——执行裁定：I3 已提取集合无缺口，零增量）；不新增语义特化 fast-path；SourceLocation → SourceSection 回映射覆盖新可抛错点（`NopException` 语义，`SyntheticSources` 既有机制）
- [x] **树指纹载荷覆盖（缓存键语义不弱化）**：`TreeFingerprints` 现为白名单 `instanceof` 链，兜底分支仅混入类名 + SourceLocation——新增可翻译节点类的**语义载荷**（属性名/变量名/类型名等标量载荷**与子表达式结构**，二者皆为载荷）必须逐类参与指纹混合（扩展白名单分支），或对"可翻译但载荷未混合"的节点类 fail-fast；禁止新节点类落入仅类名+位置的弱哈希兜底（防同 resourcePath 树变更——租户 Delta/热改——指纹碰撞串用旧缓存 AST，设计 truffle 02 §七自认最危险缺陷形态）
- [x] Q3 残余路径落地：作用域链访问族 slot 化优先（`FrameLayoutMapper` 既有布局机制）；无法 slot 化的按名访问翻译为 context 持有的 scope 链对象查找节点（经 `XLangContext` 求值窗口存取，窗口外 fail-fast 既有语义保持）；节点不存 context 数据或运行时值
- [x] 帧访问模式按 A 族节点实际用法声明扩展（READ/WRITE 新增来源；MATERIALIZE 仍不物化归 I7）；slot 越界 fail-fast 保持
- [x] 翻译级单测：每族 ≥1 翻译断言（corpus 单元或合成树；无法经前端产生的节点以合成树覆盖）；fail-fast 反证 ≥1 例（pending 集节点报 unsupported 含节点类名 + SourceLocation）；Q3 残余路径单测（可 slot 化 → 走帧；构造无法 slot 化场景 → 走 scope 链查找节点，两路径各自有验证）；**指纹载荷单测：每新增节点类构造仅语义载荷不同的两棵树 → 指纹不同（同 sourceKey 不串用）；含子表达式的复合节点类另构造仅子树结构不同（同标量载荷）的树对 → 指纹不同**

Exit Criteria:

- [x] 覆盖 A（+并入残余同口径）全部具体节点类可翻译——Phase 2 自身可判：翻译器支持集可编程枚举且覆盖 Phase 1 对齐的全部 A 族具体节点类（矩阵 live 扫描交叉验证归 Phase 3 联动）
- [x] **树指纹载荷覆盖有逐类测试**（仅语义载荷不同的树对 → 异指纹；复合节点类含仅子树结构不同的树对；同 sourceKey 不串用缓存）；`TreeFingerprints` 无"可翻译但载荷未混合"的静默兜底（fail-fast 或全载荷覆盖）
- [x] Q3 残余路径两分支各有验证（slot 化优先 + scope 链查找节点 fallback），节点 context-independent 保持（不存 context 数据）
- [x] 每族 ≥1 翻译单测 + fail-fast 反证在仓（repo-observable）
- [x] 共享 helper 增量涉及解释器改调时，`./mvnw test -pl :nop-xlang -am` 全绿（回归基线一致或仅有新增测试）——执行裁定：零增量零改调；三模块全绿（nop-xlang 507/0/2 基线不动、nop-xlang-java 228/0/0、nop-xlang-truffle 227/0/0）
- [x] 无静默跳过：新翻译节点中无法处理的形态显式 fail-fast
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - corpus 覆盖 A truffle 列对拍全绿、kind 覆盖率实测与矩阵推进

Status: completed
Targets: `nop-kernel/nop-xlang-truffle/src/test/`（列扩展与矩阵）、corpus（正常路径消费 I3 产物；fallback 为本 plan Phase 1 裁定产物）

- Item Types: `Proof`

Execution Notes（Phase 3 实测记录，`TestCorpusCoverageATruffleColumn` 输出钩子逐单元可复跑）：

- **kind 推断全量覆盖率实测（I5 移交第一项闭合）**：corpus v1（22 单元）4 slots 全部 Int 推断（100%，字面量单调写子集）；corpus 覆盖 A（33 单元）13 slots（静态 9 + 动态 4）推断 0——原因分类：NON_LITERAL_WRITE 11（map/binding/slot 族写入均为非字面量来源）/ MIXED_FAMILY 2（slot-write 双单元：字面量 + 复合赋值混合写）/ ZERO_WRITE 0 / NON_PRIMITIVE_LITERAL 0 / INFERRED 0。合计 17 slots 推断 4（23.5%）——A 族数据面写入源天然非字面量，推断率为口径性结论而非缺陷；kind 标注规则不变（仅可推断处标注、不虚构）。
- **Q3 残余路径使用统计（I5 移交第二项/设计 Q3 验收闭合）**：`[truffle-q3-stats]` 逐单元计数——作用域链类别单元（scope-chain/scope-assign/scope-self-*/global-var 及 MUTABLE_SCOPE_VAR 动态单元）100% scope 链查找路径（slotAccesses=0，scopeLookups=1-4）；slot/绑定类别单元（map-*/binding-*/slot-write）100% slot 化路径（scopeLookups=0，slotAccesses=3-6）；corpus 覆盖 A 合计 slotAccesses=41 vs scopeLookups=21（逐单元输出可复跑求和，closure audit 复核一致），两分支各自有真实语料覆盖。

- [x] truffle 列对拍全量执行覆盖 A corpus（静态 + 动态按单元适用性；三层断言 + 身份断言 + java 列缺席/不适用记录按 I1 机制区分）；fallback 补齐的 corpus 单元满足与 I3 corpus 同规格（五族每族静态 ≥1、异常单元 ≥1、动态按自然产生能力配比、schema 四字段）——执行裁定：fallback 未触发（Phase 1），直接消费 I3 corpus
- [x] 帧 kind 推断覆盖率全量实测：覆盖 A corpus 全部单元逐 slot 实测（推断比例 + 未推断原因分类），记录落当日 log——I5 移交项闭合。**统计口径**：逐单元可复跑输出（先例 `[truffle-frame-stats]` 输出钩子扩展）；原因分类至少含：字面量初始化可推断 / 混合族回落 Object / 零写入 / 字面量族外（String 等）——实现为五分类（另含非字面量写 NON_LITERAL_WRITE，A 族主力原因）
- [x] slot 化覆盖率与 Q3 残余路径使用统计记录（作用域链访问族单元中 slot 化访问 vs scope 链查找节点访问的计数占比，逐单元可复跑）落 log——Q3 验收记录
- [x] truffle 侧覆盖矩阵测试落地：A 族 + I2 子集全绿；B 族 + 归 B 边缘节点显式 pending（不算通过）；红灯注入验证（构造未注册具体节点类 → 矩阵 FAIL，红/绿对照）

Exit Criteria:

- [x] 覆盖 A corpus truffle 列 vs 解释器列对拍全绿（含身份断言 = 翻译 AST 经 CallTarget 执行）——**roadmap I6 验收第一项**（`TestCorpusCoverageATruffleColumn` 33/33：静态 20 双列 + 动态 13 双列，三层断言 + cross-compare + 身份断言；异常单元跨列错误码 + 源位置一致）
- [x] **覆盖矩阵推进（与 java 侧同基线同口径）——roadmap I6 验收第二项**：类别内基线节点类逐一注册断言全绿（`TestTruffleCoverageMatrix` 124 用例：支持集 ↔ `registeredTarget()` 双向 set 相等 + 87 类逐类最小实例真实翻译 + B 族 35 类 pending fail-fast）；pending 显式可观测；新增节点类红灯经注入验证（`FutureExecutable` 红/绿对照）
- [x] kind 推断全量覆盖率实测记录 repo-observable（log；I5 移交第一项闭合）；Q3 残余路径 + 覆盖率记录 repo-observable（I5 移交第二项/设计 Q3 验收闭合）
- [x] **端到端验证**：corpus A 单元 → 树翻译 → Truffle AST → CallTarget 执行 → 三层对拍断言全链可运行（stock JDK 21、EXCLUSIVE 形态）
- [x] **接线验证**：truffle 列身份断言在覆盖 A 单元上持续成立（非解释器兜底）
- [x] 回归不削弱既有测试（纪律 3）：`TestCorpusV1TruffleColumn` 22/22 与既有单测保持全绿（模块合计 384/0/0；fail-fast 反例随边界收缩移交 B 族 + 边界不回退守护测试）
- [x] `./mvnw test -pl :nop-xlang-truffle -am` 全绿；不泄漏断言复跑通过（`TestTruffleDependencyIsolation` 1/1）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 对应类别 corpus truffle 列 vs 解释器列对拍全绿（含身份断言）——roadmap I6 验收第一项
- [x] 覆盖矩阵推进（与 java 侧同基线同口径）——roadmap I6 验收第二项
- [x] I5 移交两项闭合：kind 全量覆盖率实测记录 + Q3 残余路径落地与覆盖率记录
- [x] fallback 裁定记录在案（无论是否触发）；触发时"先落地方为事实源"交接记录 repo-observable（Phase 1 Execution Notes：未触发态裁定 + 正常路径消费记录）
- [x] 语义敏感操作无双实现（generic 路径走共享 helper；无新增语义特化 fast-path；全部新节点调用 `XLangSemantics`/MathHelper/FilterOp/EvalHelper 既有共享实现，closure audit 逐类核验）
- [x] 回归不允许削弱现解释器测试（纪律 3）（corpus v1 truffle 列 22/22、既有单测全绿，模块 384/0/0；fail-fast 测试为边界收缩迁移 + 不回退守护，非削弱）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred But Adjudicated 为空；Follow-up 仅 Q1/Q4 watch-only）
- [x] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [x] 独立子 agent closure-audit 已完成并记录证据（task `ses_fe2cdadf1ffe80jMKndZ543MTp`，audit marker `ses_audit_i6_truffle_0820_Kx7qN4vZ`，verdict CAN CLOSE 0 Blocker）
- [x] Anti-Hollow Check：truffle 列真实经 CallTarget 执行（身份断言）；矩阵注册证据 = 真实翻译验证；无空方法体/静默跳过/no-op（audit 接线追踪：TruffleBackendColumn → XLangTruffleEval → Context.eval → parse → TranslationCache → CallTarget，无解释器兜底；`scan-hollow-implementations.mjs --module nop-xlang-truffle --severity high` 0 findings）
- [x] `./mvnw compile -pl :nop-xlang-truffle -am`（EXIT 0；`clean install -DskipTests` 三模块 BUILD SUCCESS）
- [x] `./mvnw test -pl :nop-xlang-truffle -am -T 1C`（384/0/0；三模块合计 nop-xlang 506/0/2 + nop-xlang-java 228/0/0 + nop-xlang-truffle 384/0/0）
- [x] checkstyle / 代码规范检查通过（`-Pqa checkstyle:check` 三模块 EXIT 0——本 plan 变更仅 nop-xlang-truffle，nop-xlang/nop-xlang-java 零改动脉络一并复核）

## Deferred But Adjudicated

（无——起草时无新 deferred 项；I5 移交的两项已纳入 In Scope 闭合。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- Q1（Bytecode DSL 重评估）/ Q4（与 nop-js 共享 Engine 重评估）维持 watch-only，触发口径量化归 I12（设计 §九既定归属，延续 I5 Follow-up）。

## Closure

Status Note: 三 Phase（口径对齐与矩阵定稿 → 覆盖 A 翻译/Q3 残余路径/帧映射推进 → corpus 对拍/实测/矩阵）全部 completed 且逐项勾选；roadmap I6 两项验收（corpus 覆盖 A truffle 列对拍全绿 33/33 含身份断言、truffle 侧覆盖矩阵同基线同口径 124 用例）与 I5 移交两项（kind 全量覆盖率实测 + Q3 残余路径落地与覆盖率记录）全部闭合；共享 helper 零提取（I3 已提取集合无缺口，nop-xlang 主代码零变更）。独立 fresh closure audit 逐项核验 live 代码/测试/工具门，verdict CAN CLOSE（0 Blocker / 3 Advisory 数字漂移已当场修正）。
Completed: 2026-08-20

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh 子 agent（task `ses_fe2cdadf1ffe80jMKndZ543MTp`，audit marker `ses_audit_i6_truffle_0820_Kx7qN4vZ`）
- Evidence:
  - 逐条 Exit Criterion / Closure Gate 验证结果 PASS（verdict CAN CLOSE，0 Blocker）：
    - (a) `TestCorpusCoverageATruffleColumn` 33/33 复跑绿（身份断言 live 在码：assertNotSame 解释器树 + assertSame sourceTree；静态单元 java 列 skip 记录、动态单元无 skip 区分）；
    - (b) `TestTruffleCoverageMatrix` 124/124 复跑绿（支持集 ↔ `registeredTarget()` 双向断言 + 87 类真实翻译 + B 族 35 pending + 红灯注入红/绿对照）；
    - (c) kind 实测复核：corpus A slots=13（静态 9 + 动态 4）kindInferred=0，原因 NON_LITERAL_WRITE=11 / MIXED_FAMILY=2（surefire XML 与 plan 记录一致）；corpus v1 4 slots 全推断；`KindReason` 五分类在 `FrameLayoutMapper` live；
    - (d) Q3 五个 scope 查找节点均经 `XLangContext.requireEvalScope()` 取 scope、仅持 loc/name/operator/value（context-independent）；双分支测试在仓；scopeLookups=21 / slotAccesses=41 复核一致（advisory 数字漂移已修正）；
    - (e) `TestTreeFingerprintPayloadCoverage` 158/158 复跑绿（86 标量载荷对 + 69 子树对 + 3 独立；NullExecutable 无载荷排除有据）；`TreeFingerprints` 可翻译-未白名单 fail-fast 守卫在码；
    - (f) 全模块 384/384 复跑绿；corpus v1 truffle 列 22/22；checkstyle `-Pqa` 0 违例；
    - (g) Anti-Hollow 接线追踪：TruffleBackendColumn → XLangTruffleEval（EvalHandoff）→ Context.eval → parse → TranslationCache → CallTarget 执行翻译 AST，无解释器兜底；
    - (h) B 族/注入节点 fail-fast 报 ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE 含类名 + 位置（TestTranslatorFailFast 断言全）；
    - (i) 文本一致性：三 Phase Status/items/exit criteria 全 [x]，Closure Gates 收口前唯一未勾区；
    - (j) Deferred 空、Follow-up 仅 Q1/Q4 watch-only，无 in-scope 缺陷降级；
    - (k) `git status`/`git diff` 证实 nop-xlang（main+test）零变更，全部变更限于 nop-xlang-truffle + plan/log——同基线单一事实源未被触碰。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（收口后复核）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang-truffle --severity high` 退出码 0（0 findings）
  - Deferred 项分类检查：Deferred But Adjudicated 为空；Non-Blocking Follow-ups 仅 Q1/Q4（watch-only，量化触发口径归 I12，设计 §九既定）

Follow-up:

- Q1（Bytecode DSL 重评估）/ Q4（与 nop-js 共享 Engine 重评估）维持 watch-only，触发口径量化归 I12（设计 §九既定归属，延续 I5）。
- B 族翻译 + 两级内联缓存 + 语义特化 fast-path 归 I7（依赖本 plan）；无其余 plan-owned 遗留工作。
