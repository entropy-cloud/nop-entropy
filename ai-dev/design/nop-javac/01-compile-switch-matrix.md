# nop-javac 编译开关行为矩阵

**日期**：2026-10-09
**范围**：`io.nop.javac.jdk` 传递给 javac 的编译开关
**状态**：active（Phase 2 已填入输出目录 / 编码两行及源码路径；Phase 4 续写注解处理 / 生成源目录 / 模块路径 / 自定义透传 / 选项装配）

> **实测环境**：JDK 26.0.1（Zulu 26.30+11）。`-proc` 默认值、`javax.tools.Processor` 接口形状、
> 编码类诊断是否进入 `DiagnosticCollector` 三项**仅在该版本实测**，JDK 21 需重测后才可声称跨版本成立。
>
> **本表是实测结果，不是推测。** 每个单元格都来自实际调用 `CompilationTask.call()` 或
> `JavaCompiler.getTask()` 的观察。Phase 4 按此表补全剩余开关行，**续写本表而非新建第二张**。

---

## 一、列含义

| 列 | 含义 |
|----|------|
| 正常值 | 合法取值下的行为 |
| 缺失路径 | 路径类参数指向不存在的文件/目录 |
| 非法枚举值 | 语法合法但语义非法的取值 |
| 未知选项 | 拼写错误或不支持的开关名 |
| **校验责任** | `javac` = 交给 javac 校验；`模块` = 本模块必须在 `getTask` 之前显式校验 |

---

## 二、成功判据基线（全部开关共同的判定前提）

javac 的失败信号分布在**四个**互不等价的通道上。判定"这次编译是否失败"必须同时看这四处：

| 通道 | `call()` 返回值 | `DiagnosticCollector` | task `Writer` | `System.err` |
|------|----------------|---------------------|---------------|---------------|
| 语法/语义错误 | `false` | **含 ERROR** | 空 | 空 |
| 选项冲突（`--release` + `-source`） | `getTask` 抛异常 | — | — | — |
| 目标版本过旧（`-target 8`） | `call()` 抛异常 | — | — | — |
| javac 内部错误（`-d` 指向普通文件） | `false` | **零诊断** | **有文本**（含 `java.lang.Error`） | 空 |
| 解码级失败（非法编码名 / 不可映射字符） | **`true`** | **零诊断** | **0 字节** | **有文本** |

**实测结论**：

1. 不存在"`call()` 返回 `true` 且 collector 含 ERROR"的输入（约 50 组输入验证，含 13 组注解处理场景）。故给 `isSuccess()` 增加"无 ERROR 诊断"条件是**空操作**。
2. task `Writer` 对**解码级**失败捕获 0 字节；它只承载 javac 内部的崩溃文本，而那类场景 `call()` 本就为 `false`。
3. 解码级失败的唯一信号是进程级 `System.err`，且必须在 javac 加载其 `Log` 类**之前**替换才能捕获——非线程安全的全局状态，本设计明确排除该方案。

**因此**：解码级失败必须由本模块在 `getTask` 之前自校验拦截，事后无法从 javac 反馈中恢复。

---

## 三、输出目录（`-d`）

| 取值形态 | `call()` | collector ERROR | task Writer | 产物 | 校验责任 |
|----------|----------|----------------|-------------|------|----------|
| 正常目录 | `true` | 0 | 空 | 正常写出 | `javac` |
| **多层不存在的目录** | **`true`** | 0 | 空 | **自动创建并编译成功** | `模块`（裁定见下） |
| **空字符串** | **`true`** | **0** | 空 | 无 | **`模块`（强制）** |
| 已存在的**普通文件** | `false` | **0** | 有文本（`java.lang.Error`） | 无 | **`模块`（强制）** |
| 普通文件下的子路径 `<file>/sub` | `false` | 1 | 空 | 无 | `模块` |

**裁定（D4）**：与 javac 对齐自动创建多层目录；但空串形态必须由本模块显式拒绝——javac 对它**完全静默**（返回 `true`、零诊断），是典型的静默 no-op。

