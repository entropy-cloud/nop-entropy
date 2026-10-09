# 40 nop-javac 文件式编译能力增强

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/design/nop-incremental-build/00-vision.md` §八 G10、`ai-dev/design/nop-incremental-build/01-architecture-baseline.md` §12.1；构建侧 `plexus-compiler-javac` / `maven-compiler-plugin` 反汇编对照；本模块 live repo 核对（2026-10-08 首轮，2026-10-09 二轮 adversarial review + 实测复核，JDK 26.0.1）
> Related: `ai-dev/plans/00-plan-authoring-and-execution-guide.md`
> Review: 已完成两轮独立 adversarial review（首轮 6 Blocker / 14 Major / 12 Minor；次轮 2 Blocker / 7 Major / 8 Minor）。次轮全部 Blocker/Major 已按实测复核后修入本文本（关键项：live defect A 的机制由"多要求一个无 ERROR 条件"更正为"`getTask` 前置自校验"；Phase 1/Phase 2 边界按"选项面可达性"重划；`GenAopProxy` 激活面更正为 139 个模块；`TestGenErrorCodesYaml` 从门禁中移除）。

> **实测标注约定**：本计划的实测结论按 JDK 标注。`-proc` 默认值、`javax.tools.Processor` 接口形状、编码/解码级诊断是否进入 `DiagnosticCollector` 三项**仅在 JDK 26.0.1 实测**；JDK 21 需重测后才可声称跨版本成立。凡测试依赖这三项者，必须写成 JDK-无关形态，或在 Exit Criteria 中显式声明仅在 JDK 26 验证。

## Purpose

把 `nop-kernel/nop-javac` 从"只能在内存里编译字符串、产物只在内存里"改造成"能按构建侧语义把一组磁盘源文件编译到磁盘输出目录、并给出结构化诊断"的编译能力，为 `ai-dev/design/nop-incremental-build/` 描述的 AI 增量编译服务提供编译底座。

本计划只负责**编译能力本身**。增量编译服务的作用域分析、索引接入、测试调度、回退编排不在本计划内。

## Current Baseline

### 已成立的事实（已核对 live repo，2026-10-09）

**模块形态**

- 位置 `nop-kernel/nop-javac/`，`parent = nop-kernel`；依赖仅 `nop-api-core`、`nop-commons`、`janino`，测试依赖 `junit-jupiter`。**无需为实现本计划新增任何依赖**（`javax.tools` 为 JDK 内置；读取 class 文件版本只需按字节读取，不需要字节码库）。注：现有测试已依赖 janino 的**传递**依赖 `org.codehaus.commons.compiler.CompileException`，"无需新增依赖"的结论以此为前提。
- `src/main` 共 **14 个 `.java`、892 行**（`wc -l` 口径，`JdkJavaCompiler` 214 行不含末尾换行）；`src/test/java` 1 个类 110 行；`src/test/resources` 2 个夹具（`_CompileError.java`、`_FunctionDeclaration.java`）。
- 主源实为**三支**，不是两支：`io.nop.javac.jdk.*`（编译）、`io.nop.javac.janino.*`（解析/格式化/类加载）、以及根包 `io.nop.javac.*`（接口面、错误码、`JavaCompileTool` 实现、`DynamicURLClassLoader`、`JavaLibConfig`）。

**现有编译能力（`io.nop.javac.jdk`）**

- `JdkJavaCompiler`（214 行，**具体类，未实现任何接口**）提供两个 `compile` 重载：`(String 类名, String 代码, List classpath)` 与 `(List<JavaSourceCode>, List classpath)`。
- 输入是内存源码：内部构造 `string:///<类名>.java` 的 `SimpleJavaFileObject`。
- 产物是内存字节：`ForwardingJavaFileManager.getJavaFileForOutput` 把**所有**输出重定向到内部 `ClassLoaderImpl` 的内存 `ByteArrayOutputStream`；`JavaCompileResult.saveGenerated(File)` 是编译**之后**由调用方触发的回写。
- 编译选项硬编码且仅三项：`-source`/`-target` 固定为 `Runtime.version().feature()`、`-classpath`（Windows 下剥 `file:/` 前缀）。
- **javac 选项是方法内局部变量，唯一出口是 `LOG.debug`**——当前**没有**任何可供断言的选项访问面。
- 每次调用 `ToolProvider.getSystemJavaCompiler()` 新建编译器、新建 file manager，`finally` 中关闭 file manager（注释明写是为修 Windows 锁 JAR 的句柄泄漏）。`getTask` 传入的 Writer 为 **`null`**（`JdkJavaCompiler.java:120`）。
- `ClassLoaderImpl.findClass` 未命中时抛 `ClassNotFoundException`（已修的契约缺陷）。
- `JavaCompileResult` 是 **`final`** 类；其唯一 public 构造函数参数类型 `JdkJavaCompiler.ClassLoaderImpl` 是**包级私有**（`JdkJavaCompiler.java:137`）。public 方法共 **7 个**：`isSuccess` / `getGeneratedClassNames` / `getGeneratedClassBytes` / `getGeneratedClass` / `saveGenerated` / `getErrorMessage` / `toString`。内部 `CompileResultMessage` 的字段**已是 public**，真正的缺口是 `messages` 列表为 private 且**无任何访问器**——调用方只能经 `toString()` 拿字符串。
- `createFileManager(JavaCompiler, ClassLoaderImpl)` 是**包级两参方法**（`JdkJavaCompiler.java:186`），无注入/替换点。

**接口面**

- `IJavaSourceParser`（8 行）：只有 `parseJavaSource(SourceLocation, String)`。
- `IJavaCompileTool extends IJavaSourceParser`（12 行）：只有 `createDynamicClassLoader(...)`。**接口上没有任何编译方法。** 全仓仅 1 个实现（`JavaCompileTool`）、**0 个消费者**；janino 侧无批量编译能力。
- `IDynamicClassLoader extends AutoCloseable`：`isClosed()` / `loadClass(String)`。
- `JavaCompilerErrors`：3 个错误码。

**消费者（改动必须不破坏）**

| 消费者 | 性质 | 用到的面 |
|---|---|---|
| `nop-kernel/nop-codegen` 的 `GenAopProxy`（**仓内源码**，`.../task/GenAopProxy.java`） | **生产源码归属方**。`nop-kernel/nop-codegen/pom.xml` **未激活** `exec-maven-plugin`（该 execution 只存在于根 pom 与 `nop-kernel/pom.xml` 的 `<pluginManagement>`，pluginManagement 不激活插件），且其测试树内**无任何** `GenAopProxy` 调用点（`CodeGen.java:24`、`CodeGenForIoC.java:13` 两处均为注释）。真实触发方是**声明了 `exec-maven-plugin` 的 139 个模块**（含 `nop-ioc`、`nop-orm`、`nop-wf-*`、`nop-auth-*`、`nop-retry-engine`、`nop-tcc-dao`、`nop-dyn-service`） | `new JdkJavaCompiler()`、`getDefaultClassPaths()`、`compile(List<JavaSourceCode>, List)`、`isSuccess()`、`getErrorMessage()`、`saveGenerated(File)` |
| `nop-core-framework/nop-ioc` 的 `TestAop` | 测试。`nop-core-framework/nop-ioc/pom.xml` **激活**了 `exec-maven-plugin`，是 AOP 生成链路**唯一可观察的测试承载方**（产物 `nop-core-framework/nop-ioc/target/test-classes/io/nop/ioc/aop/MyClass__aop.class`） | 同上调用序列 + `saveGenerated` 落盘到 `nop-core-framework/nop-ioc/target/test-classes` |
| `nop-core` 的 `TestAopCodeGenerator` | 测试 | `JdkJavaCompiler`、`getDefaultClassPaths()`、`JavaCompileResult` |
| `nop-kernel/nop-xlang-java`：**5 个直接调用测试类**（`TestTagUnitGeneration`、`TestExecToJavaTranslatorCoverageA/B`、`TestGeneratedCodeBehavior`、`TestJavaBackendRoutingScenarios`）+ **1 个 helper**（`JavaBackendColumn`）+ **3 个经 helper 间接消费**（`TestCorpusCoverageA/V1/CoverageBJavaColumn`，仅在 Javadoc 中提及） | 测试 | `JdkJavaCompiler`、`JavaCompileResult` |
| `nop-utils/nop-java-parser` 的 `JavaParseTool` / `JavaParserParseResult` | 生产 | `IJavaSourceParser`、`IJavaParseResult` |
| `nop-kernel/nop-xlang` 的 `JavaToXLangTransformer` | 生产 | `JavaCompilerErrors` |
| `nop-javac` 自身的 `TestJavaCompileTool` | 测试 | `JdkJavaCompiler`、`JavaCompileResult`、`JavaCompileTool` |
| `nop-runner/nop-cli-core` 的 `TestGenErrorCodesYaml` | 测试，**但它是生成器不是断言测试**：零断言，只调用 `ErrorCodeExtracter.saveToFile` **重写** `src/main/resources/_vfs/i18n/zh-CN/nop-cli-errors.i18n.yaml`，从不触碰 `en/` 份。**实跑会修改被跟踪源文件**，无稳定通过条件 | 以类字面量引用 `JavaCompilerErrors` |

**消费耦合放大面**：上述消费方**无一显式声明**对 `nop-javac` 的依赖，全部经 `nop-xlang → nop-core → nop-javac` 传递引入。公开 API 变更会波及从未声明依赖的模块。

**测试覆盖现状**

- 本模块唯一测试类 `TestJavaCompileTool`（110 行 / **7 个 `@Test`**）：解析、解析报错行号、动态类加载器、类不存在抛 CNFE、错误详情短消息、**当前 JDK 语法可编译**、**非法类名抛 NopException**。
- 内存编译路径**实际只有 2 个用例真正调用 `compile()`**（第三个 `testCompileInvalidClassNameThrowsNopException` 在 `compile` 之前就抛异常），其中已包含"产物可被 `getGeneratedClass` 加载"与"`isSuccess()` 为真"的断言。
- 真正缺口：**无 `saveGenerated` 落盘断言**、**无 `getGeneratedClassNames()` 非空断言**、**无 `getGeneratedClassBytes()` 直接断言**、**无 javac 选项断言**（因为没有访问面）。

