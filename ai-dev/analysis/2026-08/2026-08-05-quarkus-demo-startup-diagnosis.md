# Quarkus Demo 启动速度诊断方案调研（火焰图 / JFR / 内置计时）

> Status: open
> Date: 2026-08-05
> Scope: `nop-demo/nop-quarkus-demo` 的 `QuarkusDemoMain` 启动链路诊断工具与方法；结论同样适用于仓库内所有 `*-app` Quarkus 模块（`nop-auth-app`、`nop-job-app` 等）
> Conclusion: 推荐「内置计时做基线 → JFR 做整体采样 → async-profiler wall 事件火焰图做热点定位 → 优化后 diff 火焰图验证」四步工作流

---

## 一、Context / 背景

- 要回答的问题：`QuarkusDemoMain`（`nop-demo/nop-quarkus-demo`）从 `java -jar` 到服务可用的耗时花在哪，如何用火焰图等工具系统化诊断，而不是靠猜。
- 约束：macOS 开发机（`perf` 不可用）；项目目标 JDK 21（本机当前 Zulu 26 CRaC）；启动方式既有 `java -jar runner.jar`（uber-jar，`build-uber-jar.sh`）也有 IDE 直接启动 Main。
- 现状：`docs-for-ai/` 无任何 JFR/火焰图/启动性能诊断内容（grep 0 命中），属空白领域。
- 本文只做「怎么诊断」，不做优化本身；优化建议仅在诊断能定位后作为下一步（后续可转 `ai-dev/plans/`）。

## 二、现状梳理：启动链路与已有计时点

### 2.1 启动时序（代码实证）

```
java -jar ...-runner.jar
 ├─ JVM boot（类加载、JIT、CDI 等 Quarkus build-time 处理）   ← 无内置日志
 ├─ Quarkus bootstrap：扩展处理、Vert.x、HTTP server 初始化
 ├─ @Observes StartupEvent 触发
 │    ├─ QuarkusIntegration.start()   # nop-quarkus-core-starter:26-52（profile 桥接 + BeanContainer 注册）
 │    └─ new NopApplication().run()   # nop-boot NopApplication.java:32-73
 │         ├─ logStarting: "Starting QuarkusDemoMain ..."（含 hostname 解析，>200ms 会 WARN）
 │         ├─ CoreInitialization.initialize()  # nop-kernel/nop-core/initialize/CoreInitialization.java:82-127
 │         │    ├─ nop.core.begin-initialize
 │         │    ├─ nop.core.run-initializer:class=...   # 每个 ICoreInitializer 一条
 │         │    └─ nop.core.end-initialize:usedTime=... # 初始化器总耗时
 │         ├─ banner 打印
 │         ├─ logStarted: "Started QuarkusDemoMain in X seconds (JVM running for Y)"  # StartupInfoLogger.java:141-156
 │         └─ nop.app.run-success:usedTime=...   # NopApplication 段总耗时
 └─ 服务可用
```

### 2.2 已有计时点能直接回答的

| 日志行 | 覆盖区间 | 来源 |
|---|---|---|
| `Started ... in X seconds (JVM running for Y)` | X = Quarkus StartupEvent 到 CoreInit 完成；**Y − X = Quarkus bootstrap + JVM 启动** | `StartupInfoLogger.java:141` |
| `nop.core.end-initialize:usedTime=` | 全部 ICoreInitializer 串行初始化 | `CoreInitialization.java:115` |
| `nop.core.run-initializer:class=` | 单个 initializer 的开始（相邻两条时间差 = 单个耗时） | `CoreInitialization.java:106` |
| `nop.app.run-success:usedTime=` | NopApplication 段总耗时（≈ 与 X 基本重合） | `NopApplication.java:65` |

- 三个数字即可把总耗时切成两段：**Quarkus/JVM 段** vs **Nop CoreInit 段**，无需任何工具。
- 局限：只有阶段级，没有方法级；无法回答「某个 initializer 内部哪一步最贵」。

### 2.3 与启动速度强相关的既有配置（热点候选）

`nop-quarkus-demo/src/main/resources/application.yaml`：

