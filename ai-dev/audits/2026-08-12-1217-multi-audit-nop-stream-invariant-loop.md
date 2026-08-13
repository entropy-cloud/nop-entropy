> Audit Status: closed
> Audit Type: multi-dimensional
> Mission: nop-stream-invariant-loop
> Processed: 2026-08-13 — P0-01/P1-01 → plan `2026-08-13-0132-3`（已收口，closure audit APPROVE）；P2-01~P2-23 → roadmap Follow-up Backlog（`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 2026-08-13 P2 批次，含 source 追溯）；open-audit P2-09 引用复核 = 仍 live 未修复，已随本批计划与 backlog 承接

# nop-stream 多维度审计报告（multi-audit）

## 基本信息

- **审核模块**: `nop-stream/` 全模块组（core / runtime / cep / connector / connector-batch / connector-jdbc / connector-debezium / flow / rocksdb / fraud-example）
- **审核日期**: 2026-08-13（live 基线）
- **执行维度**: 01 依赖图与模块边界 / 02 模块职责与文件边界 / 03 API 表面积与契约一致性 / 08 IoC 与 Bean 配置 / 09 错误处理与错误码 / 14 异步与事务模式 / 15 类型安全与泛型 / 16 测试覆盖与质量 / 17 代码风格与规范 / 18 文档-代码一致性 / 19 命名与术语一致性 / 20 跨模块契约一致性 / 21 单元测试有效性
- **目标范围**: `nop-stream/` 下 1084 个 Java 文件（main+test）、10 个 pom.xml、4 个 beans.xml、docs-for-ai 中 nop-stream 相关文档（module-groups.md:23、INDEX.md:219、source-anchors.md STRM-001~037、error-handling.md:174）
- **审计方法**: 4 个并行子 agent 初筛（代码/契约/测试/配置风格四路）→ 主 agent 逐条独立复核（对照 live 源码 + 框架源码 + schema）

## 基线验证（2026-08-13 实测）

| 检查项 | 命令 | 结果 |
|---|---|---|
| mjs 不变式门禁 | `node ai-dev/tools/check-nop-stream-invariants.mjs all` | exit 0（inventory/sync/scan-iterations/scan-output-contract 全 OK） |
| 过渡 pin | `ai-dev/audits/nop-stream-invariants/mjs-pins.json` | `pinnedViolations: []`（Cycle 2 / I4 移除后无残留、无 stale） |
| JUnit 门禁 | core surefire 报告 | `TestInvariantTableCompleteness` 10/0、`TestOutputContractInvariant` 10/0、`TestSynchronizedCollectionInvariant` 12/0、`TestCheckpointIDCounterInvariant` 8/0 全绿 |
| 门禁接线 | `.github/workflows/maven.yml:49` | `node ai-dev/tools/check-nop-stream-invariants.mjs` 在 CI 中执行 |
| output-contract-registry | 4 实现类 + 6 发射点 | 行号与分类全部精确匹配 live 代码（ChainingOutput:111 / TimestampedCollector:97 / RWO:649 / BRWO:717；ProcessOperator:111/:134 / WindowOperator:1030/:1860 / CepOperator:483/:777） |
| 依赖基线 | dependency:tree | core → nop-commons + nop-core；无反向/循环依赖 |

## 发现清单（按优先级）

---

### [P0-01] WindowOperator 非累加器 merge fail-fast（R13-AR-6 修复）无任何回归测试保护；同名测试只测 happy path

**P0 判定理由**: 按 mission 定义 P0 = "failing/absent test for changed behavior"。`WindowOperator` 的 merge 冲突 fail-fast（R13-AR-6 修复：非累加器冲突抛 `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` / `ERR_STREAM_INVALID_STATE`，替代静默覆写数据丢失）属于已变更行为，但**零测试覆盖**——回归为静默覆写时全部测试仍绿。

- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestWindowOperatorCorrectness.java:489-533`（测试）/ `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1468-1484`（生产 fail-fast）
- **证据片段**:
  ```java
  // TestWindowOperatorCorrectness.java:489
  @Test
  void testMergeTypeIncompatibilityThrowsException() throws Exception {
      // ... "Here we verify that a consistent setup works correctly."
      // 只发 3 个元素、推进 watermark，断言 sum=60 —— 与上方两个 merge 测试同语义的 happy path
      assertEquals(1, output.size());
      assertEquals("sum=60", output.getElements().get(0));
  }
  ```
  ```java
  // WindowOperator.java:1482（生产 fail-fast，grep 证实全测试零引用）
  throw new StreamException(ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT)
          .param(ARG_DETAIL, "Cannot merge multiple non-accumulator values. ...");
  ```
- **严重程度**: P0（按 mission 定义：absent test for changed behavior）
- **现状**: (a) `MixedTypeWindowOperator`（:453-487，为测试 fail-fast 而建、含 `useAccumulator` 开关）定义后**从未被实例化**（全库 grep 仅类声明 + 构造器 2 处命中，零实例化）；(b) `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` 在全部测试中零引用；(c) 测试名承诺"抛异常"但方法体自述只验证 happy path，且为维护该测试死代码还定义了一个从不使用的内部类。
- **风险**: 把 WindowOperator.java:1468/:1482 的 throw 删除（回退为静默覆写——正是 R13-AR-6 原始 P1 数据丢失缺陷），全部 2833 个测试仍然全绿。该 P1 修复的回归保护为零，且测试名制造了"已覆盖"的假象。
- **建议**: 用 `MixedTypeWindowOperator(useAccumulator=true→false)` 构造"目标窗口 accumulator + 源窗口 raw value"场景断言抛 `ERR_STREAM_INVALID_STATE`；再构造双 raw value 场景断言抛 `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT`；若公共 API 确实无法构造混合类型（测试注释所称），则删除死类 + 诚实改名 `testSessionWindowMergeHappyPath` 并注释 fail-fast 覆盖缺口。
- **信心水平**: 确定（生产 throw 语句、死类零实例化、错误码零测试引用三者均已独立验证）
- **误报排除**: 不是"测试不足不算问题"——这是 mission P0 定义的直接命中项（changed behavior 无测试），且测试名主动误导后续审计者；不是重复报告 R13-AR-6（该修复 live 正确，问题在测试侧）。
- **复核状态**: 未复核

