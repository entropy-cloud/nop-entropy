# 2501 CRA Phase 8.7 — R1-06 沙箱/shell 工具执行链批（R09 沙箱清理族 + B1-03/B1-04 回升行，7 项）

> Plan Status: active
> Draft Review: R1 agent_66909c18（1 Blocker[ISSUE-1 C8-001 本体站点缺失]+1 Major[ISSUE-2 反向钉全组]+8 Minor——修订 agent_1524094d 全量应用[live 锚点回读核实]，active）
> Last Reviewed: 2026-10-24
> Source: roadmap 工作项 8.7（R1-06，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L149，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 R1-06 行（L67：finding ID 清单 7 项 / 模块=nop-ai / 验证命令=`./mvnw test -pl nop-ai -am` / Owner Doc=`docs-for-ai/03-modules/nop-ai.md` / PA=无 / 依赖=无前置（可并行）/ exit criteria 增补）；成员发现详情 = 3.2 审计目录 `ai-dev/audits/2026-10/2026-10-20-0829-deep-audit-nop-ai-toolkit-tools-shell-concurrency/`（C6-001/C6-002/C6-003/C1-001/C4-003）与 3.1 审计目录 `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`（MA-17[C1-007+C7-020 族]/C7-014）；执行基线 @ `e0df517864`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.6 已 done，plan 2495–2500 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2493/2494（批次划分与 Phase 8 展开）；plan 2496（R1-01 绝缘层批，completed——其 nop-ai 面为 3.1-MA-07 六站点周期绝缘，与本批沙箱/shell 面**零交集**，G50 组的 MA-07 成员已闭合，本批只承接 G50 的 R1-06 成分）；plan 2497（R1-02 池设施批，completed——本批不回改设施面）；plan 2498（R1-03 池消费方批，completed——**同文件边界**：其 3.2-C4-004 shell 专用池隔离（提交 `15f65b2c66`）为本批 C4-003 修复的池基线，本批不回退）；plan 2499（R1-04 api-core 批，completed——其 3.2-C4-008 工具取消联动（提交 `276618d9da`）改写了 ToolManagerImpl 排队分支（C1-001 行号漂移成因），本批不得回退取消联动）；plan 2285（WI4 心跳即输入）与 plan 2301（WI25 后台模式）——沙箱进度副本/进程树收割既有修复史（C6-001 现状注记「已修站点残余缺口面」的成因）；**下游交互**：R2-16（3.1 agent 编排批，软依赖本批「同仓沙箱面先行」）、R4-23（3.2 工具输出/路径束 G54，软依赖本批）——两批成员均不属本批，软依赖解除注记归 Phase 5/6
> Protected Area 与授权：本批 **PA 触碰面为零（batch-proposal PA 列 = 无）**——无 plan-first/ask-first 义务。零触碰声明（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物；`nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/beans/ai-toolkit-defaults.beans.xml` 为手维护 beans 装配文件（`_vfs` 为 VFS 资源根，非生成物），若本批涉及装配面修改按 2498 E4 先例豁免登记。

## Purpose

关闭 R1-06 沙箱/shell 工具执行链批的 7 项已确认发现（batch-proposal §二 R1-06 行：P1×6——3.2-C1-001[MA-01 主]、3.2-C6-001[MA-02 主]、3.2-C6-002[MA-03 主]、3.2-C6-003[MA-04]、3.2-C4-003[MA-06 主，B1-03 终裁维持 P1]、3.1-MA-17[B1-04 终裁回升 P1]；P2×1——3.1-C7-014[G50 行，携 3.1 反向钉死 R4 改写义务]）：①3.2 面四工单——ToolManagerImpl 路径串行队列三子缺陷（锁内执行/重入/悬挂 future）与沙箱清理族三工单（reader 泄漏/容器残留/清理线程钉死）全部收敛，沙箱清理族按「超时 destroyForcibly + reader 退出补偿 + kill 失败可见化」三口径统一落地，Docker 侧 F3 残留（Host 已修未同修面）随 3.2-MA-02 补齐；②shell 管道死锁族（MA-06）单 diff 修复——REE 提交中止回滚 + 弃管 close + `0<&1` 拒绝 + C8-001 面收集与生产并行化（单命令/末级 stage 双拓扑）+ F9 collectOutput 可观测化，C10-001 单保护面覆盖五成员；③3.1 面两工单——DockerSandboxBackend 清理链不对称（F8 分叉）按 MA-17 族口径与 NoOp 兄弟收敛，C7-014 heartbeat wakeup 预算与 WAIT 碎片生命周期收敛并同批改写反向钉 R4。批末 roadmap 8.7 → done，7 项成员台账全部收敛，B1-04 计数迁移落账确认与 B1-03 MA-06 维持 P1 确认（G56 条化成员不单独立卷声明）落 cra-index。

## Current Baseline

### 批次与判级基线

- batch-proposal §二 R1-06 = 7 项成员（P1×6 + P2×1）；cra-index P1 台账 6 行（L252 `nop-ai-core-nop-ai-agent-MA-17`[B1-04 回升行]、L253 `nop-ai-toolkit-tools-shell-C1-001（MA-01 主）`、L254 `C6-001（MA-02 主）`、L255 `C6-002（MA-03 主）`、L256 `C6-003（MA-04）`、L257 `C4-003（MA-06 主）`，裁定批次均=R1-06、状态均=open）+ P2 裁决台账 G50 行（L424：成员 MA-07/MA-17/C7-014——MA-07 已随 R1-01 闭合，MA-17 与 C7-014 的批次=R1-06）；roadmap 8.7 行 Status=todo。
- **B1-04 计数迁移已回填（本批只做落账确认，不重做）**：3.1 P1×13→14、P2×29→28 的计数联动已由 plan 2493 Phase 5 于 2026-10-23 回填 cra-index（L58 B1-04 回升注记 + L131 批次回填注记「台账 179→180 行，末 3.1 行后新增 B1-04 回升行 MA-17」+ L252 MA-17 行在册）——本批 Phase 5 核对三处回填互洽并登记确认，不开新台账行。
- **B1-03 终裁口径（batch-proposal R1-06 行增补列）**：shell 族判级分歧（C4 P1 vs C6/C8 条件化 P2）终裁 = **MA-06 维持 P1**（取最严暂计转正）；其 C6/C8 条化成员（C6-004/C8-001/C8-003/C8-011 等）**随 G56 条款，不单独立卷**——族成员关系与单 diff 修复范围不变（3.2 merge-adjudication §五-2 裁定），成员面由本批 MA-06 工单单 diff 承载，其条化判级与「投产接线即回升」触发条款维持 G56 登记（3.2-C10-002 反向钉-1 ShellIOTest 改写义务同随 G56，不在本批）。
- **MA-17 判级基线（B1-04 终裁）**：沙箱族跨单元判级分歧（3.1 MA-17 原判 P2 vs 3.2 MA-02/03 P1）统一按「装配后生产形态」配置可达判 P1——两套实现同病（toolkit `HostBashSandbox`/`DockerBashSandbox` 与 agent `DockerSandboxBackend`/`NoOpSandboxBackend` 分属两套，零共享基类）；P2 裁决台账 G50 保留承接行。
- 沙箱装配形态（live 核实）：toolkit `ai-tools:bash` 默认 **fail-closed**（`ai-toolkit-defaults.beans.xml:19-35` 注释明示，未装配 `IBashSandbox` 拒绝一切调用；Host/Docker bean 已定义、opt-in 注入）；3.2 审计已裁定「沙箱为 fail-closed opt-in 装配，按配置可达生产路径判级、不适用休眠面降档」——MA-17 回升 P1 的判据即此口径，本批不得以「opt-in 未接线」为由降档。
- 前置：无（batch-proposal 依赖列 = 无前置，可并行）。plan 2495–2500（roadmap 8.1–8.6）均 completed。

