# nop-commons 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-kernel/nop-commons
- 文件数: 实测 403 个 Java 主代码文件（src/main/java，任务描述称约 431，以 `find` 实测为准；测试代码不在范围）
- **覆盖范围声明**:
  - **逐行深读**（正确性/并发/资源重点）: `concurrent` 包全部 31 个实现类（executor、lock、lock/impl、ratelimit、semaphore、batch、impl、thread）、`cache` 包全部 17 个类、`io/serialize/JavaSerializer`、`io/stream/FileRandomOutputStream`、`util` 高频类（IoHelper、FileHelper、DateHelper、NetHelper 关键段、ClassHelper 关键段、MathHelper/IRandom 随机部分、StringHelper 转义/路径/pad/repeat 段约 800 行）、`crypto`（HashHelper、AESTextCipher 全文）、`functional`（Lazy、Seq）、`lang/impl`（Cancellable、CallbackSet）、`collections`（IntHashMap 核心算法段、StringTrie 全文）、`env/PlatformEnv`、`metrics/GlobalMeterRegistry`、`mutable/MutableInt`。
  - **模式扫描覆盖全部 403 个文件**: 空 catch、`new RuntimeException`、`printStackTrace`、`SimpleDateFormat` 字段、`Thread.sleep`、`synchronized`、可变 static 字段、`ObjectInputStream/readObject`、catch 后 return null、`@Inject`/`@InjectValue`、`IllegalStateException/IllegalArgumentException`。所有命中点均已 Read 上下文核实，未证实的一律未写入。
  - **未深读**（仅结构浏览或确认为上游拷贝代码）: `text/tokenizer/TextScanner`（1429 行，仅抽查）、`text/XMLChar`、`text/MutableString`、`path/AntPathMatcher`（Spring 拷贝）、`bytes/Bytes`（HBase 拷贝）、`bytes/ByteString/FastByteBuffer/LittleEndian`、`collections/LongHashMap`（Kryo 拷贝）及 IntHashMap 其余部分、`diff`、`aggregator`、`partition`、`tuple`、`type`、`text/marker`、`text/regex`。这些区域按 5%~10% 抽样，未发现问题的区域不代表无问题。
  - 上游拷贝代码（Tomcat TaskQueue/StandardThreadPoolExecutor、Spring cleanPath、Kryo IntHashMap、HBase Bytes）按惯例只报告与上游有实质偏差的问题。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 6 |
| P2 | 8 |
| P3 | 6 |

> 说明: 本模块无 P0（有现实调用路径且必然造成数据错误/崩溃的缺陷）。P1 中 #2/#3/#4 属于"代码本身确定性损坏、但当前仓库内尚无调用方"的公共 API 缺陷，任何下游一旦使用即触发。

## 发现列表

### [P1] Lazy.get() 永远不设置 loaded 标志，懒加载缓存语义完全失效

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/functional/Lazy.java:49-60`
- **维度**: D1（逻辑错误）+ D6（重复计算）
- **证据**:
```java
private volatile T value;
private volatile boolean loaded;

