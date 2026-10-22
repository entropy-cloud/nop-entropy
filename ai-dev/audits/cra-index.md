# CRA 审计-修复报告索引（concurrency-resource-audit）

> Campaign：全模块并发·异步·资源专项审计与修复（C1–C10 口径首次全模块滚动）
> Roadmap：`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md`
> 启动时间：2026-10-14
> 绿色基线 commit：b3a9c76bed（`./mvnw clean install -T 1C` BUILD SUCCESS，2026-10-14）

本索引是 CRA campaign 全部审计报告与发现台账的唯一入口，是 roadmap 9.1 对账的输入。纪律：报告产出即更新本索引；修复完成即回填；P0 走即时通道（发现即修，不入批量修复）；P2 逐项裁决后回填。

## 报告清单

| 报告目录 | 工作项 | 审计单元 | 维度 | P0 数 | P1 数 | P2 数 | P3 数 | 状态 |
|---------|--------|---------|------|-------|-------|-------|-------|------|
| `2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency` | 1.1 | nop-stream-core-runtime | C1–C5 C7–C10 | 0 | 10 | 27+25（含 C10 缺保护登记） | 24 | done（closure audit PASS，plan 2461） |
| `2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency` | 1.2 | nop-stream-state-connectors | C1–C8 C10 | 1 | 10 | 26+16（含 C10 缺保护登记） | 9 | done（closure audit PASS，plan 2462） |
| `2026-10/2026-10-14-1440-deep-audit-nop-stream-orchestration-thin-concurrency` | 1.3 | nop-stream-orchestration-thin | C1 C3 C6 全维 + C2/C4/C5/C7/C8/C9 抽查 + C10 | 0 | 4 | 10+8（含 C10 缺保护登记） | 6 | done（closure audit PASS，plan 2463） |
| `2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency` | 1.4 | nop-job | C1 C2 C4 C5 C7 + C10 | 0 | 2 | 23+9（含 C10 缺保护登记） | 9 | done（closure audit PASS，plan 2464） |
| `2026-10/2026-10-15-0930-deep-audit-nop-wf-nop-task-concurrency` | 1.5 | nop-wf-nop-task | C1–C5 C7 C8 + C10 | 0 | 7 | 29+14（含 C10 缺保护登记） | 18 | done（closure audit PASS，plan 2465） |
| `2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency` | 1.6 | nop-retry-nop-tcc | C1 C3 C6 C7 C8 + C10（C2/C4/C5/C9 线索 overlay） | 0 | 14 | 22+10（含 C10 缺保护登记） | 14 | done（closure audit PASS，plan 2466） |
| `2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency` | 1.7 | nop-batch | C1 C5 C6 C7 C8 + C10（C2/C3/C4/C9 线索 overlay） | 0 | 8 | 26+12（含 C10 缺保护登记） | 17 | done（closure audit PASS，plan 2467） |

> 1.5 计数口径：合并裁定后 54 个工作项（23 缺陷族 MA-01~23 + 31 独立）；P0=0（即时通道显式空执行）；C10 缺保护 14 条单列；与 WI 1.1–1.4 台账零重复立项，跨单元关联 3 处单列（nop-job-C7-006、2026-09 P2-3.1-001、AU-10 同案对齐）。

> 1.6 计数口径：合并裁定后 50 条（14 并组吸收 24 条 + 独立条目；P1 19→14、P2 38→22、P3 17→14）；P0=0（即时通道显式空执行）；C10 缺保护 10 条单列；维度集外线索 CL-001~007 全部确认（见「维度集外线索关联」节，不计入 74）；与 WI 1.1–1.5 台账零重复立项，跨单元关联 2 处单列（C8-001↔nop-job C4-003、CL-006 留 rpc/资源单元）。

> 1.7 计数口径：合并裁定后 51 条（53 保留全含 1 降级；并入 2 条 C5-005→C8-001、C7-002→C6-001/C6-002；P1×8/P2×26/P3×17）；P0=0（即时通道显式空执行）；C10 缺保护 12 条单列；CL 编号 39 条、驳回 4（CL-007/009/015/038）、确认台账 35 条（见「维度集外线索关联」节 nop-batch 小节，不计入 51）；关键事实修正：C8-001 ORM 子面「LIMIT 0 静默空任务」证伪→全表加载挂死（挂死面扩大）；与 WI 1.1–1.6 台账零重复立项，跨单元关联 5 处单列（三表零保留族/扫描守卫停摆族/平台池占用族/零生产装配对照/CL-006 留 rpc 单元）。
| `2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency` | 2.1 | nop-kernel-concurrency | C1–C7 C9 + C10（C8 线索 overlay） | 0 | 5 | 63+14（含 C10 缺保护登记） | 28 | done（closure audit PASS，plan 2468） |
| `2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency` | 2.2 | nop-core-framework | C1 C2 C4 C6 + C10（C3/C5/C7/C8/C9 线索 overlay） | 0 | 11 | 21+18（含 C10 缺保护登记） | 13 | done（closure audit PASS，plan 2469） |
| `2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency` | 2.3 | nop-service-framework-hotpath | C1 C2 C3 C4 C8 C9 + C10（C5/C6/C7 线索 overlay） | 0 | 7 | 39+17（含 C10 缺保护登记） | 12 | done（closure audit PASS，plan 2470） |
| `2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency` | 2.4 | nop-persistence-rw | C1 C2 C3 C4 C5 C6 C9 + C10（C7/C8 线索 overlay） | 0 | 13 | 31+17（含 C10 缺保护登记） | 23 | done（closure audit PASS，plan 2471） |
| `2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency` | 2.5 | nop-persistence-peri | C1 C3 C6 C7 C8 + C10（C2/C4/C5/C9 线索 overlay） | 1 | 13 | 20+39（含 C10 缺保护登记） | 1 | done（closure audit PASS，plan 2472） |
| `2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency` | 3.1 | nop-ai-core-nop-ai-agent | C1 C3 C4 C5 C7 + C10（C2/C6/C8/C9 线索 overlay） | 0 | 13 | 29+19（含 C10 缺保护登记） | 8 | done（closure audit PASS，plan 2473） |
| `2026-10/2026-10-20-0829-deep-audit-nop-ai-toolkit-tools-shell-concurrency` | 3.2 | nop-ai-toolkit-tools-shell | C1 C4 C6 C8 + C10（C2/C3/C5/C7/C9 线索 overlay） | 0 | 5 | 19+23（含 C10 缺保护登记） | 1 | done（closure audit PASS，plan 2474） |
| `2026-10/2026-10-20-1302-deep-audit-nop-ai-gateway-concurrency` | 3.3 | nop-ai-gateway | C1 C2 C3 C4 C6 C7 C8 + C10（C5/C9 线索 overlay） | 0 | 4 | 14+18（含 C10 缺保护登记） | 16 | done（closure audit PASS，plan 2475） |
| `2026-10/2026-10-20-1908-deep-audit-nop-ai-rest-concurrency` | 3.4 | nop-ai-rest | C1 C6 + rag C3 + C10（C2/C4/C5/C7/C8/C9 线索 overlay） | 0 | 4 | 6+8（含 C10 缺保护登记） | 5 | done（closure audit PASS，plan 2476） |
| `2026-10/2026-10-20-2210-deep-audit-nop-message-concurrency` | 4.1 | nop-message | C1 C3 C5 C6 C8 + C10（C2/C4/C7/C9 线索 overlay） | 0 | 3 | 15+12（含 C10 缺保护登记） | 10 | done（closure audit PASS，plan 2477） |
| `2026-10/2026-10-21-0240-deep-audit-nop-cluster-concurrency` | 4.2 | nop-cluster | C1 C2 C3 C4 C8 + C10（C5/C6/C7/C9 线索 overlay） | 0 | 4 | 13+14（含 C10 缺保护登记，复核并入执行后） | 5 | done（closure audit PASS，plan 2478） |
| `2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency` | 4.3 | nop-network-integration | C1 C3 C6 + C10（C2/C4/C5/C7/C8/C9 线索 overlay） | 1 | 15 | 32+15（含 C10 缺保护登记） | 10 | done（closure audit PASS，plan 2479） |
| `2026-10/2026-10-21-1223-deep-audit-nop-host-concurrency` | 4.4 | nop-host | C1 C4 C6 + C10（C2/C3/C5/C7/C8/C9 线索 overlay） | 0 | 4 | 10+19（含 C10 缺保护登记） | 2 | done（closure audit PASS，plan 2480） |
| `2026-10/2026-10-21-1444-deep-audit-nop-search-runner-autotest-concurrency` | 4.5 | nop-search-runner-autotest | C1 C4 C6 + C10（C2/C3/C5/C7/C8/C9 线索 overlay） | 0 | 1 | 6+9（含 C10 缺保护登记） | 7 | done（closure audit PASS，plan 2481） |
| `2026-10/2026-10-21-1925-deep-audit-nop-auth-credential-concurrency` | 5.1 | nop-auth-credential | C1 C2 C6 + C10（C3/C4/C5/C7/C8/C9 线索 overlay） | 0 | 2 | 5+7（含 C10 缺保护登记） | 7 | done（closure audit PASS，plan 2482） |
| `2026-10/2026-10-21-2148-deep-audit-nop-sys-dyn-concurrency` | 5.2 | nop-sys-dyn | C1 C2 C6 C8 + C10（C3/C4/C5/C7/C9 线索 overlay） | 0 | 3 | 8+6（含 C10 缺保护登记） | 6 | done（closure audit PASS，plan 2483） |
| `2026-10/2026-10-21-1227-deep-audit-nop-file-datav-concurrency` | 5.3 | nop-file-datav | C1 C5 C6 + C10（C2/C3/C4/C7/C8/C9 线索 overlay） | 0 | 2 | 13+5（含 C10 缺保护登记） | 10 | done（closure audit PASS，plan 2484） |
| `2026-10/2026-10-21-1326-deep-audit-nop-report-format-concurrency` | 5.4 | nop-report-format | C1 C5 C6 C9 + C10（C2/C3/C4/C7/C8 线索 overlay） | 0 | 1 | 9+25（含 C10 缺保护登记） | 15 | done（closure audit PASS with corrections，plan 2485） |
| `2026-10/2026-10-22-1201-deep-audit-nop-rule-metadata-concurrency` | 5.5 | nop-rule-metadata | C1 C4 C5 C7 + C10（C2/C3/C6/C8/C9 线索 overlay） | 0 | 3 | 9+38（含 C10 缺保护登记；工单级 P3×17/实体级 P2×13+P3×21 见计数口径） | 17 | done（closure audit PASS with corrections，plan 2486） |
| `2026-10/2026-10-22-1300-deep-audit-nop-graph-duckdb-concurrency` | 5.6 | nop-graph-duckdb | C1 C6 + 抽查（C2/C3/C4/C5/C7/C8/C9 七形态 CS-sweep） + C10 | 0 | 4 | 4+4+12（含 C10 缺保护登记） | 4 | done（closure audit PASS，plan 2487） |
| `2026-10/2026-10-22-1400-deep-audit-nop-tools-parallel-concurrency` | 6.1 | nop-tools-parallel | C1 C4 C6 C9 + C10（C2/C3/C5/C7/C8 仅 CL 线索） | 0 | 0 | 4+10（含 C10 缺保护登记；工单级 P3×13） | 13 | done（closure audit PASS with corrections，plan 2488） |

> 2.4 计数口径：合并裁定后 67 条（71 初审 + 3 深挖 = 74 复核保留全含 1 降级（C2-014）→ 维度内并入 7；P1 台账 17→有效工单 13、P2×31、P3×23）；P0=0（即时通道显式空执行）；C10 缺保护 17 条单列；CL 33 条全部确认（承接 8/收敛 5/独立 10/watch 6/移交 4——CL-700→nop-commons）；**结构性发现：nop-orm/nop-dao 事务设施 JVM 级锁为 0，并发正确性全押 session/事务同 context 单线程契约，平台语义多处破口**；跨单元映射 7 行（X1 与 2.3 C1-010 同号异单元互补、X4 CL-401↔2.2 CL-018 同站点、X7 对 2.1 零重复）；对 WI 2.1 仅误报校准引用。