### 前序批次边界核对（防重复立项——逐批 live 核验结论）

- **plan 2496（R1-01）零沙箱面**：其 nop-ai 分片（Phase 4，提交 `27c02f85a5`）为 3.1-MA-07 六站点周期任务绝缘（SessionLockRenewal/ScheduledRecoveryManager/TeamTaskSchedulerDaemon/DBMessageService×2/EmbeddingServiceImpl），全文无沙箱/shell 站点——MA-17 沙箱面无任何前批处理，本批为该面首次修复立项。G50 组三成员中 MA-07 已闭合、MA-17/C7-014 归本批，组内无重叠残留。
- **plan 2498（R1-03）同文件异面**：`ShellCommandExecutor` 池隔离（3.2-C4-004）已落地——缺省装配为专用有界池 `nop-ai-shell-pipeline`（32 线程/队列 1024/daemon，fail-loud 拒绝）；本批 C4-003 修的是**失败回滚面**（提交中止不回滚/弃管/自引用），与池隔离面正交，且不得回退 `TestShellPipelinePoolIsolation_C4_004_3_2` 钉住的隔离语义与池参数。
- **plan 2499（R1-04）同文件异面**：`ToolManagerImpl` 排队分支取消联动（3.2-C4-008，裁定⑥）已落地——本批 C1-001 修复**不得回退**取消联动语义（排队窗口取消短路 + 在途取消传播），改造须与联动代码共存。
- **plan 2285/2301 修复史边界**：HostBashSandbox 进度周期副本发布（WI4）、BashExecutionTracker 注册/进程树收割与 cancelAction 失败重抛（WI25 lineage）在位——C6-001 为「已修站点残余缺口面」（终态活对象发布/关流/stdin 悬挂面仍在），C6-008 为「运行期副本安全、终态活对象」的校准残余面。
- **下游批次边界**：R2-16（3.1 F4 终态清理族/spawn 池/取消契约等 10 项）与 R4-23（3.2-C6-007/C6-009/C8 束 10 项）软依赖本批但成员零重叠；3.2-MA-05（G56 dormant 条化束：C1-002 exportedEnv 竞态/C6-005 EOF offer/C8-002/C8-004/C8-005）不在本批——MA-06 单 diff 触达的族成员面除外（见 B1-03 口径）。