public T get() {
    if (!loaded) {
        synchronized (this) {
            if (loaded)
                return value;
            value = supplier.get();
        }               // <-- 此处缺少 loaded = true;
    }
    return value;
}
```
- **现状**: `loaded` 只在 `set()` 中被置 true，`get()` 内部计算后从不回写。于是 `!loaded` 恒为 true，每次 `get()` 都会进入同步块并重新执行 `supplier.get()`，返回值也不缓存（若 supplier 返回值变化，多次 get 结果不同）。
- **风险**: 类注释明确写着"利用 value 属性缓存 supplier 计算后的值"，契约被破坏。仓库内现实调用方包括 `nop-graphql-core` 的 `GraphQLWebService.graphQLLogger`、`nop-biz` 的 `ObjMetaBasedValidator.dictLoader`、`nop-xlang` 的 `BizFilterEvaluator.dictLoader`、`DslModelHelper.g_excelModelLoaderFactory`——这些 supplier 每次访问都会重新执行（重复服务查找/重复加载字典），既有性能损耗也可能造成行为漂移。
- **建议**: 在 `synchronized` 块内 `value = supplier.get();` 之后补 `loaded = true;`；同时注意 supplier 返回 null 时当前语义（loaded 置 true、value 为 null）需要一并定义。
- **误报排除**: 已通读全类确认 `loaded` 无其他赋值点（仅 `set()`）；已核实调用方确实通过 `Lazy.of(...)` 使用而非 `set()` 预填。

### [P1] LocalFileLock.tryLock 恒返回 false：锁获取"成功"却报告失败，lock() 必抛超时异常且泄漏 FileLock

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/LocalFileLock.java:40-105`
- **维度**: D1（逻辑错误）+ D2（资源泄漏）
- **证据**:
```java
private boolean tryLock(long timeoutMs, boolean interruptable) throws InterruptedException {
    ...
    do {
        randomAccessFile = new RandomAccessFile(lockFile, "rw");
        channel = randomAccessFile.getChannel();
        lock = channel.tryLock();
        ...
    } while (lock == null && !CoreMetrics.isExpiredNanos(expireNanos));
    ...
    return false;   // <-- 无论 lock 是否获取成功都返回 false
}

public void lock() {
    boolean result = tryLock(timeoutMs, false);
    if (!result)
        throw new NopException(ERR_FILE_ACQUIRE_LOCK_TIMEOUT)...;  // 永远走到这里
}
```
- **现状**: 私有 `tryLock(long,boolean)` 的返回值是硬编码 `false`。而 `FileChannel.tryLock()` 成功时返回非 null（失败时抛 `OverlappingFileLockException` 而非返回 null），所以只要获锁成功，循环退出后依然返回 false。
- **风险**: (1) `Lock.lock()` 在**成功获取锁之后**抛 `ERR_FILE_ACQUIRE_LOCK_TIMEOUT`；(2) `Lock.tryLock()` 返回 false 但进程实际持有 OS 文件锁，调用方认为失败而去重试/走分支，锁句柄与 RandomAccessFile 挂在对象上直到 GC，期间其他进程永远无法获锁（锁饥饿）。该类是公共 `java.util.concurrent.locks.Lock` 实现，任何下游使用即触发。当前仓库内无调用方（已全仓 grep 确认），故未评 P0。
- **建议**: 改为 `return lock != null;`；同时 `tryLock()` 抛 `OverlappingFileLockException`/`ClosedChannelException` 时也应关闭并清理已打开的 `randomAccessFile`/`channel`（当前外层 catch 直接抛出，未关闭已打开句柄）。
- **误报排除**: 已通读全类 127 行，确认无其他返回路径；确认 `FileChannel.tryLock` 语义（成功返回锁对象、重叠锁抛异常）。

### [P1] LocalResourceLockManager.tryResetLease/isHoldingLock 过期判断条件写反：未过期的锁被当作过期移除，过期的锁反被报告为"持有"

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/lock/impl/LocalResourceLockManager.java:226-268`
- **维度**: D1（逻辑错误）+ D3（互斥被破坏）
- **证据**:
```java
public boolean tryResetLease(IResourceLockState lock, long leaseTime) {
    ...
    if (expireTime < current) {          // expireTime < now 意味着租约已过期
        state.setExpireTime(current + leaseTime);
        ...
        return true;                      // 却对已过期锁续租成功
    } else {
        removeExpiredLock(state);         // 反而把仍然有效的锁移除并唤醒等待者
    }
}

