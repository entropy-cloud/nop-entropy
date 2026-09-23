---
status: draft
mission: nop-lint
work-item: "item-30"
group: "2026-09-24-0600"
verify: [test]
---

# Dataflow analyzer（roadmap item 30）：DefUseChain + 常量传播

## Current Baseline

以下事实均已对照 live repo（2026-09-24）核实：

- 依赖满足：item 30 deps = M4（`done`，2026-09-24 派生翻牌——items 19–29 全 done）。Java L2 面（JavaTypeResolver/ASTMapping/JavaNodeIndex，item 26）已就位，dataflow 构建于同一 JavaParser AST 之上。
- **设计契约**（design 06 §4.4）：`DataFlowAnalyzer.buildDefUseChain(method)` 遍历 AST 记录每个变量的定义点与使用点；`propagateConstants(method)` 沿赋值传播常量值（isConstantExpression 判定 + 常量求值）。消费规则族：UnusedLocalVariable / UnusedAssignment / SelfAssignment / DeadException（DefUseChain）；ConstantOverflow / CompileTimeConstant（常量传播）。design 06 §4.4 表 10 行 → v1 面 / 后续面映射（F7）：UnusedLocalVariable ✓、UnusedAssignment（局部变量形态）✓、SelfAssignment ✓、DeadException ✓（v1 面）；ConstantOverflow / CompileTimeConstant ✓（常量传播 v1 面）；LoopConditionChecker / MutableStaticState / UnusedPrivateField / FinalFieldCouldBeStatic → 字段面/循环面路由（后续面，Non-Goals）。
- **AST 基础**：item 26 的 `JavaNodeIndex`（named 节点 byte-range 索引 + IdentityHashMap）与 `LineColBytes`（UTF-16→byte 渐进换算）直接复用；dataflow 求解以 JavaParser AST 为主树（tree-sitter 侧仅经 ASTMapping 反查）。
- **现有面**：无任何 dataflow 代码（nop-lint-java semantic 包现有 4 类均为类型解析面）。
- **实现范围 v1 裁定基础**：方法内（intra-procedural）分析——字段级/跨方法/跨类追踪（UnusedPrivateField 等）需要项目面，归 Non-Goals（与 design 06 §4.4 的字段级条目拆分：v1 只做方法内变量面）。循环/分支的控制流敏感性 v1 裁定为流不敏感（flow-insensitive）——收集全部定义点/使用点后做集合差，不做路径敏感求解（v1 诚实口径，路径敏感归后续）。
- 硬约束适用：零平台改动；Wave-2+ 能力带测试（Minimum Rules #25）；显式失败不伪造（分析不出确定结论时返回 UNKNOWN 而非猜）。

## Goals

- **`DefUseChain`**（nop-lint-java semantic 包，方法内、流不敏感 v1）：
  - 输入：JavaParser `MethodDeclaration`（或 ConstructorDeclaration/InitializerDeclaration）
  - 遍历记录：每个变量的**定义点**（VariableDeclarator 节点本身即定义点——含无初始化形态；方法/构造器/捕获参数（Parameter）；赋值左侧（AssignExpr target 子树内的 NameExpr，F6：含 EnclosedExpr 包裹形态）；**增减量操作数（UnaryExpr POSTFIX/PREFIX_INCREMENT/DECREMENT 内的 NameExpr）**——round-1 F1 spike：x++ 非 AssignExpr，漏枚举将产出伪造常量值）与**使用点**（标识符引用出现在表达式右侧、方法实参、返回表达式、条件表达式——非赋值目标）
  - 产出：`DefUseChain.Record(variableName, definitionRanges, useRanges, isUsed, isSelfAssignment)`，按变量名聚合
  - **SelfAssignment 判定**：v1 收敛为**局部/参数变量 `x = x` 恒等形态**（round-1 F3 裁定：`x = this.x` 中 LHS 为局部而 RHS 解析为字段（同名不同符号），在 v1「局部+参数」范围内不可达且遮蔽时假阳性——移除该形态，`this.x` 场景归字段面路由）。**作用域归约机制裁定**：位置感知词法栈——声明所在最近 BlockStmt 在引用祖先链上且声明位置在引用之前（round-1 S4 实测 solver resolve() 双路亦可，但 solver 路在无 resolver 场景不可用，词法栈零依赖）；**类体屏障**：匿名类/局部类体内的 VariableDeclarator 不入方法面声明收集（round-1 S5b 实测匿名类同名字段会污染方法面），lambda 体参与外层作用域但 lambda 参数为屏障