---

### [P1-01] beans.xml 部署模板使用不存在的 Nop IoC 语法 + 对仅构造器类使用 property 注入，文档化部署契约无法按模板落地

**P1 判定理由**: 模块对外宣称的 Stage 42 多 JVM 部署接线范式（beans.xml 头部注释即文档）按模板照抄必然在容器构建期失败，属真实配置契约漂移。

- **文件**: `nop-stream/nop-stream-runtime/src/main/resources/_vfs/nop/stream/beans/stream-control-rpc.beans.xml:40-57`；`stream-data-plane.beans.xml:43-49`
- **证据片段**:
  ```xml
  <bean id="streamTaskRpcServer_node0"
        class="io.nop.stream.runtime.rpc.StreamControlRpcServer"
        ioc:configMethod="...">            <!-- ioc:configMethod 在 beans.xdef 不存在 -->
      <property name="serviceName" value="streamTaskRpc@node-0"/>  <!-- 该类无 setter，仅构造器 -->
      ...
  ```
  ```xml
  <property name="dataPlaneWireCodec"
            class="io.nop.stream.runtime.transport.SysDaoWireCodec"
            ioc:bean="true"/>              <!-- ioc:bean 属性全仓库仅此处使用，schema 无此属性 -->
  ```
- **严重程度**: P1
- **现状**: `StreamControlRpcServer.java:62-66` 为仅构造器类（5 参构造、`private final` 字段、零 setter）；`RpcDistributedExecutor.java:84-86,105-113` 的 `messageService` 也是构造器参数（仅 `setDataPlaneWireCodec`/`setRemoteDeployMode` 两个 setter）。beans.xdef（`nop-kernel/nop-xdefs/.../beans.xdef:116-138`）只有 `ioc:bean-method`/`ioc:refresh-config-method`/`ioc:config-prefix`/`ioc:auto-refresh`，**无 `ioc:configMethod`**；`ioc:bean="true"` 属性在 nop-ioc 全源码与仓库其余 469+ 个 beans.xml 中零使用。Nop IoC property 注入只认 setter/可写字段（`DefaultBeanClassIntrospection` 经 `IPropertySetter` 解析）。
- **风险**: 照抄模板的部署者（多 JVM 生产接线）在容器构建期直接报错且难以定位——`ioc:configMethod` 无 schema 校验兜底、property 注入无 setter 静默失败或抛注入异常；模块对外宣称的部署契约（E2E 测试程序化接线证明"scaffold non-hollow"）与实际可落地语法不符。
- **建议**: 模板改为 schema 支持的写法——构造器参数用 `<constructor-arg index="0" ref="..."/>`；嵌套 bean 写 `<property name="dataPlaneWireCodec"><bean class="...SysDaoWireCodec"/></property>`；删除 `ioc:configMethod="..."` 占位；或补一个被测试覆盖的 live 接线示例。
- **信心水平**: 确定（schema 属性集合 + nop-ioc 注入机制 + 目标类构造器三处独立验证）
- **误报排除**: 不是"注释里的东西无所谓"——这正是该模块唯一对外部署接线文档，且与模块自述"Stage 42 deployment scaffold"承诺冲突；不是 ioc:bean 特性漏查（全仓库唯一使用点即本文件）。
- **复核状态**: 未复核

---

### [P2-01] WindowOperator 的 keySerializer/windowSerializer 为死字段，dummy serializer 的 createInstance() 违反 TypeSerializer 契约返回 null

