# 09 - nop-stream 文档-代码一致性 + 跨模块契约一致性审计（R5）

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`
- **审计维度**: 深度审计手册维度 18（文档-代码一致性）+ 维度 20（跨模块契约一致性）
- **范围**:
  - 文档面：`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-user-guide.md`、`nop-stream-connectors.md`、`nop-stream-cdc-cookbook.md`、`nop-stream-migration-guide.md`、`nop-stream-sql.md`；`docs-for-ai/01-repo-map/module-groups.md`（nop-stream 行）、`docs-for-ai/04-reference/source-anchors.md`（nop-stream 锚点节）、`docs-for-ai/INDEX.md`（nop-stream 路由）、`nop-stream/README.md`
  - 设计文档（声明 vs 实现）：`ai-dev/design/nop-stream/`（重点：01-architecture-baseline、stream-dsl-design、distributed-runbook、window-failfast-decisions、sql-landing-decision、core-design 侧输出节；其余经锚点间接核对）
  - 代码契约面：nop-stream-core/runtime/flow/connector*/sql/rocksdb/cep/fraud-example + 跨模块对端 `nop-message/nop-message-debezium`、`nop-kernel/nop-xdefs/stream.xdef`、`nop-core-framework/nop-ioc`
- **方法**: 以 live code 为准的声明级核对——文档中每个可检验声明（类全名/方法签名/配置 key 字面量/默认值/错误码/指标名/测试锚点/xdef 属性/模块计数）逐一在代码中定位比对；示例按静态推演（xdef 合法性 + 类路径 + 默认值）判定可运行性。只读分析，未运行 maven/测试。

**发现计数：P1 × 1，P2 × 8，P3 × 4（共 13 条）**

---

### [DC-1] 用户指南「能力边界」段对 union/sideOutput 的否定已全面失真——两者 Java API 与 XDSL union 均已落地
- **文件**: `docs-for-ai/03-modules/nop-stream-user-guide.md:73 ↔ nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStream.java:157` + `DataStreamImpl.java:158-177` + `SingleOutputStreamOperator.java:66` + `SingleOutputStreamOperatorImpl.java:72` + `datastream/SideOutputDataStream.java:39`
- **证据片段**:
  - 文档（user-guide.md:73）：
    ```
    能力边界（build 期 fail-fast，不静默降级）：`union`/`connect`/侧输出提取的运行时
    API 尚未提供——XDSL 中 `<union>`/`<sideOutput>` 元素在 build 期显式报错，
    Java API 无对应方法。
    ```
  - 代码现实（DataStream.java:157 + DataStreamImpl.java:158）：
    ```java
    DataStream<T> union(DataStream<T>... streams);   // Javadoc: "Flink union semantics, WI6"

    public DataStream<T> union(DataStream<T>... streams) {
        ...
        UnionTransformation<T> unionTransform = new UnionTransformation<>(...);
        environment.addTransformation(unionTransform);
        return new DataStreamImpl<>(environment, unionTransform);
    ```
  - 代码现实（flow 侧 XDSL `<union>` 已真实构建，AdvancedTransforms.java:629+）：
    ```java
    // union — WI6: real multi-input merge over the declared edges
    private static Object buildUnion(StreamModelDslBuilder owner, StreamTransformModel t, ...) {
        ...
        merged = merged == null ? input : merged.union(input);
    ```
  - 代码现实（侧输出提取 API 已存在，SingleOutputStreamOperatorImpl.java:72 + SideOutputDataStream.java:39，跨 task 线协议经 `TYPE_SIDE_OUTPUT_RECORD` 落地）：
    ```java
    public <X> DataStream<X> getSideOutput(OutputTag<X> outputTag) { ... }
    // SideOutputDataStream javadoc: "returned by getSideOutput(OutputTag)... Consumption registers
    // a consumer ... delivered to the registered sink."
    ```
- **严重程度**: P1
- **现状**: 三个否定全错两个半：① `DataStream.union` 已实现（WI6）且有 XDSL `<union>` 端到端测试（`nop-stream-flow` `TestUnionPipelineE2E` / `TestSelfUnionPipelineE2E`）；② 侧输出提取 Java API（`getSideOutput` + `SideOutputDataStream` + 跨 task 转发）已交付（plan 369 Phase 2 G-2+09e①、HG-01 线协议，core-design.md:360-376 有完整落地记录）；③ 仅「side-output 视图上再接下游 graph 级 transform」仍 fail-fast（SideOutputDataStream Javadoc 明示 ERR_STREAM_UNSUPPORTED），XDSL `<sideOutput>` 也仍 fail-fast——即只有这条残余边界为真。
- **风险**: 门面文档把已交付能力声明为不存在，用户（与后续 AI 开发）会绕开 union/侧输出设计合法路径改用 flatMap 手工合流等劣化方案；与同文档 263-264 行竞品对照表（"union" 语境）及 INDEX 路由共同误导。
- **建议**: 重写 user-guide.md:73 能力边界段——删除 union/侧输出提取的「尚未提供」，改为：union 全支持；侧输出提取（getSideOutput/SideOutputDataStream）支持、但其视图上的下游 graph 级 transform 仍 fail-fast（ERR_STREAM_UNSUPPORTED）；XDSL `<union>` 支持、`<sideOutput>` 仍 fail-fast。
- **信心水平**: 确定
- **误报排除**: 已确认不是「接口有但 impl 抛异常」——DataStreamImpl.union 为真实实现（UnionTransformation 进执行图），TestUnionPipelineE2E 存在于 flow 模块测试目录；getSideOutput 在 SingleOutputStreamOperatorImpl 有实现体。
- **复核状态**: 未复核

### [DC-2] flow 侧 `<sideOutput>` fail-fast 文案与 core 现实脱节（声称 getSideOutput「尚未实现」）
- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:787-800 ↔ nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SingleOutputStreamOperatorImpl.java:72`（跨模块：flow ↔ core 契约面）
- **证据片段**:
  - flow（AdvancedTransforms.java:89-92 Javadoc + :792-799 抛错文案）：
    ```java
    * {@code <sideOutput>} still fails fast because the underlying
    * {@code nop-stream-core} runtime does not yet expose
    * {@code SingleOutputStreamOperator.getSideOutput}; that is tracked as a runtime-API-gap
    ...
    throw new StreamException(ERR_STREAM_NOT_IMPLEMENTED)
            .param(ARG_DETAIL, "Stream DSL <sideOutput id='" + m.getId() + "' tag='" + m.getTag()
                    + "'> requires SingleOutputStreamOperator.getSideOutput(OutputTag) which is "
                    + "not yet implemented in nop-stream-core runtime. ...
    ```
  - core 现实（接口 + 实现均在）：
    ```java
    <X> DataStream<X> getSideOutput(OutputTag<X> outputTag);   // 接口 :66
    public <X> DataStream<X> getSideOutput(OutputTag<X> outputTag) { ... }  // 实现 :72
    ```
- **严重程度**: P2
- **现状**: XDSL `<sideOutput>` fail-fast 本身仍是正确行为（side-output 视图尚不可作为 graph 级下游顶点部署），但拒绝理由已失真：core 已交付提取 API（plan 369 Phase 2），真实残余缺口是「side-output 视图上的下游 transform 不可部署」（SideOutputDataStream Javadoc 自述）。fraud-s1-cdc.stream.xml:12 的头注释（"<union> 为 build 期 fail-fast"）同样是过时口径的连带残留。
- **风险**: typed 错误文案直接面向用户，指向一个已不存在的缺口；后续维护者按该文案规划「等 core 实现」会空等。
- **建议**: 更新 AdvancedTransforms Javadoc 与 ERR_STREAM_NOT_IMPLEMENTED 文案为真实边界（下游 graph 级部署缺口），并在 WI/plan 面把 XDSL `<sideOutput>` 的放行与 `SideOutputDataStream` 可部署化对齐。
- **信心水平**: 确定
- **误报排除**: 已确认 `<sideOutput>` 在 buildUnion 同文件确实仍抛错（非本审计误读）；fail-fast 行为本身不判错，判错的是其引用的「API 不存在」事实。
- **复核状态**: 未复核

### [DC-3] connectors 文档 SPI 表被「Source API 冻结政策」章节从中间截断，sink 4 行成孤儿行
- **文件**: `docs-for-ai/03-modules/nop-stream-connectors.md:27-33`
- **证据片段**:
  ```markdown
  | source | `batch-loader` | `BatchLoaderSourceFunction` | ...工厂... | `connector-batch.beans.xml` |

  ## Source API 冻结政策（plan 369 Phase 5）

  `SourceFunction`（...）为 **legacy 冻结代**：...不在 connectors 使用文档范围内。
  | sink | `file` | `FileTwoPhaseCommitSink` | `io.nop.stream.connector.file.FileTwoPhaseCommitSinkConnectorFactory` | `connector-file.beans.xml` |
  | sink | `message` | ... （其余 3 行 sink 亦悬空）
  ```
- **严重程度**: P2
- **现状**: 8 工厂矩阵是本页核心交付物（自述「代码级单一事实源」由 `TestStreamConnectorRegistryDiscovery` 钉定），但 markdown 表在 source 4 行后被 `##` 二级标题截断，sink 4 行脱离表头渲染为断裂段落；「本期 8 个内建工厂均无别名」的完整性声明与可见形态不符。
- **风险**: 渲染后 sink 半表无列名，读者易漏读 jdbc-2pc/batch-consumer 行；脚本化抽取该矩阵（文档即契约的消费方式）会丢一半行。
- **建议**: 将「Source API 冻结政策」节移到 SPI 表之后（或并入「注册资格集」段），恢复 8 行完整一张表。
- **信心水平**: 确定
- **误报排除**: 非 markdown 渲染器差异——源文件层面表行与 `##` 标题交错即是结构错误；8 个工厂类本体均已逐一验证存在（本审计全部命中）。
- **复核状态**: 未复核

### [DC-4] connectors 文档「基类默认抛 UnsupportedOperationException」与实际抛出的 StreamException 不符
- **文件**: `docs-for-ai/03-modules/nop-stream-connectors.md:96 ↔ nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:128-136`
- **证据片段**:
  - 文档（connectors.md:96）：
    ```
    - **第三方子类契约**：未 override `copyForSubtask(int)` 的用户子类在 P>1 部署期构建
      subtask 拷贝时 **fail-fast**（基类默认抛 `UnsupportedOperationException`，No-Silent-No-Op；...
    ```
  - 代码现实（TwoPhaseCommitSinkFunction.java:128-136）：
    ```java
    public TwoPhaseCommitSinkFunction<IN> copyForSubtask(int subtaskIndex) {
        if (subtaskIndex > 0) {
            throw new StreamException(ERR_STREAM_UNSUPPORTED).param(ARG_OPERATION, getClass().getName() + " does not implement copyForSubtask(int). ..."
        }
        return this;
    }
    ```
- **严重程度**: P2
- **现状**: 契约行为（P>1 部署期 fail-fast）属实，但异常类型文档写错——实际抛框架 typed `StreamException(ERR_STREAM_UNSUPPORTED)`；该错误同时存在于 TwoPhaseCommitSinkFunction 的 Javadoc（:118/:125 `@throws UnsupportedOperationException`），文档大概率从 Javadoc 转抄。
- **风险**: 按文档 catch `UnsupportedOperationException` 的用户代码不会命中；错误处理教程/测试断言若照抄会写错断言类型。
- **建议**: 修 connectors.md:96 与 TwoPhaseCommitSinkFunction Javadoc 为 `StreamException(ERR_STREAM_UNSUPPORTED)`。
- **信心水平**: 确定
- **误报排除**: 全文件 grep 确认类体内无任何 `throw new UnsupportedOperationException`；文档所谓异常类型仅存于 Javadoc 描述层。
- **复核状态**: 未复核

### [DC-5] nop-stream/README.md 声称「独立进程入口 JobCoordinatorMain/TaskManagerMain」已实现于 runtime——与 owner doc/user guide 的 test-scope 诚实化（F-11）矛盾
- **文件**: `nop-stream/README.md:7 ↔ docs-for-ai/03-modules/nop-stream.md:242` + `docs-for-ai/03-modules/nop-stream-user-guide.md:187` + `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/launch/JobCoordinatorMain.java`
- **证据片段**:
  - README（:7）：
    ```
    ...并有独立进程入口 `JobCoordinatorMain`/`TaskManagerMain` 与多 JVM 测试
    `TestMultiJvmExactlyOnceRecovery`。尚未实现的是 K8s/YARN 集群部署编排与 HPA 弹性伸缩。
    ```
  - owner doc（nop-stream.md:242）：
    ```
    **⚠ 入口类作用域限制（F-11 诚实化）**：`JobCoordinatorMain` / `TaskManagerMain` 位于
    `nop-stream-runtime` 的 **test scope**（`src/test/java/.../launch/`，经 test-jar 导出）——
    **发布 runtime jar 的类路径上没有这两个入口类**，按本节命令直接 `java ...JobCoordinatorMain`
    会 `ClassNotFoundException`。
    ```
- **严重程度**: P2
- **现状**: 两入口类确实只存在于 `src/test/java/.../launch/`（本审计已验证 main 侧无 launch 目录），runtime pom 经 test-jar 导出（pom.xml:91,184-189 注释自述）。owner doc 与 user guide 均有完整的 F-11 披露与三种 workaround；README 作为模块门面却保留旧口径且无任何 test-scope 标注。
- **风险**: 只读 README 的用户按「独立进程入口已实现」直接启动发布 jar → ClassNotFoundException；与 R5 系列其他报告强调的「门面一致性」冲突。
- **建议**: README:7 加一句与 owner doc 同口径的 test-scope 限制说明（或直接链接 owner doc「运维手册」首注）。
- **信心水平**: 确定
- **误报排除**: 不是措辞歧义——README 明写「独立进程入口…已实现于 nop-stream-runtime」并列在「已实现」清单中；类路径事实以 main/test 目录布局为准。
- **复核状态**: 未复核

### [DC-6] nop-stream/README.md 与 INDEX.md 的「子模块共 10 个」过时——pom.xml 实为 11 个（nop-stream-sql 缺席两处清单）
- **文件**: `nop-stream/README.md:24 + docs-for-ai/INDEX.md:249 ↔ nop-stream/pom.xml:17-27 + docs-for-ai/01-repo-map/module-groups.md:23`
- **证据片段**:
  - README（:24）：`子模块清单与 nop-stream/pom.xml <modules> 一一对应（共 10 个）；...`
  - INDEX.md（:249）：`...与 nop-stream/pom.xml <modules> 一一对应共 10 个，权威口径见 01-repo-map/module-groups.md...`（且枚举串同样无 nop-stream-sql）
  - 代码现实（pom.xml）：11 个 `<module>`（含 `nop-stream-sql`，:25）；module-groups.md:23 已写「共 11 个」并把 `nop-stream-sql`（SQL 编译宿主模块，WI8b 起骨架）列入。
- **严重程度**: P2
- **现状**: README 模块表与 INDEX 尾部枚举均停在 10 模块时代；README 自称「与 pom.xml 一一对应」即自证为假。INDEX 一边写 10 个一边指向「权威口径 = module-groups（11 个）」，形成自引用矛盾。
- **风险**: 模块计数是其他审计/脚本常用的一致性锚点；nop-stream-sql 缺席门面清单会低估模块面（本模块已有独立文档族与 JMH 基准）。
- **建议**: README 表补 `nop-stream-sql` 行并改「共 11 个」；INDEX.md:249 同步；顺手在 README 补链接 `03-modules/nop-stream-sql.md`。
- **信心水平**: 确定
- **误报排除**: `grep -c "<module>" nop-stream/pom.xml` = 11（可复现）；module-groups.md 与 INDEX 两文档同仓不同值，非渲染问题。
- **复核状态**: 未复核

### [DC-7] owner doc「模块路由」表缺 `nop-stream-sql` 行
- **文件**: `docs-for-ai/03-modules/nop-stream.md:9-17 ↔ nop-stream/pom.xml:25`
- **证据片段**:
  - 文档（nop-stream.md:9-17）：
    ```
    | 子模块 | 职责 |
    | `nop-stream-core` | ... |
    | `nop-stream-runtime` | ... |
    | `nop-stream-cep` | ... |
    | `nop-stream-rocksdb` | ... |
    | `nop-stream-connector{,-batch,-jdbc,-debezium}` | 连接器族 |
    | `nop-stream-flow` | XDSL 流程集成 |
    | `nop-stream-fraud-example` | ... |
    ```
  - 代码现实：pom.xml `<module>nop-stream-sql</module>`（:25）；sql 模块有独立 owner 文档 `nop-stream-sql.md` 且 INDEX.md:139 已路由，唯独 owner doc 模块路由表未覆盖。
- **严重程度**: P3
- **现状**: owner doc 自我定位为「模块路由 + 运维契约」入口，模块表漏第 11 个子模块；sql 的 `<sql>` 声明反而出现在 xdef/flow 主管线内，路由断链影响从 owner doc 出发的读者。
- **风险**: 低——INDEX/user-guide 末行均可路由到 sql 文档；但 owner doc 表作为「子模块权威行」与其他模块清单（README/module-groups）三处两值，扩大 DC-6 的口径分裂。
- **建议**: 模块路由表补一行：`nop-stream-sql`（声明式 SQL 编译宿主，WI8b+），链接 `03-modules/nop-stream-sql.md`。
- **信心水平**: 确定
- **误报排除**: 表格其余 7 行职责描述经抽查（core/runtime/cep/rocksdb/connector 族）与实际包结构一致，问题仅在缺行。
- **复核状态**: 未复核

### [DC-8] user-guide「可用 transform 类型」清单缺 `join` 与 `union`——两者均已落地可执行
- **文件**: `docs-for-ai/03-modules/nop-stream-user-guide.md:116 ↔ nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:202,227` + `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java`（buildJoin/buildUnion）
- **证据片段**:
  - 文档（user-guide.md:116）：
    ```
    可用 transform 类型：`source`、`timestampsAndWatermarks`、`map`、`flatMap`、`filter`、
    `keyBy`（`keyExpr`）、`window`（`strategyRef`）、`aggregate`、`reduce`、`process`、
    `cep`（`patternRef`）、`sink`、`custom`（customType）。
    ```
  - 代码现实（stream.xdef:202,227）：
    ```xml
    <join joinRef="!string" xdef:name="StreamJoinModel" xdef:ref="StreamTransformModel"/>
    ...
    <union xdef:name="StreamUnionModel" xdef:ref="StreamTransformModel"/>
    ```
- **严重程度**: P2
- **现状**: xdef 合法 transform 共 15 种（含 `join`、`union`、`sideOutput`），文档列 13 种；`join`（WI8d/WI13，`EquiJoinOperator` + `TestParameterizedJoinModel`/`TestEquiJoinParallelismInvariant`）与 `union`（WI6，见 DC-1）均已实现且 build 通过，唯 `<sideOutput>` fail-fast（文档未列它不算错）。连带：`<edges>` 侧 `flowControlPolicy/queueCapacity/receiveWindow/packetSize`、`<aggregators>`/`<joins>`/`<schemas>`/`<coders>`/`<sideInputs>` 注册表等 xdef 面也未在 user-guide 出现（部分属 deep-cut，不单独立条）。
- **风险**: 用户按 13 种清单设计拓扑会漏掉等值 join 与多流合并两类一等能力；与 owner doc「提交前校验」的错误样例（bean/joinRef 校验）呼应面变窄。
- **建议**: 清单补 `join`（`joinRef`，引用 `<joins>` 注册表）与 `union`，并注明 `sideOutput` 为 build 期 fail-fast 的显式例外。
- **信心水平**: 确定
- **误报排除**: join/union 非「声明了但会 fail-fast」的半成品——buildJoin/buildUnion 均为真实构建路径且有专项 E2E 测试。
- **复核状态**: 未复核

### [DC-9] distributed-runbook §7「已知边界」两条已过时：manifest checksum/version 已落地；队列水位 gauge 已落地（与本文件 §6.1 自相矛盾）
- **文件**: `ai-dev/design/nop-stream/distributed-runbook.md:158-159 ↔ nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:70-73` + `同 runbook:120`（`nop_stream_io_channel_queue_size`）
- **证据片段**:
  - runbook（:158-159）：
    ```
    - **checkpoint manifest 无 checksum/版本字段**（Follow-up item 25）：恢复断言以「最新
      durable epoch manifest + 结果无重复无丢失」为准，不断言 manifest 校验行为。
    - **背压无直接量化指标**（...；均归 Follow-up 32）
    ```
  - 代码现实（CheckpointSerDe.java:70-73，Stage 51/F-10b 已落地）：
    ```java
    static final String STATE_FORMAT_VERSION_KEY = "stateFormatVersion";
    /** Manifest integrity checksum field key. Always the LAST key in the document. */
    static final String CHECKSUM_KEY = "checksum";
    ```
  - 同文件内部矛盾（runbook:120，item 32 已收口）：`通道水位直测：TM 面 nop_stream_io_channel_queue_size{...}（...gauge...）`
- **严重程度**: P2
- **现状**: §7 是「已知边界（诚实披露）」清单，但 item 25（manifest checksum+版本）与 item 32（TM 指标端点 + channel queue gauge）均已交付：owner doc:113 已完整记录 checksum 语义与两个 typed 错误码；§6.1/§6.4 甚至就在同一文件内记载了 item 32 交付物。§7 两条未随 roadmap 勾销而更新。
- **风险**: 「已知边界」清单是容量规划与评审输入；过期条目会让后续评审重复立项已关闭的 Follow-up，或误判引擎仍无完整性校验。
- **建议**: §7 删除/改写这两条为「已交付（item 25/item 32）」，保留其余仍开放的条目（如多 JVM gated 偶发超时）。
- **信心水平**: 确定
- **误报排除**: checksum/gauge 交付有代码与测试双重证据（`TestCheckpointBodyChecksum`、`TestTaskManagerOpsEndpoint`）；§6.1 与 §7 同文件矛盾排除了「两处指不同对象」的可能。
- **复核状态**: 未复核

### [DC-10] stream-dsl-design 的 `<union>` 行仍是「runtime-API-gap 未实现」旧口径（无落地更新标记）
- **文件**: `ai-dev/design/nop-stream/stream-dsl-design.md:130 ↔ nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:629-682`
- **证据片段**:
  - 设计文档（stream-dsl-design.md:130）：
    ```
    | `<union>` | `dataStream.union(other1, other2)` | edges 多入边识别 | **runtime-API-gap**：
    `DataStream.union()` 尚未在 `nop-stream-core` 实现（多输入算子），builder fail-fast 抛
    `UnsupportedOperationException`（见 plan Deferred 节） |
    ```
  - 代码现实：`DataStreamImpl.union` 真实实现（WI6），`buildUnion` 真实多入边合并，拒绝路径仅剩「HASH 边直入 union」与「零入边」，且抛 `StreamException(ERR_STREAM_*)`。
- **严重程度**: P2
- **现状**: 同目录 core-design.md 对 side output 采用了「更新（Cycle 2 / I4 …）已落地」的增量更新惯例，stream-dsl-design 的 union 行未跟进，三个断言（未实现/Deferred/抛 UnsupportedOperationException）全部过时。
- **风险**: 设计文档是「声明 vs 实现」对账基准；该行会让审计/排期把 WI6 误当未完成项，或按旧异常类型写断言。
- **建议**: 按本目录惯例在该行后补落地更新注记（WI6 已实现、锚点 TestUnionPipelineE2E、异常类型 StreamException），不删除历史表述。
- **信心水平**: 确定
- **误报排除**: 未把「设计文档允许滞后」当发现——判据是该目录自身的更新惯例（core-design 同期已更新同类条目）与三个可验证事实全错。
- **复核状态**: 未复核

### [DC-11] CDC cookbook 的 `TestDebeziumEngineProperties` 锚点模块归属错位（在 nop-message-debezium，非 connector-debezium）
- **文件**: `docs-for-ai/03-modules/nop-stream-cdc-cookbook.md:9-23 ↔ nop-message/nop-message-debezium/src/test/java/io/nop/message/debezium/TestDebeziumEngineProperties.java`
- **证据片段**:
  - 文档（cookbook:3-5 定位段）：`定位：Debezium CDC source（DebeziumCdcSourceFunction，nop-stream-connector-debezium）...本文关键声称带 live 行为核对锚点（测试名），均可通过模块测试复现`
  - 代码现实：`find . -name TestDebeziumEngineProperties.java` 唯一命中 `nop-message/nop-message-debezium/...`；`nop-stream-connector-debezium/src/test/` 下无此文件。`testConnectorWhitelist`/`testProcessingThreadsPinned`/`testOffsetStorageOverrideRejected` 等方法均在 nop-message-debezium 侧。
- **严重程度**: P3
- **现状**: 引擎属性白名单/守卫的强制点（`DebeziumEngineConfig`）与测试都在 nop-message-debezium，stream 侧经 `DebeziumEngineWrapper` 间接受益——跨模块契约本身成立（本审计已验证链路），但 cookbook 声称锚点「可通过（connector）模块测试复现」，读者在 nop-stream-connector-debezium 下跑不到该测试。
- **风险**: 低——核对锚点是本页方法论承诺；模块归属错位会消耗复现者时间，也可能让 connector 模块的重构者误删跨模块依赖。
- **建议**: cookbook 首注补一句「引擎面锚点（TestDebeziumEngineProperties）位于 nop-message/nop-message-debezium」；connectors.md:87 同名锚点顺带标注。
- **信心水平**: 确定
- **误报排除**: 全仓 find 确认该测试类唯一；ERR_DEBEZIUM_* 错误码定义与抛出点均在 nop-message-debezium（DebeziumErrors.java:24-39、DebeziumEngineConfig.java:83,129,137,164,185,193），cookbook 故障表的错误码本身全部真实可达（未判错）。
- **复核状态**: 未复核

### [DC-12] user-guide XDSL 示例代码块内嵌两段 markdown 正文，示例原样不可解析
- **文件**: `docs-for-ai/03-modules/nop-stream-user-guide.md:89-114`（markdown 正文位于 :97-99）
- **证据片段**:
  ```xml
  <stream name="my-pipeline" x:schema="/nop/schema/stream/stream.xdef" ...>
    <checkpoint .../>
    <windowingStrategies>
      <strategy strategyId="tx-window" windowFnId="txWindowAssigner"/>
    </windowingStrategies>

  **参数化窗口声明（WI10）**：`windowFnId="tumbling-event-time"` 搭配 `duration` ...
  **allowedLateness 声明（WI10/D9）**：strategy 级属性或 window 节点级子元素均可声明。...
    <transforms>
  ```
- **严重程度**: P3
- **现状**: ```xml 围栏内混入两个 markdown 段落（WI10 参数化窗口、allowedLateness 说明），示例非合法 XML；两条说明内容本身已与 xdef（`duration="string"`、`allowedLateness="!long=0"`、window 节点级 `<allowedLateness>` 子元素）核对一致，仅排版错位。
- **风险**: 复制示例即报 xdef 解析错；后续自动化抽样「文档示例 → xdef 校验」会把该页整块判红。
- **建议**: 把两段说明移出围栏（置于示例后），示例补 `duration="2s"` 用法注释化或留合法最小样例。
- **信心水平**: 确定
- **误报排除**: 逐行核对确认 ``` 围栏起于 :89 止于 :114，两段正文确在其间；非渲染器容错问题。
- **复核状态**: 未复核

### [DC-13] user-guide 声称 Trigger 家族目录「10 文件」——实际 11 文件（新增抽象基类 ContinuousIntervalTrigger）
- **文件**: `docs-for-ai/03-modules/nop-stream-user-guide.md:213 ↔ nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/windowing/triggers/`（11 个 .java）
- **证据片段**:
  - 文档（:213）：`` `io.nop.stream.core.windowing.triggers`（10 文件）+ `evictors` + `operators/Triggerable`：``
  - 代码现实：目录含 `ContinuousIntervalTrigger.java`（abstract 包私有基类，item 21 D-4 收敛产物）+ 既有 10 文件。
- **严重程度**: P3
- **现状**: 该表自称「live 全量」；新增的抽象基类非用户面 Trigger（表按公开类型枚举仍完备——8 concrete trigger + base 契约），仅文件计数过时；同页 263 行「8 trigger + 3 evictor」的公开类型口径仍准确。
- **风险**: 极低——「live 全量」字样与计数不符，逐文件对账的审计会出一条假阳性差异。
- **建议**: 改为「10 文件 + 1 抽象基类（ContinuousIntervalTrigger）」或去掉文件计数只保留类型表。
- **信心水平**: 确定
- **误报排除**: 连 `target/` 在外的 main 目录实测 11 文件；ContinuousIntervalTrigger 为包私有 abstract，不影响公开类型计数（故未升级 P2）。
- **复核状态**: 未复核

### [DC-14] user-guide 称窗口算子工厂「ServiceLoader 风格发现」——实际为硬编码 Class.forName 反射
- **文件**: `docs-for-ai/03-modules/nop-stream-user-guide.md:71 ↔ nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/WindowedStreamImpl.java:198-215`
- **证据片段**:
  - 文档（:71）：`窗口算子执行需要 nop-stream-runtime 在 classpath（缺失时 fail-fast 报 "WindowOperator requires nop-stream-runtime on classpath"，由 ServiceLoader 风格发现的 IWindowOperatorFactory 接线）`
  - 代码现实（WindowedStreamImpl.java:202-206）：
    ```java
    Class<?> factoryClass = Class.forName(
            "io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl");
    defaultFactory = (IWindowOperatorFactory) factoryClass.getDeclaredConstructor().newInstance();
    ```
- **严重程度**: P3
- **现状**: 用户可见契约（runtime 在 classpath 才可窗口执行、缺失 fail-fast）准确；「ServiceLoader 风格」措辞与实现不符——checkpoint 工厂才是真 ServiceLoader（META-INF/services 已验证），窗口工厂是写死 FQCN 的反射发现，无 services 文件、无多实现消歧面。
- **风险**: 低——但两机制的可替换性/可扩展性含义不同（写死 FQCN 意味着第三方无法以 ServiceLoader 方式替换窗口算子实现），误导扩展者。
- **建议**: 措辞改为「经固定 FQCN 反射发现 `WindowOperatorFactoryImpl`（runtime 在 classpath 即生效）」，与 :37 行 checkpoint 工厂的真 ServiceLoader 描述区分。
- **信心水平**: 确定
- **误报排除**: runtime/core 的 META-INF/services 均已列举，无 IWindowOperatorFactory 条目；`setDefaultFactory` 显式 setter 通路存在但不改变默认发现的实现方式。
- **复核状态**: 未复核

---

## 验证为一致的关键面（抽样清单，均逐项命中）

- **指标名表**：owner doc 22 个 `nop.stream.*` 指标名（engine 7 / task 7 含 ackSendFailures.total·commitQueueDepth·commitCallerRuns.total / operator 3 / io 4 / state 6 族）与 main 代码字面量逐一相符；`numLateRecordsDropped` 命名例外声明属实（WindowOperator/CepOperator/WindowedStream 三处）。复现：`grep -rhn '"nop\.stream\.[a-z.]*"' nop-stream --include='*.java' | grep -v test`
- **REST 运维**：`/metrics`、`/jobs` GET/POST、`/jobs/{id}/stop?mode=`、`/jobs/{id}`、`/jobs/{id}/checkpoints`、`/jobs/{id}/threaddump` 与 StreamOpsHttpServer 路由一致；401/404/409/503 语义与 F-09b/F-09a 描述一致。
- **配置键与默认值**：`nop.stream.ops.http.{enabled,port=8901,bind=127.0.0.1,token}`、`nop.stream.ops.metrics.enabled=true`、`nop.stream.metrics.log.*`、`nop.stream.alert.*`（5000/2/200）、`nop.stream.ops.checkpoint-history.*`（100/1440）、`job-record.retention-minutes=1440`、`governance.cleanup-interval-ms=300000` 全部与 StreamOpsConfig/WebhookAlertChannel/StreamGovernanceConfig 常量一致。
- **CheckpointConfig 默认值 12 项**（60000/600000/30000/500/1/5/3/3/true/1000/true/STRICT_EXACTLY_ONCE）逐项相符；ProcessingGuarantee 枚举二值相符。
- **健康状态机/事件**：七态枚举、终态判定、10 个 StreamJobEvent.EventType、三个日志前缀锚点（`nop-stream job event:` / `job health transition:` / `nop-stream alert:`）全部命中。
- **错误码**：11 个 ERR_STREAM_* + 8 个 nop.err.stream.* + 2 个 nop.err.eql.* + ERR_DEBEZIUM_CONFIG_INVALID/UNSUPPORTED_CONNECTOR_TYPE（定义于 nop-message-debezium，经 DebeziumEngineWrapper 链路可达）全部存在；`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 确已删除（与文档「随门禁移除删除」一致）。
- **连接器 SPI**：8 工厂 FQCN + 5 个 connector-*.beans.xml + `StreamConnectorCatalog`（discoverConnectorBeansResources/renderListing/probe/probeConnectivity）+ `StreamConnectorConfig(typeName, params)` + `BeanContainerBuilder(IBeanContainer)` 构造签名全部相符；ledger `stream_epoch_ledger_v2` + `(sink_namespace, epoch_id, subtask_id)` + `getLedgerTableDDL()` 相符。
- **XDSL/xdef**：示例属性（checkpoint/storageType/strategy/windowFnId/duration/allowedLateness/edge partition=FORWARD|HASH/timestampsAndWatermarks watermarkInterval）与 stream.xdef 合同一致；`<sql>` 顶层元素与唯一内容约束一致。
- **SQL**：`StreamSqlCompiler.compile(loc, sql, schema, sinkBean)` 四参签名、MANAGED_TYPES 九类型闭集、`StreamSqlExprCompiler`/`StreamSqlAggregations`、`app-sql-compiler.beans.xml` 自动注册全部相符。
- **触发语义矩阵**：countWindow(size)/(size,slide) 的 PurgingTrigger+CountTrigger / CountTrigger+CountEvictor+GlobalWindows 接线、GlobalWindows NeverTrigger、WatermarkStrategy 三个静态工厂 + withIdleness、assignTimestampsAndWatermarks 双参重载全部相符。
- **版本轴**：checkpoint formatVersion=2（CURRENT_FORMAT_VERSION）、RocksDB keyLayoutVersion=2、StorageJobIds/`nop-stream.checkpoint.storage.dir`/`${java.io.tmpdir}/nop-stream-checkpoints` 相符。
- **入口/测试面**：JobCoordinatorMain/TaskManagerMain 确在 test scope（F-11 属实）；multi-jvm/kafka/pulsar gate 属性、`io.nop.stream.runtime.multijvm.*` 与 `*MultiJvm*` 通配均能命中实际测试；quickstart `QuickstartSupport.registerCheckpointExecutorFactory()`、fraud-example 3 个 .stream.xml、`UserHistoryEnricher`、runtime test-jar 导出配置全部命中。
- **跨模块契约**：nop-message-debezium ↔ connector-debezium（DebeziumConfig 共享 + NopStreamOffsetBackingStore 静态 registry + credential 四字段 databaseUser/databasePassword/schemaHistoryJdbcUser/schemaHistoryJdbcPassword 引擎侧瞬态解密）成立；core ServiceLoader SPI（ICheckpointExecutorFactory/IDeploymentPlanProvider services 文件）成立。

## 检查范围

**全文读取**：`docs-for-ai/03-modules/nop-stream.md`（281 行全读）、`nop-stream-user-guide.md`（269 全读）、`nop-stream-connectors.md`（117 全读）、`nop-stream-cdc-cookbook.md`（92 全读）、`nop-stream-migration-guide.md`（103 全读）、`nop-stream-sql.md`（59 全读）、`nop-stream/README.md`（50 全读）。
**定位读取**：`module-groups.md` nop-stream 行（L22-23）+ By Code Location（L112-114）；`source-anchors.md` nop-stream 锚点节（L245-305，56 条锚点逐条路径比对存在性）；`INDEX.md` nop-stream 路由（L135-145、L249）；`nop-kernel/nop-xdefs/.../stream.xdef`（278 行全读）；`ai-dev/design/nop-stream/` 下 window-failfast-decisions、sql-landing-decision、distributed-runbook 全文，stream-dsl-design（transform 表节）、01-architecture-baseline（管线阶段/D71 节）、core-design（侧输出节）、connector-design/cdc-design（经 cookbook 锚点间接核对，未逐行全读）。
**声明核对量**：约 130 项可检验声明——22 指标名 + 15 配置键 + 6 REST 端点 + 12+ 默认值 + 7 健康态 + 10 事件 + 3 日志前缀 + 21 错误码 + 8 SPI 工厂/5 beans.xml + 15 xdef transform + 9 SQL 类型 + 8 窗口接线行 + 4 版本轴值 + 3 入口类作用域 + 5 门禁属性 + ~60 个被引用测试类（全部 find 命中，零缺失）+ 4 个跨模块契约链。核对方式为 grep/类路径定位与签名比对（未运行构建/测试）。

---

## 维度复核结论

- **复核人**: R5 独立维度复核子代理（文档-代码一致性 + 跨模块契约维度）
- **复核日期**: 2026-10-05
- **复核方法**: 对 DC-1..DC-14 逐条独立重验——文档行号与引用原文逐字 Read/grep 比对；代码现实（接口/实现体/异常类型/xdef 声明/pom modules/目录清单/META-INF/services/测试类存在性与断言面）逐一 Read/grep/find 定位核对。重点条目 DC-1（TestUnionPipelineE2E 静态断言面 + XDSL union 声明可执行 + 文档否定原文）与 DC-5（README 原文 vs launch 包 test-scope 布局）做了全链复核。只读分析，未运行 maven/测试。
- **复核结果**: **14 条全部保留，无降级、无驳回**。三条次要瑕疵（不影响任何裁决与定级）记录于对应条目。

### 逐条裁决

| 编号 | 裁决 | 复核理由 |
|---|---|---|
| DC-1 | **保留 P1** | 承重证据全链独立命中：user-guide.md:73 原文逐字为三重否定（`union`/`connect`/侧输出提取「尚未提供」），且 union 为全页唯一提及点（grep 证实仅 :73 一处）；`DataStream.union` 声明于 DataStream.java:157（Javadoc 明标 WI6）、DataStreamImpl.java:158-177 真实构建 `UnionTransformation` 并注册进执行图（非「接口有但 impl 抛异常」）；XDSL `<union>` 经 AdvancedTransforms.java:125-126 dispatch→`buildUnion`:642 真实多入边合并（:682 `merged.union(input)`）；`TestUnionPipelineE2E`/`TestSelfUnionPipelineE2E` 存在于 nop-stream-flow 测试目录，前者为 XDSL 全管线断言（`DslModelParser`→`StreamModelDslBuilder`→`execute`→sink 多重集断言 7 元素）；侧输出提取 `getSideOutput` 接口（SingleOutputStreamOperator.java:66）/实现（Impl:72-74）/`SideOutputDataStream`（Javadoc 自述残余边界 ERR_STREAM_UNSUPPORTED）均在；flow 侧 `<sideOutput>` 仍 fail-fast（buildSideOutput :792-800）。「三个否定全错两个半」定性准确。次要瑕疵：风险段「与同文档 263-264 行竞品对照表（union 语境）共同误导」一句不准——:263-264 为 Trigger 家族对照行，全页 union 仅 :73 一处；不改变发现本体与 P1 定级。 |
| DC-2 | **保留 P2** | AdvancedTransforms Javadoc（:89-93）与 ERR_STREAM_NOT_IMPLEMENTED 文案（:793-798）逐字核对确声称 core「does not yet expose getSideOutput」，而 core 的接口 :66 / 实现 :72-74 均在——拒绝理由失真成立；「fail-fast 行为本身不判错，判错的是其引用的事实」边界划法正确。连带声明 fraud-s1-cdc.stream.xml:12 头注释「`<union>` 为 build 期 fail-fast」过时口径核对属实。 |
| DC-3 | **保留 P2** | connectors.md 源文件层面：source 4 行（:22-25）后被 :27 `## Source API 冻结政策` 截断，sink 4 行（:30-33）紧贴 :29 段落之后、无表头成孤儿行——结构错误成立，非渲染器差异；:18「本期 8 个内建工厂均无别名」完整性声明核对属实。 |
| DC-4 | **保留 P2** | connectors.md:96 逐字为「基类默认抛 `UnsupportedOperationException`」；TwoPhaseCommitSinkFunction.java 实际于 :130 抛 `StreamException(ERR_STREAM_UNSUPPORTED)`，Javadoc :118/:125 的 `@throws UnsupportedOperationException` 与审计引用行号精确吻合——「文档从 Javadoc 转抄错误」的推断成立。 |
| DC-5 | **保留 P2** | README:7 逐字确认将两入口类列入已实现清单且仅把 K8s/YARN/HPA 列为未实现；find 确认 `JobCoordinatorMain`/`TaskManagerMain` 仅存在于 `src/test/java/io/nop/stream/runtime/launch/`（main 侧 launch 包仅 ClusterLaunchConfig/ClusterPipelineFactory，main 全模块无其他 *Main）；runtime pom 含 test-scope `test-jar` 依赖（:91）与 `maven-jar-plugin` test-jar 导出 goal（:192-199）；owner doc:242 与 user-guide:187（「入口类现状（如实标注）」节）的 F-11 披露均在。矛盾真实，非措辞歧义。 |
| DC-6 | **保留 P2** | `nop-stream/pom.xml` 实测 11 个 `<module>`（nop-stream-sql 于 :25）；README:24「与 `<modules>` 一一对应（共 10 个）」自证为假；INDEX.md:249 枚举串确无 sql 且写「共 10 个」，却指向已更新为 11 的 module-groups.md（:23「共 11 个」+ sql 列入）——自引用矛盾成立。 |
| DC-7 | **保留 P3** | owner doc 模块路由表（:9-17）7 行确无 nop-stream-sql；`nop-stream-sql.md` 存在且 INDEX.md:139 已路由；表格其余行职责描述与包结构抽查一致，问题仅在缺行。P3 恰当。 |
| DC-8 | **保留 P2** | user-guide.md:116 清单实列 13 种、无 join/union；stream.xdef :202 `<join joinRef>`、:227 `<union>` 行号精确命中（`<sideOutput>` 于 :230）；buildJoin 经 dispatch（AdvancedTransforms.java:116-118）真实接线，join 测试 TestParameterizedJoinModel/TestEquiJoinParallelismInvariant 均存在于 flow 测试目录。次要瑕疵：现状段「xdef 合法 transform 共 15 种」计数不精——实为 16（文档已列 13 + join + union + sideOutput，逐元素清点）；不改变「join/union 缺席且已落地」的发现本体与 P2 定级。 |
| DC-9 | **保留 P2** | runbook §7 两条逐字命中（:158「manifest 无 checksum/版本字段（Follow-up item 25）」/ :159「背压无直接量化指标……队列水位直测 gauge 缺——均归 Follow-up 32」）；CheckpointSerDe.java:70 STATE_FORMAT_VERSION_KEY / :73 CHECKSUM_KEY 常量在位；runbook:120 同文件自记 item 32 的 `nop_stream_io_channel_queue_size` gauge 已交付——同文件内部矛盾成立；owner doc:113 已完整记录 Stage 51 checksum 语义与两个 typed 错误码；TestCheckpointBodyChecksum/TestTaskManagerOpsEndpoint 均存在。 |
| DC-10 | **保留 P2** | stream-dsl-design.md:130 逐字命中三重过时断言（`DataStream.union()` 尚未实现 / 见 plan Deferred 节 / 抛 `UnsupportedOperationException`）；`DataStreamImpl.union` 真实实现且抛 `StreamException(ERR_STREAM_*)`；同目录 core-design.md §6（:357-361）确认「更新（Cycle 2 / I4）/（HG-01）已落地」的增量更新惯例存在——「设计文档允许滞后」抗辩被该目录自身惯例有效排除；`ai-dev/design/` 为规范性文档，P2 定级成立。 |
| DC-11 | **保留 P3** | find 全仓确认 TestDebeziumEngineProperties 唯一命中 `nop-message/nop-message-debezium`，connector-debezium 测试目录无此类；cookbook:5「均可通过模块测试复现」的承诺与锚点模块归属错位成立；cookbook:9-11 引用的 testProcessingThreadsPinned/testConnectorWhitelist/testOffsetStorageOverrideRejected 均无模块标注，connectors.md:87 同名锚点同样无标注。P3 恰当。 |
| DC-12 | **保留 P3** | ```xml 围栏起于 :89 止于 :114，两段 markdown 正文（:97 参数化窗口 / :99 allowedLateness）确在围栏内，示例非合法 XML；「两条说明内容与 xdef 一致、仅排版错位」的定性经抽核成立（xdef duration/allowedLateness 属性面相符）。P3 恰当。 |
| DC-13 | **保留 P3** | triggers 目录实测 11 个 .java（含 ContinuousIntervalTrigger.java，:37 确认 `abstract class` 包私有）；user-guide:213「（10 文件）」+ :211「live 全量」字样核对属实；原发现「同页 263 行『8 trigger + 3 evictor』公开类型口径仍准确」经核对该行号本身正确（:263 表行确含该口径），文件计数过时与公开类型计数无涉的区分成立。P3 恰当。 |
| DC-14 | **保留 P3** | WindowedStreamImpl.java:206-209 确为写死 FQCN（`io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl`）的 `Class.forName` 反射；用户可见契约 fail-fast 文案（"WindowOperator requires nop-stream-runtime on classpath"，:235/:252/:272/:289）真实存在；META-INF/services 实测仅 runtime 侧 ICheckpointExecutorFactory 与 IDeploymentPlanProvider 两文件、无 IWindowOperatorFactory 条目——「checkpoint 工厂才是真 ServiceLoader」的对照成立；`setDefaultFactory` 显式通路存在（:194-196）但不改变默认发现实现。P3 恰当。 |

### 复核附带发现（非新条目，记录备查）

- **文件头部发现计数有误**：声明「P1 × 1，P2 × 8，P3 × 4（共 13 条）」，实际 DC-1..DC-14 共 **14 条**（P1 × 1，P2 × 8，**P3 × 5**），编号无跳号。汇总引用本文件计数时以 14 条为准。
- **DC-5 引用行号微偏**：runtime pom 的 test-jar 导出 goal 实际位于 :192-199（发现所引「184-189」略有偏移）；:91 的 test-scope `test-jar` 依赖声明精确命中。实质（test-jar 导出 + test-scope 消费）成立，不影响裁决。
- 复核过程未发现任何一条发现的承重证据（行号/原文/代码现实/定级）存在足以驳回或降级的问题。

## 最终保留项

| 编号 | 等级 | 一句话结论 |
|---|---|---|
| DC-1 | P1 | 用户指南能力边界段对 union/侧输出提取的否定已全面失真——Java API 与 XDSL union 均已落地并有 E2E 钉定 |
| DC-2 | P2 | flow 侧 `<sideOutput>` fail-fast 文案引用「getSideOutput 未实现」这一已消失的缺口 |
| DC-3 | P2 | connectors.md SPI 表被「Source API 冻结政策」节从中间截断，sink 4 行成无表头孤儿行 |
| DC-4 | P2 | connectors.md:96 与 TwoPhaseCommitSinkFunction Javadoc 的异常类型（UnsupportedOperationException）与实际抛出的 StreamException(ERR_STREAM_UNSUPPORTED) 不符 |
| DC-5 | P2 | README 声称独立进程入口已实现，与 owner doc/user guide 的 test-scope（F-11）诚实化直接矛盾 |
| DC-6 | P2 | README 与 INDEX 的「共 10 个」子模块计数过时且「与 pom 一一对应」自证为假（实为 11，缺 nop-stream-sql） |
| DC-7 | P3 | owner doc 模块路由表缺 nop-stream-sql 行 |
| DC-8 | P2 | user-guide 可用 transform 清单缺 join 与 union——两者均已落地可执行 |
| DC-9 | P2 | distributed-runbook §7 两条已知边界（manifest checksum/version、背压 gauge）已交付未勾销，与同文件 §6.1 自相矛盾 |
| DC-10 | P2 | stream-dsl-design 的 `<union>` 行保留三重过时断言（未实现/Deferred/UnsupportedOperationException），违背同目录更新惯例 |
| DC-11 | P3 | CDC cookbook 锚点 TestDebeziumEngineProperties 实际位于 nop-message-debezium，模块归属未标注 |
| DC-12 | P3 | user-guide XDSL 示例代码围栏内嵌两段 markdown 正文，示例原样不可解析 |
| DC-13 | P3 | user-guide 称 triggers 目录「10 文件」，实际 11 文件（新增抽象基类 ContinuousIntervalTrigger） |
| DC-14 | P3 | user-guide 称窗口算子工厂「ServiceLoader 风格发现」，实际为写死 FQCN 的 Class.forName 反射 |
