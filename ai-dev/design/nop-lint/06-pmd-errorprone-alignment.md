# Nop Lint — PMD 与 ErrorProne 能力对标

> 日期: 2026-09-19（修订 2026-09-20）· 状态: 设计草案（索引见 [00-nop-lint-design.md](./00-nop-lint-design.md)）
> Phase 归属以 `08-migration.md` 为唯一权威时间线；本文的类型推导层级 L1–L4 见 §4.6。

## 1. PMD 规则体系

PMD 将规则分为 8 大类，共 400+ 条 Java 规则。

### 1.1 Best Practices（最佳实践，60+ 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **AvoidPrintStackTrace** | 禁止 `e.printStackTrace()`，应使用 logger | Pattern: `$E.printStackTrace()`（任意表达式语句位置） |
| **SystemPrintln** | 禁止 `System.out/err.print` 调试代码 | Pattern: `System.out.println($$$)` |
| **UnusedPrivateField** | 未使用的私有字段 | 数据流分析 + 引用计数 |
| **UnusedPrivateMethod** | 未使用的私有方法 | 数据流分析 |
| **UnusedLocalVariable** | 未使用的局部变量 | 数据流分析 |
| **UnusedAssignment** | 赋值后未使用的变量 | 数据流分析 |
| **UseTryWithResources** | 应使用 try-with-resources | Pattern + xscript |
| **ForLoopCanBeForeach** | 可简化为 foreach 的 for 循环 | Pattern: `for (int $I = 0; $I < $$$; $$$)` |
| **GuardLogStatement** | 日志前应检查 `isXxxEnabled()` | Pattern: `LOG.xxx($$$)` |
| **LooseCoupling** | 使用接口类型而非实现类 | Pattern: `new HashSet()`, `new ArrayList()` |
| **ReplaceHashtableWithMap** | 用 Map 替代 Hashtable | Pattern: `new Hashtable()` |
| **ReplaceVectorWithList** | 用 List 替代 Vector | Pattern: `new Vector()` |
| **AvoidReassigningParameters** | 禁止重新赋值方法参数 | Pattern: `$PARAM = $$$` |
| **AvoidReassigningLoopVariables** | 禁止修改循环变量 | Pattern: `$I = $$$` |
| **DoubleBraceInitialization** | 双括号初始化反模式 | Pattern: `new $T() {{ $$$ }}` |
| **EnumComparison** | 枚举用 `==` 而非 `equals()` | Pattern: `$ENUM.equals($$$)` |
| **EqualsNull** | 用 `== null` 而非 `equals(null)` | Pattern: `$X.equals(null)` |
| **LiteralsFirstInComparisons** | 字面量放 `equals()` 前面 | Pattern: `$X.equals("literal")` |
| **OneDeclarationPerLine** | 每行一个变量声明 | Pattern: `int $A, $B` |
| **MissingOverride** | 缺少 `@Override` 注解 | Pattern: 无 `@Override` 的重写方法 |
| **CheckResultSet** | 检查 ResultSet 导航方法返回值 | Pattern: `rs.next()` 无 `if` |

### 1.2 Code Style（代码风格，80+ 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **ClassNamingConventions** | 类命名规范（可配置） | Pattern + xscript |
| **MethodNamingConventions** | 方法命名规范（可配置） | Pattern + xscript |
| **FieldNamingConventions** | 字段命名规范（可配置） | Pattern + xscript |
| **LocalVariableNamingConventions** | 局部变量命名规范 | Pattern + xscript |
| **FormalParameterNamingConventions** | 参数命名规范 | Pattern + xscript |
| **TypeParameterNamingConventions** | 泛型参数命名规范 | Pattern + xscript |
| **AtLeastOneConstructor** | 非静态类至少一个构造器 | Pattern: 类无构造器 |
| **BooleanGetMethodName** | boolean 方法命名 | Pattern: `boolean get$Method()` |
| **LongVariable** | 变量名过长 | Pattern + xscript |
| **ShortVariable** | 变量名过短 | Pattern + xscript |
| **ShortClassName** | 类名过短 | Pattern + xscript |
| **ShortMethodName** | 方法名过短 | Pattern + xscript |
| **ControlStatementBraces** | 控制语句必须加花括号 | Pattern: `if ($$$) stmt;` |
| **EmptyControlStatement** | 空控制语句体 | Pattern: `if ($$$) { }` |
| **UnnecessaryBlock** | 不必要的代码块 | Pattern: 无新作用域的 `{}` |
| **UnnecessaryCast** | 不必要的类型转换 | Pattern: `(Type) $EXPR` |
| **UnnecessaryImport** | 不必要的 import | 数据流分析 |
| **UnnecessaryModifier** | 不必要的修饰符 | Pattern: interface 中的 `public` |
| **UseDiamondOperator** | 使用 diamond 操作符 | Pattern: `new ArrayList<String>()` |
| **UselessParentheses** | 无用的括号 | Pattern: `($EXPR)` |
| **ModifierOrder** | 修饰符顺序 | Pattern + xscript |
| **OnlyOneReturn** | 方法只有一个 return | Pattern: 多个 `return` |
| **PrematureDeclaration** | 过早声明变量 | 数据流分析 |
| **FieldDeclarationsShouldBeAtStartOfClass** | 字段声明应在类开头 | Pattern: 字段在方法后 |

