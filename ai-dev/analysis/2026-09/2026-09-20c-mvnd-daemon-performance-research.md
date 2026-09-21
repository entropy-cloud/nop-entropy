# mvnd (Maven Daemon) 性能优化与驻留机制调研报告

> Status: resolved
> Date: 2026-09-20
> Scope: 外部项目调研——`~/sources/mvnd`（mvnd 2.0.0-rc-4-SNAPSHOT，基于 Maven 4.0.0-rc-6）与 `~/sources/maven`（Maven 4 源码）；不涉及 nop-entropy 代码改动
> Conclusion: mvnd 的性能优势来自"瘦客户端 + 常驻 daemon JVM"架构（JVM/JIT 预热、Maven 对象图、插件类加载域跨构建复用，文件监听保证缓存安全）加默认并行构建；daemon 默认空闲 3 小时自退，将 `mvnd.idleTimeout` 在 `~/.m2/mvnd.properties` 中设为超大值即可长久驻留，但系统内存紧张等硬编码过期策略仍可能回收它且无配置开关；mvnd 原生支持 `mvnd -1`/`--serial` 做构建内串行，但多次调用之间没有排队机制，并发请求会各自起新 daemon 并行执行，跨调用串行只能靠外部加锁。

## Context

- 决策点：评估 mvnd 能否用于加速 nop-entropy 这类大型多模块 Maven 构建，并回答三个具体问题：
  1. mvnd 性能优化的原理是什么？
  2. 如何让 mvnd daemon 长久驻留不被回收？
  3. 能否控制发给 mvnd 的指令串行执行？
- 调研方式：直接阅读 mvnd 与 Maven 源码（非文档推断），全部结论带源码锚点。
- 调研对象版本：`org.apache.maven.daemon:mvnd:2.0.0-rc-4-SNAPSHOT`（`pom.xml`），父 POM `org.apache.maven:maven-parent:49`，依赖 Maven `4.0.0-rc-6`（`pom.xml` 的 `maven.version` 属性），git HEAD `53dadc00`。下文源码路径省略前缀 `~/sources/mvnd/`；Maven 侧标注 `[maven]`，省略前缀 `~/sources/maven/`。

## 调研目标

- mvnd 相对普通 `mvn` 的性能优化原理（架构层面 + 缓存层面 + 并行层面）
- daemon 生命周期管理：何时退出、过期策略全集、如何控制长久驻留
- 串行执行能力：构建内串行（模块级）与跨调用串行（多次 mvnd 命令之间）

## 调研结果

### 1. 架构总览：瘦客户端 + 常驻 daemon

- 主要模块包括：`client`（客户端）、`common`（协议/注册表/环境）、`daemon`（守护进程）、`native`（JNI 本地库）、`agent`、`helper`、`dist`（发行版）、`integration-tests`（另有 `build`/`build-plugin`/`logging` 等支撑模块）。
- 客户端是 GraalVM native-image 编译的原生二进制（`client/pom.xml` 使用 `org.graalvm.nativeimage` 与 `org.graalvm.buildtools`），另有 `client/src/main/java-fallback/` 的纯 Java 回退实现。原生二进制的进程启动开销远低于 JVM 冷启动。
- 客户端通过 socket 把 BuildRequest（参数 + 环境变量）发给 daemon；连接需校验 per-daemon token（`daemon/.../Server.java` 的 `checkToken`，常量时间比较）。socket 类型由 `mvnd.socketFamily`（DISCRIMINATING）控制，默认 `inet`。
- daemon 端注册表 `common/.../DaemonRegistry.java` 以文件形式记录各 daemon 的状态（Idle/Busy/Canceled）、兼容性选项与 stop events，供客户端发现与复用。
- 运维命令：`mvnd --status`（列 daemon 状态）、`mvnd --stop`（停全部）、`mvnd --purge`（按 `mvnd.logPurgePeriod` 默认 7 days 清旧日志），实现在 `client/src/main/java-mvnd/org/mvndaemon/mvnd/client/DefaultClient.java`。

### 2. 性能优化原理（四个层面）

#### 2.1 常驻 daemon：Maven 对象图跨构建存活（核心）

