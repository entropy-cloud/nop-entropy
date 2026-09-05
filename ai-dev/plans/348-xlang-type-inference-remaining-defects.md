# 348 XLang 类型推导遗留缺陷修复（审计 P2/P3 剩余项）

> Plan Status: draft
> Last Reviewed: 2026-09-05
> Source: `ai-dev/analysis/2026-09/2026-09-05-xlang-type-inference-correctness-audit.md`（审计 §3 P2-12/13、P3-16、§4 覆盖矩阵、§5 测试评估）；第一批 P1 修复见 `ai-dev/logs/2026/09-05.md` 与 commit `47d5a823fb`
> Related: 无

## Purpose

把 2026-09-05 类型推导审计中第一批修复（commit `47d5a823fb`）之后仍然确认存在的 live defect 收口：泛型推导器的桩实现与死代码、call 表达式实参校验缺失、未覆盖的 AST 分支（class/enum/for-range/eval/custom/filter-op）、窄化语义偏差、以及空转测试。收口后 `GenericTypeInferencer` 与 `TypeInferenceProcessor` 的每个公开行为都有对应的强断言测试。

## Current Baseline

- 第一批修复已落地：switch 推导、block/循环赋值传播、函数返回类型只收集 return 语句、`&&`/`||` 同作用域、字符串 ADD 按 typeName 判断、UnionTypeNarrower 假分支从 state 取原类型、parser 集成点以 `LOG.warn` 暴露推导错误（不中断编译）。
- `nop-kernel/nop-xlang` 模块测试基线：699 tests, 0 failures（2 skipped 为既有）。
- 仍然成立的 live 缺陷（均已在上游审计文档中逐条定位）：
  - `GenericTypeInferencer.validateTypeBounds` 恒返回 `true`（桩），`testValidateTypeBounds` 是断言恒真值的空转测试；
  - `TypeVarBindings.isCompatible` 是死代码；`ERR_TYPE_INFER_TYPE_VAR_CONFLICT` 被 import 但从未抛出——类型变量冲突静默 merge 为 union；
  - `inferFromComplexType` 中 raw type 不兼容时静默 `return true`，不上报任何信息；
  - `processCallExpression` 不校验实参个数与类型兼容性；call-site spread 实参未展开为元素类型参与推导（`XLang` 函数类型 `IFunctionType` 本身无 varargs/rest 元数据，varargs 对齐无法在现有类型模型上实现，见 Deferred）；
  - `ForRangeStatement`、`EvalExpression`、`CustomExpression` 等语句/表达式 kind 在 `TypeInferenceProcessor` 中无覆写，`defaultProcess` 不递归 → 这些子树整棵不做推导；`ImportDeclaration` 未覆写导致已有的 `processImportDefaultSpecifier/processImportNamespaceSpecifier` 覆写不可达（遍历走不到 specifier）；
  - `processNewExpression` 当 callee（`NamedTypeNode`）的 `getTypeInfo()` 为 null 时返回 ANY，未按类型名经 ReflectionManager 解析 raw type；
  - `processMemberExpression` 不处理 computed property（`obj[expr]`），返回 ANY（无诊断）；
  - `UnionTypeNarrower.getTypeByName("object")` 返回 `MAP_TYPE` 过强；`x === true` 布尔字面量窄化未实现（类注释声称支持）；
  - `mergeConditionalVariableTypes` 在"有 else 分支、变量只在 then 分支赋值、无外层 base 类型"时直接取 then 类型，未并入 undefined 语义（偏乐观）。
- 推导错误的暴露方式是 `LOG.warn`（advisory），不改变编译结果——因此精度类缺陷不阻塞运行时行为，但仍是确认的算法缺陷。

## Goals

- `GenericTypeInferencer` 无桩、无死代码：边界校验要么真实实现要么删除桩并同步测试；类型变量冲突产生显式错误（进 collector），不再静默 union。
- `processCallExpression` 对实参做个数与类型兼容性校验，错误进 `TypeErrorCollector`；支持 varargs/rest 形参与 spread 实参的基本对齐。
- 未覆盖的 AST 分支（class/enum/for-range/eval/custom/filter-op）至少完成子节点递归推导，不留整棵跳过的分支；`new Foo()`（Identifier callee）能推导出构造类型。
- 窄化语义修正：`typeof x === 'object'` 不再窄化为 `MAP_TYPE`；`x === true/false` 字面量窄化落地或显式裁定不做并写明理由。
- 三个空转测试（`testUnionTypeNarrowingFromCondition`、`testLogicalExpressionType`、`testValidateTypeBounds`）替换为强断言版本；每个新增行为有对应回归测试。

