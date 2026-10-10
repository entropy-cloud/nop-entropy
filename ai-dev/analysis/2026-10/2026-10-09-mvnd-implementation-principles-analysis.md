# Maven Daemon (mvnd) 实现原理深度分析

> Status: open
> Date: 2026-10-09
> Scope: `~/sources/mvnd` @ `master` / `53dadc00`（2026-09-17），mvnd `2.0.0-rc-4-SNAPSHOT` + Maven `4.0.0-rc-6`
> Conclusion: （open 阶段不填写）

## Context

- 为什么做这个分析：nop-entropy 的构建链目前是"每次起 JVM"的模型（`mvnw` + `mvn-antrun`/`maven-invoker` 体系）。要判断"是否值得引入常驻构建守护进程"以及"哪些机制可以平移到 nop 自身的构建/工具链"，必须先把 mvnd 的真实实现读透，而不是停留在"JVM 常驻 + 缓存"的表层认知。
- 已有材料不足：`docs/maven/maven-vfs-analysis.md` 分析的是 Maven 4 VFS 路径定制，与守护进程无关；ai-dev 下无 mvnd 相关文档。
- 源码位置：`~/sources/mvnd`，模块 `build-plugin / native / agent / helper / common / client / logging / daemon / dist / integration-tests`，生产代码约 130 个 Java 文件。
- 关键前提：本文分析的是 **mvnd 2.x（对 Maven 4）**，不是常见的 mvnd 1.x（Maven 3）。二者在 daemon 侧 Maven 集成方式上**完全不同**，网上绝大多数资料讲的都是 1.x。

---

## 1. 顶层架构与"它到底做了什么"

### 1.1 三个 JVM 角色

| 角色 | 进程 | 生命周期 | 职责 |
|---|---|---|---|
| **client** | `mvnd` 启动的 JVM（java-mvnd 变体）或 native image | 每次命令一个，秒级 | 解析参数、发现/拉起 daemon、走 socket、把 daemon 输出渲染到终端 |
| **daemon** | 由 client fork 出的独立 JVM | 常驻（默认 idle 3h） | 持有 Maven 对象图 + Plexus 容器 + 类加载 realm + 各类缓存，真正执行构建 |
| **embedded fallback** | client 进程内 | 进程内 | `java-fallback` 变体：直接 `MavenCling.main(argv)`，完全不涉及守护进程 |

核心收益来源只有三条，且都是"复用"而非"并行"：

1. **JVM 启动 + Maven 类加载 + Plexus/Sisu 容器构建**只付一次；
2. **插件 realm（ClassRealm）** 常驻，插件依赖 jar 不再重复解析/加载；
3. **元数据 / 插件描述符 / 项目 artifact 解析结果**常驻内存。

### 1.2 模块依赖

```
dist (bin/mvnd.sh, conf/mvnd.properties, mvnd-client.conf, mvnd-daemon.conf)
 ├── client        纯客户端：参数、registry、connector、终端渲染
 ├── daemon        Server + DaemonMavenCling（对 Maven 4 的适配）+ cache/invalidating + plugin + timing + interactivity
 ├── common        协议 + 环境 + 注册表 + 平台适配（client/daemon 共享）
 ├── agent         Javassist agent，patch ProcessBuilder.start
 ├── native        JNI（setenv/chdir/getOsxMemoryInfo）+ GraalVM native-image 配置
 ├── logging       MvndDaemonLogger / MvndLoggerFactory（只有 2 个类）
 └── build-plugin  构建期插件
```

`client` 与 `daemon` **不共享 Maven 依赖**——daemon 通过反射 `Class.forName("org.apache.maven.cli.DaemonMavenCling")` 加载（`daemon/src/main/java/org/mvndaemon/mvnd/daemon/Server.java:127-131`），client 则完全不依赖 `maven-core`。这是刻意的分层：client 保持极轻，可以在 GraalVM native image 里编译。

---

## 2. 启动时序（client 视角）

```
mvnd clean install
  │
  ├─[1] launcher (dist/bin/mvnd.sh)
  │      优先 native image；否则 java -classpath plexus-classworlds mvnd-client.conf
  │      Multi-Release JAR 决定加载 java-mvnd 还是 java-fallback 的 DefaultClient
  │
  ├─[2] DefaultClient.main
  │      剥掉 client-only 选项：--log-file / --diag / --serial / --completion / --status / --stop / --purge
  │      手工扫描 -D（setSystemPropertiesFromCommandLine）
  │      读 .mvn/jvm.config → mvnd.jvmArgs
  │      决定 color（auto 在非 tty / batch / log-file 时降级为 never）
  │
  ├─[3] DaemonConnector.connect
  │      ├─ 读 registry.bin（FileLock 独占）
  │      ├─ 按 DaemonCompatibilitySpec 过滤
  │      ├─ tier1: 连 Idle 且兼容的 daemon      ── 命中即返回
  │      ├─ tier2: 等 Canceled 的 Busy daemon（≤ mvnd.cancelConnectTimeout=3s）
  │      └─ tier3: fork 新 daemon（注册为 Busy）→ 200ms 轮询 connectToDaemonWithId，≤ mvnd.connectTimeout=10s
  │
  ├─[4] socket 握手：先写 16 字节 token，再发 Message.BuildRequest
  │      args + workingDir + multiModuleProjectDirectory + System.getenv()
  │
  ├─[5] daemon 侧 Server.client → Server.handle
  │      ├─ checkToken（MessageDigest.isEqual 常量时间比较）
  │      ├─ 注册型读取首条消息，超时 1 分钟
  │      ├─ updateState(Busy)
  │      ├─ 启动 sender/receiver 两个线程
  │      └─ 在"连接处理线程"上同步执行 cli.main(...)  ← 构建线程就是它
  │
  └─[6] client 侧主循环：receive() → TerminalOutput.accept() → 遇 BUILD_FINISHED 返回 exitCode
```

关键点：**构建线程 = 该连接的处理线程**。`Server.accept()` 里 `handler.join()`（`Server.java:251`）保证同一 daemon 一次只处理一个连接、一个构建。多客户端并发不是靠 daemon 内部线程池，而是靠"注册表里 Idle/Busy 的状态协议"来分配到不同 daemon（见 §4）。

---

## 3. Daemon 身份与注册表

### 3.1 身份 = javaHome + 判别性系统属性集合

`Environment` 枚举每个常量带 `Flags.DISCRIMINATING`（`common/.../Environment.java:647`），含义是"该值不同的 daemon 不能服务这个构建"。判别性项（`Environment.java` 行号为定义处）：

