# Truffle 与 GraalVM 生态调研：发展现状、JDK 内置化与未来前景

> Status: resolved
> Date: 2026-08-16
> Scope: Truffle/GraalVM 生态现状、OpenJDK AOT 路线（Galahad/Leyden/JVMCI）、对 nop-js 与 XLang native 提速方案的影响
> Conclusion: Truffle 活跃且战略地位上升；JDK 不会内置 GraalVM 原生编译（Galahad 已于 2026-03 解散，OpenJDK 走 Leyden 自有 AOT 路线）；Truffle 将长期存在。**更正（2026-08-16 复核官方文档）**：GraalVM 25 起 native image 内嵌的 Truffle 优化运行时默认支持 guest 代码运行时 JIT，Espresso 官方支持在 native exe 内动态加载字节码；但 XLang 作为宿主 Java 代码在 native image 内仍无运行时 JIT，构建期转译（方案 a）仍是 native 提速主线。

## Context

- 承接 2026-08-16 关于 "XLang 在 GraalVM native image 下如何最大化执行速度" 的讨论。当时方案 b 是"Truffle 后端"（把 XLang Executable 树包成 Truffle Node，靠 partial evaluation 在 native 内获得 JIT）。
- 该方案的长期可行性取决于三个外部事实：Truffle 的发展状况、JDK 是否会内置 GraalVM 原生编译、Truffle 是否会消亡。本文调研并回答这三个问题。
- 涉及模块：`nop-frontend-support/nop-js`（GraalVM polyglot 封装）、`nop-demo/nop-quarkus-demo`（native 编译链路）、`docs-en/dev-guide/graalvm/graalvm-compile.md`（版本配对约定）。

## 调研目标

1. Truffle 语言实现框架目前的发展状况如何？
2. 未来 JDK 是否会内置 GraalVM 的原生编译（native image）功能？
3. Truffle 还会继续存在吗？

## 调研结果

### 1. Truffle 发展现状：活跃，且战略地位不降反升

- **2023-10 "Truffle Unchained"**：GraalVM 移除 `gu` 安装器，Truffle 与各语言改为 Maven Central 构件（`org.graalvm.polyglot` / `org.graalvm.truffle`），与 GraalVM JDK 解耦。此后 Truffle 语言可以：
  - 在 **stock JVM**（任何普通 JDK 21+）上以 Maven 依赖方式嵌入运行（解释执行，monotonic frames，无 JIT）；
  - 在 **GraalVM** 上获得 partial evaluation JIT 加速。
- **语言生态现状**（2026-08）：

| 语言/引擎 | 状态 | 依据 |
|---|---|---|
| GraalJS | 活跃（新 ECMAScript 支持、Node.js 更新随 25.1 发布） | GraalVM 25.1 发布公告 |
| GraalPy | 活跃（Python 3.13 支持计划于 2026） | GraalVM 官方博客（次级来源转述） |
| GraalWasm | 继续随版本更新（25.1.x 采 bytecode-handler 重设计） | oracle/graal `wasm/CHANGELOG.md` |
| FastR (R) | 已停止维护/归档（约 2023） | oracle/RFastR 仓库归档 |
| Espresso (Java on Truffle) | **活跃**：支持 Java SE 8/11/17/21/25、通过 JCK/TCK、提供 standalone 发行（25.1.3，Oracle GraalVM 与 CE 双版本）；官方明确支持"在 native exe 内动态加载字节码" | https://www.graalvm.org/latest/reference-manual/espresso/ |

- **2025-09 起 GraalVM 转型为"独立多语言运行时"**，Truffle 是这一新定位的核心资产（见下节），而非被边缘化的遗留技术。
- 版本列车持续：GraalVM 25.1 为首个 "innovation release"，2026-07 已更新至 25.2.4；Oracle GraalVM 25 线支持至 2030-09（endoflife.date）。

### 2. "JDK 内置 GraalVM 原生编译"：答案是否定的——方向已反转

时间线（均有一手来源）：

