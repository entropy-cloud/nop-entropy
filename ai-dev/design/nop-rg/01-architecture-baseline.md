# nop-rg Architecture Baseline

**日期**：2026-09-18
**状态**：草案

---

## 一、模块结构

```
nop-rg/
├── nop-rg-core/          核心：接口、标量搜索、Glob、I/O、协调器
├── nop-rg-vector/        可选：Vector API 加速实现
├── nop-rg-cli/           命令行入口
└── nop-rg-bom/           统一版本管理
```

**设计决策**：移除了原设计中的 `nop-rg-index` 模块。FM-Index 依赖 index4j，维护状态不确定，且对动态文件系统场景价值有限。如果未来确需 FM-Index 加速，作为 `nop-rg-core` 的可选依赖处理，不单独建模块。

### 模块依赖

```
nop-rg-bom          (管理所有模块版本)
nop-rg-cli          → nop-rg-core (必须)
                    → nop-rg-vector (可选)
nop-rg-vector       → nop-rg-core
nop-rg-core         → nop-core (复用 GitIgnoreFile)
```

### 包结构

```
io.nop.rg.core
├── io.nop.rg.core.search       搜索策略接口与标量实现
├── io.nop.rg.core.glob         Glob 匹配器
├── io.nop.rg.core.io           内存映射文件读取
├── io.nop.rg.core.walk         并行文件遍历
├── io.nop.rg.core.coordinator  搜索协调器

io.nop.rg.vector                Vector API 实现
io.nop.rg.cli                   CLI 入口
```

## 二、核心设计决策

### 决策 1：最低 JDK 22+，FFM API 直接使用

**选择**：`nop-rg-core` 最低 JDK 22+，在 pom.xml 中直接指定 `maven.compiler.release=22`；nop-rg 挂入根 reactor 时复用仓库既有的 JDK 激活 profile 门控模式（同 `nop-utils` 的 `java21-modules`，按 JDK ≥ 22 激活），JDK < 22 的构建环境自动跳过 nop-rg 模块
**理由**：
- FFM API（MemorySegment + Arena）在 JDK 22 正式版，提供确定性资源回收
- 消除 2GB 文件大小限制
- 不做 JDK 17 降级——nop-rg 是独立工具模块，不要求与项目全局 JDK 版本对齐
- 根 POM 全局 `release=17`，若 nop-rg 无门控直接进入根 `<modules>`，JDK 17/21 环境会因 `release=22` 编译失败、阻断整个仓库构建；仓库已有 `nop-utils` `java21-modules`（JDK ≥ 21 激活）先例

**拒绝了什么**：
- 无门控直接加入根 POM `<modules>`：低版本 JDK 构建直接失败
- MappedByteBuffer 降级：资源回收不可控，违背设计初衷

### 决策 2：复用 GitIgnoreFile，抽取到 nop-core

**选择**：将 `nop-ai-code-analyzer` 中的 `GitIgnoreFile.java`（417 行）抽取到 `nop-core`（新包 `io.nop.core.git`）作为公共库，`nop-rg-core` 依赖 `nop-core` 复用
**理由**：
- `GitIgnoreFile` 已是完整的 `.gitignore` 模式解析器，支持 `*`、`**`、`?`、`[...]`、`!` 反转、`/` 目录限定、anchored/unanchored 规则
- 它实现 `Predicate<IResource>`，依赖 `io.nop.core.resource` 的 `IResource` / `VirtualFileSystem` / `ResourceHelper`，只能上移到 `nop-core`；`nop-commons` 位于依赖下层（nop-core 依赖 nop-commons），放 nop-commons 会成环
- 依赖 Nop 平台的 `IResource` / `VirtualFileSystem` API，与 nop-rg 的 I/O 层自然契合
- 避免重复实现，保持 gitignore 语义一致性
- `nop-ai-code-analyzer` 和 `nop-cli-core` 已是该代码的消费者，两者本就依赖 `nop-core`，抽取后仅需更新 import 路径

**当前消费者**：
- `io.nop.ai.code_analyzer.project.GitProject` — 项目级 gitignore 过滤
- `io.nop.cli.commands.CliFileCommand` — `nop file path-tree` 和 `nop file find` 命令