- `nop.orm.init-database-schema: true` — 启动时对 MySQL 执行 DDL 建表（网络 + DDL 都可能耗时）
- `nop.web.validate-page-model: true` — 页面模型全量校验（4 线程，`page-validation-thread-count`）
- `%dev` 下 `nop.debug: true` — 启动期向 `_dump/` 写大量合并模型文件（GraphQL schema、merged beans、i18n 等），本身即耗时且拖慢后续每次启动
- `%dev` 下 `ioc.app-beans-container.concurrent-start: false` — bean 容器**串行**启动（生产默认可能并发）
- `quarkus.log.category "io.nop": DEBUG` — DEBUG 日志量大时本身是启动开销
- 数据源为 `jdbc:mysql://127.0.0.1:3306/dev` — 连接建立与探测依赖本机 MySQL 是否在线

## 三、诊断目标分解

把「启动慢」拆成可测量的问题：

1. **整体基线**：一次启动总耗时 = ?（`time` + 日志时间戳）
2. **阶段切分**：JVM/Quarkus 段 vs Nop CoreInit 段各占多少（§2.2 三个日志行）
3. **热点定位**：CoreInit 内部（VFS 扫描 / XLang 模型解析 / bean 创建 / ORM 初始化 / 页面校验 / DB DDL）谁最贵 → 需要方法级采样
4. **等待类开销**：启动期大量时间花在 IO/锁/睡眠（wall-clock），CPU 采样会"看不见"它们 → 必须用 wall-clock 事件
5. **回归验证**：优化前后同条件对比（diff 火焰图 / 时间差）

## 四、工具调研对比

### Option A：内置日志计时（零成本基线）

- 思路：用 §2.2 的既有日志 + `nop.core.run-initializer` 相邻时间差粗粒度分段；`time java -jar ...` 测总耗时。
- 优点：零安装、零开销、立即可用；适合先确认「问题在 Quarkus 段还是 Nop 段」。
- 缺点：方法级以下全盲；对多线程并发段（页面校验 4 线程）时间差不可靠。
- 适用：第一轮定位大阶段。

### Option B：JFR（JDK 内置，推荐首选采样器）

- 思路：JDK 21 自带 Flight Recorder，记录类加载、GC、线程状态、方法采样（`jdk.ExecutionSample`）、IO/锁/睡眠事件。
- 命令（启动即录，覆盖完整启动）：

```bash
java -XX:StartFlightRecording=filename=_tmp/startup.jfr,settings=profile,disk=true,dumponexit=true \
     -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
```

- 分析：
  - `jfr summary _tmp/startup.jfr`（事件概览）
  - `jfr view hot-methods _tmp/startup.jfr` / `jfr view compilation _tmp/startup.jfr`（JDK 16+ 自带 CLI，无需 JMC）
  - 想看阻塞：`jfr print --events jdk.ThreadSleep,jdk.JavaMonitorEnter,jdk.FileRead,jdk.SocketRead _tmp/startup.jfr`
  - GUI：JDK Mission Control（JMC）打开 `.jfr`，有线程/内存/IO 视图
  - 转火焰图：见 §五
- 优点：**JDK 内置零安装**；事件丰富（含 `jdk.ClassLoad`、GC、线程状态，正好覆盖启动三大嫌疑：类加载、解析、IO）；可 `dumponexit` 自动收尾；macOS 上开箱即用。
- 缺点：采样间隔相对粗（默认 20ms）；火焰图需要转换步骤；JFR 默认 profile 设置不含 IO 明细事件需另配。
- 适用：**整体采样 + 阻塞归因**的首选。

### Option C：async-profiler（火焰图首选）

- 思路：采样型 profiler，直接产出 HTML 火焰图；支持 `cpu` / `wall` / `alloc` / `lock` 等事件。**启动诊断关键是 `wall` 事件**——它按墙上时钟采样所有线程状态，能看见睡在 IO/锁上的时间；纯 `cpu` 事件会漏掉启动期大量等待。
- 获取：GitHub `async-profiler/async-profiler` releases 解压（`profiler.sh` + `libasyncProfiler.dylib`）。

**用法 1：agent 方式（从 JVM 启动即录，推荐）**

```bash
java -agentpath:/path/to/libasyncProfiler.dylib=start,event=wall,file=_tmp/startup-flame.html \
     -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
# 看到 "Started QuarkusDemoMain in ..." 后停止采样：
/path/to/profiler.sh stop <pid>
```

**用法 2：attach 方式（错过早期，但无需重启）**

```bash
java -jar ...-runner.jar &
/path/to/profiler.sh -d 60 -e wall -f _tmp/startup-flame.html <pid>
```