### 7 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `e0df517864` 回读值，执行前如再漂移以 live 为准并登记；漂移成因见勘误 2–4）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 3.2-C1-001（P1，MA-01 主，←C4-005；cra-index L253） | 3.2 / nop-ai（nop-ai-toolkit） | `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:128-161`（`pathQueues.compute` 块——审计载 :101-123，`executeNow.get()` live :141 在 compute :128 锁内，`result.complete(Exceptionally)` live :154/:156）；配套 `:246-287`（`submitBounded`——审计载 :232-246，semaphore release :287 受本缺陷放大） | 路径串行队列三子缺陷同一代码块：(a) 已完成 trigger 的 `thenCompose` 在 CHM bin 锁内同步执行工具分发（nop-cli `CliCallToolsCommand` 注入 `SyncThreadPoolExecutor.INSTANCE` 为锁内全执行的实证装配，bash 命令可达 wallSeconds=60s 全程持锁）；(b) 锁内重入（call-agent 嵌套派发同 pathKey）→ CHM Recursive update；(c) `executeNow.get()` 同步抛（executor null NPE / REE）时手工 `result` future 永不完成 → 调用方永久静默悬挂、`executeParallel` allOf 永不返回 | ①`executeNow.get()` 移出 compute——compute 内只做 `tail` 取换（哨兵链），fn 挂锁外执行（(a)(b) 同根消除）；②`result` 完成契约闭合——对 `executeNow.get()` 本身 try/catch → `completeExceptionally`，完成挂整链外层 whenComplete（(c) 消除）；③null executor 装配面随条补强（fail-loud 或缺省载体，warn-only 不构成终态，rule 16）；不回退 2499 取消联动；护栏 = 3.2-C10-004（sync-throw→result 终态故障注入） |
| 2 | 3.2-C6-001（P1，MA-02 主，←C4-001/C6-008；cra-index L254） | 3.2 / nop-ai（nop-ai-toolkit sandbox） | `sandbox/HostBashSandbox.java:92-95`（reader daemon 线程）、`:188-198`（awaitReader join 宽限+interrupt 对管道 read 无效）、`:178-180`（drainStream finally 发布活 StringBuilder——C6-008 终态面）；孪生 `sandbox/DockerBashSandbox.java:107-110,277-331`（同型）；`sandbox/BashExecutionTracker.java:82-98`（cancelExecution 杀进程树**不关流不终止读者**）、`:63-75`（progressSnapshot 消费活对象）；stdin 管道默认 PIPE 从不关闭（`cat` 类命令悬挂面） | `&` 孪生子进程持 stdout 管道 → 主进程退出无 EOF，reader 线程永久泄漏（interrupt 对阻塞原生 read 无效）+ 每次泄漏 1 线程 + 管道 fd；取消路径同型不关流；终态把活 StringBuilder 发布进 capturedRef → toString 与 append 竞态（撕裂/偶发 AIOOBE）——**Host killTree 已修（plan 2285/WI4）Docker 未同修的 F3 残留面由本工单补齐** | reader 退出补偿：drainStream 结束/中断路径显式 `in.close()` 强制 EOF（关流替代无效 interrupt）；命令启动 `redirectInput(NUL)` 杜绝 stdin 悬挂；终态发布改不可变快照（`new StringBuilder(captured)` 或 String 快照）+ `capturedToString`/`progressSnapshot` 消费面同步；Docker 侧同修（F3 同族清扫）；孪生场景 `ProcessHandle.descendants()` 收割策略（对照 killTree 既有 descendants API）或文档声明「`&` 后台子进程输出截断/线程滞留」边界；护栏 = 3.2-C10-005（孪生负载 reader 终止 + 快照稳定 + 线程唯一命名断言） |
| 3 | 3.2-C6-002（P1，MA-03 主，←C8-009；cra-index L255） | 3.2 / nop-ai（nop-ai-toolkit sandbox） | `sandbox/DockerBashSandbox.java:137-141`（超时/中断分支只 `killContainer`，全文件无 `destroyForcibly`——对照 Host `killTree` :220 有兜底）、`:253-264`（`killContainer` 两步清理双 `catch Exception ignored`——失败无痕迹无升级）、`:120-124`（finally `tracker.unregister` 撤销唯一取消句柄） | 超时路径不销毁本地 `docker run` CLI 进程 + `docker kill`/`rm -f` 失败被静默吞 + 容器无任何侧超时——daemon 瞬断即泄漏带配额的运行容器 + CLI 进程，且 unregister 后容器不可寻址（永久残留） | kill 失败可见化：`killContainer` 双 catch 改显式 WARN（容器名/步骤/异常入日志，去 ignored）+ 至少一次 `process.destroyForcibly()` 兜底杀 CLI（阻止本地进程泄漏）；容器侧兜底形态三选一裁定落账（`docker run --stop-timeout` / 启动注册后台 reaper 于 wall+margin 兜底 `rm -f` / 声明运维边界+owner doc 包络）；unregister 配对保留但失败不再静默；护栏 = 3.2-C10-006（kill 失败故障注入可见性断言） |
| 4 | 3.2-C6-003（P1，MA-04，←C4-002 心跳占用面；cra-index L256） | 3.2 / nop-ai（nop-ai-toolkit sandbox） | `sandbox/DockerBashSandbox.java:266-275`（`runShortCommand`——无超时 `in.read()` drain 循环位于 `waitFor(5s)` 之前、waitFor 返回值忽略、无 `destroyForcibly`） | `docker kill`/`rm -f` 子进程挂起（daemon 故障）时 drain read 永久阻塞——执行墙钟超时清理的线程自身被钉死（wallSeconds 契约失效、工作线程被吞、F5 挂死对健康检测不可见）；waitFor 超时后辅助进程滞留 | `runShortCommand` 重写为有界形态：drain 加累计时限（或 `waitFor(total)` 先行 → `destroyForcibly()` → 再 drain 剩余）；`waitFor(5s)` 返回 false 分支补 `p.destroyForcibly()`；清理线程不再被 daemon 挂死钉死（真实锚点：对照 HostBashSandbox `killTree` :208-227 与 `ProcessHandle.descendants()` 既有用法复用同形态）；护栏 = 3.2-C10-006 同套（挂死辅助进程注入，有界返回断言） |
| 5 | 3.2-C4-003（P1，MA-06 主，B1-03 终裁维持 P1；cra-index L257） | 3.2 / nop-ai（nop-ai-shell） | `shell/executor/ShellCommandExecutor.java:291-330`（`executePipeline` 阶段提交循环——审计载 :243-286，plan 2498 专用池改造致漂移；`stageFutures.add` 中止后无回滚；:327-331 末级收集挂 `thenApply`）、`:245-284`（`executeSimpleCommand`——:273 同线程生产入有界队列、:278-279 同线程收集）、`:675-681`（`collectOutput` catch 吞异常返回空串）；`shell/io/BlockingQueueShellOutput.java:15`（LinkedBlockingQueue 容量 1024）、`:30`（`queue.put` 阻塞写） | shell 管道死锁族案（族成员 C6-004[弃管]/C8-001[先生产后收集]/C8-003[下游死亡上游阻塞]/C8-011[`0<&1` 自引用]单 diff 同修）：第 k 次阶段提交抛 REE 时循环中止无回滚——已提交阶段 k-1 写满 1024 chunk 后永久阻塞 `queue.put`，消费者永不提交、无人 close，按链长级联吞噬执行器线程；C8-001 本体（live :245-284/:327-331）：`executeSimpleCommand` 同线程先生产后收集——产出 >1024 chunk 时 `queue.put` 在生产线程自身阻塞、close/collect 永不可达（自锁）；`executePipeline` 末级收集仅在末级 stage 完成后才启动——末级产出 >1024 chunk 同型自锁；F9：`collectOutput` 异常静默吞为空串（失败不可观测）；缺省装配现为本批前 2498 专用池（32/1024，REE 面缺省即可达——勘误 3） | 提交循环 try/catch REE：失败时对 `stageFutures` 已提交项逐个 `cancel(true)` + 对 `prevOutput` 链逐级 `close()`（close offer EOF 解除下游 put 阻塞）；中途失败/早退弃管 close 同口径（C6-004 面）；`0<&1` 自引用拒绝语义（C8-011 面）；C8-001 面：`executeSimpleCommand` 收集与生产并行化（或等效有界形态）+ `executePipeline` 末级收集并行化（收集与末级生产并发启动，不待其完成）；F9：`collectOutput` 去吞异常（失败显式可观测，与 kill/cleanup 可见化同口径）；cancelToken 检查点显式写去向（G56 条化 / R4-23 工单 / Deferred 三选一裁定登记，不静默丢弃）；溢出行为按平台 `QueueOverflowPolicy` 语义显式声明；**条化成员判级维持 G56 登记不单独立卷（B1-03 口径，cra-index 注记归 Phase 5）**；护栏 = 3.2-C10-001 单保护面覆盖 MA-06 五成员，显式钉两个自锁拓扑——「末级 stage 1025-chunk」+「单命令 >1024」（小池/小容量参数化 1025-chunk 挂死检测 + 阶段异常注入 + REE 饱和池注入 + 自引用拒绝） |
| 6 | 3.1-MA-17（B1-04 回升 P1；cra-index L252；成员 C1-007+C7-020，←CL-034；护栏 3.1-C10-011） | 3.1 / nop-ai（nop-ai-agent security） | `security/DockerSandboxBackend.java:268-276`（超时分支只 killContainer，无本地 CLI 进程 destroyForcibly——审计载 :262-276）、`:515-526`（`killContainer` 双 `catch(Exception ignored)`——kill 失败静默吞，与 3.2-C6-002 同型）、`:528-539`（`runShortCommand` waitFor(5s) 超时无 destroy）、`:580-590`（awaitReader interrupt 无效）；`security/NoOpSandboxBackend.java:103-143`（success 路径无 killTree——timeout 路径 :122 有）、`:187-202`（awaitReader 同型 interrupt-only）；对照既有护栏 `TestDockerSandboxBackend.java:412-417`（docker 门内仅 happy path） | Docker 沙箱超时清理链不对称（与 NoOp 兄弟 F8 分叉）：超时不 destroy 本地 `docker run` CLI 进程树 + reader 线程滞留（interrupt 无效）+ kill 失败不可见；success 路径孙进程持 stdout 管道时 reader 线程单调累积且输出静默置空（两实现同病，与 3.2 C6-001/C6-002/C6-003 两套实现同根） | 与 3.2 沙箱清理族同口径三件套：①超时路径 `destroyForcibly`（对齐 NoOp `killTree` 形态——双实现分叉收敛：提取共享工具方法或等效消歧， descendants+root）；②reader 退出补偿（关流强制 EOF 替代无效 interrupt + 缓冲上限 + grace 耗尽输出一次性安全快照）；③cleanup/kill 失败可见化（与 3.2-C6-002 同口径，去静默）；C7-020 面：success 路径孙进程收割 + 结果 `outputIncomplete`/`timedOut` 标记（空输出不再静默交付）；护栏 = 3.1-C10-011（docker 门内「sleep 30 + wallSeconds=1」超时 kill 用例断言容器不残留 + 孙进程持管道 reader 退出用例）——与 3.2 侧护栏共享形态 |
| 7 | 3.1-C7-014（P2，G50 行 L424；携 3.1 反向钉死 R4 改写义务） | 3.1 / nop-ai（nop-ai-agent engine） | `engine/ReActAgentExecutor.java:1270-1329`（`runHeartbeatToolPhase` while(true) slice 循环——live :1277）、`:1492-1562`（`runHeartbeatWakeupTurn`——ctx.addMessage 永久入史 live :1545-1547，审计载 :1550-1553）、`:1474-1481`（nextWaitSlice）；`engine/DefaultAgentEngineConfig.java:171`（heartBeatIntervalMs 默认 60_000 **默认开启**）、`engine/AgentToolDispatcher.java:427-431`（cap≤0 → deadline=Long.MAX_VALUE）；反向钉面 `engine/TestHeartbeatWaitPhase.java` 全组 hb-入史钉（R1 扩面）：`:260-262`（`hbCount >= 2` 入史钉 + `assertTrue(chat.calls.get() >= 4)` 正面钉 wakeup 行为）、`:656`、`:748`、`:783`——执行时复核全集 | heartbeat wakeup turn 无数量预算 + WAIT 裁决碎片永久写入会话史 + 等待期无 compaction——长工具调用（4h cap 或 cap≤0 禁超时）期间 LLM 成本随等待时长（超）线性放大（~240 次全上下文 wakeup 调用、ctx 堆积 ~480+ 条碎片），cap 禁用形态下唯一终止是 ctx 撑到 force-stop（上下文洪水处决执行） | ①每等待期 wakeup 数量预算（`maxHeartbeatTurns` 或等效配置，超限降级纯机械等待——缺陷闭合必做面）；②WAIT 裁决 heartbeat 证据消息改请求副本携带、仅 commit 终局结果（WAIT 碎片 ctx 生命周期收敛——缺陷闭合必做面）；③等待循环每 K slice 顺带评估 `shouldTriggerCompaction`（增强面，实现期裁定，不纳入则落 Deferred）；**反向钉 R4 同批改写义务**：核对预算默认值与副本化改写同 `TestHeartbeatWaitPhase` 全组 hb-入史钉（`:260-262`、`:656`、`:748`、`:783`，执行时复核全集）的相容性并同步改写（修复若使预算 <4 或 hb 不再入终史，该组断言打红——改写后断言语义改为「请求副本携带 hb + ctx 终史 hb=0」的正面保护，不删除保护面）；护栏 = 唤醒预算超限降级行为断言 + WAIT 碎片不入终史断言 |