**已确认的 live defect 与死代码**

- **live defect A（假绿风险，直接威胁 AI 编译服务）——机制已实测更正（两轮）**：
  `JdkJavaCompiler.java:120` 传给 `getTask` 的 Writer 是 **`null`**。本缺陷的真实形态是：**`CompilationTask.call()` 的返回值单独不足以判定成功，而失败信号完全落在 `javax.tools` 的两个结构化通道之外**。JDK 26.0.1 实测（三种输入形态，必须分开看）：

  | 输入形态 | `call()` | collector ERROR | task Writer | `System.err` | class 产物 |
  |---|---|---|---|---|---|
  | **内存**输入（`SimpleJavaFileObject` 覆写 `getCharContent`）+ `-encoding` 非法字符集名 | `true` | 0 | **0 字节** | **空** | **正常产出**（编码被完全忽略，javac 从不解码内存源码） |
  | **磁盘**输入 + `-encoding` 非法字符集名 | `true` | 0 | **0 字节** | 有（"错误: 不支持的编码"） | **输出目录未创建、无任何产物**（比"静默损坏"更糟的静默 no-op） |
  | **磁盘**输入 + 源文件含目标编码不可映射字符（Latin-1 字节按 UTF-8 读） | `true` | 0 | **0 字节** | 有（"不可映射字符"） | **产出，内容静默损坏** |

  **必须记录的四点更正**：
  (a) **不存在**"`call()` 返回 `true` 且 collector 含 ERROR"的输入。实测约 50 组输入（含语法/符号/类型/抽象未实现/重复类/`-Werror`/缺源文件/损坏 class 文件/处理器抛异常等 13 组注解处理场景）**全部**为 `call()==false` 或 `call()` 抛出。因此"`isSuccess()` 增加'无 ERROR 诊断'条件"是**空操作**——javac 已在 `call()` 内做了这件事。
  (b) **给 `getTask` 传入真实 `Writer` 对解码级/字符集级诊断捕获 0 字节**。故"接一条 Writer 通道"**不是**本缺陷的修复手段；它只能捕获 `-d` 指向普通文件时 javac 内部的崩溃文本，而该场景 `call()` 本就返回 `false`。
  (c) 因此本缺陷**只能由本模块在 `getTask` 之前做前置自校验来覆盖**（字符集名合法性、源文件在声明编码下可解码、输出目录形态），因为唯一的替代——劫持进程级 `System.err`——被本计划列入 Non-Goals（且实测必须在 javac 加载其 `Log` 类**之前**替换才捕获得到：早期替换捕获 184B，晚期替换捕获 0B，属非线程安全的全局状态）。
  (d) **全部三行形态对今日入口都不可达**——现有编译入口的选项硬编码为 `-source`/`-target`/`-classpath`，既无编码通道也无输出目录通道，且输入固定为内存源码。故本缺陷的修复**依赖 Phase 2 的选项面**，其验收时点为 Phase 2。
  - **同源缺陷 A2（未包装异常逃逸）**：`getTask` / `call` 两处**均无 try/catch**（`fileManager.close()` 另有既有的 `IOException` → `LOG.warn`，非本计划引入）。实测各阶段异常类型**不同**：`getTask` → `IllegalArgumentException`（`--release` 与 `-source` 冲突）；`call()` → `IllegalStateException`（`-target 8` 相对运行源版本过旧）；javac 内部 → `java.lang.Error`（`-d` 指向普通文件）；处理器抛出 → `RuntimeException`/`Error` 直接逃出 `call()`。因此修复必须捕获 **`Throwable`** 而非 `Exception`，否则最危险的一类仍漏出。
  - **依赖声明**：live defect A 的修复与 A2 的 `getTask`/`call` 包装**在 Phase 2 落地**（需要选项面才可驱动对应输入）；Phase 1 不声称覆盖它们，其代码也**不在** Phase 1 触碰——避免产出"存在但不可达"的空壳（Rule #24）。
- **live defect B（静默降级）**：`JavaCompileResult.CompileResultMessage` 用 `instanceof SimpleJavaFileObject` 判断能否读源码。实测磁盘路径返回的是 `com.sun.tools.javac.file.PathFileObject$SimpleFileObject`，`instanceof javax.tools.SimpleJavaFileObject` 为 **`false`**（JDK 9+ 从来如此）。因此一旦切到文件式输入，**所有诊断的源码片段会静默降级为 `[SOURCE CODE UNAVAILABLE]` 且不打任何日志**。本计划会直接踩中它。
- **live defect B2（静默产出误导文本，与 B 同源）**：当源码不可读时 `sourceCode` 被赋为占位符字符串，而 `textHighlightPos_*` 是按 **`sourceCode.length()`**（即占位符长度 27）裁剪的，`codeOfConcern` / `codeOfConcernLong` 随后对**占位符字符串**做 `substring`（`JavaCompileResult.java:130-132, 156-157`）。结果是在源码不可读时产出**从占位符里切出来的误导性 "Code of concern" 文本**。修复 B 时必须一并修掉 B2。
- **live defect C（i18n owner-doc drift）**：`nop.err.javac.invalid-class-name` 在 `zh-CN/nop-cli-errors.i18n.yaml:854` 存在，但 `nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/en/nop-cli-errors.i18n.yaml` **完全缺失该条**（该文件只有 `parse-fail` 与 `not-support-transform-to-xlang-ast-fail`）。
- 死代码：`JdkJavaCompiler` 第 188–192 行被注释掉的 `setLocation(CLASS_PATH, ...)` 块；`urlToFiles` 包级私有、全仓无 live 调用点；`getDefaultClassPaths()` 的 `classLoader instanceof URLClassLoader` 分支——JDK 9+ 应用类加载器**永不**是 `URLClassLoader`，该分支在本仓 JDK 21/26 下恒为 false，实际总是走 `java.class.path` 回退分支。
- **owner-doc drift**：`nop-kernel/nop-javac/README.md` 声称 `IJavaCompileTool.compileAndLoadClass()` / `compile()` / `parseSource()`、`IJavaSourceParser.parse()` 与 `IDynamicClassLoader.addURL()`，这些方法**均不存在**（共 **5 处** public-contract drift）。

**与构建侧（`plexus-compiler-javac`）的能力差距（本计划要补的）**

构建侧经 `javax.tools` 标准路径：源文件经 `StandardJavaFileManager.getJavaFileObjects(File...)`（**按文件对象**传入；`getJavaFileObjectsFromStrings` 是字符串源码 API，此处不适用）传入、选项装配成 CLI 风格 `String[]`、使用**标准 file manager**、并有编译器实例池 + 显式复用策略。其配置面含本模块完全缺失的开关：

| 缺失能力 | 构建侧对应项 | 对 AI 编译服务为何必需 |
|---|---|---|
| 磁盘输出目录 | `-d` | 产物必须落模块输出目录，否则无法与既有产物共存、无法做产物一致性对拍 |
| `--release` | `releaseVersion` | 现为运行 JDK 版本（本机 26），与本仓按 execution 的 `release`（默认 17，`nop-core` 测试编译为 16，**已核对 pom**）**不一致**，会产生不同 class 版本 |
| 隐式编译策略 | `-implicit:` | 默认会为源码路径上的隐式文件**写出 class**，覆盖生成代码产物，且扩散不可观测 |
| 注解处理控制 | `-proc:` / `-processor` / `-processorpath` | 见下方实测前提 |
| 源码路径 | `-sourcepath` | 影响分析需要 |
| 生成源目录 | `-s` | 注解处理器生成物落点 |
| 模块路径 | `--module-path` | 部分模块需要 |
| 编码 | `-encoding` | 源码含非 ASCII |
| 自定义透传 | `customCompilerArguments` | 避免为个别开关改接口 |
| 结构化诊断 | 收集器 → 结构化消息 | 服务反馈契约需要"文件 + 行 + 列 + 消息 + 建议动作" |
| 编译器复用 | 实例池 + 复用策略 | 编译器/文件管理器非线程安全，须串行 + 复用而非每次新建 |

> **关于"构建侧"的证据边界**：上表的"构建侧对应项"来自 `plexus-compiler-javac` / `maven-compiler-plugin` 的公开配置面（仓内未 vendor 源码）。本计划**只把本仓实测结论当作事实依据**；"构建侧只走 file manager 编码通道"、"构建侧已验证复用编译器但每轮新建 file manager"两条属外部来源断言，**不得**在本计划的测试或 Exit Criteria 中当作实测依据。

**注解处理器的仓内现状（实测）**

- 12 个 pom **配置**了处理器路径（10 个 `annotationProcessorPaths` + 2 个 `<proc>full</proc>`）。
- 但"启用"是另一回事：实测 JDK 26.0.1，处理器在 `-classpath` 上且**无 `-proc` 标志**时**不运行**（JDK 23+ 默认 `-proc:none`；本仓 `nop-benchmark-json/pom.xml:45-46` 自己也记录了这点）。`-proc:full` + 处理器在 classpath + 无 `-processor` → **仍不运行**；`-processor <name>` + `-proc:full` → 运行。
- **处理器发现的服务文件前提**：实测 `-proc:full` + `-processorpath <dir>` 在**没有** `META-INF/services/javax.annotation.processing.Processor` 时**不会**运行处理器；补上该服务文件后才会运行。因此在"禁止放服务文件"的约束下，**必须**显式 `-processor` 命名唯一处理器。
- `nop-javac` test classpath 上的注解处理器：**0 个**（37 个 classpath 条目，含各模块 `nop-kernel/nop-javac/target/classes` 目录与 jar，无处理器服务文件）。处理器只出现在 `annotationProcessorPaths`，不进 classpath。

**已实测的 javac 行为基线（Phase 2/4 的判定依据；JDK 26.0.1，2026-10-09 复测）**