public boolean isHoldingLock(IResourceLockState lock) {
    ...
    if (expireTime < current) {
        if (locks.get(state.getResourceId()) == lock) {
            return true;                  // 已过期却报告"持有"
        }
    } else {
        removeExpiredLock(state);         // 未过期却被移除
    }
}
```
- **现状**: 与同文件其他方法的语义完全矛盾——`checkTimeout()`（第 122 行）和 `tryLockWithLease()`（第 166 行）都把 `expireTime <=/< now` 视为"已过期"。对照 nop-sys 的 `SysDaoResourceLockManager`（正确语义：仅对仍在表中的有效租约续期/判持有），这两个方法的条件显然是写反了（应为 `expireTime > current`）。
- **风险**: 对一个刚获取、租约充裕的锁调用 `isHoldingLock()` 会立刻把它从 `locks` 表移除、countDown 唤醒所有等待者，另一线程随即可以获取同一资源锁——**互斥语义被确定性破坏**；反之已过期的锁会被续租并报告持有。附带问题: `ResourceLock.tryResetLease`（`ResourceLock.java:70-72`）在未获锁时 `this.lock == null`，传入本方法后 `state.getLatch()` 直接 NPE。当前仓库内无 isHoldingLock/tryResetLease 的 LocalResourceLockManager 调用方（nop-web 的 `ResourceWithHistoryProvider` 只用 runWithLock/lock/unlock 路径），故未评 P0。
- **建议**: 将两处条件改为 `expireTime > current`（有效租约）分支内做续租/判持有，else 分支 `removeExpiredLock` 并 return false；`ResourceLock.tryResetLease` 增加 `lock == null` 判断。
- **误报排除**: 已对照 `checkTimeout`、`tryLockWithLease`、`ResourceLockState`、`SysDaoResourceLockManager` 三处交叉验证语义，确认是反转而非另有隐含语义。

### [P1] FileHelper.writeTextWithLock 用字符数 truncate，非 ASCII 内容被截断损坏

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/FileHelper.java:492-496`
- **维度**: D1（数据错误）
- **证据**:
```java
ByteBuffer sendBuffer = ByteBuffer.wrap(content.getBytes(charsetName));
while (sendBuffer.hasRemaining()) {
    channel.write(sendBuffer);
}
channel.truncate(content.length());   // content.length() 是字符数，不是字节数
```
- **现状**: 写入的是 `content.getBytes(charsetName)` 的字节，截断长度却用 `content.length()`（char 数）。UTF-8 下中文内容的字节数约为字符数 3 倍，`truncate` 会把刚写入的内容尾部切掉；GBK/UTF-16 同理。
- **风险**: 任何非纯 ASCII 内容写入后文件被静默截断（数据损坏）。该方法注释标明 copy from Nacos ConcurrentDiskUtil，上游实现用的是 `content.getBytes(charsetName).length`，属于移植时引入的偏差。当前仓库内无调用方（已全仓 grep），故未评 P0。
- **建议**: `channel.truncate(content.getBytes(charsetName).length)`，或复用已构造的 `sendBuffer` 计算写入字节数。
- **误报排除**: 已确认 `content.length()` 为 String 字符数；已确认无其他逻辑补偿该截断。

### [P1] NetHelper.findLocalIp 绕过懒初始化直接读 LOCALHOST4 字段，特定网络环境下 NPE

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/NetHelper.java:349-352`
- **维度**: D1（NPE）
- **证据**:
```java
public static String findLocalIp() {
    InetAddress addr = findFirstNonLoopbackAddress();
    return addr == null ? LOCALHOST4.getHostAddress() : addr.getHostAddress();
    //                     ^^^^^^^^^ 直接读私有静态字段，而非 LOCALHOST4()
}
```
- **现状**: `LOCALHOST4` 是懒初始化字段（第 68 行声明为 null，`LOCALHOST4()` 方法负责调 `_initLocalHost()`；注释说明 GraalVM 不允许静态直接创建 InetAddress）。`findLocalIp` 却直接访问字段。当 `findFirstNonLoopbackAddress()` 返回 null（无可用非回环网卡，或网卡被 `CFG_NET_PREFER/IGNORE_NETWORK_PATTERN` 全部过滤，容器/内网环境并不罕见）且此前没有任何调用触发过 `LOCALHOST4()`/`LOCALHOST6()` 初始化时，`LOCALHOST4.getHostAddress()` 抛 NPE。
- **风险**: 现实调用链: `SysDaoResourceLockManager`（分布式锁记录 holder 地址）、`SysSequenceGenerator`（序列 hostId）、`AbstractLeaderElector`（选主）、`DefaultServerAddrFinder` 都会走到此方法，异常环境下一个 NPE 打断锁获取/序列生成。
- **建议**: 改为 `LOCALHOST4().getHostAddress()`。
- **误报排除**: 已确认 `LOCALHOST4` 仅在 `_initLocalHost()` 内赋值，`_initLocalHost()` 仅被两个访问器方法触发；`findFirstNonLoopbackAddress` 存在返回 null 的路径（所有网卡被过滤时最后 `InetAddress.getLocalHost()` 也可能抛 UnknownHostException，仅 LOG.warn 后 return null）。

### [P1] LocalCache.putIfAbsent 非原子 check-then-act，并发下缓存 API 契约破坏

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:233-241`（接口默认实现 `ICache.java:66-71` 同样问题）
- **维度**: D3（并发竞态）+ D8（契约）
- **证据**:
```java
@Override
public boolean putIfAbsent(K key, V value) {
    V old = cache.getIfPresent(key);
    if (old == null) {
        cache.put(key, value);
        return true;
    }
    return false;
}
```
- **现状**: 先 `getIfPresent` 再 `put`，两个线程并发调用同一 key 时可能都观察到 null、都 put、都返回 true。底层是 Caffeine，其 `cache.asMap().putIfAbsent(key, value)` 本身提供原子语义，实现却没有使用。
- **风险**: `putIfAbsent` 返回 true 的语义是"由我插入"，调用方（如 nop-core `ResourceLoadingCache.state_loadFrom` 中 `if (!cache.putIfAbsent(...)) entry.clear()`）据此决定是否清理/占用，竞态下两个调用方都认为自己占有条目，后写覆盖先写。LocalCache 是全仓库默认本地缓存实现，影响面大。
- **建议**: 改为 `return cache.asMap().putIfAbsent(key, value) == null;`；`ICache` 接口默认方法的 javadoc 应注明实现应保证原子性。
- **误报排除**: 已核对 Caffeine `Cache#asMap` 返回 `ConcurrentMap`，`putIfAbsent` 原子；确认 `getAndSet`、`MapCache.putIfAbsent`（基于 `map.putIfAbsent`）无此问题。