- `daemon/.../Server.java` 构造时通过反射加载并持有**一个** `DaemonCli` 实例（即 `org.apache.maven.cli.DaemonMavenCling`，Server.java:85、127），贯穿 daemon 整个生命周期；每次构建请求只是重复调用 `cli.main(...)`（Server.java:631）。
- `org.apache.maven.cli.DaemonMavenInvoker extends ResidentMavenInvoker`。`ResidentMavenInvoker` 是 Maven 4 cling 提供的驻留模式（`[maven] impl/maven-cli/src/main/java/org/apache/maven/cling/invoker/mvn/resident/ResidentMavenInvoker.java`）；`DaemonMavenCling` 的 javadoc 明确写着 "daemon invoker is stateful (keeps Maven object graph)"。
- 效果：JVM 启动、类加载、JIT 预热、Plexus 容器、ClassWorld realm、扩展加载全部只付一次成本，后续每次构建是热启动。普通 `mvn` 每次都要冷启动 JVM 并重建整个对象图。
- 每次构建结束后 daemon 会清理 JDK 内部 `JarFileFactory` 的 `urlCache`/`fileCache`（`Server.client()` 的 finally 块，反射调用 `clearCache`），防止跨构建内存泄漏。

#### 2.2 跨构建缓存 + 文件监听失效

- `daemon/.../cache/invalidating/` 下五个类，以 `@Singleton @Named @Priority(10)` 的 Sisu bean 覆盖 Maven 默认缓存实现：
  - `InvalidatingPluginRealmCache`（插件类加载域）
  - `InvalidatingPluginDescriptorCache`（插件描述符）
  - `InvalidatingPluginArtifactsCache`（插件构件解析结果）
  - `InvalidatingProjectArtifactsCache`（项目构件解析结果）
  - `InvalidatingExtensionRealmCache`（核心扩展类加载域）+ `InvalidatingRealmCacheEventSpy`
- 普通 `mvn` 每次构建都要重新解析插件描述符、扫描插件 jar、创建插件 ClassRealm；mvnd 把这些结果留在内存跨构建复用。
- 缓存安全性由 `daemon/.../cache/impl/WatchServiceCacheFactory.java` 保证：缓存条目登记其依赖的 jar 路径（`CacheRecord.getDependencyPaths()`），通过 NIO `WatchService` 监控这些文件，jar 被修改/删除才失效条目。另有 `TimestampCacheFactory`（按时间戳失效）。
- 辅助缓存：`daemon/.../plugin/CachingPluginVersionResolver.java` 缓存插件版本解析。
- 注意：`Environment.MVND_NO_MODEL_CACHE`（`mvnd.noModelCache`）在 common 模块中有定义，但在 daemon/client 代码中未发现任何消费点，疑似历史遗留（见 Open Questions）。

#### 2.3 默认并行构建

- `client/.../DaemonParameters.java` 的 `threads()`：未显式指定 `-T`/`mvnd.threads` 时，默认取 `max(CPU 核数 - 1, mvnd.minThreads)`，`mvnd.minThreads` 默认 1。
- builder 默认 `mvnd.builder=smart`；smart builder 来自发行版内置的 takari-smart-builder 扩展（`dist/src/main/provisio/maven-distro.xml` 中声明 `io.takari.maven:takari-smart-builder`），按依赖图做模块级并行调度。`mvnd.coreExtensionsExclude` 默认排除 `io.takari.maven:takari-smart-builder` 一项是针对 `.mvn/extensions.xml` 中用户自带该扩展的场景，防止与内置版本冲突。
- 单线程 builder（`singlethreaded`）是 Maven 内置的（`[maven] impl/maven-core/.../lifecycle/internal/builder/singlethreaded/SingleThreadedBuilder.java`）。

#### 2.4 客户端侧开销压缩

- GraalVM 原生客户端：进程启动开销小、无 JVM 冷启动，构建进度通过 daemon 回推的二进制 `Message` 流（`common/.../Message.java`）实时渲染。
- 存活看门狗：构建期间 daemon 若超过 `mvnd.keepAlive`（默认 100 ms）未向客户端发送任何消息，则补发一条 keep-alive 消息；客户端按时间预算判定存活——`mvnd.keepAlive × mvnd.maxLostKeepAlive`（默认 100 ms × 30 = 3 秒）内未收到 daemon 的**任何**消息（含日志消息）即判定 daemon 已死，实现见 `client/.../DaemonClientConnection.java` 的 `maxKeepAliveMs`（时间预算机制，非逐条计数丢失）。
- 日志滚动窗口（`mvnd.rollingWindowSize`）只是展示层特性，与构建性能无关。

### 3. Daemon 生命周期与过期策略

