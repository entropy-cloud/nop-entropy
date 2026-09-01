# nop-stream rocksdb / flow / fraud-example 三模块审计（roadmap item 11，Phase M 收官项）

> Status: resolved
> Date: 2026-09-01
> Scope: `nop-stream/nop-stream-rocksdb/`（17 main / 14 test，含本审计新增 TestRocksDBAuditFixes）+ `nop-stream/nop-stream-flow/`（73 main / 22 test，含新增 TestStreamFlowAuditFixes + 1 fixture）+ `nop-stream/nop-stream-fraud-example/`（10 main / 7 test，含新增 TestFraudAuditFixes 与重写的 TestGeographicAnomalyPatternFix）：2026-05-20 duplicate-code audit 与 2026-06-30 code audit 的三模块相关发现整改收口验证 + 2026-08-04-2300-2 remediation plan rocksdb 侧修复点复核 + 产品化视角新增审计（D-GAP item 11 三项重点逐条消化）+ 小缺陷就地修复与收口
> Conclusion: 两轮历史审计三模块相关发现整改收口**成立**（05-20 无三模块专属组——归属核对显式记录；06-30 seed+派生逐项核对无 regressed，唯一 partial 为 test 通配符导入（审计时点 10 文件，重写后 9）路由 item 22；2300-2 rocksdb 侧 fail-fast 修复点 live 复核成立，2300-1/3 零三模块修复点确认）；D-GAP item 11 三项重点全部消化（①增量 segment checksum/schemaVersion 填充路径端到端真实性核验成立——每 segment 无条件填充 SHA-256 + schemaVersion=1，异常路径 fail-fast，P-REQ-20「segment 级 landed」结论成立；②flow XDef 校验完备性结论表落地——模型层字段级校验完整、连接器配置层经 bean/xpl 间接无 XDef 类型化字段，与 item 10 §2.2 表衔接，5 类 silently-dropped 声明面收敛为 build 期 fail-fast；③fraud-example 完整度评估 + 3 入门拓扑缺口清单自包含落地供 items 12/17）；产品化审计采信修复 19 项（rocksdb 8 + flow 7 + fraud 4：含 rocksdb 双 P2 backlog 收口、TTL 时间戳保持、legacy 键回退、损坏 marker fail-fast、flow 连接器配置面 fail-fast、NoOp timestampAssigner、demo 失败退出码等，4+7+4=15 个新 focused 用例）+ 结构治理 2 项转 Follow-up items 29/30；flow `_gen` 生成纪律核验 clean；hollow scan 三模块 exit 0；`./mvnw test -pl nop-stream -am -T 1C` 全模块绿（868/0）
> Source: `ai-dev/backlog/nop-stream-productization-roadmap.md` item 11；plan `ai-dev/plans/nop-stream-productization/2026-09-01-1457-3-rocksdb-flow-fraud-example-audit.md`
> Related: `2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`（历史审计原文）、`2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 11 三项重点）、`2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7：§1.1 §7 行空壳模块结论、§2.2 Flink/Beam 裁定、S-12 legacy 键回退先例）、`2026-09/2026-09-01-nop-stream-runtime-module-audit.md`（item 8：§1.3 2300-2 runtime 侧复核）、`2026-09/2026-09-01-nop-stream-cep-module-audit.md` / `2026-09/2026-09-01-nop-stream-connectors-module-audit.md`（items 9/10 siblings：报告结构复用、item 10 §2.2 XDef 覆盖表为 ② 的衔接输入）、`ai-dev/plans/nop-stream-production/2026-08-04-2300-{1,2,3}-*.md`（复核对象）、`ai-dev/backlog/nop-stream-production-roadmap.md`（P2 backlog rocksdb 双项收口）

## Context

- roadmap item 11（Phase M 第五个审计项 = 收官项，deps item 6 已 done，siblings 7/8/9/10 已 done）：对 rocksdb/flow/fraud-example 三模块按产品标准完成审计并收口；closure 后 M2 解锁（items 6—11 全 done）。
- 审计方法与 items 7—10 对齐：live 锚点优先；所有 live 核对命令于 2026-09-01 在 worktree 根执行，排除 `target`；两个独立 general subagent（flow 深审 session `ses_fa2e2979bffemYF9crHPihXQ01`、fraud-example 完整度评估 session `ses_fa2e26a5cffeGRbGE1DEqNpb58`）完成深审，**其发现均经本报告执行者逐条源码复核后才采信**（其中 1 项经复核否决——见 §2.6；1 项经 fix-revert 实验降级定性——见 §3.1 FX-1）；rocksdb 17 个 main 文件由执行者全量读审。
- 小/大缺陷判定准则（items 7—10 一致）：修复限于三模块内、不改跨模块公共契约、无需新测试基建 → 小缺陷就地修复；否则 → 大缺陷转 Follow-up。**例外**（plan Non-Goals）：fraud-example 大规模示例重写属 items 12/13，本审计只评估不重写。

## Phase 1 — 历史审计整改收口验证

### 1.1 2026-05-20 duplicate-code audit 归属核对

逐组确认 §1—§9 无三模块专属组：

| 05-20 组 | 涉及模块 | 三模块归属 |
|---|---|---|
| §1 operator/operators、§3 runtime 死代码、§4 core 死代码、§5 TimerService、§6 孤立图执行路径、§8 core/state 包分裂、§9 core/sink 单文件包 | core/runtime | 无三模块文件 |
| §2 CepOperator vs CepWindowOperator | cep/runtime | 无三模块文件（05-20 §2 表记载 CepOperator「生产引用 fraud-example」——即 fraud-example → cep 的依赖方向事实，`fraud-example/pom.xml` live 依赖 nop-stream-cep 成立，非重复代码组） |
| §7 空壳模块（api/checkpoint/flink/flow 四件） | — | **flow 在其中**（时点空壳）→ live 已实现（XDSL 编排 + Delta 定制，73 main）；引用 item 7 报告 §1.1 §7 行已落定结论「3 删 1 实现，勿重复立项」，本表仅记录归属；rocksdb/fraud-example **不在**空壳清单（06-30 时点即非空壳） |
| §10 建议包结构 | 设计建议 | §10.2 将 fraud-example 列为「欺诈检测演示」、flow 列为规划中——组织陈述非缺陷组 |

**结论：05-20 审计无 rocksdb/fraud-example 专属组**（显式记录）；flow 的唯一相关组（§7 空壳）已由 item 7 统一核收口。本审计对 flow 转入内容质量审计（§2.2/§2.4）。

### 1.2 2026-06-30 code audit 三模块相关发现核对表

> 先列全表再逐项核对。seed 清单 = plan Current Baseline 所列；派生规则 = 报告中「涉及文件位于三模块」的其余发现。三态：landed / partial / regressed。

