# Dispatch Log — CRA WI 4.1 nop-message 并发审计

- **Plan**: `ai-dev/plans/2477-cra-wi4.1-nop-message.md`（active；R1 agent_fbd52cf5 1 Major[CL-022 落点错列 debezium]+3 Minor 全修；R2 agent_38d1a7d4 0 Blocker/0 Major）
- **基线**: commit 1407fc426e，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-message.txt`（17 命令组 × 4 源码根 = 536 命中行；C3a 节 orTimeout 计数口径：代码调用点 vs grep 行含 javadoc）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`（含本单元目录）；仅 Phase 4 复核允许读历史审计与 cra-index 台账
- **串行约束**: 运营者要求——全部子代理严格串行派发，无并行批次
- **跨单元移交落点（本单元为多前序单元落点）**: WI 2.3 CL-022 orTimeout（kafka:313/pulsar:273 两站点）；WI 2.5 C3-008/CL-010 REGISTRY 本体（debezium NopStreamOffsetBackingStore:59；clearConnector:101-102 本族零调用，唯一调用点 nop-stream DebeziumCdcSourceFunction:442——跨模块清除路径须覆盖）

## 源码根实测（Phase 1 回填，手写口径不含 `_` 前缀与 target/）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-message/nop-message-core/src/main/java | 7f/723 | LocalMessageService（ConcurrentHashMap/ScheduledExecutor/volatile）/MessageConsumeFailureBridge/ChannelBacklogProbe/MessageMetrics/ReflectionMessageSubscriptionRegistrar |
| nop-message/nop-message-kafka/src/main/java | 7f/1,955 | KafkaMessageService（orTimeout:313）/KafkaConsumeTask（并发原语 13 命中）/三配置/KafkaHelper |
| nop-message/nop-message-pulsar/src/main/java | 7f/1,513 | PulsarMessageService（orTimeout:273）/PulsarConsumeTask（10 命中）/三配置/PulsarHelper |
| nop-message/nop-message-debezium/src/main/java | 10f/1,949 | DebeziumEngineWrapper（14）/DebeziumMessageSource（6）/NopStreamOffsetBackingStore（13，REGISTRY:59）/EventConverter/ChangeEvent/Config |
| **合计** | **31f/6,140** | codec 零 java 源登记零审计面；`_gen/` 残留 0；无嵌套聚合器 |

## 预注锚点核实（Phase 1，R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| 4 子模块规模 | 逐字节吻合 | 计划一致 |
| orTimeout 站点 | kafka:313 + pulsar:273；debezium 0 | CL-022 落点精确 |
| REGISTRY/clearConnector | debezium:59 定义；clearConnector 本族零调用，nop-stream:442 唯一 | 跨模块清除路径入 C5/C6 审查 |
| IMessageService 契约 | LocalMessageService(core:34)/DBMessageService(agent:79) 同实现 | WI 3.1 MA-11 同契约异实现对照 |
| 并发原语 | 8 类命中（LocalMessageService/DebeziumMessageSource/NopStreamOffsetBackingStore/DebeziumEngineWrapper/KafkaMessageService/KafkaConsumeTask/PulsarMessageService/PulsarConsumeTask） | C1/C3/C8 重点 |

## owner doc 漂移登记

- `docs-for-ai/03-modules/nop-message.md`：core/kafka/pulsar/debezium 四子模块全部在册——Phase 1 粒度**无漂移**。

## 历史审计对照口径

- `ai-dev/audits/` 下 nop-message **并发主题**直接审计 0 命中（质量域目录 `2026-05-20-adversarial-review-nop-message-pulsar/` 预登记不计命中；`check/nosql-cdc.md` 2026-08-19 cdc 前案 Phase 4 对照）。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-20，5 批次串行）

| 批次 | 编号 | 结果 |
|------|------|------|
| C1 | C1-001…011；CL-001…004 | **P1×1**（FAILURE_LISTENERS 复合协议竞态——同名 connector 交叠重启静默死亡）+P2×6（REGISTRY 本体/双后端 destroy 守卫失效族等）+P3×4 |
| C3 | C3-001…008；CL-005…008 | **P1×1**（Debezium 回调无异常屏障——畸形事件 CDC 流终态死亡）+P2×5（ack 链无上界等待/F-39 Pulsar 兄弟未清扫等）+P3×2；CL-022 落点处置→C3-007 P3 残留 |
| C5 | C5-001…004；CL-009…013 | P2×2（REGISTRY 生命周期演化/Local 重投无界 DelayedWorkQueue）+P3×2 |
| C6 | C6-001…006；CL-014…018 | **P1×1**（DebeziumMessageSource 先 put 后启动幽灵订阅）+P2×4（Local 调度器只建不关/重复订阅注销键/F-15 同族未修等）+P3×1 |
| C8 | C8-001…008；CL-019…024 | **P1×3**（ack 链无超时阻塞等待三后端/Local 消费失败静默丢弃三层失败架构断链/Debezium dispatchEvent 吞异常推进 offset）+P2×4+P3×1 |
| **Phase 2 合计** | 37 条 + CL×24 | **P0×0/P1×6/P2×21/P3×10** |

