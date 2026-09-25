# Eclipse JDT 支持的 Java 重构全景（源码实测）与 Nop 实现路径

> Status: open
> Date: 2026-09-25
> Scope: Eclipse JDT 重构能力清单（`org.eclipse.jdt.internal.corext.refactoring` 源码逐类枚举 + jdt.ls LSP 暴露面）、支撑这些重构的使能结构、以及映射到 Nop 存量资产的实现路径分级
> Conclusion: JDT 共约 **33 种**重构操作，全部建立在三件套上：Java Model + IBinding 类型归因（classpath 级）、SearchEngine 工程级引用搜索、LTK Change/条件检查/Undo 框架。按语义前提强度映射到 Nop：**档 1**（纯语法，nop-lint fix 面即可，约 5 种 + 全部 codemod）、**档 2**（单编译单元内符号解析，JavaParser+SymbolSolver 可达，约 10 种——rename 局部/字段/类型、extract/inline 局部族等，是第一里程碑）、**档 3**（工程级引用搜索+ripple，约 15 种——虚方法 rename、Change Signature、PullUp/PushDown 等，需 nop-code 索引整合）。Nop 不复刻全集；建议自建档 2 窄集 + 并行评估 jdt.ls 进程桥（类比 NodeTscBridge 模式）作为全集快捷路径。
> 增注（2026-09-25，设计裁定后）：refactor 能力的接口形态已裁定为 **AI-first / GraphQL-first，不以 LSP 组织**——见 `ai-dev/design/nop-refactor/00-vision.md` 与 `01-architecture-baseline.md`。本文 §1.7 的 LSP 暴露面仅作 JDT 事实记录，其"对 Nop LSP 规划可借鉴"的表述作废。§3.4 的 jdt.ls 桥候选**已被平台自完备约束否决**（`ai-dev/design/self-contained-design.md`：不引入外部大型系统作为能力源）——M0 spike 的"不达标则改道 jdt.ls 桥"分叉作废，档 3 外部全集不可自建时收敛范围/降档/延期；OpenRewrite 同理仅为下游侧可选的独立外部工具，非平台能力面。另（复杂度预算 + 性价比门裁定，vision §三.9）：本文 §3.3 的 M1-M3 里程碑收敛——**M2 的 extract/inline 局部族降为 out、M3 档 3 全线 out**（AI 直接重写 + verify 可替代，专用机器复杂度超性价比门），预算内保留 P0 codemod 面 + P1 rename（M0 spike 仍有效，作为 P1 rename 符号域范围的裁定输入）。

## Context

- 上一份分析（`2026-09-25d-tree-sitter-refactor-feasibility-and-nop-lint-relationship.md`）定了大方向：语法级 codemod 走 nop-lint fix 面、语义级重构走"refactor-core + 语言适配"、大迁移走 OpenRewrite；其中语义级重构的具体清单和分档留了空白。
- 本文回答：JDT（Java 重构的工业标杆）到底支持哪些重构、每种的语义前提是什么、对应到 Nop 存量资产（nop-treesitter / nop-lint / nop-java-parser / nop-code）各在哪一档可实现、第一步做什么。
- 证据来源：survey 落位的本地源码 `~/sources/refactor/eclipse.jdt.ui`（含 `org.eclipse.jdt.core.manipulation` —— 共享重构核心所在）、`~/sources/eclipse.jdt.ls`（LSP 形态暴露面）；逐类实测枚举，非转述文档。

## 一、JDT 重构清单（源码实测枚举）

### 1.1 Rename 族（`corext/refactoring/rename/`，14 个 processor）

| 重构 | 实现类 | 语义前提 |
|---|---|---|
| 重命名类型 | `RenameTypeProcessor` | 工程级引用搜索；嵌套类型/同名冲突检查；可联动文件名、构造器 |
| 重命名方法 | `RenameMethodProcessor` + `RenameVirtualMethodProcessor` / `RenameNonVirtualMethodProcessor` | 虚方法需 **ripple 查找**（`RippleMethodFinder2`：沿继承链找全部 override/overriders 并一致改名）+ 调用点搜索（含动态分派不可静态判定的保守面） |
| 重命名字段 | `RenameFieldProcessor` | 引用搜索 + getter/setter 联动 |
| 重命名局部变量 | `RenameLocalVariableProcessor` | 单 CU 内 `TempOccurrenceAnalyzer` 作用域分析 |
| 重命名枚举常量 / 类型参数 | `RenameEnumConstProcessor` / `RenameTypeParameterProcessor` | 同上 |
| 重命名包 / 编译单元 / 工程 / 源目录 / JPMS 模块 | `RenamePackageProcessor` / `RenameCompilationUnitProcessor` / `RenameJavaProjectProcessor` / `RenameSourceFolderProcessor` / `RenameModuleProcessor` | 全工程引用重写 + import 更新（`IQualifiedNameUpdating`） |