### 1.3 Design（设计问题，50+ 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **CognitiveComplexity** | 认知复杂度超阈值 | MetricsEvaluator：SonarSource 增量表（嵌套增量+流中断线性增量），Phase 3 |
| **CyclomaticComplexity** | 圈复杂度超阈值 | MetricsEvaluator：决策点计数（if/for/while/case/catch/&&/||/?:）+1，Phase 3 |
| **NPathComplexity** | NPath 复杂度超阈值 | MetricsEvaluator：各决策结构路径数乘积，Phase 3 |
| **TooManyMethods** | 方法数超阈值 | Pattern: 类方法计数 |
| **TooManyFields** | 字段数超阈值 | Pattern: 类字段计数 |
| **ExcessiveParameterList** | 参数过多 | Pattern: 方法参数计数 |
| **ExcessivePublicCount** | public 成员过多 | Pattern: 类 public 成员计数 |
| **GodClass** | God Class 检测 | MetricsEvaluator 多指标组合（方法数/耦合/内聚），Phase 3 |
| **DataClass** | 数据类检测 | Pattern: 仅 getter/setter 的类 |
| **LawOfDemeter** | 迪米特法则违反 | Pattern: `$A.getB().getC()` |
| **CollapsibleIfStatements** | 可合并的 if 语句 | Pattern: `if ($$$) { if ($$$) { $$$ } }` |
| **AvoidDeeplyNestedIfStmts** | 过深嵌套 if | Pattern: 深度 > N 的嵌套 |
| **SimplifyBooleanExpressions** | 简化布尔表达式 | Pattern: `$X == true` |
| **SimplifyBooleanReturns** | 简化布尔返回 | Pattern: `if ($X) return true; else return false;` |
| **ExcessiveImports** | import 过多 | 数据流分析 |
| **CouplingBetweenObjects** | 对象间耦合度 | MetricsEvaluator 依赖计数，Phase 3 |
| **MutableStaticState** | 可变静态状态 | Pattern: `static $T $FIELD` |
| **ImmutableField** | 不可变字段 | 数据流分析 |
| **SingularField** | 可局部化的字段 | 数据流分析 |
| **ClassWithOnlyPrivateConstructorsShouldBeFinal** | 私有构造器类应为 final | Pattern: 类约束 |
| **DoNotExtendJavaLangError** | 不要继承 Error | Pattern: `extends $ERROR` |
| **AvoidThrowingNullPointerException** | 不要手动抛 NPE | Pattern: `throw new NullPointerException()` |
| **AvoidThrowingRawExceptionTypes** | 不要抛原始异常类型 | Pattern: `throw new RuntimeException()` |
| **SignatureDeclareThrowsException** | 方法签名不要声明抛 Exception | Pattern: `throws Exception` |
| **ExceptionAsFlowControl** | 异常不应用作流程控制 | Pattern + xscript |
| **UseObjectForClearerAPI** | API 应使用对象参数 | Pattern: 参数过多 |
| **SwitchDensity** | switch 语句密度过高 | MetricsEvaluator case/语句比，Phase 3 |

### 1.4 Documentation（文档，6 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **CommentContent** | 注释内容规范 | Pattern + xscript |
| **CommentRequired** | 是否需要 Javadoc | Pattern: 类/方法无注释 |
| **CommentSize** | 注释长度限制 | Pattern + xscript |
| **DanglingJavadoc** | 悬挂 Javadoc | Pattern + xscript |
| **UncommentedEmptyConstructor** | 空构造器无注释 | Pattern: `public $T() { }` |
| **UncommentedEmptyMethodBody** | 空方法体无注释 | Pattern: `void $M() { }` |

### 1.5 Error Prone（错误检测，50+ 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **EmptyCatchBlock** | 空 catch 块 | Pattern: `catch ($E) { }` |
| **CloseResource** | 资源未关闭 | Pattern + xscript |
| **EmptyFinallyBlock** | 空 finally 块 | Pattern: `finally { }` |
| **EqualsHashCode** | equals/hashCode 不一致 | 数据流分析 |
| **ConstructorCallsOverridableMethod** | 构造器调用可重写方法 | 数据流分析 |
| **AvoidCatchingGenericException** | 不要捕获通用异常 | Pattern: `catch (Exception $E)` |
| **AvoidInstanceofChecksInCatchClause** | catch 中不要 instanceof | Pattern: `catch ($E) { if ($X instanceof $$$) }` |
| **AssignmentInOperand** | 操作数中赋值 | Pattern: `if ($X = $$$)` |
| **AvoidDecimalLiteralsInBigDecimalConstructor** | 不要用 double 构造 BigDecimal | Pattern: `new BigDecimal(0.1)` |
| **JUnit4TestShouldUseTestAnnotation** | JUnit 4 测试方法无 @Test | Pattern: 无注解的 test 方法 |
| **AvoidDuplicateLiterals** | 避免重复字面量 | 数据流分析 |
| **JUnitAssertionsShouldIncludeMessage** | 断言应包含消息 | Pattern: `assertEquals($A, $B)` |
| **TestClassWithoutTestCases** | 测试类无测试方法 | Pattern: 类约束 |
| **MoreThanOneLogger** | 多个 logger 实例 | Pattern: 类字段多 Logger |
| **DoNotCall** | 不应调用的方法 | Pattern + xscript |
| **Finalize** | 不要重写 finalize | Pattern: `void finalize()` |
| **FallThrough** | switch case 穿透 | Pattern + xscript |

### 1.6 Multithreading（多线程，15+ 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **AvoidSynchronizedAtMethodLevel** | 不要在方法级 synchronized | Pattern: `synchronized void $M()` |
| **AvoidSynchronizedStatement** | 不要使用 synchronized 语句 | Pattern: `synchronized ($$$) { $$$ }` |
| **UnsynchronizedStaticField** | 非同步静态字段 | Pattern + xscript |
| **DoNotUseThreads** | 不要直接使用 Thread | Pattern: `new Thread()` |
| **AvoidThreadGroup** | 不要使用 ThreadGroup | Pattern: `new ThreadGroup()` |
| **UseConcurrentHashMap** | 用 ConcurrentHashMap 替代 | Pattern: `Collections.synchronizedMap()` |

### 1.7 Performance（性能，15+ 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **UseStringBufferForStringAppends** | 用 StringBuilder 替代字符串拼接 | Pattern: `$S += $S` |
| **AvoidInstantiatingObjectsInLoops** | 循环内不要创建对象 | Pattern: `for ($$$) { new $T() }` |
| **UseCollectionIsEmpty** | 用 isEmpty() 替代 size()==0 | Pattern: `$COL.size() == 0` |
| **SimplifyStartsWith** | 简化 startsWith | Pattern: `$S.indexOf($X) == 0` |
| **StringInstantiation** | 不要 new String() | Pattern: `new String($$$)` |
| **BigIntegerInstantiation** | 用常量替代 new BigInteger | Pattern: `new BigInteger("0")` |
| **OptimizableToArrayCall** | 优化 toArray 调用 | Pattern: `$COL.toArray(new $T[0])` |
| **UseEnumCollections** | 用 EnumSet/EnumMap | Pattern: `new HashSet()` + 枚举键 |

### 1.8 Security（安全，10+ 条）

| 规则 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **HardCodedCryptoKey** | 硬编码加密密钥 | Pattern: `"key"` in crypto context |
| **InsecureCryptoIv** | 不安全的加密 IV | Pattern: 硬编码 IV |
| **ArrayIsStoredDirectly** | 数组直接存储（封装泄露） | Pattern: `this.$ARR = $ARR` |
| **MethodReturnsInternalArray** | 返回内部数组 | Pattern: `return this.$ARR` |
| **DoNotHardcodeCryptographicKeys** | 硬编码密钥 | Pattern + xscript |
| **DiscouragedOpticalAlliance** | 不推荐的反射使用 | Pattern: `setAccessible(true)` |

## 2. ErrorProne 规则体系

ErrorProne 在编译时检测，分为 5 大类，共 400+ 条检查。

### 2.1 Correctness（正确性，100+ 条）

