# I2 nop-xlang-java 模块骨架 + 表达式子集转译 + 共享语义 helper 基座 + 对拍 java 列激活

> Plan Status: completed
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

Status: completed
Targets: `nop-kernel/nop-xlang-java/`（新模块）、`nop-kernel/pom.xml`、`missions/xlang-execution-optimization.json`

> Execution note（2026-08-20）：模块骨架落盘——pom（compile 依赖仅 `nop-xlang`；`nop-javac` test scope（测试域编译通路）；`nop-xlang` test-jar test scope（I1 harness/corpus 消费）；junit-jupiter test）+ 包结构 `io.nop.xlang.java.translator` / `io.nop.xlang.java.gen`（package-info 承载包职责说明，无空壳公共方法）。父 pom modules 注册；mission.json 四条 commands 同一次变更为 `:nop-xlang,:nop-xlang-java` 口径。

- Item Types: `Decision`

- [x] 创建 `nop-kernel/nop-xlang-java` 模块骨架：pom（依赖仅 `nop-xlang` 及其传递依赖；`nop-javac` 以 test scope 引入——测试域用途：诊断性编译校验与 Phase 4 对拍执行通路）+ 包结构（转译器/生成约定入口）
- [x] 父 pom `nop-kernel/pom.xml` modules 注册新模块
- [x] mission.json commands 同一次变更切换：test → `./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C`；build/lint/typecheck 同步替换为同模块集口径
- [x] 空模块冒烟：新模块随构建编译通过（此 phase 无生产代码，仅骨架；禁止空壳公共方法）

Exit Criteria:

- [x] `./mvnw compile -pl :nop-xlang-java -am` 通过（模块进 reactor）
- [x] 依赖方向断言：模块 pom 无 `nop-xlang-truffle`、无 `org.graalvm.*`、`nop-javac` 不进 compile scope（repo-observable pom 检查）
- [x] mission.json commands 已切换且 live 可运行（四条命令逐条执行通过；"模块落盘即切换"裁定落实）——typecheck/lint（`-Pqa checkstyle:check` 两模块 EXIT=0；mission 原命令带 fallback 正常回显）/build（BUILD SUCCESS，tests.jar 产出）/test（BUILD SUCCESS，nop-xlang 495/0/2）逐条执行
- [x] No owner-doc update required（docs-for-ai 新模块开发指南同步归 I11）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 共享语义 helper 基座

Status: completed
Targets: `nop-kernel/nop-xlang/`（helper 定义）、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（解释器节点改用）

> Execution note（2026-08-20）：**盘点清单（逐节点类 × 逐语义分支，三类标注）**——
> ①直译无语义风险：Literal/Null（常量返回）、SlotIdentifier/SlotAssign（帧 slot 读写，slot 布局编译期已定）、Block/Seq 族（顺序执行控制流结构，Seq 尾值/Block 弃值）、ReturnNull（执行后弃值返 null）、CallFunc 程序入口（结构性载体→生成方法入口，非语义 helper 项）；
> ②已共享（既有共享实现直接引用，不重复造）：Plus 数值路径（MathHelper.add）、Minus/Multiply/Divide（MathHelper）、Eq/Ne/StrictEq/StrictNe（MathHelper.xlangEq，Strict 变体 live 与宽松同实现）、Gt/Ge/Lt/Le（MathHelper）、And/Or（ConvertHelper.toTruthy + 短路）、Not（!toTruthy）、CompareOp（FilterOp.getBiPredicate 枚举统一谓词源）、ObjFunction 族反射分派（ObjFunctionHandle 独立共享类）；
> ③内联待提取（本 phase 已提取至 `io.nop.xlang.exec.XLangSemantics`）：Plus String 拼接分支（与数值路径统一为 `plus`）、ObjFunction 族接收者 null 检查 + 调用/异常包装（`invokeObjMethod`/`invokeObjFunction0-3`/`wrapInvokeException`，ERR_EXEC_INVOKE_METHOD_FAIL loc/forWrap/params/bizFatal 语义不变）、FunctionExecutable 族调用 + addXplStack（`invokeEvalFunction0-3`/`invoke`，stackObj 传 this 保持 addXplStack 精确语义）、StaticFunctionExecutable 解析+调用+ERR_EXEC_OBJ_UNKNOWN_METHOD（`invokeStaticMethod`）、GuardNotNull（`guardNotNull`，ERR_EXEC_VALUE_NOT_ALLOW_NULL）。
> 改调共享 helper 的解释器节点：PlusExecutable、AbstractObjFunctionExecutable（doInvoke0-3/doInvoke 收口）、FunctionExecutable（全部 5 个 execute 体）、StaticFunctionExecutable、GuardNotNullExecutable；②类节点已调用共享实现，无需改动。`./mvnw test -pl :nop-xlang -am` 495/0/2 全绿（与 I1 基线一致，行为不变证据）。

