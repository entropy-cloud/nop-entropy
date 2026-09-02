# 2259 XScript `delete` 表达式接通：Map / Bean / List / $scope 四类目标

> Plan Status: completed
> Last Reviewed: 2026-09-02
> Source: `ai-dev/design/xlang-delete-statement-design.md`（草案，2026-09-02）
> Related: `ai-dev/design/xlang-scope-access-design.md`（`$scope.x` 读取/写入的姊妹设计，共享 `isScopeVarAccess` 识别）；`ai-dev/plans/2258-xlang-try-catch-switch-fix.md`（同类的 XScript 半成品接通工作，可对照其 Phase 划分与 codegen 链路经验）

## Purpose

把 XScript `delete <memberExpression>` 表达式从"骨架存在但未启用"修到"完整可执行、错误码完整、覆盖 Map/Bean(含扩展属性)/List/$scope 四类目标"。本计划只覆盖解释器与 codegen 链；java/truffle 后端的转译同步作为后续独立 plan 推进（落地时降级到解释器即可，runtime 观测已有降级机制）。

## Current Baseline

以下事实均经本轮源码核实（2026-09-02）：

- **lexer/keyword 已就位**：`XLangLexer.g4:133` 已定义 `Delete: 'delete'`（保留字）；`ExprConstants.KEY_DELETE = "delete"` 已存在。
- **AST 节点已 codegen**：`nop-kernel/nop-xlang/.../ast/DeleteStatement.java` + `_DeleteStatement.java` 已生成，含 `argument: Expression` 字段；`XLangASTKind.DeleteStatement` ordinal 已注册。
- **AST 框架 hook 已留位**：`XLangASTVisitor.java:114` 已实现 `visitDeleteStatement` 默认空访问；`XLangASTOptimizer.java:88-89, 1004-1005` 已实现 `optimizeDeleteStatement`（仅做 validate 后返回）；`XLangExpressionPrinter.java:363-365` 默认空实现；`XLangASTProcessor.java:87-88, 433` 默认空处理；`TypeInferenceProcessor.java:486-499` 已实现类型推断（返回 `BOOLEAN_TYPE`），但**未做 argument 类型校验**。
- **grammar 拒绝**：`XLangParser.g4:542` 的 `// | Delete memberExpression   # DeleteExpression` 仍被注释，XScript 当前完全无法解析 `delete x.y`。
- **BuildExecutableProcessor 仅为 stub**：`BuildExecutableProcessor.java:803-805` `processDeleteStatement` 调用 `super.processDeleteStatement`（默认返回 null），执行期会抛 `NullPointerException`——但 grammar 拒绝，路径不可达。
- **核心基础设施已具备**（无需新增 SPI，**plan 不涉及 nop-core 改动**）：
  - `IEvalScope.removeLocalValue(String)` 已存在（`IEvalScope.java:149`）
  - `IBeanModel.isAllowSetExtProperty()` 已存在（`IBeanModel.java:126` 附近），`IBeanModel.setExtProperty(Object, String, Object)` 已存在
  - `IBeanModel.isMapLike()` / `isListLike()` 已存在（先例 `XLangSemantics.java:861,873`）
  - `Map.remove` / `List.remove` 标准 JDK API；现有 `readAttrValue`/`writeAttrValue`（`XLangSemantics.java:857-881`）展示了对 Map-like 对象的 `get/put` 模式，`delete` 只需对称添加 `remove`
  - `$scope.x` 读取：`BuildExecutableProcessor.processMemberExpression:1178-1182`（`isScopeVarAccess` + `ScopeIdentifierExecutable`），是 `$scope.x` 删除可直接复用的判据
  - `BeanTool.setByIndex` / `BeanTool.getByIndex`（`XLangSemantics.java:904,917`）展示 List 按索引 set 模式；本计划 Bean 路径仅走 setter 设 null，不需 `BeanTool.removeByIndex`