### 专项义务（batch-proposal R1-06 行 exit criteria 增补的批级义务——各 Phase 落地、Closure Gates 逐条对账）

1. **超时 destroyForcibly + reader 退出补偿 + kill 失败可见化**（MA-17 沙箱清理族口径，3.2-C6-002/C6-003 与 3.1-MA-17 同口径落地，两套实现不得各走一套）。
2. **Docker 侧 F3 残留随 3.2-MA-02 补齐**（Host killTree 已修未同修面——F3 同族清扫：toolkit DockerBashSandbox 与 agent DockerSandboxBackend 的 destroyForcibly 缺失面本批闭合；Host/NoOp 已修面零回退）。
3. **3.1-C7-014 携 3.1 反向钉死改写义务同批**（R4：`TestHeartbeatWaitPhase` 中全部 hb-入史钉——`:260-262`、`:656`、`:748`、`:783`，执行时复核全集——相容性核对 + 同批改写；改写后语义目标：WAIT 场景断言改为「请求副本携带 hb + ctx 终史 hb=0」的正面保护，不删保护面；本批**无其他反向钉死义务**——3.2 反向钉-1 ShellIOTest 随 G56 条款不在本批）。
4. **B1-04 计数迁移落账确认**（已回填——Phase 5 核对 cra-index L58/L131/L252 三处互洽并登记确认）；**B1-03 族工单 MA-06 维持 P1 确认**（G56 条化成员不单独立卷声明落 cra-index）。
5. **护栏测试共享形态**（3.1-C10-011 docker 门内形态 + 3.2-C10-001 单保护面覆盖 MA-06 五成员；C10-004/005/006 故障注入 harness 共享同一套模式——3.2 审计 C10 结构性发现「故障注入维度整体缺失」的修复义务随本批护栏落地）。

### Baseline 勘误（输入不一致登记）

1. **任务简报「plan 2496 Phase 1b 已部分处理沙箱面」不成立**：plan 2496 全文无 Phase 1b（该编号见 plan 2432/2500）、无任何沙箱/Docker/MA-17 站点——其 nop-ai 面（Phase 4 分片 B，提交 `27c02f85a5`）为 3.1-MA-07 六站点周期绝缘。MA-17 沙箱面无前批处理，本批为首次修复立项，无重复立项风险；简报所称边界核对已按实执行并登记于「前序批次边界核对」节。
2. **C1-001 行号漂移（成因明确）**：审计载 `ToolManagerImpl.java:101-123/:232-246` → live `:128-161/:246-287`——plan 2499 Phase 3 子分片 C（提交 `276618d9da`）在排队分支插入取消联动代码致 +27 行漂移；2499 只加取消联动，C1-001 三子缺陷结构（锁内执行/重入/悬挂 future）原样在位（live :141 `executeNow.get()` 在 compute :128 内、:154/:156 完成点均核实）。
3. **C4-003 行号漂移 + 触发前提部分反转（2498 落地后果）**：`executePipeline` 审计载 `:243-286` → live `:291-330`（plan 2498 提交 `15f65b2c66` 类头/字段改造致漂移）；审计触发前提「缺省 globalWorker（queue 10000）下 REE 罕见、注入小池常态可达」在 2498 落地后**部分反转**——REE 面由『池深度Rare』转为『32/1024 有界 fail-loud 可达（适配器接线前提不变）』，判级 P1 不变、修复紧迫性上调；`BlockingQueueShellOutput` :15/:30 零漂移。
4. **3.1 站点行号微漂移**：`DockerSandboxBackend` 超时分支审计载 `:262-276` → live 缺陷行 `:268-276`（:262-265 为同块 waitFor/中断段，语义归属不变）；`runHeartbeatWakeupTurn` 的 addMessage 审计载 `:1550-1553` → live `:1545-1547`（执行时回读精确定位）。其余站点（HostBashSandbox :92-95/:178-180/:188-198、DockerBashSandbox :107-110/:120-124/:137-141/:253-264/:266-275、NoOpSandboxBackend :103-143/:187-202、DefaultAgentEngineConfig :171、AgentToolDispatcher :427-431、TestHeartbeatWaitPhase :262、TestDockerSandboxBackend :412-417、BashExecutionTracker :63-75/:82-98）live 零漂移。
5. **BashExecutionTracker.cancelExecution live 形态精度注记**：审计 C6-001 枚举④称「只 kill 进程不关流不终止读者」——live `:82-98` 已含 descendants+root `destroyForcibly`（WI25 lineage）且 cancelAction 失败 RuntimeException 重抛（取消路径 kill 失败可见化部分在位）；字面表述成立（确不关流、不显式终止读者），残余面收窄为「关流/读者退出补偿 + stdin 悬挂 + 终态快照」，C6-001 缺陷本体判定维持不扩不缩。

## Goals

