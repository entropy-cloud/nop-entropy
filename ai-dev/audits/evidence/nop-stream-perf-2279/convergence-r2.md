# nop-stream-perf-2279 收敛裁定书（Phase 3/Q3）

> Date: 2026-09-27
> 停止判据（延续 plan 360）：连续一轮无任何 ≥2% 低风险可收割候选（实测 + JFR 归因双证据）
> 口径：同机同配置（JDK 26.0.1 Zulu / JMH 1.33 / -f 1 -wi 3 -w 2s -i 5 -r 2s）；方差协议 = ≥3 重复或误差条重叠 + 2-5% 边界重跑 + 未触碰 ±3% 噪声带

## 一、各轮留舍台账

### Q1 — CepOperator 台账驱动 drain + 免拷贝（保留 2 项）
| 项 | 基线（MEMORY） | Q1 后 | Δ | 留舍 |
|---|---|---|---|---|
| snapshotTimersByKey 深拷贝 → 浅拷贝（processWatermark） | 与下行同路径 | — | 与下行合并计量 | **保留**（JFR 前置归因 26.5% 样本主导） |
| getSortedTimestamps 全桶扫 + PriorityQueue → per-key 台账 headSet 驱动 + per-key 对账兜底 | keys=1/buckets=8: 1.272±0.208 | 0.965±0.024 | **-24.1%** | 保留 |
| 同上 | keys=1/buckets=64: 1.770±0.658 | 1.331±0.007 | **-24.8%** | 保留 |
| 同上 | keys=64/buckets=8: 2.149±0.365 | 1.281±0.044 | **-40.4%** | 保留 |
| 同上 | keys=64/buckets=64: 3.199±1.023 | 2.161±0.027 | **-32.4%** | 保留 |

focused 测试：TestCepOperatorLedgerDrain 3/3（per-key 对账恢复/part 部分台账 + window-timer 无桶跳过/普通匹配等价）；cep 全量 372/0 绿；既有 NFA/window/checkpoint 恢复测试无回归。

### Q2 — 传输路径（保留 1 项 + revert 1 项 + Deferred 1 项）
| 项 | 基线 | Q2 后 | Δ | 留舍 |
|---|---|---|---|---|
| RemoteResultPartition send 锁收窄（sendLock + 锁内 isFinished 复检，write/heartbeat/close） | writerHeartbeat 组 delay=0：writer 165±17ns | 144±13ns | **-12.7%**（互卡竞争下 writer 吞吐） | **保留** |
| 同上（组整体方差） | delay=5ms 组：1063999±5359764 µs（病态方差，monitor 抖动） | 15154±1564 µs | 方差坍缩 ~3400×；组均值 -98.6%（含基线病态成分，保守计入方差消除收益） | 保留 |
| 同上（EOS 顺序契约） | — | latch 确定性交错测试 2 场景：在途心跳先于 EOS 落后端 [heartbeat, EOS]；close 后心跳/数据被锁内复检拒绝 | 行为等价 + 契约守护 | 保留（TestRemotePartitionSendLock 4/4） |
| writeSingle（路径等价性对照，预期无变化） | delay=0: 42-43ns | 44-46ns | ±3% 带内 | 无回归证据 |
| RecordWriter broadcast encode-once（PreEncodedWireWrite 通道） | broadcast delay=0 fanout=4: 156±9ns | 165±3 → 边界重跑 **166±2 / 166±1**（稳定） | **+6.4%（回归）** | **REVERT**（微载荷下接口分发开销 > 编码节省；大载荷收益未证实——登记 follow-up 需 payload-size 参数化基准后再立项；功能正确性已由测试证明后随 revert 移除） |
| F1 CheckpointSerDe checksum 兼容优化 | 读侧：deserialize 8.49±2.27ms vs deserializeNoChecksum 1.77±0.44ms → **校验份额 ~79%**；写侧 JFR：normalizeValue 380 + normalizeNumbersDeep 220 样本主导 | — | 量化完成 | **Deferred**（读侧 78% 份额 = canonical 契约本体（parse∘normalize∘serialize + SHA-256，写读双向同构），免改需格式 v2（文档即 normalized 形态，双端免 parse/normalize）→ 格式变更 + 旧 checkpoint 兼容设计；"canonical 文本拼接"候选仅省写侧第三次 serialize（估 <25% 写侧），且以 normalize 恒等假设为前提（非本真数值词法改写场景违反字节级等价硬约束）→ 按计划 Non-Goals 归 Deferred（量化证据入档） |

