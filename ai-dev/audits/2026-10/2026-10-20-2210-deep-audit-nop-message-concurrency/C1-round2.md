# C1 深挖第 2 轮 — nop-message（WI 4.1）

- 审计维度：C1（共享可变状态与可见性）· 深挖第 2 轮（合并批 C1/C3/C5/C6/C8 之 C1 分卷）
- 日期：2026-10-20
- 盲审声明：本轮仅读取方法论 `ai-dev/skills/concurrency-resource-audit-prompt.md`、同目录第 1 轮五文件（C1/C3/C5/C6/C8）、`docs-for-ai/**`、源码与 `_tmp/cra-baseline-nop-message.txt`；未读 `ai-dev/audits/**` 其余内容、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`，未查 git log。

## 深挖范围与方法

按派发指令对第 1 轮 11 条（C1-001…C1-011，集中 FAILURE_LISTENERS/REGISTRY/destroy 守卫）之外的五个定向面逐文件复核（全部 31 文件 6,140 行已在本轮通读）：

| 定向面 | 复核对象 | 结论 |
|---|---|---|
| Kafka/Pulsar 配置类可变态（Config 对象跨线程共享） | `KafkaClientConfig`/`KafkaProducerConfig`/`KafkaConsumerConfig`/`PulsarClientConfig`/`PulsarProducerConfig`/`PulsarConsumerConfig`/`DebeziumConfig` + 全部读取点（`buildProducerProps:167`、`buildConsumerProps:201`、`doSubscribe:426`、`buildProperties`） | **立案 C1-012**（活 Map 外泄 + 装配后可变，无 in-repo 运行期写者 → P2 理论）；标量字段配置面按装配 happens-before 豁免 |
| KafkaHelper/PulsarHelper 静态工具状态 | `KafkaHelper.java:36-163`、`PulsarHelper.java:22-135` 全文 | **零静态可变状态**（均为 static final 常量 + 纯函数），examined-empty |
| ChannelBacklogProbe 观测状态 | `ChannelBacklogProbe.java:29-35`（纯接口）+ 模块内 0 实现 0 调用 | 无观测状态可盘点，examined-empty（生命周期/语义面移交 C6/C8 卷声明） |
| MessageConsumeFailureBridge 桥接状态 | `MessageConsumeFailureBridge.java:38-115` 全文 | 5 个配置字段为装配期写入（IoC HB 覆盖），`submit` 无状态、无共享可变账本；**examined-empty for C1**（其异步传播面归 C3 卷，无超时缺口登记 CL-025） |
| ReflectionMessageSubscriptionRegistrar 反射注册表 | `ReflectionMessageSubscriptionRegistrar.java:26-152` 全文 | `subscriptions` 的 register/destroy 均为容器生命周期线程，第 1 轮已按启动期降档接受；本轮新增复核失败回滚路径（:54-66 置 null + 逐个 cancel）无新竞态面，examined-empty |

其余全量复扫：static 非 final 字段、static final 可变集合、ThreadLocal（4 根 0 处不变）、CHM 复合操作、volatile 完备性——均未发现第 1 轮清单表之外的载体。Pulsar `subscriptions` CLQ 的 add-after-clear 残余窗口已在第 1 轮 C1-006/C1-007 失效面枚举中登记为同族兄弟，本轮不重复编号。

## 新增发现

### [C1-012] 配置 bean 的集合字段以活引用外泄且装配后仍可变——subscribe/启动线程迭代非线程安全 Map，写者缺位使缺陷停留在理论面

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaClientConfig.java:29,47-53`；读取点 `KafkaMessageService.java:167-190`（buildProducerProps）、`:201-233`（buildConsumerProps）；`nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumConfig.java:152,348-362`；读取点 `engine/DebeziumEngineConfig.java:128-175`（前缀拒绝检查 + extraProperties 迭代合并）
- **证据片段**:
  ```java
  // KafkaClientConfig.java:29,47-49
  private Map<String, String> extraProps = new LinkedHashMap<>();
  public Map<String, String> getExtraProps() {
      return extraProps;                       // 内部活引用直接外泄
  }
  // KafkaMessageService.buildConsumerProps:205-206（每次 subscribe 线程执行）
  if (config.getExtraProps() != null) {
      props.putAll(config.getExtraProps());    // 迭代非线程安全 LinkedHashMap
  ```
