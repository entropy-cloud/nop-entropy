# 2026-10-20 无界 Docker 探测挂死构建（kafka/pulsar/ai-toolkit/stream 八处同族）

## Problem

- WI18 收口全量构建(`install -T 1C -rf :nop-stream-connector-kafka`)在 200 个模块中的 4 个上无限期停止：`nop-ai-toolkit`、`nop-job-local`、`nop-message-kafka`、`nop-message-pulsar`；Maven 进程 0% CPU 持续 5.5 小时。
- 串行单跑(`-pl` 四模块、无 `-T`)同样停在 `PulsarBrokerE2ETest`,排除 `-T 1C` 并行调度假设。
- 串行跑到 `nop-ai-toolkit` 的 `BashSandboxTest` 再次停住——同一个环境触发条件命中第二种写法。
- 最小可见症状:构建日志最后一行停在某个模块的 surefire 输出后,无 FAILED、无 exit。

## Diagnostic Method

- 先查 Maven 主进程状态:`ps` 显示 0.0% CPU、状态 S(sleeping)——排除"还在编译/测试"。
- 对日志做 started-vs-installed 模块差集:200 started / 197 installed,未完成模块收敛为 4 个。
- 查 surefire fork JVM:3 个 java 子进程同样 0% CPU;对 pulsar 的 fork JVM `jstack`,main 线程栈:`PulsarBrokerE2ETest.<clinit>` → `detectDocker` → `DockerClientFactory.isDockerAvailable` → `SocketChannelImpl.blockingRead`(无超时的 NIO 阻塞读)。
- 独立佐证:命令行 `docker version` 自身也挂死(>8s 不返回,alarm 杀掉后才退出);`~/.docker/run/docker.sock` 是 10 月 11 日的陈旧 socket 文件——Docker Desktop 半挂死(接受连接、不响应)。
- ai-toolkit 二次停住后对 `BashSandboxTest` 的 fork JVM `jstack`:栈停在 `isDockerAvailable` 的 `in.read(buf)` 排空循环——发现同一根因的第二种缺陷写法(drain-before-wait)。
- 仓库级同族扫描:`grep -rln DockerClientFactory` 找出另外 6 个测试类(nop-stream-runtime ×2、nop-stream-connector-debezium ×4)使用逐字相同的无界 `detectDocker()`。
- 被排除的假设:`-T 1C` 并行抖动(串行复现,否定);测试逻辑死锁(栈在类初始化器/assumeTrue 探测内,测试逻辑未开始,否定)。

## Root Cause

- **变体 A(testcontainers 探测)**:8 个测试类把 `DockerClientFactory.instance().isDockerAvailable()` 直接放在静态初始化器(`DOCKER_AVAILABLE = detectDocker()`),该探测对 docker socket 无读超时;socket 半挂死时**永远不返回**,`catch (Throwable)` 挡不住"无异常的无限阻塞"。
- **变体 B(drain-before-wait)**:`BashSandboxTest.isDockerAvailable/isImageAvailable` 先 `while (in.read(buf) != -1)` 排空子进程管道、后 `waitFor(10s)`——daemon 半挂死时 `docker version` 子进程永不退出、管道永不关,`read` 阻塞在 `waitFor` 之前,超时形同虚设。
- 类初始化器/assumeTrue 探测阻塞 → surefire fork JVM 挂 → Maven 等 fork 退出 → reactor 挂死;`-T 1C` 下阻塞 JVM 还拖累同批并行模块(如 `ToolManagerImplParallelTest` 停摆,单跑 1.15s 全绿,属连带症状非独立缺陷)。
- 环境触发条件:本机 Docker Desktop 未运行但留下陈旧 socket 文件(连接建立、响应不来)。之前构建能过是因为当时 Docker 正常(快速成功或快速报错)。

## Fix

- **变体 A(8 个测试类)**:`detectDocker()` 统一改为有界探测——提交到单线程守护线程池,`Future.get(15, TimeUnit.SECONDS)` 超时按"docker 不可用"处理;`finally` 中 `shutdownNow()` 以中断打断 NIO 阻塞读(`SocketChannelImpl` 可中断)。
- **变体 B(2 个方法)**:改 wait-first-then-drain——先 `waitFor(10s)`,超时 `destroyForcibly()` 并返回不可用;进程退出后管道已关,排空循环必然到达 EOF。
- 两种修复后都走既有 `assumeTrue(DOCKER_AVAILABLE, ...)` 门——测试**可见 SKIP**(设计语义不变),不再挂死构建。

## Tests

- 无新增确定性回归测试:超时路径需要模拟"接受连接但不响应"的 unix socket/子进程,testcontainers `DockerClientFactory` 为静态门面,确定性 mock 成本过高(评估后裁定不做)。
- 保护来自修复本身:所有探测被硬性限定(15s/10s);回归症状即"构建再次无限挂死",可由任何全量构建暴露。

## Affected Files

变体 A(有界 detectDocker):

- `nop-message/nop-message-kafka/src/test/java/io/nop/message/kafka/KafkaBrokerE2ETest.java`
- `nop-message/nop-message-pulsar/src/test/java/io/nop/message/pulsar/PulsarBrokerE2ETest.java`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/storage/TestS3CheckpointStorageMinioE2E.java`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/connector/TestKafkaConnectorExactlyOnceE2E.java`
- `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumCrossDbSyncPipeline.java`
- `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumRealMysqlParallel.java`
- `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumRealMysqlCdc.java`
- `nop-stream/nop-stream-connector-debezium/src/test/java/io/nop/stream/connector/debezium/TestDebeziumRealPostgresCdc.java`

变体 B(wait-first-then-drain):

- `nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/sandbox/BashSandboxTest.java`(isDockerAvailable + isImageAvailable)

## Notes For Future Refactors

- 任何放入测试类 static 初始化器(或 `@BeforeAll`)的外部探测必须有界(超时/可中断);`catch (Throwable)` 只兜异常,兜不住无限阻塞。
- 进程输出排空必须在 `waitFor(timeout)` **之后**(或并行),先排空等于把超时权交给对端是否关管道。
- 不要把"串行跑绿"当作 `-T 1C` 挂死的反证——先看 fork JVM 线程栈再下结论;并行挂死里可能只有一个是根因,其余是连带停摆。
- 新增 testcontainers 用例时复用有界 `detectDocker()` 模式,勿直接调 `isDockerAvailable()`。