| 检查 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **AlwaysThrows** | 必定抛异常的调用 | 数据流分析 |
| **ArrayEquals** | 用 `==` 比较数组 | Pattern: `$ARR1 == $ARR2` |
| **ArrayHashCode** | 数组的 hashCode | Pattern: `$ARR.hashCode()` |
| **ArrayToString** | 数组的 toString | Pattern: `$ARR.toString()` |
| **BoxedPrimitiveEquality** | 包装类型引用比较 | Pattern: `$X == $Y` (Integer) |
| **CollectionIncompatibleType** | 集合类型不兼容 | Pattern + 类型分析 |
| **DeadException** | 创建但未抛出的异常 | Pattern: `new $E($$$)` 无 `throw` |
| **DeadThread** | 创建但未启动的线程 | Pattern: `new Thread()` 无 `start()` |
| **EqualsHashCode** | equals/hashCode 不一致 | 数据流分析 |
| **EqualsNull** | equals(null) | Pattern: `$X.equals(null)` |
| **FormatString** | 格式化字符串错误 | Pattern + xscript |
| **IdentityBinaryExpression** | 相同操作数的二元表达式 | Pattern: `$X + $X` |
| **InfiniteRecursion** | 无限递归 | 数据流分析 |
| **LockOnBoxedPrimitive** | 锁包装类型 | Pattern: `synchronized ($INT)` |
| **LoopConditionChecker** | 循环条件未修改 | 数据流分析 |
| **MissingSuperCall** | 缺少 super 调用 | Pattern + xscript |
| **MissingTestCall** | 缺少测试调用 | Pattern + xscript |
| **OptionalEquality** | Optional 引用比较 | Pattern: `$OPT1 == $OPT2` |
| **RandomModInteger** | 随机数取模错误 | Pattern: `$RAND.nextInt() % $N` |
| **SelfAssignment** | 自赋值 | Pattern: `$X = $X` |
| **SelfComparison** | 自比较 | Pattern: `$X.compareTo($X)` |
| **SelfEquals** | 自相等测试 | Pattern: `$X.equals($X)` |
| **SizeGreaterThanOrEqualsZero** | size >= 0 恒真 | Pattern: `$COL.size() >= 0` |
| **StreamToString** | Stream 的 toString | Pattern: `$STREAM.toString()` |
| **ThrowNull** | 抛出 null | Pattern: `throw null` |
| **TryFailThrowable** | catch 中 catch Throwable | Pattern: `catch (Throwable $$$)` |
| **XorPower** | XOR 误用为幂运算 | Pattern: `$X ^ $Y` |

### 2.2 Performance（性能，30+ 条）

| 检查 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **BoxedPrimitiveConstructor** | 使用包装类构造函数 | Pattern: `new Integer($$$)` |
| **ByteBufferBackingArray** | ByteBuffer 数组访问 | Pattern + xscript |
| **DefaultCharset** | 隐式使用默认字符集 | Pattern: `new String($$$)` |
| **DoubleBraceInitialization** | 双括号初始化 | Pattern: `new $T() {{ $$$ }}` |
| **FallThrough** | switch case 穿透 | Pattern + xscript |
| **FloatingPointLiteralPrecision** | 浮点精度问题 | Pattern + xscript |
| **HashCodeAndEqualsMixed** | 混用 hashCode 和 equals | Pattern + xscript |
| **InlineMeSuggester** | API 内联建议 | Pattern + xscript |
| **IterablePath** | 路径遍历优化 | Pattern + xscript |
| **MapMultiplicity** | Map 操作优化 | Pattern: `$MAP.keySet().stream()` |
| **MissingSummary** | 缺少 Javadoc 摘要 | Pattern + xscript |
| **StreamToString** | Stream 的 toString | Pattern: `$STREAM.toString()` |
| **StringSplit** | String.split 性能 | Pattern: `$STR.split($REGEX)` |
| **UnnecessaryStringBuilder** | 不必要的 StringBuilder | Pattern: `new StringBuilder()` |

### 2.3 Style（风格，50+ 条）

| 检查 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **AlmostJavadoc** | 几乎是 Javadoc 的注释 | Pattern + xscript |
| **BadImport** | 不良 import | Pattern + xscript |
| **ClassCanBeStatic** | 内部类可为 static | Pattern + xscript |
| **FallThrough** | switch case 穿透 | Pattern + xscript |
| **HidingField** | 字段遮蔽 | Pattern + xscript |
| **InlineTrivialConstant** | 内联简单常量 | Pattern + xscript |
| **LongVariable** | 变量名过长 | Pattern + xscript |
| **MissingDefault** | 缺少 default case | Pattern + xscript |
| **NonCanonicalStaticImport** | 非规范静态导入 | Pattern + xscript |
| **RedundantOverride** | 冗余重写 | Pattern + xscript |
| **RedundantStringConversion** | 冗余字符串转换 | Pattern + xscript |
| **ShortVariable** | 变量名过短 | Pattern + xscript |
| **UnnecessaryParentheses** | 不必要的括号 | Pattern + xscript |
| **VarTypeName** | var 类型名 | Pattern + xscript |

### 2.4 Security（安全，20+ 条）

| 检查 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **BanJNDI** | JNDI 反序列化风险 | Pattern: `InitialContext().lookup($$$)` |
| **FragmentInjection** | Fragment 注入漏洞 | Pattern + xscript |
| **HardCodedKey** | 硬编码密钥 | Pattern + xscript |
| **InsecureCryptoIV** | 不安全加密 IV | Pattern + xscript |
| **InsecureWebview** | 不安全的 WebView | Pattern + xscript |
| **MissingPermission** | 缺少权限声明 | Pattern + xscript |
| **MutableConstantField** | 可变常量字段 | Pattern: `static final $T $FIELD` |
| **ProtoFieldNumberComparisons** | Proto 字段号比较 | Pattern + xscript |
| **PrivateSecurityContractProtoAccess** | 私有安全合约访问 | Pattern + xscript |
| **RequireQualifier** | 缺少限定符注解 | Pattern + xscript |

### 2.5 Javadoc（文档，15+ 条）

| 检查 | 检测内容 | Nop Lint 实现 |
|------|---------|--------------|
| **EmptyBlockTag** | 空块标签 | Pattern + xscript |
| **InvalidBlockTag** | 无效块标签 | Pattern + xscript |
| **InvalidInlineTag** | 无效内联标签 | Pattern + xscript |
| **InvalidLink** | 无效链接 | Pattern + xscript |
| **InvalidParam** | 无效 @param | Pattern + xscript |
| **InvalidThrows** | 无效 @throws | Pattern + xscript |
| **MissingJavadoc** | 缺少 Javadoc | Pattern + xscript |
| **MissingSummary** | 缺少摘要 | Pattern + xscript |
| **UnescapedEntity** | 未转义的实体 | Pattern + xscript |

## 3. Nop Lint 能力覆盖对照

### 3.1 Pattern + 约束可覆盖的能力