- Item Types: `Proof | Decision`

- [x] 盘点表达式子集内语义敏感操作现状：**逐节点类 × 逐语义分支**标注三类——"已共享（如 `MathHelper.xlangEq`）/ 内联待提取（如 `PlusExecutable` 的 String 拼接分支——数值路径已共享但拼接分支内联）/ 直译无语义风险"（盘点清单落当日 log；不以"节点类已引用 helper"整类划为已共享）
- [x] 内联待提取项提取为 `nop-xlang` 内共享 helper（已有 nop-commons 共享实现的直接引用，不重复造；如 Plus 完整语义=拼接分支+数值路径统一为共享 helper）
- [x] 解释器对应节点类改调共享 helper（行为不变）
- [x] `nop-xlang` 既有测试全量回归

Exit Criteria:

- [x] 盘点清单 repo-observable（log 记录，逐节点类×逐语义分支三类标注）
- [x] 改用共享 helper 后 `./mvnw test -pl :nop-xlang -am` 全绿（roadmap I2 验收：解释器改用后既有测试全绿）——495/0/2
- [x] helper 定义位于 nop-xlang（依赖方向合法：后端模块与解释器同一实现来源）——`io.nop.xlang.exec.XLangSemantics`
- [x] 无行为变更证据：受影响节点类的既有 focused tests 全绿（不新增语义，仅提取）——全量回归与 I1 基线逐位一致
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 表达式子集转译器与调用约定

Status: completed
Targets: `nop-kernel/nop-xlang-java/`（转译器与生成约定）

> Execution note（2026-08-20）：转译器 `io.nop.xlang.java.translator.ExecToJavaTranslator`（纯函数：树→源码；程序入口模式=根 CallFunc 按 I1 裁定翻译为生成方法本体；纯表达式模式=单表达式返回；调用形态引用多次使用/进条件分支前提升临时变量保证单次求值；And/Or 短路与 ObjFunction 接收者 null 短路以 if 块保真——右侧/实参仅在需要时求值）。生成约定 `io.nop.xlang.java.gen.EvalMethodConvention`（static + 首参 `$scope`；类名 `Gen_`+resourcePath 确定性派生；`$out` 契约常量）+ `GeneratedEvalBinding`（EvalMethod 入口定位 + `EvalMethodInvoker(MethodInvoker)` 包装）。解释器节点类补只读访问器（AbstractBinaryExecutable.getLeft/getRight、CallFunc/SlotIdentifier/SlotAssign/Not/GuardNotNull/ReturnNull/AbstractObjFunction/Function/StaticFunction/CompareOp getters，纯增量无行为变更）；新增错误码 `ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE`。FunctionExecutable 系生成代码经 `XLangSemantics.invokeGlobalFunction` 运行时从同一 `EvalGlobalRegistry` 解析（与编译期 LexicalScopeAnalysis 全局函数解析同源），StaticFunctionExecutable 系经 `invokeStaticMethodResolved`（与编译期 `classModel.getStaticMethodsByName` 同一解析方式）。
>
> **模板入口包装器契约决策记录（定稿；W4-audit R1-2 承接）**：xpl/xlib 有输出语义的编译单元，生成入口方法在声明参数之前追加固定第二隐参 `IEvalOutput $out`（`EvalMethodConvention.OUT_PARAM`）；纯表达式单元不追加（与 janino 先例签名完全一致，走既有 `EvalMethodInvoker` 包装）。依据（设计 java §七）：live 事实输出缓冲由 `EvalRuntime` 携带（`rt.getOut()`）、不经 `IEvalScope`，输出通路必须以隐参承载。**执行路径验证归 I4**：corpus 全类别含模板单元样例覆盖 `$out` API 调用序列的执行验证属 I4 验收范围（本 plan 仅定约，未生成任何输出通路代码——输出族节点转译属 I4）。

- Item Types: `Proof | Decision`

