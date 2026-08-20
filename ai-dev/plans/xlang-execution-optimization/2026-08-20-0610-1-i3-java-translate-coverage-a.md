# I3 java 转译覆盖 A（数据与作用域族）+ 覆盖矩阵机制落地

> Plan Status: completed
> Last Reviewed: 2026-08-20
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I3；类别划分 = 设计 `ai-dev/design/xlang-java/01-architecture-baseline.md` §三分类表；对拍口径 = 设计 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五
> Mission: xlang-execution-optimization
> Work Item: I3
> Related: I2（前置：转译器/共享 helper 基座/java 列）；I1（前置：对拍 harness 与 corpus v1）；后继 I4（覆盖 B + 矩阵闭环，消费本 plan 的矩阵机制与归属裁定）

<!-- Draft review: round-1（fresh session ses_fe3ea32c5ffepBxl4SOq0Psp6J，1 Major+8 Minor：四分区归属缺口具名化/138 计数/Phase 2 自含判据/扫描粒度与共享落点/调试对语义/整族 corpus 降级裁定/roadmap 同步）→ 修复 → round-2（fresh session ses_fe3e130a8ffesp77XKOFYopYI1，Ready 0 Blocker，2 Minor——ResolvedObjFunctionExecutable 具名并入默认/MakeScopeEvalFunction 入排除示例——已当场修复；round-1 的 -Pqa profile 质疑经 live 复核 root pom.xml:472 证伪）→ 共识达成 → active。 -->

## Purpose

把 java 转译器从 I2 表达式子集推进到覆盖 A（数据与作用域族）全族可转译：五类节点族（作用域链访问 / 类型操作 / 对象集合构造访问 / 绑定守卫调试 / slot 写族）逐具体节点类注册转译，对应类别 corpus 扩充后 java 列 vs 解释器列对拍全绿，并落地覆盖矩阵机制（live `exec/` 基线逐节点类断言注册，新增节点类红灯）推进到"A 族 + I2 子集全绿、B 族显式 pending"。

## Current Baseline

- I2 产物存在（前置断言——执行本 plan 前须核验 I2 已 `completed`）：`nop-kernel/nop-xlang-java` 模块（转译器 `io.nop.xlang.java.translator.ExecToJavaTranslator` + 生成约定 `EvalMethodConvention`/`GeneratedEvalBinding`）；子集 = 字面量/slot 读/let 载体 slot 写/算术/逻辑/比较/宿主方法反射分派（ObjFunction/Function/StaticFunction 系）/GuardNotNull/Seq/Block/ReturnNull/程序入口 CallFunc；子集外节点 fail-fast（`ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE`，报节点类名 + SourceLocation）。
- I1 产物存在：对拍 harness（`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compare/`，test-jar 发布：列 SPI + 三层断言引擎 + 证据驱动身份断言 + 列缺席记录 + 强制路由 API）+ corpus v1（`CorpusV1` 22 单元，6 类 × 静态/动态 + 组合，解释器基线全绿）+ java 列（`TestCorpusV1JavaColumn` 22/22）。
- 共享语义 helper 基座 live：`io.nop.xlang.exec.XLangSemantics`（plus/比较族/truthy/invokeObjMethod 系/invokeGlobalFunction/invokeStaticMethodResolved/guardNotNull 等）；I2 Phase 2 已建立三类标注法（直译无语义风险 / 已共享复用 / 内联提取）。
- 节点类基线 live：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/` 现为 **138 文件**（设计/roadmap 的 137 为 W1 时点历史基线，I2 将共享 helper `XLangSemantics.java` 落入同包——矩阵基线以 live 扫描为准，`XLangSemantics` 为包内非节点具体类，须入排除清单）。AST→Executable 产生路径锚点：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/BuildExecutableProcessor.java`。
- 起草时 live 初盘——覆盖 A 五族的具体节点类（**Phase 1 复核定稿**，以 live 目录为准）：
  - 作用域链访问族（含引用族）：`ScopeIdentifierExecutable`、`GlobalVarExecutable`、`ScopeAssignExecutable`、`ScopeSelfAssignExecutable`、`ScopeSelfIncExecutable`、`ScopeSelfDecExecutable`、`ReferenceIdentifierExecutable`、`ReferenceAssignExecutable`、`ReferenceSelfAssignExecutable`、`ReferenceSelfIncExecutable`、`ReferenceSelfDecExecutable`、`RenewReferenceExecutable`
  - 类型操作族：`CastExecutable`、`ConvertExecutable`、`ConvertWithDefaultExecutable`、`InstanceOfExecutable`、`TypeOfExecutable`
  - 对象/集合构造与访问族：`NewObjectExecutable`、`NewListExecutable`、`NewMapExecutable`、`GetPropertyExecutable`、`GetterGetPropertyExecutable`、`StaticGetterGetPropertyExecutable`、`SetPropertyExecutable`、`SetterSetPropertyExecutable`、`GetAttrExecutable`、`SetAttrExecutable`、`ListItemExecutable`、`MapItemExecutable`、`MakePropertyExecutable`
  - 绑定/守卫/调试族：`BindVarExecutable`、`ArrayBindingAssignExecutable`、`ObjectBindingAssignExecutable`、`InitRefSlotExecutable`、`EnhanceRefSlotExecutable`、`GuardNotEmptyExecutable`（GuardNotNull 已在 I2 子集）、`DebugExecutable`、`DebugIdentifierExecutable`、`VarStatusExecutable`（归属疑点：可能伴随循环语句产生，Phase 1 裁定归 A 或 B）
  - slot 写族：`SlotAssignExecutable`（基础形态已随 I2 let 载体落地）、`SelfAssignExecutable`、`SelfAssignAttrExecutable`、`SelfAssignPropertyExecutable`、`SelfIncExecutable`、`SelfDecExecutable`
