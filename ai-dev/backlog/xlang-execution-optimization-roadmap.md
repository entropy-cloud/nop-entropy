# XLang 优化执行双后端 Roadmap（nop-xlang-java + nop-xlang-truffle）

> Status: active
> Last updated: 2026-08-20（I1 执行完毕 + 独立 closure audit CAN CLOSE，标 done——阶段二首个实现条目收口；I2/I5 plan 就绪待执行）
> Sources（设计阶段必读输入，实施前不得跳过）：
> - `ai-dev/design/xlang-truffle/01-truffle-knowledge.md`（Truffle 框架知识层，2026-08-16 三轮独立审查达成共识）
> - `ai-dev/analysis/2026-08/2026-08-16-truffle-graalvm-ecosystem-research.md`（GraalVM/Truffle 生态调研：native 内 guest 代码有运行时 JIT（25 默认）但宿主 Java 无 JIT，Truffle 只服务 JVM 部署形态；Espresso 支持 native exe 内动态加载字节码）
> - `~/sources/graal`（oracle/graal sparse clone：truffle + sdk 模块一手源码，SL 参考实现）
> - `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（现解释器：Executable 树 137 文件）

**Why**：XLang 目前为内存中解释执行（`IExecutableExpression.execute` 虚调用递归），在 GraalVM native image 中作为宿主 Java 代码 AOT 编译后**无运行时 JIT**（native 内的 Truffle 运行时 JIT 只服务 guest 语言代码，不作用于宿主 XLang 解释器），解释开销无法自我修复。可逆计算原则要求 Generator 优于 Interpreter。本 roadmap 落地双后端：
1. **nop-xlang-java**（新模块，建在 nop-kernel 下；编译期确定 → Java）：构建期把 Executable 树转译为 Java 源码（`_gen/`），运行期直接执行生成类，零解释开销；native image 场景唯一提速路线；
2. **nop-xlang-truffle**（新模块，建在 nop-kernel 下；运行时 → Truffle）：Executable 树翻译为 Truffle AST，GraalVM 部署下经 partial evaluation 获得 JIT，服务运行时动态编译的脚本/表达式；支持多线程（Context 池 + 共享 Engine + ContextPolicy.SHARED）。

两后端并存互补：**编译期可确定的资源走 Java 后端，运行时动态产生的走 Truffle 后端，两者都不适用时回退现解释器兜底**。正确性基准：同一 Executable 树在解释器/Java/Truffle 三后端执行结果必须一致（对拍）。

## Work Items

> **这是唯一动态状态块。状态只在这里更新。**
> 人工设定条目与顺序；AI 取第一个 `todo`，起草/执行计划，closure audit 通过后标 `done`。

### 阶段一：设计与计划 gate（用户指定流程，先于一切实现）

- W1-design. 三组设计文档撰写：`done`（plan：`ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-1-w1-design-docs.md`，2026-08-19 closure audit CAN CLOSE，0 Blocker/0 Major；产出 5 篇正文 + 3 README，01 决策迁出收口、Open Questions 清账）
  - `ai-dev/design/xlang-execution/`：00-vision（双后端愿景）+ 01-architecture-baseline（双后端统一架构：后端选择机制"编译期确定→java / 运行时→truffle / 兜底→解释器"、后端注册 SPI、对拍验证框架、模块边界与依赖方向）
  - `ai-dev/design/xlang-java/`：README + architecture-baseline（Executable→Java 转译器、~137 节点类映射策略、SourceLocation 保真、生成类加载与 `ResourceComponentManager` 集成"生成类优先/解释器兜底"、`_gen/` 构建任务、EvalMethod 调用约定）
  - `ai-dev/design/xlang-truffle/`：00-vision + 02-architecture-baseline（truffle 模块设计：XLangLanguage/Context、帧/slot 映射、多线程架构 Context 池 + SHARED + 共享 Engine、两级内联缓存准则、与 nop-js Engine 共享评估）
  - 依赖：无（知识输入已就绪）。粒度约束：三组文档可拆多个 plan，但必须全部定稿才能进 W2
- W2-review. 设计文档独立审查 gate：`done`（plan：`ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-2-w2-design-review-gate.md`，2026-08-19 五轮独立子agent 审查至 PASS） — 依赖：W1-design。**硬性要求：至少两轮独立子agent 审查（每轮新开子agent，不共享上下文），逐轮修复后复审，直到某轮 PASS（0 P0/P1）才可关闭；审查报告落 `ai-dev/audits/xlang-execution-optimization/`，roadmap 条目须回链两轮以上报告**。轮次报告：[round-1](../audits/xlang-execution-optimization/2026-08-19-design-review-round-1.md)（FAIL 2 P1→修复）、[round-2](../audits/xlang-execution-optimization/2026-08-19-design-review-round-2.md)（FAIL 1 P1→修复）、[round-3](../audits/xlang-execution-optimization/2026-08-19-design-review-round-3.md)（FAIL 1 P1→修复）、[round-4](../audits/xlang-execution-optimization/2026-08-19-design-review-round-4.md)（FAIL 2 P1→修复）、[round-5](../audits/xlang-execution-optimization/2026-08-19-design-review-round-5.md)（**PASS 0 P0/0 P1**，含移交 W3 清单 17 项）
- W3-supplement. 按定稿设计回填实现 work items：`done`（plan：`ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-3-w3-supplement-work-items.md`，2026-08-19 独立 closure audit CAN CLOSE，0 Blocker/0 Major/3 Minor——Minor 均为 W4-audit 输入；裁定记录见 `ai-dev/logs/2026/08-19.md`） — 依赖：W2-review。修订本 roadmap 阶段二（原预列 I1-I7 增删拆并为 I1-I12，定稿验收标准与依赖，Stages 表/依赖图同步）。**commands 纳入新模块采用"模块落盘即切换"裁定**：Maven `-pl` 对尚不存在模块直接报错，提前写入会使 mission.json live commands 恒失败——目标 commands 与切换时机已写入 I2（落盘 `nop-xlang-java` 时切换）/I5（落盘 `nop-xlang-truffle` 时追加）/I12（汇总口径核验）条目验收标准，live commands 在落盘前保持仅引用已存在模块
- W4-audit. roadmap workitem 审核 gate：`done`（plan：`ai-dev/plans/xlang-execution-optimization/2026-08-19-2350-1-w4-roadmap-workitem-audit-gate.md`，2026-08-20 round-1 独立审计即 PASS + 独立 closure audit CAN CLOSE，0 Blocker/0 Major/0 Minor；W3 Closure 移交 3 项 Minor 逐项裁定非阻塞，裁定记录见 `ai-dev/logs/2026/08-20.md`） — 依赖：W3-supplement。独立 audit（openAuditPrompt）：粒度（单 plan 可完成，5-15 文件/200-500 行/1-4 phases）、依赖图无环且与 stage 表一致、验收标准可验证、复用标注准确、与定稿设计无冲突；FAIL 则回 W3。轮次报告：[round-1](../audits/xlang-execution-optimization/2026-08-20-roadmap-workitem-audit-round-1.md)（**PASS 0 P0/0 P1/4 P2**，独立 fresh session，五维度逐维度 PASS；W3 移交 3 项 Minor 逐项裁定非阻塞——I6 类别同构引用链完整可解析 / I3/I4/I6/I7/I8 复用信息由定稿口径块共享 helper 纪律 + Stages Reuse 列承载 / 编号映射表抽验 10 处覆盖全部行使分支且语义正确；遗留 4 项 P2 逐条裁定归属见报告 §八，移交后继清单见 §七）
- ★ **Milestone: 设计与计划就绪**（W1-W4 全部 done，阶段二 work items 定稿并通过审核）：`done` — 派生：W1-W4（2026-08-20 W4-audit done 后同步）

### 阶段二：实现（W3-supplement 已定稿：原预列 I1-I7 增删拆并为 I1-I12，逐项裁定与理由见 `ai-dev/logs/2026/08-19.md`；W4-audit done 前不得启动）

> **定稿口径（全条目适用）**
>
> - **对拍不变式（纪律 3）**：各条目验收中的"对拍"= [xlang-execution/01-architecture-baseline.md](../design/xlang-execution/01-architecture-baseline.md) §五口径——同一棵 Executable 树三层断言（返回值 equals 含类型 / 副作用 scope 变量与输出缓冲逐项比对 / 异常语义 错误码 + SourceLocation 回映射），列适用性（静态单元三列：解释器/java/truffle；动态单元两列：解释器/truffle），后端身份断言（java 列执行体=生成类实例、truffle 列=翻译 AST CallTarget——不断言身份的比对判无效），列缺席显式记录跳过（不算通过）；单元级降级判 FAIL 不判跳过
> - **覆盖矩阵**：以 live `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（137 文件）为基线逐节点类断言 translator 注册，新增节点类未注册即矩阵红灯；缺 translator 的树转译/翻译 fail-fast，禁止部分生成（设计 java §三 / truffle 02 §七同构策略）
> - **粒度**：条目按"单 plan 可完成（约 5-15 文件 / 200-500 行 / 1-4 phases 量级）"定稿——全覆盖条目按设计 java §三分类表切分为覆盖 A（数据与作用域族：作用域链访问/类型操作/对象集合构造访问/绑定守卫调试/slot 写族）与覆盖 B（函数闭包/控制流/输出节点生成族）两半；无欠粒度合并项
> - **共享 helper 纪律**：语义敏感操作（数值提升/宽松比较/属性反射等）生成代码与翻译 AST 统一调用定义在 `nop-xlang` 的共享 helper，禁止为后端重写语义等价实现（设计 java §三 / truffle 02 §七同一裁定）
> - **编号映射（W3 重排，解读设计文档旧引用用）**：原预列 I1-I7 → 定稿 I1-I12：旧 I1→I1+I2；旧 I2→I3+I4+I10；旧 I3→I5；旧 I4→I6+I7+I8；旧 I5→I9；旧 I6→I11；旧 I7→I12。W1/W2 定稿设计文档中的 I 系条目号引用仍为旧编号（设计文档冻结不改，finding 已移交），W4-audit 核对"与定稿设计无冲突"时按本映射解读