### [P2] RateLimitExecutorImpl.throttle 节流语义失效，且 promiseMap 永不清理

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:28-50`
- **维度**: D1（逻辑错误）+ D6（内存泄漏）
- **证据**:
```java
public void throttle(final Object key, long delay, Runnable task) {
    final CompletableFuture<Void> promise = new CompletableFuture<>();
    Future<?> old = promiseMap.putIfAbsent(key, promise);   // 永远不会被替换/移除
    if (old != null && !old.isDone())
        return;
    executorService.schedule(...);                          // 调度的新 promise 不入 map
}

public void debounce(final Object key, long delay, Runnable task) {
    ...
    promiseMap.put(key, future);                            // 从不 remove
}
```
- **现状**: `throttle` 首次调用把一个 CompletableFuture 放入 map，此后该条目永不被替换或删除。首次任务执行完成后 `old.isDone()` 恒为 true，后续每次 throttle 调用都会绕过"上次未触发则跳过"的判断直接调度新任务——节流完全失效。`debounce` 用新 future 覆盖但从不 remove。两者的 map 条目按 key 无限累积，key 为动态值时泄漏。
- **风险**: 公共 API 契约破坏 + 潜在内存泄漏。当前仓库内调用方（FileWatcher、ZipFileWatcher、CliWatchZipCommand）只用 `debounce` 且 key 固定（"refresh"/"process"），泄漏量有限，故降为 P2。
- **建议**: throttle 改为 `promiseMap.compute` 或"map 中放 schedule 返回的 future，任务执行完毕后 remove"；debounce 在任务结束后清理条目。
- **误报排除**: 已通读全类并核实所有仓库内调用方只传固定 key。

### [P2] RoundRobinSupplier.resize 扩容失败时新建资源泄漏（closeNext 起始索引错误）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/RoundRobinSupplier.java:62-84`
- **维度**: D2（资源泄漏）
- **证据**:
```java
} else if (objects.length() < size) {
    AtomicReferenceArray<T> newObjects = new AtomicReferenceArray<>(size);
    try {
        ...
        for (int i = objects.length(); i < size; i++) {
            newObjects.set(i, factory.get());      // 从旧数组长度之后新建
        }
    } catch (Exception e) {
        toClose = newObjects;
        throw NopException.adapt(e);
    }
    this.objects = newObjects;
}
...
if (toClose != null)
    closeNext(toClose, size);                      // for (i = size; i < array.length(); i++) —— 循环体永不执行
```
- **现状**: 扩容失败时 `toClose = newObjects`（长度 == size），而 `closeNext(toClose, size)` 从下标 `size` 开始遍历——起点即终点，一个资源都不会被关闭。应从 `objects.length()`（新建资源的起始下标）开始关闭。对比缩小分支 `closeNext(toClose, size)` 语义正确（关闭下标 >= size 的旧元素）。
- **风险**: factory 中途抛异常时，本次扩容已 new 出的资源（连接、句柄等 AutoCloseable）全部泄漏。
- **建议**: 扩容失败分支单独 `closeNext(newObjects, oldLength)`，注意只关新建部分、不要关从旧数组共享复制来的元素。
- **误报排除**: 已核对缩小分支与扩容分支的数组构成，确认只有扩容失败路径有此问题。