**与 `saveGenerated` 的一致性**：`saveGenerated` 底层用 `FileHelper.writeBytes`，会自动建父目录。因此"输出目录缺失 → 自动创建"是本模块既有语义，选项对象须保持同向，不得一个自动建、一个报错。

---

## 四、编码（`-encoding`）

**条件式规则（实测）**：仅当 file manager 的 charset **显式非 null** 时才压制 CLI `-encoding`；charset 为 `null` 时 CLI `-encoding` 生效。当前 `createFileManager` 传的是 `getStandardFileManager(null, null, null)`，即 charset 为 `null`——故 CLI 通道可用。

**通道互斥（强制）**：不得同时设置 options `-encoding` 与 file manager charset。两条都设会静默取其一，且**没有任何测试能发现**究竟取了哪条。

| 输入形态 + 取值 | `call()` | collector ERROR | task Writer | `System.err` | 产物 | 校验责任 |
|----------------|----------|----------------|-------------|-------------|------|----------|
| **磁盘**输入 + 合法编码名 | `true` | 0 | 空 | 空 | 正常 | `javac` |
| **磁盘**输入 + 非法编码名 | **`true`** | **0** | **0 字节** | 有（"不支持的编码"） | **输出目录未创建、无任何产物** | **`模块`（强制）** |
| **磁盘**输入 + 源码含不可映射字符 | **`true`** | **0** | **0 字节** | 有（"不可映射字符"） | **产出，内容静默损坏** | **`模块`（强制）** |
| **内存**输入 + 非法编码名 | **`true`** | 0 | 0 字节 | **空** | **正常产出（编码被完全忽略）** | 不适用 |
| 未知选项 `-encodin` | `false` | 1 | 空 | 空 | 无 | `javac` |

**裁定（D5）**：走 **CLI `-encoding` 通道**，file manager charset 保持 `null`。理由：与构建侧只走 file manager 通道不同，本模块现状 charset 为 `null`，CLI 通道已可用且改动面更小；两条都设会产生静默歧义。

**内存输入的记录**：内存源码覆写 `getCharContent`，javac **从不解码**内存源码——编码通道只对磁盘输入有意义。前置自校验的可解码性检查因此只对磁盘源文件生效。

**版本 major 观测值**（读取 class 文件偏移 6–7 的大端无符号 short）：

| `--release` | major version |
|-------------|---------------|
| 11 | 55 |
| 17 | 61 |

二者是 CI（JDK 21）与本机（JDK 26）都存在的版本，可作为跨版本安全的断言取值。

---

## 五、隐式编译（`-implicit:`）

| 取值 | `call()` | collector ERROR | 产物 | 校验责任 |
|------|----------|----------------|------|----------|
| `-implicit:none` + 源码路径上的隐式文件 | **`true`** | **0** | **静默不生成**隐式文件的 class | `javac` |
| `-implicit:class` | `true` | 0 | 生成 | `javac` |
| 未知枚举值 `-implicit:bogus` | `false` | 1 | 无 | `javac` |

**⚠️ 上游表述纠正**：`ai-dev/design/nop-incremental-build/01-architecture-baseline.md` §12.2 现写作"一旦需要源码路径上的文件即报错"——**这是错的**。`-implicit:none` 是静默不生成、零诊断。任何按"隐式扩散会响亮失败"设计回退的下游都建立在错误前提上。

**可观测性结论**：隐式编译策略**无法**通过诊断观测，只能通过**输出目录内容**观测。这是本设计中"开关生效"类测试的断言面选择依据。

---

## 六、注解处理（`-proc:` / `-processor` / `-processorpath`）

> **选项形态更正（实测踩坑）**：`-implicit:` 与 `-proc:` 是**单 token 开关**，必须拼成
> `-implicit:class` / `-proc:full`。拆成两个元素（`-implicit:` + `class`）会被 javac 判为
> **"无效的标记"** 并在 `getTask` 阶段抛 `IllegalArgumentException`。