- I1. 三后端对拍验证框架 + corpus v1：`done`（plan：`ai-dev/plans/xlang-execution-optimization/2026-08-20-0030-1-i1-compare-harness-corpus-v1.md`，2026-08-20 执行完毕并独立 closure audit CAN CLOSE（task `ses_fe4ede786ffeuzX4oxTRiiXzli`，0 Blocker/0 Major/3 Minor 收口动作项当场处置）；落地：harness 于 nop-xlang 测试源码 `io.nop.xlang.compare` 包 + test-jar 发布、corpus v1 22 单元（6 类 × 双形态 + 组合双形态）解释器基线全绿、四类分歧注入自检红/绿可控、列缺席显式记录有测试；执行中三项裁定（CallFunc 程序入口包装/slot 类 let 载体/FunctionExecutable 族属宿主反射分派族）记 plan Phase 2 Execution note 供 I2/I5 对账） — 依赖：W4-audit
  - 范围：对拍 harness（后端作为用例执行参数矩阵化，复用 Nop AutoTest 机制；落点由实现 plan 按模块依赖方向合法性定）；表达式子集 corpus v1（静态资源样例 + 动态字符串样例配比构成，配比落 plan）；测试级强制路由 API 与后端身份断言；列适用性与"列缺席显式记录"机制；差异注入自检
  - 验收：对拍不变式断言机制落地（三层断言/身份断言/列适用性）并以差异注入自检证明（故意构造分歧用例 → harness 判 FAIL，红/绿可控）；corpus v1 解释器基线列全绿；列缺席记录机制有测试（java/truffle 列缺席时显式记跳过、不算通过）
  - 复用：Nop AutoTest 机制