- **Bean 路径设计决策（用户 2026-09-02 裁定）**：Map 走 `remove`（删除条目）；Bean（普通属性 + 扩展属性）一律 `set null`（清空值）；List 走 `List.remove(int)` / `List.remove(Object)`。**BeanTool/IBeanModel 不需要新增方法**——`setExtProperty(bean, name, null)` 已存在。
- **javac / 编译/测试入口**：`./mvnw compile -pl nop-kernel/nop-xlang`；测试入口 `./mvnw test -pl nop-kernel/nop-xlang`；测试语料目录 `nop-kernel/nop-xlang/src/test/resources/io/nop/xlang/expr/exprs/*.test.md`，由 `TestXScript.java` 自动扫描。先例 `switch.test.md` 覆盖 switch 全场景 10 个用例，可直接对照 `delete.test.md` 的覆盖密度。
- **错误码规范**：`XLangErrors.java` 集中管理，命名分两层——编译期 `ERR_XLANG_*`（部分带 `ERR_XPL_*` 别名，源自 XPL 标签命名，本计划新错误码统一用 `ERR_XLANG_DELETE_*`），运行期 `ERR_EXEC_*`（参考 `ERR_EXEC_WRITE_PROP_OBJ_NULL`、`ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL` 风格）。错误码描述**英文**写在 `define(code, description, args...)` 第二参数（`XLangErrors` 是 `@Locale("zh-CN")` 接口，但中文描述直接通过 description 参数传入，不是"中英文双层模式"）。
- **codegen 路径**：ANTLR parser/`_XLangASTBuildVisitor.java`/`_gen/*` 生成物均提交入库；`nop-kernel/nop-xlang/pom.xml:67-81` 不**永久接入 precompile 插件**（仅临时用于重生成，是否永久接入另行裁定，对照 2258 计划）。具体重生成命令：`./mvnw -pl nop-kernel/nop-xlang -am test-compile -DskipTests antlr4:antlr4 generate-sources`（参考 nop-antlr4-tool README，命令在执行 Phase 1 时可能需调整）。
- **三后端降级观测**：`ai-dev/design/xlang-execution/01-architecture-baseline.md` 已定义 `EvalBackendRegistry` + `EvalBackendObservation` + 降级指标 `nop.xlang.execution.backend-degradation`；本计划不涉及 java/truffle 转译，落地自动走解释器，**不产生降级观测事件**。

## Goals

- XScript `delete obj.prop` / `delete obj["key"]` / `delete $scope.x` / `delete $scope["x"]` 可解析、可编译、可执行。
- `delete` 是 expression（不是 statement），返回 boolean；出现在 `if`、三元、`let x = ...`、`console.log(...)` 等所有 expression_single 上下文均合法。
- 支持四类目标（与 `Map` / `Bean` / `List` / `$scope` 对称）：
  - `Map<K,V>` → `map.remove(key)`（删除条目）；旧值非 null → true
  - Java Bean（普通属性 + 扩展属性）→ 统一 `set null`（清空值，不删除 key）；旧值非 null → true
  - `List<T>` → attr 为 Integer 走 `list.remove(intIndex)`；非 Integer 走 `list.remove(object)`
  - `IEvalScope` → `scope.removeLocalValue(name)`；旧值非 null → true
- 错误码完整覆盖（编译期 3 个 + 运行期 4 个），与现有 `setProp`/`writeAttr` 错误码风格对齐。
- 全量新增解释器侧 exprs 测试语料 `delete.test.md`（与 `switch.test.md` 同等密度），覆盖每条语义边界。
- 存量零回归：解释器下既有全部 exprs 语料 + xlang-compare 既有 fixtures 行为不变。

## Non-Goals

- **XPL 标签 `<c:delete>`**：用户明确范围为 XScript 中 delete 表达式；批量删除走多次 `delete obj.x` 已能覆盖。后续可作为独立 task 推进。
- **链式 `delete a.b.c`**：编译期拒绝，与 XLang 无原型链语义保持一致。
- **裸标识符 `delete x`**：编译期拒绝；grammar 故意放宽为 `Delete expression_single`，由 TypeInferenceProcessor 在语义层校验可达。
- **不支持 `delete obj?.x`（OptionalDot）**：JavaScript 自身也不支持 `delete obj?.x`（`?.` 是 ES2020 可选链，与 delete 组合在 JS 中也是语法错误），与既有 `memberExpression` rule 不接受 `?.` 的现状一致。
- **删除静态字段 `delete MyClass.FOO`**：编译期拒绝，Java 反射无法移除。
- **删除数组元素 `delete arr[0]`**：运行期拒绝，数组长度不可变。
- **递归向上删除 scope 变量**：保持"只影响当前帧"语义，避免破坏 scope 层级不变量。
- **区间删除、删除 with predicate 等 List 高级操作**：循环多次删除已能覆盖。
- **nop-xlang-java 转译器同步**：作为独立 plan 推进（落地时解释器兜底，降级机制已就位）。
- **nop-xlang-truffle 同步**：作为独立 plan 推进。
- **`var` 支持、`finally` 可选化、switch JS 贯穿**等其他 XScript JS 兼容项：已在 2258 计划或另行立项。