| 时间 | 事件 |
|---|---|
| 2022-12 | OpenJDK 提案 **Project Galahad**：把 GraalVM 的 Java 相关技术（Graal JIT，随后 native image）捐入 OpenJDK 主线 |
| 2023-10 | Truffle Unchained：Truffle/语言与 GraalVM JDK 解耦，Maven 化 |
| 2025-09 | Oracle 官方博客 **"Detaching GraalVM from the Java Ecosystem Train"**：GraalVM 团队转向非 Java 语言（GraalPy、GraalJS）；Oracle JDK 24 是包含实验性 Graal JIT 的最后一个版本；GraalVM for JDK 24 是最后一个随 Oracle Java SE 产品授权的 GraalVM 版本；建议 AOT 需求改用 JDK 25 + JEP 514/515（Leyden） |
| 2026-03 | **Galahad 解散**（失去 HotSpot Group 赞助；官方页面注明"在 2025-09 分离公告后该项目已无存在必要"） |
| 2026-04 | Project Metropolis（"以 Graal 替换 HotSpot C2"的长期愿景）解散 |
| 2026-05 | OpenJDK 启动 **JVMCI 从主干移除**（JDK-8382582；CSR JDK-8385105 已批准，理由："Galahad、Graal、Metropolis 解散后 OpenJDK 内已无 JVMCI 实验性 API 的消费者"） |

结论：**GraalVM 原生编译不会进入未来 JDK**。这不是"尚未完成"，而是 Oracle 与 OpenJDK 双方正式放弃的合并方向——GraalVM 留在 OpenJDK 之外作为独立产品线，OpenJDK 自己的 AOT 目标由 **Project Leyden** 承接。

### 3. OpenJDK 的自有 AOT 路线：Project Leyden

**重要澄清：Leyden 的 "AOT" 与 GraalVM 的 "AOT" 不是一回事。** Leyden 产物是**缓存文件**（预加载/预链接的类、方法画像、预初始化的堆对象），程序仍跑在 JVM 上；Native Image 产物是**独立原生可执行文件**，不需要 JVM。前者保留 Java 动态特性（反射、动态类加载全兼容），后者基于 closed-world 假设。

| 维度 | Leyden AOT Cache（OpenJDK 标准路线） | GraalVM Native Image（独立产品线） |
|---|---|---|
| 思路 | CDS + AOT 缓存渐进扩展（AOT 类加载/链接/方法画像/对象缓存），保留 Java 动态特性 | Closed-world 假设，构建期全量静态编译为原生可执行文件 |
| 产物 | 缓存文件（仍需 JVM 运行） | 独立 exe（无需 JVM） |
| 已落地 | JEP 483（JDK 24）、JEP 514/515（JDK 25）、**JEP 516 对象缓存 + 任意 GC（JDK 26，2026-03-17 发布）** | GraalVM 25.x 持续季度更新 |
| JDK 26 可用性 | ✅ 内置 | ❌ GraalVM 宣布跳过 JDK 26–28，直接支持 JDK 29 LTS |
| 生产案例 | Netflix 已在生产用 Leyden AOT cache 改善启动（JavaOne 2026） | Spring Boot/Quarkus/Micronaut 生态标准配置 |
| 收益量级 | 启动时间/预热改善（非数量级） | 启动毫秒级、内存大幅下降 |
| 许可/分发 | JDK 内置 | Oracle GraalVM（GFTC 免费商用）+ GraalVM CE（GPLv2-CPE） |

两者定位清晰分工：Leyden 服务"标准 Java 更快启动"，native image 服务"云原生极致启动/内存"。Spring Boot、Quarkus、Micronaut 生态的 native 编译在可预见未来仍依赖 GraalVM 独立产品。

### 4. Truffle 还存在吗：存在，且是 GraalVM 新战略的核心