`java.home`(71)、`mvnd.home`(76)、`jdk.java.options`(82)、`maven.repo.local`(88)、`maven.settings`(90)、`mvnd.noDaemon`(156)、`mvnd.debug`(169)、`mvnd.debug.address`(176)、`mvnd.idleTimeout`(180)、`mvnd.keepAlive`(185)、`mvnd.expirationCheckDelay`(271)、`mvnd.duplicateDaemonGracePeriod`(277)、`mvnd.extClasspath`(213)、`mvnd.coreExtensionsDiscriminator`(220)、`mvnd.coreExtensionsExclude`(228)、`mvnd.minHeapSize`(238)、`mvnd.maxHeapSize`(243)、`mvnd.threadStackSize`(248)、`mvnd.jvmArgs`(256)、`mvnd.enableAssertions`(266)、`mvnd.socketFamily`(294)。

**不在**身份里：`user.dir`、`maven.multiModuleProjectDirectory`（它们随每次 `BuildRequest` 走网络传输，所以一个 daemon 服务多个项目）、用户/OS 用户（隔离靠 `~/.m2` 路径）。

指纹序列化成一个 `key=value` 字符串列表（`client/.../DaemonParameters.java:82-86`），只有 DURATION 类型会归一化（`3 hours` → `3h`，`OptionType.java:44-47`）。

两个**派生**判别项很巧：

- `mvnd.coreExtensionsDiscriminator`：对 `.mvn/extensions.xml`、`~/.m2/extensions.xml`、`$MVND_HOME/mvn/conf/extensions.xml` 三者做 SHA-1（`DaemonParameters.java:481-518`）。任一文件改动 → 指纹变 → 自动换 daemon，避免 core extension 变更被旧 daemon 屏蔽。
- `mvnd.extClasspath`：来自 `maven.ext.class.path`，按平台分隔符切成 jar 列表再 join（`DaemonParameters.java:469-479`）。

兼容性判定就两行（`common/.../DaemonCompatibilitySpec.java:73-79`）：

```java
daemon.getOptions().containsAll(options) && daemon.getOptions().size() == options.size()
javaHome.equals(Paths.get(daemon.getJavaHome()))
```

client 侧的 `javaHome` 会先 `toRealPath()`（`DaemonParameters.java:168-172`），所以符号链接别名不会误判为不兼容。

### 3.2 registry.bin

位置：`${user.home}/.m2/mvnd/registry/<mvnd-version>/registry.bin`（`DaemonParameters.java:233-247`）。优先级链：`mvnd.daemonStorage` 系统属性 → `conf/mvnd.properties` → 环境变量 `MVND_DAEMON_STORAGE` → 默认。**版本号在路径里**，所以不同 mvnd 版本天然隔离。

同目录还有 `daemon-<id>.log`（daemon 自身日志）、`daemon-<id>.out.log`（daemon 启动期 stdout+stderr）、`purge-<date>.log`。

文件格式（`common/.../DaemonRegistry.java:215-243`，JDK 的 `DataOutputStream.writeUTF`，不是 mvnd 自定义 UTF）：

```
int   numDaemons
  UTF daemonId / javaHome / mvndHome
  int  pid
  UTF  address        "inet:/127.0.0.1:PORT" | "unix:/path"
  byte[16] token      SecureRandom
  UTF  locale
  int  numOptions; UTF option*
  byte state ordinal  ← 枚举序号！
  long lastIdle; long lastBusy
int   numStopEvents
  UTF daemonId; long timestamp; byte statusOrdinal(-1=null); UTF reason
```

并发控制三层：JVM 内 `static Map<Path,Object> locks` + `synchronized`；跨进程 `FileLock` 独占，`tryLock` 每 100ms 重试，上限 20s（`DaemonRegistry.java:60,254-268`）；每次读写都是"锁内全量读-改-写"。

**损坏恢复是全量的**：任何一条记录反序列化失败 → `reset()` 把两个 map 清空 + `channel.truncate(0)`（`DaemonRegistry.java:245-250,270-278`），即所有 daemon 记录一起丢。下次客户端读不到就重新拉起。

### 3.3 socket

`SocketFamily` 只有 `inet` 和 `unix` 两个常量（`SocketFamily.java:32-34`）——**Windows 上没有命名管道，用的是回环 TCP**。

选择逻辑在 client（`DaemonConnector.java:449-454`）：未显式配置时，fork `java -version` 解析出版本，**≥16 用 unix domain socket，否则 inet**。代价是每次拉起 daemon 多一次子进程 spawn。

`inet` 绑定 `InetAddress.getLoopbackAddress()` + 端口 0（内核分配临时端口），backlog 0（`SocketFamily.java:47-56`）。`unix` 直接 `bind(null, 0)` 让内核起名，mvnd 从不回读这个路径，靠 `socket.getLocalAddress()` 的字符串写进 registry。

`familyOf` 用**类名字符串比较** `"java.net.UnixDomainSocketAddress"` 而非 `instanceof`（`SocketFamily.java:137`），为的是 common 模块能在 JDK 8-15 上加载。

---

## 4. find-or-spawn 算法

`DaemonConnector.connect`（`client/.../DaemonConnector.java:94-124`）严格三段：

```
tier1  registry.getAll() 按 state==Idle 分组 → 过滤兼容 → 逐个尝试 connectToDaemon
       任一失败：记录 stop event + 从 registry 移除该 daemon（CleanupOnStaleAddress:546-564），试下一个
tier2  Busy 且 Canceled 的兼容 daemon：200ms 轮询重跑 tier1 路径，上限 mvnd.cancelConnectTimeout=3s
tier3  生成新 id → 处理 stop events → fork 进程 → 200ms 轮询 connectToDaemonWithId(id)，上限 mvnd.connectTimeout=10s
```

**没有锁、没有 CAS**。防"两个 client 抢同一个 daemon"的机制是一个状态约定：

- 新 daemon 构造时**以 `Busy` 状态注册**（`Server.java:161-164`），注释写明"so that nobody else will grab it"；
- tier1 只考虑 `Idle`，所以刚起的 daemon 不会被别人捡走；
- 启动方只按**自己的 id** 找（`connectToDaemonWithId`，`DaemonConnector.java:514-529`），不是"随便找个 idle"。

代价：N 个并发 client → N 个 daemon，注册表无法收敛。收敛交给 `DaemonExpiration.compatible()`（§5）：如果同指纹 daemon 数量 > 1，多余的 GRACEFUL_EXPIRE。

残留竞态：两个 client 同时看到 `Idle` 都会连上去，第二个连接**阻塞在 accept backlog 里**（不是排队、不是拒绝），第一个构建结束才被服务。

### 4.1 fork 出来的完整命令行

`startDaemonProcess`（`DaemonConnector.java:339-487`）：

