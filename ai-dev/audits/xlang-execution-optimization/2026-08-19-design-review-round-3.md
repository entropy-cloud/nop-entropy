# W2-review Round 3 设计审查报告（xlang-execution / xlang-java / xlang-truffle）

> Mission: xlang-execution-optimization
> Plan: `ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-2-w2-design-review-gate.md`（Phase 2）
> Round: 3
> Date: 2026-08-19
> Reviewer: 独立子 agent（fresh session，opencode task `ses_fe5ab62eeffe3HVrc45RXgo748`，模型 zhipuai-coding-plan/glm-5.2；未参与撰写与前轮审查、未读 round-1/2 报告——独立性成立）
> Verdict: **FAIL**（0 P0 / 1 P1 / 6 P2 → 修复后进入 round 4）

## 审查输入

同一输入包（三组设计文档 round-2 修复后版本），三级判级口径与文档式证据格式在 prompt 中声明。全量复审。

## Findings（审查者原文）

### [R3-1] EvalMethod 调用约定对 live 求值管线的事实误述：输出缓冲并不经 `IEvalScope` 贯穿 — **P1**

- **文档**: `xlang-java/01-architecture-baseline.md` §七（表"首参 `$scope`"行 + 末段）、§三（输出行）
- **证据**: live `EvalRuntime.java:7-10`——scope/exitMode/currentFrame/out 为 EvalRuntime 并列字段；IEvalScope 无任何输出缓冲/帧/exitMode 访问器；解释器 `execute(executor, EvalRuntime)` 输出经 `rt.getOut()`。§七却称"输出缓冲、求值上下文都经 `IEvalScope` 贯穿"；且约定生成方法隐参仅 `$scope`，生成代码无通路获得 `IEvalOutput`（janino 先例只服务无输出表达式，不构成 xpl 模板先例）。
- **现状**: W1 内容点"EvalMethod 调用约定"的沿用论证建立在与 live 矛盾的事实上，约定漏掉输出缓冲传参设计。
- **风险**: I1 实现期立即撞墙（OutputText/GenNode 族无法生成），被迫现场改约定，对拍第三层与 §三 翻译模式返工。
- **建议**: 修正事实表述（输出缓冲/帧/exitMode 由 `EvalRuntime` 携带），约定显式扩展为"隐参 = `$scope` + 输出缓冲"，说明与 janino 先例真实异同。
- **信心水平**: 确定

### [R3-2] java 组内部编译单元类型枚举不一致（§二点名 expr/xbiz，§六口径为 xpl/xlib + I6 定稿） — **P2**

- **证据**: §二"每个 xpl / xlib / expr / xbiz 编译单元生成一个类" vs §六已修正口径；live 无 `expr` 常量；xbiz 在 `BizConstants.FILE_EXT_XBIZ`（nop-biz），非 nop-kernel 口径。
- **建议**: §二收敛为"每编译单元一个类（类型集见 §六/I6 口径）"，枚举只在 §六出现一次。
- **信心水平**: 确定

### [R3-3] 设计文档硬引用可变 roadmap 条目号（I6/I7/I4），W3 增删拆并后引用悬空 — **P2**

- **证据**: roadmap W3 条目明文"修订阶段二 I1-I7（增删拆并）"；设计文档十余处规范性条款写死条目号。
- **建议**: W3 执行清单加"回检设计文档 I 系引用"步骤，或改引语义而非条目号。
- **信心水平**: 很可能

### [R3-4] truffle 02 §九 含审查轮次归因的过程叙事 — **P2**

- **证据**: 移交清单第 1 条"round-1 独立审查对 Maven Central…"（§二已载有证据本身）。
- **建议**: 删轮次归因，保留证据陈述。
- **信心水平**: 确定

### [R3-5] 01 知识层残留少量决策性/处方性表述（无冲突） — **P2**

- **证据**: §六"xlang ExitMode 直接照抄结构"；§五处方列无总注；与 00/02 逐条核对均一致无冲突。
- **建议**: §六行改决策指针；§五补"是否采用由 02/实现计划裁定"总注。
- **信心水平**: 很可能

### [R3-6] 动态路径主场景（无 resourcePath 的运行时字符串）翻译缓存键未定义 — **P2**

- **证据**: §七"翻译缓存按 resourcePath"；动态路径判定对象含无 resourcePath 的字符串表达式。
- **风险**: 高频重复求值每次重翻译，stock JVM 形态可能劣于解释器，触碰 00 §三底线。
- **建议**: §七补契约（源内容哈希键或编译出口持有；具体归 I3/I4）。
- **信心水平**: 很可能

### [R3-7] 控制流"原生语句一一对应"缺函数边界/语句粒度不变式声明 — **P2**