> 2.5 计数口径：合并裁定后 35 张工单（54 复核保留全含 1 降级（C7-005 P1→P2 零生产实例化）→ 12 缺陷族 MA-01~12 吸收 17 条覆盖 29 条 + 23 独立 + watch 1（C8-006）+ 跨单元随案 1（C7-012→WI 1.2 MA-05）；P0×1/P1×13/P2×20/P3×1——MA-02 开放裁定暂计 P1 留 7.1 终裁）；**P0×1 = C6-002 成功路径不停链（本 campaign 第 2 个 P0，即时通道）**；C10 缺保护 39 条单列（矩阵会红 0/假保护 30/无测试 24）；CL 26 条全部落位（并入工单 16/独立线索 10/驳回 0）；nop-cdc 零审计面（空接口占位）与 drivers 三纯 pom 显式登记；结构性发现：nop-orm-migration 运行期多线程流水线对并发零防护（五状态机零自环/零单飞/裸线程），标识符派生键族错位（C3-008/009/010/011/012 五站点同根）；跨单元台账级同案仅 2 处（C7-012 随案、C6-005 双轨）。

> 3.1 计数口径：合并裁定后 50 张工单（129 初审+深挖 → 128 复核保留全含 3 降级（C1-005/C5-022 opt-in 装配面、C7-013 收敛）+ 1 驳回（C5-019 初审 grep 失误证伪——releaseSession 实有 4 出口接线）→ 21 缺陷族 MA-01~21 吸收 97 + 29 独立 + watch 2；P0×0 显式空执行/**P1×13**/P2×29/P3×8）；C10 缺保护 19 条单列（矩阵会红 0/假保护 103/无测试 25；P1×30 = 假保护 23+无测试 7；反向钉死 4 项——gate3 测试字面钉 remove no-op）；CL 77 条全部落位（并入工单 46/独立线索 29/watch 2/驳回 0）；**MA-07（周期任务 Error 逃逸 6 条）单列移交 WI 2.1 C3-001 平台绝缘层**，C3-009/012 为 WI 2.1 CL-021 移交面具体化站点，C7-012↔WI 1.6 fencing 族家族关联——零重复立项；结构性发现：agent 执行链对并发/异步零防护（流式主链五 P1 轴保护力为零、方言流式协议 vs 非流式系统性分叉、超时取消契约 5 路径仅 2 实现、F4 终态清理族 22+ 站点）；C3 R3 口径偏差记录在案（矩阵穷举闭合裁定终止，供 closure audit 裁定）。

> 3.2 计数口径：合并裁定后 25 张工单（39 初审+深挖 → 39 复核保留全含 6 降档（shell 零生产接线族条件化：C1-002/C6-004/005/C8-001/002/003——投产即回升）+ 1 升档（C4-001 并案校准 P2→P1）→ 8 缺陷族 MA-01~08 吸收 22 + 17 独立 + watch 0；P0×0 显式空执行/**P1×5**/P2×19/P3×1——MA-06 判级分歧暂计 P1 留 7.1）；C10 缺保护 23 条单列（矩阵会红 0/假保护 21/无测试 18；**9/9 P1 全部无测试**；反向钉死 ShellIOTest:294-300 队满 close）；CL 20 条全部落位（并入工单 16/独立线索 4/驳回 0）；**结构性发现：工具执行链故障注入维度整体缺失（9/9 P1 零保护的结构性成因）、shell 1024 设计参数在测试树零出现、修实例不修类别 F3 残留（Host killTree 已修 Docker 未修）**；跨单元：与 WI 1.1–3.1 台账零同站点重复立项，沙箱族跨单元判级分歧（3.1 MA-17 P2 vs 本单元 P1 两套实现同病）与 shell 族 P1/P2 分叉移交 7.1。

> 3.3 计数口径：合并裁定后 34 张工单（48 初审+深挖 → 48 复核保留全含 23 降档（**渠道栈休眠条件化潮**：startAll/stopAll/dispatchInbound 全仓非测试零调用、beans.xml 无 init/destroy——C4 全部 P1 条件化挂起、C6-004 链主 P1→P2 等，13 张条件化工单携激活回升条款回升 P1×6/P2×7）→ 7 缺陷族 MA-01~07 覆盖 21 + 27 独立 + watch 0；P0×0 显式空执行/**P1×4**（全部为 FailoverStreamFlow 流式 live 面：C1-001 窗口清理竞态/C2-001 并发计数泄漏族/C3-001 终态冲刷截断/C8-001 越窗无界缓冲）/P2×14/P3×16）；C10 缺保护 18 条单列（矩阵会红 0/假保护 2/无测试 46——demand==0 分支全树零执行，FailoverTestSupport 恒 request(MAX)）；CL 23 条全落位（并入工单 10/独立线索 10/驳回 1/重复关闭 1[CL-023↔WI 2.3 C3-003]/纯移交 1[CL-009→2.1 CL-021]）；**结构性发现：FailoverStreamFlow 是 BufferedStreamingPublisher 双实现分叉中未被测试钉住的那一半（pendingComplete 正确先例在同仓）、渠道栈结构性休眠**；跨单元零同站点重复，orTimeout 站点→WI 2.3 CL-022 Delayer 移交轮。

> 3.4 计数口径：合并裁定后 15 张工单 + watch 1（19 初审+深挖 → 19 复核保留全含 1 升格（C3-005 synthesize 静默折叠错误响应 P2→P1——错误以成功交付族三步可达性闭合）+ 吸收 2（PgVectorStore.schemaReady 三登记 C1-002/C3-004→C6-001 主登记）→ 2 缺陷族 MA-01~02 覆盖 5 + 13 独立 + watch 1（C3-002）；P0×0 显式空执行/**P1×4**/P2×6/P3×5；4 张条件化 P2 裁决留 7.1）；C10 缺保护 8 条单列（矩阵会红 0/假保护 1/无测试 16；反向钉死 intern assertSame——修复 C1-008 必红）；CL 16 条全落位（并入工单 4/重复合并 3/独立移交 9/驳回 0）；**结构性发现：nop-ai 四单元会红=0 系统性形态（测试基建立即排除缺陷触发条件——fresh-instance×单集合×恒成功 stub 三联）、code-analyzer 零外部消费者（parallelStream 面内生于方法体不适用单线程降档）**； Protected Area：nop-ai-api 入圈（plan-first）但 0 张工单触碰；跨单元零同站点重复，错误以成功交付族/取消契约族家族关联单列。**R1 教训：rag 异步前提失真（零 CompletableFuture、async 委托零调用方）——起草预注必须 grep 实测，不得按 owner doc 语义推断**。

> 4.1 计数口径：合并裁定后 28 张工单（44 初审+深挖 → 44 复核保留全含 2 降档（C1-001 与 WI 1.2 C3-006 同案双轨 P1→P2——FAILURE_LISTENERS 使用面/本体面互补；C8-001 P1→P2 并入 C3-001）→ 10 缺陷族 MA-01~10 吸收 26 + 18 独立 + watch 0 显式空；P0×0 显式空执行/**P1×3**（MA-02 Debezium 屏障两极性同 diff 配对/MA-05 幽灵订阅/C8-002 Local 失败断链——后两类生产主路径结构性免疫标注、嵌入方受损面判级分歧登记 7.1）/P2×15/P3×10）；C10 缺保护 12 条单列（矩阵会红 0/假保护 23/无测试 21；反向钉死 5 处修复 diff 须先改测试；「会红」口径文件内重定义为回退方向已登记注记）；CL 27 条全落位（并入工单 18/独立线索 4/移交 5/驳回 0）；**结构性发现：保护力与缺陷分布倒挂（DebeziumMessageSource 零测试压着 3 条 P1）、REGISTRY 三面分层 + WI 2.5 消费面双轨互斥无重复、判级三案嵌入方口径留 7.1**；跨单元关联 8 处零同站点重复（含 WI 2.3 CL-022 落点闭环、WI 1.2 debezium 同族双轨）。

> 4.2 计数口径：合并裁定后 22 张工单 + watch 1（43 初审+深挖 → 43 复核保留全含 6 降档（C1-002/C2-001 同根同判 P1→P2、C1-007/C3-002/C4-001/C4-002——全附 grep 证据）+ 驳回 4 全为同案并入（C8-004→C3-002/C8-008→C3-008/C8-009→C1-006/C8-010→C3-003）→ 9 缺陷族 MA-01~09 覆盖 29（主 9+吸收 20）+ 13 独立 + watch 1（C2-004）；P0×0 显式空执行/**P1×4**（MA-01 选主并发纪律族 C1-004 主/C1-001 Nacos 事件线程原地改写/C1-003 订阅快照回退/MA-07 重试链族 C3-001 主——live 默认装配路径）/P2×13/P3×5）；C10 缺保护 14 条单列（矩阵会红 0/假保护 6/无测试 37；反向钉死 4 处——2 硬钉 assertSame/权重语义；复核覆盖处置 C1-003 并入 C10-005 升 P2）；CL 21 条全落位（并入工单 7/重复合并 4/独立线索 7/watch 3；裁定新增 2 组复核遗漏跨文件重复 CL-007=CL-001、CL-016=CL-002）；**结构性发现：全模块 17 测试文件零真并发测试（修复回归双向静默）、选主测试结构性不可达（TestElector 不自续）、2 处反向钉死**；Protected Area 零触碰；**owner doc 零覆盖**（module-groups.md 对三模块 0 提及）按 drift 登记收口。**跨单元闭环：WI 2.1 CL-024 无同族形态闭环（rpc-cluster 零自建池独立复现）**；家族关联 6 处单列零同站点重复。

> 4.3 计数口径：合并裁定后 58 张工单（81 初审+深挖 → 74 复核保留全含 1 降档（C6-003 对齐 09 前案 P1→P2）+ 1 升格（C6-024 P2→P1）+ 同案并入 7 全缺陷本体成立零证伪驳回 → 17 缺陷族 MA-01~17 覆盖 32 保留成员 + 7 并入登记 + 41 独立 + watch 1（C1-020）；§六权威表脚本程序化校验 81=74+7 零缺漏；P0×1/**P1×15**/P2×32/P3×10）；C10 缺保护 15 条单列（矩阵会红 0/假保护 20/无测试 54；反向钉死 5 处——2 断言级 MQTT assertThrows 同案 + Windows 门控排除 P0 唯一确定性症状；先行失败测试 16 处）；CL 34 条全落位（并入工单 7/重复合并 10/独立线索 12/移交 5/驳回 0）；**结构性发现：测试栈结构性单线程化（仅 1 真线程测试——缺陷非漏测而是测试基建不可表达）、断言只到功能结果从未到资源与线程（P0 全绿下存活必然）、行为等价矩阵只钉死正确的一半（F3 在测试层原样复发）、证伪 3.9 前案「文件模式主路径确认关闭」误结论**；Protected Area：nop-http-api/nop-rpc-api/nop-integration-api 三叶 plan-first——0 张工单触碰 api 叶（P0 修复仅限 impl 叶）；跨单元关联 10 处零同站点重复（两大族第 4 例/消费面回写两侧同批/3.9 并轨）。

