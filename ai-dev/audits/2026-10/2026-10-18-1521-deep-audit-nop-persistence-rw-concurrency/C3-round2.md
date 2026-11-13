# C3 异步链路正确性 — 第 2 轮深挖（blind round 2）

- **维度**: C3 异步链路正确性（异步组合的异常传播、取消/超时语义、上下文传播与时序假设）
- **批次**: 深挖合并批（第 2 轮，全部 5 源码根）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **盲审纪律声明**: 本轮为盲审第 2 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；唯一例外为本目录 7 个维度初审文件（第 1 轮，必要输入）。所有验证基于当前 live code。
- **本轮目标**（按深挖指令）:
  1. C3-001/005 失效面扩展——全部 runInNewSession/runInSession 调用点穷举（上层哪些路径异步可达）；
  2. C3-006（drainTo 池死锁）的 K 消费者部署形态量化。
- **新发现编号**: 本轮 C3 维度**无新增发现**（调用点穷举验证 C3-001 的「一处修复覆盖全部兄弟面」断言成立；量化为第 1 轮条目补强，不另立新号）。

---

## 深挖 1：runInNewSession / runInSession 调用点穷举（C3-001 失效面扩展）

5 源码根 src/main 全量 grep（排除 `IOrmTemplate.java`/`OrmTemplateImpl.java` 定义本体），按文件聚合：

| # | 调用方 | 站点数 | 同步/异步 | 是否落入 C3-001 修复点 |
|---|--------|--------|----------|----------------------|
| 1 | `nop-orm/dao/OrmEntityDao` | 21 | 全部同步 `runInSession`（grep 确认该类**零 CompletionStage**） | 承继（无既有会话时进 runInNewSession→finally 兜底，同步版安全；异步不可达） |
| 2 | `nop-orm/dao/AbstractDaoHandler` | 4 | 同步（runInTransaction=runInSession+REQUIRED；runLocal=runInNewSession+REQUIRES_NEW） | 承继（同步） |
| 3 | `nop-orm/interceptor/SingleSessionMethodInterceptor` | 2 | **async 分支→runInSessionAsync**（:34-38，核实于本轮） | **异步可达主通道**：@SingleSession 异步方法→无既有会话→runInNewSessionAsync→C3-001 |
| 4 | `nop-orm/interceptor/SingleSessionServiceInterceptor` | 2 | **interceptAsync→runInSessionAsync**（核实于本轮） | 同上（RPC 异步链） |
| 5 | `nop-orm/utils/SingleSessionFunctionInvoker` | 2 | **invokeAsync→runInSessionAsync**（:35-39，核实于本轮） | 同上 |
| 6 | `nop-orm/initialize/DataInitInitializer` | 1 | 同步，启动期 | 安全 |
| 7 | `nop-orm-data/DaoEntityBlockingSource.tryClaim` | 1 | 同步 `runInSession`（:260） | 承继（同步） |
| 8 | （跨根引用）`nop-orm-rpc/RpcEntityPersistDriver` | 7 | 同步 runInSession | 5 根之外，交叉引用登记 |

**穷举结论**：C3-001 第 1 轮 F3 断言「全部异步会话入口收敛于 runInNewSessionAsync 一处、一处修复覆盖全部兄弟面」经本轮调用点穷举**复核成立**——异步可达链有且仅有 3 条 AOP 入口（#3/#4/#5），全部经 `runInSessionAsync` 的无既有会话分支落到 `runInNewSessionAsync`；同步入口（#1/#2/#7）不落该缺陷。`flushSession/clearSession` 等直读入口不创建会话、无泄漏面。无新增缺陷位点。