> 状态说明：纯 pattern Phase 1 可用；约束/关系规则/复合规则（除单层 any）Phase 2 可用（见 08-migration.md）。

| PMD/ErrorProne 规则类别 | Nop Lint 覆盖方式 | 可用 Phase |
|------------------------|------------------|-----------|
| 简单模式匹配（空块、重复字面量、命名规范） | Pattern + 单层 any | Phase 1 |
| 关系规则（方法内、类内、包内） | inside/has/follows | Phase 2 |
| 组合规则（all/not/matches 递归） | 复合规则 | Phase 2 |
| 修复建议 | fix template | Phase 2 |

### 3.2 需要扩展的能力

| PMD/ErrorProne 能力 | Nop Lint 实现方案 |
|---------------------|------------------|
| **数据流分析**（未使用变量、自赋值、空异常） | Phase 3: DataFlowAnalyzer（以 08-migration 为准，不在 Phase 2） |
| **类型分析**（类型不兼容、包装类型比较） | Phase 2: 复用 nop-java-parser symbol solver（L2，见 §5.2） |
| **度量指标**（复杂度、字段数、方法数） | Phase 3: MetricsEvaluator（决策点计数，非 AST 深度） |
| **JUnit 测试检测** | Phase 1: xscript + 注解模式 |
| **线程安全分析** | Phase 3: ConcurrentAnalyzer |
| **资源泄漏分析** | Phase 3: ResourceTracker（依赖数据流） |
| **常量传播** | Phase 3: ConstantPropagation |

### 3.3 Nop 特有扩展

| Nop 场景 | PMD/ErrorProne 无法覆盖 | Nop Lint 方案 |
|---------|----------------------|--------------|
| BizModel 注解规则 | 无 | xscript + 类型解析 |
| ORM 模型验证 | 无 | XML Pattern |
| CrudBizModel API 使用 | 无 | xscript + 类型分析 |
| XPL 模板转义 | 无 | xscript + AST 查询 |
| 查询安全检查 | 无 | xscript + 数据流 |

## 4. 类型推导集成需求分析

很多 PMD/ErrorProne 规则**必须依赖类型推导**才能正确检测。以下是按类型推导需求分级的规则清单。

### 4.1 无需类型推导（纯 Pattern 匹配）

| 规则 | 检测方式 | 示例 |
|------|---------|------|
| EmptyCatchBlock | Pattern: `catch ($E) { }` | 纯结构匹配 |
| SystemPrintln | Pattern: `System.out.println($$$)` | 纯结构匹配 |
| AvoidPrintStackTrace | Pattern: `$E.printStackTrace()` | 纯结构匹配 |
| CyclomaticComplexity | MetricsEvaluator 决策点计数 | 无需类型信息 |
| CognitiveComplexity | MetricsEvaluator SonarSource 增量表 | 无需类型信息 |
| DoubleBraceInitialization | Pattern: `new $T() {{ $$$ }}` | 纯结构匹配 |
| ForLoopCanBeForeach | Pattern: `for (int $I = 0; $I < $$$; $$$)` | 纯结构匹配 |
| ControlStatementBraces | Pattern: `if ($$$) stmt;` | 纯结构匹配 |

### 4.2 需要轻量类型推导（声明类型 + 注解类型）

这类规则只需要知道变量的**声明类型**，不需要完整的类型推导。

| 规则 | 类型需求 | 实现方式 |
|------|---------|---------|
| **LooseCoupling** | 变量声明类型 | Pattern: `HashSet $X = new HashSet()` → 声明类型是 HashSet |
| **ReplaceHashtableWithMap** | 变量声明类型 | Pattern: `Hashtable $X = new Hashtable()` |
| **ReplaceVectorWithList** | 变量声明类型 | Pattern: `Vector $X = new Vector()` |
| **AvoidCatchingGenericException** | 异常类型 | Pattern: `catch (Exception $E)` |
| **UseEnumCollections** | Map 泛型类型 | Pattern: `new HashMap<$ENUM, $$$>()` |
| **DoNotExtendJavaLangError** | 继承类型 | Pattern: `extends Error` |
| **InstantiableUtilityClass** | 类修饰符 + 方法 | Pattern: 类字段分析 |
| **ClassCanBeStatic** | 内部类引用外部类 | 引用分析（L3） |
| **SingularField** | 字段使用范围 | 引用分析（L3） |

**实现方式**（使用 LintNode 门面，见 01-pattern-dsl.md §5）：
```java
// L1 轻量类型推导：从声明节点提取类型
public class LightTypeResolver {
    // 从 variable_declaration 获取类型
    public String resolveDeclType(LintNode varDecl) {
        return varDecl.child("type").text();
    }

    // 从 annotations 获取类型信息
    public String resolveAnnotatedType(LintNode node) {
        // 检查 @Nullable, @NonNull 等
        return extractAnnotationType(node);
    }
}
```

### 4.3 需要完整类型推导（类型解析 + 继承链）

这类规则需要知道**实际类型**（不仅仅是声明类型），需要类型解析和继承链分析。

| 规则 | 类型需求 | 实现方式 |
|------|---------|---------|
| **CollectionIncompatibleType** | 集合泛型类型 + 参数类型 | 需要类型解析 |
| **BoxedPrimitiveEquality** | 变量实际类型 | 需要类型解析 |
| **EqualsHashCode** | 类型的 equals/hashCode 实现 | 需要类型解析 + 方法分析 |
| **ComparableType** | Comparable 实现 | 需要类型解析 |
| **ImmutableField** | 字段是否在构造器后修改 | 需要数据流 + 类型 |
| **LawOfDemeter** | 方法返回类型链 | 需要类型解析 |
| **SimplifiedTernary** | 条件表达式类型 | 需要类型解析 |
| **SimplifyConditional** | instanceof 目标类型 | 需要类型解析 |
| **AvoidFieldNameMatchingMethodName** | 字段/方法类型 | 需要类型解析 |
| **InvalidJavaBean** | Bean 属性类型 | 需要类型解析 |

**实现方式**：
> **注意**：以下为 L2 需求的能力示意。实现上**不自建**此 resolver，而是复用 nop-java-parser + nop-ai-code-analyzer（见 §5.2 决策与 §6）。

```java
// （示意，实际复用 JavaParserFacade —— 见 §5.2）
public class TypeResolver {
    // 类型环境：变量名 → 类型
    private Map<String, TypeInfo> typeEnv;
    
    // 从声明推导
    public TypeInfo resolveFromDeclaration(LintNode node) {
        // int x → int
        // String s → String
        // List<String> list → List<String>
    }
    
    // 从赋值推导
    public TypeInfo resolveFromAssignment(LintNode assign) {
        // x = new ArrayList() → ArrayList
        // x = getString() → String (需要方法返回类型)
    }
    
    // 从方法调用推导
    public TypeInfo resolveFromCall(LintNode call) {
        // list.size() → int
        // map.get(key) → V (需要泛型解析)
    }
    
    // 继承链查询
    public boolean isSubtypeOf(String child, String parent) {
        // ArrayList implements List → true
    }
}
```

