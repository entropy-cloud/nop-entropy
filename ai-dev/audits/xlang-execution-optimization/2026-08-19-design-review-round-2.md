# W2-review Round 2 设计审查报告（xlang-execution / xlang-java / xlang-truffle）

> Mission: xlang-execution-optimization
> Plan: `ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-2-w2-design-review-gate.md`（Phase 2）
> Round: 2
> Date: 2026-08-19
> Reviewer: 独立子 agent（fresh session，opencode task `ses_fe5bac052ffeL3b99FpkdW8oaN`，模型 zhipuai-coding-plan/glm-5.2；未参与撰写、未读 round-1 报告内容，仅确认其存在——独立性成立）
> Verdict: **FAIL**（0 P0 / 1 P1 / 8 P2 → 修复后进入 round 3）

## 审查输入

与 round 1 同一输入包（三组设计文档修复后版本 + 知识层 + 两篇基准文档 + live 锚点），三级判级口径与文档式证据格式在 prompt 中声明。全量复审（非 delta）。

## Findings（审查者原文）

### [R2-1] 后端选择机制对"运行时产生、但经 RCM 模型加载管线编译的资源键单元"管辖未定义；java §五绑定树缺静态路径前置门，与统一决策树动态路径冲突 — **P1**

- **文档**: `xlang-execution/01-architecture-baseline.md` §三；`xlang-java/01-architecture-baseline.md` §五
- **证据**: 模型加载期行未限定构建期静态资源；java §五伪代码对任意 unit 无条件 `生成类清单.lookup`，miss → WARN + 解释器；统一决策树唯一门 `c 是构建期静态资源` 无可操作测试。live RCM 支持非 `_vfs` 静态来源组件加载（`ResourceComponentManager.java:212/290/384`）。
- **现状**: 同一类单元同时满足两个判定时机的触发描述，两条伪代码输出冲突（统一树动态路径 → TRUFFLE；java §五 miss → INTERPRETER + WARN）。
- **风险**: I2/I5 实现者按 java §五字面实现则非构建期资源被永久绑定解释器并制造降级观测噪音，违背"决策输入与降级路径全仓唯一"；若以"生成类清单命中"判定静态性，codegen 漏跑不可观测。
- **建议**: §三补静态性判定 operationalization（独立扫描清单 should-set，与生成类清单分离）；java §五加 `unit ∈ 扫描清单` 前置门，miss 区分"本应存在"与"不适用"。
- **信心水平**: 很可能

### [R2-2] 扫描口径含幻影资源类型 `*.expr`，live 平台无此文件类型 — **P2**

- **证据**: live `XlangConstants.java:58-72` 无 "expr"；全仓 `find -name "*.expr"` 零命中。三份文档 + roadmap 重复引用。
- **建议**: 以 live 常量与 `_vfs` 实际资源清点修正口径；java §六列明确定类型清单（含排除项及理由）。
- **信心水平**: 确定

### [R2-3] Q5 确认点状态双口径：§二"已提前满足" vs §九裁定表与移交清单"待复核" — **P2**

- **证据**: 02 §二与 §九 Q5 行/移交清单第 1 条互斥；§二嵌入审查过程叙事与 guide"无过程叙事"有张力；01 §十同步旧口径。
- **建议**: 统一为"已确认 + I3 冒烟复核"；§二改持久事实陈述去轮次引用。
- **信心水平**: 确定

### [R2-4] 知识层对 live 解释器机制描述不准确（"MathHelper…走 Map 查找"） — **P2**

- **证据**: live `MathHelper.java:442-462`（精确类比较逐级判断）+ `:775-794`（数值提升 switch），非 Map 查找。
- **建议**: 改为"数值提升 switch + 精确类型判断（MathHelper）与方法表查找（反射分派）"。
- **信心水平**: 确定

### [R2-5] 知识层残留决策性语气的规范性表述 — **P2**

- **证据**: §五"禁止 execute 中 new 节点""必须 boundary"；§3.2 ExitMode 映射无决策指针。
- **建议**: 改为框架事实语气；ExitMode 处补"决策见 02 §三"。
- **信心水平**: 很可能