- **范围缺口（本 plan Phase 1 裁定）**：live `exec/` 的具体节点类未全部被"A 五族 / I2 子集已落 / B 族枚举 / 排除清单"四分区覆盖，起草初盘的无主集合至少含：
  - 设计 §三"字面量/常量"+"算术/逻辑/比较"两行的残余算子：`CloneLiteralExecutable`、`NegExecutable`、`BitNotExecutable`、`NullCoalesceExecutable`、`BetweenOpExecutable`、`AssertOpExecutable`、`ConcatExecutable`、`RangeExecutable`、`PropInExecutable`、`EqNullExecutable`、`NeNullExecutable`、`StrictEqNullExecutable`、`StrictNeNullExecutable`（注：`CompareOpExecutable` 不在缺口内——I2 转译器已落地该类）；
  - 初盘遗漏的具体类：`BinaryExecutable`（`valueOf` 兜底分支对特化 switch 外的算子自我实例化）、`ReturnScopeValuesExecutable`（宏 script 路径产生）、`LocationFunction`（draft 复查未见前端产生路径，疑不可经标准前端产生）、`ResolvedObjFunctionExecutable`（`ObjFunctionExecutable` 的具体兄弟类，宿主反射分派已解析变体——I2 转译器仅落 `ObjFunctionExecutable` 本体，该类今日 fail-fast；默认随其已落兄弟并入本 plan）；
  - 函数邻接具体类（设计 §三 B 行枚举未点名）：`VarFunctionExecutable`、`VarExecutableFunction`、`LazyCompiledExecutableFunction`、`FunctionalAdapterExecutable`、`CallFuncWithClosureExecutable`、`ExecutableFunctionEvalAction`、`BuildFuncRefExecutable`。
  不逐类裁定归属则 I4 矩阵闭环时存在无主节点。
- `missions/xlang-execution-optimization.json` commands 已是三模块口径（`:nop-xlang,:nop-xlang-java,:nop-xlang-truffle`）；本 plan 不新增模块、不改 commands。
- I2 closure 移交的非阻塞优化候选：`XLangSemantics.invokeGlobalFunction` 每次执行急切构造 `display + "@" + loc` 字符串（行为等价、仅错误路径可观测）——I2 closure audit 裁定 non-blocking，"I3+ 可顺带收敛"。
- 真正剩余的 gap：A 五族节点转译全部缺失（遇即 fail-fast，truffle 侧 `TestTranslatorFailFast` 以 `GetPropertyExecutable` 为反例即证）；无覆盖矩阵机制；corpus 无 A 类别单元。

## Goals

- 覆盖 A 五族具体节点类全部可转译：子集 fail-fast 边界收缩到"A 族 + 已并入残余之外"；语义敏感操作（作用域链访问语义/类型转换语义/属性反射/数值提升残余）生成代码统一调用共享 helper（定义在 nop-xlang，三类标注法延续 I2 Phase 2 口径），禁止为生成代码重写语义等价实现。
- 残余数据面算子族归属裁定并（默认）并入本 plan 落地：设计 §三两行残余节点全部可转译——使 I4 的矩阵闭环只余 B 族，无无主节点。
- corpus 覆盖 A 类别扩充：五族每族静态形态 ≥1 单元（硬要求）；动态形态按动态编译出口自然产生能力配比（能产生则 ≥1，不能产生显式记录类别与原因）；含异常语义单元 ≥1；解释器基线列全绿。
- java 列对拍全绿：覆盖 A corpus 单元 java 列 vs 解释器列对拍全绿（三层断言 + 身份断言 = 生成类实例；动态单元 java 列不适用按列适用性机制记录）。
- 覆盖矩阵机制落地并推进：以 live `exec/` 包扫描为基线源（非纯手工清单）；抽象基类/接口/非节点辅助类排除清单显式记录（逐类理由）；I2 子集 + A 族 + 并入残余全绿；B 族显式 pending（可观测、不算通过）；新增未注册节点类红灯（注入验证红/绿可控）。
- 矩阵机制与归属裁定形成 I6/I4 可消费的口径产物（truffle 侧矩阵"同基线同口径"消费、I4 矩阵闭环消费）。

## Non-Goals

- 覆盖 B 族（函数/闭包/控制流/输出节点生成族）转译与矩阵闭环（I4）。
- `$out` 包装器契约执行路径验证（I2 显式移交 I4 的责任链）。
- truffle 侧任何翻译/矩阵工作（I6/I7）。
- 生产代码路径的后端选择/路由/降级（I9）、生成类加载与 RCM 绑定（I10）、构建任务与双清单（I11）。
- 语义特化/性能优化（含共享 helper 性能优化，见 Non-Blocking Follow-ups）。

## Scope

### In Scope

- A 五族 + 并入残余的节点类转译落地（translator 注册 + fail-fast 收缩 + 转译级单测）。
- A 族语义敏感操作盘点与共享 helper 增量（提取/复用裁定 + 解释器同步改调 + 行为不变回归）。
- corpus 覆盖 A 类别单元扩充（静态/动态配比 + 异常单元）与解释器基线。
- java 列对拍扩展到覆盖 A corpus。
- 覆盖矩阵机制（基线扫描 + 排除清单 + 注册/pending 分区 + 红灯注入验证）与两项裁定（残余算子族归属、边缘节点归属）记录。

### Out Of Scope

- 同 Non-Goals。

## Execution Plan

### Phase 1 - 覆盖 A 盘点、归属裁定与矩阵口径定稿