| 场景 | 实测行为 |
|---|---|
| `-d` 指向不存在的多层目录 | **自动创建并编译成功**，不报错 |
| `-d` 指向**空串** | `call()` 返回 **`true`**、**零诊断**、产物照旧（静默 no-op，**Rule #24 违规面**） |
| `-d` 指向一个**已存在的普通文件** | `call()` 返回 `false`、**collector 零诊断**，异常以 `java.lang.Error` 文本形式进入 task Writer |
| `--release` 与 `-source` 同时给 | **`getTask` 阶段抛 `IllegalArgumentException`**（`call()` 根本不执行） |
| `-target 8` 单独给（相对运行源版本过旧） | **`call()` 阶段抛 `IllegalStateException`**（不是返回 false） |
| `-d` 指向一个**已存在的普通文件** | `call()` 返回 `false`、**collector 零诊断**，javac 内部 `java.lang.Error` 以文本形式进入 task Writer |
| `-d` 指向**普通文件下的子路径**（`<file>/sub`） | `call()` 返回 `false`、**collector 1 条 ERROR** |
| `-implicit:none` + 源码路径上的隐式文件 | **静默不生成**该隐式文件的 class（不报错、零诊断）；`-implicit:class` 则生成。**差异可通过输出目录内容观测** |
| `getSystemJavaCompiler()` | **每次返回不同实例**，复用必须显式持有 |
| 编码通道 | **条件式**：仅当 file manager 的 charset **显式非 null** 时才压制 CLI `-encoding`；charset 为 null 时 CLI `-encoding` 生效。当前 `createFileManager` 传的是 `getStandardFileManager(null, null, null)`（charset=null），故现状下 CLI 通道可用 |
| **`call()` 的返回值不可单独作为成功判据** | 实测（磁盘输入）：`-encoding` 非法值 → `call()` 返回 `true`、collector 与 task Writer 均为空、**输出目录未创建、无任何产物**；源文件含不可映射字符 → `call()` 返回 `true`、两个通道均为空、**产物落盘且内容静默损坏**。两种场景的真实信号只在 `System.err`，见 live defect A |
| 磁盘路径 `JavaFileObject` 类型 | `com.sun.tools.javac.file.PathFileObject$SimpleFileObject`，`instanceof SimpleJavaFileObject` 为 `false` |
| `--release` 与 class 版本 | 读取 class 文件偏移 6–7 的大端无符号 short 得 major version；实测 `release 11 → 55`、`release 17 → 61`。二者是 CI（JDK 21）与本机（JDK 26）**都存在**的版本，可作为跨版本安全的断言取值 |
| 注解处理器可发现性 | `public static` 嵌套类 `extends AbstractProcessor`，编译进 `nop-core-framework/nop-ioc/target/test-classes`，以 `-processorpath <nop-kernel/nop-javac/target/test-classes>` + `-processor <Outer>$<Inner>` + `-proc:full` 可被发现并运行；`-processorpath` 接受普通目录。**自动发现需 `META-INF/services/javax.annotation.processing.Processor` 服务文件**——实测无该文件时 `-proc:full` + `-processorpath` **不会**运行处理器 |
| `AbstractProcessor.init()` | **必须显式覆写并调 `super.init(e)`**，否则 `processingEnv` 为 null 且 `getFiler()` NPE |
| 处理器 round 守卫 | **只判 `roundEnv.processingOver()` 不足以防重复 `createSourceFile`**——最终注解轮仍会进入并二次创建，抛 `FilerException: Attempt to recreate a file`，并被处理器报为 ERROR（造成假编译失败）。必须用一次性 `boolean` 闩锁 |
| 生成源目录 | 未给 `-s` 时，处理器生成的 `.java` 会落进 **`-d` 输出目录** |

## Goals

- `nop-javac` 能接受一组**磁盘源文件**，按构建侧等价语义编译到**磁盘输出目录**，并返回结构化诊断。
- 编译选项可覆盖本仓按 execution 的 `release`，可显式指定隐式编译策略、注解处理开关、源码路径、生成源目录、模块路径、编码，并支持自定义参数透传。
- **覆盖 live defect A 的真实机制**：绕开 `DiagnosticCollector` 与 task `Writer` 的解码级/选项级失败必须能被本模块观测为失败，不得静默产出损坏 class。
- 现有内存编译 API 与全部既有消费者（尤其 `GenAopProxy`、`TestAop`）**行为不变**；但上述 live defect A / B / B2 属"行为不变"的保护范围**之外**，必须修。
- 结构化诊断以独立访问面暴露，不要求调用方解析字符串。
- 修复 live defect C（en 份 i18n 缺码）与 owner-doc drift。
- 为每个新增能力补齐单元测试，且不依赖外部环境。

## Non-Goals

- 不实现增量编译的作用域分析、符号索引接入、影响分析、测试调度、回退编排（属 `nop-incremental-build` 设计文档范围）。
- 不实现注解处理器的发现/编排逻辑本身，只提供把处理器路径与开关正确传给 javac 的能力。
- 不改变 `GenAopProxy` 的 AOP 生成逻辑或其调用方式。
- 不替换或对接 `plexus-compiler-javac`（不引入 plexus 的编译抽象）。
- 不做跨 JVM 编译进程管理、外部 `javac` 进程派生、**不劫持 `System.err` 全局流**。
- 不改动 `janino` 解析分支与 `JavaLibConfig` / `DynamicURLClassLoader` 的既有语义。
- 不改动任何 `pom.xml`（含新增测试用依赖）。
- 不统一三支包的抽象层次（仅在必要时共用新增的选项/诊断类型）。
- **不改 `IJavaCompileTool` / `IJavaSourceParser` / `IDynamicClassLoader` 三个既有接口的签名**（见 D1）。

## Scope

### In Scope

- `io.nop.javac.jdk` 包内的编译能力增强，`io.nop.javac` 包内的接口、错误码与 owner 文档。
- 上述 live defect（A / B / B2 / C）与死代码清理、owner-doc drift 修复。
- 新增能力的单元测试；现有内存编译路径的回归护栏测试。
- 新建 `ai-dev/design/nop-javac/` 契约文档，并登记进 `ai-dev/design/README.md` 子系统索引。

### Out Of Scope

- 任何 Maven 插件、生命周期接入、profile 变更。
- 任何 `pom.xml` 的变更。
- AI 编译服务的模块归属、服务形态、暴露方式。
- 错误码文案的语言统一（现有 3 码为中文，AGENTS.md 要求英文）——本计划只新增码并同步既有漂移，不做语言统一改造。

## Execution Plan

### Phase 1 - 落位裁决、基线护栏与缺陷清理

Status: completed
Targets: `nop-kernel/nop-javac/src/main/java/io/nop/javac/`、`nop-kernel/nop-javac/src/test/java/io/nop/javac/`、`ai-dev/design/nop-javac/`

- Item Types: `Decision | Fix | Proof`

> **本 Phase 边界**：live defect A / A2 **不在**本 Phase 落地。它们的修复需要一个可设定编码/输出目录的选项面，而该选项面属 Phase 2；在此之前实现相应代码只会产出"存在但不可达"的空壳（Rule #24 禁止）。本 Phase 只建立后续 Phase 依赖的两个接缝与全部现有路径的回归护栏。