### [R2-6] java 组 slot→局部变量翻译未提示与 Java lambda 有效终值约束的冲突 — **P2**

- **证据**: slot 可变（`SlotAssignExecutable`/自增自减族）且可被闭包捕获（`BuildClosureBodyExecutable`）；Java lambda 要求 captured local 有效终值。
- **建议**: §三补契约级声明（可变 slot 闭包捕获经显式可变 cell/数组包装）。
- **信心水平**: 很可能

### [R2-7] truffle 组缺"语义一致性策略"陈述，与 java 组（共享 helper）不对称 — **P2**

- **证据**: truffle 02 未声明特化节点与共享 helper 的边界。
- **建议**: 增加对称段落：特化仅作已证实语义等价的 fast-path，generic/fallback 一律调用共享 helper。
- **信心水平**: 很可能

### [R2-8] "EXCLUSIVE 单 Context 过渡 → 切 SHARED"的切换语义歧义 — **P2**

- **证据**: `contextPolicy` 是 `@Registration` 编译期常量，不能运行时切换；两种读法（改注解重编译 vs SHARED 下单 Context 使用）载体不同。
- **建议**: 写明过渡机制并固定验证载体口径。
- **信心水平**: 很可能

### [R2-9] xlang-java 目录无本地 Vision 文件（README 显式委托统一愿景）——guide 字面的裁量偏离 — **P2**

- **证据**: guide"每个子系统必须包含以下两层"；README 显式委托 ../xlang-execution/00-vision.md。
- **建议**: 在 guide 或本 README 注明"委托式 Vision"为合法形态及约束。
- **信心水平**: 有趣的猜测

### 无 finding 维度结论（审查者原文摘要）

- **① 内容点覆盖 15/15**：execution 4 / java 6 / truffle 5 均有实质章节，无标题占位。
- **③ 跨文档一致性**（除 R2-1/R2-3 涉及项）：选择机制、依赖方向（五处一致）、Truffle 不泄漏（live 抽查 nop-core/nop-xlang pom 无 graalvm 坐标）、对拍三层断言（四处一致）、137 基线数、stock JVM 性能口径、native 不做 truffle、互斥判据——均无冲突；相对链接全部有效。
- **④ live 可行性**（除 R2-2/R2-4/R2-6）：ScriptCompilerRegistry 先例、Janino EvalMethod 约定逐项、JdkJavaCompiler 机制定位（内存编译+自定义 ClassLoader，与修复后的 java §二描述一致）、RCM/ComponentCacheEntry、分类表 60+ 类名全部命中、truffle 映射表全部与 live 一致。
- **⑤ 知识层清账**（除 R2-3/R2-5）：§十与 02 §九逐条对应；移交清单 3 项完整存在；§一/§4.4/§七/§八指针化。
- **总评**（审查者）：文档工程质量高、三组主张高度收敛；核心弱点单一且集中——选择机制的操作化（R2-1），修复是补定义而非改架构；其余为收口同步类瑕疵。

### 严重程度分布

| 级别 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | R2-1 |
| P2 | 8 | R2-2 ~ R2-9 |

**Verdict: FAIL**（须修复 R2-1 后进入 round 3）

## 回应段（主 agent 逐条处置记录，round 3 开启前置条件）

