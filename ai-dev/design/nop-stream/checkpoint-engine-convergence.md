# Checkpoint 双驱动面收敛设计（结构重构 WI9 测绘与裁定）

**日期**：2026-10-11
**范围**：`nop-stream-runtime`（`checkpoint/`、`execution/`、`coordinator/`、`deploy/`）、`nop-stream-core`（`execution/`、`environment/`、`checkpoint/`）
**状态**：草案（待 owner 确认；owner 确认后转 active，WI10/WI11 按本裁定实施）
**行号锚基线**：commit 2b78f8b7f4（2026-10-11）。锚以「类#方法」语义标识为主、行号为辅——WI10/WI11 改码后行号漂移时以语义标识定位。
**定位声明**：§三的七维对照表是对**两现存驱动面**的事实测绘，作为收敛裁定（§四）的证据基础；本文档不含「Proposed vs Current」演进叙事，收敛后的目标形态由 WI10/WI11 实施并在其收口时回写本档。

---

## 一、设计结论

1. **事实内核唯一，重叠在驱动面**。两驱动面共用同一个 `runtime.checkpoint.CheckpointCoordinator`：本地/savepoint 面 `GraphModelCheckpointExecutor`（GMCE）在 `GMCE#executeWithCheckpointSkeleton`（:668）内嵌构造它；分布式面由 `JobCoordinatorMain`（:158）、`RpcDistributedExecutor`（:222）、`EmbeddedDistributedExecutor`（:262）、`OpsJobManager`（:151）构造后注入 `JobCoordinator`（:489 字段）。ack 聚合、manifest 构建、commit/abort 扇出、retention 在两侧行级同源——结构重构 roadmap v1.1 的勘误成立：**不存在两套独立引擎，重复的是驱动面外围语义**。
2. **七维测绘净结果：4 完全一致、5 语义等价实现不同、10 真实语义分叉**（含 1 条相邻分叉）。分叉集中在 trigger 前置门、终态预算、快照失败传播、savepoint 产物契约、timeout abort 终局、外部 abort 通道、coordinator 级恢复深度、CANCEL 终态语义（§三）。
3. **路线裁决：方案 (a) 下沉共享内核**——以 `CheckpointCoordinator` 为事实内核，把仍散落在两驱动面的外围语义（savepoint 物化、restore 对齐、终态预算显式化）继续下沉为内核/共享服务，两驱动面变薄但保留。**拒绝方案 (b) 统一单一驱动面**（§四、五）。
4. **权威路径与分叉裁决**：按 E3 默认，分布式路径语义权威（coordinator 专测 10+ 文件、R1—R5 审计、gated 多 JVM 场景）。可从代码与设计文档判定的分叉按判定裁决（DIV-01 本地即时传播为目标语义——代码自认缺口 + 本地专测）；不可判定的按 E3 取分布式现状并升格独立缺陷项登记 r5-successors（DIV-02～06，§四.3；其中 DIV-03 仅指枚举器 stash 缺口按现状冻结——coordinator 级恢复入口向 manifest-first 对齐是另一条可判定的 §4.1 下沉项，两者不混同）。**任何分叉不在重构中顺手「统一」**。
5. **反向污染风险是本收敛的唯一真实正确性风险**：两驱动面语义若在收敛中被反向污染（把已验证侧语义错改为未充分验证侧变体，或反之），错误在单侧回归下不可见。因此 WI10/WI11 必须以「双驱动面在同一输入下产出等价结果」的差分测试为门；**不得以单侧回归通过为完成标准**（§四.4 硬约束）。

## 二、背景与动机

结构重构 roadmap Phase D 立项时假设「两套独立 checkpoint 实现」；v1.1 复审勘误为「GMCE 内嵌并委托 CheckpointCoordinator，非两套独立实现」。真正的重叠不在内核而在**驱动面**：分布式编排（`JobCoordinator` 侧）与本地编排（GMCE 的 `executeWithCheckpointSkeleton` 族）各自实现 trigger 时机、restore 后状态重建、savepoint 触发、终止模式等外围语义，且**无跨驱动面一致性测试钉**——「分布式恢复对、本地恢复错」的 exactly-once 风险正源于此。本档是 WI10（共享内核抽取）与 WI11（分布式路径切统一内核）的裁定依据：两者首句均依赖本文档的路线与分叉裁决。