- I2. nop-xlang-java 模块骨架 + 表达式子集转译 + 共享语义 helper 基座 + 对拍 java 列激活：`planned`（plan：`ai-dev/plans/xlang-execution-optimization/2026-08-20-0030-2-i2-java-backend-skeleton.md`，2026-08-20 两轮独立子agent draft review 至共识（round-2 GO 0 Blocker/0 Major）；plan 内定稿：简单方法调用子集边界=宿主反射分派族不含 CallFunc 族、java 列测试域执行通路=nop-javac 内存编译（设计文本未枚举的测试域扩展裁定）、包装器契约责任链 I2 定约/I4 验证记账（W4-audit R1-2 承接）） — 依赖：I1
  - 范围：新模块 `nop-xlang-java` 落盘（`nop-kernel` 下，pom/包结构骨架）；表达式子集（字面量/slot 标识符/算术/逻辑/比较/简单方法调用）树→Java 源码转译；共享语义 helper 基座（数值提升/宽松比较等，定义在 nop-xlang，解释器同步改用——行为不变由既有测试保证）；EvalMethod 调用约定（static + 首参 `IEvalScope $scope`，纯表达式单元经 `EvalMethodInvoker` 包装）；模板入口包装器契约定稿（xpl/xlib 追加 `IEvalOutput $out` 隐参，设计 java §七）；SourceLocation 静态常量内嵌
  - 验收：表达式 corpus java 列 vs 解释器列对拍全绿（含 java 列身份断言=生成类实例）；解释器改用共享 helper 后 nop-xlang 既有测试全绿；生成源码含 SourceLocation 静态常量（异常语义断言可执行）；mission.json commands 在模块落盘的同一次变更中切换为含 `:nop-xlang-java` 的口径（如 `./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C`，build/lint/typecheck 同步）——"模块落盘即切换"裁定，见 W3-supplement 条目备注
  - 复用：janino `EvalMethod` 先例（`JaninoScriptCompiler`/`EvalMethodInvoker`）；`nop-javac` 仅可选诊断性编译校验（不承担产物编译）