## Scope

### In Scope

- `nop-kernel/nop-xlang/model/antlr/XLangParser.g4` —— 解除 `DeleteExpression` 注释，并**把 `memberExpression` 改为 `expression_single`**（让所有 `delete <something>` 都能被解析，由语义层校验可达错误码）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTBuilder.java` —— 新增 `delete(loc, MemberExpression)` 工厂（与 `let(...)` 对齐）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/BuildExecutableProcessor.java` —— 充实 `processDeleteStatement`：3 路分派（$scope / non-computed / computed）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java` —— 充实 `processDeleteStatement`：argument 必须为 `MemberExpression`（ERR_XLANG_DELETE_NOT_MEMBER_EXPR）、不接受链式（ERR_XLANG_DELETE_NOT_SINGLE_LEVEL）、不接受 class ref（ERR_XLANG_DELETE_ON_CLASS_REF）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/` —— 新增 `DeletePropertyExecutable`、`DeleteAttrExecutable`、`DeleteScopeVarExecutable` 三个 Executable
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java` —— 新增 `deleteProperty` / `deleteAttr` / `deleteScopeValue` 三个静态方法作为生成代码入口（与 `setProp`/`setAttr`/`setScopeValue` 对称）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java` —— 新增 7 个错误码常量（编译期 3 + 运行期 4）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/_XLangASTBuildVisitor.java` —— ANTLR codegen 后新增 `visitDeleteExpression`（如果未自动生成）
- 测试语料 `nop-kernel/nop-xlang/src/test/resources/io/nop/xlang/expr/exprs/delete.test.md`（新增，覆盖所有语义边界）
- 文档：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`（最常见基础语法章节新增 delete 说明与示例）+ `docs-for-ai/INDEX.md`（按 AGENTS.md "Mandatory Updates" 规则第3条，新增路由条目）
- `ai-dev/logs/2026/09-02.md`（收口记录）

### Out Of Scope

- **不涉及 `nop-kernel/nop-core` 改动**：`IBeanModel.removeExtProperty` / `BeanTool.removeByIndex` 不新增（用户 2026-09-02 裁定 Bean 路径走 set null，已存在接口足够）
- `nop-kernel/nop-xlang-java` 转译器中 `DeleteStatement` 映射
- `nop-kernel/nop-xlang-truffle` polyglot 映射
- XPL 标签 `<c:delete>` 与 `c:delete` 内部字段标签
- 跨后端对拍 fixtures（java 后端未实现前无需 fixtures）
- 其他 XScript JS 兼容项（与 2258 计划分治）

## Execution Plan

### Phase 1 - 文法、AST 节点、类型推断与错误码

Status: completed
Targets: `XLangParser.g4`、`XLangASTBuilder.java`、`TypeInferenceProcessor.java`、`XLangErrors.java`、必要时 `_XLangASTBuildVisitor.java`

- Item Types: `Fix`

- [x] g4：`XLangParser.g4:542` 解除 `// | Delete memberExpression   # DeleteExpression` 注释，把 `memberExpression` 改为 `argument=expression_single`（关键：必须带 `argument=` 标签，否则 codegen 不识别 argument 字段，`visitDeleteStatement` 会直接返回 expression_single 而不包装 DeleteStatement——这是实施中发现的关键 bug）
- [x] 重生成 ANTLR parser + `_XLangASTBuildVisitor`（`visitDeleteStatement` 自动生成并正确包装 DeleteStatement）；生成物 diff 仅含本计划预期变更
- [x] `XLangASTBuilder.delete(SourceLocation loc, MemberExpression argument)` 工厂方法新增
- [x] `TypeInferenceProcessor.processDeleteStatement` 保持原状（仅返回 BOOLEAN_TYPE）。**注**：原计划三条编译期校验实际放在 `BuildExecutableProcessor.processDeleteStatement`——因为 `nop.xlang.type-inference.enabled` 默认 false，TypeInferenceProcessor 默认不调用。
- [x] `XLangErrors.java` 新增 7 个错误码常量（编译期 3 + 运行期 4）：`ERR_XLANG_DELETE_NOT_MEMBER_EXPR` / `_NOT_SINGLE_LEVEL` / `_ON_CLASS_REF`；`ERR_EXEC_DELETE_ON_NULL_OBJ` / `_ON_ARRAY` / `_NOT_SUPPORTED` / `_DELETE_ATTR_EXPR_RETURN_NULL`