- 2025-09 转型公告后，GraalVM 的身份从"Java 的下一代运行时"改为"独立高性能多语言运行时"，**Truffle 恰是这个定位的技术底座**，投资方向与 Truffle 高度重合。
- 发行上独立于 JDK 列车：25.x innovation releases 按自身节奏发布；据 GraalVM 官方博客（次级来源转述），将跳过 JDK 26–28，直接支持下一个 LTS Java 29。
- 社区生态解读（Micronaut 官方讨论）：GraalVM 功能（含 native image）并未被弃用，"非 Java 语言聚焦"主要影响 Oracle 商业授权组合，不影响技术可用性。

**Truffle 与 GraalVM 的耦合关系（研发耦合、消费解耦、性能绑定）：**

| 维度 | 耦合方式 |
|---|---|
| 研发 | Truffle 源码在 `oracle/graal` 仓库内，由 GraalVM 团队开发，版本号跟随 GraalVM/Polyglot 发布（23.1 → 24.x → 25.x）；API 兼容政策明确（deprecated 至少保留 2 个版本才移除） |
| 消费 | 2023-10 起以 Maven 构件（`org.graalvm.truffle` / `org.graalvm.polyglot`）独立分发，可嵌入任意 stock JDK 21+（解释执行，monotonic frames） |
| 性能 | partial evaluation JIT 仅在 GraalVM 运行时（libgraal，25.1+）内生效——**要 JIT 就必须跑在 GraalVM 上** |

结论：Truffle 将随 GraalVM 的自有节奏（LTS 对齐 + 季度更新）持续演进。风险边界在**语言层**而非框架层——个别语言可能被砍（FastR 已归档），但框架本身是战略底座，只要 GraalVM 存在（Oracle 支持承诺至 2030+）Truffle 就在。

### 5. GraalVM 的发布形态与版权协议

**发布形态：独立发行版，未来只对齐 LTS。**

- 一直是独立下载的发行版，非 OpenJDK 组成部分；2025-09 前版本号跟随 JDK 列车（GraalVM for JDK 17/21/24），之后改为自有节奏：2025-09 发布 25.1（首个 innovation release），季度 CPU 更新（2026-07 已至 25.2.4）。
- 非 LTS 的 JDK 26–28 没有对应 GraalVM 版本；下一个大版本直接基于 JDK 29（2027 LTS，计划存在变数）。**native exe 构建只能钉在 LTS 基线上。**

**版权协议：两个版本、两套协议：**

| 版本 | 协议 | 要点 |
|---|---|---|
| Oracle GraalVM（原企业版，全功能） | **GFTC**（GraalVM Free Terms and Conditions，Oracle 专有协议，非开源） | 免费，允许商用与生产环境使用；限制在**再分发**——可再分发但不得收费（把 GraalVM 本身打包卖钱不行）；早期版本（JDK 17/20 时代）曾带 1 年期限，现行 GFTC（JDK 21+）为长期许可 |
| GraalVM Community Edition | **GPLv2 + Classpath Exception**（与 OpenJDK 相同） | 真开源，源码在 github.com/oracle/graal，Native Image 包含在内，再分发无 Oracle 附加限制 |

对 nop 项目的选型含义：CI/生产直接用 Oracle GraalVM（GFTC 免费商用）合法；若需将 GraalVM 本身嵌入商业产品分发、或要求完全开源依赖链，选 CE。

### 6. 关键技术不变量：Truffle 的 JIT 依赖运行时 Graal 编译器

