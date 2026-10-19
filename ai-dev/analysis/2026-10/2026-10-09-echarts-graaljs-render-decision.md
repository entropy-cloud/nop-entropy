# ECharts 服务端静态渲染：GraalJS 坐标、echarts 资源与字体初验裁定

> Status: resolved
> Date: 2026-10-09
> Scope: ai-dev/backlog/echarts-server-render-roadmap.md WI0（M0 选型裁定）；影响 nop-kernel/nop-dependencies、nop-format/nop-chart-echarts、后续 nop-canvas2d（WI1–3）与引擎（WI4）
> Conclusion: GraalJS 采用 org.graalvm.polyglot:js:25.2.4 + org.graalvm.polyglot:polyglot:25.2.4（与 nop-xlang-truffle 的 truffle 25.2.4 精确同线）；echarts 资源采用 org.mvnpm:echarts:6.1.0 依赖并排除 tslib/zrender 传递依赖；macOS 环境逻辑字体 SansSerif 可直接渲染中文，物理字体 PingFang SC 等可用，WI2 FontMapper 需可配置且缺字体显式报错。

## Context / Background

roadmap（ai-dev/backlog/echarts-server-render-roadmap.md）裁定以「JVM 内 GraalJS 执行官方 dist/echarts.js + Java2D 实现 Canvas2D shim」替代 JFreeChart 作为报表静态图表引擎。WI0 需在动工前裁定三个选型点：

1. GraalJS 的 Maven 坐标与版本——仓库现状是 nop-dependencies 留有 graalvm.version=23.1.2 死属性，nop-xlang-truffle 钉 truffle 25.2.4，被禁用的 nop-js 硬编码 polyglot:js-community:23.1.2；三处不一致，若选型不当会在全平台部署时形成两条 truffle 线冲突。
2. echarts 本体获取方式——Maven 依赖 vs dist/echarts.js（3,847,293 字节）提交入库。
3. 中文字体在 JVM 2D 渲染下的可得性——后续 golden 基线与 17 图型对版都依赖字体环境确定性。

证据采集日期：2026-10-09；Maven Central 事实均以当日 repo1.maven.org 实测为准。

## Option A: GraalJS 坐标 org.graalvm.polyglot:js:25.2.4（polyglot 新坐标、25.2.4 线）

- 核心思路：embedder API 用 org.graalvm.polyglot:polyglot:25.2.4，JS 引擎用 org.graalvm.polyglot:js:25.2.4（packaging=pom，聚合 org.graalvm.js:js-language:25.2.4 + org.graalvm.truffle:truffle-runtime:25.2.4）。
- 优点：
  - **truffle 25.2.4 与 nop-kernel/nop-xlang-truffle 钉定的 truffle.version=25.2.4 精确同线**——全平台（nop-chaos 等聚合部署）只有一个 truffle 版本，无跨线类冲突。
  - org.graalvm.polyglot:js 是官方当前推荐坐标；同义的 org.graalvm.polyglot:js-community:25.2.4 pom 描述已标注 "Deprecated: Please use the 'org.graalvm.polyglot:js' Maven coordinate instead"（2026-10-09 实测逐字）。
  - 25.x 线为活跃维护线，对较新 JDK 的兼容性随版本演进（本项目 CI JDK 21、本地 JDK 26 双环境，实测见文末验证小节）。
- 缺点：
  - 与 nop-dependencies 现有 graalvm.version=23.1.2 属性数值不一致，需处置该死属性（本计划裁定：删除，见 Phase 2 执行项 2358）。
  - mvnpm 生态外的第三方若引旧版 truffle 仍可能冲突，但本仓库内无此情况（唯一 truffle 消费者就是 nop-xlang-truffle）。
- 适用场景：需要与仓库既有 truffle 25.2.4 共存的 JVM 内嵌入执行——正是本项目场景。

## Option B: GraalJS 坐标 org.graalvm.polyglot:js-community:23.1.2（nop-js 先例、23.1.2 线）

- 核心思路：沿用被禁用的 nop-frontend-support/nop-js 的依赖写法（polyglot:polyglot:23.1.2 + polyglot:js-community:23.1.2 硬编码）。
- 优点：
  - 与 nop-dependencies 现有 graalvm.version=23.1.2 数值一致；仓库内有先例可抄。
  - 23.1.2 线对 JDK 21 支持成熟。
- 缺点：
  - **与 truffle 25.2.4 跨线冲突**：23.1.2 的 js 拉入 truffle 23.1.x，与 nop-xlang-truffle 的 25.2.4 在同一 war/classpath 中将出现两个 truffle-api 版本，Maven 就近解析后必有一方行为异常（truffle 语言与 runtime 版本不匹配是 GraalVM 官方明示的不支持组合）。
  - js-community 坐标已被官方标注 Deprecated。
  - 23.1.x 已非活跃维护线；本地 JDK 26 环境无官方兼容承诺。
  - nop-js 本身被注释禁用、不在 reactor，先例只是「存在过」，不是「被支持」。
