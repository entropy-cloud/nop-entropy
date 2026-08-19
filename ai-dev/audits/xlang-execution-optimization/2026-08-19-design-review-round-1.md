# W2-review Round 1 设计审查报告（xlang-execution / xlang-java / xlang-truffle）

> Mission: xlang-execution-optimization
> Plan: `ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-2-w2-design-review-gate.md`（Phase 1）
> Round: 1
> Date: 2026-08-19
> Reviewer: 独立子 agent（fresh session，opencode task `ses_fe5c91112ffeLVranQ62BOsLED`，模型 zhipuai-coding-plan/glm-5.2，未参与 W1 撰写）
> Verdict: **FAIL**（0 P0 / 2 P1 / 11 P2 → 修复后进入 round 2）

## 审查输入包（主 agent 组装）

- 三组设计文档 8 篇（execution 00/01/README、java 01/README、truffle 00/02/README）
- `ai-dev/design/xlang-truffle/01-truffle-knowledge.md`（知识层，决策迁出与 Open Questions 清账核对对象）
- 审查基准：`ai-dev/design/00-design-writing-guide.md`、`ai-dev/backlog/xlang-execution-optimization-roadmap.md`（W1 内容点 4+6+5）
- live 锚点（主 agent 预核实全部存在，审查者抽查引用一致性）：`exec/`（实测 137 文件）、`ScriptCompilerRegistry`、`JaninoScriptCompiler`、`ResourceComponentManager`、`nop-kernel/nop-javac`（`JdkJavaCompiler`）、`nop-frontend-support/nop-js`
- W1 移交的遗留分歧清单：空（W1 Phase 4 收口结论"遗留分歧清单为空"）；W1 另移交 3 项确认点（02 §九末尾"移交 W2-review 输入清单"）
- 审查口径：本 plan 固化的三级判级（P0/P1/P2；方法论资产 P3 归入 P2），文档式证据格式（文档路径 + 章节/行号 + 原文摘录）

## Findings（审查者原文）

### ① roadmap W1 内容点逐点覆盖核对（15 点）

15/15 点均有实质章节（execution 4 点落 execution 01 §三/§四/§五/§二；java 6 点落 java 01 §二/§三/§四/§五/§六/§七；truffle 5 点落 02 §三/§四/§五/§六/§八），无内容点缺失，本维度无 finding。

### ② design-writing-guide 合规

三要素（选了什么/为什么/拒绝什么）齐备；伪代码属语义契约（guide 允许）；无 analysis/discussions 引用；三 README 三要件齐备。（残留问题见 R1-9/R1-10/R1-11/R1-13。）

### ③ 跨文档一致性

选择机制 / 依赖方向 / Truffle 不泄漏 / 对拍三层断言在四组表述间一致，未发现跨文档决策冲突——W1"遗留分歧清单为空"结论成立。nop-javac 表述两文档互相一致但与 live 机制矛盾，归 R1-2。

### ④ live 代码可行性核对（正向核实全部通过）

- `exec/` 实测 137 文件；节点分类表抽查 45+ 类名全部存在
- `ScriptCompilerRegistry.java:19-39` @GlobalInstance + registerCompiler/getCompiler 逐字吻合；Janino 注册于 `XLangCoreInitializer.java:62`
- EvalMethod 约定：`JaninoScriptCompiler.java:56`（setStaticMethod(true)）、`:60-62`（首参 `$scope`，`ExprConstants.java:78`）、`:75-76`（MethodInvoker/EvalMethodInvoker→IEvalFunction）
- RCM/IResourceLoadingCache/ComponentCacheEntry（`ResourceComponentManager.java:67/88/124/409/426`）
- `IExecutableExpression.execute(executor, EvalRuntime)`（`IExecutableExpression.java:44`）；ExitMode 经 EvalRuntime 传递（`BreakExecutable.java:40`；ExitMode 位于 nop-core `io.nop.core.lang.eval`）
- `EvalFrame(Object[] stack)`（`EvalFrame.java:18-20`）；nop-js polyglot 嵌入（`JavaScriptWorker.java:344`）
- **附加实测**：从 Maven Central 拉取 truffle-api / polyglot / truffle-dsl-processor 25.2.4，基础 class 均 major 61（Java 17），overlay 仅 versions/9 与 versions/21——25.x LTS 钉版与 JDK 21 基线兼容的前提成立（Q5 条件钉版担忧解除）