- I3. java 转译覆盖 A（数据与作用域族）：`todo` — 依赖：I2
  - 范围：作用域链访问/类型操作/对象集合构造访问/绑定守卫调试/slot 写族（类别划分=设计 java §三分类表）
  - 验收：对应类别 corpus 扩充后 java 列 vs 解释器列对拍全绿；覆盖矩阵推进（类别内 exec/ 基线节点类逐一注册）
- I4. java 转译覆盖 B（函数闭包/控制流/输出节点生成族）+ 覆盖矩阵闭环：`todo` — 依赖：I3
  - 范围：函数/闭包（含可变 slot 闭包捕获 cell 契约，设计 java §三）；控制流（ExitMode 传播边界不变式：不跨函数/闭包边界传播，语句位置一一对应）；输出/节点生成族（`$out` API 调用序列）
  - 验收：全类别 corpus java 列 vs 解释器列对拍全绿；覆盖矩阵全绿（exec/ 137 文件基线逐节点类注册断言，新增节点类红灯）
- I5. nop-xlang-truffle 模块骨架 + 帧/slot 映射 + 表达式子集翻译 + 翻译缓存：`planned`（plan：`ai-dev/plans/xlang-execution-optimization/2026-08-20-0030-3-i5-truffle-backend-skeleton.md`，2026-08-20 两轮独立子agent draft review 至共识（round-2 GO 0 Blocker/0 Major）；plan 内定稿：无 resourcePath 动态源缓存=源内容哈希键、I5 以 EXCLUSIVE 过渡形态落地（SHARED 切换归 I8）、不泄漏断言口径=nop-kernel 域内 truffle/polyglot 依赖仅本模块+nop-js/buildtools 豁免） — 依赖：I1（可与 I2-I4 并行）
  - 范围：新模块 `nop-xlang-truffle` 落盘（`nop-kernel` 下；truffle-api/dsl-processor/polyglot 25.x LTS 钉版引入 + 常规冒烟复核——Q5 确认点已关闭，设计 truffle 02 §二）；XLangLanguage（id `xl`、`ContextPolicy=SHARED` 终态注册；EXCLUSIVE 过渡形态=翻译正确性对拍保守载体，两形态各自是验证载体）；XLangContext（输出缓冲线程绑定）；帧/slot 映射（`LexicalScopeAnalysis` slot 布局 → FrameDescriptor/FrameSlot，kind 标注仅限可推断类型、不虚构）；表达式子集翻译（与 I2 同子集）；翻译缓存（键=resourcePath+树指纹；无 resourcePath 动态源缓存形态由 plan 定稿，设计 truffle 02 §七）
  - 验收：表达式 corpus truffle 列 vs 解释器列对拍全绿（允许 EXCLUSIVE 过渡形态；含 truffle 列身份断言=翻译 AST 经 CallTarget 执行）；org.graalvm.* 依赖仅出现在本模块 pom（不泄漏断言）；mission.json commands 在模块落盘的同一次变更中追加 `:nop-xlang-truffle`（模块落盘即切换，同 W3-supplement 裁定）
  - 复用：SL 参考实现（`~/sources/graal`）；`LexicalScopeAnalysis`
- I6. truffle 翻译覆盖 A（数据与作用域族，类别同 I3）：`todo` — 依赖：I5
  - 验收：对应类别 corpus truffle 列 vs 解释器列对拍全绿；覆盖矩阵推进（与 java 侧同基线同口径）