- [x] 实现树 → Java 源码纯函数转译器：子集（字面量/slot 标识符/算术/逻辑/比较/简单方法调用，边界见 Goals"简单方法调用"钉死项）；每编译单元一个生成类，类名从 resourcePath 确定性派生，文件头 `// source: <resourcePath>` 注释
- [x] fail-fast：树中出现子集外节点 → 转译失败并报节点类名 + SourceLocation；禁止部分生成与"剩余解释"混合产物
- [x] SourceLocation 静态常量内嵌：可抛错点抛 `NopException` 时携带对应常量
- [x] EvalMethod 调用约定落地：生成入口方法 static + 首参 `IEvalScope $scope`；纯表达式单元经 `EvalMethodInvoker` 包装为 `IEvalFunction`（janino 同构）
- [x] 语义敏感操作生成代码统一调用 Phase 2 共享 helper（禁止为生成代码重写语义等价实现）
- [x] 模板入口包装器契约定稿（决策记录）：xpl/xlib 有输出语义单元追加固定第二隐参 `IEvalOutput $out`（声明参数之前）；纯表达式单元不追加。契约文本 + "执行路径验证归 I4（corpus 全类别含模板单元样例覆盖 `$out` 通路）"责任链记入本 plan 与当日 log（W4-audit R1-2 移交承接）

Exit Criteria:

- [x] 子集内每类节点至少 1 个转译单测（生成源码文本断言或编译后行为断言）；fail-fast 有测试：子集外节点转译报错且错误信息含节点类名与 SourceLocation；边界测试含一例"局部函数调用（CallFunc 族）被子集排除"的 fail-fast——`TestExecToJavaTranslatorSource` 26 例（corpus 12 单元全覆盖 + 合成树 Minus/Divide/Ne/Ge/Le/StrictEq/StrictNe/Not/CompareOp/Null/Seq/Block/ObjFunction-null 短路/StaticFunction/And 形态 + fail-fast 5 例：NullCoalesce 子集外/CallFunc 非根=局部函数调用/入口带参/不支持的字面量类型/裸 slot）
- [x] 生成源码含静态 SourceLocation 常量（源码文本可断言）——roadmap I2 验收第三项文本形态（`private static final SourceLocation LOC_0 = SourceLocation.fromLine(...)` 文本断言）
- [x] **行为级验证**：至少 1 个行为级测试证明异常携带内嵌常量——转译含抛错点的树 → 编译（测试域编译通路，按 Phase 4 决策口径）→ 执行 → 断言 `NopException` 错误码 + SourceLocation 等于内嵌常量（roadmap I2 验收"异常语义断言可执行"的行为级兜底，防止常量存在但从未被断言携带的 vacuous 满足）——`TestGeneratedCodeBehavior.testExceptionCarriesEmbeddedSourceLocationConstant`（errorCode + path/line/col 全等断言）
- [x] 纯表达式单元经 `EvalMethodInvoker` 包装可被调用（与 janino 先例同构验证）——`testEvalMethodInvokerWrapping`（返回值 + 类型断言）；另含 `assign` 全局函数副作用落 scope（`testGlobalFunctionSideEffectOnScope`）、字符串拼接行为、类名派生
- [x] 包装器契约决策记录 repo-observable（plan 勾选 + log；契约细节为决策文本，不需要代码验证——I4 承接执行验证）
- [x] No owner-doc update required（契约属 ai-dev 设计/计划层，设计文档冻结不改，映射表纪律）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 对拍 java 列激活

Status: completed
Targets: `nop-kernel/nop-xlang-java/src/test/`（列接入与对拍用例）

> Execution note（2026-08-20）：**测试域执行通路裁定落地**——java 列在测试内将转译器产出生成源码经 `JdkJavaCompiler`（nop-javac 内存编译 + surefire classpath，先例 nop-core `TestAopCodeGenerator`）编译加载后执行；与设计裁定一致（nop-javac 不承担生产产物编译）。此为设计文本未枚举的**测试域扩展裁定**：生产边界不变（生产通路 = I11 常规构建编译 + I10 classpath 绑定，本 plan 不实现不宣称）；设计文本缺口（设计 java §二"G -. 诊断性编译校验(可选)"未枚举测试域对拍执行用途）记入当日 log，供下次设计文档修订补记。落地：`JavaBackendColumn`（backendId=java，静态单元适用/动态不适用；以请求中同一棵树实例为翻译源；executedArtifact=生成类实例、executorArtifact=生成类入口 Method）+ `JavaBackendIdentityRule`（强身份规则：执行体必须是本单元确定性派生生成类实例 + 入口 Method 符合 EvalMethod 约定——替换 I1 占位规则）+ `TestCorpusV1JavaColumn`（22 单元参数化）。执行中发现并修复 `XLangSemantics` 单一 static handle 的类级缓存跨 funcName 污染缺陷（charAt 缓存被 toUpperCase 命中——解释器为每节点一 handle，共享表必须按 funcName 分立）；focused 回归 `TestXLangSemantics` 落 nop-xlang（含 Plus 完整语义自内联提取的验证）。