| # | 06-30 发现（章节） | 涉及模块 | live 三态 | 证据锚点 |
|---|---|---|---|---|
| 1 | §1.1 模块统计（fraud-example 10 main/5 test「示例」） | fraud-example | **landed**（统计一致：live 10/5；@Test 复算 8+7+2+4+4=25 与 06-30 记载 25 一致；本审计 +2 test 文件后 7 类 33 @Test） | `find`/`grep -c @Test` 复算（2026-09-01） |
| 2 | §2.1 UOE 桩：`DemoKeyedStateStore.getReducingState/getAggregatingState`（「demo 简化」低） | fraud-example | **unchanged（guard 语义核实成立，非空壳）+ 已加固** | `DemoKeyedStateStore.java:81-83/:86-88`（修复后行号）：两处显式 `throw new UnsupportedOperationException("... not needed for demo")`——Rule #24 合规的显式失败（非静默返回）；调用方 SharedBuffer 仅用 Map/Value/List 三族（`SharedBuffer.java:103-122` 消费面核实）；hollow scan 消息分级下「not needed」非 stub 标记 → 不入 high。本审计补类级 Javadoc 契约警告（FX-3），防止被当作真实 KeyedStateStore 模板复制 |
| 3 | §2.2 通配符导入（live 复核：rocksdb test 4 / flow test 1 / fraud-example test 5；main 侧全模块已清零） | 三模块 test | **partial（main 清零 / test 10→9 文件残留）** | `grep -rl "import .*\.\*;"` 逐模块复算：rocksdb 4（TestRocksDBBackendSkeleton/DescriptorAggregatingStateRestore/SnapshotRestore/StateTypes）、flow 1（StreamModelSmokeTest）、fraud-example 5（全部 pattern 测试——本审计 TestGeographicAnomalyPatternFix 重写为显式导入后剩 4）；main 0/0/0。→ **路由 Follow-up item 22**（closure 写回枚举事实补全：rocksdb 4 + fraud-example 4，见 §3.2） |
| 4 | §3.1 测试覆盖（fraud-example 5 类 25 @Test，比 0.50） | fraud-example | **landed（覆盖增长）** | 本审计后 7 类 33 @Test（+TestFraudAuditFixes 4、TestGeographicAnomalyPatternFix 重写 2→2 换真类驱动）；覆盖面从「pattern POJO 形状」扩展到「NFA 引擎真实驱动 + demo 装配冒烟」（§2.7） |
| 5 | §2.4 `_gen` 生成代码（06-30 记载「仅在 cep」） | flow | **过时记载（时点 flow 空壳，现已实现并含 `flow/model/_gen/` 30 文件）→ Phase 2 补齐核验缺口** | 06-30 §2.4 的记载在其时点成立；live flow `_gen` 生成纪律核验结论 = clean（§2.5），与 item 9 对 cep `_gen` 的核验标准对称，该核验缺口由本审计闭合 |
| 6 | §6.2 #6 空壳模块治理（06-30 时点 4 空壳） | flow | **landed**（引用 item 7 §1.1 §7 行结论：3 删 1 实现；flow = 已实现侧，本审计其内容质量见 §2.2—§2.4） | `ls nop-stream/` 10 模块 live |
| 7 | §1.3 依赖方向验证（时点未列 rocksdb/flow/fraud） | 三模块 | **unchanged（依赖方向合规）** | live pom：rocksdb → core（+rocksdbjni）；flow → core（+nop-xlang 系 test/codegen）；fraud-example → cep（cep → core 传递）——`runtime/connector/cep → core` 约束持续成立，无反向依赖 |
| 8 | §2.1 UOE 桩 6 处中其余 5 处（forceNonParallel/GroupPattern/WindowAggregation/Trigger.onMerge/ICheckpointExecutorFactory） | core/cep | **n/a**（无三模块文件；core 侧归 item 7 §1.2 #4/#5 已核） | — |
| 9 | §2.2 大量 return null / 硬编码值（集中 core/runtime JDBC 族） | core/runtime | **n/a + 派生扫描 clean** | 三模块 main 无 catch-return-null 模式（逐文件读审：rocksdb 状态类全部 catch-rethrow 为 StreamException/IOException；`return null` 均为合法 miss 语义——MapState.get miss、RocksDBValueSerDe.deserialize(null bytes)） |
| 10 | §4.4/五（与 SeaTunnel/Flink 对比：模块成熟度） | 全局 | **unchanged（对比项）** | 4 空壳已消除（#6）；测试密度维持 |

**结论：无 regressed 项**；唯一 partial（#3 test 通配符，审计时点 10 文件、TestGeographicAnomalyPatternFix 重写后 9）路由 Follow-up item 22 并随本 plan closure 完成枚举事实补全；#5 过时记载由 Phase 2 闭合。

### 1.3 2300-1/2/3 remediation plans 三模块侧复核

| Plan | 三模块侧修复点 | 复核结论 | 抽查证据 |
|---|---|---|---|
| 2300-2 checkpoint-state-backend-cep-correctness | **rocksdb 侧两项**：`RocksDBKeyedStateBackend.restoreState` 两条增量分支补 `verifyKeyLayoutVersion(snapshotData, true)` fail-fast + 配套测试 | **成立（landed）** | live `RocksDBKeyedStateBackend.java`：marker 为 `IncrementalSnapshotResult` 分支在 `restoreIncremental` 前调用 `RocksDBKeyEncoder.verifyKeyLayoutVersion(snapshot.getStateData(), true)`（:799，RK-8 修复后行号）；marker 为 Map 经 BeanTool 重建分支同样调用（:811）——两条分支均「验证在前、扫描在后」；配套测试 `TestRocksDBIncrementalRestoreFailFast.java` 存在（plan Phase 1 exit criteria 要求经 `restoreState` 入口断言 `ERR_STREAM_STATE_ERROR`）。runtime 侧（CheckpointCoordinator unregister/JdbcCheckpointStorage upsert）归 item 8 报告 §1.3 已复核，不重复 |
| 2300-1 coordinator-runtime-concurrency-recovery-hardening | Targets：JobCoordinator（runtime）、InputGate（core）、TaskManager/SupervisionLoop（runtime）、NopStreamErrors（core） | **不适用（零三模块修复点，确认成立）** | plan 全文 `grep -i "rocksdb\|nop-stream-flow\|fraud"` 零命中；Targets :58/:80/:100/:117 全在 core/runtime |
| 2300-3 contract-drift-config-test-integrity | Targets：core state SPI（IOperatorStateStore/KeyedStateStore/StateDescriptor）+ 两份 design doc + runtime `_module` + core 测试 | **不适用（零三模块修复点，确认成立）** | Targets :64/:84/:101 全在 core/runtime/docs；plan 文中唯一的 rocksdb 字样是 Current Baseline 事实陈述（KeyedStateStore 5-vs-2 drift「两后端均实现全 5」——rocksdb 侧 live 持续成立：`RocksDBKeyedStateBackend` 8 个 getState 重载全实现），非修复目标 |

## Phase 2 — 产品化视角新增审计

### 2.1 D-GAP 重点 ①：`RocksDBIncrementalSnapshotStrategy` segment `checksum`/`schemaVersion` 填充核验

**结论：P-REQ-20「segment 级 checksum+版本 landed」的端到端真实性成立。** 逐层证据：