### 1.2 Extract 族

| 重构 | 实现类 | 语义前提 |
|---|---|---|
| 提取方法 | `code/ExtractMethodRefactoring` | 选区数据流分析（入参/出参/返回值推断 `ParameterData`）、控制流检查（选区含 return/break/continue 的边界）、调用点回填 |
| 提取局部变量 | `code/ExtractTempRefactoring` | 选区内表达式首次出现判定（`TempAssignmentFinder`）、作用域插入点 |
| 提取常量 | `code/ExtractConstantRefactoring` | 编译期常量判定（`ConstantChecks`）、全 CU 引用替换 |
| 提取类 | `structure/ExtractClassRefactoring` | 字段+访问方法迁移为新类，原类持有委托——需要字段引用分析 |
| 提取接口 / 提取超类 | `structure/ExtractInterfaceProcessor` / `ExtractSupertypeProcessor` | 成员签名归集、全工程"类型引用可选泛化为新父类型"（`ExtractInterfaceConstraintsSolver` 类型约束求解） |

### 1.3 Inline 族

| 重构 | 实现类 | 语义前提 |
|---|---|---|
| 内联方法 | `code/InlineMethodRefactoring`（`CallInliner`/`SourceProvider`/`TargetProvider` 三件套） | 最复杂之一：递归检查、this/super 语义、参数多次求值副作用、return 值替换、冲突命名重写 |
| 内联局部变量 | `code/InlineTempRefactoring` | 唯一赋值判定（多赋值拒绝）、初始化表达式副作用 |
| 内联常量 | `code/InlineConstantRefactoring` | 编译期常量判定、跨 CU 引用搜索 |

### 1.4 Move / Reorg 族

| 重构 | 实现类 | 语义前提 |
|---|---|---|
| 移动实例方法 | `structure/MoveInstanceMethodProcessor` | 参数提升为目标对象、this 引用重写、方法分派不变性验证 |
| 移动静态成员 | `structure/MoveStaticMembersProcessor` | 全工程静态引用重写（含 static import） |
| 内部类转顶层 | `structure/MoveInnerToTopRefactoring` | 外部 this 引用合成（外围实例字段注入） |
| 移动/复制/删除类型与成员 | `reorg/`（`IReorgPolicy` 策略族） | 文件系统 + Java Model 双面操作、引用重写 |
| 上移成员到父类 / 下移到子类 | `structure/PullUpRefactoringProcessor` / `PushDownRefactoringProcessor` | 继承链全部子类/兄弟类一致性检查、`ReferenceAnalyzer` 全工程调用点重写、可见性调整（`MemberVisibilityAdjustor`） |

### 1.5 签名 / 类型改造族

| 重构 | 实现类 | 语义前提 |
|---|---|---|
| 更改方法签名 | `structure/ChangeSignatureProcessor`（+ `AbstractSignatureProcessor` 基类） | **JDT 重构里语义前提最重的一个**：参数增删/重排/改类型/改返回值/改可见性/改 throws——全部调用点（含多态）重写、缺省值合成、`delegates/` 可生成 delegate 旧方法保兼容 |
| Record 签名改造 | `structure/ChangeRecordSignatureProcessor` | 同上，record 组件面 |
| 引入参数对象 | `structure/IntroduceParameterObjectProcessor`（`ParameterObjectFactory`） | 参数束成对象、字段访问重写 |
| 泛化声明类型 | `structure/ChangeTypeRefactoring` | **类型约束求解器**（`typeconstraints/`、`typeconstraints2/`）：找声明类型的最近公共可替换超型 |
| 尽可能使用父类型 | `structure/UseSuperTypeProcessor` | 同上，约束求解 |
| 推断泛型类型参数 | `generics/InferTypeArgumentsRefactoring` | 全工程原始类型（raw type）→ 泛型的约束推理（JDT 最重型的重构之一） |

### 1.6 Introduce / Convert 族与其余

