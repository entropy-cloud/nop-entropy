# I2 nop-xlang-java 模块骨架 + 表达式子集转译 + 共享语义 helper 基座 + 对拍 java 列激活

> Plan Status: active
> Last Reviewed: 2026-08-20
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I2；设计冻结于 `ai-dev/design/xlang-java/01-architecture-baseline.md`（§三语义一致性/§四 SourceLocation/§七 EvalMethod 约定）与 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五（对拍口径）
> Mission: xlang-execution-optimization
> Work Item: I2
> Related: I1（前置，对拍框架与 corpus v1）；W4-audit 移交 #5 / R1-2（包装器契约责任链记账，本 plan 承接）

<!-- Draft review: round-1（fresh session ses_fe5202579ffecMx9pSCMFNxDUQ，3 Major+3 Minor）→ 修复 → round-2（fresh session ses_fe51575ceffehTQ1EezWrZjLQ3，F1-F6 全 resolved，GO 0 Blocker/0 Major，共识达成）→ active。 -->

## Purpose

落盘 `nop-kernel/nop-xlang-java` 新模块（pom/包结构骨架），实现表达式子集的 Executable 树 → Java 源码转译与共享语义 helper 基座，激活对拍矩阵 java 列并全绿。定稿模板入口包装器契约（`$out` 隐参签名约定），其执行路径验证责任显式移交 I4。

## Current Baseline