### 6.1 "配置"与"启用"是两件事

仓内 12 个 pom **配置**了处理器路径（10 个 `annotationProcessorPaths` + 2 个 `<proc>full</proc>`），但实测 JDK 26 下其中**仅 2 个真正启用处理**——JDK 23+ 在无 `-proc` 标志时默认 `-proc:none`。owner 契约必须分两栏表述，不能只写"12 个模块启用处理器"。

### 6.2 行为矩阵

| 取值 | `call()` | 处理器是否运行 | 校验责任 |
|------|----------|---------------|----------|
| 处理器在 classpath + 无 `-proc` 标志 | `true` | **否**（JDK 23+ 默认 `-proc:none`） | — |
| `-proc:full` + 处理器在 classpath + 无 `-processor` | `true` | **否** | — |
| `-proc:full` + `-processor <name>` + 处理器在 classpath | `true` | **是** | `javac` |
| `-proc:full` + `-processorpath <dir>` + **无** `-processor` + **无** `META-INF/services/javax.annotation.processing.Processor` | `true` | **否** | — |
| 同上但**有**服务文件 | `true` | **是** | — |
| 处理器抛 `RuntimeException` / `Error` | **抛出** | — | **`模块`（映射为带错误码异常）** |
| 处理器报 ERROR（普通 / null 元素 / 末轮迟到） | `false` | 是 | `javac` |
| `-processor` 命名不存在的处理器 | `false` | 否（collector 1 条 ERROR） | `javac` |
| **处理器不在 `-processorpath` 上，但在宿主 JVM 的 `java.class.path` 上** | `true` | **是** | — |
| 处理器生成不可编译的源码 | `false` | 是 | `javac` |
| `-proc:only` | `true` | 是 | `javac` |

**⚠️ 处理器发现的回退面（实测，决定了断言面选型）**：`-processorpath` 指向**空目录**、而处理器
在宿主 JVM 的 `java.class.path` 上时，javac 照常发现并运行该处理器（`call()==true`、零诊断）。
因此"隔离 classpath 后观察处理器是否被发现"这种断言在**进程内恒为真**——是假测试面。
`-processorpath` 与 `--module-path` 这类开关的"生效"只能以**选项装配**为断言面，
不能用处理器是否运行来断言（处理器开关 `-proc:` / `-processor` 才可用运行行为断言）。

**服务文件前提（实测，容易误判）**：`-proc:full` + `-processorpath` 在**没有** `META-INF/services/javax.annotation.processing.Processor` 时**不会**运行处理器；补上该服务文件后才会运行。因此在本设计的"禁止放服务文件"约束下，**必须显式 `-processor` 命名唯一处理器**——不能依赖发现。

### 6.3 测试处理器契约（Phase 4 对照测试的实现约束）

| 约束 | 原因 |
|------|------|
| `extends AbstractProcessor`，**不得**直接 `implements Processor` | JDK 26 上 `Processor` 已有抽象的 `getCompletions`，直接实现无法跨 JDK 21/26 编译 |
| **必须显式覆写 `init(ProcessingEnvironment)` 并调其父类实现** | 否则 `processingEnv` 为 `null`，`getFiler()` NPE |
| **必须用一次性 `boolean` 闩锁做 round 守卫** | 只判 `roundEnv.processingOver()` **不足以**防重复创建：最终注解轮仍会进入并二次 `createSourceFile`，抛 `FilerException: Attempt to recreate a file`，并被处理器报为 ERROR——造成**假编译失败**。而"修法"往往是吞异常，属静默 no-op |
| **必须显式 `-processor` 命名** | 见 6.2 的服务文件前提 |
| **必须给 `-s`** | 未给 `-s` 时，处理器生成的 `.java` 会落进 `-d` 输出目录——正是 `-s` 能力本身要防的污染 |
| **禁止**把处理器服务文件放进测试资源 | 会经 `getDefaultClassPaths()` 改变本模块**全部**既有测试的 javac 行为 |