## 三、七维对照表（事实测绘）

档位：〔一致〕行为完全一致（含参数与顺序）；〔等价〕外部可观察语义一致、实现路径不同；〔分叉〕外部可观察语义不同。

| # | 维度 | GMCE 侧（本地/savepoint 面） | JobCoordinator 侧（分布式面） | 档位 |
|---|------|------------------------------|--------------------------------|------|
| 1a | trigger 周期驱动 | `TaskCheckpointWiring#startBarrierScheduler` :275-285 `scheduleAtFixedRate(interval=checkpointInterval)`，每 tick `tryTriggerPendingCheckpoint(CHECKPOINT)`，仅异常计 `incrementTriggerFailures` | `JobCoordinator#startPeriodicCheckpoints` :1259-1292 `scheduleWithFixedDelay` :1272，每 tick `triggerCheckpoint()`，同样仅异常计数；间隔外部注入（OpsJobManager :191 / JobCoordinatorMain :311） | 等价 |
| 1b | trigger 前置门 | trigger 后立即进程内 fan-out（`triggerBarrierOnAllInvokables` :290-313），无 leader/恢复期概念 | `tryTriggerCheckpoint` 三重门：`running` :908、`active`（standby 拒绝）:913-916、`recoveryPending` 抑制窗口 :924-927；无 RPC 服务 fail-fast :962-965 | **分叉**（能力不对称：分布式独有 HA 门控） |
| 1c | 终态 trigger 预算 | `triggerCheckpointBounded(type, config.getCheckpointTimeout())`（savepoint :432 / terminal :548 / final :790）；默认 600000ms（`core.checkpoint.CheckpointConfig` :28） | `terminateWithTerminalSavepoint` :2414 用 `terminationCheckpointTimeoutMs`，默认 60_000（:100，可 set :2691） | **分叉** |
| 1d | coordinator 内部门控 | `tryTriggerCheckpointWithReason` :459-581（maxConcurrent→minPause→内联完成；bounded retry 退避 50→500ms :442） | 同一类同段代码（经 :929 到达） | 一致 |
| 2a | barrier 对齐机制 | 每 task 一个 `CheckpointBarrierTracker`（`wireTaskCheckpointPipeline` :153-158），对齐在 InputGate | TM 侧同一 tracker 类（`TaskManager#triggerCheckpoint` :842-859，source-aware 注入） | 一致 |
| 2b | barrier 注入通道 | 进程内直调 tracker（:290-313），ACK 直进 `coordinator.acknowledgeTask`（:155） | RPC 扇出带 fencing epoch（:935-959），TM 侧 fencing 不匹配 typed 异常（TaskManager :848-852）；ACK `collectAck` :979-1017 三重校验 | 等价（分布式多 fencing 拒绝与 RPC 重试） |
| 2c | 快照失败传播 | 失败监听器直调 `coordinator.reportTaskCheckpointFailure`（Wiring :156-157），立即 abort 匹配 pending | `RemoteTaskDeploySupport#remoteAbortListener`（deploy/ :206-212）**只 LOG**，注释自认「waiting for coordinator-side timeout abort」 | **分叉**（分布式侧已声明能力缺口） |
| 3 | epoch manifest 格式 | 共内核 `CheckpointCoordinator#buildEpochManifest` :1658-1687 + `LocalFileCheckpointStorage#storeEpochManifest` :489-510（JSON、原子改名、retention 默认 5） | 同一类同段代码；fingerprint 是否携带取决于入口给的 StreamModel | 一致 |
| 4a | savepoint 触发（导出） | `triggerSavepoint` :413-464：一次性 runtime、目标路径覆盖 storage :374-376、`materializeKeyGroupOwnership` :454、**返回 savepoint 路径** :460 | `EXPORT_SAVEPOINT` :2374-2377：EXPORTED_SAVEPOINT 型、落作业默认存储、无路径覆盖、**不返回路径**、job 不停 | **分叉** |
| 4b | DRAIN/SUSPEND 的 checkpoint 型 | DRAIN→`TERMINAL_SAVEPOINT` :511；SUSPEND→同型+stopSources :518 | DRAIN→同型 :2365；SUSPEND→同型 :2370 | 一致 |
| 4c | savepoint 恢复入口 | `executeWithSavepoint` :466-488 + `restoreFromSavepointPath` :1207-1254（三级查找+全 miss loud fail :1233-1248） | **无对等入口**（JobCoordinator 全文无 `savepointPath` 恢复调用，grep=0） | **分叉**（能力不对称） |
| 5a | 两阶段 commit | 共内核 `onCompletePersistSuccess` :948-1021（commit-only-after-durable、失败重试、participant 直调） | 同一内核 + `registerDistributedCommitForwarder` :1334-1402 RPC 扇出 + 幂等账本吸收丢通知 | 等价 |
| 5b | abort 传播（timeout） | `registerLocalAbortHandler`（GMCE :887 → Wiring :320-375）：epoch-precise 释放对齐，无在飞 epoch 时取消任务线程，`abortMarked` → `checkAbortMarker` :893-899 掷 `ERR_STREAM_CHECKPOINT_ABORTED`（**作业进入失败态**） | `registerDistributedAbortHandler` :2556-2601：reason 以 "Timeout" 开头**只弃 epoch、任务继续**（:2568-2573，注释明示「routine back-pressure event」）；仅快照失败才 cancelTask RPC | **分叉**（同一超时事件终局相反） |
| 5c | 外部 abort 命令 | 无外部 abort 入口 | `abortCheckpoint(epochId)` RPC :2505-2530（standby 拒绝、无匹配 WARN no-op） | **分叉**（能力不对称） |
| 6a | 恢复入口（主路径） | `restoreFromCheckpoint` :1054-1113：manifest-first（:1061）→counter 推进 :1071→指纹校验 :1073→**枚举器 stash** :1081/:1124-1148→状态重建 :1086；仅 `hasExplicitStoragePath` 才 auto-restore :966-977 | coordinator failover 重建 `rotateFencingEpochCoreLocked` LEADERSHIP 分支 :2020-2052 仅 `restoreFromCheckpoint()` :2029（**raw checkpoint：不读 manifest、不做指纹校验、枚举器 stash 无分布式调用方**）；task 侧 redeploy 恢复走 manifest-first（GMCE :1290-1360 经 RemoteTaskDeploySupport :147） | **分叉** |
| 6b | 状态重建细节 | `restoreTaskStatesFromSource` :1442-1565（反向 vertex 差分拒绝、KeyGroup 重路由 rescale、2PC 跨并行度拒绝、(P,M) 一致性） | task 侧复用同一方法（:1533-1535 子任务过滤） | 等价（分布式把「何时恢复」移到 per-subtask deploy） |
| 6c | fingerprint 校验 | manifest 指纹 vs 当前指纹→快失败拒恢复 :1165-1171 | task 侧同源快失败（:1312）；coordinator 级重建无指纹概念 | 等价 |
| 7 | 终止模式 | `handleJobTermination` :498-526：CANCEL→`COMPLETED_POINT_TYPE` final checkpoint :523/:784-803；DRAIN/SUSPEND→TERMINAL_SAVEPOINT 等待；SUSPEND 停源=关 OutputWriter :586-597；**无 EXPORT_SAVEPOINT 用法** | `terminate` :2312-2345：CANCEL→`terminateCancel` **立即停止、无终态 checkpoint** :2347-2360；DRAIN/SUSPEND→TERMINAL_SAVEPOINT 后 stop；SUSPEND 停源=stop()+cancelTask 扇出；EXPORT_SAVEPOINT→job 不停 :2481-2484；终态失败→failJob :2464 | **分叉**（CANCEL 语义相反；SUSPEND 停源机制不同；EXPORT 仅分布式） |
| 附 | 状态后端（相邻） | LOCAL 用 `checkpointConfig.getStateBackend()`（可配） | 分布式固定 `MemoryStateBackend`（`RemoteTaskDeploySupport` :169-197 注释自认分叉、待裁决） | **分叉**（相邻维度补录） |