- I1 产物存在（前置断言——执行本 plan 前须核验 I1 已 `completed`）：对拍 harness（test-jar 发布）+ corpus v1 + 测试级强制路由 API + 解释器基线列全绿。
- janino EvalMethod 先例 live：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/janino/JaninoScriptCompiler.java`——生成方法为 static、首参固定 `IEvalScope $scope`（`ExprConstants.SYS_VAR_SCOPE`），经 `MethodInvoker` 包装为 `IEvalFunction`；`EvalMethodInvoker` 在 `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/`。
- 语义操作现状 live：**部分**语义敏感操作已共享，但共享不完整——`PlusExecutable` 数值路径调 `MathHelper.add`、`EqExecutable` 调 `MathHelper.xlangEq`（`io.nop.commons.util.MathHelper`），而 `PlusExecutable` 的 String 拼接分支内联于节点类（`MathHelper.add` 对非数值输入返回 NaN，不承担拼接语义）；子集内其余语义敏感操作（宽松比较其余变体/数值提升残余/属性反射等）是否内联需盘点（Phase 2 盘点项）。共享 helper 基座 = 逐节点类×逐语义分支盘点后补齐，不是从零新建、也不能按"节点类已引用 helper"整类划为已共享。
- 输出缓冲 live 事实：`IEvalOutput` 由 `EvalRuntime` 携带（`getOut()`），不经 `IEvalScope`——设计 java §七"$out 追加隐参"约定的依据。
- `nop-javac` `JdkJavaCompiler`（`javax.tools` 内存编译 + 自定义 ClassLoader 通路）live 存在；设计裁定其不承担生产产物编译，本 plan 将其用于测试域对拍执行（见 Phase 4 决策）。
- `nop-kernel` 父 pom 现无 `nop-xlang-java` module 条目；`missions/xlang-execution-optimization.json` commands 仅引用 `:nop-xlang`。
- 无生成类加载/绑定机制（I10）、无构建任务（I11）——本 plan 的 java 列是测试域验证载体，生产通路不在范围。

## Goals

- `nop-xlang-java` 模块落盘（pom + 包结构 + 父 pom 注册），依赖方向 `nop-xlang-java` → `nop-xlang`（+ 可选 nop-javac 边），禁止依赖 `nop-xlang-truffle` / GraalVM。
- 共享语义 helper 基座：子集内语义敏感操作统一为 `nop-xlang` 内共享 helper，解释器节点同步改用，行为不变由既有测试保证。
- 表达式子集转译器：字面量 / slot 标识符 / 算术 / 逻辑 / 比较 / 简单方法调用（与 I1 corpus v1 子集一致）树 → Java 源码纯函数转译；子集外节点 fail-fast（报节点类名 + SourceLocation，不部分生成）。**"简单方法调用"边界钉死**：= 宿主方法反射分派族（`ObjFunctionExecutable`/`StaticFunctionExecutable` 系，经共享 helper 翻译），不含函数字面量、闭包捕获、`CallFuncExecutable` 族局部函数调用（设计 java §三"函数/闭包"类别，属 I4 覆盖 B 范围）——与 I1 corpus v1 简单方法调用类样例边界对账一致。
- SourceLocation 保真：生成类内嵌静态 SourceLocation 常量，可抛错点携带。
- EvalMethod 调用约定：生成入口方法 static + 首参 `IEvalScope $scope`；纯表达式单元经 `EvalMethodInvoker` 包装为 `IEvalFunction`。
- 模板入口包装器契约定稿：xpl/xlib 有输出语义单元追加固定第二隐参 `IEvalOutput $out`（位于声明参数之前）——签名约定定稿 + 决策记录；执行路径验证移交 I4（R1-2 记账）。
- 对拍 java 列激活：corpus v1 表达式单元 java 列 vs 解释器列对拍全绿，含身份断言（执行体 = 生成类实例）。
- mission.json commands 在模块落盘的同一次变更中切换为含 `:nop-xlang-java` 口径（"模块落盘即切换"裁定，W3-supplement）。

## Non-Goals

- 覆盖 A/B 类别节点转译与覆盖矩阵（I3/I4）。
- `$out` 输出通路的 corpus 执行验证（I4 传递责任；本 plan 只定契约不做执行验证）。
- 生成类加载与 RCM 绑定集成（I10）、构建任务/双清单/native 兼容（I11）。
- 统一后端选择机制与生产路由（I9）；本 plan 不动生产代码路径。
- 动态源执行（truffle 后端，I5+）。

## Scope

### In Scope

- 新模块落盘与父 pom/mission.json 联动切换。
- 共享语义 helper 基座（nop-xlang 内）与解释器改用。
- 表达式子集转译器 + SourceLocation 常量 + EvalMethod 约定 + 包装器契约定稿（决策记录）。
- 对拍 java 列接入（测试域执行通路，见 Phase 4）。

### Out Of Scope

- 同 Non-Goals。

## Execution Plan

### Phase 1 - 模块落盘与 commands 切换

Status: planned
Targets: `nop-kernel/nop-xlang-java/`（新模块）、`nop-kernel/pom.xml`、`missions/xlang-execution-optimization.json`

- Item Types: `Decision`

- [ ] 创建 `nop-kernel/nop-xlang-java` 模块骨架：pom（依赖仅 `nop-xlang` 及其传递依赖；`nop-javac` 以 test scope 引入——测试域用途：诊断性编译校验与 Phase 4 对拍执行通路）+ 包结构（转译器/生成约定入口）
- [ ] 父 pom `nop-kernel/pom.xml` modules 注册新模块
- [ ] mission.json commands 同一次变更切换：test → `./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C`；build/lint/typecheck 同步替换为同模块集口径
- [ ] 空模块冒烟：新模块随构建编译通过（此 phase 无生产代码，仅骨架；禁止空壳公共方法）

Exit Criteria:

- [ ] `./mvnw compile -pl :nop-xlang-java -am` 通过（模块进 reactor）
- [ ] 依赖方向断言：模块 pom 无 `nop-xlang-truffle`、无 `org.graalvm.*`、`nop-javac` 不进 compile scope（repo-observable pom 检查）
- [ ] mission.json commands 已切换且 live 可运行（四条命令逐条执行通过；"模块落盘即切换"裁定落实）
- [ ] No owner-doc update required（docs-for-ai 新模块开发指南同步归 I11）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 共享语义 helper 基座

Status: planned
Targets: `nop-kernel/nop-xlang/`（helper 定义）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（解释器节点改用）

- Item Types: `Proof | Decision`

- [ ] 盘点表达式子集内语义敏感操作现状：**逐节点类 × 逐语义分支**标注三类——"已共享（如 `MathHelper.xlangEq`）/ 内联待提取（如 `PlusExecutable` 的 String 拼接分支——数值路径已共享但拼接分支内联）/ 直译无语义风险"（盘点清单落当日 log；不以"节点类已引用 helper"整类划为已共享）
- [ ] 内联待提取项提取为 `nop-xlang` 内共享 helper（已有 nop-commons 共享实现的直接引用，不重复造；如 Plus 完整语义=拼接分支+数值路径统一为共享 helper）
- [ ] 解释器对应节点类改调共享 helper（行为不变）
- [ ] `nop-xlang` 既有测试全量回归

Exit Criteria:

- [ ] 盘点清单 repo-observable（log 记录，逐节点类×逐语义分支三类标注）
- [ ] 改用共享 helper 后 `./mvnw test -pl :nop-xlang -am` 全绿（roadmap I2 验收：解释器改用后既有测试全绿）
- [ ] helper 定义位于 nop-xlang（依赖方向合法：后端模块与解释器同一实现来源）
- [ ] 无行为变更证据：受影响节点类的既有 focused tests 全绿（不新增语义，仅提取）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 表达式子集转译器与调用约定

Status: planned
Targets: `nop-kernel/nop-xlang-java/`（转译器与生成约定）

- Item Types: `Proof | Decision`

- [ ] 实现树 → Java 源码纯函数转译器：子集（字面量/slot 标识符/算术/逻辑/比较/简单方法调用，边界见 Goals"简单方法调用"钉死项）；每编译单元一个生成类，类名从 resourcePath 确定性派生，文件头 `// source: <resourcePath>` 注释
- [ ] fail-fast：树中出现子集外节点 → 转译失败并报节点类名 + SourceLocation；禁止部分生成与"剩余解释"混合产物
- [ ] SourceLocation 静态常量内嵌：可抛错点抛 `NopException` 时携带对应常量
- [ ] EvalMethod 调用约定落地：生成入口方法 static + 首参 `IEvalScope $scope`；纯表达式单元经 `EvalMethodInvoker` 包装为 `IEvalFunction`（janino 同构）
- [ ] 语义敏感操作生成代码统一调用 Phase 2 共享 helper（禁止为生成代码重写语义等价实现）
- [ ] 模板入口包装器契约定稿（决策记录）：xpl/xlib 有输出语义单元追加固定第二隐参 `IEvalOutput $out`（声明参数之前）；纯表达式单元不追加。契约文本 + "执行路径验证归 I4（corpus 全类别含模板单元样例覆盖 `$out` 通路）"责任链记入本 plan 与当日 log（W4-audit R1-2 移交承接）