- daemon 每 `mvnd.expirationCheckDelay`（默认 10 seconds）执行一次过期检查（`Server.run()` 中 `scheduleAtFixedRate` → `expirationCheck()`）。
- 策略组合在 `daemon/.../daemon/DaemonExpiration.java` 的 `master()`（文件头注释标明源自 Gradle `MasterExpirationStrategy`）：

```java
any(
    any(gcTrashing(), lowHeapSpace(), lowNonHeap()),          // JVM 自身内存异常
    all(compatible(), duplicateGracePeriod(), notMostRecentlyUsed()),  // 清理重复 daemon
    idleTimeout(Environment.MVND_IDLE_TIMEOUT.asDuration()),   // 空闲超时
    all(duplicateGracePeriod(), notMostRecentlyUsed(), lowMemory(0.05)), // 系统内存紧张
    registryUnavailable())                                     // 注册表丢失
```

#### 3.1 空闲超时（主要退出路径）

- `mvnd.idleTimeout` 默认 **3 hours**（`common/.../Environment.java` 的 `MVND_IDLE_TIMEOUT`）；Idle 状态持续超过该时长即 `QUIET_EXPIRE` → `requestStop`。

#### 3.2 JVM 自身内存判定（DaemonMemoryStatus）

- `daemon/.../daemon/DaemonMemoryStatus.java` 按 GC 收集器分档（`GcStrategy` 枚举），每 1 秒采样一次、滑动窗口 20 个事件：
  - `ORACLE_G1("G1 Old Gen", "Metaspace", "G1 Old Generation", 0.4, 75, 80, 2.0)`
  - `ORACLE_PARALLEL_CMS("PS Old Gen", "Metaspace", "PS MarkSweep", 1.2, 80, 80, 5.0)`，另有 CMS/Serial/IBM 变体
- 判定谓词：
  - `isTrashing()`：老年代使用率 ≥ 阈值（G1 75%）**且** GC 频率 ≥ thrashingThreshold（G1 2.0）→ `IMMEDIATE_EXPIRE`（强行终止）
  - `isHeapSpaceExhausted()`：使用率 ≥ 75% 且 GC 频率 ≥ heapRateThreshold（G1 0.4）→ `GRACEFUL_EXPIRE`
  - `isNonHeapSpaceExhausted()`：Metaspace 使用率 ≥ 80% → `GRACEFUL_EXPIRE`
- `gcRate = (窗口首尾 GC 次数差) / (首尾时间差毫秒数)`（`DaemonMemoryStatus.gcRate()`）。按此实现 GC 频率单位是"次/毫秒"，而阈值数值语义更像"次/秒"，存在单位疑点，实际触发难度可能远超设计意图（见 Open Questions）。

#### 3.3 系统内存紧张判定（lowMemory(0.05)）

三个条件必须**同时**满足（`all(...)` 组合）：

1. **内存低于阈值**（`DaemonExpiration.MemoryExpirationStrategy.checkExpiration`）：
   - 公式：`阈值 norm = min( max(物理内存总量 × 5%, 384 MB), 1 GB )`；`0 < 可用内存 < norm` 即命中。
   - 三个常量全部硬编码（`MIN_THRESHOLD_BYTES`、`MAX_THRESHOLD_BYTES`、`0.05`），无配置项。
   - 可用内存的读取按 OS 分三路：
     - **macOS**：JNI 本地方法 `CLibrary.getOsxMemoryInfo`，C 实现在 `native/src/main/native/mvndnative.c`：总量 = `sysctl(CTL_HW, HW_MEMSIZE)`；可用 = `(free_count + inactive_count − speculative_count) × 页大小`（Mach `host_statistics64`）。该口径减去了 speculative 预读页，数值低于 macOS 自身的 available 类指标，因此偏保守，更容易低于阈值而报紧张。
     - **Linux**：解析 `/proc/meminfo`，取 `MemTotal` 与 `MemAvailable`（老内核无 `MemAvailable` 时退化为 `free + Buffers + Cached + SReclaimable − Mapped`）。
     - **其他**：JMX `java.lang:type=OperatingSystem` 的 `TotalPhysicalMemorySize` / `FreePhysicalMemorySize`（IBM JVM 属性名不同）。
   - 读取失败时异常被吞、视为不紧张。