- 继续条件判定：4 维度含 P1 + C5（P2×2，全 P2/P3 且将给终止信号判定）→ **5 维度全部派第 2 轮深挖**（合并批）

### Phase 3 深挖（2026-10-20）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C3/C5/C6/C8 | +7（全 P2×3/P3×4）：C1-012、C3-009/010、C5-005/006、C6-007、C8-009 + 扩展 6 处；CL-025/026/027 | **5 维度全部显式终止**（无 P0/P1 新增） |

- **深挖后累计 44 条**：C1×12（P1×1/P2×7/P3×4）、C3×10（P1×1/P2×6/P3×3）、C5×6（P2×2/P3×4）、C6×7（P1×1/P2×4/P3×2）、C8×9（P1×3/P2×5/P3×1）→ P0×0/**P1×6**/P2×24/P3×14；CL×27

### Phase 4 独立复核（2026-10-20，5 维度 2 派发串行）

| 批次 | 判定 | 最终严重度 | 关键裁定 |
|------|------|-----------|---------|
| C1+C3 | C1 保留 11+并入 1（降 1）/C3 保留 10 | C1 P1×0/P2×8/P3×4；C3 P1×1/P2×6/P3×3 | C1-001 与 WI 1.2 C3-006 同案双轨降档（P1↔P2 分歧留 7.1）；REGISTRY 三面分层+WI 2.5 消费面表态成立；CL-022 落点闭环（C3-007 P3） |
| C5+C6+C8 | 22/22 全保留（C8 降 1） | C5 P2×2/P3×4；C6 P1×1/P2×4/P3×2；C8 P1×2/P2×5/P3×1 | C8-001 并入 C3-001（P1↔P2 留 7.1）；C6-002/C8-004 P1 维持（生产主路径结构性免疫、嵌入方受损面判级口径同 C3-002 登记 7.1）；C8-002 Local 为生产路径（SysDao/nop_sys_event）无豁免可援引 |

- **复核后最终保留 44 条：P0×0/P1×4/P2×25/P3×15**；新发现×44
- 主登记建议网：C3-001（getResult 无上界同缺陷主登记）、C3-002↔C8-004（同屏障两极性同 diff）、C6-005（Debezium 一次性状态机吸收 C1-011）、REGISTRY 同 diff 族（C5-001+C6-004）+ stop-clear vs TTL 对契约相反影响须一次裁决

### Phase 5 C10 测试保护力（2026-10-20）

- 保护力矩阵 44 行：**会红 0 / 假保护 23 / 无测试 21**（P1×4 全部无保护）；缺保护登记 C10-001…012（P2×9/P3×3）
- 反向钉死 5 处（4 硬 + 1 软）——修复 diff 必须先改测试
- 独立复核：0 对账差异、17 行回测零误判、4 证据精度修正、1 覆盖缺口处置（C3-004/C3-005/C5-006 挂靠闭合）、TestPulsarServiceLifecycle 行号系统性过期订正

### Phase 6 合并裁定（2026-10-20）

- 独立裁定子代理串行派发一次；输入 = 44 条保留发现（五维 `## 最终保留项`/`## 维度复核结论`）+ C10 矩阵（含覆盖闭合处置）+ cra-index WI 1.1–3.4 台账 130 行
- 输出 = merge-adjudication.md：44 → 28 工单（10 族 MA-01~10 吸收 26 + 18 独立 + watch 0 显式空），P0×0/P1×3/P2×15/P3×10；显式裁定 8 处（复核确认 5 + 新增归并 3）；跨单元关联 8 处零重复；CL 27 全落位（18/4/5/0）；C10 12 全映射 + 3 行挂靠落定；判级分歧 4 + 设计裁决 4 移交 7.1
- summary.md 由主 agent 按附录 B 撰写；cra-index 登记（报告行/3 P1 行/CL 4.1 小节；P1 累计 130→133）

### Phase 7 P0 即时通道（2026-10-20）

- P0=0 显式空执行；No owner-doc update required