- 7 项成员全部修复并各带 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单：`_C1_001`/`_C6_001`/`_C6_002`/`_C6_003`/`_C4_003`/`_MA_17`/`_C7_014`）；可复现项先红后绿或变异/回退论证（E2）——线程/future 终态类断言以修复前缺陷基线跑红为标准动作。
- 沙箱清理族三口径统一落地：超时 `destroyForcibly`（toolkit DockerBashSandbox 与 agent DockerSandboxBackend 双侧补齐，双实现分叉收敛）+ reader 退出补偿（关流强制 EOF 替代无效 interrupt）+ kill/cleanup 失败可见化（去静默 ignored）；Docker 侧 F3 残留（Host/NoOp 已修未同修面）随 3.2-MA-02 补齐，已修面零回退。
- ToolManagerImpl 排队链三子缺陷闭合：(a) 锁内执行、(b) 重入、(c) 悬挂 future 全部消除；2499 取消联动语义零回退。
- shell 管道死锁族单 diff 修复：REE 提交中止回滚 + 中途失败/早退弃管 close + `0<&1` 拒绝 + C8-001 面收集/生产并行化（「末级 stage 1025-chunk」+「单命令 >1024」双拓扑）+ F9 collectOutput 去吞异常；C10-001 单保护面护栏覆盖 MA-06 五成员；2498 池隔离基线零回退。
- C7-014 收敛：wakeup 数量预算 + WAIT 碎片 ctx 生命周期收敛（缺陷闭合必做两面）；反向钉 R4（`TestHeartbeatWaitPhase` 全组 hb-入史钉 :260-262/:656/:748/:783）同批改写在案——改写后语义=「请求副本携带 hb + ctx 终史 hb=0」正面保护，不删保护面。
- 批级确认落账：B1-04 计数迁移落账确认（三处回填互洽核对登记）；B1-03 MA-06 维持 P1 + G56 条化成员不单独立卷声明落 cra-index。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-ai.md`（roadmap 8.7 Owner Doc 列单份——沙箱清理契约/排队链契约/pipeline 失败回滚契约/heartbeat 预算契约四处受影响面核对与更新）。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证（`./mvnw test -pl nop-ai -am`）/ 零触碰声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不承接 3.2-MA-05（G56 dormant 条化束）**：C1-002（exportedEnv 会话状态竞态）、C6-005（EOF offer 队满丢失）、C8-002/C8-004/C8-005 等 dormant 面与 3.2 反向钉-1（ShellIOTest:294-300）改写义务随 G56 触发条款（激活时同批改写，R-G 条款登记册承载）；MA-06 单 diff 触达的族成员面（C6-004/C8-001/C8-003/C8-011）随本体承载、不另立工单（B1-03 口径）。
- **不承接 R2-16 / R4-23 成员**：3.1 F4 终态清理族/C4-010 spawn 池/C3-011 等（R2-16）、3.2-C6-007/C6-009/C8-007/C8-008/C8-010/C8-012 等（R4-23）——两批软依赖本批但成员零重叠；本批 done 后软依赖前提（「同仓沙箱面先行」）随之满足。
- **不承接 3.1-MA-17 以外的 agent 沙箱外溢面**：NoOpSandboxBackend timeout 路径（killTree 已覆盖）、`killTree` 内部 waitFor(2s)（意义不同，审计裁定通过）零改动；3.1 C10-008 Error 注入护栏维持 R2-16 归属（plan 2496 既有裁定）。
- **不做平台设施回改**：GlobalExecutors/池工厂/绝缘层（R1-02/R1-01 已闭面）零改动；站点修复过程中若暴露设施缺陷，按批次纪律登记（cra-index/新 finding）不越界承接。
- **不回退既有覆盖**：2496 R1-01 绝缘语义、2498 shell 专用池隔离与 AgentDispatchExecutor 池、2499 工具取消联动与 bindCancel 契约、2285 进度副本发布、2301 进程树收割——全部为共享载体/同文件改动的回归门禁。
- 不触碰 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物（零触碰声明见 plan 头部）。
- 不做 Deferred 节所列项（docker 门控护栏 CI 通道、容器侧 reaper 增强、等待期周期 compaction 评估等）。

## Scope

### In Scope

- `nop-ai/nop-ai-toolkit`（ToolManagerImpl 排队链 + sandbox 四类 HostBashSandbox/DockerBashSandbox/BashExecutionTracker 清理族——成员 1–4）。
- `nop-ai/nop-ai-shell`（ShellCommandExecutor.executePipeline + BlockingQueueShellOutput 失败回滚/弃管/拒绝语义——成员 5）。
- `nop-ai/nop-ai-agent`（security/DockerSandboxBackend + NoOpSandboxBackend 清理链收敛 + engine/ReActAgentExecutor heartbeat 预算 + TestHeartbeatWaitPhase 反向钉改写——成员 6–7）。
- 护栏测试：3.2-C10-001/004/005/006 与 3.1-C10-011 对应回归面（共享故障注入形态）；watch-only 注释修正（TestDockerSandboxBackend:412-417）。
- Owner doc：`docs-for-ai/03-modules/nop-ai.md`；cra-index 台账回填（7 成员行 + B1-04/B1-03 确认注记 + G50 行 R1-06 成分收敛注记）；roadmap 8.7 → done。

### Out Of Scope

- Deferred 节所列项；R2-16/R4-23/R-G 等后续批次工单；G56 dormant 条化束（MA-05）与 3.2 反向钉-1；平台设施面（见 Non-Goals）。

## Execution Plan

> 三个模块组分片修复（Phase 1–3：nop-ai-toolkit 面四工单 / nop-ai-shell 面一工单 / nop-ai-agent 面两工单）→ 全量验证 + owner doc + 批级确认（Phase 5）→ closure audit + 收口（Phase 6）。分片间无硬依赖、可按序执行；每工单独立提交（提交纪律：commit message 声明工单 ID 与 plan 号）。

### Phase 1 - 分片 A：3.2-C1-001 ToolManagerImpl 排队链三子缺陷（nop-ai-toolkit）

Status: completed
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java`（:128-161 compute 块、:246-287 submitBounded）、`nop-ai/nop-ai-toolkit/src/test/`

- Item Types: `Fix`（3.2-C1-001 已确认 live defect，不得写成 Feature）、`Proof`（回归测试）

- [x] `executeNow.get()` 移出 compute 锁：compute 内只做 `tail` 取换（哨兵链建链），fn 挂锁外执行——(a) 锁内同步执行（nop-cli SyncThreadPoolExecutor 装配全 bin 串行面）与 (b) 锁内重入（Recursive update）同根消除
- [x] `result` future 完成契约闭合：对 `executeNow.get()` 本身 try/catch → `result.completeExceptionally`，完成挂整链外层 whenComplete——(c) 同步抛（null executor NPE / REE）时调用方得异常终态非永久悬挂，`submitBounded` semaphore release 随终态恢复
- [x] null executor 装配面补强：`ToolExecuteContext.Builder.executor` 无默认值 / `ChatBiToolExecuteContext.getExecutor()` 返回 null 面 fail-loud 或缺省载体（warn-only 不构成终态，rule 16）；**不回退 2499 取消联动语义**（排队窗口取消短路 + 在途取消传播断言共存）
- [x] 回归测试（rule 25，测试名带 `_C1_001`）：①同步抛注入 → result 异常完成非悬挂（3.2-C10-004 故障注入形态：null executor / 饱和池 REE 双形态）；②锁内执行消除断言（同步执行器装配下 compute 调用线程不执行分发体）；③排队语义零回归钉（同路径串行/异路径并行/取消联动既有行为）；④nop-cli SyncThreadPoolExecutor 装配链核对——live 回读核对登记（执行时回读装配链、结论登记于执行注记/日志，非新增自动化测试；nop-cli 跨模块自动化断言不纳入本批）
- [x] **接线验证**（rule 22 端到端——真实入口→终态输出；rule 23）：真实 callTool 排队链（非 concurrency-safe 工具 + pathKey 提取命中）上断言新完成契约与锁外执行生效——从命令行真实入口到 result 终态输出完整走通
- [x] **无静默跳过**（rule 24）：executeNow 同步抛不再静默悬挂——异常路径显式 completeExceptionally 可观测
- [x] **提交纪律**：nop-ai-toolkit 一提交，commit message 带 `3.2-C1-001` 与 plan 号
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 3.2-C1-001 landed：三子缺陷（锁内执行/重入/悬挂 future）全部闭合，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：悬挂 future 断言先红后绿或变异/回退论证留痕（修复前基线 result 永不完成断言超时红；快照 `_tmp/phase1-c1-001-*.log`）
- [x] 接线验证证据登记（哪条断言证明运行时生效）
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit -am` BUILD SUCCESS（分片通道；聚合批行通道归 Phase 5 E3）
- [x] Owner doc 裁定：`nop-ai.md` 工具排队链契约核对——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 5 统一但须在本 Phase 显式登记去向
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 分片 B：3.2-C6-001/C6-002/C6-003 沙箱清理族三工单（nop-ai-toolkit sandbox）

Status: planned
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/HostBashSandbox.java`（:92-95/:178-180/:188-198）、`DockerBashSandbox.java`（:107-110/:120-124/:137-141/:253-264/:266-275）、`BashExecutionTracker.java`（:63-75/:82-98）、`nop-ai/nop-ai-toolkit/src/test/`