> 4.4 计数口径：合并裁定后 16 张工单 + watch 1（19 初审+深挖 → 19 复核保留全含 1 降档（C1-002 ContextTaskQueue 无池）+ 1 升格（C1-006 P2→P1 双宿主空链固化复核补全变体 B）→ 5 缺陷族 MA-01~05 覆盖 7 + 11 独立 + watch 1（C1-005 搭车 C6-004）；P0×0 显式空执行/**P1×4**（MA-01 启动窗口门禁族 C1-006 主——双宿主认证旁路不可自愈/C6-007 future 族第 5 例/C6-001 上传流校验失败 fd 泄漏/C1-001 SpringWebProxy 凭据撕裂）/P2×10/P3×2）；C10 缺保护 19 条单列（矩阵会红 0/假保护 1/部分 2/无测试 16；反向钉死 4 处——1 钉死型+3 掩蔽型）；CL 8 条全落位（并入工单 3/独立线索 5/watch 1 拆分/驳回 0）；**结构性发现：原始并发面极薄（6 文件 26 行）但测试面与发现面倒挂第三次同型确认、唯一集成级保护同时是掩蔽者与暴露者、测试替身宽于真实契约**；Protected Area：C6-009 触碰 nop-http-api 申报 plan-first；跨单元关联 8 处零同站点重复（future 族第 5 例/错误交付族传输层成员/上下文传播族宿主面）。

> 4.5 计数口径：合并裁定后 15 张工单 + watch 1（24 初审+深挖 → 24 复核保留全含 0 降档 0 驳回 → 5 缺陷族 MA-01~05 吸收 14 + 9 独立 + watch 1（C4-005）；P0×0 显式空执行/**P1×1**（C4-001 CliRunCommand scheduleWithFixedDelay Future 丢弃——周期任务异常静默停摆）/P2×6/P3×7）；C10 缺保护 9 条单列（矩阵会红 0/假保护 12/无测试 12——**全域会红=0 第四次同型确认**；反向钉死 0 硬钉 + 1 正向修复约束）；CL 12 条全落位（并入工单 2/独立线索 1/关闭确认 1/移交 8/驳回 0）；**结构性发现：全域会红=0 第四次同型确认（测试栈结构性单线程化）、测试性弃权明文化（注释明文回避 watch 主功能）、假保护双向形态**；dbtool 零主源码显式登记；Protected Area 零触碰；跨单元零同站点重复，周期停摆族/destroy-vs-rebuild 族/F4 无界账本族家族关联单列。

> 5.1 计数口径：合并裁定后 14 张工单 + watch 4（21 初审+深挖 → 19 复核保留全含 0 降档 0 驳回 → 8 缺陷族 MA-01~08 吸收 8 + 6 独立 + watch 4；P0×0 显式空执行+ask-first/**P1×2**（MA-01 六族 DB 清理配对族/MA-02 Redis TTL 附着族）/P2×5/P3×7）；C10 缺保护 7 条单列（矩阵会红 0/反向钉死 1/无测试 13——会红=0 第 5 例）；CL 17 条全落位（并入 3/独立 6/移交 8/驳回 0）；**Protected Area ask-first**（nop-auth P0 修复须先询问用户）；**结构性发现：并发面极稀疏（2 文件原语命中）但 DB 行回收配对为零（六族）**；跨单元关联 5 处零同站点重复。

> 5.2 计数口径：合并裁定后 P0×0 显式空执行/**P1×3**（MA-01 DB 行协调原语 fencing 纪律族——过期锁行删除缺 holderId 互斥校验 + 事件认领一次性租约不续期+完成写无 fencing，P1 站点×2；I-01 已发布 mergedStore 并发结构修改 TreeMap 撕裂读；I-02 订阅 cancel×subscribe 孤儿化→消息置 PROCESSED 静默丢弃）/P2×8/P3×6；C10 缺保护 15 条单列（矩阵 16 行会红=0——第 6 例 campaign 同型确认；反向钉死 1 处）；CL 编号实占 0（R1 预注周期任务族候选确认但 Error 绝缘已自备）；Protected Area：nop-sys-api/nop-dyn-api plan-first——0 张工单触碰 api 叶；跨单元家族关联 11 处零同站点重复（cra-index 台账 159 条时点）。

> 5.4 计数口径：27 编号实体（初审 20 + R2 深挖 6 + 复核补写 1[C1-006]）→ 25 复核保留（维持 25/跨维度调整 0/移交 1=C5-001 归 7.1）+ 驳回 2（C6-R2-002 use-after-close 前提证伪/C9-004 readBytesTerm 零生产调用方）+ 降级 2（C5-002/003 零生产调用方 P2→P3）；**未组族**——25 条逐条维持为独立工单，两组同站点互补对（C1-002↔C9-002、C1-003↔C9-001）附「单设计联合闭合」约束；P0×0 显式空执行/**P1×1**（C5-001 record 反序列化数据驱动长度无界物化——分配先于校验+文件/netty 双生产路径可达，同族三处有界保护反衬）/P2×9/P3×15；C10 缺保护 25 条单列（矩阵会红 0/25——会红=0 第 7 例，closure audit 裁定序数：4.5=第4/5.1=第5/5.2=第6/5.3 未记序）；CL 前缀声明零实占（四维零维度集外线索）；**结构性发现：campaign 最大单元（894f/102,861）四维重合零痕迹源码根 4 个（tablesaw/office-model/office-doc-model/record-netty，含 io.nop.record.netty 命名陷阱）+ nop-format 无 owner doc（占 84% 行数）+ 缺陷密度集中于 record 反序列化链（BinaryDataReader 契约面 5 条）**；家族关联 5 处单列（C6-001 泄漏族第 5 例/C9-002 锁内 I/O 族第 4 例/C1-002 全局注册表裸写族/C5-001 无界物化族机制新成员/C5-004=WI 1.7 C5-001 同缺陷第二实例）；跨单元查重 0 同站点重复；执行注记：Phase 2/3 配额中断后按落盘产物接续（逐批 agent 记录以恢复记录替代，覆盖核验由 Phase 4 复核完成）。

> 5.5 计数口径：合并裁定后 29 张工单（39 立案 → 38 实体[C5-106 并入] → 7 缺陷族主 + 22 独立，9 条吸收；三态：维持 29/跨维度调整 9/移交 0；P0×0 显式空执行/**P1×3**=3 独立缺陷——W-01 外部 JDBC 无超时族[C7-102 主+C7-001 次+C4-R2-001 跨维关联，判级暂计 P1、C4 侧 P2 分歧登记 7.1]/W-02 append-only 表族[C7-004 主+C7-101]/W-03 规则热更新失效族[C7-R2-001]；P2×9/P3×17 工单级——实体级 P1×4/P2×13/P3×21，降级 4[C1-101/C5-103/C5-105/C7-R3-001]）；C10 缺保护 38 条全量单列（矩阵会红 0/38——**第 8 例且形态升级：本单元有 3 个真并发测试类但保护目标全为历史修复回归面**「测试基建只向后看」；反向钉定 1[C1-002 setLoginTimeout 全局断言]）；CL 26 条全落位（26/26 确认 0 驳回）；**结构性发现：平台 job 调度骨架健壮（9 项盘点 7 项已防护）而缺陷集中外部依赖边界（无超时/无保留/无失效传播）+ 会红=0 形态升级**；Protected Area：nop-rule-api/nop-metadata-api plan-first——0 张工单触碰 api 叶；家族关联 4 组零同站点重复（超时族/F4 账本族/缓存失效传播族/Error 绝缘族）。

> 5.6 计数口径：合并裁定后 12 张工单（19 实体[初审 11+深挖 8] 复核全保留 0 驳回 0 降级 → 吸收 7 → 5 族主+7 独立；维持 12/跨维度调整 7/移交 0；P0×0 显式空执行/**P1×4**——W-01 账本泄漏族[C6-001 吸 C6-R2-001+CS-002+C1-R2-001]/W-02 竞态族[C6-002 吸 C1-002，判级终裁 P1]/W-03 CTAS 必败[C6-R2-002]/W-04 不可中断族[CS-001 吸 CS-R2-001]；P2×4/P3×4）；C10 缺保护 12 项（矩阵会红 0/19——第 9 例；duckdb 测试全顺序形态+graph 测试 0 并发断言）；CL 15/15 确认 0 驳回；**跨单元移交闭环：WI 2.1 C6-CL-031 三态裁定=边界变化并入 CS-001（awaitTermination 属负修复——CWTS 无中断检查点），2.1 台账已回填**；**结构性发现：DuckDB 单写者约束「登记面与锁面互不覆盖」+ 小单元高缺陷密度（2,932 行 P1×4）+ 外部库不可中断×调用面无守卫成族**；Protected Area：nop-graph-api plan-first——W-06（C1-003 Edge.attrs）为唯一触碰 api 叶工单（修复须 plan-first）；家族关联 3 组零同站点重复（F4 账本族/不可中断族/F2+F3 对偶族）；owner doc drift：nop-graph 无 owner doc（占 63% 行数）。

> 6.1 计数口径：**campaign 首个零 P1 单元**——合并裁定后 17 张工单（20 实体[初审 14+深挖 6] 复核全保留 0 驳回 → 吸收 3；维持 17/跨维度调整 3/移交 0；P0×0 显式空执行/**P1×0**/P2×4[W-01 patternBytes 惰性缓存族主 C1-001/W-03 FFM×外部截断 TOCTOU C1-R2-001 同机复现/W-05 invokeAll 首错弃全 C4-002/W-14 bytecode JarFile 重开 C9-002]/P3×13；降级 3[C6-001/C6-002/C9-001]）；取消族并卷 W-06（C4-R2-001 主+C6-002，附嵌入前提回升 P2 条款）；C10 缺保护 10 项（矩阵会红 0/部分 7/不会红 13——第 10 例，「部分保护」特色形态）；CL 9/9 确认 0 驳回；**结构性发现：FFM/Arena 实现质量正面（confined+TWR 主动设计零兜底依赖、SIMD 三层 fallback 一致）而缺陷集中在聚合语义与取消通道**；计数面>立案面口径（benchmark 8f/742 计入不立案）；Protected Area 零 api 叶；家族关联 4 组零同站点重复（F6 落盘族/TOCTOU 相邻族/聚合弃全对偶族/取消通道族）；owner doc drift：nop-rg/nop-find 无 owner doc（占 62% 行数）。

