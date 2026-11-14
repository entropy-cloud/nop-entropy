# 2572 十二问批量审查确认缺陷统一修复

> Plan Status: completed
> Last Reviewed: 2026-11-14
> Source: 用户十二问批量审查 + 三并行查证 agent 报告；本 plan 只收 CONFIRMED 缺陷，裁定成立的站点不修改

## Purpose

把批量审查确认的 5 个缺陷族收口：全局实例发布竞态、client 登记泄漏半边、限流桶无上界、时钟口径违规面、context 桥接复制粘贴。

## Current Baseline

- `XPathProvider._instance`（nop-core `lang/xml/XPathProvider.java:18`）：mutable + non-final + non-volatile 唯一命中（全仓扫描），跨线程写读竞争（启动线程注册/工作线程读）。2515 W-11 同族先例已修。
- `SimpleRpcClient.close()`（nop-rpc-simple `SimpleRpcClient.java:122-124`）只关底层连接，不从 factory `clients` 登记表摘除（`SimpleRpcClientFactory.java:44/216-219/166-181`）——长寿 factory 反复 newInstance 时登记表无界增长，destroy 重复 close 已声明容忍（:40-42）。
- `DefaultFlowControlRunner.buckets`（nop-rpc-core `flowcontrol/DefaultFlowControlRunner.java:32/61`）懒创建无 maximumSize/淘汰/清扫，键 = resourceType:resource；gateway 先例 `AiRateLimitGatewayInterceptor.java:108-129` 用 Caffeine（expireAfterAccess+maximumSize）已裁定有界形态；plan 2551 C8-008 只裁定键控语义未裁定容量。
- 时钟口径：main 代码（排除 ai/stream/code/tools 等 7 模块与 benchmark）约 49 处直接 `System.nanoTime()/currentTimeMillis()`；CoreMetrics 本体/IClock/JobWorkerScannerImpl:377-378 有豁免注释属合法；可疑违规典型 8 处（ShardFanoutEngine:271+、TaskFlowManagerImpl:259,293、ReadWriteRoutingDataSource:165、JdbcTransaction:75-77、PollingRpcClient:115,160、DbToolTerminationHelper:40-41、JsonRpcWebSocketHandler:100,295,577、ChangeSubscriptions:83,92）——多数语义正确但未按 CoreMetrics 口径/豁免登记。
- GraphQL context 桥接：`ContextProvider.thenOnContext` 已有 8+ 消费方；但 GraphQLExecutor:143-160/:186-200、TransformFetcher:40-58、EvalActionTransformFetcher:40-56 共 4 站为手写四件套（whenComplete+try/catch+context.execute+completeExceptionally）复制粘贴，未收敛。

## Goals

- 上述 5 族全部修复并有回归钉；裁定成立站点（TaskStepRuntimeImpl 强转/OrmSession 锁/DaoUserContextCache/session 先查后删/updateEntityDirectly 条件 UPDATE/fetchServerEventFlow 包络/globalWorker 占用声明族）零改动。

## Non-Goals

- 不扩 ITaskStepRuntime 接口（parentStepRt 不入公共契约，plan 2541 裁定保持）。
- 不改 DaoUserContextCache 锁与条件 UPDATE 形态。
- nop-benchmark 基准代码、已豁免注释站点不做时钟换源。

## Execution Plan

### Phase 1 - XPathProvider volatile（nop-core）

Status: completed
Targets: `lang/xml/XPathProvider.java`

- Item Types: `Fix | Proof`

- [x] `_instance` 加 volatile + javadoc 契约句（发布安全：启动线程注册/工作线程读，禁 null 半构造暴露）
- [x] 结构钉：反射断言字段 `Modifier.isVolatile`（回归钉，先红后绿）

Exit Criteria:

- [x] 钉先红后绿；nop-core 相关测试全绿
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - SimpleRpcClient 登记摘除（nop-rpc-simple）

Status: completed
Targets: `SimpleRpcClient.java`、`SimpleRpcClientFactory.java`

- Item Types: `Fix | Proof`

- [x] close() 逆序摘除登记（factory 传入 callback 或 client 持 factory 弱引用），destroy 对已摘条目幂等
- [x] 回归钉：newInstance→close→断言登记表空；漏关→destroy 兜底仍有效（既有 TestSimpleRpcClientFactoryDestroy 零回退）

Exit Criteria:

- [x] 先红后绿 + 既有 destroy/idempotent 测试全绿
- [x] `ai-dev/logs/` 条目更新

### Phase 3 - FlowControlRunner 桶上界（nop-rpc-core）

Status: completed
Targets: `DefaultFlowControlRunner.java`

- Item Types: `Fix | Proof`

- [x] buckets 改 Caffeine LocalCache（对齐 gateway 先例：expireAfterAccess + maximumSize），setter clear 语义保留
- [x] 回归钉：懒创建命中 + 空闲淘汰 + setter 重建语义（TestDefaultFlowControlRunner 契约改形登记）