- [x] **Decision D2（落位裁决）**：编译能力落在何处——扩展本模块现有封装（本计划默认方案）vs 新建独立模块。被引用的 `01-architecture-baseline.md` §12.1 裁定为"自建约 200 行薄驱动、不复用本模块封装"，本计划**反转**该裁定，理由必须写清：薄驱动落进本模块可复用其诊断模型、错误码体系与 `getDefaultClassPaths`，避免同一编译能力出现第二套诊断语义；代价是本模块成为编译底座的唯一归属方、变更半径扩大——且实测该半径**不是**计划初稿写的"7 个消费者"，而是**所有激活 `exec-maven-plugin` 的 139 个模块**（`GenAopProxy` 真实触发方），`nop-codegen` 自身不激活、自身无测试承载。裁决结论写入 owner 文档，并同步修订 Baseline §12.1 的对应表述（含把 §12.1 里"源文件按路径字符串传入"更正为"按文件对象传入"）。
- [x] **Decision D1**：`IJavaCompileTool` 是否承载编译方法。约束：该接口全仓仅 1 个实现、**0 个消费者**，且 janino 侧无批量编译能力——若挂 `default` 方法将得到"新功能默认不支持"。须裁定：接口保持不变、编译能力只挂在 `JdkJavaCompiler` 上（推荐）；或挂接口并接受 janino 侧显式不支持。**裁定须避免产出一个零消费者的死接口方法**，且不得产出"默认抛 `UnsupportedOperationException`"的空壳面。
- [x] **Decision D6**：`JavaCompileResult` 是扩展为同时表达磁盘产物，还是新增并列结果类型。约束：现有 7 个 public 方法的语义与签名不得改变；`JavaCompileResult` 是 **`final`**，其唯一 public 构造函数参数类型 `JdkJavaCompiler.ClassLoaderImpl` 是**包级私有**——即"扩展 `JavaCompileResult`"必然要求去掉 `final` 或改 public 构造函数签名，**两者都是公共契约变更**，须单列为 `Fix` 并给出 `nop-ioc` / `nop-core` / `nop-xlang-java` 三个消费模块的回归证据；新结果类型若放在 `io.nop.javac.jdk` **之外**将取不到产物字节，该位置约束必须写进裁决。
- [x] **Fix（接缝）**：抽出两个可断言的接缝——(a) **javac 选项装配缝**（当前 `options` 是局部变量、唯一出口是 `LOG.debug`，无任何可断言面）；(b) **file manager 工厂缝**（当前 `createFileManager(JavaCompiler, ClassLoaderImpl)` 是包级两参方法，无注入/替换点，Phase 5 的 `close()` 计数断言没有前置条件）。二者都**直接位于现有 `compile()` 调用路径上**，必须由 Phase 1 的断言测试证明其被真实调用（不是孤立存在）。**接缝的可见性与测试落位须一并裁定**：二选一——(i) 缝为 package-private，并在 `src/test/java/io/nop/javac/jdk/` 新增同包测试类承载 Phase 5 的 `close()` 计数断言；(ii) 缝为 public 并接受 API 面扩大。**无论哪种，都不得为测试在 `io.nop.javac` 下访问 `io.nop.javac.jdk` 的包级成员。**
- [x] **Fix**：修复 `JavaCompileResult.CompileResultMessage` 的 `instanceof SimpleJavaFileObject` 判断——磁盘输入下它恒为 false，导致源码片段静默降级为占位符且无日志。改为按 `JavaFileObject` 抽象判断可读性，并在不可读时**记录日志**而非静默占位；**同时修掉 live defect B2**：不可读时不得对占位符字符串做 `substring`（当前实现按占位符长度裁剪位置并切片，产出误导性 "Code of concern"），`codeOfConcern` / `codeOfConcernLong` 须置空。**注：本项的验证需文件式输入，故完整验证落在 Phase 2；Phase 1 只完成代码修复并保证内存路径不回归。**
- [x] **Fix**：删除 `JdkJavaCompiler` 第 188–192 行注释块；删除无调用点的 `urlToFiles`；删除或给出保留理由 `getDefaultClassPaths()` 中 `instanceof URLClassLoader` 的死分支（**注意**：D2 的裁决理由依赖 `getDefaultClassPaths`，不得把这个方法本身移出范围）。
- [x] **Fix**：修复 `nop-kernel/nop-javac/README.md` 中 `compileAndLoadClass()` / `compile()` / `parseSource()` / `parse()` / `addURL()` **五处** public-contract drift。
- [x] **Fix（live defect C）**：补齐 `nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/en/nop-cli-errors.i18n.yaml` 中缺失的 `nop.err.javac.invalid-class-name` 条目（zh-CN 份已有）。这是**独立于**任何生成器的显式修复项，不得寄生在别的门禁里。
- [x] **Fix**：清理 `TestJavaCompileTool.defineClass()`——该用例方法体为空、零断言（仓库的空测试反模式，且正落在本 Phase 的触碰面上）。
- [x] **Proof**：补**落盘与字节一致性护栏测试**——断言 `saveGenerated` 落盘后的字节与 `getGeneratedClassBytes` 一致（现有 2 个编译用例已覆盖"可加载"，不重复）。
- [x] **Proof**：补**兼容性护栏测试**——复刻 `GenAopProxy` 与 `TestAop` 的实际调用序列（构造 → `getDefaultClassPaths` → `compile(List<JavaSourceCode>, List)` → `isSuccess` → `getErrorMessage` → `saveGenerated`）。**注意**：`nop-codegen → nop-core → nop-javac` 构成依赖环，无法在本模块直接调用 `GenAopProxy`；护栏只能复刻序列，真实调用由 `nop-ioc` 的构建产物承担（见 Exit Criteria 接线验证）。
- [x] **Proof**：补**选项装配缝断言测试**——锁定当前内存编译路径的选项基线（含 `-source`/`-target` 取运行 JDK 版本、`-classpath` 拼装、Windows 前缀处理）。**此测试同时证明选项装配缝位于真实调用路径上**。
- [x] **Proof**：补 **file manager 工厂缝断言测试**——经可注入的替换点断言 `compile()` 真实经由该缝创建 file manager 并在 `finally` 关闭一次。**此测试同时证明该缝位于真实调用路径上**，并为 Phase 5 的 `close()` 计数断言提供前置。
- [x] **Proof**：补 **`getGeneratedClassNames()` 非空** 与 **`getGeneratedClassBytes()` 内容** 的直接断言（现有用例均未直接断言这两项）。
- [x] **Proof**：补 **`codeOfConcern` / `codeOfConcernLong` 非空** 的断言，覆盖 live defect B2 的修复（正常可读路径下）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1 / D2 / D6 三项 Decision 有书面裁决与理由，且与 owner 文档无冲突
- [x] `nop-javac` 模块测试通过（`./mvnw test -pl nop-kernel/nop-javac -am`）
- [x] `./mvnw test -pl nop-kernel/nop-core -am` 通过（`JdkJavaCompiler` / `JavaCompileResult` API 面回归；该模块**不激活** `exec-maven-plugin`，本门禁**不覆盖 AOP 生成**——AOP 生成仅由下方 nop-ioc 接线验证覆盖）
- [x] `./mvnw test -pl nop-core-framework/nop-ioc -am` 通过（`saveGenerated` 唯一另一处消费者，且是 AOP 生成唯一可观察承载方）
- [x] `./mvnw test -pl nop-kernel/nop-xlang-java -am` 通过（5 个直接消费测试类 + helper；该模块同样**不激活** `exec-maven-plugin`，本门禁不覆盖 AOP 生成）
- [x] **接线验证**：`nop-ioc` 是 AOP 生成链路唯一可观察的测试承载方。删除 `nop-core-framework/nop-ioc/target/test-classes/io/nop/ioc/aop/MyClass__aop.class` 与 `nop-core-framework/nop-ioc/target/generated-test-sources/` 后运行 `./mvnw test -pl nop-core-framework/nop-ioc -am`，确认该 class 由 `exec:aop` → `GenAopProxy` → `JdkJavaCompiler.compile` → `saveGenerated` 重新生成（`nop-codegen` 无测试承载该路径，**不再列为证明方**）
- [x] **无静默跳过**：修复后的诊断源码片段在不可读时**有日志**且 `codeOfConcern*` 置空；删除的死代码无残留调用点（`grep` 验证）；`defineClass()` 空用例已清理；本 Phase 未新增未实现的空方法体；**本 Phase 未产出"存在但不可达"的死代码**（defect A/A2 已显式顺延至 Phase 2，未在此提前实现）
- [x] **两个接缝均已建立**，各有一个**经由 `compile()` 真实调用路径**的断言测试（非孤立存在），且接缝可见性裁定与测试落位已落地（新增 `src/test/java/io/nop/javac/jdk/` 同包测试类，或已裁定缝为 public）
- [x] live defect C 修复已落地：`nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/en/nop-cli-errors.i18n.yaml` 含 `nop.err.javac.invalid-class-name`，且两份 yaml 的 `nop.err.javac.*` key 集合一致（9 码，含 Phase 2/5 新增码）
- [x] `ai-dev/design/nop-javac/` 已建立并登记进 `ai-dev/design/README.md` 子系统索引
- [x] `ai-dev/design/nop-incremental-build/01-architecture-baseline.md` §12.1 已按 D2 裁决同步
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 错误码一致性：**本计划作用域内**（`nop.err.javac.*`）中英两份 `nop-cli-errors.i18n.yaml` 的 key 集合 diff 为空，且占位符集合逐条一致（**不得**要求全文件 key 集合一致——实测全文件 zh 1505 / en 1334，存在 **173 条既有 zh-only drift + 2 条既有 en-only drift**，全部属本计划 scope 外的其他模块，见 `Non-Blocking Follow-ups`）。**手改而非生成**：`TestGenErrorCodesYaml` 是零断言生成器，且它从**本地仓库中的 stale nop-javac jar** 抽取错误码——不先 `./mvnw install -pl nop-kernel/nop-javac` 就重跑，产出的是旧码集合。**实测：即使先 install，重跑生成器也会把 zh-CN 份中约 29 行**属其他模块**的既有 drift 一并写入**（含一条 `nop.err.eql.collection-access-not-support` 的删除，该码已由 plan 2307 退役）——这些与本计划无关却会搭车进入本计划的提交。故本计划按作用域**手改**，并以 `git diff` 逐行核对"只含 `nop.err.javac.*` 行"作为附加门禁。**`git diff --exit-code` 不作为门禁**——本计划刻意引入的 yaml 变更本身就是 working tree 改动
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **端到端验证 N/A**：本 Phase 不引入新编译路径，端到端验证由 Phase 2 的新增路径承担（已显式声明，见 Rule #22）
- [x] **owner-doc**：`nop-kernel/nop-javac/README.md` 已按 live baseline 同步（Rule #17）。`docs-for-ai/` 实测全目录对 `nop-javac` / `JdkJavaCompiler` / `JavaCompileTool` **零引用**，无既有 owner doc 可漂移，故本 Phase `No docs-for-ai update required`（Phase 3 起若结构化诊断面升级为对外契约，另行新增）

### Phase 2 - 文件式编译与磁盘输出

