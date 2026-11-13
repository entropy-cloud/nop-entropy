# 单元内跨维度合并裁定 — nop-stream-state-connectors（终版）

- **裁定人**: 独立合并裁定子代理（与 8 个维度初审/一二轮复核及 C10 复核均不同任务实例），2026-10-14，plan 2462 第 7.1 步
- **输入（完整读取各维度「维度复核结论」+「最终保留项」终版节，必要时回读条目正文）**: 本目录 C1–C8 八个维度文件（含二轮复核权威节）、C6-round2.md、C7-round2.md、C8-round2.md、C10-test-protection.md；live code 抽验（`RocksDBMetricsRecorder.java` 等，见文末附录）
- **版本说明**: 本文件**整体取代** 2026-10-14 09:33 的旧版 merge-adjudication.md——旧版定稿于 C3–C8 二轮复核与 C10 复核落盘之前，未覆盖 C6-013/014、C7-012/013、C8-011/012/013 及 C10 终版 16 条登记，统计基数（77）与终版基数（85）不一致，不得引用
- **裁定口径**（根因 + 失效层三态判定）:
  - **同一缺陷（→并入）**: 同一根因点 + 同一失效层（或同一修复同时关闭全部失效面）→ 取一为主条目（证据链最完整、判级最准确者），成员 ID 消号、失效面并入主条目清单
  - **同族相邻（→缺陷族）**: 同一根因区域/同一状态机但根因点或失效层不同、修复不互相覆盖 → 合并为单一缺陷族/单一修复工单，修复验证须同时覆盖全部成员失效面
  - **不同（→独立保留）**: 根因与失效层均无重合 → 各自立案（可 cross-reference）；疑似重合判「不同」时必须给出失效层差异说明
  - 族最终严重度取族内最高；主条目选取原则：①维度复核已终裁严重度者 ②C10 测试保护锚定者 ③失效面枚举最完整者 ④先立案编号

---

## 1. 裁定方法与输入清单

### 1.1 各维度保留条目 × 严重度（终版权威）

| 维度 | 保留 | P0 | P1 | P2 | P3 | 备注 |
|---|---|---|---|---|---|---|
| C1 共享可变状态 | 6 | 0 | 4（001/002/005/006） | 1（003） | 1（004） | 二轮复核 6 保留 0 降级 |
| C2 锁与同步 | 11 | 0 | 4（001/002/003/010） | 3（004↓/005/011） | 4（006/007/008/009） | 二轮：保留 10 + 降级 1（C2-004 P1→P2） |
| C3 异步链路 | 14 | 0 | 6（001/002/003/004/009/014） | 8（005~008/010~013） | 0 | 一轮复核 14 保留 |
| C4 线程池治理 | 6 | 0 | 3（001/004/005） | 3（002/003/006） | 0 | 二轮复核 6 保留（C4-005 MBean 面校准为有界停顿） |
| C5 内存有界性 | 10 | 0 | 1（002） | 8（001/004/005/006/007/008/009/010） | 1（003↓） | 二轮：保留 9 + 维持降级 1（C5-003 P2→P3） |
| C6 资源生命周期 | 12 | 1（001） | 1（004） | 6（005/006/007/008/010/011） | 4（009/012/013/014） | 二轮：保留 12 + 驳回 2（C6-002/003 泄漏主链证伪，维持驳回） |
| C7 长时运行 | 13 | 1（002，随 C6-001） | 1（001） | 7（003/004/005/006/010/012/013↑） | 4（007/008/009/011） | 二轮：13 保留；C7-013 P3→P2 归并对齐上修 |
| C8 背压流控 | 13 | 0 | 2（001/006） | 11（其余） | 0 | 二轮：13 保留（含新增 C8-011/012/013） |
| **合计** | **85** | **2 条目/1 缺陷** | **22 条目** | **47 条目** | **14 条目** | C2-003/C2-010 为受害者侧跨单元登记 |

另有：C6-002/C6-003 已驳回（不入基数，残余健壮性面由 C3-012 单独承接）；C10 缺保护登记 16 条（P2，非缺陷条目，见 §4.4）。

### 1.2 维度复核已登记的归并提议（裁定起点）

kafka producer 四向、JDBC 台账四向、registry HTTP 五向、gauge 竞态三向 + 无 tag 注册四向、Avro HttpClient（C7-013→C4-002）、Debezium 停止契约三面、globalWorker 双向、manifest 双锁双向、桥 reader close（C6-002/003 驳回后 C3-012 唯一存活）、2PC epoch 缓冲、RocksDB 快照物化、file manifest 增长、CEP reconciledTimerKeys、meter 注册无摘除——本裁定对全部提议独立复核，可推翻（推翻处见 §3）。

---

## 2. 合并裁定表

### 2.1 合并组（缺陷族 MA-01 ~ MA-20）