- Truffle 的高性能来自 partial evaluation，需要**运行时**的 Graal 编译器；在 **JVM 部署形态**下，优化版 Truffle 运行时仅 GraalVM 25（Oracle GraalVM 或 CE，均无需额外配置）提供，stock OpenJDK 上 guest 代码纯解释执行（fallback runtime，官方会打印 "does not support runtime compilation to native code" 警告；可用 `truffle.UseFallbackRuntime` 显式选择）。普通 JDK 21/25 宿主上的优化运行时只有 **polyglot isolate** 一种（native image isolate，自带独立 GC 与 JIT 编译器，见下节）。
- **更正（2026-08-16，依据官方 embed-languages 文档）：Native image 内 guest 代码有运行时 JIT。**
  - 官方 23.1 起明确 "no special configuration is required to use Native Image to build images with embedded polyglot language runtimes"；
  - GraalVM 25 "Runtime Optimization Support" 表格：Oracle GraalVM 25 / CE 25 均为 Optimizing runtime（guest 代码在运行时被编译为机器码）"Supported. No extra configuration is required"；镜像构建时传 `-Dtruffle.UseFallbackRuntime=true` 才显式退回纯解释执行；
  - 本调研早期（22.3 时代）"polyglot native 不受官方支持 / native 内只能解释执行"的判断已过时；本地一手资料 `~/sources/graal/truffle/docs/AOTOverview.md` 亦描述 native image 内的 context 预初始化、engine 缓存持久化（Oracle GraalVM）等运行时编译配套机制。
- 由此修正：**"用 Truffle 给 native image 下的 XLang 提速在机制上不成立"不再成立**——若 XLang 以 Truffle 语言形态打进镜像，guest AST 执行可获得运行时 JIT；路线决策仍维持构建期转译（原因见"与当前项目的关系"），但否决理由是**路线权衡**而非机制不可行。

## 与当前项目的关系

- **nop-js（GraalJS 封装）**：GraalJS 持续活跃，无弃用风险。且 Truffle Unchained 之后 GraalJS 可作为 Maven 依赖嵌入 stock JVM，当前 `docs-en/dev-guide/graalvm/graalvm-compile.md` 中 "GraalJS 23.1.2 ↔ GraalVM for JDK 21.0.2" 的严格版本配对有望简化（待验证，列入 Open Questions）。
- **nop-quarkus-demo / native 编译链路**：native image 未死，但 GraalVM 与 JDK 列车解耦，建议 native 构建基线钉在 GraalVM 25 / JDK 25 LTS（Oracle 支持至 2030-09），并关注 Quarkus 对 GraalVM 25.x 创新列车的适配声明。JDK 26–28 期间 native exe 无新版 GraalVM 可用，这是钉 LTS 的硬约束。
- **JVM 部署的启动优化（新增选项）**：JDK 26 内置 Leyden AOT cache（JEP 516 对象缓存 + 任意 GC），对 nop 这类大量使用反射/动态代理/资源扫描的应用是**零闭世界改造成本**的快启动方案（反射全兼容），比上 native 的改造成本低得多。与 native exe 按部署形态分工：云原生极致启动/内存 → native；标准 JVM 快启动 → AOT cache。
- **版权合规**：CI/生产使用 Oracle GraalVM 构建 native 合法（GFTC 免费商用）；仅当把 GraalVM 本身再分发收费、或需要纯开源依赖链时才需切 CE。
- **XLang native 提速方案重估**：
  - 方案 b（Truffle 后端）**长期风险下调**（Truffle 活跃、战略地位上升）。机制上不再被 native image 排除（§6 更正：native 内 guest 代码有运行时 JIT），但仍不作为 native 提速手段——若把 XLang 做成 Truffle 语言塞进镜像，代价是镜像体积膨胀、warmup 与解释器长期维护成本，且构建期转译（方案 a）在可逆计算"Generator 优于 Interpreter"原则下严格更优；方案 b 只服务 JVM 部署下的运行时动态脚本场景（该场景 nop-javac/Janino 通路已存在，Truffle 的增量价值需另评估）；
  - **新增能力：native exe 内动态加载字节码（Espresso）**——见 §7。对 nop 的意义：native 产物中"运行时加载外部 .class/动态代码"的场景（插件机制、动态规则、在线扩展）有了官方通路；代价是镜像体积增加与 guest/host 边界（反射配置、性能差异），是否引入需按具体产品形态评估。
  - native 提速主线维持原判：**构建期 Executable→Java 转译（方案 a）+ GraalVM Feature 预编译（c1）**。

### 7. 如何在 native image 中动态加载字节码：Espresso（Java on Truffle）

