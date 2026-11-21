# 09 异步 Job API 设计（长轮询 / 进度 / 取消 / 日志）

> Status: active
> Date: 2026-11-21
> Owner: `nop-tools/nop-ib`
> Context: 用户裁定——对单个项目的排队可能导致等待过长；web 调用模式需要"超时返回但明示仍在排队/执行中 + taskId + 可轮询进度 + waitMs 长轮询 + 异步取消 + 任务列表 + 每任务日志（路径在状态中）"。

## 一、决策总览

| 决策 | 选择 | 被否决的替代与理由 |
|---|---|---|
| 调用形态 | submit（`wait=false` 或不传 `wait` 即返回 id）+ `GET /v1/job/{id}?waitMs=N` 长轮询 | 改成 WebSocket/SSE——loopback 单机场景引入协议复杂度不成比例；`wait=true` 保留为兼容路径 |
| 长轮询上界 | `waitMs` 缺省（不传）= 60000（1 分钟，与 AI 工具调用缺省 120s read 超时留安全余量）；显式值 clamp 到 `[0, 300000]`（5 分钟，单次阻塞覆盖典型 check/compile）；`waitMs=0` 纯快照。裁定于 2026-11-21：用户指出缺省应为分钟级 | 缺省=0 纯快照——naive 客户端被迫 sleep 轮询；无上界长阻塞——HTTP 客户端超时/代理层行为不可控 |
| 幂等 submit | 请求体可选 `clientRequestId`，服务端同 id 未终态 job 直接返回既有 job（去重），终态则返回快照并标注 `replayed:true` | 不做幂等——AI 超时重试会重复排队同一构建 |
| 进度 | 进程内 ProgressRegistry（按 repoRoot 键），TestCommand 报 `modulesDone/modulesTotal`、CompileCommand 报编译选择集规模；snapshot 字段 `progress` | 把进度写进 payload——payload 是终态 JSON 契约，中途改写会破坏 `--json` 解析 |
| 任务日志 | 每个 job 一个日志文件 `{repoRoot}/.nop-ib/jobs/{id}.log`（stdout+stderr 双流带标记 tee），`logPath` 进状态；`GET /v1/job/{id}?logTail=N` 附带尾部 N 行（clamp 500）；job prune 时同步删日志文件 | 只存内存 ring buffer——重启即失且大 payload 内存放大；写 `系统 /tmp`——违反项目临时文件纪律 |
| 运行中取消 | 协作式：进程级 ProcessTracker 登记全部三个进程源（MavenFallback、JUnitForkExecutor、JUnitWorkerPool 的常驻 worker 进程）派生的子进程；DELETE 时对每个登记进程 `handle.descendants().forEach(destroyForcibly)` 后再杀本进程（Java destroyForcibly 只杀直接子进程，surefire 孙进程须走 descendants 树终止）+ 中断 job 线程，job 置 `CANCELLED`（exit=-1），日志尾部写入 `cancelled at phase=…` fail-loud 记录 | 保持 QUEUED-only cancel——长时间跑错的测试无法止损是真实痛点；强杀的风险面由"build 状态全在 .nop-ib/、可重跑幂等"兜底 |
| 断连语义 | submit 后客户端断连不影响 job 继续执行与日志落盘（异步化的本质收益，硬契约） | 断连取消 job——与幂等重试模型冲突 |
| 终态保留 | 维持 10 分钟 prune，日志文件随 job 同步删除 | 更长保留——loopback 单机场景 10 分钟足够取回结果，磁盘不累积 |

## 二、HTTP 契约（接口面）

- `POST /v1/run`：请求体新增可选 `clientRequestId`；`wait` 缺省语义不变（兼容）。响应新增 `replayed`（命中幂等去重时 true）。
- `GET /v1/job/{id}?waitMs=N&logTail=N`：
  - 终态：现有 snapshot + `logPath`；带 `logTail` 时附 `logTail`（≤500 行字符串）。
  - 未终态（等满 waitMs）：HTTP 200 + `{id, state, phase:"queued"|"running", queueAhead, elapsedMs, progress?, logPath, waitedMs}`——**不是错误**，AI 侧循环调用直到终态。
- `DELETE /v1/job/{id}`：QUEUED 取消不变；RUNNING 走协作式取消（上表）；终态返回现有 409 语义。
- `GET /v1/jobs?limit=N&state=queued|running|done|failed|cancelled`：state 过滤为新增可选参数。
- `/v1/help` 同步以上全部契约（145→新的自描述完整性口径）。

## 三、并发与生命周期

- 长轮询等待复用 job monitor 的 `notifyAll`；现有代码只有两个唤醒点（终态 finally、QUEUED 取消），**新增 QUEUED→RUNNING 转换与 progress 更新两个唤醒点**，`wait(剩余 deadline)` 分段等待。
- ServeHttp 现状是单一 16 线程池服务全部 context——长轮询会占满 handler 池饿死读端点。裁定：长轮询请求走独立小池（longPoll 池，4 线程 + 有界拒绝），读端点留主池；长轮询并发超界立即返回纯快照（waitedMs=0），不排队等待。
- 日志目录 `{repoRoot}/.nop-ib/jobs/` 在 job 首次写日志时惰性创建（lane-full 拒绝路径不留空目录）；终态 job 与日志的清理挂到现有 engine reaper 周期任务（不依赖下一次 submit 才触发 prune）。
- ProgressRegistry 为静态 volatile map（root→progress 快照），写方为 CLI 命令线程（TestCommand 的 plan-scope 与 -pl 并行两条主路径都上报），读方为 snapshot 组装；job 结束不清理（随 root 下次构建覆盖），内存面为 O(roots)。
- ProcessTracker 为静态登记表（job→List<Process>），MavenFallback/JUnitForkExecutor 在 `Process.start()` 后登记、进程退出后注销；取消方遍历 destroyForcibly。job 线程中断作为无子进程阶段（编译快速路径）的兜底。
- 幂等表：`(repoRoot, clientRequestId) → jobId` 进程内 ConcurrentHashMap（键绑 root，防跨项目同 id 误去重），随 job prune 一并清理；JobRegistry.create 加重载保留旧签名零涟漪。

## 四、失败与可观测语义

- 取消、超时、排队三种中间态在响应里显式可辨：`state` + `phase` + `waitedMs`，绝不复用 `failed`。
- 日志是唯一权威执行记录：payload（终态 JSON）之外的全过程（含 Maven 回退、codegen 任务输出、取消点）都在 job 日志文件里。
- `/v1/help` 是契约的机器可读 owner；README.md 同步。
