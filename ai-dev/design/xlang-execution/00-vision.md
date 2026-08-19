# XLang 执行子系统愿景（xlang-execution）

**日期**：2026-08-19
**范围**：XLang 表达式 / XPL 模板的执行层：解释器（基线与兜底）+ java / truffle 双后端目标态
**状态**：active

---

## 一、设计结论

1. XLang 执行层采用**三后端**架构：现解释器（`nop-xlang`，正确性基线与兜底）+ `nop-xlang-java`（编译期确定资源的零解释执行）+ `nop-xlang-truffle`（运行时动态脚本的 JIT 提速）。
2. 分工判据是**代码的产生时机**：编译期可确定的资源走 java 后端；运行时动态产生的脚本/表达式走 truffle 后端；两者都不适用时回退解释器。
3. 正确性基准是**对拍不变式**：同一 Executable 树在三后端的执行结果（返回值、副作用、异常语义）必须一致，对拍差异即回归失败。
4. 性能目标只定方向性门槛（java 后端不低于解释器；truffle 后端在 GraalVM 形态显著优于解释器），量化数值由性能基准计划（roadmap I7）落定，本层不发明具体数字。
5. 依赖方向不可违反：新模块 → `nop-xlang` → `nop-core`；Truffle 依赖只允许出现在 `nop-xlang-truffle`。

## 二、问题定位

现状：XLang 编译前端（宏/标签全展开）产出 Executable 树，执行期为内存中解释执行——`IExecutableExpression.execute` 沿树虚调用递归。存在两条独立痛点：

### 痛点一：native image 内宿主 Java 无 JIT

XLang 解释器是宿主 Java 代码，经 GraalVM native image AOT 编译后**不再有运行时 JIT**，解释开销（虚调用分派、树遍历、类型判断）被固化在镜像里，无法自我修复。native image 内嵌的 Truffle 优化运行时只对 **guest 语言代码**生效，不作用于宿主 XLang 解释器。

### 痛点二：JVM 部署形态下运行时动态脚本无 JIT

规则、表达式、脚本大量在运行时才编译（用户输入、规则配置、动态拼接产物）。解释执行形态使它们永远停留在解释开销上——无类型画像、无特化、无内联，即使进程长驻、代码热点稳定。

### 外部环境事实（决策输入）

- **GraalVM 原生编译不会进入未来 JDK**：Galahad（2026-03 解散）、Metropolis（2026-04 解散）、JVMCI 启动自 OpenJDK 移除（JDK-8382582）。OpenJDK 自有 AOT 走 Leyden 缓存路线，产物是"仍跑在 JVM 上的缓存文件"，与 native image（独立可执行文件）是不同形态，不构成替代。native exe 构建只能钉在 GraalVM LTS 基线（25.x 线，Oracle 支持承诺至 2030-09）。
- **Truffle 以 Maven 构件独立分发**（2023-10 起）：`org.graalvm.truffle` / `org.graalvm.polyglot` 可嵌入任意 stock JDK（解释执行）；partial evaluation JIT 仅在 GraalVM 运行时内生效，同一二进制两种形态。
- **可逆计算原则**：Generator 优于 Interpreter——能编译期生成的，不留到运行期解释。

## 三、三后端分工原则

| 后端 | 模块 | 适用判据 | 部署形态收益 |
|---|---|---|---|
| java | `nop-xlang-java`（新模块） | 编译期可确定的资源（`_vfs` 内静态 xpl / xlib / expr / xbiz 等） | JVM：普通 Java 代码获 JIT；native image：生成类直编进镜像，是 native 提速的唯一路线 |
| truffle | `nop-xlang-truffle`（新模块） | 运行时动态产生的脚本/表达式 | GraalVM：partial evaluation JIT 显著提速；stock JVM：解释执行 |
| interpreter | `nop-xlang`（现状） | 兜底：后端不可用 / 未启用 / 生成物缺失 / 显式降级 | 全形态可用，正确性基线 |

选择机制、注册 SPI、对拍框架的统一架构见 `01-architecture-baseline.md`；两后端各自的架构见 `../xlang-java/01-architecture-baseline.md` 与 `../xlang-truffle/02-architecture-baseline.md`。

**为什么按"产生时机"分工**：native image 是 closed-world，宿主侧运行时无法动态加载类，"编译期可确定"是 java 后端的硬前提；Truffle 的结构性价值恰在运行时动态代码——parse 即执行、无需字节码工具链与类加载管理。两个判据互斥，且并集加上兜底后覆盖全部场景；任何环境下行为都可用（降级到解释器）。