- I7. truffle 翻译覆盖 B（函数闭包/控制流/输出族，类别同 I4）+ 两级内联缓存 + 覆盖矩阵闭环：`todo` — 依赖：I6
  - 范围：ExitMode→控制流异常族（SL 模式三值一一对应）；两级内联缓存（一级 CallTarget 身份/二级直达调用形态；禁止函数实例身份与任何运行时值身份缓存，设计 truffle 02 §六）；语义敏感操作 generic/fallback 走共享 helper
  - 验收：全类别 corpus truffle 列 vs 解释器列对拍全绿；覆盖矩阵全绿（exec/ 137 文件基线同 I4 口径）
- I8. truffle 多线程运行时（Context 池 + 共享 Engine + SHARED 形态）：`todo` — 依赖：I7
  - 范围：池租借协议（租借注入输出缓冲/全局作用域句柄、归还前清空；context 内状态只在 enter()..leave() 窗口有效，设计 truffle 02 §五）；共享 Engine 单例 + enter/leave 批求值（可重入）；SHARED 形态并发正确性验证载体；翻译缓存淘汰（容量上限/LRU）；单元级翻译失败记观测事件（衔接 I9 决策树动态路径第三分支）
  - 验收：SHARED 形态并发对拍——多线程经池并发求值同一/不同编译单元，结果与单线程解释器求值一致 + 无跨 Context 串值断言（输出缓冲/作用域隔离）全绿；池租借协议有正/负测试（归还后残留状态检测）
- I9. 统一后端选择机制（注册 SPI + 决策树 + 配置开关 + 降级观测）：`todo` — 依赖：I4, I8
  - 范围：后端注册 SPI（显式注册表，`ScriptCompilerRegistry`/`@GlobalInstance` 先例；条目=能力集/可用性状态/不可用原因；不做 classpath 扫描）；统一决策树（静态路径=扫描清单成员资格；动态路径含单元级翻译失败第三分支；单跳降级 java→解释器、truffle→解释器）；配置开关（java/truffle 各自 enable + 强制解释器诊断模式；不设"全局默认后端"）；降级观测（WARN+指标+注册表不可用条目；日志/指标命名契约落 docs-for-ai）；动态编译出口清单化核验（`ExpressionExecutor` 系"运行时字符串→Executable 树"出口统一回调注册表裁决，禁止各入口自带后端 if/else）
  - 验收（对拍引用口径）：路由场景矩阵（静态→java / 动态→truffle / 各降级→解释器 / 单元级降级判 FAIL）逐场景断言后端身份 + 执行结果一致性——**以 I1 对拍框架运行结果为验收输入**（显式引用关系：场景用例经框架执行与断言，非独立重写比对）
  - 复用：`ScriptCompilerRegistry`
- I10. java 生成类加载集成（绑定决策树 + RCM 缓存）：`todo` — 依赖：I9
  - 范围：统一决策树静态路径 java 侧落地（设计 java §五）：扫描清单成员资格判定 + 生成类清单树指纹一致性校验（施加对象=Executable 树指纹，防 stale、Delta 变更不漏检）+ 生成类优先/解释器兜底 + 绑定结果随 `ComponentCacheEntry` 缓存 + 降级观测事件分级（stale 缺陷 vs 租户差异化树预期稳态降级）
  - 验收：绑定路由对拍——静态单元经绑定后 java 列 vs 解释器列一致 + 身份断言；指纹失配/清单缺失降级解释器 + 观测事件断言；清单外资源走动态路径不记降级事件断言（测试以转译器 API 合成双清单产物，生产管线产物归 I11）
  - 复用：`ResourceComponentManager`/`IResourceLoadingCache`
