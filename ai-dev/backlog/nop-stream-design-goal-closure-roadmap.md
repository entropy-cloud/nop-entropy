# nop-stream 设计目标收口 roadmap——状态编码与恢复架构、分布式正确性闭环、能力与验证收口

> Last updated: 2026-10-18（v4 终稿：四轮独立审查收敛。v2 按首轮三路审查重写（边引用计数 / elementQueueState / live stateName / v2 CEP fixture / WI 拆分 / 性能裁定 / M1 依赖与 E2 方向）；v3 补多重集公式、恢复宿主与分派、安全三配置面、invariant-loop triage 脚本与里程碑翻转；v4 修 NFA 缺失守卫、矩阵表结构、A10 出处与 Minor 文本）
> 位置：按仓库 roadmap 惯例存放于 ai-dev/backlog/。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的文档路径用反引号，持续受 check-doc-links 保护；包名/类名/注解仍用代码格式。
> mission-driver 兼容：状态行尾部括注必须单层非嵌套（括注内不得再出现 `）` 或 `)`，否则整行被静默丢弃；改任何状态行后必须重跑解析核对 36 项。本块采用 `## Work Item Status` 与 `- WI<n> 名称: \`status\`` 形态，状态集为 `todo` / `ready` / `planned` / `done`。状态行尾部括注必须单层非嵌套。里程碑行写作 `- ★ **里程碑：<名>**（解锁条件）：\`status\``。**本 roadmap 的终态判定不只依赖 roadmapAllDone**：WI36 必须额外校验解析条数恒为 36 WI + 3 里程碑、状态集合合法、三个里程碑均为 done、且「矩阵外 open 项 = 0」（含 WI35 的 triage 结果）。roadmapAllDone 不把里程碑计入分母。
> 审计基线 commit = `2168ae056e`（2026-10-17）。规模基线：`nop-stream/pom.xml` 13 个模块；main 约 13.4 万行 / test 约 18.5 万行。现有 surefire 报告覆盖 10 个模块共 4320 用例 0 失败 / 18 skipped（core/cep/flow/runtime/rocksdb/connector/connector-batch/connector-jdbc/connector-debezium/connector-kafka）；`nop-stream-sql`、`nop-stream-fraud-example`、`nop-stream-format-avro` 无报告，WI3 重钉全部模块基线。
> 与既有 roadmap 的边界：`nop-stream-production-roadmap.md`、`nop-stream-productization-roadmap.md`、`nop-stream-independent-audit-roadmap.md`、`nop-stream-invariant-loop-roadmap.md`、`nop-stream-structure-refactor-roadmap.md`、`nop-stream-sql*-roadmap.md`、`nop-stream-xdsl-full-coverage-roadmap.md`、`nop-stream-multi-engine-comparison-roadmap.md`、`nop-stream-cdc-roadmap.md`、`nop-stream-deferred-unlock-roadmap.md` 的**已 done 项**不重复立项。仍在 open 的遗留项由本 roadmap 的「遗留归属矩阵」逐项认领或显式排除；性能项按 360/2279 已有裁定书归类，不重新打开已关闭项。**`nop-stream-invariant-loop-roadmap.md` 的 Follow-up Backlog 不在本矩阵逐项展开**（该文件为稳态暂停的循环图，含 130+ 条历史登记，状态未逐条重验）；由 **WI35** 单独做 triage（逐条判 stale/fixed/open 并把 open 映射到本 roadmap WI 或“明确不认领”），WI35 完成前 WI36 不得宣告“无未归属残留”。
> Sources:
> - `ai-dev/design/nop-stream/00-vision.md`（五条成功标准、§三约束、§八不变量、§四 Non-Goals、§六人审门）
> - `ai-dev/design/nop-stream/state-encoding-and-restore-design.md`（WI1 交付的状态编码目标模型）
> - `ai-dev/design/nop-stream/state-management-design.md` / `cep-design.md` / `checkpoint-design.md` / `checkpoint-engine-convergence.md`
> - `ai-dev/backlog/nop-stream-r5-successors.md`（R5 遗留登记）
> - `ai-dev/analysis/2026-09/2026-09-28-nop-stream-quality-perf-audit.md` + `ai-dev/audits/evidence/nop-stream-perf-360/convergence.md` + `ai-dev/audits/evidence/nop-stream-perf-2279/convergence-r2.md` + `ai-dev/plans/nop-stream-quality-perf/01-nop-stream-quality-perf-remediation.md`（性能裁定链）
> - `ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/`、`2026-10-06-0522-deep-audit-nop-stream-quality-r6/`、`2026-10-06-0522-adversarial-review-nop-stream/`
> - `ai-dev/plans/2326-nop-stream-r6-governance-reachable-p2.md`（R6 Deferred But Adjudicated 与 successor 触发规则）
> - `docs-for-ai/03-modules/nop-stream.md`、`nop-stream-user-guide.md`、`nop-stream-connectors.md`（已实现能力契约，WI36 回写）
> - `.github/workflows/maven.yml` / `compliance.yml`（现有门禁面）

## Purpose

把 nop-stream 从「核心引擎目标基本达成」推进到「五条成功标准全部有可复现证据、已知未关闭缺口全部关闭或由 owner 显式排除」。判断标准是以下六个可验证结果：

1. **成功标准 5 达成**：CEP 与 Memory / RocksDB 两个状态后端同时可用；不再存在"CEP 只支持 Memory"的永久组合限制。
2. **状态编码收敛**：durable logical 与 derived rebuildable 状态分离；`Lockable` 及其引用计数不再进入 checkpoint 且按确定性公式重建；第三方用户类型可配置地 checkpoint/restore；v2 legacy 只读兼容有含 CEP 字节的真实 fixture。
3. **分布式 exactly-once 闭环**：source enumerator 状态、分布式状态后端、快照失败即时传播、savepoint 恢复、EXPORT 物化五个缺口关闭；嵌入式分布式 E2E 进默认 CI，多 JVM E2E 有明确环境与降级等级（未实际执行不得标 e2e-proved）。
4. **能力模型与声明面收敛**：`IStateEncodingCapability`（状态后端）与 `StreamBackendCapability`（执行后端）分层落地；分布式路径做 requirement 校验；XDSL 声明面能表达分布式 checkpoint 配置。
5. **资源与性能有基线**：TTL/JDBC 2PC/retention/checkpoint 存储 fsync 等资源与耐久性残余关闭；性能项按已有 360/2279 裁定归类（实施、Deferred、watch-only 三类各自有量化依据），未裁定项补测后留舍；JMH 门禁与性能 baseline 入库。
6. **可维护性与文档收口**：上帝类与重复代码按既定拆分线收敛；架构依赖与 owner docs/README 一致；终态由独立 closure audit 判定。

**本 roadmap 不解除任何 Non-Goal**：K8s/YARN 集群编排与 HPA 弹性伸缩、异步算子、broadcast state、非等值/范围 join、Flink SQL 全栈与代价优化器、PB 级吞吐、运行时自动 reshard、Flink Runtime 结构复制均维持排除。需要扩大范围时必须走 vision §六 人审门，不在本 roadmap 内自行发明。

## Work Item Status