### 两条加载链（显式记录）

**本地/savepoint 链（SPI + ServiceLoader）**：SPI 注册文件 `nop-stream-runtime/src/main/resources/META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory`（内容一行 `...CheckpointExecutorFactoryImpl`）→ `core.environment.StreamExecutionEnvironment#discoverCheckpointExecutorFactory` :211-231（`ServiceLoader.load` :217-218；多实现 typed 异常 :223-227）→ 解析门 `requireCheckpointExecutorFactory` :241-254（显式 setter :199-201 > ServiceLoader > typed fail-fast，拒绝静默退化）→ `CheckpointExecutorFactoryImpl`（68 行，五方法一行委托）→ GMCE :216-292 skeleton（:668 内嵌 coordinator）。

**分布式链（直驱，不经 SPI）**：四处直接 `new CheckpointCoordinator(...)`（JobCoordinatorMain :158 / RpcDistributedExecutor :222 / EmbeddedDistributedExecutor :262 / OpsJobManager :151）→ `JobCoordinator` 构造器注入（:479-497，字段 :489）→ 直驱调用点：周期 trigger :929、ACK :1006-1009、终态 trigger :2414-2415、failover 重建 :2029、counter 推进（JobCoordinatorMain :176-178）、abort 扇出 :1789/:2529、终态收缩 :1102-1103、事件总线 :504-506。**全链无 ServiceLoader / `ICheckpointExecutorFactory` 引用**——两条链只共享 coordinator 实例，配置与入口互不可见。