**问题背景**：native image 是 closed-world——运行时没有 javac/`JavaCompiler`，宿主代码 AOT 编译后无 classfile 解析能力，`URLClassLoader`/自定义 ClassLoader 动态加载 class 在镜像内不可用（无运行时类元数据）。"exe 里动态加载字节码"必须另找虚拟机层通路。

**官方通路：Espresso**。官方参考手册原文能力清单明确包含 "run in the context of a native executable while still allowing dynamically-loaded bytecode"。要点：

- **机制**：Espresso 是用 Truffle 实现的 Java 字节码解释器（完全元循环 JVM），支持 Java SE 8/11/17/21/25，通过 JCK/TCK；它本身跑在 native image VM 上，guest 字节码由镜像内包含的 Graal 优化编译器在运行时 JIT 成机器码（§6 的 optimizing runtime）。
- **用法**：
  1. 构建期：polyglot 运行时与 Espresso 作为普通 Java 依赖打进 native 镜像（官方 23.1+ 无需特殊配置；`org.graalvm.polyglot:polyglot` + `java` 语言构件）；**需随镜像打包 Espresso 运行时资源**（`espresso-runtime-resources-*` 构件，或运行时 `java.JavaHome` 指向外部 JDK）——FAQ 明确 Espresso 依赖标准核心库（rt.jar / lib/modules）与关联原生库（libjava、libnio 等）；
  2. 运行时：创建 guest JVM context（语言 id `java`），动态加载 .class 的官方姿势是**语言绑定 + guest classpath**——`java.Properties.java.class.path` 指向字节码目录，再经 `polyglot.getBindings("java").getMember("pkg.Class")`（`Class#forName` 格式）在运行时解析并加载类。**注意：Espresso 不支持 `eval` Java 源码**（官方 interop 文档原文，且 `EspressoLanguage.parse` 源码实测只接受 `<GetBindings>`/`<ProcessReferences>` 等少数特殊命令），字节码文件可按需写入 classpath 目录（类首次被引用时懒加载）；
  3. 源码输入：镜像内**没有 javac**——`.java` 需在外部预编译为 `.class` 再加载（Espresso standalone 的 `java` 启动器支持单文件源码模式 `java sourcefile`，但嵌入场景仍是"外部编译 → 加载字节码"）；
  4. 性能与可用性：官方 FAQ 量化——当前比 HotSpot 慢约 2-3 倍（团队正聚焦性能优化），`--engine.Mode=latency` 可加快 JIT 换取峰值性能下降；生产可用性 Linux x64 支持、其他平台实验性；guest 访问宿主反射能力需 native-image 反射配置（同普通 polyglot native 规则）。

**示例：运行时动态加载外部 .class 字节码并调用**（写法依据官方 interop 文档）

```java
// host java —— 宿主代码随 native image AOT 编译进 exe
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

// 1. guest classpath 指向动态字节码目录（context 级配置，创建后不可变）
try (Context polyglot = Context.newBuilder()
        .allowAllAccess(true)
        .option("java.Properties.java.class.path", "/opt/app/plugins")
        .build()) {

    // 2. 运行时动态加载：按 Class#forName 格式从 classpath 解析 .class 字节码。
    //    字节码文件可在首次 getMember 前任意时刻写入该目录（类懒加载）
    Value plugin = polyglot.getBindings("java")
            .getMember("com.example.plugins.HelloPlugin");

    // 3. 调用静态方法 / 实例化并调用实例方法
    Value result = plugin.invokeMember("greet", "nop").asString(); // "hello, nop"
    Value instance = plugin.newInstance();
    int answer = instance.invokeMember("answer").asInt();          // 42
}
```

- **热更新约束**：guest JVM 内类只加载一次（同普通 JVM 语义）——新写入的 .class 只要类尚未加载过即可生效；已加载类的替换需新建 context（classpath 固定）或 guest 侧自建 `URLClassLoader`/`defineClass` 加载；字节码来源（文件/网络/DB）可先落盘到 classpath 目录再触发加载。