Exit Criteria:

- [ ] 子集内每类节点至少 1 个转译单测（生成源码文本断言或编译后行为断言）；fail-fast 有测试：子集外节点转译报错且错误信息含节点类名与 SourceLocation；边界测试含一例"局部函数调用（CallFunc 族）被子集排除"的 fail-fast
- [ ] 生成源码含静态 SourceLocation 常量（源码文本可断言）——roadmap I2 验收第三项文本形态
- [ ] **行为级验证**：至少 1 个行为级测试证明异常携带内嵌常量——转译含抛错点的树 → 编译（测试域编译通路，按 Phase 4 决策口径）→ 执行 → 断言 `NopException` 错误码 + SourceLocation 等于内嵌常量（roadmap I2 验收"异常语义断言可执行"的行为级兜底，防止常量存在但从未被断言携带的 vacuous 满足）
- [ ] 纯表达式单元经 `EvalMethodInvoker` 包装可被调用（与 janino 先例同构验证）
- [ ] 包装器契约决策记录 repo-observable（plan 勾选 + log；契约细节为决策文本，不需要代码验证——I4 承接执行验证）
- [ ] No owner-doc update required（契约属 ai-dev 设计/计划层，设计文档冻结不改，映射表纪律）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 对拍 java 列激活

Status: planned
Targets: `nop-kernel/nop-xlang-java/src/test/`（列接入与对拍用例）