- I11. 构建集成 + native image 兼容 + docs-for-ai 同步：`todo` — 依赖：I10
  - 范围：codegen/xgen 任务注册（扫描口径：`_vfs/**/*.xpl`/`*.xlib` 确定；其余 `_vfs` XDSL 类型按 live 清点定稿并显式记录纳入/排除及理由——xtask/xgen/xrun、嵌入 xpl 片段等，不引入 live 不存在类型）；双清单分离产物（构建期扫描清单 should-set + 生成类清单 resourcePath→类名+树指纹；格式与存放路径由 plan 定稿）；任务经与运行时相同编译前端取树、重生成幂等、不绕过 codegen 管线；native image 兼容（`GraalvmConfigGenerator` 管线复用 + 生成类直编镜像验证）；truffle 模块镜像排除机制（profile/classifier）；docs-for-ai 新模块开发指南同步
  - 验收：端到端对拍——真实 `_vfs` 静态资源经任务扫描→转译→常规编译→加载绑定后 java 列 vs 解释器列全量一致 + 身份断言；构建管线漏跑可观测断言（java 后端启用但扫描清单缺失→注册不可用条目 + 全局 WARN）；重生成幂等断言
  - 复用：codegen/xgen 任务体系；`GraalvmConfigGenerator`/`nop-vfs-index.txt`
- I12. 性能基准 + 全量三后端对拍收口 + 独立 closure audit：`todo` — 依赖：I11（传递覆盖 I1-I10 全链）
  - 范围：三后端基准（解释器/java/truffle × GraalVM 与 stock JVM 两形态；Context 池创建/销毁成本与池大小调优实测；静态资源 JVM 形态生成物缺失不借道 truffle 的机会成本量化；Q1/Q4 watch-only 重评估触发口径量化定义）；mission.json commands 汇总口径核验
  - 验收：全量三后端对拍套件**直接运行**全绿（解释器/java/truffle 三列全 corpus、静态/动态列适用性、后端身份断言——不得以引用形式弱化）；基准数据落 repo（报告 + 可复跑入口）
  - 复用：`nop-benchmark/nop-benchmark-xpl`（JMH 既有先例）
- ★ **Milestone: 双后端落地**（I1-I12 全部 done）：`todo` — 派生：I1-I12

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | 未开始，无计划 |
| `planned` | 有计划，通过独立 draft review |
| `done` | 完成，通过独立 closure audit |

> Milestone 状态为派生：阶段一 = W1-W4 全部 done；阶段二 = I1-I12 全部 done。

## Framework / platform reuse

| Capability | Provider | Notes |
| --- | --- | --- |
| Executable 树编译前端 | `nop-xlang` `XplCompiler`/宏全展开/slot 分配（`LexicalScopeAnalysis`） | 两后端只做树→目标翻译，不改前端 |
| 模型缓存 | `ResourceComponentManager`（IResourceLoadingCache） | Java 后端"生成类优先/解释器兜底"加载挂点 |
| Java 源编译既有通路 | `nop-javac`（JdkJavaCompiler）+ `xlang/janino`（EvalMethod 约定先例） | 转译器调用约定与对拍参照 |
| GraalVM 配置生成 | `nop-codegen` `GraalvmConfigGenerator`/`nop-vfs-index.txt` | I11 native 兼容复用 |
| Truffle 知识与参考 | `ai-dev/design/xlang-truffle/01-truffle-knowledge.md` + `~/sources/graal` SL 实现 | W1 设计输入，不重新调研 |
| 脚本引擎注册先例 | `ScriptCompilerRegistry`（nop-xlang） | I9 后端注册 SPI 参照 |
| 依赖坐标 | `org.graalvm.truffle:truffle-api` + `truffle-dsl-processor` + `org.graalvm.polyglot:polyglot`（25.x） | I5 引入，版本钉 LTS 线 |

## Current baseline

**Already shipped:**
- 解释器执行链完整：`XplModelParser` → Executable 树 → `DefaultExpressionExecutor`/`EvalRuntime`，宏/标签全编译期展开
- `nop-js` 已验证 polyglot Context 嵌入模式（GraalJS）
- `nop-codegen` GraalVM 配置生成管线（reflect/proxy/vfs-index）

**Main gaps (blocking this roadmap):**
- 无 Executable→Java 转译后端（native image 下解释执行慢）
- 无 Truffle 后端（JVM 下运行时动态脚本无 JIT）
- 无多后端选择机制与三后端对拍框架

## Stages