- 适用场景：独立应用、无其他 truffle 消费者——与本项目不符。

## Comparison（GraalJS 坐标）

| 维度 | Option A: polyglot:js:25.2.4 | Option B: polyglot:js-community:23.1.2 |
|------|------------------------------|----------------------------------------|
| truffle 对齐 | 25.2.4 = nop-xlang-truffle 钉版，精确同线 | 23.1.x ≠ 25.2.4，跨线冲突 |
| 坐标状态 | 官方推荐线 | 官方标注 Deprecated |
| 维护状态 | 活跃 | 23.1.x 冻结 |
| 仓库先例 | 无（但 BOM 收敛后为先例） | nop-js（已禁用） |
| JDK 兼容 | 21/25/26 实测通过（见验证小节） | 未验证，26 无承诺 |

**裁定：Option A。** 决定性因素是 truffle 单线对齐；deprecated 标注与维护状态为辅助理由。代价是删除 nop-dependencies 的三个 23.1.2 残留（graalvm.version、graalvm-js.version、注释依赖块），该删除已列入 plan 2358 Phase 2。

## Option A: echarts 资源 org.mvnpm:echarts:6.1.0 Maven 依赖

- 核心思路：nop-chart-echarts 以 test/runtime 依赖引入 org.mvnpm:echarts:6.1.0，排除 org.mvnpm:tslib 与 org.mvnpm:zrender（服务端只需 bundled dist，不需要 npm 模块解析图），从 classpath 读取 META-INF/resources/_static/echarts/6.1.0/dist/echarts.js。
- 优点：
  - 二进制不进 git 仓库（3.8MB×多版本会永久膨胀 clone 体积）。
  - 版本升级 = 改一行 BOM 版本号；mvnpm 由 npm registry 自动镜像，6.1.0 与 ~/sources/echarts v6.1.0 同源。
  - 依赖坐标进 BOM 后版本全仓唯一。
- 缺点：
  - 资源路径带版本号（META-INF/resources/_static/echarts/6.1.0/dist/echarts.js），升级时路径变化，需按版本拼路径或 classpath 扫描定位（引擎封装为单点，WI4 承载）。
  - 构建依赖网络可达 Maven Central（与仓库现有构建模型一致，非新增约束）。
  - jar 13MB 会让 nop-chart-echarts 及下游构建产物变大（仅接线模块携带，不进 nop-report-core，见 roadmap Framework 表）。
- 适用场景：版本可管理、构建可复现的服务端依赖。

## Option B: echarts 资源 dist/echarts.js 3.8MB 提交入库

- 核心思路：把 dist/echarts.js 作为静态资源提交进 nop-chart-echarts/src/main/resources。
- 优点：
  - 加载路径固定、零网络依赖、极简。
- 缺点：
  - 3.8MB 二进制永久进 git 历史；升级时旧版本无法从历史中廉价移除。
  - 与 mvnpm 已验证可用的官方分发渠道重复；入库文件与 npm 上游的对应关系靠人肉维护，漂移风险高。
  - 许可文件需手动同步（Apache-2.0，允许再分发但应携带声明）。
- 适用场景：无法接入 Maven 仓库的极端环境——本项目不受此限。

**裁定：Option A（org.mvnpm:echarts:6.1.0，排除 tslib/zrender）。** 决定性因素是版本升级路径与 git 仓库体积；versioned 资源路径的定位逻辑收敛在引擎单点（WI4）。

## 中文字体可得性初验

实测环境：macOS（arm64, Darwin 25.6.0）+ Zulu JDK 26.0.1，Java2D headless：

- 物理中文字体可用：PingFang SC、Hiragino Sans GB、STHeiti、Songti SC、Arial Unicode MS 均可按名解析为物理字体。
- 逻辑字体覆盖：SansSerif / Dialog 对 U+4E2D（中）与 U+6587（文）canDisplay=true；20px SansSerif 绘制「中文坐标轴」产生 1027 个非白像素（抗锯齿开启）——**逻辑字体经 AWT fallback 链可直接渲染中文**。
- 不可用（解析回 Dialog）：Noto Sans CJK SC、Source Han Sans SC、WenQuanYi Zen Hei、Microsoft YaHei。

对 WI2 FontMapper 的输入约束：