| Finding | 处置 | 落点 |
|---|---|---|
| R2-1 (P1) | **已修复**：①统一架构 §三新增"构建期扫描清单（静态性判定的操作化）"段——扫描清单（should-set）与生成类清单分离产出，静态路径判定测试即清单成员资格；清单外资源（含 RCM 动态注册/加载）走动态路径不记降级；codegen 整体漏跑由注册阶段保证可观测（启用但清单缺失 → 注册不可用 + 全局 WARN）。②决策树 `c 是构建期静态资源` → `c ∈ 构建期扫描清单`，动态路径注释补"含 RCM 加载的清单外资源"。③判定时机表模型加载期行限定"扫描清单内资源才做生成类绑定"。④java §五伪代码加 `unit ∉ 扫描清单 → 动态路径裁决 return` 前置门 + 新增"静态性判定 = 扫描清单成员资格"bullet（含反推不可行理由） | execution 01 §三（判定输入 1、判定时机表+新增段、决策树）；java 01 §五 |
| R2-2 (P2) | **已修复**：全部 5 处口径删除 `*.expr`；任务输入锚定 live 事实（`XlangConstants` FILE_TYPE_XPL/XLIB，主 agent 独立复核：全仓 `*.expr` 零命中、`XlangConstants.java:58-72` 无 expr 常量、xpl/xlib live 文件 50/263）；其余类型（xtask/xgen/xrun 等）由 I6 按 live 清点定稿并记录纳入/排除理由；java §二图同步 | execution 00 §三表、execution 01 §三、java 01 §二图/§六 |
| R2-3 (P2) | **已修复**：§九 Q5 行与移交清单第 1 条改"确认点已关闭（I3 常规冒烟复核）"；移交清单第 2/3 条标注"维持移交——归 I7"；§二去轮次引用改为持久事实陈述（"构件级证据：Maven Central 25.2.4 线实测…"）；01 §十 Q5 行同步 | truffle 02 §二、§九；01 §十 |
| R2-4 (P2) | **已修复**：§2.2 改为"数值提升 switch + 精确类型逐级判断（MathHelper），方法调用走反射/方法表查找" | 01-truffle-knowledge §2.2 |
| R2-5 (P2) | **已修复**：§五两处指令语气改框架事实语气（"DSL 规范要求…（SL 惯例）"）；§3.2 ExitMode 补决策指针 | 01-truffle-knowledge §五、§3.2 |
| R2-6 (P2) | **已修复**：§三语义一致性策略新增"可变 slot 的闭包捕获契约"段（可变 cell 包装，语义与解释器 slot 写读一致；未捕获 slot 保持局部变量直译） | java 01 §三 |
| R2-7 (P2) | **已修复**：02 §七新增与 java 对称的"语义一致性策略"bullet（特化仅作已证实语义等价 fast-path，generic/fallback 一律共享 helper，定义在 nop-xlang） | truffle 02 §七 |
| R2-8 (P2) | **已修复**：02 §五保守过渡路径补"切换机制"（注解取值变更后重新编译的形态切换，非运行时开关；两形态各自为验证载体） | truffle 02 §五 |
| R2-9 (P2) | **裁定遗留**：guide 非 W1 产出（修复范围仅限 W1 文档）；与 round-1 移交 W3 清单第 4 项合并（guide 增补"外部知识参考层"与"多目录共享/委托式 Vision 层"条款） | 移交 W3 清单 |

## 移交 W3 清单（round 2 汇总，与 round-1 清单合并去重后共 10 项，见 round-3/PASS 轮汇总）

1.（合）design-writing-guide 增补"外部知识参考层"与"委托式 Vision 层"条款（R1-10/R1-11/R2-9）
2.（新）静态性判定工件化（扫描清单）与 codegen 漏跑可观测的实现细节（随 R2-1 已定稿设计要求，实现归 I1/I6）
3.（新）闭包捕获可变 slot 的可变 cell 策略细化（R2-6；契约已定稿，实现归 I2）
4.（新）truffle 特化节点与共享 helper 语义边界的实现细化（R2-7；策略已定稿，归 I3/I4）
5.（新）对拍 harness 的后端强制路由与身份断言 API 契约（实现归 I1）
6.（新）编译单元资源类型全集清点（xtask/xgen/xrun 等取舍）替代 roadmap I6 既有 `.expr` 口径（R2-2；roadmap 非 W1 产出，W3 回填 I6 时修正）
7.（合）降级观测的日志/指标命名契约（归 I5 或 docs-for-ai）
8.（承）Q5 条件钉版确认已关闭（round-1 实测），I3 常规冒烟复核即可
9.（承）静态资源 JVM 形态下生成物缺失时"不借道 truffle"的机会成本——I7 基准顺带量化
10.（承）roadmap 纪律 5 依赖白名单是否补记 `nop-xlang-java -.-> nop-javac` 构建期可选诊断边