| 族 ID | 判定 | 成员条目 | 主条目 | 终裁严重度 | 根因（一句） | 修复验证须覆盖的失效面 |
|---|---|---|---|---|---|---|
| **MA-01** | 同一缺陷→并入 | C6-001（主）、C3-001、C4-001、C7-002 | C6-001 | **P0**（C6 复核四点独立终裁，二轮维持） | KafkaTwoPhaseCommitSink 首次 invoke 创建 producer 后全类无 close、不实现 AutoCloseable，基类 TwoPhaseCommitSinkFunction 与 StreamSinkOperator.close:175 双钩子恒不触达 | ①正常 DRAIN/取消/失败全部终止路径每作业×每 subtask 确定性泄漏 1 producer（daemon sender 线程+broker 连接+≤32MB 缓冲池）②probe 路径 TWR 干净（非第二泄漏点）③修复后 close 恰一次且幂等、recover/abort 不误关（C10-001 先红后绿） |
| **MA-02** | 同一缺陷→并入 | C1-002（主）、C2-001、C6-004 | C1-002 | **P1**（三维一致） | gauge 闭包 `ref -> readProperty(ref.get(), property)` 在 synchronized 之外求值 + close 仅 dbRef.set(null) 无摘除 | ①scrape 线程持 stale 引用读已释放 native 句柄（SIGSEGV/IllegalStateException）②recorder.close→db.close 顺序窗 ③null 守卫与锁均不设防 ④白盒断言取参点在锁内 + scrape×close 压窗测试（C10-002） |
| **MA-03** | 同族相邻→缺陷族 | C1-001、C1-005、C1-006（三条各自保留 P1） | C1-001 | **P1** | 同根因：`CepOperator.copyForSubtask:329-339` 对 nfaFactory+udf 引用透传不做序列化 round-trip | ①C1-001=NFA 共享条件图 keyedStateStore last-writer-wins（静默读错键控状态）②C1-005=SideCollector ctx 槽位跨 subtask 错路由（错 collector+错 timestamp）③C1-006=udf open×N 泄漏+首个 close 全关。修复单一（copyForSubtask 一并 round-trip）+ 测试反转 TestCepOperatorSubtaskCopy:43-44 assertSame（C10-003/004；C10-003 为条件会红——仅合并修复时经 ：43 反转） |
| **MA-04** | 同一缺陷→并入 | C2-002（主）、C3-003、C4-003、C6-008、C8-004、C7-006①（部分并入） | C2-002 | **P1**（C2/C3 双维 P1；C4/C6/C8 的 P2 仅见单一挂起面） | Avro registry HTTP 仅 connectTimeout 无请求级超时 × computeIfAbsent bin 锁内发起 HTTP × copyForSubtask 引用透传致跨 subtask 共享同一 client/CHM | ①read 阶段无界挂起 ②bin 锁毒化→同 subject 全 subtask 阻塞 ③无异常无输出不可见挂死 ④registry 半死即全管线冻结 ⑤锁内 I/O 互阻放大（C8-004 注记）。修复=请求超时+锁外加载；测试=挂死注入 typed 失败+锁限时释放（C10-008） |
| **MA-05** | 同族相邻→缺陷族 | C3-002（主，P1）、C5-006、C7-001、C8-007、C5-007（P2）、C7-007（拆分，manifest 面归本族） | C3-002 | **P1**（终裁：C7 判级口径「长时运行必然触发的无界增长」字面命中且失效面最完整，压倒 C5/C8 的 P2） | 同根因区域：2PC 持久化登记（JDBC 台账表 + file manifest）无任何保留/裁剪策略 | ①JDBC：唯一写路径 INSERT，每成功 checkpoint 每 subtask 确定性一行（30s≈2880 行/天/subtask），无 DELETE/TTL/prune、维护工具不触达、手工删行有踩坏幂等守卫风险 ②File：manifest 每 epoch 追加一键+每 commit 全量 load/rewrite/fsync（O(全部 epoch) 放大）。修复=统一保留策略族一单工单，验证须同盖台账清理与 manifest 裁剪两面（C10-007） |
| **MA-06** | 同一缺陷→并入 | C4-004（主）、C3-009 | C4-004 | **P1** | DebeziumEngineWrapper.start:159 将无限阻塞 engine.run() 提交平台共享 globalWorker 池（core=4/max=30/queue=10000） | ①每 CDC subtask 钉死 1 条共享线程至 job 停止（任务隔离失效）②池耗尽→新引擎 10000 深队列永不执行→静默死流 ③拖饿平台共租方。hook 断言引擎线程不落 globalWorker + 占池 fail-fast（C10-010） |
| **MA-07** | 同族相邻→缺陷族 | C3-014（主，P1）、C4-005（并入面）、C4-006（族内面，P2） | C3-014 | **P1** | 同一状态机（startRequested/stopped/running）的停止契约无终结保证：stop 为 fire-and-forget、engine.close() 在 STARTING_TASKS 抛 ISE 被双层吞掉、启动 lambda 无 stopped 守卫 | ①C3-014=STARTING_TASKS 窗口 stop 被拒→永久幽灵引擎（活 binlog 连接+线程组，javap 3.7.0.Final 证实）②C4-005=快速重启同名交叠：双 binlog 连接+offset REGISTRY 双写+MBean 有界停顿（~60s，二轮校准）③C4-006=饱和池排队×快速取消→出队 lambda 无条件 engine.run()（3.7 下 CAS 失败即抛，版本耦合的理论风险）。修复=专用单线程执行器+终结 latch+启动守卫一并实施（C4 二轮已证一改盖三面）；快速重启循环断言有界终态（C10-011，须同盖族内三面） |
| **MA-08** | 同一缺陷→并入 | C5-002（主）、C8-005 | C5-002 | **P1** | 默认全量 JSON 快照逐值反序列化整库物化进堆（增量 opt-in 缺省 false，backend:119 缺省分派） | ①任务线程同步 O(状态库) 堆内物化，大状态作业 checkpoint 即 OOM 点 ②owner doc 无数值包络 ③时长维由 MA-18 承接（不并入本族）。参数化键数+堆驻留上界断言（C10-012） |
| **MA-09** | 同一缺陷→并入 | C2-011（主）、C6-010、C7-004、C3-013 | C2-011 | **P2** | gauge 以固定名+Tags.empty() 注册进程级 static registry，close 无 registry.remove（micrometer first-wins） | ①多 subtask 注册静默去重（1/N 可见）②首个 backend close 后全进程 gauge 永久 NaN（确定性零并发）③与 MA-02 为同代码位竞态兄弟面，机制不同分立两条，修复同批（tags+remove+锁内取参一改同点位落地） |
| **MA-10** | 同族相邻→缺陷族 | C7-006（主，仅②面；①面已并入 MA-04）、C3-007 | C7-006 | **P2** | 同一代码点 `readerSchemaFor` 兜底 catch(StreamAvroException)→回落 writer schema、失败态不入缓存 | ①C3-007=兜底语义面：registry 不可达静默关闭 schema 演进（违反 owner doc「不静默降级」声明）②C7-006②=无负缓存无退避：每记录一次失败调用、吞吐塌陷零 error 信号。修复同点位分两步（typed 失败传播+负缓存退避），验证同盖两面 |
| **MA-11** | 同一缺陷→并入 | C4-002（主）、C7-013 | C4-002 | **P2**（C7-013 随归并对齐上修） | AvroSchemaRegistryClient 构造即建 JDK HttpClient（Java 25 下 AutoCloseable）且全类无 close | ①每份 wire-format 装配泄漏 1 条 daemon selector 线程+连接池（线程强引用 GC 不可达）②job 装配-停止循环确定性累积。装配-停止循环泄漏断言 |
| **MA-12** | 同族相邻→缺陷族 | C2-004（主，P2↓）、C1-003（P2） | C2-004 | **P2** | 同根因区域：Message/Debezium reader 内嵌线程与任务线程多入口无同步 | ①C2-004=launchRunner/launchEngine check-then-act 双启动（当前 LOCAL 接线单线程串行不可达=理论竞态，接线模型迁移即成活，附回升条件）②C1-003=addSplits 交付面字段无同步无 volatile（后果自愈）。单守卫（CAS/synchronized）+交付字段 volatile 一单工单同盖两面；若 7.1 发现未覆盖接线或 DISTRIBUTED 落地，MA-12 回升 P1 |
| **MA-13** | 同族相邻→缺陷族 | C5-005（主，并 C8-003）、C5-010 | C5-005 | **P2** | 同根因区域：2PC sink 侧状态无上界旋钮 | ①C5-005(+C8-003)=epoch 内 currentBuffer 随体量无界（Jdbc+File 双面；maxBatchSize 仅执行侧分段；无 spill/深度指标）②C5-010=跨 epoch pendingCommits 堆叠无守卫+每 barrier 全量重序列化进 checkpoint+restore commit 失败 retain。上限旋钮族一单工单同盖双容器 |
| **MA-14** | 同一缺陷→并入 | C5-004（主）、C5-008、C5-009 | C5-004 | **P2** | 迁移 helper 同型的全 column family keys+values 双列表堆内物化（Map/List/Value 三状态类共享模式，一修盖 4 状态类） | ①schema 指纹失配+已注册迁移函数触发 ②restore 期 OOM→作业无法拉起（冷路径不豁免）③失效面按 map/list/value 三实例路径验收（C5-008 为最重物化形态） |
| **MA-15** | 同一缺陷→并入 | C2-005（主）、C3-008 | C2-005 | **P2** | manifest 跨 JVM 两级锁（jvmLock.lock 非 interruptible + channel.lock 阻塞 OS 锁）无超时获取 | 对端 JVM 活而挂死时 exactly-once 提交主路径无限阻塞；修复=lockInterruptibly/tryLock 超时+OS 锁超时语义，验证挂死注入下提交可 typed 失败 |
| **MA-16** | 同一缺陷→并入 | C5-001（主）、C7-005 | C5-001 | **P2** | reconciledTimerKeys 仅声明+contains+add 无任何移除点（同文件 registeredEventTimeTimersByKey 有完整清理对照） | 每 key 单调增长永不清理（key 状态全清后强引用仍驻留）；修复=清理点同步 remove，验证 key 全清后集合收敛 |
| **MA-17** | 同一缺陷→并入 | C7-012（主，P2）、C2-008（P3） | C7-012 | **P2**（终裁见 §3.1） | RocksDBMetricsRecorder javadoc（:26-28 类级/:87-89 方法级）与 owner doc nop-stream.md:90 声明「one-shot 告警」，readProperty catch（:91-101）无 once-flag 每 scrape 必 WARN | ①15s scrape×1 失败属性≈240 条/时、5,760 条/天无限期（构建缺属性为持久性特征）②文档-代码分歧使运维告警规则失配 ③与 MA-09 组合「先刷屏后失明」。一次性守卫（per-property 去重）+文档对齐；同点位与 MA-02/MA-09 同批实施 |
| **MA-18** | 同族相邻→缺陷族 | C8-010（主）、C8-013 | C8-010 | **P2** | 同根因区域：barrier 内同步快照时长 ∝ 状态总体积与固定预算（60s/600s/3 次）无耦合守卫无旋钮 | ①C8-010=默认全量路径+file epoch 落盘时长为规模函数、预算为常量（runtime fallback 1s/10s 裕度更小，移交 runtime 知悉）②C8-013=增量逃生门自身对全部 SST 无条件 sha256Hex 重哈希、无跳过逻辑/旋钮（时长 ∝ 总体积非增量）。修复=预算耦合校验（对照 validateUnalignedConfig 先例）+哈希增量化旋钮，一单工单同盖双面；越界后「超时→3 次失败→FAIL→重启→重演」循环为共同失效层 |
| **MA-19** | 同一缺陷→并入 | C2-009（主）、C1-004、C6-009、C7-007（拆分，锁面归本族） | C2-009 | **P3** | MANIFEST_JVM_LOCKS 静态 CHM 锁注册表仅 computeIfAbsent 写点、无 remove/淘汰 | 长驻多作业 JVM 按历史输出目录数缓慢累积（互斥正确、键空间有界，信息性保留）；C6-009 有 2026-09-26 审计历史同锚点登记 |
| **MA-20** | 同一缺陷→并入 | C7-009（主）、C6-012 | C7-009 | **P3** | CepOperator 迟到丢弃 counter 的 identityHashCode 兜底标签+open 注册+close 无注销（R5-CEP-03 修复打 tags 后残留面） | meter 随实例累积（限无位置实例反复创建形态）；生产稳定 TaskLocation 标签路径语义正确 |