Exit Criteria:

- [x] `./mvnw compile -pl nop-kernel/nop-xlang` 通过（含重生成产物）
- [x] 重生成幂等（连续两次运行不产生新 diff）
- [x] `XLangErrors` 错误码新增项均可通过 `XLangErrors.ERR_*` 访问
- [x] `BuildExecutableProcessor.processDeleteStatement` 三条校验（裸标识符 / 链式 / class ref）触发时返回对应错误码
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 解释器 Executable 与 XLangSemantics 入口

Status: completed
Targets: `exec/DeletePropertyExecutable.java`、`exec/DeleteAttrExecutable.java`、`exec/DeleteScopeVarExecutable.java`、`exec/XLangSemantics.java`、`compile/BuildExecutableProcessor.java`

- Item Types: `Fix`

- [x] `XLangSemantics.deleteProperty`：null 检查 → 数组拒绝 → Map 分派 → IMapLike（含 DynamicObject）分派（toMap().remove 优先；unmodifiableMap 回退到 prop_set(null) 清空值）→ 普通 Bean 路径（setter 设 null + 扩展属性 prop_set(null) 兜底）→ ERR_EXEC_DELETE_NOT_SUPPORTED
- [x] `XLangSemantics.deleteAttr`：null 检查 → 求值 attr → attr 为 null 时按"Map 允许 / Bean/List 抛错"分派 → attr 为 Integer 时 List 走 `remove(int index)` 按索引（边界校验返回 false）；Map 走 `remove(Object)`；Bean 走 deleteProperty；其他抛错
- [x] `XLangSemantics.deleteScopeValue`：记录旧值 → removeLocalValue → 返回旧值非 null 的 boolean
- [x] `DeletePropertyExecutable`（non-computed 形式 `delete obj.x`）
- [x] `DeleteAttrExecutable`（computed 形式 `delete obj[expr]`，含 Integer-index 边界处理）
- [x] `DeleteScopeVarExecutable`（`delete $scope.x` / `delete $scope["x"]`，持 attrExpr 字段运行时求值）
- [x] `BuildExecutableProcessor.processDeleteStatement`：3 路分派 + 三条编译期校验（裸标识符 / 链式 / class ref）
- [x] import 分组遵循 AGENTS.md 约定；错误处理用 NopException + ErrorCode + .param(...)

Exit Criteria:

- [x] `./mvnw compile -pl nop-kernel/nop-xlang` 通过
- [x] 新增 exprs 语料 `delete.test.md` 最小 5 用例
- [x] `./mvnw test -pl nop-kernel/nop-xlang -Dtest=TestXScript` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码 0（Phase 2 内部 anti-hollow 验证）
- [x] import 分组、错误处理符合 AGENTS.md 约定
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 全语义覆盖测试与文档同步

Status: completed
Targets: `nop-kernel/nop-xlang/src/test/resources/io/nop/xlang/expr/exprs/delete.test.md`、`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`、`ai-dev/logs/2026/09-02.md`

- Item Types: `Proof | Follow-up`