### 4.4 需要数据流分析（定义-使用链 + 常量传播）

这类规则需要追踪**变量的值**在程序中的流动。

| 规则 | 数据流需求 | 实现方式 |
|------|----------|---------|
| **UnusedLocalVariable** | 定义-使用链 | DefUseChain |
| **UnusedPrivateField** | 定义-使用链 | DefUseChain |
| **UnusedAssignment** | 定义-使用链 | DefUseChain |
| **DeadException** | 定义-使用链 | DefUseChain |
| **SelfAssignment** | 定义-使用链 | DefUseChain |
| **LoopConditionChecker** | 循环体内修改检测 | 数据流分析 |
| **ConstantOverflow** | 常量传播 | 常量传播算法 |
| **CompileTimeConstant** | 常量传播 | 常量传播算法 |
| **FinalFieldCouldBeStatic** | 常量传播 | 常量传播算法 |
| **MutableStaticState** | 静态字段修改追踪 | 数据流分析 |

**实现方式**（契约级描述，实现见源码）：
- `DataFlowAnalyzer.buildDefUseChain(method)`：遍历 AST 记录每个变量的定义点与使用点，产出定义-使用链
- `DataFlowAnalyzer.propagateConstants(method)`：沿赋值传播常量值（`isConstantExpression` 判定 + 常量求值）

### 4.5 需要语义分析（类型系统 + 方法签名）

这类规则需要深入理解**类型系统**和**方法语义**。

| 规则 | 语义需求 | 实现方式 |
|------|---------|---------|
| **CloseResource** | AutoCloseable 实现检测 | 类型解析 + 接口检测 |
| **UseTryWithResources** | AutoCloseable 实现检测 | 类型解析 + 接口检测 |
| **GuardLogStatement** | Logger API 方法签名 | 方法签名解析 |
| **JUnit 测试规则** | JUnit 注解语义 | 注解解析 |
| **ConstructorCallsOverridableMethod** | 方法可重写性分析 | 类型层次分析 |
| **ExceptionAsFlowControl** | 异常类型分析 | 类型解析 |
| **SignatureDeclareThrowsException** | throws 声明分析 | 方法签名解析 |

**实现方式**（契约级描述）：
- `SemanticAnalyzer.implementsInterface(typeName, interfaceName)`：解析类型层次，检查接口实现
- `SemanticAnalyzer.isOverridable(methodDecl)`：检查 final/static/private 修饰符与类 final 性
- `SemanticAnalyzer.isLoggerMethod(call)`：调用对象类型是否 Logger + 方法名是否 isDebugEnabled 等

### 4.6 类型推导实现分层

```
┌─────────────────────────────────────────────────────────┐
│                    Nop Lint Type System                   │
├─────────────────────────────────────────────────────────┤
│  L1（项目 Phase 1）: 轻量类型推导（声明类型 + 注解）           │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │ DeclTypeResolver │  │ AnnotationParser│               │
│  └────────┬────────┘  └────────┬────────┘               │
│           │                    │                         │
│  L2（项目 Phase 2）: 完整类型推导（symbol solver / tsc）      │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │ JavaParserFacade │  │ tsc bridge (TS) │               │
│  │ (已有模块复用)    │  │                 │               │
│  └────────┬────────┘  └────────┬────────┘               │
│           │                    │                         │
│  L3（项目 Phase 3）: 数据流分析（定义-使用链 + 常量传播）        │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │  DefUseChain     │  │ ConstantProp    │               │
│  └────────┬────────┘  └────────┬────────┘               │
│           │                    │                         │
│  L4（项目 Phase 3）: 语义分析（方法签名 + 类型层次）            │
│  ┌─────────────────┐  ┌─────────────────┐               │
│  │ MethodSignature  │  │ TypeHierarchy   │               │
│  └─────────────────┘  └─────────────────┘               │
└─────────────────────────────────────────────────────────┘
```

> **命名说明**：类型推导层级用 **L1–L4** 编号，避免与项目 Phase 1–4 撞号。L1→Phase 1，L2→Phase 2，L3/L4→Phase 3（映射以 08-migration.md 为准）。

### 4.7 规则与类型推导需求映射

| 类型层级（对应项目 Phase） | 类型推导能力 | 可实现的规则 |
|--------------------------|------------|------------|
| 无（Phase 1） | 无类型推导 | ~40 条（纯 Pattern） |
| **L1**（Phase 1） | 轻量类型推导 | ~30 条（声明类型） |
| **L2**（Phase 2） | 完整类型推导 | ~50 条（类型解析） |
| **L3**（Phase 3） | 数据流分析 | ~40 条（定义-使用链） |
| **L4**（Phase 3） | 语义分析 | ~30 条（方法签名 + 类型层次） |

**总计**: ~190 条规则，其中 **~150 条需要 L1–L4 某层类型推导支持**（另有 ~40 条纯 Pattern 规则无需类型信息）。

## 5. 类型推导实现方案

### 5.1 核心问题

**Tree-sitter 本身不提供类型推导** — 它只提供语法树结构。类型推导必须在语法树之上构建。

现有工具的类型推导方案：

| 工具 | 类型推导方式 | 局限性 |
|------|------------|--------|
| **ast-grep** | **无类型推导** — 纯语法结构匹配 | 无法检测类型相关问题 |
| **Semgrep** | 有限的类型感知（Pro 版） | 需要语言服务器，性能开销大 |
| **typescript-eslint** | **调用 TypeScript 编译器** | 依赖 tsc，需要完整项目配置 |
| **ErrorProne** | **编译时集成** — 使用 javac 类型系统 | 必须在编译过程中运行 |
| **PMD** | 基于 AST 的简单类型解析 | 无法处理复杂泛型、继承链 |

### 5.2 Java 类型推导方案

Java 类型系统相对简单，可以基于 tree-sitter AST 构建**轻量级类型解析器**：

```
┌─────────────────────────────────────────────────────────┐
│                 Java Type Resolver                        │
├─────────────────────────────────────────────────────────┤
│  1. 声明类型提取（tree-sitter AST）                        │
│     └── variable_declaration.type → "String"             │
│     └── method_declaration.return_type → "int"           │
│     └── formal_parameter.type → "List<String>"           │
│                                                          │
│  2. 类型层次解析（Maven 依赖 + 源码）                      │
│     └── extends/implements → 继承链                       │
│     └── 泛型参数解析 → List<String> → String              │
│                                                          │
│  3. 方法签名解析（源码级别）                                │
│     └── 方法返回类型 → 从 AST 提取                         │
│     └── 方法参数类型 → 从 AST 提取                         │
│                                                          │
│  4. 类型兼容性检查                                         │
│     └── 子类型检查 → 继承链遍历                            │
│     └── 泛型兼容性 → 类型参数匹配                          │
└─────────────────────────────────────────────────────────┘
```