```
<javaHome>/bin/java
  -classpath <MVND_HOME>/mvn/boot/plexus-classworlds-*.jar      ← 目录扫描得到
  -javaagent:<MVND_HOME>/mvn/lib/mvnd/mvnd-agent-*.jar         ← 目录扫描得到
  [--enable-native-access=ALL-UNNAMED]                          ← mvnd.jvmArgs 默认值
  [-Xms/-Xmx/-Xss]                                              ← minHeapSize/maxHeapSize/threadStackSize
  [-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=host:port]   ← mvnd.debug
  -Dmvnd.home / -Dmaven.home / -Dmaven.conf / -Dclassworlds.conf
  -D<MAVEN_LOGGER_LOG_FILE>=daemon-<id>.log
  -Dmvnd.java.home / -Dmvnd.id / -Dmvnd.daemonStorage / -Dmvnd.registry
  -Dmvnd.socketFamily=inet|unix
  -D<其余全部判别性属性>
  org.codehaus.plexus.classworlds.launcher.Launcher             ← mainClass 取自 mvnd-daemon.conf
```

注意 `-X*` 排在 `jvmArgs` **之后**，所以 `mvnd.maxHeapSize` 优先于 `-Dmvnd.jvmArgs` 里写的 `-Xmx`（后者 JVM 层面后者胜）。

**环境变量只设一个**：`JDK_JAVA_OPTIONS`（`DaemonConnector.java:470-472`），其余全继承。

**`MAVEN_OPTS` 在 mvnd 源码里完全不存在**（只在 `bin/mvnd.sh:209` / `mvnd.cmd:200` 传给 **client** JVM）。mvnd 有意改用 `JDK_JAVA_OPTIONS`：它从环境注入 daemon、也作为命令行选项透传给 daemon 的 Maven 层（`DefaultClient.java:103-106`）、并且参与 daemon 身份判定（`Environment.java:82`）。

### 4.2 agent 做了什么

`agent/.../Agent.java` 用 Javassist patch `java.lang.ProcessBuilder.start`：当两个 redirect 都是 `INHERIT` 时改写为 `PIPE` 并把子进程输出泵回 daemon 自己的 stdout/stderr（`Agent.java:30-38,55-58`）。效果是 fork 出的插件子进程（如 surefire、exec-maven-plugin）即使"继承"了 IO，其输出仍进入 daemon 日志体系而不是泄漏到启动终端。

---

## 5. 生命周期状态机

```
                 ┌───────────────────────────────────────┐
   start ──► Busy ──► Idle ◄──────┐                        │
             │  ▲                 │  (build 结束 + System.gc)│
 CANCEL_    ▼  │                 ▼                          │
        Canceled            StopRequested ──► Stopped ───────┤
             │                  ▲                            │
             │                  │ requestStop(Busy 时延迟)   │
             └──► Broken ──────┴────────────────────────────┘
                (receiver 线程抛出非 Recoverable 异常)
```

状态集：`Idle, Busy, Canceled, StopRequested, Stopped, Broken`（`common/.../DaemonState.java:25-32`）。**没有 Dead / Expired**——过期是一个"决策"不是状态。

- 全部迁移走 `Server.updateState`（`Server.java:661-672`）：同状态短路，否则持 `stateLock` → `registry.store(...)` → `condition.signalAll()`。**每次状态变更都是一次注册表文件的加锁全量重写**。
- `requestStop`：若 `Busy` 只置 `StopRequested`（`beginStopping`），等构建自然结束再退出（`Server.java:394-432`）。
- `cancelNow()`：`SmartBuilder.cancel()` + `ProcessHelper.killChildrenProcesses()`，等 ≤10s（`CANCEL_TIMEOUT`），超时 `stopNow`（`Server.java:460-504`）。
- 每次构建结束 `Server.java:653-657`：`ProjectBuildLogAppender.setProjectId(null)` → 恢复 `System.in` → `updateState(Idle)` → **`System.gc()`**。

### 5.1 过期策略

`daemon/.../DaemonExpiration.java:65-72` 的组合子：

```
any(
    any(gcTrashing(), lowHeapSpace(), lowNonHeap()),
    all(compatible(), duplicateGracePeriod(), notMostRecentlyUsed()),
    idleTimeout(mvnd.idleTimeout = 3h),
    all(duplicateGracePeriod(), notMostRecentlyUsed(), lowMemory(0.05)),
    registryUnavailable()
)
```

优先级 `DO_NOT_EXPIRE < QUIET_EXPIRE < GRACEFUL_EXPIRE < IMMEDIATE_EXPIRE`（`DaemonExpirationStatus.java:22-26`）。只有 `IMMEDIATE_EXPIRE`（= GC thrashing）会**打断正在跑的构建**；其余全部延迟到构建结束。

**没有 `maxIdleDaemons` 这个设置**。"限制 idle daemon 数量"这个职责由 `notMostRecentlyUsed()` 承担——idle daemon 中只有 `lastBusy` 最大的那个豁免，其余全部过期；配合 `duplicateDaemonGracePeriod`（10s），效果接近"每个指纹保留一个 idle daemon + 10s 抖动窗口"。

`lowMemory(0.05)` 的阈值被**钳制在 [384MB, 1GB]**（`DaemonExpiration.java:248-280`）：64GB 机器上 5% = 3.2GB 会被压到 1GB。平台分支：macOS 走 JNI `host_statistics64`（`native/.../mvndnative.c:93-138`）、Linux 解析 `/proc/meminfo`、其他走 `MemoryMXBean`。整个探测包在 `catch (Exception e) { /* ignore */ }` 里，探测失败就永不回收。

过期后写入一条 `DaemonStopEvent` 到注册表，**下一次拉起 daemon 的客户端会把它读出来并汇报**："N stopped daemons could not be reused"（`DaemonConnector.java:194-221`）。

### 5.2 GC thrashing 判定

`daemon/.../DaemonMemoryStatus.java:43-61` 硬编码了 5 种 GC 策略（按 GC MXBean 名字 + 内存池名字匹配）：

| 策略 | 堆池 | 非堆池 | GC | 堆占用阈值 | GC 速率阈值 | 非堆阈值 | thrashing 阈值 |
|---|---|---|---|---|---|---|---|
| ORACLE_PARALLEL_CMS | PS Old Gen | Metaspace | PS MarkSweep | 80 | 1.2 | 80 | 5.0 |
| ORACLE_6_CMS | CMS Old Gen | Metaspace | ConcurrentMarkSweep | 80 | 1.2 | 80 | 5.0 |
| ORACLE_SERIAL | Tenured Gen | Metaspace | MarkSweepCompact | 80 | 1.2 | 80 | 5.0 |
| ORACLE_G1 | G1 Old Gen | Metaspace | G1 Old Generation | 75 | 0.4 | 80 | 2.0 |
| IBM_ALL | Java heap | Not Used | MarkSweepCompact | 70 | 0.8 | -1 | 6.0 |

每秒采样一次，保留最近 20 个 `GcEvent`；`isTrashing()` = 堆使用率 ≥ 阈值 **且** GC 频率 ≥ 阈值（GC 次数/秒）。匹配不到任何策略则整个功能禁用（`strategy = null`）。