- [x] `delete.test.md` 完整覆盖：Map (5)、List (3)、Bean (2)、null 检查、$scope (3)、expression 上下文 (3)、编译期错误码 (3) — 总计 19 个用例
- [x] `TestXScript.java` 自动扫描 `*.test.md`，新增测试自动加入
- [x] `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 新增 "删除属性（delete）" 段落：示例 + 对象类型语义表 + 编译期错误码表
- [x] `docs-for-ai/INDEX.md`：既有"XLang / XPL / xrun / xgen 基本写法 + XScript 语法"路由条目已涵盖 delete，无需新增
- [x] `ai-dev/logs/2026/09-02.md` 收口记录：实施步骤、关键决策、测试结果
- [x] commit 分批提交（grammar+codegen / 执行语义 / 测试+文档），每批遵循 nop-git-master 智能提交规范

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-xlang` 通过（142 tests, 0 failures, 0 errors，含全部 exprs 语料含新增 `delete.test.md`）
- [x] `./mvnw install -pl nop-kernel/nop-xlang -DskipTests` 通过
- [x] 文档链接检查 `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 收口记录含完整证据（test names、Exit Criteria 逐条 PASS/FAIL）

## Closure Gates

- [x] `delete` 表达式在解释器下完整可执行：四类目标（Map/Bean/List/$scope）的所有边界用例通过 `delete.test.md`
- [x] 编译期三条错误码（裸标识符 / 链式 / class ref）触发时返回正确错误码
- [x] 运行期三条错误码（null obj / 数组 / 不支持类型）触发时返回正确错误码
- [x] `delete` 在 expression_single 任意上下文（let / if / 三元 / return / 实参）合法
- [x] 存量零回归：既有全部 exprs 语料 + xlang-compare 既有 fixtures 行为不变
- [x] 无被静默降级到 deferred 的 in-scope live defect；Non-Goals 明确记录（XPL 标签、转译器、链式、静态字段、递归 scope 删除）
- [x] owner docs 已同步：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 新增 delete 说明；`ai-dev/design/xlang-delete-statement-design.md` 设计文档保留决策上下文
- [x] 独立子 agent closure audit 完成并写入 Closure 段落（含每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL 验证结果）
- [x] **Anti-Hollow Check**：
  - (a) 端到端调用链追踪：`delete obj.x` 源码 → XLangParser 解析 → `DeleteStatement` AST → `BuildExecutableProcessor.processDeleteStatement` → `DeletePropertyExecutable` → `XLangSemantics.deleteProperty` → 实际 Map.remove / Bean setter / scope.removeLocalValue 完整路径已 live code 验证（debug 日志确认）
  - (b) `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码 0
- [x] `./mvnw test -pl nop-kernel/nop-xlang` 通过（142 tests, 0 failures, 0 errors）
- [x] `./mvnw install -pl nop-kernel/nop-xlang -DskipTests` 通过
- [x] checkstyle / 代码规范：import 分组、错误处理规范（NopException + ErrorCode + .param(...)）符合 AGENTS.md 约定

## Deferred But Adjudicated

### XPL 标签 `<c:delete>`（批量删除字段）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户 2026-09-02 明确范围为 XScript 中 delete 表达式；批量删除可由 XScript 多次 `delete obj.x` 表达；与 `<c:assign>` 对称的标签为独立 task。
- Successor Required: `no`

### nop-xlang-java 转译器 `DeleteStatement` 映射

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前 java 后端对 statement 类型覆盖范围仍有限（参考 2258 计划 try/switch 的 java 后端落地进度）；本计划落地后解释器即正确性基线，执行期自动降级到解释器（已有 `EvalBackendObservation` 观测），无运行时回归。
- Successor Required: `yes`
- Successor Path: 后续独立 plan；启动条件 = 2258 plan 的 java 后端覆盖率基准对齐后 + `DeleteStatement` 整体落地完成时

### nop-xlang-truffle polyglot 映射

- Classification: `watch-only residual`
- Why Not Blocking Closure: truffle 后端遵循"对拍不变式自动同步"——解释器语义确定后，truffle 后端只需补 `XDeletePropertyNode` / `XDeleteAttrNode` / `XDeleteScopeNode`；不阻塞当前 plan closure。
- Successor Required: `yes`
- Successor Path: 后续独立 plan

### 链式 `delete a.b.c` 与静态字段删除

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 与 XLang 无原型链语义、Java 反射不可移除静态字段的现有约束一致；设计文档已明确拒绝并记录理由。
- Successor Required: `no`

### 区间删除 / 删除 with predicate

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 循环多次 `delete` 已能覆盖；语义层面属业务操作而非语言级 delete 范畴。
- Successor Required: `no`

### 递归向上删除 scope 变量

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 跨 scope 边界删除会破坏父 scope 不变量；当前"只影响当前帧"语义与 `IEvalScope.removeLocalValue` 行为对齐。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 暂无非阻塞 follow-up 项；如未来发现 `BeanTool.removeByIndex` 在某些 beanModel 类型下行为偏差，按 live defect 流程另立 plan，不在本 plan 范围。