### 2.2 独立立案（判定：不同，26 条）

| ID | 严重度 | 标题 | 独立性说明（与疑似重合组的失效层差异） |
|---|---|---|---|
| C3-004 | **P1** | Debezium 枚举器恢复路径双登记→同表双引擎重复消费 | 与 MA-07 同模块不同根因（恢复分配逻辑 vs 停止契约）；与 C2-003（跨单元锁纪律）不同失效层（双消费 vs CME）。cross-ref WI1.1-C1-001 |
| C8-001 | **P1** | Debezium reader checkpoint offset 领先已发射→恢复静默丢 ≤256 条/检查点 | 与 MA-07 同模块不同失效层（数据正确性 vs 资源生命周期）；队列偏斜机制独立成立 |
| C8-006 | **P1** | Kafka 2PC epoch 事务存活期与 checkpoint 间隔零裕度（60s==60s） | 与 MA-01 同文件不同根因（事务超时配置缺失 vs close 缺失）、不同失效层（broker 触界 FAIL 循环 vs 线程/连接泄漏）；修复同批评估 |
| C3-005 | P2 | KafkaSourceFunction.cancel 无 wakeup | 取消响应性面，与他组无根因重合 |
| C3-006 | P2 | Debezium 失败监听静态表按键覆盖→故障信号串号 | 与 MA-07 仅共享 NopStreamOffsetBackingStore 相邻面，根因（同名覆盖无守卫）与失效层（信号串号）不同 |
| C3-010 | P2 | BatchLoaderSourceFunction.cancel 无中断响应点 | 与 C3-005 同形态异载体（batch loader），分立 |
| C3-011 | P2 | Debezium run() 重入守卫静默 return→假 EOS | 响应语义缺陷（C10-015 锚定）；与 MA-07 不同根因 |
| C3-012 | P2 | 桥 reader close() join 超时分支先抛后 clear 且无 interrupt 兜底 | C6-002/003 驳回后残面唯一存续锚点（主触发=Local 总线发送方持续泵送）；独立保留 |
| C6-005 | P2 | RocksDBKeyedStateBackend openDB 失败路径泄漏全部 native 句柄 | 构造失败路径，与他组无重合（R6 ST-11 P3 从重） |
| C6-006 | P2 | RocksDBIncrementalRestore.restoreRangeInto 先关 DB 后关 handles | 与 C7-003 同方法（restoreRangeInto/reconstructRocksdbDir 异常边界）两独立缺陷：关闭顺序违约（native UAF 风险）vs finally 边界前抛残留，修复不互盖 |
| C6-007 | P2 | File 2PC epoch 临时文件 checkpoint 未 durable 即失败时永久孤儿 | 与 C6-014（删除失败零观测）、MA-05（保留策略）根因/失效层均不同（崩溃窗口登记丢失） |
| C6-011 | P2 | Files.walk 返回 Stream 未 TWR 关闭→异常路径目录 fd 泄漏 | 载体/根因/失效层均无重合（FileSplitEnumerator:147 + FileSource:407） |
| C7-003 | P2 | RocksDB 增量恢复临时目录跨 id 残留无清扫 | 与 C6-007 同为「临时盘上残留」模式但模块/机制/清扫责任方不同，分立（cross-ref 同模式） |
| C7-010 | P2 | RocksDB TTL 清理仅挂 snapshotState（Memory 后端挂 setCurrentKey，doc-code 分歧） | 与 C2-010（跨单元 REST 线程安全）不同缺陷：本条是清理触发点缺失非并发竞态；ops 端点仅为额外手动触发面 |
| C8-002 | P2 | CEP 匹配缓冲无过载行为/深度观测/包络声明（升 P1 已驳回维持） | 与 MA-16 同 operator 不同根因（缓冲无包络 vs 集合无清理）；与 MA-13 不同容器不同触发 |
| C8-008 | P2 | connector source/sink/CEP 边界积压深度零遥测（证伪修正后限缩范围） | 观测缺失面，RocksDB 面已由 recorder gauge 审及故限缩 |
| C8-009 | P2 | JDBC 2PC 提交无语句级超时且位于 checkpointTimeout 盲区 | 与 MA-04 判「不同」：不同模块（JDBC vs HTTP）、不同机制（语句级/socket 超时 vs request timeout）、独有「屏障外盲区」加重面；仅修复排批同批（外部依赖超时契约） |
| C8-011 | P2 | message sink 每记录热路径同步 send 无 sendTimeout opt-out（第三无限期等待点） | 与 MA-04/C8-009 判「不同」：独有「旋钮存在但被 opt-out」形态+每记录热路径；判级维持 P2（触发依赖慢/挂 message 后端=外部故障，与 C8-009 同档；热路径形态记录为修复优先级加权，不升 P1） |
| C8-012 | P2 | push→pull 桥 256 槽位仅条数界无字节界 | 与 MA-13 判「不同」：触发维度不同（单条 payload 字节 vs epoch 时间×速率累积）、容器不同（桥队列 vs sink epoch 缓冲）、修复不同（字节界守卫 vs 上限旋钮） |
| C2-006 | P3 | File sink saveState 持 currentBuffer 监视器执行整 epoch 写盘+fsync | 锁scope I/O 优化项（JDBC 兄弟正例在仓），正确性无损 |
| C2-007 | P3 | FileSourceReader 持 this 监视器执行文件 I/O（已声明边界残留登记） | AR-15-② 刻意设计残留，非缺陷主张；二轮修正触发面（mailbox 串行化掩蔽） |
| C5-003 | P3 | RocksDBMapState entries()/keys()/values() 全量物化（降级维持） | 调用方普查证伪热路径前提：生产唯一调用面=OverWindowOperator 一次性门控重建 |
| C6-013 | P3 | CepOperator close 链中间异常无 suppressed 收集（super.close 先抛中断定时器取消与 NFA 条件关闭） | 与 MA-03 不同根因（close 链异常传播 vs 引用共享）、不同失效层（清理完备性 vs 共享态变异） |
| C6-014 | P3 | deleteIfExistsQuiet 空 catch 静默吞 IOException（abort 删除失败零观测） | 与 C6-007 不同层：本条是失败可观测性缺失，非孤儿窗口本体 |
| C7-008 | P3 | Avro schemaByIdCache 无上界无淘汰（fetch 失败不入缓存，投毒驻留被证伪） | 增长=registry 真实 id 分配速率；与 MA-04/MA-10 同类不同缺陷（驻留 vs 挂起/回落） |
| C7-011 | P3 | kafka-2pc checkConnection 以 initTransactions（fencing 原语）作连通性探测——owner doc 红线表缺行 | 当前三处生产部署点均 TaskLocation 限定 id 无实际触发面，文档契约缺口 |