**拒绝了什么**：
- 抽取到 `nop-commons`：需要反向依赖 nop-core，Maven 循环依赖
- 抽取到 `nop-utils/nop-git`：该模块绑定 JGit，会拖入重量级传递依赖
- 自实现 gitignore 解析器：已有完整实现，重复造轮子
- 引入 JGit 依赖：重量级（~15MB），对搜索工具不合理

### 决策 3：标量搜索默认路径 — Boyer-Moore-Horspool + 最罕见字节启发式

**选择**：自实现标量 BMH 搜索，不依赖第三方搜索库
**理由**：
- ripgrep 的核心优化在于选择模式中最罕见的字节作为锚点，而非传统最后一个字节
- 纯标量实现无外部依赖
- 对典型代码搜索场景（短模式、大文件）效果显著

**拒绝了什么**：
- java.util.regex：对字面量搜索开销过大，无法利用跳跃优化
- 第三方搜索库：引入不必要依赖

### 决策 4：并行文件遍历使用专用 ExecutorService

**选择**：专用线程池而非 ForkJoinPool.commonPool()
**理由**：
- 文件遍历是 I/O 密集型，commonPool 默认线程数=CPU 核心数，利用率不足
- 专用线程池可配置更大并发度，避免与其他任务竞争

**实现期补充（plan 2265）**：专用 **ForkJoinPool**（work-stealing，目录级 RecursiveAction 分治）属于本决策范围——决策反对的是 commonPool 共享池，专用 FJP 满足"可配置并发度、不与其他任务竞争"的决策意图；walker 与 coordinator 均采用。

### 决策 5：搜索策略模式

**选择**：搜索策略分层——字节域策略接口 + 正则独立接口。

**字节域策略契约**（字面量搜索）：

```java
public interface ByteSearchStrategy {
    long findPattern(MemorySegment seg, long offset, long limit, byte[] pattern);
    long findFirstByte(MemorySegment seg, long offset, long limit, byte target);
}
```

**实现层级**（plan 2264/2266/2267 修订：RegexSearcher 移出字节域接口；-i 折叠由 PreparedLiteral 承担（FoldingByteSearcher 已删除，f5d44be31e）；Vector 经 PreparedFinder + SPI 接入并带模式长度阈值策略）：

| 实现 | 依赖 | 启用条件 |
|------|------|---------|
| ScalarByteSearcher / PreparedLiteral | 无 | 默认（区分大小写；`-i` 走 PreparedLiteral ASCII 折叠） |
| VectorPreparedLiteral（SIMD findPattern + findFirstByte） | jdk.incubator.vector | `--vector` 显式开关 + JDK 25+；SPI 工厂内 API 不可用时降级标量；**模式 < 16 字节返回标量等价（plan 2267 R1 长度阈值策略：6B SIMD = 标量 72-80%，16B 起反超 2.5-3.5x）** |
| VectorByteSearcher.findFirstByte | jdk.incubator.vector | 契约对齐实现（memchr 向量化，暂无生产消费者） |

**发现机制（plan 2266 裁定）**：core 定义 `LiteralFinderProvider` SPI（`compile(pattern, ignoreCase) → PreparedFinder` + `available()` + 降级原因），vector 模块经 META-INF/services 注册（ServiceLoader）。两条失败面可区分：ServiceLoader 无 provider = classpath 缺 nop-rg-vector（coordinator 显式报错）；provider.available() 为 false = 孵化模块未 add-modules（provider 内降级标量 + 降级原因可获取）。coordinator 两条字面量路径消费 `PreparedFinder` 接口（`find`/`patternLength`），Vector 与标量实现可互换。

**正则回退**（独立接口，vision 成功标准 4）：`RegexSearcher` 基于 java.util.regex + 整文件 UTF-8 解码（非法字节走 replacement char，与 rg 纯字节域的已知偏差），char→字节偏移桥接映射；`ignoreCase` 用 `CASE_INSENSITIVE` 且不带 `UNICODE_CASE`。独立成接口的原因：正则作用于字符域而非字节域，无法满足 `ByteSearchStrategy` 的 `MemorySegment` 签名语义（plan 2264 执行期裁定，取代初版"实现 ByteSearchStrategy"的表述）。

### 决策 6：JFR 性能诊断

