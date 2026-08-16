# Truffle 与 GraalVM 生态调研：发展现状、JDK 内置化与未来前景

> Status: resolved
> Date: 2026-08-16
> Scope: Truffle/GraalVM 生态现状、OpenJDK AOT 路线（Galahad/Leyden/JVMCI）、对 nop-js 与 XLang native 提速方案的影响
> Conclusion: Truffle 活跃且战略地位上升；JDK 不会内置 GraalVM 原生编译（Galahad 已于 2026-03 解散，OpenJDK 走 Leyden 自有 AOT 路线）；Truffle 将长期存在，但其 JIT 依赖运行时 Graal 编译器，native image 内无法提速，不能作为 XLang native 提速手段。

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
| Espresso (Java on Truffle) | 存在，但 Oracle 转型公告明确重点是非 Java 语言 | 2025-09 Detaching 公告 |

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
| 收益量级 | 启动秒级→几百毫秒、预热更快（非数量级） | 毫秒级启动、内存大幅下降 |
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

- Truffle 的高性能来自 partial evaluation，需要**运行时**的 Graal 编译器（libgraal）；官方 25.1 发行说明明确"优化版 Truffle 运行时仅支持 GraalVM 25.1+"（即 stock JVM 上只解释执行）。
- **Native image 内没有运行时 JIT**，因此：
  - 早年（22.3 前）虽可将 Truffle 语言打包进 native 可执行文件，但 guest 语言只能解释执行、镜像体积巨大，该路径已被弃用/不官方支持（Quarkus 相关 issue 亦确认 polyglot native 不受支持）；
  - **"用 Truffle 给 native image 下的 XLang 提速"在机制上不成立**。

## 与当前项目的关系

- **nop-js（GraalJS 封装）**：GraalJS 持续活跃，无弃用风险。且 Truffle Unchained 之后 GraalJS 可作为 Maven 依赖嵌入 stock JVM，当前 `docs-en/dev-guide/graalvm/graalvm-compile.md` 中 "GraalJS 23.1.2 ↔ GraalVM for JDK 21.0.2" 的严格版本配对有望简化（待验证，列入 Open Questions）。
- **nop-quarkus-demo / native 编译链路**：native image 未死，但 GraalVM 与 JDK 列车解耦，建议 native 构建基线钉在 GraalVM 25 / JDK 25 LTS（Oracle 支持至 2030-09），并关注 Quarkus 对 GraalVM 25.x 创新列车的适配声明。JDK 26–28 期间 native exe 无新版 GraalVM 可用，这是钉 LTS 的硬约束。
- **JVM 部署的启动优化（新增选项）**：JDK 26 内置 Leyden AOT cache（JEP 516 对象缓存 + 任意 GC），对 nop 这类大量使用反射/动态代理/资源扫描的应用是**零闭世界改造成本**的快启动方案（反射全兼容），比上 native 的改造成本低得多。与 native exe 按部署形态分工：云原生极致启动/内存 → native；标准 JVM 快启动 → AOT cache。
- **版权合规**：CI/生产使用 Oracle GraalVM 构建 native 合法（GFTC 免费商用）；仅当把 GraalVM 本身再分发收费、或需要纯开源依赖链时才需切 CE。
- **XLang native 提速方案重估**：
  - 方案 b（Truffle 后端）**长期风险下调**（Truffle 活跃、战略地位上升），但**不解决 native image 场景**（无运行时 JIT）——它只服务 JVM 部署下的运行时动态脚本（该场景 nop-javac/Janino 通路已存在，Truffle 的增量价值需另评估）；
  - native 提速主线维持原判：**构建期 Executable→Java 转译（方案 a）+ GraalVM Feature 预编译（c1）**，与可逆计算"Generator 优于 Interpreter"原则同构。

## Conclusion

- 三个问题的答案：Truffle 发展现状良好且战略地位上升；**JDK 不会内置 GraalVM 原生编译**（Galahad 2026-03 解散、Metropolis 随后解散、JVMCI 开始从 OpenJDK 移除，OpenJDK 的 AOT 由 Leyden 以 CDS/AOT 缓存路线承接）；Truffle 将长期存在，作为 GraalVM "独立多语言运行时"战略的核心。
- 被否决方案：XLang Truffle 后端作为 **native image 提速手段**——native 内无运行时 JIT，Truffle 的 partial evaluation 机制无法生效。
- 后续工作：XLang native 提速按原路线推进（构建期转译 + Feature 预编译），如启动相关 plan 写入 `ai-dev/plans/`。

## Open Questions

- [ ] GraalJS 以 Maven 依赖嵌入 stock JDK 21+ 能否替代 nop-js 当前对完整 GraalVM 发行版的依赖（简化部署）？
- [ ] Quarkus 后续版本对 GraalVM 25.x 创新列车 / JDK 29 的 native 构建支持矩阵？（JDK 26 已确认无 GraalVM 对应版本）
- [ ] Espresso（Java on Truffle）在 Oracle 转型后的投入是否收缩（影响以 Espresso 做"JVM 内跑 JDK"的场景）？
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
- 仓库内相关：`docs-en/dev-guide/graalvm/graalvm-compile.md`、`nop-frontend-support/nop-js/`