| 层 | 证据 | 锚点 |
|---|---|---|
| 填充路径（task 侧） | `RocksDBIncrementalSnapshotStrategy.doSnapshot` 对 native checkpoint 目录下**每个** `.sst`/`.ldb` 文件计算 `SstFileChecksum.sha256Hex`（SHA-256 流式，core `SstFileChecksum.java:32-42`，64KB 缓冲恒定内存），构造 `SharedStateHandle(hash, path, size)` 加入 sstHandles——**无任何跳过 hash 的分支**（循环体每 SST 必经 :93-97） | `RocksDBIncrementalSnapshotStrategy.java:85-104` |
| 填充路径（coordinator 侧） | `CheckpointCoordinator.buildAndMaterializeSegments` 遍历全部 task snapshots 的 `IncrementalSnapshotResult` × 全部 sstHandles，**无条件**构造 `new StateSegmentDescriptor(SEGMENT_TYPE_ROCKSDB_SST, hash, CODEC_IDENTITY, hash, SCHEMA_VERSION_ROCKSDB_SST)`——checksum = contentHash（SHA-256）、schemaVersion = 1，每个增量 segment 必填 | `CheckpointCoordinator.java:652-674`（descriptor 构造 :669-674） |
| 产物抽样验证 | `TestRocksDBIncrementalSnapshotStrategy`：断言每个 handle 的 contentHash 为 **64 字符小写 hex**（:88）、文件存在、size>0；确定性去重（两次无变更 checkpoint 产生相同 hash 集合，:112-115）；registry 引用计数 =2（:128-130） | rocksdb test（live 绿） |
| 持久化 round-trip | `CheckpointSerDe` 反序列化 segments 时读全 5 字段（segmentType/path/codec/checksum/schemaVersion），schemaVersion 缺失时回退 1（向后兼容）；`EpochManifest.segments` 承载 descriptor 列表 | `CheckpointSerDe.java:270-283` |
| schemaVersion 语义衔接 | segment 级 `SCHEMA_VERSION_ROCKSDB_SST=1` 与 core 侧 `SerializerFingerprint.schemaVersion` 恒 1（item 7 §2.1 ① 结论「core 侧无阻碍，字段落地增量全在 runtime manifest 层」）语义一致：segment 级已 landed，manifest 级 `stateFormatVersion`/`checksum` 属 Follow-up item 25（本审计 Non-Goal，只核验 segment 级证据） | `StateSegmentDescriptor.java:43-50` |
| 异常路径（checksum 计算失败） | `sha256Hex` 抛 IOException → `doSnapshot` 无 catch 直接传播 → `RocksDBKeyedStateBackend.snapshotIncremental`（:743-763）无 catch-swallow → `snapshotState`（:724-734）传播 → checkpoint 失败——**fail-fast，无降级产出无 checksum segment 的路径** | 逐帧读审 :724-763 |
| 异常路径（部分填充） | 不存在部分填充态：descriptor 构造在 coordinator 侧对已物化 handle 一次性完成；persist 失败走 `releaseIncrementalSegments` 回滚（unregister + 零引用物理回收，2300-2 Phase 2 修复点，item 8 已复核） | `CheckpointCoordinator.java:683-706` |
| 已知边界（记录） | restore 侧**不重算** segment 内容 hash 校验（content-addressed path=hash 提供结构性完整性；`RocksDBIncrementalRestore.reconstructRocksdbDir` 对 hash 缺失于 store 时 IOException fail-fast :95-98）——段内容 hash 校验为 2300-2 显式 Out Of Scope P2 项（归 backlog，非本审计新增发现） | `RocksDBIncrementalRestore.java:94-98` |

**P-REQ-20 回填结论**：D-GAP §1.1 四分项之「② segment 级 checksum+版本 landed（增量路径）」经端到端核验**成立**；剩余缺口维持既有裁定（manifest 级字段 = item 25，torn-write 注入测试 = item 8 已补齐）。

### 2.2 D-GAP 重点 ②：flow 模块 XDef 校验链路对连接器配置段的字段级校验完备性

**消费路径事实**（与 item 10 报告 §2.2 的连接器侧结论表衔接：连接器配置 bean 8/8 走 Java 构造期校验、无一进入 XDef 类型化字段）——本审计深审 flow 编译器侧：

| 配置段 / 属性（stream.xdef） | XDef 字段级校验 | flow 编译器消费与校验时点（修复后 live） |
|---|---|---|
| `stream@parallelism/watermarkInterval`、`checkpoint@*`（13 attr，含 storageConfig entry） | ✓（typed int/long/enum，`!` 必填 + 默认值） | build 期全量映射（P1-XDSL-6 六字段含 declared-zero 守卫）；`storageType` 为自由 string——未知值在 runtime storage 初始化期 fail-fast（晚错误非静默，watch-only W-F1） |
| `transforms/*@bean` | 格式校验（bean-name） | **build 期存在性 + 接口类型校验**：`beanResolver.resolve` → `ERR_STREAM_BEAN_NOT_FOUND`/`ERR_STREAM_BEAN_TYPE_MISMATCH`（GlobalBeanFunctionResolver :23-32） |
| 内联 `<source>`/`<timestampAssigner>`/`<watermarkGenerator>` xpl 体 | parse 期编译（`xpl-fn:` 签名检查） | build 期包装为 Xpl*Function；缺 body → `ERR_STREAM_REQUIRED_BODY`；`<timestampsAndWatermarks>` 缺 timestampAssigner → **NoOp 透传兜底**（FL-3 修复，原 null → 执行期 NPE） |
| `source/sink/custom@params`、`outputType/inputType`、`maxParallelism`、非默认 `consistencyCapability` | ✓（typed，但无消费者） | **修复前：全部 silently dropped**（repo-wide 零消费，rg 核实）→ **修复后（FL-1）：build 期 fail-fast `ERR_STREAM_NOT_IMPLEMENTED`**（声明面诚实化；实际消费归 items 12/13 XDSL 场景 + item 20 conf-validate，见 §3.2 路由） |
| `transforms/*@parallelism` | ✓（int） | **修复前：静默忽略（仅 env 级生效）→ 修复后（FL-2）：声明值 ≠ 生效值时 fail-fast**（相等则放行；Transformation.parallelism 为 core final 字段，per-operator 消费需 core API 扩展 → item 29） |
| `edge@partition/keyExpr/flow-control` | ✓（enum） | build 期决策矩阵：HASH+keyExpr 消费、REBALANCE/BROADCAST/four flow-control attrs fail-fast（P1-XDSL-5，先例）；本审计复核实存 |
| windowing strategy / cep patternRef / custom customType | ✓ | build 期解析（builtin 目录 + bean + registry，unknown → `ERR_STREAM_REF_UNKNOWN`） |
| 顶层 registries（streams/sideInputs/environments/schemas/coders/requirements/checkpointParticipants/lifecycle） | ✓ | **build 期 fail-fast `ERR_STREAM_NOT_IMPLEMENTED`**（failFastOnUnsupportedRegistries，复核实存） |

**结论**：模型结构层（拓扑/参数类型/枚举）XDef 字段级校验完整；连接器配置层经 bean/xpl 间接（无 XDef 类型化字段），bean 存在性/类型在 build 期校验、连接器自有参数在 Java 构造期校验（item 10 §2.2）——两层 fail-fast 链路闭合；修复前唯一的静默面（params/capability/maxParallelism/type 声明、per-transform parallelism）已收敛为 build 期显式拒绝。**与 item 10 §2.2 表的衔接结论一致且互补**（该表结论「8/8 连接器配置 bean 构造期校验」在 flow 侧的消费端得到验证）。

### 2.3 flow `_gen` 生成纪律核验（补齐 06-30 §2.4 过时记载的核验缺口）

**结论：clean，无手改痕迹。** 证据：30 个 `_gen` 文件统一携带标准生成头（`generate from /nop/schema/stream/stream.xdef`）+ `CPD-OFF/CPD-ON` 标记 + 一致的生成形态（字段按 xml 名排序、getter/setter 带 `checkAllowChange()`、keyed-list 三件套、freeze/outputJson/clone 家族）；`_StreamModel` 等 8 个文件抽查结构一致，无时间戳、无手写方法混入；16 个 transform 子类的 `_type` 空 xml-name 判别字段为 `xdef:bean-sub-type-prop` 标准 codegen 输出（与 wf.xdef/task.xdef 同机制）；git 历史整树单 commit（`350a48624e`）引入后**零修改 commit**；再生成链路 live（`nop-stream-flow/precompile/gen-stream-xdsl.xgen` + root pom exec-maven-plugin precompile + nop-codegen test 依赖）。与 item 9 对 cep `_gen` 的核验标准对称。