- **`ConstantPropagation`**（方法内、流不敏感）：
  - 收集「声明时初始化为编译期常量且此后未被重赋值」的变量 → 常量绑定（String/int/long/boolean/char/null 字面量 + 编译期字符串拼接 `"a" + "b"`）
  - 查询接口：`constantValueOf(variableName)` → sealed 三态 `ConstantResult`（`Constant(String value)` 载体按字面量类型记录：String/int/long/boolean/char/null 六形态；`NotConstant` = 被重赋值或非常量表达式；`Unknown` = 变量名未在方法面声明）——round-1 F2：Optional 两态不可表达三分语义；「`x = x + 0` 仅在常量传播面判」子句删除（`x = x + 0` 的常量折叠归后续面，v1 只做 `x = x` 恒等形态）
- **`DataFlowAnalyzer` 门面**：`buildDefUseChain(method)` 与 `propagateConstants(method)` 两个入口（design 06 §4.4 契约名）
- 单元测试矩阵（Minimum Rules #25）：
  - DefUseChain：已使用变量（isUsed=true）/未使用变量/自赋值（x=x）/参数作为定义点/方法实参作为使用点/字段访问不算局部变量/嵌套块（if/for 内）使用点收集/同名遮蔽（内层块重声明）——遮蔽 v1 裁定：按声明作用域区分，同名内层声明独立建链（不做跨作用域混淆）
  - ConstantPropagation：String/int/long/boolean/char/null 六形态 + 字符串拼接/被重赋值（含 x++ 增减量）后非常量/无初始化声明非绑定
- owner docs 回写：design 06 §4.4（v1 落地形态增注：方法内/流不敏感/字段面路由）+ roadmap item 30 状态回写。

## Non-Goals

- 字段级分析（UnusedPrivateField/UnusedAssignment 的字段形态/SingularField/ImmutableField——需类级跨方法聚合，v1 只做方法内局部变量与参数）
- 路径敏感/控制流图（CFG）求解——流不敏感集合差已满足 v1 消费规则（SelfAssignment 恒等形态判定不需要 CFG）
- 跨过程/跨文件分析
- 基于 dataflow 的完整规则批量落地（UnusedLocalVariable 等规则本体归 item 35/36 规则库面，本 plan 只交付 analyzer 能力 + e2e 证明）
- JavaParser 之外的 tree-sitter 原生 dataflow

## Phase 1 — DefUseChain + ConstantPropagation 内核（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-java`（semantic 包：DefUseChain/ConstantPropagation/DataFlowAnalyzer）

- Item Types: `Fix | Proof`

- [x] `DefUseChain.Record` + 收集器：VariableDeclarator/参数/赋值左侧 → 定义点；NameExpr（非赋值目标）→ 使用点；SelfAssignment 恒等形态判定
- [x] `ConstantPropagation`：常量字面量绑定 + 编译期字符串拼接 + 被重赋值失效（**重赋值判定复用定义点收集，含增减量 UnaryExpr 形态**——防止 F1 在常量传播路径复活）+ 查询三分（Constant/NotConstant/Unknown）
- [x] `DataFlowAnalyzer` 门面（design 06 §4.4 契约名两方法）
- [x] 单元测试矩阵（Minimum Rules #25）：使用/未使用/自赋值/参数定义/实参使用/字段访问排除/嵌套块收集/同名遮蔽独立建链 + 常量六形态（含 long/char）/重赋值失效/非绑定

Exit Criteria:

- [x] 矩阵全格有断言（Minimum Rules #25）
- [x] 同名遮蔽不混淆（内层重声明独立建链，测试断言）
- [x] `./mvnw -pl nop-lint/nop-lint-java test` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Phase 2 — 消费 e2e + 收口（Proof）

Status: completed
Targets: `nop-lint/nop-lint-java`（测试）、design docs、roadmap