### 2.3 跨单元关联（2 条，不重复立项、不计入本单元统计）

| ID | 严重度 | 挂靠 | 说明 |
|---|---|---|---|
| C2-003 | P1 | WI1.1-C1-001（cra-index #30，open） | DebeziumTableSplitEnumerator 裸 LinkedHashMap 并发读写受害者侧落账；core 侧修复（addReader/requestSplits 收敛入锁）落地后自动闭合 |
| C2-010 | P1 | WI1.1-C3-009 | keyed-state REST 在 HTTP 4 线程池直调 backend.snapshotState 无 mailbox 编组+TtlContext 裸 HashMap 三点竞态受害者侧落账（P1 重于派发侧 P2：native 面/TTL 语义损坏）；修复挂 WI1.1 批次+受害侧 CHM 化加固 |

### 2.4 驳回与拆分消号

- **C6-002/C6-003 维持驳回**（两轮复核证伪「put 阻塞→join 超时→finally 不可达→永久泄漏」主链）：不入基数；残余健壮性面（join 超时抛错先于 clear、无 interrupt 桥）由 C3-012 单独承接。
- **C7-007 拆分消号**：其两个构成面分别归并 MA-05（manifest 面→C5-007）与 MA-19（锁面→C2-009），ID 本身消号（C5/C7 复核共同裁定，C2 复核「整体归并」的较粗提议不采纳，见 §3.4）。
- **C7-006 拆分保留**：①超时面并入 MA-04；②回落/负缓存面作为 MA-10 主条目保留。