### ⑤ 01 知识层决策迁出与 Open Questions 清账

§十清账表 5 行与 02 §九 Q1–Q5 一一对应、互相回链一致；移交 3 项确认点完整存在；架构级决策已全部迁出（细节级残留见 R1-9）。

### [R1-1] 对拍框架未定义"单元级降级"判定规则，存在 vacuous pass 风险 — **P1**

- **文档**: `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五（L112-117）
- **证据**: "后端未启用时……对应矩阵列自动跳过"仅覆盖"后端未启用"；而 java 列生成类缺失/指纹失配在绑定决策树中降级为解释器执行（java 01 §五 L79-81）。
- **现状**: 对拍框架未规定"后端已启用但该编译单元降级"时该列行为；降级后该列实际执行解释器，与解释器列恒等，对拍必然通过。
- **风险**: "静默执行旧逻辑"这一自认最危险缺陷形态在测试环境不可见——stale 生成类、codegen 漏跑从对拍眼皮下逃逸，any-divergence FAIL 承诺被架空。
- **建议**: 补硬规则：后端已启用的列发生单元级降级判 FAIL（非跳过）；harness 断言每列实际执行的后端身份。
- **信心水平**: 确定

### [R1-2] "nop-javac 通路"复用定位与"无自定义 ClassLoader"自相矛盾，编译执行主体未裁定 — **P1**

- **文档**: `xlang-java/01-architecture-baseline.md` §二（L25-26 图）、§五（L86）、§六（L93）；`xlang-execution/01-architecture-baseline.md` §二（L24 依赖边）
- **证据**: java §二图 `JC["常规 javac (nop-javac 通路)"]`；§五"无运行时动态编译、无自定义 ClassLoader"。
- **live 代码**: `JdkJavaCompiler.java:39-119` 是 `javax.tools` 内存编译 + `ClassLoaderImpl`（自定义 ClassLoader），无落盘输出模式；现役唯一生产使用者 `GenAopProxy.java:88-90` 即此模式。
- **现状**: `_gen/` 源码由谁编译未裁定：按图实现（任务进程内走 JdkJavaCompiler）则产物落在内存 ClassLoader，与"classpath 常规加载""无自定义 ClassLoader""native 直编"三条硬决策直接冲突。
- **风险**: I6 构建集成按错误机制起步返工；两名实现者各取一种读法产生分叉。
- **建议**: 裁定编译责任主体（应为"任务只产源码与清单 + 常规构建编译"），修正图/依赖边/表述。
- **信心水平**: 很可能

### [R1-3] 生成类清单"内容指纹"比对对象口径不一（源资源指纹 vs 树指纹） — **P2**

- **文档**: `xlang-java/01-architecture-baseline.md` §五（L77 伪码 vs L85）、§六（L92）
- **证据**: 两处分别以"源资源指纹"与"树指纹"为清单存储对象；Delta 合并场景二者不等价。
- **建议**: 统一为树指纹（伪码已是此意），修正 §五/§六措辞。
- **信心水平**: 确定

### [R1-4] 动态路径裁决入口归属自指，注入缝未命名 — **P2**

- **文档**: `xlang-execution/01-architecture-baseline.md` §三判定时机表（L55）
- **证据**: "归属：选择机制裁决入口"——用入口解释入口。
- **建议**: 命名统一接入缝（contract 级）。
- **信心水平**: 很可能

### [R1-5] "全局默认后端"配置输入未被决策树消费 — **P2**

- **文档**: `xlang-execution/01-architecture-baseline.md` §三（L47 vs L59-71）
- **证据**: 判定输入 4 含"全局默认后端"，决策树无对应分支；非 auto 取值与判据互斥叙事冲突。
- **建议**: 删除该输入或显式消费并说明关系。
- **信心水平**: 确定

### [R1-6] SHARED/并发形态的正确性验证方法无框架承载 — **P2**

- **文档**: `xlang-execution/01-architecture-baseline.md` §五；`xlang-truffle/00-vision.md` §三.3；`xlang-truffle/02-architecture-baseline.md` §五（L117）
- **证据**: truffle 00 把并发正确性列为一票否决级成功标准，但 SHARED 上池后的并发复验方法无文档定义；对拍矩阵无并发形态维度。
- **建议**: 在对拍框架或 I4 验收标准中补并发正确性验证载体。
- **信心水平**: 很可能

### [R1-7] Context 池租借的 per-rental 状态注入/重置协议未定义 — **P2**

- **文档**: `xlang-truffle/02-architecture-baseline.md` §三（L46）与 §五（L115）
- **证据**: Context 持有"每次求值"的句柄与输出缓冲，池租借是"包住一批求值"——跨租借状态清理协议缺失。
- **建议**: 补租借注入/归还清空契约。
- **信心水平**: 很可能

### [R1-8] FrameSlot primitive kind 标注的类型信息来源未定义 — **P2**

- **文档**: `xlang-truffle/02-architecture-baseline.md` §四（L71）
- **证据**: live `EvalFrame` 为 `Object[]`，`LexicalScopeAnalysis` 只产 slot 布局无类型信息——标注所需静态类型翻译期不存在。
- **建议**: 改为"能推断类型的 slot 才标注 kind"或删弱条款。
- **信心水平**: 很可能

### [R1-9] 01 知识层残留少量决策性/规范性表述 — **P2**

- **文档**: `ai-dev/design/xlang-truffle/01-truffle-knowledge.md` §五表（L167）、§3.1（L74）
- **证据**: "`BinaryExecutable` 的算术按 long/double/Object 三档特化"（xlang 翻译结构决策，02 无对应裁定）；示例代码注释内嵌选定值。
- **建议**: 三档特化改为非规范性提示或迁入 02；注释仅留指针。
- **信心水平**: 确定

### [R1-10] xlang-java 目录无本地 Vision 层文件（guide 字面合规张力） — **P2**

- **文档**: `xlang-java/README.md` vs `00-design-writing-guide.md` L96-103
- **证据**: guide"每个子系统必须包含两层"；xlang-java README 声明愿景在 ../xlang-execution/00-vision.md。
- **建议**: guide 增补"多目录共享 Vision 层"条款，或放 3 行指针文件。
- **信心水平**: 确定

### [R1-11] README 豁免条款未回写 design-writing-guide — **P2**

- **文档**: `xlang-truffle/README.md` L17
- **证据**: guide 本体无"外部框架知识参考层"类别；豁免是单方声明。
- **建议**: 把该层级类型写入 guide。
- **信心水平**: 确定

### [R1-12] 决策文档引用机器本地克隆路径，跨机器不可复核 — **P2**

- **文档**: `xlang-truffle/02-architecture-baseline.md` §二（L29 `~/sources/graal` 行号级证据）
- **建议**: 改为"上游仓库路径为主，本地克隆为便利"。
- **信心水平**: 确定

### [R1-13] truffle 目录编号与阅读顺序错位；01 含迁出过程叙事残留 — **P2**

- **文档**: `xlang-truffle/README.md` L21；`01-truffle-knowledge.md` §七/§十
- **建议**: 编号保持现状（已固化被外部引用）；过程叙事下次修订时压缩。
- **信心水平**: 确定

### 严重程度分布

| 级别 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 2 | R1-1, R1-2 |
| P2 | 11 | R1-3 ~ R1-13 |

**Verdict: FAIL**（须修复 2 条 P1 后进入 round 2 全量复审）

## 回应段（主 agent 逐条处置记录，round 2 开启前置条件）

| Finding | 处置 | 落点 |
|---|---|---|
| R1-1 (P1) | **已修复**：§五补硬规则"单元级降级判 FAIL 不判跳过"+ harness 后端身份断言要求 | execution 01 §五（新增 bullet） |
| R1-2 (P1) | **已修复**：裁定编译执行主体为常规构建（Maven/javac 编译 `_gen/` 源码）；nop-javac 明确为内存编译+自定义 ClassLoader 通路、与本架构硬决策不兼容，仅可选承担构建任务诊断性编译校验；两处图修正（java §二图 JC→BC 常规构建编译 + NJ 改虚线诊断边；execution §二 NJ 边改虚线并改标注） | java 01 §二（图 + 新增结构决策 bullet）；execution 01 §二（图） |
| R1-3 (P2) | **已修复**：指纹对象统一为 Executable 树指纹，显式拒绝"源资源指纹"并说明 Delta 漏检理由 | java 01 §五、§六 |
| R1-4 (P2) | **已修复**：判定时机表"运行时求值期"归属改为契约级接入缝描述（所有运行时字符串→Executable 树编译出口统一回调注册表裁决，禁止各入口自带 if/else） | execution 01 §三 |
| R1-5 (P2) | **已修复**：删除"全局默认后端"输入，默认语义固定 auto 并写明拒绝理由；§七拒绝表补一行 | execution 01 §三判定输入 4、§七 |
| R1-6 (P2) | **已修复**：02 §五补"SHARED 形态正确性验证载体"条款（并发求值断言与单线程一致、无跨 Context 串值，纳入 I4 验收）；00-vision §三.3 补验证载体指针 | truffle 02 §五、00-vision §三.3 |
| R1-7 (P2) | **已修复**：02 §五补"租借状态协议"契约（租借注入/归还清空，状态只在 enter..leave 窗口有效） | truffle 02 §五 |
| R1-8 (P2) | **已修复**：primitive kind 标注加前置约束（仅可推断 slot 标注，其余 Object，不虚构类型信息；覆盖率归实现计划） | truffle 02 §四 |
| R1-9 (P2) | **已修复**：§五表三档特化改为非规范性提示（策略由 02 裁定）；§3.1 注释改为"枚举值演示 + 指针" | 01-truffle-knowledge §五、§3.1 |
| R1-10 (P2) | **裁定遗留**：guide 非 W1 产出（修复范围仅限 W1 文档）；xlang-java README 已显式标注愿景归属（guide L109 替代路径）。根治需改 guide——列入移交 W3 清单第 4 项 | 移交 W3 清单 |
| R1-11 (P2) | **裁定遗留**：同上，guide 增补"外部知识参考层"类别列入移交 W3 清单第 4 项；truffle README 豁免边界声明现状已自洽 | 移交 W3 清单 |
| R1-12 (P2) | **已修复**：02 §二证据路径改为"上游 oracle/graal 仓库路径为准，~/sources/graal 为本地 sparse clone 便利副本" | truffle 02 §二 |
| R1-13 (P2) | **裁定遗留**：编号已固化被外部引用，README 已解释阅读顺序（维持现状）；01 §七/§十现形态为指针/清账表（非演进叙事主体），压缩属表述优化非必需 | 本报告裁定记录 |

附加收获（无需修复，状态更新）：round-1 审查者对 Maven Central 25.2.4 构件的字节级实测确认了 Q5 条件钉版前提——已在 02 §二把该确认点标记为"已提前满足，I3 做常规冒烟复核"。

## 移交 W3 清单（round 1 汇总，out-of-scope 项供 W3 回填阶段二时核对）

1. 对拍 harness"后端身份断言"实现机制（随 R1-1 修复已定稿为设计要求，实现细节归 I1 验收标准）
2. SHARED 池化形态并发正确性验证的归属工作项（R1-6；建议进 I4 验收标准——设计已写明载体与归属）
3. Context 池租借 reset/注入协议实现细节（R1-7；契约已定稿于 02 §五，实现归 I4）
4. design-writing-guide 增补"外部知识参考层"与"多目录共享 Vision 层"条款（R1-10/R1-11 根治；guide 非 W1 产出）
5. Q5 条件钉版条款可提前关闭（round-1 已实测 25.2.4 构件 class 61 / overlay ≤21；02 §二已更新状态，I3 常规冒烟即可）
6. 静态资源 JVM 形态下生成物缺失时"不借道 truffle"的机会成本——I7 基准顺带量化，为未来是否允许"静态路径 truffle 兜底"留数据
7. roadmap 纪律 5 依赖白名单是否补记 `nop-xlang-java -.-> nop-javac`（构建期可选诊断边，非产物编译依赖）——roadmap 非 W1 产出，由 W3 裁定