**Java 类型推导实现（复用已有模块，不自建完整 resolver）**：

> **L1（Phase 1）— 仅声明类型提取**（纯 AST，无外部依赖）：

```java
// L1: 基于 LintNode 门面的声明类型提取（见 01-pattern-dsl.md §5）
public class DeclTypeResolver {
    public String resolveDeclType(LintNode declNode) {
        LintNode typeNode = declNode.childByField("type");  // 声明生产式的 type 命名字段
        return typeNode != null ? typeNode.text() : null;  // "String", "List<String>"
    }
}
```

> **L1 消费契约（已落地：`io.nop.lint.core.type.DeclTypeResolver`，roadmap item 12）**：单方法 API——输入声明节点（`field_declaration` / `local_variable_declaration` / `method_declaration` / `formal_parameter` 等带 `type` 字段的生产式），输出按书写文本保真的类型字符串（泛型/数组/限定名/通配符不规范化）；非声明节点、无 `type` 字段或 null 入参一律返回 null（显式"无法提取"契约，无猜测）；`var` 返回字面 `"var"`（initializer 推断属 L2）。xscript 引擎（roadmap item 14）与 L1 类规则按此契约直接注入调用，无其他公共方法。

> **L2（Phase 2）— 复用已有 JavaParser 资产**，不自建类型层次数据库：
> - `nop-utils/nop-java-parser`：已依赖 javaparser-core + **javaparser-symbol-solver-core**，`JavaParseTool` 已配置 symbol solver
> - `nop-ai/nop-ai-skills/nop-ai-code-analyzer`：`JavaParserBuilder` 已实现 `CombinedTypeSolver` + `addModuleJars(MavenModule)`（从 Maven 仓库加载依赖 jar）；`MavenProject` 提供 pom 解析、依赖树、`findJavaFileByClassName`

```java
// L2: 基于 nop-ai-code-analyzer 的 JavaParserBuilder 构建项目级 solver
public class JavaSymbolSolverService {
    private final JavaParser javaParser;       // 来自 nop-java-parser / JavaParserBuilder
    private final MavenProject mavenProject;   // 来自 nop-ai-code-analyzer

    public void init(Path moduleRoot) {
        // 复用 JavaParserBuilder: ReflectionTypeSolver(JDK) + JarTypeSolver(依赖 jar) + JavaParserTypeSolver(源码)
        this.javaParser = JavaParserBuilder.build(mavenProject.load(moduleRoot));
    }

    // 类型兼容性检查：直接问 symbol solver，不自己维护继承链
    public boolean isAssignable(LintNode exprNode, String targetFqn) {
        Node jpNode = astMapping.getJavaNode(exprNode);      // 双 AST 映射，见 §6
        if (jpNode instanceof Expression e) {
            ResolvedType t = e.calculateResolvedType();
            return t.isAssignableBy(ResolvedReferenceType.of(targetFqn));
        }
        return false;
    }
}
```

**L1 声明类型提取的 tree-sitter 查询（辅助用途）**：

```scheme
;; 提取字段声明类型
(field_declaration
  type: (type_identifier) @field_type
  declarator: (variable_declarator
    name: (identifier) @field_name))

;; 提取方法返回类型
(method_declaration
  type: (type_identifier) @return_type
  name: (identifier) @method_name)

;; 提取泛型类型
(generic_type
  type_arguments: (type_arguments
    (type_identifier) @type_arg))

;; 提取继承关系
(class_declaration
  name: (identifier) @class_name
  superclass: (superclass
    (type_identifier) @super_type))

;; 提取接口实现
(class_declaration
  name: (identifier) @class_name
  interfaces: (interfaces
    (type_identifier) @interface_name))
```

### 5.3 TypeScript 类型推导方案

TypeScript 类型系统复杂得多，**不应该自己实现类型推导**，而应该**调用 TypeScript 编译器**：

```
┌─────────────────────────────────────────────────────────┐
│                TypeScript Type Integration                │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  方案 A: 调用 tsc API（推荐）                              │
│  ┌─────────────────────────────────────────────────┐     │
│  │  Nop Lint → TypeScript Compiler API → 类型信息   │     │
│  │  - 使用 ts.createProgram() 创建程序              │     │
│  │  - 使用 checker.getTypeAtLocation() 获取类型     │     │
│  │  - 使用 checker.isTypeAssignableTo() 检查兼容性  │     │
│  └─────────────────────────────────────────────────┘     │
│                                                          │
│  方案 B: 调用 tsserver（LSP 模式）                        │
│  ┌─────────────────────────────────────────────────┐     │
│  │  Nop Lint → tsserver Protocol → 类型信息         │     │
│  │  - 通过 JSON-RPC 通信                            │     │
│  │  - 复用 IDE 的类型推导能力                        │     │
│  └─────────────────────────────────────────────────┘     │
│                                                          │
│  方案 C: 混合模式（Phase 1 用 AST，Phase 2 用 tsc）       │
│  ┌─────────────────────────────────────────────────┐     │
│  │  Phase 1: 基于 AST 的轻量类型解析                 │     │
│  │    - 声明类型提取                                │     │
│  │    - 简单类型匹配                                │     │
│  │  Phase 2: 调用 TypeScript 编译器                  │     │
│  │    - 完整类型推导                                │     │
│  │    - 泛型解析                                    │     │
│  │    - 类型兼容性检查                               │     │
│  └─────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────┘
```

**TypeScript 类型推导实现**（契约级描述）：

- `TypeScriptTypeResolver`（方案 A）：经常驻 Node 进程调用 TypeScript 编译器 API——`initProject(tsConfigPath)` 建 program（进程复用 + 缓存，失效策略见 11 §4）、`getTypeAtLocation(file, line, col)` 取节点类型、`isTypeAssignableTo(from, to)` 查兼容性
- `HybridTypeResolver`（方案 C）：按语言分派——Java 走声明类型提取（L1）/ symbol solver（L2），TypeScript 走 tsc bridge（Phase 2 起）；对上层暴露统一的 `resolve(node)` 契约

**落地增注（2026-09-22，item 20 Phase 1，live 以源码为准）**：