### 2.4 D-GAP 重点 ③：fraud-example 作为快速起步脚手架（P-REQ-25）的完整度评估

**现有示例盘点**（10 main 全量读审）：

| 组件 | 现状 | 评估 |
|---|---|---|
| `FraudDetectionDemo`（221 行） | main 驱动 4 pattern × MockTransactionGenerator 数据，console 输出 alert；自包含无外部依赖 | 可从零跑通（`mvn exec:java`）；修复后失败退出非零（FX-2）；但驱动的是 **CEP 引擎内部 API**（NFA/NFACompiler/SharedBuffer 直连 + null RuntimeContext/TimerService），未展示两扇正门（DataStream API / XDSL）——产品示例定位下为主要缺口（Gap A） |
| 4 个 pattern | 真实 CEP 逻辑（begin/next/within + IterativeCondition），无 stub——除 UnusualAmountPattern（诚实标注 DEMO STUB：固定 $100 平均而非 keyed 历史均值，`MIN_TRANSACTIONS=3` 声明未强制） | 逻辑正确性经 NFA 真驱动测试钉定（本审计补齐 Rapid/AccountTakeover 侧）；UnusualAmount 去 stub 化归 Gap B |
| `DemoKeyedStateStore` | demo 专用状态店：每次 getState 返回全新不连通匿名状态（真实契约应共享存储）；UOE×2（guard 语义成立） | 本审计补类级 Javadoc 契约警告（FX-3）；替换为真实后端接线归 Gap C |
| `UserTransactionHistory`（109 行） | 良好 Javadoc 的 keyed-state 用法示例——**死代码**（仅被 UnusualAmountPattern Javadoc 引用，零实例化） | Gap B 的现成素材 |
| `MockTransactionGenerator` | 5 组场景生成器与 4 pattern 对齐；`Math.random()` 非确定（demo 可接受，测试正确避开） | 无直接测试（watch-only C6） |
| 模型类 | immutable + Serializable + 完整 toString/equals/hashCode（exemplary）；FraudAlert 带 ASF license 头与其余 9 文件 Nop 头不一致（provenance 待 owner 裁定，watch-only C5） | — |
| `fraud-detection.stream.xml`（114 行） | **死文件**：零代码/测试引用；模块 pom 无 nop-stream-flow 依赖（xdef 亦不可解析）；文件内注释自认「破损死文件 + REBALANCE/HASH backlog」；含 2 个未接线的 transform | Gap A 素材/由 items 12/13 处置 |
| README（109 行） | 存在但 stale：结构树缺 state//util/、乱码行（"support- Persistent"）、引用不存在的 LICENSE/CONTRIBUT.md、「MemoryStateBackend (configurable)」虚假声明（实际是 DemoKeyedStateStore 不可配置） | 本审计全部修复（FX-4） |
| 测试（5 类 25 @Test） | 覆盖 pattern POJO 形状 + generateAlert 参数校验；仅 TestUnusualAmountPattern 驱动 NFA；**demo 装配/main 零覆盖**；TestGeographicAnomalyPatternFix 复制条件体内联（真类回归时测试仍绿——零回归价值） | 本审计：TestGeographicAnomalyPatternFix 重写为真类 NFA 驱动（FX-5）+ demo main 冒烟（FX-2 附属）+ Rapid/AccountTakeover NFA 驱动钉定 |

**作为快速起步脚手架的可用性评估**：

| 准则 | 状态 |
|---|---|
| 从零跑通（无 Kafka/DB/文件依赖、无硬编码路径、有意义输出） | ✓（自包含；exit code 修复后诚实） |
| 依赖与配置透明 | ✓/△：pom 仅依赖 cep（轻且诚实）；但「切换 RocksDB / 增量 checkpoint / XDSL」路径完全缺失（README 曾有虚假「可配置」声明，已修正） |
| README/注释质量 | 修复后 ✓（结构/事实对齐；模块内无独立 LICENSE 文件属仓库级布局） |
| 展示产品正门与核心语义（DataStream/XDSL/keyBy/checkpoint/window/connector） | ✗ 全缺（CEP 内部 API 直连） |

**3 个入门拓扑缺口清单（入门用户视角定义；自包含，供 item 12 场景设计与 item 17 文档直接消费）**：

- **Gap A — 可运行的 XDSL 声明式版欺诈管线**：以 `.stream.xml` 定义 source → watermark/filter → CEP → sink 并经 `nop-stream-flow` 执行——产品声明的主入口（nop-stream/README「主入口是 XDSL」）目前在整个示例族中零覆盖；现成素材：死文件 `fraud-detection.stream.xml` 骨架（含 bean 引用模式 :31-34/:75-80）+ flow 测试 fixture 模式（test-smoke-collecting）。教会：声明式入口、bean/xpl 双函数形态、Delta 定制。工作量 M（补 flow 依赖 + 完成 4 pattern 声明化 + 接线测试 + 清理死接线）。
- **Gap B — keyBy + 窗口聚合 + keyed state 示例（DataStream API）**：`keyBy(userId)` 真分区（对照 demo 在条件内部做 userId 字符串比较的反模式）+ 滚动窗口 + per-key `ValueState` 累计——入门者学 CEP 前必须建立的核心流心智模型；现成素材：死代码 `UserTransactionHistory`（正是为此写的）+ UnusualAmountPattern 去 stub 化（真实 per-user 均值替代固定 $100）。教会：分区、窗口、keyed state。工作量 M。
- **Gap C — checkpoint/恢复 + 状态后端切换 demo（kill-recover）**：启动作业 → checkpoint → 中途 kill → 恢复 → 无丢失/重复断言，再以纯配置切换 memory → RocksDB（增量）——产品差异化能力（runtime checkpoint 协调器 + rocksdb 增量后端）在示例族零展示（README「Future Enhancements」自认）；模块 pom 现无法触及 runtime/rocksdb。工作量 L（需 runtime+rocksdb 依赖 + 可重启 harness/脚本 + 文档化配置开关）。

（第四候选「独立连接器示例」并入 Gap A——任何可运行 XDSL 管线天然需要 source/sink 接线；真实连接器示例可作 item 12 扩展。）

### 2.5 三模块核心路径审计（重复代码 / 错误处理一致性 / 边界条件；subagent 发现逐条源码复核后采信）

#### rocksdb（执行者全量读审 17 main）