**P2 判定理由**: 无当前错误行为（字段零消费），属潜伏契约违约 + 死代码，非阻塞。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:151/:161/:262-331/:358-360`；`WindowOperatorFactoryImpl.java:122-159`
- **证据片段**:
  ```java
  // WindowOperator.java:151
  /** For serializing the key in checkpoints. */
  protected final TypeSerializer<K> keySerializer;
  // 全文件 2040 行 grep：keySerializer./windowSerializer. 零方法调用（仅声明/构造赋值/notNull/拷贝）
  ```
  ```java
  // WindowOperatorFactoryImpl.java:137-143
  public T createInstance() {
      try {
          return typeClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
          return null;   // 违反 TypeSerializer 契约（createInstance 不得返回 null）
      }
  }
  ```
- **严重程度**: P2
- **现状**: `keySerializer`/`windowSerializer` 仅有字段声明、构造器参数、`notNull()` 强制非空（:329-331）、`copyForSubtask` 拷贝（:358-360），**全类零使用点**；4 个工厂入口全部传 `createDummySerializer(keyClass)`，其 `createInstance()` 反射失败时静默返回 null。对照 `TimeWindowSerializer.createInstance()` 返回真实实例（:184-186）。
- **风险**: invariant #1 门禁钉死"参数 round-trip 传递"但**不验证消费**——未来任何恢复路径（RocksDB key 序列化、跨版本 checkpoint）一旦开始消费，`createInstance()` 静默 null key 而非 fail-fast。是 F1 族"参数被遗忘"打地鼠模式的镜像：传递侧被门禁钉死、消费侧被遗忘。
- **建议**: 三选一：(1) 在 snapshot/restore 路径真实消费两个 serializer；(2) `createInstance()` 反射失败改 fail-fast 抛 `UnsupportedOperationException`；(3) 确认架构不需要 key/window 级序列化则删字段 + 在 invariant #1 门禁登记"仅传递不消费"exclusion。
- **信心水平**: 确定
- **误报排除**: 不是死代码优雅性问题——`notNull` 强制 + javadoc 承诺 + 门禁保证传递三层叠加，消费端零实现，属门禁未覆盖的新实例。
- **复核状态**: 未复核

### [P2-02] 全模块 142 处裸 IllegalArgumentException/IllegalStateException/UnsupportedOperationException，偏离 error-handling 两档策略（flow DSL 公共入口为高发区）

**P2 判定理由**: 无错误行为（消息英文、fail-fast 语义成立），属模块级约定漂移与维护成本，非阻塞。

- **文件**: 分布（main）：`nop-stream-flow/.../builder/StreamModelDslBuilder.java:88-432`（24 处）、`AdvancedTransforms.java:100-372`（23 处）、`nop-stream-core/.../state/shard/KeyGroupAssignment.java:83-160`（9 处）、`KeyGroupReshard.java:74-116`（7 处）、`cep/.../GroupPattern.java:46-56`、`execution/Mail*.java`、`CheckpointCoordinator.java:1333-1341`、`fraud-example/pattern/*.java`（12 处）等
- **证据片段**:
  ```java
  // StreamModelDslBuilder.java:88
  throw new IllegalArgumentException("StreamModel must not be null");
  // KeyGroupAssignment.java:83
  throw new IllegalArgumentException("maxParallelism must be at least 1: " + maxParallelism);
  ```
- **严重程度**: P2
- **现状**: `error-handling.md:203` 反模式表明确禁止裸 `RuntimeException("some message")`；`NopStreamErrors` 已有 `ERR_STREAM_INVALID_ARG`/`ERR_STREAM_UNSUPPORTED`/`ERR_STREAM_INVALID_STATE` 却未在此类校验点使用。同模块 cep 用 `StreamException(ERR_CEP_*)`、connector/rocksdb 用 `StreamRuntimeException`，flow 用裸 IAE——模块内两套异常心智模型并存。
- **风险**: flow DSL 是框架公开入口，DSL 解析错误无 ErrorCode → 上层无法按错误码路由/结构化响应；运维需肉眼读消息区分 DSL 错误与 JDK 层 IAE；新增错误分类逻辑时 142 处漏网。
- **建议**: 统一迁移到 `StreamRuntimeException("...")`（保持英文消息），或新代码收敛 + 在 error-handling.md 登记豁免；优先 flow builder（对外入口）。
- **信心水平**: 确定（事实）；判级含判断成分
- **误报排除**: 不是逐文件挑刺——142 处可量化，且是 AGENTS.md 明文规范 + 错误码已存在却未使用，属结构性约定漂移；按 mission 定级（无行为错误、消息英文清晰）不升 P1。
- **复核状态**: 未复核

### [P2-03] RocksDBKeyedStateBackend 打开期临时 Options 原生资源未关闭

**P2 判定理由**: 真实资源泄漏（每次 open 泄漏一个 JNI 堆外 Options），但单对象、非高频，非阻塞。

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:195-201`
- **证据片段**:
  ```java
  try {
      Options options = new Options(dbOptions, cfOptions);   // 无任何 close
      existingCFs = RocksDB.listColumnFamilies(options, dbPath);
  } catch (RocksDBException e) {
      existingCFs = Collections.emptyList();
  }
  ```
- **严重程度**: P2
- **现状**: 每次 open 泄漏一个原生 `Options`；同模块 `RocksDBIncrementalRestore.java:150` 对同样操作使用 `try (Options listOpts = ...)` 正确写法，可证为疏漏。
- **风险**: 任务反复 restart/recovery 时 JNI 堆外内存缓慢累积。
- **建议**: `try (Options options = new Options(dbOptions, cfOptions))`（与 :150 一致）。
- **信心水平**: 确定
- **误报排除**: 同文件内正确写法与错误写法直接对照，非"不优雅"。
- **复核状态**: 未复核

### [P2-04] JdbcCheckpointStorage 通用回退路径用裸 RuntimeException 包装

**P2 判定理由**: 单点、模块内部、非高频路径，无行为错误。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:747-750`
- **证据片段**:
  ```java
  } catch (Exception e) {
      if (!isDuplicateKeyException(e)) {
          throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
      }
  ```
- **严重程度**: P2
- **现状**: GENERIC 方言 upsert 回退路径对非 RuntimeException 异常包裸 `new RuntimeException(e)`，绕过 StreamException/ErrorCode 体系。
- **风险**: checkpoint 存储写入失败时上层无法按错误码分类/路由；违反 error-handling.md 反模式表。
- **建议**: `throw NopException.adapt(e)` 或 `new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e)`。
- **信心水平**: 确定
- **误报排除**: 同文件其他路径（:676/:743/:752）异常处理规范，仅此一处，非全局风格。
- **复核状态**: 未复核

### [P2-05] GraphModelCheckpointExecutor 1595 行静态上帝类，执行与恢复职责混合

**P2 判定理由**: 结构性维护成本，无行为缺陷。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`（全文件，1595 行，40+ 个 private static 方法）
- **证据片段**:
  ```java
  public static StreamExecutionResult executeWithCheckpoint(...)   // :102 执行入口
  public static String triggerSavepoint(...)                       // :311
  private static void restoreFromCheckpoint(...)                   // :940 恢复
  private static TaskStateSnapshot buildRescaledTaskState(...)     // :1260 reshard 合并
  private static void restoreTaskStatesFromCheckpoint(...)         // :1455 恢复
  ```
- **严重程度**: P2
- **现状**: 构建计划（:294-548）、任务注册（:625-773）、调度执行（:773-940）、恢复（:940-1056）、reshard/状态合并（:1057-1455）全部堆在无实例字段的静态工具类中。
- **风险**: 恢复路径（过半代码）与执行路径耦合，恢复语义变更波及全类；静态方法不可注入/不可 mock，单元覆盖成本高。
- **建议**: 拆分为 `ExecutionPlanBuilder` / `CheckpointRestoreCoordinator` / `RescaleStateMerger`；或至少按区块加区域注释。
- **信心水平**: 确定（结构性事实）
- **误报排除**: 功能正确、无行为缺陷，故 P2 不升 P1。
- **复核状态**: 未复核

### [P2-06] FollowKind.java main 代码 javadoc 含中文

**P2 判定理由**: 规范类小项（main 代码仅此一处实质中文 javadoc；无运行时中文消息）。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/FollowKind.java:12-18`
- **证据片段**:
  ```java
   *  举例来说，模式"a b"，给定事件序列"a"，"c"，"b1"，"b2"，会产生如下的结果：
  ```
- **严重程度**: P2
- **现状**: 全模块 14 处中文，13 处在测试/引用注释，仅此文件是 main 代码 javadoc 实质中文。
- **风险**: 违反英文规范；AI 文档阅读准确性。
- **建议**: 翻译为英文 javadoc。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-07] TestTaskDeploymentDescriptor.defaultConstructorAndSettersInteract — 纯 getter/setter 往返测试（命中 P-1）

**P2 判定理由**: 低价值镜像测试（无保护力但无害），测试卫生问题非产品缺陷。

- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/rpc/TestTaskDeploymentDescriptor.java:90-113`
- **证据片段**:
  ```java
  descriptor.setJobId("job-3"); ...
  assertEquals("job-3", descriptor.getJobId());
  assertEquals("map", descriptor.getVertexId());  // 8 个 set→get 往返，编译器已保证
  ```
- **严重程度**: P2（命中 P-1；该类无任何自定义校验/派生逻辑，不符合 P-1 例外条件）
- **现状**: 纯数据持有类 set/get 往返；同文件另有真正有价值的 `descriptorRoundTripsThroughJavaSerialization`。
- **风险**: 虚增绿测信心；惯例信号稀释（`@Tag("low-value")` 未打）。
- **建议**: 删除或按项目惯例标 `@Tag("low-value")`。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-08] partitionedPlanEdgePlanIsSerializable — 名字声称序列化验证，实际无序列化（命中 P-6+P-1）

**P2 判定理由**: 名称误导 + 断言镜像，制造虚假保护信号。

- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/rpc/TestTaskDeploymentDescriptor.java:115-121`
- **证据片段**:
  ```java
  @Test
  void partitionedPlanEdgePlanIsSerializable() {
      // Sanity: PartitionedPlan components must round-trip ...
      PartitionedPlan.VertexPlan vp = new PartitionedPlan.VertexPlan("v", 4, null);
      assertEquals(4, vp.getParallelism());   // 无任何序列化/反序列化
  }
  ```
- **严重程度**: P2
- **现状**: 注释声称 "must round-trip"，方法体只构造 + 断言 getter；`VertexPlan` 丢失 `Serializable` 实现（RPC 线协议要求）时测试照常通过。
- **风险**: RPC 部署线协议序列化契约上虚假保护；未来 `DeploymentPlan` 字段改动无保护。
- **建议**: 执行真实 Java 序列化往返（同文件第一个测试的模式），或删除。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-09] 测试命名与内容不符的遗留命名 + 近重复测试（P-6 小集合）

**P2 判定理由**: 命名误导类问题，行为断言本身有效。

- **文件**:
  - `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/operator/TestCepOperatorTimeout.java:94-115` — `testTimeoutWithProcessingTime` 实际由 `processWatermark(20)`（event-time 路径）触发，MOCK_PTS 不参与（P-6）
  - `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/model/builder/TestCepPatternBuilder.java:90-113` — `testBuildTwoPartPattern_nfaHasMoreStatesThanSinglePart` 从未构造 single-part NFA 做对比（P-6）
  - `nop-stream-runtime/.../windowing/TestWindowOperatorBasic.java:23-72`、`core/.../operators/TestWindowOperatorWatermarkReception.java:24-27` — 类名带 WindowOperator 实为测试 timer/assigner 原语（javadoc 已诚实自述）
  - `TestCepOperatorDanglingCleanup.java:81-125` 与 `:142-182`、`TestWindowOperatorCorrectness.java:362-443` 与 `:489-533` — 近重复测试
- **严重程度**: P2
- **现状**: 测试名承诺与身体不一致，误导按名字搜索覆盖度的审计者/开发者。
- **风险**: 如 `onProcessingTime` 超时路径单独回归（F4 族 R11-AR-4 同类），名字为 processing-time 的测试不会失败而掩盖缺口。
- **建议**: 按行为改名；近重复合并为参数化或注明回归锚点。
- **信心水平**: 很可能（16-04 已通读文件确认无 processing-time timer 触发代码）
- **复核状态**: 未复核

### [P2-10] `@Tag("low-value")` 约定应用不一致 + surefire 未配置 excludedGroups

**P2 判定理由**: 标签惯例不彻底，属测试卫生/统计信号问题。

- **文件**: 全局（已标 33 处 / 20 文件；未标镜像大量存在：`TestOperatorSnapshotResult.java:42-71,89-131`、`TestTaskStateSnapshot.java:35-64,89-105`、`TestCompletedCheckpoint.java:42-75`、`TestTaskDeploymentDescriptor.java:90-121` 等）；所有 nop-stream pom 均无 `excludedGroups`
- **证据片段**:
  ```java
  // TestOperatorSnapshotResult.java:42-47
  result.putOperatorState("op1", "operator-state");
  assertEquals("operator-state", result.getOperatorState("op1"));
  ```
- **严重程度**: P2
- **现状**: 项目已确立低价值测试打标惯例（`TestOneInputTransformation` 类级注释），实际约 90-120 个镜像级测试只标了 33 个；surefire 无 `excludedGroups` 配置（标签仅是文档）。
- **风险**: "绿测数字"与真实保护力脱节约 3-4%；惯例信号价值稀释。
- **建议**: 批量复核数据持有类测试，纯 set/get 往返补 `@Tag("low-value")` 或删除。
- **信心水平**: 很可能（统计基于抽样精读 28 文件 + 全量 grep，数量为估计）
- **复核状态**: 未复核

### [P2-11] 两个 beans.xml 声明同一 bean id `streamMessageService`，模块 beans/ 目录两文件不可同时加载进同一容器

**P2 判定理由**: 潜伏配置陷阱（当前无 live 冲突、测试有规避注释），非阻塞。

- **文件**: `stream-control-rpc.beans.xml:34-35`；`stream-data-plane.beans.xml:68-69`；规避逻辑 `nop-stream-runtime/src/test/java/io/nop/stream/runtime/ioc/TestStreamModuleDiscovery.java:93-103`
- **证据片段**:
  ```xml
  <bean id="streamMessageService" ioc:default="true"
        class="io.nop.message.core.local.LocalMessageService"/>
  ```
  ```java
  // TestStreamModuleDiscovery.java:93-103
  // "loading both raw into one scoped container would collide, so we select the
  //  data-plane file by name from the discovered set."
  ```
- **严重程度**: P2
- **现状**: 两文件都只在各自测试中被单独加载；但 `_module` 驱动的模块发现遍历会同时返回两文件，任何按 beans/ 目录泛化加载的部署代码都会撞 `ERR_IOC_DUPLICATE_BEAN_DEFINITION`（`BeansDefinition.addBean` 显式抛错；ioc:default 消解逻辑在源码中为注释态，`BeansDefinition.java:78-88` 未启用）。
- **风险**: 生产多 JVM 部署按模块 beans/ 目录加载时容器启动失败，且错误定位依赖知道"两文件二选一"的隐含约定。
- **建议**: 从 control-rpc 文件移除重复的 `streamMessageService` 声明（只留模板），或加 `<ioc:condition>` 互斥，并在注释写明"两文件二选一加载"。
- **信心水平**: 确定
- **误报排除**: 不是"ioc:default 自动消解"——源码证实该消解逻辑被注释禁用（`BeansDefinition.java:78-88` 注释块），测试注释自证 collide。
- **复核状态**: 未复核

### [P2-12] fraud-example demo 引用全仓库未定义的 IoC bean `transactionSourceFunction`

**P2 判定理由**: 示例模块配置不完整（demo 无生产接线、无人加载），非产品缺陷。

- **文件**: `nop-stream/nop-stream-fraud-example/src/main/resources/_vfs/nop/stream/demo/fraud-detection.stream.xml:31-34`；解析失败路径 `nop-stream-flow/.../builder/GlobalBeanFunctionResolver.java:24-28`
- **证据片段**:
  ```xml
  <source id="tx-source" name="TransactionSource"
          bean="transactionSourceFunction" parallelism="2" .../>
  ```
  ```java
  Object bean = BeanContainer.tryGetBean(beanName);
  if (bean == null) {
      throw new IllegalArgumentException("Stream DSL bean reference not found ...");
  ```
- **严重程度**: P2
- **现状**: `transactionSourceFunction` 在 nop-stream 全模块（含 4 个 beans.xml）零定义；demo 无 Java driver 无人加载，但作为用户第一接触面的示例宣称"bean 引用模式"。
- **风险**: 任何按 demo 复制/加载的尝试在 bean 解析处硬失败；示例模块配置完整性影响模块可信度。
- **建议**: fraud-example 增加 beans.xml 注册 demo bean，或将 source 改为内联 XPL 模式，并注明 demo 未接线。
- **信心水平**: 确定（grep 单命中 + 模块无 beans.xml）
- **误报排除**: 与前置审计 dangling-transform 发现（graph 拓扑问题）不同——这是 IoC 引用问题。
- **复核状态**: 未复核

### [P2-13] import 分组在模块内部分裂 + code-style.md 与 AGENTS.md 方向矛盾

**P2 判定理由**: 双文档矛盾 + 风格分裂，真实维护噪音。

- **文件**: `nop-stream-flow/.../builder/StreamModelDslBuilder.java:10-20`、`runtime/.../execution/RpcDistributedExecutor.java:10-17`（java-first）；`runtime/.../rpc/StreamControlRpcServer.java:10-23`（io.nop-first）；`docs-for-ai/02-core-guides/code-style.md:17`
- **证据片段**:
  ```
  code-style.md:17: import 分组清晰：java.* -> jakarta.* -> third-party -> io.nop.*
  AGENTS.md:     Imports: grouped (io.nop.* → jakarta.*/javax.* → third-party → java.*)
  ```
- **严重程度**: P2
- **现状**: main 代码 java-first 是多数（344 vs 205），test 代码 io.nop-first 是多数（332 vs 70）；AGENTS.md 与 code-style.md 方向相反（MA4.2-14 裁定后 code-style.md 未同步）。
- **风险**: AI 代理按 AGENTS.md "修正" import 时产生两波反向 noisy diff；IDE 自动整理来回横跳。
- **建议**: 先修 code-style.md:17 消除文档矛盾；nop-stream 选择与 AGENTS.md 一致方向（io.nop 优先）批量收敛。
- **信心水平**: 确定（全模块统计 + 双文档矛盾可验证）
- **复核状态**: 未复核

### [P2-14] StreamControlRpcServer javadoc 语病 + 裸 RuntimeException 包装

**P2 判定理由**: 文档残缺句 + 单点异常包装偏离。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/StreamControlRpcServer.java:49, 124`
- **证据片段**:
  ```java
  /** Builds and starts-not a control-plane RPC server. Call {@link #start()} to ... */
  out = DefaultRpcMessageAdapter.INSTANCE.getErrorResponse(
          ex instanceof Exception ? (Exception) ex : new RuntimeException(ex), request);
  ```