- 依赖落位：`typescript` npm 包是 `ai-dev/tools` 的 devDependency（绝不引入平台级 npm 依赖）；JS helper 为共享脚本 `ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs`。Java 侧解析顺序：系统属性 `nop.lint.tsc.helper` → 环境变量 `NOP_LINT_TSC_HELPER` → 从工作目录向上查找该脚本路径；均未命中时抛 `TscBridgeUnavailableException`（fail-visible，不静默降级）。
- 协议形态：stdin/stdout 上的换行分隔 JSON 帧。启动 ready 帧携带 node/typescript 版本号（typescript 绑定缺失或不可识别 → 不可用桥，拒绝握手）；请求帧含关联 `id` 与 `op`（`initProject`/`getTypeAtLocation`/`isTypeAssignableTo`/`shutdown`）；响应为 `ok:true`+`result` 或 `ok:false`+结构化 `error:{code,message}`。线坐标为 0-based line/col（TypeScript 内部约定），无跨端换算。
- 进程语义：惰性 spawn（首次真实查询才起进程，见 11 §3）；每请求 deadline 超时与进程崩溃均走结构化失败 + 有限重启预算；预算耗尽或握手失败 → 终态不可用，不无限重试、不伪造类型级答案。
- 查询语义（Phase 3 增注）：位置查询对**关键字 token**（`new`/`typeof` 等）上爬到其父表达式再取类型——关键字自身无类型，直查会得到 error/any 型（assignability 对一切为真，破坏 typeOf 语义）；assignability 的 `expectedType` 字符串形态经项目内 query-program（同一 checker）解析类型表达式，节点引用形态要求 from/to 同工程。

### 5.4 类型推导分层策略

| 项目 Phase | 类型层级 | Java 方案 | TypeScript 方案 | 累计可覆盖规则（对齐 §4.7） |
|-------|-------|----------|----------------|---------|
| **Phase 1** | 无 + L1 | AST 声明类型提取 | —（TS 支持 Phase 2 起） | ~70 条（40 纯 Pattern + 30 L1，均 Java） |
| **Phase 2** | +L2 | 复用 nop-java-parser symbol solver | tsc bridge（program 缓存/降级） | ~120 条（+50 L2） |
| **Phase 3** | +L3/L4 | 数据流 + 常量传播 + 语义分析 | tsc + 数据流 | ~190 条（+40 L3、+30 L4） |

### 5.5 关键决策

| 决策点 | 选项 | 推荐 | 理由 |
|--------|------|------|------|
| **Java 类型推导** | A: 从零自建 vs B: javac 集成 vs C: 复用 nop-java-parser + nop-ai-code-analyzer | **C** | JavaParserBuilder 已实现 CombinedTypeSolver + Maven jar 加载；L1 仍用 AST 声明提取 |
| **TypeScript 类型推导** | A: 自建 vs B: 调用 tsc | **B: 调用 tsc** | TS 类型复杂，自建不现实；tsc 是权威来源 |
| **类型信息缓存** | A: 文件级 vs B: 项目级 | **B: 项目级** | 类型跨文件引用，需要项目级缓存 |
| **增量更新** | A: 全量 vs B: 增量 | **B: 增量** | 编辑器集成场景需要增量类型更新 |

### 5.6 性能考虑

> 本文不另立延迟数字。Java/TS 各场景的目标延迟、预算与档位对应关系统一见 `11-performance-profiles.md` §3（分析器成本模型）与 §6（场景化运行剖面）；类型缓存/增量/惰性加载策略见 11 §3–§5 与本文 §6.6。

## 6. JavaParser 集成方案（基于已有模块）

> 本节不是"引入 JavaParser"，而是**复用** `nop-utils/nop-java-parser`（JavaParseTool，已含 symbol-solver）与 `nop-ai/nop-ai-skills/nop-ai-code-analyzer`（JavaParserBuilder、MavenProject）。§5.2 的 L1 声明类型提取（纯 tree-sitter AST）与本节的 L2 symbol solver 是互补关系：L1 覆盖轻量规则，L2 覆盖需要继承链/方法签名解析的规则。

### 6.1 两种 AST 的对比

| 特性 | Tree-sitter AST | JavaParser AST |
|------|----------------|----------------|
| **解析速度** | 极快（增量） | 较慢（全量） |
| **类型信息** | 无（纯语法） | 完整（类型推导） |
| **错误恢复** | 优秀 | 一般 |
| **增量解析** | 支持 | 不支持 |
| **节点结构** | 通用（kind + children） | 强类型（ClassDeclaration 等） |
| **适用场景** | Pattern 匹配、实时检查 | 类型分析、语义检查 |

### 6.2 推荐方案：双 AST + 映射 Map

```
┌─────────────────────────────────────────────────────────┐
│                  Nop Lint 双 AST 架构                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Source File                                              │
│       │                                                  │
│       ├──→ Tree-sitter Parse ──→ LintNode Tree (门面)     │
│       │         │                    │                   │
│       │         │                    ├── Pattern Match   │
│       │         │                    ├── Meta-Var Bind   │
│       │         │                    └── Rule Execution  │
│       │         │                                       │
│       │         └──→ Node Position Map ──────────────┐   │
│       │                                              │   │
│       └──→ JavaParser Parse ──→ CompilationUnit ──┐  │   │
│                │                                  │  │   │
│                ├── TypeResolver                   │  │   │
│                ├── SymbolResolver                 │  │   │
│                └── SemanticAnalysis               │  │   │
│                                                   │  │   │
│       ┌───────────────────────────────────────────┘  │   │
│       │                                              │   │
│       └──→ ASTMapping ←──────────────────────────────┘   │
│            (position-based bidirectional mapping)         │
│                 │                                        │
│                 └──→ Rule Execution                      │
│                      (Pattern + Type Info)               │
└─────────────────────────────────────────────────────────┘
```

### 6.3 映射 Map 设计

```java
// 基于位置的双向映射（契约）
public class ASTMapping {
    // 统一 byte offset 区间为键，避免行列换算歧义
    public record Range(int startByte, int endByte) {}

    // 构建：遍历 JavaParser AST，按节点位置在 tree-sitter AST 中查找同位置节点建立双向索引
    public void buildMapping(LintNode lintRoot, CompilationUnit cu) { ... }

    public <T extends Node> T getJavaNode(LintNode lintNode, Class<T> type);  // LintNode → JavaParser
    public LintNode getLintNode(Node javaNode);                                // JavaParser → LintNode
}
```

**边界不对齐的消解规则**（tree-sitter CST 与 JavaParser AST 的节点边界并非一一对应——注释归属、括号包裹、包级/占位伪节点都会造成 range 不完全相等）：

1. **精确命中**：range 完全相等 → 直接建立映射
2. **包含式命中**：JavaParser 节点 range 被 tree-sitter 节点 range 包含（或反之，容差 = 注释/空白）→ 取**最小包含者**建立映射
3. **多义命中**：同一 range 命中多个候选 → 取 kind 语义等价者（如 `MethodDeclaration` ↔ `method_declaration`）；无法判定时不建映射，查询返回 null
4. **兜底**：映射缺失时 `getResolvedType()` 返回 null，规则按"类型不可得"降级（不报假阳性）——映射是**尽力而为**的加速设施，不是正确性前提