| 重构 | 实现类 | 语义前提 |
|---|---|---|
| 引入工厂 | `code/IntroduceFactoryRefactoring` | 构造器全部调用点替换为工厂方法 |
| 引入间接层 | `code/IntroduceIndirectionRefactoring` | 静态中转方法 + 全工程调用点重写 |
| 局部变量提升为字段 | `code/PromoteTempToFieldRefactoring` | 初始化点分析（构造器/声明处）、参数提升变体 |
| 实例方法转静态 | `code/MakeStaticRefactoring` | this 引用改参数、调用点改写 |
| 匿名类转嵌套类 | `code/ConvertAnonymousToNestedRefactoring` | 外部捕获变量合成字段 |
| 类转 record | `code/ConvertToRecordRefactoring` | 字段/访问器语义等价检查 |
| Surround with try/catch(/resources) | `surround/`（`SurroundWithTryCatchRefactoring` UI 侧） | 异常类型分析（`ExceptionAnalyzer`）——选区包裹，最接近"语法级"的一档 |
| delegate 生成 / import 清理 / 可见性调整 | `delegates/DelegateCreator`、`ImportRemover`、`MemberVisibilityAdjustor` | 非独立重构，是 rename/change signature 的横切组件 |

### 1.7 LSP 暴露面（jdt.ls 实测 handlers）

VSCode Java 的重构入口 = 同一 corext 实现 + LSP 协议包装：`RenameHandler`/`PrepareRenameHandler`（textDocument/rename）、`ChangeSignatureHandler`、`ExtractInterfaceHandler`、`MoveHandler`、`GetRefactorEditHandler`（extract method/variable/constant 等 quick assist 统一走"取 WorkspaceEdit"）。**含义：LSP 的 CodeAction/rename 协议面足以承载全部重构，无需自造协议**——这对 Nop 的 LSP 规划（design 03 §2"codeAction 留候选池"）是直接可借鉴的暴露形态。

## 二、JDT 重构的使能结构（为什么 JDT 能做这些）

重构操作本身只是表象，真正的地基是四层：

1. **Java Model + IBinding 类型归因**（org.eclipse.jdt.core）：`IJavaProject/IType/IMethod/IField` 工程模型 + classpath 解析 + 编译器绑定（`IBinding/ITypeBinding/IMethodBinding`）。每个重构拿到的 AST 节点都带完整绑定——方法解析到声明（含继承/重载消歧）、字段解析到定义。**这是 rename/inline/change signature 的正确性根源，也是 tree-sitter 路线补不上的那一块**（OpenRewrite 的对应物是 javac 归因，本项目对应物只能是 JavaParser SymbolSolver 或编译器桥）。
2. **SearchEngine 工程级引用搜索**：`RefactoringSearchEngine` 在工程/classpath 范围内按绑定（非文本）找全部引用——跨文件 rename/inline 的前提。
3. **LTK 框架**（org.eclipse.ltk.core.refactoring，eclipse.platform 仓库）：`Refactoring` 两段式契约（`checkInitialConditions` → 参数交互 → `checkFinalConditions` → `createChange`）、`Change` 树（`CompositeChange`/`TextChange` per-file TextEdit 累积）、条件检查结果（fatal/warning 分级 + 预览）、UndoManager（重构可撤销）、重构脚本（`CreateRefactoringScript`/`ApplyRefactoringScript`——把一次重构记录为参数化脚本重放）、participants 扩展点（`RenameParticipant`/`MoveParticipant` 等，非 Java 资源联动改名）。
4. **横切 util**：`CompilationUnitRewrite`（AST 改动 → TextEdit 累积，保格式）、`ImportRemover`/`ImportRewriteUtil`、`ASTNodeDeleteUtil`、`MemberVisibilityAdjustor`。

## 三、Nop 实现路径

### 3.1 使能结构对应表（JDT 概念 → Nop 存量资产 → 差距）