## 四、核心设计：收敛裁定

### 4.1 路线裁决：方案 (a) 下沉共享内核（拒绝 (b)）

**选 (a)**：以 `CheckpointCoordinator` 为事实内核继续下沉散落外围语义，两驱动面变薄但保留。

理由：
1. 内核已经共享（§一.1），病根是驱动面外围语义分叉而非「面多」；(a) 直接对病根施术，(b) 则把两个面捏成一个，但分叉语义仍须逐条裁决后才谈得上「统一」——(b) 把裁决难度乘以迁移风险。
2. 差分门需要参照面。(b) 合并后本地面消失，双跑校验失去第二参照，「单侧回归不可见」的风险敞口最大化；(a) 全程保留两面对跑，直到差分归零。
3. 迁移成本：(a) 按语义块渐进下沉（每块先钉后拆，E1）；(b) 一次性合并 `JobCoordinator`（2886 行）+ GMCE（1872 行）≈ 4700 行编排代码，10 条分叉须在同一变更里预裁完毕，违反「先钉后拆」方法论。

**下沉范围（散落语义清单，(a) 的实施输入）**：
- savepoint 物化：目标路径覆盖、KeyGroup 归属戳、路径返回（GMCE :362-395/:413-464）下沉为共享 savepoint 服务；分布式 `EXPORT_SAVEPOINT`（JC :2374-2377）复用同一物化，产物契约差异（路径返回与否）保留为面级参数。
- restore 对齐：coordinator 级 failover 重建（JC :2029 raw）与本地 manifest-first（GMCE :1054-1113）的差距收敛——分布式重建复用同一 manifest-first 助手（行为变化由 §4.4 差分门放行）。
- 终态预算显式化：1c 的 600s/60s 统一为命名配置（terminationCheckpointTimeout 语义），默认值归属由差分语料放行后裁定。
- abort 机制与策略分离：epoch-precise abort **机制**（tracker 通知、对齐释放、fencing 扇出）下沉内核；abort **策略**（timeout 终局）保留为面级 policy hook（DIV-02 未裁决前不得合并）。

### 4.2 三问裁定

1. **哪条路径语义权威？** E3 默认：分布式路径权威——coordinator 专测 10+ 文件（StandbyStateMachine / RecoveryPendingCleanup / FencingEpochUnification 族）、R1—R5 审计、gated 多 JVM 场景的证据覆盖显著更强。本档测绘发现 1 条可判定的反例（DIV-01，本地证据更强），按证据反转处理；其余分叉按本条默认。
2. **分叉项以谁为准？** 逐条裁决见 §4.3。
3. **迁移期双跑校验怎么做？** 见 §4.4。

### 4.3 分叉逐条裁决