- **严重程度**: P2（理论风险，未具体化——模块内无运行期写者，写不出 in-repo 触发时序，按口径纪律降 P2）
- **并发场景**: T1 = 任意 subscribe 调用线程（`doSubscribe → buildConsumerProps` 迭代 `extraProps`）或引擎启动线程（`DebeziumEngineWrapper.start → buildProperties` 迭代 `extraProperties`，含 :128/:135/:149 的三次 containsKey/get 与 :165-175 的全 Map 迭代）；T2 = 任何持有配置 bean 外泄引用的运行期代码（`config.getExtraProps().put(...)` / `addExtraProperty:359-362`）。
- **触发时序**: 前提（模块外写者）：部署侧经 `getExtraProps()` 活引用或 `addExtraProperty` 在服务 init 之后变更集合 → T1 subscribe 线程在 :206 `putAll` 迭代 LinkedHashMap/HashMap 桶结构 → T2 同刻 put 触发再哈希 → CME 或读出撕裂的桶链（错误配置静默生效）。`DebeziumConfig` 面另有一条放大链：该 bean 为 `Serializable` 且被 nop-stream 持久化进 checkpoint（javadoc :20-22 自述），快照线程序列化 `extraProperties` 时与 T2 写并发 → checkpoint 流损坏。
- **现状**: 配置 bean 全部为 `@DataBean` 可变 POJO，getter 返回内部 Map 活引用，无 `Map.copyOf` 防御性拷贝、无装配后冻结标记；`KafkaClientConfig` javadoc 声明了 extraProps 的合并语义但未声明"装配后只读"契约。
- **风险**: 一旦未来引入运行期配置刷新/多租户共享配置 bean，缺陷即刻从理论转实；checkpoint 损坏面还会把并发问题传导到持久化层。当前仓库无写者，故不判 P1。
- **建议**: 装配终点做防御性快照（`extraProps = Map.copyOf(extraProps)` 于 `KafkaMessageService.doStart` 入口或配置类 `freeze()`）；getter 返回不可变视图；`addExtraProperty` 标注 `@Deprecated` 装配期专用。对齐平台 `CacheConfig`/`ThreadPoolConfig` 的"构建后不可变"惯例。
- **失效面枚举**（F3 同族清扫：全部配置集合字段逐一）: ① `KafkaClientConfig.extraProps`（LinkedHashMap 活引用，本条主证）；② `DebeziumConfig.extraProperties`（HashMap 活引用 + checkpoint 序列化放大面，本条）；③ `PulsarClientConfig`（仅 `serviceUrl` 标量，无集合，**豁免**）；④ `KafkaProducerConfig`/`KafkaConsumerConfig`/`PulsarProducerConfig`/`PulsarConsumerConfig`（全标量字段，装配 HB 后事实只读，**豁免**）；⑤ `DebeziumConfig` 其余字段（标量/String/Duration/Long，**豁免**）；⑥ `setTopicSchemas(Map)`（PulsarMessageService.java:112-116——putAll 入 CHM 目标，装配期一次性，**豁免**）。
- **信心水平**: 缺口存在 = 确定；可触发 = 理论（无 in-repo 写者）
- **误报排除**: 非"无状态单例被并发调用"豁免——外泄的是可变集合活引用；非"有界声明存在的缓存"豁免——无任何上界/只读声明；未按 P1 报告是因触发前提（运行期写者）在本仓库不存在，符合"写不出具体时序降 P2"的硬规则。
- **复核状态**: 未复核
- 复核状态: 保留(P2，理论面)｜现状:新发现（活引用外泄 KafkaClientConfig:29/:47-49 与 DebeziumConfig:152/:348-362 + Serializable checkpoint 放大面（DebeziumConfig javadoc :20-22、DebeziumEngineConfig:128-175 读点）逐点回码确认；「无 in-repo 运行期写者」经独立 grep 复现（addExtraProperty/setExtraProps 主代码零调用），理论面 P2 自评符合降档口径）

## 维度集外线索（overlay）

（本轮 C1 分卷无新增集外线索；统一流水自 nop-message-CL-025 起，见 C3/C8 分卷。）

## 继续判定

**终止信号。** 依据：本轮新增仅 C1-012（P2×1，理论面），无 P0/P1；五个定向面中 KafkaHelper/PulsarHelper、ChannelBacklogProbe、MessageConsumeFailureBridge（C1 面）、ReflectionMessageSubscriptionRegistrar 四面 examined-empty，仅配置 bean 一面有残余缺口且根因单一（缺装配后冻结）；第 1 轮 11 条 + 本轮 1 条后，static 注册表（2 处）、实例守卫（send/subscribe 面）、订阅实现四后端、状态机复位面已全量盘点且有第 1 轮清单表兜底，收敛证据充分。C1 维度判定为可收口，无需第 3 轮。

## 复核结论（WI 4.1 独立复核，2026-10-20）

- **C1-012 复核保留（P2，理论面）**：证据链逐点回码确认，零写者前提 grep 复现，状态行已写于本文件该条目末尾。本轮终止信号判定成立——新增仅 1 条 P2 理论面、五个定向面四面 examined-empty，C1 维度两轮覆盖可收口的裁定成立。
- 本轮无集外线索登记，无可复核对象；Pulsar `subscriptions` add-after-clear 残余窗口不重复编号的处置与第 1 轮失效面枚举核对一致。
- 全维度复核结论（判定统计/跨单元对照/最终保留项）见 `C1-shared-mutable-state.md`「维度复核结论」节：12 条 = 保留 11（含 2 条并入主登记）+ 降级 1（C1-001，同案并入 WI 1.2 C3-006），最终 P1×0/P2×8/P3×4。