| 维度 | 结论 |
|---|---|
| SST 文件生命周期 | task 侧策略不删文件（native checkpoint 目录归 coordinator/调用方，Javadoc 显式声明 :40-44）；coordinator 侧物化/回收/GC 闭环（item 8 复核）；restore 侧临时重建目录 try-finally `deleteRecursively`（修复后失败留 WARN，RK-7） |
| 句柄与 Options 释放 | **发现两处缺陷并修复（RK-1/RK-2）**：`openDB` 的 `Options` JNI 对象泄漏（production roadmap P2 backlog 正式条目，本审计收口）；`close()` 逐 handle 无隔离（同 backlog 第二条，本审计收口）；其余路径（迭代器 try-with-resources、restoreRangeInto 全链）exemplary |
| 异常路径恢复 | **发现并修复两处静默面（RK-6/RK-8）**：`cfNameOf` getName 失败误归类 default CF → 静默跳过该 CF 数据；`restoreState` 损坏增量 marker（非 typed 非 Map）→ 静默落入全量路径空恢复。fail-fast 主线（verifyKeyLayoutVersion 双分支 / segment 缺失 IOException / verifySchemaCompatibility + migration）复核实存 |
| TTL 与增量快照交互 | `snapshotState` 两路径均前置 `cleanupExpiredEntries()`（:729/:732）——过期状态不入 SST 亦不入全量快照（设计意图达成）；SerDe 快照侧 `expiredForSnapshot` 二次过滤；**发现并修复 RK-4**：`applyTtl` 每次 getState 重绑新 TtlContext 抹掉累计 sidecar 时间戳（TTL 窗口静默重置） |
| 错误处理一致性 | RocksDBException → StreamException（ERR_STREAM_STATE_ERROR + ARG_DETAIL）为主线；ValueState 族 public API throws IOException 受接口签名约束（合法）；`inferAccumulatorType`/`resolveStorageValueType` 静默 catch → 补 WARN（RK-5，镜像 core S-3） |
| 重复代码 | **结构性（大缺陷 → Follow-up item 30）**：SerDe 8 snapshot + 8 restore 分支同构（与 core MemoryStateSerDe D-1 同族）+ 8 个 getXxxState 重载 85% 同构（与 core D-3 同族）+ 状态类族成对克隆（List/InternalList 等 4 对，与 core D-2 同族）——镜像 item 21 的 core 侧清单 |
| 边界条件（watch-only） | `extractMapKey` 4 字节长度无界读（键为自写，损坏时 AIOOBE fail-fast）；`deserializeList` 非 List payload → empty（降级解析族，同 core 容忍基线）；`readSstNameMap` 畸形行跳过（MANIFEST 引用缺失文件时 openReadOnly fail-fast 间接兜底） |

#### flow（subagent 深审 + 执行者复核；13 builder 文件全读）

| 维度 | 结论 |
|---|---|
| DSL 编译器错误报告质量 | 错误码纪律 exemplary（14 个专用 ERR_STREAM_* + 参数锚定 element/id/attr，TestStreamErrorCodeContract 钉定可编程切换）；**修复 FL-4**：cycle/unreachable 错误补 unprocessed id 列表（原仅计数）；build 期错误不带 file/line 源位置（模型已脱离 XML 位置）→ 结构性（W-F2 → item 29） |
| Delta 合并语义 | x:extends 显式路径 + `_delta/default` 分层 + config-only delta 三形态测试钉定；fail-fast 经 delta 合并后仍生效（TestStreamModelDeltaFailFast）；removal delta 无测试（W-F3，generic XDSL 机制推定可用） |
| 与 core StreamModel 契约一致性 | **发现并修复 FL-1/FL-2**（silently-dropped 声明面，见 §2.2）；`requireSingleInput` 两实现错误码分叉 → 统一（FL-6）；DAG Kahn 拓扑 + 不可达/环检测正确；checkpoint 13 字段全映射复核实存 |
| Xpl*Function 家族 | 7 包装器参数绑定/truthy 转换/取消标志有专属测试；ctor 守卫裸消息异常 → 错误码化（FL-5）；**结构性（W-F6 → item 29）**：`XplSourceFunction.cancel` 的 volatile 标志无任何可观察路径（run 不读、ctx 无取消访问器、无线程中断）——内联 xpl source 不可取消 |
| 干净面 | 13 文件零 catch/零 TODO/零空方法/零 placeholder null；两 resolver 生产/测试分工有文档；`AdvancedTransforms` 内联全限定名（风格，顺手修 FL-3 时保留原样——最小 diff） |

#### fraud-example（subagent 完整度评估 + 执行者复核；10 main 全读）

| 维度 | 结论 |
|---|---|
| 示例正确性 | **FX-1（fix-revert 实验定性，见 §3.1）**：Rapid/AccountTakeover 的 same-user 条件使用「循环首元素即 return」习语——经引擎语义核实（`getEventsForPattern` 为 branch-local 物化，NFA.java:935-950；线性 pattern 下 iterable 为分支局部单元素）两版行为等价（revert 实验测试仍绿），属**复制模板正确性缺陷**（多元素 iterable 如 quantifier pattern 下分叉）而非 live 行为缺陷——修复对齐已修好的 GeographicAnomalyPattern continue 习语 + 多用户流行为钉定测试；`Math.random()` 非确定（watch-only C6） |
| 代码健康度 | 模型类 exemplary；`main` catch-all 吞异常 + exit 0 → 修复（FX-2）；`runPattern`/`runUnusualAmountPattern` 双胞胎（低价值去重，watch-only C7——items 12/13 重写时自然消除）；`TestGeographicAnomalyPatternFix` 内联复制条件（零回归价值）→ 重写真类驱动（FX-5） |
| 错误处理 | generateAlert 族 IAE + 参数上下文（两级策略第二级合规）；模块内无吞异常路径（修复后） |

### 2.6 空壳/静默跳过扫描（hollow scan）