Status: completed
Targets: 本 plan 与当日 log（裁定记录）；矩阵基线共享清单落点（候选：`nop-kernel/nop-xlang/src/test/` test-jar 共享位，I1 先例）+ 矩阵断言测试落点（候选：`nop-kernel/nop-xlang-java/src/test/`）——两落点 Phase 1 定稿

- Item Types: `Decision | Proof`

- [x] 复核定稿覆盖 A 五族具体节点类清单（以 live `exec/` 目录 + 设计 §三分类表为准，修正本 plan Current Baseline 初盘的遗漏/误归）；逐类标注"抽象基类/接口/非节点辅助类"排除清单及理由（如 `AbstractSelfAssignExecutable` 系基类、`AssignIdentifier`/`ScopeValues`/`PropBinding`/`ObjFunctionHandle`/`MakeScopeEvalFunction`/`ExecutableHelper` 等非具体节点，及包内非节点具体类 `XLangSemantics`）
- [x] 裁定并记录：**live `exec/` 全部具体类逐类落入四分区之一（A 五族 / I2 子集已落 / B 族 / 排除清单），无无主残留**。Current Baseline 初盘的无主集合至少含：残余算子 13 类（默认并入本 plan，理由：设计 §三两行由 I2 子集起步，残余为数据面尾部、直译风险低，I4 保持 B 族 + 闭环专注）+ `BinaryExecutable`（默认并入，兜底算子语义）+ `ReturnScopeValuesExecutable`、`LocationFunction` 及函数邻接 7 类（默认归 B/I4 或排除，逐类记录依据：按产生路径与语义族裁定）。裁定结果同步 roadmap I3/I4 条目范围行注记（一次小修订），避免 I4 起草时重新推导归属
- [x] 裁定并记录：边缘节点归属（至少 `VarStatusExecutable`、`DebugIdentifierExecutable`；按其产生路径与语义族归 A 或 B，记录依据）
- [x] 产生路径盘点：经 `BuildExecutableProcessor`（及前端语法/编译选项）确认五族逐类的树产生路径；**无法经标准前端产生的节点类显式记录**（其转译验证走 Phase 2 合成树转译级测试，不进 corpus）；若某族整族证实不可产生，该族 Phase 3 corpus 硬要求降级为"合成树转译级测试覆盖 ≥1 + 盘点证据记录"（显式裁定，非静默跳过）
- [x] 矩阵机制定稿（决策记录）：基线源 = live 扫描 `io.nop.xlang.exec` 包具体类（能检出包内新增未注册类；**扫描粒度裁定**：文件级 vs 类级、嵌套具体类如 `VarFunctionExecutable` 静态子类的处理口径）；注册证据 = 转译器支持声明显式化（支持集可编程枚举）+ 每族代表性节点真实转译验证 + fail-fast 反证（pending 集节点转译报 unsupported）；**共享落点裁定**：基线清单/排除清单/归属分区作为共享口径产物落 nop-xlang 测试源码（test-jar 发布，供 I6 truffle 侧矩阵同基线消费），矩阵断言测试落 java 侧——或等效单一事实源方案，记录决策与理由
- [x] corpus 扩充形态定稿（决策记录）：覆盖 A 单元以独立 corpus 装载类落 nop-xlang 测试源码（`CorpusV1` 的 22 单元与既有类别不动，新增类别/单元追加），保持 I1 产物稳定；解释器基线与 java 列各自新增参数化消费

Exit Criteria:

- [x] 五族清单 + 排除清单 + 四分区归属裁定（无无主具体类）+ 产生路径盘点 + 矩阵机制（含扫描粒度与共享落点）+ corpus 形态，全部 repo-observable（本 plan Execution note 或当日 log；I6/I4 可直接消费）
- [x] 归属裁定已同步 roadmap I3/I4 条目范围行注记（repo-observable）
- [x] 排除清单逐类有理由，无"整类划为辅助"的粗粒度划分（I2 三类标注法同源纪律）
- [x] No owner-doc update required（矩阵与裁定属 ai-dev 层产物；docs-for-ai 同步归 I11）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 覆盖 A 转译落地与共享 helper 增量

Status: completed
Targets: `nop-kernel/nop-xlang-java/src/main/java/io/nop/xlang/java/translator/`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（helper 增量与解释器改调）

- Item Types: `Proof`

- [x] A 五族（+并入残余）语义敏感操作盘点：逐节点类 × 逐语义分支三类标注（直译无语义风险 / 已共享复用 / 内联提取至 `XLangSemantics`），清单落当日 log；重点：作用域链访问语义（经 `$scope` API 调用，设计 §三）、类型操作（Cast/Convert 转换语义）、属性反射族（Get/SetProperty、Get/SetAttr 与解释器同一实现）、slot 写族（自增自减/复合赋值的求值顺序与类型提升）、调试对语义（`DebugExecutable`/`DebugIdentifierExecutable` 的调试钩子在生成代码中的承载方式——共享 helper 或等价直译，禁止静默吞掉调试语义）
- [x] 内联待提取项提取为共享 helper，解释器对应节点同步改调（行为不变）；已共享项直接引用不重复造
- [x] translator 注册 A 五族（+并入残余）逐具体节点类：语义敏感操作生成代码统一调共享 helper；SourceLocation 静态常量内嵌覆盖新可抛错点；子集外 fail-fast 边界相应收缩（错误信息仍报节点类名 + SourceLocation）
- [x] 转译级单测：每族 ≥1 真实转译断言（corpus 单元或合成树源码级断言；无法经前端产生的节点以合成树覆盖）；fail-fast 反证 ≥1 例（pending 集节点转译报 unsupported）

Exit Criteria:

