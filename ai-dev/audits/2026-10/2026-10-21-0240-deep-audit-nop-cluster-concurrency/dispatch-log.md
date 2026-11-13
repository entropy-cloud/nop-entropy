# Dispatch Log — CRA WI 4.2 nop-cluster 并发审计

- **Plan**: `ai-dev/plans/2478-cra-wi4.2-nop-cluster.md`（active；R1 agent_9469e2a9 2 Major[hazelcast 嵌套双根/CL-024 同族证伪]+4 Minor 全修；R2 agent_889a002b 1 Major[⑧口径与附录 C C2 基线矛盾]+1 Minor[源码根 6→7]全修）
- **基线**: commit 120ff53d0d，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-cluster.txt`（15 命令组 × 7 源码根 = 428 命中行；C2b 节注记 hazelcast-cache computeIfAbsent×3 与 CachingNamingService:37 为委派面非自管原语）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`（含本单元目录）；仅 Phase 4 复核允许读历史审计与 cra-index 台账
- **串行约束**: 运营者要求——全部子代理严格串行派发，无并行批次
- **跨单元移交落点**: WI 2.1 CL-024（per-client/topic 池线性累计→nop-cluster 移交）——**R1/R2 双实测：rpc-cluster 无同族形态**（零自建池，globalTimer 共享 2 站点 + 每调用 new 包装对象）→ Phase 4 按漂移复查/无同族预期闭环

## 源码根实测（Phase 1 回填，手写口径不含 `_` 前缀与 target/）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-cluster/nop-cluster-core/src/main/java | 54f/3,807 | naming/AutoRegistration + CachingNamingService（LocalCache）+ lb/impl 9 类 + chooser 3 + filter 5 + AbstractLeaderElector（并发原语 11 全模块最高） |
| nop-cluster/nop-rpc-cluster/src/main/java | 9f/646 | ClusterRpcClient/ClusterRpcServiceInvoker/BroadcastRpcClient/ClusterRpcProxyFactoryBean；零自建池 |
| nop-cluster/nop-cluster-nacos/src/main/java | 4f/564 | NacosNamingService（8 命中）+ ServiceInfo（3） |
| nop-cluster/nop-cluster-sentinel/src/main/java | 2f/299 | 自管原语 0——SDK 适配面薄 |
| nop-cluster/nop-hazelcast/nop-hazelcast-core/src/main/java | 1f/50 | HazelcastProvider |
| nop-cluster/nop-hazelcast/nop-hazelcast-cache/src/main/java | 1f/187 | HazelcastCache（computeIfAbsent×3 委派面非自管） |
| nop-cluster/nop-cluster-admin/src/main/java | 3f/24 | 近零面 |
| **合计** | **74f/5,577**（6 子模块族 7 源码根） | `_gen/` 残留 0 |

## 预注锚点核实（Phase 1，R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| 7 源码根规模 | 逐一吻合 | 计划一致 |
| rpc-cluster 池形态 | 零 Executors/ExecutorService/new Thread；globalTimer :43/:68 | CL-024 无同族——漂移复查 |
| AbstractLeaderElector | volatile leaderEpoch:32/electionPromise:33/CopyOnWriteArrayList:43/synchronized:165/173 | C1/C2 首要站点 |
| NacosNamingService | volatile:43/44、CHM:46、synchronized:67/82/230/238、computeIfAbsent:175、subscribe:182-196 | C1/C2/C3 站点 |
| sentinel/hazelcast/admin 自管原语 | 0（附录 C C2 基线 flagged 委派面 4 处单列） | 适配面薄预校准 |

## owner doc 漂移登记

- `docs-for-ai/01-repo-map/module-groups.md`：对 nop-cluster/nop-rpc-cluster/nop-hazelcast **0 提及**——无模块级并发契约；**漂移登记：owner doc 零覆盖**，收口按 No owner-doc update / 补行显式裁定；契约以 javadoc 为准。

## 历史审计对照口径

- `ai-dev/audits/` 下 nop-cluster **并发主题**直接审计 0 命中。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-21，5 批次串行）