**C3-005（flush 半成功）失效面补强**：第 1 轮 F3 列出 flush()/flushImmediately/save·update·deleteDirectly/flushSave·Update·Delete。本轮补充核对 `DaoEntityBlockingSource.drainTo` 轮询体内的 `dao.flushSession()`（:196）——该调用在 REQUIRES_NEW 事务内、消费循环可捕获异常后继续轮询（`BlockingSourceConsumeTask.run` 的 catch-continue 形态，nop-commons 侧核实），恰是 C3-005 触发时序中「调用方捕获异常并继续使用会话」的现实部署形态——C3-005 的 P1 判级触发面由「批处理框架常见编排」落实为**平台自带的消费任务骨架**，维持 P1。

## 深挖 2：C3-006 K 消费者部署形态量化

**部署形态核实**（消费框架实际接线，本轮逐层追证）：
- 消费骨架 1：`nop-batch` 的 `BlockingSourceBatchLoader`（批任务 loader）：`source.drainTo(items, batchSize, minWaitInterval, pollInterval)`——**maxWait=pollInterval>0（有界）**，但 loader 为 do-while 循环连续调用：连接持有占空比 ≈ pollInterval/(pollInterval+事务开关销开销) ≈ 接近 100%，空闲队列时每个 loader 线程**近乎连续**钉住 1 个连接（第 1 轮 F3 成员 ③「有界窗口」的量化补强：有界不等于低占空比）。
- 消费骨架 2：`nop-commons` 的 `BlockingSourceConsumeTask`（消息消费线程）：`source.drainTo(items, batchSize, config.getMinWaitMillis(), config.getMaxWaitMillis())`——maxWait 配置 ≤0 时即 C6-001/C3-006 主案的**无限等待**形态；每个消费任务 1 线程。
- 池容量核实：`DataSourceConfig.maxSize` **默认 8**（`nop-dao/jdbc/datasource/DataSourceConfig.java:22`），Hikari `maximumPoolSize` 直接取该值（`HikariDataSourceFactory.java:17-18`）；connectionTimeout 未配置时取 Hikari 默认 30s。
- 轮询负载核实：`DEFAULT_POLL_INTERVAL_MILLIS=100`（`DaoEntityBlockingSource.java:37`），空闲消费者每 tick 一条 SELECT（10 次/秒/消费者）。

**量化表（K 消费者 × 单 querySpace）**：

| K（阻塞消费者数） | 连接占用 | 后果 |
|---|---|---|
| K=1~3 | K/8 池容量被稳态钉住 | 可容忍，但空闲 DB 负载 10K q/s |
| K=4~7 | 过半池容量被空闲消费者钉住；业务高峰期池竞争显著，P99 获取延迟上升 | 性能劣化带 |
| **K≥8** | **池 100% 被空闲消费者钉住**；第 9 个并发 DB 需求（任何业务查询/写）等待 30s 后 SQLException | **全应用 DB 访问饥饿**（C6-001/C3-006 P1 判定的量化落点） |
| 批形态（B 任务 × C 并发 loader） | 空闲队列时 B×C 个连接近 100% 占空比 | K_eff = B×C，同上表 |

**量化结论**：默认配置下**8 个**阻塞消费者即触发池整体饥饿——该阈值在任何队列型应用（任务表消费者 ≥8 是常规配置）中常规出现，维持 C3-006/C6-001 P1。空闲 DB 负载 10K q/s（K=8）为白噪声级但持续。

---

## 新发现

无（C3-007 起编号保留；本轮两项深挖均为既有条目的失效面闭合与量化补强）。

## 终止判定

- **本轮新增**: 0 条；新增 CL：0。
- **终止信号**: (a) runInSession/runInNewSession 调用点穷举闭合（8 类调用方，异步可达链仅 3 条 AOP 入口且全部收敛于 C3-001 单一修复点）；(b) K 消费者量化闭合（池默认 8、轮询 100ms、两种消费骨架接线均已追证到具体文件行）；(c) C3-005 的现实触发形态已落实（BlockingSourceConsumeTask catch-continue）。
- **判定**: C3 维度第 2 轮**终止**，不建议第 3 轮。修复轮注意：C3-006 修复（事务边界内移到单次轮询）需与 C6-001 同一 work item 执行（同位点裁定见 `C6-round2.md`）。