---

## 3. 独立裁定与复核提议不一致处

1. **C2-008 = C7-012 同一缺陷（新裁定，复核提议缺登）**: C7-012 二轮复核宣称「全审计目录无重复立项」，但该断言漏查 C2-008——两条为**同一文件（RocksDBMetricsRecorder.java）同一根因（javadoc/owner doc 声明 one-shot 告警 vs readProperty catch 无 once-flag 每 scrape 必 WARN）同一失效层（采集路径日志放大+文档-代码分歧）**。live code 复核证实：类 javadoc :26-28「one-shot warn log」、方法 javadoc :87-89「one-shot-style warn」、catch 块 :97-99 无条件 LOG.warn，全类无抑制字段。C2-008 自身「误报排除」亦自认「属 C2/C7 交叉的可观测性代价」——盲审边界致双向漏并。**主条目取 C7-012**（证据链最完整：owner doc 声明点+全模块日志盘点+算术修正）。**严重度终裁 P2**（族内最高规则）：C7-012 以长时运行口径判 P2（失败源为持久性构建特征、放大无衰减）；C2 一轮+二轮曾两度以「有 scrape 频率上界」判 P3，该口径只约束瞬时频率不约束无限时长累计，且 scrape 间隔为公开旋钮可缩小——终裁取 P2 并在此记录 P3 异议。
2. **MA-18（C8-010 + C8-013 族）为新分组**: C8-013 条目自认「非与 C8-005/C8-010 重复」（失效面不同：增量路径重哈希 vs 全量路径预算），同时自认「C8-010 确立的缺陷类别（时长 ∝ 数据体积 × 固定预算）的 F3 兄弟站点」。按口径裁为**同族相邻**（同一根因区域、根因点不同、修复互补不互盖：预算耦合守卫 vs 哈希增量化旋钮）→ 单一缺陷族/单一修复工单。既不采纳「各自完全独立」（失效层同为 checkpoint 超时-失败-重启循环，会漏掉同批验证），也不并入同一缺陷（根因点确不同）。
3. **MA-12（C2-004 + C1-003）判「同族相邻」而非 C2 复核提议的「归并」**: C2 复核称 C1-003「覆盖同根 facet，7.1 归并」。独立比对：C2-004 根因点=launch 检查与生效点置后无守卫（修复=CAS/synchronized 守卫）；C1-003 根因点=addSplits 交付面字段无同步无 volatile（修复=字段同步化）。两者修复不互相覆盖、失效层不同（双启动双引擎 vs 可见性竞态后果自愈）→ 不满足「同一缺陷」的同一修复判据，按族处理（单一工单同批实施，效果与 C2 提议的修复并轨一致，仅计数口径不同）。
4. **C7-007 拆分消号（采 C5/C7 复核，不采 C2 复核）**: C2 复核提议 C7-007 与 C1-004/C6-009「同一容器整体归并」；C5/C7 复核证实其两个构成面分属不同缺陷（manifest 增长=C5-007 P2、锁表增长=C6-009/C2-009 P3），整体归并会把 P2 面错并入 P3 条目。裁定按构成面拆分（§2.4）。
5. **C4-002 = C7-013 并入（确认 C7 二轮提议）**: C7-013（round2 立项）与 C4-002 为同一缺陷（同类、同 HttpClient eager 构造无 close、同 selector 线程泄漏失效层），系 round2 盲审边界致重复立项；随归并对齐上修 P3→P2。此为对 C7 复核提议的确认，非推翻。
6. **严重度终裁记录**（无复核冲突，按口径裁定）: ①MA-05 族终裁 **P1**（C5/C8 维持 P2、C3/C7 维持 P1，两维均声明「终裁归 7.1」——本裁定取 P1：C7 判级口径字面命中且 C7-001 失效面枚举最完整）；②MA-04 族终裁 **P1**（C2/C3 双维 P1、锁中毒×跨 subtask 共享为最重失效层）；③C8-002 升 P1 **驳回维持 P2**（watermark 停滞为部署前提非负载必然）；④C5-003 降级 P3 **维持**（调用方普查两轮独立复现）；⑤C8-011 判级取向（C8 复核移交问题）**明示维持 P2**：热路径形态作为修复优先级加权，不构成升 P1 依据（触发依赖外部后端慢/挂，与 C8-009 同档）。
7. **首次进入裁定的条目**（C6-013/C6-014/C7-012/C8-011/C8-012/C8-013，中断会话遗留+本轮新增）: 除 §3.1/§3.2 所列外，全部独立立案无跨维重合（逐条比对 C1–C8 全部保留项后确认）。