- 扫描命令与退出码：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-{rocksdb,flow,fraud-example} --severity high` → **3×exit 0**（0 critical / 0 high；修复前后均 exit 0——三模块本无 high 发现，修复属主动加固）。
- **DemoKeyedStateStore 类 UOE 按 guard/stub 语义分级核实**：「not needed for demo」非 stub 标记（not implemented/not yet/unimplemented/stub/placeholder）亦非 guard 标记族——消息语义分级下不入 high；实质语义 = 显式拒绝（Rule #24 合规），消费面（SharedBuffer 三族状态）核实不触及（§1.2 #2）。**无需修正代码或工具**（对照 item 7 P1 分级先例：分级目的正是让合法显式失败不入 high 门禁）。
- 多行 UOE 人工补审（行级正则盲区，沿 items 7—10 基线）：三模块 main 侧 UOE 仅 DemoKeyedStateStore 2 处（单行，上已核）；空方法体人工核验：三模块 main 零命中（rg 验证）；fraud `catch (Exception e) { LOG.error }` 吞异常型已修复（FX-2）。
- **结论：无 high/critical 真实发现，无误报需处置，三模块 exit 0 硬门禁达成。**

### 2.7 测试覆盖抽查

**rocksdb**（top-3 by `wc -l`）：

| 文件（行数） | 直接测试 |
|---|---|
| RocksDBKeyedStateBackend（892） | TestRocksDBBackendSkeleton / TestRocksDBStateTtl / TestRocksDBKeyGroupRangeRestore / TestRocksDBIncrementalRestoreFailFast + 本审计 TestRocksDBAuditFixes（4 用例） |
| RocksDBSnapshotSerDe（839，package-private） | TestRocksDBSnapshotRestore（21 用例 round-trip）+ TestRocksDBStateTypes + 本审计 legacy 键/警告用例 |
| RocksDBMapState（339） | TestRocksDBStateTypes（map 族操作） |

增量族 4 测试（IncrementalSnapshotStrategy/RangeRestore/BackendWiring/RestoreAndBenchmark）+ 迁移/TTL/descriptor-restore 专项——覆盖充分；**修复带来的新覆盖**：TTL 重绑保持、legacy *TypeName 键、损坏 marker fail-fast、推断降级 WARN。

**flow**（top-3）：

| 文件（行数） | 直接测试 |
|---|---|
| `_StreamModel`（851，生成物） | 全部 parse 测试间接覆盖（codegen 不要求专属单测） |
| StreamModelDslBuilder（617+本审计增量） | E2E/FailFast/EdgeContract/CheckpointAndWindowContract/ErrorCodeContract/Delta 三件套/TestAdvancedTransforms/TestDagTopologyConsistency + 本审计 TestStreamFlowAuditFixes（7 用例） |
| AdvancedTransforms（536+） | TestAdvancedTransforms + TestAdvancedPipelineE2E + 本审计 NoOp assigner E2E |

覆盖缺口（修复后）：原「未消费契约无测试」面随 fail-fast 落地获得钉定测试；Delta removal delta 仍无测试（W-F3）。

**fraud-example**（top-3）：

| 文件（行数） | 直接测试 |
|---|---|
| AccountTakeoverPattern（222） | TestAccountTakeoverPattern（8，形状级）+ 本审计 NFA 真驱动 1 |
| FraudDetectionDemo（221） | **本审计新增 main 冒烟**（原零覆盖） |
| MockTransactionGenerator（201） | 无直接测试（watch-only，低值） |

**修复带来的新覆盖**：多用户流 NFA 行为钉定（rapid 2 + takeover 1 + geo 2）+ demo 装配冒烟。

### 2.8 D-GAP §3.1 item 11 勾销清单对照

| # | D-GAP 条目 | 消化结论 | 证据 |
|---|---|---|---|
| ① | `RocksDBIncrementalSnapshotStrategy` segment checksum/schemaVersion 实际填充核验（P-REQ-20 segment 级 landed 端到端真实性） | **落地**：填充路径（task+coordinator 双侧无条件）+ 产物抽样（64-hex 断言）+ 异常路径（fail-fast 无降级）——P-REQ-20 segment 级结论成立 | §2.1 |
| ② | flow XDef 校验链路对连接器配置段的字段级校验完备性（与 item 10 重点 ② 衔接） | **落地**：§2.2 逐配置段归属表 + silently-dropped 面收敛为 fail-fast + 与 item 10 §2.2 表互证 | §2.2 |
| ③ | fraud-example 完整度评估（P-REQ-25，item 17 输入；3 入门拓扑缺口清单供 item 12） | **落地**：§2.4 盘点表 + 可用性评估 + Gap A/B/C 自包含 | §2.4 |

无遗漏条目（D-GAP item 11 行仅此三项）。

## Phase 3 — 缺陷处置与收口

### 3.1 小缺陷就地修复（19 项全部限三模块内 + 2 项 backlog 状态写回；行为修复配 focused 测试）

**rocksdb（RK-1..RK-8）**：

| ID | 修复 | focused 测试 |
|---|---|---|
| RK-1 | `openDB` 的 `Options` JNI 对象 try-with-resources（**production roadmap P2 backlog「Options native handle leaked」条目收口**） | 无新增测试（豁免理由：JNI native handle 生命周期无公共可观察 API；验证 = 代码模式 + 全量回归；backlog 条目状态写回 Closed） |
| RK-2 | `close()` 逐 native handle 隔离 try/catch，首错误重抛 + 其余 suppressed（**P2 backlog「close() 非健壮」条目收口**） | 同上豁免（错误注入需 JNI mock） |
| RK-3 | `restoreReducing/Aggregating/InternalAggregating` 补 legacy `valueTypeName`/`accumulatorTypeName` 回退（与同文件其余 5 分支及 core S-12 对齐——同名 legacy 快照 Reducing 原本恢复失败） | **新增** TestRocksDBAuditFixes.legacyTypeNameKeysRestoreReducingState（legacy 键快照恢复 12L） |
| RK-4 | `applyTtl` 配置未变时保留既有 TtlContext（原每次 getState 重绑 → 累计 sidecar 时间戳静默清零 = TTL 窗口重置） | **新增** repeatedGetStatePreservesTtlTimestamps（中途 re-getState 后按原计划过期；fix 前必败——时间戳被抹后 grantFreshWindow 使 t=110 不过期） |
| RK-5 | `RocksDBSnapshotSerDe.inferAccumulatorType` 与 `RocksDBInternalAggregatingState.resolveStorageValueType` 静默 catch 补 LOG.warn（可观测降级，镜像 core S-3） | **新增** accumulatorTypeInferenceFailureIsLogged（ListAppender 断言 WARN + 恢复继续） |
| RK-6 | `RocksDBIncrementalRestore.cfNameOf` getName 失败 → StreamException fail-fast（原返回 `__default__` → 该 CF 数据被静默跳过） | 无新增测试（豁免理由：触发需 RocksDB JNI getName 异常注入，无 mock 基建；行为变更仅存在于病态 JNI 失败路径，主线由既有增量恢复测试回归） |
| RK-7 | `deleteRecursively` 清理失败 LOG.warn（原 `catch (IOException ignored)`） | 日志级增补（清理行为不变），No new test required per Rule #25 |
| RK-8 | `restoreState` 损坏增量 marker（非 typed 非 Map）→ fail-fast（原静默落入全量路径 = 空恢复，静默状态丢失） | **新增** corruptedIncrementalMarkerFailsFast（String marker → StreamException 含定位信息） |

**flow（FL-1..FL-7）**：

| ID | 修复 | focused 测试 |
|---|---|---|
| FL-1 | source/sink/custom 的 `params`/`outputType`/`inputType`/`maxParallelism`/非默认 `consistencyCapability` → build 期 `ERR_STREAM_NOT_IMPLEMENTED` fail-fast（原全部 silently dropped，repo-wide 零消费者） | **新增** sourceParamsFailFast / sourceNonDefaultConsistencyCapabilityFailFast / sinkMaxParallelismFailFast；fixture test-smoke.stream.xml 移除 REPLAYABLE/IDEMPOTENT 声明（其原依赖静默丢弃） |
| FL-2 | per-transform `parallelism` 声明值 ≠ 生效值（stream 级）→ fail-fast（相等放行；core Transformation.parallelism final，消费需 API 扩展 → item 29） | **新增** perTransformParallelismMismatchFailFast（断言 declared=4 出现在错误中）+ perTransformParallelismMatchingEffectiveValueIsAccepted（正控制）；fixture transform parallelism 1→2 对齐 |
| FL-3 | `XplWatermarkStrategy.createTimestampAssigner` body 缺失时返回 NoOp 透传（原 null → `TimestampsAndWatermarksOperator:100` 执行期 NPE）——与既有 NoOpWatermarkGenerator 对称 | **新增** timestampsWithoutAssignerExecutesWithoutNpe（E2E：fixture 管线仅声明 watermarkGenerator，3 记录完整到达 sink）+ 新 fixture test-timestamps-no-assigner.stream.xml |
| FL-4 | cycle/unreachable 错误补 unprocessed transform id 列表（原仅 processed/declared 计数） | **新增** cycleErrorNamesUnprocessedTransformIds（断言 cycSrc/cycMap 出现） |
| FL-5 | Xpl* 7 包装器 + EvalActionKeySelector ctor 守卫：裸消息 StreamException → `ERR_STREAM_NULL_ARG` + ARG_ARG_NAME（taxonomy 对齐；路径经 DSL builder 不可达，属防御守卫） | 编译即验证 + 既有 TestXplFunctionWrappers happy-path 回归（No new test required：异常类型不变仅错误码化，无行为面） |
| FL-6 | SMDB/AT 两版 `requireSingleInput` 的 null upstream 统一为 `ERR_STREAM_UPSTREAM_NULL`（原 SMDB 版报 UPSTREAM_TYPE+"null"） | 既有错误码契约测试回归（TestStreamErrorCodeContract/TestStreamModelEdgeContract 全绿） |
| FL-7 | `BeanFunctionResolver.resolve` javadoc `@throws IllegalArgumentException` → 实际 `StreamException`（ERR_STREAM_BEAN_*） | 纯文档（No new test required per Rule #25） |

**fraud-example（FX-1..FX-5）**：

| ID | 修复 | focused 测试 |
|---|---|---|
| FX-1 | Rapid/AccountTakeover same-user 条件由「循环首元素即 return」改为全量扫描 continue 习语（对齐已修复的 GeographicAnomalyPattern）。**定性（fix-revert 实验记录）**：引擎 `getEventsForPattern` 为 branch-local 物化（NFA.java:935-950），线性 pattern 下 iterable 单元素、两版行为等价（revert 后测试仍绿）——本项为**复制模板正确性/示例一致性修复**（多元素 iterable 下分叉），非 live 行为缺陷；报告如实记录 | **新增** TestFraudAuditFixes：rapidTransactionMatchesUserPairWithOtherUserPartialFirst / rapidTransactionDoesNotMatchAcrossUsers / accountTakeoverCompletesUserChainWithOtherUserPartialFirst（NFA 真驱动多用户行为钉定）+ demoMainHappyPathRunsAllPatterns（FX-2 附属冒烟） |
| FX-2 | `main` catch-all 吞异常 exit 0 → LOG.error 后重抛（脚本化快速起步可检测失败） | demoMainHappyPathRunsAllPatterns（快乐路径钉定；失败路径注入不可行——豁免理由：需替换静态 MockTransactionGenerator，超出最小修复范围） |
| FX-3 | `DemoKeyedStateStore` 类级 Javadoc 契约警告（demo-only 语义三要点：每次 getState 新实例/keyed-in-name-only/UOE 边界） | 纯文档（No new test required per Rule #25） |
| FX-4 | README 修复：结构树补 state//util//stream.xml/TestFix；乱码行修正；删除不存在的 LICENSE/CONTRIBUT.md 引用；「MemoryStateBackend (configurable)」虚假声明改为 DemoKeyedStateStore 事实 + 新增 Known Limitations（含死 XDSL 文件披露） | 纯文档（doc-links 门禁验证） |
| FX-5 | TestGeographicAnomalyPatternFix 重写：删除内联复制的条件体（真类回归时测试仍绿的零价值回归），改为真 GeographicAnomalyPattern NFA 驱动 2 用例（contiguousPair 匹配 + interleaved 严格邻接不匹配——同时钉定 branch-local 与 strict contiguity 语义） | 重写本体即测试（2 用例） |

**端到端验证裁定**（plan Phase 3）：本审计修复未改变快照→恢复数据面语义（RK-3/RK-8 为恢复路径 fail-fast/兼容分支，非重处理语义变更）——既有端到端回归面承载：TestRocksDBSnapshotRestore 21 用例（round-trip）、增量族 4 测试（真实 SST 物化→range restore）、TestRocksDBIncrementalRestoreFailFast（restoreState 入口 fail-fast）；flow 侧 FL-3 修复含 XDSL 定义→执行端到端（timestampsWithoutAssignerExecutesWithoutNpe，parse→build→execute→sink 断言）；fraud 侧 FX 含 NFA 引擎真驱动路径。全模块 868 测试绿。

### 3.2 大缺陷/结构治理处置：Follow-up items 29/30 追加 + 显式路由

**新增 roadmap Follow-up（按 Rules 追加 Work Items 末尾，2026-09-01 落库）**：

- **item 29** [Follow-up，来源 item 11 plan（本报告 §2.5 flow 侧 F6.1/F2.1/F4.1 结构部分）]：flow DSL 编译器产品化收敛：① xpl source 取消语义（`XplSourceFunction.cancel` 的 volatile 标志无任何可观察路径——run 不读、SourceContext 无取消访问器、无线程中断；内联 xpl source 不可取消）② build 期错误源位置锚点（编译错误带 transform/edge id 但无 file/line——模型层已脱离 XML 位置，需 `_gen`/builder 传递 sourceLocation）③ per-transform parallelism 消费（core `Transformation.parallelism` 为 final，需 core API 扩展后由 builder 接线，替代本审计的 fail-fast 过渡）: `todo`
- **item 30** [Follow-up，来源 item 11 plan（本报告 §2.5 rocksdb 重复代码清单）]：rocksdb SerDe 克隆家族收敛：`RocksDBSnapshotSerDe` 8 snapshot + 8 restore 分支同构、`RocksDBKeyedStateBackend` 8 个 getXxxState 重载 85% 同构、状态类族 4 对克隆（List/InternalList、Appending/Internal*、Aggregating 两态、Map）——与 item 21（core 侧同族清单）联动执行避免双倍改动: `todo`

**显式路由（不新增 Follow-up）**：

| 项 | 路由 | 理由 |
|---|---|---|
| fraud-example 三缺口（Gap A XDSL 管线 / Gap B keyBy+窗口+keyed state / Gap C checkpoint+后端切换）+ demo 重写 + UnusualAmount 去 stub + UserTransactionHistory 复活 + 死 stream.xml 处置 + demo 装配测试面 | **items 12/13**（plan Non-Goals 明文：大规模示例重写属其范围；本审计交付 §2.4 自包含评估 + 缺口清单为其直接输入） | roadmap stage details item 11 Out of scope |
| flow `params`/capability/`maxParallelism` 的**实际消费**（fail-fast 过渡后的能力落地） | **items 12/13**（XDSL 场景设计天然消费）+ **item 20 邻域**（conf-validate 的字段级校验数据面） | FL-1 后无静默契约违反残留（declared→loud reject），剩余为能力缺口而非 live defect，且被既有 planned items 完全覆盖 |
| restore 侧 segment 内容 hash 重算校验 | **production roadmap P2 backlog 既有条目**（2300-2 显式 Out Of Scope「段内容 hash 校验」） | 既有裁定，本审计 §2.1 记录其结构性兜底（content-addressed path） |

**watch-only residual（逐条附 Why Not Blocking Closure）**：

| ID | 分类 | 发现 | Why Not Blocking Closure |
|---|---|---|---|
| W-R1 | watch-only residual | rocksdb `extractMapKey` 无界 4 字节长度读、`deserializeList` 非 List → empty、`readSstNameMap` 畸形行跳过 | 键/payload 均为自写格式；损坏输入终以异常或 MANIFEST 引用缺失 fail-fast 兜底；与 core 降级解析容忍基线一致（同 item 7 W-4 族） |
| W-R2 | watch-only residual | `RocksDBStateBackend(dbPath)` 单 key-group 默认构造（Javadoc 已自述建议显式传 DEFAULT_MAX_PARALLELISM） | 文档化设计取舍，测试/旧用法兼容 |
| W-F1 | watch-only residual | flow `checkpoint@storageType` 自由 string——错误值在 runtime storage 初始化期才报错（晚错误非静默） | 有效值集归 runtime 所有，flow 侧白名单会引入耦合；conf-validate（item 20）为正确落点 |
| W-F2 | → item 29 | build 期错误无源位置 | 结构性（§3.2） |
| W-F3 | watch-only residual | Delta removal delta 无测试、unused source 无告警、body-only delta 不影响 fingerprint（by-design） | generic XDSL 机制推定可用 + 拓扑层有 ERR_STREAM_REF_UNKNOWN 兜底；fingerprint 语义经 TestStreamModelDeltaFingerprint 显式裁定 |
| W-F4 | watch-only residual | `_type` 判别字段零消费（标准 codegen 输出）、`findEdge` O(E) 双查、测试侧 edgeChain 助手复制 | 无行为面；规模不变；item 21/30 重构时顺手评估 |
| W-F5 | watch-only residual | `AdvancedTransforms` 内置窗口 assigner 魔法目录（标注 test convenience；unknown id fail-fast） | 有边界且 fail-fast；正式目录化随 items 12/13 XDSL 场景设计裁定 |
| W-C1 | watch-only residual | `FraudAlert` ASF license 头与其余 9 文件 Nop 头不一致（疑为模板复制残留，但原始性无法确证） | license 头变更属法务/owner 裁定，审计不擅改；已披露 |
| W-C2 | watch-only residual | `MockTransactionGenerator` 无直接测试、`Math.random` 非确定、无乱序/迟到数据 | demo 值定位；测试正确避开随机性；乱序/迟到语义教学归 Gap A/B 场景 |
| W-C3 | watch-only residual | fraud README「Java 17+」与 AGENTS.md「Java 21」仓库级不一致 | 根 pom release=17 为事实，AGENTS 为环境约定；非模块缺陷，item 17 文档化时统一 |

**否决项（复核不采信）**：subagent 报告 F2.4（TestStreamModelDeltaFailFast「stale javadoc 称 must throw UOE」）——live grep 零命中，false positive；subagent 报告 fraud README「exec-maven-plugin 未接线致命令不可用」——CLI prefix 解析下命令可用性成立，保留 README 修正中的措辞谨慎处理（未宣称断裂）。

### 3.3 回归验证（2026-09-01 执行记录）

- `./mvnw test -pl nop-stream -am -T 1C` → **BUILD SUCCESS，868 tests / 0 failures / 0 errors / 9 skipped**（skipped 均为既有 gated 多 JVM 用例；rocksdb 93/0、flow 81/0、fraud-example 29/0，含本审计 15 个新 focused 用例 + 2 个重写用例）。
- `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` → BUILD SUCCESS（编译 + checkstyle 阶段）。
- `node ai-dev/tools/check-nop-stream-invariants.mjs` → 退出码 **0**。
- `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/{rocksdb,flow,fraud-example} --severity high` → **3×exit 0**。
- `node ai-dev/tools/check-doc-links.mjs --strict` → 退出码 0（本报告 + README + roadmap 修改后复跑，见 closure 记录）。
- `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` → 退出码 0（closure 时）。
- production roadmap P2 backlog 两条 rocksdb native-handle 条目状态写回 ✅ Closed（item 11 plan，RK-1/RK-2）。
- Owner-doc 裁定：**`No owner-doc update required`**——`state-management-design.md` 的 TTL/增量语义、`stream-dsl-design.md` 的三入口合一契约均未因修复改变（RK-4 是实现缺陷收敛而非契约变更；FL-1/FL-2 是把「未消费声明」从静默变为显式拒绝，stream-dsl-design 未承诺这些字段的消费语义）；fraud-example README 为模块文档（非 design owner doc）已做事实修复（FX-4）。trigger 语义证据与 D-GAP 输入的引用关系不变。

## Conclusion

- **历史审计收口**：05-20 逐组归属核对——无 rocksdb/fraud-example 专属组（显式记录），flow §7 空壳→已实现（引 item 7 结论）；06-30 seed+派生 10 项核对（landed/unchanged 为主，1 partial 路由 item 22，无 regressed）；2300-2 rocksdb 侧双分支 fail-fast 修复点 live 复核成立 + 2300-1/3 零三模块修复点确认。
- **产品化审计（D-GAP 三重点）**：①P-REQ-20 segment 级 checksum/schemaVersion 端到端真实性成立（双侧无条件填充 + 64-hex 产物断言 + 异常 fail-fast）；②flow XDef 完备性结论表落地（模型层字段级完整、连接器配置层 bean/xpl 间接 + build 期校验；5 类 silently-dropped 声明面收敛为 fail-fast）；③fraud-example 完整度评估 + Gap A/B/C 缺口清单自包含（供 items 12/17）。flow `_gen` 纪律 clean（补齐 06-30 §2.4 过时记载的核验缺口）。
- **修复与收口**：19 项小缺陷就地修复（rocksdb 8 + flow 7 + fraud 4；15 个新 focused 用例 + 1 fixture + 1 个重写测试类；2 项 production P2 backlog 收口写回）；结构治理 2 项转 Follow-up items 29/30；fraud 改造路由 items 12/13；item 22 枚举事实补全（rocksdb 4 + fraud-example 4，重写自愈 1）；全量回归绿 + 全部门禁 exit 0。
- **被否决的方案**：将 FX-1 上报为 live 行为缺陷（否决：fix-revert 实验证明 branch-local 语义下两版等价——如实降级为复制模板修复）；flow 侧直接消费 params/capability（否决：需连接器工厂/registry 设计，超出单审计 plan；fail-fast 过渡 + items 12/13/20 路由更合规）；为 RK-1/RK-2/RK-6 强写单测（否决：JNI 句柄/异常注入无 mock 基建，显式豁免并记录理由）；修改 FraudAlert license 头（否决：原始性无法确证，owner 裁定）；删除 `UserTransactionHistory` 死代码（否决：Gap B 现成素材，items 12/13 消费）。
- **后续工作**：items 12/13 消费 §2.4 缺口清单；item 17 消费可用性评估；Follow-up items 29/30 待调度；item 22 承接 rocksdb 4 + fraud 4 文件 sweep；M2 解锁条件（items 6—11 全 done）随本 item 写回成立。

## References

- `ai-dev/analysis/2026-05-20-nop-stream-duplicate-code-audit.md`、`2026-06-30-nop-stream-code-audit.md`（历史审计原文）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-design-productization-gap-analysis.md`（D-GAP §3.1 item 11 三项重点）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md`（item 7：§1.1 §7 空壳结论、§2.2 Flink/Beam 裁定、S-12 先例）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-runtime-module-audit.md`（item 8：§1.3 2300-2 runtime 侧复核）
- `ai-dev/analysis/2026-09/2026-09-01-nop-stream-cep-module-audit.md`、`2026-09-01-nop-stream-connectors-module-audit.md`（items 9/10：报告结构、§2.2 XDef 覆盖表）
- `ai-dev/plans/nop-stream-production/2026-08-04-2300-{1,2,3}-*.md`（复核对象）
- `ai-dev/backlog/nop-stream-production-roadmap.md`（P2 backlog rocksdb 双条目收口）
- `ai-dev/design/nop-stream/state-management-design.md`、`stream-dsl-design.md`（owner docs，裁定 No update required）
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`（XDef 合同）
- subagent 审计 session：flow 深审 `ses_fa2e2979bffemYF9crHPihXQ01`、fraud-example 完整度 `ses_fa2e26a5cffeGRbGE1DEqNpb58`（发现经执行者逐条复核，1 项否决）
- `ai-dev/plans/nop-stream-productization/2026-09-01-1457-3-rocksdb-flow-fraud-example-audit.md`（执行 plan）