**内存中直接生成并加载字节码（无需落盘）**——回答"能否把内存中创建的字节码直接加载"：

- **前提澄清（源码实测 `EspressoLanguage.parse`）**：Espresso 的 `eval` 只接受少数特殊命令，不支持 eval Java 源码、也不支持 eval .class 文件字节——"内存直接加载"**不走 eval 通路**，官方姿势（bindings + classpath）本身依赖磁盘上的 classpath 条目。
- **纯内存方案：guest 侧 `ClassLoader#defineClass`**。guest 是完整 JVM，`defineClass(name, byte[], off, len)` 在 guest 内正常可用：
  1. 宿主侧内存生成字节码——`nop-xlang-java` 转译器 / ASM / Janino 均为纯 Java 实现（不依赖 javac），在 native image 宿主内可直接运行并产出 `.class` 字节数组；
  2. 通过 interop 把 `byte[]` 作为方法实参传入 guest 引导类（只需一个预置于 guest classpath 的自定义 `ClassLoader` 子类，引导类很小）；
  3. guest 内 `defineClass` 定义类，随后按完整 JVM 语义反射/实例化调用——全程无磁盘 IO。
- 优点：热更新天然支持（每版本新建 `ClassLoader` 实例即隔离）；无 classpath 目录管理与懒加载时序问题。

```java
// guest java —— 预置于 guest classpath 的引导类（唯一需要预置的类）
public class MemoryClassLoader extends ClassLoader {
    public Class<?> define(String name, byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }
}
```

```java
// host java —— native exe 内：内存生成字节码 → interop 传入 guest → defineClass
try (Context polyglot = Context.newBuilder()
        .allowAllAccess(true)
        .option("java.Properties.java.class.path", "/opt/app/guests") // 仅引导类
        .build()) {

    // 1. 取 guest 引导类并实例化自定义 ClassLoader
    Value loader = polyglot.getBindings("java")
            .getMember("MemoryClassLoader").newInstance();

    // 2. 宿主侧内存生成字节码（转译器/ASM/Janino 产物，不落盘），直接传入 guest
    byte[] classBytes = generateInMemoryBytecode(); // e.g. nop-xlang-java 转译产物
    Value dynCls = loader.invokeMember("define", "com.example.DynRule", classBytes);

    // 3. 实例化并调用（guest 完整反射语义）
    Value instance = dynCls.newInstance();
    int answer = instance.invokeMember("answer").asInt(); // 42
}
```

> 注：宿主数组作为实参流入 guest `byte[]` 形参依赖 Espresso implicit interop（默认开启；Espresso launcher 场景默认关闭，需 `--java.EnableImplicitInterop=true`）。
- **备选**：动态代码不限定 Java 时，GraalJS/GraalPy/GraalWasm 同机制（guest 代码同样有 native 内运行时 JIT）；沙箱/隔离场景可用 **polyglot isolate**（Native Image isolate + 独立 GC/JIT，官方已提供 JS/Python/Wasm 构件，**暂无 Java**）。
- **对 XLang native 的意义**：若未来需要"native exe 内动态加载并执行 XLang 编译产物"，可行组合是——`nop-xlang-java` 转译器（宿主代码，AOT 进镜像）在运行时产出 `.class`，再由 Espresso 动态加载执行；此为选项而非路线，当前 native 主线仍是构建期全量纳入镜像（方案 a）。

### 8. XLang 执行形态性能对比：转译字节码 vs Truffle 后端

**问题**：同一份 XLang 逻辑，走"构建期转译为 Java 字节码"（方案 a，nop-xlang-java）还是"翻译为 Truffle AST 执行"（方案 b，nop-xlang-truffle），哪个性能更好？

**关键事实**：

