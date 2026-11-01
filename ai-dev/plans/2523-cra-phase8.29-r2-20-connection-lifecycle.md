# 2523 CRA Phase 8.29 — R2-20 4.3 连接生命周期批（6 项：MQTT removeConnection 属主校验（MA-01，C1-008+C3-018+C6-020 三登记一工单）+ sidecar 探测移出 I/O reactor 回调线程（MA-03，B1-06 终裁落账，C3-004 主+C6-006 吸收）+ SimpleRpcClientFactory 幂等 init+世代守卫（MA-07，C1-001 主+←C6-014）+ NettyTcpServer bind 失败释放事件循环组（C6-017）+ SftpClient 构造期会话回收（C6-018，R2-19 后 live 基线续作）+ SesClient 生命周期配对（C6-019））

> Plan Status: active
> Draft Review R1: agent_37f989a4 @ 2026-11-01 —— 无 Blocker；2 Major（F1 HC5 ioThreadCount 缺省=availableProcessors 事实更正[运行时反射+javap 亲证，非审计所记缺省 1——勘误 10 登记+四处文本改写+B1-06 SSOT 机制修正+Phase 4 :275 文本勘误义务]；F2 窗口①钉绿路径未成文[补 acceptEndpoint 集成级交错模拟+禁 addConnection 扩张禁令]）+3 Minor（F3 MqttConnection 措辞/F4 计数三处/F5 Closure 占位）+2 登记级建议——协调方机械修复闭环，F1/F2 经 agent_08e34460 快速复核 PASS，修订转 active
> Design Review R2: agent_8b832793 @ 2026-11-01 —— 设计文档独立对抗性评审（60+ 行号锚抽验零漂移；红①–红⑥缺陷态可红性全成立）：1 Blocker（C6-006 取消传播载体断裂——live 亲读证伪「promise.cancel 触达传输层」：jdk thenApply 派生 stage 取消不上传/okhttp·apache 裸 CF promise.cancel ≠ call.cancel()·HC5 cancel——重裁为「桥接+超时域内取消」两段[fetchAsync whenComplete isCancelled→底层取消]）+6 Minor（m2 TimeoutException 措辞/m3 jdk catch 行号/m4 globalWorker 池参数容量论证/m5 releaseBindFailure 签名补 workerGroup/m6 HttpClientConfig 实位 nop-http-api 叶勘误/m7 命名登记载体指认）——已同 diff 修入设计文档；Blocker-1 修复经 agent_70b0482f 快速复核 PASS（三实现句柄可达+CF 取消语义闭环+jdk 桥挂载点须为返回 promise 注记），放行 Phase 2
> Draft Review：待独立子 agent draft review（通过后本行登记 R1 结论，修订转 active）
> Last Reviewed: 2026-11-01
> 起草说明：本 plan 由独立起草子 agent 于 2026-11-01 在工作仓 `nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation` @ `d4c32127ad`）起草；全部站点行号为该基线 live 亲读回读值（勘误节逐条登记漂移核对结果），执行前如再漂移以 live 为准并登记。结构沿 plan 2522 先例（Source 全文引用 / Draft Review 行预留 / PA 判定表 / 对齐纪律显式条款 / 成员逐条表 / 勘误节 / 红绿判据 / C10 半边口径 / 环境三联预登记）。待独立 draft review 通过后修订转 active。
> Source: roadmap 工作项 8.29（R2-20，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L171，Status=todo[本 plan 立项时回写为「todo（plan 2523 drafted，待 draft review @ 2026-11-01）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-20 行（L94 全文照录：finding ID 清单 6 项[4.3-C1-008+C3-018+C6-020；4.3-C3-004；4.3-C1-001；4.3-C6-017；4.3-C6-018；4.3-C6-019] / 模块与验证命令 nop-network `./mvnw test -pl nop-network -am`[E3 盲区勘误见勘误 1——sftp/email 两载体在 nop-integration 聚合，须补焦点腿] / Owner Doc=`docs-for-ai/03-modules/nop-network.md` / PA=无（三 api 叶零触碰声明——live 亲读 6/6 成员零触碰成立；R2-19 已在 :299 建立裁定⑥例外先例，本批如触 api 叶须同样 plan-first 申报） / 依赖「无前置（可并行）；vertx-mqtt 面=§三.5#9 部署包络确认项（随批确认）；MA-07 R26（2.1-CL-024 原始站点）」 / exit criteria 增补：**MQTT removeConnection 属主校验（三登记一工单）；sidecar 探测移出 I/O reactor 回调线程（apache 面承载 P1，jdk/okhttp P2 备注随单）；SimpleRpcClientFactory 幂等 init+世代令牌；bind 失败释放事件循环组（对照 C6-014 正确兄弟写法）[括注归属勘误见勘误 3]；构造期会话回收；SesClient 生命周期配对**）+ §一 通用门禁 E1–E6；B1-06 终裁条款 = 同目录 `family-clustering.md` :517（B1-06 = 4.3-MA-03 双判级「**工单判级 P1（apache 面承载）**；jdk/okhttp 面失效面枚举保留 P2 备注随单」——取最严成员面四要素裁定）+ :292（4.3-MA-03 族行）+ :568（§三.5#9：4.3 休眠面激活回升条款两组终裁——(b) vertx-mqtt 面（MA-01 合并工单/MA-15 的 C3-021 站点/C3-022）：**维持已定判级不改**，登记为 owner 部署包络确认项，若部署包络声明排除 vertx-mqtt 条件化降档随 owner 裁决生效）；成员发现详情 = WI 4.3 审计目录 `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/`（C1-shared-mutable-state.md[C1-001 :100-134 / C1-008 :293-315 / 复核判定表 :618-631 / 族关联 :647-675]、C3-async-chains.md[C3-004 :98-122 / C3-018 :444-464 / 复核判定表 :856-899 / 族关联 :909-945]、C6-resource-lifecycle.md[C6-006 :250 起 / C6-017 :528-558 / C6-018 :560-587 / C6-019 :589-616 / C6-020 :618-647 / 复核判定表 :937-976 / 对照表 :53-81]、merge-adjudication.md[MA-01 :49-54 / MA-03 :63-68 / MA-07 :89-94 / §五 休眠面 :235 / 判级分歧 :236 / 权威表 :260-355]、C10-test-protection.md[C10-004 :231-250 / C10-006 :272-292 / C10-008 :315-334 / C10-013 :420 起 / :568 覆盖闭合 A2]、summary.md[:37 P1 全清单]）；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` :545（WI 4.3 节 32 条表）+ :801 小结行[**4.3（15，立项 14+watch 1）**——004→C6-005/C6-017/C6-018/C6-019(P1)+MA-03(P1)+MA-16(P2)+C3-024(P1)；006→MA-01(P1)+MA-09(P2)+C1-018/C3-022(P2)+A2 补登 C3-021；008→MA-07(P1)+C1-005/C1-007/C3-009/C6-010/C6-015(P2/P3)；013→C3-025(P2)+MA-11(P2)——本批承载 C10-004 的 C6-017/C6-018/C6-019/MA-03 四成员半边、C10-006 的 MA-01 主半边、C10-008 的 MA-07 主半边；半边口径见勘误 4]；P1 台账 = cra-index `ai-dev/audits/cra-index.md` live 2026-11-01 回读 R2-20 open 六行 = **:273**[C1-008+C3-018+C6-020（MA-01 主，「三登记一工单」）]/:**275**[C3-004（MA-03 主，「成员 C6-006；双判级 apache P1/jdk,okhttp P2 留 7.1」——B1-06 载体行）]/:**279**[C1-001（MA-07 主，「WI 2.1 CL-024 原始站点实审；成员 C6-014」）]/:**292**[C6-017]/:**293**[C6-018]/:**294**[C6-019]——**4.3 单元 R2-20 批次列 open 六行全部且仅为本批成员**（R2-19 七行已由 plan 2522 全部收敛 fixed）；4.3 计数口径 = cra-index :70；执行基线 @ `d4c32127ad`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.28 已 done，plan 2495–2522 均已完成或收口）
> Related: plan 2522（completed——**R2-19 同文件接缝与基线续作义务**：C6-018 构造回收以 R2-19 后 live SftpClient.java 为基线续作——`openConnection` 三段超时接线已落地（`session.setTimeout` :167 + `session.connect(ms)` :168 + `channel.connect(ms)` :170，`SftpConfig.timeoutMillis` 缺省 15s :48），connect(ms) 抛超时异常路径即本批 C6-018 回收面的触发前提[2522 对齐条款第 7 条 + owner doc `nop-network.md` :73 边界注记明文「构造期回收面归 R2-20」]；C10-004 的 C6-005/C3-024 两半边已由 2522 落地 fixed——本批零回退义务；owner doc `nop-network.md` 已有「Feishu Stream 通道生命周期并发契约」:64-68 与「SFTP 客户端超时契约」:70-74 两节——本批契约句落点避让既有节、SFTP 节内补句；`NETWORK-CC-001` 锚点 :105 在案——本批新增 `NETWORK-CC-002` 行沿例）；plan 2505（completed——R2-02 下载 body 阶段取消/读超时契约已落 owner doc :96-105 节 + `TestBodyPhaseCancel_C3_002` 等钉族在案——本批 sidecar 收尾重活移出回调线程与既有 body-phase 修复同链相邻、零回退义务）；**R26/R08 族面**（batch-proposal R2-20 标题「B1-06 + R26/R08 面」照录）：MA-07 timer/executor 泄漏与 C6-019 SesClient 连接池属 R26 通道池/设施面（WI 2.1 CL-024 原始站点——4.2 侧 cra-index :70 已登记「CL-024 无同族形态闭环（rpc-cluster 零自建池独立复现）」，本批落原始站点面）；MA-01/C6-017/C6-018 属 R08 资源生命周期面；**下游 R4-26/R4-27**（G67 a/b，软依赖本批）：MA-09/C1-018/C3-022/MA-16/C1-005/C3-009/C6-010/C3-025/C6-028 等 P2/P3 半边承接（勘误 4）；**同族前案**：MA-01 与 check/nosql-cdc 2026-08-19（Lettuce PubSub P2×2 未修复）及 WI 2.5 MA-01（peri 侧修复）家族关联单列零同站点（merge-adjudication §五.3 :375）
> Protected Area 授权：批次 PA=**无**（batch-proposal R2-20 行 PA 列「无（三 api 叶零触碰声明）」+ §五 :299「R2-20 零触碰声明不变」）。**头部预设（live 载体亲读核实成立）：6/6 成员零 orm.xml、零 `_` 生成物、零三 api 叶（nop-http-api/nop-rpc-api/nop-integration-api）触碰、零 nop-api-core 触碰**——六载体模块（nop-vertx-mqtt-server/nop-http-client-{apache,jdk,okhttp}/nop-rpc-simple/nop-netty/nop-integration-{sftp,email-tencent}）均为 impl 叶，`find` 亲证零 `_` 前缀文件零 orm.xml；SftpClient/SftpConfig 为 nop-integration-sftp impl 叶内手工维护源（`IFileServiceClient` 契约零变更）；SimpleRpcClientFactory 为 nop-rpc-simple impl 叶（WI 2.1 CL-024 站点邻接非同缺陷——nop-rpc-api 零触碰）。**先例申报义务成文**：R2-19 裁定⑥已在 batch-proposal :299 建立「批内触 api 叶须 plan-first 申报」例外先例——本批执行期如任何修复需触三 api 叶或 nop-api-core，须沿裁定⑥形态在本 plan + 设计文档 + owner doc 契约句 + 回归测试四件证据链下同样申报，禁静默扩权。站点分布于 nop-vertx-mqtt-server（MA-01 三文件）、nop-http 三 client impl 叶（MA-03 三文件+helper 配置面）、nop-rpc-simple（MA-07 单文件）、nop-netty（C6-017 单文件）、nop-integration-sftp（C6-018 单文件）、nop-integration-email-tencent（C6-019 单文件）。

## Purpose

关闭 R2-20 批的 6 项已确认缺陷（P1×6；cra-index open 六行 :273/:275/:279/:292/:293/:294 live 2026-11-01 回读全 open 且批次列=R2-20；B1-06 终裁随本批落账——工单判级 P1 apache 面承载、jdk/okhttp P2 备注随单；vertx-mqtt 面部署包络确认项随批确认义务成文；C10 缺保护 15 条中 4 条与本批成员对应、随批承载半边口径[勘误 4]）。核心结果面有六：①**MQTT removeConnection 属主校验**（MA-01，C1-008+C3-018+C6-020 三登记一工单）——会话接管/迟到 close 双窗口下旧连接清理路径无条件清空新连接订阅表：设备重连后下行路由静默失效（客户端自认仍订阅不重发 SUBSCRIBE，QoS0 无任何错误可见；`findConnections` 永失配 + `sendAsync` success(null) 半个成功形态）。②**sidecar 探测移出回调线程**（MA-03，C3-004 主+C6-006 吸收，B1-06 终裁 P1 apache 面承载）——下载完成回调线程内嵌套同步 `get(30s)×2` 复用同一 client：apache 共享 reactor（**HC5 `ioThreadCount` 缺省 = `Runtime.availableProcessors()`[R1 Major-F1 勘误：非审计所记缺省 1，运行时反射实测 + 全版本 javap 证实]**；多核缺省下嵌套请求由其他 dispatch 线程服务、不构成缺省全停摆——生产影响 = 每次下载占用一个 reactor 回调线程 60s（30s×2 双探测）+ C6-006 内层滞留；全连接停摆为 `ioThreadCount=1` 配置面[CL-021 点名]形态）；超时后内层 future 不取消在途请求滞留池（C6-006 面）。③**SimpleRpcClientFactory 幂等 init**（MA-07，C1-001 主+←C6-014，WI 2.1 CL-024 原始站点）——init 挂在 newInstance 非幂等：logBody 拦截器共享列表无界累积 + timer/executor 检查-创建竞态双建泄漏单线程定时器 + destroy 后 newInstance 在已销毁 executor 上投递。④**NettyTcpServer bind 失败释放事件循环组**（C6-017）——doStart 两抛出点零清理：每次「启动失败→再启动」循环净增 2×CPU 个非 daemon I/O 线程 + NIO selector/fd 泄漏（容器端口冲突重试循环线性增长）。⑤**SftpClient 构造期会话回收**（C6-018）——「session 已连、channel 未开」失败 SSH 会话无人 disconnect（构造抛错调用方引用不可得；R2-19 的 connect(ms) 超时路径即其触发前提，逐次新建工厂语义放大）。⑥**SesClient 生命周期配对**（C6-019）——每次 sendEmail 新建 Tencent SDK SesClient 从不 shutdown：HTTP 连接池随发送次数无界累积（对照同模块 YunpianSmsSender finally close 正确兄弟）。批末 roadmap 8.29 → done，cra-index 六行收敛（B1-06 落账注记随行）+ C10 三条成员面注记 + vertx-mqtt 部署包络确认申报留痕。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-20 = 6 项成员全 P1（MA-01 三登记一工单[C1-008 主登记+C3-018+C6-020，C6-020 独有窗口①随同 diff、C1-008 失效面①证伪修正落账]；MA-03[C3-004 主登记+成员 C6-006，双判级 apache P1/jdk,okhttp P2——B1-06 终裁工单 P1]；MA-07[C1-001 主登记+←C6-014 并入]；C6-017[独立]；C6-018[独立]；C6-019[独立]）；cra-index live 2026-11-01 回读 R2-20 批次列 open 六行 = :273/:275/:279/:292/:293/:294；roadmap 8.29 行 Status=todo。依赖核对（live 2026-11-01）：**无前置（可并行）**——batch-proposal R2-20 行依赖列明文「无前置（可并行）」；vertx-mqtt 面=§三.5#9(b) 部署包络确认项（随批确认义务，非代码依赖——勘误 6）；MA-07 R26（2.1-CL-024 原始站点）为站点溯源注记非前置。与 R2-19 的接缝 = C6-018 同文件排序续作（2522 对齐条款第 7 条：批次间 diff 排序注记，无硬依赖、无互斥——R2-19 已收口故本批以 live 为基线直接续作）。
- **B1-06 终裁落账（条款成文，落账位置登记）**：family-clustering :517[B1-06 = 4.3-MA-03 双判级（apache P1 / jdk,okhttp P2）→「**工单判级 P1（apache 面承载）**；jdk/okhttp 面失效面枚举保留 P2 备注随单」——理由：本体与修复点同一（探测移出 I/O reactor 回调线程/异步化），「单一缺陷单一判级」原则下取最严成员面：apache 面=共享 reactor 全连接停摆 30s（①同步 get(30s) 时序具体；②sidecar 校验探测为 http-client 主路径；③无守卫；④回调线程占用 30s×2/下载 + 内层滞留 + ioThreadCount=1 配置面全停摆[R1 Major-F1 机制修正]功能后果确定）；jdk/okhttp 独占线程载体后果收敛为单探测挂起，作 P2 备注随单保留] + :568（§三.5#4：4.4-C4-001 与 4.3-MA-03 并档——**各自判级维持**，同形不同站点不并族防双计）+ :292（4.3-MA-03 族行）。**落账位置**：Phase 4 cra-index 回写时 :275 行内「双判级 apache P1/jdk,okhttp P2 留 7.1」注记改写为「B1-06 终裁落账（工单 P1 apache 面承载；jdk/okhttp P2 备注随单）」终态注记；条款文本以本节为 plan 内 SSOT。B1-06 与 B1-07/08 形态差异声明：B1-07/08 为「暂计转正+对称条款」，B1-06 为**直裁终态**（无对称条款、无消费面回撤条件）——落账注记照此书写，禁套用 B1-07/08 模板。
- **vertx-mqtt 面部署包络确认项（§三.5#9b 随批确认义务成文）**：family-clustering :568 明文——vertx-mqtt 面（MA-01 合并工单/MA-15 的 C3-021 站点/C3-022）**维持已定判级不改**（MA-01 P1 维持），登记为 owner 部署包络确认项；「若部署包络声明排除 vertx-mqtt，条件化降档随 owner 裁决生效」。live 零装配证据（2026-11-01 亲读在案）：`nop-vertx-mqtt-server/src/main/` 仅 `java` 目录（**无 main/resources、无 beans.xml**）；pom 引用仅 nop-bom（依赖管理）/nop-network 聚合器/顶层 tests pom——全仓零业务模块引用。**本批义务形态**：确认动作为申报性非裁定性——Phase 4 收口时向 owner 登记部署包络确认申报（证据=上述零装配事实 + MA-01 功能后果为「服务器产品化部署预期形态的主路径后果」family-clustering 终裁理由），owner 结论（维持 P1/条件化降档）随行登记；本 plan 不预设 owner 结论、不以零装配为由单方降档（禁违反「本轮不单方面改变已定跨维判级」约束）。
- **MA-01 三登记结构与本级修正落账（merge-adjudication :49-54）**：成员 = C1-008（主登记）+ C3-018 + C6-020（独有窗口①随同 diff）；根因 = `MqttSessionManager` 清理粒度（按 clientId 整组）与所有权（per-connection 登记）不匹配——`removeConnection` L44 无条件清空订阅 + `addConnection` L36 接管删除在 CONNACK 先于会话注册的顺序窗口下同样命中新连接；修复点 = 订阅登记与连接属主绑定（对齐 `sessions.remove(key, value)` 两参守卫），删除前校验属主；`acceptEndpoint` 调整为先注册会话再 `endpoint.accept()` 消除窗口①；**单 diff 覆盖双窗口**。本级修正：C1-008 失效面①证伪修正落账——C6-020① 前提下「L36 语义正确」不成立，随本工单失效面枚举修正（本 plan 成员表已按修正后口径登记窗口①）。
- **C10 验收前置入批集（映射 SSOT = `inventory-p2-c10.md` :545 WI 4.3 节 + :801 小结行 + `C10-test-protection.md` 逐条；随本体同 diff 落地[E2]）**：
  - C10-006→MA-01 主半边（MQTT 会话/订阅复合操作零交错覆盖，P2 承载 P1——**本批承载 MA-01 全条**：现有 `TestMqttServerFix.testSessionTakeoverClosesOldConnection`/`testSessionRemovedOnConnectionClose` 只断言 close 被调、subscriptions 表状态零断言；本批承载单线程交错脚本钉[addConnection(B)→subscribe(B,filter)→removeConnection(A)→断言 B 订阅仍在——当前即红] + 双窗口红钉 + 并发 subscribe×N 防过修钉；C10-006 其余半边[MA-09/C1-018/C3-022/C3-021 RP-1 改写义务]→ R4-26 不代做）；
  - C10-004→C6-017/C6-018/C6-019/MA-03 四成员半边（HTTP/SFTP/邮件生命周期资源面零检出——本批承载 **bind 失败线程组终止钉**[C6-017]/**recording-jsch 注入点上移+disconnect 断言钉**[C6-018]/**SesClient shutdown 断言钉**[C6-019]/**sidecar 悬挂端点+总时长上界+内层取消钉**[MA-03]（C10-004 建议②③④⑤形态）；其余半边：C6-005/C3-024 已由 plan 2522 落地 fixed 零回退、MA-16→ R4-26 不代做）；
  - C10-008→MA-07 主半边（nop-rpc-simple 工厂/服务器生命周期零测试——本批承载 **newInstance()×N interceptors 长度恒定+timer 实例唯一钉**（C10-008 建议①形态，当前即红）+ destroy 后 fail-fast 钉；其余半边 C1-005/C1-007/C3-009/C6-010/C6-015 → R4-26/R4-27 rpc-simple 批不代做）；
  - C10-013 为 C6-019 保护面注记（cra-index :294 行内明文「保护面 C10-004/C10-013」——本批 shutdown 断言钉落 C10-004 面；C10-013 本体面 C3-025 吞异常→ R4-27 不代做，修复禁顺手改 catch 语义）。
- **4.3 批前已收口面（live 形态登记，零触碰）**：R2-19（plan 2522）七成员已 fixed——`SftpClient` 三段超时接线 :167-170 与 `SftpConfig.timeoutMillis` :48 在案（C6-018 基线续作前提）；OkHttp onResponse 终结性包裹 :76-94/:352-364、verifyAndRename rename 无条件强制 :166-167、三 impl BASE64 try-with-resources（C6-005/C3-001/C3-005 钉族在案零回退义务）；R2-02（plan 2505）C3-002/C3-003/C3-012/C6-004 已 fixed——本批 MA-03 修复的 sidecar 收尾链与 body-phase 修复同链相邻零回退；`NETWORK-CC-001` 锚点 :105 在案；P0×1 C6-001 已由 plan 2479 就地修复（与 MA-03 同链不同本体零冲突）。
- **平台设施与测试资产盘点（live 实读 @ `d4c32127ad` + 基线亲跑）**：
  - **基线测试结果（2026-11-01 亲跑）**：`./mvnw test -pl nop-network` **BUILD SUCCESS，EXIT=0，215 tests 0F0E 7 skipped**（skip = `TestJdkHttpClient` 3 + `TestApacheHttpClient` 4 类级 `@Disabled` 历史真实端点测试——确定性 skip 非环境抖动面；20 模块 14 test-bearing；较 2522 批前基线 209 +6 = 2522 三钉族[C3-001/C3-005/C6-005] landed；`_tmp/baseline-2523-nop-network.log`）。**补充焦点腿基线：`./mvnw test -pl nop-integration/nop-integration-sftp,nop-integration/nop-integration-email-tencent` BUILD SUCCESS EXIT=0，30 tests 0F0E 0 skipped**（sftp 19 + email-tencent 11；`_tmp/baseline-2523-nop-integration-sftp-email.log`）——本批零回退钉的绿色基线（合计 245 tests）。
  - **先例范式可引用不重开**：属主校验两参守卫范式（`sessions.remove(key, value)` 本文件 L43 自身在案——裁定①对齐对象）；世代守卫/单飞范式（plan 2522 feishu `lifecycleGeneration`+`reconnectPending` CAS 已落——MA-07 世代守卫同族参照但站点语义不同[工厂生命周期 vs 连接生命周期]，禁照抄语义）；bind 失败清理正确兄弟（`SocketServer.doStart` catch 内 `IoHelper.safeCloseObject`[C6-017 失效面③已闭合] + 同文件 `doStop` :311-329 清理清单逆序参照）；finally 资源配对正确兄弟（`YunpianSmsSender` finally close :120-121/:132-133——C6-019 对齐对象）；`OssFileServiceClientFactory` bean 级单例形态（C6-019 备选参照——audit 建议②）；R21 族 completeExceptionally 纪律与 R11 族纪律（2521/2522 已落范式，本批无成员命中此两族——仅引用不扩张）；TestFeishuClientLifecycleConcurrency 假体升级形态（2522 落地——本批 MQTT/rpc-simple 假体设计同构参照[确定性交错脚本/计数断言]，禁跨模块共享测试基建除非裁定⑦定夺）。
  - **owner doc 现状（2026-11-01 亲读，110 行）**：`docs-for-ai/03-modules/nop-network.md` 为本批 owner（roadmap 8.29 Owner Doc 列）——既有节结构：IHttpClient 文件上传（BASE64_FORM :25-35）/文件下载（:37-48，**sidecar 行 :46「探测失败不阻断」**即 MA-03 契约句落点——现状句未声明回调线程约束，Phase 3 须原地补强）/MQTT 服务端消息（:50-57，**:57「会话接管、断连清理由 MqttSessionManager 维护」**即 MA-01 属主校验契约句落点——现状句未声明属主语义）/SMS 与 Feishu ACK 契约（:59-63）/Feishu Stream 通道生命周期并发契约（:64-68，2522 落点零触碰）/SFTP 客户端超时契约（:70-74，**:73 边界注记明文「构造期回收面归 R2-20（本批 connect(ms) 抛超时路径即其触发前提）」**——C6-018 契约句落既有 SFTP 节内补句，落账时改写该边界注记为已落地终态）/Kafka/nop-cluster/nop-search/JDK 503/网关过滤/响应体阶段取消（:96-105）各节零触碰；**rpc-simple 工厂/NettyTcpServer 启停/email 发送器三域零覆盖**——MA-07/C6-017/C6-019 契约句落点 = 新增三小节（紧邻既有节按主题插入）；`docs-for-ai/04-reference/source-anchors.md` 新增 `NETWORK-CC-002` 锚点行（命名沿 NETWORK-CC-001 :105 既有惯例，插入点 = NETWORK-CC-001 :105 之后按表序实际行号，live 亲读裁定点）。
  - **测试资产（live 核实）**：vertx-mqtt-server **5 类 18 用例**（`TestMqttServerFix` 7[R1 Minor-F4 更正：亲数 7 非 8][含 `testSessionTakeoverClosesOldConnection` :150-179 / `testSessionRemovedOnConnectionClose` :181-201——两钉均无 subscriptions 断言，C10-006 结论 live 复核维持] + `TestMqttMessageRouting` + `TestMqttConnection` + `TestMqttFailClosedNoChecker` + `TestMqttTopicMatcher`；全部经代理 endpoint + 反射 handleEndpoint 单线程驱动，零真实 event loop 交错）；netty **4 类**（`TestNettyTcpServerFix` 在案——既有 server 面钉零回退义务；TestNettyTcpClient/Reconnect 两类=C6-022 面[MA-10→R4-26]零触碰）；rpc-simple **1 类 1 用例**（`TestSimpleRpcClient` 仅 transformer 异常包装——C10-008「模块全部测试内容与工厂生命周期无关」live 复核维持）；email-tencent **1 类 11 用例**（`TestTencentEmailSenderCredential` 仅凭证面——生命周期零断言）；sftp **3 类 19 用例**[R1 Minor-F4 更正：Credential 12+HostKeyChecking 5+Timeout 2]（2522 后含 C3-024 超时钉与黑洞探针——红⑤注入点上移与既有钉协同禁回退）；**无 docker 门控测试**；六成员红钉全部为 plain-JVM 可跑面（单线程交错脚本/recording-jsch 假体/端口预置占用/seam 覆写/loopback 悬挂端点），无外部依赖门控。
  - **红② sidecar 悬挂端点 mock 形态预登记**：红②需「sidecar 服务端接受连接不响应」故障注入——形态候选 = 既有 `TestFileTransfer` loopback `HttpServer` 挂起 handler 形态（零新依赖，2505/2522 先例基建）或 socket 级半开放手写——**Phase 1 裁定⑦一次定夺，预设 = loopback HttpServer 挂起 handler（零新依赖）**；apache/jdk/okhttp 三实现各自跑同型钉（F3 族三处同改验证面）。
- **模块聚合形态 + E3 盲区（勘误 1，live pom 亲读）**：批次标签「nop-network」= 聚合器口径，但 `nop-network/pom.xml` <module> 六项（nop-codec/nop-http/nop-rpc/nop-netty/nop-socket/nop-vertx）**不含 nop-integration 聚合器**——6 成员中 2 个载体（C6-018 sftp + C6-019 email-tencent）不在 `./mvnw test -pl nop-network[-am]` 触达面；4 成员载体（MQTT/三 http client impl/rpc-simple/netty）在 reactor 内。E3 命令照录执行（批次 SSOT 不改写），**Phase 3 强制补充焦点腿** `./mvnw test -pl nop-integration/nop-integration-sftp,nop-integration/nop-integration-email-tencent`（基线 30 tests 在案）——E3 实触 = 4/6 载体 + 焦点腿 2/6 载体。批次验证命令列的回写（batch-proposal/roadmap 补注记）登记 Phase 4 动作（勘误 1）。

### 对齐纪律显式条款

- **MA-01 三登记一处闭合（C1-008 + C3-018 + C6-020）**：单 diff 覆盖双窗口——窗口②（迟到 close 回调 `removeConnection` 无条件清订阅）与窗口①（CONNACK 先于 addConnection 的接管删除清掉新连接已登记订阅；C6-020 独有、C1-008 失效面①证伪修正随行落账）；修复点 = 属主绑定/删除前校验 + `acceptEndpoint` 先注册会话再 `endpoint.accept()`（merge-adjudication MA-01 修复点明文）；三登记不拆分、不四处改（cra-index :273 单行回填）。
- **MA-01 × C1-018/MA-09/C6-028/R4-26 相邻不并族（同文件或同模块 adjacent 面）**：`acceptEndpoint` 顺序调整与 C1-018（回调挂载时序倒置 close 回调丢失，P2→R4-26）同站点相邻——修复点互异不并族；裁定①的顺序调整**不得顺手修 C1-018 面**（close 回调早于 onCloseCallback 注册的丢失窗口留 R4-26），但顺序调整对该窗口的影响须在设计文档登记论证；`MqttServerMessageService` dispatcher 配对面（MA-09）与 doStop 清表面（C6-028）零触碰（不同文件/不同生命周期段，R4-26/27）。
- **MA-03 × C6-006 吸收成员一处闭合 + CL-021 配置面随单**：单 diff 三实现同改（F3 族）——收尾重活（sidecar 探测/校验/rename 收尾段）整体移出回调线程 + 超时后内层 future 取消（C6-006 面：get 超时 catch 分支不得遗留在途请求滞留池至 readTimeout）；CL-021（ioThreadCount=1 自死锁配置面）吸收线索随工单收敛（是否新增配置项/文档化 ioThreadCount 语义一次定夺——裁定②域）；**B1-06 终裁口径随行**：修复必须覆盖三实现（P1 apache 面承载不意味着 jdk/okhttp 免修——P2 备注随单指判级备注非修复豁免，merge-adjudication MA-03 修复点「三实现同 diff」明文）；`TestBodyPhaseCancel_C3_002` 等下载链既有钉零回退。
- **MA-07 × WI 2.1 CL-024 站点邻接声明 + C6-014 并入**：MA-07 修复落原始站点面（batch-proposal 依赖列「MA-07 R26（2.1-CL-024 原始站点）」）——CL-024 本体（nop-cluster rpc-cluster 池累计面）已由 4.2 侧独立复现登记、本批零触碰 nop-cluster；C6-014 并入 C1-001 同站点同本体（merge-adjudication §六 :349）零重复立项；**正确兄弟对照**：幂等守卫对照 = `SimpleRpcServer.init` Guard 防重复 start（C6-014 失效面②，C1-007 同族站点）——**禁顺手修 SimpleRpcServer 的 C1-007/C6-015 P3 面**（rpc-simple 批次 R4-26/R4-27，修复点互异）；「世代令牌」语义定位（exit criteria 原文）= destroy×newInstance 交错守卫的 destroyed 世代/幂等守卫形态，与 feishu lifecycleGeneration 连接生命周期语义**不同族禁照抄**——具体形态一次定夺（裁定③域）。
- **C6-017 × 修复边界（仅 doStart bind 失败路径）**：修复仅覆盖 C6-017 失效面①（NettyTcpServer.doStart 两抛出点）——`NettyTcpClient`（C6-022/MA-10→R4-26）零触碰、`VertxMqttServer.doStart`（C6-023/MA-14→R4-26「与 C6-028 同提交」）零触碰、`SocketServer`（已闭合兄弟）零触碰；外部注入 workerGroup 场景只关 bossGroup（所有权纪律——非自有资源禁 shutdown）一次定夺（裁定④域）。
- **C6-018 × C3-024（R2-19 已修）同文件接缝续作**：C6-018 以 R2-19 后 live SftpClient.java 为基线续作（openConnection :131-171，三段超时接线 :167-170 在案）；修复 = `session.connect()` 成功之后的步骤 try/catch 包裹、失败时回收 session 再抛（audit 建议：disconnectQuietly 提取或 connect() catch 内调用）——**超时接线与超时异常路径零回退**（`TestSftpClientTimeout_C3-024` 钉族零改写）；SftpConfig 零新增配置项（回收行为零配置面）；**构造回收与超时是同文件不同本体**（C6-018 复核行「同案：C3-024（同文件不同面：本条构造期泄漏 vs 挂死面）」——不互吸、不分叉）。
- **C6-019 × Yunpian 兄弟对照 + C3-025 不并族**：保守修复 = try/finally `client.shutdown()`（对照 `YunpianSmsSender` finally close 正确兄弟 + audit 建议①——当前注释明示「逐次解析、每次新建」语义下的最小契约面变更）vs bean 级单例（audit 建议②参照 `OssFileServiceClientFactory`——凭证轮换可见性变化属设计层一次定夺，裁定⑥域）；**catch 语义零触碰**（C3-025 吞异常面→R4-27；EmailAddressIsNULL/FrequencyLimit 特判分支行为保持）。
- **同变更集绑定（C10 随本体，E2）**：C10-006[MA-01 面]/C10-004[C6-017/C6-018/C6-019/MA-03 半边]/C10-008[MA-07 半边] 与各自本体同 diff 落地，禁止先修本体后补测的拆分交付。
- **硬序条款**：本批无跨批硬依赖（无前置，可并行）；批内纪律 = 每成员先红后绿或变异/回退论证（E2），非批次次序约束；批内软序 = MA-01 单 diff 三登记一次闭合（禁拆分交付）+ MA-03 三实现同 diff（禁只修 apache——B1-06 备注随单非修复豁免）。
- **跨单元同号防混**：4.3-C1-001 与 2.1-C1-001（R2-01 已收口）/3.2-C1-001/1.3-C1-001 等同号异站零关联；4.3-C3-004 与 2.1-C3-004（R1-04 bindCancel 面）/2.3-C3-004（R4-16）同号异站零关联——cra-index 回填时逐行带 4.3 前缀核对；MA-01 与 WI 2.5 MA-01（peri PubSub 侧已修）/nosql-cdc 前案家族关联单列零同站点（merge-adjudication §五.3）。

### 6 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `d4c32127ad` 2026-11-01 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；吸收成员） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 4.3-C1-008+C3-018+C6-020（P1，MA-01 主；三登记一工单；C6-020 独有窗口①随同 diff；C10-006 面；vertx-mqtt 部署包络确认项随批确认[勘误 6]） | `MqttSessionManager.java` + `VertxMqttServer.java` + `MqttConnection.java`（nop-vertx-mqtt-server `impl`） | `removeConnection` :42-45（:43 `sessions.remove(clientId, conn)` 两参条件删 / **:44 `subscriptions.remove(clientId)` 无条件清**）；`addConnection` :33-40（:34 put / :36 接管清订阅[窗口①命中点] / :37 `old.close()` 异步）；触发链 `VertxMqttServer.acceptEndpoint` :116-126（:117 new MqttConnection[构造内 endpoint.accept() 已发 CONNACK] / :119 setOnCloseCallback / :121 addConnection——注册晚于 CONNACK 的顺序窗）；回调链 `MqttConnection.complete` :211-224（closed 守卫 :212-215 + finally 回调 :217-223）；消费点 `findConnections` :68-84（订阅缺失即不命中 → `sendAsync` success(null) 半个成功） | removeConnection 会话接管/迟到 close 双窗口无属主校验——旧连接清理路径 ：44 无条件 `subscriptions.remove(clientId)` 抹掉新连接已登记订阅（窗口②）+ CONNACK 后 SUBSCRIBE 先落表、addConnection 接管删除 ：36 再清（窗口①，C6-020 独有）——设备重连后下行路由静默失效（客户端不重发 SUBSCRIBE、QoS0 无错误可见直至重连） | 裁定①（C10-006 随同 diff；**三登记一工单单 diff 双窗口**）：removeConnection 改属主校验条件清（仅当 sessions 当前值==conn 才清订阅——对齐 ：43 两参守卫同法[预设 compute 或 get-校验-remove 形态一次定夺]）；`acceptEndpoint` 调整为先注册会话（addConnection+回调挂载）再 `endpoint.accept()` 消除窗口①[预设]；窗口①修复与 C1-018 相邻面论证随裁定①（对齐条款第 2 条）；同族前案 nosql-cdc/WI 2.5 MA-01 家族关联单列零同站点 |
| 2 | 4.3-C3-004+C6-006（P1，MA-03 主；B1-06 终裁=工单 P1 apache 面承载/jdk,okhttp P2 备注随单[落账条款见 Current Baseline]；C10-004 面；C6-006 吸收成员+CL-021 配置面吸收线索） | `ApacheHttpClient.java` + `JdkHttpClient.java` + `OkHttpClientImpl.java`（三 http client impl 叶）+ `ApacheHttpClientHelper.java`（CL-021 配置面，如裁定②定夺收敛） | Apache：`handleDownloadResult` :300 起（FutureCallback.completed 回调链 → HC5 I/O reactor 线程执行）+ sidecar 调用 :332 + `fetchSidecarChecksum` :370-383（get(30s) :374）；Jdk：sidecar 调用 :490 + `fetchSidecarChecksum` :511-515（get(30s) :515）；OkHttp：sidecar 调用 :224 + `fetchSidecarChecksum` :251-256（get(30s) :256）；配置面 `ApacheHttpClientHelper.createReactorConfig` :102-111（ioThreadCount 仅 >0 覆写——HC5 缺省 = availableProcessors[R1 Major-F1 勘误]，=1 配置面即 CL-021 全停摆形态）；超时后内层不取消：三实现 get(30s) 超时 catch 分支均无 `cancel(true)`（C6-006 面）；默认 `DownloadOptions.fetchSidecarChecksum=true` 全量无校验头下载触达 | 下载完成回调线程内嵌套同步 get(30s)×2 复用同一 client——apache 共享 reactor 上嵌套同步 get(30s)×2 占用回调线程 60s、超时后内层滞留（[R1 Major-F1 机制修正：HC5 缺省 ioThreadCount = availableProcessors 非 1——多核缺省不构成全部连接停摆，全停摆为 ioThreadCount=1 配置面形态]；悬挂红判据不受影响——悬挂在服务端侧）；jdk/okhttp 占用回调池单探测挂起（P2 备注面）；超时后内层 future 不取消在途请求滞留池至 readTimeout（C6-006） | 裁定②（C10-004 半边随同 diff；**三实现同 diff F3 族**）：收尾重活（sidecar 探测/校验/rename 收尾段）整体移出回调线程投递独立 executor[预设方向——池选择（GlobalExecutors.globalWorker vs 专用池）vs 非阻塞链式 whenCompose 一次定夺；回调线程只做 promise 完成语义保持] + get 超时路径内层取消（C6-006 闭合）；CL-021 ioThreadCount 配置面收敛随单（裁定②域）；B1-06 落账+§三.5#4 并档注记；下载链既有钉（2505/2522）零回退 |
| 3 | 4.3-C1-001（P1，MA-07 主；←C6-014 并入同站点同本体；WI 2.1 CL-024 原始站点实审；C10-008 面） | `SimpleRpcClientFactory.java`（nop-rpc-simple） | `init()` :112-129（prepend :113-115 原地变更共享列表[`CollectionHelper.prepend` = `list.add(0,item)` 返回同一列表] + timer/executor 检查-创建 :121-128 无原子守卫）；`newInstance()` :156-172（:157 每次调用 init() + :167 共享 interceptors 按引用交 handler + :170 `executeRecv(executor)`）；`destroy()` :136-146（destroyed=true :137 先于 executor.destroy() :139-141——destroy×newInstance 交错在已销毁 executor 上投递）；字段全非 volatile/final（timer/executor/ownTimer/ownExecutor/interceptors/destroyed）；正确兄弟对照 = `SimpleRpcServer.init` Guard 防重复 start（C6-014 失效面②；C1-007 同族站点零触碰） | init 挂在 newInstance 非幂等——logBody 时每次连接 prepend 一个拦截器共享列表无界累积（RPC 链 k 次重复日志+时延线性增长）+ timer/executor 检查-创建竞态双建泄漏单线程定时器（后写者覆盖先建者永无人 destroy）+ destroyed 先置位竞态（destroy 后 newInstance 在已销毁 executor 上 executeRecv） | 裁定③（C10-008 半边随同 diff）：幂等守卫单点[预设 volatile inited + synchronized 或 AtomicBoolean CAS 一次定夺] + 拦截器装配一次化并不可变快照后再交 handler（禁共享可变列表） + timer/executor 原子创建惯用法 + destroyed 世代守卫（volatile + destroy 后 newInstance fail-fast——exit criteria「世代令牌」语义定位[勘误 3 关联：与 SimpleRpcServer Guard 兄弟对照]）[预设]；消费方 XLangDebugConnector（dev-tools）兼容性核对（当前未开 logBody、单线程 IDE 使用——修复不改健康路径可观测形态） |
| 4 | 4.3-C6-017（P1，独立；R08 族面；对照 = 同文件 doStop 清理清单 + SocketServer.doStart 已闭合兄弟[C6-017 失效面③；exit criteria 括注归属勘误见勘误 3]；C10-004 面） | `NettyTcpServer.java`（nop-netty） | `doStart` :135-141（bossGroup :135 / workerGroup :137-141 先创建[NioEventLoopGroup 默认线程数=CPU×2 非 daemon]）+ shaping handler :165-171 + bind **两抛出点零清理**：catch :187-190（throw :189）与 isSuccess 复检 :192-196（throw :195）；对照 `doStop` :311-329（shutdownGracefully :319/:321 + `globalTrafficShapingHandler.release()` :326——清理清单逆序参照） | doStart bind 失败（端口占用/权限）两抛出点直接 throw——已创建 boss/worker 事件循环组与 globalTrafficShapingHandler 均未 shutdown/release；每次「启动失败→再启动」循环净增 2×CPU 个非 daemon I/O 线程 + NIO selector/fd 泄漏（容器 restart backoff 线性增长至 unable to create native thread；非 daemon 线程阻止 JVM 退出）；doStop 是唯一清理点但 bean 启动失败不会走到 doStop | 裁定④（C10-004 半边随同 diff）：bind 失败 catch/复检路径补组清理[预设 = 按 doStop 清理清单逆序：globalTrafficShapingHandler.release() + 自建 workerGroup.shutdownGracefully() + bossGroup.shutdownGracefully()；外部注入 workerGroup 时只关 boss——所有权纪律一次定夺]；修复仅覆盖失效面①（NettyTcpClient/VertxMqttServer/SocketServer 零触碰——对齐条款第 5 条） |
| 5 | 4.3-C6-018（P1，独立；C10-004 面；C3-024[R2-19 已修] 同文件不同本体接缝——**以 R2-19 后 live 为基线续作**；Related/对齐条款第 6 条） | `SftpClient.java` + `SftpConfig.java`（nop-integration-sftp） | `openConnection` :131-171——R2-19 后 live：`session.setTimeout(config.getTimeoutMillis())` :167 + `session.connect(config.getTimeoutMillis())` :168 + `channel = (ChannelSftp) session.openChannel("sftp")` :169 + `channel.connect(config.getTimeoutMillis())` :170；**缺陷窗口 = :168 成功后 :170 失败**（sftp 子系统不可用/channel 超时）→ `ERR_SFTP_CONNECT_FAIL` 从构造函数（this.connect() :68）传播——session 已连无人 disconnect（调用方 `SftpClientFactory.newClient` :71-74 / `FileServiceResource` finally 引用为 null 无从清理）；`close()` :314-321+（channel.exit + finally session.disconnect）是唯一回收点、构造失败永不可达；jsch Session 无 finalizer 兜底 | SftpClient 构造期「session 已连、channel 未开」失败——SSH 会话/socket 无人回收；「逐次操作期解析、每次新建」工厂语义逐次放大（服务端 MaxSessions 耗尽/本地 fd 泄漏/对端审计未正常退出会话）；R2-19 的 connect(ms) 超时异常路径即本条触发前提（已落地） | 裁定⑤（C10-004 半边随同 diff；**R2-19 后 live 基线续作**）：openConnection 用 try/catch 包裹 session.connect 成功之后的步骤，失败时 session.disconnect() 再抛[预设——disconnectQuietly 提取 vs 内联一次定夺；ERR_SFTP_CONNECT_FAIL 包装语义保持]；超时接线与超时异常路径零回退（TestSftpClientTimeout_C3_024 钉族零改写）；SftpConfig 零新增配置项；recording-jsch 失败注入点上移到 channel.connect 断言 disconnect 被调（红⑤当前即红） |
| 6 | 4.3-C6-019（P1，独立；R26 族面；保护面 C10-004/C10-013[cra-index :294 行内明文]；对照 `YunpianSmsSender` finally close :120-121/:132-133 已闭合兄弟） | `TencentEmailSender.java`（nop-integration-email-tencent） | `sendEmail` :167-197（`createClient(credential)` :169 每次新建 + `client.SendEmail(req)` :186 + catch 分支 :187-196 **无 finally 无 shutdown**）；`createClient` :150-163（`new SesClient(cred, credential.region, clientProfile)` :163 每次新建——Tencent SDK BaseClient 内部持有 HTTP 连接池与底层 Apache HttpClient 资源，官方契约用毕 shutdown）；catch 特判分支（EmailAddressIsNULL return / FrequencyLimit warn+return）语义在案 | 每次 sendEmail 新建 SesClient 从不 shutdown——HTTP 连接池随发送次数无界累积（fd/内存/后台线程线性增长，ulimit 耗尽进程整体不可用）；MFA/邮件通知高频长驻场景；成功与失败路径均零关闭；与 yunpian 短信兄弟 finally close 纪律形成同族偏差 | 裁定⑥（C10-004 半边随同 diff；C10-013 本体面不代做）：try/finally `client.shutdown()`[预设——注释明示「逐次解析、每次新建」语义下最小契约面变更] vs bean 级单例（`OssFileServiceClientFactory` 形态参照——凭证轮换可见性变化属设计层一次定夺）；**catch 语义零触碰**（EmailAddressIsNULL/FrequencyLimit 特判保持——C3-025[R4-27] 不并族）；seam 覆写 createClient 断言 shutdown 被调（红⑥当前即红）；TencentSmsSender（qcloudsms 无 close API）非同缺陷零触碰[C6-019 失效面②] |

### PA 判定表（6 成员逐一，orm/_生成物/三 api 叶/nop-api-core 触碰与否；live 2026-11-01 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 触 `_` 生成物？ | 触三 api 叶（nop-http-api/nop-rpc-api/nop-integration-api）？ | 触 nop-api-core？ | 判定依据与条件申报 |
|---|---|---|---|---|---|---|---|---|
| 1 | MA-01（C1-008+C3-018+C6-020） | nop-vertx-mqtt-server main java | removeConnection 属主校验 + acceptEndpoint 顺序 | 否 | 否 | 否（三文件均在 nop-vertx-mqtt-server impl 叶；IMqttConnection 接口契约零变更） | 否 | 纯 Java 修复；`find` 亲证模块零 `_` 文件零 orm |
| 2 | MA-03（C3-004+C6-006） | 三 http client impl 叶（apache/jdk/okhttp）+ helper | 收尾重活移出回调线程 + 内层取消 | 否 | 否 | 否（三实现均 impl 叶；`IHttpClient` 接口签名零变更；sidecar 逻辑为 impl 内部面） | 否 | 纯 Java 修复；CL-021 配置面如收敛亦在 apache impl 叶内（HttpClientConfig 为 impl/config 源非 api 叶——live 亲读裁定） |
| 3 | MA-07（C1-001+←C6-014） | nop-rpc-simple main java | 幂等 init + 世代守卫 | 否 | 否 | 否（SimpleRpcClientFactory 为 nop-rpc-simple impl 叶公开运行期 API——**字段/方法可见性零提升、`newInstance` 签名零变更**；WI 2.1 CL-024 站点邻接非同缺陷，nop-rpc-api 零触碰） | 否 | 纯 Java 修复；审计风险行「公开运行期 API」语义 = 修复须保持外部调用形态兼容（幂等化对单次调用方可观测形态零变化） |
| 4 | C6-017 | nop-netty main java | bind 失败组清理 | 否 | 否 | 否（NettyTcpServer 为 nop-netty impl 叶） | 否 | 纯 Java 修复 |
| 5 | C6-018 | nop-integration-sftp main java | 构造期 session 回收 | 否 | 否 | 否（SftpClient/SftpConfig 为 nop-integration-sftp impl 叶手工维护源——`IFileServiceClient` 契约零变更，**nop-integration-api 零触碰**） | 否 | 纯 Java 修复；R2-19 同文件先例（2522 PA 判定表成员 6 同形判定） |
| 6 | C6-019 | nop-integration-email-tencent main java | finally shutdown 配对 | 否 | 否 | 否（TencentEmailSender 为 nop-integration-email-tencent impl 叶） | 否 | 纯 Java 修复；Tencent SDK 调用形态零变更（shutdown 为 SDK 官方契约方法） |

**结论：头部零 orm 触碰、零 `_` 生成物触碰、零 nop-api-core 触碰、**三 api 叶零触碰声明按字面成立**（6/6 成员全部 impl 叶——与 R2-19 的 FileDownloadSink 张力不同，本批无 api 叶触碰点，预设即终态）；批次 PA=无[batch-proposal R2-20 行显式「无」]零条件申报触发。**先例申报义务成文**：执行期如任何修复需触三 api 叶/nop-api-core（例如 sidecar 收尾链重构若被裁定上移到共享 api 面），须沿 R2-19 裁定⑥先例（batch-proposal :299）在本 plan + 设计文档 + owner doc 契约句 + 回归测试四件证据链下 plan-first 申报，禁静默扩权。站点分布于 nop-vertx-mqtt-server（1 单三文件）、nop-http 三 client impl 叶 + helper（1 单）、nop-rpc-simple（1 单）、nop-netty（1 单）、nop-integration-sftp（1 单）、nop-integration-email-tencent（1 单）——六载体模块六单零交叉（MA-03 三实现同 diff 为单工单内三叶）。

### Baseline 勘误（输入不一致登记）

1. **E3 验证命令盲区勘误（批次定义层，本 plan 最高优先勘误）**：batch-proposal R2-20 行验证命令「nop-network / `./mvnw test -pl nop-network -am`」照录为 E3——但 live pom 亲读证实 `nop-network` 聚合器六 module **不含 nop-integration 聚合器**，6 成员中 2 个载体（C6-018 sftp + C6-019 email-tencent）不在该命令触达面（4 载体 MQTT/三 http client/rpc-simple/netty 在 reactor 内——盲区较 R2-19 的 4/7 收窄为 2/6）。处置：E3 照录执行（批次 SSOT 验证命令不改写）+ **Phase 3 强制补充焦点腿** `./mvnw test -pl nop-integration/nop-integration-sftp,nop-integration/nop-integration-email-tencent`（基线 30 tests EXIT=0 在案）+ Phase 4 回写时在 roadmap 8.29 状态注记与 batch-proposal R2-20 行附近登记「R2-20 验证命令实触 4/6 载体，sftp/email 两载体经补充焦点腿覆盖」勘误注记（沿 2522 勘误 1 先例形态）。
2. **成员行号漂移勘误（审计基线 2026-10-21 b63e4cbe85 → live d4c32127ad；漂移根因 = R2-19/plan 2522 修复 landed + 既有注释块）**：SftpClient openConnection 审计 :131-166 → live :131-171（+5——host-key WARN 块与 C3-024 注释 landed；session.connect 审计 :163 → live :168 / channel.connect 审计 :165 → live :170；close() 审计 :309-319 → live :314 起）；JdkHttpClient sidecar 审计 :455-458/:475-487 → live :490/:511-515（+~35——C3-002/C6-001/C3-012/C3-001 修复行 landed）；OkHttpClientImpl sidecar 审计 :212-215/:239-252 → live :224/:251-256（+~12——同源）；ApacheHttpClient sidecar 调用审计 :330-332 → live :332（fetchSidecarChecksum :370-383 **零漂移**）；NettyTcpServer doStop 审计 :310-329 → live :311-329（+1，doStart :135-141/:165-171/两抛出点 :187-196 逐行亲读吻合）；TencentEmailSender sendEmail 审计 :167-198 → live :167-197（方法体 :197 止、文件 :198 类闭——javadoc 行差口径）。**MQTT/SimpleRpcClientFactory 两站点零漂移**（MqttSessionManager :33-40/:42-45、VertxMqttServer :116-126、MqttConnection :211-224、SimpleRpcClientFactory :112-129/:136-146/:156-172 全部逐一回码吻合）。全部为行号漂移，缺陷机制零漂移（六站点逐行亲读在位）。
3. **exit criteria 括注归属勘误（batch-proposal L94 文本层，成员集合零变更）**：L94 exit criteria「bind 失败释放事件循环组（对照 C6-014 正确兄弟写法）」的括注按 live 裁定归属 **MA-07 幂等 init 条目**——C6-014 已并入 C1-001（merge-adjudication §六 :349「→C1-001（复核已定）」，同站点同本体），其正确兄弟 = `SimpleRpcServer.init` 的 Guard 防重复 start（C6-014 失效面枚举②明文）；而 C6-017 bind 失败清理的自然对照 = 同文件 doStop 清理清单 + `SocketServer.doStart` 已闭合兄弟（C6-017 失效面枚举③明文）——L94 括注系列表拼接错位非缺陷登记错误。处置：exit criteria 六项照录不拆改（批次 SSOT）；括注语义归属以本节为勘误登记，裁定③按「对照 SimpleRpcServer Guard 兄弟」执行、裁定④按「对照 doStop 清理清单/SocketServer 兄弟」执行；Phase 4 回写时不改 L94 原文，勘误注记随 roadmap 8.29 状态列登记。
4. **C10 半边口径**：C10-006 本批仅承载 **MA-01 主半边**（三登记单 diff 红钉 + 防过修钉；MA-09/C1-018/C3-022 半边与 C3-021 的 RP-1 改写义务[A2 补登]→ R4-26 不代做）；C10-004 本批仅承载 **C6-017/C6-018/C6-019/MA-03 四成员半边**（C6-005+C3-024 半边已由 plan 2522 落地 fixed——本批零回退义务；MA-16 半边→ R4-26 不代做）；C10-008 本批仅承载 **MA-07 主半边**（C1-005/C1-007/C3-009/C6-010/C6-015 半边→ R4-26/R4-27 rpc-simple 批不代做）；C10-013 为 C6-019 **保护面注记**（cra-index :294 行内明文——本批 shutdown 断言钉落 C10-004 面；C10-013 本体 C3-025→ R4-27 不代做）。Phase 4 收敛注记按此书写。
5. **B1-06 登记体系口径**：inventory-p1 双判级登记（apache P1/jdk,okhttp P2）→ family-clustering :292（4.3-MA-03 族行）→ :517（B1-06 终裁：工单 P1 apache 面承载/jdk,okhttp P2 备注随单）→ :568 §三.5#4（4.4-C4-001 并档各自判级维持）→ cra-index :275 行内「双判级 apache P1/jdk,okhttp P2 留 7.1」并存。本批以 family-clustering :517 为终裁 SSOT（batch-proposal R2-20 标题「B1-06」指向）；Phase 4 cra-index 回写时 :275 行内注记改写为「B1-06 终裁落账（工单 P1 apache 面承载；jdk/okhttp P2 备注随单）」终态，inventory-p1 历史登记不改（时效性文档）；成员集合本身不变更（本批 finding 清单 6 项照录）。
6. **vertx-mqtt 部署包络确认项口径**：§三.5#9(b)（family-clustering :568）终裁「维持已定判级不改（MA-01 P1 维持）+ 登记为 owner 部署包络确认项」——确认动作为**申报性非裁定性**：本批义务 = Phase 4 登记确认申报（零装配 live 证据 + MA-01 功能后果主路径论证）留 owner 裁决；本 plan 与本批修复均**不得**以零装配为由单方降档或搁置 MA-01（「本轮不单方面改变已定跨维判级」约束）——MA-01 修复按 P1 全条执行与申报并行不悖。
7. **E3 上游链与环境三联预登记（批前既有，沿 plan 2512–2522 已知面）**：`./mvnw test -pl nop-network -am` 按 pom 亲读上游闭包 = nop-kernel（nop-api-core/nop-commons）/nop-core/nop-config 及传递依赖——已知四面 flake 中**仅 nop-core `TestResourceCacheInvalidationChain` 负载 flake 在闭包内**（2517–2522 六批留痕先例，2522 r1 最新同面：r1 3 失败→solo 4/4 绿→r2 全链绿三联）；debezium docker fork timeout/stream-runtime E2E 收敛/auth MFA TOTP 窗口三面不在闭包；sftp/email 焦点腿上游闭包 = nop-integration-api/nop-ioc/nop-credential-api 链（nop-core 同面在闭包内）。上游环境抖动按「stash 基线同红 / solo 绿 / 恢复复跑全绿」**三联裁定**处理并留痕，不得静默重跑或跳过。
8. **测试资产盘点勘误（2522 修复后 live 形态）**：nop-network 基线 215 tests（2522 批前 209 +6 = C3-001/C3-005/C6-005 三钉族 landed——2522 计数口径演进非漂移）；vertx-mqtt 5 类 18 用例（任务书口径亲读吻合）；rpc-simple 1 类 1 用例（C10-008 结论 live 复核维持）；email 1 类 11 用例；sftp 3 类 19 用例（2522 后）[R1 Minor-F4 更正]。红钉形态分层：红①③④⑤⑥**当前即红**（先行失败测试形态——C10-004/006/008 建议形态零并发可构造）；红②为缺陷态注入红（loopback 悬挂端点确定性构造非竞态红）。2522 落地的 feishu/sftp/okhttp/http-api 钉族（`TestFeishuClientLifecycleConcurrency`/`TestSftpClientTimeout_C3_024`/`TestOkHttpOnResponseTermination_C3_001`/`TestFileDownloadSinkVerifyRename_C3_005`/`TestBase64FormUploadStreamClose_C6_005` 等）零回退义务在案。
9. **[设计文档起草增补条款（预留）]** 裁定①触碰 acceptEndpoint 顺序与 C1-018（R4-26）同站点 adjacent——设计文档须给出顺序调整的窗口论证表（窗口①消除 vs C1-018 close-回调丢失窗口的影响面登记）；裁定②触碰下载收尾链与 2505 body-phase/2522 verifyAndRename 已落行相邻——设计文档须给出收尾段重排后的既有钉兼容性核对表；裁定③触碰 SimpleRpcClientFactory 公开运行期 API 形态——设计文档须给出消费方兼容性声明（XLangDebugConnector 面）；既有钉零回退。

10. **[R1 Major-F1 勘误：HC5 ioThreadCount 缺省值事实更正]** 审计与本 plan 初稿引「HC5 ioThreadCount 缺省即 1」——运行时反射实测（httpcore5 5.3.4）= `Runtime.availableProcessors()`（`IOReactorConfig$Builder.defaultMaxIOThreadCount = -1` → 缺省取 CPU 数；.m2 5.1.3–5.4.3 全版本 javap 同形态）；「无配置覆写路径」成立（HttpClientConfig 缺省 0、仅 >0 覆写）。机制修正：多核缺省下嵌套 sidecar 请求由其他 dispatch 线程服务、不构成缺省全停摆——生产影响 = 每次下载占用一个 reactor 回调线程 60s（30s×2）+ C6-006 内层滞留；全停摆为 ioThreadCount=1 配置面（CL-021）形态。红②悬挂红判据不受影响（悬挂在服务端侧）。Phase 4 cra-index :275 finding 文本勘误义务扩及本条。

## Goals

- Phase 1 设计裁定文档一次裁决 7 项裁定（裁定①–⑦，6 成员各一条 + 测试缝/零回退钉清单，含 MA-01 三登记单 diff 绑定与 MA-03 三实现同 diff/B1-06 落账绑定）+ 独立 draft review 通过，无临场发明空间：①MA-01 removeConnection 属主校验形态+acceptEndpoint 顺序调整（窗口①②单 diff）②MA-03 sidecar 探测移出回调线程形态+超时内层取消（B1-06 承载）③MA-07 幂等 init 守卫形态+世代令牌语义定位（B1-06 之外的 CL-024 原始站点承载）④C6-017 bind 失败组清理清单与所有权纪律⑤C6-018 构造期回收形态（R2-19 基线续作）⑥C6-019 SesClient 配对形态（finally shutdown vs bean 单例）⑦测试缝与红①–红⑥驱动器表+零回退钉清单——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。设计文档拟名 `ai-dev/design/concurrency/mqtt-session-ownership-callback-thread-offload-and-connection-lifecycle-concurrency.md`（Phase 1 产出物，前向引用；命名裁定：MQTT 会话属主（MA-01 域）+ 回调线程卸载（MA-03 域）+ 连接生命周期（MA-07/C6-017/C6-018/C6-019 域）三域合一，与现有 concurrency 设计集命名风格一致；如内容演进更适可调，调整时在本 plan 登记命名裁定）。
- 6 项成员全部修复并各带回归测试（rule 25）；C10 收口门（C10-006 MA-01 面/C10-004 四成员半边/C10-008 MA-07 半边，三条成员面[勘误 4 口径]）随本体同 diff 落地（E2）。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红⑥逐成员登记：红① 属主红（MA-01：单线程交错脚本 addConnection(B)→subscribe(B,filter)→removeConnection(A)→断言 B 的订阅仍在——缺陷态 ：44 无条件清红（**当前即红=先行失败测试**，C10-006 建议①形态零并发可构造）→属主校验绿；窗口①第二钉 = CONNACK 后 subscribe 先落表再 addConnection→断言订阅仍在[当前即红]；**绿路径[R1 Major-F2]：该钉驱动层级 = VertxMqttServer.acceptEndpoint 集成级交错模拟（reorder 后「SUBSCRIBE 早于注册」经真实路径不可达→钉转绿）——禁在 MqttSessionManager.addConnection 上补属主语义（超出 merge-adjudication MA-01 修复点，未申报扩张禁令）**；既有 `testSessionTakeoverClosesOldConnection`/`testSessionRemovedOnConnectionClose` 零回退禁弱化）；红② 停摆红（MA-03：loopback 悬挂 sidecar 端点注入 → 断言下载 promise 有限时间完成且回调承载线程不被阻塞/总时长上界（如 5s 预算）——缺陷态 apache 面阻塞至 30s×2 红→移出回调线程绿；**挂死类：失败模式=超时，给足有限 timeout（建议 ≥5s）并在钉内注释失败模式语义**[沿 2522 红④先例]；C6-006 半边第二钉 = get 超时后断言内层请求已取消（连接池无滞留在途）；三实现各自跑同型钉）；红③ 幂等红（MA-07：newInstance()×N 后断言 interceptors 长度恒定 + timer 实例唯一（反射读字段，C10-008 建议①形态）——缺陷态线性累积/双建红（**当前即红**）→幂等守卫绿；destroy 后 newInstance fail-fast 钉随行）；红④ 泄漏红（C6-017：预置占用端口再 start → 断言抛出后存活 event loop 线程数≤基线+常量（线程名/计数断言，C10-004 建议⑤形态）——缺陷态线性增长红（**当前即红**）→bind 失败清理绿）；红⑤ 回收红（C6-018：recording-jsch 失败注入点上移到 channel.connect() → 断言 `session.disconnect()` 被调——缺陷态零回收红（**当前即红**，C10-004 建议②形态「注入点恰好早于缺陷窗口」修正）→构造回收绿）；红⑥ 配对红（C6-019：seam 覆写 createClient 产物 → 断言 shutdown 被调（成功+异常两路径）——缺陷态零 shutdown 红（**当前即红**，C10-004 建议③形态）→finally 配对绿；catch 特判分支行为保持防过修钉）。
- B1-06 终裁落账（条款成文见 Current Baseline 节，Phase 4 cra-index :275 注记改写落账）；vertx-mqtt 部署包络确认申报动作落账（Phase 4，勘误 6——申报性非裁定性）。
- 联调纪律显式守约（本批九条）：**MA-01 三登记一处闭合（单 diff 双窗口）**；**MA-01×C1-018/MA-09/C6-028 相邻不并族**；**MA-03×C6-006 一处闭合+三实现同 diff（B1-06 备注随单非修复豁免）**；**MA-07×CL-024 站点邻接+C6-014 并入+SimpleRpcServer 兄弟零顺手修**；**C6-017 修复边界仅 doStart（NettyTcpClient/VertxMqttServer 零触碰）**；**C6-018×C3-024[R2-19] 同文件基线续作零回退**；**C6-019×Yunpian 兄弟对照+C3-025 不并族**；**C10 三条同 diff（E2，半边口径按勘误 4）**；**无跨批硬依赖（可并行）+ vertx-mqtt 部署包络确认随批申报**。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-network.md`（六落点——:57 MQTT 属主校验契约句补强 + :46 sidecar 回调线程契约句补强 + :73 SFTP 节内 C6-018 构造回收补句[边界注记改写为已落地终态] + 新增「Simple RPC 客户端工厂生命周期契约」小节 + 新增「Netty TCP 服务端启动失败资源回收契约」小节 + 新增「Tencent 邮件发送器资源生命周期契约」小节）+ `docs-for-ai/04-reference/source-anchors.md`（新增 `NETWORK-CC-002` 锚点行，NETWORK-CC-001 :105 之后按实际行号插入）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-26/R4-27 全部成员**（G67 a/b 两束 28 项：MA-09 dispatcher 配对/C1-018 回调挂载时序/MA-10 NettyTcpClient stop×reconnect[C6-022]/MA-16 HttpClient selector/C1-005/C3-009/C6-010/C6-015/C3-025 吞异常/C6-028 doStop 清表/MA-14 listenFuture 等全部 P2/P3 面）——特别地 **C1-018（acceptEndpoint 回调挂载时序倒置）不代做**：裁定①顺序调整仅消除窗口①，close-回调早于 onCloseCallback 注册的丢失窗口留 R4-26（对齐条款第 2 条）；**C3-025 不代做**：C6-019 修复只落 finally shutdown，catch 吞异常面零触碰。
- **不做 MA-02/C6-003/C6-013 下载链 P1/P2 面**（R2-02 已修 MA-02 零回退义务；C6-003 416 重传/C6-013 直写残留归下载链批/R4-27——MA-03 收尾段重排禁顺手触碰该两本体）。
- **不做 2.1-CL-024 本体**（nop-cluster rpc-cluster 池累计面——4.2 侧已独立复现登记；本批仅落 MA-07 原始站点面，nop-cluster 零触碰）。
- **不做 SimpleRpcServer 同族面**（C1-007 start 守卫非原子/C6-015 stop 后不可重启——P3 rpc-simple 批次 R4-26/R4-27；MA-07 修复禁顺手扩 SimpleRpcServer.init prepend 或 start Guard——兄弟对照零代码触碰）。
- **不做 C6-022/MA-10**（NettyTcpClient stop×reconnect 同构 P2——family-clustering 同构异站零关联；`NettyTcpClient.java` 零触碰）。
- **不做 CL-021 本体独立工单**（ioThreadCount=1 自死锁配置面——MA-03 吸收线索随工单收敛注记；独立 CL 立项/配置项新增与否由裁定②域定夺，不预置新配置面）。
- **不做 MA-01 以外的 vertx-mqtt 面**（C3-022 packetId 碰撞/C6-023 listenFuture/C6-026 dispatcher 配对/C6-028 doStop 清表——R4-26/R4-27 条化面；MA-01 修复禁顺手清 sessionManager 表）。
- 不做平台层改码：nop-api-core/nop-core 平台设施零触碰；`IMqttConnection`/`IHttpClient`/`IFileServiceClient` 契约签名零变更（SimpleRpcClientFactory 修复保持外部调用形态兼容[单次调用方可观测形态零变化]）；不做 nop-integration 其余叶（feishu[2522 已收口]/sms/oss/file-local/zxing）任何触碰。
- 不触碰 `_` 前缀生成物；R4-26/R4-27 批外成员本体；ChannelConnectorManager 渠道栈激活面；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-network/nop-vertx/nop-vertx-mqtt-server/src/main/java/io/nop/vertx/mqtt/server/impl/`：`MqttSessionManager.java`（裁定①——removeConnection 属主校验）、`VertxMqttServer.java`（裁定①——acceptEndpoint 顺序调整）、`MqttConnection.java`（裁定①[R1 Minor-F3 措辞更正：`endpoint.accept()` 移出构造函数属构造行为变更非注记]；禁触碰 C1-018 close 回调挂载面）。
- `nop-network/nop-http/`：`nop-http-client-apache/.../ApacheHttpClient.java`（裁定②）、`nop-http-client-jdk/.../JdkHttpClient.java`（裁定②）、`nop-http-client-okhttp/.../OkHttpClientImpl.java`（裁定②）、`nop-http-client-apache/.../ApacheHttpClientHelper.java`（裁定②——CL-021 配置面如定夺收敛）。
- `nop-network/nop-rpc/nop-rpc-simple/src/main/java/io/nop/rpc/simple/SimpleRpcClientFactory.java`（裁定③）。
- `nop-network/nop-netty/src/main/java/io/nop/netty/tcp/NettyTcpServer.java`（裁定④）。
- `nop-integration/nop-integration-sftp/src/main/java/io/nop/integration/sftp/SftpClient.java`（裁定⑤——SftpConfig 零新增配置项）。
- `nop-integration/nop-integration-email-tencent/src/main/java/io/nop/integration/email/tencent/TencentEmailSender.java`（裁定⑥）。
- 测试树：vertx-mqtt-server（红①两钉 + 防过修钉 + 既有 18 用例零回退）、三 http client impl（红②三实现悬挂端点钉 + 内层取消钉 + 下载链既有钉零回退）、rpc-simple（红③幂等/fail-fast 钉）、netty（红④线程组终止钉 + 既有 4 类零回退）、sftp（红⑤注入点上移钉 + TestSftpClientTimeout 钉族零改写）、email-tencent（红⑥shutdown/防过修钉 + 既有 11 用例零回退）；既有 245 tests 零回退（nop-network 215 + sftp/email 30）。
- 设计文档：`ai-dev/design/concurrency/mqtt-session-ownership-callback-thread-offload-and-connection-lifecycle-concurrency.md`（新建；裁定①–⑦+测试缝+零回退钉+MA-01 三登记/MA-03 三实现/B1-06 对齐条款，最终状态写法；命名调整须登记裁定）。Owner doc：`docs-for-ai/03-modules/nop-network.md`（六落点见 Goals）、`docs-for-ai/04-reference/source-anchors.md`（NETWORK-CC-002 行）。

### Out Of Scope

- Non-Goals 所列全部项；R4-26/R4-27 批次工单；CL-024 本体/C3-025 本体/C6-022 本体/C1-018 面；nop-api-core/nop-core 平台设施本体；`model/*.orm.xml` 与 `_` 前缀生成物（预设零触碰）；三 api 叶（预设零触碰——如执行翻转沿裁定⑥先例申报）；`IMqttConnection`/`IHttpClient`/`IFileServiceClient` 契约演进；nop-integration 非 sftp/email-tencent 叶模块；nop-cluster/nop-socket 模块。

## Execution Plan

### Phase 1 - MQTT 会话属主/回调线程卸载/连接生命周期并发设计裁定文档 + 独立评审

Status: completed
Targets: `ai-dev/design/concurrency/mqtt-session-ownership-callback-thread-offload-and-connection-lifecycle-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① MA-01 removeConnection 属主校验+acceptEndpoint 顺序（三登记一处闭合）**：属主校验形态[预设 = 仅当 sessions 当前值==conn 才清订阅——compute 两参同法 vs get-校验-remove 形态一次定夺]；`acceptEndpoint` 先注册会话（addConnection+回调挂载）再 `endpoint.accept()`[预设]——窗口①消除论证；C1-018 相邻面影响论证表（顺序调整对 close-回调丢失窗口的影响登记，不代做[R4-26]）；C6-028 doStop 清表面零触碰声明；窗口②与窗口①的红钉形态（C10-006 建议①单线程交错脚本）
  - [x] **裁定② MA-03 sidecar 探测移出回调线程+超时内层取消（B1-06 承载）**：收尾重活移出形态[预设 = handleDownloadResult 重活段整体投递独立 executor、回调线程只做 promise 完成——池选择（GlobalExecutors.globalWorker[audit 建议形态] vs 专用池）vs 非阻塞链式 whenCompose 一次定夺] + get 超时路径内层 `cancel`（C6-006 闭合）[预设]；三实现同 diff 改写矩阵（apache/jdk/okhttp 收尾链形态差异表）；CL-021 ioThreadCount 配置面收敛一次定夺（文档化/配置项/零动作三选）；B1-06 落账口径+§三.5#4 并档注记；`TestBodyPhaseCancel_C3_002` 等下载链钉兼容核对表（勘误 9）
  - [x] **裁定③ MA-07 幂等 init+世代守卫（CL-024 原始站点承载）**：幂等守卫形态[预设 = volatile inited + synchronized 或 AtomicBoolean CAS 一次定夺；对照 SimpleRpcServer Guard 兄弟（勘误 3）]；拦截器不可变快照形态（装配一次化+unmodifiableList 交 handler）；timer/executor 原子创建惯用法；destroyed 世代守卫形态（volatile+destroy 后 newInstance fail-fast[预设]——exit criteria「世代令牌」语义定位声明，与 feishu lifecycleGeneration 语义不同族禁照抄）；消费方兼容性声明（XLangDebugConnector 面，勘误 9）
  - [x] **裁定④ C6-017 bind 失败组清理**：清理清单[预设 = 按 doStop :311-329 逆序：globalTrafficShapingHandler.release() + 自建 workerGroup.shutdownGracefully() + bossGroup.shutdownGracefully()]；两抛出点（catch :189/isSuccess :195）覆盖矩阵；外部注入 workerGroup 所有权纪律（只关 boss[预设]）一次定夺；NettyTcpClient/VertxMqttServer/SocketServer 零触碰声明
  - [x] **裁定⑤ C6-018 构造期会话回收（R2-19 基线续作）**：回收形态[预设 = openConnection 内 session.connect 成功后的步骤 try/catch 包裹、失败 session.disconnect() 再抛——disconnectQuietly 提取 vs 内联一次定夺]；ERR_SFTP_CONNECT_FAIL 包装语义保持；超时接线/超时异常路径零回退声明（TestSftpClientTimeout_C3_024 钉族零改写）；SftpConfig 零新增配置项声明
  - [x] **裁定⑥ C6-019 SesClient 生命周期配对**：finally `client.shutdown()`[预设——最小契约面变更] vs bean 级单例（OssFileServiceClientFactory 参照、凭证轮换可见性变化属设计层）两案一次定夺禁第三形态；catch 特判分支（EmailAddressIsNULL/FrequencyLimit）行为保持防过修声明（C3-025 不并族）
  - [x] **裁定⑦ 测试缝与红①–红⑥驱动器表+零回退钉清单**：红①–红⑥ 测试形态逐条（含缺陷态红判据[当前即红五条+注入红一条] + 挂死类失败模式=超时给足有限 timeout + 全部 plain-JVM 可跑面声明[无 docker 门控；sftp/email 走补充焦点腿]）；红②悬挂端点 mock 形态一次定夺[预设 = loopback HttpServer 挂起 handler 零新依赖]；红⑤ recording-jsch 注入点上移与既有 TestSftpClientTimeout 钉协同形态；零回退钉 = nop-network 215 tests + sftp/email 30 tests（双 log 路径），其中点名：vertx-mqtt 18 用例（TestMqttServerFix takeover/close 两钉禁弱化）/`TestNettyTcpServerFix`/`TestSimpleRpcClient`/`TestTencentEmailSenderCredential`/`TestSftpClientCredential`/`TestSftpHostKeyChecking`/2505 钉族/2522 钉族；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动；记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑦全部项且每条有唯一预设默认分支，独立 draft review 记录在案（文件命名如调整已登记裁定）
- [x] 裁定①含 MA-01 单 diff 双窗口绑定与 C1-018 影响论证；裁定②含三实现同 diff 矩阵与 B1-06 落账口径；裁定③含「世代令牌」语义定位与消费方兼容声明；裁定④含所有权纪律；裁定⑤含 R2-19 基线续作零回退声明；裁定⑥含两案定夺；裁定⑦含红①–红⑥驱动器表与零回退钉全列
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 6 成员实现 + 回归测试（按子域分组）

Status: planned
Targets: `nop-network/nop-vertx/nop-vertx-mqtt-server/`、`nop-network/nop-http/`（三 impl 叶+helper）、`nop-network/nop-rpc/nop-rpc-simple/`、`nop-network/nop-netty/`、`nop-integration/nop-integration-{sftp,email-tencent}/`（各自 main + test 树）

- Item Types: `Fix`（6 项 P1 均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [ ] 组 A MQTT 会话属主（nop-vertx-mqtt-server）：MA-01（裁定①，三登记单 diff——红①双窗口钉[当前即红]+防过修钉；C10-006 MA-01 面随同 diff；C1-018/MA-09/C6-028 零触碰核对）；既有 18 用例零回退（takeover/close 两钉禁弱化）
- [ ] 组 B HTTP 下载收尾线程解耦（nop-http 三 impl 叶）：MA-03（裁定②，红②三实现悬挂端点钉[挂死类失败模式=超时]+C6-006 内层取消钉——三实现同 diff 禁只修 apache；B1-06 备注随单注记；CL-021 收敛随裁定②）；2505 body-phase/2522 下载链钉族零回退
- [ ] 组 C rpc-simple 工厂生命周期（nop-rpc-simple）：MA-07（裁定③，红③幂等/fail-fast 钉[当前即红]——←C6-014 一处闭合；SimpleRpcServer 兄弟零顺手修核对；消费方兼容性声明执行形态留痕）
- [ ] 组 D netty 启动失败回收（nop-netty）：C6-017（裁定④，红④线程组终止钉[当前即红]——两抛出点覆盖；NettyTcpClient 零触碰核对）
- [ ] 组 E nop-integration 资源配对（sftp + email-tencent，E3 盲区焦点腿对应）：C6-018（裁定⑤，红⑤注入点上移钉[当前即红]——R2-19 基线续作、超时钉族零改写核对）；C6-019（裁定⑥，红⑥shutdown/防过修钉[当前即红]——catch 语义零触碰核对）；C10-004/C10-013 半边随同 diff（勘误 4 口径）
- [ ] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①–红⑥快照落 `_tmp/` 并登记（`_tmp/red-green-2523-summary.md` 总册 + 逐 trial 日志；sftp/email 腿日志单列——E3 盲区对应）；**变异探针纪律**：凡以「注释/删除目标行 → 跑钉子 → 恢复」验证回退必红者，恢复动作必须以断言方式确认目标可执行行真实存在（grep/读取校验），防止恢复脚本半写入造成假绿（R5 教训成文义务）；并发钉设 trial 上界防噪声化（红①② trial 上界在裁定⑦成文）；挂死类红钉（红②）timeout 预算与失败模式注释逐钉留痕
- [ ] **端到端验证**（rule 22）：MQTT 会话链 = 接管全链钉（新连接接管→SUBSCRIBE→旧连接迟到 close→新连接订阅保留→findConnections 命中→下行送达，从 addConnection 入口到下行投递出口的完整路径）；下载收尾链 = sidecar 悬挂全链钉（loopback 下载→悬挂 sidecar→下载 promise 限时完成→回调线程未被阻塞→内层请求取消，从 handleDownloadResult 入口到连接池出口）；工厂链 = 幂等全链钉（newInstance×N→connect→invoke→断言单次拦截器执行+timer 唯一，从 newInstance 入口到 RPC 调用出口）
- [ ] **接线验证**（rule 23）：裁定①属主校验真实消费断言（过期 close 真不清新订阅，非仅条件分支存在）；裁定② executor 投递真达断言（收尾段真在非回调线程执行——线程名断言）；裁定③幂等守卫真拦截断言（第二次 init 真短路非仅字段存在）；裁定④清理真执行断言（组真 shutdown 非仅调用在位）；裁定⑤ disconnect 真调用断言；裁定⑥ shutdown 真配对断言（成功+异常双路径）
- [ ] **无静默跳过**（rule 24）：裁定①过期清理分支不得静默吞（不属主即跳过清理属正常语义——注释声明；禁无条件吞日志）；裁定②超时路径必取消内层（禁 catch 后仅记日志不取消）；裁定③destroy 后 newInstance 必 fail-fast（禁返回半初始化实例）；新增公共分支无空方法体/TODO

Exit Criteria:

- [ ] 6 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 收口门（3 条成员面，按勘误 4 半边口径）同 diff 落地
- [ ] 必红证据（E2）：红①–红⑥均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记（红①③④⑤⑥当前即红形态标注）
- [ ] 零回退实证：基线 245 tests（nop-network 215 + sftp/email 30）+ 既有钉零回退（钉零回改逐条 git diff 为空核对；2505/2522 钉族/vertx-mqtt takeover 两钉/TestSftpClientTimeout 钉族零触碰核对）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-network -am` 批行验证命令执行留痕（模块/测试数/退出码/快照路径；**E3 实触 4/6 载体登记[勘误 1]**）+ **补充焦点腿** `./mvnw test -pl nop-integration/nop-integration-sftp,nop-integration/nop-integration-email-tencent` 留痕；上游环境抖动按三联裁定留痕（勘误 7）；实际触达模块以 git diff 为准逐条登记（预期 vertx-mqtt-server/http 三 impl+helper/rpc-simple/netty/sftp/email-tencent 七模块 main+test，无其他）；零触碰确认：产品 diff 不含 `_` 前缀生成物手改、不含 `model/*.orm.xml`、不含 nop-api-core/nop-core 平台设施、不含三 api 叶、不含 R4-26/R4-27 批外成员本体（NettyTcpClient/SimpleRpcServer/MqttServerMessageService/TencentSmsSender 面）；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向；执行中断时本 Phase 收束前回补本清单勾选，禁跨 Phase 欠账）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-network 聚合全量测试 + sftp/email 补充焦点腿；`docs-for-ai/03-modules/nop-network.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（如全聚合测试通道被零 diff 上游已知 flake 阻断，授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，沿 plan 2497–2522 Phase 3 先例声明支路理由与逐面三联裁定留痕）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-network -am` BUILD SUCCESS 留痕（模块/测试数/退出码/快照路径；上游抖动三联裁定；E3 盲区登记）+ **补充焦点腿（勘误 1 强制项）**：`./mvnw test -pl nop-integration/nop-integration-sftp,nop-integration/nop-integration-email-tencent` BUILD SUCCESS 留证；实际触达模块以 git diff 为准逐条登记；零触碰确认（生成物/orm 模型/平台设施/三 api 叶/R4-26·27 批外成员本体/既有钉面）
- [ ] Owner doc 更新：`nop-network.md` 六处落点——①:57 MQTT 行补属主校验契约句（会话接管/断连清理按属主绑定语义——过期清理不清新订阅+双窗口语义）；②:46 sidecar 行补回调线程契约句（探测在非回调线程执行+超时内层取消+ioThreadCount 语义如收敛）；③:73 SFTP 节内补 C6-018 构造回收句（既有边界注记改写为已落地终态）；④新增「Simple RPC 客户端工厂生命周期契约」小节（幂等 init/世代守卫/destroy 后 fail-fast）；⑤新增「Netty TCP 服务端启动失败资源回收契约」小节（两抛出点清理/所有权纪律）；⑥新增「Tencent 邮件发送器资源生命周期契约」小节（finally shutdown 配对/Yunpian 兄弟对照/catch 语义边界）；`source-anchors.md` 新增 `NETWORK-CC-002` 锚点行（六成员文件行号锚 + 契约 + 回归钉族 + 设计权威指针，修复后 live 亲读核实；NETWORK-CC-001 :105 之后按实际行号插入）；check-doc-links --strict 退出码 0
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-network --severity high` 与 `--module nop-integration --severity high` 逐条登记（触达面 high=0 或批前既有已裁定——沿 2518–2522 先例口径，注意 shell 管道退出码误读教训，以工具真实退出码为准；nop-integration 已知 1 条批前既有 JavaEmailSender :216 email 叶零触面[2522 Phase 3 留痕]）
- [ ] vertx-mqtt 部署包络确认**申报动作**执行留痕（勘误 6——申报性非裁定性：登记申报记录[零装配证据+MA-01 功能后果论证]与 owner 结论留白位；结论登记归 Phase 4 收口时随 owner 反馈落账）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令 + 补充焦点腿 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由；E3 盲区处置登记
- [ ] nop-network.md 六处契约句落点 + source-anchors.md NETWORK-CC-002 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记
- [ ] 零触碰确认登记（生成物/orm 模型/平台设施/三 api 叶/R4-26·27 批外成员本体/既有钉面）
- [ ] vertx-mqtt 部署包络确认申报记录已登记（Phase 4 结论落账去向显式）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.29 行、cra-index P1 台账 :273/:275/:279/:292/:293/:294 + C10 成员面注记 + B1-06 落账 + batch-proposal 勘误注记、vertx-mqtt 部署包络确认结论

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（属主校验真生效/收尾段真在非回调线程/幂等守卫真拦截/组真 shutdown/disconnect 真调用/shutdown 真配对运行时行为、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-20 六行状态收敛（open→fixed，注记 plan 2523 + closure audit 结论；MA-01 行三登记单 diff 注（**含 :275 finding 文本勘误 10 回写[R1 复核交叉引用]**）记；MA-03 行 **B1-06 终裁落账**[「留 7.1」注记改写终态——条款文本以 Current Baseline 节为 SSOT]；C6-014 并入随 :279 行；C10-004/C10-006/C10-008/C10-013 半边注记按勘误 4 口径）；roadmap 8.29 行 Status → done + 「R2-20 验证命令实触 4/6 载体，sftp/email 两载体经补充焦点腿覆盖」勘误注记 + vertx-mqtt 部署包络确认结论登记（owner 反馈如有）；batch-proposal R2-20 行附近勘误注记（L94 括注归属勘误[勘误 3]——不改 L94 原文）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 复核登记；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 六行收敛 + MA-01 三登记注记 + B1-06 落账注记 + C10 半边注记、roadmap 8.29 = done（含 E3 盲区勘误注记 + 部署包络确认结论）、batch-proposal 勘误注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 6 项成员全部修复：MA-01（removeConnection 属主校验+窗口①消除，三登记一处闭合）、MA-03（sidecar 探测移出回调线程+超时内层取消，B1-06 承载）、MA-07（幂等 init+世代守卫，←C6-014 一处闭合）、C6-017（bind 失败组清理）、C6-018（构造期会话回收，R2-19 基线续作）、C6-019（SesClient finally 配对）
- [ ] batch-proposal R2-20 行 exit criteria 增补逐条达成并留证：MQTT removeConnection 属主校验（三登记一工单）；sidecar 探测移出 I/O reactor 回调线程（apache 面承载 P1，jdk/okhttp P2 备注随单——三实现同 diff）；SimpleRpcClientFactory 幂等 init+世代令牌；bind 失败释放事件循环组（对照兄弟写法——勘误 3 归属裁定执行）；构造期会话回收；SesClient 生命周期配对
- [ ] 对齐纪律守约：MA-01 三登记单 diff 双窗口兑现；MA-01×C1-018/MA-09/C6-028 不并族兑现；MA-03 三实现同 diff（B1-06 备注非豁免）兑现；MA-07 兄弟零顺手修兑现；C6-017 修复边界兑现；C6-018 基线续作零回退兑现；C6-019 catch 语义零触碰兑现；C10 三条同 diff 兑现；PA 零触碰声明闭环（git diff 核对三 api 叶/orm/生成物零触碰）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑥均有先红后绿或变异/回退论证（E2；挂死类失败模式=超时预算留痕；当前即红五条标注）；C10 收口门（3 条成员面）同 diff 落地
- [ ] 设计文档裁定①–⑦成文且经独立 draft review；既有钉（基线 245 tests + 2505/2522 钉族 + vertx-mqtt takeover 两钉 + TestSftpClientTimeout 钉族零触碰）零回退实证
- [ ] 全量构建绿：E1（含授权支路声明[如有]）+ 批行验证命令 BUILD SUCCESS + **sftp/email 补充焦点腿 BUILD SUCCESS**（E3 + 勘误 1 强制项；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-network.md 六处落点 + source-anchors.md NETWORK-CC-002（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0 或批前既有已裁定（逐条登记，真实退出码）
- [ ] vertx-mqtt 部署包络确认申报与结论登记完成（§三.5#9b 随批确认义务——申报记录+owner 结论留痕；不以零装配单方降档）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）属主校验/线程卸载/幂等守卫/组清理/disconnect/shutdown 运行时真实生效（不只类型系统——红绿日志证行为改变），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-network 聚合 + sftp/email 焦点腿）；checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index R2-20 六行收敛（含 B1-06 落账）+ roadmap 8.29 → done（E6）

## Deferred But Adjudicated

### R4-26/R4-27 束成员（G67 a/b：MA-09/C1-018/C3-022/MA-16/C1-005/C3-009/C6-010/C3-025/C6-028/MA-14/C6-022 等 P2/P3 半边）与 CL-024 本体（nop-cluster）

- Classification: `out-of-scope improvement`（batch-proposal §二 R4-26 行 :141 + R4-27 行 :142 + cra-index 对应 P2 台账批前裁定批外面，非本批降级）；Successor Required: `yes`；Successor Path: `ai-dev/plans/` R4-26/R4-27 批 plan（软依赖 8.28/8.29——本批 done 后解除 8.29 侧软依赖）
- Why Not Blocking Closure: 判级对齐零重复——R4-26/R4-27 各成员批次列显式；本批已收敛 R2-20 单元全部 open P1（6 张）；C1-018/C3-025/MA-09/C6-028/C6-022 承接面在本批修复中已登记禁顺手引入接缝（对齐条款第 2/5/6/7 条与 Non-Goals）。

### C3-024 已收口面回退防护与 CL-021 独立配置项（如裁定②零动作）

- Classification: `watch-only residual`（批前裁定：C3-024 超时钉族=R2-19 落地 fixed——本批零回退义务随 Closure Gates；CL-021 独立 CL 线索=审计 CL 台账在案——裁定②若定「零动作/仅文档化」则配置项候选留 CL 轨）；Successor Required: `no`（C3-024 零回退由本批钉守护；CL-021 归审计 CL 台账既有轨迹）
- Why Not Blocking Closure: 批前裁定批外面/已收口面；本批对 CL-021 仅登记「随工单收敛注记」接缝（对齐条款第 3 条），ioThreadCount 语义文档化与否由裁定②域定夺，不影响 MA-03 修复成立。

## Non-Blocking Follow-ups

- **vertx-mqtt 部署包络 owner 反馈跟踪**（§三.5#9b 确认项——本批仅申报，owner 条件化降档裁决如后续作出随 cra-index 行注记演进）：登记申报去向，非 in-scope 缺陷（对齐 CRA 判级与 family-clustering 终裁）。
- **MA-07 消费方演进注记**（XLangDebugConnector 若未来开 logBody/多线程化——修复后的幂等契约已覆盖，无回滚面；登记 dev-tools 消费面即可）：owner doc 微补登记去向。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （draft——收口时填写）
Completed: ——

Closure Audit Evidence:

- Reviewer / Agent: ——（收口时填写）
- Audit Session: ——（如用子 agent，记录 session ID）[R1 Minor-F5 补占位]
- Evidence: ——（收口时填写；`check-plan-checklist.mjs --strict` 退出码；Anti-Hollow 检查结果；Deferred 项分类检查）

Follow-up:

- ——（收口时登记 non-blocking follow-up 或明确写 no remaining plan-owned work）

## Risks And Rollback

- **裁定①窗口①修复的行为面风险**：acceptEndpoint 顺序调整（先注册后 accept）改变「CONNACK 早于注册」现状时序——同 clientId 高频重连场景下新连接 SUBSCRIBE 可能落在注册前窗口（窗口①消除后该窗口收窄但客户端行为不可控）；顺序调整对 C1-018 窗口（close 回调丢失）的影响若论证不足可能引入新时序面——设计文档论证表为 Phase 1 放行前置；回滚 = 顺序调整单点可逆（属主校验部分独立成立），红①双钉防形态漂移。
- **裁定②收尾段重排的回归面风险**：下载收尾链重排（回调线程→executor）触碰 2505 body-phase/2522 verifyAndRename 已落行的执行时序——既有钉（TestBodyPhaseCancel_C3_002 6 钉/下载 fd 钉族）零回退为硬门禁；executor 池选择不当（globalWorker 争用）可能引入新停表面——裁定②池选择一次定夺并留容量论证；回滚 = 投递点单点可逆，红②三实现钉防退化。
- **裁定③公开运行期 API 形态风险**：SimpleRpcClientFactory 幂等化改变「每次 newInstance 重建拦截器列表」现状（可观测形态对单次调用方零变化，但反射型消费方若依赖重复 prepend 行为将漂移——全仓消费方仅 XLangDebugConnector[未开 logBody]已枚举）；destroy 后 fail-fast 对 stop→restart 编排的消费方为行为变更（现库无该编排面）；回滚 = 守卫行 revert 单点可逆，红③钉防漂移。
- **裁定④⑤⑥错误路径清理的副作用风险**：bind 失败清理若误关外部注入 workerGroup（所有权纪律定夺失误）将破坏共享组——裁定④预设只关自建组并留注入场景核对；C6-018 disconnect 回收若与 jsch 内部状态竞态（connect 部分完成后 disconnect 安全性——jsch Session.disconnect 幂等语义）须设计文档登记论证；C6-019 finally shutdown 若 SDK shutdown 抛异常将吞原业务异常（shutdown 包裹 try-catch-ignore 形态或 helper 一次定夺）。
- **E3 盲区的验证完备性风险**：批次验证命令实触 4/6 载体——补充焦点腿为强制项（Phase 2/3 双登记），若执行期遗漏将造成 sftp/email 两成员零验证收口（closure audit 专项核对项）；两模块基线 30 tests 在案为焦点腿绿色参照。
- **红②挂死红的可复现性风险**：缺陷态为确定性停摆（loopback 悬挂端点确定性构造，非竞态红），红证稳定；风险在测试自身超时设计（缺陷态断言必超时红——钉的失败模式是超时而非断言失败，须给足但有限 timeout 并在钉内注释失败模式语义）。
- **上游链环境噪声风险**：`./mvnw test -pl nop-network -am` 触达上游闭包（nop-core/nop-kernel/nop-commons 等），nop-core `TestResourceCacheInvalidationChain` 负载 flake 有 2517–2522 六批先例（勘误 7）——三联裁定执行并留痕；sftp/email 焦点腿上游闭包含 nop-core 同面。