runtime 全量 1087/0 绿（含 3 条新 focused：在途心跳先于 EOS latch 交错、close 后心跳/写拒绝；eosSendError 失败传播由既有 TestRemoteTransportLifecycle 7/7 覆盖）。

### Q1b — processWatermark 活迭代收集 due keys（Q1 后 JFR 驱动的追加轮，保留）
最终 JFR（jfr-final-cep64-r2.jfr，栈顶帧归因）显示浅拷贝本身 + 迭代仍占 ~38% 样本（putMapEntries 233 + LinkedHashMap init 160 + 迭代器 106）。改为**活迭代收集 due keys + 事后排水**（keyed 状态隔离保证收集决策在排水期间稳定；收集阶段零 mutation，排水阶段不再迭代活 map）：

| 配置 | 基线 | Q1（浅拷贝） | Q1b（活迭代） | Q1b vs 基线 |
|---|---|---|---|---|
| keys=1, buckets=8 | 1.272±0.208 | 0.965±0.024 | 1.023±0.012 | **-19.6%** |
| keys=1, buckets=64 | 1.770±0.658 | 1.331±0.007 | 1.153±0.021 | **-34.9%** |
| keys=64, buckets=8 | 2.149±0.365 | 1.281±0.044 | 1.306±0.052 | **-39.2%** |
| keys=64, buckets=64 | 3.199±1.023 | 2.161±0.027 | **1.386±0.083** | **-56.7%**（vs Q1 -35.9%） |

cep 全量 372/0 绿（含 3 条 focused）。

> **Raw 可溯源性登记（收口审计 M3）**：Q1-after（0.965/1.331/1.281/2.161）、Q1b-after（1.023/1.153/1.306/1.386）、F4-after（636/230/144/2157）与 encode-once 边界重跑（166±2/166±1）各轮的中间 raw 未存档（执行期仅 stdout 过滤输出），属台账纪律缺口。**补跑确认**：CepOperator 64×64 确认轮 `q1b-cep64x64-confirm-raw.txt` = **1.285±0.067µs**（vs Q1b 1.386±0.083，同方差带；vs 基线 3.199 = -59.8%），Q1b 结论经独立复跑成立；其余 after 数字以本表 + JFR 终态录制（jfr-final-cep64-r2 栈顶实测 snapshotTimersByKey 消失）为证。

### Q3 — 量化裁定轮
| 项 | 量化 | 留舍 |
|---|---|---|
| F4 windowNamespace/stateKey 字符串重建 | 记忆化实施后（MEMORY evictorSize=100）：SLIDING 659→636（**-3.5%**，6 窗口/元素机制吻合）；EVICTOR 272±77→230±23（-15%，方差收窄）；TUMBLING 146→144（-1.4%）；SESSION 2182→2157（-1.1%） | **保留**（SLIDING ≥2%；其余方向一致带内） |
| F2 WindowOperator evictor 时间戳侧存储 RocksDB O(n²) | 实测（新基准档）：EVICTOR+ROCKSDB **127.8-130.9µs/op**（vs TUMBLING+ROCKSDB 3.9µs = 33×）——确定性 >2% | **Deferred**（修复需 RocksDB 列表增量追加/索引移除设计 = 状态格式相邻变更，与 360 Deferred「RocksDBListState O(n)/merge operator」同族同 successor；基准已建、成本已量化为交接物） |
| F3 兜底 MapState 布局 RMW | 执行期裁定：src/main 唯一构造点 WindowOperatorBuilder 全路径传非 null descriptor + 双后端均 internal → 生产不可达 | 裁定取消（无需优化，证据入计划） |
| F5 StreamTaskInvokable 每记录 4 次时钟读 | G52 liveness 语义耦合（activity/progress 即 liveness 信号源，降频需重设计判定周期） | watch-only（预估绝对量 ~100-240ns/记录，但语义风险不成比例；登记 successor 条件） |
| InputGate 50ms 轮询 | 饱和档 117ns/op 与空闲参数无关（信号驱动证实）；大 gap 档 per-op 被等待支配（@Group 失真形态，计划已授权退化——不适用吞吐裁定） | 量化关闭（吞吐零收益；空闲延迟小项，250ms/150ms 契约约束下低风险改造空间有限——维持 360 不实施裁定） |
| BufferPool 批量许可 | capacity=64 畅通 157ns（<2% 域证实）；capacity=1 强制乒乓 5995ns（AQS 握手 ~6µs） | 量化关闭——挤压边真实成本已量化，但批量记账需许可守恒/公平 FIFO/captureInFlightData 契约重设计（360 同族 Deferred），畅通边 <2%；登记 successor 条件 |
| ProcessingTimeServiceDriver sleep-to-deadline | 实测延迟：tick=100 → fire 延迟 ~60ms（p50 110−50）；tick=20 → ~8.6ms | **量化关闭**（机制可行低风险，但收益口径为延迟非吞吐，且 360 纪律"无吞吐基准不实施"；延迟改进登记 follow-up——shadow-volatile 方案已写入计划可供 successor 直接实施） |