### 5.3 故障诊断

`--diag` 是 JLine 的终端诊断（`DefaultClient.java:89-92`），**不接触 daemon**。真正有用的是 daemon 连接超时时内联的 `DaemonDiagnostics.describe()`（`client/.../DaemonDiagnostics.java:54-59`）：打印 daemon id + `daemon-<id>.log` 和 `daemon-<id>.out.log` 的**最后 200 行**。设计直接抄自 Gradle（类注释里给了链接）。

`--status` 表格：`ID / PID / Address / Status / RSS / Last activity / Java home`，`Last activity = max(lastIdle, lastBusy)`，并顺手用 `ProcessHandle.of(pid).isEmpty()` 清理死记录（`DefaultClient.java:296-319`）。

---

## 6. 通信协议

### 6.1 帧格式：没有帧

协议是**裸 `DataInputStream`/`DataOutputStream` 字节流**：没有 magic number、没有版本字段、没有长度前缀。每条消息以**一个字节的类型 id** 开头（`Message.java:84`），后续按该类型的固定原语序列读取（`Message.java:211-213`）。

握手是唯一的前置内容：**16 字节 token**（`SecureRandom`，`Server.java:138-140`），客户端 connect 后立刻裸写，服务端读满 16 字节后用 `MessageDigest.isEqual` 常量时间比较，不匹配直接丢连接（`Server.java:305-320`）。

**没有协议版本协商**。兼容性完全靠 §3.1 的属性集比对实现——不兼容就换一个 daemon，而不是协商降级。

消息类型 31 个（`Message.java:37-75`）：`BUILD_REQUEST(1) / BUILD_STARTED(2) / BUILD_FINISHED(3) / PROJECT_STARTED(4) / PROJECT_STOPPED(5) / MOJO_STARTED(6) / PROJECT_LOG_MESSAGE(7) / BUILD_LOG_MESSAGE(8) / BUILD_EXCEPTION(9) / KEEP_ALIVE(10) / STOP(11) / DISPLAY(12) / PROMPT(13) / PROMPT_RESPONSE(14) / BUILD_STATUS(15) / KEYBOARD_INPUT(16) / CANCEL_BUILD(17) / TRANSFER_*(18-23) / EXECUTION_FAILURE(24) / PRINT_OUT(25) / PRINT_ERR(26) / REQUEST_INPUT(27) / INPUT_DATA(28) / REQUEST_INPUT_AVAILABLE(29) / INPUT_AVAILABLE_DATA(30) / PROJECT_TEST_PROGRESS(31)`。

关键 payload：

- `BuildRequest`：`args(List<String>) + workingDir + projectDir + env(Map)`。`user.dir` 和 `maven.multiModuleProjectDirectory` 走这里，所以**一个 daemon 服务多个项目**。服务端还会把 env 里的 `MAVEN_ARGS` 拆开追加到 args（`Server.java:622-630`）。
- `BuildStarted`：`projectId + projectCount + maxThreads + artifactIdDisplayLength`。后两个是给客户端渲染用的——`artifactIdDisplayLength` 是全部 artifactId 长的**第 90 百分位**（`ClientDispatcher.java:69-89`），客户端用它算对齐列宽。
- `BuildException`：`message + className + stackTrace(字符串)`，客户端**重建成一个普通 `Exception`**（`DefaultClient.java:411-416`）——异常类型只保留字符串前缀，没有 cause 链、没有 suppressed。
- `Transfer*`：`projectId + requestType + repositoryId + repositoryUrl + resourceName + contentLength + transferredBytes + exception`，用于客户端显示依赖下载进度条。
- `Stop(11)` 是 sender 线程的唯一终止信号，靠**单例引用比较**（`Server.java:539` `m == STOP_SINGLETON`）。

### 6.2 自定义字符串编码

mvnd 不用 `DataOutputStream.writeUTF`，自己实现了同名的一对（`Message.java:255-351`）：

```
[int32 charCount]      ← 计的是 UTF-16 char 数，不是字节数
[bytes]                ← 1/2/3 字节可变长，按 char 逐个编码（代理对编成两个 3 字节序列，即 CESU-8）
```

动机是 JDK `writeUTF` 有 65535 字节上限。副作用：长度前缀是 char 数，解码 buffer 是 768 字节的 `ThreadLocal` 暂存区。`null` 用长度 `-1` 表示——`InputData` 靠这个表达 EOF（`Message.java:1317-1319,1225-1227`）。

### 6.3 发送队列的优先级重排

daemon 侧 `Server.handle`（`Server.java:508`）：

```java
PriorityBlockingQueue<>(64, Message.getMessageComparator())
```

比较器是 `comparingInt(Message::getClassOrder).thenComparingLong(m -> m.seq)`（`Message.java:148-150`）。`getClassOrder` 给每个类型一个 rank（`Message.java:152-201`）：

```
KEEP_ALIVE/BUILD_REQUEST 0 · BUILD_STARTED 1 · PROMPT/DISPLAY/IO 2 · PROJECT_STARTED 3
MOJO_STARTED 4 · PROJECT_TEST_PROGRESS 5 · EXECUTION_FAILURE 10
TRANSFER_* 40/41/42 · PROJECT_LOG_MESSAGE 50 · BUILD_LOG_MESSAGE 51
PROJECT_STOPPED 95 · BUILD_FINISHED 96 · BUILD_EXCEPTION 97 · STOP 99
```

效果是**控制类消息插队、日志消息保序**：多个模块线程并发产生的日志，按 daemon 产生顺序落在正确的生命周期位置之间。这不是"排队"，是**重排**——代价是 `PriorityBlockingQueue` 无法保留行边界，所以 `ClientDispatcher.trimTrailingEols` 用 `Pattern.compile("[\r\n]+$")` 把每条消息的行尾剥掉（`ClientDispatcher.java:218-220`）。

`seq` 和 `timestamp`（`Message.java:143-146`）**不序列化**，纯粹是 daemon 进程内的排序键。

### 6.4 socket 心跳

sender 线程双状态循环（`Server.java:520-551`）：

- 队列空超过 `mvnd.keepAlive`（默认 100ms）→ 主动发 `KEEP_ALIVE`
- 有消息在途 → 切非阻塞 `poll()`，队列空时才 `flush()`，把多条小消息攒成一次 32KiB 缓冲写

客户端 `DaemonClientConnection.receive` 的看门狗超时 = `keepAlive × mvnd.maxLostKeepAlive`（默认 100ms × 30 = 3s，`DaemonClientConnection.java:76`），超时抛 `"No message received within … ms, daemon may have crashed"`。空闲构建（比如长时间 compile 无日志）靠这个心跳保活。

客户端 inbound 队列是 `ArrayBlockingQueue<>(16)`（有界），消费侧用 `drainTo` 批量取——慢消费者会把背压一路传回 daemon 的 socket。