- WI1 状态编码与恢复设计文档修订: `todo`
- WI2 Owner 裁定门与 v3 格式授权: `todo`
- WI3 成功标准证据矩阵与全模块基线重钉: `todo`
- WI4 状态编码内核与恢复原子性: `todo`
- WI5 CEP durable schema 与引用计数重建: `todo`
- WI6 RocksDB 编码能力与增量 checkpoint 边界: `todo`
- WI7 用户类型白名单与 codec 安全边界: `todo`
- WI8 迁移机制生产接线与 v3 格式推进: `todo`
- WI9 CEP 乘 RocksDB 能力开启与成功标准 5 验收: `todo`
- WI10 分布式 source enumerator 状态闭环: `todo`
- WI11 分布式状态与存储后端传递及 fail-fast: `todo`
- WI12 快照失败即时传播: `todo`
- WI13 分布式 savepoint 恢复入口: `todo`
- WI14 EXPORT 物化契约: `todo`
- WI15 checkpoint 分叉项裁决落地: `todo`
- WI16 分布式恢复 E2E 矩阵: `todo`
- WI17 source stall liveness 检测: `todo`
- WI18 TaskProgress fencingEpoch 契约: `todo`
- WI19 执行后端能力模型实体化: `todo`
- WI20 XDSL 分布式 checkpoint 声明面: `todo`
- WI21 状态编码遗留与 CEP 小缺陷: `todo`
- WI22 资源上界与保留策略: `todo`
- WI23 flow 声明面残余: `todo`
- WI24 CEP 正确性残余: `todo`
- WI25 checkpoint 耐久性与资源残余: `todo`
- WI26 恢复与终止路径 P0 行为缺陷收口: `todo`
- WI27 R6 Deferred 治理批次二残余: `todo`
- WI28 数据热路径微优化: `todo`
- WI29 性能 Deferred 裁定复核: `todo`
- WI30 性能基线与门禁: `todo`
- WI31 CI 验证门禁与格式兼容 fixture: `todo`
- WI32 外部服务 E2E 编排: `todo`
- WI33 结构债拆分与注释收敛: `todo`
- WI34 依赖方向与文档一致性收口: `todo`
- WI35 invariant-loop Follow-up Backlog 处置审计: `todo`
- WI36 最终收口与独立 closure audit: `todo`
- ★ **里程碑：M1 状态编码内核收敛**（WI1—WI9 全部 done）：`todo`
- ★ **里程碑：M2 分布式正确性闭环**（WI10—WI18 全部 done）：`todo`
- ★ **里程碑：M3 设计目标全量达成**（WI19—WI36 全部 done）：`todo`

## Phase A——设计与裁定门

### WI1 状态编码与恢复设计文档修订

- 交付 `ai-dev/design/nop-stream/state-encoding-and-restore-design.md` 修订稿：三层状态分类、`StateSchema` + `IStateCodec` + `IStateKeyCodec` + `IStateRebuildUnit` 契约、恢复五阶段与宿主/时序、失败原子性、CEP durable schema（live stateName + elementQueue + eventClass + 边引用计数公式）、用户类型按面白名单、`IStateEncodingCapability` 分层、v3/v2 兼容、不变量。
- 回写并保持一致：`state-management-design.md` §6、`cep-design.md` §5.3.1/§8、`checkpoint-design.md` §2.6/§8.4.1、`ai-dev/design/nop-stream/README.md`。
- 验证：`node ai-dev/tools/check-doc-links.mjs --strict` exit 0；设计文档不包含实现演进叙事（design guide 允许目标架构，不允许 Proposed vs Current 叙事）；本 WI 的 done 只表示"修订稿完成"，技术验收由 WI2 与独立复审判定。
- 依赖：无。

### WI2 Owner 裁定门与 v3 格式授权

> **本 WI 是 M1 的硬门**：WI4—WI9 涉及持久化格式、codec 契约、恢复语义；未获裁定前不得进入实施。WI13/WI14/WI15/WI20 依赖本 WI 的对应裁定，WI2 完成前不得启动。

裁定项（每项给选项、推荐方向、影响面，由 owner 显式确认并落档）：

1. **v3 格式授权**：`CheckpointFormatVersions.CURRENT_FORMAT_VERSION` 2→3；v2 只读兼容窗口；是否提供 v2 写入灰度开关。推荐：只写 v3、v2 只读长期保留、不双写。
2. **状态编码契约落点**：codec/rebuild 注册放在 `StateDescriptor` 还是 `StreamComponents` 注册表（若选后者触发 vision §六 #1 的 StreamComponents 结构变更，需明确授权）。
3. **CEP 边引用计数重建方案**：按 §2.3 lockEvents 公式重建（推荐）vs 在逻辑 schema 中显式持久化 edgeRefCount；两者都禁止恢复置 0。
4. **stateName 策略**：v3 保持 live 名称（推荐，零迁移风险）vs alias/rename map（需 "未声明持久 state" fail-fast + v2 CEP fixture）。
5. **v2/v3 读取分派**：先按 manifest `stateFormatVersion` 判定（推荐）；v2 再按 stateName/family 选择 legacy 或 JSON reader，`__java_bytes__` 仅作 legacy reader 内部格式校验，不得以 payload 内容触发 Java 反序列化；v3 按 per-state `codecId` 分派。附带裁定：legacy 投影后是否用旧 refCounter 对账。
6. **operator state 范围**：v3 初版 operator states 继续走现有 JSON 快照面（推荐，控制爆炸半径）vs 纳入 codec（需独立设计）。
7. **incremental SST 边界**：v3 初版非 JSON codec 与增量 checkpoint 组合 typed fail-fast（推荐）vs per-segment codec（需独立设计）。
8. **用户类型白名单**：三套配置面分离（v3 state codec / v2 legacy Java state / transport）；旧键不是别名（推荐）；custom codec 受信边界声明。
8b. **eventsCount 类别**（推荐 Durable）与 **CEP event class 携带机制**（推荐声明面优先 + 首事件捕获兜底；空状态允许无类型恢复）。
8c. **binary codec 默认启用条件**（推荐显式 opt-in）与 v2 legacy reader 保留窗口。
9. **DIV-02**：checkpoint timeout abort 终局——设计意图（超时→作业失败态→恢复）vs 现行分布式代码（只弃 epoch）。推荐以 §8.7 为目标语义并补分布式 cancelTask 路径。
10. **DIV-04**：是否补齐分布式显式 savepoint 恢复入口（推荐补齐；否则"savepoint 等价"承诺不成立）。
11. **DIV-05**：CANCEL 终态语义——以 checkpoint-design §7.2/§7.3 为权威 vs 保留本地强化；需 owner 选边。
12. **vision §六 #3（分布式状态后端变更）**：允许分布式路径按 deployment descriptor 选择 RocksDB（WI11）。
13. **vision §六 #4（Checkpoint 协议变更）**：per-task 快照失败上报通道（WI12）是否构成协议变更；推荐认定为实现级控制消息、不改变 epoch 协议。
14. **范围缩减授权**：WI10/WI20 默认路线为 (a)；只有 owner 显式选择缩减（(b)）并同步修改 Purpose/成功标准矩阵/vision 时才成立，且 M2/M3 不得宣称对应目标达成。
14b. **WI14 materializer 四契约**（materialize 类型/预算源/KeyGroup 归属戳/storage 实例可达）的取舍与 EXPORT 产物语义。
15. **maxConcurrentCheckpoints > 1 追认**：Stage 45 已实现 aligned 多 in-flight，但 vision §六裁决记录只覆盖 unaligned；补一条追认或明确不属于 #4。
16. **binary codec 与 vision §四 Non-Goal 边界**：确认"允许显式 opt-in 二进制 value codec"不等于"复制 Flink 二进制序列化体系"。
- 交付：设计文档 §七逐条回填；vision §六裁决记录（如需）；`nop-stream-r5-successors.md` 的 DIV 条目状态更新。
- 验证：每项裁定有选项、理由、影响面、后续 WI 映射；无"待定"项进入 WI4；doc-links exit 0。

### WI3 成功标准证据矩阵与全模块基线重钉

- 产出「成功标准 → 可执行断言 → 测试/命令 → 环境等级（LOCAL / embedded distributed / 多 JVM / 真外部服务）」矩阵；每条至少一个可复跑证据行，禁止 component-only 冒充 e2e-proved。
- 重钉全部 13 个模块的测试基线（含 `nop-stream-sql`、`nop-stream-fraud-example`、`nop-stream-format-avro`），记录 run/fail/skip、测试文件数、命令与时间；作为后续 WI 的"不得下降"基线。
- **生成 CEP v2 fixture**：用改动前代码写出含 `nfaStateName`/`sharedBuffer-*` `__java_bytes__` 状态的 checkpoint；与现有 source-job legacy fixture 并列入库，作为 WI5/WI8/WI31 的 legacy 证据。同步核对 v2 fixture 的 `stateFormatVersion=2` 与 envelope 语义。
- 验证：矩阵逐行可复跑；fixture 可被现有 reader 读取并断言 CEP 状态非空；命令与结果入库。