### [P2] DefaultRateLimiter.getAcquireFailCount 返回的是成功计数

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/ratelimit/DefaultRateLimiter.java:33-41`
- **维度**: D1（复制粘贴错误）+ D8（契约）
- **证据**:
```java
@Override
public long getAcquireSuccessCount() {
    return acquireSuccessCount.get();
}

@Override
public long getAcquireFailCount() {
    return acquireSuccessCount.get();   // 应为 acquireFailCount
}
```
- **现状**: 复制粘贴错误，失败计数永远等于成功计数，`acquireFailCount` 字段虽在 `tryAcquire` 中正确自增但永远读不到。
- **风险**: 基于 IRateLimiter 统计的监控/熔断指标失真（失败被报告为成功数量）。
- **建议**: 改为 `acquireFailCount.get()`。
- **误报排除**: 已通读全类确认无别名/委托逻辑。

### [P2] SequentialTaskExecutor.getTotalExecutionTime 时间单位错乱，且字段无同步

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/SequentialTaskExecutor.java:68-70、122-147`
- **维度**: D1（单位错误）+ D3（可见性）
- **证据**:
```java
public long getTotalExecutionTime() {
    return TimeUnit.NANOSECONDS.convert(totalExecutionTime, TimeUnit.MILLISECONDS);
}
...
long begin = CoreMetrics.nanoTime();
...
diff = CoreMetrics.nanoTimeDiff(begin);   // 返回纳秒（已核对 CoreMetrics.nanoTimeDiff）
...
totalExecutionTime += diff;               // 累加的是纳秒
```
- **现状**: `totalExecutionTime` 累加纳秒，getter 却把它当作毫秒转换为纳秒，结果放大 10^6 倍。另外该字段由 worker 线程写、任意线程读，无 volatile/锁，存在可见性问题（long 字段理论上可撕裂读）。
- **风险**: 公开统计指标数值错误（quota 相关逻辑用 `quotaNanos` 对比同一 diff 是自洽的，不受影响）；跨线程读取可能读到过期值。
- **建议**: getter 直接返回 `totalExecutionTime`（纳秒）或统一改毫秒语义；字段加 volatile。
- **误报排除**: 已核对 `CoreMetrics.nanoTime()/nanoTimeDiff()` 均基于 `System.nanoTime`；quotaNanos 对比路径正确，确认仅 getter 有单位问题。

### [P2] GlobalCacheRegistry.register 先 put 后抛异常，失败时旧缓存已被覆盖、新缓存残留

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/GlobalCacheRegistry.java:65-71`
- **维度**: D1（错误路径副作用）+ D3
- **证据**:
```java
public void register(@Nonnull ICacheManagement<?> cache) {
    ICacheManagement<?> oldCache = caches.put(cache.getName(), cache);   // 先替换
    if (oldCache != null)
        throw new NopException(ERR_CACHE_DUPLICATE_REGISTRATION)...;    // 后抛异常
    ...
}
```
- **现状**: 注册重名缓存时先执行 `put`（旧缓存已被新缓存替换）再抛异常。调用方收到"注册失败"异常后通常会继续运行，但注册表里已经是新缓存，旧缓存实例被悄悄顶掉；且抛异常路径上新缓存仍留在 map 中——"失败"的操作实际生效了一半。
- **风险**: 重复注册场景（如多个模块注册同名缓存）下缓存实例被静默替换，违背 `ERR_CACHE_DUPLICATE_REGISTRATION` 的防护意图。
- **建议**: 改为 `if (caches.putIfAbsent(cache.getName(), cache) != null) throw ...`。
- **误报排除**: 已确认无调用方在捕获该异常后做回滚。

### [P2] IoHelper.getEncodingFromBOM 对不足 4 字节的流抛"意外 EOF"

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/IoHelper.java:332-354`
- **维度**: D1（边界条件）
- **证据**:
```java
public static String getEncodingFromBOM(InputStream is) throws IOException {
    is.mark(4);
    byte[] bom = new byte[4];
    readFully(is, bom);        // readFully 不足 4 字节即 throw ERR_IO_UNEXPECTED_EOF
    ...
}
```
- **现状**: 无论流多长都先强制读满 4 字节。内容少于 4 字节的文件（如只有 BOM 无内容、或 1~3 字节的 XML/配置文件）直接抛 `ERR_IO_UNEXPECTED_EOF`，而不是返回 null/UTF-8。
- **风险**: 现实调用方 `XNodeParser.guessEncoding`（nop-core，第 201 行）解析小于 4 字节的资源文件时会得到误导性的 EOF 错误（真实原因是文件太短）。另外该方法依赖 `mark/reset`，传入不支持 mark 的流会抛 IOException，调用方需自行保证缓冲。
- **建议**: 改为按可读长度逐字节探测（先读 2 字节判 UTF-16/32，再读第 3 字节判 UTF-8，EOF 时 reset 返回 null）。
- **误报排除**: 已核对 `readFully` 实现与 XNodeParser 调用路径。