- **官方数据点（Espresso FAQ，2026-08 访问）**：Java 字节码经 Truffle 执行（解释 + 运行时 JIT）比 HotSpot 慢 **2-3 倍**——这是 Truffle 家族处理静态语义语言的下限参照（XLang 原生 Truffle 后端省去字节码解释层会优于 Espresso，但仍不优于 JVM 直接 JIT）。
- **行业共识**：Truffle 的设计目标是"让动态语言解释器追上 JIT 编译"（PE + 特化针对解释器结构）；对已编译为静态代码的语言（Java 字节码）没有结构性优势——GraalVM 团队自身对 Espresso 的定位是隔离/互操作/工具链，而非性能。
- **代码形状可控性**：方案 a 的转译器可以决定生成代码的形状（扁平方法、原始类型、直接方法调用、局部变量布局），帮助 JVM 分层编译的内联与逃逸分析；方案 b 的 PE 有编译预算（内联深度/大小限制），超大 AST 可能无法完全优化。
- **warmup 路径**：方案 a 只有常规 JVM 分层编译 warmup；方案 b 需要 解释 → 类型画像/特化 → PE 编译 的更长的热身链路，且类型画像变化会触发 deopt 回退重编译。

| 维度 | 方案 a：转译 Java 字节码 | 方案 b：Truffle 后端 |
|---|---|---|
| JVM（HotSpot/GraalVM）峰值 | ≈ 手写 Java（JIT 按普通 Java 优化生成代码） | PE 后接近机器码，但需特化 + 更长 warmup + deopt 风险 |
| stock JVM（无 GraalVM） | 正常 JIT（C1/C2），快 | 纯解释执行，慢 1-2 个数量级 |
| native image 热代码 | AOT 直编的宿主代码，最优 | guest 运行时 JIT，仍有解释器/特化开销 |
| 运行时动态生成代码 | 需要字节码工具链（ASM/Janino）| 天然支持（parse 即解释执行）|
| polyglot 互操作 / 沙箱工具链 | 无 | 有（Truffle 工具全家桶）|

**结论**：

- 峰值性能与总吞吐：**方案 a 全面优于方案 b**。官方数据点（Espresso 2-3x 慢于 HotSpot）与行业共识（Truffle 不服务于静态语言性能）指向同一结论；转译产物在 JVM 上就是"手写 Java"，这是已知最强执行形态。
- 方案 b 的价值**不在性能**，而在三条结构性能力：运行时动态代码无需字节码工具链即可执行、stock JVM 上可解释执行、polyglot 互操作与 Truffle 工具链（沙箱/追踪/隔离）。与 roadmap "编译期确定→方案 a、运行时动态→方案 b" 的分工完全一致。
- 推论：即便在"运行时动态"场景，若性能优先于上述三条能力，运行时字节码生成（ASM/Janino）+ JVM JIT 仍优于 Truffle 后端——方案 b 只在需要 polyglot 或纯解释环境时才是正确选择。

## Conclusion

- 三个问题的答案：Truffle 发展现状良好且战略地位上升；**JDK 不会内置 GraalVM 原生编译**（Galahad 2026-03 解散、Metropolis 随后解散、JVMCI 开始从 OpenJDK 移除，OpenJDK 的 AOT 由 Leyden 以 CDS/AOT 缓存路线承接）；Truffle 将长期存在，作为 GraalVM "独立多语言运行时"战略的核心。
- 机制更正：**native image 内 Truffle guest 代码有运行时 JIT**（GraalVM 25 默认，`-Dtruffle.UseFallbackRuntime=true` 可构建期退回纯解释）；Espresso 官方支持在 native exe 内动态加载字节码——"native 内无法动态加载/无法 JIT"的旧结论作废。
- 被否决方案（路线决策不变）：XLang Truffle 后端作为 **native image 提速手段**——机制上可行（guest JIT），但镜像体积/warmup/维护成本与"Generator 优于 Interpreter"原则均指向构建期转译；XLang 作为宿主 Java 代码在 native image 内仍无运行时 JIT，方案 b 只服务 JVM 部署形态。
- 后续工作：XLang native 提速按原路线推进（构建期转译 + Feature 预编译），如启动相关 plan 写入 `ai-dev/plans/`；Espresso 动态加载路径是否引入 nop 产品形态由后续业务评估决定。