| 批次 | 编号 | 结果 |
|------|------|------|
| C1 | C1-001…008；CL-001…004 | **P1×4**（Nacos 事件线程原地改写 ServiceInstance/选主 doStop 复活竞态/订阅旧快照无版本守护/cleanupTasks 读侧丢任务）+P2×4 |
| C2 | C2-001…004；CL-005/006 | **P1×1**（cleanupTasks 无锁迭代违反门禁先例）+P2×2（computeIfAbsent 锁内远程调用/选主写站点锁覆盖）+P3×1 |
| C3 | C3-001…011；CL-007…011 | **P1×2**（callAsync 重试异常吞没挂死+Sentinel entry 泄漏复合/广播半个成功）+P2×5+P3×4 |
| C4 | C4-001…004；CL-012…015 | **P1×1**（选主轮询回路 stop 后复活——LifeCycleSupport allowRestart 窗口）+P2×3（globalTimer 裸交给 PollingRpcClient/自续链单点断链/BindScheduledExecutor 死守卫） |
| C8 | C8-001…010；CL-016/017 | P2×8（LeastActive 活跃计数硬编码 0/重试零退避风暴/Nacos 缓存无界+订阅永不退订/Sentinel 规则棘轮+runAsync 漏计熔断等）+P3×2 |
| **Phase 2 合计** | 37 条 + CL×17 | **P0×0/P1×8/P2×22/P3×7** |

- 继续条件判定：4 维度含 P1 + C8（P2×8 ≥3）→ **5 维度全部派第 2 轮深挖**（合并批）

### Phase 3 深挖（2026-10-21）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C2/C3/C4/C8 | +6（全 P2×1/P3×5）：C1-009/010、C2-005、C3-012、C4-005、C8-011 + 扩展并入 7 处；CL-018/019/020/021 | **5 维度全部显式终止**（无 P0/P1 新增） |

- **深挖后累计 43 条**：C1×10（P1×4/P2×4/P3×2）、C2×5（P1×1/P2×3/P3×1）、C3×12（P1×2/P2×5/P3×5）、C4×5（P1×1/P2×3/P3×1）、C8×11（P2×9/P3×2）→ P0×0/**P1×8**/P2×23/P3×12；CL×21

### Phase 4 独立复核（2026-10-21，5 维度 2 派发串行）

| 批次 | 判定 | 关键裁定 |
|------|------|---------|
| C1+C2+C3 | 27/27 保留（降 3、并入 1） | **P1×4 维持**（C1-001/C1-003/C1-004/C3-001——C3-001 live 默认装配路径证实）；降档全附 grep 证据（addCleanupTask 零生产调用/BroadcastRpcProxyFactoryBean 零接线/CachingNamingService 零装配）；CL-024 漂移复查闭环成立 |
| C4+C8 | 16/16 保留（降 2、驳回并入 4） | C4-001 P1→P2 同案双轨并入 C1-004 主登记（五子项逐环回码成立）；C8 驳回 4 全为同案并入（本体成立）；C8-003 休眠条件化携回升条款（修正「缺省装配」表述） |

- **复核后最终保留 43 条：P0×0/P1×4/P2×23/P3×12**（并入/移交 6 条计入主登记）；新发现×43
- 主登记建议网：C1-004（选主 stop 复活+调度句柄回归面）、C1-002=C2-001（同根同判）、C3-001（callAsync 族）、C8-005 三面拆分（cleanupTasks→C3-006、订阅泄漏→C3-005）

### Phase 5 C10 测试保护力（2026-10-21）

- 保护力矩阵 43 行：**会红 0 / 假保护 6 / 无测试 37**（P1×4 全部无保护）；缺保护登记 C10-001…014（复核并入执行后 P2×4/P3×10）
- 反向钉死 4 处（TestSentinelFlowControlRunner assertSame 硬钉/TestWeightedPartitionAssigner 权重语义钉死/TestLoadBalance 结构性假保护/TestAutoRegistration 弱守卫）
- 独立复核：0 对账差异、13 行回测零误判、14 登记全保留、1 覆盖缺口处置（C1-003 并入 C10-005 升 P2）+4 标注级勘误

### Phase 6 合并裁定（2026-10-21）

- 独立裁定子代理串行派发一次；输入 = 43 条保留发现（五维 `## 最终保留项`/`## 维度复核结论`）+ C10 矩阵（含复核覆盖处置）+ cra-index WI 1.1–4.1 台账 133 行
- 输出 = merge-adjudication.md：43 → 22 工单 + watch 1（9 族 MA-01~09 覆盖 29 = 主 9+吸收 20 + 13 独立），P0×0/P1×4/P2×13/P3×5；冲突裁定 4 处；7.1 移交 6 项；CL 21 全落位（7/4/7/3，新增发现 CL-007=CL-001、CL-016=CL-002 两组复核遗漏重复）；C10 14 全映射 + C1-003 并入 C10-005 升 P2 处置落位
- summary.md 由主 agent 按附录 B 撰写；cra-index 登记（报告行/4 P1 行/CL 4.2 小节；P1 累计 133→137）

### Phase 7 P0 即时通道（2026-10-21）

- P0=0 显式空执行；No owner-doc update required（owner doc 零覆盖——补行与否按 7.1 owner 裁决）