### [P2] JavaSerializer 原生 Java 反序列化无类型过滤（ObjectInputFilter 缺失）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/io/serialize/JavaSerializer.java:44-50`（配合 `IoHelper.java:48-49` 全局默认注册）
- **维度**: D5（不安全反序列化）
- **证据**:
```java
@Override
public Object getObjectInput(InputStream is) {
    try {
        return new ObjectInputStream(is);   // 无 setObjectInputFilter
    } ...
}
```
- **现状**: `JavaSerializer.INSTANCE` 是 `IoHelper.s_streamSerializer/s_byteArraySerializer` 的默认实现，`ObjectInputStream` 未配置任何 `ObjectInputFilter`（JDK 内置反序列化过滤器也未设置），任意 classpath 上的 gadget 类均可被实例化。
- **风险**: 反序列化入口在仓库内真实存在（nop-core `ResourceHelper.readObject` 读取资源状态文件；`IoHelper.serializeClone` 深拷贝）。当前数据源基本是平台自己写出的本地 store 文件，攻击面有限（需能写这些文件），故评 P2 而非 P0/P1，但作为全仓库默认序列化器应做加固。
- **建议**: 为 `ObjectInputStream` 设置基于白名单/最大深度/最大字节数的 `ObjectInputFilter`（至少 `Config.serialFilter` 级别限制），或在文档中明确禁止反序列化不可信数据。
- **误报排除**: 已确认仓库内无其他自定义 filter 注册点；已确认 ResourceHelper.readObject 走此实现。

### [P2] DateHelper.buildFormatter 缓存未命中后不回填，热路径每次重新编译 pattern

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/DateHelper.java:270-275`
- **维度**: D6（热路径重复计算）
- **证据**:
```java
static DateTimeFormatter buildFormatter(String pattern) {
    DateTimeFormatter formatter = s_formatters.get(pattern);
    if (formatter == null)
        formatter = DateTimeFormatter.ofPattern(pattern);   // 未 s_formatters.put(...)
    return formatter;
}
```
- **现状**: 类内专门维护了 `s_formatters` 缓存（静态块预注册了常用 pattern），但未命中时新建的 formatter 不回填缓存。所有非预注册 pattern 的 format/parse 每次调用都要执行 `DateTimeFormatter.ofPattern`（内部含 pattern 解析、区域设置处理），成本远高于缓存的 Map 查找。`DateTimeFormatter` 本身不可变线程安全，完全可缓存。
- **风险**: 高频日期格式化（报表、日志、导出）下 CPU 浪费；`s_formatters` 是 HashMap，若未来有人修复为回填需同时换 ConcurrentHashMap。
- **建议**: `s_formatters` 改为 `ConcurrentHashMap`，未命中时 `computeIfAbsent(pattern, DateTimeFormatter::ofPattern)`。
- **误报排除**: 已确认 `registerFormatter` 仅在静态初始化和显式注册时调用，运行期无回填路径。

### [P3] FileHelper.getClassPathFile / getJarFile 资源不存在时 NPE

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/FileHelper.java:417-424、426-440`
- **维度**: D1（NPE）
- **证据**:
```java
public static File getClassPathFile(String path) {
    URL url = FileHelper.class.getClassLoader().getResource(path);
    String s = url.getFile();      // url == null 时 NPE
    ...
}
public static File getJarFile(Class<?> clazz) {
    ...
    URL url = FileHelper.class.getClassLoader().getResource(path);
    String strUrl = url.toString(); // 同上
```
- **现状**: `getResource` 找不到时返回 null，两处直接解引用，抛出无诊断信息的 NPE。
- **风险**: `getClassPathFile` 被 `MavenDirHelper` 使用，`getJarFile` 被 `CliRepackageCommand` 使用；资源缺失（类路径配置错误）时得到裸 NPE 而非带路径的错误信息。
- **建议**: 判空后抛 `NopException`（带 path 参数）。
- **误报排除**: 已确认调用方未预先判空。