| # | Stage | Owner plan | Deps | Critical path | Reuse |
| --- | --- | --- | --- | --- | --- |
| 1 | 双后端设计文档（execution/java/truffle 三组） | W1-design | — | **Yes** | 知识文档/SL 源码 |
| 2 | 设计独立审查（≥2 轮至 PASS） | W2-review | W1-design | **Yes** | ai-dev/skills 审查 prompt |
| 3 | 回填实现 work items + mission.json 更新 | W3-supplement | W2-review | **Yes** | — |
| 4 | roadmap workitem 审核 | W4-audit | W3-supplement | **Yes** | openAuditPrompt |
| ★ | 设计与计划就绪 | — | W1-W4 | — | — |
| 5 | 三后端对拍验证框架 + corpus v1 | I1 | W4-audit | **Yes** | Nop AutoTest |
| 6 | nop-xlang-java 骨架 + 子集转译 + 共享 helper 基座 | I2 | I1 | Yes | janino EvalMethod 先例 |
| 7 | java 转译覆盖 A（数据与作用域族） | I3 | I2 | Yes | 共享 helper |
| 8 | java 转译覆盖 B（函数/控制流/输出族）+ 覆盖矩阵闭环 | I4 | I3 | Yes | — |
| 9 | nop-xlang-truffle 骨架 + 帧映射 + 子集翻译 + 翻译缓存 | I5 | I1 | **Yes** | SL 参考实现/LexicalScopeAnalysis |
| 10 | truffle 翻译覆盖 A（类别同 I3） | I6 | I5 | **Yes** | — |
| 11 | truffle 翻译覆盖 B + 两级内联缓存 + 覆盖矩阵闭环 | I7 | I6 | **Yes** | 共享 helper |
| 12 | truffle 多线程运行时（Context 池 + SHARED） | I8 | I7 | **Yes** | 知识文档 §五 |
| 13 | 统一后端选择机制（SPI + 决策树 + 降级观测） | I9 | I4, I8 | **Yes** | ScriptCompilerRegistry |
| 14 | java 生成类加载集成（绑定决策树 + RCM 缓存） | I10 | I9 | **Yes** | ResourceComponentManager |
| 15 | 构建集成 + native 兼容 + docs 同步 | I11 | I10 | **Yes** | GraalvmConfigGenerator |
| 16 | 性能基准 + 全量三后端对拍收口 + 独立 closure audit | I12 | I11 | **Yes** | nop-benchmark-xpl（JMH） |
| ★ | 双后端落地 | — | I1-I12 | — | — |

## Dependency graph

```mermaid
graph TD
    W1[W1-design] --> W2[W2-review ≥2轮PASS] --> W3[W3-supplement] --> W4[W4-audit]
    W4 --> M1{Milestone 设计就绪}
    W4 --> I1[I1 对拍框架+corpus v1]
    I1 --> I2[I2 java骨架+子集转译] --> I3[I3 java覆盖A] --> I4[I4 java覆盖B+矩阵闭环]
    I1 --> I5[I5 truffle骨架+帧映射] --> I6[I6 truffle覆盖A] --> I7[I7 truffle覆盖B+内联缓存] --> I8[I8 truffle多线程]
    I4 --> I9[I9 统一选择机制]
    I8 --> I9
    I9 --> I10[I10 java加载集成] --> I11[I11 构建集成+native+docs] --> I12[I12 基准+全量对拍收口]
    I12 --> M2{Milestone 双后端落地}
```

## 审查与验证纪律（本 roadmap 特有约束）

1. **设计 gate 硬约束**：W2-review 不少于两轮独立子agent（无共享上下文），PASS（0 P0/P1）前不得进 W3；审查报告持久化到 `ai-dev/audits/xlang-execution-optimization/` 并在 W2 条目回链。
2. **阶段二冻结**：W4-audit done 前，任何 I 系列工作项不得起草 plan（阶段二条目已由 W3-supplement 定稿；解冻点仍是 W4-audit done）。**解冻点已达成（2026-08-20 W4-audit done），I 系列可起草 plan。**
3. **对拍不变式**：每个 I 系列实现 plan 的验收必须包含"同一 Executable 树多后端执行结果一致"的对拍断言；回归不允许削弱现解释器测试。
4. **生成物纪律**：`_gen/` 与 `_` 前缀产物不可手改（AGENTS.md 硬规则）；Java 后端只生成源码与加载器，不绕过 codegen 管线。
5. **模块边界**：新模块依赖方向 `nop-xlang-java`/`nop-xlang-truffle` → `nop-xlang` → `nop-core`，禁止反向；`nop-xlang-java` → `nop-javac` 仅限构建期可选诊断性编译校验边（设计 execution 01 §二虚线边，非运行时依赖）；Truffle 依赖只出现在 nop-xlang-truffle（不得泄漏进内核其他模块）。