- Item Types: `Decision | Proof`

- [ ] 测试域执行通路决策与落地：java 列在测试内将转译器产出生成源码经 `JdkJavaCompiler`（nop-javac 内存编译通路）编译加载后执行——与设计裁定一致（nop-javac 不承担生产产物编译；此为设计文本未枚举的**测试域扩展裁定**，生产边界不变：生产通路 = I11 常规构建编译 + I10 classpath 绑定，本 plan 不实现不宣称；裁定与设计文本缺口说明记入当日 log，供下次设计文档修订补记）。仓内测试域先例：nop-core `TestAopCodeGenerator` 经 `getDefaultClassPaths()` + 内存编译在 surefire 下运行
- [ ] 以 I1 harness 注册 java 列（test-jar 依赖）：静态单元适用；身份断言 = 执行体为生成类实例（非解释器树）
- [ ] corpus v1 表达式单元 java 列 vs 解释器列对拍全量执行（三层断言 + 身份断言 + truffle 列缺席显式记录）

Exit Criteria:

- [ ] corpus v1 表达式**静态**单元 java 列 vs 解释器列对拍全绿（动态单元 java 列不适用，按 I1 列适用性机制显式记录；含身份断言）——roadmap I2 验收第一项
- [ ] 差异注入不可达性说明不需要（I1 已自检 harness；本 phase 只接入真列）
- [ ] **接线验证**：java 列执行体身份断言通过即证明生成类真实执行（非解释器兜底、非摆设列）
- [ ] **端到端验证**：树 → 转译器 → 生成源码 → 测试域编译加载 → 执行 → 三层对拍断言全链可运行
- [ ] 测试域执行通路决策记录 repo-observable（log）
- [ ] `./mvnw test -pl :nop-xlang-java -am` 全绿
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 表达式 corpus（静态单元，动态单元 java 列不适用按列适用性记录）java 列 vs 解释器列对拍全绿（含 java 列身份断言 = 生成类实例）——roadmap I2 验收第一项
- [ ] 解释器改用共享 helper 后 nop-xlang 既有测试全绿——roadmap I2 验收第二项
- [ ] 生成源码含 SourceLocation 静态常量——roadmap I2 验收第三项
- [ ] mission.json commands 已按"模块落盘即切换"切换且逐条可运行——roadmap I2 验收第四项
- [ ] 包装器契约定稿 + I4 责任链显式记账（R1-2 承接）——已记入 Deferred But Adjudicated 并在 I4 交接说明
- [ ] 回归不允许削弱现解释器测试（纪律 3）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：java 列真实经生成类执行（身份断言）；无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl :nop-xlang,:nop-xlang-java -am`
- [ ] `./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 模板入口包装器契约的执行路径验证

- Classification: `out-of-scope improvement`（责任移交，非缺陷延期）
- Why Not Blocking Closure: 本 plan 交付契约定稿（签名约定 + 决策记录）；执行路径（`$out` API 调用序列、输出族节点）依赖覆盖 B 输出节点生成族，corpus 全类别含模板单元样例的验证属 I4 验收范围（W4-audit R1-2 裁定：I4 验收语义已覆盖，仅责任链需显式化——本条即显式化）。
- Successor Required: yes
- Successor Path: `ai-dev/plans/xlang-execution-optimization/`（I4 plan 起草时引用本条；对账注记：设计 java §三"函数/闭包"类别中的宿主方法分派族（ObjFunction/StaticFunction 系）已由本 plan 子集承接，I4 覆盖该类别其余成员）

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<模板单元 $out 通路验证归 I4；其余 no remaining plan-owned work>>