- **严重程度**: P2
- **现状**: "Builds and starts-not" 残缺句使"构造后是否需手动 start()"产生歧义；:124 对非 Exception Throwable 用裸 RuntimeException 包装。
- **风险**: @Internal 类唯一使用说明歧义；异常链类型信息丢失。
- **建议**: 改为 "Builds a control-plane RPC server without starting it."；包装类改用 `StreamRuntimeException`。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-15] CEP 错误码常量名与错误码 ID 描述不一致

**P2 判定理由**: 命名/ID 语义不一致（公共错误码 ID 是跨系统契约，但非行为错误）。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/NopCepErrors.java:27-29`
- **证据片段**:
  ```java
  ErrorCode ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP =
          define("nop.err.cep.follow-not-does-support-group", "Not condition does not support complex patterns", ...);
  ```
- **严重程度**: P2
- **现状**: 常量名述 "NOT_CONDITION 不支持"，code 串却写 "follow-not-does-support-group"（英文也不通顺）；`NopStreamErrors` 44 个其余错误码常量名与 code 一一对应，此条唯一例外。
- **风险**: 按 code 查文档的运维与按常量名读代码的开发者得到两个"事实"；排查误导。
- **建议**: 统一为 `ERR_CEP_FOLLOW_NOT_DOES_SUPPORT_GROUP` + `nop.err.cep.follow-not-does-support-group`（code 串改动属公共 API 变更需谨慎）。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-16] 同一测试类在两个 test beans.xml 注册为两个不互相对应的 id

**P2 判定理由**: test-only bean 命名不一致。

- **文件**: `nop-stream-flow/src/test/resources/_vfs/nop/stream/test/test-smoke.beans.xml:14-15`；`test-reduce-pipeline.beans.xml:18-19`
- **证据片段**:
  ```xml
  <bean id="collectingSinkFunction" class="io.nop.stream.flow.testing.CollectingSinkFunction"/>
  <bean id="advancedCollectingSink"  class="io.nop.stream.flow.testing.CollectingSinkFunction"/>
  ```
- **严重程度**: P2
- **现状**: 同一类注册为 `collectingSinkFunction` 与 `advancedCollectingSink`，后者 id 不含 Function 后缀也不对应类名。
- **风险**: 读者误以为 `advancedCollectingSink` 是另一个实现；两文件互为模板时延续不一致。
- **建议**: 改为 `advancedCollectingSinkFunction`（测试查找点 `TestAdvancedPipelineE2E.java:87` 改动 1 行）。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-17] BeanFunctionResolver 平台自建 SPI 缺少 I 前缀

**P2 判定理由**: 命名一致性（模块内平台接口均 I 前缀）。

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/BeanFunctionResolver.java:20`
- **证据片段**:
  ```java
  public interface BeanFunctionResolver {
      <T> T resolve(String beanName, Class<T> targetType);
      boolean contains(String beanName);
  }
  ```