2. **已空闲超过 10 秒**：`duplicateGracePeriod()` 复用 `mvnd.duplicateDaemonGracePeriod`（默认 10 seconds）作为空闲时长闸门。构建进行中不会因此被杀。
3. **是注册表中的 idle daemon**（`notMostRecentlyUsed()`）：在注册表全部 Idle daemon 中按 `lastBusy` 取最大值并与自身比对。`lastBusy` 语义见 `common/.../DaemonInfo.java` 的 `withState`：Idle→Busy 转换时记录为当前时间（即"最近一次开始构建的时间"），Busy→Idle 时只更新 `lastIdle`。注意实现返回的是"该 daemon **是** max 时命中"，与 Gradle 原版策略名暗示的"非最近使用才清理"方向相反；单 daemon 场景下必然命中自身（见 Open Questions）。

触发后果：`GRACEFUL_EXPIRE` → 记录 stop event → `requestStop`。

#### 3.4 其余过期策略

- 重复 daemon 清理：`all(compatible(), duplicateGracePeriod(), notMostRecentlyUsed())`——注册表中存在多于一个与自身相同 `javaHome` + 相同选项集的兼容 daemon 且自身空闲超过 10 秒时退出。
- 注册表丢失：registry 文件不可读或自身条目被移除 → `GRACEFUL_EXPIRE`。

### 4. 长久驻留的可行做法

- 配置文件优先级（`client/.../DaemonParameters.java`）：项目级 `.mvn/mvnd.properties` > 用户级 `~/.m2/mvnd.properties`；亦可用 `mvnd.propertiesPath` 显式指定。发行版默认模板见 `dist/src/main/distro/conf/mvnd.properties`。
- 核心配置：

```properties
# ~/.m2/mvnd.properties

# 默认 3 hours；时长解析支持 d/h/m/s/ms（common/.../TimeUtils.java）
mvnd.idleTimeout = 365 days
# 给足堆，避免 lowHeapSpace / gcTrashing 触发
mvnd.maxHeapSize = 4G
```

- 三个注意点：
  1. `mvnd.idleTimeout` 带 `Flags.DISCRIMINATING`。daemon 兼容性判定（`common/.../DaemonCompatibilitySpec.java`）要求 `javaHome` 相同**且** discriminating 选项集完全一致（`daemonOptsMatch` 是逐项相等比较）；在命令行上改动 discriminating 选项会因不兼容而新起一个 daemon。驻留参数必须固定写在配置文件里。
  2. 没有"永不退出"开关。`lowMemory`/`gcTrashing`/注册表丢失等策略全部硬编码；系统重启、OOM killer、`mvnd --stop` 也会终止 daemon。mvnd 自身没有崩溃自动拉起机制，外部保活（launchd/systemd）意义有限——daemon 是按需 spawn 的，死了下次 mvnd 自然新建。
  3. `mvnd.noDaemon=true` 是反向模式：客户端 JVM 内起 server 线程、构建完即退（native 客户端下直接抛 `UnsupportedOperationException`，见 `DaemonConnector.connectNoDaemon`），仅调试用。

### 5. 串行执行

#### 5.1 构建内串行（有原生支持）

- `mvnd -1` / `mvnd --serial`（`Environment.SERIAL`，属性名 `mvnd.serial`）。客户端处理逻辑在 `DefaultClient.java`：
  1. 命令行出现 `--serial`/`-1` → 置系统属性 `mvnd.serial=true`；
  2. `parameters.serial()` 为 true 时叠加设置 `mvnd.threads=1` + `mvnd.builder=singlethreaded` + `mvnd.noBuffering=true`。
- 官方注释："Use one thread, no log buffering and the default project builder to behave like a standard maven"。
- 只想单线程、保留其余优化：`-T1` 或 `mvnd.threads=1`（仅控制线程数与 builder 无关；`mvnd.builder=singlethreaded` 可单独设置）。

#### 5.2 跨调用串行（无内置支持，需外部加锁）

- 客户端选 daemon 的逻辑（`client/.../DaemonConnector.java` 的 `connect()`）：按 Idle/Busy 分组 → 只尝试连接"空闲且兼容"的 daemon（先 Idle，再 Canceled）→ 找不到即**直接新建 daemon**。Busy 的兼容 daemon 不排队等待。
- daemon 端一次只服务一个客户端：`Server.accept()` 循环里 accept → 起 handler 线程处理 → `handler.join()` 后才 accept 下一个。
- 结论：并发发 N 条 mvnd 命令 = N 个 daemon 并行构建。mvnd 没有"最大 daemon 数"或请求队列机制。
- 外部串行化示例：