## Non-Goals

- 不把类型推导错误升级为编译失败（保持 advisory/warn 语义；是否 fail-fast 是独立的 product decision）。
- 不实现完整 Hindley-Milner/结构化子类型系统；只修复确认的缺陷路径。
- 不做编译 scope 全量桥接（`IXLangCompileScope` 变量注入根 state）——影响面大，单独裁定（见 Deferred）。
- 不处理 `nop-xlang-java` / `nop-xlang-truffle` 工作区中与本计划无关的既有未提交改动。

## Scope

### In Scope

- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/GenericTypeInferencer.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/UnionTypeNarrower.java`
- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compile/TestGenericTypeInferencer.java`
- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compile/TestTypeInferenceProcessor.java`
- 相关 `ai-dev/logs/`、`docs-for-ai/`（如触及行为约定）

### Out Of Scope

- `XLangErrors` 新错误码之外的所有 public API 变更（保持 collector 内部消化）。
- scope 桥接、错误 fail-fast、`nop-xlang-java`/`truffle` 模块。

## Execution Plan

### Phase 1 - GenericTypeInferencer 去桩与冲突上报

Status: planned
Targets: `GenericTypeInferencer.java`、`TestGenericTypeInferencer.java`

- Item Types: `Fix | Decision`

- [ ] 删除 `validateTypeBounds` 桩方法及其唯一调用方（仅测试）与三个相关测试（`testValidateTypeBounds`、`testValidateTypeBoundsWithNullArgs`、`testValidateTypeBoundsWithEmptyArgs`）。裁定理由：边界信息只存在于 `TypeParameterNode` AST，`ITypeVariable`/`IGenericType` 层无边界 API，真实校验需要 public API 变更（被 Out Of Scope 排除），保留恒真桩违反 No Silent No-Op 规则
- [ ] 删除 `TypeVarBindings.isCompatible` 死代码（其逻辑并入 `bind()` 的冲突判定路径）
- [ ] `bind()` 在两次绑定类型互不兼容（无子类型关系且非 any）时通过 `TypeErrorCollector` 上报 `ERR_TYPE_INFER_TYPE_VAR_CONFLICT`（含类型变量名）；collector 为 null 时跳过上报（多个现有测试以 null collector 调用，不得 NPE）。注意：现有 `TestGenericTypeInferencer` 中断言"冲突 merge 为 union"的用例（`testTypeConflict`、`testTypeConflictProducesUnionType` 等）需同步改写为冲突错误断言——这是行为裁定变更，不是回归
- [ ] `inferFromComplexType` raw type 不兼容时向 collector 记 warning（仅入 collector 的 warnings 列表；parser 集成点当前只消费 errors，warning 端到端不可见属预期，Phase 5 的 e2e 断言只针对 errors）

Exit Criteria:

- [ ] `GenericTypeInferencer` 中不存在恒真返回的公开方法与未被调用的私有方法（以 grep/评审确认）；`validateTypeBounds` 及三个相关测试已删除
- [ ] 类型变量冲突场景有测试证明错误进入 collector 且错误码为 `ERR_TYPE_INFER_TYPE_VAR_CONFLICT`；原 union-merge 断言用例已改写
- [ ] No owner-doc update required（推导器为内部算法组件，无 docs-for-ai 条目约定其行为）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - call 实参校验与 spread 实参展开

Status: planned
Targets: `TypeInferenceProcessor.java`（`processCallExpression`）、`TestTypeInferenceProcessor.java`

- Item Types: `Fix`

- [ ] 实参个数校验：callee 为函数类型时，实参数 ≠ 形参数 → collector error（含函数类型名与两边的个数）。注意：`IFunctionType` 无 varargs 元数据，不做"末参为 List 即 varargs"的启发式（会压制合法的个数错误）；varargs 对齐记入 Deferred
- [ ] 实参类型校验：逐位用 `isTypeCompatible(形参类型, 实参类型)`，不兼容 → collector error（跳过 any 与无类型位置）
- [ ] call-site spread 实参：`SpreadElement` 实参在参与个数/类型比对与泛型推导时展开为元素类型（复用已有 `getSpreadElementType`）；仅当 parser 能在 call 实参位置构造出 SpreadElement 时提供经 parser 的用例，否则允许手搓 AST 用例并记录
- [ ] 校验只进 collector（LOG.warn 暴露），不影响返回类型推导与编译结果

Exit Criteria:

- [ ] 新增测试：正常调用不产生 error；个数不匹配产生 error；类型不匹配产生 error；spread 实参类型正确参与泛型推导
- [ ] 既有 699 基线测试全绿（无误报导致的既有用例失败；若既有用例因新校验暴露真实缺陷，修复用例并记录）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - AST 覆盖补齐与 new 表达式构造类型

Status: planned
Targets: `TypeInferenceProcessor.java`、`TestTypeInferenceProcessor.java`

- Item Types: `Fix | Decision`

- [ ] `ForRangeStatement` 覆写：循环变量（`getVar()`）按区间语义绑定为 int（运行时构造的节点可能无 step；step 为浮点字面量时提升为对应数值类型），index 绑 int；body 内对外层变量的赋值经 `mergeChildAssignments` 回写
- [ ] `EvalExpression` → any、`CustomExpression` → any：覆写为递归处理子节点后返回保守类型
- [ ] `ClassDefinition`：方法按函数声明路径推导（方法名/字段注册进作用域）；构造语义不实现，保持保守处理并注释说明
- [ ] `EnumDeclaration`：注册枚举名为保守类型（无法从 AST 构造 resolved 枚举类型，ReflectionManager 只解析 Java 类），成员访问返回成员声明类型或 ANY——以"不再整棵跳过"为最低标准
- [ ] `processNewExpression`：callee 为 `NamedTypeNode` 且 `getTypeInfo()==null` 时，按其类型名经 `ReflectionManager` 解析 raw type，解析成功返回该类类型，失败保持 ANY
- [ ] `processMemberExpression`：computed property 对 List/Map 对象给出元素/值类型（`list[i]` → 元素类型），其余 computed 保持 ANY
- [ ] import/export 可达性修复：为 `ImportDeclaration`（及 `ExportDeclaration`/`ExportNamedDeclaration`/`ExportAllDeclaration`）增加覆写递归子节点，使已有 specifier 覆写可达；`ExportSpecifier` 等元数据节点同步裁定
- [ ] 完整裁定清单（写入 daily log 并在 plan closure 时核对）：语句级未覆写 kind 逐一裁定——`CompilationUnit`（递归 body）、`ImportDeclaration`/`ImportAsDeclaration`/`ImportSpecifier`（递归）、Export 系列（递归）、`TypeAliasDeclaration`（注册别名映射或保守跳过并注明）；表达式级——`TemplateExpression`（区别于已覆写的 `TemplateStringExpression`，按模板求值返回 string）、`MacroExpression`（递归后 any）、`OutputXmlAttrExpression`/`OutputXmlExtAttrsExpression`（void）；`Decorator(s)`、`MetaObject/Property/Array`、`QualifiedName`、各 TypeDef 节点、`EnumMember`、`FieldDeclaration` 裁定为"无独立运行时类型语义，随父节点递归处理，无需单独覆写"或补覆写

Exit Criteria:

- [ ] 逐一测试：for-range 循环变量类型、eval/custom 返回类型、class 内方法返回类型可被推导、enum 成员访问不跳过、`new Foo()`（NamedTypeNode callee）返回类型、`list[i]` 元素类型、import 说明符覆写可达（经 ImportDeclaration 递归）
- [ ] 分发表对照检查完成：每个语句级 kind 都有"覆写 / 随父递归 / 无运行时语义"三者之一的显式裁定，清单记录在 daily log；不存在整棵跳过且含类型语义的语句级 kind
- [ ] **审查勘误记录**：审计矩阵中 `FilterOpExpression` 为幻影条目（无此 AST kind；其具体子类 CompareOp/AssertOp/BetweenOp 已全部覆写返回 boolean），审计文档同步修正
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 窄化语义修正

Status: planned
Targets: `UnionTypeNarrower.java`、`TypeInferenceProcessor.java`（`mergeConditionalVariableTypes`）、`TestTypeInferenceProcessor.java`

- Item Types: `Fix | Decision`

- [ ] `getTypeByName("object")` 改为返回 null（不窄化），调用方对 null 不写入 narrowed map，并注释说明：任意 Java 对象都满足 `typeof === 'object'`，窄化为 MAP 过强
- [ ] `x === true` / `x === false`：按 boolean 级语义实现（类型系统无字面量类型）——true 分支窄化为 boolean；false 分支仅当 union 含其它子类型时移除 boolean。同步修正 `UnionTypeNarrower` 类注释中"x === true → 窄化为 true 字面量类型"的不实描述
- [ ] `mergeConditionalVariableTypes`：有 else 分支且变量只在 then 赋值、无 base 类型时，合并结果并入 null/undefined 语义（union NULL）而非直接取 then 类型
- [ ] 每项修正配回归测试（走完整 `processIfStatement` 的集成断言，不只测 `collectNarrowedTypes`）

Exit Criteria:

- [ ] `typeof x === 'object'` 不再产生 `MAP_TYPE` 窄化（测试断言 narrowed map 中无 x 或值为非强假设类型）
- [ ] `x === true` 字面量窄化有正反两向测试
- [ ] if/else 单侧赋值的合并类型含 NULL 的测试
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 测试加固与收口

Status: planned
Targets: `TestTypeInferenceProcessor.java`、`TestGenericTypeInferencer.java`

- Item Types: `Proof`

- [ ] `testUnionTypeNarrowingFromCondition` 重写：断言 narrowed map 内容（x → string，null 被移除）
- [ ] `testLogicalExpressionType` 重写：AND true 分支的窄化内容断言
- [ ] 汇总运行 `./mvnw test -pl nop-kernel/nop-xlang`，记录最终基线数字（执行前先重跑确认 699 基线仍成立）
- [ ] 端到端验证：至少一条测试经 `IXLangExprParser.parseSimpleExpr(loc, source, scope)` + `buildExecutable` 入口走完整推导路径（需构造 `IXLangCompileScope`，现有测试无可参考先例，脚手架成本已计入），断言 AST 节点上的 `returnTypeInfo` 与 collector 的 errors（**只断言 errors**——warnings 当前不被 parser 集成点消费）

Exit Criteria:

- [ ] 三个空转测试全部替换为强断言版本
- [ ] `./mvnw test -pl nop-kernel/nop-xlang` 全绿，基线数字写入 daily log
- [ ] 端到端（parser 入口）测试存在且通过
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 审计报告 §3/§4 中所有标记为 in-scope 的确认缺陷已修复或有 recorded adjudication
- [ ] `GenericTypeInferencer` 无桩/死代码（Phase 1 Exit Criteria）
- [ ] call 校验、AST 覆盖、窄化修正全部有强断言测试覆盖
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] `./mvnw test -pl nop-kernel/nop-xlang` 全绿
- [ ] checkstyle / 代码规范检查通过
- [ ] 独立子 agent closure-audit 已完成并把 evidence 写入本 plan
- [ ] Anti-Hollow Check：新增覆写/校验路径在运行时确实被调用（端到端 parser 测试证明），无空方法体/恒真桩
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/348-xlang-type-inference-remaining-defects.md --strict` 退出码 0

