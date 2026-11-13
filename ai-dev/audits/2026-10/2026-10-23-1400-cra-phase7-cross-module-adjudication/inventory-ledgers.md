# CRA Phase 7.1 盘点——CL 域留档 / 家族关联链 / 历史承接 / 批次约束 / 已知差 盘点台账（子代理 C）

- 日期：2026-10-23；Status: open（Phase 7.1 跨模块裁决输入，裁决消费后收敛）
- 工作仓库：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`
- 输入（只读）：`ai-dev/audits/cra-index.md`（363 行全文）、WI 1.6/1.7/2.1/2.2/2.3/2.4/2.5/3.1–3.4/4.1–4.5/5.1–5.6/6.1–6.5 各单元 `merge-adjudication.md`、`ai-dev/backlog/security-audit-roadmap.md`、`ai-dev/backlog/full-code-review-roadmap.md`、`ai-dev/backlog/nop-stream-independent-audit-roadmap.md`
- 口径：CL 数据权威 = 各单元 merge-adjudication 权威表（cra-index 小节仅为登记视图）；家族链与约束以 cra-index 计数口径块 + 各裁定跨单元节的并集登记；本文不新增裁定，只做盘点与差异登记。

---

## §一 CL 域留档与 7.1 候选清单

### 1.1 重点条目（任务点名的域留档 / watch-only 附升级条款）

| CL | 单元 | 一句话 | 登记去向 | 显式标记 |
|---|---|---|---|---|
| 6.4 CL-001 | nop-code-rest | JavaFileAnalyzer 构造器每 analyze 重建 CombinedTypeSolver+JavaParser（C9 分配放大热路径） | cra-index WI 6.4 CL 小节；6.4 裁定 §三 | **7.1 消费**（C9 域留档 7.1 候选，量化归 C9 口径；测试先置=C10 W-03） |
| 6.4 CL-002 | nop-code-rest | Go/Rust/CSharp ImportResolver 每 import O(n) 线性扫项目文件集（同族六 resolver 三档复杂度并存） | 同上 | **7.1 消费**（C9/C5 域留档；立案则 F3 同族三连修复；测试先置=C10 W-04 无先置条件） |
| 6.4 CL-102 | nop-code-rest | 四分析器 nodeText() 每调用全文重编码 UTF-8（Python 12/CSharp 11/Go 19/Rust 17 站点；TS 分析器为同族正确参照） | 同上 | **7.1 消费**（C5/C9 域留档，预期 P2 性能候选；测试先置=C10 W-02） |
| 6.4 CL-101 | nop-code-rest | CSharpCodeFileAnalyzer 实例字段承载 per-call 状态（六分析器同族孤例，理论级 C1 脆弱） | 同上；6.4 裁定 §三/§四.3 | **watch-only 附升 P1 条款**：analyzer 缓存化或跨文件并行复用接线即升 P1，先置=C10 W-01（assertNotSame 双调用断言+CSharp 交错并发断言，今日全缺）；与 6.3 W-02 反向约束联动（见 §四.2） |
| 6.4 CL-003 | nop-code-rest | nop-treesitter Language.java 单调 max/幂等记忆化（C1 良性竞争，跨单元） | 6.4 裁定 §三 | watch-only，与 6.2 CL-205 同站点 cross-ref 收口（两单元独立判定一致良性），**不另立台账项** |
| 6.5 CL-004 | nop-peripheral | DynamicJsLoader 资源加载路径同步等待 JS transform future（C3/C9 弱信号） | cra-index WI 6.5 CL 小节；6.5 裁定 §五 | **7.1 消费**（独立线索·域留档 C3/C9 域 7.1 聚类候选；与 W-01 修复联动注记——W-01 修复后阻塞放大面收敛，异步链同步化形态独立待核） |
| 6.5 CL-301 | nop-peripheral | nop-idea-plugin Gradle 独立构建不入 `./mvnw test` 门禁（campaign 首报） | 同上；6.5 裁定 §五/§六 | **7.1 消费**（独立线索·治理轨 7.1 聚类候选）+ 附工单验证通道约束（W-02/W-03/W-05/W-07 四站点，详见 §四.1） |
| 6.5 CL-202 | nop-peripheral | BreakpointManagerImpl.toSourcePath 写共享 SourceLocation | 6.5 裁定 §五 | watch-only（与 C1 载体 SourceLocation 行同案；平台 SourceLocation 契约收紧时两处一并复查） |
| 6.3 CL-201=CL-301 | nop-code-index | indexLocks 持锁 remove 竞态（两维复核确认强信号；本轮四维未含 C2 且不新增发现） | cra-index WI 6.3 CL 小节；6.3 裁定 §六.5 | **7.1 消费**（C1/C2 域登记级，7.1 可作 P2 候选线索） |
| 6.3 CL-004 | nop-code-index | per-indexId 表无淘汰 | 6.3 裁定 §六.5（域留档登记级 6 之一，随 W-37 注记） | 维持留档（watch） |
| 6.3 CL-005 | nop-code-index | OrmFingerprintStore 依赖 withIndexLock 正确性 | 同上（随 W-38 注记） | 维持留档（watch） |
| 6.3 CL-206 | nop-code-index | FlowDetector 双缓存独立驱逐（降级非损坏） | 同上 | 维持留档（watch） |
| 6.3 CL-302 | nop-code-index | detectFlows 无锁 delete+save 竞态 | 同上（C1/C3 域登记级） | 维持留档（7.1 可聚类） |
| 6.3 CL-305 | nop-code-index | OrmFingerprintStore O(N) 点查（C9 域） | 同上；6.4 裁定 §三（与 6.4 CL-001/CL-102 同属 C9 域「重复分配/放大」候选族——域族关联注记不合并条目） | 维持留档（7.1 与 6.4 两条同域聚类） |

### 1.2 各单元独立线索台账（维持留档，7.1 裁决修 / deferred）

| 单元 | 独立线索（CL 或线索桶） | 权威出处 | 登记去向 / 备注 |
|---|---|---|---|
| 1.6 | CL-001~007 确认 7 条：CL-001（完成期记账失败族，关联 C3-006/C8-012）、CL-002、CL-003、CL-004（P1 级建议，终裁留协调方）、CL-005（与 C8-003 同位点）、**CL-006（留 rpc/资源单元终裁）**、CL-007（与 C3-015 修复同批） | 1.6 裁定 §5.5 | 单列不计入 74；CL-006 为跨单元待办 |
| 1.7 | 确认台账 35 条（编号 39、驳回 4：CL-007/009/015/038）；净独立行动项 ≈25~27（并入标注 8：CL-008/016/017/021/022/024/026/027；同族合并口径 2：CL-027→CL-006、CL-030→CL-011） | 1.7 裁定 §四 | 单列不计入 51；锁内 I/O 族（CL-012/016/020/036）合并修复面 |
| 2.1 | 确认台账 35 条（编号 36、驳回 1=C1-CL-001；含 11 别名归并、4 移交 nop-graph/nop-stream/rpc/api-core）；转域移交 1（JsonWhitelist 三登记→功能/安全域） | 2.1 裁定 §五 | 见 §三 移交清单 |
| 2.2 | 关联保留 8（CL-003/004/005/006/007/012/014/017）+ 纯移交 7（CL-010/011/013/015/016/019/021）+ 零立案归档 1（CL-009 PluginManager syncGet 零缺陷终判维持） | 2.2 裁定 §六 | 升格 1（CL-001→C1-021 P1）、落格 2（CL-002→C2-005、CL-018→C4-010）、同位点并入设施 2（CL-008→2.1 C6-011、CL-020→2.1 C6-003） |
| 2.3 | 独立确认线索 14（CL-001/006/007/008/009/012/013/014/016/017/022/023/024/025）+ 升格 3（CL-003→C8-005、CL-010→C9-005、CL-015→C9-012）+ 并入/随工单 5 + 驳回 1（CL-011）+ 空号 1（CL-021） | 2.3 裁定 §5 | CL-022（JDK Delayer 11+1 站点）为跨单元移交轮源登记；CL-024 单元外双检锁 7 站点移交各审计单元；CL-025 平台原语前置（C3-010/C4-003 修复先决） |
| 2.4 | 独立行动 10（CL-300/301/302/403/408/410/500/501/503/504）+ watch 6（CL-411/412/413/414/415/602）+ 移交 4（CL-401/402/600/700）+ 承接 8 + 收敛 5 | 2.4 裁定 §六 | CL-415（SqlDataTypeMapping.register watch）为 3.2 C1-006 修复方向对照判例 |
| 2.5 | 独立线索 10（并入工单 16/独立 10/驳回 0，共 26 条） | 2.5 裁定 §八 | 逐条见其 CL 线索处置表 |
| 3.1 | 独立线索 29 = 4 个归并桶（CL-IND-01 DB 自建表保留策略族 6 条 / CL-IND-02 javadoc 并发契约失真族 / CL-IND-03 正则重复编译清扫族 / CL-IND-04 超时须作用于所有等待点）+ 20 条散项（CL-002/003/004/017/020/021/022/033/045/048/053+068/054/055/056/060/073/074/075 等）+ watch 2（CL-031/CL-035 附转立案触发条件） | 3.1 裁定 §八.2/§八.3 | CL-075 移交功能正确性线；CL-022 与 WI 2.3 CL-022 编号巧合不同案（防混淆登记） |
| 3.2 | 独立线索 4（CL×20 = 并入 16/独立 4/驳回 0） | 3.2 裁定 §八 | — |
| 3.3 | 独立线索台账 10（CL-007/008/011/013/016/018/019/020/021/022）+ 驳回 1（CL-015 UK 实证证伪）+ 重复关闭 1（CL-023↔WI 2.3 C3-003）+ 纯移交 1（CL-009→2.1 CL-021 载体轮）；7.1 移交项 7.1-A（6 张 P3 逐项裁决）/7.1-B（CL-022 观测 vs 安全归属）/7.1-C（CL-021 前置核实=条件升级触发器）/7.1-D（C10-001/002/003 回归面立项）/7.1-E（判级分歧显式空） | 3.3 裁定 §五/§八 | — |
| 3.4 | 独立线索移交 9 主登记：CL-001（→C2 定审）、CL-005（→C9）、CL-006+CL-007（→C9 合并）、CL-009（→C9）、CL-011（→C5）、CL-012+CL-004（→C5/C7）、CL-013+CL-002（→C7/C2）、CL-015（→C7）、CL-016（→owner/功能正确性批次） | 3.4 裁定 §五.2/§八 | O-1~O-4 四项开放裁定留 7.1 |
| 4.1 | 独立线索 4：CL-007（doStop 串行 cancel N×6s 停机预算，7.1 决定立案与否）、CL-017（Pulsar failureAttempts 只增不删，条件面 P3）、CL-021（并入 CL-017 同一线索）、CL-025（MessageConsumeFailureBridge 无 pending 可观测）；移交 5：CL-003/006/010/018（→WI 1.2 C4-004/MA-06 globalWorker 占用面）、CL-024（→消费线程 IContext 传播专题，7.1 指定归属） | 4.1 裁定 §五-8/§八 | — |
| 4.2 | 独立线索台账 7：CL-001/002/008（C9 rpc-cluster 分配面桶）、CL-010（HazelcastProvider 生命周期）、CL-019（Sentinel 基数无上界）、CL-020（Sentinel per-node 语义未声明→owner doc 收口 §五-6）、CL-021（实例事件风暴）；watch 3：CL-013/015/018 | 4.2 裁定 §八 | 重复登记勘误 4 组（复核 1 组 CL-003=CL-008 + 本裁定新发现 2 组 CL-007=CL-001、CL-016=CL-002 + 同桶扩容 CL-012→CL-008） |
| 4.3 | 独立线索 12（CL-004/012/013/014/016/017/019/020/022/023/024/033）+ 移交 5（CL-011/018/025/031/034）+ 并入 7 + 重复合并 10 | 4.3 裁定 §八 | CL-034 netty close 语义核实→C7 维度，核实为缺陷则升格立案 |
| 4.4 | 独立线索 5：CL-001（computeIfAbsent 锁内 I/O，关联 1.2 MA-04 同族异站）、CL-002、CL-003（C5 OOM 候选）、CL-006（webClients 无 TTL）、CL-008（isResponseSent 两宿主不对称，受 RP-1 反向钉死约束）；watch 1：CL-005a（拆分后幂等冗余面） | 4.4 裁定 §八 | 并入 3：CL-004→C1-001、CL-005b→C6-004、CL-007→C4-002 |
| 4.5 | 独立线索保留 1：CL-002（BuildIndexTool 全文件读入，C5 面，admin 触达条件化随 C10-001 容量腿观察）；移交 8（见 §三） | 4.5 裁定 §八 | — |
| 5.1 | watch 台账 4：CL-001（Vault 吞异常丢 cause，可观测性）、CL-002（reencryptAll javadoc-事务语义不符，随 MA-01 评估拆事务）、CL-009（setTtl syncGet 阻塞，C9 面）、CL-010（每请求反序列化，C7/C9 面） | 5.1 裁定 §六 watch 行/§八 | ⚠️ cra-index 5.1 小节与口径块的 CL 分布（并入 3/独立 6/移交 8）与 §八 权威表（并入 10/重复合并 2/升格 1/watch 4）矛盾——以 §八 为准，见 §五.4 |
| 5.2 | CL 编号实占 0——R1 预注周期任务族候选确认但 Error 绝缘已自备（三周期任务 try/catch 全包），残余面归 I-05/I-09/MA-07 | 5.2 裁定 §八 | overlay 五形态处置：C4 全落立案面、C5/C3/C9 零命中关闭、C7 并入 I-05 |
| 5.3 | watch-only 2：CL-007（Semaphore 占池，先量化后立项）、CL-018（ChatBI 崩溃窗口，可声明豁免出口）；其余 23 条随工单（CL 编号与 25 发现 1:1 别名） | 5.3 裁定 §八/§九 | — |
| 5.4 | CL 前缀声明零实占（四维零维度集外线索） | 5.4 裁定 | 无 CL 台账行 |
| 5.5 | 26 条确认处置：随工单注记 10 + 已声明边界关联 2（CL-404/CL-504 watch）+ 移交 10（CL-201→nop-job 批次 watch、CL-206→维度 14、CL-301/001/002→C2 候选、CL-302→正确性/F2、CL-303/603→C6、CL-503→C8、CL-102→C3）+ 独立线索台账 2（CL-402 manifest O(R×E)、CL-602 find-then-increment UK 兜底假失败）+ 对账输入 1（CL-003）+ watch 确认 1（CL-101 锁注册表） | 5.5 裁定 §六.5 | — |
| 5.6 | 15 条处置：随工单注记 6 + 维度移交/留专项 5（CL-101→C3/C7、CL-102→C9、CL-103→C1/C7、CL-002→C2 专项、CL-202→功能正确性维度）+ 留 owner 裁定 1（CL-209 DuckDB JDBC 零 queryTimeout，对照 F5）+ 微观测记 3（CL-205/207/208）；另 WI 2.1 移交项 C6-CL-031 闭环（见 §三） | 5.6 裁定 §六.5/§二.2 | — |
| 6.1 | 9/9 确认；归并去向——CL-301 并入 C1-001、CL-202 闭环至 C9-002、CL-101 与 C9-001 去重转 C2、CL-102 转 C5（域移交） | 6.1 裁定 | — |
| 6.2 | 19/19 确认（撞号重编 CL-206）；CL-401 并 W-02、CL-501 并 C1-104、CL-502 并 C1-103、CL-504 并 W-02 族、**CL-403 移交查询引擎 owner** | 6.2 裁定 §六.5 | — |

### 1.3 汇总口径

- 显式标注「7.1 聚类候选 / 域留档供 7.1 消费」：6.4×3（CL-001/002/102）+ 6.5×2（CL-004/CL-301）+ 6.3×2（CL-201=CL-301、CL-302 域登记级）+ 3.3 7.1-A~E 五通道 = **12 条/通道**。
- watch-only 附升级/触发条款：6.4 CL-101（升 P1）、3.3 7.1-C（CL-021 条件升级触发器）、5.6 W-12（CS-R2-004 装配激活升 P2，实体侧）、6.1 W-06（取消族嵌入前提回升 P2，实体侧）、6.5 CL-202、5.1 CL-016（MA-05 条件化回升，实体侧）等。
- 其余独立线索台账维持留档：按单元分布见表 1.2，合计约 **170+ 条**（1.7 约 27 + 2.1 约 19 + 2.3 约 17 + 2.4 约 10+6watch + 3.1 约 31 + 3.2 约 4 + 3.3 约 10 + 3.4 约 9 + 4.1 约 8 + 4.2 约 10 + 4.3 约 12 + 4.4 约 6 + 4.5 约 1 + 5.1 watch 4 + 5.3 watch 2 + 5.5 约 5 + 5.6 约 9 + 6.x 各单元小量）。

---

## §二 跨单元家族关联链清单

> 成员格式 `单元-发现`；「已记序」= 口径块/裁定中显式给出序数的成员；修复方向为各裁定登记的族级口径（不合并工单，批次对齐）。

| # | 族名 | 已记序成员 | 未记序成员（同族关联登记） | 修复方向关联 |
|---|---|---|---|---|
| 1 | future-永不完成族 | 第 4 例=4.3 C3-001（OkHttp 吞异常）；第 5 例=4.4 C6-007（ContextTaskQueue 吞异常）；第 6 例=6.5 W-01（C1-002 拒绝语义缺失新变体） | 1.6 C8-012、3.2 MA-01（C1-001 ToolManagerImpl）、3.3 MA-05（C3-004/C7-001）、4.2 MA-07（C3-001 ClusterRpcClient）、4.5 C4-001（Future 丢弃变体） | 失败路径必须 completeExceptionally；平台根因绝缘层移交 WI 2.3 CL-018 + WI 2.1 C3-001，族成员修复方案对齐 |
| 2 | socket/fd 泄漏族 | 4.3 C6-001（P0 已修复，下载 fd）、4.4 C6-001（上传流 fd）、3.2 C6-001（沙箱孪生子进程 reader/fd） | 6.5 W-02（socket+recv 线程钉死双后果，族内唯一） | 通道级配对 close；fd 计数回归（4.3 TestFileDownloadSinkClose 先例，4.4 C10-011 复用） |
| 3 | F4 无界账本/零保留族（跨 ≥6 单元） | 5.4 口径：C6-001 泄漏族第 5 例（单元内编号） | 1.1 C1-006、1.2 MA-05（2PC 登记无保留）、1.4 MA-01、1.5 C5-003、1.6 C7-001、1.7 C7-003、3.1 MA-03（引擎终态清理 29 站点）、4.5 MA-01/C6-002（Lucene 三 CHM）、5.1 MA-01（六族 DB 清理）、5.3 CL-013、5.5 W-02（append-only 七表）、5.6 W-01（JVM 进程内账本新变体）、6.5 W-06（suspendedThreads 只进不出）、2.5（五状态机 C7-002 面） | 保留期/清理设施统一批次（5.1 MA-01 / 1.6 C7-001 / 1.7 C7-003 / 5.5 W-02 / 5.6 W-01）；族级纪律=上限+逐出+fail-fast 包络声明（4.5 口径） |
| 4 | 超时族（外部 I/O 链无超时预算） | 4.3 C3-024（SftpClient，同根最近先例） | 5.5 W-01（C7-102 主+C7-001+C4-R2-001）、2.3 C4-007（SSO IdP HTTP）、3.1 C7-001（流式聚合无期限）、3.1 CL-IND-04（直调 chat 无超时包装）、4.3 C6-004（SSE 反压）、5.6 CL-209（DuckDB JDBC 零 queryTimeout，owner 裁定）、3.3 C8-002（orTimeout 锚点） | 单点收口（withConnection/queryTimeout/setNetworkTimeout）；后果层与周期停摆族同型关联非同根因 |
| 5 | Error 绝缘族（周期任务 Error/Throwable 逃逸绝缘缺失） | 2.1 C3-001（平台绝缘层 29 站点/30 点表） | 1.6 C8-001、1.7 C6-001、3.1 MA-07（6 条整族移交 2.1，暂计 P2）、4.1 C3-009（7 屏障清单随单移交）、4.2 C4-004+CL-011（自续链断链新消费端）、5.5 C7-002（W-11，2.1 具体化站点，模块侧 catch Throwable 可先行）、5.2 预注（Error 绝缘已自备→零重复） | WI 2.1 C3-001 平台绝缘层统一批次；3.1 保留 Error 注入护栏义务（C10-008） |
| 6 | 取消通道族（超时取消传播断链） | 3.1 MA-08（C3-011，进程级 kill 通道仅 heartbeat 一处） | 3.1 MA-05（C7-022 成员/spawn 超时 F3 漏修）、3.2 C4-008+C6-009（工具侧结构面双轨）、3.4 O-2（IEmbeddingModel SPI 无 ICancelToken）、4.3 MA-02（JDK 取消语义失效）、6.1 W-06（取消族并卷，嵌入前提回升 P2）、3.1 MA-01（AttemptCancelToken 原语） | 协同点=WI 2.1 api-core 移交包 `FutureHelper.bindCancel` 单点改造（同时服务 2.1 C3-016/C6-001/C3-004 三条） |
| 7 | 停机窗口拒绝/兜底语义族 | 6.5 W-01/W-02 | 2.2 C1-009（close 竞态后重建永不关闭）、1.7 C6-001（Error 逃逸 future 永不完成） | 「生命周期终止语义统一」修复方向，7.1 批次对齐 |
| 8 | 错误以成功交付族 | 第 4 例=4.3 C3-005（无校验源跳过 verifyAndRename） | 3.1 C7-002（流式断连误判成功）、3.3 C3-001（终态冲刷截断）、3.4 C3-005（IND-03，三方 P1 判级先例互证）；对偶面=5.6 W-03（必败掩盖半个成功） | 成功信号与产物落地原子性；族内修复批对齐 |
| 9 | 周期停摆族 | 4.5 C4-001（scheduleWithFixedDelay 周期静默停摆，F9 定向命中） | 1.6 C8-001、5.5 W-01 后果层（RUNNING 挂死期间 cron tick 静默跳过）、1.4 MA-01（rpcPoll 注册表泄漏放大） | 平台级 fencing/自防御/观测统一（1.6 C8-001「修复须平台级统一」） |
| 10 | 平台池占用族（globalTimer/globalWorker/共享池） | 2.1 C4-001（globalTimer 死配置，28 消费方） | 1.5 MA-04（4 线程共享定时器）、2.3 C8-001（WS 推送钉死 globalTimer，ρ=N·L/40）、3.1 C4-011（AgentDispatchExecutor commonPool）、1.2 C4-004/MA-06（Debezium 常驻 globalWorker）、1.7 C8-003（兜底 cached 池）、4.2 C4-003（消费端 opt-in 新站点）、4.1 CL-003/006/010/018（→移交 1.2 MA-06）、6.1 C4-002（invokeAll） | 隔离/卸载方案平台族统一裁决（4.2 §五-5：1.5 MA-04/2.1 C4-001/2.3 C8-001/3.1 C4-011 纳入）；先平台后使用方（3.1 §5.1-2 顺序约束，见 §四.5） |
| 11 | 扫描守卫停摆族（fencing） | 1.7 C7-001（RUNNING 死区） | 1.5 C7-001（MA-23 WfTaskScanner）、1.6 C1-001/C3-003（CAS 守卫/续租）、5.2 MA-01（holderId fencing 纪律）、3.1 MA-19（C7-012 接管锁续期异常无限容忍）、1.7 C7-003 | 平台级统一 fencing 语义（1.7：与 WI 1.5 C7-001/WI 1.6 C8-001 对齐，避免两套 fencing） |
| 12 | 沙箱清理族（超时不 destroy） | 3.2 MA-02/MA-03（P1，按配置可达生产路径判级） | 3.1 MA-17（P2，DockerSandboxBackend——**跨单元判级分歧移交 7.1**：「装配后生产形态」统一）；F3 残留=Host killTree 已修 Docker 未修 | 判级统一前维持各自立案；修复批次协同、护栏测试共享形态（3.1 C10-011） |
| 13 | 不可中断族（外部库零 interrupt × 超时仅放弃等待） | 5.6 W-04（CS-001，CWTS 222 class 反汇编 0 interrupt；2.1 C6-CL-031 别名已并入） | 5.6 W-08（Betweenness，族 cross-ref 不并单）、4.3 C3-002/C6-004（底层 I/O cancel 无效）、3.1 C3-011、5.5 W-01 后果层 | daemon 线程工厂+可中断/有界执行路径；反汇编取证双重复现（CWTS+JGraphT） |
| 14 | F2 半个成功族 | 1.6 C6-003（delete+insert 非原子） | 2.4 C2-013（事务注册）、5.1 I-02（OAuth 刷新）、5.5 W-12（C7-R3-001 wf.start save-then-finish）、5.6 W-03（CTAS 必败，兼 F3 守卫漏配成员） | 归一/补偿/声明三选一口径 |
| 15 | 错误以成功交付族（同 #8，此处不计） | — | — | — |
| 16 | 全局注册表裸写族 | 5.4 C1-002（单元内） | 5.5 C1-002（W-05，JDK 全局 API 形态首例，关联 2.2 C1-007 配置发布三字段裸写）、6.5 W-03（IDE Project 键静态缓存首例，与 5.4 C1-002、2.4 C2-004 异机制同域） | 静态容器生命周期治理（弱键/Disposable/unmodifiable/冻结） |
| 17 | 无界物化/无上界读族（跨 ≥5 单元） | 5.4 C5-001（P1，record 反序列化无界物化） | 1.5 C5-001、1.7 C5-001/C5-002、5.3 W-04、6.3 W-01/W-02、3.3 C8-001（越窗无界缓冲）、3.1 C5-008/MA-06、1.1 C5-010、4.3 CL-013/CL-022、5.5 W-09/W-10/W-15~W-23（§4.1#9） | 三范式：setLimit+fail-fast / 分批 / 列投影；C5-004=1.7 C5-001 同缺陷第二实例 |
| 18 | 缓存失效传播缺口族 | 2.1 C7-007（check-changed 双漏检，与 C9-001/C9-010 同链三面绑定单点修复） | 5.5 W-03（C7-R2-001 规则热更新，使用面依赖钉定粒度缺口）、5.2 I-04（动态模型删除零缓存失效） | 同链上下游：2.1 C7-007 单点修复若涉失效判据须联动 5.5 W-03 验证 |
| 19 | 懒初始化守卫族（DCL/volatile） | 5.5 W-04（C1-102 ensure* 族主，11 字段×8 载体，同族最大失效面） | 5.2 MA-06→5.1 I-04 先例（5.2 §七#10「DCL/volatile 同法」）、5.5 C1-001/C4-001 吸收成员 | volatile/生命周期钩子/构造期装配同法 |
| 20 | 上下文传播专题（消费线程 IContext / ThreadLocal 生命周期） | 2.3 C3-002（invokeOperation 出口泵缺 thenOnContext） | 4.1 CL-024（Local 红投，7.1 指定归属）、4.3 CL-011/018/025（移交 C2/平台上下文传播专题，nop-api-core 面）、2.3 C3-007、4.4（上下文传播族宿主面）、6.5（—） | api-core 批次统一（bindCancel/executeWithThrottling/completeAfterTask 三半边随 2.1 api-core 移交包） |
| 21 | Debezium 双轨族（使用面↔本体面） | 4.1 MA-09（幽灵订阅，使用面） | 1.2 C3-014/MA-07（wrapper 本体面，停止契约无终结保证）、4.1 MA-02（屏障两极性，判级分歧留 7.1）、1.2 MA-06（C4-004 无限阻塞占池） | 使用面/本体面修复批次对齐；REGISTRY 契约一次裁决（4.1 §五-5 禁止各修一半） |
| 22 | 同案双轨互挂（先案定判级基线） | 4.1 MA-10 ↔ 1.2 C3-006（FAILURE_LISTENERS，先案 P2 基线，P1↔P2 留 7.1） | 4.1 C1-001 同案双轨（C1-001 P1→P2 降档对齐）、1.5 C4-003（=2026-09 P2-3.1-001）、2.5 C6-005 双轨、1.7 C4-003↔2.1 C4-003（处置归 2.1） | 两单元同批调整 |
| 23 | 内联消费/队头阻塞族 | 3.1 MA-11（DBMessageService 单线程 poller 内联消费） | 4.1 MA-01（同 SPI 消费等待病，consumeAwaitTimeout 契约对齐 §五-6）、4.1 C8-002（Local 失败断链） | 契约语义对齐后同批调整 |
| 24 | globalWorker 长驻占用（4.1 overlay → 1.2 本体） | 4.1 CL-003/006/010/018 移交 | 1.2 C4-004/MA-06 本体面（缺省池容 30） | 观测子项随 CL-003；占用本体面在 WI 1.2 |
| 25 | 双实现分叉族（BufferedStreamingPublisher） | 2.3 C3-003（背压断裂，主实现） | 3.3 FailoverStreamFlow（孪生，同文件修复批 A 四工单）；3.3 CL-023 孪生站点重复关闭；6.4 CL-003/6.2 CL-205 良性对照 | 「共享有界缓冲组件」方向；pendingComplete 正确先例在同仓 |
| 26 | 重试放大族 | 2.3 C8-002（429 ×7 加法放大，在途×90s OOM 轨迹） | 4.2 C8-002（MA-07 成员，无时间整形，分档独立成立） | 重试准入上界/时间整形 |
| 27 | shutdown 懒重建族 | 4.2 C1-005（独立工单 #3） | 2.2 C1-009（停机后在途刷新重建连接，P1） | closed 双检+synchronized 化 |
| 28 | 监视器内长操作族（两面分工） | 1.4 C2-001（per-job 监视器内外部 invoker，设施面） | 5.5 C4-R2-001（监视器内占池+suspend/cancel 锁死，消费面后果层随 W-01）、5.5 CL-201（已移交 nop-job 批次 watch） | 设施面/消费面零重复（对齐 2.4「X1 同号异单元互补」口径） |
| 29 | 自续链断链族 | 4.2 C4-004（独立工单 #6）+CL-011/CL-014 | 2.1 C3-001（30 点表 #14）、1.6 C8-001、4.1 C8-002 | 随 WI 2.1 C3-001 平台方案同批 |
| 30 | 订阅生命周期注册/注销配对族 | 4.3 MA-09（putIfAbsent/cancel-remove 正确先例对照） | 4.1 MA-05（DebeziumMessageSource 幽灵订阅） | 修复对齐 4.3 正确先例 |
| 31 |PubSub 断链族（check/nosql-cdc 2026-08-19 前案） | 2.5 MA-01（C6-004，peri 侧修复本体） | 4.3 MA-01+C6-028（MQTT 面兄弟，非同站点）、2.5 C7-014（lettuce 重连订阅全丢） | 家族关联单列；属主校验/停机对账模式 |
| 32 | ID 工厂/init 非幂等族（SimpleRpcClientFactory 面） | 4.3 MA-07（C1-001，工厂 init 挂在 newInstance 非幂等） | 2.1 CL-024（原始站点，设施面）、6.5 W-02（使用方配对缺失，同设施类不同缺陷面） | 使用方↔设施两面切分，防双计不防登记 |
| 33 | C4-003 使用方↔设施两面 | 2.1 C4-003（工厂校验面 F3 明列 JavaScriptService.java:40） | 6.5 CL-203（移交本体归 2.1，cross-ref 防双计）、6.5 CL-002（消费侧 taskQueue 无界归 W-01）、1.7 C4-003（同设施同面双登记，处置归 2.1） | 使用方↔设施映射模式（2.1 §五 15 行映射表） |
| 34 | IDE 插件项目泄漏族（首例） | 6.5 W-03（IntelliJ Project 键静态缓存） | — | 弱键/Disposable 二选一（平台惯例） |
| 35 | destroy-vs-rebuild 族 | 4.5（口径块登记） | — | 家族关联单列 |
| 36 | F6 落盘族 / TOCTOU 相邻族 / 聚合弃全对偶族（6.1 特色三组） | 6.1 口径块 | FFM×外部截断 TOCTOU（C1-R2-001 同机复现）、invokeAll 首错弃全（C4-002） | 单元内 4 组零同站点重复 |
| 37 | F2+F3 对偶族（5.6 特色） | 5.6 口径块 | W-03（CTAS，F2+F3 双形态）+ readExternal 守卫正确兄弟 | ingest fail-fast 范式（TABLE_EXISTS+bizFatal） |
| 38 | 泄漏族（5.4 单元内记序第 5 例） | 5.4 C6-001（单元内编号，与 5.6 C6-001 同名异站——防混淆注记） | C9-002 锁内 I/O 族第 4 例（5.4 口径） | — |
| 39 | 会红=0 同型链（测试保护缺口序列，非缺陷族） | 已记序：4.5=第 4、5.1=第 5、5.2=第 6、5.4=第 7、5.5=第 8（形态升级：3 真并发测试只护历史修复）、5.6=第 9、6.1=第 10（「部分保护」特色）、6.2=第 11（反向保护 4 处新高）、6.3=第 12、6.5=第 13 | 第 1~3 例未显式记序（4.2「17 测试文件零真并发测试」/4.3「测试栈结构性单线程化」为形态源头描述）；5.3 落盘未记序；6.4 零缺陷面盘点形态不计序；3.2「9/9 P1 全部无测试」 | 7.1 测试基建聚类输入（6.5 CL-301 治理轨、6.4 双契约 watch、5.5「测试基建只向后看」） |
| 40 | 同号异物防混淆登记对 | 3.1 CL-022 ↔ 2.3 CL-022（编号巧合不同案）；4.3 CL-022 ↔ 2.3 CL-022（同号异物无关联，orTimeout 两树 0 命中实测）；5.4 C6-001 ↔ 5.6 C6-001（同名异站）；2.2 CL-021 ↔ 2.3 CL-021（同号混写勘误，见 §五.9） | — | 各对均已双向显式登记 |
| 41 | 2026-09 G 系列承接对（详见 §三.1） | G10-01-02/03（6.2）、G11-15-02（6.3）、G11-03-01（6.4 已修） | — | 三态裁定闭环回填 |
| 42 | 单元内同族协同组（非台账编号，7.1 批次参考） | 5.5 G1 外部 JDBC 治理链（W-01×W-07×W-14）/G2 retention×reJudge 同表契约/G3 告警通道三失效层/G4 无界物化四范式/G5 ensure*；6.2 批次约束 4 组（W-03+W-10 同点、W-01+W-11+W-12 同批失败出口、W-13+W-14 同批、W-06/W-17+W-18 随 L2 装配接线）；2.2 §8.4 七个修复批次（插件生命周期/IoC 启动图/配置刷新链/JdbcConfigSource/ShellRunner/VTE 治理/销毁链）；2.4 §7.4 收敛对照 12 组 | — | 同族修复批顺带闭合，不并单 |

> 计数：上表 42 组（含 3 组防混淆/承接索引对），覆盖 cra-index 31 个计数口径块登记的全部「家族关联 N 组/处」与各裁定 §四/§七 家族节。零同站点重复立项为各单元查重一致结论（仅 3.3 CL-023 一处重复关闭、1.7 C4-003 一处双登记归 2.1，均已有 cross-ref 处置）。

---

## §三 历史承接与跨 roadmap 重叠

### 3.1 G 系列承接项终态（2026-09-30 全仓审计 `2026-09-30-1726-deep-audit-nop-entropy-full/`）

| 承接项 | 源 | 承接单元 | 终态 | 登记位置 |
|---|---|---|---|---|
| G10-01-02（LanguageRegistry 裸 HashMap，P3） | `10-lint-refactor-jq.md` | WI 6.2 nop-tools-parse | **仍成立 → 并入 nop-tools-parse-C1-104**（W-04，P2 裁决台账；P2 超集，补强并发防护与调用面证据） | cra-index L87/L89 回填块；6.2 裁定 §六.3 |
| G10-01-03（rename/rewrite 每请求 ServiceLoader 重复发现） | 同上 | WI 6.2 | **仍成立 → 并入 CL-204**（C9 形态线索） | 同上 |
| G10-01-01 | 同上 | WI 6.2 | 非并发，维持原轨 | 同上 |
| G11-15-02（SymbolTable 读未同步 × CodeCacheManager 数据竞争，P2） | `11-code-graph.md` | WI 6.3 nop-code-index | **仍成立（未修复）→ 并入 nop-code-index-C1-001**（P2 超集/扩面复活：失效面扩至 36 读站点×5 读类）；关闭为 C1-001 别名，单一修复工单防双计数 | cra-index L91/L93 回填块 |
| G11-03-01（Go/C#/Rust adapter 未装配，P1） | 同上 | WI 6.4 nop-code-rest | **已修复闭环 @df4e4f8fa7**（六语言 adapter bean 接线；C1 复核独立核读 app-service.beans.xml L11-16 确认）——非「仍成立」非「并入工单」 | cra-index L95/L97 回填块；6.4 裁定 §二 |
| G11-09-03（JavaFileAnalyzer 降级日志） | 同上 | WI 6.4 | error-handling 域非并发，不入圈维持原轨 | 6.4 裁定 §二 |
| 2026-09 G 系列 13 文件对 6.5 四模块 | `07`~`12` 各文件 | WI 6.5 | 零命中（复核 grep 证实）——无承接项 | 6.5 裁定 §七 |

### 3.2 移交他单元项状态清单

| 移交项 | 源→目标 | 状态 |
|---|---|---|
| 2.1 C6-CL-031（LeidenDetector 非 daemon+无 awaitTermination） | 2.1 → WI 5.6 | **已闭环（2026-10-22）**：三态裁定=边界变化→并入 CS-001（awaitTermination 属负修复，CWTS 全库零 interrupt 检查点）；关闭为 nop-graph-duckdb-W-04（CS-001，P1）别名；2.1 台账已回填（cra-index L107；5.6 裁定 §二.2 为终局权威） |
| 2.1 api-core 移交包 5 项（C2-016 全条 / C2-CL-019 / C3-016 半边 / C3-007 半边 / C3-005 半边；协同点 FutureHelper.bindCancel） | 2.1 → api-core 治理 plan | **开放**：`io.nop.api.core.*` plan-first 保护区；消费方=1.6（C8-012 同族）/4.3 C3-012/3.4 O-2 等列入回归面 |
| 2.1 CL-021（12+ raw JDK/Netty 载体绕过设施，点名 nop-stream/ai-agent/feishu） | 2.1 → 各单元 | 承接确认：3.1 C3-009/C3-012 具体化（修复批次对齐，不离族）；3.3 CL-009 纯移交（feishu 载体）+C4-005 handler 使用面保留；4.3 C1-011+MA-04 载体链接（先平台设施后使用方）；4.2 无接收义务（调度站点全经平台设施，闭环）；4.4 漂移复查闭环（宿主零载体） |
| 2.1 CL-024（per-client/topic 池线性累计） | 2.1 → nop-cluster | **闭环**：4.2 R1/R2 双实测 rpc-cluster 零自建池/线程，无同族形态——预期闭环零立案；原始站点实审=4.3 MA-07（C1-001 SimpleRpcClientFactory.init 非幂等，P1 台账在案） |
| 2.1 C3-001 周期任务绝缘层（接收方） | 3.1 MA-07（6 条整族，暂计 P2）/4.1 C3-009/5.5 C7-002/4.2 C4-004/5.3 CL-020 | **开放**（2.1 C3-001 本体待修，P1 台账在案）；3.1 保留 Error 注入护栏义务；4.1 C3-009 修复 diff 归绝缘层批次、7 站点清单随单移交、族内判级统一时可升格 |
| 2.2 CL-013/CL-019 → nop-kernel（2.1 未登记新面 registerCleanup / TaskExecutionGraph 仅 catch Exception） | 2.2 → 2.1 | 开放（映射表 10 行/3 行） |
| 2.3 C9-012（LocalDateTimeFieldMarshaller 线格式，P1） | 2.3 → 功能正确性维度 | 开放——P1 台账已登记（`nop-service-framework-hotpath-C9-012`，修复状态注记「接收轨落名」）；本单元承担修复 58 口径下不计 |
| 2.4 CL-401/CL-402 → api-core/nop-kernel（ContextTaskQueue.syncGet 排空通道 / executeWithContext 并发仅 WARN）；CL-600 → nop-batch/nop-job 同形态复核；CL-700 → nop-commons（IntArray equals） | 2.4 → 各轨 | 开放（跨单元映射 X3/X4/N11） |
| 2.5 C7-012（扫描器单 namespace，P3 已知未修复） | 2.5 → WI 1.2 MA-05 随案 | 开放（跨单元随案 1 条） |
| 2.5 范围外残余（LocalMfaChallengeStore.peek / TOTPAuthenticator 本体） | 5.1 → nop-biz-auth-core 所在单元 | 开放（后续审计输入） |
| 3.1 CL-075（middleware 伪 lifecyclePoint，非并发域） | 3.1 → 功能正确性线 | 开放（线索级移交落名） |
| 3.3 CL-009（FeishuClient 单线程调度器承载重连同步 HTTP） | 3.3 → 2.1 CL-021 载体治理移交轮 | 开放（纯移交，本单元不计入） |
| 4.1 CL-024（Local 红投 IContext 不传播） | 4.1 → 消费线程 IContext 传播专题 | 开放（**7.1 指定归属**） |
| 4.4 C6-007 平台根因绝缘层注记 | 4.4 → WI 2.3 CL-018 + WI 2.1 C3-001 | 注记随迁（future 族口径） |
| 4.5 移交 8：CL-001/011→nop-core 轨（FileWatcher check-then-act / stop() 停共享单例，同文件同批）；CL-005/012→nop-commons 轨（debounce cancel 不 join / get-put 非原子）；CL-003→nop-orm 轨（add/removeDaoListener 契约）；CL-008→nop-dao 轨（SimpleDataSource 生命周期契约）；CL-009→平台生命周期轨（CoreInitialization.destroy 幂等）；CL-006→WI 2.1 C4-001 站点核对（非新缺陷零重复） | 4.5 → 各轨 | 开放（各接收轨均首报，零同站点） |
| 5.5 CL-201（LocalJobScheduler 设施面 watch） | 5.5 → nop-job 批次 watch | 开放 |
| 6.2 CL-403 | 6.2 → 查询引擎 owner | 开放 |
| 6.5 CL-203（JavaScriptService -1,-1 cached 参数语义） | 6.5 → WI 2.1 C4-003 | 开放（本体归 2.1，F3 成员明列 JavaScriptService.java:40，已知未修复；6.5 侧 cross-ref 关闭防双计） |
| 3.4 O-2（IEmbeddingModel.embedAsync/embedAllAsync 无 ICancelToken，SPI 设计层缺失） | 3.4 → nop-ai-core 归属单元（WI 3.1）+ 7.1 协调 | 开放（跨模块公共 API 变更须 plan-first 在归属单元立项） |

### 3.3 跨 roadmap 重叠 cross-reference 清单

> cra-index 使用说明既定规则：与其他 campaign（security / full-code-review / nop-stream-independent-audit）发现重叠时，**先完成修复方为准**，另一方登记 cross-reference 后关闭，不重复修复。

| # | 重叠面 | CRA 侧 | 对侧 roadmap/状态 | 先完成修复方 | 登记位置 |
|---|---|---|---|---|---|
| 1 | 1.5 C4-003（wf 扫描 job.yaml invoker.bean=wfTaskScanner 失配，启用即 ERR_JOB_BEAN_NO） | WI 1.5 立案 | full-code-review：P2-3.1-001/AU-01/SM-10 同案（已知未修复，同级）——该 roadmap Wave 0–3 已全 done（v7 2026-10-10），该 P2 在其 P2 台账 45 行 deferred-watch 清账（R2.0/plan 2409） | 双方均未修；7.1 按同案对齐一处修复 | cra-index L22（1.5 口径块跨单元关联 3 处之一）；1.5 裁定 L260 |
| 2 | 1.5 口径块登记的 AU-10 同案对齐 | WI 1.5 | 2026-09 系列（同 #1 campaign 域） | 同上 | cra-index L22 |
| 3 | 2.2 C2-001（getBean0 锁序环，P1） | WI 2.2 P1 台账 | 历史 check 审计 P2-6 同源（check 系列 939 条处置已由 full-code-review 6.4–6.9 收口闭合）；2.2 裁定 §九登记「判级张力：本轮 P1 vs 历史 P2-6 暂缓」 | CRA 侧 P1 在台账待修 | cra-index L105/L196；2.2 裁定 §九 |
| 4 | 4.3 C6-003（JDK/OkHttp 416 陈旧 Range 重传，P2） | WI 4.3 立案（对齐 09 前案 P1→P2 降档） | full-code-review 3.9 前案（P2 已复核保留 / still-live P2-15 相邻面）——跨 campaign 前案 cross-ref，并轨修复不重复立项 | 双方并轨 | cra-index L69；4.3 裁定 §七-9/L197 |
| 5 | 4.3 C6-001（P0 下载 fd 泄漏，已修复） | WI 4.3 P0 即时通道 | 3.9 前案「文件模式主路径确认关闭」误结论被本轮**证伪**（P0 全绿下存活）——P0 修复在案（回归测试 fd 计数必红） | CRA 已修（本轮提交） | cra-index L69/L122 |
| 6 | 2.5 C6-004/MA-01（PubSub 订阅管理竞态，P1，已知未修复） | WI 2.5 P1 台账 | check/nosql-cdc 2026-08-19 前案（Lettuce PubSub P2×2 未修复）——已知未修复登记 | CRA P1 在台账待修 | cra-index L55/L223 |
| 7 | 1.1/1.2 nop-stream 审计面 vs nop-stream-independent-audit-roadmap（23/23 done，bounded ready：ready only for enumerated e2e-proved capability/environment pairs） | WI 1.1–1.3 全量并发/资源维 | 该 roadmap 审计项 1–23 全 done、修复项 1–3 done（2026-08-09 收口）；其 P0/P1 修复已经独立 closure audit（coordinator/runtime recovery hardening） | 该 roadmap 先行完成修复面；CRA 1.1–1.3 为并发/资源维增量（1.2 C1-001「已知未修复←r4 R5-CEP-05」为唯一显式前案锚，P1 台账在案） | 1.2 P1 台账行 C1-001 注记；1.2 裁定 MA-19（C6-009 有 2026-09-26 审计历史同锚） |
| 8 | 2.2 CL-021（OsUserHelper 口令入进程参数） | 2.2 CL 纯移交 | security-audit-roadmap 安全线（items 1–13 done；item 14 successor-deferred 12 live P2 已转 full-code-review R1.0 P2 台账） | 安全线归属（移交） | cra-index L348；2.2 裁定 §六 |
| 9 | 3.3 CL-022（maskAccount apiKey 前缀+长度入 micrometer 标签） | 3.3 CL 独立线索 | 观测面 vs 安全线归属未定（security 域相邻） | **7.1-B 裁决归属** | 3.3 裁定 §五/§八 |
| 10 | 5.1 Protected Area（nop-auth 认证模型 ask-first；nop-auth.orm.xml plan-first） | WI 5.1 MA-06 | 与 security-audit 对 nop-auth 的管辖面相邻（该 roadmap item 2 已 done） | MA-06 实施前须走 plan audit + ask-first | 5.1 裁定 §九；cra-index L75 |
| 11 | 6.2 EvalExprProvider._executor 全局槽 | 6.2 W-02（P1，消费侧首登记） | 2.1 注册的是 nop-commons GlobalExecutors 设施缺陷——`EvalExprProvider._executor`（nop-core 槽）未被任何单元立案，6.2 为消费侧首登记（非重叠、互补登记） | —（互补非重叠） | 6.2 裁定 §七家族判定 |
| 12 | 4.5 F4 无界账本族（MA-01/C6-002 Lucene 三 CHM） | 4.5 立案 | 台账在案成员 1.1 C1-006/1.2 MA-05/1.4 MA-01/1.7 C7-003/3.1 MA-03（CRA 内部族，见 §二#3） | 族级纪律对齐、站点独立 | 4.5 裁定 §七.3 |
| 13 | 3.2 F3 残留（Host killTree 已修 Docker 未修） | 3.2 MA-02 立案 | 历史修复回归面（Host 侧已修先例在仓） | Docker 侧随 MA-02 补齐 | cra-index L59 |

> security-audit-roadmap 状态摘录：Phase 1 审计 items 1–8 done、合并 item 9 done、修复 items 10–13 done、item 14 `todo`（14 条 successor-deferred 中 12 live P2 + P2-3.11-001 已转 full-code-review R1.0「P2 裁决台账」，3.11/plan 2338 标定完成）。
> full-code-review-roadmap 状态摘录：Wave 0–3 全部 done（V.1 全量回归 454 模块新增失败=0、V.2 修复闭环 8/8 PASS、R1.1–R1.42+R1.6a/b/c 全 done、R2.0 P2 台账 45 行 deferred-watch 清账）。
> nop-stream-independent-audit-roadmap 状态摘录：23 个工作项全 done；readiness 判定 bounded（§2a 4 lane-blocked rows 为唯一 blocker 类，审计范围外）。

---

## §四 批次约束清单

### 4.1 验证通道约束

| 约束 | 来源 | 影响工单/批次 |
|---|---|---|
| idea-plugin 站点回归验证**不得依赖 `./mvnw test`**（Maven 反应器外，`nop-dev-tools/pom.xml:20-24` 不含；「./mvnw test 通过」门禁对其零约束）；通道三选一写入验收标准：① `./gradlew test`（IntelliJ harness）纳入 CI；② 可测逻辑下沉 plain-JVM seam（connector 配对/registrar 引用计数）；③ cra-index 工单行显式登记「验证通道=Gradle/harness」 | 6.5 裁定 §六（CL-301）；cra-index L99/L343 | **W-02（P1）/W-03（P2）/W-05（P2）+ watch 级 W-07 四站点**；P1 台账行 `nop-peripheral-C6-001` 已登记「验证通道=Gradle/harness 非 ./mvnw[CL-301]」；P2 站点涉 idea-plugin 同受约束；Maven 反应器内工单（W-01/W-04/W-06/W-08/W-10）通道不变 |
| 4.3 两模块套件验证 + scan-hollow 门禁（P0 修复先例） | cra-index L122 | TestFileDownloadSinkClose fd 计数回归为 4.4 C10-011 复用先例 |

### 4.2 反向约束注记（跨单元条件触发）

| 约束 | 来源 | 影响工单/批次 |
|---|---|---|
| **6.3 W-02 反向约束**：W-02 修复或任何索引并行化若引入 analyzer 缓存或跨文件复用 → 触发 6.4 CL-101 升 P1，**须先满足 6.4 C10 W-01 测试先置**（adapter 双调用 assertNotSame 断言 + CSharp 交错并发断言，今日全缺，接线失败将是静默数据损坏）；随 7.1 对 W-02 的裁决一并宣读 | 6.4 裁定 §四.3/§四.4（7.1 输入三类之二） | 6.3 W-02（nop-code-index-C5-002，P1）+ 任何触碰 nop-code analyzer 接线的批次 |
| 6.4 双契约 watch-only 脆弱性：任何触碰 analyzer 接线的批次须先满足 C10 W-01 | 6.4 裁定 §六.4 | 同上 |

### 4.3 反向钉死（修复 diff 必须先改/同改测试；凡口径块出现「反向钉」「反向保护」全量）

| 单元 | 数量 | 明细与捆绑工单 |
|---|---|---|
| 3.1 | 4 项 | gate3 测试字面钉 remove no-op（C10-003 F4 断言止于 checkpoint 单件+钉不完整清单）；C7-014 与 R4 反向钉死改写同批 |
| 3.2 | 1 钉 + 1 watch | 反向钉-1：ShellIOTest:294-300 队满 close——MA-05 put 变体须同批改写（变体选择属工单设计非 7.1 事项）；反向钉 watch：TestToolPathQueue:203-228 pathless 措辞（随 C1-007 工单与 C10-023） |
| 3.4 | 1 | intern assertSame（C10-008，TestCodeFileInfoStructure L69/72/74 分级改写，低基数面保留）——修复 C1-008 必红，**不解除则 C1-008 不可修**；解除为 IND-11 同工单强制测试改造项 |
| 4.1 | 5（4 硬+1 软） | #1 sendAfterDestroyIsTypedRejectedNotNpe（:459-467）→C3-004 同批；#2 batchReceivePolicyNotWiredWhenUnconfigured（:442-456）→MA-08；#3 TestDebeziumEngineProperties :181/187/207/218 四处活 map setup→C1-012；#4 SysDao:617+3 测试→C1-008；软钉 C1-002 setOffsets 子面。全部为**强制前置联动（先改测试再修本体）** |
| 4.2 | 4（2 硬钉） | 硬钉 #1 testSupplierSyncThrowPropagatesSynchronously→MA-05（C3-007）同 diff 解除并以「rejected future+异常统计」新断言钉定；硬钉 #3 TestWeightedPartitionAssigner.testZeroAndNegativeWeight:251-270（钉定 0/-50→各得 100 缺陷契约）→MA-08（C1-006）同 diff，改写以 owner 零权重裁决结论为契约（§五-3）；另结构性假保护 LeastActive 恒 0 adapter→独立工单 #7 同 diff 强制项 |
| 4.3 | 5 | 2 断言级 MQTT assertThrows 同案（RP-1 随 MA-15 先改测试，4.2 先例）+ Windows 门控排除 P0 唯一确定性症状（C6-001 面）等；**先行失败测试 16 处**（复核勘误 R5 口径）为各工单验收物 |
| 4.4 | 4（1 钉死型+3 掩蔽型） | RP-1→CL-008 线索（改 ended()/closed() 形态须同 diff 更新 TestVertxHttpServerContextAdapter）；RP-2/RP-4→C4-002 工单（stub 须可抛错+取消断言先于修复）；RP-3→C4-004 工单（须新增不预初始化测试上下文，CoreInitialization.java:118 兼容已核）——总约束：禁止修码留钉 |
| 4.5 | 0 硬钉+1 正向修复约束 | S-04 正向约束；修复陷阱：locallyOwned 测试不拦 footgun 修复变体 |
| 5.1 | 1 | C10-007 反向钉死测试改写是 **I-02 同 diff 前置**（修复须先改 TestCredentialOAuthRefresh.refreshFailureFailsClosed） |
| 5.2 | 1 | TestInMemoryCodeCacheContract（:177-194）——MA-02 修复同 diff 改写；I-01 COW 不触红 |
| 5.3 | 1 | 钉死无 fencing 语义：W-02 必须同 diff 改写 testSlowExecutionRaceWorkerSuccessOverwritesScanFailed（钉死测试反转） |
| 5.5 | 1 | C1-002→W-05：TestMetaDataSourceConnectionSecurity.java:776-792 断言全局 setLoginTimeout==5，修复须同 diff 改写（改断言连接属性路径）；W-01 超时族修复（setNetworkTimeout 连接级）不触红该测试 |
| 5.6 | 0 | 反向钉定 0（两点边界注记：#19 直接 new 装配为假保护/固化形态注记非反向钉定；契约锚为正向回归锚） |
| 6.1 | 0 | 反向钉定 0（fuzz 钉死行为正向回归锚，修复后测试仍应通过） |
| 6.2 | 4（反向保护，campaign 新高平历史高点） | #2→W-02（P1）：TestLintDeadlineExecutor:84-85「install must own the global executor slot」钉定全局槽设计，**Route-B scope 传播方案必须重写该测试类（锁定 P1 修复路径）**；#8→W-08：TestNopRefactorBizModel:200-202 反钉非原子语义；#9→W-13：ThreadLocalRetentionTest:79 钉定池化常驻为设计目标；#11→W-15：SubtreeArenaTest:30-59 钉定 free 机制语义（修复取删死代码须同步删测试）。全部「按三维建议修复将使现有测试红」硬冲突，修复工单必须捆绑测试改写；另 hang-once-then-ok=W-01 时序敏感假保护 |
| 6.3 | 0 | 反向钉定 0（TestRebuildFromCommit 反射 resetDebounce 掩蔽为掩蔽形态非反向钉定；TestProjectAnalyzerConcurrency 为 watch-only 资产） |
| 6.4 | 0 立案 | C10 零缺陷面盘点形态（assertNotSame 0 命中契约缺口登记 watch W-01~05） |
| 6.5 | 0（会红 0/假保护 0/无测试 6） | 唯一假保护候选 C10-003 经复核变异重放证伪修正为「无测试」 |
| 1.7 | （口径块外，随工单） | C5-002 修复须同步改写测试 N2；C7-001 修复前须参数化改写测试 N1；C6-001 修复必须与 C6-010 同 diff（try 上移+catch Throwable） |
| 2.5/2.4 | （随工单） | 2.4 全单元唯一真并发回归钉=C1-016；2.5 MA-02 反向钉死改写随批 |

### 4.4 plan-first / ask-first 约束

| 单元 | 约束 |
|---|---|
| 4.3 | nop-http-api/nop-rpc-api/nop-integration-api 三叶 plan-first——0 工单触碰 api 叶（P0 修复仅限 impl 叶） |
| 4.4 | nop-host-C6-009 修复触碰 nop-http-api → **plan-first**（立项随 migration note）；其余 15 工单+watch 限 impl 叶 |
| 5.1 | **ask-first**（nop-auth 认证模型，P0 修复须先询问用户）+ MA-06 触碰 nop-auth/model/nop-auth.orm.xml（ORM plan-first，version 列）——实施前须走 plan audit |
| 5.2 | nop-sys-api/nop-dyn-api plan-first 0 触碰；I-03 若走唯一索引方案触 orm=plan-first |
| 5.3 | nop-file-api（3f/492）plan-first 审计只读 |
| 5.4 | （四维零线索；owner doc 缺口治理轨） |
| 5.5 | nop-rule-api/nop-metadata-api plan-first——0 工单触碰 api 叶 |
| 5.6 | nop-graph-api plan-first——**W-06（C1-003 Edge.attrs）为唯一触碰 api 叶工单，修复须 plan-first** |
| 6.2 | 零 api 叶；唯一 Protected Area 触点=W-02 Route-B 可选硬化项（向 nop-core `EvalExprProvider` 补 volatile → **plan-first**；P1 台账行已注记） |
| 6.4 | nop-code-api plan-first 零触碰（零工单自然零触碰） |
| 3.4 | nop-ai-api 入圈 plan-first 但 0 工单触碰；O-2 若推进触 nop-ai-core 公共 SPI 须 plan-first 在归属单元立项 |
| 2.1 | api-core 移交包：`io.nop.api.core.*` plan-first 保护区（5 项移交清单见 §三.2） |
| 4.5 | Protected Area 零触碰 |
| 6.5 | 零 api 叶（nop-api-debugger 近失裁定非契约叶） |

### 4.5 修复顺序 / 批次协同约束

| 约束 | 来源 | 影响批次 |
|---|---|---|
| globalTimer 修复批次先后：C4-004 与 MA-16（C4-008/C7-019）修复建议在 **WI 2.1 C4-001 落地前不得指向 globalTimer**（先平台后使用方，否则新增占用方复刻 MA-04/C8-001 形态） | 3.1 裁定 §5.1-2 | 2.1 C4-001 → 3.1 C4-004/MA-16 |
| 4.2 §五-5：globalTimer 族隔离/卸载方案须与平台族统一（1.5 MA-04、2.1 C4-001、2.3 C8-001、3.1 C4-011） | 4.2 裁定 | 平台族隔离方案统一裁决时纳入 4.2 C4-003 |
| 1.7 C8-001 修复**先于 C8-009**；C6-001 与 C6-010 同 diff | 1.7 裁定/cra-index L179/L183 | Phase 8 nop-batch 批内排序 |
| 2.5 C3-008 键派生为 MA-06（C7-001 链死亡不可见）**修复顺序前置** | 2.5/cra-index L227/L231 | 2.5 批内先修 C3-008 |
| 4.1 §五-5：MA-03 REGISTRY 契约一次裁决（stop-clear vs TTL/evictIdle vs 条件移除），覆盖 C5-001+C6-004+C1-002 三单+WI 2.5 消费面表态，**禁止各修一半** | 4.1 裁定 | 工单前置设计裁定 |
| 4.1 §五-3：C3-002/C6-002/C8-004 P1 判级口径三案配对裁定，统一后同批调整 | 4.1 裁定 | 判级分歧留 7.1 |
| 4.1 §五-6：consumeAwaitTimeout 契约与 WI 3.1 MA-11 修复批次对齐 | 4.1 裁定 | 4.1↔3.1 批次对齐 |
| 1.7 C7-001 修复须与 WI 1.5 C7-001/WI 1.6 C8-001 平台级 fencing 对齐，避免两套 fencing 语义 | 1.7 裁定 §五.2 | 平台 fencing 统一批次 |
| 1.7 C7-003 与 WI 1.6 C7-001 账本保留族：共用保留策略设计（TTL/行数上限/成功即清理），各自独立立项，Phase 8 批次对齐 | 1.7 裁定 §五.1 | F4 族批次对齐 |
| 1.7 C8-003 兜底全局池与平台共享池治理同一主题，修复须平台级统一评估 | 1.7 裁定 §五.3 | 平台池批次 |
| 3.2 沙箱族：7.1 按「装配后生产形态」统一 3.1 MA-17（P2）与本单元 MA-02/03（P1）判级；零重复，修复批次协同、护栏测试共享形态 | 3.2 裁定 §五-1/§七.2 | 7.1 判级统一项 |
| 3.2 shell 族：MA-06 按取最严暂计 P1，7.1 按装配后生产形态统一（无论走向族成员关系与单 diff 修复范围不变） | 3.2 裁定 §五-2 | 7.1 判级统一项 |
| 4.2 §五-3：Assigner 零权重语义 owner 裁决后落地；反向钉死 #3 测试改写以裁决结论为契约 | 4.2 裁定 | owner 裁决前置 |
| 4.2 §五-4：C8-001 活跃计数实现形态 owner 裁决；实现后现 LB 测试全绿且无 leastActive 语义验证（C10-007 建议(2) 同批钉定） | 4.2 裁定 | owner 裁决前置 |
| 5.5 G2：retention（W-02）与 reJudge（W-28）同表（NopMetaQualityResult）同契约，清理 job 与追加化同批设计，避免对 insert-only 声明双重触碰 | 5.5 裁定 §4.2 | 同批设计 |
| 5.5 G3：告警通道三失效层（W-24/W-25/W-12）合组裁决修复优先序，三修面独立不并单 | 5.5 裁定 §4.2 | 7.1 优先序 |
| 5.5 W-01 若 7.1 终裁 P2 → 同步移入 P2 台账并统一三登记点判级 | 5.5 裁定 §六.3 | 7.1 判级终裁联动 |
| 6.2 批次约束 4 组：W-03+W-10 同点落地；W-01+W-11+W-12 同批统一失败出口；W-13+W-14 同批（ThreadLocal 池改造+release 容量重置）；W-06/W-17+W-18 随 L2 装配接线同批 | 6.2 裁定 §六.4 | 6.2 批内排序 |
| 6.3 W-03 与 5.6 W-04：独立+交叉引用（6.3 立消费侧单飞缺失，5.6 立 detector 本体+调用面守卫） | cra-index L308 | 两侧同批 |
| 4.3 MA-02（JDK 取消语义）消费面回写：与 3.1 C7-001 **修复两侧同批**（聚合层期限+传输层读超时/可中断） | 4.3 裁定 §七-6 | 两侧同批 |
| 2.4 缓存失效世代号：C2-004+C1-027 共享设计、两个 work item（复核确认不合并执行） | 2.4 裁定 §7.4 | 设计共享 |
| 2.2 §8.4 七个修复批次（插件生命周期/IoC 启动图/配置刷新链/JdbcConfigSource/ShellRunner/VTE 治理/销毁链）同族相邻协同要点 | 2.2 裁定 | 批内防碎片化 |
| 6.3 W-02 上游保证注记：W-02 修复面限于 nop-code-service 消费侧，无需延伸 lang-* 六模块 | 6.4 裁定 §四.2 | 修复面划界 |
| 5.3 CL-020 平台级修复归 WI 2.1 C3-001 工单，本工单仅接入点登记 | 5.3 裁定 §九 | 批内划界 |
| 4.3 P0 修复仅限 impl 叶（api 叶 plan-first 零触碰） | 4.3 裁定 | P0 即修边界 |
| 5.2 MA-02 携反向钉死改写、I-02 携 C10-007 前置（见 4.3 节） | 5.2 裁定 附录 | 同 diff 前置 |
| 3.1 MA-07 移交单暂计 P2；移交后本单元保留 Error 注入护栏义务（C10-008） | 3.1 裁定 §七 | 2.1 C3-001 批次 |

---

## §五 已知差与 cra-index 缺陷（含修复建议）

| # | 缺陷/差异 | 证据（file:line） | 修复建议 |
|---|---|---|---|
| 1 | **「WI 2.2 nop-core-framework（CL-001~021：」截断空节出现两处**：L323 一处（标题截断且无内容体）、L345 一处（标题同样截断，内容体在 L348） | cra-index L323、L345、L348 | **合并为单节**：删除 L323 空节；将 L345 标题补全为完整口径句（建议：「### WI 2.2 nop-core-framework（CL 编号 21 条：升格 1→C1-021、落格 2、同位点并入设施 2、零立案 1、关联保留 8、纯移交 7；逐条见下方台账与本目录 merge-adjudication.md §六）」），保留 L348 内容体于其下 |
| 2 | **「维度集外线索关联」节悬空引用**：被 4 处口径块引用（1.6 L24、1.7 L26、2.2 L105、2.1 L109「见『维度集外线索关联』节…」），但该节在 cra-index 中**不存在**；且 CL 小节实际只覆盖 WI 4.4/4.5/5.1–6.5（L313–L348），WI 1.6/1.7/2.1/2.3/2.4/2.5/3.1–3.4/4.1–4.3 的 CL 台账无任何索引小节 | cra-index L24/L26/L105/L109 vs L313–L348 | 二选一：(a) 新建「维度集外线索关联」汇总节，按单元 3 行内摘要+指向各 merge-adjudication 权威表（Link-don't-copy，SSOT 合规）——**推荐**，同时补齐 1.6/1.7/2.1/2.3/2.4/2.5/3.x/4.1–4.3 的索引行；(b) 把 4 处口径块引用改写为「见下方各 WI CL 小节与各单元 merge-adjudication.md」 |
| 3 | **5.2 报告行 C10=6 vs 口径块 15**：报告清单 WI 5.2 行 P2 格「8+6（含 C10 缺保护登记）」，而 5.2 计数口径块与其 merge-adjudication §九/附录回填备忘均为「C10 缺保护 **15** 条单列（矩阵 16 行会红=0，含假保护随案 1）」 | cra-index L42 vs L77；5.2 裁定 §九、附录 | 将报告行 L42 改为「8+15（含 C10 缺保护登记）」 |
| 4 | **WI 5.1 CL 分布矛盾**：cra-index 口径块（L75「并入 3/独立 6/移交 8」）与 5.1 小节（L319–L321，列 CL-006 为独立线索、CL-014 为移交）同 5.1 merge-adjudication §八 权威表（**并入工单 10 / 重复合并 2 / 升格立案 1（CL-004↔CL-014 归一升格 I-06 P2）/ 独立线索 watch 4（CL-001/002/009/010）/ 移交 0**；且 CL-006 为 MA-02 主登记并升 P1）直接冲突。小节头虽写「全表见 merge-adjudication.md §八」，但正文未像 4.4/4.5 那样加「closure audit 发现误贴后按 §八 改写」注记 | cra-index L75、L319–321 vs 5.1 裁定 §二/§八/§四#3/#8 | 按 4.4/4.5 先例：5.1 小节头加「closure audit 发现本小节正文误贴后按 §八 改写」注记并以 §八 重写正文；同步修正口径块 L75 的 CL 分布为「并入 10/重复合并 2/升格 1/watch 4/移交 0」 |
| 5 | **WI 1.1、1.2 缺计数口径块**：31 个单元中仅此两单元报告清单行后无「> N.N 计数口径」块 | cra-index L14–15（有行无块） | 补两块（来源=各单元 summary/merge-adjudication），或行内加「计数口径见本目录 summary.md」指针 |
| 6 | **口径块物理排序与报告清单不一致**：5.3/2.3/2.2/2.1/1.4/1.3 六块追加在 6.5 之后（L101–L113），报告清单却按 1.1→6.5 排列 | cra-index L99–L113 | 非事实错误；建议重排至各 WI 行邻近位置，或在该组块前加「（补录块，按收口时序）」注记 |
| 7 | **5.3 小节文本与裁定 §九 建议登记文本不一致**：cra-index L327=「缺保护登记 5 条覆盖 18+7=25 闭合」；5.3 裁定 §九 建议登记=「并入工单 7、独立线索 12、watch 2、移交平台族 3（CL-003/CL-020/CL-013 方案对齐）」——且 7+12+2+3=24≠25，建议文本自身亦有 1 条未归类滑差（5.3 的 CL 编号与 25 发现 1:1 别名，分类口径需按 §八 权威表重算） | cra-index L327 vs 5.3 裁定 §八/§九 | 按 5.3 merge-adjudication §八 重算分类计数后统一小节文本；修正 §九 建议文本的和值 |
| 8 | **4.2 疑引证漂移待对账项未登入 cra-index**：4.2 裁定 §七#3 自注「输入口径列『WI 4.1 C4-003』在 cra-index 台账无同名 P1，疑引证漂移，留 7.1 对账」——cra-index 无对应登记 | 4.2 裁定 §七#3 | 将该待对账项列入 7.1 议程清单（对账后销项） |
| 9 | **CL-021 同号混写订正未落地**：2.3 裁定 §6.5-4 勘误——dispatch-log:55「CL-021→安全线」系 2.2 CL-021 同号混写（2.3 的 CL-021 为空号），建议「随单元收口在 cra-index 登记时同步订正 dispatch-log:55 移交清单行」；cra-index 未见订正登记 | 2.3 裁定 L200/L229/L283 | 收口时同步订正 1.7 dispatch-log:55（或在其移交清单行加勘误注记） |
| 10 | **P2 裁决台账空表 vs 各单元已产 P2 工单**：cra-index「P2 裁决台账（7.1 逐项裁决）」表体为「（无）」，但各裁定已产出待 7.1 逐项裁决的 P2 工单至少：3.4 O-3×4、4.1 判级分歧 4 项、4.2 §五×6、5.5 P2×9、5.6 P2×4、6.1 P2×4、6.2 P2×7、6.3 P2×16、6.5 P2×4、3.2 MA-06/沙箱分叉、3.3 13 张条件化回升工单等 | cra-index L350–356 vs 各裁定 | 7.1 启动时按各裁定 §六/§五通道批量导入 P2 台账行（本轮盘点不代改） |
| 11 | **双截断节引出的 L348 归属歧义风险**：若仅删 L323 而保留 L345 截断标题，L348 内容体的从属关系在渲染层仍悬空（标题行未闭合括号） | cra-index L345/L348 | 与 #1 合并处理：补全标题并确认内容体紧跟其后 |
| 12 | （正面核对，非缺陷）6.4/6.5 回填完整性核对通过：6.4/6.5 计数口径块（L95/L99）、CL 小节（L341/L343）、P1 台账 2 行（L309/L310）、G11-03-01 回填（L97）与两单元裁定 §六「回填待办」逐项对得上；5.1 P1 台账 2 行（L281/L282）与裁定 §九 一致 | — | — |

---

## 附：本盘点方法与边界

- 只读提取，未修改任何源文件；CL/移交/家族/约束的逐条权威均为各单元 merge-adjudication.md（cra-index 为登记视图，差异已在 §五 登记）。
- §二 家族链成员以「口径块显式登记 ∪ 各裁定 §四/§七 家族节」为并集；未把裁定未登记的同型猜测计入。
- §四 约束收录规则：cra-index 31 个计数口径块 + 各裁定中出现「反向钉/反向保护/先行失败/验证通道/plan-first/ask-first/修复须先/先于/同 diff/同批」字样者全部收录；watch-only 升级条款入 §一。
- 后续：本文为 Phase 7.1 跨模块裁决的四份输入台账之一（子代理 C 份）；§五 表所列 11 项缺陷修复归主 agent 收口通道。