```bash
flock ~/mvnd-build.lock -c 'mvnd clean install'
```

- 附带收益：多个 daemon 并发构建共享同一本地仓库时存在并发下载/写入竞争，外部加锁可一并规避。

### 6. 关键配置速查表

| 配置项 | 默认值 | 作用 | 来源锚点 |
|---|---|---|---|
| `mvnd.idleTimeout` | `3 hours` | 空闲自退时限（DISCRIMINATING） | `Environment.MVND_IDLE_TIMEOUT` |
| `mvnd.expirationCheckDelay` | `10 seconds` | 过期检查周期（DISCRIMINATING） | `Environment`；`Server.run()` |
| `mvnd.duplicateDaemonGracePeriod` | `10 seconds` | 重复 daemon/内存压力策略的空闲闸门（DISCRIMINATING） | `Environment`；`DaemonExpiration` |
| `mvnd.keepAlive` / `mvnd.maxLostKeepAlive` | `100 ms` / `30` | 构建期存活看门狗（时间预算 = 两者乘积；keepAlive 为 DISCRIMINATING） | `Environment`；`DaemonClientConnection.java` |
| `mvnd.threads` | `max(CPU-1, minThreads)` | 并行线程数，等同 `-T` | `DaemonParameters.threads()` |
| `mvnd.builder` | `smart` | 构建调度器（takari-smart-builder） | `Environment`；`dist/src/main/provisio/maven-distro.xml` |
| `mvnd.serial` / `-1` / `--serial` | `false` | 单线程 + singlethreaded + 关日志缓冲 | `DefaultClient.java` |
| `mvnd.noDaemon` | `false` | 不用 daemon，构建完即退（DISCRIMINATING，调试用；native 客户端不支持） | `Environment`；`DaemonConnector` |
| `mvnd.maxHeapSize` | —（未设置） | daemon JVM 最大堆 `-Xmx`（DISCRIMINATING） | `Environment` |
| `mvnd.jvmArgs` | `--enable-native-access=ALL-UNNAMED` | daemon JVM 附加启动参数（DISCRIMINATING） | `Environment` |
| `mvnd.registry` / `mvnd.logPurgePeriod` | — / `7 days` | 注册表位置 / 日志清理周期 | `Environment` |

## 与当前项目的关系

- 可直接尝试的点：nop-entropy 全量构建（AGENTS.md 中 `./mvnw clean install -T 1C`）理论上可换用 mvnd 获得 daemon 驻留收益。但需注意版本匹配：本仓库 Maven wrapper 为 4.0.0-rc-5，调研的 mvnd 绑定 Maven 4.0.0-rc-6（见 Open Questions）。
- 可借鉴的机制（对 nop 平台自身）：
  - 常驻进程 + resident 容器模式（`ResidentMavenInvoker`）：与 Nop `IocContainer` 生命周期管理思路相通；
  - `WatchServiceCacheFactory`：以文件监听做跨进程生命周期缓存失效，可借鉴到 nop 的资源缓存/模板缓存设计；
  - `DaemonCompatibilitySpec` 的 discriminating 选项集设计：驻留服务按"参数指纹"匹配复用，避免脏环境复用；
  - keep-alive 看门狗协议：长任务主从进程存活检测的轻量实现。
- 不可直接借鉴的点：mvnd 的缓存与过期策略深度绑定 Maven 内部缓存接口（Sisu override），对非 Maven 生态的 nop 构建管线无复用价值；`mvnd.idleTimeout` 等 DISCRIMINATING 参数的坑在自研驻留服务中要提前规避。

## Conclusion