---

## 7. 输出多路复用与渲染

四条独立通道，**不是传输层复用，全靠消息类型字节区分**：

| 通道 | 产生方 | 客户端落点 |
|---|---|---|
| `PRINT_OUT(25)` / `PRINT_ERR(26)` | `LoggingOutputStream` 包住 `System.out/err`（`Server.java:620-621`），接插件里裸 `System.out.println` | `TerminalOutput.java:314-333`：`FileLog` 模式进日志文件，否则 `clearDisplay()` 后直写 `System.out/err` |
| `DISPLAY(12)` | `DaemonPrompter.doDisplay` | JLine 终端，`[projectId] msg` |
| `PROJECT_LOG_MESSAGE(7)` | `ClientDispatcher.projectLogMessage` | 三分支：未知模块→平铺日志；`-B`/`--batch`/noBuffering→加 `[projectId] ` 前缀（**仅当 maxThreads>1**，单线程串行时输出裸 Maven 格式）；否则**按模块缓冲** |
| `BUILD_LOG_MESSAGE(8)` | `ClientDispatcher.log` | 共享日志，无模块归属 |

`BUILD_STARTED` 到达时决定缓冲模式（`TerminalOutput.java:214-230`）：`maxThreads<=1 || totalProjects<=1` 时**强制 `noBuffering=true`**（没什么可交错的）。模块日志在 `PROJECT_STOPPED` 时 flush；任何终止消息（`CANCEL_BUILD`/`BUILD_EXCEPTION`/`BUILD_FINISHED`）也会 flush 全部已缓冲日志后再关闭（`TerminalOutput.java:233,253,278-294`），**保证异常终止不丢输出**。

这部分是 mvnd 相对 Gradle daemon 最有产品感的部分：并行构建下每个模块一个可折叠的滚动窗口（`mvnd.rollingWindowSize`），且 `--batch-mode` 下自动退化。

---

## 8. daemon 侧的 Maven 集成（2.x 与 1.x 的根本差异）

这是最容易读错的地方。

### 8.1 2.x：几乎什么都不做

`daemon/.../DaemonMavenCling.java` **不是 `MavenCling` 的子类**，它 `implements DaemonCli`，内部持有：

```java
this.parser  = new DaemonMavenParser();      // 无状态，可复用
this.invoker = new DaemonMavenInvoker(       // 有状态，生命周期 = daemon 进程
    ProtoLookup.builder()
        .addMapping(ClassWorld.class,
            ((ClassRealm) Thread.currentThread().getContextClassLoader()).getWorld())
        .build(),
    null);
```

核心两行：

- **`ClassWorld` 是从 TCCL 里"取"出来的，不是新建的**（`DaemonMavenCling.java:53-55`）。这是整个 realm 复用能成立的地基：`ClassWorld` 活得比任何单次构建都久，`disposeRealm` 才能精确摘除一个过期的 plugin realm 而不破坏其他。
- **`DaemonMavenInvoker extends ResidentMavenInvoker`**（`DaemonMavenInvoker.java:41`）——Maven 4 自己提供了"常驻 invoker"抽象，容器跨 `invoke()` 复用。mvnd 1.x 时代那一大堆 `doCreateContainer()` / TCCL 设置 / `LoggingExecutionListener` 复用逻辑，在 Maven 4 线上**全部消失了**（`logging/smart` 包已不存在，`logging` 模块只剩 2 个类）。

`DaemonMavenInvoker` 只覆盖 6 个钩子（`DaemonMavenInvoker.java`）：

| 覆盖点 | 行 | 作用 |
|---|---|---|
| `doCreateTerminal` | 46-58 | 用 **client 的流** 建 JLine terminal；`builder.paused(true)`——daemon 绝不能抢 client 的 stdin |
| `doConfigureWithTerminalWithRawStreamsEnabled` | 60-64 | `System.setOut/Err` 到 client 流，解开 `LoggingOutputStream` |
| `doDetermineBuildEventListener` | 76-79 | 返回请求传入的 `BuildEventListener`——**这就是输出流回 socket 的机制本体** |
| `helpOrVersionAndVersion` | 81-98 | 走 `BuildEventListener::log` |
| `preCommands` | 100-110 | `--verbose` 时输出 mvnd 版本 |
| `createContainerCapsuleFactory` | 112-115 | 返回 `DaemonPlexusContainerCapsuleFactory`——**唯一的容器定制缝隙** |

`DaemonPlexusContainerCapsuleFactory` 只做两件事（`DaemonPlexusContainerCapsuleFactory.java`）：把 `org.mvndaemon.mvnd.interactivity` 加进导出包（让 `DaemonPrompter` 能覆盖 Maven 的 `Prompter` SPI）；`parseExtClasspath` **完全替换** Maven 的 ext-classpath 发现逻辑，只返回 `Environment.MVND_EXT_CLASSPATH`。

`DaemonMavenParser` 三处覆盖（`DaemonMavenParser.java`）：`parseArgs` 走 `CommonsCliDaemonMavenOptions`；`populateSystemProperties` 把 **client 传来的整个 env map 塞进 system properties**（`:48`，这样不同 client 的 `HOME`/`user.dir` 差异不用重启 daemon 就能生效）；`readCoreExtensionsDescriptorFromFile` 过滤掉 `mvnd.coreExtensionsExclude`（默认 `io.takari.maven:takari-smart-builder`）里的扩展。

### 8.2 每次构建重置什么、保留什么

**保留**：`ClassWorld`、Plexus/Sisu 组件图、所有 `@Singleton`（含下面全部缓存）、Windows 上的 WatchService 注册。

**每次重置**（`Server.java:620-657` + `DaemonMavenCling`/`DaemonMavenParser`）：
`maven.multiModuleProjectDirectory` 重设；system properties 从 client env 重新灌；`System.out/err` 重绑；JLine terminal 重建；`ProjectBuildLogAppender.setProjectId(null)`（并行多模块下这是日志/prompt 归属正确性的关键）；`updateState(Idle)` + `System.gc()`。

**选择性释放**：realm 逐个 `disposeRealm`，不是整体 dispose。见 §9。

---

## 9. 缓存层

### 9.1 抽象

- `Cache<K, V extends CacheRecord>`：`contains/get/put/clear/removeIf/computeIfAbsent`
- `CacheRecord` 只有两个方法：`Stream<Path> getDependencyPaths()`（"**文件**（非目录）路径，修改或删除即失效"）和 `void invalidate()`。整个抽象就一句话：**声明你的文件依赖，文件变了告诉你**。
- `CacheFactory.newCache()`：工厂而非全局 store，这样每个 cache 实例持有自己的失效簿记。

### 9.2 三种实现 / 两种失效信号

`DefaultCacheFactory` 一行分派（`cache/impl/DefaultCacheFactory.java:40`）：