> 5.3 计数口径：合并裁定后 8 张修复工单（25 初审+深挖 → 25 复核保留零降级零驳回 → 7 修复工单 W-01~07 + 1 测试保护工单 W-08；P0×0 显式空执行/**P1×2**（W-01 文件去重竞态/W-04 dashboard 行上界）/P2×13/P3×10）；C10 缺保护 5 条单列（矩阵会红 0/假保护 4/部分 1/无测试 13——P1×2 全部无保护；反向钉死 1 处钉死无 fencing 语义）；Protected Area：nop-file-api 3f/492 plan-first——审计只读；跨单元关联 6 组零同站点重复。

> 2.3 计数口径：合并裁定后 59 条（64 初审+深挖 → 63 复核保留全含降级 9（P1×6 + P2→P3×3）+ 1 撤案（C2-006 转正面样例）→ 并入 4 条 + 跨单元并入 1（C9-002→2.1 C9-010）；P1 存量 12（初审 10 + 深挖 2）−6 降级（C1-001/C2-004/C3-001/005/013/C4-003 全有实测或部署面证据）+1 升格（C9-012）→ 7；P1×7/P2×39/P3×12 + 量化记录 1；本单元承担修复 58）；P0=0（即时通道显式空执行）；C10 缺保护 17 条单列；CL 编号段 25 实占 24（CL-021 空号勘误）：升格 3（CL-003→C8-005、CL-010→C9-005、CL-015→C9-012）、并入 5、驳回 1（CL-011）、纯移交（CL-016/017/022/023/025）；跨单元映射：C9-012 移交功能正确性维度（接收轨落名）、C9-002→2.1 C9-010 同链第三面、C1-010 Dao flush 修复归 nop-auth-service 单元、CL-018→nop-kernel（ContextTaskQueue 承重原语）、CL-025 平台原语前置（C3-010/C4-003 修复先决）。

> 2.2 计数口径：合并裁定后 45 条（63 初审+深挖 → 59 复核保留 + 4 驳回 → 并入 14 条；P1×11/P2×21/P3×13）；P0=0（即时通道显式空执行）；C10 缺保护 18 条单列；CL 编号 21 条（升格 1→C1-021、落格 2、同位点并入设施 2、零立案 1、关联保留 8、纯移交 7；见「维度集外线索关联」节 2.2 小节）；跨单元映射表 11 行（C1-007 族案↔2.1 C1-001 刷新链上下游、C1-021 吸收进 2.1 C3-001 平台方案、CL-008/020 同位点主处置在 2.1、C6-010 三重登记设施归 2.1、Jdk21CommonsCoreInitializer 注册链为 2.1 C6-002 可达性终审输入）；C2-001 与历史 check 审计 P2-6 同源（本轮 P1 裁定留档）。

> 2.1 移交闭环回填（2026-10-22）：C6-CL-031「移交 nop-graph 轮次」已在 WI 5.6（plan 2487）闭环——三态裁定=**边界变化**（非 daemon+无 awaitTermination 站点事实仍成立未修复；awaitTermination 属负修复，CWTS networkanalysis 1.3.0 全库零 interrupt 检查点）；并入 nop-graph-duckdb-W-04（CS-001，P1）关闭。

> 2.1 计数口径：合并裁定后 96 条（120 初审+深挖 → 118 复核保留含 7 降级 + 2 驳回 → 并入 22 条；P1 9→5、P2×63、P3×28）；P0=0（即时通道显式空执行）；C10 缺保护 14 条单列；CL 编号 36 条、驳回 1（C1-CL-001）、确认台账 35（含 11 别名归并、4 移交 nop-graph/nop-stream/rpc/api-core；见「维度集外线索关联」节 2.1 小节）；**本单元为唯一设施本体审计**——跨单元映射表 15 行（使用方↔设施两面，非重复立项）见其 merge-adjudication.md §五；1 处跨单元双登记（WI 1.7 C4-003↔2.1 C4-003 同设施同面，处置归 2.1，1.7 侧 cross-ref 关闭）；api-core 移交包 5 项单列（Protected Area 另行裁定）。

> 1.4 计数口径：合并裁定后 34 项（7 缺陷族 MA-01~07 + 27 独立）；P0=0（即时通道显式空执行）；C10 缺保护 9 条单列；与 WI 1.1–1.3 台账零重复立项，跨单元关联 3 条单列。

> 1.3 计数口径：合并裁定后 20 项（6 缺陷族 MA-01~06 + 14 独立）；P0=0（即时通道显式空执行）；C10 缺保护 8 条单列；与 WI 1.1/1.2 台账零重复立项，4 条跨单元关联单列。

## P0 发现追踪（即时通道）

> P0 永不进入 Phase 8 批量修复：发现即就地修复（审计 plan 内加修复 Phase）或异步注入独立修复 plan。

| Finding ID | 报告 | 描述 | 修复路径 | 修复状态 |
|-----------|------|------|---------|---------|
| nop-stream-state-connectors-C6-001（MA-01，成员 C3-001/C4-001/C7-002） | 1.2 | KafkaTwoPhaseCommitSink transactional producer 全终止路径（DRAIN/CANCEL/FAILED）无 close——每子任务每次作业终止泄漏 producer+网络线程+缓冲池 | plan 2462 Phase 7 就地修复：implements AutoCloseable + 幂等 close()（abort 开启事务 → producer.close(30s)），经 StreamSinkOperator.close:175 钩子全终态触发；回归测试 3 个（TestKafkaTwoPhaseCommitSinkLifecycle）；`./mvnw test -pl nop-stream -am` BUILD SUCCESS | 已修复（commit 8dcfa4cd0c） |
| nop-network-integration-C6-001 | 4.3 | JDK/OkHttp 下载成功路径从不调用 FileDownloadSink.close()（仅 catch 分支关）——resume 默认 true 下每次成功下载泄漏文件句柄（Windows Files.move 确定性失败/Unix 静默 fd 泄漏）；Apache HC5 兜底闭合跨实现分歧 | plan 2479 Phase 7 就地修复：JdkHttpClient.processDownloadResponse + OkHttpClientImpl onResponse 成功路径 verifyAndRename 后补 sink.close()；回归测试 TestFileDownloadSinkClose（fd 计数 40 次下载增量断言，回退实测必红）；两模块套件 33 测试全绿；scan-hollow --severity high 退出码 0；证伪 3.9 前案「文件模式主路径确认关闭」注记随 cra-index 4.3 计数口径 | 已修复（本轮提交） |
| nop-persistence-peri-C6-002（MA-03 主登记，成员 C3-003） | 2.5 | 搬迁成功路径（VERIFY/SWITCH/DONE）从不调用 stopAll（全模块唯一调用点在 abort，与 IncrementalJobManager javadoc 矛盾）——DONE 后链线程/binlog 连接/engine/offset store/checkpoint 执行器永久滞留；写门仅拦本 JVM ORM DML，源库域外写入经存活链 keyed upsert 持续回写已晋级目标表 | plan 2472 Phase 7 就地修复：OrmMigrationOrchestrator 新增 stopIncrementalChains（字段直读不惰性建管理器）——confirmSwitch 收敛点停链 + completeSwitch 开门前兜底幂等停链；新增 setIncrementalManager 注入 seam；回归测试 TestMigrationSwitchStopsChains 2 例（收敛停链断言回退必红 + 非 incremental 配置不动管理器）；模块套件 52 测试全绿；scan-hollow --severity high 退出码 0；owner doc data-migration.md SWITCH 节已同步 | 已修复（本轮提交） |
| （1.1/1.3/1.4/1.5/1.6/1.7/2.1/2.2/2.3/2.4/3.1/3.2/3.3/4.1/4.2/4.4/4.5/5.1 单元无 P0 发现，已按各 plan Phase 7 显式空执行记录） | | | | |

## P1 发现汇总（待 Phase 8 批量修复）

> 7.1 合并裁定后按批次展开；每条 P1 必须在 9.1 对账前变为「已修 / deferred+successor」。

| Finding ID | 报告 | 单元 | 一句话描述 | 裁定批次 | 修复状态 |
|-----------|------|------|-----------|---------|---------|
| C1-001 | 1.1 | nop-stream-core-runtime | SplitEnumerator 被任务线程无锁直调，快照撕裂/双份分配（←C2-001、C3-001） | 待 7.1 | open |
| C1-006 | 1.1 | nop-stream-core-runtime | 任务分配账本只增不删（removeTaskAssignment 零调用方；←C7-002） | 待 7.1 | open |
| C4-001 | 1.1 | nop-stream-core-runtime | LOCAL 执行固定池=核数+有界输出队列互等死锁（←C8-013） | 待 7.1 | open |
| C4-002 | 1.1 | nop-stream-core-runtime | CachingTableLookup 刷新池无界+无 in-flight 去重（←C3-002、C8-014） | 待 7.1 | open |
| C4-009 | 1.1 | nop-stream-core-runtime | OpsJobManager.submit 失败窗口孤儿 coordinator+同 jobId 双活 | 待 7.1 | open |
| C5-010 | 1.1 | nop-stream-core-runtime | PollingJdbcMessageService 无 LIMIT 全量物化 backlog OOM（←C8-002） | 待 7.1 | open |
| C7-011 | 1.1 | nop-stream-core-runtime | standby 降级不取消在途 checkpoint persist，僵尸第二写者 | 待 7.1 | open |
| C8-003 | 1.1 | nop-stream-core-runtime | msg_queue INSERT-only 无界账本+WI6 门控不可达（←C7-003） | 待 7.1 | open |
| C8-011 | 1.1 | nop-stream-core-runtime | launch 共享 2 线程 poller：数据面积压垄断→控制面饥饿（←C4-004） | 待 7.1 | open |
| FAM-1 | 1.1 | nop-stream-core-runtime | materialization store 无界+overflow-bypass 背压旁路（C5-001 主←C7-001、C8-001） | 待 7.1 | open |
| C1-002（MA-02） | 1.2 | nop-stream-state-connectors | RocksDB gauge 锁外取 dbRef→native UAF（SIGSEGV 窗口；←C2-001、C6-004） | 待 7.1 | open |
| C1-001（MA-03） | 1.2 | nop-stream-state-connectors | CEP copyForSubtask 引用透传共享：NFA 条件图/ctx 槽位/udf 生命周期三面（←C1-005、C1-006；C1-001 已知未修复←r4 R5-CEP-05） | 待 7.1 | open |
| C2-002（MA-04） | 1.2 | nop-stream-state-connectors | Avro registry 无请求超时 × computeIfAbsent 锁内 HTTP（挂死+锁毒化；←C3-003、C4-003、C6-008、C8-004、C7-006①） | 待 7.1 | open |
| C3-002（MA-05） | 1.2 | nop-stream-state-connectors | 2PC 持久化登记无保留策略：JDBC 台账只增不删 + file manifest O(E) 放大（←C5-006、C7-001、C8-007、C7-007①） | 待 7.1 | open |
| C4-004（MA-06） | 1.2 | nop-stream-state-connectors | Debezium 引擎无限阻塞任务常驻平台共享 globalWorker 池（←C3-009） | 待 7.1 | open |
| C3-014（MA-07） | 1.2 | nop-stream-state-connectors | Debezium 停止/启动契约无终结保证：幽灵引擎/重启交叠/排队窗口三面（←C4-005、C4-006） | 待 7.1 | open |
| C5-002（MA-08） | 1.2 | nop-stream-state-connectors | RocksDB 默认全量 JSON checkpoint 整库物化进堆（←C8-005） | 待 7.1 | open |
| C3-004 | 1.2 | nop-stream-state-connectors | Debezium 枚举器恢复双登记→同表双引擎重复消费 | 待 7.1 | open |
| C8-001 | 1.2 | nop-stream-state-connectors | Debezium reader checkpoint offset 领先已发射→恢复静默丢 ≤256 条/检查点 | 待 7.1 | open |
| C8-006 | 1.2 | nop-stream-state-connectors | Kafka 2PC 事务存活期与 checkpoint 间隔零裕度→触界失败循环 | 待 7.1 | open |
| C1-001（MA-01） | 1.3 | nop-stream-orchestration-thin | UDF 注册表非线程安全：逐记录 call() 无锁读 × 公开 register() 任意线程写 + 运行期静默换绑（←C3-002、C2-001；含 sql.md §3a docs bug） | 待 7.1 | open |
| C3-008 | 1.3 | nop-stream-orchestration-thin | region 重启 Phase-3 流水线下兄弟 initializeState REPLACE 清零已推进游标→恢复重放已反映窗口（编排面关联 runtime） | 待 7.1 | open |
| C3-009 | 1.3 | nop-stream-orchestration-thin | 共享 UDF × 各子任务 open() 覆写共享 RuntimeContext→并行 ≥2 键控状态收敛单一 backend（根因载体 core ProcessOperator，跨单元关联） | 待 7.1 | open |
| C6-007 | 1.3 | nop-stream-orchestration-thin | cancelled volatile 跨 run 永不复位→区域重启共享实例零事件静默 EOS 假成功 | 待 7.1 | open |
| C4-001（MA-01，成员 C5-001/C7-001/C5-005） | 1.4 | nop-job | rpcPoll 条目无终结驱动：注册表/句柄永久泄漏+无退避重试风暴+WARN 放大+零观测（修复须同盖失败预算/deadline 兜底/DB 回传/PollEntry 精简/gauge 五面） | 待 7.1 | open |
| C2-001 | 1.4 | nop-job | per-job 监视器内调用外部 invoker：管理面/停机无限期阻塞+可构造 ABBA 死锁 | 待 7.1 | open |
| C1-001（MA-01，成员 C2-001/C7-016） | 1.5 | nop-wf-nop-task | task SUSPENDED 转移游离监视器 × cancel 竞态（kill 静默丢失/KILLED 后复活双向） | 待 7.1 | open |
| C3-001（MA-02，五站点族） | 1.5 | nop-wf-nop-task | wf 跨请求快照竞态：组迁移丢失/终结滞留/join 双实例/action 双跑/子流回滚 | 待 7.1 | open |
| C3-002（MA-03，成员 C4-002/C2-010） | 1.5 | nop-wf-nop-task | sync+executor 同池嵌套死锁与无界 sync 等待 | 待 7.1 | open |
| C4-001（MA-04，成员 C4-008/C3-013） | 1.5 | nop-wf-nop-task | retry/delay 业务体+saveState 簿记恒跑共享 globalTimer（4 线程），全平台定时语义失准 | 待 7.1 | open |
| C5-001（MA-05，成员 C8-001） | 1.5 | nop-wf-nop-task | 到期/提醒扫描无 LIMIT 全量载入+毒步骤不收敛 | 待 7.1 | open |
| C5-003（MA-08，成员 C8-004） | 1.5 | nop-wf-nop-task | 全局闸门强引用注册表 × 动态 keyExpr 键空间持久无界（已知未修复 plan 364 之上；owner 表态出口） | 待 7.1 | open |
| C7-001（MA-23，成员 C1-005） | 1.5 | nop-wf-nop-task | WfTaskScanner 多节点/重叠扫描无 fencing：due 双跑副作用先于守卫+remindCount 丢失更新 | 待 7.1 | open |
| C8-012（←C6-001/C1-008/C1-003A） | 1.6 | nop-retry-nop-tcc | executeImmediateRetry 返回 future ≠ 回调挂接 future，同步 DB 异常下扫描器 join 永久冻结、零日志（C10-001 覆盖） | 待 7.1 | open |
| C1-001（←C3-001） | 1.6 | nop-retry-nop-tcc | TCC 全局/分支状态 CAS 失败不阻断补偿 RPC，守卫基于私有过期内存副本 | 待 7.1 | open |
| C3-002（←C3-016） | 1.6 | nop-retry-nop-tcc | 分支级 CAS 失败（0 行/异常）不回写内存，聚合折叠 SUCCESS→全局误报终态 | 待 7.1 | open |
| C3-003（←C1-009） | 1.6 | nop-retry-nop-tcc | retry 调用链全程不续租，租约到期扫描器接管在途任务双执行 | 待 7.1 | open |
| C3-005 | 1.6 | nop-retry-nop-tcc | OVERWRITE 删行不取消在途异步链：幽灵任务与双执行 | 待 7.1 | open |
| C3-006（←C6-016） | 1.6 | nop-retry-nop-tcc | doExecute 簿记写内联结果回调，簿记异常覆盖业务结果→成功改判失败并重试 | 待 7.1 | open |
| C3-012 | 1.6 | nop-retry-nop-tcc | 全局首跳 CAS 异常→分支零驱动→聚合折叠 SUCCESS→陈旧 from 直写终态 | 待 7.1 | open |
| C6-002 | 1.6 | nop-retry-nop-tcc | 崩溃/挂死后 RUNNING attempt 永久残留，UK 碰撞将记录永久砖化 | 待 7.1 | open |
| C6-003（←C8-006） | 1.6 | nop-retry-nop-tcc | OVERWRITE delete+insert 非原子：半个成功后任务蒸发且无死信 | 待 7.1 | open |
| C7-001（←C6-004） | 1.6 | nop-retry-nop-tcc | retry 三张账本表零保留机制，成功即沉淀永久行 | 待 7.1 | open |
| C7-003 | 1.6 | nop-retry-nop-tcc | 解析/同步异常路径产生永不终止僵尸记录 + 每租约周期全栈 ERROR 放大 | 待 7.1 | open |
| C7-005 | 1.6 | nop-retry-nop-tcc | tcc 账本清理 API 零生产调用方 + 耗尽行双重永久排除 + 全表零索引 | 待 7.1 | open |
| C7-012 | 1.6 | nop-retry-nop-tcc | 扫描器只服务单一 namespace：非 default 记录永久 PENDING、零日志零接管（条件面：default-only 部署不触发） | 待 7.1 | open |
| C8-001（←C7-004/C6-014） | 1.6 | nop-retry-nop-tcc | 扫描循环被拒/Error 即（近）静默永久停摆：无自防御、无观测、无重臂（跨单元关联 nop-job C4-003，修复须平台级统一） | 待 7.1 | open |
| C8-001（←C5-005） | 1.7 | nop-batch | dispatch 容量全链零护栏：loadBatchSize≤0 → orm-reader 全表加载 Semaphore(0) 永久挂死 / jdbc-file 族静默空任务丢数（参数护栏矩阵三层完整者 0；修复先于 C8-009） | 待 7.1 | open |
| C8-002（←CL-008） | 1.7 | nop-batch | ExportDbTool 缺省 batchSize=0 静默钳位 1 每行一 chunk：日志/状态文件写放大磁盘耗尽（与 Import `>0` 守卫 F3 不对称；biz 链 batchSize=0 穿透同批 C8-010） | 待 7.1 | open |
| C5-001 | 1.7 | nop-batch | excel-reader beforeRead 全 sheet 物化 List：50 万行 1.5–3GB，maxCountExpr 接线禁用且先物化后截断无兜底（C10-011 补回归钉义务） | 待 7.1 | open |
| C5-002（←CL-038 事实） | 1.7 | nop-batch | jdbc-reader fetchSize 死配置：门条件恒 false + 非 MySQL dialect 无 streamingFetchSize + autocommit 未关 → GB 级全量缓冲（修复须同步改写测试 N2） | 待 7.1 | open |
| C6-001（←C7-002B/CL-026/CL-024 验证断言） | 1.7 | nop-batch | executeChunkLoop 仅 catch Exception：Error 逃逸 future 永不完成 → 资源永不关闭+任务行永久 RUNNING+调用方挂死（修复必须与 C6-010 同 diff：try 上移+catch Throwable） | 待 7.1 | open |
| C6-002（←C7-002A） | 1.7 | nop-batch | executor.execute REE 裸逃逸：句柄泄漏+永久 RUNNING（注入有界池饱和/停机竞态；默认装配不可达已裁定） | 待 7.1 | open |
| C7-001 | 1.7 | nop-batch | 任务行永久 RUNNING 死区：tryMarkTaskRunning 无 stale 判定无 fencing 无专用运维端点，cron 每 5s 错误风暴（跨单元关联 WI 1.5 C7-001/WI 1.6 C8-001 平台级 fencing 对齐；修复前须参数化改写测试 N1） | 待 7.1 | open |
| C7-003 | 1.7 | nop-batch | 三张账本表零保留：清理代码完全不存在（非零装配），UUID taskKey 每触发 +1 行单调增长（跨单元关联 WI 1.6 C7-001 账本保留族，Phase 8 批次对齐） | 待 7.1 | open |
| nop-kernel-concurrency-C1-001 | 2.1 | nop-kernel-concurrency | RoundRobinSupplier.resize 收缩路径锁外关闭旧数组尾段：并发 get 取得已关连接（Lettuce 生产链+IoC 刷新派发链可达；退役代际族吸收 C2-021） | 待 7.1 | open |
| nop-kernel-concurrency-C2-001 | 2.1 | nop-kernel-concurrency | ResourceCacheEntry 持 entry 监视器加载模型：跨 entry 嵌套监视器循环依赖死锁（C2-017 同变更集绑定；单线程循环 StackOverflow 新面） | 待 7.1 | open |
| nop-kernel-concurrency-C3-001 | 2.1 | nop-kernel-concurrency | 周期任务设施无统一异常绝缘层：一次 Throwable 静默永久停摆（29 处生产站点；30 点绝缘状态表；与 WI 1.6 C8-001 平台级统一） | 待 7.1 | open |
| nop-kernel-concurrency-C4-001 | 2.1 | nop-kernel-concurrency | globalTimer 死配置（newScheduledExecutor 副作用改写 maxPoolSize=0）+4 线程全平台共享定时器阻塞放大（28 消费方分类终表；与 WI 1.5 MA-04 隔离方案统一） | 待 7.1 | open |
| nop-kernel-concurrency-C7-007 | 2.1 | nop-kernel-concurrency | 模型缓存 check-changed 双漏检窗口（旧内容+新 mtime 固化；ResourceTimestampCache 死代码；与 C9-001/C9-010 同链三面绑定单点修复） | 待 7.1 | open |
| nop-core-framework-C1-007 | 2.2 | nop-core-framework | 配置发布协议三字段裸写裸读+两步写序撕裂（族案含 C1-017；api-core 单一不可变 holder 修复，7 写点×8 读站；WI 2.1 C1-001 刷新链上游） | 待 7.1 | open |
| nop-core-framework-C1-009 | 2.2 | nop-core-framework | JdbcConfigSource.close 竞态：停机后在途刷新重建永不关闭的连接（←C4-002 同位点并案） | 待 7.1 | open |
| nop-core-framework-C1-010 | 2.2 | nop-core-framework | 插件 unload×reconcile 交错活容器永久泄漏（含 C1-011/012 并案、←C2-004；lifecycleLock 全迁移统一修复批次） | 待 7.1 | open |
| nop-core-framework-C1-021 | 2.2 | nop-core-framework | tryTriggerChange 在 try 外：回调异常永久静默杀死 DB 配置热更新全链（CL-001 终裁升格；WI 2.1 C3-001 平台绝缘层吸收） | 待 7.1 | open |
| nop-core-framework-C2-001 | 2.2 | nop-core-framework | getBean0 锁序环依赖边不可见跨线程永久死锁（←C2-012；allow-cycle 默认 true 附注；历史 check 审计 P2-6 同源回访升 P1） | 待 7.1 | open |
| nop-core-framework-C2-002 | 2.2 | nop-core-framework | ProducedBeanInstance 跨实例 monitor 等待环 wait 无超时（←C1-002 跨维度并案） | 待 7.1 | open |
| nop-core-framework-C4-003 | 2.2 | nop-core-framework | concurrent-start 三复合面：无 join 点+失败吞入+死容器 exit-0 伪装/quarkus 三绿假活（←C4-008；与 C4-009/011+CL-019 同 PR 批次；门控 P1/P2 双口径留 7.1 裁） | 待 7.1 | open |
| nop-core-framework-C6-001 | 2.2 | nop-core-framework | loadPluginFromJar 失败点 try 外：ClassLoader+JAR 句柄线性累积泄漏（与测试 N2 设计冲突须消解） | 待 7.1 | open |
| nop-core-framework-C6-002 | 2.2 | nop-core-framework | 插件状态机永久卡死 DEACTIVATING/ACTIVATING 无恢复 API | 待 7.1 | open |
| nop-core-framework-C6-003 | 2.2 | nop-core-framework | config source 先调度后加载：初始失败泄漏永久 globalTimer 刷新任务（启动期降档不适用论证成立） | 待 7.1 | open |
| nop-core-framework-C6-004 | 2.2 | nop-core-framework | ShellRunner 非 timeout 异常不销毁子进程+stderr 线程 pin（←C6-014；修复须外层 try finally） | 待 7.1 | open |
| nop-service-framework-hotpath-C3-002 | 2.3 | nop-service-framework-hotpath | invokeOperation 出口泵缺 thenOnContext：OrmBatchLoader 在 future 完成线程 requireSession→NOT_IN_SESSION/错 ORM 会话 | 待 7.1 | open |
| nop-service-framework-hotpath-C3-003 | 2.3 | nop-service-framework-hotpath | BufferedStreamingPublisher 背压断裂：映射链自驱动 request(1) 越窗后元素进无界 ArrayDeque，慢客户端长流内存失控（AI 路由默认形态） | 待 7.1 | open |
| nop-service-framework-hotpath-C3-007 | 2.3 | nop-service-framework-hotpath | BizActionService.callActionAsync 裸 executor.execute：调用方 IContext 不传播，租户/用户跨任务残留（全单元唯一站点，10 形态 grep） | 待 7.1 | open |
| nop-service-framework-hotpath-C4-007 | 2.3 | nop-service-framework-hotpath | SSO 半衰期 token 刷新在认证过滤器载体线程无超时阻塞 IdP HTTP：brownout 确定性服务级停摆（修复面：ILoginService 异步化+timeout API+thenCompose 领先 next.get） | 待 7.1 | open |
| nop-service-framework-hotpath-C8-001 | 2.3 | nop-service-framework-hotpath | WS 订阅推送同步阻塞发送（无写超时）钉死 globalTimer 全池（实为 4 线程，ρ=N·L/40；平台 27 处消费方） | 待 7.1 | open |
| nop-service-framework-hotpath-C8-002 | 2.3 | nop-service-framework-hotpath | Gateway 429 重试放大 ×7（加法）无准入上界：在途×90s 挂起确定性 OOM 轨迹（与 C4-003 降级事由独立） | 待 7.1 | open |
| nop-service-framework-hotpath-C9-012 | 2.3 | nop-service-framework-hotpath | LocalDateTimeFieldMarshaller computeSize=20 vs 纳秒实写 23-29 字节 protobuf 越界（双模式硬失效实测；**移交功能正确性维度**） | 待 7.1 | open（接收轨落名） |
| nop-persistence-rw-C1-001 | 2.4 | nop-persistence-rw | IContext 会话/事务注册表 get-or-create 竞态：僵尸会话/事务表覆盖/双一级缓存（←C1-023/C2-003；ContextAttrs.getOrCreate 单修复） | 待 7.1 | open |
| nop-persistence-rw-C1-003 | 2.4 | nop-persistence-rw | ormModel 非 volatile：reload 后旧模型无限期可见+非同步 setOrmInterceptor | 待 7.1 | open |
| nop-persistence-rw-C1-015 | 2.4 | nop-persistence-rw | OrmComponentModel 先发布引用后填充：getColumnPropIdMap 热路径竞态（无需 JMM 假设） | 待 7.1 | open |
| nop-persistence-rw-C2-001 | 2.4 | nop-persistence-rw | session 全生命周期零互斥：flush 重入守卫为静默跳过（同上下文并发丢刷写/双刷写） | 待 7.1 | open |
| nop-persistence-rw-C2-002 | 2.4 | nop-persistence-rw | runInNewSessionAsync 完成线程执行 flush/registry 恢复/close 无同步 | 待 7.1 | open |
| nop-persistence-rw-C2-004 | 2.4 | nop-persistence-rw | 全局实体缓存 read-repair：迟到 put 覆盖并发 evict，陈旧值无界驻留（实体级 opt-in 启用上可达） | 待 7.1 | open |
| nop-persistence-rw-C2-005 | 2.4 | nop-persistence-rw | DaoEntityBlockingSource CAS 抢占退化无条件 UPDATE：任务重复投递 | 待 7.1 | open |
| nop-persistence-rw-C2-013 | 2.4 | nop-persistence-rw | 事务注册协议无原子性：半个成功 ERR_TXN_NOT_REGISTERED（REQUIRED 组语义退化） | 待 7.1 | open |
| nop-persistence-rw-C2-015 | 2.4 | nop-persistence-rw | subTransactions 裸 LinkedHashMap：组提交丢写无感知（最重后果） | 待 7.1 | open |
| nop-persistence-rw-C2-017 | 2.4 | nop-persistence-rw | JdbcTransaction 连接生命周期无互斥：双取池连接泄漏+eagerRelease 后僵尸连接丢写（←C1-026） | 待 7.1 | open |
| nop-persistence-rw-C3-001 | 2.4 | nop-persistence-rw | runInNewSessionAsync 上游永不完成：session 永不关闭+注册表滞留 | 待 7.1 | open |
| nop-persistence-rw-C3-005 | 2.4 | nop-persistence-rw | flush 先清 dirty 再执行 SQL：非事务路径半个成功残留（←C2-006） | 待 7.1 | open |
| nop-persistence-rw-C6-001 | 2.4 | nop-persistence-rw | drainTo 事务横跨等待窗口：K≥8 消费者先于生产者即连接池耗尽（←C3-006 单工单） | 待 7.1 | open |
| nop-persistence-peri-C6-004（MA-01 主） | 2.5 | nop-persistence-peri | PubSub 订阅管理竞态：cancel/suspend/resume 绕过监视器三步变更共享 entry/注册表/通道，订阅静默失效或复活双投递（←C1-011/C3-004；已知未修复 check/nosql-cdc 2026-08-19 前案） | 待 7.1 | open |
| nop-persistence-peri-C8-001（MA-02 主，开放裁定暂计 P1） | 2.5 | nop-persistence-peri | PubSub 消费回调在共享 Redis IO 线程内联执行：慢消费者队头阻塞→服务端断链→静默丢消息，CompletionStage ack 契约被整体丢弃（←C3-005/C7-010；严重度 7.1 终裁） | 待 7.1 | open |
| nop-persistence-peri-C6-003（MA-04 主） | 2.5 | nop-persistence-peri | 链停止协议不可验证：thread 字段晚于 start 发布使 join 空转、abort 读 stale-null 跳过 stopAll、stopAll 仅 cancel 不等待（←C1-004/CL-009） | 待 7.1 | open |
| nop-persistence-peri-C7-003（MA-05 主） | 2.5 | nop-persistence-peri | 状态行全列覆盖无版本谓词 + 7 编排入口零单飞/fencing：abort×confirmSwitch 背离终态、多节点双跑双链（←C1-005/006/009） | 待 7.1 | open |
| nop-persistence-peri-C7-001（MA-06 主） | 2.5 | nop-persistence-peri | 链死亡不可见：getFailure 全仓零调用方，lag=0 门禁把死链判追平，VERIFY 放行缺数目标（←C3-002/CL-006/CL-018/CL-023；先修 C3-008 键派生） | 待 7.1 | open |
| nop-persistence-peri-C3-001（MA-10 主） | 2.5 | nop-persistence-peri | startFallbackChains 部分启动无补偿 + jobName 丢 querySpace 句柄 put 覆盖：孤儿链不可停且双写同目标表（←C6-001） | 待 7.1 | open |
| nop-persistence-peri-C1-003 | 2.5 | nop-persistence-peri | 迁移历史表全守卫非原子 check-then-act：多节点并发启动 DDL 重复执行 + FAILED 脏行覆盖对方 success 行致永久重试循环 | 待 7.1 | open |
| nop-persistence-peri-C1-007 | 2.5 | nop-persistence-peri | rebuildRouting 非 volatile 热替换 shardSelector：SWITCH 开门后请求线程无限期读旧路由，业务写静默落旧分片（平台侧同案 nop-core-framework-C1-007） | 待 7.1 | open |
| nop-persistence-peri-C3-008 | 2.5 | nop-persistence-peri | lag 探测 connector 名与链注册名 base-i 错位：rescale 主场景 lag=0 门禁观测量恒空平凡通过（MA-06 修复顺序前置） | 待 7.1 | open |
| nop-persistence-peri-C3-009 | 2.5 | nop-persistence-peri | relocate 路由 querySpace 硬编码空串：增量链 filter 恒 false 100% 丢事件、FULL upsert 落回源侧 default 表（与 C3-008 非同根双立） | 待 7.1 | open |
| nop-persistence-peri-C7-002 | 2.5 | nop-persistence-peri | 五状态机 ALLOWED 零自环：javadoc 承诺的 fix-forward 路径自身走不通，一次瞬时故障全量报废（←CL-007/CL-008） | 待 7.1 | open |
| nop-persistence-peri-C7-004 | 2.5 | nop-persistence-peri | 写门 updateConfigValue 进程内裸写无持久化：多实例拓扑门状态不收敛，VERIFY 资格判定失真 | 待 7.1 | open |
| nop-persistence-peri-C7-014 | 2.5 | nop-persistence-peri | PubSub 断线自动重连后 Redis 侧订阅全丢且无重订阅无检测：静默失联直至重启（lettuce-core 7.5.2 字节码核验） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-001（MA-01 主） | 3.1 | nop-ai-core-nop-ai-agent | 流式主链族：callStream 取消只传上游、publisher 永不关闭、聚合计无界——取消后订阅者永久无终止信号、单请求 OOM（←C3-002/009+C4-001/002+C5-001） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C1-008（MA-02 主） | 3.1 | nop-ai-core-nop-ai-agent | 引擎注入目标登记窗口：resume/wake/restore 零注册 + 暂存分支裸 ArrayList——CME/撕裂持久化/注入丢失（←C3-014） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C5-015（MA-03 族首） | 3.1 | nop-ai-core-nop-ai-agent | F4 引擎终态清理族：session store put-only + checkpoint/账本/注册表无终态释放——历史会话堆内永驻（族 29 站点，remove 已实现纯接线；←C5-009/012/013/020+C1-006/011/014/016/017/018/026+C7-013/017/028/030/031） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C4-010（MA-04 主） | 3.1 | nop-ai-core-nop-ai-agent | spawn 编排池泄漏：per-invocation orchestrator 全仓无 close——每次工具调用泄漏 max(2,NCPU) 固定池常驻线程（←C4-010/C7-032 三重登记统一 P1） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-022（MA-05 主） | 3.1 | nop-ai-core-nop-ai-agent | 成员/spawn 超时取消契约 F3 漏修族：5 路径仅 2 实现 cancelSession——挂死成员僵尸化 + RECLAIM 同任务双执行（←C7-023/C3-025/C4-014） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C5-008（MA-06 主） | 3.1 | nop-ai-core-nop-ai-agent | 长会话归档/spill/journal 无界累积 + 恢复全量物化 4× 放大（←C5-010/011） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-011（MA-08 主） | 3.1 | nop-ai-core-nop-ai-agent | 超时取消传播断链：机械超时/中断不向底层工具执行传播，进程级 kill 通道仅 heartbeat 一处调用、cancelCall javadoc 契约无实现——幽灵 bash/容器/HTTP 持续运行 | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-028（MA-09 主） | 3.1 | nop-ai-core-nop-ai-agent | 方言流式协议族（单修复批）：三站点 tool_call 截断 + 流式 usage 全链失活 + Ollama 空串抢占 + 流式错误通道缺失——缺省 stream=true 主路径确定性丢数据（←C3-030/031/032） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-021（MA-11 主） | 3.1 | nop-ai-core-nop-ai-agent | DBMessageService 单线程 poller 内联消费：call-agent join 120s 期间全模块消息冻结 + 毒消息无 DLQ 停摆（←C7-025/C4-CL-040） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-012（MA-19 主） | 3.1 | nop-ai-core-nop-ai-agent | 接管锁续期异常无限容忍：SQLException 仅 WARN 无限重试——非对称分区期间永不自 abort，fencing 语义整体失效双执行窗口 | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C4-011 | 3.1 | nop-ai-core-nop-ai-agent | AgentDispatchExecutor 经 runAsync 落 commonPool：阻塞工具体（300s cap）占满 JVM 全局池（与 WI 1.2 C4-004/WI 2.3 C8-001 同族判级一致；←CL-025） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-001 | 3.1 | nop-ai-core-nop-ai-agent | 流式聚合无全程期限/静默检测：SSE 中途挂死 future 永不完成且对健康检测不可见（缺省装配挂死链补证） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-002 | 3.1 | nop-ai-core-nop-ai-agent | 流式中途干净断连被 onComplete 误判成功：五方言终止信号在聚合端未校验——截断内容以成功响应交付，重试/守卫全部失效 | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C1-001（MA-01 主） | 3.2 | nop-ai-toolkit-tools-shell | ToolManagerImpl 路径串行队列：CHM bin 锁内同步执行 + executeNow.get() 同步抛出时 result future 永不完成——排队分支全部静默悬挂（JDK 实测证伪 loud-fail 对照；←C4-005） | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C6-001（MA-02 主） | 3.2 | nop-ai-toolkit-tools-shell | 沙箱 reader 泄漏族：孪生后台子进程持 stdout 管道→reader 线程/fd 永久泄漏 + finally 发布活 StringBuilder 终态竞态（←C4-001/C6-008；Host killTree 已修 Docker 未同修的 F3 残留） | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C6-002（MA-03 主） | 3.2 | nop-ai-toolkit-tools-shell | docker 清理族：超时路径不 destroyForcibly CLI + killContainer 失败被吞 + 取消句柄已注销——容器/进程/线程无界残留（←C8-009） | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C6-003（MA-04） | 3.2 | nop-ai-toolkit-tools-shell | runShortCommand 先无限期 drain 后才 waitFor(5s)：daemon 挂起时清理线程自身永久阻塞，墙钟预算失效 | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C4-003（MA-06 主，判级分歧暂计 P1） | 3.2 | nop-ai-toolkit-tools-shell | shell 管道死锁族案：阶段提交 REE 无回滚级联吞线程 + ≥1024 行输出确定性挂死 + 下游死亡上游阻塞 + `0<&1` 自引用（←C8-001/003/011；C4 P1 vs C6/C8 条件化 P2 分叉留 7.1） | 待 7.1 | open |
| nop-ai-gateway-C2-001（MA-07 主） | 3.3 | nop-ai-gateway | FailoverStreamFlow 取消×重订阅 TOCTOU：新 attempt 的 registry.acquire 无任何 release 路径——per-账号并发计数永久 +1 幽灵，慢相变饱和误判，无 gauge 需重启恢复（←C1-008 一行闭合） | 待 7.1 | open |
| nop-ai-gateway-C1-001 | 3.3 | nop-ai-gateway | FailoverStreamFlow 窗口内失败清理（L438 buffer.clear 脱离 monitor）与 request() 冲刷交错——attempt-1 已缓冲内容泄漏进最终输出或 ArrayDeque 撕裂致 onNext(null)（同文件修复批 A） | 待 7.1 | open |
| nop-ai-gateway-C3-001 | 3.3 | nop-ai-gateway | FailoverStreamFlow 终态冲刷受 demand 约束——缓冲剩余元素静默丢弃后仍发 onComplete，流式响应以成功截断；BufferedStreamingPublisher pendingComplete 为同仓正确先例（同文件修复批 A） | 待 7.1 | open |
| nop-ai-gateway-C8-001 | 3.3 | nop-ai-gateway | FailoverStreamFlow 越窗后零需求缓冲无上界（容量门只写在越窗前分支）+ 上游恒 request(MAX)——下游慢/停滞时整条补全流无界积压（吸收 CL-001/003/005/012/014；与 WI 2.3 C3-003/WI 3.1 C5-001 跨单元同族 P1 对齐，同文件修复批 A） | 待 7.1 | open |
| nop-ai-rest-C6-001（MA-01 主） | 3.4 | nop-ai-rest | PgVectorStore.schemaReady 实例级旗标 vs per-collection DDL 作用域错配：多 index 部署从第二个起永久确定性 SQL 失败（进程生命周期不可自愈；并发首建 DDL 竞态窗；三登记一处修复闭合） | 待 7.1 | open |
| nop-ai-rest-C1-001 | 3.4 | nop-ai-rest | JavaSourceFileFinder.getAllJavaSourceFiles parallelStream 向普通 ArrayList 并发 add——多模块仓静默丢文件或 AIOOBE（并行面内生于方法体；零外部消费者条件化保留 P1） | 待 7.1 | open |
| nop-ai-rest-C1-003 | 3.4 | nop-ai-rest | InMemoryVectorStore removeIf+add 复合 upsert 非原子——并发同 docId 摄取重复条目（与 javadoc「线程安全」相悖；PgVectorStore ON CONFLICT 正确范式反证；触发需显式同 id 并发 upsert） | 待 7.1 | open |
| nop-ai-rest-C3-005 | 3.4 | nop-ai-rest | RagSynthesizeService 不检查 ChatResponse.isSuccess——LLM 错误响应静默折叠为空串 200 返回（错误以成功交付族，与 WI 3.1 C7-002/WI 3.3 C3-001 家族关联；升格：可达性三步闭合） | 待 7.1 | open |
| nop-message-C3-002+C8-004（MA-02 同 diff 配对） | 4.1 | nop-message | Debezium 事件回调无异常屏障：畸形事件逃逸进 engine.run() 使 CDC 流终态死亡（isStarted 永久 true 静默断流）+ dispatchEvent 吞订阅者异常推进 offset 事件永久丢失——屏障存活/投递两极性，生产主路径免疫、直接嵌入方受损面（判级分歧留 7.1） | 待 7.1 | open |
| nop-message-C6-002（MA-05 主） | 4.1 | nop-message | DebeziumMessageSource.subscribe 先 put 后启动引擎：启动失败幽灵订阅残留 + 调用方拿不到 ICancellable 永不可注销——后续成功启动后事件持续投递给「被告知失败」的消费者（长生命周期嵌入方双投递） | 待 7.1 | open |
| nop-message-C8-002 | 4.1 | nop-message | LocalMessageService 消费失败静默丢弃且从不派发 onConsumeFailure——三层失败架构在 Local 整体断链（Local 为生产路径 nop_sys_event 通道，无已声明边界可援引） | 待 7.1 | open |
| nop-cluster-C1-004（MA-01 主） | 4.2 | nop-cluster | 选主 doStop 复活竞态：无锁 stop 与 synchronized onElectionCompleted 竞交——stop 后 leaderEpoch/electionPromise 复活为「本节点是 leader」、监听器乱序（allowRestart 默认 true + 生产子类 DB I/O 窗口；←C4-001/C2-003/C3-004/C2-005/C4-002 六条族） | 待 7.1 | open |
| nop-cluster-C1-001 | 4.2 | nop-cluster | Nacos 事件线程原地改写共享 ServiceInstance（copyFromInstance 全字段非 volatile）——请求线程撕裂读 + _host 惰性缓存固化陈旧端点（12 读者站点失效面） | 待 7.1 | open |
| nop-cluster-C1-003 | 4.2 | nop-cluster | 订阅初始化旧快照无版本守护——事件 B 落在 getAllInstances 返回与 initInstances 之间即回退新事件态（复核补强：窗口不依赖 SDK 内部乱序） | 待 7.1 | open |
| nop-cluster-C3-001（MA-07 主） | 4.2 | nop-cluster | ClusterRpcClient.callAsync 重试回调内同步异常被吞（whenComplete 返回值丢弃）——返回 future 永不完成调用方永久挂起 + Sentinel entry 泄漏复合（live 默认装配路径证实；←C3-008/C8-008 同 diff） | 待 7.1 | open |
| nop-network-integration-C1-008+C3-018+C6-020（MA-01 主） | 4.3 | nop-network-integration | MQTT 会话接管订阅抹除：removeConnection 无属主校验——设备重连后下行路由静默失效（C1-008 失效面①被异步 auth 前提证伪修正；C6-020 独有窗口①成立；三登记一工单） | 待 7.1 | open |
| nop-network-integration-C3-002（MA-02 主） | 4.3 | nop-network-integration | JDK 取消语义失效：下载 body/SSE readLine 的 cancelToken 只绑 headers 阶段 sendAsync——取消后幽灵下载继续写盘、读取线程按次泄漏（成员 C3-003+C6-002） | 待 7.1 | open |
| nop-network-integration-C3-004（MA-03 主） | 4.3 | nop-network-integration | sidecar 校验和探测同步 get(30s) 跑 apache I/O reactor 回调线程（HC5 ioThreadCount 缺省即 1）——小部署必现 30s 全连接停摆（成员 C6-006；双判级 apache P1/jdk,okhttp P2 留 7.1） | 待 7.1 | open |
| nop-network-integration-C3-015（MA-04 主） | 4.3 | nop-network-integration | FeishuClient JdkStreamTransport.send 无界 get() 跑在心跳+重连唯一单线程 scheduler——半开连接静默故障即心跳/重连整体死锁（成员 C6-024 升格 P1；载体 raw 线程属 WI 2.1 CL-021 点名） | 待 7.1 | open |
| nop-network-integration-C1-009（MA-05 主） | 4.3 | nop-network-integration | FeishuClient 重连无单飞/世代守卫：onClose/onError 双入口双调度 + started 跨代复用 + stop→start 重启窗口陈旧回调——双活/三活 WS（成员 C3-017；判级留 7.1） | 待 7.1 | open |
| nop-network-integration-C6-021（MA-06 主） | 4.3 | nop-network-integration | FeishuClient.stop() 与在途 attemptReconnect 竞态（检查后无世代复核、listener 被重武装）——stop 后新建 WS 孤儿连接（成员 C3-016；判级留 7.1；同构竞态 NettyTcpClient C6-022 另立 P2） | 待 7.1 | open |
| nop-network-integration-C1-001（MA-07 主） | 4.3 | nop-network-integration | SimpleRpcClientFactory.init 挂在 newInstance 非幂等——logBody 拦截器列表无界累积 + timer/executor 检查-创建竞态单线程定时器泄漏（WI 2.1 CL-024 原始站点实审；成员 C6-014） | 待 7.1 | open |
| nop-host-C1-006（MA-01 主） | 4.4 | nop-host | 双宿主启动窗口空链固化：IocCoreInitializer registerInstance 覆盖 BeanContainer facade 窗口内 filter 全落 Spring/CDI 桥空链——认证旁路不可自愈（←C4-003 启动窗口无就绪门禁） | 待 7.1 | open |
| nop-host-C6-007（MA-02） | 4.4 | nop-host | ContextTaskQueue 吞同步异常 future 永不完成——Quarkus 认证请求永久悬挂（/px/ 畸形 JSON 用户可触发；future 族第 5 例，平台根因绝缘层移交 WI 2.3 CL-018+WI 2.1 C3-001） | 待 7.1 | open |
| nop-host-C6-001 | 4.4 | nop-host | 上传流「先校验后消费」失败路径永不关闭——每被拒上传泄漏 1 个 fd，外部可稳定触发（C6-008 判级维持 P2 随族留 7.1） | 待 7.1 | open |
| nop-host-C1-001 | 4.4 | nop-host | SpringWebProxy 凭据对（authorization/cookie）由 Reactor 线程写、请求线程读无 volatile——陈旧 token 重发 + 撕裂凭据对 | 待 7.1 | open |
| nop-search-runner-autotest-C4-001 | 4.5 | nop-search-runner-autotest | CliRunCommand scheduleWithFixedDelay 返回 Future 丢弃——周期任务任一次脚本异常即静默停摆且进程挂在 stdin（F9 定向命中；watch 模式按实际触达面判级） | 待 7.1 | open |
| nop-auth-credential-MA-01（六族清理配对族主） | 5.1 | nop-auth-credential | 六族 DB 状态存储惰性删除+零回收：NopAuthSession（含 accessToken/refreshToken 永久驻留）+ MfaChallenge + SmsCode + EmailCode + RateLimitCounter + LoginAttempt——长时运行单调增长（F4） | 待 7.1 | open |
| nop-auth-credential-MA-02（Redis TTL 附着族主） | 5.1 | nop-auth-credential | Redis 六处 INCR+setTimeoutAsync 非原子——TTL 附着失败致 RedisLoginAttemptStore 永久锁号 | 待 7.1 | open |
| nop-network-integration-C3-001 | 4.3 | nop-network-integration | OkHttp onResponse 异常吞没：body().string() 断连 IOException 被吞 future 永不完成——同步调用方永久阻塞（future 永不完成族第 4 例；同文件 doDownloadAsync 已有保护 F3 漏修） | 待 7.1 | open |
| nop-network-integration-C3-005 | 4.3 | nop-network-integration | 无校验源跳过 verifyAndRename——默认配置下载报成功但目标文件永不产生（.part 滞留）（错误以成功交付族第 4 例；与 C6-001 P0 同批设计；Apache 无条件 move 跨实现分歧实证） | 待 7.1 | open |
| nop-network-integration-C3-024 | 4.3 | nop-network-integration | SftpClient 全链路无超时（jsch 默认 0=无限，SftpConfig 无 timeout 配置面）——网络故障下业务线程与 SSH 会话无限挂起（FileServiceResource 请求路径可达证实） | 待 7.1 | open |
| nop-network-integration-C6-004 | 4.3 | nop-network-integration | SSE 反压无超时阻塞等待——Apache 形态挂起 I/O reactor 线程（HC5 ioThreadCount 缺省 1 全连接停摆）/JDK readLine 不可中断 cancel 无效（与 WI 3.1 C7-001 本体面两侧同批） | 待 7.1 | open |
| nop-network-integration-C6-005 | 4.3 | nop-network-integration | BASE64_FORM 上传 IoHelper.readBytes 不关闭输入流——三实现共漏（与 CL-012 OOM 峰值面线索同站点不同本体，修复批协同） | 待 7.1 | open |
| nop-network-integration-C6-017 | 4.3 | nop-network-integration | NettyTcpServer doStart bind 失败不关 boss/worker 事件循环组——每次启动失败泄漏整组 I/O 线程（对照 C6-014 族正确兄弟写法） | 待 7.1 | open |
| nop-network-integration-C6-018 | 4.3 | nop-network-integration | SftpClient 构造期「session 已连、channel 未开」失败——SSH 会话无人回收（与 C3-024 同文件同批） | 待 7.1 | open |
| nop-network-integration-C6-019 | 4.3 | nop-network-integration | TencentEmailSender 每次 sendEmail 新建 SesClient 从不 shutdown——连接池随发送次数无界累积（同模块 YunpianSmsSender 有 finally close；保护面 C10-004/C10-013） | 待 7.1 | open |
| nop-sys-dyn-MA-01（DB 行协调原语 fencing 纪律族主，P1 站点×2） | 5.2 | nop-sys-dyn | 过期分布式锁行删除缺 holderId 互斥校验（无声破坏互斥）+ 事件认领一次性租约不续期且完成写无 fencing（双节点并发处理同一事件） | 待 7.1 | open |
| nop-sys-dyn-I-01 | 5.2 | nop-sys-dyn | 已发布 mergedStore 并发结构修改——TreeMap 撕裂读 | 待 7.1 | open |
| nop-sys-dyn-I-02 | 5.2 | nop-sys-dyn | 订阅 cancel×subscribe 竞态孤儿化 → 消息置 PROCESSED 静默丢弃 | 待 7.1 | open |
| nop-file-datav-W-01（CL-014） | 5.3 | nop-file-datav | 文件去重竞态：isUniqueRef 判-删窗口 → 悬挂引用 | 待 7.1 | open |
| nop-file-datav-W-04（CL-009） | 5.3 | nop-file-datav | dashboard 主查询路径 rowLimit=null 全量物化无行上界 | 待 7.1 | open |
| nop-report-format-C5-001 | 5.4 | nop-report-format | record 反序列化数据驱动长度无界物化：getFieldLength 无上限 + readBytes 先 new byte[n] 后读——文件/netty 双生产路径 OOM 面（同族 CountArray 1M/DynLV 64MB/LV 相对长度三处有界反衬；ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG 可复用） | 待 7.1 | open |
| nop-rule-metadata-W-01（超时族主 C7-102+C7-001+C4-R2-001） | 5.5 | nop-rule-metadata | 外部 JDBC 全链无超时（queryTimeout/networkTimeout 0 配置、SimpleDataSource.setLoginTimeout no-op、DriverManager.setLoginTimeout(5) 仅界建连）——外部源挂起→质量监控静默停摆+checkpoint 永久锁死+调度池控制面锁死（判级暂计 P1，C4 侧 P2 分歧登记 7.1） | 待 7.1 | open |
| nop-rule-metadata-W-02（retention 族主 C7-004+C7-101） | 5.5 | nop-rule-metadata | 7 张 append-only 时序表零保留路径（QualityResult/QualityScore/Catalog/ProfilingResult/ReconResult/ModelChangedEvent mediumtext 双快照+告警工作流实例）——cron+auto-score 驱动无界增长（F4） | 待 7.1 | open |
| nop-rule-metadata-W-03（C7-R2-001） | 5.5 | nop-rule-metadata | 规则热更新失效传播缺口：DaoRuleModelLoader traceDepends 只钉定义行，判定树内容在 NopRuleNode 子行且一等 CRUD 可直接改写——节点级编辑不触发缓存失效，陈旧规则无限期服务无日志无告警 | 待 7.1 | open |
| nop-graph-duckdb-W-01（账本泄漏族主 C6-001 吸 3 载体） | 5.6 | nop-graph-duckdb | OPEN_FILES+connections 账本仅 engine.close() 释放——单例长驻期每任务净增永不回收（F4）+ 过期指纹误拒同路径异配置重开（registry 自述场景 100% 假阳性 bizFatal） | 待 7.1 | open |
| nop-graph-duckdb-W-02（竞态族主 C6-002 吸 C1-002） | 5.6 | nop-graph-duckdb | open/close 竞态：checkClosed→慢速 connect 窗口内 close() 早退，track 迟到连接进已关闭引擎——native 句柄+单写者文件锁泄漏（IDuckDbEngine L16 契约明文违约） | 待 7.1 | open |
| nop-graph-duckdb-W-03（C6-R2-002） | 5.6 | nop-graph-duckdb | materialize CTAS 无 table-exists 守卫（对照 readExternal 有）——CTAS 提交后失败留孤儿表，非 bizFatal 可重试→持久 dbPath 重试/恢复重放永久必败（F2 半个成功+F3 守卫漏配） | 待 7.1 | open |
| nop-graph-duckdb-W-04（不可中断族主 CS-001 吸 CS-R2-001+2.1 C6-CL-031 别名） | 5.6 | nop-graph-duckdb | LeidenDetector runWithTimeout 超时幽灵线程：CWTS 1.3.0 全库零 interrupt 引用 shutdownNow 必然无效，非 daemon 线程不可辨识；5 调用点零规模守卫且 7 个 @BizQuery 请求线程同步触发 | 待 7.1 | open |


### WI 4.4 nop-host（CL-001~008：编号 8、并入工单 3、独立线索 5、watch 1[CL-005 拆分]、驳回 0；逐条归属以 merge-adjudication.md §八 权威表为准——closure audit 发现本小节正文误贴后按 §八 改写）

**并入工单（3）**：CL-004→C1-001（宿主过滤器异步流阻塞族）、CL-005b→C6-004（redelivery/心跳面协同）、CL-007→C4-002（同站点治理）。**独立线索台账（5）**：CL-001/002/003/006/008（CL-008 isResponseSent 语义两宿主不对称）。**watch（1）**：CL-005a（redelivery 队列无界/failureAttempts cap=0）。
### WI 4.5 nop-search-runner-autotest（CL-001~012：编号 12、并入工单 2、独立线索 1、关闭确认 1、移交 8、驳回 0；逐条归属以 merge-adjudication.md §八 权威表为准——closure audit 发现本小节正文误贴后按 §八 改写）

**并入工单（2）**：CL-007→I-C1-008（async 命名同步语义，同一 SPI javadoc 义务单 diff）、CL-010→MA-01（随 C6-002 治理收敛）。**独立线索台账（1）**：CL-002（BuildIndexTool 全文件读入，C5 面）。**关闭确认（1）**：CL-004（dbtool 零主源码核对）。**移交（8）**：CL-001/003/005/006/008/009/011/012。
### WI 5.1 nop-auth-credential（CL-001~017：编号 17、并入工单 3、独立线索 6、移交 8、驳回 0；全表见 merge-adjudication.md §八）

**并入工单（3）**：CL-005→MA-02、CL-007→MA-01、CL-012→MA-01。**独立线索台账（6）**：CL-001/002/003/006/014/016。**移交（8）**：CL-008/009/010/011/013/014/015/017。

### WI 2.2 nop-core-framework（CL-001~021：

### WI 5.2 nop-sys-dyn（CL 编号实占 0——R1 预注周期任务族候选确认但 Error 绝缘已自备，残余面归 I-05/I-09/MA-07；C3/C5/C9 零命中关闭；C7 并入 I-05）

### WI 5.3 nop-file-datav（CL-001~025：编号 25、缺保护登记 5 条覆盖 18+7=25 闭合；逐条见 merge-adjudication.md §九）

### WI 5.4 nop-report-format（CL 前缀 `nop-report-format-CL-0NN` 声明零实占——四维初审/深挖未产出维度集外线索，overlay 面 C2/C3/C4/C7/C8 零候选；无 CL 台账行）

### WI 5.5 nop-rule-metadata（CL-001~003/101~102/201~206/301~303/401~404/501~504/601~604 分段编号 26 条：26/26 确认 0 驳回；归并去向——CL-205=CL-501 同点归并、CL-601 与 C1-101/C5-102 同族归 W-13、CL-604 与 C5-R2-001 归 W-23、CL-504 watch-only；逐条见 merge-adjudication.md）

### WI 5.6 nop-graph-duckdb（CL-001~002/101~103/201~210 分段编号 15 条：15/15 确认 0 驳回；归并去向——CL-001 归并 CS-001 别名、CL-201 并 C6-002 族、CL-203 后果并 C6-003、CL-204 作 CS-004 族根因附注；逐条见 merge-adjudication.md）

### WI 6.1 nop-tools-parallel（CL-001~003/101~102/201~202/301~302 分段编号 9 条：9/9 确认 0 驳回；归并去向——CL-301 并入 C1-001、CL-202 闭环至 C9-002、CL-101 与 C9-001 去重转 C2、CL-102 转 C5；逐条见 merge-adjudication.md）

### WI 2.2 nop-core-framework（CL-001~021：


**升格立案**：CL-001→C1-021（P1）。**落格**：CL-002→C2-005（同站点 computeIfAbsent bin 锁内 HTTP 下载/类加载/start）、CL-018→C4-010（BeanScopeImpl 哈希序销毁同站点）。**同位点并入设施**：CL-008→WI 2.1 C6-011、CL-020→WI 2.1 C6-003。**零立案**：CL-009（PluginManager syncGet 调用线程内联终判）。**纯移交（7）**：CL-013/CL-019→nop-kernel（CL-013 为 2.1 未登记新面 registerCleanup）、CL-021→安全线（OsUserHelper 明文口令入进程参数）、CL-016→api-core 功能缺陷轨（PlaceholderConfigReference 未替换占位符返回）、CL-010→插件功能线（子容器不经 PluginClassLoader 加载——热卸载隔离失效）、CL-011→C5 面（DefaultShellOutputCollector 无界收集）、CL-012→C7 面（ChangeSubscriptions 空条目不摘）。**关联保留（8）**：CL-003/005/007/014/015/017 及其余——一句话归属见审计目录 merge-adjudication.md。

## P2 裁决台账（7.1 逐项裁决）

> P2 默认不在修复范围；7.1 逐项裁决为「修（in-scope）/ deferred（必须带 Why Not Blocking + successor 指针）」；已确认 live 的 must-fix P2 不得降级 deferred。

| Finding ID | 报告 | 单元 | 一句话描述 | 裁决 | Why Not Blocking / 批次 |
|-----------|------|------|-----------|------|----------------------|
| （无） | | | | | |

## 使用说明

- Finding ID 全局唯一，格式 `{单元标识}-C{N}-{序号}`，如 `nop-stream-core-runtime-C1-001`；单元标识取 roadmap 工作项中的单元名（归档目录名一致）。
- 报告目录命名：`ai-dev/audits/{YYYY}-{MM}/{YYYY-MM-DD}-{HHMM}-deep-audit-{单元标识}-concurrency/`，每维度一个文件（`{C编号}-{英文短名}.md`）+ `summary.md`。
- 审计工作项的 done 门控 = 审计目录完整 + summary.md 归档 + 本索引已更新 + 独立 closure audit PASS。
- 与其他 campaign（security / full-code-review / nop-stream-independent-audit）发现重叠时，先完成修复方为准，另一方在此登记 cross-reference 后关闭，不重复修复。