---

## 七、源码路径（`-sourcepath`）

| 取值 | `call()` | collector ERROR | 产物 | 校验责任 |
|------|----------|----------------|------|----------|
| 正常目录 | `true` | 0 | 正常 | `javac` |
| **不存在的路径** | **`true`** | **0** | **正常**（javac 不校验 classpath/sourcepath 类路径是否存在） | `模块` |
| 未知选项 | `false` | 1 | 无 | `javac` |

---

## 八、生成源目录（`-s`）

| 取值 | `call()` | collector ERROR | 校验责任 |
|------|----------|----------------|----------|
| 正常目录 | `true` | 0 | `javac` |
| **任意值（不存在 / 无写权限）** | **`true`** | **0** | **`模块`（javac 完全不校验）** |
| 未知选项 | `false` | 1 | `javac` |

---

## 九、模块路径（`--module-path`）

| 取值 | `call()` | collector ERROR | 校验责任 |
|------|----------|----------------|----------|
| 正常目录 | `true` | 0 | `javac` |
| 不存在的路径 | 待 Phase 4 实测填表 | — | 待定 |
| 未知选项 | `false` | 1 | `javac` |

---

## 十、`--release` 与 `-source` / `-target`

| 取值组合 | 结果 | 校验责任 |
|----------|------|----------|
| `--release N` 单独 | `call()` 返回 `true`，产物 major 随 N 变化 | `javac` |
| `--release N` + `-source M` | **`getTask` 抛 `IllegalArgumentException`** | `模块`（映射为带错误码异常） |
| `-target 8` 单独（相对运行源版本过旧） | **`call()` 抛 `IllegalStateException`** | `模块`（映射为带错误码异常） |
| `-source 99` 单独 | **`getTask` 抛 `IllegalArgumentException`** | `模块` |

**注意阶段差异**：`--release` 与 `-source` 的冲突在 `getTask` 阶段暴露（`call()` 根本不执行）；`-target` 过旧在 `call()` 阶段暴露。两者的捕获位置不同，实现时不可只包一处。

---

## 十一、自定义参数透传

| 取值 | `call()` | collector ERROR | 校验责任 |
|------|----------|----------------|----------|
| 合法 javac 开关 | 生效 | 0 | `javac` |
| 未知选项 | `false` | 1 | `javac` |

---

## 十二、源文件存在性

| 取值 | `call()` | collector ERROR | 校验责任 |
|------|----------|----------------|----------|
| 正常文件 | `true` | 0 | `javac` |
| **不存在的路径** | `false` | 诊断归到 file manager，`JavaDiagnostic.file` 为 null | **`模块`**（补带错误码诊断） |

---

## 十三、本模块必须自校验的形态汇总

以下形态 javac **静默接受**（`call()` 返回 `true`、collector 零诊断、产物照旧或干脆不产出），
必须由本模块在 `getTask` 之前显式失败，不得交给 javac：

| # | 形态 | 已落地 | 测试 |
|---|------|--------|------|
| 1 | 输出目录为空字符串 | Phase 2 | `testEmptyOutputDirIsRejectedExplicitly` |
| 2 | 输出目录指向已存在的普通文件 | Phase 2 | `testOutputDirPointingToRegularFileIsRejectedExplicitly` |
| 3 | 输出目录父路径为普通文件 | Phase 2 | `testOutputDirUnderRegularFileIsRejectedExplicitly` |
| 4 | 编码名不存在（磁盘输入） | Phase 2 | `testInvalidEncodingNameFailsInsteadOfSilentNoOp` |
| 5 | 源文件在声明编码下不可解码 | Phase 2 | `testUndecodableSourceFailsInsteadOfProducingCorruptClass` |
| 6 | sourcepath 路径不存在 | Phase 2 | `testNonExistentSourcePathIsRejectedByModuleNotJavac` |
| 7 | 源文件不存在 | Phase 2 | `testMissingSourceFileIsRejected` |
| 8 | `getTask` / `call` / file manager 关闭三处的任何 `Throwable`（含 `java.lang.Error`） | Phase 2 | `testReleaseAndSourceConflictMapsToNopException`、`testInvalidTargetMapsToNopException` |
| 9 | classpath 路径不存在 | Phase 4 | `testMissingClassPathEntryRejectedByModuleNotJavac` |
| 10 | 生成源目录指向普通文件 | Phase 4 | `testGeneratedSourceDirPointingToRegularFileIsRejected` |
| 11 | 模块路径不存在 | Phase 4 | `testMissingModulePathRejectedByModuleNotJavac` |
| 12 | 隐式编译策略非法枚举值 | Phase 4 | `testInvalidImplicitPolicyRejectedBeforeJavac` |
| 13 | 注解处理开关非法枚举值 | Phase 4 | `testInvalidProcModeRejectedBeforeJavac` |
| 14 | 处理器类不存在 | `javac` 负责 | `testMissingProcessorClassFailsWithDiagnostic` |

