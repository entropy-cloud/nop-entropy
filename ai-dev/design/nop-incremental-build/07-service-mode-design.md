# nop-ib 服务化模式（无状态常驻构建服务）设计

**范围**：`nop-tools/nop-ib`
**状态**：active（规范性文档，只描述当前有效设计；变更历史见 git 与时效性文档）
**上游**：[05-nop-ib-engine-design.md](05-nop-ib-engine-design.md)（引擎形态与正确性门）、[06-junit-worker-reuse-and-generalization-design.md](06-junit-worker-reuse-and-generalization-design.md)（worker 复用与执行模型）

> 本档细化 05 的 `serve` 形态：从"单项目 stdio JSONL"扩展为"多项目无状态常驻服务"——任何程序发送项目地址 + 指令即可获得编译/测试结果。不改变 05 的正确性门、回退矩阵与锁纪律。

## 一、设计结论

| 编号 | 决策 | 理由 |
|---|---|---|
| IB-S1 | 服务进程是**无状态调度器**：全部构建状态（model.json / 单元索引 / 测试缓存 / last-check）留在各项目 `.nop-ib/` 目录；进程内存只持有 job registry、engine cache 与队列锁 | 崩溃重启零恢复成本——状态在磁盘，重启后同一请求重放即得相同结论（幂等）；"无状态"指服务不持有跨请求必须持久化的构建事实 |
| IB-S2 | 传输 = JDK 内置 HTTP server（loopback only）+ 既有 stdio JSONL 模式保留；协议复用 CLI 的 JSON 契约（同一 `NopIbMain.run` 入口） | 零新增依赖（nop-ib 不引入 web 框架）；任何语言的调用方（AI agent、编辑器插件、CI 脚本）用 curl 即可；stdio 模式覆盖嵌入场景 |
| IB-S3 | **同项目串行**：以 repo root 规范路径为键，每个项目同一时刻至多一个构建任务在执行，其余请求排队（FIFO、有界） | 同一 worktree 并发写 `target/` 必然互相踩踏（05 IB-D9 的进程内对应面）；调用方无需自行加锁 |
| IB-S4 | **双层并行预算**：① 全局 job 并发闸 = 信号量 permits `max(2, cores/2)`，跨项目共享；② 服务执行每个 job 时把该 job 的 worker 预算压为 `max(2, permits / 活跃 job 数)`（经引擎的 worker 预算 override 接缝，CLI 路径不受影响） | 只有 job 数封顶封不住机器负载——单 job 内部还叠着测试 worker / 编译线程；两层合起来才保证"机器上同时活跃的 worker 总数 ≤ cores/2"；单项目运行时（活跃=1）仍可用满其原预算 |
| IB-S5 | 异步 job 模型：`run` 请求默认同步等待（简单调用方）；`wait=false` 时立即返回 job id，`GET /v1/job/{id}` 轮询结果；job 记录只存内存（结果本体可由客户端持 job id 取一次） | 多数调用方要的是阻塞语义（等价于跑 mvn）；长任务不占连接；job registry 丢失 = 调用方重发请求，磁盘状态保证幂等 |
| IB-S6 | 多项目 engine cache：`canonicalPath → IbEngine`，模型/索引已落盘故 cache 仅省重复装载；cache 有界（LRU，默认 8），**驱逐只作用于非 running root**（运行中 job 持强引用）；驱逐后重建无损 | engine 本身无独占资源；驱逐即冷装载，正确性不受影响；禁止驱逐运行中 root 避免同项目双 engine 实例的不一致写路径 |

## 二、核心契约

### 2.1 HTTP API（loopback）

```
POST /v1/run          {"projectDir": "...", "cmd": "test|compile|check|plan|status", "args": [...], "wait": true|false}
GET  /v1/job/{id}     → {"id", "state": queued|running|done|failed, "exit", "payload", "queuedAt", "startedAt", "finishedAt"}
GET  /v1/queue        → 每项目运行中/排队中的 job 计数（可观测）
GET  /v1/health       → {"version", "cores", "workerBudget", "activeProjects"}
DELETE /v1/job/{id}   → 取消排队中的 job（running 的 job 不中断，语义 = 构建不可安全半途中断）
```

- 回复 payload 与 CLI `--json` 输出同构（同一 RunLog 契约）：CLI stdout 是 NDJSON 序列且字符串可含换行——服务端按 JSON 序列解析（单文档 → 对象，多文档 → 数组），`[nop-ib]` 进度行过滤，stderr 诊断单独放 `stderr` 字段（不混入 payload）；解析失败回退原始字符串并如实标注；payload 超 8MB 截断并附 `truncated:true`；
- HTTP 状态码只表达传输层结果（队列满 = 429）；构建成败看 `exit` 字段（与 CLI 退出码同表：0 成功 / 1 失败 / 6 partial / 75 锁超时）。