Status: completed
Targets: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/`、`nop-kernel/nop-javac/src/test/java/io/nop/javac/`

- Item Types: `Decision | Feature | Proof`

- [x] **Decision D3**：新增编译选项对象的字段集与默认值。至少覆盖：输出目录、classpath、`release`、编码、源码路径；其余字段在 Phase 4 追加。约束：**未设置项必须走与今天等价的默认行为**，且该等价性由 Phase 1 的选项装配缝断言锁定。
- [x] **Decision D4**：输出目录缺失时的策略。实测 javac 会**自动创建多层目录并成功编译**（见基线表）。须裁定：(a) 与 javac 对齐自动创建；(b) 显式失败。**若选 (b) 须同时说明与 `saveGenerated` 既有语义（`FileHelper.writeBytes` 会自动建父目录）如何统一**——两者不一致本身就是缺陷来源。另须覆盖实测的另外三种形态：`-d` 指向**空串**（`call()==true`、零诊断、静默 no-op）、`-d` 指向**已存在的普通文件**（`call()==false`、collector 零诊断、javac 内部 `Error` 进 task Writer）、`-d` 指向**普通文件下的子路径**（`call()==false`、collector 1 条 ERROR）。**空串形态必须由本模块在 `getTask` 之前显式拒绝**——它对 javac 是完全静默的。
- [x] **Decision D9**：错误传播形态。实测各阶段异常类型**不同**：`getTask` → `IllegalArgumentException`（`--release` 与 `-source` 冲突）；`call()` → `IllegalStateException`（`-target 8` 相对运行源版本过旧）；javac 内部 → `java.lang.Error`（`-d` 指向普通文件）；处理器抛出 → `RuntimeException` / `Error` 直接逃出 `call()`。须裁定：编译入口 `getTask` / `call` 两处失败一律映射为**带错误码的 NopException**，不得有未包装的 `Throwable` 逃逸（因此捕获范围必须覆盖 `Throwable` 而非仅 `Exception`——`java.lang.Error` 是实测存在的一类）。`fileManager.close()` 沿用既有的 `IOException` → `LOG.warn` 处理，非本计划引入。**此项必须落地，否则本 Phase 引入未识别的生产路径行为变更。**
  **D9 必须回答一个子问题**：该包装是否同时施加于**既有** `compile(List<JavaSourceCode>, List)`（`GenAopProxy` 唯一调用点）？若是，属对生产路径错误契约的 live 变更，须列为独立 `Fix` 并补一条断言异常类型的回归测试；若否，须显式记录既有泄漏仍在，并为其登记 successor ownership（**不得留空**）。
- [x] **Fix（live defect A，最高优先）**：按实测机制修复——**在 `getTask` 之前**完成三项前置自校验，任一失败即以带错误码的 NopException 显式失败：字符集名合法性、源文件在声明编码下可解码、输出目录形态合法。**明确不做**以下两种无效修复：(i) 给 `isSuccess()` 加"无 ERROR 诊断"条件（实测 javac 已在 `call()` 内做了这件事，是空操作）；(ii) 给 `getTask` 传真实 `Writer`（实测对解码级/字符集级诊断**捕获 0 字节**，该 Writer 只能捕获 `-d` 指向普通文件时 `call()` 本就为 `false` 的崩溃文本）。**该机制与"接一条原始诊断通道"是同一项工作的两面**：Writer 通道按 D10 保留并纳入结构化面，但假绿的**实际拦截**由前置自校验承担。
- [x] **Fix（live defect A2）**：按 D9 映射三处 `Throwable`，并把 task `Writer` 由 `null` 改为实参（当前 `JdkJavaCompiler.java:120`），使 javac 的崩溃文本不再被丢弃。
- [x] **Decision D5**：编码通道。实测规则是**条件式**的——仅当 file manager 的 charset **显式非 null** 时才压制 CLI `-encoding`；charset 为 null 时 CLI `-encoding` 生效（当前 `createFileManager` 正是传 null，故现状下 CLI 通道可用）。须裁定本模块走哪一条，并明确"不把编码放进 options"或"不放进 file manager"，**禁止两条都设**（会静默取其一且无任何测试能发现）。同时须记录：内存输入（`SimpleJavaFileObject` 覆写 `getCharContent`）**完全绕过编码**，非法编码名在内存路径上无任何可观测后果——编码通道只对磁盘输入有意义。
- [x] **Decision D11 的 Phase 2 子集**：D11 的完整开关行为矩阵在 Phase 4 产出，但 Phase 2 已消费的开关（输出目录、编码）行为须在 Phase 2 就实测落表，表体写在 `ai-dev/design/nop-javac/`（Phase 4 续写同一张表，**不新建第二张**）。这样 Phase 2 不依赖尚未产出的 D11。
- [x] 支持以**磁盘源文件路径集合**为输入。
- [x] 支持指定磁盘输出目录，由 javac 自行写出 class。
- [x] 支持显式 `release`。
- [x] 支持编码指定（按 D5 裁定的那一条通道）。
- [x] 支持源码路径。
- [x] 补测试：文件式输入 + 磁盘输出，断言 `.class` 落在输出目录的预期位置。
- [x] 补测试：**字节一致性**——同一份源码分别走内存路径与文件式路径（同组选项），断言产物字节一致。**前提**：两条路径必须用同一组选项；一旦文件式路径带 `--release`，字节会不同（`--release` 还改变系统 API 可见性 → 常量池差异），此时须改为断言"两者都能加载且行为一致"。
- [x] 补测试：`release` 生效——按字节读取产出 class 文件偏移 6–7 的大端无符号 short 得到 major version，断言其随 `release` 变化。**取值范围必须选在 CI（JDK 21）与本机（JDK 26）都存在的版本**（实测 `11 → 55`、`17 → 61`），不得使用只在单一 JDK 上存在的版本。
- [x] 补测试：编码生效——**必须是能区分的对照，且断言面不得是 `call()`**（实测 `call()` 在编码错误时仍返回 true，且解码级诊断不进 collector）。采用形态 (a)：加载产出类并断言其字符串常量与源码字面量**逐字符相等**（实测 `-encoding UTF-8` → 常量池为 UTF-8 字节；`-encoding ISO-8859-1` → 常量池为 Latin-1 字节，二者可区分）。**并须裁定**：解码失败时是否允许产出 class 文件——实测 javac 允许，故必须由本模块决定是否升级为失败；若升级为失败，须与 D5/D9 的错误契约一致。
- [x] 补测试（**live defect A 的回归测试**）：非法 `-encoding` 取值（不存在的字符集名）经**磁盘输入**驱动时，断言 `isSuccess()` 为 false **且**诊断中可读到编码类错误。**注意实测形态差异**：磁盘输入下该场景**输出目录不会被创建、无任何产物**（不是"产物损坏"）；断言面必须与此一致。
- [x] 补测试（**live defect A 的回归测试**）：磁盘源文件含声明编码不可映射的字符时，断言 `isSuccess()` 为 false 且诊断可读，**不得**产出 class。**不得依赖 `System.err` 捕获**（Non-Goals 明确排除）。
- [x] 补测试：非法 `-target` 取值（过旧）时抛出的 `IllegalStateException` 被映射为**带错误码的 NopException**。
- [x] 补测试：选项冲突（`release` 与 `source/target` 同时给）时，`getTask` 阶段的 `IllegalArgumentException` 被映射为**带错误码的 NopException**，而非未包装的运行时异常。
- [x] 补测试：输出目录行为符合 D4 裁定——含"不存在的多层目录"、"空串"、"指向已存在的普通文件"、"指向普通文件的子路径"四形态。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D3 / D4 / D5 / D9 四项 Decision 有书面裁决与理由；D11 的 Phase 2 子集已实测落表
- [x] live defect A 已修复：三项前置自校验（字符集名合法性、源文件在声明编码下可解码、输出目录形态）各存在且**各有一个经编译入口驱动的单测**；两个回归场景（磁盘 + 非法编码名、磁盘 + 不可映射字符）均断言失败且**不产出 class**，且测试不依赖 `System.err` 捕获
- [x] live defect A2 在**文件式入口**已修复：`getTask` / `call` 两处无未包装 `Throwable` 逃逸，三个实测异常形态（`getTask` 的 `IllegalArgumentException`、`call` 的 `IllegalStateException`、javac 内部 `Error`）各有断言异常类型的单测。**内存路径按 D9 裁定不做包装**，其残余见 `Deferred But Adjudicated`
- [x] Phase 1 的回归护栏全部仍绿：`TestJavaCompileTool` 的既有 7 个用例 + Phase 1 新增的接缝/落盘一致性/字节内容/`codeOfConcern*` 用例（内存路径行为未变）
- [x] `nop-core` / `nop-ioc` / `nop-xlang-java` 三个模块测试仍通过
- [x] **端到端验证**：从"给定一组磁盘源文件路径 + 选项"到"输出目录出现预期 `.class`、且编译结果可经既有 `getErrorMessage()` 读出"的完整路径在测试中跑通（结构化诊断面的端到端由 Phase 3 承担，见 Rule #22）
- [x] **接线验证**：新增文件式编译入口经由 `JdkJavaCompiler` 的 **public 编译面**被调用（不是测试自建内部对象、不是绕过入口直接调底层），由本 Phase 的文件式编译测试自身即可判定
- [x] **端到端补充**：文件式输入下诊断源码片段可读——复验 `instanceof` 修复在**磁盘输入**下确实取到源码，且 `codeOfConcern*` 非空。**实现要点**：源码片段抽取被提取为两条编译路径**共享**的 `JavaSourceSnippet`；此前它只存在于 `CompileResultMessage`（仅内存路径可达），而内存路径上 `instanceof SimpleJavaFileObject` 恒为 true，磁盘路径恒为 false——即修复在磁盘路径上**从未被验证**。测试 `testFileInputDiagnosticsCarryReadableSourceSnippet`
- [x] **无静默跳过**：`getTask` 阶段失败、输出目录不可写/为空串/指向普通文件、编码不可用等分支均显式抛带错误码异常或返回失败结果，不返回空结果、不交给 javac 静默处理
- [x] 每个新增开关都有"生效"与"非法取值"两类测试；后者按 D9 断言为带错误码异常
- [x] `ai-dev/design/nop-javac/` 已记录本 Phase 的新增能力、裁决与 D11 子表
- [x] 错误码一致性：`nop.err.javac.*` 中英 key 集合与占位符集合逐条一致（9 码）；**不使用 `git diff --exit-code` 作门禁**（本计划刻意修改这两份 yaml，working tree 必然 dirty，该门禁结构上不可满足），理由同 Phase 1
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **owner-doc**：新增能力改变了模块的编译面，`ai-dev/design/nop-javac/` 已同步；`docs-for-ai/` 无既有引用，判定 `No docs-for-ai update required`（同 Phase 1 结论，Rule #17）

### Phase 3 - 结构化诊断访问面

Status: completed
Targets: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/`、`ai-dev/design/nop-javac/`

- Item Types: `Decision | Feature | Fix | Proof`