- Item Types: `Fix`（三工单均为已确认 live defect P1）、`Decision`（容器侧兜底形态三选一裁定）、`Proof`（回归测试）

> 续作纪律：本分片三工单允许分会话续作——每工单独立提交（commit message 带对应工单 ID 与 plan 号），每工单完成即时勾选对应 checklist 项与 Exit Criteria，不跨工单积压未勾选项；分会话交接以本 plan 的勾选状态为进度事实源，不依赖会话记忆。

- [ ] 3.2-C6-001（MA-02 主，含 C6-008/C4-001 成员面同 diff）：reader 退出补偿——drainStream 结束/中断路径显式 `in.close()` 强制 EOF（关流替代无效 interrupt）；`redirectInput(NUL)` 杜绝 stdin 悬挂；终态发布改不可变快照 + `capturedToString`/`progressSnapshot` 消费面同步；Docker 侧孪生实现同修（**F3 残留随 MA-02 补齐义务**）；孪生场景 `ProcessHandle.descendants()` 收割或文档声明边界（二选一以实现期裁定落账）
- [ ] 3.2-C6-002（MA-03 主，含 C8-009）：`killContainer` 双 `catch ignored` 改显式 WARN（容器名/步骤/异常可见）+ CLI 进程 `destroyForcibly()` 兜底；容器侧兜底形态三选一裁定落账（`--stop-timeout` / 后台 reaper / 声明运维边界+owner doc 包络）；unregister 配对保留、失败不再静默
- [ ] 3.2-C6-003（MA-04，含 C4-002 心跳占用面）：`runShortCommand` 重写有界形态（drain 累计时限或 `waitFor(total)`→`destroyForcibly()`→再 drain）；`waitFor` false 分支补 `destroyForcibly()`；清理线程不再被 daemon 挂死钉死
- [ ] 回归测试（rule 25，测试名带 `_C6_001`/`_C6_002`/`_C6_003`）：①孪生进程负载（`sh -c "sleep 30 & echo started"` 形态）reader 线程预算内终止断言（线程名计数，Host 侧无 Docker 依赖承载 CI 可见面——3.2-C10-005 形态）；②终态快照并发稳定性断言（execute 返回后并发 capturedToString/progressSnapshot N 次无异常内容前缀稳定）；③kill 失败故障注入可见性断言（3.2-C10-006 形态：注入失败 CLI → WARN 可观测 + destroyForcibly 兜底发生）；④runShortCommand 挂死注入有界返回断言；⑤既有沙箱测试（TestBashExecutionProgress 等 plan 2285 已修面）零回归
- [ ] **护栏共享形态**：docker 门内用例与 3.1-C10-011 共用 `isDockerAvailable` 门控形态；Host 侧无 Docker 依赖用例承载主要回归面
- [ ] **接线验证**（rule 23）：真实 execute/超时路径上断言关流补偿与 destroyForcibly 兜底运行时生效（线程终止/进程退出可观测）
- [ ] **无静默跳过**（rule 24）：kill/cleanup 失败路径显式日志与异常传播，不吞异常
- [ ] **提交纪律**：三工单三提交（或按实际 diff 面单提交并声明关系），commit message 带对应工单 ID 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 3.2-C6-001/C6-002/C6-003 三工单全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 沙箱清理族三口径在位：超时 destroyForcibly + reader 退出补偿 + kill 失败可见化；Docker 侧 F3 残留补齐（toolkit 侧）
- [ ] 必红证据（E2）：reader 泄漏断言与 kill 失败静默断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase2-c6-*.log`）
- [ ] 接线验证证据登记
- [ ] `./mvnw test -pl nop-ai/nop-ai-toolkit -am` BUILD SUCCESS（含 Phase 1 面回归）
- [ ] Owner doc 裁定：`nop-ai.md` bash 工具 sandbox 节（清理契约/超时语义）核对——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 5 但须在本 Phase 显式登记去向
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 分片 C：3.2-C4-003（MA-06）shell pipeline 死锁族单 diff（nop-ai-shell）

Status: planned
Targets: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java`（:291-330 executePipeline、:245-284 executeSimpleCommand、:675-681 collectOutput）、`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java`（:15/:30）、`nop-ai/nop-ai-shell/src/test/`

- Item Types: `Fix`（3.2-C4-003 族案已确认 live defect P1——B1-03 终裁维持）、`Decision`（cancelToken 检查点去向三选一裁定 + 溢出行为声明口径）、`Proof`（回归测试）