## Open Questions

- [ ] GraalJS 以 Maven 依赖嵌入 stock JDK 21+ 能否替代 nop-js 当前对完整 GraalVM 发行版的依赖（简化部署）？
- [ ] Quarkus 后续版本对 GraalVM 25.x 创新列车 / JDK 29 的 native 构建支持矩阵？（JDK 26 已确认无 GraalVM 对应版本）
- [x] Espresso（Java on Truffle）在 Oracle 转型后的投入是否收缩？——**已回答（2026-08-16）**：官方参考手册持续维护，Espresso 25.1.3 standalone 提供 Oracle/CE 双版本，支持 Java 8/11/17/21/25 并通过 JCK；虽非战略重点（转型聚焦非 Java 语言），但它是"native exe 内动态加载字节码"的唯一官方通路，短期内不会消失。
- [ ] native image 内 Espresso guest 代码的实际性能基准（vs HotSpot 上 Espresso / vs 方案 a 转译产物）？——需实测。
- [x] Leyden 是否覆盖 nop 应用"无 GraalVM 也能快启动"的场景？——**部分回答**：JDK 26（2026-03）已内置 AOT cache + JEP 516 对象缓存（任意 GC），Netflix 已生产验证；剩余问题是 nop 的 VFS/模型加载等自有初始化开销能否被训练运行覆盖，需实测。

## References

- OpenJDK Galahad 项目页（含解散声明）：https://openjdk.org/projects/galahad/
- Oracle 博客 "Detaching GraalVM from the Java Ecosystem Train"（2025-09）：https://blogs.oracle.com/java/detaching-graalvm-from-the-java-ecosystem-train
- GraalVM "Truffle Unchained"（2023-10）：https://medium.com/graalvm/truffle-unchained-13887b77b62c
- GraalVM CE 25.x 发行说明：https://www.graalvm.org/release-notes/JDK_25/ 、https://www.graalvm.org/release-notes/25.1/
- Galahad 解散与 JVMCI 移除分析（2026-05，含 JDK-8382582/JDK-8385105 因果链）：https://jonghoonpark.com/2026/05/23/openjdk-galahad-dissolution
- Micronaut 社区对 Detaching 公告的解读：https://github.com/micronaut-projects/micronaut-core/discussions/12073
- Wikipedia: GraalVM（版本 25.2.4 / 2026-07）：https://en.wikipedia.org/wiki/GraalVM
- Oracle GraalVM 生命周期：https://endoflife.date/oracle-graalvm
- GFTC 许可协议全文：https://www.oracle.com/downloads/licenses/graal-free-license.html
- GraalVM FAQ（GFTC 许可范围说明）：https://www.graalvm.org/faq/
- Oracle 博客 "Introducing the GraalVM Free License"：https://blogs.oracle.com/graal/graalvm-free-license
- JDK 26 发布（含 JEP 516）：https://blogs.oracle.com/java/the-arrival-of-java-26
- JDK 26 功能概览（AOT cache 等）：https://bell-sw.com/blog/an-overview-of-jdk-26-features/
- Leyden AOT cache 与 ZGC（JDK 26）：https://softwaremill.com/project-leyden-and-jdk-26-bringing-aot-caching-to-zgc/
- Netflix 生产环境使用 Leyden AOT（JavaOne 2026）：https://www.youtube.com/watch?v=4kEh8hxAP4U
- Espresso 官方参考手册（GraalVM latest，2026-08-16 访问）：https://www.graalvm.org/latest/reference-manual/espresso/
- Embedding Languages 官方文档（Runtime Optimization / Polyglot Isolates / Build Native Executables，2026-08-16 访问）：https://www.graalvm.org/latest/reference-manual/embed-languages/
- 本地一手资料：`~/sources/graal/truffle/docs/AOTOverview.md`（native image 内 Truffle 运行时编译/缓存机制）
- 仓库内相关：`docs-en/dev-guide/graalvm/graalvm-compile.md`、`nop-frontend-support/nop-js/`