Exit Criteria:

- [x] 先红后绿 + 既有限流测试零回退（forkCount=1 面）
- [x] `ai-dev/logs/` 条目更新

### Phase 4 - 时钟口径治理（跨模块）

Status: completed
Targets: Phase Baseline 列 8 个可疑文件

- Item Types: `Fix | Follow-up`

- [x] 逐点裁定：单调性敏感面（TaskFlowManagerImpl gate idle 判定、DbToolTerminationHelper busy-wait deadline、ChangeSubscriptions 计时）换 CoreMetrics.nanoTime；诊断耗时/日志面加豁免注释（对齐 JobWorkerScannerImpl:377 先例）；心跳墙钟（JsonRpcWebSocketHandler）裁定语义（keepalive 是墙钟域还是时长域）后处理
- [x] 每点一行裁定记录入 daily log（换源 or 豁免+理由）

Exit Criteria:

- [x] 8 文件全部裁定落地；受影响模块测试全绿
- [x] `ai-dev/logs/` 条目更新

### Phase 5 - GraphQL context 桥接收敛（nop-graphql）

Status: completed
Targets: `GraphQLExecutor.java`、`TransformFetcher.java`、`EvalActionTransformFetcher.java`、`ContextProvider.java`

- Item Types: `Fix | Proof`

- [x] `ContextProvider.thenOnContext` 增补变体（execute 抛异常→异常终态防挂死分支保留）或新增 FutureHelper 收敛点
- [x] 4 站改收敛调用，行为逐字节等价（含防挂死分支）

Exit Criteria:

- [x] GraphQL 既有测试全绿；行为等价论证写入 log
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 5 族全部落地 + 回归钉先红后绿
- [x] 裁定成立站点零改动核实（diff 范围核查）
- [x] 独立 closure audit + 证据
- [x] 受影响模块（nop-core/nop-rpc-simple/nop-rpc-core/时钟面模块/nop-graphql）测试全绿

## Deferred But Adjudicated

### 裁定成立站点不修改

- Classification: `watch-only residual`
- Why Not Blocking Closure: TaskStepRuntimeImpl instanceof（有意裁定+唯一构造点）、OrmSession 锁（2517 R2-14 防御性互斥声明）、session 先查后删（批上限便携实现）、DaoUserContextCache（2568 工单闭环）、updateEntityDirectly→条件 UPDATE（2569 工单闭环+500 vs false 语义正确）、fetchServerEventFlow 包络（裁定 H Publisher 对偶）、globalWorker 占用（9 站 8 站已声明）
- Successor Required: no

### 限额默认值 3x 余量原则

- Classification: `optimization candidate`
- Why Not Blocking Closure: 属配置治理而非缺陷；现有限额配置有观测面，改动需逐配置回归
- Successor Required: no

## Closure

Status Note: 5 族全部落地——P1 XPathProvider volatile（结构钉红→绿）；P2 close 摘除登记（callback 注入，变异红→绿，destroy 兜底零回退）；P3 buckets LocalCache 有界（maximumSize+expireAfterAccess，对齐 gateway 先例，5/5 绿）；P4 时钟口径 8 文件逐点裁定（6 换源 mono + 2 豁免登记：ShardFanoutEngine 诊断耗时/ReadWriteRoutingDataSource 经 seam 全族换源含测试 ns 换算）；P5 ContextProvider.whenCompleteInContextWindow 收敛 4 站（窗口载体与 dispatchAll 缝隙语义不变，graphql 205/205）。裁定成立 7 站零改动。10 受影响模块 BUILD SUCCESS。
Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立 Explore 子代理 agent_7a567685-71e3-475c-a583-89eaa22e68b5
- Audit Session: agent_7a567685-71e3-475c-a583-89eaa22e68b5
- Evidence:
  - P1-P5 逐 Gate PASS（file:line 见审计报告）：XPathProvider:21 volatile+钉；SimpleRpcClient:51/126-134+Factory:216-225 摘除链连通；FlowControlRunner:38-40 LocalCache+钉；时钟 8 文件换源/豁免逐点核实（TaskFlowManagerImpl 裸调用=0）；ContextProvider:211-226 helper+4 站收敛（dispatchAll 缝隙注释保留）
  - Anti-Hollow：close→callback→registry.remove 与 future.whenComplete→helper→context.execute→windowBody 两链代码级连通
  - checklist --strict exit 0；受影响 10 模块 reactor BUILD SUCCESS
  - 结论 APPROVED（1 Minor：TestReadWriteRoutingDataSource 钉命名偏泛，不影响验收）

Follow-up:

- 限额默认值 3x 余量治理（Deferred：optimization candidate）
- 其余 ~40 处时钟直接调用站点未逐点裁定（豁免清单已登记 8 文件，余量属诊断/基准豁免形态，后续按需）