- [x] 覆盖 A（+并入残余）全部具体节点类可转译——Phase 2 自身可判：转译器支持集可编程枚举且覆盖 Phase 1 定稿的 A 族 + 并入残余全部具体节点类（矩阵 live 扫描交叉验证归 Phase 3 联动）
- [x] 盘点清单 repo-observable（log）；解释器改调共享 helper 后 `./mvnw test -pl :nop-xlang -am` 全绿（回归基线与 I2 收口时一致或仅有新增测试）
- [x] 每族 ≥1 转译单测 + fail-fast 反证在仓（repo-observable 测试用例）
- [x] 无静默跳过：新注册节点转译中无法处理的形态显式 fail-fast，不默认通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - corpus 覆盖 A 扩充、对拍全绿与矩阵推进

Status: completed
Targets: `nop-kernel/nop-xlang/src/test/`（corpus 与基线）、`nop-kernel/nop-xlang-java/src/test/`（java 列与矩阵）

- Item Types: `Proof`

- [x] 构建覆盖 A corpus 单元：五族每族静态 ≥1、动态按自然产生能力配比（不能产生显式记录）、异常语义单元 ≥1（错误码 + 预期源位置）、单元 schema 四字段齐备（I1 口径）；Phase 1 盘点证实整族不可产生的族，按其裁定以合成树转译级测试覆盖替代并记录（不算静默跳过）
- [x] 解释器基线列全量执行覆盖 A corpus（单列阶段判定基准 = 列结果 vs 单元声明预期）
- [x] java 列对拍全量执行覆盖 A corpus（三层断言 + 身份断言 + truffle 列缺席显式记录；动态单元 java 列不适用按列适用性机制区分）
- [x] 覆盖矩阵测试落地：基线 = live 包扫描；I2 子集 + A 族 + 并入残余全绿；B 族 + 已裁定归 B 的边缘节点显式 pending（不算通过）；红灯注入验证（构造未注册具体节点类 → 矩阵 FAIL，红/绿对照）

Exit Criteria:

- [x] 覆盖 A corpus 单元 repo-observable（清单/类别/schema/动态缺席记录）；解释器基线全绿；**java 列 vs 解释器列对拍全绿（含身份断言）——roadmap I3 验收第一项**
- [x] **覆盖矩阵推进到位——roadmap I3 验收第二项**：类别内（含并入残余）基线节点类逐一注册断言全绿；pending 集显式可观测；新增节点类红灯经注入验证（红/绿可控）
- [x] **端到端验证**：corpus A 单元 → 树编译 → 转译器 → 生成源码 → 测试域编译加载 → 执行 → 三层对拍断言全链可运行（java 列参数化用例即载体）
- [x] **接线验证**：java 列身份断言（生成类实例）在覆盖 A 单元上持续成立（非解释器兜底）
- [x] 回归不削弱既有解释器测试（纪律 3）：`TestCorpusV1JavaColumn` 22/22 与 nop-xlang/nop-xlang-java 既有测试保持全绿
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C` 全绿
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Execution Notes

### Phase 1 决策记录（2026-08-20，全部 repo-observable）

**四分区归属裁定（代码化单一事实源）**：`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compare/ExecNodeBaseline.java`
（test-jar 发布，I1 先例同包）+ 新鲜度红灯测试 `TestExecNodeBaselineFreshness`（live 目录扫描 vs 声明分区，
新增未分类文件即 FAIL；红灯注入对照 = 未知类名判未归属/已登记类名判绿）。live 138 文件逐一归属，无无主残留：

| 分区 | 计数 | 内容 |
|---|---|---|
| A 族（I3 范围） | 44 | 五族清单见 `ExecNodeBaseline.A_*` 常量（与 plan Current Baseline 初盘一致，无修订项） |
| 并入残余（I3 范围） | 15 | 残余算子 13 类 + `BinaryExecutable`（兜底算子）+ `ResolvedObjFunctionExecutable`（宿主反射分派已解析变体，无产生路径，随已落兄弟并入） |
| I2 子集已落 | 28 | 含结构性载体（CallFunc/Seq/Block/SlotAssign/ReturnNull/GuardNotNull/Null） |
| B 族（I4，矩阵 pending） | 35 | 控制流 11 + 输出/节点生成 12 + 函数邻接 9 + 边缘裁定归 B 3（见下） |
| 排除（非节点/抽象/接口） | 16 | `ExecNodeBaseline.EXCLUDED_REASONS` 逐类理由（含 `XLangSemantics`、`ExecutableFunction`=函数对象仅作 Literal 载荷） |

**残余算子族归属裁定**：设计 §三"字面量/常量"+"算术/逻辑/比较"两行的 13 个残余类**并入本 plan**（理由：
数据面尾部、直译风险低；I4 保持 B 族 + 矩阵闭环专注；`CompareOpExecutable` 已在 I2 落地不属残余）。

**函数邻接 7 类 + 3 类边缘裁定**（逐类依据）：
- `VarFunctionExecutable`/`VarExecutableFunction`/`LazyCompiledExecutableFunction`/`FunctionalAdapterExecutable`/
  `CallFuncWithClosureExecutable`/`BuildFuncRefExecutable`：函数值调用/闭包构造/懒编译机制 → **归 B（I4）**。
- `ExecutableFunctionEvalAction`：懒编译适配器（implements IExecutableExpression 但非标准树编译产物，
  包装 ExecutableFunction 为 EvalAction）→ **归 B（I4）**，矩阵 pending 可观测。
- `ReturnScopeValuesExecutable`：宏 script 单元产物（`Program.isMacroScript` 路径，依赖 ScopeValues + 函数声明回读）→ **归 B（I4）**。
- `LocationFunction`：全仓无产生路径、宿主互通语义（返回 SourceLocation）→ **归 B（I4）**，矩阵 pending；I4 闭环时最终裁定转译或维持 pending。

**边缘节点归属裁定**（Phase 1 委托项）：
- `VarStatusExecutable`：**归 A**（绑定族——构造 LoopVarStatus 写入帧 slot 的纯数据面操作，不驱动迭代控制流）；
  live 无产生路径（全仓无构造点，属遗留设计）→ 合成树转译级测试覆盖，不进 corpus。
- `DebugIdentifierExecutable`：**归 A**（作用域链访问语义的调试包装——按名帧查找 + deRef 回落 scope 读取）；
  产生路径仅在 nop-dev-tools 调试器专用处理器（非标准前端）→ 合成树覆盖，不进 corpus。

**产生路径盘点**（经 `BuildExecutableProcessor` + 前端语法 live 诊断验证，Phase 1 scratch 会话）：
- **corpus 可产生**（标准前端表达式出口；Phase 3 corpus 覆盖）：作用域链 6 类（`$scope.x` 读写/复合赋值族；`x++`/`x--`
  需动态出口注册可变 scope var——`ScopeSelfInc/Dec` 仅动态形态）；`GlobalVarExecutable`（`$Math.abs(-3)`）；
  `Convert/ConvertWithDefault`（`'12'.$toInt()` 系）；`InstanceOf`（`x instanceof Date`）；`TypeOf`（`typeof x`）；
  `NewObject`（`new StringBuilder(16)`；注：`new Date(0)`/`new ArrayList(10)` 命中 nop-core 反射
  `getConstructorForArgs` 多候选误选 0 参构造的存量行为，corpus 规避，watch-only 移交 Non-Blocking Follow-ups）；
  `NewList/ListItem`（含 spread）、`NewMap/MapItem`（含 spread）；`Get/SetProperty`、`Get/SetAttr`、
  `StaticGetterGetProperty`（`Math.PI`）、`SelfAssignProperty/Attr`；`Array/ObjectBindingAssign`（对象解构
  rest 路径有存量缺陷，见 Phase 2 修复记录；corpus 首版不依赖该路径）；`DebugExecutable`（`x.$('p')`）；
  slot 写 3 类（`a += 2`/`a++`/`a--`）；`Neg/BitNot/NullCoalesce/EqNull 族/PropIn/BinaryExecutable`（`7 % 3`）；
  `ConcatExecutable`（动态单元经 `compileTemplateExpr` 出口，``a${1+2}c``）。
- **corpus 不可产生 → Phase 2 合成树转译级测试覆盖**（逐类记录）：`ReferenceIdentifier/ReferenceAssign/
  ReferenceSelfAssign/ReferenceSelfInc/ReferenceSelfDec/RenewReference`（闭包捕获可变变量标记 useRef 才产生，
  必然伴随 B 族函数节点）、`InitRefSlotExecutable`（全仓无产生路径）、`EnhanceRefSlotExecutable`（仅函数参数
  useRef 包装路径）、`BindVarExecutable`（仅闭包绑定机制运行期构造，编译期树中不出现）、`CastExecutable`
  （`processCastExpression` 返回 null，无产生路径）、`GetterGetProperty/SetterSetProperty/MakePropertyExecutable`
  （全仓无产生路径）、`VarStatusExecutable`、`DebugIdentifierExecutable`、`GuardNotEmptyExecutable`（仅
  `XplLibTagCompiler` mandatory 属性路径，非表达式出口）、`CloneLiteralExecutable`（仅函数默认参数初始化路径）、
  `BetweenOpExecutable/AssertOpExecutable`（表达式语法不产生，AST 来自 filter DSL 消费方）、
  `RangeExecutable`（仅 for-range 语句，伴随控制流 B 族）、`ResolvedObjFunctionExecutable`（全仓无产生路径）。
- **族级降级裁定**：无整族不可产生（五族均有可产生成员），corpus 硬要求不降级。

**矩阵机制定稿**：基线源 = live 文件级包扫描（`ExecNodeBaseline` 声明 + `TestExecNodeBaselineFreshness` 红灯）；
扫描粒度 = **文件级**（嵌套具体类如 `ObjFunctionExecutable$NoArgExecutable` 均为顶层类同族特化变体，
instanceof 分派按顶层类注册即覆盖，矩阵不单独枚举——裁定记录于 `ExecNodeBaseline` javadoc）；注册证据 =
转译器支持集可编程枚举（`ExecToJavaTranslator.getSupportedNodeClasses()`）+ 每类真实转译验证（矩阵测试对
I3 范围 59 类逐类构造最小实例真实转译，非清单自证）+ fail-fast 反证（B 族 35 类逐类构造最小实例断言
unsupported）；矩阵断言测试落 java 侧（`nop-xlang-java` 测试源码，消费 test-jar 共享基线）——共享落点裁定
= 基线/分区/排除清单单一事实源在 nop-xlang 测试源码（与 I6 truffle 侧同基线消费），断言引擎在后端模块侧。

**corpus 扩充形态定稿**：独立装载类 `CorpusCoverageA`（nop-xlang 测试源码，`io.nop.xlang.compare` 包），
`CorpusV1` 22 单元与既有类别不动；静态单元 = `xlang-compare/static-a/*.xpl` 新目录（c:script 编译单元）；
动态单元 = 表达式串（`compileFullExpr`/`compileSimpleExpr` 出口 + 两变体：注册可变 scope var 的编译 scope
（产生 ScopeSelfInc/Dec/ScopeAssign 平凡形态）、`compileTemplateExpr`（产生 ConcatExecutable））；
单元 schema 四字段齐备（I1 口径）；类别 → 必含节点规则（`getRequiredNodeRules` 同 CorpusV1 机制）+
A 范围节点白名单（`isAllowedNodeClass`）防越界。

### Phase 2 决策记录（2026-08-20）

**语义敏感操作三类标注**（逐节点类 × 语义分支，摘要；完整口径以 `XLangSemantics` 方法划分为准）：
- 直译无语义风险：`EqNull/NeNull/StrictEqNull/StrictNeNull`（null 判等括号化）、`NullCoalesce`（无条件提升
  临时变量 + if-null 短路）、`Neg/BitNot`（MathHelper 直调）、`BetweenOp/AssertOp`（FilterOp 谓词直引，
  CompareOp 同源先例）、`BinaryExecutable`（`io.nop.xlang.utils.EvalHelper.binaryOp` 直调）、
  `InitRef/EnhanceRef`（EvalReference 构造直译）、slot 写族（帧 slot ↔ `$v` 局部变量直译 + 共享
  selfAssignValue/selfIncValue）。
- 已共享复用：`DebugExecutable`（同一 `io.nop.xlang.utils.DebugHelper.v` 调用，LOC 常量内嵌；日志副作用
  不在对拍副作用断言域，返回值透传——调试语义不被静默吞掉）；`TypeOf`（提取后共享）。
- 内联提取至 `XLangSemantics`（解释器同步改调，行为不变回归 = `./mvnw test -pl :nop-xlang -am` 502/0/2 绿）：
  作用域链访问族（getScopeValue/setScopeValue/scopeSelfInc/getGlobalVarValue + 引用族
  getRefValue/asRef/setRefValue/renewReference）、selfAssignValue（自 AbstractExecutable 提取，6 节点类改调）、
  类型操作族（convertValue/convertWithDefault/castValue/instanceOf/typeOf）、属性反射族
  （getPropGetter/getPropSetter/readPropValue/writePropValue + getProperty/setProperty/getAttr/setAttr/
  selfAssignProperty/selfAssignAttr/makeProperty/getMakerGetter/readMakerPropValue/getStaticProperty/
  getterGetProperty/setterSetProperty + 按 propName 全局 PropAccessor 缓存镜像解释器 per-node 缓存语义；
  AbstractPropertyExecutable/AbstractExecutable 改为委托壳，MakeProperty 多态结构保留）、构造族
  （newInstance 两入口——解释器复用编译期 classModel 零查找、生成代码按 className 缓存解析；
  spreadListAdd/spreadMapPut；cloneList/cloneMap 复合字面量）、绑定族（asListBinding/asMapBinding，解构目标
  分派 = AssignIdentifier.assign 语义结构化直译）、guardNotEmpty、varStatus、propIn、range。
- 求值顺序保真：所有写族节点（ScopeAssign/ReferenceAssign/SelfAssign 族/SetProperty/SetAttr）value 无条件
  提升临时变量（只求值一次；复合赋值先读旧值再求值 change）；短路族（NullCoalesce/ConvertWithDefault）分支内
  求值。
- 转译器支持集：`ExecToJavaTranslator.getSupportedNodeClasses()`（87 类 = I2 28 + A 44 + 残余 15，嵌套同族
  变体经继承链解析 `isNodeClassSupported`）；分派仍为 instanceof 链，一致性由 Phase 3 矩阵逐类真实转译验证
  （非清单自证）。
- 无产生路径节点的转译形态裁定：`ResolvedObjFunctionExecutable`/`GetterGetPropertyExecutable`/
  `SetterSetPropertyExecutable` 持编译期解析对象（IEvalFunction/IPropertyGetter 不可内嵌生成源码），生成代码
  按 funcName/propName 走与解释器同一解析实现（invokeObjMethod 分派 / getPropGetter 解析）；
  `CastExecutable` converter 按 className 运行时解析（`ReflectionManager.getConverterForJavaType` 同源）；
  `DebugIdentifierExecutable` 转译期按入口 slotNames 定位槽位（deRef）否则回落 `$scope.getValue`（与
  `ExprExecHelper.getVar` 同一查找序）；`BindVarExecutable` vars 经字面量发射（非可发射类型显式 fail-fast）。
- 只读访问器增量（I5 先例同源，解释器行为不变）：为 30+ exec 节点类补齐转译器消费的 getter
  （getVarName/getSlot/getExpr/getOperator/getValueExpr/getItems 等）。
- **执行中发现并修复的 live 缺陷（in-scope Fix，附 bug note）**：
  1. `TypeOfExecutable.execute` 误引用 `XplInputFormat.value` 枚举常量 → `typeof null` NPE；修复 = 提取共享
     `typeOf`（意图语义 "undefined"），bug note `ai-dev/bugs/2026-08/2026-08-20-typeof-null-npe.md`。
  2. `ObjectBindingAssignExecutable` rest 分支 `tail.put(entry.getKey(), value)` 误放整 map → 应为
     `entry.getValue()`；bug note `ai-dev/bugs/2026-08/2026-08-20-object-binding-rest-value.md`。
- watch-only（非缺陷，不移交 nop-core 域修复）：`ClassModel.getConstructorForArgs` 多 1 参构造候选误选 0 参
  构造（`new Date(0)`/`new ArrayList(10)` 报 wrong number of arguments；`new StringBuilder(16)` 正常），
  corpus 规避该形态；`CastExecutable` 校验 `clazz.isInstance(value)` 而非 converted（忠实转译保留，quirk 记录）。
- 新增错误码：`ERR_EXEC_CLASS_NOT_FOUND`（nop.err.xlang.exec.class-not-found，castValue/instanceOf 运行时
  类解析失败 fail-fast）。
- 单测：`TestExecToJavaTranslatorCoverageA` 18 用例（五族 + 残余源码级真实转译断言；不可产生节点合成树覆盖
  ——引用族 6 + InitRef/EnhanceRef/BindVar/Cast/Getter/Setter/MakeProperty/VarStatus/DebugIdentifier/
  GuardNotEmpty/CloneLiteral/ResolvedObjFunction；行为级执行：scope 自增旧值/引用族 round-trip/typeof null
  修复证明/NewMap+GetProperty/NewObject/数组解构/slot 写复合/NullCoalesce 短路/CloneLiteral 深拷贝新鲜性/
  Convert 异常；fail-fast 反证：slot 越界 + IfExecutable pending 节点）。

### Phase 3 决策与执行记录（2026-08-20）

**corpus 覆盖 A 落地形态**（repo-observable：`CorpusCoverageA` + `xlang-compare/static-a/` 20 个 xpl +
`TestCorpusCoverageAInterpreterBaseline`）：
- 静态单元 20（五族每族 ≥1 硬要求 + 异常单元 2：`exception-prop`（ERR_EXEC_GET_PROP_ON_NULL_OBJ，
  行 3）/`exception-convert`（ERR_CONVERT_TO_TYPE_FAIL，行 2），错误码 + 预期源位置齐备）；动态单元 13
  （MUTABLE_SCOPE_VAR 变体 3 = ScopeSelfInc/Dec/ScopeAssign 平凡形态唯一出口能力记录；TEMPLATE 变体 1 =
  ConcatExecutable 唯一出口；STANDARD 变体 9 镜像静态源，含动态 `exception-convert` 异常镜像）；schema
  四字段齐备（source/inputVars/expectation/kind）。
- 类别节点规则与 CorpusV1 同语义（每条规则一组等价类，树含任一成员即满足）；A 范围白名单改判
  `ExecNodeBaseline.registeredTarget()` 单一事实源（嵌套同族变体按宿主顶层类归并，如
  `SeqExecutable$SimpleSeqExecutable`），消除手工清单漂移。
- 解释器基线：`TestCorpusCoverageAInterpreterBaseline` 4 用例全绿（全量单元 vs 声明预期 + 类别规则 +
  白名单 + 每族静态 ≥1 + 异常单元存在性）。

**java 列对拍**（repo-observable：`TestCorpusCoverageAJavaColumn`，33/33 绿）：
- 静态单元 = 解释器 + java 双列（同树实例分列执行，三层断言 + cross-compare + 身份断言 = 确定性派生
  生成类实例，非解释器树）；动态单元 = 仅解释器列（java 不适用无 skip 记录）+ truffle 缺席显式记录
  （not-registered）。异常单元跨列错误码 + SourceLocation 一致（LOC 常量内嵌生效）。
- **执行中发现并修复的 live 缺陷（in-scope Fix）**：`ExecToJavaTranslator.genDebug` 生成代码误引
  `io.nop.commons.util.ConvertHelper`（不存在类）→ 生成源码编译失败；修复 = 与解释器
  `DebugExecutable.execute` 同源引用 `io.nop.api.core.convert.ConvertHelper`。发现路径 =
  `debug-call-static-a` 单元 java 列对拍（对拍机制生效的直接证据）；回归载体 = 该单元持续在
  `TestCorpusCoverageAJavaColumn` 执行（含 DebugHelper.v 调用链）。
- 会话中断残留修复：`CorpusCoverageA` 缺 3 参 `staticUnit` 重载 + 两单元 inputVars/expectedVars 误用
  （scope-assign 族改输入 n=5 + `.scopeVars` 声明执行后态）+ 基线测试规则语义对齐 CorpusV1 口径。

**覆盖矩阵落地**（repo-observable：`TestExecTranslationCoverageMatrix`，124 用例全绿）：
- 支持集一致性：`ExecToJavaTranslator.getSupportedNodeClasses()` ↔ `ExecNodeBaseline.registeredTarget()`
  双向 set 相等（87 = I2 28 + i3Scope 59），missing/extra 逐向报错。
- 真实转译验证（非清单自证）：registeredTarget 87 类逐类最小实例真实转译成功（断言 EvalMethod 入口
  约定）；slot 依赖节点以程序入口帧包装；嵌套同族变体（SimpleSeq/SimpleBlock 等）经顶层类工厂产生、
  转译器按 `ISeqExecutable` 接口分派（文件级粒度裁定 javadoc 记录）；`ListItem/MapItem` 以含它的
  NewList/NewMap 为最小产生载体。
- fail-fast 反证：B 族 35 类逐类 `isNodeClassSupported=false` + 树节点类构造最小实例断言
  `ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE`（非树节点具体类 `GenNodeAttrExecutable` 以支持集不含为
  pending 证据，矩阵可观测）；`ExecutableFunctionEvalAction` 经 Proxy IFunctionModel 构造。
- 红灯注入（红/绿对照）：测试域合成未注册具体节点类 `FutureExecutable` → 支持集不含 + 转译 fail-fast
  报注入类名 + 基线 `isClassified=false`（新鲜度红灯路径）；绿对照 = LiteralExecutable 支持且已归属、
  IfExecutable 判 pending 但已归属。

**验证汇总**：`./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C` 全绿（nop-xlang 506/0/2，较 Phase 2
基线 502/0/2 净增 4 = CoverageA 基线测试；nop-xlang-java 228/0/0，含 CoverageA java 列 33 + 矩阵 124）；
`:nop-xlang-truffle` 37 用例全绿（消费 compare test-jar 无回归）；`./mvnw compile` 三模块 exit 0；
`-Pqa checkstyle:check` 两模块 exit 0。

## Closure Gates

- [x] 对应类别 corpus 扩充后 java 列 vs 解释器列对拍全绿（含 java 列身份断言）——roadmap I3 验收第一项
- [x] 覆盖矩阵推进（类别内 exec/ 基线节点类逐一注册；新增节点类红灯）——roadmap I3 验收第二项
- [x] 残余算子族归属与边缘节点归属裁定记录 repo-observable（无无主节点留给 I4 闭环）
- [x] 语义敏感操作无双实现（共享 helper 纪律）：生成代码调用的语义 helper 与解释器同一实现来源
- [x] 回归不允许削弱现解释器测试（纪律 3）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：矩阵注册证据 = 真实转译验证而非清单自证；无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl :nop-xlang,:nop-xlang-java -am`
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C`
- [x] checkstyle / 代码规范检查通过（`-Pqa checkstyle:check` 两模块）

## Deferred But Adjudicated

（无——起草时无 deferred 项；执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- `XLangSemantics.invokeGlobalFunction` 每次执行急切构造 `display + "@" + loc` 字符串——I2 closure audit 已裁定的行为等价优化候选（non-blocking，仅错误路径可观测）；后续触及 `XLangSemantics` 的 plan 可顺带收敛，不作为本 plan closure 门。
- `ClassModel.getConstructorForArgs` 多 1 参构造候选误选 0 参构造（watch-only residual，Phase 2 发现：`new Date(0)`/`new ArrayList(10)` 报 wrong number of arguments；解释器与生成代码同源的 nop-core 反射存量行为，非 I3 引入的缺陷；corpus 以 `new StringBuilder(16)` 规避该形态）——Why Not Blocking Closure：双后端同一实现来源（共享 newInstance helper），行为一致故对拍不因此分歧；归属 nop-core 域，后续该域变更时顺带复核。

## Closure

Status Note: 三 Phase 全部完成且逐项勾选：Phase 1 四分区归属裁定 + 矩阵口径 + corpus 形态定稿（`ExecNodeBaseline` 单一事实源 + 新鲜度红灯）；Phase 2 A 五族 + 并入残余 59 类转译落地 + 共享 helper 增量（87 类支持集 + 两处 live 缺陷修复附 bug note）；Phase 3 corpus 覆盖 A（20 静态 + 13 动态单元）解释器基线与 java 列对拍全绿（33/33，含身份断言）+ 覆盖矩阵落地（124 用例：支持集双向一致 + 87 类真实转译 + B 族 35 pending + 红灯注入）。roadmap I3 两项验收（对拍全绿 + 矩阵推进）均达成；I4 只余 B 族，无无主节点。执行中第三处 live 缺陷（genDebug 误引 FQN）由对拍机制发现并 scope 内修复 + 语料回归覆盖。
Completed: 2026-08-20

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh session 子 agent closure audit（task `ses_fe312a5ddffeVNdliAXMZZ6EwH`，read-only + 定向测试复跑）
- Evidence:
  - 每条 Phase Exit Criterion 验证结果：Phase 1/2/3 全 PASS（分区计数 44+15+28+35+16=138 = live 文件数；`TestExecNodeBaselineFreshness` 4/4 绿；roadmap I3/I4 条目注记在位 `roadmap.md:52/:55`；支持集 ↔ `registeredTarget()` 87=87 双向一致；`TestExecToJavaTranslatorCoverageA` 18/18、`TestCorpusCoverageAInterpreterBaseline` 4/4、`TestCorpusCoverageAJavaColumn` 33/33、`TestExecTranslationCoverageMatrix` 124/124、回归 `TestCorpusV1JavaColumn` 22/22 全部 live 复跑绿）
  - 每条 Closure Gate 验证结果：12 项全 PASS（1-6 live 证据如上；7 owner-docs N/A per I11 分工；8 由本 audit 满足；10-11 compile/test/checkstyle 由实现者运行 + audit 定向复跑支持）
  - Anti-Hollow 检查结果：java 列端到端调用链真实（`JavaBackendColumn.execute` → `translate` → `JdkJavaCompiler.compile` → 生成实例 invoke，无解释器兜底/stub）；矩阵非清单自证（`MinimalNodeFactory` 87 类独立构造真实转译）；新增/变更文件无空方法体/静默 no-op（`FutureExecutable` 为红灯注入合成件非生产代码）；`genDebug` 修复验证 = 误引 FQN 全仓清零 + `debug-call` 单元 live 执行证据（DebugHelper.v 日志）
  - `scan-hollow-implementations.mjs --module nop-xlang-java / --module nop-xlang` 均 0 findings（exit 0）
  - Deferred 项分类检查：Non-Blocking Follow-ups 仅含 non-blocking 优化候选 + watch-only residual（`getConstructorForArgs`，双后端同源行为一致，已补记 Why Not Blocking Closure）；无 in-scope live defect 降级
  - audit 发现 2 Minor 文档漂移（动态单元计数 12→13；watch-only 项未记录于 Follow-ups 节）已在收口记录时修复
- 工具门：`./mvnw compile`（三模块）EXIT=0；`./mvnw test -pl :nop-xlang,:nop-xlang-java -am -T 1C` 全绿（506/0/2 + 228/0/0）；`:nop-xlang-truffle` 37 用例全绿；`-Pqa checkstyle:check` 两模块 EXIT=0

Follow-up:

- non-blocking：`XLangSemantics.invokeGlobalFunction` display 串急切构造优化候选（I2 移交，见 Non-Blocking Follow-ups）
- watch-only residual：`ClassModel.getConstructorForArgs` 多候选误选（nop-core 域存量，见 Non-Blocking Follow-ups）
- 其余无 plan-owned 剩余工作（B 族转译 + 矩阵闭环归 I4，truffle 侧归 I6/I7）