### [P3] ExecutorHelper.newScheduledExecutor 修改调用方传入的 config 对象

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:56-71`
- **维度**: D1（副作用）+ D8
- **证据**:
```java
public static ScheduledThreadPoolExecutor newScheduledExecutor(ThreadPoolConfig config) {
    ...
    // ScheduledThreadPoolExecutor的maxPoolSize没有被使用
    config.setMaxPoolSize(0);   // 修改了调用方的可变 config
```
- **现状**: 工具方法直接改写传入的 `ThreadPoolConfig`（该对象可从外部长期持有，如 `DefaultScheduledExecutor.getConfig()` 暴露给监控/刷新逻辑）。副作用: 后续 `refreshConfig()` 走 `updateThreadPool` 时看到的 maxPoolSize 已被篡改为 0，且同一 config 复用创建第二个执行器时行为不同。
- **风险**: 配置对象共享场景下的隐性状态污染，排查困难。
- **建议**: 不改入参，或先 clone。
- **误报排除**: 已确认 `DefaultScheduledExecutor.refreshConfig` 会继续使用同一 config 实例。

### [P3] ClassHelper/HashHelper 存在 bare RuntimeException，违背错误处理两档策略

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/ClassHelper.java:679-681`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/HashHelper.java:32-34`
- **维度**: D4 + D7（平台规范）
- **证据**:
```java
// ClassHelper.findContainingJar (copy from Kylin)
} catch (IOException var6) {
    throw new RuntimeException(var6);
}
// HashHelper 静态初始化 ThreadLocal
throw new RuntimeException("unexpected exception creating MessageDigest instance for [" + digest + "]", e);
```
- **现状**: 模块内仅有的 4 处 `new RuntimeException` 中的 2 处可触达处（另两处为注释/ByteHelper 反射兜底）。按平台规范，框架核心公共 API 应使用 `NopException + ErrorCode + .param(...)`，模块内部也禁止 bare `RuntimeException`。
- **风险**: 低（前者仅 IO 异常路径，后者仅 JDK 缺算法这种不可能场景），主要是规范一致性问题。
- **建议**: 换成 `NopException.adapt(e)` 或模块错误码。
- **误报排除**: 已全模块 grep `new RuntimeException`，仅此 4 处。

### [P3] 可变全局静态字段缺少 volatile（IoHelper 序列化器、GlobalMeterRegistry、GlobalExecutors SYNC_EXECUTOR）

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/IoHelper.java:48-49`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/metrics/GlobalMeterRegistry.java:18`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java:38`
- **维度**: D3（可见性）
- **证据**:
```java
// IoHelper
static IStreamSerializer s_streamSerializer = JavaSerializer.INSTANCE;
static IByteArraySerializer s_byteArraySerializer = JavaSerializer.INSTANCE;
// GlobalMeterRegistry
static MeterRegistry s_instance = new SimpleMeterRegistry();
// ExecutorHelper
static Executor SYNC_EXECUTOR = task -> task.run();
```
- **现状**: 这些静态字段都提供运行期替换方法（`registerStreamSerializer`、`registerInstance`），但字段未声明 volatile。在 JIT 优化下，其他线程可能长期读到旧引用（尤其 `SYNC_EXECUTOR` 与 lambda 一起被内联缓存时）。
- **风险**: 启动后动态替换序列化器/metrics 注册表的场景下，部分线程可能继续使用旧实例。实际发生概率低（替换通常发生在启动早期）。
- **建议**: 加 `volatile`（零成本修复）。
- **误报排除**: 已确认这些字段都有运行期写路径，非"仅初始化一次"的 final 化场景。