---

## 4. 合并后统计

### 4.1 总量与自洽核对

- **原始条目数**: **85**（C1×6、C2×11、C3×14、C4×6、C5×10、C6×12、C7×13、C8×13；C6-002/003 驳回不入基数；C10 的 16 条为缺保护登记非缺陷条目）
- **合并后裁定项**: **46 = 20 缺陷族（MA-01~MA-20）+ 26 独立立案**；另跨单元关联 2 条（C2-003/C2-010）单列不计入
- **自洽核对**: 20 族吸纳成员 57 条目（其中 C7-006 拆分计入 MA-04①+MA-10②、C7-007 拆分消号计入 MA-05+MA-19，两条各计一次）+ 26 独立 + 2 跨单元 = **85，无遗漏、无重复计数** ✓
- **并入消号数**: 族内消号 37 = 57 − 20（每族只保留主条目一个编号，其余 37 个 ID 消号，含 C7-007 拆分消号）；另跨单元 2 条（C2-003/C2-010）不占本单元族编号。37 + 26 独立 + 20 主条目 + 2 跨单元 = 85 ✓

### 4.2 逐严重度缺陷族/立案清单

**P0 × 1**

| ID | 主条目 | 标题 | 涉及模块 |
|---|---|---|---|
| MA-01 | C6-001 | kafka-2pc producer 全终止路径不关闭（确定性泄漏） | connector-kafka（+core 钩子契约面） |

**P1 × 10**

| ID | 主条目 | 标题 | 涉及模块 |
|---|---|---|---|
| MA-02 | C1-002 | RocksDB gauge 锁外取 dbRef→native UAF（SIGSEGV） | rocksdb |
| MA-03 | C1-001 | CEP copyForSubtask 引用透传共享（NFA 条件图/ctx 槽位/udf 生命周期三面） | cep |
| MA-04 | C2-002 | Avro registry 无请求超时 × computeIfAbsent 锁内 HTTP（挂死+锁毒化） | format-avro |
| MA-05 | C3-002 | 2PC 持久化登记无保留策略（JDBC 台账只增不删 P1 面 + file manifest O(E) 放大 P2 面） | connector-jdbc、connector(file) |
| MA-06 | C4-004 | Debezium 引擎无限阻塞任务常驻平台共享 globalWorker 池 | message-debezium（锚点 connector-debezium） |
| MA-07 | C3-014 | Debezium 停止/启动契约无终结保证（幽灵引擎/重启交叠/排队窗口三面） | message-debezium |
| MA-08 | C5-002 | RocksDB 默认全量 JSON checkpoint 整库物化进堆 | rocksdb |
| C3-004 | C3-004 | Debezium 枚举器恢复双登记→同表双引擎重复消费 | connector-debezium |
| C8-001 | C8-001 | Debezium reader checkpoint offset 领先已发射→恢复静默丢数 | connector-debezium |
| C8-006 | C8-006 | Kafka 2PC 事务存活期与 checkpoint 间隔零裕度→触界失败循环 | connector-kafka |

**P2 × 26**