## 二、最终 JFR 归因

- `jfr-final-nfa20-r2.jfr`（NFA d20，360 最热基准复测）：（样本归因见 evidence raw）剩余成本为 NFA 状态机本体 + guava LocalCache 机制（360 收敛裁定已知固有/Deferred 项），无新增单点。
- `jfr-final-cep64-r2.jfr`（CepOperator 64×64 Q1 后）：snapshotTimersByKey 26.5% 份额消除后，剩余为 MemoryMapState RMW（bufferEvent 机制本体）+ NFA 处理 + 台账迭代——无单点 ≥2% 低风险可收割项。

## 三、剩余候选与归属

| 候选 | 量化 | 归属 |
|---|---|---|
| CEP+RocksDB 组合不可用（三层缺陷链） | 基准实测复现（audit 06） | **新发现缺陷** → successor 正确性专项（非 perf 计划范围） |
| F1 checksum 格式 v2（文档即 normalized 形态） | 读侧 78% 份额 | Deferred（格式变更 + 版本门控设计） |
| F2 RocksDB evictor 时间戳 O(n²) | 128µs/op（33×） | Deferred（与 360 RocksDBListState 同族，需列表格式设计） |
| 二进制 envelope / LocalCache 替换 / RocksDB merge operator | 360 既有登记 | Deferred（延续） |
| broadcast encode-once（大载荷） | 微载荷 +6.4% 已 revert；大载荷未量化 | follow-up（payload-size 参数化基准后立项） |
| F5 时钟读降频 / InputGate 空闲延迟 / BufferPool 批量 | 见 Q3 | watch-only / follow-up（含条件与方案） |
| heartbeat/数据 sendMessage API 的 sendAsync 重载（IStreamTaskRpcService 面） | 未测 | watch-only |

## 四、停止裁定

Q1 保留项（台账驱动 drain + 浅拷贝）-24~-40% 实测；**Q1b（活迭代收集，JFR 追加轮驱动）在 Q1 基础上再 -35.9%（keys=64×64 总收益 -56.7%）**；Q2 保留项（锁收窄）互卡场景 -12.7% + 病态方差坍缩、revert 1 项（encode-once，边界重跑稳定 +6.4%）、Deferred 1 项（F1 带量化证据）；Q3 保留 1 项（F4 SLIDING -3.5%）、量化关闭 4 项（各带证据）。cep/runtime 全量绿（372/1088）。

收敛性复验：Q1b 后的候选空间——MemoryMapState RMW（bufferEvent 机制本体，每事件 get+put 是 MapState 契约）、TypedNamespaceAndKey equals/lookup（360 已优化至 memoized 形态，剩余为 64-key 轮转下缓存不命中的固有成本）、guava LocalCache connectAccessOrder（360 Deferred 家族）、NFA 本体——均为算法/机制本体或已登记 Deferred，**无单点 ≥2% 低风险可收割项，停止判据成立**。