| 分叉 | 裁决 | 依据与登记 |
|---|---|---|
| 1b 分布式独有 HA 门控 | **保留**（非缺陷） | standby/recoveryPending/fencing 是分布式执行形固有的 HA 需求，本地无此概念无对应物；不属「待统一」分叉 |
| 1c 终态预算 600s vs 60s | **显式化后统一概念，默认值经差分语料放行** | 同名语义两种默认是历史层积；下沉为命名配置，迁移期两 faces 各持现值，差分语料证明等价后归一（方向：分布式 60s，E3） |
| 2c 快照失败只 LOG | **本地语义为目标；分布式缺口登记 DIV-01** | 分布式侧注释自认「waiting for timeout abort」是已声明能力缺口；本地即时传播有专测（TestSingleInputAbortWiringE2E / TestCheckpointAbortWiring）。不在重构中顺手补 RPC——登记后由 WI11 或 successor 立项 |
| 4a savepoint 产物契约 | **保留双形态，物化下沉共享** | 交互式导出（本地：路径返回+归属戳）与运行中导出（分布式：默认存储不停机）是两种合法使用契约；共享的是物化机制（§4.1） |
| 4c 分布式无 savepoint 恢复入口 | **登记 DIV-04 能力缺口**（与 DIV-03 合并审议） | 「从显式 savepoint 路径启动」仅本地存在；分布式恢复只认 checkpoint 目录。是否补齐属产品裁定，非重构 scope |
| 5b timeout abort 终局相反 | **升格 DIV-02 供 owner 裁决（设计意图与代码现状冲突）** | `checkpoint-design.md` §8.7 设计意图明确「checkpoint 超时 abort 后 job 明确进入失败态……distributed 路径 lease failover → globalRecovery」，且其 distributed 段描述超时经 cancelTask 取消——**与现行代码不符**：JC :2563-2573 对 Timeout 只弃 epoch（「routine back-pressure」注释）并有专测（TestDistributedAbortPath）锁定。即分布式超时容忍是**偏离已记录设计意图的后续演进**，非「不可判定」。裁决输入 = §8.7 设计意图 vs 代码演进及其测试；E3 的「不可判定才取分布式」前提在此不适用。**重构期两侧策略一律不动**，policy hook 隔离 |
| 5c 外部 abort 仅分布式 | **保留**（能力不对称） | 运维通道，本地无对等需求 |
| 6a coordinator 级恢复深度 | **分布式侧向本地 manifest-first 对齐（4.1 下沉项）；枚举器 stash 缺口登记 DIV-03** | 恢复语义分叉正是 roadmap 预警的 exactly-once 风险点；manifest/指纹语义两侧行级同源（3/6c 已一致），对齐的是 coordinator 级入口；枚举器状态分布式无恢复调用方为潜在正确性缺口，登记待证 |
| 7 CANCEL 终态语义相反 | **分布式与设计文档一致（立即取消）；本地偏离已记录契约——升格 DIV-05 供 owner 裁决** | `checkpoint-design.md` §7.2 CANCEL 流程「作业结束，不保证输出完整」无终态 checkpoint 步骤；§7.3 将 `COMPLETED_POINT_TYPE` 定义为「bounded source 的最终 checkpoint」——本地 CANCEL 无条件触发 final checkpoint（GMCE :523/:784-803）**超出该定义**（更保守的有意强化，有专测锁定）。裁决输入 = §7 契约 vs 本地强化；重构期不动 |
| 附 状态后端固定 | **登记 DIV-06** | 分布式部署恢复侧固定 MemoryStateBackend 与 LOCAL 可配后端分叉，`RemoteTaskDeploySupport` 注释自认待裁决 |

登记载体：`ai-dev/backlog/nop-stream-r5-successors.md` 新增「checkpoint 驱动面分叉专项（WI9）」节（DIV-01～06），与本文档双向互指；roadmap「各 roadmap 不重复立项」边界维持——本档只裁定，缺陷处置归 r5-successors 的既有台账纪律。

### 4.4 双跑校验门（WI10/WI11 硬约束）

1. **差分 harness**：同一模型/JobGraph + 同一输入 fixture，分别经本地链（SPI→GMCE）与分布式链（EmbeddedDistributedExecutor→JobCoordinator）执行；断言面 = 算子输出序列、epoch manifest 内容（格式零变更硬约束下逐字段比对）、restore 后状态指纹。
2. **先钉后拆（E1）**：WI10 任何抽取动作 landing 前，差分 harness 必须在**当前未改代码**上双跑绿（钉住现状）；不接受「重构后单侧测试也绿」。
3. **格式零变更**：epoch manifest / checkpoint 存储布局 / 序列化格式在本 roadmap 全程零变更（WI10 硬约束）；存量 checkpoint fixture 首次入库作为格式钉。
4. **反向污染风险声明（硬约束）**：两驱动面语义在收敛中被反向污染（把已验证侧语义错改为未充分验证侧变体或反之）时，单侧回归不可见。因此 WI10/WI11 的完成标准是**双驱动面差分归零**，任何单侧回归通过不构成完成证据；差分不归零的分叉项只能走 §4.3 的登记路径，不得在重构中顺手统一。
5. **CC-06/CC-17 边界**：coordinator monitor 内阻塞 RPC fan-out（CC-06）与无界队列/终态 RPC 同步发送（CC-17）为 r5-successors 既有登记项，属 WI10 落地时的顺带修正（差分绿后独立提交），不在本档重复设计。