## Phase B——状态编码与恢复内核（完成门：M1，含 Phase A 的 WI1—WI3）

### WI4 状态编码内核与恢复原子性

- 在 `nop-stream-core` 落地状态三层分类与编码契约：`StateSchema`、`IStateCodec`、`IStateKeyCodec`（MapState key）、`IStateRebuildUnit`/`StateRebuildContext`（per-key/per-namespace、同 unit 跨 family 只读访问）。
- MapState 语义固定为 per-entry codec；TTL sidecar 声明为 Derived/Ephemeral，不进入 schema，恢复后按 state-management §12 重新绑定；operator state 沿用现有 JSON 面（由 WI2 裁定确认）。
- 恢复五阶段编排：load → migrate（eager，不依赖懒 getState）→ rebuild → validate → activate；定义宿主组件与 CEP timer 再武装时序（从 `open()` 移到 activate）。
- **恢复原子性（B2）**：禁止 clear-before-validate；load 到暂存结构或失败即丢弃 backend 实例；validate 失败 = 作业失败，按 latest durable epoch 现有全局恢复路径处理（不承诺隐式 older-epoch）。
- 未声明类别/未知 codecId/未知 stateName 均 typed fail-fast；"快照中存在但本作业未声明 state"必须显式检测。
- 验证：`TestStateCodecContract`（encode/decode、per-entry MapState、key codec、TTL 重绑、未知 codec/state fail-fast、rebuild 幂等、validate 失败不激活）；`TestRestoreAtomicity`（损坏快照不留下可运行部分状态）；core 全量绿且不低于 WI3 基线。

### WI5 CEP durable schema 与引用计数重建

- 按设计 §3.5 实现，物理 stateName 沿用 live 名称：`sharedBuffer-events`、`sharedBuffer-entries-with-lockable-edges`、`sharedBuffer-events-count`、`nfaStateName`、`eventQueuesStateName`、`cep-current-watermark`、`cep-event-time-timers`、`cep-key-class`、`cep-event-class`。
- **必须包含 `eventQueuesStateName`**：`MapState<Long, List<EventPayload>>`（ET watermark 之上/PT 时间桶的缓冲事件）；PT 排水依赖它，不能从 events 推导。⚠ 与 NFA 缺失/空守卫的关系按设计 §3.5：仅 events/entries 全空才允许初始态，**eventQueues 不参与门禁**（首事件 > watermark、reset 后仍有未来 bucket 均可合法非空）。
- **用户事件 payload 类型来源**：声明面显式类型优先；未声明时首事件捕获 + checkpoint 携带 event class（规则同 key class）；仅在“存在需要按该类型解码的 event payload”时 fail-fast（空状态/全超时清空后的 checkpoint 允许恢复）；多态事件需显式基类或注册 codec；v2 投影从旧对象图运行时类型推导，推导失败按上述规则处理。
- **引用计数重建**：逐 key 按 **lockEvents 多重集**公式重建 nodeRef/edgeRef/eventRef（同节点同 dewey 重复加锁必须重复计数）；partial 与 completed computation states 都计入；边计数不得置 0；eventRef 由节点引用数重建；孤立 ref=0 条目不自动删除，validate 记录候选并由本 WI 给出有界清理方案。
- NFAStateRecord 为扁平记录；PriorityQueue/comparator 由代码重建；stateChanged/isNewStartPartialMatch 恢复为 false；**记录缺失/空恢复为 initial NFA state**（全超时清空后的合法态），不得物化为空队列。
- **v2 legacy**：格式分派由 manifest `stateFormatVersion` + per-state `codecId` 是否存在决定，`__java_bytes__` 只作 legacy reader 内部 marker（不作为 Java 反序列化触发条件）；旧对象图 → DTO 投影 → 同一 rebuild/validate；用 WI3 的 CEP v2 fixture 验证；旧 refCounter 仅用于 validate 对账。
- 验证：`TestCepEdgeRefcountRebuild`（a→b→c、a,b,b、**同节点同 dewey 多处 lock（optional-final 双锁）**、多父边同版本；人工置 0 必红）；`TestCepBufferedEventRestore`（事件入队后 checkpoint，恢复后仍被处理）；`TestCepFirstEventBufferedRestore`（首个事件时间戳 > watermark 时 checkpoint：NFA 记录为 null、eventQueues 非空 → 恢复成功且事件最终被处理）；`TestCepNfaClearedWithFutureBucketRestore`（`resetNfaStateIfFullyTimedOut` 清空 NFA 后仍有未来 bucket → 恢复成功）；`TestCepEmptyStateRestore`（全超时清空后恢复仍是初始态且新事件可匹配）；`TestLegacyCepV2Restore`；多 key 同 EventId 不串线；cep 全量绿。

### WI6 RocksDB 编码能力与增量 checkpoint 边界

- `RocksDBKeyedStateBackend` 接受 `IStateCodec` 字节直存；默认 JSON codec 行为等价，binary/custom codec 显式 opt-in；保持 key layout/key-group 语义不变。
- 引入 `IStateEncodingCapability`（状态后端）：`json` / `binary` / `custom` / `derived-rebuild`；Memory 与 RocksDB 都实现该能力面（Memory 实现可落在 WI4，RocksDB 实现落在本 WI）；execution 层 `StreamBackendCapability` 保持独立（WI19）。
- **增量 checkpoint 边界**：v3 初版非 JSON codec 与增量 SST 组合在部署/启动期 typed fail-fast；JSON codec + 增量的 migrate/validate 语义必须明确（固定 schema 版本、禁止迁移，或把增量排除出 v3 兼容面）；如后续要支持 per-segment codec，另立设计（WI2 裁定）。
- 顺带修复 B8（`RocksDB.open` 抛异常时 dbOptions/cfOptions JNI 句柄不关闭）。
- **A12-RocksDB serde**：MapState 回退布局在 RocksDB 下 Object 值经 JSON 包装层无法回读（public 构造器路径可达）→ 在 codec/restore 面修正并补 public-API 路径测试。
- 验证：`TestRocksDBStateCodec`（JSON/binary round-trip、跨重启恢复、能力拒绝、增量+非 JSON fail-fast、增量+JSON 的 migrate/validate 边界）；RocksDB 全量绿；JMH 记录 binary codec 的 get/put 收益基线。

### WI7 用户类型白名单与 codec 安全边界

- **B7（用户状态类白名单无逃生口）**：三套配置面分离：v3 状态 codec 用新键 `nop.stream.state.codec.allowed-prefixes`；v2 legacy Java 状态沿用旧键 `nop.stream.state.deserialize.allowed-prefixes`（保持现状不放宽）；传输/线协议用 `ClassNameValidator` 独立 baseline。旧键不是新键的别名；合并必须显式安全评估。
- **已知缺口认领**：`PollingJdbcMessageService` 的 JDBC message 路径用裸 `ObjectInputStream`（无 `ObjectInputFilter`）；本 WI 补 filter + 负向测试，或在“明确不认领”登记为已接受缺口（二选一，不能声称已有 baseline）。
- 写侧/读侧对称：容器元素类型在 encode 侧也校验；无法解析时 fail-fast（禁止默认 WARN + JSON-native 降级）。
- 类加载：白名单通过后 `Class.forName`，失败再试 TCCL；两次失败均 typed 错误。
- custom codec 受信边界：平台只对内置 codec 强制白名单；JSON 多态 `@type` 定义**内置允许集**（TimeWindow/GlobalWindow/VoidNamespace/内置容器/DTO），用户类型显式注册后才允许。
- 验证：`TestUserStateTypeCheckpointRestore`（第三方前缀配置可恢复；未配置写侧 fail-fast；容器元素写读对称）；TCCL 场景；`@type` 内置允许集正反例；JDBC message 反序列化 filter 负向测试；memory + rocksdb 双后端。