```java
this.delegate = Os.current() == Os.WINDOWS ? new WatchServiceCacheFactory() : new TimestampCacheFactory();
```

理由写在注释里（`:37-39`）：**Windows 上 `BasicFileAttributes.fileKey()` 恒为 null**，单靠 mtime 不可靠，所以 Windows 用 `WatchService`。

- **`TimestampCacheFactory`（pull）**：`FileState = (path, lastModifiedTime, fileKey)` 快照；缺失文件容忍为 `FileTime.fromMillis(0)`（"reactor artifact 可能不存在"）；`get/computeIfAbsent` 时在 `ConcurrentHashMap.compute` 内重新 stat 并比对，不一致则 `invalidate()`。stat 异常一律 `catch (RuntimeException e) { /* 忽略并失效 */ }`——**fail-safe 偏向正确而非速度**。代价是每次访问 O(依赖 jar 数) 次 stat。
- **`WatchServiceCacheFactory`（push）**：`WatchService` 只能监听**目录**，而 CacheRecord 关心**文件**，所以维护 `registrationsByDir: Map<Path, Registration>`，`Registration` 持 `WatchKey` + `AtomicInteger` **引用计数**，归零才 `watchKey.cancel()`（`:172-196`）。注册 `ENTRY_DELETE + ENTRY_MODIFY`；**`OVERFLOW` 时把该目录下所有 record 全部失效**（`:143-162`，丢事件的保守正确响应）。测试里 macOS 睡 2500ms、其他平台 200ms 以吸收 OS 监听延迟。

### 9.3 缓存了什么

5 个类，全部是 Maven 自身 `Default*Cache` 的 `@Singleton @Named @Priority(10)` **同优先级覆盖**：

| 类 | Maven 父类 | 缓存内容 | `invalidate()` | 文件依赖 |
|---|---|---|---|---|
| `InvalidatingPluginRealmCache:43` | `DefaultPluginRealmCache` | plugin ClassRealm + artifact 列表 | **`disposeRealm`** | realm 的 artifacts |
| `InvalidatingExtensionRealmCache:42` | `DefaultExtensionRealmCache` | 扩展 realm + descriptor | **`disposeRealm`** | artifacts |
| `InvalidatingPluginDescriptorCache:49` | `DefaultPluginDescriptorCache` | 解析后的 `PluginDescriptor` | **`disposeRealm`** | descriptor 的 artifacts |
| `InvalidatingPluginArtifactsCache:39` | `DefaultPluginArtifactsCache` | 插件 artifact 解析结果 | no-op | artifacts |
| `InvalidatingProjectArtifactsCache:53` | `DefaultProjectArtifactsCache` | 每项目已解析 artifact 集 | no-op | artifacts（过滤 null） |

**与 Maven 自身缓存的交互技巧**（关键）：`put()` 先调 `super.put(...)` 让 Maven 建好它自己的 `CacheRecord`，然后**立刻把它从 Maven 的内部 map 里摘掉**，存进 mvnd 自己的 cache：

```java
CacheRecord record = super.put(key, pluginRealm, pluginArtifacts);
super.cache.remove(key);                        // 从 Maven map 摘掉
cache.put(key, new Record(record));              // 只留在 mvnd cache
```
（`InvalidatingPluginRealmCache.java:107-112`）

即 mvnd 不是"复制"一份缓存，而是**把 Maven 的缓存搬迁**到一个能按文件系统状态驱逐的容器里；record 对象仍是 Maven 自己的类型，所以 Maven 侧无需任何改动。`register(project, key, record)` 在 3 个类里被覆盖成 no-op（`:120` / `:97` / `:251`），刻意丢弃 Maven 的 per-project 反向引用，避免跨构建持有已死 project。

Maven 4 特有的两处加固：`InvalidatingPluginDescriptorCache` 在存取两侧都 **clone** descriptor（`:93,100,109,126`），因为 Maven 4 的 descriptor 带 per-lookup 可变的 realm/artifact 状态，不能共享同一实例；`InvalidatingPluginArtifactsCache.assertUniqueKey` 对重复 key 直接抛异常（`:94-98`）。

`InvalidatingProjectArtifactsCache.CacheKey`（`:55-170`）重写了 Maven 的 key，预计算 `hashCode`，并镜像 `repositoriesHashCode/Equals`，含"仓库管理器展开为镜像仓库"的逻辑。

### 9.4 EventSpy 驱动的构建边界失效

`cache/invalidating/InvalidatingRealmCacheEventSpy.java` 是 `@Typed(EventSpy.class)`，**由构建生命周期驱动而非文件系统轮询**，与 §9.2 互补：

- `MavenExecutionRequest`：记录 `multiModuleProjectDirectory`；把 `mvnd.pluginRealmEvictPattern` 编译成匹配器。`mvn:groupId:artifactId[:version]` 会被翻译成仓库坐标 glob `glob:**/group/path/artifactId/version-or-**`（`:83-100`）；多个 pattern 用 OR 串起来。
- `MavenExecutionResult`：对三个 cache 做 `removeIf(shouldEvict)`。判定逻辑（`:130-155`）是遍历 realm 的 URL，过滤 `file:` 协议，**只要 realm 里含有来自 reactor 目录的 jar，就在每次构建结束时驱逐**——这条规则解决了"同一次构建里先构建的模块，其产物在后面被覆盖/改写"导致读到旧 realm 的问题。`URISyntaxException` 一律 `return true`（fail-safe 驱逐）。

顺序上要注意：构建**之后**驱逐（`MavenExecutionResult`）+ 构建**之中**靠 timestamp/WatchService，任一机制至少能命中一次改动。

### 9.5 `CachingPluginVersionResolver`

`daemon/.../plugin/CachingPluginVersionResolver.java` 只缓存 **version range / LATEST / RELEASE / 前缀元数据**解析（不是固定版本）。

存储位置很特别：缓存放在 **Aether 的 `SessionData`** 里而不是字段里（`:75-85`），且用 `data.set(key, null, value)` 三参 CAS 形式 + `while` 重读做无锁 "set if absent"。这样缓存生命周期绑定在**仓库会话**上，会话结束即丢弃，不会成为 daemon 生命周期的无界泄漏。

**它没有任何失效机制**——这是与 §9.3 四个 cache 的不对称点：`PluginVersionResult` 是纯元数据解析结果，没有 jar 可做指纹，只能依赖 session 级生命周期 + 本地仓库的 `maven-metadata` 更新策略。

---

## 10. 交互式输入与提示

daemon 没有终端，所以所有 I/O 都 RPC 回 client。