- [x] **Decision D10**：诊断以什么形态暴露。约束事实：`JavaCompileResult` 是 `final`；`messages` 列表为 private 且无访问器；`CompileResultMessage` 的字段已是 public；服务侧需要可序列化（现有 `JavaSourceCode` 是 `@DataBean` + `@JsonProperty`）。须裁定：诊断列表的访问器形态、是否提供只读视图、诊断类型是否标注为可序列化数据bean、以及**磁盘与内存两种输入下的文件标识统一取值规则**（当前内存路径是 `string:///` URI）。**另须覆盖 Phase 2 建立的原始诊断 Writer 通道**：实测该通道**对解码级/字符集级诊断捕获为 0 字节**，它只能承载 javac 的崩溃文本。因此 D10 必须规定：结构化诊断面以**前置自校验产生的诊断**（Phase 2 的三项自校验须产出结构化诊断，而非只抛异常）为失败来源之一，否则 Phase 2 修好的假绿在 Phase 3 会**重新静默**。这是 Phase 3 相对 Phase 2 的硬性衔接要求。
- [x] 诊断以结构化形式暴露：问题级别、源文件标识、行号、列号、消息、编译单元标识。
- [x] **Fix**：确认 Phase 1 的 `instanceof SimpleJavaFileObject` 修复在**文件式输入**下确实取到源码片段（Phase 1 的验证在内存输入下做的，此处需在文件式输入下复验）。
- [x] **Fix**：确认 live defect A 的修复在**结构化诊断面**上仍成立——即"仅 `System.err` 可见"的失败不得在结构化面中表现为"零诊断 + `isSuccess()` 为 true"。即前置自校验的三项失败必须各自在结构化诊断面上可读。
- [x] 保留现有 `getErrorMessage()` / `toString()` 的输出语义，但不得成为获取结构化信息的唯一途径。
- [x] 补测试：含语法错误与语义错误的源码，断言结构化字段（级别 / 文件标识 / 行 / 列）与 javac 报告一致。
- [x] 补测试：磁盘文件输入时诊断携带真实文件路径（不是 `string:///` 形态）。
- [x] 补测试：源码内容不可得时**有日志**且按 D10 裁定的占位语义返回，`codeOfConcern*` 为空，不静默。
- [x] 补测试：走原始诊断通道的失败（非法 `-encoding` 取值）在结构化面上可读，不表现为"零诊断 + 成功"。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D10 有书面裁决与理由
- [x] 结构化诊断的每个字段都有对应测试断言
- [x] 文件式输入下源码片段可读（`testFileInputDiagnosticsCarryReadableSourceSnippet`）；源码不可得时返回占位语义且 `codeOfConcern*` 为**空串**（`testUnreadableSourceYieldsPlaceholderWithoutSlicingIt` / `testReadFailureIsNotSilent`）
- [x] 现有 `getErrorMessage()` / `toString()` 行为未被破坏（Phase 1 回归护栏仍绿）
- [x] **接线验证**：新增诊断访问面可被**模块外**的调用方使用——以一个位于 `io.nop.javac.jdk` 之外的测试或真实消费者读取结构化诊断并断言成功（验证可见性/可访问性，不只是类型存在）
- [x] **无静默跳过**：诊断收集异常或源码不可读时不静默丢弃，进入日志；原始诊断通道的内容已并入结构化面，不存在"结构化面看不到失败"的分支
- [x] `ai-dev/design/nop-javac/` 已记录结构化诊断的字段契约与文件标识语义
- [x] 本 Phase 不新增错误码，故无 i18n 同步项（显式声明）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **端到端验证 N/A**：本 Phase 只增加诊断读取面，不新增编译路径；端到端由 Phase 5 承担（已显式声明）
- [x] **owner-doc**：若 D10 把结构化诊断面升级为对外契约，则新增 `docs-for-ai/` 模块页并同步 `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md`；否则显式写 `No docs-for-ai update required`（Rule #17）

### Phase 4 - 注解处理、隐式编译与模块路径控制

Status: completed
Targets: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/`、`ai-dev/design/nop-javac/`

- Item Types: `Decision | Feature | Proof`

- [x] **Decision D7**：注解处理默认值。实测前提：`nop-javac` 的 test classpath 上**有 0 个注解处理器**（37 个 classpath 条目，无处理器服务文件）；处理器只经 `annotationProcessorPaths` 进处理器路径，**不进 classpath**；且 JDK 23+ 在无 `-proc` 标志时默认 `-proc:none`（实测），因此"配置了处理器路径的 12 个 pom"里实际只有 2 个（`<proc>full</proc>`）真正启用处理。须分两栏裁定："配置"与"实际启用"的差异如何写进 owner 契约。选项：(a) 保持现状并显式化；(b) 新增能力默认关闭、既有路径维持现状；(c) 其他。**任何改变既有内存路径行为的选项都需先给出影响分析。**
- [x] **Decision D11**：逐开关的校验责任划分（续写 Phase 2 的同一张矩阵）。实测表明部分非法取值 javac **不做校验或仅静默**（`-d ""`；classpath 路径不存在；`-s` 任意值），部分在选项解析阶段失败（`--release` 与 `-source` 冲突），部分完全绕开结构化通道（`-encoding` 非法值）。须补全一张**实测的开关行为矩阵**（每个开关 × 正常值 / 缺失路径 / 非法枚举值 / 未知选项），据此裁定每个开关由 javac 校验还是由本模块自校验。**不得凭推测写测试。**
- [x] 支持显式指定注解处理开关。
- [x] 支持指定处理器类集合与处理器路径。
- [x] 支持显式指定隐式编译策略（对应 `-implicit:`）。默认语义须写清。**注意实测：`-implicit:none` 是静默不生成隐式 class，不是报错**——本仓 `ai-dev/design/nop-incremental-build/01-architecture-baseline.md` §12.2 现写作"一旦需要源码路径上的文件即报错"，需一并纠正，否则下游会按错前提设计回退。
- [x] 支持指定生成源目录。
- [x] 支持模块路径。
- [x] 支持自定义参数透传。
- [x] **Proof**：补隐式编译行为测试——按实测形态断言：`-implicit:none` 时源码路径上的隐式文件**不产出 class**（且不报错、零诊断），`-implicit:class` 时产出。断言面为输出目录内容。
- [x] **Proof**：补注解处理对照测试。**约束**：不改 pom、classpath 上无处理器。**可行形态（已实测）**：测试处理器为 `public static` 嵌套类，`extends AbstractProcessor`（**不得直接 `implements Processor`**——JDK 26 上 `Processor` 已有抽象的 `getCompletions`），**必须显式覆写 `init(ProcessingEnvironment)` 并调用其父类实现**（否则 `processingEnv` 为 null 且 `getFiler()` NPE），编译产物落 `nop-core-framework/nop-ioc/target/test-classes`；测试时以「`-processorpath` = `nop-kernel/nop-javac/target/test-classes` + `-processor <Outer>$<Inner>` + `-proc:full` + `-s <临时生成源目录>`」加载。**必须显式 `-processor` 命名唯一处理器**：实测 `-proc:full` + `-processorpath` 在**没有** `META-INF/services/javax.annotation.processing.Processor` 时**不会**运行处理器，而本计划禁止放服务文件。**必须用一次性 `boolean` 闩锁做 round 守卫**：只判 `roundEnv.processingOver()` 不足以防重复创建——最终注解轮仍会进入并二次创建，抛 `FilerException: Attempt to recreate a file` 并被处理器报为 ERROR（造成假编译失败；其"修法"往往变成吞异常，违反 Rule #24）。**必须给 `-s`**：实测未给 `-s` 时处理器生成的 `.java` 会落进 `-d` 输出目录。对照断言：`proc:none` 时不产生生成物、`proc:full` 时产生。**禁止**把处理器服务文件放进测试资源（那会经 `getDefaultClassPaths()` 改变本模块全部既有测试的 javac 行为）。
- [x] **Proof**：补非法取值测试——按 D11 矩阵逐开关断言，javac 不校验的开关必须由本模块自校验并显式失败。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D7 / D11 有书面裁决与理由；D11 的开关行为矩阵已实测产出并记录（不得是推测表），且与 Phase 2 的子表是**同一张表**
- [x] D7 的影响分析已记录（明确说明对 `GenAopProxy` 生产路径是否有行为变化，并把"配置处理器路径"与"实际启用处理"的 2-vs-12 差异写入 owner 契约）
- [x] 每个新增开关都有"生效"与"非法取值"两类测试，且断言形态与 D11 矩阵一致
- [x] `nop-core` / `nop-ioc` / `nop-xlang-java` / `nop-cli-core` 四个模块测试仍通过
- [x] 测试处理器为测试树内嵌套类 + 显式 `init()` + 一次性 `boolean` round 闩锁，**测试资源中无处理器服务文件**（`grep` 验证），且该测试显式 `-processor` 命名（不依赖发现）、显式给 `-s`（不在 `-d` 目录留生成源码）
- [x] `ai-dev/design/nop-incremental-build/01-architecture-baseline.md` §12.2 关于 `-implicit:none` 的表述已纠正
- [x] **接线验证**：新增开关确实作用于真实编译调用（通过产物差异或诊断差异观测），不是仅存在于选项对象上
- [x] **无静默跳过**：所有未支持/未实现的选项分支抛带错误码异常；D11 判定为"本模块自校验"的开关确有校验代码与测试
- [x] `ai-dev/design/nop-javac/` 已记录全部新增开关、默认值与校验责任划分
- [x] 错误码一致性：`nop.err.javac.*` 中英 key 集合一致（不使用 `TestGenErrorCodesYaml` 作门禁，理由同 Phase 1）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **端到端验证 N/A**：本 Phase 只增加编译开关，端到端由 Phase 5 承担（已显式声明）
- [x] **owner-doc**：全部新增开关的默认值与校验责任已写入 `ai-dev/design/nop-javac/`；`docs-for-ai/` 无既有引用，判定 `No docs-for-ai update required`（Rule #17）

### Phase 5 - 编译器复用与端到端就绪验证

Status: completed
Targets: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/`、`ai-dev/design/nop-incremental-build/`

- Item Types: `Decision | Feature | Proof`