### WI8 迁移机制生产接线与 v3 格式推进

- 生产 restore 路径注入 `StateMigrationRegistry`（本地 GMCE、分布式 TM、CEP 内部 backend 创建路径）；未注册迁移时 `ERR_STREAM_STATE_SCHEMA_MISMATCH` fail-fast。
- 指纹升级为 `codecId + schemaVersion + schemaChecksum`；迁移函数作用于逻辑值（decode 后、rebuild 前）；`schemaVersion` 由 StateSchema 提供递增来源。
- `CheckpointFormatVersions.CURRENT_FORMAT_VERSION` 2→3；信封 formatVersion 与 manifest stateFormatVersion 同步；v2 只读兼容；旧引擎读 v3 typed reject。
- **读取分派**：manifest `stateFormatVersion` + per-state `codecId` 是否存在决定 v2/v3；restore context 显式携带 formatVersion，从 manifest 下传到 keyed state restore；禁止以 payload 内容触发 Java 反序列化。
- 完整回写 checkpoint-design §2.6/§8.4.1（含 lazy migrate 改 eager、fingerprint 匹配规则由 checksum 扩展为 codecId+schemaVersion+checksum、删除“schemaVersion 恒为 1”的当前态矛盾）、state-management §6.3 的版本/迁移语义（WI1 只做指针与局部校正，最终一致性在本 WI）。
- 验证：`TestStateMigrationWiring`（生产注入、迁移命中、未注册 fail-fast）；`TestCheckpointFormatV3Compatibility`（v2 fixture 恢复、v3 写入、版本面不一致 reject、旧 reader reject v3、formatVersion 下传链路断言）；两后端一致。

### WI9 CEP 乘 RocksDB 能力开启与成功标准 5 验收

- 删除 `CepOperator` 的后端类名字符串判断；改查 `IStateEncodingCapability`（WI6）。
- CEP 在 RocksDB 上完成 checkpoint/restore/kill-recover E2E；Memory 与 RocksDB 的匹配输出、refCounter 守恒、timer 语义一致。
- **embedded distributed CEP E2E 不在本 WI 验收**：它依赖 WI11 的分布式状态后端传递（M2），由 WI16 在 WI11 完成后覆盖；本 WI 只验收 Memory/嵌入式 RocksDB 的 CEP 能力。
- 更新成功标准 5 证据行；owner doc 移除"CEP 限 Memory"（最终回写归 WI36）。
- 验证：`TestCepRocksDBExactlyOnceE2E`（跨 checkpoint 匹配恰一次 + restore 后续匹配）；memory/rocksdb 参数化双跑；矩阵行更新。依赖：WI5/WI6/WI7 全部 done。

## Phase C——分布式正确性闭环（完成门：M2）

### WI10 分布式 source enumerator 状态闭环（DIV-03 / 2026-10-AR-04）

- 事实：`SourceCoordinatorRegistry` 为 TM JVM 静态表且按裸 vertexId 作键；`GraphModelCheckpointExecutor` 只注册 source vertex；分布式 JC 侧 `CheckpointCoordinator.snapshotSourceEnumerators` 读不到 TM 注册表 → manifest 的 `sourceEnumeratorSnapshots` 为空，恢复重扫已完成 split。
- 处置（**默认路线 (a)，不提供 (b) 收口捷径**）：
  - (a) 上收：TM 在 checkpoint ACK 中携带 enumerator 状态，JC 合并进 manifest；恢复时随 deployment descriptor 下发；需 RPC/协议扩展（优先复用 ACK 结构）。
  - 如果 owner 选择 (b) 显式不支持，必须按 WI2 #14 改 Purpose/成功标准矩阵/vision，M2 不得宣称该目标达成。
- `SourceCoordinatorRegistry` 键改为 `jobId + vertexId`（多作业同 JVM 串线风险），补清理断言；registerSourceApiVertices 的接线点扩展到分布式路径。
- 验证：`TestDistributedSourceEnumeratorRestore`（file source 多 split：kill 后恢复不重扫已完成 split，恰一次）；多作业同 JVM 不串线；gated 多 JVM 至少一条。

### WI11 分布式状态与存储后端传递及 fail-fast

- 事实：`RemoteTaskDeploySupport` 远端部署固定 provision `MemoryStateBackend`，用户配置 RocksDB 时无警告、无 typed error（DIV-06）；S3 checkpoint storage 只在嵌入式/单进程 createStorage 生效，分布式执行器/savepoint override 仍落 local。
- 处置：`TaskDeploymentDescriptor` 携带 state backend 类型/配置与 checkpoint storage 配置；TM 按 descriptor provision；不支持组合部署期 typed fail-fast，禁止静默 local/内存回退。
- 验证：`TestDistributedStateBackendPropagation`（RocksDB 配置生效、恢复后状态一致、未支持类型 fail-fast）；S3 分布式传播或显式 fail-fast；与 WI6 联测。

### WI12 快照失败即时传播

- 事实：分布式 `RemoteTaskDeploySupport.remoteAbortListener` 只 LOG，等待 coordinator 侧 timeout abort（DIV-01）；本地路径即时传播。
- 处置：新增 per-task 快照失败上报通道（RPC 方法或复用 ACK/状态上报结构），携带 fencing epoch；JC 收到后立即 abort 对应 epoch 并触发恢复；重复/迟到上报幂等、standby 拒绝；是否构成协议变更按 WI2 #13 裁定。
- 验证：`TestDistributedSnapshotFailureFastAbort`（注入 sink saveState 失败，断言 abort 时延远小于 checkpointTimeout、epoch 精准、后续 checkpoint 正常）；gated 多 JVM 一条。

### WI13 分布式 savepoint 恢复入口

- 补齐"从显式 savepoint 路径启动"的分布式入口（DIV-04）：coordinator 支持 `savepointPath` 恢复参数；与 checkpoint 目录自动恢复互斥且显式；路径不存在/格式错 typed fail-fast。
- 验证：`TestDistributedSavepointRestore`（导出 savepoint → 停集群 → 指定路径恢复 → 状态/输出等价）；无 savepoint 路径时行为不变。

### WI14 EXPORT 物化契约

- 接入 `SavepointMaterializer`（结构重构 WI11 的 defer 项）：解决 materialize 类型、预算源、KeyGroup 归属戳、storage 实例可达四个契约缺口；EXPORT 产物语义（不返回路径 vs 返回路径）保留为面级参数。
- 验证：EXPORT E2E（作业不停、产物可恢复）；materialize 契约测试；与 WI13 的恢复入口联动。

### WI15 checkpoint 分叉项裁决落地（DIV-02 / DIV-05 / B12）

- 按 WI2 裁定实现 DIV-02（timeout abort 终局）、DIV-05（CANCEL 终态语义）、终态预算默认值归一；不允许"文档与代码相反"。
- **B12**：用户显式禁用 checkpoint 时 `buildJobGraph` 静默强制开启（`StreamExecutionEnvironment`）→ 改为尊重显式禁用或 typed 拒绝。
- **D4**：CheckpointCoordinator monitor 内持有阻塞 RPC fan-out（锁内外部 I/O）→ 移出 monitor 或改用独立 executor，补并发/锁纪律测试。
- 同步 `checkpoint-design.md` §7.2/§7.3/§8.7 与 `checkpoint-engine-convergence.md` §4.3；r5-successors 对应条目销号。
- 验证：定向测试覆盖两侧分叉点（本地/分布式同输入同结果）；差分 harness 白名单更新；文档无相反表述。

### WI16 分布式恢复 E2E 矩阵