| ID | 主条目 | 标题 | 涉及模块 |
|---|---|---|---|
| MA-09 | C2-011 | RocksDB gauge 空 tags 进程级注册无摘除（1/N 可见+永久 NaN） | rocksdb |
| MA-10 | C7-006 | Avro readerSchemaFor 静默回落+无负缓存退避 | format-avro |
| MA-11 | C4-002 | Avro HttpClient 无关闭路径（selector 线程泄漏） | format-avro |
| MA-12 | C2-004 | reader 并发入口无同步（launch 守卫+交付面字段） | connector、connector-debezium、connector-batch |
| MA-13 | C5-005 | 2PC sink 状态无上界旋钮（epoch 缓冲+pendingCommits 堆叠） | connector-jdbc、connector(file) |
| MA-14 | C5-004 | RocksDB 迁移全 CF 双列表物化（三实例一修盖 4 状态类） | rocksdb |
| MA-15 | C2-005 | manifest 跨 JVM 双级锁无超时不可中断 | connector(file) |
| MA-16 | C5-001 | CEP reconciledTimerKeys 每 key 单调增长永不清理 | cep |
| MA-17 | C7-012 | RocksDB gauge WARN 无一次性守卫（「告警一次」声明未实现） | rocksdb |
| MA-18 | C8-010 | checkpoint 时长与固定预算无耦合守卫（全量路径+增量重哈希两面） | rocksdb（预算面 core） |
| C3-005 | C3-005 | Kafka cancel 无 wakeup（取消挂起至 max.block.ms） | connector-kafka |
| C3-006 | C3-006 | Debezium 失败监听静态表按键覆盖（信号串号） | message-debezium/connector-debezium |
| C3-010 | C3-010 | BatchLoaderSourceFunction.cancel 无中断响应点 | connector-batch |
| C3-011 | C3-011 | Debezium run() 重入守卫静默 return（假 EOS） | connector-debezium |
| C3-012 | C3-012 | 桥 reader close 超时分支先抛后 clear、无 interrupt 兜底 | connector |
| C6-005 | C6-005 | RocksDB openDB 失败路径泄漏全部 native 句柄 | rocksdb |
| C6-006 | C6-006 | restoreRangeInto 先关 DB 后关 handles（关闭顺序违约） | rocksdb |
| C6-007 | C6-007 | File 2PC epoch 临时文件永久孤儿（无启动清扫） | connector(file) |
| C6-011 | C6-011 | Files.walk 未关闭→异常路径目录 fd 泄漏 | connector(file) |
| C7-003 | C7-003 | RocksDB 增量恢复临时目录跨 id 残留无清扫 | rocksdb |
| C7-010 | C7-010 | RocksDB TTL 清理仅挂 snapshotState（与 Memory 后端 doc-code 分歧） | rocksdb |
| C8-002 | C8-002 | CEP 匹配缓冲无过载行为/深度观测/包络声明 | cep |
| C8-008 | C8-008 | connector 边界积压深度零遥测（限缩后范围） | connector 六模块+cep |
| C8-009 | C8-009 | JDBC 2PC 提交无语句级超时且位于 checkpointTimeout 盲区 | connector-jdbc |
| C8-011 | C8-011 | message sink 每记录同步 send 无 sendTimeout opt-out | connector |
| C8-012 | C8-012 | push→pull 桥仅条数界无字节界 | connector、connector-debezium |

**P3 × 9**

| ID | 主条目 | 标题 | 涉及模块 |
|---|---|---|---|
| MA-19 | C2-009 | MANIFEST_JVM_LOCKS 静态锁表只增不删 | connector(file) |
| MA-20 | C7-009 | CEP 迟到丢弃 counter 实例标签无注销 | cep |
| C2-006 | C2-006 | File sink saveState 持监视器执行整 epoch 写盘+fsync | connector(file) |
| C2-007 | C2-007 | FileSourceReader 持 this 监视器执行文件 I/O（已声明边界残留） | connector(file) |
| C5-003 | C5-003 | RocksDBMapState 全量物化（热路径前提证伪降级维持） | rocksdb |
| C6-013 | C6-013 | CepOperator close 链中间异常无 suppressed 收集 | cep |
| C6-014 | C6-014 | deleteIfExistsQuiet 空 catch 静默吞 IOException | connector(file) |
| C7-008 | C7-008 | Avro schemaByIdCache 无上界无淘汰 | format-avro |
| C7-011 | C7-011 | checkConnection 以 initTransactions 作探测（owner doc 红线表缺行） | connector-kafka |

**计数核对**: P0×1 + P1×10 + P2×26 + P3×9 = 46 ✓（跨单元 P1×2 另计）

### 4.3 严重度迁移记录（相对原判级）

- 上修：C7-013 P3→P2（归并 MA-11 对齐）；MA-05 族内 C3-002/C5-006/C7-001/C8-007 终裁统一 P1（C5/C8 原判 P2）
- 下修：C2-004 P1→P2（C2 二轮，接线不可达）；C5-003 P2→P3（调用方普查）
- 维持争议终裁：MA-17 P2（C2-008 原 P3，见 §3.1）；C8-002 维持 P2；C6-001 维持 P0

### 4.4 C10 缺保护登记 → 缺陷族映射（16 条；修哪族需补哪测试）