- [x] **Decision D8**：复用策略形态与默认态。**默认态必须写死为"每次新建"（等价今天）**——默认态若改为复用，即构成对 `GenAopProxy` 生产路径的行为变更，与本计划的兼容性约束冲突。另须写明实测约束：`getSystemJavaCompiler()` **每次返回不同实例**，因此复用必须显式持有字段；且复用必须**只复用编译器实例、每轮新建 file manager 并在 finally 关闭**——若改为复用 file manager，javac 内部上下文会跨轮污染。**注意：此裁定以 Phase 1 已裁定的 file manager 工厂缝为前置。**
- [x] 实现 acquire / release 生命周期，关闭责任明确（复用时不得提前关闭，释放时不得泄漏句柄）。
- [x] **Proof**：补句柄释放断言测试——用**可注入计数的 file manager**（依赖 Phase 1 的接缝与测试落位裁定）断言 `close()` 的调用次数与时机（确定性、跨平台）。**不采用"Windows 上 JAR 被锁定"作为断言面**：该语义在 macOS 上不存在，用 rename/delete 或 fd 计数验证会得到恒真结果，即本仓历史反复出现的假测试模式。Windows 语义只作为文档说明记录。
- [x] **Proof**：补多轮编译隔离测试——同一实例连续多轮编译，前一轮产物不影响后一轮，且诊断不串轮。
- [x] **Proof**：补非法状态转换测试——未 acquire 就 release、重复 release 的行为是显式失败而非静默。
- [x] **端到端就绪验证**：新增一条测试，模拟 AI 编译服务的最小调用形态——按 effective-pom 等价语义设置选项（`release` + classpath + 输出目录 + 隐式编译策略）→ 编译一组磁盘源文件 → 从输出目录读取产物 → 用**结构化诊断**（Phase 3 交付面）断言结果。该测试即服务后续落地时的能力验收基准。
- [x] 记录本 Phase 结束时 `nop-javac` 的能力矩阵（已具备 / 仍缺），作为 `nop-incremental-build` 的输入。
- [x] 按能力矩阵更新 `ai-dev/design/nop-incremental-build/01-architecture-baseline.md` §12.1 的"仍缺"表述。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D8 有书面裁决，默认态为"每次新建"，且实测约束（实例不共享、file manager 每轮新建）已写入
- [x] 复用生命周期的每个状态转换都有测试；非法转换显式失败
- [x] `close()` 调用次数/时机有确定性断言（跨平台可观察）；"每轮新建"以**真正交给 `getTask` 的那个 manager 的实例身份**（`assertNotSame`）断言——只断言计数、或只断言测试替身自己的 delegate，都会让"跨轮缓存同一 manager 交给 javac"的实现恒真（经二次审计的 variant-B 变异实测确认）
- [x] **端到端验证**：上述"最小调用形态"测试跑通，覆盖选项设置 → 编译 → 产物落盘 → **结构化诊断**全链路（见 Rule #22）
- [x] **接线验证**：`GenAopProxy` 的真实 AOP 生成由 `nop-ioc` 的构建产物证明（Phase 1 起每 Phase 回归）；本 Phase 的最小调用形态测试覆盖新增入口到产物的完整链路
- [x] **Anti-Hollow**：新增的复用/释放路径确实被测试经由编译入口调用，不是孤立存在
- [x] **无静默跳过**：所有未实现分支显式抛错
- [x] `nop-javac` 模块测试通过；`nop-core` / `nop-ioc` / `nop-xlang-java` 测试通过
- [x] 能力矩阵已记录并被 `ai-dev/design/nop-incremental-build/` **§12.1 的能力矩阵表格**引用（该设计文档无 "Phase 1" 概念，不得引用不存在的章节名）
- [x] 本 Phase 不新增错误码，故无 i18n 同步项（显式声明）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **owner-doc**：`ai-dev/design/nop-javac/` 与 `ai-dev/design/nop-incremental-build/01-architecture-baseline.md` §12.1 均已按能力矩阵同步；`docs-for-ai/` 判定 `No docs-for-ai update required`（Rule #17）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Phase 1–5 全部 `completed`，且各自 Exit Criteria 全部勾选
- [x] D1 – D11 共十一项 Decision 均有书面裁决与理由，且与 owner 文档无冲突
- [x] `GenAopProxy` 生产路径行为未变（唯一可观察承载方为 `nop-ioc` 的 `exec:aop` 构建产物 + `TestAop`；`nop-codegen`/`nop-core`/`nop-xlang-java` 的门禁仅证明内存编译 API 与 `JavaCompileResult` 面）
- [x] 内存编译路径行为未变（由 Phase 1 回归护栏证明）
- [x] live defect A（绕开结构化通道的假绿）已修复，且经结构化诊断面验证仍成立（Phase 3 复验）
- [x] live defect B / B2（磁盘输入源码片段静默降级 + 占位符切片误导）已修复且两种输入形态均已验证
- [x] live defect C（`en` 份 i18n 缺 `nop.err.javac.invalid-class-name`）已修复
- [x] `nop-kernel/nop-javac/README.md` 的五处 public-contract drift 已修复
- [x] 每个新增能力都有对应单元测试（Rule #25），无"仅断言原有测试通过"的 Phase
- [x] 不存在被降级为 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 新增错误码已同步 `nop-cli-errors.i18n.yaml` 的**中英双份**：`nop.err.javac.*` 域内 **9 个码**（既有 3 + 本计划新增 6）key 集合与占位符集合逐条一致，且本计划对两份 yaml 的改动**只含 `nop.err.javac.*` 行**。验证方式是作用域 key 集合 + 占位符集合 diff，**不是** `git diff --exit-code`（本计划刻意修改这两份文件），**也不是** `TestGenErrorCodesYaml`（零断言生成器，抽取源是本地仓库的 stale jar）。**全文件其余 drift 不在本计划范围**（见 `Non-Blocking Follow-ups`）
- [x] `ai-dev/design/nop-javac/` 与 `ai-dev/design/README.md` 索引已同步；`ai-dev/design/nop-incremental-build/` §12.1 / §12.2 已按裁决同步
- [x] 独立子 agent closure-audit 已完成，`Closure` 段已写入证据
- [x] **Anti-Hollow Check**：closure audit 已验证 (a) 新增能力被真实编译入口调用，(b) 端到端路径连通，(c) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/40-nop-javac-file-based-compile-capability.md --strict` 退出码为 0（**注意：实测该命令在 `draft`/`in progress` 且 checklist 未勾选时也返回 0**，因此它**只在置 `completed` 前**才具约束力；不得把它当作执行期进度信号）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-javac --severity high` 退出码 0（`--module` 必须是含父目录的模块路径）。**注意：该工具的扫描范围是 `src/main`，不覆盖本计划大量新增的 `src/test` 代码**——因此 `src/test` 的空壳（空用例、零断言）由各 Phase 的 Exit Criteria 逐条覆盖，**不得依赖该工具兜底**
- [x] 本计划**新增/改动文件**的 import 顺序合规（`check-import-order.mjs` 全仓基线本已为红——实测 2227 errors / 1936 files——故按改动文件范围核对，不设全仓门禁）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw compile -pl nop-kernel/nop-javac -am`
- [x] `./mvnw test -pl nop-kernel/nop-javac -am`
- [x] `./mvnw test -pl nop-kernel/nop-core -am`
- [x] `./mvnw test -pl nop-core-framework/nop-ioc -am`
- [x] `./mvnw test -pl nop-kernel/nop-xlang-java -am`

> **门禁说明**：(1) 本仓库**没有** checkstyle 插件（全仓 pom 无 `checkstyle` 命中），门禁用仓内实际存在的脚本替代。(2) **不使用** `check-error-param-consistency.mjs` 作为门禁。**真实原因**：该工具**能**识别本模块使用的 `define(...)` 写法，且当前对 `nop-kernel/nop-javac` 报 **0 条**；其退出码 1 来自**全仓 34 条既有**违规（`nop-batch-*` / `nop-orm*` / `nop-graphql-orm` / `nop-db-migration` / `nop-search-lucene` / `nop-http-api`），属**全仓基线已红**，不是"对本模块结构性不支持"。**裁定**：新增错误码必须手工保证 `define` 的占位符与尾参 `ARG` 一一对应，并以重新生成 + 双份 yaml key 集合 diff 作为门禁。(3) `check-import-order.mjs` 的**全仓基线本已为红**，故只按本计划改动文件范围核对。

## Deferred But Adjudicated

### 跨 JVM / 外部 javac 进程派生

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 构建侧对 javac 有进程内与进程派生两套实现，但 AI 编译服务只需进程内一种；进程派生涉及进程生命周期管理与超时，属服务侧职责。
- Successor Required: `no`

### 与 plexus-compiler 抽象对齐

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 构建侧的配置抽象与本仓元数据模型不匹配；本计划只补齐能力，不引入该抽象。
- Successor Required: `no`

### 跨模块符号级增量（哪些文件需要重编译）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 `nop-incremental-build` 的作用域分析职责；本计划的产物是"能按给定集合编译"，集合如何算出不在此。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/` 下 `nop-incremental-build` 子系统 roadmap（须在收口时把该 roadmap 的具体文件名写实，不得只留目录名）

### 内存编译路径的未包装 `Throwable` 逃逸（D9 子裁定的残余）

- Classification: `watch-only residual`
- Why Not Blocking Closure: D9 的 `Throwable` 包装**只施加于新增的文件式入口**，不施加于既有 `compile(List<JavaSourceCode>, List)`。理由（**精确表述**）：内存路径的**选项面**是硬编码且合法的（`-source`/`-target` 取运行 JDK 版本、`-classpath` 由调用方给字符串），因此 `--release` 与 `-source` 冲突、`-target` 过旧、`-d` 指向普通文件这三类实测异常**在该路径上不可达**；施加包装会改变 `GenAopProxy` 的错误契约（它当前依赖未包装异常经 `NopException.adapt` 归一），构成对 AOP 生产路径的 live 行为变更。**已实测的可达失败（不在"不可达"之列）**：空源文件列表会从 `call()` 抛出未包装的 `IllegalStateException`（"无源文件"）——这是 javac 对空输入的既有行为，与本计划的开关面无关，故不因其未包装而判定本项阻塞收口。
- Successor Required: `yes`
- Successor Path: 若内存路径将来获得可设定选项面（使上述异常形态变为可达），必须在同一改动中施加包装并补断言异常类型的回归测试；该触发条件已写入 `ai-dev/design/nop-javac/README.md` §3.3。

### 拦截 `System.err` 以捕获解码级诊断