- 交付恢复矩阵：enumerator / state backend / snapshot failure / savepoint / EXPORT / fencing / kill-recover / rescale 组合；每一步标注环境等级与执行证据。
- 嵌入式分布式（同 JVM 多 TM）全套进默认测试面；多 JVM gated 关键路径（kill -9 TM、coordinator failover、并行 2PC、CEP+enumerator）进夜间/手动执行，并记录命令与结果。
- 验证：矩阵逐行可复跑；失败即阻塞 M2；gated 命令写入用户文档；未执行项标 blocked 而非 e2e-proved。

### WI17 source stall liveness 检测（AR-05）

- 事实：SOURCE/SELF_CONTAINED 角色 liveness 用墙钟恒新鲜，挂死 source 对 stall 检测不可见。
- 处置：独立长窗口 source stall 检测（可配 `sourceStallTimeoutMs`，默认关闭或小时级），基于 `max(lastProgress, lastActivity)`；不改变既有"空闲 source 不误杀"语义。
- 验证：挂死 source 定向测试（超时后触发 stall 恢复）；空闲 source 不误杀对照测试；指标/日志可观测。

### WI18 TaskProgress fencingEpoch 契约（CC-05）

- `TaskProgress` DTO 携带 `fencingEpoch`，替代短期 attemptNumber 过滤；与既有 fencing 测试族联测；明确向后兼容与旧 TM 拒绝语义。
- 验证：DTO/协议单测；stale 报告拒绝；多 JVM gated 一条。

## Phase D——能力模型、声明面与残余正确性（M3 组成，与 Phase E/F 共同收口）

### WI19 执行后端能力模型实体化

- `StreamBackendCapability`（执行后端）从"只有 localRuntime 硬编码实例"升级为真实契约：本地执行与分布式执行各自声明支持的 `StreamRequirement`、分布式执行、远程状态服务、rescale；分布式构建路径执行 `StreamRequirementValidator`。**Memory/RocksDB 的状态编码能力不在此层**（它们实现 `IStateEncodingCapability`，见 WI4/WI6）。
- 与 `IStateEncodingCapability`（WI4/WI6）分层，不混用。
- 验证：`TestStreamRequirementDistributedValidation`；能力矩阵参数化测试。

### WI20 XDSL 分布式 checkpoint 声明面

- 事实：`StreamModelDslBuilder` 对 `deploymentMode=DISTRIBUTED` + 活动 checkpoint 是 typed reject；`StreamExecutionEnvironment.execute()` 在 checkpoint 开启时先走 checkpoint 引擎路径，永远到不了 distributed 分支。
- 处置（**默认路线 (a)**）：支持 `<checkpoint>` 配置进入 DeploymentPlan/launch 配置，并**包含 execute() 分发改造或 launch-only 入口**；若 owner 选择 (b) 维持 reject，必须按 WI2 #14 同步改 Purpose/矩阵/vision，不能只写文档。
- 验证：XDSL 场景（DISTRIBUTED + checkpoint 声明）E2E；或 (b) 的文档矩阵 + conf-validate 提示 + owner 裁定记录。

### WI21 状态编码遗留与 CEP 小缺陷（R5-ST-05 / R5-ST-18 / B9 / B10 / B11 / B13）

- **R5-ST-05**：容器类型 ValueState 元素类型在恢复链路丢失。唯一 owner = WI7 实现；本 WI 只做回归证据（不重复实现）。
- **R5-ST-18**：增量快照共享 SST 的 task-local 绝对路径依赖（显式 fail-fast + 文档，或内容寻址）。
- **B9**：`CepOperator.deleteProcessingTimeTimer` / `forEachProcessingTimeTimer` 空实现 → 实现或 typed 显式不支持（processing-time timer 可取消语义）。
- **B10**：SharedBuffer match 物化 NPE 丢 eventId、lockNode null 静默跳过 → typed 错误。
- **B11**：`Lockable` 已零计数 release 先 set(0) 再抛、equals/hashCode 含可变 refcount → 语义修正。
- **B13**：错误码不一致（如 `JdbcTwoPhaseCommitSink` 用 NULL_ARG 表达范围违规、File sink 泄漏 JDK `UnsupportedCharsetException`）→ 归位 typed 错误码；本 WI 为 B13 唯一 owner。
- 验证：每项定向测试；cep/core 全量绿；r5-successors 对应条目销号。

### WI22 资源上界与保留策略（R5-ST-12 / R5-CON-11 / R5-ST-16 / A10）

- **R5-ST-12**：内存 TTL 已有周期 sweep（`ttlSweepIntervalMillis`）；**RocksDB 仍只在 snapshot 时 sweep** → RocksDB 周期清理/上界。
- **R5-CON-11**：JDBC 2PC 整 epoch 驻留内存无上限 → 有界/落盘 spill 或显式上限失败。
- **R5-ST-16**：retention 双平面差异（checkpoint 平面按 jobId、manifest 平面按 jobId+pipelineId）已被 checkpoint-design D2b 文档化为"生产单 pipeline 下等价"；本项只做"是否统一"的裁决记录，不重复实现。
- **A10**：`completedTasks` 超限随机驱逐（非 LRU），刚完成任务结果可能被逐出 → LRU/按序驱逐，补定向测试。
- 验证：RocksDB TTL 有界性测试；JDBC 2PC 内存上界测试；ST-16 裁决记录；A10 驱逐顺序测试。

### WI23 flow 声明面残余（R5-AR-06/08/09）

- **R5-AR-06**：xpl watermark 生成器 `onPeriodicEmit` 恒空、`interval` 声明对该组合静默失效 → 实现或 typed reject。
- **R5-AR-08**：重复 `from→to` 边不校验、`findEdge` 取首条 → 构建期 fail-fast。
- **R5-AR-09**：`<checkpoint enabled="true" interval="0">` 静默禁用 → 与 0 语义统一并 fail-fast/显式裁定。
- 验证：flow 定向测试 + XDSL 场景回归；conf-validate 报错可定位；文档同步。

### WI24 CEP 正确性残余

- **CEP-05**：`copyForSubtask` 共享 `nfaFactory`（Rich 条件 + 并行子任务竞争）→ 按子任务拷贝/隔离或证明工厂不可变并加断言。
- **CEP-06**：NFAState `toString().split` 辅助比较器双标准 → 统一比较器。
- **TE-03/TE-10**：qualifier 家族语义断言增强、NFA 超时 × skip 策略交叉测试。
- 验证：并行子任务 CEP 测试（Rich 条件竞争）；比较器单测；超时 × skip 矩阵测试；cep 全量绿。

### WI25 checkpoint 耐久性与资源残余（2026-10 AR-06/07/08/09）

- **2026-10-AR-06**：JDBC 2PC ledger 无 prune/retention → 有界清理策略（按 epoch 窗口/保留数，运维可配）。
- **2026-10-AR-07**：`LocalFileCheckpointStorage` 原子 rename 但无 fsync → checkpoint/manifest 的 temp 文件 move 前 `FileChannel.force`（与 file 2PC sink 纪律对齐）。
- **2026-10-AR-08**：`JobCoordinator.reportTaskStatus` FAILED 分支锁外写 `subtaskLiveness` → 统一锁纪律/tombstone 守卫。
- **2026-10-AR-09**：`AbstractStreamOperator.snapshotState(null)` 声称 null 安全但 participant/backend 分支解引用 → 契约修正（null 安全或 typed 拒绝）。
- **CC-17**：`taskExecutor` 无界队列 + 终态 RPC 在任务线程同步发送的残余面（结构重构 WI10 只收口了有界化/异步半，r5-successors 仍列兜底复核）→ 复核并补剩余验证。
- **A11**：恢复/checkpoint 路径锁序未文档化（recoveryLock→CC monitor→RPC→commitExecutor→用户 JDBC 链式嵌套）→ 文档化锁序并用结构/并发测试守住。
- 验证：ledger 有界性测试；fsync 故障/顺序测试；锁纪律结构/并发测试；null 契约测试；CC-17 复核记录；对应模块全绿。