- 补充事件：`-e alloc`（大对象/解析产生大量分配）；`-e lock`（锁竞争）。
- 输出 JFR 兼容格式便于 JMC 看：`-o jfr`。
- 优点：火焰图开箱即用；wall/alloc/lock 事件对启动诊断极其对口；`jfr2flame` 可直接吃 JFR；支持两份火焰图 diff。
- 缺点：macOS 上 **CPU 事件需要 root/`sudo`**（kperf 权限），wall 事件不需要（所以 macOS 上默认就用 wall）；不是 JDK 内置，需下载。
- 适用：**热点定位 + 优化前后对比**的首选。

### Option D：jstack 循环采样（极简兜底）

- 思路：启动期间每 500ms 打一次 `jstack <pid>`，人工数出现次数最多的栈帧。

```bash
while kill -0 $pid 2>/dev/null; do jstack $pid >> _tmp/startup-stacks.txt; sleep 0.5; done
```

- 优点：零安装、任何 JDK 可用。
- 缺点：粗（采样数少）、费人工、污染启动线程（本身会触发 safepoint 停顿）。
- 适用：没有任何工具时的临时手段，不推荐作为主力。

### Option E：辅助日志（配合 A/B/C 归因）

- 类加载：`-Xlog:class+load=info:file=_tmp/classload.log` — 验证是否类加载过多（JIT 前类加载在启动占比高）。
- GC：`-Xlog:gc:file=_tmp/gc.log` — 排除 GC 抖动（JFR 也能看）。
- VFS：Nop 有 `nop.core.resource.use-nop-vfs-index`（`CoreConfigs.java:204-205`，原生镜像必需），JVM 模式默认关；若火焰图显示 VFS/classpath 扫描是热点，可试开启对比（demo 已预生成 `nop-vfs-index.txt`）。
- AppCDS：JDK 21 支持 `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=_tmp/app.jsa`，用于加速 JVM 启动与类加载——**这是诊断之后的提速手段**，用于验证「类加载占总耗时的比例」。

### 对比表

| 维度 | A 内置日志 | B JFR | C async-profiler | D jstack 循环 | E 辅助日志 |
|---|---|---|---|---|---|
| 安装成本 | 0 | 0 | 下载一个包 | 0 | 0 |
| 方法级热点 | ✗ | ✓ | ✓ | △（手工） | ✗ |
| 阻塞/等待归因 | ✗ | ✓ | ✓（wall/lock） | △ | ✗ |
| 火焰图 | ✗ | 需转换 | **直接输出** | ✗ | ✗ |
| 覆盖启动全过程 | ✓ | ✓（agent 内置于 VM init） | ✓（agent 方式） | ✓ | ✓ |
| macOS 兼容 | ✓ | ✓ | wall ✓ / cpu 需 sudo | ✓ | ✓ |
| diff 对比 | 时间差 | 需转换后 diff | **内置 diff** | ✗ | 文件对比 |

## 五、火焰图生成与解读

### 5.1 JFR → 火焰图（async-profiler 的 jfr2flame）

```bash
/path/to/profiler.sh jfr2flame _tmp/startup.jfr _tmp/startup-jfr-flame.html
```

- 另有纯 JDK 路线：`jfr print --events jdk.ExecutionSample --stack-depth 128 _tmp/startup.jfr` 导出采样 → Brendan Gregg 的 `FlameGraph` 脚本（`stackcollapse-jstack.pl` + `flamegraph.pl`）——但 jfr2flame 更省事。

### 5.2 优化前后 diff

```bash
/path/to/profiler.sh -d 60 -e wall -f _tmp/after-flame.html <pid>   # 优化后
/path/to/profiler.sh diff _tmp/before-flame.html _tmp/after-flame.html
```

- diff 火焰图红色 = 变慢的帧，绿色 = 变快的帧；用于验证「改了一个配置/一段代码」的净效果。
- 对比前提：同机器、同 JVM 参数、同 profile（`-Dquarkus.profile=dev` 与否必须一致）、同数据源状态、尽量多次取中位数。

### 5.3 解读要点（启动场景特有）

- **先看 wall 火焰图主栈**：宽帧 = 占墙钟时间多。启动期宽帧典型是：VFS 资源扫描、XML/XLang 模型解析（`DslModelParser`/XDef 校验）、bean 创建链、JDBC 连接/DDL、页面模型校验。
- **区分 CPU 与等待**：同一帧在 cpu 图里窄、wall 图里宽 = 等待/IO；两图都宽 = 纯计算热。**两个事件各出一张图对比**是启动诊断的标准姿势。
- **看线程名**：很多耗时在 `Thread-x`/`pool-x`（并发校验线程），主线程反而窄——说明瓶颈已并行化，这时看 `-e alloc` 或按线程过滤。
- **结合阶段日志**：火焰图的时间轴对不上日志阶段时，以日志时间戳为准切窗口（JFR 有精确时间戳，可对 `nop.core.run-initializer` 逐条对齐）。