| JDT 使能结构 | Nop 存量对应 | 差距（要做的事） |
|---|---|---|
| DOM AST + IBinding | JavaParser + SymbolSolver（`nop-utils/nop-java-parser`；nop-lint-java L2+ 已在用：ScopeAnalyzer/Dataflow 等 SPI） | **classpath 装配**：SymbolSolver 需要依赖 jar 的类型反射源；需建 Maven 模型 → `ParserConfiguration`/`TypeDeclarationConfiguration` 装配器（OpenRewrite 用构建插件解决同一问题）；classpath 不全时的浅层降级口径 |
| SearchEngine 引用搜索 | nop-code 符号表/依赖图（只读查询 API 已有；Java 走 JavaParser、TS 走 nop-treesitter） | 裁定"按绑定找全部引用"的落点：操作器内嵌轻量索引 vs 复用 nop-code 服务（上轮 open question，rename 立项前必须裁定）；nop-code 当前无"引用搜索"专用面 |
| LTK Change/TextEdit | nop-lint fix 管线：`Fix(range,replacement)` + `Fixer.merge` + `FixApplier`（原子写/multipass/重解析守卫/回滚）+ `UnifiedDiff` | FixApplier 是**诊断驱动**，重构需**编辑计划驱动**（per-file 编辑列表先于诊断存在）——需抽出"对给定文件序列应用编辑计划"的通用入口；全仓事务性报告（多文件原子提交面）不存在 |
| checkInitial/checkFinal 条件检查 | 无直接对应；RuleTester 的 valid/invalid fixture 范式可迁移为重构的 before/after 测试 | 新建**前置校验器**：名字冲突/引用丢失/可见性检查（JavaParser 层）+ 重构后编译验证（调 javac/ECJ 或 `mvn test-compile`）+ after 断言 |
| 预览/dry-run | `--fix-dry-run` + UnifiedDiff 已有 | 直接近似复用 |
| participants 扩展 | Nop SPI（`LanguageRegistry` 的 ServiceLoader 形态）/ IoC bean | 按需，第一里程碑不做 |
| Undo/重构脚本 | 无 | 第一里程碑不做（git 即回滚面） |

### 3.2 可实现性分档（JDT 清单 → Nop 三档）

**档 1：纯语法级——nop-lint fix 面直接承载（R0/R1，无新模块）**

- `Surround with try/catch(/resources)`（异常类型可先做 checked 语法近似）、`Extract Constant`（字面量 + 单 CU 文本替换）在放宽语义检查后语法级可做。
- 更大的意义是 codemod 全集（pattern→template）与 JDT 的交集天然是档 1；JDT 不做的批量改写正是 nop-lint R1 的主场。
- 前提：R0 等 plan 11（TemplateFix NPE）、R1 需 design 裁定 transform 规则形态。

**档 2：单编译单元 / 可达符号域内的语义重构——JavaParser+SymbolSolver 承载（R2 第一里程碑）**

| 重构 | 依赖 |
|---|---|
| Rename 局部变量 / 枚举常量 / 类型参数 | ScopeAnalyzer（已有，审计确认语义正确） |
| Rename 字段 / 非虚方法 / 类型（同模块） | SymbolSolver 引用解析 + 单/少文件编辑编排 |
| Inline 局部变量（唯一赋值判定）/ 提取常量 | DefUseChain（plan 11 修正后） |
| Extract 方法 / 局部变量（限定无控制流边界的选区起步） | JavaParser 选区数据流——**自建面**，JDT 的 ExtractMethod 约 3k 行，先做保守子集（纯表达式语句选区） |
| Promote Temp to Field / Make Static / Convert Anonymous to Nested | 单 CU 结构改写 |
| Introduce Factory | 构造器调用点解析 |

**档 3：工程级引用搜索 + 继承链 ripple——需 nop-code 索引整合 + classpath（远期，逐个立项）**

Rename 虚方法（ripple）、Rename 包/编译单元、Change Signature（含 record）、Introduce Parameter Object、Move 实例方法/静态成员/内部类转顶层、PullUp/PushDown、Extract 接口/超类/类、Generalize Declared Type、Use Supertype、Infer Type Arguments、Introduce Indirection、Rename 工程级联动。共同前提：按绑定（非文本）的全工程引用搜索 + classpath 级类型归因。**Infer Type Arguments / Change Type 建议永不自建**（类型约束求解器是 JDT 十余年积累，`typeconstraints/` 两包自足性极强，投入产出比在所有清单里最低）。

### 3.3 建议路线（细化上轮 R2 为三里程碑）

- **M0（spike，先行）**：classpath 装配验证——用 nop-entropy 自身（Maven 多模块）验证 SymbolSolver 跨模块类型解析率，产出"精确档覆盖率"实测数字；同时裁定引用搜索落点（内嵌 vs nop-code）。**这是所有档 2/3 估算成立的前提，不通过则档 3 全线改道 jdt.ls 桥。**
- **M1**：Rename 阶梯（局部变量 → 字段 → 非虚方法 → 类型），复用 FixApplier 抽出的编辑计划入口 + 前置校验器 + dry-run；测试范式 = before/after fixture（对齐 RuleTester 心智）+ 重构后编译验证。
- **M2**：Extract/Inline 局部族（保守子集）+ Promote/MakeStatic 等单 CU 结构改写。
- **M3**：工程级（档 3）逐操作立项，前置 M0 结论。