**Prompt（Maven `Prompter` SPI）**：`DaemonPrompter` 是 `@Named @Priority(20) @Typed(Prompter.class)`（`DaemonPrompter.java:39-42`），靠 §8.1 的包导出才可见。`doPrompt` 流程（`:140-153`）：取 `Connection.getCurrent()`（线程绑定的本次构建连接）→ `ProjectBuildLogAppender.getProjectId()` 打上模块标签 → 生成 UUID `uid` → `con.request(msg, PromptResponse.class, r -> uid.equals(r.getUid()))` **同步阻塞**，靠 uid 匹配器在并发 prompt 中解复用。保留了 Maven 原有的校验循环（`:81-106`）：不在候选值里就重问，`line == null && defaultReply == null` 抛 `IOException("EOF")` 而不是死循环。

**stdin 读取**：daemon 侧把 `DaemonInputStream` 装到 `System.in`（`Server.java:511-516`）。mojo 调 `System.in.read()` → 缓冲空则发 `REQUEST_INPUT(projectId, bytesToRead)` 并阻塞在条件变量上（`DaemonInputStream.java:155-159`）；client 的 `TerminalInputHandler` 线程读终端后回 `INPUT_DATA`，daemon 的 receiver 线程喂给 `daemonInputStream` 并 signal。`available()` 有**硬编码 1 秒** `awaitNanos` 预算（`:110`），超时返回本地缓冲量而非无限阻塞。

---

## 11. native / GraalVM

`native/.../CLibrary.java` 只有 3 个 native 方法：`setenv`、`chdir`、`getOsxMemoryInfo`。**没有窗口标题设置，没有进程列举**。macOS 内存信息走 `sysctl(CTL_HW/HW_MEMSIZE)` + `host_statistics64`（`mvndnative.c:93-138`），供 §5.1 的内存回收策略使用。

`MvndNativeLoader`（384 行）是从 HawtJNI 抄来的完整提取/加载逻辑：先删陈旧的 `mvndnative-<ver>*`（除非有 `.lck` 伴随文件——Windows DLL 未解锁 bug #80）、提取到 UUID 后缀的临时名（避免不同架构 JVM 并发冲突）、**先建 `.lck` 再写**、置 rwx、然后**逐字节校验**再 `System.load`。

native-image 侧（`client/pom.xml:140-282`）：非 mac 平台启用 `-H:+StaticExecutableWithDynamicLibC`；`linux-image-only-require-glibc-2.12/-2.17` profile 用 `target/graalvm-libs-for-glibc-2.X` 目录做 **glibc 基线兼容**（对老 sysroot stub 编译，保证在老发行版上能跑）。`Environment.isNative()` = `org.graalvm.nativeimage.kind == "executable"`。

`bin/mvnd.sh:97-123` 决定启动哪个：native 存在则 exec native image，Linux 上还额外跑 `ldd` 检查 `"not found"`；失败才回落到 JVM 路径。

**native 下 `--noDaemon` 直接抛 `UnsupportedOperationException`**（`DaemonConnector.java:127-130`）——native image 进程里没法再宿主一个 JVM。

---

## 12. 客户端实现与 Multi-Release JAR

`client` 模块有两个同名 `org.mvndaemon.mvnd.client.DefaultClient`：

| | `java-mvnd/`（559 行，真客户端） | `java-fallback/`（33 行） |
|---|---|---|
| 形态 | `implements Client` | **不实现 `Client`**，只有 `main` |
| 行为 | 完整参数解析、daemon 复用、socket 流式输出、purge/status/stop | 打印两条 warn 后 `MavenCling.main(argv)` |
| 依赖 | jline、registry、connector、Message | 仅 `maven-core` + slf4j |

`client/pom.xml:80-126` 用三套 `maven-compiler-plugin` execution 把 `java-fallback` 编译到 **最低支持 JDK**（17 以下可加载），`java-mvnd` 走 `<multiReleaseOutput>`，jar manifest 打 `Multi-Release: true`。因为 launcher 用 ClassWorlds 且 classpath 里就是这个 MR-JAR，**JDK < 17 自动加载 fallback，≥ 17 加载真客户端**——一个产物，两种行为。

这解释了一个反直觉的现象：**mvnd 2.x 最低要 JDK 17**，老 JDK 上会静默降级成"每次起 JVM"，只是打两条 warn。

`Client` 接口只有两个方法（`Client.java:28-32`），对 daemon/socket/mvnd 选项**零知识**——这正是它能同时被真客户端和"纯 Maven 桩"实现的原因。

---

## 13. 缺陷与风险清单

代码审计中发现的具体问题，按影响排序：

1. **协议无帧、无版本、无校验和**（`Message.java:84`）。一旦流错位不可恢复；跨 mvnd 版本只能靠"指纹不匹配就换 daemon"绕开，没有协议层降级。
2. **`KEYBOARD_INPUT(16)` 在 `Message.read` 里没有 case**（`Message.java:88-139`），daemon 侧若发这个类型会在 `:140` 抛 `IllegalStateException`。目前是 client-only 类型，未触发。
3. **注册表把枚举按 ordinal 序列化**（`DaemonRegistry.java:192,231`）。往 `DaemonState` / `DaemonExpirationStatus` 插入常量会静默改变旧注册表文件的语义。缓解手段是解析失败即全量 reset。
4. **注册表丢失更新在跨进程场景理论存在**：A 读、B 读、A 写、B 写，B 的快照不含 A 的条目。20s 锁把窗口压得很小但不消除；实际靠 daemon 自身周期性 `updateState` 自愈，以及 `registryUnavailable()` 策略兜底。
5. **注册表损坏 = 全量丢失**，不是丢单个 daemon（`DaemonRegistry.java:245-250`）。
6. **`getProcessId0` 在无 `/proc/self` 的平台可能返回随机 16 位整数**（`DaemonRegistry.java:309-311`），`--status` 的 RSS 列会失真。
7. **首条消息读取是无超时边界的 1 分钟**（`Server.java:279`），加上 §4 的连接串行化——一个连上、认证通过、然后挂住的客户端会阻塞该 daemon 最多 60s，其间其他所有客户端全部排队。
8. **`ClientDispatcher.projectLogMessage` 有死代码**：`if (b != Boolean.TRUE) {}`（`ClientDispatcher.java:101`）空分支；配套的 `projects` map 被写但从未被读。
9. **自定义 UTF 的补充字节数启发式脆弱**：`readUTF` 每次 refill 请求 `min(768, len - charIdx)`，而 `len` 是 char 数、上限是字节数（`Message.java:265`），多字节内容下可能补不足一个 3 字节序列，靠每分支重检 `i == cnt` 才没出错。
10. **`getNotIdle()` 不调 `read()`**（`DaemonRegistry.java:109-111`），直接过滤内存里恰好存在的 map——看起来是 bug，但当前树无人调用。
11. **`mvnd.enableAssertions` 在整个仓库无消费点**：声明为判别性选项、文档说会加 `-ea`，但 `startDaemonProcess` 里并没有拼这个参数。设了它会导致 daemon 指纹不同（多起一个 daemon）却毫无效果。
12. **`CachingPluginVersionResolver` 无失效机制**，与其它四个有文件指纹的 cache 不对称。
13. **`lowMemory` 探测整体吞异常**（`DaemonExpiration.java:273-275`），探测失败 = 永不因系统内存压力回收。
14. **`SocketFamily` 声明默认值是 `inet`**（`Environment.java:294`），但 client 总会显式覆盖成 `unix`（JDK16+），且为此每次拉起 daemon 多 fork 一次 `java -version`。
15. **`PROJECT_TEST_PROGRESS(31)` 能解码但 daemon 侧不产出**（其自身 javadoc `Message.java:70-74` 承认），客户端渲染路径沉睡。
16. **`Connection.request` 每次唤醒都在 monitor 内线性扫描 `recvQueue` 做谓词匹配**（`Server.java:600-613`），与 receiver 线程争锁。
17. **`BuildException` 在客户端被拍平成普通 `Exception`**（`DefaultClient.java:411-416`），cause 链与 suppressed 全部丢失。
18. **`noModelCache` / `buildTime` / `pluginRealmEvictPattern` / `maxLostKeepAlive` 这几个选项**在本仓库无消费点或只在特定路径生效，需在 1.x 文档里交叉验证后再依赖。