## 六、推荐工作流（四步）

```bash
# 0. 预热目录
mkdir -p _tmp

# 1. 基线（内置计时，零工具）
time java -Dquarkus.profile=dev -jar nop-quarkus-demo/target/nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar \
    2>&1 | tee _tmp/baseline.log
#    记录三段：Y−X（Quarkus/JVM）、end-initialize usedTime、run-success usedTime

# 2. JFR 整体采样
java -XX:StartFlightRecording=filename=_tmp/startup.jfr,settings=profile,disk=true,dumponexit=true \
     -Dquarkus.profile=dev -jar ...-runner.jar
jfr summary _tmp/startup.jfr && jfr view hot-methods _tmp/startup.jfr

# 3. async-profiler wall 火焰图定位热点
java -agentpath:$HOME/tools/async-profiler/libasyncProfiler.dylib=start,event=wall,file=_tmp/startup.html \
     -Dquarkus.profile=dev -jar ...-runner.jar &   # 看到 Started 后 profiler.sh stop <pid>
#    对照 -e cpu 图区分计算/等待；必要时 -e alloc、-e lock 补图

# 4. 定位后按 §2.3 热点候选逐个开关验证（改配置或代码）→ 重复步骤 3 出 diff 图
```

- 每次对比前固定条件清单：profile、MySQL 是否在跑（`nop.orm.init-database-schema` 依赖它）、`nop.debug` 开闭、`_dump/` 是否已生成、IDE vs `java -jar`（IDE 直接启动 Main 会因 Quarkus build-time 处理缺失导致行为差异，`docs-for-ai/02-core-guides/debugging-and-diagnostics.md:252` 有说明）。

## 七、结论

- **被否决的方案**：
  - 仅用 jstack 循环采样：精度不够且干扰启动，只作无工具兜底（D）。
  - 仅用 `-e cpu` 火焰图：启动期大量等待时间对 cpu 事件不可见，会得到"看起来没热点"的错误结论（C 的 cpu 模式单用）。
  - 手工在框架代码加 System.currentTimeMillis 打点：属于改框架核心（`nop-core` 为 plan-first 保护区），且并发段不可靠；既有 `nop.core.run-initializer` 日志已覆盖阶段级需求。
- **推荐组合**：内置日志（A）做阶段基线 → JFR（B）做整体采样与阻塞归因 → async-profiler wall 火焰图（C）做热点定位与 diff 验证 → 辅助日志（E）按需归因类加载/GC。工具链全部可用 macOS + JDK 21，无框架代码改动。
- **首次诊断的优先怀疑对象**（来自 §2.3 配置）：`init-database-schema` DDL、`validate-page-model` 页面校验、`nop.debug` 的 `_dump` 写盘、Quarkus bootstrap 段（若 Y−X 占比大则与 Nop 无关）。
- 后续工作：定位到具体热点后，优化方案与验证应转入 `ai-dev/plans/`；本报告结论如被实测推翻，标 `superseded`。

## Open Questions

- [ ] JVM 模式下 VFS classpath 扫描的真实占比（若 wall 图显示 `ResourceHelper`/VFS 扫描宽帧，`use-nop-vfs-index` 开关值得实测对比）
- [ ] `concurrent-start: false`（`%dev`）相对生产默认的差距量化
- [ ] 本机 Zulu 26 CRaC 与项目目标 JDK 21 在启动耗时上的差异（CRaC 为容器快照设计，直接对比可能失真）

## References

- `nop-demo/nop-quarkus-demo/src/main/java/io/nop/demo/quarkus/QuarkusDemoMain.java`
- `nop-core-framework/nop-boot/src/main/java/io/nop/boot/NopApplication.java:32-73`
- `nop-core-framework/nop-boot/src/main/java/io/nop/boot/StartupInfoLogger.java:141-156`
- `nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:82-127`
- `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java:204-205`（`use-nop-vfs-index`）
- `docs-for-ai/02-core-guides/debugging-and-diagnostics.md`（Quarkus 应用启动/调试：:252）
- `docs-for-ai/01-repo-map/module-groups.md`（`nop-quarkus` 模块组）
- 外部：async-profiler（github.com/async-profiler/async-profiler）、JDK `jfr` CLI（`jfr --help`）