## 四.5 WI10 落地回写（2026-10-11，plan 2426）

- **SavepointMaterializer 下沉落地**：SAVEPOINT 触发/等待/归属戳/落盘序列下沉 `runtime.checkpoint.core.SavepointMaterializer`，本地面委托（字节级等价，savepoint 测试族 + 差分 harness 复跑绿）；屏障扇出与 KeyGroup 归属保持面级机制回调。§4.1「EXPORT 接入」维持 WI11 评估。
- **1c 概念统一落地**：`CheckpointConfig.terminationCheckpointTimeoutMs` nullable 旋钮（默认 null→回退 checkpointTimeout，字节级零行为变更）；触发预算与 await 预算语义显式分离。**默认值归属仍留 WI11 差分语料**（§4.3 1c 裁定不变）。
- **6a 归一落地**：`JobCoordinator` failover 重建 manifest-first 校验门（manifest 读取→指纹 fail-closed〔无源=不可证明〕→counter 越 manifest epoch 封堵 shadow window）+ raw restore 保留为视图填充。编排适用子集 = manifest/指纹/counter 三步（enumerator stash 与 task 状态重建不适用 coordinator 级，回写 §4.1 切分）。差分 harness + fixture + 三模块基线全绿放行（§4.4 门）。
- **abort 机制下沉裁弃**（§4.1 bullet 4 修订）：本地 abort handler 为 legacy 单参签名（无 reason 输入）、分布式判定点为单句前缀判断——共享契约即「1 常量 + 1 前缀判断」伪抽象且 DIV-02 owner 裁决未决；机制下沉 defer 至 DIV-02 裁决后随 r5-successors 处置，DIV-02 冻结语义维持。
- **JC 启动路径残留**（JobCoordinatorMain raw restore + counter 推进）未在本 WI 对齐，如实残留——归 WI11 评估。
- **新增实测事实（修正测绘）**：本地 face pipelineId 实为 `pipeline-0`（与分布式同约定，修正 §三加载链旁注）；终态 COMPLETED_POINT_TYPE epoch 仅写 manifest 无数据行，restore 点=最新数据 checkpoint——恢复点定时漂移属引擎语义，差分断言按边界无关不变量钉。

## 四.6 WI11 落地回写（2026-10-11，plan 2432）

- **CheckpointStorages 工厂**：`runtime.checkpoint.storage.CheckpointStorages`（create(config) 分派 local/jdbc-failfast/s3 + defaultBaseDir + local(path) 显式路径入口）。GMCE.createStorage 改委托保留签名（3 个测试文件约 10 处直调零迁移）；savepoint 存储/launch/embed/rpc 五处显式路径构型收口经 `local(path)` 单 choke-point（savepoint 格式本就是 local file——直构属设计内，收敛意义在未来的存储类型演进单点切换）。src/main 内 `new LocalFileCheckpointStorage` 仅剩工厂内部。
- **启动路径校验门**：共享助手 `checkpoint.core.RestoreGates.applyManifestGate`（指纹 fail-closed〔无源=不可证明〕+ counter 越 manifest epoch 封堵 shadow window）；JC failover 门与 JobCoordinatorMain 启动路径两调用点同语义；**启动路径语义裁定：门失败 = launch 失败（抛出），非静默 fresh**。TestRestoreGate 四场景钉。
- **EXPORT 接入评估结论 = defer**（4 契约缺口，接入须先做 materialize 契约参数化设计）：①materialize 硬编码 SAVEPOINT vs 矩阵钉 EXPORTED_SAVEPOINT；②预算源三方冲突（config-resolve/JC 字段/矩阵显式 set）；③分布式面无 GraphExecutionPlan，KeyGroup 归属戳机制不存在（WI9 4a 原裁定 EXPORT 无归属戳）；④storage 实例不可达（JC 仅持路径字符串）。语义注记：materialize 末次 storeCheckPoint 为幂等覆写；EXPORT 丢弃路径返回不损失持久性（coordinator 完成路径先行落盘）。successor → r5-successors。
- **终态预算默认值归一 = defer**：无差分语料可仲裁（钉子测试属 WI10 零变更契约；其余相关测试显式传预算或不走 resolve）。E3 方向（分布式 60s）为 successor 指引 → r5-successors。
- **已知差异台账（双跑归零状态）**：恢复点=最新数据 checkpoint（定时漂移，结构性保留——差分断言按边界无关不变量钉）；指纹非对称（分布式携带/本地 JobGraph 入口 null，结构性保留——GMCE pinned asymmetry）；终态 COMPLETED_POINT_TYPE 仅本地面写（生命周期差异，结构性保留）；以上三项均已在差分 harness 白名单中显式排除并有依据记录——无未解释差异。