---

## 14. 对 nop-entropy 的可借鉴点

nop-entropy 不是 Java 构建工具，但"常驻进程 + 增量失效"这套模式在几个场景同构：

1. **"声明文件依赖 → 变了就告诉我"的失效契约**（`CacheRecord.getDependencyPaths()`）非常轻量：一个接口两个方法即可。可以作为任何"编译产物/元数据常驻内存"机制的标准抽象——例如 `nop-treesitter` 的 grammar 缓存、`nop-metadata` 的模型缓存、`nop-jq` 的编译缓存。
2. **Windows 必须用 WatchService 而非 mtime**（`fileKey()` 在 Windows 恒 null）——这是跨平台缓存失效的硬约束，任何做文件时间戳判定的模块都适用。
3. **指纹 + 兼容性 spec 的 daemon 复用模型**：`javaHome + 判别性属性集合`，不兼容就换一个而不是协商降级。映射到"工具服务复用"上等价于"运行时版本 + 配置指纹"。
4. **push/pull 双失效通道互补**：构建边界（EventSpy）+ 文件系统状态（timestamp/WatchService），至少一条命中即可，比单一机制健壮。
5. **fail-safe 方向一致**：stat 失败 / URL 非法 一律按"失效"处理，而不是按"未变更"处理。
6. **`ClassWorld` 式的"长生命周期容器 + 精确 dispose 子域"**：不要整体 dispose，按 key 逐个摘除。
7. **`buildRequestEnvironment()` 可覆盖**（`DefaultClient.java:245-252`）让测试可以隔离环境变量——测试工程化的实用细节。

## 15. 不可借鉴 / 需要警惕的点

1. **协议无帧无版本**在长生命周期组件里是隐患；nop 若做常驻服务，帧头里至少要有 magic + version + length。
2. **跨进程共享状态用"全量文件重写"**在 daemon 数量增大时代价线性上升（每次状态变更一次加锁全量读写），且没有 CAS，容易丢更新。
3. **靠枚举 ordinal 序列化**是经典兼容陷阱。
4. **多客户端并发靠状态约定而非排队**，导致"第二个 client 阻塞在 backlog 里"这种不可观测的等待。
5. `BuildException` 拍平、消息类型缺失 case 之类的粗率，在内部框架里不该复制。

## 16. 未决问题

- [ ] mvnd 1.x 分支的 `DaemonMavenCli`（真·覆盖 `container()` 的那一版）在 Maven 3 生态里仍被大量使用，本文哪些结论在 1.x 上不成立需要单独标注一份对照表。
- [ ] `mvnd.noModelCache` / `SnapshotModelCache` 在 1.x 存在、2.x 移除，对增量构建能力是否构成实际回退，需要在长构建场景实测。
- [ ] mvnd 2.x 的实测加速比（本机 darwin-arm64，需跑 `integration-tests` 的 benchmark）未做，本文只做静态分析，未验证性能收益。
- [ ] `client/src/main/java-mvnd` 与 `daemon` 模块对 Maven 4.0.0-rc-6 的 API 依赖意味着 mvnd 2.x 目前**只能配 Maven 4 rc**，用于生产前需确认 rc 稳定性。
- [ ] `DaemonCompatibilitySpec` 不比较 `locale`（虽存在 `DaemonInfo.locale`），跨 locale 复用 daemon 是否会引发日期/排序类插件问题，未验证。

## References

- 源码：`~/sources/mvnd` @ master `53dadc00`（2026-09-17）
  - `daemon/src/main/java/org/mvndaemon/mvnd/daemon/Server.java`、`DaemonExpiration.java`、`DaemonMemoryStatus.java`
  - `daemon/src/main/java/org/apache/maven/cli/DaemonMavenCling.java`、`DaemonMavenInvoker.java`、`DaemonMavenParser.java`、`DaemonPlexusContainerCapsuleFactory.java`、`DaemonMessageBuilderFactory.java`、`CommonsCliDaemonMavenOptions.java`
  - `daemon/src/main/java/org/mvndaemon/mvnd/cache/**`、`plugin/CachingPluginVersionResolver.java`、`timing/BuildTimeEventSpy.java`、`interactivity/DaemonPrompter.java`
  - `client/src/main/java/org/mvndaemon/mvnd/client/DaemonConnector.java`、`DaemonParameters.java`、`DaemonDiagnostics.java`、`DefaultClient.java`（`java-mvnd` / `java-fallback`）
  - `common/src/main/java/org/mvndaemon/mvnd/common/Message.java`、`Environment.java`、`DaemonRegistry.java`、`DaemonInfo.java`、`DaemonCompatibilitySpec.java`、`DaemonState.java`、`SocketFamily.java`
  - `common/src/main/java/org/mvndaemon/mvnd/common/logging/TerminalOutput.java`、`TerminalInputHandler.java`
  - `agent/src/main/java/org/mvndaemon/mvnd/agent/Agent.java`
  - `native/src/main/java/org/mvndaemon/mvnd/nativ/*`、`native/src/main/native/mvndnative.c`
  - `logging/src/main/java/org/mvndaemon/mvnd/logging/slf4j/*`
  - `dist/src/main/distro/bin/mvnd.sh`、`dist/src/main/distro/conf/mvnd.properties`
- 相关既有分析：`docs/maven/maven-vfs-analysis.md`（Maven 4 VFS 路径定制，与本主题不重叠）
- 抄写来源：mvnd 的 `DaemonDiagnostics`（Gradle v5.6.2）、`MvndNativeLoader`（HawtJNI）、`DaemonRegistry`（Gradle + OpenHFT AbstractBytes）——类注释均保留了出处链接