- Item Types: `Decision | Proof`

- [x] 测试域执行通路决策与落地：java 列在测试内将转译器产出生成源码经 `JdkJavaCompiler`（nop-javac 内存编译通路）编译加载后执行——与设计裁定一致（nop-javac 不承担生产产物编译；此为设计文本未枚举的**测试域扩展裁定**，生产边界不变：生产通路 = I11 常规构建编译 + I10 classpath 绑定，本 plan 不实现不宣称；裁定与设计文本缺口说明记入当日 log，供下次设计文档修订补记）。仓内测试域先例：nop-core `TestAopCodeGenerator` 经 `getDefaultClassPaths()` + 内存编译在 surefire 下运行
- [x] 以 I1 harness 注册 java 列（test-jar 依赖）：静态单元适用；身份断言 = 执行体为生成类实例（非解释器树）
- [x] corpus v1 表达式单元 java 列 vs 解释器列对拍全量执行（三层断言 + 身份断言 + truffle 列缺席显式记录）

Exit Criteria:

- [x] corpus v1 表达式**静态**单元 java 列 vs 解释器列对拍全绿（动态单元 java 列不适用，按 I1 列适用性机制显式记录；含身份断言）——roadmap I2 验收第一项——`TestCorpusV1JavaColumn` 22/22（11 静态双列全绿 + 11 动态单列 java 不适用无记录）
- [x] 差异注入不可达性说明不需要（I1 已自检 harness；本 phase 只接入真列）
- [x] **接线验证**：java 列执行体身份断言通过即证明生成类真实执行（非解释器兜底、非摆设列）——强身份规则（确定性派生类名 + EvalMethod 约定入口）由 harness 依证据判定 + 测试显式复核 assertNotSame(解释器树)
- [x] **端到端验证**：树 → 转译器 → 生成源码 → 测试域编译加载 → 执行 → 三层对拍断言全链可运行——`TestCorpusV1JavaColumn` 参数化用例即完整链路
- [x] 测试域执行通路决策记录 repo-observable（log）
- [x] `./mvnw test -pl :nop-xlang-java -am` 全绿——53/53（26 源码断言 + 5 行为级 + 22 对拍）；nop-xlang 498/498（含 3 例新增 focused 回归）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 表达式 corpus（静态单元，动态单元 java 列不适用按列适用性记录）java 列 vs 解释器列对拍全绿（含 java 列身份断言 = 生成类实例）——roadmap I2 验收第一项——`TestCorpusV1JavaColumn` 22/22
- [x] 解释器改用共享 helper 后 nop-xlang 既有测试全绿——roadmap I2 验收第二项——498/0/2（与 I1 基线 495/0/2 一致 + 3 例新增 focused 回归）
- [x] 生成源码含 SourceLocation 静态常量——roadmap I2 验收第三项——源码文本断言 + 行为级异常携带断言双证
- [x] mission.json commands 已按"模块落盘即切换"切换且逐条可运行——roadmap I2 验收第四项——四条命令逐条执行通过（Phase 1）
- [x] 包装器契约定稿 + I4 责任链显式记账（R1-2 承接）——已记入 Deferred But Adjudicated 并在 I4 交接说明
- [x] 回归不允许削弱现解释器测试（纪律 3）——零删改，仅增量（helper 收口为代码等价提取，全量回归逐位一致）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（执行中发现的共享 handle 缓存污染缺陷当场修复 + focused 回归；Deferred 区仅责任移交项）
- [x] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [x] 独立子 agent closure-audit 已完成并记录证据（task `ses_fe4c3e210ffexDxW6Ji3fpPwTa`，verdict **CAN CLOSE 0 Blocker/0 deferred-defect**；advisory：①未提交→收口时提交；②无 `-am` 单模块测试因本地仓旧 SNAPSHOT 报错＝环境现象（mission build 命令刷新本地仓）；③display() 每次执行急切构造＝行为等价的优化候选——见 Closure Evidence）
- [x] Anti-Hollow Check：java 列真实经生成类执行（身份断言）；无空方法体/静默跳过/no-op——强身份规则（确定性派生类名 + EvalMethod 入口）+ `scan-hollow-implementations.mjs` 两模块 exit 0
- [x] `./mvnw compile -pl :nop-xlang,:nop-xlang-java -am`——EXIT=0
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C`——BUILD SUCCESS（nop-xlang 498/0/2 + nop-xlang-java 53/0/0）
- [x] checkstyle / 代码规范检查通过——`-Pqa checkstyle:check -pl :nop-xlang,:nop-xlang-java` EXIT=0；`check-doc-links.mjs --strict` 0 errors

## Deferred But Adjudicated

### 模板入口包装器契约的执行路径验证

- Classification: `out-of-scope improvement`（责任移交，非缺陷延期）
- Why Not Blocking Closure: 本 plan 交付契约定稿（签名约定 + 决策记录）；执行路径（`$out` API 调用序列、输出族节点）依赖覆盖 B 输出节点生成族，corpus 全类别含模板单元样例的验证属 I4 验收范围（W4-audit R1-2 裁定：I4 验收语义已覆盖，仅责任链需显式化——本条即显式化）。
- Successor Required: yes
- Successor Path: `ai-dev/plans/xlang-execution-optimization/`（I4 plan 起草时引用本条；对账注记：设计 java §三"函数/闭包"类别中的宿主方法分派族（ObjFunction/StaticFunction 系）已由本 plan 子集承接，I4 覆盖该类别其余成员）

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: 四个 Phase 全部完成并逐项勾选：nop-xlang-java 模块落盘 + mission.json commands 切换（四条 live 可运行）；共享语义 helper 基座 `XLangSemantics` 落地 nop-xlang、五节点类收口改调、全量回归与 I1 基线一致；表达式子集转译器（fail-fast + SourceLocation 常量 + EvalMethod 约定 + 语义统一走共享 helper）+ 包装器契约定稿（$out，I4 承接执行验证）；对拍 java 列激活，corpus v1 静态单元 java vs 解释器全绿（三层断言 + 强身份断言 + 列缺席记录）。独立 closure audit verdict CAN CLOSE（0 Blocker）。
Completed: 2026-08-20

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh session 子 agent（task `ses_fe4c3e210ffexDxW6Ji3fpPwTa`）
- Evidence:
  - 四个 Phase 逐项 PASS（对照 live 文件核验）：Phase 1 模块/父 pom/mission.json live 在位且四命令切换；Phase 2 `XLangSemantics` 共享基座（Plus 拼接分支逐字提取、异常包装错误参数全等）、五节点委托 diff＝等价提取+纯增量 getter、498/0/2＝基线 495+3 focused；Phase 3 fail-fast 含 CallFunc 非根边界、LOC 常量行为级断言（errorCode + path/line/col）、EvalMethod 包装 tested、$out 契约四处记录（plan/log/Deferred/OUT_PARAM 常量）；Phase 4 mission test 命令 live 复跑 BUILD SUCCESS（498/0/2 + 53/0/0 = 26 源码 + 5 行为 + 22 对拍），与 plan 宣称逐位一致。
  - Anti-Hollow：接线端到端追踪（corpus → 同树单次编译 → JavaBackendColumn 以树实例为翻译源 → JdkJavaCompiler 内存编译 → 生成类实例/入口 Method 证据 → 强身份规则判定 → 三层断言 + cross-compare）——java 列真实经生成类执行；无空方法体/静默吞异常；`scan-hollow-implementations.mjs --module nop-xlang-java --severity high` exit 0。
  - Closure Gates 工具门：`./mvnw compile -pl :nop-xlang,:nop-xlang-java -am` EXIT=0；`./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C` BUILD SUCCESS；`-Pqa checkstyle:check` 两模块 EXIT=0；`check-doc-links.mjs --strict` 0 errors；`check-plan-checklist.mjs --strict` exit 0（仅 closure-audit gate 留待本审计，现已勾选）。
  - Deferred 项检查：Deferred 区仅包装器契约责任移交 I4（out-of-scope improvement，W4-audit R1-2 裁定显式化）；执行中发现的共享 handle 缓存污染缺陷当场修复 + focused 回归，未降级。
  - Advisory 处置：①代码未提交→本收口提交（`feat(xlang): ...`）；②单模块无 `-am` 测试因本地仓旧 SNAPSHOT＝环境现象，mission build 命令刷新；③`display()` 急切构造＝行为等价优化候选（不阻塞，I3+ 可顺带收敛）。
- Audit Session: ses_fe4c3e210ffexDxW6Ji3fpPwTa

Follow-up:

- 模板单元 `$out` 通路验证归 I4（Deferred But Adjudicated 已显式记账）；`display()` 急切构造优化候选（行为等价，non-blocking）；其余 no remaining plan-owned work
