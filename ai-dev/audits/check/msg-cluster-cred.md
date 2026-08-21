# msg-cluster-cred 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-message + nop-cluster + nop-credential
- 文件数: 约 199（src/main/java）
- 覆盖范围声明:
  - 全模块 grep 扫描: 空 catch、`new RuntimeException`、`printStackTrace`、`Cipher/SecretKey/AES/ECB`、password/secret/token 日志、`synchronized`、TODO/FIXME（三模块 src/main/java 全量，均排除 target/ 与 `_` 前缀生成文件）。
  - 全文深读:
    - nop-message: LocalMessageService、ReflectionMessageSubscriptionRegistrar、KafkaMessageService、KafkaConsumeTask、KafkaHelper、PulsarMessageService、PulsarConsumeTask、PulsarHelper、DebeziumMessageSource、DebeziumEngineWrapper、NopStreamOffsetBackingStore、DebeziumEngineConfig（12 文件）。
    - nop-cluster: elector 包 2、chooser 包 3 + filter 3（Healthy/Zone/Route）、lb/impl 6（LeastActive/RoundRobin/ConsistentHash/Dynamic/WeightedRandom/MasterFirst + ServiceLoadBalanceAdapter）、naming 4（CachingNamingService/AutoRegistration/PartitionResolver/PartitionAssignHelper）、discovery 2（ServiceInstance/FilteredDiscoveryClient）、assigner 1、nacos 3（NacosNamingService/ServiceInfo/NacosConfigService）、sentinel 1、admin 3、rpc-cluster 2（ClusterRpcClient/BroadcastRpcClient）、hazelcast-cache 1，以及手写装配文件 `load-balance-defaults.beans.xml`、`rpc-cluster-defaults.beans.xml` 与 sys-dao 选主 bean 声明 `app-dao.beans.xml`（约 34 处）。
    - nop-credential: DefaultCredentialKeyProvider、CredentialCipher、VaultCredentialKeyProvider、CredentialProviderImpl、NopCredentialBizModel、CredentialOwnership、OAuthFlowService、OAuthTokenClient、NopCredentialOauthStateStore、CredentialMigrationSupportImpl、DefaultCredentialTypeRegistry、CredentialConfigs、NopCredentialAuthBizModel（前 120 行），以及依赖侧 `AESTextCipher`（验证加密强度: AES-GCM + PBKDF2WithHmacSHA256/65536/256bit + 每密文随机 IV，v1 格式强度合格）。
  - 未逐行深读（低风险，仅扫描）: message 的 config/model/errors DTO（Kafka/Pulsar/Debezium 各 config 类、MessageModel 等）、DebeziumEventConverter/ChangeEvent*；cluster 的 rpc-cluster 其余 5 文件（ProxyFactoryBean/ServiceInvoker/HttpRpc*）、hazelcast-core HazelcastProvider、resources/* 与 MemorySize 等 DTO、filter 剩余 2 个（Tag/Specific）；credential 的 4 个 dao entity、OauthState/Usage BizModel、api 模块 DTO。
  - 跨模块追踪（用于误报排除）: nop-ioc 的 `ConfigExpressionProcessor`/`BeanDefinitionBuilder`/`DefaultBeanClassIntrospection`/`SpringBeanSupport`（@InjectValue 解析链）、nop-api-core 的 `NopException.getMessage()`。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 8 |
| P2 | 7 |
| P3 | 4 |

## 发现列表

### [P1] 选主配置注解 `@cfg;` 笔误——启用选主 bean 时 IoC 容器构建失败

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/elector/AbstractLeaderElector.java:72`
- **维度**: D7 / D1
- **证据**:
```java
@InjectValue("@cfg;nop.cluster.leader.lease-safe-gap|4000")
public void setLeaseSafeGap(int leaseSafeGap) {
    this.leaseSafeGap = leaseSafeGap;
}
```
- **现状**: 全仓唯一一处 `@cfg;`（分号）而非 `@cfg:`（冒号）。NopIoC 解析链: `DefaultBeanClassIntrospection.getValueInject` → `BeanDefinitionBuilder.buildInjectResolver` → `ConfigExpressionProcessor.process`——`expr.charAt(0)=='@'` 且不匹配 `@bean:`/`@cfg:`/`@rcfg:` 前缀时，`parsePrefixExpr` 抛 `ERR_IOC_INVALID_BIND_EXPR`（ConfigExpressionProcessor.java:137-155）。`nop-sys-dao` 的 `app-dao.beans.xml:26` 声明的 `nopSysDaoLeaderElector` 未显式配置该属性（bean 缺省 autowire=byType，注解属性必然走该链）。
- **风险**: 一旦按文档启用 `nop.cluster.leader-elector.sys-dao-elector.enabled`，容器构建直接失败（应用无法启动）；且 `leaseSafeGap` 参与租约安全边界判定（SysDaoLeaderElector.java:96、JdbcLeaderElector.java:208 均用 `getLeaseSafeGap()`），该配置项永不生效。
- **建议**: 改为 `@cfg:nop.cluster.leader.lease-safe-gap|4000`；补一条启用 sys-dao 选主 bean 的 IoC 启动回归测试。
- **误报排除**: 已核实 `IocConstants.PREFIX_CFG = "@cfg:"`（IocConstants.java:25）、`containsSpringExpr`/`containsBindExpr` 对该串均不命中（无 `${`/`@{`），异常路径确认可达；排除"可能被 beans.xml 显式属性覆盖"——sys-dao 声明处无该属性。

### [P1] LeastActiveLoadBalance 用未取模的原始下标访问列表——随机起点回绕时越界

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/LeastActiveLoadBalance.java:64-107`
- **维度**: D1
- **证据**:
```java
while (currentCursor < max) {
    int i = startIndex + currentCursor;          // i 可 >= n
    T item = items.get(i % n);                   // 取值做了取模
    ...
    leastIndexs[0] = i;                          // 存的是原始 i（未取模）
    ...
    leastIndexs[leastCount++] = i;
...
return items.get(leastIndexs[0]);                // 直接用原始 i 访问 → 越界
```
- **现状**: `startIndex = random.nextInt(n)`，当 `startIndex > 0` 时扫描窗口必然越过 n 回绕（max = min(n, 10)），落在回绕区的候选其存储下标 `i ∈ [n, 2n-2)`；line 90/99/107 用原始 `i` 调 `items.get(...)` 抛 `IndexOutOfBoundsException`。配套的 `nopServiceLoadBalanceAdapter.getActiveCount()` 恒返回 0，全体候选活跃数相同，必然走 line 107 的"等权随机"分支。
- **风险**: 选用 leastActive 策略（bean `nopLoadBalance_leastActive` 已在 `load-balance-defaults.beans.xml:13` 注册，可经 DynamicLoadBalance 选择）时，单次请求以约 startIndex/n 概率直接抛异常，RPC 调用随机失败。附带: line 81 `i > 0` 用原始 i 判断，回绕时权重同值检测口径也不正确。
- **建议**: 循环内先 `int i = (startIndex + currentCursor) % n;` 统一取模（同时修正 line 81 的 `i > 0` 语义）。
- **误报排除**: Dubbo 原版实现是先取模再存储；本类"取值取模、存值不取模"的不一致是重构引入。仓库内无该类的测试覆盖（TestLoadBalance 不含 LeastActive），也无其他调用方能掩盖该路径。

### [P1] SentinelFlowControlRunner.runAsync 成功路径不退出 Context——ThreadLocal 上下文泄漏

- **文件**: `nop-cluster/nop-cluster-sentinel/src/main/java/io/nop/cluster/sentinel/SentinelFlowControlRunner.java:44-96`
- **维度**: D3 / D2
- **证据**:
```java
ContextUtil.enter(entry.getContextName(), entry.getOrigin());
...
boolean proceedCalled = false;
try {
    serviceEntry = SphU.asyncEntry(...);
    CompletionStage<T> ret = task.get();
    proceedCalled = true;
    return ret.whenComplete((r, e) -> {
        ...
        serviceEntryArg.exit(1, args);   // 只退出了 Entry
    });
} finally {
    if (!proceedCalled) {               // 成功路径为 true → 不执行
        if (serviceEntry != null) serviceEntry.exit(1, args);
        ContextUtil.exit();             // ← 成功路径永不执行
    }
}
```
- **现状**: `runAsync` 在调用线程 `ContextUtil.enter` 后，仅当 `task.get()` 同步抛错（proceedCalled=false）才在 finally 里 `ContextUtil.exit()`；正常返回 future 的路径中，whenComplete 只退出 Entry，从不退出 Context。对比同文件同步版 `run()`（line 166-173）finally 无条件 exit。
- **风险**: 开启 Sentinel 流控的每个异步调用都在发起线程残留一个 Sentinel Context。`ContextUtil.enter` 对已存在 Context 是复用语义，后续无关请求将复用陈旧 contextName/origin——origin 相关授权规则误判、上下文维度统计污染；线程池线程长期持有引用。
- **建议**: 在发起线程注册完 whenComplete 后（finally 中 proceedCalled 分支内）调用 `ContextUtil.exit()`，Entry 的退出保持在 whenComplete。
- **误报排除**: 已通读全文确认成功路径无任何 `ContextUtil.exit()` 调用点；Sentinel 官方 async 示例要求发起侧退出 Context、异步侧退出 Entry，此处仅做了一半。

### [P1] NacosNamingService.copyFromInstance 假设 serviceName 含 `@@`——无分隔符时 substring(0,-1) 崩溃

- **文件**: `nop-cluster/nop-cluster-nacos/src/main/java/io/nop/cluster/nacos/NacosNamingService.java:290-292`
- **维度**: D1
- **证据**:
```java
int pos = inst.getServiceName().indexOf("@@");
ret.setGroupName(inst.getServiceName().substring(0, pos));
ret.setServiceName(inst.getServiceName().substring(pos + 2));
```
- **现状**: 未对 `pos == -1`（serviceName 为纯服务名，无 `group@@` 前缀）做防护，`substring(0, -1)` 抛 `StringIndexOutOfBoundsException`；serviceName 为 null 时先 NPE。同类 `ServiceInstance.getNormalizedServiceName()`（ServiceInstance.java:251-256）对同一输入做了 `pos > 0` 防护，证明该形态输入在作者认知内是合法的。
- **风险**: 依赖 Nacos 客户端版本/服务端返回形态：只要 `getAllInstances`/事件推送返回的 Instance.serviceName 不带 `@@` 组合前缀，`getInstances` 与实例变更监听整体崩溃（服务发现不可用），所有依赖该 discovery 的 RPC 选路失败。
- **建议**: 与 `getNormalizedServiceName` 同口径: `pos > 0` 时拆分，否则 groupName 置配置的 groupName、serviceName 原样保留。
- **误报排除**: 已核对 `copyFromInstance` 是所有 Nacos → ServiceInstance 转换的唯一入口且无前置校验；防御不一致（同输入形态一处防护一处不防护）排除了"约定必然带 @@"的解释。

### [P1] KafkaMessageService 订阅 suspend/resume 在调用线程直接操作 KafkaConsumer——违反单线程约束

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:364-381`
- **维度**: D3
- **证据**:
```java
@Override
public void suspend() {
    suspended = true;
    try {
        consumer.pause(consumer.assignment());   // 调用线程
    } catch (Exception e) {
        LOG.error("nop.message.kafka.suspend-failed", e);
    }
}
```
- **现状**: `KafkaConsumer` 非线程安全（唯一可跨线程方法是 wakeup）。poll 循环运行在专属单线程 executor 上（KafkaConsumeTask），而 `suspend()`/`resume()` 从调用方线程直接调 `consumer.pause/resume`。且 pause 失败被 catch 吞掉后 `suspended=true` 仍置位（内部状态与消费者实际状态漂移）。
- **风险**: 与 poll/commitSync/seek 并发访问触发 `ConcurrentModificationException("KafkaConsumer is not safe for multi-threaded access")`，suspend/resume 功能随机失败；失败还被吞成日志，状态不一致。对比 Pulsar 侧（PulsarMessageSubscription，Pulsar Consumer 线程安全）无此问题。
- **建议**: 以 volatile 标志位 + poll 循环内检查的方式转发 pause/resume 到消费线程执行。
- **误报排除**: 已核对 KafkaConsumeTask 的 poll 循环持续运行（active=true，pollTimeout 1s），并发窗口现实存在；`IMessageSubscription.suspend/resume` 是接口契约方法，可达。

### [P1] KafkaMessageSubscription.cancel() 先 close 后等线程退出——close 与消费线程竞态导致 consumer 泄漏

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaMessageService.java:330-351`
- **维度**: D3 / D2
- **证据**:
```java
public void cancel() {
    cancelled = true;
    task.stop();                 // active=false + wakeup（异步生效）
    try {
        consumer.close();        // ← 立即 close，消费线程可能仍在 onMessage/seek/commitSync 中
    } catch (Exception e) {
        LOG.error("nop.message.kafka.cancel-close-consumer-failed", e);
    }
    executor.shutdown();
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) { ... }  // 等待发生在 close 之后
```
- **现状**: close 在 `awaitTermination` 之前执行。若消费线程正处于 `consumer.seek/commitSync`（如消息处理失败回退、批量提交）中，`close()` 的内部 acquire 检测到多线程访问抛 `ConcurrentModificationException`，被外层 catch 吞掉——consumer 实际未关闭。
- **风险**: 未关闭的 KafkaConsumer 持有 broker 连接、协调者心跳与网络线程，造成连接/线程泄漏；消费线程随后还会在已 close/未 close 的 consumer 上抛异常进入错误日志。正确顺序应为 shutdown + awaitTermination → 再 close。
- **建议**: 调整顺序: `task.stop(); executor.shutdown(); awaitTermination(...); consumer.close();`。
- **误报排除**: KafkaConsumer.close 的非重入/多线程保护是 Kafka 客户端既定行为；Pulsar 侧同构代码无此问题（线程安全），差异佐证这是 Kafka 特有顺序缺陷。

### [P1] KafkaConsumeTask.seekToPosition 在分区分配前执行——SeekMode/seekToTime 全部静默无效

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:106-166`（触发链 `KafkaMessageService.java:273-288`）
- **维度**: D1 / D8
- **证据**:
```java
// KafkaMessageService.doSubscribe:
kConsumer.subscribe(topics);
...
task.start();   // executor 内先执行 seekToPosition，再进入 poll 循环

// KafkaConsumeTask.seekToPosition:
Collection<TopicPartition> partitions = consumer.assignment();  // ← subscribe 后未 poll，恒为空集
consumer.seekToBeginning(partitions);   // 空集 no-op
...
for (TopicPartition partition : consumer.assignment()) { timestamps.put(...); }  // 空集 no-op
```
- **现状**: Kafka 的分区分配发生在首次 poll（组协议）或 rebalance 回调时。`subscribe()` 之后、任何 poll 之前执行 `assignment()` 恒返回空集，所有 seek（INIT_SEEK_TO_BEGIN/INIT_SEEK_TO_END/ALWAYS_SEEK_TO_END/seekByTime）均作用于空集合，整个 Seek 功能无效。类 javadoc 却声明 "Honors SeekMode (not a stub)... executed once before the poll loop starts"。
- **风险**: 使用方设置 `SeekMode.INIT_SEEK_TO_END` 期望跳过积压，实际按默认 offset（常见 earliest）从头消费——全量重放，数据语义错误且伴随流量冲击；且完全静默无日志。
- **建议**: 在 `ConsumerRebalanceListener.onPartitionsAssigned` 回调中执行 seek，或首次 poll 返回分区后再 seek。
- **误报排除**: 已确认 `doSubscribe` 未注册任何 rebalance 监听器、start() 的 executor 任务先于一切 poll 执行 seekToPosition；空集 no-op 是 Kafka 客户端既定行为。

### [P1] 主密钥 passphrase 整串写入异常 param——NopException.getMessage 全量输出到日志

- **文件**: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/crypto/DefaultCredentialKeyProvider.java:107-135`、`nop-credential/nop-credential-kms-vault/src/main/java/io/nop/credential/kms/vault/VaultCredentialKeyProvider.java:250-274`
- **维度**: D5
- **证据**:
```java
// DefaultCredentialKeyProvider.init()
throw new NopException(CredentialErrors.ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID)
        .param(CredentialErrors.ARG_MASTER_KEY_ENTRY, entry);   // entry = "keyId:passphrase" 完整明文

// VaultCredentialKeyProvider 迁移残余校验
throw new NopException(VaultCredentialErrors.ERR_CREDENTIAL_VAULT_MIGRATION_KEY_INVALID)
        .param(VaultCredentialErrors.ARG_MIGRATION_KEY, entry); // 同样含 passphrase
```
`NopException.getMessage()`（nop-api-core NopException.java:325-331）无条件拼接 `params=` + 全部参数值。
- **现状**: 任何 master-keys/migration-keys 条目校验失败（格式错、缺冒号、空白 passphrase、keyId 重复、keyId 冲突等）时，启动失败异常的 message 携带完整主密钥口令。同模块 `CredentialCipher.truncateCiphertext`（CredentialCipher.java:59-73）已建立"密文只进 16 字符前缀"的纪律，此处不一致。
- **风险**: 有日志读取权限者可从启动失败日志获取真实主密钥 passphrase——该 keyId 名下所有 `cv1:` 凭证密文可被离线解密（跨部署复用同一密钥时影响更大）。触发条件（配置错误）是运维现实中高频事件。
- **建议**: param 只放 keyId（`entry.substring(0, colonIdx)`）+ 长度/原因；与 CredentialCipher 的截断纪律对齐。
- **误报排除**: 已核实 `NopException.getMessage()` 的 params 全量拼接行为（无脱敏钩子）；触发路径为 @PostConstruct 校验异常，容器启动失败必然打印异常消息与堆栈。

### [P2] PulsarConsumeTask.seekToPosition 为空 TODO——订阅 seek 选项静默忽略

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:70-73`
- **维度**: D8
- **证据**:
```java
// TODO: implement seekToPosition based on SeekMode/seekToMessage/seekToTime from MessageSubscribeOptions
private void seekToPosition() {

}
```
- **现状**: Pulsar 后端对 `MessageSubscribeOptions` 的 SeekMode/seekToMessage/seekToTime 完全不处理，也不告警。`start()` 仍调用这个空方法。
- **风险**: 使用方通过订阅选项指定起始位置在 Pulsar 后端静默失效（对比 Kafka 后端会告警"unsupported"的设计意图，见 KafkaErrors.ERR_KAFKA_SEEK_NOT_SUPPORTED 的 fail-loud 策略），消费起点不可控。
- **建议**: 映射到 Pulsar `Consumer.seek/seekAsync`（Timestamp/MessageId），或至少按平台"no silent skip"约定打 WARN/抛明确错误。
- **误报排除**: KafkaConsumeTask javadoc 明确指出该方法是 TODO stub；代码为空方法体，无任何条件分支。

### [P2] Pulsar 后端对非 String 载荷直接 value() —— STRING schema 下序列化崩溃（与 Kafka 行为不对称）

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarHelper.java:66-72,99`（默认 schema 见 `PulsarMessageService.java:85,139-144`）
- **维度**: D8 / D1
- **证据**:
```java
public static void buildPulsarMessage(TypedMessageBuilder builder, Object message, MessageSendOptions options) {
    if (message instanceof ApiMessage) {
        _buildPulsarMessage(builder, (ApiMessage) message);
    } else {
        builder.value(message);        // 未做类型适配
    }
...
builder.value(message.getData());      // ApiMessage.data 非 String 时同样直塞
```
- **现状**: 默认 producer 为 `Schema.STRING`。非 ApiMessage 的 POJO/Map，或 ApiMessage.data 为 Map 等对象时，STRING schema 的 encode 内部做 `(String)` 强转 → `ClassCastException`，且发生在异步发送路径。Kafka 后端对应路径 `KafkaMessageService.toWireValue`（KafkaMessageService.java:224-233）对非 String data JSON 编码，两后端同一 `IMessageService` 契约行为不一致。
- **风险**: 同一段发送代码在 Kafka 后端正常、Pulsar 后端运行期 CCE；错误发生在 Pulsar 客户端内部，堆栈不直观。
- **建议**: 与 Kafka 对齐——非 String 载荷 JSON 编码（或按 topicSchemas 校验并在发送前抛出带错误码的明确异常）。
- **误报排除**: 已核对 `getProducer` 的默认分支返回 STRING schema 的 defaultProducer，且 `topicSchemas` 未配置该 topic 时必然走默认。

### [P2] ConsistentHashLoadBalance 每次请求重建整个一致性哈希环

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/ConsistentHashLoadBalance.java:32-49`
- **维度**: D6
- **证据**:
```java
public T choose(List<T> items, R request) {
    int hash = hashFunc.hashRequest(request);
    TreeMap<Integer, T> map = new TreeMap<>();
    for (i = 0; i < n; i++) {
        hashFunc.hashCandidate(item, replicaNumber, map);   // 默认 160 虚拟节点/实例
    }
    ...
}
```
- **现状**: 每次 `choose` 都重建 TreeMap 环（N 实例 × 160 副本的哈希与插入），无按实例列表的缓存。
- **风险**: 高 QPS RPC 场景 CPU 与 GC 放大（10 实例即每次请求 1600 次哈希+树插入）；候选列表通常在两次服务发现刷新之间不变，完全可以缓存。
- **建议**: 以 `List<T>` 标识（如 List.hashCode 或实例 id 集）为 key 缓存哈希环，列表变更时重建。
- **误报排除**: 已确认类中无任何缓存字段；`replicaNumber=160` 为默认值。

### [P2] WeightedRandomLoadBalance 全零权重时 nextInt(0) 抛异常

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/lb/impl/WeightedRandomLoadBalance.java:26-28`
- **维度**: D1
- **证据**:
```java
int[] weights = getWeights(items);
int ttlWeight = sum(weights);
int rnd = MathHelper.random().nextInt(ttlWeight);   // ttlWeight=0 → IllegalArgumentException(bound must be positive)
```
- **现状**: 未对 `ttlWeight <= 0` 防护。`nopLoadBalance_weightedRandom` 为注册 bean（load-balance-defaults.beans.xml:27），权重来自 `ServiceInstance.getWeight()`（Nacos 侧 `copyFromInstance` 直接取实例权重，Nacos 允许置 0 摘流量）。
- **风险**: 运维将全部实例权重置 0（整体摘除/维护场景）后，每次请求抛 IllegalArgumentException，选路全失败而非可预期的降级；同文件注释表明作者考虑过边界（`ttl > rnd`），但漏了入口。对比 LeastActiveLoadBalance 有 `totalWeight > 0` 防护。
- **建议**: `ttlWeight <= 0` 时退化为等权随机（与 LeastActive 一致）。
- **误报排除**: 已确认 `ServiceLoadBalanceAdapter.getWeight` 无下限钳制（原样返回），全零输入可达。

### [P2] DebeziumEngineWrapper start/stop 竞态——stop 早于异步启动完成时引擎泄漏且无法再停止

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:91-127`
- **维度**: D3 / D2
- **证据**:
```java
GlobalExecutors.globalWorker().execute(() -> {
    try {
        running.set(true);        // ← 异步置位
        engine.run();
    ...
});
...
public synchronized void stop() {
    if (!running.get() || stopped.getAndSet(true)) {   // start() 返回后、pool 任务执行前 running 仍为 false
        return;                                        //   → 直接返回，引擎随后照常启动且永不被 close
    }
```
- **现状**: 两个叠加缺陷: (1) `running` 在池任务内才置 true，紧随 `start()` 的 `stop()` 因 `!running` 直接返回，引擎随后启动并永久运行；(2) `stopped` 一旦置 true 不再复位，`start()` 不检查 `stopped`——重启后新引擎创建成功但任何后续 `stop()` 均被 `stopped.getAndSet(true)` 短路，新引擎泄漏。
- **风险**: 快速订阅-取消（或引擎重启）场景下 CDC 引擎与数据库连接泄漏；DebeziumMessageSource 层的 `started` 标志缓解了重启，但不覆盖 (1) 的窗口。
- **建议**: `start()` 中同步置 `running`（提交前）并在创建引擎时 `stopped.set(false)`；或 stop 分支对"已提交但未运行"的引擎也执行 close。
- **误报排除**: 已通读全文确认 `running` 仅在异步任务内置位、`stopped` 无复位点、`start()` 不检查 `stopped`。

### [P2] CachingNamingService 注销/更新实例不失效缓存——下线节点在 TTL 内仍被选路

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/CachingNamingService.java:20-43`
- **维度**: D1 / D6
- **证据**:
```java
@Override
public void unregisterInstance(ServiceInstance instance) {
    service.unregisterInstance(instance);      // 未 cache.remove/invalidate
}
@Override
public void updateInstance(ServiceInstance instance) {
    service.updateInstance(instance);          // 同样不失效
}
```
- **现状**: 只有 TTL 过期能清除已注销实例；`getInstancesAsync` 还有并发下重复回源的小竞态（get 判空后各自 fetch）。
- **风险**: 主动下线（发布前摘流）后，使用方在 cacheTimeout 内继续拿到已下线实例并发起失败请求；`updateInstance` 的权重变更 likewise 延迟生效。
- **建议**: unregister 时 `cache.remove(instance.getServiceName())`，update 时同样失效对应 key。
- **误报排除**: 已核对缓存仅按 serviceName 键控、无其他失效通道；构造参数 cacheTimeout 由调用方给定，TTL 未知但有限。

### [P2] nopServerChooser_roundRobin 实际装配的是 Random 负载均衡

- **文件**: `nop-cluster/nop-rpc-cluster/src/main/resources/_vfs/nop/rpc/beans/rpc-cluster-defaults.beans.xml:68-70`
- **维度**: D8 / D7
- **证据**:
```xml
<bean id="nopServerChooser_roundRobin" parent="nopServerChooser_base">
    <property name="loadBalance" ref="nopLoadBalance_random"/>   <!-- 应为 nopLoadBalance_roundRobin -->
</bean>
```
- **现状**: 复制粘贴错误——roundRobin chooser 引用了 random LB。`nopLoadBalance_roundRobin` bean 存在（同目录 load-balance-defaults.beans.xml:24）但未被任何 chooser 使用。
- **风险**: 下游应用引用 `nopServerChooser_roundRobin` 期望轮询（会话粘性/顺序语义）时实际得到随机均衡，语义静默漂移；仓内当前无引用方，属"对外选择点已损坏"。
- **建议**: ref 改为 `nopLoadBalance_roundRobin`。
- **误报排除**: 已全仓 grep 确认无其他地方纠正该装配；bean id 与 ref 的不对称仅此一处（leastActive/first/random 装配均正确）。

### [P3] ServiceInstance.compareTo 对 null instanceId 抛 NPE

- **文件**: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/ServiceInstance.java:162-165`
- **维度**: D1
- **证据**:
```java
@Override
public int compareTo(ServiceInstance o) {
    return instanceId.compareTo(o.getInstanceId());   // 任一侧 instanceId 为 null 即 NPE
}
```
- **现状**: 无 null 防护。排序路径现实存在: `NacosNamingService.updateInstances` 的 `sorted()`、`PartitionResolver.resolvePartitions` 的显式排序。
- **风险**: 任何发现端实现返回未填 instanceId 的实例时，实例列表更新/分区解析抛 NPE 中断；当前仓内 Nacos 转换路径总会填 id（但值可能为 null，来自 Nacos Instance 本身缺 id 的形态）。
- **建议**: null 安全比较（如 `StringHelper.compareNullLast`）或在反序列化入口强制校验。
- **误报排除**: 防护缺失确认；触达依赖具体发现端数据形态，故定 P3。

### [P3] nop-cluster-admin 为未接线骨架——shutdown mutation 空实现

- **文件**: `nop-cluster/nop-cluster-admin/src/main/java/io/nop/cluster/admin/biz/ClusterAdminBizModel.java:10-13`、`nop-cluster/nop-cluster-admin/src/main/java/io/nop/cluster/admin/commands/ShutdownCommand.java:3-5`
- **维度**: D8
- **证据**:
```java
@BizMutation
public void shutdown(IServiceContext ctx) {
}

public class ShutdownCommand {
}
```
- **现状**: `ClusterAdmin__shutdown` 声明为 GraphQL mutation 但方法体为空（调用成功却无任何效果）；ShutdownCommand 为空类；整个模块无 `_vfs` beans.xml 装配（@BizModel 不会自动注册）。
- **风险**: 当前因未装配不可达，属死代码骨架；若后续按注解接入，将暴露"成功返回但无动作"的契约陷阱。
- **建议**: 实现关闭语义（或先移除该 action），并补 beans.xml 装配或删除骨架模块。
- **误报排除**: 已列全模块文件确认无装配文件、无其他调用方。

### [P3] CredentialProviderImpl.countUsage 全量加载实体计数

- **文件**: `nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/CredentialProviderImpl.java:244-249`
- **维度**: D6
- **证据**:
```java
public long countUsage(String credentialId) {
    ...
    return dao.findAllByQuery(query).size();   // 加载全部 usage 行到内存再取 size
}
```
- **现状**: 应使用 `countByQuery`；同模块 `NopCredentialBizModel.prepareDeleteWithUsageCheck`（NopCredentialBizModel.java:801）已用 `usageDao.countByQuery`，口径不一致。
- **风险**: 引用记录多的凭证（大量 consumer 绑定）时无谓的实体物化开销。
- **建议**: 改为 `dao.countByQuery(query)`。
- **误报排除**: 实体行确实全部加载；对照同模块正确用法确认是遗漏而非风格选择。

### [P3] LocalMessageService 消费异常吞掉且消息体整体进日志

- **文件**: `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java:172-177`
- **维度**: D4
- **证据**:
```java
try {
    Object ret = FutureHelper.getResult(consumer.onMessage(topic, message, context));
    handleMessageResult(ret, topic, message, context);
} catch (Exception e) {
    LOG.error("nop.message.consumer-failed:topic={},message={}", topic, message, e);
}
```
- **现状**: 消费者异常仅记日志后继续（send() 无失败通道，调用方无法感知）；且 `message={}` 将完整消息体打入 error 日志。
- **风险**: 本地消息总线的失败语义与 Kafka（negativeAck 重投）/Pulsar（nack + 重投）后端不一致（D8 侧面）；消息体含敏感字段时随 error 日志落盘（D5 侧面）。`FutureHelper.getResult` 还会在同步 send 中阻塞等待消费者 future，行为耦合。
- **建议**: 至少将 message 体改为摘要（类名/长度/hash）；失败策略（继续/中断/传播）与后端实现对齐并在接口 javadoc 声明。
- **误报排除**: catch 范围与日志格式已核实；"继续广播其余订阅者"本身是合理设计，问题聚焦在无失败通道 + 全量载荷进日志。

## 补充说明（非缺陷的验证结论）

- **D5 加密强度**: 凭证加密主链路（CredentialCipher → AESTextCipher v1）为 AES-256-GCM + PBKDF2WithHmacSHA256(65536 迭代) + 每密文随机 12 字节 IV，无 ECB、无硬编码密钥（legacy MD5+静态 IV 路径仅显式 opt-out 时启用，且 cv1 解密强制 `v1:` 前缀拒绝 legacy 裸载荷，CredentialCipher.java:147-150）。Vault 通道 fail-closed、无本地降级，实现质量高。
- **D7 总体**: 三模块未发现空 catch、bare `new RuntimeException`、`printStackTrace`、`@Inject private` 字段注入；`UnsupportedOperationException` 用于凭证 BizModel 旁路收口属文档化决策（非 bare RuntimeException）。
- **审计边界**: `AbstractLeaderElector` 的具体租约实现（SysDaoLeaderElector/JdbcLeaderElector 的心跳与双主判定）位于 nop-sys/nop-stream 模块，不在本次范围；本报告仅覆盖 nop-cluster 内抽象层问题（发现 #1）。