### [P3] 契约漂移杂项：MutableInt javadoc 声称原子、HighWatermarkSemaphore 统计计数器从不累加、MapCache 默认非线程安全但暴露 async 方法

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/mutable/MutableInt.java:207-215`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/semaphore/HighWatermarkSemaphore.java:37-71、121-130`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/MapCache.java:29-36、53-67`
- **维度**: D8（契约漂移）+ D3
- **证据**:
```java
// MutableInt（javadoc 摘自本文件）
/**
 * Atomically adds the given value to the current value.
 */
public int addAndGet(int delta) {
    return value += delta;          // 非原子，字段也无 volatile
}
```
```java
// HighWatermarkSemaphore.tryAcquire/release 中从不更新 acquireSuccessCount/acquireFailCount，
// getAcquireSuccessCount()/getAcquireFailCount() 永远返回 0
```
```java
// MapCache 默认 threadSafe=false（HashMap），却提供 getAsync/futureCall 等跨线程执行的异步方法
public MapCache() { this("default", false); }
```
- **现状**: (1) MutableInt 沿袭 commons-lang 文档，但调用方若按"原子"语义并发使用会丢更新；(2) HighWatermarkSemaphore 实现了 `resetStats` 却没有任何累加点，ISemaphore 统计契约为空实现（对比 DefaultRateLimiter 至少有累加只是读错字段）；(3) MapCache 非 threadSafe 实例上调用 `*Async` 方法会把 HashMap 暴露给其他线程（futureCall 在公共线程池执行）。
- **风险**: 低，均属误用诱因类问题。
- **建议**: 修正 javadoc 或提供真正原子版本；信号量统计补累加；MapCache async 方法对非线程安全实例直接同步执行或抛异常。
- **误报排除**: 已通读三个类确认无其他累加/同步点。

### [P3] StringTrie.find 传入空字符串抛 StringIndexOutOfBoundsException

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/text/StringTrie.java:113-122`
- **维度**: D1（边界条件）
- **证据**:
```java
protected TrieNode<T> findNode(String str, boolean onlyPrefix) {
    return findInList(roots, str, 0, onlyPrefix);
}

TrieNode<T> findInList(List<TrieNode<T>> list, String str, int startPos, boolean onlyPrefix) {
    char c = str.charAt(startPos);   // str 为空串时直接越界
```
- **现状**: `find("")` / `findWithPrefix("")` 未做空串短路，直接 `charAt(0)` 抛运行时异常，而同类查找 API 的惯例是返回 null。`add("")` 同样会在 `addToList` 的 `charAt(startPos)` 处越界。
- **风险**: 调用方以用户输入作为 key 查询时（关键字 Trie 常用于敏感词/标记匹配），空输入引发异常而非未命中。
- **建议**: 入口处 `if (str.isEmpty()) return null;`。
- **误报排除**: 已通读全类确认无空串防护。

## 补充说明（核实过、不构成缺陷的点）

- `TaskQueue`/`StandardThreadPoolExecutor`（Tomcat 拷贝）的 submittedCount、contextStopping 逻辑与上游一致，未发现移植偏差。
- `SequentialTaskExecutor` 的 hasTask/status 双检调度（volatile 写后读原子量）经推演无丢任务竞态。
- `DedupBlockingQueue` 的 map-then-offer / take 时 map.remove 语义自洽；`OverflowBlockingQueue` 的 DROP_ELDEST 循环正确。
- `Cancellable`/`CallbackSet` 的回调并发语义正确（fetch-and-null 模式下 append/cancel 交错不会丢回调）。
- 两处"空 catch"（`DateHelper.safeParseDate` 多 pattern 试解析、`Seq.consumeTillStop` 捕获 StopException）均为设计意图，非吞噬。
- `FileRandomOutputStream.lock()` 在 `OverlappingFileLockException` 时无限 sleep 重试，理论上有活锁风险，但该类仓库内无调用方且行为继承自 esProc 拷贝源，未单列。
- D7 平台约定: 模块内唯一的注入注解 `DefaultServerAddrFinder.setAddr` 使用 setter 注入 + `@InjectValue`，合规；nop-commons 无 beans.xml、无 `@Inject` 字段注入、无 `_` 前缀产物文件，未见违背。