- Classification: `watch-only residual`
- Why Not Blocking Closure: 实测解码级/字符集级诊断**只经 `System.err`**，且**必须**在 javac 加载其 `Log` 类之前替换才能捕获（实测早期替换捕获 184B、晚期替换捕获 0B），属进程级全局状态、非线程安全，且对**内存输入完全不可达**（内存源码覆写 `getCharContent`，javac 从不解码）。live defect A 由**本模块三项前置自校验**（字符集名合法性、源文件可解码性、输出目录形态）覆盖，该契约由 Phase 2 Exit Criteria 逐项断言，与 D5/D9 的最终取值无关。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 把 `ai-dev/design/nop-incremental-build/` 的成本基线实测（构建骨架成本、负载敏感性）同步进构建/测试相关 owner 文档，作为后续构建性能讨论的共享基线。
- 根 pom 的 `exec-maven-plugin` 版本属性与 pluginManagement 声明不一致（属性 `1.6.0`，实际用 `3.0.0`）——属构建配置 drift，与本计划 scope 正交。
- `nop-cli-errors.i18n.yaml` 全文件的中英 key 集合不一致（实测 zh 1505 / en 1334：**173 条 zh-only** + 2 条 en-only，集中在 `nop.err.ai.*` / `nop.err.javac.*` 以外的模块）。本计划只修 `nop.err.javac.*`（live defect C，已在 Phase 1 落地）；其余 drift 属其他模块的 owner 责任，**已实测记录并排除在本计划范围外**，不在此静默吞掉。归类：`watch-only residual`，successor 见 `ai-dev/backlog/`（须在收口时写实具体 roadmap 文件名）。
- `ai-dev/design/nop-incremental-build/00-vision.md` §八 G10 与 `01-architecture-baseline.md` §12.1/§12.2 的修订目前是**未提交的工作树改动**；若被丢弃，D2 的反转目标将消失。收口时须确认这些改动已入库。
- `TestGenErrorCodesYaml` 有两个独立问题（均在本计划外）：(1) 抽取源是**本地仓库中的 `nop-javac` jar**，不先 `install` 会得到旧码集合；(2) 即使先 `install`，重跑也会把 zh-CN 份中约 29 行**属其他模块**的既有 drift 写入——它按 `ErrorCodeExtracter` 的注册类列表重写整个文件，因此任何一次运行都会把当时的全仓错误码状态固化进本文件。归类：`watch-only residual`（工具自身行为），后继归属：应改为只生成不覆盖、或补一个"只校验不重写"的模式。

## Closure

Status Note: 本计划把 `nop-javac` 从"只在内存编译字符串"改造成"可按构建侧语义把磁盘源文件编译到磁盘输出目录并给出结构化诊断"的编译底座。11 项 Decision 全部裁定并落入 `ai-dev/design/nop-javac/`；4 项 live defect（假绿、源码片段静默降级、占位符切片误导、i18n en 缺码）经修复 + 变异验证；内存路径与 AOP 生产链行为经三模块回归与产物级接线验证确认未变。经两轮独立 closure audit（均判 REOPEN，各发现 3–4 项未被真正钉住的"已修复"）后全部修复并复验。

Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 两轮独立 closure audit（均非实现 session）：`ses_ee18cee51ffe3eho4ey59HOHyI`（第一轮，判 REOPEN）、`ses_ee176f308ffeTMSz34TK5C8sCb`（第二轮，判 REOPEN）
- Audit Session: 同上 task_id；fix-and-verify 记录见 `ai-dev/logs/2026/10-09.md`
- Evidence:
  - **每条 Exit Criterion 的验证结果**：Phase 1–5 共 130 条全部 PASS。第二轮审计推翻并已修复 4 条虚标（见下"第二轮审计发现与处置"）。逐项 live code path 与测试名记录于各 Phase 的 `[x]` 条目正文内。
  - **每条 Closure Gate 的验证结果**（退出码均由本人复跑）：
    - `check-plan-checklist.mjs --strict` → **0**（PASS，153/153 已勾）
    - `scan-hollow-implementations.mjs --module nop-kernel/nop-javac --severity high` → **0**
    - `check-doc-links.mjs --strict` → **0**
    - `check-error-param-consistency.mjs` → 本模块 **0** 条违规（全仓 34 条既有在本计划外模块）
    - `check-import-order.mjs` → 本计划新增/改动文件 **0** 条（全仓基线 2227 errors / 1936 files 为既有）
    - `./mvnw -o test -pl nop-kernel/nop-javac` → **0**，**75/75** 绿
    - `./mvnw -o test -pl nop-kernel/nop-core` → **0**，508（2 skipped）
    - `./mvnw -o test -pl nop-core-framework/nop-ioc` → **0**，72
    - `./mvnw -o test -pl nop-kernel/nop-xlang-java` → **0**，501
    - `./mvnw -o test -pl nop-runner/nop-cli-core` → **0**，36（15 skipped）
    - **接线验证**（Phase 1 起每 Phase 复跑）：删除 `nop-core-framework/nop-ioc/target/test-classes/io/nop/ioc/aop/MyClass__aop.class` 后 `./mvnw -o test -pl nop-core-framework/nop-ioc -am` → BUILD SUCCESS，该 class 由 `exec:aop` → `GenAopProxy` → `JdkJavaCompiler.compile` → `saveGenerated` 重新生成（末次 11:01:31）。两轮审计各自独立复现。
    - **错误码一致性**：`nop.err.javac.*` 中英各 **9** 码，key 集合与占位符集合逐条一致；两份 yaml 的 diff **只含 `nop.err.javac.*` 行**（非 javac 行数 = 0）
  - **Anti-Hollow 检查**：
    - (a) 新增能力被真实编译入口调用 — 第二轮审计**独立编译并运行了一个 `io.nop.javac.jdk` 包外的外部消费者**，实测输出 `success=true / outputDir=… / diagnostics=[] / classOnDisk=true / reuseSuccess=true / failSuccess=false + 结构化诊断字段齐全`。全部新类型（`JavaCompileOptions` / `JavaDiagnostic` / `JavaSourceSnippet` / `JavaFileCompileResult` / `compileFiles` 三参重载）为 public 且包外可访问。
    - (b) 端到端路径连通 — `TestJdkJavaCompilerServiceE2E` 覆盖"设选项 → 编译 3 个磁盘源文件 → 输出目录读到 3 个 class → major version 断言 → 加载并断言方法返回值"，另两例覆盖失败面与三种 javac 静默形态。第二轮审计判定该测试"名副其实，非弱化版冒名"。
    - (c) 无空方法体 / 静默跳过 / no-op — 本计划为 in-scope 的死代码在 Phase 1 全部清理；第二轮审计另发现并已删除本计划自己引入的两个零调用方方法（`readMajorVersion` / `decode`）。
  - **变异验证共 18 项**（详见 `ai-dev/design/nop-javac/01-compile-switch-matrix.md` §13）。三项最关键的最终复验：
    - 恢复 `instanceof SimpleJavaFileObject`（live defect B）→ **1 failure**
    - 对占位符按诊断位置切片（live defect B2，替身诊断位置取 10/12/20）→ **2 failures + 2 errors**
    - 跨轮缓存同一 file manager 交给 javac（缝每轮仍调用）→ **5 failures**
  - **Deferred 项分类检查**：两项 `watch-only residual`（内存路径未包装 `Throwable`；拦截 `System.err`）与两项 `out-of-scope improvement`（跨 JVM 进程派生、plexus 抽象对齐）+ 一项 successor（符号级增量，属 `nop-incremental-build`）。逐项核对：无 in-scope live defect 被降级——4 项 live defect 全部已修复并经变异验证。
  - **第二轮审计发现与处置**（4 项全部修复并复验）：
    1. B2 测试恒真（替身诊断位置全为 0，`substring(0,0)` 与修复后输出逐字节相同）→ 位置改 10/12/20，变异变红
    2. file manager 身份断言恒真（断言的是测试替身自建 delegate）→ 生产代码加 `getLastTaskFileManager()`，断言真正交给 `getTask` 的对象身份，variant-B 变异变红
    3. D9 子裁定只写进 design 未写进 plan，且 Phase 2 EC 有与之矛盾的勾选项；"失败分支不可达"表述不准确（实测空源列表会抛未包装 `IllegalStateException`）→ 残余写入 `Deferred But Adjudicated`、EC 改为"仅文件式入口"、表述收窄为精确的选项面口径
    4. `git diff --exit-code` i18n 门禁结构上不可满足（实测退出码 1）且混入了 29 行其他模块 drift → 删除该门禁、改用作用域 key+占位符双 diff，并把 yaml 改动还原为只含 javac 行
    5. 附带修复：`fileManager.close()` "无 try/catch" 的事实性错误（该处理是既有的）、零调用方死代码、诊断位置倒置会抛 `StringIndexOutOfBoundsException`、design §3.4 重复段落、仓库 `.class` 污染
  - **文本一致性核对（Rule #19）**：`Plan Status`、5 个 Phase `Status`、各 Phase `Exit Criteria`、`Closure Gates`、本 `Closure` 段、`ai-dev/logs/2026/10-09.md` 收口记录五处一致。

Follow-up:

- **no remaining plan-owned work**。全部 153 条 checklist 已勾，11 项 Decision 已裁定，两项 `watch-only residual` 均带明确触发条件与后继归属。

## Open Risks

| 风险 | 说明 | 缓解 |
|------|------|------|
| 触碰 AOP 生产路径 | `GenAopProxy` 在**激活 `exec-maven-plugin` 的 139 个模块**的 `compile` 阶段运行（`nop-codegen` 自身不激活、自身无测试），改错会静默影响全仓 AOP 织入 | Phase 1 先落兼容性护栏；每 Phase 跑 `nop-core` / `nop-ioc` / `nop-xlang-java` 回归；D6 强制做影响分析 |
| 编译入口的错误传播变更 | 选项解析阶段失败目前会漏出未包装异常，破坏 `GenAopProxy` 的错误契约 | D9 强制裁定并要求带错误码包装 + `catch (Throwable)`（实测 javac 抛 `Error` 而非 `Exception`）；Phase 2 有专门测试断言异常类型 |
| 消费耦合均为传递依赖 | 消费方无一显式声明依赖，公开 API 变更半径大于表面 | Phase 1–5 每个 Phase 的回归清单覆盖全部消费模块 |
| 新增能力在服务落地前无人消费 | 可能变成死代码 | Phase 5 的最小调用形态端到端测试 + 能力矩阵交给 `nop-incremental-build` |
| owner-doc drift 面较大 | 模块 README 与 design 索引均已 drift，另有 i18n en 份缺码 | Phase 1 内一并修复并纳入 Exit Criteria |
| 解码级假绿的修复依赖自校验而非 javac 反馈 | 若 D5/D9 选错形态，`-d ""` / 非法 `-encoding` 一类仍会静默 | D11 矩阵逐开关实测；Phase 2 为每个非法取值形态配断言 |