- Item Types: `Proof`

- [x] e2e：UnusedLocalVariable 形态规则 + SelfAssignment 形态规则经 analyzer 判定产出诊断——**不挂 requires: [L2]**（dataflow 是纯 AST 分析，round-1 F8：仅 solver 归约路才需 L2 接线，词法栈零依赖），测试内直接接线 DataFlowAnalyzer——两条 e2e 各有断言
- [x] owner-doc：design 06 §4.4 v1 落地增注（方法内/流不敏感/字段面路由三裁定）与 live 一致
- [x] 一致性核对：analyzer 契约名与 design 06 §4.4 一致；消费规则族映射登记
- [x] 收口项：roadmap item 30 状态回写（draft review 置 planned，closure audit 置 done）；核对 items 31–36 与 M5 未受扰动
- [x] Exit Criteria 汇总：`./mvnw -pl nop-lint/nop-lint-java test` 退出码 0；owner-doc 与 live 一致；`ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] **e2e 双规则验证**（Minimum Rules #22）：analyzer→规则判定→诊断从入口到输出完整走通
- [x] `./mvnw -pl nop-lint/nop-lint-java test` 退出码 0
- [x] roadmap item 30 状态回写正确，items 31–36 与 M5 未受扰动
- [x] owner-doc 与 live 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。

- [x] in-scope 行为结果已达成：DefUseChain（定义/使用/自赋值/遮蔽）、ConstantPropagation（六形态/失效/三分查询）、DataFlowAnalyzer 门面
- [x] fail-closed 无降级：无法判定返回 UNKNOWN/空集，从不伪造（Minimum Rules #24）
- [x] 端到端验证（Minimum Rules #22）：analyzer 经规则判定产出诊断的完整链路
- [x] v1 边界诚实：流不敏感/方法内/字段面路由三裁定记录在 design 06 §4.4 增注
- [x] 零平台改动取证
- [x] 无 in-scope confirmed live defect / contract drift 被静默降级
- [x] owner docs 与 live 一致；roadmap item 30 状态回写正确
- [x] 独立子 agent closure-audit 已完成并记录证据到 `## Closure`
- [x] **Anti-Hollow Check**：closure audit 已验证 analyzer 被规则判定真实消费（e2e 断言），无空方法体/静默跳过
- [x] `./mvnw -pl nop-lint/nop-lint-java test` 退出码 0
- [x] checkstyle / 代码规范检查按 mission `commands.lint` 既有裁定记录

## Verification

- `./mvnw -pl nop-lint/nop-lint-java test` 退出码 0（预期新增 TestDefUseChain + TestConstantPropagation 共 ≥12 断言格 + e2e 2 例）
- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

## Draft Review Record

- 2026-09-24：iteration 2，增量复核（agent_f42aba19-a18e-4a76-a4e1-db26096433a4）——F1-F9 修复 9/9 落文本（F7 §4.4 全表映射经 live 10 行表核对通过）；新发现 N1 六/五形态矛盾（补 long/char 断言格，三处改"六形态"）、N2 增减量在重赋值侧残留（补"重赋值判定复用定义点收集"+ x++ 测试格）、N3 watch-only；APPROVED 进入执行。
- 2026-09-24：iteration 1，独立子代理 spike 驱动审查（agent_682c7477-af1f-49a5-b3e7-e571bd26837c，41 项断言实测 javaparser 3.26.3）——F1 Blocker（增减量 UnaryExpr 非 AssignExpr，漏枚举产出伪造常量值）+ F2 Major（constantValueOf Optional 两态 vs 三分语义矛盾）+ F3 Major（作用域机制未裁定/类体屏障缺失/x=this.x 不可达）+ F4-F9 Minor（无初始化定义点/catch 参数/EnclosedExpr 包裹/§4.4 全表映射/e2e L2 标注/Verification 空壳），全部按指定采纳修订。Spike 证据：S1-S9 全 PASS（基础收集/自赋值 AST 形态/字符串拼接/遮蔽双路归约/stream 跨 lambda 匿名类遍历/InitializerDeclaration/字段解析反射路/声明形态枚举/增减量陷阱）。