**生成源目录的处置与输出目录同向**：不存在的目录**自动创建**（javac 对 `-d` 也是自动建，
`saveGenerated` 底层的 `FileHelper.writeBytes` 同样自动建父目录），只有"存在但不是目录"与
"不可写"才是显式失败。见 `testMissingGeneratedSourceDirIsAutoCreated`。

**变异验证**：上表 13 项自校验 + 各开关的选项装配已逐个做过变异探针（移除对应代码 → 目标测试变红
→ 恢复并 grep 确认可执行行真实存在），确认每项都有测试真正锁住，不是恒真断言。探针结果：

| 变异 | 结果 |
|------|------|
| 去字符集名合法性校验 | 1 error |
| 去可解码性校验 | 2 failures |
| 去空串输出目录校验 | 1 failure |
| `getDiagnostics()` 返回空列表 | 1 failure |
| 去 `-implicit` 选项装配 | 1 failure |
| 去 `-processor` 选项装配 | 2 failures |
| 去 `-s` 选项装配 | 1 failure |
| 去 `-processorpath` 选项装配 | 1 failure |
| 去 `--module-path` 选项装配 | 1 failure |
| **恢复 `instanceof SimpleJavaFileObject` 判断（live defect B）** | **1 failure** |
| **对占位符做 substring（live defect B2，诊断位置取 10/12/20）** | **2 failures + 2 errors** |
| 复用标志被忽略（恒新建编译器） | 2 failures + 1 error |
| 复用路径不关闭 file manager | 3 failures |
| `release` 未 acquire 时静默 return | 2 failures |
| file manager 跨轮缓存复用（**两轮都调缝，但把同一个 manager 交给 javac**） | **5 failures** |

### 断言面设计的两条硬规则（都是审计打出来的）

1. **诊断位置必须取非平凡值。** 不可读源码的 B2 断言最初让替身诊断返回
   `start = position = end = 0`。那样即使实现恢复成"按占位符长度裁剪再切片"，
   `substring(0, 0)` 也恰好是空串——与修复后的输出**逐字节相同**，断言恒真。
   改为 `10 / 12 / 20` 后，变异立刻产出 `[ UNAVAIL]` 这类误导文本并让测试变红。
2. **"每轮新建"必须断言真正交给 javac 的那个对象的身份。** 只数缝的调用次数、
   或只断言测试替身自己持有的 delegate，都会让"跨轮缓存同一个 manager 交给 javac"
   的实现通过——因为这两者都在测试的控制之下。必须在生产代码里记录实际传给 `getTask`
   的 manager，测试断言它的身份差异。

**已排除的假测试面**（两次尝试后才定型的断言面选择）：`-processorpath` 最初用"隔离目录 + 空 classpath
+ 观察处理器是否被发现"作断言面，变异探针显示**恒为绿**——因为 javac 会回退到宿主 JVM 的
`java.class.path` 找处理器（§六 的回退面）。改为选项装配断言后变异才真的变红。