- [ ] 提交循环 REE 回滚：`supplyAsync` 提交 try/catch REE——失败时对 `stageFutures` 已提交项逐个 `cancel(true)` + 对 `prevOutput` 链逐级 `close()`（close offer EOF 解除下游 `queue.put` 阻塞）；已提交阶段无线程滞留
- [ ] C8-001 面修复（同 diff）：`executeSimpleCommand` 收集与生产并行化（或等效有界形态——消除同线程写满 1024 chunk 自锁，live :245-284）+ `executePipeline` 末级收集并行化（收集与末级 stage 生产并发启动、不待其完成，live :327-331）
- [ ] F9 面（同 diff）：`collectOutput`（live :675-681）去吞异常——收集失败显式可观测（日志/异常传播，与 kill/cleanup 可见化同口径），不静默返回空串
- [ ] cancelToken 检查点去向裁定：G56 条化 / R4-23 工单 / Deferred 三选一，显式登记于执行注记（不静默丢弃——本执行器链现仅透传 token、无自身检查点，去向须落账）
- [ ] 中途失败/早退弃管 close（C6-004 面）+ `0<&1` 自引用拒绝语义（C8-011 面）同 diff 落地；溢出行为按平台 `QueueOverflowPolicy` 语义显式声明
- [ ] 族成员面单 diff 承载核对：C6-004/C8-001/C8-003/C8-011 面随本 diff 收敛（族成员关系与单 diff 修复范围不变裁定）；条化判级维持 G56 登记、不单独立卷（cra-index 注记归 Phase 5）
- [ ] 回归测试（rule 25，测试名带 `_C4_003`，3.2-C10-001 单保护面覆盖 MA-06 五成员）：①1025-chunk 参数化挂死检测（小容量/小池注入 + `@Timeout` 断言完成；显式钉两个自锁拓扑——「末级 stage 1025-chunk」+「单命令 >1024」）；②阶段 lambda 异常注入断言上游解锁、无线程滞留（线程数断言）；③REE 饱和池提交中止回滚断言（已提交阶段取消 + 队列解除阻塞）；④`0<&1` 拒绝语义；⑤collectOutput 失败可观测断言（不再静默空串）；⑥既有 `ShellConcurrencyEdgeCaseTest` 与 `TestShellPipelinePoolIsolation_C4_004_3_2`（2498 池隔离钉）零回归
- [ ] **接线验证**（rule 22 端到端——真实入口→终态输出；rule 23）：真实 executePipeline 多阶段链（缺省专用池装配）上断言回滚路径运行时生效；真实单命令入口到 ExecutionResult 终态输出完整走通
- [ ] **无静默跳过**（rule 24）：提交中止路径显式异常传播 + 回滚动作可观测；collectOutput 失败不静默吞
- [ ] **提交纪律**：nop-ai-shell 一提交，commit message 带 `3.2-C4-003` 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 3.2-C4-003 landed：REE 回滚 + 弃管 close + 自引用拒绝 + C8-001 面收集/生产并行化（单命令与末级 stage 双拓扑）+ F9 collectOutput 可观测化在位（族单 diff），带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 必红证据（E2）：REE 级联阻塞断言与 C8-001 自锁断言（「末级 stage 1025-chunk」+「单命令 >1024」双拓扑）先红后绿或变异/回退论证留痕（修复前基线阶段 k-1 永久阻塞红；快照 `_tmp/phase3-c4-003-*.log`）
- [ ] 接线验证证据登记
- [ ] `./mvnw test -pl nop-ai/nop-ai-shell -am` BUILD SUCCESS；2498 池隔离基线零回退（`TestShellPipelinePoolIsolation_C4_004_3_2` 全绿）
- [ ] Owner doc 裁定：`nop-ai.md` shell pipeline 节（失败回滚契约）核对——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 5 但须在本 Phase 显式登记去向
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 分片 D：3.1-MA-17 沙箱清理族 + 3.1-C7-014 heartbeat 预算（nop-ai-agent）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java`（:254-258/:268-276/:528-539/:580-590）、`NoOpSandboxBackend.java`（:103-143/:187-202）、`engine/ReActAgentExecutor.java`（:1270-1329/:1474-1481/:1492-1562）、`engine/DefaultAgentEngineConfig.java`（:171 邻域配置键）、`nop-ai/nop-ai-agent/src/test/`（含 `TestHeartbeatWaitPhase.java:262` 反向钉改写、`TestDockerSandboxBackend.java` 护栏扩充）

- Item Types: `Fix`（MA-17[B1-04 回升 P1]与 C7-014 已确认 live defect）、`Decision`（C7-014 compaction 增强面裁定）、`Proof`（回归测试）

- [ ] 3.1-MA-17（C1-007+C7-020，←CL-034）：①超时路径本地 CLI 进程树 `destroyForcibly`（对齐 NoOp `killTree` 形态；双实现分叉收敛——共享工具方法或等效消歧，与 3.2 侧三口径同口径不各走一套）；②reader 退出补偿（关流强制 EOF 替代无效 interrupt + 缓冲上限 + grace 耗尽输出一次性安全快照）；③cleanup/kill 失败可见化（去静默）；④success 路径孙进程收割 + 结果 `outputIncomplete`/`timedOut` 标记（空输出不再静默交付）
- [ ] 3.1-C7-014：①每等待期 wakeup 数量预算（`maxHeartbeatTurns` 或等效配置键，超限降级纯机械等待）；②WAIT 裁决 heartbeat 证据消息请求副本携带、仅 commit 终局结果（WAIT 碎片不入终史）；③等待循环每 K slice `shouldTriggerCompaction` 评估——增强面按实现期裁定，不纳入则移入 Deferred 并登记理由
- [ ] **反向钉 R4 同批改写（全组）**：`TestHeartbeatWaitPhase` 中全部 hb-入史钉——`:260-262`（`hbCount >= 2` 入史钉 + `chat.calls.get() >= 4` 正面钉 wakeup 行为）、`:656`（仅成功 wakeup 的 hb 在史 count==1）、`:748`（engine 路径 hb 流通钉）、`:783`（blocked reply 不入史但 hb 入史钉）——执行时复核全集后逐处核对与预算/副本化改写的相容性并同步改写；改写后语义目标：WAIT 场景断言改为「请求副本携带 hb + ctx 终史 hb=0」的正面保护，不删保护面；改写记录留执行注记
- [ ] 护栏（3.1-C10-011）：docker 门内「sleep 30 + wallSeconds=1」超时 kill 用例（断言 `docker ps` 无同名残留容器）；NoOp/共享层孙进程持管道 reader 退出用例；watch-only 注释修正（`TestDockerSandboxBackend:412-417`「timeout path can kill」错觉注释随修复同批更正）
- [ ] 回归测试（rule 25，测试名带 `_MA_17`/`_C7_014`）：①Docker 超时路径 CLI 进程 destroyForcibly 断言（门内集成 + 进程存活计数）；②reader 预算内退出断言（关流补偿生效）；③wakeup 预算超限降级断言（预算内行为 + 超限机械等待 + LLM 调用计数封顶）；④WAIT 碎片不入终史断言；⑤既有 TestHeartbeatWaitPhase/TestDockerSandboxBackend/TestNoOpSandboxBackend 零回归（改写面除外——改写面 = 反向钉 R4 全组 hb-入史钉 :260-262/:656/:748/:783，按「请求副本携带 hb + ctx 终史 hb=0」正面保护口径改写）
- [ ] **接线验证**（rule 23）：真实 DockerSandboxBackend 超时链 / 真实 runHeartbeatToolPhase 等待循环上断言清理与预算运行时生效
- [ ] **无静默跳过**（rule 24）：kill/cleanup 失败显式可观测；wakeup 超限降级路径显式（日志/标记），不静默
- [ ] **提交纪律**：两工单两提交，commit message 带对应工单 ID 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 3.1-MA-17 landed：超时 destroyForcibly + reader 退出补偿 + kill 失败可见化 + success 路径孙进程收割/outputIncomplete 标记在位，双实现分叉收敛，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 3.1-C7-014 landed：wakeup 预算 + WAIT 碎片生命周期收敛在位（compaction 增强面已裁定：纳入/Deferred 二选一留痕）；反向钉 R4 同批改写在案（全组 hb-入史钉 :260-262/:656/:748/:783 相容性核对记录；改写后语义=「请求副本携带 hb + ctx 终史 hb=0」正面保护）
- [ ] 必红证据（E2）：MA-17 reader 滞留断言与 C7-014 预算缺失断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase4-ma17-c7-014-*.log`）
- [ ] 接线验证证据登记
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` BUILD SUCCESS
- [ ] Owner doc 裁定：`nop-ai.md` 沙箱清理契约与 heartbeat 预算契约核对——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 5 但须在本 Phase 显式登记去向
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 全量验证 + owner doc 更新 + B1-04/B1-03 批级确认

Status: planned
Targets: 受影响聚合模块全量测试；`docs-for-ai/03-modules/nop-ai.md`（roadmap 8.7 Owner Doc 列）；`ai-dev/audits/cra-index.md`（B1-04 确认 + B1-03 声明 + G50 行注记准备）

- Item Types: `Proof`、`Decision`（B1-03 声明注记）

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2496–2500 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-ai -am` BUILD SUCCESS 留证（模块/测试数/退出码/快照路径）
- [ ] 零触碰确认（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物路径（`git diff --name-only` 核对登记；`_vfs` 下 beans 装配文件若触达按头部豁免口径登记）
- [ ] Owner doc 更新（E5）：`docs-for-ai/03-modules/nop-ai.md` 四处受影响面逐项核对与更新（工具排队链契约 / bash 工具 sandbox 清理契约与超时语义 / shell pipeline 失败回滚契约 / heartbeat wakeup 预算契约），或逐项显式登记 `No owner-doc update required` + 理由；缺口声明（实际 diff 触达模块无 03-modules 条目者——本批三子模块均在 nop-ai.md 覆盖内，核对登记）
- [ ] **B1-04 计数迁移落账确认**：核对 cra-index L58（B1-04 回升注记）/ L131（批次回填注记 179→180 行）/ L252（MA-17 回升行）三处互洽，确认结论登记（不重做回填）
- [ ] **B1-03 MA-06 维持 P1 确认 + G56 不单独立卷声明**：cra-index `nop-ai-toolkit-tools-shell-C4-003（MA-06 主）` 行（L257）注记「B1-03 终裁维持 P1；族成员 C6-004/C8-001/C8-003/C8-011 面随本批单 diff 承载，条化判级与回升条款维持 G56 登记、不单独立卷」；交叉注记：在 G56 行或触发条款登记册对应条目加指向 L257 的注记，或显式声明 L257 注记即条款锚点（二选一登记）；G50 行（L424）R1-06 成分（MA-17/C7-014）收敛注记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0（或批前既有发现逐条裁定登记——沿 2496/2498 先例：批前基线见 plan 2498 Phase 7 nop-ai 4 条裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] Owner doc `nop-ai.md` 四处受影响面结论逐项登记（含 `No owner-doc update required` 声明者）；check-doc-links --strict 退出码 0
- [ ] B1-04 落账确认 + B1-03 MA-06 维持 P1/G56 不单独立卷声明 + G50 行注记已登记（载体可核验：cra-index 对应行）
- [ ] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.7 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常）；重点抽查「沙箱清理三口径运行时真实生效（reader 终止/进程销毁/失败可观测）」「排队链 result 终态」「pipeline 回滚解锁」「wakeup 预算真实约束」与「既有覆盖零回退（2496/2498/2499/2285/2301 面回归门禁）」
- [ ] cra-index 台账回填：7 成员行状态收敛（open → 已修/本批；P1 六行 L252–L257 + G50 行 L424 的 R1-06 成分）+ 专项义务注记（三口径/Docker F3 补齐/反向钉 R4 改写/B1-04 确认/B1-03 声明）
- [ ] roadmap 8.7 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2501-cra-phase8.7-r1-06-sandbox-shell.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 7 成员行状态全部收敛 + 专项义务注记可核验、roadmap 8.7 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 7 项成员全部修复收敛：3.2-C1-001（排队链三子缺陷）、3.2-C6-001（reader 泄漏+终态快照+stdin）、3.2-C6-002（kill 可见化+destroyForcibly 兜底）、3.2-C6-003（runShortCommand 有界化）、3.2-C4-003（REE 回滚+弃管 close+自引用拒绝+C8-001 收集/生产并行化+F9 collectOutput 可观测化）、3.1-MA-17（超时 destroy+reader 补偿+失败可见化+success 路径收割）、3.1-C7-014（wakeup 预算+WAIT 碎片生命周期）
- [ ] 沙箱清理族三口径统一（超时 destroyForcibly / reader 退出补偿 / kill 失败可见化）且双实现不各走一套；Docker 侧 F3 残留补齐（toolkit DockerBashSandbox + agent DockerSandboxBackend），Host/NoOp 已修面零回退
- [ ] 3.1-C7-014 携反向钉 R4 改写义务同批完成（TestHeartbeatWaitPhase 全组 hb-入史钉 :260-262/:656/:748/:783 相容性核对 + 改写记录在案——改写后语义=「请求副本携带 hb + ctx 终史 hb=0」正面保护）；本批无其他反向钉死义务（3.2 反向钉-1 随 G56，不在本批）
- [ ] B1-04 计数迁移落账确认登记（cra-index 三处回填互洽核对）；B1-03 MA-06 维持 P1 + G56 条化成员不单独立卷声明落 cra-index
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；可复现项有先红后绿或变异/回退论证（E2）；护栏共享形态落地（3.2-C10-001 单保护面覆盖 MA-06 五成员 + C10-004/005/006 故障注入 + 3.1-C10-011 docker 门内形态）
- [ ] 既有覆盖零回退：2496 绝缘、2498 池隔离（shell 专用池/AgentDispatchExecutor）、2499 取消联动、2285 进度副本、2301 进程树收割对应回归面全绿
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-ai -am` BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：`docs-for-ai/03-modules/nop-ai.md` 四处受影响面逐项核对与更新（或 `No owner-doc update required` 声明）；缺口声明登记（E5）
- [ ] 零触碰确认（E4）：产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-auth/`_` 前缀生成物
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/增强面，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）沙箱清理三口径与排队链/回滚/预算改造运行时真实生效（线程终止/进程销毁/future 终态/调用计数可观测，不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2501-cra-phase8.7-r1-06-sandbox-shell.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 7 成员行收敛 + roadmap 8.7 → done（E6）

## Deferred But Adjudicated

### Docker 门控护栏的 CI 盲区（3.1-C10-011 / 3.2-C10-006 docker 门内用例在无 Docker CI 环境不执行）

- Classification: `watch-only residual`
- Why Not Blocking Closure: docker 门控为审计裁定的护栏共享形态本体（C10-011 口径「会红（门内）但 CI 永不执行」结构性已知）；Host 侧无 Docker 依赖的孪生进程/线程终止用例（C10-005 形态）承载主要回归面，缺陷主面不因 CI 盲区失护；构建通道决定（Gradle/docker 门控）归 R-G 治理批（CL-301 同族）。
- Successor Required: `no`（R-G 治理轨登记；后续按需求立项）

### 容器侧兜底增强（后台 reaper 于 wall+margin 兜底 `docker rm -f`——若 Phase 2 工单裁定「声明运维边界+owner doc 包络」支路时成立）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 「不可寻址永久残留」缺陷本体由 kill 失败可见化 + CLI destroyForcibly 兜底 + unregister 失败不静默闭合；reaper 属容器侧增强，三选一裁定落账后无静默残留。
- Successor Required: `no`（若实际容器泄漏观测显示需要，经 backlog 立项）

### C7-014 等待期周期 compaction 评估（审计建议③——每 K slice 评估 shouldTriggerCompaction）

- Classification: `optimization candidate`
- Why Not Blocking Closure: C7-014 缺陷闭合必做面 = wakeup 数量预算 + WAIT 碎片 ctx 生命周期（两面已入 Phase 4 工单硬性范围）；周期 compaction 评估为成本放大面的进一步优化，审计原文亦列为建议③可选支。
- Successor Required: `no`（若 Phase 4 实现期裁定纳入则本项不成立）

## Non-Blocking Follow-ups

- **R2-16/R4-23 软依赖解除注记**：本批 done 后两批的软依赖前提（「同仓沙箱面先行」）满足——Phase 6 在 cra-index 对应组行核对登记，防后续批次重复等待或越界承接。
- **3.2-C10-002 反向钉-1（ShellIOTest:294-300 队满 close）**：随 G56 条款激活时同批改写（R-G 条款登记册承载），本批不触碰。
- **G56 dormant 条化束回升监控**：MA-05 族（C1-002/C6-005/C8-002/C8-004/C8-005 等）「投产接线即回升」条款归触发条款登记册，本批不承载。
- **BashExecutionTracker 取消路径收口核对**：cancelExecution live 已有进程树收割 + cancelAction 失败重抛（勘误 5）——Phase 2 工单落地时核对关流补偿是否需覆盖该路径（reader 补偿口径一致性），不改判级不扩批。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence:
  - <<每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）>>
  - <<每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）>>
  - <<`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码>>
  - <<Anti-Hollow 检查结果；`scan-hollow-implementations.mjs` 退出码>>
  - <<Deferred 项分类检查>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