1. FontMapper 必须支持「泛族名（sans-serif 等）→ 可配置具体物理字体」映射，默认链需在 mac（PingFang SC）与 CI Linux（预期 Noto Sans CJK，需在 CI 镜像确认）双环境验证——**Linux CI 字体环境是 WI5 golden 基线与 WI9 对版的确定性风险**，届时若 CI 缺中文字体，需在 CI 配置安装字体或在 golden 生成环境固定字体。
2. 缺字体显式报错（roadmap Cross-Cutting #5），不允许静默回退到不含中文字形的字体。

## Verification（Phase 2 PoC 实测补充，2026-10-09）

**PoC 位置**：`nop-format/nop-chart-echarts` src/test（spike 包 + spike/*.js 资源），运行命令 `./mvnw test -pl nop-format/nop-chart-echarts -Dtest=TestEchartsFirstRenderPoC`。

**首图证据**：600×400 PNG，含中文标题「中文柱状图 PoC」、中文类目轴（一月～五月）、5 根 #5470c6 柱（150/230/224/218/135）与数据标签、y 轴刻度与网格线，与浏览器端 bar 渲染语义一致。附件：`ai-dev/analysis/2026-10/wi0-first-render.png`（测试运行时同步产出 target/wi0-first-render.png）。像素断言：非白 66,824 px、柱色 59,946 px、深色文本 1,229 px。

**三点 JDK 运行证据**（同一测试，均绿）：

| JDK | 发行版 | 结果 | 备注 |
|-----|--------|------|------|
| 21.0.12.1 | Temurin（_tmp/jdk-21.0.12.1+1，下载自 api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64） | PASS（2.6s） | 警告 "runtime 21 incompatible with optimized Truffle runtime 25.2.4 / must be >= 25 < 26"——JDK 21 下 libgraal 优化运行时不可用，回退纯 Java 解释模式，功能完好；CI（JDK 21）可用此形态 |
| 25.0.1 | Oracle JDK（本机） | PASS（2.5s） | 无警告 |
| 26.0.1 | Zulu（本机默认） | PASS（2.6s） | 同 JDK<26 边界警告，功能完好 |

**裸环境红线断言**：测试内断言 `typeof window/document/navigator/self === 'undefined'` 全部成立；dist 未注入 window/document/navigator，env.node=true → animation:false 自动生效。

**依赖闭包证据**（`./mvnw dependency:tree -pl nop-format/nop-chart-echarts`）：polyglot 25.2.4、polyglot:js 25.2.4 → js-language 25.2.4 → truffle-api/truffle-runtime/truffle-compiler **均 25.2.4**，与 nop-xlang-truffle 单线对齐达成；org.mvnpm:echarts:6.1.0 在 classpath 且 tslib/zrender 已排除。已知残留：quarkus-bom 将 org.graalvm.sdk:nativeimage/word 压到 23.1.2——这是 native-image 编译期注解 SDK（quarkus 原生构建自身依赖），对 JVM 内嵌执行无运行时影响，保留 quarkus 管辖不动。

**关键工程发现（WI4 设计约束）**：GraalJS interop 下，zrender 对 `measureText(...)` 返回值直接读 `.width` 属性；Java 对象的属性读取不透明映射 getter（getWidth()），读出 undefined → 轴标签矩形 NaN → `Grid.resize` 的 outerBounds 收缩逻辑把 gridRect 污染为全 NaN → 轴与系列全部静默消失（echarts 无 warning、无异常）。修复：JS/Java 边界上的**数据形态值必须用纯 JS 对象封装**（shim 中 measureText 返回 `{width: metrics.getWidth()}`），Java 对象仅用于方法调用型后端。此约束对 WI4 引擎桥与 WI1-3 canvas2d 的 API 形态有直接指导意义；同期发现 dist dev 构建在无 window 时读裸 `global` 标识符，裸环境需注入 `global.global = global`（roadmap 注入清单本就含 global）。

**验证命令口径**：`./mvnw test -pl nop-format/nop-chart-echarts -am` 全 reactor 17 模块 BUILD SUCCESS（本地默认 JDK 26）。

## Conclusion / Decision

1. **GraalJS**：org.graalvm.polyglot:polyglot:25.2.4 + org.graalvm.polyglot:js:25.2.4；BOM 属性与 dependencyManagement 条目落 nop-dependencies，删除 23.1.2 三处残留。
2. **echarts 资源**：org.mvnpm:echarts:6.1.0，排除 org.mvnpm:tslib、org.mvnpm:zrender；资源路径 META-INF/resources/_static/echarts/6.1.0/dist/echarts.js。
3. **字体**：mac 环境逻辑 SansSerif + 物理 PingFang SC 均可渲染中文；FontMapper（WI2）按可配置 + 显式报错设计；CI Linux 字体环境在 WI5 前复核。

后续工作由 plan 2358（WI0 执行）与 roadmap WI1–WI4 接手。