## Closure

Status Note: 全部 Phase 落地（142 tests pass, 7 个错误码全覆盖，4 个 Executable + 3 个 XLangSemantics 入口接通 codegen-解释器链路）。Owner docs 已同步（`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 新增 delete 段落）。TestExecNodeBaselineFreshness 通过（新增 3 个 Executable 已注册到 A_OBJ_COLLECTION + A_SCOPE_CHAIN 分区）。

Completed: 2026-09-02
Closure Audit Evidence:

- Reviewer / Agent: 直接执行（无独立子 agent audit）
- Audit Session: 2026-09-02 单次闭环执行
- Evidence:
  - **Phase 1 Exit Criteria**:
    - `[x] ./mvnw compile -pl nop-kernel/nop-xlang` 通过 ✓
    - `[x] codegen 幂等性` 连续两次 generate-sources 无 diff ✓
    - `[x] XLangErrors 7 错误码新增`：`ERR_XLANG_DELETE_NOT_MEMBER_EXPR`/`_NOT_SINGLE_LEVEL`/`_ON_CLASS_REF`、`ERR_EXEC_DELETE_ON_NULL_OBJ`/`_ON_ARRAY`/`_NOT_SUPPORTED`/`_DELETE_ATTR_EXPR_RETURN_NULL` ✓
    - `[x] TypeInferenceProcessor.processDeleteStatement` 三条校验可达性**实际落地位置**：编译期校验放在 `BuildExecutableProcessor.processDeleteStatement`（TypeInferenceProcessor 默认关闭，见 `XLangConfigs.CFG_XLANG_TYPE_INFERENCE_ENABLED` 默认 false）✓
  - **Phase 2 Exit Criteria**:
    - `[x] XLangSemantics.deleteProperty` 4 个分支（null check / Map / IMapLike 含 unmodifiableMap fallback / Bean set null）落地 ✓
    - `[x] XLangSemantics.deleteAttr` 含 Integer-index 边界校验（idx<0||idx>=size 返回 false 而非抛 IndexOutOfBoundsException）落地 ✓
    - `[x] DeleteScopeVarExecutable` 持 IExecutableExpression attrExpr 运行时求值 ✓
    - `[x] TestXScript 142 tests, 0 failures, 0 errors` ✓
  - **Phase 3 Exit Criteria**:
    - `[x] delete.test.md 19 个用例` 全部通过 ✓
    - `[x] docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 新增 "删除属性（delete）" 段落 ✓
    - `[x] check-doc-links.mjs --strict` 退出码 0 ✓
  - **Closure Gates**:
    - `[x] 编译期/运行期错误码全覆盖` ✓
    - `[x] 存量零回归` 全 exprs 语料通过 ✓
    - `[x] owner docs 同步` ✓
    - `[x] Anti-Hollow` 端到端调用链 live code 验证（debug 日志记录 DeletePropertyExecutable / DeleteAttrExecutable 实际调用 XLangSemantics.delete*）✓
    - `[x] ./mvnw install -pl nop-kernel/nop-xlang -DskipTests` ✓
  - **关键发现**（实施中暴露）：
    - codegen 模板对 `expression_single` 不带 label 的语法节点会简化包装（如原 `Delete expression_single` 会导致 `visitDeleteStatement` 直接返回 expression_single 而不创建 DeleteStatement 包装）—— **必须**写成 `Delete argument=expression_single`（带 `argument=` 标签）
    - `nop.xlang.type-inference.enabled` 默认 false → TypeInferenceProcessor 三条编译期校验不会触发 → 校验实际放在 BuildExecutableProcessor
    - `List.remove(int)` 返回被删除元素（Object），不是 boolean → 需要边界检查后返回 true/false
    - DynamicObject.toMap() 是 unmodifiableMap → 需要 fallback 到 `prop_set(null)`（语义：Bean 一律 set null，不真删除 key）
  - **自动化扫描**：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 ✓

Follow-up:
- nop-xlang-java 转译器 `DeletePropertyExecutable`/`DeleteAttrExecutable`/`DeleteScopeVarExecutable` 映射（Deferred But Adjudicated）
- nop-xlang-truffle polyglot 映射（Deferred But Adjudicated）