| C10 | 关联（原） | 重映射至 | 测试先行要求 |
|---|---|---|---|
| C10-001 | C6-001 | **MA-01** | 先红后绿：close 传播/恰一次/幂等 |
| C10-002 | C1-002=C2-001=C6-004 | **MA-02** | 双线程压窗+白盒锁内取参断言 |
| C10-003 | C1-001 | **MA-03**（C1-001 面；assertSame udf 兼钉 C1-006 面，条件会红） | NFA 图实例不同+keyedStateStore 探针 |
| C10-004 | C1-005 | **MA-03**（C1-005 面） | 双线程超时错路由断言 |
| C10-005 | C2-003 | 跨单元 C2-003（挂 WI1.1-C1-001） | 并发 addReader×snapshotState 无 CME |
| C10-006 | C2-010 | 跨单元 C2-010（挂 WI1.1-C3-009） | 任务线程写×REST 快照无 CME/native 异常 |
| C10-007 | C3-002 四向族 | **MA-05** | 保留策略：高水位保留/历史删除/幂等守卫+行数上界 |
| C10-008 | C3-003 五向族 | **MA-04** | 挂死注入 typed 失败+锁限时释放 |
| C10-009 | C3-004 | **C3-004 独立** | 恢复态每表恰一 reader+污染输入去重 |
| C10-010 | C3-009=C4-004 | **MA-06** | 引擎线程不落 globalWorker+占池 fail-fast |
| C10-011 | C3-014 停止契约族 | **MA-07**（C10 终版登记已含族内 C4-005/C4-006 面口径，须同盖三面验证） | 快速重启有界终态+MBean 重注册+stopped 守卫覆盖排队窗口 |
| C10-012 | C5-002=C8-005 | **MA-08** | 键数参数化+堆驻留上界断言 |
| C10-013 | C8-001 | **C8-001 独立** | snapshot offset==last-emitted+恢复重放在途 K 条 |
| C10-014 | C8-006 | **C8-006 独立** | transaction.timeout.ms ≥ 超时×系数+耦合校验 fail-fast |
| C10-015 | C3-011 | **C3-011 独立** | 并发 run 第二线程终态形态（typed 异常或真实进入，不允许假 EOS） |
| C10-016 | 测试自身缺陷 | 独立（非缺陷族） | 无界自旋改 TestAwait.until + isAlive 断言补充 |

注：P1 之外各族（MA-09~MA-20、P2/P3 独立案）暂无 C10 登记，修复时按「非平凡缺陷修复须回归测试」总则补充，不强制先红后绿台账。

---

## 5. 修复批次划分建议（仅建议不做决定）

1. **P0 即时通道**: MA-01（producer close + AutoCloseable 通道），C10-001 先红后绿；同文件 **C8-006**（transaction.timeout.ms + 预算耦合校验）同批评估（同根因文件、测试点位相同）。
2. **Avro registry 集群**（同文件簇 AvroSchemaRegistryClient/AvroWireFormat，建议一计划三工单）: MA-04（请求超时+锁外加载）→ MA-10（typed 回落+负缓存退避）→ MA-11（HttpClient close），C7-008（缓存上界）顺带；C10-008 先红。
3. **Debezium 生命周期集群**（同一修复设计：专用单线程执行器+终结 latch+启动守卫，C4 二轮已证一改盖 MA-06/MA-07 全部三面）: MA-06 + MA-07 同批；C10-010/C10-011 先红；MA-07 与 **C3-011/C3-006**（同模块语义面）可同计划分票。
4. **RocksDB 内存/快照集群**: MA-08（流式快照）+ MA-14（流式迁移 helper，同一 RocksIterator 包装模式）同批（C5 复核已证同一修复模式）；**MA-02 + MA-09 + MA-17 同点位一改同批**（tags/removal/once-flag/锁内取参四处改动集中于 RocksDBMetricsRecorder，C10-002 先红，防互相回退）；MA-18（预算耦合+哈希增量化旋钮）与 MA-08 同计划（checkpoint 治理主题）。
5. **2PC 持久化治理集群**: MA-05（JDBC 台账+file manifest 保留策略，C10-007 先红）+ MA-13（epoch 缓冲/pendingCommits 上限旋钮）+ MA-15（锁超时）+ C6-007（孤儿清扫）——同在 2PC 提交链，建议同计划分票。
6. **CEP 集群**: MA-03（copyForSubtask round-trip，C10-003/004 反转）+ MA-16（timerKeys 清理）+ MA-20（counter 标签）+ C6-013（close 链 suppressed）+ C8-002（缓冲包络声明）——同 operator 文件簇。
7. **外部依赖超时契约**（一份契约工单分模块执行）: MA-04（HTTP）+ C8-009（JDBC 语句级）+ C8-011（message sendTimeout）——统一「外部依赖等待必须有界且 typed」契约与部署期校验。
8. **reader 并发加固**: MA-12（launch 守卫+交付字段同步，附 C2-004 回升条件）+ C3-012（close 兜底）；跨单元 C2-003/C2-010 随 WI1.1 批次闭合，本单元仅做受害侧加固（CHM 化）。
9. **P3 信息性项**: MA-19、MA-20、C2-006、C2-007、C5-003、C6-013、C6-014、C7-008、C7-011 按随手修/文档修复打包，不占独立排批。

---

## 附录：live code 抽验记录（本裁定新增证据）

- `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/metrics/RocksDBMetricsRecorder.java`：类 javadoc「…plus a one-shot warn log」（:26-28 附近）+ 方法 javadoc「one-shot-style warn」（:86-89）+ `readProperty` catch 块无条件 `LOG.warn` 无抑制字段（:91-101）——证实 §3.1 C2-008=C7-012 同一缺陷（MA-17）。
- `C7-round2.md` C7-013 条目正文：类声明非 AutoCloseable、构造即建 HttpClient、全模块无 close 调用点——与 C4-002 条目正文同锚点同机制，证实 MA-11 并入。
- `C8-round2.md` C8-013 条目正文：doSnapshot 对 native 目录全部 SST 无条件 sha256Hex、自认「C8-010 确立的缺陷类别的 F3 兄弟站点」——证实 §3.2 MA-18 同族相邻分组。