横切纪律（继承上轮）：每里程碑独立 design/plan；不与 plan 07-14 在途工作抢文件（M1 依赖 plan 11 的 DefUseChain 修正与 plan 12 的语义层收敛）。

### 3.4 备选路径：借 jdt.ls 进程桥而不是自建全集

上轮已证明"TS 语义层走 NodeTscBridge 进程桥"是本项目已验证的模式——同一模式可套 jdt.ls：

- **形态**：`nop-refactor` 操作 → jdt.ls LSP 请求（rename/changeSignature/GetRefactorEdit）→ `WorkspaceEdit` 回来 → Nop 侧用既有编辑计划入口落盘。EPL-2.0、进程隔离，无许可顾虑。
- **优势**：直接获得全部 33 种重构的工业实现，自建成本几乎为零。
- **代价与约束**：jdt.ls 要求完整 Eclipse 工程模型（.project/.classpath 或 Maven 导入，启动时全量编译 build）、部署体积大（Equinox/OSGi）、首次 build 延迟、绑定 jdt.ls 版本演进；适合**服务端/工具链常驻**场景，不适合轻量 CLI 按需调用。
- **裁定建议**：自建档 2 窄集（高频、低语义风险、可控）为确定性路线；档 3 在 M0 spike 后二选一——SymbolSolver 覆盖率达标则自建逐操作推进，不达标则 jdt.ls 桥承接档 3、自建止步档 2。OpenRewrite（批量迁移）与两者正交互补。

### 3.5 被否决的方向

- **按语言切 `nop-java-refactor` 单模块**：编排层（两遍式/编辑计划/校验/报告）语言无关，按语言切会复制——对齐 nop-lint/nop-code 既有 `core + lang 适配` 拓扑（上轮已裁定，本文维持）。
- **复刻 LTK 全框架（Undo/脚本/participants）**：git 即回滚面，脚本重放场景由 OpenRewrite recipe 承接，第一版全部不做。
- **自建类型约束求解器（ChangeType/InferTypeArguments/UseSupertype）**：见 3.2 档 3 结论。
- **intellij-community 代码复用**：许可 2026-06 起 JetBrains Open-Source Build Terms v1.3（survey 已标注须重新评估兼容性），只借鉴设计。

## Open Questions

- [ ] M0 spike：JavaParser SymbolSolver 在 Maven 多模块工程（含注解处理器生成类、Lombok 类源）上的类型解析率——决定档 3 自建 or jdt.ls 桥
- [ ] 引用搜索落点：操作器内嵌轻量索引 vs 复用 nop-code 查询面（影响 nop-code 定位演进）
- [ ] 编辑计划入口从 FixApplier 抽出的接口形态（per-file TextEdit 列表 → 原子写/守卫复用）
- [ ] Extract Method 保守子集的选区边界定义（纯表达式 vs 含控制流）

## References

- 本地源码实测：`~/sources/refactor/eclipse.jdt.ui/org.eclipse.jdt.core.manipulation/core extension/org/eclipse/jdt/internal/corext/refactoring/`（rename 14 processor、code/structure/generics/surround/delegates/typeconstraints 各包）、`org.eclipse.jdt.core.manipulation/refactoring/org/eclipse/jdt/core/refactoring/`（公共 descriptors/participants API）、`~/sources/eclipse.jdt.ls/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/`（RenameHandler/ChangeSignatureHandler/MoveHandler/GetRefactorEditHandler）
- 相邻分析：`2026-09-25d-tree-sitter-refactor-feasibility-and-nop-lint-relationship.md`（大方向与 R0-R3）、`2026-09-25-java-refactor-tools-survey.md`（工具落位与许可）、`2026-09-25-nop-lint-quality-optimization-deep-audit.md`（plan 07-14 在途约束）
- 设计文档：`ai-dev/design/nop-lint/03-execution-engine.md`（fix 管线/LSP 现状）、`00-overview.md`（架构分层）
- LTK 框架：org.eclipse.ltk.core.refactoring（eclipse.platform 仓库，本地未落位——survey 已标注）