## 四、成功标准

1. **对拍基准（正确性，一票否决）**：同一 Executable 树在解释器 / java / truffle 三后端执行，返回值、副作用（scope 变更、输出缓冲）、异常语义（错误码 + 源位置）一致；对拍差异即回归失败（fail-fast），不允许静默跳过。对拍纳入常规回归，不允许削弱现解释器测试。
2. **性能门槛（方向性，量化归 I7）**：
   - java 后端：不低于解释器（JVM 与 native 两形态）——转译产物是普通 Java 方法，结构性消除解释开销；
   - truffle 后端：GraalVM 形态显著优于解释器（partial evaluation + 内联缓存收益）；stock JVM 形态以"不显著劣于解释器"为底线——收益取决于 DSL 特化节点密度，量化归 I7；
   - 基准口径（负载集、形态矩阵、阈值）由 I7 基准计划落定，本层不发明具体数字。
3. **降级安全**：任一后端不可用时按选择机制降级到解释器，降级事件显式可观测（日志/指标），不允许静默。
4. **边界干净**：Truffle 依赖零泄漏（约束见 §六），现解释器行为与测试零回归。

## 五、显式 Non-Goals

- **不改编译前端**：宏/标签展开、Delta 差量合并、slot 分配（`LexicalScopeAnalysis`）全部保持现状；两后端只消费最终 Executable 树。
- **不做 native image 形态的 Truffle 后端**：native 内 guest 代码虽有运行时 JIT（GraalVM 25 默认），但镜像体积、warmup、双解释器长期维护成本与"Generator 优于 Interpreter"原则都指向构建期转译；native 提速唯一路线是 java 后端。
- **不用 Espresso（Java on Truffle）作 XLang 提速路线**：Espresso 是"native exe 内运行时动态加载字节码"的官方通路（未来插件/在线扩展场景的选项），不是执行提速手段（官方 FAQ 量化：Truffle 执行 Java 字节码比 HotSpot 慢约 2-3 倍）。
- **不以运行时字节码工具链（ASM/Janino 动态生成 + 类加载）作为动态脚本的默认后端**：既有 janino 通路继续服务 `java:` 脚本语言（按需保留）；动态 XLang 表达式的默认路线是 truffle 后端——决策依据是无需字节码工具链、类加载/卸载管理的结构性简化。峰值性能优先于该简化收益的场景可另行评估，不阻塞本愿景。
- **一期不做 instrumentation / debugger 集成**：Truffle `ExecutionEventListener` 留作后续，接入 nop 链路追踪。

## 六、不可违反约束

1. **依赖方向**：`nop-xlang-java` / `nop-xlang-truffle` → `nop-xlang` → `nop-core`，禁止反向；`nop-xlang` 与 `nop-core` 不得感知任何后端的具体实现，只感知注册 SPI 契约。
2. **Truffle 依赖不泄漏**：`org.graalvm.truffle` / `org.graalvm.polyglot` 坐标只允许出现在 `nop-xlang-truffle` 的 pom 与代码中；内核其他模块（含 `nop-xlang`、`nop-core`）不得出现 GraalVM import。
3. **对拍不变式**：每个后端实现计划的验收必须包含三后端对拍断言；回归不允许削弱现解释器测试。
4. **生成物纪律**：`_gen/` 与 `_` 前缀产物不可手改（仓库硬规则）；java 后端只生成源码与加载器，不绕过 codegen 管线。
5. **前端不可变**：Delta 定制全部发生在上游（差量合并完成、Executable 树固化之后才翻译/转译），后端不感知 Delta。

## 七、与已有设计的关系

- 上游编译前端（不改）：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/`（XplCompiler、LexicalScopeAnalysis 等）
- 现解释器基线：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/`（Executable 节点类，live 计 137 个文件）
- 统一架构：[01-architecture-baseline.md](01-architecture-baseline.md)
- java 后端架构：[../xlang-java/01-architecture-baseline.md](../xlang-java/01-architecture-baseline.md)
- truffle 后端愿景与架构：[../xlang-truffle/00-vision.md](../xlang-truffle/00-vision.md)、[../xlang-truffle/02-architecture-baseline.md](../xlang-truffle/02-architecture-baseline.md)