- **严重程度**: P2
- **现状**: 模块内 Flink 兼容函数接口无 I 前缀是有意为之（Flink 命名对齐），但 `BeanFunctionResolver` 是 Nop 平台自建 SPI（对照 `IStreamTaskRpcService`/`IDataPlaneWireCodec`/`IStreamExecutionDispatcher`）。
- **风险**: 扩展点信号弱，实现类（GlobalBeanFunctionResolver/InMemoryBeanFunctionResolver）与接口易混淆。
- **建议**: 更名 `IBeanFunctionResolver`（3 实现类 + 2 测试 + 文档，小范围重构）。
- **信心水平**: 有趣的猜测
- **复核状态**: 未复核

### [P2-18] INDEX.md:219 nop-stream 子模块清单缺失 2 个模块（connector-jdbc / rocksdb）

**P2 判定理由**: 权威导航文档内容遗漏（module-groups.md 正确，反证为 INDEX 疏漏）。

- **文件**: `docs-for-ai/INDEX.md:219`
- **证据片段**:
  ```
  文档列 8 个模块（core/cep/runtime/connector/connector-batch/connector-debezium/flow/fraud-example）
  live nop-stream/pom.xml 10 个模块（另有 nop-stream-connector-jdbc、nop-stream-rocksdb）
  ```