**选择**：使用 Java Flight Recorder (JFR) 作为性能诊断工具
**理由**：
- JFR 是 JDK 内置工具，零额外依赖，生产环境开销极低（<1%）
- 可记录搜索过程中的 CPU 采样、内存分配、锁竞争等事件
- 配合 `jfr print` 或 JMC 进行分析，定位性能瓶颈
- 比 JMH 更适合端到端场景的性能诊断（JMH 适合微基准，JFR 适合真实负载）

**实现期事实修正（plan 2265 实测）**：
- `jdk.FileRead` 只钩 read 类 API，对 mmap 路径恒无事件——内存映射 I/O 的诊断依赖 ExecutionSample/缺页表现，FileRead 设置仅作占位。
- `Configuration` 加载 API 为 `create(Path/Reader)`（无 fromFile）；.jfc 事件名必须用 `<event name="jdk.X">` 属性形式；JDK 17+ 分配事件名为 `jdk.ObjectAllocationInNewTLAB/OutsideTLAB`。
- 短/空闲录制下 ExecutionSample 可能为 0（周期采样需 Java 线程实际运行）。

**使用场景**：
- 搜索大文件时的内存分配热点
- 并行搜索的线程利用率和锁竞争
- I/O 等待时间占比
- GC 停顿对搜索延迟的影响

## 三、分层架构

```
┌─────────────────────────────────────────────┐
│              CLI / API 入口层                │
│            nop-rg-cli / nop-rg-core         │
├─────────────────────────────────────────────┤
│         搜索协调器 SearchCoordinator         │
│  （任务分发、结果聚合、并行控制、策略选择）    │
├──────────────┬──────────────────────────────┤
│ GlobMatcher  │      ContentSearcher         │
│ (文件名匹配) │  ScalarByteSearcher (默认)   │
│              │  VectorByteSearcher (可选)    │
├──────────────┴──────────────────────────────┤
│              I/O 抽象层                      │
│     MemorySegment + Arena (JDK 22+)         │
├─────────────────────────────────────────────┤
│            并行文件遍历层                    │
│    专用 ExecutorService + FileTreeWalker     │
├─────────────────────────────────────────────┤
│         .gitignore 解析层                   │
│    GitIgnoreFile (nop-core 公共库复用)       │
└─────────────────────────────────────────────┘
```

## 四、性能诊断与调优

### JFR 集成

搜索 CLI 提供 `--jfr` 开关，启动 JFR 记录：

```bash
nop-rg "pattern" --jfr /path/to/search
```

JFR 配置文件 `nop-rg-jfr.jfc` 包含：
- `jdk.ExecutionSample` — CPU 热点
- `jdk.AllocationInNewTLAB` / `jdk.AllocationOutsideTLAB` — 内存分配热点
- `jdk.JavaMonitorWait` — 锁竞争
- `jdk.FileRead` / `jdk.FileWrite` — I/O 事件
- `jdk.GCHeapStatistics` — GC 行为

### JMH 基准测试

`nop-rg-benchmark` 模块提供微基准：
- 标量搜索吞吐量（ops/sec）
- Glob 匹配吞吐量
- 不同文件大小下的搜索延迟
- 与系统 rg 的端到端对比

## 五、测试策略

| 层级 | 方法 | 工具 |
|------|------|------|
| 单元测试 | Glob 边界、BMH 正确性、跨块匹配 | JUnit 5 |
| 集成测试 | 与系统 rg 结果对比、大文件搜索、并发安全 | JUnit 5 + ProcessBuilder |
| 性能基准 | 标量 vs Vector（VectorCompareBenchmark，SPI 发现 + 降级警示）、端到端吞吐量与 rg 对比（CoordinatorEndToEndBenchmark/RgCompareBenchmark）、不同文件大小（@Param 1MB/64MB/512MB） | JMH |
| 性能诊断 | 真实负载下的 CPU/内存/I/O 分析 | JFR |

## 六、与外部系统的关系

- **ripgrep**：默认行为参考对象，`--delegate-rg` 可委托执行
- **nop-search**：不依赖、不交互，服务不同场景（文件扫描 vs 索引搜索）
- **nop-core**：复用 `GitIgnoreFile`（从 `nop-ai-code-analyzer` 抽取到 `io.nop.core.git`）
- **JGit**：参考其 FastIgnoreRule 设计思路，不引入依赖