### WI26 恢复与终止路径 P0 行为缺陷收口（A3/A4/A5/A6）

- A3：终态 savepoint 失败/超时后仍上报 JOB_FINISHED + `health.onFinished`（静默数据丢失语义）→ 失败态/告警。
- A4：`stop()` 仅 gate 在 running，standby 可 shutdown 共享 CheckpointCoordinator → active gate。
- A5：背压+materialization 边队列满时记录静默移出 live 路径、bypass store 无界 → 有界/可观测/失败语义。
- A6：region 重启时 live map 缺 key 仅 WARN 跳过、region 有洞 → fail-fast/修复。
- 验证：每项定向测试；恢复/终止 E2E；与 WI25 的 checkpoint 耐久性联动。

### WI27 R6 Deferred 治理批次二残余

- 承接 plan 2326 的 Deferred But Adjudicated（Successor Required: yes）：
  - R6-ST-04 / R6-ST-05 / R6-ST-06 增量 checkpoint 族（任何接线增量 checkpoint 的计划必须先修/同批修）。
  - R6-RD-01/02/03（结构治理）、R6-PF-01/02（性能候选）、R6-TE-04（gated 覆盖面）、R6-ST-08（RocksDB 容器族绕过）、R6-REG-07（R5 CON-04 截断提交面）。
- 逐项给出处理或显式排除理由；触发规则写死（如 R6-ST-04/05/06 只有接线增量 checkpoint 时才处理）。
- 验证：逐项处置记录；不引入回归；R6 治理批次二 successor（拟名，避免与既有 2327-* plan 文件名冲突）与 WI 映射。

## Phase E——性能（M3 组成）

### WI28 数据热路径微优化（C3 处置）

- **C3 已有 disposition**：`ai-dev/plans/nop-stream-quality-perf/01-nop-stream-quality-perf-remediation.md` 将其归入“其余 P2 杂项：optimization candidate，<2% 预估，Successor Required: no”。本 WI **默认不实施**；完成态 = 在性能台账记录该 disposition 并做一次复核（若 owner 重开，必须按 WI29 规则附新 JMH 证据 + owner 授权，另立实施项）。
- C1/C2/C4/F5 按已有裁定登记为 closed/watch-only/Deferred（见「性能项裁定台账」），不在本 WI 实施。
- 验证：C3 处置记录入库；无代码变更（除非 owner 重开）；与 plan 01 证据双向链接。

### WI29 性能 Deferred 裁定复核

- 逐项复核并登记（不默认实施）：D1（生产不可达）、D2（RocksDB evictor O(n²)，Deferred）、D3（checksum canonical 格式，Deferred；注意与 `stateFormatVersion` 不是同一版本面）、E1（已由 2279 Q1/Q1b 覆盖）、E2（watch-only）、E3（已修复，LazyVerdictCache）、F3（SharedBuffer 缓存替换 guava→Caffeine，Deferred）、C1/C2/C4（closed/watch-only）。
- 每项给出裁定来源、当前状态、是否重开（重开必须附新证据 + owner 授权）。
- 验证：裁定台账入库；无代码变更或仅 owner 批准的重开项；与 360/2279/plan 01 证据双向链接。

### WI30 性能基线与门禁

- 以既有 `nop-benchmark/nop-benchmark-stream` 与 `ai-dev/audits/evidence/nop-stream-perf-{360,2279}/` 结果为 before 基线；定义各场景回归阈值（按 2279 方差协议）。
- 形成可执行门禁：默认 CI 跑快速 smoke（单 fork、短迭代）防止明显回退；完整基准走夜间/手动。
- 明确 benchmark 模块（不在 `nop-stream` 13 模块 reactor 内）的构建/运行入口。
- 验证：门禁脚本可复现；人为回退能被捕获（正反例）；文档记录运行命令与判定口径。

## Phase F——验证面、结构债与最终收口（完成门：M3）

### WI31 CI 验证门禁与格式兼容 fixture

- 在 `.github/workflows/` 固化：embedded distributed E2E 子集进默认 CI；多 JVM gated 关键路径进 nightly/workflow_dispatch（明确 runner/环境，未执行不得标 e2e-proved）；v2→v3 checkpoint fixture（含 CEP）跨版本恢复进默认 CI；状态编码不变量门禁（无 Lockable/Java 字节进入 v3 CEP 状态；能力矩阵校验）；性能 smoke。
- 验证：每类门禁有正控与反例；CI/证据入库；文档记录触发条件与豁免。
- 依赖：WI16/WI25/WI30。

### WI32 外部服务 E2E 编排

- 为 Kafka/Pulsar/MinIO/CDC 真服务 gated 测试定义可执行环境（testcontainers/docker compose/外部实例）、凭据隔离、超时、清理与降级路径；建立 owner 与运行脚本。
- 若环境不可得：在 roadmap/成功矩阵中显式标 blocked 或 out-of-scope，禁止以"测试存在"冒充"能力已证"。
- 验证：至少一条真服务 E2E 在本机/CI 实际执行并留证；其余明确 blocked/out-of-scope。

### WI33 结构债拆分与注释收敛

- 上帝类拆分（R5-RD-05 等）：`JobCoordinator`（约 2955 行）、`WindowOperator`（约 2796）、`StreamModelDslBuilder`（约 2473）、`CheckpointCoordinator`（约 1892）、`GraphModelCheckpointExecutor`（约 1842），以行为保持差分门为前置；拆分顺序在 WI5/WI15/WI20 之后。**本 WI 只做类级拆分**，不混入依赖方向与文档改动。
- **G2/G3/G10/G12（结构治理）**：InputGate 拆分、CepOperator 拆分、serde/执行会话合并类结构治理，纳入本 WI 的拆分清单（与上帝类拆分同批，按依赖顺序执行）。
- 重复代码（R5-RD-06/07/08）与考古注释（R5-RD-13）收敛；镜像测试（R5-TE-07）批量处置。RD-13 的计数以可复现命令为准：`Stage [0-9]+` 448、`plan [0-9]{3,4}` 819、`WI[0-9]+` 1036（2026-10-18 实测，命令写入 WI 记录）。
- 验证：structure/invariants 门禁 exit 0；拆分前后差分测试全绿；无行为变更。

### WI34 依赖方向与文档一致性收口

- 依赖方向：消除 `nop-stream-runtime` 对 `nop-stream-flow` 的 compile 依赖（仅 3 个 maintain CLI 文件使用）——提取 ops 叶子模块或改 SPI；同步 `01-architecture-baseline.md` §1.4 与 `nop-stream/nop-stream-runtime/pom.xml`。
- 文档一致性：`nop-stream/README.md` 入口类 test-scope 过期表述、模块数 11→13、design README 的 flow "(规划)" 与 runtime→flow 依赖缺失；`docs-for-ai/04-reference/source-anchors.md` 锚点。
- 验证：structure gate exit 0；doc-links exit 0；pom 依赖树无 runtime→flow compile 边（或显式登记例外并更新架构基线）。

### WI35 invariant-loop Follow-up Backlog 处置审计