- **严重程度**: P2
- **现状**: INDEX.md（AGENTS.md 称 "authoritative docs navigation baseline"）漏掉 `nop-stream-connector-jdbc`（JDBC exactly-once sink）与 `nop-stream-rocksdb`（RocksDB 增量状态后端）；module-groups.md:23 正确列出全部 10 个。
- **风险**: 按 INDEX 导航的 AI/开发者找不到两模块位置，可能误判不存在。
- **建议**: INDEX.md:219 补上两项。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-19] OutputTag 公共 API javadoc 示例不可编译 + "必须匿名内部类" 陈述与本实现不符

**P2 判定理由**: 公共 API javadoc 契约漂移（示例不编译），非行为错误。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/OutputTag.java:31-44`
- **证据片段**:
  ```java
  /** An {@code OutputTag} must always be an anonymous inner class so that Flink can derive ... */
  // Example: OutputTag<Tuple2<String, Long>> info = new OutputTag<Tuple2<String, Long>>("late-data"){};
  public OutputTag(String id, TypeInformation<T> typeInfo) {   // 唯一构造器
  ```
- **严重程度**: P2
- **现状**: javadoc 从 Flink 复制未适配：唯一构造器要求显式 `TypeInformation`，单参匿名内部类形式不存在；示例类型 `Tuple2` 在本包无导入。
- **风险**: 按 javadoc 写匿名单参形式编译失败，误导 side-output 使用者；公共 API 文档是用户契约的一部分。
- **建议**: 重写 javadoc 为 `new OutputTag<>("late-data", TypeInformation.of(Long.class))`，删除"必须匿名内部类"与不可编译示例。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-20] STRM-026 锚点：SubtaskTask 状态机描述不完整（实际 9 态，文档 5 态）

**P2 判定理由**: 锚点描述简化致不完整（P3 级文档漂移）。

- **文件**: `docs-for-ai/04-reference/source-anchors.md:210` vs `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/SubtaskTask.java:35-47`
- **证据片段**:
  ```
  文档: CREATED→RUNNING→CANCELING→COMPLETED/FAILED/CANCELED
  代码: enum State { CREATED, SCHEDULED, DEPLOYING, RUNNING, RECOVERING, CANCELING, COMPLETED, FAILED, CANCELED }
  ```
- **严重程度**: P2
- **现状**: 文档省略 `SCHEDULED`/`DEPLOYING`/`RECOVERING` 三个中间态。
- **风险**: 低；调试状态转换时可能漏查中间态。
- **建议**: 补全状态集或注明"简化视图"。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-21] INDEX.md:219 对 nop-stream-flow 描述"流控"措辞不准确

**P2 判定理由**: 措辞漂移（flow 实际是 XDSL 声明式编排）。

- **文件**: `docs-for-ai/INDEX.md:219`
- **证据片段**:
  ```
  INDEX: `nop-stream-flow`（流控）
  module-groups.md:23: nop-stream-flow（XDSL StreamModel 声明式编排 + Delta 定制）
  ```
- **严重程度**: P2
- **现状**: "流控"通常指流量控制；flow 实际职责是 XDSL 声明式编排 + Delta 定制。
- **风险**: 低；误导对模块能力的预期。
- **建议**: 改为 "XDSL StreamModel 声明式编排（Delta 定制）"。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-22] module-groups.md:23 将"检查点存储抽象"归入 nop-stream-runtime，实际抽象接口在 core

**P2 判定理由**: 归属描述不一致（低影响文档漂移）。

- **文件**: `docs-for-ai/01-repo-map/module-groups.md:23`
- **证据片段**:
  ```
  文档: runtime（运行时、检查点协调器、检查点存储抽象、窗口算子、...）
  实际: ICheckpointStorage / ISegmentStore / LocalFileSegmentStore 在 core/checkpoint/storage；
        runtime/checkpoint/storage 仅有 JdbcCheckpointStorage / LocalFileCheckpointStorage / CheckpointSerDe
  ```
- **严重程度**: P2
- **现状**: 检查点存储抽象（ICheckpointStorage 等）在 core，文档归到 runtime；实现类（Jdbc/LocalFile）在 runtime。
- **风险**: 低；按文档在 runtime 找不到抽象接口浪费查找时间。
- **建议**: 将"检查点存储抽象"从 runtime 职责移到 core（或注明 storage 抽象在 core、协调器在 runtime）。
- **信心水平**: 确定
- **复核状态**: 未复核

### [P2-23] core→runtime 的 `IWindowOperatorFactory` 反射类名契约未在 docs-for-ai 记录

**P2 判定理由**: 跨模块字符串级契约点无文档记录（有 fail-fast 兜底，非阻塞）。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/WindowedStreamImpl.java:154-177`；实现 `nop-stream-runtime/.../windowing/WindowOperatorFactoryImpl.java`
- **证据片段**:
  ```java
  Class<?> factoryClass = Class.forName(
          "io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl");
  ```
- **严重程度**: P2
- **现状**: core 窗口 API 通过硬编码类名反射加载 runtime 实现（core 对 runtime 无编译依赖）；类名改名/移动无编译期保护（有 fail-fast 兜底：加载失败 WARN + 调用点 `StreamException`）。
- **风险**: 低-中；`WindowOperatorFactoryImpl` 重命名会静默退化为"窗口操作运行时失败"，排查需知道反射机制。
- **建议**: 在 source-anchors.md 或 module-groups.md 补一条说明（重命名需同步 `WindowedStreamImpl:162`）。
- **信心水平**: 很可能
- **复核状态**: 未复核

---

## 逐条核对过的锚点清单（STRM-001 ~ STRM-037）

37 条中 **36 条有效、1 条漂移（STRM-026，P2-20）**。重点核验项（STRM-005/006/007 携带 StreamModel、STRM-012 TypeSerializer G41、STRM-021 InputGate barrier 对齐、STRM-024 GraphModelCheckpointExecutor、STRM-032 CepOperator fallback、STRM-033/036 parallelism fail-fast 门禁 + `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`）全部与 live 代码一致。

## 零发现维度结论（含检查范围）

- **维度01 依赖图与模块边界**: 10 个 pom.xml 全部核对，`core → nop-commons+nop-core` 干净；runtime → core+cluster+rpc+message+ioc（rocksdb/connector/pulsar/kafka 全部 test scope）；无循环依赖、无反向依赖。`runtime/pom.xml:14-15` 注释"depends only on nop-stream-core"过时（另有 3 个 compile 依赖）——注释漂移，未报。
- **维度02 职责与文件边界**: `_gen/` 无手写代码混入；JobCoordinator（1823 行）/TaskManager（865 行）/WindowOperator（2040 行）职责分区清晰（B-01 GraphModelCheckpointExecutor 例外已报）。
- **维度14 异步与事务**: 8 个线程池全部 shutdown/awaitTermination；`txn().runInTransaction` 使用正确；2PC finishCommit 无条件 remove 防 pendingCommits 泄漏；连接/Statement 全 try-with-resources；heartbeat 两段 try/catch 完整；InputGate 中断语义注释明确。零发现。
- **维度15 类型安全**: 无裸 raw type（301 处均带 diamond）；unchecked cast 集中在序列化边界且有 instanceof 守卫，属合理。
- **维度03/20 公共契约**: 无 Map<String,Object> 反模式污染公共 DataStream API（仅 checkpoint 持久化格式内部使用）；fraud-example 全部 import 契约闭合；错误码全集（66 个 ERR_STREAM_* + ERR_CEP_*）文档引用与代码定义双向一致，无"文档引用但代码不存在"错误码。
- **维度08 注入安全**: `@Inject`/`@InjectValue`/Spring 注解全模块零使用（统一 BeanContainer 显式查找模式）；无 `@Inject private` 违规；bean 命名无 nop* 前缀滥用；`_module` 0 字节位置正确。

## 执行统计

| 批次 | 初筛 agent | 发现数 | 复核后保留 | 驳回/合并 |
|---|---|---|---|---|
| 代码（01/02/09/14/15） | 6 | 6 | 0（2 个合并入 P2-02） |
| 契约（03/18/20） | 7 | 6 | 1 驳回（"runtime 注释过时"未报） |
| 测试（16/21） | 8 | 4（合并为 P2-07~10 四组） | 4 个 P3 命名项并入 P2-09 |
| 配置/风格（08/17/19） | 9 | 8 | 1 合并入 P2-02 |

**最终保留**: P0=1、P1=1、P2=21。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | 变更行为无回归测试（WindowOperator merge fail-fast） |
| P1 | 1 | 部署接线模板使用不存在的 IoC 语法（配置契约漂移） |
| P2 | 21 | 死代码/潜伏契约违约（2）、异常约定漂移（3）、结构维护成本（1）、测试卫生/命名（5）、配置陷阱/示例完整性（3）、文档漂移（7） |

## 总评

nop-stream 模块组整体健康度显著高于同类引擎模块：13 个审计维度中 6 个零发现；37 条架构锚点 36 条有效；不变式门禁（mjs 5 命令 + JUnit 10 类 102 tests）全绿且与 live 代码双向吻合；依赖边界干净、并发/事务/序列化边界克制。Cycle 2 / I6 稳态暂停裁定与 live 现状一致（门禁零命中、pin 清零、无新族实例）。

但本审计暴露了一个**门禁体系的系统性盲区**：不变式门禁覆盖"传递/注册/枚举"等结构性契约，却不覆盖"行为路径的回归测试是否存在"。P0-01（R13-AR-6 merge fail-fast 零测试 + 误导性测试名）正是该盲区的实例——被修复的 P1 数据丢失缺陷没有任何测试会抓住其回退。这与 roadmap 中"反应式测试（per-bug）非穷举"的根因分析相互印证：修复已落地，但部分修复的测试保护是**名义性**的。建议在 I6 收口检查项中增加"变更行为必须存在可触发该行为的测试（不得只有 happy-path 同名测试）"的核对，并在测试维度按 P-1~P-8 反模式对已修复族做一次翻查（本次抽样已发现 4 组，全量可能更多）。

## 优先修复建议

1. **P0-01**: TestWindowOperatorCorrectness 补 merge 冲突 fail-fast 双场景断言（accumulator+raw → `ERR_STREAM_INVALID_STATE`；双 raw → `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT`），删除或修复 `MixedTypeWindowOperator` 死代码；若公共 API 确实不可构造混合类型，删除死类 + 诚实改名。
2. **P1-01**: 修正两个 beans.xml 部署模板为 schema 支持的构造器注入/嵌套 bean 写法，删除 `ioc:configMethod`/`ioc:bean="true"` 占位。
3. **P2 批次**: E-01 serializer 消费/删字段裁决 → RocksDB Options try-with-resources → flow builder 异常迁移（或登记豁免）→ 测试打标一致性 → 文档 7 处漂移修订。

## 本次审核盲区自评

1. **测试全量未跑**：本审计以 live 源码阅读 + surefire 报告核对为主，未重新执行全量 `./mvnw test -pl nop-stream -am -T 1C`（基线 2833/0 来自 Cycle 2 / I5 记录，代码自彼时无变更——working tree clean）。P0-01 的"全测试仍绿"结论基于该基线 + fail-fast 路径零引用的静态证据，未做"删 throw 后测试是否变红"的实证。
2. **测试维度为抽样**：446 个测试文件中精读约 28 个，低价值测试比例（约 4-5%）是抽样外推，非精确统计。
3. **未审计 `_gen/` 生成代码与 flow 的 XDSL 模型文件本体**（仅核对生成链路闭合性）。
4. **并发/时序类问题**：静态审计无法覆盖运行期竞态，本轮未发现新并发族实例不代表不存在。
5. **nop-stream-fraud-example 与 nop-message-debezium 边界**：仅核对契约引用闭合，未审计 nop-message 侧实现。

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