### 6.4 节点扩展 vs 映射 Map

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|---------|
| **扩展 LintNode** | 访问快，无需额外查找 | 增加内存 | 高频访问（Pattern 匹配） |
| **映射 Map** | 解耦，灵活，可按需加载 | 查找开销，需要维护 | 低频访问（类型检查） |
| **混合方案** | 兼顾性能和灵活性 | 实现复杂 | **推荐** |

**混合方案**（契约级描述）：

- `TypedLintNode.getDeclType()`：纯 AST 快路径——取 `child("type")` 文本（L1，无 JavaParser 参与）
- `TypedLintNode.getResolvedType()`：惰性慢路径——经 ASTMapping 找到 JavaParser 节点，`Resolvable.resolve()` 求类型后缓存于节点
- `TypedLintNode.getMethodInfo()`：同上，映射到 `MethodDeclaration` 后提取签名

### 6.5 类型信息统一结构

> 这是 L2 求解器与规则/xscript 之间的**公共数据契约**（typeAnalyzer API 的返回结构），故保留字段级定义。

```java
// 统一的类型信息结构
public record TypeInfo(
    String name,                    // 类型名称
    String qualifiedName,           // 全限定名
    List<TypeInfo> typeArguments,   // 泛型参数
    TypeInfo componentType,         // 数组元素类型
    TypeKind kind,                  // 类型类别
    List<TypeInfo> superTypes,      // 父类型列表
    List<MethodInfo> methods,       // 方法列表
    List<FieldInfo> fields          // 字段列表
) {
    public enum TypeKind {
        CLASS, INTERFACE, ENUM, RECORD,
        PRIMITIVE, ARRAY, TYPE_VARIABLE,
        VOID, NULL, WILDCARD
    }
}

// 方法/字段签名（MethodInfo: name/returnType/parameters/thrownExceptions/isStatic/isFinal；
// FieldInfo: name/type/isStatic/isFinal —— 字段级契约同上，从简）
```

### 6.6 性能优化

| 优化策略 | 实现方式 | 收益 |
|---------|---------|------|
| **延迟解析** | 只在需要类型信息时才调用 JavaParser | 减少不必要的解析 |
| **增量缓存** | 文件修改时只重新解析变更部分 | 编辑器场景 |
| **项目级缓存** | 类型信息缓存到磁盘 | 重复检查加速 |
| **并行解析** | 多文件并行调用 JavaParser | 多核利用 |
| **选择性解析** | 只解析需要类型信息的文件 | 减少解析量 |

### 6.7 实现分层

```
┌─────────────────────────────────────────────────────────┐
│                    Nop Lint 分层架构                      │
├─────────────────────────────────────────────────────────┤
│  Layer 1: Pattern Matching（纯 Tree-sitter）              │
│  └── 快速、增量、无类型依赖                               │
│                                                          │
│  Layer 2: Type Resolution（JavaParser + Mapping）         │
│  └── 按需加载、延迟解析、缓存                             │
│                                                          │
│  Layer 3: Semantic Analysis（类型 + 数据流）               │
│  └── 完整分析、跨文件、项目级                              │
└─────────────────────────────────────────────────────────┘
```

## 7. 覆盖清单（Manifest）与排除声明

> 本文 §1–§2 的规则表是**代表性采样**（约 74 条 ErrorProne + PMD 各类代表），不是完整清单。"完整覆盖 PMD/ErrorProne 能力"这一需求的**可验收形式**是覆盖 manifest，而非把 800+ 条规则全部抄进设计文档。

### 7.1 Manifest 形式

- 位置：规划路径 `nop-lint-nop` 模块下 `src/main/resources/manifest/pmd-errorprone-coverage.yml`（尚未创建；随 Phase 2 首版建立，Phase 3 补全）
- 每条记录：`source_rule` / `tier`（Phase 1|2|3|excluded|excluded-with-approximation）/ `mechanism`（pattern 草图 | analyzer 依赖 L1–L4）/ `fixture`（RuleTester 验收用例路径）
- CI 校验：tier ∈ {1,2,3} 的规则必须有 fixture；excluded 必须有 reason

### 7.2 明确排除的 PMD/ErrorProne 能力

| 能力 | 排除理由 | 替代 |
|------|---------|------|
| **PMD CPD**（copy-paste 检测） | token 序列重复检测与 tree pattern 匹配是不同问题域，无法用 pattern DSL 表达 | backlog：独立 token-shingling 分析器，复用 Diagnostic 模型 |
| **ErrorProne DI/Dagger/Guice 系列检查** | Nop 使用 NopIoC 而非 Guice/Dagger，此类检查无意义 | 无需替代 |
| **ErrorProne 编译期数据流检查**（GuardedBy 锁分析等需 javac dataflow 的规则） | 依赖 javac 内部 dataflow API | Phase 3 数据流分析器近似覆盖子集；manifest 中标 excluded-with-approximation |
| **ErrorProne Android/protobuf 专用检查** | 非 Nop 技术栈 | 无需替代 |
| **PMD JSP/PLSQL/Apex 等非 Java 语言规则** | 源码语言范围仅 Java + TypeScript/TSX（XML 模型规则走 XNode 引擎，不属源码语言） | 无需替代 |

### 7.3 Severity 映射表（迁移与 `lint__listRules` 使用）

| 来源 | 映射到 Nop severity |
|------|--------------------|
| PMD priority 1–2 | error |
| PMD priority 3 | warning |
| PMD priority 4–5 | info / hint |
| ErrorProne ERROR | error |
| ErrorProne WARNING | warning |
| ErrorProne SUGGESTION | hint |

## 8. Checkstyle / PMD 现有配置迁移

> 本仓库当前 QA gate：`checkstyle.xml`（17 条激活规则：RegexpSingleline + 16 条 TreeWalker 规则）+ `pmd-ruleset.xml`（9 条规则：7 errorprone + 2 security）。切换到 Nop Lint 时需要逐条映射，避免双重报告或覆盖缺口。

### 8.1 迁移步骤（Phase 4）

1. **映射表**：为 17+9 条激活规则逐条写 Nop Lint 规则或映射到内置规则（manifest 中 `source: checkstyle.xml#RuleName`）
2. **并行期**：Nop Lint 以 `warning` 运行（不阻断 CI），与 checkstyle/PMD 双跑一个迭代
3. **切换**：逐模块移除 checkstyle/PMD 执行，Nop Lint 提升为 `error`
4. **回退预案**：并行期内保留旧配置于 git 历史，一条命令可恢复

### 8.2 配置导入器（可选，Phase 4 backlog）

- `checkstyle.xml → nop-lint config` 简单导入器（模块名/严重度/排除模式映射）；复杂规则人工映射