## 四.7 execution 包拆分评估裁定（plan 2432，只裁不拆）

- **迁出后剩余职责**：dispatcher 双实现（Embedded/Rpc）/supervision（SupervisionLoop）/rescale（RescaleStateAssembler）/TM 侧 wiring（TaskCheckpointWiring）/plan 生成 + storage 工厂委托（GMCE 内）/DeploymentPlanProvider——checkpoint 编排主体已归 checkpoint/coordinator/core。
- **裁定：暂不拆分。** 理由：①checkpoint 相关大头已随 WI10/WI11 迁出（materializer/gate/factory），剩余五类职责间耦合点集中在 GMCE 一个类而非包级网状；②execution 的扇出 7 包主要来自 GMCE 与 JobCoordinator 两个巨型类，包拆分不解决类级膨胀（RD-05 JobCoordinator 拆分才是对症项，归 r5-successors 既有登记）；③拆分要求先定义 execution 与 checkpoint 两包的稳定接口面，当前 WI11 刚收口，接口面未经一个迭代周期检验。**复核触发条件**：GMCE/JobCoordinator 任一完成类级拆分（RD-05）后重新评估，或 execution 包再增第 8 个扇出方向时。

## 五、拒绝了什么

- **方案 (b) 统一单一驱动面引擎**：见 §4.1 三条理由。核心拒绝理由：合并即消灭差分参照面，反向污染敞口最大化；且内核已共享，(b) 的收益（一个面）不解决病根（面间语义分叉）。
- **「按 Flink 语义全量对齐」**：Flink 是参照系不是裁决者（平台多处有意偏离，如 handleJobTermination 的 final checkpoint 设计）；且 Flink 自身在 checkpoint timeout 上也有可配的容错策略谱系。分叉裁决以本仓证据覆盖与 E3 默认为准，Flink 对照仅作 DIV-02/05 的 owner 裁决输入。
- **「重构中顺手统一可判定分叉（1c/6a）」**：违反 roadmap E4 同源的「先钉后拆」纪律——行为变化必须经差分门放行并独立提交，不得搭结构性变更的便车（否则回归归因不可分）。

## 六、与已有设计的关系

- `ai-dev/design/nop-stream/checkpoint-design.md`：存储/格式/指纹契约（§8.4.1「Serializer Fingerprint 策略」、状态迁移注册载体）**全部维持**；本档只裁定驱动面编排语义，不触碰存储/格式契约（§4.4.3 与之一致）。例外：测绘发现 §8.7 distributed 段与 §7.2/§7.3 CANCEL 契约同现行代码存在两处偏差（DIV-02/DIV-05），已按该档 Updated 惯例在原节补偏差注记并指向本文档裁决项，原设计意图文字保留为裁决输入。
- `ai-dev/design/nop-stream/core-design.md` §1.2/§1.2.1（StreamComponents 注册表与所有权）：SPI 加载链的 core 侧契约来源；本档 §三加载链记录与其一致。
- `ai-dev/design/nop-stream/distributed-runbook.md`：gated 多 JVM 场景手册；§4.4 差分 harness 的分布式腿按其手册单独跑并留档。
- `ai-dev/backlog/nop-stream-r5-successors.md`：DIV-01～06 缺陷登记载体；CC-06/CC-17 的既有台账。
- 结构重构 roadmap Phase D：WI10/WI11 的执行输入即本档 §4.1（下沉范围）+ §4.3（分叉裁决）+ §4.4（差分门）。