## Deferred But Adjudicated

### varargs/rest 形参对齐（审计 P2-13 部分）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `IFunctionType`/`GenericFunctionTypeImpl` 无 varargs/rest 元数据；实现需要对函数类型 public API 加 varargs 标志（Out Of Scope 明确排除 public API 变更）。启发式"末参为 List 即 varargs"会压制 Phase 2 新增的合法个数校验，已否决。
- Successor Required: yes
- Successor Path: 待立项（前置条件：函数类型模型扩展 varargs 元数据）

### 编译 scope 全量桥接（审计 P2-14）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 推导结果为 advisory（LOG.warn），外部变量退化为 ANY 只是精度损失而非错误判定；桥接需要定义 `IXLangCompileScope` 变量到 `TypeInferenceState` 的映射契约，影响面跨 LexicalScopeAnalysis/BuildExecutableProcessor，超出本缺陷修复计划的结果面。
- Successor Required: yes
- Successor Path: 待立项（建议 `ai-dev/plans/` 新 plan）

### 类型错误 fail-fast（audit P1-1 的升级方向）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 warn 语义是第一批修复裁定的行为，本计划不改变错误处理策略。
- Successor Required: no
- Successor Path: 无（product decision）

## Non-Blocking Follow-ups

- `ConditionalExpression`（三元）AST 节点当前被注释禁用，启用时需同步补推导覆写与测试。
- `mergeConditionalVariableTypes` 的 switch fall-through 精确控制流合并（本计划只做 case 级保守合并）。

## Closure

Status Note: （待关闭时填写）
Completed: （待关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （待关闭时填写）
- Evidence: （待关闭时填写）