### 2.2 项目身份与排队语义

**lane 键 = canonical repo root**，推导规则（缺一不可，任何别名不得产生第二条 lane）：

1. 优先 `mvnw + .mvn` 标记目录（nop-entropy worktree 形态）；
2. 无标记时沿**连续 pom 链**爬到 reactor 聚合顶（父目录还有 pom.xml 即同 reactor）——从 `<root>/moduleA` 提交与从 `<root>` 提交是同一项目；
3. 服务入口对该结果做 `toRealPath()`——symlink 别名（`~/sources/x` vs 真实挂载路径）解析为同一 lane；引擎内部的 model 过期判定同样做 real-path 归一比较（别名不再制造永久 Maven fallback）。

违反即后果：同 worktree 双 lane = 并发写同一 `target/` 且 `.mvn/build.lock` 随 lane 分裂失去互斥——这正是服务要消灭的践踏面。

排队语义原文：

```
on run(projectDir, cmd, args):
  root := discoverRepoRoot(projectDir)          // 失败 → 400
  slot := queueFor(root)                        // 每 root 一个 FIFO 队列 + 单执行闸门
  if slot.depth >= MAX_QUEUE(32) → 429（排队满，显式拒绝，不静默丢弃）
  job := slot.enqueue(job)
  执行时：acquire 全局 worker 信号量 → NopIbMain.run(cmd, args on root) → 释放
```

- 排队中的 job 可取消；进入执行后不可取消（返回 `cannot-cancel-running`）；
- 相同项目 + 相同参数 + 目标状态可复用（如 `--cache` 命中）时**不合并**请求：每个请求独立入队、独立得到显式结果（cached-up-to-date 也是结果）。合并会让第二个调用方拿到不属于自己的执行语义。

### 2.3 并行预算

- 全局信号量 permits = `max(2, cores/2)`，系统属性 `nop.ib.serviceWorkerBudget` 覆盖；
- 服务执行 job 时把单 job 的 worker 预算压为 `max(2, permits / 活跃 job 数)`（**入场时压缩**：已运行 job 保持其入场份额直到结束，瞬态超出以一个 job 的初始份额为界）；经引擎的 worker 预算 override 接缝生效，CLI（非服务）路径不读 override、行为零漂移；单 job 内部调度（worker pool、模块级并行）不变；
- 编译执行沿用引擎内既有的"模块间有界并行"；服务层不重复实现模块级调度；
- `wait=true` 请求与异步 job 走同一提交路径（handler 内等待完成），保证执行语义唯一；HTTP executor 固定小尺寸但有下限（≥ 排队面），长任务不得饿死读端点。

## 三、约束与边界

1. **无文件锁（2026-11-17 owner 裁定）**：并发完全由 ib 进程内控制——service per-project lane 是唯一闸门；跨进程（人类裸 mvn、双实例换端口）不设防，与人类并发 mvn 的现状风险同级。冷路径 Maven 定位：`nop.ib.maven` > mvnw > PATH。2. **正确性门不变**：fallback / partial / wideImpact / cached 标记全部原样透传给调用方；服务不吞并、不改写任何结果语义。
3. **不做**：远程（非 loopback）服务、多机分布式构建、构建结果的全局内容寻址缓存（`--cache` 仍是每项目 opt-in）、鉴权体系（loopback 即边界）。
4. **资源**：HTTP 线程池固定小尺寸（IO 等待为主）；服务进程 `-Xmx` 由启动方决定，引擎不自行设堆。

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| gRPC / Netty / Spring 等框架 | nop-ib 依赖面必须保持极小；JDK HttpServer 覆盖 loopback JSON 场景且零依赖 |
| 每请求 fork 一个 nop-ib CLI 进程 | 失去常驻意义：模型/索引装载与 JVM 预热反复付费；stdio serve 已覆盖该形态 |
| 每项目独立常驻进程 | N 个项目 = N 份 JVM 骨架 + 无法做全局机器预算；单进程多引擎才是"按机器情况并行"的持有方 |
| 全局并发执行（不按项目串行） | 同项目并发写 target/ 互相破坏；排队成本远低于踩踏修复成本 |
| 请求合并 / 结果去重 | 第二个调用方将拿到不属于自己的执行语义（违反显式结果契约） |
| job 结果持久化到磁盘 | 无状态原则的反面；客户端持 id 取一次即可，崩溃后重发请求幂等 |

## 五、与已有设计的关系

- **05 引擎设计**：本档不触碰其命令面、JSON 契约、退出码、正确性门与锁纪律；`serve` 的 stdio 模式继续由 05 §九 定义。
- **06 worker 复用**：worker pool 的预算键仍按项目隔离；服务层信号量在 pool 之外做跨项目总封顶。
- **`.mvn/build.lock`（IB-D9）**：服务执行与手工 `mvn`/`mvnq` 并发时，文件锁仍是最终仲裁者。