- **证据**: live ExitMode 是运行时状态协议——`SeqExecutable.java:63`/`BlockExecutable.java:70` 每语句后检查、`ExecutableFunction.java:98/119/140` 函数边界清零（不跨函数传播）；Java 原生 break 在私有方法内无目标循环即编译错误。
- **建议**: §三补边界不变式（ExitMode 不跨生成方法边界传播；闭包内非局部跳转映射为闭包返回）。
- **信心水平**: 很可能

### 无 finding 维度结论（审查者原文摘要）

- **① 内容点覆盖 15/15**：execution 4 / java 6 / truffle 5 均实质章节。
- **③ 跨文档一致性**（除 R3-2）：选择机制/扫描清单/静态与动态路径管辖、单跳降级链、依赖方向与 Truffle 零泄漏、对拍三层断言与身份断言、"未启用跳过 vs 单元级降级判 FAIL"区分、分工判据与部署形态矩阵——三组含 README 一致。
- **④ live 可行性**（除 R3-1/R3-6/R3-7）：exec/ 实数 137 ✓；分类表点名类每类抽查全部存在 ✓；ScriptCompilerRegistry/Janino EvalMethod 约定逐项 ✓；RCM ✓；JdkJavaCompiler"不承担产物编译"定位准确 ✓；XlangConstants 类型常量 ✓；EvalFrame/EvalScopeImpl/ExprEvalAction/LexicalScopeAnalysis/MathHelper/ExitMode 位置一致 ✓；§五伪代码与统一决策树一致 ✓。
- **⑤ 知识层清账**（除 R3-5）：§十与 02 §九五条一一对应、状态口径一致；移交清单 3 条完整且处置状态明确。
- **总评**：文档整体成熟度高；唯一实质缺陷集中在 java 后端与 live 求值管线的接缝（R3-1）；其余为口径与完备性改进。

### 严重程度分布

| 级别 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | R3-1 |
| P2 | 6 | R3-2 ~ R3-7 |

**Verdict: FAIL**（须修复 R3-1 后进入 round 4）

## 回应段（主 agent 逐条处置记录，round 4 开启前置条件）

| Finding | 处置 | 落点 |
|---|---|---|
| R3-1 (P1) | **已修复**（主 agent 独立复核 live 事实：`EvalRuntime.java:7-10` 四字段并列、IEvalScope 无输出访问器、`ExecutableFunction.java:98/119/140` 边界清零——审查者证据全部属实）：§七重写——live 事实修正（输出缓冲/帧/ExitMode 由 `EvalRuntime` 携带，非 `IEvalScope`）；约定扩展为"首参 `$scope` 骨架 + 有输出语义单元（xpl/xlib）追加 `IEvalOutput $out` 固定第二隐参，纯表达式单元与 janino 签名完全一致"；显式拒绝"`EvalRuntime` 单参承载一切"（泄漏解释器运行时结构 + 破坏 EvalMethod 兼容）；异同表补"输出缓冲"维度；设计结论 #5 同步 | java 01 §七、§一设计结论 5 |
| R3-2 (P2) | **已修复**：§二粒度行收敛为"每编译单元一个类（类型集以 §六任务输入口径为准）"，类型枚举仅在 §六出现一次 | java 01 §二 |
| R3-3 (P2) | **裁定遗留**：I 系条目号引用为全库既有惯例（W1 成稿即如此），W3 增删拆并是条目号漂移的唯一来源——"W3/W4 执行清单加入回检设计文档 I 系引用步骤"列入移交 W3 清单（W3 plan 修订 roadmap 时一并处理） | 移交 W3 清单 |
| R3-4 (P2) | **已修复**：§九移交清单第 1 条删除"round-1 独立审查"归因，保留证据陈述 | truffle 02 §九 |
| R3-5 (P2) | **已修复**：§六行改"xlang ExitMode 映射决策见 02 §三"；§五表后补总注（要点为框架惯例，是否采用由 02/实现计划裁定） | 01-truffle-knowledge §六、§五 |
| R3-6 (P2) | **已修复**：§七翻译缓存补契约（无 resourcePath 动态源按源内容哈希键入缓存或由编译出口持有，避免高频重翻译；具体形态归 I3/I4） | truffle 02 §七 |
| R3-7 (P2) | **已修复**：§三控制流行补"传播边界不变式"（ExitMode 不跨函数/闭包边界传播、以生成方法边界为传播边界、闭包内非局部跳转映射为函数体返回） | java 01 §三 |

## 移交 W3 清单（round 3 汇总，并入累计清单）

- （新）W3/W4 执行清单加入"回检设计文档 I 系条目号引用"步骤（R3-3）
- （新）EvalMethod 约定扩展（`$out` 隐参）的包装器契约随 I1 对拍框架定稿（R3-1 修复后遗留的实现细节）
- （新）无 resourcePath 动态源翻译缓存键策略实现归 I3/I4（R3-6 契约已定稿）
- （承）round-2 清单全部条目（合并见 PASS 轮汇总）