- 问题 1（性能原理）：瘦客户端（GraalVM native）+ 常驻 daemon（持有单一 `DaemonMavenCling`，复用 Maven 4 resident invoker 的整个对象图）+ 五个 Sisu 覆盖式跨构建缓存（文件监听失效保安全）+ 默认 `max(CPU-1)` 线程的 smart 并行构建。
- 问题 2（长久驻留）：`~/.m2/mvnd.properties` 固定 `mvnd.idleTimeout` 超大值（如 `365 days`）并给足堆即可；过期检查还受硬编码的 GC/堆/元空间/系统内存/重复 daemon/注册表六类策略影响，系统内存阈值 `min(max(5%, 384MB), 1GB)` 无配置开关，macOS 口径偏保守更易触发；无崩溃自动拉起。
- 问题 3（串行）：构建内串行用 `mvnd -1`/`--serial`（= threads 1 + singlethreaded builder + 关缓冲）；跨调用无排队，并发请求各自起新 daemon 并行执行，需 `flock` 等外部锁串行化。
- 被否决的方案：`mvnd.noDaemon=true`（等价普通 mvn 且失去全部驻留收益）；依赖 mvnd 内置机制做跨调用排队（源码证实不存在该机制）。
- 后续工作：本报告为纯调研，无后续 plan；若决定在 nop-entropy 构建中落地 mvnd，需先验证 Maven 版本兼容性（见 Open Questions 第 1 条）再另立 plan。后续已产出落地方案：[2026-09-21a-multi-agent-maven-queue-and-per-worktree-repo.md](2026-09-21a-multi-agent-maven-queue-and-per-worktree-repo.md)（多 agent 并发构建排队包装器 `ai-dev/tools/mvnq` 与 per-worktree repository 方案）。

## Open Questions

- [ ] mvnd 2.0.0-rc-4（绑定 Maven 4.0.0-rc-6）与 nop-entropy 的 Maven wrapper 4.0.0-rc-5 是否兼容，需实测。
- [ ] `DaemonMemoryStatus.gcRate()` 的单位是"次/毫秒"（`gcCountDelta / timeDelta.toMillis()`），与 `GcStrategy` 阈值数值（如 G1 0.4/2.0）的隐含单位（次/秒？）不一致，JVM 内存过期策略的实际触发难度存疑；需对照 Gradle 原版实现确认是否为移植偏差。
- [ ] `notMostRecentlyUsed()` 实现为"自身是 idle daemon 中 lastBusy 最大者即命中"，与策略名及 Gradle 原版意图（清理非最近使用者）方向相反；净效果（优先清理谁、收敛到几个 daemon）值得推演确认是否为移植 bug。
- [ ] `Environment.MVND_NO_MODEL_CACHE`（`mvnd.noModelCache`）仅有定义、未发现消费代码，疑似遗留死配置，可在 mvnd 社区求证或提 issue。

## References

- 调研对象：`~/sources/mvnd`（`org.apache.maven.daemon:mvnd:2.0.0-rc-4-SNAPSHOT`，git HEAD `53dadc00`）；`~/sources/maven`（Maven 4 源码）
- 关键源码锚点（省略 `~/sources/mvnd/` 前缀）：
  - `daemon/src/main/java/org/mvndaemon/mvnd/daemon/Server.java`（主循环、单客户端 accept、过期调度、缓存清理）
  - `daemon/src/main/java/org/mvndaemon/mvnd/daemon/DaemonExpiration.java`（过期策略全集、lowMemory 公式）
  - `daemon/src/main/java/org/mvndaemon/mvnd/daemon/DaemonMemoryStatus.java`（GC 分档阈值、滑动窗口）
  - `daemon/src/main/java/org/apache/maven/cli/DaemonMavenCling.java` / `DaemonMavenInvoker.java`（驻留 Maven 对象图）
  - `daemon/src/main/java/org/mvndaemon/mvnd/cache/invalidating/`、`daemon/src/main/java/org/mvndaemon/mvnd/cache/impl/WatchServiceCacheFactory.java`（跨构建缓存）
  - `client/src/main/java/org/mvndaemon/mvnd/client/DaemonConnector.java`（daemon 选择/新建，无排队）
  - `client/src/main/java/org/mvndaemon/mvnd/client/DaemonParameters.java`（默认线程数、配置文件位置）
  - `client/src/main/java-mvnd/org/mvndaemon/mvnd/client/DefaultClient.java`（`--serial` 语义、status/stop/purge）
  - `common/src/main/java/org/mvndaemon/mvnd/common/Environment.java`（全部 `mvnd.*` 配置项与默认值）
  - `common/src/main/java/org/mvndaemon/mvnd/common/DaemonCompatibilitySpec.java`、`DaemonInfo.java`（兼容性判定、lastBusy 语义）
  - `native/src/main/native/mvndnative.c`（macOS 可用内存口径）
  - `[maven] impl/maven-cli/src/main/java/org/apache/maven/cling/invoker/mvn/resident/ResidentMavenInvoker.java`
- 上游项目：https://github.com/apache/maven-mvnd ；过期策略来源：https://github.com/gradle/gradle （`MasterExpirationStrategy`，见源码文件头注释）