- 对 `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 的 Follow-up Backlog 做**条目级** triage（单位 = 带 `Status:` 的条目；2026-10-18 实测：100 个状态条目，其中 94 `todo` / 3 `done` / 3 closed；文件该节共 154 个 bullet，含描述子项）。分类为 `fixed/stale/open`，open 项映射到本 roadmap 的 WI 或“明确不认领 + owner 接受理由”。
- 重钉已修项证据（例如 RunningTask 身份化 remove、RocksDB Options try-with-resources 等），避免“一处 done 一处 todo”僵尸状态。
- **交付可复跑脚本** ai-dev/tools/check-nop-stream-residual-closure.mjs（或等价工具）：输入 = 本 roadmap 矩阵 + WI35 triage 表 + 原 backlog 状态；输出 = `矩阵外 open 项` 集合（必须为空）与未映射条目清单；exit 非 0 即阻塞 WI36。脚本与用例入库并进 CI（WI31 可选接入）。
- 产出：triage 表 + 原文件状态更新 + 脚本；本 WI 完成前 WI36 不得宣告“无未归属残留”。
- 验证：脚本 exit 0；100 个状态条目全部有分类与去向；`grep 'Status: `todo`'` 的剩余项全部有“不认领”或“映射 WI”记录；doc-links exit 0。

### WI36 最终收口与独立 closure audit

- 按 WI3 矩阵逐条核验五条成功标准：每条至少 1 个 e2e-proved 证据（测试名 + 命令 + 环境等级），无 blocked 行（或 blocked 由 owner 签字）。
- 全量回归：`./mvnw clean install -T 1C` + gated 多 JVM + 格式兼容套件 + 性能 smoke。
- owner docs 全量回写：`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-user-guide.md`、`nop-stream-connectors.md`、`nop-stream-migration-guide.md`、`01-architecture-baseline.md`、`source-anchors.md`、`nop-stream/README.md`；`00-vision.md` 成功标准仅在 owner 裁定调整时同步。
- **终态预检**：`parseRoadmapMarkdown` 结果恒为 36 WI + 3 里程碑；状态集合合法；WI35 的 `check-nop-stream-residual-closure.mjs` exit 0（矩阵外 open 项 = 0）；`roadmapAllDone` 仅作辅助（不把里程碑计入分母）。
- **里程碑翻转时序（显式）**：完成 WI36 全部证据 → 将 WI36 置 `done` → 回写 M1/M2/M3 为 `done`（里程碑为派生值，需人工/脚本回写，parser 不会自动翻）→ 重跑终态预检并留证。M3 含 WI36 的自指按此顺序解决，不构成循环。
- 独立 closure audit（fresh session，不得自审）：核对 WI 状态、证据矩阵、门禁结果、文档一致性与未归属残留；REJECTED 项回填 WI 后重审。
- 验证：closure audit APPROVED；终态预检通过；日志记录执行事实与偏差。

## 遗留归属矩阵

> ID 前缀携带审计轮次，避免跨轮同名碰撞：`DIV-`（checkpoint 驱动面分叉）、`R5-`（r5-successors / R5 审计）、`R6-`（plan 2326 Deferred）、`2026-10-AR-`（2026-10-06 对抗审查）、`A-`（2026-09 性能审计 P0）。

| 遗留 ID | 问题摘要 | 认领 WI | 原登记处 / 状态 |
|---|---|---|---|
| DIV-01 | 分布式快照失败只 LOG，等 timeout abort | WI12 | r5-successors |
| DIV-02 | timeout abort 终局相反 | WI2/WI15 | r5-successors；WI2 裁定 |
| DIV-03 | 分布式 coordinator 恢复深度 / enumerator stash 缺失 | WI10 | r5-successors；manifest-first 半已由 plan 2426 落地，enumerator 半归 WI10 |
| DIV-04 | 分布式无 savepoint 恢复入口 | WI2/WI13 | r5-successors |
| DIV-05 | CANCEL 终态语义相反 | WI2/WI15 | r5-successors |
| DIV-06 | 分布式固定 MemoryStateBackend | WI11 | r5-successors |
| R5-ST-05 | 容器 ValueState 元素类型恢复丢失 | WI7（实现）/ WI21（回归证据） | r5-successors；唯一实现 owner=WI7，WI21 只做证据 |
| R5-ST-12 | RocksDB TTL 只在 checkpoint 时 sweep | WI22 | r5-successors；内存侧已有周期 sweep，仅 RocksDB 残余 |
| R5-ST-16 | retention 双平面界限不一致 | WI22 | 已由 checkpoint-design D2b 文档化；WI22 只做"是否统一"裁决 |
| R5-ST-18 | 增量快照共享 SST task-local 路径依赖 | WI21 | r5-successors |
| R5-CON-11 | JDBC 2PC 整 epoch 驻留内存无上限 | WI22 | r5-successors |
| R5-CEP-05 | copyForSubtask 共享 nfaFactory | WI24 | r5-successors |
| R5-CEP-06 | NFAState 比较器双标准 | WI24 | r5-successors |
| R5-TE-03/TE-10 | CEP qualifier / 超时×skip 测试深化 | WI24 | r5-successors |
| R5-RD-05/06/07/08/13/TE-07 | 上帝类/重复代码/考古注释/镜像测试 | WI33 | r5-successors；RD-13 计数以 WI33 可复现命令为准 |
| R5-AR-06 | xpl watermark onPeriodicEmit 恒空 | WI23 | r5-successors（注意与 2026-10-AR-06 不同） |
| R5-AR-08 | 重复边不校验、findEdge 取首条 | WI23 | r5-successors |
| R5-AR-09 | checkpoint interval=0 静默禁用 | WI23 | r5-successors |
| R5-RD-12 | import 分组系统性偏离 | 不立项 | 平台级风格问题（MA4.2-14 裁定），不属 nop-stream 独有；WI34 仅做局部一致性 |
| R6-ST-04/05/06 | 增量 checkpoint 族（未接线，watch-only residual） | WI27 | plan 2326 Deferred；触发规则=任何接线增量 checkpoint 的计划先修/同批修 |
| R6-ST-07 | keyType header raw Class.forName | 已关闭 | plan 2326 已修，不认领 |
| R6-ST-08 | RocksDB 容器族绕过 | WI27 | plan 2326 Deferred |
| R6-RD-01/02/03、PF-01/02、TE-04、REG-07 | 结构/性能/覆盖面残余 | WI27 | plan 2326 Deferred |
| 2026-10-AR-04 | 分布式 enumerator / 注册表串线 | WI10 | 2026-10 对抗审查 |
| 2026-10-AR-05 | source 挂死对 stall 检测不可见 | WI17 | 2026-10 对抗审查 |
| 2026-10-AR-06 | JDBC 2PC ledger 无 prune | WI25 | 2026-10 对抗审查（注意与 R5-AR-06 不同） |
| 2026-10-AR-07 | LocalFileCheckpointStorage 无 fsync | WI25 | 2026-10 对抗审查 |
| 2026-10-AR-08 | FAILED 分支锁外写 subtaskLiveness | WI25 | 2026-10 对抗审查 |
| 2026-10-AR-09 | snapshotState(null) NPE 契约 | WI25 | 2026-10 对抗审查 |
| A3/A4/A5/A6 | 恢复/终止路径 P0 行为缺陷 | WI26 | 2026-09 性能审计；原登记为 watch-only/successor，本 roadmap 认领 |
| CC-05 | TaskProgress DTO 缺 fencingEpoch | WI18 | r5-successors |
| CC-17 | 终态 RPC 同步发送 / taskExecutor 有界化残余复核 | WI25 | r5-successors；结构重构 WI10 已收口主面，本项兜底复核 |
| invariant-loop Follow-up Backlog | 约 130+ 条历史登记（当前 94 条 todo），状态未逐条重验 | WI35 | 独立 triage；开放项映射后回写原登记 |
| B7 | 用户状态类白名单无逃生口 | WI7 | 2026-09 性能审计 |
| B8 | RocksDB.open 失败不关 JNI 句柄 | WI6 | 2026-09 性能审计 |
| B9/B10/B11 | CEP timer 空实现 / SharedBuffer NPE / Lockable 语义 | WI21 | 2026-09 性能审计 |
| B12 | 用户显式禁用 checkpoint 时 buildJobGraph 静默强制开启 | WI15 | 2026-09 性能审计（与 checkpoint 分叉同批） |
| B13 | 错误码不一致 | WI21 | 2026-09 性能审计；唯一 owner = WI21 |
| B2 | restore 先 clear 再校验，失败不原子 | WI4 | 2026-09 性能审计；Failure Atomicity 实现项 |
| C3 | 每记录 Optional 分配 | WI28 | 2026-09 性能审计；plan 01 已裁 successor=no，WI28 只做处置记录（默认不实施） |
| C1/C2/C4、D1/D2/D3、E1/E2/E3、F3 | 已裁定 closed/watch-only/Deferred 的性能项 | WI29（只复核登记，不默认实施） | 360/2279 裁定书 + plan 01 |
| F-36 | DebeziumCdcSourceCompletion 永久 @Disabled | 已关闭 | productization roadmap 已以文档标注结案，不认领 |
| — | CEP×RocksDB 不可用 | WI9 | vision 成功标准 5 |
| — | 状态迁移注册表未接线 | WI8 | 本 roadmap 复核 |
| — | 能力模型仅 localRuntime | WI19 | 本 roadmap 复核 |
| — | XDSL 分布式 checkpoint 不可声明 | WI20 | 本 roadmap 复核 |
| — | S3 checkpoint storage 分布式不可用 | WI11 | 用户文档 Deferred |
| — | 2026-10-AR-06/07/08/09 + CC-17 的 checkpoint 耐久性/资源残余 | WI25 | 2026-10 对抗审查 + r5-successors |
| A10 | completedTasks 超限随机驱逐（非 LRU），刚完成任务结果可能被逐出 | WI22 | 2026-09 性能审计 P2 遗漏枚举；plan 01 标 Successor Required: no，但 live 复核仍在，本 roadmap 作为资源上界项认领 |
| A11 | 锁序未文档化：recoveryLock→CC monitor→RPC→commitExecutor→用户 JDBC 链式嵌套 | WI25 | 2026-09 性能审计 successor backlog；plan 01 标 Successor Required: yes |
| D4 | CheckpointCoordinator monitor 内持有 RPC fan-out（锁内外部 I/O） | WI15 | 2026-09 性能审计 successor backlog；plan 01 标 Successor Required: yes |
| G2/G3 | InputGate 拆分 / CepOperator 拆分（结构治理） | WI33 | 2026-09 性能审计 successor backlog |
| G10/G12 | serde/执行会话合并类结构治理 | WI33 | 2026-09 性能审计 successor backlog |
| A12-RocksDB serde | MapState 回退布局在 RocksDB 下 Object 值经 JSON 包装层无法回读（public 构造器路径可达） | WI6 | plan 01 successor note（与 restore/serde 专项同批） |

**明确不认领（owner 已接受或不在设计目标内）**：

- Kafka 2PC 的 commit-at-barrier 残余重复窗口：已在连接器成熟度中声明 EXPERIMENTAL，WI36 复核表述；除 owner 改判不立项。
- 平台级 import 分组风格（R5-RD-12）：由平台统一裁定，不在 nop-stream roadmap 重复。
- K8s/YARN/HPA、异步算子、broadcast state、非等值 join、Flink SQL 全栈等 vision Non-Goals：不认领。

## 性能项裁定台账（不重开）

| 项 | 已有裁定 | 本 roadmap 处置 |
|---|---|---|
| C1 InputGate 50ms 轮询 | 360 R2 不实施；2279 量化关闭（吞吐零收益，空闲延迟小项） | WI29 复核登记，不实施 |
| C2 BufferPool 批量许可 | 2279 量化关闭（畅通边 <2%，批量记账需契约重设计） | WI29 复核登记，不实施 |
| C3 Optional 分配 | plan 01 已裁：<2% 预估、Successor Required: no | WI28 只做处置记录（默认不实施）；owner 重开需新 JMH + 授权 |
| C4/F5 每记录时钟读 | 2279 watch-only（G52 liveness 语义耦合） | WI29 复核登记，不实施 |
| D1 MapState 回退 RMW | 2279 F3 裁定生产不可达（WindowOperatorBuilder 全路径非 null descriptor）；plan 01 A12 指出 public 构造器路径可达，属独立 serde 缺陷（已认领 WI6，不重开 D1） | WI29 复核登记，不实施 |
| D2 evictor 时间戳 RocksDB O(n²) | 2279 F2 Deferred（需列表格式设计，128µs/op） | WI29 复核登记；owner 重开需新设计 |
| D3 checksum canonical 格式 | 2279 F1 Deferred（读侧 78%，需格式 v2） | WI29 复核登记；与 v3 stateFormatVersion 不是同一版本面，不得混用 |
| E1 SharedBuffer.advanceTime | 2279 Q1/Q1b 已覆盖（-56.7%），终态 JFR 无热点 | WI29 复核登记，不实施 |
| E2 NFA 分配池化 | 360 watch-only（分散无单点） | WI29 复核登记，不实施 |
| E3 PROCEED 条件双重评估 | plan 01 已修复（-7.7%~-10.6%，LazyVerdictCache） | 已关闭；WI29 复核确认 |
| F3 SharedBuffer 缓存替换 guava→Caffeine | 360 Deferred（connectAccessOrder 17.1% 单点，需驱逐语义重设计） | WI29 复核登记；owner 重开需独立 WI |

## 成功标准验收矩阵（终态）

| Vision 成功标准 | 终态判定 | 主证据（WI） |
|---|---|---|
| 1. Java API 构建 source→window/aggregate→sink 并正确执行 | e2e-proved | WI3 基线 + 既有 core/runtime E2E + 回归门 |
| 2. 三入口生成同一 canonical StreamModel | e2e-proved | WI3 + 既有 flow/XDSL 场景 + WI20 声明面 |
| 3. 任意 durable epoch 恢复 + 端到端 exactly-once 可独立验证 | e2e-proved | WI4/WI5/WI8/WI13/WI16/WI31；"任意 epoch"按 latest durable + 显式 savepoint 两种入口证明 |
| 4. DISTRIBUTED + 多 TaskManager，三面分离 | e2e-proved | WI10/WI11/WI12/WI16/WI31 + 既有 gated 多 JVM |
| 5. CEP 独立可用 + 接入统一状态后端 | e2e-proved | WI4—WI9 + WI19 + WI24 |

## 执行约定

- **E1 先钉后拆**：WI4—WI9、WI13—WI16、WI20、WI28、WI33、WI34 属行为敏感变更；先固化现状（测试基线、格式 fixture、JMH 基线、差分 harness），再动实现。WI10—WI12、WI17/WI18、WI21—WI27 同样必须先有失败反例/定向测试钉，再改行为。
- **E2 格式兼容方向**：v3 引擎必须能读 v2 checkpoint（含 CEP `__java_bytes__` fixture）；v2 引擎读 v3 不承诺，必须 typed reject。任何格式变更须同时提供 legacy fixture 与跨版本测试。
- **E3 证据分级**：LOCAL / embedded distributed / 多 JVM / 真外部服务四级；未实际执行的项只能标 blocked 或 out-of-scope，不得标 e2e-proved。gated 测试只有实际执行后才是证据。
- **E4 销号联动**：每关闭一个遗留项，权威登记处（r5-successors / plan 2326 / 审计报告）必须更新，其他位置同步指针；不允许"一处 done 一处 todo"。
- **E5 owner 门**：WI2 未完成前不得实施 WI4—WI9、WI13、WI14、WI15、WI20 的格式/协议/范围变更；任何扩大 Non-Goal 的诉求回到 vision §六。
- **E6 计划纪律**：每个 WI 执行前按 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 拟制 plan 并走独立 review；执行中实时维护 plan 状态；收口需独立 closure audit，不得自审。
- **E7 文档时态**：设计文档描述目标终态；`docs-for-ai/` 只描述已实现能力。WI 未完成前不得把目标能力写进 owner doc 的"已实现"段。
- **E8 性能判据**：性能 WI 的完成标准是"实测收益 > 噪声阈值"或"按已有裁定登记为 closed/watch-only/Deferred 且有量化依据"；不得以主观优化代替基准。
- **E9 终态预算**：性能/Deferred 类 WI（WI28/WI29）完成态可以是"裁定不实施 + 证据入库"；正确性/交付类 WI 必须有可执行验证。
